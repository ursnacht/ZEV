import { test, expect, Page } from '@playwright/test';
import { navigateViaMenu } from './helpers';

/**
 * tests / messwerte-grafik.spec.ts
 * E2E-Tests der Messwerte-Grafik (`/chart`, `MesswerteChartComponent`)
 *
 * **Warum diese Datei entsteht:** Die Seite hatte bisher **keinen** E2E-Test, und ihre Unit-Spec
 * lädt mit `of([])` — die Diagramm-Erzeugung wurde also nie ausgeführt. Damit lag die einzige Seite
 * mit chart.js ohne Netz da. Diese Tests sind die Voraussetzung dafür, dort später gefahrlos etwas
 * zu ändern (etwa eine Migration auf ECharts, siehe Diskussion im Umsetzungsplan der
 * Preiszeitreihe).
 *
 * **Kein Aufräumen nötig, und das ist hier keine Nachlässigkeit:** Die Seite ist reine Anzeige. Sie
 * wählt Einheiten und einen Zeitraum und zeichnet die vorhandenen Messwerte — sie schreibt nichts.
 *
 * **Datenabhängig:** Ob im Vorquartal Messwerte liegen, weiss der Test nicht im Voraus. Er liest die
 * Zahl der geladenen Datenpunkte aus der Erfolgsmeldung und überspringt nur den einen Vergleich,
 * der echte Daten braucht — mit Begründung im Report statt stumm grün.
 */

const CANVAS = '.zev-panel--chart canvas';

async function oeffneGrafik(page: Page): Promise<void> {
    await navigateViaMenu(page, '/chart');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
    // Auf die ERSTE echte Einheit warten, nicht auf `#einheit-select-all`: Das Kästchen „Alle
    // auswählen" steht **statisch** im Template und ist auch sichtbar, solange die Einheiten noch
    // nicht geladen sind. Ein `count()` direkt danach liest 0 — dieser Test war deshalb flaky, und
    // dieselbe Falle hat schon eine Messung verdorben.
    await page.locator('.zev-checkbox-item:not(.zev-checkbox-item--select-all) input[type="checkbox"]')
        .first().waitFor({ state: 'attached', timeout: 20000 });
}

function anzeigenButton(page: Page) {
    return page.locator('button[type="submit"]');
}

/** Erste echte Einheit (nicht „Alle auswählen"). */
function ersteEinheit(page: Page) {
    return page.locator('.zev-checkbox-item:not(.zev-checkbox-item--select-all) input[type="checkbox"]')
        .first();
}

/**
 * Sendet das Formular ab und wartet auf die Meldung. Liefert die Zahl der geladenen Datenpunkte
 * aus der Erfolgsmeldung (`"1234 Datenpunkte für 1 Einheiten geladen"`), sonst `null`.
 */
async function zeigeAn(page: Page): Promise<number | null> {
    await anzeigenButton(page).click();
    const meldung = page.locator('.zev-message');
    await expect(meldung).toBeVisible({ timeout: 30000 });
    const text = (await meldung.textContent()) ?? '';
    const treffer = text.match(/(\d+)/);
    return treffer ? Number(treffer[1]) : null;
}

/**
 * Zahl der gezeichneten Pixel des ersten Diagramms.
 *
 * chart.js zeichnet Achsen und Legende auch ohne Datenreihe — ein Wert > 0 belegt also „das Canvas
 * ist da und wird bemalt", nicht mehr. Die Aussagekraft holt der Vergleich weiter unten: Ein
 * Zeitraum **mit** Messwerten muss mehr Pixel ergeben als einer ohne.
 */
async function gezeichnetePixel(page: Page): Promise<number> {
    return page.locator(CANVAS).first().evaluate((canvas) => {
        const c = canvas as HTMLCanvasElement;
        const ctx = c.getContext('2d');
        if (!ctx || c.width === 0 || c.height === 0) {
            return 0;
        }
        const daten = ctx.getImageData(0, 0, c.width, c.height).data;
        let sichtbar = 0;
        for (let i = 3; i < daten.length; i += 4) {
            if (daten[i] > 0) {
                sichtbar++;
            }
        }
        return sichtbar;
    });
}

/**
 * Wartet, bis das Diagramm wirklich gezeichnet ist, und liefert die Pixelzahl.
 *
 * Nötig, weil die Komponente die Diagramme **verzögert** aufbaut
 * (`createChartsSequentially`: `setTimeout(…, 100)`, danach 50 ms je weiteres Diagramm). Das Canvas
 * hängt also schon im DOM, während es noch leer ist — ein direktes Auslesen traf im Vergleichstest
 * genau dieses Fenster und sah zweimal null Pixel.
 */
async function warteAufGezeichnetesDiagramm(page: Page): Promise<number> {
    await expect.poll(() => gezeichnetePixel(page), { timeout: 20000 }).toBeGreaterThan(0);
    return gezeichnetePixel(page);
}

async function setzeZeitraum(page: Page, von: string, bis: string): Promise<void> {
    // dateFrom zuerst: Die Maske zieht dateTo auf das Monatsende nach, das muss danach kommen.
    await page.locator('#dateFrom').fill(von);
    await page.locator('#dateTo').fill(bis);
}

// ---------------------------------------------------------------------------
// Navigation und Vorbelegung
// ---------------------------------------------------------------------------

test.describe('Messwerte-Grafik - Navigation und Vorbelegung', () => {

    test('should open the page from the menu', async ({ page }) => {
        await oeffneGrafik(page);

        await expect(page.locator('.zev-container h1')).toBeVisible();
        await expect(page.locator('#dateFrom')).toBeVisible();
        await expect(page.locator('#dateTo')).toBeVisible();
    });

    test('should default the period to the previous quarter', async ({ page }) => {
        await oeffneGrafik(page);

        const heute = new Date();
        let jahr = heute.getFullYear();
        let quartal = Math.ceil((heute.getMonth() + 1) / 3) - 1;
        if (quartal < 1) {
            quartal = 4;
            jahr--;
        }
        const zwei = (w: number) => String(w).padStart(2, '0');
        const von = `${jahr}-${zwei((quartal - 1) * 3 + 1)}-01`;
        const bis = new Date(jahr, quartal * 3, 0);

        await expect(page.locator('#dateFrom')).toHaveValue(von);
        await expect(page.locator('#dateTo')).toHaveValue(
            `${bis.getFullYear()}-${zwei(bis.getMonth() + 1)}-${zwei(bis.getDate())}`);
    });

    test('should offer the unit selection with a select-all entry', async ({ page }) => {
        await oeffneGrafik(page);

        await expect(page.locator('#einheit-select-all')).toBeVisible();
        await expect(page.locator('.zev-checkbox-item:not(.zev-checkbox-item--select-all)').first())
            .toBeVisible();
    });

    test('should snap the end date to the end of the month when the start date changes', async ({ page }) => {
        await oeffneGrafik(page);

        await page.locator('#dateFrom').fill('2026-02-10');

        // Februar 2026 endet am 28. - die Maske rechnet das Monatsende selbst.
        await expect(page.locator('#dateTo')).toHaveValue('2026-02-28');
    });

    test('should set both dates from the quarter selector', async ({ page }) => {
        await oeffneGrafik(page);

        const quartal = page.locator('.zev-quarter-button').first();
        await quartal.click();

        await expect(page.locator('#dateFrom')).toHaveValue(/^\d{4}-\d{2}-\d{2}$/);
        await expect(page.locator('#dateTo')).toHaveValue(/^\d{4}-\d{2}-\d{2}$/);
        await expect(quartal).toHaveClass(/zev-quarter-button--active/);
    });
});

// ---------------------------------------------------------------------------
// Auswahl und Validierung
// ---------------------------------------------------------------------------

test.describe('Messwerte-Grafik - Auswahl und Validierung', () => {

    test('should keep the submit button disabled without a unit selection', async ({ page }) => {
        await oeffneGrafik(page);

        await expect(anzeigenButton(page)).toBeDisabled();
    });

    test('should enable the submit button once a unit is selected', async ({ page }) => {
        await oeffneGrafik(page);

        await ersteEinheit(page).check();

        await expect(anzeigenButton(page)).toBeEnabled();
    });

    test('should select every unit via the select-all checkbox', async ({ page }) => {
        await oeffneGrafik(page);

        const einzelne = page.locator(
            '.zev-checkbox-item:not(.zev-checkbox-item--select-all) input[type="checkbox"]');
        const anzahl = await einzelne.count();
        expect(anzahl).toBeGreaterThan(0);

        await page.locator('#einheit-select-all').check();

        for (let i = 0; i < anzahl; i++) {
            await expect(einzelne.nth(i)).toBeChecked();
        }
        await expect(anzeigenButton(page)).toBeEnabled();
    });

    test('should reject a reversed period with a readable message', async ({ page }) => {
        await oeffneGrafik(page);
        await ersteEinheit(page).check();

        await setzeZeitraum(page, '2026-03-31', '2026-03-01');
        await anzeigenButton(page).click();

        const fehler = page.locator('.zev-message--error');
        await expect(fehler).toBeVisible({ timeout: 10000 });
        const text = (await fehler.textContent()) ?? '';
        expect(text.trim()).not.toBe('');
        expect(text).not.toContain('[object Object]');
        // Kein Diagramm bei abgewiesener Eingabe.
        await expect(page.locator(CANVAS)).toHaveCount(0);
    });
});

// ---------------------------------------------------------------------------
// Diagramm
// ---------------------------------------------------------------------------

test.describe('Messwerte-Grafik - Diagramm', () => {

    test('should draw a chart for the selected unit', async ({ page }) => {
        await oeffneGrafik(page);
        await ersteEinheit(page).check();

        const datenpunkte = await zeigeAn(page);
        expect(datenpunkte).not.toBeNull();

        // Ein Panel je Einheit, mit Titel und Canvas.
        await expect(page.locator('.zev-panel--chart')).toHaveCount(1);
        await expect(page.locator('.zev-panel--chart .zev-panel__title')).toBeVisible();
        await expect(page.locator(CANVAS)).toBeVisible({ timeout: 20000 });

        // Das Canvas wird tatsaechlich bemalt - nicht bloss eingehaengt und 0x0 gross.
        expect(await warteAufGezeichnetesDiagramm(page)).toBeGreaterThan(0);
    });

    test('should draw one chart per selected unit', async ({ page }) => {
        await oeffneGrafik(page);

        const einzelne = page.locator(
            '.zev-checkbox-item:not(.zev-checkbox-item--select-all) input[type="checkbox"]');
        const verfuegbar = await einzelne.count();
        const gewaehlt = Math.min(verfuegbar, 2);
        test.skip(gewaehlt < 2, 'Weniger als zwei Einheiten vorhanden');

        for (let i = 0; i < gewaehlt; i++) {
            await einzelne.nth(i).check();
        }
        await zeigeAn(page);

        await expect(page.locator('.zev-panel--chart')).toHaveCount(gewaehlt);
        await expect(page.locator(CANVAS)).toHaveCount(gewaehlt);
    });

    test('should report the number of loaded data points', async ({ page }) => {
        await oeffneGrafik(page);
        await ersteEinheit(page).check();

        const datenpunkte = await zeigeAn(page);

        expect(datenpunkte).not.toBeNull();
        expect(datenpunkte).toBeGreaterThanOrEqual(0);
        await expect(page.locator('.zev-message--success')).toBeVisible();
    });

    test('should stay usable for a period without measurements', async ({ page }) => {
        await oeffneGrafik(page);
        await ersteEinheit(page).check();
        await setzeZeitraum(page, '2001-01-01', '2001-01-31');

        const datenpunkte = await zeigeAn(page);

        expect(datenpunkte).toBe(0);
        // Die Maske bleibt bedienbar: Panel und Canvas entstehen, nur ohne Datenreihe.
        await expect(page.locator(CANVAS)).toBeVisible({ timeout: 20000 });
        await expect(anzeigenButton(page)).toBeEnabled();
    });

    /**
     * Zoomen: Mausrad über dem Diagramm ändert das Bild — **ohne** neuen Server-Aufruf.
     *
     * Der Zoom kam mit dem Wechsel auf ECharts (`Specs/EChart.md`) und ist die einzige neue
     * Funktion dieses Umbaus. Geprüft wird über die bemalten Pixel: Ein anderer Ausschnitt ergibt
     * ein anderes Bild. Ein Zoom, der zufällig **genau** dieselbe Pixelzahl bemalt, ist bei
     * fünfstelligen Zahlen praktisch ausgeschlossen.
     */
    test('should zoom with the mouse wheel without asking the server', async ({ page }) => {
        await oeffneGrafik(page);
        await ersteEinheit(page).check();

        const datenpunkte = await zeigeAn(page);
        test.skip(!datenpunkte, 'Im Vorquartal liegen keine Messwerte - Zoom nicht pruefbar');
        const vorher = await warteAufGezeichnetesDiagramm(page);

        let aufrufe = 0;
        page.on('request', (r) => {
            if (r.url().includes('/api/messwerte')) {
                aufrufe++;
            }
        });

        // Das Diagramm liegt unterhalb des Formulars und kann teilweise ausserhalb des
        // Sichtfensters sein - ein Mausrad-Ereignis an einer nicht sichtbaren Stelle erreicht
        // ECharts nicht.
        const canvas = page.locator(CANVAS).first();
        await canvas.scrollIntoViewIfNeeded();
        const flaeche = await canvas.boundingBox();
        await page.mouse.move(flaeche!.x + flaeche!.width / 2, flaeche!.y + flaeche!.height / 2);
        for (let i = 0; i < 5; i++) {
            await page.mouse.wheel(0, -300);
            await page.waitForTimeout(120);
        }

        await expect.poll(() => gezeichnetePixel(page), { timeout: 10000 })
            .not.toBe(vorher);
        expect(aufrufe).toBe(0);
        page.removeAllListeners('request');
    });

    /**
     * Der eigentliche Wächter: Ein Zeitraum **mit** Messwerten muss mehr Pixel bemalen als einer
     * ohne. chart.js zeichnet Achsen und Legende in beiden Fällen — nur die **Datenreihe**
     * unterscheidet sie. Genau das würde brechen, wenn die Serie eines Tages nicht mehr gezeichnet
     * wird (etwa nach einem Wechsel der Diagramm-Bibliothek).
     */
    test('should paint more when the period contains measurements', async ({ page }) => {
        await oeffneGrafik(page);
        await ersteEinheit(page).check();

        // Leerer Zeitraum als Referenz.
        await setzeZeitraum(page, '2001-01-01', '2001-01-31');
        await zeigeAn(page);
        await expect(page.locator(CANVAS)).toBeVisible({ timeout: 20000 });
        const ohneDaten = await warteAufGezeichnetesDiagramm(page);

        // Zurueck auf das Vorquartal (Vorbelegung der Maske).
        await page.reload({ waitUntil: 'domcontentloaded' });
        await page.locator('#einheit-select-all').waitFor({ state: 'visible', timeout: 15000 });
        await ersteEinheit(page).check();
        const datenpunkte = await zeigeAn(page);
        test.skip(!datenpunkte,
            'Im Vorquartal liegen fuer die erste Einheit keine Messwerte - Vergleich nicht moeglich');

        await expect(page.locator(CANVAS)).toBeVisible({ timeout: 20000 });
        const mitDaten = await warteAufGezeichnetesDiagramm(page);

        expect(mitDaten).toBeGreaterThan(ohneDaten);
    });
});
