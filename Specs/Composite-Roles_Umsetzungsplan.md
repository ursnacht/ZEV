# Composite-Roles – Umsetzungsplan

## Zusammenfassung

Die Autorisierung wird von fixen Fachrollen auf eine **Permission-Ebene** umgestellt: Die Anwendung prüft künftig Permissions (z.B. `einstellungen:write`) via `hasAuthority(...)`, während die Fachrollen `zev_user`/`org_admin`/`zev_admin` diese Permissions über **Keycloak Composite Roles** bündeln. Ziel ist die Entkopplung der Autorisierungslogik von der Rollenstruktur – neue Rollen oder geänderte Berechtigungen erfordern dann nur noch eine Keycloak-Konfiguration statt Code-Änderungen. Das **effektive Zugriffsverhalten bleibt identisch** zur heutigen Matrix (Regressionsfreiheit).

Grundlage: [`Specs/Composite-Roles.md`](./Composite-Roles.md), Ziel-Matrix: [`Specs/Berechtigungen.md`](./Berechtigungen.md).

## Betroffene Komponenten

### Keycloak
- `keycloak/realms/zev-realm.json` – 15 Permission-Rollen + Composite-Zusammenstellung der Fachrollen; Test-User auf reine Fachrollen-Zuweisung vereinfachen
- `docs/realm-export.json` – Doku-Kopie nachziehen (nicht operativ)

### Backend (`backend-service/src/main/java/ch/nacht/`)
- `config/SecurityConfig.java` – `JwtAuthenticationConverter`: Authorities 1:1 aus `realm_access.roles` (ohne `ROLE_`-Präfix)
- `controller/EinheitController.java` – `einheit:read` (Liste) / `einheit:write` (übrige)
- `controller/MesswerteController.java` – `messwerte:read` / `messwerte:write`
- `controller/StatistikController.java` – `statistik:read`
- `controller/TranslationController.java` – `translations:read` / `translations:manage`
- `controller/TarifController.java` – `tarife:manage`
- `controller/MieterController.java` – `mieter:manage`
- `controller/DebitorController.java` – `debitoren:manage`
- `controller/RechnungController.java` – `rechnungen:manage`
- `controller/EinstellungenController.java` – `einstellungen:write`
- `controller/FeatureFlagController.java` – `featureflags:read` / `featureflags:manage`
- `controller/LizenzenController.java` – `lizenzen:read`
- `controller/AuthController.java` – `isAuthenticated()` bleibt unverändert
- `controller/PingController.java` – öffentlich, unverändert

### Frontend (`frontend-service/src/app/`)
- `guards/auth.guard.ts` – liest `data.permissions`, prüft gegen `grantedRoles.realmRoles`
- `app.routes.ts` – `data.roles` → `data.permissions` je Route
- `components/einstellungen/einstellungen.component.ts` – `hasRealmRole('featureflags:manage')`

### Tests
- `backend-service/src/test/java/ch/nacht/architecture/ArchitectureTest.java` – optionale Regel „Controller nutzen nur `hasAuthority`"
- Neue Autorisierungs-Integrationstests (Permission-Enforcement je Endpunkt)
- `frontend-service/src/app/guards/auth.guard.spec.ts` – neu (Permission-Prüfung)
- `frontend-service/src/app/components/einstellungen/einstellungen.component.spec.ts` – Permission-Mock anpassen
- E2E: `tests/einstellungen.spec.ts` / `feature-flag-upload.spec.ts` unverändert lauffähig prüfen

### Dokumentation
- `Specs/Berechtigungen.md` – bereits an Ziel-Modell angepasst (verifizieren)
- `docs/Anleitung-keycloak.md` – Anlage Permission-Rollen + Composites
- `CLAUDE.md` – API-/Routen-Tabellen, Rollenbeschreibung (Permission-Hinweis)

## Umsetzungsreihenfolge (Phasen)

| Status | Phase                          | Beschreibung                                                                                                                                              |
|--------|--------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
|  [x]   | 1. Keycloak Permission-Rollen  | 15 Permission-Realm-Rollen in `zev-realm.json` anlegen (FR-1)                                                                                             |
|  [x]   | 2. Keycloak Composites         | `zev_user`/`org_admin`/`zev_admin` als verschachtelte Composites zusammenstellen (FR-2); Test-User auf reine Fachrolle vereinfacht. `docs/realm-export.json`: **nicht** hand-editiert (Nativ-Export mit IDs/Hashes) – aus laufendem Keycloak neu exportieren |
|  [x]   | 3. Backend SecurityConfig      | `JwtAuthenticationConverter`: Authorities 1:1 aus `realm_access.roles` ohne `ROLE_`-Präfix                                                                |
|  [x]   | 4. Backend @PreAuthorize       | Alle Controller/Methoden von `hasRole`/`hasAnyRole` auf `hasAuthority('<permission>')` umgestellt (Mapping gemäss Berechtigungen.md)                      |
|  [x]   | 5. Frontend AuthGuard          | `auth.guard.ts`: `data.permissions` lesen, `.some()`-Prüfung gegen `grantedRoles.realmRoles`                                                              |
|  [x]   | 6. Frontend Routing            | `app.routes.ts`: `data.roles` → `data.permissions` je Route gesetzt                                                                                       |
|  [x]   | 7. Frontend Component          | `einstellungen.component.ts`: `hasRealmRole('featureflags:manage')` statt `'zev_admin'`                                                                   |
|  [x]   | 8. ArchUnit-Regel              | `ArchitectureTest.preAuthorizeShouldUsePermissionsNotRoles`: Controller-`@PreAuthorize` nutzen nur `hasAuthority`, kein `hasRole`/`hasAnyRole`             |
|  [x]   | 9. Backend-Tests               | `SecurityConfigTest` (Converter 1:1 ohne `ROLE_`) + `ControllerAuthorizationTest` (Security aktiv: Permission→Zugriff, fehlend→403, unauth→401, org_admin↛Feature-Flags) |
|  [x]   | 10. Frontend-Tests             | `auth.guard.spec.ts` (neu, 7 Tests: Permission-Prüfung inkl. Redirect); `einstellungen.component.spec.ts` auf `'featureflags:manage'` angepasst. `isAccessAllowed` für Test exportiert |
|  [x]   | 11. E2E-Tests                  | `composite-roles.spec.ts` (5 Szenarien: zev_admin/org_admin/zev_user-Zugriff); Helper `handleKeycloakLogin` multi-user + `loginAs`. **Grün** (5/5 chromium) nach App-Rebuild + Anlage des `orgadmin`-Users im Realm |
|  [x]   | 12. Dokumentation              | `docs/Anleitung-keycloak.md` (Rollen/Permissions-Sektion), `CLAUDE.md` (API-/Routen-Tabellen, Auth, Test-User); `Berechtigungen.md` verifiziert          |

> **Deploy-Reihenfolge (NFR-3):** Phasen 1–2 (Realm) müssen **vor** dem Anwendungs-Deploy (Phasen 3–4) aktiv sein, sonst verlieren Admins Zugriff. Lokal: Keycloak mit aktualisiertem Realm neu importieren, bevor das Backend mit den neuen `hasAuthority`-Checks startet.

## Permission-Mapping (Referenz für Phase 4)

| Controller / Endpunkt                                       | Permission              |
|-------------------------------------------------------------|-------------------------|
| `EinheitController` `GET /api/einheit` (Liste)              | `einheit:read`          |
| `EinheitController` übrige                                  | `einheit:write`         |
| `MesswerteController` `GET /by-einheit`                     | `messwerte:read`        |
| `MesswerteController` `upload`, `calculate-distribution`, `calculation-progress` | `messwerte:write` |
| `StatistikController` alle                                  | `statistik:read`        |
| `TranslationController` `GET /api/translations`             | `translations:read`     |
| `TranslationController` `list`/`POST`/`PUT`/`DELETE`        | `translations:manage`   |
| `TarifController` alle                                      | `tarife:manage`         |
| `MieterController` alle                                     | `mieter:manage`         |
| `DebitorController` alle                                    | `debitoren:manage`      |
| `RechnungController` alle                                   | `rechnungen:manage`     |
| `EinstellungenController` alle                              | `einstellungen:write`   |
| `FeatureFlagController` `GET /api/feature-flags`            | `featureflags:read`     |
| `FeatureFlagController` `/admin`, `PUT/{key}`, `DELETE/{key}` | `featureflags:manage` |
| `LizenzenController` alle                                   | `lizenzen:read`         |

Composite-Zuordnung (Phasen 1–2): `zev_user` = alle `*:read` + `rechnungen:manage` + `debitoren:manage`; `org_admin` = `zev_user` + `einstellungen:write` + `einheit:write` + `messwerte:write` + `tarife:manage` + `mieter:manage`; `zev_admin` = `org_admin` + `translations:manage` + `featureflags:manage`.

## Validierungen

### Backend (Autorisierung)
- Jeder geschützte Endpunkt erzwingt genau die Permission gemäss Mapping via `@PreAuthorize("hasAuthority('<permission>')")`.
- **Deny by default:** Fehlt die Permission (auch bei fehlkonfiguriertem Realm), wird der Zugriff verweigert (403).
- Fachrollen (`zev_user`, `zev_admin`, `org_admin`) werden im Code **nicht mehr** direkt geprüft.
- `everyEndpointMustBeAuthorized` (ArchUnit) bleibt erfüllt; optionale Zusatzregel verbietet `hasRole`/`hasAnyRole` in Controllern.
- `org_id`/Mandantentrennung unverändert server-seitig aus dem JWT – von der Umstellung nicht berührt.

### Frontend (UX-Gating, nicht sicherheitsrelevant)
- `AuthGuard` erlaubt eine Route, wenn der User **eine** der `data.permissions` besitzt (`.some()`); sonst Redirect auf `/`.
- Fehlt `data.permissions`/ist leer: Zugriff erlaubt (nur Authentifizierung nötig) – wie bisher bei fehlenden `roles`.
- `EinstellungenComponent` zeigt die Feature-Flag-Sektion nur bei Permission `featureflags:manage`; lädt Admin-Flags nur dann.

### Regressions-Invariante
- Effektiver Zugriff je Fachrolle identisch zur Matrix in `Berechtigungen.md`: `zev_user` = Lesen + Rechnungen/Debitoren; `org_admin` = `zev_user` + Einstellungen/Einheiten/Messwerte/Tarife/Mieter; `zev_admin` = alles.

## Offene Punkte / Annahmen

Aus Spec-Abschnitt 8 übernommene Annahmen (vorbelegt, bei Bedarf korrigieren):

- **Präfix:** Authorities ohne Präfix, Prüfung via `hasAuthority('einstellungen:write')` (kein `PERM_`/`ROLE_`).
- **Composites verschachtelt:** `zev_user ⊂ org_admin ⊂ zev_admin`. Folge: Ein `zev_admin`-Token enthält auch die Rollen `org_admin`/`zev_user` – für die Permission-Prüfung irrelevant.
- **`debitoren:manage`** als eigenständige Permission (nicht mit `rechnungen:manage` zusammengefasst).
- **Lese-Permissions** (`*:read`) sind Teil der Basisrolle `zev_user` und damit für jeden Fachnutzer verfügbar.
- **`AuthController.logout`** bleibt bei `isAuthenticated()` (keine Permission).
- **ArchUnit-Zusatzregel** (Phase 8) wird umgesetzt; falls unerwünscht, Phase entfällt.
- **Token-Grösse:** durch zusätzliche Permission-Rollen minimal grösser, unkritisch (NFR-1).
- Es entstehen **keine** neuen DB-Tabellen/Entities und **keine** Flyway-Migration – Permissions liegen ausschliesslich in Keycloak.
