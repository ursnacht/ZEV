import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface CalculationResponse {
  status: string;
  processedTimestamps: number;
  processedRecords: number;
  dateFrom: string;
  dateTo: string;
  totalSolarProduced: number;
  totalDistributed: number;
  message?: string;
}

@Injectable({
  providedIn: 'root'
})
export class MesswerteService {
  private apiUrl = 'http://localhost:8080/api/messwerte';

  constructor(private http: HttpClient) {}

  calculateDistribution(dateFrom: string, dateTo: string): Observable<CalculationResponse> {
    const params = new HttpParams()
      .set('dateFrom', dateFrom)
      .set('dateTo', dateTo);

    return this.http.post<CalculationResponse>(`${this.apiUrl}/calculate-distribution`, null, { params });
  }
}
