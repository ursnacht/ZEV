# Datenaufbewahrung (Retention & Löschkonzept)

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Ein **gestaffeltes Aufbewahrungs- und Löschkonzept** für die betrieblichen Daten der Anwendung — pro Datenklasse eine definierte Frist, danach **Löschen** bzw. **Verdichten** durch einen geplanten Job. Bisher wächst der Datenbestand (`messwerte`, `zaehler_rohdaten`, `metriken`) **unbegrenzt**; es gibt keine Regel, wann etwas verschwindet.
* **Warum machen wir das:** Zwei Treiber, in dieser Reihenfolge:
  1. **Datenschutz (revDSG):** 15-Minuten-Lastgänge einzelner Haushalte sind Personendaten mit hohem Aussagegehalt (Anwesenheit, Tagesablauf, Gerätenutzung). Zweckbindung und Verhältnismässigkeit verlangen, sie zu löschen oder zu verdichten, sobald der Zweck (Abrechnung, Nachvollziehbarkeit) entfällt. **Unbegrenztes Vorhalten ist der eigentliche Mangel**, nicht der Speicherplatz.
  2. **Nachvollziehbarkeit/Beweisbarkeit:** Umgekehrt müssen die Grundlagen einer gestellten Abrechnung so lange vorhanden bleiben, wie sie belegt werden müssen (Rechnungslegung, Verjährung, Belegeinsicht der Mieter).

  > **Speicherplatz ist ausdrücklich *kein* Treiber.** Grössenordnung: ~10 Einheiten × 96 Intervalle/Tag ≈ 350'000 Zeilen/Jahr — für PostgreSQL vernachlässigbar. Eine Retention, die nur mit „die Tabelle wird zu gross" begründet wird, wäre hier nicht nötig.
* **Aktueller Stand:**
  - `messwerte` (15-Min-Werte), `zaehler_rohdaten` (absolute Stände aus MQTT) und `metriken` werden **nie** gelöscht.
  - Einzig `systemmeldung` hat bereits eine Retention: `SystemmeldungCleanupJob` löscht **erledigte** Einträge älter als 90 Tage (konfigurierbar, `@Scheduled`-Cron). Dieser Job ist die **Vorlage** für die hier beschriebenen Jobs.
  - `Specs/MQTT-Integration.md` §8 führt die Rohdaten-Retention als **offene Frage** („folgt später") samt dem entscheidenden Vorbehalt zum Referenzstand.
  - **Rechnungs-PDFs werden nicht archiviert** (`RechnungStorageService` hält sie nur temporär in-memory). Eine gestellte Rechnung ist damit **ausschliesslich** aus `messwerte` + `tarif` reproduzierbar — die Messdaten sind der einzige Beleg.

### Rechtliche Ausgangslage (Annahme — **zu verifizieren**, siehe §8)
> Die folgenden Fristen sind eine **fachliche Einschätzung, keine Rechtsauskunft**. Vor produktivem Einsatz durch eine Fachstelle (z.B. VSE, EnergieSchweiz/Suisseénergie-Leitfaden ZEV) oder juristisch prüfen lassen.

| Grundlage | Frist | Wirkung |
|---|---|---|
| **EnG (SR 730.0) Art. 16–18 / EnV (SR 730.01) Art. 14–18** | — | Regeln Eigenverbrauch/ZEV und verlangen eine nachvollziehbare, verursachergerechte Abrechnung mit Einsichtsmöglichkeit, nennen aber **keine** Aufbewahrungsdauer |
| **OR Art. 958f** (Geschäftsbücher, Buchungsbelege) | **10 Jahre** | Massgebliche **Untergrenze** für alles, was eine Abrechnung belegt |
| **MWSTG Art. 70** (falls MWST-pflichtig) | 10 Jahre (Liegenschaften länger) | analog |
| **OR Art. 128 Ziff. 1** (Verjährung periodischer Forderungen) | **5 Jahre** | Zeitraum, in dem mit Nachforderungen/Einwänden zu rechnen ist → Neuberechnung muss möglich bleiben |
| **OR Art. 257b Abs. 2** (Belegeinsicht Mieter) | — | Grundlagen müssen während der Einsichtsmöglichkeit verfügbar sein |
| **revDSG** (seit 1.9.2023), Zweckbindung/Verhältnismässigkeit | **Obergrenze** | Nach Zweckwegfall löschen oder anonymisieren/verdichten |
| **StromVG/StromVV, Metering Code VSE** | — | Betrifft das Messwesen des **Netzbetreibers** am Anschlusspunkt, nicht die ZEV-internen Zähler |

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Aufbewahrungsklassen und Default-Fristen
Jede Datenklasse erhält eine eigene Frist. Alle Fristen sind über `application.yml` konfigurierbar (FR-5); die Defaults:

| # | Daten | Default-Frist | Danach | Begründung |
|---|-------|---------------|--------|------------|
| 1 | `zaehler_rohdaten` (absolute Stände, MQTT) | **24 Monate** | **löschen** | Zwischenprodukt; die Abrechnung stützt sich auf `messwerte`. Guards siehe FR-2 |
| 2 | `messwerte` in **15-Min-Auflösung** | **5 Jahre** | **verdichten** auf Tageswerte (FR-3) | Deckt die Verjährungsfrist (OR 128) ab: Innerhalb dieser Zeit muss eine vollständige Neuberechnung möglich bleiben |
| 3 | `messwerte` als **Tageswerte** (verdichtet) | **10 Jahre** ab Messzeitpunkt | **löschen** | Belegfunktion nach OR 958f |
| 4 | `metriken` | **13 Monate** | **löschen** | Betriebskennzahlen; 13 Monate erlauben den Jahresvergleich |
| 5 | `systemmeldung` | **90 Tage** nach Erledigung | löschen | **Bestehend, unverändert** (`SystemmeldungCleanupJob`) |

* Die Fristen laufen ab dem **Messzeitpunkt** (`zeit`) bzw. bei Systemmeldungen ab `erledigt_am` — nicht ab dem Erstellungs-/Importzeitpunkt.
* Die Fristen gelten **mandantenübergreifend** einheitlich (je Mandant konfigurierbare Fristen: Out of Scope, §7).

### FR-2: Rohdaten-Cleanup (`zaehler_rohdaten`)
1. Gelöscht werden nur Sätze, die **alle** folgenden Bedingungen erfüllen:
   * `verarbeitet = true` — unverarbeitete Stände werden **nie** gelöscht (sie sind noch nicht in `messwerte` überführt),
   * `zeit` älter als die Frist,
   * **nicht** der jüngste Satz der Einheit.
2. **Referenzstand-Guard (kritisch, aus `Specs/MQTT-Integration.md` §8):** Pro Einheit muss der **letzte Stand vor einer noch unverarbeiteten Lücke** erhalten bleiben — er ist die Referenz für die überbrückende Delta-Bildung nach einem Unterbruch. Wird er gelöscht, geht der Verbrauch des gesamten Unterbruchs **verloren** (`referenz == null` → kein Messwert). Sichere Umsetzung: pro Einheit den jüngsten Satz **immer** behalten und zusätzlich nichts löschen, was **jünger** ist als der älteste unverarbeitete Satz derselben Einheit.
3. **Zählertausch-Erkennung** (`Specs/Zaehlertausch-Erkennung.md`) vergleicht die Seriennummer mit dem Vorgänger-Satz. Da der jüngste Satz je Einheit erhalten bleibt (Punkt 1/2), bleibt die Erkennung funktionsfähig.

### FR-3: Verdichtung der Messwerte (`messwerte`)
1. Nach Ablauf der 15-Min-Frist werden die Werte je `einheit_id` und **Kalendertag** zu **einem** Satz zusammengefasst:
   * `zeit` = Tagesbeginn (00:00) des betroffenen Tages,
   * `total`, `zev`, `zev_calculated` = **Summen** des Tages (jeweils getrennt — bei CSV-Mandanten ist `zev` gemessen und **nicht** identisch mit `zev_calculated`),
   * `org_id`, `einheit_id` unverändert; die 15-Min-Sätze des Tages werden gelöscht.
2. **Tageswerte, nicht Monatswerte** — bewusst: `tarif` hat `gueltig_von`/`gueltig_bis` als **Datums**bereiche, und `RechnungService` summiert je Tarif-Gültigkeitsperiode. Ein Tarifwechsel **mitten im Monat** ist zulässig; Monatsaggregate würden die Rechnung dann falsch machen. Tagesaggregate sind die feinste Granularität, die ein Tarifwechsel erfordert, und zugleich die gröbste, die noch alle Rechnungen exakt reproduziert.
3. **Rechnungen bleiben exakt reproduzierbar:** `RechnungService` bildet Summen über Datumsbereiche (`sumTotalByEinheitAndZeitBetween`, `sumZevCalculatedByEinheitAndZeitBetween`). Diese Summen sind gegenüber der Verdichtung **invariant**, solange die Aggregate in derselben Tabelle liegen und in den Zeitraum fallen.
4. **Was die Verdichtung kostet (bewusst akzeptiert):**
   * Eine **Neuberechnung der Verteilung** (`MesswerteService.distribute`) ist für verdichtete Zeiträume **nicht mehr möglich** — sie arbeitet je 15-Min-Intervall. Deshalb liegt die Frist bei 5 Jahren (Verjährung), nicht kürzer.
   * Die Statistik verliert für verdichtete Zeiträume die Tages-/Intervall-Diagnostik („Tage mit Abweichungen", Vollständigkeitsprüfung je Intervall); Monats-Summen und Kennzahlen bleiben korrekt.
   * Genau dieser Auflösungsverlust **ist** der Datenschutz-Nutzen: Der Personenbezug (Tagesprofil) verschwindet, der Beleg bleibt.
5. **Kennzeichnung:** Verdichtete Sätze sind als solche erkennbar (Vorschlag: neuer Wert `AGGREGAT` im bestehenden Enum `Quelle` — String-Spalte, **kein** DDL nötig). Verdichtete Sätze werden von der Verteilung (`distribute`/`distributeBilanz`) und von der MQTT-Aggregation **ignoriert**; die MQTT-Sentinel-Regel (`zev == 0` → berechneter Wert) darf auf ihnen nicht greifen.
6. **Idempotenz:** Ein erneuter Lauf über einen bereits verdichteten Zeitraum ändert nichts (keine Doppel-Aggregation, keine Summen-Verdopplung).

### FR-4: Löschung nach Ablauf der Belegfrist
* Tageswerte älter als die Belegfrist (Default 10 Jahre ab `zeit`) werden gelöscht — ebenso `metriken` älter als ihre Frist.
* Gelöscht wird **hart** (kein Soft-Delete): Ein Soft-Delete würde den Zweck (Löschpflicht nach revDSG) verfehlen.

### FR-5: Konfiguration, Ausführung und Protokollierung
1. **Konfiguration** in `application.yml`, analog `systemmeldung.retention.*`:
   ```yaml
   datenaufbewahrung:
     enabled: false                 # Default AUS – bewusste Inbetriebnahme-Entscheidung
     cron: "0 30 3 * * *"           # nach dem Systemmeldung-Cleanup (03:00)
     rohdaten-monate: 24
     messwerte-detail-jahre: 5
     messwerte-beleg-jahre: 10
     metriken-monate: 13
     dry-run: true                  # nur protokollieren, nichts löschen/verdichten
   ```
2. **`enabled: false` als Default** und **`dry-run: true`**: Ein Job, der unwiederbringlich löscht, darf nicht durch ein blosses Deployment scharf werden. Erst nach Prüfung der protokollierten Mengen wird beides umgestellt.
3. **Ausführung:** Ein `@Scheduled`-Job (`DatenaufbewahrungJob`), mandantenübergreifend, ohne Request-Kontext → alle Zugriffe **org-explizit** (kein `getCurrentOrgId()`, vgl. `Specs/Bilanzmodell.md` FR-1.4).
4. **Protokollierung:** Je Lauf **ein** INFO-Log mit Klasse, Zeitraum, betroffener Anzahl je Datenklasse und Laufzeit; im Dry-Run mit dem Präfix „(dry-run)".
5. **Systemmeldung:** Je Lauf mit tatsächlichen Änderungen **eine** INFO-Systemmeldung je Mandant (Kategorie Datenaufbewahrung) mit den Mengen — damit die Löschung nicht still passiert (analog `Specs/Zaehlertausch-Erkennung.md`). Läufe ohne Änderung erzeugen **keine** Meldung.
6. **Fehlerverhalten:** Ein Fehler bei einem Mandanten/einer Datenklasse bricht den Gesamtlauf **nicht** ab (ERROR-Log + Systemmeldung, übrige laufen weiter).

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

### Rohdaten
* [ ] Verarbeitete Rohdaten älter als die Frist werden gelöscht.
* [ ] **Unverarbeitete** Rohdaten werden **nie** gelöscht, unabhängig vom Alter.
* [ ] Der **jüngste** Satz je Einheit bleibt immer erhalten, auch wenn er älter als die Frist ist.
* [ ] Existiert für eine Einheit ein unverarbeiteter Satz, wird **kein** jüngerer Satz dieser Einheit gelöscht (Referenzstand-Guard); ein anschliessender Aggregationslauf bildet den Verbrauch des Unterbruchs weiterhin korrekt ab.
* [ ] Die Zählertausch-Erkennung funktioniert nach einem Cleanup unverändert.

### Verdichtung
* [ ] 15-Min-Werte älter als die Detail-Frist werden je Einheit und Tag zu **einem** Satz zusammengefasst; die Ausgangssätze sind danach gelöscht.
* [ ] Die Tages-Summen von `total`, `zev` und `zev_calculated` entsprechen **exakt** den Summen der Ausgangssätze (getrennt je Feld).
* [ ] Eine Rechnung über einen verdichteten Zeitraum ergibt **denselben** Betrag wie vor der Verdichtung — auch bei einem **Tarifwechsel mitten im Monat**.
* [ ] Ein zweiter Lauf über denselben Zeitraum verändert nichts (Idempotenz, keine Summen-Verdopplung).
* [ ] Verdichtete Sätze werden von der Solarverteilung und der MQTT-Aggregation ignoriert (kein Überschreiben via Sentinel-Regel).
* [ ] Tageswerte älter als die Belegfrist werden gelöscht.

### Betrieb
* [ ] Bei `enabled: false` passiert nichts (kein Löschen, kein Verdichten).
* [ ] Bei `dry-run: true` werden die Mengen protokolliert, aber **keine** Daten verändert.
* [ ] Je Lauf mit Änderungen entsteht eine INFO-Systemmeldung mit den Mengen; ein Lauf ohne Änderungen erzeugt keine.
* [ ] Ein Fehler bei einem Mandanten stoppt die übrigen nicht.
* [ ] Der Job läuft ohne Request-Kontext, ohne `NoOrganizationException`, und vermischt keine Mandantendaten.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Löschen/Verdichten erfolgt **batchweise** (z.B. 10'000 Zeilen bzw. tageweise je Einheit), damit keine Langläufer-Transaktion die Tabelle blockiert.
* Der Job läuft **nachts** und ausserhalb des MQTT-Ingest-/Aggregations-Fensters; er darf den laufenden Betrieb nicht ausbremsen.
* Bestehende Indizes (`idx_messwerte_zeit`, `idx_messwerte_einheit_id`, `idx_zaehler_rohdaten_unverarbeitet`) sind zu nutzen; ein zusätzlicher Index wird erst bei nachgewiesenem Bedarf angelegt.

### NFR-2: Sicherheit
* **Kein** REST-Endpunkt zum manuellen Auslösen in der ersten Ausbaustufe (§7). Kommt einer hinzu, dann ausschliesslich mit `zev_admin`-Permission.
* Multi-Tenancy: Der Job verarbeitet Mandanten **getrennt**; Zugriffe org-explizit. Eine mandantenübergreifende Löschung darf nie durch einen fehlenden Filter entstehen.
* **Unwiederbringlichkeit:** Vor Aktivierung ist ein funktionierendes DB-Backup Voraussetzung — das Löschen ist nicht rückgängig zu machen.

### NFR-3: Kompatibilität
* Rein additiv: neuer Job + Konfiguration; **kein** Schema-Change (der Aggregat-Marker nutzt die bestehende `quelle`-Spalte).
* Bestehende Auswertungen (Statistik, Rechnungen) bleiben für nicht verdichtete Zeiträume unverändert; für verdichtete Zeiträume gelten die in FR-3.4 genannten Einschränkungen.
* Bei `enabled: false` (Default) ist das Verhalten der Anwendung **identisch zu heute**.

## 5. Edge Cases & Fehlerbehandlung
| Szenario | Verhalten |
|----------|-----------|
| Keine Daten älter als die Frist | Lauf endet ohne Änderung, keine Systemmeldung |
| Einheit wurde gelöscht, Messwerte existieren noch | Werden nach denselben Fristen behandelt (Verdichtung/Löschung greift unabhängig von der Einheit) |
| Tag mit **unvollständigen** Intervallen (Datenlücke) | Wird trotzdem verdichtet; die Summe entspricht den vorhandenen Werten (keine Hochrechnung, keine Meldung) |
| Zeitraum enthält bereits verdichtete Sätze (`quelle = AGGREGAT`) | Werden übersprungen, nicht erneut aggregiert (FR-3.6) |
| Rohdaten-Unterbruch reicht über die Rohdaten-Frist hinaus | Referenzstand-Guard verhindert die Löschung; die Daten bleiben, bis der Unterbruch aufgearbeitet ist |
| Job läuft, während eine Verteilung/Rechnung läuft | Verdichtete Zeiträume liegen Jahre zurück → praktisch keine Überschneidung; zusätzlich Batch-Transaktionen statt einer Grosstransaktion |
| Zwei Job-Läufe überschneiden sich (langer Lauf) | Ein zweiter Lauf wird übersprungen (kein paralleles Löschen) |
| Zeitumstellung (Sommer-/Winterzeit) | Tagesgrenzen folgen dem Kalendertag der gespeicherten lokalen Zeit; der 23-/25-Stunden-Tag wird als ein Tag aggregiert |
| Mandant mit CSV-Daten (gemessener `zev`) | `zev` und `zev_calculated` werden **getrennt** summiert; der gemessene Wert wird nicht überschrieben |
| Backup/Restore in einen alten Stand | Nach Restore laufen die Fristen wieder ab dem Messzeitpunkt — die Verdichtung wiederholt sich idempotent |

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** `messwerte`, `zaehler_rohdaten`, `metriken`, `systemmeldung` (bestehend); `SystemmeldungService` für die INFO-Meldungen; funktionierendes DB-Backup (NFR-2).
* **Betroffener Code (Backend, neu):**
  - `service/DatenaufbewahrungService.java` — Fristen-Logik, Verdichtung, Löschung (org-explizit, batchweise)
  - `service/DatenaufbewahrungJob.java` — `@Scheduled`, Vorlage: `SystemmeldungCleanupJob`
  - Repository-Erweiterungen: Bulk-Delete/-Aggregation (`@Modifying`-Queries) in `MesswerteRepository`, `ZaehlerRohdatenRepository`, `MetrikRepository`
  - `entity/Quelle.java` — neuer Wert `AGGREGAT` (kein DDL, `EnumType.STRING`)
* **Betroffener Code (Backend, geändert):**
  - `MesswerteService.distribute`/`distributeBilanz` — verdichtete Sätze (`quelle = AGGREGAT`) überspringen
  - `ZaehlerAggregationService` — dito für die MQTT-Aggregation
  - `SystemmeldungService` — neue Kategorie/Keys für die Retention-Meldungen
  - `application.yml` — Konfigurationsblock (FR-5.1)
* **i18n:** Übersetzungs-Keys für die Systemmeldung (DE/EN) via Flyway, `ON CONFLICT (key) DO NOTHING`; Versionsnummer zum Umsetzungszeitpunkt via `zev-db`/`flyway:info` eruieren.
* **Datenmigration:** Keine. Der erste Lauf verändert **Bestandsdaten** — deshalb Default `enabled: false` + `dry-run: true` (FR-5.2).
* **Dokumentation:** `Specs/MQTT-Integration.md` §8 (offene Frage „Rohdaten-Retention") auf diese Spec verweisen lassen.

## 7. Abgrenzung / Out of Scope
* **Je Mandant konfigurierbare Fristen** — vorerst global; Mandanten mit abweichenden Anforderungen wären eine eigene Anforderung.
* **UI zur Retention** (Anzeige/Auslösen/Fristen bearbeiten) — reiner Hintergrund-Job; kein Endpunkt, keine Seite.
* **Export/Archivierung vor dem Löschen** (z.B. CSV-Dump ins Dateisystem oder auf ein NAS-Volume) — bewusst nicht Teil dieser Spec.
* **Archivierung der Rechnungs-PDFs** — heute gibt es keine (nur temporär in-memory). Ob eine revisionssichere Ablage nötig ist, ist eine **eigene** Anforderung (siehe §8).
* **Anonymisierung statt Löschung** bei Mieterwechsel (Trennung Messdaten ↔ Person) — eigenes Thema, hier nicht gelöst.
* **Löschung auf Betroffenen-Antrag** (Auskunfts-/Löschbegehren nach revDSG) — kein automatisierter Prozess in dieser Spec.
* **Backups** unterliegen ihrer eigenen Aufbewahrung; ein Löschen in der DB entfernt die Daten **nicht** aus alten Backups.

## 8. Offene Fragen
* [ ] **Rechtliche Verifikation der Fristen** (blockierend für die Aktivierung, nicht für die Umsetzung): Sind 10 Jahre (OR 958f) für ZEV-Abrechnungsbelege korrekt, und gibt es im Energierecht/kantonal doch eine spezifische Vorgabe? → durch Fachstelle/juristisch prüfen lassen. Bis dahin gelten die Werte in FR-1 als **Annahme**.
* [ ] **Verdichten oder einfach löschen?** Die Verdichtung (FR-3) ist der aufwändigere Weg. Alternative: 15-Min-Werte 10 Jahre behalten und danach löschen — einfacher, aber datenschutzseitig schwächer.
* [ ] **Aggregat-Ablage:** In `messwerte` mit `quelle = AGGREGAT` (Vorschlag — bestehende Summen-Queries bleiben korrekt) **oder** in einer eigenen Tabelle `messwerte_tag` (sauberere Semantik, erfordert aber Anpassungen in `RechnungService`/`StatistikService`, sonst liefern alte Zeiträume still **0**).
* [ ] **Frist für die 15-Min-Auflösung:** 5 Jahre (Verjährung) oder kürzer (z.B. 3 Jahre = laufendes + 2 Vorjahre)? Kürzer heisst: weniger Personenbezug, aber Neuberechnungen sind früher unmöglich.
* [ ] **Rechnungsarchiv:** Soll eine gestellte Rechnung als PDF revisionssicher abgelegt werden? Heute ist sie nur reproduzierbar — was die Messdaten zum einzigen Beleg macht und damit deren Aufbewahrungspflicht verschärft.
* [ ] **Backup-Aufbewahrung:** Wie lange werden DB-Backups gehalten, und ist das mit den Löschfristen konsistent?
