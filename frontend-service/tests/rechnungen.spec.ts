import { test as baseTest, expect, Locator, Page } from '@playwright/test';
import { getPreviousQuarter, navigateViaMenu, waitForFormResult, waitForTableWithData } from './helpers';

/**
 * tests / rechnungen.spec.ts
 * E2E tests for the Rechnungen (Invoice Generation) page
 * Tests cover: navigation, unit selection (select-all selects ONLY billable units),
 * date entry, invoice generation, and the total amount shown above the results.
 *
 * Die Generierungstests legen ihre eigenen ZEV- und VNB-Tarife an. Zuvor liefen sie gegen 2099,
 * fuer das kein Tarif existiert: Der Lauf endete in der Tariflueckenmeldung, und weil die
 * Zusicherungen in `if (isSuccess) { … }` standen, wurde faktisch nichts geprueft. Jetzt ist der
 * Erfolgsfall herstellbar - und der Fehlerfall wird als eigener Test ausdruecklich geprueft.
 */

/** Laufkennung je Worker-Prozess. */
const RUN_ID = Date.now().toString().slice(-6);

/**
 * Basisjahr je Browser-Projekt. Zusammen mit dem Versatz je Test erhaelt jeder Test ein eigenes
 * Abrechnungsjahr, sodass sich die parallel angelegten Tarife nicht ueberschneiden.
 * Die uebrigen Suites belegen 2050-2099; 2030-2039 ist frei.
 */
const PROJEKT_BASISJAHR: Record<string, number> = { chromium: 2030, firefox: 2034 };

/** Jahr, das dieser Test bespielt. Der Versatz trennt die Tests innerhalb der Suite. */
function abrechnungsjahr(versatz: number): number {
    return (PROJEKT_BASISJAHR[test.info().project.name] ?? 2038) + versatz;
}

/** Jahr ohne jeden Tarif - fuer die Pruefung der Tariflueckenmeldung. */
const JAHR_OHNE_TARIFE = 2039;

type TarifTracker = {
    names: string[];
    add: (name: string) => void;
};

/**
 * Fixture, die die je Test angelegten Tarife am Ende wieder entfernt. Ohne das blieben die
 * Tarife stehen und der naechste Lauf scheiterte an der Ueberschneidungspruefung.
 */
const test = baseTest.extend<{ tarifTracker: TarifTracker }>({
    tarifTracker: async ({ page }, use) => {
        const tracker: TarifTracker = {
            names: [],
            add: (name: string) => tracker.names.push(name)
        };

        await use(tracker);

        if (tracker.names.length === 0) {
            return;
        }

        page.removeAllListeners('dialog');
        await navigateToTarife(page);

        for (const name of tracker.names) {
            const row = page.locator(`tr:has-text("${name}")`).first();
            if (!await row.isVisible().catch(() => false)) {
                console.log(`Cleanup: Tarif "${name}" nicht gefunden (bereits geloescht)`);
                continue;
            }
            page.once('dialog', dialog => dialog.accept());
            await row.locator('.zev-kebab-button').click();
            await row.locator('.zev-kebab-menu__item--danger').click();
            await expect(row).not.toBeVisible({ timeout: 10000 });
        }
    }
});

async function navigateToRechnungen(page: Page): Promise<void> {
    await navigateViaMenu(page, '/rechnungen');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
}

async function navigateToTarife(page: Page): Promise<void> {
    await navigateViaMenu(page, '/tarife');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
    await waitForTableWithData(page, 10000);
}

/**
 * Wartet, bis keine Meldung mehr steht. Erfolgsmeldungen blenden sich erst nach 5 Sekunden aus
 * und wuerden sonst als Ergebnis der naechsten Aktion gewertet.
 */
async function clearMessages(page: Page): Promise<void> {
    const error = page.locator('.zev-message--error');
    if (await error.isVisible().catch(() => false)) {
        await error.click();
        await expect(error).not.toBeVisible({ timeout: 5000 });
    }
    const success = page.locator('.zev-message--success');
    if (await success.isVisible().catch(() => false)) {
        await expect(success).not.toBeVisible({ timeout: 10000 });
    }
}

/**
 * Legt einen Tarif an und besteht auf dem Erfolg - ein stilles Scheitern wuerde den
 * nachfolgenden Rechnungslauf in die Tariflueckenmeldung laufen lassen.
 */
async function createTarifOrFail(page: Page, tracker: TarifTracker, daten: {
    tariftyp: 'ZEV' | 'VNB';
    bezeichnung: string;
    jahr: number;
}): Promise<void> {
    await page.locator('button.zev-button--primary').first().click();
    await expect(page.locator('form')).toBeVisible();

    await page.locator('#tariftyp').selectOption(daten.tariftyp);
    await page.locator('#bezeichnung').fill(daten.bezeichnung);
    await page.locator('#preis').fill('0.20');
    await page.locator('#gueltigVon').fill(`${daten.jahr}-01-01`);
    await page.locator('#gueltigBis').fill(`${daten.jahr}-12-31`);

    tracker.add(daten.bezeichnung);
    await clearMessages(page);
    await page.locator('button[type="submit"]').click();
    if (!await waitForFormResult(page, 20000)) {
        const meldung = await page.locator('.zev-message--error').textContent().catch(() => '');
        throw new Error(`Tarif "${daten.bezeichnung}" konnte nicht angelegt werden: ${meldung?.trim()}`);
    }

    await waitForTableWithData(page, 10000);
    await expect(page.locator(`tr:has-text("${daten.bezeichnung}")`).first()).toBeVisible({ timeout: 10000 });
}

/**
 * Legt das ZEV-/VNB-Tarifpaar an, das `validateTarifAbdeckung` fuer einen Konsumenten verlangt,
 * und gibt das bespielte Jahr zurueck.
 */
async function bereiteAbrechnungsjahrVor(page: Page, tracker: TarifTracker, versatz: number): Promise<number> {
    const jahr = abrechnungsjahr(versatz);
    await navigateToTarife(page);
    await createTarifOrFail(page, tracker, { tariftyp: 'ZEV', bezeichnung: `RE ZEV${versatz} ${RUN_ID}`, jahr });
    await createTarifOrFail(page, tracker, { tariftyp: 'VNB', bezeichnung: `RE VNB${versatz} ${RUN_ID}`, jahr });
    return jahr;
}

/** Alle Einheiten-Checkboxen ohne "Alle auswaehlen". */
function einheitItems(page: Page): Locator {
    return page.locator('.zev-checkbox-item:not(.zev-checkbox-item--select-all)');
}

/**
 * Wartet auf die geladene Einheitenliste und besteht darauf, dass sie nicht leer ist.
 * Frueher stieg jeder dieser Tests bei `count === 0` still aus - eine leere Liste waere aber
 * ein Umgebungsfehler und soll auffallen.
 */
async function einheitItemsOrFail(page: Page): Promise<Locator> {
    const items = einheitItems(page);
    await expect(items.first()).toBeVisible({ timeout: 15000 });
    return items;
}

/**
 * Waehlt genau einen Konsumenten aus. Genau einer, weil jede weitere Einheit ohne Rechnung
 * (Produzent, Ladestation ohne Positionen) die Erfolgs- in eine Warnmeldung verwandeln wuerde.
 */
async function waehleErstenKonsumenten(page: Page): Promise<string> {
    const items = await einheitItemsOrFail(page);
    const anzahl = await items.count();
    for (let i = 0; i < anzahl; i++) {
        const label = (await items.nth(i).locator('label').textContent()) ?? '';
        // Der Typ steht als "[Konsument]" bzw. "[Consumer]" hinter dem Namen
        if (/konsument|consumer/i.test(label)) {
            await items.nth(i).locator('input[type="checkbox"]').check();
            return label.trim();
        }
    }
    throw new Error('Keine Konsumenten-Einheit in der Auswahl gefunden');
}

/** Setzt den Zeitraum auf das erste Quartal des Jahres und startet den Lauf. */
async function generiereQuartal(page: Page, jahr: number): Promise<void> {
    // Reihenfolge beachten: Das Aendern von "von" setzt "bis" automatisch auf das Monatsende
    await page.locator('#dateFrom').fill(`${jahr}-01-01`);
    await page.locator('#dateTo').fill(`${jahr}-03-31`);

    const generateButton = page.locator('button[type="submit"]');
    await expect(generateButton).toBeEnabled();
    await generateButton.click();
}

test.describe('Rechnungen - Navigation and Display', () => {
    test('should navigate to Rechnungen page', async ({ page }) => {
        await navigateToRechnungen(page);

        await expect(page.locator('.zev-container h1')).toBeVisible();
    });

    test('should display unit selection checkbox group', async ({ page }) => {
        await navigateToRechnungen(page);

        // The checkbox group for unit selection must be present
        const checkboxGroup = page.locator('.zev-checkbox-group');
        await expect(checkboxGroup).toBeVisible();
    });

    test('should display date input fields', async ({ page }) => {
        await navigateToRechnungen(page);

        await expect(page.locator('#dateFrom')).toBeVisible();
        await expect(page.locator('#dateTo')).toBeVisible();
    });

    test('should display generate button', async ({ page }) => {
        await navigateToRechnungen(page);

        const generateButton = page.locator('button[type="submit"]');
        await expect(generateButton).toBeVisible();
    });

    test('should have generate button disabled when no units selected', async ({ page }) => {
        await navigateToRechnungen(page);
        await einheitItemsOrFail(page);

        // Die Seite startet ohne Auswahl - ohne Einheit ist der Lauf nicht ausloesbar
        await expect(page.locator('#einheit-select-all')).not.toBeChecked();
        await expect(page.locator('button[type="submit"]')).toBeDisabled();
    });
});

test.describe('Rechnungen - Einheiten Auswahl (FR-3: Produzenten)', () => {
    test('should show type labels [KONSUMENT] or [PRODUZENT] for units', async ({ page }) => {
        await navigateToRechnungen(page);
        const items = await einheitItemsOrFail(page);

        // Jede Zeile traegt den Typ in eckigen Klammern hinter dem Namen
        const labels = await items.locator('label').allTextContents();
        for (const label of labels) {
            expect(label, `Einheit "${label}" zeigt keinen Typ in eckigen Klammern`)
                .toMatch(/\[.+\]/);
        }
    });

    test('should show select-all checkbox', async ({ page }) => {
        await navigateToRechnungen(page);

        const selectAllCheckbox = page.locator('#einheit-select-all');
        await expect(selectAllCheckbox).toBeVisible();

        const selectAllLabel = page.locator('label[for="einheit-select-all"]');
        await expect(selectAllLabel).toBeVisible();
    });

    test('select-all should select only billable units (consumers and charging stations)', async ({ page }) => {
        await navigateToRechnungen(page);
        const items = await einheitItemsOrFail(page);
        const itemCount = await items.count();

        // Click select-all (page starts with nothing selected)
        const selectAll = page.locator('#einheit-select-all');
        await selectAll.click();

        // Type label is rendered as "[Typ]" and localized. "Select all" selects the
        // BILLABLE units: consumers (DE "Konsument"/EN "Consumer") and charging stations
        // (DE "Ladestation"/EN "Charging station", Specs/Ladestationen.md - ein Nutzer ohne
        // Wohnung wird ueber seine Ladestation abgerechnet). Producers and the balance meter
        // points Bezug/Rücklieferung (Grid supply/Feed-in) must NOT be selected.
        const isBillable = (label: string) =>
            /konsument|consumer|ladestation|charging station/i.test(label);

        // Rechnungsfaehige Einheiten muessen angehakt sein, alle anderen nicht
        let sawBillable = false;
        for (let i = 0; i < itemCount; i++) {
            const item = items.nth(i);
            const labelText = (await item.locator('label').textContent()) ?? '';
            const checkbox = item.locator('input[type="checkbox"]');
            if (isBillable(labelText)) {
                sawBillable = true;
                await expect(checkbox).toBeChecked();
            } else {
                await expect(checkbox).not.toBeChecked();
            }
        }
        // The test data is expected to contain at least one billable unit
        expect(sawBillable).toBe(true);

        // Clicking select-all again deselects the consumers
        await selectAll.click();
        for (let i = 0; i < itemCount; i++) {
            await expect(items.nth(i).locator('input[type="checkbox"]')).not.toBeChecked();
        }
    });

    test('should enable generate button when unit is selected and dates are set', async ({ page }) => {
        await navigateToRechnungen(page);
        const items = await einheitItemsOrFail(page);

        // Der Zeitraum ist mit dem Vorquartal vorbelegt - es fehlt nur die Einheit
        await expect(page.locator('#dateFrom')).not.toHaveValue('');
        await expect(page.locator('#dateTo')).not.toHaveValue('');
        await expect(page.locator('button[type="submit"]')).toBeDisabled();

        await items.first().locator('input[type="checkbox"]').check();

        await expect(page.locator('button[type="submit"]')).toBeEnabled();
    });
});

test.describe('Rechnungen - Datumseingabe', () => {
    test('should default dateFrom to previous quarter start', async ({ page }) => {
        await navigateToRechnungen(page);

        const dateFrom = await page.locator('#dateFrom').inputValue();
        expect(dateFrom).toBe(getPreviousQuarter().von);
    });

    test('should default dateTo to previous quarter end', async ({ page }) => {
        await navigateToRechnungen(page);

        const dateTo = await page.locator('#dateTo').inputValue();
        expect(dateTo).toBe(getPreviousQuarter().bis);
    });

    test('should mark the previous quarter button as active on page load', async ({ page }) => {
        await navigateToRechnungen(page);

        const activeButton = page.locator('.zev-quarter-button--active');
        await expect(activeButton).toHaveCount(1);
        await expect(activeButton).toHaveText(getPreviousQuarter().label);
    });

    test('should auto-set dateTo to month end when dateFrom is changed', async ({ page }) => {
        await navigateToRechnungen(page);

        await page.locator('#dateFrom').fill('2024-03-15');

        await expect(page.locator('#dateTo')).toHaveValue('2024-03-31');
    });

    test('should display quarter selector component', async ({ page }) => {
        await navigateToRechnungen(page);

        // Quarter selector is embedded in the form
        const quarterSelector = page.locator('app-quarter-selector');
        await expect(quarterSelector).toBeVisible();
    });
});

test.describe('Rechnungen - Invoice Generation', () => {
    test('should generate an invoice for a consumer and offer it for download',
        async ({ page, tarifTracker }) => {
            const jahr = await bereiteAbrechnungsjahrVor(page, tarifTracker, 0);

            await navigateToRechnungen(page);
            await waehleErstenKonsumenten(page);
            await generiereQuartal(page, jahr);

            // Genau ein Konsument war gewaehlt, ZEV und VNB decken das Quartal ab: Der Lauf
            // muss erfolgreich sein - keine Warnung (Einheit ohne Rechnung), kein Fehler.
            await expect(page.locator('.zev-message--success')).toBeVisible({ timeout: 30000 });

            const ergebnisZeilen = page.locator('.zev-panel .zev-table tbody tr');
            await expect(ergebnisZeilen.first()).toBeVisible({ timeout: 10000 });

            // Jede Ergebniszeile bietet den Download der Rechnung an
            const zeilenAnzahl = await ergebnisZeilen.count();
            await expect(page.locator('.zev-panel .zev-button--secondary')).toHaveCount(zeilenAnzahl);
        });

    test('should display the total amount above the results table after generation',
        async ({ page, tarifTracker }) => {
            const jahr = await bereiteAbrechnungsjahrVor(page, tarifTracker, 1);

            await navigateToRechnungen(page);
            await waehleErstenKonsumenten(page);
            await generiereQuartal(page, jahr);

            await expect(page.locator('.zev-message--success')).toBeVisible({ timeout: 30000 });

            // Der Gesamtbetrag steht ueber der Tabelle, im Schweizer Format aus
            // `Specs/generell.md`: Punkt als Dezimal-, ASCII-Hochkomma als Tausendertrenner
            // und zwei Nachkommastellen, z.B. "1'234.50 CHF".
            const total = page.locator('.zev-rechnungen-total');
            await expect(total).toBeVisible();
            await expect(total).toContainText(/-?\d{1,3}(?:'\d{3})*\.\d{2} CHF/);
        });

    test('should report a tariff gap instead of generating an invoice', async ({ page }) => {
        await navigateToRechnungen(page);
        await waehleErstenKonsumenten(page);

        // Fuer dieses Jahr legt keine Suite Tarife an - die Abdeckungspruefung muss anschlagen
        await generiereQuartal(page, JAHR_OHNE_TARIFE);

        await expect(page.locator('.zev-message--error')).toBeVisible({ timeout: 30000 });
        // Ohne Rechnungen bleibt der Ergebnisbereich aus
        await expect(page.locator('.zev-rechnungen-total')).toHaveCount(0);
    });

    test('should not allow generating without selecting units', async ({ page }) => {
        await navigateToRechnungen(page);
        await einheitItemsOrFail(page);

        // Ohne Auswahl bleibt der Knopf gesperrt - der Lauf ist gar nicht ausloesbar
        await expect(page.locator('button[type="submit"]')).toBeDisabled();

        await page.locator('#einheit-select-all').click();
        await expect(page.locator('button[type="submit"]')).toBeEnabled();

        await page.locator('#einheit-select-all').click();
        await expect(page.locator('button[type="submit"]')).toBeDisabled();
    });
});
