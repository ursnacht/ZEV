import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { map } from 'rxjs/operators';
import { FeatureFlagService } from '../services/feature-flag.service';

/**
 * Route-Guard: verweigert den Zugriff auf eine Route, wenn das in `route.data.featureFlag`
 * genannte Feature-Flag inaktiv ist, und leitet auf die Startseite um. Die Flags werden vor
 * der Entscheidung (neu) geladen (Backend-seitig gecacht), um Race-Conditions beim App-Start
 * zu vermeiden.
 *
 * Verwendung: `data: { featureFlag: 'MESSWERTE_UPLOAD' }`
 */
export const FeatureFlagGuard: CanActivateFn = (route) => {
  const featureFlagService = inject(FeatureFlagService);
  const router = inject(Router);

  const key = route.data['featureFlag'] as string | undefined;
  if (!key) {
    return true;
  }

  return featureFlagService.load().pipe(
    map(() => featureFlagService.isEnabled(key) ? true : router.parseUrl('/startseite'))
  );
};
