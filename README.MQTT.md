# ZEV-Stack mit MQTT (`docker-compose-mqtt.yml`)

Startet den ZEV-Stack **inklusive MQTT-Broker** und dem Backend mit aktivem Spring-Profil
`mqtt` (MQTT-Subscriber + Aggregations-Job) – **ohne** Prometheus/Grafana. Damit lässt sich
der komplette MQTT-Workflow lokal end-to-end testen:

```
Publisher-Simulator → Mosquitto → Backend-Ingest → zev.zaehler_rohdaten
                                                  → Aggregations-Job → zev.messwerte
```

Referenzen: [`Specs/MQTT-Integration.md`](./Specs/MQTT-Integration.md) (Backend-Vertrag),
[`pi-gateway/README.md`](./pi-gateway/README.md) (Simulator/Publisher).

## Enthaltene Services

| Service          | Port        | Zweck                                             |
|------------------|-------------|---------------------------------------------------|
| postgres         | 5432        | Datenbank (`zev`)                                 |
| mosquitto        | 1883        | MQTT-Broker (Auth aktiv, Config ins Image gebacken) |
| keycloak         | 9000        | OAuth2/JWT (`--import-realm`)                      |
| backend-service  | 8090        | Spring Boot, **Profil `mqtt`** (Subscriber + Job) |
| admin-service    | 8081        | Spring Boot Admin                                 |
| frontend-service | 4200 → 8080 | Angular (via Spring Boot)                         |

**Nicht enthalten:** Prometheus, Grafana.

## Voraussetzungen
- Docker & Docker Compose
- Für den Simulator: Python ≥ 3.9 (siehe `pi-gateway/README.md`)

## Zugangsdaten (nur Entwicklung)
- **MQTT-Broker:** `zev-backend` / `zev-mqtt-dev` (in `.env.mqtt.example` und der gebackenen
  `pi-gateway/deploy/mosquitto/passwd`). **Nicht** produktiv verwenden.
- Keycloak-Admin `admin` / `admin`, PostgreSQL `postgres` / `postgres` (aus `.env.mqtt`).
- Keycloak-Testuser wie im Haupt-Stack (`testuser`/`testpassword`, …).

## Schnellstart

```bash
# 1) Env-Datei aus der Vorlage anlegen (einmalig)
cp .env.mqtt.example .env.mqtt

# 2) Stack bauen und starten
docker compose --env-file .env.mqtt -f docker-compose-mqtt.yml up --build
```

> **Wichtig:** Bei **jedem** Compose-Aufruf **beide** Flags angeben – `--env-file .env.mqtt`
> **und** `-f docker-compose-mqtt.yml`. Ohne `--env-file` zieht Compose die allgemeine `.env`
> für die `${...}`-Interpolation (nicht `.env.mqtt`); ohne `-f` das Standard-`docker-compose.yml`.
> Die Flags stehen **vor** dem Unterbefehl (`up`, `logs`, …).

Zugriffspunkte nach dem Start: Frontend http://localhost:4200 · Backend http://localhost:8090 ·
Keycloak http://localhost:9000 · Admin http://localhost:8081 · MQTT `tcp://localhost:1883`.

## End-to-End-Test mit dem Publisher-Simulator

Der Stack empfängt nur Nachrichten, wenn Publisher senden. Ohne echten Pi nutzt man den
**Simulator** (`protokoll: sim`) aus `pi-gateway/`.

**1) Einheiten anlegen** – im Frontend (oder per API) je `messpunkt` aus der Sim-Config eine
Einheit mit **`org_id = 42`** anlegen. Standard-Messpunkte der Vorlage:
`MP-Wohnung-1`, `MP-Wohnung-2`, `MP-Producer`. Fehlt eine Einheit, verwirft der Ingest die
Nachricht mit „unbekannter Messpunkt".

**2) Simulator starten** (eigenes Terminal, auf dem Host).

**Windows (PowerShell)** – Schritt für Schritt aus dem Repo-Root `C:\data\git\ZEV`:

```powershell
cd pi-gateway

# venv anlegen (py-Launcher; alternativ 'python')
py -3 -m venv .venv

# venv aktivieren:
.\.venv\Scripts\Activate.ps1
# Falls PowerShell die Aktivierung blockiert ("... kann nicht geladen werden ..."),
# einmalig für DIESE Sitzung erlauben und erneut aktivieren:
#   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
# (oder ohne Aktivierung direkt die venv-Python nutzen: .\.venv\Scripts\python.exe -m gateway.main --config config.sim.yaml)

# Abhängigkeiten installieren
pip install -r requirements.txt

# Konfiguration aus Vorlage kopieren (ggf. messpunkte/org_id anpassen)
Copy-Item config.sim.example.yaml config.sim.yaml

# Broker-Credentials für DIESE Sitzung setzen (Dev-Werte):
$env:MQTT_USERNAME = "zev-backend"
$env:MQTT_PASSWORD = "zev-mqtt-dev"

# Simulator starten (läuft dauerhaft; mit Strg+C beenden)
python -m gateway.main --config config.sim.yaml
```

> Hinweise (PowerShell):
> - `py -3` wählt die neueste installierte Python-3-Version; alternativ `py -3.9` oder `python`.
> - `$env:VAR = "wert"` gilt nur in der **aktuellen** PowerShell-Sitzung. Neues Fenster → erneut setzen.
> - Die venv-Aktivierung ändert den Prompt zu `(.venv)`. Danach genügt `python`.

**Linux/macOS (bash):**

```bash
cd pi-gateway
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
cp config.sim.example.yaml config.sim.yaml
export MQTT_USERNAME=zev-backend MQTT_PASSWORD=zev-mqtt-dev
python -m gateway.main --config config.sim.yaml
```

Der Simulator publiziert alle 5 s absolute, monoton steigende Zählerstände
(`"producer"` im Messpunkt → Einspeisung überwiegt = negatives `total`). Der Zeitstempel
wird in **lokaler Zeit mit UTC-Offset** gesendet (z. B. `2026-07-10T14:30:00+02:00`).

### Aktualisierten Simulator neu starten (nach Code-Änderung)

Der Simulator läuft direkt aus dem Quellcode (`python -m gateway.main`), daher werden geänderte
`.py`-Dateien beim nächsten Start automatisch übernommen – **kein erneutes `pip install` nötig**
(solange sich `requirements.txt` nicht geändert hat). venv und `config.sim.yaml` bleiben bestehen.

**Windows (PowerShell)** – im vorhandenen Simulator-Fenster **Strg+C** (alten Lauf beenden), dann:

```powershell
cd C:\data\git\ZEV\pi-gateway
.\.venv\Scripts\Activate.ps1                # falls die Sitzung neu ist
$env:MQTT_USERNAME = "zev-backend"          # nur nötig, wenn in dieser Sitzung noch nicht gesetzt
$env:MQTT_PASSWORD = "zev-mqtt-dev"
python -m gateway.main --config config.sim.yaml
```

**Linux/macOS (bash):**

```bash
cd pi-gateway && . .venv/bin/activate
export MQTT_USERNAME=zev-backend MQTT_PASSWORD=zev-mqtt-dev
python -m gateway.main --config config.sim.yaml
```

> **Wichtig:** Die Zeitzonen-Korrektur steckt zum grössten Teil im **Backend** (Payload-Format,
> verbatim-Speicherung, `TZ=Europe/Zurich`). Nach Code-Änderungen daher auch das Backend neu bauen:
> ```powershell
> docker compose --env-file .env.mqtt -f docker-compose-mqtt.yml up --build -d backend-service
> ```
> Für einen sauberen Test vorher die alten (ggf. UTC-Raster-)Testdaten entfernen –
> `... down -v` (inkl. DB-Volume) oder gezielt `TRUNCATE zev.zaehler_rohdaten;`
> und `DELETE FROM zev.messwerte WHERE quelle='MQTT';`.

**3) Prüfen**
- `zev.zaehler_rohdaten` füllt sich laufend (Ingest).
- Nach der nächsten Viertelstunde (`:00/:15/:30/:45`) schreibt der Aggregations-Job
  `zev.messwerte` (`total` vorzeichenbehaftet, `zev = 0`, `quelle = 'MQTT'`).
- Nachrichten mitlesen (optional):
  ```bash
  mosquitto_sub -h localhost -t 'zev/#' -v -u zev-backend -P zev-mqtt-dev
  ```
- Health/Metriken:
  `curl http://localhost:8090/actuator/health/mqtt` ·
  `curl -s http://localhost:8090/actuator/prometheus | grep zev_mqtt`

## Nützliche Befehle

```bash
# im Hintergrund starten
docker compose --env-file .env.mqtt -f docker-compose-mqtt.yml up --build -d

# Logs (nur Backend)
docker compose --env-file .env.mqtt -f docker-compose-mqtt.yml logs -f backend-service

# stoppen
docker compose --env-file .env.mqtt -f docker-compose-mqtt.yml down

# stoppen inkl. Datenbank-Volume (frischer Start)
docker compose --env-file .env.mqtt -f docker-compose-mqtt.yml down -v
```

## Troubleshooting
- **Alle MQTT-Nachrichten werden verworfen** → Einheiten mit passendem `messpunkt` **und**
  `org_id = 42` fehlen, oder der Simulator nutzt eine andere `org_id`.
- **Simulator: „Umgebungsvariable ${MQTT_USERNAME} ist nicht gesetzt"** → vor dem Start
  `MQTT_USERNAME`/`MQTT_PASSWORD` exportieren (siehe oben).
- **Broker lehnt Verbindung ab („not authorised")** → falsche Credentials; Dev-Werte sind
  `zev-backend` / `zev-mqtt-dev`.
- **Backend baut keine MQTT-Verbindung auf** → läuft es mit Profil `mqtt`? Im Compose ist
  `SPRING_PROFILES_ACTIVE=mqtt` gesetzt; ohne dieses Profil startet kein Subscriber/Job.
- **Compose nimmt falsche Werte** → `--env-file .env.mqtt` vergessen (siehe „Wichtig" oben).

## Externer Broker (statt lokalem Mosquitto)
Für einen echten Broker (z. B. auf dem NAS) in `.env.mqtt` `MQTT_BROKER_URL` sowie eigene
`MQTT_BROKER_USERNAME`/`MQTT_BROKER_PASSWORD` setzen. Produktionsbetrieb des Brokers (getrennte
User pro Publisher/Subscriber, ACL, TLS) siehe [`docs/Netzwerk-Topologie-Hene.md`](./docs/Netzwerk-Topologie-Hene.md).
