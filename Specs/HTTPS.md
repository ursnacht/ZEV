# HTTPS für die ZEV-Anwendung

Analyse des Ist-Zustands und der Schritte, die für den Wechsel von HTTP auf HTTPS nötig sind —
lokal (Abschnitt 5, vorbereitet) und auf dem NAS (Abschnitt 6, offen).
Stand: 20.08.2026, Zertifikatsweg **A** (Caddys interne CA) entschieden.

> **Kein Feature, sondern eine Betriebsänderung.** Dieses Dokument folgt deshalb nicht dem
> Template `Specs/SPEC.md`, sondern der Struktur der Deployment-Pläne
> (`Specs/NAS-Einheitlicher-Hostname_Umsetzungsplan.md`, `docs/NAS-Proxy.md`).

---

## 1. Ausgangslage

Die Anwendung läuft hinter einem Caddy-Reverse-Proxy als **eine Origin**
(`http://nafam.zev:8000` auf dem NAS, `http://localhost:8000` lokal). Pfad-Routing:
`/` → Frontend, `/api` → Backend, `/auth` → Keycloak.

| Baustein | Ist | Datei |
|---|---|---|
| Caddy-Site-Adresse | `:80` (ohne Hostname, beantwortet jeden Host) | `caddy/Caddyfile:11` |
| Port-Mapping | `8000:80` (DSM belegt 80/443) | `docker-compose.yml:207` |
| Keycloak-Hostname | `${KC_HOSTNAME:-http://localhost:8000/auth}` | `docker-compose.yml:45` |
| Redirect/WebOrigins | `${ZEV_FRONTEND_URL:-http://localhost:8000}` | `docker-compose.yml:48` |
| Token-Issuer | `${BACKEND_JWT_ISSUER_URI:-http://localhost:8000/auth/realms/zev}` | `docker-compose.yml:95` |
| Frontend → Keycloak | `${FRONTEND_KEYCLOAK_URL:-http://localhost:8000/auth}` | `docker-compose.yml:143` |
| JWK-Set (intern) | `http://keycloak:9000/auth/realms/zev/...` | `docker-compose.yml:98` |

### Befund: kein Image-Rebuild nötig

Alle browserseitigen Werte sind zur Laufzeit über Environment-Variablen gesetzt. Der
`FrontendConfigController` liefert `/assets/config.json` aus `frontend.config.*`
(`frontend-service/src/main/resources/application.yml:26-32`), Issuer und Redirects kommen
ebenfalls aus dem Environment. Der Wechsel ist damit **reine Konfiguration**: Caddyfile,
`.env`, Realm-Reimport. Die eingecheckte `frontend-service/src/assets/config.json` ist nur
der Fallback für den Angular-Dev-Server und für den Container-Betrieb ohne Belang.

---

## 2. Der eigentliche Knackpunkt: das Zertifikat

Nicht die Konfiguration ist das Problem, sondern die Zertifikatsbeschaffung. Zwei Umstände
schliessen den einfachen Weg aus:

1. **`nafam.zev` ist kein echter Name.** `.zev` ist keine existierende TLD; der Name ist
   rein intern (DNS-Record `nafam.zev → 192.168.7.240`). Let's Encrypt stellt für nicht
   öffentlich auflösbare Namen kein Zertifikat aus.
2. **Die Ports 80 und 443 sind von DSM belegt.** Caddy hängt deshalb auf `8000:80`. Damit
   fallen **HTTP-01 und TLS-ALPN-01 als Challenge aus** — beide setzen voraus, dass Let's
   Encrypt den Host auf genau diesen Ports erreicht.

Es bleiben drei Wege:

| Weg | Hostname | Aufwand | Haken |
|---|---|---|---|
| **A — Caddys interne CA** (`tls internal`) | bleibt `nafam.zev` | am kleinsten | Root-Zertifikat muss auf **jedem** Client importiert werden (Windows, macOS, Android, iOS) |
| **B — DSM-Zertifikat einbinden** | wechselt auf `xxx.synology.me` | mittel | DSM holt/erneuert das Let's-Encrypt-Zertifikat, PEM wird in Caddy gemountet (`tls /etc/caddy/cert.pem /etc/caddy/key.pem`); nach jeder Erneuerung braucht Caddy einen Reload |
| **C — Let's Encrypt via DNS-01 in Caddy** | echte Domain nötig | am grössten | Das offizielle Image `caddy:2` enthält **keine** DNS-Provider-Module — es braucht ein eigenes Image (xcaddy-Build) |

**Empfehlung:** **B**, wenn der Hostname wechseln darf — danach ist der Betrieb wartungsfrei
und ohne Arbeit auf den Clients. **A**, wenn `nafam.zev` bleiben soll und die Geräteanzahl
überschaubar ist.

> **Entscheid (20.08.2026): Weg A — Caddys interne CA.** Der Hostname bleibt damit erhalten
> (`localhost` lokal, `nafam.zev` auf dem NAS), und es besteht keine Abhängigkeit zu einer
> externen Zertifizierungsstelle. Preis dafür: Das Root-Zertifikat muss auf jedem Client in
> den Zertifikatspeicher importiert werden (Abschnitt 5).

---

## 3. Zielbild

Origin nach dem Wechsel (Beispiel Weg A, Port 8443, weil 443 von DSM belegt ist):

```
https://nafam.zev:8443          (eine Origin, wie bisher)
   /                 -> frontend-service:8080
   /api/*            -> backend-service:8090
   /auth/*           -> keycloak:9000
```

Die **Invariante aus `docs/NAS-Proxy.md` gilt unverändert:** Frontend `keycloak.url` ==
Backend `issuer-uri`-Host == vom Browser tatsächlich verwendete Keycloak-URL == `KC_HOSTNAME`.
Beim Wechsel auf HTTPS muss sie an *allen* vier Stellen gleichzeitig nachgezogen werden —
eine halbe Umstellung ergibt `401` auf jeden API-Call.

---

## 4. Konfigurationswerte im Ziel

### `caddy/Caddyfile`

Die Datei beschreibt den Umbau bereits selbst (Zeilen 9–10). Aus

```caddyfile
:80 {
```

wird

```caddyfile
nafam.zev {
	tls internal          # Weg A
	# tls /etc/caddy/cert.pem /etc/caddy/key.pem    # Weg B
```

Das Mapping `8000:80` sollte **bestehen bleiben**, damit alte Lesezeichen nicht ins Leere
laufen. Caddys **automatische** HTTP→HTTPS-Weiterleitung genügt dafür allerdings nicht: Sie
kennt nur ihren eigenen Listener-Port (443) und zeigt auf `https://nafam.zev/` — von aussen
ist der Port aber 8443. Die Weiterleitung gehört deshalb explizit in den Caddyfile, so wie
in `caddy/Caddyfile.https` für den lokalen Betrieb vorgemacht (Abschnitt 5).

Zu beachten: Mit einem Hostnamen als Site-Adresse beantwortet Caddy nur noch **diesen** Host.
Der Zugriff über die reine IP (`http://192.168.7.240:8000`) funktioniert dann nicht mehr —
bisher tat er das, weil `:80` jeden Host annimmt.

### `docker-compose.yml`

```yaml
  caddy:
    ports:
      - "8000:80"       # bleibt: HTTP->HTTPS-Weiterleitung
      - "8443:443"      # neu
```
Bei Weg B zusätzlich das Zertifikat als Volume einhängen (read-only).

### `.env` auf dem NAS

| Variable | Ziel-Wert | Bemerkung |
|---|---|---|
| `KC_HOSTNAME` | `https://nafam.zev:8443/auth` | volle URL inkl. Schema, Port **und Pfad** — siehe Warnung unten |
| `ZEV_FRONTEND_URL` | `https://nafam.zev:8443` | speist `redirectUris`/`webOrigins` |
| `FRONTEND_KEYCLOAK_URL` | `https://nafam.zev:8443/auth` | |
| `BACKEND_JWT_ISSUER_URI` | `https://nafam.zev:8443/auth/realms/zev` | muss zeichengleich zum Token-`iss` sein |
| `FRONTEND_API_BASE_URL` | **bleibt leer** | Frontend baut relative `/api`-Aufrufe |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:4200,https://nafam.zev:8443` | **zwingend**, trotz same-origin — siehe Warnung unten |

**Ausdrücklich unverändert:**
`SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://keycloak:9000/auth/realms/zev/protocol/openid-connect/certs`
— diese Verbindung läuft im Container-Netz und hat mit TLS nichts zu tun. Sie auf HTTPS
umzustellen wäre ein Fehler (Keycloak lauscht intern auf HTTP).

`KC_PROXY_HEADERS: xforwarded` ist bereits gesetzt; Caddy sendet `X-Forwarded-Proto: https`
automatisch, Keycloak leitet Schema und Port daraus ab.

> ### ⚠ `KC_HOSTNAME` muss auf `/auth` enden
> Fehlt der Pfad, schreibt Keycloak **alle** seine URLs ohne ihn aus — der Issuer lautet dann
> `https://…:8443/realms/zev` statt `https://…:8443/auth/realms/zev`. `KC_HTTP_RELATIVE_PATH`
> wird **nicht** zusätzlich angehängt. Der Proxy routet aber nur `/auth/*` zu Keycloak, also
> endet der Login im 404 auf einer Adresse, die es hinter dem Proxy nicht gibt.
>
> Beim lokalen Wechsel am 20.08.2026 genau so passiert. Prüfen lässt es sich in einem Zug:
> ```bash
> curl -sk https://localhost:8443/auth/realms/zev/.well-known/openid-configuration | grep -o '"issuer":"[^"]*"'
> ```
> Der Wert muss zeichengleich zu `BACKEND_JWT_ISSUER_URI` sein.
>
> `Specs/NAS-Einheitlicher-Hostname_Umsetzungsplan.md` behauptet in der Wertetabelle das
> Gegenteil („**ohne** `/auth` – den relativen Pfad hängt Keycloak selbst an"). Das trifft für
> die eingesetzte Keycloak-Version nicht zu; der Default in `docker-compose.yml` enthält den
> Pfad seit jeher.

> ### ⚠ Die TLS-Terminierung macht aus same-origin eine CORS-Anfrage
> Hinter dem Proxy laufen Frontend und API über dieselbe Origin. Spring erkennt das und prüft
> CORS gar nicht erst — **solange Schema, Host und Port übereinstimmen**. Caddy reicht den
> `Host`-Header unverändert weiter, Host und Port stimmen also. Das **Schema** nicht: TLS endet
> am Proxy, intern läuft HTTP, und das Backend wertet `X-Forwarded-Proto` nicht aus. Es
> rekonstruiert die Anfrage deshalb als `http://localhost:8443/...`, während der Browser
> `Origin: https://localhost:8443` sendet — für Spring zwei verschiedene Herkünfte.
>
> Sichtbar wird das nur bei **Nicht-GET-Aufrufen**: Bei gleicher Herkunft sendet der Browser
> den `Origin`-Header ausschliesslich für POST/PUT/DELETE. Die Anwendung besteht fast nur aus
> GET-Aufrufen — betroffen sind `POST /api/datenbank/abfrage` (Datenbank-Ansicht) und
> `POST /api/rechnungen/generate` (Rechnungslauf, und in der Folge der Download, weil ohne
> Lauf nichts zum Herunterladen entsteht). Symptom: **`Invalid CORS request` (403)**.
>
> Prüfen lässt es sich ohne Anmeldung; entscheidend ist nur, ob CORS oder erst die
> Authentisierung greift:
> ```bash
> curl -sk -o /dev/null -w "%{http_code}\n" -X POST -H "Origin: https://localhost:8443" \
>   -H "Content-Type: application/json" -d '{"tabelle":"einheit"}' \
>   https://localhost:8443/api/datenbank/abfrage
> ```
> `401` = CORS in Ordnung (nur der Token fehlt). `403` mit `Invalid CORS request` = Origin fehlt
> in der Liste.
>
> **Der HTTP-Betrieb ist nicht betroffen** — dort stimmen Schema, Host und Port überein. Der
> Wert war bisher allerdings in **keiner** Compose-Datei durchgereicht und liess sich per `.env`
> gar nicht setzen; das ist jetzt in allen drei Dateien ergänzt (Default unverändert).
>
> **Sauberere Alternative, noch nicht umgesetzt:** `server.forward-headers-strategy: framework`
> im Backend. Dann rekonstruiert Spring die externe URL aus `X-Forwarded-Proto`/`-Host`,
> erkennt den Aufruf korrekt als same-origin, und CORS entfällt vollständig — die Origin müsste
> beim Hostnamenwechsel nirgends mehr nachgezogen werden. Die Einstellung wirkt über CORS
> hinaus (erzeugte Links, Redirects) und ist deshalb separat zu bewerten.

### Realm

`redirectUris` und `webOrigins` werden aus `${ZEV_FRONTEND_URL}` gebildet
(`keycloak/realms/zev-realm.json:14-23`). Nach der Änderung ist ein **Realm-Reimport**
nötig — oder die Werte werden in der Admin-Konsole von Hand angepasst. Ohne diesen Schritt
scheitert der Login mit „Invalid redirect_uri".

Die zusätzlich hart eingetragenen `http://localhost:8000/*`-Einträge betreffen die lokale
Entwicklung und können bleiben.

`sslRequired` ist im Realm nicht gesetzt, gilt also mit dem Keycloak-Default `external`.
Unter HTTPS ist die Bedingung ohnehin erfüllt — kein Handlungsbedarf.

---

## 5. Lokale Umstellung (vorbereitet)

Die lokale Umstellung ist im Repository **vorbereitet, aber nicht aktiv**. Der Stack läuft
unverändert auf `http://localhost:8000`; der Wechsel ist ein Block in der `.env`.

### Was dafür im Repository liegt

| Datei | Rolle |
|---|---|
| `caddy/Caddyfile.https` | HTTPS-Variante mit `tls internal` und expliziter HTTP→HTTPS-Weiterleitung. Der bisherige `caddy/Caddyfile` bleibt der Default und ist unverändert. |
| `docker-compose.yml` | Caddyfile-Pfad über `${CADDY_CONFIG:-./caddy/Caddyfile}`, zusätzlicher Port `8443:443`. |
| `.env.example` | Auskommentierter HTTPS-Block mit allen sechs Werten. |
| `frontend-service/playwright.config.ts` | `ignoreHTTPSErrors: true` — sonst scheiterte die gesamte E2E-Suite am Zertifikat. |

**Warum eine eigene Weiterleitungs-Site statt Caddys automatischer:** Caddys eingebaute
HTTP→HTTPS-Weiterleitung kennt nur ihren eigenen Listener-Port (443) und zeigt auf
`https://localhost/`. Von aussen ist der Port aber 8443 — die Weiterleitung liefe ins Leere.
Deshalb steht sie in `Caddyfile.https` explizit als `redir https://localhost:8443{uri}`.

**Warum Port 8443 auch lokal:** 443 ist auf dem NAS von DSM belegt. Lokal denselben Port zu
verwenden hält beide Umgebungen deckungsgleich — genau der Grund, aus dem auch `8000` schon
lokal gespiegelt wird.

### Schritte

```bash
# 1. HTTPS-Block in der .env einkommentieren (alle sechs Zeilen, siehe .env.example)

# 2. Stack neu starten
docker compose up -d

# 3. Root-Zertifikat der internen CA exportieren
docker cp caddy:/data/caddy/pki/authorities/local/root.crt caddy-root.crt

# 4. Import in den Windows-Zertifikatspeicher (Administrator-Konsole)
certutil -addstore -f ROOT caddy-root.crt

# 5. Realm neu importieren (redirectUris/webOrigins haengen an ZEV_FRONTEND_URL)

# 6. Issuer gegenpruefen - MUSS zeichengleich zu BACKEND_JWT_ISSUER_URI sein
curl -sk https://localhost:8443/auth/realms/zev/.well-known/openid-configuration \
  | grep -o '"issuer":"[^"]*"'
# erwartet: "issuer":"https://localhost:8443/auth/realms/zev"
```

Schritt 6 ist der wichtigste: Er faengt den Fehler ab, bei dem Keycloak seine URLs ohne `/auth`
ausschreibt (siehe Warnung in Abschnitt 4). Symptom sonst: Die Anwendung laedt, aber der Login
endet auf einer 404-Adresse.

**Firefox** benutzt einen eigenen Zertifikatspeicher: entweder `security.enterprise_roots.enabled`
in `about:config` auf `true` setzen (übernimmt dann den Windows-Speicher) oder `caddy-root.crt`
unter *Einstellungen → Datenschutz → Zertifikate anzeigen → Importieren* eintragen.

Das Zertifikat wird über das benannte Volume `caddy-data` gehalten. Wird es gelöscht
(`docker compose down -v`), erzeugt Caddy eine **neue** CA — dann ist Schritt 3–4 zu wiederholen.

### Was dabei zu beachten ist

* **Der Angular-Dev-Server braucht eine eigene Anpassung.** `ng serve` auf `:4200` liest
  `frontend-service/src/assets/config.json` (im Container wird diese Datei vom
  `FrontendConfigController` überschrieben, beim Dev-Server nicht). Dort stehen noch
  `http://localhost:8090` und `http://localhost:8000/auth`. Bleiben sie stehen, meldet man sich
  am HTTP-Keycloak an und bekommt einen Token, dessen `iss` nicht zum umgestellten
  `BACKEND_JWT_ISSUER_URI` passt → `401` auf jeden API-Call. Die Datei ist bewusst **nicht**
  vorab umgestellt, weil das den heutigen HTTP-Dev-Betrieb bräche.
* **E2E:** `E2E_BASE_URL=https://localhost:8443` ist Teil des `.env`-Blocks. `ignoreHTTPSErrors`
  ist bereits gesetzt, ein CA-Import ist für die Tests also nicht nötig.
* **Rollback:** Den `.env`-Block wieder auskommentieren und den Stack neu starten. Es ist keine
  Datei zu ändern und kein Image neu zu bauen.

---

## 6. Umsetzungsreihenfolge (NAS)

Die Reihenfolge ist so gewählt, dass der bisherige Zugang bis zum letzten Schritt
funktionsfähig bleibt.

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [ ] | 1. Zertifikatsweg entscheiden | Abschnitt 2. Bestimmt den Hostnamen und damit alle weiteren Werte. |
| [ ] | 2. Zertifikat bereitstellen | Weg A: nichts (Caddy erzeugt es beim Start). Weg B: DSM-Zertifikat anfordern und den PEM-Pfad festlegen. Weg C: eigenes Caddy-Image bauen. |
| [ ] | 3. Caddy **additiv** auf HTTPS | Site-Adresse + `tls`-Direktive, Port `8443:443` ergänzen, `8000:80` **behalten**. Danach ist die App über beide Wege erreichbar — kein Aussperren möglich. |
| [ ] | 4. Erreichbarkeit prüfen | `https://nafam.zev:8443/` liefert die index.html; das Zertifikat wird als vertrauenswürdig angezeigt (bei Weg A erst nach Import der Root-CA). |
| [ ] | 5. Root-CA verteilen (nur Weg A) | Caddys Root-Zertifikat aus dem Volume `caddy-data` holen und auf allen Clients importieren. |
| [ ] | 6. `.env` umstellen | Alle Werte aus Abschnitt 4 **gemeinsam** — eine halbe Umstellung bricht die Anmeldung. |
| [ ] | 7. Realm-Reimport | `redirectUris`/`webOrigins` auf die neue Origin. |
| [ ] | 8. Neustart und Verifikation | Abschnitt 6. |
| [ ] | 9. HTTP abklemmen (optional) | Erst wenn alles läuft: Weiterleitung behalten, aber sicherstellen, dass kein Client mehr direkt auf `:8000` konfiguriert ist. |

Die Schritte 2–9 greifen in die Umgebung ein (Container, Keycloak, Zertifikate) und werden
**vom Betreiber ausgeführt**, nicht von einem Agenten.

---

## 7. Verifikation

1. `https://nafam.zev:8443` öffnet die App, leitet zu Keycloak unter derselben Origin und
   nach dem Login zurück.
2. `http://nafam.zev:8000` leitet auf HTTPS weiter.
3. `curl -sS https://nafam.zev:8443/assets/config.json` liefert `keycloak.url` mit `https://`
   (bei Weg A mit `--cacert` bzw. `-k`).
4. Der Token-`iss` (DevTools → Access-Token) ist zeichengleich zu `BACKEND_JWT_ISSUER_URI`.
5. Keine Mixed-Content-Warnung in der Browser-Konsole.
6. Aus **beiden** Netzen prüfen (VPN und LAN).

Häufigster Fehler beim Wechsel: `401` auf alle API-Calls, weil Token-`iss` und
`BACKEND_JWT_ISSUER_URI` sich in Schema oder Port unterscheiden.

---

## 8. Auswirkungen auf das Projekt

- **E2E-Tests:** `playwright.config.ts:23` liest `E2E_BASE_URL`, der Wechsel ist also über
  eine Variable machbar. Bei Weg A braucht die Konfiguration zusätzlich
  `ignoreHTTPSErrors: true`, oder die Root-CA muss auf dem Testrechner vertraut sein.
- **Frontend-Upgrade wird entsperrt:** Das Projekt steht bewusst auf `keycloak-js ^25` und
  Angular 21, weil keycloak-js 26 einen Secure Context verlangt und der NAS auf HTTP läuft.
  Mit HTTPS fällt diese Sperre — der Upgrade-Pfad auf Angular 22 wird danach neu bewertbar.
- **Lokale Entwicklung** bleibt unberührt: `localhost` ist bereits ein Secure Context, die
  Defaults in `docker-compose.yml` zeigen weiterhin auf `http://localhost:8000`.

---

## 9. Offene Punkte

* Welcher Zertifikatsweg (A, B oder C)? → bestimmt den Hostnamen.
* Bei Weg B: Soll der Hostname dauerhaft auf die Synology-DDNS wechseln, oder bleibt
  `nafam.zev` als zusätzlicher interner Name bestehen (dann zwei Namen im Zertifikat bzw.
  zwei Site-Blöcke in Caddy)?
* Externer Port: `8443` gesetzt, weil DSM 443 belegt. Alternativ liesse sich DSM umkonfigurieren
  — das ist aber ein Eingriff ins NAS ausserhalb dieser Anwendung.

---

## Siehe auch
* `docs/NAS-Proxy.md` — Reverse-Proxy-Setup, Schritt 6 (HTTPS) als Kurzfassung
* `Specs/NAS-Einheitlicher-Hostname_Umsetzungsplan.md` — schuf die Voraussetzung (ein Hostname)
* `docs/Deploy-Hene.md`, `docs/NAS-Images.md` — Deployment-Abläufe
