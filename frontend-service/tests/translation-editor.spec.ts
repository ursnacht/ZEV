import { test, expect } from "@playwright/test";
import { navigateViaMenu, clickKebabMenuItem, openKebabMenu } from "./helpers";

/**
 * tests / translation-editor.spec.ts
 * E2E tests for the Translation Editor
 */

/**
 * Helper function to navigate to translation editor
 */
async function navigateToTranslationEditor(page: any) {
    await navigateViaMenu(page, "/translations");
    await page.locator(".zev-form-container").waitFor({ state: "visible", timeout: 10000 });
}

/**
 * Helper to fill the translation form
 */
async function fillTranslationForm(page: any, key: string, deutsch: string, englisch: string) {
    const formContainer = page.locator(".zev-form-container");
    const inputs = formContainer.locator("input.zev-input");
    await inputs.nth(0).fill(key);
    await inputs.nth(1).fill(deutsch);
    await inputs.nth(2).fill(englisch);
}

/**
 * Helper to submit the translation form
 */
async function submitTranslationForm(page: any) {
    await page.locator(".zev-form-container .zev-form-actions button.zev-button--primary").click();
}

test.describe("Translation Editor - Kebab Menu Functionality", () => {
    test("should display kebab menu for each translation row", async ({ page }) => {
        await navigateToTranslationEditor(page);
        const tableRows = page.locator(".zev-table tbody tr");
        const rowCount = await tableRows.count();
        if (rowCount > 0) {
            const kebabButtons = page.locator(".zev-kebab-button");
            await expect(kebabButtons.first()).toBeVisible();
        }
    });

    test("should show save and delete options in kebab menu", async ({ page }) => {
        await navigateToTranslationEditor(page);
        const tableRows = page.locator(".zev-table tbody tr");
        const rowCount = await tableRows.count();
        if (rowCount > 0) {
            const firstRow = tableRows.first();
            await openKebabMenu(page, firstRow);
            const menuItems = firstRow.locator(".zev-kebab-menu__item");
            const itemCount = await menuItems.count();
            expect(itemCount).toBeGreaterThanOrEqual(2);
            const dangerItem = firstRow.locator(".zev-kebab-menu__item--danger");
            await expect(dangerItem).toBeVisible();
        }
    });
});

test.describe("Translation Editor - Delete Functionality via Kebab Menu", () => {
    test("should show confirmation dialog when delete is clicked via kebab menu", async ({ page }) => {
        await navigateToTranslationEditor(page);
        const tableRows = page.locator(".zev-table tbody tr");
        const rowCount = await tableRows.count();
        if (rowCount > 0) {
            const firstRow = tableRows.first();
            let dialogMessage = "";
            page.on("dialog", async dialog => {
                dialogMessage = dialog.message();
                await dialog.dismiss();
            });
            await clickKebabMenuItem(page, firstRow, "delete");
            await page.waitForTimeout(500);
            expect(dialogMessage).toBeTruthy();
        }
    });

    test("should delete translation when confirmed via kebab menu", async ({ page }) => {
        await navigateToTranslationEditor(page);
        const testKey = `testkey_${Date.now()}`;

        // Fill the form to create a test translation
        await fillTranslationForm(page, testKey, "E2E Test Loeschen", "E2E Test Delete");
        await submitTranslationForm(page);

        await page.waitForTimeout(1000);
        const testRow = page.locator(`tr:has-text("${testKey}")`);
        await expect(testRow).toBeVisible();
        const rowsBefore = await page.locator(".zev-table tbody tr").count();

        page.on("dialog", async dialog => {
            await dialog.accept();
        });
        await clickKebabMenuItem(page, testRow, "delete");
        await page.waitForTimeout(1000);
        await expect(testRow).not.toBeVisible();
        const rowsAfter = await page.locator(".zev-table tbody tr").count();
        expect(rowsAfter).toBe(rowsBefore - 1);
    });

    test("should not delete translation when cancelled via kebab menu", async ({ page }) => {
        await navigateToTranslationEditor(page);
        const firstRow = page.locator(".zev-table tbody tr").first();
        const firstKey = await firstRow.locator("td").first().textContent();
        const rowsBefore = await page.locator(".zev-table tbody tr").count();

        page.on("dialog", async dialog => {
            await dialog.dismiss();
        });
        await clickKebabMenuItem(page, firstRow, "delete");
        await page.waitForTimeout(500);
        await expect(firstRow).toBeVisible();
        if (firstKey) {
            await expect(page.locator(`tr:has-text("${firstKey}")`)).toBeVisible();
        }
        const rowsAfter = await page.locator(".zev-table tbody tr").count();
        expect(rowsAfter).toBe(rowsBefore);
    });
});

test.describe("Translation Editor - Save Functionality via Kebab Menu", () => {
    test("should save translation changes via kebab menu", async ({ page }) => {
        await navigateToTranslationEditor(page);
        const testKey = `savetest_${Date.now()}`;

        // Create a test translation
        await fillTranslationForm(page, testKey, "Original DE", "Original EN");
        await submitTranslationForm(page);

        await page.waitForTimeout(1000);
        const testRow = page.locator(`tr:has-text("${testKey}")`);
        await expect(testRow).toBeVisible();

        // Click edit via kebab menu - this populates the form at the top
        await clickKebabMenuItem(page, testRow, "edit");
        await page.waitForTimeout(500);

        // Form should now be in edit mode - modify the German text
        const formContainer = page.locator(".zev-form-container");
        const deutschInput = formContainer.locator("input.zev-input").nth(1);
        await deutschInput.fill("Modified DE");

        // Save via the form's save button
        await submitTranslationForm(page);
        await page.waitForTimeout(1000);

        // Verify the row now shows the modified text
        const updatedRow = page.locator(`tr:has-text("${testKey}")`);
        await expect(updatedRow).toBeVisible();
        await expect(updatedRow.locator("td").nth(1)).toHaveText("Modified DE");

        // Clean up: delete the test translation
        page.on("dialog", async dialog => {
            await dialog.accept();
        });
        await clickKebabMenuItem(page, updatedRow, "delete");
        await page.waitForTimeout(1000);
    });
});
