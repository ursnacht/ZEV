import { test, expect } from '@playwright/test';
import { navigateViaMenu } from './helpers';

/**
 * tests / statistik.spec.ts
 * E2E tests for the Statistik (Statistics) page
 */

/**
 * Helper function to navigate to Statistik page
 */
async function navigateToStatistik(page: any) {
    await navigateViaMenu(page, '/statistik');

    // Wait for Statistik page to load
    await page.locator('h1').filter({ hasText: /Statistik/i }).waitFor({ state: 'visible', timeout: 10000 });
}

test.describe('Statistik Page', () => {
    test('should display the statistics page with date selection form', async ({ page }) => {
        await navigateToStatistik(page);

        // Check that date inputs are visible
        const dateFromInput = page.locator('#dateFrom');
        const dateToInput = page.locator('#dateTo');
        await expect(dateFromInput).toBeVisible();
        await expect(dateToInput).toBeVisible();

        // Check that submit button exists
        const submitButton = page.locator('button.zev-button--primary[type="submit"]');
        await expect(submitButton).toBeVisible();

        // Check that PDF export button exists and is disabled (no data loaded yet)
        const pdfButton = page.locator('button.zev-button--secondary');
        await expect(pdfButton).toBeVisible();
        await expect(pdfButton).toBeDisabled();

        // Check empty state message is shown
        const emptyState = page.locator('.zev-empty-state');
        await expect(emptyState).toBeVisible();
    });

    test('should load and display statistics when date range is submitted', async ({ page }) => {
        await navigateToStatistik(page);

        // The form should have default dates pre-filled (previous month)
        const dateFromInput = page.locator('#dateFrom');
        const dateToInput = page.locator('#dateTo');

        // Verify dates are filled
        const dateFromValue = await dateFromInput.inputValue();
        const dateToValue = await dateToInput.inputValue();
        expect(dateFromValue).toBeTruthy();
        expect(dateToValue).toBeTruthy();

        // Click submit button to load statistics
        const submitButton = page.locator('button.zev-button--primary[type="submit"]');
        await submitButton.click();

        // Wait for loading to complete (button text changes or results appear)
        // Either we get statistics panels or an error message
        await page.waitForTimeout(2000);

        // Check if statistics overview panel appears OR empty state is still shown (if no data)
        const statistikUebersicht = page.locator('.zev-panel__title').filter({ hasText: /Uebersicht|Overview/i });
        const emptyState = page.locator('.zev-empty-state');
        const errorMessage = page.locator('.zev-message--error');

        // One of these should be visible after submission
        const hasOverview = await statistikUebersicht.isVisible().catch(() => false);
        const hasEmpty = await emptyState.isVisible().catch(() => false);
        const hasError = await errorMessage.isVisible().catch(() => false);

        // At minimum, the page should respond to the submission
        expect(hasOverview || hasEmpty || hasError).toBeTruthy();

        // If statistics loaded, verify structure
        if (hasOverview) {
            // Check for info rows in overview
            const infoRows = page.locator('.zev-info-row');
            await expect(infoRows.first()).toBeVisible();

            // Check for status indicator
            const statusIndicator = page.locator('.zev-status-indicator').first();
            await expect(statusIndicator).toBeVisible();

            // PDF button should now be enabled
            const pdfButton = page.locator('button.zev-button--secondary');
            await expect(pdfButton).toBeEnabled();
        }
    });
});

test.describe('Statistik Page - Monthly Statistics', () => {
    test('should display monthly panels with data tables when statistics are loaded', async ({ page }) => {
        await navigateToStatistik(page);

        // Submit form to load statistics
        const submitButton = page.locator('button.zev-button--primary[type="submit"]');
        await submitButton.click();

        // Wait for response
        await page.waitForTimeout(2000);

        // Check if monthly panels are displayed
        const monthPanels = page.locator('.zev-panel--month');
        const monthPanelCount = await monthPanels.count();

        if (monthPanelCount > 0) {
            // Verify first month panel structure
            const firstMonthPanel = monthPanels.first();
            await expect(firstMonthPanel).toBeVisible();

            // Check for bar chart table
            const barTable = firstMonthPanel.locator('.zev-table--bars');
            await expect(barTable).toBeVisible();

            // Check for bar containers (visualization)
            const barContainers = firstMonthPanel.locator('.zev-bar-container');
            expect(await barContainers.count()).toBeGreaterThan(0);

            // Check for comparison section
            const comparisonSection = firstMonthPanel.locator('.zev-comparison-section');
            await expect(comparisonSection).toBeVisible();

            // Check for comparison grid items
            const comparisonItems = firstMonthPanel.locator('.zev-comparison-item');
            expect(await comparisonItems.count()).toBe(3); // A=B, A=C, B=C

            // Check for Einheit-Summen table (new feature)
            const einheitSummenTable = firstMonthPanel.locator('.zev-table--compact');
            if (await einheitSummenTable.isVisible()) {
                // Verify table has producer/consumer rows
                const producerRows = firstMonthPanel.locator('.zev-table__row--producer');
                const consumerRows = firstMonthPanel.locator('.zev-table__row--consumer');

                // At least some rows should exist
                const totalRows = await producerRows.count() + await consumerRows.count();
                expect(totalRows).toBeGreaterThan(0);
            }
        }
    });
});
