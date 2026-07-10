# Docker-Images für das NAS bauen und übertragen

Diese Anleitung beschreibt, wie die drei lokal gebauten ZEV-Images
(`backend-service`, `admin-service`, `frontend-service`) für das **NAS**
(x86-64 / `linux/amd64`) gebaut, als Archiv exportiert, komprimiert auf das NAS
übertragen, dort geladen und gestartet werden.

> **Architektur:** Der ZEV-App-Stack (backend/admin/frontend + Broker) läuft auf dem **NAS**.
> Der **Raspberry Pi** fährt nur den `pi-gateway` (Zähler auslesen → MQTT) und wird **separat**
> paketiert – siehe [`scripts/package-pi-gateway.ps1`](../scripts/package-pi-gateway.ps1) bzw.
> [`pi-gateway/README.md`](../pi-gateway/README.md).

> Die übrigen Images (`postgres`, `keycloak`, `prometheus`, `grafana`, `eclipse-mosquitto`)
> zieht das NAS direkt aus den öffentlichen Registries und müssen **nicht** übertragen werden.

## Voraussetzungen

### Auf dem Bau-Rechner

- Docker mit **Buildx** (in Docker Desktop standardmässig enthalten)
- Nur falls der Bau-Rechner **nicht** x86-64 ist (z.B. Apple Silicon): einmalig QEMU-Emulation
  für Cross-Plattform-Builds aktivieren:
  ```bash
  docker run --privileged --rm tonistiigi/binfmt --install all
  ```
- Ziel-Plattform des NAS kennen: Intel/AMD-NAS → `linux/amd64` (Standard). Eine ARM-NAS
  entsprechend `linux/arm64` (überall `linux/amd64` durch `linux/arm64` ersetzen).

### Auf dem NAS (Synology)

- **Container Manager** (DSM 7.2+; früher „Docker"-Paket) installieren.
- SSH aktivieren; danach stehen `docker` und `docker compose` auf dem NAS zur Verfügung.

## Automatisierung (Schritte 1 + 2)

Die beiden folgenden Schritte (Bauen + Exportieren/Komprimieren) erledigt das
Skript [`scripts/build-nas-images.ps1`](../scripts/build-nas-images.ps1) in einem Lauf:

```powershell
# Standard: linux/amd64, Tag "amd64" -> /data/ZEV/zev-images-amd64.tar.gz
./scripts/build-nas-images.ps1

# ARM-NAS:
./scripts/build-nas-images.ps1 -Platform linux/arm64 -Tag arm64
```

Wer die Schritte manuell nachvollziehen möchte, folgt 1 + 2 unten.

## 1. Images für die NAS-Plattform bauen

Jeden Service einzeln mit Buildx für die Ziel-Plattform bauen und ins lokale
Docker laden (`--load`):

```bash
docker buildx build --platform linux/amd64 \
  -t zev-backend-service:amd64 --load ./backend-service

docker buildx build --platform linux/amd64 \
  -t zev-admin-service:amd64 --load ./admin-service

docker buildx build --platform linux/amd64 \
  -t zev-frontend-service:amd64 --load ./frontend-service
```

Prüfen, ob die Images vorhanden sind:

```bash
docker images | grep zev-
```

## 2. Images exportieren und komprimieren

Alle drei Images in **eine** gzip-komprimierte tar-Datei exportieren:

```bash
docker save zev-backend-service:amd64 zev-admin-service:amd64 zev-frontend-service:amd64 \
  | gzip > zev-images-amd64.tar.gz
```

Ergebnis: `zev-images-amd64.tar.gz`.

## 3. Datei auf das NAS übertragen

```bash
scp zev-images-amd64.tar.gz <user>@<nas-host>:/volume1/docker/zev/
```

`<nas-host>` durch IP oder Hostname des NAS ersetzen; Zielpfad an die NAS-Freigabe anpassen.

## 4. Auf dem NAS entpacken und laden

Per SSH auf das NAS verbinden und das Archiv direkt in Docker laden
(`docker load` entpackt gzip selbst, ein separates `gunzip` ist nicht nötig):

```bash
ssh <user>@<nas-host>

gunzip -c /volume1/docker/zev/zev-images-amd64.tar.gz | docker load
```

Geladene Images prüfen:

```bash
docker images | grep zev-
```

## 5. Stack auf dem NAS starten

Auf dem NAS werden folgende Dateien benötigt:

- `docker-compose.yml`
- `.env` (mit Secrets: `ANTHROPIC_API_KEY`, DB-Passwörter, Keycloak-Admin etc.)
- die referenzierten Bind-Mounts (`keycloak/realms`, `prometheus/prometheus.yml`,
  `grafana/provisioning`)

Damit Compose die übertragenen Images verwendet (statt sie neu zu bauen), in der
`docker-compose.yml` auf dem NAS für die drei Services `build:` durch `image:` ersetzen:

```yaml
  backend-service:
    image: zev-backend-service:amd64
    # build: ./backend-service   <- entfällt auf dem NAS

  admin-service:
    image: zev-admin-service:amd64

  frontend-service:
    image: zev-frontend-service:amd64
```

Anschliessend starten:

```bash
docker compose up -d
```

Status und Logs prüfen:

```bash
docker compose ps
docker compose logs -f backend-service
```

## Alternative: Übertragung über eine private Registry (Synology NAS)

Die Schritte 2–4 (Exportieren → Übertragen → Laden) lassen sich durch eine private
Docker-Registry ersetzen. Das lohnt sich, sobald **wiederholt** deployt wird: der
Deploy wird dann zu `docker compose pull && docker compose up -d`.

Eine Synology NAS kann diese Registry hosten – über den offiziellen `registry:2`-Container:

1. Auf der NAS **Container Manager** (DSM 7.2+; früher „Docker"-Paket) installieren.
2. Image `registry:2` herunterladen und als Container starten:
   - Port `5000` mappen
   - ein Volume für `/var/lib/registry` setzen (persistente Ablage der Images)
3. Images taggen und auf die NAS pushen (statt Schritt 2):
   ```bash
   docker tag zev-backend-service:amd64 nas.local:5000/zev-backend-service:amd64
   docker push nas.local:5000/zev-backend-service:amd64
   # analog für admin-service und frontend-service
   ```
4. Ziehen (statt Schritt 4) – in der `docker-compose.yml` die `image:`-Einträge
   auf `nas.local:5000/zev-<service>:amd64` setzen, dann:
   ```bash
   docker compose pull
   docker compose up -d
   ```

Hinweise zur Registry:

- **Architektur egal:** Eine Registry speichert beliebige Architekturen (inkl. Multi-Arch-Manifest).
- **TLS/HTTP:** Ohne gültiges Zertifikat gilt die Registry als „insecure". Dann auf
  Bau-Rechner **und** NAS in `/etc/docker/daemon.json` ergänzen und Docker neu starten:
  ```json
  { "insecure-registries": ["nas.local:5000"] }
  ```
  Sauberer: die Registry hinter den **Reverse Proxy** der NAS (DSM → Anmeldeportal →
  Reverse Proxy) mit Let's-Encrypt-Zertifikat stellen – dann entfällt der insecure-Eintrag.
- **Auth:** `registry:2` ist standardmässig offen. Bei Bedarf htpasswd-Basic-Auth aktivieren
  oder hinter den NAS-Reverse-Proxy mit Authentisierung stellen.

## Hinweise

- **`.env` wird nicht übertragen** – Secrets auf dem NAS separat anlegen, niemals ins
  Image backen.
- **Persistente Daten** (`postgres-data`, `grafana-data`) sind Volumes, keine Images.
  Bei einer Datenmigration separat per `pg_dump` bzw. Volume-Backup übertragen.
- **Plattform-Fehler** wie `exec format error` beim Start bedeuten, dass das Image für
  die falsche Architektur gebaut wurde → Schritt 1 mit korrektem `--platform` wiederholen.
- Ein nativer x86-64-Build ist deutlich schneller als ein QEMU-emulierter Cross-Build.
