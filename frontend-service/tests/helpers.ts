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
    const maxWaitTime = 15000;
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
            await page.waitForURL('http://localhost:4200/**', { timeout: 10000 });
            return;
        }

        // Neither visible yet, wait a bit and try again
        await page.waitForTimeout(200);
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
    await page.goto('/');
    await handleKeycloakLogin(page);
    await page.locator('.zev-navbar').waitFor({ state: 'visible', timeout: 10000 });
}

/**
 * Open hamburger menu
 */
export async function openHamburgerMenu(page: Page): Promise<void> {
    await page.locator('.zev-hamburger').click();
}

/**
 * Navigate to a specific page via hamburger menu
 */
export async function navigateViaMenu(page: Page, href: string): Promise<void> {
    await navigateToHome(page);
    await openHamburgerMenu(page);
    await page.locator(`a[href="${href}"]`).click();
}
