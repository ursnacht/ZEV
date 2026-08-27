import { test, expect, Page } from '@playwright/test';
import {
    clickKebabMenuItem, loescheZeileMitText, navigateViaMenu, waitForFormResult, waitForTableWithData
} from './helpers';

/**
 * tests / tarif-verwaltung.spec.ts
 * E2E tests for the Tarif (Tariff) Management page
 *
 * **Jeder Test bekommt sein eigenes Gueltigkeitsjahr** (siehe `gueltigkeit`).
 * Vorher legten alle Tests ihre Tarife im selben Zeitraum 2099 an. Innerhalb eines Browsers
 * ging das gut (die Tests laufen nacheinander und raeumen auf), zwei parallele Browser-Projekte
 * verletzten aber die Ueberschneidungsregel je Tariftyp. Die Anlage schlug fehl, und die Tests
 * meldeten wegen `if (isSuccess) { ... }` trotzdem gruen - sichtbar nur als
 * "Tariff creation failed, skipping ..." im Log. Mit disjunkten Jahren je Test *und* je Projekt
 * kann sich kein Lauf mehr mit einem anderen ueberschneiden.
 */

// Track created tariffs for cleanup
let createdTarifNames: string[] = [];

/**
 * Basisjahr je Browser-Projekt. Die Bereiche sind disjunkt und liegen ausserhalb der echten
 * Tarife wie auch der Bereiche anderer Suites (ladestationen: 2089, ladestromtarif: 2093-2098).
 */
const PROJEKT_BASISJAHR: Record<string, number> = { chromium: 2070, firefox: 2060 };

/**
 * Gueltigkeitszeitraum fuer einen Test. `slot` ist je Test eindeutig (0-9) und ergibt zusammen
 * mit dem Basisjahr des Projekts ein Jahr, das kein anderer Test verwendet.
 */
function gueltigkeit(slot: number): { von: string; bis: string; jahr: number } {
    const basis = PROJEKT_BASISJAHR[test.info().project.name] ?? 2050;
    const jahr = basis + slot;
    return { von: `${jahr}-01-01`, bis: `${jahr}-12-31`, jahr };
}

/**
 * Helper to create a unique test tariff name.
 * IMPORTANT: bezeichnung is limited to 30 chars in DB (@Column length=30).
 */
function generateTestTarifName(prefix: string = 'E2E Test'): string {
    const name = `${prefix} ${Date.now()}`;
    if (name.length > 30) {
        throw new Error(`Tarif-Bezeichnung "${name}" ist laenger als die 30 Zeichen des Felds`);
    }
    return name;
}

/**
 * Helper function to navigate to Tarif management page
 */
async function navigateToTarife(page: Page): Promise<void> {
    await navigateViaMenu(page, '/tarife');

    // Wait for Tarife page to load - use the container's h1, not navbar
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
    // Wait for table to load
    await waitForTableWithData(page, 10000);
}

/**
 * Helper to fill the tariff form
 */
async function fillTarifForm(page: Page, data: {
    tariftyp: 'ZEV' | 'VNB' | 'GRUNDGEBUEHR';
    bezeichnung: string;
    preis: string;
    gueltigVon: string;
    gueltigBis: string;
}): Promise<void> {
    await page.locator('#tariftyp').selectOption(data.tariftyp);
    await page.locator('#bezeichnung').fill(data.bezeichnung);
    // Das Feld kuerzt ohne Rueckmeldung - eine Kuerzung soll hier scheitern, nicht spaeter
    // als "Zeile nicht gefunden"
    await expect(page.locator('#bezeichnung')).toHaveValue(data.bezeichnung);
    await page.locator('#preis').fill(data.preis);
    await page.locator('#gueltigVon').fill(data.gueltigVon);
    await page.locator('#gueltigBis').fill(data.gueltigBis);
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
 * beim Speichern ist ein Testergebnis, kein Grund zum stillen Aussteigen. Die Server-Meldung
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
 * Legt einen Tarif an, registriert ihn fuer das Aufraeumen und prueft, dass er in der Liste steht.
 * Erwartet die geoeffnete Tarif-Liste.
 */
async function createTarifOrFail(page: Page, data: {
    tariftyp: 'ZEV' | 'VNB' | 'GRUNDGEBUEHR';
    bezeichnung: string;
    preis: string;
    gueltigVon: string;
    gueltigBis: string;
}): Promise<void> {
    await page.locator('button.zev-button--primary').first().click();
    await expect(page.locator('form')).toBeVisible();

    await fillTarifForm(page, data);

    createdTarifNames.push(data.bezeichnung);
    await submitAndExpectSuccess(page, `Tarif "${data.bezeichnung}"`);

    await waitForTableWithData(page, 10000);
    await expect(page.locator(`tr:has-text("${data.bezeichnung}")`)).toBeVisible({ timeout: 10000 });
}

/**
 * Loescht einen Tarif ueber das Kebab-Menue. Liefert `true`, wenn danach keine Zeile mehr steht.
 *
 * <p>Wartet zusaetzlich auf die DELETE-Antwort: Sie ist das verlaessliche Signal dafuer, dass der
 * Server den Tarif wirklich entfernt hat - eine verschwundene Zeile allein koennte auch ein
 * Neuladen der Liste sein.
 */
async function deleteTarifByName(page: Page, name: string): Promise<boolean> {
    try {
        await navigateToTarife(page);
        await closeOpenForm(page);
        await waitForTableWithData(page, 10000);
    } catch (error) {
        console.error(`CLEANUP FEHLGESCHLAGEN: Tarifliste fuer "${name}" - ${error}`);
        return false;
    }
    return loescheZeileMitText(page, name);
}

/** Schliesst ein offenes Formular ueber "Abbrechen", falls eines sichtbar ist. */
async function closeOpenForm(page: Page): Promise<void> {
    const form = page.locator('form');
    if (await form.isVisible().catch(() => false)) {
        const cancelButton = form.locator('button.zev-button--secondary').first();
        if (await cancelButton.isVisible().catch(() => false)) {
            await cancelButton.click();
            await expect(form).not.toBeVisible({ timeout: 5000 });
        }
    }
}

/**
 * Loest eine Validierung aus und liefert das Ergebnis des Servers.
 *
 * Auf die HTTP-Antwort zu warten statt nur auf die gerenderte Meldung trennt zwei Dinge, die
 * sich im UI aehnlich sehen: das fachliche Ergebnis (`VALIDIERUNG_FEHLER` samt Luecken-Liste)
 * und einen echten Uebertragungsfehler (`FEHLER_VALIDIERUNG` aus dem error-Zweig). Ob die
 * Umgebung gerade Luecken hat, darf der Test nicht vorwegnehmen - ein fehlgeschlagener Aufruf
 * dagegen ist immer ein Testergebnis.
 */
async function runValidation(page: Page, modus: 'quartale' | 'jahre'): Promise<{ valid: boolean }> {
    const muster = modus === 'jahre' ? /jahr/i : /quartal/i;
    const [response] = await Promise.all([
        page.waitForResponse(
            r => r.url().includes('/api/tarife/validate') && r.request().method() === 'GET',
            { timeout: 20000 }),
        page.locator('.zev-button-row button.zev-button--secondary').filter({ hasText: muster }).click()
    ]);

    expect(response.status(), 'Die Validierung muss serverseitig durchlaufen').toBe(200);

    const success = page.locator('.zev-message--success');
    const error = page.locator('.zev-message--error');
    await expect(success.or(error)).toBeVisible({ timeout: 15000 });

    const valid = await success.isVisible().catch(() => false);
    if (!valid) {
        // Bleibt die Liste leer, steht dort der generische Uebertragungsfehler
        // (FEHLER_VALIDIERUNG) - und der passt nicht zu einer Antwort mit Status 200. Genau so
        // fiel der frueher als POST gefuehrte Endpunkt auf: Der ungelesene Request-Body machte
        // die Proxy-Verbindung unbrauchbar (siehe TarifController.validateTarife).
        // Die Meldung zu Luecken nennt jede Periode einzeln. Bleibt die Liste leer, steht dort
        // der generische Uebertragungsfehler (FEHLER_VALIDIERUNG) - und der passt nicht zu einer
        // Antwort mit Status 200. Der Response-Body wird bewusst nicht gelesen: Navigiert die
        // Seite zwischendurch, ist er nicht mehr abrufbar und der Test scheiterte an sich selbst.
        await expect(page.locator('.validation-errors li').first()).toBeVisible({ timeout: 5000 });
    }
    return { valid };
}

/**
 * Cleanup function to delete all created tariffs
 */
/**
 * Raeumt die angelegten Tarife ab — und **scheitert sichtbar**, wenn das nicht gelingt.
 *
 * <p>Vorher wurde jeder Fehlschlag nur auf die Konsole geschrieben: Die Suite blieb gruen, waehrend
 * die Datenbank volllief. Ein zweiter Versuch davor, weil ein einzelnes Loeschen an einer stehenden
 * Meldung oder einem offenen Formular scheitern kann.
 */
async function cleanupCreatedTariffs(page: Page): Promise<void> {
    const gescheitert: string[] = [];
    for (const name of createdTarifNames) {
        let erfolg = await deleteTarifByName(page, name);
        if (!erfolg) {
            erfolg = await deleteTarifByName(page, name);
        }
        if (!erfolg) {
            gescheitert.push(name);
        }
    }
    createdTarifNames = [];

    expect(gescheitert,
        `Testdaten blieben in der Datenbank zurueck: ${gescheitert.join(', ')}`).toEqual([]);
}

// Reset tracking before each test
test.beforeEach(() => {
    createdTarifNames = [];
});

// Cleanup after each test
test.afterEach(async ({ page }) => {
    await cleanupCreatedTariffs(page);
});

test.describe('Tarif Management - Navigation and Display', () => {
    test('should display the tariff management page with table and create button', async ({ page }) => {
        await navigateToTarife(page);

        const title = page.locator('.zev-container h1');
        await expect(title).toBeVisible();

        const createButton = page.locator('button.zev-button--primary');
        await expect(createButton).toBeVisible();

        await expect(page.locator('.zev-table')).toBeVisible();
    });

    test('should display tariff table with correct columns', async ({ page }) => {
        await navigateToTarife(page);

        await expect(page.locator('.zev-table')).toBeVisible();

        const headers = page.locator('.zev-table th');
        // Tariftyp, Bezeichnung, Preis, Gueltig von, Gueltig bis, Actions
        await expect.poll(() => headers.count()).toBeGreaterThanOrEqual(6);
    });

    test('should display tariff type badges (ZEV/VNB)', async ({ page }) => {
        await navigateToTarife(page);

        const tableRows = page.locator('.zev-table tbody tr');
        // Badges lassen sich nur mit Daten pruefen - eine leere Tabelle ist hier ein
        // Umgebungsfehler und soll auffallen, statt den Test stumm durchzuwinken.
        await expect.poll(() => tableRows.count(),
            { message: 'Fuer den Badge-Test wird mindestens ein Tarif benoetigt' })
            .toBeGreaterThan(0);

        // Jede Zeile traegt ein Badge. Erst warten, bis eines gerendert ist: Zwei blanke
        // `count()` verglichen sonst zwei Momentaufnahmen, die noch im Aufbau waren.
        const badges = page.locator('.zev-table tbody .tarif-typ-badge');
        await expect(badges.first()).toBeVisible();
        const anzahlZeilen = await tableRows.count();
        await expect.poll(() => badges.count()).toBe(anzahlZeilen);
    });
});

test.describe('Tarif Management - Sorting', () => {
    test('should sort tariffs by clicking on column headers', async ({ page }) => {
        await navigateToTarife(page);

        await expect(page.locator('.zev-table')).toBeVisible();

        const rows = page.locator('.zev-table tbody tr');
        await expect.poll(() => rows.count(),
            { message: 'Fuer den Sortier-Test werden mindestens zwei Tarife benoetigt' })
            .toBeGreaterThan(1);

        const tariftypHeader = page.locator('th').filter({ hasText: /Tariftyp/i }).first();
        await tariftypHeader.click();

        const sortIndicator = page.locator('.zev-table__sort-indicator');
        await expect(sortIndicator.first()).toBeVisible();

        // Erneuter Klick dreht die Richtung um
        await tariftypHeader.click();
        await expect(sortIndicator.first()).toBeVisible();
    });
});

test.describe('Tarif Management - Create Tariff', () => {
    test('should show tariff form when clicking create button', async ({ page }) => {
        await navigateToTarife(page);

        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        const form = page.locator('form');
        await expect(form).toBeVisible();

        await expect(page.locator('#tariftyp')).toBeVisible();
        await expect(page.locator('#bezeichnung')).toBeVisible();
        await expect(page.locator('#preis')).toBeVisible();
        await expect(page.locator('#gueltigVon')).toBeVisible();
        await expect(page.locator('#gueltigBis')).toBeVisible();

        const submitButton = page.locator('button[type="submit"]');
        const cancelButton = page.locator('button.zev-button--secondary');
        await expect(submitButton).toBeVisible();
        await expect(cancelButton).toBeVisible();
    });

    test('should create a new ZEV tariff successfully', async ({ page }) => {
        await navigateToTarife(page);

        const testName = generateTestTarifName('ZEV Test');
        const zeitraum = gueltigkeit(0);
        await createTarifOrFail(page, {
            tariftyp: 'ZEV',
            bezeichnung: testName,
            preis: '0.19500',
            gueltigVon: zeitraum.von,
            gueltigBis: zeitraum.bis
        });

        const newTarifRow = page.locator(`tr:has-text("${testName}")`).first();
        await expect(newTarifRow.locator('.tarif-typ-badge--zev')).toBeVisible();
    });

    test('should create a new VNB tariff successfully', async ({ page }) => {
        await navigateToTarife(page);

        const testName = generateTestTarifName('VNB Test');
        const zeitraum = gueltigkeit(1);
        await createTarifOrFail(page, {
            tariftyp: 'VNB',
            bezeichnung: testName,
            preis: '0.34192',
            gueltigVon: zeitraum.von,
            gueltigBis: zeitraum.bis
        });

        const newTarifRow = page.locator(`tr:has-text("${testName}")`).first();
        await expect(newTarifRow.locator('.tarif-typ-badge--vnb')).toBeVisible();
    });

    test('should cancel form and return to list', async ({ page }) => {
        await navigateToTarife(page);

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

test.describe('Tarif Management - Form Validation', () => {
    test('should show error for empty bezeichnung', async ({ page }) => {
        await navigateToTarife(page);

        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        const zeitraum = gueltigkeit(9);
        await page.locator('#tariftyp').selectOption('ZEV');
        await page.locator('#bezeichnung').fill('');
        await page.locator('#preis').fill('0.20000');
        await page.locator('#gueltigVon').fill(zeitraum.von);
        await page.locator('#gueltigBis').fill(zeitraum.bis);

        const submitButton = page.locator('button[type="submit"]');
        const isDisabled = await submitButton.isDisabled();
        const errorMessage = page.locator('.zev-form-error');
        const hasError = await errorMessage.isVisible().catch(() => false);

        expect(isDisabled || hasError).toBeTruthy();
    });

    test('should show error for invalid date range (von > bis)', async ({ page }) => {
        await navigateToTarife(page);

        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        const zeitraum = gueltigkeit(9);
        await page.locator('#tariftyp').selectOption('ZEV');
        await page.locator('#bezeichnung').fill('Test Invalid Date');
        await page.locator('#preis').fill('0.20000');
        await page.locator('#gueltigVon').fill(zeitraum.bis);
        await page.locator('#gueltigBis').fill(zeitraum.von); // Before von

        const submitButton = page.locator('button[type="submit"]');
        const isDisabled = await submitButton.isDisabled();
        const errorMessage = page.locator('.zev-form-error');
        const hasError = await errorMessage.isVisible().catch(() => false);

        expect(isDisabled || hasError).toBeTruthy();
    });

    test('should show error for negative price', async ({ page }) => {
        await navigateToTarife(page);

        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        const zeitraum = gueltigkeit(9);
        await page.locator('#tariftyp').selectOption('ZEV');
        await page.locator('#bezeichnung').fill('Test Negative Price');
        await page.locator('#preis').fill('-0.10000');
        await page.locator('#gueltigVon').fill(zeitraum.von);
        await page.locator('#gueltigBis').fill(zeitraum.bis);

        const submitButton = page.locator('button[type="submit"]');
        const isDisabled = await submitButton.isDisabled();
        const errorMessage = page.locator('.zev-form-error');
        const hasError = await errorMessage.isVisible().catch(() => false);

        expect(isDisabled || hasError).toBeTruthy();
    });
});

test.describe('Tarif Management - Edit Tariff', () => {
    test('should edit an existing tariff', async ({ page }) => {
        await navigateToTarife(page);

        const originalName = generateTestTarifName('Edit Test');
        const zeitraum = gueltigkeit(2);
        await createTarifOrFail(page, {
            tariftyp: 'ZEV',
            bezeichnung: originalName,
            preis: '0.20000',
            gueltigVon: zeitraum.von,
            gueltigBis: zeitraum.bis
        });

        const tarifRow = page.locator(`tr:has-text("${originalName}")`).first();
        await clickKebabMenuItem(page, tarifRow, 'edit');

        await expect(page.locator('form')).toBeVisible({ timeout: 5000 });
        await expect(page.locator('#bezeichnung')).toHaveValue(originalName);

        await page.locator('#preis').fill('0.21000');
        await submitAndExpectSuccess(page, `Tarif "${originalName}"`);

        await waitForTableWithData(page, 10000);
        const updatedRow = page.locator(`tr:has-text("${originalName}")`).first();
        await expect(updatedRow).toBeVisible({ timeout: 10000 });
        // Der geaenderte Preis muss in der Liste ankommen (Schweizer Format mit Punkt)
        await expect(updatedRow).toContainText('0.21');
    });
});

test.describe('Tarif Management - Delete Tariff', () => {
    test('should show confirmation dialog when deleting tariff', async ({ page }) => {
        await navigateToTarife(page);

        const testName = generateTestTarifName('Delete Test');
        const zeitraum = gueltigkeit(3);
        await createTarifOrFail(page, {
            tariftyp: 'ZEV',
            bezeichnung: testName,
            preis: '0.20000',
            gueltigVon: zeitraum.von,
            gueltigBis: zeitraum.bis
        });

        const tarifRow = page.locator(`tr:has-text("${testName}")`).first();

        // Dialog abweisen - der Tarif muss erhalten bleiben
        let dialogMessage = '';
        page.once('dialog', async dialog => {
            dialogMessage = dialog.message();
            await dialog.dismiss();
        });

        await clickKebabMenuItem(page, tarifRow, 'delete');
        await page.waitForTimeout(500);

        expect(dialogMessage).toBeTruthy();
        await expect(tarifRow).toBeVisible();
    });

    test('should delete tariff when confirmed', async ({ page }) => {
        await navigateToTarife(page);

        const testName = generateTestTarifName('Delete Confirm');
        const zeitraum = gueltigkeit(4);
        await createTarifOrFail(page, {
            tariftyp: 'VNB',
            bezeichnung: testName,
            preis: '0.35000',
            gueltigVon: zeitraum.von,
            gueltigBis: zeitraum.bis
        });

        const tarifRow = page.locator(`tr:has-text("${testName}")`).first();

        page.once('dialog', async dialog => { await dialog.accept(); });
        await clickKebabMenuItem(page, tarifRow, 'delete');

        // Kein try/catch: Bleibt die Zeile stehen, ist das Loeschen fehlgeschlagen
        await expect(tarifRow).toHaveCount(0, { timeout: 10000 });
        createdTarifNames = createdTarifNames.filter(n => n !== testName);
    });
});

test.describe('Tarif Management - Overlapping Validation', () => {
    test('should show error when creating overlapping tariff of same type', async ({ page }) => {
        await navigateToTarife(page);

        const zeitraum = gueltigkeit(5);
        const testName1 = generateTestTarifName('Overlap 1');
        await createTarifOrFail(page, {
            tariftyp: 'ZEV',
            bezeichnung: testName1,
            preis: '0.20000',
            gueltigVon: zeitraum.von,
            gueltigBis: zeitraum.bis
        });

        // Zweiter Tarif desselben Typs, der in den ersten hineinragt -> muss abgewiesen werden
        const testName2 = generateTestTarifName('Overlap 2');
        await page.locator('button.zev-button--primary').first().click();
        await expect(page.locator('form')).toBeVisible();
        await fillTarifForm(page, {
            tariftyp: 'ZEV',
            bezeichnung: testName2,
            preis: '0.21000',
            gueltigVon: `${zeitraum.jahr}-06-01`,
            gueltigBis: `${zeitraum.jahr + 1}-06-30`
        });

        await clearMessages(page);
        await page.locator('button[type="submit"]').click();
        const isSuccess = await waitForFormResult(page, 20000);
        if (isSuccess) {
            // Fuer das Aufraeumen registrieren, bevor der Test scheitert
            createdTarifNames.push(testName2);
        }
        expect(isSuccess,
            'Ein ueberschneidender Tarif desselben Typs darf nicht gespeichert werden').toBe(false);

        await expect(page.locator('.zev-message--error')).toBeVisible();
    });
});

test.describe('Tarif Management - Validation Buttons', () => {
    test('should display validation buttons', async ({ page }) => {
        await navigateToTarife(page);

        const buttons = page.locator('.zev-button-row button.zev-button--secondary');
        await expect.poll(() => buttons.count()).toBe(2);

        const buttonTexts = await buttons.allTextContents();
        expect(buttonTexts.some(text => text.toLowerCase().includes('quartal'))).toBeTruthy();
        expect(buttonTexts.some(text => text.toLowerCase().includes('jahr'))).toBeTruthy();
    });

    test('should report the quartale validation result of the server', async ({ page }) => {
        await navigateToTarife(page);
        await runValidation(page, 'quartale');
    });

    test('should report the jahre validation result of the server', async ({ page }) => {
        await navigateToTarife(page);
        await runValidation(page, 'jahre');
    });

    test('should dismiss the validation message when clicked', async ({ page }) => {
        await navigateToTarife(page);

        const ergebnis = await runValidation(page, 'jahre');
        if (ergebnis.valid) {
            // Erfolgsmeldungen verschwinden nach 5 Sekunden von selbst
            await expect(page.locator('.zev-message--success')).not.toBeVisible({ timeout: 10000 });
        } else {
            // Meldungen zu Luecken bleiben stehen, bis sie weggeklickt werden
            const errorMessage = page.locator('.zev-message--error');
            await expect(errorMessage).toBeVisible();
            await expect(page.locator('.zev-message__dismiss')).toBeVisible();
            await errorMessage.click();
            await expect(errorMessage).not.toBeVisible({ timeout: 5000 });
        }
    });
});

test.describe('Tarif Management - Grundgebühr (GRUNDGEBUEHR)', () => {
    test('should show GRUNDGEBUEHR option in tarif type dropdown', async ({ page }) => {
        await navigateToTarife(page);

        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        await expect(page.locator('form')).toBeVisible();

        const grundgebuehrOption = page.locator('#tariftyp option[value="GRUNDGEBUEHR"]');
        await expect(grundgebuehrOption).toHaveCount(1);
    });

    test('should create a GRUNDGEBUEHR tariff successfully', async ({ page }) => {
        await navigateToTarife(page);

        const testName = generateTestTarifName('GGB Test');
        const zeitraum = gueltigkeit(6);
        await createTarifOrFail(page, {
            tariftyp: 'GRUNDGEBUEHR',
            bezeichnung: testName,
            preis: '12.50000',
            gueltigVon: zeitraum.von,
            gueltigBis: zeitraum.bis
        });
    });

    test('should edit a GRUNDGEBUEHR tariff', async ({ page }) => {
        await navigateToTarife(page);

        const testName = generateTestTarifName('GGB Edit');
        const zeitraum = gueltigkeit(7);
        await createTarifOrFail(page, {
            tariftyp: 'GRUNDGEBUEHR',
            bezeichnung: testName,
            preis: '10.00000',
            gueltigVon: zeitraum.von,
            gueltigBis: zeitraum.bis
        });

        const tarifRow = page.locator(`tr:has-text("${testName}")`).first();
        await clickKebabMenuItem(page, tarifRow, 'edit');

        await expect(page.locator('form')).toBeVisible({ timeout: 5000 });
        await expect(page.locator('#bezeichnung')).toHaveValue(testName);
        await expect(page.locator('#tariftyp')).toHaveValue('GRUNDGEBUEHR');

        await page.locator('#preis').fill('15.00000');
        await submitAndExpectSuccess(page, `Tarif "${testName}"`);

        await waitForTableWithData(page, 10000);
        const updatedRow = page.locator(`tr:has-text("${testName}")`).first();
        await expect(updatedRow).toBeVisible({ timeout: 10000 });
        await expect(updatedRow).toContainText('15.00');
    });

    test('should delete a GRUNDGEBUEHR tariff', async ({ page }) => {
        await navigateToTarife(page);

        const testName = generateTestTarifName('GGB Delete');
        const zeitraum = gueltigkeit(8);
        await createTarifOrFail(page, {
            tariftyp: 'GRUNDGEBUEHR',
            bezeichnung: testName,
            preis: '8.00000',
            gueltigVon: zeitraum.von,
            gueltigBis: zeitraum.bis
        });

        const tarifRow = page.locator(`tr:has-text("${testName}")`).first();

        page.once('dialog', async dialog => { await dialog.accept(); });
        await clickKebabMenuItem(page, tarifRow, 'delete');

        await expect(tarifRow).toHaveCount(0, { timeout: 10000 });
        createdTarifNames = createdTarifNames.filter(n => n !== testName);
    });
});
