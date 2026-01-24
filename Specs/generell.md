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
* Spring Boot mit Java

### Frontend
* Angular 19 mit TypeScript
* Verwende immer das Schweizer Datumsformat dd.MM.yyyy
* Verwende wo möglich und sinnvoll ein Feather Icon: Navigation, Seitentitel

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
