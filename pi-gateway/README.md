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
{ "timestamp": "2026-06-19T14:30:00+02:00", "zaehlerstandBezug": 12345.678, "zaehlerstandEinspeisung": 4321.0 }
```

`timestamp` in lokaler Zeit mit UTC-Offset (ISO 8601), Stände in kWh (kumulativ, ≥ 0).
Muss byte-genau zu `Specs/MQTT-Integration.md` passen.

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

## Publisher-Simulator (lokaler End-to-End-Test)

Statt echter Zähler kann ein **synthetischer Reader** (`protokoll: sim`) monoton steigende
Zählerstände erzeugen und über den **echten** Publish-Pfad (Topic/Payload, `MqttPublisher`)
senden. Damit lässt sich der komplette MQTT-Workflow ohne Pi/Hardware testen:
`Simulator → Broker → Backend-Ingest → zaehler_rohdaten → Aggregations-Job → messwerte`.

Vorlage: [`config.sim.example.yaml`](./config.sim.example.yaml) (Zähler mit `protokoll: sim`).
Das Verhalten steuert der `messpunkt`-Name: `"producer"` → Einspeisung überwiegt (negatives
`total`); `"bezug"` bzw. `"rücklieferung"` → Bilanzmesspunkt am Netzanschluss (nur Bezug- bzw.
nur Einspeisungs-Register wächst); sonst Consumer (positives `total`).

Der Broker erzwingt Auth (Username/Passwort). Dev-Credentials: `zev-backend` / `zev-mqtt-dev`
(Default in `.env.mqtt.example` → `MOSQUITTO_USERS`).

**1) Broker + Backend starten** – am einfachsten über den MQTT-Stack (enthält Broker `mosquitto`
und Backend mit Profil `mqtt`), aus dem Repo-Root:

```bash
cp .env.mqtt.example .env.mqtt
docker compose --env-file .env.mqtt -f docker-compose-mqtt.yml up --build
```

<details><summary>Alternative: nur ein Wegwerf-Broker (Backend separat)</summary>

```bash
# aus dem Repo-Root; Config ins Image gebacken, passwd zur Laufzeit aus MOSQUITTO_USERS (Auth aktiv)
docker build -t zev-mosquitto pi-gateway/deploy/mosquitto
docker run --rm -it -p 1883:1883 -e MOSQUITTO_USERS='zev-backend:zev-mqtt-dev' zev-mosquitto
```
</details>

**2) Einheiten anlegen** – je `messpunkt` aus der Sim-Config muss eine Einheit mit
passendem `org_id` (hier `42`) existieren, sonst verwirft der Ingest „unbekannter Messpunkt".

**3) Simulator starten** (mit Broker-Credentials; venv/Abhängigkeiten siehe
[Lokale Installation](#lokale-installation-entwicklung)):

```bash
cd pi-gateway
cp config.sim.example.yaml config.sim.yaml     # ggf. messpunkte/org_id anpassen
export MQTT_USERNAME=zev-backend MQTT_PASSWORD=zev-mqtt-dev
python -m gateway.main --config config.sim.yaml
```

<details><summary>Windows/PowerShell (inkl. venv-Setup)</summary>

```powershell
cd pi-gateway

# Einmalig: venv anlegen und Abhängigkeiten installieren
python -m venv .venv
.\.venv\Scripts\Activate.ps1   # bei Execution-Policy-Fehler vorher: Set-ExecutionPolicy -Scope Process RemoteSigned
pip install -r requirements.txt

Copy-Item config.sim.example.yaml config.sim.yaml   # ggf. messpunkte/org_id anpassen

# Broker-Credentials (Dev-Defaults)
$env:MQTT_USERNAME = "zev-backend"
$env:MQTT_PASSWORD = "zev-mqtt-dev"

python -m gateway.main --config config.sim.yaml
```

Bei späteren Starts genügen `Activate.ps1`, die beiden `$env:`-Variablen und der Start-Befehl;
beenden mit `Ctrl+C`.
</details>

**4) Prüfen:** `zev.zaehler_rohdaten` füllt sich laufend; nach der jeweils nächsten
Viertelstunde (`:00/:15/:30/:45`, Job-Läufe `:05/:20/:35/:50`) schreibt der Job `zev.messwerte`
(`total` vorzeichenbehaftet, `quelle = 'MQTT'`); die unmittelbar anschliessende Solarverteilung
setzt `zev`/`zev_calculated` (Producer: im ZEV konsumierter Anteil). Mitlesen der Nachrichten optional:
`mosquitto_sub -h localhost -t 'zev/#' -v -u zev-backend -P zev-mqtt-dev`.

## Broker-Zugangsdaten (`MOSQUITTO_USERS`)

Der Broker erzwingt Auth (`allow_anonymous false`). Die Zugangsdaten liegen **nicht** mehr als
Datei im Repo/Image, sondern werden beim Container-Start aus der Umgebungsvariablen
`MOSQUITTO_USERS` erzeugt: Das [Entrypoint-Skript](./deploy/mosquitto/docker-entrypoint.sh)
schreibt die `user:passwort`-Paare in `/mosquitto/config/passwd` und hasht sie in-place mit
`mosquitto_passwd -U` (PBKDF2 – kein Klartext im laufenden Container). So bleiben Secrets aus
Image und Repo; derselbe Broker taugt für Dev **und** NAS.

Format (whitespace-getrennte Paare; Passwörter ohne Whitespace, Doppelpunkt im Passwort erlaubt):

```dotenv
# in .env.mqtt (bzw. der NAS-.env)
MOSQUITTO_USERS=zev-backend:zev-mqtt-dev pi-org42:change-me
```

- **Backend-Subscriber:** einer der Einträge muss zu `MQTT_BROKER_USERNAME` /
  `MQTT_BROKER_PASSWORD` passen.
- **Pi-Publisher:** für den Pi einen eigenen User ergänzen (Produktion: getrennte User
  je Publisher/Subscriber, siehe Topologie-Doku); derselbe User/dasselbe Passwort dann in
  `/etc/pi-gateway.env` auf dem Pi (`MQTT_USERNAME`/`MQTT_PASSWORD`).

**Passwort ändern / User hinzufügen:** `MOSQUITTO_USERS` anpassen und den Broker neu starten
(kein Image-Rebuild nötig, da die passwd zur Laufzeit erzeugt wird):

```bash
docker compose --env-file .env.mqtt -f docker-compose-mqtt.yml up -d mosquitto
```

> **Zeilenenden:** `docker-entrypoint.sh` und `mosquitto.conf` müssen **LF** behalten (via
> `.gitattributes` erzwungen) – CRLF bricht den `#!/bin/sh`-Shebang bzw. das Config-Parsing.

## Deployment auf dem Pi (systemd)

Vollständige Schritt-für-Schritt-Anleitung: **Anhang A** in
[`Specs/Pi-Gateway-Software.md`](../Specs/Pi-Gateway-Software.md).

**Paketierung (auf dem Bau-Rechner):** [`scripts/package-pi-gateway.ps1`](../scripts/package-pi-gateway.ps1)
packt diesen Ordner (ohne venv/Cache/Secrets/Dev-Broker) in `zev-pi-gateway.zip` zur Übertragung:

```powershell
./scripts/package-pi-gateway.ps1        # -> /data/ZEV/zev-pi-gateway.zip
# dann: scp zev-pi-gateway.zip <user>@<pi-host>:/home/pi/
```

Kurzfassung (auf dem Pi):

```bash
# 1) Dienst-Nutzer + Verzeichnis
sudo useradd --system --no-create-home --shell /usr/sbin/nologin pigw
sudo mkdir -p /opt/pi-gateway && sudo chown -R pigw:pigw /opt/pi-gateway
# Code aus dem ZIP entpacken:  unzip zev-pi-gateway.zip -d /tmp && sudo cp -r /tmp/pi-gateway/* /opt/pi-gateway/

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

> **NTP & Zeitzone:** Korrekte Zeitstempel sind abrechnungskritisch – sicherstellen, dass
> Uhr **und lokale Zeitzone** stimmen (`timedatectl status`; ggf.
> `sudo timedatectl set-timezone Europe/Zurich`). Der publizierte Zeitstempel trägt den lokalen Offset.

### Update (neuer Code auf bestehender Installation)

Neu paketieren + übertragen (Bau-Rechner, s.o.), dann auf dem Pi. `config.yaml`/`.env`/`.venv`
sind **nicht** im ZIP und bleiben erhalten. Vollständige Fassung: **Anhang A** in
[`Specs/Pi-Gateway-Software.md`](../Specs/Pi-Gateway-Software.md).

```bash
sudo systemctl stop pi-gateway.service
unzip -o zev-pi-gateway.zip -d /tmp/pi-gw-update
sudo cp -r /tmp/pi-gw-update/pi-gateway/. /opt/pi-gateway/   # überschreibt Code, nicht config/venv
sudo chown -R pigw:pigw /opt/pi-gateway
# Nur nötig, wenn sich requirements.txt geändert hat (z.B. pymodbus-Pin):
sudo -u pigw /opt/pi-gateway/.venv/bin/pip install -r /opt/pi-gateway/requirements.txt
sudo systemctl start pi-gateway.service
journalctl -u pi-gateway.service -f
```

## Stand / Abgrenzung

- **Umgesetzt:** Projekt-Setup, Konfiguration, Modbus-Reader (Wago), MQTT-Publisher,
  Read-Loop mit Fehlerisolation je Zähler, Reconnect/Backoff, Heartbeat-Logging, systemd.
- **Später:** gPlug-Reader (BKW), optionale Pufferung (aktuell nicht nötig – absolute
  Stände sind verlusttolerant).
- **Tests:** werden separat erstellt (nicht Teil der Umsetzung).
