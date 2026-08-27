import { test, expect, Locator, Page } from '@playwright/test';
import { clickKebabMenuItem, navigateViaMenu, waitForFormResult } from './helpers';

/**
 * tests / nebenkosten-abrechnung.spec.ts
 * E2E-Tests der Nebenkostenabrechnung (Specs/Nebenkosten/Abrechnung.md):
 *   - Liste, Anlegen, Löschen, Flag „abgerechnet" samt Rückfrage-Regel
 *   - Feldfehler erst nach dem ersten Speicherversuch
 *   - Positionsarten UMLAGE und ANTEIL bis zum Betrag im Mieterblock
 *   - **Neuladen der Route** — der Fall, an dem die Anwendung mit einer Whitelabel-Fehlerseite
 *     antwortete, weil `SpaRedirectController` nur einstufige Pfade kannte
 *
 * Serial, weil die Abrechnungsliste mandantenweit ist: Ein Test, der die Liste durchsucht, würde
 * sonst die Zeilen eines parallel laufenden sehen. Die Bezeichnungen tragen zusätzlich Laufzeit
 * und Browser, damit die beiden Browser-Projekte sich nicht in die Quere kommen.
 */
test.describe.configure({ mode: 'serial', timeout: 180000 });

const ROUTE = '/nebenkosten/abrechnung';

/**
 * Zeitraum weit in der Zukunft. Die Mietverhältnisse laufen ohne Mietende weiter und berühren
 * damit jeden künftigen Zeitraum — die Mieterblöcke sind also besetzt, ohne echte Daten zu
 * berühren.
 */
const DATUM_VON = '2087-01-01';
const DATUM_BIS = '2087-12-31';

/**
 * Bewusst weit über der tatsächlichen Belegung: Der Nenner muss mindestens so gross sein wie die
 * Summe der Miettage, sonst weist der Server das Speichern ab (FR-2). Mit einem festen, hohen Wert
 * ist der Test unabhängig davon, wie viele Mieter die Umgebung kennt.
 */
const ANZAHL_WOHNUNGEN = '99';

let angelegteBezeichnungen: string[] = [];

// ---------------------------------------------------------------------------
// Hilfsfunktionen
// ---------------------------------------------------------------------------

/** Eindeutige Bezeichnung: Laufzeit gegen frühere Läufe, Browser gegen das Parallelprojekt. */
function neueBezeichnung(suffix: string): string {
    return `E2E NK ${test.info().project.name} ${Date.now().toString().slice(-6)} ${suffix}`;
}

async function navigateToListe(page: Page): Promise<void> {
    await navigateViaMenu(page, ROUTE);
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

/**
 * Öffnet die Maske für eine neue Abrechnung. Erwartet die geöffnete Liste.
 *
 * <p>Wartet auf die **Antwort der Vorlage**, nicht nur auf das sichtbare Feld: Die Maske ist
 * bedienbar, während die Vorlage unterwegs ist. Wer davor tippt, arbeitet auf einem Stand, den die
 * Antwort noch anfassen kann — zwei Fälle dieser Suite scheiterten genau daran (die Bezeichnung war
 * zwischen Tippen und Klick wieder weg). Die Anwendung überschreibt die Eingabe inzwischen nicht
 * mehr; hier wird zusätzlich abgewartet, damit der Test unabhängig von der Antwortzeit ist.
 */
async function oeffneNeueAbrechnung(page: Page): Promise<void> {
    const vorlage = page.waitForResponse(
        res => res.url().includes('/api/nebenkosten/abrechnungen/vorlage')
            && res.request().method() === 'GET',
        { timeout: 20000 });
    await page.locator('.zev-button-row .zev-button--primary').first().click();
    await expect(page.locator('#bezeichnung')).toBeVisible({ timeout: 10000 });
    await vorlage;
}

/** Füllt die Angaben zur Abrechnung. */
async function fuelleKopf(page: Page, bezeichnung: string): Promise<void> {
    await page.locator('#bezeichnung').fill(bezeichnung);
    await page.locator('#datumVon').fill(DATUM_VON);
    await page.locator('#datumBis').fill(DATUM_BIS);
    await page.locator('#anzahlWohnungen').fill(ANZAHL_WOHNUNGEN);
}

/**
 * Wartet auf das Ergebnis eines Speichervorgangs und nennt im Fehlerfall die **Meldung**.
 *
 * `waitForFormResult` liefert `false`, wenn eine FEHLERmeldung sichtbar ist — nicht, wenn keine
 * Meldung kommt (dann wirft es selbst). Ohne diesen Helfer stand im Report nur
 * `Expected: true / Received: false`; der Meldungstext steht nicht im Snapshot von Playwright und
 * war nach einem gelungenen Retry gar nicht mehr rekonstruierbar. Genau daran hing ein Flaky-Fall
 * dieser Suite.
 */
async function erwarteErfolg(page: Page, was: string): Promise<void> {
    if (await waitForFormResult(page, 20000)) {
        return;
    }
    const meldung = await page.locator('.zev-message--error').first().textContent()
        .catch(() => '');
    throw new Error(`${was}: Speichern abgewiesen — "${meldung?.trim()}"`);
}

/** Die Speichern-Schaltfläche am Ende der Maske. */
function speichernUnten(page: Page) {
    return page.locator('.zev-form-actions .zev-button--primary');
}

/** Legt eine Abrechnung an und lässt die Maske offen. */
async function erstelleAbrechnung(page: Page, bezeichnung: string): Promise<void> {
    angelegteBezeichnungen.push(bezeichnung);
    await oeffneNeueAbrechnung(page);
    await fuelleKopf(page, bezeichnung);
    await clearMessages(page);
    await speichernUnten(page).click();
    await erwarteErfolg(page, 'Abrechnung anlegen');
}

/**
 * Position der Art im Auswahlfeld — die Reihenfolge stammt aus `NK_POSITIONSARTEN`.
 *
 * Bewusst über den **Index**: Das Select bindet mit `[ngValue]`, Angular vergibt dort technische
 * Werte (`0: UMLAGE`). Ein `selectOption('UMLAGE')` liefe in ein „did not find some options",
 * dessen Meldung nur das Symptom nennt.
 */
const ART_INDEX = { UMLAGE: 0, VERBRAUCH: 1, ANTEIL: 2, ZUSCHLAG: 3 } as const;

/** Fügt eine Position hinzu und wählt ihre Art. */
async function fuegePositionHinzu(page: Page, art: keyof typeof ART_INDEX,
                                  bezeichnung: string): Promise<void> {
    const zeilenVorher = await page.locator('.nk-positionen tbody tr').count();
    // „Position hinzufuegen" ist die erste sekundaere Schaltflaeche im Positionen-Panel.
    await page.locator('.zev-panel .zev-button--secondary').first().click();
    const zeilen = page.locator('.nk-positionen tbody tr');
    await expect(zeilen).toHaveCount(zeilenVorher + 1, { timeout: 10000 });

    const neueZeile = zeilen.nth(zeilenVorher);
    await neueZeile.locator('select').first().selectOption({ index: ART_INDEX[art] });
    await neueZeile.locator('input[type="text"]').fill(bezeichnung);
}

/**
 * Öffnet den ersten Mieterblock und gibt dessen Inhalt zurück.
 *
 * Idempotent: Der Aufklappzustand überlebt das Speichern (er ist Zustand der Maske, nicht der
 * Daten). Ein blindes Klicken würde einen bereits offenen Block wieder schliessen — und der
 * Test scheiterte an einem Inhalt, den er selbst zugeklappt hat.
 */
async function oeffneErstenMieterblock(page: Page) {
    const kopf = page.locator('.zev-collapsible__header').first();
    await expect(kopf).toBeVisible({ timeout: 10000 });

    const inhalt = page.locator('.zev-collapsible__content').first();
    if (!await inhalt.isVisible().catch(() => false)) {
        await kopf.click();
    }
    await expect(inhalt).toBeVisible({ timeout: 10000 });
    return inhalt;
}

/**
 * Führt die Liste frisch auf und wartet, bis sie geladen ist.
 *
 * Bewusst `goto` statt Menü-Klick: Endet ein Test in der offenen Maske, führt ein Klick auf den
 * Menüeintrag der Route, auf der man schon steht, zu keiner Neu-Montage — die Maske bliebe offen
 * und die Liste unsichtbar. Und es wird auf die **Zeilen** gewartet, nicht bloss auf die
 * Überschrift: Die Liste lädt serverseitig nach.
 */
async function oeffneListeFrisch(page: Page): Promise<void> {
    await page.goto(ROUTE, { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.zev-container h1')).toBeVisible({ timeout: 20000 });
    await expect(page.locator('table.zev-table, .zev-empty-state').first())
        .toBeVisible({ timeout: 20000 });
}

/**
 * Löscht eine Abrechnung über das Kebab-Menü. Liefert `true`, wenn danach keine Zeile mehr steht.
 *
 * <p>Hier lag der Grund für die Rückstände in der Datenbank: Die Existenzprüfung lief über
 * `zeile.isVisible()` — und das fragt **ohne zu warten**. Die Zeile erscheint aber erst mit der
 * Antwort der Listen-Abfrage, weshalb die Prüfung „nicht vorhanden" meldete und die Funktion
 * stillschweigend zurückkehrte. Jeder anlegende Test hinterliess so seine Abrechnung, ohne dass
 * es auffiel. Jetzt wird über `toHaveCount` gewartet.
 */
async function loescheAbrechnung(page: Page, bezeichnung: string): Promise<boolean> {
    page.removeAllListeners('dialog');
    try {
        await oeffneListeFrisch(page);
        const zeile = page.locator(`tr:has-text("${bezeichnung}")`);

        // Wartende Prüfung: Steht die Zeile nach dem Zeitfenster nicht da, ist sie wirklich weg.
        const vorhanden = await zeile.first().waitFor({ state: 'visible', timeout: 10000 })
            .then(() => true).catch(() => false);
        if (!vorhanden) {
            return true;
        }

        page.on('dialog', async dialog => { await dialog.accept(); });
        await clickKebabMenuItem(page, zeile.first(), 'delete');
        await expect(zeile).toHaveCount(0, { timeout: 10000 });
        return true;
    } catch (error) {
        console.error(`CLEANUP FEHLGESCHLAGEN: "${bezeichnung}" - ${error}`);
        return false;
    } finally {
        page.removeAllListeners('dialog');
    }
}

/**
 * Klickt einen Kebab-Eintrag über seinen **Text**.
 *
 * `clickKebabMenuItem` aus den Helpers kennt nur „erster" und „gefährlicher" Eintrag; für
 * „Rechnungen erstellen" in der Mitte wäre das ein Index — und der verschiebt sich, sobald ein
 * Eintrag dazukommt.
 */
async function klickeKebabEintrag(page: Page, zeile: Locator, text: string): Promise<void> {
    await zeile.locator('.zev-kebab-button').click();
    await zeile.locator('.zev-kebab-menu--open').waitFor({ state: 'visible', timeout: 5000 });
    await zeile.locator('.zev-kebab-menu__item', { hasText: text }).click();
}

/** Setzt das Flag „abgerechnet" der Zeile und wartet auf die Bestätigung. */
async function schliesseAbrechnungAb(page: Page, bezeichnung: string): Promise<void> {
    const zeile = page.locator(`tr:has-text("${bezeichnung}")`);
    await expect(zeile).toBeVisible({ timeout: 10000 });
    await clearMessages(page);
    // Abschliessen fragt bewusst NICHT nach - es ist jederzeit umkehrbar.
    await zeile.locator('input[type="checkbox"]').check();
    await erwarteErfolg(page, 'Abrechnung abschliessen');
    await clearMessages(page);
}

/**
 * Löst den Rechnungslauf über das Kebab-Menü aus und wartet auf das Ergebnis-Panel.
 *
 * Die Rückfrage wird angenommen: Der Lauf bucht Forderungen, deshalb fragt er nach.
 */
async function erstelleRechnungen(page: Page, bezeichnung: string): Promise<void> {
    await oeffneListeFrisch(page);
    const zeile = page.locator(`tr:has-text("${bezeichnung}")`);
    await expect(zeile).toBeVisible({ timeout: 10000 });

    page.once('dialog', async dialog => { await dialog.accept(); });
    const antwort = page.waitForResponse(
        res => res.url().includes('/rechnungen') && res.request().method() === 'POST',
        { timeout: 60000 });
    await klickeKebabEintrag(page, zeile, 'Rechnungen erstellen');
    const response = await antwort;
    expect(response.status()).toBe(200);

    await expect(page.locator('.zev-panel')).toBeVisible({ timeout: 20000 });
}

/** Zeitraum der Debitorenkontrolle setzen und auf die Antwort zu genau diesem Zeitraum warten. */
async function setzeDebitorZeitraum(page: Page, von: string, bis: string): Promise<void> {
    const antwort = page.waitForResponse(
        res => res.url().includes('/api/debitoren')
            && res.request().method() === 'GET'
            && res.url().includes(`von=${von}`)
            && res.url().includes(`bis=${bis}`),
        { timeout: 20000 });
    await page.locator('#dateFrom').fill(von);
    await page.locator('#dateTo').fill(bis);
    await antwort;
}

/** Öffnet die Debitorenkontrolle im Zeitraum der Testabrechnung. */
async function oeffneDebitorenImTestzeitraum(page: Page): Promise<void> {
    await page.goto('/debitoren', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.zev-container h1')).toBeVisible({ timeout: 20000 });
    await setzeDebitorZeitraum(page, DATUM_VON, DATUM_BIS);
}

/**
 * Räumt die vom Rechnungslauf gebuchten Forderungen ab.
 *
 * Im Zeitraum 2087 liegen ausschliesslich Testdaten — es werden deshalb **alle** Zeilen des
 * Zeitraums gelöscht, ohne sie einzeln zuzuordnen. Liefert `true`, wenn danach keine mehr steht.
 */
async function loescheTestDebitoren(page: Page): Promise<boolean> {
    page.removeAllListeners('dialog');
    try {
        await oeffneDebitorenImTestzeitraum(page);
        const zeilen = page.locator('.zev-table tbody tr');

        // Nicht ueber eine Zaehlung vorab: Jedes Loeschen laedt die Liste neu, und eine
        // eingefrorene Anzahl wuerde nach dem ersten Durchgang auf veraltete Zeilen zeigen.
        for (let versuch = 0; versuch < 50; versuch++) {
            const anzahl = await zeilen.count();
            if (anzahl === 0) {
                return true;
            }
            page.once('dialog', async dialog => { await dialog.accept(); });
            const erste = zeilen.first();
            await erste.locator('.zev-kebab-button').click();
            await erste.locator('.zev-kebab-menu--open').waitFor({ state: 'visible', timeout: 5000 });
            await erste.locator('.zev-kebab-menu__item--danger').last().click();
            await expect(zeilen).toHaveCount(anzahl - 1, { timeout: 15000 });
        }
        return await zeilen.count() === 0;
    } catch (error) {
        console.error(`CLEANUP FEHLGESCHLAGEN: Debitoren im Testzeitraum - ${error}`);
        return false;
    } finally {
        page.removeAllListeners('dialog');
    }
}

/** Wird gesetzt, sobald ein Test Forderungen gebucht hat — dann räumt `afterEach` sie ab. */
let debitorenGebucht = false;

// ---------------------------------------------------------------------------
// Feature-Flag: Vorbedingung dieser Suite
// ---------------------------------------------------------------------------

const NK_FLAG_CHECKBOX = '#flag-NEBENKOSTENABRECHNUNG';
const NK_FLAG_ENDPOINT = '/api/feature-flags/NEBENKOSTENABRECHNUNG';
const EFFECTIVE_FLAGS_GET = '/api/feature-flags';

/** Zustand des Flags vor dieser Suite — wird am Ende wiederhergestellt. */
let nkFlagVorher: boolean | null = null;

/**
 * Schaltet das Flag `NEBENKOSTENABRECHNUNG` über die Einstellungen und wartet auf das `PUT`
 * **und** das anschliessende Nachladen der effektiven Flags — erst danach kennt die reaktive
 * Navigation den neuen Stand. Liefert den Zustand **vor** dem Aufruf.
 */
async function setzeNkFlag(page: Page, aktiv: boolean): Promise<boolean> {
    await navigateViaMenu(page, '/einstellungen');
    const checkbox = page.locator(NK_FLAG_CHECKBOX);
    await checkbox.waitFor({ state: 'visible', timeout: 15000 });

    const vorher = await checkbox.isChecked();
    if (vorher === aktiv) {
        return vorher;
    }

    const put = page.waitForResponse(
        r => r.url().includes(NK_FLAG_ENDPOINT) && r.request().method() === 'PUT',
        { timeout: 15000 });
    const nachladen = page.waitForResponse(
        r => r.url().endsWith(EFFECTIVE_FLAGS_GET) && r.request().method() === 'GET',
        { timeout: 15000 });
    await checkbox.click();
    await put;
    await nachladen.catch(() => console.log('setzeNkFlag: kein Nachladen der Flags erkannt'));
    await waitForFormResult(page);
    return vorher;
}

/**
 * Der Bereich Nebenkosten liegt hinter einem Flag, dessen **Default `false`** ist
 * (`FeatureFlag.NEBENKOSTENABRECHNUNG`): Ohne ausdrücklichen Mandanten-Schalter erscheint der
 * Menüeintrag nicht, und jeder Test dieser Datei scheitert schon an der Navigation.
 *
 * <p>Diese Suite setzte den Schalter bisher **stillschweigend voraus**. Sie war damit von einem
 * Zustand abhängig, den sie nicht selbst herstellt — und lief rot, sobald jemand das Flag
 * abschaltete. Jetzt stellt sie ihre Vorbedingung selbst her und gibt den vorherigen Zustand am
 * Ende zurück.
 */
test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    try {
        nkFlagVorher = await setzeNkFlag(page, true);
    } finally {
        await page.close();
    }
});

test.afterAll(async ({ browser }) => {
    if (nkFlagVorher === null || nkFlagVorher === true) {
        return;
    }
    const page = await browser.newPage();
    try {
        await setzeNkFlag(page, false);
    } catch (error) {
        console.error(`Feature-Flag konnte nicht zurueckgesetzt werden: ${error}`);
    } finally {
        await page.close();
    }
});

test.beforeEach(() => {
    angelegteBezeichnungen = [];
    debitorenGebucht = false;
});

/**
 * Räumt die angelegten Abrechnungen ab — und **scheitert sichtbar**, wenn das nicht gelingt.
 *
 * <p>Vorher wurde jeder Fehlschlag nur auf die Konsole geschrieben; die Suite blieb grün, während
 * die Datenbank volllief. Ein Rückstand ist ein Befund und gehört gemeldet. Ein zweiter Versuch
 * davor, weil ein einzelnes Löschen an einer stehenden Meldung scheitern kann.
 */
test.afterEach(async ({ page }) => {
    const gescheitert: string[] = [];

    // Zuerst die Forderungen: Sie haengen am Mieter und nicht an der Abrechnung, ein Loeschen der
    // Abrechnung nimmt sie also NICHT mit (bewusst so - eine Kaskade waere stiller Datenverlust).
    if (debitorenGebucht && !await loescheTestDebitoren(page)) {
        gescheitert.push('Debitoren im Testzeitraum');
    }
    debitorenGebucht = false;

    for (const bezeichnung of angelegteBezeichnungen) {
        let erfolg = await loescheAbrechnung(page, bezeichnung);
        if (!erfolg) {
            erfolg = await loescheAbrechnung(page, bezeichnung);
        }
        if (!erfolg) {
            gescheitert.push(bezeichnung);
        }
    }
    angelegteBezeichnungen = [];

    expect(gescheitert,
        `Testdaten blieben in der Datenbank zurueck: ${gescheitert.join(', ')}`).toEqual([]);
});

// ---------------------------------------------------------------------------
// Liste und Navigation
// ---------------------------------------------------------------------------

test.describe('Nebenkostenabrechnung - Liste', () => {

    test('should open the page via the menu', async ({ page }) => {
        await navigateToListe(page);

        await expect(page.locator('.zev-container h1')).toBeVisible();
        // Die Schaltflaeche steht oberhalb der Tabelle - wie auf den uebrigen Listenseiten.
        await expect(page.locator('.zev-button-row .zev-button--primary').first()).toBeVisible();
    });

    /**
     * Regressionstest zum Whitelabel-Fehler: `SpaRedirectController` leitete nur einstufige Pfade
     * auf die index.html um, `/nebenkosten/abrechnung` lief deshalb in die Fehlerseite von Spring
     * Boot. Betrifft jedes Neuladen, jedes Lesezeichen und jeden geteilten Link.
     */
    test('should survive a reload of the nested route', async ({ page }) => {
        await navigateToListe(page);

        await page.reload({ waitUntil: 'domcontentloaded' });

        await expect(page.locator('.zev-container h1')).toBeVisible({ timeout: 20000 });
        await expect(page.locator('body')).not.toContainText('Whitelabel');
        expect(new URL(page.url()).pathname).toBe(ROUTE);
    });

    test('should create a billing and show it in the list', async ({ page }) => {
        const bezeichnung = neueBezeichnung('Anlegen');
        await navigateToListe(page);

        await erstelleAbrechnung(page, bezeichnung);
        await page.locator('.zev-form-actions .zev-button--secondary').last().click();

        await expect(page.locator(`tr:has-text("${bezeichnung}")`)).toBeVisible({ timeout: 10000 });
    });

    test('should delete a billing via the kebab menu', async ({ page }) => {
        const bezeichnung = neueBezeichnung('Loeschen');
        await navigateToListe(page);
        await erstelleAbrechnung(page, bezeichnung);
        await page.locator('.zev-form-actions .zev-button--secondary').last().click();

        const zeile = page.locator(`tr:has-text("${bezeichnung}")`);
        await expect(zeile).toBeVisible({ timeout: 10000 });

        page.on('dialog', async dialog => { await dialog.accept(); });
        await clickKebabMenuItem(page, zeile, 'delete');

        await expect(zeile).not.toBeVisible({ timeout: 10000 });
        page.removeAllListeners('dialog');
        // Bereits geloescht - der Cleanup muss nichts mehr tun.
        angelegteBezeichnungen = [];
    });
});

// ---------------------------------------------------------------------------
// Maske: Validierung
// ---------------------------------------------------------------------------

test.describe('Nebenkostenabrechnung - Maske', () => {

    test('should not show field errors before the first save attempt', async ({ page }) => {
        // Eine frisch geoeffnete Maske ist zwangslaeufig unvollstaendig; rote Meldungen tadelten
        // den Benutzer fuer etwas, das er noch gar nicht getan hat.
        await navigateToListe(page);
        await oeffneNeueAbrechnung(page);

        await expect(page.locator('.zev-form-error')).toHaveCount(0);
    });

    test('should show field errors after saving incomplete input', async ({ page }) => {
        await navigateToListe(page);
        await oeffneNeueAbrechnung(page);
        await page.locator('#bezeichnung').fill('');

        await speichernUnten(page).click();

        await expect(page.locator('.zev-form-error').first()).toBeVisible({ timeout: 10000 });
        await expect(page.locator('.zev-message--error')).toBeVisible();
    });

    test('should reject a period that ends before it starts', async ({ page }) => {
        await navigateToListe(page);
        await oeffneNeueAbrechnung(page);
        // Registriert, obwohl der Server ablehnen MUSS: Sollte die Ablehnung je ausfallen, wird
        // die Abrechnung aufgeraeumt statt unbemerkt liegen zu bleiben.
        const bezeichnung = neueBezeichnung('Zeitraum');
        angelegteBezeichnungen.push(bezeichnung);
        await page.locator('#bezeichnung').fill(bezeichnung);
        await page.locator('#datumVon').fill(DATUM_BIS);
        await page.locator('#datumBis').fill(DATUM_VON);
        await page.locator('#anzahlWohnungen').fill(ANZAHL_WOHNUNGEN);

        await speichernUnten(page).click();

        await expect(page.locator('.zev-message--error')).toBeVisible({ timeout: 10000 });
    });

    test('should keep tenant blocks collapsed after loading', async ({ page }) => {
        const bezeichnung = neueBezeichnung('Zugeklappt');
        await navigateToListe(page);
        await erstelleAbrechnung(page, bezeichnung);

        // Nach dem Speichern erscheinen die Bloecke - alle geschlossen.
        await expect(page.locator('.zev-collapsible__header').first())
            .toBeVisible({ timeout: 15000 });
        await expect(page.locator('.zev-collapsible__content')).toHaveCount(0);
    });
});

// ---------------------------------------------------------------------------
// Positionen und Betraege
// ---------------------------------------------------------------------------

test.describe('Nebenkostenabrechnung - Positionen', () => {

    test('should distribute an allocation and report the undistributed share',
        async ({ page }) => {
        const bezeichnung = neueBezeichnung('Umlage');
        await navigateToListe(page);
        await erstelleAbrechnung(page, bezeichnung);

        await fuegePositionHinzu(page, 'UMLAGE', 'E2E Allgemeinstrom');
        const zeile = page.locator('.nk-positionen tbody tr').first();
        await zeile.locator('input[type="number"]').first().fill('900');

        await clearMessages(page);
        await speichernUnten(page).click();
        await erwarteErfolg(page, 'Umlage-Position speichern');

        // Kontrollzahlen: Der Nenner ist absichtlich zu gross, es bleibt also etwas unverteilt.
        const kontrolle = page.locator('.nk-kontrolle tbody tr').first();
        await expect(kontrolle).toContainText('900.00');
        await expect(kontrolle).toBeVisible();

        const block = await oeffneErstenMieterblock(page);
        await expect(block.locator('.nk-mieterzeilen tbody tr').first())
            .toContainText('E2E Allgemeinstrom');
    });

    test('should distribute a share position by the percentage per tenant', async ({ page }) => {
        // ANTEIL ist unabhaengig von den Miettagen: 1000.00 x 50% = 500.00, unabhaengig davon,
        // wie viele Mieter die Umgebung kennt. Damit ist der Betrag exakt pruefbar.
        const bezeichnung = neueBezeichnung('Anteil');
        await navigateToListe(page);
        await erstelleAbrechnung(page, bezeichnung);

        await fuegePositionHinzu(page, 'ANTEIL', 'E2E Heizkosten');
        const zeile = page.locator('.nk-positionen tbody tr').first();
        await zeile.locator('input[type="number"]').first().fill('1000');

        const block = await oeffneErstenMieterblock(page);
        const mengenfeld = block.locator('.nk-mieterzeilen input[type="number"]').first();
        await expect(mengenfeld).toBeVisible({ timeout: 10000 });
        await mengenfeld.fill('50');

        // Sofortberechnung: der Betrag steht ohne Speichern in der Zeile.
        await expect(block.locator('.nk-mieterzeilen tbody tr').first())
            .toContainText('500.00', { timeout: 10000 });

        // Kontrollzahl: Die Summe der Anteile ergibt 50% und wird als Abweichung markiert.
        await expect(page.locator('.nk-kontrolle tbody tr').first()).toContainText('50');

        await clearMessages(page);
        await speichernUnten(page).click();
        await erwarteErfolg(page, 'Anteil-Position speichern');

        // Nach dem Speichern zeigt die Maske die Werte des Servers - derselbe Betrag.
        const blockNachher = await oeffneErstenMieterblock(page);
        await expect(blockNachher.locator('.nk-mieterzeilen tbody tr').first())
            .toContainText('500.00', { timeout: 10000 });
    });

    test('should offer a quantity field for a consumption position right away', async ({ page }) => {
        // Regressionstest: Die Mengenfelder waren gesperrt, weil die Herkunftspruefung einer
        // Zeile auf `undefined` verglich - das Backend schickt aber `null`.
        const bezeichnung = neueBezeichnung('Verbrauch');
        await navigateToListe(page);
        await erstelleAbrechnung(page, bezeichnung);

        await fuegePositionHinzu(page, 'VERBRAUCH', 'E2E Warmwasser');
        const zeile = page.locator('.nk-positionen tbody tr').first();
        await zeile.locator('input[type="number"]').first().fill('2');

        await clearMessages(page);
        await speichernUnten(page).click();
        await erwarteErfolg(page, 'Verbrauch-Position speichern');

        // Frisch geladener Zustand: Das Feld muss ohne weitere Eingabe bedienbar sein. Erreicht
        // ueber Verlassen und Wiederoeffnen - die Maske wird neu aufgebaut und laedt die Daten
        // serverfoermig nach. Bewusst KEIN page.reload() davor: Firefox bricht die dann noch
        // laufende Navigation mit NS_BINDING_ABORTED ab. Das Neuladen selbst hat einen eigenen
        // Test (siehe "should survive a reload of the nested route").
        await navigateToListe(page);
        const listenZeile = page.locator(`tr:has-text("${bezeichnung}")`);
        await clickKebabMenuItem(page, listenZeile, 'edit');
        await expect(page.locator('#bezeichnung')).toBeVisible({ timeout: 15000 });

        const block = await oeffneErstenMieterblock(page);
        const mengenfeld = block.locator('.nk-mieterzeilen input[type="number"]').first();
        await expect(mengenfeld).toBeVisible({ timeout: 10000 });
        await expect(mengenfeld).toBeEnabled();

        await mengenfeld.fill('10');
        await expect(block.locator('.nk-mieterzeilen tbody tr').first())
            .toContainText('20.00', { timeout: 10000 });
    });
});

// ---------------------------------------------------------------------------
// Flag „abgerechnet"
// ---------------------------------------------------------------------------

test.describe('Nebenkostenabrechnung - abgerechnet', () => {

    test('should close a billing without asking back and lock the form', async ({ page }) => {
        const bezeichnung = neueBezeichnung('Abschluss');
        await navigateToListe(page);
        await erstelleAbrechnung(page, bezeichnung);
        await page.locator('.zev-form-actions .zev-button--secondary').last().click();

        const zeile = page.locator(`tr:has-text("${bezeichnung}")`);
        await expect(zeile).toBeVisible({ timeout: 10000 });
        await clearMessages(page);

        // Abschliessen ist jederzeit umkehrbar und fragt deshalb NICHT nach.
        let dialogErschien = false;
        page.on('dialog', async dialog => { dialogErschien = true; await dialog.accept(); });
        await zeile.locator('input[type="checkbox"]').check();
        await erwarteErfolg(page, 'Abrechnung abschliessen');
        expect(dialogErschien).toBe(false);
        page.removeAllListeners('dialog');

        // In der Maske sind die Felder nun gesperrt.
        await clickKebabMenuItem(page, page.locator(`tr:has-text("${bezeichnung}")`), 'edit');
        await expect(page.locator('#bezeichnung')).toBeDisabled({ timeout: 10000 });
        await expect(page.locator('.zev-message--info')).toBeVisible();

        // Fuer den Cleanup wieder freigeben.
        await page.locator('.zev-form-actions .zev-button--secondary').last().click();
        page.on('dialog', async dialog => { await dialog.accept(); });
        await page.locator(`tr:has-text("${bezeichnung}")`).locator('input[type="checkbox"]')
            .uncheck();
        await expect(page.locator('.zev-message--success')).toBeVisible({ timeout: 10000 });
        page.removeAllListeners('dialog');
    });

    test('should ask back before reopening a closed billing', async ({ page }) => {
        const bezeichnung = neueBezeichnung('Freigabe');
        await navigateToListe(page);
        await erstelleAbrechnung(page, bezeichnung);
        await page.locator('.zev-form-actions .zev-button--secondary').last().click();

        const zeile = page.locator(`tr:has-text("${bezeichnung}")`);
        await expect(zeile).toBeVisible({ timeout: 10000 });
        await clearMessages(page);

        await zeile.locator('input[type="checkbox"]').check();
        await erwarteErfolg(page, 'Abrechnung abschliessen');
        await clearMessages(page);

        // Das Oeffnen einer abgeschlossenen Abrechnung fragt zurueck.
        let dialogErschien = false;
        page.on('dialog', async dialog => { dialogErschien = true; await dialog.accept(); });
        await page.locator(`tr:has-text("${bezeichnung}")`).locator('input[type="checkbox"]')
            .uncheck();
        await erwarteErfolg(page, 'Abrechnung wieder oeffnen');
        expect(dialogErschien).toBe(true);
        page.removeAllListeners('dialog');
    });
});

// ---------------------------------------------------------------------------
// Rechnungen aus der Abrechnung (Specs/Nebenkosten/RechnungenGenerieren.md)
// ---------------------------------------------------------------------------

test.describe('Nebenkostenabrechnung - Rechnungen', () => {

    /**
     * Der Menüeintrag erscheint **nur** auf einer abgeschlossenen Abrechnung — bewusst als
     * fehlender Eintrag und nicht als gesperrter: Ein ausgegrauter müsste erklären, warum er
     * ausgegraut ist (FR-2).
     */
    test('should offer the invoice run only on a closed billing', async ({ page }) => {
        const bezeichnung = neueBezeichnung('Menue');
        await navigateToListe(page);
        await erstelleAbrechnung(page, bezeichnung);
        await page.locator('.zev-form-actions .zev-button--secondary').last().click();

        const zeile = page.locator(`tr:has-text("${bezeichnung}")`);
        await expect(zeile).toBeVisible({ timeout: 10000 });

        // Offen: kein Eintrag.
        await zeile.locator('.zev-kebab-button').click();
        await expect(zeile.locator('.zev-kebab-menu--open')).toBeVisible({ timeout: 5000 });
        await expect(zeile.locator('.zev-kebab-menu__item', { hasText: 'Rechnungen erstellen' }))
            .toHaveCount(0);
        await page.keyboard.press('Escape');

        await schliesseAbrechnungAb(page, bezeichnung);

        // Abgeschlossen: Eintrag vorhanden, und der gefaehrliche bleibt unten.
        const zeileZu = page.locator(`tr:has-text("${bezeichnung}")`);
        await zeileZu.locator('.zev-kebab-button').click();
        await expect(zeileZu.locator('.zev-kebab-menu--open')).toBeVisible({ timeout: 5000 });
        await expect(zeileZu.locator('.zev-kebab-menu__item', { hasText: 'Rechnungen erstellen' }))
            .toBeVisible();
        const eintraege = await zeileZu.locator('.zev-kebab-menu__item').allInnerTexts();
        expect(eintraege.length).toBe(3);
        expect(eintraege[2]).toContain('Löschen');
        await page.keyboard.press('Escape');
    });

    /** Die Rückfrage schützt vor einem versehentlichen Klick — es entstehen Forderungen. */
    test('should not create anything when the confirmation is declined', async ({ page }) => {
        const bezeichnung = neueBezeichnung('Abbruch');
        await navigateToListe(page);
        await erstelleAbrechnung(page, bezeichnung);
        await page.locator('.zev-form-actions .zev-button--secondary').last().click();
        await schliesseAbrechnungAb(page, bezeichnung);

        const zeile = page.locator(`tr:has-text("${bezeichnung}")`);
        let dialogErschien = false;
        page.once('dialog', async dialog => { dialogErschien = true; await dialog.dismiss(); });
        await klickeKebabEintrag(page, zeile, 'Rechnungen erstellen');
        await page.waitForTimeout(1000);

        expect(dialogErschien).toBe(true);
        // Kein Ergebnis-Panel, also auch kein Lauf.
        await expect(page.locator('.zev-panel')).toHaveCount(0);
    });

    /**
     * Der vollständige Weg: abschliessen, Rechnungen erstellen, Ergebnis prüfen, PDF holen und
     * die Forderung in der Debitorenkontrolle mit Herkunft **Nebenkosten** wiederfinden.
     *
     * Die Position ist eine ANTEIL-Zeile mit 50 % auf einen hohen Totalbetrag: Der Betrag hängt
     * damit **nicht** an der Zahl der Mieter, die die Umgebung kennt, und liegt sicher über einem
     * etwaigen Akonto — es entsteht also verlässlich eine Nachzahlung.
     */
    test('should create invoices, offer the pdf and book the receivable',
        async ({ page }) => {
        const bezeichnung = neueBezeichnung('Lauf');
        await navigateToListe(page);
        await erstelleAbrechnung(page, bezeichnung);

        await fuegePositionHinzu(page, 'ANTEIL', 'E2E Heizkosten');
        await page.locator('.nk-positionen tbody tr').first()
            .locator('input[type="number"]').first().fill('100000');

        const block = await oeffneErstenMieterblock(page);
        const prozentfeld = block.locator('.nk-mieterzeilen input[type="number"]').first();
        await expect(prozentfeld).toBeVisible({ timeout: 10000 });
        await prozentfeld.fill('50');

        await clearMessages(page);
        await speichernUnten(page).click();
        await erwarteErfolg(page, 'Anteil je Mieter speichern');
        await page.locator('.zev-form-actions .zev-button--secondary').last().click();

        await schliesseAbrechnungAb(page, bezeichnung);

        debitorenGebucht = true;
        await erstelleRechnungen(page, bezeichnung);

        // --- Ergebnis-Panel ---
        const panel = page.locator('.zev-panel');
        await expect(panel).toContainText('Erstellte Rechnungen');
        await expect(panel).toContainText(bezeichnung);

        const zeilen = panel.locator('tbody tr');
        await expect(zeilen.first()).toBeVisible({ timeout: 10000 });
        // Mindestens eine gebuchte Forderung - die Zeile mit 50 % traegt eine Nachzahlung.
        await expect(panel.locator('.zev-status--success').first()).toBeVisible();
        // Betraege im Schweizer Format: Punkt als Dezimal-, Hochkomma als Tausendertrenner.
        await expect(panel).toContainText(/\d{1,3}(?:'\d{3})*\.\d{2}/);

        // --- PDF ---
        // Der Download wird ueber die Antwort geprueft und nicht ueber den Browser-Download:
        // Das belegt Route, Berechtigung, Feature-Flag und Ablage in einem Zug, ohne von der
        // Download-Mechanik des Browsers abzuhaengen.
        const pdfAntwort = page.waitForResponse(
            res => res.url().includes('/pdf') && res.request().method() === 'GET',
            { timeout: 30000 });
        // Der Klick loest zusaetzlich einen echten Browser-Download aus. Er muss abgewartet
        // werden, bevor navigiert wird: In Firefox scheiterte das anschliessende `page.goto`
        // sonst mit „Download is starting" — die Navigation fiel in den Beginn des Downloads.
        const download = page.waitForEvent('download', { timeout: 30000 }).catch(() => null);
        await zeilen.first().locator('.zev-button--secondary').click();
        const pdf = await pdfAntwort;
        expect(pdf.status()).toBe(200);
        expect(pdf.headers()['content-type']).toContain('application/pdf');
        // Der Dateiname ist lesbar und nicht der Ablageschluessel aus zwei IDs.
        expect(pdf.headers()['content-disposition']).toContain('Nebenkosten');

        // Download abgeschlossen (oder es gab keinen) — erst danach ist Navigieren sicher.
        const abgelegt = await download;
        if (abgelegt) {
            await abgelegt.path();
        }

        // --- Forderung in der Debitorenkontrolle ---
        await oeffneDebitorenImTestzeitraum(page);
        const debitorZeilen = page.locator('.zev-table tbody tr');
        await expect(debitorZeilen.first()).toBeVisible({ timeout: 15000 });
        // Im Testzeitraum liegen ausschliesslich die Forderungen dieses Laufs - jede muss die
        // Herkunft Nebenkosten tragen.
        const anzahl = await debitorZeilen.count();
        for (let i = 0; i < anzahl; i++) {
            await expect(debitorZeilen.nth(i)).toContainText('Nebenkosten');
        }
    });

    /**
     * Ein zweiter Lauf ist erlaubt und **erzeugt keine zweite Forderung**: Das Upsert läuft je
     * Herkunft idempotent. Ohne die Herkunft im Unique-Key hätte er die ZEV-Forderung desselben
     * Mieters mit demselben `datum_von` überschrieben.
     */
    test('should not create a second receivable on a repeated run', async ({ page }) => {
        const bezeichnung = neueBezeichnung('Wiederholt');
        await navigateToListe(page);
        await erstelleAbrechnung(page, bezeichnung);

        await fuegePositionHinzu(page, 'ANTEIL', 'E2E Heizkosten');
        await page.locator('.nk-positionen tbody tr').first()
            .locator('input[type="number"]').first().fill('100000');
        const block = await oeffneErstenMieterblock(page);
        await block.locator('.nk-mieterzeilen input[type="number"]').first().fill('50');
        await clearMessages(page);
        await speichernUnten(page).click();
        await erwarteErfolg(page, 'Anteil je Mieter speichern');
        await page.locator('.zev-form-actions .zev-button--secondary').last().click();

        await schliesseAbrechnungAb(page, bezeichnung);

        debitorenGebucht = true;
        await erstelleRechnungen(page, bezeichnung);
        await oeffneDebitorenImTestzeitraum(page);
        const nachErstem = await page.locator('.zev-table tbody tr').count();
        expect(nachErstem).toBeGreaterThan(0);

        await erstelleRechnungen(page, bezeichnung);
        await oeffneDebitorenImTestzeitraum(page);

        await expect(page.locator('.zev-table tbody tr')).toHaveCount(nachErstem);
    });

    /**
     * Das Ergebnis gehört zu dem Stand, den die Tabelle beim Lauf zeigte — nach einem Neuladen
     * wäre es ein Ergebnis zu Zeilen, die es so nicht mehr geben muss.
     */
    test('should drop the result panel when the list is reloaded', async ({ page }) => {
        const bezeichnung = neueBezeichnung('Panel');
        await navigateToListe(page);
        await erstelleAbrechnung(page, bezeichnung);
        await page.locator('.zev-form-actions .zev-button--secondary').last().click();
        await schliesseAbrechnungAb(page, bezeichnung);

        // Ohne Positionen ist der Saldo <= 0: PDF ja, Forderung nein (FR-4). Der Test braucht
        // deshalb kein Abraeumen in der Debitorenkontrolle.
        await erstelleRechnungen(page, bezeichnung);
        const panel = page.locator('.zev-panel');
        await expect(panel).toBeVisible();
        await expect(panel).toContainText('keine Forderung');

        await oeffneListeFrisch(page);

        await expect(page.locator('.zev-panel')).toHaveCount(0);
    });
});
