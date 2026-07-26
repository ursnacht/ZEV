import { test, expect, Page, Locator } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { navigateViaMenu } from './helpers';

/**
 * tests / export-messdaten.spec.ts
 * E2E tests for the "Export-Messdaten" feature (Specs/Export-Messdaten.md).
 *
 * Read-only feature: a CSV-download button per CONSUMER row in the
 * "Summen pro Einheit" table on the Statistik page (/statistik).
 * No data is created, so no cleanup is required.
 *
 * Selectors are chosen to be robust against the translation state:
 * the button text comes from the key DOWNLOAD_CSV, which may still render as
 * the raw key if the translation migration (V88) is not yet applied in the
 * running backend. We therefore locate the button via the download icon
 * (<app-icon name="download">) inside the ".zev-einheit-summen-section",
 * never via its display text.
 */

/**
 * Navigate to the Statistik page and load the default period (previous quarter),
 * which the seed data covers with consumer measurements. Returns true if at
 * least one monthly "Summen pro Einheit" section was rendered.
 */
async function loadStatistik(page: Page): Promise<boolean> {
    await navigateViaMenu(page, '/statistik');
    await page.locator('h1').filter({ hasText: /Statistik/i }).waitFor({ state: 'visible', timeout: 15000 });

    // Submit the pre-filled (previous quarter) date range to load the statistics.
    const submitButton = page.locator('button.zev-button--primary[type="submit"]');
    await submitButton.click();
    // Loading finished when the submit button is enabled again.
    await expect(submitButton).toBeEnabled({ timeout: 15000 });

    // Wait for the per-unit summary section (only present when data exists).
    const summenSection = page.locator('.zev-einheit-summen-section').first();
    try {
        await summenSection.waitFor({ state: 'visible', timeout: 10000 });
        return true;
    } catch {
        return false;
    }
}

/** The first month panel that contains a "Summen pro Einheit" section. */
function firstSummenSection(page: Page): Locator {
    return page.locator('.zev-einheit-summen-section').first();
}

/** All CSV-download buttons (download icon) within a summary section. */
function csvButtons(section: Locator): Locator {
    return section.locator('button:has(app-icon[name="download"])');
}

test.describe('Export-Messdaten (CSV-Download je Consumer)', () => {

    test('Statistik zeigt "Summen pro Einheit"; jede Consumer-Zeile hat einen CSV-Button', async ({ page }) => {
        const hasData = await loadStatistik(page);
        test.skip(!hasData, 'Keine Messwerte im Standard-Zeitraum (Vorquartal) vorhanden.');

        const section = firstSummenSection(page);
        await expect(section).toBeVisible();

        // Consumer rows exist ...
        const consumerRows = section.locator('.zev-table__row--consumer');
        const consumerCount = await consumerRows.count();
        expect(consumerCount).toBeGreaterThan(0);

        // ... and there is exactly one CSV-download button per consumer row.
        const buttons = csvButtons(section);
        await expect(buttons).toHaveCount(consumerCount);

        // Every consumer row carries its own download button.
        for (let i = 0; i < consumerCount; i++) {
            await expect(csvButtons(consumerRows.nth(i))).toHaveCount(1);
        }
    });

    test('Klick auf den Button eines Consumers mit Daten loest einen CSV-Download aus', async ({ page }) => {
        const hasData = await loadStatistik(page);
        test.skip(!hasData, 'Keine Messwerte im Standard-Zeitraum (Vorquartal) vorhanden.');

        const section = firstSummenSection(page);
        // A consumer with data has an ENABLED download button (disabled when Total === 0).
        const enabledButton = csvButtons(section).and(page.locator('button:not([disabled])')).first();
        await expect(enabledButton).toBeVisible();

        const [download] = await Promise.all([
            page.waitForEvent('download'),
            enabledButton.click(),
        ]);

        const filename = download.suggestedFilename();
        expect(filename).toContain('verbrauch');
        expect(filename.endsWith('.csv')).toBe(true);

        // Verify the CSV payload: comma-separated header with (at least) 3 columns.
        const path = await download.path();
        expect(path).toBeTruthy();
        const content = await readFile(path!, 'utf-8');
        expect(content.length).toBeGreaterThan(0);
        const headerLine = content.split(/\r?\n/)[0];
        // 3 columns => at least 2 field separators (comma). Decimal separator is a dot,
        // so commas only appear as field separators.
        expect((headerLine.match(/,/g) || []).length).toBeGreaterThanOrEqual(2);
    });

    test('Nicht-Consumer-Zeilen (z.B. Producer) haben KEINEN CSV-Button', async ({ page }) => {
        const hasData = await loadStatistik(page);
        test.skip(!hasData, 'Keine Messwerte im Standard-Zeitraum (Vorquartal) vorhanden.');

        const section = firstSummenSection(page);

        // Producer rows exist in the seed data and must not carry a download button.
        const producerRows = section.locator('.zev-table__row--producer');
        const producerCount = await producerRows.count();
        test.skip(producerCount === 0, 'Keine Producer-Zeile im geladenen Zeitraum vorhanden.');

        await expect(csvButtons(producerRows)).toHaveCount(0);

        // Cross-check: the total number of CSV buttons equals the number of consumer rows,
        // i.e. no button leaks onto any non-consumer row (Producer/Bezug/Ruecklieferung).
        const consumerCount = await section.locator('.zev-table__row--consumer').count();
        await expect(csvButtons(section)).toHaveCount(consumerCount);
    });
});
