import { test, expect } from '@playwright/test';
import { handleKeycloakLogin } from './helpers';

test.describe('ZEV App', () => {
    test('should display the navigation bar with title after login', async ({ page }) => {
        await page.goto('/');

        // Handle Keycloak login if redirected
        await handleKeycloakLogin(page);

        // Wait for the navbar to be visible
        const navbar = page.locator('.zev-navbar');
        await expect(navbar).toBeVisible({ timeout: 10000 });

        // Check for navbar title
        const navbarTitle = page.locator('.zev-navbar__title');
        await expect(navbarTitle).toBeVisible();
    });

    test('should have hamburger menu button', async ({ page }) => {
        await page.goto('/');

        // Handle Keycloak login if redirected
        await handleKeycloakLogin(page);

        // Wait for the navbar to be visible first
        await page.locator('.zev-navbar').waitFor({ state: 'visible', timeout: 10000 });

        // Check that hamburger button exists
        const hamburgerButton = page.locator('.zev-hamburger');
        await expect(hamburgerButton).toBeVisible();
    });

    test('should be able to toggle hamburger menu', async ({ page }) => {
        await page.goto('/');

        // Handle Keycloak login if redirected
        await handleKeycloakLogin(page);

        // Wait for the navbar to be visible
        await page.locator('.zev-navbar').waitFor({ state: 'visible', timeout: 10000 });

        const hamburgerButton = page.locator('.zev-hamburger');
        const menu = page.locator('.zev-navbar__menu');

        // Menu should not be open initially
        await expect(menu).not.toHaveClass(/zev-navbar__menu--open/);

        // Click hamburger to open menu
        await hamburgerButton.click();
        await expect(menu).toHaveClass(/zev-navbar__menu--open/);

        // Click again to close menu
        await hamburgerButton.click();
        await expect(menu).not.toHaveClass(/zev-navbar__menu--open/);
    });
});
