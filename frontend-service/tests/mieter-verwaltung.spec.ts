import { test, expect, Locator, Page } from '@playwright/test';
import { navigateViaMenu, clickKebabMenuItem, waitForFormResult, waitForTableWithData } from './helpers';

/**
 * tests / mieter-verwaltung.spec.ts
 * E2E tests for the Mieter (Tenant) Management page
 *
 * **Jeder Test, der einen Mieter anlegt, legt sich zuerst eine eigene Einheit an.**
 * Vorher griffen alle Tests die *erste* Einheit der Liste - also produktive Stammdaten - und
 * verwendeten denselben Mietbeginn. Zwei parallel laufende Browser-Projekte verletzten damit
 * die Regel "keine ueberlappenden Mietverhaeltnisse je Einheit", die Anlage schlug fehl, und
 * die Tests meldeten wegen `if (isSuccess) { ... }` trotzdem gruen (sichtbar nur als
 * "Mieter creation failed, skipping ..." im Log). Mit einer eigenen Einheit je Test kann sich
 * kein Lauf mehr mit einem anderen oder mit echten Daten ueberschneiden - und jede Anlage,
 * die trotzdem fehlschlaegt, laesst den Test ehrlich rot werden.
 */

// Track created objects for cleanup (Mieter zuerst - die Einheit ist sonst gesperrt)
let createdMieterNames: string[] = [];
let createdEinheitNames: string[] = [];

/**
 * Laufkennung je Worker-Prozess. Einheiten-Namen sind auf 30 Zeichen begrenzt und wuerden
 * daraeuber hinaus stillschweigend abgeschnitten - die Zeile waere anschliessend nicht mehr
 * auffindbar.
 */
const RUN_ID = Date.now().toString().slice(-6);

/** Eindeutiger Einheiten-Name, hart auf die 30 Zeichen des Formularfelds begrenzt. */
function generateTestEinheitName(suffix: string): string {
    const projekt = test.info().project.name.slice(0, 2);
    const name = `E2E-${projekt}${RUN_ID} ${suffix}`;
    if (name.length > 30) {
        throw new Error(`Einheiten-Name "${name}" ist laenger als die 30 Zeichen des Formularfelds`);
    }
    return name;
}

/**
 * Helper to create a unique test mieter name
 */
function generateTestMieterName(prefix: string = 'E2E Mieter'): string {
    return `${prefix} ${test.info().project.name} ${RUN_ID} ${Date.now()}`;
}

/**
 * Helper function to navigate to Mieter management page
 */
async function navigateToMieter(page: Page): Promise<void> {
    await navigateViaMenu(page, '/mieter');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
    await waitForTableWithData(page, 10000);
}

async function navigateToEinheiten(page: Page): Promise<void> {
    await navigateViaMenu(page, '/einheiten');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
    await waitForTableWithData(page, 10000);
}

/**
 * Wartet, bis keine Meldung mehr steht.
 *
 * Zwingend vor jedem Absenden: Erfolgsmeldungen blenden sich erst nach 5 Sekunden aus. Eine
 * noch stehende Meldung der vorherigen Aktion wuerde sonst als Ergebnis der naechsten gewertet -
 * ein abgewiesenes Speichern saehe dann wie ein erfolgreiches aus.
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
 * Ersetzt das fruehere `try { … } catch { return; }` samt `if (isSuccess)`: Ein Fehlschlag
 * beim Anlegen ist ein Testergebnis, kein Grund zum stillen Aussteigen. Die Server-Meldung
 * wandert in die Fehlermeldung, damit der Grund im Report steht.
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
 * Legt eine eigene Konsumenten-Einheit an und registriert sie fuer das Aufraeumen.
 * Erwartet die geoeffnete Einheiten-Liste, liefert den Namen der Einheit.
 */
async function createTestEinheit(page: Page, suffix: string): Promise<string> {
    const name = generateTestEinheitName(suffix);
    createdEinheitNames.push(name);

    await page.locator('button.zev-button--primary').first().click();
    await expect(page.locator('form')).toBeVisible();

    await page.locator('#name').fill(name);
    // Das Feld kuerzt ohne Rueckmeldung - eine Kuerzung soll hier scheitern, nicht spaeter
    // als "Zeile nicht gefunden"
    await expect(page.locator('#name')).toHaveValue(name);
    await page.locator('#typ').selectOption('CONSUMER');
    await page.locator('#messpunkt').fill(`MP-${RUN_ID}-${Date.now().toString().slice(-5)}`);

    await submitAndExpectSuccess(page, `Einheit "${name}"`);
    await waitForTableWithData(page, 10000);
    await expect(page.locator(`tr:has-text("${name}")`)).toBeVisible({ timeout: 10000 });
    return name;
}

/** Checkbox der Einheit im Mieter-Formular (Mehrfachauswahl seit Specs/Ladestationen.md). */
function einheitCheckbox(page: Page, einheitName: string): Locator {
    return page.locator('.zev-checkbox-item')
        .filter({ hasText: einheitName })
        .locator('input[type="checkbox"]')
        .first();
}

/**
 * Helper to fill the mieter form
 */
async function fillMieterForm(page: Page, data: {
    einheitName?: string;
    name: string;
    strasse: string;
    plz: string;
    ort: string;
    mietbeginn: string;
    mietende?: string;
}): Promise<void> {
    if (data.einheitName) {
        const checkbox = einheitCheckbox(page, data.einheitName);
        await checkbox.waitFor({ state: 'visible', timeout: 10000 });
        if (!(await checkbox.isChecked())) {
            await checkbox.check();
        }
    }
    await page.locator('#name').fill(data.name);
    await page.locator('#strasse').fill(data.strasse);
    await page.locator('#plz').fill(data.plz);
    await page.locator('#ort').fill(data.ort);
    await page.locator('#mietbeginn').fill(data.mietbeginn);
    if (data.mietende) {
        await page.locator('#mietende').fill(data.mietende);
    }
}

/**
 * Legt einen Mieter auf einer eigens erzeugten Einheit an und liefert beide Namen.
 * Fasst den Ablauf zusammen, den fast jeder Test hier als Vorbedingung braucht.
 */
async function createMieterMitEigenerEinheit(page: Page, data: {
    suffix: string;
    strasse: string;
    plz?: string;
    ort?: string;
    mietbeginn?: string;
    mietende?: string;
}): Promise<{ mieterName: string; einheitName: string }> {
    await navigateToEinheiten(page);
    const einheitName = await createTestEinheit(page, data.suffix);

    await navigateToMieter(page);
    await page.locator('button.zev-button--primary').first().click();
    await expect(page.locator('form')).toBeVisible();

    const mieterName = generateTestMieterName(data.suffix);
    await fillMieterForm(page, {
        einheitName,
        name: mieterName,
        strasse: data.strasse,
        plz: data.plz ?? '8000',
        ort: data.ort ?? 'Zürich',
        mietbeginn: data.mietbeginn ?? '2099-01-01',
        mietende: data.mietende
    });

    createdMieterNames.push(mieterName);
    await submitAndExpectSuccess(page, `Mieter "${mieterName}"`);
    await waitForTableWithData(page, 10000);
    await expect(page.locator(`tr:has-text("${mieterName}")`)).toBeVisible({ timeout: 10000 });

    return { mieterName, einheitName };
}

/**
 * Schliesst ein offenes Formular ueber "Abbrechen", falls eines sichtbar ist.
 */
async function closeOpenForm(page: Page): Promise<void> {
    const form = page.locator('form');
    if (await form.isVisible().catch(() => false)) {
        const cancelButton = form.locator('button.zev-button--secondary');
        if (await cancelButton.isVisible().catch(() => false)) {
            await cancelButton.click();
            await expect(form).not.toBeVisible({ timeout: 5000 });
        }
    }
}

/**
 * Helper to delete a mieter by name
 */
async function deleteMieterByName(page: Page, name: string): Promise<void> {
    try {
        page.removeAllListeners('dialog');
        await navigateToMieter(page);
        await closeOpenForm(page);

        const mieterRow = page.locator(`tr:has-text("${name}")`).first();
        if (!await mieterRow.isVisible().catch(() => false)) {
            return;
        }

        page.once('dialog', async dialog => { await dialog.accept(); });
        await clickKebabMenuItem(page, mieterRow, 'delete');
        await mieterRow.waitFor({ state: 'hidden', timeout: 10000 }).catch(() =>
            console.log(`Cleanup: Mieter "${name}" ist nach dem Loeschen noch sichtbar`));
    } catch (error) {
        console.log(`Cleanup: Fehler beim Loeschen des Mieters "${name}": ${error}`);
        page.removeAllListeners('dialog');
    }
}

/**
 * Helper to delete an einheit by name
 */
async function deleteEinheitByName(page: Page, name: string): Promise<void> {
    try {
        page.removeAllListeners('dialog');
        await navigateToEinheiten(page);
        await closeOpenForm(page);

        const einheitRow = page.locator(`tr:has-text("${name}")`).first();
        if (!await einheitRow.isVisible().catch(() => false)) {
            return;
        }

        page.once('dialog', async dialog => { await dialog.accept(); });
        await clickKebabMenuItem(page, einheitRow, 'delete');
        await einheitRow.waitFor({ state: 'hidden', timeout: 10000 }).catch(() =>
            console.log(`Cleanup: Einheit "${name}" ist nach dem Loeschen noch sichtbar`));
    } catch (error) {
        console.log(`Cleanup: Fehler beim Loeschen der Einheit "${name}": ${error}`);
        page.removeAllListeners('dialog');
    }
}

test.beforeEach(() => {
    createdMieterNames = [];
    createdEinheitNames = [];
});

// Reihenfolge zwingend: Eine Einheit mit zugeordnetem Mieter laesst sich nicht loeschen.
test.afterEach(async ({ page }) => {
    for (const name of createdMieterNames) {
        await deleteMieterByName(page, name);
    }
    for (const name of createdEinheitNames) {
        await deleteEinheitByName(page, name);
    }
    createdMieterNames = [];
    createdEinheitNames = [];
});

test.describe('Mieter Management - Navigation and Display', () => {
    test('should display the mieter management page with table and create button', async ({ page }) => {
        await navigateToMieter(page);

        const title = page.locator('.zev-container h1');
        await expect(title).toBeVisible();

        const createButton = page.locator('button.zev-button--primary');
        await expect(createButton).toBeVisible();

        await expect(page.locator('.zev-table')).toBeVisible();
    });

    test('should display mieter table with correct columns', async ({ page }) => {
        await navigateToMieter(page);

        await expect(page.locator('.zev-table')).toBeVisible();

        const headers = page.locator('.zev-table th');
        // Einheit, Name, Strasse, PLZ/Ort, Mietbeginn, Mietende, Actions
        expect(await headers.count()).toBeGreaterThanOrEqual(6);
    });
});

test.describe('Mieter Management - Sorting', () => {
    test('should sort mieter by clicking on column headers', async ({ page }) => {
        await navigateToMieter(page);

        const rows = page.locator('.zev-table tbody tr');
        // Sortieren laesst sich nur mit Daten pruefen - eine leere Tabelle ist hier ein
        // Umgebungsfehler und soll auffallen, statt den Test stumm durchzuwinken.
        expect(await rows.count(),
            'Fuer den Sortier-Test werden mindestens zwei Mieter benoetigt').toBeGreaterThan(1);

        const nameHeader = page.locator('th').filter({ hasText: /Name/i }).first();
        await nameHeader.click();

        const sortIndicator = page.locator('.zev-table__sort-indicator');
        await expect(sortIndicator.first()).toBeVisible();

        // Erneuter Klick dreht die Richtung um
        await nameHeader.click();
        await expect(sortIndicator.first()).toBeVisible();
    });
});

test.describe('Mieter Management - Create Mieter', () => {
    test('should show mieter form when clicking create button', async ({ page }) => {
        await navigateToMieter(page);

        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        const form = page.locator('form');
        await expect(form).toBeVisible();

        await expect(page.locator('.zev-checkbox-group input[type="checkbox"]').first()).toBeVisible();
        await expect(page.locator('#name')).toBeVisible();
        await expect(page.locator('#strasse')).toBeVisible();
        await expect(page.locator('#plz')).toBeVisible();
        await expect(page.locator('#ort')).toBeVisible();
        await expect(page.locator('#mietbeginn')).toBeVisible();
        await expect(page.locator('#mietende')).toBeVisible();

        const submitButton = page.locator('button[type="submit"]');
        const cancelButton = page.locator('button.zev-button--secondary');
        await expect(submitButton).toBeVisible();
        await expect(cancelButton).toBeVisible();
    });

    test('should create a new mieter successfully', async ({ page }) => {
        const { mieterName } = await createMieterMitEigenerEinheit(page, {
            suffix: 'Create',
            strasse: 'Teststrasse 1'
        });

        const newRow = page.locator(`tr:has-text("${mieterName}")`).first();
        await expect(newRow).toContainText('Teststrasse 1');
        await expect(newRow).toContainText('8000');
    });

    test('should create a mieter with mietende', async ({ page }) => {
        const { mieterName } = await createMieterMitEigenerEinheit(page, {
            suffix: 'WithEnd',
            strasse: 'Endstrasse 2',
            plz: '3000',
            ort: 'Bern',
            mietende: '2099-12-31'
        });

        // Zeigt das Mietende, nicht "AKTUELL"
        await expect(page.locator(`tr:has-text("${mieterName}")`).first()).toContainText('31.12.2099');
    });

    test('should cancel form and return to list', async ({ page }) => {
        await navigateToMieter(page);

        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        const form = page.locator('form');
        await expect(form).toBeVisible();

        const cancelButton = page.locator('button.zev-button--secondary');
        await cancelButton.click();

        await expect(form).not.toBeVisible();
        const createButtonAgain = page.locator('button.zev-button--primary').first();
        await expect(createButtonAgain).toBeVisible();
    });
});

test.describe('Mieter Management - Form Validation', () => {
    test('should disable submit when required fields are empty', async ({ page }) => {
        await navigateToMieter(page);

        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        // Leave all fields empty
        await page.locator('#name').fill('');

        const submitButton = page.locator('button[type="submit"]');
        const isDisabled = await submitButton.isDisabled();
        const errorMessage = page.locator('.zev-form-error');
        const hasError = await errorMessage.isVisible().catch(() => false);

        expect(isDisabled || hasError).toBeTruthy();
    });

    test('should show error for invalid date range (mietende before mietbeginn)', async ({ page }) => {
        await navigateToEinheiten(page);
        const einheitName = await createTestEinheit(page, 'Validate');

        await navigateToMieter(page);
        await page.locator('button.zev-button--primary').first().click();
        await expect(page.locator('form')).toBeVisible();

        await fillMieterForm(page, {
            einheitName,
            name: generateTestMieterName('Validation'),
            strasse: 'Str. 1',
            plz: '8000',
            ort: 'Zürich',
            mietbeginn: '2099-12-31',
            mietende: '2099-01-01' // Before mietbeginn
        });

        const submitButton = page.locator('button[type="submit"]');
        const isDisabled = await submitButton.isDisabled();
        const errorMessage = page.locator('.zev-form-error');
        const hasError = await errorMessage.isVisible().catch(() => false);

        expect(isDisabled || hasError).toBeTruthy();
    });
});

test.describe('Mieter Management - Edit Mieter', () => {
    test('should edit an existing mieter', async ({ page }) => {
        const { mieterName } = await createMieterMitEigenerEinheit(page, {
            suffix: 'Edit',
            strasse: 'Editstr. 1'
        });

        const mieterRow = page.locator(`tr:has-text("${mieterName}")`).first();
        await clickKebabMenuItem(page, mieterRow, 'edit');

        await expect(page.locator('form')).toBeVisible({ timeout: 5000 });
        await expect(page.locator('#name')).toHaveValue(mieterName);

        await page.locator('#strasse').fill('Editstr. 99 Updated');
        await submitAndExpectSuccess(page, `Mieter "${mieterName}"`);

        await waitForTableWithData(page, 10000);
        const updatedRow = page.locator(`tr:has-text("${mieterName}")`).first();
        await expect(updatedRow).toBeVisible({ timeout: 10000 });
        await expect(updatedRow).toContainText('Editstr. 99 Updated');
    });
});

test.describe('Mieter Management - Copy Mieter', () => {
    test('should copy an existing mieter without id', async ({ page }) => {
        const { mieterName } = await createMieterMitEigenerEinheit(page, {
            suffix: 'Copy',
            strasse: 'Copystr. 1',
            // Die Kopie liegt auf derselben Einheit - ohne Mietende wuerden sich die beiden
            // Mietverhaeltnisse ueberschneiden und das Speichern der Kopie abgewiesen.
            mietende: '2099-06-30'
        });

        const mieterRow = page.locator(`tr:has-text("${mieterName}")`).first();

        // Kopieren ist der zweite Eintrag im Kebab-Menue (nach Bearbeiten, vor Loeschen)
        await mieterRow.locator('.zev-kebab-button').click();
        await mieterRow.locator('.zev-kebab-menu--open').waitFor({ state: 'visible', timeout: 2000 });
        const copyItem = mieterRow.locator('.zev-kebab-menu__item:not(.zev-kebab-menu__item--danger)').nth(1);
        await copyItem.click();

        // Formular erscheint mit den Werten des Originals
        await expect(page.locator('form')).toBeVisible({ timeout: 5000 });
        await expect(page.locator('#name')).toHaveValue(mieterName);
        await expect(page.locator('#strasse')).toHaveValue('Copystr. 1');

        // Name und Zeitraum der Kopie anpassen (anschliessender, ueberschneidungsfreier Zeitraum)
        const copyName = generateTestMieterName('Copied');
        await page.locator('#name').fill(copyName);
        await page.locator('#mietbeginn').fill('2099-07-01');
        await page.locator('#mietende').fill('2099-12-31');

        createdMieterNames.push(copyName);
        await submitAndExpectSuccess(page, `Kopie "${copyName}"`);

        await waitForTableWithData(page, 10000);
        await expect(page.locator(`tr:has-text("${mieterName}")`).first()).toBeVisible({ timeout: 10000 });
        await expect(page.locator(`tr:has-text("${copyName}")`).first()).toBeVisible({ timeout: 10000 });
    });
});

test.describe('Mieter Management - Delete Mieter', () => {
    test('should show confirmation dialog when deleting mieter', async ({ page }) => {
        const { mieterName } = await createMieterMitEigenerEinheit(page, {
            suffix: 'DelDlg',
            strasse: 'Deletestr. 1'
        });

        const mieterRow = page.locator(`tr:has-text("${mieterName}")`).first();

        // Dialog abweisen - der Mieter muss erhalten bleiben
        let dialogMessage = '';
        page.once('dialog', async dialog => {
            dialogMessage = dialog.message();
            await dialog.dismiss();
        });

        await clickKebabMenuItem(page, mieterRow, 'delete');
        await page.waitForTimeout(500);

        expect(dialogMessage).toBeTruthy();
        await expect(mieterRow).toBeVisible();
    });

    test('should delete mieter when confirmed', async ({ page }) => {
        const { mieterName } = await createMieterMitEigenerEinheit(page, {
            suffix: 'DelOk',
            strasse: 'Deletestr. 2'
        });

        const mieterRow = page.locator(`tr:has-text("${mieterName}")`).first();

        page.once('dialog', async dialog => { await dialog.accept(); });
        await clickKebabMenuItem(page, mieterRow, 'delete');

        // Kein try/catch: Bleibt die Zeile stehen, ist das Loeschen fehlgeschlagen
        await expect(mieterRow).toHaveCount(0, { timeout: 10000 });
        createdMieterNames = createdMieterNames.filter(n => n !== mieterName);
    });
});
