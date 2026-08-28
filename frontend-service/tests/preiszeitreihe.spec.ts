import { test, expect, Page } from '@playwright/test';
import { navigateViaMenu, waitForFormResult } from './helpers';

/**
 * tests / preiszeitreihe.spec.ts
 * E2E-Tests der Preiszeitreihe (Specs/Preiszeitreihe.md)
 *
 * **Nur Chromium und `serial`.** Diese Suite schaltet das mandantenweite Feature-Flag
 * `PREISZEITREIHE` ein und am Ende wieder aus. Liefen beide Browser-Projekte parallel, sähen beide
 * `beforeAll` das Flag noch auf `false` — und wer zuerst fertig ist, schaltet es dem anderen mitten
 * im Lauf ab. Dieselbe Grenze ziehen `feature-flag-upload.spec.ts` und
 * `nebenkosten-abrechnung.spec.ts`.
 *
 * **Kein Aufräumen von Testdaten, und das ist Absicht:** Die Reihe ist mandantenübergreifende
 * Marktdaten, kein Testbestand. Der Download schreibt per Upsert echte Preise — genau das, was der
 * geplante Job täglich tut. Zurückgestellt wird nur das Feature-Flag.
 *
 * **Was hier hingehört und nirgends sonst:** dass **beide** Darstellungen tatsächlich etwas
 * zeichnen. ECharts meldet einen nicht registrierten Serientyp nicht als Fehler — `type: 'bar'`
 * ohne `charts.BarChart` liess die Fläche stumm leer. Ein Unit-Test kann das nicht sehen (jsdom hat
 * kein Canvas), deshalb zählt der Test unten die gezeichneten Pixel.
 */

test.describe.configure({ mode: 'serial', timeout: 120000 });

test.beforeEach(({ browserName }) => {
    test.skip(browserName !== 'chromium',
        'PREISZEITREIHE ist ein mandantenweites Flag; nur in einem Browser testen, um '
        + 'Zustands-Races zu vermeiden.');
});

const FLAG_CHECKBOX = '#flag-PREISZEITREIHE';
const FLAG_ENDPOINT = '/api/feature-flags/PREISZEITREIHE';
const EFFECTIVE_FLAGS_GET = '/api/feature-flags';
const PANEL = '.preiszeitreihe';
const DIAGRAMM = '.preiszeitreihe__diagramm';

/** Zustand des Flags vor dieser Suite — wird am Ende wiederhergestellt. */
let flagVorher: boolean | null = null;

/**
 * Schaltet das Flag über die Einstellungen und wartet auf das `PUT` **und** das Nachladen der
 * effektiven Flags — erst danach kennt die Anwendung den neuen Stand. Liefert den Zustand **vor**
 * dem Aufruf.
 */
async function setzeFlag(page: Page, aktiv: boolean): Promise<boolean> {
    await navigateViaMenu(page, '/einstellungen');
    const checkbox = page.locator(FLAG_CHECKBOX);
    await checkbox.waitFor({ state: 'visible', timeout: 15000 });

    const vorher = await checkbox.isChecked();
    if (vorher === aktiv) {
        return vorher;
    }

    const put = page.waitForResponse(
        r => r.url().includes(FLAG_ENDPOINT) && r.request().method() === 'PUT',
        { timeout: 15000 });
    const nachladen = page.waitForResponse(
        r => r.url().endsWith(EFFECTIVE_FLAGS_GET) && r.request().method() === 'GET',
        { timeout: 15000 });
    await checkbox.click();
    await put;
    await nachladen.catch(() => console.log('setzeFlag: kein Nachladen der Flags erkannt'));
    await waitForFormResult(page);
    return vorher;
}

/** Öffnet die Tarifseite und wartet auf den Bereich Einspeisepreise. */
async function oeffneTarife(page: Page): Promise<void> {
    await navigateViaMenu(page, '/tarife');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
    await expect(page.locator(PANEL)).toBeVisible({ timeout: 15000 });
}

/** Wartet auf die Antwort zu **genau dieser** Spanne — nicht auf irgendeinen GET. */
function antwortFuer(page: Page, von: string, bis: string) {
    return page.waitForResponse(
        r => r.url().includes('/api/preiszeitreihe')
            && r.request().method() === 'GET'
            && r.url().includes(`von=${von}`)
            && r.url().includes(`bis=${bis}`),
        { timeout: 20000 });
}

function spannenButton(page: Page, text: string) {
    return page.locator('.zev-toggle-button').filter({ hasText: text }).first();
}

/**
 * Zahl der gezeichneten Pixel im Diagramm.
 *
 * Der einzige Weg, „das Diagramm zeichnet etwas" ohne Bildvergleich zu prüfen: Ein leeres Canvas
 * ist vollständig durchsichtig. Genau hier wäre der nicht registrierte Balken-Serientyp aufgefallen.
 */
async function gezeichnetePixel(page: Page): Promise<number> {
    return page.locator(`${DIAGRAMM} canvas`).first().evaluate((canvas) => {
        const c = canvas as HTMLCanvasElement;
        const ctx = c.getContext('2d');
        if (!ctx || c.width === 0 || c.height === 0) {
            return 0;
        }
        const daten = ctx.getImageData(0, 0, c.width, c.height).data;
        let sichtbar = 0;
        // Jeder vierte Wert ist der Alphakanal.
        for (let i = 3; i < daten.length; i += 4) {
            if (daten[i] > 0) {
                sichtbar++;
            }
        }
        return sichtbar;
    });
}

/**
 * Holt die Preise bei der Quelle. Liefert `true` bei Erfolg.
 *
 * Der Abruf geht an die **echte** BKW-API. Ist sie nicht erreichbar, ist das kein Fehler dieser
 * Anwendung: Der Test, der Daten braucht, überspringt sich dann mit Begründung, statt stumm grün zu
 * werden oder rot zu leuchten, wo nichts kaputt ist.
 */
async function holePreise(page: Page): Promise<boolean> {
    const antwort = page.waitForResponse(
        r => r.url().includes('/api/preiszeitreihe/download') && r.request().method() === 'POST',
        { timeout: 40000 });
    await page.locator(`${PANEL} .zev-button--primary`).click();
    const status = (await antwort).status();
    if (status !== 200) {
        console.log(`Preiszeitreihe: Quelle antwortete mit ${status} - Datentests werden übersprungen`);
        return false;
    }
    await expect(page.locator('.zev-message--success')).toBeVisible({ timeout: 15000 });
    return true;
}

test.beforeAll(async ({ browser }, testInfo) => {
    // `test.skip` im `beforeEach` haelt die Worker-Hooks nicht auf.
    if (testInfo.project.name !== 'chromium') {
        return;
    }
    const page = await browser.newPage();
    try {
        flagVorher = await setzeFlag(page, true);
    } finally {
        await page.close();
    }
});

test.afterAll(async ({ browser }, testInfo) => {
    if (testInfo.project.name !== 'chromium' || flagVorher === null || flagVorher === true) {
        return;
    }
    const page = await browser.newPage();
    try {
        await setzeFlag(page, false);
    } catch (error) {
        console.error(`Feature-Flag konnte nicht zurueckgesetzt werden: ${error}`);
    } finally {
        await page.close();
    }
});

// ---------------------------------------------------------------------------
// Sichtbarkeit und Aufbau
// ---------------------------------------------------------------------------

test.describe('Preiszeitreihe - Aufbau', () => {

    test('should show the section below the tariff list', async ({ page }) => {
        await oeffneTarife(page);

        await expect(page.locator(`${PANEL}__titel`)).toBeVisible();
        await expect(page.locator(DIAGRAMM)).toBeVisible();
    });

    test('should offer all controls in one row at the same height', async ({ page }) => {
        await oeffneTarife(page);

        const zeile = page.locator(`${PANEL} .zev-date-range-row`);
        await expect(zeile).toBeVisible();

        const tag = await spannenButton(page, 'Tag').boundingBox();
        const feld = await page.locator('#preisVon').boundingBox();
        const knopf = await page.locator(`${PANEL} .zev-button--primary`).boundingBox();
        expect(tag).not.toBeNull();
        expect(feld).not.toBeNull();
        expect(knopf).not.toBeNull();

        // Gleiche Hoehe (38px aus dem Design System) und dieselbe Grundlinie: Genau das war vorher
        // nicht der Fall, weil die Regel fuer .zev-toggle-button fehlte.
        expect(Math.round(tag!.height)).toBe(Math.round(feld!.height));
        expect(Math.round(knopf!.height)).toBe(Math.round(feld!.height));
        expect(Math.abs((tag!.y + tag!.height) - (feld!.y + feld!.height))).toBeLessThan(2);
    });

    test('should place the view toggle between "Datum bis" and "Herunterladen"', async ({ page }) => {
        await oeffneTarife(page);

        const bis = await page.locator('#preisBis').boundingBox();
        const linie = await spannenButton(page, 'Linie').boundingBox();
        const knopf = await page.locator(`${PANEL} .zev-button--primary`).boundingBox();

        expect(linie!.x).toBeGreaterThan(bis!.x);
        expect(knopf!.x).toBeGreaterThan(linie!.x);
    });

    test('should start on today with the day view and the line view active', async ({ page }) => {
        await oeffneTarife(page);

        const heute = new Date();
        const iso = `${heute.getFullYear()}-${String(heute.getMonth() + 1).padStart(2, '0')}`
            + `-${String(heute.getDate()).padStart(2, '0')}`;

        await expect(page.locator('#preisVon')).toHaveValue(iso);
        await expect(page.locator('#preisBis')).toHaveValue(iso);
        await expect(spannenButton(page, 'Tag')).toHaveClass(/zev-toggle-button--active/);
        await expect(spannenButton(page, 'Linie')).toHaveClass(/zev-toggle-button--active/);
    });
});

// ---------------------------------------------------------------------------
// Auswahl der Spanne
// ---------------------------------------------------------------------------

test.describe('Preiszeitreihe - Spanne', () => {

    test('should load a whole month and mark the selection', async ({ page }) => {
        await oeffneTarife(page);

        const antwort = page.waitForResponse(
            r => r.url().includes('/api/preiszeitreihe') && r.request().method() === 'GET',
            { timeout: 20000 });
        await spannenButton(page, 'Monat').click();
        await antwort;

        await expect(spannenButton(page, 'Monat')).toHaveClass(/zev-toggle-button--active/);
        await expect(spannenButton(page, 'Tag')).not.toHaveClass(/zev-toggle-button--active/);
        await expect(page.locator('#preisVon')).toHaveValue(/^\d{4}-\d{2}-01$/);
    });

    test('should page one day back and reload for that day', async ({ page }) => {
        await oeffneTarife(page);
        const vorher = await page.locator('#preisVon').inputValue();

        const gestern = new Date(vorher);
        gestern.setDate(gestern.getDate() - 1);
        const erwartet = `${gestern.getFullYear()}-${String(gestern.getMonth() + 1).padStart(2, '0')}`
            + `-${String(gestern.getDate()).padStart(2, '0')}`;

        const antwort = antwortFuer(page, erwartet, erwartet);
        await page.locator(`${PANEL} .zev-toggle-button`).nth(3).click(); // '<'
        await antwort;

        await expect(page.locator('#preisVon')).toHaveValue(erwartet);
        await expect(page.locator('#preisBis')).toHaveValue(erwartet);
    });

    test('should drop the span selection when a date is entered', async ({ page }) => {
        await oeffneTarife(page);

        const antwort = antwortFuer(page, '2026-01-01', '2026-01-31');
        await page.locator('#preisVon').fill('2026-01-01');
        await page.locator('#preisBis').fill('2026-01-31');
        await antwort;

        await expect(spannenButton(page, 'Tag')).not.toHaveClass(/zev-toggle-button--active/);
        await expect(spannenButton(page, 'Woche')).not.toHaveClass(/zev-toggle-button--active/);
        await expect(spannenButton(page, 'Monat')).not.toHaveClass(/zev-toggle-button--active/);
    });

    test('should reject a reversed period without asking the server', async ({ page }) => {
        await oeffneTarife(page);

        // Erst 'von' setzen. Das ist noch eine GUELTIGE Spanne (von liegt vor dem heutigen 'bis')
        // und laedt deshalb zu Recht - gezaehlt wird darum erst danach. Ein Zaehler ab dem ersten
        // Feld haette hier eine Verletzung gemeldet, wo keine ist.
        await page.locator('#preisVon').fill('2026-03-31');
        await page.waitForTimeout(1500);

        let aufrufe = 0;
        page.on('request', (r) => {
            if (r.url().includes('/api/preiszeitreihe') && r.method() === 'GET') {
                aufrufe++;
            }
        });

        // Jetzt wird 'bis' vor 'von' gelegt - das muss die Maske selbst abweisen.
        await page.locator('#preisBis').fill('2026-03-01');
        await expect(page.locator('.zev-message--error')).toBeVisible({ timeout: 10000 });

        // Die Meldung kommt aus der Maske, nicht vom Server - und sie ist lesbar, kein
        // "[object Object]".
        const text = await page.locator('.zev-message--error').textContent();
        expect(text).not.toContain('[object Object]');
        expect(aufrufe).toBe(0);
        page.removeAllListeners('request');
    });
});

// ---------------------------------------------------------------------------
// Abruf und Darstellung
// ---------------------------------------------------------------------------

test.describe('Preiszeitreihe - Abruf und Darstellung', () => {

    test('should download the prices and report the counts', async ({ page }) => {
        await oeffneTarife(page);

        const antwort = page.waitForResponse(
            r => r.url().includes('/api/preiszeitreihe/download') && r.request().method() === 'POST',
            { timeout: 40000 });
        await page.locator(`${PANEL} .zev-button--primary`).click();
        const status = (await antwort).status();

        // Erfolg ODER lesbarer Fehler - aber niemals eine unbrauchbare Meldung: Der Abruf haengt an
        // einer fremden API, die Robustheit der Maske haengt daran nicht.
        const meldung = page.locator('.zev-message');
        await expect(meldung).toBeVisible({ timeout: 20000 });
        const text = (await meldung.textContent()) ?? '';
        expect(text.trim()).not.toBe('');
        expect(text).not.toContain('[object Object]');
        expect([200, 400, 502]).toContain(status);
    });

    /**
     * Der Kern dieser Suite: **beide** Darstellungen zeichnen wirklich etwas.
     *
     * ECharts ignoriert einen nicht registrierten Serientyp stillschweigend — die Balken blieben
     * zuerst unsichtbar, ohne Fehler in der Konsole. Gezählt werden deshalb die nicht
     * durchsichtigen Pixel des Canvas.
     */
    test('should draw both the line and the bar view', async ({ page }) => {
        await oeffneTarife(page);

        const geladen = await holePreise(page);
        test.skip(!geladen, 'Quelle der Einspeisepreise nicht erreichbar - kein Datenbestand');

        await expect(page.locator(`${DIAGRAMM} canvas`)).toBeVisible({ timeout: 20000 });
        const alsLinie = await gezeichnetePixel(page);
        expect(alsLinie).toBeGreaterThan(0);

        await spannenButton(page, 'Balken').click();
        await expect(spannenButton(page, 'Balken')).toHaveClass(/zev-toggle-button--active/);
        // Kurz warten, bis ECharts neu gezeichnet hat - das laeuft ohne HTTP-Aufruf.
        await page.waitForTimeout(500);
        const alsBalken = await gezeichnetePixel(page);
        expect(alsBalken).toBeGreaterThan(0);

        await spannenButton(page, 'Linie').click();
        await page.waitForTimeout(500);
        expect(await gezeichnetePixel(page)).toBeGreaterThan(0);
    });

    test('should switch the view without asking the server', async ({ page }) => {
        await oeffneTarife(page);

        let aufrufe = 0;
        page.on('request', (r) => {
            if (r.url().includes('/api/preiszeitreihe')) {
                aufrufe++;
            }
        });

        await spannenButton(page, 'Balken').click();
        await expect(spannenButton(page, 'Balken')).toHaveClass(/zev-toggle-button--active/);
        await page.waitForTimeout(500);

        expect(aufrufe).toBe(0);
        page.removeAllListeners('request');
    });

    test('should show a hint instead of an empty chart', async ({ page }) => {
        await oeffneTarife(page);

        // Ein Zeitraum, in dem es keine Preise geben kann.
        const antwort = antwortFuer(page, '2001-01-01', '2001-01-31');
        await page.locator('#preisVon').fill('2001-01-01');
        await page.locator('#preisBis').fill('2001-01-31');
        await antwort;

        await expect(page.locator(`${PANEL}__hinweis`)).toBeVisible({ timeout: 10000 });
    });
});

// ---------------------------------------------------------------------------
// Feature-Flag
// ---------------------------------------------------------------------------

test.describe('Preiszeitreihe - Feature-Flag', () => {

    /**
     * Läuft zuletzt und stellt den Flag selbst wieder her: Die übrigen Tests dieser Datei brauchen
     * ihn eingeschaltet, und `serial` garantiert die Reihenfolge.
     */
    test('should hide the section when the flag is off', async ({ page }) => {
        await setzeFlag(page, false);
        try {
            await navigateViaMenu(page, '/tarife');
            await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });

            await expect(page.locator('.zev-table')).toBeVisible({ timeout: 15000 });
            await expect(page.locator(PANEL)).toHaveCount(0);
        } finally {
            await setzeFlag(page, true);
        }
    });
});
