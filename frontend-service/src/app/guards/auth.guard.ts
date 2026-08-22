import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { inject } from '@angular/core';
import { AuthGuardData, createAuthGuard } from 'keycloak-angular';
import { hasAnyPermission } from '../utils/permissions';

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

    // Erforderliche Permissions aus den Route-Daten lesen. Die Auswertung der Rollen liegt in
    // utils/permissions.ts, damit die Permission-Direktive im Template exakt gleich prüft.
    // Keine Permission gefordert -> Zugriff für jeden authentifizierten User erlaubt;
    // sonst genügt EINE der geforderten Permissions.
    const requiredPermissions = route.data['permissions'] as string[] | undefined;

    if (hasAnyPermission(grantedRoles, requiredPermissions ?? [])) {
        return true;
    }

    // Fehlende Permission -> zurück auf die Startseite.
    return router.parseUrl('/');
};

export const AuthGuard = createAuthGuard<CanActivateFn>(isAccessAllowed);
