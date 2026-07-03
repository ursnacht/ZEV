# Composite-Roles (Permission-basierte Autorisierung)

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Einführung einer feingranularen **Permission-Ebene** zwischen den fachlichen Keycloak-Rollen und der Anwendung. Die Anwendung prüft künftig **Permissions** (z.B. `einstellungen:write`) statt Fachrollen (`zev_admin`, `org_admin`). Welche Fachrolle welche Permissions bündelt, wird über **Keycloak Composite Roles** konfiguriert.
* **Warum machen wir das:**
  * Die Autorisierungslogik im Code (`@PreAuthorize`, Frontend-Routen, Component-Checks) wird von der Rollenstruktur **entkoppelt**.
  * Eine neue Fachrolle oder eine geänderte Berechtigungsverteilung erfordert dann nur eine **Neu-Zusammenstellung in Keycloak** – **kein** Code-Deploy.
  * `@PreAuthorize` beschreibt fachlich, *was* erlaubt ist (die Aktion), nicht *wer* (die Rolle).
* **Aktueller Stand:** Controller prüfen direkt Fachrollen (`@PreAuthorize("hasRole('zev_admin')")`, `hasAnyRole('zev_admin','org_admin')`), das Frontend prüft Fachrollen in `app.routes.ts` (`data.roles`) und in Komponenten (`keycloak.hasRealmRole('zev_admin')`). Jede neue Rolle (zuletzt `org_admin`) zwang zu Code-Änderungen an mehreren Stellen. Die aktuelle Zuordnung ist in `Specs/Berechtigungen.md` dokumentiert.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Permission-Katalog (pro Fachbereich/Aktion)

Es wird folgender Satz an **Permission-Rollen** (Realm-Rollen) im Keycloak-Realm definiert:

| Permission              | Deckt ab (Endpunkte / Feature)                                                        |
|-------------------------|----------------------------------------------------------------------------------------|
| `einheit:read`          | `GET /api/einheit` (Liste)                                                             |
| `einheit:write`         | `GET /api/einheit/{id}`, `POST`, `PUT`, `DELETE`, `POST /api/einheit/match`            |
| `messwerte:read`        | `GET /api/messwerte/by-einheit`                                                        |
| `messwerte:write`       | `POST /upload`, `POST /calculate-distribution`, `GET /calculation-progress`           |
| `statistik:read`        | `GET /api/statistik/**`                                                                |
| `tarife:manage`         | `TarifController` (alle)                                                               |
| `mieter:manage`         | `MieterController` (alle)                                                              |
| `rechnungen:manage`     | `RechnungController` (alle)                                                            |
| `debitoren:manage`      | `DebitorController` (alle)                                                             |
| `translations:read`     | `GET /api/translations` (i18n-Laufzeit)                                                |
| `translations:manage`   | `GET /list`, `POST`, `PUT`, `DELETE` (Übersetzungs-Editor)                             |
| `einstellungen:write`   | `EinstellungenController` (alle)                                                       |
| `featureflags:read`     | `GET /api/feature-flags` (effektive Flags, UI-Steuerung)                               |
| `featureflags:manage`   | `GET /admin`, `PUT /{key}`, `DELETE /{key}`                                            |
| `lizenzen:read`         | `GET /api/lizenzen`                                                                    |

### FR-2: Fachrollen als hierarchische Composite Roles

Die bestehenden Fachrollen bleiben erhalten und werden als **verschachtelte Composite Roles** zusammengestellt (Hierarchie `zev_user ⊂ org_admin ⊂ zev_admin`):

* **`zev_user`** (Composite) → `einheit:read`, `messwerte:read`, `statistik:read`, `translations:read`, `featureflags:read`, `lizenzen:read`, `rechnungen:manage`, `debitoren:manage`
* **`org_admin`** (Composite) → **`zev_user`** + `einstellungen:write`, `einheit:write`, `messwerte:write`, `tarife:manage`, `mieter:manage`
* **`zev_admin`** (Composite) → **`org_admin`** + `translations:manage`, `featureflags:manage`

Keycloak löst verschachtelte Composites auf und liefert **alle effektiven Rollen** (Fachrollen + Permissions) im Token unter `realm_access.roles`. Einem User wird künftig nur seine **Fachrolle** zugewiesen; Basis-Lesezugriff und Permissions kommen über die Composite-Auflösung.

### FR-3: Backend – Autorisierung über Permissions

1. Der `JwtAuthenticationConverter` (`SecurityConfig`) mappt jede Rolle aus `realm_access.roles` **1:1** auf eine `GrantedAuthority` (ohne `ROLE_`-Präfix), z.B. `einstellungen:write`.
2. Alle `@PreAuthorize`-Ausdrücke werden von `hasRole(...)` / `hasAnyRole(...)` auf **`hasAuthority('<permission>')`** umgestellt, gemäss FR-1.
   ```java
   // vorher
   @PreAuthorize("hasAnyRole('zev_admin', 'org_admin')")
   // nachher
   @PreAuthorize("hasAuthority('einstellungen:write')")
   ```
3. Fachrollen (`zev_user`, `zev_admin`, `org_admin`) werden im Anwendungscode **nicht mehr direkt** geprüft.

### FR-4: Frontend – Route-Guard über Permissions

1. `app.routes.ts` verwendet statt `data.roles` neu **`data.permissions`** (Liste), z.B. `data: { permissions: ['einstellungen:write'] }`.
2. Der `AuthGuard` prüft, ob der User **eine** der geforderten Permissions besitzt (`.some()`), gegen die effektiven Realm-Rollen (`grantedRoles.realmRoles`, die die Permissions via Composite enthalten).
3. Komponenten prüfen Permissions statt Fachrollen:
   ```ts
   // vorher
   readonly canManageFeatureFlags = inject(Keycloak).hasRealmRole('zev_admin');
   // nachher
   readonly canManageFeatureFlags = inject(Keycloak).hasRealmRole('featureflags:manage');
   ```

### FR-5: Dokumentation

* `Specs/Berechtigungen.md` wird aktualisiert: neue Zuordnungstabelle **Fachrolle → Permissions** sowie die Matrix als **Feature × Permission** (statt Feature × Rolle). Die Endpunkt-Referenz zeigt die geprüfte Permission je Endpunkt.
* `docs/Anleitung-keycloak.md` beschreibt Anlage der Permission-Rollen und die Composite-Zusammenstellung.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

* [ ] Der Realm (`keycloak/realms/zev-realm.json`) enthält alle 15 Permission-Rollen aus FR-1.
* [ ] `zev_user`, `org_admin`, `zev_admin` sind Composite Roles mit der Zusammenstellung aus FR-2 (verschachtelt).
* [ ] Ein User mit **nur** der Fachrolle `zev_admin` (ohne explizite `zev_user`-Zuweisung) hat im Token alle Permissions inkl. der Lese-Permissions.
* [ ] Der `JwtAuthenticationConverter` erzeugt Authorities 1:1 aus `realm_access.roles` (ohne `ROLE_`-Präfix).
* [ ] Kein Controller/keine Methode prüft mehr eine Fachrolle; alle `@PreAuthorize` verwenden `hasAuthority('<permission>')` gemäss FR-1.
* [ ] `org_admin` kann Einstellungen speichern (`PUT /api/einstellungen` → 200), erhält aber bei `PUT /api/feature-flags/{key}` **403**.
* [ ] `zev_user` erhält bei den Verwaltungs-Endpunkten ohne Leseberechtigung (`tarife`, `mieter`, `einstellungen`, `messwerte:write`, `einheit:write`, `featureflags:manage`, `translations:manage`) **403**; auf `rechnungen`/`debitoren` hat `zev_user` hingegen Zugriff.
* [ ] Der **effektive Zugriff** jeder Fachrolle entspricht der Matrix in `Specs/Berechtigungen.md` (inkl. der angepassten `org_admin`-/`zev_user`-Permissions).
* [ ] Frontend-Routen nutzen `data.permissions`; ein User ohne passende Permission wird auf `/` umgeleitet.
* [ ] Die Feature-Flag-Sektion in `EinstellungenComponent` ist nur bei Permission `featureflags:manage` sichtbar.
* [ ] Die drei Test-User (`user`, `testuser`, `orgadmin`) funktionieren unverändert (gleiches Zugriffsverhalten wie vor der Migration).
* [ ] `Specs/Berechtigungen.md` und `docs/Anleitung-keycloak.md` sind aktualisiert.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Keine spürbare Laufzeitänderung – die Autorisierung bleibt eine In-Memory-Prüfung der Token-Authorities. Das Token wird durch die zusätzlichen Permission-Rollen geringfügig grösser (< 1 KB erwartet); unkritisch.

### NFR-2: Sicherheit
* **Server-seitige Durchsetzung bleibt massgeblich:** `@PreAuthorize` (`hasAuthority(...)`) ist die verbindliche Autorisierung. Frontend-Prüfungen (Guard, Component) sind reine UX/Anzeige.
* Die `org_id` (Mandantentrennung) bleibt unverändert server-seitig aus dem JWT abgeleitet – Permissions ändern **nichts** an der Mandantenfähigkeit.
* Permission-Zuordnung je Fachrolle (Ziel gemäss `Specs/Berechtigungen.md`):
  * `zev_user`: Lese-Permissions (`*:read`) **+** `rechnungen:manage`, `debitoren:manage`.
  * `org_admin`: `zev_user` **+** `einstellungen:write`, `einheit:write`, `messwerte:write`, `tarife:manage`, `mieter:manage` (kein `translations:manage`, kein `featureflags:manage`).
  * `zev_admin`: alle Permissions.
* Fehlkonfiguration (Fachrolle ohne aufgelöste Permissions) darf **nicht** zu unkontrolliertem Zugriff führen – im Zweifel Zugriff verweigern (Deny by default).

### NFR-3: Kompatibilität
* Bestehende Keycloak-User behalten ihre Fachrollen-Zuweisung; durch die beibehaltenen Composite-Rollen ist **keine User-Migration** nötig.
* **Deploy-Reihenfolge:** Der aktualisierte Realm (Permission-Rollen + Composites) muss **vor** dem Anwendungs-Deploy aktiv sein, sonst verlieren Admins Zugriff (die neuen `hasAuthority`-Checks würden ins Leere laufen).
* Keine Datenbankänderung nötig – Permissions liegen ausschliesslich in Keycloak.

## 5. Edge Cases & Fehlerbehandlung

* **Token ohne `realm_access` / ohne Rollen:** Converter liefert leere Authority-Liste → alle geschützten Endpunkte 401/403 (bestehendes Verhalten).
* **Realm noch nicht migriert (Permission-Rollen fehlen):** Admin-User besitzt zwar `zev_admin`, aber keine `*:manage`-Permissions → Zugriff verweigert (403). Absicherung über NFR-3 Deploy-Reihenfolge.
* **Unbekannte Permission in `data.permissions` einer Route:** Guard findet keine passende Realm-Rolle → Redirect auf `/` (kein Zugriff).
* **`GET /api/feature-flags` (featureflags:read) schlägt für einen User ohne diese Permission fehl:** Frontend fällt konservativ auf „alle Flags inaktiv" zurück (bestehendes Verhalten in `FeatureFlagService.load()`), App bleibt lauffähig.
* **Netzwerkfehler beim Token-Refresh:** unverändert durch Keycloak-Adapter behandelt.
* **Doppelte/leere Permission-Zuweisung im Realm:** Keycloak dedupliziert Rollen im Token; leere Composites führen lediglich zu weniger Authorities.

## 6. Abhängigkeiten & betroffene Funktionalität

* **Voraussetzungen:** Bestehendes Keycloak-Setup mit Realm `zev`; Rolle `org_admin` (bereits eingeführt).
* **Betroffener Code:**
  * Keycloak: `keycloak/realms/zev-realm.json` (Permission-Rollen + Composite-Zusammenstellung).
  * Backend: `config/SecurityConfig.java` (`JwtAuthenticationConverter`); **alle** Controller mit `@PreAuthorize` (`Einheit`, `Messwerte`, `Statistik`, `Translation`, `Tarif`, `Mieter`, `Debitor`, `Rechnung`, `Einstellungen`, `FeatureFlag`, `Lizenzen`, ggf. `Auth`).
  * Frontend: `app.routes.ts` (`data.roles` → `data.permissions`), `guards/auth.guard.ts` (Prüfung gegen Permissions), `components/einstellungen/einstellungen.component.ts` (Permission-Check).
  * Tests: `ArchitectureTest.java` (falls Regeln auf Rollennamen prüfen), Controller-/Component-Specs, ggf. E2E.
  * Dokumentation: `Specs/Berechtigungen.md`, `docs/Anleitung-keycloak.md`, `CLAUDE.md` (API-/Routen-Tabellen, Rollenbeschreibung), Skill `8_security-check` (Referenz auf @PreAuthorize-Matrix).
* **Datenmigration:** Keine DB-Migration. Reine Keycloak-Realm-Anpassung.

## 7. Abgrenzung / Out of Scope

* **Keine** Umstellung auf Keycloak Authorization Services (UMA/RPT, Resource-/Scope-/Policy-Modell) – bewusst zu schwergewichtig für diese Anwendung.
* **Keine** neuen fachlichen Rollen. Die effektive Berechtigungsverteilung folgt der Matrix in `Specs/Berechtigungen.md`; Anpassungen daran erfolgen über die Composite-Zusammenstellung in Keycloak, nicht im Code.
* **Keine** attribut-/kontextbasierten Policies (z.B. zeit- oder mandantenspezifische Permissions).
* **Kein** UI zur Verwaltung von Rollen/Permissions in der Anwendung – Konfiguration erfolgt in Keycloak.
* **Keine** Änderung an der Mandantentrennung (`org_id`).

## 8. Offene Fragen

* **Präfix-Konvention:** Authorities ohne Präfix (`einstellungen:write`, via `hasAuthority`) vs. eigenes Präfix (`PERM_…`)? Vorschlag: ohne Präfix, da Fachrollen nicht mehr per `hasRole` geprüft werden. → Bestätigen.
* **Verschachtelte vs. flache Composites:** `zev_admin` nested über `org_admin`/`zev_user` (DRY, aber Fachrollen erscheinen als Kind-Rollen im Token) vs. direkte Auflistung aller Permissions je Fachrolle. Vorschlag: verschachtelt (FR-2). → Bestätigen.
* **`debitoren:manage`:** Eigene Permission (wie hier vorgeschlagen) oder mit `rechnungen:manage` zusammenfassen (fachlich verwandt)? → Klären.
* **`translations:read` / `featureflags:read`:** Diese Lese-Permissions sind Teil der Basisrolle `zev_user`. Bestätigen, dass jeder authentifizierte Fachnutzer sie erhalten soll.
* **ArchUnit-Regel:** Soll eine Architektur-Regel ergänzt werden, die sicherstellt, dass Controller-Methoden nur `hasAuthority(...)` (keine `hasRole`) verwenden?
* **`AuthController.logout` (`isAuthenticated()`):** bleibt ohne Permission-Check – bestätigen.
