import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { DatenbankAbfrageRequest, DatenbankAbfrageResponse } from '../models/datenbank.model';
import { getRuntimeConfig } from '../runtime-config';

@Injectable({
  providedIn: 'root'
})
export class DatenbankService {
  private apiUrl = `${getRuntimeConfig().apiBaseUrl}/api/datenbank`;

  constructor(private http: HttpClient) {}

  getTabellen(): Observable<string[]> {
    return this.http.get<string[]>(`${this.apiUrl}/tabellen`);
  }

  /**
   * Liefert den Standard-Filter für eine Tabelle. Hat die Tabelle eine {@code org_id}-Spalte,
   * enthält die Antwort {@code org_id = <orgId>} des eingeloggten Benutzers, sonst einen leeren String.
   */
  getStandardFilter(tabelle: string): Observable<string> {
    return this.http
      .get<{ where: string }>(`${this.apiUrl}/standard-filter`, { params: { tabelle } })
      .pipe(map((response) => response.where));
  }

  abfrage(request: DatenbankAbfrageRequest): Observable<DatenbankAbfrageResponse> {
    return this.http.post<DatenbankAbfrageResponse>(`${this.apiUrl}/abfrage`, request);
  }
}
