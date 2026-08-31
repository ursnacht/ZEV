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
| [~] | 9. Tests | **Backend erledigt:** `PdfNumberFormatTest` (18), `StatistikServiceTest` (+10 KPI-Tests), `MesswerteRepositoryIT` (+1 Aggregat-Query) — 52 Unit + 13 IT grün. **Frontend erledigt:** `statistik.component.spec.ts` (+25: `getKennzahlen` Zeilen/Einheiten/„–"/Batterie-nur-bei-verfügbar/`berechnet`-Flag, `formatSwissNumber` Hochkomma-Gruppierung via `formatNumber`/`formatDifferenz`, `isBilanz`) — Suite 815 grün. **Offen:** E2E (`statistik.spec.ts` bereits angepasst; Verifikation nach Frontend-Redeploy) → `/5_e2e-tests` |

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

## Nachtrag: Gemessene Gegenstücke zu Autarkiegrad und Netzbezugsquote (FR-1.6)

**Auslöser:** Bei einer Anlage standen ein gemessener Netzbezug von 38 kWh und eine Netzbezugsquote
von 9.8 % (bei 985 kWh Verbrauch) nebeneinander auf derselben Seite. Beide Zahlen waren richtig —
die Quote rechnet aus dem ZEV-Anteil der Konsumenten (`(C − Cz)/C`), die 38 kWh sind der Zähler am
Netzanschluss. Nebeneinander gelesen widersprachen sie sich trotzdem. Statt eine der beiden
Definitionen zu ersetzen, werden nun **beide** ausgewiesen: Ihre Differenz ist der Verbrauchsanteil,
der weder direkt aus der PV noch aus dem Netz kam — typischerweise die Batterie-Entladung.

### Backend
* `MesswerteRepository.countDistinctZeitByEinheitTypAndZeitBetween(typ, von, bis)` — neue Query für
  die Intervall-Abdeckung (JPQL, damit der `orgFilter` greift).
* `MonatsStatistikDTO`: `autarkiegradGemessen`, `netzbezugsquoteGemessen`,
  `bilanzKennzahlenVerfuegbar`, `bilanzBezugLueckenhaft`.
* `StatistikService.berechneKennzahlen(dto, von, bis)` — Signatur um den Zeitraum erweitert (für die
  Lückenprüfung). Rechnet `B/C` bzw. `1 − B/C`, sobald eine `BEZUG`-Einheit existiert **und** `C > 0`.
* **Lückenprüfung:** `pruefeDatenVollstaendigkeitMonat` arbeitet tage- und einheitengenau und sieht
  fehlende Intervalle **innerhalb** eines Tages nicht — genau die entstehen aber, wenn das
  Bilanzmodell einzelne Intervalle ohne `BEZUG`-Messwert überspringt (`distributeBilanz`, FR-2.5).
  Deshalb wird die Anzahl `BEZUG`-Intervalle gegen die der Konsumenten gezählt; bei Unterdeckung
  `bilanzBezugLueckenhaft = true` plus WARN-Log mit den beiden Zahlen. Der Wert wird **trotzdem**
  geliefert: gekennzeichnet ist brauchbarer als „–".

### Frontend
* `MonatsStatistik` um die vier Felder erweitert.
* `KennzahlZeile` erhält das optionale Flag `luecke`; `getKennzahlen()` schiebt die gemessene Zeile
  **direkt hinter** ihr gerechnetes Gegenstück, damit die Differenz beim Lesen auffällt.
* Kennzeichnung „lückenhaft" über die bestehende `.zev-tag`-Darstellung neben `KENNZAHL_BERECHNET` —
  **kein neues CSS**.
* Die gemessenen Zeilen tragen **nicht** die Markierung „berechnet/geschätzt": Sie stammen aus einer
  Messung, nicht aus einem Residuum der Energiebilanz.

### PDF
* `statistik.jrxml`: zwei Zeilen in der linken Spalte (y=376/392) mit
  `printWhenExpression = bilanzKennzahlenVerfuegbar`, darunter der Lücken-Hinweis (y=408). Band von
  470 auf 502 erhöht, Subreport „Summen pro Einheit" von y=392 auf y=432 verschoben.
* Geprüft mit `mvn test -Dtest=JasperTemplateCompileTest`.

### i18n
* `V133__Add_Statistik_Kennzahlen_Gemessen_Translations.sql` (V132 war die höchste angewandte
  Migration, via `zev-db` geprüft): sechs neue Keys mit `ON CONFLICT (key) DO NOTHING`.
* Zusätzlich zwei `UPDATE`s auf `KENNZAHL_AUTARKIEGRAD_HINWEIS` und `KENNZAHL_NETZBEZUGSQUOTE_HINWEIS`:
  Solange der Autarkiegrad allein stand, genügte „intern aus PV/Batterie gedeckt"; neben dem
  gemessenen Wert muss der Tooltip sagen, dass eine Batterie-Entladung hier **nicht** mitzählt. Beide
  `UPDATE`s sind mit dem **alten Textwert** in der `WHERE`-Klausel abgesichert — eine eigene Anpassung
  über die Übersetzungsverwaltung wird dadurch nicht überschrieben.

### Tests
* `StatistikServiceTest`: sechs neue Fälle (Werte aus der Messung, Komplementarität, ohne
  `BEZUG`-Einheit, `C = 0`, mit und ohne Lücken) — 40 Tests grün.
* `statistik.component.spec.ts` / `statistik.service.spec.ts`: Fixtures um die vier Felder erweitert,
  acht neue Fälle (Zeilenanzahl 5/7/11, Reihenfolge, Formatierung, Lücken-Kennzeichnung).
* Ein bestehender Test griff die Batterie-Zeilen über `zeilen.slice(5)` ab und brach durch die zwei
  zusätzlichen Zeilen. Neu wird über den Key-Präfix gefiltert — beim nächsten Zusatz hält das.

### Korrektur: Lücken verzerren beide Seiten, nicht nur die gemessene

Der erste Wurf (V133) kennzeichnete nur die **gemessenen** Zeilen als „lückenhaft" — mit der
Begründung, dem Netzbezug fehlten die übersprungenen Intervalle. Das stimmt, ist aber der kleinere
der beiden Fehler, und im Bilanzmodus war auch die Herleitung des Zeilenpaars falsch.

**Was übersehen wurde:** Im Modus `BILANZ` stammt der ZEV-Anteil der Konsumenten selbst aus den
Bilanzdaten (`S = max(0, Verbrauch − Bezug)` je Intervall). Überspringt `distributeBilanz` ein
Intervall, bleiben die Konsumenten dort ohne `zev`, ihr Verbrauch zählt aber weiter — er schlägt also
**voll** als Netzbezug zu Buche statt nur mit seinem tatsächlichen Netzanteil.

Über den Monat gerechnet: `Cz = C_ohneLücke − B`, also `C − Cz = C_Lücke + B`. Der Abstand der beiden
angezeigten Autarkiegrade ist damit **exakt `C_Lücke / C`** — der Verbrauch während der Intervalle
ohne Bilanz-Messwert. Der wahre Wert liegt dazwischen: die gemessene Zeile ist um `B_Lücke / C` zu
günstig, die gerechnete um `(C_Lücke − B_Lücke) / C` zu ungünstig.

**Folge für die Herleitung des Features:** Im Bilanzmodus enthält der gerechnete Autarkiegrad die
Batterie-Entladung bereits (sie senkt den Bezug und erhöht damit `S`). Beide Zeilen sollten dort
praktisch übereinstimmen; eine Differenz zeigt **keine Batterie**, sondern Lücken in der
Bilanzmessung. Der ursprünglich genannte „Batterie-Effekt" gilt nur im Modus `PRODUCER_MESSUNG`, wo
`Cz` die direkt verbrauchte Produktion ist. Das Zeilenpaar bleibt in beiden Modi nützlich — im einen
als Batterie-Anzeige, im anderen als Datenqualitäts-Anzeige.

* `MonatsStatistikDTO.verteilungLueckenhaft` — neu; wahr bei Lücken **und** Modus `BILANZ`.
* `StatistikService`: Der Verteilmodus wird in `getStatistik` einmal ermittelt und über
  `berechneMonatsStatistiken` → `berechneMonatsStatistik` → `berechneKennzahlen` durchgereicht
  (statt je Monat neu abzufragen). Die WARN-Meldung nennt jetzt Modus und beide Richtungen.
* `KennzahlZeile.lueckeHintKey` — die beiden Seiten brauchen **verschiedene** Hinweise, weil die
  Verzerrung in entgegengesetzte Richtungen zeigt. Im Template wird `kz.luecke && kz.lueckeHintKey`
  geprüft; die zweite Bedingung engt den Typ für die `TranslatePipe` ein (`string | undefined`).
* `statistik.jrxml`: Der Fussnoten-Hinweis wählt den Text nach `verteilungLueckenhaft`.
* `V134__Statistik_Kennzahlen_Luecken_Richtung.sql`: zwei neue Hinweis-Keys
  (`KENNZAHL_LUECKE_MESSUNG_HINWEIS`, `KENNZAHL_LUECKE_VERTEILUNG_HINWEIS`) sowie `UPDATE`s auf die
  beiden `_GEMESSEN_HINWEIS`-Texte, die bisher die Batterie als Grund der Differenz nannten. V133 war
  bereits angewendet (via `zev-db` geprüft) und durfte nicht geändert werden.
* Tests: `StatistikServiceTest` 42 (Bilanzmodus kennzeichnet beide Seiten, Producer-Messung nur die
  gemessene), `statistik.component.spec.ts` um Richtung und Hinweis-Keys erweitert.
