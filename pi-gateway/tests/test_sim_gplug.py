"""Simulator mit gPlug-Meldungen (Specs/Pi-Gateway-Software.md, FR-8.3).

Ausführen (im Verzeichnis ``pi-gateway``): ``python -m unittest discover -s tests -t .``
"""

from __future__ import annotations

import json
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path
from unittest import mock

import paho.mqtt.client as mqtt

from gateway.config import load_config
from gateway.models import BrokerConfig, MeterConfig, MeterReading, RegisterSpec
from gateway.publisher import MqttPublisher, _to_gplug_payload
from gateway.readers.factory import create_reader
from gateway.readers.sim_reader import SimReader


def _meter(messpunkt: str = "MP-Nachbar-1", protokoll: str = "sim-gplug",
           seriennummer: str | None = "GPLUG-0001") -> MeterConfig:
    return MeterConfig(
        messpunkt=messpunkt,
        protokoll=protokoll,
        register_bezug=RegisterSpec(addr=0),
        register_einspeisung=RegisterSpec(addr=0),
        seriennummer=seriennummer,
    )


def _reading(**abweichend) -> MeterReading:
    werte = dict(
        messpunkt="MP-Nachbar-1",
        timestamp=datetime(2026, 10, 20, 12, 30, 0, 123456, tzinfo=timezone.utc),
        zaehlerstand_bezug=12345.67891,
        zaehlerstand_einspeisung=4321.00049,
        seriennummer="GPLUG-0001",
        meldung="gplug",
        leistung_bezug_kw=0.512,
        leistung_einspeisung_kw=0.0,
    )
    werte.update(abweichend)
    return MeterReading(**werte)


class SimGplugReaderTest(unittest.TestCase):

    def test_factory_SimGplug_ErzeugtSimReader(self) -> None:
        self.assertIsInstance(create_reader(_meter()), SimReader)

    def test_read_SimGplug_MeldungGplugOhneZustand(self) -> None:
        # Auch ein Speicher-Name erzeugt bei sim-gplug keinen Ladezustand - ein gPlug hat keinen.
        reader = create_reader(_meter(messpunkt="Batterie-Nachbar"))

        reading = reader.read()

        self.assertEqual("gplug", reading.meldung)
        self.assertIsNone(reading.zustand)
        self.assertEqual("GPLUG-0001", reading.seriennummer)

    def test_read_ErsteMeldung_LeistungNull(self) -> None:
        reading = create_reader(_meter()).read()

        self.assertEqual(0.0, reading.leistung_bezug_kw)
        self.assertEqual(0.0, reading.leistung_einspeisung_kw)

    def test_read_FolgeMeldung_LeistungAusZuwachs(self) -> None:
        reader = create_reader(_meter())
        with mock.patch("gateway.readers.sim_reader.time.monotonic", side_effect=[1000.0, 1036.0]):
            erste = reader.read()
            zweite = reader.read()

        # 36 s = 0.01 h: kW = Zuwachs in kWh / 0.01 h
        erwartet = round((zweite.zaehlerstand_bezug - erste.zaehlerstand_bezug) / 0.01, 3)
        self.assertAlmostEqual(erwartet, zweite.leistung_bezug_kw, places=1)
        self.assertGreater(zweite.leistung_bezug_kw, 0.0)

    def test_read_SimGplug_StaendeSteigenMonoton(self) -> None:
        reader = create_reader(_meter())

        staende = [reader.read().zaehlerstand_bezug for _ in range(5)]

        self.assertEqual(sorted(staende), staende)
        self.assertGreater(staende[-1], staende[0])

    def test_read_Sim_MeldungBleibtZev(self) -> None:
        reading = create_reader(_meter(protokoll="sim")).read()

        self.assertEqual("zev", reading.meldung)
        self.assertIsNone(reading.leistung_bezug_kw)


class GplugPayloadTest(unittest.TestCase):

    def test_payload_TimeLokalOhneOffsetOhneMikrosekunden(self) -> None:
        payload = _to_gplug_payload(_reading())

        lokal = datetime(2026, 10, 20, 12, 30, 0, tzinfo=timezone.utc).astimezone()
        self.assertEqual(lokal.replace(tzinfo=None).isoformat(), payload["Time"])
        self.assertNotIn("+", payload["Time"])
        self.assertNotIn("Z", payload["Time"])

    def test_payload_ZaehlerstaendeInKwhMitDreiStellen(self) -> None:
        z = _to_gplug_payload(_reading())["z"]

        self.assertEqual(12345.679, z["Ei"])
        self.assertEqual(4321.0, z["Eo"])
        self.assertEqual("GPLUG-0001", z["SMid"])
        self.assertEqual(0.512, z["Pi"])
        self.assertEqual(0.0, z["Po"])

    def test_payload_TarifregisterSummierenSich(self) -> None:
        z = _to_gplug_payload(_reading())["z"]

        self.assertAlmostEqual(z["Ei"], z["Ei1"] + z["Ei2"])
        self.assertAlmostEqual(z["Eo"], z["Eo1"] + z["Eo2"])

    def test_payload_OhneSeriennummer_KeinSMid(self) -> None:
        z = _to_gplug_payload(_reading(seriennummer=None))["z"]

        self.assertNotIn("SMid", z)


class GplugPublishTest(unittest.TestCase):

    def _publisher(self) -> tuple[MqttPublisher, mock.Mock]:
        with mock.patch("gateway.publisher.mqtt.Client") as client_klasse:
            client = client_klasse.return_value
            client.publish.return_value = mock.Mock(rc=mqtt.MQTT_ERR_SUCCESS)
            publisher = MqttPublisher(42, BrokerConfig(url="tcp://localhost:1883", qos=1))
        return publisher, client

    def test_publish_Gplug_TopicSensorUndQos0(self) -> None:
        publisher, client = self._publisher()

        self.assertTrue(publisher.publish(_reading()))

        topic, payload = client.publish.call_args.args
        self.assertEqual("gPlug/42/MP-Nachbar-1/tele/SENSOR", topic)
        self.assertEqual(0, client.publish.call_args.kwargs["qos"])
        self.assertEqual({"Time", "z"}, set(json.loads(payload)))

    def test_publish_Zev_UnveraendertTopicMesswertUndBrokerQos(self) -> None:
        publisher, client = self._publisher()

        publisher.publish(_reading(meldung="zev", leistung_bezug_kw=None, leistung_einspeisung_kw=None))

        topic, payload = client.publish.call_args.args
        self.assertEqual("zev/42/MP-Nachbar-1/messwert", topic)
        self.assertEqual(1, client.publish.call_args.kwargs["qos"])
        self.assertIn("zaehlerstandBezug", json.loads(payload))


class SimGplugConfigTest(unittest.TestCase):

    def test_loadConfig_SimGplugOhneHostUndRegister_Gueltig(self) -> None:
        yaml = (
            "org_id: 42\n"
            "publish_interval: 30s\n"
            "broker:\n"
            "  url: tcp://localhost:1883\n"
            "zaehler:\n"
            "  - { messpunkt: \"MP-Nachbar-1\", protokoll: sim-gplug, seriennummer: \"GPLUG-0001\" }\n"
        )
        with tempfile.TemporaryDirectory() as verzeichnis:
            pfad = Path(verzeichnis) / "config.yaml"
            pfad.write_text(yaml, encoding="utf-8")

            config = load_config(pfad)

        self.assertEqual("sim-gplug", config.meters[0].protokoll)
        self.assertEqual("GPLUG-0001", config.meters[0].seriennummer)


if __name__ == "__main__":
    unittest.main()
