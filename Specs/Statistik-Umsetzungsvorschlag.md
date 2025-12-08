# Umsetzungsvorschlag: Statistikseite

## Übersicht

Die Statistikseite zeigt eine Übersicht über Messdaten und Verteilungen pro Monat mit:
- Übersichtsdaten (letztes Datum mit Messwerten, Vollständigkeit)
- Monatsweise Statistiken mit Summen und Vergleichen
- Grafische Darstellung von Abweichungen
- Datumsfilter (Default: vorheriger Monat)

---

## Backend

### 1. Neuer REST-Endpoint: `StatistikController`

```
GET /api/statistik?dateFrom=...&dateTo=...
```

Liefert aggregierte Statistikdaten für den angegebenen Zeitraum.

### 2. Neue DTOs

```java
public record StatistikResponse(
    LocalDate letztesMessdatum,
    boolean alleDatenVollstaendig,
    List<String> einheitenMitLuecken,
    List<MonatsStatistik> monatsStatistiken
) {}

public record MonatsStatistik(
    YearMonth monat,
    boolean vollstaendig,
    int tageGesamt,
    int tageMitDaten,
    SummenVergleich consumerSummen,
    SummenVergleich producerSummen,
    boolean zevSummenGleich,        // Producer.zev == Consumer.zev
    boolean zevCalculatedGleich,    // Producer.zevCalc == Consumer.zevCalc
    boolean zevUndCalculatedGleich, // zev == zev_calculated
    List<LocalDate> tageUngleich
) {}

public record SummenVergleich(
    double total,
    double zev,
    double zevCalculated
) {}
```

### 3. Neuer Service: `StatistikService`

Verantwortlichkeiten:
- Aggregiert Messwerte pro Monat
- Prüft Vollständigkeit (alle Tage im Monat vorhanden?). Prüft ob auch Werte in zev und zev_calculated vorhanden sind (beachte es gibt viele Werte mit dem Wert 0.0)
- Berechnet Summen pro Consumer/Producer
- Vergleicht zev-Werte und zev_calculated-Werte zwischen Producer und Consumer
- Ermittelt Tage mit Abweichungen

### 4. Repository-Erweiterungen: `MesswerteRepository`

Neue Queries für Aggregationen:

```java
// Summen pro Tag und Typ
@Query("SELECT DATE(m.zeit) as tag, m.einheit.typ as typ, " +
       "SUM(m.total) as total, SUM(m.zev) as zev, SUM(m.zevCalculated) as zevCalc " +
       "FROM Messwerte m WHERE m.zeit BETWEEN :from AND :to " +
       "GROUP BY DATE(m.zeit), m.einheit.typ")
List<TagesStatistik> findTagesStatistik(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

// Letztes Messdatum
@Query("SELECT MAX(DATE(m.zeit)) FROM Messwerte m")
LocalDate findLetztesMessdatum();

// Einheiten mit Lücken im Zeitraum
@Query("SELECT DISTINCT e.name FROM Einheit e WHERE e.id NOT IN " +
       "(SELECT DISTINCT m.einheit.id FROM Messwerte m WHERE DATE(m.zeit) = :datum)")
List<String> findEinheitenOhneDatenAmTag(@Param("datum") LocalDate datum);
```

### 5. Flyway-Migration für Übersetzungen

Neue Translation-Keys (DE/EN):

| Key | Deutsch | English |
|-----|---------|---------|
| STATISTIK | Statistik | Statistics |
| STATISTIK_UEBERSICHT | Übersicht | Overview |
| MESSWERTE_VORHANDEN_BIS | Messwerte vorhanden bis | Data available until |
| ALLE_DATEN_VOLLSTAENDIG | Alle Daten vollständig | All data complete |
| DATEN_LUECKEN | Datenlücken vorhanden | Data gaps exist |
| EINHEITEN_MIT_LUECKEN | Einheiten mit Lücken | Units with gaps |
| MONAT | Monat | Month |
| VOLLSTAENDIG | Vollständig | Complete |
| UNVOLLSTAENDIG | Unvollständig | Incomplete |
| TAGE_MIT_DATEN | Tage mit Daten | Days with data |
| CONSUMER_SUMMEN | Consumer Summen | Consumer totals |
| PRODUCER_SUMMEN | Producer Summen | Producer totals |
| SUMME_TOTAL | Summe Total | Total sum |
| SUMME_ZEV | Summe ZEV | ZEV sum |
| SUMME_ZEV_CALCULATED | Summe ZEV berechnet | ZEV calculated sum |
| ZEV_SUMMEN_GLEICH | ZEV Summen gleich | ZEV sums match |
| ZEV_CALCULATED_GLEICH | ZEV berechnet gleich | ZEV calculated match |
| TAGE_MIT_ABWEICHUNGEN | Tage mit Abweichungen | Days with discrepancies |

---

## Frontend

### 1. Neue Komponente: `StatistikComponent`

- **Pfad**: `/statistik`
- **Rolle**: `zev`
- **Typ**: Standalone-Komponente mit TranslatePipe

### 2. Features

**Datumsfilter**
- Analog zur Messwerte-Seite
- Default: vorheriger Monat
- Von-Datum setzt automatisch Bis-Datum auf Monatsende

**Übersichtsbereich**
- "Messwerte vorhanden bis" mit letztem Datum
- Status-Anzeige: Alle Daten vollständig (grün) / Lücken vorhanden (rot)
- Liste der Einheiten mit Lücken (falls vorhanden)

**Monatsstatistiken**
- Karten pro Monat mit:
  - Monat/Jahr als Titel
  - Vollständigkeits-Indikator (grün/rot Icon)
  - Tage mit Daten: X von Y
  - Summentabelle (Consumer/Producer)
  - Vergleichsstatus mit Checkmarks/Warnings
  - Expandierbare Liste der Tage mit Abweichungen

### 3. Grafische Darstellung

**Balkendiagramm pro Monat**
- X-Achse: Monate
- Y-Achse: kWh
- Balken: Producer ZEV vs Consumer ZEV nebeneinander
- Farbcodierung: Grün = Match, Rot = Abweichung
- Chart.js (bereits im Projekt vorhanden)

**Alternative/Ergänzung: Heatmap**
- Tagesübersicht für ausgewählten Monat
- Farbcodierung nach Vollständigkeit/Abweichung

### 4. Navigation

Neuer Menüpunkt "Statistik" in der Navigation:
- Position: nach "Messwerte Grafik", vor "Translation Editor"
- Translation-Key: `STATISTIK`

### 5. Template-Struktur

```html
<div class="zev-container">
  <!-- Header mit Filter -->
  <h1>{{ 'STATISTIK' | translate }}</h1>

  <!-- Datumsfilter -->
  <div class="zev-form-group">
    <!-- Von/Bis Datumseingabe -->
  </div>

  <!-- Übersicht -->
  <section class="zev-card">
    <h2>{{ 'STATISTIK_UEBERSICHT' | translate }}</h2>
    <!-- Letzte Messdaten, Vollständigkeit -->
  </section>

  <!-- Chart -->
  <section class="zev-card">
    <canvas id="statistikChart"></canvas>
  </section>

  <!-- Monatsstatistiken -->
  <section class="zev-card" *ngFor="let monat of monatsStatistiken">
    <!-- Monatskarte -->
  </section>
</div>
```

---

## Umsetzungsplan

| # | Schritt | Beschreibung | Aufwand |
|---|---------|--------------|---------|
| 1 | Backend DTOs | StatistikResponse, MonatsStatistik, SummenVergleich Records | Klein |
| 2 | Repository Queries | Aggregations-Queries für Summen und Vollständigkeit | Mittel |
| 3 | StatistikService | Geschäftslogik für Statistikberechnung | Mittel |
| 4 | StatistikController | REST-Endpoint mit Datumsfilter | Klein |
| 5 | Flyway Migration | Translation-Keys (DE/EN) | Klein |
| 6 | Frontend Service | StatistikService für API-Calls | Klein |
| 7 | Frontend Component | StatistikComponent mit Template und Styling | Mittel |
| 8 | Navigation & Routing | Menüeintrag und Route hinzufügen | Klein |
| 9 | Chart Integration | Balkendiagramm für Monatsvergleiche | Mittel |
| 10 | Tests | Unit Tests (Service), IT (Repository), E2E (Playwright) | Mittel |

---

## Entscheidungen

| Frage | Entscheidung |
|-------|--------------|
| **Grafik** | Balkendiagramm pro Monat (Producer ZEV vs Consumer ZEV) |
| **Abweichungstoleranz** | 0.0009 kWh - Werte gelten als "gleich" wenn Differenz < 0.0009 |
| **Caching** | Ja - Statistikberechnungen werden gecacht |

---

## Caching-Strategie

### Backend-Cache mit Spring Cache

```java
@Service
public class StatistikService {

    private static final double TOLERANZ = 0.0009;

    @Cacheable(value = "statistik", key = "#dateFrom.toString() + '-' + #dateTo.toString()")
    public StatistikResponse getStatistik(LocalDate dateFrom, LocalDate dateTo) {
        // Berechnung...
    }

    @CacheEvict(value = "statistik", allEntries = true)
    public void invalidateCache() {
        // Wird aufgerufen wenn neue Messwerte hochgeladen werden
    }
}
```

### Cache-Invalidierung

Der Cache wird invalidiert wenn:
- Neue Messwerte hochgeladen werden (MesswerteUpload)
- Solarverteilung berechnet wird (zev_calculated ändert sich)
- Einheiten geändert werden

### Cache-Konfiguration

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("statistik");
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(1))  // Max 1 Stunde
            .maximumSize(100));                      // Max 100 Einträge
        return cacheManager;
    }
}
```

### Abhängigkeit

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

---

## Abweichungsvergleich

### Konstante für Toleranz

```java
public class StatistikService {
    private static final double TOLERANZ = 0.0009;

    private boolean sindGleich(double a, double b) {
        return Math.abs(a - b) < TOLERANZ;
    }
}
```

### Anwendung

- `zevSummenGleich`: `sindGleich(producerZev, consumerZev)`
- `zevCalculatedGleich`: `sindGleich(producerZevCalc, consumerZevCalc)`
- `zevUndCalculatedGleich`: `sindGleich(zev, zevCalculated)`

---

## Technische Hinweise

- Design System verwenden (zev-* CSS-Klassen)
- Alle Texte über TranslationService mehrsprachig
- Logging im Service für Debugging
- Rolle "zev" für Zugriffskontrolle
- Bestehende Patterns aus MesswerteChartComponent übernehmen
