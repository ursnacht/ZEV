import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
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

  abfrage(request: DatenbankAbfrageRequest): Observable<DatenbankAbfrageResponse> {
    return this.http.post<DatenbankAbfrageResponse>(`${this.apiUrl}/abfrage`, request);
  }
}
