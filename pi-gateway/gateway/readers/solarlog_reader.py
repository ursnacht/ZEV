"""HTTP-Reader für ein Solar-Log (Specs/Pi-Gateway-Software.md, FR-9).

Liest die kumulativen Zähler eines Solar-Log über dessen offene JSON-Schnittstelle
(``POST /getjp``) statt über Modbus. Die Antwort trägt die Werte unter ``801`` → ``170``,
geschlüsselt nach Nummern (Tabelle: README, Abschnitt „Solar-Log“). Die Zähler stehen dort in
**Wh**; publiziert wird wie überall in kWh.
"""

from __future__ import annotations

import http.client
import json
import logging
import math
import urllib.error
import urllib.request
from datetime import datetime, timezone

from ..models import MeterConfig, MeterReading, ReadError
from .base import Reader

log = logging.getLogger(__name__)

# Die Abfrage der aktuellen Werte. Der Rumpf ist fest: Das Solar-Log liefert auf genau diese
# Anfrage den Block 801/170 mit allen Schlüsseln.
_ABFRAGE = b'{"801":{"170":null}}'

# Schlüssel 100 der Antwort: Zeitstempel der Werte in der Ortszeit des Solar-Log, ohne Zone.
_SCHLUESSEL_ZEIT = "100"
_ZEITFORMAT = "%d.%m.%y %H:%M:%S"

# Das Solar-Log führt seine Zähler in Wh, der Payload-Vertrag verlangt kWh.
_WH_JE_KWH = 1000.0


def _jetzt() -> datetime:
    """Ortszeit des Pi, ohne Zone - vergleichbar mit dem Zeitstempel des Solar-Log.

    Als Funktion, damit Tests die Zeit vorgeben können.
    """
    return datetime.now()


class SolarLogReader(Reader):
    """Liest Ertrag und/oder Verbrauch eines Solar-Log über HTTP."""

    def __init__(self, config: MeterConfig) -> None:
        super().__init__(config)
        if not config.host:
            raise ValueError(f"Solar-Log-Zähler '{config.messpunkt}' ohne host.")
        if config.schluessel_bezug is None and config.schluessel_einspeisung is None:
            raise ValueError(
                f"Solar-Log-Zähler '{config.messpunkt}' ohne Schlüssel (bezug oder einspeisung)."
            )
        self._url = f"http://{config.host}:{config.port}/getjp"
        # Wie beim Modbus-Zähler ohne Einspeisung-Register (FR-1.7): beim Start sichtbar machen,
        # welches Feld als 0 hinausgeht - sonst sähe ein vergessener Schlüssel aus wie ein Zähler,
        # an dem nichts läuft.
        for rolle, schluessel in (("bezug", config.schluessel_bezug),
                                  ("einspeisung", config.schluessel_einspeisung)):
            if schluessel is None:
                log.info("Solar-Log-Reader '%s': kein Schlüssel für %s konfiguriert – "
                         "es wird 0 publiziert.", config.messpunkt, rolle)

    def read(self) -> MeterReading:
        werte = self._hole_werte()
        self._pruefe_alter(werte)

        # Ein nicht konfigurierter Schlüssel ist kein Lesefehler: Das Feld wird mit 0 publiziert,
        # wie beim Modbus-Zähler ohne Einspeisung-Register (FR-1.7).
        bezug = self._stand(werte, self.config.schluessel_bezug, "bezug")
        einspeisung = self._stand(werte, self.config.schluessel_einspeisung, "einspeisung")

        return MeterReading(
            messpunkt=self.messpunkt,
            timestamp=datetime.now(timezone.utc),
            zaehlerstand_bezug=bezug,
            zaehlerstand_einspeisung=einspeisung,
            seriennummer=self.config.seriennummer,
        )

    def _hole_werte(self) -> dict:
        """Fragt das Solar-Log ab und liefert den Block ``801`` → ``170``."""
        anfrage = urllib.request.Request(self._url, data=_ABFRAGE, method="POST")
        try:
            # Das Schema der URL ist fest http:// (siehe __init__), nie aus der Antwort.
            with urllib.request.urlopen(
                    anfrage, timeout=self.config.read_timeout_seconds) as antwort:
                rumpf = antwort.read()
        except (urllib.error.URLError, OSError, http.client.HTTPException) as exc:
            # URLError deckt Verbindungsfehler und HTTP-Fehlerstatus ab, OSError die
            # Zeitüberschreitung beim Lesen (TimeoutError ist ein OSError). HTTPException kommt,
            # wenn die Verbindung mitten im Rumpf abbricht (IncompleteRead) oder das Gerät nicht
            # mit HTTP antwortet (BadStatusLine) - ohne sie gälte das als unerwarteter Fehler und
            # würde nicht wiederholt.
            raise ReadError(
                f"'{self.messpunkt}': Solar-Log unter {self._url} nicht lesbar "
                f"(Timeout {self.config.read_timeout_seconds:.1f}s): {exc}"
            ) from exc

        try:
            werte = json.loads(rumpf)["801"]["170"]
        except (ValueError, KeyError, TypeError) as exc:
            raise ReadError(
                f"'{self.messpunkt}': Antwort des Solar-Log ist kein erwartetes JSON "
                f"(801/170 fehlt): {rumpf[:120]!r}"
            ) from exc

        # Verlangt das Solar-Log eine Anmeldung, steht hier statt der Werte ein Text
        # ("ACCESS DENIED") - das soll als solcher in der Meldung erscheinen.
        if not isinstance(werte, dict):
            raise ReadError(
                f"'{self.messpunkt}': Solar-Log liefert keine Werte, sondern {werte!r} - "
                f"ist die offene JSON-Schnittstelle eingeschaltet?"
            )
        return werte

    def _pruefe_alter(self, werte: dict) -> None:
        """Verwirft Werte, deren Zeitstempel älter ist als ``max_alter``.

        Ein Solar-Log, das seine Wechselrichter nicht mehr erreicht, antwortet weiter - mit
        stehendem Zähler. Publiziert sähe das aus wie „keine Produktion“. Verworfen fehlen dem
        Backend die Messungen, und dessen Ausfall-Erkennung meldet es.

        Verglichen wird Ortszeit gegen Ortszeit: Beide Geräte brauchen dieselbe Zone und eine
        richtig gehende Uhr. Mit ``max_alter: 0`` entfällt die Prüfung.
        """
        max_alter = self.config.max_alter_seconds
        if max_alter <= 0:
            return

        roh = werte.get(_SCHLUESSEL_ZEIT)
        try:
            zeit = datetime.strptime(str(roh), _ZEITFORMAT)
        except ValueError as exc:
            raise ReadError(
                f"'{self.messpunkt}': Zeitstempel des Solar-Log nicht lesbar ({roh!r}) - "
                f"mit 'max_alter: 0' entfällt die Prüfung."
            ) from exc

        alter = (_jetzt() - zeit).total_seconds()
        # Auch ein Zeitstempel aus der Zukunft ist ein Befund: Die Uhren passen nicht.
        if abs(alter) > max_alter:
            raise ReadError(
                f"'{self.messpunkt}': Werte des Solar-Log sind von {roh} und weichen "
                f"{alter:.0f}s von der Zeit des Pi ab (erlaubt: {max_alter:.0f}s) - verworfen. "
                f"Stimmen Uhr und Zeitzone beider Geräte?"
            )

    def _stand(self, werte: dict, schluessel: int | None, rolle: str) -> float:
        """Ein kumulativer Zähler in kWh; 0, wenn für die Rolle kein Schlüssel konfiguriert ist."""
        if schluessel is None:
            return 0.0

        roh = werte.get(str(schluessel))
        # bool ist ein int-Subtyp - true/false ist kein Zählerstand.
        if isinstance(roh, bool) or not isinstance(roh, (int, float)) or not math.isfinite(roh):
            raise ReadError(
                f"'{self.messpunkt}': Schlüssel {schluessel} ({rolle}) fehlt in der Antwort des "
                f"Solar-Log oder ist keine Zahl (war: {roh!r})."
            )
        if roh < 0:
            raise ReadError(
                f"'{self.messpunkt}': negativer Zählerstand unter Schlüssel {schluessel} "
                f"({rolle}): {roh} - verworfen."
            )
        return roh / _WH_JE_KWH
