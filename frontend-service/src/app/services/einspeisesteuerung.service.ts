import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Prognosepunkt,
  Simulation,
  SimulationAnfrage,
  Steuerentscheid
} from '../models/einspeisesteuerung.model';
import { getRuntimeConfig } from '../runtime-config';

/**
 * Zugriff auf die Einspeisesteuerung (Specs/Einspeisesteuerung.md, FR-4).
 */
@Injectable({
  providedIn: 'root'
})
export class EinspeisesteuerungService {
  private apiUrl = `${getRuntimeConfig().apiBaseUrl}/api/einspeisesteuerung`;

  constructor(private http: HttpClient) {}

  /**
   * Entscheide eines Ortstages.
   *
   * @param datum Tag in Ortszeit (ISO `yyyy-MM-dd`)
   */
  getEntscheide(datum: string): Observable<Steuerentscheid[]> {
    return this.http.get<Steuerentscheid[]>(`${this.apiUrl}/entscheide?datum=${datum}`);
  }

  /**
   * Entscheide eines Ortstages, **nachgerechnet** mit einem abweichenden Schwellwert.
   *
   * Gleiche Form wie {@link getEntscheide}, aber gerechnet statt aufgezeichnet — **gespeichert
   * wird nichts**. Damit zeigt die Tagesansicht, *wann* ein anderer Schwellwert gesperrt hätte.
   *
   * @param datum        Tag in Ortszeit (ISO `yyyy-MM-dd`)
   * @param schwellwert  zu erprobender Schwellwert; darf negativ sein
   * @param speicherwert zu erprobender Speicherwert; `null` → Wert des Mandanten
   */
  getEntscheideSimuliert(datum: string, schwellwert: number,
                         speicherwert?: number | null,
                         mindestAbstand?: number | null): Observable<Steuerentscheid[]> {
    const params = new URLSearchParams({ datum, schwellwert: String(schwellwert) });
    if (speicherwert != null) {
      params.set('speicherwert', String(speicherwert));
    }
    if (mindestAbstand != null) {
      params.set('mindestAbstand', String(mindestAbstand));
    }
    return this.http.get<Steuerentscheid[]>(`${this.apiUrl}/entscheide/simuliert?${params}`);
  }

  /**
   * Rechnet die Regel mit abweichenden Schwellen nach — **verändert nichts**.
   */
  /**
   * Produktionsprognose eines Ortstages.
   *
   * Leer, wenn keine vorliegt — vor dem ersten Abruf oder ohne erfassten Standort. Das ist kein
   * Fehler, sondern der Normalzustand einer frisch eingerichteten Anlage.
   */
  getPrognose(datum: string): Observable<Prognosepunkt[]> {
    return this.http.get<Prognosepunkt[]>(`${this.apiUrl}/prognose?datum=${datum}`);
  }

  simuliere(anfrage: SimulationAnfrage): Observable<Simulation> {
    return this.http.post<Simulation>(`${this.apiUrl}/simulation`, anfrage);
  }
}
