import { Component, OnInit, inject } from '@angular/core';
import { RouterModule } from '@angular/router';

import Keycloak from 'keycloak-js';
import { KeycloakProfile } from 'keycloak-js';
import { TranslationService } from '../../services/translation.service';
import { TranslatePipe } from '../../pipes/translate.pipe';

@Component({
  selector: 'app-navigation',
  standalone: true,
  imports: [RouterModule, TranslatePipe],
  templateUrl: './navigation.component.html',
  styleUrls: ['./navigation.component.css']
})
export class NavigationComponent implements OnInit {
  userProfile: KeycloakProfile | null = null;
  currentLang = 'de';
  organizationAlias: string | null = null;
  private readonly keycloak = inject(Keycloak);

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
