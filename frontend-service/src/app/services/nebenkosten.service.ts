import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { NkAbrechnung, NkAbrechnungDetail, NkRechnungLauf } from '../models/nebenkosten.model';
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

  /**
    * Kopiert eine Abrechnung samt Positionen, Mengen, Zusatzpositionen, Akonto und Personenzahlen.
    *
    * Die Bezeichnung geht als Parameter mit: Der Zusatz „(Kopie)" ist ein Anzeigetext und gehört
    * damit hierhin, nicht ins Backend. Die Antwort ist die neue Abrechnung samt Berechnung.
    */
  kopiereAbrechnung(id: number, bezeichnung: string): Observable<NkAbrechnungDetail> {
    return this.http.post<NkAbrechnungDetail>(`${this.apiUrl}/${id}/kopie`, null,
      { params: { bezeichnung } });
  }

  deleteAbrechnung(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  /**
   * Erzeugt je Mieter eine Rechnung und bucht die Forderungen
   * (Specs/Nebenkosten/RechnungenGenerieren.md, FR-6).
   *
   * Eigener Endpunkt im NK-Bereich, nicht `POST /api/rechnungen/generate`: Dessen Antwort wäre
   * sonst je Rechnungsart eine andere geworden.
   */
  erzeugeRechnungen(abrechnungId: number, sprache?: string): Observable<NkRechnungLauf> {
    return this.http.post<NkRechnungLauf>(`${this.apiUrl}/${abrechnungId}/rechnungen`,
      { sprache: sprache ?? 'de' });
  }

  /**
   * Holt das erzeugte PDF eines Mieters.
   *
   * Bewusst über `rechnung.service.ts` hinweg: Die Route liegt im NK-Bereich, damit die
   * Rechnungsart strukturell feststeht und der Feature-Flag greift.
   *
   * Liefert das Blob, statt den Download selbst auszulösen — der Aufrufer muss den Fehlerfall
   * behandeln: Nach 30 Minuten ist das PDF weg und der Server antwortet mit 404.
   */
  ladeRechnungPdf(abrechnungId: number, mieterId: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/${abrechnungId}/rechnungen/${mieterId}/pdf`,
      { responseType: 'blob' });
  }
}
