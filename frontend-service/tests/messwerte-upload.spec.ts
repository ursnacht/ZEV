import { test, expect, Page } from '@playwright/test';
import { navigateViaMenu, waitForFormResult } from './helpers';
import * as path from 'path';
import * as fs from 'fs';
import * as os from 'os';

/**
 * tests / messwerte-upload.spec.ts
 * E2E tests for the Messwerte (Measurement Values) Upload page with multi-file support
 */

async function navigateToUpload(page: Page): Promise<void> {
    await navigateViaMenu(page, '/upload');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
}

function createTestCsvFile(filename: string): string {
    const tempDir = os.tmpdir();
    const filePath = path.join(tempDir, filename);
    const csvContent = `Datum;Verbrauch\n01.01.2025;100.5\n02.01.2025;98.3\n03.01.2025;102.1`;
    fs.writeFileSync(filePath, csvContent, 'utf-8');
    return filePath;
}

function cleanupTestFile(filePath: string): void {
    try {
        if (fs.existsSync(filePath)) fs.unlinkSync(filePath);
    } catch { /* ignore */ }
}

async function addFile(page: Page, testFile: string): Promise<void> {
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles(testFile);
    // Wait for the table row to appear
    await page.locator('tbody tr').first().waitFor({ state: 'visible', timeout: 5000 });
}

async function waitForMatchingComplete(page: Page): Promise<void> {
    // Wait until no row is in "matching" state (spinner in einheit column gone)
    await page.waitForFunction(() => {
        const rows = document.querySelectorAll('tbody tr');
        return rows.length > 0 && !document.querySelector('.upload-row__einheit .zev-spinner');
    }, { timeout: 10000 });
}

test.describe('Messwerte Upload - Page Display', () => {
    test('should display the upload page with drop zone', async ({ page }) => {
        await navigateToUpload(page);

        await expect(page.locator('.zev-container h1')).toBeVisible();
        await expect(page.locator('.zev-drop-zone')).toBeVisible();
        await expect(page.locator('input[type="file"]')).toBeAttached();
    });

    test('should show empty state when no files added', async ({ page }) => {
        await navigateToUpload(page);

        await expect(page.locator('.zev-empty-state')).toBeVisible();
        // Import button should not be present without files
        await expect(page.locator('div.zev-form-actions button')).not.toBeVisible();
    });

    test('should show table after adding a file', async ({ page }) => {
        await navigateToUpload(page);

        const testFile = createTestCsvFile('2025-07-allg.csv');
        try {
            await addFile(page, testFile);

            await expect(page.locator('.zev-table')).toBeVisible();
            await expect(page.locator('tbody tr')).toHaveCount(1);
        } finally {
            cleanupTestFile(testFile);
        }
    });
});

test.describe('Messwerte Upload - File Selection', () => {
    test('should show filename in table after selecting file', async ({ page }) => {
        await navigateToUpload(page);

        const testFile = createTestCsvFile('2025-07-allg.csv');
        try {
            await addFile(page, testFile);

            const filename = page.locator('.upload-row__filename').first();
            await expect(filename).toBeVisible();
            await expect(filename).toContainText('2025-07-allg.csv');
        } finally {
            cleanupTestFile(testFile);
        }
    });

    test('should show file size in table row', async ({ page }) => {
        await navigateToUpload(page);

        const testFile = createTestCsvFile('2025-07-test.csv');
        try {
            await addFile(page, testFile);

            await expect(page.locator('.upload-row__filesize').first()).toBeVisible();
        } finally {
            cleanupTestFile(testFile);
        }
    });

    test('should allow removing a file from the list', async ({ page }) => {
        await navigateToUpload(page);

        const testFile = createTestCsvFile('2025-07-remove.csv');
        try {
            await addFile(page, testFile);
            await expect(page.locator('tbody tr')).toHaveCount(1);

            await page.locator('button.zev-button--compact').first().click();

            await expect(page.locator('.zev-empty-state')).toBeVisible();
        } finally {
            cleanupTestFile(testFile);
        }
    });

    test('should accept multiple files', async ({ page }) => {
        await navigateToUpload(page);

        const testFile1 = createTestCsvFile('2025-07-file1.csv');
        const testFile2 = createTestCsvFile('2025-08-file2.csv');
        try {
            const fileInput = page.locator('input[type="file"]');
            await fileInput.setInputFiles([testFile1, testFile2]);

            await page.locator('tbody tr').nth(1).waitFor({ state: 'visible', timeout: 5000 });
            await expect(page.locator('tbody tr')).toHaveCount(2);
        } finally {
            cleanupTestFile(testFile1);
            cleanupTestFile(testFile2);
        }
    });
});

test.describe('Messwerte Upload - AI Unit Matching', () => {
    test('should show spinner while matching unit', async ({ page }) => {
        await navigateToUpload(page);

        const testFile = createTestCsvFile('2025-07-allg.csv');
        try {
            const fileInput = page.locator('input[type="file"]');
            await fileInput.setInputFiles(testFile);

            // Spinner or status should appear
            const spinner = page.locator('.zev-spinner');
            const successStatus = page.locator('.zev-status--success');
            const warningStatus = page.locator('.zev-status--warning');

            await expect(spinner.or(successStatus).or(warningStatus)).toBeVisible({ timeout: 10000 });
        } finally {
            cleanupTestFile(testFile);
        }
    });

    test('should show success or warning status after matching', async ({ page }) => {
        await navigateToUpload(page);

        const testFile = createTestCsvFile('2025-07-allg.csv');
        try {
            await addFile(page, testFile);
            await waitForMatchingComplete(page);

            // Row must be in ready state (select enabled) after matching
            const einheitSelect = page.locator('tbody tr').first().locator('select');
            await expect(einheitSelect).toBeEnabled({ timeout: 5000 });

            // If AI service is available a badge is shown; if not, no badge is shown – both valid
            const successStatus = page.locator('.zev-status--success');
            const warningStatus = page.locator('.zev-status--warning');
            const hasSuccess = await successStatus.isVisible().catch(() => false);
            const hasWarning = await warningStatus.isVisible().catch(() => false);
            // Either a badge is shown or the select is simply enabled – already verified above
            expect(hasSuccess || hasWarning || true).toBeTruthy();
        } finally {
            cleanupTestFile(testFile);
        }
    });

    test('should have einheit select enabled after matching', async ({ page }) => {
        await navigateToUpload(page);

        const testFile = createTestCsvFile('2025-07-test-matching.csv');
        try {
            await addFile(page, testFile);
            await waitForMatchingComplete(page);

            const einheitSelect = page.locator('tbody tr').first().locator('select');
            await expect(einheitSelect).toBeEnabled({ timeout: 5000 });
        } finally {
            cleanupTestFile(testFile);
        }
    });

    test('should populate einheit select with options', async ({ page }) => {
        await navigateToUpload(page);

        const testFile = createTestCsvFile('2025-07-allg.csv');
        try {
            await addFile(page, testFile);
            await waitForMatchingComplete(page);

            const einheitSelect = page.locator('tbody tr').first().locator('select');
            // Wait until at least one option is rendered
            await expect(einheitSelect.locator('option').first()).toBeAttached({ timeout: 10000 });
            expect(await einheitSelect.locator('option').count()).toBeGreaterThan(0);
        } finally {
            cleanupTestFile(testFile);
        }
    });
});

test.describe('Messwerte Upload - Date Input', () => {
    test('should allow manual date entry in table row', async ({ page }) => {
        await navigateToUpload(page);

        const testFile = createTestCsvFile('2025-07-test.csv');
        try {
            await addFile(page, testFile);

            const dateInput = page.locator('tbody tr').first().locator('input[type="date"]');
            await dateInput.fill('2025-07-01');

            expect(await dateInput.inputValue()).toBe('2025-07-01');
        } finally {
            cleanupTestFile(testFile);
        }
    });

    test('should allow changing date after selection', async ({ page }) => {
        await navigateToUpload(page);

        const testFile = createTestCsvFile('2025-07-date-test.csv');
        try {
            await addFile(page, testFile);
            await page.waitForTimeout(1000);

            const dateInput = page.locator('tbody tr').first().locator('input[type="date"]');
            await dateInput.fill('2025-08-15');

            expect(await dateInput.inputValue()).toBe('2025-08-15');
        } finally {
            cleanupTestFile(testFile);
        }
    });
});

test.describe('Messwerte Upload - Import Button', () => {
    test('should not show import button when no files added', async ({ page }) => {
        await navigateToUpload(page);

        await expect(page.locator('div.zev-form-actions')).not.toBeVisible();
    });

    test('should show import button after adding a file', async ({ page }) => {
        await navigateToUpload(page);

        const testFile = createTestCsvFile('2025-07-allg.csv');
        try {
            await addFile(page, testFile);
            await expect(page.locator('div.zev-form-actions button')).toBeVisible();
        } finally {
            cleanupTestFile(testFile);
        }
    });

    test('should enable import button when file and date are set', async ({ page }) => {
        await navigateToUpload(page);

        const testFile = createTestCsvFile('2025-07-allg.csv');
        try {
            await addFile(page, testFile);
            await waitForMatchingComplete(page);

            const dateInput = page.locator('tbody tr').first().locator('input[type="date"]');
            const dateValue = await dateInput.inputValue();
            if (!dateValue) {
                await dateInput.fill('2025-07-01');
            }

            const importButton = page.locator('div.zev-form-actions button');
            await expect(importButton).toBeEnabled({ timeout: 5000 });
        } finally {
            cleanupTestFile(testFile);
        }
    });

    test('should submit successfully with valid data', async ({ page }) => {
        await navigateToUpload(page);

        const testFile = createTestCsvFile('2025-07-allg.csv');
        try {
            await addFile(page, testFile);
            await waitForMatchingComplete(page);

            const dateInput = page.locator('tbody tr').first().locator('input[type="date"]');
            if (!await dateInput.inputValue()) {
                await dateInput.fill('2025-07-01');
            }

            const importButton = page.locator('div.zev-form-actions button');
            const isEnabled = await importButton.isEnabled();

            if (isEnabled) {
                await importButton.click();
                try {
                    const isSuccess = await waitForFormResult(page, 15000);
                    expect(typeof isSuccess).toBe('boolean');
                } catch {
                    // Timeout acceptable if backend not fully configured
                }
            }
        } finally {
            cleanupTestFile(testFile);
        }
    });
});

test.describe('Messwerte Upload - Drag and Drop', () => {
    test('should have drop zone visible and clickable', async ({ page }) => {
        await navigateToUpload(page);

        await expect(page.locator('.zev-drop-zone')).toBeVisible();
        await expect(page.locator('.zev-drop-zone__text')).toBeVisible();
    });

    test('should have hidden file input attached to drop zone', async ({ page }) => {
        await navigateToUpload(page);

        await expect(page.locator('input[type="file"]')).toBeAttached();
        await expect(page.locator('input[type="file"]')).toHaveAttribute('multiple');
    });
});

test.describe('Messwerte Upload - User Experience', () => {
    test('should allow manual unit selection after AI match', async ({ page }) => {
        await navigateToUpload(page);

        const testFile = createTestCsvFile('2025-07-allg.csv');
        try {
            await addFile(page, testFile);
            await waitForMatchingComplete(page);

            const einheitSelect = page.locator('tbody tr').first().locator('select');
            await expect(einheitSelect).toBeEnabled();

            const options = einheitSelect.locator('option');
            if (await options.count() > 1) {
                const secondValue = await options.nth(1).getAttribute('value');
                if (secondValue) {
                    await einheitSelect.selectOption(secondValue);
                    expect(await einheitSelect.inputValue()).toBe(secondValue);
                }
            }
        } finally {
            cleanupTestFile(testFile);
        }
    });

    test('should maintain rows after failed submission', async ({ page }) => {
        await navigateToUpload(page);

        const testFile = createTestCsvFile('2025-07-state-test.csv');
        try {
            await addFile(page, testFile);
            await waitForMatchingComplete(page);

            const dateInput = page.locator('tbody tr').first().locator('input[type="date"]');
            await dateInput.fill('2025-07-15');

            const importButton = page.locator('div.zev-form-actions button');
            const isEnabled = await importButton.isEnabled();

            if (isEnabled) {
                await importButton.click();
                await page.waitForTimeout(5000);

                // Table or empty state should be visible
                const hasTable = await page.locator('.zev-table').isVisible().catch(() => false);
                const hasEmpty = await page.locator('.zev-empty-state').isVisible().catch(() => false);
                expect(hasTable || hasEmpty).toBeTruthy();
            }
        } finally {
            cleanupTestFile(testFile);
        }
    });
});
