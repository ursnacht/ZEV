/**
 * Gemeinsame Auswertung der Keycloak-Rollen für Permission-Prüfungen.
 *
 * Permissions sind Keycloak-Rollen, die von den Fachrollen (Composite Roles) gebündelt werden
 * und deshalb in den effektiven Realm-/Resource-Rollen des Tokens erscheinen
 * (siehe `Specs/Berechtigungen.md`).
 *
 * Diese Datei existiert, damit `AuthGuard` (Route) und `PermissionDirective` (Template) **dieselbe**
 * Logik verwenden. Zwei Kopien würden über kurz oder lang auseinanderlaufen — mit dem
 * unangenehmsten aller Ergebnisse: einem sichtbaren Menüpunkt, der beim Klick abgewiesen wird.
 */

/** Rollen des angemeldeten Benutzers, aufgeteilt nach Realm und Resource (Client). */
export interface GrantedRoles {
    realmRoles: string[];
    resourceRoles: Record<string, string[]>;
}

/**
 * Prüft, ob der Benutzer eine bestimmte Permission besitzt (Realm- oder Resource-Rolle).
 */
export function hasPermission(grantedRoles: GrantedRoles, permission: string): boolean {
    if (grantedRoles.realmRoles.includes(permission)) {
        return true;
    }
    return Object.values(grantedRoles.resourceRoles).some((roles) => roles.includes(permission));
}

/**
 * Prüft, ob der Benutzer **mindestens eine** der geforderten Permissions besitzt.
 *
 * Eine leere Liste gilt als „keine Anforderung" und liefert `true` — das entspricht dem
 * Verhalten des `AuthGuard` bei Routen ohne `data.permissions`.
 */
export function hasAnyPermission(grantedRoles: GrantedRoles, permissions: string[]): boolean {
    if (!permissions || permissions.length === 0) {
        return true;
    }
    return permissions.some((permission) => hasPermission(grantedRoles, permission));
}
