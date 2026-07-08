"""Einstiegspunkt: Orchestrierung des Lese-/Publish-Zyklus.

- Konfiguration laden, Reader je Zähler erzeugen (Factory), Broker verbinden.
- Im konfigurierten Intervall über die (beliebig lange) Zähler-Liste iterieren:
  jeder Zähler wird gelesen und publiziert; Fehler werden **je Zähler isoliert**
  (ein defekter Zähler stoppt die anderen nicht).
- Sauberes Herunterfahren bei SIGINT/SIGTERM (systemd stop).
"""

from __future__ import annotations

import argparse
import logging
import os
import signal
import sys
import threading

from .config import ConfigError, load_config
from .heartbeat import Heartbeat
from .models import GatewayConfig, ReadError
from .publisher import MqttPublisher
from .readers.base import Reader
from .readers.factory import create_reader

log = logging.getLogger("gateway")

_DEFAULT_CONFIG = "/opt/pi-gateway/config.yaml"


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv)
    _setup_logging()

    try:
        config = load_config(args.config)
    except ConfigError as exc:
        log.error("Konfigurationsfehler: %s", exc)
        return 2

    log.info(
        "Pi-Gateway startet: org_id=%s, %d Zähler, Intervall=%ds, Broker=%s",
        config.org_id,
        len(config.meters),
        config.publish_interval_seconds,
        config.broker.url,
    )

    readers = _build_readers(config)
    publisher = MqttPublisher(config.org_id, config.broker)
    publisher.connect()

    stop_event = threading.Event()
    _install_signal_handlers(stop_event)

    heartbeat = Heartbeat()
    try:
        _run_loop(config, readers, publisher, heartbeat, stop_event)
    finally:
        log.info("Fahre herunter ...")
        for reader in readers:
            reader.close()
        publisher.disconnect()

    return 0


def _run_loop(
    config: GatewayConfig,
    readers: list[Reader],
    publisher: MqttPublisher,
    heartbeat: Heartbeat,
    stop_event: threading.Event,
) -> None:
    """Liest und publiziert alle Zähler bis zum Stop-Signal."""
    while not stop_event.is_set():
        success = 0
        for reader in readers:
            if stop_event.is_set():
                break
            if _read_and_publish(reader, publisher, heartbeat):
                success += 1

        heartbeat.log_cycle(success, len(readers), publisher.is_connected())
        # Unterbrechbares Warten bis zum nächsten Zyklus (reagiert sofort auf Stop).
        stop_event.wait(config.publish_interval_seconds)


def _read_and_publish(
    reader: Reader,
    publisher: MqttPublisher,
    heartbeat: Heartbeat,
) -> bool:
    """Liest einen Zähler und publiziert ihn. Fehler bleiben lokal (kein Abbruch)."""
    try:
        reading = reader.read()
    except ReadError as exc:
        log.warning("Read übersprungen (%s): %s", reader.messpunkt, exc)
        return False
    except Exception:  # noqa: BLE001 - ein defekter Zähler darf die anderen nicht stoppen
        log.exception("Unerwarteter Lesefehler bei '%s' – übersprungen.", reader.messpunkt)
        return False

    heartbeat.record_read()
    if publisher.publish(reading):
        heartbeat.record_publish()
        return True
    return False


def _build_readers(config: GatewayConfig) -> list[Reader]:
    """Erzeugt einen Reader je Zähler. Baufehler eines Zählers sind nicht fatal."""
    readers: list[Reader] = []
    for meter in config.meters:
        try:
            readers.append(create_reader(meter))
        except Exception:  # noqa: BLE001
            log.exception("Reader für '%s' konnte nicht erstellt werden – ignoriert.",
                          meter.messpunkt)
    if not readers:
        log.error("Kein einziger Zähler-Reader verfügbar – nichts zu tun.")
    return readers


def _install_signal_handlers(stop_event: threading.Event) -> None:
    def handler(signum, _frame):
        log.info("Signal %s empfangen – beende.", signal.Signals(signum).name)
        stop_event.set()

    signal.signal(signal.SIGINT, handler)
    signal.signal(signal.SIGTERM, handler)


def _setup_logging() -> None:
    level = os.environ.get("LOG_LEVEL", "INFO").upper()
    logging.basicConfig(
        level=level,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
        stream=sys.stdout,  # journald erfasst stdout
    )


def _parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(prog="pi-gateway", description="ZEV Pi-Gateway")
    parser.add_argument(
        "--config",
        default=os.environ.get("PI_GATEWAY_CONFIG", _DEFAULT_CONFIG),
        help=f"Pfad zur Konfigurationsdatei (Default: {_DEFAULT_CONFIG})",
    )
    return parser.parse_args(argv)


if __name__ == "__main__":
    sys.exit(main())
