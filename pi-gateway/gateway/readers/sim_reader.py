"""Publisher-Simulator: erzeugt synthetische, monoton steigende Zählerstände.

Ersetzt den Modbus-Zugriff durch generierte Werte, damit der komplette
MQTT-Pfad (Read-Loop → MqttPublisher → Topic/Payload → Backend-Ingest → Aggregation)
ohne Hardware/Pi end-to-end getestet werden kann.

Verhalten je Zähler wird über den `messpunkt` gesteuert:
- enthält der Name "producer"                        → Einspeisung wächst schneller als Bezug (negatives `total`)
- enthält der Name "bilanz"                          → Bilanzzähler am Netzanschluss: BEIDE Register wachsen
                                                       (eine Meldung mit Bezug UND Einspeisung; der Backend-Ingest
                                                       splittet sie auf die Einheiten Bezug/Rücklieferung)
- enthält der Name "rücklieferung"/"ruecklieferung"  → nur Einspeisung wächst (negatives `total`)
- enthält der Name "bezug"                           → nur Bezug wächst (positives `total`)
- enthält der Name "speicher"/"batterie"             → Batteriespeicher: WECHSELNDE Lade- und
                                                       Entladephasen, nie beides gleichzeitig
                                                       (`total` mal positiv, mal negativ); dazu ein
                                                       Ladezustand (`soc`), der der Phase folgt
- sonst (Consumer)                                   → Bezug wächst, kaum Einspeisung (positives `total`)

Mit ``protokoll: sim-gplug`` erzeugt derselbe Reader die Messung eines **gPlugK**: Der Publisher
sendet sie als Tasmota-SENSOR-Meldung auf ``gPlug/{orgId}/{messpunkt}/tele/SENSOR`` statt im
Format des Pi-Gateways (Specs/Pi-Gateway-Software.md, FR-8.3). Dazu kommt die Momentanleistung
(``Pi``/``Po``), die ein echter gPlug mitliefert; einen Ladezustand hat ein gPlug nicht.
"""

from __future__ import annotations

import random
import time
from datetime import datetime, timezone

from ..models import MeterConfig, MeterReading
from .base import Reader

# Laenge einer Lade- bzw. Entladephase des Speichers, in Lesevorgaengen. Lang genug, dass die
# 15-Minuten-Aggregation ganze Intervalle mit eindeutigem Vorzeichen sieht.
_PHASE_MIN = 6
_PHASE_MAX = 12

# Grenzen des simulierten Ladezustands. Das Backend weist alles ausserhalb 0-100 ab
# (Specs/Geraetezustand.md, FR-3) - ein Simulator, der das reizt, erzeugte nur Systemmeldungen.
_SOC_MIN = 0.0
_SOC_MAX = 100.0


class SimReader(Reader):
    """Liefert bei jedem Aufruf leicht erhöhte, kumulative Zählerstände."""

    def __init__(self, config: MeterConfig, start_bezug: float = 1000.0,
                 start_einspeisung: float = 0.0) -> None:
        super().__init__(config)
        name = config.messpunkt.lower()
        if "producer" in name:
            self._mode = "producer"
        elif "bilanz" in name:
            self._mode = "bilanz"
        elif "rücklieferung" in name or "ruecklieferung" in name:
            self._mode = "ruecklieferung"
        elif "bezug" in name:
            self._mode = "bezug"
        elif "speicher" in name or "batterie" in name:
            # Auch "batterie": Der Messpunkt heisst in der Praxis nach dem Geraet, nicht nach
            # seinem Einheiten-Typ - bei Hene "Batterie-Hene".
            self._mode = "speicher"
        else:
            self._mode = "consumer"
        self._bezug = start_bezug
        self._einspeisung = start_einspeisung
        # Speicher: laufende Phase und ihre Restlaenge in Lesevorgaengen.
        self._laedt = True
        self._phase_rest = random.randint(_PHASE_MIN, _PHASE_MAX)
        # Ladezustand in Prozent. Startet halbvoll, damit beide Richtungen sofort sichtbar sind.
        self._soc = 50.0
        # gPlug-Modus: Momentanleistung aus dem Zuwachs seit dem letzten Lesen. None = noch kein
        # Lesen - die erste Meldung traegt Leistung 0, sonst teilte sie durch Millisekunden.
        self._gplug = config.protokoll == "sim-gplug"
        self._letzter_read: float | None = None

    def read(self) -> MeterReading:
        bezug_vorher, einspeisung_vorher = self._bezug, self._einspeisung
        if self._mode == "producer":
            self._bezug += random.uniform(0.0, 0.05)
            self._einspeisung += random.uniform(0.30, 0.80)
        elif self._mode == "bilanz":
            # Bilanzzähler Netzanschluss: beide Register in einer Meldung
            self._bezug += random.uniform(0.30, 0.90)
            self._einspeisung += random.uniform(0.10, 0.50)
        elif self._mode == "ruecklieferung":
            # Bilanzmesspunkt Netzanschluss: Rücklieferung an den VNB (nur Einspeisung wächst)
            self._einspeisung += random.uniform(0.10, 0.50)
        elif self._mode == "bezug":
            # Bilanzmesspunkt Netzanschluss: Bezug vom VNB (nur Bezug wächst)
            self._bezug += random.uniform(0.30, 0.90)
        elif self._mode == "speicher":
            # Ein Speicher laedt ODER entlaedt - nie beides gleichzeitig. Genau das unterscheidet
            # ihn von allen anderen Modi und ist der Grund fuer die Phasen: Wuerden beide Register
            # zugleich wachsen, waere `total` dauerhaft nahe 0 und die Aggregation zeigte eine
            # Batterie, die nichts tut.
            #
            # bezug = Ladung, einspeisung = Entladung (Specs/Batteriespeicher.md, FR-2).
            if self._phase_rest <= 0:
                self._laedt = not self._laedt
                self._phase_rest = random.randint(_PHASE_MIN, _PHASE_MAX)
            self._phase_rest -= 1
            if self._laedt:
                self._bezug += random.uniform(0.30, 0.80)
                self._soc = min(_SOC_MAX, self._soc + random.uniform(1.0, 3.0))
            else:
                # Etwas weniger als die Ladung: ergibt ueber die Zeit einen Round-Trip-
                # Wirkungsgrad um 90 %, wie ihn ein Lithiumspeicher tatsaechlich zeigt. Ein
                # Wirkungsgrad ueber 100 % waere ein stiller Hinweis auf vertauschte Register.
                self._einspeisung += random.uniform(0.25, 0.75)
                self._soc = max(_SOC_MIN, self._soc - random.uniform(1.0, 3.0))
        else:
            self._bezug += random.uniform(0.20, 0.60)
            self._einspeisung += random.uniform(0.0, 0.05)

        # Der Ladezustand kommt NUR vom Speicher - an einem Zaehler hat er keine Bedeutung, und
        # das Backend verwuerfe ihn mit einer Systemmeldung (falscher Einheiten-Typ).
        #
        # Er wird aus der LAUFENDEN PHASE abgeleitet, nicht unabhaengig gewuerfelt: Ein SOC, der
        # faellt waehrend die Ladung steigt, widerspraeche den Zaehlerstaenden derselben Nachricht -
        # und genau diese Art Widerspruch soll der Simulator aufdecken, nicht erzeugen.
        zustand = {"soc": round(self._soc, 1)} if self._mode == "speicher" and not self._gplug else None

        leistung_bezug = leistung_einspeisung = None
        if self._gplug:
            # Mittlere Leistung seit dem letzten Lesen: kWh / h = kW. So passen Leistung und
            # Zaehlerstaende derselben Meldung zusammen, wie bei einem echten Zaehler. Weil der
            # Simulator beschleunigt (je Lesen statt je Stunde) zaehlt, ist sie entsprechend hoch.
            jetzt = time.monotonic()
            if self._letzter_read is None:
                leistung_bezug = leistung_einspeisung = 0.0
            else:
                stunden = max(jetzt - self._letzter_read, 1.0) / 3600.0
                leistung_bezug = round((self._bezug - bezug_vorher) / stunden, 3)
                leistung_einspeisung = round((self._einspeisung - einspeisung_vorher) / stunden, 3)
            self._letzter_read = jetzt

        return MeterReading(
            messpunkt=self.messpunkt,
            timestamp=datetime.now(timezone.utc),
            zaehlerstand_bezug=round(self._bezug, 4),
            zaehlerstand_einspeisung=round(self._einspeisung, 4),
            seriennummer=self.config.seriennummer,
            zustand=zustand,
            meldung="gplug" if self._gplug else "zev",
            leistung_bezug_kw=leistung_bezug,
            leistung_einspeisung_kw=leistung_einspeisung,
        )
