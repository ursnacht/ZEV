import { test, expect } from '@playwright/test';
import { navigateViaMenu } from './helpers';

/**
 * tests / translation-editor.spec.ts
 * E2E tests for the Translation Editor
 */

/**
 * Helper function to navigate to translation editor
 */
async function navigateToTranslationEditor(page: any) {
    await navigateViaMenu(page, '/translations');

    // Wait for translation editor to load
    await page.locator('.new-translation-form').waitFor({ state: 'visible', timeout: 10000 });
}

test.describe('Translation Editor - Delete Functionality', () => {
    test('should display delete button for each translation', async ({ page }) => {
        await navigateToTranslationEditor(page);

        // Check that delete buttons exist
        const deleteButtons = page.locator('button:has-text("Delete"), button:has-text("Löschen")');
        await expect(deleteButtons.first()).toBeVisible();
    });

    test('should show confirmation dialog when delete button is clicked', async ({ page }) => {
        await navigateToTranslationEditor(page);

        // Set up dialog handler before clicking delete
        let dialogMessage = '';
        page.on('dialog', async dialog => {
            dialogMessage = dialog.message();
            await dialog.dismiss(); // Cancel the deletion
        });

        // Click first delete button
        const deleteButton = page.locator('button:has-text("DELETE"), button:has-text("Löschen")').first();
        await deleteButton.click();

        // Wait a bit for dialog to be handled
        await page.waitForTimeout(500);

        // Verify confirmation dialog was shown
        expect(dialogMessage).toBeTruthy();
        expect(dialogMessage).toContain('löschen'); // German or English "delete"
    });

    test('should delete translation when confirmed', async ({ page }) => {
        // Debug console
        page.on('console', msg => console.log(`BROWSER LOG: ${msg.text()}`));

        await navigateToTranslationEditor(page);

        // Create a test translation first
        const testKey = `testkey_${Date.now()}`;
        await page.fill('input[placeholder*="KEY"], input[placeholder*="Key"]', testKey);
        await page.fill('input[placeholder*="DEUTSCH"], input[placeholder*="Deutsch"]', 'E2E Test Löschen');
        await page.fill('input[placeholder*="ENGLISH"], input[placeholder*="Englisch"]', 'E2E Test Delete');
        await page.locator('button:has-text("ADD"), button:has-text("Hinzufügen")').click();

        // Wait for translation to appear in table
        await page.waitForTimeout(1000);

        // Find the row with our test translation
        const testRow = page.locator(`tr:has-text("${testKey}")`);
        await expect(testRow).toBeVisible();

        // Count rows before deletion
        const rowsBefore = await page.locator('tbody tr').count();

        // Set up dialog handler to accept deletion
        page.on('dialog', async dialog => {
            await dialog.accept();
        });

        // Click delete button for our test translation
        const deleteButton = testRow.locator('button:has-text("DELETE"), button:has-text("Löschen")');
        await deleteButton.click();

        // Wait for deletion to complete
        await page.waitForTimeout(1000);

        // Verify translation is removed from table
        await expect(testRow).not.toBeVisible();

        // Verify row count decreased
        const rowsAfter = await page.locator('tbody tr').count();
        expect(rowsAfter).toBe(rowsBefore - 1);
    });

    test('should not delete translation when cancelled', async ({ page }) => {
        await navigateToTranslationEditor(page);

        // Get first translation key
        const firstRow = page.locator('tbody tr').first();
        const firstKey = await firstRow.locator('td').first().textContent();

        // Count rows before
        const rowsBefore = await page.locator('tbody tr').count();

        // Set up dialog handler to cancel deletion
        page.on('dialog', async dialog => {
            await dialog.dismiss();
        });

        // Click delete button
        const deleteButton = firstRow.locator('button:has-text("DELETE"), button:has-text("Löschen")');
        await deleteButton.click();

        // Wait a bit
        await page.waitForTimeout(500);

        // Verify translation still exists
        await expect(firstRow).toBeVisible();
        await expect(page.locator(`tr:has-text("${firstKey}")`)).toBeVisible();

        // Verify row count unchanged
        const rowsAfter = await page.locator('tbody tr').count();
        expect(rowsAfter).toBe(rowsBefore);
    });
});
