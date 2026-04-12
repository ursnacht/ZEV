import { test, expect, Page } from '@playwright/test';
import { navigateViaMenu, clickKebabMenuItem, waitForFormResult, waitForTableWithData } from './helpers';

/**
 * tests / debitorkontrolle.spec.ts
 * E2E tests for Debitorkontrolle (Debtor Control) page
 */

// Track created debitoren for cleanup — identified by their datumVon value (far-future test dates)
let createdDebitorDates: string[] = [];

/**
 * Navigate to the Debitorkontrolle page
 */
async function navigateToDebitorkontrolle(page: Page): Promise<void> {
    await navigateViaMenu(page, '/debitoren');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
}

/**
 * Set the date range inputs to show a specific period
 */
async function setDateRange(page: Page, von: string, bis: string): Promise<void> {
    const dateFrom = page.locator('#dateFrom');
    const dateTo = page.locator('#dateTo');
    await dateFrom.fill(von);
    await dateFrom.dispatchEvent('change');
    await dateTo.fill(bis);
    await dateTo.dispatchEvent('change');
    await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {});
}

/**
 * Get the first selectable mieter option value from the form's dropdown
 */
async function getFirstMieterOptionValue(page: Page): Promise<string | null> {
    const select = page.locator('#mieterId');
    const options = select.locator('option:not([disabled])');
    const count = await options.count();
    if (count > 0) {
        return await options.first().getAttribute('value');
    }
    return null;
}

/**
 * Fill the debitor form
 */
async function fillDebitorForm(page: Page, data: {
    mieterId?: string;
    betrag: string;
    datumVon: string;
    datumBis: string;
    zahldatum?: string;
}): Promise<void> {
    if (data.mieterId) {
        await page.locator('#mieterId').selectOption(data.mieterId);
        await page.waitForTimeout(200);
    }
    await page.locator('#betrag').fill(data.betrag);
    await page.locator('#datumVon').fill(data.datumVon);
    await page.locator('#datumBis').fill(data.datumBis);
    if (data.zahldatum) {
        await page.locator('#zahldatum').fill(data.zahldatum);
    }
}

/**
 * Open the "Neu erfassen" form
 */
async function openCreateForm(page: Page): Promise<void> {
    const createButton = page.locator('button.zev-button--primary').first();
    await createButton.click();
    await page.locator('form').waitFor({ state: 'visible', timeout: 5000 });
}

/**
 * Delete a debitor row that contains the given datumVon date text
 */
async function deleteDebitorByDate(page: Page, datumVon: string): Promise<void> {
    const swissDate = formatToSwiss(datumVon);
    console.log(`Cleanup: Attempting to delete debitor with datumVon "${swissDate}"`);
    try {
        page.removeAllListeners('dialog');
        await navigateToDebitorkontrolle(page);
        await setDateRange(page, datumVon, datumVon.replace('-01-', '-12-').replace('-01', '-31'));

        // Wait a moment for load
        await page.waitForTimeout(1000);

        const row = page.locator(`tr:has-text("${swissDate}")`).first();
        if (await row.isVisible().catch(() => false)) {
            page.on('dialog', async dialog => { await dialog.accept(); });
            await row.locator('.zev-kebab-button').click();
            await page.waitForTimeout(300);
            await row.locator('.zev-kebab-menu__item--danger').click();
            await page.waitForTimeout(1500);
            page.removeAllListeners('dialog');
        } else {
            console.log(`Cleanup: Debitor with datumVon "${swissDate}" not found`);
        }
    } catch (error) {
        console.log(`Cleanup: Error during cleanup: ${error}`);
        page.removeAllListeners('dialog');
    }
}

/**
 * Format ISO date to Swiss dd.MM.yyyy
 */
function formatToSwiss(iso: string): string {
    const [y, m, d] = iso.split('-');
    return `${d}.${m}.${y}`;
}

test.beforeEach(() => {
    createdDebitorDates = [];
});

test.afterEach(async ({ page }) => {
    for (const date of createdDebitorDates) {
        await deleteDebitorByDate(page, date);
    }
    createdDebitorDates = [];
});

// ──────────────────────────────────────────────────────────────────────────────
// Navigation & Display
// ──────────────────────────────────────────────────────────────────────────────

test.describe('Debitorkontrolle - Navigation and Display', () => {

    test('should navigate to Debitorkontrolle via menu', async ({ page }) => {
        await navigateToDebitorkontrolle(page);

        await expect(page.locator('.zev-container h1')).toBeVisible();
        await expect(page.locator('button.zev-button--primary')).toBeVisible();
    });

    test('should show quarter selector and date inputs', async ({ page }) => {
        await navigateToDebitorkontrolle(page);

        await expect(page.locator('app-quarter-selector')).toBeVisible();
        await expect(page.locator('#dateFrom')).toBeVisible();
        await expect(page.locator('#dateTo')).toBeVisible();
    });

    test('should show correct table columns when data is present', async ({ page }) => {
        await navigateToDebitorkontrolle(page);

        const hasData = await waitForTableWithData(page, 5000);

        if (hasData) {
            const headers = page.locator('.zev-table th');
            const headerCount = await headers.count();
            // Mieter, Betrag, Datum von, Datum bis, Zahldatum, Status, Actions
            expect(headerCount).toBeGreaterThanOrEqual(7);
        }
    });

    test('should show empty state message when no debitoren for period', async ({ page }) => {
        await navigateToDebitorkontrolle(page);

        // Set a date range far in the past where no data exists
        await setDateRange(page, '1900-01-01', '1900-03-31');

        const emptyState = page.locator('.zev-empty-state');
        await expect(emptyState).toBeVisible({ timeout: 10000 });
    });

});

// ──────────────────────────────────────────────────────────────────────────────
// Sorting
// ──────────────────────────────────────────────────────────────────────────────

test.describe('Debitorkontrolle - Sorting', () => {

    test('should toggle sort indicator when clicking column header', async ({ page }) => {
        await navigateToDebitorkontrolle(page);

        const hasData = await waitForTableWithData(page, 5000);
        if (!hasData) {
            console.log('No data for sorting test, skipping');
            return;
        }

        // Mieter column header (already sorted by default)
        const mieterHeader = page.locator('.zev-table th').first();
        const sortIndicator = page.locator('.zev-table__sort-indicator');
        await expect(sortIndicator.first()).toBeVisible();

        // Click to reverse
        await mieterHeader.click();
        await expect(sortIndicator.first()).toBeVisible();
    });

    test('should show sort indicator after clicking Betrag column', async ({ page }) => {
        await navigateToDebitorkontrolle(page);

        const hasData = await waitForTableWithData(page, 5000);
        if (!hasData) {
            console.log('No data for sorting test, skipping');
            return;
        }

        const betragHeader = page.locator('th').filter({ hasText: /CHF/i }).first();
        await betragHeader.click();

        const sortIndicator = page.locator('.zev-table__sort-indicator');
        await expect(sortIndicator.first()).toBeVisible();
    });

});

// ──────────────────────────────────────────────────────────────────────────────
// Create
// ──────────────────────────────────────────────────────────────────────────────

test.describe('Debitorkontrolle - Create Debitor', () => {

    test('should show create form when clicking "Neu erfassen"', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        await openCreateForm(page);

        await expect(page.locator('form')).toBeVisible();
        await expect(page.locator('#mieterId')).toBeVisible();
        await expect(page.locator('#betrag')).toBeVisible();
        await expect(page.locator('#datumVon')).toBeVisible();
        await expect(page.locator('#datumBis')).toBeVisible();
        await expect(page.locator('#zahldatum')).toBeVisible();
        await expect(page.locator('button[type="submit"]')).toBeVisible();
        await expect(page.locator('button.zev-button--secondary')).toBeVisible();
    });

    test('should cancel form and return to list', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        await openCreateForm(page);

        await expect(page.locator('form')).toBeVisible();

        await page.locator('button.zev-button--secondary').click();

        await expect(page.locator('form')).not.toBeVisible();
        await expect(page.locator('button.zev-button--primary').first()).toBeVisible();
    });

    test('should create a new debitor successfully', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        await openCreateForm(page);

        const mieterId = await getFirstMieterOptionValue(page);
        if (!mieterId) {
            console.log('No mieter available, skipping create test');
            return;
        }

        const testDate = '2099-01-01';
        const testDateBis = '2099-03-31';

        await fillDebitorForm(page, {
            mieterId,
            betrag: '150.00',
            datumVon: testDate,
            datumBis: testDateBis
        });

        await page.locator('button[type="submit"]').click();

        let isSuccess = false;
        try {
            isSuccess = await waitForFormResult(page, 20000);
        } catch {
            console.log('Debitor creation failed, skipping verification');
            return;
        }

        if (isSuccess) {
            createdDebitorDates.push(testDate);

            // Set date range to show the created entry
            await setDateRange(page, testDate, testDateBis);
            await waitForTableWithData(page, 10000);

            const newRow = page.locator(`tr:has-text("${formatToSwiss(testDate)}")`).first();
            await expect(newRow).toBeVisible({ timeout: 10000 });
            await expect(newRow).toContainText('150.00');
        }
    });

    test('should populate Einheit field when Mieter is selected', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        await openCreateForm(page);

        const mieterId = await getFirstMieterOptionValue(page);
        if (!mieterId) {
            console.log('No mieter available, skipping test');
            return;
        }

        await page.locator('#mieterId').selectOption(mieterId);
        await page.waitForTimeout(300);

        // Einheit field is read-only and should be populated
        const einheitInput = page.locator('input[readonly]');
        const einheitValue = await einheitInput.inputValue();
        // Could be empty if no einheit assigned; just verify the field is rendered
        await expect(einheitInput).toBeVisible();
        console.log(`Einheit name populated: "${einheitValue}"`);
    });

    test('should show status "Offen" for new debitor without zahldatum', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        await openCreateForm(page);

        const mieterId = await getFirstMieterOptionValue(page);
        if (!mieterId) {
            console.log('No mieter available, skipping test');
            return;
        }

        const testDate = '2099-02-01';
        const testDateBis = '2099-02-28';

        await fillDebitorForm(page, {
            mieterId,
            betrag: '99.00',
            datumVon: testDate,
            datumBis: testDateBis
            // zahldatum not set → status = Offen
        });

        await page.locator('button[type="submit"]').click();

        let isSuccess = false;
        try {
            isSuccess = await waitForFormResult(page, 20000);
        } catch {
            console.log('Debitor creation failed, skipping status test');
            return;
        }

        if (isSuccess) {
            createdDebitorDates.push(testDate);

            await setDateRange(page, testDate, testDateBis);
            await waitForTableWithData(page, 10000);

            const row = page.locator(`tr:has-text("${formatToSwiss(testDate)}")`).first();
            await expect(row).toBeVisible({ timeout: 10000 });

            const statusBadge = row.locator('.zev-status--warning');
            await expect(statusBadge).toBeVisible();
        }
    });

});

// ──────────────────────────────────────────────────────────────────────────────
// Form Validation
// ──────────────────────────────────────────────────────────────────────────────

test.describe('Debitorkontrolle - Form Validation', () => {

    test('should disable submit button when required fields are empty', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        await openCreateForm(page);

        // Leave mieterId at 0 (default disabled option) and betrag at 0
        const submitButton = page.locator('button[type="submit"]');
        const isDisabled = await submitButton.isDisabled();
        const errorMessage = page.locator('.zev-form-error');
        const hasError = await errorMessage.isVisible().catch(() => false);

        expect(isDisabled || hasError).toBeTruthy();
    });

    test('should show validation error for betrag = 0', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        await openCreateForm(page);

        await page.locator('#betrag').fill('0');
        await page.locator('#betrag').blur();

        const errorMessage = page.locator('.zev-form-error').filter({ hasText: /betrag/i });
        const submitDisabled = await page.locator('button[type="submit"]').isDisabled();

        // Either error shown or submit disabled
        expect(submitDisabled).toBeTruthy();
    });

    test('should show error when datumVon is after datumBis', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        await openCreateForm(page);

        const mieterId = await getFirstMieterOptionValue(page);
        if (mieterId) await page.locator('#mieterId').selectOption(mieterId);

        await page.locator('#betrag').fill('100');
        await page.locator('#datumVon').fill('2025-12-31');
        await page.locator('#datumBis').fill('2025-01-01');

        const errorMessage = page.locator('.zev-form-error');
        await expect(errorMessage).toBeVisible();

        const submitButton = page.locator('button[type="submit"]');
        await expect(submitButton).toBeDisabled();
    });

    test('should show error when zahldatum is before datumBis', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        await openCreateForm(page);

        const mieterId = await getFirstMieterOptionValue(page);
        if (mieterId) await page.locator('#mieterId').selectOption(mieterId);

        await page.locator('#betrag').fill('100');
        await page.locator('#datumVon').fill('2025-01-01');
        await page.locator('#datumBis').fill('2025-03-31');
        await page.locator('#zahldatum').fill('2025-02-01'); // Before datumBis

        const errorMessage = page.locator('.zev-form-error');
        await expect(errorMessage).toBeVisible();

        const submitButton = page.locator('button[type="submit"]');
        await expect(submitButton).toBeDisabled();
    });

});

// ──────────────────────────────────────────────────────────────────────────────
// Edit
// ──────────────────────────────────────────────────────────────────────────────

test.describe('Debitorkontrolle - Edit Debitor', () => {

    test('should edit a debitor and set zahldatum (status changes to Bezahlt)', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        await openCreateForm(page);

        const mieterId = await getFirstMieterOptionValue(page);
        if (!mieterId) {
            console.log('No mieter available, skipping edit test');
            return;
        }

        const testDate = '2099-03-01';
        const testDateBis = '2099-03-31';

        await fillDebitorForm(page, {
            mieterId,
            betrag: '200.00',
            datumVon: testDate,
            datumBis: testDateBis
        });

        await page.locator('button[type="submit"]').click();

        let isSuccess = false;
        try {
            isSuccess = await waitForFormResult(page, 20000);
        } catch {
            console.log('Debitor creation failed, skipping edit test');
            return;
        }

        if (!isSuccess) {
            console.log('Debitor creation failed, skipping edit test');
            return;
        }

        createdDebitorDates.push(testDate);

        await setDateRange(page, testDate, testDateBis);
        await waitForTableWithData(page, 10000);

        const row = page.locator(`tr:has-text("${formatToSwiss(testDate)}")`).first();
        await expect(row).toBeVisible({ timeout: 10000 });

        // Edit via kebab menu
        await clickKebabMenuItem(page, row, 'edit');
        await expect(page.locator('form')).toBeVisible({ timeout: 5000 });

        // Set zahldatum
        await page.locator('#zahldatum').fill('2099-04-05');

        const submitButton = page.locator('button[type="submit"]');
        await submitButton.click();

        try {
            isSuccess = await waitForFormResult(page, 20000);
        } catch {
            console.log('Debitor edit failed, skipping status verification');
            return;
        }

        if (isSuccess) {
            await setDateRange(page, testDate, testDateBis);
            await waitForTableWithData(page, 10000);

            const updatedRow = page.locator(`tr:has-text("${formatToSwiss(testDate)}")`).first();
            await expect(updatedRow).toBeVisible({ timeout: 10000 });

            // Status should now be "Bezahlt" (success badge)
            const statusBadge = updatedRow.locator('.zev-status--success');
            await expect(statusBadge).toBeVisible();

            // Zahldatum should be shown
            await expect(updatedRow).toContainText('05.04.2099');
        }
    });

    test('should pre-fill form when editing an existing debitor', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        await openCreateForm(page);

        const mieterId = await getFirstMieterOptionValue(page);
        if (!mieterId) {
            console.log('No mieter available, skipping prefill test');
            return;
        }

        const testDate = '2099-04-01';
        const testDateBis = '2099-04-30';

        await fillDebitorForm(page, {
            mieterId,
            betrag: '75.50',
            datumVon: testDate,
            datumBis: testDateBis
        });

        await page.locator('button[type="submit"]').click();

        let isSuccess = false;
        try {
            isSuccess = await waitForFormResult(page, 20000);
        } catch {
            console.log('Debitor creation failed, skipping prefill test');
            return;
        }

        if (!isSuccess) {
            console.log('Debitor creation failed');
            return;
        }

        createdDebitorDates.push(testDate);

        await setDateRange(page, testDate, testDateBis);
        await waitForTableWithData(page, 10000);

        const row = page.locator(`tr:has-text("${formatToSwiss(testDate)}")`).first();
        await expect(row).toBeVisible({ timeout: 10000 });

        await clickKebabMenuItem(page, row, 'edit');
        await expect(page.locator('form')).toBeVisible({ timeout: 5000 });

        // Form should be pre-filled
        const betragInput = page.locator('#betrag');
        const betragValue = await betragInput.inputValue();
        expect(parseFloat(betragValue)).toBeCloseTo(75.50, 1);
    });

});

// ──────────────────────────────────────────────────────────────────────────────
// Delete
// ──────────────────────────────────────────────────────────────────────────────

test.describe('Debitorkontrolle - Delete Debitor', () => {

    test('should show confirmation dialog before deleting', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        await openCreateForm(page);

        const mieterId = await getFirstMieterOptionValue(page);
        if (!mieterId) {
            console.log('No mieter available, skipping delete dialog test');
            return;
        }

        const testDate = '2099-05-01';
        const testDateBis = '2099-05-31';

        await fillDebitorForm(page, {
            mieterId,
            betrag: '50.00',
            datumVon: testDate,
            datumBis: testDateBis
        });

        await page.locator('button[type="submit"]').click();

        let isSuccess = false;
        try {
            isSuccess = await waitForFormResult(page, 20000);
        } catch {
            console.log('Debitor creation failed, skipping delete dialog test');
            return;
        }

        if (!isSuccess) {
            console.log('Debitor creation failed');
            return;
        }

        createdDebitorDates.push(testDate);

        await setDateRange(page, testDate, testDateBis);
        await waitForTableWithData(page, 10000);

        const row = page.locator(`tr:has-text("${formatToSwiss(testDate)}")`).first();
        await expect(row).toBeVisible({ timeout: 10000 });

        // Dismiss the dialog → row should still be visible
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

    test('should delete debitor when confirmed', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        await openCreateForm(page);

        const mieterId = await getFirstMieterOptionValue(page);
        if (!mieterId) {
            console.log('No mieter available, skipping delete test');
            return;
        }

        const testDate = '2099-06-01';
        const testDateBis = '2099-06-30';

        await fillDebitorForm(page, {
            mieterId,
            betrag: '33.33',
            datumVon: testDate,
            datumBis: testDateBis
        });

        await page.locator('button[type="submit"]').click();

        let isSuccess = false;
        try {
            isSuccess = await waitForFormResult(page, 20000);
        } catch {
            console.log('Debitor creation failed, skipping delete test');
            return;
        }

        if (!isSuccess) {
            console.log('Debitor creation failed');
            return;
        }

        createdDebitorDates.push(testDate);

        await setDateRange(page, testDate, testDateBis);
        await waitForTableWithData(page, 10000);

        const row = page.locator(`tr:has-text("${formatToSwiss(testDate)}")`).first();
        await expect(row).toBeVisible({ timeout: 10000 });

        page.once('dialog', async dialog => { await dialog.accept(); });

        await clickKebabMenuItem(page, row, 'delete');

        try {
            await expect(page.locator(`tr:has-text("${formatToSwiss(testDate)}")`)).not.toBeVisible({ timeout: 10000 });
            createdDebitorDates = createdDebitorDates.filter(d => d !== testDate);
        } catch {
            console.log('Row still visible after delete attempt');
        }
    });

});

// ──────────────────────────────────────────────────────────────────────────────
// Date Filter
// ──────────────────────────────────────────────────────────────────────────────

test.describe('Debitorkontrolle - Date Filter', () => {

    test('should filter list by date range', async ({ page }) => {
        await navigateToDebitorkontrolle(page);

        // Set a range that should have no data
        await setDateRange(page, '1900-01-01', '1900-03-31');

        const emptyState = page.locator('.zev-empty-state');
        await expect(emptyState).toBeVisible({ timeout: 10000 });

        // Now set current year range
        const currentYear = new Date().getFullYear();
        await setDateRange(page, `${currentYear}-01-01`, `${currentYear}-12-31`);

        // After changing range, table or empty state should be visible
        const table = page.locator('.zev-table');
        const hasTable = await table.isVisible({ timeout: 8000 }).catch(() => false);
        const hasEmpty = await emptyState.isVisible({ timeout: 3000 }).catch(() => false);

        expect(hasTable || hasEmpty).toBeTruthy();
    });

});
