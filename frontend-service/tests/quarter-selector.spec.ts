import { test, expect } from '@playwright/test';
import { navigateViaMenu } from './helpers';

/**
 * tests / quarter-selector.spec.ts
 * E2E tests for the Quarter Selector component
 */

/**
 * Helper to get the current quarter info
 */
function getCurrentQuarterInfo(): { year: number; quarter: number } {
    const today = new Date();
    const year = today.getFullYear();
    const quarter = Math.ceil((today.getMonth() + 1) / 3);
    return { year, quarter };
}

/**
 * Helper to get expected quarter labels (last 5 quarters)
 */
function getExpectedQuarterLabels(): string[] {
    const labels: string[] = [];
    let { year, quarter } = getCurrentQuarterInfo();

    for (let i = 0; i < 5; i++) {
        labels.unshift(`Q${quarter}/${year}`);
        quarter--;
        if (quarter < 1) {
            quarter = 4;
            year--;
        }
    }
    return labels;
}

/**
 * Helper to calculate expected date range for a quarter
 */
function getQuarterDateRange(quarterLabel: string): { von: string; bis: string } {
    const match = quarterLabel.match(/Q(\d)\/(\d{4})/);
    if (!match) throw new Error(`Invalid quarter label: ${quarterLabel}`);

    const quarter = parseInt(match[1]);
    const year = parseInt(match[2]);
    const startMonth = (quarter - 1) * 3;

    const von = new Date(year, startMonth, 1);
    const bis = new Date(year, startMonth + 3, 0);

    const formatDate = (d: Date) => {
        const y = d.getFullYear();
        const m = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        return `${y}-${m}-${day}`;
    };

    return { von: formatDate(von), bis: formatDate(bis) };
}

test.describe('Quarter Selector Component', () => {

    test.describe('on Rechnungen page', () => {
        test('should display 5 quarter buttons', async ({ page }) => {
            await navigateViaMenu(page, '/rechnungen');
            await page.locator('h1').filter({ hasText: /Rechnungen/i }).waitFor({ state: 'visible', timeout: 10000 });

            const quarterButtons = page.locator('.zev-quarter-button');
            await expect(quarterButtons).toHaveCount(5);
        });

        test('should display correct quarter labels', async ({ page }) => {
            await navigateViaMenu(page, '/rechnungen');
            await page.locator('h1').filter({ hasText: /Rechnungen/i }).waitFor({ state: 'visible', timeout: 10000 });

            const expectedLabels = getExpectedQuarterLabels();
            const quarterButtons = page.locator('.zev-quarter-button');

            for (let i = 0; i < 5; i++) {
                await expect(quarterButtons.nth(i)).toHaveText(expectedLabels[i]);
            }
        });

        test('should set date fields when quarter is clicked', async ({ page }) => {
            await navigateViaMenu(page, '/rechnungen');
            await page.locator('h1').filter({ hasText: /Rechnungen/i }).waitFor({ state: 'visible', timeout: 10000 });

            const quarterButtons = page.locator('.zev-quarter-button');
            const dateFromInput = page.locator('#dateFrom');
            const dateToInput = page.locator('#dateTo');

            // Click the first quarter button (oldest)
            const firstButton = quarterButtons.first();
            const firstLabel = await firstButton.textContent();
            const expectedRange = getQuarterDateRange(firstLabel!.trim());

            await firstButton.click();

            // Verify date fields are set correctly
            await expect(dateFromInput).toHaveValue(expectedRange.von);
            await expect(dateToInput).toHaveValue(expectedRange.bis);
        });

        test('should highlight active quarter button', async ({ page }) => {
            await navigateViaMenu(page, '/rechnungen');
            await page.locator('h1').filter({ hasText: /Rechnungen/i }).waitFor({ state: 'visible', timeout: 10000 });

            const quarterButtons = page.locator('.zev-quarter-button');

            // Click the second quarter button
            await quarterButtons.nth(1).click();

            // Verify it has the active class
            await expect(quarterButtons.nth(1)).toHaveClass(/zev-quarter-button--active/);

            // Verify other buttons don't have active class
            await expect(quarterButtons.nth(0)).not.toHaveClass(/zev-quarter-button--active/);
            await expect(quarterButtons.nth(2)).not.toHaveClass(/zev-quarter-button--active/);
        });

        test('should allow manual date adjustment after quarter selection', async ({ page }) => {
            await navigateViaMenu(page, '/rechnungen');
            await page.locator('h1').filter({ hasText: /Rechnungen/i }).waitFor({ state: 'visible', timeout: 10000 });

            const quarterButtons = page.locator('.zev-quarter-button');
            const dateFromInput = page.locator('#dateFrom');
            const dateToInput = page.locator('#dateTo');

            // Select a quarter
            await quarterButtons.first().click();

            // Manually change the date
            await dateFromInput.fill('2024-01-15');

            // Date should be updated
            await expect(dateFromInput).toHaveValue('2024-01-15');

            // Quarter button should no longer be active (dates don't match)
            await expect(quarterButtons.first()).not.toHaveClass(/zev-quarter-button--active/);
        });
    });

    test.describe('on Statistik page', () => {
        test('should display quarter selector', async ({ page }) => {
            await navigateViaMenu(page, '/statistik');
            await page.locator('h1').filter({ hasText: /Statistik/i }).waitFor({ state: 'visible', timeout: 10000 });

            const quarterSelector = page.locator('.zev-quarter-selector');
            await expect(quarterSelector).toBeVisible();

            const quarterButtons = page.locator('.zev-quarter-button');
            await expect(quarterButtons).toHaveCount(5);
        });

        test('should set date fields when quarter is clicked', async ({ page }) => {
            await navigateViaMenu(page, '/statistik');
            await page.locator('h1').filter({ hasText: /Statistik/i }).waitFor({ state: 'visible', timeout: 10000 });

            const quarterButtons = page.locator('.zev-quarter-button');
            const dateFromInput = page.locator('#dateFrom');
            const dateToInput = page.locator('#dateTo');

            // Click the last quarter button (current quarter)
            const lastButton = quarterButtons.last();
            const lastLabel = await lastButton.textContent();
            const expectedRange = getQuarterDateRange(lastLabel!.trim());

            await lastButton.click();

            await expect(dateFromInput).toHaveValue(expectedRange.von);
            await expect(dateToInput).toHaveValue(expectedRange.bis);
        });
    });

    test.describe('on Messwerte Chart page', () => {
        test('should display quarter selector', async ({ page }) => {
            await navigateViaMenu(page, '/chart');
            await page.locator('h1').filter({ hasText: /Messwerte|Grafik/i }).waitFor({ state: 'visible', timeout: 10000 });

            const quarterSelector = page.locator('.zev-quarter-selector');
            await expect(quarterSelector).toBeVisible();

            const quarterButtons = page.locator('.zev-quarter-button');
            await expect(quarterButtons).toHaveCount(5);
        });

        test('should set date fields when quarter is clicked', async ({ page }) => {
            await navigateViaMenu(page, '/chart');
            await page.locator('h1').filter({ hasText: /Messwerte|Grafik/i }).waitFor({ state: 'visible', timeout: 10000 });

            const quarterButtons = page.locator('.zev-quarter-button');
            const dateFromInput = page.locator('#dateFrom');
            const dateToInput = page.locator('#dateTo');

            // Click a middle quarter button
            const middleButton = quarterButtons.nth(2);
            const middleLabel = await middleButton.textContent();
            const expectedRange = getQuarterDateRange(middleLabel!.trim());

            await middleButton.click();

            await expect(dateFromInput).toHaveValue(expectedRange.von);
            await expect(dateToInput).toHaveValue(expectedRange.bis);
        });
    });

    test.describe('on Solar Calculation page', () => {
        test('should display quarter selector', async ({ page }) => {
            await navigateViaMenu(page, '/solar-calculation');
            await page.locator('h1').filter({ hasText: /Solar|Berechnung/i }).waitFor({ state: 'visible', timeout: 10000 });

            const quarterSelector = page.locator('.zev-quarter-selector');
            await expect(quarterSelector).toBeVisible();

            const quarterButtons = page.locator('.zev-quarter-button');
            await expect(quarterButtons).toHaveCount(5);
        });

        test('should set date fields when quarter is clicked', async ({ page }) => {
            await navigateViaMenu(page, '/solar-calculation');
            await page.locator('h1').filter({ hasText: /Solar|Berechnung/i }).waitFor({ state: 'visible', timeout: 10000 });

            const quarterButtons = page.locator('.zev-quarter-button');
            const dateFromInput = page.locator('#dateFrom');
            const dateToInput = page.locator('#dateTo');

            // Click first quarter button
            const firstButton = quarterButtons.first();
            const firstLabel = await firstButton.textContent();
            const expectedRange = getQuarterDateRange(firstLabel!.trim());

            await firstButton.click();

            await expect(dateFromInput).toHaveValue(expectedRange.von);
            await expect(dateToInput).toHaveValue(expectedRange.bis);
        });
    });
});
