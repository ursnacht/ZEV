import { provideZoneChangeDetection } from "@angular/core";
import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { buildAppConfig } from './app/app.config';
import { RuntimeConfig, setRuntimeConfig, getRuntimeConfig } from './app/runtime-config';

/**
 * Load runtime configuration from assets/config.json before bootstrapping the app.
 * Falls back to the built-in defaults if the file is missing or invalid.
 */
async function loadRuntimeConfig(): Promise<void> {
  try {
    const response = await fetch('assets/config.json', { cache: 'no-cache' });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    setRuntimeConfig(await response.json() as RuntimeConfig);
  } catch (err) {
    console.error('Could not load assets/config.json - falling back to default config', err);
  }
}

loadRuntimeConfig().then(() => {
  const appConfig = buildAppConfig(getRuntimeConfig());
  bootstrapApplication(AppComponent, {
    ...appConfig,
    providers: [provideZoneChangeDetection(), ...appConfig.providers]
  }).catch((err) => console.error(err));
});
