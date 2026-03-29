# Grundgerüst

## 1. Ziel & Kontext

* **Was soll erreicht werden:** Ein generisches Starter-Projekt (`ch.glue / starter-*`) mit dem gleichen Tech-Stack wie das ZEV-Projekt, das als Ausgangspunkt für neue Applikationen dient.
* **Warum machen wir das:** Neue Projekte sollen nicht von Null beginnen – Authentication, i18n, SBOM, Dark Mode, Navigation und Design System stehen sofort fertig zur Verfügung.
* **Aktueller Stand:** Das ZEV-Projekt enthält alle benötigten Bausteine, aber mit projektspezifischen Namen (`zev`, `ch.nacht`, `zev-`-CSS-Präfix). Diese müssen generalisiert werden.

---

## 2. Funktionale Anforderungen (FR)

### FR-1: Projekt-Struktur

Das Grundgerüst besteht aus folgenden Modulen (identisch zu ZEV, aber umbenannt):

| Modul | Beschreibung |
|-------|-------------|
| `backend-service` | Spring Boot 4, Java 25, `ch.glue` groupId |
| `frontend-service` | Angular 21, `app-`-Präfix im Design System |
| `admin-service` | Spring Boot Admin 4 |
| `design-system` | Generisches CSS-Design-System mit `app-`-Präfix |

### FR-2: Übernommene Features (aus ZEV)

Die folgenden Features werden 1:1 übernommen und auf die neuen Namen angepasst:

| Feature | Beschreibung |
|---------|-------------|
| Keycloak-Authentifizierung | OAuth2/JWT, Rollen `user` und `admin` |
| Hamburger-Navigation | Responsive Navigation mit allen Standard-Menüpunkten |
| Startseite | Landingpage nach Login mit geöffneter Navigation |
| Übersetzungen (i18n) | DB-basiert, DE/EN, `TranslationService`, `TranslatePipe`, Editor |
| SBOM / Lizenzen | CycloneDX (Backend) + `license-checker-rseidelsohn` (Frontend), Anzeige auf `/lizenzen` |
| Light / Dark Mode | Toggle in Navbar + Hamburger-Menü, `localStorage`-Persistierung, Systempräferenz |
| Sprachwahl (DE / EN) | Toggle im Hamburger-Menü, `localStorage`-Persistierung |
| Logout | Button im Hamburger-Menü, Keycloak-Logout |
| Design System | 18 Kategorien, `app-`-Präfix statt `zev-` |
| Multi-Tenancy | `org_id` auf Entities, Hibernate-Filter, `OrganizationContextService` |
| Spring Boot Admin | Monitoring-Dashboard, Port 8081 |

### FR-3: Anpassungen gegenüber ZEV

| Was | ZEV | Grundgerüst |
|-----|-----|-------------|
| groupId | `ch.nacht` | `ch.glue` |
| artifactId | `zev-*` | `starter-*` |
| Java-Version | 21 | 25 |
| CSS-Präfix | `zev-` | `app-` |
| npm-Paket Design System | `@zev/design-system` | `@glue/design-system` |
| Keycloak-Rolle Standardbenutzer | `zev` | `user` |
| Keycloak-Rolle Administrator | `zev_admin` | `admin` |
| DB-Schema | `zev` | `app` |
| App-Titel (Navbar) | `ZEV_MANAGEMENT` | `APP_TITLE` |
| Feather-Icon App-Titel | `sun` | konfigurierbar (Platzhalter `layers`) |
| ZEV-spezifische Features | Messwerte, Einheiten, Tarife, Rechnungen, Statistik, Solar-Distribution | **nicht enthalten** |

### FR-4: Navigation im Grundgerüst

Das Hamburger-Menü enthält nur die generischen Einträge:

| Route | Icon | Rolle |
|-------|------|-------|
| `/startseite` | `home` | `user`, `admin` |
| `/translations` | `globe` | `admin` |
| `/lizenzen` | `shield` | `user`, `admin` |
| Dark-Mode-Toggle | `moon` / `sun` | alle |
| Sprachwahl DE/EN | `globe` | alle |
| Logout | `log-out` | alle |

### FR-5: Mitgelieferte Specs und Skills

Das Grundgerüst-Repository enthält analog zu ZEV:

| Datei | Inhalt |
|-------|--------|
| `Specs/SPEC.md` | Template für Feature-Specs (angepasst auf `user`/`admin`-Rollen und `app-`-Präfix) |
| `Specs/generell.md` | Generelle Anforderungen (Spring Boot 4 / Java 25, Angular 21, `app-`-Präfix, `user`/`admin`) |
| `.claude/commands/0_anforderungen.md` | Skill: Anforderungsdokument erstellen |
| `.claude/commands/1_umsetzungsplan.md` | Skill: Umsetzungsplan erstellen |
| `.claude/commands/2_umsetzung.md` | Skill: Phasenweise Umsetzung |
| `.claude/commands/3_backend-tests.md` | Skill: Backend Unit/Integration Tests |
| `.claude/commands/4_frontend-unit-tests.md` | Skill: Angular Unit Tests |
| `.claude/commands/5_e2e-tests.md` | Skill: Playwright E2E Tests |
| `.claude/commands/6_test-gap-analyse.md` | Skill: Test-Gap-Analyse |
| `.claude/commands/7_akzeptanzkriterien-check.md` | Skill: Akzeptanzkriterien prüfen |

Skills werden auf `ch.glue`, `starter-*`, `app-`-Präfix und `user`/`admin`-Rollen angepasst.

### FR-6: CLAUDE.md

`CLAUDE.md` wird übernommen und angepasst:
- `ch.nacht` → `ch.glue`
- `zev` → `starter`
- `zev-` → `app-`
- Java 21 → Java 25
- Rollen `zev`/`zev_admin` → `user`/`admin`
- ZEV-spezifische Controller/Services/Komponenten aus der Referenztabelle entfernt
- Code-Vorlagen aktualisiert (auf generische Beispiel-Entity `Sample` statt `Tarif`)

### FR-7: Docker Compose

`docker-compose.yml` wird übernommen, Services umbenannt:
- DB-Name: `app` (statt `zev`)
- DB-Schema: `app` (statt `zev`)
- Keycloak-Realm: `app` (statt `zev`)
- prometheus und grafana **nicht** übernehmen
- Alle Service-Namen ohne `zev`-Präfix

---

## 3. Akzeptanzkriterien

* [ ] Das Projekt lässt sich mit `docker-compose up --build` starten
* [ ] Login via Keycloak funktioniert mit Testusern (`testuser`/`testpassword` → Rolle `admin`, `user`/`password` → Rolle `user`)
* [ ] Nach Login wird die Startseite angezeigt mit geöffneter Navigation
* [ ] Hamburger-Menü enthält: Startseite, Übersetzungen, Lizenzen, Dark-Mode-Toggle, Sprachwahl, Logout
* [ ] Dark Mode / Light Mode Toggle funktioniert und wird in `localStorage` gespeichert
* [ ] Sprachwahl DE/EN funktioniert und wird in `localStorage` gespeichert
* [ ] Logout beendet die Keycloak-Session
* [ ] `/translations` ist nur für Rolle `admin` zugänglich
* [ ] `/lizenzen` zeigt Backend- und Frontend-Bibliotheken mit Lizenzen und Hashes
* [ ] Design System verwendet durchgehend `app-`-Präfix (kein `zev-` mehr)
* [ ] `mvn compile` im `backend-service` läuft fehlerfrei durch
* [ ] `ng build` im `frontend-service` läuft fehlerfrei durch
* [ ] `cd design-system && npm run build` läuft fehlerfrei durch
* [ ] `CLAUDE.md`, `Specs/generell.md`, `Specs/SPEC.md` und alle Skills sind angepasst und konsistent

---

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Keine Änderung gegenüber ZEV – gleiche Caching-Strategie (Caffeine), gleiche SBOM-Generierung.

### NFR-2: Sicherheit
* Alle Routen ausser `/ping` (Backend) erfordern Authentifizierung via Keycloak.
* Rollen: `user` für Lesezugriff, `admin` für Schreibzugriff und administrative Seiten.
* `@PreAuthorize`-Annotationen im Backend, `AuthGuard` im Frontend.

### NFR-3: Kompatibilität
* Java 25 (Virtual Threads, ohne Preview-Features).
* Angular 21.2.x, TypeScript 5.9.x.
* PostgreSQL 16 (Alpine).
* Spring Boot 4.0.x.

### NFR-4: Tests
* ArchUnit-Tests übernehmen
* Testtools gemäss Skills übernehmen 

---

## 5. Edge Cases & Fehlerbehandlung

* **SBOM nicht verfügbar:** Backend gibt HTTP 503 zurück; Frontend zeigt Fehlermeldung.
* **Keycloak nicht erreichbar:** Frontend zeigt Login-Fehler; Backend gibt 401/403 zurück.
* **Übersetzungs-Key fehlt:** `TranslatePipe` gibt den Key als Fallback zurück.
* **localStorage nicht verfügbar** (Private Browsing): Dark Mode und Sprachwahl fallen auf Systemstandard zurück.
* **DB-Migration schlägt fehl:** Flyway bricht den Start ab; Fehler im Log sichtbar.

---

## 6. Abhängigkeiten & betroffene Funktionalität

* **Voraussetzungen:** Keines – das Grundgerüst ist ein eigenständiges Projekt.
* **Betroffener Code:** Gesamter Code aus ZEV, der übernommen wird, muss auf neue Namen angepasst werden (Suchen & Ersetzen + gezielte Anpassungen).
* **Datenmigration:** Keine (neues Projekt, leere Datenbank).
* **Neue Flyway-Basisversion:** Startet bei `V1__` neu.

---

## 7. Abgrenzung / Out of Scope

* ZEV-spezifische Features (Messwerte, Einheiten, Tarife, Rechnungen, Statistik, Solar-Distribution, QR-Bill, KI-Matching, MQTT) werden **nicht** übernommen.
* Keine Spring AI / Anthropic-Integration im Grundgerüst (kann separat hinzugefügt werden).
* Keine JasperReports-Integration (kann separat hinzugefügt werden).
* Keine automatische Projektgenerierung (CLI-Tool / Maven Archetype) – Grundgerüst ist ein manuell zu klonendes Startprojekt.
* Kein Prometheus / Grafana im Grundgerüst (Infra-optionale Ergänzung).

---

## 8. Offene Fragen

* **Wo lebt das Grundgerüst?** Eigenes Git-Repository (empfohlen: `https://github.com/ursnacht/glue-starter` o.ä.) oder als Branch/Subfolder? --> Empfehlung passt. Erstelle das neue Projekt im lokalen Verzeichnis /data/git/glue-starter
* **Beispiel-Entity:** Soll eine generische Demo-Entity (`Sample` o.ä.) mit CRUD als Vorlage enthalten sein, damit die Code-Vorlagen in `CLAUDE.md` sofort referenzierbar sind? --> keine Demo-Entity, aber Code-Vorlagen in den Skills und in `CLAUDE.md` übernehmen und anpassen
* **Keycloak-Realm-Name:** `app` als Default, oder soll der Realm-Name parametrisierbar sein (z.B. über `.env`)? --> Erstelle Realm 'glue'
* **MCP-Server:** Der ZEV-MCP-Server `zev-db` referenziert die ZEV-Datenbank. Für das Grundgerüst muss ein eigener MCP-Server konfiguriert werden – Anleitung in `CLAUDE.md` ergänzen. --> erstelle MCP-Server `glue-db`
