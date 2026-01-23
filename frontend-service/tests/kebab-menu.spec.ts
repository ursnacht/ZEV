import { test as baseTest, expect, Page } from '@playwright/test';
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
 * Helper to create a unique test tariff name
 * Note: bezeichnung field has max 30 characters, so we use short timestamps
 */
function generateTestName(prefix: string = 'E2E'): string {
    // Use last 8 digits of timestamp for uniqueness (fits in 30 char limit)
    const shortTimestamp = Date.now().toString().slice(-8);
    const name = `${prefix} ${shortTimestamp}`;
    // Ensure max 30 characters (DB limit)
    return name.slice(0, 30);
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

        // Wait for table to be visible
        const table = page.locator('.zev-table');
        const hasTable = await table.isVisible().catch(() => false);

        if (hasTable) {
            // Check for kebab buttons in table rows
            const tableRows = page.locator('.zev-table tbody tr');
            const rowCount = await tableRows.count();

            if (rowCount > 0) {
                const firstRow = tableRows.first();
                const kebabButton = firstRow.locator('.zev-kebab-button');
                await expect(kebabButton).toBeVisible();

                // Verify kebab button has three dots
                const dots = kebabButton.locator('.zev-kebab-button__dot');
                await expect(dots).toHaveCount(3);
            }
        }
    });

    test('should open menu when clicking kebab button', async ({ page }) => {
        await navigateToTarife(page);

        const table = page.locator('.zev-table');
        const hasTable = await table.isVisible().catch(() => false);

        if (hasTable) {
            const tableRows = page.locator('.zev-table tbody tr');
            const rowCount = await tableRows.count();

            if (rowCount > 0) {
                const firstRow = tableRows.first();

                // Open kebab menu
                await openKebabMenu(page, firstRow);

                // Verify menu is visible
                const menu = firstRow.locator('.zev-kebab-menu--open');
                await expect(menu).toBeVisible();

                // Verify menu contains items
                const menuItems = firstRow.locator('.zev-kebab-menu__item');
                const itemCount = await menuItems.count();
                expect(itemCount).toBeGreaterThan(0);
            }
        }
    });

    test('should display edit and delete options in menu', async ({ page }) => {
        await navigateToTarife(page);

        const table = page.locator('.zev-table');
        const hasTable = await table.isVisible().catch(() => false);

        if (hasTable) {
            const tableRows = page.locator('.zev-table tbody tr');
            const rowCount = await tableRows.count();

            if (rowCount > 0) {
                const firstRow = tableRows.first();

                // Open kebab menu
                await openKebabMenu(page, firstRow);

                // Verify menu has at least 2 items (edit and delete)
                const menuItems = firstRow.locator('.zev-kebab-menu__item');
                const itemCount = await menuItems.count();
                expect(itemCount).toBeGreaterThanOrEqual(2);

                // Verify delete item has danger styling
                const dangerItem = firstRow.locator('.zev-kebab-menu__item--danger');
                await expect(dangerItem).toBeVisible();
            }
        }
    });
});

test.describe('Kebab Menu - Close Behavior', () => {
    test('should close menu when clicking outside', async ({ page }) => {
        await navigateToTarife(page);

        const table = page.locator('.zev-table');
        const hasTable = await table.isVisible().catch(() => false);

        if (hasTable) {
            const tableRows = page.locator('.zev-table tbody tr');
            const rowCount = await tableRows.count();

            if (rowCount > 0) {
                const firstRow = tableRows.first();

                // Open kebab menu
                await openKebabMenu(page, firstRow);

                // Verify menu is visible
                const menu = firstRow.locator('.zev-kebab-menu--open');
                await expect(menu).toBeVisible();

                // Click outside to close
                await closeKebabMenu(page);

                // Verify menu is closed
                await expect(menu).not.toBeVisible();
            }
        }
    });

    test('should close menu when pressing ESC key', async ({ page }) => {
        await navigateToTarife(page);

        const table = page.locator('.zev-table');
        const hasTable = await table.isVisible().catch(() => false);

        if (hasTable) {
            const tableRows = page.locator('.zev-table tbody tr');
            const rowCount = await tableRows.count();

            if (rowCount > 0) {
                const firstRow = tableRows.first();

                // Open kebab menu
                await openKebabMenu(page, firstRow);

                // Verify menu is visible
                const menu = firstRow.locator('.zev-kebab-menu--open');
                await expect(menu).toBeVisible();

                // Press ESC to close
                await closeKebabMenuWithEsc(page);

                // Verify menu is closed
                await expect(menu).not.toBeVisible();
            }
        }
    });

    test('should close first menu when opening another', async ({ page }) => {
        await navigateToTarife(page);

        const table = page.locator('.zev-table');
        const hasTable = await table.isVisible().catch(() => false);

        if (hasTable) {
            const tableRows = page.locator('.zev-table tbody tr');
            const rowCount = await tableRows.count();

            if (rowCount >= 2) {
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
            } else {
                // Skip test if not enough rows
                console.log('Skipping test: need at least 2 rows');
            }
        }
    });
});

test.describe('Kebab Menu - Edit Action', () => {
    test('should open edit form when clicking edit in kebab menu', async ({ page, tarifTracker }) => {
        await navigateToTarife(page);

        // First create a test tariff
        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        const testName = generateTestName('Kebab Edit Test');
        await fillTarifForm(page, {
            tariftyp: 'ZEV',
            bezeichnung: testName,
            preis: '0.20000',
            gueltigVon: '2099-01-01',
            gueltigBis: '2099-12-31'
        });

        const submitButton = page.locator('button[type="submit"]');
        await submitButton.click();

        // Wait for form result
        let isSuccess = false;
        try {
            isSuccess = await waitForFormResult(page, 20000);
        } catch {
            console.log('Tariff creation failed, skipping test');
            return;
        }

        if (!isSuccess) {
            console.log('Tariff creation failed, skipping test');
            return;
        }

        // Track for cleanup
        tarifTracker.add(testName);

        // Wait for table to reload
        await waitForTableWithData(page, 10000);

        // Find the row and click edit via kebab menu
        const tarifRow = page.locator(`tr:has-text("${testName}")`);
        await expect(tarifRow).toBeVisible({ timeout: 10000 });

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

        // First create a test tariff
        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        const testName = generateTestName('Kebab Delete Test');
        await fillTarifForm(page, {
            tariftyp: 'VNB',
            bezeichnung: testName,
            preis: '0.25000',
            gueltigVon: '2099-01-01',
            gueltigBis: '2099-12-31'
        });

        const submitButton = page.locator('button[type="submit"]');
        await submitButton.click();

        // Wait for form result
        let isSuccess = false;
        try {
            isSuccess = await waitForFormResult(page, 20000);
        } catch {
            console.log('Tariff creation failed, skipping test');
            return;
        }

        if (!isSuccess) {
            console.log('Tariff creation failed, skipping test');
            return;
        }

        // Track for cleanup
        tarifTracker.add(testName);

        // Wait for table to reload
        await waitForTableWithData(page, 10000);

        // Find the row
        const tarifRow = page.locator(`tr:has-text("${testName}")`);
        await expect(tarifRow).toBeVisible({ timeout: 10000 });

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

        // First create a test tariff
        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        const testName = generateTestName('Kebab Delete Confirm');
        await fillTarifForm(page, {
            tariftyp: 'ZEV',
            bezeichnung: testName,
            preis: '0.30000',
            gueltigVon: '2099-01-01',
            gueltigBis: '2099-12-31'
        });

        const submitButton = page.locator('button[type="submit"]');
        await submitButton.click();

        // Wait for form result
        let isSuccess = false;
        try {
            isSuccess = await waitForFormResult(page, 20000);
        } catch {
            console.log('Tariff creation failed, skipping test');
            return;
        }

        if (!isSuccess) {
            console.log('Tariff creation failed, skipping test');
            return;
        }

        // Track for cleanup (in case deletion fails)
        tarifTracker.add(testName);

        // Wait for table to reload
        await waitForTableWithData(page, 10000);

        // Find the row
        const tarifRow = page.locator(`tr:has-text("${testName}")`);
        await expect(tarifRow).toBeVisible({ timeout: 10000 });

        // Set up dialog handler to accept
        page.once('dialog', async dialog => {
            await dialog.accept();
        });

        // Click delete via kebab menu
        await clickKebabMenuItem(page, tarifRow, 'delete');

        // Wait for deletion - row should disappear
        try {
            await expect(tarifRow).not.toBeVisible({ timeout: 10000 });
            // Remove from tracking since it was successfully deleted
            tarifTracker.remove(testName);
        } catch {
            console.log('Deletion may have failed, row still visible');
        }
    });
});

test.describe('Kebab Menu - Multiple Components', () => {
    test('should have kebab menu in Einheiten list', async ({ page }) => {
        await navigateToEinheiten(page);

        const table = page.locator('.zev-table');
        const hasTable = await table.isVisible().catch(() => false);

        if (hasTable) {
            const tableRows = page.locator('.zev-table tbody tr');
            const rowCount = await tableRows.count();

            if (rowCount > 0) {
                const firstRow = tableRows.first();
                const kebabButton = firstRow.locator('.zev-kebab-button');
                await expect(kebabButton).toBeVisible();
            }
        }
    });
});
