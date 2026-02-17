# OIDC-Authentisierung (Sequenzdiagramm)

Dieses Diagramm zeigt den vollständigen Authentisierungsfluss eines Benutzers über OpenID Connect mit Keycloak.

```mermaid
sequenceDiagram
    actor User as Benutzer
    participant FE as Angular Frontend<br/>(keycloak-angular)
    participant KC as Keycloak<br/>(OIDC Provider)
    participant Guard as AuthGuard
    participant Interceptor as Bearer Token<br/>Interceptor
    participant BE as Spring Boot<br/>Backend
    participant OrgInt as Organization<br/>Interceptor
    participant API as Controller /<br/>Service

    Note over User, API: 1. Initiale Authentisierung (Authorization Code Flow)

    User->>FE: App aufrufen (localhost:4200)
    FE->>KC: Redirect zu Keycloak Login<br/>(onLoad: 'login-required')<br/>scope: openid organization
    KC-->>User: Login-Seite anzeigen
    User->>KC: Credentials eingeben<br/>(Username + Passwort)
    KC->>KC: Credentials validieren
    KC-->>FE: Authorization Code (Redirect)
    FE->>KC: Authorization Code + Client ID<br/>(Token-Endpunkt)
    KC-->>FE: ID Token + Access Token (JWT)<br/>+ Refresh Token

    Note over FE: JWT enthält:<br/>- realm_access.roles: [zev, zev_admin]<br/>- organizations: {alias: {id: uuid}}

    Note over User, API: 2. Routenschutz (Frontend)

    User->>FE: Navigation zu /rechnungen
    FE->>Guard: canActivate(route)
    Guard->>Guard: Authentifiziert?
    Guard->>Guard: Rollen prüfen<br/>(route.data.roles = ['zev_admin'])
    Guard-->>FE: Zugriff erlaubt / verweigert

    Note over User, API: 3. API-Aufruf mit JWT

    FE->>Interceptor: HTTP Request an /api/rechnungen
    Interceptor->>Interceptor: JWT aus Keycloak-Session lesen
    Interceptor->>BE: GET /api/rechnungen<br/>Authorization: Bearer {JWT}

    Note over User, API: 4. Backend-Validierung

    BE->>BE: JWT-Signatur prüfen<br/>(JWKS von Keycloak)
    BE->>BE: Issuer + Expiration validieren<br/>(issuer: localhost:9000/realms/zev)
    BE->>BE: Rollen extrahieren<br/>(realm_access.roles → ROLE_zev_admin)

    BE->>OrgInt: preHandle()
    OrgInt->>OrgInt: organizations-Claim auslesen
    OrgInt->>OrgInt: org_id in OrganizationContextService setzen<br/>(RequestScope)

    alt Keine Organisation im JWT
        OrgInt-->>BE: NoOrganizationException
        BE-->>FE: 403 {error: "NO_ORGANIZATION"}
        FE->>FE: ErrorInterceptor fängt 403
        FE->>KC: Logout auslösen
        KC-->>User: Zur Login-Seite weiterleiten
    end

    BE->>API: @PreAuthorize("hasRole('zev_admin')")
    API->>API: Daten mit org_id filtern<br/>(Hibernate @Filter)
    API-->>BE: Response (mandantenspezifisch)
    BE-->>FE: 200 OK + JSON
    FE-->>User: Daten anzeigen

    Note over User, API: 5. Token-Erneuerung

    FE->>KC: Refresh Token senden<br/>(automatisch bei Ablauf)
    KC-->>FE: Neuer Access Token
```

---

## Szenario 2: Login-Seite von der ZEV-Anwendung bereitgestellt

In diesem Szenario rendert die ZEV-Anwendung eine eigene Login-Seite. Die Credentials werden über das Backend an Keycloak weitergeleitet (Resource Owner Password Credentials / Direct Access Grant). Der Benutzer verlässt nie die ZEV-Oberfläche.

```mermaid
sequenceDiagram
    actor User as Benutzer
    participant FE as Angular Frontend<br/>(Login-Komponente)
    participant BE as Spring Boot<br/>Backend
    participant KC as Keycloak<br/>(OIDC Provider)
    participant OrgInt as Organization<br/>Interceptor
    participant API as Controller /<br/>Service

    Note over User, API: 1. Login über ZEV-eigene Login-Seite

    User->>FE: App aufrufen (localhost:4200)
    FE->>FE: Nicht authentifiziert →<br/>Login-Seite anzeigen (Angular)
    User->>FE: Username + Passwort eingeben
    FE->>BE: POST /api/auth/login<br/>{username, password}

    Note over BE, KC: Backend leitet Credentials an Keycloak weiter

    BE->>KC: POST /realms/zev/protocol/openid-connect/token<br/>grant_type=password<br/>client_id=zev-backend<br/>client_secret=***<br/>username & password<br/>scope=openid organization
    KC->>KC: Credentials validieren

    alt Credentials ungültig
        KC-->>BE: 401 Unauthorized
        BE-->>FE: 401 {error: "INVALID_CREDENTIALS"}
        FE-->>User: Fehlermeldung anzeigen<br/>"Benutzername oder Passwort falsch"
    end

    KC-->>BE: ID Token + Access Token (JWT)<br/>+ Refresh Token
    BE->>BE: Tokens optional validieren /<br/>Claims extrahieren
    BE-->>FE: 200 OK<br/>{accessToken, refreshToken, expiresIn}

    Note over FE: Frontend speichert Tokens<br/>(z.B. im Memory oder HttpOnly Cookie)

    Note over User, API: 2. Routenschutz (Frontend)

    User->>FE: Navigation zu /rechnungen
    FE->>FE: Token vorhanden?
    FE->>FE: Rollen aus JWT dekodieren<br/>(realm_access.roles)
    FE->>FE: Zugriff erlaubt / verweigert

    Note over User, API: 3. API-Aufruf mit JWT

    FE->>BE: GET /api/rechnungen<br/>Authorization: Bearer {JWT}

    Note over User, API: 4. Backend-Validierung (identisch zu Szenario 1)

    BE->>BE: JWT-Signatur prüfen<br/>(JWKS von Keycloak)
    BE->>BE: Issuer + Expiration validieren
    BE->>BE: Rollen extrahieren<br/>(realm_access.roles → ROLE_zev_admin)

    BE->>OrgInt: preHandle()
    OrgInt->>OrgInt: organizations-Claim auslesen
    OrgInt->>OrgInt: org_id in OrganizationContextService setzen

    alt Keine Organisation im JWT
        OrgInt-->>BE: NoOrganizationException
        BE-->>FE: 403 {error: "NO_ORGANIZATION"}
        FE-->>User: Fehlermeldung + Redirect zur Login-Seite
    end

    BE->>API: @PreAuthorize("hasRole('zev_admin')")
    API->>API: Daten mit org_id filtern<br/>(Hibernate @Filter)
    API-->>BE: Response (mandantenspezifisch)
    BE-->>FE: 200 OK + JSON
    FE-->>User: Daten anzeigen

    Note over User, API: 5. Token-Erneuerung

    FE->>BE: POST /api/auth/refresh<br/>{refreshToken}
    BE->>KC: POST /realms/zev/protocol/openid-connect/token<br/>grant_type=refresh_token
    KC-->>BE: Neuer Access Token + Refresh Token
    BE-->>FE: 200 OK {accessToken, refreshToken}

    Note over User, API: 6. Logout

    User->>FE: Logout klicken
    FE->>BE: POST /api/auth/logout<br/>{refreshToken}
    BE->>KC: POST /realms/zev/protocol/openid-connect/logout<br/>refresh_token (Revocation)
    KC-->>BE: 204 No Content
    BE-->>FE: 200 OK
    FE->>FE: Tokens löschen
    FE-->>User: Login-Seite anzeigen
```

### Unterschiede zu Szenario 1 (Keycloak-Login-Seite)

| Aspekt | Szenario 1 (Keycloak-Seite) | Szenario 2 (ZEV-Login-Seite) |
|--------|----------------------------|-------------------------------|
| **Login-UI** | Keycloak rendert Login-Seite | Angular rendert eigene Login-Komponente |
| **Flow** | Authorization Code Flow (+ PKCE) | Resource Owner Password Credentials (Direct Access Grant) |
| **Redirect** | Browser-Redirect zu Keycloak und zurück | Kein Redirect, alles in der ZEV-App |
| **Token-Austausch** | Frontend ↔ Keycloak direkt | Frontend → Backend → Keycloak (Proxy) |
| **Client-Typ** | Public Client (kein Secret) | Confidential Client (Secret im Backend) |
| **Sicherheit** | Empfohlen (Credentials nur bei Keycloak) | Credentials passieren das Backend |
| **MFA / Social Login** | Nativ unterstützt via Keycloak-UI | Muss selbst implementiert werden |
| **Token-Speicherung** | keycloak-angular verwaltet Session | Eigene Verwaltung (Memory / Cookie) |
| **Refresh** | keycloak-angular automatisch | Eigener Refresh-Mechanismus nötig |

> **Hinweis:** Szenario 1 (Authorization Code Flow) ist die empfohlene Variante gemäss OAuth 2.1 und OIDC Best Practices. Der Resource Owner Password Credentials Grant in Szenario 2 wird in OAuth 2.1 nicht mehr empfohlen, bietet aber eine nahtlose UX ohne Redirect.

---

## Beteiligte Komponenten

| Komponente | Datei | Aufgabe |
|------------|-------|---------|
| Keycloak-Init | `app.config.ts` | Keycloak-Konfiguration (realm: `zev`, clientId: `zev-frontend`) |
| AuthGuard | `guards/auth.guard.ts` | Routenschutz basierend auf Rollen |
| Bearer Interceptor | `app.config.ts` (provideKeycloak) | JWT automatisch an API-Requests anhängen |
| Error Interceptor | `interceptors/error.interceptor.ts` | NO_ORGANIZATION-Fehler abfangen, Logout |
| SecurityConfig | `config/SecurityConfig.java` | JWT-Validierung, Rollen-Extraktion |
| OrganizationInterceptor | `config/OrganizationInterceptor.java` | org_id aus JWT extrahieren |
| OrganizationContextService | `service/OrganizationContextService.java` | org_id im Request-Scope speichern |
| GlobalExceptionHandler | `exception/GlobalExceptionHandler.java` | NoOrganizationException → 403 |
