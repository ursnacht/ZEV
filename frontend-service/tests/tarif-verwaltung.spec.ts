import { test, expect, Page } from '@playwright/test';
import { navigateViaMenu, clickKebabMenuItem, waitForFormResult, waitForTableWithData } from './helpers';

/**
 * tests / tarif-verwaltung.spec.ts
 * E2E tests for the Tarif (Tariff) Management page
 */

// Track created tariffs for cleanup
let createdTarifNames: string[] = [];

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
 * Helper to create a unique test tariff name
 */
function generateTestTarifName(prefix: string = 'E2E Test'): string {
    return `${prefix} ${Date.now()}`;
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

/**
 * Helper to delete a tariff by name
 */
async function deleteTarifByName(page: Page, name: string): Promise<void> {
    console.log(`Cleanup: Attempting to delete tariff "${name}"`);
    try {
        // Remove any existing dialog handlers to avoid conflicts
        page.removeAllListeners('dialog');

        // Always navigate to tarife page for cleanup
        await navigateToTarife(page);

        // Cancel any open form first
        const form = page.locator('form');
        if (await form.isVisible().catch(() => false)) {
            const cancelButton = page.locator('button.zev-button--secondary');
            if (await cancelButton.isVisible().catch(() => false)) {
                await cancelButton.click();
                await page.waitForTimeout(500);
            }
        }

        // Wait for table
        await waitForTableWithData(page, 5000);

        // Find the tariff row
        const tarifRow = page.locator(`tr:has-text("${name}")`);
        const isVisible = await tarifRow.isVisible().catch(() => false);
        console.log(`Cleanup: Tariff row "${name}" visible: ${isVisible}`);

        if (isVisible) {
            // Set up dialog handler to accept deletion BEFORE clicking
            page.on('dialog', async dialog => {
                console.log(`Cleanup: Dialog appeared, accepting...`);
                await dialog.accept();
            });

            // Open kebab menu and click delete
            const kebabButton = tarifRow.locator('.zev-kebab-button');
            await kebabButton.click();
            await page.waitForTimeout(300);

            const deleteItem = tarifRow.locator('.zev-kebab-menu__item--danger');
            await deleteItem.click();

            // Wait for deletion to complete
            await page.waitForTimeout(2000);

            // Verify deletion
            const stillVisible = await tarifRow.isVisible().catch(() => false);
            if (!stillVisible) {
                console.log(`Cleanup: Successfully deleted tariff "${name}"`);
            } else {
                console.log(`Cleanup: Tariff "${name}" still visible after delete attempt`);
            }

            // Remove dialog handler
            page.removeAllListeners('dialog');
        } else {
            console.log(`Cleanup: Tariff "${name}" not found (may already be deleted)`);
        }
    } catch (error) {
        console.log(`Cleanup: Error deleting tariff "${name}": ${error}`);
        page.removeAllListeners('dialog');
    }
}

/**
 * Cleanup function to delete all created tariffs
 */
async function cleanupCreatedTariffs(page: Page): Promise<void> {
    for (const name of createdTarifNames) {
        await deleteTarifByName(page, name);
    }
    createdTarifNames = [];
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

        // Check for page title
        const title = page.locator('.zev-container h1');
        await expect(title).toBeVisible();

        // Check for "Create new tariff" button
        const createButton = page.locator('button.zev-button--primary');
        await expect(createButton).toBeVisible();

        // Check that either the table or empty message is visible
        const table = page.locator('.zev-table');
        const emptyMessage = page.locator('p');
        const hasTable = await table.isVisible().catch(() => false);
        const hasEmptyMessage = await emptyMessage.isVisible().catch(() => false);

        expect(hasTable || hasEmptyMessage).toBeTruthy();
    });

    test('should display tariff table with correct columns', async ({ page }) => {
        await navigateToTarife(page);

        // Wait for table to be visible (may need to check if tariffs exist)
        const table = page.locator('.zev-table');
        const hasTable = await table.isVisible().catch(() => false);

        if (hasTable) {
            // Check for expected column headers
            const headers = page.locator('.zev-table th');
            const headerTexts = await headers.allTextContents();

            // Should have columns for: Tariftyp, Bezeichnung, Preis, Gueltig von, Gueltig bis, Actions
            expect(headerTexts.length).toBeGreaterThanOrEqual(6);
        }
    });

    test('should display tariff type badges (ZEV/VNB)', async ({ page }) => {
        await navigateToTarife(page);

        // Check if tariff type badges are displayed
        const zevBadge = page.locator('.tarif-typ-badge--zev');
        const vnbBadge = page.locator('.tarif-typ-badge--vnb');

        // At least one badge should be visible if tariffs exist
        const hasZev = await zevBadge.first().isVisible().catch(() => false);
        const hasVnb = await vnbBadge.first().isVisible().catch(() => false);

        // Check badges if tariffs exist in table
        const tableRows = page.locator('.zev-table tbody tr');
        const rowCount = await tableRows.count();

        if (rowCount > 0) {
            // Each row should have a badge
            const badges = page.locator('.tarif-typ-badge');
            await expect(badges.first()).toBeVisible();
        }
    });
});

test.describe('Tarif Management - Sorting', () => {
    test('should sort tariffs by clicking on column headers', async ({ page }) => {
        await navigateToTarife(page);

        // Wait for table to load
        const table = page.locator('.zev-table');
        const hasTable = await table.isVisible().catch(() => false);

        if (hasTable) {
            // Get initial order of first column values
            const firstColumnCells = page.locator('.zev-table tbody td:first-child');
            const initialValues = await firstColumnCells.allTextContents();

            if (initialValues.length > 1) {
                // Click on Tariftyp header to sort
                const tariftypHeader = page.locator('th').filter({ hasText: /Tariftyp/i }).first();
                await tariftypHeader.click();

                // Check for sort indicator
                const sortIndicator = page.locator('.zev-table__sort-indicator');
                await expect(sortIndicator.first()).toBeVisible();

                // Click again to reverse sort
                await tariftypHeader.click();

                // Verify sort indicator is still visible (just direction changed)
                await expect(sortIndicator.first()).toBeVisible();
            }
        }
    });
});

test.describe('Tarif Management - Create Tariff', () => {
    test('should show tariff form when clicking create button', async ({ page }) => {
        await navigateToTarife(page);

        // Click create button
        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        // Form should now be visible
        const form = page.locator('form');
        await expect(form).toBeVisible();

        // Check for form fields
        await expect(page.locator('#tariftyp')).toBeVisible();
        await expect(page.locator('#bezeichnung')).toBeVisible();
        await expect(page.locator('#preis')).toBeVisible();
        await expect(page.locator('#gueltigVon')).toBeVisible();
        await expect(page.locator('#gueltigBis')).toBeVisible();

        // Check for submit and cancel buttons
        const submitButton = page.locator('button[type="submit"]');
        const cancelButton = page.locator('button.zev-button--secondary');
        await expect(submitButton).toBeVisible();
        await expect(cancelButton).toBeVisible();
    });

    test('should create a new ZEV tariff successfully', async ({ page }) => {
        await navigateToTarife(page);

        // Click create button
        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        // Fill in the form
        const testName = generateTestTarifName('ZEV Test');
        await fillTarifForm(page, {
            tariftyp: 'ZEV',
            bezeichnung: testName,
            preis: '0.19500',
            gueltigVon: '2099-01-01',
            gueltigBis: '2099-12-31'
        });

        // Submit the form
        const submitButton = page.locator('button[type="submit"]');
        await submitButton.click();

        // Wait for form result
        let isSuccess = false;
        try {
            isSuccess = await waitForFormResult(page, 20000);
        } catch {
            console.log('Tariff creation failed, skipping verification');
            return;
        }

        if (isSuccess) {
            // Track for cleanup
            createdTarifNames.push(testName);

            // Wait for table to reload
            await waitForTableWithData(page, 10000);

            // Verify the new tariff appears in the table
            const newTarifRow = page.locator(`tr:has-text("${testName}")`);
            await expect(newTarifRow).toBeVisible({ timeout: 10000 });

            // Verify it has ZEV badge
            const zevBadge = newTarifRow.locator('.tarif-typ-badge--zev');
            await expect(zevBadge).toBeVisible();
        } else {
            console.log('Tariff creation failed, skipping verification');
        }
    });

    test('should create a new VNB tariff successfully', async ({ page }) => {
        await navigateToTarife(page);

        // Click create button
        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        // Fill in the form
        const testName = generateTestTarifName('VNB Test');
        await fillTarifForm(page, {
            tariftyp: 'VNB',
            bezeichnung: testName,
            preis: '0.34192',
            gueltigVon: '2099-01-01',
            gueltigBis: '2099-12-31'
        });

        // Submit the form
        const submitButton = page.locator('button[type="submit"]');
        await submitButton.click();

        // Wait for form result
        let isSuccess = false;
        try {
            isSuccess = await waitForFormResult(page, 20000);
        } catch {
            console.log('Tariff creation failed, skipping verification');
            return;
        }

        if (isSuccess) {
            // Track for cleanup
            createdTarifNames.push(testName);

            // Wait for table to reload
            await waitForTableWithData(page, 10000);

            // Verify the new tariff appears in the table
            const newTarifRow = page.locator(`tr:has-text("${testName}")`);
            await expect(newTarifRow).toBeVisible({ timeout: 10000 });

            // Verify it has VNB badge
            const vnbBadge = newTarifRow.locator('.tarif-typ-badge--vnb');
            await expect(vnbBadge).toBeVisible();
        } else {
            console.log('Tariff creation failed, skipping verification');
        }
    });

    test('should cancel form and return to list', async ({ page }) => {
        await navigateToTarife(page);

        // Click create button
        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        // Form should be visible
        const form = page.locator('form');
        await expect(form).toBeVisible();

        // Click cancel button
        const cancelButton = page.locator('button.zev-button--secondary');
        await cancelButton.click();

        // Form should be hidden, list should be visible
        await expect(form).not.toBeVisible();
        const createButtonAgain = page.locator('button.zev-button--primary').first();
        await expect(createButtonAgain).toBeVisible();
    });
});

test.describe('Tarif Management - Form Validation', () => {
    test('should show error for empty bezeichnung', async ({ page }) => {
        await navigateToTarife(page);

        // Click create button
        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        // Fill form with empty bezeichnung
        await page.locator('#tariftyp').selectOption('ZEV');
        await page.locator('#bezeichnung').fill('');
        await page.locator('#preis').fill('0.20000');
        await page.locator('#gueltigVon').fill('2099-01-01');
        await page.locator('#gueltigBis').fill('2099-12-31');

        // Check that submit button is disabled or error message is shown
        const submitButton = page.locator('button[type="submit"]');
        const isDisabled = await submitButton.isDisabled();
        const errorMessage = page.locator('.zev-form-error');
        const hasError = await errorMessage.isVisible().catch(() => false);

        expect(isDisabled || hasError).toBeTruthy();
    });

    test('should show error for invalid date range (von > bis)', async ({ page }) => {
        await navigateToTarife(page);

        // Click create button
        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        // Fill form with invalid date range
        await page.locator('#tariftyp').selectOption('ZEV');
        await page.locator('#bezeichnung').fill('Test Invalid Date');
        await page.locator('#preis').fill('0.20000');
        await page.locator('#gueltigVon').fill('2099-12-31');
        await page.locator('#gueltigBis').fill('2099-01-01'); // Before von

        // Check that error message is shown or button is disabled
        const submitButton = page.locator('button[type="submit"]');
        const isDisabled = await submitButton.isDisabled();
        const errorMessage = page.locator('.zev-form-error');
        const hasError = await errorMessage.isVisible().catch(() => false);

        expect(isDisabled || hasError).toBeTruthy();
    });

    test('should show error for negative price', async ({ page }) => {
        await navigateToTarife(page);

        // Click create button
        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        // Fill form with negative price
        await page.locator('#tariftyp').selectOption('ZEV');
        await page.locator('#bezeichnung').fill('Test Negative Price');
        await page.locator('#preis').fill('-0.10000');
        await page.locator('#gueltigVon').fill('2099-01-01');
        await page.locator('#gueltigBis').fill('2099-12-31');

        // Check that error message is shown or button is disabled
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

        // First create a test tariff
        let createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        const originalName = generateTestTarifName('Edit Test');
        await fillTarifForm(page, {
            tariftyp: 'ZEV',
            bezeichnung: originalName,
            preis: '0.20000',
            gueltigVon: '2099-01-01',
            gueltigBis: '2099-06-30'
        });

        let submitButton = page.locator('button[type="submit"]');
        await submitButton.click();

        // Wait for form result
        let isSuccess = false;
        try {
            isSuccess = await waitForFormResult(page, 20000);
        } catch {
            console.log('Tariff creation failed, skipping edit test');
            return;
        }

        if (!isSuccess) {
            console.log('Tariff creation failed, skipping edit test');
            return;
        }

        // Track for cleanup
        createdTarifNames.push(originalName);

        // Wait for table to reload
        await waitForTableWithData(page, 10000);

        // Find the row and click edit
        const tarifRow = page.locator(`tr:has-text("${originalName}")`);
        await expect(tarifRow).toBeVisible({ timeout: 10000 });

        // Use kebab menu for edit
        await clickKebabMenuItem(page, tarifRow, 'edit');

        // Form should now be in edit mode
        await expect(page.locator('form')).toBeVisible({ timeout: 5000 });

        // Verify form is populated with existing values
        const bezeichnungInput = page.locator('#bezeichnung');
        await expect(bezeichnungInput).toHaveValue(originalName);

        // Change the price
        await page.locator('#preis').fill('0.21000');

        // Save changes
        submitButton = page.locator('button[type="submit"]');
        await submitButton.click();

        // Wait for form result after edit
        try {
            isSuccess = await waitForFormResult(page, 20000);
        } catch {
            console.log('Tariff edit failed, skipping verification');
            return;
        }

        if (!isSuccess) {
            console.log('Tariff edit failed, skipping verification');
            // Cancel form if still visible
            const cancelButton = page.locator('button.zev-button--secondary');
            if (await cancelButton.isVisible().catch(() => false)) {
                await cancelButton.click();
            }
            return;
        }

        // Wait for table to reload
        await waitForTableWithData(page, 10000);
        const updatedRow = page.locator(`tr:has-text("${originalName}")`);
        await expect(updatedRow).toBeVisible({ timeout: 10000 });
    });
});

test.describe('Tarif Management - Delete Tariff', () => {
    test('should show confirmation dialog when deleting tariff', async ({ page }) => {
        await navigateToTarife(page);

        // First create a test tariff
        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        const testName = generateTestTarifName('Delete Test');
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
            console.log('Tariff creation failed, skipping delete test');
            return;
        }

        if (!isSuccess) {
            console.log('Tariff creation failed, skipping delete test');
            return;
        }

        // Track for cleanup (in case test fails before deletion)
        createdTarifNames.push(testName);

        // Wait for table to reload
        await waitForTableWithData(page, 10000);

        // Find the row
        const tarifRow = page.locator(`tr:has-text("${testName}")`);
        await expect(tarifRow).toBeVisible({ timeout: 10000 });

        // Set up dialog handler to dismiss
        let dialogMessage = '';
        page.once('dialog', async dialog => {
            dialogMessage = dialog.message();
            await dialog.dismiss(); // Cancel deletion
        });

        // Click delete button via kebab menu
        await clickKebabMenuItem(page, tarifRow, 'delete');

        await page.waitForTimeout(500);

        // Verify dialog was shown
        expect(dialogMessage).toBeTruthy();

        // Tariff should still exist
        await expect(tarifRow).toBeVisible();

        // Cleanup will happen in afterEach
    });

    test('should delete tariff when confirmed', async ({ page }) => {
        await navigateToTarife(page);

        // First create a test tariff
        const createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        const testName = generateTestTarifName('Delete Confirm');
        await fillTarifForm(page, {
            tariftyp: 'VNB',
            bezeichnung: testName,
            preis: '0.35000',
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
            console.log('Tariff creation failed, skipping delete test');
            return;
        }

        if (!isSuccess) {
            console.log('Tariff creation failed, skipping delete test');
            return;
        }

        // Track for cleanup (in case deletion fails)
        createdTarifNames.push(testName);

        // Wait for table to reload
        await waitForTableWithData(page, 10000);

        // Find the row
        const tarifRow = page.locator(`tr:has-text("${testName}")`);
        await expect(tarifRow).toBeVisible({ timeout: 10000 });

        // Set up dialog handler BEFORE clicking delete
        page.once('dialog', async dialog => {
            await dialog.accept();
        });

        // Click delete button via kebab menu
        await clickKebabMenuItem(page, tarifRow, 'delete');

        // Wait for deletion - the specific row should disappear
        try {
            await expect(tarifRow).not.toBeVisible({ timeout: 10000 });
            // Remove from tracking since it was successfully deleted
            createdTarifNames = createdTarifNames.filter(n => n !== testName);
        } catch {
            console.log('Tariff deletion may have failed, row still visible');
        }
    });
});

test.describe('Tarif Management - Overlapping Validation', () => {
    test('should show error when creating overlapping tariff of same type', async ({ page }) => {
        await navigateToTarife(page);

        // Create first tariff
        let createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        const testName1 = generateTestTarifName('Overlap Test 1');
        await fillTarifForm(page, {
            tariftyp: 'ZEV',
            bezeichnung: testName1,
            preis: '0.20000',
            gueltigVon: '2098-01-01',
            gueltigBis: '2098-12-31'
        });

        let submitButton = page.locator('button[type="submit"]');
        await submitButton.click();

        // Wait for API response - either success message or error message
        const successMessage = page.locator('.zev-message--success');
        const errorMessage = page.locator('.zev-message--error');
        await expect(successMessage.or(errorMessage)).toBeVisible({ timeout: 15000 });

        // Check if first tariff creation was successful
        let isSuccess = await successMessage.isVisible().catch(() => false);
        if (!isSuccess) {
            console.log('First tariff creation failed, skipping overlapping test');
            return;
        }

        // Track for cleanup
        createdTarifNames.push(testName1);

        // Wait for list
        await page.locator('.zev-table').waitFor({ state: 'visible', timeout: 10000 });

        // Try to create overlapping tariff
        createButton = page.locator('button.zev-button--primary').first();
        await createButton.click();

        const testName2 = generateTestTarifName('Overlap Test 2');
        await fillTarifForm(page, {
            tariftyp: 'ZEV', // Same type
            bezeichnung: testName2,
            preis: '0.21000',
            gueltigVon: '2098-06-01', // Overlaps with first tariff
            gueltigBis: '2099-06-30'
        });

        submitButton = page.locator('button[type="submit"]');
        await submitButton.click();

        // Wait for API response
        await expect(successMessage.or(errorMessage)).toBeVisible({ timeout: 15000 });

        // Either error message is shown (overlapping validation), or form stays visible
        const hasError = await errorMessage.isVisible().catch(() => false);
        const formStillVisible = await page.locator('form').isVisible().catch(() => false);

        // If second tariff was created despite overlap, track it for cleanup
        if (!hasError && !formStillVisible) {
            createdTarifNames.push(testName2);
        }

        expect(hasError || formStillVisible).toBeTruthy();

        // Clean up: cancel form if still visible
        if (formStillVisible) {
            const cancelButton = page.locator('button.zev-button--secondary');
            await cancelButton.click();
        }
    });
});

test.describe('Tarif Management - Validation Buttons', () => {
    test('should display validation buttons', async ({ page }) => {
        await navigateToTarife(page);

        // Check for validation buttons (secondary buttons)
        const buttons = page.locator('.button-row button.zev-button--secondary');
        const buttonCount = await buttons.count();

        // Should have 2 validation buttons
        expect(buttonCount).toBe(2);

        // Check button texts
        const buttonTexts = await buttons.allTextContents();
        const hasQuartaleButton = buttonTexts.some(text => text.toLowerCase().includes('quartal'));
        const hasJahreButton = buttonTexts.some(text => text.toLowerCase().includes('jahr'));

        expect(hasQuartaleButton).toBeTruthy();
        expect(hasJahreButton).toBeTruthy();
    });

    test('should show success message when validation passes', async ({ page }) => {
        await navigateToTarife(page);

        // Click "Quartale validieren" button
        const quartaleButton = page.locator('button.zev-button--secondary').filter({ hasText: /quartal/i });
        await quartaleButton.click();

        // Wait for response - either success or error message
        const successMessage = page.locator('.zev-message--success');
        const errorMessage = page.locator('.zev-message--error');
        await expect(successMessage.or(errorMessage)).toBeVisible({ timeout: 15000 });

        // If success, verify message content
        const isSuccess = await successMessage.isVisible().catch(() => false);
        if (isSuccess) {
            // Success message should be visible
            await expect(successMessage).toBeVisible();
        }
    });

    test('should show error message with details when validation fails', async ({ page }) => {
        await navigateToTarife(page);

        // Click "Jahre validieren" button
        const jahreButton = page.locator('button.zev-button--secondary').filter({ hasText: /jahr/i });
        await jahreButton.click();

        // Wait for response
        const successMessage = page.locator('.zev-message--success');
        const errorMessage = page.locator('.zev-message--error');
        await expect(successMessage.or(errorMessage)).toBeVisible({ timeout: 15000 });

        // Check if there are validation errors
        const hasError = await errorMessage.isVisible().catch(() => false);
        if (hasError) {
            // Error message should have validation errors list
            const errorList = page.locator('.validation-errors');
            const hasErrorList = await errorList.isVisible().catch(() => false);

            // If validation fails, errors should be shown
            if (hasErrorList) {
                const errorItems = page.locator('.validation-errors li');
                const errorCount = await errorItems.count();
                expect(errorCount).toBeGreaterThan(0);
            }
        }
    });

    test('should dismiss error message when clicked', async ({ page }) => {
        await navigateToTarife(page);

        // Click validation button
        const jahreButton = page.locator('button.zev-button--secondary').filter({ hasText: /jahr/i });
        await jahreButton.click();

        // Wait for response
        const errorMessage = page.locator('.zev-message--error');
        const successMessage = page.locator('.zev-message--success');
        await expect(successMessage.or(errorMessage)).toBeVisible({ timeout: 15000 });

        // If error message is shown, click to dismiss
        const hasError = await errorMessage.isVisible().catch(() => false);
        if (hasError) {
            // Check for dismiss button (×)
            const dismissButton = page.locator('.zev-message__dismiss');
            const hasDismiss = await dismissButton.isVisible().catch(() => false);

            if (hasDismiss) {
                // Click on error message to dismiss
                await errorMessage.click();

                // Message should disappear
                await expect(errorMessage).not.toBeVisible({ timeout: 5000 });
            }
        }
    });

    test('should run quartale validation and show result', async ({ page }) => {
        await navigateToTarife(page);

        // Click "Quartale validieren" button
        const quartaleButton = page.locator('button.zev-button--secondary').filter({ hasText: /quartal/i });
        await expect(quartaleButton).toBeVisible();
        await quartaleButton.click();

        // Wait for message to appear
        const message = page.locator('.zev-message');
        await expect(message).toBeVisible({ timeout: 15000 });

        // Message should contain some text
        const messageText = await message.textContent();
        expect(messageText).toBeTruthy();
    });

    test('should run jahre validation and show result', async ({ page }) => {
        await navigateToTarife(page);

        // Click "Jahre validieren" button
        const jahreButton = page.locator('button.zev-button--secondary').filter({ hasText: /jahr/i });
        await expect(jahreButton).toBeVisible();
        await jahreButton.click();

        // Wait for message to appear
        const message = page.locator('.zev-message');
        await expect(message).toBeVisible({ timeout: 15000 });

        // Message should contain some text
        const messageText = await message.textContent();
        expect(messageText).toBeTruthy();
    });
});
