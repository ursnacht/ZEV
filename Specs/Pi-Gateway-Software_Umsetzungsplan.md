# Pi-Gateway-Software – Umsetzungsplan

## Zusammenfassung

Umsetzung der **Raspberry-Pi-Reader/Publisher-Software**: liest die Zähler (Wago via Modbus TCP, BKW via gPlug) und publiziert deren **absolute Zählerstände** per MQTT gemäss dem Vertrag aus `Specs/MQTT-Integration.md`; die **Delta-/Intervall-Bildung übernimmt das Backend** (verlusttolerant). Es entsteht ein **eigenständiges Artefakt** (eigenes Repo), unabhängig von der ZEV-Java/Angular-App.

Grundlage: [`Specs/Pi-Gateway-Software.md`](./Pi-Gateway-Software.md); Vertrag: [`Specs/MQTT-Integration.md`](./MQTT-Integration.md); Topologie/Broker: [`docs/Netzwerk-Topologie-Hene.md`](../docs/Netzwerk-Topologie-Hene.md).

> **Kein ZEV-App-Feature:** Die Code-Vorlagen aus CLAUDE.md (Entity/Repository/Controller/Angular) und der `/2_umsetzung`-Pattern-Katalog gelten hier **nicht**. Sprache laut Annahme **Python** (siehe Offene Punkte); bei Go würden v. a. Setup/Build/Deploy-Phasen anders aussehen, die fachlichen Phasen bleiben.

## Betroffene Komponenten

Neues Repo/Verzeichnis, z. B. `pi-gateway/` (getrennt vom ZEV-Monorepo oder eigenes Repo):

- `pyproject.toml` / `requirements.txt` – Abhängigkeiten (`pymodbus`, `paho-mqtt`, `requests`/HTTP-Client, ggf. `pydantic` für Config)
- `config.example.yaml` / `.env.example` – Zähler-Endpunkte, `messpunkt`↔Quelle, `org_id`, Broker, Intervalle, Puffer
- `gateway/config.py` – Konfigurations-Laden/-Validierung
- `gateway/readers/modbus_reader.py` – Wago (Modbus TCP)
- `gateway/readers/gplug_reader.py` – BKW (gPlug)
- `gateway/publisher.py` – MQTT-Publish der **absoluten Zählerstände** (Topic/Payload gemäss Vertrag; keine Delta-Bildung auf dem Pi)
- `gateway/main.py` – Orchestrierung/Read-Loop, Reconnect, Heartbeat
- `deploy/pi-gateway.service` – systemd-Unit
- `deploy/mosquitto/` – **optional** lokaler Broker + Bridge-Config (Variante C, nur für Auflösungserhalt)
- `tests/` – Unit-/Integrationstests
- `README.md` – Installation/Deployment/Konfiguration

## Umsetzungsreihenfolge (Phasen)

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [ ] | 1. Projekt-Setup | Repo/Struktur, Dependencies (`pymodbus`, `paho-mqtt`, HTTP-Client), Lint/Format, venv/Packaging |
| [ ] | 2. Konfiguration | Config-Modell + Laden/Validierung: Zähler-Endpunkte, Register-Mapping, `messpunkt`↔Quelle, `org_id`, Broker-URL/Creds (Env, **keine Secrets im Code**), Lese-/Publish-Intervall, Puffer-Optionen |
| [ ] | 3. gPlug-Schnittstelle klären (Spike) | Protokoll des BKW-Zählers ermitteln (Modbus / HTTP-REST / proprietär) → Reader-Design für Phase 5 festlegen |
| [ ] | 4. Modbus-Reader (Wago) | `pymodbus`-Client, Register/Unit-ID/Skalierung aus Config, robustes Lesen (Timeouts, Teil-Reads verwerfen) |
| [ ] | 5. gPlug-Reader (BKW) | Reader gemäss Ergebnis Phase 3 |
| [ ] | 6. Zählerstand-Handling (ohne Delta) | Gelesene **absolute Stände** unverändert übernehmen (keine Delta-Bildung, keine „letzter Stand"-Persistenz); nur fehlgeschlagene/Teil-Reads verwerfen. Delta-/Reset-Erkennung liegt im Backend |
| [ ] | 7. MQTT-Publisher | Topic `zev/{orgId}/{messpunkt}/messwert`, Payload `{timestamp,zaehlerstandBezug,zaehlerstandEinspeisung}` (UTC), **QoS 0/1**, stabile Client-ID — byte-genau gemäss `MQTT-Integration.md` |
| [ ] | 8. (Optional) Pufferung | Nur falls Auflösungserhalt bei Ausfällen gewünscht: Variante C (lokaler Mosquitto + Bridge) oder in-process Queue. **Kein Muss** — absolute Stände sind verlusttolerant |
| [ ] | 9. Orchestrierung / Read-Loop | Intervall-Scheduler: Reader → Publish; Fehler isolieren (ein defekter Zähler stoppt nicht alle) |
| [ ] | 10. Betrieb & Resilienz | `systemd`-Unit (Autostart), Reconnect (Broker/Modbus/gPlug) mit Backoff, NTP-Voraussetzung, Logging + Rotation, Persistenz auf **USB-SSD** (nicht SD) |
| [ ] | 11. Monitoring / Heartbeat | „Letzter erfolgreicher Read/Publish" exponieren (Log / Status-Topic), damit stiller Ausfall erkennbar ist |
| [ ] | 12. Tests | Unit: Topic-/Payload-Format (absolute Stände), Config-Parsing, Zeitstempel-UTC, Verwerfen fehlerhafter Reads. Integration: Publish gegen Test-Broker (Mosquitto), optional Modbus-Simulator |
| [ ] | 13. Doku & Deployment | `README` (Installation, Config, systemd), Deployment auf den Pi (arm64; bei Python venv/pipx, bei Go Single-Binary) |

> **Reihenfolge-Hinweis:** Phase 3 (gPlug-Spike) vor Phase 5; Phase 7 setzt den Vertrag aus `MQTT-Integration.md` voraus (bei Vertragsänderung dort abstimmen). Phase 8 (Pufferung) ist **optional**.

## Validierungen

### Konfiguration
- Pflichtfelder vorhanden (Broker-URL/Creds, mind. ein Zähler mit `messpunkt` + `org_id`); Intervalle > 0; keine Secrets im Repo.
- `messpunkt`↔Quelle eindeutig; Register-Mapping je Wago-Gerät vorhanden.

### Laufzeit / Datenkorrektheit
- **Payload-Vertrag:** `timestamp` (ISO 8601 UTC), `zaehlerstandBezug`/`zaehlerstandEinspeisung` numerisch **≥ 0** (kumulativ); Topic exakt `zev/{orgId}/{messpunkt}/messwert`.
- **Reads:** nur vollständige, plausible Stände publizieren; Reset-/Rücksprung-Erkennung erfolgt im **Backend** (nicht auf dem Pi).
- **Zeit:** Zeitstempel in UTC; bei fehlendem NTP/Drift WARN.
- **Teil-/Fehl-Reads:** unvollständige Messungen werden verworfen, nicht publiziert.
- **Puffer (falls aktiv):** definierte Obergrenze + Policy bei Überlauf; **ohne** Puffer ist Verlust dank absoluter Stände unkritisch.
- **Idempotenz:** QoS 0/1; Duplikate sind backend-seitig unschädlich (Unique `einheit_id`+`zeit`).

## Offene Punkte / Annahmen

Aus `Specs/Pi-Gateway-Software.md` Abschnitt 8 (dokumentierte Annahmen bis zur Klärung):

- **Sprache:** angenommen **Python** (Empfehlung: `pymodbus`/`paho-mqtt`, schnelle Umsetzung). Alternative **Go** (Single-Binary) würde Phasen 1/10/13 umformen, die fachlichen Phasen bleiben.
- **Pufferung:** angenommen **keine** (absolute Stände sind verlusttolerant, Broker auf NAS = Variante B genügt). Optionale Pufferung nur für Auflösungserhalt (Variante C / Queue).
- **gPlug-Schnittstelle:** offen → eigene **Spike-Phase 3** klärt das Protokoll, bevor Phase 5 umgesetzt wird.
- **Publish-Modell:** angenommen, der Pi publiziert **je Lesezyklus die absoluten Zählerstände**; die Delta-Bildung/15-Min-Aggregation macht das Backend (`MQTT-Integration.md`, FR-6).
- **`org_id` im Topic:** internes `org_id` (BIGINT) vs. Keycloak-Alias/UUID — konsistent zu `MQTT-Integration.md` festzulegen.
- **Wago-Register-Mapping:** konkrete Register/Skalierung je Zählertyp aus der Gerätedoku (Diagnose vorab mit `mbpoll`, siehe FR-1 der Spec).
- **Reset-/Rücksprung-Policy:** liegt im Backend (`MQTT-Integration.md`); der Pi publiziert Rohstände unverändert.
- **Repo-Ort:** eigenes Repo für die Pi-Software angenommen (nicht Teil des ZEV-Monorepos); dieser Plan liegt dennoch in `Specs/` zur Nachvollziehbarkeit.
