"""Hauptschleife, Lese-Retry und Start-Logs (Specs/Pi-Gateway-Software.md, FR-1, FR-1.5, FR-1.6, FR-7).

Ausführen (im Verzeichnis ``pi-gateway``): ``python -m unittest discover -s tests -t .``
"""

from __future__ import annotations

import logging
import threading
import unittest
from datetime import datetime, timezone
from unittest import mock

from gateway.heartbeat import Heartbeat
from gateway.main import _log_abweichende_timeouts, _read_and_publish, _read_with_retry, _run_loop, main
from gateway.models import (
    BrokerConfig,
    GatewayConfig,
    MeterConfig,
    MeterReading,
    ReadError,
    RegisterSpec,
)
from gateway.readers.base import Reader
from gateway.readers.modbus_reader import ModbusReader


def _meter(messpunkt: str, read_timeout_seconds: float = 5.0) -> MeterConfig:
    return MeterConfig(
        messpunkt=messpunkt,
        protokoll="modbus-tcp",
        register_bezug=RegisterSpec(addr=0x600C),
        register_einspeisung=RegisterSpec(addr=0x6018),
        host="127.0.0.1",
        read_timeout_seconds=read_timeout_seconds,
    )


def _config(meters: list[MeterConfig], read_timeout_seconds: float = 5.0,
            read_attempts: int = 2) -> GatewayConfig:
    return GatewayConfig(
        org_id=42,
        publish_interval_seconds=300,
        broker=BrokerConfig(url="tcp://localhost:1883"),
        meters=meters,
        read_timeout_seconds=read_timeout_seconds,
        read_attempts=read_attempts,
    )


def _reading(messpunkt: str) -> MeterReading:
    return MeterReading(
        messpunkt=messpunkt,
        timestamp=datetime(2026, 10, 22, 10, 0, 0, tzinfo=timezone.utc),
        zaehlerstand_bezug=1000.0,
        zaehlerstand_einspeisung=0.0,
    )


class _StubReader(Reader):
    """Liefert der Reihe nach die gegebenen Ergebnisse; eine Exception wird geworfen."""

    def __init__(self, messpunkt: str, *ergebnisse) -> None:
        super().__init__(_meter(messpunkt))
        self._ergebnisse = list(ergebnisse)
        self.aufrufe = 0

    def read(self) -> MeterReading:
        self.aufrufe += 1
        ergebnis = self._ergebnisse.pop(0) if len(self._ergebnisse) > 1 else self._ergebnisse[0]
        if isinstance(ergebnis, Exception):
            raise ergebnis
        return ergebnis


def _publisher(erfolgreich: bool = True) -> mock.Mock:
    """Ein Publisher-Double; ``erfolgreich=False`` = Broker offline (Publish nicht eingereiht)."""
    publisher = mock.Mock()
    publisher.publish.return_value = erfolgreich
    publisher.is_connected.return_value = True
    return publisher


def _antwort(registers: list[int]) -> mock.Mock:
    """Eine fehlerfreie Modbus-Antwort mit den gegebenen Registern."""
    antwort = mock.Mock()
    antwort.isError.return_value = False
    antwort.registers = registers
    return antwort


def _modbus_reader() -> ModbusReader:
    """Ein Konsument ohne Einspeisung-Register: genau ein Modbus-Zugriff je Read."""
    config = MeterConfig(
        messpunkt="MP-Modbus",
        protokoll="modbus-tcp",
        register_bezug=RegisterSpec(addr=0x600C),
        register_einspeisung=None,
        host="127.0.0.1",
    )
    with mock.patch("gateway.readers.modbus_reader.ModbusTcpClient"):
        reader = ModbusReader(config)
    reader._client.connect.return_value = True
    return reader


@mock.patch("gateway.main.time.sleep")
class RunLoopTest(unittest.TestCase):
    """FR-1, FR-1.6: Fehler bleiben je Zähler isoliert, der Zyklus zählt die Erfolge."""

    def _ein_zyklus(self, readers: list[Reader], publisher: mock.Mock,
                    heartbeat: Heartbeat) -> threading.Event:
        """Lässt die Hauptschleife genau einen Zyklus laufen (Stop beim Heartbeat-Log)."""
        stop_event = threading.Event()
        with mock.patch.object(heartbeat, "log_cycle",
                               side_effect=lambda *_: stop_event.set()) as log_cycle:
            _run_loop(_config([r.config for r in readers]), readers, publisher, heartbeat,
                      stop_event)
        self.log_cycle = log_cycle
        return stop_event

    def test_runLoop_MittlererZaehlerScheitertImmer_UebrigePubliziert(self, _sleep) -> None:
        erster = _StubReader("MP-1", _reading("MP-1"))
        defekt = _StubReader("MP-Defekt", ReadError("keine Antwort"))
        dritter = _StubReader("MP-3", _reading("MP-3"))
        publisher = _publisher()

        with self.assertLogs("gateway", level="WARNING") as logs:
            self._ein_zyklus([erster, defekt, dritter], publisher, Heartbeat())

        publiziert = [aufruf.args[0].messpunkt for aufruf in publisher.publish.call_args_list]
        self.assertEqual(["MP-1", "MP-3"], publiziert)
        self.assertEqual(2, defekt.aufrufe)  # read_attempts: 2
        self.log_cycle.assert_called_once_with(2, 3, True)
        self.assertTrue(any("MP-Defekt" in zeile and "nach 2 Versuch(en)" in zeile
                            for zeile in logs.output))

    def test_runLoop_UnerwarteteException_UebrigeLaufenWeiter(self, _sleep) -> None:
        defekt = _StubReader("MP-Defekt", RuntimeError("Programmierfehler"))
        zweiter = _StubReader("MP-2", _reading("MP-2"))
        publisher = _publisher()

        with self.assertLogs("gateway", level="ERROR"):
            self._ein_zyklus([defekt, zweiter], publisher, Heartbeat())

        self.assertEqual(1, defekt.aufrufe)  # kein Retry bei unerwarteter Exception
        self.assertEqual(["MP-2"], [a.args[0].messpunkt for a in publisher.publish.call_args_list])
        self.log_cycle.assert_called_once_with(1, 2, True)

    def _wartezeit_nach_zyklus(self, zyklus_start: float, zyklus_ende: float) -> list[float]:
        """Lässt genau einen Zyklus laufen und liefert die Wartezeit danach (Uhr gestellt)."""
        gewartet: list[float] = []

        class _EinZyklusEvent(threading.Event):
            def wait(self, timeout=None):
                gewartet.append(timeout)
                self.set()
                return True

        self.erster = _StubReader("MP-1", _reading("MP-1"))
        self.zweiter = _StubReader("MP-2", _reading("MP-2"))
        with mock.patch("gateway.main.time.monotonic", side_effect=[zyklus_start, zyklus_ende]):
            readers = [self.erster, self.zweiter]
            _run_loop(_config([r.config for r in readers]), readers, _publisher(), Heartbeat(),
                      _EinZyklusEvent())
        return gewartet

    def test_runLoop_KurzerZyklus_WartetIntervallMinusZyklusdauer(self, _sleep) -> None:
        """FR-1.4: Der Takt ist publish_interval ab Zyklusbeginn - die Zyklusdauer geht ab."""
        gewartet = self._wartezeit_nach_zyklus(1000.0, 1012.5)  # Zyklus 12.5 s

        self.assertEqual([287.5], gewartet)  # publish_interval_seconds 300 aus _config
        self.assertEqual(1, self.erster.aufrufe)
        self.assertEqual(1, self.zweiter.aufrufe)

    def test_runLoop_ZyklusLaengerAlsIntervall_LiestSofortWieder(self, _sleep) -> None:
        """FR-1.4: Bleibt vom Intervall nichts übrig, beginnt der nächste Zyklus sofort."""
        gewartet = self._wartezeit_nach_zyklus(1000.0, 1360.0)  # Zyklus 360 s > 300 s

        self.assertEqual([0.0], gewartet)

    def test_runLoop_ZyklusGenauIntervall_WartetNicht(self, _sleep) -> None:
        gewartet = self._wartezeit_nach_zyklus(1000.0, 1300.0)

        self.assertEqual([0.0], gewartet)


@mock.patch("gateway.main.time.sleep")
class ReadAndPublishTest(unittest.TestCase):
    """FR-1.6 und Akzeptanzkriterium „Unvollständige/fehlgeschlagene Reads werden verworfen"."""

    def test_readAndPublish_ZweiterVersuchGelingt_PubliziertUndTrue(self, sleep) -> None:
        reader = _StubReader("MP-1", ReadError("code 11"), _reading("MP-1"))
        publisher = _publisher()
        heartbeat = Heartbeat()

        ergebnis = _read_and_publish(reader, publisher, heartbeat, attempts=2)

        self.assertTrue(ergebnis)
        self.assertEqual(2, reader.aufrufe)
        publisher.publish.assert_called_once()
        sleep.assert_called_once()
        self.assertIsNotNone(heartbeat.last_successful_publish)

    def test_readWithRetry_EinVersuch_KeinWiederholversuch(self, sleep) -> None:
        reader = _StubReader("MP-1", ReadError("code 11"), _reading("MP-1"))

        with self.assertLogs("gateway", level="WARNING") as logs:
            stand = _read_with_retry(reader, attempts=1)

        self.assertIsNone(stand)
        self.assertEqual(1, reader.aufrufe)
        sleep.assert_not_called()
        self.assertTrue(any("nach 1 Versuch(en)" in zeile for zeile in logs.output))

    def test_readAndPublish_UnvollstaendigerRead_NichtPubliziert(self, _sleep) -> None:
        reader = _modbus_reader()
        reader._client.read_holding_registers.return_value = _antwort([0x42C8])  # 1 statt 2
        publisher = _publisher()
        heartbeat = Heartbeat()

        with self.assertLogs("gateway", level="WARNING") as logs:
            ergebnis = _read_and_publish(reader, publisher, heartbeat, attempts=1)

        self.assertFalse(ergebnis)
        publisher.publish.assert_not_called()
        self.assertIsNone(heartbeat.last_successful_read)
        self.assertTrue(any("unvollständiger Read" in zeile for zeile in logs.output))

    def test_readAndPublish_NegativerStand_NichtPubliziert(self, _sleep) -> None:
        reader = _modbus_reader()
        # float32 big-endian 0xC2C80000 = -100.0
        reader._client.read_holding_registers.return_value = _antwort([0xC2C8, 0x0000])
        publisher = _publisher()

        with self.assertLogs("gateway", level="WARNING") as logs:
            ergebnis = _read_and_publish(reader, publisher, Heartbeat(), attempts=1)

        self.assertFalse(ergebnis)
        publisher.publish.assert_not_called()
        self.assertTrue(any("negativer Zählerstand" in zeile for zeile in logs.output))

    def test_readAndPublish_PublishScheitert_FalseOhnePublishZeitpunkt(self, _sleep) -> None:
        reader = _StubReader("MP-1", _reading("MP-1"))
        heartbeat = Heartbeat()

        ergebnis = _read_and_publish(reader, _publisher(erfolgreich=False), heartbeat, 2)

        self.assertFalse(ergebnis)
        self.assertIsNotNone(heartbeat.last_successful_read)
        self.assertIsNone(heartbeat.last_successful_publish)


class StartLogTest(unittest.TestCase):
    """FR-1.5, FR-1.6: Die Start-Zeilen nennen die wirksamen Lese-Parameter."""

    def _start_logs(self, config: GatewayConfig) -> list[str]:
        """Startet ``main`` bis vor die Hauptschleife und liefert die Log-Zeilen."""
        with mock.patch("gateway.main.load_config", return_value=config), \
                mock.patch("gateway.main._setup_logging"), \
                mock.patch("gateway.main._build_readers", return_value=[]), \
                mock.patch("gateway.main.MqttPublisher"), \
                mock.patch("gateway.main._install_signal_handlers"), \
                mock.patch("gateway.main._run_loop"), \
                self.assertLogs("gateway", level="INFO") as logs:
            self.assertEqual(0, main(["--config", "egal.yaml"]))
        return [eintrag.getMessage() for eintrag in logs.records]

    def test_main_Default_StartzeileNenntTimeoutUndVersuche(self) -> None:
        zeilen = self._start_logs(_config([_meter("MP-1"), _meter("MP-2")]))

        startzeile = next(zeile for zeile in zeilen if zeile.startswith("Pi-Gateway startet"))
        self.assertIn("org_id=42", startzeile)
        self.assertIn("2 Zähler", startzeile)
        self.assertIn("Intervall=300s", startzeile)
        self.assertIn("Lese-Timeout=5.0s", startzeile)
        self.assertIn("Lese-Versuche=2", startzeile)
        self.assertIn("Broker=tcp://localhost:1883", startzeile)

    def test_main_GesetzteWerte_StartzeileNenntWirksameWerte(self) -> None:
        config = _config([_meter("MP-1", 8.0)], read_timeout_seconds=8.0, read_attempts=1)

        zeilen = self._start_logs(config)

        startzeile = next(zeile for zeile in zeilen if zeile.startswith("Pi-Gateway startet"))
        self.assertIn("Lese-Timeout=8.0s", startzeile)
        self.assertIn("Lese-Versuche=1", startzeile)

    def test_main_OhneAbweichung_KeineZeileEigenesTimeout(self) -> None:
        zeilen = self._start_logs(_config([_meter("MP-1"), _meter("MP-2")]))

        self.assertFalse(any("Eigenes Lese-Timeout" in zeile for zeile in zeilen))

    def test_logAbweichendeTimeouts_NenntGenauDieAbweichenden(self) -> None:
        config = _config([_meter("MP-1"), _meter("MP-Langsam", 12.0), _meter("MP-3"),
                          _meter("MP-Schnell", 2.5)])

        with self.assertLogs("gateway", level="INFO") as logs:
            _log_abweichende_timeouts(config)

        self.assertEqual(["Eigenes Lese-Timeout: MP-Langsam=12.0s, MP-Schnell=2.5s"],
                         [eintrag.getMessage() for eintrag in logs.records])


class ReaderStartLogTest(unittest.TestCase):
    """FR-1.4: Ein Zähler ohne Einspeisung-Register wird beim Start auf INFO genannt."""

    def _logs(self, meter: MeterConfig) -> list[logging.LogRecord]:
        with mock.patch("gateway.readers.modbus_reader.ModbusTcpClient"), \
                self.assertLogs("gateway", level="DEBUG") as logs:
            ModbusReader(meter)
        return logs.records

    def test_init_OhneEinspeisung_InfoZeile(self) -> None:
        meter = MeterConfig(messpunkt="MP-Konsument", protokoll="modbus-tcp",
                            register_bezug=RegisterSpec(addr=0x600C), register_einspeisung=None,
                            host="127.0.0.1")

        infos = [r.getMessage() for r in self._logs(meter) if r.levelno == logging.INFO]

        self.assertEqual(1, len(infos))
        self.assertIn("MP-Konsument", infos[0])
        self.assertIn("kein Einspeisung-Register", infos[0])

    def test_init_MitEinspeisung_KeineInfoZeile(self) -> None:
        # Der Reader loggt sein Timeout auf DEBUG - das macht assertLogs auch ohne INFO-Zeile glücklich
        infos = [r for r in self._logs(_meter("MP-Produzent")) if r.levelno >= logging.INFO]

        self.assertEqual([], infos)


if __name__ == "__main__":
    unittest.main()
