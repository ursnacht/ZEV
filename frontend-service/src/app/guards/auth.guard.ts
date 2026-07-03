import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { inject } from '@angular/core';
import { AuthGuardData, createAuthGuard } from 'keycloak-angular';

// Exportiert für Unit-Tests (die Guard-Logik unabhängig vom keycloak-angular-Wrapper prüfbar).
export const isAccessAllowed = async (
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot,
    authData: AuthGuardData
): Promise<boolean | UrlTree> => {
    const { authenticated, grantedRoles } = authData;
    const router = inject(Router);

    // Force the user to log in if not authenticated
    if (!authenticated) {
        // Redirect will be handled by Keycloak's login-required option
        return false;
    }

    // Erforderliche Permissions aus den Route-Daten lesen.
    const requiredPermissions = route.data['permissions'];

    // Keine Permission gefordert -> Zugriff für jeden authentifizierten User erlaubt.
    if (!requiredPermissions || requiredPermissions.length === 0) {
        return true;
    }

    // Prüft, ob der User eine Permission besitzt. Permissions sind Keycloak-Rollen, die von den
    // Fachrollen (Composite Roles) gebündelt werden und daher in den effektiven Realm-/Resource-Rollen erscheinen.
    const hasPermission = (permission: string): boolean => {
        // Realm-Rollen prüfen
        if (grantedRoles.realmRoles.includes(permission)) {
            return true;
        }
        // Resource-Rollen prüfen
        return Object.values(grantedRoles.resourceRoles).some((roles) => roles.includes(permission));
    };

    // Zugriff wird gewährt, sobald der User EINE der geforderten Permissions besitzt.
    const hasAnyRequiredPermission = requiredPermissions.some((permission: string) => hasPermission(permission));

    if (hasAnyRequiredPermission) {
        return true;
    }

    // Fehlende Permission -> zurück auf die Startseite.
    return router.parseUrl('/');
};

export const AuthGuard = createAuthGuard<CanActivateFn>(isAccessAllowed);
