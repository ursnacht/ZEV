import { test, expect, Locator, Page } from '@playwright/test';
import { clickKebabMenuItem, navigateViaMenu, waitForFormResult, waitForTableWithData } from './helpers';

/**
 * tests / ladestationen.spec.ts
 * E2E-Tests für das Feature "Ladestationen" (Specs/Ladestationen.md):
 *   - Einheit vom Typ LADESTATION anlegen/bearbeiten/löschen, RFID im Feld `messpunkt`
 *   - Eindeutigkeit der RFID (nur für LADESTATION, nicht für andere Typen)
 *   - Mieter mit mehreren Einheiten (Wohnung + Ladestation) und Mieter ohne Wohnung
 *   - Mieter ohne Einheit wird abgewiesen
 *   - Löschschutz: Einheit mit Mieter, Mieter mit Positionen, Tarif mit Positionen
 *   - Rechnung: Mieter mit Wohnung + Ladestation erhält EINE Rechnung (die der Wohnung),
 *     die übersprungene Ladestation wird namentlich gemeldet
 *
 * Abgrenzung zu `ladestromtarif.spec.ts`: Dort steht die Erfassungsmaske
 * (Tarifpositionen, Sortierung, Kopieren, Rechnung des Nutzers OHNE Wohnung) im Zentrum.
 * Hier liegt der Schwerpunkt auf dem Datenmodell rundherum - Einheit, Mieter-Zuordnung,
 * Löschschutz und die Abgrenzung "eine Rechnung je Mieter".
 *
 * WICHTIG (mandantenweiter Zustand → serial + ein Browser):
 * Alle Tests teilen sich denselben Keycloak-Mandanten (testuser). Einheiten, Mieter und
 * Tarife sind mandantenweit sichtbar; ein zweiter Browser oder parallele Tests würden sich
 * gegenseitig Vorbedingungen zerstören (z.B. die Regel "höchstens ein Mieter ohne Mietende
 * je Einheit" oder die Überschneidungsprüfung der Tarife) - analog ladestromtarif.spec.ts.
 */
test.describe.configure({ mode: 'serial', timeout: 180000 });

test.beforeEach(({ browserName }) => {
    test.skip(browserName !== 'chromium',
        'Einheiten, Mieter und Tarife sind mandantenweiter Zustand; nur in einem Browser testen.');
});

/**
 * Namenspräfix aller Testdaten. Bewusst mit "ZZ" beginnend: Die Einheiten-Auswahl ist
 * alphabetisch sortiert, und andere Suites (mieter-verwaltung.spec.ts) greifen sich die
 * *erste* Einheit der Liste. Ein früh einsortierter Testname würde dort mitgenommen und
 * beide Suites blockierten sich beim Aufräumen.
 */
const PREFIX = 'ZZ-LS';

/** Rechnungszeitraum des Tests - Q2/2026 ist durch ZEV-/VNB-Tarife abgedeckt. */
const RECHNUNG_VON = '2026-04-01';
const RECHNUNG_BIS = '2026-06-30';
const RECHNUNG_JAHR = '2026';
const RECHNUNG_QUARTAL = '2';

/** Preis des Test-Ladestromtarifs; 100 kWh ergeben damit glatte 50.00 CHF. */
const TARIF_PREIS = '0.50000';
/**
 * Gültigkeit des Test-Tarifs. Fern in der Zukunft und ausserhalb der Bereiche von
 * ladestromtarif.spec.ts (2093-2098), damit die Überschneidungsprüfung für LADESTROM-Tarife
 * weder echte Tarife noch die Parallel-Suite trifft. Für die Position ist die Gültigkeit
 * ohne Belang - sie trägt Jahr und Quartal selbst.
 */
const TARIF_VON = '2089-01-01';
const TARIF_BIS = '2089-12-31';

// Aufräum-Register (pro Test zurückgesetzt)
let createdEinheitNames: string[] = [];
let createdMieterNames: string[] = [];
let createdTarifNames: string[] = [];
let einheitenMitPositionen: string[] = [];

/**
 * Laufkennung, damit sich Testdaten verschiedener Läufe nicht in die Quere kommen.
 * Bewusst nur die letzten sieben Stellen des Zeitstempels: Einheiten-Name und
 * Tarif-Bezeichnung sind im Formular auf `maxlength="30"` begrenzt - ein längerer Name
 * würde beim Tippen stillschweigend abgeschnitten, und die Suche nach der Tabellenzeile
 * fände anschliessend nichts (genau so ist dieser Test beim ersten Lauf gescheitert).
 */
const RUN_ID = Date.now().toString().slice(-7);

/** Eindeutiger Name für Einheit oder Tarif - hart auf die 30 Zeichen des Formulars begrenzt. */
function testName(suffix: string): string {
    const name = `${PREFIX}${RUN_ID} ${suffix}`;
    if (name.length > 30) {
        throw new Error(`Testname "${name}" ist länger als die 30 Zeichen des Formularfelds`);
    }
    return name;
}

/** Eindeutiger Mieter-Name; das Feld fasst 150 Zeichen, hier gilt die 30er-Grenze nicht. */
function mieterName(suffix: string): string {
    return `${PREFIX}${RUN_ID} ${suffix}`;
}

// ---------------------------------------------------------------------------
// Navigation
// ---------------------------------------------------------------------------

async function navigateToEinheiten(page: Page): Promise<void> {
    await navigateViaMenu(page, '/einheiten');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
    await waitForTableWithData(page, 10000);
}

async function navigateToMieter(page: Page): Promise<void> {
    await navigateViaMenu(page, '/mieter');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
    await waitForTableWithData(page, 10000);
}

async function navigateToTarife(page: Page): Promise<void> {
    await navigateViaMenu(page, '/tarife');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
    await waitForTableWithData(page, 10000);
}

async function navigateToTarifpositionen(page: Page): Promise<void> {
    await navigateViaMenu(page, '/tarifpositionen');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
    await page.locator('#einheitAuswahl').waitFor({ state: 'visible', timeout: 15000 });
}

// ---------------------------------------------------------------------------
// Meldungen
// ---------------------------------------------------------------------------

/** Schliesst eine offene Fehlermeldung, damit sie nicht als Ergebnis der nächsten Aktion gilt. */
async function dismissError(page: Page): Promise<void> {
    const error = page.locator('.zev-message--error');
    if (await error.isVisible().catch(() => false)) {
        await error.click();
        await expect(error).not.toBeVisible({ timeout: 5000 });
    }
}

/**
 * Wartet, bis keine Meldung mehr sichtbar ist.
 *
 * Zwingend vor jedem Absenden: Erfolgsmeldungen blenden sich erst nach 5 Sekunden aus. Eine
 * noch stehende Meldung der vorherigen Aktion würde sonst als Ergebnis der nächsten gewertet -
 * ein abgewiesener Speicherversuch sähe dann wie ein erfolgreicher aus.
 */
async function clearMessages(page: Page): Promise<void> {
    await dismissError(page);
    const success = page.locator('.zev-message--success');
    if (await success.isVisible().catch(() => false)) {
        await expect(success).not.toBeVisible({ timeout: 10000 });
    }
}

/** Schliesst ein offenes Formular über "Abbrechen", falls eines sichtbar ist. */
async function closeOpenForm(page: Page): Promise<void> {
    const form = page.locator('form');
    if (await form.isVisible().catch(() => false)) {
        const cancel = form.locator('button.zev-button--secondary');
        if (await cancel.isVisible().catch(() => false)) {
            await cancel.click();
            await expect(form).not.toBeVisible({ timeout: 5000 });
        }
    }
}

// ---------------------------------------------------------------------------
// Einheiten
// ---------------------------------------------------------------------------

interface EinheitDaten {
    name: string;
    /** Beschriftung im Typ-Dropdown, z.B. 'Ladestation' oder 'Konsument'. */
    typ: 'LADESTATION' | 'CONSUMER' | 'PRODUCER';
    messpunkt?: string;
}

/** Legt eine Einheit an. Erwartet die geöffnete Einheiten-Liste. */
async function createEinheit(page: Page, daten: EinheitDaten): Promise<boolean> {
    await closeOpenForm(page);
    await page.locator('button.zev-button--primary').first().click();
    await expect(page.locator('form')).toBeVisible();

    await page.locator('#name').fill(daten.name);
    // Das Feld ist auf 30 Zeichen begrenzt und kürzt ohne Rückmeldung - ein gekürzter Name
    // liefe erst später als "Zeile nicht gefunden" auf.
    await expect(page.locator('#name')).toHaveValue(daten.name);
    await page.locator('#typ').selectOption(daten.typ);
    await page.locator('#messpunkt').fill(daten.messpunkt ?? '');

    await clearMessages(page);
    await page.locator('button[type="submit"]').click();
    return waitForFormResult(page, 20000);
}

/** Legt eine Einheit an und registriert sie fürs Aufräumen. Wirft, wenn das misslingt. */
async function createEinheitOrFail(page: Page, daten: EinheitDaten): Promise<void> {
    createdEinheitNames.push(daten.name);
    if (!await createEinheit(page, daten)) {
        const text = await page.locator('.zev-message--error').textContent().catch(() => '');
        throw new Error(`Einheit "${daten.name}" konnte nicht angelegt werden: ${text}`);
    }
    await waitForTableWithData(page, 10000);
    await expect(page.locator(`tr:has-text("${daten.name}")`)).toBeVisible({ timeout: 10000 });
}

async function deleteEinheitByName(page: Page, name: string): Promise<boolean> {
    page.removeAllListeners('dialog');
    try {
        await navigateToEinheiten(page);
        await closeOpenForm(page);

        const row = page.locator(`tr:has-text("${name}")`);

        // Wartende Pruefung: `isVisible()` fragt ohne zu warten, die Zeile erscheint aber erst mit
        // der Antwort der Listenabfrage. Zu frueh gefragt hiess "nicht vorhanden" - und der
        // Datensatz blieb stillschweigend in der Datenbank.
        const vorhanden = await row.first().waitFor({ state: 'visible', timeout: 10000 })
            .then(() => true).catch(() => false);
        if (!vorhanden) {
            return true;
        }
        page.once('dialog', async dialog => { await dialog.accept(); });
        await clickKebabMenuItem(page, row.first(), 'delete');
        await expect(row).toHaveCount(0, { timeout: 10000 });
        return true;
    } catch (error) {
        console.error(`CLEANUP FEHLGESCHLAGEN: Einheit "${name}" - ${error}`);
        return false;
    } finally {
        page.removeAllListeners('dialog');
    }
}

/** Löscht eine Einheit über das Kebab-Menü und liefert die Zeile zur weiteren Prüfung. */
async function deleteEinheitViaUi(page: Page, name: string): Promise<Locator> {
    const row = page.locator(`tr:has-text("${name}")`).first();
    await expect(row).toBeVisible({ timeout: 10000 });
    page.once('dialog', async dialog => { await dialog.accept(); });
    await clickKebabMenuItem(page, row, 'delete');
    return row;
}

// ---------------------------------------------------------------------------
// Mieter
// ---------------------------------------------------------------------------

interface MieterDaten {
    name: string;
    /** Namen der zuzuordnenden Einheiten (Teilstring der Beschriftung genügt). */
    einheitNamen: string[];
    mietbeginn: string;
    mietende?: string;
    strasse?: string;
}

/** Hakt eine Einheit im Mieter-Formular an bzw. ab. */
function einheitCheckbox(page: Page, einheitName: string): Locator {
    return page.locator('.zev-checkbox-item')
        .filter({ hasText: einheitName })
        .locator('input[type="checkbox"]')
        .first();
}

/**
 * Legt einen Mieter an. Die Zuordnung ist seit Specs/Ladestationen.md eine Mehrfachauswahl:
 * angehakt wird jede Einheit, deren Beschriftung einen der übergebenen Namen enthält.
 */
async function createMieter(page: Page, daten: MieterDaten): Promise<boolean> {
    await closeOpenForm(page);
    await page.locator('button.zev-button--primary').first().click();
    await expect(page.locator('form')).toBeVisible();

    for (const einheitName of daten.einheitNamen) {
        await einheitCheckbox(page, einheitName).check();
    }
    await page.locator('#name').fill(daten.name);
    await page.locator('#strasse').fill(daten.strasse ?? 'Ladestrasse 1');
    await page.locator('#plz').fill('3000');
    await page.locator('#ort').fill('Bern');
    await page.locator('#mietbeginn').fill(daten.mietbeginn);
    if (daten.mietende) {
        await page.locator('#mietende').fill(daten.mietende);
    }

    await clearMessages(page);
    await page.locator('button[type="submit"]').click();
    return waitForFormResult(page, 20000);
}

/** Legt einen Mieter an und registriert ihn fürs Aufräumen. Wirft, wenn das misslingt. */
async function createMieterOrFail(page: Page, daten: MieterDaten): Promise<void> {
    createdMieterNames.push(daten.name);
    if (!await createMieter(page, daten)) {
        const text = await page.locator('.zev-message--error').textContent().catch(() => '');
        throw new Error(`Mieter "${daten.name}" konnte nicht angelegt werden: ${text}`);
    }
    await waitForTableWithData(page, 10000);
    await expect(page.locator(`tr:has-text("${daten.name}")`)).toBeVisible({ timeout: 10000 });
}

async function deleteMieterByName(page: Page, name: string): Promise<boolean> {
    page.removeAllListeners('dialog');
    try {
        await navigateToMieter(page);
        await closeOpenForm(page);

        const row = page.locator(`tr:has-text("${name}")`);

        const vorhanden = await row.first().waitFor({ state: 'visible', timeout: 10000 })
            .then(() => true).catch(() => false);
        if (!vorhanden) {
            return true;
        }
        page.once('dialog', async dialog => { await dialog.accept(); });
        await clickKebabMenuItem(page, row.first(), 'delete');
        await expect(row).toHaveCount(0, { timeout: 10000 });
        return true;
    } catch (error) {
        console.error(`CLEANUP FEHLGESCHLAGEN: Mieter "${name}" - ${error}`);
        return false;
    } finally {
        page.removeAllListeners('dialog');
    }
}

/** Löscht einen Mieter über das Kebab-Menü und liefert die Zeile zur weiteren Prüfung. */
async function deleteMieterViaUi(page: Page, name: string): Promise<Locator> {
    const row = page.locator(`tr:has-text("${name}")`).first();
    await expect(row).toBeVisible({ timeout: 10000 });
    page.once('dialog', async dialog => { await dialog.accept(); });
    await clickKebabMenuItem(page, row, 'delete');
    return row;
}

// ---------------------------------------------------------------------------
// Tarife
// ---------------------------------------------------------------------------

/** Legt einen LADESTROM-Tarif an und registriert ihn fürs Aufräumen. */
async function createLadestromTarifOrFail(page: Page, name: string): Promise<void> {
    createdTarifNames.push(name);
    await closeOpenForm(page);
    await page.locator('button.zev-button--primary').first().click();
    await expect(page.locator('form')).toBeVisible();

    await page.locator('#tariftyp').selectOption('LADESTROM');
    await page.locator('#bezeichnung').fill(name);
    // 30 Zeichen Maximum, siehe createEinheit
    await expect(page.locator('#bezeichnung')).toHaveValue(name);
    await page.locator('#preis').fill(TARIF_PREIS);
    await page.locator('#gueltigVon').fill(TARIF_VON);
    await page.locator('#gueltigBis').fill(TARIF_BIS);

    await clearMessages(page);
    await page.locator('button[type="submit"]').click();
    if (!await waitForFormResult(page, 20000)) {
        const text = await page.locator('.zev-message--error').textContent().catch(() => '');
        throw new Error(`Ladestrom-Tarif "${name}" konnte nicht angelegt werden: ${text}`);
    }
    await waitForTableWithData(page, 10000);
    await expect(page.locator(`tr:has-text("${name}")`)).toBeVisible({ timeout: 10000 });
}

async function deleteTarifByName(page: Page, name: string): Promise<boolean> {
    page.removeAllListeners('dialog');
    try {
        await navigateToTarife(page);
        await closeOpenForm(page);

        const row = page.locator(`tr:has-text("${name}")`);

        const vorhanden = await row.first().waitFor({ state: 'visible', timeout: 10000 })
            .then(() => true).catch(() => false);
        if (!vorhanden) {
            return true;
        }
        page.once('dialog', async dialog => { await dialog.accept(); });
        await clickKebabMenuItem(page, row.first(), 'delete');
        await expect(row).toHaveCount(0, { timeout: 10000 });
        return true;
    } catch (error) {
        console.error(`CLEANUP FEHLGESCHLAGEN: Tarif "${name}" - ${error}`);
        return false;
    } finally {
        page.removeAllListeners('dialog');
    }
}

// ---------------------------------------------------------------------------
// Tarifpositionen
// ---------------------------------------------------------------------------

/**
 * Wählt eine Ladestation in der Auswahlliste und wartet, bis deren Liste steht.
 *
 * Auf die HTTP-Antwort warten statt auf "Tabelle ODER Leer-Hinweis": Der Leer-Hinweis steht
 * schon vor der Antwort auf der Seite, ein Warten darauf liefe ins Leere und liesse Tests
 * (und vor allem das Aufräumen) eine noch ungefüllte Liste sehen.
 */
async function selectEinheit(page: Page, name: string): Promise<void> {
    const option = page.locator('#einheitAuswahl option').filter({ hasText: name }).first();
    const value = await option.getAttribute('value');
    await Promise.all([
        page.waitForResponse(r => r.url().includes('/api/tarifpositionen')
            && r.request().method() === 'GET' && r.status() === 200, { timeout: 20000 }),
        page.locator('#einheitAuswahl').selectOption(value ?? { label: name })
    ]);
    await page.locator('button.zev-button--primary').first().waitFor({ state: 'visible', timeout: 10000 });
}

interface PositionsDaten {
    tarif: string;
    jahr: string;
    quartal: string;
    menge: string;
}

/** Erfasst eine Position für die aktuell gewählte Einheit. */
async function createPosition(page: Page, daten: PositionsDaten): Promise<boolean> {
    await dismissError(page);
    await page.locator('button.zev-button--primary').first().click();
    await expect(page.locator('form')).toBeVisible();

    await page.locator('#tarifId').selectOption({ label: daten.tarif });
    await page.locator('#jahr').selectOption({ label: daten.jahr });
    await page.locator('#quartal').selectOption({ label: `Q${daten.quartal}` });
    await page.locator('#menge').fill(daten.menge);

    await clearMessages(page);
    await page.locator('button[type="submit"]').click();
    return waitForFormResult(page, 20000);
}

/** Erfasst eine Position und registriert die Einheit fürs Aufräumen. Wirft bei Misserfolg. */
async function createPositionOrFail(page: Page, einheitName: string, daten: PositionsDaten): Promise<void> {
    einheitenMitPositionen.push(einheitName);
    if (!await createPosition(page, daten)) {
        const text = await page.locator('.zev-message--error').textContent().catch(() => '');
        throw new Error(`Position für "${einheitName}" konnte nicht erfasst werden: ${text}`);
    }
}

/** Löscht alle Positionen einer Einheit über die Erfassungsseite. */
async function deletePositionen(page: Page, einheitName: string): Promise<void> {
    try {
        page.removeAllListeners('dialog');
        await navigateToTarifpositionen(page);
        // Die Einheit kann bereits gelöscht sein (Cascade räumt die Positionen dann mit auf)
        const option = page.locator('#einheitAuswahl option').filter({ hasText: einheitName });
        if (await option.count() === 0) {
            return;
        }
        await selectEinheit(page, einheitName);
        await closeOpenForm(page);

        for (let i = 0; i < 10; i++) {
            const rows = page.locator('.zev-table tbody tr');
            const count = await rows.count();
            if (count === 0) {
                break;
            }
            page.once('dialog', async dialog => { await dialog.accept(); });
            await clickKebabMenuItem(page, rows.first(), 'delete');
            await expect(rows).toHaveCount(count - 1, { timeout: 10000 });
        }
    } catch (error) {
        console.error(`CLEANUP FEHLGESCHLAGEN: Fehler beim Löschen der Positionen von "${einheitName}": ${error}`);
        page.removeAllListeners('dialog');
    }
}

// ---------------------------------------------------------------------------
// Rechnung
// ---------------------------------------------------------------------------

/** Wandelt einen Schweizer Betrag ("1'234.55") in eine Zahl. */
function parseBetrag(text: string): number {
    return Number(text.replace(/['\s]/g, ''));
}

/**
 * Erzeugt die Rechnungen für die genannten Einheiten im Testzeitraum.
 * Liefert die Zeilen der Ergebnistabelle; wirft bei einer Fehlermeldung.
 */
async function generateRechnungen(page: Page, einheitNamen: string[]): Promise<Locator> {
    await navigateViaMenu(page, '/rechnungen');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });

    for (const name of einheitNamen) {
        const item = page.locator('.zev-checkbox-item').filter({ hasText: name }).first();
        await item.waitFor({ state: 'visible', timeout: 15000 });
        const checkbox = item.locator('input[type="checkbox"]');
        if (!await checkbox.isChecked()) {
            await checkbox.click();
        }
    }

    await page.locator('#dateFrom').fill(RECHNUNG_VON);
    await page.locator('#dateTo').fill(RECHNUNG_BIS);
    await page.locator('button[type="submit"]').click();

    const success = page.locator('.zev-message--success');
    const warning = page.locator('.zev-message--warning');
    const error = page.locator('.zev-message--error');
    await expect(success.or(warning).or(error)).toBeVisible({ timeout: 60000 });
    if (await error.isVisible().catch(() => false)) {
        throw new Error(`Rechnungserzeugung fehlgeschlagen: ${await error.textContent()}`);
    }
    return page.locator('.zev-panel .zev-table tbody tr');
}

// ---------------------------------------------------------------------------
// Vor- und Nachbereitung
// ---------------------------------------------------------------------------

test.beforeEach(() => {
    createdEinheitNames = [];
    createdMieterNames = [];
    createdTarifNames = [];
    einheitenMitPositionen = [];
});

/**
 * Raeumt die angelegten Datensaetze ab — und **scheitert sichtbar**, wenn das nicht gelingt.
 *
 * <p>Vorher wurde jeder Fehlschlag nur auf die Konsole geschrieben: Die Suite blieb gruen, waehrend
 * die Datenbank volllief. Ein Rueckstand ist ein Befund und gehoert gemeldet. Ein zweiter Versuch
 * davor, weil ein einzelnes Loeschen an einer stehenden Meldung oder einem offenen Formular
 * scheitern kann.
 *
 * <p>Reihenfolge zwingend: Positionen verweisen auf den Tarif und blockieren das Loeschen des
 * Mieters; der Mieter belegt die Einheit (Loeschschutz); die Einheit traegt die Positionen.
 */
test.afterEach(async ({ page, browserName }) => {
    if (browserName !== 'chromium') {
        return;
    }
    const gescheitert: string[] = [];

    /** Zweiter Versuch, bevor ein Rueckstand gemeldet wird. */
    async function raeumeAb(was: string, loeschen: () => Promise<boolean>): Promise<void> {
        let erfolg = await loeschen();
        if (!erfolg) {
            erfolg = await loeschen();
        }
        if (!erfolg) {
            gescheitert.push(was);
        }
    }

    for (const name of [...new Set(einheitenMitPositionen)]) {
        await deletePositionen(page, name);
    }
    for (const name of createdMieterNames) {
        await raeumeAb(`Mieter ${name}`, () => deleteMieterByName(page, name));
    }
    for (const name of createdEinheitNames) {
        await raeumeAb(`Einheit ${name}`, () => deleteEinheitByName(page, name));
    }
    for (const name of createdTarifNames) {
        await raeumeAb(`Tarif ${name}`, () => deleteTarifByName(page, name));
    }
    einheitenMitPositionen = [];
    createdMieterNames = [];
    createdEinheitNames = [];
    createdTarifNames = [];

    expect(gescheitert,
        `Testdaten blieben in der Datenbank zurueck: ${gescheitert.join(', ')}`).toEqual([]);
});

// ---------------------------------------------------------------------------
// Einheiten-Verwaltung
// ---------------------------------------------------------------------------

test.describe('Ladestationen - Einheiten-Verwaltung', () => {
    test('should create, edit and delete a charging unit carrying its RFID in the messpunkt field',
        async ({ page }) => {
            await navigateToEinheiten(page);
            await page.locator('button.zev-button--primary').first().click();
            await expect(page.locator('form')).toBeVisible();

            // Der neue Typ steht im bestehenden Dropdown
            await expect(page.locator('#typ option[value="LADESTATION"]')).toHaveCount(1);

            // Der Hinweis zum Messpunkt wechselt für Ladestationen auf die RFID.
            // Bewusst auf die Messpunkt-Gruppe eingeschränkt: Verbraucher zeigen einen
            // zweiten Hinweis (Nebenkosten-Relevanz), sonst greift Playwrights Strict Mode.
            await page.locator('#typ').selectOption('CONSUMER');
            const hint = page.locator('.zev-form-group:has(#messpunkt) .zev-form-hint');
            await expect(hint).not.toContainText('RFID');
            await page.locator('#typ').selectOption('LADESTATION');
            await expect(hint).toContainText('RFID');

            const name = testName('Station');
            const rfid = `RFID-${Date.now()}`;
            createdEinheitNames.push(name);
            await page.locator('#name').fill(name);
            await page.locator('#messpunkt').fill(rfid);
            await page.locator('button[type="submit"]').click();
            expect(await waitForFormResult(page, 20000)).toBe(true);

            await waitForTableWithData(page, 10000);
            const row = page.locator(`tr:has-text("${name}")`);
            await expect(row).toBeVisible({ timeout: 10000 });
            await expect(row).toContainText('Ladestation');
            await expect(row).toContainText(rfid);

            // Bearbeiten: Typ bleibt LADESTATION, RFID wird ausgetauscht (Mieterwechsel-Fall)
            const neueRfid = `${rfid}-NEU`;
            await clickKebabMenuItem(page, row, 'edit');
            await expect(page.locator('form')).toBeVisible({ timeout: 5000 });
            await expect(page.locator('#typ')).toHaveValue('LADESTATION');
            await expect(page.locator('#messpunkt')).toHaveValue(rfid);
            await page.locator('#messpunkt').fill(neueRfid);
            await clearMessages(page);
            await page.locator('button[type="submit"]').click();
            expect(await waitForFormResult(page, 20000)).toBe(true);

            await waitForTableWithData(page, 10000);
            await expect(page.locator(`tr:has-text("${name}")`)).toContainText(neueRfid);

            // Löschen: ohne zugeordneten Mieter zulässig
            const zeile = await deleteEinheitViaUi(page, name);
            await expect(zeile).toHaveCount(0, { timeout: 10000 });
            createdEinheitNames = createdEinheitNames.filter(n => n !== name);
        });

    test('should reject a duplicate RFID for a second charging unit but allow it for other types',
        async ({ page }) => {
            await navigateToEinheiten(page);

            const rfid = `RFID-DUP-${Date.now()}`;
            const ersteStation = testName('Station A');
            await createEinheitOrFail(page, { name: ersteStation, typ: 'LADESTATION', messpunkt: rfid });

            // Zweite Ladestation mit derselben RFID -> abgewiesen, kein Datensatz
            const zweiteStation = testName('Station B');
            expect(await createEinheit(page, {
                name: zweiteStation, typ: 'LADESTATION', messpunkt: rfid
            })).toBe(false);
            await expect(page.locator('.zev-message--error'))
                .toContainText('bereits einer anderen Ladestation zugeordnet');
            await closeOpenForm(page);
            await expect(page.locator(`tr:has-text("${zweiteStation}")`)).toHaveCount(0);

            // Die Eindeutigkeit gilt nur für LADESTATION: derselbe Messpunkt an einem anderen
            // Typ bleibt erlaubt (BEZUG und RUECKLIEFERUNG teilen sich bewusst einen Messpunkt;
            // sie sind je Mandant Singletons und lassen sich hier nicht anlegen, deshalb der
            // gleichwertige Nachweis über einen Konsumenten).
            const konsument = testName('Konsument');
            await createEinheitOrFail(page, { name: konsument, typ: 'CONSUMER', messpunkt: rfid });

            // Die eigene RFID beim Bearbeiten beizubehalten darf nicht als Dublette gelten
            // (die Prüfung muss die eigene ID ausnehmen)
            await clearMessages(page);
            const row = page.locator(`tr:has-text("${ersteStation}")`).first();
            await clickKebabMenuItem(page, row, 'edit');
            await expect(page.locator('form')).toBeVisible({ timeout: 5000 });
            const umbenannt = testName('Station C');
            await page.locator('#name').fill(umbenannt);
            createdEinheitNames = createdEinheitNames.map(n => n === ersteStation ? umbenannt : n);
            await page.locator('button[type="submit"]').click();
            expect(await waitForFormResult(page, 20000)).toBe(true);

            await waitForTableWithData(page, 10000);
            const umbenannteZeile = page.locator(`tr:has-text("${umbenannt}")`);
            await expect(umbenannteZeile).toBeVisible({ timeout: 10000 });
            await expect(umbenannteZeile).toContainText(rfid);
        });

    test('should refuse deleting a charging unit while a tenant is assigned', async ({ page }) => {
        const station = testName('Station Mieter');
        await navigateToEinheiten(page);
        await createEinheitOrFail(page, { name: station, typ: 'LADESTATION', messpunkt: `RFID-M-${Date.now()}` });

        // Nutzer ohne Wohnung: einzige Zuordnung ist die Ladestation
        const mieter = mieterName('Nutzer ohne Wohnung');
        await navigateToMieter(page);
        await createMieterOrFail(page, {
            name: mieter, einheitNamen: [station], mietbeginn: '2010-01-01'
        });
        await expect(page.locator(`tr:has-text("${mieter}")`)).toContainText(station);

        // Löschen der Einheit wird abgewiesen - Meldung mit Anzahl
        await navigateToEinheiten(page);
        const row = await deleteEinheitViaUi(page, station);
        const error = page.locator('.zev-message--error');
        await expect(error).toBeVisible({ timeout: 10000 });
        await expect(error).toContainText('Einheit kann nicht gelöscht werden');
        await expect(error).toContainText('1 Mieter');
        await expect(row).toBeVisible();

        // Ohne Zuordnung ist die Einheit löschbar
        await deleteMieterByName(page, mieter);
        createdMieterNames = createdMieterNames.filter(n => n !== mieter);

        await navigateToEinheiten(page);
        const zeile = await deleteEinheitViaUi(page, station);
        await expect(zeile).toHaveCount(0, { timeout: 10000 });
        createdEinheitNames = createdEinheitNames.filter(n => n !== station);
    });
});

// ---------------------------------------------------------------------------
// Mieterverwaltung
// ---------------------------------------------------------------------------

test.describe('Ladestationen - Mieterverwaltung', () => {
    test('should refuse saving a tenant without any assigned unit', async ({ page }) => {
        const station = testName('Station Pflicht');
        await navigateToEinheiten(page);
        await createEinheitOrFail(page, { name: station, typ: 'LADESTATION', messpunkt: `RFID-P-${Date.now()}` });

        await navigateToMieter(page);
        await page.locator('button.zev-button--primary').first().click();
        await expect(page.locator('form')).toBeVisible();

        // Alle übrigen Pflichtfelder gefüllt - es fehlt einzig die Einheit
        await page.locator('#name').fill(mieterName('Ohne Einheit'));
        await page.locator('#strasse').fill('Teststrasse 1');
        await page.locator('#plz').fill('3000');
        await page.locator('#ort').fill('Bern');
        await page.locator('#mietbeginn').fill('2010-01-01');

        await expect(page.locator('.zev-form-error')).toContainText('Einheit ist erforderlich');
        await expect(page.locator('button[type="submit"]')).toBeDisabled();

        // Mit Zuordnung ist das Formular gültig ...
        const checkbox = einheitCheckbox(page, station);
        await checkbox.check();
        await expect(page.locator('button[type="submit"]')).toBeEnabled();

        // ... und ohne die letzte Zuordnung wieder ungültig
        await checkbox.uncheck();
        await expect(page.locator('.zev-form-error')).toContainText('Einheit ist erforderlich');
        await expect(page.locator('button[type="submit"]')).toBeDisabled();
    });

    test('should assign an apartment and a charging station to one tenant and keep both after editing',
        async ({ page }) => {
            const wohnung = testName('Wohnung');
            const station = testName('Station Zwei');
            await navigateToEinheiten(page);
            await createEinheitOrFail(page, { name: wohnung, typ: 'CONSUMER', messpunkt: `MP-W-${Date.now()}` });
            await createEinheitOrFail(page, { name: station, typ: 'LADESTATION', messpunkt: `RFID-W-${Date.now()}` });

            const mieter = mieterName('Mieter Zwei Einheiten');
            await navigateToMieter(page);
            await createMieterOrFail(page, {
                name: mieter, einheitNamen: [wohnung, station], mietbeginn: '2010-01-01'
            });

            // Die Liste zeigt beide zugeordneten Einheiten
            const row = page.locator(`tr:has-text("${mieter}")`).first();
            await expect(row).toContainText(wohnung);
            await expect(row).toContainText(station);

            // Bearbeiten: beide Haken stehen weiterhin
            await clickKebabMenuItem(page, row, 'edit');
            await expect(page.locator('form')).toBeVisible({ timeout: 5000 });
            await expect(einheitCheckbox(page, wohnung)).toBeChecked();
            await expect(einheitCheckbox(page, station)).toBeChecked();

            // Speichern einer anderen Änderung darf die Zuordnung nicht verlieren
            await page.locator('#strasse').fill('Geänderte Strasse 9');
            await clearMessages(page);
            await page.locator('button[type="submit"]').click();
            expect(await waitForFormResult(page, 20000)).toBe(true);

            await waitForTableWithData(page, 10000);
            const updated = page.locator(`tr:has-text("${mieter}")`).first();
            await expect(updated).toContainText('Geänderte Strasse 9');
            await expect(updated).toContainText(wohnung);
            await expect(updated).toContainText(station);
        });

    test('should refuse deleting a tenant while positions exist on one of their units',
        async ({ page }) => {
            const tarif = testName('Tarif Mieter');
            await navigateToTarife(page);
            await createLadestromTarifOrFail(page, tarif);

            const station = testName('Station Position');
            await navigateToEinheiten(page);
            await createEinheitOrFail(page, { name: station, typ: 'LADESTATION', messpunkt: `RFID-Q-${Date.now()}` });

            const mieter = mieterName('Mieter Position');
            await navigateToMieter(page);
            await createMieterOrFail(page, {
                name: mieter, einheitNamen: [station], mietbeginn: '2010-01-01'
            });

            await navigateToTarifpositionen(page);
            await selectEinheit(page, station);
            await createPositionOrFail(page, station, {
                tarif, jahr: RECHNUNG_JAHR, quartal: RECHNUNG_QUARTAL, menge: '10'
            });

            // Löschen wird abgewiesen, solange Positionen an einer zugeordneten Einheit hängen
            await navigateToMieter(page);
            const row = await deleteMieterViaUi(page, mieter);
            const error = page.locator('.zev-message--error');
            await expect(error).toBeVisible({ timeout: 10000 });
            await expect(error).toContainText('Mieter kann nicht gelöscht werden');
            await expect(error).toContainText('1 Tarifposition');
            await expect(row).toBeVisible();

            // Ohne Positionen ist der Mieter löschbar - die Einheit bleibt bestehen
            await deletePositionen(page, station);
            einheitenMitPositionen = einheitenMitPositionen.filter(n => n !== station);

            await navigateToMieter(page);
            const zeile = await deleteMieterViaUi(page, mieter);
            await expect(zeile).toHaveCount(0, { timeout: 10000 });
            createdMieterNames = createdMieterNames.filter(n => n !== mieter);

            await navigateToEinheiten(page);
            await expect(page.locator(`tr:has-text("${station}")`)).toBeVisible();
        });
});

// ---------------------------------------------------------------------------
// Tarifverwaltung
// ---------------------------------------------------------------------------

test.describe('Ladestationen - Tarifverwaltung', () => {
    test('should refuse deleting a tariff that positions refer to', async ({ page }) => {
        const tarif = testName('Tarif Schutz');
        await navigateToTarife(page);
        await createLadestromTarifOrFail(page, tarif);

        const station = testName('Station Tarif');
        await navigateToEinheiten(page);
        await createEinheitOrFail(page, { name: station, typ: 'LADESTATION', messpunkt: `RFID-T-${Date.now()}` });

        await navigateToTarifpositionen(page);
        await selectEinheit(page, station);
        await createPositionOrFail(page, station, {
            tarif, jahr: RECHNUNG_JAHR, quartal: RECHNUNG_QUARTAL, menge: '5'
        });

        await navigateToTarife(page);
        const row = page.locator(`tr:has-text("${tarif}")`).first();
        await expect(row).toBeVisible({ timeout: 10000 });
        page.once('dialog', async dialog => { await dialog.accept(); });
        await clickKebabMenuItem(page, row, 'delete');

        const error = page.locator('.zev-message--error');
        await expect(error).toBeVisible({ timeout: 10000 });
        await expect(error).toContainText('Tarif kann nicht gelöscht werden');
        await expect(error).toContainText('1 Tarifposition');
        await expect(row).toBeVisible();

        // Ohne Positionen ist der Tarif löschbar
        await deletePositionen(page, station);
        einheitenMitPositionen = einheitenMitPositionen.filter(n => n !== station);

        await navigateToTarife(page);
        const zeile = page.locator(`tr:has-text("${tarif}")`).first();
        page.once('dialog', async dialog => { await dialog.accept(); });
        await clickKebabMenuItem(page, zeile, 'delete');
        await expect(zeile).toHaveCount(0, { timeout: 10000 });
        createdTarifNames = createdTarifNames.filter(n => n !== tarif);
    });
});

// ---------------------------------------------------------------------------
// Tarifpositionen
// ---------------------------------------------------------------------------

test.describe('Ladestationen - Tarifpositionen', () => {
    test('should offer charging units only and prefill the source reference with the RFID',
        async ({ page }) => {
            const wohnung = testName('Wohnung Auswahl');
            const station = testName('Station Auswahl');
            const rfid = `RFID-A-${Date.now()}`;
            await navigateToEinheiten(page);
            await createEinheitOrFail(page, { name: wohnung, typ: 'CONSUMER', messpunkt: `MP-A-${Date.now()}` });
            await createEinheitOrFail(page, { name: station, typ: 'LADESTATION', messpunkt: rfid });

            await navigateToTarifpositionen(page);

            // Nur Ladestationen stehen zur Auswahl - die eben angelegte Wohnung nicht
            const optionen = (await page.locator('#einheitAuswahl option').allTextContents())
                .map(o => o.trim());
            expect(optionen.some(o => o.includes(station))).toBe(true);
            expect(optionen.some(o => o.includes(wohnung))).toBe(false);
            // Die Beschriftung nennt die RFID
            expect(optionen.find(o => o.includes(station))).toContain(rfid);

            // Die Quell-Referenz ist mit dem Messpunkt vorbelegt und änderbar
            await selectEinheit(page, station);
            await page.locator('button.zev-button--primary').first().click();
            await expect(page.locator('form')).toBeVisible();
            await expect(page.locator('#quellReferenz')).toHaveValue(rfid);
            await page.locator('#quellReferenz').fill('Handeingabe');
            await expect(page.locator('#quellReferenz')).toHaveValue('Handeingabe');
        });

    test('should warn while no tenant is assigned to the selected charging unit',
        async ({ page }) => {
            // Die Rechnung entsteht je Mieter: ohne Zuordnung erscheinen erfasste Positionen auf
            // keiner Rechnung (Specs/Ladestationen.md, Edge Case "Einheit ohne zugeordneten Mieter").
            const station = testName('Station Ohne M');
            await navigateToEinheiten(page);
            await createEinheitOrFail(page, {
                name: station, typ: 'LADESTATION', messpunkt: `RFID-O-${Date.now()}`
            });

            await navigateToTarifpositionen(page);
            await selectEinheit(page, station);
            const hinweis = page.locator('.zev-message--warning');
            await expect(hinweis).toBeVisible({ timeout: 10000 });
            await expect(hinweis).toContainText('kein Mieter zugeordnet');

            // Mit zugeordnetem Mieter verschwindet der Hinweis
            const mieter = mieterName('Nutzer Hinweis');
            await navigateToMieter(page);
            await createMieterOrFail(page, {
                name: mieter, einheitNamen: [station], mietbeginn: '2010-01-01'
            });

            await navigateToTarifpositionen(page);
            await selectEinheit(page, station);
            await expect(page.locator('.zev-message--warning')).toHaveCount(0);
        });
});

// ---------------------------------------------------------------------------
// Rechnung
// ---------------------------------------------------------------------------

test.describe('Ladestationen - Rechnung', () => {
    test('should bill a tenant with apartment and charging station on one invoice only',
        async ({ page }) => {
            test.setTimeout(300000);

            const tarif = testName('Tarif Rechnung');
            await navigateToTarife(page);
            await createLadestromTarifOrFail(page, tarif);

            const wohnung = testName('Wohnung Rechnung');
            const station = testName('Station Rechnung');
            await navigateToEinheiten(page);
            await createEinheitOrFail(page, { name: wohnung, typ: 'CONSUMER', messpunkt: `MP-R-${Date.now()}` });
            await createEinheitOrFail(page, { name: station, typ: 'LADESTATION', messpunkt: `RFID-R-${Date.now()}` });

            const mieter = mieterName('Mieter Rechnung');
            await navigateToMieter(page);
            await createMieterOrFail(page, {
                name: mieter, einheitNamen: [wohnung, station], mietbeginn: '2010-01-01'
            });

            // Lauf 1 ohne Ladestrom-Position: genau EINE Rechnung (die der Wohnung); die
            // Ladestation erzeugt keine eigene und wird namentlich gemeldet.
            const rows = await generateRechnungen(page, [wohnung, station]);
            await expect(rows).toHaveCount(1);
            await expect(rows.first().locator('td').nth(0)).toHaveText(wohnung);
            await expect(rows.first().locator('td').nth(1)).toHaveText(mieter);
            const warnung = page.locator('.zev-message--warning');
            await expect(warnung).toContainText('Keine Rechnung erzeugt für');
            await expect(warnung).toContainText(station);
            const betragOhnePosition = parseBetrag(
                (await rows.first().locator('td').nth(2).textContent()) ?? '0');

            // 100 kWh * 0.50 CHF = 50.00 - ein Vielfaches von 0.05, die Rundung des Endbetrags
            // verschiebt sich dadurch nicht und der Zuwachs ist exakt vergleichbar.
            await navigateToTarifpositionen(page);
            await selectEinheit(page, station);
            await createPositionOrFail(page, station, {
                tarif, jahr: RECHNUNG_JAHR, quartal: RECHNUNG_QUARTAL, menge: '100'
            });

            // Lauf 2: immer noch EINE Rechnung, aber um die Ladestrom-Zeile erhöht
            const rows2 = await generateRechnungen(page, [wohnung, station]);
            await expect(rows2).toHaveCount(1);
            await expect(rows2.first().locator('td').nth(0)).toHaveText(wohnung);
            await expect(page.locator('.zev-message--warning')).toContainText(station);
            const betragMitPosition = parseBetrag(
                (await rows2.first().locator('td').nth(2).textContent()) ?? '0');
            expect(betragMitPosition - betragOhnePosition).toBeCloseTo(50, 2);
        });
});
