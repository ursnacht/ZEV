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
