import { test, expect, Locator, Page } from '@playwright/test';
import {
    clickKebabMenuItem, loescheZeileMitText, navigateViaMenu, openKebabMenu, waitForFormResult,
    waitForTableWithData
} from './helpers';

/**
 * tests / ladestromtarif.spec.ts
 * E2E-Tests für das Feature "Ladestromtarif" (Specs/Ladestromtarif.md):
 *   - Tariftyp LADESTROM in der Tarifverwaltung (anlegen, ändern, löschen, Überschneidung)
 *   - Kebab-Eintrag "Tarifpositionen" in der Mieterverwaltung (Sprung mit ?mieterId)
 *   - Seite /tarifpositionen: Mieter-Auswahl, Hinweis, Erfassen/Bearbeiten/Kopieren/Löschen,
 *     Sortierung, Spaltenbreiten
 *   - Wirkung einer Position auf den Rechnungsbetrag
 *
 * WICHTIG (mandantenweiter Zustand → serial + ein Browser):
 * Alle Tests teilen sich denselben Keycloak-Mandanten (testuser). Der Ladestrom-Tarif ist
 * mandantenweit: `TarifService.saveTarif` weist einen zweiten LADESTROM-Tarif mit
 * überschneidender Gültigkeit ab, und je Mieter/Quartal/Tariftyp ist nur EINE Position
 * zulässig. Parallel laufende Tests (oder ein zweiter Browser) würden sich damit
 * gegenseitig die Vorbedingungen zerstören - analog zu feature-flag-upload.spec.ts.
 */
test.describe.configure({ mode: 'serial', timeout: 150000 });

test.beforeEach(({ browserName }) => {
    test.skip(browserName !== 'chromium',
        'Ladestrom-Tarif und Tarifpositionen sind mandantenweiter Zustand; nur in einem Browser testen.');
});

/** Gemeinsam genutzter Ladestrom-Tarif (Vorbedingung der Seite /tarifpositionen). */
const TARIF_NAME = 'E2E Ladestrom';
const TARIF_PREIS = '0.50000';
/** Ferne Gültigkeit, damit die Überschneidungsprüfung nicht mit echten Tarifen kollidiert. */
const TARIF_VON = '2098-01-01';
const TARIF_BIS = '2098-12-31';

/** Eigene Ladestations-Einheit für die Positions-Tests - Anker der Tarifposition. */
const LADESTATION_NAME = 'E2E Ladestation';
/** Messpunkt einer Ladestation = RFID (Specs/Ladestationen.md); belegt die Quell-Referenz vor. */
const LADESTATION_RFID = 'RFID-E2E-SHARED';
/** Eigener Mieter der Ladestation - ohne Zuordnung erschiene keine Position auf einer Rechnung. */
const MIETER_NAME = 'E2E Ladestrom Mieter';
/**
 * Mietbeginn des Testmieters. Bewusst **ohne Mietende**: Die Ladestation ist eine eigene, neu
 * angelegte Einheit - dort gibt es keinen konkurrierenden Mieter, und nur ein offenes
 * Mietverhaeltnis deckt den Rechnungszeitraum (2026) ab.
 */
const MIETJAHRE = {
    geteilt: '2010'
};

/** Rechnungszeitraum des Tests - muss das Quartal der erfassten Position enthalten. */
const RECHNUNG_VON = '2026-04-01';
const RECHNUNG_BIS = '2026-06-30';
const RECHNUNG_JAHR = '2026';
const RECHNUNG_QUARTAL = '2';

// Aufräum-Register (pro Test zurückgesetzt)
let createdTarifNames: string[] = [];
let createdMieterNames: string[] = [];
let einheitenMitPositionen: string[] = [];

// ---------------------------------------------------------------------------
// Navigation
// ---------------------------------------------------------------------------

async function navigateToTarife(page: Page): Promise<void> {
    await navigateViaMenu(page, '/tarife');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
    await waitForTableWithData(page, 10000);
}

async function navigateToMieter(page: Page): Promise<void> {
    await navigateViaMenu(page, '/mieter');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
    await waitForTableWithData(page, 10000);
}

async function navigateToEinheiten(page: Page): Promise<void> {
    await navigateViaMenu(page, '/einheiten');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
    await waitForTableWithData(page, 10000);
}

async function navigateToTarifpositionen(page: Page): Promise<void> {
    await navigateViaMenu(page, '/tarifpositionen');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
    await page.locator('#einheitAuswahl').waitFor({ state: 'visible', timeout: 15000 });
}

/**
 * Wählt eine Ladestation in der Auswahlliste und wartet, bis deren Liste steht.
 * Die Beschriftung enthält den Messpunkt in Klammern, deshalb wird über den Options-Text
 * gesucht statt über ein exaktes Label.
 */
async function selectEinheit(page: Page, name: string): Promise<void> {
    const option = page.locator('#einheitAuswahl option').filter({ hasText: name }).first();
    const value = await option.getAttribute('value');
    // Auf die HTTP-Antwort warten statt auf "Tabelle ODER Leer-Hinweis": Der Leer-Hinweis steht
    // schon vor der Antwort auf der Seite, ein Warten darauf liefe ins Leere und liesse Tests
    // (und vor allem das Aufräumen) eine noch ungefüllte Liste sehen.
    await Promise.all([
        page.waitForResponse(r => r.url().includes('/api/tarifpositionen')
            && r.request().method() === 'GET' && r.status() === 200, { timeout: 20000 }),
        page.locator('#einheitAuswahl').selectOption(value ?? { label: name })
    ]);
    await page.locator('button.zev-button--primary').first().waitFor({ state: 'visible', timeout: 10000 });
}

/**
 * Beschriftung der gewählten Option. Nötig, weil die Selects mit `[ngValue]` arbeiten -
 * Angular vergibt dort technische Werte wie `2: 3`, die keine Aussage über die Auswahl haben.
 */
async function selectedLabel(page: Page, selector: string): Promise<string> {
    return page.locator(selector).evaluate(
        (el: HTMLSelectElement) => el.selectedOptions[0]?.textContent?.trim() ?? ''
    );
}

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

// ---------------------------------------------------------------------------
// Tarif-Helfer
// ---------------------------------------------------------------------------

interface TarifDaten {
    bezeichnung: string;
    preis?: string;
    gueltigVon: string;
    gueltigBis: string;
}

/** Legt einen LADESTROM-Tarif über die Tarifverwaltung an. Erwartet die geöffnete Liste. */
async function createLadestromTarif(page: Page, daten: TarifDaten): Promise<boolean> {
    await page.locator('button.zev-button--primary').first().click();
    await expect(page.locator('form')).toBeVisible();

    await page.locator('#tariftyp').selectOption('LADESTROM');
    await page.locator('#bezeichnung').fill(daten.bezeichnung);
    await page.locator('#preis').fill(daten.preis ?? TARIF_PREIS);
    await page.locator('#gueltigVon').fill(daten.gueltigVon);
    await page.locator('#gueltigBis').fill(daten.gueltigBis);

    await clearMessages(page);
    await page.locator('button[type="submit"]').click();
    return waitForFormResult(page, 20000);
}

/** Schliesst ein offenes Formular ueber "Abbrechen", falls eines sichtbar ist. */
async function closeOpenForm(page: Page): Promise<void> {
    const form = page.locator('form');
    if (await form.isVisible().catch(() => false)) {
        const abbrechen = form.locator('button.zev-button--secondary').first();
        if (await abbrechen.isVisible().catch(() => false)) {
            await abbrechen.click();
            await expect(form).not.toBeVisible({ timeout: 5000 });
        }
    }
}

async function deleteTarifByName(page: Page, name: string): Promise<boolean> {
    try {
        await navigateToTarife(page);
        await closeOpenForm(page);
    } catch (error) {
        console.error(`CLEANUP FEHLGESCHLAGEN: Liste fuer Tarif "${name}" - ${error}`);
        return false;
    }
    return loescheZeileMitText(page, name);
}

/**
 * Stellt den gemeinsamen Ladestrom-Tarif sicher. Bewusst idempotent: Bricht ein Lauf ab,
 * findet der nächste den Tarif vor und legt ihn nicht erneut an (die Überschneidungsprüfung
 * würde ihn sonst abweisen).
 */
async function ensureLadestromTarif(page: Page): Promise<void> {
    await navigateToTarife(page);
    const row = page.locator(`tr:has-text("${TARIF_NAME}")`);
    if (await row.first().isVisible().catch(() => false)) {
        return;
    }
    const ok = await createLadestromTarif(page, {
        bezeichnung: TARIF_NAME,
        gueltigVon: TARIF_VON,
        gueltigBis: TARIF_BIS
    });
    if (!ok) {
        throw new Error('Vorbedingung fehlgeschlagen: Ladestrom-Tarif konnte nicht angelegt werden');
    }
}

// ---------------------------------------------------------------------------
// Einheiten-Helfer
// ---------------------------------------------------------------------------

/** Legt eine Einheit vom Typ Ladestation an. Erwartet die geöffnete Einheiten-Liste. */
async function createLadestation(page: Page, name: string, rfid: string): Promise<boolean> {
    await page.locator('button.zev-button--primary').first().click();
    await expect(page.locator('form')).toBeVisible();

    await page.locator('#name').fill(name);
    await page.locator('#typ').selectOption({ label: 'Ladestation' });
    await page.locator('#messpunkt').fill(rfid);

    await clearMessages(page);
    await page.locator('button[type="submit"]').click();
    return waitForFormResult(page, 20000);
}

/** Stellt die gemeinsam genutzte Ladestation sicher (idempotent, siehe ensureLadestromTarif). */
async function ensureLadestation(page: Page): Promise<void> {
    await navigateToEinheiten(page);
    const row = page.locator(`tr:has-text("${LADESTATION_NAME}")`);
    if (await row.first().isVisible().catch(() => false)) {
        return;
    }
    if (!await createLadestation(page, LADESTATION_NAME, LADESTATION_RFID)) {
        throw new Error('Vorbedingung fehlgeschlagen: Ladestation konnte nicht angelegt werden');
    }
}

async function deleteEinheitByName(page: Page, name: string): Promise<boolean> {
    try {
        await navigateToEinheiten(page);
        await closeOpenForm(page);
    } catch (error) {
        console.error(`CLEANUP FEHLGESCHLAGEN: Liste fuer Einheit "${name}" - ${error}`);
        return false;
    }
    return loescheZeileMitText(page, name);
}

// ---------------------------------------------------------------------------
// Mieter-Helfer
// ---------------------------------------------------------------------------

interface MieterDaten {
    name: string;
    /** Name der zuzuordnenden Einheit (Teilstring genügt). */
    einheitName: string;
    /** Jahr des Mietbeginns. */
    mietjahr: string;
    /** Ohne Mietende laeuft das Mietverhaeltnis weiter - noetig, damit es den Rechnungszeitraum abdeckt. */
    befristet?: boolean;
}

/**
 * Legt einen Mieter an. Die Zuordnung ist seit Specs/Ladestationen.md eine Mehrfachauswahl:
 * angehakt wird die Einheit, deren Beschriftung `daten.einheitName` enthält.
 */
async function createMieter(page: Page, daten: MieterDaten): Promise<boolean> {
    await page.locator('button.zev-button--primary').first().click();
    await expect(page.locator('form')).toBeVisible();

    const checkbox = page.locator('.zev-checkbox-item')
        .filter({ hasText: daten.einheitName })
        .locator('input[type="checkbox"]')
        .first();
    await checkbox.check();
    await page.locator('#name').fill(daten.name);
    await page.locator('#strasse').fill('Ladestrasse 1');
    await page.locator('#plz').fill('3000');
    await page.locator('#ort').fill('Bern');
    await page.locator('#mietbeginn').fill(`${daten.mietjahr}-01-01`);
    if (daten.befristet) {
        await page.locator('#mietende').fill(`${daten.mietjahr}-12-31`);
    }

    await clearMessages(page);
    await page.locator('button[type="submit"]').click();
    return waitForFormResult(page, 20000);
}

async function deleteMieterByName(page: Page, name: string): Promise<boolean> {
    try {
        await navigateToMieter(page);
        await closeOpenForm(page);
    } catch (error) {
        console.error(`CLEANUP FEHLGESCHLAGEN: Liste fuer Mieter "${name}" - ${error}`);
        return false;
    }
    return loescheZeileMitText(page, name);
}

/** Stellt den gemeinsam genutzten Testmieter sicher (idempotent, siehe ensureLadestromTarif). */
async function ensureTestMieter(page: Page): Promise<void> {
    await navigateToMieter(page);
    const row = page.locator(`tr:has-text("${MIETER_NAME}")`);
    if (await row.first().isVisible().catch(() => false)) {
        return;
    }
    const ok = await createMieter(page, {
        name: MIETER_NAME,
        einheitName: LADESTATION_NAME,
        mietjahr: MIETJAHRE.geteilt
    });
    if (!ok) {
        throw new Error('Vorbedingung fehlgeschlagen: Testmieter konnte nicht angelegt werden');
    }
}

// ---------------------------------------------------------------------------
// Tarifpositions-Helfer
// ---------------------------------------------------------------------------

interface PositionsDaten {
    tarif?: string;
    jahr: string;
    quartal: string;
    menge: string;
    quellReferenz?: string;
    bemerkung?: string;
}

/** Öffnet das Erfassungsformular. Erwartet einen gewählten Mieter. */
async function openPositionsForm(page: Page): Promise<void> {
    await dismissError(page);
    await page.locator('button.zev-button--primary').first().click();
    await expect(page.locator('form')).toBeVisible();
}

/** Füllt das Positionsformular und sendet es ab; liefert true bei Erfolgsmeldung. */
async function submitPositionsForm(page: Page, daten: PositionsDaten): Promise<boolean> {
    if (daten.tarif) {
        await page.locator('#tarifId').selectOption({ label: daten.tarif });
    }
    await page.locator('#jahr').selectOption({ label: daten.jahr });
    await page.locator('#quartal').selectOption({ label: `Q${daten.quartal}` });
    await page.locator('#menge').fill(daten.menge);
    if (daten.quellReferenz !== undefined) {
        await page.locator('#quellReferenz').fill(daten.quellReferenz);
    }
    if (daten.bemerkung !== undefined) {
        await page.locator('#bemerkung').fill(daten.bemerkung);
    }
    await clearMessages(page);
    await page.locator('button[type="submit"]').click();
    return waitForFormResult(page, 20000);
}

/** Erfasst eine Position für den aktuell gewählten Mieter. */
async function createPosition(page: Page, daten: PositionsDaten): Promise<boolean> {
    await openPositionsForm(page);
    return submitPositionsForm(page, daten);
}

/** Zeilen der Positionsliste, die auf einen der Test-Tarife verweisen. */
function positionRows(page: Page, tarifName: string = TARIF_NAME): Locator {
    return page.locator(`.zev-table tbody tr:has-text("${tarifName}")`);
}

/** Löscht alle Positionen eines Mieters, die auf einen Test-Tarif verweisen. */
async function deletePositionen(page: Page, einheitName: string): Promise<void> {
    try {
        page.removeAllListeners('dialog');
        await navigateToTarifpositionen(page);
        // Der Mieter kann bereits gelöscht sein (Cascade räumt die Positionen dann mit auf)
        const option = page.locator('#einheitAuswahl option').filter({ hasText: einheitName });
        if (await option.count() === 0) {
            console.log(`Cleanup: Einheit "${einheitName}" nicht mehr in der Auswahl`);
            return;
        }
        await selectEinheit(page, einheitName);

        const form = page.locator('form');
        if (await form.isVisible().catch(() => false)) {
            await form.locator('button.zev-button--secondary').click();
            await expect(form).not.toBeVisible({ timeout: 5000 });
        }

        for (let i = 0; i < 10; i++) {
            const rows = positionRows(page);
            const count = await rows.count();
            if (count === 0) {
                break;
            }
            page.once('dialog', async dialog => { await dialog.accept(); });
            await clickKebabMenuItem(page, rows.first(), 'delete');
            await expect(rows).toHaveCount(count - 1, { timeout: 10000 });
        }
    } catch (error) {
        console.log(`Cleanup: Fehler beim Löschen der Positionen von "${einheitName}": ${error}`);
        page.removeAllListeners('dialog');
    }
}

// ---------------------------------------------------------------------------
// Rechnungs-Helfer
// ---------------------------------------------------------------------------

/** Wandelt einen Schweizer Betrag ("1'234.55") in eine Zahl. */
function parseBetrag(text: string): number {
    return Number(text.replace(/['\s]/g, ''));
}

/** Erzeugt die Rechnung für genau eine Einheit und liefert den ausgewiesenen Endbetrag. */
/** Waehlt eine Einheit und den Testzeitraum in der Rechnungsmaske. */
async function waehleEinheitUndZeitraum(page: Page, einheitName: string): Promise<void> {
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });

    const item = page.locator('.zev-checkbox-item').filter({ hasText: einheitName });
    await item.first().waitFor({ state: 'visible', timeout: 15000 });
    const checkbox = item.first().locator('input[type="checkbox"]');
    if (!await checkbox.isChecked()) {
        await checkbox.click();
    }

    await page.locator('#dateFrom').fill(RECHNUNG_VON);
    await page.locator('#dateTo').fill(RECHNUNG_BIS);
}

async function generateRechnungBetrag(page: Page, einheitName: string): Promise<number> {
    await navigateViaMenu(page, '/rechnungen');
    await waehleEinheitUndZeitraum(page, einheitName);

    await page.locator('button[type="submit"]').click();

    const success = page.locator('.zev-message--success');
    const error = page.locator('.zev-message--error');
    await expect(success.or(error)).toBeVisible({ timeout: 60000 });
    if (await error.isVisible().catch(() => false)) {
        throw new Error(`Rechnungserzeugung fehlgeschlagen: ${await error.textContent()}`);
    }

    const row = page.locator('.zev-panel .zev-table tbody tr').first();
    await expect(row).toBeVisible({ timeout: 10000 });
    return parseBetrag((await row.locator('td').nth(2).textContent()) ?? '0');
}

// ---------------------------------------------------------------------------
// Vor- und Nachbereitung
// ---------------------------------------------------------------------------

test.beforeAll(async ({ browser, browserName }) => {
    if (browserName !== 'chromium') {
        return;
    }
    test.setTimeout(150000);
    const page = await browser.newPage();
    try {
        await ensureLadestromTarif(page);
        await ensureLadestation(page);
        await ensureTestMieter(page);
    } finally {
        await page.close();
    }
});

test.afterAll(async ({ browser, browserName }) => {
    if (browserName !== 'chromium') {
        return;
    }
    test.setTimeout(150000);
    const page = await browser.newPage();
    try {
        // Reihenfolge zwingend: Positionen verweisen auf den Tarif (ON DELETE RESTRICT),
        // der Mieter belegt die Einheit (Löschschutz), die Einheit trägt die Positionen.
        await deletePositionen(page, LADESTATION_NAME);
        const gescheitert: string[] = [];
        if (!await deleteMieterByName(page, MIETER_NAME)) {
            gescheitert.push(`Mieter ${MIETER_NAME}`);
        }
        if (!await deleteEinheitByName(page, LADESTATION_NAME)) {
            gescheitert.push(`Einheit ${LADESTATION_NAME}`);
        }
        if (!await deleteTarifByName(page, TARIF_NAME)) {
            gescheitert.push(`Tarif ${TARIF_NAME}`);
        }
        expect(gescheitert,
            `Testdaten blieben in der Datenbank zurueck: ${gescheitert.join(', ')}`).toEqual([]);
    } finally {
        await page.close();
    }
});

test.beforeEach(() => {
    createdTarifNames = [];
    createdMieterNames = [];
    einheitenMitPositionen = [];
});

/**
 * Raeumt die angelegten Datensaetze ab — und **scheitert sichtbar**, wenn das nicht gelingt.
 *
 * <p>Vorher wurde jeder Fehlschlag nur auf die Konsole geschrieben: Die Suite blieb gruen, waehrend
 * die Datenbank volllief.
 */
test.afterEach(async ({ page, browserName }) => {
    if (browserName !== 'chromium') {
        return;
    }
    const gescheitert: string[] = [];

    async function raeumeAb(was: string, loeschen: () => Promise<boolean>): Promise<void> {
        let erfolg = await loeschen();
        if (!erfolg) {
            erfolg = await loeschen();
        }
        if (!erfolg) {
            gescheitert.push(was);
        }
    }

    for (const name of einheitenMitPositionen) {
        await deletePositionen(page, name);
    }
    for (const name of createdMieterNames) {
        await raeumeAb(`Mieter ${name}`, () => deleteMieterByName(page, name));
    }
    for (const name of createdTarifNames) {
        await raeumeAb(`Tarif ${name}`, () => deleteTarifByName(page, name));
    }
    einheitenMitPositionen = [];
    createdMieterNames = [];
    createdTarifNames = [];

    expect(gescheitert,
        `Testdaten blieben in der Datenbank zurueck: ${gescheitert.join(', ')}`).toEqual([]);
});

// ---------------------------------------------------------------------------
// Tarifverwaltung
// ---------------------------------------------------------------------------

test.describe('Ladestromtarif - Tarifverwaltung', () => {
    test('should offer LADESTROM in the tariff type dropdown and create such a tariff', async ({ page }) => {
        await navigateToTarife(page);

        await page.locator('button.zev-button--primary').first().click();
        await expect(page.locator('form')).toBeVisible();
        await expect(page.locator('#tariftyp option[value="LADESTROM"]')).toHaveCount(1);

        const name = 'E2E LS Anlegen';
        createdTarifNames.push(name);
        await page.locator('#tariftyp').selectOption('LADESTROM');
        await page.locator('#bezeichnung').fill(name);
        await page.locator('#preis').fill('0.42000');
        await page.locator('#gueltigVon').fill('2096-01-01');
        await page.locator('#gueltigBis').fill('2096-12-31');
        await page.locator('button[type="submit"]').click();

        expect(await waitForFormResult(page, 20000)).toBe(true);

        await waitForTableWithData(page, 10000);
        const row = page.locator(`tr:has-text("${name}")`);
        await expect(row).toBeVisible({ timeout: 10000 });
        // Typ, Preis und Gültigkeit stehen in der Liste
        await expect(row.locator('.tarif-typ-badge')).toHaveText(/ladestrom|charging/i);
        await expect(row).toContainText('0.42');
        await expect(row).toContainText('31.12.2096');
    });

    test('should reject a second LADESTROM tariff with overlapping validity but accept an adjacent one',
        async ({ page }) => {
            await navigateToTarife(page);

            const ersterName = 'E2E LS Ueberlap';
            createdTarifNames.push(ersterName);
            expect(await createLadestromTarif(page, {
                bezeichnung: ersterName,
                gueltigVon: '2095-01-01',
                gueltigBis: '2095-12-31'
            })).toBe(true);

            await waitForTableWithData(page, 10000);
            await expect(page.locator(`tr:has-text("${ersterName}")`)).toBeVisible({ timeout: 10000 });

            // Überschneidend -> muss abgewiesen werden
            const ueberlappend = 'E2E LS Konflikt';
            const abgewiesen = await createLadestromTarif(page, {
                bezeichnung: ueberlappend,
                gueltigVon: '2095-07-01',
                gueltigBis: '2096-06-30'
            });
            expect(abgewiesen).toBe(false);
            await expect(page.locator('.zev-message--error')).toBeVisible();

            // Anschliessender, überschneidungsfreier Zeitraum ist zulässig
            const anschliessend = 'E2E LS Anschluss';
            createdTarifNames.push(anschliessend);
            await clearMessages(page);
            await page.locator('#bezeichnung').fill(anschliessend);
            await page.locator('#gueltigVon').fill('2096-01-01');
            await page.locator('#gueltigBis').fill('2096-12-31');
            await page.locator('button[type="submit"]').click();
            expect(await waitForFormResult(page, 20000)).toBe(true);

            await waitForTableWithData(page, 10000);
            await expect(page.locator(`tr:has-text("${anschliessend}")`)).toBeVisible({ timeout: 10000 });
            // Der abgewiesene Tarif darf nicht existieren
            await expect(page.locator(`tr:has-text("${ueberlappend}")`)).toHaveCount(0);
        });

    test('should edit and delete a LADESTROM tariff', async ({ page }) => {
        await navigateToTarife(page);

        const name = 'E2E LS Aendern';
        createdTarifNames.push(name);
        expect(await createLadestromTarif(page, {
            bezeichnung: name,
            gueltigVon: '2093-01-01',
            gueltigBis: '2093-12-31'
        })).toBe(true);

        await waitForTableWithData(page, 10000);
        const row = page.locator(`tr:has-text("${name}")`);
        await expect(row).toBeVisible({ timeout: 10000 });

        // Bearbeiten: Typ bleibt LADESTROM, Preis ändern
        await clickKebabMenuItem(page, row, 'edit');
        await expect(page.locator('form')).toBeVisible({ timeout: 5000 });
        await expect(page.locator('#tariftyp')).toHaveValue('LADESTROM');
        await page.locator('#preis').fill('0.66000');
        await clearMessages(page);
        await page.locator('button[type="submit"]').click();
        expect(await waitForFormResult(page, 20000)).toBe(true);

        await waitForTableWithData(page, 10000);
        await expect(page.locator(`tr:has-text("${name}")`)).toContainText('0.66');

        // Löschen
        page.once('dialog', async dialog => { await dialog.accept(); });
        await clickKebabMenuItem(page, page.locator(`tr:has-text("${name}")`), 'delete');
        await expect(page.locator(`tr:has-text("${name}")`)).toHaveCount(0, { timeout: 10000 });
        createdTarifNames = createdTarifNames.filter(n => n !== name);
    });
});

// ---------------------------------------------------------------------------
// Einheiten-Verwaltung: Kebab-Sprung
// ---------------------------------------------------------------------------

test.describe('Ladestromtarif - Einheiten', () => {
    test('should jump from the Einheit kebab menu to the Tarifpositionen page with the unit preselected',
        async ({ page }) => {
            await navigateToEinheiten(page);

            const row = page.locator(`tr:has-text("${LADESTATION_NAME}")`);
            await expect(row).toBeVisible({ timeout: 10000 });

            await openKebabMenu(page, row);
            // Reihenfolge: Bearbeiten, Tarifpositionen, Löschen (danger)
            const tarifpositionen = row.locator('.zev-kebab-menu__item:not(.zev-kebab-menu__item--danger)').nth(1);
            await expect(tarifpositionen).toContainText(/tarifposition/i);
            await tarifpositionen.click();

            await page.waitForURL(/\/tarifpositionen\?einheitId=\d+/, { timeout: 15000 });

            // Die Einheit ist vorausgewählt
            const auswahl = page.locator('#einheitAuswahl');
            await expect(auswahl).toBeVisible();
            await expect.poll(() => selectedLabel(page, '#einheitAuswahl'), { timeout: 10000 })
                .toContain(LADESTATION_NAME);
        });
});

// ---------------------------------------------------------------------------
// Tarifpositionen
// ---------------------------------------------------------------------------

test.describe('Ladestromtarif - Tarifpositionen', () => {
    test('should show the multiple-billing hint and keep it hidden after dismissing it', async ({ page }) => {
        await navigateToTarifpositionen(page);

        const hinweis = page.locator('.zev-message--info');
        await expect(hinweis).toBeVisible();

        await hinweis.click();
        await expect(hinweis).not.toBeVisible();

        // Die Entscheidung wird pro Browser in localStorage gemerkt
        await page.reload({ waitUntil: 'domcontentloaded' });
        await page.locator('#einheitAuswahl').waitFor({ state: 'visible', timeout: 15000 });
        await expect(page.locator('.zev-message--info')).toHaveCount(0);
    });

    test('should require a unit selection and show a hint instead of an empty table', async ({ page }) => {
        await navigateToTarifpositionen(page);

        // Ohne gewählte Einheit weder Liste noch Erfassen-Schaltfläche
        await expect(page.locator('.zev-table')).toHaveCount(0);
        await expect(page.locator('button.zev-button--primary')).toHaveCount(0);

        await selectEinheit(page, LADESTATION_NAME);
        await expect(page.locator('button.zev-button--primary')).toBeVisible();
        // Keine Positionen -> Hinweistext statt leerem Tabellengerüst
        await expect(page.locator('.zev-table')).toHaveCount(0);
        await expect(page.locator('p.zev-text--muted')).toBeVisible();
    });

    test('should offer only manually captured tariff types and reject a negative quantity',
        async ({ page }) => {
        await navigateToTarifpositionen(page);
        await selectEinheit(page, LADESTATION_NAME);
        await openPositionsForm(page);

        // Manuell erfassbar sind LADESTROM und GRUNDGEBUEHR (Specs/Ladestromtarif.md FR-6)
        const optionen = await page.locator('#tarifId option').allTextContents();
        const waehlbar = optionen.map(o => o.trim()).filter(o => !/wählen|select/i.test(o));
        expect(waehlbar.length).toBeGreaterThan(0);
        expect(waehlbar).toContain(TARIF_NAME);
        // ZEV-, VNB- und Grundgebuehr-Tarife der Basisdaten duerfen nicht erscheinen
        expect(waehlbar.some(o => /vZEV|Strombezug|Messgebühr/i.test(o))).toBe(false);

        await page.locator('#tarifId').selectOption({ label: TARIF_NAME });
        await expect(page.locator('label[for="menge"]')).toContainText('kWh');
        await page.locator('#menge').fill('-5');
        await expect(page.locator('button[type="submit"]')).toBeDisabled();

        await page.locator('#menge').fill('0');
        await expect(page.locator('button[type="submit"]')).toBeEnabled();
    });

    test('should create positions for several quarters, record the source reference and show the origin',
        async ({ page }) => {
            await navigateToTarifpositionen(page);
            await selectEinheit(page, LADESTATION_NAME);
            einheitenMitPositionen.push(LADESTATION_NAME);

            await openPositionsForm(page);

            expect(await submitPositionsForm(page, {
                tarif: TARIF_NAME,
                jahr: '2027',
                quartal: '1',
                menge: '123.5',
                quellReferenz: LADESTATION_RFID,
                bemerkung: 'E2E Beleg'
            })).toBe(true);

            // Zweites Quartal für denselben Mieter
            expect(await createPosition(page, {
                tarif: TARIF_NAME,
                jahr: '2027',
                quartal: '2',
                menge: '10'
            })).toBe(true);

            const rows = positionRows(page);
            await expect(rows).toHaveCount(2);

            const q1 = page.locator('.zev-table tbody tr:has-text("Q1/2027")');
            await expect(q1).toContainText(TARIF_NAME);
            // Menge 3 Nachkommastellen, Preis 5, Betrag 2 (123.5 * 0.5 = 61.75)
            await expect(q1).toContainText('123.500');
            await expect(q1).toContainText('0.50000');
            await expect(q1).toContainText('61.75');
            // Herkunft: manuell erfasst, mit Quell-Referenz
            await expect(q1).toContainText(/manuell|manual/i);
            await expect(q1).toContainText(LADESTATION_RFID);
        });

    test('should reject a second position for the same unit, quarter and tariff type', async ({ page }) => {
        // Zweiter LADESTROM-Tarif: die Eindeutigkeit gilt je Tariftyp, nicht je Tarif
        await navigateToTarife(page);
        const zweitTarif = 'E2E Zweittarif';
        createdTarifNames.push(zweitTarif);
        expect(await createLadestromTarif(page, {
            bezeichnung: zweitTarif,
            gueltigVon: '2097-01-01',
            gueltigBis: '2097-12-31'
        })).toBe(true);

        await navigateToTarifpositionen(page);
        await selectEinheit(page, LADESTATION_NAME);
        einheitenMitPositionen.push(LADESTATION_NAME);

        expect(await createPosition(page, {
            tarif: TARIF_NAME,
            jahr: '2027',
            quartal: '3',
            menge: '20'
        })).toBe(true);
        await expect(positionRows(page)).toHaveCount(1);

        // Gleicher Mieter, gleiches Quartal, anderer LADESTROM-Tarif -> abgewiesen
        const abgewiesen = await createPosition(page, {
            tarif: zweitTarif,
            jahr: '2027',
            quartal: '3',
            menge: '5'
        });
        expect(abgewiesen).toBe(false);
        await expect(page.locator('.zev-message--error')).toBeVisible();

        await page.locator('form button.zev-button--secondary').click();
        await expect(page.locator('.zev-table tbody tr')).toHaveCount(1);
    });

    test('should copy a position via the kebab menu and reject an unchanged copy', async ({ page }) => {
        await navigateToTarifpositionen(page);
        await selectEinheit(page, LADESTATION_NAME);
        einheitenMitPositionen.push(LADESTATION_NAME);

        expect(await createPosition(page, {
            tarif: TARIF_NAME,
            jahr: '2027',
            quartal: '3',
            menge: '30',
            quellReferenz: 'LP-KOPIE',
            bemerkung: 'Original'
        })).toBe(true);

        const rows = positionRows(page);
        await expect(rows).toHaveCount(1);

        // Kopieren = zweiter Nicht-Danger-Eintrag im Kebab-Menü
        await openKebabMenu(page, rows.first());
        await rows.first().locator('.zev-kebab-menu__item:not(.zev-kebab-menu__item--danger)').nth(1).click();

        // Formular im Anlege-Modus (ohne ID), aber mit allen Werten der Vorlage
        await expect(page.locator('form')).toBeVisible();
        await expect(page.locator('.form-container h2')).toContainText(/neue position|add position/i);
        await expect(page.locator('#menge')).toHaveValue('30');
        await expect(page.locator('#quellReferenz')).toHaveValue('LP-KOPIE');
        await expect(page.locator('#bemerkung')).toHaveValue('Original');
        expect(await selectedLabel(page, '#quartal')).toBe('Q3');
        expect(await selectedLabel(page, '#jahr')).toBe('2027');
        expect(await selectedLabel(page, '#tarifId')).toBe(TARIF_NAME);

        // Unverändert gespeichert -> Duplikat-Meldung, kein zweiter Datensatz
        await clearMessages(page);
        await page.locator('button[type="submit"]').click();
        expect(await waitForFormResult(page, 20000)).toBe(false);

        // Mit anderem Quartal wird die Kopie angelegt, das Original bleibt unverändert
        await clearMessages(page);
        await page.locator('#quartal').selectOption({ label: 'Q4' });
        await page.locator('button[type="submit"]').click();
        expect(await waitForFormResult(page, 20000)).toBe(true);

        await expect(rows).toHaveCount(2);
        await expect(page.locator('.zev-table tbody tr:has-text("Q3/2027")')).toContainText('30.000');
        await expect(page.locator('.zev-table tbody tr:has-text("Q4/2027")')).toContainText('30.000');
    });

    test('should edit a position via the kebab menu', async ({ page }) => {
        await navigateToTarifpositionen(page);
        await selectEinheit(page, LADESTATION_NAME);
        einheitenMitPositionen.push(LADESTATION_NAME);

        expect(await createPosition(page, {
            tarif: TARIF_NAME,
            jahr: '2027',
            quartal: '1',
            menge: '10'
        })).toBe(true);

        const rows = positionRows(page);
        await expect(rows).toHaveCount(1);

        await clickKebabMenuItem(page, rows.first(), 'edit');
        await expect(page.locator('.form-container h2')).toContainText(/bearbeiten|edit position/i);
        await expect(page.locator('#menge')).toHaveValue('10');

        await page.locator('#menge').fill('42.5');
        await clearMessages(page);
        await page.locator('button[type="submit"]').click();
        expect(await waitForFormResult(page, 20000)).toBe(true);

        await expect(rows).toHaveCount(1);
        // 42.5 kWh * 0.50 = 21.25
        await expect(rows.first()).toContainText('42.500');
        await expect(rows.first()).toContainText('21.25');
    });

    test('should delete a position via the kebab menu after confirmation', async ({ page }) => {
        await navigateToTarifpositionen(page);
        await selectEinheit(page, LADESTATION_NAME);
        einheitenMitPositionen.push(LADESTATION_NAME);

        expect(await createPosition(page, {
            tarif: TARIF_NAME,
            jahr: '2027',
            quartal: '4',
            menge: '7'
        })).toBe(true);

        const rows = positionRows(page);
        await expect(rows).toHaveCount(1);

        // Abbrechen im Bestätigungsdialog lässt die Position bestehen
        let dialogMessage = '';
        page.once('dialog', async dialog => {
            dialogMessage = dialog.message();
            await dialog.dismiss();
        });
        await clickKebabMenuItem(page, rows.first(), 'delete');
        await expect.poll(() => dialogMessage, { timeout: 5000 }).not.toBe('');
        await expect(rows).toHaveCount(1);

        // Bestätigt wird gelöscht
        page.once('dialog', async dialog => { await dialog.accept(); });
        await clickKebabMenuItem(page, rows.first(), 'delete');
        await expect(rows).toHaveCount(0, { timeout: 10000 });
        await expect(page.locator('p.zev-text--muted')).toBeVisible();
    });

    test('should sort every data column and keep the sorting after creating a position', async ({ page }) => {
        await navigateToTarifpositionen(page);
        await selectEinheit(page, LADESTATION_NAME);
        einheitenMitPositionen.push(LADESTATION_NAME);

        expect(await createPosition(page, {
            tarif: TARIF_NAME, jahr: '2026', quartal: '4', menge: '100'
        })).toBe(true);
        expect(await createPosition(page, {
            tarif: TARIF_NAME, jahr: '2027', quartal: '1', menge: '5'
        })).toBe(true);

        const rows = page.locator('.zev-table tbody tr');
        await expect(rows).toHaveCount(2);

        const quartalHeader = page.locator('.zev-table thead th', { hasText: /quartal|quarter/i }).first();
        const mengeHeader = page.locator('.zev-table thead th', { hasText: /menge|quantity/i }).first();

        // Startsortierung: Quartal absteigend -> jüngstes Quartal zuerst
        await expect(rows.first()).toContainText('Q1/2027');

        // Quartal aufsteigend: chronologisch über die Jahresgrenze hinweg (Q4/2026 vor Q1/2027)
        await quartalHeader.click();
        await expect(rows.first()).toContainText('Q4/2026');
        await quartalHeader.click();
        await expect(rows.first()).toContainText('Q1/2027');

        // Menge aufsteigend / absteigend
        await mengeHeader.click();
        await expect(rows.first()).toContainText('5.000');
        await mengeHeader.click();
        await expect(rows.first()).toContainText('100.000');

        // Jede Datenspalte ist sortierbar und zeigt den Indikator
        const sortable = page.locator('.zev-table thead th.zev-table__header--sortable');
        const count = await sortable.count();
        expect(count).toBe(6);
        for (let i = 0; i < count; i++) {
            await sortable.nth(i).click();
            await expect(sortable.nth(i).locator('.zev-table__sort-indicator')).toBeVisible();
            await sortable.nth(i).click();
            await expect(sortable.nth(i).locator('.zev-table__sort-indicator')).toBeVisible();
        }

        // Sortierung nach Menge absteigend setzen und eine Position erfassen -> bleibt erhalten
        await mengeHeader.click();
        await expect(mengeHeader.locator('.zev-table__sort-indicator')).toBeVisible();
        if (!(await rows.first().textContent())?.includes('100.000')) {
            await mengeHeader.click();
        }
        await expect(rows.first()).toContainText('100.000');

        expect(await createPosition(page, {
            tarif: TARIF_NAME, jahr: '2027', quartal: '2', menge: '50'
        })).toBe(true);

        await expect(rows).toHaveCount(3);
        await expect(mengeHeader.locator('.zev-table__sort-indicator')).toBeVisible();
        await expect(rows.first()).toContainText('100.000');
        await expect(rows.nth(1)).toContainText('50.000');
        await expect(rows.nth(2)).toContainText('5.000');
    });

    test('should allow resizing the column widths', async ({ page }) => {
        await navigateToTarifpositionen(page);
        await selectEinheit(page, LADESTATION_NAME);
        einheitenMitPositionen.push(LADESTATION_NAME);

        expect(await createPosition(page, {
            tarif: TARIF_NAME, jahr: '2027', quartal: '1', menge: '1'
        })).toBe(true);

        const firstHeader = page.locator('.zev-table thead th').first();
        const handle = firstHeader.locator('.zev-table__resize-handle');
        await expect(handle).toBeAttached({ timeout: 10000 });

        const before = (await firstHeader.boundingBox())!.width;
        const box = (await handle.boundingBox())!;
        await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
        await page.mouse.down();
        await page.mouse.move(box.x + box.width / 2 + 90, box.y + box.height / 2, { steps: 10 });
        await page.mouse.up();

        await expect.poll(async () => (await firstHeader.boundingBox())!.width, { timeout: 5000 })
            .toBeGreaterThan(before + 40);
    });
});

// ---------------------------------------------------------------------------
// Rechnung
// ---------------------------------------------------------------------------

test.describe('Ladestromtarif - Rechnung', () => {
    test('should bill a charging unit whose tenant has no apartment, but not for a zero quantity',
        async ({ page }) => {
            test.setTimeout(240000);

            // Menge 0 ist speicherbar, erzeugt aber keine Rechnungszeile - und fuer eine
            // Ladestation damit gar keine Rechnung. Der Hinweis nennt die Einheit namentlich.
            await navigateToTarifpositionen(page);
            await selectEinheit(page, LADESTATION_NAME);
            einheitenMitPositionen.push(LADESTATION_NAME);

            expect(await createPosition(page, {
                tarif: TARIF_NAME,
                jahr: RECHNUNG_JAHR,
                quartal: RECHNUNG_QUARTAL,
                menge: '0'
            })).toBe(true);

            await navigateViaMenu(page, '/rechnungen');
            await waehleEinheitUndZeitraum(page, LADESTATION_NAME);
            await page.locator('button[type="submit"]').click();
            const hinweis = page.locator('.zev-message--warning');
            await expect(hinweis).toBeVisible({ timeout: 60000 });
            await expect(hinweis).toContainText(LADESTATION_NAME);

            // Menge > 0 -> der Mieter der Ladestation hat keine Wohnung, also entsteht genau
            // eine Rechnung mit ausschliesslich dieser Zeile: 100 kWh * 0.50 CHF = 50.00.
            await navigateToTarifpositionen(page);
            await selectEinheit(page, LADESTATION_NAME);
            const rows = positionRows(page);
            await expect(rows).toHaveCount(1);
            await clickKebabMenuItem(page, rows.first(), 'edit');
            await expect(page.locator('#menge')).toBeVisible();
            await page.locator('#menge').fill('100');
            await clearMessages(page);
            await page.locator('button[type="submit"]').click();
            expect(await waitForFormResult(page, 20000)).toBe(true);

            expect(await generateRechnungBetrag(page, LADESTATION_NAME)).toBeCloseTo(50, 2);
        });
});
