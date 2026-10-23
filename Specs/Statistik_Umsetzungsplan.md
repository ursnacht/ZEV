# Umsetzungsplan: Statistikseite

> **Seit 19.10.2026 zusammengeführt:** Teil A ist der bisherige Plan der Statistikseite. Teil B war
> `Statistik-Kennzahlen_Umsetzungsplan.md`, Teil C `Statistik-PDF_Summen-pro-Einheit_Umsetzungsplan.md`
> (beide unverändert übernommen, Überschriften eine Ebene tiefer). Die FR-Nummern in Teil B beziehen
> sich auf die frühere `Specs/Statistik-Kennzahlen.md`; heute stehen sie in `Specs/Statistik.md` als
> FR-12 bis FR-16 (FR-1 → FR-12, FR-2 → FR-13, FR-2a → FR-13a, FR-3 → FR-14, FR-4 → FR-15,
> FR-5 → FR-16; „Spec §8“ → Anhang A.9).

# Teil A – Statistikseite

### Zusammenfassung

Die Statistikseite bietet einen Überblick über Messdaten und Verteilungen. Sie zeigt pro Monat im gewählten Datumsbereich die Summen aller Producer und Consumer an und vergleicht diese. Datenlücken werden mit einem Ampel-System und aufklappbaren Details visualisiert.

---

### Phase 1: Backend - Statistik-Service und API

#### 1.1 Neues DTO für Statistik-Daten erstellen

**Datei:** `backend-service/src/main/java/ch/nacht/dto/StatistikDTO.java`

```java
public class StatistikDTO {
    private LocalDate messwerteBisDate;        // Letztes Datum mit Daten
    private boolean datenVollstaendig;          // Keine Lücken vorhanden
    private List<String> fehlendeEinheiten;     // Einheiten ohne Daten
    private List<LocalDate> fehlendeTage;       // Tage ohne Daten
    private List<MonatsStatistikDTO> monate;    // Pro-Monat-Statistiken
}

public class MonatsStatistikDTO {
    private int jahr;
    private int monat;
    private LocalDate von;
    private LocalDate bis;
    private boolean datenVollstaendig;
    private List<String> fehlendeEinheiten;
    private List<LocalDate> fehlendeTage;

    // Summen
    private Double summeProducerTotal;          // Summe A
    private Double summeConsumerTotal;          // Summe B
    private Double summeProducerZev;            // Summe C
    private Double summeConsumerZev;            // Summe D
    private Double summeConsumerZevCalculated;  // Summe E

    // Vergleiche
    private boolean summenCDGleich;
    private Double differenzCD;                 // Differenz in kWh
    private boolean summenCEGleich;
    private Double differenzCE;
    private boolean summenDEGleich;
    private Double differenzDE;

    // Tage mit Abweichungen
    private List<TagMitAbweichungDTO> tageAbweichungen;
}

public class TagMitAbweichungDTO {
    private LocalDate datum;
    private String abweichungstyp;  // "C!=D", "C!=E", "D!=E"
    private Double differenz;
}
```

#### 1.2 Repository-Methoden erweitern

**Datei:** `backend-service/src/main/java/ch/nacht/repository/MesswerteRepository.java`

Neue Query-Methoden:
- `findMaxZeit()` - Letztes Datum mit Messwerten
- `findDistinctDatesInRange(dateFrom, dateTo)` - Alle Tage mit Daten
- `sumTotalByEinheitTypAndZeitBetween(typ, von, bis)` - Summe total pro Typ
- `sumZevByEinheitTypAndZeitBetween(typ, von, bis)` - Summe zev pro Typ
- `sumZevCalculatedByEinheitTypAndZeitBetween(typ, von, bis)` - Summe zev_calculated

#### 1.3 StatistikService erstellen

**Datei:** `backend-service/src/main/java/ch/nacht/service/StatistikService.java`

Methoden:
- `getStatistik(LocalDate von, LocalDate bis)` - Hauptmethode
- `ermittleLetztesMessdatum()` - Letztes Datum mit Daten
- `pruefeDatenVollstaendigkeit(von, bis)` - Prüft auf Lücken
- `berechneMonatsStatistik(jahr, monat)` - Statistik für einen Monat
- `vergleicheSummen(summeC, summeD, summeE)` - Vergleicht und berechnet Differenzen

#### 1.4 StatistikController erstellen

**Datei:** `backend-service/src/main/java/ch/nacht/controller/StatistikController.java`

```java
@RestController
@RequestMapping("/api/statistik")
@PreAuthorize("hasRole('zev')")
public class StatistikController {

    @GetMapping
    public ResponseEntity<StatistikDTO> getStatistik(
        @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate von,
        @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate bis
    )

    @GetMapping("/letztes-datum")
    public ResponseEntity<LocalDate> getLetztesMessdatum()
}
```

---

### Phase 2: Frontend - Statistik-Komponente

#### 2.1 Statistik-Models erstellen

**Datei:** `frontend-service/src/app/models/statistik.model.ts`

TypeScript-Interfaces analog zu den Backend-DTOs.

#### 2.2 Statistik-Service erstellen

**Datei:** `frontend-service/src/app/services/statistik.service.ts`

```typescript
@Injectable({ providedIn: 'root' })
export class StatistikService {
    getStatistik(von: string, bis: string): Observable<StatistikDTO>
    getLetztesMessdatum(): Observable<string>
}
```

#### 2.3 Statistik-Komponente erstellen

**Dateien:**
- `frontend-service/src/app/components/statistik/statistik.component.ts`
- `frontend-service/src/app/components/statistik/statistik.component.html`
- `frontend-service/src/app/components/statistik/statistik.component.css`

**Aufbau der Komponente:**

1. **Filter-Bereich** (wie Messwerte-Seite)
   - Datumsbereich-Auswahl (von/bis)
   - Default: Vorheriger Monat
   - "Anzeigen"-Button

2. **Übersichts-Panel**
   - "Messwerte vorhanden bis: [Datum]"
   - Ampel-Status für Datenvollständigkeit
   - Aufklappbare Liste fehlender Einheiten/Tage

3. **Monats-Karten** (pro Monat im Bereich)
   - Header: "Monat Jahr (von - bis)"
   - Ampel-Status für Datenvollständigkeit
   - Summen-Tabelle:
     | Beschreibung | Wert |
     |--------------|------|
     | Produktion (Producer total) | X kWh |
     | Verbrauch Total (Consumer total) | X kWh |
     | ZEV Producer | X kWh |
     | ZEV Consumer | X kWh |
     | ZEV Consumer berechnet | X kWh |
   - Vergleichs-Bereich:
     - C = D: ✓/✗ (Differenz: X kWh)
     - C = E: ✓/✗ (Differenz: X kWh)
     - D = E: ✓/✗ (Differenz: X kWh)
   - Aufklappbare Liste: Tage mit Abweichungen

#### 2.4 Visuelle Darstellung

**Ampel-System:**
- Grün (`zev-status--success`): Alle Daten vollständig
- Gelb (`zev-status--warning`): Teilweise Daten fehlen
- Rot (`zev-status--error`): Keine Daten vorhanden

**Vergleichs-Darstellung:**
- Gleich: Grüner Haken (✓)
- Ungleich: Rotes X (✗) + Differenz in kWh

**Design-System-Klassen verwenden:**
- `zev-panel` für Monats-Karten
- `zev-form-group`, `zev-input` für Filter
- `zev-button--primary` für Buttons
- `zev-message--success/error` für Statusanzeigen

---

### Phase 3: Design-System erweitern

#### 3.1 Status-Indikatoren hinzufügen

**Datei:** `design-system/src/components/status.css` (neu)

```css
.zev-status-indicator {
    display: inline-flex;
    align-items: center;
    gap: 8px;
}

.zev-status-dot {
    width: 12px;
    height: 12px;
    border-radius: 50%;
}

.zev-status-dot--success { background-color: var(--color-success); }
.zev-status-dot--warning { background-color: var(--color-warning); }
.zev-status-dot--error { background-color: var(--color-error); }
```

#### 3.2 Aufklappbare Details-Komponente

**Datei:** `design-system/src/components/collapsible.css` (neu)

```css
.zev-collapsible { ... }
.zev-collapsible__header { ... }
.zev-collapsible__content { ... }
.zev-collapsible--open .zev-collapsible__content { display: block; }
```

---

### Phase 4: Routing und Navigation

#### 4.1 Route hinzufügen

**Datei:** `frontend-service/src/app/app.routes.ts`

```typescript
{
    path: 'statistik',
    component: StatistikComponent,
    canActivate: [AuthGuard],
    data: { roles: ['zev'] }
}
```

#### 4.2 Navigation erweitern

**Datei:** `frontend-service/src/app/components/navbar/navbar.component.html`

Neuen Menüpunkt "Statistik" hinzufügen.

---

### Phase 5: Internationalisierung

#### 5.1 Flyway-Migration für Übersetzungen

**Datei:** `backend-service/src/main/resources/db/migration/V[next]__Add_Statistik_Translations.sql`

```sql
INSERT INTO translation (key, deutsch, englisch) VALUES
-- Seitentitel
('STATISTIK', 'Statistik', 'Statistics'),
('STATISTIK_UEBERSICHT', 'Statistik-Übersicht', 'Statistics Overview'),

-- Filter
('ZEITRAUM_WAEHLEN', 'Zeitraum wählen', 'Select Period'),
('VORHERIGER_MONAT', 'Vorheriger Monat', 'Previous Month'),

-- Übersicht
('MESSWERTE_VORHANDEN_BIS', 'Messwerte vorhanden bis', 'Measurements available until'),
('DATEN_VOLLSTAENDIG', 'Daten vollständig', 'Data complete'),
('DATEN_UNVOLLSTAENDIG', 'Daten unvollständig', 'Data incomplete'),
('FEHLENDE_EINHEITEN', 'Fehlende Einheiten', 'Missing units'),
('FEHLENDE_TAGE', 'Fehlende Tage', 'Missing days'),

-- Summen
('PRODUKTION_TOTAL', 'Produktion (Total)', 'Production (Total)'),
('VERBRAUCH_TOTAL', 'Verbrauch (Total)', 'Consumption (Total)'),
('ZEV_PRODUCER', 'ZEV Producer', 'ZEV Producer'),
('ZEV_CONSUMER', 'ZEV Consumer', 'ZEV Consumer'),
('ZEV_CONSUMER_BERECHNET', 'ZEV Consumer (berechnet)', 'ZEV Consumer (calculated)'),

-- Vergleiche
('SUMMEN_VERGLEICH', 'Summen-Vergleich', 'Sum Comparison'),
('DIFFERENZ', 'Differenz', 'Difference'),
('GLEICH', 'Gleich', 'Equal'),
('UNGLEICH', 'Ungleich', 'Not equal'),
('TAGE_MIT_ABWEICHUNGEN', 'Tage mit Abweichungen', 'Days with deviations'),

-- Status
('STATUS_VOLLSTAENDIG', 'Alle Daten vorhanden', 'All data available'),
('STATUS_TEILWEISE', 'Teilweise Daten fehlen', 'Some data missing'),
('STATUS_FEHLEND', 'Keine Daten vorhanden', 'No data available'),

-- Einheiten
('KWH', 'kWh', 'kWh');
```

---

### Phase 6: Logging und Fehlerbehandlung

#### 6.1 Backend-Logging

- INFO: Statistik-Abfrage mit Datumsbereich
- DEBUG: Berechnete Summen pro Monat
- WARN: Datenlücken gefunden
- ERROR: Datenbankfehler bei Abfrage

#### 6.2 Frontend-Fehlerbehandlung

- Fehlermeldung bei API-Fehlern anzeigen
- Loading-Indikator während Datenladung
- Leerer Zustand wenn keine Daten vorhanden

---

### Technische Details

#### Datenbank-Abfragen (optimiert)

Für die Summenberechnung pro Monat:
```sql
SELECT
    SUM(CASE WHEN e.typ = 'PRODUCER' THEN m.total ELSE 0 END) as summe_producer_total,
    SUM(CASE WHEN e.typ = 'CONSUMER' THEN m.total ELSE 0 END) as summe_consumer_total,
    SUM(CASE WHEN e.typ = 'PRODUCER' THEN m.zev ELSE 0 END) as summe_producer_zev,
    SUM(CASE WHEN e.typ = 'CONSUMER' THEN m.zev ELSE 0 END) as summe_consumer_zev,
    SUM(CASE WHEN e.typ = 'CONSUMER' THEN m.zev_calculated ELSE 0 END) as summe_consumer_zev_calc
FROM messwerte m
JOIN einheit e ON m.einheit_id = e.id
WHERE m.zeit >= :von AND m.zeit < :bis
```

#### Toleranz bei Summenvergleichen

Da Gleitkommazahlen verglichen werden, wird eine Toleranz von 0.0009 kWh verwendet:
```java
private static final double TOLERANZ = 0.0009;

private boolean sindGleich(Double a, Double b) {
    return Math.abs(a - b) < TOLERANZ;
}
```

---

### Phase 7: Caching

#### 7.1 Backend-Caching mit Spring Cache

**Konfiguration:** `backend-service/src/main/java/ch/nacht/config/CacheConfig.java`

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("statistik");
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(100));
        return cacheManager;
    }
}
```

**Abhängigkeit hinzufügen:** `pom.xml`
```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

**Service-Methoden mit Cache-Annotationen:**
```java
@Cacheable(value = "statistik", key = "#von.toString() + '-' + #bis.toString()")
public StatistikDTO getStatistik(LocalDate von, LocalDate bis) { ... }

@CacheEvict(value = "statistik", allEntries = true)
public void invalidateCache() { ... }
```

**Cache-Invalidierung:** Bei Upload neuer Messwerte oder Neuberechnung der Solarverteilung wird der Cache geleert.

---

### Phase 8: PDF-Export

#### 8.1 Backend - PDF-Generierung

**Abhängigkeit hinzufügen:** `pom.xml`
```xml
<dependency>
    <groupId>com.openhtmltopdf</groupId>
    <artifactId>openhtmltopdf-pdfbox</artifactId>
    <version>1.0.10</version>
</dependency>
```

**Service:** `backend-service/src/main/java/ch/nacht/service/StatistikPdfService.java`

```java
@Service
public class StatistikPdfService {

    public byte[] generatePdf(StatistikDTO statistik, String sprache) {
        // 1. HTML-Template mit Thymeleaf oder String-Template rendern
        // 2. HTML zu PDF konvertieren mit OpenHTMLToPDF
        // 3. byte[] zurückgeben
    }
}
```

**Controller-Endpoint:**
```java
@GetMapping("/export/pdf")
public ResponseEntity<byte[]> exportPdf(
    @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate von,
    @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate bis,
    @RequestParam(defaultValue = "de") String sprache
) {
    StatistikDTO statistik = statistikService.getStatistik(von, bis);
    byte[] pdf = statistikPdfService.generatePdf(statistik, sprache);

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=statistik.pdf")
        .contentType(MediaType.APPLICATION_PDF)
        .body(pdf);
}
```

#### 8.2 Frontend - Export-Button

**Statistik-Komponente erweitern:**
```typescript
exportPdf(): void {
    const url = `/api/statistik/export/pdf?von=${this.dateFrom}&bis=${this.dateTo}&sprache=${this.currentLanguage}`;
    window.open(url, '_blank');
}
```

**Template:**
```html
<button class="zev-button zev-button--secondary" (click)="exportPdf()">
    {{ 'PDF_EXPORTIEREN' | translate }}
</button>
```

#### 8.3 PDF-Layout

Das PDF enthält:
- Header mit Titel "Statistik-Report" und Datumsbereich
- Übersicht: Messwerte vorhanden bis, Datenvollständigkeit
- Pro Monat eine Sektion mit:
  - Monat/Jahr als Überschrift
  - Summen-Tabelle
  - Vergleichs-Tabelle mit Differenzen
  - Liste der Tage mit Abweichungen (falls vorhanden)
- Footer mit Generierungsdatum

#### 8.4 Zusätzliche Übersetzungen für PDF

```sql
INSERT INTO translation (key, deutsch, englisch) VALUES
('PDF_EXPORTIEREN', 'Als PDF exportieren', 'Export as PDF'),
('STATISTIK_REPORT', 'Statistik-Report', 'Statistics Report'),
('ZEITRAUM', 'Zeitraum', 'Period'),
('GENERIERT_AM', 'Generiert am', 'Generated on'),
('SEITE', 'Seite', 'Page');
```

---

### Zusammenfassung der zu erstellenden/ändernden Dateien (aktualisiert)

#### Neue Dateien

| Datei | Beschreibung |
|-------|--------------|
| `backend-service/.../dto/StatistikDTO.java` | DTOs für Statistik-Daten |
| `backend-service/.../dto/MonatsStatistikDTO.java` | DTO für Monats-Statistik |
| `backend-service/.../dto/TagMitAbweichungDTO.java` | DTO für Tages-Abweichungen |
| `backend-service/.../service/StatistikService.java` | Statistik-Geschäftslogik |
| `backend-service/.../service/StatistikPdfService.java` | PDF-Generierung |
| `backend-service/.../controller/StatistikController.java` | REST-API |
| `backend-service/.../config/CacheConfig.java` | Cache-Konfiguration |
| `backend-service/.../db/migration/V[n]__Add_Statistik_Translations.sql` | Übersetzungen |
| `frontend-service/.../models/statistik.model.ts` | TypeScript-Models |
| `frontend-service/.../services/statistik.service.ts` | API-Service |
| `frontend-service/.../components/statistik/statistik.component.ts` | Komponente |
| `frontend-service/.../components/statistik/statistik.component.html` | Template |
| `frontend-service/.../components/statistik/statistik.component.css` | Styles |
| `design-system/src/components/status.css` | Status-Indikatoren |
| `design-system/src/components/collapsible.css` | Aufklappbare Elemente |

#### Zu ändernde Dateien

| Datei | Änderung |
|-------|----------|
| `backend-service/pom.xml` | Caffeine + OpenHTMLToPDF Abhängigkeiten |
| `backend-service/.../repository/MesswerteRepository.java` | Neue Query-Methoden |
| `backend-service/.../service/MesswerteService.java` | Cache-Invalidierung bei Upload/Berechnung |
| `frontend-service/src/app/app.routes.ts` | Route hinzufügen |
| `frontend-service/.../components/navbar/navbar.component.html` | Menüpunkt hinzufügen |
| `design-system/src/components/index.css` | Neue CSS-Dateien importieren |

---

### Entscheidungen (festgelegt)

| Thema | Entscheidung |
|-------|--------------|
| Toleranzwert für Summenvergleiche | 0.0009 kWh |
| Caching | Ja, mit Caffeine (15 Min TTL) |
| Export-Format | PDF |

---

### Nachträgliche Ergänzungen

#### Vorangehendes Quartal beim Öffnen vorselektiert (Frontend)

Beim Öffnen von `/statistik` ist neu das vorangehende Quartal (statt des Vormonats) als Zeitraum vorbelegt.

- **`statistik.component.ts`**: `setDefaultDateRange()` berechnet das Vorquartal relativ zum aktuellen Datum (Jahreswechsel: im Q1 wird Q4 des Vorjahres gesetzt) und belegt `dateFrom`/`dateTo` mit erstem/letztem Tag des Quartals.
- **Quartal-Button aktiv**: keine Änderung am `QuarterSelectorComponent` nötig – via bestehendem `[selectedVon]`/`[selectedBis]`-Binding.
- **Tests**: `statistik.component.spec.ts` – Initialisierungs-Tests mit `jasmine.clock().mockDate()` (Vorquartal inkl. Jahreswechsel-Fall). E2E in `tests/quarter-selector.spec.ts` (Abschnitt "on Statistik page"): Default-Daten entsprechen dem Vorquartal, Vorquartal-Button hat `zev-quarter-button--active`.
- **Keine neuen Texte/Backend**: rein clientseitig.

#### kWh-Werte rechtsbündig (Design System)

Die kWh-Werte in den Statistik-Tabellen erschienen im Web linksbündig, obwohl `.zev-table__number` `text-align: right` setzt. Ursache: `.zev-table th, .zev-table td { text-align: left }` (`table/table.css`, Spezifität 0,1,1) überschrieb `.zev-table__number` (0,1,0).

- **`design-system/src/components/statistik/statistik.css`**: Regel auf höhere Spezifität angehoben (`.zev-table th.zev-table__number, .zev-table td.zev-table__number { text-align: right }`); anschliessend `npm run build` im Design System.
- **Keine Frontend-Code-Änderung**: die Tabellenzellen tragen bereits die Klasse `.zev-table__number`.
- **Keine neuen Texte/Backend/Tests**: rein visuell, konsistent mit der PDF-Ausgabe.

#### Erklärender Hinweis (`title`) je visualisiertem Wert

Die Zeilen der Summen-Tabelle mit Balken tragen neu einen erklärenden Tooltip – analog zu den
Statistik-Kennzahlen (`Specs/Statistik-Kennzahlen.md` FR-3.5, dort `<tr [title]="kz.hintKey | translate">`).

- **`statistik.component.html`**: `[title]="'<KEY>_HINWEIS' | translate"` auf allen sieben `<tr>` der
  Tabelle `.zev-table--bars` (Produktion Total, Verbrauch Total, ZEV Produzent, ZEV Konsumenten,
  ZEV Konsumenten berechnet sowie die beiden optionalen Bilanz-Zeilen). Kein Component-Code nötig –
  die Zeilen sind statisch, ein View-Model wie `getKennzahlen()` wäre hier Overhead.
- **Bilanz-Zeilen**: generische Keys `STATISTIK_BILANZ_BEZUG_HINWEIS` /
  `STATISTIK_BILANZ_RUECKLIEFERUNG_HINWEIS`, da die Beschriftung dieser Zeilen der **Einheiten-Name**
  ist (bzw. im Bilanzmodus `STATISTIK_RUECKLIEFERUNG_GEMESSEN`) und nicht als Key taugt.
- **i18n**: `V99__Add_Statistik_Visualisierung_Hinweise_Translations.sql` (DE/EN,
  `ON CONFLICT (key) DO NOTHING`) mit den sieben `*_HINWEIS`-Keys. V98 war die höchste angewandte
  Migration (via `zev-db` geprüft).
- **Kein Design-System-/CSS-Anteil**: natives `title`-Attribut, wie bei den Kennzahlen und den
  Vergleichs-Items (`TOOLTIP_VERGLEICH_*`).
- **PDF unverändert**: Tooltips existieren nur im Web (`StatistikPdfService` nicht betroffen).
- **Tests**: keine Anpassung nötig – `statistik.component.spec.ts` prüft die Balken-Tabelle nicht.

#### Monats-Panels aufklappbar, alle zugeklappt

Die drei Monate eines Quartals füllten aufgeklappt je rund eine Bildschirmseite. Neu ist jeder Monat
ein Collapsible; beim Laden sind alle zugeklappt.

- **Kein neues CSS**: Verwendet wird der bestehende Design-System-Baustein `.zev-collapsible` /
  `.zev-collapsible__header` / `.zev-collapsible__content` – dasselbe Muster wie „Details anzeigen"
  weiter unten auf derselben Seite und wie die Mieter-Blöcke in
  `nebenkosten-abrechnung-form.component.html`. Damit sieht die Seite an allen drei Stellen gleich
  aus, und das Design System bleibt unverändert.
  - Ein erster Entwurf hatte stattdessen eine neue Panel-Variante (`.zev-panel__title--toggle`)
    eingeführt, damit der Monatstitel seine Schriftgrösse behält. Verworfen zugunsten der
    Einheitlichkeit – ein zweites Aufklapp-Design neben dem bestehenden ist der teurere Weg.
- **Struktur**: Der Wrapper `<div class="zev-panel zev-panel--month">` bleibt (Abstand zwischen den
  Monaten, Anker für die E2E-Tests); darin sitzt das Collapsible. Der frühere `<h3>`-Titel entfällt,
  seine Bestandteile (Datenstatus-Punkt, Monatsname, Jahr) stehen jetzt in der Kopfleiste; der
  Zeitraum behält die bestehende Klasse `.zev-panel__subtitle`, das Dreieck steht rechts
  (`.zev-collapsible__icon`) wie bei den anderen Collapsibles.
- **`statistik.component.ts`**: neuer Zustand `expandedMonthPanels: Set<number>` mit
  `toggleMonthPanel(i)` / `isMonthPanelExpanded(i)`; `onSubmit()` leert das Set, damit nach einem
  Zeitraumwechsel kein Monat offen bleibt, der jetzt einen anderen Zeitraum zeigt. Der bestehende
  Zustand `expandedMonths` („Details anzeigen" **innerhalb** eines Monats) bleibt unangetastet – zwei
  unabhängige Ebenen.
- **Keine neuen Texte, kein Backend, keine Migration**: Monatsname, Zeitraum und Datenstatus standen
  bereits in der Kopfzeile. Die **PDF-Ausgabe** ist nicht betroffen (`StatistikPdfService` enthält
  weiterhin alle Monate vollständig).
- **Tests**:
  - `statistik.component.spec.ts`: sechs neue Fälle (Start zugeklappt, einzelnes Auf-/Zuklappen,
    Unabhängigkeit mehrerer Monate, Unabhängigkeit von „Details anzeigen", Zurücksetzen beim
    Absenden).
  - `tests/helpers.ts`: neuer Helfer `klappeMonateAuf(page, anzahl)`, der über `aria-expanded`
    aufklappt und die Anzahl geöffneter Monate zurückgibt. Der Selektor greift bewusst über
    **direkte Kinder** (`.zev-panel--month > .zev-collapsible > .zev-collapsible__header`): Ein
    aufgeklappter Monat enthält mit „Details anzeigen" eine zweite Collapsible-Kopfleiste.
  - `tests/statistik.spec.ts`: die beiden Tests auf Monatsinhalte klappen zuerst auf; ein neuer Test
    prüft „alle zugeklappt → einzeln auf → wieder zu".
  - `tests/export-messdaten.spec.ts`: `loadStatistik()` klappt auf, **bevor** es auf
    „Summen pro Einheit" wartet. Ohne diesen Schritt hätte der Helfer stillschweigend `false`
    geliefert und **alle** Tests der Datei übersprungen statt rot zu laufen.

---

### Ergänzung 11.10.2026: Ladeanzeige

| Status | Phase | Beschreibung |
|--------|-------|--------------|
|  [x]   | 1. Design System | `spinner.css`: `.zev-loading` (zentriert, grosser Spinner, Einblenden nach 300 ms), `.zev-loading--inline`, `prefers-reduced-motion` |
|  [x]   | 2. Komponente | `components/loading-indicator/loading-indicator.component.ts` (`app-loading-indicator`, Eingaben `text`, `inline`; `role="status"`) |
|  [x]   | 3. Einsatz | Statistik: Anzeige während `loading`, Ergebnisse in `.zev-busy`-Wrapper; Rechnungen: Anzeige während `generating` |
|  [x]   | 4. Übersetzungen | `V177__Add_Ladeanzeige_Translations.sql`: `STATISTIK_WIRD_BERECHNET`, `RECHNUNGEN_WERDEN_GENERIERT` |
|  [x]   | 5. Showcase | Abschnitt „Ladeanzeige" im Design-System-Showcase |
|  [x]   | 6. Tests | `loading-indicator.component.spec.ts`; je ein Test in `statistik.component.spec.ts` und `rechnungen.component.spec.ts` |

---

### Ergänzung 13.10.2026: Blättern des Zeitraums

| Status | Phase | Beschreibung |
|--------|-------|--------------|
|  [x]   | 1. Utility | `blaettereZeitraum(von, bis, richtung)` in `utils/date-utils.ts`: Tag / ganze Monate / Anzahl Tage, lokale Kalenderdaten |
|  [x]   | 2. Komponente | `statistik.component`: `onBlaettern(richtung)` (setzt Daten, lädt sofort), `kannBlaettern`; zwei Schaltflächen vor „Heute" (`zev-button--secondary`, kein neues CSS) |
|  [x]   | 3. Übersetzungen | `V181__Add_Statistik_Blaettern_Translations.sql`: `ZEITRAUM_ZURUECK`, `ZEITRAUM_VOR` |
|  [x]   | 4. Tests | `date-utils.spec.ts` (Tag, Monat inkl. Schaltjahr, Quartal, Jahr, Tage, Zeitumstellung, ungültig), `statistik.component.spec.ts` |

---

### Änderung 14.10.2026: „Heute" zeigt sofort an

| Status | Phase | Beschreibung |
|--------|-------|--------------|
|  [x]   | 1. Komponente | `onHeute()` ruft nach dem Setzen der Daten `onSubmit()`; `zeigtHeute` entfällt, die Schaltfläche ist nur während des Ladens gesperrt (zweiter Klick = aktualisieren) |
|  [x]   | 2. Tests | `statistik.component.spec.ts` (lädt sofort, zweiter Klick lädt erneut), E2E `statistik.spec.ts` (Heute zeigt das Gesamt-Panel ohne „Anzeigen") |

---

### Änderung 14.10.2026: keine Erfolgsmeldung nach dem Laden

| Status | Phase | Beschreibung |
|--------|-------|--------------|
|  [x]   | 1. Komponente | `onSubmit()`: Erfolgsmeldung „n Monate geladen" entfernt; `dismissMessage()` beim Start des Ladens räumt eine alte Meldung weg |
|  [x]   | 2. Tests | `statistik.component.spec.ts`: keine Meldung nach dem Laden, alter Fehler wird weggeräumt |

Der Key `MONATE_GELADEN` wird nicht mehr verwendet; er bleibt in der Datenbank (keine Migration für eine reine Bereinigung).

---

### Änderung 14.10.2026: „Anzeigen" neben den Quartalen

| Status | Phase | Beschreibung |
|--------|-------|--------------|
|  [x]   | 1. Design System | `quarter-selector.css`: `.zev-quarter-selector__actions` (Abstand links, `:empty` ausgeblendet), Selektor-Zeile `align-items: center` |
|  [x]   | 2. Komponenten | `quarter-selector.component.ts`: Aktions-Slot `<ng-content>`; `statistik.component.html`: Submit-Knopf „Anzeigen" in den Slot verschoben, aus der Knopfgruppe der Datumszeile entfernt |
|  [x]   | 3. Tests | `quarter-selector.component.spec.ts` (projizierter Inhalt nach den Quartalen), E2E `statistik.spec.ts` (Heute-Test: Position von „Anzeigen") |

# Teil B – Statistik-Kennzahlen

## Statistik-Kennzahlen – Umsetzungsplan

### Zusammenfassung
Die Statistik-Seite erhält je Monat ein **Kennzahlen-Panel** mit fachlichen Energie-KPIs (Autarkiegrad, Eigenverbrauchsquote, Netzbezugs-/Einspeisequote, ZEV-Eigenverbrauch) sowie – wo Bilanz-Daten vorliegen – **berechneten Batterie-Kennzahlen** (Netto-Speicherfluss; Stufe 2: geladen/entladen/Wirkungsgrad). Die KPIs ersetzen im Bilanzmodus den tautologischen Summen-Vergleich und machen den impliziten Batteriespeicher sichtbar. **Rein additiv:** keine neue Tabelle/Spalte, nur berechnete DTO-Felder, ein UI-Panel, PDF-Ergänzung und Übersetzungen.

### Vorgehen in zwei Stufen (Spec §8)
* **Stufe 1** (Phasen 1–5, 8): KPIs + Netto-Speicherfluss aus den bereits vorhandenen Monats-Summen (reine Arithmetik).
* **Stufe 2** (Phasen 6–7): Batterie geladen/entladen/Wirkungsgrad via Pro-Intervall-Aggregation (neue Query). Als Folgeschritt umsetzbar, ohne Stufe 1 zu blockieren.

### Betroffene Komponenten

#### Backend (kein Schema-Change)
| Datei | Änderung |
|-------|----------|
| `dto/MonatsStatistikDTO.java` | Neue transiente Felder: `autarkiegrad`, `eigenverbrauchsquote`, `netzbezugsquote`, `einspeisequote`, `zevEigenverbrauch`, `batterieNetto`, `batterieGeladen`, `batterieEntladen`, `batterieWirkungsgrad` (alle `Double`, `null` = „–") + `batterieKennzahlenVerfuegbar`/`kennzahlenBerechnet` (boolean, optional) |
| `service/StatistikService.java` | In `berechneMonatsStatistik(...)`: KPI-Berechnung aus vorhandenen Summen (Stufe 1) + Netto-Speicherfluss; Stufe 2: Batterie geladen/entladen aus Intervall-Aggregat |
| `repository/MesswerteRepository.java` | **Neue** Aggregat-Query „Netto je `zeit`" für Stufe 2 (bedingte Summen über `EinheitTyp` je Zeitstempel), Alternative: Intervall-Loop über `findDistinctZeitBetween`+`findByZeitAndEinheitTyp` |
| `service/StatistikPdfService.java` | KPIs an den JasperReports-Datencontext übergeben; Parameter `IST_BILANZ` |
| `util/PdfNumberFormat.java` | **neu** — zentrale locale-unabhängige Zahlenformatierung (Punkt-Dezimal, Hochkomma-Gruppierung) für das gesamte PDF |
| `resources/reports/statistik.jrxml` | Kennzahlen-Block je Monat; Summen-Vergleich im Bilanzmodus ausblenden; alle Zahlen über `PdfNumberFormat` |
| `resources/reports/einheit-summen.jrxml` | Subreport-Zahlen (Total/ZEV/ZEV berechnet) über `PdfNumberFormat` vereinheitlicht |

#### Frontend
| Datei | Änderung |
|-------|----------|
| `models/statistik.model.ts` | `MonatsStatistik` um die neuen KPI-Felder erweitern (`number \| null`) |
| `components/statistik/statistik.component.html` | Kennzahlen-Panel je Monat (`.zev-panel`/`.zev-info-row`); Summen-Vergleich `@if (!isBilanz)` |
| `components/statistik/statistik.component.ts` | Formatierung Prozent (1 NKS) + „–" bei `null`; „berechnet"-Kennzeichnung; ggf. Helfer `formatPercent`/`isNa` |
| `design-system/src/components/statistik/statistik.css` | Nur falls neue Markierung „berechnet/geschätzt" nötig → wiederverwendbare Klasse (z.B. `.zev-info-value--berechnet`), **nicht** im Component-CSS |

#### Übersetzungen
| Datei | Änderung |
|-------|----------|
| `resources/db/migration/V90__Add_Statistik_Kennzahlen_Translations.sql` | Kennzahlen-Labels DE/EN (`ON CONFLICT (key) DO NOTHING`) |
| `resources/db/migration/V91__Add_Statistik_Kennzahlen_Hinweise_Translations.sql` | Erklärende Hinweise/Tooltips je Kennzahl DE/EN |

### Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Backend-DTO | `MonatsStatistikDTO` um berechnete KPI-Felder + Getter/Setter erweitert (kein Schema-Change) |
| [x] | 2. Backend-Service (Stufe 1) | In `StatistikService.berechneMonatsStatistik(...)` `berechneKennzahlen(...)`: Quoten-KPIs, ZEV-Eigenverbrauch und Netto-Speicherfluss aus vorhandenen Summen; `null` bei Nenner=0 / fehlenden Bilanz-Daten |
| [x] | 3. Frontend-Model | `MonatsStatistik` (statistik.model.ts) um KPI-Felder (`number \| null`) ergänzt |
| [x] | 4. Frontend-Komponente | Kennzahlen als **3-Spalten-Tabelle** ohne Titelzeile, Inhaltsbreite (`.zev-table--compact`/`.zev-table--auto`, Wert `.zev-table__number` rechtsbündig) via `getKennzahlen(monat)`-View-Model; Summen-Vergleich per `@if (!isBilanz)` ausgeblendet; „–" + `.zev-tag`-„berechnet"-Markierung; erklärender `title`-Tooltip je Zeile. Neuer DS-Modifier `.zev-table--auto` (Design-System + Showcase) |
| [x] | 5. Übersetzungen | Flyway `V90__Add_Statistik_Kennzahlen_Translations.sql` (DE/EN) angelegt |
| [x] | 6. Backend-Batterie (Stufe 2) | Neue Aggregat-Query `sumBilanzKomponentenPerZeitBetween` in `MesswerteRepository` (Netto je `zeit`); `berechneBatterieKennzahlen(...)` berechnet geladen/entladen/Wirkungsgrad (nur bei Producer + Bezug + Rücklieferung) |
| [x] | 7. Frontend-Batterie (Stufe 2) | Batterie-Block im Panel (Netto/geladen/entladen/Wirkungsgrad), als „berechnet" markiert; nur bei `batterieKennzahlenVerfuegbar` |
| [x] | 8. PDF-Export | `StatistikPdfService` (Parameter `IST_BILANZ`) + `statistik.jrxml`: Kennzahlen-Block je Monat, Batterie als „berechnet"; Summen-Vergleich im Bilanzmodus ausgeblendet; `JasperTemplateCompileTest` grün |
| [~] | 9. Tests | **Backend erledigt:** `PdfNumberFormatTest` (18), `StatistikServiceTest` (+10 KPI-Tests), `MesswerteRepositoryIT` (+1 Aggregat-Query) — 52 Unit + 13 IT grün. **Frontend erledigt:** `statistik.component.spec.ts` (+25: `getKennzahlen` Zeilen/Einheiten/„–"/Batterie-nur-bei-verfügbar/`berechnet`-Flag, `formatSwissNumber` Hochkomma-Gruppierung via `formatNumber`/`formatDifferenz`, `isBilanz`) — Suite 815 grün. **Offen:** E2E (`statistik.spec.ts` bereits angepasst; Verifikation nach Frontend-Redeploy) → `/5_e2e-tests` |

### Berechnungslogik (Referenz für die Umsetzung)

Grundgrössen (bereits im DTO): `P=summeProducerTotal`, `C=summeConsumerTotal`, `Cz=summeConsumerZev`, `Pz=summeProducerZev`, `B=bilanzBezug`, `R=bilanzRuecklieferung` (alle Betrag/positiv).

**Stufe 1 (aus Summen, `StatistikService`):**
* `autarkiegrad = C > 0 ? Cz / C : null`
* `netzbezugsquote = C > 0 ? (C − Cz) / C : null`
* `eigenverbrauchsquote = P > 0 ? Pz / P : null`  *(Bedeutung von `Pz` modus-abhängig, s. Spec FR-1.2)*
* `einspeisequote = P > 0 ? (P − Pz) / P : null`
* `zevEigenverbrauch = Cz` (kWh)
* `batterieNetto = (Producer vorhanden && Bezug && Rücklieferung) ? P − C + B − R : null` (berechnet/geschätzt)

**Stufe 2 (Pro-Intervall, neue Query):** je `zeit`: `Netto_i = P_i − C_i + B_i − R_i`
* `batterieGeladen = Σ max(0, Netto_i)`
* `batterieEntladen = Σ max(0, −Netto_i)`
* `batterieWirkungsgrad = batterieGeladen > 0 ? batterieEntladen / batterieGeladen : null`

Alle Prozent-KPIs: Anzeige mit 1 Nachkommastelle + `%`; kWh wie bestehende Statistik-Anzeige.

### Validierungen

#### Backend
* Nenner-Prüfung `C > 0` bzw. `P > 0` vor jeder Division → sonst `null` (kein `NaN`/`Infinity`, keine Exception).
* Batterie-KPIs nur wenn Producer **und** Bilanz-Bezug **und** Rücklieferung im Zeitraum vorhanden (sonst `null`); `batterieWirkungsgrad` nur wenn `batterieGeladen > 0`.
* Multi-Tenancy unverändert: Berechnung ausschliesslich auf den bereits org-gefilterten Summen/Messwerten; keine neuen mandantenübergreifenden Queries.
* Autorisierung: keine neue Permission, weiterhin `@PreAuthorize("hasAuthority('statistik:read')")` am `StatistikController`.

#### Frontend
* `null` → Anzeige „–" (kein `NaN`, kein `0 %` als Fehlwert).
* Zahlenformat locale-unabhängig: **Punkt** als Dezimal-, **Hochkomma (`'`)** als Tausendertrennzeichen. Frontend über den seitenweiten Helfer `formatSwissNumber()` (basiert auf `toFixed`), den `formatNumber`/`formatDifferenz`/`formatPercent` nutzen; **gesamtes** PDF (statistik.jrxml + einheit-summen.jrxml) über `ch.nacht.util.PdfNumberFormat` (`String.format(Locale.ROOT, …)` mit Gruppierungs-Ersatz `,`→`'`).
* Summen-Vergleich nur im Modus `PRODUCER_MESSUNG` rendern (`@if (!isBilanz)`); Kennzahlen-Panel in beiden Modi.
* Ausschliesslich Design-System-Klassen; keine Ad-hoc-Styles in der Komponente.

### Übersetzungs-Keys (V90)
`STATISTIK_KENNZAHLEN`, `KENNZAHL_AUTARKIEGRAD`, `KENNZAHL_EIGENVERBRAUCHSQUOTE`, `KENNZAHL_NETZBEZUGSQUOTE`, `KENNZAHL_EINSPEISEQUOTE`, `KENNZAHL_ZEV_EIGENVERBRAUCH`, `KENNZAHL_BATTERIE_NETTO`, `KENNZAHL_BATTERIE_GELADEN`, `KENNZAHL_BATTERIE_ENTLADEN`, `KENNZAHL_BATTERIE_WIRKUNGSGRAD`, `KENNZAHL_BERECHNET_HINWEIS` (+ ggf. `KENNZAHL_NA` = „–" / „n/a").

### Offene Punkte / Annahmen
* **Stufen-Reihenfolge (Spec §8, entschieden):** Stufe 1 zuerst, Stufe 2 als Folgeschritt. Phasen 6–7 können separat/später umgesetzt werden.
* **Summen-Vergleich im Bilanzmodus (entschieden):** vollständig ausblenden (nicht nur Tautologie-Hinweis), Kennzahlen-Panel tritt an seine Stelle.
* **Eigenverbrauchsquote-Definition (entschieden):** `Pz/P` über `summeProducerZev`; im Bilanzmodus entspricht `Pz = max(0, P − R)` → Batterieladung zählt als Eigenverbrauch mit (Textbuch-Definition, bewusst akzeptiert; s. Spec FR-1.2/§5).
* **ZEV-Eigenverbrauch-Feld (Annahme):** verwendet `summeConsumerZev` (effektiv/gemessen), nicht `summeConsumerZevCalculated` – konsistent zum bestehenden Summen-Vergleich (Spec FR-1.5).
* **Stufe-2-Aggregation (Annahme):** bevorzugt SQL-seitige Aggregat-Query „Netto je `zeit`" (bedingte Summe je `EinheitTyp`), um einen zweiten vollen Intervall-Loop zu vermeiden (NFR-1); Fallback ist der Loop über `findDistinctZeitBetween`+`findByZeitAndEinheitTyp`.
* **Cache:** Der bestehende `statistik`-Caffeine-Cache (TTL 15 min) greift unverändert, da die KPIs Teil des berechneten `StatistikDTO` sind.
* **„berechnet"-Markierung (Annahme):** falls kein passender Design-System-Baustein existiert, neue wiederverwendbare Klasse in `design-system/src/components/statistik/` (z.B. `.zev-info-value--berechnet`) + Tooltip via `KENNZAHL_BERECHNET_HINWEIS`.
* **Migrations-Version:** aktuell höchste ist `V89`; die Übersetzungs-Migration wird als `V90` angelegt (zum Umsetzungszeitpunkt final prüfen).

### Nachtrag: Gemessene Gegenstücke zu Autarkiegrad und Netzbezugsquote (FR-1.6)

**Auslöser:** Bei einer Anlage standen ein gemessener Netzbezug von 38 kWh und eine Netzbezugsquote
von 9.8 % (bei 985 kWh Verbrauch) nebeneinander auf derselben Seite. Beide Zahlen waren richtig —
die Quote rechnet aus dem ZEV-Anteil der Konsumenten (`(C − Cz)/C`), die 38 kWh sind der Zähler am
Netzanschluss. Nebeneinander gelesen widersprachen sie sich trotzdem. Statt eine der beiden
Definitionen zu ersetzen, werden nun **beide** ausgewiesen: Ihre Differenz ist der Verbrauchsanteil,
der weder direkt aus der PV noch aus dem Netz kam — typischerweise die Batterie-Entladung.

#### Backend
* `MesswerteRepository.countDistinctZeitByEinheitTypAndZeitBetween(typ, von, bis)` — neue Query für
  die Intervall-Abdeckung (JPQL, damit der `orgFilter` greift).
* `MonatsStatistikDTO`: `autarkiegradGemessen`, `netzbezugsquoteGemessen`,
  `bilanzKennzahlenVerfuegbar`, `bilanzBezugLueckenhaft`.
* `StatistikService.berechneKennzahlen(dto, von, bis)` — Signatur um den Zeitraum erweitert (für die
  Lückenprüfung). Rechnet `B/C` bzw. `1 − B/C`, sobald eine `BEZUG`-Einheit existiert **und** `C > 0`.
* **Lückenprüfung:** `pruefeDatenVollstaendigkeitMonat` arbeitet tage- und einheitengenau und sieht
  fehlende Intervalle **innerhalb** eines Tages nicht — genau die entstehen aber, wenn das
  Bilanzmodell einzelne Intervalle ohne `BEZUG`-Messwert überspringt (`distributeBilanz`, FR-2.5).
  Deshalb wird die Anzahl `BEZUG`-Intervalle gegen die der Konsumenten gezählt; bei Unterdeckung
  `bilanzBezugLueckenhaft = true` plus WARN-Log mit den beiden Zahlen. Der Wert wird **trotzdem**
  geliefert: gekennzeichnet ist brauchbarer als „–".

#### Frontend
* `MonatsStatistik` um die vier Felder erweitert.
* `KennzahlZeile` erhält das optionale Flag `luecke`; `getKennzahlen()` schiebt die gemessene Zeile
  **direkt hinter** ihr gerechnetes Gegenstück, damit die Differenz beim Lesen auffällt.
* Kennzeichnung „lückenhaft" über die bestehende `.zev-tag`-Darstellung neben `KENNZAHL_BERECHNET` —
  **kein neues CSS**.
* Die gemessenen Zeilen tragen **nicht** die Markierung „berechnet/geschätzt": Sie stammen aus einer
  Messung, nicht aus einem Residuum der Energiebilanz.

#### PDF
* `statistik.jrxml`: zwei Zeilen in der linken Spalte (y=376/392) mit
  `printWhenExpression = bilanzKennzahlenVerfuegbar`, darunter der Lücken-Hinweis (y=408). Band von
  470 auf 502 erhöht, Subreport „Summen pro Einheit" von y=392 auf y=432 verschoben.
* Geprüft mit `mvn test -Dtest=JasperTemplateCompileTest`.

#### i18n
* `V133__Add_Statistik_Kennzahlen_Gemessen_Translations.sql` (V132 war die höchste angewandte
  Migration, via `zev-db` geprüft): sechs neue Keys mit `ON CONFLICT (key) DO NOTHING`.
* Zusätzlich zwei `UPDATE`s auf `KENNZAHL_AUTARKIEGRAD_HINWEIS` und `KENNZAHL_NETZBEZUGSQUOTE_HINWEIS`:
  Solange der Autarkiegrad allein stand, genügte „intern aus PV/Batterie gedeckt"; neben dem
  gemessenen Wert muss der Tooltip sagen, dass eine Batterie-Entladung hier **nicht** mitzählt. Beide
  `UPDATE`s sind mit dem **alten Textwert** in der `WHERE`-Klausel abgesichert — eine eigene Anpassung
  über die Übersetzungsverwaltung wird dadurch nicht überschrieben.

#### Tests
* `StatistikServiceTest`: sechs neue Fälle (Werte aus der Messung, Komplementarität, ohne
  `BEZUG`-Einheit, `C = 0`, mit und ohne Lücken) — 40 Tests grün.
* `statistik.component.spec.ts` / `statistik.service.spec.ts`: Fixtures um die vier Felder erweitert,
  acht neue Fälle (Zeilenanzahl 5/7/11, Reihenfolge, Formatierung, Lücken-Kennzeichnung).
* Ein bestehender Test griff die Batterie-Zeilen über `zeilen.slice(5)` ab und brach durch die zwei
  zusätzlichen Zeilen. Neu wird über den Key-Präfix gefiltert — beim nächsten Zusatz hält das.

#### Korrektur: Lücken verzerren beide Seiten, nicht nur die gemessene

Der erste Wurf (V133) kennzeichnete nur die **gemessenen** Zeilen als „lückenhaft" — mit der
Begründung, dem Netzbezug fehlten die übersprungenen Intervalle. Das stimmt, ist aber der kleinere
der beiden Fehler, und im Bilanzmodus war auch die Herleitung des Zeilenpaars falsch.

**Was übersehen wurde:** Im Modus `BILANZ` stammt der ZEV-Anteil der Konsumenten selbst aus den
Bilanzdaten (`S = max(0, Verbrauch − Bezug)` je Intervall). Überspringt `distributeBilanz` ein
Intervall, bleiben die Konsumenten dort ohne `zev`, ihr Verbrauch zählt aber weiter — er schlägt also
**voll** als Netzbezug zu Buche statt nur mit seinem tatsächlichen Netzanteil.

Über den Monat gerechnet: `Cz = C_ohneLücke − B`, also `C − Cz = C_Lücke + B`. Der Abstand der beiden
angezeigten Autarkiegrade ist damit **exakt `C_Lücke / C`** — der Verbrauch während der Intervalle
ohne Bilanz-Messwert. Der wahre Wert liegt dazwischen: die gemessene Zeile ist um `B_Lücke / C` zu
günstig, die gerechnete um `(C_Lücke − B_Lücke) / C` zu ungünstig.

**Folge für die Herleitung des Features:** Im Bilanzmodus enthält der gerechnete Autarkiegrad die
Batterie-Entladung bereits (sie senkt den Bezug und erhöht damit `S`). Beide Zeilen sollten dort
praktisch übereinstimmen; eine Differenz zeigt **keine Batterie**, sondern Lücken in der
Bilanzmessung. Der ursprünglich genannte „Batterie-Effekt" gilt nur im Modus `PRODUCER_MESSUNG`, wo
`Cz` die direkt verbrauchte Produktion ist. Das Zeilenpaar bleibt in beiden Modi nützlich — im einen
als Batterie-Anzeige, im anderen als Datenqualitäts-Anzeige.

* `MonatsStatistikDTO.verteilungLueckenhaft` — neu; wahr bei Lücken **und** Modus `BILANZ`.
* `StatistikService`: Der Verteilmodus wird in `getStatistik` einmal ermittelt und über
  `berechneMonatsStatistiken` → `berechneMonatsStatistik` → `berechneKennzahlen` durchgereicht
  (statt je Monat neu abzufragen). Die WARN-Meldung nennt jetzt Modus und beide Richtungen.
* `KennzahlZeile.lueckeHintKey` — die beiden Seiten brauchen **verschiedene** Hinweise, weil die
  Verzerrung in entgegengesetzte Richtungen zeigt. Im Template wird `kz.luecke && kz.lueckeHintKey`
  geprüft; die zweite Bedingung engt den Typ für die `TranslatePipe` ein (`string | undefined`).
* `statistik.jrxml`: Der Fussnoten-Hinweis wählt den Text nach `verteilungLueckenhaft`.
* `V134__Statistik_Kennzahlen_Luecken_Richtung.sql`: zwei neue Hinweis-Keys
  (`KENNZAHL_LUECKE_MESSUNG_HINWEIS`, `KENNZAHL_LUECKE_VERTEILUNG_HINWEIS`) sowie `UPDATE`s auf die
  beiden `_GEMESSEN_HINWEIS`-Texte, die bisher die Batterie als Grund der Differenz nannten. V133 war
  bereits angewendet (via `zev-db` geprüft) und durfte nicht geändert werden.
* Tests: `StatistikServiceTest` 42 (Bilanzmodus kennzeichnet beide Seiten, Producer-Messung nur die
  gemessene), `statistik.component.spec.ts` um Richtung und Hinweis-Keys erweitert.

#### Nachtrag — Batterie-Kennzahlen aus der Einheit `SPEICHER` (16.09.2026, FR-2a)

**Auslöser:** Seit dem Einheiten-Typ `SPEICHER` (`Specs/Batteriespeicher.md`) liefert der
Wechselrichter Ladung und Entladung gemessen. Bis dahin waren die Batterie-Kennzahlen ein Residuum
der Energiebilanz — mit allem, was sonst noch darin steckt.

**Die gemessene Quelle hat Vorrang.** Existiert eine `SPEICHER`-Einheit, kommen Ladung, Entladung,
Netto und Wirkungsgrad aus deren Messwerten; sonst bleibt alles wie bisher.

| | mit `SPEICHER` | ohne |
|---|---|---|
| Quelle | Messwerte der Einheit | `P − C + B − R` je Intervall |
| Voraussetzung | nur die Einheit | Producer **und** Bilanz-Bezug **und** Rücklieferung |
| Kennzeichnung „berechnet" | **entfällt** | bleibt |
| Aufwand | **eine** Abfrage | Intervall-Loop |

**Zwei Dinge, die dabei wichtig waren:**

Eine schlichte `SUM(total)` wäre nutzlos gewesen — beim Speicher ist `total` der Saldo aus Ladung
und Entladung und liegt über einen Monat nahe null. Die neue Abfrage
`sumLadungEntladungByEinheitTypAndZeitBetween` trennt beide Richtungen in der Datenbank.

Die Kennzeichnung **„berechnet" entfällt nur bei gemessenen Werten** — in Web und PDF. Sie ist kein
Schmuck: Sie sagt, dass die Zahl auch Messfehler und nicht gemessene Lasten enthält. Bei einem
Zählerstand wäre der Hinweis schlicht falsch.

**Nebenbei fällt eine Kontrollgrösse ab:** Ein Round-Trip-Wirkungsgrad über 100 % ist unmöglich —
er wäre der stille Hinweis auf vertauschte Register. Bei Hene zeigt der echte Speicher 92.3 %.

**Im Frontend** war die Änderung minimal, weil `berechnet` schon ein Parameter der Zeilen-Helfer
war; nur `signedKwhZeile` hatte ihn hart auf `true`. Das Template blieb unberührt.

**Geprüft:** 1325 Backend-Tests (vier neue: gemessen, ohne Bilanzdaten, ohne Messwerte, Regression
ohne Speicher), 1659 Frontend-Tests (zwei neue zur Kennzeichnung), `JasperTemplateCompileTest` grün.

> **Am PDF sichtgeprüft (16.09.2026):** Der Hinweis verschwindet bei gemessenen Werten
> tatsächlich. Nötig war die Prüfung, weil `JasperTemplateCompileTest` nur die Kompilierbarkeit
> feststellt — eine falsche `printWhenExpression` oder ein zu kleines Band fiele dort nicht auf.

# Teil C – „Summen pro Einheit“ im Statistik-PDF

## Plan: "Summen pro Einheit"-Tabelle im Statistik-PDF

### Ausgangslage

Das Statistik-PDF (`statistik.jrxml`) zeigt derzeit pro Monat:
- Monatsheader mit Zeitraum
- Datenstatus
- Summen-Tabelle (5 Werte: ProducerTotal, ConsumerTotal, ProducerZev, ConsumerZev, ConsumerZevCalculated)
- Summen-Vergleich (A=B, A=C, B=C)

**Fehlt:** Die "Summen pro Einheit"-Tabelle, die auf der Webseite `/statistik` pro Monat angezeigt wird.

Die Daten sind bereits vorhanden: `MonatsStatistikDTO.einheitSummen` (Liste von `EinheitSummenDTO`) wird vom `StatistikService` berechnet und befüllt.

### Ansatz: Subreport

Da die Anzahl Einheiten dynamisch ist, verwenden wir einen **JasperReports-Subreport**. Die Liste `einheitSummen` wird pro Monat als DataSource an den Subreport übergeben. Der Subreport iteriert über die Einheiten und rendert die Tabelle.

### Zu ändernde Dateien

#### 1. NEU: `backend-service/src/main/resources/reports/einheit-summen.jrxml`

Subreport-Template mit:
- **Parameter:** `TRANSLATIONS` (Map), `SPRACHE` (String)
- **Fields:** `einheitName` (String), `einheitTyp` (EinheitTyp), `summeTotal` (Double), `summeZev` (Double), `summeZevCalculated` (Double)
- **columnHeader-Band:** Abschnittstitel "Summen pro Einheit" + Tabellenkopf mit 5 Spalten:
  - Einheit | Typ | Total (kWh) | ZEV (kWh) | ZEV berechnet (kWh)
- **detail-Band:** Eine Zeile pro Einheit
  - Typ-Spalte: `PRODUCER` → translate("PRODUZENT"), `CONSUMER` → translate("KONSUMENT")
  - ZEV berechnet: Nur für CONSUMER anzeigen, bei PRODUCER "-"
  - Zahlenformat: `#,##0.000`

Spaltenbreiten (gesamt 555):

| Spalte | Breite | Ausrichtung |
|--------|--------|-------------|
| Einheit | 170 | links |
| Typ | 85 | links |
| Total | 100 | rechts |
| ZEV | 100 | rechts |
| ZEV berechnet | 100 | rechts |

#### 2. ÄNDERN: `backend-service/src/main/resources/reports/statistik.jrxml`

- Neues Field hinzufügen: `einheitSummen` (java.util.List)
- Neuen Parameter hinzufügen: `EINHEIT_SUMMEN_SUBREPORT` (JasperReport)
- Detail-Band-Höhe erhöhen: 220 → 280 (Platz für Subreport-Minimum)
- Subreport-Element am Ende des Detail-Bands einfügen (y=225):
  - `printWhenExpression`: Nur wenn `einheitSummen` nicht leer
  - DataSource: `new JRBeanCollectionDataSource($F{einheitSummen})`
  - Parameter durchreichen: `TRANSLATIONS`, `SPRACHE`

#### 3. ÄNDERN: `backend-service/src/main/java/ch/nacht/service/StatistikPdfService.java`

- Neues Feld: `private JasperReport compiledEinheitSummenReport;`
- In `init()`: `einheit-summen.jasper` laden (analog zu compiledReport)
- In `generatePdf()`: Parameter `EINHEIT_SUMMEN_SUBREPORT` zur Map hinzufügen

#### 4. ÄNDERN: `backend-service/src/test/java/ch/nacht/JasperTemplateCompileTest.java`

- Neuen Test `testEinheitSummenTemplateCompiles()` hinzufügen (analog zu den bestehenden Tests)

### Verifizierung

1. `mvn test -pl backend-service -Dtest=JasperTemplateCompileTest` - Templates kompilieren
2. `mvn compile -pl backend-service` - Build inkl. Jasper-Kompilierung
3. Manuell: PDF-Export über `/api/statistik/export/pdf` testen

## Nachtrag: Aufruf mit Zeitraum in der URL (FR-1.4, 23.10.2026)

`StatistikComponent.ngOnInit` liest `von`/`bis` aus `ActivatedRoute.snapshot.queryParamMap`. Sind
beide echte Kalendertage (`isoZuDatum` + Rückformatierung, damit `2026-02-30` nicht in den März
rollt) und `von <= bis`, werden sie übernommen und `onSubmit()` lädt sofort; sonst gilt das
Vorquartal ohne Laden. Aufrufer: Schaltfläche „Statistik“ der Einspeisesteuerung. Tests: 3 in
`statistik.component.spec.ts` (Describe „Aufruf mit Zeitraum in der URL (FR-1.4)“).

