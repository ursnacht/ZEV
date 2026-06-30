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

@Component({
  selector: 'app-navigation',
  standalone: true,
  imports: [RouterModule, TranslatePipe, IconComponent],
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

    // Open menu on startseite
    this.checkAndOpenMenuForStartseite(this.router.url);

    // Listen for route changes
    this.routerSubscription = this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event: NavigationEnd) => {
      this.checkAndOpenMenuForStartseite(event.urlAfterRedirects || event.url);
    });
  }

  ngOnDestroy() {
    if (this.routerSubscription) {
      this.routerSubscription.unsubscribe();
    }
  }

  private checkAndOpenMenuForStartseite(url: string): void {
    if (url === '/' || url === '/startseite') {
      this.isMenuOpen = true;
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
}
