import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = 'http://localhost:8090/api/auth';

  constructor(private http: HttpClient) {}

  /**
   * Notify the backend that the current user is logging out, so it can be logged server-side.
   * Must be called while the JWT is still valid (i.e. before redirecting to the Keycloak logout).
   */
  notifyLogout(): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/logout`, {});
  }
}
