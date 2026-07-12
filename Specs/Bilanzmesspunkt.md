# Bilanzmesspunkt

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Zwei neue Einheiten-Typen **`Bezug`** und **`Rücklieferung`** einführen, mit denen die **gesamte am ZEV-Bilanzmesspunkt** aus dem Netz bezogene bzw. ins Netz zurückgelieferte Energie gemessen und aggregiert wird. Die aggregierten Summen dieser beiden Typen werden in der **Statistik** je einem berechneten Wert gegenübergestellt (Summen-Vergleich): der Typ `Bezug` gegen den bereits vorhandenen Wert **„Bezug von VNB"** und der Typ `Rücklieferung` gegen den Wert **„Rücklieferung"**.
* **Warum machen wir das:** Der Bilanzmesspunkt am Netzanschluss (Verrechnungsmessung des VNB) liefert die „Ground Truth" für Bezug und Rücklieferung des gesamten ZEV. Der Vergleich mit den aus den Einzel-Messungen **berechneten** Werten (`Bezug von VNB`, `Rücklieferung`) dient als **Plausibilisierung/Kontrolle**: Stimmen Summe der Einzelmessungen und Bilanzmessung im Rahmen der Toleranz überein, sind die Daten konsistent.
* **Aktueller Stand:**
  - Es gibt genau zwei Einheiten-Typen: `PRODUCER` und `CONSUMER` (`EinheitTyp`; gespeichert als `VARCHAR` in `einheit.typ`, `@Enumerated(STRING)`).
  - Die Statistik (`StatistikService`/`StatistikComponent`) zeigt pro Monat die Summen A–E, zwei berechnete Werte **`Bezug von VNB`** (= `ConsumerTotal − zev_berechnet der Consumer`) und **`Rücklieferung`** (= `ProducerTotal − zev der Producer`) sowie drei Summen-Vergleiche (A=B, A=C, B=C) mit Toleranz `0.1 kWh`.
  - Die MQTT-Aggregation (`ZaehlerAggregationService`) bildet je Einheit `total = ΔBezug − ΔEinspeisung`; Producer erhalten `zev = total`, Consumer `zev = 0`.
  - Die Solarverteilung (`MesswerteService.distribute`) verarbeitet ausschliesslich Einheiten der Typen `PRODUCER` und `CONSUMER` (Abfrage `findByZeitAndEinheitTyp`).

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Neue Einheiten-Typen
1. Der Aufzählungstyp `EinheitTyp` wird um zwei Werte erweitert: **`BEZUG`** (Anzeige „Bezug") und **`RUECKLIEFERUNG`** (Anzeige „Rücklieferung").
2. Einheiten dieser Typen werden **wie bisher** über das Einheiten-Formular angelegt/bearbeitet (Typ-Auswahl um die zwei neuen Optionen erweitert). Sie besitzen einen `messpunkt` wie andere Einheiten.
3. **Eindeutigkeit:** Pro Mandant (`org_id`) darf es **höchstens eine** Einheit vom Typ `BEZUG` und **höchstens eine** vom Typ `RUECKLIEFERUNG` geben (es gibt genau einen Bilanzmesspunkt je Richtung). Der Versuch, eine zweite Einheit desselben Bilanz-Typs anzulegen, wird mit einer verständlichen Fehlermeldung abgewiesen (Validierung im `EinheitService`). Für `PRODUCER`/`CONSUMER` gilt diese Beschränkung **nicht**.
4. Keine Schema-/Constraint-Migration nötig (`einheit.typ` ist `VARCHAR`); es sind **nur neue Übersetzungs-Keys** erforderlich (FR-6).

### FR-2: Aggregation (wie andere Typen)
1. Messwerte der Typen `BEZUG` und `RUECKLIEFERUNG` werden **identisch zu den übrigen Typen** aggregiert (`ZaehlerAggregationService`): `total = ΔBezug − ΔEinspeisung`, Reset-Guard pro Register, Upsert in `messwerte`, `quelle = MQTT`.
2. `zev` dieser Einheiten wird beim Ingest auf **`0`** gesetzt (kein Producer → kein `zev = total`). Da diese Typen nicht an der Verteilung teilnehmen, bleibt `zev`/`zev_calculated` ohne fachliche Bedeutung.

### FR-3: Ausschluss aus der Solarverteilung
1. Einheiten der Typen `BEZUG` und `RUECKLIEFERUNG` **fliessen nicht in die Solarstromverteilung ein** (`MesswerteService`): Sie werden **weder** als Producer (Erzeugung) **noch** als Consumer (Verbrauch) berücksichtigt.
2. Da die Verteilung nur `PRODUCER`/`CONSUMER` abfragt, ist dies durch **Nicht-Aufnahme** sichergestellt; `zev_calculated` dieser Einheiten wird nicht gesetzt.

### FR-4: Zwei neue Summen-Vergleiche in der Statistik
Pro Monat werden **zusätzlich** zu A=B, A=C, B=C zwei neue Vergleiche berechnet und angezeigt — **vor** oder unmittelbar im Bereich „Summen-Vergleich" (konsistent mit den bestehenden Vergleichen):
1. **Bezug-Vergleich:** berechneter Wert **`Bezug von VNB`** (`ConsumerTotal − zev_berechnet Consumer`) gegen die **Bilanz-Summe Bezug** = Summe `total` der Einheit(en) vom Typ `BEZUG` im Zeitraum.
2. **Rücklieferungs-Vergleich:** berechneter Wert **`Rücklieferung`** (`ProducerTotal − zev der Producer`) gegen die **Bilanz-Summe Rücklieferung** = Summe `total` der Einheit(en) vom Typ `RUECKLIEFERUNG` im Zeitraum.
3. Es gilt **dieselbe Toleranz** wie bei den bestehenden Vergleichen (`TOLERANZ = 0.1 kWh`). Ein Vergleich gilt als „gleich", wenn `|Differenz| < TOLERANZ`.
4. Bei Abweichung wird — wie bei den anderen Vergleichen — die **Differenz** angezeigt.
5. **Vorzeichenkonvention (geklärt):** Die Bilanz-Summen werden mit ihrem **natürlichen Vorzeichen** geführt: **Bezug positiv**, **Rücklieferung negativ** (Rücklieferung wird — wie die Producer-Einspeisung — negativ aggregiert). Für die Gleichheitsprüfung werden beide Seiten auf dasselbe Vorzeichen gebracht:
   * **Bezug:** `differenzBezug = BezugVonVnb − BilanzSummeBezug` (beide positiv).
   * **Rücklieferung:** Vergleich über die Beträge — die berechnete `Rücklieferung` liegt positiv vor, die Bilanz-Summe negativ; `differenzRuecklieferung = Rücklieferung − |BilanzSummeRücklieferung|`.
   * In beiden Fällen: „gleich" ⇔ `|Differenz| < TOLERANZ`.
6. Sind **keine** Einheiten des jeweiligen Typs vorhanden, ist die Bilanz-Summe `0`; der Vergleich wird **dennoch angezeigt** (Differenz = berechneter Wert).

### FR-5: Anzeige / Layout
1. **Statistik-Seite** (`StatistikComponent`): zwei zusätzliche Vergleichs-Items im Bereich „Summen-Vergleich" (Design-System-`zev-comparison-item` mit Status-Dot ✓/✗ und Differenz-Anzeige, wie bestehend). Die Vergleiche tragen **eigene, kurze Kürzel/Labels** (analog „A = B"; **nicht** die bestehenden Value-Keys `BEZUG_VON_VNB`/`RUECKLIEFERUNG` wiederverwenden). Optional: die beiden Bilanz-Summen als zusätzliche Zeilen/Balken in der Werte-Tabelle.
2. **Statistik-PDF** (`statistik.jrxml`): die zwei neuen Vergleiche werden — **konsistent zur Web-Ansicht** — ebenfalls ausgegeben (in Scope).
3. **Einheiten-Formular** (`EinheitFormComponent`): Typ-Dropdown um „Bezug" und „Rücklieferung" ergänzt.
4. **Typ-Anzeige** (`EinheitTypPipe`, Einheiten-Liste, „Summen pro Einheit"): muss alle **vier** Typen korrekt beschriften (bisher binär `CONSUMER` vs. sonst → `PRODUZENT`; muss auf 4 Typen erweitert werden).
5. **„Summen pro Einheit":** Bilanz-Einheiten werden dort **mitgelistet**, mit **Absolutwerten** (analog zur Producer-Darstellung).
6. Alle neuen Texte über `TranslationService`/`TranslatePipe` (keine Hardcodings).

### FR-6: Persistierung & i18n
* Keine neue Tabelle/Spalte; `einheit.typ` speichert die neuen Enum-Strings `BEZUG` / `RUECKLIEFERUNG`.
* Neue Übersetzungs-Keys via Flyway-Migration (`ON CONFLICT (key) DO NOTHING`), u.a.:
  - Typ-Labels: `TYP_BEZUG` („Bezug" / „Grid supply"), `TYP_RUECKLIEFERUNG` („Rücklieferung" / „Feed-in").
  - **Eigene** Vergleichs-Label-Keys (nicht die bestehenden `BEZUG_VON_VNB`/`RUECKLIEFERUNG` wiederverwenden), z.B. `VERGLEICH_BEZUG` („Bezug ↔ Bilanz" / „Supply ↔ balance") und `VERGLEICH_RUECKLIEFERUNG` („Rücklieferung ↔ Bilanz" / „Feed-in ↔ balance"); genaue Wortwahl im Umsetzungsplan finalisieren.
  - Fehlermeldung Eindeutigkeit: z.B. `EINHEIT_BILANZ_TYP_EXISTIERT` („Es existiert bereits eine Einheit dieses Bilanz-Typs" / „A unit of this balance type already exists").
* Mandantenfähigkeit: `messwerte`/`einheit` tragen `org_id` und unterliegen dem Hibernate-`orgFilter` — für die neuen Typen unverändert gültig (keine Sonderbehandlung).

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

### Einheiten-Typen
* [ ] `EinheitTyp` enthält die Werte `BEZUG` und `RUECKLIEFERUNG` zusätzlich zu `PRODUCER`/`CONSUMER`.
* [ ] Im Einheiten-Formular können Einheiten der Typen „Bezug" und „Rücklieferung" angelegt und bearbeitet werden.
* [ ] Die Typ-Anzeige (`EinheitTypPipe` / Listen / Summen pro Einheit) zeigt für alle vier Typen die korrekte, übersetzte Beschriftung an.
* [ ] Speichern und Laden einer Einheit vom Typ `BEZUG`/`RUECKLIEFERUNG` funktioniert (Roundtrip über DB).
* [ ] Der Versuch, eine **zweite** Einheit vom Typ `BEZUG` (bzw. `RUECKLIEFERUNG`) im selben Mandanten anzulegen, wird mit Fehlermeldung abgewiesen; für `PRODUCER`/`CONSUMER` bleibt beliebige Anzahl möglich.

### Aggregation & Verteilung
* [ ] Messwerte von `BEZUG`/`RUECKLIEFERUNG`-Einheiten werden vom Aggregations-Job wie andere Typen zu `messwerte` aggregiert (`total` korrekt, `quelle = MQTT`).
* [ ] Einheiten der Typen `BEZUG`/`RUECKLIEFERUNG` werden von der Solarverteilung **nicht** berücksichtigt: ihr `zev_calculated` bleibt unverändert (nicht gesetzt), und sie beeinflussen `zev_calculated` der Consumer **nicht**.
* [ ] Die bestehenden Statistik-Summen A–E (Producer/Consumer) enthalten **keine** Werte der neuen Typen.

### Statistik-Vergleiche
* [ ] Pro Monat wird ein Vergleich „Bezug von VNB (berechnet) ↔ Summe Typ `BEZUG`" mit Status (gleich/ungleich) und Differenz angezeigt.
* [ ] Pro Monat wird ein Vergleich „Rücklieferung (berechnet) ↔ Summe Typ `RUECKLIEFERUNG`" mit Status und Differenz angezeigt.
* [ ] Beide neuen Vergleiche verwenden dieselbe Toleranz (`0.1 kWh`); `|Differenz| < Toleranz` ⇒ „gleich".
* [ ] Liegt die Differenz ausserhalb der Toleranz, wird die Differenz (kWh) angezeigt.
* [ ] Sind keine Einheiten des jeweiligen Typs vorhanden, ist die Bilanz-Summe `0` und der Vergleich wird trotzdem angezeigt (Differenz = berechneter Wert).
* [ ] Der Bezug-Vergleich rechnet mit positivem, der Rücklieferungs-Vergleich mit negativer Bilanz-Summe (Vergleich über Beträge); beide nutzen Toleranz `0.1 kWh`.
* [ ] Die Bilanz-Einheiten erscheinen in „Summen pro Einheit" mit **Absolutwerten**.
* [ ] Die neuen Vergleiche erscheinen auch im PDF-Export (konsistent zur Web-Ansicht).

### Sicherheit & i18n
* [ ] Statistik bleibt mit Rolle `zev_user` (`statistik:read`) aufrufbar; keine neuen Berechtigungen nötig.
* [ ] Alle neuen UI-Texte stammen aus dem `TranslationService` (DE/EN), keine Hardcodings.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Die beiden zusätzlichen Bilanz-Summen werden über bereits vorhandene Aggregat-Abfragen (`sumTotalByEinheitTypAndZeitBetween`) ermittelt — pro Monat je eine zusätzliche, leichtgewichtige Aggregatabfrage. Die Statistik bleibt cache-fähig (`statistik`-Cache, TTL 15 min).

### NFR-2: Sicherheit
* Kein neues Rollen-/Permission-Modell. Statistik: `statistik:read` (Fachrolle `zev_user`). Einheiten-Verwaltung: `einheit:read`/`einheit:write` wie bisher (Anlegen der Bilanz-Einheiten benötigt `einheit:write`, Fachrolle `org_admin`/`zev_admin`).
* Multi-Tenancy: neue Typen ändern die Mandanten-Isolation nicht; `org_id`/`orgFilter` gelten unverändert.

### NFR-3: Kompatibilität
* **Rückwärtskompatibel:** `einheit.typ` ist `VARCHAR`, daher keine DB-Schema-Änderung. Bestehende Einheiten (`PRODUCER`/`CONSUMER`) und alle bestehenden Auswertungen bleiben unverändert.
* Bestehende Statistik-Summen/-Vergleiche und der CSV-Upload bleiben funktionsfähig.
* Code, der nur `PRODUCER`/`CONSUMER` unterscheidet (`RechnungService`, `EinheitTypPipe`, Verteilung), muss auf die vier Typen geprüft und so angepasst werden, dass Bilanz-Typen **nicht** fälschlich als Producer/Consumer behandelt werden (insb. **keine Verrechnung** in `RechnungService`).

## 5. Edge Cases & Fehlerbehandlung
| Szenario | Verhalten |
|----------|-----------|
| Keine `BEZUG`/`RUECKLIEFERUNG`-Einheit vorhanden | Bilanz-Summe = 0; Vergleich wird angezeigt, Differenz = berechneter Wert |
| Zweite Einheit desselben Bilanz-Typs anlegen | abgewiesen mit Fehlermeldung (`EINHEIT_BILANZ_TYP_EXISTIERT`) |
| `total` der Bilanz-Einheit ist `NULL` (keine Daten im Zeitraum) | als `0.0` behandeln (analog bestehende Summen) |
| Rücklieferung negativ aggregiert (Einspeisung) | Betrag (`abs`) für den Vergleich verwenden (wie Producer) |
| Bilanz-Einheit ohne Messwerte, aber vorhanden | erscheint ggf. in „Summen pro Einheit" mit 0-Werten |
| Bilanz-Einheit fälschlich in Rechnung/Verteilung | darf **nicht** vorkommen — Bilanz-Typen sind aus Verrechnung & Verteilung ausgeschlossen |
| Alte Datensätze / Migration | keine (nur additiv) |
| i18n-Key fehlt | Fallback: Key wird angezeigt (bestehendes `TranslatePipe`-Verhalten) |
| Netzwerkfehler beim Laden der Statistik | bestehende Fehlerbehandlung der `StatistikComponent` greift unverändert |

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** bestehende Statistik (`Specs/Statistik.md`), MQTT-Aggregation (`Specs/MQTT-Integration.md`), Einheiten-Verwaltung.
* **Betroffener Code (Backend):**
  - `entity/EinheitTyp.java` — zwei neue Enum-Werte.
  - `service/StatistikService.java` + `dto/MonatsStatistikDTO.java` — zwei neue Bilanz-Summen + zwei Vergleichsfelder (gleich/Differenz); Berechnung mit Toleranz.
  - `repository/MesswerteRepository.java` — `sumTotalByEinheitTypAndZeitBetween` ist bereits generisch nutzbar (kein neuer Query zwingend).
  - `service/MesswerteService.java` — sicherstellen, dass Verteilung nur `PRODUCER`/`CONSUMER` verarbeitet (Ist-Zustand; ggf. explizit dokumentieren/absichern).
  - `service/ZaehlerAggregationService.java` — `zev`-Behandlung für neue Typen (Default `0`); ansonsten unverändert.
  - `service/EinheitService.java` — Eindeutigkeits-Validierung (max. eine `BEZUG`- und eine `RUECKLIEFERUNG`-Einheit pro Mandant).
  - `service/RechnungService.java` — prüfen, dass Bilanz-Typen nicht verrechnet werden.
  - `service/StatistikPdfService.java` + `reports/statistik.jrxml` — zwei neue Vergleiche (falls in Scope).
* **Betroffener Code (Frontend):**
  - `models/einheit.model.ts` (`EinheitTyp`), `components/einheit-form` (Typ-Optionen), `pipes/einheit-typ.pipe.ts` (4 Typen).
  - `models/statistik.model.ts`, `components/statistik/*` (zwei neue Vergleichs-Items), zugehörige Tests/Mocks.
* **Datenmigration:** keine (nur neue Übersetzungs-Keys via Flyway).
* **i18n:** neue Keys für Typ-Labels und ggf. Vergleichs-Beschriftungen (DE/EN).

## 7. Abgrenzung / Out of Scope
* **Import der Bilanz-Werte aus einer Datei** (CSV/o.ä.) — **folgt später** (separate Spec). Aktuell werden Werte der Bilanz-Typen nur über den bestehenden Aggregations-/Ingest-Pfad befüllt.
* Keine automatische Anlage der Bilanz-Einheiten; sie werden manuell über das Einheiten-Formular erstellt.
* Keine Änderung der bestehenden Verteil-Logik oder der Summen A–E.
* Keine Verrechnung/Rechnungsstellung für Bilanz-Typen.
* KI-gestütztes Einheiten-Matching (CSV-Upload) für die neuen Typen ist nicht Teil dieser Spec.

## 8. Offene Fragen
Alle Fragen sind **geklärt** (in FR/AK eingearbeitet):
* [x] **Enum-Benennung:** → `BEZUG` / `RUECKLIEFERUNG` (deutsch, domänennah). (FR-1)
* [x] **Vorzeichenkonvention:** → **Bezug positiv, Rücklieferung negativ**; Gleichheitsprüfung über die Beträge, Toleranz `0.1 kWh`. (FR-4.5)
* [x] **PDF-Export:** → **konsistent zur Web-Ansicht**, in Scope. (FR-5.2)
* [x] **Leerer Vergleich:** → Bilanz-Summe `0`, Vergleich **anzeigen**. (FR-4.6)
* [x] **Anzahl Bilanz-Einheiten pro Typ:** → **höchstens eine** je Typ und Mandant (Validierung). (FR-1.3)
* [x] **„Summen pro Einheit":** → Bilanz-Einheiten **mitlisten, Absolutwerte**. (FR-5.5)
* [x] **Vergleichsbezeichnung im UI:** → **neue Kürzel/Label-Keys** (`VERGLEICH_BEZUG`/`VERGLEICH_RUECKLIEFERUNG`), nicht die Value-Keys wiederverwenden. (FR-5.1/FR-6)
