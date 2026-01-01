# Generelle Anforderungen

## 1. Ziel & Kontext
* **Was soll erreicht werden:** Hier sind generelle Anforderungen aufgeführt, die bei jeder Umsetzung einer Anforderung
  berücksichtigt werden sollen.
* **Warum machen wir das:** Die Anwendung wird so einheitlich erstellt und bekommt ein einheitliches Look & Feel.

## 2. Funktionale Anforderungen (Functional Requirements)
* Die funktionalen Anforderungen werden in User Stories aufgeführt.
* **Mehrsprachigkeit:**
    * Verwende den TranslationService für alle UI-Texte
    * Füge neue Text-Keys in die Datenbank ein mit "ON CONFLICT (key) DO NOTHING"
* **Fehleranzeige im Frontend:**
    * Zeige Fehlermeldungen als Toast-Nachrichten an
    * Erfolgreiche Aktionen mit kurzer Bestätigung quittieren

## 3. Technische Spezifikationen (Technical Specs)

### Backend
* Spring Boot mit Java
* Flyway für Datenbankmigration (Naming: `V[nummer]__[beschreibung].sql`)
* REST-API Konventionen:
    * Endpoints: `/api/[ressource]` (Plural, kebab-case)
    * HTTP-Methoden: GET (lesen), POST (erstellen), PUT (aktualisieren), DELETE (löschen)

### Frontend
* Angular 19 mit TypeScript
* Komponenten im Verzeichnis `frontend-service/src/app/`

### Styles / Design System
* Verwende bestehende Styles aus dem Maven Module `design-system`
* Neue Styles ins Design System einfügen
* Design System Showcase bei neuen Komponenten ergänzen

### Git-Konventionen
* Commit-Messages: Kurz und prägnant, beschreiben was geändert wurde
* Keine Secrets oder Credentials committen

## 4. Nicht-funktionale Anforderungen

### Authentifizierung & Autorisierung
* Alle Benutzer sind via Keycloak authentifiziert
* Rollen beachten:
    * `zev` - Standardbenutzer (Lesezugriff)
    * `zev_admin` - Administrator (Schreibzugriff)
* Backend: `@PreAuthorize` Annotationen verwenden
* Frontend: `AuthGuard` für geschützte Routen

### Logging
* INFO: Wichtige Geschäftsvorgänge (Upload, Berechnung)
* WARN: Unerwartete aber behandelbare Situationen
* ERROR: Fehler mit Stacktrace

### Exception Handling
* Backend: Exceptions abfangen, ins Log schreiben, sinnvolle HTTP-Response an Frontend
* Frontend: Fehler abfangen und dem Benutzer anzeigen

### Validierung
* Backend: Eingaben validieren bevor sie verarbeitet werden
* Frontend: Formularvalidierung mit sofortigem Feedback

## 5. Testing
* Ergänze Tests wo nötig (Unit Tests, Integration Tests, End-to-End Tests)
* Test-Pyramide beachten: Mehr Unit Tests, weniger E2E Tests

## 6. Edge Cases & Fehlerbehandlung (Generelle Patterns)
* Leere Listen: Hilfreiche Meldung anzeigen ("Keine Daten vorhanden")
* Netzwerkfehler: Retry-Möglichkeit oder klare Fehlermeldung
* Ungültige IDs: 404 zurückgeben, nicht 500

## 7. Zusätzliche Infos
* Zugriff auf die Datenbank via docker container
  * Beispiel um alle Übersetzungskeys zu lesen: "docker exec postgres psql -U postgres -d zev -c "SELECT key FROM zev.translation ORDER BY key;"
