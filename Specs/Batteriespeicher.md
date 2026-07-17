# Batteriespeicher

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Ein Batteriespeicher wird als neuer Einheiten-Typ **`SPEICHER`** in den ZEV eingebunden. Sein Zähler liefert Ladung und Entladung über den bestehenden MQTT-Pfad (zwei Register). Die Solarverteilung berücksichtigt den Speicher **nachrangig**: Consumer werden zuerst bedient, nur der verbleibende PV-Überschuss gilt als ZEV-Ladung; entladener Strom wird wie Produktion auf die Consumer verteilt. Statistik und Bilanz-Vergleiche werden um Ladung/Entladung erweitert. Die Speicher-Einheit selbst wird **nicht verrechnet**.
* **Warum machen wir das:** Ein Speicher erhöht die Eigenverbrauchsquote des ZEV (PV-Überschuss wird gespeichert statt rückgeliefert und später im ZEV konsumiert). Damit Abrechnung und Plausibilisierung (Bilanz-Vergleiche) korrekt bleiben, muss der Speicher energetisch sauber modelliert sein.
* **Aktueller Stand:**
  - `EinheitTyp` kennt `PRODUCER`, `CONSUMER`, `BEZUG`, `RUECKLIEFERUNG`. Die Solarverteilung (`MesswerteService.distribute`) verarbeitet ausschliesslich `PRODUCER`/`CONSUMER`.
  - MQTT-Ingest/Aggregation: `total = ΔBezug − ΔEinspeisung` (vorzeichenbehaftet), Reset-Guard je Register, `quelle = MQTT`; der Wire-Contract transportiert bereits beide Register (`zaehlerstandBezug`/`zaehlerstandEinspeisung`).
  - Statistik: Summen A–E, berechnete Vergleichswerte `Bezug von VNB = ConsumerTotal − zev(Consumer)` und `Rücklieferung = ProducerTotal − zev(Producer)` gegen die Bilanz-Einheiten (`Specs/Bilanzmesspunkt.md`).
  - zev(Producer) = im ZEV konsumierte Produktion (= im Intervall verteilte Menge).

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Neuer Einheiten-Typ SPEICHER
1. `EinheitTyp` wird um **`SPEICHER`** erweitert (Anzeige „Speicher" / „Storage", Key `TYP_SPEICHER`). Keine Schema-Migration (`einheit.typ` ist `VARCHAR`).
2. Speicher-Einheiten werden wie andere Einheiten über das Einheiten-Formular angelegt (Typ-Dropdown um „Speicher" ergänzt) und besitzen einen `messpunkt`.
3. **Eindeutigkeit:** Pro Mandant (`org_id`) ist **höchstens eine** Einheit vom Typ `SPEICHER` erlaubt (Validierung im `EinheitService`, analog Bilanz-Typen; eigener Fehler-Key `EINHEIT_SPEICHER_EXISTIERT`, HTTP 400).
4. Die Typ-Anzeige (`EinheitTypPipe`, „Summen pro Einheit" Web **und** PDF-Subreport `einheit-summen.jrxml`) beschriftet alle **fünf** Typen korrekt.

### FR-2: Messung & Aggregation
1. Der Speicher-Zähler publiziert wie alle Zähler beide Register: `zaehlerstandBezug` = **Ladung** (kumulativ), `zaehlerstandEinspeisung` = **Entladung** (kumulativ). Ingest und Aggregation laufen unverändert: `total = ΔLadung − ΔEntladung` je 15-Minuten-Intervall (**positiv = Ladung, negativ = Entladung**; gleichzeitiges Laden/Entladen wird saldiert).
2. Beim Ingest/bei der Aggregation gilt für `SPEICHER` keine Register-Projektion (im Gegensatz zu den Bilanz-Typen): beide Register gehören zur **einen** Speicher-Einheit.
3. `zev` wird bei der Aggregation vorläufig auf `0` gesetzt; die anschliessende Solarverteilung setzt den ZEV-relevanten Anteil (FR-3.4). `zev_calculated` bleibt (wie bei Producern) ungesetzt.
4. Der Publisher-Simulator (`pi-gateway`) erhält einen Modus `"speicher"` (Name enthält „speicher"): wechselnde Phasen von Ladung und Entladung, damit der Pfad end-to-end testbar ist.

### FR-3: Nachrangige Einbindung in die Solarverteilung
Pro Zeitintervall mit den Grössen `P` = PV-Produktion (Betrag), `E` = Entladung (Betrag, bei `total < 0`), `L` = Ladung (bei `total > 0`), `V` = Summe Consumer-Verbrauch:
1. **Verteilbare Energie:** `Q = P + E`. Die Entladung wird der verteilbaren Produktion zugeschlagen (gespeicherter PV-Strom).
2. **Consumer zuerst:** Die Verteilung an die Consumer erfolgt wie bisher (EQUAL_SHARE/PROPORTIONAL, Zuteilung je Consumer am Verbrauch gekappt) auf Basis von `Q`; verteilte Menge `D ≤ min(Q, V)`.
3. **Speicher nachrangig (Ladung):** `ZEV-Ladung = min(L, Q − D)` — nur der nach den Consumern verbleibende Überschuss gilt als im ZEV geladene Energie. Der Rest (`L − ZEV-Ladung`) ist **Netzladung** (rechnerische Kappung, keine Konfiguration nötig).
4. **`zev` der Speicher-Einheit** (nur `quelle = MQTT`, vorzeichenbehaftet wie `total`): Die ZEV-Ladung wird **ausschliesslich** hier attribuiert (nicht zusätzlich im zev(Producer), sonst Doppelzählung in der Rücklieferungs-Bilanz):
   - Ladungs-Intervall: `zev = +ZEV-Ladung`.
   - Entladungs-Intervall: `zev = −(D × E / Q)` — der quellen-proportionale Anteil der Entladung an der verteilten Menge.
5. **zev(Producer) unverändert:** zev(Producer) (Summe) = `D × P/Q` (der auf PV entfallende Anteil der an Consumer verteilten Menge); bei mehreren Producern proportional zur Produktion aufgeteilt. Die in den Speicher geladene PV-Energie zählt **nicht** zum zev(Producer) (sie steht im zev(Speicher), FR-3.4). CSV-Producer bleiben unangetastet (gemessene Werte). Ohne Speicher (`E = 0`, `Q = P`) ergibt sich `D` — identisch zu heute.
6. zev(Consumer)/`zev_calculated` bleiben wie bisher = zugeteilte Menge (Quelle PV oder Speicher ist für den Consumer transparent, gleicher ZEV-Tarif).

### FR-4: Statistik & Bilanz-Vergleiche
1. Die Summen A–E (Producer/Consumer) enthalten **keine** Speicher-Werte.
2. **Neue Werte-Zeilen** (nur falls Speicher-Einheit existiert, Beschriftung = Einheiten-Name mit Zusatz), **Ladung und Entladung getrennt** (keine Saldo-Zeile, da ein Monatssaldo ≈ 0 irreführend wäre):
   - „`<Name>` (Ladung)" = Summe der positiven `total`-Intervalle (Brutto-Ladung, inkl. Netzladung), Key `STATISTIK_LADUNG`.
   - „`<Name>` (Entladung)" = Betrag der Summe der negativen `total`-Intervalle, Key `STATISTIK_ENTLADUNG`.
   - „`<Name>` (Netzladung)" = `Ladung − ZEV-Ladung` (Summe über die Ladungs-Intervalle), Key `STATISTIK_NETZLADUNG`; mit **Tooltip** (Key `TOOLTIP_NETZLADUNG`), der erläutert: Anteil der Ladung, der nicht aus PV-Überschuss stammt und daher als Netzbezug in „Bezug von VNB" einfliesst.
   - Alle drei mit Balken (gleiche Skala), Web + PDF.
3. **Angepasste Vergleichswerte** (Summen-Vergleich gegen die Bilanz-Einheiten):
   - `Rücklieferung (berechnet) = |ProducerTotal| + Entladung − zev(Producer) − Σ|zev(Speicher)|` — Produktion und Entladung, die weder von Consumern konsumiert noch (als PV) in den Speicher geladen wurden. **`Σ|zev(Speicher)|` = die je Intervall absolut genommene und dann summierte zev(Speicher)-Grösse** (nicht `|Σ zev|`): über Ladungs-Intervalle ergibt das die ZEV-Ladung, über Entlade-Intervalle `Σ(D × E/Q)`. Beide Anteile mindern die Rücklieferung (PV in die Batterie geladen bzw. entladener Strom im ZEV konsumiert). Da die ZEV-Ladung nur im zev(Speicher) steht (FR-3.4) und **nicht** im zev(Producer) (FR-3.5), wird sie genau einmal abgezogen. Kontrolle Ladungs-Intervall (E=0): `P − D − ZEV-Ladung` ✓ (z.B. `P=10, V=6, L=5`: `10 − 6 − 4 = 0`); Entladungs-Intervall (L=0): `P + E − D` ✓.
   - `Bezug von VNB (berechnet) = ConsumerTotal − zev(Consumer) + Netzladung` mit `Netzladung = Ladung − ZEV-Ladung` (= `Ladung − zev(Speicher geladen)` über die Ladungs-Intervalle).
   - Toleranz unverändert 0.1 kWh; ohne Speicher-Einheit sind die Formeln identisch zu heute (`L = E = 0`).
4. **„Summen pro Einheit":** Der Speicher wird mitgelistet (`total` = Absolutwert des Saldos `|Ladung − Entladung|`; `zev` = `Σ|zev(Speicher)|`, d.h. je Intervall absolut und summiert — konsistent zur Rücklieferungs-Formel FR-4.3, **nicht** `|Σ zev|`; „ZEV berechnet" = `-`). Sortierung: Produzenten, Konsumenten, **Speicher**, Rücklieferung, Bezug.

### FR-5: Verrechnung
1. Einheiten vom Typ `SPEICHER` werden **nicht verrechnet** (weder Consumer- noch Producer-Zweig im `RechnungService`, analog Bilanz-Typen).
2. Entladener Strom wird den Consumern implizit über deren `zev` zum **normalen ZEV-Tarif** verrechnet — keine Tarif- oder Rechnungsänderung.

### FR-6: Persistierung & i18n
* Keine neue Tabelle/Spalte; `einheit.typ` speichert `SPEICHER`.
* Neue Übersetzungs-Keys via Flyway-Migration (`ON CONFLICT (key) DO NOTHING`): `TYP_SPEICHER` („Speicher"/„Storage"), `EINHEIT_SPEICHER_EXISTIERT` („Es existiert bereits eine Speicher-Einheit."/„A storage unit already exists."), `STATISTIK_LADUNG` („Ladung"/„Charge"), `STATISTIK_ENTLADUNG` („Entladung"/„Discharge"), `STATISTIK_NETZLADUNG` („Netzladung"/„Grid charge") und `TOOLTIP_NETZLADUNG` (Erläuterung, s. FR-4.2).
* Multi-Tenancy unverändert: `einheit`/`messwerte` tragen `org_id` und unterliegen dem `orgFilter`.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

### Einheiten-Typ
* [ ] `EinheitTyp` enthält `SPEICHER`; im Formular ist „Speicher" wählbar, Roundtrip über die DB funktioniert.
* [ ] Eine **zweite** Speicher-Einheit im selben Mandanten wird mit `EINHEIT_SPEICHER_EXISTIERT` (HTTP 400, übersetzt angezeigt) abgewiesen.
* [ ] `EinheitTypPipe`, Einheiten-Liste, „Summen pro Einheit" (Web + PDF) beschriften alle fünf Typen korrekt.

### Aggregation & Verteilung
* [ ] Speicher-Messwerte werden wie andere Typen aggregiert: Ladung → positives, Entladung → negatives `total`; `quelle = MQTT`.
* [ ] Entladung erhöht die verteilbare Energie: Consumer erhalten bei `P=0, E=4, V=6` in Summe `4` kWh zugeteilt.
* [ ] Nachrangigkeit: Bei `P=10, V=6, L=5` erhalten die Consumer `6`, die ZEV-Ladung ist `4`, Netzladung `1` (Kappung).
* [ ] `zev` des Speichers: Ladungs-Intervall `+ZEV-Ladung`, Entladungs-Intervall `−D×E/Q`; ohne Consumer im Intervall ist die ZEV-Ladung `min(L, Q)`.
* [ ] zev(Producer) = `D × P/Q` und enthält die ZEV-Ladung **nicht** (`P=10, V=6, L=5` → zev(Producer) = 6, ZEV-Ladung 4 steht im zev(Speicher)); CSV-Producer bleiben unverändert.
* [ ] Ein reines Entladungs-Intervall **ohne** Producer-Einheit wird trotzdem verteilt (der `producers.isEmpty()`-Guard darf die Verteilung nicht mehr überspringen, wenn ein entladender Speicher vorhanden ist).
* [ ] Ohne Speicher-Einheit ist das Verteilergebnis **identisch** zu heute (Regression).
* [ ] Der Publisher-Simulator (`pi-gateway`) erzeugt für einen Messpunkt mit „speicher" im Namen abwechselnde Lade-/Entladephasen (Modus `speicher`).

### Statistik
* [ ] Existiert eine Speicher-Einheit, zeigt die Werte-Tabelle die Zeilen „`<Name>` (Ladung)", „`<Name>` (Entladung)" und „`<Name>` (Netzladung)" (mit Tooltip) mit Balken (Web + PDF); ohne Speicher entfallen sie.
* [ ] Die Vergleichswerte rechnen gemäss FR-4.3: „Rücklieferung" zieht `Σ|zev(Speicher)|` (Lade- **und** Entlade-Anteil, je Intervall absolut) ab, „Bezug von VNB" addiert die Netzladung. Konkret `P=10, V=6, L=5, E=0` → Rücklieferung 0, Bezug 1. Die Bilanz-Vergleiche gehen bei konsistenten Daten – auch bei PV-Ladung der Batterie – innerhalb der Toleranz auf.
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

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** MQTT-Integration (`Specs/MQTT-Integration.md`), Solarverteilung, Statistik inkl. Bilanzmesspunkt (`Specs/Bilanzmesspunkt.md`), Einheiten-Verwaltung.
* **Betroffener Code (Backend):**
  - `entity/EinheitTyp.java` — Wert `SPEICHER`.
  - `service/EinheitService.java` — Eindeutigkeits-Validierung um `SPEICHER` erweitern (eigener Fehler-Key).
  - `service/ZaehlerAggregationService.java` — `zev = 0` für `SPEICHER` (wie Consumer/Bilanz); Kommentar.
  - `service/MesswerteService.java` — **Kern**: Speicher-Messwert je Zeitpunkt laden; Entladung in den Produktionspool, zweistufige Zuteilung (Consumer → ZEV-Ladung), `zev` für Speicher und Producer gemäss FR-3.4/3.5. **Achtung:** Der heutige `if (producers.isEmpty()) continue;`-Guard (`MesswerteService.java:204`) und der „keine Consumer"-Zweig (Zeile 230 ff.) müssen umgebaut werden — ein Intervall mit entladendem Speicher aber ohne PRODUCER-Einheit muss weiterhin verteilt werden; verteilbar ist `Q = P + E`, nicht nur `P`.
  - `service/StatistikService.java` + `dto/MonatsStatistikDTO.java` — Ladung/Entladung + Speicher-Name; Vergleichsformeln FR-4.3.
  - `service/RechnungService.java` — `SPEICHER` im Ausschluss-Zweig (wie Bilanz-Typen).
  - `reports/statistik.jrxml` / `einheit-summen.jrxml` — neue Zeilen bzw. Typ-Label/ZEV-Spalte.
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

Ebenfalls geklärt (Review /0_anforderungen-check):
* [x] **ZEV-Attribution der Ladung:** ausschliesslich im zev(Speicher); zev(Producer) bleibt `D × P/Q` (behebt die Doppelzählung in der Rücklieferungs-Bilanz). (FR-3.4/3.5/4.3)
* [x] **Quellen-Proportionalität:** Faktoren `P/Q` (Producer) bzw. `E/Q` (Speicher entladen) beibehalten.
* [x] **Statistik-Darstellung:** Ladung und Entladung als getrennte Zeilen, keine Saldo-Zeile. (FR-4.2)
* [x] **Netzladung:** eigene Statistik-Zeile mit Tooltip. (FR-4.2)

Keine offenen Punkte mehr — bereit für den Umsetzungsplan.
