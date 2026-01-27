import { test, expect, Page } from '@playwright/test';
import { navigateViaMenu, waitForFormResult } from './helpers';

/**
 * tests / einstellungen.spec.ts
 * E2E tests for the Einstellungen (Settings) page
 */

interface EinstellungenFormData {
    zahlungsfrist: string;
    iban: string;
    stellerName: string;
    stellerStrasse: string;
    stellerPlz: string;
    stellerOrt: string;
}

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
 * Helper to read current form values
 */
async function readEinstellungenForm(page: Page): Promise<EinstellungenFormData> {
    return {
        zahlungsfrist: await page.locator('#zahlungsfrist').inputValue(),
        iban: await page.locator('#iban').inputValue(),
        stellerName: await page.locator('#stellerName').inputValue(),
        stellerStrasse: await page.locator('#stellerStrasse').inputValue(),
        stellerPlz: await page.locator('#stellerPlz').inputValue(),
        stellerOrt: await page.locator('#stellerOrt').inputValue()
    };
}

/**
 * Helper to fill the settings form
 */
async function fillEinstellungenForm(page: Page, data: EinstellungenFormData): Promise<void> {
    await page.locator('#zahlungsfrist').fill(data.zahlungsfrist);
    await page.locator('#iban').fill(data.iban);
    await page.locator('#stellerName').fill(data.stellerName);
    await page.locator('#stellerStrasse').fill(data.stellerStrasse);
    await page.locator('#stellerPlz').fill(data.stellerPlz);
    await page.locator('#stellerOrt').fill(data.stellerOrt);
}

/**
 * Helper to save the current form
 */
async function submitEinstellungenForm(page: Page): Promise<boolean> {
    const saveButton = page.locator('button[type="submit"]');
    await saveButton.click();
    try {
        return await waitForFormResult(page, 15000);
    } catch {
        return false;
    }
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

        // Step 1: Read and store the original settings
        const originalData = await readEinstellungenForm(page);

        // Step 2: Fill in the form with test data
        const testData: EinstellungenFormData = {
            zahlungsfrist: '30 Tage',
            iban: 'CH7006300016946459910',
            stellerName: 'Test Steller',
            stellerStrasse: 'Teststrasse 1',
            stellerPlz: '3000',
            stellerOrt: 'Bern'
        };
        await fillEinstellungenForm(page, testData);

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
        await expect(page.locator('#zahlungsfrist')).toHaveValue(testData.zahlungsfrist);
        await expect(page.locator('#iban')).toHaveValue(testData.iban);
        await expect(page.locator('#stellerName')).toHaveValue(testData.stellerName);
        await expect(page.locator('#stellerStrasse')).toHaveValue(testData.stellerStrasse);
        await expect(page.locator('#stellerPlz')).toHaveValue(testData.stellerPlz);
        await expect(page.locator('#stellerOrt')).toHaveValue(testData.stellerOrt);

        // Step 3: Restore original settings
        const hasOriginalData = originalData.zahlungsfrist || originalData.iban || originalData.stellerName;
        if (hasOriginalData) {
            await fillEinstellungenForm(page, originalData);
            await submitEinstellungenForm(page);
        }
    });
});
