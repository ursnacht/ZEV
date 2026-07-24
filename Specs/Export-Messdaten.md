# Export-Messdaten

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** In der **Statistik** erhält in der Monatstabelle **„Summen pro Einheit"** jede **Consumer**-Zeile einen Button **„Download CSV"**. Damit werden die **15-Minuten-Messwerte** dieser Consumer-Einheit für den betreffenden **Monat** als CSV-Datei heruntergeladen (Spalten: Datum+Zeit, Energiebezug Total, Anteil Bezug aus ZEV). Die Spaltentitel enthalten zusätzlich das jeweilige **Monatstotal**.
* **Warum machen wir das:** So können den **Mietern** ihre eigenen Verbrauchsdaten (Gesamtbezug und ZEV-Anteil) im Detail gezeigt/ausgehändigt werden – als Ergänzung zur Quartalsrechnung und zur bestehenden PDF-Statistik.
* **Aktueller Stand:** Die Statistik (`/statistik`, `StatistikComponent`) zeigt pro Monat eine Tabelle „Summen pro Einheit" (`einheitSummen`: `einheitId`, `einheitName`, `einheitTyp`, `summeTotal`, `summeZev`, `summeZevCalculated`). Es gibt einen **PDF-Export** (`GET /api/statistik/export/pdf`), aber **keinen** CSV-Export der 15-Minuten-Werte je Einheit. Die 15-Min-Werte je Einheit sind bereits abrufbar (`MesswerteService.getMesswerteByEinheit` → `zeit`, `total`, `zev`; Endpoint `GET /api/messwerte/by-einheit`).

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Ablauf / Flow
1. In der Statistik-Seite hat in **jeder Monatstabelle „Summen pro Einheit"** jede Zeile vom Typ **CONSUMER** einen Button **„Download CSV"**.
2. Zeilen vom Typ **PRODUCER**, **BEZUG**, **RUECKLIEFERUNG** erhalten **keinen** Button (nur Consumer/Mieter-Verbrauch ist relevant).
3. Klick auf den Button lädt eine **CSV-Datei** mit den 15-Minuten-Messwerten dieser Consumer-Einheit für den **Zeitraum des jeweiligen Monats** (`monat.von`–`monat.bis`) herunter.
4. Der Download läuft über einen **Backend-Endpoint** (analog PDF-Export): `GET /api/statistik/export/csv?einheitId=<id>&von=<yyyy-MM-dd>&bis=<yyyy-MM-dd>&sprache=<de|en>`, Antwort `text/csv` mit `Content-Disposition: attachment`. Der `sprache`-Parameter steuert die (übersetzten) Spaltentitel (analog `export/pdf`).
5. Ist im Monat **kein** Messwert für die Einheit vorhanden (Monatstotal = 0 bzw. keine Intervalle), ist der Button **deaktiviert** (kein Export).

### FR-2: CSV-Format
* **Trennzeichen:** Feldtrenner **Komma** (`,`). **Dezimaltrennzeichen Punkt** (`.`) – zwingend, damit kein Konflikt mit dem Feldtrenner entsteht.
* **Kodierung:** UTF-8. Zeilenende `\n` (bzw. `\r\n`).
* **Kopfzeile (3 Spalten):** Die Titel werden **serverseitig in der UI-Sprache** (Param `sprache`) übersetzt — analog `StatistikPdfService`: Übersetzungen laden und je `sprache` das Feld `deutsch`/`englisch` wählen (es gibt keinen `translate(key, sprache)`-Helper). An die beiden Wert-Spalten wird das Monatstotal in Klammern angehängt:
  1. `<EXPORT_SPALTE_DATUM_ZEIT>` (DE „Datum+Zeit")
  2. `<EXPORT_SPALTE_ENERGIEBEZUG_TOTAL> (<Monatstotal Total>)` (DE „Energiebezug Total kWh") — `<Monatstotal Total>` = Summe `total` der Einheit im Monat (= `summeTotal`).
  3. `<EXPORT_SPALTE_ANTEIL_ZEV> (<Monatstotal ZEV>)` (DE „Anteil Bezug aus ZEV kWh") — `<Monatstotal ZEV>` = Summe `zev` der Einheit im Monat (= `summeZev`).
* **Datenzeilen:** je 15-Minuten-Intervall des Monats eine Zeile, aufsteigend nach Zeit:
  1. Zeitstempel im **Schweizer Format** `dd.MM.yyyy HH:mm`.
  2. `total` (Energiebezug in kWh) mit fixer Nachkommastellen-Zahl (Annahme: 3).
  3. `zev` (Anteil Bezug aus ZEV in kWh) mit derselben Nachkommastellen-Zahl.
* **Monatstotals aus derselben Rundungsbasis:** `<Monatstotal Total>` bzw. `<Monatstotal ZEV>` werden als **Summe der exportierten, auf 3 Nachkommastellen gerundeten Intervallwerte** gebildet (nicht aus einer separaten Aggregat-Query), damit der Header-Wert **exakt** der Summe der CSV-Zeilen entspricht. Eine minimale Abweichung zur in der Statistik angezeigten `summeTotal`/`summeZev` ist durch die Rundung möglich und akzeptiert.

### FR-3: Layout
* Button **„Download CSV"** in der Consumer-Zeile der Tabelle „Summen pro Einheit" (z.B. in einer Aktions-Spalte), Design-System-Button (`.zev-button .zev-button--secondary .zev-button--compact`) mit Download-Icon (`download`).
* Button-Beschriftung via `TranslationService` (Key `DOWNLOAD_CSV`).
* Vorlage-Anlehnung: bestehende Statistik-Seite / PDF-Export-Button.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] In der Monatstabelle „Summen pro Einheit" hat jede **CONSUMER**-Zeile einen Button „Download CSV"; PRODUCER/BEZUG/RUECKLIEFERUNG-Zeilen haben **keinen**.
* [ ] Klick lädt eine CSV mit `Content-Disposition: attachment` und Content-Type `text/csv` herunter.
* [ ] Die CSV ist **kommagetrennt** mit **Punkt** als Dezimaltrennzeichen.
* [ ] Kopfzeile enthält 3 Spaltentitel **in der aktuellen UI-Sprache** (Param `sprache`); an die beiden Wert-Spalten ist das Monatstotal in Klammern angehängt. Beispiel DE: `Datum+Zeit,Energiebezug Total kWh (<Total>),Anteil Bezug aus ZEV kWh (<ZEV>)`; EN entsprechend übersetzt.
* [ ] Es wird **genau eine Zeile pro vorhandenem 15-Minuten-Intervall** des Monats ausgegeben, aufsteigend nach Zeit, Zeitstempel im Format `dd.MM.yyyy HH:mm`.
* [ ] Die Summe der (gerundeten) Spalte „Energiebezug Total" entspricht **exakt** dem im Titel genannten Monatstotal (Header aus derselben Rundungsbasis wie die Zeilen); analog für „Anteil Bezug aus ZEV".
* [ ] Der Export ist **mandantengetrennt**: es werden nur Messwerte der eigenen Organisation exportiert (`org_id`/`orgFilter`), auch bei manipulierter `einheitId` einer fremden Org → kein Fremd-Export.
* [ ] Der Export erfordert `statistik:read`; ohne diese Permission ist er nicht möglich (Backend lehnt ab).
* [ ] Kein Messwert im Monat (Monatstotal 0) → der Button ist **deaktiviert** (kein Export möglich).
* [ ] `von > bis` → HTTP 400 (analog PDF-Export).
* [ ] Netzwerkfehler beim Download → übersetzte Fehlermeldung (`.zev-message--error`); kein stiller Fehlschlag.
* [ ] Alle UI-Texte (Button, Fehlermeldungen) via `TranslationService` (DE/EN).

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Ein Monat × 15-Min-Intervalle = max. ~2976 Zeilen je Einheit – unkritisch. Eine Query je Export (`findByEinheitAndZeitBetween`), Streaming/Direktantwort ohne Zwischenspeicherung.

### NFR-2: Sicherheit
* Permission **`statistik:read`** (Fachrollen `zev_user`, `org_admin`, `zev_admin`) – konsistent mit der Statistik-Seite. Backend: `@PreAuthorize("hasAuthority('statistik:read')")` (Endpoint am `StatistikController`). Frontend: Route `/statistik` bereits per `AuthGuard` mit `statistik:read` geschützt.
* **Mandantenfähigkeit:** Export ausschliesslich für Einheiten der eigenen `org_id`; Hibernate-`orgFilter` aktiv. **Wichtig:** Da `EntityManager.find()`/`findById` den `orgFilter` **nicht** anwendet, muss die `org_id` der Einheit **explizit** geprüft werden (bzw. eine org-scoped Query genutzt werden) — sonst könnte der Einheiten-Name (Header/Dateiname) einer fremden Org geladen werden, obwohl die Messwerte selbst org-gefiltert sind. Keine Cross-Tenant-Exporte.

### NFR-3: Kompatibilität
* Rein additiv: neuer Endpoint + Button + Übersetzungs-Keys. **Keine** Schema-/Datenänderung, keine Migration ausser Übersetzungen. Bestehende Statistik/PDF-Export unverändert.

## 5. Edge Cases & Fehlerbehandlung
| Szenario | Verhalten |
|----------|-----------|
| Kein Messwert im Monat für die Einheit (Monatstotal 0) | Button **deaktiviert** (kein Export möglich) |
| Nicht-Consumer-Zeile | kein Button |
| Netzwerkfehler beim Download | Fehlermeldung `.zev-message--error` (übersetzt) |
| Ungültige/fremde `einheitId` (andere Org) | 404/leer, kein Fremd-Export (orgFilter) |
| `von > bis` | HTTP 400 (analog PDF-Export) |
| Sonderzeichen im Einheiten-Namen (Dateiname) | Dateiname bereinigen (nur unbedenkliche Zeichen), keine ungültigen Dateinamen |
| Sehr grosse Werte / negative Werte | unverändert ausgeben (Consumer-`total` ist normalerweise ≥ 0) |

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** Statistik (`Specs/Statistik.md`, „Summen pro Einheit"), Messwerte (15-Min-Werte je Einheit), Einheiten (Typ CONSUMER), Multi-Tenancy.
* **Betroffener Code (Backend):**
  - `controller/StatistikController.java` — neuer Endpoint `GET /export/csv` (analog `export/pdf`).
  - `service/StatistikService.java` (oder `MesswerteService`) — CSV-Erzeugung aus den 15-Min-Werten + Monatstotals; wiederverwendbar `findByEinheitAndZeitBetween`.
* **Betroffener Code (Frontend):** `statistik.component.*` (Button je Consumer-Zeile + Download-Handler), `statistik.service.ts` (Blob-Download analog PDF-Export).
* **i18n:** neue Übersetzungs-Keys via Flyway (`ON CONFLICT (key) DO NOTHING`, DE/EN): `DOWNLOAD_CSV` (Button) sowie die **CSV-Spaltentitel** `EXPORT_SPALTE_DATUM_ZEIT`, `EXPORT_SPALTE_ENERGIEBEZUG_TOTAL`, `EXPORT_SPALTE_ANTEIL_ZEV` (+ ggf. Fehlermeldung). Die Spaltentitel werden **serverseitig in der UI-Sprache** übersetzt (Endpoint-Param `sprache`, analog PDF-Export → `TranslationService` mit Sprachkontext).
* **Datenmigration:** keine (nur Übersetzungs-Keys; nächste freie Flyway-Version zum Umsetzungszeitpunkt prüfen).

## 7. Abgrenzung / Out of Scope
* **Kein** Export für Producer-/Bilanz-Einheiten.
* **Keine** Aufteilung nach Mieter-Zeiträumen innerhalb des Monats (Export erfolgt je **Consumer-Einheit** und Monat; Mieterwechsel innerhalb des Monats wird nicht gesplittet).
* **Keine** weiteren Formate (Excel/XLSX, PDF je Einheit) – nur CSV.
* **Kein** Sammel-Export aller Consumer in einer Datei (ein Download je Consumer-Zeile).
* **Keine** neue Aggregation/Änderung der Statistik-Berechnung.

## 8. Offene Fragen

Geklärt (im Dokument eingearbeitet):
* [x] **„Anteil Bezug aus ZEV" = `zev`** (nicht `zev_calculated`; entspricht `summeZev`).
* [x] **Leerer Monat → Button deaktiviert** (kein Export).
* [x] **CSV-Erzeugung serverseitig** (neuer Endpoint `GET /api/statistik/export/csv`).
* [x] **3 Nachkommastellen** für kWh-Werte.
* [x] **Dateiname** `verbrauch_<einheitName>_<yyyy-MM>.csv` (Name bereinigt).
* [x] **Excel/CSV:** reines UTF-8, Feldtrenner Komma, Dezimaltrenner Punkt (kein BOM/`sep=`-Zeile).
* [x] **Spaltentitel gemäss UI-Sprache** (serverseitig via `TranslationService`, Endpoint-Param `sprache`).

Noch offen: –
