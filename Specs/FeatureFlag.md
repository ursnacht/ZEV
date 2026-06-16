# FeatureFlag

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Eine leichtgewichtige, selbst gehostete Feature-Flag-Lösung, mit der einzelne Features pro Mandant (`org_id`) zur Laufzeit ein- und ausgeschaltet werden können – ohne externe Library und ohne zusätzlichen Server. Ergänzend sollen rein statische, deploy-feste Schalter via `@ConditionalOnProperty` möglich sein.
* **Warum machen wir das:** Features sollen kontrolliert ausgerollt werden können (z.B. für einen Pilot-Mandanten freischalten, bei Problemen schnell deaktivieren – Kill-Switch), ohne Redeploy und ohne Bindung an eine externe SaaS- oder Server-Komponente. Der bestehende Stack (Spring Boot 4, Caffeine, JSONB-pro-Mandant, Keycloak-Organisationen, Angular-Runtime-Config) liefert alle Bausteine bereits.
* **Aktueller Stand:** Es gibt keine Feature-Flags. Verhalten ist für alle Mandanten gleich und nur über Code/Deploy änderbar. Mandantenspezifische Konfiguration existiert bereits für die Rechnungsstellung (`organisation.konfiguration` JSONB, verwaltet durch `EinstellungenService`); dieses Muster wird hier übernommen, aber bewusst in einer **eigenen Spalte** geführt, um Kollisionen zu vermeiden.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Ablauf / Flow
1. Beim Start der Angular-App (nach erfolgreichem Keycloak-Login) lädt das Frontend die für den aktuellen Mandanten **effektiven** Feature-Flags via `GET /api/feature-flags` und hält sie im `FeatureFlagService`.
2. Komponenten/Templates fragen Flags über `featureFlagService.isEnabled('KEY')`, eine Struktur-Direktive `*appFeature="'KEY'"` oder einen Route-Guard ab und blenden Features entsprechend ein/aus.
3. Ein Administrator (`zev_admin`) öffnet die Seite „Feature-Flags" aus dem Menü.
4. Das System zeigt alle **im Code deklarierten** Flags mit ihrem aktuellen effektiven Zustand für den eigenen Mandanten (inkl. Hinweis, ob der Wert vom globalen Default stammt oder mandantenspezifisch überschrieben ist).
5. Der Administrator schaltet ein Flag ein/aus. Das System speichert die mandantenspezifische Überschreibung und invalidiert den Cache; der neue Zustand ist sofort wirksam (nach Neuladen der Flags im Frontend).
6. Der Administrator kann eine mandantenspezifische Überschreibung wieder entfernen, womit das Flag auf den globalen Default zurückfällt.

### FR-2: Persistierung
* **Flag-Registry (zentral, im Code):** Die Menge der bekannten Flags wird zentral im Enum `FeatureFlag` deklariert, jeweils mit technischem Key (z.B. `NEUE_STATISTIK`), globalem Default (`true`/`false`) und einem Beschreibungs-Übersetzungs-Key (z.B. `FEATURE_FLAG_NEUE_STATISTIK`). Es können zur Laufzeit **keine** beliebigen neuen Keys angelegt werden.
* **Globale Defaults (im Code):** Der globale Default jedes Flags ist als Konstante im `FeatureFlag`-Enum hinterlegt. Es gibt **keine** Default-Konfiguration in `application.yml`. Ist (theoretisch) kein Default deklariert, gilt `false`.
* **Mandantenspezifische Überschreibungen (Laufzeit):** Neue JSONB-Spalte `feature_flags` auf der Tabelle `zev.organisation` (eine Spalte zusätzlich zur bestehenden `konfiguration`). Struktur: flaches Objekt `{ "<key>": true, "<key2>": false }`. Nur abweichende/explizit gesetzte Flags werden gespeichert; nicht enthaltene Keys fallen auf den globalen Default zurück.
* **Effektiver Wert:** `mandantenspezifische Überschreibung (falls vorhanden) sonst globaler Default sonst false`.
* **Caching:** Caffeine-Cache `featureFlags`, Schlüssel = `orgId`, in `CacheConfig` ergänzen. Schreiboperationen evicten den Cache-Eintrag des Mandanten (`@CacheEvict`), zusätzlich greift die bestehende TTL (15 Min) als Backstop.
* **REST-Endpunkte unter `/api/feature-flags`:**
  * `GET /api/feature-flags` – effektive Flags des aktuellen Mandanten als `{ "<key>": boolean, ... }` (Rolle `zev`).
  * `GET /api/feature-flags/admin` – alle deklarierten Flags inkl. Default-Wert, effektivem Wert und Quelle (`DEFAULT` / `OVERRIDE`) für die Admin-Seite (Rolle `zev_admin`).
  * `PUT /api/feature-flags/{key}` mit Body `{ "enabled": boolean }` – setzt eine mandantenspezifische Überschreibung (Rolle `zev_admin`).
  * `DELETE /api/feature-flags/{key}` – entfernt die Überschreibung, Flag fällt auf Default zurück (Rolle `zev_admin`).
* **Statische Backend-Schalter:** Wo ein Feature komplett deploy-fest (nicht pro Mandant) sein soll, werden Beans/Endpunkte zusätzlich mit `@ConditionalOnProperty(prefix = "features", name = "...")` geschaltet. Dies ist unabhängig vom mandantenspezifischen Mechanismus.

### FR-3: Layout
* Neue Route `/feature-flags` mit Komponente `FeatureFlagListComponent` (nur `zev_admin`).
* Neuer Menüeintrag „Feature-Flags" in der Navigation (im Admin-Bereich, z.B. nach „Einstellungen"), mit Feather-Icon (z.B. `toggle-right` oder Fallback `flag`).
* **Seite:** Tabelle aller deklarierten Flags mit Spalten:
  * Flag (technischer Key + übersetzte Beschreibung des Flags)
  * Default (globaler Wert aus dem `FeatureFlag`-Enum)
  * Status (effektiver Wert, als Toggle/Checkbox bedienbar) – „Aktiv" → `zev-status--success`, „Inaktiv" → `zev-status--warning`
  * Quelle (`Default` / `Überschrieben`)
  * Aktion: Überschreibung zurücksetzen (nur sichtbar, wenn `OVERRIDE`)
* Verwende das Design System (`zev-table`, `zev-status`, `zev-checkbox`, `zev-message`, `app-icon`).
* Leerstate, falls keine Flags deklariert sind: Hinweis „Keine Feature-Flags definiert".

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] `GET /api/feature-flags` liefert für einen authentifizierten `zev`-Benutzer die effektiven Flags seines Mandanten als Key→Boolean-Map
* [ ] Ein Flag ohne mandantenspezifische Überschreibung liefert den im `FeatureFlag`-Enum deklarierten globalen Default
* [ ] Ein Flag ohne deklarierten Default liefert `false`
* [ ] Ein nicht deklarierter (unbekannter) Key wird beim Lesen als `false` behandelt und beim Schreiben (`PUT`) mit HTTP 400 abgelehnt
* [ ] `PUT /api/feature-flags/{key}` mit `{ "enabled": true|false }` setzt eine mandantenspezifische Überschreibung und ist nach Cache-Invalidierung sofort über `GET` sichtbar
* [ ] `DELETE /api/feature-flags/{key}` entfernt die Überschreibung; das Flag liefert anschliessend wieder den globalen Default
* [ ] Eine Überschreibung eines Mandanten ist für andere Mandanten nicht sichtbar (Mandantentrennung)
* [ ] `org_id` wird serverseitig aus dem JWT (`OrganizationContextService`) bestimmt, nie vom Client übergeben
* [ ] Die Admin-Seite `/feature-flags` ist nur mit Rolle `zev_admin` erreichbar; Lese-Endpunkt `GET /api/feature-flags` mit Rolle `zev`
* [ ] Das Frontend lädt die Flags beim App-Start (nach Login) und stellt `isEnabled('KEY')` bereit
* [ ] Ein per `*appFeature="'KEY'"` umschlossenes Element wird nur gerendert, wenn das Flag aktiv ist
* [ ] Ein per `FeatureFlagGuard` geschützter Route-Zugriff wird bei inaktivem Flag abgewiesen (Redirect auf Startseite)
* [ ] Die zweite Lese-Anfrage desselben Mandanten wird aus dem Caffeine-Cache bedient (kein erneuter DB-Zugriff bis Eviction/TTL)
* [ ] Statische Backend-Beans/Endpunkte lassen sich via `@ConditionalOnProperty` (`features.*`) deploy-fest schalten
* [ ] Die Admin-Seite zeigt zu jedem Flag eine übersetzte Beschreibung (DE/EN)
* [ ] Alle UI-Texte der Admin-Seite kommen via `TranslationService` (DE/EN)
* [ ] Die Darstellung verwendet das Design System

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* `GET /api/feature-flags` antwortet im Normalfall (Cache-Hit) in unter 100 ms.
* Die Flag-Auflösung darf den App-Start des Frontends nicht spürbar verzögern (ein einziger API-Call beim Init).

### NFR-2: Sicherheit
* `GET /api/feature-flags`: Rolle `zev` (jeder authentifizierte Benutzer, damit das Frontend rendern kann).
* `GET /api/feature-flags/admin`, `PUT`, `DELETE`: Rolle `zev_admin` (`@PreAuthorize`).
* `org_id` wird ausschliesslich serverseitig via `OrganizationContextService` aus dem Keycloak-Token gesetzt.
* Feature-Flags dürfen nicht als Ersatz für Autorisierung dienen: Backend-Endpunkte müssen unabhängig von Flags weiterhin per Rolle abgesichert sein (ein deaktiviertes Frontend-Flag schützt keine API).

### NFR-3: Kompatibilität
* Die neue Spalte `feature_flags` ist additiv; die bestehende `konfiguration`-Spalte und der `EinstellungenService` bleiben unverändert.
* Fehlt die Spalte/der Wert (NULL), verhält sich das System wie „keine Überschreibungen" (alle Defaults) – rückwärtskompatibel.
* Bestehende Funktionalität ist nicht betroffen, solange keine Flags um bestehende Features gelegt werden.

## 5. Edge Cases & Fehlerbehandlung
* **`feature_flags`-Spalte ist NULL/leer:** Leere Überschreibungs-Map → es gelten ausschliesslich globale Defaults.
* **Ungültiges/korruptes JSON in der Spalte:** Fehler loggen (ERROR), auf Defaults zurückfallen, App nicht abstürzen lassen.
* **Unbekannter Flag-Key:** Lesen → `false`; Schreiben (`PUT`/`DELETE`) → HTTP 400 mit Fehlermeldung.
* **Mandant ohne `organisation`-Datensatz:** Defaults zurückgeben (kein 500).
* **Frontend kann Flags nicht laden (Netzwerkfehler):** Konservativer Fallback auf globale Defaults bzw. „alle aus", Fehler im Frontend anzeigen (`zev-message--error`, bleibt bis dismissed); App bleibt nutzbar.
* **Gleichzeitige Bearbeitung durch zwei Admins:** Last-Write-Wins auf Ebene der Überschreibungs-Map; akzeptiert (kein Optimistic Locking vorgesehen).
* **Leere Flag-Registry:** Admin-Seite zeigt Leerstate „Keine Feature-Flags definiert".

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:**
  * `Multi-Tenancy` (`OrganizationContextService`, `organisation`-Tabelle mit `org_id`/JSONB-Muster)
  * `Caffeine`-Cache (`CacheConfig`)
  * Keycloak-Authentifizierung (Rollen `zev`, `zev_admin`)
  * Angular-App-Init-Mechanismus (`app.config.ts`, vgl. Runtime-Config-Laden in `main.ts`)
* **Betroffener Code:**
  * `CacheConfig` – Cache `featureFlags` ergänzen
  * `application.yml` – nur falls optionale statische `@ConditionalOnProperty`-Schalter (`features.*`) genutzt werden; keine Default-Konfiguration der dynamischen Flags
  * `NavigationComponent` – neuer Menüeintrag
  * Angular-Routing (`app.routes.ts`) – Route `/feature-flags`
  * `app.config.ts` – `APP_INITIALIZER` zum Laden der Flags nach Login
* **Neue Dateien (Backend):** Flag-Registry (`FeatureFlag`-Enum mit Key, Default, Beschreibungs-Übersetzungs-Key), `FeatureFlagService`, `FeatureFlagController`, `FeatureFlagDTO` (Admin-Sicht: key, beschreibung, default, effektiv, Quelle), Flyway-Migration 1 (Spalte `feature_flags` auf `organisation`), Flyway-Migration 2 (Übersetzungen: Menü-/Seitentexte + Beschreibungs-Keys je Flag, `ON CONFLICT (key) DO NOTHING`).
* **Neue Dateien (Frontend):** `feature-flag.service.ts`, `feature-flag.model.ts`, `feature-flag.directive.ts` (`*appFeature`), `feature-flag.guard.ts`, `feature-flag-list/`-Komponente.
* **Datenmigration:** Keine Transformation bestehender Daten nötig; nur additive Spalte (`feature_flags`), Default NULL.

## 7. Abgrenzung / Out of Scope
* Keine prozentualen/graduellen Rollouts (z.B. „20 % der Nutzer").
* Kein A/B-Testing, keine Experiment-Auswertung.
* Keine Flags pro **einzelnem Benutzer** – nur pro Mandant (`org_id`) bzw. global statisch.
* Kein Anlegen beliebiger neuer Flag-Keys zur Laufzeit über die UI (Keys werden im Code deklariert).
* Keine Änderungshistorie/Audit-Log der Flag-Schaltungen.
* Keine externe Feature-Flag-Library oder externer Flag-Server (Unleash, Flagsmith, LaunchDarkly o.ä.).
* Kein zeitgesteuertes Aktivieren/Deaktivieren (Scheduling).

## 8. Offene Fragen
Keine offenen Fragen – folgende Punkte wurden entschieden und sind oben eingearbeitet:
* **Speicherort der Überschreibungen:** Eigene, additive JSONB-Spalte `feature_flags` auf `organisation` (die bestehende `konfiguration`-Spalte und der `EinstellungenService` bleiben unangetastet).
* **Default-Quelle:** Globale Defaults ausschliesslich als Konstanten im `FeatureFlag`-Enum – **keine** Default-Konfiguration in `application.yml`. (Optionale statische `@ConditionalOnProperty`-Schalter bleiben davon unberührt.)
* **Cache-Invalidierung über Instanzen:** Einzelinstanz angenommen; lokales `@CacheEvict` plus 15-Min-TTL als Backstop genügt vorerst (keine cluster-weite Invalidierung).
* **Anzeigenamen/Beschreibung der Flags:** Jedes Flag hat eine übersetzte Beschreibung (eigener Translation-Key je Flag), die auf der Admin-Seite angezeigt wird.
