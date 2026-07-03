import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AuthGuardData } from 'keycloak-angular';
import { createSpyObj, SpyObj } from '../../testing/spy';
import { isAccessAllowed } from './auth.guard';

describe('AuthGuard (isAccessAllowed)', () => {
  let routerSpy: SpyObj<Router>;
  const rootUrlTree = { toString: () => '/' } as UrlTree;
  const stateStub = {} as RouterStateSnapshot;

  const routeWith = (data: Record<string, unknown>) =>
    ({ data } as unknown as ActivatedRouteSnapshot);

  const authData = (opts: {
    authenticated: boolean;
    realmRoles?: string[];
    resourceRoles?: Record<string, string[]>;
  }): AuthGuardData =>
    ({
      authenticated: opts.authenticated,
      grantedRoles: {
        realmRoles: opts.realmRoles ?? [],
        resourceRoles: opts.resourceRoles ?? {}
      }
    } as unknown as AuthGuardData);

  const run = (route: ActivatedRouteSnapshot, data: AuthGuardData) =>
    TestBed.runInInjectionContext(() => isAccessAllowed(route, stateStub, data));

  beforeEach(() => {
    routerSpy = createSpyObj<Router>('Router', ['parseUrl']);
    routerSpy.parseUrl.mockReturnValue(rootUrlTree);

    TestBed.configureTestingModule({
      providers: [{ provide: Router, useValue: routerSpy }]
    });
  });

  it('should deny (false) when the user is not authenticated', async () => {
    const result = await run(
      routeWith({ permissions: ['einstellungen:write'] }),
      authData({ authenticated: false })
    );

    expect(result).toBe(false);
  });

  it('should allow when the route requires no permissions', async () => {
    const result = await run(routeWith({}), authData({ authenticated: true }));

    expect(result).toBe(true);
  });

  it('should allow when the required permissions array is empty', async () => {
    const result = await run(routeWith({ permissions: [] }), authData({ authenticated: true }));

    expect(result).toBe(true);
  });

  it('should allow when the user has the required permission (realm role)', async () => {
    const result = await run(
      routeWith({ permissions: ['einstellungen:write'] }),
      authData({ authenticated: true, realmRoles: ['einstellungen:write', 'einheit:read'] })
    );

    expect(result).toBe(true);
    expect(routerSpy.parseUrl).not.toHaveBeenCalled();
  });

  it('should allow when the user has one of several required permissions', async () => {
    const result = await run(
      routeWith({ permissions: ['featureflags:manage', 'einstellungen:write'] }),
      authData({ authenticated: true, realmRoles: ['einstellungen:write'] })
    );

    expect(result).toBe(true);
  });

  it('should allow when the permission is granted via a resource (client) role', async () => {
    const result = await run(
      routeWith({ permissions: ['tarife:manage'] }),
      authData({ authenticated: true, resourceRoles: { 'zev-frontend': ['tarife:manage'] } })
    );

    expect(result).toBe(true);
  });

  it('should redirect to / when the required permission is missing', async () => {
    // z.B. org_admin (einstellungen:write) auf einer Route, die featureflags:manage verlangt
    const result = await run(
      routeWith({ permissions: ['featureflags:manage'] }),
      authData({ authenticated: true, realmRoles: ['einstellungen:write'] })
    );

    expect(routerSpy.parseUrl).toHaveBeenCalledWith('/');
    expect(result).toBe(rootUrlTree);
  });
});
