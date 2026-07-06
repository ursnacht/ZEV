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
1. **Wago (3×) via Modbus TCP:** periodisches Lesen der relevanten Register (absolute Zählerstände Bezug/Einspeisung) pro Gerät; Host/Port/Register/Unit-ID konfigurierbar.
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
   { "timestamp": "2026-06-19T14:30:00Z", "zaehlerstandBezug": 12345.678, "zaehlerstandEinspeisung": 4321.000 }
   ```
   `timestamp` in **UTC (ISO 8601)**, Stände in kWh (kumulativ, ≥ 0).
3. QoS **0 oder 1** genügt (Verlusttoleranz durch absolute Stände); stabile Client-ID.
4. Topic/Payload müssen **byte-genau** zum Backend-Vertrag passen (Änderungen nur abgestimmt mit `MQTT-Integration.md`).

### FR-4: Übertragung & (optionale) Pufferung
1. Bei nicht erreichbarem Broker/VPN ist ein **Nachrichtenverlust unkritisch** — der nächste erfolgreich übertragene Stand schliesst die Lücke (Backend bildet die Differenz).
2. **Pufferung ist optional** (nur falls auch die *zeitliche Auflösung* während Ausfällen erhalten bleiben soll): entweder ein lokaler Mosquitto + Bridge (Variante C) oder eine einfache in-process/Disk-Queue. **Kein** zwingender Store-and-Forward.
3. Ohne Pufferung reicht ein direkter Publish an den NAS-Broker (Variante B).

### FR-5: Konfiguration
1. Alle Parameter über Konfigurationsdatei/Env: Zähler-Endpunkte + Register-Mapping, `messpunkt`↔Quelle, `org_id`, Broker-URL/Credentials, Lese-/Publish-Intervall, optionale Puffer-Optionen.
2. **Keine Secrets im Code/Repo** (Broker-Passwort etc. via Env/gesicherte Datei).

### FR-6: Betrieb & Resilienz
1. Läuft als **`systemd`-Service** mit Autostart nach Reboot/Stromausfall.
2. Automatischer **Reconnect** (Broker/Modbus/gPlug) mit Backoff.
3. **NTP-Zeitsynchronisation** vorausgesetzt (korrekte Zeitstempel sind abrechnungskritisch).
4. Strukturiertes Logging mit Rotation; ein lokaler Puffer (falls genutzt) **nicht** auf die SD-Karte (Wear) — USB-SSD.

### FR-7: Monitoring / Heartbeat
1. „Letzter erfolgreicher Read"- und „letzter erfolgreicher Publish"-Zeitstempel exponieren (z.B. Logfile, lokaler Statusendpunkt oder eigenes MQTT-Status-Topic), damit stiller Ausfall erkennbar ist (vgl. `docs/Netzwerk-Topologie-Hene.md`, Punkt „Monitoring gegen stillen Ausfall").

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

* [ ] Der Dienst liest alle konfigurierten Zähler (3× Wago Modbus TCP, 1× BKW gPlug) im konfigurierten Intervall aus.
* [ ] Der Dienst publiziert die **absoluten Zählerstände** unverändert (keine Delta-Berechnung, keine „letzter Stand"-Persistenz).
* [ ] Publizierte Topic-/Payload-Struktur entspricht exakt `MQTT-Integration.md` (`zev/{orgId}/{messpunkt}/messwert`, `{timestamp,zaehlerstandBezug,zaehlerstandEinspeisung}`).
* [ ] Bei Broker-/VPN-Ausfall entsteht **kein Datenverlust** in der Gesamtsumme (nächster übertragener Stand schliesst die Lücke); optionale Pufferung erhält zusätzlich die Auflösung.
* [ ] Unvollständige/fehlgeschlagene Reads werden verworfen (nicht publiziert).
* [ ] Dienst startet nach Reboot automatisch (`systemd`) und verbindet sich selbstständig neu.
* [ ] Zeitstempel sind UTC und korrekt (NTP); driftende Uhr wird als Problem erkennbar.
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
* Der bestehende SFTP-Weg kann parallel bestehen bleiben (Umstellung/Abschaltung separat).

## 5. Edge Cases & Fehlerbehandlung
| Szenario | Verhalten |
|----------|-----------|
| Zähler (Modbus/gPlug) nicht erreichbar | Loggen, diesen Zyklus überspringen, Retry (nächster Stand schliesst die Lücke) |
| Teilweiser/fehlgeschlagener Read | Messwert dieser Quelle verwerfen, nicht publizieren |
| Zählerüberlauf / Rücksprung / Zählerwechsel | Rohstand wird publiziert; Erkennung/Behandlung im **Backend** (Differenz < 0) |
| Broker/VPN nicht erreichbar | Verlust unkritisch; optional puffern (Variante C / Queue) für Auflösung |
| Puffer voll (falls Pufferung aktiv) | Definiertes Verhalten (z.B. älteste verwerfen + WARN) |
| Uhr driftet / kein NTP | WARN; Zeitstempel bleiben UTC; Betreiber-Alarm |
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
* [ ] **Technologie/Sprache:** Empfehlung **Python** (`pymodbus`, `paho-mqtt`, HTTP-Client) für schnelle Umsetzung; **Go** als Alternative (ein statisches arm64-Binary, minimaler Footprint, einfachstes Deployment). Java auf dem Pi eher nicht (JVM-Overhead). → Entscheiden.
* [ ] **gPlug-Schnittstelle (BKW):** Modbus, HTTP/REST oder proprietär? Bestimmt Libs und Aufwand.
* [ ] **Optionale Pufferung:** überhaupt nötig (nur für Auflösungserhalt bei Ausfällen)? Falls ja: Variante C (lokaler Broker + Bridge) oder in-process Queue?
* [ ] **Welche Register/Stände** werden publiziert (Bezug/Einspeisung, ggf. ZEV-Register) und wie im Backend auf `total`/`zev` gemappt? (Spiegelt Offene Frage in `MQTT-Integration.md`.)
* [ ] **`org_id` im Topic:** internes `org_id` (BIGINT) vs. Keycloak-Alias/UUID — konsistent zu `MQTT-Integration.md` festlegen.
* [ ] **Register-Mapping Wago:** konkrete Modbus-Register/Skalierung je Zählertyp; `messpunkt`↔Gerät-Zuordnung.
* [ ] **Publish-Intervall:** wie oft absolute Stände senden (bestimmt die erreichbare zeitliche Auflösung)?
