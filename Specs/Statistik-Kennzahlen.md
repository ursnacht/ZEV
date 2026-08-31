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
6. **Autarkiegrad (gemessen)** = `1 − B / C` und **Netzbezugsquote (gemessen)** = `B / C`, mit dem **gemessenen** Netzbezug `B = bilanzBezug` der `BEZUG`-Bilanz-Einheit. Nur wenn eine `BEZUG`-Einheit existiert und `C > 0`; sonst entfallen beide Zeilen.
   * Die gemessenen Werte stehen in der Tabelle **direkt hinter** ihrem gerechneten Gegenstück, damit die Differenz beim Lesen auffällt. **Was die Differenz bedeutet, hängt vom Verteilmodus ab:**
     * `PRODUCER_MESSUNG`: `Cz` ist die **direkt** verbrauchte Produktion. Eine **Batterie-Entladung** steckt weder darin noch im Netzbezug – die Differenz der beiden Autarkiegrade ist damit der Beitrag der Batterie.
     * `BILANZ`: `Cz` wird selbst aus der Bilanz abgeleitet (`S = max(0, Verbrauch − Bezug)` je Intervall). Die Batterie-Entladung senkt den Bezug und ist deshalb **schon im gerechneten Wert enthalten** – beide Autarkiegrade sollten hier praktisch **übereinstimmen**. Eine Differenz zeigt **Lücken in der Bilanzmessung** an; das Zeilenpaar wirkt in diesem Modus als Datenqualitäts-Anzeige.
   * **Lücken in der Bilanzmessung** (FR-2.5 Bilanzmodell) verzerren **beide** Seiten – unterschiedlich stark und in **entgegengesetzte** Richtung:
     * *gemessen:* In `B` fehlt der Netzbezug der übersprungenen Intervalle → Autarkiegrad **zu hoch**. Fehler = `B_Lücke / C`.
     * *gerechnet (nur im Bilanzmodus):* Die übersprungenen Intervalle lassen die Konsumenten ohne `zev` zurück, ihr Verbrauch zählt aber weiter – er schlägt **voll** als Netzbezug zu Buche statt nur mit seinem Netzanteil → Autarkiegrad **zu tief**. Fehler = `(C_Lücke − B_Lücke) / C`, also in der Regel der grössere von beiden.
     * Folglich liegt der **wahre Wert zwischen den beiden angezeigten Zeilen**, und ihr Abstand entspricht `C_Lücke / C` – dem Verbrauch während der Intervalle ohne Bilanz-Messwert. Rechnerisch: `C − Cz = C_Lücke + B` (obere Schranke; die Klammerung `max(0, ·)` kann `C_Lücke` kleiner ausfallen lassen).
     * Die tages- und einheitengenaue Vollständigkeitsprüfung sieht solche Lücken **innerhalb** eines Tages nicht. Deshalb wird die Intervall-Abdeckung des `BEZUG` gegen die der Konsumenten gezählt; bei Unterdeckung werden die betroffenen Zeilen als **„lückenhaft"** gekennzeichnet – mit **je eigenem Hinweis**, weil die Verzerrung in entgegengesetzte Richtungen zeigt. Der Wert wird trotzdem angezeigt.
     * Im Modus `PRODUCER_MESSUNG` stammt `Cz` aus der Producer-Verteilung; fehlende `BEZUG`-Werte berühren ihn nicht – dort werden nur die **gemessenen** Zeilen gekennzeichnet.
7. **Netto-Speicherfluss** = `P − C + B − R` (absolut, kWh; > 0 = Netto-Ladung, < 0 = Netto-Entladung über den Zeitraum), mit Netzbezug `B = bilanzBezug`, Rücklieferung `R = bilanzRuecklieferung` (Betrag). **Berechneter Schätzwert** (Residuum der Energiebilanz, s. FR-2): nur wenn Producer **und** Bilanz-Bezug **und** Rücklieferung vorhanden; als **„berechnet/geschätzt"** gekennzeichnet.

* Prozentwerte werden auf sinnvolle Genauigkeit gerundet (z.B. 1 Nachkommastelle) und mit `%` dargestellt. **Dezimaltrennzeichen ist der Punkt** (`.`), **Tausendertrennzeichen das Hochkomma** (`'`, Schweizer Konvention) – locale-unabhängig (Frontend `toFixed`, PDF `String.format(Locale.ROOT, …)` mit Gruppierungs-Ersatz `,`→`'`).
* Die Quoten-KPIs (1–5) sind in beiden Verteilmodi verfügbar, sobald der jeweilige Nenner (`C` bzw. `P`) > 0 ist. Die gemessenen Quoten (6) setzen eine `BEZUG`-Einheit voraus; nur der Netto-Speicherfluss (7) und die Batterie-KPIs (FR-2) setzen zusätzlich Bilanz-Bezug **und** Rücklieferung voraus.
* Fehlt eine benötigte Grösse (kein Producer → Nenner `P = 0`, kein Verbrauch → `C = 0`, bzw. für die Batterie-KPIs fehlende Bilanz-Daten), wird die betroffene Kennzahl als **„–"/n/a** angezeigt (kein Fehler, kein Abbruch).

### FR-2: Batterie-Kennzahlen mit Pro-Intervall-Aggregation (Stufe 2)
Zusätzlich zum Netto-Speicherfluss (FR-1.7) werden – getrennt – **geladene** und **entladene** Energie ausgewiesen. Diese lassen sich **nicht** aus den Monats-Summen ableiten, sondern erfordern eine **neue Aggregation je 15-Minuten-Intervall** (die vorhandenen Zeitraum-Summen-Queries genügen dafür nicht, s. Abschnitt 6):

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
4. Darstellung **tabellarisch** mit Design-System-Bausteinen (`@zev/design-system`): eine `.zev-table`/`.zev-table--compact`/`.zev-table--auto` **ohne Titelzeile** (kein `<thead>`) mit **drei Spalten** je Kennzahl. `.zev-table--auto` begrenzt die Tabelle auf ihre **Inhaltsbreite**, damit die Bezeichnungs-Spalte nicht unnötig breit wird (nicht `width:100%`):
   * Spalte 1 – **Bezeichnung** (linksbündig), inkl. „berechnet"-Markierung bei Batterie-Kennzahlen;
   * Spalte 2 – **Wert** (rechtsbündig, `.zev-table__number`), Zahlen ohne Einheit;
   * Spalte 3 – **Einheit** (linksbündig, z.B. `%`/`kWh`; bei n/a leer).
   Keine komponenten-eigenen Ad-hoc-Styles; alle Klassen bestehen im Design-System.
5. **Erklärender Hinweis je Kennzahl:** Jede Tabellenzeile (`<tr>`) trägt einen erklärenden Tooltip (natives `title`-Attribut), der dem Benutzer die Bedeutung der Kennzahl kurz erläutert. Die Hinweistexte sind via `TranslationService` übersetzt (DE/EN). Bei den Batterie-Kennzahlen bleibt der separate „berechnet"-Tooltip zusätzlich bestehen.

### FR-4: PDF-Export
* Die Kennzahlen erscheinen **auch im Statistik-PDF** (`statistik.jrxml`) je Monat, analog zur Bildschirmanzeige: Quoten-KPIs (in beiden Modi) sowie – wo vorhanden – die als „berechnet/geschätzt" markierten Batterie-KPIs. Der Summen-Vergleich im PDF folgt derselben Modus-Logik wie die Bildschirmanzeige (im Bilanzmodus durch das Kennzahlen-Panel ersetzt).

### FR-5: Persistierung & i18n
* **Keine** neue Tabelle/Spalte. Die Kennzahlen werden **berechnet** und im `MonatsStatistikDTO` als zusätzliche (transiente) Felder geliefert – kein Schema-Change.
* Neue Übersetzungs-Keys via Flyway (`ON CONFLICT (key) DO NOTHING`, DE/EN), u.a.: `STATISTIK_KENNZAHLEN`, `KENNZAHL_AUTARKIEGRAD`, `KENNZAHL_EIGENVERBRAUCHSQUOTE`, `KENNZAHL_NETZBEZUGSQUOTE`, `KENNZAHL_EINSPEISEQUOTE`, `KENNZAHL_ZEV_EIGENVERBRAUCH`, `KENNZAHL_BATTERIE_NETTO`, `KENNZAHL_BATTERIE_GELADEN`, `KENNZAHL_BATTERIE_ENTLADEN`, `KENNZAHL_BATTERIE_WIRKUNGSGRAD`, `KENNZAHL_BERECHNET`, `KENNZAHL_BERECHNET_HINWEIS`.
* **Hinweis-Keys je Kennzahl** (Tooltips, FR-3.5): `KENNZAHL_AUTARKIEGRAD_HINWEIS`, `KENNZAHL_EIGENVERBRAUCHSQUOTE_HINWEIS`, `KENNZAHL_NETZBEZUGSQUOTE_HINWEIS`, `KENNZAHL_EINSPEISEQUOTE_HINWEIS`, `KENNZAHL_ZEV_EIGENVERBRAUCH_HINWEIS`, `KENNZAHL_BATTERIE_NETTO_HINWEIS`, `KENNZAHL_BATTERIE_GELADEN_HINWEIS`, `KENNZAHL_BATTERIE_ENTLADEN_HINWEIS`, `KENNZAHL_BATTERIE_WIRKUNGSGRAD_HINWEIS`.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [x] Je Monat wird ein Kennzahlen-Panel mit Autarkiegrad, Eigenverbrauchsquote, Netzbezugsquote, Einspeisequote und ZEV-Eigenverbrauch angezeigt.
* [x] Die Quoten-KPIs (Autarkiegrad, Eigenverbrauchsquote, Netzbezugs-/Einspeisequote, ZEV-Eigenverbrauch) werden in **beiden** Verteilmodi identisch berechnet und benötigen **keinen** Bilanz-Bezug.
* [x] **Autarkiegrad** = `summeConsumerZev / summeConsumerTotal`; Beispiel `Cz=600, C=1000` → `60.0 %`.
* [x] **Eigenverbrauchsquote** = `summeProducerZev / summeProducerTotal`; Beispiel `Pz=900, P=1200` → `75.0 %`. Im Modus `BILANZ` entspricht `Pz` der nicht eingespeisten Produktion `max(0, P − R)` (Batterieladung zählt mit); im Modus `PRODUCER_MESSUNG` der direkt verteilten Produktion.
* [x] **Netzbezugsquote** = `1 − Autarkiegrad` und **Einspeisequote** = `1 − Eigenverbrauchsquote`; im Beispiel `40.0 %` bzw. `25.0 %`.
* [x] **ZEV-Eigenverbrauch** = `summeConsumerZev` (kWh); im Beispiel `600 kWh`.
* [x] **Netto-Speicherfluss** = `Produktion − Verbrauch + Bezug − Rücklieferung`; Beispiel `P=1200, C=1000, B=400, R=300` → `+300 kWh` (Netto-Ladung).
* [x] **Batterie geladen/entladen** werden aus der Pro-Intervall-Summe der positiven bzw. negativen Nettos gebildet; Beispiel je Intervall `+5 / −3 / +2` → geladen `7`, entladen `3`.
* [x] **Round-Trip-Wirkungsgrad** = `entladen / geladen`; Beispiel `entladen=3, geladen=7` → `42.9 %`. Bei `geladen = 0` wird „–" angezeigt (keine Division durch 0).
* [x] Fehlt Producer (Nenner `P = 0`) → Eigenverbrauchsquote/Einspeisequote „–"; fehlt Verbrauch (Nenner `C = 0`) → Autarkiegrad/Netzbezugsquote „–". Keine Exception, kein NaN/Infinity.
* [ ] **Autarkiegrad (gemessen)** = `1 − bilanzBezug / summeConsumerTotal` und **Netzbezugsquote (gemessen)** = `bilanzBezug / summeConsumerTotal`; Beispiel `C=1000, B=38` → `96.2 %` bzw. `3.8 %`.
* [ ] Die beiden gemessenen Zeilen stehen unmittelbar **hinter** ihrem gerechneten Gegenstück (Autarkiegrad → Autarkiegrad (gemessen); Netzbezugsquote → Netzbezugsquote (gemessen)).
* [ ] Ohne `BEZUG`-Bilanz-Einheit entfallen beide Zeilen ganz (keine „–"-Zeilen); die übrigen Kennzahlen bleiben unverändert.
* [ ] Bei `C = 0` entfallen die gemessenen Zeilen ebenso wie Autarkiegrad und Netzbezugsquote.
* [ ] Deckt der `BEZUG` weniger 15-Min-Intervalle ab als die Konsumenten, tragen die **gemessenen** Zeilen die Kennzeichnung **„lückenhaft"**; der Wert wird trotzdem angezeigt.
* [ ] Im Modus `BILANZ` tragen bei denselben Lücken **auch** die gerechneten Zeilen (Autarkiegrad, Netzbezugsquote) die Kennzeichnung – dort stammt der ZEV-Anteil ebenfalls aus den Bilanzdaten.
* [ ] Im Modus `PRODUCER_MESSUNG` bleiben die gerechneten Zeilen bei Bilanz-Lücken **ungekennzeichnet**.
* [ ] Die beiden Kennzeichnungen tragen **unterschiedliche** Hinweistexte: gemessen „zu günstig", gerechnet „zu ungünstig", jeweils mit dem Hinweis, dass der wahre Wert dazwischen liegt.
* [ ] Die gemessenen Zeilen sind **nicht** als „berechnet/geschätzt" gekennzeichnet – sie stammen aus einer Messung, nicht aus einem Residuum.
* [ ] Beide Zeilen erscheinen auch im **PDF-Export**, mit demselben Lücken-Hinweis.
* [x] Fehlt Bilanz-Bezug oder Rücklieferung → nur die Batterie-KPIs (Netto-Speicherfluss, geladen/entladen/Wirkungsgrad) werden als **„–"** angezeigt; die Quoten-KPIs bleiben verfügbar.
* [x] Batterie-Kennzahlen (inkl. Netto-Speicherfluss) sind als **„berechnet/geschätzt"** gekennzeichnet und werden nur bei vorhandenen Producer- **und** Bilanz-Daten (Bezug + Rücklieferung) gezeigt.
* [x] Im **Producer-Messung**-Modus bleibt der bestehende Summen-Vergleich erhalten; im **Bilanzmodus** wird er ausgeblendet und durch das Kennzahlen-Panel ersetzt.
* [x] Die Kennzahlen erscheinen auch im **Statistik-PDF** (`statistik.jrxml`) je Monat mit derselben Modus-Logik wie am Bildschirm.
* [x] Alle Texte via `TranslationService` (DE/EN); Prozentwerte mit `%`, kWh-Werte in kWh.
* [x] Alle Kennzahlen (Prozent- und kWh-Werte) verwenden den **Punkt** als Dezimaltrennzeichen und – bei Werten ≥ 1000 – das **Hochkomma** (`'`) als Tausendertrennzeichen, am Bildschirm und im PDF, unabhängig von der Server-/Browser-Locale (`60.0 %`; `1'234.567 kWh`, nicht `60,0 %` / `1,234.567 kWh`).
* [x] Die Kennzahlen werden **tabellarisch** dargestellt (`.zev-table`/`.zev-table--compact`/`.zev-table--auto`, **ohne Titelzeile**): drei Spalten Bezeichnung (links) | Wert (rechtsbündig, `.zev-table__number`) | Einheit (links). Die Tabelle nutzt Inhaltsbreite (`.zev-table--auto`), die Bezeichnungs-Spalte ist nicht überbreit. Keine komponenten-eigenen Ad-hoc-Styles; ausschliesslich Design-System-Klassen.
* [x] Jede Kennzahl zeigt beim Hovern einen erklärenden Hinweis (`title`-Tooltip), der die Bedeutung der Kennzahl beschreibt; die Hinweistexte sind via `TranslationService` (DE/EN) übersetzt.
* [x] Statistik bleibt mit `statistik:read` erreichbar; Multi-Tenancy unverändert.

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
* **Betroffener Code (Frontend):** `statistik.component.*` (+ `statistik.model.ts`) — Kennzahlen-Panel als tabellarische Darstellung mit Design-System-Bausteinen (`.zev-table`/`.zev-table--compact`/`.zev-table--auto`, Wert `.zev-table__number`), „berechnet"-Kennzeichnung via `.zev-tag`, `title`-Tooltip je Zeile; View-Model `getKennzahlen()`.
* **Design-System (`design-system/src/components/table/table.css`):** neuer Modifier `.zev-table--auto` (Inhaltsbreite) + Showcase-Eintrag.
* **PDF-Export (Backend):** `service/StatistikPdfService.java` + `reports/statistik.jrxml` — Kennzahlen je Monat ins PDF aufnehmen (Quoten-KPIs in beiden Modi, Batterie-KPIs als „berechnet"; Summen-Vergleich im Bilanzmodus ausgeblendet).
* **PDF-Zahlenformat (Backend):** `util/PdfNumberFormat.java` — zentrale, locale-unabhängige Formatierung (Punkt-Dezimal, Hochkomma-Gruppierung) für **das gesamte** Statistik-PDF (`statistik.jrxml` **und** Subreport `einheit-summen.jrxml`), ersetzt die bisherigen `pattern="#,##0.000"`/`String.format(...)`-Stellen.
* **i18n:** neue Keys via Flyway – `V90` (Kennzahlen-Labels) und `V91` (erklärende Hinweise/Tooltips).
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
* [x] **Rundung/Format:** **Entschieden:** Prozentwerte mit 1 Nachkommastelle. **Dezimaltrennzeichen = Punkt** (`.`), **Tausendertrennzeichen = Hochkomma** (`'`, Schweizer Konvention) – am Bildschirm (`toFixed` + Gruppierung) und im PDF (`String.format(Locale.ROOT, …)` mit `,`→`'`), locale-unabhängig.
