import { Page } from '@playwright/test';

/**
 * tests / helpers.ts
 * Shared helper functions for E2E tests
 */

/**
 * Handle Keycloak login if redirected to login page
 */
export async function handleKeycloakLogin(page: Page): Promise<void> {
    const currentUrl = page.url();

    if (currentUrl.includes('/realms/') && currentUrl.includes('/protocol/openid-connect/auth')) {
        await page.fill('input[name="username"]', 'testuser');
        await page.fill('input[name="password"]', 'testpassword');
        await page.click('input[type="submit"]');
        await page.waitForURL('http://localhost:4200/**', { timeout: 10000 });
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
