import { test, expect, Locator, Page } from '@playwright/test';
import { getPreviousQuarter, navigateViaMenu, clickKebabMenuItem, waitForFormResult, waitForTableWithData } from './helpers';

/**
 * tests / debitorkontrolle.spec.ts
 * E2E tests for Debitorkontrolle (Debtor Control) page
 *
 * **Jeder Test bekommt seinen eigenen Monat, jedes Browser-Projekt sein eigenes Jahr.**
 * Vorher verwendeten alle Tests feste 2099er-Daten. Ein Debitor ist über
 * (`mieter_id`, `datum_von`, `org_id`) eindeutig und wird per Upsert geschrieben — Chromium und
 * Firefox schrieben also auf denselben Datensatz und löschten ihn einander unter den Füssen weg.
 * Das war der Grund, weshalb praktisch jeder Test hier mit `try { … } catch { return; }` und
 * `if (isSuccess) { … }` abgesichert war und bei jedem Fehlschlag **stumm grün** meldete — auf
 * einer Maske, die Geldbeträge und Zahlungsstatus führt.
 *
 * Mit disjunkten Zeiträumen kann sich kein Lauf mehr mit einem anderen überschneiden, und jeder
 * Fehlschlag darf den Test ehrlich rot machen.
 */

// Track created debitoren for cleanup — identified by their datumVon value
let createdDebitorDates: string[] = [];

/**
 * Basisjahr je Browser-Projekt. Die Bereiche sind disjunkt; zusammen mit dem je Test eigenen
 * Monat ist jeder Datensatz eindeutig einem Test in einem Projekt zugeordnet.
 */
const PROJEKT_JAHR: Record<string, number> = { chromium: 2091, firefox: 2092 };

function jahr(): number {
    return PROJEKT_JAHR[test.info().project.name] ?? 2093;
}

/**
 * Zeitfenster eines Tests — **je Test eindeutig und überschneidungsfrei**.
 *
 * Zwei Fenster je Monat (01.–11. und 15.–25.), damit 24 Tests in ein Jahr passen. Playwright
 * lässt die Tests einer Datei parallel laufen (`fullyParallel`, 4 Worker); teilten sich zwei
 * Tests ein Fenster, sähe der eine die Zeilen des anderen — und ein Aufräumen mitten im Lauf
 * zöge dem anderen die Zeile unter den Füssen weg.
 *
 * @param slot Eindeutige Nummer 1..24
 */
function zeitraum(slot: number): { von: string; bis: string } {
    const j = jahr();
    const mm = String(Math.ceil(slot / 2)).padStart(2, '0');
    const start = slot % 2 === 1 ? 1 : 15;
    const pad = (t: number) => String(t).padStart(2, '0');
    return { von: `${j}-${mm}-${pad(start)}`, bis: `${j}-${mm}-${pad(start + 10)}` };
}

/**
 * Zeitraum in der Vergangenheit — nötig, wo „Heute"/„Gestern" als Zahldatum gesetzt wird: Das
 * Backend weist ein Zahldatum vor `datumBis` ab.
 */
function vergangenerZeitraum(): { von: string; bis: string } {
    // 2002/2003 statt 2000/2001: Auf 2000-01-01 liegt ein Rest aus einem früheren Lauf der alten
    // Fassung dieser Suite, und mit gesetztem Zahldatum weist der Upsert eine Neuanlage ab.
    const j = jahr() - 89; // chromium 2002, firefox 2003
    return { von: `${j}-01-01`, bis: `${j}-03-31` };
}

/**
 * Navigate to the Debitorkontrolle page
 */
async function navigateToDebitorkontrolle(page: Page): Promise<void> {
    await navigateViaMenu(page, '/debitoren');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
}

/**
 * Set the date range inputs to show a specific period
 */
async function setDateRange(page: Page, von: string, bis: string): Promise<void> {
    const dateFrom = page.locator('#dateFrom');
    const dateTo = page.locator('#dateTo');

    // Steht der Zeitraum bereits, löst ein erneutes `change` kein Nachladen aus - das Warten
    // liefe in den Timeout. Die Liste ist dann ohnehin aktuell: Nach dem Speichern lädt die
    // Komponente selbst mit genau diesem Zeitraum neu.
    if (await dateFrom.inputValue() === von && await dateTo.inputValue() === bis) {
        return;
    }

    // Die Felder haengen an (ngModelChange), nicht an (change): Schon `fill` loest das Nachladen
    // aus, ein zusaetzliches dispatchEvent('change') bewirkt nichts. Die Warte-Registrierung muss
    // deshalb **vor** dem Ausfuellen stehen - sonst ist die Antwort schon da, bevor gewartet wird,
    // und der Test laeuft in den Timeout.
    //
    // Gewartet wird auf die Antwort zu **genau diesem** Zeitraum (von/bis stehen als Query-
    // Parameter, siehe DebitorService.getDebitoren). Damit wird weder der Zwischenstand nach dem
    // Fuellen des ersten Felds noch das Nachladen nach einem Speichern faelschlich als das
    // erwartete Ergebnis gewertet.
    const antwort = page.waitForResponse(
        (res) => res.url().includes('/api/debitoren')
            && res.request().method() === 'GET'
            && res.url().includes(`von=${von}`)
            && res.url().includes(`bis=${bis}`),
        { timeout: 15000 }
    );
    await dateFrom.fill(von);
    await dateTo.fill(bis);
    await antwort;
}

/**
 * Erste wählbare Mieter-Option des Formulars.
 *
 * Wirft, wenn keine existiert: Ohne Mieter ist diese Maske nicht bedienbar, das ist ein
 * Umgebungsfehler und kein Grund, den Test stumm zu überspringen.
 */
async function mieterIdOrFail(page: Page): Promise<string> {
    const options = page.locator('#mieterId').locator('option:not([disabled])');
    if (await options.count() === 0) {
        throw new Error('Kein Mieter vorhanden - ohne Mieter lässt sich kein Debitor erfassen');
    }
    const value = await options.first().getAttribute('value');
    if (!value) {
        throw new Error('Mieter-Option ohne Wert');
    }
    return value;
}

/**
 * Fill the debitor form
 */
async function fillDebitorForm(page: Page, data: {
    mieterId?: string;
    betrag: string;
    datumVon: string;
    datumBis: string;
    zahldatum?: string;
}): Promise<void> {
    if (data.mieterId) {
        await page.locator('#mieterId').selectOption(data.mieterId);
        await page.waitForTimeout(200);
    }
    await page.locator('#betrag').fill(data.betrag);
    await page.locator('#datumVon').fill(data.datumVon);
    await page.locator('#datumBis').fill(data.datumBis);
    if (data.zahldatum) {
        await page.locator('#zahldatum').fill(data.zahldatum);
    }
}

/**
 * Open the "Neu erfassen" form
 */
async function openCreateForm(page: Page): Promise<void> {
    const createButton = page.locator('button.zev-button--primary').first();
    await createButton.click();
    await page.locator('form').waitFor({ state: 'visible', timeout: 5000 });
}

/**
 * Wartet, bis keine Meldung mehr steht.
 *
 * Zwingend vor jedem Absenden: Erfolgsmeldungen blenden sich erst nach 5 Sekunden aus. Eine noch
 * stehende Meldung der vorherigen Aktion würde sonst als Ergebnis der nächsten gewertet — ein
 * abgewiesener Speicherversuch sähe dann wie ein erfolgreicher aus.
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
 * Sendet das offene Formular ab und besteht auf einer Erfolgsmeldung.
 *
 * Ersetzt das frühere `try { … } catch { return; }` samt `if (isSuccess)`: Ein Fehlschlag beim
 * Speichern ist ein Testergebnis, kein Grund zum stillen Aussteigen. Die Server-Meldung wandert
 * in die Fehlermeldung, damit der Grund im Report steht.
 */
async function submitAndExpectSuccess(page: Page, was: string): Promise<void> {
    await clearMessages(page);
    await page.locator('button[type="submit"]').click();
    const isSuccess = await waitForFormResult(page, 20000);
    if (!isSuccess) {
        const meldung = await page.locator('.zev-message--error').textContent().catch(() => '');
        throw new Error(`${was} konnte nicht gespeichert werden: ${meldung?.trim()}`);
    }
}

/**
 * Legt einen Debitor an, registriert ihn fürs Aufräumen und stellt den Zeitraum darauf ein.
 * Erwartet die geöffnete Debitorkontrolle. Wirft, wenn das Anlegen misslingt.
 *
 * @returns Die Zeile des angelegten Debitors
 */
async function createDebitorOrFail(page: Page, daten: {
    von: string; bis: string; betrag: string; zahldatum?: string;
}): Promise<Locator> {
    await openCreateForm(page);
    const mieterId = await mieterIdOrFail(page);
    await fillDebitorForm(page, {
        mieterId,
        betrag: daten.betrag,
        datumVon: daten.von,
        datumBis: daten.bis,
        zahldatum: daten.zahldatum
    });

    createdDebitorDates.push(daten.von);
    await submitAndExpectSuccess(page, `Debitor ${formatToSwiss(daten.von)}`);

    await setDateRange(page, daten.von, daten.bis);
    await waitForTableWithData(page, 10000);

    const row = page.locator(`tr:has-text("${formatToSwiss(daten.von)}")`).first();
    await expect(row).toBeVisible({ timeout: 10000 });
    return row;
}

/**
 * Klickt „Löschen" im Kebab-Menü einer Zeile.
 *
 * Nicht der gemeinsame `clickKebabMenuItem`-Helper: Eine bezahlte Zeile hat **zwei** Einträge
 * mit `--danger` („Zahldatum löschen" und „Löschen"), der Helper träfe beide und scheiterte an
 * Playwrights strict mode. „Löschen" ist der letzte Eintrag des Menüs.
 */
async function clickDeleteInKebab(page: Page, row: Locator): Promise<void> {
    await row.locator('.zev-kebab-button').click();
    await row.locator('.zev-kebab-menu--open').waitFor({ state: 'visible', timeout: 2000 });
    await row.locator('.zev-kebab-menu__item--danger').last().click();
}

/**
 * Delete a debitor row that contains the given datumVon date text (nur Aufräumen — hier darf
 * nachsichtig gearbeitet werden, ein Fehlschlag soll den Test nicht überschreiben).
 */
async function deleteDebitorByDate(page: Page, datumVon: string): Promise<void> {
    const swissDate = formatToSwiss(datumVon);
    try {
        page.removeAllListeners('dialog');
        await navigateToDebitorkontrolle(page);
        // Use the whole year as range to safely find any test entry regardless of month
        const year = datumVon.substring(0, 4);
        await setDateRange(page, `${year}-01-01`, `${year}-12-31`);

        const row = page.locator(`tr:has-text("${swissDate}")`).first();
        if (!await row.isVisible().catch(() => false)) {
            return;
        }
        page.once('dialog', async dialog => { await dialog.accept(); });
        await clickDeleteInKebab(page, row);
        await row.waitFor({ state: 'hidden', timeout: 10000 }).catch(() =>
            console.log(`Cleanup: Debitor "${swissDate}" ist nach dem Löschen noch sichtbar`));
    } catch (error) {
        console.log(`Cleanup: Fehler beim Löschen von "${swissDate}": ${error}`);
        page.removeAllListeners('dialog');
    }
}

/**
 * Format ISO date to Swiss dd.MM.yyyy
 */
function formatToSwiss(iso: string): string {
    const [y, m, d] = iso.split('-');
    return `${d}.${m}.${y}`;
}

test.beforeEach(() => {
    createdDebitorDates = [];
});

test.afterEach(async ({ page }) => {
    for (const date of createdDebitorDates) {
        await deleteDebitorByDate(page, date);
    }
    createdDebitorDates = [];
});

// ──────────────────────────────────────────────────────────────────────────────
// Navigation & Display
// ──────────────────────────────────────────────────────────────────────────────

test.describe('Debitorkontrolle - Navigation and Display', () => {

    test('should navigate to Debitorkontrolle via menu', async ({ page }) => {
        await navigateToDebitorkontrolle(page);

        await expect(page.locator('.zev-container h1')).toBeVisible();
        await expect(page.locator('button.zev-button--primary')).toBeVisible();
    });

    test('should show quarter selector and date inputs', async ({ page }) => {
        await navigateToDebitorkontrolle(page);

        await expect(page.locator('app-quarter-selector')).toBeVisible();
        await expect(page.locator('#dateFrom')).toBeVisible();
        await expect(page.locator('#dateTo')).toBeVisible();
    });

    test('should default the date range to the previous quarter', async ({ page }) => {
        await navigateToDebitorkontrolle(page);

        const previousQuarter = getPreviousQuarter();
        await expect(page.locator('#dateFrom')).toHaveValue(previousQuarter.von);
        await expect(page.locator('#dateTo')).toHaveValue(previousQuarter.bis);
    });

    test('should mark the previous quarter button as active on page load', async ({ page }) => {
        await navigateToDebitorkontrolle(page);

        const activeButton = page.locator('.zev-quarter-button--active');
        await expect(activeButton).toHaveCount(1);
        await expect(activeButton).toHaveText(getPreviousQuarter().label);
    });

    test('should show correct table columns', async ({ page }) => {
        // Eigenen Debitor anlegen statt auf vorhandene Daten hoffen - sonst lief der Test
        // ohne jede Zusicherung durch, wenn der Zeitraum leer war.
        await navigateToDebitorkontrolle(page);
        const z = zeitraum(1);
        await createDebitorOrFail(page, { von: z.von, bis: z.bis, betrag: '150.00' });

        const headers = page.locator('.zev-table th');
        // Auswahl, Mieter, Betrag, Datum von, Datum bis, Zahldatum, Status, Actions
        expect(await headers.count()).toBeGreaterThanOrEqual(7);
    });

    test('should show empty state message when no debitoren for period', async ({ page }) => {
        await navigateToDebitorkontrolle(page);

        // Set a date range far in the past where no data exists
        await setDateRange(page, '1900-01-01', '1900-03-31');

        const emptyState = page.locator('.zev-empty-state');
        await expect(emptyState).toBeVisible({ timeout: 10000 });
    });

});

// ──────────────────────────────────────────────────────────────────────────────
// Sorting
// ──────────────────────────────────────────────────────────────────────────────

test.describe('Debitorkontrolle - Sorting', () => {

    test('should toggle sort indicator when clicking column header', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        const z = zeitraum(2);
        await createDebitorOrFail(page, { von: z.von, bis: z.bis, betrag: '10.00' });

        const mieterHeader = page.locator('.zev-table th').filter({ hasText: /Mieter/i }).first();
        const sortIndicator = page.locator('.zev-table__sort-indicator');
        await expect(sortIndicator.first()).toBeVisible();

        // Click to reverse
        await mieterHeader.click();
        await expect(sortIndicator.first()).toBeVisible();
    });

    test('should show sort indicator after clicking Betrag column', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        const z = zeitraum(3);
        await createDebitorOrFail(page, { von: z.von, bis: z.bis, betrag: '10.00' });

        const betragHeader = page.locator('th').filter({ hasText: /CHF/i }).first();
        await betragHeader.click();

        const sortIndicator = page.locator('.zev-table__sort-indicator');
        await expect(sortIndicator.first()).toBeVisible();
    });

});

// ──────────────────────────────────────────────────────────────────────────────
// Create
// ──────────────────────────────────────────────────────────────────────────────

test.describe('Debitorkontrolle - Create Debitor', () => {

    test('should show create form when clicking "Neu erfassen"', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        await openCreateForm(page);

        await expect(page.locator('form')).toBeVisible();
        await expect(page.locator('#mieterId')).toBeVisible();
        await expect(page.locator('#betrag')).toBeVisible();
        await expect(page.locator('#datumVon')).toBeVisible();
        await expect(page.locator('#datumBis')).toBeVisible();
        await expect(page.locator('#zahldatum')).toBeVisible();
        await expect(page.locator('button[type="submit"]')).toBeVisible();
        await expect(page.locator('button.zev-button--secondary')).toBeVisible();
    });

    test('should cancel form and return to list', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        await openCreateForm(page);

        await expect(page.locator('form')).toBeVisible();

        await page.locator('button.zev-button--secondary').click();

        await expect(page.locator('form')).not.toBeVisible();
        await expect(page.locator('button.zev-button--primary').first()).toBeVisible();
    });

    test('should create a new debitor successfully', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        const z = zeitraum(4);

        const row = await createDebitorOrFail(page, { von: z.von, bis: z.bis, betrag: '150.00' });

        await expect(row).toContainText('150.00');
    });

    test('should populate Einheit field when Mieter is selected', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        await openCreateForm(page);

        const mieterId = await mieterIdOrFail(page);
        await page.locator('#mieterId').selectOption(mieterId);

        // Jeder Mieter hat mindestens eine Einheit (Specs/Ladestationen.md) - das Feld muss also
        // gefüllt sein. Frueher prüfte dieser Test nur, dass das Feld ueberhaupt sichtbar ist,
        // und hielt damit nicht, was sein Name verspricht.
        const einheitInput = page.locator('input[readonly]').first();
        await expect(einheitInput).toBeVisible();
        await expect(einheitInput).not.toHaveValue('');
    });

    test('should show status "Offen" for new debitor without zahldatum', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        const z = zeitraum(5);

        // Ohne Zahldatum -> Status "Offen"
        const row = await createDebitorOrFail(page, { von: z.von, bis: z.bis, betrag: '99.00' });

        await expect(row.locator('.zev-status--warning')).toBeVisible();
    });

});

// ──────────────────────────────────────────────────────────────────────────────
// Form Validation
// ──────────────────────────────────────────────────────────────────────────────

test.describe('Debitorkontrolle - Form Validation', () => {

    test('should disable submit button when required fields are empty', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        await openCreateForm(page);

        // Leave mieterId at 0 (default disabled option) and betrag at 0
        const submitButton = page.locator('button[type="submit"]');
        const isDisabled = await submitButton.isDisabled();
        const errorMessage = page.locator('.zev-form-error');
        const hasError = await errorMessage.isVisible().catch(() => false);

        expect(isDisabled || hasError).toBeTruthy();
    });

    test('should show validation error for betrag = 0', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        await openCreateForm(page);

        await page.locator('#betrag').fill('0');
        await page.locator('#betrag').blur();

        await expect(page.locator('button[type="submit"]')).toBeDisabled();
    });

    test('should show error when datumVon is after datumBis', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        await openCreateForm(page);

        await page.locator('#mieterId').selectOption(await mieterIdOrFail(page));
        await page.locator('#betrag').fill('100');
        await page.locator('#datumVon').fill('2025-12-31');
        await page.locator('#datumBis').fill('2025-01-01');

        // Multiple .zev-form-error elements may be visible; use first() to avoid strict-mode violation
        await expect(page.locator('.zev-form-error').first()).toBeVisible();
        await expect(page.locator('button[type="submit"]')).toBeDisabled();
    });

    test('should show error when zahldatum is before datumBis', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        await openCreateForm(page);

        await page.locator('#mieterId').selectOption(await mieterIdOrFail(page));
        await page.locator('#betrag').fill('100');
        await page.locator('#datumVon').fill('2025-01-01');
        await page.locator('#datumBis').fill('2025-03-31');
        await page.locator('#zahldatum').fill('2025-02-01'); // Before datumBis

        await expect(page.locator('.zev-form-error').first()).toBeVisible();
        await expect(page.locator('button[type="submit"]')).toBeDisabled();
    });

});

// ──────────────────────────────────────────────────────────────────────────────
// Edit
// ──────────────────────────────────────────────────────────────────────────────

test.describe('Debitorkontrolle - Edit Debitor', () => {

    test('should edit a debitor and set zahldatum (status changes to Bezahlt)', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        const z = zeitraum(6);
        const row = await createDebitorOrFail(page, { von: z.von, bis: z.bis, betrag: '200.00' });

        await clickKebabMenuItem(page, row, 'edit');
        await expect(page.locator('form')).toBeVisible({ timeout: 5000 });

        const zahldatum = `${jahr()}-12-20`; // nach datumBis des eigenen Fensters
        await page.locator('#zahldatum').fill(zahldatum);
        await submitAndExpectSuccess(page, `Debitor ${formatToSwiss(z.von)}`);

        await setDateRange(page, z.von, z.bis);
        await waitForTableWithData(page, 10000);

        const updatedRow = page.locator(`tr:has-text("${formatToSwiss(z.von)}")`).first();
        await expect(updatedRow).toBeVisible({ timeout: 10000 });
        // Status ist jetzt "Bezahlt"
        await expect(updatedRow.locator('.zev-status--success')).toBeVisible();
        await expect(updatedRow).toContainText(formatToSwiss(zahldatum));
    });

    test('should pre-fill form when editing an existing debitor', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        const z = zeitraum(7);
        const row = await createDebitorOrFail(page, { von: z.von, bis: z.bis, betrag: '75.50' });

        await clickKebabMenuItem(page, row, 'edit');
        await expect(page.locator('form')).toBeVisible({ timeout: 5000 });

        // Formular ist mit den Werten der Zeile vorbelegt
        expect(parseFloat(await page.locator('#betrag').inputValue())).toBeCloseTo(75.50, 1);
        await expect(page.locator('#datumVon')).toHaveValue(z.von);
        await expect(page.locator('#datumBis')).toHaveValue(z.bis);
    });

});

// ──────────────────────────────────────────────────────────────────────────────
// Kebab quick actions (Heute / Gestern / Zahldatum löschen)
// ──────────────────────────────────────────────────────────────────────────────

test.describe('Debitorkontrolle - Kebab quick actions for Zahldatum', () => {

    /**
     * Open the kebab menu of a row and click the item with the given visible label.
     */
    /**
     * Bewusst **ohne** Warten auf die HTTP-Antwort: Ob die Liste danach neu lädt, ist ein
     * Implementierungsdetail — und ein Warten darauf lief je nach Timing in den Timeout. Der
     * Aufrufer prüft ohnehin das sichtbare Ergebnis (Status-Abzeichen), und diese Zusicherung
     * pollt von sich aus.
     */
    async function clickQuickAction(page: Page, row: Locator, label: string | RegExp): Promise<void> {
        await row.locator('.zev-kebab-button').click();
        await row.locator('.zev-kebab-menu--open').waitFor({ state: 'visible', timeout: 2000 });
        await row.locator('.zev-kebab-menu__item', { hasText: label }).click();
    }

    /**
     * Der Eintrag heisst „Datum löschen" (Übersetzung ZAHLDATUM_LOESCHEN), nicht
     * „Zahldatum löschen" — der alte Test suchte den falschen Text und wäre daran gescheitert,
     * wenn er nicht vorher schon still ausgestiegen wäre. Als Muster ohne Umlaut, weil ein „ö"
     * aus anderer Quelle anders kodiert sein kann als das im DOM.
     */
    const DATUM_LOESCHEN = /Datum l.schen/;

    test('should set Zahldatum via "Heute" and clear it via "Zahldatum löschen"', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        // Vergangener Zeitraum: Das Backend weist ein Zahldatum vor datumBis ab
        const z = vergangenerZeitraum();
        const row = await createDebitorOrFail(page, { von: z.von, bis: z.bis, betrag: '120.00' });

        // Neuer Eintrag startet "Offen"
        await expect(row.locator('.zev-status--warning')).toBeVisible();

        // "Heute" → Status wird "Bezahlt"
        await clickQuickAction(page, row, 'Heute');
        const paidRow = page.locator(`tr:has-text("${formatToSwiss(z.von)}")`).first();
        await expect(paidRow.locator('.zev-status--success')).toBeVisible({ timeout: 10000 });

        // "Datum löschen" → zurück auf "Offen"
        await clickQuickAction(page, paidRow, DATUM_LOESCHEN);
        const clearedRow = page.locator(`tr:has-text("${formatToSwiss(z.von)}")`).first();
        await expect(clearedRow.locator('.zev-status--warning')).toBeVisible({ timeout: 10000 });
    });

});

// ──────────────────────────────────────────────────────────────────────────────
// Delete
// ──────────────────────────────────────────────────────────────────────────────

test.describe('Debitorkontrolle - Delete Debitor', () => {

    test('should show confirmation dialog before deleting', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        const z = zeitraum(8);
        const row = await createDebitorOrFail(page, { von: z.von, bis: z.bis, betrag: '50.00' });

        // Dialog abweisen → Zeile bleibt bestehen
        let dialogMessage = '';
        page.once('dialog', async dialog => {
            dialogMessage = dialog.message();
            await dialog.dismiss();
        });

        await clickDeleteInKebab(page, row);
        await page.waitForTimeout(500);

        expect(dialogMessage).toBeTruthy();
        await expect(row).toBeVisible();
    });

    test('should delete debitor when confirmed', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        const z = zeitraum(9);
        const row = await createDebitorOrFail(page, { von: z.von, bis: z.bis, betrag: '33.33' });

        page.once('dialog', async dialog => { await dialog.accept(); });
        await clickDeleteInKebab(page, row);

        // Kein try/catch: Bleibt die Zeile stehen, ist das Löschen fehlgeschlagen
        await expect(page.locator(`tr:has-text("${formatToSwiss(z.von)}")`)).toHaveCount(0, { timeout: 10000 });
        createdDebitorDates = createdDebitorDates.filter(d => d !== z.von);
    });

});

// ──────────────────────────────────────────────────────────────────────────────
// Date Filter
// ──────────────────────────────────────────────────────────────────────────────

test.describe('Debitorkontrolle - Date Filter', () => {

    test('should filter list by date range', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        const z = zeitraum(10);
        await createDebitorOrFail(page, { von: z.von, bis: z.bis, betrag: '42.00' });

        // Eigener Zeitraum: die Zeile ist da
        await expect(page.locator(`tr:has-text("${formatToSwiss(z.von)}")`)).toBeVisible();

        // Zeitraum ohne Daten: Leer-Hinweis, und die Tabelle ist leer. Über die Tabellenzeilen
        // geprüft statt über den Datumstext - beim Umschalten stehen Leer-Hinweis und alte
        // Tabelle kurz gleichzeitig im DOM.
        // Geprüft wird die leere Liste, nicht der Leer-Hinweis: Dessen Erscheinen deckt der
        // eigene Test „should show empty state message …" ab, und beim Umschalten aus einer
        // gefüllten Liste heraus stand er nicht zuverlässig zum Prüfzeitpunkt.
        await setDateRange(page, '1900-01-01', '1900-03-31');
        await expect(page.locator('.zev-table tbody tr')).toHaveCount(0, { timeout: 10000 });

        // Zurück auf den eigenen Zeitraum: die Zeile erscheint wieder
        await setDateRange(page, z.von, z.bis);
        await expect(page.locator(`tr:has-text("${formatToSwiss(z.von)}")`)).toBeVisible({ timeout: 10000 });
    });

});

// ──────────────────────────────────────────────────────────────────────────────
// Checkbox & Bulk Delete
// ──────────────────────────────────────────────────────────────────────────────

test.describe('Debitorkontrolle - Checkbox and Bulk Delete', () => {

    test('should not show "Auswahl löschen" button when nothing is selected', async ({ page }) => {
        await navigateToDebitorkontrolle(page);

        const deleteSelectedButton = page.locator('button.zev-button--danger');
        await expect(deleteSelectedButton).not.toBeVisible();
    });

    test('should show "Auswahl löschen (N)" button when a row is checked', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        const z = zeitraum(11);
        await createDebitorOrFail(page, { von: z.von, bis: z.bis, betrag: '10.00' });

        const firstRowCheckbox = page.locator('.zev-table tbody tr').first().locator('input[type="checkbox"]');
        await firstRowCheckbox.click();

        const deleteSelectedButton = page.locator('button.zev-button--danger');
        await expect(deleteSelectedButton).toBeVisible();
        await expect(deleteSelectedButton).toContainText('1');
    });

    test('should hide "Auswahl löschen" button when selection is cleared', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        const z = zeitraum(12);
        await createDebitorOrFail(page, { von: z.von, bis: z.bis, betrag: '10.00' });

        const firstRowCheckbox = page.locator('.zev-table tbody tr').first().locator('input[type="checkbox"]');

        // Check → button appears
        await firstRowCheckbox.click();
        await expect(page.locator('button.zev-button--danger')).toBeVisible();

        // Uncheck → button disappears
        await firstRowCheckbox.click();
        await expect(page.locator('button.zev-button--danger')).not.toBeVisible();
    });

    test('should select all rows via header checkbox and show correct count', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        // Zwei Debitoren im selben Fenster: verschiedene datum_von, damit der Upsert zwei
        // Zeilen anlegt statt eine zu überschreiben.
        const z = zeitraum(13);
        const von2 = z.von.replace(/-(\d\d)$/, (_, d) => `-${String(Number(d) + 1).padStart(2, '0')}`);

        await createDebitorOrFail(page, { von: z.von, bis: z.bis, betrag: '11.00' });
        await createDebitorOrFail(page, { von: von2, bis: z.bis, betrag: '12.00' });

        await setDateRange(page, z.von, z.bis);
        await waitForTableWithData(page, 10000);
        const rowCount = await page.locator('.zev-table tbody tr').count();
        expect(rowCount).toBeGreaterThanOrEqual(2);

        // Click header checkbox to select all
        await page.locator('.zev-table thead input[type="checkbox"]').click();

        const deleteSelectedButton = page.locator('button.zev-button--danger');
        await expect(deleteSelectedButton).toBeVisible();
        await expect(deleteSelectedButton).toContainText(`${rowCount}`);
    });

    test('should reset selection when date range changes', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        const z = zeitraum(14);
        await createDebitorOrFail(page, { von: z.von, bis: z.bis, betrag: '10.00' });

        const firstRowCheckbox = page.locator('.zev-table tbody tr').first().locator('input[type="checkbox"]');
        await firstRowCheckbox.click();
        await expect(page.locator('button.zev-button--danger')).toBeVisible();

        // Change date range (triggers list reload → selection reset)
        await setDateRange(page, '1900-01-01', '1900-03-31');

        await expect(page.locator('button.zev-button--danger')).not.toBeVisible();
    });

    test('should bulk delete selected rows via "Auswahl löschen" button', async ({ page }) => {
        await navigateToDebitorkontrolle(page);
        const z = zeitraum(15);
        await createDebitorOrFail(page, { von: z.von, bis: z.bis, betrag: '10.00' });

        // Select the row
        await page.locator('.zev-table tbody tr').first().locator('input[type="checkbox"]').click();

        // Accept the confirmation dialog and click "Auswahl löschen"
        page.once('dialog', async dialog => { await dialog.accept(); });
        await page.locator('button.zev-button--danger').click();

        // Kein Ausweichen: Die Zeile muss verschwinden
        await expect(page.locator(`tr:has-text("${formatToSwiss(z.von)}")`)).toHaveCount(0, { timeout: 10000 });
        createdDebitorDates = createdDebitorDates.filter(d => d !== z.von);
    });

});
