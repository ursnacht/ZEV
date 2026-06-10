import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Tarif, ValidationResult } from '../models/tarif.model';
import { getRuntimeConfig } from '../runtime-config';

@Injectable({
  providedIn: 'root'
})
export class TarifService {
  private apiUrl = `${getRuntimeConfig().apiBaseUrl}/api/tarife`;

  constructor(private http: HttpClient) {}

  getAllTarife(): Observable<Tarif[]> {
    return this.http.get<Tarif[]>(this.apiUrl);
  }

  getTarifById(id: number): Observable<Tarif> {
    return this.http.get<Tarif>(`${this.apiUrl}/${id}`);
  }

  createTarif(tarif: Tarif): Observable<Tarif> {
    return this.http.post<Tarif>(this.apiUrl, tarif);
  }

  updateTarif(id: number, tarif: Tarif): Observable<Tarif> {
    return this.http.put<Tarif>(`${this.apiUrl}/${id}`, tarif);
  }

  deleteTarif(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  validateQuartale(): Observable<ValidationResult> {
    return this.http.post<ValidationResult>(`${this.apiUrl}/validate?modus=quartale`, {});
  }

  validateJahre(): Observable<ValidationResult> {
    return this.http.post<ValidationResult>(`${this.apiUrl}/validate?modus=jahre`, {});
  }
}
