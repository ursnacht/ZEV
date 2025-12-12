import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface GeneratedRechnung {
  einheitId: number;
  einheitName: string;
  mietername: string;
  endBetrag: number;
  filename: string;
  downloadKey: string;
}

export interface GenerateResponse {
  rechnungen: GeneratedRechnung[];
  count: number;
}

export interface GenerateRequest {
  von: string;
  bis: string;
  einheitIds: number[];
  sprache: string;
}

@Injectable({
  providedIn: 'root'
})
export class RechnungService {
  private apiUrl = 'http://localhost:8090/api/rechnungen';

  constructor(private http: HttpClient) {}

  /**
   * Generate invoices for the selected units and time period.
   */
  generateRechnungen(request: GenerateRequest): Observable<GenerateResponse> {
    return this.http.post<GenerateResponse>(`${this.apiUrl}/generate`, request);
  }

  /**
   * Download a generated invoice by its download key.
   */
  downloadRechnung(downloadKey: string, filename: string): void {
    this.http.get(`${this.apiUrl}/download/${downloadKey}`, {
      responseType: 'blob'
    }).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        link.click();
        window.URL.revokeObjectURL(url);
      },
      error: (error) => {
        console.error('Download failed:', error);
      }
    });
  }
}
