# NAS: einheitlicher Hostname hinter Reverse-Proxy – Umsetzungsplan

## Zusammenfassung

Die Anwendung auf dem NAS ist heute nur über die **VPN-Adresse** `10.8.0.1` nutzbar. Auch im LAN
des NAS (`192.168.7.0/24`, NAS = `192.168.7.240`) muss deshalb zuerst das VPN aufgebaut werden. Ursache: In der
Laufzeit-Konfiguration des Frontends stehen **absolute IP-Adressen**, und eine IP existiert immer
nur in einem der beiden Netze.

Ziel: **ein Hostname, eine Origin.** Ein Reverse-Proxy (Caddy) verteilt nach Pfad auf Frontend,
Backend und Keycloak; der Hostname löst je Netz auf die passende Adresse auf. Danach funktioniert
dieselbe Konfiguration aus VPN **und** LAN, ohne Umschalten.

Grundlage: [`docs/NAS-Proxy.md`](../docs/NAS-Proxy.md) Variante 1 (Caddy, pfadbasiert). Dieser Plan
beschreibt die **konkrete Migration der bestehenden Installation** – die Anleitung dort wird nicht
wiederholt, sondern referenziert.

> **Nebeneffekt (kein Ziel dieses Plans):** Ein Hostname ist die Voraussetzung für HTTPS. Solange
> über HTTP gearbeitet wird, bleibt `keycloak-js` bei 25 und Angular bei 21
> (siehe `.claude/commands/20_dependencies-check.md`, Angular-Upgrade-Gate). Dieser Plan ändert
> daran nichts, macht den späteren Schritt aber möglich.

## Ausgangslage (Ist)

| Aspekt | Ist-Zustand | Fundstelle |
|---|---|---|
| Zugriff | Drei Origins: `:4200` Frontend, `:8090` Backend, `:9000` Keycloak | `docker-compose-mqtt.yml` |
| Frontend-Config | `FRONTEND_API_BASE_URL` / `FRONTEND_KEYCLOAK_URL` = `http://10.8.0.1:…` | `.env.mqtt` (NAS) |
| Keycloak | `start-dev` **ohne** `KC_HOSTNAME` → `iss` wird aus dem Request abgeleitet | `docker-compose-mqtt.yml:67-72` |
| Backend | Validiert gegen ein **festes** `BACKEND_JWT_ISSUER_URI` | `docker-compose-mqtt.yml` |
| Realm | `redirectUris`/`webOrigins` aus `${ZEV_FRONTEND_URL}`; Client erzwingt PKCE `S256` | `keycloak/realms/zev-realm.json:14-24` |
| Proxy | Nicht vorhanden; `docker-compose.nas.yml` aus der Doku existiert im Repo **nicht** | — |
| Ports 80/443 auf dem NAS | **Belegt von DSMs eigenem nginx** (verifiziert: HTTP 200, `Server: nginx`) → Caddy kann sie nicht binden | — |

**Portwahl:** Caddy wird deshalb als `8000:80` gemappt; die Origin lautet `http://nafam.zev:8000`.
Freie Ports auf dem NAS geprüft (2026-08-14): 8000, 8888, 9080 frei; 8081 belegt (admin-service).
Der Port ist Teil der Origin und steckt damit in `KC_HOSTNAME`, `BACKEND_JWT_ISSUER_URI` und den
Realm-Einträgen – **jetzt festlegen**, ein späterer Wechsel zieht alle diese Werte nach sich.

**Warum ein blosser IP-Tausch nicht reicht:** Das Token trägt den `iss`-Claim der Keycloak-Adresse.
Kommen je nach Netz unterschiedliche `iss` an, scheitert die Validierung im Backend mit `401`.
Es braucht einen Namen, der in **beiden** Netzen identisch ist.

## Zielbild

```
              http://nafam.zev:8000          (ein Name, eine Origin)
                        │
                  [caddy  8000:80]
                        ├── /        → frontend-service:8080
                        ├── /api/*   → backend-service:8090
                        └── /auth/*  → keycloak:9000   (KC_HTTP_RELATIVE_PATH=/auth)

DNS:  nafam.zev → 192.168.7.240   (ein Record, gilt für LAN und VPN)
```

> **Geklärt am 2026-08-14: Ein einziger A-Record genügt, kein Split-Horizon, keine Views.**
> Verifiziert von einem VPN-Client (`10.8.0.6`): `ping 192.168.7.240` → 2/2 Pakete (12 ms),
> `route print` zeigt `192.168.7.0/24 via 10.8.0.5` (das VPN pusht die Route ins NAS-LAN),
> `curl http://192.168.7.240:4200/` → HTTP 200. VPN-Clients erreichen die LAN-Adresse des NAS
> also direkt.

Konfigurationswerte im Ziel:

| Variable | Ziel-Wert | Bemerkung |
|---|---|---|
| `FRONTEND_API_BASE_URL` | **leer** | Frontend baut relative `/api`-Aufrufe; der Bearer-Interceptor greift über `^/api(/.*)?$` (`app.config.ts:25-27`) |
| `FRONTEND_KEYCLOAK_URL` | `http://nafam.zev:8000/auth` | |
| `KC_HOSTNAME` | `http://nafam.zev:8000` | **volle URL inkl. Schema und Port**, aber **ohne** `/auth` – den relativen Pfad haengt Keycloak selbst an |
| `KC_HTTP_PORT` | `9000` | **zwingend beibehalten**: ohne diese Variable lauscht Keycloak auf 8080 und die Management-Schnittstelle belegt 9000 |
| `KC_HTTP_MANAGEMENT_PORT` | `9001` | loest den Portkonflikt strukturell |
| `KC_PROXY_HEADERS` | `xforwarded` | |
| `KC_HTTP_RELATIVE_PATH` | `/auth` | |
| `BACKEND_JWT_ISSUER_URI` | `http://nafam.zev:8000/auth/realms/zev` | muss exakt dem `iss` entsprechen |
| `ZEV_FRONTEND_URL` | `http://nafam.zev:8000` | speist `redirectUris`/`webOrigins` |

## Umsetzungsreihenfolge (Phasen)

Die Reihenfolge ist **nicht beliebig** – sie ist so gewählt, dass der bisherige Zugang bis zum
letzten Schritt funktionsfähig bleibt und kein Aussperren möglich ist.

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Namensauflösung **zuerst** | Hostname festlegen und in **beiden** Netzen auflösbar machen (Optionen s. u.). Prüfen mit `ping nafam.zev` bzw. `nslookup` aus VPN und LAN. **Vor** jeder Konfigurationsänderung – ohne funktionierende Auflösung führt jeder weitere Schritt ins Leere. |
| [x] | 2. Caddy **additiv** einführen | Service `caddy` (`image: caddy:2`, s. `docs/NAS-Proxy.md` Variante 1) ergänzen, aber die bestehenden Port-Mappings `4200/8090/9000` **noch nicht entfernen**. Danach ist die Anwendung über den alten *und* den neuen Weg erreichbar. Test: `curl http://nafam.zev:8000/` liefert die index.html, `curl http://nafam.zev:8000/api/…` das Backend. |
| [x] | 3. Keycloak auf `/auth` umstellen | `KC_HTTP_RELATIVE_PATH=/auth`, `KC_HOSTNAME=http://nafam.zev:8000` (**ohne** `/auth` – den relativen Pfad haengt Keycloak selbst an; mit `/auth` in beiden Variablen droht `…/auth/auth/…`), `KC_PROXY_HEADERS=xforwarded` setzen (die Zeilen sind in `docker-compose-mqtt.yml:71-72` bereits als Kommentar vorbereitet). **Achtung:** Ab hier ist die Admin-Konsole nur noch über `http://nafam.zev:8000/auth` erreichbar. |
| [x] | 4. Realm: Redirect-URIs **ergänzen** | In der Admin-Konsole beim Client `zev-frontend` `http://nafam.zev:8000/*` zu `redirectUris` und `http://nafam.zev:8000` zu `webOrigins` **hinzufügen** – die alten Einträge vorerst **stehen lassen**. So bleibt der Zugriff über die IP funktionsfähig, bis alles verifiziert ist. |
| [x] | 5. Backend-Issuer umstellen | **Zwei** Werte: (a) `BACKEND_JWT_ISSUER_URI=http://nafam.zev:8000/auth/realms/zev` und (b) die fest verdrahtete JWK-Set-URL in `docker-compose-mqtt.yml:122` auf `http://keycloak:9000/auth/realms/zev/protocol/openid-connect/certs` (interner Container-Aufruf, geht **nicht** ueber Caddy – nur der Pfad `/auth` kommt dazu). Ohne (b) findet das Backend keine Signaturschluessel und jede Anfrage scheitert. Backend neu starten. **Ab hier werden Tokens mit altem `iss` abgelehnt** – bestehende Sessions brechen, alle Benutzer müssen sich neu anmelden. |
| [x] | 6. Frontend-Config umstellen | `FRONTEND_API_BASE_URL=` (leer), `FRONTEND_KEYCLOAK_URL=http://nafam.zev:8000/auth`, `ZEV_FRONTEND_URL=http://nafam.zev:8000`. Frontend-Container neu starten. |
| [~] | 7. Verifikation aus beiden Netzen | **Teilweise erledigt:** Aus dem VPN heraus verifiziert (Config, Realm/Issuer, JWK-Bezug, Redirect bis zur Login-Maske). **Offen:** echte Anmeldung im Browser und ein Test von einem LAN-Client (`192.168.7.0/24`) **ohne** VPN – genau der Fall, um dessentwillen die Migration gemacht wurde. |
| [ ] | 8. Aufräumen | Direkte Port-Mappings `4200:8080`, `8090:8090`, `9000:9000` entfernen (nur noch Caddy von aussen erreichbar). Alte IP-Einträge aus `redirectUris`/`webOrigins` entfernen. |

### Phase 1 – Optionen für die Namensauflösung

**Zuerst den Routing-Test** (s. Zielbild): `ping 192.168.7.240` von einem VPN-Client.

| Variante | Aufwand | Bemerkung |
|---|---|---|
| **Synology DNS Server** (DSM-Paket) | mittel | Zone auf dem NAS; im LAN per DHCP als DNS verteilen, im VPN als DNS pushen. Sauberste Lösung, beide Netze an einem Ort. Konkrete Schritte s. u. |
| **Router-DNS im NAS-LAN** (`192.168.7.0/24`) | klein, falls unterstützt | Lokaler A-Record im Router; deckt nur das LAN ab, VPN braucht zusätzlich einen Weg |
| **`hosts`-Einträge je Gerät** | minimal | Laut `docs/NAS-Proxy.md:59` „gut zum Verifizieren, keine Dauerlösung" – bei wenigen Geräten aber pragmatisch und sofort verfügbar |

**Gewählter Name: `nafam.zev`** (Zone `zev`, Host `nafam`). Für reines HTTP muss er **nicht**
öffentlich auflösbar sein.

> **Zwei Hinweise zur Namenswahl:** `.local` wäre ungeeignet – für mDNS/Bonjour reserviert, führt
> zu sporadischen Auflösungsfehlern. `.zev` ist keine delegierte TLD; sollte ICANN sie künftig
> vergeben, kollidiert der interne Name mit öffentlichen Adressen. Das Risiko ist gering und
> bewusst akzeptiert; die offiziell für Privatgebrauch reservierte Alternative wäre `.internal`
> (z.B. `nafam.internal`).

#### Synology DNS Server – konkrete Schritte

1. **Paket-Zentrum → DNS Server** installieren.
2. **Zonen → Erstellen → Primäre Zone** (in älteren DSM-Versionen „Master-Zone" – identisch,
   Synology folgt der Umbenennung primary/secondary): Forward-Zone, Domänenname **`zev`**,
   Primärer DNS-Server `192.168.7.240`.
3. **Ressourcendatensatz → A-Record**: Name **`nafam`** → `192.168.7.240`
   (ergibt den FQDN `nafam.zev`).
4. **Auflösung aktivieren** und **Weiterleitungen** auf den bisherigen DNS setzen (Router oder
   öffentlicher Resolver). Ohne diesen Schritt können Clients, die den NAS als DNS nutzen, keine
   Internet-Namen mehr auflösen.
5. **Clients auf den NAS-DNS zeigen:**
   - LAN: im Router als DHCP-DNS (Option 6) `192.168.7.240` eintragen.
   - VPN: im DSM-Paket *VPN Server* die DNS-Adresse an die Clients pushen (OpenVPN:
     Server-Einstellungen; WireGuard: `DNS =` in der Client-Konfiguration).
6. ~~Views/Ansichten~~ – **nicht nötig** (Routing-Test bestanden, s. Zielbild).

Beim Testen den DNS-Cache leeren (`ipconfig /flushdns`) und mit `nslookup nafam.zev` prüfen –
die Ausgabe zeigt auch, **welcher** Server geantwortet hat.

## Risiken und wie sie vermieden werden

| Risiko | Vermeidung |
|---|---|
| **Aussperren aus der Keycloak-Admin-Konsole** durch falsches `KC_HOSTNAME` | Phase 3 erst nach erfolgreichem Phase-2-Test. Rückweg: `KC_HOSTNAME`/`KC_HTTP_RELATIVE_PATH` auskommentieren, Container neu starten – `start-dev` leitet den Host wieder aus dem Request ab |
| **Login kaputt, weil Redirect-URI fehlt** | Phase 4 **ergänzt** statt zu ersetzen; alte Einträge bleiben bis Phase 8 |
| **`--import-realm` greift nicht** | Der Import läuft nur, wenn der Realm noch **nicht existiert**. Eine geänderte `ZEV_FRONTEND_URL` wirkt sich auf einen bestehenden Realm **nicht** aus – die Änderung muss in der Admin-Konsole erfolgen (oder per bewusstem Realm-Reimport, der vom Benutzer angewandt wird) |
| **Alle Sessions brechen** beim `iss`-Wechsel (Phase 5) | Bewusst einplanen, Zeitpunkt wählen; Benutzer melden sich neu an |
| **Backend `401` nach Umstellung** | Ursache ist fast immer ein `iss`-Mismatch: `BACKEND_JWT_ISSUER_URI` muss **exakt** dem `iss` im Token entsprechen (Trailing Slash, Schema, Pfad `/auth`) |
| **Frontend ruft weiterhin absolute URLs** | `FRONTEND_API_BASE_URL` muss wirklich **leer** sein, nicht `http://nafam.zev:8000` – nur dann werden relative `/api`-Pfade gebaut |

## Verifikation

Aus **beiden** Netzen (VPN-Client und LAN-Client bei Hene) jeweils:

```bash
# 1. Name löst auf die erwartete Adresse auf
nslookup nafam.zev

# 2. Ausgelieferte Laufzeit-Config: apiBaseUrl leer, keycloak-URL mit /auth
curl -s http://nafam.zev:8000/assets/config.json

# 3. Keycloak über den Proxy erreichbar, iss stimmt
curl -s http://nafam.zev:8000/auth/realms/zev/.well-known/openid-configuration | grep -o '"issuer":"[^"]*"'

# 4. Ausgelieferte Frontend-Version (Deployment-Check)
curl -s http://nafam.zev:8000/assets/frontend-licenses.json | grep -o '"@angular/core","version":"[^"]*"'
```

Der `issuer` aus Schritt 3 muss **zeichengleich** mit `BACKEND_JWT_ISSUER_URI` sein.

Anschliessend ein echter Login im Browser aus beiden Netzen. Für einen automatisierten Check der
Browser-Konsole eignet sich dasselbe Vorgehen wie bei der Secure-Context-Diagnose (Playwright,
Konsolen-Events mitlesen) – damit wird sichtbar, ob der Redirect sauber durchläuft.

## Offene Punkte / Annahmen

- ~~Hostname noch nicht festgelegt~~ → **entschieden: `nafam.zev`** (Zone `zev`, Host `nafam`).
- **Wo die Auflösung stattfindet**, ist zu entscheiden (Phase 1). Empfehlung: Synology DNS Server,
  weil beide Netze an einem Ort konfiguriert werden.
- **Service-Name `caddy`** (nicht `edge-service`): Das Compose benennt eigene Maven-Module
  `*-service` (`backend-service`, `admin-service`, `frontend-service`) und Fremd-Images nach dem
  Produkt (`postgres`, `keycloak`, `mosquitto`). Caddy ist ein Fremd-Image – `*-service` würde
  fälschlich „von uns gebaut" signalisieren. Entschieden am 2026-08-14.
- **`FRONTEND_API_BASE_URL` leer setzen:** Die Compose nutzt `${VAR:-default}`; diese Syntax greift auch bei **leerem** Wert. Fuer einen echt leeren Wert muss der Doppelpunkt weg (`${VAR-default}`), sonst landet stillschweigend `http://localhost:8090` in der Config.
- **`docker-compose.nas.yml` existiert im Repo nicht** – die NAS-Installation nutzt
  `docker-compose-mqtt.yml`. Der Caddy-Service ist dort (bzw. in der NAS-Kopie) zu ergänzen; ob
  die Compose-Datei ins Repo gehört, ist offen.
- **Realm-Änderungen wendet der Benutzer selbst an** (Konvention: keine Realm-Reimporte durch
  Claude).
- **LAN-IP des NAS:** `192.168.7.240` (Stand 2026-08-14). Nicht zu verwechseln mit dem
  **Zählernetz** bei Hene (`192.168.10.0/24`, Router `.1`, Pi `.189`, WAGO `.164`) – das ist ein
  anderes Subnetz und für diesen Plan irrelevant.
- **HTTPS bleibt ausserhalb dieses Plans**; er schafft nur die Voraussetzung dafür.
