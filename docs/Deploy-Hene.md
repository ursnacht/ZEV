# Deploy-Checkliste „Hene" (NAS + Raspberry Pi)

Zusammenhängende Anleitung für ein **Release über beide Seiten**: ZEV-Stack auf dem
**Synology NAS** und Reader/Publisher auf dem **Raspberry Pi** am Zählerstandort.

Die Detailanleitungen bleiben maßgeblich — diese Datei führt sie in der richtigen
Reihenfolge zusammen und ergänzt die Kontrollschritte:

| Thema | Referenz |
|-------|----------|
| NAS-Images bauen/übertragen/laden | [`docs/NAS-Images.md`](./NAS-Images.md) |
| NAS-Konfiguration (Host-URLs, `.env`) | [`Specs/NAS-Deployment.md`](../Specs/NAS-Deployment.md) |
| Pi-Update (Kurzfassung) | [`pi-gateway/README.md`](../pi-gateway/README.md) |
| Pi-Erstinstallation + systemd (vollständig) | [`Specs/Pi-Gateway-Software.md`](../Specs/Pi-Gateway-Software.md), Anhang A |
| Netzwerk/Zugriff/Diagnose | [`docs/Netzwerk-Topologie-Hene.md`](./Netzwerk-Topologie-Hene.md) |

> **Reihenfolge NAS ↔ Pi ist unkritisch.** Der Backend-Parser toleriert unbekannte
> Payload-Felder, und das Backend arbeitet ohne die optionalen Pi-Felder im Fallback
> (heutiges Verhalten). Beide Seiten lassen sich also unabhängig deployen.

---

## 0. Vorbereitung (Bau-Rechner)

- [ ] Aktueller Stand von `main` gezogen, Arbeitsverzeichnis sauber (`git status`)
- [ ] Tests grün: `mvn test` (Backend) und `npm test` in `frontend-service`
- [ ] **Welche Migrationen kommen mit?** Auf der Hene-DB sind ggf. mehrere offen —
      die lokale Entwicklungs-DB ist eine **andere** Datenbank:
      ```bash
      ls backend-service/src/main/resources/db/migration/ | tail -8
      ```
      Auf dem NAS nach dem Start gegenprüfen (Schritt 2.4).

> **Design System:** kein manueller `npm run build` nötig — `design-system` ist ein
> Maven-Modul (baut sein CSS via `frontend-maven-plugin` selbst) und wird von
> `frontend-service` als Abhängigkeit entpackt. `mvn clean package` im Root genügt.

---

## 1. NAS: Images bauen und übertragen

- [ ] Images für die NAS-Plattform bauen und exportieren (erledigt Bauen + Packen):
      ```powershell
      ./scripts/build-nas-images.ps1                              # linux/amd64, Tag "amd64"
      ./scripts/build-nas-images.ps1 -Platform linux/arm64 -Tag arm64   # ARM-NAS
      ```
- [ ] Archiv auf das NAS übertragen (Pfad wie in `NAS-Images.md`):
      ```bash
      scp zev-images-amd64.tar.gz <user>@<nas-host>:/volume1/docker/zev/
      ```

## 2. NAS: Stack aktualisieren

- [ ] Images laden:
      ```bash
      ssh <user>@<nas-host>
      gunzip -c /volume1/docker/zev/zev-images-amd64.tar.gz | docker load
      docker images | grep zev-
      ```
- [ ] Stack neu starten (Compose-Datei auf dem NAS nutzt `image:` statt `build:`):
      ```bash
      cd /volume1/docker/zev
      docker compose up -d
      ```
- [ ] **Flyway-Migrationen prüfen** — sie laufen beim Backend-Start automatisch:
      ```bash
      docker logs backend-service 2>&1 | grep -i flyway | tail -20
      docker exec postgres psql -U postgres -d zev \
        -c "SELECT version, success, installed_on FROM zev.flyway_schema_history ORDER BY installed_rank DESC LIMIT 8;"
      ```
      Erwartet: alle `success = t`. Bei `success = f` → Backend stoppen, Ursache im Log
      klären, **nie** eine angewendete Migration nachträglich ändern (s.
      `Specs/generell.md`, Abschnitt Datenbank/Migrationen).
- [ ] Health-Checks:
      ```bash
      curl -s http://<nas-host>:8090/ping
      curl -s http://<nas-host>:8090/actuator/health/mqtt      # nur mit Profil mqtt
      ```
- [ ] Frontend im Browser prüfen: Anmeldung, dann eine Seite mit neuen Übersetzungen
      öffnen. **Rohe Übersetzungs-Keys** (z.B. `SYSTEMMELDUNGEN_ERLEDIGTE_LOESCHEN`
      statt Text) bedeuten: Migration fehlt oder Translation-Cache noch warm →
      Migrationen prüfen, ggf. Backend neu starten.

## 3. Pi: Code aktualisieren

- [ ] ZIP bauen (enthält **keine** `config.yaml`/`.env`/`.venv`):
      ```powershell
      ./scripts/package-pi-gateway.ps1
      ```
- [ ] Übertragen und einspielen:
      ```bash
      scp zev-pi-gateway.zip <user>@<pi-host>:/home/pi/

      ssh <user>@<pi-host>
      sudo systemctl stop pi-gateway.service
      unzip -o zev-pi-gateway.zip -d /tmp/pi-gw-update
      sudo cp -r /tmp/pi-gw-update/pi-gateway/. /opt/pi-gateway/
      sudo chown -R pigw:pigw /opt/pi-gateway
      # nur bei geänderter requirements.txt:
      sudo -u pigw /opt/pi-gateway/.venv/bin/pip install -r /opt/pi-gateway/requirements.txt
      ```

## 4. Pi: Konfiguration nachziehen (leicht vergessen!)

`config.yaml` liegt **nicht** im ZIP und bleibt beim Update unverändert. Neue
Konfigurations-Optionen wirken daher erst, wenn sie **manuell** eingetragen werden.

- [ ] `sudo -u pigw nano /opt/pi-gateway/config.yaml`
- [ ] **Seriennummern** je Zähler (Zählertausch-Erkennung, `Specs/Zaehlertausch-Erkennung.md`):
      ```yaml
      zaehler:
        - messpunkt: "ID742-Wohnung-1"
          seriennummer: "WAGO-8791234"     # einmalig am Gerät abgelesen
      ```
      **Vor dem ersten Zählertausch alle Zähler befüllen** und mindestens ein
      Publish-Intervall laufen lassen — sonst fehlt die Vergleichsbasis und genau
      dieser Tausch bleibt unerkannt (Details: [`docs/Zaehlertausch.md`](./Zaehlertausch.md)).
- [ ] **Lese-Timeout** falls nötig (Default 5 s), global und/oder je Zähler:
      ```yaml
      read_timeout: 8s          # global
      # zaehler[].read_timeout: 20    # nur für einen langsamen Slave
      ```
- [ ] Neu starten und Logs beobachten:
      ```bash
      sudo systemctl start pi-gateway.service
      journalctl -u pi-gateway.service -f
      ```
      Erwartet: die Start-Zeile („Pi-Gateway startet: …") nennt Zähleranzahl, Intervall und
      das wirksame **`Lese-Timeout`** — so ist sofort belegt, ob die Config-Änderung greift;
      Zähler mit eigenem Wert erscheinen darunter als „Eigenes Lese-Timeout: …". Danach je
      Zyklus ein Heartbeat („letzter erfolgreicher Read/Publish", Broker verbunden) und pro
      Zähler ein erfolgreicher Read.

## 5. Ende-zu-Ende-Kontrolle

- [ ] **Rohdaten kommen an** (NAS):
      ```bash
      docker exec postgres psql -U postgres -d zev -c \
        "SELECT einheit_id, zeit, seriennummer, verarbeitet FROM zev.zaehler_rohdaten ORDER BY zeit DESC LIMIT 10;"
      ```
      `seriennummer` gefüllt → Pi-Config greift; `NULL` → Schritt 4 nachholen.
- [ ] **Aggregation erzeugt Messwerte** (läuft :05/:20/:35/:50):
      ```bash
      docker exec postgres psql -U postgres -d zev -c \
        "SELECT zeit, quelle, count(*) FROM zev.messwerte WHERE zeit > now() - interval '2 hours' GROUP BY zeit, quelle ORDER BY zeit DESC LIMIT 10;"
      ```
- [ ] **Systemmeldungen** in der Anwendung durchsehen (Seite „Systemmeldungen"):
      offene `WARN`/`ERROR` prüfen — z.B. Zähler-Ausfall (`MQTT_ZAEHLER_AUSFALL`),
      übersprungene Bilanz-Intervalle (`BILANZMODELL_INTERVALLE_UEBERSPRUNGEN`).
- [ ] **Statistik** für den aktuellen Monat öffnen: Kennzahlen-Panel plausibel?

## Rollback

- [ ] **NAS:** vorheriges Image erneut laden (`docker load` des alten Archivs) und
      `docker compose up -d`. **Achtung:** Flyway-Migrationen sind damit **nicht**
      zurückgenommen — die Schema-/Übersetzungsänderungen dieses Release sind additiv
      und mit der Vorversion verträglich (neue Spalten sind nullable, neue Keys werden
      von der alten UI nicht gelesen). Ein echter Schema-Rollback wäre eine eigene
      Migration.
- [ ] **Pi:** vorheriges ZIP erneut einspielen (Schritt 3). `config.yaml` bleibt
      erhalten; neu ergänzte Felder werden von der alten Version **ignoriert**
      (optionale Felder), es ist also kein Zurückbauen der Config nötig.

## Bekannte Stolpersteine

| Symptom | Ursache / Abhilfe |
|---------|-------------------|
| UI zeigt rohe Übersetzungs-Keys | Migration nicht gelaufen → `flyway_schema_history` prüfen; Backend neu starten |
| `seriennummer` bleibt `NULL` | Pi-`config.yaml` nicht ergänzt (Schritt 4) |
| Flyway „checksum mismatch" beim Start | Eine bereits angewendete Migration wurde nachträglich geändert → Datei auf den ausgeführten Stand zurücksetzen, Änderung als **neue** Migration (s. `Specs/generell.md`) |
| Reads schlagen mit „keine Antwort" fehl | `read_timeout` erhöhen (Schritt 4) |
| `ExceptionResponse … exception_code=11` | **Kein** Timeout-Problem: der Hub hat geantwortet. Ursache auf der RTU-Strecke bzw. am Hub — s. `docs/Netzwerk-Topologie-Hene.md`, „Modbus-Diagnose" |
| Keine Rohdaten trotz laufendem Pi | Broker-Verbindung (`/actuator/health/mqtt`), Topic/`org_id`, Einheit mit passendem `messpunkt` vorhanden? |
