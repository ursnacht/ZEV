# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Leitplanken für alle Agenten (inkl. Sub-Agenten)

Diese Regeln gelten für **jeden** Agenten in diesem Repository — auch für Sub-Agenten, die über die
Commands in `.claude/commands/` gestartet werden. Sie sehen die Freigaben des Users nicht und dürfen
deshalb nicht aus dem Kontext schliessen, dass etwas erlaubt ist.

- **Kein Commit, kein Push, kein Stagen ohne explizite Anweisung des Users.** Sub-Agenten committen,
  stagen und pushen **grundsätzlich nie** — sie berichten ihr Ergebnis, der Haupt-Agent holt die
  Freigabe ein. (Ein Sub-Agent hat schon einmal ungefragt auf `main` gepusht.)
- **Die Umgebung gehört dem User.** Keine Docker-Container bauen, starten oder stoppen
  (`docker compose up/down/build`), keine Keycloak-Änderungen, keine Realm-Reimporte. Wird ein
  Neustart oder Rebuild gebraucht: **Befehl nennen, der User führt ihn aus.**
- **Bereits ausgeführte Flyway-Migrationen nie ändern** — die Checksum-Prüfung bricht sonst beim
  nächsten Start. Status über den `zev-db`-MCP-Server prüfen; im Zweifel eine neue Migration anlegen.
- **Secrets bleiben lokal:** `.env` und `.env.*` (z.B. `.env.mqtt.hene`) gehören nicht ins Repository.

## Project Overview

ZEV (Zusammenschluss zum Eigenverbrauch) is a solar power distribution application for managing fair allocation of solar energy among consumers in a self-consumption community. Multi-module Maven project with Spring Boot backend, Angular frontend, and Keycloak authentication.

## Architecture

**Backend Layers:** Controller → Service → Repository → Entity

### REST API Endpoints

Autorisierung permission-basiert via `@PreAuthorize("hasAuthority('<permission>')")` (siehe `Specs/Berechtigungen.md`). Permissions werden über Keycloak Composite Roles gebündelt (`zev_user ⊂ org_admin ⊂ zev_admin`).

**Key Backend Components:**
- `SolarDistribution.java` - Core fair distribution algorithm (equal shares)
- `ProportionalConsumptionDistribution.java` - Alternative proportional distribution algorithm (higher consumers get proportionally more)
- Multi-tenant support via Keycloak organization claim (`OrganizationContextService`, `HibernateFilterService`)

**Shared Frontend Utilities:**
- `ErrorInterceptor` - Global HTTP error handling (logs out on NO_ORGANIZATION 403)

### Frontend Routes & Roles

Routen sind über `data.permissions` (im `AuthGuard`) geschützt; Zugriff bei **einer** passenden Permission.

## Key Conventions

### Testing Strategy
- Unit tests: `*Test.java` (backend), `*.spec.ts` (frontend)
- Integration tests: `*IT.java` with TestContainers
- E2E tests: Playwright in `frontend-service/tests/`
- Architecture tests: ArchUnit in `ArchitectureTest.java`
- Follow test pyramid: 70-80% unit, 15-20% integration, 5-10% E2E
- **Nach jeder Änderung an einem JasperReports-Template (`.jrxml`)** prüfen, ob es noch kompiliert:
  `mvn test -Dtest=JasperTemplateCompileTest` (fängt XML-/Ausdrucksfehler, die sonst erst zur Laufzeit beim PDF-Export auffallen)

### Database
- Flyway migrations in `backend-service/src/main/resources/db/migration/`
- Migration naming: `V[number]__[description].sql`
- Schema: `zev` (application), `keycloak` (identity)

#### Lokale flyway Befehle
| Befehl                                  | Zweck                                                                   |
|-----------------------------------------|-------------------------------------------------------------------------|
| mvn -pl backend-service flyway:info     | Status aller Migrationen anzeigen (welche angewendet, welche pending)   |
| mvn -pl backend-service flyway:migrate  | Ausstehende Migrationen auf die lokale DB anwenden                      |
| mvn -pl backend-service flyway:validate | Geprüfte Migrationen gegen Checksums validieren                         |
| mvn -pl backend-service flyway:clean    | Alle Objekte im Schema löschen (nur Dev!)                               |
| mvn -pl backend-service flyway:repair   | flyway_schema_history reparieren (z.B. nach fehlgeschlagener Migration) |

### Internationalization
- All UI text via `TranslationService` (not hardcoded)
- Translations stored in database
- Use `TranslatePipe` in Angular templates

### Authentication & Authorization
- **Permission-basiert:** Die Anwendung prüft feingranulare Permissions (z.B. `einstellungen:write`), nicht Fachrollen. Vollständige Matrix + Fachrolle→Permission-Zuordnung: `Specs/Berechtigungen.md`.
- **Fachrollen** (Keycloak Composite Roles, hierarchisch `zev_user ⊂ org_admin ⊂ zev_admin`):
  - `zev_user` (member) – alle `*:read`-Permissions (inkl. `mieter:read`) + `rechnungen:manage`, `debitoren:manage`
  - `org_admin` – `zev_user` + `einstellungen:write`, `einheit:write`, `messwerte:write`, `tarife:manage`, `mieter:manage` (kein `translations:manage`/`featureflags:manage`)
  - `zev_admin` (admin) – alle Permissions
- Backend: `@PreAuthorize("hasAuthority('<permission>')")`; `JwtAuthenticationConverter` mappt `realm_access.roles` 1:1 auf Authorities (ohne `ROLE_`-Präfix)
- Frontend: `AuthGuard` prüft `data.permissions` je Route

### Design System
- Use `@zev/design-system` for UI components (local workspace dependency)
- Design tokens in `design-system/src/tokens/` (colors, spacing, typography, shadows, transitions)

## Code-Vorlagen für deterministische Generierung

Bei der Code-Generierung diese Dateien als Vorlage verwenden und deren Struktur exakt übernehmen:

### Backend

| Neuer Code      | Vorlage                                                                      |
|-----------------|------------------------------------------------------------------------------|
| Entity          | `backend-service/src/main/java/ch/nacht/entity/Tarif.java`                   |
| Repository      | `backend-service/src/main/java/ch/nacht/repository/TarifRepository.java`     |
| Service         | `backend-service/src/main/java/ch/nacht/service/TarifService.java`           |
| Controller      | `backend-service/src/main/java/ch/nacht/controller/TarifController.java`     |
| Service Test    | `backend-service/src/test/java/ch/nacht/service/TarifServiceTest.java`       |
| Controller Test | `backend-service/src/test/java/ch/nacht/controller/TarifControllerTest.java` |

### Frontend

| Neuer Code          | Vorlage                                                                       |
|---------------------|-------------------------------------------------------------------------------|
| Model               | `frontend-service/src/app/models/tarif.model.ts`                              |
| Service             | `frontend-service/src/app/services/tarif.service.ts`                          |
| List-Component      | `frontend-service/src/app/components/tarif-list/`                             |
| Form-Component      | `frontend-service/src/app/components/tarif-form/`                             |
| Directive           | `frontend-service/src/app/directives/column-resize.directive.ts`              |
| Pipe                | `frontend-service/src/app/pipes/swiss-date.pipe.ts`                           |
| Utility             | `frontend-service/src/app/utils/date-utils.ts`                                |
| Service Unit Test   | `frontend-service/src/app/services/tarif.service.spec.ts`                     |
| Component Unit Test | `frontend-service/src/app/components/tarif-form/tarif-form.component.spec.ts` |
| Directive Unit Test | `frontend-service/src/app/directives/column-resize.directive.spec.ts`         |
| Pipe Unit Test      | `frontend-service/src/app/pipes/swiss-date.pipe.spec.ts`                      |
| E2E Test            | `frontend-service/tests/tarif-verwaltung.spec.ts`                             |

### Umsetzungsplan

| Neuer Plan             | Vorlage                                   |
|------------------------|-------------------------------------------|
| Feature-Umsetzungsplan | `Specs/Tarifverwaltung_Umsetzungsplan.md` |

## Specifications

Feature specs live in `/Specs/` (aktuelle Liste: `ls Specs/`). Zwei Dateien sind für jede Umsetzung maßgeblich:
- `SPEC.md` - Template for new feature specifications (8 sections: Ziel & Kontext, Funktionale Anforderungen, Akzeptanzkriterien, NFR, Edge Cases, Abhängigkeiten & betroffene Funktionalität, Abgrenzung, Offene Fragen)
- `generell.md` - General requirements (i18n, design system, multi-tenancy, error handling, code templates, testing, Zahlenformatierung)

Specs mit `_Umsetzungsplan`-Suffix enthalten Umsetzungspläne (teils ohne separate Spec).

## Documentation

Additional documentation in `/docs/` (aktuelle Liste: `ls docs/`) - u.a. Keycloak-Anleitung, ArchUnit-Tests, Betriebsanleitungen.

## Docker Compose

**Environment:** Copy `.env.example` to `.env` and set `ANTHROPIC_API_KEY` for AI features.

## Test Users (Keycloak)

- `testuser` / `testpassword` (`zev_admin` – alle Permissions)
- `user` / `password` (`zev_user` – Lese-Permissions + Rechnungen/Debitoren verwalten)
- `orgadmin` / `orgadminpassword` (`org_admin` – Einstellungen bearbeiten, keine Feature-Flags)

## Database Access

Direct database access options:
- **MCP Server**: Use the `zev-db` MCP server for SQL queries
- **Docker**: `docker exec postgres psql -U postgres -d zev -c "SELECT ..."`
