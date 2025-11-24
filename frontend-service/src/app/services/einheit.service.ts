import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Einheit } from '../models/einheit.model';

@Injectable({
  providedIn: 'root'
})
export class EinheitService {
  private apiUrl = 'http://localhost:8080/api/einheit';

  constructor(private http: HttpClient) {}

  getAllEinheiten(): Observable<Einheit[]> {
    return this.http.get<Einheit[]>(this.apiUrl);
  }

  getEinheitById(id: number): Observable<Einheit> {
    return this.http.get<Einheit>(`${this.apiUrl}/${id}`);
  }

  createEinheit(einheit: Einheit): Observable<Einheit> {
    return this.http.post<Einheit>(this.apiUrl, einheit);
  }

  updateEinheit(id: number, einheit: Einheit): Observable<Einheit> {
    return this.http.put<Einheit>(`${this.apiUrl}/${id}`, einheit);
  }

  deleteEinheit(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
