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
   * Aufklapp-Zustand eines Untermenues.
   *
   * <p><b>Derzeit von keinem Menueeintrag verwendet</b> und dennoch mit Absicht hier: Seit FR-3
   * fuehrt "Nebenkosten" direkt auf die Abrechnung, das Untermenue ist weg. Die Mechanik bleibt
   * auf ausdrueckliche Weisung erhalten, damit ein kuenftiges Untermenue nur wieder angeschlossen
   * werden muss, statt neu geschrieben zu werden.
   *
   * <p>Dasselbe gilt fuer die Umgebung: Die Untermenue-Variante im Design System
   * (`design-system/src/components/navigation/`) samt Showcase und der E2E-Helfer
   * `oeffneUntermenue` in `tests/helpers.ts` sind unangetastet. Der Helfer merkt von sich aus,
   * dass kein Untermenue mehr da ist, und tut nichts.
   */
  isNebenkostenOpen = false;

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

    // Beim Laden der Seite NUR das Untermenue - das Hauptmenue bleibt zu (siehe
    // oeffneMenueBeiNavigation).
    this.oeffneUntermenueFuer(this.router.url);

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
    this.oeffneUntermenueFuer(url);

    if (this.ersteNavigationSteht) {
      this.ersteNavigationSteht = false;
      return;
    }
    if (url === '/' || url === '/startseite') {
      this.isMenuOpen = true;
    }
  }

  /**
   * Klappt das Untermenue auf, in dem die aufgerufene Seite liegt.
   *
   * <p>Anders als das Hauptmenue auch beim <b>Laden</b>: Sonst waere der aktive Eintrag nach einem
   * Reload nicht sichtbar. Ohne Untermenue im Menue bleibt das ohne Wirkung; die Zeile gehoert zur
   * erhaltenen Mechanik (siehe isNebenkostenOpen).
   */
  private oeffneUntermenueFuer(url: string): void {
    if (url.startsWith('/nebenkosten')) {
      this.isNebenkostenOpen = true;
    }
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
  }

  closeMenu() {
    this.isMenuOpen = false;
  }

  /**
   * Klappt ein Untermenue auf/zu.
   *
   * <p>Zurzeit ruft es niemand - siehe {@link isNebenkostenOpen}. Ein Elterneintrag, der nur
   * aufklappt, braucht einen Button und keinen Link; ein Eintrag, der navigiert, einen Link.
   */
  toggleNebenkosten(): void {
    this.isNebenkostenOpen = !this.isNebenkostenOpen;
  }
}
