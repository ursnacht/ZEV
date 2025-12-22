import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import Keycloak from 'keycloak-js';

/**
 * HTTP Interceptor für die Behandlung von API-Fehlern.
 * Bei NO_ORGANIZATION Fehler wird der Benutzer ausgeloggt.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
    const keycloak = inject(Keycloak);

    return next(req).pipe(
        catchError((error: HttpErrorResponse) => {
            if (error.status === 403 && error.error?.error === 'NO_ORGANIZATION') {
                console.error('Keine Organisation zugewiesen - Benutzer wird ausgeloggt');

                // Alert anzeigen bevor ausgeloggt wird
                alert(error.error?.message || 'Keine Organisation zugewiesen. Bitte kontaktieren Sie den Administrator.');

                // Benutzer ausloggen
                keycloak.logout({ redirectUri: window.location.origin });
            }

            return throwError(() => error);
        })
    );
};
