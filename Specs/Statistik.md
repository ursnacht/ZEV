# Statistik

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Eine Statistikseite gibt einen Überblick über die Messdaten und Verteilungen eines wählbaren Zeitraums: Datenstand und Lücken, je Monat und über den ganzen Zeitraum die Summen, Summen-Vergleiche, „Summen pro Einheit“ und ein **Kennzahlen-Panel** mit Energie-KPIs (Autarkiegrad, Eigenverbrauchsquote, Netzbezugs-/Einspeisequote, ZEV-Eigenverbrauch) sowie – wo die Daten es zulassen – **Batterie-Kennzahlen** (Netto-Speicherfluss, geladen/entladen, Wirkungsgrad). Alles auch als PDF.
* **Warum machen wir das:** Ohne die Seite muss ich in die Datenbank schauen oder die Messwerte anzeigen, wenn ich sehen will, welche Daten und Verteilungen vorhanden sind. Im **Bilanzmodell** (`Specs/Bilanzmodell.md`) ist der Summen-Vergleich „berechneter Bezug ↔ Bilanz-Bezug“ zudem **tautologisch** (per Konstruktion ≈ 0); statt einer reinen Plausibilitätsprüfung zeigt die Seite echte Betriebs-Kennzahlen, die Betreiber und Mieter verstehen. Der **Batteriespeicher** ist im Bilanzmodell implizit enthalten und wird über die Kennzahlen sichtbar.
* **Ausgangslage:** Die Spec führt zwei frühere Dokumente zusammen – `Statistik.md` (Seite, Summen, Bedienung) und `Statistik-Kennzahlen.md` (Kennzahlen, FR-12 bis FR-16); Zuordnung der alten Nummern: Anhang A.1.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Zeitraum wählen
1. Ein Filter wählt den Datumsbereich (analog zur Seite Messwerte): „Datum von“, „Datum bis“ und der Quartal-Selektor.
2. Beim Öffnen der Seite ist das **vorangehende Quartal** gesetzt:
   * „Datum von“ und „Datum bis“ sind mit dem ersten bzw. letzten Tag des vorangehenden Quartals (relativ zum aktuellen Datum) vorbelegt;
   * der entsprechende Quartal-Button im Quartal-Selektor ist aktiv markiert;
   * Jahreswechsel: im Q1 wird Q4 des Vorjahres vorselektiert;
   * der Zeitraum bleibt manuell änderbar.
3. Der Quartal-Selektor setzt nur den Zeitraum; geladen wird mit „Anzeigen“ (FR-2). „Heute“ (FR-3) und das Blättern (FR-4) laden selbst.

### FR-2: Schaltfläche „Anzeigen“
1. „Anzeigen“ steht **rechts neben den Quartalsknöpfen**: Wer ein Quartal wählt, findet den Auslöser gleich daneben (Hintergrund: Anhang A.2).
2. Umgesetzt über einen **Aktions-Slot** des Quartal-Selektors (`ng-content`, Design System `.zev-quarter-selector__actions`); ohne Inhalt nimmt der Slot keinen Platz ein, die übrigen Verwendungen (Rechnungen, Messwerte, Debitoren, Solarberechnung) bleiben unverändert.
3. Verhalten: Submit des Formulars, deaktiviert während des Ladens oder ohne beide Daten, während des Ladens mit dem Text `LADE_DATEN`.

### FR-3: Schaltfläche „Heute“
1. „Heute“ steht nach den Blätterknöpfen ◀ / ▶ und setzt „Datum von“ **und** „Datum bis“ auf das heutige Datum (Ortszeit).
2. **Zeigt die Statistik sofort an** – wie beim Blättern ist der Umweg über „Anzeigen“ bei einem einzelnen Tag nur lästig (Hintergrund: Anhang A.2).
3. **Auch ein zweiter Klick lädt:** Für den laufenden Tag kommen laufend Messwerte dazu, „Heute“ ist damit zugleich „aktualisieren“. Gesperrt ist die Schaltfläche nur während des Ladens.
4. Icon `calendar`, `zev-button--secondary`; Text über den bestehenden Schlüssel `HEUTE` (seit V65) — **keine** neue Übersetzung, keine Migration.
5. Der Quartal-Selektor zeigt danach kein Quartal mehr als aktiv: Ein einzelner Tag ist kein Quartal.

### FR-4: Blättern ◀ / ▶
Zwei Schaltflächen **◀ / ▶** blättern den Zeitraum vor und zurück, ohne die Daten von Hand zu ändern (Entscheid vom 13.10.2026).
1. **Regel — geblättert wird um die Länge des Zeitraums:**

   | Zeitraum | Schritt | Beispiel ▶ |
   |---|---|---|
   | von = bis | 1 Tag | 13.10. → 14.10. |
   | ganze Monate (von = 1. eines Monats, bis = letzter Tag eines Monats) | Anzahl Monate; Monatsende angepasst | 01.01.–31.01. → 01.02.–28.02.; Quartal 01.07.–30.09. → 01.10.–31.12. |
   | sonst | Anzahl Tage | 01.10.–07.10. → 08.10.–14.10. |

2. **Lädt sofort** — wie „Heute“, anders als der Quartal-Selektor, der nur die Daten setzt: Blättern ohne Ergebnis hiesse, jedes Mal zusätzlich „Anzeigen“ zu klicken (Hintergrund: Anhang A.2).
3. Die Regel steckt in `blaettereZeitraum` (`utils/date-utils.ts`), rechnet mit lokalen Kalenderdaten (Schaltjahr, Sommerzeit).

### FR-5: Ladeanzeige
Während der Berechnung erscheint eine **Ladeanzeige**, weil das Laden einige Sekunden dauert und die Seite sonst wirkt, als reagiere sie nicht (Entscheid vom 11.10.2026).
* **Wiederverwendbar:** Aussehen im Design System (`.zev-loading` in `spinner.css`), Angular-Komponente `app-loading-indicator` (`components/loading-indicator/`) mit den Eingaben `text` (Übersetzungs-Key, Vorgabe `LADE_DATEN`) und `inline`. Zweiter Einsatz: **Rechnungen generieren** (`RECHNUNGEN_WERDEN_GENERIERT`).

### FR-6: Meldungen nach dem Laden
1. Nach dem Laden erscheint **keine Erfolgsmeldung**: Die angezeigte Statistik ist die Rückmeldung, und seit „Heute“ und das Blättern sofort laden, erschiene sie bei fast jedem Klick (Hintergrund: Anhang A.2).
2. Eine noch stehende Meldung (z.B. ein früherer Ladefehler) wird beim Start des Ladens weggeräumt.
3. Fehlermeldungen (Ladefehler, ungültiger Zeitraum, Export) bleiben bestehen.

### FR-7: Überblick über die Daten
* „Messwerte vorhanden bis“ mit Angabe des letzten Datums, für das Daten vorhanden sind.
* Angabe, ob für alle Consumer und Producer Daten bis zum Datum „Messwerte vorhanden bis“ vorliegen oder Lücken vorhanden sind, d.h. Daten von Einheiten fehlen oder Tage fehlen.

### FR-8: Monatswerte und Summen-Vergleich
Pro Monat zeigt die Seite:
1. den Zeitbereich;
2. ob für jeden Tag im Bereich Messdaten vorhanden sind, d.h. ob es für alle Einheiten Daten gibt;
3. die Summen:
   * Summe A: Summe total aller Producer (Produktion)
   * Summe B: Summe total aller Consumer (Verbrauch Total)
   * Summe C: Summe zev aller Producer (Verbrauch Anteil ZEV)
   * Summe D: Summe zev aller Consumer (Verbrauch Anteil ZEV)
   * Summe E: Summe zev_calculated aller Consumer (Verbrauch Anteil ZEV)
4. zwei berechnete Werte (kWh), die **nicht als Zeilen** in der Werte-Tabelle stehen, sondern nur im **Summen-Vergleich** gegen die Bilanz-Einheiten verwendet werden (`Specs/Bilanzmesspunkt.md` FR-4/FR-5.7; Hintergrund: Anhang A.3):
   * **Bezug von VNB** = Verbrauch (Consumer Total) − **gemessener** zev der Consumer (`summeConsumerTotal − summeConsumerZev`)
   * **Rücklieferung** = Produktion (Producer Total) − zev der Producer (`summeProducerTotal − summeProducerZev`, jeweils Absolutwerte)
   * Fachlich: *Bezug von VNB* = aus dem Netz bezogener Rest des Verbrauchs; *Rücklieferung* = ins Netz eingespeister Überschuss der Produktion.
5. Existieren **Bilanz-Einheiten** (Typen `BEZUG`/`RUECKLIEFERUNG`, `Specs/Bilanzmesspunkt.md` FR-5.7), erscheinen deren gemessene Bilanz-Summen als Zeilen in der Werte-Tabelle (Beschriftung = **Einheiten-Name**, mit Balken, Web + PDF); ohne Bilanz-Einheiten entfallen diese Zeilen und die zugehörigen Bilanz-Vergleiche.
6. die Vergleiche Summe C = Summe D, Summe C = Summe E, Summe D = Summe E, und eine Liste der Tage, an denen mind. eine Summe nicht gleich ist. Im Bilanzmodus tritt das Kennzahlen-Panel an die Stelle des Summen-Vergleichs (FR-14.2).
7. Die Werte werden grafisch dargestellt: Summen-Tabelle mit den Spalten „Beschreibung / Visualisierung / Wert“ (Balken; Hintergrund: Anhang A.3).
8. **Darstellung numerischer Werte:** Alle kWh-Werte in den Tabellen (Summen-Tabelle, „Summen pro Einheit“, Tage-mit-Abweichungen) stehen **rechtsbündig** – konsistent mit der PDF-Ausgabe. Umgesetzt über die Design-System-Klasse `.zev-table__number`.

### FR-9: Erklärende Hinweise in der Summen-Tabelle
Jede Zeile der Summen-Tabelle mit Balken trägt einen erklärenden Tooltip (natives `title`-Attribut auf dem `<tr>`), der die Bedeutung des dargestellten Werts kurz erläutert – **analog zu den Kennzahlen** (FR-14.5).
* Betroffen sind alle Zeilen: Produktion (Total), Verbrauch (Total), ZEV Produzent, ZEV Konsumenten, ZEV Konsumenten (berechnet) sowie – falls vorhanden – die beiden **Bilanz-Zeilen** (`BEZUG`/`RUECKLIEFERUNG`).
* Für die Bilanz-Zeilen ist der Hinweis **generisch** formuliert (die Beschriftung ist der Einheiten-Name bzw. im Bilanzmodus „Rücklieferung (gemessen)“), also unabhängig vom konkreten Einheiten-Namen.
* Die Hinweistexte kommen via `TranslationService` (DE/EN), Key-Konvention `<LABEL_KEY>_HINWEIS` wie bei den Kennzahlen.
* Rein additiv: **kein** neues CSS (natives `title`), keine Änderung an Werten, Balken oder der PDF-Ausgabe (Tooltips existieren nur im Web).

### FR-10: Monats-Panels aufklappbar
Die Monate eines Zeitraums (bei der Vorbelegung „Vorquartal“ drei) werden **aufklappbar** dargestellt und sind beim Laden **alle zugeklappt**.
> Ausgeklappt füllt ein einzelner Monat (Balken-Tabelle, Kennzahlen, Summen-Vergleich, „Summen pro Einheit“, Details) rund eine Bildschirmseite; drei Monate untereinander machen die Seite unübersichtlich. Zugeklappt passt der ganze Zeitraum auf einen Blick.
* Darstellung wie bei „Details anzeigen“ auf derselben Seite und bei den Mieter-Blöcken der Nebenkostenabrechnung (`Specs/Nebenkosten/Abrechnung.md`): Design-System-Baustein **Collapsible** (`.zev-collapsible`), also eine klickbare Kopfleiste mit Dreieck rechts.
* Die Kopfleiste zeigt Datenstatus-Punkt, Monatsname, Jahr und den Zeitraum – die geschlossene Ansicht sagt damit weiterhin aus, ob der Monat vollständige Daten hat.
* Jeder Monat wird **einzeln** auf- und zugeklappt; ein erneuter Klick schliesst ihn wieder.
* Ein neuer Abruf über „Anzeigen“ setzt alle Monate wieder auf zugeklappt – sonst bliebe ein Panel offen, das anschliessend einen anderen Monat zeigt.
* Die **Detail-Sektion innerhalb** eines Monats („Details anzeigen“ für fehlende Einheiten/Tage und Tage mit Abweichungen) bleibt davon unberührt und behält ihren eigenen Zustand.
* Rein visuell: **keine** Änderung an Werten, Berechnungen oder der **PDF-Ausgabe** (das PDF enthält weiterhin alle Monate vollständig), **keine** neuen Texte (Monatsname und Zeitraum stehen bereits in der Kopfzeile).

### FR-11: Gesamt-Panel über den gewählten Zeitraum
* Oberhalb der Monats-Panels steht ein Panel **„Gesamter Zeitraum“** mit denselben Inhalten wie ein Monat (Summentabelle, Kennzahlen, Vergleiche, Summen pro Einheit, Details) — aber über den **ganzen** gewählten Zeitraum. Kopfzeile: Datenstatus-Punkt, Titel, Zeitraum von–bis.
* Es startet **aufgeklappt**, die Monate zugeklappt, und lässt sich wie ein Monat zu- und aufklappen. Auch nach jedem neuen Abruf über „Anzeigen“ ist es wieder aufgeklappt.
* **Gerechnet, nicht addiert.** Das Backend berechnet das Panel mit **derselben** Methode wie einen Monat, nur über `von`–`bis`.
  > Summen liessen sich aus den Monaten addieren, die **Quoten** (Autarkiegrad, Eigenverbrauchsquote, Netzbezugs- und Einspeisequote) und der **Batterie-Wirkungsgrad** aber nicht: Ein Mittel aus Monatsquoten gewichtete einen trüben Monat gleich wie einen mit zehnmal mehr Energie. Aus den Gesamtsummen gerechnet, mit denselben Formeln, können Monat und Gesamt nicht auseinanderlaufen.
* **Auch bei nur einem Monat** wird es angezeigt. Sein Inhalt gleicht dann dem Monat — dafür sieht man die Zahlen sofort, ohne aufzuklappen.
* **Eine Vorlage für beide Panels** (Frontend, `ng-template`): Zwei Kopien liefen beim nächsten Ausbau eines Monats unbemerkt auseinander.
* **Auch im PDF**, als erster Block vor den Monaten — mit **demselben** Detail-Band wie ein Monat, nur mit der Überschrift „Gesamter Zeitraum (von – bis)“. Wie auf der Seite eine Vorlage für beide: Zwei Bänder liefen beim nächsten Ausbau auseinander.
  * Das Template erkennt das Gesamt an `monat == 0` und ruft für die Überschrift dann nicht `Month.of(monat)` auf: `Month.of(0)` wirft, der ganze Export bräche ab.
  * Die Bänder kommen aus einer **neuen** Liste (Gesamt + Monate), nicht aus der ergänzten Monatsliste der Statistik: Die stammt aus dem Cache, und das Gesamt stünde dort sonst dauerhaft — die Seite zeigte es danach ein zweites Mal als vermeintlichen Monat.
* **Aufwand:** eine zusätzliche Berechnung je Abruf (bei einem Quartal vier statt drei); das Ergebnis liegt im bestehenden Statistik-Cache.
* Übersetzungsschlüssel `STATISTIK_GESAMTER_ZEITRAUM` (V171).

### FR-12: Kennzahlen aus den Monats-Summen (Stufe 1)
Je Monat werden folgende KPIs berechnet und angezeigt. **Grundlage sind ausschliesslich die bereits vorhandenen ZEV-/Total-Summen** – dadurch sind die Quoten-KPIs **modus-agnostisch** und funktionieren in **beiden** Verteilmodi (`PRODUCER_MESSUNG` und `BILANZ`) identisch, ohne dass Bilanz-Bezug/Rücklieferung zwingend vorhanden sein müssen:
* Produktion `P = summeProducerTotal`
* Verbrauch `C = summeConsumerTotal`
* intern gedeckter Verbrauch `Cz = summeConsumerZev` (effektiver/gemessener ZEV-Anteil; bei MQTT-Daten der verteilte Anteil, bei CSV der gemessene Wert)
* intern genutzte Produktion `Pz = summeProducerZev` (Betrag; modus-abhängige Bedeutung, s. FR-12.2)

1. **Autarkiegrad** = `Cz / C` (Anteil des Verbrauchs, der intern – aus PV/Batterie – gedeckt wurde). Nur wenn `C > 0`.
2. **Eigenverbrauchsquote** = `Pz / P` (Anteil der Produktion, der intern genutzt statt eingespeist wurde). Nur wenn `P > 0`. Definition bewusst über die vorhandene ZEV-Summe `summeProducerZev`, damit die Kennzahl in beiden Verteilmodi ohne zusätzliche Bilanz-Daten verfügbar ist. **Wichtig – modus-abhängige Bedeutung** (Code: `MesswerteService.aktualisiereProducerZev`): Die *Berechnungsformel* ist in beiden Modi identisch, die *Bedeutung* von `Pz` unterscheidet sich jedoch:
   * `PRODUCER_MESSUNG`: `Pz` = die **direkt im ZEV verbrauchte** Produktion (die tatsächlich auf Consumer verteilte Menge). Eine (in diesem Modus nicht modellierte) Batterieladung zählt **nicht** mit.
   * `BILANZ`: `Pz` wird je Intervall als `max(0, |Produktion| − |Rücklieferung|)` gebildet – das ist die **nicht eingespeiste** Produktion und entspricht damit `(P − R)`. Eine Batterieladung senkt die Rücklieferung und **erhöht** `Pz`, zählt also im Bilanzmodus als „Eigenverbrauch“ mit. Das ist die gebräuchliche (Textbuch-)Eigenverbrauchsquote; die Kennzahl ist hier bewusst so akzeptiert.

   Siehe Anhang A.9 (Definition) und Abschnitt 5 (Edge Cases) für diese Modus-Abhängigkeit.
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
     * Die tages- und einheitengenaue Vollständigkeitsprüfung sieht solche Lücken **innerhalb** eines Tages nicht. Deshalb wird die Intervall-Abdeckung des `BEZUG` gegen die der Konsumenten gezählt; bei Unterdeckung werden die betroffenen Zeilen als **„lückenhaft“** gekennzeichnet – mit **je eigenem Hinweis**, weil die Verzerrung in entgegengesetzte Richtungen zeigt. Der Wert wird trotzdem angezeigt.
     * Im Modus `PRODUCER_MESSUNG` stammt `Cz` aus der Producer-Verteilung; fehlende `BEZUG`-Werte berühren ihn nicht – dort werden nur die **gemessenen** Zeilen gekennzeichnet.
7. **Netto-Speicherfluss** = `P − C + B − R` (absolut, kWh; > 0 = Netto-Ladung, < 0 = Netto-Entladung über den Zeitraum), mit Netzbezug `B = bilanzBezug`, Rücklieferung `R = bilanzRuecklieferung` (Betrag). **Berechneter Schätzwert** (Residuum der Energiebilanz, s. FR-13): nur wenn Producer **und** Bilanz-Bezug **und** Rücklieferung vorhanden; als **„berechnet/geschätzt“** gekennzeichnet.

* Prozentwerte werden auf sinnvolle Genauigkeit gerundet (z.B. 1 Nachkommastelle) und mit `%` dargestellt. **Dezimaltrennzeichen ist der Punkt** (`.`), **Tausendertrennzeichen das Hochkomma** (`'`, Schweizer Konvention) – locale-unabhängig (Frontend `toFixed`, PDF `String.format(Locale.ROOT, …)` mit Gruppierungs-Ersatz `,`→`'`).
* Die Quoten-KPIs (1–5) sind in beiden Verteilmodi verfügbar, sobald der jeweilige Nenner (`C` bzw. `P`) > 0 ist. Die gemessenen Quoten (6) setzen eine `BEZUG`-Einheit voraus; nur der Netto-Speicherfluss (7) und die Batterie-KPIs (FR-13) setzen zusätzlich Bilanz-Bezug **und** Rücklieferung voraus (ohne `SPEICHER`-Einheit, sonst FR-13a).
* Fehlt eine benötigte Grösse (kein Producer → Nenner `P = 0`, kein Verbrauch → `C = 0`), wird die betroffene Kennzahl als **„–“/n/a** angezeigt (kein Fehler, kein Abbruch). Fehlen für die Batterie-KPIs die Bilanz-Daten (ohne `SPEICHER`-Einheit, sonst FR-13a), entfällt der Batterie-Block (FR-13).

### FR-13: Batterie-Kennzahlen mit Pro-Intervall-Aggregation (Stufe 2)
Zusätzlich zum Netto-Speicherfluss (FR-12.7) werden – getrennt – **geladene** und **entladene** Energie ausgewiesen. Diese lassen sich **nicht** aus den Monats-Summen ableiten, sondern erfordern eine **neue Aggregation je 15-Minuten-Intervall** (die vorhandenen Zeitraum-Summen-Queries genügen dafür nicht, s. Abschnitt 6):

Pro Intervall `i`: `Netto_i = P_i − C_i + B_i − R_i`, dann über den Zeitraum:
1. **Batterie geladen** = `Σ max(0, Netto_i)`
2. **Batterie entladen** = `Σ max(0, −Netto_i)`
3. **Round-Trip-Wirkungsgrad** = `entladen / geladen` (nur wenn `geladen > 0`), als Prozent.

* **Kennzeichnung als Schätzwert:** Alle Batterie-Kennzahlen sind ein **Residuum** der Energiebilanz und enthalten damit auch Messfehler, Wandlungs-/Leitungsverluste und nicht gemessene Lasten. Sie werden **explizit als „berechnet“/„geschätzt“** gekennzeichnet (Hinweis/Tooltip).
* Nur anzeigen, wenn Producer **und** Bilanz-Bezug **und** Rücklieferung im Zeitraum vorhanden sind; sonst entfällt der Batterie-Block.

### FR-13a: Gemessene Batterie-Kennzahlen aus der Einheit `SPEICHER`
Existiert eine Einheit vom Typ **`SPEICHER`** (`Specs/Batteriespeicher.md`), stammen die Batterie-Kennzahlen **aus deren Messwerten** statt aus der Energiebilanz. Die gemessene Quelle hat **Vorrang**.

Beim Speicher gilt `total = ΔLadung − ΔEntladung`; über den Zeitraum getrennt summiert:
1. **Batterie geladen** = `Σ total` über die Intervalle mit `total > 0`
2. **Batterie entladen** = `Σ |total|` über die Intervalle mit `total < 0`
3. **Netto-Speicherfluss** = `geladen − entladen`
4. **Round-Trip-Wirkungsgrad** = `entladen / geladen` (nur wenn `geladen > 0`)

> **Eine schlichte Summe über `total` wäre nutzlos:** Sie ergäbe den Saldo, und der liegt über einen
> Monat nahe null. Getrennt summiert werden beide Mengen lesbar — und der Wirkungsgrad wird zur
> Kontrollgrösse: Ein Wert über 100 % ist der stille Hinweis auf vertauschte Register.

* **Kein „berechnet“-Hinweis.** Ein Zählerstand ist kein Residuum der Bilanz und enthält weder Messfehler noch nicht gemessene Lasten. Die Kennzeichnung entfällt in Web **und** PDF.
* **Keine Bilanz-Daten nötig.** Der Zähler der Batterie genügt: Weder Producer noch Bilanz-Bezug noch Rücklieferung werden vorausgesetzt. Ohne Speicher-Einheit gelten unverändert die Bedingungen aus FR-13.
* **Eine Abfrage statt eines Intervall-Loops:** Die Trennung in Ladung und Entladung erledigt die Datenbank (`sumLadungEntladungByEinheitTypAndZeitBetween`).
* **Einheit ohne Messwerte:** Ladung und Entladung sind `0`, der Wirkungsgrad bleibt leer — kein Fehler, nur nichts zu zeigen.

### FR-14: Anzeige der Kennzahlen & Verhältnis zum Summen-Vergleich
1. Das **Kennzahlen-Panel** wird je Monat angezeigt (in **beiden** Verteilmodi – die KPIs sind generell nützlich).
2. Im **Bilanzmodus** tritt das Kennzahlen-Panel an die Stelle des wenig aussagekräftigen Summen-Vergleichs: der **Summen-Vergleich wird im Bilanzmodus ausgeblendet**, das Kennzahlen-Panel ersetzt ihn.
3. Im **Producer-Messung**-Modus bleibt der Summen-Vergleich (FR-8.6) unverändert erhalten (dort ist er als Plausibilitätsprüfung sinnvoll); das Kennzahlen-Panel wird zusätzlich angezeigt.
4. Darstellung **tabellarisch** mit Design-System-Bausteinen (`@zev/design-system`): eine `.zev-table`/`.zev-table--compact`/`.zev-table--auto` **ohne Titelzeile** (kein `<thead>`) mit **drei Spalten** je Kennzahl. `.zev-table--auto` begrenzt die Tabelle auf ihre **Inhaltsbreite**, damit die Bezeichnungs-Spalte nicht unnötig breit wird (nicht `width:100%`):
   * Spalte 1 – **Bezeichnung** (linksbündig), inkl. „berechnet“-Markierung bei Batterie-Kennzahlen;
   * Spalte 2 – **Wert** (rechtsbündig, `.zev-table__number`), Zahlen ohne Einheit;
   * Spalte 3 – **Einheit** (linksbündig, z.B. `%`/`kWh`; bei n/a leer).
   Keine komponenten-eigenen Ad-hoc-Styles; alle Klassen bestehen im Design-System.
5. **Erklärender Hinweis je Kennzahl:** Jede Tabellenzeile (`<tr>`) trägt einen erklärenden Tooltip (natives `title`-Attribut), der dem Benutzer die Bedeutung der Kennzahl kurz erläutert. Die Hinweistexte sind via `TranslationService` übersetzt (DE/EN). Bei den Batterie-Kennzahlen bleibt der separate „berechnet“-Tooltip zusätzlich bestehen.

### FR-15: PDF-Export der Kennzahlen
* Die Kennzahlen erscheinen **auch im Statistik-PDF** (`statistik.jrxml`) je Monat, analog zur Bildschirmanzeige: Quoten-KPIs (in beiden Modi) sowie – wo vorhanden – die als „berechnet/geschätzt“ markierten Batterie-KPIs. Der Summen-Vergleich im PDF folgt derselben Modus-Logik wie die Bildschirmanzeige (im Bilanzmodus durch das Kennzahlen-Panel ersetzt).

### FR-16: Persistierung & i18n der Kennzahlen
* **Keine** neue Tabelle/Spalte. Die Kennzahlen werden **berechnet** und im `MonatsStatistikDTO` als zusätzliche (transiente) Felder geliefert – kein Schema-Change.
* Neue Übersetzungs-Keys via Flyway (`ON CONFLICT (key) DO NOTHING`, DE/EN), u.a.: `STATISTIK_KENNZAHLEN`, `KENNZAHL_AUTARKIEGRAD`, `KENNZAHL_EIGENVERBRAUCHSQUOTE`, `KENNZAHL_NETZBEZUGSQUOTE`, `KENNZAHL_EINSPEISEQUOTE`, `KENNZAHL_ZEV_EIGENVERBRAUCH`, `KENNZAHL_BATTERIE_NETTO`, `KENNZAHL_BATTERIE_GELADEN`, `KENNZAHL_BATTERIE_ENTLADEN`, `KENNZAHL_BATTERIE_WIRKUNGSGRAD`, `KENNZAHL_BERECHNET`, `KENNZAHL_BERECHNET_HINWEIS`.
* **Hinweis-Keys je Kennzahl** (Tooltips, FR-14.5): `KENNZAHL_AUTARKIEGRAD_HINWEIS`, `KENNZAHL_EIGENVERBRAUCHSQUOTE_HINWEIS`, `KENNZAHL_NETZBEZUGSQUOTE_HINWEIS`, `KENNZAHL_EINSPEISEQUOTE_HINWEIS`, `KENNZAHL_ZEV_EIGENVERBRAUCH_HINWEIS`, `KENNZAHL_BATTERIE_NETTO_HINWEIS`, `KENNZAHL_BATTERIE_GELADEN_HINWEIS`, `KENNZAHL_BATTERIE_ENTLADEN_HINWEIS`, `KENNZAHL_BATTERIE_WIRKUNGSGRAD_HINWEIS`.

### Übersetzungen (i18n)
* Alle Texte sind mehrsprachig und stehen in der Datenbank (`TranslationService`, Flyway-Migrationen). Kennzahlen: FR-16; Gesamt-Panel `STATISTIK_GESAMTER_ZEITRAUM` (V171); Ladeanzeige V177; Blättern `ZEITRAUM_ZURUECK`/`ZEITRAUM_VOR` (V181); „Heute“ über `HEUTE` (V65).

### Layout & Navigation
* Die Seite ist im Menü wählbar.
* Verwende das Design System; neue Designs werden ins Design System aufgenommen; das Design lehnt sich an die bisherigen Seiten an.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

### „Anzeigen“ (FR-2)
* [x] „Anzeigen“ steht in derselben Zeile rechts neben dem letzten Quartalsknopf, mit sichtbarem Abstand. (Test: quarter-selector.component.spec.ts › QuarterSelectorComponent - Aktions-Slot › should render projected content after the quarter buttons; E2E: statistik.spec.ts › Statistik Page - Heute › should set both dates to today and load immediately)
* [x] In der Zeile der Datumsfelder steht kein „Anzeigen“ mehr; dort bleiben ◀ / ▶, „Heute“ und „PDF exportieren“. (E2E: statistik.spec.ts › Statistik Page - Heute › should set both dates to today and load immediately; E2E: statistik.spec.ts › Statistik Page › should display the statistics page with date selection form)
* [x] Klick auf „Anzeigen“ lädt die Statistik des eingestellten Zeitraums. (Test: statistik.component.spec.ts › StatistikComponent › onSubmit › should call getStatistik with correct params; E2E: statistik.spec.ts › Statistik Page › should load and display statistics when date range is submitted)
* [ ] Andere Seiten mit Quartal-Selektor sehen unverändert aus.

### „Heute“ (FR-3)
* [x] Die Schaltfläche „Heute“ steht nach ◀ / ▶ („Anzeigen“ steht neben den Quartalen). (E2E: statistik.spec.ts › Statistik Page - Heute › should set both dates to today and load immediately)
* [x] Ein Klick setzt „Datum von“ und „Datum bis“ auf das heutige Datum. (Test: statistik.component.spec.ts › StatistikComponent › onHeute › should set dateFrom and dateTo to today; E2E: statistik.spec.ts › Statistik Page - Heute › should set both dates to today and load immediately)
* [x] Das Datum ist das **lokale** Tagesdatum, nicht das UTC-Datum — kurz nach Mitternacht darf nicht der Vortag erscheinen. (Test: statistik.component.spec.ts › StatistikComponent › onHeute › should use the local date shortly after midnight)
* [x] Ein Klick zeigt die Statistik des heutigen Tages sofort an (ohne „Anzeigen“). (Test: statistik.component.spec.ts › StatistikComponent › onHeute › should load statistics for today immediately; E2E: statistik.spec.ts › Statistik Page - Heute › should set both dates to today and load immediately)
* [x] Ein weiterer Klick bei bereits gezeigtem heutigem Tag lädt neu (aktualisiert). (Test: statistik.component.spec.ts › StatistikComponent › onHeute › should reload when today is already shown)
* [x] Während des Ladens ist die Schaltfläche deaktiviert. (Test: statistik.component.spec.ts › StatistikComponent › onHeute › should disable the button while loading)

### Blättern (FR-4)
* [x] Links von „Heute“ stehen zwei Schaltflächen ◀ / ▶ (Icons `chevron-left`/`chevron-right`, übersetzte Beschriftung `ZEITRAUM_ZURUECK`/`ZEITRAUM_VOR` als Tooltip und `aria-label`). (Test: statistik.component.spec.ts › StatistikComponent › Blättern › should render chevron icons with translated title and aria-label)
* [x] Ein Tag blättert um einen Tag, auch über Monats- und Jahresgrenzen. (Test: date-utils.spec.ts › Date Utils › blaettereZeitraum › should move a single day by one day; Test: date-utils.spec.ts › Date Utils › blaettereZeitraum › should move a single day across month and year)
* [x] Ein ganzer Monat blättert monatsweise und passt das Monatsende an (Januar → Februar 28./29., Mai ← April 30.). (Test: date-utils.spec.ts › Date Utils › blaettereZeitraum › should move a whole month and adjust the month end; Test: date-utils.spec.ts › Date Utils › blaettereZeitraum › should respect leap years)
* [x] Mehrere ganze Monate (Quartal, Jahr) blättern um ihre Anzahl Monate. (Test: date-utils.spec.ts › Date Utils › blaettereZeitraum › should move a quarter by three months; Test: date-utils.spec.ts › Date Utils › blaettereZeitraum › should move a year by twelve months)
* [x] Andere Zeiträume blättern um ihre Anzahl Tage; 01.–15. ist kein ganzer Monat (→ 16.–30.). (Test: date-utils.spec.ts › Date Utils › blaettereZeitraum › should move other ranges by their number of days; Test: date-utils.spec.ts › Date Utils › blaettereZeitraum › should not treat the 1st to a mid-month day as a whole month)
* [x] Über die Zeitumstellung bleiben 7 Tage 7 Tage. (Test: date-utils.spec.ts › Date Utils › blaettereZeitraum › should count days correctly across the daylight saving change)
* [x] Nach dem Blättern wird die Statistik sofort geladen. (Test: statistik.component.spec.ts › StatistikComponent › Blättern › should move a month forward and load immediately; E2E: statistik.spec.ts › Statistik Page - Blaettern › should page a whole month and load immediately)
* [x] Während des Ladens und bei ungültigem Zeitraum (von > bis, leer) sind die Schaltflächen gesperrt. (Test: statistik.component.spec.ts › StatistikComponent › Blättern › should not move while loading or with an invalid range; Test: date-utils.spec.ts › Date Utils › blaettereZeitraum › should leave an invalid range unchanged)

### Ladeanzeige (FR-5)
* [x] Während die Statistik berechnet wird, erscheint unter dem Formular ein Spinner mit dem Text „Statistik wird berechnet …“. (Test: statistik.component.spec.ts › StatistikComponent › Ladeanzeige › should show the loading indicator while loading)
* [x] Ist die Berechnung fertig (oder fehlgeschlagen), verschwindet die Anzeige. (Test: statistik.component.spec.ts › StatistikComponent › Ladeanzeige › should not show the loading indicator when idle; Test: statistik.component.spec.ts › StatistikComponent › onSubmit › should set loading to false after success; Test: statistik.component.spec.ts › StatistikComponent › onSubmit › should set loading to false on error)
* [x] Auf der Seite „Rechnungen“ erscheint während des Generierens dieselbe Anzeige mit „Rechnungen werden generiert …“. (Test: rechnungen.component.spec.ts › RechnungenComponent › Ladeanzeige › should show the loading indicator while generating)
* [ ] Die Anzeige erscheint erst nach **300 ms**; schnellere Antworten zeigen sie nicht (kein Aufblitzen).
* [x] Screenreader: Die Anzeige ist `role="status"` mit `aria-live="polite"`, der Spinner selbst `aria-hidden`. (Test: loading-indicator.component.spec.ts › LoadingIndicatorComponent › initialization › should announce itself as a status; Test: loading-indicator.component.spec.ts › LoadingIndicatorComponent › initialization › should render a spinner hidden from screen readers)
* [ ] Bei „weniger Bewegung“ (`prefers-reduced-motion`) dreht der Spinner langsamer.
* [x] Die Texte sind übersetzt (V177). (Nachweis: V177__Add_Ladeanzeige_Translations.sql:3-4)
* [x] Der Design-System-Showcase zeigt die Anzeige (Standard und Inline) mit ihren CSS-Klassen. (Nachweis: design-system-showcase.component.html:375-394)

### Meldungen nach dem Laden (FR-6)
* [x] Nach erfolgreichem Laden steht keine Meldung. (Test: statistik.component.spec.ts › StatistikComponent › onSubmit › should not show a message after loading)
* [x] Eine Fehlermeldung eines früheren Ladeversuchs verschwindet beim nächsten Laden. (Test: statistik.component.spec.ts › StatistikComponent › onSubmit › should clear a previous error message when loading again)
* [x] Ein Ladefehler wird weiterhin als Fehlermeldung angezeigt. (Test: statistik.component.spec.ts › StatistikComponent › onSubmit › should show error message on service failure; Test: statistik.component.spec.ts › StatistikComponent › message auto-dismiss › should not auto-dismiss error messages)

### Erklärende Hinweise in der Summen-Tabelle (FR-9)
* [x] Hovern über eine Zeile der Summen-Tabelle zeigt einen erklärenden Hinweis; jede der fünf Standard-Zeilen hat einen **eigenen**, inhaltlich passenden Text. (Test: statistik.component.spec.ts › StatistikComponent › Summen-Tabelle Hinweise › should give each of the five standard rows its own hint)
* [x] Sind Bilanz-Einheiten vorhanden, tragen auch deren Zeilen einen Hinweis; fehlen sie, entfallen die Zeilen samt Hinweis (keine leeren Tooltips). (Test: statistik.component.spec.ts › StatistikComponent › Summen-Tabelle Hinweise › should add hinted balance rows only when balance units exist; Test: statistik.component.spec.ts › StatistikComponent › Summen-Tabelle Hinweise › should give each of the five standard rows its own hint; Test: statistik.component.spec.ts › StatistikComponent › Summen-Tabelle Hinweise › should not render empty tooltips)
* [x] Die Hinweise sind in DE und EN vorhanden und werden über den `TranslationService` geladen (keine Hardcodings im Template). (Nachweis: V99__Add_Statistik_Visualisierung_Hinweise_Translations.sql:4-10; statistik.component.html:194-294)
* [x] Alle Hinweis-Keys sind per Flyway-Migration angelegt: Die `TranslatePipe` fällt bei fehlendem Key auf den **Key-Namen** zurück (`TranslationService.translate`), ein Tooltip wie „PRODUKTION_TOTAL_HINWEIS“ wäre also direkt sichtbar und gilt als Fehler. (Nachweis: V99__Add_Statistik_Visualisierung_Hinweise_Translations.sql:4-10)

### Monats-Panels (FR-10)
* [x] Nach dem Laden einer Statistik sind alle Monats-Panels zugeklappt; die Kopfzeilen aller Monate sind sichtbar, aber kein Monatsinhalt (Balken-Tabelle, Kennzahlen, „Summen pro Einheit“). (Test: statistik.component.spec.ts › StatistikComponent › initialization › should start with every month panel collapsed; Test: statistik.component.spec.ts › StatistikComponent › Gesamt-Panel › should show the total panel expanded and the months collapsed; E2E: statistik.spec.ts › Statistik Page - Monthly Statistics › should start with all month panels collapsed and toggle them individually)
* [x] Ein Klick auf die Kopfleiste eines Monats klappt genau diesen Monat auf; die übrigen bleiben zugeklappt. (Test: statistik.component.spec.ts › StatistikComponent › toggleMonthPanel › should handle multiple month panels independently; E2E: statistik.spec.ts › Statistik Page - Monthly Statistics › should start with all month panels collapsed and toggle them individually)
* [x] Ein erneuter Klick auf dieselbe Kopfleiste klappt den Monat wieder zu. (Test: statistik.component.spec.ts › StatistikComponent › toggleMonthPanel › should collapse an expanded month panel; E2E: statistik.spec.ts › Statistik Page - Monthly Statistics › should start with all month panels collapsed and toggle them individually)
* [x] Die Kopfleiste zeigt auch im zugeklappten Zustand Monatsname, Jahr, Zeitraum und den Datenstatus-Punkt. (Test: statistik.component.spec.ts › StatistikComponent › Monats-Panels › should show month name, year, range and status dot in a collapsed header; Test: statistik.component.spec.ts › StatistikComponent › Monats-Panels › should show an error dot in the collapsed header of an incomplete month)
* [x] Kopfleiste und Inhalt verwenden den bestehenden Collapsible-Baustein des Design Systems – kein eigenes CSS für die Statistik-Seite. (Nachweis: statistik.component.css:1-5 ohne Regeln; E2E: statistik.spec.ts › Statistik Page - Monthly Statistics › should start with all month panels collapsed and toggle them individually)
* [x] Ein erneuter Abruf über „Anzeigen“ klappt alle Monate wieder zu. (Test: statistik.component.spec.ts › StatistikComponent › onSubmit › should collapse all month panels on submit)
* [x] Der Zustand von „Details anzeigen“ innerhalb eines Monats wird durch das Auf-/Zuklappen des Monats nicht verändert. (Test: statistik.component.spec.ts › StatistikComponent › toggleMonthPanel › should not touch the details toggle of the same month)
* [x] Die PDF-Ausgabe ist unverändert (alle Monate vollständig). (Test: StatistikPdfServiceTest#fuelle_MitGesamt_StehtVorDenMonaten; Test: StatistikPdfServiceTest#fuelle_OhneGesamt_NurMonate)

### Gesamt-Panel (FR-11)
* [x] Oberhalb der Monats-Panels steht ein Panel „Gesamter Zeitraum“ mit dem gewählten Zeitraum von–bis in der Kopfzeile. (Test: statistik.component.spec.ts › StatistikComponent › Gesamt-Panel › should render the total panel above the months; Test: statistik.component.spec.ts › StatistikComponent › Gesamt-Panel › should show the selected range in the header)
* [x] Es ist nach dem Laden aufgeklappt, die Monate sind zugeklappt. (Test: statistik.component.spec.ts › StatistikComponent › onSubmit › should expand the total panel on submit; Test: statistik.component.spec.ts › StatistikComponent › Gesamt-Panel › should show the total panel expanded and the months collapsed; E2E: statistik.spec.ts › Statistik Page - Gesamter Zeitraum › should show the total panel above the months, expanded; E2E: statistik.spec.ts › Statistik Page - Gesamter Zeitraum › should re-expand the total panel on a new query)
* [x] Es zeigt dieselben Bereiche wie ein aufgeklappter Monat. (Test: statistik.component.spec.ts › StatistikComponent › Gesamt-Panel › should render the same content as a month; E2E: statistik.spec.ts › Statistik Page - Gesamter Zeitraum › should show the total panel above the months, expanded)
* [x] Die Summen entsprechen dem ganzen Zeitraum; die Quoten sind aus den **Gesamtsummen** gerechnet, nicht aus den Monatsquoten gemittelt. (Test: StatistikServiceTest#getStatistik_Gesamt_RechnetUeberDenGanzenZeitraum)
* [x] Bei angebrochenen Monaten am Rand trägt es genau den gewählten Zeitraum (z. B. 15.01.–10.02.). (Test: StatistikServiceTest#getStatistik_Gesamt_TraegtDenGewaehltenZeitraum)
* [x] „Details anzeigen“ im Gesamt-Panel hat einen eigenen Zustand, unabhängig von den Monaten. (Test: statistik.component.spec.ts › StatistikComponent › Gesamt-Panel › should keep the details state separate from the first month)
* [x] Das PDF beginnt nach der Übersicht mit dem Block „Gesamter Zeitraum (von – bis)“, danach folgen die Monate; Inhalt wie ein Monatsblock. (Test: StatistikPdfServiceTest#fuelle_MitGesamt_StehtVorDenMonaten; Test: StatistikPdfServiceTest#generatePdf_MitGesamt_ErzeugtEinPdf)
* [x] Die Überschrift ist übersetzt („Entire period“ auf Englisch). (Test: StatistikPdfServiceTest#fuelle_Englisch_UebersetztDieUeberschrift; Nachweis: V171__Add_Statistik_Gesamter_Zeitraum_Translation.sql:5-7)
* [x] Ein PDF-Export ändert die Monatsliste der Seite nicht — auch nach mehreren Exporten erscheint das Gesamt-Panel auf der Seite genau einmal. (Test: StatistikPdfServiceTest#baender_VeraendertDieGecachteMonatslisteNicht)
* [x] Der CSV-Download im Gesamt-Panel exportiert den ganzen Zeitraum; der Dateiname trägt dann von und bis statt eines Monats. (Test: statistik.component.spec.ts › StatistikComponent › onDownloadCsv › should name a multi-month export after its range)

### Kennzahlen (FR-12 bis FR-16)
* [x] Je Monat wird ein Kennzahlen-Panel mit Autarkiegrad, Eigenverbrauchsquote, Netzbezugsquote, Einspeisequote und ZEV-Eigenverbrauch angezeigt. (Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should omit the measured rows when there is no balance unit; E2E: statistik.spec.ts › Statistik Page - Monthly Statistics › should display monthly panels with data tables when statistics are loaded)
* [x] Die Quoten-KPIs (Autarkiegrad, Eigenverbrauchsquote, Netzbezugs-/Einspeisequote, ZEV-Eigenverbrauch) werden in **beiden** Verteilmodi identisch berechnet und benötigen **keinen** Bilanz-Bezug. (Test: StatistikServiceTest#berechneKennzahlen_QuotenAusSummen_KorrektBerechnet; Test: StatistikServiceTest#berechneKennzahlen_KeineBilanzDaten_BatterieNichtVerfuegbar)
* [x] **Autarkiegrad** = `summeConsumerZev / summeConsumerTotal`; Beispiel `Cz=600, C=1000` → `60.0 %`. (Test: StatistikServiceTest#berechneKennzahlen_QuotenAusSummen_KorrektBerechnet; Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should format quota rows as percent with 1 decimal and "%" unit)
* [x] **Eigenverbrauchsquote** = `summeProducerZev / summeProducerTotal`; Beispiel `Pz=900, P=1200` → `75.0 %`. Im Modus `BILANZ` entspricht `Pz` der nicht eingespeisten Produktion `max(0, P − R)` (Batterieladung zählt mit); im Modus `PRODUCER_MESSUNG` der direkt verteilten Produktion. (Test: StatistikServiceTest#berechneKennzahlen_QuotenAusSummen_KorrektBerechnet; Test: MesswerteServiceTest#calculateSolarDistribution_Bilanz_MqttProducer_ProducerZevAusProduktionMinusRuecklieferung; Test: MesswerteServiceTest#calculateSolarDistribution_MqttProducer_ZevIstVerteilteMenge)
* [x] **Netzbezugsquote** = `1 − Autarkiegrad` und **Einspeisequote** = `1 − Eigenverbrauchsquote`; im Beispiel `40.0 %` bzw. `25.0 %`. (Test: StatistikServiceTest#berechneKennzahlen_QuotenAusSummen_KorrektBerechnet; Test: StatistikServiceTest#berechneKennzahlen_NetzbezugsUndEinspeisequote_SindKomplemente)
* [x] **ZEV-Eigenverbrauch** = `summeConsumerZev` (kWh); im Beispiel `600 kWh`. (Test: StatistikServiceTest#berechneKennzahlen_QuotenAusSummen_KorrektBerechnet; Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should format ZEV-Eigenverbrauch with 3 decimals and "kWh" unit)
* [x] **Netto-Speicherfluss** = `Produktion − Verbrauch + Bezug − Rücklieferung`; Beispiel `P=1200, C=1000, B=400, R=300` → `+300 kWh` (Netto-Ladung). (Test: StatistikServiceTest#berechneKennzahlen_MitBilanzDaten_BatterieNettoBerechnet; Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should prefix positive Batterie-Netto with "+" and unit "kWh")
* [x] **Batterie geladen/entladen** werden aus der Pro-Intervall-Summe der positiven bzw. negativen Nettos gebildet; Beispiel je Intervall `+5 / −3 / +2` → geladen `7`, entladen `3`. (Test: StatistikServiceTest#berechneBatterieKennzahlen_ProIntervallAggregation_GeladenEntladenWirkungsgrad)
* [x] **Round-Trip-Wirkungsgrad** = `entladen / geladen`; Beispiel `entladen=3, geladen=7` → `42.9 %`. Bei `geladen = 0` wird „–“ angezeigt (keine Division durch 0). (Test: StatistikServiceTest#berechneBatterieKennzahlen_ProIntervallAggregation_GeladenEntladenWirkungsgrad; Test: StatistikServiceTest#berechneBatterieKennzahlen_GeladenNull_WirkungsgradNull; Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should render quota rows as "–" with empty unit when value is null)
* [x] Fehlt Producer (Nenner `P = 0`) → Eigenverbrauchsquote/Einspeisequote „–“; fehlt Verbrauch (Nenner `C = 0`) → Autarkiegrad/Netzbezugsquote „–“. Keine Exception, kein NaN/Infinity. (Test: StatistikServiceTest#berechneKennzahlen_KeinProducer_EigenverbrauchsUndEinspeisequoteNull; Test: StatistikServiceTest#berechneKennzahlen_KeinVerbrauch_AutarkiegradUndNetzbezugsquoteNull; Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should render quota rows as "–" with empty unit when value is null)
* [x] **Autarkiegrad (gemessen)** = `1 − bilanzBezug / summeConsumerTotal` und **Netzbezugsquote (gemessen)** = `bilanzBezug / summeConsumerTotal`; Beispiel `C=1000, B=38` → `96.2 %` bzw. `3.8 %`. (Test: StatistikServiceTest#berechneKennzahlen_MitBezugEinheit_GemesseneQuotenAusMessung; Test: StatistikServiceTest#berechneKennzahlen_GemesseneUndGerechneteQuote_SindJeweilsKomplemente; Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should format the measured figures as percentages)
* [x] Die beiden gemessenen Zeilen stehen unmittelbar **hinter** ihrem gerechneten Gegenstück (Autarkiegrad → Autarkiegrad (gemessen); Netzbezugsquote → Netzbezugsquote (gemessen)). (Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should place each measured figure right after its calculated counterpart)
* [x] Ohne `BEZUG`-Bilanz-Einheit entfallen beide Zeilen ganz (keine „–“-Zeilen); die übrigen Kennzahlen bleiben unverändert. (Test: StatistikServiceTest#berechneKennzahlen_OhneBezugEinheit_GemesseneQuotenNull; Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should omit the measured rows when there is no balance unit)
* [x] Bei `C = 0` entfallen die gemessenen Zeilen; Autarkiegrad und Netzbezugsquote zeigen „–“. (Test: StatistikServiceTest#berechneKennzahlen_KeinVerbrauch_GemesseneQuotenNull; Test: StatistikServiceTest#berechneKennzahlen_KeinVerbrauch_AutarkiegradUndNetzbezugsquoteNull; Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should omit the measured rows when there is no balance unit; Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should render quota rows as "–" with empty unit when value is null)
* [x] Deckt der `BEZUG` weniger 15-Min-Intervalle ab als die Konsumenten, tragen die **gemessenen** Zeilen die Kennzeichnung **„lückenhaft“**; der Wert wird trotzdem angezeigt. (Test: StatistikServiceTest#berechneKennzahlen_BezugMitLuecken_GemesseneZeilenGekennzeichnet; Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should mark the measured figures when the grid supply has gaps)
* [x] Im Modus `BILANZ` tragen bei denselben Lücken **auch** die gerechneten Zeilen (Autarkiegrad, Netzbezugsquote) die Kennzeichnung – dort stammt der ZEV-Anteil ebenfalls aus den Bilanzdaten. (Test: StatistikServiceTest#berechneKennzahlen_BezugMitLueckenImBilanzmodus_AuchGerechneteZeilenGekennzeichnet; Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should mark the calculated figures too when the distribution skipped intervals)
* [x] Im Modus `PRODUCER_MESSUNG` bleiben die gerechneten Zeilen bei Bilanz-Lücken **ungekennzeichnet**. (Test: StatistikServiceTest#berechneKennzahlen_BezugMitLueckenImProducerModus_GerechneteZeilenUnberuehrt; Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should leave the calculated figures unmarked outside the balance mode)
* [x] Die beiden Kennzeichnungen tragen **unterschiedliche** Hinweistexte: gemessen „zu günstig“, gerechnet „zu ungünstig“, jeweils mit dem Hinweis, dass der wahre Wert dazwischen liegt. (Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should explain each gap with its own direction; Nachweis: V134__Statistik_Kennzahlen_Luecken_Richtung.sql, V137__Uebersetzungen_Mit_Umlauten.sql)
* [x] Die gemessenen Zeilen sind **nicht** als „berechnet/geschätzt“ gekennzeichnet – sie stammen aus einer Messung, nicht aus einem Residuum. (Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should format the measured figures as percentages)
* [ ] Beide Zeilen erscheinen auch im **PDF-Export**, mit demselben Lücken-Hinweis. (Test: StatistikPdfServiceTest#fuelle_GemesseneKennzahlenMitLuecken_ZeigtZeilenUndHinweis; Test: StatistikPdfServiceTest#fuelle_GemesseneKennzahlenMitVerteilungsluecken_ZeigtVerteilungsHinweis; Test: StatistikPdfServiceTest#fuelle_GemesseneKennzahlenOhneLuecken_KeinHinweis; Test: StatistikPdfServiceTest#fuelle_OhneBezugEinheit_KeineGemessenenZeilen)
* [x] Fehlt Bilanz-Bezug oder Rücklieferung (ohne `SPEICHER`-Einheit, sonst FR-13a) → der Batterie-Block (Netto-Speicherfluss, geladen/entladen/Wirkungsgrad) entfällt; die Quoten-KPIs bleiben verfügbar. (Test: StatistikServiceTest#berechneKennzahlen_KeineBilanzDaten_BatterieNichtVerfuegbar; Test: StatistikServiceTest#berechneKennzahlen_NurBezugOhneRuecklieferung_BatterieNichtVerfuegbar; Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should not include any battery rows when not available)
* [x] Batterie-Kennzahlen (inkl. Netto-Speicherfluss) sind als **„berechnet/geschätzt“** gekennzeichnet und werden nur bei vorhandenen Producer- **und** Bilanz-Daten (Bezug + Rücklieferung) gezeigt — **sofern keine `SPEICHER`-Einheit existiert**. (Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should mark the battery figures as calculated when they come from the balance; Test: StatistikServiceTest#berechneKennzahlen_KeineBilanzDaten_BatterieNichtVerfuegbar; Test: StatistikServiceTest#berechneKennzahlen_NurBezugOhneRuecklieferung_BatterieNichtVerfuegbar)
* [x] **Mit `SPEICHER`-Einheit** stammen Ladung, Entladung, Netto und Wirkungsgrad aus deren Messwerten; Beispiel `geladen 100, entladen 92` → Netto `+8`, Wirkungsgrad `92.0 %`. (Test: StatistikServiceTest#batterie_MitSpeicherEinheit_NimmtGemesseneWerte)
* [x] Bei gemessenen Werten entfällt die Kennzeichnung **berechnet** — in der Web-Ansicht **und** im PDF. (Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should not mark the battery figures when they are measured at the storage unit; Test: StatistikPdfServiceTest#fuelle_BatterieGemessen_OhneBerechnetHinweis; Test: StatistikPdfServiceTest#fuelle_BatterieAusBilanz_MitBerechnetHinweis)
* [x] Eine `SPEICHER`-Einheit genügt: Die Kennzahlen erscheinen auch **ohne** Bilanz-Bezug und Rücklieferung. (Test: StatistikServiceTest#batterie_MitSpeicherEinheitOhneBilanzdaten_TrotzdemVerfuegbar)
* [x] Eine `SPEICHER`-Einheit **ohne Messwerte** ergibt `0`/`0` und einen leeren Wirkungsgrad statt einer Division durch null. (Test: StatistikServiceTest#batterie_SpeicherEinheitOhneMesswerte_NullenUndKeinWirkungsgrad)
* [x] Ohne `SPEICHER`-Einheit bleibt das Verhalten **unverändert** (Residuum der Bilanz, Kennzeichnung berechnet). (Test: StatistikServiceTest#batterie_OhneSpeicherEinheit_BleibtBerechnet; Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should mark the battery figures as calculated when they come from the balance)
* [x] Im **Producer-Messung**-Modus bleibt der bestehende Summen-Vergleich erhalten; im **Bilanzmodus** wird er ausgeblendet und durch das Kennzahlen-Panel ersetzt. (Test: statistik.component.spec.ts › StatistikComponent › Summen-Vergleich › should hide the sum comparison in the balance mode; Test: statistik.component.spec.ts › StatistikComponent › Summen-Vergleich › should show the sum comparison in the producer measurement mode; Test: StatistikPdfServiceTest#fuelle_Bilanzmodus_OhneSummenVergleich; Test: StatistikPdfServiceTest#fuelle_ProducerModus_MitSummenVergleich)
* [x] Die Kennzahlen erscheinen auch im **Statistik-PDF** (`statistik.jrxml`) je Monat mit derselben Modus-Logik wie am Bildschirm. (Test: StatistikPdfServiceTest#fuelle_ZeigtDieKennzahlen; Test: StatistikPdfServiceTest#fuelle_Bilanzmodus_OhneSummenVergleich; Test: StatistikPdfServiceTest#fuelle_ProducerModus_MitSummenVergleich; Test: StatistikPdfServiceTest#fuelle_BatterieGemessen_OhneBerechnetHinweis)
* [x] Alle Texte via `TranslationService` (DE/EN); Prozentwerte mit `%`, kWh-Werte in kWh. (Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should format quota rows as percent with 1 decimal and "%" unit; Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should format Batterie-Geladen/-Entladen as kWh with 3 decimals; Nachweis: V90__Add_Statistik_Kennzahlen_Translations.sql, V91__Add_Statistik_Kennzahlen_Hinweise_Translations.sql)
* [x] Alle Kennzahlen (Prozent- und kWh-Werte) verwenden den **Punkt** als Dezimaltrennzeichen und – bei Werten ≥ 1000 – das **Hochkomma** (`'`) als Tausendertrennzeichen, am Bildschirm und im PDF, unabhängig von der Server-/Browser-Locale (`60.0 %`; `1'234.567 kWh`, nicht `60,0 %` / `1,234.567 kWh`). (Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should group thousands in a large ZEV-Eigenverbrauch value; Test: statistik.component.spec.ts › StatistikComponent › formatNumber › should group thousands with apostrophe; Test: PdfNumberFormatTest#kwh_ValueAtLeast1000_UsesApostropheAsThousandsSeparator; Test: PdfNumberFormatTest#percent_Fraction_OneDecimalWithPercentSign)
* [x] Die Kennzahlen werden **tabellarisch** dargestellt (`.zev-table`/`.zev-table--compact`/`.zev-table--auto`, **ohne Titelzeile**): drei Spalten Bezeichnung (links) | Wert (rechtsbündig, `.zev-table__number`) | Einheit (links). Die Tabelle nutzt Inhaltsbreite (`.zev-table--auto`), die Bezeichnungs-Spalte ist nicht überbreit. Keine komponenten-eigenen Ad-hoc-Styles; ausschliesslich Design-System-Klassen. (E2E: statistik.spec.ts › Statistik Page - Monthly Statistics › should display monthly panels with data tables when statistics are loaded; Nachweis: statistik.component.html:298-318)
* [x] Jede Kennzahl zeigt beim Hovern einen erklärenden Hinweis (`title`-Tooltip), der die Bedeutung der Kennzahl beschreibt; die Hinweistexte sind via `TranslationService` (DE/EN) übersetzt. (E2E: statistik.spec.ts › Statistik Page - Monthly Statistics › should render a non-empty title tooltip on every Kennzahl row; Test: statistik.component.spec.ts › StatistikComponent › getKennzahlen › should derive hintKey by appending _HINWEIS to labelKey)
* [x] Statistik bleibt mit `statistik:read` erreichbar; Multi-Tenancy unverändert. (Test: ControllerAuthorizationTest#getStatistik_withStatistikRead_ok; Test: ControllerAuthorizationTest#getStatistik_withoutPermission_forbidden; Test: ControllerAuthorizationTest#getStatistik_unauthenticated_unauthorized; Test: ControllerAuthorizationTest#getLetztesMessdatum_withStatistikRead_ok; Test: ControllerAuthorizationTest#getLetztesMessdatum_withoutPermission_forbidden; Test: ControllerAuthorizationTest#exportStatistikCsv_withStatistikRead_ok; Test: ControllerAuthorizationTest#exportStatistikCsv_withoutPermission_forbidden; Test: ControllerAuthorizationTest#exportStatistikPdf_withStatistikRead_ok; Test: ControllerAuthorizationTest#exportStatistikPdf_withoutPermission_forbidden; Test: StatistikServiceTest#getStatistik_AktiviertMandantenfilterVorDemLesen; Test: StatistikServiceTest#ermittleLetztesMessdatum_AktiviertMandantenfilterVorDemLesen; Test: StatistikServiceTest#exportMesswerteCsv_ForeignOrg_ThrowsIllegalArgumentException_NoExport)

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Kennzahlen Stufe 1 (FR-12) sind reine Arithmetik auf bereits vorhandenen Summen → vernachlässigbar.
* Stufe 2 (FR-13, Batterie geladen/entladen) erfordert eine Aggregation je 15-Minuten-Intervall (analog zur Solarverteilung) **oder** eine dedizierte Aggregat-Query. Die Komplexität bleibt O(Zeitpunkte × Einheiten); der `statistik`-Cache greift weiterhin. Bevorzugt eine SQL-seitige Aggregation (Netto je `zeit`, dann bedingte Summe), um einen zweiten vollen Intervall-Loop zu vermeiden.
* Das Gesamt-Panel kostet eine zusätzliche Berechnung je Abruf (FR-11).

### NFR-2: Sicherheit
* Die Seite verlangt die Permission `statistik:read`; die Kennzahlen bringen keine neuen Permissions. Multi-Tenancy (`org_id`/`orgFilter`) unverändert; keine mandantenübergreifenden Daten (Hintergrund: Anhang A.4).

### NFR-3: Kompatibilität
* Die Kennzahlen sind rein additiv: zusätzliche berechnete DTO-Felder + UI-Panel + Übersetzungen. Kein Schema-Change, keine Änderung an Rechnung/Verteilung. Bestehende Statistik (inkl. Summen-Vergleich im Producer-Modus und PDF-Export) bleibt funktionsfähig.

### NFR-4: Logging
* Sinnvolles Logging.
* Sinnvolle und hilfreiche Tests werden erst auf Anweisung erstellt.

## 5. Edge Cases & Fehlerbehandlung
| Szenario | Verhalten |
|----------|-----------|
| Keine Producer-Einheit (P = 0) | Eigenverbrauchsquote/Einspeisequote → „–“; Batterie-Block entfällt (ohne `SPEICHER`-Einheit, sonst FR-13a); Autarkiegrad/Netzbezugsquote/ZEV-Eigenverbrauch bleiben (basieren auf Consumer-Summen) |
| Keine Bilanz-Einheiten (Bezug/Rücklieferung) | Nur der Batterie-Block (Netto-Speicherfluss, geladen/entladen/Wirkungsgrad) entfällt (ohne `SPEICHER`-Einheit, sonst FR-13a); Quoten-KPIs bleiben verfügbar (modus-agnostisch aus ZEV-Summen); die Bilanz-Zeilen der Werte-Tabelle und ihre Vergleiche entfallen (FR-8.5) |
| Nur Bezug, keine Rücklieferung | Quoten-KPIs ok; Batterie-Block entfällt (Rücklieferung fehlt; ohne `SPEICHER`-Einheit, sonst FR-13a) |
| Verbrauch = 0 bzw. Produktion = 0 | betroffene Quote „–“ (keine Division durch 0) |
| Batterie geladen = 0 | Round-Trip-Wirkungsgrad → „–“ (keine Division durch 0) |
| Negativer Netto-Speicherfluss | als Netto-Entladung ausweisen (Vorzeichen/Label) |
| Residuum durch Messfehler leicht negativ/positiv obwohl keine Batterie | Batterie-Werte als „berechnet“ gekennzeichnet; kleine Werte sind erwartbar (Verluste/Messrauschen) |
| Batterie lädt im Bilanzmodus | Eigenverbrauchsquote (`Pz/P` mit `Pz = max(0, P − R)`) zählt die Batterieladung **als Eigenverbrauch** mit (bewusst, s. FR-12.2); im Modus `PRODUCER_MESSUNG` ist das nicht der Fall (kein Batteriemodell) |
| Leere Liste / kein Monat im Zeitraum | kein Panel, kein Fehler |
| Netzwerkfehler beim Laden der Statistik | Fehlermeldung (FR-6.3); die bestehende Fehlerbehandlung greift |
| Jahreswechsel beim Öffnen | im Q1 ist Q4 des Vorjahres vorselektiert (FR-1.2) |
| „Heute“ kurz nach Mitternacht | lokales Tagesdatum, nicht UTC (FR-3.1) |

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** Bilanzmodell (`Specs/Bilanzmodell.md`), Bilanzmesspunkt (`Specs/Bilanzmesspunkt.md`, Typen `BEZUG`/`RUECKLIEFERUNG`), Batteriespeicher (`Specs/Batteriespeicher.md`, Typ `SPEICHER`, FR-13a), Quartal-Selektor (`Specs/Quartale.md`); allgemeine Vorgaben `Specs/generell.md`.
* **Betroffener Code (Backend):**
  - `dto/MonatsStatistikDTO.java` — berechnete Kennzahl-Felder (Autarkiegrad, Eigenverbrauchsquote, Netzbezugsquote, Einspeisequote, ZEV-Eigenverbrauch, Batterie netto/geladen/entladen/Wirkungsgrad).
  - `service/StatistikService.java` — Monats- und Gesamtberechnung (FR-8, FR-11), Kennzahlen aus den Summen (Stufe 1) bzw. Pro-Intervall-Aggregation für geladen/entladen (Stufe 2).
  - `repository/MesswerteRepository.java` — Aggregat-Query bzw. Intervall-Loop (`findDistinctZeitBetween` + `findByZeitAndEinheitTyp`) für den Netto je 15-Min-Intervall (Stufe 2); die bestehenden `sum…ByEinheitTypAndZeitBetween`-Queries liefern nur Zeitraum-Summen und reichen für geladen/entladen **nicht** aus; `sumLadungEntladungByEinheitTypAndZeitBetween` (FR-13a).
* **Betroffener Code (Frontend):** `statistik.component.*` (+ `statistik.model.ts`) — Kennzahlen-Panel als tabellarische Darstellung mit Design-System-Bausteinen (`.zev-table`/`.zev-table--compact`/`.zev-table--auto`, Wert `.zev-table__number`), „berechnet“-Kennzeichnung via `.zev-tag`, `title`-Tooltip je Zeile; View-Model `getKennzahlen()`; `utils/date-utils.ts` (`blaettereZeitraum`, FR-4); `components/loading-indicator/` (FR-5).
* **Design-System:** `table.css` (`.zev-table--auto`, `.zev-table__number`), `spinner.css` (`.zev-loading`), Quartal-Selektor (`.zev-quarter-selector__actions`), Collapsible (`.zev-collapsible`); jeweils mit Showcase-Eintrag.
* **PDF-Export (Backend):** `service/StatistikPdfService.java` + `reports/statistik.jrxml` (Subreport `einheit-summen.jrxml` für „Summen pro Einheit“) — Kennzahlen und Gesamt-Panel je Block (Quoten-KPIs in beiden Modi, Batterie-KPIs als „berechnet“; Summen-Vergleich im Bilanzmodus ausgeblendet).
* **PDF-Zahlenformat (Backend):** `util/PdfNumberFormat.java` — zentrale, locale-unabhängige Formatierung (Punkt-Dezimal, Hochkomma-Gruppierung) für **das gesamte** Statistik-PDF (`statistik.jrxml` **und** Subreport `einheit-summen.jrxml`), statt `pattern="#,##0.000"`/`String.format(...)` je Feld (Hintergrund: Anhang A.5).
* **i18n:** Keys via Flyway – `V90` (Kennzahlen-Labels), `V91` (erklärende Hinweise/Tooltips), `V133` (gemessene Gegenstücke), `V134` (Hinweise zur Richtung der Lücken-Verzerrung), `V171`, `V177`, `V181`.
* **Datenmigration:** keine.

## 7. Abgrenzung / Out of Scope
* **Kein** direkter Batteriezähler / keine Speicher-Modellierung als eigene Einheit im Rahmen der rechnerischen Kennzahlen (FR-13); die gemessene Variante kommt aus `Specs/Batteriespeicher.md` (FR-13a).
* **Keine** Kosten-/Tarif-Kennzahlen (eingesparte Netzkosten o.ä.) in dieser Stufe.
* **Keine** Änderung an Verteilung/Verrechnung.
* **Keine** neue Persistenz/Historisierung der Kennzahlen (werden bei Bedarf berechnet).
* Tooltips gibt es nur im Web, nicht im PDF (FR-9).

## 8. Offene Fragen
* Keine. Die Fragen der Kennzahlen sind beantwortet (Anhang A.9).

## Anhang A – Entscheidungen und Verlauf

### A.1 Zusammenführung (19.10.2026)
Die Spec fasst zwei Dokumente zusammen. `Statistik.md` (Statistikseite, seit 08.12.2025: angelegt mit Commit `936f42c`, umgeschrieben mit `b4602b2` „Statistikanforderungen“) hatte keine FR-Nummern und nicht die Struktur von `Specs/SPEC.md`; ihre Anforderungen stehen jetzt als FR-1 bis FR-11. `Statistik-Kennzahlen.md` (seit 02.08.2026, Commit `9303da9`) ist als FR-12 bis FR-16 aufgenommen; an ihrer Stelle steht ein Verweis, weil ausgeführte Migrationen (`V90`, `V91`, `V133`) und Code-Kommentare die Datei beim Namen nennen. Die Umsetzungspläne `Statistik-Kennzahlen_Umsetzungsplan.md` und `Statistik-PDF_Summen-pro-Einheit_Umsetzungsplan.md` sind in `Statistik_Umsetzungsplan.md` aufgegangen.

Zuordnung der alten Nummern aus `Statistik-Kennzahlen.md` (Code-Kommentare nennen „FR-2a“, „FR-2/2a“, „Stufe 1“, „Stufe 2“):

| `Statistik-Kennzahlen.md` | `Statistik.md` |
|---|---|
| FR-1 (Stufe 1) | FR-12 |
| FR-2 (Stufe 2) | FR-13 |
| FR-2a | FR-13a |
| FR-3 | FR-14 |
| FR-4 | FR-15 |
| FR-5 | FR-16 |
| NFR-1 bis NFR-3, Abschnitte 5–7 | NFR-1 bis NFR-3, Abschnitte 5–7 (zusammengelegt) |
| Abschnitt 8 (Offene Fragen) | Anhang A.9 |

Benannte Teile der früheren `Statistik.md`, auf die Code, Tests und Migrationen verweisen: „Heute“ → FR-3, „Blättern“ → FR-4, „Ladeanzeige“ → FR-5, „Monats-Panels aufklappbar“ → FR-10, „Gesamt-Panel“ → FR-11, „geändert am 14.10.2026“ → Anhang A.2.

Abschnitt 1 der Kennzahlen trug einen **Aktuellen Stand** zur Zeit der Spec: „Die Statistik (`StatistikService` → `StatistikDTO`/`MonatsStatistikDTO`) liefert je Monat bereits: `summeProducerTotal` (Produktion, Betrag), `summeConsumerTotal` (Verbrauch), `summeProducerZev`, `summeConsumerZev`, `summeConsumerZevCalculated`, sowie die gemessenen Bilanz-Summen `bilanzBezug`/`bilanzRuecklieferung` (Betrag). Der Verteilmodus (`PRODUCER_MESSUNG` / `BILANZ`) ist top-level in `StatistikDTO.verteilmodus` vorhanden (`StatistikService` setzt ihn via `EinstellungenService.getVerteilmodus`) und wird angezeigt; im Bilanzmodus trägt der Summen-Vergleich einen Tautologie-Hinweis. 15-Minuten-Rohwerte je Einheit sind über `messwerteRepository` verfügbar (`findByZeitAndEinheitTyp`, `findDistinctZeitBetween`).“

Die frühere `Statistik.md` begann mit „Es soll eine Statistikseite realisiert werden.“ und schloss mit den Abschnitten „Technische Spezifikationen“ (Design System verwenden, neue Designs aufnehmen, an bisherige Seiten anlehnen – jetzt Layout & Navigation), „Nicht-funktionale Anforderungen“ (siehe A.4; „Sinnvolles Logging“ – jetzt NFR-4; „Erstelle sinnvolle und hilfreiche Tests erst auf Anweisung“ – jetzt NFR-4) und „Verschiedenes“ („Beachte die Anweisungen in den Dateien Specs/generell.md und …“ – jetzt Abschnitt 6; der zweite Verweis zeigte auf eine nicht mehr vorhandene Datei und ist am 19.10.2026 entfernt).

### A.2 Bedienung am 14.10.2026 umgestellt (FR-2, FR-3, FR-4, FR-6)
* **„Anzeigen“** stand bis 14.10.2026 am Ende der Zeile mit den Datumsfeldern; seither neben den Quartalsknöpfen (Commit `d4d467f`). Das Verhalten blieb unverändert. Das Kriterium hiess damals „Klick auf ‚Anzeigen‘ lädt die Statistik des eingestellten Zeitraums wie bisher.“
* **„Heute“** setzte bis 14.10.2026 nur den Zeitraum, geladen wurde mit „Anzeigen“; zudem war die Schaltfläche gesperrt, solange heute–heute schon eingestellt war (Commit `f78d771`). Eingeführt am 07.10.2026 (Commit `1ee00ac`). Das Kriterium zur Position lautete: „Die Schaltfläche ‚Heute‘ steht nach ◀ / ▶ (seit 14.10.2026 steht ‚Anzeigen‘ neben den Quartalen).“
* **Blättern** (13.10.2026, Commit `a3e4dff`) lädt seit 14.10.2026 sofort, wie „Heute“.
* **Erfolgsmeldung** „n Monate geladen“ nach dem Laden entfiel am 14.10.2026; eine stehende Meldung überschrieb zuvor die Erfolgsmeldung, statt beim Start des Ladens weggeräumt zu werden.

### A.3 Berechnete Werte und Darstellung (FR-8)
* **Bezug von VNB / Rücklieferung** (11.07.2026, Commits `ce22e9d`, `d5c5770`) waren zuerst Zeilen der Werte-Tabelle; revidiert (17.07.2026, Commit `53de18f`): Sie stehen nicht mehr als Zeilen, sondern dienen nur noch dem Summen-Vergleich gegen die Bilanz-Einheiten. Bezug von VNB wurde vorher mit `zev_berechnet` gerechnet, seither „Messung gegen Messung“ mit dem gemessenen zev der Consumer. An ihrer Stelle stehen „stattdessen“ die gemessenen Bilanz-Summen, wenn Bilanz-Einheiten existieren.
* **Grafische Darstellung:** Die ursprüngliche Anforderung lautete „Vielleicht hast du eine gute Idee, das graphisch darzustellen“; umgesetzt als Balken in der Summen-Tabelle (Spalten „Beschreibung / Visualisierung / Wert“).
* **Rechtsbündige kWh-Werte** (22.07.2026, Commit `4554778`), **erklärende Tooltips** (13.08.2026, Commit `9b67309`, damals „Nachtrag“) und **aufklappbare Monate** (30.08.2026, Commit `c6605ff`, „Nachtrag“).
* **Summen pro Einheit im PDF:** Plan ohne eigene Spec, jetzt Teil C von `Statistik_Umsetzungsplan.md` (Subreport `einheit-summen.jrxml`).

### A.4 Berechtigung (NFR-2)
Die frühere `Statistik.md` hielt fest: „Sicherheit: Die Seite kann mit der Rolle "zev" aufgerufen werden“. Seit der Umstellung auf Permissions (`Specs/Berechtigungen.md`) gilt `statistik:read`, wie es die Kennzahlen-Spec und ihr Kriterium „Statistik bleibt mit `statistik:read` erreichbar“ schon sagten.

### A.5 Kennzahlen Stufe 1 und 2 (FR-12, FR-13, 02.08.2026)
Spec, Umsetzungsplan und Umsetzung am 02.08.2026 (Commits `9303da9`, `6ac37cd`, `d8cfa2c`), Abgleich am 03.08.2026 (`8738cf4`). `PdfNumberFormat` ersetzte damals die bisherigen `pattern="#,##0.000"`/`String.format(...)`-Stellen; „Die Überschrift rief zuvor `Month.of(monat)` auf“ stand in FR-11 (Gesamt-Panel, A.8).

### A.6 Gemessene Gegenstücke und Lücken (FR-12.6, 30./31.08.2026)
Autarkiegrad und Netzbezugsquote (gemessen) kamen mit `V133` dazu (Commit `c6605ff`). Der Kommentar in `V133` nennt „FR-1.7“; gemeint ist die damalige FR-1.6, heute FR-12.6 (die Migration ist ausgeführt und bleibt unverändert). Die Hinweise zu den Lücken kamen mit `V134`. Am 31.08.2026 korrigiert (Commit `8196599` „Bilanz-Lücken verzerren beide Autarkiegrade, nicht nur den gemessenen“): Seither tragen im Bilanzmodus auch die gerechneten Zeilen die Kennzeichnung „lückenhaft“, mit eigenem Hinweis.

### A.7 Batterie-Kennzahlen aus `SPEICHER` (FR-13a, 16.09.2026)
Commit `8441e9a`; PDF am 16.09.2026 sichtgeprüft (Commit `a3bfac8` „PDF sichtgeprüft, FR-2a vollständig erfüllt“).

### A.8 Gesamt-Panel, „Heute“ und Ladeanzeige (FR-11, FR-3, FR-5, 07./11.10.2026)
Commit `1ee00ac`; Ladeanzeige am 11.10.2026 (Commit `04a725c`).

### A.9 Beantwortete Fragen der Kennzahlen (FR-12 bis FR-15)
Abschnitt 8 der Kennzahlen-Spec („Alle geklärt.“):
* [x] **Stufen-Umfang:** **Entschieden:** Stufe 1 (KPIs aus Summen) zuerst, Stufe 2 (Batterie geladen/entladen via Pro-Intervall-Aggregation) als Folgeschritt.
* [x] **Summen-Vergleich im Bilanzmodus:** **Entschieden:** im Bilanzmodus **ausblenden**; das Kennzahlen-Panel tritt an seine Stelle (siehe FR-14.2). Im Producer-Messung-Modus bleibt er erhalten.
* [x] **Eigenverbrauchsquote-Definition:** **Entschieden:** `summeProducerZev / summeProducerTotal` (intern genutzte Produktion / Produktion), weil so **ohne zusätzliche Bilanz-Daten** in beiden Verteilmodi verfügbar. **Achtung modus-abhängige Bedeutung** (s. FR-12.2): Im Modus `BILANZ` wird `summeProducerZev` intern als `max(0, |Produktion| − |Rücklieferung|)` = `(P − R)` gebildet, d.h. eine **Batterieladung zählt hier als Eigenverbrauch mit** (Textbuch-Eigenverbrauchsquote – bewusst akzeptiert). Im Modus `PRODUCER_MESSUNG` ist `summeProducerZev` die direkt verteilte Produktion **ohne** Batterie. Analog Autarkiegrad über `summeConsumerZev / summeConsumerTotal`.
* [x] **PDF-Export:** **Entschieden:** ja – die Kennzahlen erscheinen auch im Statistik-PDF (siehe FR-15).
* [x] **Anzeige in beiden Modi:** **Entschieden:** in beiden Modi anzeigen (Quoten-KPIs sind modus-agnostisch; Batterie-KPIs nur bei vorhandenen Bilanz-Daten). *Ergänzt am 19.10.2026: gilt ohne `SPEICHER`-Einheit, sonst FR-13a.*
* [x] **Rundung/Format:** **Entschieden:** Prozentwerte mit 1 Nachkommastelle. **Dezimaltrennzeichen = Punkt** (`.`), **Tausendertrennzeichen = Hochkomma** (`'`, Schweizer Konvention) – am Bildschirm (`toFixed` + Gruppierung) und im PDF (`String.format(Locale.ROOT, …)` mit `,`→`'`), locale-unabhängig.

In der Abgrenzung stand der Tippfehler „Kostsen/Tarif-Kennzahlen“ (jetzt „Kosten-/Tarif-Kennzahlen“) und „Kein direkter Batteriezähler / keine Speicher-Modellierung als eigene Einheit (die explizite Variante bleibt `Specs/Batteriespeicher.md`); hier nur die **rechnerische** Ableitung aus der Bilanz.“ – seit FR-13a kommt die gemessene Variante aus der Speicher-Einheit.

### A.10 Batterie-Bedingungen präzisiert (FR-12, Abschnitte 3 und 5, 19.10.2026)
Bei der Zusammenführung fiel auf: Mehrere Stellen verlangten für die Batterie-Kennzahlen Producer **und** Bilanz-Bezug **und** Rücklieferung, obwohl seit FR-13a eine `SPEICHER`-Einheit genügt – das Kriterium „Batterie-Kennzahlen … sofern keine `SPEICHER`-Einheit existiert“ sagte es schon. Auf Wunsch des Users ergänzt um „(ohne `SPEICHER`-Einheit, sonst FR-13a)“: FR-12 (Schluss, zwei Punkte), das Kriterium „Fehlt Bilanz-Bezug oder Rücklieferung …“, die Edge Cases „Keine Producer-Einheit“, „Keine Bilanz-Einheiten“, „Nur Bezug, keine Rücklieferung“ und die Antwort „Anzeige in beiden Modi“ (A.9). Der Checkbox-Status des Kriteriums bleibt.

### A.11 Kriterium „gedimmt stehen bleiben“ gestrichen (FR-5, 19.10.2026)
Das Kriterium lautete: „Liegt schon eine Statistik vor und wird neu berechnet, bleibt sie stehen und wird gedimmt (`.zev-busy`) — kein Layout-Sprung.“ Beim Akzeptanzkriterien-Check vom 19.10.2026 als ❌ gefunden: `onSubmit()` setzt die Statistik vor dem Laden auf `null`, sie verschwindet während der Berechnung (der Unit-Test „should set statistik to null before loading“ prüft genau das). Entscheid des Users: Kriterium streichen, das Verhalten bleibt. Das Binding `[class.zev-busy]="loading"` im Template wirkt damit nicht.

### A.12 Drei Kriterien umformuliert (FR-9, FR-12, Abschnitt 5, 19.10.2026)
Beim Akzeptanzkriterien-Check vom 19.10.2026 vorgeschlagen, vom User übernommen:
* FR-9: „Werte, Balkenlängen, Sortierung und PDF-Ausgabe sind unverändert.“ – gestrichen: eine Additivitäts-Zusage, die FR-9 und Abschnitt 7 schon festhalten; ein Test schützte vor nichts.
* Kennzahlen: „Bei `C = 0` entfallen die gemessenen Zeilen ebenso wie Autarkiegrad und Netzbezugsquote.“ – falsch formuliert: Nach FR-12 und Code bleiben Autarkiegrad und Netzbezugsquote als „–“ stehen.
* Kennzahlen: „Fehlt Bilanz-Bezug oder Rücklieferung … → nur die Batterie-KPIs … werden als **„–“** angezeigt“ – nach FR-13 und Code entfällt der Batterie-Block. Ebenso angepasst: FR-12 (letzter Punkt: „bzw. für die Batterie-KPIs fehlende Bilanz-Daten … als „–“/n/a“) und die Edge Cases „Keine Producer-Einheit“ („Batterie → „–““), „Keine Bilanz-Einheiten“ („Nur Batterie-KPIs … → „–““), „Nur Bezug, keine Rücklieferung“ („Batterie-KPIs → „–““).

### A.13 Lücken-Hinweis im PDF an den Bildschirm angeglichen (FR-12.6, FR-15, 19.10.2026)
Beim Akzeptanzkriterien-Check vom 19.10.2026 als ❌ gefunden: Das PDF druckte unter den gemessenen Zeilen **einen** Hinweis, im Bilanzmodus mit Verteilungs-Lücken aber den Text der **gerechneten** Zeilen („zu ungünstig … zwischen dieser Zeile und ihrem gemessenen Gegenstück“); die gerechneten Zeilen trugen keine Kennzeichnung. Das Feld war zudem 290 × 20 pt gross, die Hinweise sind 200–290 Zeichen lang. Entscheid des Users: PDF angleichen. Seither tragen im PDF wie am Bildschirm die gemessenen Zeilen bei Lücken im `BEZUG` „(lückenhaft)“ mit dem Messungs-Hinweis, im Bilanzmodus zusätzlich Autarkiegrad und Netzbezugsquote „(lückenhaft)“ mit dem Verteilungs-Hinweis; die Hinweise stehen über die volle Breite und wachsen mit dem Text (`StatistikPdfServiceTest#fuelle_GemesseneKennzahlenMitLuecken_ZeigtZeilenUndHinweis`, `#fuelle_GemesseneKennzahlenMitVerteilungsluecken_ZeigtVerteilungsHinweis`; gegen das alte Template rot).

