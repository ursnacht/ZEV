import { test, expect, Page } from '@playwright/test';
import { loginAs, navigateViaMenu } from './helpers';

/**
 * tests / systemmeldungen.spec.ts
 * E2E-Tests der Seite Systemmeldungen (Specs/Systemmeldungen.md).
 *
 * Schwerpunkte:
 *   - Die Route ist über das Menü erreichbar und rendert (sie war bisher gar nicht abgedeckt).
 *   - Filter (Erledigt/Kategorie/Level), Sortierung und Paginierung laufen serverseitig — geprüft
 *     wird, dass die Bedienung die Liste tatsächlich neu lädt und die Seite dabei ganz bleibt.
 *   - FR-1.7: Wer nur `systemmeldungen:read` besitzt, sieht die Liste, kann aber nichts umschalten
 *     und nichts löschen. Das ist der sicherheitsrelevante Teil.
 *
 * KEINE Daten-Voraussetzung: Die Tabelle kann im Testmandanten leer sein. Die Tests prüfen
 * deshalb entweder Tabelle **oder** Leerstate — und dort, wo Zeilen nötig sind, wird das
 * ausdrücklich übersprungen statt falsch grün zu melden.
 *
 * Read-only bis auf den Filterzustand (rein clientseitig, kein mandantenweiter Zustand) —
 * trotzdem serial, weil mehrere Tests denselben Benutzer an- und abmelden.
 */

const ADMIN = { user: 'testuser', pass: 'testpassword' };  // zev_admin: read + manage
const MEMBER = { user: 'user', pass: 'password' };         // zev_user: nur read

test.describe.configure({ mode: 'serial' });

test.beforeEach(({ browserName }) => {
    test.skip(browserName !== 'chromium',
        'Der Anmeldezustand ist mandantenweit; nur in einem Browser testen.');
});

/** Wartet, bis die Liste geladen ist — Tabelle oder Leerstate ist dann sichtbar. */
async function warteAufListe(page: Page): Promise<void> {
    await expect(page.locator('table.zev-table, .zev-empty-state').first())
        .toBeVisible({ timeout: 15000 });
}

async function oeffneSystemmeldungen(page: Page): Promise<void> {
    await navigateViaMenu(page, '/systemmeldungen');
    await expect(page.locator('#erledigtFilter')).toBeVisible({ timeout: 15000 });
    await warteAufListe(page);
}

/**
 * Fuehrt eine Interaktion aus und wartet auf die Antwort der Listen-Abfrage.
 *
 * Notwendig, weil Filter und Sortierung serverseitig laufen: Bis die Antwort da ist, steht noch
 * die ALTE Tabelle im DOM. Ein blosses Warten auf Sichtbarkeit greift deshalb zu frueh, und eine
 * dazwischen genommene Zeilen- oder Badge-Zahl gehoert noch zur vorherigen Liste.
 */
async function mitNeuladen(page: Page, interaktion: () => Promise<void>): Promise<void> {
    await Promise.all([
        page.waitForResponse(r => r.url().includes('/api/systemmeldungen')
            && !r.url().includes('/kategorien') && r.request().method() === 'GET',
            { timeout: 15000 }),
        interaktion()
    ]);
    await warteAufListe(page);
}

/** Anzahl Datenzeilen; 0 wenn der Leerstate steht. */
async function zeilen(page: Page): Promise<number> {
    if (await page.locator('table.zev-table').count() === 0) {
        return 0;
    }
    return page.locator('table.zev-table tbody tr').count();
}

test.describe('Systemmeldungen - Seite und Filter', () => {

    test('should be reachable from the menu and render the filter row', async ({ page }) => {
        await loginAs(page, ADMIN.user, ADMIN.pass);
        await oeffneSystemmeldungen(page);

        await expect(page).toHaveURL(/\/systemmeldungen$/);
        await expect(page.locator('#erledigtFilter')).toBeVisible();
        await expect(page.locator('#kategorieFilter')).toBeVisible();
        await expect(page.locator('#levelFilter')).toBeVisible();
    });

    test('should default to the open messages filter', async ({ page }) => {
        // FR-1.3: Die Seite geht mit dem auf, was zu tun ist.
        await loginAs(page, ADMIN.user, ADMIN.pass);
        await oeffneSystemmeldungen(page);

        await expect(page.locator('#erledigtFilter')).toHaveValue('OFFENE');
        await expect(page.locator('#kategorieFilter')).toHaveValue('');
        await expect(page.locator('#levelFilter')).toHaveValue('');
    });

    test('should switch between all three states of the erledigt filter', async ({ page }) => {
        await loginAs(page, ADMIN.user, ADMIN.pass);
        await oeffneSystemmeldungen(page);

        for (const wert of ['ALLE', 'ERLEDIGTE', 'OFFENE']) {
            await mitNeuladen(page, () =>
                page.locator('#erledigtFilter').selectOption(wert));
            await expect(page.locator('#erledigtFilter')).toHaveValue(wert);
        }
    });

    test('should never show fewer entries under "Alle" than under "Offene"', async ({ page }) => {
        // Die Kernaussage des dreiwertigen Filters: "Alle" ist eine Obermenge von "Offene".
        // Mit erledigt=false anstelle von undefined waere sie gleich gross - und die erledigten
        // Meldungen unerreichbar.
        await loginAs(page, ADMIN.user, ADMIN.pass);
        await oeffneSystemmeldungen(page);

        await mitNeuladen(page, () => page.locator('#erledigtFilter').selectOption('OFFENE'));
        const offene = await zeilen(page);

        await mitNeuladen(page, () => page.locator('#erledigtFilter').selectOption('ALLE'));
        const alle = await zeilen(page);

        expect(alle).toBeGreaterThanOrEqual(offene);
    });

    test('should filter by level without breaking the page', async ({ page }) => {
        await loginAs(page, ADMIN.user, ADMIN.pass);
        await oeffneSystemmeldungen(page);

        await mitNeuladen(page, () => page.locator('#erledigtFilter').selectOption('ALLE'));
        await mitNeuladen(page, () => page.locator('#levelFilter').selectOption('ERROR'));

        // Jede angezeigte Zeile muss das gewaehlte Level tragen. Gezaehlt werden die Badges
        // NACH dem Filterwechsel - eine vorher genommene Zeilenzahl gehoerte noch zur alten
        // Liste und der Vergleich liefe ins Leere, sobald ERROR nichts trifft.
        const badges = page.locator('table.zev-table tbody tr td:first-child .zev-status');
        const anzahlBadges = await badges.count();
        for (let i = 0; i < anzahlBadges; i++) {
            await expect(badges.nth(i)).toHaveClass(/zev-status--error/);
        }
        await expect(page.locator('.zev-message--error')).toHaveCount(0);
    });

    test('should offer only existing categories in the category filter', async ({ page }) => {
        // Die Auswahl ergibt sich aus den vorhandenen Kategorien (FR-1.3) - "Alle" ist immer dabei.
        await loginAs(page, ADMIN.user, ADMIN.pass);
        await oeffneSystemmeldungen(page);

        // `toHaveValue` gilt fuer das Auswahlfeld, nicht fuer eine Option - deren Wert steht
        // im Attribut.
        const optionen = page.locator('#kategorieFilter option');
        await expect(optionen.first()).toHaveAttribute('value', '');
        expect(await optionen.count()).toBeGreaterThanOrEqual(1);

        // Jede weitere Option ist ein Kategorie-Key und damit nicht leer.
        const anzahl = await optionen.count();
        for (let i = 1; i < anzahl; i++) {
            await expect(optionen.nth(i)).not.toHaveAttribute('value', '');
        }
    });
});

test.describe('Systemmeldungen - Sortierung und Paginierung', () => {

    test('should sort by a column and show the direction indicator', async ({ page }) => {
        await loginAs(page, ADMIN.user, ADMIN.pass);
        await oeffneSystemmeldungen(page);
        await mitNeuladen(page, () => page.locator('#erledigtFilter').selectOption('ALLE'));

        if (await zeilen(page) === 0) {
            test.skip(true, 'Keine Systemmeldungen im Testmandanten - Sortierung nicht pruefbar.');
        }

        const levelHeader = page.locator('th.zev-table__header--sortable').first();
        await mitNeuladen(page, () => levelHeader.click());
        await expect(levelHeader.locator('.zev-table__sort-indicator')).toBeVisible();
        const erste = await levelHeader.locator('.zev-table__sort-indicator').textContent();

        // Zweiter Klick dreht die Richtung um.
        await mitNeuladen(page, () => levelHeader.click());
        await expect(levelHeader.locator('.zev-table__sort-indicator')).not.toHaveText(erste ?? '');
    });

    test('should disable the previous-page button on the first page', async ({ page }) => {
        await loginAs(page, ADMIN.user, ADMIN.pass);
        await oeffneSystemmeldungen(page);
        await mitNeuladen(page, () => page.locator('#erledigtFilter').selectOption('ALLE'));

        if (await zeilen(page) === 0) {
            test.skip(true, 'Keine Systemmeldungen im Testmandanten - Paginierung nicht sichtbar.');
        }

        await expect(page.locator('.zev-pagination')).toBeVisible();
        await expect(page.locator('.zev-pagination button').first()).toBeDisabled();
        await expect(page.locator('.zev-pagination')).toContainText('1');
    });
});

test.describe('Systemmeldungen - Berechtigungen (FR-1.7)', () => {

    test('zev_admin sees the checkbox enabled and the cleanup button', async ({ page }) => {
        await loginAs(page, ADMIN.user, ADMIN.pass);
        await oeffneSystemmeldungen(page);
        await mitNeuladen(page, () => page.locator('#erledigtFilter').selectOption('ALLE'));

        // Der Aufraeum-Button haengt allein an systemmeldungen:manage und ist datenunabhaengig.
        await expect(page.locator('button.zev-button--danger')).toBeVisible();

        if (await zeilen(page) > 0) {
            await expect(page.locator('table.zev-table tbody input[type="checkbox"]').first())
                .toBeEnabled();
        }
    });

    /**
     * Der sicherheitsrelevante Fall: Lesen erlaubt Ansehen, nicht Verwalten.
     *
     * Geprueft wird die Oberflaeche - die Route bleibt erreichbar (`systemmeldungen:read`), aber
     * der Aufraeum-Button fehlt, die Erledigt-Checkbox ist gesperrt und es gibt kein Kebab-Menue.
     * Die serverseitige Durchsetzung liegt bei `@PreAuthorize` und ist im
     * ControllerAuthorizationTest abgedeckt.
     */
    test('zev_user sees the list read-only: no cleanup, no toggle, no kebab', async ({ page }) => {
        await loginAs(page, MEMBER.user, MEMBER.pass);
        await oeffneSystemmeldungen(page);
        await mitNeuladen(page, () => page.locator('#erledigtFilter').selectOption('ALLE'));

        // Die Seite ist erreichbar ...
        await expect(page).toHaveURL(/\/systemmeldungen$/);
        await expect(page.locator('#erledigtFilter')).toBeVisible();

        // ... aber nichts davon ist bedienbar.
        await expect(page.locator('button.zev-button--danger')).toHaveCount(0);
        await expect(page.locator('app-kebab-menu')).toHaveCount(0);

        if (await zeilen(page) > 0) {
            await expect(page.locator('table.zev-table tbody input[type="checkbox"]').first())
                .toBeDisabled();
        }
    });
});
