import { Component, OnInit, inject } from '@angular/core';
import { RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';

import Keycloak from 'keycloak-js';
import { KeycloakProfile } from 'keycloak-js';
import { TranslationService } from '../../services/translation.service';
import { TranslatePipe } from '../../pipes/translate.pipe';

@Component({
  selector: 'app-navigation',
  standalone: true,
  imports: [CommonModule, RouterModule, TranslatePipe],
  templateUrl: './navigation.component.html',
  styleUrls: ['./navigation.component.css']
})
export class NavigationComponent implements OnInit {
  userProfile: KeycloakProfile | null = null;
  currentLang = 'de';
  private readonly keycloak = inject(Keycloak);

  isMenuOpen = false;

  constructor(
    public translationService: TranslationService
  ) { }

  async ngOnInit() {
    if (this.keycloak.authenticated) {
      this.userProfile = await this.keycloak.loadUserProfile();
    }
    this.currentLang = this.translationService.currentLang();
  }

  get userName(): string {
    if (!this.userProfile) {
      return '';
    }
    const firstName = this.userProfile.firstName || '';
    const lastName = this.userProfile.lastName || '';
    return `${firstName} ${lastName}`.trim();
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
