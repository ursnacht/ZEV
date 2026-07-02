import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Observable, of } from 'rxjs';
import { createSpyObj, SpyObj } from '../../testing/spy';
import { FeatureFlagGuard } from './feature-flag.guard';
import { FeatureFlagService } from '../services/feature-flag.service';

describe('FeatureFlagGuard', () => {
  let featureFlagServiceSpy: SpyObj<FeatureFlagService>;
  let routerSpy: SpyObj<Router>;
  const startseiteUrlTree = { toString: () => '/startseite' } as UrlTree;
  const stateStub = {} as RouterStateSnapshot;

  const executeGuard = (route: ActivatedRouteSnapshot) =>
    TestBed.runInInjectionContext(() => FeatureFlagGuard(route, stateStub));

  const routeWith = (data: Record<string, unknown>) =>
    ({ data } as unknown as ActivatedRouteSnapshot);

  beforeEach(() => {
    featureFlagServiceSpy = createSpyObj<FeatureFlagService>('FeatureFlagService', [
      'isEnabled', 'load', 'getAdminFlags', 'setFlag', 'resetFlag'
    ]);
    routerSpy = createSpyObj<Router>('Router', ['parseUrl']);
    routerSpy.parseUrl.mockReturnValue(startseiteUrlTree);

    TestBed.configureTestingModule({
      providers: [
        { provide: FeatureFlagService, useValue: featureFlagServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });
  });

  it('should allow activation when no featureFlag is configured on the route', () => {
    const result = executeGuard(routeWith({}));

    expect(result).toBe(true);
    expect(featureFlagServiceSpy.load).not.toHaveBeenCalled();
  });

  it('should allow activation when the configured flag is active', () => {
    featureFlagServiceSpy.load.mockReturnValue(of({ MESSWERTE_UPLOAD: true }));
    featureFlagServiceSpy.isEnabled.mockReturnValue(true);

    const result = executeGuard(routeWith({ featureFlag: 'MESSWERTE_UPLOAD' })) as Observable<
      boolean | UrlTree
    >;

    let value: boolean | UrlTree | undefined;
    result.subscribe(v => (value = v));

    expect(featureFlagServiceSpy.load).toHaveBeenCalled();
    expect(featureFlagServiceSpy.isEnabled).toHaveBeenCalledWith('MESSWERTE_UPLOAD');
    expect(value).toBe(true);
    expect(routerSpy.parseUrl).not.toHaveBeenCalled();
  });

  it('should redirect to /startseite when the configured flag is inactive', () => {
    featureFlagServiceSpy.load.mockReturnValue(of({ MESSWERTE_UPLOAD: false }));
    featureFlagServiceSpy.isEnabled.mockReturnValue(false);

    const result = executeGuard(routeWith({ featureFlag: 'MESSWERTE_UPLOAD' })) as Observable<
      boolean | UrlTree
    >;

    let value: boolean | UrlTree | undefined;
    result.subscribe(v => (value = v));

    expect(routerSpy.parseUrl).toHaveBeenCalledWith('/startseite');
    expect(value).toBe(startseiteUrlTree);
  });
});
