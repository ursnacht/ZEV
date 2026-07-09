"""Publisher-Simulator: erzeugt synthetische, monoton steigende Zählerstände.

Ersetzt den Modbus-/gPlug-Zugriff durch generierte Werte, damit der komplette
MQTT-Pfad (Read-Loop → MqttPublisher → Topic/Payload → Backend-Ingest → Aggregation)
ohne Hardware/Pi end-to-end getestet werden kann.

Verhalten je Zähler wird über den `messpunkt` gesteuert:
- enthält der Name "producer" → Einspeisung wächst schneller als Bezug (negatives `total`)
- sonst (Consumer)           → Bezug wächst, kaum Einspeisung (positives `total`)
"""

from __future__ import annotations

import random
from datetime import datetime, timezone

from ..models import MeterConfig, MeterReading
from .base import Reader


class SimReader(Reader):
    """Liefert bei jedem Aufruf leicht erhöhte, kumulative Zählerstände."""

    def __init__(self, config: MeterConfig, start_bezug: float = 1000.0,
                 start_einspeisung: float = 0.0) -> None:
        super().__init__(config)
        self._is_producer = "producer" in config.messpunkt.lower()
        self._bezug = start_bezug
        self._einspeisung = start_einspeisung

    def read(self) -> MeterReading:
        if self._is_producer:
            self._bezug += random.uniform(0.0, 0.05)
            self._einspeisung += random.uniform(0.30, 0.80)
        else:
            self._bezug += random.uniform(0.20, 0.60)
            self._einspeisung += random.uniform(0.0, 0.05)

        return MeterReading(
            messpunkt=self.messpunkt,
            timestamp=datetime.now(timezone.utc),
            zaehlerstand_bezug=round(self._bezug, 4),
            zaehlerstand_einspeisung=round(self._einspeisung, 4),
        )
