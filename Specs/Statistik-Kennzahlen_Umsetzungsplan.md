# Statistik-Kennzahlen – Umsetzungsplan

## Zusammenfassung
Die Statistik-Seite erhält je Monat ein **Kennzahlen-Panel** mit fachlichen Energie-KPIs (Autarkiegrad, Eigenverbrauchsquote, Netzbezugs-/Einspeisequote, ZEV-Eigenverbrauch) sowie – wo Bilanz-Daten vorliegen – **berechneten Batterie-Kennzahlen** (Netto-Speicherfluss; Stufe 2: geladen/entladen/Wirkungsgrad). Die KPIs ersetzen im Bilanzmodus den tautologischen Summen-Vergleich und machen den impliziten Batteriespeicher sichtbar. **Rein additiv:** keine neue Tabelle/Spalte, nur berechnete DTO-Felder, ein UI-Panel, PDF-Ergänzung und Übersetzungen.

## Vorgehen in zwei Stufen (Spec §8)
* **Stufe 1** (Phasen 1–5, 8): KPIs + Netto-Speicherfluss aus den bereits vorhandenen Monats-Summen (reine Arithmetik).
* **Stufe 2** (Phasen 6–7): Batterie geladen/entladen/Wirkungsgrad via Pro-Intervall-Aggregation (neue Query). Als Folgeschritt umsetzbar, ohne Stufe 1 zu blockieren.

## Betroffene Komponenten

### Backend (kein Schema-Change)
| Datei | Änderung |
|-------|----------|
| `dto/MonatsStatistikDTO.java` | Neue transiente Felder: `autarkiegrad`, `eigenverbrauchsquote`, `netzbezugsquote`, `einspeisequote`, `zevEigenverbrauch`, `batterieNetto`, `batterieGeladen`, `batterieEntladen`, `batterieWirkungsgrad` (alle `Double`, `null` = „–") + `batterieKennzahlenVerfuegbar`/`kennzahlenBerechnet` (boolean, optional) |
| `service/StatistikService.java` | In `berechneMonatsStatistik(...)`: KPI-Berechnung aus vorhandenen Summen (Stufe 1) + Netto-Speicherfluss; Stufe 2: Batterie geladen/entladen aus Intervall-Aggregat |
| `repository/MesswerteRepository.java` | **Neue** Aggregat-Query „Netto je `zeit`" für Stufe 2 (bedingte Summen über `EinheitTyp` je Zeitstempel), Alternative: Intervall-Loop über `findDistinctZeitBetween`+`findByZeitAndEinheitTyp` |
| `service/StatistikPdfService.java` | KPIs an den JasperReports-Datencontext übergeben; Parameter `IST_BILANZ` |
| `util/PdfNumberFormat.java` | **neu** — zentrale locale-unabhängige Zahlenformatierung (Punkt-Dezimal, Hochkomma-Gruppierung) für das gesamte PDF |
| `resources/reports/statistik.jrxml` | Kennzahlen-Block je Monat; Summen-Vergleich im Bilanzmodus ausblenden; alle Zahlen über `PdfNumberFormat` |
| `resources/reports/einheit-summen.jrxml` | Subreport-Zahlen (Total/ZEV/ZEV berechnet) über `PdfNumberFormat` vereinheitlicht |

### Frontend
| Datei | Änderung |
|-------|----------|
| `models/statistik.model.ts` | `MonatsStatistik` um die neuen KPI-Felder erweitern (`number \| null`) |
| `components/statistik/statistik.component.html` | Kennzahlen-Panel je Monat (`.zev-panel`/`.zev-info-row`); Summen-Vergleich `@if (!isBilanz)` |
| `components/statistik/statistik.component.ts` | Formatierung Prozent (1 NKS) + „–" bei `null`; „berechnet"-Kennzeichnung; ggf. Helfer `formatPercent`/`isNa` |
| `design-system/src/components/statistik/statistik.css` | Nur falls neue Markierung „berechnet/geschätzt" nötig → wiederverwendbare Klasse (z.B. `.zev-info-value--berechnet`), **nicht** im Component-CSS |

### Übersetzungen
| Datei | Änderung |
|-------|----------|
| `resources/db/migration/V90__Add_Statistik_Kennzahlen_Translations.sql` | Kennzahlen-Labels DE/EN (`ON CONFLICT (key) DO NOTHING`) |
| `resources/db/migration/V91__Add_Statistik_Kennzahlen_Hinweise_Translations.sql` | Erklärende Hinweise/Tooltips je Kennzahl DE/EN |

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Backend-DTO | `MonatsStatistikDTO` um berechnete KPI-Felder + Getter/Setter erweitert (kein Schema-Change) |
| [x] | 2. Backend-Service (Stufe 1) | In `StatistikService.berechneMonatsStatistik(...)` `berechneKennzahlen(...)`: Quoten-KPIs, ZEV-Eigenverbrauch und Netto-Speicherfluss aus vorhandenen Summen; `null` bei Nenner=0 / fehlenden Bilanz-Daten |
| [x] | 3. Frontend-Model | `MonatsStatistik` (statistik.model.ts) um KPI-Felder (`number \| null`) ergänzt |
| [x] | 4. Frontend-Komponente | Kennzahlen als **3-Spalten-Tabelle** ohne Titelzeile, Inhaltsbreite (`.zev-table--compact`/`.zev-table--auto`, Wert `.zev-table__number` rechtsbündig) via `getKennzahlen(monat)`-View-Model; Summen-Vergleich per `@if (!isBilanz)` ausgeblendet; „–" + `.zev-tag`-„berechnet"-Markierung; erklärender `title`-Tooltip je Zeile. Neuer DS-Modifier `.zev-table--auto` (Design-System + Showcase) |
| [x] | 5. Übersetzungen | Flyway `V90__Add_Statistik_Kennzahlen_Translations.sql` (DE/EN) angelegt |
| [x] | 6. Backend-Batterie (Stufe 2) | Neue Aggregat-Query `sumBilanzKomponentenPerZeitBetween` in `MesswerteRepository` (Netto je `zeit`); `berechneBatterieKennzahlen(...)` berechnet geladen/entladen/Wirkungsgrad (nur bei Producer + Bezug + Rücklieferung) |
| [x] | 7. Frontend-Batterie (Stufe 2) | Batterie-Block im Panel (Netto/geladen/entladen/Wirkungsgrad), als „berechnet" markiert; nur bei `batterieKennzahlenVerfuegbar` |
| [x] | 8. PDF-Export | `StatistikPdfService` (Parameter `IST_BILANZ`) + `statistik.jrxml`: Kennzahlen-Block je Monat, Batterie als „berechnet"; Summen-Vergleich im Bilanzmodus ausgeblendet; `JasperTemplateCompileTest` grün |
| [ ] | 9. Tests | Backend: `StatistikServiceTest` (KPI-Formeln, Nenner=0, fehlende Bilanz-Daten, Batterie geladen/entladen, Wirkungsgrad geladen=0). Frontend: `statistik.component.spec.ts` (Panel, „–", Vergleich-Ausblenden bilanz). E2E: KPI-Panel sichtbar (`statistik.spec.ts`). **→ separate Test-Commands** |

## Berechnungslogik (Referenz für die Umsetzung)

Grundgrössen (bereits im DTO): `P=summeProducerTotal`, `C=summeConsumerTotal`, `Cz=summeConsumerZev`, `Pz=summeProducerZev`, `B=bilanzBezug`, `R=bilanzRuecklieferung` (alle Betrag/positiv).

**Stufe 1 (aus Summen, `StatistikService`):**
* `autarkiegrad = C > 0 ? Cz / C : null`
* `netzbezugsquote = C > 0 ? (C − Cz) / C : null`
* `eigenverbrauchsquote = P > 0 ? Pz / P : null`  *(Bedeutung von `Pz` modus-abhängig, s. Spec FR-1.2)*
* `einspeisequote = P > 0 ? (P − Pz) / P : null`
* `zevEigenverbrauch = Cz` (kWh)
* `batterieNetto = (Producer vorhanden && Bezug && Rücklieferung) ? P − C + B − R : null` (berechnet/geschätzt)

**Stufe 2 (Pro-Intervall, neue Query):** je `zeit`: `Netto_i = P_i − C_i + B_i − R_i`
* `batterieGeladen = Σ max(0, Netto_i)`
* `batterieEntladen = Σ max(0, −Netto_i)`
* `batterieWirkungsgrad = batterieGeladen > 0 ? batterieEntladen / batterieGeladen : null`

Alle Prozent-KPIs: Anzeige mit 1 Nachkommastelle + `%`; kWh wie bestehende Statistik-Anzeige.

## Validierungen

### Backend
* Nenner-Prüfung `C > 0` bzw. `P > 0` vor jeder Division → sonst `null` (kein `NaN`/`Infinity`, keine Exception).
* Batterie-KPIs nur wenn Producer **und** Bilanz-Bezug **und** Rücklieferung im Zeitraum vorhanden (sonst `null`); `batterieWirkungsgrad` nur wenn `batterieGeladen > 0`.
* Multi-Tenancy unverändert: Berechnung ausschliesslich auf den bereits org-gefilterten Summen/Messwerten; keine neuen mandantenübergreifenden Queries.
* Autorisierung: keine neue Permission, weiterhin `@PreAuthorize("hasAuthority('statistik:read')")` am `StatistikController`.

### Frontend
* `null` → Anzeige „–" (kein `NaN`, kein `0 %` als Fehlwert).
* Zahlenformat locale-unabhängig: **Punkt** als Dezimal-, **Hochkomma (`'`)** als Tausendertrennzeichen. Frontend über den seitenweiten Helfer `formatSwissNumber()` (basiert auf `toFixed`), den `formatNumber`/`formatDifferenz`/`formatPercent` nutzen; **gesamtes** PDF (statistik.jrxml + einheit-summen.jrxml) über `ch.nacht.util.PdfNumberFormat` (`String.format(Locale.ROOT, …)` mit Gruppierungs-Ersatz `,`→`'`).
* Summen-Vergleich nur im Modus `PRODUCER_MESSUNG` rendern (`@if (!isBilanz)`); Kennzahlen-Panel in beiden Modi.
* Ausschliesslich Design-System-Klassen; keine Ad-hoc-Styles in der Komponente.

## Übersetzungs-Keys (V90)
`STATISTIK_KENNZAHLEN`, `KENNZAHL_AUTARKIEGRAD`, `KENNZAHL_EIGENVERBRAUCHSQUOTE`, `KENNZAHL_NETZBEZUGSQUOTE`, `KENNZAHL_EINSPEISEQUOTE`, `KENNZAHL_ZEV_EIGENVERBRAUCH`, `KENNZAHL_BATTERIE_NETTO`, `KENNZAHL_BATTERIE_GELADEN`, `KENNZAHL_BATTERIE_ENTLADEN`, `KENNZAHL_BATTERIE_WIRKUNGSGRAD`, `KENNZAHL_BERECHNET_HINWEIS` (+ ggf. `KENNZAHL_NA` = „–" / „n/a").

## Offene Punkte / Annahmen
* **Stufen-Reihenfolge (Spec §8, entschieden):** Stufe 1 zuerst, Stufe 2 als Folgeschritt. Phasen 6–7 können separat/später umgesetzt werden.
* **Summen-Vergleich im Bilanzmodus (entschieden):** vollständig ausblenden (nicht nur Tautologie-Hinweis), Kennzahlen-Panel tritt an seine Stelle.
* **Eigenverbrauchsquote-Definition (entschieden):** `Pz/P` über `summeProducerZev`; im Bilanzmodus entspricht `Pz = max(0, P − R)` → Batterieladung zählt als Eigenverbrauch mit (Textbuch-Definition, bewusst akzeptiert; s. Spec FR-1.2/§5).
* **ZEV-Eigenverbrauch-Feld (Annahme):** verwendet `summeConsumerZev` (effektiv/gemessen), nicht `summeConsumerZevCalculated` – konsistent zum bestehenden Summen-Vergleich (Spec FR-1.5).
* **Stufe-2-Aggregation (Annahme):** bevorzugt SQL-seitige Aggregat-Query „Netto je `zeit`" (bedingte Summe je `EinheitTyp`), um einen zweiten vollen Intervall-Loop zu vermeiden (NFR-1); Fallback ist der Loop über `findDistinctZeitBetween`+`findByZeitAndEinheitTyp`.
* **Cache:** Der bestehende `statistik`-Caffeine-Cache (TTL 15 min) greift unverändert, da die KPIs Teil des berechneten `StatistikDTO` sind.
* **„berechnet"-Markierung (Annahme):** falls kein passender Design-System-Baustein existiert, neue wiederverwendbare Klasse in `design-system/src/components/statistik/` (z.B. `.zev-info-value--berechnet`) + Tooltip via `KENNZAHL_BERECHNET_HINWEIS`.
* **Migrations-Version:** aktuell höchste ist `V89`; die Übersetzungs-Migration wird als `V90` angelegt (zum Umsetzungszeitpunkt final prüfen).
