# Umsetzungsplan: OIDC mit PKCE (Authorization Code Flow + S256)

## Zusammenfassung

Der Keycloak-Client `zev-frontend` erzwingt PKCE aktuell nicht serverseitig — ein Angreifer könnte einen Authorization Code Request ohne `code_challenge` senden. Ziel ist es, PKCE (S256) im Keycloak-Realm-Export (`zev-realm.json`) serverseitig zu erzwingen, den Realm-Import in Docker automatisch zu aktivieren und die Frontend-Konfiguration (`app.config.ts`) explizit auf `pkceMethod: 'S256'` zu setzen. Das Backend bleibt unverändert, da die JWT-Validierung flow-agnostisch ist.

---

## Analyse Ist-Zustand

| Komponente | Ist-Zustand | Soll-Zustand |
|------------|-------------|--------------|
| `keycloak/realms/zev-realm.json` | Kein PKCE-Enforcement, `directAccessGrantsEnabled` nicht gesetzt, `webOrigins: ["*"]` | PKCE S256 erzwungen, unnötige Flows deaktiviert |
| `docker-compose.yml` | `--import-realm` auskommentiert, kein `--features=organization` | Import aktiv, Organization-Feature aktiviert |
| `frontend-service/src/app/app.config.ts` | Kein `pkceMethod`, scope `openid organization` | `pkceMethod: 'S256'`, scope `openid profile organization` |
| `backend-service/config/SecurityConfig.java` | JWT Resource Server | Keine Änderung |

---

## Betroffene Komponenten

| Typ | Datei | Änderungsart |
|-----|-------|--------------|
| Keycloak Config | `docker-compose.yml` | Änderung |
| Keycloak Realm | `keycloak/realms/zev-realm.json` | Änderung |
| Frontend Config | `frontend-service/src/app/app.config.ts` | Änderung |

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [ ] | 1. docker-compose.yml | `--import-realm` aktivieren und `--features=organization` ergänzen |
| [ ] | 2. Realm-Export: PKCE-Enforcement | `pkce.code.challenge.method: S256` im `zev-frontend`-Client setzen, unnötige Flows deaktivieren, `webOrigins` einschränken |
| [ ] | 3. Realm-Export: Client Scopes | Standard-Client-Scopes (`profile`, `openid`) und optionalen Scope `organization` konfigurieren |
| [ ] | 4. Frontend: PKCE-Konfiguration | `pkceMethod: 'S256'` und scope `openid profile organization` in `app.config.ts` |
| [ ] | 5. Manuelle Verifikation | Login-Flow testen, Browser-Netzwerk-Tab prüfen, PKCE-Ablehnung ohne `code_challenge` verifizieren |

---

## Detailbeschreibung der Phasen

### Phase 1: docker-compose.yml

**Datei:** `docker-compose.yml`

Zwei Anpassungen am Keycloak-Service:

1. `--import-realm` einkommentieren (automatischer Realm-Import beim Start)
2. `--features=organization` hinzufügen (Organisation-Feature für `organization`-Scope)

```yaml
command:
  - start-dev
  - --import-realm
  - --features=organization
```

> **Hinweis:** Keycloak importiert den Realm nur, wenn er noch nicht in der Datenbank existiert (`IF NOT EXISTS`-Semantik). Bei laufender Postgres mit bestehendem Realm ist ein `docker-compose down -v` erforderlich, damit der Import greift.

---

### Phase 2: Realm-Export — PKCE-Enforcement & Sicherheitshärtung

**Datei:** `keycloak/realms/zev-realm.json`

Der `zev-frontend`-Client erhält folgende Ergänzungen:

```json
{
    "clientId": "zev-frontend",
    "enabled": true,
    "publicClient": true,
    "standardFlowEnabled": true,
    "implicitFlowEnabled": false,
    "directAccessGrantsEnabled": false,
    "serviceAccountsEnabled": false,
    "redirectUris": [
        "http://localhost:4200/*"
    ],
    "webOrigins": [
        "http://localhost:4200"
    ],
    "attributes": {
        "pkce.code.challenge.method": "S256"
    }
}
```

Geänderte Felder gegenüber Ist-Zustand:
- `standardFlowEnabled: true` — explizit gesetzt (Authorization Code Flow)
- `implicitFlowEnabled: false` — deaktiviert (veraltet, unsicher)
- `directAccessGrantsEnabled: false` — deaktiviert (Resource Owner Password Credentials, in OAuth 2.1 deprecated)
- `serviceAccountsEnabled: false` — deaktiviert (kein Machine-to-Machine für Frontend-Client)
- `webOrigins: ["http://localhost:4200"]` — eingeschränkt statt `["*"]`
- `attributes.pkce.code.challenge.method: "S256"` — **PKCE S256 serverseitig erzwungen**

---

### Phase 3: Realm-Export — Client Scopes

**Datei:** `keycloak/realms/zev-realm.json`

Standard-Client-Scopes für `zev-frontend` konfigurieren, damit `profile` und `openid` automatisch ausgestellt werden und `organization` als optionaler Scope verfügbar ist:

```json
{
    "clientId": "zev-frontend",
    ...
    "defaultClientScopes": [
        "openid",
        "profile",
        "email",
        "roles",
        "web-origins"
    ],
    "optionalClientScopes": [
        "organization"
    ]
}
```

> **Hinweis:** Die Scopes `openid`, `profile`, `email`, `roles`, `web-origins` sind Keycloak-Standardscopes und müssen nicht separat im Realm-Export definiert werden. `organization` ist ein Feature-Scope, der durch `--features=organization` (Phase 1) bereitgestellt wird.

---

### Phase 4: Frontend — `app.config.ts`

**Datei:** `frontend-service/src/app/app.config.ts`

Zwei Anpassungen in `initOptions`:

1. `pkceMethod: 'S256'` hinzufügen (explizit, obwohl keycloak-js v25 S256 bereits als Default verwendet; Typ: `KeycloakPkceMethod = 'S256' | false`)
2. `scope` um `profile` erweitern (für `name`- und `email`-Claims im ID Token)

```typescript
initOptions: {
    onLoad: 'login-required',
    checkLoginIframe: false,
    pkceMethod: 'S256',
    scope: 'openid profile organization'
}
```

---

### Phase 5: Manuelle Verifikation

Nach `docker-compose down -v && docker-compose up --build`:

1. **Login-Flow:** Browser-Login mit `testuser/testpassword` → App öffnet sich ohne Fehler.
2. **Browser Netzwerk-Tab prüfen:**
   - Authorization Request enthält `code_challenge` und `code_challenge_method=S256`
   - Token Request enthält `code_verifier`
   - Token Request enthält **kein** `client_secret`
3. **PKCE-Enforcement prüfen:** Authorization Request ohne `code_challenge` manuell (z.B. curl) an `http://localhost:9000/realms/zev/protocol/openid-connect/auth?response_type=code&client_id=zev-frontend&redirect_uri=http://localhost:4200/` → Keycloak antwortet mit `Missing parameter: code_challenge`
4. **Rollen prüfen:** `testuser` hat Zugriff auf `/rechnungen`, `user` wird abgewiesen.
5. **Token-Claims prüfen:** Access Token im Browser-Netzwerk-Tab mit jwt.io dekodieren → Claims `name`, `email`, `realm_access.roles`, `organizations` sind vorhanden.

---

## Validierungen

### Serverseitig (Keycloak)
1. Requests ohne `code_challenge` werden abgelehnt (`invalid_request`)
2. Token-Requests mit falschem `code_verifier` werden abgelehnt
3. Authorization Code ist nach erstem Einlösen ungültig (One-Time-Use)
4. Redirect URI wird gegen Whitelist geprüft (`http://localhost:4200/*`)

### Frontend (keycloak-js)
1. `state`-Parameter wird nach Redirect gegen gespeicherten Wert geprüft (CSRF)
2. `nonce`-Claim im ID Token wird validiert (Replay-Schutz)
3. Tokens werden im Memory gehalten (kein `localStorage`)

---

## Offene Punkte / Annahmen

1. **Annahme:** `docker-compose down -v` ist bei der ersten Anwendung nötig, um den bestehenden Realm in der Postgres-DB zu löschen und den neuen Import zu aktivieren. Dies ist ein einmaliger Schritt.
2. **Annahme:** Die `organization`-Scope ist ein Keycloak-Feature-Scope, der durch `--features=organization` bereitgestellt wird. Falls bereits manuell in Keycloak konfiguriert, ist kein Mapping-Konflikt zu erwarten.
3. **Offen:** Soll `Revoke Refresh Token` im Client aktiviert werden? (Höhere Sicherheit bei kompromittierten Refresh Tokens, aber etwas grösserer Server-Load.) → Empfehlung: Ja, kann in `attributes` als `"revoke.refresh.token": "true"` gesetzt werden.
4. **Offen:** Token-Lifetimes (Access Token: 5 min, Refresh Token: 30 min)? → Nicht Teil dieser Phase, kann in `tokenSettings` nachgeführt werden.
5. **Offen:** Soll `offline_access` als optionaler Scope explizit ausgeschlossen werden? → Konservative Empfehlung: Ja, `offline_access` aus `optionalClientScopes` entfernen.
