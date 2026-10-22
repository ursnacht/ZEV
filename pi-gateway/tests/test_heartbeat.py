"""Heartbeat: letzter erfolgreicher Read/Publish (Specs/Pi-Gateway-Software.md, FR-7).

Ausführen (im Verzeichnis ``pi-gateway``): ``python -m unittest discover -s tests -t .``
"""

from __future__ import annotations

import unittest
from datetime import datetime, timezone
from unittest import mock

from gateway.heartbeat import Heartbeat


class HeartbeatTest(unittest.TestCase):

    def test_neu_OhneReadUndPublish_ZeitpunkteLeer(self) -> None:
        heartbeat = Heartbeat()

        with self.assertLogs("gateway.heartbeat", level="INFO") as logs:
            heartbeat.log_cycle(0, 3, False)

        self.assertIsNone(heartbeat.last_successful_read)
        self.assertIsNone(heartbeat.last_successful_publish)
        self.assertIn("0/3 Zähler ok", logs.output[0])
        self.assertIn("letzter Read=— | letzter Publish=—", logs.output[0])

    def test_recordReadUndPublish_ZeitpunkteFestgehalten(self) -> None:
        heartbeat = Heartbeat()
        vorher = datetime.now(timezone.utc)

        heartbeat.record_read()
        heartbeat.record_publish()

        nachher = datetime.now(timezone.utc)
        self.assertTrue(vorher <= heartbeat.last_successful_read <= nachher)
        self.assertTrue(vorher <= heartbeat.last_successful_publish <= nachher)

    def test_logCycle_NachReadUndPublish_GibtZeitpunkteAus(self) -> None:
        heartbeat = Heartbeat()
        lesen = datetime(2026, 10, 22, 10, 0, 0, tzinfo=timezone.utc)
        publizieren = datetime(2026, 10, 22, 10, 0, 1, tzinfo=timezone.utc)
        with mock.patch("gateway.heartbeat.datetime") as uhr:
            uhr.now.side_effect = [lesen, publizieren]
            heartbeat.record_read()
            heartbeat.record_publish()

        with self.assertLogs("gateway.heartbeat", level="INFO") as logs:
            heartbeat.log_cycle(2, 3, True)

        zeile = logs.output[0]
        self.assertIn("2/3 Zähler ok", zeile)
        self.assertIn("Broker verbunden=True", zeile)
        self.assertIn("letzter Read=2026-10-22T10:00:00Z", zeile)
        self.assertIn("letzter Publish=2026-10-22T10:00:01Z", zeile)


if __name__ == "__main__":
    unittest.main()
