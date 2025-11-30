import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { inject } from '@angular/core';
import { AuthGuardData, createAuthGuard } from 'keycloak-angular';

const isAccessAllowed = async (
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

    // Get the required roles from the route data
    const requiredRoles = route.data['roles'];

    // Allow the user to proceed if no additional roles are required to access the route.
    if (!requiredRoles || requiredRoles.length === 0) {
        return true;
    }

    // Check if user has all required roles
    const hasRequiredRole = (role: string): boolean => {
        // Check in realm roles
        if (grantedRoles.realmRoles.includes(role)) {
            return true;
        }
        // Check in resource roles
        return Object.values(grantedRoles.resourceRoles).some((roles) => roles.includes(role));
    };

    const hasAllRequiredRoles = requiredRoles.every((role: string) => hasRequiredRole(role));

    if (hasAllRequiredRoles) {
        return true;
    }

    // User doesn't have required roles, redirect to forbidden or home
    return router.parseUrl('/');
};

export const AuthGuard = createAuthGuard<CanActivateFn>(isAccessAllowed);
