import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Tarifposition } from '../models/tarifposition.model';
import { getRuntimeConfig } from '../runtime-config';

@Injectable({
  providedIn: 'root'
})
export class TarifpositionService {
  private apiUrl = `${getRuntimeConfig().apiBaseUrl}/api/tarifpositionen`;

  constructor(private http: HttpClient) {}

  getByEinheit(einheitId: number): Observable<Tarifposition[]> {
    return this.http.get<Tarifposition[]>(this.apiUrl, { params: { einheitId } });
  }

  createTarifposition(position: Tarifposition): Observable<Tarifposition> {
    return this.http.post<Tarifposition>(this.apiUrl, position);
  }

  updateTarifposition(id: number, position: Tarifposition): Observable<Tarifposition> {
    return this.http.put<Tarifposition>(`${this.apiUrl}/${id}`, position);
  }

  deleteTarifposition(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
