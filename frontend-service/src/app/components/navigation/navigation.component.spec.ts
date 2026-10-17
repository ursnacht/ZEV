import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import Keycloak from 'keycloak-js';
import { of } from 'rxjs';
import { NavigationComponent } from './navigation.component';
import { AuthService } from '../../services/auth.service';
import { FeatureFlagService } from '../../services/feature-flag.service';
import { ThemeService } from '../../services/theme.service';
import { TranslationService } from '../../services/translation.service';

/** Ziel jeder Navigation im Test - die echten Seiten braucht es nicht. */
@Component({ standalone: true, template: '' })
class LeereSeite {}

/**
 * Tests des Untermenüs "Admin" (Specs/Hamburgermenü.md, FR-2).
 *
 * <p>Die Rollen kommen wie im Betrieb aus dem Token (`realmAccess`); die Permissions der
 * Untereinträge liest die Komponente aus `app.routes.ts`.
 */
describe('NavigationComponent', () => {
  let fixture: ComponentFixture<NavigationComponent>;
  let component: NavigationComponent;

  const ALLE_ADMIN_PERMISSIONS = [
    'messwerte:write', 'systemmeldungen:read', 'einstellungen:write', 'translations:manage',
    'lizenzen:read'
  ];

  /** Kein `createSpyObj`: Die Direktive liest Felder (`realmAccess`), keine Methoden. */
  const keycloakMit = (rollen: string[]): Keycloak => ({
    authenticated: false,
    realmAccess: { roles: rollen },
    resourceAccess: {}
  } as unknown as Keycloak);

  /**
   * Baut die Komponente je Test statt in `beforeEach`: Rollen und aufgerufene URL wirken schon in
   * `ngOnInit` und unterscheiden sich von Test zu Test.
   */
  const baue = async (rollen: string[], url = '/'): Promise<void> => {
    await TestBed.configureTestingModule({
      imports: [NavigationComponent],
      providers: [
        provideRouter([{ path: '**', component: LeereSeite }]),
        { provide: Keycloak, useValue: keycloakMit(rollen) },
        { provide: AuthService, useValue: { notifyLogout: () => of(void 0) } },
        { provide: ThemeService, useValue: { isDarkMode: signal(false), toggleTheme: () => {} } },
        { provide: FeatureFlagService, useValue: { isEnabled: () => false } },
        {
          provide: TranslationService,
          useValue: { translate: (k: string) => k, currentLang: () => 'de', setLanguage: () => {} }
        }
      ]
    }).compileComponents();
    vi.spyOn(TestBed.inject(Router), 'url', 'get').mockReturnValue(url);

    fixture = TestBed.createComponent(NavigationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  };

  const el = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const adminToggle = (): HTMLButtonElement | null =>
    el().querySelector('.zev-navbar__submenu-toggle');
  const submenu = (): HTMLElement | null => el().querySelector('.zev-navbar__submenu');
  const hrefs = (wurzel: Element | null): string[] =>
    Array.from(wurzel?.querySelectorAll('a') ?? []).map(a => a.getAttribute('href') ?? '');

  describe('Admin-Untermenü', () => {
    it('should list the five admin entries in order inside the submenu', async () => {
      await baue(ALLE_ADMIN_PERMISSIONS);

      expect(adminToggle()?.textContent).toContain('ADMIN');
      expect(hrefs(submenu())).toEqual([
        '/solar-calculation', '/systemmeldungen', '/einstellungen', '/translations', '/lizenzen'
      ]);
      // In der ersten Ebene steht keiner davon mehr
      const ersteEbene = Array.from(el().querySelectorAll('.zev-navbar__menu > li > a'))
        .map(a => a.getAttribute('href'));
      for (const href of hrefs(submenu())) {
        expect(ersteEbene).not.toContain(href);
      }
    });

    it('should show only the entries the user is permitted to open', async () => {
      // Wie zev_user: alle *:read, keine Schreibrechte
      await baue(['systemmeldungen:read', 'lizenzen:read']);

      expect(adminToggle()).not.toBeNull();
      expect(hrefs(submenu())).toEqual(['/systemmeldungen', '/lizenzen']);
    });

    it('should hide Admin when no entry is permitted', async () => {
      await baue(['tarife:manage']);

      expect(adminToggle()).toBeNull();
      expect(submenu()).toBeNull();
    });

    it('should start collapsed and toggle on click', async () => {
      await baue(ALLE_ADMIN_PERMISSIONS);

      expect(component.isAdminOpen).toBe(false);
      expect(submenu()?.classList.contains('zev-navbar__submenu--open')).toBe(false);
      expect(adminToggle()?.getAttribute('aria-expanded')).toBe('false');

      adminToggle()!.click();
      fixture.detectChanges();
      expect(submenu()?.classList.contains('zev-navbar__submenu--open')).toBe(true);
      expect(adminToggle()?.getAttribute('aria-expanded')).toBe('true');

      adminToggle()!.click();
      fixture.detectChanges();
      expect(submenu()?.classList.contains('zev-navbar__submenu--open')).toBe(false);
    });

    it('should open the submenu when the current page lies in it', async () => {
      await baue(ALLE_ADMIN_PERMISSIONS, '/einstellungen');

      expect(component.isAdminOpen).toBe(true);
      expect(submenu()?.classList.contains('zev-navbar__submenu--open')).toBe(true);
    });

    it('should keep the submenu closed on a page outside of it', async () => {
      await baue(ALLE_ADMIN_PERMISSIONS, '/statistik');

      expect(component.isAdminOpen).toBe(false);
    });

    it('should collapse Admin after navigating to a page outside of it', async () => {
      await baue(ALLE_ADMIN_PERMISSIONS);
      const router = TestBed.inject(Router);
      component.toggleAdmin();
      expect(component.isAdminOpen).toBe(true);

      await router.navigateByUrl('/statistik');

      expect(component.isAdminOpen).toBe(false);
    });

    it('should keep Admin open after navigating to a page inside of it', async () => {
      await baue(ALLE_ADMIN_PERMISSIONS);
      const router = TestBed.inject(Router);
      component.toggleAdmin();

      await router.navigateByUrl('/einstellungen');

      expect(component.isAdminOpen).toBe(true);
    });

    it('should collapse a manually opened Admin when the menu is opened again', async () => {
      // Ein Klick auf die bereits aktive Seite navigiert nicht - das erneute Oeffnen setzt zurueck
      await baue(ALLE_ADMIN_PERMISSIONS, '/statistik');
      component.toggleMenu();
      component.toggleAdmin();
      component.closeMenu();

      component.toggleMenu();

      expect(component.isAdminOpen).toBe(false);
    });

    it('should take the permissions of the entries from the routes', async () => {
      await baue(ALLE_ADMIN_PERMISSIONS);

      expect(component.adminEintraege.map(e => e.permissions)).toEqual(
        ALLE_ADMIN_PERMISSIONS.map(p => [p]));
      expect(component.adminPermissions).toEqual(ALLE_ADMIN_PERMISSIONS);
    });
  });
});
