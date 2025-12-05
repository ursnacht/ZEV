import { test as setup, expect } from '@playwright/test';

const authFile = 'playwright/.auth/user.json';

setup('authenticate', async ({ page }) => {
    // Navigate to the app - this will redirect to Keycloak login
    await page.goto('/');

    // Wait for Keycloak login page to load
    await page.waitForURL(/.*\/realms\/zev\/protocol\/openid-connect\/auth.*/);

    // Fill in login credentials
    await page.fill('input[name="username"]', 'testuser');
    await page.fill('input[name="password"]', 'testpassword');

    // Submit the login form
    await page.click('input[type="submit"]');

    // Wait for redirect back to the app
    await page.waitForURL('http://localhost:4200/**');

    // Verify we're logged in by checking for the navbar
    await expect(page.locator('.zev-navbar')).toBeVisible();

    // Save authenticated state
    await page.context().storageState({ path: authFile });
});
