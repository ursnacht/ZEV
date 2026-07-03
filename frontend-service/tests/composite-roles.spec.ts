import { test, expect, Page } from '@playwright/test';
import { loginAs } from './helpers';

/**
 * tests / composite-roles.spec.ts
 * E2E-Tests für die permission-basierte Autorisierung (Composite-Roles).
 *
 * Prüft das effektive Zugriffsverhalten der drei Fachrollen:
 *  - zev_admin  (testuser)  – alle Permissions
 *  - org_admin  (orgadmin)  – Lesen + einstellungen:write, KEINE Feature-Flags
 *  - zev_user   (user)      – nur Lese-Permissions
 *
 * Voraussetzung: Stack läuft mit dem migrierten Realm (Rolle zev_user, Permission-Rollen,
 * Composites) sowie dem Backend/Frontend im Permission-Modell.
 *
 * Read-only-Beobachtungen (keine Mutation mandantenweiten Zustands) -> serial zur Sicherheit.
 */

const ADMIN = { user: 'testuser', pass: 'testpassword' };        // zev_admin
const ORG_ADMIN = { user: 'orgadmin', pass: 'orgadminpassword' }; // org_admin
const MEMBER = { user: 'user', pass: 'password' };               // zev_user

test.describe.configure({ mode: 'serial' });

async function gotoRoute(page: Page, route: string): Promise<void> {
    await page.goto(route, { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
}

test.describe('Composite-Roles: permission-basierter Zugriff', () => {

    test('zev_admin sieht die Feature-Flags-Sektion auf /einstellungen', async ({ page }) => {
        await loginAs(page, ADMIN.user, ADMIN.pass);
        await gotoRoute(page, '/einstellungen');

        // Einstellungen-Formular erreichbar ...
        await expect(page.locator('#zahlungsfrist')).toBeVisible();
        // ... und die Feature-Flag-Verwaltung ist sichtbar (Toggle-Checkboxen id="flag-<key>")
        await expect(page.locator('input[id^="flag-"]').first()).toBeVisible();
    });

    test('org_admin darf /einstellungen bearbeiten, sieht aber KEINE Feature-Flags', async ({ page }) => {
        await loginAs(page, ORG_ADMIN.user, ORG_ADMIN.pass);
        await gotoRoute(page, '/einstellungen');

        // Formular erreichbar (einstellungen:write) ...
        await expect(page.locator('#zahlungsfrist')).toBeVisible();
        // ... aber keine Feature-Flag-Sektion (fehlt featureflags:manage)
        await expect(page.locator('input[id^="flag-"]')).toHaveCount(0);
    });

    test('org_admin wird vom Übersetzungs-Editor weggeleitet (fehlt translations:manage)', async ({ page }) => {
        await loginAs(page, ORG_ADMIN.user, ORG_ADMIN.pass);
        await gotoRoute(page, '/translations');

        await expect(page).toHaveURL(/\/startseite$/);
    });

    test('zev_user wird von /einstellungen weggeleitet (fehlt einstellungen:write)', async ({ page }) => {
        await loginAs(page, MEMBER.user, MEMBER.pass);
        await gotoRoute(page, '/einstellungen');

        await expect(page).toHaveURL(/\/startseite$/);
    });

    test('zev_user kann die Statistik ansehen (statistik:read)', async ({ page }) => {
        await loginAs(page, MEMBER.user, MEMBER.pass);
        await gotoRoute(page, '/statistik');

        await expect(page).toHaveURL(/\/statistik$/);
    });
});
