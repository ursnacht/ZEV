import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Einstellungen } from '../models/einstellungen.model';

@Injectable({
  providedIn: 'root'
})
export class EinstellungenService {
  private apiUrl = 'http://localhost:8090/api/einstellungen';

  constructor(private http: HttpClient) {}

  /**
   * Get current settings for the tenant.
   * Returns empty response (204) if no settings exist yet.
   */
  getEinstellungen(): Observable<Einstellungen | null> {
    return this.http.get<Einstellungen | null>(this.apiUrl);
  }

  /**
   * Save or update settings for the tenant.
   */
  saveEinstellungen(einstellungen: Einstellungen): Observable<Einstellungen> {
    return this.http.put<Einstellungen>(this.apiUrl, einstellungen);
  }
}
