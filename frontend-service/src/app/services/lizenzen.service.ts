import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Lizenz } from '../models/lizenzen.model';
import { getRuntimeConfig } from '../runtime-config';

@Injectable({
  providedIn: 'root'
})
export class LizenzenService {
  private apiUrl = `${getRuntimeConfig().apiBaseUrl}/api/lizenzen`;
  private assetsUrl = 'assets/frontend-licenses.json';

  constructor(private http: HttpClient) {}

  getBackendLizenzen(): Observable<Lizenz[]> {
    return this.http.get<Lizenz[]>(this.apiUrl);
  }

  getFrontendLizenzen(): Observable<Lizenz[]> {
    return this.http.get<Lizenz[]>(this.assetsUrl);
  }
}
