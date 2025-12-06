import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
    provideKeycloak,
    createInterceptorCondition,
    IncludeBearerTokenCondition,
    includeBearerTokenInterceptor,
    INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG
} from 'keycloak-angular';
import { routes } from './app.routes';

const urlCondition = createInterceptorCondition<IncludeBearerTokenCondition>({
    urlPattern: /^(http:\/\/localhost:8090)(\/.*)?$/i,
    bearerPrefix: 'Bearer'
});

export const appConfig: ApplicationConfig = {
    providers: [
        provideKeycloak({
            config: {
                url: 'http://localhost:9000',
                realm: 'zev',
                clientId: 'zev-frontend'
            },
            initOptions: {
                onLoad: 'login-required',
                checkLoginIframe: false
            }
        }),
        {
            provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
            useValue: [urlCondition]
        },
        provideRouter(routes),
        provideHttpClient(withInterceptors([includeBearerTokenInterceptor]))
    ]
};
