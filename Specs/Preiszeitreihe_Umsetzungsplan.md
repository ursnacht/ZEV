# Umsetzungsplan: Preiszeitreihe

## Zusammenfassung

Die dynamischen Einspeisepreise der BKW werden täglich um 02:00 sowie auf Knopfdruck von
`https://api.bkw.ch/api/dyntariffs/v1/Tariffs` bezogen, per Upsert in einer neuen, mandantenüber-
greifenden Tabelle `zev.preiszeitreihe` gespeichert (15-Minuten-Raster, UTC) und auf der Seite
**Tarife** unterhalb der Tarifliste als zoombare Stufenlinie mit ECharts dargestellt — Auswahl über
TAG/WOCHE/MONAT oder Datum von/bis, mit Blättern.

Das Feature liegt hinter dem neuen Feature-Flag `PREISZEITREIHE` (Default `false`) und ist reine
Datengrundlage für künftige dynamische Tarife; die Abrechnung bleibt unberührt.

Grundlage: `Specs/Preiszeitreihe.md`.

---

## Betroffene Komponenten

### Neu — Backend

| Datei | Zweck |
|---|---|
| `db/migration/V129__Create_Preiszeitreihe.sql` | Sequenz, Tabelle, Unique-Constraint, Spaltenkommentare |
| `db/migration/V130__Add_Preiszeitreihe_Translations.sql` | Übersetzungen DE/EN |
| `entity/Preiszeitreihe.java` | Entity **ohne** `org_id` (begründete Ausnahme, FR-2) |
| `repository/PreiszeitreiheRepository.java` | Bereichsabfrage + natives Upsert |
| `service/PreiszeitreiheService.java` | Maske-zugewandt: Bereichsabfrage und Download, prueft das Feature-Flag |
| `service/PreiszeitreiheAbrufService.java` | Beschaffung: HTTP, Validierung, Upsert, Systemmeldungen (ohne Mandantenkontext — siehe Abweichung 1) |
| `util/PreiszeitreiheZeit.java` | Umrechnung UTC ↔ Europe/Zurich an einer Stelle |
| `exception/PreiszeitreiheQuelleException.java` | Fehler der Quelle → `502` |
| `service/PreiszeitreiheDownloadJob.java` | `@Component` + `@Scheduled` (nicht `@Service`) |
| `controller/PreiszeitreiheController.java` | `GET` Bereich, `POST` Download |
| `dto/PreiszeitreihePunktDTO.java` | Ausgabe: `zeit` (Ortszeit), `preis` |
| `dto/PreiszeitreiheDownloadDTO.java` | Ergebnis: `abgerufen`, `neu`, `aktualisiert`, `uebersprungen`, `publikation` |
| `dto/BkwTariffsResponseDTO.java` (+ verschachtelte Records) | Abbild der Fremdantwort |
| `config/RestClientConfig.java` | `RestClient`-Bean mit Timeouts |

### Neu — Frontend

| Datei | Zweck |
|---|---|
| `models/preiszeitreihe.model.ts` | `PreiszeitreihePunkt`, `PreiszeitreiheDownload`, `Spanne` |
| `services/preiszeitreihe.service.ts` | `getPunkte(von, bis)`, `download()` |
| `components/preiszeitreihe-chart/preiszeitreihe-chart.component.ts` | Spannenlogik, dynamischer ECharts-Import |
| `components/preiszeitreihe-chart/preiszeitreihe-chart.component.html` | Steuerzeile + Diagramm-Panel |
| `components/preiszeitreihe-chart/preiszeitreihe-chart.component.css` | nur komponentenspezifisches CSS |

### Geändert

| Datei | Änderung |
|---|---|
| `entity/FeatureFlag.java` | Flag `PREISZEITREIHE(false, "FEATURE_FLAG_PREISZEITREIHE")` |
| `service/FeatureFlagService.java` | neue Methode `getOrgIdsMitAktivemFlag(FeatureFlag)` |
| `service/SystemmeldungService.java` | Konstante `KATEGORIE_PREISZEITREIHE` |
| `application.yml` | Block `preiszeitreihe:` (`url`, `download.cron`, Timeouts) |
| `test/.../architecture/ArchitectureTest.java` | Ausnahme in `everyEntityMustHaveOrgId`, neue Flag-Regel |
| `components/tarif-list/tarif-list.component.html` | `<app-preiszeitreihe-chart>` hinter `*appFeature` |
| `frontend-service/package.json` | Abhängigkeit `echarts` (6.1.0, exakt gepinnt wie die übrigen) |
| `design-system/.../quarter-selector.css` | geteilte Deklarationen für `zev-toggle-button` |
| `components/design-system-showcase/…html` | Showcase-Eintrag für `zev-toggle-button` |
| `components/tarif-list/tarif-list.component.ts` | Import der Komponente und der `appFeature`-Direktive |
| `Specs/Berechtigungen.md` | Zeile `PreiszeitreiheController` → `tarife:manage` |

### Unverändert (bewusst)

`app.routes.ts` und `app.component.html` — kein neuer Menüeintrag, keine neue Route (Spec §7).

---

## Phasen-Tabelle

| Status | Phase                          | Beschreibung                                                                                                    |
|--------|--------------------------------|-----------------------------------------------------------------------------------------------------------------|
|  [x]   | 1. DB-Migration                | `V129__Create_Preiszeitreihe.sql`: Sequenz, Tabelle, `UNIQUE (zeit_von)`, Spaltenkommentare                      |
|  [x]   | 2. Entity + Repository         | `Preiszeitreihe` (ohne `org_id`), Bereichsabfrage, natives Upsert nach dem Muster `DebitorRepository`            |
|  [x]   | 3. Konfiguration              | `application.yml`-Block, `RestClientConfig` mit Verbindungs-/Lese-Timeout                                        |
|  [x]   | 4. Fremd-DTOs                  | `BkwTariffsResponse` samt verschachtelten Records, tolerant gegen unbekannte Felder                             |
|  [x]   | 5. Feature-Flag                | Flag `PREISZEITREIHE`, `FeatureFlagService.getOrgIdsMitAktivemFlag`, Kategorie-Konstante in `SystemmeldungService` |
|  [x]   | 6. Backend-Service             | Abruf, Validierung (Einheit, Menge), Umwandlung, Upsert in `zeit_von`-Reihenfolge, Systemmeldung + `autoResolve` |
|  [x]   | 7. Geplanter Job               | `PreiszeitreiheDownloadJob` (`@Component`, `@Scheduled`), überspringt ohne aktives Flag                          |
|  [x]   | 8. Backend-Controller + DTOs   | `GET` mit Datum→UTC-Umrechnung und Grenzen, `POST` mit Statuscodes `200/400/403/502`                             |
|  [x]   | 9. ArchUnit                    | Namentliche `org_id`-Ausnahme, neue Regel „Flag-Prüfung in `Preiszeitreihe*`-Services"                          |
|  [x]   | 10. Übersetzungen              | `V130__Add_Preiszeitreihe_Translations.sql`, alle Keys mit `ON CONFLICT (key) DO NOTHING`                        |
|  [x]   | 11. Abhängigkeit ECharts       | `npm i echarts`, Lizenz/SBOM prüfen (`/lizenzen`)                                                               |
|  [x]   | 12. Frontend-Model + Service   | `preiszeitreihe.model.ts`, `preiszeitreihe.service.ts` über `getRuntimeConfig().apiBaseUrl`                      |
|  [x]   | 13. Frontend-Komponente (TS)   | Spannenlogik TAG/WOCHE/MONAT, Blättern, Laden, **dynamischer** ECharts-Import, Fehler-/Ladezustand               |
|  [x]   | 14. Frontend-Template + CSS    | Steuerzeile und Panel aus Design-System-Klassen; Design System **zuerst** prüfen                                 |
|  [x]   | 15. Einbindung in Tarife       | `<app-preiszeitreihe-chart>` in `tarif-list.component.html` hinter `*appFeature="'PREISZEITREIHE'"`              |
|  [x]   | 16. Bundle-Kontrolle           | Produktionsbau: `main-*.js` wächst < 20 kB, ECharts in eigenem Chunk                                            |
|  [x]   | 17. Doku nachziehen            | `Specs/Berechtigungen.md` (Controller-Matrix)                                                                   |
|  [x]   | 21. Steuerzeile + Darstellung   | Eine Zeile auf gleicher Höhe, Umschaltung Linie/Balken, `V131` (Vibe-Ergänzung)                                  |
|  [ ]   | 18. Backend-Tests              | `/3_backend-tests` — Service, Controller, Repository-IT, `ControllerAuthorizationTest`                           |
|  [ ]   | 19. Frontend-Unit-Tests        | `/4_frontend-unit-tests` — Service, Komponente (ECharts gemockt)                                                 |
|  [ ]   | 20. E2E-Tests                  | `/5_e2e-tests` — nur Chromium, `serial`, Flag setzen und zurückstellen                                           |

---

## Phase 1: DB-Migration

**Datei:** `backend-service/src/main/resources/db/migration/V129__Create_Preiszeitreihe.sql`

```sql
CREATE SEQUENCE IF NOT EXISTS zev.preiszeitreihe_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE zev.preiszeitreihe (
    id              BIGINT PRIMARY KEY DEFAULT nextval('zev.preiszeitreihe_seq'),
    zeit_von        TIMESTAMP      NOT NULL,
    zeit_bis        TIMESTAMP      NOT NULL,
    preis           NUMERIC(10, 5) NOT NULL,
    publikation     TIMESTAMP,
    aktualisiert_am TIMESTAMP      NOT NULL DEFAULT now(),
    CONSTRAINT uq_preiszeitreihe_zeit_von UNIQUE (zeit_von),
    CONSTRAINT ck_preiszeitreihe_intervall CHECK (zeit_von < zeit_bis),
    CONSTRAINT ck_preiszeitreihe_preis CHECK (preis >= 0)
);

COMMENT ON TABLE  zev.preiszeitreihe          IS 'Dynamische Einspeisepreise (BKW), 15-Min-Raster. Mandantenuebergreifend - siehe Specs/Preiszeitreihe.md FR-2';
COMMENT ON COLUMN zev.preiszeitreihe.zeit_von IS 'Intervallbeginn in UTC (verbatim aus der Quelle; lokale Zeit waere an der Zeitumstellung nicht eindeutig)';
COMMENT ON COLUMN zev.preiszeitreihe.zeit_bis IS 'Intervallende in UTC';
COMMENT ON COLUMN zev.preiszeitreihe.preis    IS 'Einspeisepreis in CHF/kWh mit 5 Nachkommastellen';
COMMENT ON COLUMN zev.preiszeitreihe.publikation IS 'publication_timestamp der Quelle (UTC); leer, wenn die Quelle keinen liefert';
COMMENT ON COLUMN zev.preiszeitreihe.aktualisiert_am IS 'Zeitpunkt des letzten Schreibens';
```

Der Unique-Constraint erzeugt den Index, den die Bereichsabfrage nutzt — ein zusätzlicher Index
wäre Ballast.

**Kein `org_id`:** bewusste Ausnahme (Spec FR-2). Die Begründung steht im Tabellenkommentar, damit
sie am Objekt selbst auffindbar ist.

---

## Phase 2: Entity + Repository

**Vorlage:** `entity/Tarif.java`, `repository/TarifRepository.java`, Upsert nach
`repository/DebitorRepository.java`.

`entity/Preiszeitreihe.java` — Felder `id`, `zeitVon`, `zeitBis`, `preis`, `publikation`,
`aktualisiertAm`; `@Entity`, `@Table(name = "preiszeitreihe", schema = "zev")`,
`GenerationType.SEQUENCE` auf `zev.preiszeitreihe_seq`. **Kein** `orgId`, **kein** `@Filter` — mit
Klassenkommentar, der auf FR-2 und die ArchUnit-Ausnahme (Phase 9) verweist.

`repository/PreiszeitreiheRepository.java`:

```java
@Query("SELECT p FROM Preiszeitreihe p WHERE p.zeitVon >= :von AND p.zeitVon < :bis "
        + "ORDER BY p.zeitVon")
List<Preiszeitreihe> findByZeitraum(@Param("von") LocalDateTime von,
                                    @Param("bis") LocalDateTime bis);

@Modifying
@Query(value = """
    INSERT INTO zev.preiszeitreihe (zeit_von, zeit_bis, preis, publikation, aktualisiert_am)
    VALUES (:zeitVon, :zeitBis, :preis, :publikation, now())
    ON CONFLICT (zeit_von)
    DO UPDATE SET zeit_bis = EXCLUDED.zeit_bis, preis = EXCLUDED.preis,
                  publikation = EXCLUDED.publikation, aktualisiert_am = now()
    """, nativeQuery = true)
void upsert(@Param("zeitVon") LocalDateTime zeitVon, @Param("zeitBis") LocalDateTime zeitBis,
            @Param("preis") BigDecimal preis, @Param("publikation") LocalDateTime publikation);
```

* Der Konfliktschlüssel muss **genau** `uq_preiszeitreihe_zeit_von` entsprechen, sonst scheitert
  jeder Aufruf mit „no unique or exclusion constraint matching the ON CONFLICT specification".
* Die obere Bereichsgrenze ist **exklusiv** (`< :bis`): Der Controller übergibt den Beginn des
  Folgetags, damit kein Wert doppelt oder gar nicht erfasst wird.
* **Kein `findById`** — sonst muss `PreiszeitreiheRepository` in die `ungefiltert`-Whitelist der
  ArchUnit-Regel `servicesMustNotUseFindByIdOnFilteredRepositories`.

Für die Zählung „neu vs. aktualisiert" liefert das Upsert keinen Hinweis. Der Service ermittelt sie
über `existsByZeitVon`-freies Vorgehen: Er liest vor dem Schreiben die vorhandenen `zeit_von` der
gelieferten Spanne (`findByZeitraum`) in ein `Set` und vergleicht.

---

## Phase 3: Konfiguration

**`application.yml`** (Block am Ende, Stil wie `systemmeldung:`):

```yaml
preiszeitreihe:
  url: "https://api.bkw.ch/api/dyntariffs/v1/Tariffs"   # Quelle der dynamischen Einspeisepreise
  download:
    cron: "0 0 2 * * *"      # täglich 02:00
  timeout:
    verbindung: 5s
    lesen: 10s
```

**`config/RestClientConfig.java`** — `RestClient`-Bean mit den beiden Timeouts (Vorlage: bestehende
`@Configuration`-Klassen in `config/`). Bewusst ein eigener Bean und kein `RestClient.create()` im
Service: Timeouts sind eine Betriebs-, keine Fachentscheidung, und ohne sie hängt der Job im
Zweifel bis zum nächsten Lauf.

---

## Phase 4: Fremd-DTOs

`dto/BkwTariffsResponse.java` als Records:

```java
public record BkwTariffsResponse(LocalDateTime publicationTimestamp, List<BkwPrice> prices) {
    public record BkwPrice(LocalDateTime startTimestamp, LocalDateTime endTimestamp,
                           List<BkwValue> feedIn) { }
    public record BkwValue(String unit, BigDecimal value) { }
}
```

* Feldnamen der Quelle sind `snake_case` → `@JsonProperty` oder eine `PropertyNamingStrategy` auf
  dem lokal konfigurierten `ObjectMapper` des `RestClient`; **nicht** die globale Jackson-Konfiguration
  ändern (sie gilt für die eigene API).
* Zeitstempel enden auf `Z`: als `Instant` lesen und auf `LocalDateTime` in UTC abbilden, damit die
  Entity ohne Zeitzonenlogik auskommt.
* Unbekannte Felder werden ignoriert (`FAIL_ON_UNKNOWN_PROPERTIES=false`), damit eine Erweiterung
  der Quelle den Abruf nicht bricht.

---

## Phase 5: Feature-Flag und Systemmeldungs-Kategorie

* `entity/FeatureFlag.java`: `PREISZEITREIHE(false, "FEATURE_FLAG_PREISZEITREIHE")` samt Javadoc
  („Bereich im Aufbau, je Mandant freischalten" — analog `NEBENKOSTENABRECHNUNG`).
* `service/FeatureFlagService.java`:

```java
@Transactional(readOnly = true)
public List<Long> getOrgIdsMitAktivemFlag(FeatureFlag flag) {
    return organisationRepository.findAll().stream()
            .map(Organisation::getId)
            .filter(orgId -> isEnabled(orgId, flag))
            .toList();
}
```

  Über `isEnabled` statt direkt über die Overrides: Damit greift auch ein später geänderter globaler
  Default, ohne dass diese Methode angepasst werden muss.
* `service/SystemmeldungService.java`: `public static final String KATEGORIE_PREISZEITREIHE =
  "SYSTEMMELDUNG_KATEGORIE_PREISZEITREIHE";` — nur die Konstante, `erfasse` und `autoResolve`
  bleiben unverändert.

---

## Phase 6: Backend-Service

**Datei:** `service/PreiszeitreiheService.java`

Öffentliche Methoden (jede beginnt mit `pruefeFeatureFlag()` — Phase 9 hält das fest):

| Methode | Zweck |
|---|---|
| `List<PreiszeitreihePunktDTO> getPunkte(LocalDate von, LocalDate bis)` | Bereichsabfrage samt Datum→UTC-Umrechnung und Rückumrechnung auf Ortszeit |
| `PreiszeitreiheDownloadDTO download()` | Abruf und Upsert, für Job und Endpunkt derselbe Weg |

Ablauf von `download()`:

1. Antwort holen (`RestClient`). Jede Ausnahme → `PreiszeitreiheQuelleException` (neu, wird vom
   Controller auf `502` abgebildet).
2. `prices` prüfen: `null` → Formatfehler; Grösse > 10'000 → unplausibel, Abbruch **ohne** Schreiben.
3. **Einheitsprüfung vor dem Schreiben:** Enthält irgendein Eintrag eine `unit != "CHF_kWh"`, wird
   der **gesamte** Abruf abgewiesen. Erst prüfen, dann schreiben — sonst stünde die halbe Reihe in
   fremder Einheit in der Tabelle.
4. Einträge filtern: fehlender Zeitstempel, fehlender Wert, leeres `feed_in`, `end <= start` →
   überspringen und zählen.
5. Bestand der gelieferten Spanne lesen (`findByZeitraum`) → `Set<LocalDateTime>` für die Zählung
   neu/aktualisiert.
6. **Sortiert nach `zeitVon`** upserten (gleiche Sperrreihenfolge wie der parallele Aufrufer, FR-5).
7. Erfolg: `autoResolve(orgId, "PREISZEITREIHE_ABRUF_FEHLER")` für jede Organisation mit aktivem
   Flag; ohne übersprungene Einträge zusätzlich `autoResolve(..., "PREISZEITREIHE_WERTE_UEBERSPRUNGEN")`,
   sonst `erfasse(..., WARN, KATEGORIE_PREISZEITREIHE, "PREISZEITREIHE_WERTE_UEBERSPRUNGEN", …)`.
8. Fehler: `erfasse(..., WARN, KATEGORIE_PREISZEITREIHE, "PREISZEITREIHE_ABRUF_FEHLER", kurzgrund)`
   je Organisation mit aktivem Flag; **`kurzgrund` auf 500 Zeichen kürzen** (Spaltenbreite von
   `systemmeldung.parameter`).

Zeitumrechnung an genau einer Stelle (privat, mit `ZoneId.of("Europe/Zurich")`):
`von 00:00` Ortszeit → UTC, `bis + 1 Tag 00:00` Ortszeit → UTC (exklusive Obergrenze), und
`LocalDateTime` (UTC) → Ortszeit für das DTO.

---

## Phase 7: Geplanter Job

**Datei:** `service/PreiszeitreiheDownloadJob.java` — **`@Component`**, nicht `@Service`
(`NamingConventionTests.servicesShouldEndWithService` prüft nur `@Service`-Klassen; Vorbild
`SystemmeldungCleanupJob`).

```java
@Scheduled(cron = "${preiszeitreihe.download.cron:0 0 2 * * *}")
public void hole() { … }
```

* Ermittelt zuerst `getOrgIdsMitAktivemFlag(PREISZEITREIHE)`. Leer → `log.debug` und Ende, **kein**
  HTTP-Aufruf.
* Ruft danach die Service-Methode auf und fängt jede Ausnahme, damit der Scheduler-Thread lebt.
* Der Job selbst prüft das Flag nicht per `pruefeFeatureFlag()` — er entscheidet über die
  Flag-Menge und steht deshalb namentlich in der ArchUnit-Ausnahme (Phase 9).

---

## Phase 8: Controller + DTOs

**Datei:** `controller/PreiszeitreiheController.java` (Vorlage `TarifController`)

```java
@RestController
@RequestMapping("/api/preiszeitreihe")
@PreAuthorize("hasAuthority('tarife:manage')")
public class PreiszeitreiheController { … }
```

| Endpunkt | Verhalten |
|---|---|
| `GET ?von=&bis=` | `200` + Liste (leer möglich). `von > bis` oder Spanne > 366 Tage → `400` mit Klartext |
| `POST /download` | `200` + `PreiszeitreiheDownloadDTO`; `PreiszeitreiheQuelleException` → `502` Klartext; `IllegalArgumentException` (Konfiguration) → `400` Klartext |

Das Flag wird im Service geprüft; `FeatureDisabledException` bildet der `GlobalExceptionHandler`
bereits auf `403` ab. Rumpf im Fehlerfall immer **Klartext**, nie ein Objekt — sonst zeigt die
Maske `[object Object]`.

---

## Phase 9: ArchUnit

**Datei:** `backend-service/src/test/java/ch/nacht/architecture/ArchitectureTest.java`

1. `everyEntityMustHaveOrgId()`: `Preiszeitreihe` **namentlich** ausnehmen — als `Set`-Konstante mit
   Begründung im Javadoc (Marktdaten, für alle Mandanten identisch, Job ohne Mandantenkontext), nach
   dem Muster von `ohneMandantenzugriff`. **Nicht** über ein Namensmuster: Ein neu hinzukommendes
   Entity muss weiterhin automatisch erfasst sein.
2. Neue Regel `preiszeitreiheServicesMustCheckFeatureFlag()`: Jede öffentliche Methode der Klassen
   in `..service..` mit Präfix `Preiszeitreihe` ruft `pruefeFeatureFlag()`; `PreiszeitreiheDownloadJob`
   ist namentlich ausgenommen (Phase 7).

---

## Phase 10: Übersetzungen

**Datei:** `db/migration/V130__Add_Preiszeitreihe_Translations.sql` — alle Einträge mit
`ON CONFLICT (key) DO NOTHING`.

| Key | DE (Kurzform) |
|---|---|
| `FEATURE_FLAG_PREISZEITREIHE` | Preiszeitreihe der dynamischen Einspeisepreise |
| `EINSPEISEPREISE` | Einspeisepreise |
| `PREISE_HERUNTERLADEN` | Herunterladen |
| `PREISE_HERUNTERGELADEN` | Preise aktualisiert ({0} neu, {1} geändert, Stand {2}) |
| `PREISE_ABRUF_FEHLGESCHLAGEN` | Die Preise konnten nicht abgerufen werden |
| `KEINE_PREISE_VORHANDEN` | Für den gewählten Zeitraum sind keine Preise vorhanden |
| `SPANNE_TAG` / `SPANNE_WOCHE` / `SPANNE_MONAT` | Tag / Woche / Monat |
| `ZURUECK` / `VOR` | Zurück / Vor |
| `PREIS_CHF_KWH` | Preis (CHF/kWh) |
| `DIAGRAMM_LAEDT` | Diagramm wird geladen … |
| `DIAGRAMM_NICHT_LADBAR` | Das Diagramm konnte nicht geladen werden |
| `SYSTEMMELDUNG_KATEGORIE_PREISZEITREIHE` | Preiszeitreihe |
| `PREISZEITREIHE_ABRUF_FEHLER` | Abruf der Einspeisepreise fehlgeschlagen: {0} |
| `PREISZEITREIHE_WERTE_UEBERSPRUNGEN` | {0} Preisintervall(e) übersprungen ({1}) |

Vorhandene Keys (`VON_DATUM`, `BIS_DATUM`, `ANZEIGEN`, `LADE_DATEN`) werden wiederverwendet — vor
dem Anlegen prüfen.

---

## Phase 11–16: Frontend

### Phase 11: Abhängigkeit

`cd frontend-service && npm i echarts`. Danach `/lizenzen` prüfen (Apache-2.0 erscheint in der
Liste) — `Specs/SBOM.md`.

### Phase 12: Model + Service

`models/preiszeitreihe.model.ts`:

```ts
export interface PreiszeitreihePunkt { zeit: string; preis: number; }
export interface PreiszeitreiheDownload {
  abgerufen: number; neu: number; aktualisiert: number;
  uebersprungen: number; publikation: string | null;   // null: Quelle nannte keinen Stand
}
export type Spanne = 'TAG' | 'WOCHE' | 'MONAT' | 'FREI';
```

`services/preiszeitreihe.service.ts` nach `tarif.service.ts`:
`private apiUrl = `${getRuntimeConfig().apiBaseUrl}/api/preiszeitreihe`;` mit `getPunkte(von, bis)`
und `download()`.

> `publikation` ist `string | null` — das Backend schickt `null`, nicht `undefined`. Prüfungen im
> Template mit `== null` / `!= null`.

### Phase 13: Komponente (TypeScript)

`components/preiszeitreihe-chart/preiszeitreihe-chart.component.ts`

* Zustand: `spanne`, `von`, `bis`, `punkte`, `laedt`, `message`/`messageType`.
* `ngOnInit`: `spanne = 'TAG'`, `von = bis = heute`, laden.
* `setzeSpanne(s)`: berechnet `von`/`bis` (Tag = heute; Woche = Mo–So der aktuellen Woche; Monat =
  1. bis letzter Tag) und lädt.
* `blaettere(richtung: -1 | 1)`: verschiebt um die **Länge der aktuellen Spanne** (bei `FREI` um
  `bis - von + 1` Tage) und lädt.
* `onDatumChange()`: `spanne = 'FREI'`, laden.
* `subscribe({ next, error })`-Objektsyntax; Erfolgsmeldung nach 5 s weg, Fehlermeldung bleibt
  (`showMessage`-Muster der List-Komponenten, inkl. Timer-Guard `if (this.message === message)`).
* **Dynamischer ECharts-Import**, einmalig gemerkt:

```ts
private echarts?: typeof import('echarts/core');

private async ladeBibliothek(): Promise<void> {
  if (this.echarts) { return; }
  // Dynamisch, nicht am Dateikopf: /tarife ist eine eager Route - ein statischer Import
  // landete im Initial-Bundle und jede Seite der Anwendung lüde ECharts mit (NFR-1).
  const [core, charts, komponenten, renderer] = await Promise.all([
    import('echarts/core'), import('echarts/charts'),
    import('echarts/components'), import('echarts/renderers')
  ]);
  core.use([charts.LineChart, komponenten.GridComponent, komponenten.TooltipComponent,
            komponenten.DataZoomComponent, renderer.CanvasRenderer]);
  this.echarts = core;
}
```

* Diagramm-Optionen: `series.type = 'line'`, `step: 'end'`, `connectNulls: false`,
  `dataZoom: [{ type: 'inside' }, { type: 'slider' }]`, Tooltip-Formatter über
  `formatSwissNumber(wert, 5)` aus `utils/number-utils.ts` (**keine** `toLocaleString`).
* Farben aus den Design-Tokens (CSS-Variablen über `getComputedStyle`), damit Dark Mode stimmt.
* `ngOnDestroy`: `dispose()` der Instanz und `ResizeObserver` abmelden.

### Phase 14: Template + CSS

**Zuerst das Design System prüfen** (`design-system/src/components/`) — vorhanden und zu verwenden:
`zev-panel`, `zev-panel--chart`, `zev-panel__title`, `zev-panel__content`, `zev-button`,
`zev-button--primary/--secondary`, `zev-button-group`, `zev-date-range-row`, `zev-form-group`,
`zev-input`, `zev-message--success/--error`. Icons: `download`, `chevron-left`, `chevron-right`
(alle im Registry vorhanden).

Neu nur, falls unvermeidbar: Höhe des Diagramm-Containers — zuerst prüfen, ob
`zev-panel--chart` das schon regelt; wiederverwendbare Styles gehören ins Design System (plus
Showcase-Eintrag), nicht in die Komponente.

### Phase 15: Einbindung

In `tarif-list.component.html` nach der Tarifliste, **vor** `<app-tarif-form>`:

```html
@if (!showForm) {
  <div *appFeature="'PREISZEITREIHE'">
    <app-preiszeitreihe-chart></app-preiszeitreihe-chart>
  </div>
}
```

Das `@if (!showForm)` verhindert, dass Diagramm und Formular gleichzeitig um die Aufmerksamkeit
konkurrieren, und spart das Neuaufbauen des Diagramms beim Bearbeiten eines Tarifs.

### Phase 16: Bundle-Kontrolle

`npx ng build` und die Grössen vergleichen: `main-*.js` darf um **< 20 kB** wachsen, ECharts muss in
einem eigenen Chunk stehen. Ausgangswert vor der Änderung notieren (~950 kB gegen
`maximumWarning: 1mb` in `angular.json`).

---

## Validierungen

### Backend

| Regel | Ort | Reaktion |
|---|---|---|
| `von`/`bis` gesetzt und `von <= bis` | Controller `GET` | `400` Klartext |
| Spanne ≤ 366 Tage | Controller `GET` | `400` Klartext |
| Feature-Flag `PREISZEITREIHE` aktiv | Service (jede öffentliche Methode) | `403` (`FeatureDisabledException`) |
| Permission `tarife:manage` | `@PreAuthorize` auf Klassenebene | `403` |
| Quell-URL konfiguriert | Service `download()` | `400` Klartext |
| Antwort lesbar, `prices` vorhanden | Service | `502` + Systemmeldung |
| `prices` ≤ 10'000 Einträge | Service, **vor** dem Schreiben | `502` + Systemmeldung, nichts gespeichert |
| Alle `unit == "CHF_kWh"` | Service, **vor** dem Schreiben | `502` + Systemmeldung, nichts gespeichert |
| Eintrag vollständig (`start`, `value`, `feed_in` nicht leer, `end > start`) | Service | Eintrag überspringen, zählen, Systemmeldung `…_UEBERSPRUNGEN` |
| `preis >= 0`, `zeit_von < zeit_bis` | DB-Constraints | letzte Verteidigungslinie; Verstoss → `409` (Handler) |
| Kurzgrund ≤ 500 Zeichen | Service vor `erfasse` | kürzen |

### Frontend

| Regel | Ort | Reaktion |
|---|---|---|
| `von <= bis` | Komponente vor dem Laden | Fehlermeldung, kein HTTP-Aufruf |
| Spanne ≤ 366 Tage | Komponente | Fehlermeldung, kein HTTP-Aufruf |
| Beide Datumsfelder gefüllt | Komponente | „Anzeigen"/Laden gesperrt |
| Leere Antwort | Template | `KEINE_PREISE_VORHANDEN` statt leerem Diagramm |
| Bibliothek nicht ladbar | Komponente | `DIAGRAMM_NICHT_LADBAR`, Seite bleibt bedienbar |
| Fehlerantwort des Servers | Komponente | `error.error` (Klartext) oder Fallback-Key |

---

## Offene Punkte / Annahmen

Aus `Specs/Preiszeitreihe.md` §8 übernommen, alle mit Annahme — keine blockiert die Umsetzung:

1. **Zwei Diagramm-Bibliotheken.** chart.js bleibt für `messwerte-chart`; eine Migration ist ein
   eigenes Vorhaben. Durch den dynamischen Import trägt der Initial-Load nichts davon.
2. **Ein Anbieter.** Keine Spalte „Quelle"; ein zweiter Anbieter würde Teil des
   Eindeutigkeitsschlüssels und braucht dann eine Migration.
3. **Keine Historie in der Quelle.** Kein „Nachladen von–bis"; Lücken vor der Inbetriebnahme bleiben.
4. **Ein Lauf um 02:00.** Ob das den Vortag vollständig erfasst, zeigt der Betrieb; ein zweiter Lauf
   ist eine Zeile Konfiguration.
5. **Keine Historisierung von Preiskorrekturen.** Der Upsert überschreibt; `publikation` und
   `aktualisiert_am` dokumentieren die Herkunft.
6. **Nutzungsbedingungen der BKW-API ungeprüft** (Rate Limits, Verfügbarkeit, Rechte) — vor dem
   produktiven Dauerbetrieb klären. Betriebsrisiko, kein Umsetzungsrisiko.

Zusätzliche Annahmen dieses Plans:

7. **Zählung neu/aktualisiert** über einen Vorab-Lesevorgang der Spanne (Phase 2). Ein
   `RETURNING`-basiertes Zählen wäre exakter, aber Spring Data liefert bei `@Modifying`-Upserts
   keinen brauchbaren Rückgabewert.
8. **Keine serverseitige Verdichtung.** Auch die Monatsansicht liefert 15-Minuten-Werte; `dataZoom`
   übernimmt die Darstellung.
9. **`@if (!showForm)`** um das Diagramm (Phase 15) — Annahme über die gewünschte Ergonomie, leicht
   umzustellen, falls das Diagramm auch neben dem Formular sichtbar bleiben soll.

---

## Abweichungen und Befunde der Umsetzung

Festgehalten, weil sie den Plan korrigieren — nicht als Notiz am Rand:

1. **Zwei Services statt einem** (Phasen 6/7). `PreiszeitreiheService` ist die Maske-zugewandte
   Seite und prüft in jeder öffentlichen Methode das Feature-Flag; `PreiszeitreiheAbrufService`
   beschafft und schreibt. Der Grund ist zwingend: Die Flag-Prüfung braucht
   `organizationContextService.getCurrentOrgId()`, und im geplanten Job gibt es keinen angemeldeten
   Benutzer. Ein einziger Service hätte entweder die Prüfung aufgeben oder im Job scheitern müssen.
   Die ArchUnit-Regel nimmt `PreiszeitreiheAbrufService` und den Job namentlich aus — dieselbe
   Konstruktion wie `NkBerechnungService` bei der Nebenkostenabrechnung.
2. **`BkwTariffsResponseDTO`** statt `BkwTariffsResponse`: Die Regel `dtosShouldEndWithDTO` verlangt
   das Suffix für alle Klassen in `..dto..` (verschachtelte Records sind als Member-Klassen
   ausgenommen).
3. **Neuer Design-System-Baustein `zev-toggle-button`** (Phase 14). Für Tag/Woche/Monat gab es keine
   passende Klasse; `zev-quarter-button` trifft die Optik genau, heisst aber nach den Quartalen.
   Die Deklarationen sind jetzt **geteilt** (`.zev-quarter-button, .zev-toggle-button`) statt
   kopiert, samt Eintrag im Showcase.
4. **Ein Übersetzungs-Key mehr** als geplant: `PREISE_ZEITRAUM_VERTAUSCHT` für die
   frontendseitige Prüfung. `FEHLER_BEIM_LADEN_DER_DATEN` und `LADE_DATEN` waren schon vorhanden und
   werden wiederverwendet.
5. **`SystemmeldungService.erfasse` kürzt `parameter` nicht.** Die Methode `kuerze()` existiert, wird
   aber nur von `erfasseAudit` verwendet. Der Abruf-Service kürzt deshalb selbst auf 500 Zeichen.
   Die zentrale Lücke bleibt bestehen — ein Aufrufer mit langem Text lässt `erfasse` auflaufen. Das
   zu schliessen gehört nicht in dieses Feature, ist aber eine Zeile in `erfasse`.
6. **Bundle-Zahlen gemessen** (Phase 16), Produktionsbau je mit und ohne die Änderung:

   | | `main-*.js` | Initial total |
   |---|---|---|
   | vorher | 974.06 kB | 1.04 MB |
   | nachher | 983.46 kB | 1.05 MB |

   Zuwachs **+9.4 kB** — die Vorgabe (< 20 kB) ist erfüllt. ECharts liegt in eigenen, **lazy**
   Chunks (`charts` 675 kB, `components` 643 kB, `renderers` 84 kB, `core` 9 kB) und wird erst beim
   Öffnen des Diagramms geladen.

   **Vorbestehend:** Die Budget-Warnung von Angular (`maximumWarning: 1mb`) trat schon **vor** dieser
   Änderung auf (41 kB über Budget, jetzt 52 kB). Sie ist eine Warnung, kein Fehler (`maximumError`
   liegt bei 2 mb) — aber sie gehört aufgeräumt, unabhängig von diesem Feature.
7. **Nicht umgesetzt:** Phasen 18–20 (Tests) laufen über die Commands `/3_backend-tests`,
   `/4_frontend-unit-tests` und `/5_e2e-tests`; die Umsetzung erstellt bewusst keine Tests.

---

## Phase 21: Steuerzeile und Darstellungsart (Ergänzung)

Nachgezogene Anforderung: alle Bedienelemente auf gleicher Höhe, dazu eine Umschaltung der
Darstellung zwischen **Datum bis** und **Herunterladen**.

* **Eine Zeile statt zwei.** Bereichsauswahl, Pfeile, Datumsfelder, Darstellungsumschaltung und
  „Herunterladen" stehen jetzt in **einer** `zev-date-range-row`. Die Klasse liefert die untere
  Ausrichtung und 38 px Höhe für `.zev-input` und `.zev-button` bereits — für
  `.zev-toggle-button` fehlte die Regel und wurde im Design System **ergänzt** (`form.css`), statt
  sie in der Komponente zu duplizieren. Ohne sie sass die Auswahlreihe sichtbar tiefer als das
  Feld daneben.
* **Gruppierung:** `.preiszeitreihe__gruppe` setzt zusammengehörende Toggle-Buttons dichter
  beieinander als die Gruppen untereinander. Sonst wäre nicht erkennbar, dass Tag/Woche/Monat eine
  Auswahl bilden und die Pfeile eine andere.
* **Linie / Balken** als zwei Toggle-Buttons statt eines einzelnen Umschalters: So ist die aktive
  Darstellung sichtbar, ohne die Beschriftung interpretieren zu müssen — dasselbe Muster wie eine
  Zeile darüber.
* **Kein Nachladen beim Umschalten** (`setzeDarstellung`): Die Daten liegen vor, es ändert sich nur
  ihre Darstellung. Balken sind mit `barMaxWidth: 24` begrenzt — über einen Monat liegen bis zu
  2'976 Balken nebeneinander.
* **`V131__Add_Preiszeitreihe_Darstellung_Translations.sql`** für `DARSTELLUNG_LINIE` und
  `DARSTELLUNG_BALKEN`. Eigene Migration, **weil V130 bereits ausgeführt war** (per `zev-db`
  geprüft: 129 und 130 stehen mit `success = true` in `flyway_schema_history`) — eine Änderung hätte
  die Checksum-Prüfung beim nächsten Start gebrochen.

### Nachtrag zu Phase 21: zwei Fehler in der Darstellung

Beim Ausprobieren gemeldet — beide behoben:

1. **Balken zeichneten nichts.** `core.use([...])` registrierte nur `charts.LineChart`. ECharts
   meldet einen **nicht registrierten Serientyp nicht als Fehler**: `type: 'bar'` wird stillschweigend
   ignoriert, die Fläche bleibt leer. Jetzt ist `charts.BarChart` mitregistriert. Lehre für weitere
   Serientypen in diesem Diagramm: Registrierung und `type` gehören zusammen geändert.
2. **Die Linie war ein Flächendiagramm.** Das `areaStyle` auf der Linienserie füllte die Fläche unter
   der Kurve. Entfernt — eine gefüllte Fläche liest sich als Summe über die Zeit, und aufsummierte
   Preise sind sinnlos. Der Farb-Token `--color-primary-light` wird damit nicht mehr gebraucht und
   ist aus `farben()` verschwunden.

Keine Migration nötig; die Übersetzungen aus `V131` bleiben unverändert.
