import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';

import { KeycloakService } from 'keycloak-angular';
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

  constructor(
    private keycloakService: KeycloakService,
    public translationService: TranslationService
  ) { }

  async ngOnInit() {
    if (this.keycloakService.isLoggedIn()) {
      this.userProfile = await this.keycloakService.loadUserProfile();
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
    this.keycloakService.logout();
  }

  switchLanguage() {
    this.currentLang = this.currentLang === 'de' ? 'en' : 'de';
    this.translationService.setLanguage(this.currentLang as 'de' | 'en');
  }
}
