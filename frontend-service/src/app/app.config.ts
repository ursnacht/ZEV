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
import { errorInterceptor } from './interceptors/error.interceptor';
import { RuntimeConfig } from './runtime-config';

function escapeRegExp(value: string): string {
    return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Build the application config from the runtime configuration (loaded from assets/config.json).
 */
export function buildAppConfig(config: RuntimeConfig): ApplicationConfig {
    // Attach the bearer token to backend API calls only. When apiBaseUrl is an absolute
    // URL match that origin; when it is empty (same-origin) match relative /api requests.
    const apiUrlPattern = config.apiBaseUrl
        ? new RegExp(`^${escapeRegExp(config.apiBaseUrl)}(/.*)?$`, 'i')
        : /^\/api(\/.*)?$/i;

    const urlCondition = createInterceptorCondition<IncludeBearerTokenCondition>({
        urlPattern: apiUrlPattern,
        bearerPrefix: 'Bearer'
    });

    return {
        providers: [
            provideKeycloak({
                config: {
                    url: config.keycloak.url,
                    realm: config.keycloak.realm,
                    clientId: config.keycloak.clientId
                },
                initOptions: {
                    onLoad: 'login-required',
                    checkLoginIframe: false,
                    pkceMethod: 'S256',
                    scope: 'openid profile organization'
                }
            }),
            {
                provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
                useValue: [urlCondition]
            },
            provideRouter(routes),
            provideHttpClient(withInterceptors([includeBearerTokenInterceptor, errorInterceptor]))
        ]
    };
}
