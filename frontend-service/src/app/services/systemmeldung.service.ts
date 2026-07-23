import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MeldungLevel, Systemmeldung, SystemmeldungSeite } from '../models/systemmeldung.model';
import { getRuntimeConfig } from '../runtime-config';

/**
 * Filter-/Paginierungs-/Sortier-Parameter für die Systemmeldungen-Liste.
 * `erledigt`: undefined = Alle, false = Offene, true = Erledigte.
 */
export interface SystemmeldungQuery {
  erledigt?: boolean;
  kategorie?: string;
  level?: MeldungLevel;
  page: number;
  size: number;
  sortSpalte: string;
  sortRichtung: 'ASC' | 'DESC';
}

@Injectable({
  providedIn: 'root'
})
export class SystemmeldungService {
  private apiUrl = `${getRuntimeConfig().apiBaseUrl}/api/systemmeldungen`;

  constructor(private http: HttpClient) {}

  getSeite(query: SystemmeldungQuery): Observable<SystemmeldungSeite> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sortSpalte', query.sortSpalte)
      .set('sortRichtung', query.sortRichtung);
    if (query.erledigt !== undefined) {
      params = params.set('erledigt', query.erledigt);
    }
    if (query.kategorie) {
      params = params.set('kategorie', query.kategorie);
    }
    if (query.level) {
      params = params.set('level', query.level);
    }
    return this.http.get<SystemmeldungSeite>(this.apiUrl, { params });
  }

  getKategorien(): Observable<string[]> {
    return this.http.get<string[]>(`${this.apiUrl}/kategorien`);
  }

  setErledigt(id: number, erledigt: boolean): Observable<Systemmeldung> {
    return this.http.put<Systemmeldung>(
      `${this.apiUrl}/${id}/erledigt`, null, { params: new HttpParams().set('erledigt', erledigt) });
  }

  deleteSystemmeldung(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
