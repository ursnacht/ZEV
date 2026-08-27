import { test, expect, Locator, Page } from '@playwright/test';
import {
    clickKebabMenuItem, navigateViaMenu, raeumeMitWiederholung, waitForFormResult, waitForTableWithData
} from './helpers';

/**
 * tests / einheiten-verwaltung.spec.ts
 * E2E tests for the Einheiten (Units) Management page
 *
 * Die Tests waren durchgehend mit `try { … } catch { return; }` und `if (isSuccess) { … }`
 * abgesichert und meldeten bei jedem Fehlschlag **stumm grün**. Jetzt bestehen sie auf dem
 * Erfolg: `createEinheitOrFail` wirft mit der Server-Meldung im Text, wenn das Anlegen
 * misslingt.
 */

// Track created einheiten for cleanup
/**
 * Mehr Zeit als die 30 Sekunden der Voreinstellung: Das Aufraeumen laeuft im `afterEach` und
 * zaehlt bei Playwright zum Test-Timeout. Es muss notfalls abwarten, bis der Mieter einer parallel
 * laufenden Nebenkostenabrechnung wieder loeschbar ist.
 */
test.describe.configure({ timeout: 120000 });

let createdEinheitNames: string[] = [];

/**
 * Laufkennung je Worker und Browser-Projekt. Der Name ist auf **30 Zeichen** begrenzt und würde
 * darüber hinaus stillschweigend gekürzt — die Zeile wäre danach nicht mehr auffindbar.
 */
const RUN_ID = Date.now().toString().slice(-6);

/**
 * Helper function to navigate to Einheiten management page
 */
async function navigateToEinheiten(page: Page): Promise<void> {
    await navigateViaMenu(page, '/einheiten');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
    await waitForTableWithData(page, 10000);
}

/**
 * Eindeutiger Einheiten-Name, hart auf die 30 Zeichen des Formularfelds begrenzt.
 */
function generateTestEinheitName(suffix: string): string {
    const projekt = test.info().project.name.slice(0, 2);
    const name = `E2E-${projekt}${RUN_ID} ${suffix}`;
    if (name.length > 30) {
        throw new Error(`Einheiten-Name "${name}" ist länger als die 30 Zeichen des Formularfelds`);
    }
    return name;
}

/**
 * Helper to fill the einheit form
 */
async function fillEinheitForm(page: Page, data: {
    name: string;
    typ: 'PRODUCER' | 'CONSUMER';
    messpunkt?: string;
}): Promise<void> {
    await page.locator('#name').fill(data.name);
    // Das Feld kürzt ohne Rückmeldung - eine Kürzung soll hier scheitern, nicht später als
    // "Zeile nicht gefunden"
    await expect(page.locator('#name')).toHaveValue(data.name);
    await page.locator('#typ').selectOption(data.typ);
    if (data.messpunkt) {
        await page.locator('#messpunkt').fill(data.messpunkt);
    }
}

/**
 * Schliesst ein offenes Formular über "Abbrechen", falls eines sichtbar ist.
 */
async function closeOpenForm(page: Page): Promise<void> {
    const form = page.locator('form');
    if (await form.isVisible().catch(() => false)) {
        const cancel = form.locator('button.zev-button--secondary').first();
        if (await cancel.isVisible().catch(() => false)) {
            await cancel.click();
            await expect(form).not.toBeVisible({ timeout: 5000 });
        }
    }
}

/**
 * Wartet, bis keine Meldung mehr steht.
 *
 * Zwingend vor jedem Absenden: Erfolgsmeldungen blenden sich erst nach 5 Sekunden aus. Eine noch
 * stehende Meldung der vorherigen Aktion würde sonst als Ergebnis der nächsten gewertet — ein
 * abgewiesener Speicherversuch sähe dann wie ein erfolgreicher aus.
 */
async function clearMessages(page: Page): Promise<void> {
    const error = page.locator('.zev-message--error');
    if (await error.isVisible().catch(() => false)) {
        await error.click();
        await expect(error).not.toBeVisible({ timeout: 5000 });
    }
    const success = page.locator('.zev-message--success');
    if (await success.isVisible().catch(() => false)) {
        await expect(success).not.toBeVisible({ timeout: 10000 });
    }
}

/**
 * Sendet das offene Formular ab und besteht auf einer Erfolgsmeldung.
 *
 * Ersetzt das frühere `try { … } catch { return; }` samt `if (isSuccess)`: Ein Fehlschlag beim
 * Speichern ist ein Testergebnis, kein Grund zum stillen Aussteigen.
 */
async function submitAndExpectSuccess(page: Page, was: string): Promise<void> {
    await clearMessages(page);
    await page.locator('button[type="submit"]').click();
    const isSuccess = await waitForFormResult(page, 20000);
    if (!isSuccess) {
        const meldung = await page.locator('.zev-message--error').textContent().catch(() => '');
        throw new Error(`${was} konnte nicht gespeichert werden: ${meldung?.trim()}`);
    }
}

/**
 * Legt eine Einheit an, registriert sie fürs Aufräumen und liefert ihre Zeile.
 * Erwartet die geöffnete Einheiten-Liste. Wirft, wenn das Anlegen misslingt.
 */
async function createEinheitOrFail(page: Page, daten: {
    name: string;
    typ: 'PRODUCER' | 'CONSUMER';
    messpunkt?: string;
}): Promise<Locator> {
    await closeOpenForm(page);
    await page.locator('button.zev-button--primary').first().click();
    await expect(page.locator('form')).toBeVisible();

    await fillEinheitForm(page, daten);

    createdEinheitNames.push(daten.name);
    await submitAndExpectSuccess(page, `Einheit "${daten.name}"`);

    await waitForTableWithData(page, 10000);
    const row = page.locator(`tr:has-text("${daten.name}")`).first();
    await expect(row).toBeVisible({ timeout: 10000 });
    return row;
}

/**
 * Löscht eine Einheit über das Kebab-Menü. Liefert `true`, wenn danach keine Zeile mehr steht.
 *
 * <p>Die Existenzprüfung **wartet**: `isVisible()` fragt ohne zu warten, und die Zeile erscheint
 * erst mit der Antwort der Listenabfrage. Eine zu früh gestellte Frage meldete „nicht vorhanden"
 * und liesse den Datensatz stillschweigend in der Datenbank zurück — genau so entstanden die
 * Rückstände, die in der Nebenkosten-Suite aufgefallen sind.
 */
async function deleteEinheitByName(page: Page, name: string): Promise<boolean> {
    page.removeAllListeners('dialog');
    try {
        await navigateToEinheiten(page);
        await closeOpenForm(page);

        const einheitRow = page.locator(`tr:has-text("${name}")`);

        const vorhanden = await einheitRow.first().waitFor({ state: 'visible', timeout: 10000 })
            .then(() => true).catch(() => false);
        if (!vorhanden) {
            return true;
        }

        page.once('dialog', async dialog => { await dialog.accept(); });
        await clickKebabMenuItem(page, einheitRow.first(), 'delete');
        await expect(einheitRow).toHaveCount(0, { timeout: 10000 });
        return true;
    } catch (error) {
        console.error(`CLEANUP FEHLGESCHLAGEN: Einheit "${name}" - ${error}`);
        return false;
    } finally {
        page.removeAllListeners('dialog');
    }
}

test.beforeEach(() => {
    createdEinheitNames = [];
});

/**
 * Räumt die angelegten Einheiten ab — und **scheitert sichtbar**, wenn das nicht gelingt.
 *
 * <p>Vorher wurde jeder Fehlschlag nur auf die Konsole geschrieben: Die Suite blieb grün, während
 * die Datenbank volllief. Ein Rückstand ist ein Befund und gehört gemeldet.
 */
test.afterEach(async ({ page }) => {
    const gescheitert: string[] = [];
    // Mehrere Versuche mit Pause: Haengt an der Einheit ein Mieter, den eine parallel laufende
    // Nebenkostenabrechnung erfasst hat, ist erst der Mieter und damit auch die Einheit
    // voruebergehend gesperrt.
    for (const name of createdEinheitNames) {
        if (!await raeumeMitWiederholung(() => deleteEinheitByName(page, name))) {
            gescheitert.push(name);
        }
    }
    createdEinheitNames = [];

    expect(gescheitert,
        `Testdaten blieben in der Datenbank zurueck: ${gescheitert.join(', ')}`).toEqual([]);
});

test.describe('Einheiten Management - Navigation and Display', () => {
    test('should display the einheiten management page with table and create button', async ({ page }) => {
        await navigateToEinheiten(page);

        await expect(page.locator('.zev-container h1')).toBeVisible();
        await expect(page.locator('button.zev-button--primary')).toBeVisible();
        await expect(page.locator('.zev-table')).toBeVisible();
    });

    test('should display einheiten table with correct columns', async ({ page }) => {
        await navigateToEinheiten(page);

        await expect(page.locator('.zev-table')).toBeVisible();

        const headers = page.locator('.zev-table th');
        // Name, Typ, Messpunkt, Actions
        await expect.poll(() => headers.count()).toBeGreaterThanOrEqual(4);
    });

    test('should display einheiten data in table rows', async ({ page }) => {
        await navigateToEinheiten(page);

        const rows = page.locator('.zev-table tbody tr');
        await expect.poll(() => rows.count()).toBeGreaterThan(0);
    });
});

test.describe('Einheiten Management - Sorting', () => {
    test('should sort einheiten by clicking on column headers', async ({ page }) => {
        await navigateToEinheiten(page);

        // Sortieren laesst sich nur mit Daten pruefen - eine zu kurze Tabelle ist hier ein
        // Umgebungsfehler und soll auffallen, statt den Test stumm durchzuwinken.
        const rows = page.locator('.zev-table tbody tr');
        await expect.poll(() => rows.count(),
            { message: 'Fuer den Sortier-Test werden mindestens zwei Einheiten benoetigt' })
            .toBeGreaterThan(1);

        const nameHeader = page.locator('th').filter({ hasText: /Name/i }).first();
        await nameHeader.click();

        const sortIndicator = page.locator('.zev-table__sort-indicator');
        await expect(sortIndicator.first()).toBeVisible();

        // Erneuter Klick dreht die Richtung um
        await nameHeader.click();
        await expect(sortIndicator.first()).toBeVisible();
    });
});

test.describe('Einheiten Management - Create Einheit', () => {
    test('should show einheit form when clicking create button', async ({ page }) => {
        await navigateToEinheiten(page);

        await page.locator('button.zev-button--primary').first().click();

        await expect(page.locator('form')).toBeVisible();
        await expect(page.locator('#name')).toBeVisible();
        await expect(page.locator('#typ')).toBeVisible();
        await expect(page.locator('#messpunkt')).toBeVisible();
        await expect(page.locator('button[type="submit"]')).toBeVisible();
        await expect(page.locator('button.zev-button--secondary')).toBeVisible();
    });

    test('should create a new CONSUMER einheit successfully', async ({ page }) => {
        await navigateToEinheiten(page);

        const testName = generateTestEinheitName('Cons');
        const row = await createEinheitOrFail(page, {
            name: testName, typ: 'CONSUMER', messpunkt: `MP-C${RUN_ID}`
        });

        // Die Typ-Spalte zeigt den uebersetzten Text
        await expect(row).toContainText('Konsument');
        await expect(row).toContainText(`MP-C${RUN_ID}`);
    });

    test('should create a new PRODUCER einheit successfully', async ({ page }) => {
        await navigateToEinheiten(page);

        const testName = generateTestEinheitName('Prod');
        const row = await createEinheitOrFail(page, {
            name: testName, typ: 'PRODUCER', messpunkt: `MP-P${RUN_ID}`
        });

        await expect(row).toContainText('Produzent');
    });

    test('should cancel form and return to list', async ({ page }) => {
        await navigateToEinheiten(page);

        await page.locator('button.zev-button--primary').first().click();

        const form = page.locator('form');
        await expect(form).toBeVisible();

        await page.locator('button.zev-button--secondary').click();

        await expect(form).not.toBeVisible();
        await expect(page.locator('button.zev-button--primary').first()).toBeVisible();
    });
});

test.describe('Einheiten Management - Form Validation', () => {
    test('should disable submit when name is empty', async ({ page }) => {
        await navigateToEinheiten(page);

        await page.locator('button.zev-button--primary').first().click();

        // Leave name empty, fill other fields
        await page.locator('#name').fill('');
        await page.locator('#typ').selectOption('CONSUMER');

        const submitButton = page.locator('button[type="submit"]');
        const isDisabled = await submitButton.isDisabled();
        const errorMessage = page.locator('.zev-form-error');
        const hasError = await errorMessage.isVisible().catch(() => false);

        expect(isDisabled || hasError).toBeTruthy();
    });
});

test.describe('Einheiten Management - Edit Einheit', () => {
    test('should edit an existing einheit', async ({ page }) => {
        await navigateToEinheiten(page);

        const originalName = generateTestEinheitName('Edit');
        const row = await createEinheitOrFail(page, {
            name: originalName, typ: 'CONSUMER', messpunkt: `MP-E${RUN_ID}`
        });

        await clickKebabMenuItem(page, row, 'edit');
        await expect(page.locator('form')).toBeVisible({ timeout: 5000 });

        // Formular ist mit den Werten der Zeile vorbelegt
        await expect(page.locator('#name')).toHaveValue(originalName);
        await expect(page.locator('#messpunkt')).toHaveValue(`MP-E${RUN_ID}`);

        const neuerMesspunkt = `MP-U${RUN_ID}`;
        await page.locator('#messpunkt').fill(neuerMesspunkt);
        await submitAndExpectSuccess(page, `Einheit "${originalName}"`);

        await waitForTableWithData(page, 10000);
        const updatedRow = page.locator(`tr:has-text("${originalName}")`).first();
        await expect(updatedRow).toBeVisible({ timeout: 10000 });
        await expect(updatedRow).toContainText(neuerMesspunkt);
    });
});

test.describe('Einheiten Management - Delete Einheit', () => {
    test('should show confirmation dialog when deleting einheit', async ({ page }) => {
        await navigateToEinheiten(page);

        const testName = generateTestEinheitName('DelDlg');
        const row = await createEinheitOrFail(page, { name: testName, typ: 'CONSUMER' });

        // Dialog abweisen - die Einheit muss erhalten bleiben
        let dialogMessage = '';
        page.once('dialog', async dialog => {
            dialogMessage = dialog.message();
            await dialog.dismiss();
        });

        await clickKebabMenuItem(page, row, 'delete');
        await page.waitForTimeout(500);

        expect(dialogMessage).toBeTruthy();
        await expect(row).toBeVisible();
    });

    test('should delete einheit when confirmed', async ({ page }) => {
        await navigateToEinheiten(page);

        const testName = generateTestEinheitName('DelOk');
        const row = await createEinheitOrFail(page, { name: testName, typ: 'CONSUMER' });

        page.once('dialog', async dialog => { await dialog.accept(); });
        await clickKebabMenuItem(page, row, 'delete');

        // Kein try/catch: Bleibt die Zeile stehen, ist das Loeschen fehlgeschlagen
        await expect(page.locator(`tr:has-text("${testName}")`)).toHaveCount(0, { timeout: 10000 });
        createdEinheitNames = createdEinheitNames.filter(n => n !== testName);
    });
});
