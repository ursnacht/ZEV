"""Heartbeat/Monitoring: hält fest, wann zuletzt erfolgreich gelesen/publiziert wurde.

Damit ist ein stiller Ausfall erkennbar (vgl. Specs/Pi-Gateway-Software.md FR-7).
Die Zeitstempel werden pro Zyklus geloggt; ein externer Watchdog kann darauf
(oder auf ein späteres Status-Topic) aufsetzen.
"""

from __future__ import annotations

import logging
from datetime import datetime, timezone

log = logging.getLogger("gateway.heartbeat")


class Heartbeat:
    """Verfolgt die letzten erfolgreichen Read-/Publish-Zeitpunkte."""

    def __init__(self) -> None:
        self.last_successful_read: datetime | None = None
        self.last_successful_publish: datetime | None = None

    def record_read(self) -> None:
        self.last_successful_read = datetime.now(timezone.utc)

    def record_publish(self) -> None:
        self.last_successful_publish = datetime.now(timezone.utc)

    def log_cycle(self, success: int, total: int, connected: bool) -> None:
        """Loggt eine Zusammenfassung des Lesezyklus."""
        log.info(
            "Heartbeat: %d/%d Zähler ok | Broker verbunden=%s | "
            "letzter Read=%s | letzter Publish=%s",
            success,
            total,
            connected,
            _fmt(self.last_successful_read),
            _fmt(self.last_successful_publish),
        )


def _fmt(value: datetime | None) -> str:
    return value.isoformat().replace("+00:00", "Z") if value else "—"
