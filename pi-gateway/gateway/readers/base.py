"""Basisklasse für Zähler-Reader."""

from __future__ import annotations

import abc

from ..models import MeterConfig, MeterReading


class Reader(abc.ABC):
    """Liest einen Zähler und liefert dessen absolute Zählerstände.

    Ein Reader kapselt genau ein Protokoll (z. B. Modbus TCP). Weitere Protokolle
    werden als eigene Reader-Klasse ergänzt und in der Factory registriert.
    """

    def __init__(self, config: MeterConfig) -> None:
        self.config = config

    @property
    def messpunkt(self) -> str:
        return self.config.messpunkt

    @abc.abstractmethod
    def read(self) -> MeterReading:
        """Liest die absoluten Stände. Wirft ``ReadError`` bei Teil-/Fehl-Reads."""

    def close(self) -> None:  # pragma: no cover - optionales Aufräumen
        """Verbindung/Ressourcen freigeben (Default: nichts)."""
