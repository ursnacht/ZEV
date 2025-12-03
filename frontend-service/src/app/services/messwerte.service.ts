import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface CalculationResponse {
  status: string;
  algorithm?: string;
  processedTimestamps: number;
  processedRecords: number;
  dateFrom: string;
  dateTo: string;
  totalSolarProduced: number;
  totalDistributed: number;
  message?: string;
}

export interface MesswertData {
  zeit: string;
  total: number;
  zevCalculated: number;
}

@Injectable({
  providedIn: 'root'
})
export class MesswerteService {
  private apiUrl = 'http://localhost:8080/api/messwerte';

  constructor(private http: HttpClient) { }

  calculateDistribution(dateFrom: string, dateTo: string, algorithm: string = 'EQUAL_SHARE'): Observable<CalculationResponse> {
    const params = new HttpParams()
      .set('dateFrom', dateFrom)
      .set('dateTo', dateTo)
      .set('algorithm', algorithm);

    return this.http.post<CalculationResponse>(`${this.apiUrl}/calculate-distribution`, null, { params });
  }

  getMesswerteByEinheit(einheitId: number, dateFrom: string, dateTo: string): Observable<MesswertData[]> {
    const params = new HttpParams()
      .set('einheitId', einheitId.toString())
      .set('dateFrom', dateFrom)
      .set('dateTo', dateTo);

    return this.http.get<MesswertData[]>(`${this.apiUrl}/by-einheit`, { params });
  }
}
