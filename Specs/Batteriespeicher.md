# Batteriespeicher

> **Zum Zuschnitt vor der Umsetzung lesen.** Diese Spec beschreibt zwei sehr verschiedene Teile:
>
> * **Sichtbarkeit** — Einheiten-Typ (FR-1), Messung und Aggregation (FR-2), Statistik-Zeilen
>   (FR-4.2), keine Verrechnung (FR-5), Übersetzungen (FR-6). Gilt in **beiden** Verteilmodi und
>   ist das, was gebraucht wird, damit die Batterie in den Daten überhaupt vorkommt.
> * **Umbau der Solarverteilung** — FR-3 und die angepassten Vergleichswerte in FR-4.3. Sie
>   betreffen **ausschliesslich** den Verteilmodus `PRODUCER_MESSUNG`.
>
> **Der zweite Teil nützt heute keinem Mandanten mit Batterie:** Die Batterie steht bei Hene, und
> dort gilt `BILANZ` (FR-3a). Längerfristig soll ohnehin **nur noch der Bilanzmodus** unterstützt
> werden (Absicht, Stand 13.09.2026; beide Mandanten haben bereits Bezugs- und
> Rücklieferungs-Einheiten, der Umstieg wäre ein Konfigurationswert). FR-3 und FR-4.3 wären damit
> Arbeit an einem Pfad, der verschwindet.
>
> **Empfehlung:** Zuerst — und womöglich ausschliesslich — den Sichtbarkeitsteil umsetzen. Er ist
> auch die Grundlage dafür, die Wirkung der Steuerung aus `Specs/Einspeisesteuerung.md` zu
> beurteilen.

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Ein Batteriespeicher wird als neuer Einheiten-Typ **`SPEICHER`** in den ZEV eingebunden. Sein Zähler liefert Ladung und Entladung über den bestehenden MQTT-Pfad (zwei Register). Die Solarverteilung berücksichtigt den Speicher **nachrangig**: Consumer werden zuerst bedient, nur der verbleibende PV-Überschuss gilt als ZEV-Ladung; entladener Strom wird wie Produktion auf die Consumer verteilt. Statistik und Bilanz-Vergleiche werden um Ladung/Entladung erweitert. Die Speicher-Einheit selbst wird **nicht verrechnet**.
* **Warum machen wir das:** Ein Speicher erhöht die Eigenverbrauchsquote des ZEV (PV-Überschuss wird gespeichert statt rückgeliefert und später im ZEV konsumiert). Damit Abrechnung und Plausibilisierung (Bilanz-Vergleiche) korrekt bleiben, muss der Speicher energetisch sauber modelliert sein.
* **Aktueller Stand:**
  - `EinheitTyp` kennt `PRODUCER`, `CONSUMER`, `BEZUG`, `RUECKLIEFERUNG` und **`LADESTATION`** (`Specs/Ladestationen.md`, seit 17.08.2026; Ladestationen nehmen weder an Verteilung noch an Aggregation oder Statistik teil). Die Solarverteilung verarbeitet ausschliesslich `PRODUCER`/`CONSUMER`.
  - **Es gibt zwei Verteilmodi** (`Specs/Bilanzmodell.md`, seit 18.07.2026), umgeschaltet über `organisation.konfiguration.verteilmodus`:
    - `PRODUCER_MESSUNG` — die verteilbare Energie kommt aus den gemessenen Producern (`MesswerteService.distributeProducerMessung`).
    - `BILANZ` — sie wird aus der Netz-Bilanz abgeleitet: `S = max(0, ConsumerTotal − Bezug)` (`MesswerteService.distributeBilanz`).
    **Hene, der Mandant mit dem Speicher, fährt `BILANZ`.**
  - MQTT-Ingest/Aggregation: `total = ΔBezug − ΔEinspeisung` (vorzeichenbehaftet), Reset-Guard je Register, `quelle = MQTT`; der Wire-Contract transportiert bereits beide Register (`zaehlerstandBezug`/`zaehlerstandEinspeisung`).
  - Statistik: Summen A–E, berechnete Vergleichswerte `Bezug von VNB = ConsumerTotal − zev(Consumer)` und `Rücklieferung = ProducerTotal − zev(Producer)` gegen die Bilanz-Einheiten (`Specs/Bilanzmesspunkt.md`).
  - zev(Producer) = im ZEV konsumierte Produktion (= im Intervall verteilte Menge).

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Neuer Einheiten-Typ SPEICHER
1. `EinheitTyp` wird um **`SPEICHER`** erweitert (Anzeige „Speicher" / „Storage", Key `TYP_SPEICHER`). Keine Schema-Migration (`einheit.typ` ist `VARCHAR`).
2. Speicher-Einheiten werden wie andere Einheiten über das Einheiten-Formular angelegt (Typ-Dropdown um „Speicher" ergänzt) und besitzen einen `messpunkt`.
3. **Eindeutigkeit:** Pro Mandant (`org_id`) ist **höchstens eine** Einheit vom Typ `SPEICHER` erlaubt (Validierung im `EinheitService`, analog Bilanz-Typen; eigener Fehler-Key `EINHEIT_SPEICHER_EXISTIERT`, HTTP 400).
4. Die Typ-Anzeige (`EinheitTypPipe`, „Summen pro Einheit" Web **und** PDF-Subreport `einheit-summen.jrxml`) beschriftet alle **sechs** Typen korrekt — `PRODUCER`, `CONSUMER`, `BEZUG`, `RUECKLIEFERUNG`, `LADESTATION` und neu `SPEICHER`.
   > **Achtung, stiller Fehler:** `EinheitTypPipe` endet auf einem `default`-Zweig, der
   > `PRODUZENT` liefert. Ohne eigenen `case` erscheint ein Speicher also **als „Produzent"** —
   > kein Compiler-Fehler, keine Ausnahme, nur eine falsche Beschriftung neben richtigen Zahlen.
   > Der Code warnt an Ort und Stelle davor. Ein Test, der bloss prüft, dass *irgendein* Text
   > erscheint, fängt das nicht; er muss den erwarteten Text nennen.

### FR-2: Messung & Aggregation
1. Der Speicher-Zähler publiziert wie alle Zähler beide Register: `zaehlerstandBezug` = **Ladung** (kumulativ), `zaehlerstandEinspeisung` = **Entladung** (kumulativ). Ingest und Aggregation laufen unverändert: `total = ΔLadung − ΔEntladung` je 15-Minuten-Intervall (**positiv = Ladung, negativ = Entladung**; gleichzeitiges Laden/Entladen wird saldiert).
2. Beide Register gehören zur **einen** Speicher-Einheit — im Unterschied zu Bezug und Rücklieferung, die als **zwei getrennte Einheiten** mit eigenem `messpunkt` geführt werden.
   > Hier ist **nichts zu tun**: Die Aggregation rechnet `total = ΔBezug − ΔEinspeisung` für *jede*
   > Einheit gleich, ohne Fallunterscheidung nach Typ (`ZaehlerAggregationService`). Eine
   > typabhängige „Register-Projektion", die für `SPEICHER` auszunehmen wäre, gibt es im Code
   > nicht.
3. `zev` wird bei der Aggregation vorläufig auf `0` gesetzt; die anschliessende Solarverteilung setzt den ZEV-relevanten Anteil (FR-3.4). `zev_calculated` bleibt (wie bei Producern) ungesetzt.
4. Der Publisher-Simulator (`pi-gateway`) erhält einen Modus `"speicher"` (Name enthält „speicher"): wechselnde Phasen von Ladung und Entladung, damit der Pfad end-to-end testbar ist.

### FR-3: Nachrangige Einbindung in die Solarverteilung

> **Gilt ausschliesslich für den Verteilmodus `PRODUCER_MESSUNG`.** Für `BILANZ` siehe FR-3a — dort
> ändert sich an der Verteilung **nichts**.
>
> **Vor dem Bauen den Zuschnitt prüfen** (Kasten am Dokumentanfang): Dieser Abschnitt ist der
> aufwendigste der Spec, nützt aber keinem Mandanten, der heute eine Batterie hat — und der Modus,
> auf den er zielt, soll längerfristig entfallen.

Pro Zeitintervall mit den Grössen `P` = PV-Produktion (Betrag), `E` = Entladung (Betrag, bei `total < 0`), `L` = Ladung (bei `total > 0`), `V` = Summe Consumer-Verbrauch:
1. **Verteilbare Energie:** `Q = P + E`. Die Entladung wird der verteilbaren Produktion zugeschlagen (gespeicherter PV-Strom).
2. **Consumer zuerst:** Die Verteilung an die Consumer erfolgt wie bisher (EQUAL_SHARE/PROPORTIONAL, Zuteilung je Consumer am Verbrauch gekappt) auf Basis von `Q`; verteilte Menge `D ≤ min(Q, V)`.
3. **Speicher nachrangig (Ladung):** `ZEV-Ladung = min(L, Q − D)` — nur der nach den Consumern verbleibende Überschuss gilt als im ZEV geladene Energie. Der Rest (`L − ZEV-Ladung`) ist **Netzladung** (rechnerische Kappung, keine Konfiguration nötig).
4. **`zev` der Speicher-Einheit** (nur `quelle = MQTT`, vorzeichenbehaftet wie `total`): Die ZEV-Ladung wird **ausschliesslich** hier attribuiert (nicht zusätzlich im zev(Producer), sonst Doppelzählung in der Rücklieferungs-Bilanz):
   - Ladungs-Intervall: `zev = +ZEV-Ladung`.
   - Entladungs-Intervall: `zev = −(D × E / Q)` — der quellen-proportionale Anteil der Entladung an der verteilten Menge.
5. **zev(Producer) unverändert:** zev(Producer) (Summe) = `D × P/Q` (der auf PV entfallende Anteil der an Consumer verteilten Menge); bei mehreren Producern proportional zur Produktion aufgeteilt. Die in den Speicher geladene PV-Energie zählt **nicht** zum zev(Producer) (sie steht im zev(Speicher), FR-3.4). CSV-Producer bleiben unangetastet (gemessene Werte). Ohne Speicher (`E = 0`, `Q = P`) ergibt sich `D` — identisch zu heute.
6. zev(Consumer)/`zev_calculated` bleiben wie bisher = zugeteilte Menge (Quelle PV oder Speicher ist für den Consumer transparent, gleicher ZEV-Tarif).

### FR-3a: Verteilmodus `BILANZ` — keine Änderung an der Verteilung

**Im Modus `BILANZ` bleibt die Verteilung unverändert** (Entscheid). Der Speicher wird dort weder
in die verteilbare Energie eingerechnet noch nachrangig bedient.

**Begründung:** `S = max(0, ConsumerTotal − Bezug)` enthält den Speicher **bereits**.
`Specs/Bilanzmodell.md` FR-1 sagt es ausdrücklich: „`S` ist der intern (aus PV **und/oder
Speicher**) gedeckte Teil des Verbrauchs." Entlädt die Batterie, sinkt der Netzbezug und `S` steigt
von selbst; lädt sie, steigt der Bezug oder sinkt die Rücklieferung. Würde man `E` nach FR-3
zusätzlich der verteilbaren Energie zuschlagen, wäre dieselbe Energie **zweimal** gezählt — die
Consumer bekämen mehr ZEV-Strom zugeteilt, als je geflossen ist, und die Abrechnung wäre falsch.

Konkret bleibt in `distributeBilanz` unberührt:
* `S` wird weiterhin allein aus `ConsumerTotal − Bezug` gebildet.
* `zev` der Consumer, `zev(Producer)` und der Umgang mit fehlenden Bilanzdaten ändern sich nicht.
* Es entsteht **keine** ZEV-Ladung und keine Netzladung als Rechengrösse (siehe FR-4.2).

**Die Statistik-Zeilen aus FR-4.2 erscheinen dennoch in beiden Modi:** Ladung und Entladung stammen
unmittelbar aus den Messwerten der Speicher-Einheit und sind vom Verteilmodus unabhängig. Erst
damit wird der Speicher überhaupt sichtbar — und genau das ist im Bilanzmodus der einzige Gewinn
dieses Features.

### FR-4: Statistik & Bilanz-Vergleiche
1. Die Summen A–E (Producer/Consumer) enthalten **keine** Speicher-Werte.
2. **Neue Werte-Zeilen** (nur falls Speicher-Einheit existiert, Beschriftung = Einheiten-Name mit Zusatz), **Ladung und Entladung getrennt** (keine Saldo-Zeile, da ein Monatssaldo ≈ 0 irreführend wäre):
   - „`<Name>` (Ladung, gemessen)" = Summe der positiven `total`-Intervalle (Brutto-Ladung, inkl. Netzladung), Key `STATISTIK_LADUNG`.
   - „`<Name>` (Entladung, gemessen)" = Betrag der Summe der negativen `total`-Intervalle, Key `STATISTIK_ENTLADUNG`.

   > **Der Zusatz „gemessen" ist kein Schmuck — es gibt dieselbe Grösse bereits berechnet.**
   > `Specs/Statistik-Kennzahlen.md` (Stufe 2) weist heute schon „Batterie geladen" und „Batterie
   > entladen" aus, geschätzt aus der Bilanz über `Netto_i = P_i − C_i + B_i − R_i`
   > (`StatistikService.berechneBatterieKennzahlen`) — **ohne** dass eine Speicher-Einheit
   > existiert. Mit ihr stehen beide Grössen nebeneinander (Entscheid, s. §8): die eine aus vier
   > Zählern am Netzanschluss, die andere aus einem am Speicher.
   >
   > **Sie werden auseinanderlaufen** — Messungenauigkeit, Zeitversatz an Intervallgrenzen,
   > Wandlungsverluste, nicht erfasste Verbraucher. Das ist **kein Fehlerfall** und wird weder
   > korrigiert noch gemeldet (dieselbe Haltung wie in `Bilanzmodell.md` zu den Bilanz-Differenzen).
   > Der Vergleich ist der Nutzen: Er zeigt, wie gut Bilanz und Speicherzähler zusammenpassen.
   >
   > Die bestehenden Kennzahlen behalten ihre Schlüssel (`KENNZAHL_BATTERIE_GELADEN`,
   > `KENNZAHL_BATTERIE_ENTLADEN`, `KENNZAHL_BATTERIE_NETTO`, `KENNZAHL_BATTERIE_WIRKUNGSGRAD`) und
   > ihre Hinweistexte, die bereits „**Berechnete** geladene Energie …" sagen. Es gibt also keine
   > Schlüssel-Kollision; zu tun ist nur der Zusatz auf der neuen, gemessenen Seite.
   - „`<Name>` (Netzladung)" = `Ladung − ZEV-Ladung` (Summe über die Ladungs-Intervalle), Key `STATISTIK_NETZLADUNG`; mit **Tooltip** (Key `TOOLTIP_NETZLADUNG`), der erläutert: Anteil der Ladung, der nicht aus PV-Überschuss stammt und daher als Netzbezug in „Bezug von VNB" einfliesst.
   > **Ladung und Entladung erscheinen in beiden Verteilmodi** — sie kommen unmittelbar aus den Messwerten der Speicher-Einheit (FR-3a).
   > **Die Netzladung dagegen nur bei `PRODUCER_MESSUNG`:** Sie ist als `Ladung − ZEV-Ladung` definiert, und eine ZEV-Ladung entsteht nur in der Verteilung nach FR-3.3. Im Modus `BILANZ` gibt es sie nicht — dort entfällt die Zeile. Eine Näherung wäre denkbar (§8), ist aber nicht Teil dieser Spec: Eine gerechnete Zahl, die anders zustande kommt als die gleichnamige im anderen Modus, wäre schlimmer als keine.
   - Alle drei mit Balken (gleiche Skala), Web + PDF.
3. **Angepasste Vergleichswerte** (Summen-Vergleich gegen die Bilanz-Einheiten) — **nur im Modus `PRODUCER_MESSUNG`**, und damit wie FR-3 an einem auslaufenden Pfad (Kasten am Dokumentanfang). Im Modus `BILANZ` ist `zev(Producer)` als `|Produktion| − |Rücklieferung|` definiert (`Bilanzmodell.md` FR-2.4) und damit eine andere Grösse; die Formeln unten würden dort nicht aufgehen. Dort bleiben die Vergleichswerte unverändert:
   - `Rücklieferung (berechnet) = |ProducerTotal| + Entladung − zev(Producer) − Σ|zev(Speicher)|` — Produktion und Entladung, die weder von Consumern konsumiert noch (als PV) in den Speicher geladen wurden. **`Σ|zev(Speicher)|` = die je Intervall absolut genommene und dann summierte zev(Speicher)-Grösse** (nicht `|Σ zev|`): über Ladungs-Intervalle ergibt das die ZEV-Ladung, über Entlade-Intervalle `Σ(D × E/Q)`. Beide Anteile mindern die Rücklieferung (PV in die Batterie geladen bzw. entladener Strom im ZEV konsumiert). Da die ZEV-Ladung nur im zev(Speicher) steht (FR-3.4) und **nicht** im zev(Producer) (FR-3.5), wird sie genau einmal abgezogen. Kontrolle Ladungs-Intervall (E=0): `P − D − ZEV-Ladung` ✓ (z.B. `P=10, V=6, L=5`: `10 − 6 − 4 = 0`); Entladungs-Intervall (L=0): `P + E − D` ✓.
   - `Bezug von VNB (berechnet) = ConsumerTotal − zev(Consumer) + Netzladung` mit `Netzladung = Ladung − ZEV-Ladung` (= `Ladung − zev(Speicher geladen)` über die Ladungs-Intervalle).
   - Toleranz unverändert 0.1 kWh; ohne Speicher-Einheit sind die Formeln identisch zu heute (`L = E = 0`).
4. **„Summen pro Einheit":** Der Speicher wird mitgelistet (`total` = Absolutwert des Saldos `|Ladung − Entladung|`; `zev` = `Σ|zev(Speicher)|`, d.h. je Intervall absolut und summiert — konsistent zur Rücklieferungs-Formel FR-4.3, **nicht** `|Σ zev|`; „ZEV berechnet" = `-`). Sortierung: Produzenten, Konsumenten, **Speicher**, Rücklieferung, Bezug.

### FR-5: Verrechnung
1. Einheiten vom Typ `SPEICHER` werden **nicht verrechnet** (weder Consumer- noch Producer-Zweig im `RechnungService`, analog Bilanz-Typen).
2. Entladener Strom wird den Consumern implizit über deren `zev` zum **normalen ZEV-Tarif** verrechnet — keine Tarif- oder Rechnungsänderung.

### FR-6: Persistierung & i18n
* Keine neue Tabelle/Spalte; `einheit.typ` speichert `SPEICHER`.
* Neue Übersetzungs-Keys via Flyway-Migration (`ON CONFLICT (key) DO NOTHING`): `TYP_SPEICHER` („Speicher"/„Storage"), `EINHEIT_SPEICHER_EXISTIERT` („Es existiert bereits eine Speicher-Einheit."/„A storage unit already exists."), `STATISTIK_LADUNG` („Ladung, gemessen"/„Charge, measured"), `STATISTIK_ENTLADUNG` („Entladung, gemessen"/„Discharge, measured"), `STATISTIK_NETZLADUNG` („Netzladung"/„Grid charge") und `TOOLTIP_NETZLADUNG` (Erläuterung, s. FR-4.2).
* Zusätzlich ein Tooltip für die gemessenen Zeilen, `TOOLTIP_SPEICHER_GEMESSEN` („Am Speicherzähler gemessen. Die Kennzahlen „Batterie geladen/entladen" schätzen dieselbe Grösse aus der Netz-Bilanz; Abweichungen sind normal."/„Measured at the storage meter. The metrics ‚Battery charged/discharged' estimate the same quantity from the grid balance; deviations are normal.").
* Multi-Tenancy unverändert: `einheit`/`messwerte` tragen `org_id` und unterliegen dem `orgFilter`.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

### Einheiten-Typ
* [ ] `EinheitTyp` enthält `SPEICHER`; im Formular ist „Speicher" wählbar, Roundtrip über die DB funktioniert.
* [ ] Eine **zweite** Speicher-Einheit im selben Mandanten wird mit `EINHEIT_SPEICHER_EXISTIERT` (HTTP 400, übersetzt angezeigt) abgewiesen.
* [ ] `EinheitTypPipe`, Einheiten-Liste, „Summen pro Einheit" (Web + PDF) beschriften alle **sechs** Typen korrekt (inkl. `LADESTATION`).

### Aggregation & Verteilung
* [ ] Speicher-Messwerte werden wie andere Typen aggregiert: Ladung → positives, Entladung → negatives `total`; `quelle = MQTT`.
* [ ] Entladung erhöht die verteilbare Energie: Consumer erhalten bei `P=0, E=4, V=6` in Summe `4` kWh zugeteilt.
* [ ] Nachrangigkeit: Bei `P=10, V=6, L=5` erhalten die Consumer `6`, die ZEV-Ladung ist `4`, Netzladung `1` (Kappung).
* [ ] `zev` des Speichers: Ladungs-Intervall `+ZEV-Ladung`, Entladungs-Intervall `−D×E/Q`; ohne Consumer im Intervall ist die ZEV-Ladung `min(L, Q)`.
* [ ] zev(Producer) = `D × P/Q` und enthält die ZEV-Ladung **nicht** (`P=10, V=6, L=5` → zev(Producer) = 6, ZEV-Ladung 4 steht im zev(Speicher)); CSV-Producer bleiben unverändert.
* [ ] Ein reines Entladungs-Intervall **ohne** Producer-Einheit wird trotzdem verteilt (der `producers.isEmpty()`-Guard darf die Verteilung nicht mehr überspringen, wenn ein entladender Speicher vorhanden ist).
* [ ] Ohne Speicher-Einheit ist das Verteilergebnis **identisch** zu heute (Regression).
* [ ] **Im Modus `BILANZ` ist das Verteilergebnis identisch zu heute — auch MIT Speicher-Einheit und mit Lade-/Entlade-Messwerten** (Regression). `S` wird weiterhin allein aus `ConsumerTotal − Bezug` gebildet.
* [ ] Derselbe Datensatz, einmal in jedem Modus verteilt, ergibt im Bilanzmodus **nicht** die zusätzliche Zuteilung aus `Q = P + E` — sonst wäre die Entladung doppelt gezählt.
* [ ] Der Publisher-Simulator (`pi-gateway`) erzeugt für einen Messpunkt mit „speicher" im Namen abwechselnde Lade-/Entladephasen (Modus `speicher`).

### Statistik
* [ ] Existiert eine Speicher-Einheit, zeigt die Werte-Tabelle die Zeilen „`<Name>` (Ladung)" und „`<Name>` (Entladung)" mit Balken (Web + PDF) — **in beiden Verteilmodi**; ohne Speicher entfallen sie.
* [ ] Die Zeile „`<Name>` (Netzladung)" (mit Tooltip) erscheint **nur im Modus `PRODUCER_MESSUNG`**; im Modus `BILANZ` fehlt sie.
* [ ] Die gemessenen Zeilen tragen den Zusatz „gemessen" und einen Tooltip, der auf die berechneten Kennzahlen verweist.
* [ ] Die bestehenden Kennzahlen „Batterie geladen/entladen" (aus der Bilanz geschätzt) bleiben **unverändert** erhalten und verschwinden **nicht**, sobald eine Speicher-Einheit existiert.
* [ ] Weichen gemessener und berechneter Wert voneinander ab, erscheint **keine** Fehlermeldung und **keine** Korrektur — beide Zahlen stehen nebeneinander.
* [ ] Im Modus `BILANZ` bleiben die Vergleichswerte („Rücklieferung", „Bezug von VNB") **unverändert** gegenüber heute.
* [ ] Im Modus `PRODUCER_MESSUNG` rechnen die Vergleichswerte gemäss FR-4.3: „Rücklieferung" zieht `Σ|zev(Speicher)|` (Lade- **und** Entlade-Anteil, je Intervall absolut) ab, „Bezug von VNB" addiert die Netzladung. Konkret `P=10, V=6, L=5, E=0` → Rücklieferung 0, Bezug 1. Die Bilanz-Vergleiche gehen bei konsistenten Daten – auch bei PV-Ladung der Batterie – innerhalb der Toleranz auf.
* [ ] Die Summen A–E enthalten keine Speicher-Werte.

### Verrechnung & Sicherheit
* [ ] Für die Speicher-Einheit wird keine Rechnung erzeugt; die Consumer-Rechnungen bleiben unverändert (gleicher ZEV-Tarif).
* [ ] Anlegen/Ändern erfordert `einheit:write`; Statistik bleibt mit `statistik:read` aufrufbar; alle neuen UI-Texte via `TranslationService` (DE/EN).

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Die Verteilung lädt pro Zeitpunkt eine zusätzliche, kleine Ergebnismenge (max. eine Speicher-Einheit); Komplexität bleibt O(Zeitpunkte × Einheiten). Statistik: höchstens zwei zusätzliche Aggregat-Abfragen pro Monat; `statistik`-Cache unverändert.

### NFR-2: Sicherheit
* Keine neuen Permissions: Einheiten-Verwaltung `einheit:read`/`einheit:write`, Statistik `statistik:read`. Multi-Tenancy (`org_id`, `orgFilter`) unverändert; `orgId` stammt nie aus dem Request.

### NFR-3: Kompatibilität
* Rein additiv: kein Schema-Change, bestehende Einheiten/Auswertungen unverändert. Verteilläufe über Zeiträume **ohne** Speicher-Messwerte liefern exakt das heutige Ergebnis. Bestehende Bilanz-Vergleichsformeln sind der Spezialfall `L = E = 0`.
* **Im Modus `BILANZ` bleibt die Verteilung auch MIT Speicher unverändert** (FR-3a). Der einzige Unterschied dort sind die zwei zusätzlichen Statistik-Zeilen. Damit ist das Feature für den heute produktiv laufenden Mandanten (Hene) rein darstellend — eine Änderung an abgerechneten Werten kann nicht entstehen.

## 5. Edge Cases & Fehlerbehandlung
| Szenario | Verhalten                                                                                 |
|----------|-------------------------------------------------------------------------------------------|
| Keine Speicher-Einheit vorhanden | Verteilung/Statistik/Rechnung exakt wie heute (Spezialfall der Formeln)                   |
| Speicher-Einheit ohne Messwerte im Intervall/Zeitraum | wie nicht vorhanden; Statistik-Zeilen zeigen 0                                            |
| Ladung ohne PV-Überschuss (`Q − D = 0`) | ZEV-Ladung = 0, gesamte Ladung = Netzladung (fliesst in „Bezug von VNB")                  |
| Entladung ohne Consumer im Intervall | nichts verteilt (`D = 0`), zev(Speicher) = 0; Entladung erscheint als Rücklieferung |
| Entladung > Verbrauch | Zuteilung an Consumer gekappt, Rest der Entladung = Rücklieferung                         |
| Laden und Entladen im selben 15-min-Intervall | durch Delta-Bildung saldiert (ein Vorzeichen pro Intervall)                               |
| Zählerstands-Rücksprung | bestehender Reset-Guard (Δ < 0 → 0) greift je Register                                    |
| Zweite Speicher-Einheit anlegen/ändern | HTTP 400 `EINHEIT_SPEICHER_EXISTIERT`                                                     |
| Netzwerkfehler beim Laden der Statistik | bestehende Fehlerbehandlung der `StatistikComponent` unverändert                          |
| Speicher-Einheit im Modus `BILANZ` | Verteilung unverändert (FR-3a); Statistik zeigt Ladung und Entladung, **keine** Netzladung |
| Wechsel des Verteilmodus bei vorhandenen Speicher-Messwerten | Ein erneuter Verteillauf rechnet nach dem dann gültigen Modus; die Statistik-Zeilen Ladung/Entladung bleiben gleich, die Netzladung erscheint bzw. verschwindet |

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** MQTT-Integration (`Specs/MQTT-Integration.md`), Solarverteilung, Statistik inkl. Bilanzmesspunkt (`Specs/Bilanzmesspunkt.md`), Einheiten-Verwaltung.
* **Berührt ausserdem:** `Specs/Bilanzmodell.md` (zwei Verteilmodi, FR-3a) und
  `Specs/Statistik-Kennzahlen.md` (bereits vorhandene, aus der Bilanz geschätzte Batterie-Kennzahlen,
  FR-4.2). Beide sind **nach** dieser Spec entstanden.
* **Betroffener Code (Backend):**
  - `entity/EinheitTyp.java` — Wert `SPEICHER`.
  - `service/EinheitService.java` — Eindeutigkeits-Validierung um `SPEICHER` erweitern, mit **eigenem** Fehler-Key `EINHEIT_SPEICHER_EXISTIERT` (Entscheid). **Das ist mehr als eine Mengen-Erweiterung:** Heute prüft eine einzige Bedingung über `BILANZ_TYPEN` und wirft den gemeinsamen Key `EINHEIT_BILANZ_TYP_EXISTIERT` (Zeilen 50 und 65). Für einen eigenen Key braucht es einen zweiten Zweig oder eine Zuordnung Typ → Key; `SPEICHER` einfach zu `BILANZ_TYPEN` hinzuzufügen ergäbe die falsche Meldung.
  - `service/RechnungService.java` — **keine Änderung nötig.** Der Service wählt die Typen *positiv* aus (`if CONSUMER … else if PRODUCER … else if LADESTATION`); `SPEICHER` fällt von selbst durch. Einen Ausschluss-Zweig für Bilanz-Typen, an den man sich anhängen könnte, gibt es nicht.
  - `service/ZaehlerAggregationService.java` — **nur ein Kommentar, kein Code.** `messwert.setZev(typ == PRODUCER ? total : 0.0)` setzt `SPEICHER` bereits auf 0, FR-2.3 ist damit ohne Zutun erfüllt. Anzupassen ist die Erläuterung darüber: Sie sagt heute, Bilanz-Typen blieben „**dauerhaft** bei zev = 0" — für `SPEICHER` gilt das nicht, die Verteilung setzt ihn im Modus `PRODUCER_MESSUNG` (FR-3.4).
  - `service/MesswerteService.java` — **Kern, aber nur in `distributeProducerMessung`**: Speicher-Messwert je Zeitpunkt laden; Entladung in den Produktionspool, zweistufige Zuteilung (Consumer → ZEV-Ladung), `zev` für Speicher und Producer gemäss FR-3.4/3.5. **Achtung:** Der `if (producers.isEmpty()) continue;`-Guard und der „keine Consumer"-Zweig **in dieser Methode** müssen umgebaut werden — ein Intervall mit entladendem Speicher, aber ohne PRODUCER-Einheit muss weiterhin verteilt werden; verteilbar ist `Q = P + E`, nicht nur `P`.
    **`distributeBilanz` bleibt unangetastet** (FR-3a) — dort stehen dieselben Guards ein zweites Mal, und wer nach dem Codemuster sucht statt nach der Methode, ändert die falsche Stelle.
    > Verweise bewusst über **Methodennamen** statt Zeilennummern: Die Datei ist seit dem Schreiben dieser Spec um rund 130 Zeilen gewachsen, die ursprünglich genannten Positionen (`:204`, `230 ff.`) zeigen längst woanders hin.
  - `service/StatistikService.java` + `dto/MonatsStatistikDTO.java` — Ladung/Entladung + Speicher-Name; Vergleichsformeln FR-4.3.
  - `reports/statistik.jrxml` / `einheit-summen.jrxml` — neue Zeilen bzw. Typ-Label/ZEV-Spalte.
    > **Die Höhe des Detail-Bands mitziehen.** Sie steht auf `502` (zuletzt von 470 erhöht, als die
    > gemessenen Kennzahlen dazukamen). Passen die neuen Zeilen nicht hinein, werden sie
    > **stillschweigend abgeschnitten**: `JasperTemplateCompileTest` prüft, ob das Template
    > kompiliert, nicht ob alles darauf Platz hat. Das zeigt nur ein erzeugtes PDF — mit PDFBox
    > aus dem lokalen Maven-Repo als PNG gerendert, siehe `Specs/Nebenkosten/RechnungenGenerieren_Umsetzungsplan.md`.
* **Betroffener Code (Frontend):** `einheit.model.ts`, `einheit-form` (Typ-Option), `einheit-typ.pipe.ts` (5 Typen), `statistik.model.ts` + `statistik.component.*` (Zeilen), Tests/Mocks.
* **Simulator:** `pi-gateway/gateway/readers/sim_reader.py` Modus `"speicher"`, `config.sim.example.yaml`.
* **Datenmigration:** keine (nur Übersetzungs-Keys via Flyway, nächste freie Version prüfen).

## 7. Abgrenzung / Out of Scope
* **Steuerung/Optimierung der Batterie** (Lade-/Entladestrategie, SoC-Management) — das System misst und rechnet nur ab.
* **Eigener Speicher-Tarif** — Entladung wird zum normalen ZEV-Tarif verrechnet (geklärt); Wirkungsgradverluste werden nicht separat verrechnet.
* **Mehrere Speicher pro Mandant** (geklärt: max. einer).
* **State-of-Charge-/Leistungs-Anzeige** (nur Energiemengen im 15-min-Raster).
* **CSV-Import für Speicher-Messwerte** — Befüllung ausschliesslich über den MQTT-Pfad.

## 8. Offene Fragen
Vorab geklärt:
* [x] **Tarif der Entladung:** normaler ZEV-Tarif (kein eigener Tariftyp).
* [x] **Anzahl:** höchstens eine Speicher-Einheit je Mandant.
* [x] **Netzladung:** rechnerische Kappung (`ZEV-Ladung = min(Ladung, Überschuss)`), Rest zählt als Netzbezug.

Ebenfalls geklärt (Review /0_anforderungen-check, erste Runde):
* [x] **ZEV-Attribution der Ladung:** ausschliesslich im zev(Speicher); zev(Producer) bleibt `D × P/Q` (behebt die Doppelzählung in der Rücklieferungs-Bilanz). (FR-3.4/3.5/4.3)
* [x] **Quellen-Proportionalität:** Faktoren `P/Q` (Producer) bzw. `E/Q` (Speicher entladen) beibehalten.
* [x] **Statistik-Darstellung:** Ladung und Entladung als getrennte Zeilen, keine Saldo-Zeile. (FR-4.2)
* [x] **Netzladung:** eigene Statistik-Zeile mit Tooltip. (FR-4.2)

Geklärt (Review /0_anforderungen-check, zweite Runde am 13.09.2026 — die Spec war seit dem
17.07.2026 unverändert, während Bilanzmodell und Ladestationen dazukamen):
* [x] **Verteilmodus `BILANZ`:** Die Verteilung ändert sich **nicht** (FR-3a). Der Speicher steckt bereits in `S = max(0, ConsumerTotal − Bezug)`; ihn zusätzlich einzurechnen wäre eine Doppelzählung. Betrifft besonders Hene — den einzigen Mandanten mit Speicher, der `BILANZ` fährt.
* [x] **Statistik im Modus `BILANZ`:** Die Zeilen Ladung und Entladung erscheinen **auch dort** (FR-4.2); sie stammen aus den Messwerten und sind vom Modus unabhängig.
* [x] **`RechnungService`:** keine Änderung nötig — der Service wählt Typen positiv aus, `SPEICHER` fällt von selbst durch. (§6)
* [x] **Eigener Fehler-Key `EINHEIT_SPEICHER_EXISTIERT`** bleibt; der dafür nötige Umbau der Prüfung ist in §6 benannt.
* [x] **Sechs Einheiten-Typen**, nicht fünf — `LADESTATION` kam dazwischen dazu. (FR-1.4, AK)

* [x] **Netzladung im Modus `BILANZ`:** entfällt ersatzlos (FR-4.2). Sie ist im Producer-Modus ein
  Korrekturterm in einer *gerechneten* Grösse: „Bezug von VNB" entsteht dort aus den Consumer-Daten
  und wird gegen die gemessene `BEZUG`-Einheit gestellt. Im Bilanzmodus ist der Bezug die
  **Eingangsgrösse** (`S = ConsumerTotal − Bezug`), der Vergleichswert also per Konstruktion wieder
  genau der Bezug. Es gibt keine Differenz, die die Netzladung erklären müsste.

* [x] **Doppelte Batterie-Kennzahlen — Entscheid: beide nebeneinander.** Die Statistik rechnet
  bereits ohne Speicher-Einheit `batterieGeladen`, `batterieEntladen` und `batterieWirkungsgrad`,
  geschätzt aus der Bilanz über `Netto_i = P_i − C_i + B_i − R_i`
  (`Specs/Statistik-Kennzahlen.md` Stufe 2, `StatistikService.berechneBatterieKennzahlen`). Diese
  Spec ist älter und kannte sie nicht.

  **Beide Werte bleiben und stehen nebeneinander**, die gemessene Seite mit dem Zusatz „gemessen"
  und einem Tooltip (FR-4.2, FR-6). Begründung: Die eine Zahl stammt aus vier Zählern am
  Netzanschluss, die andere aus einem am Speicher — ein Auseinanderlaufen ist eine Aussage über die
  Messkette, kein Fehler. Dieselbe Darstellung wie bei den beiden Autarkiegraden. Dazu kommt ein
  praktisches Argument: Die geschätzte Kennzahl hängt an Producer **und** Rücklieferung; fällt einer
  dieser Bilanzzähler aus, verschwindet sie — die gemessene bliebe stehen.

Keine offenen Punkte mehr — bereit für den Umsetzungsplan.
