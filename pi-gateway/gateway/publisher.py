"""MQTT-Publisher: publiziert absolute Zählerstände gemäss Vertrag.

Topic:   zev/{orgId}/{messpunkt}/messwert
Payload: {"timestamp": "...Z", "zaehlerstandBezug": ..., "zaehlerstandEinspeisung": ...}

Der Pi publiziert die gelesenen Stände unverändert (keine Delta-Bildung); die
Aggregation erfolgt im Backend (Specs/MQTT-Integration.md).
"""

from __future__ import annotations

import json
import logging
from datetime import UTC
from urllib.parse import urlparse

import paho.mqtt.client as mqtt

from .models import BrokerConfig, MeterReading

log = logging.getLogger(__name__)

# kWh mit 4 Nachkommastellen entspricht der Backend-Spalte DECIMAL(14,4).
_KWH_DECIMALS = 4

_DEFAULT_PORTS = {"tcp": 1883, "mqtt": 1883, "tls": 8883, "mqtts": 8883, "ssl": 8883}
_TLS_SCHEMES = {"tls", "mqtts", "ssl"}


class MqttPublisher:
    """Kapselt die MQTT-Verbindung und den Publish der Zählerstände."""

    def __init__(self, org_id: int, broker: BrokerConfig) -> None:
        self._org_id = org_id
        self._broker = broker
        self._connected = False

        scheme, host, port = _parse_broker_url(broker.url)
        self._host = host
        self._port = port

        self._client = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id=broker.client_id,
            clean_session=True,
        )
        if broker.username:
            self._client.username_pw_set(broker.username, broker.password)
        if scheme in _TLS_SCHEMES:
            self._client.tls_set()

        # Automatischer Reconnect mit Backoff (1s .. 120s), vom paho-Loop getrieben.
        self._client.reconnect_delay_set(min_delay=1, max_delay=120)
        self._client.on_connect = self._on_connect
        self._client.on_disconnect = self._on_disconnect

    def connect(self) -> None:
        """Baut die Verbindung auf und startet den Netzwerk-Loop im Hintergrund."""
        log.info("Verbinde mit MQTT-Broker %s:%s ...", self._host, self._port)
        self._client.connect_async(self._host, self._port, keepalive=60)
        self._client.loop_start()

    def publish(self, reading: MeterReading) -> bool:
        """Publiziert einen Zählerstand. Gibt True bei erfolgreichem Enqueue zurück."""
        topic = f"zev/{self._org_id}/{reading.messpunkt}/messwert"
        payload = json.dumps(_to_payload(reading), separators=(",", ":"))

        info = self._client.publish(topic, payload, qos=self._broker.qos)
        if info.rc != mqtt.MQTT_ERR_SUCCESS:
            log.warning(
                "Publish für '%s' fehlgeschlagen (rc=%s) – Broker vermutlich offline.",
                reading.messpunkt,
                info.rc,
            )
            return False

        log.debug("Publiziert %s → %s", topic, payload)
        return True

    def is_connected(self) -> bool:
        return self._connected

    def disconnect(self) -> None:
        self._client.loop_stop()
        self._client.disconnect()

    # --- paho-Callbacks (API v2) -------------------------------------------------

    def _on_connect(self, client, userdata, flags, reason_code, properties=None) -> None:
        if reason_code.is_failure:
            self._connected = False
            log.error("MQTT-Verbindung abgelehnt: %s", reason_code)
        else:
            self._connected = True
            log.info("MQTT-Broker verbunden (%s:%s).", self._host, self._port)

    def _on_disconnect(self, client, userdata, flags, reason_code, properties=None) -> None:
        self._connected = False
        if reason_code.is_failure:
            log.warning("MQTT-Verbindung verloren (%s) – automatischer Reconnect läuft.",
                        reason_code)


def _to_payload(reading: MeterReading) -> dict:
    """Baut das Vertrags-JSON (UTC-Zeitstempel mit 'Z', Stände in kWh)."""
    timestamp = reading.timestamp.astimezone(UTC).replace(microsecond=0)
    return {
        "timestamp": timestamp.isoformat().replace("+00:00", "Z"),
        "zaehlerstandBezug": round(reading.zaehlerstand_bezug, _KWH_DECIMALS),
        "zaehlerstandEinspeisung": round(reading.zaehlerstand_einspeisung, _KWH_DECIMALS),
    }


def _parse_broker_url(url: str) -> tuple[str, str, int]:
    """Zerlegt tcp://host:port bzw. tls://host:port in (scheme, host, port)."""
    parsed = urlparse(url)
    scheme = (parsed.scheme or "tcp").lower()
    if not parsed.hostname:
        raise ValueError(f"broker.url ohne Host: '{url}'")
    port = parsed.port or _DEFAULT_PORTS.get(scheme, 1883)
    return scheme, parsed.hostname, port
