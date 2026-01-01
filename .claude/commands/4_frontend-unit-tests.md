# Erstelle Frontend-Unit-Tests

Erstelle Unit Tests für die angegebene Angular-Komponente oder Service.

## Input
* Ziel-Datei: $ARGUMENTS (z.B. `einheit-form.component.ts` oder `einheit.service.ts`)

## Vorgehen
1. **Analysiere die Ziel-Datei** - Verstehe die Geschäftslogik, Inputs, Outputs und Abhängigkeiten
2. **Prüfe existierende Tests** - Schaue ob bereits eine `*.spec.ts` existiert und was fehlt
3. **Orientiere dich an bestehenden Tests** - Nutze vorhandene Specs als Vorlage für Stil und Struktur

## Test-Anforderungen
* **Tool:** Jasmine mit Karma
* **Namenskonvention:** `*.spec.ts` (gleicher Ordner wie Quell-Datei)
* Mocke alle externen Abhängigkeiten (Services, HTTP-Calls)
* Teste: Initialisierung, Inputs/Outputs, public Methoden, Edge Cases, Fehlerbehandlung
* Keine E2E-Aspekte (DOM-Interaktion, Routing) - nur isolierte Logik

## Ausführung
* Führe `npm test` im `frontend-service` Verzeichnis aus
* Alle Tests müssen grün sein

## Referenz
* Specs/AutomatisierteTests.md, Kapitel "3. Frontend Tests"

## Hinweise für Tester
### Unit Tests interaktiv ausführen

`cd frontend-service`

* Alle Tests einmalig (headless)
  * `npm.cmd test -- --browsers=ChromeHeadless --watch=false`
* Tests mit Live-Browser (interaktiv)
  * `npm.cmd test`
* Tests im Watch-Mode (re-run bei Änderungen)
  * `npm.cmd test -- --watch=true`
* Einzelne Test-Datei
  * `npm.cmd test -- --include=**/tarif.service.spec.tsv`
* Mit Code-Coverage Report
  * `npm.cmd test -- --code-coverage --browsers=ChromeHeadless --watch=false`

### npm test ausführen
`cd frontend-service`
`npm.cmd test`

#### Was passiert?
1. Chrome öffnet sich - Ein Browser-Fenster erscheint mit der Karma-Testseite
2. Tests laufen automatisch - Ergebnisse werden im Browser und Terminal angezeigt
3. Watch-Mode aktiv - Bei Dateiänderungen laufen Tests automatisch neu

#### Browser-Ansicht
Die Karma-Seite zeigt:
* Grüne Punkte = erfolgreiche Tests
* Rote Punkte = fehlgeschlagene Tests
* Klick auf einen Test zeigt Details

#### Beenden
Drücke Ctrl+C im Terminal, um Karma zu stoppen.

#### Ohne Browser-Fenster (headless)
`npm.cmd test -- --browsers=ChromeHeadless --watch=false`

Dies ist nützlich für CI/CD oder wenn du kein Browser-Fenster möchtest.