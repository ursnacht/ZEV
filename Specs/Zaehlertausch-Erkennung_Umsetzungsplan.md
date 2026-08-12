# Zählertausch-Erkennung – Umsetzungsplan

## Zusammenfassung
Der MQTT-Payload wird um ein optionales Feld `seriennummer` erweitert (Wert aus der Pi-Config je `messpunkt`), das je Rohdatensatz in einer neuen nullable Spalte `zaehler_rohdaten.seriennummer` gespeichert wird. Umgesetzt wird **beide Seiten in diesem Repo**: Backend (`backend-service`) und Publisher (`pi-gateway`, Python). Der Aggregations-Job vergleicht die Seriennummer von Referenz- und End-Stand eines 15-Min-Intervalls und setzt bei einem Wechsel eine **neue Baseline** (kein Delta über die Tausch-Grenze, kein Messwert für das Übergangsintervall) – damit wird ein Zählertausch **richtungsunabhängig** erkannt und der bisherige Blindspot (neuer Zähler startet **höher** → stiller Bogus-Wert) geschlossen. Fehlt die Seriennummer, gilt exakt das heutige Verhalten (Delta + Reset-Guard) – rein additiv und rückwärtskompatibel.

## Betroffene Komponenten

### Backend
| Datei | Änderung |
|-------|----------|
| `resources/db/migration/V92__Add_Seriennummer_To_Zaehler_Rohdaten.sql` | **neu** — `ALTER TABLE zev.zaehler_rohdaten ADD COLUMN seriennummer VARCHAR(64)` + `COMMENT ON COLUMN` (Stil wie `V72`) |
| `dto/ZaehlerMesswertPayloadDTO.java` | Feld `seriennummer` (String) + Getter/Setter |
| `entity/ZaehlerRohdaten.java` | Feld `seriennummer` + `@Column(name = "seriennummer", length = 64)` + Getter/Setter. **Konstruktor unverändert** (Setter im Ingest verwenden), damit bestehende Aufrufer/Tests unberührt bleiben |
| `service/MqttIngestService.java` | Normalisierung (`trim` → leer=`null` → auf 64 kürzen) in einer privaten Hilfsmethode; Wert in `upsertRohdaten` per Setter an **jede** betroffene Einheit schreiben (geteilter Bilanzmesspunkt) |
| `service/ZaehlerAggregationService.java` | In `verarbeiteIntervall`: Serien-Vergleich (`equals`, case-sensitive) → bei Wechsel `return false` (Baseline-Reset, kein Messwert) + `log.warn`; Rollout-Marker `log.info` beim Übergang `null` → gesetzt |

### Tests
| Datei | Änderung |
|-------|----------|
| `service/MqttIngestServiceTest.java` | Normalisierung: mit/ohne Serie, Whitespace, nur-Whitespace, >64 Zeichen (Nachricht darf **nie** verworfen werden), geteilter Bilanzmesspunkt (Serie an beiden Einheiten) |
| `service/ZaehlerAggregationServiceTest.java` | Serien-Wechsel (höher/niedriger startend), gleiche Serie, Fallback bei `null`, Rollout-Marker, Rücktausch A→B→A, Offline-Lücke, bestehender Messwert bleibt unverändert. Bestehende Helper nutzen: `stubStaende(referenz, letzter)`, `rohdaten(bezug, einspeisung)` → **Overload** `rohdaten(bezug, einspeisung, seriennummer)` ergänzen |
| `repository/ZaehlerRohdatenRepositoryIT.java` | Persistenz der neuen Spalte (Wert + `NULL`), Upsert-Verhalten je `(einheit_id, zeit)` unverändert |

### Pi-Gateway (`pi-gateway/`, Python – **in diesem Repo**)
| Datei | Änderung |
|-------|----------|
| `gateway/models.py` | `MeterConfig`: Feld `seriennummer: str \| None = None`; `MeterReading`: Feld `seriennummer: str \| None = None` (trägt den Wert zum Publisher) |
| `gateway/config.py` | In `_parse_meters`: `entry.get("seriennummer")` lesen – **optional**, Typprüfung `str`, `strip()`, leer → `None`, Länge > 64 → `ConfigError` (frühe, klare Fehlermeldung statt stiller Kürzung im Backend) |
| `gateway/publisher.py` | `_to_payload`: `"seriennummer"` **nur aufnehmen, wenn gesetzt** – so bleibt der Payload für unkonfigurierte Zähler byte-identisch zu heute |
| `gateway/readers/modbus_reader.py` | `MeterReading(...)` um `seriennummer=self._config.seriennummer` erweitern (Zeile ~62) |
| `gateway/readers/sim_reader.py` | dito (Zeile ~64) |
| `config.example.yaml`, `config.sim.example.yaml`, `config.sim.yaml` | `seriennummer:` je `zaehler`-Eintrag ergänzen (mit Kommentar „beim Tausch aktualisieren!") |
| `README.md` | Payload-Beispiel (Zeile ~39) + Config-Doku um das optionale Feld ergänzen |

> `gateway/readers/gplug_reader.py` konstruiert derzeit kein `MeterReading` (noch nicht implementiert) → keine Änderung nötig.

### Specs / Doku (Nachzug)
| Datei | Änderung |
|-------|----------|
| `Specs/MQTT-Integration.md` | FR-3: `seriennummer` als **optionales** Feld in Payload-Beispiel + Feldtabelle; Wortlaut „byte-genau"/„exakt" auf „additive optionale Felder erlaubt" lockern (Review-Minor #3) |
| `Specs/Pi-Gateway-Software.md` | Beispiel-Config: `seriennummer:` je Eintrag unter `zaehler:`; Payload-AK (Zeile ~127) um das optionale Feld ergänzen; „byte-genau"-Wortlaut ebenfalls lockern |
| `docs/Zaehlertausch.md` | Checkliste + Ablauf: „Pi-Config: neue Seriennummer eintragen" als verbindlichen Schritt; Blindspot-Abschnitt auf „gelöst, sofern Config aktualisiert" umstellen |

### Nicht betroffen
* **Kein Frontend** (keine UI, kein Model/Service/Component, kein Routing, keine Navigation).
* **Keine Übersetzungen** (NFR-3: keine UI → keine Translation-Migration; Log-Meldungen bleiben unübersetzt).
* **Keine** neue Entity/Tabelle → keine neue `org_id`/`@Filter`-Definition nötig (`zaehler_rohdaten` trägt beides bereits).

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. DB-Migration | `V92__Add_Seriennummer_To_Zaehler_Rohdaten.sql`: `ADD COLUMN seriennummer VARCHAR(64)` (nullable) + `COMMENT ON COLUMN`. Bestehende Zeilen behalten `NULL`; `uk_zaehler_rohdaten (einheit_id, zeit)` unberührt |
| [x] | 2. Backend-DTO | `ZaehlerMesswertPayloadDTO`: Feld `seriennummer` + Getter/Setter (optional, keine Bean-Validation) |
| [x] | 3. Backend-Entity | `ZaehlerRohdaten`: Feld + `@Column(name="seriennummer", length=64)` + Getter/Setter; Konstruktor unverändert |
| [x] | 4. Backend-Ingest | `MqttIngestService`: private `normalisiereSeriennummer(String)` (trim → leer=`null` → auf 64 kürzen); in `upsertRohdaten` per Setter setzen – im Create- **und** Update-Zweig, für jede Einheit des Messpunkts |
| [x] | 5. Backend-Aggregation | `ZaehlerAggregationService.verarbeiteIntervall`: nach dem `referenz == null`-Check den Serien-Vergleich einfügen. Beide gesetzt & `!equals` → `log.warn` (Einheit, Intervall, alte/neue Serie) + `return false`. `referenz == null && letzter != null` → `log.info` (Rollout-Marker), danach normaler Delta-Pfad |
| [x] | 6. Tests Backend | `ZaehlerAggregationServiceTest` 13→25 (Serien-Wechsel höher/niedriger inkl. Blindspot-Regression, Case-Sensitivität, bestehender Messwert unverändert, Fallback-Varianten, Rollout-Marker, Offline-Lücke, Rücktausch A→B→A, Log-Prüfung via ListAppender), `MqttIngestServiceTest` 18→30 (Normalisierung: Trim/leer/Überlänge/Grenze 64, geteilter Bilanzmesspunkt, Update-Zweig, unbekanntes Zusatzfeld), `ZaehlerRohdatenRepositoryIT` 11→14 (Persistenz Wert/NULL/Maxlänge). **546 Unit + 14 IT grün** |
| [x] | 7. Pi-Gateway: Config & Modelle | `models.py`: `seriennummer` an `MeterConfig` **und** `MeterReading`; `config.py`: optionales Feld je `zaehler`-Eintrag parsen (`strip()`, leer → `None`, > 64 Zeichen → `ConfigError`) |
| [x] | 8. Pi-Gateway: Publisher & Reader | `publisher.py`: `_to_payload` nimmt `seriennummer` **nur wenn gesetzt** auf; `modbus_reader.py` + `sim_reader.py` geben `seriennummer=self._config.seriennummer` an `MeterReading` weiter |
| [x] | 9. Pi-Gateway: Configs & README | `config.example.yaml`, `config.sim.example.yaml`, `config.sim.yaml` um `seriennummer:` ergänzen; `README.md` (Payload-Beispiel + Config-Doku) |
| [x] | 10. Specs/Doku nachziehen | `MQTT-Integration.md` (FR-3 Payload-Vertrag), `Pi-Gateway-Software.md` (Config-Beispiel + AK, „byte-genau" lockern) – siehe Tabellen oben. `docs/Zaehlertausch.md` ist bereits vorbereitet (Erstbefüllung + Checkliste) |
| [ ] | 11. Inbetriebnahme (Betrieb) | **Erstbefüllung:** alle `zaehler`-Einträge der produktiven Pi-Config mit `seriennummer` versehen und mindestens ein Publish-Intervall laufen lassen – **vor** dem ersten Tausch (s. Hinweis unten) |
| [x] | 12. Nachtrag (Vibe): INFO-Systemmeldung bei Zählerwechsel (FR-5) | Der Wechsel war bisher **nur im Log** sichtbar. Neu: Konstante `SystemmeldungService.KEY_ZAEHLERTAUSCH` (`MQTT_ZAEHLERTAUSCH`, Kategorie `KATEGORIE_MQTT` aus V93) + private `ZaehlerAggregationService.meldeZaehlerwechsel(...)`, aufgerufen im Wechsel-Zweig von `verarbeiteIntervall`. Bewusst **`erfasseAudit`** (ein Eintrag je Ereignis, direkt erledigt) statt `erfasse`: ein Tausch am **geteilten Bilanzmesspunkt** löst die Erkennung **zweimal** aus (BEZUG + RUECKLIEFERUNG) – mit Dedup würde der zweite den ersten überschreiben. Fehler beim Erfassen werden geschluckt (nur `log.warn`), damit die Aggregation nicht abbricht. `parameter` = Einheit, Serie alt → neu, Intervall. Nebenbei: neuer Formatter `ZEIT_FORMAT` (`dd.MM.yyyy HH:mm`) – auch für die bestehenden Datenlücken-Meldungen, damit die Spalte „Meldung" ein einheitliches Zeitformat zeigt. Übersetzungen `V98` (DE/EN). |

**Reihenfolge-Hinweis:** Phasen 1–6 (Backend) sind in sich abgeschlossen und **gefahrlos deploybar, bevor** der Pi das Feld sendet – ohne Serie greift der Fallback (heutiges Verhalten). Phasen 7–9 (Pi-Gateway) aktivieren die Erkennung; sie sind unabhängig vom Backend-Deploy, weil der Parser unbekannte Felder toleriert (NFR-3). **Beide Richtungen der Deploy-Reihenfolge sind also unkritisch.**

**Wichtig – Erstbefüllung vor dem ersten Tausch (Phase 11):** Die Erkennung vergleicht zwei **gesetzte** Serien. Wird die `seriennummer` erst **beim** Tausch eingetragen, ist im Übergangsintervall `referenz = NULL` (alter Zähler war nie konfiguriert) → **Fallback**, kein Baseline-Reset: **genau dieser Tausch wird nicht erkannt**, die Erkennung wirkt erst ab dem *zweiten*. Deshalb müssen **alle** Seriennummern einmalig vorab erfasst werden, mit mindestens einem Publish-Intervall Vorlauf, damit der letzte Stand des alten Zählers die Serie trägt. Die Erstbefüllung selbst ist gefahrlos (Übergang `NULL` → gesetzt erzeugt nur den Rollout-Log-Hinweis, keinen Baseline-Reset, keinen Datenverlust). Details: `docs/Zaehlertausch.md`.

**Verifikation Pi-Gateway (Phasen 7–9):** Das `pi-gateway`-Modul hat **keine** Testsuite (`pytest` ist in `pyproject.toml` nur als optionale Dev-Dependency deklariert, es existieren keine Testdateien). Prüfung daher über `ruff` (Lint, in `pyproject.toml` konfiguriert) und einen Lauf mit `protokoll: sim` gegen einen lokalen Broker – der publizierte Payload lässt sich am Log (`log.debug("Publiziert %s → %s")`) bzw. am Broker verifizieren. Ob für dieses Modul eine Testsuite aufgebaut wird, ist eine separate Entscheidung und **nicht** Teil dieses Plans.

## Implementierungs-Skizze (Referenz für Phase 5)

```java
// in verarbeiteIntervall(), nach dem bestehenden "referenz == null || letzter == null"-Check:
String serieRef = referenz.getSeriennummer();
String serieNeu = letzter.getSeriennummer();

if (serieRef != null && serieNeu != null && !serieRef.equals(serieNeu)) {
    // Zählerwechsel: kein Delta über die Grenze, neue Baseline (FR-3.2)
    log.warn("Zählerwechsel erkannt (einheit={}, intervall={} - {}): Seriennummer {} -> {}"
            + " - kein Messwert für das Übergangsintervall", einheitId, start, ende, serieRef, serieNeu);
    return false;
}
if (serieRef == null && serieNeu != null) {
    // Rollout-Marker (FR-4.2): ab jetzt ist die Tausch-Erkennung für diese Einheit aktiv
    log.info("Seriennummer erstmals vorhanden (einheit={}, intervall={} - {}, seriennummer={})",
            einheitId, start, ende, serieNeu);
}
// ... unverändert: Delta-Bildung + nichtNegativ() + upsertMesswert()
```

**Wichtig:** `return false` bedeutet – wie im bestehenden „erste Messung"-Zweig – dass für dieses Intervall **kein** Messwert erzeugt **und ein bereits vorhandener nicht angetastet** wird (FR-3.2), der Zeitraum **nicht** in die anschliessende Solarverteilung einfliesst, die Rohdaten aber dennoch als `verarbeitet` markiert werden (`markVerarbeitet` läuft im Aufrufer unabhängig vom Rückgabewert).

## Validierungen

### Backend – Ingest (`MqttIngestService`)
* `seriennummer` **trimmen**; leer nach Trim → `null` (FR-1.2/1.3).
* Länger als **64 Zeichen** → auf 64 kürzen. **Zwingend**, sonst scheitert der Insert an `VARCHAR(64)` und die transaktionale Verarbeitung verwirft die ganze Nachricht (bei geteiltem Bilanzmesspunkt die Rohdaten beider Einheiten).
* Kein Format-Check, kein Case-Mapping, keine Bean-Validation – der Inhalt wird nicht interpretiert.
* Fehlende/ungültige Seriennummer führt **nie** zum Verwerfen der Nachricht.
* Unbekannte JSON-Zusatzfelder werden weiterhin toleriert (Jackson-Default) → Deploy-Reihenfolge Backend/Pi unkritisch.

### Backend – Aggregation (`ZaehlerAggregationService`)
* Vergleich nur wenn **beide** Serien `!= null`; sonst Fallback (heutiges Verhalten inkl. Reset-Guard).
* Vergleich **exakt und case-sensitive** (`equals`) – keine Laufzeit-Normalisierung (Werte sind beim Ingest normalisiert).
* Bei Wechsel: kein Delta, kein neuer Messwert, bestehender Messwert unverändert.
* Kein zusätzlicher DB-Zugriff (`referenz`/`letzter` sind ohnehin geladen).
* Multi-Tenancy unverändert: Vergleiche sind `einheitId`-gebunden; `seriennummer` ist **nicht** mandanten-auflösend (Auflösung bleibt `(org_id, messpunkt)`).

### Pi-Gateway (`gateway/config.py`)
* `seriennummer` ist **optional**; fehlt sie, wird das Payload-Feld **weggelassen** (Payload byte-identisch zu heute).
* Typprüfung `str` (analog `_require`-Muster), `strip()`, leer nach Trim → `None`.
* Länge > 64 Zeichen → **`ConfigError`** beim Start. Bewusst strenger als das Backend: ein Config-Fehler soll beim Deploy auffallen, statt später still auf 64 Zeichen gekürzt zu werden (das Backend kürzt zusätzlich defensiv – doppelte Absicherung).
* Keine Format-/Eindeutigkeitsprüfung über Zähler hinweg: **dieselbe** Seriennummer an mehreren `messpunkt`-Einträgen ist zulässig (z.B. geteilter Bilanzmesspunkt mit getrennten BEZUG-/RUECKLIEFERUNG-Einheiten im Backend).

### Frontend
* Keine – kein UI-Anteil.

## Offene Punkte / Annahmen
* **Retention-Wechselwirkung (Spec §8, bewusst offen):** Räumt ein künftiger Cleanup-Job den letzten Stand **vor** einem Wechsel weg, wird `referenz` zu `null` → Fallback, die Erkennung schweigt. Verhalten ist definiert, **kein Blocker**; die Regel „nur verarbeitete Rohdaten löschen und je Einheit mindestens den jüngsten Stand behalten" (offene Frage in `MQTT-Integration.md`) deckt das mit ab. Bei der Retention-Umsetzung berücksichtigen.
* **Annahme Migrations-Version:** höchste bestehende ist `V91` → neue ist `V92`. Zum Umsetzungszeitpunkt erneut prüfen und **niemals** eine bereits ausgeführte Migration ändern (via `zev-db` MCP verifizieren).
* **Annahme Entity-Konstruktor:** bleibt 5-parametrig; die Seriennummer wird per Setter gesetzt (verhindert Anpassungen an bestehenden Aufrufern und Test-Helpern).
* **Annahme Log-Level:** Wechsel = `warn` (operativ relevant, soll auffallen), Rollout-Marker = `info`.
* **Restrisiko (bewusst, Spec FR-1.4):** Wird beim Tausch die **Pi-Config nicht aktualisiert**, bleibt die Serie gleich → der Wechsel wird nicht erkannt und der Blindspot besteht für diesen Tausch weiter. Das Config-Update ist daher verbindlicher Teil der Tausch-Checkliste (`docs/Zaehlertausch.md`, Phase 7).
* **Pi-Gateway ist Teil dieses Repos** (`pi-gateway/`, Python) – die Phasen 7–9 gehören zum Umfang. Ohne sie bliebe das Feature dauerhaft im Fallback (funktional unschädlich, aber ohne Wirkung).
* **Annahme Payload-Form:** Das Feld wird bei fehlender Config **weggelassen** (nicht als `null` gesendet) – hält den Payload für unkonfigurierte Zähler unverändert und vermeidet unnötige Vertragsänderung.
* **Annahme Pi-Validierung:** Überlänge → `ConfigError` beim Start (statt Kürzen). Das Backend kürzt zusätzlich defensiv, weil es beliebigen MQTT-Input verarbeiten muss (nicht nur den eigenen Pi).
