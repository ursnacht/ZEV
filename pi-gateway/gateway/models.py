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
    typ: str = "float32"          # aktuell nur float32 unterstützt
    wortfolge: str = "big"        # "big" (AB CD) oder "little" (CD AB)
    skalierung: float = 1.0       # gelesener Rohwert * skalierung = kWh


@dataclass(frozen=True)
class MeterConfig:
    """Vollständige Beschreibung eines Zählers (ein Eintrag der Zähler-Liste)."""

    messpunkt: str
    protokoll: str                # "modbus-tcp"; später "gplug"
    register_bezug: RegisterSpec
    register_einspeisung: RegisterSpec
    host: str | None = None
    port: int = 502
    unit_id: int = 1


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


@dataclass(frozen=True)
class MeterReading:
    """Ein gelesener, absoluter Zählerstand zum Messzeitpunkt."""

    messpunkt: str
    timestamp: datetime            # UTC
    zaehlerstand_bezug: float      # kWh, kumulativ, >= 0
    zaehlerstand_einspeisung: float  # kWh, kumulativ, >= 0
