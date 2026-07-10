# ZEV - Zusammenschluss zum Eigenverbrauch

Solar power distribution application for managing fair allocation of solar energy among consumers in a ZEV (self-consumption community).

## Prerequisites

- Java 25
- Maven 3.6+
- Node.js 20.19.0+
- Docker & Docker Compose

## Architecture

Multi-module Maven project (Spring Boot 4 / Angular 21 / PostgreSQL 16 / Keycloak):
- **backend-service**: Spring Boot REST API (Port 8090)
- **frontend-service**: Angular-App, als Spring-Boot-JAR ausgeliefert (Port 4200 → 8080 im Container)
- **admin-service**: Spring Boot Admin Monitoring (Port 8081)
- **design-system**: wiederverwendbare UI-Komponenten/Styles (`@zev/design-system`)

Weitere Verzeichnisse: `keycloak/` (Realm-Import), `pi-gateway/` (Raspberry-Pi Reader/Publisher für MQTT),
`Specs/` (Feature-Spezifikationen), `docs/` (Zusatz-Doku), `prometheus/` + `grafana/` (Monitoring).

> Ausführliche Architektur-, API- und Konventionsdoku: **[`CLAUDE.md`](./CLAUDE.md)**.

## Build & Test

```bash
mvn clean compile test
```

## Database Setup

Start PostgreSQL:

```bash
docker-compose up -d postgres
```

Run Flyway migrations:

```bash
cd backend-service
mvn flyway:migrate
```

## Run Application

```bash
docker-compose up --build
```

Access:
- Frontend: http://localhost:4200
- Backend API: http://localhost:8090
- Admin Service: http://localhost:8081
- Keycloak: http://localhost:9000 (admin/admin)
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)

**Environment:** `.env.example` nach `.env` kopieren und `ANTHROPIC_API_KEY` setzen (für die KI-gestützte Einheiten-Zuordnung beim CSV-Upload).

### Test-Benutzer (Keycloak)

| Benutzer | Passwort | Fachrolle |
|----------|----------|-----------|
| `testuser` | `testpassword` | `zev_admin` (alle Permissions) |
| `orgadmin` | `orgadminpassword` | `org_admin` |
| `user` | `password` | `zev_user` |

## Betriebsmodi (mit / ohne MQTT)

Das Backend läuft in zwei Modi, gesteuert über das Spring-Profil `mqtt`:

| Modus | Start | MQTT-Subscriber & Aggregations-Job | Messwert-Erfassung |
|-------|-------|------------------------------------|--------------------|
| **Standard (ohne MQTT)** | `docker-compose up --build` | inaktiv (kein `mqtt`-Profil) | nur CSV-Upload |
| **Mit MQTT** | `docker compose --env-file .env.mqtt -f docker-compose-mqtt.yml up --build` | aktiv (`SPRING_PROFILES_ACTIVE=mqtt`) | CSV-Upload **und** MQTT-Ingest |

- Ohne aktives Profil `mqtt` werden **keine** MQTT-Beans gestartet (kein Broker-Client, kein
  Scheduled-Aggregations-Job); die Anwendung verhält sich wie bisher (CSV-Upload).
- Der MQTT-Stack enthält zusätzlich einen **Mosquitto-Broker** und **kein** Prometheus/Grafana.
- Vollständige Anleitung (inkl. Publisher-Simulator zum End-to-End-Test): **[`README.MQTT.md`](./README.MQTT.md)**.
- Fachliche Details des MQTT-Ingests: [`Specs/MQTT-Integration.md`](./Specs/MQTT-Integration.md).

## Project Structure

```
backend-service/     # Spring Boot REST API (Controller → Service → Repository → Entity),
                     #   JasperReports-PDFs, Swiss QR-Bill, Keycloak-JWT, Flyway-Migrationen,
                     #   MQTT-Subscriber (nur mit Profil 'mqtt')
frontend-service/    # Angular 21; via frontend-maven-plugin gebaut und als Spring-Boot-JAR
                     #   ausgeliefert (SpaRedirectController)
admin-service/       # Spring Boot Admin (Monitoring)
design-system/       # @zev/design-system – CSS-Komponenten + Design-Tokens
keycloak/            # Realm-Import (Rollen/Permissions, Test-User)
pi-gateway/          # Raspberry-Pi Reader/Publisher (Zähler → MQTT), eigenes Python-Artefakt
Specs/               # Feature-Spezifikationen + Umsetzungspläne
docs/                # Zusatzdokumentation (Keycloak, Netzwerk-Topologie, ArchUnit, …)
prometheus/ grafana/ # Monitoring-Stack
```

> Detaillierte Package-Struktur, API-Endpunkte, Berechtigungen und Code-Vorlagen: **[`CLAUDE.md`](./CLAUDE.md)**.

## Algorithm

Die Solarverteilung wird über zwei wählbare Algorithmen berechnet:

- **`SolarDistribution`** (gleiche Anteile): Ist die Solarproduktion ≥ Gesamtverbrauch, erhält jeder
  Verbraucher seinen vollen Bedarf; sonst wird gleichmässig verteilt.
- **`ProportionalConsumptionDistribution`** (proportional): Verbraucher mit höherem Verbrauch
  erhalten einen proportional grösseren Anteil.

Das Ergebnis wird als `zev_calculated` je Messwert gespeichert.
