# Bilanz-Messwerte-Upload

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Der Messwerte-Upload wird um **Bilanz-Dateien** erweitert (Beispiel `docs/2026-06-Bilanz.csv`). Eine Bilanz-Datei enthält in **einer** Datei den gesamten Netzaustausch des ZEV über einen Monat – Netzbezug (`vZEV Bezug von VNB`) und Rücklieferung (`vZEV Rücklieferung an VNB`) – im 15-Minuten-Raster. Beim Upload werden die Werte den beiden Einheiten vom Typ `BEZUG` und `RUECKLIEFERUNG` zugeordnet, in `messwerte` gespeichert und stehen damit für den **Summen-Vergleich in der Statistik** (`Specs/Bilanzmesspunkt.md`) zur Verfügung. Die KI-basierte Zuordnung (`EinheitMatchingService`) wird so erweitert, dass sie Bilanz-Dateien am Dateinamen erkennt.
* **Warum machen wir das:** Ohne MQTT-Anbindung ist der Bilanzmesspunkt bisher nur manuell befüllbar. Mit dem Bilanz-Upload können die Ground-Truth-Werte des VNB-Zählers per CSV eingespielt und laufend gegen die berechneten Werte (`Bezug von VNB`, `Rücklieferung`) plausibilisiert werden.
* **Aktueller Stand:**
  - Der Upload (`MesswerteController.uploadCsv` → `MesswerteService.processCsvUpload`) verarbeitet **eine Datei → eine Einheit**: Datum als Parameter, je Zeile ein 15-Min-Intervall (`+15 min`), Spalten `total`,`zev`; Überschreiben pro Einheit für den ganzen Monat.
  - Die Einheit wird via KI aus dem **Dateinamen** ermittelt (`EinheitMatchingService`, Prompt listet die Einheiten; Rückgabe = eine ID oder `KEINE`).
  - Die Bilanz-Typen `BEZUG`/`RUECKLIEFERUNG` existieren (`Specs/Bilanzmesspunkt.md`), je Mandant max. eine; `total` positiv (Bezug) bzw. negativ (Rücklieferung), `zev = 0` (nicht Teil der Verteilung). Die Statistik vergleicht sie bereits mit den berechneten Werten.
  - Testdaten: Einheiten `Bezug` und `Rücklieferung` sind im Mandant `org_id = 1` erfasst.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Dateiformat Bilanz-CSV
1. Kopfzeile: `category;<Bezug-Spalte>;<Rücklieferung-Spalte>` (Trennzeichen `;`), z.B. `category;vZEV Bezug von VNB (Total 315.54 kWh);vZEV Rücklieferung an VNB (Total -454.06 kWh)`. Zuordnung **positionsbasiert** (1. Datenspalte = Bezug, 2. = Rücklieferung), **zusätzlich am Spaltentitel plausibilisiert**: die Kopfzeile muss in der Bezug-Spalte `Bezug` und in der Rücklieferung-Spalte `Rücklieferung` (case-insensitive) enthalten; passt der Titel nicht zur Position, wird die Datei mit `BILANZ_CSV_UNGUELTIG` abgewiesen.
2. Je Datenzeile: `<Tag>;<Bezug-Wert oder leer>;<Rücklieferung-Wert oder leer>`. Der `category`-Wert ist das **Tagesdatum** im Format `EEE MMM dd yyyy` (z.B. `Mon Jun 01 2026`, JS-`Date`-toString). Pro Intervall ist **genau eine** der beiden Spalten gefüllt.
3. Vorzeichen wird aus der Datei übernommen: Bezug positiv, Rücklieferung negativ (konsistent zu `Specs/Bilanzmesspunkt.md`).

### FR-2: KI-Erkennung von Bilanz-Dateien
1. Der KI-Prompt (`EinheitMatchingService.buildSystemPrompt`) wird um die Bilanz-Erkennung erweitert: Ein Dateiname mit der Abkürzung **`bilanz`** (z.B. `2026-06-Bilanz.csv`) kennzeichnet eine Bilanz-Datei, die **beide** Bilanz-Einheiten betrifft.
2. Die KI antwortet in diesem Fall mit dem Sentinel **`BILANZ`** (analog zu `KEINE`). `EinheitMatchResponseDTO` erhält ein Flag **`bilanz` (boolean)**; ein Bilanz-Treffer setzt `bilanz = true` (ohne einzelne `einheitId`).
3. Die übrige Einheiten-Zuordnung (Producer/Consumer per ID) bleibt unverändert.
4. **Manueller Fallback:** Erkennt die KI die Bilanz-Datei nicht oder ist der KI-Service nicht erreichbar, kann der Benutzer einen Upload-Eintrag **manuell** als Bilanz markieren (analog zur bereits bestehenden manuellen Einheiten-Auswahl bei fehlgeschlagenem Matching). Ein manuell als Bilanz markierter Eintrag verhält sich identisch zum KI-erkannten (`bilanz = true`).

### FR-3: Upload & Persistierung
1. Neuer Endpoint **`POST /api/messwerte/upload-bilanz`** mit den Parametern **`file`** und **`date`** (kein `einheitId`) – **analog zum bestehenden `/upload`**, nur ohne Einheit (die beiden Bilanz-Einheiten werden serverseitig per Typ aufgelöst). Permission `messwerte:write` **und** Feature-Flag `MESSWERTE_UPLOAD` (Prüfung `featureFlagService.isEnabled(orgId, FeatureFlag.MESSWERTE_UPLOAD)` wie beim bestehenden `/upload`; disabled → `FeatureDisabledException("FEATURE_FLAG_DEAKTIVIERT")`, zentral auf HTTP 403 gemappt).
2. Der Service löst im aktuellen Mandanten die Einheiten vom Typ `BEZUG` und `RUECKLIEFERUNG` per Typ auf (`findFirstByTyp`, `orgFilter` aktiv). Fehlt eine der beiden → HTTP 400 mit `BILANZ_EINHEIT_FEHLT` (übersetzt), es wird nichts gespeichert.
3. **Zeitstempel – identisch zum Consumer-Upload:** Der Zeitstempel ergibt sich aus dem **`date`-Parameter** (`00:00` des Tages) plus fortlaufendem **`+15 min` je gespeicherter Zeile** (Positions-Index über die gesamte Datei) – exakt wie `processCsvUpload`. Die `category`-Spalte der Datei wird dabei – wie `parts[0]` beim Consumer-Upload – **nicht** ausgewertet (das GUI liest daraus lediglich den Vorbefüll-Wert für `date`, FR-4.5). **Keine** Validierung auf 96 Zeilen/Tag; best-effort. Verbatim als lokale Zeit.
4. Je Datenzeile wird **ein** Messwert erzeugt: gefüllte Bezug-Spalte → Einheit `BEZUG` (`total = Wert`), gefüllte Rücklieferung-Spalte → Einheit `RUECKLIEFERUNG` (`total = Wert`). In beiden Fällen `zev = 0`, `quelle = CSV`.
5. **Überschreiben:** Bestehende Messwerte beider Einheiten werden **gleich wie beim Consumer-Upload** (`processCsvUpload`) für den Monat der Daten vor dem Speichern gelöscht (idempotenter Re-Upload). Tage über die Monatsgrenze hinaus (z.B. eine `Jul 01`-Zeile in einer Juni-Datei) werden wie beim Consumer-Upload behandelt — keine gesonderte Mehr-Monats-Logik.
6. `org_id` wird serverseitig aus dem `OrganizationContextService` gesetzt (nicht aus der Datei/dem Request).

### FR-4: Frontend-Upload
1. Erkennt die KI-Zuordnung eine Bilanz-Datei (`bilanz = true`), wird der Upload-Eintrag als **Bilanz** dargestellt (Badge/Label, Key `UPLOAD_TYP_BILANZ`); die **Einheiten-Auswahl** entfällt für diesen Eintrag (durch das Bilanz-Label ersetzt). Das **Datumsfeld bleibt sichtbar** und wird – **gleich wie bei Consumer-/Producer-Dateien** – aus dem Dateiinhalt vorbefüllt (s. FR-4.5). Zusätzlich kann der Benutzer einen Eintrag **manuell** als Bilanz markieren/zurücksetzen (Fallback, FR-2.4).
2. **Readiness:** Die „Bereit"-Prüfung verlangt für **alle** Einträge ein `date`; nur **normale** Einträge benötigen zusätzlich eine `einheitId`. Ein Bilanz-Eintrag ist also bereit, sobald sein (aus der Datei vorbefülltes) `date` gesetzt ist – **ohne** `einheitId`.
3. Der Bilanz-Eintrag wird beim Hochladen an `/api/messwerte/upload-bilanz` gesendet – mit **`file` und `date`** (ohne `einheitId`), analog zum normalen Upload. Erfolg/Fehler werden wie bei den übrigen Einträgen angezeigt (`.zev-message--success`/`--error`).
4. Gemischte Uploads (Bilanz-Datei + normale Einheiten-Dateien) in einem Vorgang sind möglich; jeder Eintrag nutzt seinen passenden Endpoint.
5. **Datum aus der Datei (alle Dateitypen einheitlich):** Beim Hinzufügen einer Datei liest das GUI das erste Datum aus dem Dateiinhalt (erste Datenzeile, Spalte `category`, Format `EEE MMM dd yyyy`) und zeigt es im **editierbaren Datumsfeld** an – **identisch für Consumer/Producer und Bilanz**. Dieses Datum wird beim Upload als `date`-Parameter gesendet und dient dem Backend als Startzeitpunkt (`date + Zeilenindex·15min`, FR-3.3) – **die Verwendung ist bei Bilanz dieselbe wie bei Consumer/Producer**. Das Datum darf nach der KI-Erkennung **nicht verschwinden**. Schlägt das Lesen fehl, bleibt das Feld leer und der Eintrag ist nicht „bereit" (der Benutzer setzt das Datum dann manuell – wie bei Consumer/Producer).

### FR-5: Statistik-Nutzung
* Es ist **kein** neuer Statistik-Code nötig: Sobald die Messwerte der Einheiten `BEZUG`/`RUECKLIEFERUNG` in `messwerte` liegen, verwendet der bestehende Summen-Vergleich (`StatistikService`, `Specs/Bilanzmesspunkt.md`) sie automatisch.

### FR-6: Persistierung & i18n
* Keine neue Tabelle/Spalte (Messwerte-Tabelle unverändert; `quelle = CSV`).
* Neue Übersetzungs-Keys via Flyway-Migration (`ON CONFLICT (key) DO NOTHING`): `UPLOAD_TYP_BILANZ`, `BILANZ_EINHEIT_FEHLT`, `BILANZ_UPLOAD_ERFOLG`, `BILANZ_CSV_UNGUELTIG` (DE/EN). Nächste freie Version prüfen (aktuell höchste `V83`).
* Multi-Tenancy unverändert: `messwerte`/`einheit` tragen `org_id`, `orgFilter` aktiv.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

### KI-Erkennung
* [ ] Eine Datei `2026-06-Bilanz.csv` wird von der KI als Bilanz-Datei erkannt (`bilanz = true`, keine einzelne `einheitId`).
* [ ] Normale Einheiten-Dateien werden weiterhin einer einzelnen Einheit zugeordnet (Regression; `bilanz = false`).
* [ ] Erkennt die KI nicht/ist nicht erreichbar, kann der Benutzer den Eintrag manuell als Bilanz markieren (Ergebnis wie `bilanz = true`).
* [ ] Passt der Spaltentitel nicht zur Position (kein `Bezug`/`Rücklieferung`) → HTTP 400 `BILANZ_CSV_UNGUELTIG`.

### Upload & Persistierung
* [ ] `POST /api/messwerte/upload-bilanz` speichert je Datenzeile einen Messwert für `BEZUG` (positiv) bzw. `RUECKLIEFERUNG` (negativ), `zev = 0`, `quelle = CSV`.
* [ ] Der Zeitstempel wird – wie beim Consumer-Upload – aus dem `date`-Parameter (`00:00`) + fortlaufendem `+15 min` je Zeile gebildet; die `category`-Spalte wird backendseitig nicht ausgewertet.
* [ ] Fehlt im Mandanten die `BEZUG`- oder `RUECKLIEFERUNG`-Einheit → HTTP 400 `BILANZ_EINHEIT_FEHLT`, nichts wird gespeichert.
* [ ] Re-Upload derselben Bilanz-Datei ersetzt die Monatsdaten beider Einheiten (kein Duplikat).
* [ ] `org_id` wird serverseitig gesetzt; ein anderer Mandant sieht die Werte nicht (Mandanten-Isolation).
* [ ] Beispiel `docs/2026-06-Bilanz.csv`: 2880 Zeilen → Summe Bezug ≈ 315.54 kWh, Summe Rücklieferung ≈ −454.06 kWh (Kopf-Totale).

### Frontend & Statistik
* [ ] Ein als Bilanz erkannter Eintrag zeigt ein Bilanz-Label und blendet die **Einheiten-Auswahl** aus; das **Datumsfeld bleibt sichtbar** und wird aus der Datei vorbefüllt (wie Consumer/Producer); Upload an den Bilanz-Endpoint.
* [ ] Beim Hinzufügen einer Bilanz-Datei wird das Datum aus der Datei gelesen und bleibt sichtbar – es verschwindet **nicht** nach der KI-Erkennung.
* [ ] Ein Bilanz-Eintrag gilt ohne `einheitId`/`date` als „bereit" (Batch wird nicht blockiert); normale Einträge brauchen weiterhin Einheit + Datum.
* [ ] Nach dem Upload erscheinen die Bilanz-Summen im Statistik-Summen-Vergleich (Bezug/Rücklieferung ↔ Bilanz).

### Sicherheit & i18n
* [ ] Der Upload erfordert `messwerte:write` **und** das aktive Feature-Flag `MESSWERTE_UPLOAD` (disabled → `FeatureDisabledException`, HTTP 403); Statistik `statistik:read`.
* [ ] Alle neuen UI-Texte/Meldungen via `TranslationService` (DE/EN).

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Eine Bilanz-Datei hat ~2880 Zeilen/Monat; Verarbeitung als Batch-Insert (`saveAll`) analog zum bestehenden Upload. Cache-Evict `statistik` nach dem Upload.

### NFR-2: Sicherheit
* Endpoint `messwerte:write` (Fachrolle `org_admin`/`zev_admin`); `@PreAuthorize` wie beim bestehenden Upload. Multi-Tenancy (`org_id`, `orgFilter`) unverändert; `orgId` nie aus Datei/Request. KI-Aufruf: nur Dateiname wird gesendet (kein Dateiinhalt), wie heute.

### NFR-3: Kompatibilität
* Rein additiv: bestehender Einheiten-Upload und `EinheitMatchResponseDTO` (neues Flag `bilanz`, Default `false`) bleiben rückwärtskompatibel. Kein Schema-Change.

## 5. Edge Cases & Fehlerbehandlung
| Szenario | Verhalten |
|----------|-----------|
| `BEZUG`- oder `RUECKLIEFERUNG`-Einheit fehlt im Mandanten | HTTP 400 `BILANZ_EINHEIT_FEHLT`, nichts gespeichert |
| Spaltentitel passt nicht zur Position (kein `Bezug`/`Rücklieferung`) | HTTP 400 `BILANZ_CSV_UNGUELTIG` (Plausibilisierung, FR-1.1) |
| Zeile mit beiden Spalten gefüllt oder beiden leer | Zeile überspringen + Warn-Log (kein Abbruch) |
| ≠ 96 Datenzeilen/Tag bzw. Lücken (Zusatzzeilen) | **best-effort, gleich wie Consumer-Upload**: keine Validierung, fortlaufendes `+15 min` je Zeile ab dem `date`-Parameter (`00:00`) |
| Tage über die Monatsgrenze | wie beim Consumer-Upload: kein gesonderter Umgang; Monats-Overwrite nach dem Monat des `date`-Parameters |
| Unparsbares `category`-Datum in der Datei | backendseitig irrelevant (Spalte wird nicht ausgewertet); im GUI bleibt das Datumsfeld leer → Eintrag nicht „bereit" (FR-4.5) |
| Leere Datei / nur Kopfzeile | HTTP 400 `BILANZ_CSV_UNGUELTIG` |
| Nicht-numerischer Wert in einer Spalte | Zeile überspringen + Warn-Log |
| KI-Service nicht erreichbar / erkennt Bilanz nicht | bestehende Fehlerbehandlung (`extractErrorMessage`); Benutzer markiert den Eintrag **manuell** als Bilanz (FR-2.4) |
| Netzwerkfehler beim Upload | Eintrag auf `error`, Meldung im Frontend |
| Datum kann nicht aus der (Bilanz-)Datei gelesen werden | Datumsfeld bleibt leer; Eintrag **nicht** „bereit" – Benutzer setzt das Datum manuell (wie Consumer/Producer, FR-4.5) |

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** Bilanzmesspunkt (`Specs/Bilanzmesspunkt.md`, Typen `BEZUG`/`RUECKLIEFERUNG`), Messwerte-Upload mit KI (`Specs/Messwerte-mit-KI.md`), Statistik.
* **Betroffener Code (Backend):**
  - `service/EinheitMatchingService.java` — Prompt um Bilanz-Abkürzung erweitern; Sentinel `BILANZ` parsen → `bilanz = true`.
  - `dto/EinheitMatchResponseDTO.java` — Flag `bilanz` (Default `false`).
  - `controller/MesswerteController.java` — neuer Endpoint `POST /upload-bilanz` (`file` + `date`, `messwerte:write` + `MESSWERTE_UPLOAD`-Flag-Prüfung wie bei `/upload`).
  - `service/MesswerteService.java` — neue Methode `processBilanzCsvUpload(file, date)`: Einheiten per Typ auflösen (`findFirstByTyp`), Spaltentitel-Plausibilisierung, Zeitstempel = `date`-Parameter (`00:00`) + fortlaufend `+15 min` je Zeile (**identisch zu `processCsvUpload`**, `category`-Spalte nicht ausgewertet), Messwerte je Einheit schreiben, Monats-Overwrite, `@CacheEvict("statistik")`.
  - `repository/EinheitRepository.java` — `findFirstByTyp(EinheitTyp)` (existiert bereits, `EinheitRepository.java:24`) zur Auflösung der Bilanz-Einheiten.
* **Betroffener Code (Frontend):** `services/einheit.service.ts` (Response-Flag `bilanz`), `components/messwerte-upload/*` (Bilanz-Eintrag: Label statt Einheiten-Auswahl, **Datumsfeld wie Consumer/Producer** aus Datei vorbefüllt und als `date` gesendet, manuelle Bilanz-Markierung, Readiness-Prüfung, Bilanz-Endpoint), `models/*`, Tests/Mocks.
* **Datenmigration:** keine (nur Übersetzungs-Keys via Flyway).

## 7. Abgrenzung / Out of Scope
* **Änderung des Statistik-Vergleichs** — dieser existiert bereits (`Specs/Bilanzmesspunkt.md`); hier wird nur die Datenquelle (Upload) ergänzt.
* **Bilanzmodell-Abrechnung** (`Specs/Bilanzmodell.md`) — unabhängiges Feature; dieser Upload liefert nur die Bilanz-Messwerte.
* **KI-Analyse des Dateiinhalts** — die KI erhält weiterhin nur den Dateinamen; die Format-/Spaltenerkennung erfolgt beim Parsen im Backend.
* **Automatische Anlage der Bilanz-Einheiten** — Bezug/Rücklieferung müssen vorab manuell erfasst sein.

## 8. Offene Fragen
Vorab geklärt:
* [x] **Erkennung:** KI erkennt Bilanz-Datei am Dateinamen (Sentinel `BILANZ`, Flag `bilanz`).
* [x] **Zeitstempel:** aus der Datei (Tag aus `category`, 15-Min-Slot aus Zeilenposition).
* [x] **KI-Rolle:** Bilanz-Abkürzung im Prompt ergänzen (kein Umbau des Antwort-Typformats).

Ebenfalls geklärt (Review):
* [x] **Unvollständige Tage (≠ 96 Zeilen):** best-effort, **gleich wie bestehender Consumer-Upload** — keine Validierung, fortlaufendes `+15 min` je Zeile (FR-3.3).
* [x] **Manueller Fallback:** ja — der Benutzer kann einen Eintrag manuell als Bilanz markieren (FR-2.4/FR-4.1).
* [x] **Spalten-Erkennung:** positionsbasiert **und** am Spaltentitel plausibilisiert (`Bezug`/`Rücklieferung`), sonst `BILANZ_CSV_UNGUELTIG` (FR-1.1).
* [x] **Jahres-/Monatswechsel:** wie bei den Consumer-Dateien behandelt — keine gesonderte Mehr-Monats-Logik (FR-3.5).
* [x] **Feature-Flag:** der neue Endpoint prüft `MESSWERTE_UPLOAD` wie `/upload` (FR-3.1).
* [x] **Frontend-Readiness:** Bilanz-Einträge gelten ohne Einheit/Datum als bereit (FR-4.2).
