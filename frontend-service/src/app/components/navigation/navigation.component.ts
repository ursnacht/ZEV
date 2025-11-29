import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';

import { KeycloakService } from 'keycloak-angular';
import { KeycloakProfile } from 'keycloak-js';

@Component({
  selector: 'app-navigation',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './navigation.component.html',
  styleUrls: ['./navigation.component.css']
})
export class NavigationComponent implements OnInit {
  userProfile: KeycloakProfile | null = null;

  constructor(private keycloakService: KeycloakService) { }

  async ngOnInit() {
    if (this.keycloakService.isLoggedIn()) {
      this.userProfile = await this.keycloakService.loadUserProfile();
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

  logout() {
    this.keycloakService.logout();
  }
}
