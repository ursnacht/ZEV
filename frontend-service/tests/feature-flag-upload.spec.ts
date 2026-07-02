import { test, expect, Page } from '@playwright/test';
import { navigateViaMenu, openHamburgerMenu, waitForFormResult } from './helpers';

/**
 * tests / feature-flag-upload.spec.ts
 * E2E tests for the "Feature-Flag-Upload" feature (MESSWERTE_UPLOAD flag).
 *
 * Das Flag MESSWERTE_UPLOAD (globaler Default: AKTIV) steuert die Sichtbarkeit des
 * Navigationseintrags "Messdatenupload" und die Erreichbarkeit der Route /upload.
 * Verwaltet wird es im Abschnitt "Feature-Flags" auf der Seite /einstellungen (nur zev_admin).
 *
 * Die Navigation (`*appFeature`) ist reaktiv: ein Umschalten in den Einstellungen aktualisiert
 * die Navbar in derselben SPA-Sitzung (kein Reload nötig), da `setFlag`/`resetFlag` die
 * effektiven Flags neu laden. Die Tests prüfen den Menüeintrag daher reaktiv auf derselben Seite.
 *
 * WICHTIG (Reihenfolge-Robustheit): Alle Tests teilen sich denselben Mandanten (testuser).
 * Da der globale Default AKTIV ist, stellt beforeEach/afterEach den Default-Zustand wieder her
 * (Überschreibung entfernen), damit andere Specs nicht durch ein deaktiviertes Upload-Flag brechen.
 */

// Diese Tests schalten EIN gemeinsames, mandantenweites Flag (MESSWERTE_UPLOAD) um. Da alle
// Tests denselben Keycloak-Mandanten (testuser) teilen, dürfen sie sich nicht gegenseitig
// überlagern. Daher:
//  - serial: die Tests dieser Datei laufen nacheinander auf EINEM Worker (kein paralleles Umschalten).
//  - single browser: die Flag-Logik ist browser-agnostisch; ein zweiter Browser (Firefox) würde nur
//    denselben Mandantenzustand konkurrierend verändern. Wir testen daher nur in Chromium.
test.describe.configure({ mode: 'serial' });

test.beforeEach(({ browserName }) => {
    test.skip(browserName !== 'chromium',
        'MESSWERTE_UPLOAD ist ein mandantenweites Flag; nur in einem Browser testen, um Zustands-Races zu vermeiden.');
});

const UPLOAD_FLAG_KEY = 'MESSWERTE_UPLOAD';
const UPLOAD_CHECKBOX = `#flag-${UPLOAD_FLAG_KEY}`;
const UPLOAD_MENU_LINK = '.zev-navbar__menu a[href="/upload"]';
const FLAG_ENDPOINT = `/api/feature-flags/${UPLOAD_FLAG_KEY}`;
const EFFECTIVE_FLAGS_GET = '/api/feature-flags';

/**
 * Navigate to the Einstellungen page and wait for the Feature-Flags table
 * (identified by the MESSWERTE_UPLOAD checkbox) to be rendered.
 */
async function navigateToEinstellungen(page: Page): Promise<void> {
    await navigateViaMenu(page, '/einstellungen');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
    await page.locator(UPLOAD_CHECKBOX).waitFor({ state: 'visible', timeout: 15000 });
}

/** Locator for the MESSWERTE_UPLOAD table row within the Feature-Flags section. */
function uploadRow(page: Page): ReturnType<Page['locator']> {
    return page.locator(`tr:has(${UPLOAD_CHECKBOX})`);
}

/** Reset button (only present when a tenant-specific override exists). */
function resetButton(page: Page): ReturnType<Page['locator']> {
    return uploadRow(page).locator('button.zev-button--secondary');
}

/**
 * Toggle the upload flag via the checkbox and wait for the PUT request, the subsequent
 * effective-flags reload GET (so the reactive navbar signal is up to date) and the success
 * message. Returns true if a toggle was performed.
 */
async function setUploadFlag(page: Page, enabled: boolean): Promise<boolean> {
    const checkbox = page.locator(UPLOAD_CHECKBOX);
    if ((await checkbox.isChecked()) === enabled) {
        return false; // already in desired state, no PUT would fire
    }
    const putResponse = page.waitForResponse(
        r => r.url().includes(FLAG_ENDPOINT) && r.request().method() === 'PUT', { timeout: 15000 });
    const reloadGet = page.waitForResponse(
        r => r.url().endsWith(EFFECTIVE_FLAGS_GET) && r.request().method() === 'GET', { timeout: 15000 });
    await checkbox.click();
    await putResponse;
    await reloadGet.catch(() => console.log('setUploadFlag: no reload GET detected'));
    return waitForFormResult(page);
}

/**
 * Remove the tenant-specific override via the reset button and wait for the DELETE request,
 * the reload GET and the success message.
 */
async function resetUploadFlag(page: Page): Promise<void> {
    const btn = resetButton(page);
    if (!(await btn.isVisible().catch(() => false))) {
        return; // no override present
    }
    const deleteResponse = page.waitForResponse(
        r => r.url().includes(FLAG_ENDPOINT) && r.request().method() === 'DELETE', { timeout: 15000 });
    const reloadGet = page.waitForResponse(
        r => r.url().endsWith(EFFECTIVE_FLAGS_GET) && r.request().method() === 'GET', { timeout: 15000 });
    await btn.click();
    await deleteResponse;
    await reloadGet.catch(() => console.log('resetUploadFlag: no reload GET detected'));
    await waitForFormResult(page).catch(() => false);
}

/**
 * Defensive state reset: ensure the MESSWERTE_UPLOAD override is removed so the flag falls
 * back to its global default (AKTIV). Runs before and after each test so every test (and retry)
 * starts from a deterministic, default-active state.
 */
async function ensureUploadFlagDefault(page: Page): Promise<void> {
    try {
        await navigateToEinstellungen(page);
        await resetUploadFlag(page);
        await expect(page.locator(UPLOAD_CHECKBOX)).toBeChecked({ timeout: 10000 }).catch(() => {});
    } catch (error) {
        console.log(`Cleanup: ensureUploadFlagDefault failed: ${error}`);
    }
}

test.beforeEach(async ({ page }) => {
    await ensureUploadFlagDefault(page);
});

test.afterEach(async ({ page }) => {
    await ensureUploadFlagDefault(page);
});

test.describe('Feature-Flag Upload - Verwaltung in Einstellungen', () => {
    test('should show the Feature-Flags section with an active upload toggle', async ({ page }) => {
        await navigateToEinstellungen(page);

        await expect(uploadRow(page)).toBeVisible();
        await expect(page.locator(UPLOAD_CHECKBOX)).toBeVisible();
        await expect(page.locator(UPLOAD_CHECKBOX)).toBeChecked();
        // Status wird als "Aktiv" (zev-status--success) angezeigt
        await expect(uploadRow(page).locator('.zev-status--success')).toBeVisible();
    });
});

test.describe('Feature-Flag Upload - Deaktivieren', () => {
    test('should deactivate upload flag and hide the navigation menu entry', async ({ page }) => {
        await navigateToEinstellungen(page);
        await expect(page.locator(UPLOAD_CHECKBOX)).toBeChecked();

        // Flag deaktivieren -> Erfolgsmeldung; nach Reload-GET ist die Navbar reaktiv aktualisiert
        expect(await setUploadFlag(page, false)).toBe(true);
        await expect(page.locator('.zev-message--success')).toBeVisible();
        await expect(page.locator(UPLOAD_CHECKBOX)).not.toBeChecked({ timeout: 10000 });
        await expect(resetButton(page)).toBeVisible();

        // Menüeintrag zu /upload wird bei inaktivem Flag reaktiv aus dem DOM entfernt (*appFeature)
        await openHamburgerMenu(page);
        await expect(page.locator(UPLOAD_MENU_LINK)).toHaveCount(0, { timeout: 15000 });
    });
});

test.describe('Feature-Flag Upload - Route-Guard', () => {
    test('should redirect /upload to /startseite when flag is deactivated', async ({ page }) => {
        test.setTimeout(90000); // direkter goto kann einen Keycloak-Silent-Auth-Roundtrip auslösen

        await navigateToEinstellungen(page);
        await setUploadFlag(page, false);
        await expect(page.locator(UPLOAD_CHECKBOX)).not.toBeChecked({ timeout: 10000 });

        // Direkter Aufruf von /upload -> FeatureFlagGuard lädt die Flags und leitet auf /startseite um
        await page.goto('/upload');
        await page.waitForURL('**/startseite', { timeout: 30000 });
        expect(page.url()).toContain('/startseite');
        expect(page.url()).not.toContain('/upload');
        await expect(page.locator('.zev-drop-zone')).toHaveCount(0);
    });
});

test.describe('Feature-Flag Upload - Reaktivieren', () => {
    test('should re-enable upload via reset button and make route reachable again', async ({ page }) => {
        test.setTimeout(90000);

        // Zunächst deaktivieren, damit eine Überschreibung existiert
        await navigateToEinstellungen(page);
        await setUploadFlag(page, false);
        await expect(page.locator(UPLOAD_CHECKBOX)).not.toBeChecked({ timeout: 10000 });

        // Über den "Zurücksetzen"-Button (Default = aktiv) wieder aktivieren
        await resetUploadFlag(page);
        await expect(page.locator(UPLOAD_CHECKBOX)).toBeChecked({ timeout: 10000 });

        // Menüeintrag ist reaktiv wieder sichtbar
        await openHamburgerMenu(page);
        await expect(page.locator(UPLOAD_MENU_LINK)).toBeVisible({ timeout: 15000 });

        // Route /upload ist wieder erreichbar (kein Redirect)
        await page.goto('/upload');
        await page.waitForURL('**/upload', { timeout: 30000 });
        expect(page.url()).toContain('/upload');
        await expect(page.locator('.zev-drop-zone')).toBeVisible({ timeout: 10000 });
    });
});
