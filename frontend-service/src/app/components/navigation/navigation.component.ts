import { Component, OnInit, inject, OnDestroy, HostListener, HostBinding } from '@angular/core';
import { Router, RouterModule, NavigationEnd } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';

import Keycloak from 'keycloak-js';
import { KeycloakProfile } from 'keycloak-js';
import { AuthService } from '../../services/auth.service';
import { ThemeService } from '../../services/theme.service';
import { TranslationService } from '../../services/translation.service';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { IconComponent } from '../icon/icon.component';
import { FeatureFlagDirective } from '../../directives/feature-flag.directive';
import { PermissionDirective } from '../../directives/permission.directive';
import { routes } from '../../app.routes';

/** Ein Untereintrag des Menüs: Route, Beschriftung (Translation-Key), Icon und Permissions. */
export interface MenueEintrag {
  route: string;
  key: string;
  icon: string;
  permissions: string[];
}

/**
 * Die Permissions einer Route, gelesen aus der Routen-Konfiguration.
 *
 * <p>Eine Quelle für Menü und `AuthGuard`: Stünden sie hier ein zweites Mal, könnte das Menü
 * einen Eintrag zeigen, den die Route abweist - oder umgekehrt.
 */
function permissionsDerRoute(route: string): string[] {
  const pfad = route.replace(/^\//, '');
  return (routes.find(r => r.path === pfad)?.data?.['permissions'] as string[] | undefined) ?? [];
}

function eintrag(route: string, key: string, icon: string): MenueEintrag {
  return { route, key, icon, permissions: permissionsDerRoute(route) };
}

@Component({
  selector: 'app-navigation',
  standalone: true,
  imports: [RouterModule, TranslatePipe, IconComponent, FeatureFlagDirective, PermissionDirective],
  templateUrl: './navigation.component.html',
  styleUrls: ['./navigation.component.css']
})
export class NavigationComponent implements OnInit, OnDestroy {
  userProfile: KeycloakProfile | null = null;
  currentLang = 'de';
  organizationName: string | null = null;
  private readonly keycloak = inject(Keycloak);
  private readonly router = inject(Router);
  private routerSubscription: Subscription | null = null;

  private readonly authService = inject(AuthService);
  private readonly themeService = inject(ThemeService);

  readonly isDarkMode = this.themeService.isDarkMode;

  isMenuOpen = false;
  isCompact = false;

  /**
   * Ob das erste `NavigationEnd` noch aussteht.
   *
   * <p>Beim Laden der Seite fuehrt der Router eine erste Navigation aus, die genauso ein
   * `NavigationEnd` ausloest wie ein Klick im Menue. Beides liesse sich sonst nicht
   * auseinanderhalten - und genau daran haengt, ob das Menue beim Neuladen aufspringt.
   */
  private ersteNavigationSteht = true;

  /**
   * Die selten gebrauchten Seiten im Untermenü "Admin", in Anzeigereihenfolge
   * (Specs/Hamburgermenü.md, FR-2).
   */
  readonly adminEintraege: MenueEintrag[] = [
    eintrag('/einheiten', 'EINHEITEN_VERWALTUNG', 'home'),
    eintrag('/solar-calculation', 'SOLAR_DISTRIBUTION', 'zap'),
    eintrag('/systemmeldungen', 'SYSTEMMELDUNGEN', 'file-text'),
    eintrag('/einstellungen', 'EINSTELLUNGEN', 'settings'),
    eintrag('/translations', 'TRANSLATION_EDITOR', 'globe'),
    eintrag('/lizenzen', 'LIZENZEN', 'shield')
  ];

  /** "Admin" erscheint mit einer dieser Permissions - also mit mindestens einem Untereintrag. */
  readonly adminPermissions: string[] =
    [...new Set(this.adminEintraege.flatMap(e => e.permissions))];

  /**
   * Aufklapp-Zustand von "Admin": zugeklappt, ausser die aktuelle Seite liegt darin. Ein Klick auf
   * "Admin" schaltet um - bis zur naechsten Navigation oder zum naechsten Oeffnen des Menues.
   */
  isAdminOpen = false;

  @HostListener('window:scroll')
  onWindowScroll(): void {
    this.isCompact = window.scrollY > 50;
  }

  constructor(
    public translationService: TranslationService
  ) { }

  async ngOnInit() {
    if (this.keycloak.authenticated) {
      this.userProfile = await this.keycloak.loadUserProfile();
      this.extractOrganization();
    }
    this.currentLang = this.translationService.currentLang();

    // Beim Laden der Seite NUR den Zustand des Untermenues setzen - das Hauptmenue bleibt zu (siehe
    // oeffneMenueBeiNavigation).
    this.setzeUntermenueFuer(this.router.url);

    // Listen for route changes
    this.routerSubscription = this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event: NavigationEnd) => {
      this.oeffneMenueBeiNavigation(event.urlAfterRedirects || event.url);
    });
  }

  ngOnDestroy() {
    if (this.routerSubscription) {
      this.routerSubscription.unsubscribe();
    }
  }

  /**
   * Klappt auf der Startseite das Menue auf - aber <b>nicht</b> beim Laden der Seite.
   *
   * <p>Auf der Startseite soll das Menue offen sein: Sie hat nichts als den Weg weiter
   * (`Specs/Startseite_Umsetzungsplan.md`). Beim <b>Neuladen</b> stoert das jedoch - man will die
   * Seite sehen, nicht ein Menue wegklicken, und vor allem verschwindet es beim Reload nicht,
   * sondern kommt zurueck.
   *
   * <p><b>Warum ein Merker und keine Pruefung in `ngOnInit`:</b> Der Router fuehrt beim Laden
   * selbst eine erste Navigation aus, die hier ankommt wie ein Klick. Es genuegt also nicht, den
   * Aufruf in `ngOnInit` wegzulassen - das erste `NavigationEnd` haette das Menue trotzdem
   * geoeffnet.
   */
  private oeffneMenueBeiNavigation(url: string): void {
    this.setzeUntermenueFuer(url);

    if (this.ersteNavigationSteht) {
      this.ersteNavigationSteht = false;
      return;
    }
    if (url === '/' || url === '/startseite') {
      this.isMenuOpen = true;
    }
  }

  /**
   * Setzt "Admin" auf den Zustand, den die Seite vorgibt: aufgeklappt genau dann, wenn sie darin
   * liegt (FR-2.3, FR-2.4).
   *
   * <p>Beim Laden, nach jeder Navigation und beim Oeffnen des Menues - so verhaelt sich das Menue
   * nach einem Klick wie nach einem Reload. Das Oeffnen braucht es zusaetzlich: Ein Klick auf die
   * bereits aktive Seite loest keine Navigation aus.
   */
  private setzeUntermenueFuer(url: string): void {
    this.isAdminOpen = this.adminEintraege.some(e => url === e.route
        || url.startsWith(e.route + '/') || url.startsWith(e.route + '?'));
  }

  private extractOrganization(): void {
    try {
      const token = this.keycloak.tokenParsed;
      // Keycloak Organizations liefert den Claim "organization" (Singular):
      // { "<alias>": { "id": "uuid", "displayName": ["Name"] } }
      const organizations = token?.['organization'] as
        | Record<string, { id?: string; displayName?: string[] }>
        | undefined;
      if (organizations) {
        const aliases = Object.keys(organizations);
        if (aliases.length > 0) {
          const alias = aliases[0];
          // Optionalen Anzeigenamen (displayName) verwenden, sonst Alias als Fallback
          const displayName = organizations[alias]?.displayName?.[0];
          this.organizationName = displayName?.trim() || alias;
        }
      }
    } catch (e) {
      console.warn('Could not extract organization from token', e);
    }
  }

  get userName(): string {
    if (!this.userProfile) {
      return '';
    }
    const firstName = this.userProfile.firstName || '';
    const lastName = this.userProfile.lastName || '';
    const fullName = `${firstName} ${lastName}`.trim();
    return fullName || this.userProfile.username || '';
  }


  logout() {
    this.closeMenu();
    // Notify the backend while the token is still valid, then always redirect to the
    // Keycloak logout - even if the notification fails - so the user can always log out.
    this.authService.notifyLogout().subscribe({
      next: () => this.keycloak.logout(),
      error: () => this.keycloak.logout()
    });
  }

  toggleDarkMode(): void {
    this.themeService.toggleTheme();
  }

  switchLanguage() {
    this.currentLang = this.currentLang === 'de' ? 'en' : 'de';
    this.translationService.setLanguage(this.currentLang as 'de' | 'en');
    this.closeMenu();
  }

  toggleMenu() {
    this.isMenuOpen = !this.isMenuOpen;
    if (this.isMenuOpen) {
      this.setzeUntermenueFuer(this.router.url);
    }
  }

  closeMenu() {
    this.isMenuOpen = false;
  }

  /**
   * Klappt "Admin" auf/zu. Der Elterneintrag ist ein Button, kein Link: Er navigiert nicht.
   */
  toggleAdmin(): void {
    this.isAdminOpen = !this.isAdminOpen;
  }
}
