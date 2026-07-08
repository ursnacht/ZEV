# NAS-Deployment – Umsetzungsplan

## Zusammenfassung

Die ZEV-Anwendung wird für den Betrieb auf einer **Synology NAS** (Container Manager, DSM 7.2+) lauffähig gemacht: bereitgestellte Images (kein Build auf der NAS), eine NAS-Compose-Datei, sowie die **Entkopplung aller `localhost`-URLs** (Frontend-Config, Backend-Issuer/CORS, Keycloak-Redirect) — konfigurierbar ohne Quellcode-/JAR-Rebuild. Business-Logik und Rollenmodell bleiben unverändert.

Grundlage: [`Specs/NAS-Deployment.md`](./NAS-Deployment.md). Baut auf `docs/images.md` (Image-Transfer/Registry) und `docs/Anleitung-keycloak.md` (Keycloak-Setup) auf.

## Betroffene Komponenten

### Neu
- `docker-compose.nas.yml` – image-basiert (statt `build:`), Compose-Profiles `slim`/`full`
- `.env.nas.example` – NAS-Vorlage inkl. Host-URLs + geänderte Secrets
- `backend-service/src/main/resources/application-nas.yml` – Spring-Profil `nas` (CORS/Issuer/Datasource via Env-Platzhalter, Gerüst in `NAS-Deployment.md` FR-2.5)
- `docs/NAS-Deployment.md` – End-to-End-Installationsanleitung (Build → Transfer → Start → Keycloak-Setup)

### Geändert
- `frontend-service/Dockerfile` + Startup/Entrypoint – `assets/config.json` aus Env erzeugen (externe Static-Location)
- `frontend-service/src/main/resources/application.properties` (bzw. Spring-Config des Frontend-JARs) – zusätzliche externe Static-Location für `config.json`
- `backend-service/src/main/resources/application.yml` – sicherstellen, dass `app.cors.allowed-origins` per Env (`APP_CORS_ALLOWED_ORIGINS`) überschreibbar ist
- `keycloak/realms/zev-realm.json` – `redirectUris`/`webOrigins` per Platzhalter/Env parametrierbar
- `scripts/build-pi-images.ps1` → generalisieren auf `scripts/build-images.ps1` (Plattform-Parameter `linux/amd64` **und** `linux/arm64`)
- `docker-compose.yml` – Fremd-Image-Tags pinnen; Keycloak-Env `KC_BOOTSTRAP_ADMIN_*` prüfen
- `CLAUDE.md` – Verweis auf NAS-Deployment-Doku

## Umsetzungsreihenfolge (Phasen)

| Status | Phase                                | Beschreibung                                                                                                                              |
|--------|--------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
|  [ ]   | 1. Frontend-Config externalisieren   | `config.json` beim Containerstart aus Env (`API_BASE_URL`, `KEYCLOAK_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_CLIENT_ID`) erzeugen; Spring serviert externe Static-Location mit Vorrang → Override **ohne** Rebuild |
|  [ ]   | 2. Backend-URL-Konfig (Profil `nas`) | `application-nas.yml` (Profil `nas`) mit `issuer-uri`/`jwk-set-uri`/`APP_CORS_ALLOWED_ORIGINS`/Datasource als **Env-Platzhalter** (`${VAR:default}`); Aktivierung `SPRING_PROFILES_ACTIVE=nas`. Direkte Relaxed-Binding-Env-Vars behalten Vorrang. Siehe `NAS-Deployment.md` FR-2.5 |
|  [ ]   | 3. Keycloak-Client parametrieren     | `redirectUris`/`webOrigins` in `zev-realm.json` per Platzhalter (Realm-Import-Env-Substitution) oder dokumentierter Post-Install-Anpassung |
|  [ ]   | 4. Image-Build für Ziel-Arch         | `scripts/build-images.ps1` mit Plattform-Param (`linux/amd64` / `linux/arm64`); Ergebnis tar.gz **oder** Registry (vgl. `docs/images.md`) |
|  [ ]   | 5. NAS-Compose                       | `docker-compose.nas.yml`: `image:` statt `build:`, Fremd-Image-Tags gepinnt, Compose-Profiles `slim` (Postgres/Keycloak/Backend/Frontend) und `full` (+admin/prometheus/grafana), JVM-/Memory-Limits; `backend-service` mit `SPRING_PROFILES_ACTIVE=nas` |
|  [ ]   | 6. `.env.nas.example`                | Vorlage mit Host-URLs (`<nas>`), geänderten Passwörtern, optionalem `ANTHROPIC_API_KEY`                                                    |
|  [ ]   | 7. Keycloak-Betriebsmodus            | LAN/HTTP (`start-dev`) dokumentiert; HTTPS-Variante (`start` + `KC_HOSTNAME`/`KC_PROXY` hinter Synology-Reverse-Proxy) als Option — Details siehe Abschnitt „HTTPS / Reverse-Proxy (Synology)" |
|  [ ]   | 8. Post-Install Keycloak-Setup       | Anleitung: Organization anlegen, Client-Scope „organization" + Mapper, Nutzer zuordnen, Fachrollen/Composites prüfen                       |
|  [ ]   | 9. Installationsanleitung            | `docs/NAS-Deployment.md` (Container Manager, Ports/DSM-Konflikte, Volumes/Backup, Reboot-Verhalten, Troubleshooting `exec format error`/401/CORS) |
|  [ ]   | 10. Smoke-Test auf NAS               | Manueller End-to-End-Test (Login je Rolle, API-Call ohne 401/CORS, Reboot-Persistenz) gemäss Akzeptanzkriterien                           |
|  [ ]   | 11. Doku-Querverweise                | `CLAUDE.md` + `docs/images.md` auf NAS-Anleitung verlinken                                                                                 |

> **Deploy-Reihenfolge:** Phasen 1–3 (Konfigurierbarkeit) müssen vor dem NAS-Rollout fertig sein; sonst greifen die `localhost`-Defaults (→ 401/CORS/Redirect-Fehler).

## Konfigurations-Referenz (die zu setzenden Werte)

| Zweck                    | Ort / Env                                                                 | NAS-Wert (Beispiel LAN)                          |
|--------------------------|---------------------------------------------------------------------------|--------------------------------------------------|
| Frontend → API           | `config.json` `apiBaseUrl` (via `API_BASE_URL`)                           | `http://<nas>:8090`                              |
| Frontend → Keycloak      | `config.json` `keycloak.url` (via `KEYCLOAK_URL`)                         | `http://<nas>:9000`                              |
| Backend Token-Issuer     | `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI`                    | `http://<nas>:9000/realms/zev`                   |
| Backend CORS             | `APP_CORS_ALLOWED_ORIGINS`                                                | `http://<nas>:4200`                              |
| Keycloak-Client          | `zev-frontend` `redirectUris`/`webOrigins`                                | `http://<nas>:4200/*` / `http://<nas>:4200`      |
| Backend-Profil           | `SPRING_PROFILES_ACTIVE`                                                   | `nas` (bzw. `nas,mqtt`)                          |
| JWK-Set (intern)         | `..._JWK_SET_URI`                                                          | `http://keycloak:9000/...` (Container-Netz, bleibt) |

> **Invariante:** Frontend `keycloak.url` == Backend `issuer-uri`-Host == vom Browser genutzte Keycloak-URL. Bei HTTPS/Reverse-Proxy überall die Domain statt `<nas>:9000`.

## HTTPS / Reverse-Proxy (Synology) — Variante zu Phase 7

Synology bringt einen **integrierten Reverse Proxy** mit (technisch nginx, kein Zusatzpaket). Damit terminiert TLS an der NAS; die Container sprechen intern weiter HTTP. Da der DSM-Proxy **host-basiert** routet (Pfad-Routing schwach), wird **je Dienst eine Subdomain** verwendet.

### 1. Zertifikat
*Systemsteuerung → Sicherheit → Zertifikat* → Let's Encrypt (Ports 80/443 erreichbar bzw. DDNS). Zertifikat den Hostnamen zuweisen.

### 2. Reverse-Proxy-Regeln
*Systemsteuerung → Anmeldeportal → Erweitert → Reverse Proxy* (DSM 7.2). Je Regel Quelle (HTTPS/Hostname/443) → Ziel (HTTP/`localhost`/Container-Port):

| Quelle (öffentlich)          | Ziel (intern)        | Dienst          |
|------------------------------|----------------------|-----------------|
| `https://zev.example.com`    | `http://localhost:4200` | Frontend      |
| `https://auth.example.com`   | `http://localhost:9000` | Keycloak      |
| `https://api.example.com`    | `http://localhost:8090` | Backend API   |

„HTTP→HTTPS-Weiterleitung" aktivieren; Ports 80/443 dürfen nicht von anderen DSM-Diensten belegt sein (DSM selbst: 5000/5001).

### 3. Custom Header (je Regel)
Reiter „Benutzerdefinierter Kopf" → „Erstellen → WebSocket" (Upgrade/Connection) **und** Proxy-Header ergänzen — essenziell, damit Keycloak die externe HTTPS-URL bildet:
`X-Forwarded-Proto`, `X-Forwarded-For`, `X-Forwarded-Host`, `X-Real-IP`.

### 4. Keycloak-Container-Env (Proxy-Modus)
Im NAS-Compose für den `keycloak`-Service (statt reinem `start-dev`):
```yaml
    command:
      - start                     # Produktionsmodus
      - --import-realm
      - --features=organization
    environment:
      KC_PROXY_HEADERS: xforwarded # ältere KC-Versionen: KC_PROXY=edge
      KC_HOSTNAME: https://auth.example.com
      KC_HTTP_ENABLED: "true"      # TLS endet am Reverse-Proxy
      KC_HEALTH_ENABLED: "true"
```

### 5. Konfigurationswerte (HTTPS-Variante der Referenztabelle)

| Zweck                 | Ort / Env                                              | HTTPS-Wert                              |
|-----------------------|--------------------------------------------------------|-----------------------------------------|
| Frontend → API        | `config.json` `apiBaseUrl` (`API_BASE_URL`)            | `https://api.example.com`               |
| Frontend → Keycloak   | `config.json` `keycloak.url` (`KEYCLOAK_URL`)          | `https://auth.example.com`              |
| Backend Token-Issuer  | `..._JWT_ISSUER_URI`                                   | `https://auth.example.com/realms/zev`   |
| Backend CORS          | `APP_CORS_ALLOWED_ORIGINS`                             | `https://zev.example.com`               |
| Keycloak-Client       | `zev-frontend` `redirectUris`/`webOrigins`             | `https://zev.example.com/*` / `https://zev.example.com` |
| JWK-Set (intern)      | `..._JWK_SET_URI`                                      | `http://keycloak:9000/...` (Container-Netz, bleibt)     |

> Invariante bei HTTPS: Frontend `keycloak.url` == Backend `issuer-uri`-Host == `KC_HOSTNAME` == `https://auth.example.com`.

### 6. Zusätzliche Prüfungen (HTTPS)
- Token-`iss` ist die HTTPS-Domain (nicht `http`/interner Host) → Backend akzeptiert (kein 401).
- Keycloak-Login-Redirect bleibt auf HTTPS (kein Mixed-Content, kein Redirect auf internen Port).
- Nur Frontend/Keycloak (ggf. API) öffentlich; Postgres/Monitoring nicht nach aussen exponiert.

### Alternative
Statt des DSM-Proxys ein eigener Reverse-Proxy-Container (nginx/Traefik/Caddy) mit **Pfad-Routing** (`/`, `/api`, `/auth`) unter **einer** Domain — flexibler, aber mehr Eigenpflege.

## Validierungen

### Vor dem Rollout (Build-/Konfig-Ebene)
- `docker-compose.nas.yml` referenziert ausschliesslich `image:` (kein `build:`).
- Alle Host-URLs stammen aus `.env`/Config, **kein** verbliebenes `localhost` in den effektiv genutzten Werten.
- Fremd-Image-Tags sind gepinnt (kein `:latest` im NAS-Compose).
- Image-Architektur == NAS-Architektur (sonst `exec format error`).

### Nach dem Start (Laufzeit-Smoke-Test)
- Alle Container `running`/`healthy`; Startreihenfolge Postgres → Keycloak → Backend eingehalten.
- `GET http://<nas>:4200` liefert die App; Keycloak-Login-Redirect auf NAS-URL funktioniert.
- Nach Login: Beispiel-API-Call (z.B. `/api/einheit`) **200** (kein 401 Issuer-Mismatch, kein CORS-Fehler im Browser).
- Rollen wirken gemäss `Specs/Berechtigungen.md` (Stichprobe je Rolle).
- Kein „keine Organisation"-403 nach Post-Install-Setup.
- Nach `docker compose restart`/NAS-Reboot: Stack oben, DB-Daten erhalten.

## Offene Punkte / Annahmen

Aus `Specs/NAS-Deployment.md` Abschnitt 8 (dokumentierte Annahmen, bis geklärt):
- **Architektur:** angenommen **x86-64** (`linux/amd64`). Bei ARM-NAS überall `linux/arm64` (Skript-Param).
- **Zugriffsweg:** angenommen **LAN/HTTP** über `<nas>`-Host als Basis; HTTPS über Synology-Reverse-Proxy als empfohlene Option (Phase 7).
- **Monitoring:** angenommen **Slim-Profil** als Default (ohne admin/prometheus/grafana); `full`-Profil optional.
- **`config.json`-Override-Mechanismus:** gewählt **externe Spring-Static-Location + Entrypoint-Generierung aus Env** (Phase 1); Alternative Bind-Mount der Datei wird in der Doku erwähnt. Betrifft **nur das Frontend**.
- **Backend-Konfig-Ansatz:** Spring-Profil **`nas`** (`application-nas.yml`, Env-Platzhalter), aktiviert via `SPRING_PROFILES_ACTIVE=nas`; MQTT optional via `nas,mqtt`. Secrets bleiben in `.env`/Env, nicht im Profil-File.
- **Realm-Parametrierung:** Keycloak-Realm-Import-Env-Substitution vs. Post-Install-Anpassung (Phase 3) – im Zweifel dokumentierte manuelle Anpassung, da robust.
- Keine DB-/Schema-Änderung, keine neuen Entities/Endpunkte, kein neuer App-Code (reines Deployment/Config).
