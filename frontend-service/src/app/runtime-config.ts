/**
 * Runtime configuration loaded from `assets/config.json` at application startup
 * (see main.ts). This avoids baking URLs into the build, so the same build/image
 * can be deployed to any environment by simply replacing config.json.
 *
 * The defaults below are used as a fallback if config.json cannot be loaded
 * (e.g. during unit tests) and point to the local development services.
 */
export interface RuntimeConfig {
  /** Base URL prefixed to backend API calls, e.g. 'http://localhost:8090' or '' for same-origin. */
  apiBaseUrl: string;
  keycloak: {
    url: string;
    realm: string;
    clientId: string;
  };
}

let runtimeConfig: RuntimeConfig = {
  apiBaseUrl: 'http://localhost:8090',
  keycloak: {
    url: 'http://localhost:9000',
    realm: 'zev',
    clientId: 'zev-frontend'
  }
};

export function setRuntimeConfig(config: RuntimeConfig): void {
  runtimeConfig = config;
}

export function getRuntimeConfig(): RuntimeConfig {
  return runtimeConfig;
}
