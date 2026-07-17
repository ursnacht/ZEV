import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { getRuntimeConfig } from '../runtime-config';

export interface VersionInfo {
  schemaVersion: string | null;
  buildTime: string | null;
}

@Injectable({
  providedIn: 'root'
})
export class VersionService {
  private apiUrl = `${getRuntimeConfig().apiBaseUrl}/api/version`;

  constructor(private http: HttpClient) {}

  getVersion(): Observable<VersionInfo> {
    return this.http.get<VersionInfo>(this.apiUrl);
  }
}
