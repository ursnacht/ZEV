import { test, expect } from "@playwright/test";
import { navigateViaMenu, clickKebabMenuItem, openKebabMenu } from "./helpers";

/**
 * tests / translation-editor.spec.ts
 * E2E tests for the Translation Editor
 * Updated to use kebab menu instead of direct buttons
 */

/**
 * Helper function to navigate to translation editor
 */
async function navigateToTranslationEditor(page: any) {
    await navigateViaMenu(page, "/translations");
    await page.locator(".new-translation-form").waitFor({ state: "visible", timeout: 10000 });
}

test.describe("Translation Editor - Kebab Menu Functionality", () => {
    test("should display kebab menu for each translation row", async ({ page }) => {
        await navigateToTranslationEditor(page);
        const tableRows = page.locator(".translation-table tbody tr, table tbody tr");
        const rowCount = await tableRows.count();
        if (rowCount > 0) {
            const kebabButtons = page.locator(".zev-kebab-button");
            await expect(kebabButtons.first()).toBeVisible();
        }
    });

    test("should show save and delete options in kebab menu", async ({ page }) => {
        await navigateToTranslationEditor(page);
        const tableRows = page.locator(".translation-table tbody tr, table tbody tr");
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
        const tableRows = page.locator(".translation-table tbody tr, table tbody tr");
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
        
        // Use the correct input selectors based on the actual placeholders
        await page.locator(".new-translation-form input").nth(0).fill(testKey);
        await page.locator(".new-translation-form input").nth(1).fill("E2E Test Loeschen");
        await page.locator(".new-translation-form input").nth(2).fill("E2E Test Delete");
        
        // Click the primary button in the form
        await page.locator(".new-translation-form button.zev-button--primary").click();
        
        await page.waitForTimeout(1000);
        const testRow = page.locator(`tr:has-text("${testKey}")`);
        await expect(testRow).toBeVisible();
        const rowsBefore = await page.locator(".translation-table tbody tr").count();
        
        page.on("dialog", async dialog => {
            await dialog.accept();
        });
        await clickKebabMenuItem(page, testRow, "delete");
        await page.waitForTimeout(1000);
        await expect(testRow).not.toBeVisible();
        const rowsAfter = await page.locator(".translation-table tbody tr").count();
        expect(rowsAfter).toBe(rowsBefore - 1);
    });

    test("should not delete translation when cancelled via kebab menu", async ({ page }) => {
        await navigateToTranslationEditor(page);
        const firstRow = page.locator(".translation-table tbody tr").first();
        const firstKey = await firstRow.locator("td").first().textContent();
        const rowsBefore = await page.locator(".translation-table tbody tr").count();
        
        page.on("dialog", async dialog => {
            await dialog.dismiss();
        });
        await clickKebabMenuItem(page, firstRow, "delete");
        await page.waitForTimeout(500);
        await expect(firstRow).toBeVisible();
        if (firstKey) {
            await expect(page.locator(`tr:has-text("${firstKey}")`)).toBeVisible();
        }
        const rowsAfter = await page.locator(".translation-table tbody tr").count();
        expect(rowsAfter).toBe(rowsBefore);
    });
});

test.describe("Translation Editor - Save Functionality via Kebab Menu", () => {
    test("should save translation changes via kebab menu", async ({ page }) => {
        await navigateToTranslationEditor(page);
        const testKey = `savetest_${Date.now()}`;
        
        // Use the correct input selectors
        await page.locator(".new-translation-form input").nth(0).fill(testKey);
        await page.locator(".new-translation-form input").nth(1).fill("Original DE");
        await page.locator(".new-translation-form input").nth(2).fill("Original EN");
        
        // Click the primary button in the form
        await page.locator(".new-translation-form button.zev-button--primary").click();
        
        await page.waitForTimeout(1000);
        const testRow = page.locator(`tr:has-text("${testKey}")`);
        await expect(testRow).toBeVisible();
        
        // Modify the German translation in the inline input
        const germanInput = testRow.locator("input").first();
        await germanInput.fill("Modified DE");
        
        // Click save via kebab menu (first non-danger item is SAVE)
        await clickKebabMenuItem(page, testRow, "edit");
        await page.waitForTimeout(1000);
        
        // Clean up: delete the test translation
        page.on("dialog", async dialog => {
            await dialog.accept();
        });
        await clickKebabMenuItem(page, testRow, "delete");
        await page.waitForTimeout(1000);
    });
});
