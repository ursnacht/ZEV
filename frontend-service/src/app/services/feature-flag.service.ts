import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { FeatureFlagAdmin, FeatureFlagMap } from '../models/feature-flag.model';
import { getRuntimeConfig } from '../runtime-config';

/**
 * Hält die effektiven Feature-Flags des aktuellen Mandanten und stellt `isEnabled()` für
 * Templates/Guards/Direktiven bereit. Die Flags werden – analog zum TranslationService –
 * beim ersten Injizieren (nach Keycloak-Login) geladen. Der reaktive Signal-Zustand sorgt
 * dafür, dass Navigation und `*appFeature`-Direktive nach dem Laden automatisch aktualisieren.
 */
@Injectable({
  providedIn: 'root'
})
export class FeatureFlagService {
  private apiUrl = `${getRuntimeConfig().apiBaseUrl}/api/feature-flags`;

  /** Reaktiver Zustand der effektiven Flags (Key → aktiv/inaktiv). */
  private flags = signal<FeatureFlagMap>({});

  constructor(private http: HttpClient) {
    this.load().subscribe();
  }

  /**
   * Lädt die effektiven Flags des Mandanten. Bei Fehlern (Netzwerk) wird konservativ auf
   * "alle aus" zurückgefallen; das Observable schlägt nicht fehl.
   */
  load(): Observable<FeatureFlagMap> {
    return this.http.get<FeatureFlagMap>(this.apiUrl).pipe(
      tap(flags => this.flags.set(flags ?? {})),
      catchError(err => {
        console.error('Feature-Flags konnten nicht geladen werden - Fallback auf inaktiv', err);
        this.flags.set({});
        return of<FeatureFlagMap>({});
      })
    );
  }

  /**
   * Prüft (reaktiv), ob ein Flag aktiv ist. Liefert konservativ `false` für unbekannte oder
   * noch nicht geladene Flags.
   */
  isEnabled(key: string): boolean {
    return this.flags()[key] === true;
  }

  /** Admin-Sicht aller deklarierten Flags (Default, effektiv, Quelle). */
  getAdminFlags(): Observable<FeatureFlagAdmin[]> {
    return this.http.get<FeatureFlagAdmin[]>(`${this.apiUrl}/admin`);
  }

  /** Setzt eine mandantenspezifische Überschreibung und lädt die effektiven Flags neu. */
  setFlag(key: string, enabled: boolean): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/${key}`, { enabled }).pipe(
      tap(() => this.load().subscribe())
    );
  }

  /** Entfernt eine Überschreibung (Flag fällt auf Default zurück) und lädt neu. */
  resetFlag(key: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${key}`).pipe(
      tap(() => this.load().subscribe())
    );
  }
}
