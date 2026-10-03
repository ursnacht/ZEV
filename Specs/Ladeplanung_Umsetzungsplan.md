# Ladeplanung — Umsetzungsplan

## Zusammenfassung

Die Entscheidung, welcher PV-Überschuss in den Speicher geht, wird von einer Regelkaskade je
Intervall auf eine **Merit-Order über den Resttag** umgestellt: Die Intervalle des Ortstages werden
nach Einspeisepreis aufsteigend sortiert und aufgefüllt, bis die freie Batteriekapazität gedeckt
ist. Liegt das ausgewertete Intervall im Plan, ist die Ladung frei, sonst gesperrt.

**Der Abruf der Einstrahlungsprognose und der gelernte Umrechnungsfaktor sind bereits umgesetzt**
(Commit `cf818f8`: FR-2, FR-3 ohne Lastprofil, FR-6, FR-7 und die davon berührten Übersetzungen).
Dieser Plan deckt den **verbleibenden** Teil ab: Lastprofil, erwarteter Überschuss, Merit-Order,
Rückfall-Kennzeichen und die Erweiterung des Entscheidungsprotokolls.

> **Weiterhin Trockenlauf.** Es wird nichts geschaltet — der Entscheid wird nur festgehalten
> (`Specs/Ladeplanung.md`, §7). Was sich ändert, ist allein der Inhalt der Spalten
> `batterieladung` und `einspeisung`, nicht ihre Wirkung.

---

## Betroffene Komponenten

### Neu

| Datei | Zweck |
|---|---|
| `db/migration/V169__Steuerentscheid_Ladeplanung.sql` | Sieben neue Spalten, `regel`-CHECK um `LADEPLAN` erweitert |
| `db/migration/V170__Add_Ladeplanung_Merit_Order_Translations.sql` | Die sieben offenen Übersetzungen |
| `entity/Steuerverfahren.java` | Enum `MERIT_ORDER` / `REGEL` |
| `service/LadeplanService.java` | Die Merit-Order (FR-1) |

### Geändert

| Datei | Änderung |
|---|---|
| `entity/Steuerregel.java` | Neuer Wert `LADEPLAN` |
| `entity/Steuerentscheid.java` | Sieben neue Felder |
| `dto/SteuerentscheidDTO.java` | Sieben neue Felder |
| `dto/PrognosepunktDTO.java` | Lastprofil und erwarteter Überschuss |
| `repository/SteuerentscheidRepository.java` | `upsert` um sieben Parameter erweitert |
| `repository/MesswerteRepository.java` | Query für das Lastprofil (`CONSUMER`, je Intervall) |
| `service/ProduktionsprognoseService.java` | Lastprofil und erwarteter Überschuss (FR-3-Rest) |
| `service/SteuerungService.java` | Merit-Order vorschalten, Rückfall auf die Kaskade |
| `components/einspeisesteuerung/*` | Neue Tabellenspalten und Tooltip-Zeilen |
| `models/steuerentscheid.model.ts` | Sieben neue Felder |

### Ausdrücklich **nicht** geändert

* **`SteuerRegelService`** — die Kaskade bleibt Wort für Wort, sie ist der Rückfall (FR-4). Wer
  hier etwas anpasst, ändert das Verhalten in genau den Fällen, in denen keine Prognose vorliegt.
* **`rechneNach`, `getEntscheideSimuliert`, `POST /simulation`** — die Rückrechnung bekommt die
  Merit-Order **nicht** (FR-8 zurückgenommen). Grund: `zev.einstrahlungsprognose` hält je Intervall
  nur die **zuletzt** geholte Fassung, und der stündliche Abruf überschreibt mit `forecast_days=2`
  auch bereits vergangene Intervalle. Eine Rückrechnung damit kennte das Wetter, das inzwischen
  eingetreten ist, und überschätzte den Nutzen des Verfahrens systematisch — als Kalibrierwerkzeug
  wäre sie nicht bloss ungenau, sondern irreführend.

---

## Phasen

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. DB-Migration Entscheid | `V169`: sieben Spalten (alle **nullable**), `ck_steuerentscheid_regel` um `LADEPLAN` erweitert. Vorher mit `pg_get_constraintdef` über den `zev-db`-MCP prüfen. Jede Spalte mit `COMMENT ON COLUMN`. |
| [x] | 2. Entity, DTO, Enums | `Steuerverfahren` neu; `Steuerregel.LADEPLAN`; sieben Felder an Entity und DTO. |
| [x] | 3. Upsert erweitern | 20 → 27 Parameter. **Danach `SteuerentscheidUpsertQueryTest` laufen lassen** — er zählt Spalten gegen VALUES und prüft die DO-UPDATE-Liste auf Vollständigkeit. |
| [x] | 4. Lastprofil | Query im `MesswerteRepository` (`CONSUMER`, gleiche Wochentage innerhalb `historieTage`), Median je Tageszeit im `ProduktionsprognoseService`. Zeitversatz beachten. |
| [x] | 5. Erwarteter Überschuss | `max(0, prognose − lastprofil)` je Intervall, am `PrognosepunktDTO` ergänzt. |
| [x] | 6. `LadeplanService` | Merit-Order: sortieren, auffüllen, Rang und benötigte Anzahl zurückgeben. Aufgefüllt wird bis `kapazitaetFrei / 0.95` (Ladewirkungsgrad, FR-1). Reine Rechnung ohne Repository-Zugriff — damit einzeln prüfbar. |
| [x] | 7. Einhängen mit Rückfall | `SteuerungService.werteAus`: Voraussetzungen an **einer** Stelle prüfen → Merit-Order oder Kaskade; `verfahren` setzen. |
| [x] | 8. Übersetzungen | `V170`: `STEUERUNG_LADEPLAN_GESPERRT`, `STEUERUNG_VERFAHREN`, `MERIT_ORDER`, `REGEL`, `RANG`, `RANG_BENOETIGT`, `KAPAZITAET_FREI`, `OHNE_PROGNOSE`, dazu `LADEPLAN` als Regelname. Mit `ON CONFLICT (key) DO NOTHING`, deutsche Texte mit Umlauten. |
| [x] | 9. Frontend | **Drittes Zustandsband** direkt unter dem der Batterieladung, gleiche Farbe, gestrichelt und blasser — nur wenn Planergebnisse vorliegen. Dazu **eine** Tooltip-Zeile mit Zustand und Rang, Abweichungen hervorgehoben. |
| [x] | 10. Tests | Unit für `LadeplanService` und das Lastprofil, IT für den erweiterten Upsert. |

---

## Wo es schiefgehen kann

Vier Stellen, an denen ein Fehler **nicht** auffiele. Jede Art hat in diesem Feature schon einmal
zugeschlagen:

**1. Der Upsert (Phase 3).** Beim letzten Erweitern um zwei Spalten fehlten die VALUES-Platzhalter;
der Fehler zeigte sich erst im Job auf der Anlage (`SQLGrammarException: INSERT has more target
columns than expressions`). Sieben Spalten auf einmal ist dieselbe Änderung, nur grösser.
`SteuerentscheidUpsertQueryTest` fängt es — **wenn er nach der Änderung läuft.**

**2. Der CHECK-Constraint (Phase 1).** `regel` ist `NOT NULL` und zählt die erlaubten Werte auf.
Ohne `LADEPLAN` in der Liste scheitert der **erste** Merit-Order-Entscheid beim INSERT — im Job,
also ohne Zuschauer. Genau dafür steht die Notiz in `V160`.

**3. Der Zeitversatz (Phase 4).** `messwerte.zeit` trägt das Intervall**ende**,
`einstrahlungsprognose.zeit` den **Beginn**. Das Lastprofil hat dieselbe Falle wie der Faktor. Ein
versetztes Profil verschiebt den erwarteten Überschuss um eine Viertelstunde, und die Kurve sähe
weiterhin richtig aus.

**4. Die Voraussetzungsprüfung (Phase 7).** Fehlt eine Prüfung, rechnet die Merit-Order mit einer
Lücke statt zurückzufallen. Ein zu tiefer erwarteter Überschuss lässt sie dauerhaft „die Restsonne
reicht nicht" schliessen und grundsätzlich laden — **das Verfahren wirkte wie abgeschaltet, ohne
dass ein Fehler sichtbar wäre.** Deshalb gehört die Prüfung an eine Stelle, nicht verteilt.

---

## Validierungen

### Backend

| Regel | Ort | Verhalten bei Verstoss |
|---|---|---|
| Breiten-/Längengrad, Azimut, Neigung vollständig | `SteuerKonfigurationDTO.standortVollstaendig()` (vorhanden) | Rückfall |
| Batteriekapazität vorhanden **und > 0** | Phase 7 | Rückfall — Kapazität 0 ergäbe dauerhaft einen leeren Plan |
| `SPEICHER`-Einheit erfasst | Phase 7 | Rückfall |
| Ladezustand (SOC) bekannt | Phase 7 | Rückfall |
| Umrechnungsfaktor vorhanden (≥ 150 Intervalle mit `gti > 0`) | `ProduktionsprognoseService` (vorhanden) | Rückfall |
| Lastprofil vorhanden (`historieTage ≥ 7`) | Phase 4 | Rückfall |
| Einstrahlungsprognose für den Resttag vorhanden | Phase 7 | Rückfall |
| Preise für den Resttag vorhanden | Phase 7 | Rückfall — ohne sie gibt es keine Reihenfolge, und eine willkürliche wäre schlechter als das Regelwerk |

> **Jeder Rückfall setzt `verfahren = REGEL`.** Ohne diese Angabe trüge die Spalte `regel` zwei
> verschiedene Bedeutungen, und das Protokoll wäre später nicht mehr lesbar.

### Frontend

Keine neuen Eingaben — die Konfigurationsfelder aus FR-6 sind umgesetzt und validiert. Die neuen
Spalten sind reine Anzeige.

---

## Offene Punkte / Annahmen

**Aus Abschnitt 8 der Spec übernommen — keine davon blockiert die Umsetzung:**

* **28 Tage** für Lastprofil und Faktor, ein gemeinsamer Wert. Getrennte Werte wären denkbar,
  sobald die Daten etwas anderes nahelegen.
* **Nur Rang und benötigte Anzahl** werden gespeichert, nicht der ganze Plan. „Rang 34, gebraucht
  werden 12" erklärt den Entscheid vollständig; 96 Werte je Intervall wären ein Vielfaches an Daten
  für dieselbe Aussage.
* **Die Regelkaskade bleibt** als Rückfall. Ob sie nach einer Erprobungszeit entfällt, ist nicht
  entschieden — und muss es für diesen Plan nicht sein.

**Beim Planen aufgekommen:**

* **Der Ladewirkungsgrad steht als Konstante 0.95** (FR-1), gemessen am 27.09.2026: aus dem
  Energiezähler gerechnet 43 kWh gegen 40.8 kWh konfigurierte Kapazität. Er gehört in
  `LadeplanService`, nicht in die Konfiguration — ein Feld dafür wäre eine Frage, die niemand ohne
  genau diese Rechnung beantworten kann. Ändert sich die Hardware, ist die Rechnung mit einer
  Abfrage zu wiederholen: `speicher_ladung` gegen `ΔSOC × kapazitaet` über einen Ladevorgang.

* **Gleiche Preise in der Merit-Order.** Die Preiszeitreihe ist viertelstündlich, Gleichstände sind
  trotzdem möglich. Die Sortierung braucht als zweites Kriterium die Zeit, sonst wechselt der Rang
  zwischen zwei Läufen und der Entscheid flattert, ohne dass sich etwas geändert hätte.
* **Der Upsert hat nach Phase 3 27 Parameter.** Unhandlich, aber ein Umbau auf ein Parameter-Objekt
  geht bei einem nativen `@Query` nicht ohne Weiteres. **Bewusst hingenommen**, abgesichert durch
  `SteuerentscheidUpsertQueryTest`.
* **`PREIS_NEGATIV` ändert sein Verhalten** (FR-1): Sie bricht die Auswertung nicht mehr ab,
  sondern sperrt nur noch die Einspeisung; über die Ladung entscheidet die Merit-Order. Das ist
  eine Verhaltensänderung, keine blosse Umstellung — bei der Abnahme gezielt anzuschauen.
* **Der URL-Fehler war laut, nicht still.** Am 26.09.2026 antwortete Open-Meteo auf das doppelt
  kodierte `%252F` mit `400 Bad Request: {"error":true,"reason":"Invalid timezone"}`; der Abruf
  erzeugte eine Systemmeldung und schrieb nichts. **Es sind dabei keine falschen Daten entstanden**
  — die Zeilen in `zev.einstrahlungsprognose` stammen aus erfolgreichen Läufen und stehen in
  Ortszeit. Eine Bereinigung ist nicht nötig.

  Auf Hene lässt sich das so kontrollieren:

  ```sql
  SELECT to_char(zuletzt_aufgetreten, 'YYYY-MM-DD HH24:MI') AS zuletzt, zaehler, parameter
  FROM zev.systemmeldung WHERE meldung_key = 'LADEPLANUNG_PROGNOSE_FEHLER';
  ```

  > **`TIMESTAMP` immer mit `to_char` abfragen.** Der `zev-db`-MCP hängt an zonenlose Zeitstempel
  > ein `Z` und rechnet sie dabei um — aus 07:45 wird `05:45Z`. Das hat hier schon einmal zu einer
  > Fehldiagnose geführt: Die Verschiebung steckte in der Ausgabe, nicht in den Daten.

* **Bis die erwartete Erzeugung im Diagramm erscheint, vergehen vier Tage.** Der Faktor braucht 150
  Intervalle mit `gti > 0` aus den Tagen **vor** dem angezeigten Tag; Ende September sind rund 46
  Intervalle pro Tag hell. Vorher bleibt die Kurve leer — richtig so, aber im Diagramm nicht von
  „keine Prognosedaten" zu unterscheiden.
