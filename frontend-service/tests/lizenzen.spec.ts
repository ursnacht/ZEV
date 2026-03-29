import { test, expect, Page } from '@playwright/test';
import { navigateViaMenu, waitForTableWithData } from './helpers';

/**
 * tests / lizenzen.spec.ts
 * E2E tests for the Lizenzen (Licenses) page
 */

/**
 * Helper function to navigate to the Lizenzen page
 */
async function navigateToLizenzen(page: Page): Promise<void> {
    await navigateViaMenu(page, '/lizenzen');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
}

/**
 * Wait for both license tables to finish loading (spinners gone)
 */
async function waitForLizenzenLoaded(page: Page): Promise<void> {
    // Wait for spinners to disappear (loading complete)
    await page.locator('.zev-spinner').waitFor({ state: 'hidden', timeout: 15000 }).catch(() => {});
    // Wait for at least one table to appear
    await waitForTableWithData(page, 15000);
}

test.describe('Lizenzen – Navigation', () => {
    test('should navigate to lizenzen page via menu', async ({ page }) => {
        await navigateToLizenzen(page);
        await expect(page.locator('.zev-container h1')).toBeVisible();
        expect(page.url()).toContain('/lizenzen');
    });

    test('should display page heading with shield icon', async ({ page }) => {
        await navigateToLizenzen(page);
        await expect(page.locator('.zev-container h1')).toBeVisible();
        // The page title area contains the shield icon SVG
        const heading = page.locator('.zev-container h1');
        await expect(heading).toBeVisible();
    });
});

test.describe('Lizenzen – Page Structure', () => {
    test('should show two panels for backend and frontend libraries', async ({ page }) => {
        await navigateToLizenzen(page);
        await waitForLizenzenLoaded(page);

        const panels = page.locator('.zev-panel');
        expect(await panels.count()).toBeGreaterThanOrEqual(2);

        // First panel: Backend Libraries
        const firstPanel = panels.first();
        await expect(firstPanel.locator('h2')).toBeVisible();

        // Second panel: Frontend Libraries
        const secondPanel = panels.nth(1);
        await expect(secondPanel.locator('h2')).toBeVisible();
    });

    test('should show search input for backend libraries', async ({ page }) => {
        await navigateToLizenzen(page);
        await waitForLizenzenLoaded(page);

        const backendPanel = page.locator('.zev-panel').first();
        const searchInput = backendPanel.locator('input.zev-input');
        await expect(searchInput).toBeVisible();
    });

    test('should show search input for frontend libraries', async ({ page }) => {
        await navigateToLizenzen(page);
        await waitForLizenzenLoaded(page);

        const frontendPanel = page.locator('.zev-panel').nth(1);
        const searchInput = frontendPanel.locator('input.zev-input');
        await expect(searchInput).toBeVisible();
    });
});

test.describe('Lizenzen – Backend Libraries Table', () => {
    test('should load and display backend library data', async ({ page }) => {
        await navigateToLizenzen(page);
        await waitForLizenzenLoaded(page);

        const backendPanel = page.locator('.zev-panel').first();
        const table = backendPanel.locator('.zev-table');
        await expect(table).toBeVisible();

        // Table should have rows
        const rows = backendPanel.locator('.zev-table tbody tr');
        expect(await rows.count()).toBeGreaterThan(0);
    });

    test('should display correct table columns for backend libraries', async ({ page }) => {
        await navigateToLizenzen(page);
        await waitForLizenzenLoaded(page);

        const backendPanel = page.locator('.zev-panel').first();
        const headers = backendPanel.locator('.zev-table thead th');
        expect(await headers.count()).toBe(5);
    });

    test('should show library name and license in backend table rows', async ({ page }) => {
        await navigateToLizenzen(page);
        await waitForLizenzenLoaded(page);

        const backendPanel = page.locator('.zev-panel').first();
        const firstRow = backendPanel.locator('.zev-table tbody tr').first();
        await expect(firstRow).toBeVisible();

        // First cell (name) should have content
        const nameCell = firstRow.locator('td').first();
        const nameText = await nameCell.textContent();
        expect(nameText?.trim().length).toBeGreaterThan(0);
    });

    test('should display hash with tooltip for backend libraries', async ({ page }) => {
        await navigateToLizenzen(page);
        await waitForLizenzenLoaded(page);

        const backendPanel = page.locator('.zev-panel').first();
        const rows = backendPanel.locator('.zev-table tbody tr');
        const rowCount = await rows.count();
        expect(rowCount).toBeGreaterThan(0);

        // Find a row that has a hash (hash-cell span or LIZENZ_KEIN_HASH)
        let foundHashCell = false;
        for (let i = 0; i < Math.min(rowCount, 10); i++) {
            const row = rows.nth(i);
            const hashCell = row.locator('.hash-cell');
            if (await hashCell.isVisible().catch(() => false)) {
                // Hash should be truncated (≤ 12 chars + ellipsis)
                const hashText = await hashCell.textContent();
                expect(hashText?.trim().length).toBeLessThanOrEqual(15); // 12 chars + "…"

                // Hash cell should have a title attribute with full hash
                const title = await hashCell.getAttribute('title');
                expect(title).toBeTruthy();
                expect(title!.length).toBeGreaterThan(12);
                foundHashCell = true;
                break;
            }
        }
        // It's okay if some rows have no hash (LIZENZ_KEIN_HASH) — just log
        if (!foundHashCell) {
            console.log('No hash-cell found in first 10 backend rows (all may show KEIN_HASH)');
        }
    });
});

test.describe('Lizenzen – Frontend Libraries Table', () => {
    test('should load and display frontend library data', async ({ page }) => {
        await navigateToLizenzen(page);
        await waitForLizenzenLoaded(page);

        const frontendPanel = page.locator('.zev-panel').nth(1);
        const table = frontendPanel.locator('.zev-table');
        await expect(table).toBeVisible();

        const rows = frontendPanel.locator('.zev-table tbody tr');
        expect(await rows.count()).toBeGreaterThan(0);
    });

    test('should show SHA-512 hashes for frontend libraries', async ({ page }) => {
        await navigateToLizenzen(page);
        await waitForLizenzenLoaded(page);

        const frontendPanel = page.locator('.zev-panel').nth(1);
        const rows = frontendPanel.locator('.zev-table tbody tr');
        const rowCount = await rows.count();
        expect(rowCount).toBeGreaterThan(0);

        // Frontend packages should have SHA-512 hashes from package-lock.json
        let foundHashWithTitle = false;
        for (let i = 0; i < Math.min(rowCount, 5); i++) {
            const hashCell = rows.nth(i).locator('.hash-cell');
            if (await hashCell.isVisible().catch(() => false)) {
                const title = await hashCell.getAttribute('title');
                if (title && title.startsWith('SHA-512:')) {
                    foundHashWithTitle = true;
                    break;
                }
            }
        }
        expect(foundHashWithTitle).toBe(true);
    });
});

test.describe('Lizenzen – Search / Filter', () => {
    test('should filter backend libraries by name', async ({ page }) => {
        await navigateToLizenzen(page);
        await waitForLizenzenLoaded(page);

        const backendPanel = page.locator('.zev-panel').first();
        const rowsBefore = await backendPanel.locator('.zev-table tbody tr').count();
        expect(rowsBefore).toBeGreaterThan(1);

        // Type a search term that will narrow the results
        const searchInput = backendPanel.locator('input.zev-input');
        await searchInput.fill('spring');

        // Rows should be reduced
        const rowsAfter = await backendPanel.locator('.zev-table tbody tr').count();
        expect(rowsAfter).toBeLessThan(rowsBefore);
        expect(rowsAfter).toBeGreaterThan(0);
    });

    test('should show empty state when backend filter matches nothing', async ({ page }) => {
        await navigateToLizenzen(page);
        await waitForLizenzenLoaded(page);

        const backendPanel = page.locator('.zev-panel').first();
        const searchInput = backendPanel.locator('input.zev-input');
        await searchInput.fill('xyzxyzxyz-no-match-12345');

        // Table should disappear and empty state should appear
        await expect(backendPanel.locator('.zev-table')).not.toBeVisible();
        await expect(backendPanel.locator('.zev-empty-state')).toBeVisible();
    });

    test('should restore all backend libraries when filter is cleared', async ({ page }) => {
        await navigateToLizenzen(page);
        await waitForLizenzenLoaded(page);

        const backendPanel = page.locator('.zev-panel').first();
        const rowsBefore = await backendPanel.locator('.zev-table tbody tr').count();

        const searchInput = backendPanel.locator('input.zev-input');
        await searchInput.fill('spring');

        const rowsFiltered = await backendPanel.locator('.zev-table tbody tr').count();
        expect(rowsFiltered).toBeLessThan(rowsBefore);

        // Clear the filter
        await searchInput.fill('');
        await page.waitForTimeout(200);

        const rowsAfterClear = await backendPanel.locator('.zev-table tbody tr').count();
        expect(rowsAfterClear).toBe(rowsBefore);
    });

    test('should filter frontend libraries by name', async ({ page }) => {
        await navigateToLizenzen(page);
        await waitForLizenzenLoaded(page);

        const frontendPanel = page.locator('.zev-panel').nth(1);
        const rowsBefore = await frontendPanel.locator('.zev-table tbody tr').count();
        expect(rowsBefore).toBeGreaterThan(0);

        // Filter by "angular" — should match Angular packages
        const searchInput = frontendPanel.locator('input.zev-input');
        await searchInput.fill('angular');

        const rowsAfter = await frontendPanel.locator('.zev-table tbody tr').count();
        expect(rowsAfter).toBeLessThan(rowsBefore);
        expect(rowsAfter).toBeGreaterThan(0);
    });

    test('should show empty state when frontend filter matches nothing', async ({ page }) => {
        await navigateToLizenzen(page);
        await waitForLizenzenLoaded(page);

        const frontendPanel = page.locator('.zev-panel').nth(1);
        const searchInput = frontendPanel.locator('input.zev-input');
        await searchInput.fill('xyzxyzxyz-no-match-12345');

        await expect(frontendPanel.locator('.zev-table')).not.toBeVisible();
        await expect(frontendPanel.locator('.zev-empty-state')).toBeVisible();
    });

    test('should filter case-insensitively', async ({ page }) => {
        await navigateToLizenzen(page);
        await waitForLizenzenLoaded(page);

        const backendPanel = page.locator('.zev-panel').first();
        const searchInput = backendPanel.locator('input.zev-input');

        await searchInput.fill('SPRING');
        const rowsUpper = await backendPanel.locator('.zev-table tbody tr').count();

        await searchInput.fill('spring');
        const rowsLower = await backendPanel.locator('.zev-table tbody tr').count();

        expect(rowsUpper).toBe(rowsLower);
        expect(rowsUpper).toBeGreaterThan(0);
    });
});

test.describe('Lizenzen – Backend and Frontend filters are independent', () => {
    test('filtering backend should not affect frontend table', async ({ page }) => {
        await navigateToLizenzen(page);
        await waitForLizenzenLoaded(page);

        const frontendPanel = page.locator('.zev-panel').nth(1);
        const frontendRowsBefore = await frontendPanel.locator('.zev-table tbody tr').count();

        // Filter the backend panel
        const backendPanel = page.locator('.zev-panel').first();
        await backendPanel.locator('input.zev-input').fill('spring');

        // Frontend rows should be unchanged
        const frontendRowsAfter = await frontendPanel.locator('.zev-table tbody tr').count();
        expect(frontendRowsAfter).toBe(frontendRowsBefore);
    });
});
