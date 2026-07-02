# Feature-Flag-Upload

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Der Messdatenupload (`/upload`, `MesswerteUploadComponent`) soll pro Mandant über ein Feature-Flag ein- und ausgeschaltet werden können. Bei deaktiviertem Flag wird der Menüeintrag ausgeblendet, die Route gesperrt und der Upload-Endpunkt abgelehnt.
* **Warum machen wir das:** Nicht jeder Mandant nutzt den manuellen CSV-Upload (z.B. wenn Messwerte künftig automatisch via MQTT/SmartMeter geliefert werden). Über ein Flag kann der Upload mandantenspezifisch deaktiviert werden, ohne Redeploy und ohne den Code für andere Mandanten zu ändern. Dies ist die erste konkrete Anwendung der Feature-Flag-Infrastruktur (siehe `Specs/FeatureFlag.md`).
* **Aktueller Stand:** Der Upload ist aktuell für alle Mandanten fest sichtbar und erreichbar. Der Menüeintrag `MESSWERTE_UPLOAD` verweist unbedingt auf `/upload`; die Route ist für Rollen `zev`/`zev_admin` freigegeben; der Upload-Endpunkt `POST /api/messwerte/upload` verlangt `zev_admin`. Eine Feature-Flag-Steuerung existiert noch nicht. Die Basis-Infrastruktur aus `FeatureFlag.md` (Enum, Service, Endpunkte, JSONB-Spalte, Cache) ist noch **nicht** implementiert und ist Voraussetzung für dieses Feature.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Ablauf / Flow

**Flag-Definition**
1. Im zentralen `FeatureFlag`-Enum (siehe `FeatureFlag.md`) wird ein Flag `MESSWERTE_UPLOAD` deklariert:
   * technischer Key: `MESSWERTE_UPLOAD`
   * globaler Default: `true` (rückwärtskompatibel – Upload bleibt für alle Mandanten aktiv, solange nicht explizit deaktiviert)
   * Beschreibungs-Übersetzungs-Key: `FEATURE_FLAG_MESSWERTE_UPLOAD`

**Verwaltung durch den Admin (in den Einstellungen)**
2. Ein Administrator (`zev_admin`) öffnet die Seite „Einstellungen" (`/einstellungen`).
3. Zusätzlich zu den bestehenden Rechnungs-Einstellungen zeigt die Seite einen neuen Abschnitt „Feature-Flags" mit einem Toggle je deklariertem Flag (initial: „Messdatenupload"), jeweils mit übersetzter Beschreibung und dem aktuellen effektiven Zustand des eigenen Mandanten.
4. Der Admin schaltet den Toggle „Messdatenupload" ein/aus. Das System speichert die mandantenspezifische Überschreibung über die Feature-Flag-Endpunkte (`PUT /api/feature-flags/{key}` mit `{ "enabled": boolean }`) und invalidiert den Cache; der neue Zustand ist nach erneutem Laden der Flags wirksam.
5. Das System quittiert das Speichern mit einer Erfolgsmeldung (`zev-message--success`).

**Auswirkung im Frontend**
6. Beim App-Start (nach Login) lädt der `FeatureFlagService` die effektiven Flags (`GET /api/feature-flags`).
7. Ist `MESSWERTE_UPLOAD` **aktiv**, wird der Menüeintrag „Messdatenupload" angezeigt und die Route `/upload` ist erreichbar.
8. Ist `MESSWERTE_UPLOAD` **inaktiv**:
   * Der Menüeintrag „Messdatenupload" wird nicht gerendert (`*appFeature="'MESSWERTE_UPLOAD'"` bzw. `featureFlagService.isEnabled(...)`).
   * Ein direkter Aufruf von `/upload` (URL/Bookmark) wird durch den `FeatureFlagGuard` abgewiesen und auf `/startseite` umgeleitet.

**Auswirkung im Backend**
9. Ist `MESSWERTE_UPLOAD` für den Mandanten inaktiv, lehnt der Endpunkt `POST /api/messwerte/upload` die Anfrage mit HTTP 403 (Feature deaktiviert) ab – unabhängig von der Rolle. Die serverseitige Flag-Prüfung nutzt `FeatureFlagService` mit der `org_id` aus `OrganizationContextService`.

### FR-2: Persistierung
* **Keine neue Tabelle/Spalte für dieses Feature.** Die mandantenspezifische Überschreibung wird in der durch `FeatureFlag.md` eingeführten JSONB-Spalte `feature_flags` auf `zev.organisation` als Eintrag `{ "MESSWERTE_UPLOAD": true|false }` abgelegt.
* **Effektiver Wert:** mandantenspezifische Überschreibung (falls vorhanden), sonst globaler Default (`true`), sonst `false`.
* **Übersetzungen (Flyway-Migration, `ON CONFLICT (key) DO NOTHING`, DE/EN):**
  * `FEATURE_FLAG_MESSWERTE_UPLOAD` – Beschreibung des Flags (z.B. „Messdatenupload (CSV-Upload) aktivieren" / „Enable measurement data upload (CSV)")
  * `FEATURE_FLAGS` – Abschnittstitel in den Einstellungen (z.B. „Feature-Flags")
  * ggf. `FEATURE_FLAG_DEAKTIVIERT` – Fehlermeldung bei gesperrtem Zugriff/Endpunkt (z.B. „Diese Funktion ist für Ihren Mandanten nicht aktiviert.")
* **Caching:** Nutzung des in `FeatureFlag.md` definierten Caffeine-Caches `featureFlags` (Key = `orgId`); Schreiboperationen evicten den Eintrag.

### FR-3: Layout
* **Einstellungen-Seite (`/einstellungen`):** Neuer Abschnitt „Feature-Flags" unterhalb der bestehenden Rechnungs-Einstellungen. Je Flag eine Zeile mit übersetzter Beschreibung und einem Toggle/Checkbox (`zev-checkbox`). Verwendung des Design Systems (`zev-form-section`, `zev-checkbox`, `zev-message`, `app-icon`).
* **Kein separater Menüeintrag und keine eigene Route** `/feature-flags` (bewusste Abweichung von `FeatureFlag.md` FR-3 – die dedizierte Feature-Flag-Seite ist hier Out of Scope; Verwaltung erfolgt integriert in den Einstellungen).
* **Navigation:** Der bestehende Menüeintrag „Messdatenupload" (`navigation.component.html`) wird mit `*appFeature="'MESSWERTE_UPLOAD'"` umschlossen, sodass er bei inaktivem Flag nicht gerendert wird.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] Das Flag `MESSWERTE_UPLOAD` ist im `FeatureFlag`-Enum mit globalem Default `true` und Beschreibungs-Key `FEATURE_FLAG_MESSWERTE_UPLOAD` deklariert.
* [ ] In den Einstellungen (`/einstellungen`) gibt es einen Abschnitt „Feature-Flags" mit einem Toggle „Messdatenupload", der den effektiven Zustand des eigenen Mandanten anzeigt (nur `zev_admin`).
* [ ] Deaktivieren des Toggles speichert die Überschreibung (`PUT /api/feature-flags/MESSWERTE_UPLOAD` mit `{ "enabled": false }`) und wird mit `zev-message--success` quittiert.
* [ ] Bei aktivem Flag wird der Menüeintrag „Messdatenupload" angezeigt; bei inaktivem Flag wird er **nicht** gerendert.
* [ ] Bei inaktivem Flag wird ein direkter Aufruf von `/upload` durch den `FeatureFlagGuard` auf `/startseite` umgeleitet.
* [ ] Bei aktivem Flag ist die Route `/upload` normal erreichbar (rollenbasiert wie bisher).
* [ ] Bei inaktivem Flag lehnt `POST /api/messwerte/upload` die Anfrage mit HTTP 403 und einer sinnvollen Fehlermeldung ab.
* [ ] Bei aktivem Flag funktioniert `POST /api/messwerte/upload` unverändert (für `zev_admin`).
* [ ] Die Flag-Überschreibung eines Mandanten wirkt sich nicht auf andere Mandanten aus (Mandantentrennung); `org_id` wird serverseitig aus dem JWT bestimmt.
* [ ] Ein Mandant ohne Überschreibung sieht/nutzt den Upload weiterhin (Default `true`).
* [ ] Alle neuen UI-Texte kommen via `TranslationService` (DE/EN); die Flag-Beschreibung ist in beiden Sprachen vorhanden.
* [ ] Die Darstellung verwendet das Design System.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Die Flag-Prüfung im Frontend (`isEnabled`) erfolgt in-memory ohne zusätzlichen API-Call (Flags werden einmalig beim App-Start geladen).
* Die serverseitige Flag-Prüfung im Upload-Endpunkt nutzt den Cache und darf den Upload nicht spürbar verzögern (< 5 ms Overhead bei Cache-Hit).

### NFR-2: Sicherheit
* Verwaltung der Flags in den Einstellungen: Rolle `zev_admin` (`@PreAuthorize` auf den Feature-Flag-Endpunkten, `AuthGuard` mit `roles: ['zev_admin']` auf `/einstellungen`).
* Lesen der effektiven Flags (`GET /api/feature-flags`): Rolle `zev`.
* Der Upload-Endpunkt bleibt zusätzlich rollen-geschützt (`zev_admin`); die Flag-Prüfung ist eine **ergänzende** Sperre, kein Ersatz für Autorisierung (vgl. `FeatureFlag.md` NFR-2).
* `org_id` wird ausschliesslich serverseitig via `OrganizationContextService` gesetzt, nie vom Client übergeben.

### NFR-3: Kompatibilität
* Additiv: keine neue Tabelle/Spalte über die in `FeatureFlag.md` definierte `feature_flags`-Spalte hinaus.
* Fehlt eine Überschreibung (NULL/kein Eintrag), gilt der Default `true` → bestehende Mandanten behalten den Upload ohne Migration.
* Bestehende Upload-Funktionalität bleibt bei aktivem Flag unverändert.

## 5. Edge Cases & Fehlerbehandlung
* **Keine Überschreibung vorhanden:** Default `true` → Menü sichtbar, Route erreichbar, Endpunkt offen.
* **Flag inaktiv + Direktaufruf `/upload`:** Guard leitet auf `/startseite` um (kein leerer Screen, kein Fehler).
* **Flag inaktiv + direkter API-Aufruf `POST /api/messwerte/upload`:** HTTP 403 mit übersetzbarer Fehlermeldung (kein 500).
* **Flags können im Frontend nicht geladen werden (Netzwerkfehler):** Konservativer Fallback (Upload-Menü/-Route aus bzw. gemäss `FeatureFlagService`-Fallback aus `FeatureFlag.md`); Fehler als `zev-message--error` anzeigen; App bleibt nutzbar.
* **Admin deaktiviert Upload, während ein Upload läuft/geöffnet ist:** Die neue Sichtbarkeit greift nach erneutem Laden der Flags; ein bereits gestarteter Upload-Request wird bei inaktivem Flag vom Backend mit 403 abgelehnt (Last-Write-Wins, kein Locking).
* **Unbekannter/verschriebener Flag-Key:** Nicht relevant für die UI (Key ist im Code fest); Backend behandelt unbekannte Keys gemäss `FeatureFlag.md` (Lesen → `false`, Schreiben → 400).
* **Leere/korrupte `feature_flags`-Spalte:** Rückfall auf Default (`true`), Fehler loggen (gemäss `FeatureFlag.md`).

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:**
  * **`FeatureFlag.md` muss umgesetzt sein** (Enum `FeatureFlag`, `FeatureFlagService`, `FeatureFlagController`, JSONB-Spalte `feature_flags`, Cache `featureFlags`, Frontend `FeatureFlagService`, `*appFeature`-Direktive, `FeatureFlagGuard`, App-Init-Laden der Flags).
  * Multi-Tenancy (`OrganizationContextService`), Keycloak-Rollen `zev`/`zev_admin`.
* **Betroffener Code:**
  * `FeatureFlag`-Enum – neues Flag `MESSWERTE_UPLOAD` (Default `true`).
  * `MesswerteController#uploadCsv` (`POST /api/messwerte/upload`) – serverseitige Flag-Prüfung ergänzen (403 bei inaktiv).
  * `app.routes.ts` – Route `/upload` um `FeatureFlagGuard` (`data: { featureFlag: 'MESSWERTE_UPLOAD' }`) erweitern.
  * `navigation.component.html` – Menüeintrag „Messdatenupload" mit `*appFeature="'MESSWERTE_UPLOAD'"`.
  * `einstellungen.component.ts` / `.html` – Abschnitt „Feature-Flags" mit Toggle(s); Anbindung an Feature-Flag-Endpunkte (ggf. via `FeatureFlagService`).
  * Flyway-Migration – Übersetzungen (`FEATURE_FLAG_MESSWERTE_UPLOAD`, `FEATURE_FLAGS`, `FEATURE_FLAG_DEAKTIVIERT`) DE/EN.
* **Datenmigration:** Keine – Default `true` gilt automatisch für alle bestehenden Mandanten ohne Eintrag.

## 7. Abgrenzung / Out of Scope
* Keine dedizierte Feature-Flag-Seite/Route `/feature-flags` und kein eigener Menüeintrag (Verwaltung erfolgt integriert in `/einstellungen`) – die generische Admin-Seite aus `FeatureFlag.md` FR-3 ist hier nicht Gegenstand.
* Keine Neu-Implementierung der Feature-Flag-Infrastruktur – diese wird durch `FeatureFlag.md` bereitgestellt und hier nur genutzt/ergänzt.
* Kein Flag um andere Features (nur Messdatenupload); weitere Flags sind später additiv ergänzbar.
* Keine prozentualen Rollouts, kein A/B-Testing, keine Flags pro Benutzer, kein Audit-Log, kein Scheduling (vgl. `FeatureFlag.md`).

## 8. Offene Fragen
* Geklärt (via Rückfrage): Verwaltung der Flags erfolgt **integriert in `/einstellungen`** (nicht auf separater Seite).
* Geklärt (via Rückfrage): Bei inaktivem Flag wird **sowohl** der Menüeintrag ausgeblendet **als auch** die Route via `FeatureFlagGuard` gesperrt.
* Geklärt (via Rückfrage): Der Backend-Endpunkt `POST /api/messwerte/upload` wird bei inaktivem Flag **ebenfalls gesperrt** (403).
* Zu bestätigen: Genauer HTTP-Status für den gesperrten Endpunkt – Vorschlag 403 (Forbidden); Alternative 409/404. (Annahme: 403.)
* Zu bestätigen: Sollen bei inaktivem Upload auch abhängige Aktionen (z.B. `POST /api/messwerte/calculate-distribution`) gesperrt werden, oder nur der reine Upload? (Annahme: nur der Upload-Endpunkt.)
