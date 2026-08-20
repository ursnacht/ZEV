import { test as baseTest, expect, Locator, Page } from '@playwright/test';
import { navigateViaMenu, openKebabMenu, closeKebabMenu, closeKebabMenuWithEsc, clickKebabMenuItem, waitForFormResult, waitForTableWithData } from './helpers';

/**
 * tests / kebab-menu.spec.ts
 * E2E tests for the Kebab Menu component
 * Tests the dropdown menu functionality in list components
 */

// Custom fixture to track tariff names per test (isolated, no shared state)
type TarifTracker = {
    names: string[];
    add: (name: string) => void;
    remove: (name: string) => void;
};

const test = baseTest.extend<{ tarifTracker: TarifTracker }>({
    tarifTracker: async ({ page }, use) => {
        // Create isolated tracker for this test
        const tracker: TarifTracker = {
            names: [],
            add: (name: string) => {
                console.log(`Tracker: Added "${name}"`);
                tracker.names.push(name);
            },
            remove: (name: string) => {
                tracker.names = tracker.names.filter(n => n !== name);
            }
        };

        // Run the test
        await use(tracker);

        // Cleanup: delete all tracked tariffs
        if (tracker.names.length === 0) {
            console.log('Cleanup: No test tariffs to delete');
            return;
        }

        try {
            page.removeAllListeners('dialog');
            await navigateToTarife(page);

            const form = page.locator('form');
            if (await form.isVisible().catch(() => false)) {
                const cancelButton = page.locator('button.zev-button--secondary');
                if (await cancelButton.isVisible().catch(() => false)) {
                    await cancelButton.click();
                    await page.waitForTimeout(500);
                }
            }

            await waitForTableWithData(page, 5000);

            for (const tarifName of [...tracker.names]) {
                console.log(`Cleanup: Deleting tariff "${tarifName}"`);

                const tarifRow = page.locator(`tr:has-text("${tarifName}")`);
                if (await tarifRow.isVisible().catch(() => false)) {
                    page.once('dialog', async dialog => {
                        await dialog.accept();
                    });

                    const kebabButton = tarifRow.locator('.zev-kebab-button');
                    await kebabButton.click();
                    await page.waitForTimeout(300);

                    const deleteItem = tarifRow.locator('.zev-kebab-menu__item--danger');
                    await deleteItem.click();

                    await page.waitForTimeout(1500);
                    console.log(`Cleanup: Deleted tariff "${tarifName}"`);
                } else {
                    console.log(`Cleanup: Tariff "${tarifName}" not found (already deleted)`);
                }
            }
            console.log('Cleanup: All tracked test tariffs processed');
        } catch (error) {
            console.log(`Cleanup error: ${error}`);
            page.removeAllListeners('dialog');
        }
    }
});

/**
 * Helper function to navigate to Tarif management page
 */
async function navigateToTarife(page: Page): Promise<void> {
    await navigateViaMenu(page, '/tarife');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
    // Wait for table to load
    await waitForTableWithData(page, 10000);
}

/**
 * Helper function to navigate to Einheiten management page
 */
async function navigateToEinheiten(page: Page): Promise<void> {
    await navigateViaMenu(page, '/einheiten');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
}

/**
 * Helper to create a unique test tariff name.
 *
 * Die Bezeichnung ist auf 30 Zeichen begrenzt. Frueher wurde stillschweigend mit `.slice(0, 30)`
 * gekuerzt - ein zu langer Praefix machte die Zeile damit unauffindbar, ohne dass es auffiel.
 * Jetzt wirft der Helfer stattdessen.
 */
function generateTestName(prefix: string): string {
    const name = `${prefix} ${RUN_ID}`;
    if (name.length > 30) {
        throw new Error(`Tarif-Bezeichnung "${name}" ist laenger als die 30 Zeichen des Felds`);
    }
    return name;
}

/** Laufkennung je Worker-Prozess. */
const RUN_ID = Date.now().toString().slice(-6);

/**
 * Basisjahr je Browser-Projekt. Zusammen mit dem Versatz je Test ergibt sich fuer jeden
 * angelegten Tarif ein eigenes Gueltigkeitsjahr.
 *
 * Ohne das legten zwei Tests denselben ZEV-Tarif fuer 2099 an - die Ueberschneidungspruefung
 * wies den zweiten ab, und beide Tests stiegen wegen `if (!isSuccess) return;` still aus.
 * Die Bereiche liegen ausserhalb der uebrigen Suites (tarif-verwaltung 2060-2079,
 * tarifpositionen 2085, ladestationen 2089, ladestromtarif 2093-2098).
 */
const PROJEKT_BASISJAHR: Record<string, number> = { chromium: 2050, firefox: 2055 };

function gueltigkeit(versatz: number): { von: string; bis: string } {
    const jahr = (PROJEKT_BASISJAHR[test.info().project.name] ?? 2040) + versatz;
    return { von: `${jahr}-01-01`, bis: `${jahr}-12-31` };
}

/**
 * Erste Tabellenzeile - mit Zusicherung statt `if (rowCount > 0)`.
 *
 * Die Listen dieses Mandanten sind nie leer; eine leere Tabelle waere ein Umgebungsfehler und
 * soll auffallen, statt den Test ohne jede Pruefung durchlaufen zu lassen.
 */
async function ersteZeileOrFail(page: Page): Promise<Locator> {
    await expect(page.locator('.zev-table')).toBeVisible();
    const zeilen = page.locator('.zev-table tbody tr');
    expect(await zeilen.count(),
        'Fuer diesen Test wird mindestens eine Tabellenzeile benoetigt').toBeGreaterThan(0);
    return zeilen.first();
}

/**
 * Wartet, bis keine Meldung mehr steht. Erfolgsmeldungen blenden sich erst nach 5 Sekunden aus
 * und wuerden sonst als Ergebnis der naechsten Aktion gewertet.
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
 * Legt einen Tarif an und besteht auf dem Erfolg. Ersetzt das fruehere
 * `try { … } catch { return; }` samt `if (!isSuccess) return;`.
 */
async function createTarifOrFail(page: Page, tracker: TarifTracker, daten: {
    tariftyp: 'ZEV' | 'VNB';
    bezeichnung: string;
    preis: string;
    versatz: number;
}): Promise<Locator> {
    await page.locator('button.zev-button--primary').first().click();
    await expect(page.locator('form')).toBeVisible();

    const z = gueltigkeit(daten.versatz);
    await fillTarifForm(page, {
        tariftyp: daten.tariftyp,
        bezeichnung: daten.bezeichnung,
        preis: daten.preis,
        gueltigVon: z.von,
        gueltigBis: z.bis
    });

    tracker.add(daten.bezeichnung);
    await clearMessages(page);
    await page.locator('button[type="submit"]').click();
    if (!await waitForFormResult(page, 20000)) {
        const meldung = await page.locator('.zev-message--error').textContent().catch(() => '');
        throw new Error(`Tarif "${daten.bezeichnung}" konnte nicht angelegt werden: ${meldung?.trim()}`);
    }

    await waitForTableWithData(page, 10000);
    const row = page.locator(`tr:has-text("${daten.bezeichnung}")`).first();
    await expect(row).toBeVisible({ timeout: 10000 });
    return row;
}

/**
 * Helper to fill the tariff form
 */
async function fillTarifForm(page: Page, data: {
    tariftyp: 'ZEV' | 'VNB';
    bezeichnung: string;
    preis: string;
    gueltigVon: string;
    gueltigBis: string;
}): Promise<void> {
    await page.locator('#tariftyp').selectOption(data.tariftyp);
    await page.locator('#bezeichnung').fill(data.bezeichnung);
    await page.locator('#preis').fill(data.preis);
    await page.locator('#gueltigVon').fill(data.gueltigVon);
    await page.locator('#gueltigBis').fill(data.gueltigBis);
}

test.describe('Kebab Menu - Basic Functionality', () => {
    test('should display kebab menu button in table rows', async ({ page }) => {
        await navigateToTarife(page);

        const firstRow = await ersteZeileOrFail(page);
        const kebabButton = firstRow.locator('.zev-kebab-button');
        await expect(kebabButton).toBeVisible();

        // Verify kebab button has three dots
        await expect(kebabButton.locator('.zev-kebab-button__dot')).toHaveCount(3);
    });

    test('should open menu when clicking kebab button', async ({ page }) => {
        await navigateToTarife(page);

        const firstRow = await ersteZeileOrFail(page);
        await openKebabMenu(page, firstRow);

        await expect(firstRow.locator('.zev-kebab-menu--open')).toBeVisible();
        expect(await firstRow.locator('.zev-kebab-menu__item').count()).toBeGreaterThan(0);
    });

    test('should display edit and delete options in menu', async ({ page }) => {
        await navigateToTarife(page);

        const firstRow = await ersteZeileOrFail(page);
        await openKebabMenu(page, firstRow);

        // Mindestens Bearbeiten und Loeschen
        expect(await firstRow.locator('.zev-kebab-menu__item').count()).toBeGreaterThanOrEqual(2);
        await expect(firstRow.locator('.zev-kebab-menu__item--danger')).toBeVisible();
    });
});

test.describe('Kebab Menu - Close Behavior', () => {
    test('should close menu when clicking outside', async ({ page }) => {
        await navigateToTarife(page);

        const firstRow = await ersteZeileOrFail(page);
        await openKebabMenu(page, firstRow);

        const menu = firstRow.locator('.zev-kebab-menu--open');
        await expect(menu).toBeVisible();

        await closeKebabMenu(page);
        await expect(menu).not.toBeVisible();
    });

    test('should close menu when pressing ESC key', async ({ page }) => {
        await navigateToTarife(page);

        const firstRow = await ersteZeileOrFail(page);
        await openKebabMenu(page, firstRow);

        const menu = firstRow.locator('.zev-kebab-menu--open');
        await expect(menu).toBeVisible();

        await closeKebabMenuWithEsc(page);
        await expect(menu).not.toBeVisible();
    });

    test('should close first menu when opening another', async ({ page }) => {
        await navigateToTarife(page);

        await expect(page.locator('.zev-table')).toBeVisible();
        const tableRows = page.locator('.zev-table tbody tr');
        expect(await tableRows.count(),
            'Fuer diesen Test werden mindestens zwei Tabellenzeilen benoetigt').toBeGreaterThanOrEqual(2);

        const firstRow = tableRows.nth(0);
        const secondRow = tableRows.nth(1);

        // Open first kebab menu
        const firstKebabButton = firstRow.locator('.zev-kebab-button');
        await firstKebabButton.click();
        const firstMenu = firstRow.locator('.zev-kebab-menu--open');
        await expect(firstMenu).toBeVisible();

        // Close first menu with ESC to avoid overlay blocking second button
        await page.keyboard.press('Escape');
        await expect(firstMenu).not.toBeVisible();

        // Open second kebab menu
        const secondKebabButton = secondRow.locator('.zev-kebab-button');
        await secondKebabButton.click();
        const secondMenu = secondRow.locator('.zev-kebab-menu--open');
        await expect(secondMenu).toBeVisible();

        // Re-open first menu to verify only one can be open (use force since menus may overlap)
        await firstKebabButton.click({ force: true });
        await page.waitForTimeout(200);

        // First menu should now be open
        await expect(firstMenu).toBeVisible();

        // Second menu should now be closed
        await expect(secondMenu).not.toBeVisible();
    });
});

test.describe('Kebab Menu - Edit Action', () => {
    test('should open edit form when clicking edit in kebab menu', async ({ page, tarifTracker }) => {
        await navigateToTarife(page);

        const testName = generateTestName('Kebab Edit');
        const tarifRow = await createTarifOrFail(page, tarifTracker, {
            tariftyp: 'ZEV', bezeichnung: testName, preis: '0.20000', versatz: 0
        });

        await clickKebabMenuItem(page, tarifRow, 'edit');

        // Form should now be visible
        await expect(page.locator('form')).toBeVisible({ timeout: 5000 });

        // Verify form is populated with existing values
        const bezeichnungInput = page.locator('#bezeichnung');
        await expect(bezeichnungInput).toHaveValue(testName);

        // Cancel form
        const cancelButton = page.locator('button.zev-button--secondary');
        await cancelButton.click();
    });
});

test.describe('Kebab Menu - Delete Action', () => {
    test('should show confirmation dialog when clicking delete in kebab menu', async ({ page, tarifTracker }) => {
        await navigateToTarife(page);

        const testName = generateTestName('Kebab DelDlg');
        const tarifRow = await createTarifOrFail(page, tarifTracker, {
            tariftyp: 'VNB', bezeichnung: testName, preis: '0.25000', versatz: 1
        });

        // Set up dialog handler to capture and dismiss
        let dialogMessage = '';
        page.once('dialog', async dialog => {
            dialogMessage = dialog.message();
            await dialog.dismiss();
        });

        // Click delete via kebab menu
        await clickKebabMenuItem(page, tarifRow, 'delete');

        await page.waitForTimeout(500);

        // Verify dialog was shown
        expect(dialogMessage).toBeTruthy();

        // Tariff should still exist (we dismissed the dialog)
        await expect(tarifRow).toBeVisible();

        // Cleanup will happen in afterEach
    });

    test('should delete item when confirming in kebab menu', async ({ page, tarifTracker }) => {
        await navigateToTarife(page);

        const testName = generateTestName('Kebab DelOk');
        const tarifRow = await createTarifOrFail(page, tarifTracker, {
            tariftyp: 'ZEV', bezeichnung: testName, preis: '0.30000', versatz: 2
        });

        // Set up dialog handler to accept
        page.once('dialog', async dialog => {
            await dialog.accept();
        });

        // Click delete via kebab menu
        await clickKebabMenuItem(page, tarifRow, 'delete');

        // Kein try/catch: Bleibt die Zeile stehen, ist das Loeschen fehlgeschlagen
        await expect(page.locator(`tr:has-text("${testName}")`)).toHaveCount(0, { timeout: 10000 });
        tarifTracker.remove(testName);
    });
});

test.describe('Kebab Menu - Multiple Components', () => {
    test('should have kebab menu in Einheiten list', async ({ page }) => {
        await navigateToEinheiten(page);

        const firstRow = await ersteZeileOrFail(page);
        await expect(firstRow.locator('.zev-kebab-button')).toBeVisible();
    });
});
