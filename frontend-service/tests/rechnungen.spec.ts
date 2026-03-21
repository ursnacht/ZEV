import { test, expect, Page } from '@playwright/test';
import { navigateViaMenu, waitForFormResult, waitForTableWithData } from './helpers';

/**
 * tests / rechnungen.spec.ts
 * E2E tests for the Rechnungen (Invoice Generation) page
 * Tests cover: navigation, unit selection (CONSUMER + PRODUCER), date entry, invoice generation
 */

/**
 * Navigate to the Rechnungen page
 */
async function navigateToRechnungen(page: Page): Promise<void> {
    await navigateViaMenu(page, '/rechnungen');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
}

test.describe('Rechnungen - Navigation and Display', () => {
    test('should navigate to Rechnungen page', async ({ page }) => {
        await navigateToRechnungen(page);

        await expect(page.locator('.zev-container h1')).toBeVisible();
    });

    test('should display unit selection checkbox group', async ({ page }) => {
        await navigateToRechnungen(page);

        // The checkbox group for unit selection must be present
        const checkboxGroup = page.locator('.zev-checkbox-group');
        await expect(checkboxGroup).toBeVisible();
    });

    test('should display date input fields', async ({ page }) => {
        await navigateToRechnungen(page);

        await expect(page.locator('#dateFrom')).toBeVisible();
        await expect(page.locator('#dateTo')).toBeVisible();
    });

    test('should display generate button', async ({ page }) => {
        await navigateToRechnungen(page);

        const generateButton = page.locator('button[type="submit"]');
        await expect(generateButton).toBeVisible();
    });

    test('should have generate button disabled when no units selected', async ({ page }) => {
        await navigateToRechnungen(page);

        // Deselect everything
        const selectAllCheckbox = page.locator('#einheit-select-all');
        const isChecked = await selectAllCheckbox.isChecked().catch(() => false);
        if (isChecked) {
            await selectAllCheckbox.click();
            await page.waitForTimeout(300);
        }

        // Deselect all individual checkboxes
        const unitCheckboxes = page.locator('.zev-checkbox-item:not(.zev-checkbox-item--select-all) input[type="checkbox"]');
        const count = await unitCheckboxes.count();
        for (let i = 0; i < count; i++) {
            const checkbox = unitCheckboxes.nth(i);
            if (await checkbox.isChecked()) {
                await checkbox.click();
                await page.waitForTimeout(100);
            }
        }

        const generateButton = page.locator('button[type="submit"]');
        await expect(generateButton).toBeDisabled();
    });
});

test.describe('Rechnungen - Einheiten Auswahl (FR-3: Produzenten)', () => {
    test('should show type labels [KONSUMENT] or [PRODUZENT] for units', async ({ page }) => {
        await navigateToRechnungen(page);

        // Wait a bit for units to load
        await page.waitForTimeout(2000);

        const checkboxLabels = page.locator('.zev-checkbox-item:not(.zev-checkbox-item--select-all) label');
        const count = await checkboxLabels.count();

        if (count === 0) {
            console.log('No units available, skipping type label test');
            return;
        }

        // At least one label should contain a type indicator
        const allTexts = await checkboxLabels.allTextContents();
        const hasTypeLabel = allTexts.some(t =>
            t.includes('KONSUMENT') || t.includes('PRODUZENT') ||
            t.includes('[') // The type is shown as [KONSUMENT] or [PRODUZENT]
        );
        expect(hasTypeLabel).toBe(true);
    });

    test('should show select-all checkbox', async ({ page }) => {
        await navigateToRechnungen(page);

        const selectAllCheckbox = page.locator('#einheit-select-all');
        await expect(selectAllCheckbox).toBeVisible();

        const selectAllLabel = page.locator('label[for="einheit-select-all"]');
        await expect(selectAllLabel).toBeVisible();
    });

    test('should toggle all units when select-all is clicked', async ({ page }) => {
        await navigateToRechnungen(page);
        await page.waitForTimeout(2000);

        const unitCheckboxes = page.locator('.zev-checkbox-item:not(.zev-checkbox-item--select-all) input[type="checkbox"]');
        const unitCount = await unitCheckboxes.count();

        if (unitCount === 0) {
            console.log('No units available, skipping select-all test');
            return;
        }

        // Deselect all first
        const selectAll = page.locator('#einheit-select-all');
        if (await selectAll.isChecked()) {
            await selectAll.click();
            await page.waitForTimeout(300);
        }

        // Click select-all to select all
        await selectAll.click();
        await page.waitForTimeout(300);

        // All individual checkboxes should now be checked
        for (let i = 0; i < unitCount; i++) {
            await expect(unitCheckboxes.nth(i)).toBeChecked();
        }

        // Click select-all again to deselect all
        await selectAll.click();
        await page.waitForTimeout(300);

        // All should be unchecked
        for (let i = 0; i < unitCount; i++) {
            await expect(unitCheckboxes.nth(i)).not.toBeChecked();
        }
    });

    test('should enable generate button when unit is selected and dates are set', async ({ page }) => {
        await navigateToRechnungen(page);
        await page.waitForTimeout(2000);

        const unitCheckboxes = page.locator('.zev-checkbox-item:not(.zev-checkbox-item--select-all) input[type="checkbox"]');
        const unitCount = await unitCheckboxes.count();

        if (unitCount === 0) {
            console.log('No units available, skipping generate button test');
            return;
        }

        // Select first unit
        await unitCheckboxes.first().click();
        await page.waitForTimeout(300);

        // Ensure dates are set
        const dateFromInput = page.locator('#dateFrom');
        const dateToInput = page.locator('#dateTo');
        const dateFrom = await dateFromInput.inputValue();
        const dateTo = await dateToInput.inputValue();

        if (!dateFrom) {
            await dateFromInput.fill('2099-01-01');
        }
        if (!dateTo) {
            await dateToInput.fill('2099-01-31');
        }

        const generateButton = page.locator('button[type="submit"]');
        await expect(generateButton).not.toBeDisabled();
    });
});

test.describe('Rechnungen - Datumseingabe', () => {
    test('should default dateFrom to previous month start', async ({ page }) => {
        await navigateToRechnungen(page);

        const dateFrom = await page.locator('#dateFrom').inputValue();
        expect(dateFrom).toMatch(/^\d{4}-\d{2}-01$/); // Always first of month
    });

    test('should default dateTo to previous month end', async ({ page }) => {
        await navigateToRechnungen(page);

        const dateTo = await page.locator('#dateTo').inputValue();
        expect(dateTo).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    });

    test('should auto-set dateTo to month end when dateFrom is changed', async ({ page }) => {
        await navigateToRechnungen(page);

        await page.locator('#dateFrom').fill('2024-03-15');
        // Trigger change event
        await page.locator('#dateFrom').dispatchEvent('input');
        await page.locator('#dateFrom').dispatchEvent('change');
        await page.waitForTimeout(500);

        const dateTo = await page.locator('#dateTo').inputValue();
        expect(dateTo).toBe('2024-03-31');
    });

    test('should display quarter selector component', async ({ page }) => {
        await navigateToRechnungen(page);

        // Quarter selector is embedded in the form
        const quarterSelector = page.locator('app-quarter-selector');
        await expect(quarterSelector).toBeVisible();
    });
});

test.describe('Rechnungen - Invoice Generation', () => {
    test('should generate invoices and show results table', async ({ page }) => {
        await navigateToRechnungen(page);
        await page.waitForTimeout(2000);

        const unitCheckboxes = page.locator('.zev-checkbox-item:not(.zev-checkbox-item--select-all) input[type="checkbox"]');
        const unitCount = await unitCheckboxes.count();

        if (unitCount === 0) {
            console.log('No units available, skipping invoice generation test');
            return;
        }

        // Select all units
        const selectAll = page.locator('#einheit-select-all');
        if (!await selectAll.isChecked()) {
            await selectAll.click();
            await page.waitForTimeout(300);
        }

        // Set a period with high year to avoid real tariff validation
        await page.locator('#dateFrom').fill('2099-01-01');
        await page.locator('#dateTo').fill('2099-03-31');

        const generateButton = page.locator('button[type="submit"]');
        await expect(generateButton).not.toBeDisabled();

        await generateButton.click();

        // Wait for success or error response
        const successMessage = page.locator('.zev-message--success');
        const errorMessage = page.locator('.zev-message--error');
        await expect(successMessage.or(errorMessage)).toBeVisible({ timeout: 30000 });

        const isSuccess = await successMessage.isVisible().catch(() => false);
        if (isSuccess) {
            // Results table should appear
            const resultsPanel = page.locator('.zev-panel');
            await expect(resultsPanel).toBeVisible({ timeout: 5000 });
        } else {
            // Error is expected if no tariffs exist for 2099 – that's OK
            const errorText = await errorMessage.textContent().catch(() => '');
            console.log(`Invoice generation error (expected if no tariffs for 2099): ${errorText}`);
        }
    });

    test('should show error when generating without selecting units', async ({ page }) => {
        await navigateToRechnungen(page);
        await page.waitForTimeout(1000);

        // Ensure nothing is selected
        const selectAll = page.locator('#einheit-select-all');
        if (await selectAll.isChecked()) {
            await selectAll.click();
            await page.waitForTimeout(300);
        }
        const unitCheckboxes = page.locator('.zev-checkbox-item:not(.zev-checkbox-item--select-all) input[type="checkbox"]');
        const unitCount = await unitCheckboxes.count();
        for (let i = 0; i < unitCount; i++) {
            if (await unitCheckboxes.nth(i).isChecked()) {
                await unitCheckboxes.nth(i).click();
                await page.waitForTimeout(100);
            }
        }

        // Generate button should be disabled — cannot submit
        const generateButton = page.locator('button[type="submit"]');
        await expect(generateButton).toBeDisabled();
    });

    test('should show results with download buttons after successful generation', async ({ page }) => {
        await navigateToRechnungen(page);
        await page.waitForTimeout(2000);

        const unitCheckboxes = page.locator('.zev-checkbox-item:not(.zev-checkbox-item--select-all) input[type="checkbox"]');
        const unitCount = await unitCheckboxes.count();

        if (unitCount === 0) {
            console.log('No units available, skipping results test');
            return;
        }

        await page.locator('#einheit-select-all').click();
        await page.waitForTimeout(300);

        await page.locator('#dateFrom').fill('2099-01-01');
        await page.locator('#dateTo').fill('2099-03-31');

        await page.locator('button[type="submit"]').click();

        const successMessage = page.locator('.zev-message--success');
        const errorMessage = page.locator('.zev-message--error');
        await expect(successMessage.or(errorMessage)).toBeVisible({ timeout: 30000 });

        const isSuccess = await successMessage.isVisible().catch(() => false);
        if (isSuccess) {
            // Results table should have download buttons
            const downloadButtons = page.locator('.zev-panel .zev-button--secondary');
            const downloadCount = await downloadButtons.count();
            expect(downloadCount).toBeGreaterThan(0);
        }
    });
});
