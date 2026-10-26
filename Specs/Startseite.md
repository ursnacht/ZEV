# Startseite erstellen

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Es soll eine neue Startseite erstellt werden. Nach dem Login wird dem Benutzer diese Seite angezeigt. 
* **Warum machen wir das:** Professionalität und User Experience wird erhöht.
* **Aktueller Stand:** Aktuell wird die Seite /chart angezeigt

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?
### FR-1: Ablauf / Flow
1. User loggt sich ein.
2. Das System zeigt die neue Startseite mit geöffneter Navigation an.

### FR-2: Persistierung
* -

### FR-3: Layout
* Das Bild `assets/vZEV.png` wird auf der Startseite angezeigt.

### FR-4: Versions- und Build-Anzeige (Erweiterung)
1. **Datenbank-Version:** Unten auf der Startseite wird die höchste erfolgreich ausgeführte Flyway-Schema-Version angezeigt (z.B. „Datenbank-Version: V83").
   * Quelle: `zev.flyway_schema_history` (höchste Version mit `success = true`, ermittelt über `installed_rank`).
   * Backend: `GET /api/version` (`VersionController`, `@PreAuthorize("isAuthenticated()")` – Startseite ist rollenunabhängig sichtbar); `VersionService` liest die Tabelle read-only via `JdbcTemplate` (bewusste Abweichung vom Repository-Pattern: Flyway-interne Tabelle, keine JPA-Entity; kein `orgFilter` – die Version gilt installationsweit).
2. **Build-Datum:** Zusätzlich wird der Build-Zeitpunkt des Backends angezeigt (z.B. „Build: 17.07.2026 10:15").
   * Quelle: `build-info.properties`, erzeugt vom `spring-boot-maven-plugin` (Goal `build-info`); im Backend als `BuildProperties`-Bean verfügbar, Ausgabe im Schweizer Format `dd.MM.yyyy HH:mm` (lokale Zeitzone).
   * Wird das Jar nicht über das Plugin gebaut (z.B. IDE-Start), fehlt die Bean → das Build-Datum entfällt, ohne Fehler.
3. **Spring-Boot-Version:** Die Spring-Boot-Version des Backends (z.B. „Spring Boot 4.1.2“).
   * Quelle: `SpringBootVersion.getVersion()` – Spring Boot kennt seine Version selbst, ohne Konfiguration und ohne Build-Schritt. Geliefert als `springBootVersion` in `GET /api/version`.
4. **Angular-Version:** Die Angular-Version des ausgelieferten Frontends (z.B. „Angular 22.2.3“).
   * Quelle: `VERSION.full` aus `@angular/core`, direkt im Browser – kein Backend-Aufruf. So steht dort genau die Version, die tatsächlich ausgeliefert wurde.
5. Anzeige-Format: dezente Zeile am unteren Seitenrand, Teile mit ` · ` getrennt:
   `Datenbank-Version: V<nn> · Build: <Datum> · Spring Boot <x.y.z> · Angular <x.y.z>`.
   Labels über Translation-Keys `DB_SCHEMA_VERSION` (V82) und `BUILD_DATUM` (V83); „Spring Boot“ und „Angular“ sind Eigennamen und werden nicht übersetzt.
   > Wozu: Nach einem Deploy lässt sich ohne Entwicklertools prüfen, ob der neue Stand läuft – belegt beim Angular-22- und Spring-Boot-4.1.2-Upgrade auf Hene (26.10.2026).
6. Alle Angaben sind rein informativ: Ist eine davon nicht ermittelbar (`null`), entfällt nur der jeweilige Teil; schlägt der API-Aufruf fehl, entfallen die drei Backend-Teile, die Angular-Version bleibt stehen (keine Fehlermeldung). (Hintergrund: Anhang A.1)

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] Dem Benutzer wird nach dem Login die neue Startseite angezeigt.
* [ ] Die Navigation ist bereits aufgeklappt.
* [ ] Unten auf der Startseite wird die höchste Flyway-Schema-Version angezeigt (Format `V<nn>`).
* [ ] Neben der Version wird das Build-Datum des Backends angezeigt (`dd.MM.yyyy HH:mm`), sofern das Jar über das Spring-Boot-Plugin gebaut wurde.
* [x] Die Zeile zeigt zusätzlich die Spring-Boot-Version des Backends (`Spring Boot <x.y.z>`, aus `SpringBootVersion.getVersion()`).
      (Test: VersionServiceTest#getSpringBootVersion_ReturnsLaufendeVersion; startseite.component.spec.ts › StartseiteComponent › should show the Spring Boot version delivered by the backend)
* [x] Die Zeile zeigt zusätzlich die Angular-Version des ausgelieferten Frontends (`Angular <x.y.z>`, aus `VERSION.full`).
      (Test: startseite.component.spec.ts › StartseiteComponent › should show the Angular version of the delivered frontend)
* [x] Die vorhandenen Teile stehen in der Reihenfolge Datenbank-Version, Build, Spring Boot, Angular, getrennt mit ` · `.
      (Test: startseite.component.spec.ts › StartseiteComponent › should show all four parts in order, separated by a middle dot)
* [x] `GET /api/version` ist für jeden authentifizierten Benutzer (jede Rolle) aufrufbar und liefert `{ schemaVersion, buildTime, springBootVersion }`.
      (Test: ControllerAuthorizationTest#getVersion_authenticatedWithoutPermission_ok; ControllerAuthorizationTest#getVersion_withoutAuthentication_unauthorized; VersionControllerTest#getVersion_AlleWerte_ReturnsSchemaBuildUndSpringBoot)
* [x] Schlägt der Versions-Aufruf fehl oder fehlen Werte, zeigt die Startseite keine Fehlermeldung – nur die fehlenden Teile entfallen; die Angular-Version steht immer da.
      (Test: startseite.component.spec.ts › StartseiteComponent › should omit missing parts without a stray separator; startseite.component.spec.ts › StartseiteComponent › should keep only the Angular version when the version call fails, without an error message; VersionControllerTest#getVersion_FehlendeWerte_AlsNull; VersionServiceTest#getSchemaVersion_DatenbankWirft_ReturnsNull; VersionServiceTest#getBuildTime_OhneBuildInfo_ReturnsNull)
* [ ] Die Labels stammen aus dem `TranslationService` (DE/EN).


## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* -

### NFR-2: Sicherheit
* Die Startseite ist mit jeder Rolle sichtbar
* `GET /api/version` erfordert Authentifizierung, aber keine Permission (rollenunabhängig wie die Startseite selbst); es werden keine sensitiven Daten geliefert (nur Schema-Version, Build-Zeitpunkt und Spring-Boot-Version)

### NFR-3: Kompatibilität
* -


## 5. Edge Cases & Fehlerbehandlung
| Szenario | Verhalten |
|----------|-----------|
| `flyway_schema_history` leer oder nicht lesbar | `schemaVersion = null` → Versions-Teil entfällt (Log-Warnung im Backend) |
| Jar ohne `build-info` gebaut (z.B. IDE-Start) | `buildTime = null` → Build-Teil entfällt |
| `GET /api/version` schlägt fehl | Backend-Teile entfallen, nur die Angular-Version steht; keine Fehlermeldung (rein informativ) |

## 6. Abgrenzung / Out of Scope
* -

## 7. Offene Fragen
* -

## Anhang A – Entscheidungen und Verlauf

### A.1 Spring-Boot- und Angular-Version (FR-4, 26.10.2026)
FR-4.3 lautete: „Anzeige-Format: dezente Zeile am unteren Seitenrand, `Datenbank-Version: V<nn> ·
Build: <Datum>`“, FR-4.4: „Beide Angaben sind rein informativ: Ist eine davon nicht ermittelbar
(`null`), entfällt nur der jeweilige Teil; schlägt der API-Aufruf fehl, wird nichts angezeigt“. Das
Kriterium nannte als Antwort `{ schemaVersion, buildTime }` und „die Zeile entfällt (ganz oder
teilweise)“. Auf Wunsch des Users zeigt die Zeile zusätzlich die Spring-Boot- und die
Angular-Version. Weil die Angular-Version im Browser feststeht, entfällt die Zeile nicht mehr ganz.

