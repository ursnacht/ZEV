import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Tarif } from '../models/tarif.model';

@Injectable({
  providedIn: 'root'
})
export class TarifService {
  private apiUrl = 'http://localhost:8090/api/tarife';

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
}
