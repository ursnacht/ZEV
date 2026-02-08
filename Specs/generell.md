# Generelle Anforderungen

## 1. Ziel & Kontext
* **Was soll erreicht werden:** Hier sind generelle Anforderungen aufgeführt, die bei jeder Umsetzung einer Anforderung
    berücksichtigt werden sollen.
* **Warum machen wir das:**
  * Deterministische Umsetzung wird so erreicht.
  * Die Anwendung wird einheitlich erstellt und bekommt ein einheitliches Look & Feel.

## 2. Funktionale Anforderungen (Functional Requirements)
* Die funktionalen Anforderungen werden in User Stories aufgeführt.

## 3. Technische Spezifikationen (Technical Specs)

### Backend
* Spring Boot 4 mit Java 21

### Frontend
* Angular 21 mit TypeScript
* Verwende immer das Schweizer Datumsformat dd.MM.yyyy
* Verwende wo möglich und sinnvoll ein Feather Icon: Navigation, Seitentitel

### Design System
Das Design System (`/design-system`) enthält alle wiederverwendbaren UI-Komponenten und Styles.

**Wichtig:** Vor dem Erstellen neuer Styles immer prüfen, ob bereits eine passende Klasse existiert!

| Kategorie | Verfügbare Klassen |
|-----------|-------------------|
| Container | `.zev-container`, `.zev-card`, `.zev-panel` |
| Formulare | `.zev-form-container`, `.zev-form-group`, `.zev-form-row`, `.zev-form-actions`, `.zev-form-section`, `.zev-date-range-row` |
| Inputs | `.zev-input`, `.zev-select`, `.zev-form-error`, `.zev-form-hint` |
| Buttons | `.zev-button`, `.zev-button--primary`, `.zev-button--secondary`, `.zev-button--danger`, `.zev-button--compact` |
| Tabellen | `.zev-table`, `.zev-table__header--sortable`, `.zev-table--compact` |
| Messages | `.zev-message`, `.zev-message--success`, `.zev-message--error`, `.zev-message--dismissible` |
| Navigation | `.zev-navbar`, `.zev-navbar__link`, `.zev-navbar__link--active`, `.zev-hamburger` |
| Kebab-Menü | `.zev-kebab-container`, `.zev-kebab-button`, `.zev-kebab-menu`, `.zev-kebab-menu__item`, `.zev-kebab-menu__item--danger` |
| Spinner | `.zev-spinner` |
| Status | `.zev-status` |
| Drop-Zone | `.zev-drop-zone` |
| Collapsible | `.zev-collapsible` |
| Quarter-Selector | `.zev-quarter-selector` |
| Checkbox | `.zev-checkbox` |
| Icon | `.zev-icon` |
| Typographie | `.zev-text--primary`, `.zev-text--secondary`, `.zev-text--danger`, `.zev-text--muted` |
| Statistik | `.zev-info-row`, `.zev-bar-container`, `.zev-empty-state` |

**Regeln:**
* Komponenten-CSS-Dateien minimal halten (nur komponentenspezifische Styles)
* Neue wiederverwendbare Styles in `design-system/src/components/` hinzufügen
* Nach Änderungen am Design System: `cd design-system && npm run build`

### Multi-Tenancy
* Jede Entity muss eine `org_id` (UUID) Spalte haben
* Hibernate-Filter (`@Filter`, `@FilterDef`) für automatische Mandantentrennung verwenden
* `OrganizationContextService` liefert die aktuelle `org_id` aus dem Keycloak-Token
* Flyway-Migrationen: Neue Tabellen immer mit `org_id` Spalte anlegen

### Mehrsprachigkeit (i18n)
* Alle UI-Texte via `TranslationService` / `TranslatePipe` - keine hartcodierten Texte
* Neue Übersetzungen als Flyway-Migration mit `ON CONFLICT (key) DO NOTHING`
* Übersetzungen in Deutsch und Englisch

### Fehleranzeige
* Frontend: Fehlermeldungen als `.zev-message--error` anzeigen
* Frontend: Erfolgreiche Aktionen mit `.zev-message--success` quittieren
* Backend: Sinnvolle HTTP-Status-Codes und Fehlermeldungen zurückgeben

### Git-Konventionen
* Commit-Messages: Kurz und prägnant, beschreiben was geändert wurde
* Keine Secrets oder Credentials committen

### Code-Vorlagen
Bei der Code-Generierung die Vorlagen-Tabelle in `CLAUDE.md` (Abschnitt "Code-Vorlagen für deterministische Generierung") beachten und deren Struktur exakt übernehmen.

## 4. Nicht-funktionale Anforderungen

### Authentifizierung & Autorisierung
* Alle Benutzer sind via Keycloak authentifiziert
* Rollen beachten:
    * `zev` - Standardbenutzer (Lesezugriff)
    * `zev_admin` - Administrator (Schreibzugriff)
* Backend: `@PreAuthorize` Annotationen verwenden
* Frontend: `AuthGuard` für geschützte Routen

### Logging
* INFO: Wichtige Geschäftsvorgänge (z.B. Upload, Berechnung)
* WARN: Unerwartete aber behandelbare Situationen
* ERROR: Fehler mit Stacktrace

### Exception Handling
* Backend: Exceptions abfangen, ins Log schreiben, sinnvolle HTTP-Response an Frontend
* Frontend: Fehler abfangen und dem Benutzer anzeigen

### Validierung
* Backend: Eingaben validieren bevor sie verarbeitet werden
* Frontend: Formularvalidierung mit sofortigem Feedback

## 5. Edge Cases & Fehlerbehandlung (Generelle Patterns)
* Leere Listen: Hilfreiche Meldung anzeigen ("Keine Daten vorhanden")
* Netzwerkfehler: Retry-Möglichkeit oder klare Fehlermeldung
* Ungültige IDs: 404 zurückgeben, nicht 500

## 6. Zusätzliche Infos
* Zugriff auf die Datenbank
  * via MCP-Server 'zev-db'
  * via docker container
    * Beispiel um alle Übersetzungskeys zu lesen: "docker exec postgres psql -U postgres -d zev -c "SELECT key FROM zev.translation ORDER BY key;"
