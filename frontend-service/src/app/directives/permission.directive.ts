import { Directive, TemplateRef, ViewContainerRef, inject, input } from '@angular/core';
import Keycloak from 'keycloak-js';
import { GrantedRoles, hasAnyPermission } from '../utils/permissions';

/**
 * Struktur-Direktive: rendert den Inhalt nur, wenn der angemeldete Benutzer die angegebene
 * Permission besitzt.
 *
 * Verwendung: `<li *appPermission="'nebenkosten:manage'">...</li>`
 * Mehrere Permissions sind zulässig; eine davon genügt (gleiche Regel wie im `AuthGuard`).
 *
 * <p>Die Rollen stammen aus dem Keycloak-Token (`realmAccess` / `resourceAccess`) — derselben
 * Quelle, aus der keycloak-angular die `AuthGuardData.grantedRoles` bildet. Die Auswertung liegt
 * in `utils/permissions.ts` und wird mit dem `AuthGuard` geteilt, damit Menü und Route nie
 * unterschiedlich entscheiden.
 *
 * <p>Bewusst **nicht** reaktiv (anders als `appFeature`): Die Rollen eines Benutzers ändern sich
 * innerhalb einer Sitzung nicht — eine neue Rollenzuweisung wirkt erst nach erneutem Anmelden,
 * weil sie im Token steht.
 */
@Directive({
  selector: '[appPermission]',
  standalone: true
})
export class PermissionDirective {
  /** Eine Permission oder mehrere, von denen eine genügt. */
  readonly appPermission = input.required<string | string[]>();

  private readonly templateRef = inject(TemplateRef<unknown>);
  private readonly viewContainer = inject(ViewContainerRef);
  private readonly keycloak = inject(Keycloak);

  ngOnInit(): void {
    const geforderte = this.appPermission();
    const permissions = Array.isArray(geforderte) ? geforderte : [geforderte];

    if (hasAnyPermission(this.grantedRoles(), permissions)) {
      this.viewContainer.createEmbeddedView(this.templateRef);
    }
  }

  /** Rollen aus dem Token in die vom Guard genutzte Form bringen. */
  private grantedRoles(): GrantedRoles {
    const resourceRoles: Record<string, string[]> = {};
    for (const [client, access] of Object.entries(this.keycloak.resourceAccess ?? {})) {
      resourceRoles[client] = access.roles ?? [];
    }
    return {
      realmRoles: this.keycloak.realmAccess?.roles ?? [],
      resourceRoles
    };
  }
}
