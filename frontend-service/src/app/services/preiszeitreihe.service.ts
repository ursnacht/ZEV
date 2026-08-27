import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PreiszeitreiheDownload, PreiszeitreihePunkt } from '../models/preiszeitreihe.model';
import { getRuntimeConfig } from '../runtime-config';

/**
 * Zugriff auf die Preiszeitreihe (Specs/Preiszeitreihe.md, FR-4).
 */
@Injectable({
  providedIn: 'root'
})
export class PreiszeitreiheService {
  private apiUrl = `${getRuntimeConfig().apiBaseUrl}/api/preiszeitreihe`;

  constructor(private http: HttpClient) {}

  /**
   * Werte einer Spanne.
   *
   * @param von erster Tag (ISO `yyyy-MM-dd`, einschliesslich)
   * @param bis letzter Tag (ISO `yyyy-MM-dd`, einschliesslich)
   */
  getPunkte(von: string, bis: string): Observable<PreiszeitreihePunkt[]> {
    return this.http.get<PreiszeitreihePunkt[]>(`${this.apiUrl}?von=${von}&bis=${bis}`);
  }

  /** Holt die Preise jetzt bei der Quelle. */
  download(): Observable<PreiszeitreiheDownload> {
    return this.http.post<PreiszeitreiheDownload>(`${this.apiUrl}/download`, {});
  }
}
