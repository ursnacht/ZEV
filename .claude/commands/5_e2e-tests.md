# Erstelle E2E-Tests

Erstelle End-to-End Tests mit Playwright für komplette User Flows.

## Input
* Ziel: $ARGUMENTS (z.B. `Tarifverwaltung` oder `Specs/Tarifverwaltung_Umsetzungsplan.md`)

---

## Unabhängige Ausführung

Dieser Skill arbeitet UNABHÄNGIG vom Kontext der aktuellen Session.

**Analysiere NUR:**
1. Die Spec-Datei (falls angegeben)
2. Die tatsächlich implementierten Komponenten und Routes
3. Bestehende E2E-Tests als Vorlage

**IGNORIERE** jeglichen Kontext aus der vorherigen Konversation.

---

## Vorgehen

### Phase 1: Unabhängige Code-Analyse
1. Lies die Spec-Datei (falls vorhanden) - extrahiere User Stories und Akzeptanzkriterien
2. Analysiere die Routing-Konfiguration: `frontend-service/src/app/app.routes.ts`
3. Finde relevante Komponenten mit Glob/Grep:
   - `frontend-service/src/app/components/**/*.html`
4. Identifiziere testbare User Flows:
   - Navigation (Menü, Links)
   - Formulare (Eingabe, Validierung, Submit)
   - CRUD-Operationen (Liste, Erstellen, Bearbeiten, Löschen)
   - Fehlermeldungen und Feedback

### Phase 2: Test-Gap-Analyse
1. Prüfe existierende E2E-Tests in `frontend-service/tests/`
2. Vergleiche mit Spec-Anforderungen und UI-Flows
3. Liste fehlende Test-Szenarien auf

### Phase 3: Test-Erstellung
1. Erstelle Tests für fehlende Szenarien
2. Führe Tests aus: `npm run e2e:ci` oder `npx playwright test tests/xxx.spec.ts`
3. Behebe Fehler bis Tests grün sind

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

---

## Playwright Report anzeigen
`npx.cmd playwright show-report "C:\\data\\git\\ZEV\\frontend-service\\playwright-report"`
