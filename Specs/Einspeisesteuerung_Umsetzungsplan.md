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

1. Solange die Batterie lädt, gibt der Wechselrichter über den Zähler nur den **Hausbedarf** ab.
2. Die übrige PV-Energie fliesst DC-seitig in den Speicher und passiert den Zähler **nie**.
3. Produktion ≈ Verbrauch, der Überschuss erscheint als `0`.
4. `KEIN_UEBERSCHUSS` stand an **zweiter** Stelle und blockierte alle Preisregeln.
5. Entschieden wurde erst, wenn die Batterie voll war — und es nichts mehr zu entscheiden gab.

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

> **Was damit NICHT gelöst ist:** `ueberschuss` und `energie_verschoben` im Protokoll bleiben zu
> klein, solange die Batterie DC-seitig lädt — die Energie erreicht den Zähler nicht, und keine
> Auswertung kann sie herbeirechnen. Abhilfe schafft nur das Auslesen des Wechselrichters; das
> lieferte zugleich den Ladezustand, der der Regel bis heute fehlt (§8).


### Nachtrag 10 — Die Ursache ist belegt, und sie liegt vor dem Zähler (15.09.2026)

Nachtrag 8 nannte zwei mögliche Ursachen für die zu tiefe Produktion. Die Rohdaten des
Producer-Zählers bei Hene entscheiden zugunsten der zweiten — **meine zuerst vertretene Hypothese
ist widerlegt**.

| Zeit | `produktion_brutto` | `bezug_am_pv_zaehler` |
|---|---|---|
| 08:15–11:15 | 0.17 – 0.27 (konstant) | **0.000** |
| 11:30 | **2.606** | 0.000 |
| 11:45–13:30 | 4.0 – 4.5 | 0.000 |

**Das Bezugsregister des PV-Zählers bleibt bei null.** Die Batterieladung wird also *nicht*
gegengerechnet; `total` entspricht praktisch exakt der gemessenen Einspeisung. Der in Nachtrag 8
vorgeschlagene zweite Weg — `ΔEinspeisung` als Bruttoproduktion durchreichen — ist damit
**gegenstandslos**: Es ist derselbe Wert, den die Auswertung bereits verwendet.

**Was die Zahlen stattdessen zeigen:** drei Stunden Plateau bei rund 0.8 kW — ungefähr die
Hausgrundlast —, dann um 11:30 ein Sprung auf das Zwölffache. Das ist kein Solarverlauf, sondern ein
Hybrid-Wechselrichter, der AC-seitig nur den Hausbedarf abgibt und alles übrige DC-seitig in die
Batterie schiebt. Um 11:30 war sie voll.

**Folgerungen:**
* Die Software rechnet korrekt; der Zähler misst korrekt, was er messen kann. Die Differenz zur
  Anlagen-App ist real und entsteht **vor** dem Zähler.
* Der Sprung um 11:15/11:30, den ich zuerst für aufreissenden Hochnebel gehalten habe, ist die volle
  Batterie — an zwei aufeinanderfolgenden Tagen dasselbe Muster.
* **Nachtrag 9 war die richtige Antwort auf diese Lage.** Morgens ist der Überschuss tatsächlich
  null, nicht wegen eines Messfehlers. Genau dann muss die Steuerung trotzdem entscheiden können.
* Die echte PV-Produktion **und** der Ladezustand sind nur über den Wechselrichter zu bekommen. Das
  bleibt die einzige offene Baustelle dieser Frage (§8).

Code-Kommentare, Enum-Javadoc, Testdoku und FR-2 tragen die widerlegte Begründung nicht mehr.

### Nachtrag 11 — Ladezustand im Diagramm (17.09.2026)

**Auslöser:** Der SOC soll in der Grafik erscheinen, „mit entsprechender Skalierung, so dass alle
Kurven gut sichtbar sind".

**Der Wert musste zuerst in den Entscheid.** `Specs/Gerätezustand.md` §8 hatte das als nächsten
Schritt benannt: `V155` ergänzt `steuerentscheid.soc`, gefüllt mit dem **letzten Wert vor dem
Intervallende** — der Entscheid beschreibt das abgeschlossene Intervall, also zählt der Zustand an
dessen Ende. Ohne Speicher-Einheit wird gar nicht erst gesucht.

Der SOC geht in **keine Regel** ein. Er erklärt den Entscheid im Nachhinein: „Ladung gesperrt bei
95 %" war wirkungslos, „bei 40 %" hat Kapazität freigehalten. Dass die Regel ihn auswerten sollte,
bleibt die offene Frage aus §8.

**Zur Skalierung — der eigentliche Punkt der Anforderung:**

Eine **dritte** y-Achse rechts aussen, 0–100 %, mit Versatz; der rechte Rand des Diagramms wächst
von 60 auf 115. Auf der Mengen-Achse hätte der SOC nicht funktioniert: 0–100 % gegen 0–25 kWh
drückte die Mengenkurven an den unteren Rand.

Gestrichelt und ohne Fläche, weil es eine **Zustands**grösse ist — die Linienart sagt das, bevor
jemand die Legende liest. Und `connectNulls: false`: Eine Lücke bleibt eine Lücke, statt dass die
Linie eine Gerade darüber zieht, die es nie gab.

**Die Farbpalette war erschöpft.** Fünf Diagrammfarben waren belegt (Produktion grün, Verbrauch
blau, Preis rot, Batterieladung orange, Einspeisung grau); alles Übrige im Design System sind
Abstufungen derselben Familien. Eine sechste Reihe in einem vorhandenen Ton hiesse, dass zwei
Kurven in der Legende gleich aussehen — **genau der Fehler, der bei Band und Kurve schon einmal
auftrat**. Deshalb ein neues Token `--color-chart-purple` in allen vier Theme-Blöcken (hell
`#7E57C2`, dunkel `#b197fc`), und der Farbtest vergleicht jetzt **sechs** statt fünf.

**Geprüft:** 1325 Backend-Tests, 1659 Frontend-Tests, Design System gebaut, Frontend gebaut.

**Nötig:** Rebuild für V155 und V156. Die Spalte füllt sich ab dem nächsten Job-Lauf; für
zurückliegende Intervalle bleibt sie leer, und ohne Speicher-Einheit bleibt sie es ganz.

> **Noch nicht am Bildschirm geprüft:** Ob die drei Achsen nebeneinander tatsächlich lesbar sind und
> der rechte Rand reicht. Das zeigt erst die Ansicht mit echten Daten — die Tests prüfen die
> Farben und die Daten, nicht die Lesbarkeit.

---

## Nachtrag 12 — „Heute“ und „Aktualisieren“ (17.09.2026)

Zwei Schaltflächen in der Steuerzeile. Beide betreffen nur die Ansicht; Backend und Datenmodell
bleiben unangetastet.

**„Heute“** setzt das Datum auf den heutigen Tag. Vorher führte der Weg zurück nur über
wiederholtes Blättern oder über das Datumsfeld. Deaktiviert, solange der heutige Tag schon
angezeigt wird — sonst wäre der Klick folgenlos, ohne dass man das vorher sähe.

**„Aktualisieren“** lädt den angezeigten Tag neu. Der Job schreibt alle 15 Minuten einen weiteren
Entscheid; eine offene Tagesansicht merkt davon nichts, weil sie nur beim Öffnen und beim
Tageswechsel lädt. Beides ruft `ladeTag()` auf — damit bleibt die **Betriebsart** erhalten: Wird
ein Schwellwert erprobt, wird derselbe Tag erneut nachgerechnet, sonst die Aufzeichnung gelesen.
Ein „Aktualisieren“, das stillschweigend auf die Aufzeichnung zurückfällt, wäre genau die
Verwechslung, gegen die FR-6a schon einmal angetreten ist.

**Keine neue Migration für „Heute“:** Der Schlüssel `HEUTE` besteht seit V65 (Debitor-Schnellaktion)
mit derselben Bedeutung — in der Datenbank geprüft, nicht angenommen.

**Wohl aber für „Aktualisieren“ (V157, `ANSICHT_AKTUALISIEREN`).** Der vorhandene Schlüssel
`AKTUALISIEREN` trägt englisch „Update“ und ist in fünf Formularen das Submit-Label. Auf einem
Knopf, der nur neu lädt, hiesse das im englischen UI „Daten ändern“. Deutsch sind beide Texte
identisch — die Kollision wäre im deutschen UI unsichtbar geblieben und erst einem englischen
Benutzer aufgefallen. Ohne `STEUERUNG_`-Präfix, weil der Text an kein Feature gebunden ist.

**Ein Icon musste weichen.** `refresh-cw` trug bisher „Aufzeichnung zeigen“. Es gehört semantisch
zu „Aktualisieren“, und zwei gleiche Icons in derselben Zeile wären nicht auseinanderzuhalten.
„Aufzeichnung zeigen“ trägt jetzt `database` — passender, denn es holt die **gespeicherten**
Entscheide zurück, statt neu zu laden. „Heute“ bekam `calendar`. Alle drei Namen gegen
`icons.ts` geprüft: Ein unbekannter Name zeichnet nichts und meldet es nur auf der Konsole — in
diesem Feature schon zweimal passiert.

**Platzierung vor dem Schwellwert-Block,** nicht am Zeilenende: Dort sitzt „Aufzeichnung zeigen“,
das nur zeitweise erscheint; Nachbarn am Zeilenende würden bei jedem Nachrechnen ihre Position
wechseln.

**Geprüft:** Frontend gebaut, 1659 Frontend-Tests grün.

**Nötig:** Rebuild für V157 — sonst zeigt die Schaltfläche den Schlüssel `ANSICHT_AKTUALISIEREN`
statt des Textes.

---

## Nachtrag 13 — Die Zustandsbänder waren zu hoch (17.09.2026)

Am Bildschirm aufgefallen: Das Band „Batterieladung gesperrt“ belegte knapp die halbe
Diagrammhöhe und drückte Produktion und Verbrauch in einen schmalen Streifen oben.

**Ursache.** Die Bandkanten standen als **feste kWh-Werte** im Code (−0.1 bis −1.0 für Ebene 1,
−1.3 bis −2.2 für Ebene 2, Achsen-`min` −2.4). Gewählt waren sie für eine Mengen-Achse bis etwa
25 kWh — dort nehmen sie rund 4 % der Höhe ein. Die Achse skaliert aber mit den Daten. Am
gezeigten Tag reichte sie bis 1 kWh: Der Bandbereich von 2.4 kWh war damit **mehr als doppelt so
hoch wie der gesamte Datenbereich**.

Der Fehler war von Anfang an da; er wurde erst sichtbar, als ein Tag mit kleinen Mengen auftrat.
Bei den Testdaten und den bisherigen Screenshots lagen die Mengen hoch genug, dass die festen Werte
zufällig passten — ein Maß, das nur in einem Teil seines Wertebereichs stimmt.

**Behebung.** Höhe und Abstände sind jetzt **Anteile der höchsten dargestellten Menge**: 4 % je
Band, 1.5 % Abstand. Achsen-`min` und Bandkanten kommen aus derselben Bezugsgrösse, also bleibt das
Verhältnis bei jeder Skalierung gleich. Bei 25 kWh entspricht das fast genau den bisherigen Werten
— die Darstellung, die vorher richtig aussah, ändert sich praktisch nicht.

**Ersatzwert für den Tag ohne Mengen.** Ist die höchste Menge 0, wäre jede Bandhöhe 0 und beide
Bänder unsichtbar — an genau den Tagen, an denen sie die einzige Aussage des Diagramms sind.
Deshalb tritt dort 1 kWh an ihre Stelle.

**Nebenbefund: die Achsenbeschriftung.** Sie lief über `String(w)`. Das genügte, solange die Achse
ganze kWh zeigte; bei einer Achse bis 1 kWh liefert die Gleitkommarechnung Striche wie
„0.30000000000000004“. Jetzt `formatSwissNumber` mit einer Stellenzahl nach Grössenordnung (ab
10 keine, ab 1 eine, ab 0.1 zwei, sonst drei) — sonst trügen zwei Striche dieselbe Beschriftung.

**Geprüft:** Frontend gebaut, 1659 Frontend-Tests grün.

> **Kein Test deckt das ab.** Die Komponente hat keine Unit-Tests (Phase 14 ist offen), und die
> Proportion wäre genau das, was einer prüfen könnte: Bandhöhe gegen Achsenbereich, bei kleinen
> und bei grossen Mengen. Gefunden hat den Fehler ein Blick auf den Bildschirm.

**Am Bildschirm bestätigt** (17.09.2026): 4 % sind bei einem Tag mit 1 kWh gut lesbar; die Bänder
wirken weder zu hoch noch zu dünn. Der Anteil bleibt, wie er ist.

---

## Nachtrag 14 — Speichermengen in Tabelle und Kurve (17.09.2026)

Die Anlage hat seit dem Typ `SPEICHER` einen Zähler für Ladung und Entladung. Beides steht jetzt
je Intervall im Entscheid (V158, `speicher_ladung` / `speicher_entladung`, beide nullable) und
erscheint als eigene Spalten in der Protokolltabelle. Die **Produktionskurve** zeigt
`produktion + Ladung − Entladung`.

**Das behebt einen Anzeigefehler, keinen Rechenfehler.** Der Hybrid-Wechselrichter gibt
wechselstromseitig nur ab, was das Haus braucht; was in die Batterie geht, läuft über keinen
Erzeugungszähler. Deshalb stand bei voller Sonne weniger Produktion als Verbrauch — die Frage, die
diese Arbeit ausgelöst hat.

**Überschuss und Regel bleiben unberührt** (so entschieden). Ein Entscheid vor und nach dieser
Änderung ist derselbe, die Rückrechnung liefert unveränderte Zahlen. Das ursprüngliche Problem —
Steuerung greift erst bei voller Batterie — ist bereits über die Regelreihenfolge behoben.

**Der Verbrauch bleibt, wie er ist.** Angenommen, der Verbrauchszähler erfasst den Hausverbrauch
wechselstromseitig vollständig und die Ladung erscheint dort nicht. Träfe das nicht zu, wäre die
Ladung doppelt gezählt. Die Annahme steht in FR-5b und ist an der Tabelle prüfbar, weil dort alle
Grössen einzeln stehen — sie war beim Umsetzen nicht entscheidbar.

**Die Tabellenspalte Produktion bleibt der gemessene Wert.** Die Tabelle ist das Protokoll der
Eingangsgrössen: Der Überschuss muss sich aus ihr erklären lassen. Würde dort der verrechnete Wert
stehen, passte er nicht mehr zum Überschuss daneben. Das Diagramm zeigt die Interpretation, die
Tabelle die Grundlage — und über die beiden neuen Spalten lässt sich die eine in die andere
umrechnen.

**Der Legendenname wechselt mit der Datenlage.** Ohne Speicherdaten `Produktion`, mit ihnen
`Produktion (mit Speicher)`. Ein fester Name hätte „Produktion“ über eine Zahl geschrieben, die in
keiner Tabelle steht.

**Ein Fund nebenbei:** `zuDto(Nachgerechnet)` setzte **kein** `soc`. Die nachgerechnete Ansicht
zeigte damit keinen Ladezustand, die Aufzeichnung schon — beim Umschalten verschwand die Kurve,
ohne dass sich an den Daten etwas geändert hätte. Beides wird jetzt in `reichereSpeicherAn()`
ergänzt, und zwar **nur im Tagespfad**: Der gemeinsame Rechenweg trägt auch die Rückrechnung über
366 Tage, und die Zustandszeitreihe mit mehreren tausend Werten je Tag wäre dort die eigentliche
Laufzeit (NFR-1).

**Der Anfangswert kommt aus dem Vortag.** Für das erste Intervall eines Tages liegt der letzte
Ladezustand davor vor Mitternacht; ohne eine eigene Abfrage bliebe 00:00–00:15 als einziges
Intervall leer.

**Eigene Abfrage statt Erweiterung.** `sumBilanzKomponentenPerZeitBetween` hätte die Werte in einem
Zug mitliefern können. Der Speicher ist aber keine Bilanzkomponente, und diese Abfrage trägt die
Statistik-Kennzahlen mit — eine zusätzliche Spalte dort verschiebt die Indizes für einen Aufrufer,
den diese Änderung nichts angeht.

**Spaltenpräfix `speicher_`:** Die Tabelle hat bereits `batterieladung` — das ist der **Zustand**
(FREI/GESPERRT), keine Menge. Eine Spalte `ladung` daneben wäre beim Lesen einer Abfrage kaum
auseinanderzuhalten.

**Geprüft:** Backend kompiliert, 1325 Backend-Tests, 1659 Frontend-Tests, Frontend gebaut — alles grün.

**Nötig:** Rebuild für V158 und V159. Die Spalten füllen sich ab dem nächsten Job-Lauf;
zurückliegende Entscheide bleiben leer. Die **nachgerechnete** Ansicht zeigt die Mengen dagegen
sofort auch für vergangene Tage — sie liest die Messwerte, nicht den Entscheid.

**Am Bildschirm gesehen** (17.09.2026): Die verrechnete Kurve wird gezeichnet und liegt über dem
Verbrauch, die Legende heißt „Produktion (mit Speicher)“, Tooltip und Tabellenspalten stimmen
zusammen — im selben Bild trug ein Intervall 3.913 kWh verrechnet bei 3.700 kWh Ladung und
0.213 kWh am Erzeugungszähler. Genau der Anteil, der vorher fehlte.

> **Weiter offen:** Ob die Kurve über einen ganzen Sonnentag hinweg fachlich plausibel bleibt.
> Das ist eine Beurteilung an echten Tagesverläufen, nicht an einem Intervall — und die lokalen
> Speicherwerte taugen dazu nicht, weil der Simulator sie unabhängig von Produktion und Verbrauch
> erzeugt.

**Nachtrag zum Tooltip (17.09.2026):** Mit den drei neuen Zeilen nennt er dreizehn Grössen und war
bei der ECharts-Vorgabe von 14 px höher als das Diagramm. ECharts schneidet dann **oben** ab statt
zu scrollen — sichtbar blieb der Rumpf ab „Produktion (mit Speicher)“, während Zeitpunkt, Preis und
Tiefstpreis verschwanden. Also gerade die Führungsgrössen der Steuerung. Jetzt 11 px und knapperes
Padding. Der Fehler wächst mit der Zeilenzahl: Kommt eine weitere Grösse dazu, ist er wieder da.

---

## Nachtrag 15 — Protokolltabelle neuste zuerst (17.09.2026)

Die Tabelle sortiert absteigend nach Zeit. Beim heutigen Tag steht damit das zuletzt ausgewertete
Intervall oben, statt am Ende von bis zu 96 Zeilen.

**Zwei Listen statt einer gedrehten.** `entscheide` bleibt aufsteigend — das Diagramm zeichnet eine
Zeitachse nach rechts, und `bloecke()` setzt aufeinanderfolgende Intervalle voraus. Ein `reverse()`
auf der gemeinsamen Liste hätte die Kurven rückwärts laufen lassen und die Zustandsbänder in 96
Einzelrechtecke zerlegt — und kein Test hätte es gemerkt, weil die Komponente keine hat.

**Einmal beim Laden berechnet, nicht als Getter.** Ein Getter liefe bei jedem
Change-Detection-Zyklus und gäbe jedes Mal ein neues Array zurück.

**Sortiert statt gedreht.** Ein `reverse()` wäre nur richtig, solange der Server aufsteigend
liefert. Das tut er (`ORDER BY zeitVon`), aber die Tabelle hätte damit an einer Zusage gehängt, die
sie selbst nicht prüfen kann. Bei 96 Zeilen kostet das Sortieren nichts.

**Geprüft:** Frontend gebaut, 1659 Frontend-Tests grün. Keine Migration, keine neuen Übersetzungen.

---

## Nachtrag 16 — Solarproduktion gelb (17.09.2026)

Die Produktionskurve ist gelb statt grün. Zwei neue Tokens, in allen vier Theme-Blöcken:
`--color-chart-yellow` (hell `#EAB308`, dunkel `#FFE066`) und `--color-chart-orange`
(hell `#F97316`, dunkel `#FB923C`).

**Warum zwei und nicht eines.** Das Zustandsband „Batterieladung gesperrt“ bezog seine Farbe aus
`--color-warning`. Das ist eine **Statusfarbe** und wechselt mit dem Thema den Farbton: hell
`#FF9800` (orange), dunkel `#ffd43b` (**gelb**). Im dunklen Thema war das Band also schon gelb —
im Screenshot gut zu sehen. Hätte nur die Produktion Gelb bekommen, wären beide dort
ununterscheidbar gewesen: derselbe Fehler wie zu Beginn dieses Features, als Band und Kurve beide
grün bzw. beide blau waren. Das Band trägt deshalb jetzt einen eigenen Ton, der in beiden Themes
orange bleibt.

**Ein Test, der genau das abfaengt.** `chart-farben.spec.ts` setzt `--color-warning` auf einen
Erkennungswert und prüft, dass weder `solar` noch `bandEins` ihn übernimmt. Der bisherige
Set-Test hätte die Kollision **nicht** gefunden: Er vergleicht die Rückfallwerte, weil in jsdom
keine Tokens geladen sind — und die Fallbacks waren nie gleich. Sichtbar war der Fehler nur im
dunklen Thema im Browser.

**Geprüft:** Design System gebaut, Frontend gebaut, 1660 Frontend-Tests grün (einer mehr als zuvor).
Keine Migration, keine neuen Übersetzungen.

**Am Bildschirm bestätigt** (18.09.2026, dunkles Thema): Gelb und Orange sind in der Legende klar
auseinanderzuhalten — die Produktionskurve gelb, das Band darunter deutlich orange. Im hellen
Thema noch nicht angesehen.

---

## Nachtrag 17 — Die verrechnete Produktion war nachts negativ (18.09.2026)

Im Tagesverlauf der Anlage aufgefallen: Die Kurve „Produktion (mit Speicher)“ lag zwischen 00:00
und 07:00 **unter null**.

**Der erste Erklärungsversuch war falsch** und ist hier festgehalten, weil er plausibel klang: Die
Entladung werde zu Unrecht abgezogen, weil der Erzeugungszähler sie gar nicht sehe. Das
Entscheidungsprotokoll widerlegte es sofort — **nachts steht in `produktion` 0.117 bis 0.266 kWh**,
ohne Sonne. Der Zähler misst die Wechselstromabgabe des Wechselrichters, und die kommt nachts aus
der Batterie. Der Abzug ist also richtig.

**Die wirkliche Ursache ist die Auflösung.** Die Entladung kommt in Schritten von 0.1 kWh
(`Specs/Solinteg_Modbus_Register.md`), die Produktion auf drei Stellen. Liegt die wirkliche
Entladung bei 0.17 kWh je Viertelstunde, meldet der Zähler mal 0.1 und mal 0.2 — im Mittel richtig,
je Intervall aber um bis zu ±0.08 daneben. Nachgerechnet am Protokoll:

| Zeit | Produktion | Entladung | `P + L − E` |
|---|---|---|---|
| 06:45 | 0.117 | 0.200 | −0.083 |
| 04:00 | 0.172 | 0.200 | −0.028 |
| 07:00 | 0.266 | 0.200 | +0.066 |

Das sind genau die Täler und Zacken des Diagramms.

**Behoben über eine Schranke bei 0.** Eine negative Erzeugung gibt es nicht; das ist eine
physikalische Aussage und keine Kosmetik. Die Rohwerte bleiben in der Tabelle einzeln stehen, die
Rechnung ist nachvollziehbar.

**Was die Schranke NICHT behebt:** das Zacken tagsüber. Solange grobe Entladung von feiner
Produktion abgezogen wird, bleibt das Rauschen — es fällt nur nicht mehr unter null. Bei Mengen um
0.2 kWh je Viertelstunde ist das ein erheblicher Anteil; bei den Mengen eines Sonnentags fällt es
kaum ins Gewicht.

**Zur Lehre:** Der erste Schluss entstand aus der Kurve allein. Das Protokoll darunter hätte ihn in
einer Zeile widerlegt — es steht genau dafür da (FR-5), und ich habe es nicht gelesen.

**Geprüft:** Frontend gebaut; am Tagesverlauf vom 18.09.2026 gegen das Protokoll nachgerechnet —
08:00 `0.151 + 0.100 = 0.251`, 07:30 `0.171 − 0.100 = 0.071`, 07:15 `0.192 − 0.200 → 0`. Die Täler
liegen jetzt auf der Nulllinie statt darunter. Keine Migration — gespeichert wird weiterhin beides
getrennt, gerechnet wird erst in der Anzeige.

> **Offen bleibt die andere Hälfte desselben Fehlers:** Nachts müsste die Kurve durchgehend auf
> null liegen. Sie zeigt stattdessen Zacken bis 0.07 kWh — immer dann, wenn der Zähler 0.1 meldet,
> wo 0.2 richtig wäre. Die Schranke fängt nur die negative Seite ab. Abhilfe gäbe nur eine feinere
> Quelle (Register 30258, Batterieleistung in Watt) und damit ein anderes Messprinzip.
