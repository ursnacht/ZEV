# NAS hinter Reverse-Proxy: Zugriff aus VPN **und** LAN

Diese Anleitung stellt die ZEV-Anwendung auf der Synology-NAS hinter einen Reverse-Proxy,
sodass sie unter **einem stabilen Hostnamen** erreichbar ist – gleichermaßen über VPN wie
direkt aus dem Netzwerk, in dem die NAS steht.

## Warum es aktuell nur via VPN geht

Alle browserseitigen URLs der App sind derzeit auf **eine IP** (die VPN-Adresse `10.8.0.1`)
festgenagelt:

- Frontend-Laufzeit-Config (`config.json`: `apiBaseUrl`, `keycloak.url`)
- Backend-CORS (`APP_CORS_ALLOWED_ORIGINS`) und Token-Issuer (`BACKEND_JWT_ISSUER_URI`)
- Keycloak-Client (`redirectUris`/`webOrigins` via `ZEV_FRONTEND_URL`)

Aus dem LAN ist die NAS unter einer **anderen** IP erreichbar (z. B. `192.168.1.50`). Damit
passen Origin/Issuer/Redirect nicht mehr → CORS-Fehler, `401` (Issuer-Mismatch), Login-Redirect
scheitert. Der Ausweg ist **nicht** eine zweite IP-Konfiguration, sondern **ein Hostname**, der
in beiden Netzen aufgelöst werden kann. Dann steht in der App-Config nur noch dieser Name –
unabhängig davon, über welche IP die NAS gerade erreicht wird.

> **Invariante (merken):** Frontend `keycloak.url` == Backend `issuer-uri`-Host == vom Browser
> tatsächlich verwendete Keycloak-URL == `KC_HOSTNAME`. Sobald überall **derselbe Hostname**
> steht, ist der Zugriffsweg (VPN oder LAN) egal.

---

## Schritt 1 — Namensauflösung in beiden Netzen (der eigentliche Knackpunkt)

Die NAS hat **zwei** IP-Adressen: die VPN-Adresse (`10.8.0.1`) und die LAN-Adresse
(`192.168.x.y`). Ein DNS-A-Record kann aber nur **eine** IP tragen. Deshalb muss man einmal
grundsätzlich entscheiden, **wie** derselbe Name `zev.example` in beiden Netzen auf eine
jeweils erreichbare NAS-IP zeigt. Drei Varianten, empfohlen zuerst:

### Variante A (empfohlen, am einfachsten): LAN-Subnetz über das VPN routen
Konfiguriere den VPN-Server so, dass er den Clients **eine Route ins LAN-Subnetz** mitgibt
(z. B. `192.168.1.0/24` via VPN-Gateway). Dann erreichen VPN-Clients die NAS über **dieselbe
LAN-IP** wie LAN-Clients – und ein **einziger** A-Record (`zev.example → 192.168.1.50`) gilt
für beide Netze. Kein Split-DNS nötig.

- OpenVPN (Synology VPN Server): `push "route 192.168.1.0 255.255.255.0"`.
- WireGuard: das LAN-Subnetz in `AllowedIPs` des Clients aufnehmen.

### Variante B: Split-Horizon-DNS (gleicher Name, IP je Netz)
Zwei DNS-Antworten für denselben Namen:
- **LAN-DNS** (Router bzw. Synology-Paket *DNS Server*): `zev.example → 192.168.1.50`
- **VPN-DNS** (vom VPN an die Clients gepushter DNS-Server): `zev.example → 10.8.0.1`

Praktikabel, wenn ohnehin ein lokaler DNS-Server läuft; sonst aufwändiger als Variante A.

### Variante C: `hosts`-Datei je Client (Quick-Start / Test)
Ohne DNS-Infrastruktur pro Client einen Eintrag setzen (die je Netz erreichbare IP):
- Windows: `C:\Windows\System32\drivers\etc\hosts`
- macOS/Linux: `/etc/hosts`
```
10.8.0.1     zev.example      # auf dem VPN-Client
# bzw.
192.168.1.50 zev.example      # auf dem LAN-Client
```
Gut zum Verifizieren des Proxy-Setups, aber pro Gerät zu pflegen – keine Dauerlösung.

> **Hostname wählen:** eine Synology-DDNS (`zev.<name>.synology.me`), eine eigene Domain
> (`zev.example.com`) oder ein rein internes `zev.example`/`zev.home`. Für **HTTPS mit
> öffentlich vertrauenswürdigem Zertifikat** (Schritt 6) braucht es einen echten, per DNS
> auflösbaren Namen (DDNS/eigene Domain).

---

## Schritt 2 — Reverse-Proxy auf der NAS

Zwei Wege. **Variante 1** (ein Hostname, Pfad-Routing) ist für den Doppelnetz-Zugriff am
saubersten: nur **ein** Name aufzulösen und Frontend↔Backend sind **same-origin** → CORS
entfällt komplett. **Variante 2** nutzt den DSM-eigenen Proxy mit Subdomains.

### Variante 1 (empfohlen): ein Hostname + Caddy-Container (Pfad-Routing)

Ein schlanker `caddy`-Container im selben Compose-Netz routet nach Pfad:
`/` → Frontend, `/api` → Backend, `/auth` → Keycloak.

`caddy/Caddyfile`:
```caddyfile
# Ein Einstiegspunkt für alles. HTTP auf :80 (HTTPS siehe Schritt 6).
http://zev.example {
    # Backend-API: KEIN Prefix-Stripping (Backend erwartet /api/...)
    handle /api/* {
        reverse_proxy backend-service:8090
    }
    # Keycloak unter /auth (Container mit KC_HTTP_RELATIVE_PATH=/auth, s. Schritt 3)
    handle /auth/* {
        reverse_proxy keycloak:9000
    }
    # Alles andere -> Angular-Frontend
    handle {
        reverse_proxy frontend-service:8080
    }
}
```

In `docker-compose.nas.yml` ergänzen und die **direkten** Host-Ports von Frontend/Backend/
Keycloak entfernen (nur noch der Proxy ist von außen erreichbar):
```yaml
  caddy:
    image: caddy:2
    container_name: caddy
    ports:
      - "80:80"
      # - "443:443"        # für HTTPS (Schritt 6)
    volumes:
      - ./caddy/Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy-data:/data
      - caddy-config:/config
    depends_on:
      - frontend-service
      - backend-service
      - keycloak
    networks:
      - zev-network
    restart: unless-stopped

volumes:
  caddy-data:
  caddy-config:
```
> Container-interne Ports: Frontend `8080`, Backend `8090`, Keycloak `9000`. Die bisherigen
> `ports:`-Mappings `4200:8080`, `8090:8090`, `9000:9000` bei diesen Services **streichen**
> (Postgres/Monitoring ohnehin nicht nach außen exponieren).

### Variante 2: Synology-Reverse-Proxy (Subdomains)

Nativ über DSM, host-basiert → **je Dienst eine Subdomain** (`zev.` / `auth.` / `api.`).
Vollständig beschrieben in `Specs/NAS-Deployment_Umsetzungsplan.md`, Abschnitt
„HTTPS / Reverse-Proxy (Synology)". Kurz:

*Systemsteuerung → Anmeldeportal → Erweitert → Reverse Proxy*, je Regel Quelle → Ziel:

| Quelle                      | Ziel (intern)             | Dienst    |
|-----------------------------|---------------------------|-----------|
| `zev.example` (443/80)      | `http://localhost:4200`   | Frontend  |
| `auth.example` (443/80)     | `http://localhost:9000`   | Keycloak  |
| `api.example` (443/80)      | `http://localhost:8090`   | Backend   |

Je Regel unter „Benutzerdefinierter Kopf" WebSocket **und** `X-Forwarded-Proto`/`-For`/`-Host`,
`X-Real-IP` ergänzen. Nachteil im Doppelnetz-Kontext: **drei** Namen müssen in beiden Netzen
auflösen, und Frontend↔Backend bleiben cross-origin (CORS bleibt aktiv).

---

## Schritt 3 — Keycloak im Proxy-Modus

Keycloak muss die **externe** URL kennen und den Proxy-Headern vertrauen, sonst baut es
Issuer/Redirects mit dem internen Host/Port. Im `keycloak`-Service:

```yaml
    command:
      - start                          # Produktionsmodus (statt start-dev)
      - --import-realm
      - --features=organization
    environment:
      KC_PROXY_HEADERS: xforwarded     # ältere KC-Versionen: KC_PROXY=edge
      KC_HTTP_ENABLED: "true"          # TLS endet am Proxy, intern HTTP
      KC_HEALTH_ENABLED: "true"
      # Variante 1 (ein Hostname, Pfad /auth):
      KC_HOSTNAME: http://zev.example
      KC_HTTP_RELATIVE_PATH: /auth
      # Variante 2 (Subdomain): KC_HOSTNAME: http://auth.example  (ohne RELATIVE_PATH)
      ZEV_FRONTEND_URL: http://zev.example   # -> redirectUris/webOrigins im Realm
```
> Bei HTTPS (Schritt 6) überall `https://…` statt `http://…`.
> Falls Keycloak im `start`-Modus über die Hostname-Konfiguration meckert, zusätzlich
> `KC_HOSTNAME_STRICT: "false"` setzen.

**Realm-Reimport:** `redirectUris`/`webOrigins` werden aus `${ZEV_FRONTEND_URL}` gebildet
(`keycloak/realms/zev-realm.json`). Nach Änderung von `ZEV_FRONTEND_URL` muss der Realm neu
importiert werden (bzw. die Werte im laufenden Realm über die Admin-Konsole anpassen).

---

## Schritt 4 — ZEV-Konfiguration (`.env` auf der NAS)

Alle browserseitigen Werte auf den **einen Hostnamen** setzen. Bei Variante 1 (Pfad-Routing):

```dotenv
# Frontend-Laufzeit-Config (FrontendConfigController -> /assets/config.json)
FRONTEND_API_BASE_URL=http://zev.example
FRONTEND_KEYCLOAK_URL=http://zev.example/auth
FRONTEND_KEYCLOAK_REALM=zev
FRONTEND_KEYCLOAK_CLIENT_ID=zev-frontend

# Backend: Token-Issuer == vom Browser genutzte Keycloak-URL
BACKEND_JWT_ISSUER_URI=http://zev.example/auth/realms/zev
# CORS: bei Variante 1 same-origin -> praktisch nicht nötig; als Absicherung den Host eintragen
APP_CORS_ALLOWED_ORIGINS=http://zev.example

# Keycloak-Client-Redirect/WebOrigins
ZEV_FRONTEND_URL=http://zev.example
```

Zusätzlich das **interne** JWK-Set-URI an den relativen Pfad `/auth` anpassen (wird
serverseitig im Container-Netz geladen, bleibt HTTP/interner Name):
```
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://keycloak:9000/auth/realms/zev/protocol/openid-connect/certs
```

Referenztabelle:

| Zweck                | Env                                            | Variante 1 (`/auth`)                          | Variante 2 (Subdomains)                    |
|----------------------|------------------------------------------------|-----------------------------------------------|--------------------------------------------|
| Frontend → API       | `FRONTEND_API_BASE_URL`                        | `http://zev.example`                          | `http://api.example`                       |
| Frontend → Keycloak  | `FRONTEND_KEYCLOAK_URL`                         | `http://zev.example/auth`                     | `http://auth.example`                      |
| Token-Issuer         | `BACKEND_JWT_ISSUER_URI`                        | `http://zev.example/auth/realms/zev`          | `http://auth.example/realms/zev`           |
| CORS                 | `APP_CORS_ALLOWED_ORIGINS`                      | `http://zev.example` (entfällt faktisch)      | `http://zev.example`                       |
| Client-Redirect      | `ZEV_FRONTEND_URL`                              | `http://zev.example`                          | `http://zev.example`                       |
| JWK-Set (intern)     | `..._JWT_JWK_SET_URI`                           | `http://keycloak:9000/auth/realms/zev/...`    | `http://keycloak:9000/realms/zev/...`      |
| Keycloak-Hostname    | `KC_HOSTNAME` (+ `KC_HTTP_RELATIVE_PATH`)       | `http://zev.example` + `/auth`                | `http://auth.example`                      |

---

## Schritt 5 — Starten

```bash
docker compose -f docker-compose.nas.yml up -d
docker compose -f docker-compose.nas.yml ps
docker logs caddy
docker logs keycloak
```

---

## Schritt 6 — HTTPS (empfohlen)

HTTP genügt für einen rein vertrauenswürdigen VPN/LAN-Betrieb; für sichere Cookies/Token und
zur Vermeidung von Mixed-Content ist HTTPS empfohlen.

- **Caddy (Variante 1):** Bei einem **öffentlich auflösbaren** Namen genügt es, im `Caddyfile`
  `http://zev.example` durch `zev.example` zu ersetzen – Caddy holt automatisch ein
  Let's-Encrypt-Zertifikat (Port 443 mappen). Für **rein interne** Namen entweder Caddys
  interne CA (`tls internal`, deren Root-Zertifikat die Clients importieren müssen) oder ein
  Let's-Encrypt-Zertifikat per **DNS-01-Challenge**.
- **Synology-Proxy (Variante 2):** *Systemsteuerung → Sicherheit → Zertifikat* → Let's Encrypt
  (DDNS/Port 80/443), Zertifikat den Hostnamen zuweisen, „HTTP→HTTPS-Weiterleitung" aktivieren.

Danach **alle** `http://`-Werte aus Schritt 3/4 auf `https://…` umstellen (inkl. `KC_HOSTNAME`
und `ZEV_FRONTEND_URL` → Realm-Reimport). Der Token-`iss` muss dann die HTTPS-URL sein.

---

## Schritt 7 — Verifikation aus beiden Netzen

Jeweils **einmal über VPN** und **einmal aus dem LAN** prüfen:

1. `http://zev.example` öffnet die App, leitet zu Keycloak (unter demselben Namen) und nach
   Login zurück; die Navbar ist sichtbar.
2. Nach dem Login funktionieren API-Calls (kein `401`, kein CORS-Fehler in der Browser-Konsole).
3. `curl -sS http://zev.example/assets/config.json` liefert `apiBaseUrl`/`keycloak.url` mit dem
   Hostnamen (nicht mit einer IP).
4. Der Token-`iss` (im Browser-DevTools am Access-Token prüfbar) ist der Hostname, identisch zu
   `BACKEND_JWT_ISSUER_URI`.

---

## Troubleshooting

| Symptom | Ursache | Fix |
|---|---|---|
| Seite lädt nur über VPN, LAN „not reachable" | Name löst nicht auf bzw. auf eine im aktuellen Netz nicht erreichbare IP | Schritt 1 (Route ins LAN pushen / Split-DNS / hosts) |
| `CORS … No 'Access-Control-Allow-Origin'` | Origin ≠ `APP_CORS_ALLOWED_ORIGINS`; oder bei Variante 2 Origin fehlt | Hostname in `APP_CORS_ALLOWED_ORIGINS`; bei Variante 1 sollte es same-origin sein |
| `401` auf allen API-Calls | Token-`iss` ≠ `BACKEND_JWT_ISSUER_URI` | Issuer exakt auf die vom Browser genutzte Keycloak-URL setzen |
| Login-Redirect scheitert („Invalid redirect_uri") | `redirectUris`/`webOrigins` passen nicht zum Hostnamen | `ZEV_FRONTEND_URL` setzen + Realm-Reimport |
| Keycloak-Links zeigen internen Host/Port | `KC_PROXY_HEADERS`/`KC_HOSTNAME` fehlen | Schritt 3; Proxy sendet `X-Forwarded-*` |
| `config.json` zeigt noch die IP | alte `.env`/Container | `.env` anpassen, `frontend-service` neu erstellen |
| Keycloak `/auth`-Pfad 404 | `KC_HTTP_RELATIVE_PATH` bzw. JWK-Set-URI ohne `/auth` | beide auf `/auth` (Schritt 3/4) |

---

## Siehe auch
- `Specs/NAS-Deployment.md` – Gesamtbild NAS-Deployment
- `Specs/NAS-Deployment_Umsetzungsplan.md` – Abschnitt „HTTPS / Reverse-Proxy (Synology)"
- `docs/NAS-Images.md` – Images bauen/übertragen
