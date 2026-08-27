import { test, expect, Locator, Page } from '@playwright/test';
import {
    clickKebabMenuItem, loescheZeileMitText, navigateViaMenu, waitForFormResult, waitForTableWithData
} from './helpers';

/**
 * tests / tarifpositionen.spec.ts
 * E2E-Tests für den Tariftyp `ZUSATZ` (Specs/Tarifpositionen.md):
 *   - Tarifverwaltung: Mengeneinheit nur bei ZUSATZ, Pflichtfeld, Preis-Label folgt der Einheit
 *   - Mehrere gleichzeitig gültige ZUSATZ-Tarife (Überschneidungsprüfung ausgenommen)
 *   - Tarifpositionen: Checkbox „Nur Ladestationen", Wohnungen als Ausnahme
 *   - Position an einer Wohnung erfassen; an Wohnungen ist nur ZUSATZ zulässig
 *
 * WICHTIG (mandantenweiter Zustand → serial + ein Browser): Tarife, Einheiten und Mieter sind
 * mandantenweit sichtbar; parallele Browser würden sich die Vorbedingungen zerstören - analog
 * ladestromtarif.spec.ts.
 */
test.describe.configure({ mode: 'serial', timeout: 180000 });

test.beforeEach(({ browserName }) => {
    test.skip(browserName !== 'chromium',
        'Tarife, Einheiten und Mieter sind mandantenweiter Zustand; nur in einem Browser testen.');
});

/**
 * Laufkennung. Einheiten-Name und Tarif-Bezeichnung sind auf 30 Zeichen begrenzt und würden
 * darüber hinaus stillschweigend gekürzt - die Zeile wäre danach nicht mehr auffindbar.
 */
const RUN_ID = Date.now().toString().slice(-6);

/** Gültigkeit fern in der Zukunft, ausserhalb der Bereiche der anderen Suites. */
const TARIF_VON = '2085-01-01';
const TARIF_BIS = '2085-12-31';

const WOHNUNG_NAME = `E2E-Z${RUN_ID} Wohnung`;
const MIETER_NAME = `E2E Zusatz Mieter ${RUN_ID}`;
const SAUNA = `E2E-Z${RUN_ID} Sauna`;
const WASCHKUECHE = `E2E-Z${RUN_ID} Wasch`;

// Aufräum-Register (pro Test zurückgesetzt)
let createdTarifNames: string[] = [];
let createdMieterNames: string[] = [];
let createdEinheitNames: string[] = [];
let einheitenMitPositionen: string[] = [];

// ---------------------------------------------------------------------------
// Navigation und Meldungen
// ---------------------------------------------------------------------------

async function navigateTo(page: Page, route: string): Promise<void> {
    await navigateViaMenu(page, route);
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
}

/**
 * Wartet, bis keine Meldung mehr steht. Zwingend vor jedem Absenden: Erfolgsmeldungen blenden
 * sich erst nach 5 Sekunden aus und würden sonst als Ergebnis der nächsten Aktion gewertet.
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

async function closeOpenForm(page: Page): Promise<void> {
    const form = page.locator('form');
    if (await form.isVisible().catch(() => false)) {
        const cancel = form.locator('button.zev-button--secondary').first();
        if (await cancel.isVisible().catch(() => false)) {
            await cancel.click();
            await expect(form).not.toBeVisible({ timeout: 5000 });
        }
    }
}

// ---------------------------------------------------------------------------
// Tarife
// ---------------------------------------------------------------------------

/**
 * Mengeneinheit im Formular. Bewusst der ASCII-Schlüssel und **kein** Anzeigetext: Ein „ü" aus
 * einer anderen Quelle kann als kombinierende Diärese kodiert sein und ist dann nicht
 * byte-gleich mit dem vorkomponierten „ü" im DOM — der Vergleich scheitert unsichtbar.
 */
type MengeneinheitKey = 'KWH' | 'MONAT' | 'STUECK';

/** Position der Einheit im Dropdown; Index 0 ist der Platzhalter. */
const EINHEIT_INDEX: Record<MengeneinheitKey, number> = { KWH: 1, MONAT: 2, STUECK: 3 };

/** Legt einen ZUSATZ-Tarif an. Erwartet die geöffnete Tarif-Liste. */
async function createZusatzTarif(page: Page, bezeichnung: string, einheit: MengeneinheitKey,
                                 preis = '5.00000'): Promise<void> {
    createdTarifNames.push(bezeichnung);
    await closeOpenForm(page);
    await page.locator('button.zev-button--primary').first().click();
    await expect(page.locator('form')).toBeVisible();

    await page.locator('#tariftyp').selectOption('ZUSATZ');

    // Das Mengeneinheit-Feld erscheint erst durch das @if am Tariftyp. Auf die Optionen warten,
    // bevor gewaehlt wird - sonst laeuft selectOption in ein "did not find some options", und
    // die Fehlermeldung nennt nur das Symptom.
    const einheitFeld = page.locator('#mengeneinheit');
    await expect(einheitFeld).toBeVisible({ timeout: 10000 });
    await expect(einheitFeld.locator('option')).toHaveCount(4, { timeout: 10000 });
    // Ueber den Index waehlen: Das Select bindet mit [ngValue], Angular vergibt dort technische
    // Werte wie "1: STUECK" - ein selectOption('STUECK') findet nichts.
    await einheitFeld.selectOption({ index: EINHEIT_INDEX[einheit] });
    // Gegenprobe am Wert (ASCII), damit ein falscher Index nicht unbemerkt bleibt
    await expect(einheitFeld).toHaveValue(new RegExp(`${einheit}$`));
    await page.locator('#bezeichnung').fill(bezeichnung);
    await expect(page.locator('#bezeichnung')).toHaveValue(bezeichnung);
    await page.locator('#preis').fill(preis);
    await page.locator('#gueltigVon').fill(TARIF_VON);
    await page.locator('#gueltigBis').fill(TARIF_BIS);

    await clearMessages(page);
    await page.locator('button[type="submit"]').click();
    if (!await waitForFormResult(page, 20000)) {
        const text = await page.locator('.zev-message--error').textContent().catch(() => '');
        throw new Error(`Zusatz-Tarif "${bezeichnung}" konnte nicht angelegt werden: ${text?.trim()}`);
    }
    await waitForTableWithData(page, 10000);
    await expect(page.locator(`tr:has-text("${bezeichnung}")`)).toBeVisible({ timeout: 10000 });
}

async function deleteZeileByName(page: Page, route: string, name: string): Promise<boolean> {
    try {
        await navigateTo(page, route);
        await closeOpenForm(page);
        await waitForTableWithData(page, 10000);
    } catch (error) {
        console.error(`CLEANUP FEHLGESCHLAGEN: Liste ${route} fuer "${name}" - ${error}`);
        return false;
    }
    return loescheZeileMitText(page, name);
}

// ---------------------------------------------------------------------------
// Einheit und Mieter
// ---------------------------------------------------------------------------

/** Legt eine eigene Konsumenten-Einheit an - produktive Stammdaten bleiben unberührt. */
async function createWohnung(page: Page): Promise<void> {
    createdEinheitNames.push(WOHNUNG_NAME);
    await navigateTo(page, '/einheiten');
    await waitForTableWithData(page, 10000);
    await closeOpenForm(page);

    await page.locator('button.zev-button--primary').first().click();
    await expect(page.locator('form')).toBeVisible();
    await page.locator('#name').fill(WOHNUNG_NAME);
    await expect(page.locator('#name')).toHaveValue(WOHNUNG_NAME);
    await page.locator('#typ').selectOption('CONSUMER');
    await page.locator('#messpunkt').fill(`MP-Z${RUN_ID}`);

    await clearMessages(page);
    await page.locator('button[type="submit"]').click();
    expect(await waitForFormResult(page, 20000)).toBe(true);
    await expect(page.locator(`tr:has-text("${WOHNUNG_NAME}")`)).toBeVisible({ timeout: 10000 });
}

/** Ordnet der Wohnung einen Mieter zu - ohne Mieter erschiene keine Position auf einer Rechnung. */
async function createMieter(page: Page): Promise<void> {
    createdMieterNames.push(MIETER_NAME);
    await navigateTo(page, '/mieter');
    await waitForTableWithData(page, 10000);
    await closeOpenForm(page);

    await page.locator('button.zev-button--primary').first().click();
    await expect(page.locator('form')).toBeVisible();

    const checkbox = page.locator('.zev-checkbox-item').filter({ hasText: WOHNUNG_NAME })
        .locator('input[type="checkbox"]').first();
    await checkbox.waitFor({ state: 'visible', timeout: 10000 });
    await checkbox.check();

    await page.locator('#name').fill(MIETER_NAME);
    await page.locator('#strasse').fill('Zusatzstrasse 1');
    await page.locator('#plz').fill('3000');
    await page.locator('#ort').fill('Bern');
    await page.locator('#mietbeginn').fill('2010-01-01');

    await clearMessages(page);
    await page.locator('button[type="submit"]').click();
    expect(await waitForFormResult(page, 20000)).toBe(true);
}

// ---------------------------------------------------------------------------
// Tarifpositionen
// ---------------------------------------------------------------------------

async function navigateToTarifpositionen(page: Page): Promise<void> {
    await navigateTo(page, '/tarifpositionen');
    await page.locator('#einheitAuswahl').waitFor({ state: 'visible', timeout: 15000 });
}

function nurLadestationenCheckbox(page: Page): Locator {
    return page.locator('#nurLadestationen');
}

/**
 * Wählt eine Einheit und wartet auf die HTTP-Antwort. Auf „Tabelle ODER Leer-Hinweis" zu warten
 * wäre unzuverlässig - der Leer-Hinweis steht schon vor der Antwort auf der Seite.
 */
async function selectEinheit(page: Page, name: string): Promise<void> {
    const option = page.locator('#einheitAuswahl option').filter({ hasText: name }).first();
    const value = await option.getAttribute('value');
    await Promise.all([
        page.waitForResponse(r => r.url().includes('/api/tarifpositionen')
            && r.request().method() === 'GET' && r.status() === 200, { timeout: 20000 }),
        page.locator('#einheitAuswahl').selectOption(value ?? { label: name })
    ]);
}

/** Löscht alle Positionen der gewählten Einheit. */
async function deletePositionen(page: Page, einheitName: string): Promise<void> {
    try {
        page.removeAllListeners('dialog');
        await navigateToTarifpositionen(page);
        await nurLadestationenCheckbox(page).uncheck();
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
        console.log(`Cleanup: Fehler beim Löschen der Positionen von "${einheitName}": ${error}`);
        page.removeAllListeners('dialog');
    }
}

// ---------------------------------------------------------------------------
// Vor- und Nachbereitung
// ---------------------------------------------------------------------------

test.beforeEach(() => {
    createdTarifNames = [];
    createdMieterNames = [];
    createdEinheitNames = [];
    einheitenMitPositionen = [];
});

/**
 * Raeumt die angelegten Datensaetze ab — und **scheitert sichtbar**, wenn das nicht gelingt.
 *
 * <p>Vorher wurde jeder Fehlschlag nur auf die Konsole geschrieben. Genau hier blieben nach einem
 * Lauf ein Mieter und eine Einheit zurueck, ohne dass die Suite es meldete.
 *
 * <p>Reihenfolge zwingend: Positionen sperren Tarif und Mieter, der Mieter sperrt die Einheit.
 */
test.afterEach(async ({ page, browserName }) => {
    if (browserName !== 'chromium') {
        return;
    }
    const gescheitert: string[] = [];

    /** Zweiter Versuch, bevor ein Rueckstand gemeldet wird. */
    async function raeumeAb(was: string, route: string, name: string): Promise<void> {
        let erfolg = await deleteZeileByName(page, route, name);
        if (!erfolg) {
            erfolg = await deleteZeileByName(page, route, name);
        }
        if (!erfolg) {
            gescheitert.push(`${was} ${name}`);
        }
    }

    for (const name of [...new Set(einheitenMitPositionen)]) {
        await deletePositionen(page, name);
    }
    for (const name of createdMieterNames) {
        await raeumeAb('Mieter', '/mieter', name);
    }
    for (const name of createdEinheitNames) {
        await raeumeAb('Einheit', '/einheiten', name);
    }
    for (const name of createdTarifNames) {
        await raeumeAb('Tarif', '/tarife', name);
    }
    einheitenMitPositionen = [];
    createdMieterNames = [];
    createdEinheitNames = [];
    createdTarifNames = [];

    expect(gescheitert,
        `Testdaten blieben in der Datenbank zurueck: ${gescheitert.join(', ')}`).toEqual([]);
});

// ---------------------------------------------------------------------------
// Tarifverwaltung
// ---------------------------------------------------------------------------

test.describe('Tarifpositionen - Tariftyp Zusatzleistung', () => {
    test('should show the unit field only for ZUSATZ and require it', async ({ page }) => {
        await navigateTo(page, '/tarife');
        await waitForTableWithData(page, 10000);
        await page.locator('button.zev-button--primary').first().click();
        await expect(page.locator('form')).toBeVisible();

        const einheitFeld = page.locator('#mengeneinheit');
        const preisLabel = page.locator('label[for="preis"]');

        // Feste Typen: kein Einheiten-Feld, Preis in CHF/kWh bzw. CHF/Monat
        await page.locator('#tariftyp').selectOption('LADESTROM');
        await expect(einheitFeld).toHaveCount(0);
        await expect(preisLabel).toContainText('kWh');

        await page.locator('#tariftyp').selectOption('GRUNDGEBUEHR');
        await expect(preisLabel).toContainText('Monat');
        await expect(preisLabel).not.toContainText('kWh');

        // ZUSATZ: Einheiten-Feld erscheint, Preis-Label folgt der Auswahl
        await page.locator('#tariftyp').selectOption('ZUSATZ');
        await expect(einheitFeld).toBeVisible();
        await expect(preisLabel).not.toContainText('/');

        const bezeichnung = `${SAUNA} Feld`;
        await page.locator('#bezeichnung').fill(bezeichnung);
        await page.locator('#preis').fill('5.00000');
        await page.locator('#gueltigVon').fill(TARIF_VON);
        await page.locator('#gueltigBis').fill(TARIF_BIS);

        // Ohne Mengeneinheit bleibt das Speichern gesperrt
        await expect(page.locator('button[type="submit"]')).toBeDisabled();

        await einheitFeld.selectOption({ index: EINHEIT_INDEX.STUECK });
        // Umlaut bewusst als Muster: ein „ü" aus anderer Quelle kann anders kodiert sein
        await expect(preisLabel).toContainText(/St.ck/);
        await expect(page.locator('button[type="submit"]')).toBeEnabled();

        createdTarifNames.push(bezeichnung);
        await clearMessages(page);
        await page.locator('button[type="submit"]').click();
        expect(await waitForFormResult(page, 20000)).toBe(true);

        // In der Liste steht die Einheit beim Preis
        await waitForTableWithData(page, 10000);
        await expect(page.locator(`tr:has-text("${bezeichnung}")`)).toContainText(/St.ck/);
    });

    test('should allow two overlapping zusatz tariffs', async ({ page }) => {
        // Sauna und Waschküche teilen sich den Typ und müssen gleichzeitig gültig sein - die
        // Überschneidungsprüfung ist für ZUSATZ ausgenommen (Specs/Tarifpositionen.md FR-1.2).
        await navigateTo(page, '/tarife');
        await waitForTableWithData(page, 10000);

        await createZusatzTarif(page, SAUNA, 'STUECK', '5.00000');
        await createZusatzTarif(page, WASCHKUECHE, 'MONAT', '12.00000');

        await expect(page.locator(`tr:has-text("${SAUNA}")`)).toBeVisible();
        await expect(page.locator(`tr:has-text("${WASCHKUECHE}")`)).toBeVisible();
    });
});

// ---------------------------------------------------------------------------
// Erfassungsmaske
// ---------------------------------------------------------------------------

test.describe('Tarifpositionen - Erfassung für Konsumenten', () => {
    test('should offer consumers only when the filter is unchecked', async ({ page }) => {
        await createWohnung(page);
        await navigateToTarifpositionen(page);

        // Normalfall: Checkbox gesetzt, nur Ladestationen
        await expect(nurLadestationenCheckbox(page)).toBeChecked();
        let optionen = await page.locator('#einheitAuswahl option').allTextContents();
        expect(optionen.some(o => o.includes(WOHNUNG_NAME))).toBe(false);

        // Ausnahme: abwählen bringt die Wohnungen dazu
        await nurLadestationenCheckbox(page).uncheck();
        optionen = await page.locator('#einheitAuswahl option').allTextContents();
        expect(optionen.some(o => o.includes(WOHNUNG_NAME))).toBe(true);

        // Wieder anwählen setzt eine Wohnungs-Auswahl zurück. Geprüft am Verhalten statt am
        // Select-Wert: Angular vergibt dort technische Werte, der Erfassen-Button erscheint
        // dagegen nur bei gewählter Einheit.
        await selectEinheit(page, WOHNUNG_NAME);
        await expect(page.locator('button.zev-button--primary').first()).toBeVisible();

        await nurLadestationenCheckbox(page).check();
        await expect(page.locator('button.zev-button--primary')).toHaveCount(0);
    });

    test('should record a zusatz position for a consumer', async ({ page }) => {
        await navigateTo(page, '/tarife');
        await waitForTableWithData(page, 10000);
        await createZusatzTarif(page, SAUNA, 'STUECK', '5.00000');

        await createWohnung(page);
        await createMieter(page);

        await navigateToTarifpositionen(page);
        await nurLadestationenCheckbox(page).uncheck();
        await selectEinheit(page, WOHNUNG_NAME);

        await page.locator('button.zev-button--primary').first().click();
        await expect(page.locator('form')).toBeVisible();

        // An einer Wohnung ist ausschliesslich ZUSATZ zulässig - Ladestrom erscheint nicht
        const tarifOptionen = (await page.locator('#tarifId option').allTextContents())
            .map(o => o.trim()).filter(o => !/wählen|select/i.test(o));
        expect(tarifOptionen).toContain(SAUNA);
        expect(tarifOptionen.some(o => /Ladestrom|Messgebühr/i.test(o))).toBe(false);

        await page.locator('#tarifId').selectOption({ label: SAUNA });
        // Das Mengenfeld nennt die Einheit des Tarifs
        await expect(page.locator('label[for="menge"]')).toContainText(/St.ck/);
        await page.locator('#menge').fill('3');

        einheitenMitPositionen.push(WOHNUNG_NAME);
        await clearMessages(page);
        await page.locator('button[type="submit"]').click();
        expect(await waitForFormResult(page, 20000)).toBe(true);

        // Die Zeile führt Menge samt Einheit und den Betrag (3 * 5.00)
        const zeile = page.locator(`.zev-table tbody tr:has-text("${SAUNA}")`).first();
        await expect(zeile).toBeVisible({ timeout: 10000 });
        await expect(zeile).toContainText(/St.ck/);
        await expect(zeile).toContainText('15.00');
    });
});
