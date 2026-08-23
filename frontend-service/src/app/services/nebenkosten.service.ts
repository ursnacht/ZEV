import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { NkAbrechnung, NkAbrechnungDetail } from '../models/nebenkosten.model';
import { getRuntimeConfig } from '../runtime-config';

/**
 * Zugriff auf die Nebenkostenabrechnungen (Specs/Nebenkosten/Abrechnung.md, FR-6).
 *
 * Alle Endpunkte antworten mit 403, wenn der Feature-Flag `NEBENKOSTENABRECHNUNG` für den
 * Mandanten aus ist — das prüft das Backend, nicht dieser Service.
 */
@Injectable({
  providedIn: 'root'
})
export class NebenkostenService {
  private apiUrl = `${getRuntimeConfig().apiBaseUrl}/api/nebenkosten/abrechnungen`;

  constructor(private http: HttpClient) {}

  getAllAbrechnungen(): Observable<NkAbrechnung[]> {
    return this.http.get<NkAbrechnung[]>(this.apiUrl);
  }

  /** Abrechnung samt Positionen, Mieterblöcken und berechneten Beträgen — ein Aufruf. */
  getAbrechnungDetail(id: number): Observable<NkAbrechnungDetail> {
    return this.http.get<NkAbrechnungDetail>(`${this.apiUrl}/${id}`);
  }

  /** Vorlage für eine neue Abrechnung, inklusive der vorgeschlagenen Anzahl Wohnungen. */
  getVorlage(): Observable<NkAbrechnungDetail> {
    return this.http.get<NkAbrechnungDetail>(`${this.apiUrl}/vorlage`);
  }

  createAbrechnung(abrechnung: NkAbrechnung): Observable<NkAbrechnung> {
    return this.http.post<NkAbrechnung>(this.apiUrl, abrechnung);
  }

  /** Speichert den vollständigen Stand; die Antwort enthält die Werte des Servers. */
  updateAbrechnung(id: number, detail: NkAbrechnungDetail): Observable<NkAbrechnungDetail> {
    return this.http.put<NkAbrechnungDetail>(`${this.apiUrl}/${id}`, detail);
  }

  /** Eigener Endpunkt: Das Flag ist als einziges Feld auch auf einer abgeschlossenen Abrechnung änderbar. */
  setAbgerechnet(id: number, abgerechnet: boolean): Observable<NkAbrechnung> {
    return this.http.patch<NkAbrechnung>(`${this.apiUrl}/${id}/abgerechnet`, { abgerechnet });
  }

  deleteAbrechnung(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
