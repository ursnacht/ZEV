import { Page } from '@playwright/test';

/**
 * tests / helpers.ts
 * Shared helper functions for E2E tests
 */

/**
 * Handle Keycloak login if redirected to login page
 */
export async function handleKeycloakLogin(page: Page): Promise<void> {
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
            // Login form is visible, perform login
            await loginFormLocator.fill('testuser');
            await page.fill('input[name="password"]', 'testpassword');
            // Keycloak 26+ uses a button element instead of input[type="submit"]
            await page.click('button[type="submit"], input[type="submit"]');

            // Wait for redirect and app to load
            try {
                await page.waitForURL('http://localhost:4200/**', { timeout: 15000 });
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
    // Ensure navbar is fully loaded and stable
    const navbar = page.locator('.zev-navbar');
    await navbar.waitFor({ state: 'visible', timeout: 15000 });
    // Wait for Angular to stabilize
    await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
}

/**
 * Open hamburger menu
 */
export async function openHamburgerMenu(page: Page): Promise<void> {
    const hamburger = page.locator('.zev-hamburger');
    await hamburger.waitFor({ state: 'visible', timeout: 5000 });
    await hamburger.click();
    // Wait for menu animation
    await page.waitForTimeout(150);
}

/**
 * Navigate to a specific page via hamburger menu
 */
export async function navigateViaMenu(page: Page, href: string): Promise<void> {
    await navigateToHome(page);
    await openHamburgerMenu(page);
    const menuLink = page.locator(`a[href="${href}"]`);
    await menuLink.waitFor({ state: 'visible', timeout: 5000 });
    await menuLink.click();
    // Wait for navigation to complete
    await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
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
    const successMessage = page.locator('.zev-message--success');
    const errorMessage = page.locator('.zev-message--error');

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
