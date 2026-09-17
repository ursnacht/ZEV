"""Datenmodelle für Konfiguration und Messwerte.

Die absoluten Zählerstände (Wirkenergie in kWh, OBIS 1.8.0 Bezug / 2.8.0
Einspeisung) werden unverändert publiziert; die Delta-/Intervall-Bildung
erfolgt im Backend (siehe Specs/MQTT-Integration.md).
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime


class ReadError(Exception):
    """Fehlgeschlagener/unvollständiger Read eines Zählers – Messung verwerfen."""


@dataclass(frozen=True)
class RegisterSpec:
    """Ein Modbus-Register für einen Zählerstand (z. B. Bezug oder Einspeisung)."""

    addr: int
    typ: str = "float32"          # "float32" (IEEE 754) oder "uint32" (vorzeichenlos)
    wortfolge: str = "big"        # "big" (AB CD) oder "little" (CD AB)
    skalierung: float = 1.0       # gelesener Rohwert * skalierung = kWh


@dataclass(frozen=True)
class MeterConfig:
    """Vollständige Beschreibung eines Zählers (ein Eintrag der Zähler-Liste)."""

    messpunkt: str
    protokoll: str                # "modbus-tcp"; später "gplug"
    register_bezug: RegisterSpec
    # None = in der Config nicht angegeben (typisch bei Konsumenten): das Register wird
    # NICHT gelesen, publiziert wird 0 (Payload-Vertrag bleibt vollständig).
    register_einspeisung: RegisterSpec | None
    host: str | None = None
    port: int = 502
    unit_id: int = 1
    # Seriennummer des verbauten Geräts (Zählertausch-Erkennung, siehe
    # Specs/Zaehlertausch-Erkennung.md). Optional; beim Zählertausch aktualisieren –
    # der Wechsel dieses Werts ist das Signal, an dem das Backend den Tausch erkennt.
    seriennummer: str | None = None
    # Lese-Timeout in Sekunden für diesen Zähler. Wird von der Konfiguration aufgelöst:
    # zaehler[].read_timeout, sonst der globale read_timeout (Default s. config.py).
    read_timeout_seconds: float = 5.0
    # Zustandsregister: Groessenname (klein, z. B. "soc") -> Register. Leer = das Geraet meldet
    # keine Momentanwerte, der Payload traegt dann kein 'zustand'-Objekt.
    #
    # Getrennt von bezug/einspeisung, weil es etwas anderes ist: Die beiden sind kumulative
    # Zaehlerstaende, aus denen das Backend Deltas bildet. Ein Ladezustand ist ein Momentanwert -
    # er wird nie aggregiert (Specs/Geraetezustand.md).
    register_zustand: dict[str, RegisterSpec] = field(default_factory=dict)


@dataclass(frozen=True)
class BrokerConfig:
    """MQTT-Broker-Verbindung (Secrets kommen aus der Umgebung)."""

    url: str
    username: str | None = None
    password: str | None = None
    qos: int = 1
    client_id: str = "zev-pi-gateway"


@dataclass(frozen=True)
class GatewayConfig:
    """Gesamte Gateway-Konfiguration."""

    org_id: int
    publish_interval_seconds: int
    broker: BrokerConfig
    meters: list[MeterConfig] = field(default_factory=list)
    # Globaler Default für das Lese-Timeout (Sekunden); je Zähler überschreibbar.
    read_timeout_seconds: float = 5.0
    # Lese-Versuche je Zähler und Zyklus, inklusive Erstversuch (1 = kein Retry).
    # Gilt global, da sporadische RTU-Fehler wechselnde Zähler treffen.
    read_attempts: int = 2


@dataclass(frozen=True)
class MeterReading:
    """Ein gelesener, absoluter Zählerstand zum Messzeitpunkt."""

    messpunkt: str
    timestamp: datetime            # aware; wird als lokale Zeit mit Offset publiziert
    zaehlerstand_bezug: float      # kWh, kumulativ, >= 0
    zaehlerstand_einspeisung: float  # kWh, kumulativ, >= 0
    seriennummer: str | None = None  # aus der Config; None -> Feld wird nicht publiziert
    # Momentanwerte des Geraets (Ladezustand u.a.), Groessenname -> Wert. Leer/None -> das Feld
    # 'zustand' wird NICHT publiziert, der Payload bleibt fuer Zaehler ohne solche Werte
    # unveraendert. Anders als die Zaehlerstaende sind das KEINE kumulativen Groessen: Das Backend
    # legt sie getrennt ab und aggregiert sie nie (Specs/Geraetezustand.md).
    zustand: dict[str, float] | None = None
