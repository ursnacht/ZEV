"""Modbus-TCP-Reader für Wago-Zähler.

Liest die Wirkenergie-Zählerstände (kWh, OBIS 1.8.0 Bezug / 2.8.0 Einspeisung)
als 32-bit-Float über zwei 16-bit-Holding-Register. Byte-/Wortfolge und
Skalierung stammen aus der Konfiguration.
"""

from __future__ import annotations

import logging
import struct
from datetime import UTC, datetime

from pymodbus.client import ModbusTcpClient

from ..models import MeterConfig, MeterReading, ReadError, RegisterSpec
from .base import Reader

log = logging.getLogger(__name__)

# float32 belegt zwei aufeinanderfolgende 16-bit-Register.
_REGISTERS_PER_FLOAT32 = 2


class ModbusReader(Reader):
    """Liest einen Wago-Zähler via Modbus TCP."""

    def __init__(self, config: MeterConfig, timeout: float = 3.0) -> None:
        super().__init__(config)
        if not config.host:
            raise ValueError(f"Modbus-Zähler '{config.messpunkt}' ohne host.")
        self._client = ModbusTcpClient(
            host=config.host,
            port=config.port,
            timeout=timeout,
        )

    def read(self) -> MeterReading:
        if not self._client.connect():
            raise ReadError(
                f"Modbus-Verbindung zu {self.config.host}:{self.config.port} "
                f"('{self.messpunkt}') fehlgeschlagen."
            )

        bezug = self._read_float(self.config.register_bezug, rolle="bezug")
        einspeisung = self._read_float(self.config.register_einspeisung, rolle="einspeisung")

        if bezug < 0 or einspeisung < 0:
            raise ReadError(
                f"'{self.messpunkt}': negativer Zählerstand gelesen "
                f"(bezug={bezug}, einspeisung={einspeisung}) – verworfen."
            )

        return MeterReading(
            messpunkt=self.messpunkt,
            timestamp=datetime.now(UTC),
            zaehlerstand_bezug=bezug,
            zaehlerstand_einspeisung=einspeisung,
        )

    def _read_float(self, register: RegisterSpec, rolle: str) -> float:
        response = self._client.read_holding_registers(
            address=register.addr,
            count=_REGISTERS_PER_FLOAT32,
            slave=self.config.unit_id,
        )
        if response.isError():
            raise ReadError(
                f"'{self.messpunkt}' {rolle}: Modbus-Fehler bei Register "
                f"0x{register.addr:04X} – {response}."
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
