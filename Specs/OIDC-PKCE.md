# Benutzerauthentisierung mit OIDC und PKCE

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Die Authentisierung wird explizit auf OpenID Connect (OIDC) mit dem Authorization Code Flow + PKCE (Proof Key for Code Exchange, S256) umgestellt und serverseitig erzwungen.
* **Warum machen wir das:** Der aktuelle Keycloak-Client `zev-frontend` erzwingt PKCE nicht serverseitig. Ohne serverseitiges Enforcement kann ein Angreifer die Authorization Code Request ohne `code_challenge` senden und so den PKCE-Schutz aushebeln (Authorization Code Interception Attack). OAuth 2.1 und die aktuellen OIDC Best Practices (BCP 212) schreiben PKCE für alle Public Clients (SPAs) verbindlich vor.
* **Aktueller Stand:** Das Frontend verwendet `keycloak-angular` mit Authorization Code Flow. `keycloak-js` v25 aktiviert PKCE clientseitig standardmässig (S256), aber der Keycloak-Client `zev-frontend` ist serverseitig nicht auf PKCE-Pflicht konfiguriert. Zudem fehlt die explizite `pkceMethod`-Konfiguration in `app.config.ts`.

---

## 2. Funktionale Anforderungen (FR)

### FR-1: Keycloak-Client-Konfiguration (serverseitig)

Der Keycloak-Client `zev-frontend` im Realm `zev` muss wie folgt konfiguriert sein:

1. **Client-Typ:** Public Client (kein Client Secret — der Quellcode des Frontends ist öffentlich zugänglich).
2. **PKCE Enforcement:** `Proof Key for Code Exchange Code Challenge Method` auf `S256` setzen — Keycloak lehnt Authorization Requests ohne `code_challenge` ab.
3. **Standard Flow:** Aktiviert (Authorization Code Flow).
4. **Direct Access Grants:** Deaktiviert (kein Resource Owner Password Credentials Flow).
5. **Implicit Flow:** Deaktiviert.
6. **Client Credentials:** Deaktiviert.
7. **Valid Redirect URIs:** `http://localhost:4200/*` (Entwicklung), Produktions-URL ergänzen.
8. **Web Origins:** `http://localhost:4200` (CORS).

### FR-2: Frontend-Konfiguration (`app.config.ts`)

Die `provideKeycloak`-Konfiguration muss explizit angepasst werden:

1. `pkceMethod: 'S256'` in den `initOptions` ergänzen.
2. `scope` muss `openid profile organization` enthalten (`profile` für Benutzerinfos im ID Token, `openid` für OIDC-Compliance, `organization` für Mandanten-Claim).
3. `checkLoginIframe: false` bleibt gesetzt (verhindert Silent-Check-iFrame-Probleme bei SameSite-Cookies).
4. `onLoad: 'login-required'` bleibt gesetzt.

### FR-3: OIDC-Ablauf (Authorization Code Flow + PKCE)

Der vollständige Ablauf nach Umsetzung:

1. Angular-App startet → `keycloak-js` prüft Session.
2. Kein gültiges Token vorhanden → `keycloak-js` generiert:
   - `code_verifier` (kryptografisch zufälliger String, 43–128 Zeichen, Base64url-kodiert)
   - `code_challenge` = SHA-256(`code_verifier`), Base64url-kodiert
   - `state` (zufälliger String, CSRF-Schutz)
   - `nonce` (zufälliger String, Replay-Schutz für ID Token)
3. Browser-Redirect zu Keycloak mit: `response_type=code`, `client_id=zev-frontend`, `code_challenge`, `code_challenge_method=S256`, `state`, `nonce`, `scope=openid profile organization`.
4. Keycloak prüft PKCE-Parameter — lehnt Request ab, falls `code_challenge` fehlt.
5. Benutzer meldet sich an → Keycloak sendet `code` + `state` per Redirect zurück.
6. `keycloak-js` prüft `state` (CSRF-Schutz).
7. `keycloak-js` tauscht `code` + `code_verifier` gegen Tokens (ID Token, Access Token, Refresh Token).
8. Keycloak prüft: `SHA-256(code_verifier) == code_challenge` — lehnt ab, falls nicht übereinstimmend.
9. Tokens werden im Memory von `keycloak-js` gehalten (kein `localStorage`).
10. Access Token (JWT) wird via `includeBearerTokenInterceptor` an Backend-Requests angehängt.

### FR-4: Token-Validierung Backend

Das Spring-Boot-Backend bleibt als OAuth2 Resource Server konfiguriert und validiert JWTs via JWKS-Endpoint von Keycloak. Keine Änderungen an `SecurityConfig.java` nötig, da PKCE ein Frontend-/Keycloak-Server-Thema ist.

### FR-5: Keycloak-Konfiguration via Docker Compose / Realm-Export

Die PKCE-Konfiguration muss im Keycloak-Realm-Export (`keycloak/`-Verzeichnis) hinterlegt sein, damit sie bei jedem `docker-compose up` automatisch eingespielt wird. Manuelle Konfiguration über die Keycloak Admin Console reicht nicht.

---

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

* [ ] Ein Authorization Code Request **ohne** `code_challenge` an Keycloak (`/realms/zev/protocol/openid-connect/auth`) wird von Keycloak mit einem Fehler abgelehnt.
* [ ] Ein Authorization Code Request **mit** `code_challenge_method=S256` und gültigem `code_challenge` wird akzeptiert.
* [ ] Ein Token-Request mit falschem `code_verifier` (SHA-256 stimmt nicht mit `code_challenge` überein) wird von Keycloak abgelehnt.
* [ ] Der Benutzer `testuser` kann sich erfolgreich über den OIDC PKCE Flow einloggen und auf `/rechnungen` zugreifen.
* [ ] Der Benutzer `user` (Rolle: `zev`) kann sich einloggen, hat aber keinen Zugriff auf `/rechnungen`.
* [ ] Nach Token-Ablauf erneuert `keycloak-js` das Token automatisch via Refresh Token, ohne erneute PKCE-Challenge.
* [ ] Das ID Token enthält die Claims: `sub`, `nonce`, `email`, `name`, `realm_access.roles`, `organizations`.
* [ ] Nach Logout ist der Token ungültig und ein neuer API-Request führt zum 401.
* [ ] Im Browser-Netzwerk-Tab ist kein `client_secret` im Token-Request sichtbar.
* [ ] Die Keycloak-Konfiguration (PKCE-Enforcement) ist im Realm-Export enthalten und wird bei `docker-compose up` automatisch eingespielt.

---

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Sicherheit

* PKCE-Methode: ausschliesslich `S256` (SHA-256). `plain` ist verboten.
* Kein `client_secret` im Frontend (Public Client).
* Tokens werden ausschliesslich im Arbeitsspeicher (`memory`) gehalten — kein `localStorage`, kein `sessionStorage`. `keycloak-js` implementiert dies standardmässig.
* `state`-Parameter für CSRF-Schutz beim Authorization Code Redirect.
* `nonce`-Parameter im ID Token für Replay Attack Protection.
* `checkLoginIframe: false` bleibt gesetzt, da der iFrame-Check bei modernen SameSite-Cookie-Policies scheitert.

### NFR-2: Kompatibilität

* `keycloak-js` v25 unterstützt PKCE mit S256 nativ — keine zusätzlichen Libraries nötig.
* Backend (`SecurityConfig.java`) bleibt unverändert (JWT-Validierung ist flow-agnostisch).
* Bestehende Rollen (`zev`, `zev_admin`) und Multi-Tenancy (`organizations`-Claim) bleiben vollständig erhalten.

### NFR-3: Betrieb

* Die Keycloak-Konfiguration muss über den Realm-Export reproduzierbar sein (`docker-compose up` startet mit korrekter PKCE-Konfiguration ohne manuelle Schritte).

---

## 5. Edge Cases & Fehlerbehandlung

* **`code_challenge` fehlt im Request:** Keycloak lehnt mit `invalid_request` ab → `keycloak-js` zeigt Fehler → Benutzer sieht Fehlermeldung.
* **`state`-Mismatch (CSRF-Angriff):** `keycloak-js` bricht den Flow ab, kein Token wird ausgestellt.
* **`nonce`-Mismatch (Replay Attack):** `keycloak-js` verwirft das ID Token.
* **`code_verifier` ungültig:** Keycloak lehnt Token-Request ab → Benutzer wird zur Login-Seite weitergeleitet.
* **Authorization Code bereits verwendet (Replay):** Keycloak invalidiert den Code nach erstem Einlösen.
* **Refresh Token abgelaufen:** `keycloak-js` löst Re-Authentisierung aus → Redirect zur Keycloak-Login-Seite.
* **Keycloak nicht erreichbar:** Frontend zeigt Fehlermeldung; keine Anmeldung möglich.
* **Redirect URI nicht in der Whitelist:** Keycloak verweigert die Anfrage → Open Redirect ist nicht möglich.

---

## 6. Abhängigkeiten & betroffene Funktionalität

* **Voraussetzungen:**
  * Keycloak läuft und Realm `zev` mit Client `zev-frontend` existiert.
  * `keycloak-angular ^21.0.0` / `keycloak-js ^25.0.0` sind bereits im Projekt vorhanden.
* **Betroffener Code:**
  * `frontend-service/src/app/app.config.ts` — `pkceMethod: 'S256'` und `scope`-Anpassung.
  * Keycloak-Realm-Export (Docker-Konfiguration) — PKCE-Enforcement aktivieren.
* **Kein Änderungsbedarf:**
  * `backend-service/src/main/java/ch/nacht/config/SecurityConfig.java` (JWT-Validierung ist flow-agnostisch).
  * `AuthGuard`, `ErrorInterceptor`, `OrganizationInterceptor` — bleiben unverändert.
  * Datenbank — keine Migration nötig.
* **Datenmigration:** Keine.

---

## 7. Abgrenzung / Out of Scope

* **Resource Owner Password Credentials Flow (Szenario 2 aus `docs/oidc-authentisierung.md`):** Wird nicht umgesetzt. OAuth 2.1 deprecates diesen Flow.
* **Eigene Login-Seite im Angular-Frontend:** Kein Custom Login UI — Keycloak rendert die Login-Seite.
* **Token-Speicherung in HttpOnly-Cookies:** `keycloak-js` verwaltet Tokens im Memory; Cookie-basiertes Token-Handling ist Out of Scope.
* **Backchannel Logout:** Nicht Teil dieser Anforderung.
* **MFA / Social Login:** Nicht Teil dieser Anforderung (Keycloak unterstützt dies nativ, muss aber separat konfiguriert werden).
* **Produktions-Keycloak-Konfiguration:** Nur lokale Docker-Umgebung wird adressiert.

---

## 8. Offene Fragen

* Wo liegt der aktuelle Keycloak-Realm-Export? Existiert ein `keycloak/`-Verzeichnis im Repo oder wird der Realm manuell konfiguriert?
* Soll der Keycloak-Client nach der Umstellung auf PKCE-Enforcement auch `Revoke Refresh Token` aktivieren (erhöht Sicherheit bei kompromittiertem Refresh Token)?
* Soll zusätzlich `offline_access` als Scope ausgeschlossen werden, um langlebige Offline-Tokens zu verhindern?
* Soll die Access Token Lifetime in Keycloak verkürzt werden (z.B. 5 Minuten) und Refresh Token Lifetime auf Sitzungslänge (z.B. 30 Minuten) gesetzt werden?
