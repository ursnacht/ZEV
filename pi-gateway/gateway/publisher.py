"""MQTT-Publisher: publiziert absolute Zählerstände gemäss Vertrag.

Topic:   zev/{orgId}/{messpunkt}/messwert
Payload: {"timestamp": "2026-07-10T14:30:00+02:00", "zaehlerstandBezug": ..., "zaehlerstandEinspeisung": ...}
         (lokale Zeit mit Offset; optional "seriennummer" und "zustand")

Nur der Simulator (protokoll: sim-gplug) erzeugt zusaetzlich die Meldung eines gPlugK:
Topic gPlug/{orgId}/{messpunkt}/tele/SENSOR, Tasmota-Payload, QoS 0
(Specs/Pi-Gateway-Software.md, FR-8.3; Specs/MQTT-Integration.md, FR-10).

Der Pi publiziert die gelesenen Stände unverändert (keine Delta-Bildung); die
Aggregation erfolgt im Backend (Specs/MQTT-Integration.md).
"""

from __future__ import annotations

import json
import logging
from urllib.parse import urlparse

import paho.mqtt.client as mqtt

from .models import BrokerConfig, MeterReading

log = logging.getLogger(__name__)

# kWh mit 4 Nachkommastellen entspricht der Backend-Spalte DECIMAL(14,4).
_KWH_DECIMALS = 4

# Ein gPlug liefert die Zaehlerstaende in Wh-Aufloesung, also kWh mit 3 Nachkommastellen.
_GPLUG_KWH_DECIMALS = 3
# Tasmota publiziert mit QoS 0 - der Simulator ebenso, damit er auch den Puffer des Brokers
# (queue_qos0_messages) so beansprucht wie ein echter gPlug.
_GPLUG_QOS = 0

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
        if reading.meldung == "gplug":
            topic = f"gPlug/{self._org_id}/{reading.messpunkt}/tele/SENSOR"
            payload = json.dumps(_to_gplug_payload(reading), separators=(",", ":"))
            qos = _GPLUG_QOS
        else:
            topic = f"zev/{self._org_id}/{reading.messpunkt}/messwert"
            payload = json.dumps(_to_payload(reading), separators=(",", ":"))
            qos = self._broker.qos

        info = self._client.publish(topic, payload, qos=qos)
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
    """Baut das Vertrags-JSON (Zeitstempel in LOKALER Zeit mit Offset, Stände in kWh).

    Der Zeitstempel wird als ISO-8601 in der lokalen Zeitzone des Pi mit UTC-Offset
    gesendet (z. B. ``2026-07-10T14:30:00+02:00``). Das ist eindeutig (kein
    DST-Doppelstunden-Problem) und das Backend speichert die lokale Wanduhrzeit verbatim.
    Voraussetzung: die lokale Zeitzone des Pi ist korrekt gesetzt (NTP + Zeitzone, FR-6.3).
    """
    timestamp = reading.timestamp.astimezone().replace(microsecond=0)
    payload = {
        "timestamp": timestamp.isoformat(),
        "zaehlerstandBezug": round(reading.zaehlerstand_bezug, _KWH_DECIMALS),
        "zaehlerstandEinspeisung": round(reading.zaehlerstand_einspeisung, _KWH_DECIMALS),
    }
    # Optionale Felder: nur aufnehmen, wenn vorhanden – so bleibt der Payload für Zähler ohne
    # Seriennummer bzw. ohne Zustandswerte unverändert zum bisherigen Vertrag.
    if reading.seriennummer:
        payload["seriennummer"] = reading.seriennummer
    # Ein LEERES Objekt waere kein neutraler Zusatz: Jede Nachricht truege dann ein Feld, das
    # nichts aussagt, und ein Leser muesste zwischen "nichts gemeldet" und "nichts vorhanden"
    # raten.
    if reading.zustand:
        payload["zustand"] = reading.zustand
    return payload


def _to_gplug_payload(reading: MeterReading) -> dict:
    """Baut die Tasmota-SENSOR-Meldung eines gPlugK (Specs/MQTT-Integration.md, FR-10.3).

    ``Time`` ist die lokale Zeit **ohne** Offset - so sendet Tasmota sie. Im Objekt ``z`` stehen
    die Zaehlerstaende (``Ei``/``Eo``, kWh), die Tarifregister (hier alles im Hochtarif), die
    Momentanleistung (``Pi``/``Po``, kW) und - falls konfiguriert - die Zaehlerkennung ``SMid``.
    Das Backend verwendet nur ``Time``, ``Ei``, ``Eo`` und ``SMid``; die uebrigen Felder sind
    dabei, damit der Simulator auch prueft, dass sie das Speichern nicht verhindern.
    """
    zeit = reading.timestamp.astimezone().replace(microsecond=0, tzinfo=None)
    ei = round(reading.zaehlerstand_bezug, _GPLUG_KWH_DECIMALS)
    eo = round(reading.zaehlerstand_einspeisung, _GPLUG_KWH_DECIMALS)
    z: dict = {}
    if reading.seriennummer:
        z["SMid"] = reading.seriennummer
    z["Pi"] = reading.leistung_bezug_kw if reading.leistung_bezug_kw is not None else 0.0
    z["Po"] = reading.leistung_einspeisung_kw if reading.leistung_einspeisung_kw is not None else 0.0
    z["Ei"] = ei
    z["Eo"] = eo
    z["Ei1"] = ei
    z["Ei2"] = 0.0
    z["Eo1"] = eo
    z["Eo2"] = 0.0
    return {"Time": zeit.isoformat(), "z": z}


def _parse_broker_url(url: str) -> tuple[str, str, int]:
    """Zerlegt tcp://host:port bzw. tls://host:port in (scheme, host, port)."""
    parsed = urlparse(url)
    scheme = (parsed.scheme or "tcp").lower()
    if not parsed.hostname:
        raise ValueError(f"broker.url ohne Host: '{url}'")
    port = parsed.port or _DEFAULT_PORTS.get(scheme, 1883)
    return scheme, parsed.hostname, port
