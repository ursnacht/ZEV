"""Modbus-TCP-Reader für Wago-Zähler.

Liest die Wirkenergie-Zählerstände (kWh, OBIS 1.8.0 Bezug / 2.8.0 Einspeisung)
als 32-bit-Float über zwei 16-bit-Holding-Register. Byte-/Wortfolge und
Skalierung stammen aus der Konfiguration.
"""

from __future__ import annotations

import logging
import struct
from datetime import datetime, timezone

from pymodbus.client import ModbusTcpClient

from ..models import MeterConfig, MeterReading, ReadError, RegisterSpec
from .base import Reader

log = logging.getLogger(__name__)

# float32 belegt zwei aufeinanderfolgende 16-bit-Register.
_REGISTERS_PER_FLOAT32 = 2


class ModbusReader(Reader):
    """Liest einen Wago-Zähler via Modbus TCP."""

    def __init__(self, config: MeterConfig) -> None:
        super().__init__(config)
        if not config.host:
            raise ValueError(f"Modbus-Zähler '{config.messpunkt}' ohne host.")
        # Timeout kommt aus der Konfiguration (global `read_timeout`, je Zähler
        # via `zaehler[].read_timeout` überschreibbar) – keine zweite Quelle im Code.
        log.debug("Modbus-Reader '%s': Lese-Timeout %.1fs",
                  config.messpunkt, config.read_timeout_seconds)
        if config.register_einspeisung is None:
            # Bewusste Konfigurationsentscheidung -> beim Start sichtbar machen (INFO),
            # damit ein versehentlich fehlender Block nicht unbemerkt 0 liefert.
            log.info("Modbus-Reader '%s': kein Einspeisung-Register konfiguriert – "
                     "es wird nicht gelesen und 0 publiziert.", config.messpunkt)
        self._client = ModbusTcpClient(
            host=config.host,
            port=config.port,
            timeout=config.read_timeout_seconds,
        )

    def read(self) -> MeterReading:
        try:
            if not self._client.connect():
                raise ReadError(
                    f"Modbus-Verbindung zu {self.config.host}:{self.config.port} "
                    f"('{self.messpunkt}') fehlgeschlagen."
                )

            bezug = self._read_float(self.config.register_bezug, rolle="bezug")
            # Fehlt das Einspeisung-Register (typisch Konsument), wird es nicht gelesen:
            # ein Modbus-Zugriff weniger je Zyklus und Zähler – entlastet die RS485-Strecke.
            einspeisung = (
                0.0 if self.config.register_einspeisung is None
                else self._read_float(self.config.register_einspeisung, rolle="einspeisung")
            )
        finally:
            # An einem RTU->TCP-Hub mit wenigen erlaubten Sockets darf immer nur EINE
            # Verbindung offen sein. Da die Zähler sequenziell gelesen werden, hält das
            # Schliessen nach jedem Read die Zahl gleichzeitig offener Sockets auf 1 und
            # vermeidet tote ("stale") Sockets. Intervall ist Minuten -> Reconnect-Overhead
            # vernachlässigbar; absolute Stände sind verlusttolerant.
            self._client.close()

        if bezug < 0 or einspeisung < 0:
            raise ReadError(
                f"'{self.messpunkt}': negativer Zählerstand gelesen "
                f"(bezug={bezug}, einspeisung={einspeisung}) – verworfen."
            )

        return MeterReading(
            messpunkt=self.messpunkt,
            timestamp=datetime.now(timezone.utc),
            zaehlerstand_bezug=bezug,
            zaehlerstand_einspeisung=einspeisung,
            seriennummer=self.config.seriennummer,
        )

    def _read_float(self, register: RegisterSpec, rolle: str) -> float:
        response = self._client.read_holding_registers(
            address=register.addr,
            count=_REGISTERS_PER_FLOAT32,
            slave=self.config.unit_id,
        )
        if response.isError():
            # Timeout und Exception-Response unterscheiden: bei einer Exception-Response hat
            # das Gerät/der Hub GEANTWORTET (z. B. code 11 = Gateway target device failed to
            # respond) – dann hilft ein höheres Client-Timeout nicht, die Ursache liegt auf
            # der RTU-Strecke bzw. am Hub. Timeout-Hinweis daher nur, wenn keine Antwort kam.
            hinweis = ""
            if "ExceptionResponse" not in str(response):
                hinweis = (f" (keine Antwort innerhalb {self.config.read_timeout_seconds:.1f}s – "
                           f"ggf. 'read_timeout' für diesen Zähler erhöhen)")
            raise ReadError(
                f"'{self.messpunkt}' {rolle}: Modbus-Fehler bei Register "
                f"0x{register.addr:04X} – {response}.{hinweis}"
            )

        registers = response.registers
        if len(registers) < _REGISTERS_PER_FLOAT32:
            raise ReadError(
                f"'{self.messpunkt}' {rolle}: unvollständiger Read "
                f"({len(registers)} statt {_REGISTERS_PER_FLOAT32} Register)."
            )

        value = _decode_float32(registers, register.wortfolge)
        return value * register.skalierung

    def close(self) -> None:
        self._client.close()


def _decode_float32(registers: list[int], word_order: str) -> float:
    """Setzt zwei 16-bit-Register zu einem IEEE-754-float32 zusammen.

    ``big`` = höherwertiges Wort zuerst (AB CD), ``little`` = niederwertiges
    Wort zuerst (CD AB). Innerhalb eines Registers gilt Big-Endian (Modbus-Standard).
    """
    high, low = (
        (registers[0], registers[1])
        if word_order == "big"
        else (registers[1], registers[0])
    )
    packed = struct.pack(">HH", high, low)
    return struct.unpack(">f", packed)[0]
