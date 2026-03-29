# Umsetzungsplan: Grundgerüst

## Zusammenfassung

Erstellung eines neuen generischen Starter-Projekts unter `/data/git/glue-starter/` auf Basis des ZEV-Projekts. Alle ZEV-spezifischen Features werden entfernt; übernommen werden die generische Infrastruktur (Authentication, i18n, SBOM, Dark Mode, Navigation, Design System) mit angepassten Namen (`ch.glue`, `starter-*`, `app-`-CSS-Präfix, Rollen `user`/`admin`, Realm `glue`, Schema `app`). Das bestehende ZEV-Projekt wird nicht verändert.

---

## Betroffene Komponenten

### Neu erstellt in `/data/git/glue-starter/`

| Typ | Datei |
|-----|-------|
| Root | `pom.xml`, `docker-compose.yml`, `.gitignore`, `.env.example`, `CLAUDE.md` |
| Design System | `design-system/package.json`, `design-system/src/**` (alle CSS: `zev-` → `app-`) |
| Admin Service | `admin-service/pom.xml`, `admin-service/src/main/java/ch/glue/admin/AdminApplication.java`, `admin-service/src/main/resources/application.properties`, `admin-service/Dockerfile` |
| Backend pom | `backend-service/pom.xml` (ch.glue, Java 23 override, cyclonedx-plugin, log4j-fix, ArchUnit 1.4.0) |
| Backend Core | `BackendServiceApplication.java`, `config/SecurityConfig.java`, `config/CacheConfig.java`, `config/WebMvcConfig.java`, `config/OrganizationInterceptor.java` |
| Backend Infrastruktur | `service/OrganizationContextService.java`, `service/HibernateFilterService.java`, `service/OrganisationService.java`, `entity/Organisation.java`, `entity/package-info.java`, `repository/OrganisationRepository.java`, `exception/GlobalExceptionHandler.java`, `exception/NoOrganizationException.java`, `controller/PingController.java` |
| Backend Translation | `entity/Translation.java`, `repository/TranslationRepository.java`, `service/TranslationService.java`, `controller/TranslationController.java` |
| Backend Lizenzen | `dto/LizenzenDTO.java`, `dto/LizenzenHashDTO.java`, `service/LizenzenService.java`, `controller/LizenzenController.java` |
| Backend Resources | `application.properties`, `Dockerfile` |
| DB-Migrationen | `V1__Create_Schemas.sql`, `V2__Create_Translation_Table.sql`, `V3__Create_Organisation_Table.sql`, `V4__Add_Initial_Translations.sql` |
| Frontend Setup | `package.json`, `angular.json`, `tsconfig*.json`, `karma.conf.js`, `playwright.config.ts`, `Dockerfile`, `nginx.conf`, `scripts/generate-licenses.js`, `src/index.html`, `src/styles.css`, `src/main.ts` |
| Frontend Core | `app.config.ts`, `guards/auth.guard.ts`, `interceptors/error.interceptor.ts`, `pipes/translate.pipe.ts`, `pipes/swiss-date.pipe.ts`, `directives/column-resize.directive.ts`, `utils/date-utils.ts`, `services/translation.service.ts`, `services/theme.service.ts`, `services/lizenzen.service.ts`, `models/lizenzen.model.ts` |
| Frontend App | `app.component.ts/.html/.css`, `app.routes.ts` |
| Frontend Komponenten | `components/icon/`, `components/kebab-menu/`, `components/navigation/`, `components/startseite/`, `components/translation-editor/`, `components/lizenzen/`, `components/design-system-showcase/` |
| Frontend Tests | `tests/helpers.ts`, `tests/app.spec.ts`, `tests/lizenzen.spec.ts` |
| Keycloak | `keycloak/realms/glue-realm.json` |
| Specs | `Specs/SPEC.md`, `Specs/generell.md` |
| Skills | `.claude/commands/0_anforderungen.md` bis `.claude/commands/7_akzeptanzkriterien-check.md` |
| MCP Server | `.claude/settings.local.json` (MCP `glue-db`) |

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [ ] | 1. Root-Verzeichnis & Parent-POM | `/data/git/glue-starter/` anlegen; `pom.xml` (ch.glue, starter-parent, Java 25), `.gitignore`, `.env.example` |
| [ ] | 2. Design System | `design-system/` kopieren; `package.json`: `@zev/design-system` → `@glue/design-system`; alle CSS-Klassen `zev-` → `app-`; `npm run build` |
| [ ] | 3. Admin Service | `admin-service/` kopieren; Package `ch.nacht` → `ch.glue`; Parent-Referenz anpassen; `mvn compile -pl admin-service` |
| [ ] | 4. Backend – Projekt-Setup | `backend-service/pom.xml` (ch.glue, starter-backend, Java 25, deps ohne JasperReports/Spring AI/QR-Bill, cyclonedx-plugin + log4j-fix); `BackendServiceApplication.java`; `application.properties`; `Dockerfile` |
| [ ] | 5. Backend – Core-Infrastruktur | `SecurityConfig` (Rollen `user`/`admin`), `WebMvcConfig`, `CacheConfig` (nur `lizenzen`), `OrganizationInterceptor`, `OrganizationContextService`, `HibernateFilterService`, `OrganisationService`, `Organisation`-Entity, `OrganisationRepository`, `GlobalExceptionHandler`, `NoOrganizationException`, `PingController`, `package-info.java` |
| [ ] | 6. Backend – Translation | `Translation`-Entity, `TranslationRepository`, `TranslationService`, `TranslationController` (Rolle `admin`); `mvn compile -pl backend-service` |
| [ ] | 7. Backend – Lizenzen / SBOM | `LizenzenDTO`, `LizenzenHashDTO`, `LizenzenService`, `LizenzenController` (Rolle `user`); `CacheConfig` um `lizenzen` ergänzen; `mvn compile -pl backend-service` |
| [ ] | 8. Backend – DB-Migrationen | `V1__Create_Schemas.sql` (Schema `app` + `keycloak`), `V2__Create_Translation_Table.sql`, `V3__Create_Organisation_Table.sql`, `V4__Add_Initial_Translations.sql` (alle Generic-Translations: Navigation, Dark Mode, Lizenzen, Translation-Editor, Startseite) |
| [ ] | 9. Frontend – Projekt-Setup | `package.json` (`@glue/design-system`, Angular 21.2.6, ohne ZEV-spezifische Deps); `angular.json`; `tsconfig*.json`; `karma.conf.js`; `playwright.config.ts`; `Dockerfile`; `nginx.conf`; `scripts/generate-licenses.js`; `src/index.html` (Dark-Mode-FOUC-Script); `src/styles.css`; `src/main.ts` |
| [ ] | 10. Frontend – Core | `app.config.ts`; `guards/auth.guard.ts`; `interceptors/error.interceptor.ts`; `pipes/translate.pipe.ts`; `pipes/swiss-date.pipe.ts`; `directives/column-resize.directive.ts`; `utils/date-utils.ts`; `services/translation.service.ts`; `services/theme.service.ts`; `services/lizenzen.service.ts`; `models/lizenzen.model.ts` |
| [ ] | 11. Frontend – App & Navigation | `app.component.ts/.html/.css`; `app.routes.ts` (Routen: `startseite`, `translations`, `lizenzen`, `design-system`); `NavigationComponent` (nur generische Menüeinträge: Startseite, Übersetzungen, Lizenzen, Dark-Mode-Toggle, Sprachwahl, Logout) |
| [ ] | 12. Frontend – Seiten-Komponenten | `IconComponent` + `icons.ts`; `KebabMenuComponent`; `StartseiteComponent` (generischer Willkommenstext + Logo-Platzhalter); `TranslationEditorComponent`; `LizenzenComponent`; `DesignSystemShowcaseComponent`; `ng build --configuration=development` |
| [ ] | 13. Frontend – Tests | `tests/helpers.ts` (angepasst auf `user`/`admin`-Rollen und Realm `glue`); `tests/app.spec.ts` (Login, Navigation); `tests/lizenzen.spec.ts` (kopiert aus ZEV) |
| [ ] | 14. Docker Compose & Keycloak Realm | `docker-compose.yml` (ohne Prometheus/Grafana; DB `app`, Realm `glue`, alle Service-Namen ohne `zev`-Präfix); `keycloak/realms/glue-realm.json` (Realm `glue`, Rollen `user`/`admin`, Client `starter-frontend`, Testuser `testuser`/`testpassword` → `admin`, `user`/`password` → `user`) |
| [ ] | 15. CLAUDE.md | Vollständige Adaption: `ch.nacht` → `ch.glue`, `zev` → `starter`, `zev-` → `app-`, Java 21 → 25, Rollen, DB-Schema, keine ZEV-spezifischen Controller/Services, Code-Vorlagen auf generische Klassen anpassen |
| [ ] | 16. Specs & Skills | `Specs/SPEC.md` (Rollen `user`/`admin`, Präfix `app-`); `Specs/generell.md` (Java 25, Angular 21, `app-`, MCP `glue-db`); alle `.claude/commands/*.md` anpassen (groupId, Präfix, Rollen, Pfade) |
| [ ] | 17. MCP Server `glue-db` | `.claude/settings.local.json` mit MCP-Server-Konfiguration für `glue-db` (PostgreSQL, DB `app`) |
| [ ] | 18. Validierung | `mvn compile` (Backend), `ng build` (Frontend), `npm run build` (Design System), `docker-compose up --build` (End-to-End-Smoke-Test) |

---

## Detailbeschreibung ausgewählter Phasen

### Phase 1: Root-Verzeichnis & Parent-POM

**Datei:** `/data/git/glue-starter/pom.xml`

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>4.0.4</version>
</parent>
<groupId>ch.glue</groupId>
<artifactId>starter-parent</artifactId>
<version>1.0-SNAPSHOT</version>
<properties>
  <java.version>25</java.version>
</properties>
<modules>
  <module>admin-service</module>
  <module>backend-service</module>
  <module>design-system</module>
  <module>frontend-service</module>
</modules>
```

`.gitignore`: Aus ZEV kopieren, ergänzt um `frontend-service/src/assets/frontend-licenses.json`.

`.env.example`: Kein `ANTHROPIC_API_KEY` (Spring AI nicht enthalten).

### Phase 2: Design System

- `package.json`: `"name": "@glue/design-system"`
- Alle Dateien in `src/components/**/*.css`: Vollständiger Ersatz `zev-` → `app-` (betrifft alle 18 CSS-Komponenten-Dateien)
- `src/tokens/tokens.css` / `tokens.ts`: Referenzen `zev-` → `app-` (falls vorhanden)
- `DesignSystemShowcaseComponent` im Frontend in Phase 12 anpassen

### Phase 4: Backend – pom.xml Abhängigkeiten

**Behalten:** spring-boot-starter-webmvc, spring-boot-starter-data-jpa, spring-boot-starter-security, spring-boot-starter-oauth2-resource-server, spring-boot-starter-actuator, spring-boot-starter-cache, caffeine, flyway, jackson, micrometer-prometheus

**Entfernen:** jasperreports, spring-ai-anthropic, net.codecrete.qrbill (QR-Bill), alle ZEV-spezifischen Abhängigkeiten

**cyclonedx-plugin + log4j-Fix** (identisch zu ZEV):
```xml
<dependencies>
  <dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-core</artifactId>
    <version>${log4j2.version}</version>
  </dependency>
</dependencies>
```
> **Hinweis:** `jasperreports-maven-plugin` wird **nicht** übernommen (JasperReports ist Out of Scope). Der log4j-Fix in `backend-service/pom.xml` wird trotzdem präventiv hinzugefügt, falls das Plugin später nachgerüstet wird.

**ArchUnit + Java 25 Kompatibilität:**

ArchUnit 1.4.0 bundelt ASM 9.7.1, das nur Klassen-Dateien bis Version 67 (Java 23) unterstützt. Java 25 erzeugt Version 69. Daher muss `backend-service/pom.xml` das Java-Kompilierungsziel auf 23 überschreiben:

```xml
<!-- backend-service/pom.xml -->
<properties>
  <java.version>23</java.version>
</properties>
```

Die JVM läuft weiterhin auf Java 25; nur das `--release`-Target ist 23. ArchUnit wird auf Version 1.4.0 aktualisiert (ZEV verwendet 1.3.0). Sobald eine ArchUnit-Version mit ASM 9.8 verfügbar ist (voraussichtlich 1.5.0), kann diese Property entfernt und `java.version` auf 25 gesetzt werden.

### Phase 5: Backend – SecurityConfig

Rollen anpassen:
```java
// ZEV:       hasRole('zev') / hasRole('zev_admin')
// Starter:   hasRole('user') / hasRole('admin')
```

Alle `@PreAuthorize`-Annotationen und Permit-Regeln entsprechend anpassen.

### Phase 8: Backend – DB-Migrationen

| Migration | Inhalt |
|-----------|--------|
| `V1__Create_Schemas.sql` | `CREATE SCHEMA IF NOT EXISTS app;` + `CREATE SCHEMA IF NOT EXISTS keycloak;` |
| `V2__Create_Translation_Table.sql` | `CREATE TABLE app.translation (key, deutsch, englisch)` |
| `V3__Create_Organisation_Table.sql` | `CREATE SEQUENCE app.organisation_seq` + `CREATE TABLE app.organisation (...)` |
| `V4__Add_Initial_Translations.sql` | Alle Generic-Translations (siehe Tabelle unten) |

**Translations in V4** (destilliert aus ZEV-Migrationen, nur relevante Keys):

| Keys | Herkunft ZEV-Migration |
|------|----------------------|
| `STARTSEITE`, `WILLKOMMEN`, `APP_BESCHREIBUNG` | V43 (angepasst, kein ZEV-spezifischer Text) |
| `DARK_MODE`, `LIGHT_MODE` | V48 |
| `LIZENZEN`, `LIZENZEN_BACKEND`, `LIZENZEN_FRONTEND`, `LIZENZ_NAME`, `LIZENZ_VERSION`, `LIZENZ_LIZENZ`, `LIZENZ_HERSTELLER`, `LIZENZ_HASH`, `LIZENZ_SUCHEN`, `LIZENZ_UNBEKANNT`, `LIZENZ_KEIN_HASH`, `LIZENZEN_LEER`, `LIZENZEN_FEHLER_BACKEND`, `LIZENZEN_FEHLER_FRONTEND` | V53 |
| `TRANSLATION_EDITOR`, `KEY`, `DEUTSCH`, `ENGLISCH`, `SPEICHERN`, `ABBRECHEN` | V10/V11 |
| `LOGOUT` | V43 |
| `KEINE_ERGEBNISSE`, `LADEN`, `FEHLER` | V25/V30 |
| `BEARBEITEN`, `LOESCHEN`, `BESTAETIGEN`, `ABBRECHEN` | V12 |
| `APP_TITLE` | Neu: `('APP_TITLE', 'Starter', 'Starter')` |

### Phase 9: Frontend – package.json

```json
{
  "dependencies": {
    "@glue/design-system": "file:../design-system/dist",
    "@angular/core": "21.2.6"
    // ... alle Angular-Pakete
  }
}
```

`prebuild`-Script und `generate-licenses`-Script identisch zu ZEV übernehmen.

### Phase 11: Frontend – app.routes.ts

```typescript
export const routes: Routes = [
  { path: '', redirectTo: '/startseite', pathMatch: 'full' },
  { path: 'startseite', component: StartseiteComponent, canActivate: [AuthGuard], data: { roles: ['user', 'admin'] } },
  { path: 'translations', component: TranslationEditorComponent, canActivate: [AuthGuard], data: { roles: ['admin'] } },
  { path: 'lizenzen', component: LizenzenComponent, canActivate: [AuthGuard], data: { roles: ['user', 'admin'] } },
  { path: 'design-system', component: DesignSystemShowcaseComponent, canActivate: [AuthGuard], data: { roles: ['user', 'admin'] } },
];
```

### Phase 14: Keycloak Realm – glue-realm.json

Basis: `keycloak/realms/zev-realm.json` aus ZEV kopieren, folgende Werte ersetzen:

| Feld | Vorher (ZEV) | Nachher (Starter) |
|------|-------------|-------------------|
| `realm` | `zev` | `glue` |
| Rolle 1 | `zev` | `user` |
| Rolle 2 | `zev_admin` | `admin` |
| Client-ID | `zev-frontend` | `starter-frontend` |
| Redirect URIs | `http://localhost:4200/*` | `http://localhost:4200/*` (gleich) |
| Testuser 1 | `testuser` / Rolle `zev_admin` | `testuser` / Rolle `admin` |
| Testuser 2 | `user` / Rolle `zev` | `user` / Rolle `user` |

### Phase 17: MCP Server glue-db

`.claude/settings.local.json`:
```json
{
  "mcpServers": {
    "glue-db": {
      "command": "...",
      "args": ["postgresql://postgres:postgres@localhost:5432/app"]
    }
  }
}
```
Gleiche MCP-Server-Implementierung wie `zev-db`, nur mit DB `app` statt `zev`.

---

## Validierungen

### Backend
1. `mvn compile -pl backend-service` nach Phase 5, 6, 7
2. `mvn package -DskipTests -pl backend-service` nach Phase 8 (SBOM-Plugin-Lauf)

### Frontend
1. `npx ng build --configuration=development` nach Phase 12
2. `npm run generate-licenses` muss `src/assets/frontend-licenses.json` erzeugen

### Design System
1. `npm run build` nach Phase 2

### End-to-End
1. `docker-compose up --build` → alle Services starten ohne Fehler
2. Login via Keycloak mit `testuser`/`testpassword` funktioniert
3. Startseite wird nach Login angezeigt

---

## Offene Punkte / Annahmen

1. **`APP_TITLE`-Wert:** `('APP_TITLE', 'Starter', 'Starter')` als Platzhalter – jedes neue Projekt ersetzt diesen Wert.
2. **Startseite-Bild:** Generischer Platzhalter (z.B. Angular-Logo oder einfaches Willkommens-Layout ohne Bild) – kein ZEV-spezifisches Solar-Bild.
3. **`swiss-date.pipe.ts`:** Wird übernommen (allgemein nützlich für CH-Projekte), gehört aber nicht zur Pflicht-Navigation.
4. **`column-resize.directive.ts`:** Wird übernommen (generisch wiederverwendbar).
5. **`KebabMenuComponent`:** Wird übernommen (generisches UI-Pattern).
6. **`ArchitectureTest.java`:** Wird übernommen (adaptiert auf `ch.glue`-Package). ArchUnit 1.4.0 verwenden (1.3.0 unterstützt Java-25-Klassen nicht). Java-Kompilierungs-Target auf 23 setzen via `<java.version>23</java.version>` in `backend-service/pom.xml` (siehe Phase 4).
7. **Ports:** Identisch zu ZEV (4200, 8090, 8081, 9000) – ZEV und Starter können nicht gleichzeitig laufen.
8. **`design-system` Maven-Modul:** Enthält kein Java – der Parent-POM-Eintrag bleibt wie in ZEV (Design System wird via npm gebaut, nicht via Maven).
9. **Spring Boot Admin `4.0.0-M1`:** Wird unverändert übernommen (keine neuere stabile Version verfügbar).
10. **`frontend-service/src/assets/frontend-licenses.json`** wird in `.gitignore` eingetragen (generierte Datei).
