# ZEV Pi-Gateway

Reader-/Publisher-Software für den Raspberry Pi am Zählerstandort: liest die
Stromzähler (aktuell **Wago via Modbus TCP**) aus und **publiziert deren absolute
Zählerstände** (Wirkenergie in kWh) per MQTT an den Broker. Die Delta-/Intervall-
Bildung übernimmt das **Backend** – der Pi ist damit weitgehend zustandslos und
verlusttolerant.

- **Spezifikation:** [`Specs/Pi-Gateway-Software.md`](../Specs/Pi-Gateway-Software.md)
- **Umsetzungsplan:** [`Specs/Pi-Gateway-Software_Umsetzungsplan.md`](../Specs/Pi-Gateway-Software_Umsetzungsplan.md)
- **MQTT-Vertrag (Backend):** [`Specs/MQTT-Integration.md`](../Specs/MQTT-Integration.md)
- **Topologie/Broker:** [`docs/Netzwerk-Topologie-Hene.md`](../docs/Netzwerk-Topologie-Hene.md)

> Dies ist ein **eigenständiges Python-Artefakt**, nicht Teil der ZEV-Java/Angular-App.

## Architektur

```
config.yaml ─► config.py ─► main.py (Read-Loop)
                               │
              factory.py ──────┤ pro Zähler: Reader.read()  ──► MeterReading (absolute Stände)
              modbus_reader ───┘                                      │
                                                              publisher.py ──► MQTT
                                                       zev/{orgId}/{messpunkt}/messwert
```

- `gateway/config.py` – Laden/Validieren der Konfiguration (Zähler-Liste beliebiger Länge)
- `gateway/readers/factory.py` – erzeugt je `protokoll` den passenden Reader (erweiterbar)
- `gateway/readers/modbus_reader.py` – Wago (Modbus TCP), float32-Wirkenergie
- `gateway/readers/gplug_reader.py` – BKW/gPlug (**Platzhalter, folgt später**)
- `gateway/publisher.py` – MQTT-Publish der absoluten Stände (Topic/Payload gemäss Vertrag)
- `gateway/main.py` – Orchestrierung, Read-Loop, Signal-Handling, Heartbeat

## Payload-Vertrag

Topic: `zev/{orgId}/{messpunkt}/messwert`

```json
{ "timestamp": "2026-06-19T14:30:00Z", "zaehlerstandBezug": 12345.678, "zaehlerstandEinspeisung": 4321.0 }
```

`timestamp` in UTC (ISO 8601), Stände in kWh (kumulativ, ≥ 0). Muss byte-genau zu
`Specs/MQTT-Integration.md` passen.

## Konfiguration

Vorlage: [`config.example.yaml`](./config.example.yaml). Nach `config.yaml` kopieren
und anpassen. Die Zähler stehen als **Liste beliebiger Länge** (typisch 10–20+);
ein zusätzlicher Zähler = nur ein weiterer Listeneintrag (kein Code-Change).

- **Secrets** kommen nicht in die YAML, sondern werden über `${MQTT_USERNAME}` /
  `${MQTT_PASSWORD}` aus der Umgebung aufgelöst (siehe `.env.example`).
- **Registeradressen:** als String = **Hexadezimal** (wie Wago-Datenblatt, daher quoten),
  als Integer = Dezimal.
- **Intervall:** `publish_interval` z. B. `5m`, `30s`, `1h` oder reine Sekunden-Zahl.

## Lokale Installation (Entwicklung)

```bash
cd pi-gateway
python3 -m venv .venv
. .venv/bin/activate
pip install -e .            # bzw. pip install -r requirements.txt

cp config.example.yaml config.yaml   # anpassen
export MQTT_USERNAME=pi-org42 MQTT_PASSWORD=...
python -m gateway.main --config config.yaml
```

## Diagnose der Zähler (mbpoll)

```bash
sudo apt install mbpoll
# Modbus TCP, Unit-ID 1, 32-bit-Float ab Register (Hex-Adresse), einmalig:
mbpoll -a 1 -t 4:float -r <register> -c 2 -1 <zaehler-ip> -p 502
```

Konkrete Register/Skalierung stammen aus dem Wago-Datenblatt.

## Deployment auf dem Pi (systemd)

Vollständige Schritt-für-Schritt-Anleitung: **Anhang A** in
[`Specs/Pi-Gateway-Software.md`](../Specs/Pi-Gateway-Software.md).

Kurzfassung:

```bash
# 1) Dienst-Nutzer + Verzeichnis
sudo useradd --system --no-create-home --shell /usr/sbin/nologin pigw
sudo mkdir -p /opt/pi-gateway && sudo chown -R pigw:pigw /opt/pi-gateway
# ... Code nach /opt/pi-gateway kopieren ...

# 2) venv + Abhängigkeiten
sudo -u pigw python3 -m venv /opt/pi-gateway/.venv
sudo -u pigw /opt/pi-gateway/.venv/bin/pip install -r /opt/pi-gateway/requirements.txt

# 3) Konfiguration + Secrets (root-only Env-Datei)
sudo install -o pigw -g pigw -m 640 config.yaml /opt/pi-gateway/config.yaml
sudo install -m 600 -o root -g root .env /etc/pi-gateway.env   # MQTT_USERNAME/PASSWORD

# 4) systemd-Unit
sudo install -m 644 deploy/pi-gateway.service /etc/systemd/system/pi-gateway.service
sudo systemctl daemon-reload
sudo systemctl enable --now pi-gateway.service

# 5) Status/Logs
systemctl status pi-gateway.service
journalctl -u pi-gateway.service -f
```

> **NTP:** Korrekte UTC-Zeitstempel sind abrechnungskritisch – sicherstellen, dass
> die Uhr synchron ist (`timedatectl status`).

## Stand / Abgrenzung

- **Umgesetzt:** Projekt-Setup, Konfiguration, Modbus-Reader (Wago), MQTT-Publisher,
  Read-Loop mit Fehlerisolation je Zähler, Reconnect/Backoff, Heartbeat-Logging, systemd.
- **Später:** gPlug-Reader (BKW), optionale Pufferung (aktuell nicht nötig – absolute
  Stände sind verlusttolerant).
- **Tests:** werden separat erstellt (nicht Teil der Umsetzung).
