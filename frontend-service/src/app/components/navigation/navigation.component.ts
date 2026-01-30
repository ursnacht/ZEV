import { Component, OnInit, inject, OnDestroy } from '@angular/core';
import { Router, RouterModule, NavigationEnd } from '@angular/router';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';

import Keycloak from 'keycloak-js';
import { KeycloakProfile } from 'keycloak-js';
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
  organizationAlias: string | null = null;
  private readonly keycloak = inject(Keycloak);
  private readonly router = inject(Router);
  private routerSubscription: Subscription | null = null;

  isMenuOpen = false;

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
      if (token && token['organizations']) {
        const organizations = token['organizations'] as Record<string, { id: string }>;
        const aliases = Object.keys(organizations);
        if (aliases.length > 0) {
          this.organizationAlias = aliases[0];
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
    return `${firstName} ${lastName}`.trim();
  }

  get userDisplayName(): string {
    const name = this.userName;
    if (this.organizationAlias) {
      return `${name}, ${this.organizationAlias}`;
    }
    return name;
  }

  logout() {
    this.keycloak.logout();
    this.closeMenu();
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
