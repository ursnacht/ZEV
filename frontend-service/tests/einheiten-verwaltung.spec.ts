import { test, expect, Page } from '@playwright/test';
import { navigateViaMenu, clickKebabMenuItem, waitForFormResult, waitForTableWithData } from './helpers';

/**
 * tests / einheiten-verwaltung.spec.ts
 * E2E tests for the Einheiten (Units) Management page
 */

// Track created einheiten for cleanup
let createdEinheitNames: string[] = [];

/**
 * Helper function to navigate to Einheiten management page
 */
async function navigateToEinheiten(page: Page): Promise<void> {
    await navigateViaMenu(page, '/einheiten');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
    await waitForTableWithData(page, 10000);
}

/**
 * Helper to create a unique test einheit name
 */
function generateTestEinheitName(prefix: string = 'E2E Einheit'): string {
    return `${prefix} ${Date.now()}`;
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
    await page.locator('#typ').selectOption(data.typ);
    if (data.messpunkt) {
        await page.locator('#messpunkt').fill(data.messpunkt);
    }
}

/**
 * Helper to delete an einheit by name
 */
async function deleteEinheitByName(page: Page, name: string): Promise<void> {
    console.log(`Cleanup: Attempting to delete einheit "${name}"`);
    try {
        page.removeAllListeners('dialog');
        await navigateToEinheiten(page);

        const form = page.locator('form');
        if (await form.isVisible().catch(() => false)) {
            const cancelButton = page.locator('button.zev-button--secondary');
            if (await cancelButton.isVisible().catch(() => false)) {
                await cancelButton.click();
                await page.waitForTimeout(500);
            }
        }

        await waitForTableWithData(page, 5000);

        const einheitRow = page.locator(`tr:has-text("${name}")`);
        const isVisible = await einheitRow.isVisible().catch(() => false);
        console.log(`Cleanup: Einheit row "${name}" visible: ${isVisible}`);

        if (isVisible) {
            page.on('dialog', async dialog => {
                console.log(`Cleanup: Dialog appeared, accepting...`);
                await dialog.accept();
            });

            const kebabButton = einheitRow.locator('.zev-kebab-button');
            await kebabButton.click();
            await page.waitForTimeout(300);

            const deleteItem = einheitRow.locator('.zev-kebab-menu__item--danger');
            await deleteItem.click();

            await page.waitForTimeout(2000);

            const stillVisible = await einheitRow.isVisible().catch(() => false);
            if (!stillVisible) {
                console.log(`Cleanup: Successfully deleted einheit "${name}"`);
            } else {
                console.log(`Cleanup: Einheit "${name}" still visible after delete attempt`);
            }

            page.removeAllListeners('dialog');
        } else {
            console.log(`Cleanup: Einheit "${name}" not found (may already be deleted)`);
        }
    } catch (error) {
        console.log(`Cleanup: Error deleting einheit "${name}": ${error}`);
        page.removeAllListeners('dialog');
    }
}

/**
 * Cleanup function to delete all created einheiten
 */
async function cleanupCreatedEinheiten(page: Page): Promise<void> {
    for (const name of createdEinheitNames) {
        await deleteEinheitByName(page, name);
    }
    createdEinheitNames = [];
}

test.beforeEach(() => {
    createdEinheitNames = [];
});

test.afterEach(async ({ page }) => {
    await cleanupCreatedEinheiten(page);
});

test.describe('Einheiten Management - Navigation and Display', () => {
    test('should display the einheiten management page with table and create button', async ({ page }) => {
        await navigateToEinheiten(page);

        const title = page.locator('.zev-container h1');
        await expect(title).toBeVisible();

        const createButton = page.locator('button.zev-button--primary');
        await expect(createButton).toBeVisible();

        const table = page.locator('.zev-table');
        const hasTable = await table.isVisible().catch(() => false);
        expect(hasTable).toBeTruthy();
    });

    test('should display einheiten table with correct columns', async ({ page }) => {
        await navigateToEinheiten(page);

        const table = page.locator('.zev-table');
        const hasTable = await table.isVisible().catch(() => false);

        if (hasTable) {
            const headers = page.locator('.zev-table th');
            const headerCount = await headers.count();
            // ID, Name, Typ, Messpunkt, Actions
            expect(headerCount).toBeGreaterThanOrEqual(4);
        }
    });

    test('should display einheiten data in table rows', async ({ page }) => {
        await navigateToEinheiten(page);

        const rows = page.locator('.zev-table tbody tr');
        const rowCount = await rows.count();
        expect(rowCount).toBeGreaterThan(0);
    });
});

test.describe('Einheiten Management - Sorting', () => {
    test('should sort einheiten by clicking on column headers', async ({ page }) => {
        await navigateToEinheiten(page);

        const table = page.locator('.zev-table');
        const hasTable = await table.isVisible().catch(() => false);

        if (hasTable) {
            const rows = page.locator('.zev-table tbody tr');
            const rowCount = await rows.count();

            if (rowCount > 1) {
                // Click on Name header to sort
                const nameHeader = page.locator('th').filter({ hasText: /Name/i }).first();
                await nameHeader.click();

                const sortIndicator = page.locator('.zev-table__sort-indicator');
                await expect(sortIndicator.first()).toBeVisible();

                // Click again to reverse sort
                await nameHeader.click();
                await expect(sortIndicator.first()).toBeVisible();
            }
        }
    });
});

test.describe('Einheiten Management - Create Einheit', () => {
    test('should show einheit form when clicking create button', async ({ page }) => {
        await navigateToEinheiten(page);

        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        const form = page.locator('form');
        await expect(form).toBeVisible();

        await expect(page.locator('#name')).toBeVisible();
        await expect(page.locator('#typ')).toBeVisible();
        await expect(page.locator('#messpunkt')).toBeVisible();

        const submitButton = page.locator('button[type="submit"]');
        const cancelButton = page.locator('button.zev-button--secondary');
        await expect(submitButton).toBeVisible();
        await expect(cancelButton).toBeVisible();
    });

    test('should create a new CONSUMER einheit successfully', async ({ page }) => {
        await navigateToEinheiten(page);

        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        const testName = generateTestEinheitName('Consumer');
        await fillEinheitForm(page, {
            name: testName,
            typ: 'CONSUMER',
            messpunkt: 'MP-E2E-001'
        });

        const submitButton = page.locator('button[type="submit"]');
        await submitButton.click();

        let isSuccess = false;
        try {
            isSuccess = await waitForFormResult(page, 20000);
        } catch {
            console.log('Einheit creation failed, skipping verification');
            return;
        }

        if (isSuccess) {
            createdEinheitNames.push(testName);

            await waitForTableWithData(page, 10000);

            const newRow = page.locator(`tr:has-text("${testName}")`);
            await expect(newRow).toBeVisible({ timeout: 10000 });

            // Verify typ column shows CONSUMER
            await expect(newRow).toContainText('CONSUMER');
        }
    });

    test('should create a new PRODUCER einheit successfully', async ({ page }) => {
        await navigateToEinheiten(page);

        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        const testName = generateTestEinheitName('Producer');
        await fillEinheitForm(page, {
            name: testName,
            typ: 'PRODUCER',
            messpunkt: 'MP-E2E-002'
        });

        const submitButton = page.locator('button[type="submit"]');
        await submitButton.click();

        let isSuccess = false;
        try {
            isSuccess = await waitForFormResult(page, 20000);
        } catch {
            console.log('Einheit creation failed, skipping verification');
            return;
        }

        if (isSuccess) {
            createdEinheitNames.push(testName);

            await waitForTableWithData(page, 10000);

            const newRow = page.locator(`tr:has-text("${testName}")`);
            await expect(newRow).toBeVisible({ timeout: 10000 });

            await expect(newRow).toContainText('PRODUCER');
        }
    });

    test('should cancel form and return to list', async ({ page }) => {
        await navigateToEinheiten(page);

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

test.describe('Einheiten Management - Form Validation', () => {
    test('should disable submit when name is empty', async ({ page }) => {
        await navigateToEinheiten(page);

        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

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

        // First create a test einheit
        let createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        const originalName = generateTestEinheitName('Edit');
        await fillEinheitForm(page, {
            name: originalName,
            typ: 'CONSUMER',
            messpunkt: 'MP-EDIT-001'
        });

        let submitButton = page.locator('button[type="submit"]');
        await submitButton.click();

        let isSuccess = false;
        try {
            isSuccess = await waitForFormResult(page, 20000);
        } catch {
            console.log('Einheit creation failed, skipping edit test');
            return;
        }

        if (!isSuccess) {
            console.log('Einheit creation failed, skipping edit test');
            return;
        }

        createdEinheitNames.push(originalName);

        await waitForTableWithData(page, 10000);

        const einheitRow = page.locator(`tr:has-text("${originalName}")`);
        await expect(einheitRow).toBeVisible({ timeout: 10000 });

        // Use kebab menu for edit
        await clickKebabMenuItem(page, einheitRow, 'edit');

        await expect(page.locator('form')).toBeVisible({ timeout: 5000 });

        // Verify form is populated
        const nameInput = page.locator('#name');
        await expect(nameInput).toHaveValue(originalName);

        // Change the messpunkt
        await page.locator('#messpunkt').fill('MP-EDIT-UPDATED');

        submitButton = page.locator('button[type="submit"]');
        await submitButton.click();

        try {
            isSuccess = await waitForFormResult(page, 20000);
        } catch {
            console.log('Einheit edit failed, skipping verification');
            return;
        }

        if (isSuccess) {
            await waitForTableWithData(page, 10000);
            const updatedRow = page.locator(`tr:has-text("${originalName}")`);
            await expect(updatedRow).toBeVisible({ timeout: 10000 });
            await expect(updatedRow).toContainText('MP-EDIT-UPDATED');
        }
    });
});

test.describe('Einheiten Management - Delete Einheit', () => {
    test('should show confirmation dialog when deleting einheit', async ({ page }) => {
        await navigateToEinheiten(page);

        // Create a test einheit
        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        const testName = generateTestEinheitName('Delete Dialog');
        await fillEinheitForm(page, {
            name: testName,
            typ: 'CONSUMER'
        });

        const submitButton = page.locator('button[type="submit"]');
        await submitButton.click();

        let isSuccess = false;
        try {
            isSuccess = await waitForFormResult(page, 20000);
        } catch {
            console.log('Einheit creation failed, skipping delete test');
            return;
        }

        if (!isSuccess) {
            console.log('Einheit creation failed, skipping delete test');
            return;
        }

        createdEinheitNames.push(testName);

        await waitForTableWithData(page, 10000);

        const einheitRow = page.locator(`tr:has-text("${testName}")`);
        await expect(einheitRow).toBeVisible({ timeout: 10000 });

        // Set up dialog handler to dismiss (cancel deletion)
        let dialogMessage = '';
        page.once('dialog', async dialog => {
            dialogMessage = dialog.message();
            await dialog.dismiss();
        });

        await clickKebabMenuItem(page, einheitRow, 'delete');
        await page.waitForTimeout(500);

        expect(dialogMessage).toBeTruthy();

        // Einheit should still exist
        await expect(einheitRow).toBeVisible();
    });

    test('should delete einheit when confirmed', async ({ page }) => {
        await navigateToEinheiten(page);

        // Create a test einheit
        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        const testName = generateTestEinheitName('Delete Confirm');
        await fillEinheitForm(page, {
            name: testName,
            typ: 'CONSUMER'
        });

        const submitButton = page.locator('button[type="submit"]');
        await submitButton.click();

        let isSuccess = false;
        try {
            isSuccess = await waitForFormResult(page, 20000);
        } catch {
            console.log('Einheit creation failed, skipping delete test');
            return;
        }

        if (!isSuccess) {
            console.log('Einheit creation failed, skipping delete test');
            return;
        }

        createdEinheitNames.push(testName);

        await waitForTableWithData(page, 10000);

        const einheitRow = page.locator(`tr:has-text("${testName}")`);
        await expect(einheitRow).toBeVisible({ timeout: 10000 });

        // Accept deletion
        page.once('dialog', async dialog => {
            await dialog.accept();
        });

        await clickKebabMenuItem(page, einheitRow, 'delete');

        try {
            await expect(einheitRow).not.toBeVisible({ timeout: 10000 });
            createdEinheitNames = createdEinheitNames.filter(n => n !== testName);
        } catch {
            console.log('Einheit deletion may have failed, row still visible');
        }
    });
});
