"""Solar-Log über die JSON-Schnittstelle lesen (Specs/Pi-Gateway-Software.md, FR-9).

Ausführen (im Verzeichnis ``pi-gateway``): ``python -m unittest discover -s tests -t .``
"""

from __future__ import annotations

import http.client
import json
import unittest
import urllib.error
from datetime import datetime, timezone
from unittest import mock

from gateway.config import ConfigError
from gateway.main import _build_readers, _read_with_retry
from gateway.models import GatewayConfig, MeterConfig, ReadError, RegisterSpec
from gateway.readers.solarlog_reader import SolarLogReader
from tests.test_config import _KOPF
from tests.test_config import _lade as _lade_yaml

_URLOPEN = "gateway.readers.solarlog_reader.urllib.request.urlopen"
_JETZT = "gateway.readers.solarlog_reader._jetzt"

# Die Zeit des Pi in allen Tests: eine halbe Minute nach dem Zeitstempel der Beispielantwort.
_PI_ZEIT = datetime(2026, 11, 21, 20, 15, 0)

# Antwort des Solar-Log vom 21.11.2026 (README, Abschnitt "Solar-Log").
_WERTE = {
    "100": "21.11.26 20:14:30", "101": 0, "102": 0, "103": 0, "104": 0,
    "105": 18320, "106": 7728, "107": 240848, "108": 11062328, "109": 71366333,
    "110": 2382, "111": 13294, "112": 18724, "113": 438558, "114": 6268664,
    "115": 44092261, "116": 9230,
}


def _antwort(werte=None, rumpf: bytes | None = None) -> mock.MagicMock:
    """Eine HTTP-Antwort, wie ``urlopen`` sie als Kontextmanager liefert."""
    if rumpf is None:
        rumpf = json.dumps({"801": {"170": _WERTE if werte is None else werte}}).encode()
    antwort = mock.MagicMock()
    antwort.__enter__.return_value.read.return_value = rumpf
    return antwort


# Bewusst nicht der Default 5 s: So zeigt der Test, dass der Wert DIESES Zählers hinausgeht.
_LESE_TIMEOUT = 8.0


def _reader(bezug: int | None = None, einspeisung: int | None = 109,
            max_alter: float = 900.0) -> SolarLogReader:
    return SolarLogReader(MeterConfig(
        messpunkt="PV-SolarLog",
        protokoll="solarlog-http",
        register_bezug=RegisterSpec(addr=0),
        register_einspeisung=None,
        host="192.168.20.90",
        port=8081,
        read_timeout_seconds=_LESE_TIMEOUT,
        schluessel_bezug=bezug,
        schluessel_einspeisung=einspeisung,
        max_alter_seconds=max_alter,
    ))


class SolarLogReaderTest(unittest.TestCase):

    def setUp(self) -> None:
        patcher = mock.patch(_JETZT, return_value=_PI_ZEIT)
        patcher.start()
        self.addCleanup(patcher.stop)

    def test_read_Ertrag_AlsEinspeisungInKwh(self) -> None:
        """Der Ertrag (Schlüssel 109, Wh) kommt als Einspeisung in kWh heraus, Bezug ist 0.

        Im Feld Einspeisung, weil das Backend ``total = ΔBezug − ΔEinspeisung`` rechnet und ein
        Produzent negatives ``total`` trägt. Unter Bezug publiziert, zählte die Anlage als
        Verbraucher.
        """
        with mock.patch(_URLOPEN, return_value=_antwort()):
            reading = _reader().read()

        self.assertEqual("PV-SolarLog", reading.messpunkt)
        self.assertAlmostEqual(71366.333, reading.zaehlerstand_einspeisung, places=6)
        self.assertEqual(0.0, reading.zaehlerstand_bezug)
        self.assertEqual("zev", reading.meldung)
        self.assertIsNone(reading.zustand)

    def test_read_Verbrauch_AlsBezugInKwh(self) -> None:
        """Der Verbrauch (Schlüssel 115) kommt als Bezug heraus, Einspeisung ist 0."""
        with mock.patch(_URLOPEN, return_value=_antwort()):
            reading = _reader(bezug=115, einspeisung=None).read()

        self.assertAlmostEqual(44092.261, reading.zaehlerstand_bezug, places=6)
        self.assertEqual(0.0, reading.zaehlerstand_einspeisung)

    def test_read_BeideSchluessel_BeideStaende(self) -> None:
        with mock.patch(_URLOPEN, return_value=_antwort()):
            reading = _reader(bezug=115, einspeisung=109).read()

        self.assertAlmostEqual(44092.261, reading.zaehlerstand_bezug, places=6)
        self.assertAlmostEqual(71366.333, reading.zaehlerstand_einspeisung, places=6)

    def test_read_FragtPerPostAnGetjpMitLeseTimeout(self) -> None:
        """Genau eine Anfrage je Read: POST an /getjp, mit dem Lese-Timeout des Zählers."""
        with mock.patch(_URLOPEN, return_value=_antwort()) as urlopen:
            _reader().read()

        urlopen.assert_called_once()
        anfrage = urlopen.call_args.args[0]
        self.assertEqual("http://192.168.20.90:8081/getjp", anfrage.full_url)
        self.assertEqual("POST", anfrage.get_method())
        self.assertEqual({"801": {"170": None}}, json.loads(anfrage.data))
        self.assertEqual(_LESE_TIMEOUT, urlopen.call_args.kwargs["timeout"])

    def test_read_ZeitstempelDesPi_NichtDesSolarLog(self) -> None:
        """Publiziert wird die Zeit des Pi (mit Zone) - der Stempel des Solar-Log hat keine.

        Der Stempel der Antwort liegt Jahre zurück (``max_alter=0`` lässt ihn durch). Käme er in
        den Payload, läge ``timestamp`` nicht zwischen den beiden Messungen um den Read herum.
        """
        werte = {**_WERTE, "100": "01.01.20 00:00:00"}
        vorher = datetime.now(timezone.utc)
        with mock.patch(_URLOPEN, return_value=_antwort(werte=werte)):
            reading = _reader(max_alter=0).read()
        nachher = datetime.now(timezone.utc)

        self.assertIsNotNone(reading.timestamp.tzinfo)
        self.assertTrue(vorher <= reading.timestamp <= nachher)

    # --- Fehler der Verbindung und der Antwort -------------------------------------

    def test_read_NichtErreichbar_ReadErrorMitUrlUndTimeout(self) -> None:
        with mock.patch(_URLOPEN, side_effect=urllib.error.URLError("refused")):
            with self.assertRaises(ReadError) as kontext:
                _reader().read()

        self.assertIn("http://192.168.20.90:8081/getjp", str(kontext.exception))
        self.assertIn("8.0s", str(kontext.exception))

    def test_read_Zeitueberschreitung_ReadError(self) -> None:
        """Eine Zeitüberschreitung beim Lesen kommt als TimeoutError, nicht als URLError."""
        with mock.patch(_URLOPEN, side_effect=TimeoutError("timed out")):
            with self.assertRaises(ReadError):
                _reader().read()

    def test_read_AbgebrocheneOderKeineHttpAntwort_ReadError(self) -> None:
        """Bricht die Verbindung mitten im Rumpf ab oder antwortet das Gerät nicht mit HTTP,
        ist das ein gescheiterter Read - kein unerwarteter Fehler, der nie wiederholt würde."""
        for fehler in (http.client.IncompleteRead(b'{"801"'), http.client.BadStatusLine("x")):
            with self.subTest(fehler=type(fehler).__name__):
                with mock.patch(_URLOPEN, side_effect=fehler):
                    with self.assertRaises(ReadError):
                        _reader().read()

    def test_readWithRetry_AbgebrocheneAntwortDannErfolg_Wiederholt(self) -> None:
        with mock.patch(_URLOPEN, side_effect=[http.client.IncompleteRead(b""), _antwort()]) \
                as urlopen, mock.patch("gateway.main.time.sleep"):
            reading = _read_with_retry(_reader(), attempts=2)

        self.assertEqual(2, urlopen.call_count)
        self.assertIsNotNone(reading)

    def test_read_KeinJson_ReadError(self) -> None:
        with mock.patch(_URLOPEN, return_value=_antwort(rumpf=b"<html>Solar-Log</html>")):
            with self.assertRaises(ReadError) as kontext:
                _reader().read()

        self.assertIn("801/170", str(kontext.exception))

    def test_read_BlockFehlt_ReadError(self) -> None:
        with mock.patch(_URLOPEN, return_value=_antwort(rumpf=b'{"801":{}}')):
            with self.assertRaises(ReadError):
                _reader().read()

    def test_read_ZugriffVerweigert_ReadErrorNenntDenText(self) -> None:
        """Mit Anmeldepflicht steht statt der Werte ein Text - er gehört in die Meldung."""
        with mock.patch(_URLOPEN, return_value=_antwort(werte="ACCESS DENIED")):
            with self.assertRaises(ReadError) as kontext:
                _reader().read()

        self.assertIn("ACCESS DENIED", str(kontext.exception))

    def test_read_SchluesselFehlt_ReadErrorNenntSchluessel(self) -> None:
        """Ein fehlender Schlüssel wird NICHT als 0 publiziert - der Zähler spränge zurück."""
        werte = {k: v for k, v in _WERTE.items() if k != "109"}
        with mock.patch(_URLOPEN, return_value=_antwort(werte=werte)):
            with self.assertRaises(ReadError) as kontext:
                _reader().read()

        self.assertIn("109", str(kontext.exception))

    def test_read_WertKeineZahl_ReadError(self) -> None:
        for wert in ("71366333", None, True):
            with self.subTest(wert=wert):
                with mock.patch(_URLOPEN, return_value=_antwort(werte={**_WERTE, "109": wert})):
                    with self.assertRaises(ReadError):
                        _reader().read()

    def test_read_NegativerStand_ReadError(self) -> None:
        with mock.patch(_URLOPEN, return_value=_antwort(werte={**_WERTE, "109": -1})):
            with self.assertRaises(ReadError):
                _reader().read()

    # --- Alter der Werte -------------------------------------------------------------

    def test_read_WerteZuAlt_ReadError(self) -> None:
        """Ein stehendes Solar-Log wird verworfen statt als „keine Produktion“ publiziert."""
        werte = {**_WERTE, "100": "21.11.26 19:59:59"}   # 15 Minuten und 1 Sekunde alt
        with mock.patch(_URLOPEN, return_value=_antwort(werte=werte)):
            with self.assertRaises(ReadError) as kontext:
                _reader().read()

        self.assertIn("21.11.26 19:59:59", str(kontext.exception))

    def test_read_WerteGenauAnDerGrenze_Publiziert(self) -> None:
        werte = {**_WERTE, "100": "21.11.26 20:00:00"}   # genau 15 Minuten
        with mock.patch(_URLOPEN, return_value=_antwort(werte=werte)):
            reading = _reader().read()

        self.assertAlmostEqual(71366.333, reading.zaehlerstand_einspeisung, places=6)

    def test_read_ZeitstempelInDerZukunft_ReadError(self) -> None:
        """Eine Stunde voraus heisst: Uhr oder Zone passen nicht - auch das ist ein Befund."""
        werte = {**_WERTE, "100": "21.11.26 21:15:00"}
        with mock.patch(_URLOPEN, return_value=_antwort(werte=werte)):
            with self.assertRaises(ReadError):
                _reader().read()

    def test_read_ZeitstempelNichtLesbar_ReadErrorMitHinweis(self) -> None:
        werte = {**_WERTE, "100": "2026-11-21T20:14:30"}
        with mock.patch(_URLOPEN, return_value=_antwort(werte=werte)):
            with self.assertRaises(ReadError) as kontext:
                _reader().read()

        self.assertIn("max_alter: 0", str(kontext.exception))

    def test_read_MaxAlterNull_KeinePruefung(self) -> None:
        """Mit ``max_alter: 0`` zählt weder ein alter noch ein unlesbarer Zeitstempel."""
        for stempel in ("01.01.20 00:00:00", "unlesbar", None):
            with self.subTest(stempel=stempel):
                werte = {**_WERTE, "100": stempel}
                with mock.patch(_URLOPEN, return_value=_antwort(werte=werte)):
                    reading = _reader(max_alter=0).read()
                self.assertAlmostEqual(71366.333, reading.zaehlerstand_einspeisung, places=6)

    # --- Zusammenspiel mit dem Retry -------------------------------------------------

    def test_readWithRetry_ErstFehlerDannAntwort_LiefertStand(self) -> None:
        """Ein Fehler des Solar-Log wird wie ein Modbus-Fehler wiederholt (FR-1.6)."""
        with mock.patch(_URLOPEN, side_effect=[urllib.error.URLError("reset"), _antwort()]), \
                mock.patch("gateway.main.time.sleep"):
            reading = _read_with_retry(_reader(), attempts=2)

        self.assertIsNotNone(reading)
        self.assertAlmostEqual(71366.333, reading.zaehlerstand_einspeisung, places=6)

    def test_init_NurEinSchluessel_InfoZeileFuerDasFehlendeFeld(self) -> None:
        """Beim Start steht im Log, welches Feld als 0 hinausgeht (wie FR-1.7)."""
        with self.assertLogs("gateway.readers.solarlog_reader", level="INFO") as log:
            _reader(bezug=None, einspeisung=109)
        self.assertEqual(1, len(log.output))
        self.assertIn("bezug", log.output[0])
        self.assertIn("PV-SolarLog", log.output[0])

        # Die andere Richtung: der Verbrauchs-Eintrag ohne Schluessel fuer die Einspeisung.
        with self.assertLogs("gateway.readers.solarlog_reader", level="INFO") as log:
            _reader(bezug=115, einspeisung=None)
        self.assertEqual(1, len(log.output))
        self.assertIn("einspeisung", log.output[0])

        # Mit beiden Schluesseln keine Zeile. Ueber einen Mock statt assertNoLogs: Das gibt es
        # erst ab Python 3.10, das Projekt laeuft ab 3.9 (pyproject.toml).
        with mock.patch("gateway.readers.solarlog_reader.log") as log_mock:
            _reader(bezug=115, einspeisung=109)
        log_mock.info.assert_not_called()

    def test_init_OhneHostOderSchluessel_ValueError(self) -> None:
        with self.assertRaises(ValueError):
            _reader(bezug=None, einspeisung=None)
        with self.assertRaises(ValueError):
            SolarLogReader(MeterConfig(
                messpunkt="X", protokoll="solarlog-http", register_bezug=RegisterSpec(addr=0),
                register_einspeisung=None, schluessel_einspeisung=109))


def _eintrag(*zeilen: str) -> str:
    """Ein Solar-Log-Zähler als YAML-Listeneintrag; ``zeilen`` ersetzen den Schlüssel-Block."""
    kopf = [
        "  - messpunkt: \"PV-SolarLog\"\n",
        "    protokoll: solarlog-http\n",
    ]
    return "".join(kopf + [f"    {zeile}\n" for zeile in zeilen])


def _lade(zaehler: str) -> GatewayConfig:
    """Lädt eine Konfiguration mit dem gegebenen Zähler-Eintrag (Helfer aus ``test_config``)."""
    return _lade_yaml(_KOPF + "zaehler:\n" + zaehler)


class SolarLogConfigTest(unittest.TestCase):

    def test_loadConfig_SolarLog_OhneRegisterGueltig(self) -> None:
        """Ein Solar-Log-Zähler braucht keinen ``register``-Block: ``host`` und ``schluessel``."""
        config = _lade(_eintrag("host: 192.168.20.90", "port: 8081", "schluessel:",
                                "  einspeisung: 109"))

        zaehler = config.meters[0]
        self.assertEqual("solarlog-http", zaehler.protokoll)
        self.assertEqual(8081, zaehler.port)
        self.assertEqual(109, zaehler.schluessel_einspeisung)
        self.assertIsNone(zaehler.schluessel_bezug)
        self.assertEqual(900.0, zaehler.max_alter_seconds)

    def test_loadConfig_OhnePort_Default80(self) -> None:
        """Port 80, nicht der Modbus-Default 502 - dort lauscht kein Solar-Log."""
        config = _lade(_eintrag("host: 192.168.20.90", "schluessel: { bezug: 115 }"))

        self.assertEqual(80, config.meters[0].port)
        self.assertEqual(115, config.meters[0].schluessel_bezug)

    def test_loadConfig_ModbusOhnePort_Weiterhin502(self) -> None:
        config = _lade("  - messpunkt: \"Wago\"\n    protokoll: modbus-tcp\n    host: 1.2.3.4\n"
                       "    register:\n      bezug: { addr: \"600C\" }\n")

        self.assertEqual(502, config.meters[0].port)
        self.assertIsNone(config.meters[0].schluessel_bezug)

    def test_loadConfig_SchluesselAlsText_Uebernommen(self) -> None:
        config = _lade(_eintrag("host: h", "schluessel: { einspeisung: \"109\" }"))

        self.assertEqual(109, config.meters[0].schluessel_einspeisung)

    def test_loadConfig_OhneSchluessel_ConfigError(self) -> None:
        """Ohne Schlüssel publizierte der Eintrag dauerhaft 0."""
        for zeilen in (("host: h",), ("host: h", "schluessel: {}"), ("host: h", "schluessel: 109")):
            with self.subTest(zeilen=zeilen):
                with self.assertRaises(ConfigError):
                    _lade(_eintrag(*zeilen))

    def test_loadConfig_UnbekannterSchluesselname_ConfigError(self) -> None:
        """Ein Tippfehler darf nicht stillschweigend zu 0 werden."""
        with self.assertRaises(ConfigError) as kontext:
            _lade(_eintrag("host: h", "schluessel: { einspeisng: 109 }"))

        self.assertIn("einspeisng", str(kontext.exception))

    def test_loadConfig_SchluesselKeineZahl_ConfigError(self) -> None:
        for wert in ("ertrag", "true", "-1", "1.5"):
            with self.subTest(wert=wert):
                with self.assertRaises(ConfigError):
                    _lade(_eintrag("host: h", f"schluessel: {{ einspeisung: {wert} }}"))

    def test_loadConfig_OhneHost_ConfigError(self) -> None:
        with self.assertRaises(ConfigError):
            _lade(_eintrag("schluessel: { einspeisung: 109 }"))

    def test_loadConfig_ModbusAngabenAmSolarLog_ConfigError(self) -> None:
        """``register``, ``zustand`` und ``unit_id`` gibt es hier nicht - ein kopierter Block
        darf nicht stillschweigend wirkungslos bleiben."""
        for fremd in ("register: { bezug: { addr: \"600C\" } }", "zustand: { soc: { addr: 1 } }",
                      "unit_id: 1"):
            with self.subTest(fremd=fremd):
                with self.assertRaises(ConfigError) as kontext:
                    _lade(_eintrag("host: h", fremd, "schluessel: { einspeisung: 109 }"))
                # Der Name der fremden Angabe steht in der Meldung - sie kommt also aus dieser
                # Pruefung und nicht aus dem Parsen des schluessel-Blocks.
                self.assertIn(fremd.split(":")[0], str(kontext.exception))

    def test_loadConfig_MaxAlter_KurzformUndNull(self) -> None:
        """``0`` schaltet die Prüfung aus und ist - anders als bei ``read_timeout`` - gültig."""
        for angabe, erwartet in (("5m", 300.0), ("120", 120.0), ("0", 0.0), ("0.0", 0.0),
                                 ("\"0s\"", 0.0), ("\"0S\"", 0.0)):
            with self.subTest(angabe=angabe):
                config = _lade(_eintrag("host: h", f"max_alter: {angabe}",
                                        "schluessel: { einspeisung: 109 }"))
                self.assertEqual(erwartet, config.meters[0].max_alter_seconds)

    def test_loadConfig_MaxAlterUngueltig_ConfigError(self) -> None:
        for angabe in ("-5", "bald", "true"):
            with self.subTest(angabe=angabe):
                with self.assertRaises(ConfigError):
                    _lade(_eintrag("host: h", f"max_alter: {angabe}",
                                   "schluessel: { einspeisung: 109 }"))

    def test_loadConfig_MaxAlterNegativ_MeldungNenntNullAlsErlaubt(self) -> None:
        """Die Meldung darf nicht „> 0“ verlangen - 0 ist hier gültig."""
        with self.assertRaises(ConfigError) as kontext:
            _lade(_eintrag("host: h", "max_alter: -5", "schluessel: { einspeisung: 109 }"))

        self.assertIn(">= 0", str(kontext.exception))

    def test_buildReaders_SolarLog_ErzeugtSolarLogReader(self) -> None:
        config = _lade(_eintrag("host: 192.168.20.90", "schluessel: { einspeisung: 109 }"))

        readers = _build_readers(config)

        self.assertEqual(1, len(readers))
        self.assertIsInstance(readers[0], SolarLogReader)


if __name__ == "__main__":
    unittest.main()
