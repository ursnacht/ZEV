# Batteriespeicher — Umsetzungsplan

## Zusammenfassung

Ein Batteriespeicher wird als Einheiten-Typ **`SPEICHER`** eingebunden: Sein Zähler liefert Ladung
und Entladung über den bestehenden MQTT-Pfad, die Statistik weist beides aus, verrechnet wird er
nicht. **Der Umbau der Solarverteilung (FR-3) und die angepassten Vergleichswerte (FR-4.3) sind
zurückgestellt** — sie betreffen ausschliesslich den Verteilmodus `PRODUCER_MESSUNG`, nützen keinem
Mandanten mit Batterie und zielen auf einen Pfad, der längerfristig entfällt (Kasten am Anfang der
Spec).

Umgesetzt wird damit der **Sichtbarkeitsteil**: Ohne ihn kommt die Batterie in den Daten gar nicht
vor, und die Wirkung der Steuerung aus `Specs/Einspeisesteuerung.md` liesse sich nicht beurteilen.

## Betroffene Komponenten

**Backend**
| Datei | Änderung |
|---|---|
| `entity/EinheitTyp.java` | Wert `SPEICHER` mit Javadoc |
| `service/EinheitService.java` | Eindeutigkeit je Mandant, **eigener** Fehler-Key |
| `service/ZaehlerAggregationService.java` | **nur Kommentar** — `zev = 0` gilt für `SPEICHER` bereits |
| `dto/MonatsStatistikDTO.java` | `speicherName`, `speicherLadung`, `speicherEntladung` |
| `service/StatistikService.java` | die beiden Summen je Zeitraum |
| `repository/MesswerteRepository.java` | Summe der positiven bzw. negativen `total` je Typ |
| `resources/reports/statistik.jrxml` | zwei Zeilen **und Bandhöhe** |
| `resources/reports/einheit-summen.jrxml` | Typ-Label (s. u.) |
| `db/migration/V<nächste>__Add_Speicher_Translations.sql` | Übersetzungen |

**Frontend**
| Datei | Änderung |
|---|---|
| `models/einheit.model.ts` | `SPEICHER` im Enum |
| `components/einheit-form/` | Typ-Option |
| `pipes/einheit-typ.pipe.ts` | eigener `case` (s. u.) |
| `models/statistik.model.ts` | drei neue Felder |
| `components/statistik/statistik.component.html` | zwei Zeilen nach dem Muster von `bilanzBezugName` |

**Simulator**
| Datei | Änderung |
|---|---|
| `pi-gateway/gateway/readers/sim_reader.py` | Modus `"speicher"` |
| `pi-gateway/config.sim.example.yaml` | Beispiel-Messpunkt |

> **Zwei Stellen mit derselben Falle — beide sind `default`-Zweige, die stillschweigend das Falsche
> beschriften:**
> * `einheit-typ.pipe.ts` endet auf `PRODUZENT` — ein Speicher hiesse dort „Produzent".
> * `einheit-summen.jrxml` (Zeile 68–71) endet auf `KONSUMENT` — ein Speicher hiesse dort
>   „Konsument". **Und `LADESTATION` ist dort bereits falsch**: Der Ausdruck kennt nur `PRODUCER`,
>   `BEZUG` und `RUECKLIEFERUNG`, alles andere wird „Konsument". Dieser bestehende Fehler gehört
>   beim Anfassen mitgenommen.
>
> Kein Compiler-Fehler, keine Ausnahme — nur eine falsche Beschriftung neben richtigen Zahlen. Die
> Tests müssen den **erwarteten Text** nennen, nicht bloss prüfen, dass irgendeiner erscheint.

## Phasen

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Enum Backend | `EinheitTyp.SPEICHER` mit Javadoc (Ladung positiv, Entladung negativ). Keine Migration — `einheit.typ` ist `VARCHAR` |
| [x] | 2. Eindeutigkeit | `EinheitService`: höchstens **eine** `SPEICHER`-Einheit je Mandant, Fehler-Key `EINHEIT_SPEICHER_EXISTIERT`, HTTP 400. **Umbau nötig:** Heute prüft eine Bedingung über `BILANZ_TYPEN` und wirft den gemeinsamen Key (Zeilen 50/65); für einen eigenen Key braucht es einen zweiten Zweig oder eine Zuordnung Typ → Key |
| [~] | 3. Übersetzungen | Migration mit `TYP_SPEICHER`, `EINHEIT_SPEICHER_EXISTIERT`, `STATISTIK_LADUNG` („Ladung, gemessen"), `STATISTIK_ENTLADUNG` („Entladung, gemessen"), `TOOLTIP_SPEICHER_GEMESSEN`. **Teilweise (V152):** `TYP_SPEICHER` und `EINHEIT_SPEICHER_EXISTIERT` sind da. `STATISTIK_LADUNG`, `STATISTIK_ENTLADUNG` und `TOOLTIP_SPEICHER_GEMESSEN` folgen mit Phase 7/8 — ohne die Statistik-Zeilen wären es Karteileichen |
| [x] | 4. Typ-Anzeige | `einheit-typ.pipe.ts` (eigener `case`) und `einheit-summen.jrxml` (Ausdruck um `SPEICHER` **und** `LADESTATION` erweitern). Siehe Kasten oben |
| [x] | 5. Frontend-Enum & Formular | `einheit.model.ts`, Typ-Option im `einheit-form` |
| [x] | 6. Aggregation | `ZaehlerAggregationService`: **nur den Kommentar** über `setZev(...)` ergänzen. Der Code setzt `SPEICHER` bereits auf `zev = 0`; die Erläuterung behauptet heute, Bilanz-Typen blieben „dauerhaft" bei 0 — das gilt für den Speicher nicht, sobald Phase 10 käme |
| [ ] | 7. Statistik Backend | `MonatsStatistikDTO` um `speicherName`, `speicherLadung`, `speicherEntladung`; `StatistikService` füllt sie aus einer neuen Repository-Abfrage (Summe der positiven bzw. negativen `total` der `SPEICHER`-Einheit im Zeitraum). Nur wenn eine Speicher-Einheit existiert, sonst `null` |
| [ ] | 8. Statistik Frontend | Zwei Zeilen mit Balken nach dem Muster von `bilanzBezugName` (`statistik.component.html` ab Zeile 218), Tooltip `TOOLTIP_SPEICHER_GEMESSEN`; `statistik.model.ts` nachziehen |
| [ ] | 9. Statistik PDF | Zwei Zeilen in `statistik.jrxml` **und die Bandhöhe von 502 erhöhen**. `JasperTemplateCompileTest` prüft nur, ob das Template kompiliert — ein zu kleines Band schneidet still ab. Sichtprüfung am erzeugten PDF (PDFBox-Weg, s. `Specs/Nebenkosten/RechnungenGenerieren_Umsetzungsplan.md`) |
| [x] | 10. Simulator | `sim_reader.py`: Modus `"speicher"` (Name enthält „speicher") mit wechselnden Lade-/Entladephasen, analog den bestehenden Modi; Beispiel in `config.sim.example.yaml` |
| [~] | 11. Tests | Backend: Eindeutigkeit (beide Zweige), Aggregation `zev = 0`, Statistik-Summen mit und ohne Speicher-Einheit. Frontend: Pipe mit **erwartetem Text**, Statistik-Zeilen. Regression: Statistik ohne Speicher unverändert. **Erledigt:** Eindeutigkeit (beide Zweige plus die Gegenprobe, dass PRODUCER ungeprüft bleibt), Pipe mit erwartetem Text. **Offen:** alles zur Statistik (Phase 7/8) |

### Zurückgestellt — nur bei Verteilmodus `PRODUCER_MESSUNG`

Diese Phasen werden **nicht** umgesetzt, solange die Absicht gilt, längerfristig nur noch den
Bilanzmodus zu unterstützen. Sie stehen hier, damit klar ist, was fehlt — nicht als Arbeitsvorrat.

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [–] | A. Verteilung (FR-3) | `MesswerteService.distributeProducerMessung`: Entladung in den Produktionspool (`Q = P + E`), zweistufige Zuteilung, `zev` für Speicher und Producer, Umbau der beiden Guards. **`distributeBilanz` bleibt unangetastet** (FR-3a) — dieselben Guards stehen dort ein zweites Mal |
| [–] | B. Netzladung | Dritte Statistik-Zeile `STATISTIK_NETZLADUNG` mit `TOOLTIP_NETZLADUNG`. Setzt A voraus: Ohne ZEV-Ladung gibt es die Grösse nicht |
| [–] | C. Vergleichswerte (FR-4.3) | „Rücklieferung" und „Bezug von VNB" um Entladung, `Σ\|zev(Speicher)\|` und Netzladung erweitern |

## Validierungen

**Backend**
| Regel | Ort | Fehler |
|---|---|---|
| Höchstens **eine** `SPEICHER`-Einheit je Mandant (`org_id`) | `EinheitService.save` und `.update` | `EINHEIT_SPEICHER_EXISTIERT`, HTTP 400 |
| `messpunkt` eindeutig je Mandant | bestehend, unverändert | `EINHEIT_MESSPUNKT_EXISTIERT` |
| `SPEICHER` wird nicht verrechnet | `RechnungService` — **keine Änderung**, der Service wählt Typen positiv aus (`CONSUMER`/`PRODUCER`/`LADESTATION`), alles andere fällt durch | — |

**Frontend**
| Regel | Ort |
|---|---|
| Typ „Speicher" im Dropdown wählbar | `einheit-form` |
| Fehlermeldung des Servers wird übersetzt angezeigt | bestehendes Muster (`error.error \|\| 'FALLBACK'`) |

**Multi-Tenancy:** Keine neue Entity — `einheit` und `messwerte` tragen `org_id` und den `orgFilter`
bereits. `existsByTyp` ist damit automatisch mandantenbezogen.

## Offene Punkte / Annahmen

* **§8 der Spec hat keine offenen Fragen** — alle elf Punkte sind entschieden und begründet.
* **Annahme:** Die Zeilen „Ladung, gemessen" und „Entladung, gemessen" sind auch **ohne** die
  Netzladung sinnvoll. Sie kommen unmittelbar aus den Messwerten und sind vom Verteilmodus
  unabhängig (FR-3a); die Netzladung ist die einzige der drei, die die Verteilungsrechnung braucht.
* **Annahme:** Die neue Repository-Abfrage summiert positive und negative `total` getrennt in
  **einer** Abfrage (zwei Aggregate über dieselbe Zeitspanne), nicht in zweien. Muster:
  `sumBilanzKomponentenPerZeitBetween`.
* **Bekannt und bewusst:** Neben den gemessenen Zeilen stehen weiterhin die aus der Bilanz
  **geschätzten** Kennzahlen `KENNZAHL_BATTERIE_GELADEN` / `_ENTLADEN` / `_WIRKUNGSGRAD`
  (`Specs/Statistik-Kennzahlen.md` Stufe 2). Beide Werte laufen auseinander; das ist kein
  Fehlerfall und wird weder korrigiert noch gemeldet (Entscheid in §8 der Spec).
* **Nicht Teil dieser Umsetzung:** Ladezustand (SoC), Steuerung der Batterie, CSV-Import für
  Speicher-Messwerte, mehr als ein Speicher je Mandant — alles in §7 der Spec abgegrenzt.
* **Reihenfolge-Hinweis:** Die Phasen 1–5 sind in sich abgeschlossen und liefern eine anlegbare,
  korrekt beschriftete Speicher-Einheit. Erst ab Phase 7 braucht es Messwerte, also entweder den
  Simulator (Phase 10) oder einen echten Zähler.


### Nachtrag — Teilumsetzung für die Einspeisesteuerung (16.09.2026)

**Auslöser:** Der Pi liefert seit heute Ladung und Entladung des Solinteg MHT via MQTT
(`zev/1/Batterie-Hene/messwert`, Register 31108/31110). Ohne den Einheiten-Typ wurden die
Nachrichten mit „unbekannter Messpunkt" verworfen.

**Umgesetzt sind die Phasen 1, 2, 4, 5 und 6** — genug, damit die Messwerte ankommen und in der
Bilanz auftauchen. Die Statistik-Phasen 7 bis 9 bleiben offen; sie brauchen die Zeilen in Web und
PDF und sind ein eigenes Stück Arbeit.

**Vor der Migration geprüft:** `zev.einheit.typ` ist `VARCHAR(20)` **ohne** CHECK-Constraint — die
Enum-Erweiterung braucht keine DDL, wie der Plan annahm. Bei anderen Enums im Projekt zählt ein
CHECK die erlaubten Werte auf; dort wäre es beim ersten Insert gescheitert.

**Die Eindeutigkeit** ist als Zuordnung `Typ → Fehlerkey` umgesetzt, nicht als zweiter Zweig: Ein
weiterer einmaliger Typ ist damit eine Zeile, und jeder behält seine eigene Meldung.

**Zwei Tests, die mehr prüfen als verlangt:**
* `einheit-form.component.spec.ts` verglich die Typauswahl gegen die **feste Zahl 5** und fiel
  sofort um. Er vergleicht jetzt gegen `Object.values(EinheitTyp)` — ein künftig vergessener Typ
  fällt damit auf, statt nur einen Zähler zu verschieben.
* `einheit-typ.pipe.spec.ts` prüft neu, dass **jeder** Enum-Wert ausser `PRODUCER` eine eigene
  Beschriftung bekommt. Der `default`-Zweig der Pipe fängt `PRODUCER` und alles Unbekannte ab; ein
  neuer Typ ohne eigenen `case` erschiene sonst stillschweigend als „Produzent" — genau davor warnt
  der Kommentar dort, und jetzt fällt der Test statt der Anzeige.

**`LADESTATION` im PDF-Subreport mitgenommen** (Phase 4 verlangt es): Der Ausdruck kannte sie nicht
und hätte sie als „Konsument" beschriftet. Praktisch trat das nie auf, weil Ladestationen keine
Messwerte erhalten — es blieb ein latenter Fehler.

**Geprüft:** 1321 Backend-Tests, 1657 Frontend-Tests, `JasperTemplateCompileTest` grün.

**Nötig:** Rebuild für V152, danach die Einheit `Batterie-Hene` vom Typ Speicher anlegen.

**Nachtrag Simulator (Phase 10, 16.09.2026):** Lokal kam nichts an — der Simulator kannte keinen
Speicher. Ergänzt als Modus `speicher`, erkannt an **„speicher" oder „batterie"** im Messpunkt: In
der Praxis heisst der Messpunkt nach dem Gerät, nicht nach seinem Einheiten-Typ (bei Hene
`Batterie-Hene`).

Der Modus unterscheidet sich als einziger grundlegend von den übrigen: Er erzeugt **wechselnde
Phasen**, weil ein Speicher lädt *oder* entlädt, nie beides. Liessen beide Register gleichzeitig
wachsen, wäre `total` dauerhaft nahe null und die Aggregation zeigte eine Batterie, die nichts tut.

Geprüft über 40 Lesevorgänge: klare Phasen (`LLLLLLLLLLLEEEEEEELLLLLLLEEEEEEEEEEEELLL`), nie beide
Register zugleich, Wirkungsgrad **91.8 %** — praktisch identisch mit den 92.3 %, die der echte
Speicher bei Hene zeigt. Ein Wert über 100 % wäre der stille Hinweis auf vertauschte Register.
