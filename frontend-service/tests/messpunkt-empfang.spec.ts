import { test, expect, Page, Route } from '@playwright/test';
import {
    navigateViaMenu,
    navigateToEinheiten,
    openKebabMenu,
    loescheZeileMitText,
    raeumeMitWiederholung,
    waitForFormResult,
    waitForTableWithData,
} from './helpers';

/**
 * tests / messpunkt-empfang.spec.ts
 * E2E tests for the Messpunkt-Empfang view (Specs/Messpunkt-Empfang.md)
 *
 * <p>Nur die kritischen Flows: Navigation, Kein Empfang einer echten Einheit, Einheit anlegen und
 * Letzte Meldung anzeigen. Alles Übrige (Badges, Sortierung, Formatierung, Löschen) decken die
 * Unit- und Integrationstests ab.
 *
 * <p><b>Warum `page.route` für Zeilen mit Empfang:</b> Empfangszeilen entstehen nur im MQTT-Ingest.
 * Der Mandant des Testusers hat keine, und eine Meldung an den Broker oder ein Insert in die
 * Datenbank wäre eine Änderung an der Umgebung, die kein Test aufräumen kann (die Zeile entstünde
 * bei jeder weiteren Meldung neu). Die Zeilen mit Empfang und ihre letzte Meldung werden deshalb
 * nur im Browser vorgegeben. Die Zeile "Kein Empfang" dagegen kommt aus echten Daten.
 */

/** Mehr Zeit als die Voreinstellung: Das Aufräumen im `afterEach` zählt zum Test-Timeout. */
test.describe.configure({ timeout: 120000 });

/** Laufkennung; mit dem Projektkürzel eindeutig je Browser-Projekt und Lauf. */
const RUN_ID = Date.now().toString().slice(-6);

/** Angelegte Einheiten fürs Aufräumen. */
let createdEinheitNames: string[] = [];

function kennung(): string {
    return `${test.info().project.name.slice(0, 2)}${RUN_ID}`;
}

/** Öffnet die Ansicht über Admin › Messpunkt-Empfang und wartet auf den Titel. */
async function navigateToMesspunktEmpfang(page: Page): Promise<void> {
    await navigateViaMenu(page, '/messpunkt-empfang');
    await page.locator('.zev-container h1').waitFor({ state: 'visible', timeout: 15000 });
}

/** Zeile der Liste mit vollen Empfangsfeldern; `ueberschreibung` setzt einzelne Felder. */
function empfangsZeile(ueberschreibung: Record<string, unknown>): Record<string, unknown> {
    return {
        id: 1,
        messpunkt: 'MP',
        einheiten: [],
        quelle: 'PI_GATEWAY',
        ersterEmpfang: '2026-10-20T08:00:00',
        letzterEmpfang: '2026-10-25T10:15:30',
        sekundenSeitEmpfang: 60,
        anzahlMeldungen: 42,
        zaehlerstandBezug: 1234.5,
        zaehlerstandEinspeisung: 678.25,
        ergebnis: 'UNBEKANNTER_MESSPUNKT',
        grund: null,
        keinEmpfang: false,
        ...ueberschreibung
    };
}

/**
 * Gibt die Liste `GET /api/messpunkt-empfang` im Browser vor (siehe Dateikommentar). Nur genau
 * dieser Pfad - `…/{id}/letzte-meldung` und alle übrigen Aufrufe gehen weiter ans Backend bzw.
 * an ihre eigene Route.
 */
async function gibListeVor(page: Page, zeilen: Record<string, unknown>[]): Promise<void> {
    await page.route(url => url.pathname === '/api/messpunkt-empfang', async (route: Route) => {
        if (route.request().method() !== 'GET') {
            await route.fallback();
            return;
        }
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(zeilen) });
    });
}

/** Gibt `GET /api/messpunkt-empfang/{id}/letzte-meldung` im Browser vor. */
async function gibMeldungVor(page: Page, id: number, payload: string | null): Promise<void> {
    await page.route(url => url.pathname === `/api/messpunkt-empfang/${id}/letzte-meldung`,
        async (route: Route) => {
            await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ payload }) });
        });
}

/**
 * Wartet, bis keine Meldung mehr steht - vor jedem Absenden, sonst gälte die 5 s stehende
 * Erfolgsmeldung der vorherigen Aktion als Ergebnis der nächsten.
 */
async function clearMessages(page: Page): Promise<void> {
    const error = page.locator('.zev-message--error');
    if (await error.isVisible().catch(() => false)) {
        await error.click();
        await expect(error).not.toBeVisible({ timeout: 5000 });
    }
    await expect(page.locator('.zev-message--success')).not.toBeVisible({ timeout: 10000 });
}

/** Legt in der Einheitenverwaltung eine Einheit an und registriert sie fürs Aufräumen. */
async function createEinheitOrFail(page: Page, name: string, messpunkt: string): Promise<void> {
    await navigateToEinheiten(page);
    // Liegengebliebenen Datensatz eines abgebrochenen Laufs vorher räumen: Der Name wäre sonst
    // doppelt, und die Zeilensuche fände zwei
    expect(await loescheZeileMitText(page, name, 3000),
        `Rest "${name}" eines früheren Laufs liess sich nicht löschen`).toBe(true);

    await page.locator('button.zev-button--primary').first().click();
    await expect(page.locator('form')).toBeVisible();
    await page.locator('#name').fill(name);
    await expect(page.locator('#name')).toHaveValue(name);
    await page.locator('#typ').selectOption('CONSUMER');
    await page.locator('#messpunkt').fill(messpunkt);
    await expect(page.locator('#messpunkt')).toHaveValue(messpunkt);

    createdEinheitNames.push(name);
    await clearMessages(page);
    await page.locator('button[type="submit"]').click();
    if (!await waitForFormResult(page, 20000)) {
        const meldung = await page.locator('.zev-message--error').first().textContent().catch(() => '');
        throw new Error(`Einheit "${name}" konnte nicht gespeichert werden: ${meldung?.trim()}`);
    }
    await expect(page.locator(`tr:has-text("${name}")`).first()).toBeVisible({ timeout: 10000 });
}

test.beforeEach(() => {
    createdEinheitNames = [];
});

/** Räumt angelegte Einheiten ab und scheitert sichtbar, wenn das nicht gelingt. */
test.afterEach(async ({ page }) => {
    await page.unrouteAll({ behavior: 'ignoreErrors' });
    const gescheitert: string[] = [];
    for (const name of createdEinheitNames) {
        const ok = await raeumeMitWiederholung(async () => {
            await navigateToEinheiten(page);
            return loescheZeileMitText(page, name);
        });
        if (!ok) {
            gescheitert.push(name);
        }
    }
    createdEinheitNames = [];

    expect(gescheitert,
        `Testdaten blieben in der Datenbank zurueck: ${gescheitert.join(', ')}`).toEqual([]);
});

test.describe('Messpunkt-Empfang - Navigation', () => {
    test('should open the view via Admin menu with title and columns in order', async ({ page }) => {
        await navigateToMesspunktEmpfang(page);

        await expect(page).toHaveURL(/\/messpunkt-empfang$/);
        await expect(page.locator('.zev-container h1')).toContainText('Messpunkt-Empfang');
        await expect(page.locator('.zev-button-row button')).toContainText('Aktualisieren');

        // Der Mandant hat Einheiten mit Messpunkt -> die Tabelle steht
        await expect(page.locator('.zev-table')).toBeVisible({ timeout: 10000 });
        const kopf = (await page.locator('.zev-table thead th').allTextContents())
            .map(t => t.replace(/[▲▼]/g, '').trim());
        expect(kopf.slice(0, 9)).toEqual([
            'Messpunkt', 'Einheiten', 'Quelle', 'Erster Empfang', 'Letzter Empfang',
            'Zählerstand Bezug (kWh)', 'Zählerstand Einspeisung (kWh)', 'Meldungen', 'Ergebnis'
        ]);
    });
});

test.describe('Messpunkt-Empfang - Kein Empfang', () => {
    test('should show a newly created unit with meter point as Kein Empfang', async ({ page }) => {
        const name = `E2E-MPE-${kennung()}`;
        const messpunkt = `E2E-MPE-KE-${kennung()}`;
        await createEinheitOrFail(page, name, messpunkt);

        await navigateToMesspunktEmpfang(page);
        await waitForTableWithData(page, 10000);

        const zeile = page.locator('.zev-table tbody tr').filter({ hasText: messpunkt });
        await expect(zeile).toHaveCount(1, { timeout: 10000 });
        const zellen = zeile.locator('td');
        await expect(zellen.nth(0)).toHaveText(messpunkt);
        await expect(zellen.nth(1)).toHaveText(name);
        // Quelle, Erster und Letzter Empfang, Stände und Meldungen leer
        for (const i of [2, 3, 4, 5, 6, 7]) {
            await expect(zellen.nth(i)).toHaveText('–');
        }
        // Rotes Ergebnis-Badge, kein Alters-Badge
        const ergebnis = zellen.nth(8).locator('.zev-status');
        await expect(ergebnis).toHaveText('Kein Empfang');
        await expect(ergebnis).toHaveClass(/zev-status--error/);
        await expect(zellen.nth(4).locator('.zev-status')).toHaveCount(0);
        // Hat eine Einheit, keine Empfangszeile: kein Kebab-Eintrag, also kein Knopf
        await expect(zeile.locator('.zev-kebab-button')).toHaveCount(0);
    });
});

test.describe('Messpunkt-Empfang - Einheit anlegen', () => {
    test('should open the unit form with the meter point prefilled from a row without unit', async ({ page }) => {
        const messpunkt = `E2E-MPE-NEU-${kennung()}`;
        // Bewusst EINE Zeile: In einer so kurzen Tabelle in `.zev-table-scroll` war das Kebab-Menü
        // abgeschnitten und der oberste Eintrag nicht anklickbar. Seit das Menü fest im Fenster
        // liegt, ist es bedienbar (Specs/Messpunkt-Empfang.md, Anhang A.2).
        await gibListeVor(page, [empfangsZeile({ id: 990001, messpunkt })]);

        await navigateToMesspunktEmpfang(page);
        const zeile = page.locator('.zev-table tbody tr').filter({ hasText: messpunkt });
        await expect(zeile).toHaveCount(1, { timeout: 10000 });
        await expect(zeile.locator('.zev-status').last()).toHaveText('Keine Einheit zugeordnet');

        await openKebabMenu(page, zeile);
        await zeile.locator('.zev-kebab-menu__item', { hasText: 'Einheit anlegen' }).click();

        // Einheitenverwaltung mit offenem Formular für eine neue Einheit; die Parameter
        // verschwinden danach aus der URL (Einheitenverwaltung FR-8)
        await expect(page).toHaveURL(/\/einheiten$/, { timeout: 10000 });
        await expect(page.locator('form')).toBeVisible();
        await expect(page.locator('.zev-form-container h2')).toContainText('Neue Einheit');
        await expect(page.locator('#messpunkt')).toHaveValue(messpunkt);
        await expect(page.locator('#name')).toHaveValue('');

        // Nicht speichern: Abbrechen schliesst das Formular, es entsteht keine Einheit
        await page.locator('form button.zev-button--secondary').click();
        await expect(page.locator('form')).not.toBeVisible();
    });
});

test.describe('Messpunkt-Empfang - Letzte Meldung', () => {
    test('should show the last message below the row, replace it and close it', async ({ page }) => {
        const mpA = `E2E-MPE-A-${kennung()}`;
        const mpB = `E2E-MPE-B-${kennung()}`;
        await gibListeVor(page, [
            empfangsZeile({ id: 990011, messpunkt: mpA, sekundenSeitEmpfang: 30 }),
            empfangsZeile({ id: 990012, messpunkt: mpB, sekundenSeitEmpfang: 90, ergebnis: 'UNGUELTIG',
                grund: 'Payload nicht lesbar', zaehlerstandBezug: null, zaehlerstandEinspeisung: null })
        ]);
        await gibMeldungVor(page, 990011, '{"zaehler":"A","bezug":1234.5}');
        await gibMeldungVor(page, 990012, 'kein json <b>x</b>');

        await navigateToMesspunktEmpfang(page);
        const zeileA = page.locator('.zev-table tbody tr').filter({ hasText: mpA });
        const zeileB = page.locator('.zev-table tbody tr').filter({ hasText: mpB });
        await expect(zeileA).toHaveCount(1, { timeout: 10000 });
        const codeBlock = page.locator('.zev-table .zev-code-block');
        await expect(codeBlock).toHaveCount(0);

        // Meldung A: eingerücktes JSON in der Zeile direkt unter A
        await openKebabMenu(page, zeileA);
        await zeileA.locator('.zev-kebab-menu__item', { hasText: 'Letzte Meldung anzeigen' }).click();
        await expect(codeBlock).toHaveCount(1);
        expect(await codeBlock.textContent())
            .toBe('{\n  "zaehler": "A",\n  "bezug": 1234.5\n}');
        const unterA = zeileA.locator('xpath=following-sibling::tr[1]');
        await expect(unterA.locator('.zev-code-block')).toHaveCount(1);
        await expect(unterA.locator('td')).toHaveAttribute('colspan', '10');

        // Meldung B ersetzt A; kein JSON -> unverändert, Markup als Text
        await openKebabMenu(page, zeileB);
        await zeileB.locator('.zev-kebab-menu__item', { hasText: 'Letzte Meldung anzeigen' }).click();
        await expect(codeBlock).toHaveCount(1);
        await expect(codeBlock).toHaveText('kein json <b>x</b>');
        await expect(codeBlock.locator('b')).toHaveCount(0);
        await expect(zeileB.locator('xpath=following-sibling::tr[1]').locator('.zev-code-block')).toHaveCount(1);

        // Schliessen
        await page.locator('.zev-table button', { hasText: 'Schliessen' }).click();
        await expect(codeBlock).toHaveCount(0);

        // Aktualisieren schliesst eine offene Meldung ebenfalls
        await openKebabMenu(page, zeileA);
        await zeileA.locator('.zev-kebab-menu__item', { hasText: 'Letzte Meldung anzeigen' }).click();
        await expect(codeBlock).toHaveCount(1);
        await page.locator('.zev-button-row button', { hasText: 'Aktualisieren' }).click();
        await expect(codeBlock).toHaveCount(0);
        await expect(zeileA).toHaveCount(1);
    });
});
