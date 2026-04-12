import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Debitor } from '../models/debitor.model';

@Injectable({
  providedIn: 'root'
})
export class DebitorService {
  private apiUrl = 'http://localhost:8090/api/debitoren';

  constructor(private http: HttpClient) {}

  getDebitoren(von: string, bis: string): Observable<Debitor[]> {
    return this.http.get<Debitor[]>(this.apiUrl, { params: { von, bis } });
  }

  createDebitor(debitor: Debitor): Observable<Debitor> {
    return this.http.post<Debitor>(this.apiUrl, debitor);
  }

  updateDebitor(id: number, debitor: Debitor): Observable<Debitor> {
    return this.http.put<Debitor>(`${this.apiUrl}/${id}`, debitor);
  }

  deleteDebitor(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
