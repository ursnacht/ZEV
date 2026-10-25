import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { MesspunktEmpfang, MesspunktEmpfangMeldung } from '../models/messpunkt-empfang.model';
import { getRuntimeConfig } from '../runtime-config';

@Injectable({
  providedIn: 'root'
})
export class MesspunktEmpfangService {
  private apiUrl = `${getRuntimeConfig().apiBaseUrl}/api/messpunkt-empfang`;

  constructor(private http: HttpClient) {}

  getAllMesspunktEmpfang(): Observable<MesspunktEmpfang[]> {
    return this.http.get<MesspunktEmpfang[]>(this.apiUrl);
  }

  getLetzteMeldung(id: number): Observable<MesspunktEmpfangMeldung> {
    return this.http.get<MesspunktEmpfangMeldung>(`${this.apiUrl}/${id}/letzte-meldung`);
  }

  deleteMesspunktEmpfang(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
