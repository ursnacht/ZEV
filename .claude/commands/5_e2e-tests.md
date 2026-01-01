# Erstelle E2E-Tests

Erstelle End-to-End Tests mit Playwright für komplette User Flows.

## Input
* Ziel: $ARGUMENTS (z.B. `Tarifverwaltung` oder `Specs/Tarifverwaltung_Umsetzungsplan.md`)

## Vorgehen
1. **Identifiziere User Flows** - Welche Benutzer-Szenarien sollen getestet werden?
2. **Prüfe existierende Tests** - Schaue in `frontend-service/tests/` nach vorhandenen Tests
3. **Orientiere dich an bestehenden Tests** - Nutze vorhandene Specs als Vorlage für Stil und Struktur

## Testpyramide
* **E2E Tests:** 5-10% der Tests (dieser Command)
* **Unit Tests:** 70-80% der Tests (separater Command)
* E2E Tests sind am aufwändigsten - nur kritische User Flows testen

---

## Test-Anforderungen
* **Tool:** Playwright
* **Verzeichnis:** `frontend-service/tests/`
* **Namenskonvention:** `[feature].spec.ts`
* **Test-User:** `testuser` / `testpassword` (zev_admin Rolle)

## Was testen?
* Navigation und Routing
* Formular-Eingaben und Validierungen
* CRUD-Operationen (Create, Read, Update, Delete)
* Fehlerbehandlung und Benutzer-Feedback
* Berechtigungen (falls relevant)

## Best Practices
* **Selektoren:** Bevorzuge `data-testid` Attribute
* **Isolation:** Jeder Test sollte unabhängig sein
* **Aufräumen:** Testdaten nach dem Test bereinigen
* **Warten:** `await expect()` statt feste Timeouts

## Ausführung
```bash
cd frontend-service
npm run e2e:ci      # Headless (CI)
npm run e2e:ui      # Interaktiv mit UI
npx playwright test tests/tarif-verwaltung.spec.ts # Einzelne Spec-Datei
```

## Test-Daten

* **E2E Tests:** Dedizierte Testbenutzer in Keycloak
* **Test-User:** `testuser` / `testpassword` (zev_admin Rolle)
* **Isolation:** Jeder Test erstellt eigene Testdaten und räumt auf

---

## Voraussetzungen
* Backend und Frontend müssen laufen (`docker compose up`)
* Keycloak muss erreichbar sein mit Test-User
