import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Statistik } from '../models/statistik.model';

@Injectable({
  providedIn: 'root'
})
export class StatistikService {
  private apiUrl = 'http://localhost:8090/api/statistik';

  constructor(private http: HttpClient) { }

  getStatistik(von: string, bis: string): Observable<Statistik> {
    const params = new HttpParams()
      .set('von', von)
      .set('bis', bis);

    return this.http.get<Statistik>(this.apiUrl, { params });
  }

  getLetztesMessdatum(): Observable<string> {
    return this.http.get(`${this.apiUrl}/letztes-datum`, { responseType: 'text' });
  }

  exportPdf(von: string, bis: string, sprache: string = 'de'): Observable<Blob> {
    const params = new HttpParams()
      .set('von', von)
      .set('bis', bis)
      .set('sprache', sprache);

    return this.http.get(`${this.apiUrl}/export/pdf`, {
      params,
      responseType: 'blob'
    });
  }
}
