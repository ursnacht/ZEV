# Pi-Gateway-Software

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Eine Software auf dem **Raspberry Pi** am Zählerstandort liest die Stromzähler aus und **publiziert deren absolute Zählerstände** per MQTT an den Broker. Sie ist das **Publisher-Gegenstück** zum Backend-Subscriber aus `Specs/MQTT-Integration.md`.
* **Warum machen wir das:**
  - Automatisierte, zeitnahe Datenerfassung statt manuellem CSV-Upload bzw. dem heutigen File/SFTP-Weg.
  - Klar getrennte Verantwortung: der MQTT-Broker verteilt nur Nachrichten, das **Auslesen der Zähler ist Aufgabe dieses separaten Prozesses** (siehe Rollentrennung in `docs/Netzwerk-Topologie-Hene.md`).
* **Aktueller Stand:**
  - Der Pi liest die Zähler physisch bereits aus (**3× Wago via Modbus TCP**, **1× BKW via gPlug**) und stellt Daten heute per **SFTP** bereit (`docs/Netzwerk-Topologie-Hene.md`).
  - Es existiert **noch keine** MQTT-Publisher-Software. In `Specs/MQTT-Integration.md` ist diese Pi-Software bewusst **Out of Scope** – dieses Dokument schliesst die Lücke.
  - Ziel-Topologie: Broker auf dem NAS (Variante B). Ein lokaler Broker + Bridge (Variante C) ist **nicht erforderlich**, da absolute Zählerstände verlusttolerant sind (siehe unten).

> **Designentscheidung – absolute Zählerstände statt Deltas:** Der Pi überträgt die **absoluten (kumulativen) Zählerstände**, **nicht** berechnete Deltas. Die Delta-/Intervall-Bildung erfolgt im **Backend** (`Specs/MQTT-Integration.md`, FR-6). Vorteil: **verlusttolerant** — geht eine Nachricht verloren, bleibt die Gesamtsumme korrekt (die Differenz zweier empfangener Stände deckt die Lücke), es sinkt nur kurz die Auflösung. Dadurch ist der Pi **weitgehend zustandslos** (keine „letzter Stand"-Persistenz nötig) und QoS/Pufferung entspannen sich deutlich.

> Dieses Dokument beschreibt **Pi-seitige Software** (eigenes Repo/Artefakt), **nicht** das ZEV-Backend. App-Rollen (`zev_user`/`zev_admin`), i18n und die DB-Mandantenfähigkeit gelten hier **nicht** direkt; die Mandantentrennung erfolgt über Topic-`org_id` + Broker-ACL (siehe NFR-2).

## 2. Funktionale Anforderungen (FR)

### FR-1: Zähler auslesen
1. **Wago via Modbus TCP (aktuell 3 Geräte, Liste beliebig erweiterbar — siehe FR-5):** periodisches Lesen der relevanten Register — die **Wirkenergie**-Zählerstände in **kWh** (Bezug/Einspeisung), **nicht** Leistung (kW) oder Blind-/Scheinenergie; pro Gerät Host/Port/Register/Unit-ID konfigurierbar.
2. **BKW via gPlug:** Auslesen über die gPlug-Schnittstelle (Protokoll siehe Offene Fragen).
3. Jede physische Quelle ist einer **Einheit** über deren **`messpunkt`** zugeordnet (Konfiguration, kein Rückgriff auf interne DB-IDs).
4. Lese-/Publish-Intervall konfigurierbar (Default z.B. 1–5 min).

> **Manuelles Auslesen / Diagnose (CLI):** Zum Prüfen von Verkabelung/Registern
> eignet sich `mbpoll` (`sudo apt install mbpoll`). „Adresse 1" = Modbus
> **Unit-/Slave-ID** (die Geräte-**IP** ist bei Modbus TCP zusätzlich nötig).
> ```bash
> # Modbus TCP, Unit-ID 1, 10 Holding-Register ab Register 1, einmalig:
> mbpoll -a 1 -t 4 -r 1 -c 10 -1 <zaehler-ip> -p 502
> # 32-bit-Float-Werte (ggf. -B fuer Big-Endian-Words):
> mbpoll -a 1 -t 4:float -r 1 -c 4 -1 <zaehler-ip>
> # Modbus RTU (serielle Leitung) statt TCP:
> mbpoll -m rtu -a 1 -b 9600 -P none -t 4 -r 1 -c 10 -1 /dev/ttyUSB0
> ```
> `-a` Unit-ID, `-t 4`/`3` Holding/Input Register, `-r` Start, `-c` Anzahl,
> `-1` One-Shot. Konkrete Register/Skalierung stammen aus der Zähler-Doku
> (siehe Offene Frage „Register-Mapping Wago"). BKW/gPlug: falls HTTP-API,
> Diagnose per `curl` statt `mbpoll`. Alternativen: `mbtget`, kurzes
> `pymodbus`-Skript.

### FR-2: Zählerstände übertragen (keine Delta-Bildung auf dem Pi)
1. Die Software publiziert die **gelesenen absoluten Zählerstände** unverändert — **keine** Delta-Berechnung, **keine** Persistenz eines „letzten Standes" (Pi ist zustandslos).
2. **Reset/Überlauf/Zählertausch** werden **nicht** auf dem Pi behandelt; der Rohstand wird publiziert, das Backend erkennt einen Rücksprung bei der Differenzbildung (`MQTT-Integration.md`, FR-6).
3. Nur **unvollständige/fehlgeschlagene Reads** werden verworfen (nicht publiziert), damit keine Scheinwerte entstehen.

### FR-3: MQTT-Publish (Vertrag gemäss `MQTT-Integration.md`)
1. **Topic:** `zev/{orgId}/{messpunkt}/messwert` (exakt wie Backend-Subscription `zev/+/+/messwert`).
2. **Payload (JSON, absolute Stände):**
   ```json
   { "timestamp": "2026-06-19T14:30:00+02:00", "zaehlerstandBezug": 12345.678, "zaehlerstandEinspeisung": 4321.000 }
   ```
   `timestamp` in **lokaler Zeit mit UTC-Offset (ISO 8601)** (z. B. `+02:00`; eindeutig, kein DST-Problem), Stände in kWh (kumulativ, ≥ 0). Voraussetzung: korrekte lokale Zeitzone + NTP auf dem Pi (FR-6.3).
3. QoS **0 oder 1** genügt (Verlusttoleranz durch absolute Stände); stabile Client-ID.
4. Topic/Payload müssen **byte-genau** zum Backend-Vertrag passen (Änderungen nur abgestimmt mit `MQTT-Integration.md`).

### FR-4: Übertragung & (optionale) Pufferung
1. Bei nicht erreichbarem Broker/VPN ist ein **Nachrichtenverlust unkritisch** — der nächste erfolgreich übertragene Stand schliesst die Lücke (Backend bildet die Differenz).
2. **Pufferung ist optional** (nur falls auch die *zeitliche Auflösung* während Ausfällen erhalten bleiben soll): entweder ein lokaler Mosquitto + Bridge (Variante C) oder eine einfache in-process/Disk-Queue. **Kein** zwingender Store-and-Forward.
3. Ohne Pufferung reicht ein direkter Publish an den NAS-Broker (Variante B).

### FR-5: Konfiguration
1. **Flexible Zähler-Liste:** Die Zähler werden als **Liste beliebiger Länge** konfiguriert — typisch **10–20 oder mehr**, keine feste Anzahl im Code. Jeder Eintrag beschreibt einen Zähler vollständig (Protokoll, Host/IP, Port, Unit-ID, Register-Mapping, `messpunkt`).
2. **Neuer Zähler = nur ein Konfigurations-Eintrag** (kein Code-Change, kein Rebuild).
3. Weitere Parameter über Konfigurationsdatei/Env: `org_id`, Broker-URL/Credentials, Lese-/Publish-Intervall, optionale Puffer-Optionen.
4. **Keine Secrets im Code/Repo** (Broker-Passwort etc. via Env/gesicherte Datei).

> **Beispiel-Konfiguration (3 Wago-Zähler; ohne gPlug — folgt später):**
> ```yaml
> # config.yaml auf dem Pi (Secrets via Env)
> org_id: 42
> publish_interval: 5m
> broker:
>   url: tcp://nas.local:1883        # bzw. tls://nas.local:8883
>   username: ${MQTT_USERNAME}
>   password: ${MQTT_PASSWORD}
>   qos: 1
>
> # Beliebig viele Zähler — hier 3 (Wago, Modbus TCP). Weitere einfach anhängen.
> zaehler:
>   - messpunkt: "ID742-Wohnung-1"
>     protokoll: modbus-tcp
>     host: 192.168.10.11
>     port: 502
>     unit_id: 1
>     register:                       # Wirkenergie in kWh (Beispielwerte!)
>       bezug:       { addr: 600C, typ: float32, wortfolge: big, skalierung: 1.0 }   # OBIS 1.8.0
>       einspeisung: { addr: 6018, typ: float32, wortfolge: big, skalierung: 1.0 }   # OBIS 2.8.0
>   - messpunkt: "ID742-Wohnung-2"
>     protokoll: modbus-tcp
>     host: 192.168.10.12
>     port: 502
>     unit_id: 1
>     register:
>       bezug:       { addr: 600C, typ: float32, wortfolge: big, skalierung: 1.0 }
>       einspeisung: { addr: 6018, typ: float32, wortfolge: big, skalierung: 1.0 }
>   - messpunkt: "ID742-Allgemein"
>     protokoll: modbus-tcp
>     host: 192.168.10.13
>     port: 502
>     unit_id: 1
>     register:
>       bezug:       { addr: 600C, typ: float32, wortfolge: big, skalierung: 1.0 }
>       einspeisung: { addr: 6018, typ: float32, wortfolge: big, skalierung: 1.0 }
>
> # Später (Erweiterung): BKW als weiterer Eintrag mit  protokoll: gplug  (siehe Offene Fragen).
> ```
> Die Register-`addr`/`typ`/`skalierung` sind **beispielhaft** und müssen dem Wago-Datenblatt entsprechen (Diagnose per `mbpoll`, siehe FR-1). Der Skalierungs-/OBIS-Bezug ist unter „Register-Mapping Wago" (Offene Fragen) beschrieben.

### FR-6: Betrieb & Resilienz
1. Läuft als **`systemd`-Service** mit Autostart nach Reboot/Stromausfall.
2. Automatischer **Reconnect** (Broker/Modbus/gPlug) mit Backoff.
3. **NTP-Zeitsynchronisation** vorausgesetzt (korrekte Zeitstempel sind abrechnungskritisch).
4. Strukturiertes Logging mit Rotation; ein lokaler Puffer (falls genutzt) **nicht** auf die SD-Karte (Wear) — USB-SSD.

### FR-7: Monitoring / Heartbeat
1. „Letzter erfolgreicher Read"- und „letzter erfolgreicher Publish"-Zeitstempel exponieren (z.B. Logfile, lokaler Statusendpunkt oder eigenes MQTT-Status-Topic), damit stiller Ausfall erkennbar ist (vgl. `docs/Netzwerk-Topologie-Hene.md`, Punkt „Monitoring gegen stillen Ausfall").

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

* [ ] Der Dienst liest **alle konfigurierten Zähler** (beliebige Anzahl; aktuell 3× Wago Modbus TCP, gPlug folgt später) im konfigurierten Intervall aus.
* [ ] Ein zusätzlicher Zähler lässt sich **allein per Konfigurations-Eintrag** ergänzen (kein Code-Change/Rebuild).
* [ ] Der Dienst publiziert die **absoluten Zählerstände** unverändert (keine Delta-Berechnung, keine „letzter Stand"-Persistenz).
* [ ] Publizierte Topic-/Payload-Struktur entspricht exakt `MQTT-Integration.md` (`zev/{orgId}/{messpunkt}/messwert`, `{timestamp,zaehlerstandBezug,zaehlerstandEinspeisung}`).
* [ ] Bei Broker-/VPN-Ausfall entsteht **kein Datenverlust** in der Gesamtsumme (nächster übertragener Stand schliesst die Lücke); optionale Pufferung erhält zusätzlich die Auflösung.
* [ ] Unvollständige/fehlgeschlagene Reads werden verworfen (nicht publiziert).
* [ ] Dienst startet nach Reboot automatisch (`systemd`) und verbindet sich selbstständig neu.
* [ ] Zeitstempel sind lokale Zeit mit korrektem UTC-Offset (NTP + Zeitzone); driftende Uhr wird als Problem erkennbar.
* [ ] Keine Secrets im Code/Repo; Broker-Zugangsdaten via Env/Config.
* [ ] „Letzter erfolgreicher Read/Publish" ist auslesbar (Heartbeat).

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance / Ressourcen
* Läuft schlank auf einem Raspberry Pi (arm64); ein Lesezyklus über alle Zähler deutlich innerhalb des Intervalls.

### NFR-2: Sicherheit & Mandantentrennung
* **Keine ZEV-App-Rollen** (`zev_user`/`zev_admin`) — dies ist Maschinen-zu-Broker-Kommunikation. Authentisierung am **MQTT-Broker** (Username/Passwort); **ACL** stellt sicher, dass der Pi nur in den Topic-Teilbaum seiner **`org_id`** publizieren darf (Mandantentrennung, konsistent zu `MQTT-Integration.md`).
* **TLS (Port 8883)** empfohlen, zusätzlich zum **VPN**-Tunnel (Defense-in-Depth).
* Broker-Ports nur über den VPN-Pfad erreichbar; keine Inbound-Exposition am Zählerstandort.
* Zugriff auf das lokale Netzsegment der Zähler minimieren (Modbus TCP ist unverschlüsselt/ohne Auth — vgl. Segmentierungs-Hinweis der Topologie-Doku).

### NFR-3: Zuverlässigkeit
* **Verlusttoleranz durch absolute Zählerstände** — eine verlorene Nachricht führt nicht zu Datenverlust; daher genügt QoS 0/1 und **keine** zwingende Pufferung.
* Duplikate sind backend-seitig idempotent (Unique `einheit_id`+`zeit`).
* Selbstheilung: Reconnect für Broker, Modbus und gPlug.

### NFR-4: Kompatibilität
* Payload/Topic-Vertrag mit `MQTT-Integration.md` bleibt stabil; Änderungen nur koordiniert.
* Läuft auf Raspberry Pi OS (arm64); Deployment ohne Build auf dem Pi bevorzugt (siehe Sprachwahl, Offene Fragen).

## 5. Edge Cases & Fehlerbehandlung
| Szenario | Verhalten |
|----------|-----------|
| Zähler (Modbus/gPlug) nicht erreichbar | Loggen, diesen Zyklus überspringen, Retry (nächster Stand schliesst die Lücke) |
| Teilweiser/fehlgeschlagener Read | Messwert dieser Quelle verwerfen, nicht publizieren |
| Zählerüberlauf / Rücksprung / Zählerwechsel | Rohstand wird publiziert; Erkennung/Behandlung im **Backend** (Differenz < 0) |
| Broker/VPN nicht erreichbar | Verlust unkritisch; optional puffern (Variante C / Queue) für Auflösung |
| Puffer voll (falls Pufferung aktiv) | Definiertes Verhalten (z.B. älteste verwerfen + WARN) |
| Uhr driftet / kein NTP | WARN; Zeitstempel bleiben lokale Zeit mit Offset; Betreiber-Alarm |
| Doppelter Publish (nach Reconnect) | Backend behandelt idempotent (Unique-Constraint) |
| Prozessabsturz/Neustart | `systemd` startet neu; kein „letzter Stand" nötig (zustandslos) |

## 6. Abhängigkeiten & betroffene Funktionalität
* **Vertrag/Backend:** `Specs/MQTT-Integration.md` (Topic, Payload, QoS, Delta-Bildung/Aggregation) — **maßgeblich**.
* **Topologie/Broker:** `docs/Netzwerk-Topologie-Hene.md` (Broker-Setup NAS/Pi, Varianten A/B/C, VPN, SD-Wear, Monitoring).
* **Hardware/Protokolle:** Wago (Modbus TCP), BKW (gPlug), Mosquitto-Broker, VPN (WireGuard).
* **Voraussetzungen:** Einheiten inkl. `messpunkt` sind im ZEV-System angelegt; `org_id` bekannt; VPN eingerichtet.
* **Kein** ZEV-App-Code betroffen (separates Pi-Artefakt); keine DB-/i18n-/Frontend-Änderung.

## 7. Abgrenzung / Out of Scope
* **Delta-/Intervall-Bildung und Aggregation** → **Backend** (`MQTT-Integration.md`, FR-6). Der Pi liefert nur absolute Stände.
* **Backend-Ingestion** (MQTT-Subscriber, Rohdaten-Tabelle) → `MQTT-Integration.md`.
* **Broker-Installation/Betrieb** (NAS/Pi) → `docs/Netzwerk-Topologie-Hene.md`.
* **VPN-Einrichtung**, Netzwerk-Segmentierung der Zähler.
* **Bidirektionale Steuerung** (Befehle an Zähler), automatische Geräteregistrierung.
* **Keine UI**; Konfiguration per Datei/Env.

## 8. Offene Fragen
* [ ] **Technologie/Sprache:** Empfehlung **Python** (`pymodbus`, `paho-mqtt`, HTTP-Client) für schnelle Umsetzung; **Go** als Alternative (ein statisches arm64-Binary, minimaler Footprint, einfachstes Deployment). Java auf dem Pi eher nicht (JVM-Overhead). → Python
* [ ] **gPlug-Schnittstelle (BKW):** Modbus, HTTP/REST oder proprietär? Bestimmt Libs und Aufwand. → damit noch warten. Kommt später als Erweiterung.
* [ ] **Optionale Pufferung:** überhaupt nötig (nur für Auflösungserhalt bei Ausfällen)? Falls ja: Variante C (lokaler Broker + Bridge) oder in-process Queue? → keine Pufferung
* [ ] **Welche Register/Stände** werden publiziert (Bezug/Einspeisung, ggf. ZEV-Register) und wie im Backend auf `total`/`zev` gemappt? (Spiegelt Offene Frage in `MQTT-Integration.md`.) → **Wirkenergie** (Active Energy, kumulativ in kWh): **Bezug = OBIS 1.8.0** → `zaehlerstandBezug`, **Einspeisung = OBIS 2.8.0** → `zaehlerstandEinspeisung`. **Keine** Wirk-/Blind-/Scheinleistung (kW/kvar/kVA) und keine Blind-/Scheinenergie. Das Mapping der Stände auf `messwerte` ist entschieden (`MQTT-Integration.md` FR-6.4): `total = ΔBezug − ΔEinspeisung` (vorzeichenbehaftet), `zev = 0` (Sentinel), `zev_calculated` via Solarverteilung. Für den Pi ändert sich dadurch **nichts** — er publiziert weiterhin nur die absoluten Bezug-/Einspeisung-Stände.
* [ ] **`org_id` im Topic:** internes `org_id` (BIGINT) vs. Keycloak-Alias/UUID — konsistent zu `MQTT-Integration.md` festlegen. → interne org_id
* [ ] **Register-Mapping Wago:** konkrete Modbus-Register/Skalierung je Zählertyp; `messpunkt`↔Gerät-Zuordnung. → Semantik geklärt: **Wirkenergie kWh** (OBIS 1.8.0 Bezug / 2.8.0 Einspeisung, s. o.). Die **konkreten Modbus-Registeradressen + Skalierung** je Wago-Typ stammen aus dem Gerätedatenblatt (Diagnose per `mbpoll`); `messpunkt`↔Gerät-Zuordnung per Config.
* [ ] **Publish-Intervall:** wie oft absolute Stände senden (bestimmt die erreichbare zeitliche Auflösung)? → konfigurierbar. Wir beginnen mit 5 Minuten.

## Anhang A: systemd-Registrierung (Raspberry Pi OS)

Anleitung, um das Python-Skript als `systemd`-Dienst mit Autostart zu betreiben
(erfüllt FR-6.1/6.2). Annahmen: Raspberry Pi OS (arm64, systemd), Code liegt in
`/opt/pi-gateway`, Ausführung als dedizierter, unprivilegierter Nutzer in einem
virtuellen Environment (venv). Secrets kommen **nicht** ins Unit-File, sondern in
eine root-only Env-Datei (FR-5.4).

### A.1 Dienst-Nutzer und Verzeichnisse anlegen

```bash
# Dedizierter Systemnutzer ohne Login und ohne Home
sudo useradd --system --no-create-home --shell /usr/sbin/nologin pigw

# Code- und Konfig-Verzeichnis
sudo mkdir -p /opt/pi-gateway
sudo chown -R pigw:pigw /opt/pi-gateway
# ... Code nach /opt/pi-gateway kopieren (git clone / rsync / Release-Tarball) ...
```

### A.2 Virtuelles Environment und Abhängigkeiten

```bash
sudo -u pigw python3 -m venv /opt/pi-gateway/.venv
sudo -u pigw /opt/pi-gateway/.venv/bin/pip install --upgrade pip
sudo -u pigw /opt/pi-gateway/.venv/bin/pip install -r /opt/pi-gateway/requirements.txt
```

### A.3 Konfiguration und Secrets

```bash
# Anwendungs-Konfiguration (ohne Secrets, siehe FR-5)
sudo install -o pigw -g pigw -m 640 config.yaml /opt/pi-gateway/config.yaml

# Broker-Zugangsdaten als root-only Env-Datei (NICHT ins Unit-File!)
sudo tee /etc/pi-gateway.env >/dev/null <<'EOF'
MQTT_USERNAME=pi-org42
MQTT_PASSWORD=<broker-passwort>
EOF
sudo chmod 600 /etc/pi-gateway.env
sudo chown root:root /etc/pi-gateway.env
```

### A.4 systemd-Unit anlegen

Datei `/etc/systemd/system/pi-gateway.service` (im Repo als
`deploy/pi-gateway.service` versioniert):

```ini
[Unit]
Description=ZEV Pi-Gateway (Zähler auslesen und per MQTT publizieren)
Documentation=https://github.com/ursnacht/ZEV/blob/main/Specs/Pi-Gateway-Software.md
# Netzwerk (und ggf. VPN) muss vor dem Start verfügbar sein:
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=pigw
Group=pigw
WorkingDirectory=/opt/pi-gateway
# Secrets aus root-only Env-Datei laden:
EnvironmentFile=/etc/pi-gateway.env
ExecStart=/opt/pi-gateway/.venv/bin/python -m gateway.main --config /opt/pi-gateway/config.yaml
# Automatischer Neustart nach Absturz/Reboot (FR-6.1/6.2):
Restart=always
RestartSec=10
# Logs an journald (kein eigenes Logfile-Management nötig):
StandardOutput=journal
StandardError=journal
SyslogIdentifier=pi-gateway

# Härtung (optional, empfohlen):
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true
ReadWritePaths=/opt/pi-gateway

[Install]
WantedBy=multi-user.target
```

> **Hinweis Pufferung/USB-SSD:** Wird später doch eine lokale Puffer-/Statusdatei
> genutzt, deren Pfad (auf USB-SSD, **nicht** SD-Karte — FR-6.4) unter
> `ReadWritePaths=` ergänzen.

### A.5 Aktivieren, starten, prüfen

```bash
# Unit-Datei aus dem Repo installieren (oder direkt anlegen)
sudo install -m 644 deploy/pi-gateway.service /etc/systemd/system/pi-gateway.service

sudo systemctl daemon-reload
sudo systemctl enable pi-gateway.service     # Autostart nach Reboot
sudo systemctl start  pi-gateway.service     # sofort starten

# Status und Logs
systemctl status pi-gateway.service
journalctl -u pi-gateway.service -f          # Live-Log (Ctrl+C beendet)
journalctl -u pi-gateway.service --since "1 hour ago"
```

### A.6 Update / Neustart / Deaktivieren

```bash
# Nach Code-Update:
sudo systemctl restart pi-gateway.service

# Nach Änderung der Unit-Datei zusätzlich:
sudo systemctl daemon-reload && sudo systemctl restart pi-gateway.service

# Dienst dauerhaft abschalten:
sudo systemctl disable --now pi-gateway.service
```

> **Zeitsynchronisation & Zeitzone (FR-6.3):** Korrekte Zeitstempel sind
> abrechnungskritisch. Sicherstellen, dass NTP läuft **und die lokale Zeitzone gesetzt ist**
> (`timedatectl status` → „System clock synchronized: yes“ und korrekte „Time zone“;
> ggf. `sudo timedatectl set-ntp true` bzw. `sudo timedatectl set-timezone Europe/Zurich`).
> Der publizierte Zeitstempel trägt den lokalen UTC-Offset.
