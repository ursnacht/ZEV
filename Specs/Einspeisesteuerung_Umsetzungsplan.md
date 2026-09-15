# Einspeisesteuerung — Umsetzungsplan

## Zusammenfassung

Eine Steuerung entscheidet viertelstündlich über **Batterieladung** und **Einspeisung** auf Basis
der dynamischen Einspeisepreise, schreibt jeden Entscheid samt Eingangsgrössen fort und macht ihn
als Tagesdiagramm mit Zustandsbändern sichtbar. Über die vorhandene Preishistorie lässt sich die
Regel mit verändertem Schwellwert **nachrechnen**, um ihn empirisch zu bestimmen.

**Sie schaltet nichts.** Es entsteht kein Schreibpfad zur Anlage; MQTT bleibt lesend. Der
Trockenlauf beantwortet zuerst die Frage, ob die Regel überhaupt etwas zu entscheiden hat.

## Betroffene Komponenten

**Neu (Backend)**
| Datei | Zweck |
|---|---|
| `db/migration/V145__Create_Steuerentscheid.sql` | Tabelle `zev.steuerentscheid` |
| `db/migration/V146__Add_Einspeisesteuerung_Translations.sql` | 20 Schlüssel |
| `entity/Steuerentscheid.java` | Entity mit `org_id`, `@Filter(orgFilter)` |
| `entity/Steuerzustand.java` | Enum `FREI` / `GESPERRT` |
| `entity/Steuerregel.java` | Enum der fünf Regeln |
| `repository/SteuerentscheidRepository.java` | Upsert + Tagesabfrage |
| `service/SteuerRegelService.java` | **die Regel als reine Funktion** (s. u.) |
| `service/SteuerungService.java` | Persistenz, Tagesabfrage, Simulation |
| `service/SteuerungJob.java` | `@Scheduled`, je Organisation |
| `controller/SteuerungController.java` | zwei Endpunkte |
| `dto/SteuerentscheidDTO.java`, `dto/SimulationDTO.java`, `dto/SteuerKonfigurationDTO.java` | Transport |

**Geändert (Backend)**
| Datei | Änderung |
|---|---|
| `entity/FeatureFlag.java` | `EINSPEISESTEUERUNG(false, "FEATURE_FLAG_EINSPEISESTEUERUNG")` |
| `dto/RechnungKonfigurationDTO.java` | **Feld `steuerung`** — siehe Kasten |
| `service/EinstellungenService.java` | **keine Änderung** — Jackson serialisiert das neue Feld des DTO automatisch mit |
| `resources/application.yml` | `einspeisesteuerung.job.cron: "0 1,16,31,46 * * * *"` |
| `Specs/Berechtigungen.md` | `SteuerungController` in der Endpunkt-Matrix |

**Neu / geändert (Frontend)**
| Datei | Änderung |
|---|---|
| `models/einspeisesteuerung.model.ts` | Entscheid, Simulation, Zustände, Regeln |
| `services/einspeisesteuerung.service.ts` | zwei Endpunkte |
| `components/einspeisesteuerung/` | Seite mit Diagramm und Protokolltabelle |
| `app.routes.ts` | Route mit `AuthGuard` + `FeatureFlagGuard` |
| `components/navigation/navigation.component.html` | Menüeintrag hinter Flag **und** Permission |
| `components/einstellungen/` | Abschnitt mit den drei Werten |

> ### Der kritische Punkt: `steuerung` MUSS ins DTO
>
> `EinstellungenService.toJson()` schreibt mit `objectMapper.writeValueAsString(dto)` die
> **gesamte** `konfiguration`-Spalte neu — aus einem DTO, das nur kennt, was es als Feld hat. Ein
> Block, den `RechnungKonfigurationDTO` nicht kennt, überlebt das erste Speichern der
> Rechnungsdaten **nicht**: Die Werte wären weg, der Job liefe still mit den Vorgaben weiter, und
> im Protokoll stünde ein plausibel aussehender Schwellwert von `0.05`.
>
> Das abbildende DTO ist `RechnungKonfigurationDTO` (dort steht bereits `verteilmodus`), **nicht**
> `EinstellungenDTO` — jenes trägt nur `id` und `rechnung`.

> ### Die Regel gehört in eine eigene, reine Klasse
>
> `SteuerRegelService` nimmt Eingangsgrössen (Preis, Tiefstpreis des Resttages, Produktion,
> Verbrauch, Schwellen) und liefert einen Entscheid — **ohne Repository, ohne Datenbank**. Grund:
> Job **und** Simulation verwenden dieselbe Regel; zwei Rechenwege wären zwei Wahrheiten. Dasselbe
> Muster wie `NkBerechnungService`, der genau deshalb ohne Datenbank prüfbar ist und die
> Kalenderarithmetik in Unit-Tests hält.

> ### Keine neue Repository-Abfrage nötig
>
> `MesswerteRepository.sumBilanzKomponentenPerZeitBetween(von, bis)` liefert je Zeitpunkt
> `zeit, |Produktion|, Verbrauch, Bezug, |Rücklieferung|` — gruppiert, sortiert und **mit `ABS()`
> für die Producer**. Damit ist das Vorzeichenproblem aus FR-2 gelöst, *sofern man diese Methode
> nimmt* statt selbst zu summieren. Sie deckt beide Fälle ab: den Job (ein Intervall) und die
> Simulation (ganzer Zeitraum in **einer** Abfrage, NFR-1).
>
> Für die Preise genügt `PreiszeitreiheRepository.findByZeitraum(von, bis)` — der Name wich von der Annahme im Plan ab und fiel erst beim Kompilieren auf.

## Phasen

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. DB-Migration | `zev.steuerentscheid` nach FR-3: `org_id` **Pflicht**, `UNIQUE (org_id, zeit_von)`, `zeit_von`/`preis`/`preis_tief_rest`/`produktion`/`verbrauch`/`ueberschuss`/`regel`/`batterieladung`/`einspeisung`/`schwellwert`/`speicherwert`/`erstellt_am`. Spalten-Kommentare. **Nächste freie Nummer prüfen** — `Specs/Batteriespeicher.md` braucht ebenfalls eine |
| [x] | 2. Entity & Repository | `Steuerentscheid` mit `@Filter(orgFilter)`, Enums `Steuerzustand`/`Steuerregel`; Repository mit nativem Upsert auf `(org_id, zeit_von)` (Muster `DebitorRepository.upsert`) und Tagesabfrage |
| [x] | 3. Konfiguration | `SteuerKonfigurationDTO` (`schwellwert`, `speicherwert`, `batteriekapazitaet`) **als Feld `steuerung` in `RechnungKonfigurationDTO`** — siehe Kasten. Vorgaben `0.05` / `0.31` / leer, wenn der Block fehlt |
| [x] | 4. Feature-Flag | `FeatureFlag.EINSPEISESTEUERUNG`, Vorgabe `false` |
| [x] | 5. Regel | `SteuerRegelService`: die fünf Regeln in fester Reihenfolge, **reine Funktion**. Überschuss aus `|Produktion| − Verbrauch`, mindestens 0. Tiefstpreis = Minimum der Preise **nach** dem Intervall, gleicher Ortstag |
| [x] | 6. Job | `SteuerungJob` mit `@Scheduled(cron = "${einspeisesteuerung.job.cron:0 1,16,31,46 * * * *}")`, `getOrgIdsMitAktivemFlag(...)`, je Organisation `enableOrgFilter(orgId)` — **die parametrisierte Variante**, im Job gibt es keinen Sicherheitskontext. Wertet das zuletzt abgeschlossene Intervall aus, schreibt per Upsert. Fängt jede Ausnahme (Muster `PreiszeitreiheDownloadJob`) |
| [x] | 7. Controller | `GET /api/einspeisesteuerung/entscheide?datum=`, `POST /api/einspeisesteuerung/simulation`; `@PreAuthorize("hasAuthority('tarife:manage')")` auf Klassenebene, Feature-Flag-Prüfung in **jeder** Methode → `403`. Fehlerrümpfe als Klartext |
| [x] | 8. Nachrechnen | Simulation über `SteuerRegelService` mit abweichenden Schwellen; liest Preise und Messwerte, **schreibt nichts**. Zählt je Regel **nur Intervalle mit Überschuss** (FR-6). Zeitraum auf 366 Tage begrenzt |
| [x] | 9. Einstellungen | Abschnitt „Einspeisesteuerung" in der bestehenden Maske, sichtbar nur bei aktivem Flag; drei Felder mit Validierung (negativ erlaubt bei beiden Preisen) |
| [x] | 10. Frontend-Grundlage | `einspeisesteuerung.model.ts` und `.service.ts` nach den Vorlagen (`tarif.model.ts`, `tarif.service.ts`) |
| [x] | 11. Frontend-Seite | Komponente nach dem Muster von `preiszeitreihe-chart`: ECharts **dynamisch** über `ladeECharts()`, Steuerzeile (`zev-date-range-row`), Diagramm in `zev-panel--chart`, Protokolltabelle als `zev-table`. **Zustandsbänder als Balkenserie** — `BarChart` ist bereits registriert, `markArea` bräuchte ein zusätzliches Modul im gemeinsamen Loader |
| [x] | 12. Routing & Navigation | Route `/einspeisesteuerung` mit `AuthGuard` + `FeatureFlagGuard`; Menüeintrag mit `*appFeature` **und** `*appPermission` |
| [x] | 13. Übersetzungen | Migration mit den 20 Schlüsseln aus FR-9, deutsch **mit Umlauten**, `ON CONFLICT (key) DO NOTHING` |
| [ ] | 14. Tests | siehe unten — **bewusst offen**: `/2_umsetzung` erstellt keine Tests, das übernehmen `/3_backend-tests`, `/4_frontend-unit-tests` und `/5_e2e-tests` |

### Phase 14 im Einzelnen

| Ebene | Inhalt |
|---|---|
| `SteuerRegelServiceTest` | **Der Schwerpunkt.** Jede der fünf Regeln einzeln, die Reihenfolge (erste zutreffende gewinnt), und der Vorzeichenfall: Producer `total = −10`, Consumer `total = 4` → Überschuss **6**, nicht 0 und nicht −14 |
| `SteuerungServiceTest` | Upsert-Idempotenz, Tagesabfrage über UTC-Grenzen, Simulation verändert nichts, Zählung nur mit Überschuss |
| `EinstellungenServiceTest` | **Pflichttest:** Rechnungsdaten speichern, danach prüfen, dass die Steuerungswerte noch dastehen |
| `SteuerungControllerTest` | 403 ohne Flag, 403 ohne Permission, 400 bei `von > bis` und > 366 Tagen, leere Liste statt 404 |
| Frontend-Unit | Zustandsbänder aus Entscheiden, Datumswechsel, leerer Tag |
| E2E | Seite erreichbar nur mit Flag; ein Tag mit Entscheiden zeigt Diagramm und Tabelle |

## Validierungen

**Backend**
| Regel | Ort | Verhalten |
|---|---|---|
| Feature-Flag aktiv | jede öffentliche Methode von `SteuerungService` | `FeatureDisabledException` → `403` |
| `tarife:manage` | `SteuerungController` (Klassenebene) | `403` |
| `von` ≤ `bis` | Simulation | `400`, Klartext |
| Zeitraum ≤ 366 Tage | Simulation | `400`, Klartext |
| `org_id` serverseitig | `SteuerungService` | nie aus dem Request |
| Schwellwert/Speicherwert dürfen **negativ** sein | Einstellungen | kein Vorzeichen-Wächter |
| `batteriekapazitaet` ≥ 0 oder leer | Einstellungen | `400` bei negativ |

**Frontend**
| Regel | Ort |
|---|---|
| Datum nicht leer | Tagesansicht |
| Schwellwert ist eine Zahl | Nachrechnen-Feld |
| Seite nur bei aktivem Flag erreichbar | `FeatureFlagGuard` |

## Offene Punkte / Annahmen

**Aus §8 der Spec übernommen (beantwortet):**
* Massstab `speicherwert` = vermiedener Netzbezug → Vorgabe `0.31`. **Folge:** Regel 3 löst
  praktisch nie aus (höchster gemessener Preis `0.238`) und ist ein Wächter, kein Normalfall.
* Schwellwert wird **empirisch** über Phase 8 ermittelt; `0.05` ist ein Startwert, keine Aussage.
* Batterie: Pylontech 20 kWh, Wechselrichter MHT-30K-100. Die Kapazität ist konfigurierbar, wird
  aber von **keiner Regel** ausgewertet — sie steht dort für eine spätere Ertragsrechnung.
* Berechtigung `tarife:manage` genügt.
* Konfiguration je Mandant in `organisation.konfiguration`.
* Job eine Minute nach Intervallende.

**Weiterhin offen (blockieren nicht):**
* **Ladezustand des Wechselrichters** — wird geklärt. Ohne ihn bleibt offen, ob ein „Ladung
  gesperrt" gewirkt hat oder ins Leere lief, weil die Batterie ohnehin voll war.
* **Ist die Kapazität überhaupt knapp?** Bleibt der Tagesüberschuss unter 20 kWh, ist nie eine
  Entscheidung nötig und Regel 4 ohne Wirkung. **Das beantwortet der Trockenlauf in den ersten
  Tagen** — und es ist der Grund, ihn vor allem anderen zu bauen.
* **`SPEICHER`-Einheit** (`Specs/Batteriespeicher.md`): keine Voraussetzung für die Steuerung, aber
  für die Beurteilung ihrer Wirkung. Sinnvollerweise davor oder parallel.

**Annahmen dieses Plans:**
* Die Regel läuft **unabhängig vom Verteilmodus** — sie liest Produktion und Verbrauch direkt aus
  den Messwerten, nicht aus dem Verteilergebnis. `PRODUCER_MESSUNG` und `BILANZ` verhalten sich
  gleich.
* Ein Ortstag hat an den Umstellungstagen 92 bzw. 100 Intervalle; Auswertung und Anzeige rechnen
  durchgehend über UTC und wandeln erst zur Darstellung um.
* Die Protokolltabelle zeigt **alle** Intervalle eines Tages, auch die ohne Überschuss — dort ist
  Vollständigkeit der Zweck. Nur die Zählung in Phase 8 filtert.

## Nachtrag: Zeitpunkt des Jobs und drei Zeitbezüge

Im Betrieb fiel auf, dass die Steuerung „um 15 Minuten nachzuhinken" schien. Die Ursache war
dreifach, und nur der erste Teil war der gemeldete:

1. **Der Job lief um `:01`, die Aggregierung läuft um `:05`.** Er las damit Messwerte, die es noch
   nicht gab. Der Zeitpunkt hängt nicht am Intervallende, sondern an
   `ZaehlerAggregationService.aggregiere()` — jetzt `0 6,21,36,51 * * * *`.

2. **`messwerte.zeit` ist Ortszeit, nicht UTC.** Die Aggregierung rechnet mit
   `LocalDateTime.now()` (Kommentar dort: „Voraussetzung: Backend/Container läuft in der lokalen
   Zone"). `preiszeitreihe.zeit_von` ist dagegen UTC. Belegt an den Daten: `max(messwerte.zeit)`
   war 12:30, während es 12:50 Ortszeit war — in UTC läge dieser Messwert in der Zukunft.

3. **`messwerte.zeit` trägt das Intervall-ENDE**, die Preise den Beginn
   (`upsertMesswert(einheit, ende, total)`).

Zusammen ergab das bis zu zwei Stunden Zonenversatz **plus** eine Viertelstunde: Ein Entscheid
trug den Preis von 11:45 Ortszeit neben der Produktion von 09:30–09:45. **Beide Zahlen sahen
plausibel aus** — das ist das Tückische daran. Aufgefallen ist es nur, weil der Zeitpunkt des Jobs
zur Diskussion stand.

**Behoben:**
* `PreiszeitreiheZeit.nachUtc(...)` neu — die Gegenrichtung zu `nachOrtszeit`, mit Hinweis auf die
  Mehrdeutigkeit an der Zeitumstellung.
* `SteuerungService.messungFuer(zeitVonUtc)` rechnet UTC → Ortszeit **und** Beginn → Ende.
* Die Simulation verschiebt ihre Grenzen ebenso und rechnet die Zeitstempel zurück auf
  „Beginn in UTC", die Form, in der Preise und Entscheide geführt werden.
* `SteuerungJob` bestimmt das Intervall in **Ortszeit** (dort liegen die Viertelstundengrenzen der
  Aggregierung) und speichert in UTC.
* Ein Kasten am Klassenkopf von `SteuerungService` hält die drei Zeitbezüge fest.

**Die bereits geschriebenen Entscheide sind falsch** und sollten gelöscht werden — sie tragen
Messwerte aus dem falschen Zeitraum:
`DELETE FROM zev.steuerentscheid;`

### Nachtrag 2 — Logging (13.09.2026)

Verifiziert am ersten Lauf nach der Korrektur (15:06, eine Minute nach der Aggregierung um 15:05):
Der Entscheid `zeit_von = 12:45 UTC` trug den Preis von 12:45 UTC (0.068), den Tiefstpreis des
Resttages (0.089) und die Messwerte von 15:00 Ortszeit (14.302 / 21.221) — alles dasselbe Intervall
14:45–15:00 Ortszeit, gegen die Quelltabellen geprüft.

Damit ein solcher Nachweis künftig ohne SQL möglich ist, protokolliert die Steuerung nach dem
Muster von `ZaehlerAggregationService.aggregiere()` (Start / je Mandant / Abschluss mit Anzahl, alles
auf `INFO`). Der bisherige `log.debug` im Erfolgsfall zeigte im Betrieb nicht einmal, **ob** der Job
lief. Vollständige Tabelle: `Specs/Einspeisesteuerung.md`, NFR-4.

Zwei Punkte, die dabei aufgefallen sind und nun `WARN` erzeugen statt stumm zu bleiben:
* **Fehlende Messwerte** ergaben bisher lautlos einen Entscheid mit `0` — der sieht aus wie Nacht.
* **Fehlender Preis** lässt die Regeln 1 und 3 wirkungslos; das war nur an einer leeren Spalte
  erkennbar.

> **Zur lokalen Umgebung:** Die synthetischen Daten erzeugen nie einen Überschuss
> (`Produktion − Verbrauch` ist durchgehend negativ), also greift immer Regel 2. Die Regeln 1, 3, 4
> und 5 lassen sich hier **nicht durch Zusehen** prüfen — sie gehören in Unit-Tests auf
> `SteuerRegelService` (Phase 14), der als reine Funktion genau dafür gebaut ist.

### Nachtrag 3 — `zeit_von` auf Ortszeit (13.09.2026)

**Auslöser:** Die Frage, warum `steuerentscheid.zeit_von` UTC führt, wo `zaehler_rohdaten.zeit` und
`messwerte.zeit` doch Ortszeit sind.

Die ursprüngliche Begründung (V145: „lokale Zeit wäre an der Zeitumstellung nicht eindeutig") stimmt
für sich genommen — sie führte aber dazu, dass im selben System **drei** Zeitkonventionen
nebeneinander galten. Genau daran ist die erste Fassung der Steuerung gescheitert (Nachtrag 1).

**Nachgeprüft, bevor entschieden wurde:**

| Prüfung | Ergebnis |
|---|---|
| Unique-Key `zaehler_rohdaten` | `(einheit_id, zeit)` — in **Ortszeit**, hat das Problem also bereits |
| `messwerte` am 26.10.2025 (Umstellungstag) | **96** statt 100 Messwerte je Einheit — die doppelte Stunde fehlt schon heute |
| `upsertMesswert` | `findByEinheitAndZeit` → gefunden → überschreiben; der zweite Durchgang verdrängt den ersten |

Das bestehende Modell behandelt die Zeitumstellung also nicht. Die UTC-Speicherung vermied den
Verlust für **eine** Tabelle und erkaufte das mit einer Sonderkonvention — kein guter Tausch.

**Umgestellt (V147):**
* Migration rechnet vorhandene Werte um: `(zeit_von AT TIME ZONE 'UTC') AT TIME ZONE 'Europe/Zurich'`.
* `messungFuer` braucht **keine Zonenrechnung** mehr, nur noch Beginn → Ende.
* `getEntscheide` nimmt die Tagesgrenzen direkt (`atStartOfDay()`), ohne `tagesbeginnUtc`/`tagesendeUtc`.
* `zuDto` reicht `zeitVon` unverändert durch.
* `SteuerungJob` speichert, was er ohnehin in Ortszeit gerechnet hat — die Rückrechnung nach UTC entfällt.
* In der Rückrechnung werden die Preise **beim Bündeln** auf Ortszeit umgeschlüsselt; danach ist der
  Abgleich ein schlichter Kartenzugriff statt einer Kette von Umrechnungen je Zeile.

**Was bleibt:** Die Preiszeitreihe ist die einzige Quelle in UTC und wird an drei Stellen
umgerechnet (`preisFuer`, `tiefstpreisRestDesTages`, `preiseJeOrtstag`) — vorher verteilten sich
Umrechnungen über Messwerte, Anzeige, Tagesgrenzen **und** Preise.

**Was das kostet:** Am 25.10.2026 (Umstellung auf Winterzeit) überschreiben sich die vier
Entscheide der doppelten Stunde 02:00–03:00. Nachts, ohne Überschuss, ohne Schaltwirkung — und
`messwerte` verliert dieselben vier Intervalle ohnehin. In Spec (§5), Spaltenkommentar und
Entity-Javadoc festgehalten, damit es nicht der nächste stille Fehler wird.

**Nötig:** Rebuild, damit V147 läuft. Die bestehenden Entscheide werden dabei mit umgerechnet — sie
müssen **nicht** gelöscht werden.

### Nachtrag 4 — Zustandsbänder als `markArea` (14.09.2026)

**Auslöser:** Die Beobachtung, dass `batterieladung` und `einspeisung` für dieselbe Zeitspanne um
eine Viertelstunde versetzt gezeichnet wurden. **Nein, das war nicht gewollt** — es waren zwei
Fehler derselben Wurzel: Balken auf einer Zeitachse.

| Fehler | Ursache | Sichtbar als |
|---|---|---|
| Versatz **zwischen** den Bändern | ECharts ordnet mehrere Balkenserien **nebeneinander** an (`barGap` wirkt), nicht übereinander | Batterieladung 15 Min vor Einspeisung |
| Versatz **gegenüber dem Intervall** | ECharts **zentriert** einen Balken auf seinen Datenpunkt | Band 7½ Min zu früh, während die Preislinie (`step: 'end'`) korrekt lag |

Der zweite Fehler wäre ohne den ersten nicht aufgefallen — er lag unterhalb der Auflösung, in der
man ein Diagramm liest.

**Umgestellt auf `markArea`:** Rechtecke von `zeit` bis `zeit + 15min`, zusammenhängende gesperrte
Intervalle zu einem Block verschmolzen (sonst Fugen an den Stossstellen), jedes Band auf fester
Ebene — unabhängig davon, ob das andere gesetzt ist.

**Der ursprüngliche Einwand gegen `markArea` hat sich nicht bestätigt.** Er lautete: ein
zusätzliches Modul vergrössert den Chunk für *alle* Diagramme. Gemessen vor und nach der Änderung:

```
chunk-Q5OOXIWC.js | components | 642.59 kB   ← identisch
```

`echarts/components` wird als **ganzer** Chunk nachgeladen; `core.use()` entscheidet nur, was davon
registriert wird. `MarkAreaComponent` kostet null Bytes.

**Zwei Fallen, die dabei sichtbar wurden:**
* `markArea` spannt die Skala **nicht** auf (anders als eine Datenserie) — ohne festes `min` auf
  der Mengen-Achse endete sie bei 0 und die Bänder wären unsichtbar geblieben.
* Ein nicht registriertes Modul zeichnet **stumm nichts**. `echarts-loader.spec.ts` prüft deshalb
  die Existenz jedes registrierten Exports; `MarkAreaComponent` ist dort ergänzt.

**Geprüft:** 1654 Frontend-Tests grün, Build fehlerfrei.

### Nachtrag 5 — Nachgerechneter Tag in der Ansicht (14.09.2026, FR-6a)

**Auslöser:** Die Rückrechnung lieferte nur Kennzahlen — *wie oft* ein Schwellwert gesperrt hätte.
Diagramm und Tabelle blieben derweil auf der Aufzeichnung stehen und widersprachen der Auswertung
darunter. Die eigentliche Frage an einen Schwellwert ist aber *wann*: Trifft er das Preistal oder
sperrt er morgens um 8?

**Ein Rechenweg, zwei Verwertungen.** `simuliere()` wurde auf eine gemeinsame Methode
`rechneNach(von, bis, schwellwert, speicherwert, Consumer<Nachgerechnet>)` zurückgeführt:
* `simuliere()` **zählt** darüber (nur Intervalle mit Überschuss),
* `getEntscheideSimuliert()` **sammelt** DTOs (alle Intervalle — die Ansicht zeichnet einen Verlauf).

Bewusst ein `Consumer` statt einer Liste: Über 366 Tage sind das 35'000 Intervalle, die niemand
zwischenlagern muss. Und bewusst **eine** Methode: Widersprächen sich Tagesansicht und Kennzahlen,
wäre die ganze Ansicht wertlos.

**Warum ein eigener Endpunkt** (`GET /entscheide/simuliert`) und nicht ein Parameter an
`/entscheide`: Beide liefern dieselbe Form, meinen aber Verschiedenes — Protokoll gegen Hypothese.
Und nicht als Feld im `SimulationDTO`: Beim Blättern müsste sonst die Rückrechnung über 366 Tage
erneut laufen, um einen einzelnen Tag zu zeigen.

**Im Frontend** merkt sich `simuliertMitSchwellwert` den Wert statt eines blossen Schalters — er
steht im Hinweis über dem Diagramm und bleibt beim Blättern erhalten. `ladeTag()` wählt danach die
Quelle; „Aufzeichnung zeigen" setzt zurück, lässt die Kennzahlen aber stehen (sie gelten für die
ganze Historie, nicht für den Tag).

**Design System:** Keine neuen Klassen — `zev-message--warning zev-message--statisch` und
`zev-button--secondary` waren vorhanden. Das zuerst gewählte Icon `rotate-ccw` gibt es nicht; die
Registry (`components/icon/icons.ts`) führt 38 Namen, verwendet wird `refresh-cw`. **Zum zweiten Mal
in diesem Feature ein geratener Icon-Name** — die Registry ist vor dem Schreiben zu lesen.

**Geprüft:** 1300 Backend-Tests grün, Frontend baut. V148 bringt zwei Übersetzungs-Keys.

### Nachtrag 6 — Regel 4 wartete auf sich selbst (14.09.2026)

**Der gravierendste Fehler des Features**, gefunden an echten Daten von Hene:

| Zeit | Preis | Tiefstpreis Rest | Überschuss | Entscheid |
|---|---|---|---|---|
| 12:45 | 0.175 | 0.161 | 4.259 | Gesperrt — richtig, es kommt noch Günstigeres |
| 13:00 | **0.161** | **0.161** | 4.299 | Gesperrt — **falsch** |
| … | 0.161 | 0.161 | … | Gesperrt |
| 14:30 | **0.161** | **0.161** | 3.567 | Gesperrt |
| 14:45 | 0.161 | 0.170 | 3.668 | Laden |

Ab 13:00 stand der aktuelle Preis gleich dem Tiefstpreis des Resttages — es gab kein Tal mehr, auf
das sich warten liess. Die Steuerung sperrte trotzdem: **27.9 kWh Überschuss**, mehr als die
Batterie fasst, gingen im Preistal ins Netz. Geladen wurde ab 14:45, als der Preis gestiegen war.

**Der Fehler stand schon in der Spec, nicht erst im Code.** Der Fliesstext war immer richtig
(„statt sie jetzt mit **teurerem** Strom zu füllen"), die Regeltabelle prüfte aber nur
`Tiefstpreis Rest < Schwellwert`. Tabelle und Begründung widersprachen sich; der Code folgte der
Tabelle. Weder `/0_anforderungen-check` (zwei Runden) noch die Umsetzung haben das bemerkt.

**Korrigiert:** Regel 4 verlangt jetzt beide Bedingungen — `Tiefstpreis Rest < Schwellwert`
**und** `Tiefstpreis Rest < Preis jetzt`. Ohne aktuellen Preis wird nicht gesperrt.

**Warum es so lange unsichtbar blieb:**
* Auf der Entwicklungsumgebung entsteht **nie** ein Überschuss — dort greift immer Regel 2, und die
  Regeln 1, 3, 4, 5 laufen nie. Kein lokaler Test hätte es gezeigt.
* Die Kennzahlen der Rückrechnung zeigen es nicht: Dort stünde „7× WARTEN_AUF_TAL", und das sieht
  richtig aus.
* Sichtbar wurde es erst durch die **nachgerechnete Tagesansicht** (Nachtrag 5, FR-6a) — an der
  Tabelle mit Preis und Tiefstpreis nebeneinander.

**`SteuerRegelServiceTest` neu** (15 Tests, Phase 14 vorgezogen): Die Regel ist eine reine Funktion
und ohne Datenbank vollständig prüfbar — genau dafür wurde sie von der Datenbeschaffung getrennt.
Enthalten ist der Regressionstest `entscheide_PreisBereitsAufTagestief_LaedtStattZuWarten` sowie die
Grenzfälle „später teurer", „später günstiger aber über Schwellwert" und „ohne Preis".

> Beim Schreiben der Tests fiel ein weiterer Fall auf: Ein Test war noch nach der alten Logik
> gedacht (Preis 0.000, Tiefstpreis 0.100 → erwartet `WARTEN_AUF_TAL`). Richtig ist `LADEN` — dort
> ist *jetzt* das Tal.

**Geprüft:** 1315 Backend-Tests grün. **Rebuild nötig**, damit die Regel greift; bereits
geschriebene Entscheide der betroffenen Intervalle bleiben falsch und werden erst beim nächsten
Lauf für neue Intervalle korrekt.

### Nachtrag 7 — Energiebilanz sichtbar machen (15.09.2026, FR-6b)

**Auslöser:** Der Verdacht, Produktion und Verbrauch seien falsch summiert, möglicherweise im
Zusammenhang mit der Batterieladung.

**Zuerst geprüft, ob die Summierung wirklich falsch ist — sie ist es nicht:**

| Prüfung | Ergebnis |
|---|---|
| Vergleich mit `StatistikService` | **Identische Formel**: `SUM(m.total)` je Einheiten-Typ, `Math.abs()` für Producer. Ein Summierungsfehler wäre auch dort sichtbar. |
| Doppelte Messwerte | 8 Intervalle, **eine** Einheit, Typ `BEZUG`, Mandant Mut13 — weder Produktion noch Verbrauch, nicht Hene. |
| `LADESTATION` als stiller Verbraucher | Scheidet aus: Ladestationen erhalten laut `EinheitTyp` **keine** Messwerte. |

**Der eigentliche Mangel war ein anderer: Die Ansicht zeigte die halbe Bilanz.** Produktion und
Verbrauch standen ohne Gegenprobe da; der Verdacht liess sich weder bestätigen noch entkräften.
`sumBilanzKomponentenPerZeitBetween` liefert Bezug und Rücklieferung längst mit — sie wurden
weggeworfen (`zeile[3]`, `zeile[4]`).

**Umgesetzt:** V149 ergänzt `bezug` und `ruecklieferung` in `steuerentscheid` (nullable — Bestands-
entscheide haben sie nicht, und eine `0` wäre dort eine Falschaussage). Die Tabelle zeigt beide und
daneben `Produktion + Bezug − Verbrauch − Rücklieferung` als **Netto-Batteriefluss**. Damit wird die
Batterie sichtbar, obwohl es keinen Einheiten-Typ `SPEICHER` gibt.

Die Differenz wird **abgeleitet, nicht gespeichert** — ein gespeicherter Ableitungswert kann von
seiner Grundlage abweichen.

> **Die Zahl heisst „Batterie (aus Bilanz)", enthält aber mehr:** jeden Verbraucher, der nicht als
> Einheit erfasst ist. Eine dauerhaft grosse Differenz bei stillstehender Batterie ist genau dieser
> Fall — und damit der Hinweis, dass Einheiten fehlen. Der Hinweistext an der Spalte sagt das.

**Beinahe-Fehler bei den Übersetzungen:** Der Key `RUECKLIEFERUNG` ist bereits vergeben und trägt
„Rücklieferung: Produktion (Total) - C" — ein Label der Statistik samt Spaltenbuchstabe. Wegen
`ON CONFLICT (key) DO NOTHING` wäre der neue Eintrag **stillschweigend verworfen** worden und die
Spalte trüge diesen Text. Vor dem Schreiben der Migration geprüft; die Keys heissen deshalb
`STEUERUNG_BEZUG` und `STEUERUNG_RUECKLIEFERUNG`. Bei V146 war dieselbe Annahme schon einmal in die
andere Richtung falsch.

**Geprüft:** 1315 Backend-Tests grün, Frontend baut. **Rebuild nötig** für V149 und V150; erst
danach füllen sich die neuen Spalten — für zurückliegende Intervalle bleiben sie leer.

### Nachtrag 8 — Produktion erscheint zu tief (15.09.2026)

**Auslöser:** „Die Sonne scheint voll und trotzdem ist die Produktion tiefer als der Verbrauch. Die
App der Anlage belegt dies ebenfalls." Damit gibt es erstmals eine **unabhängige Referenz** — das
ist keine Anzeigefrage mehr.

**Der gesamte Pfad wurde geprüft, Schritt für Schritt:**

| Schritt | Befund |
|---|---|
| MQTT-Payload | Zwei Register (`zaehlerstandBezug`, `zaehlerstandEinspeisung`), absolute Zählerstände |
| `MqttIngestService.upsertRohdaten` | `PRODUCER` erhält **beide** Register unverändert; genullt wird nur bei `BEZUG`/`RUECKLIEFERUNG` |
| `verarbeiteIntervall` | `total = ΔBezug − ΔEinspeisung`; einspeisende Anlage ergibt negatives `total` — wie spezifiziert |
| Aggregation → `messwerte` | Delta aus letztem Stand vor Intervallbeginn und letztem Stand bis Intervallende |
| Summierung | `SUM(total)` je Typ mit `ABS()` für Producer — identisch zur Statistik |

**Ein Rechenfehler liess sich nicht finden.** Aber eine stille Verlustquelle: `nichtNegativ()` setzt
ein negatives Delta auf `0` und protokollierte das **nur als Logzeile**. Anders als bei einer
Datenlücke geht die Energie dabei **dauerhaft verloren** — bei einem Producer sinkt genau dadurch
die ausgewiesene Produktion, ohne dass der Anlage etwas fehlt.

**Umgesetzt:** Der Fall erzeugt jetzt eine Systemmeldung (`MQTT_ZAEHLER_RUECKSPRUNG`, `WARN`, V151)
mit Einheit, Register, Intervall und **verworfener Menge**. Ohne den Betrag liesse sich nicht
abschätzen, ob es um Rundung oder um Kilowattstunden geht.

**Offen — und nur am Objekt zu klären:** Die wahrscheinlichste Erklärung ist nicht die Software,
sondern der **Messpunkt**. Bei einem Hybrid-Wechselrichter mit DC-gekoppelter Batterie (Hene fährt
MHT-30K-100 mit Pylontech) fliesst PV-Energie direkt DC-seitig in den Speicher und passiert den
AC-seitigen Zähler **nie**. Die Anlagen-App zeigt dann die DC-Produktion, unser Zähler die
AC-Abgabe nach Batterieladung — bei voller Sonne und ladender Batterie kann diese unter dem
Hausverbrauch liegen. Das ist mit Software nicht zu beheben, sondern nur durch einen zusätzlichen
Messpunkt oder das Auslesen des Wechselrichters.

Zu unterscheiden sind die beiden Fälle an den **Rohdaten**: Steigt `zaehlerstandEinspeisung` der
PV-Einheit langsamer, als die App an Produktion ausweist, kommt bereits am Zähler zu wenig an.

**Geprüft:** 1315 Backend-Tests grün.

### Nachtrag 9 — Der Überschuss blockiert die Preisregeln nicht mehr (15.09.2026)

**Auslöser:** „So bringt die Einspeisesteuerung nichts, wenn erst Überschuss vorhanden ist, wenn die
Batterie voll geladen ist."

**Die Wirkungskette, die das erklärt:**

1. `messwerte.total` ist ein Saldo — `ΔBezug − ΔEinspeisung` des Messpunkts.
2. Lädt die Batterie über denselben Messpunkt, wird ihre Ladeleistung von der Produktion abgezogen.
3. Die ausgewiesene Produktion sinkt unter den Verbrauch, der Überschuss erscheint als `0`.
4. `KEIN_UEBERSCHUSS` stand an **zweiter** Stelle und blockierte alle Preisregeln.
5. Entschieden wurde erst, wenn die Batterie voll war — und es nichts mehr zu entscheiden gab.

Der Produktionsverlauf vom 14.09. stützt das: bis 11:15 rund `0.19` kWh, ab 11:30 sprunghaft `1.94`
und `4.10`. Das sah nach aufreissendem Hochnebel aus, passt aber genauso zu einer Batterie, die um
11:15 voll war.

**Umgestellt:** `KEIN_UEBERSCHUSS` steht jetzt **nach** den Preisregeln.

| alt | neu |
|---|---|
| 1 `PREIS_NEGATIV` | 1 `PREIS_NEGATIV` |
| 2 `KEIN_UEBERSCHUSS` ← blockierte | 2 `EINSPEISEN_LOHNT` |
| 3 `EINSPEISEN_LOHNT` | 3 `WARTEN_AUF_TAL` |
| 4 `WARTEN_AUF_TAL` | 4 `KEIN_UEBERSCHUSS` ← beschreibt nur noch |
| 5 `LADEN` | 5 `LADEN` |

**Warum das trägt:** Die Frage „laden oder einspeisen?" hängt am **Preis**, nicht an der Menge. Ist
kein Überschuss da, läuft eine Sperre ins Leere — schaden kann sie nicht, denn der Anlagenregler
entscheidet ohnehin, ob tatsächlich geladen wird. Die Zeilen 4 und 5 ergeben denselben Entscheid
(`FREI`/`FREI`) und unterscheiden sich nur in der Begründung.

**Folge für die Rückrechnung:** Jetzt greift **jede** Preisregel auch nachts. Dass die Zählung
ausschliesslich Intervalle mit Überschuss berücksichtigt, wird damit wichtiger, nicht unwichtiger —
die Begründung in FR-6 ist entsprechend nachgezogen.

**Die Enum-Reihenfolge trägt die Fachlichkeit** und wurde mitgezogen (Backend und Frontend-Modell).
Persistenzrelevant ist sie nicht: `@Enumerated(EnumType.STRING)`, und der CHECK-Constraint zählt
Werte auf, keine Positionen.

**Spec durchgängig auf Regelnamen umgestellt.** Die Nummern in Fliesstexten waren nach der
Umstellung teils falsch — dieselbe Falle wie bei Migrationsnummern: Sie veralten stillschweigend.
`grep "Regel [1-5]"` liefert jetzt null Treffer.

**Geprüft:** 1317 Backend-Tests grün (zwei neue Regressionstests: Preisregel greift ohne
Überschuss), Frontend baut.

> **Was damit NICHT gelöst ist:** Die Produktion bleibt eine Netto-Grösse. Die Steuerung entscheidet
> jetzt zwar über den ganzen Tag, aber `ueberschuss` und `energie_verschoben` im Protokoll sind
> weiterhin zu klein, solange die Batterieladung am Zähler des Produzenten gegengerechnet wird.
> Das ist der zweite Weg (Bruttoproduktion beschaffen) und noch offen.
