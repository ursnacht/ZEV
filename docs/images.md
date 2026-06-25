# Docker-Images für Raspberry Pi bauen und übertragen

Diese Anleitung beschreibt, wie die drei lokal gebauten ZEV-Images
(`backend-service`, `admin-service`, `frontend-service`) für einen Raspberry Pi
(ARM-Architektur) gebaut, als Archiv exportiert, komprimiert auf den Pi übertragen,
dort entpackt und gestartet werden.

> Die übrigen Images (`postgres`, `keycloak`, `prometheus`, `grafana`) werden vom Pi
> direkt aus den öffentlichen Registries gezogen und müssen **nicht** übertragen werden.

## Voraussetzungen

### Auf dem Bau-Rechner

- Docker mit **Buildx** (in Docker Desktop standardmässig enthalten)
- Einmalig QEMU-Emulation für Cross-Plattform-Builds aktivieren:
  ```bash
  docker run --privileged --rm tonistiigi/binfmt --install all
  ```
- Plattform des Raspberry Pi kennen:
  - **64-Bit-OS** (Raspberry Pi OS 64-bit, Pi 3/4/5) → `linux/arm64`
  - **32-Bit-OS** (älteres Raspberry Pi OS, Pi Zero/1/2) → `linux/arm/v7`

In dieser Anleitung wird `linux/arm64` verwendet. Bei 32-Bit-OS überall
`linux/arm64` durch `linux/arm/v7` ersetzen.

### Auf dem Raspberry Pi

Raspberry Pi OS bringt **kein** Docker vorinstalliert mit. Docker Engine inklusive
`docker compose`-Plugin einmalig installieren:

```bash
curl -fsSL https://get.docker.com | sh
```

Damit `docker` ohne `sudo` läuft, den Benutzer zur `docker`-Gruppe hinzufügen
(danach ab- und wieder anmelden):

```bash
sudo usermod -aG docker $USER
```

Installation prüfen:

```bash
docker --version
docker compose version
docker run --rm hello-world
```

> Funktioniert am saubersten auf **64-Bit Raspberry Pi OS** (arm64). Das ältere,
> separate Paket `docker-compose` (mit Bindestrich) wird nicht benötigt – das
> Plugin (`docker compose`, ohne Bindestrich) reicht.

## Automatisierung (Schritte 1 + 2)

Die beiden folgenden Schritte (Bauen + Exportieren/Komprimieren) erledigt das
Skript `scripts/build-pi-images.ps1` in einem Lauf:

```powershell
# Standard: linux/arm64, Tag "arm64" -> /data/ZEV/zev-images-arm64.tar.gz
./scripts/build-pi-images.ps1

# 32-Bit-OS:
./scripts/build-pi-images.ps1 -Platform linux/arm/v7 -Tag armv7
```

Wer die Schritte manuell nachvollziehen möchte, folgt 1 + 2 unten.

## 1. Images für die Pi-Plattform bauen

Jeden Service einzeln mit Buildx für die Ziel-Plattform bauen und ins lokale
Docker laden (`--load`):

```bash
docker buildx build --platform linux/arm64 \
  -t zev-backend-service:arm64 --load ./backend-service

docker buildx build --platform linux/arm64 \
  -t zev-admin-service:arm64 --load ./admin-service

docker buildx build --platform linux/arm64 \
  -t zev-frontend-service:arm64 --load ./frontend-service
```

Prüfen, ob die Images vorhanden sind:

```bash
docker images | grep zev-
```

## 2. Images exportieren und komprimieren

Alle drei Images in **eine** gzip-komprimierte tar-Datei exportieren:

```bash
docker save zev-backend-service:arm64 zev-admin-service:arm64 zev-frontend-service:arm64 \
  | gzip > zev-images-arm64.tar.gz
```

Ergebnis: `zev-images-arm64.tar.gz`.

## 3. Datei auf den Raspberry Pi übertragen

```bash
scp zev-images-arm64.tar.gz pi@<pi-host>:/home/pi/
```

`<pi-host>` durch IP oder Hostname des Pi ersetzen (z.B. `raspberrypi.local`).

## 4. Auf dem Raspberry Pi entpacken und laden

Per SSH auf den Pi verbinden und das Archiv direkt in Docker laden
(`docker load` entpackt gzip selbst, ein separates `gunzip` ist nicht nötig):

```bash
ssh pi@<pi-host>

gunzip -c /home/pi/zev-images-arm64.tar.gz | docker load
```

Geladene Images prüfen:

```bash
docker images | grep zev-
```

## 5. Stack auf dem Raspberry Pi starten

Auf dem Pi werden folgende Dateien benötigt:

- `docker-compose.yml`
- `.env` (mit Secrets: `ANTHROPIC_API_KEY`, DB-Passwörter, Keycloak-Admin etc.)
- die referenzierten Bind-Mounts (`keycloak/realms`, `prometheus/prometheus.yml`,
  `grafana/provisioning`)

Damit Compose die übertragenen Images verwendet (statt sie neu zu bauen), in der
`docker-compose.yml` auf dem Pi für die drei Services `build:` durch `image:` ersetzen:

```yaml
  backend-service:
    image: zev-backend-service:arm64
    # build: ./backend-service   <- entfällt auf dem Pi

  admin-service:
    image: zev-admin-service:arm64

  frontend-service:
    image: zev-frontend-service:arm64
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

## Hinweise

- **`.env` wird nicht übertragen** – Secrets auf dem Pi separat anlegen, niemals ins
  Image backen.
- **Persistente Daten** (`postgres-data`, `grafana-data`) sind Volumes, keine Images.
  Bei einer Datenmigration separat per `pg_dump` bzw. Volume-Backup übertragen.
- **Plattform-Fehler** wie `exec format error` beim Start bedeuten, dass das Image für
  die falsche Architektur gebaut wurde → Schritt 1 mit korrektem `--platform` wiederholen.
- Der Build per QEMU-Emulation ist deutlich langsamer als ein nativer Build.
