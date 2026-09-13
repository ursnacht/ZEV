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
| `db/migration/V<nächste>__Create_Steuerentscheid.sql` | Tabelle `zev.steuerentscheid` |
| `db/migration/V<nächste+1>__Add_Einspeisesteuerung_Translations.sql` | 20 Schlüssel |
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
| `service/EinstellungenService.java` | Durchreichen des neuen Blocks |
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
> Für die Preise genügt `PreiszeitreiheRepository.findByZeitVonBetween(von, bis)`.

## Phasen

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [ ] | 1. DB-Migration | `zev.steuerentscheid` nach FR-3: `org_id` **Pflicht**, `UNIQUE (org_id, zeit_von)`, `zeit_von`/`preis`/`preis_tief_rest`/`produktion`/`verbrauch`/`ueberschuss`/`regel`/`batterieladung`/`einspeisung`/`schwellwert`/`speicherwert`/`erstellt_am`. Spalten-Kommentare. **Nächste freie Nummer prüfen** — `Specs/Batteriespeicher.md` braucht ebenfalls eine |
| [ ] | 2. Entity & Repository | `Steuerentscheid` mit `@Filter(orgFilter)`, Enums `Steuerzustand`/`Steuerregel`; Repository mit nativem Upsert auf `(org_id, zeit_von)` (Muster `DebitorRepository.upsert`) und Tagesabfrage |
| [ ] | 3. Konfiguration | `SteuerKonfigurationDTO` (`schwellwert`, `speicherwert`, `batteriekapazitaet`) **als Feld `steuerung` in `RechnungKonfigurationDTO`** — siehe Kasten. Vorgaben `0.05` / `0.31` / leer, wenn der Block fehlt |
| [ ] | 4. Feature-Flag | `FeatureFlag.EINSPEISESTEUERUNG`, Vorgabe `false` |
| [ ] | 5. Regel | `SteuerRegelService`: die fünf Regeln in fester Reihenfolge, **reine Funktion**. Überschuss aus `|Produktion| − Verbrauch`, mindestens 0. Tiefstpreis = Minimum der Preise **nach** dem Intervall, gleicher Ortstag |
| [ ] | 6. Job | `SteuerungJob` mit `@Scheduled(cron = "${einspeisesteuerung.job.cron:0 1,16,31,46 * * * *}")`, `getOrgIdsMitAktivemFlag(...)`, je Organisation `enableOrgFilter(orgId)` — **die parametrisierte Variante**, im Job gibt es keinen Sicherheitskontext. Wertet das zuletzt abgeschlossene Intervall aus, schreibt per Upsert. Fängt jede Ausnahme (Muster `PreiszeitreiheDownloadJob`) |
| [ ] | 7. Controller | `GET /api/einspeisesteuerung/entscheide?datum=`, `POST /api/einspeisesteuerung/simulation`; `@PreAuthorize("hasAuthority('tarife:manage')")` auf Klassenebene, Feature-Flag-Prüfung in **jeder** Methode → `403`. Fehlerrümpfe als Klartext |
| [ ] | 8. Nachrechnen | Simulation über `SteuerRegelService` mit abweichenden Schwellen; liest Preise und Messwerte, **schreibt nichts**. Zählt je Regel **nur Intervalle mit Überschuss** (FR-6). Zeitraum auf 366 Tage begrenzt |
| [ ] | 9. Einstellungen | Abschnitt „Einspeisesteuerung" in der bestehenden Maske, sichtbar nur bei aktivem Flag; drei Felder mit Validierung (negativ erlaubt bei beiden Preisen) |
| [ ] | 10. Frontend-Grundlage | `einspeisesteuerung.model.ts` und `.service.ts` nach den Vorlagen (`tarif.model.ts`, `tarif.service.ts`) |
| [ ] | 11. Frontend-Seite | Komponente nach dem Muster von `preiszeitreihe-chart`: ECharts **dynamisch** über `ladeECharts()`, Steuerzeile (`zev-date-range-row`), Diagramm in `zev-panel--chart`, Protokolltabelle als `zev-table`. **Zustandsbänder als Balkenserie** — `BarChart` ist bereits registriert, `markArea` bräuchte ein zusätzliches Modul im gemeinsamen Loader |
| [ ] | 12. Routing & Navigation | Route `/einspeisesteuerung` mit `AuthGuard` + `FeatureFlagGuard`; Menüeintrag mit `*appFeature` **und** `*appPermission` |
| [ ] | 13. Übersetzungen | Migration mit den 20 Schlüsseln aus FR-9, deutsch **mit Umlauten**, `ON CONFLICT (key) DO NOTHING` |
| [ ] | 14. Tests | siehe unten |

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
