"""Reader-Factory: erzeugt je Zähler den passenden Reader anhand des Protokolls.

Erweiterbar: neues Protokoll = neue Reader-Klasse + ein Eintrag in ``_READERS``.
"""

from __future__ import annotations

from collections.abc import Callable

from ..models import MeterConfig
from .base import Reader
from .gplug_reader import GplugReader
from .modbus_reader import ModbusReader
from .sim_reader import SimReader

# Protokoll-Name (aus der Config) → Reader-Konstruktor.
_READERS: dict[str, Callable[[MeterConfig], Reader]] = {
    "modbus-tcp": ModbusReader,
    "gplug": GplugReader,  # spätere Erweiterung (wirft aktuell NotImplementedError)
    "sim": SimReader,      # Publisher-Simulator (synthetische Zählerstände)
}


def create_reader(config: MeterConfig) -> Reader:
    """Instanziiert den Reader für den konfigurierten ``protokoll``-Wert."""
    factory = _READERS.get(config.protokoll)
    if factory is None:
        raise ValueError(
            f"Unbekanntes Protokoll '{config.protokoll}' für Zähler "
            f"'{config.messpunkt}' (verfügbar: {sorted(_READERS)})."
        )
    return factory(config)
