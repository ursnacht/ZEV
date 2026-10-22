"""Laden und Validieren der Konfiguration (Specs/Pi-Gateway-Software.md, FR-1.5, FR-1.6, FR-2).

Ausführen (im Verzeichnis ``pi-gateway``): ``python -m unittest discover -s tests -t .``
"""

from __future__ import annotations

import os
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from gateway.config import ConfigError, _expand_env, load_config
from gateway.main import _build_readers
from gateway.models import GatewayConfig
from gateway.readers.modbus_reader import ModbusReader

_KOPF = (
    "org_id: 42\n"
    "publish_interval: 5m\n"
    "broker:\n"
    "  url: tcp://localhost:1883\n"
)


def _zaehler(messpunkt: str, zusatz: str = "", einspeisung: bool = True) -> str:
    """Ein Modbus-Zähler als YAML-Listeneintrag; ``zusatz`` wird als weitere Zeile eingefügt."""
    zeilen = [
        f"  - messpunkt: \"{messpunkt}\"\n",
        "    protokoll: modbus-tcp\n",
        "    host: 192.168.10.10\n",
    ]
    if zusatz:
        zeilen.append(f"    {zusatz}\n")
    zeilen.append("    register:\n")
    zeilen.append("      bezug: { addr: \"600C\" }\n")
    if einspeisung:
        zeilen.append("      einspeisung: { addr: \"6018\" }\n")
    return "".join(zeilen)


def _yaml(global_zusatz: str = "", zaehler: str | None = None) -> str:
    """Vollständige Konfiguration; ohne ``zaehler`` zwei Standard-Zähler."""
    if zaehler is None:
        zaehler = _zaehler("MP-1") + _zaehler("MP-2")
    return _KOPF + global_zusatz + "zaehler:\n" + zaehler


def _lade(yaml: str) -> GatewayConfig:
    """Schreibt ``yaml`` in eine Temp-Datei und lädt sie wie der Dienst beim Start."""
    with tempfile.TemporaryDirectory() as verzeichnis:
        pfad = Path(verzeichnis) / "config.yaml"
        pfad.write_text(yaml, encoding="utf-8")
        return load_config(pfad)


class ReadTimeoutConfigTest(unittest.TestCase):
    """FR-1.5: globales ``read_timeout`` mit Default 5 s, je Zähler überschreibbar."""

    def test_loadConfig_OhneReadTimeout_Default5sFuerAlle(self) -> None:
        config = _lade(_yaml())

        self.assertEqual(5.0, config.read_timeout_seconds)
        self.assertEqual([5.0, 5.0], [m.read_timeout_seconds for m in config.meters])

    def test_loadConfig_ReadTimeoutGesetzt_GiltFuerAlle(self) -> None:
        for angabe, erwartet in (("8", 8.0), ("2.5", 2.5), ("\"8s\"", 8.0), ("\"1m\"", 60.0)):
            with self.subTest(angabe=angabe):
                config = _lade(_yaml(f"read_timeout: {angabe}\n"))

                self.assertEqual(erwartet, config.read_timeout_seconds)
                self.assertEqual([erwartet, erwartet],
                                 [m.read_timeout_seconds for m in config.meters])

    def test_loadConfig_ZaehlerReadTimeout_NurFuerDiesenZaehler(self) -> None:
        zaehler = _zaehler("MP-1") + _zaehler("MP-Langsam", "read_timeout: 12s") + _zaehler("MP-3")

        config = _lade(_yaml("read_timeout: 8s\n", zaehler))

        self.assertEqual(8.0, config.read_timeout_seconds)
        self.assertEqual({"MP-1": 8.0, "MP-Langsam": 12.0, "MP-3": 8.0},
                         {m.messpunkt: m.read_timeout_seconds for m in config.meters})

    def test_loadConfig_UngueltigesReadTimeout_ConfigError(self) -> None:
        for angabe in ("0", "-1", "\"0s\"", "\"-5s\"", "abc", "\"5x\"", "true", "false", "[5]"):
            with self.subTest(angabe=angabe), self.assertRaises(ConfigError):
                _lade(_yaml(f"read_timeout: {angabe}\n"))

    def test_loadConfig_UngueltigesZaehlerReadTimeout_ConfigError(self) -> None:
        for angabe in ("0", "-2", "abc", "true"):
            with self.subTest(angabe=angabe):
                with self.assertRaises(ConfigError) as kontext:
                    _lade(_yaml(zaehler=_zaehler("MP-1", f"read_timeout: {angabe}")))

                self.assertIn("MP-1", str(kontext.exception))


class ReadAttemptsConfigTest(unittest.TestCase):
    """FR-1.6: ``read_attempts`` (inkl. Erstversuch), Default 2."""

    def test_loadConfig_OhneReadAttempts_Default2(self) -> None:
        self.assertEqual(2, _lade(_yaml()).read_attempts)

    def test_loadConfig_ReadAttemptsGesetzt_Uebernommen(self) -> None:
        for angabe in (1, 3):
            with self.subTest(angabe=angabe):
                self.assertEqual(angabe, _lade(_yaml(f"read_attempts: {angabe}\n")).read_attempts)

    def test_loadConfig_UngueltigeReadAttempts_ConfigError(self) -> None:
        for angabe in ("0", "-1", "2.5", "true", "false", "\"2\"", "zwei"):
            with self.subTest(angabe=angabe), self.assertRaises(ConfigError):
                _lade(_yaml(f"read_attempts: {angabe}\n"))


class RegisterConfigTest(unittest.TestCase):
    """FR-1.4: ``register.einspeisung`` optional, ``register.bezug`` Pflicht."""

    def test_loadConfig_OhneEinspeisung_RegisterNone(self) -> None:
        config = _lade(_yaml(zaehler=_zaehler("MP-Konsument", einspeisung=False)))

        self.assertIsNone(config.meters[0].register_einspeisung)
        self.assertEqual(0x600C, config.meters[0].register_bezug.addr)

    def test_loadConfig_MitEinspeisung_RegisterGelesen(self) -> None:
        config = _lade(_yaml())

        self.assertEqual(0x6018, config.meters[0].register_einspeisung.addr)

    def test_loadConfig_FehlerhafterEinspeisungBlock_ConfigError(self) -> None:
        # Tippfehler 'adr' statt 'addr' darf nicht still zu 0 werden
        zaehler = (
            "  - messpunkt: \"MP-1\"\n"
            "    protokoll: modbus-tcp\n"
            "    host: 192.168.10.10\n"
            "    register:\n"
            "      bezug: { addr: \"600C\" }\n"
            "      einspeisung: { adr: \"6018\" }\n"
        )
        with self.assertRaises(ConfigError) as kontext:
            _lade(_yaml(zaehler=zaehler))

        self.assertIn("einspeisung", str(kontext.exception))

    def test_loadConfig_EinspeisungKeinMapping_ConfigError(self) -> None:
        zaehler = (
            "  - messpunkt: \"MP-1\"\n"
            "    protokoll: modbus-tcp\n"
            "    host: 192.168.10.10\n"
            "    register:\n"
            "      bezug: { addr: \"600C\" }\n"
            "      einspeisung: \"6018\"\n"
        )
        with self.assertRaises(ConfigError):
            _lade(_yaml(zaehler=zaehler))

    def test_loadConfig_OhneBezug_ConfigError(self) -> None:
        zaehler = (
            "  - messpunkt: \"MP-1\"\n"
            "    protokoll: modbus-tcp\n"
            "    host: 192.168.10.10\n"
            "    register:\n"
            "      einspeisung: { addr: \"6018\" }\n"
        )
        with self.assertRaises(ConfigError) as kontext:
            _lade(_yaml(zaehler=zaehler))

        self.assertIn("bezug", str(kontext.exception))


class UmgebungsvariablenTest(unittest.TestCase):
    """FR-4: Secrets kommen als ``${VAR}`` aus der Umgebung, nicht aus der Datei."""

    def test_loadConfig_PlatzhalterImBroker_AusUmgebungAufgeloest(self) -> None:
        yaml = _yaml().replace(
            "  url: tcp://localhost:1883\n",
            "  url: tcp://localhost:1883\n"
            "  username: ${ZEV_TEST_MQTT_USER}\n"
            "  password: \"${ZEV_TEST_MQTT_PASSWORD}\"\n",
        )
        umgebung = {"ZEV_TEST_MQTT_USER": "gateway", "ZEV_TEST_MQTT_PASSWORD": "geheim"}
        with mock.patch.dict(os.environ, umgebung):
            config = _lade(yaml)

        self.assertEqual("gateway", config.broker.username)
        self.assertEqual("geheim", config.broker.password)

    def test_loadConfig_FehlendeUmgebungsvariable_ConfigError(self) -> None:
        yaml = _yaml().replace(
            "  url: tcp://localhost:1883\n",
            "  url: tcp://localhost:1883\n  password: ${ZEV_TEST_NICHT_GESETZT}\n",
        )
        with mock.patch.dict(os.environ, {}, clear=False):
            os.environ.pop("ZEV_TEST_NICHT_GESETZT", None)
            with self.assertRaises(ConfigError) as kontext:
                _lade(yaml)

        self.assertIn("ZEV_TEST_NICHT_GESETZT", str(kontext.exception))

    def test_expandEnv_Verschachtelt_ErsetztInDictsUndListen(self) -> None:
        with mock.patch.dict(os.environ, {"ZEV_TEST_HOST": "hub"}):
            ergebnis = _expand_env({"a": ["x-${ZEV_TEST_HOST}-y", 5], "b": {"c": "${ZEV_TEST_HOST}"}})

        self.assertEqual({"a": ["x-hub-y", 5], "b": {"c": "hub"}}, ergebnis)


class ZaehlerListeTest(unittest.TestCase):
    """FR-2: Zähler-Liste beliebiger Länge; ein neuer Zähler = ein Konfigurations-Eintrag."""

    def test_loadConfig_ZwanzigZaehler_AlleGeladenUndReaderErzeugt(self) -> None:
        zaehler = "".join(_zaehler(f"MP-{nummer}", f"unit_id: {nummer}") for nummer in range(1, 21))

        config = _lade(_yaml(zaehler=zaehler))
        with mock.patch("gateway.readers.modbus_reader.ModbusTcpClient"):
            readers = _build_readers(config)

        self.assertEqual(20, len(config.meters))
        self.assertEqual(list(range(1, 21)), [m.unit_id for m in config.meters])
        self.assertEqual(20, len(readers))
        self.assertTrue(all(isinstance(reader, ModbusReader) for reader in readers))

    def test_loadConfig_DoppelterMesspunkt_ConfigError(self) -> None:
        with self.assertRaises(ConfigError):
            _lade(_yaml(zaehler=_zaehler("MP-1") + _zaehler("MP-1")))


if __name__ == "__main__":
    unittest.main()


class UngueltigeWerteConfigTest(unittest.TestCase):
    """Ungueltige Angaben brechen den Start mit ConfigError ab - nicht mit einem ValueError, den
    main() nicht als Konfigurationsfehler erkennt, und nicht still mit einem unsinnigen Wert."""

    def test_loadConfig_ReadTimeoutNanOderInf_ConfigError(self) -> None:
        for angabe in (".nan", ".inf", "\"nan\"", "\"inf\"", "\"infs\""):
            with self.subTest(angabe=angabe):
                with self.assertRaises(ConfigError):
                    _lade(_yaml(f"read_timeout: {angabe}\n"))

    def test_loadConfig_PortOderUnitIdKeineZahl_ConfigError(self) -> None:
        for zusatz in ("port: abc", "port: 2.5", "port: true", "port: 0", "port: 70000",
                       "unit_id: abc", "unit_id: true", "unit_id: 256", "unit_id: -1"):
            with self.subTest(zusatz=zusatz):
                with self.assertRaises(ConfigError):
                    _lade(_yaml(zaehler=_zaehler("MP-1", zusatz)))

    def test_loadConfig_PortUndUnitIdAlsText_Uebernommen(self) -> None:
        # So kommen Zahlen nach ${VAR}-Aufloesung an
        config = _lade(_yaml(zaehler=_zaehler("MP-1", "port: \"1502\"") .replace(
            "    protokoll: modbus-tcp\n", "    protokoll: modbus-tcp\n    unit_id: \"7\"\n")))

        self.assertEqual(1502, config.meters[0].port)
        self.assertEqual(7, config.meters[0].unit_id)

    def test_loadConfig_OhnePortUndUnitId_Defaults(self) -> None:
        config = _lade(_yaml())

        self.assertEqual(502, config.meters[0].port)
        self.assertEqual(1, config.meters[0].unit_id)

    def test_loadConfig_SkalierungUngueltig_ConfigError(self) -> None:
        for skalierung in ("abc", "0", "-0.1", ".nan", ".inf", "true"):
            with self.subTest(skalierung=skalierung):
                zaehler = _zaehler("MP-1").replace(
                    "bezug: { addr: \"600C\" }", f"bezug: {{ addr: \"600C\", skalierung: {skalierung} }}")
                with self.assertRaises(ConfigError):
                    _lade(_yaml(zaehler=zaehler))

    def test_loadConfig_SkalierungGueltig_Uebernommen(self) -> None:
        zaehler = _zaehler("MP-1").replace(
            "bezug: { addr: \"600C\" }", "bezug: { addr: \"600C\", skalierung: 0.1 }")

        config = _lade(_yaml(zaehler=zaehler))

        self.assertEqual(0.1, config.meters[0].register_bezug.skalierung)

    def test_loadConfig_OrgIdBoolean_ConfigError(self) -> None:
        with self.assertRaises(ConfigError):
            _lade(_yaml().replace("org_id: 42", "org_id: true"))

