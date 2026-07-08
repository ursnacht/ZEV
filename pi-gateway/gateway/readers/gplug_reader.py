"""gPlug-Reader (BKW) – Platzhalter, folgt als spätere Erweiterung.

Das gPlug-Protokoll (Modbus / HTTP-REST / proprietär) ist noch nicht geklärt
(siehe Specs/Pi-Gateway-Software.md, Offene Fragen). Sobald bekannt, wird hier
ein vollwertiger Reader analog zu ``ModbusReader`` implementiert und in der
Factory freigeschaltet.
"""

from __future__ import annotations

from ..models import MeterReading
from .base import Reader


class GplugReader(Reader):
    """Noch nicht implementiert – Erweiterung nach Klärung des gPlug-Protokolls."""

    def read(self) -> MeterReading:  # pragma: no cover - noch nicht implementiert
        raise NotImplementedError(
            "gPlug-Reader ist noch nicht implementiert (spätere Erweiterung)."
        )
