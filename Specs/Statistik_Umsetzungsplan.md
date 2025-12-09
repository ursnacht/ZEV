# Umsetzungsplan: Statistikseite

## Zusammenfassung

Die Statistikseite bietet einen Überblick über Messdaten und Verteilungen. Sie zeigt pro Monat im gewählten Datumsbereich die Summen aller Producer und Consumer an und vergleicht diese. Datenlücken werden mit einem Ampel-System und aufklappbaren Details visualisiert.

---

## Phase 1: Backend - Statistik-Service und API

### 1.1 Neues DTO für Statistik-Daten erstellen

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

### 1.2 Repository-Methoden erweitern

**Datei:** `backend-service/src/main/java/ch/nacht/repository/MesswerteRepository.java`

Neue Query-Methoden:
- `findMaxZeit()` - Letztes Datum mit Messwerten
- `findDistinctDatesInRange(dateFrom, dateTo)` - Alle Tage mit Daten
- `sumTotalByEinheitTypAndZeitBetween(typ, von, bis)` - Summe total pro Typ
- `sumZevByEinheitTypAndZeitBetween(typ, von, bis)` - Summe zev pro Typ
- `sumZevCalculatedByEinheitTypAndZeitBetween(typ, von, bis)` - Summe zev_calculated

### 1.3 StatistikService erstellen

**Datei:** `backend-service/src/main/java/ch/nacht/service/StatistikService.java`

Methoden:
- `getStatistik(LocalDate von, LocalDate bis)` - Hauptmethode
- `ermittleLetztesMessdatum()` - Letztes Datum mit Daten
- `pruefeDatenVollstaendigkeit(von, bis)` - Prüft auf Lücken
- `berechneMonatsStatistik(jahr, monat)` - Statistik für einen Monat
- `vergleicheSummen(summeC, summeD, summeE)` - Vergleicht und berechnet Differenzen

### 1.4 StatistikController erstellen

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

## Phase 2: Frontend - Statistik-Komponente

### 2.1 Statistik-Models erstellen

**Datei:** `frontend-service/src/app/models/statistik.model.ts`

TypeScript-Interfaces analog zu den Backend-DTOs.

### 2.2 Statistik-Service erstellen

**Datei:** `frontend-service/src/app/services/statistik.service.ts`

```typescript
@Injectable({ providedIn: 'root' })
export class StatistikService {
    getStatistik(von: string, bis: string): Observable<StatistikDTO>
    getLetztesMessdatum(): Observable<string>
}
```

### 2.3 Statistik-Komponente erstellen

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

### 2.4 Visuelle Darstellung

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

## Phase 3: Design-System erweitern

### 3.1 Status-Indikatoren hinzufügen

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

### 3.2 Aufklappbare Details-Komponente

**Datei:** `design-system/src/components/collapsible.css` (neu)

```css
.zev-collapsible { ... }
.zev-collapsible__header { ... }
.zev-collapsible__content { ... }
.zev-collapsible--open .zev-collapsible__content { display: block; }
```

---

## Phase 4: Routing und Navigation

### 4.1 Route hinzufügen

**Datei:** `frontend-service/src/app/app.routes.ts`

```typescript
{
    path: 'statistik',
    component: StatistikComponent,
    canActivate: [AuthGuard],
    data: { roles: ['zev'] }
}
```

### 4.2 Navigation erweitern

**Datei:** `frontend-service/src/app/components/navbar/navbar.component.html`

Neuen Menüpunkt "Statistik" hinzufügen.

---

## Phase 5: Internationalisierung

### 5.1 Flyway-Migration für Übersetzungen

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

## Phase 6: Logging und Fehlerbehandlung

### 6.1 Backend-Logging

- INFO: Statistik-Abfrage mit Datumsbereich
- DEBUG: Berechnete Summen pro Monat
- WARN: Datenlücken gefunden
- ERROR: Datenbankfehler bei Abfrage

### 6.2 Frontend-Fehlerbehandlung

- Fehlermeldung bei API-Fehlern anzeigen
- Loading-Indikator während Datenladung
- Leerer Zustand wenn keine Daten vorhanden

---

## Technische Details

### Datenbank-Abfragen (optimiert)

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

### Toleranz bei Summenvergleichen

Da Gleitkommazahlen verglichen werden, wird eine Toleranz von 0.0009 kWh verwendet:
```java
private static final double TOLERANZ = 0.0009;

private boolean sindGleich(Double a, Double b) {
    return Math.abs(a - b) < TOLERANZ;
}
```

---

## Phase 7: Caching

### 7.1 Backend-Caching mit Spring Cache

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

## Phase 8: PDF-Export

### 8.1 Backend - PDF-Generierung

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

### 8.2 Frontend - Export-Button

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

### 8.3 PDF-Layout

Das PDF enthält:
- Header mit Titel "Statistik-Report" und Datumsbereich
- Übersicht: Messwerte vorhanden bis, Datenvollständigkeit
- Pro Monat eine Sektion mit:
  - Monat/Jahr als Überschrift
  - Summen-Tabelle
  - Vergleichs-Tabelle mit Differenzen
  - Liste der Tage mit Abweichungen (falls vorhanden)
- Footer mit Generierungsdatum

### 8.4 Zusätzliche Übersetzungen für PDF

```sql
INSERT INTO translation (key, deutsch, englisch) VALUES
('PDF_EXPORTIEREN', 'Als PDF exportieren', 'Export as PDF'),
('STATISTIK_REPORT', 'Statistik-Report', 'Statistics Report'),
('ZEITRAUM', 'Zeitraum', 'Period'),
('GENERIERT_AM', 'Generiert am', 'Generated on'),
('SEITE', 'Seite', 'Page');
```

---

## Zusammenfassung der zu erstellenden/ändernden Dateien (aktualisiert)

### Neue Dateien

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

### Zu ändernde Dateien

| Datei | Änderung |
|-------|----------|
| `backend-service/pom.xml` | Caffeine + OpenHTMLToPDF Abhängigkeiten |
| `backend-service/.../repository/MesswerteRepository.java` | Neue Query-Methoden |
| `backend-service/.../service/MesswerteService.java` | Cache-Invalidierung bei Upload/Berechnung |
| `frontend-service/src/app/app.routes.ts` | Route hinzufügen |
| `frontend-service/.../components/navbar/navbar.component.html` | Menüpunkt hinzufügen |
| `design-system/src/components/index.css` | Neue CSS-Dateien importieren |

---

## Entscheidungen (festgelegt)

| Thema | Entscheidung |
|-------|--------------|
| Toleranzwert für Summenvergleiche | 0.0009 kWh |
| Caching | Ja, mit Caffeine (15 Min TTL) |
| Export-Format | PDF |
