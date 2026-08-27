import { expect, Page } from '@playwright/test';

/**
 * tests / helpers.ts
 * Shared helper functions for E2E tests
 */

/**
 * Handle Keycloak login if redirected to login page.
 * @param username Keycloak-Benutzername (Default: testuser / zev_admin)
 * @param password Passwort (Default: testpassword)
 */
export async function handleKeycloakLogin(
    page: Page,
    username: string = 'testuser',
    password: string = 'testpassword'
): Promise<void> {
    // Wait for either the app to load or the login page to appear
    const loginFormLocator = page.locator('input[name="username"]');
    const navbarLocator = page.locator('.zev-navbar');

    // Wait for page to stabilize - either login form or app navbar should appear
    const maxWaitTime = 30000; // Increased timeout for slower environments
    const startTime = Date.now();

    while (Date.now() - startTime < maxWaitTime) {
        const isLoginFormVisible = await loginFormLocator.isVisible().catch(() => false);
        const isNavbarVisible = await navbarLocator.isVisible().catch(() => false);

        if (isNavbarVisible) {
            // Already logged in, nothing to do
            return;
        }

        if (isLoginFormVisible) {
            // Keycloak 26+ uses a multi-step login: first username, then password
            // Step 1: Enter username and submit
            await loginFormLocator.fill(username);
            await page.click('button[type="submit"], input[type="submit"]');

            // Step 2: Wait for password field (multi-step) or check if already redirected
            const passwordLocator = page.locator('input[name="password"]');
            try {
                await passwordLocator.waitFor({ state: 'visible', timeout: 5000 });
                // Password field appeared - enter password and submit
                await passwordLocator.fill(password);
                await page.click('button[type="submit"], input[type="submit"]');
            } catch {
                // Password field didn't appear - might be single-step login or error
                // Check if we're already redirected to the app
                const navbarVisible = await navbarLocator.isVisible().catch(() => false);
                if (navbarVisible) {
                    return;
                }
                // Check for any error message or unexpected state
                const errorLocator = page.locator('.kc-feedback-text, .alert, [role="alert"]');
                const hasError = await errorLocator.isVisible().catch(() => false);
                if (hasError) {
                    const errorText = await errorLocator.textContent().catch(() => 'Unknown error');
                    throw new Error(`Login failed: ${errorText}`);
                }
            }

            // Warten, bis der Browser Keycloak verlassen hat - bewusst OHNE feste Basis-URL:
            // Die Anwendung laeuft je nach Umgebung unter :8000 (Reverse-Proxy) oder :4200
            // (Dev-Server). Eine hartcodierte URL wuerde hier nicht matchen, in den Timeout
            // laufen und JEDEN Test um die volle Wartezeit verlaengern (der catch-Zweig unten
            // faengt das stillschweigend ab - Symptom: alles ist langsam, nichts schlaegt fehl).
            try {
                await page.waitForURL((url) => !url.pathname.includes('/realms/'), { timeout: 15000 });
                // Wait for navbar to appear after login
                await navbarLocator.waitFor({ state: 'visible', timeout: 10000 });
            } catch {
                // If URL redirect fails, check if we're already on the app
                const navbarVisible = await navbarLocator.isVisible().catch(() => false);
                if (!navbarVisible) {
                    throw new Error('Login failed: App did not load after login');
                }
            }
            return;
        }

        // Neither visible yet, wait a bit and try again
        await page.waitForTimeout(300);
    }

    // Timeout - check final state
    const finalNavbarVisible = await navbarLocator.isVisible().catch(() => false);
    if (!finalNavbarVisible) {
        throw new Error('Login timeout: Neither login form nor navbar appeared');
    }
}

/**
 * Navigate to home page and handle login
 */
export async function navigateToHome(page: Page): Promise<void> {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await handleKeycloakLogin(page);
    await waitForAppReady(page);
}

/**
 * Wartet auf einen **deterministischen** Bereitschafts-Zustand der App: Navbar sichtbar und
 * Hamburger-Button bedienbar. Ersetzt das frühere `waitForLoadState('networkidle')`, das
 * unzuverlässig ist (Playwright rät davon ab), in Firefox anders greift als in Chromium und
 * dessen Timeout hier zudem verschluckt wurde – der Helper kehrte dann zurück, bevor die Seite
 * bereit war, und der Test lief in die Assertion (Hauptquelle der Flakiness).
 */
export async function waitForAppReady(page: Page): Promise<void> {
    await page.locator('.zev-navbar').waitFor({ state: 'visible', timeout: 15000 });
    await page.locator('.zev-hamburger[aria-label="Menu"]').waitFor({ state: 'visible', timeout: 15000 });
}

/**
 * Navigate to home page and log in as a specific user (für Rollen-/Permission-Tests).
 * Jeder Playwright-Test läuft in einem frischen Context (keine geteilte SSO-Session),
 * daher kann pro Test ein anderer Benutzer angemeldet werden.
 */
export async function loginAs(page: Page, username: string, password: string): Promise<void> {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await handleKeycloakLogin(page, username, password);
    await waitForAppReady(page);
}

/**
 * Open hamburger menu
 */
export async function openHamburgerMenu(page: Page): Promise<void> {
    const hamburger = page.locator('.zev-hamburger[aria-label="Menu"]');
    await hamburger.waitFor({ state: 'visible', timeout: 5000 });
    await hamburger.click();
    // Wait for menu animation
    await page.waitForTimeout(150);
}

/**
 * Klappt das Untermenü auf, in dem der Eintrag liegt (z.B. Nebenkosten).
 *
 * Zwingend VOR dem Klick auf den Eintrag: Ein eingeklapptes Untermenü ist
 * `max-height: 0` mit `overflow: hidden` — der Eintrag behält damit eine Bounding-Box und gilt
 * für Playwright als sichtbar. Der Klick geht auf seine Mitte, die weggeschnitten ist, und trifft
 * die Umschalt-Schaltfläche darüber: Das Menü klappt auf, navigiert wird aber nicht. Der Test
 * scheitert dann erst später beim Warten auf die URL, und die Meldung nennt nur das Symptom.
 *
 * Tut nichts, wenn der Eintrag nicht in einem Untermenü liegt oder es bereits offen ist.
 */
async function oeffneUntermenue(page: Page, href: string): Promise<void> {
    const submenu = page.locator(`.zev-navbar__submenu:has(a[href="${href}"])`).first();
    if (await submenu.count() === 0) {
        return;
    }
    const istOffen = await submenu.evaluate(
        el => el.classList.contains('zev-navbar__submenu--open')).catch(() => true);
    if (istOffen) {
        return;
    }

    await page.locator(`li:has(a[href="${href}"]) .zev-navbar__submenu-toggle`).first().click();
    // Auf die Klasse warten, nicht auf eine feste Zeit: Die Aufklapp-Animation laeuft ueber
    // max-height und ist je nach Umgebung verschieden schnell.
    await submenu.locator('xpath=.').waitFor({ state: 'visible', timeout: 5000 });
    for (let i = 0; i < 25; i++) {
        if (await submenu.evaluate(el => el.classList.contains('zev-navbar__submenu--open'))) {
            break;
        }
        await page.waitForTimeout(100);
    }
}

/**
 * Navigate to a specific page via hamburger menu
 */
export async function navigateViaMenu(page: Page, href: string): Promise<void> {
    await navigateToHome(page);
    await openHamburgerMenu(page);
    const menuLink = page.locator(`a[href="${href}"]`);
    await menuLink.waitFor({ state: 'visible', timeout: 5000 });
    await oeffneUntermenue(page, href);
    // Das Hamburger-Menü kann bei vielen Einträgen scrollen -> Eintrag zuerst in den
    // Sichtbereich bringen. Falls das Overlay in headless zeitweise nicht klickbar ist
    // (z.B. Cold-Start-Layout), sauber auf direkte Navigation zurückfallen.
    await menuLink.scrollIntoViewIfNeeded().catch(() => {});
    try {
        await menuLink.click({ timeout: 5000 });
    } catch {
        try {
            await menuLink.click({ force: true, timeout: 5000 });
        } catch {
            await page.goto(href, { waitUntil: 'domcontentloaded' });
        }
    }
    // Auf die tatsächliche Route warten (statt auf 'networkidle'): deterministisch, gilt auch
    // nach dem goto-Fallback oben und schlägt bei ausbleibender Navigation ehrlich fehl,
    // statt den Test stillschweigend zu früh weiterlaufen zu lassen.
    await page.waitForURL((url) => url.pathname === href, { timeout: 15000 });
}

/**
 * Click on a kebab menu item within a table row
 * @param page - The Playwright page object
 * @param row - The table row locator containing the kebab menu
 * @param action - The action to click ('edit' or 'delete')
 */
export async function clickKebabMenuItem(page: Page, row: ReturnType<Page['locator']>, action: 'edit' | 'delete'): Promise<void> {
    // Click the kebab button to open the menu
    const kebabButton = row.locator('.zev-kebab-button');
    await kebabButton.click();

    // Wait for menu to be visible
    const menu = row.locator('.zev-kebab-menu--open');
    await menu.waitFor({ state: 'visible', timeout: 2000 });

    // Click the appropriate menu item
    if (action === 'delete') {
        const deleteItem = row.locator('.zev-kebab-menu__item--danger');
        await deleteItem.click();
    } else {
        // 'edit' - click the first non-danger item
        const editItem = row.locator('.zev-kebab-menu__item:not(.zev-kebab-menu__item--danger)').first();
        await editItem.click();
    }
}

/**
 * Open a kebab menu within a table row
 * @param page - The Playwright page object
 * @param row - The table row locator containing the kebab menu
 */
export async function openKebabMenu(page: Page, row: ReturnType<Page['locator']>): Promise<void> {
    const kebabButton = row.locator('.zev-kebab-button');
    await kebabButton.click();

    // Wait for menu to be visible
    const menu = row.locator('.zev-kebab-menu--open');
    await menu.waitFor({ state: 'visible', timeout: 2000 });
}

/**
 * Close an open kebab menu by clicking outside
 * @param page - The Playwright page object
 */
export async function closeKebabMenu(page: Page): Promise<void> {
    // Click on the body to close any open menus
    await page.locator('body').click({ position: { x: 10, y: 10 } });
    await page.waitForTimeout(200);
}

/**
 * Close an open kebab menu by pressing ESC
 * @param page - The Playwright page object
 */
export async function closeKebabMenuWithEsc(page: Page): Promise<void> {
    await page.keyboard.press('Escape');
    await page.waitForTimeout(200);
}

/**
 * Wait for a form submission result (success or error message)
 * Returns true if success, false if error, throws if timeout
 */
export async function waitForFormResult(page: Page, timeout: number = 15000): Promise<boolean> {
    // `.first()`, weil `isVisible()` auf einem mehrdeutigen Locator eine Strict-Mode-Verletzung
    // wirft - die das `catch` unten stillschweigend als "nicht sichtbar" wertet. Aus zwei
    // gleichzeitigen Meldungen wuerde so "gar keine Meldung", und der Test liefe in den Timeout
    // statt den eigentlichen Befund zu nennen.
    const successMessage = page.locator('.zev-message--success').first();
    const errorMessage = page.locator('.zev-message--error').first();

    const startTime = Date.now();
    while (Date.now() - startTime < timeout) {
        const isSuccess = await successMessage.isVisible().catch(() => false);
        const isError = await errorMessage.isVisible().catch(() => false);

        if (isSuccess) return true;
        if (isError) return false;

        await page.waitForTimeout(200);
    }

    // One final check
    const finalSuccess = await successMessage.isVisible().catch(() => false);
    if (finalSuccess) return true;

    throw new Error('Form submission timeout: No success or error message appeared');
}

/**
 * Wait for table to be visible and have data
 */
export async function waitForTableWithData(page: Page, timeout: number = 10000): Promise<boolean> {
    const table = page.locator('.zev-table');
    try {
        await table.waitFor({ state: 'visible', timeout });
        // Wait for at least one row
        const rows = page.locator('.zev-table tbody tr');
        const count = await rows.count();
        return count > 0;
    } catch {
        return false;
    }
}

/**
 * Calculates the previous quarter relative to today
 * (mirrors the default period selection on /rechnungen, /debitoren and /chart).
 */
export function getPreviousQuarter(): { label: string; von: string; bis: string } {
    const now = new Date();
    let year = now.getFullYear();
    let quarter = Math.ceil((now.getMonth() + 1) / 3) - 1;
    if (quarter < 1) {
        quarter = 4;
        year--;
    }
    const startMonth = (quarter - 1) * 3;
    const format = (d: Date) =>
        `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    return {
        label: `Q${quarter}/${year}`,
        von: format(new Date(year, startMonth, 1)),
        bis: format(new Date(year, startMonth + 3, 0))
    };
}

/**
 * Loescht die Zeile, die den Text enthaelt, ueber das Kebab-Menue. Erwartet die geoeffnete,
 * geladene Liste.
 *
 * <p>Liefert `true`, wenn danach keine solche Zeile mehr steht — auch dann, wenn von Anfang an
 * keine da war. `false` heisst: Der Datensatz steht noch, das Aufraeumen ist gescheitert. Der
 * Aufrufer soll das im `afterEach` **melden**, nicht auf die Konsole schreiben: Ein Rueckstand ist
 * ein Befund, und eine gruene Suite ueber einer volllaufenden Datenbank ist schlimmer als ein
 * roter Test.
 *
 * <p>Die Existenzpruefung **wartet**. `isVisible()` fragt ohne zu warten, und die Zeile erscheint
 * erst mit der Antwort der Listenabfrage; zu frueh gefragt hiess "nicht vorhanden", und der
 * Datensatz blieb stillschweigend liegen. Genau so entstanden die Rueckstaende, die zuerst in der
 * Nebenkosten- und dann in der Tarifpositionen-Suite aufgefallen sind.
 *
 * @param page    Seite mit der geoeffneten Liste
 * @param text    Text, den die zu loeschende Zeile enthaelt (typischerweise der Name)
 * @param timeout Wartezeit je Schritt
 */
export async function loescheZeileMitText(page: Page, text: string,
                                          timeout: number = 10000): Promise<boolean> {
    page.removeAllListeners('dialog');
    try {
        const zeile = page.locator(`tr:has-text("${text}")`);

        const vorhanden = await zeile.first().waitFor({ state: 'visible', timeout })
            .then(() => true).catch(() => false);
        if (!vorhanden) {
            return true;
        }

        page.once('dialog', async dialog => { await dialog.accept(); });

        // Bewusst der **letzte** gefaehrliche Eintrag und nicht `clickKebabMenuItem`: Eine Zeile
        // kann mehrere davon tragen - eine bezahlte Forderung bietet "Zahldatum loeschen" UND
        // "Loeschen", beide als `--danger`. Ein mehrdeutiger Locator wirft im Strict Mode, und der
        // Datensatz blieb liegen. "Loeschen" steht ueberall zuletzt.
        const erste = zeile.first();
        await erste.locator('.zev-kebab-button').click();
        await erste.locator('.zev-kebab-menu--open').waitFor({ state: 'visible', timeout: 5000 });
        await erste.locator('.zev-kebab-menu__item--danger').last().click();

        await expect(zeile).toHaveCount(0, { timeout });
        return true;
    } catch (error) {
        console.error(`CLEANUP FEHLGESCHLAGEN: "${text}" - ${error}`);
        return false;
    } finally {
        page.removeAllListeners('dialog');
    }
}
