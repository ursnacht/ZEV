# Pi-Gateway-Software – Umsetzungsplan

## Zusammenfassung

Umsetzung der **Raspberry-Pi-Reader/Publisher-Software**: liest die Zähler (Wago via Modbus TCP, BKW via gPlug) und publiziert deren **absolute Zählerstände** per MQTT gemäss dem Vertrag aus `Specs/MQTT-Integration.md`; die **Delta-/Intervall-Bildung übernimmt das Backend** (verlusttolerant). Es entsteht ein **eigenständiges Artefakt** (eigenes Repo), unabhängig von der ZEV-Java/Angular-App.

Grundlage: [`Specs/Pi-Gateway-Software.md`](./Pi-Gateway-Software.md); Vertrag: [`Specs/MQTT-Integration.md`](./MQTT-Integration.md); Topologie/Broker: [`docs/Netzwerk-Topologie-Hene.md`](../docs/Netzwerk-Topologie-Hene.md).

> **Kein ZEV-App-Feature:** Die Code-Vorlagen aus CLAUDE.md (Entity/Repository/Controller/Angular) und der `/2_umsetzung`-Pattern-Katalog gelten hier **nicht**. Sprache laut Annahme **Python** (siehe Offene Punkte); bei Go würden v. a. Setup/Build/Deploy-Phasen anders aussehen, die fachlichen Phasen bleiben.

## Betroffene Komponenten

Neues Repo/Verzeichnis, z. B. `pi-gateway/` (getrennt vom ZEV-Monorepo oder eigenes Repo):

- `pyproject.toml` / `requirements.txt` – Abhängigkeiten (`pymodbus`, `paho-mqtt`, `requests`/HTTP-Client, ggf. `pydantic` für Config)
- `config.example.yaml` / `.env.example` – **Liste beliebig vieler Zähler** (je Eintrag: Protokoll, Host/Port, Unit-ID, Register-Mapping, `messpunkt`) plus `org_id`, Broker, Intervall (Beispiel mit 3 Wago in `Pi-Gateway-Software.md` FR-5)
- `gateway/config.py` – Konfigurations-Laden/-Validierung (Zähler-Liste)
- `gateway/readers/factory.py` – Reader je `protokoll` erzeugen (erweiterbar: `modbus-tcp`, später `gplug`)
- `gateway/readers/modbus_reader.py` – Wago (Modbus TCP)
- `gateway/readers/gplug_reader.py` – BKW (gPlug, später)
- `gateway/publisher.py` – MQTT-Publish der **absoluten Zählerstände** (Topic/Payload gemäss Vertrag; keine Delta-Bildung auf dem Pi)
- `gateway/main.py` – Orchestrierung/Read-Loop, Reconnect, Heartbeat
- `deploy/pi-gateway.service` – systemd-Unit
- `deploy/mosquitto/` – **optional** lokaler Broker + Bridge-Config (Variante C, nur für Auflösungserhalt)
- `tests/` – Unit-/Integrationstests
- `README.md` – Installation/Deployment/Konfiguration

## Umsetzungsreihenfolge (Phasen)

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Projekt-Setup | Repo/Struktur, Dependencies (`pymodbus`, `paho-mqtt`, `PyYAML`), Lint/Format (ruff), venv/Packaging (`pyproject.toml`, `requirements.txt`, `.env.example`, `.gitignore`) |
| [x] | 2. Konfiguration | Config-Schema: **Zähler als Liste beliebiger Länge (10–20+)**, je Eintrag vollständig (Protokoll, Host/Port, Unit-ID, Register-Mapping, `messpunkt`); dazu `org_id`, Broker-URL/Creds (Env-Expansion `${VAR}`, **keine Secrets im Code**), Intervall (**Default 5 min**, `5m`/`30s`/`1h`). `gateway/config.py` + `config.example.yaml`; **neuer Zähler = nur Config-Eintrag** |
| [ ] | 3. gPlug-Schnittstelle klären (Spike) — **später / Erweiterung** | BKW/gPlug **nicht** im ersten Wurf; Protokoll (Modbus / HTTP-REST / proprietär) später ermitteln |
| [x] | 4. Modbus-Reader (Wago) | `pymodbus`-Client (`gateway/readers/modbus_reader.py`) für **alle Modbus-Zähler der Liste**, **Wirkenergie-Register (kWh, OBIS 1.8.0/2.8.0)** als float32 (Wortfolge/Skalierung aus Config, `struct`-Decode), robustes Lesen (Timeout, Teil-Reads verwerfen); Reader-`base.py` + `factory.py` |
| [ ] | 5. gPlug-Reader (BKW) — **später / Erweiterung** | Platzhalter `gplug_reader.py` (wirft `NotImplementedError`); vollwertig nach Ergebnis Phase 3 |
| [x] | 6. Zählerstand-Handling (ohne Delta) | Gelesene **absolute Stände** unverändert übernommen (keine Delta-Bildung, keine „letzter Stand"-Persistenz); nur fehlgeschlagene/Teil-Reads + negative Stände verworfen. Delta-/Reset-Erkennung liegt im Backend |
| [x] | 7. MQTT-Publisher | `gateway/publisher.py`: Topic `zev/{orgId}/{messpunkt}/messwert`, Payload `{timestamp,zaehlerstandBezug,zaehlerstandEinspeisung}` (lokale Zeit mit UTC-Offset, kWh gerundet auf 4 NKS), **QoS 0/1**, stabile Client-ID, paho-2.x — byte-genau gemäss `MQTT-Integration.md` |
| [x] | 8. Pufferung — **entfällt** | Entscheidung: **keine** Pufferung (Variante B genügt; absolute Stände sind verlusttolerant). Bei späterem Bedarf nachrüstbar (Variante C / Queue) |
| [x] | 9. Orchestrierung / Read-Loop | `gateway/main.py`: Intervall-Loop iteriert über die **konfigurierte Zähler-Liste (N, skaliert auf 10–20+)**: pro Zähler Reader → Publish; Fehler **je Zähler isoliert** (ein defekter stoppt nicht alle); unterbrechbares Warten via `threading.Event` |
| [x] | 10. Betrieb & Resilienz | `systemd`-Unit (`deploy/pi-gateway.service`, Autostart/`Restart=always`), MQTT-Reconnect mit Backoff (`reconnect_delay_set`), Modbus-Reconnect je Zyklus, NTP-Voraussetzung dokumentiert, Logging (journald/stdout), Signal-Handling (SIGINT/SIGTERM) |
| [x] | 11. Monitoring / Heartbeat | `gateway/heartbeat.py`: „letzter erfolgreicher Read/Publish" + Broker-Status pro Zyklus geloggt, damit stiller Ausfall erkennbar ist |
| [ ] | 12. Tests | **Separater Command** (nicht Teil der Umsetzung). Unit: Topic-/Payload-Format, Config-Parsing/-Validierung, float32-Decode, Verwerfen fehlerhafter Reads. Integration: Publish gegen Test-Broker (Mosquitto), optional Modbus-Simulator |
| [x] | 13. Doku & Deployment | `README.md` (Installation, Config, systemd-Kurzfassung, mbpoll-Diagnose); systemd-Vollanleitung in `Pi-Gateway-Software.md` Anhang A |

> **Reihenfolge-Hinweis / Erstwurf:** Erster Wurf = **nur Wago (Modbus)**; **gPlug (Phasen 3+5) und Pufferung (Phase 8) entfallen zunächst**. Phase 7 setzt den Vertrag aus `MQTT-Integration.md` voraus (bei Vertragsänderung dort abstimmen).

## Validierungen

### Konfiguration
- Pflichtfelder vorhanden (Broker-URL/Creds, `org_id`, **Zähler-Liste nicht leer**); Intervall > 0; keine Secrets im Repo.
- **`messpunkt` über die gesamte Liste eindeutig**; je Zähler-Eintrag Protokoll/Host/Port/Unit-ID + Register-Mapping (Bezug/Einspeisung) vorhanden.
- Funktioniert für **beliebige Zähleranzahl** (10–20+) rein per Konfiguration, ohne Code-Änderung.

### Laufzeit / Datenkorrektheit
- **Payload-Vertrag:** `timestamp` (ISO 8601, lokale Zeit mit UTC-Offset), `zaehlerstandBezug`/`zaehlerstandEinspeisung` numerisch **≥ 0** (kumulativ); Topic exakt `zev/{orgId}/{messpunkt}/messwert`.
- **Reads:** nur vollständige, plausible Stände publizieren; Reset-/Rücksprung-Erkennung erfolgt im **Backend** (nicht auf dem Pi).
- **Zeit:** Zeitstempel in lokaler Zeit mit UTC-Offset; bei fehlendem NTP/Drift WARN.
- **Teil-/Fehl-Reads:** unvollständige Messungen werden verworfen, nicht publiziert.
- **Puffer (falls aktiv):** definierte Obergrenze + Policy bei Überlauf; **ohne** Puffer ist Verlust dank absoluter Stände unkritisch.
- **Idempotenz:** QoS 0/1; Duplikate sind backend-seitig unschädlich (Unique `einheit_id`+`zeit`).

## Entscheidungen / Offene Punkte

**Getroffene Entscheidungen** (aus `Specs/Pi-Gateway-Software.md` Abschnitt 8):

- **Sprache: Python** (`pymodbus`/`paho-mqtt`). Go bleibt eine mögliche Alternative, ist aber nicht vorgesehen.
- **Erstwurf nur Wago (Modbus TCP); gPlug/BKW später** als Erweiterung (Phasen 3+5 verschoben).
- **Keine Pufferung** — Broker auf dem NAS (Variante B) genügt, da absolute Stände verlusttolerant sind (Phase 8 entfällt).
- **Publish-Modell:** Pi publiziert **je Lesezyklus die absoluten Zählerstände**; Delta-/15-Min-Bildung im Backend (`MQTT-Integration.md`, FR-6).
- **Publish-Intervall:** konfigurierbar, **Start mit 5 Minuten**.
- **`org_id` im Topic: internes `org_id` (BIGINT)**.
- **Register: Wirkenergie (kWh)** — **Bezug = OBIS 1.8.0** → `zaehlerstandBezug`, **Einspeisung = OBIS 2.8.0** → `zaehlerstandEinspeisung`; keine Leistung/Blind-/Scheingrössen.
- **Reset-/Rücksprung-Policy:** liegt im Backend (`MQTT-Integration.md`); der Pi publiziert Rohstände unverändert.
- **Repo-Ort:** eigenes Repo für die Pi-Software (nicht Teil des ZEV-Monorepos); dieser Plan liegt dennoch in `Specs/`.

**Verbleibend offen:**

- **Konkrete Modbus-Registeradressen + Skalierung** je Wago-Typ (aus Datenblatt; Diagnose per `mbpoll`).
- **gPlug-Protokoll** (Modbus / HTTP-REST / proprietär) — erst bei der Erweiterung relevant.
- **Mapping der Stände auf `total`/`zev`** — mit `MQTT-Integration.md` abzustimmen.
