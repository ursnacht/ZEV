# Berechtigungen (Rollen-Matrix)

Dieses Dokument beschreibt das **permission-basierte Autorisierungsmodell** gemäss [`Specs/Composite-Roles.md`](./Composite-Roles.md): Die Anwendung prüft **Permissions** (z.B. `einstellungen:write`), die Fachrollen bündeln diese über **Keycloak Composite Roles**.

Quelle der Wahrheit:

- **Keycloak:** Permission-Rollen + Composite-Zusammenstellung der Fachrollen (`keycloak/realms/zev-realm.json`)
- **Backend:** `@PreAuthorize("hasAuthority('<permission>')")` auf Controllern/Methoden
- **Frontend:** Route-Guard `AuthGuard` mit `data.permissions` in `frontend-service/src/app/app.routes.ts`

> **Status:** Umgesetzt. Backend prüft `hasAuthority('<permission>')`, Keycloak bündelt die Permissions in den Composite-Rollen (`zev-realm.json`). Dieses Dokument ist die **massgebliche Zuordnung** – Realm, Backend und Frontend folgen ihr.

Stand: 2026-07-09

## Rollenmodell

- Die Anwendung prüft **Permissions**, nicht Fachrollen.
- Die Fachrollen sind **hierarchische Composite Roles** (`zev_user ⊂ org_admin ⊂ zev_admin`); Keycloak liefert die aufgelösten Permissions im Token (`realm_access.roles`).
- Einem User wird nur seine **Fachrolle** zugewiesen – Lesezugriff und Permissions kommen über die Composite-Auflösung (keine separate `zev_user`-Zuweisung nötig).
- Test-User: `user` (`zev_user`), `testuser` (`zev_admin`), `orgadmin` (`org_admin`).

### Fachrolle → Permissions

| Permission              | zev_user | zev_admin | org_admin |
|-------------------------|:---:|:---------:|:---------:|
| `einheit:read`          | ✅  | ✅        | ✅        |
| `messwerte:read`        | ✅  | ✅        | ✅        |
| `statistik:read`        | ✅  | ✅        | ✅        |
| `translations:read`     | ✅  | ✅        | ✅        |
| `featureflags:read`     | ✅  | ✅        | ✅        |
| `lizenzen:read`         | ✅  | ✅        | ✅        |
| `mieter:read`           | ✅  | ✅        | ✅        |
| `einstellungen:write`   | ❌  | ✅        | ✅        |
| `einheit:write`         | ❌  | ✅        | ✅        |
| `messwerte:write`       | ❌  | ✅        | ✅        |
| `tarife:manage`         | ❌  | ✅        | ✅        |
| `mieter:manage`         | ❌  | ✅        | ✅        |
| `rechnungen:manage`     | ✅  | ✅        | ✅        |
| `debitoren:manage`      | ✅  | ✅        | ✅        |
| `translations:manage`   | ❌  | ✅        | ❌        |
| `featureflags:manage`   | ❌  | ✅        | ❌        |
| `datenbank:read`        | ❌  | ✅        | ❌        |

## Legende

| Symbol | Bedeutung          |
|--------|--------------------|
| ✅     | Zugriff / erlaubt  |
| ❌     | kein Zugriff       |

Die Rollen-Spalten sind aus der obigen Fachrolle→Permission-Zuordnung abgeleitet.

## Matrix (Feature × Permission)

| Feature                                              | erforderliche Permission | zev_user | zev_admin | org_admin |
|------------------------------------------------------|--------------------------|:---:|:---------:|:---------:|
| Startseite ⁰                                         | – (authentifiziert)      | ✅  | ✅        | ✅        |
| Einheiten – Liste ansehen                            | `einheit:read`           | ✅  | ✅        | ✅        |
| Einheiten – anlegen / bearbeiten / löschen (KI-Matching) | `einheit:write`      | ❌  | ✅        | ✅        |
| Messwerte-Grafik ansehen                             | `messwerte:read`         | ✅  | ✅        | ✅        |
| Messwerte-Upload (CSV + KI-Matching) ¹               | `messwerte:write`        | ❌  | ✅        | ✅        |
| Solarverteilung berechnen ²                          | `messwerte:write`        | ❌  | ✅        | ✅        |
| Statistik ansehen & PDF-Export                       | `statistik:read`         | ✅  | ✅        | ✅        |
| Rechnungen generieren & herunterladen                | `rechnungen:manage`      | ✅  | ✅        | ✅        |
| Debitorenkontrolle ⁴                                 | `debitoren:manage`       | ✅  | ✅        | ✅        |
| Tarife verwalten                                     | `tarife:manage`          | ❌  | ✅        | ✅        |
| Mieterverwaltung                                     | `mieter:manage`          | ❌  | ✅        | ✅        |
| Übersetzungs-Editor (verwalten)                      | `translations:manage`    | ❌  | ✅        | ❌        |
| **Einstellungen (Rechnungsangaben) bearbeiten**      | `einstellungen:write`    | ❌  | ✅        | ✅        |
| **Feature-Flags verwalten** ³                        | `featureflags:manage`    | ❌  | ✅        | ❌        |
| **Datenbank-Ansicht** (Einstellungen) ⁵              | `datenbank:read`         | ❌  | ✅        | ❌        |
| Lizenzen ansehen                                     | `lizenzen:read`          | ✅  | ✅        | ✅        |
| Design-System-Showcase ⁰                             | – (authentifiziert)      | ✅  | ✅        | ✅        |

## Fussnoten

⁰ Reine Frontend-Seite ohne eigenen geschützten Backend-Endpunkt; für jeden authentifizierten User zugänglich.

¹ Zusätzlich per Feature-Flag `MESSWERTE_UPLOAD` gesteuert (`FeatureFlagGuard`). Ist das Flag inaktiv, ist die Route für alle gesperrt.

² Die Seite `/solar-calculation` ist per Route auch für Leser erreichbar, die eigentliche Berechnung (`POST /api/messwerte/calculate-distribution` und `.../calculation-progress`) erfordert jedoch `messwerte:write`.

³ Das **Lesen** der effektiven Feature-Flags (`GET /api/feature-flags`, Permission `featureflags:read`) ist für alle Fachrollen erlaubt (interne UI-Steuerung). Das **Verwalten** erfordert `featureflags:manage`. In der `EinstellungenComponent` wird die Feature-Flag-Sektion nur bei `featureflags:manage` angezeigt.

⁴ Die Debitorkontrolle lädt für das Anlegen/Bearbeiten (Mieter-Dropdown) zusätzlich die Mieter-Liste (`GET /api/mieter`, Permission `mieter:read`). Daher hat jede Fachrolle mit `debitoren:manage` auch `mieter:read` (die Debitoren-Tabelle selbst nutzt `mieterName`/`einheitName` aus dem JOIN und benötigt sie nicht).

⁵ Generische, **read-only** Tabellenansicht innerhalb der Seite `/einstellungen` (nur `zev_admin`). Die Seite selbst erfordert `einstellungen:write` (auch `org_admin`), der Datenbank-Bereich wird jedoch nur bei `datenbank:read` angezeigt und ist backend-seitig separat geschützt. Mandantenübergreifende Rohansicht (orgFilter nicht angewendet).

## Öffentlich (ohne Authentifizierung)

- `GET /ping` – Health-Check
- `/api/public/**`
- `/actuator/health`, `/actuator/info`, `/actuator/prometheus` (Docker-Healthchecks, Prometheus-Scrape)

Alle übrigen Actuator-Endpoints (`/actuator/env`, `/actuator/loggers`, ...) erfordern **Basic Auth**
(`ACTUATOR_USER`/`ACTUATOR_PASSWORD`, siehe `SecurityConfig.actuatorFilterChain`); der Spring Boot
Admin Server authentifiziert sich mit denselben Credentials (Client-Metadata `user.name`/`user.password`).

Die **Spring-Boot-Admin-UI** (admin-service, `:8081`) ist ebenfalls per Basic Auth geschützt
(`SBA_USER`/`SBA_PASSWORD`, Rolle `SBA_ADMIN`); die Clients registrieren sich mit denselben
Credentials (`spring.boot.admin.client.username/password`) an `POST /instances`.

## Endpunkt-Referenz (Backend `@PreAuthorize`)

Die Spalten `zev_user` / `zev_admin` / `org_admin` sind gleich dargestellt wie in der Matrix (abgeleitet aus der Fachrolle→Permission-Zuordnung). Die Spalte `@PreAuthorize` zeigt die technische Regel im Ziel-Modell.

| Controller               | Endpunkt(e)                                                                 | `@PreAuthorize`                          | zev_user | zev_admin | org_admin |
|--------------------------|-----------------------------------------------------------------------------|------------------------------------------|:---:|:---------:|:---------:|
| EinheitController        | `GET /api/einheit` (Liste)                                                  | `hasAuthority('einheit:read')`           | ✅  | ✅        | ✅        |
| EinheitController        | übrige (`GET /{id}`, `POST`, `PUT`, `DELETE`, `POST /match`)                 | `hasAuthority('einheit:write')`          | ❌  | ✅        | ✅        |
| MesswerteController      | `GET /by-einheit`                                                           | `hasAuthority('messwerte:read')`         | ✅  | ✅        | ✅        |
| MesswerteController      | `POST /upload`, `POST /calculate-distribution`, `GET /calculation-progress` | `hasAuthority('messwerte:write')`        | ❌  | ✅        | ✅        |
| StatistikController      | alle                                                                        | `hasAuthority('statistik:read')`         | ✅  | ✅        | ✅        |
| TranslationController    | `GET /api/translations`                                                     | `hasAuthority('translations:read')`      | ✅  | ✅        | ✅        |
| TranslationController    | `GET /list`, `POST`, `PUT`, `DELETE`                                        | `hasAuthority('translations:manage')`    | ❌  | ✅        | ❌        |
| TarifController          | alle                                                                        | `hasAuthority('tarife:manage')`          | ❌  | ✅        | ✅        |
| MieterController         | `GET /api/mieter` (Liste), `GET /{id}`                                       | `hasAuthority('mieter:read')`            | ✅  | ✅        | ✅        |
| MieterController         | `POST`, `PUT`, `DELETE`                                                      | `hasAuthority('mieter:manage')`          | ❌  | ✅        | ✅        |
| DebitorController        | alle                                                                        | `hasAuthority('debitoren:manage')`       | ✅  | ✅        | ✅        |
| RechnungController       | alle                                                                        | `hasAuthority('rechnungen:manage')`      | ✅  | ✅        | ✅        |
| EinstellungenController  | alle                                                                        | `hasAuthority('einstellungen:write')`    | ❌  | ✅        | ✅        |
| DatenbankController      | `GET /api/datenbank/tabellen`, `POST /api/datenbank/abfrage`                | `hasAuthority('datenbank:read')`         | ❌  | ✅        | ❌        |
| FeatureFlagController    | `GET /api/feature-flags`                                                    | `hasAuthority('featureflags:read')`      | ✅  | ✅        | ✅        |
| FeatureFlagController    | `GET /admin`, `PUT /{key}`, `DELETE /{key}`                                 | `hasAuthority('featureflags:manage')`    | ❌  | ✅        | ❌        |
| LizenzenController       | `GET /api/lizenzen`                                                         | `hasAuthority('lizenzen:read')`          | ✅  | ✅        | ✅        |
| AuthController           | `POST /api/auth/logout`                                                     | `isAuthenticated()`                      | ✅  | ✅        | ✅        |

`PingController` (`GET /ping`), `/api/public/**` sowie `/actuator/health|info|prometheus` sind öffentlich; die übrigen Actuator-Endpoints erfordern Basic Auth (siehe Abschnitt oben).
