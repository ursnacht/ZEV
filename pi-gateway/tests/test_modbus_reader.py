"""Lese-Retry bei Fehlern der Modbus-Bibliothek (Specs/Pi-Gateway-Software.md, FR-1.6).

Ausführen (im Verzeichnis ``pi-gateway``): ``python -m unittest discover -s tests -t .``
"""

from __future__ import annotations

import unittest
from unittest import mock

from pymodbus.exceptions import ModbusIOException
from pymodbus.pdu import ExceptionResponse

from gateway.main import _read_with_retry
from gateway.models import MeterConfig, ReadError, RegisterSpec
from gateway.readers.modbus_reader import ModbusReader


def _antwort(registers: list[int]) -> mock.Mock:
    """Eine fehlerfreie Modbus-Antwort mit den gegebenen Registern."""
    antwort = mock.Mock()
    antwort.isError.return_value = False
    antwort.registers = registers
    return antwort


def _reader() -> ModbusReader:
    """Ein Konsument ohne Einspeisung-Register: genau ein Modbus-Zugriff je Read."""
    config = MeterConfig(
        messpunkt="Batterie-Test",
        protokoll="modbus-tcp",
        register_bezug=RegisterSpec(addr=0x5000),
        register_einspeisung=None,
        host="127.0.0.1",
    )
    reader = ModbusReader(config)
    reader._client = mock.Mock()
    reader._client.connect.return_value = True
    return reader


# float32 big-endian 0x42C80000 = 100.0
_HUNDERT = [0x42C8, 0x0000]


def _abgeschnitten() -> ModbusIOException:
    """Wie pymodbus eine abgeschnittene Antwort meldet. Je Aufruf neu - eine geworfene Instanz
    sammelt Traceback und Kontext an."""
    return ModbusIOException("Unable to decode frame: byte_count 4 > length of packet 3")


class ModbusReaderTest(unittest.TestCase):

    def test_read_AbgeschnitteneAntwort_WirftReadError(self) -> None:
        """Die Exception der Bibliothek kommt als ReadError heraus, nicht als unerwarteter."""
        reader = _reader()
        ursache = _abgeschnitten()
        reader._client.read_holding_registers.side_effect = ursache

        with self.assertRaises(ReadError) as kontext:
            reader.read()

        self.assertIn("Batterie-Test", str(kontext.exception))
        self.assertIs(kontext.exception.__cause__, ursache)
        reader._client.close.assert_called_once()


class TimeoutHinweisTest(unittest.TestCase):
    """Specs/Pi-Gateway-Software.md, FR-1.5: Der Hinweis auf read_timeout erscheint nur, wenn
    keine Antwort kam - bei einer Exception-Response (code 11) hilft ein hoeheres Timeout nicht."""

    def _fehlermeldung(self, side_effect=None, return_value=None) -> str:
        reader = _reader()
        if side_effect is not None:
            reader._client.read_holding_registers.side_effect = side_effect
        else:
            reader._client.read_holding_registers.return_value = return_value
        with self.assertRaises(ReadError) as kontext:
            reader.read()
        return str(kontext.exception)

    def test_read_KeineAntwort_NenntTimeoutUndHinweis(self) -> None:
        # So meldet pymodbus 3.8 eine ausbleibende Antwort
        meldung = self._fehlermeldung(side_effect=ModbusIOException(
            "No response received after 3 retries, continue with next request"))

        self.assertIn("keine Antwort innerhalb 5.0s", meldung)
        self.assertIn("'read_timeout'", meldung)

    def test_read_KeineAntwortVerbindungGeschlossen_NenntHinweis(self) -> None:
        meldung = self._fehlermeldung(side_effect=ModbusIOException(
            "ERROR: No response received of the last requests (default: retries+3), CLOSING CONNECTION."))

        self.assertIn("'read_timeout'", meldung)

    def test_read_ExceptionResponseCode11_OhneHinweis(self) -> None:
        # Der Hub hat geantwortet: "Gateway target device failed to respond"
        meldung = self._fehlermeldung(return_value=ExceptionResponse(3, 11))

        self.assertIn("Batterie-Test", meldung)
        self.assertNotIn("read_timeout", meldung)

    def test_read_ExceptionResponse_NenntCodeUndDevId(self) -> None:
        # Den Code liest man sonst nirgends; dev_id braucht die Diagnose (grep "dev_id=")
        meldung = self._fehlermeldung(return_value=ExceptionResponse(3, 11))

        self.assertIn("Exception-Response code 11", meldung)
        self.assertIn("dev_id=", meldung)


    def test_read_AbgeschnitteneAntwort_OhneHinweis(self) -> None:
        # Auch das ist eine Antwort - nur eine kaputte. Ein hoeheres Timeout aendert nichts.
        meldung = self._fehlermeldung(side_effect=_abgeschnitten())

        self.assertNotIn("read_timeout", meldung)


class ClientKonfigurationTest(unittest.TestCase):

    def test_init_KeineInternenWiederholungen(self) -> None:
        """FR-1.5/1.6: pymodbus soll nicht selbst wiederholen - sonst dauert ein Timeout
        (retries + 1) x read_timeout, und die Meldung nennt die falsche Zeit."""
        with mock.patch("gateway.readers.modbus_reader.ModbusTcpClient") as client_klasse:
            ModbusReader(MeterConfig(
                messpunkt="Wago-1", protokoll="modbus-tcp", register_bezug=RegisterSpec(addr=0x600C),
                register_einspeisung=None, host="192.168.10.164", read_timeout_seconds=5.0))

        kwargs = client_klasse.call_args.kwargs
        self.assertEqual(0, kwargs["retries"])
        self.assertEqual(5.0, kwargs["timeout"])


class EinspeisungRegisterTest(unittest.TestCase):
    """FR-1.4: Ohne register.einspeisung ein Modbus-Zugriff weniger, publiziert wird 0."""

    def test_read_OhneEinspeisung_EinZugriffUndNull(self) -> None:
        reader = _reader()
        reader._client.read_holding_registers.return_value = _antwort(_HUNDERT)

        stand = reader.read()

        self.assertEqual(1, reader._client.read_holding_registers.call_count)
        self.assertEqual(0x5000, reader._client.read_holding_registers.call_args.kwargs["address"])
        self.assertEqual(100.0, stand.zaehlerstand_bezug)
        self.assertEqual(0.0, stand.zaehlerstand_einspeisung)

    def test_read_MitEinspeisung_ZweiZugriffeUndEchterWert(self) -> None:
        reader = _reader()
        reader.config = MeterConfig(
            messpunkt="Wago-1", protokoll="modbus-tcp", register_bezug=RegisterSpec(addr=0x600C),
            register_einspeisung=RegisterSpec(addr=0x6018), host="127.0.0.1")
        # float32 big-endian 0x41200000 = 10.0
        reader._client.read_holding_registers.side_effect = [_antwort(_HUNDERT),
                                                             _antwort([0x4120, 0x0000])]

        stand = reader.read()

        adressen = [aufruf.kwargs["address"]
                    for aufruf in reader._client.read_holding_registers.call_args_list]
        self.assertEqual([0x600C, 0x6018], adressen)
        self.assertEqual(100.0, stand.zaehlerstand_bezug)
        self.assertEqual(10.0, stand.zaehlerstand_einspeisung)


def _speicher_reader(read_attempts: int = 2) -> ModbusReader:
    """Speicher mit Lade-/Entladeregister und Ladezustand (uint16), wie der Wechselrichter von Hene."""
    config = MeterConfig(
        read_attempts=read_attempts,
        messpunkt="Batterie-Test",
        protokoll="modbus-tcp",
        register_bezug=RegisterSpec(addr=0x7984, typ="uint32", skalierung=0.1),
        register_einspeisung=RegisterSpec(addr=0x7986, typ="uint32", skalierung=0.1),
        register_zustand={"soc": RegisterSpec(addr=0x80E8, typ="uint16", skalierung=0.01)},
        host="127.0.0.1",
    )
    reader = ModbusReader(config)
    reader._client = mock.Mock()
    reader._client.connect.return_value = True
    return reader


def _keine_antwort() -> ModbusIOException:
    return ModbusIOException("No response received after 0 retries, continue with next request")


@mock.patch("gateway.readers.modbus_reader.time.sleep")
class ZustandAlsBeiwerkTest(unittest.TestCase):
    """FR-1.8: Ein Zustandsregister wird bis zu read_attempts-mal gelesen; scheitern alle
    Versuche, kostet es die Zählerstände nicht."""

    # uint32 1000 -> 100.0 kWh, 500 -> 50.0 kWh; uint16 8750 -> 87.5 %
    _LADUNG = [0x0000, 0x03E8]
    _ENTLADUNG = [0x0000, 0x01F4]
    _SOC = [0x222E]

    def test_read_ZustandImmerOhneAntwort_StaendeOhneZustandNachAllenVersuchen(self, sleep) -> None:
        reader = _speicher_reader(read_attempts=2)
        reader._client.read_holding_registers.side_effect = [
            _antwort(self._LADUNG), _antwort(self._ENTLADUNG), _keine_antwort(), _keine_antwort(),
        ]

        with self.assertLogs("gateway", level="INFO") as logs:
            stand = reader.read()

        self.assertEqual(100.0, stand.zaehlerstand_bezug)
        self.assertEqual(50.0, stand.zaehlerstand_einspeisung)
        self.assertIsNone(stand.zustand)  # nicht {} - der Payload bleibt ohne 'zustand'
        # Zwei Versuche am Ladezustand, die Zaehlerstaende je nur einmal gelesen
        adressen = [a.kwargs["address"] for a in reader._client.read_holding_registers.call_args_list]
        self.assertEqual([0x7984, 0x7986, 0x80E8, 0x80E8], adressen)
        sleep.assert_called_once_with(0.5)
        self.assertTrue(any("Versuch 1/2" in zeile for zeile in logs.output))
        self.assertTrue(any("WARNING" in zeile and "nach 2 Versuch(en)" in zeile and "ohne ihn" in zeile
                            for zeile in logs.output))

    def test_read_ZustandZweiterVersuchGelingt_ZustandImReading(self, sleep) -> None:
        reader = _speicher_reader(read_attempts=2)
        reader._client.read_holding_registers.side_effect = [
            _antwort(self._LADUNG), _antwort(self._ENTLADUNG), _keine_antwort(), _antwort(self._SOC),
        ]

        stand = reader.read()

        self.assertEqual({"soc": 87.5}, stand.zustand)
        self.assertEqual(100.0, stand.zaehlerstand_bezug)
        sleep.assert_called_once_with(0.5)

    def test_read_ReadAttemptsEins_ZustandNurEinmalGelesen(self, sleep) -> None:
        reader = _speicher_reader(read_attempts=1)
        reader._client.read_holding_registers.side_effect = [
            _antwort(self._LADUNG), _antwort(self._ENTLADUNG), _keine_antwort(),
        ]

        with self.assertLogs("gateway", level="WARNING"):
            stand = reader.read()

        self.assertIsNone(stand.zustand)
        self.assertEqual(3, reader._client.read_holding_registers.call_count)
        sleep.assert_not_called()

    def test_read_ZustandExceptionResponse_StaendeOhneZustand(self, sleep) -> None:
        reader = _speicher_reader(read_attempts=2)
        reader._client.read_holding_registers.side_effect = [
            _antwort(self._LADUNG), _antwort(self._ENTLADUNG),
            ExceptionResponse(3, 2), ExceptionResponse(3, 2),
        ]

        with self.assertLogs("gateway", level="WARNING"):
            stand = reader.read()

        self.assertEqual(100.0, stand.zaehlerstand_bezug)
        self.assertIsNone(stand.zustand)

    def test_read_ZustandGelesen_ImReading(self, sleep) -> None:
        reader = _speicher_reader()
        reader._client.read_holding_registers.side_effect = [
            _antwort(self._LADUNG), _antwort(self._ENTLADUNG), _antwort(self._SOC),
        ]

        stand = reader.read()

        self.assertEqual({"soc": 87.5}, stand.zustand)

    def test_read_ZaehlerstandKeineAntwort_WeiterhinReadError(self, sleep) -> None:
        # Nur der Zustand ist Beiwerk - fehlt ein Zaehlerstand, gilt der Read als gescheitert.
        reader = _speicher_reader()
        reader._client.read_holding_registers.side_effect = ModbusIOException(
            "No response received after 0 retries, continue with next request")

        with self.assertRaises(ReadError):
            reader.read()

    @mock.patch("gateway.main.time.sleep")
    def test_readWithRetry_ZustandKeineAntwort_MessungNichtWiederholt(self, main_sleep, sleep) -> None:
        # Der Zustand wird im Reader wiederholt - die ganze Messung (Zaehlerstaende) nicht.
        reader = _speicher_reader(read_attempts=2)
        reader._client.read_holding_registers.side_effect = [
            _antwort(self._LADUNG), _antwort(self._ENTLADUNG), _keine_antwort(), _keine_antwort(),
        ]

        with self.assertLogs("gateway", level="WARNING"):
            stand = _read_with_retry(reader, attempts=2)

        self.assertIsNotNone(stand)
        self.assertEqual(4, reader._client.read_holding_registers.call_count)
        main_sleep.assert_not_called()


class ReadWithRetryTest(unittest.TestCase):

    @mock.patch("gateway.main.time.sleep")
    def test_readWithRetry_AbgeschnitteneAntwortDannErfolg_LiefertStand(self, _sleep) -> None:
        """Beobachtet am 16.10.2026 bei 'Batterie-Hene': der zweite Versuch gelingt."""
        reader = _reader()
        reader._client.read_holding_registers.side_effect = [_abgeschnitten(), _antwort(_HUNDERT)]

        stand = _read_with_retry(reader, attempts=2)

        self.assertIsNotNone(stand)
        self.assertEqual(100.0, stand.zaehlerstand_bezug)
        self.assertEqual(2, reader._client.read_holding_registers.call_count)

    @mock.patch("gateway.main.time.sleep")
    def test_readWithRetry_ImmerAbgeschnitten_UebersprungenNachAllenVersuchen(self, _sleep) -> None:
        reader = _reader()
        reader._client.read_holding_registers.side_effect = [_abgeschnitten(), _abgeschnitten()]

        with self.assertLogs("gateway", level="WARNING") as logs:
            stand = _read_with_retry(reader, attempts=2)

        self.assertIsNone(stand)
        self.assertEqual(2, reader._client.read_holding_registers.call_count)
        self.assertTrue(any("nach 2 Versuch(en)" in zeile for zeile in logs.output))


if __name__ == "__main__":
    unittest.main()
