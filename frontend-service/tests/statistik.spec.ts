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
        // PDF-Export-Button gezielt: sekundär, aber NICHT --compact (die Download-CSV-Buttons
        // in "Summen pro Einheit" sind ebenfalls --secondary, aber --compact).
        const pdfButton = page.locator('button.zev-button--secondary:not(.zev-button--compact)');
        await expect(pdfButton).toBeVisible();
        await expect(pdfButton).toBeDisabled();

        // Check empty state message is shown
        const emptyState = page.locator('.zev-empty-state');
        await expect(emptyState).toBeVisible();
    });

    test('should load and display statistics when date range is submitted', async ({ page }) => {
        await navigateToStatistik(page);

        // The form should have default dates pre-filled (previous quarter)
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

        // Wait for the request to complete - empty state should disappear or stay (if no data for selected range)
        // The page has multiple .zev-panel elements: first is the filter form, second would be the overview
        // We need to wait for loading to complete and check the result

        // Wait for loading state to end (submit button becomes enabled again)
        await expect(submitButton).toBeEnabled({ timeout: 10000 });

        // After submission, one of three states should occur:
        // 1. Statistics loaded: Multiple panels visible (filter + overview + monthly)
        // 2. No data: Empty state still visible
        // 3. Error: Error message visible
        const panels = page.locator('.zev-panel');
        const emptyState = page.locator('.zev-empty-state');
        const errorMessage = page.locator('.zev-message--error');

        const panelCount = await panels.count();
        const hasEmpty = await emptyState.isVisible().catch(() => false);
        const hasError = await errorMessage.isVisible().catch(() => false);

        // At minimum, the page should respond to the submission
        // Either: more than 1 panel (filter + overview), empty state, or error
        expect(panelCount > 1 || hasEmpty || hasError).toBeTruthy();

        // If statistics loaded (more than just the filter panel), verify structure
        if (panelCount > 1) {
            // Check for info rows in overview (second panel)
            const infoRows = page.locator('.zev-info-row');
            await expect(infoRows.first()).toBeVisible();

            // Check for status indicator
            const statusIndicator = page.locator('.zev-status-indicator').first();
            await expect(statusIndicator).toBeVisible();

            // PDF button should now be enabled
            // PDF-Export-Button gezielt: sekundär, aber NICHT --compact (die Download-CSV-Buttons
        // in "Summen pro Einheit" sind ebenfalls --secondary, aber --compact).
        const pdfButton = page.locator('button.zev-button--secondary:not(.zev-button--compact)');
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

            // Kennzahlen-Panel (neues Feature): tabellarisch (ohne Titelzeile) im ersten
            // .zev-comparison-section; wird in BEIDEN Verteilmodi angezeigt.
            const kennzahlenTable = firstMonthPanel.locator('.zev-comparison-section .zev-table--compact').first();
            await expect(kennzahlenTable).toBeVisible();
            // Mindestens eine Kennzahl-Zeile mit rechtsbündigem Wert (.zev-table__number)
            expect(await kennzahlenTable.locator('tbody tr').count()).toBeGreaterThan(0);
            await expect(kennzahlenTable.locator('td.zev-table__number').first()).toBeVisible();

            // Summen-Vergleich: nur im Producer-Messung-Modus vorhanden; im Bilanzmodus
            // durch das Kennzahlen-Panel ersetzt (ausgeblendet). Daher modus-agnostisch:
            // entweder 0 (Bilanz) oder 3 Basis-Vergleiche (A=B, A=C, B=C) + bis zu 2 Bilanz-
            // Vergleiche (Bezug/Rücklieferung ↔ Bilanzmesspunkt).
            const comparisonCount = await firstMonthPanel.locator('.zev-comparison-item').count();
            expect(comparisonCount === 0 || (comparisonCount >= 3 && comparisonCount <= 5)).toBeTruthy();

            // Einheit-Summen table (eigene Section; ebenfalls .zev-table--compact, daher gezielt scopen)
            const einheitSummenTable = firstMonthPanel.locator('.zev-einheit-summen-section .zev-table--compact');
            if (await einheitSummenTable.count() > 0 && await einheitSummenTable.first().isVisible()) {
                // Verify table has producer/consumer rows
                const producerRows = firstMonthPanel.locator('.zev-table__row--producer');
                const consumerRows = firstMonthPanel.locator('.zev-table__row--consumer');

                // At least some rows should exist
                const totalRows = await producerRows.count() + await consumerRows.count();
                expect(totalRows).toBeGreaterThan(0);
            }
        }
    });

    test('should render a non-empty title tooltip on every Kennzahl row', async ({ page }) => {
        await navigateToStatistik(page);

        // Submit form to load statistics
        const submitButton = page.locator('button.zev-button--primary[type="submit"]');
        await submitButton.click();

        // Wait for response
        await page.waitForTimeout(2000);

        const monthPanels = page.locator('.zev-panel--month');
        const monthPanelCount = await monthPanels.count();

        // Nur prüfen, wenn Monatsdaten geladen wurden (Testdaten-abhängig).
        if (monthPanelCount === 0) {
            return;
        }

        const firstMonthPanel = monthPanels.first();

        // Kennzahlen-Panel: headerless 3-Spalten-Tabelle im ersten .zev-comparison-section.
        const kennzahlenTable = firstMonthPanel.locator('.zev-comparison-section .zev-table--compact').first();
        await expect(kennzahlenTable).toBeVisible();

        // Jede Kennzahl-Zeile ist eine <tr> mit einem erklärenden Tooltip (natives title-Attribut).
        const kennzahlRows = kennzahlenTable.locator('tbody tr');
        const rowCount = await kennzahlRows.count();
        expect(rowCount).toBeGreaterThan(0);

        // Jede Zeile trägt ein gesetztes, nicht-leeres title-Attribut.
        // Bewusst KEIN Vergleich mit konkreten Übersetzungstexten: bei nicht angewendeten
        // V90/V91-Migrationen erscheint der Rohkey - dieser ist ebenfalls nicht-leer.
        for (let i = 0; i < rowCount; i++) {
            const title = await kennzahlRows.nth(i).getAttribute('title');
            expect(title).not.toBeNull();
            expect((title ?? '').trim().length).toBeGreaterThan(0);
        }
    });
});
