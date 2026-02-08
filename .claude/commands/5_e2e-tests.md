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
4. Lies die bestehenden Helpers: `frontend-service/tests/helpers.ts`
5. Identifiziere testbare User Flows:
   - Navigation (Menü, Links)
   - Formulare (Eingabe, Validierung, Submit)
   - CRUD-Operationen (Liste, Erstellen, Bearbeiten, Löschen)
   - Fehlermeldungen und Feedback

### Phase 2: Test-Gap-Analyse
1. Prüfe existierende E2E-Tests in `frontend-service/tests/`
2. Vergleiche mit Spec-Anforderungen und UI-Flows
3. Liste fehlende Test-Szenarien auf

### Phase 3: Test-Erstellung
1. Erstelle Tests für fehlende Szenarien (Vorlage unten beachten)
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

## Shared Helpers (`tests/helpers.ts`)

Die folgenden Helper-Funktionen stehen in `frontend-service/tests/helpers.ts` zur Verfügung:

| Helper | Beschreibung |
|--------|-------------|
| `handleKeycloakLogin(page)` | Login via Keycloak (Username/Password) |
| `navigateToHome(page)` | Navigiert zur Startseite und handled Login |
| `openHamburgerMenu(page)` | Öffnet das Hamburger-Menü |
| `navigateViaMenu(page, href)` | Navigiert zu einer Seite via Menü (inkl. Login) |
| `clickKebabMenuItem(page, row, action)` | Klickt Kebab-Menü-Aktion ('edit' oder 'delete') |
| `openKebabMenu(page, row)` | Öffnet Kebab-Menü einer Zeile |
| `closeKebabMenu(page)` | Schliesst Kebab-Menü (Klick aussen) |
| `closeKebabMenuWithEsc(page)` | Schliesst Kebab-Menü (ESC-Taste) |
| `waitForFormResult(page, timeout?)` | Wartet auf Erfolgs-/Fehlermeldung |
| `waitForTableWithData(page, timeout?)` | Wartet bis Tabelle mit Daten geladen ist |

## Test-Datei Vorlage

```typescript
import { test, expect, Page } from '@playwright/test';
import { navigateViaMenu, clickKebabMenuItem, waitForFormResult, waitForTableWithData } from './helpers';

/**
 * tests / [feature].spec.ts
 * E2E tests for [Feature Name]
 */

// Track created items for cleanup
let createdItemNames: string[] = [];

/**
 * Helper function to navigate to feature page
 */
async function navigateToFeature(page: Page): Promise<void> {
    await navigateViaMenu(page, '/feature-route');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
    await waitForTableWithData(page, 10000);
}

/**
 * Helper to create a unique test name
 */
function generateTestName(prefix: string = 'E2E Test'): string {
    return `${prefix} ${Date.now()}`;
}

test.describe('Feature Name', () => {
    test.afterAll(async ({ browser }) => {
        // Cleanup: Delete test data created during tests
    });

    test('should navigate to feature page', async ({ page }) => {
        await navigateToFeature(page);
        await expect(page.locator('.zev-container h1')).toBeVisible();
    });

    test('should create new item successfully', async ({ page }) => {
        await navigateToFeature(page);

        // Fill form
        // ...

        // Submit and verify
        const success = await waitForFormResult(page);
        expect(success).toBe(true);
    });

    test('should show validation error for invalid input', async ({ page }) => {
        await navigateToFeature(page);

        // Submit empty form
        // ...

        // Verify validation
        await expect(page.locator('.zev-form-error')).toBeVisible();
    });

    test('should edit existing item via kebab menu', async ({ page }) => {
        await navigateToFeature(page);

        const row = page.locator('.zev-table tbody tr').first();
        await clickKebabMenuItem(page, row, 'edit');

        // Verify edit form appears
        // ...
    });

    test('should delete item via kebab menu', async ({ page }) => {
        await navigateToFeature(page);

        const row = page.locator('.zev-table tbody tr').first();
        await clickKebabMenuItem(page, row, 'delete');

        // Confirm deletion
        // ...
    });
});
```

## Best Practices
* **Selektoren:** Bevorzuge `data-testid` Attribute, dann CSS-Klassen
* **Isolation:** Jeder Test sollte unabhängig sein
* **Aufräumen:** Testdaten nach dem Test bereinigen
* **Warten:** `await expect()` statt feste Timeouts
* **Helper nutzen:** Immer die Helpers aus `tests/helpers.ts` verwenden

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
