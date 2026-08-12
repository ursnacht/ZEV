import { Injectable } from '@angular/core';

/**
 * Lokale Historie der zuletzt verwendeten WHERE-Filter der Datenbank-Ansicht.
 *
 * Die Historie wird **pro Tabelle** geführt und ausschliesslich im Browser
 * (`localStorage`) gehalten – keine Server-Persistenz, keine Übertragung an das Backend.
 * Es werden maximal {@link MAX_EINTRAEGE} Einträge je Tabelle gespeichert (neuester zuerst).
 */
@Injectable({ providedIn: 'root' })
export class DatenbankFilterHistorieService {
  /** Maximale Anzahl gespeicherter Filter je Tabelle. */
  static readonly MAX_EINTRAEGE = 20;

  private readonly STORAGE_KEY = 'zev-datenbank-filter-historie';

  /** Liefert die Historie einer Tabelle (neuester Eintrag zuerst); leer, falls keine vorhanden. */
  getHistorie(tabelle: string): string[] {
    if (!tabelle) {
      return [];
    }
    return this.loadFromStorage()[tabelle] ?? [];
  }

  /**
   * Nimmt einen Filter in die Historie der Tabelle auf (neuester zuerst).
   * Leere Filter werden ignoriert, Duplikate nach vorne verschoben (kein Doppeleintrag),
   * die Liste auf {@link MAX_EINTRAEGE} gekürzt.
   *
   * @returns die aktualisierte Historie der Tabelle
   */
  addFilter(tabelle: string, filter: string): string[] {
    const eintrag = filter?.trim() ?? '';
    if (!tabelle || !eintrag) {
      return this.getHistorie(tabelle);
    }
    const alle = this.loadFromStorage();
    const bisher = alle[tabelle] ?? [];
    const neu = [eintrag, ...bisher.filter((f) => f !== eintrag)]
      .slice(0, DatenbankFilterHistorieService.MAX_EINTRAEGE);
    alle[tabelle] = neu;
    this.saveToStorage(alle);
    return neu;
  }

  private loadFromStorage(): Record<string, string[]> {
    try {
      const raw = localStorage.getItem(this.STORAGE_KEY);
      if (!raw) {
        return {};
      }
      const parsed = JSON.parse(raw);
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
        return {};
      }
      // Nur wohlgeformte Einträge übernehmen (defensiv gegen manipulierten/veralteten Storage)
      const bereinigt: Record<string, string[]> = {};
      for (const [tabelle, filter] of Object.entries(parsed as Record<string, unknown>)) {
        if (Array.isArray(filter)) {
          bereinigt[tabelle] = filter
            .filter((f): f is string => typeof f === 'string' && f.trim().length > 0)
            .slice(0, DatenbankFilterHistorieService.MAX_EINTRAEGE);
        }
      }
      return bereinigt;
    } catch {
      // localStorage nicht verfügbar oder Inhalt defekt – Historie ist optional
      return {};
    }
  }

  private saveToStorage(historie: Record<string, string[]>): void {
    try {
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(historie));
    } catch {
      // localStorage nicht verfügbar / Quota überschritten – ignorieren
    }
  }
}
