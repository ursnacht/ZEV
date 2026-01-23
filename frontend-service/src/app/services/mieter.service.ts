import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Mieter } from '../models/mieter.model';

@Injectable({
  providedIn: 'root'
})
export class MieterService {
  private apiUrl = 'http://localhost:8090/api/mieter';

  constructor(private http: HttpClient) {}

  getAllMieter(): Observable<Mieter[]> {
    return this.http.get<Mieter[]>(this.apiUrl);
  }

  getMieterById(id: number): Observable<Mieter> {
    return this.http.get<Mieter>(`${this.apiUrl}/${id}`);
  }

  createMieter(mieter: Mieter): Observable<Mieter> {
    return this.http.post<Mieter>(this.apiUrl, mieter);
  }

  updateMieter(id: number, mieter: Mieter): Observable<Mieter> {
    return this.http.put<Mieter>(`${this.apiUrl}/${id}`, mieter);
  }

  deleteMieter(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
