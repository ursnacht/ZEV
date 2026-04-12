# Erstelle E2E-Tests

Erstelle End-to-End Tests mit Playwright für komplette User Flows, die aus Anforderungen generiert worden sind.

## Input

* **Feature-Name**: $ARGUMENTS (z.B. `Debitorkontrolle`) → liest `Specs/[Feature-Name].md`  
  Falls nicht angegeben: aus dem Konversations-Kontext ableiten (z.B. wenn zuvor `/0_anforderungen` oder `/1_umsetzungsplan` ausgeführt wurde); nur wenn unklar: nachfragen.

---

## Unabhängige Ausführung

Dieser Skill arbeitet UNABHÄNGIG vom Kontext der aktuellen Session und kann auch mit einem neuen Agenten ausgeführt werden.

**Analysiere NUR:**
1. Die Anforderungen in `Specs/[Feature-Name].md`
2. Die tatsächlich implementierten Komponenten und Routes
3. Bestehende E2E-Tests als Vorlage

---

## Vorgehen

### Phase 1: Unabhängige Code-Analyse
1. Lies die Anforderungen `Specs/[Feature-Name].md` - extrahiere User Stories und Akzeptanzkriterien
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
 * Helper to create a unique test item name
 */
function generateTestName(prefix: string = 'E2E Test'): string {
    return `${prefix} ${Date.now()}`;
}

/**
 * Helper to delete an item by name via the UI (kebab menu)
 */
async function deleteItemByName(page: Page, name: string): Promise<void> {
    console.log(`Cleanup: Attempting to delete "${name}"`);
    try {
        page.removeAllListeners('dialog');
        await navigateToFeature(page);
        await waitForTableWithData(page, 5000);

        const row = page.locator(`tr:has-text("${name}")`);
        if (await row.isVisible().catch(() => false)) {
            page.on('dialog', async dialog => { await dialog.accept(); });
            await row.locator('.zev-kebab-button').click();
            await page.waitForTimeout(300);
            await row.locator('.zev-kebab-menu__item--danger').click();
            await page.waitForTimeout(1000);
            page.removeAllListeners('dialog');
        } else {
            console.log(`Cleanup: "${name}" not found (may already be deleted)`);
        }
    } catch (error) {
        console.log(`Cleanup: Error deleting "${name}": ${error}`);
        page.removeAllListeners('dialog');
    }
}

// Reset tracking before each test
test.beforeEach(() => {
    createdItemNames = [];
});

// Cleanup after each test
test.afterEach(async ({ page }) => {
    for (const name of createdItemNames) {
        await deleteItemByName(page, name);
    }
    createdItemNames = [];
});

test.describe('Feature Name', () => {
    test('should navigate to feature page', async ({ page }) => {
        await navigateToFeature(page);
        await expect(page.locator('.zev-container h1')).toBeVisible();
    });

    test('should create new item successfully', async ({ page }) => {
        await navigateToFeature(page);
        const name = generateTestName();

        // Fill form fields (IDs aus dem HTML-Template lesen)
        await page.locator('#fieldName').fill(name);
        // ...

        await page.locator('button[type="submit"]').click();
        const success = await waitForFormResult(page);
        expect(success).toBe(true);

        createdItemNames.push(name); // Für Cleanup registrieren
    });

    test('should show validation error for invalid input', async ({ page }) => {
        await navigateToFeature(page);

        // Formular leer absenden
        await page.locator('button[type="submit"]').click();
        await expect(page.locator('.zev-form-error')).toBeVisible();
    });

    test('should edit existing item via kebab menu', async ({ page }) => {
        await navigateToFeature(page);

        const row = page.locator('.zev-table tbody tr').first();
        await clickKebabMenuItem(page, row, 'edit');

        // Formular erscheint mit vorausgefüllten Daten
        await expect(page.locator('form')).toBeVisible();
    });

    test('should delete item via kebab menu', async ({ page }) => {
        await navigateToFeature(page);

        const rows = page.locator('.zev-table tbody tr');
        const countBefore = await rows.count();

        page.on('dialog', async dialog => { await dialog.accept(); });
        await clickKebabMenuItem(page, rows.first(), 'delete');
        await page.waitForTimeout(1000);

        await expect(rows).toHaveCount(countBefore - 1);
    });
});
```

## Best Practices
* **Selektoren:** CSS-Klassen aus dem Design System (`.zev-table`, `.zev-kebab-button`, etc.) und Formular-IDs aus den HTML-Templates – kein `data-testid` (wird im Projekt nicht verwendet)
* **Cleanup:** Immer `afterEach` (nicht `afterAll`) + `beforeEach` zum Zurücksetzen des Tracking-Arrays
* **Erstellte Items registrieren:** Direkt nach erfolgreichem Create `createdItemNames.push(name)` aufrufen
* **Dialog-Handler:** Vor dem Auslösen registrieren, nach dem Test mit `removeAllListeners('dialog')` bereinigen
* **Warten:** `await expect()` statt feste Timeouts; `page.waitForTimeout()` nur für UI-Animationen
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
