import { test, expect, Page } from '@playwright/test';
import { navigateViaMenu, waitForFormResult } from './helpers';

/**
 * tests / einstellungen.spec.ts
 * E2E tests for the Einstellungen (Settings) page
 */

/**
 * Helper function to navigate to Einstellungen page
 */
async function navigateToEinstellungen(page: Page): Promise<void> {
    await navigateViaMenu(page, '/einstellungen');

    // Wait for page to load (either form or loading indicator)
    const container = page.locator('.zev-container h1');
    await container.waitFor({ state: 'visible', timeout: 15000 });

    // Wait for loading to finish
    const loadingText = page.locator('p:has-text("...")');
    try {
        await loadingText.waitFor({ state: 'hidden', timeout: 10000 });
    } catch {
        // Loading may already be done
    }
}

/**
 * Helper to fill the settings form
 */
async function fillEinstellungenForm(page: Page, data: {
    zahlungsfrist: string;
    iban: string;
    stellerName: string;
    stellerStrasse: string;
    stellerPlz: string;
    stellerOrt: string;
}): Promise<void> {
    await page.locator('#zahlungsfrist').fill(data.zahlungsfrist);
    await page.locator('#iban').fill(data.iban);
    await page.locator('#stellerName').fill(data.stellerName);
    await page.locator('#stellerStrasse').fill(data.stellerStrasse);
    await page.locator('#stellerPlz').fill(data.stellerPlz);
    await page.locator('#stellerOrt').fill(data.stellerOrt);
}

test.describe('Einstellungen - Navigation and Display', () => {
    test('should navigate to Einstellungen page and display form', async ({ page }) => {
        await navigateToEinstellungen(page);

        // Check for page title
        const title = page.locator('.zev-container h1');
        await expect(title).toBeVisible();

        // Check that form is visible (after loading completes)
        const form = page.locator('form');
        await expect(form).toBeVisible({ timeout: 10000 });

        // Check for all required form fields
        await expect(page.locator('#zahlungsfrist')).toBeVisible();
        await expect(page.locator('#iban')).toBeVisible();
        await expect(page.locator('#stellerName')).toBeVisible();
        await expect(page.locator('#stellerStrasse')).toBeVisible();
        await expect(page.locator('#stellerPlz')).toBeVisible();
        await expect(page.locator('#stellerOrt')).toBeVisible();

        // Check for Rechnungssteller section title
        const sectionTitle = page.locator('.zev-form-section__title');
        await expect(sectionTitle).toBeVisible();

        // Check for save button
        const saveButton = page.locator('button[type="submit"]');
        await expect(saveButton).toBeVisible();
    });
});

test.describe('Einstellungen - Save Settings', () => {
    test('should save settings and show success message', async ({ page }) => {
        await navigateToEinstellungen(page);

        // Wait for form to be visible
        const form = page.locator('form');
        await expect(form).toBeVisible({ timeout: 10000 });

        // Fill in the form with test data
        await fillEinstellungenForm(page, {
            zahlungsfrist: '30 Tage',
            iban: 'CH7006300016946459910',
            stellerName: 'Test Steller',
            stellerStrasse: 'Teststrasse 1',
            stellerPlz: '3000',
            stellerOrt: 'Bern'
        });

        // Save button should now be enabled
        const saveButton = page.locator('button[type="submit"]');
        await expect(saveButton).toBeEnabled();

        // Submit the form
        await saveButton.click();

        // Wait for success message
        const isSuccess = await waitForFormResult(page, 15000);
        expect(isSuccess).toBeTruthy();

        // Success message should be visible
        const successMessage = page.locator('.zev-message--success');
        await expect(successMessage).toBeVisible();

        // Reload the page and verify the data was persisted
        await navigateToEinstellungen(page);
        await expect(form).toBeVisible({ timeout: 10000 });

        // Verify that saved values are loaded back
        await expect(page.locator('#zahlungsfrist')).toHaveValue('30 Tage');
        await expect(page.locator('#iban')).toHaveValue('CH7006300016946459910');
        await expect(page.locator('#stellerName')).toHaveValue('Test Steller');
        await expect(page.locator('#stellerStrasse')).toHaveValue('Teststrasse 1');
        await expect(page.locator('#stellerPlz')).toHaveValue('3000');
        await expect(page.locator('#stellerOrt')).toHaveValue('Bern');
    });
});
