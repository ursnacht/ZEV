"""Laden und Validieren der Gateway-Konfiguration.

- YAML-Datei einlesen
- ``${ENV_VAR}``-Platzhalter aus der Umgebung auflösen (Secrets nie in der Datei)
- Zähler-Liste beliebiger Länge (10–20+) validieren; neuer Zähler = nur ein
  Konfigurations-Eintrag, kein Code-Change.
"""

from __future__ import annotations

import os
import re
from pathlib import Path

import yaml

from .models import (
    BrokerConfig,
    GatewayConfig,
    MeterConfig,
    RegisterSpec,
)

_ENV_PATTERN = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}")

# Umrechnung von Intervall-Kurzschreibweisen (z. B. "5m", "30s", "1h") in Sekunden.
_INTERVAL_UNITS = {"s": 1, "m": 60, "h": 3600}

_SUPPORTED_PROTOCOLS = {"modbus-tcp"}  # "gplug" folgt später
_SUPPORTED_REGISTER_TYPES = {"float32"}
_SUPPORTED_WORD_ORDERS = {"big", "little"}


class ConfigError(Exception):
    """Ungültige oder unvollständige Konfiguration."""


def load_config(path: str | Path) -> GatewayConfig:
    """Liest und validiert die Konfigurationsdatei. Wirft bei Fehlern ``ConfigError``."""
    path = Path(path)
    if not path.is_file():
        raise ConfigError(f"Konfigurationsdatei nicht gefunden: {path}")

    try:
        raw = yaml.safe_load(path.read_text(encoding="utf-8"))
    except yaml.YAMLError as exc:
        raise ConfigError(f"YAML nicht lesbar: {exc}") from exc

    if not isinstance(raw, dict):
        raise ConfigError("Konfiguration muss ein YAML-Mapping (Schlüssel/Wert) sein.")

    raw = _expand_env(raw)

    org_id = _require(raw, "org_id", int)
    interval = _parse_interval(_require(raw, "publish_interval", (str, int)))
    broker = _parse_broker(_require(raw, "broker", dict))
    meters = _parse_meters(raw.get("zaehler"))

    return GatewayConfig(
        org_id=org_id,
        publish_interval_seconds=interval,
        broker=broker,
        meters=meters,
    )


def _expand_env(value):
    """Ersetzt ``${VAR}`` rekursiv durch Umgebungsvariablen (fehlende → ConfigError)."""
    if isinstance(value, dict):
        return {k: _expand_env(v) for k, v in value.items()}
    if isinstance(value, list):
        return [_expand_env(v) for v in value]
    if isinstance(value, str):
        def repl(match: re.Match) -> str:
            name = match.group(1)
            env_value = os.environ.get(name)
            if env_value is None:
                raise ConfigError(f"Umgebungsvariable ${{{name}}} ist nicht gesetzt.")
            return env_value

        return _ENV_PATTERN.sub(repl, value)
    return value


def _parse_broker(data: dict) -> BrokerConfig:
    url = _require(data, "url", str)
    qos = data.get("qos", 1)
    if qos not in (0, 1, 2):
        raise ConfigError(f"broker.qos muss 0, 1 oder 2 sein (war: {qos}).")
    return BrokerConfig(
        url=url,
        username=data.get("username"),
        password=data.get("password"),
        qos=int(qos),
        client_id=data.get("client_id", "zev-pi-gateway"),
    )


def _parse_meters(data) -> list[MeterConfig]:
    if not isinstance(data, list) or not data:
        raise ConfigError("'zaehler' muss eine nicht-leere Liste sein.")

    meters: list[MeterConfig] = []
    seen_messpunkte: set[str] = set()

    for index, entry in enumerate(data):
        if not isinstance(entry, dict):
            raise ConfigError(f"zaehler[{index}] muss ein Mapping sein.")

        messpunkt = _require(entry, "messpunkt", str, ctx=f"zaehler[{index}]")
        if messpunkt in seen_messpunkte:
            raise ConfigError(
                f"messpunkt '{messpunkt}' ist nicht eindeutig "
                f"(mehrfach in der Zähler-Liste)."
            )
        seen_messpunkte.add(messpunkt)

        protokoll = _require(entry, "protokoll", str, ctx=f"zaehler[{index}]")
        if protokoll not in _SUPPORTED_PROTOCOLS:
            raise ConfigError(
                f"zaehler[{index}] '{messpunkt}': Protokoll '{protokoll}' wird noch "
                f"nicht unterstützt (verfügbar: {sorted(_SUPPORTED_PROTOCOLS)})."
            )

        register = _require(entry, "register", dict, ctx=f"zaehler[{index}] '{messpunkt}'")
        register_bezug = _parse_register(register.get("bezug"), messpunkt, "bezug")
        register_einspeisung = _parse_register(
            register.get("einspeisung"), messpunkt, "einspeisung"
        )

        meters.append(
            MeterConfig(
                messpunkt=messpunkt,
                protokoll=protokoll,
                host=_require(entry, "host", str, ctx=f"zaehler[{index}] '{messpunkt}'"),
                port=int(entry.get("port", 502)),
                unit_id=int(entry.get("unit_id", 1)),
                register_bezug=register_bezug,
                register_einspeisung=register_einspeisung,
            )
        )

    return meters


def _parse_register(data, messpunkt: str, rolle: str) -> RegisterSpec:
    ctx = f"zaehler '{messpunkt}' register.{rolle}"
    if not isinstance(data, dict):
        raise ConfigError(f"{ctx} fehlt oder ist kein Mapping.")

    addr = _parse_addr(_require(data, "addr", (int, str), ctx=ctx), ctx)
    typ = data.get("typ", "float32")
    if typ not in _SUPPORTED_REGISTER_TYPES:
        raise ConfigError(
            f"{ctx}: typ '{typ}' nicht unterstützt (verfügbar: "
            f"{sorted(_SUPPORTED_REGISTER_TYPES)})."
        )
    wortfolge = data.get("wortfolge", "big")
    if wortfolge not in _SUPPORTED_WORD_ORDERS:
        raise ConfigError(
            f"{ctx}: wortfolge '{wortfolge}' ungültig "
            f"(verfügbar: {sorted(_SUPPORTED_WORD_ORDERS)})."
        )

    return RegisterSpec(
        addr=addr,
        typ=typ,
        wortfolge=wortfolge,
        skalierung=float(data.get("skalierung", 1.0)),
    )


def _parse_addr(value: int | str, ctx: str) -> int:
    """Registeradresse. Konvention (eindeutig):

    - **Integer** (z. B. ``24588``) → Dezimal.
    - **String** (z. B. ``"600C"`` oder ``"0x600C"``) → **Hexadezimal**, so wie im
      Wago-Datenblatt notiert. Deshalb Hex-Adressen in der YAML immer quoten.
    """
    if isinstance(value, int):
        return value
    text = value.strip().lower().removeprefix("0x")
    try:
        return int(text, 16)
    except ValueError as exc:
        raise ConfigError(
            f"{ctx}: addr '{value}' ist keine gültige Hex-Adresse "
            f"(String = Hex, Integer = Dezimal)."
        ) from exc


def _parse_interval(value: str | int) -> int:
    """'5m'/'30s'/'1h' oder reine Sekunden-Zahl → Sekunden (> 0)."""
    if isinstance(value, int):
        seconds = value
    else:
        text = str(value).strip().lower()
        if text and text[-1] in _INTERVAL_UNITS:
            try:
                seconds = int(text[:-1]) * _INTERVAL_UNITS[text[-1]]
            except ValueError as exc:
                raise ConfigError(f"publish_interval '{value}' ungültig.") from exc
        else:
            try:
                seconds = int(text)
            except ValueError as exc:
                raise ConfigError(f"publish_interval '{value}' ungültig.") from exc

    if seconds <= 0:
        raise ConfigError("publish_interval muss > 0 sein.")
    return seconds


def _require(data: dict, key: str, expected_type, ctx: str | None = None):
    """Pflichtfeld holen und Typ prüfen."""
    where = f"{ctx}." if ctx else ""
    if key not in data or data[key] is None:
        raise ConfigError(f"Pflichtfeld '{where}{key}' fehlt.")
    value = data[key]
    if not isinstance(value, expected_type):
        raise ConfigError(f"Feld '{where}{key}' hat falschen Typ (war: {type(value).__name__}).")
    return value
