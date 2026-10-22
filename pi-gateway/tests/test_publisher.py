"""MQTT-Publisher: Topic, Payload-Vertrag und Reconnect (Specs/Pi-Gateway-Software.md, FR-3, FR-4, FR-5).

Ausführen (im Verzeichnis ``pi-gateway``): ``python -m unittest discover -s tests -t .``
"""

from __future__ import annotations

import json
import unittest
from datetime import datetime, timezone
from unittest import mock

import paho.mqtt.client as mqtt

from gateway.models import BrokerConfig, MeterReading
from gateway.publisher import MqttPublisher, _to_payload


def _reading(**abweichend) -> MeterReading:
    werte = dict(
        messpunkt="ID742-Wohnung-1",
        timestamp=datetime(2026, 10, 22, 12, 30, 0, 654321, tzinfo=timezone.utc),
        zaehlerstand_bezug=12345.678949,
        zaehlerstand_einspeisung=0.0,
    )
    werte.update(abweichend)
    return MeterReading(**werte)


def _publisher(broker: BrokerConfig | None = None) -> tuple[MqttPublisher, mock.Mock]:
    """Ein Publisher mit gemocktem paho-Client (keine Netzwerkverbindung)."""
    broker = broker or BrokerConfig(url="tcp://nas.local:1883", qos=1)
    with mock.patch("gateway.publisher.mqtt.Client") as client_klasse:
        client = client_klasse.return_value
        client.publish.return_value = mock.Mock(rc=mqtt.MQTT_ERR_SUCCESS)
        publisher = MqttPublisher(42, broker)
    return publisher, client


class PayloadTest(unittest.TestCase):
    """Vertrag gemäss Specs/MQTT-Integration.md, FR-3."""

    def test_payload_OhneOptionaleFelder_GenauDiePflichtfelder(self) -> None:
        payload = _to_payload(_reading())

        self.assertEqual({"timestamp", "zaehlerstandBezug", "zaehlerstandEinspeisung"}, set(payload))

    def test_payload_Timestamp_LokaleZeitMitOffsetOhneMikrosekunden(self) -> None:
        payload = _to_payload(_reading())

        zeitpunkt = datetime.fromisoformat(payload["timestamp"])
        lokal = datetime(2026, 10, 22, 12, 30, 0, tzinfo=timezone.utc).astimezone()
        self.assertEqual(lokal.isoformat(), payload["timestamp"])
        self.assertIsNotNone(zeitpunkt.utcoffset())
        self.assertEqual(0, zeitpunkt.microsecond)
        self.assertEqual(datetime(2026, 10, 22, 12, 30, 0, tzinfo=timezone.utc), zeitpunkt)

    def test_payload_Staende_UnveraendertAufVierStellenGerundet(self) -> None:
        payload = _to_payload(_reading(zaehlerstand_bezug=12345.678949,
                                       zaehlerstand_einspeisung=4321.5))

        self.assertEqual(12345.6789, payload["zaehlerstandBezug"])
        self.assertEqual(4321.5, payload["zaehlerstandEinspeisung"])

    def test_payload_EinspeisungNull_FeldVorhanden(self) -> None:
        payload = _to_payload(_reading(zaehlerstand_einspeisung=0.0))

        self.assertEqual(0.0, payload["zaehlerstandEinspeisung"])

    def test_payload_MitSeriennummer_Unveraendert(self) -> None:
        payload = _to_payload(_reading(seriennummer="WAGO-8791234"))

        self.assertEqual("WAGO-8791234", payload["seriennummer"])

    def test_payload_OhneSeriennummer_FeldFehlt(self) -> None:
        self.assertNotIn("seriennummer", _to_payload(_reading(seriennummer=None)))

    def test_payload_MitZustand_Uebernommen(self) -> None:
        payload = _to_payload(_reading(zustand={"soc": 87.5}))

        self.assertEqual({"soc": 87.5}, payload["zustand"])

    def test_payload_ZustandLeerOderNone_FeldFehlt(self) -> None:
        for zustand in ({}, None):
            with self.subTest(zustand=zustand):
                self.assertNotIn("zustand", _to_payload(_reading(zustand=zustand)))


class PublishTest(unittest.TestCase):

    def test_publish_Zev_TopicMesswertUndPayload(self) -> None:
        publisher, client = _publisher()
        reading = _reading(seriennummer="WAGO-8791234")

        self.assertTrue(publisher.publish(reading))

        topic, payload = client.publish.call_args.args
        self.assertEqual("zev/42/ID742-Wohnung-1/messwert", topic)
        self.assertEqual(_to_payload(reading), json.loads(payload))
        self.assertEqual(1, client.publish.call_args.kwargs["qos"])

    def test_publish_BrokerOffline_False(self) -> None:
        publisher, client = _publisher()
        client.publish.return_value = mock.Mock(rc=mqtt.MQTT_ERR_NO_CONN)

        with self.assertLogs("gateway.publisher", level="WARNING"):
            self.assertFalse(publisher.publish(_reading()))


class VerbindungTest(unittest.TestCase):
    """FR-5: automatischer Reconnect, vom paho-Loop getrieben."""

    def test_init_ReconnectMitBackoff(self) -> None:
        _, client = _publisher()

        client.reconnect_delay_set.assert_called_once_with(min_delay=1, max_delay=120)

    def test_connect_AsynchronUndLoopGestartet(self) -> None:
        publisher, client = _publisher()

        publisher.connect()

        client.connect_async.assert_called_once_with("nas.local", 1883, keepalive=60)
        client.loop_start.assert_called_once()
        client.connect.assert_not_called()  # blockierend - wuerde ohne Broker den Start stoppen

    def test_callbacks_VerbundenUndVerloren_IsConnected(self) -> None:
        publisher, _client = _publisher()
        erfolg = mock.Mock(is_failure=False)
        fehler = mock.Mock(is_failure=True)

        publisher._on_connect(None, None, None, erfolg)
        verbunden = publisher.is_connected()
        with self.assertLogs("gateway.publisher", level="WARNING"):
            publisher._on_disconnect(None, None, None, fehler)

        self.assertTrue(verbunden)
        self.assertFalse(publisher.is_connected())

    def test_init_ZugangsdatenUndTls_AusKonfiguration(self) -> None:
        broker = BrokerConfig(url="tls://nas.local", username="gateway", password="geheim")

        publisher, client = _publisher(broker)
        publisher.connect()

        client.username_pw_set.assert_called_once_with("gateway", "geheim")
        client.tls_set.assert_called_once()
        client.connect_async.assert_called_once_with("nas.local", 8883, keepalive=60)

    def test_init_OhneZugangsdaten_KeinLoginKeinTls(self) -> None:
        _, client = _publisher()

        client.username_pw_set.assert_not_called()
        client.tls_set.assert_not_called()


if __name__ == "__main__":
    unittest.main()
