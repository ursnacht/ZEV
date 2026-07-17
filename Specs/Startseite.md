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
3. Anzeige-Format: dezente Zeile am unteren Seitenrand, `Datenbank-Version: V<nn> · Build: <Datum>`; Labels über Translation-Keys `DB_SCHEMA_VERSION` (V82) und `BUILD_DATUM` (V83).
4. Beide Angaben sind rein informativ: Ist eine davon nicht ermittelbar (`null`), entfällt nur der jeweilige Teil; schlägt der API-Aufruf fehl, wird nichts angezeigt (keine Fehlermeldung).

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] Dem Benutzer wird nach dem Login die neue Startseite angezeigt.
* [ ] Die Navigation ist bereits aufgeklappt.
* [ ] Unten auf der Startseite wird die höchste Flyway-Schema-Version angezeigt (Format `V<nn>`).
* [ ] Neben der Version wird das Build-Datum des Backends angezeigt (`dd.MM.yyyy HH:mm`), sofern das Jar über das Spring-Boot-Plugin gebaut wurde.
* [ ] `GET /api/version` ist für jeden authentifizierten Benutzer (jede Rolle) aufrufbar und liefert `{ schemaVersion, buildTime }`.
* [ ] Schlägt der Versions-Aufruf fehl oder fehlen die Werte, zeigt die Startseite keine Fehlermeldung – die Zeile entfällt (ganz oder teilweise).
* [ ] Die Labels stammen aus dem `TranslationService` (DE/EN).


## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* -

### NFR-2: Sicherheit
* Die Startseite ist mit jeder Rolle sichtbar
* `GET /api/version` erfordert Authentifizierung, aber keine Permission (rollenunabhängig wie die Startseite selbst); es werden keine sensitiven Daten geliefert (nur Schema-Version und Build-Zeitpunkt)

### NFR-3: Kompatibilität
* -


## 5. Edge Cases & Fehlerbehandlung
| Szenario | Verhalten |
|----------|-----------|
| `flyway_schema_history` leer oder nicht lesbar | `schemaVersion = null` → Versions-Teil entfällt (Log-Warnung im Backend) |
| Jar ohne `build-info` gebaut (z.B. IDE-Start) | `buildTime = null` → Build-Teil entfällt |
| `GET /api/version` schlägt fehl | keine Anzeige, keine Fehlermeldung (rein informativ) |

## 6. Abgrenzung / Out of Scope
* -

## 7. Offene Fragen
* -