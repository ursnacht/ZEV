# Statistik-Kennzahlen

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Die Statistik-Seite erhält je Monat ein **Kennzahlen-Panel** mit fachlich aussagekräftigen Energie-KPIs (Autarkiegrad, Eigenverbrauchsquote, Netzbezugs-/Einspeisequote, ZEV-Eigenverbrauch) sowie – wo die Daten es zulassen – **berechneten Batterie-Kennzahlen** (Netto-Speicherfluss; in Stufe 2 zusätzlich geladen/entladen/Wirkungsgrad).
* **Warum machen wir das:** Im **Bilanzmodell** (`Specs/Bilanzmodell.md`) ist der bestehende Summen-Vergleich „berechneter Bezug ↔ Bilanz-Bezug" **tautologisch** (per Konstruktion ≈ 0) und damit wenig nützlich. Statt einer reinen Plausibilitätsprüfung sollen echte Betriebs-Kennzahlen dargestellt werden, die Betreiber und Mieter verstehen. Ausserdem ist der **Batteriespeicher** im Bilanzmodell zwar implizit enthalten, aber bisher nirgends sichtbar – er lässt sich rechnerisch aus der Energiebilanz ableiten.
* **Aktueller Stand:**
  - Die Statistik (`StatistikService` → `StatistikDTO`/`MonatsStatistikDTO`) liefert je Monat bereits: `summeProducerTotal` (Produktion, Betrag), `summeConsumerTotal` (Verbrauch), `summeProducerZev`, `summeConsumerZev`, `summeConsumerZevCalculated`, sowie die gemessenen Bilanz-Summen `bilanzBezug`/`bilanzRuecklieferung` (Betrag).
  - Der Verteilmodus (`PRODUCER_MESSUNG` / `BILANZ`) ist top-level in `StatistikDTO.verteilmodus` vorhanden (`StatistikService` setzt ihn via `EinstellungenService.getVerteilmodus`) und wird angezeigt; im Bilanzmodus trägt der Summen-Vergleich einen Tautologie-Hinweis.
  - 15-Minuten-Rohwerte je Einheit sind über `messwerteRepository` verfügbar (`findByZeitAndEinheitTyp`, `findDistinctZeitBetween`).

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Kennzahlen aus den bestehenden Monats-Summen (Stufe 1)
Je Monat werden folgende KPIs berechnet und angezeigt. **Grundlage sind ausschliesslich die bereits vorhandenen ZEV-/Total-Summen** – dadurch sind die Quoten-KPIs **modus-agnostisch** und funktionieren in **beiden** Verteilmodi (`PRODUCER_MESSUNG` und `BILANZ`) identisch, ohne dass Bilanz-Bezug/Rücklieferung zwingend vorhanden sein müssen:
* Produktion `P = summeProducerTotal`
* Verbrauch `C = summeConsumerTotal`
* intern gedeckter Verbrauch `Cz = summeConsumerZev` (effektiver/gemessener ZEV-Anteil; bei MQTT-Daten der verteilte Anteil, bei CSV der gemessene Wert)
* intern genutzte Produktion `Pz = summeProducerZev` (Betrag; modus-abhängige Bedeutung, s. FR-1.2)

1. **Autarkiegrad** = `Cz / C` (Anteil des Verbrauchs, der intern – aus PV/Batterie – gedeckt wurde). Nur wenn `C > 0`.
2. **Eigenverbrauchsquote** = `Pz / P` (Anteil der Produktion, der intern genutzt statt eingespeist wurde). Nur wenn `P > 0`. Definition bewusst über die vorhandene ZEV-Summe `summeProducerZev`, damit die Kennzahl in beiden Verteilmodi ohne zusätzliche Bilanz-Daten verfügbar ist. **Wichtig – modus-abhängige Bedeutung** (Code: `MesswerteService.aktualisiereProducerZev`): Die *Berechnungsformel* ist in beiden Modi identisch, die *Bedeutung* von `Pz` unterscheidet sich jedoch:
   * `PRODUCER_MESSUNG`: `Pz` = die **direkt im ZEV verbrauchte** Produktion (die tatsächlich auf Consumer verteilte Menge). Eine (in diesem Modus nicht modellierte) Batterieladung zählt **nicht** mit.
   * `BILANZ`: `Pz` wird je Intervall als `max(0, |Produktion| − |Rücklieferung|)` gebildet – das ist die **nicht eingespeiste** Produktion und entspricht damit `(P − R)`. Eine Batterieladung senkt die Rücklieferung und **erhöht** `Pz`, zählt also im Bilanzmodus als „Eigenverbrauch" mit. Das ist die gebräuchliche (Textbuch-)Eigenverbrauchsquote; die Kennzahl ist hier bewusst so akzeptiert.
   
   Siehe Abschnitt 8 (Definition) und Abschnitt 5 (Edge Cases) für diese Modus-Abhängigkeit.
3. **Netzbezugsquote** = `(C − Cz) / C` (Komplement zum Autarkiegrad). Nur wenn `C > 0`.
4. **Einspeisequote** = `(P − Pz) / P` (Komplement zur Eigenverbrauchsquote). Nur wenn `P > 0`.
5. **ZEV-Eigenverbrauch** = `Cz` (absolut, kWh) – der effektiv intern gedeckte Verbrauch. Modus-agnostisch, kein Bilanz-Bezug nötig. *Hinweis:* `summeConsumerZev` ist der **effektive/gemessene** ZEV-Anteil; die exakt **verteilte** Menge `S` steht in `summeConsumerZevCalculated`. Bei MQTT-Daten sind beide gleich (Sentinel `zev==0` wird mit dem verteilten Anteil überschrieben, `MesswerteService`), bei CSV-Consumern bleibt `summeConsumerZev` der gemessene Wert. Für diese Kennzahl wird bewusst `summeConsumerZev` verwendet (konsistent zum bestehenden Summen-Vergleich).
6. **Netto-Speicherfluss** = `P − C + B − R` (absolut, kWh; > 0 = Netto-Ladung, < 0 = Netto-Entladung über den Zeitraum), mit Netzbezug `B = bilanzBezug`, Rücklieferung `R = bilanzRuecklieferung` (Betrag). **Berechneter Schätzwert** (Residuum der Energiebilanz, s. FR-2): nur wenn Producer **und** Bilanz-Bezug **und** Rücklieferung vorhanden; als **„berechnet/geschätzt"** gekennzeichnet.

* Prozentwerte werden auf sinnvolle Genauigkeit gerundet (z.B. 1 Nachkommastelle) und mit `%` dargestellt.
* Die Quoten-KPIs (1–5) sind in beiden Verteilmodi verfügbar, sobald der jeweilige Nenner (`C` bzw. `P`) > 0 ist. Nur der Netto-Speicherfluss (6) und die Batterie-KPIs (FR-2) setzen zusätzlich Bilanz-Bezug **und** Rücklieferung voraus.
* Fehlt eine benötigte Grösse (kein Producer → Nenner `P = 0`, kein Verbrauch → `C = 0`, bzw. für die Batterie-KPIs fehlende Bilanz-Daten), wird die betroffene Kennzahl als **„–"/n/a** angezeigt (kein Fehler, kein Abbruch).

### FR-2: Batterie-Kennzahlen mit Pro-Intervall-Aggregation (Stufe 2)
Zusätzlich zum Netto-Speicherfluss (FR-1.6) werden – getrennt – **geladene** und **entladene** Energie ausgewiesen. Diese lassen sich **nicht** aus den Monats-Summen ableiten, sondern erfordern eine **neue Aggregation je 15-Minuten-Intervall** (die vorhandenen Zeitraum-Summen-Queries genügen dafür nicht, s. Abschnitt 6):

Pro Intervall `i`: `Netto_i = P_i − C_i + B_i − R_i`, dann über den Zeitraum:
1. **Batterie geladen** = `Σ max(0, Netto_i)`
2. **Batterie entladen** = `Σ max(0, −Netto_i)`
3. **Round-Trip-Wirkungsgrad** = `entladen / geladen` (nur wenn `geladen > 0`), als Prozent.

* **Kennzeichnung als Schätzwert:** Alle Batterie-Kennzahlen sind ein **Residuum** der Energiebilanz und enthalten damit auch Messfehler, Wandlungs-/Leitungsverluste und nicht gemessene Lasten. Sie werden **explizit als „berechnet"/„geschätzt"** gekennzeichnet (Hinweis/Tooltip).
* Nur anzeigen, wenn Producer **und** Bilanz-Bezug **und** Rücklieferung im Zeitraum vorhanden sind; sonst entfällt der Batterie-Block.

### FR-3: Anzeige & Verhältnis zum bestehenden Summen-Vergleich
1. Das **Kennzahlen-Panel** wird je Monat angezeigt (in **beiden** Verteilmodi – die KPIs sind generell nützlich).
2. Im **Bilanzmodus** tritt das Kennzahlen-Panel an die Stelle des wenig aussagekräftigen Summen-Vergleichs: der **Summen-Vergleich wird im Bilanzmodus ausgeblendet**, das Kennzahlen-Panel ersetzt ihn.
3. Im **Producer-Messung**-Modus bleibt der bestehende Summen-Vergleich unverändert erhalten (dort ist er als Plausibilitätsprüfung sinnvoll); das Kennzahlen-Panel wird zusätzlich angezeigt.
4. Darstellung **ausschliesslich mit Design-System-Bausteinen** (`@zev/design-system`), analog zur bestehenden Statistik-Seite: ein `.zev-panel`/`.zev-panel--month` mit `.zev-panel__title`/`.zev-panel__content`, je Kennzahl eine `.zev-info-row` mit `.zev-info-label` + `.zev-info-value`. Keine komponenten-eigenen Ad-hoc-Styles; fehlende Bausteine (z.B. Markierung „berechnet/geschätzt") werden als wiederverwendbare Klasse **im Design-System** ergänzt. Batterie-Werte optisch als „berechnet" markiert.

### FR-4: PDF-Export
* Die Kennzahlen erscheinen **auch im Statistik-PDF** (`statistik.jrxml`) je Monat, analog zur Bildschirmanzeige: Quoten-KPIs (in beiden Modi) sowie – wo vorhanden – die als „berechnet/geschätzt" markierten Batterie-KPIs. Der Summen-Vergleich im PDF folgt derselben Modus-Logik wie die Bildschirmanzeige (im Bilanzmodus durch das Kennzahlen-Panel ersetzt).

### FR-5: Persistierung & i18n
* **Keine** neue Tabelle/Spalte. Die Kennzahlen werden **berechnet** und im `MonatsStatistikDTO` als zusätzliche (transiente) Felder geliefert – kein Schema-Change.
* Neue Übersetzungs-Keys via Flyway (`ON CONFLICT (key) DO NOTHING`, DE/EN), u.a.: `STATISTIK_KENNZAHLEN`, `KENNZAHL_AUTARKIEGRAD`, `KENNZAHL_EIGENVERBRAUCHSQUOTE`, `KENNZAHL_NETZBEZUGSQUOTE`, `KENNZAHL_EINSPEISEQUOTE`, `KENNZAHL_ZEV_EIGENVERBRAUCH`, `KENNZAHL_BATTERIE_NETTO`, `KENNZAHL_BATTERIE_GELADEN`, `KENNZAHL_BATTERIE_ENTLADEN`, `KENNZAHL_BATTERIE_WIRKUNGSGRAD`, `KENNZAHL_BERECHNET_HINWEIS`.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] Je Monat wird ein Kennzahlen-Panel mit Autarkiegrad, Eigenverbrauchsquote, Netzbezugsquote, Einspeisequote und ZEV-Eigenverbrauch angezeigt.
* [ ] Die Quoten-KPIs (Autarkiegrad, Eigenverbrauchsquote, Netzbezugs-/Einspeisequote, ZEV-Eigenverbrauch) werden in **beiden** Verteilmodi identisch berechnet und benötigen **keinen** Bilanz-Bezug.
* [ ] **Autarkiegrad** = `summeConsumerZev / summeConsumerTotal`; Beispiel `Cz=600, C=1000` → `60,0 %`.
* [ ] **Eigenverbrauchsquote** = `summeProducerZev / summeProducerTotal`; Beispiel `Pz=900, P=1200` → `75,0 %`. Im Modus `BILANZ` entspricht `Pz` der nicht eingespeisten Produktion `max(0, P − R)` (Batterieladung zählt mit); im Modus `PRODUCER_MESSUNG` der direkt verteilten Produktion.
* [ ] **Netzbezugsquote** = `1 − Autarkiegrad` und **Einspeisequote** = `1 − Eigenverbrauchsquote`; im Beispiel `40,0 %` bzw. `25,0 %`.
* [ ] **ZEV-Eigenverbrauch** = `summeConsumerZev` (kWh); im Beispiel `600 kWh`.
* [ ] **Netto-Speicherfluss** = `Produktion − Verbrauch + Bezug − Rücklieferung`; Beispiel `P=1200, C=1000, B=400, R=300` → `+300 kWh` (Netto-Ladung).
* [ ] **Batterie geladen/entladen** werden aus der Pro-Intervall-Summe der positiven bzw. negativen Nettos gebildet; Beispiel je Intervall `+5 / −3 / +2` → geladen `7`, entladen `3`.
* [ ] **Round-Trip-Wirkungsgrad** = `entladen / geladen`; Beispiel `entladen=3, geladen=7` → `42,9 %`. Bei `geladen = 0` wird „–" angezeigt (keine Division durch 0).
* [ ] Fehlt Producer (Nenner `P = 0`) → Eigenverbrauchsquote/Einspeisequote „–"; fehlt Verbrauch (Nenner `C = 0`) → Autarkiegrad/Netzbezugsquote „–". Keine Exception, kein NaN/Infinity.
* [ ] Fehlt Bilanz-Bezug oder Rücklieferung → nur die Batterie-KPIs (Netto-Speicherfluss, geladen/entladen/Wirkungsgrad) werden als **„–"** angezeigt; die Quoten-KPIs bleiben verfügbar.
* [ ] Batterie-Kennzahlen (inkl. Netto-Speicherfluss) sind als **„berechnet/geschätzt"** gekennzeichnet und werden nur bei vorhandenen Producer- **und** Bilanz-Daten (Bezug + Rücklieferung) gezeigt.
* [ ] Im **Producer-Messung**-Modus bleibt der bestehende Summen-Vergleich erhalten; im **Bilanzmodus** wird er ausgeblendet und durch das Kennzahlen-Panel ersetzt.
* [ ] Die Kennzahlen erscheinen auch im **Statistik-PDF** (`statistik.jrxml`) je Monat mit derselben Modus-Logik wie am Bildschirm.
* [ ] Alle Texte via `TranslationService` (DE/EN); Prozentwerte mit `%`, kWh-Werte in kWh.
* [ ] Das Kennzahlen-Panel nutzt **ausschliesslich** Design-System-Klassen (`.zev-panel`, `.zev-info-row`, `.zev-info-label`, `.zev-info-value`); keine komponenten-eigenen Ad-hoc-Styles. Eine ggf. neue „berechnet"-Kennzeichnung wird als Klasse im Design-System (`@zev/design-system`) ergänzt, nicht im Component-CSS.
* [ ] Statistik bleibt mit `statistik:read` erreichbar; Multi-Tenancy unverändert.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Stufe 1 (FR-1) ist reine Arithmetik auf bereits vorhandenen Summen → vernachlässigbar.
* Stufe 2 (FR-2, Batterie geladen/entladen) erfordert eine Aggregation je 15-Minuten-Intervall (analog zur Solarverteilung) **oder** eine dedizierte Aggregat-Query. Die Komplexität bleibt O(Zeitpunkte × Einheiten); der `statistik`-Cache greift weiterhin. Bevorzugt eine SQL-seitige Aggregation (Netto je `zeit`, dann bedingte Summe), um einen zweiten vollen Intervall-Loop zu vermeiden.

### NFR-2: Sicherheit
* Keine neuen Permissions: Anzeige über `statistik:read`. Multi-Tenancy (`org_id`/`orgFilter`) unverändert; keine mandantenübergreifenden Daten.

### NFR-3: Kompatibilität
* Rein additiv: zusätzliche berechnete DTO-Felder + UI-Panel + Übersetzungen. Kein Schema-Change, keine Änderung an Rechnung/Verteilung. Bestehende Statistik (inkl. Summen-Vergleich im Producer-Modus und PDF-Export) bleibt funktionsfähig.

## 5. Edge Cases & Fehlerbehandlung
| Szenario | Verhalten |
|----------|-----------|
| Keine Producer-Einheit (P = 0) | Eigenverbrauchsquote/Einspeisequote/Batterie → „–"; Autarkiegrad/Netzbezugsquote/ZEV-Eigenverbrauch bleiben (basieren auf Consumer-Summen) |
| Keine Bilanz-Einheiten (Bezug/Rücklieferung) | Nur Batterie-KPIs (Netto-Speicherfluss, geladen/entladen/Wirkungsgrad) → „–"; Quoten-KPIs bleiben verfügbar (modus-agnostisch aus ZEV-Summen) |
| Nur Bezug, keine Rücklieferung | Quoten-KPIs ok; Batterie-KPIs → „–" (Rücklieferung fehlt) |
| Verbrauch = 0 bzw. Produktion = 0 | betroffene Quote „–" (keine Division durch 0) |
| Batterie geladen = 0 | Round-Trip-Wirkungsgrad → „–" (keine Division durch 0) |
| Negativer Netto-Speicherfluss | als Netto-Entladung ausweisen (Vorzeichen/Label) |
| Residuum durch Messfehler leicht negativ/positiv obwohl keine Batterie | Batterie-Werte als „berechnet" gekennzeichnet; kleine Werte sind erwartbar (Verluste/Messrauschen) |
| Batterie lädt im Bilanzmodus | Eigenverbrauchsquote (`Pz/P` mit `Pz = max(0, P − R)`) zählt die Batterieladung **als Eigenverbrauch** mit (bewusst, s. FR-1.2); im Modus `PRODUCER_MESSUNG` ist das nicht der Fall (kein Batteriemodell) |
| Leere Liste / kein Monat im Zeitraum | kein Panel, kein Fehler |
| Netzwerkfehler beim Laden der Statistik | bestehende Fehlerbehandlung greift unverändert |

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** Bilanzmodell (`Specs/Bilanzmodell.md`), Bilanzmesspunkt (`Specs/Bilanzmesspunkt.md`, Typen `BEZUG`/`RUECKLIEFERUNG`), bestehende Statistik.
* **Betroffener Code (Backend):**
  - `dto/MonatsStatistikDTO.java` — neue berechnete Felder (Autarkiegrad, Eigenverbrauchsquote, Netzbezugsquote, Einspeisequote, ZEV-Eigenverbrauch, Batterie netto/geladen/entladen/Wirkungsgrad).
  - `service/StatistikService.java` — Berechnung der KPIs aus den Summen (Stufe 1) bzw. Pro-Intervall-Aggregation für geladen/entladen (Stufe 2).
  - `repository/MesswerteRepository.java` — **neue** Aggregat-Query bzw. Intervall-Loop (`findDistinctZeitBetween` + `findByZeitAndEinheitTyp`) für den Netto je 15-Min-Intervall (Stufe 2); die bestehenden `sum…ByEinheitTypAndZeitBetween`-Queries liefern nur Zeitraum-Summen und reichen für geladen/entladen **nicht** aus.
* **Betroffener Code (Frontend):** `statistik.component.*` (+ `statistik.model.ts`) — Kennzahlen-Panel mit bestehenden Design-System-Bausteinen (`.zev-panel`/`.zev-info-row`), „berechnet"-Kennzeichnung; ggf. `statistik.service`/DTO-Mapping.
* **Design-System (`design-system/src/components/statistik/`):** nur falls eine neue „berechnet/geschätzt"-Markierung benötigt wird → dort als wiederverwendbare Klasse ergänzen (nicht im Component-CSS).
* **PDF-Export (Backend):** `service/StatistikPdfService.java` + `reports/statistik.jrxml` — Kennzahlen je Monat ins PDF aufnehmen (Quoten-KPIs in beiden Modi, Batterie-KPIs als „berechnet"; Summen-Vergleich im Bilanzmodus ausgeblendet).
* **i18n:** neue Keys via Flyway (nächste freie Version zum Umsetzungszeitpunkt prüfen; aktuell höchste `V89`).
* **Datenmigration:** keine.

## 7. Abgrenzung / Out of Scope
* **Kein** direkter Batteriezähler / keine Speicher-Modellierung als eigene Einheit (die explizite Variante bleibt `Specs/Batteriespeicher.md`); hier nur die **rechnerische** Ableitung aus der Bilanz.
* **Keine** Kostsen/Tarif-Kennzahlen (eingesparte Netzkosten o.ä.) in dieser Stufe.
* **Keine** Änderung an Verteilung/Verrechnung.
* **Keine** neue Persistenz/Historisierung der Kennzahlen (werden bei Bedarf berechnet).

## 8. Offene Fragen
> Alle geklärt.

* [x] **Stufen-Umfang:** **Entschieden:** Stufe 1 (KPIs aus Summen) zuerst, Stufe 2 (Batterie geladen/entladen via Pro-Intervall-Aggregation) als Folgeschritt.
* [x] **Summen-Vergleich im Bilanzmodus:** **Entschieden:** im Bilanzmodus **ausblenden**; das Kennzahlen-Panel tritt an seine Stelle (siehe FR-3.2). Im Producer-Messung-Modus bleibt er erhalten.
* [x] **Eigenverbrauchsquote-Definition:** **Entschieden:** `summeProducerZev / summeProducerTotal` (intern genutzte Produktion / Produktion), weil so **ohne zusätzliche Bilanz-Daten** in beiden Verteilmodi verfügbar. **Achtung modus-abhängige Bedeutung** (s. FR-1.2): Im Modus `BILANZ` wird `summeProducerZev` intern als `max(0, |Produktion| − |Rücklieferung|)` = `(P − R)` gebildet, d.h. eine **Batterieladung zählt hier als Eigenverbrauch mit** (Textbuch-Eigenverbrauchsquote – bewusst akzeptiert). Im Modus `PRODUCER_MESSUNG` ist `summeProducerZev` die direkt verteilte Produktion **ohne** Batterie. Analog Autarkiegrad über `summeConsumerZev / summeConsumerTotal`.
* [x] **PDF-Export:** **Entschieden:** ja – die Kennzahlen erscheinen auch im Statistik-PDF (siehe FR-4).
* [x] **Anzeige in beiden Modi:** **Entschieden:** in beiden Modi anzeigen (Quoten-KPIs sind modus-agnostisch; Batterie-KPIs nur bei vorhandenen Bilanz-Daten).
* [x] **Rundung/Format:** **Entschieden:** Prozentwerte mit 1 Nachkommastelle, kWh-Werte wie in der bestehenden Statistik-Anzeige.
