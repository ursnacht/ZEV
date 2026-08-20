import { HttpErrorResponse, HttpEvent, HttpHandlerFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import Keycloak from 'keycloak-js';
import { firstValueFrom, Observable, of, throwError } from 'rxjs';
import { createSpyObj, SpyObj } from '../../testing/spy';
import { errorInterceptor } from './error.interceptor';

/**
 * Unit-Tests für {@link errorInterceptor}.
 *
 * <p>Der Interceptor hängt an jedem HTTP-Aufruf der Anwendung und hat genau eine, aber
 * folgenreiche Aufgabe: Bei `403 NO_ORGANIZATION` meldet er den Benutzer ab. Beides ist
 * gefährlich, wenn es kippt — eine zu weite Bedingung wirft Benutzer bei jedem beliebigen
 * 403 aus der Anwendung, eine zu enge lässt sie in einer Sitzung ohne Mandant hängen.
 *
 * <p>Die Tests pinnen deshalb bewusst beide Richtungen: den Abmeldefall und die Fälle,
 * in denen gerade nicht abgemeldet werden darf. Zusätzlich wird geprüft, dass der Fehler
 * in jedem Fall weitergereicht wird — verschluckt er ihn, sähe der Aufrufer einen Erfolg.
 */
describe('errorInterceptor', () => {
    let keycloakSpy: SpyObj<Keycloak>;
    let alertSpy: ReturnType<typeof vi.spyOn>;

    const request = new HttpRequest('GET', '/api/tarife');

    /** Fehlerantwort des Backends, wenn dem Benutzer keine Organisation zugewiesen ist. */
    const noOrganizationError = (message?: string) =>
        new HttpErrorResponse({
            status: 403,
            statusText: 'Forbidden',
            url: '/api/tarife',
            error: message ? { error: 'NO_ORGANIZATION', message } : { error: 'NO_ORGANIZATION' }
        });

    const nextFailsWith = (error: HttpErrorResponse): HttpHandlerFn => () => throwError(() => error);

    const run = (next: HttpHandlerFn): Observable<HttpEvent<unknown>> =>
        TestBed.runInInjectionContext(() => errorInterceptor(request, next));

    /**
     * Erwartet, dass der Strom mit einem Fehler endet, und gibt diesen zurück.
     * Ein `try/catch` allein genügt nicht: Es würde auch eine eigene Zusicherung schlucken.
     */
    async function abgewiesenMit(strom: Observable<unknown>): Promise<unknown> {
        let gefangen: unknown;
        let erfolgreich = false;
        try {
            await firstValueFrom(strom);
            erfolgreich = true;
        } catch (error) {
            gefangen = error;
        }
        expect(erfolgreich, 'Der Interceptor muss den Fehler weiterreichen').toBe(false);
        return gefangen;
    }

    beforeEach(() => {
        keycloakSpy = createSpyObj<Keycloak>('Keycloak', ['logout']);
        alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
        // Der Interceptor protokolliert den Abmeldegrund - im Testlauf nur Rauschen
        vi.spyOn(console, 'error').mockImplementation(() => {});

        TestBed.configureTestingModule({
            providers: [{ provide: Keycloak, useValue: keycloakSpy }]
        });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('erfolgreiche Antworten', () => {
        it('should pass a successful response through untouched', async () => {
            const antwort = new HttpResponse({ status: 200, body: { id: 1 } });

            const ergebnis = await firstValueFrom(run(() => of(antwort)));

            expect(ergebnis).toBe(antwort);
            expect(keycloakSpy.logout).not.toHaveBeenCalled();
            expect(alertSpy).not.toHaveBeenCalled();
        });

        it('should forward the request unchanged to the next handler', async () => {
            let gesehen: HttpRequest<unknown> | undefined;
            const next: HttpHandlerFn = (req) => {
                gesehen = req;
                return of(new HttpResponse({ status: 200 }));
            };

            await firstValueFrom(run(next));

            expect(gesehen).toBe(request);
        });
    });

    describe('403 NO_ORGANIZATION', () => {
        it('should log the user out', async () => {
            await abgewiesenMit(run(nextFailsWith(noOrganizationError())));

            expect(keycloakSpy.logout).toHaveBeenCalledTimes(1);
            expect(keycloakSpy.logout).toHaveBeenCalledWith({ redirectUri: window.location.origin });
        });

        it('should show the message sent by the backend', async () => {
            await abgewiesenMit(run(nextFailsWith(noOrganizationError('Bitte den Administrator kontaktieren'))));

            expect(alertSpy).toHaveBeenCalledWith('Bitte den Administrator kontaktieren');
        });

        it('should show a fallback message when the backend sends none', async () => {
            await abgewiesenMit(run(nextFailsWith(noOrganizationError())));

            expect(alertSpy).toHaveBeenCalledTimes(1);
            expect(alertSpy.mock.calls[0][0]).toContain('Keine Organisation zugewiesen');
        });

        it('should still rethrow the original error', async () => {
            const fehler = noOrganizationError();

            const gefangen = await abgewiesenMit(run(nextFailsWith(fehler)));

            // Dieselbe Instanz: Der Aufrufer muss Status und Rumpf unveraendert auswerten koennen
            expect(gefangen).toBe(fehler);
        });
    });

    describe('Fehler, die keine Abmeldung ausloesen duerfen', () => {
        it('should not log out on a 403 with a different error code', async () => {
            // Ein fehlendes Recht ist der Normalfall - der Benutzer bleibt angemeldet
            const fehler = new HttpErrorResponse({
                status: 403,
                error: { error: 'ACCESS_DENIED' }
            });

            const gefangen = await abgewiesenMit(run(nextFailsWith(fehler)));

            expect(gefangen).toBe(fehler);
            expect(keycloakSpy.logout).not.toHaveBeenCalled();
            expect(alertSpy).not.toHaveBeenCalled();
        });

        it('should not log out when NO_ORGANIZATION arrives with another status', async () => {
            // Der Status gehoert zur Bedingung: Ein 401 fuehrt zur Anmeldung, nicht zur Abmeldung
            const fehler = new HttpErrorResponse({
                status: 401,
                error: { error: 'NO_ORGANIZATION' }
            });

            await abgewiesenMit(run(nextFailsWith(fehler)));

            expect(keycloakSpy.logout).not.toHaveBeenCalled();
        });

        it('should not log out when the error body is empty', async () => {
            // Kein Rumpf, kein Absturz im optionalen Zugriff `error.error?.error`
            const fehler = new HttpErrorResponse({ status: 403, error: null });

            const gefangen = await abgewiesenMit(run(nextFailsWith(fehler)));

            expect(gefangen).toBe(fehler);
            expect(keycloakSpy.logout).not.toHaveBeenCalled();
        });

        it('should not log out when the error body is a plain string', async () => {
            // Fehlermeldungen des Backends kommen teils als reiner Text
            const fehler = new HttpErrorResponse({ status: 403, error: 'NO_ORGANIZATION' });

            await abgewiesenMit(run(nextFailsWith(fehler)));

            expect(keycloakSpy.logout).not.toHaveBeenCalled();
        });

        it('should not log out on a network error', async () => {
            // Netzwerkfehler haben Status 0 - der Benutzer soll seine Sitzung behalten
            const fehler = new HttpErrorResponse({ status: 0, statusText: 'Unknown Error' });

            const gefangen = await abgewiesenMit(run(nextFailsWith(fehler)));

            expect(gefangen).toBe(fehler);
            expect(keycloakSpy.logout).not.toHaveBeenCalled();
            expect(alertSpy).not.toHaveBeenCalled();
        });

        it('should not log out on a server error', async () => {
            const fehler = new HttpErrorResponse({ status: 500, error: { error: 'INTERNAL_ERROR' } });

            await abgewiesenMit(run(nextFailsWith(fehler)));

            expect(keycloakSpy.logout).not.toHaveBeenCalled();
        });
    });
});
