import { test, expect, Page } from '@playwright/test';
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

/** Öffnet die Maske für eine neue Abrechnung. Erwartet die geöffnete Liste. */
async function oeffneNeueAbrechnung(page: Page): Promise<void> {
    await page.locator('.zev-button-row .zev-button--primary').first().click();
    await expect(page.locator('#bezeichnung')).toBeVisible({ timeout: 10000 });
}

/** Füllt die Angaben zur Abrechnung. */
async function fuelleKopf(page: Page, bezeichnung: string): Promise<void> {
    await page.locator('#bezeichnung').fill(bezeichnung);
    await page.locator('#datumVon').fill(DATUM_VON);
    await page.locator('#datumBis').fill(DATUM_BIS);
    await page.locator('#anzahlWohnungen').fill(ANZAHL_WOHNUNGEN);
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
    expect(await waitForFormResult(page)).toBe(true);
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

/** Löscht eine Abrechnung über das Kebab-Menü. */
async function loescheAbrechnung(page: Page, bezeichnung: string): Promise<void> {
    try {
        page.removeAllListeners('dialog');
        await navigateToListe(page);
        const zeile = page.locator(`tr:has-text("${bezeichnung}")`);
        if (!await zeile.isVisible().catch(() => false)) {
            return;
        }
        page.on('dialog', async dialog => { await dialog.accept(); });
        await clickKebabMenuItem(page, zeile, 'delete');
        await expect(zeile).not.toBeVisible({ timeout: 10000 });
    } catch (error) {
        console.log(`Cleanup: "${bezeichnung}" konnte nicht geloescht werden: ${error}`);
    } finally {
        page.removeAllListeners('dialog');
    }
}

test.beforeEach(() => {
    angelegteBezeichnungen = [];
});

test.afterEach(async ({ page }) => {
    for (const bezeichnung of angelegteBezeichnungen) {
        await loescheAbrechnung(page, bezeichnung);
    }
    angelegteBezeichnungen = [];
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
        await page.locator('#bezeichnung').fill(neueBezeichnung('Zeitraum'));
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
        expect(await waitForFormResult(page)).toBe(true);

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
        expect(await waitForFormResult(page)).toBe(true);

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
        expect(await waitForFormResult(page)).toBe(true);

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
        expect(await waitForFormResult(page)).toBe(true);
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
        expect(await waitForFormResult(page)).toBe(true);
        await clearMessages(page);

        // Das Oeffnen einer abgeschlossenen Abrechnung fragt zurueck.
        let dialogErschien = false;
        page.on('dialog', async dialog => { dialogErschien = true; await dialog.accept(); });
        await page.locator(`tr:has-text("${bezeichnung}")`).locator('input[type="checkbox"]')
            .uncheck();
        expect(await waitForFormResult(page)).toBe(true);
        expect(dialogErschien).toBe(true);
        page.removeAllListeners('dialog');
    });
});
