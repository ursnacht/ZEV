# NAS-Deployment (Synology)

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Die ZEV-Anwendung soll auf einer **Synology NAS** (Container Manager / Docker) installiert und produktiv genutzt werden können – erreichbar für Nutzer im LAN (bzw. optional per HTTPS-Domain).
* **Warum machen wir das:** Die NAS ist eine kostengünstige, dauerhaft laufende Plattform beim Betreiber. Aktuell ist der Stack zwar containerisiert, aber auf die **lokale Entwicklungsumgebung** zugeschnitten.
* **Aktueller Stand:**
  * `docker-compose.yml` mit `.env`, benannten Volumes, Healthchecks, `restart: unless-stopped`.
  * `docs/images.md` + `scripts/build-pi-images.ps1`: Cross-Arch-Build (Buildx), Übertragung per tar.gz **oder** private `registry:2`-Registry auf der NAS. Der Image-Transfer ist damit bereits gelöst.
  * **Hindernis:** Die drei App-Images werden per `build:` erzeugt (die Dockerfiles kopieren `target/*.jar`, Build erfolgt ausserhalb) – auf der NAS kann/soll nicht gebaut werden.
  * **Hindernis:** Host-URLs sind an mehreren Stellen fest auf `localhost` verdrahtet (siehe FR-2).
  * **Hindernis:** Keycloak läuft im `start-dev`-Modus; der Realm-Import enthält **keine** Organization, der `OrganizationInterceptor` verlangt aber einen `organization`-Claim.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: NAS-taugliche Images & Bereitstellung
1. Die drei App-Images (`backend-service`, `admin-service`, `frontend-service`) werden für die **Ziel-Architektur der NAS** gebaut (x86-64 → `linux/amd64`, ARM → `linux/arm64`).
2. Bereitstellung auf der NAS wahlweise per tar.gz-Transfer **oder** private Registry (bestehende `docs/images.md`).
3. Eine **NAS-spezifische Compose-Datei** (`docker-compose.nas.yml`) verwendet `image:` statt `build:` und referenziert die bereitgestellten Images.

### FR-2: Konfigurierbare Host-URLs (Entkopplung von `localhost`)
Alle vom Browser bzw. von der Token-Validierung genutzten URLs müssen ohne Quellcode-Änderung auf die NAS-Adresse gesetzt werden können:
1. **Frontend-Laufzeit-Config** (`assets/config.json`: `apiBaseUrl`, `keycloak.url`): extern überschreibbar machen, **ohne** das Frontend-JAR neu zu bauen (aktuell liegt die Datei als statische Ressource im JAR → kein Override möglich). Lösung z.B. externe Static-Location / Bind-Mount / Ersetzen beim Container-Start.
2. **Backend** `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` muss der extern erreichbaren Keycloak-URL entsprechen (Token-`iss`), per Env setzbar (ist es bereits).
3. **Backend** CORS `app.cors.allowed-origins` (`application.yml`) muss die NAS-Frontend-Origin zulassen, per Env `APP_CORS_ALLOWED_ORIGINS`.
4. **Keycloak-Client** `zev-frontend`: `redirectUris`/`webOrigins` dürfen nicht auf `http://localhost:4200` festgenagelt sein, sondern müssen die NAS-URL erlauben (Realm-Konfiguration bzw. nachträglich anpassbar).
5. **Spring-Profil `nas`** (`application-nas.yml`) bündelt die NAS-spezifische Backend-Konfiguration strukturiert; aktiviert via `SPRING_PROFILES_ACTIVE=nas`. Host-/Secret-Werte bleiben **Env-Platzhalter** (`${VAR:default}`) → ohne JAR-Rebuild setzbar. Gerüst:

```yaml
# backend-service/src/main/resources/application-nas.yml
# Aktivierung: SPRING_PROFILES_ACTIVE=nas (im docker-compose.nas.yml gesetzt)
app:
  cors:
    # NAS-Frontend-Origin (LAN) bzw. HTTPS-Domain
    allowed-origins: ${APP_CORS_ALLOWED_ORIGINS:http://nas.local:4200}

spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://postgres:5432/zev}
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
  security:
    oauth2:
      resourceserver:
        jwt:
          # MUSS dem Token-`iss` = extern erreichbare Keycloak-URL entsprechen (sonst 401)
          issuer-uri: ${JWT_ISSUER_URI:http://nas.local:9000/realms/zev}
          # Zertifikatsabruf im Container-Netz bleibt intern
          jwk-set-uri: ${JWT_JWK_SET_URI:http://keycloak:9000/realms/zev/protocol/openid-connect/certs}
# Optional: MQTT nur bei Bedarf zuschalten -> SPRING_PROFILES_ACTIVE=nas,mqtt
```

> **Hinweise:** Secrets (DB-/Keycloak-Passwörter) bleiben in `.env`/Env, **nicht** im Profil-File. Alternativ/zusätzlich überschreiben die direkten Relaxed-Binding-Env-Vars (`APP_CORS_ALLOWED_ORIGINS`, `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI`, …) jeden Profilwert — höchste Priorität. Für die **HTTPS-Variante** die `nas.local`-Defaults durch die Domain ersetzen (`https://auth.example.com/realms/zev`, `https://zev.example.com`; siehe Umsetzungsplan-Abschnitt „HTTPS / Reverse-Proxy").

### FR-3: `.env`-Vorlage & Secrets
1. Eine `.env`-Vorlage für die NAS mit allen nötigen Werten inkl. Host-URLs und **geänderten** Standard-Passwörtern.
2. Optional: `ANTHROPIC_API_KEY` (nur für die KI-CSV-Zuordnung; App läuft ohne, nur dieses Feature entfällt).

### FR-4: Keycloak-Betrieb auf der NAS
1. Konsistente **Hostname-Konfiguration** (Frontend `keycloak.url` = Backend `issuer-uri` = tatsächlich vom Browser genutzte URL).
2. Betriebsmodus dokumentiert: `start-dev` (LAN/HTTP) vs. `start` mit `KC_HOSTNAME`/`KC_PROXY` (HTTPS/Reverse-Proxy).
3. **Einmaliges Post-Install-Setup** dokumentiert (Anleitung): Organization anlegen, Client-Scope „organization" + Mapper, Nutzer zuordnen, Fachrollen/Composites prüfen (siehe `docs/Anleitung-keycloak.md`).

### FR-5: Persistenz & Betrieb
1. Persistente Daten (`postgres-data`, `grafana-data`) auf NAS-Volumes; Backup-Hinweis (`pg_dump`).
2. `restart: unless-stopped` (vorhanden) für automatischen Start nach NAS-Reboot.
3. Optionales **Slim-Profil**: `admin-service`, `prometheus`, `grafana` für den reinen App-Betrieb weglassen (RAM sparen).

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

* [ ] Auf einer Synology (DSM 7.2+, Container Manager) lässt sich der Stack aus **bereitgestellten Images** (ohne Build) via `docker-compose.nas.yml` starten.
* [ ] Alle Container erreichen `healthy`/`running`; Reihenfolge (Postgres → Keycloak → Backend) wird eingehalten.
* [ ] Ein Nutzer im LAN öffnet `http://<nas>:<port>`, wird zu Keycloak (NAS-URL) geleitet, meldet sich an und landet in der App (Navbar sichtbar).
* [ ] Nach dem Login funktionieren API-Calls (kein 401 wegen Issuer-Mismatch, kein CORS-Fehler).
* [ ] Die Host-URLs (`config.json`, Issuer, CORS, Keycloak-Redirect/Web-Origins) sind **ohne Quellcode-/JAR-Rebuild** konfigurierbar (nur `.env`/Config-Dateien/Realm).
* [ ] Nach dem Post-Install-Setup (Organization + Nutzer) erscheint **kein** „keine Organisation"-Fehler (403).
* [ ] Rollen wirken wie dokumentiert (`Specs/Berechtigungen.md`): `zev_user`/`org_admin`/`zev_admin`.
* [ ] Nach NAS-Reboot startet der Stack automatisch; Daten (DB) bleiben erhalten.
* [ ] Standard-Passwörter (`postgres`, Keycloak-Admin, Grafana) sind gegenüber der Dev-Vorlage geändert.
* [ ] Falsche Image-Architektur äussert sich klar (`exec format error`) und ist in der Doku adressiert.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance / Ressourcen
* Der Stack läuft auf einer NAS mit begrenztem RAM. Für kleine Geräte **Slim-Profil** (nur Postgres, Keycloak, Backend, Frontend) und **JVM-Heap-Limits** (`-Xmx`) bzw. Container-Memory-Limits vorsehen. Richtwert Voll-Stack: mind. ~4 GB RAM frei.

### NFR-2: Sicherheit
* Autorisierung/Rollen unverändert (permission-basiert, `zev_user`/`org_admin`/`zev_admin`) – siehe `Specs/Composite-Roles.md`.
* **Secrets** nicht ins Image backen; `.env` separat auf der NAS, Standard-Passwörter ändern.
* **HTTPS** für Zugriff ausserhalb des vertrauenswürdigen LAN bzw. für sichere Cookies/Token: über den Synology-Reverse-Proxy (Let's Encrypt); Keycloak dann `KC_HOSTNAME`/`KC_PROXY`.
* Exponierte Ports minimieren; idealerweise nur das Frontend (und Keycloak) nach aussen, DB/Monitoring intern.
* Registry (falls genutzt) nicht offen betreiben (Auth/Reverse-Proxy) – vgl. `docs/images.md`.

### NFR-3: Kompatibilität
* Ziel: **Synology DSM 7.2+** mit Container Manager (Compose v2). Ältere DSM „Docker"-Pakete nicht unterstützt.
* Image-Tags der Fremd-Images (`keycloak`, `postgres`, `prometheus`, `grafana`) für reproduzierbare NAS-Installation **pinnen** (aktuell teils `:latest`).
* Keycloak-Env `KEYCLOAK_ADMIN*` ist in KC 26+ zugunsten `KC_BOOTSTRAP_ADMIN_*` veraltet → prüfen/aktualisieren.
* Keine Datenbank-/Schema-Änderung nötig; App-Code (Business-Logik) unverändert.

## 5. Edge Cases & Fehlerbehandlung
* **Issuer-Mismatch:** Browser holt Token von NAS-URL, Backend erwartet `localhost` → 401 auf allen API-Calls. (Kernfehlerquelle, durch FR-2.2 adressiert.)
* **CORS:** Frontend-Origin nicht erlaubt → Requests im Browser blockiert. (FR-2.3)
* **Keycloak-Redirect:** `redirectUris` passt nicht zur NAS-URL → Login-Redirect scheitert. (FR-2.4)
* **Keine Organization:** Frischer Realm-Import ohne Organization → `NoOrganizationException` (403) bei jedem Zugriff. (FR-4.3)
* **Falsche Architektur:** `exec format error` beim Containerstart → Images für falsche Plattform gebaut.
* **Port-Konflikt mit DSM:** DSM belegt 5000/5001; 9000/3000/8080 kollidieren häufig mit anderen Paketen → Host-Ports umlegen.
* **`config.json` nicht ladbar:** Frontend fällt auf `localhost`-Defaults zurück → Symptom wie Issuer-/CORS-Fehler; Override-Mechanismus muss zuverlässig greifen.
* **RAM-Knappheit:** OOM-Kills einzelner Container → Slim-Profil / Limits.
* **NAS-Reboot:** Stack muss automatisch und in korrekter Reihenfolge hochkommen.

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** Synology mit Container Manager (DSM 7.2+); Bau-Rechner mit Docker Buildx; Kenntnis der NAS-Architektur und der geplanten Zugriffs-URL.
* **Betroffener Code / betroffene Dateien:**
  * `frontend-service`: Externalisierung von `assets/config.json` (Dockerfile/Startup bzw. Spring-Static-Location); `runtime-config.ts` (Defaults).
  * `backend-service/src/main/resources/application.yml`: `app.cors.allowed-origins`, `issuer-uri` (per Env überschreibbar halten).
  * `keycloak/realms/zev-realm.json`: `redirectUris`/`webOrigins` konfigurierbar (bzw. Post-Install-Anpassung).
  * Neu: `docker-compose.nas.yml`, `.env`-Vorlage (NAS), ggf. Slim-Profil (Compose-Profiles).
  * Neu/erweitert: `docs/` NAS-Installationsanleitung (baut auf `docs/images.md` + `docs/Anleitung-keycloak.md` auf).
* **Datenmigration:** Keine (Neuinstallation). Bei Übernahme bestehender Daten: `pg_dump`/Volume-Backup.

## 7. Abgrenzung / Out of Scope
* **Keine** Änderung der Anwendungs-/Business-Logik oder des Rollenmodells.
* **Kein** Aufbau der privaten Registry selbst (in `docs/images.md` beschrieben) – hier nur referenziert.
* **Keine** automatische Keycloak-Organization-/Nutzer-Provisionierung (bleibt manueller, dokumentierter Schritt).
* **Kein** Kubernetes/Swarm; Ziel ist Docker Compose auf der NAS.
* **Keine** produktive Härtung über das Genannte hinaus (kein IdP-Federation, kein HA/Cluster).

## 8. Offene Fragen
* **NAS-Architektur:** x86-64 (Intel/AMD) oder ARM (aarch64)? Bestimmt die Build-Plattform (`linux/amd64` vs `linux/arm64`). *Annahme bis zur Klärung: x86-64.*
* **Zugriffsweg:** nur LAN per IP/Hostname über HTTP, oder HTTPS über eine Domain hinter dem Synology-Reverse-Proxy? Bestimmt Keycloak-Modus (`start-dev` vs `start` + `KC_HOSTNAME`/`KC_PROXY`) und ob TLS nötig ist. *Annahme: LAN/HTTP als Basis, HTTPS als empfohlene Option.*
* **Monitoring:** `admin-service`, `prometheus`, `grafana` auf der NAS mitbetreiben oder Slim-Profil? *Annahme: Slim-Profil als Default, Monitoring optional.*
* **Erreichbarkeits-Topologie:** Ein einzelner Reverse-Proxy-Einstieg (nur Frontend/Keycloak öffentlich) oder mehrere Ports direkt exponiert? *Annahme: Reverse-Proxy bevorzugt.*
* **`config.json`-Override-Mechanismus:** externe Spring-Static-Location vs. Bind-Mount vs. envsubst beim Containerstart – im Umsetzungsplan zu entscheiden.
