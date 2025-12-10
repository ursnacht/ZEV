# Umsetzungsplan: Refactoring Producer und Solarstromverteilung

## Zusammenfassung

Dieses Refactoring ändert die Art und Weise, wie Producer-Werte gespeichert und verarbeitet werden. Aktuell werden alle Werte als absolute Werte (positiv) gespeichert. Nach dem Refactoring werden Producer-Werte so gespeichert, wie sie in der Importdatei stehen (typischerweise negativ für Solarproduktion, positiv für Steuerungsverbrauch).

## Analyse des Ist-Zustands

### Aktueller Import (MesswerteService.java:69-70)
```java
Double total = Math.abs(Double.parseDouble(parts[1].trim()));
Double zev = Math.abs(Double.parseDouble(parts[2].trim()));
```
Alle Werte werden mit `Math.abs()` in positive Werte umgewandelt.

### Aktuelle Verteilung (MesswerteService.java:161-163)
```java
BigDecimal solarProduction = producers.stream()
    .map(m -> BigDecimal.valueOf(m.getTotal()))
    .reduce(BigDecimal.ZERO, BigDecimal::add);
```
Die Solarproduktion wird als Summe aller Producer berechnet.

### Aktuelle Statistik (StatistikService.java)
Die Statistik verwendet `sumTotalByEinheitTypAndZeitBetween()` für Producer und erwartet positive Werte.

---

## Geklärte Anforderungen

### Vorzeichen-Konvention
- **Solarproduktion** (eingespeister Strom): **negativ** (z.B. `-5.0`)
- **Steuerungsverbrauch** (verbrauchter Strom der Solarsteuerung): **positiv** (z.B. `+0.2`)
- Die Summe aller Producer ergibt die "Netto-Solarproduktion"

### Vorhandene Daten
- **Migration erforderlich:** Alle bestehenden Producer-Werte (total und zev) werden negiert

### ZEV-Spalte
- Ja, `zev` wird ebenfalls ohne `Math.abs()` gespeichert (gleiche Logik wie `total`)

### Statistik-Vergleiche
- Für **Anzeige und Vergleiche** werden **absolute Werte** verwendet
- Benutzer sehen weiterhin positive Produktionswerte

---

## Umsetzungsschritte

### Schritt 1: MesswerteService - Import anpassen
**Datei:** `backend-service/src/main/java/ch/nacht/service/MesswerteService.java`

**Änderung:**
```java
// Vorher (Zeile 69-70):
Double total = Math.abs(Double.parseDouble(parts[1].trim()));
Double zev = Math.abs(Double.parseDouble(parts[2].trim()));

// Nachher:
Double total = Double.parseDouble(parts[1].trim());
Double zev = Double.parseDouble(parts[2].trim());
```

**Auswirkung:** Werte werden so gespeichert, wie sie in der Datei stehen.

---

### Schritt 2: MesswerteService - Verteilung anpassen
**Datei:** `backend-service/src/main/java/ch/nacht/service/MesswerteService.java`

**Änderung in `calculateSolarDistribution()` (Zeile 161-163):**
```java
// Vorher:
BigDecimal solarProduction = producers.stream()
    .map(m -> BigDecimal.valueOf(m.getTotal()))
    .reduce(BigDecimal.ZERO, BigDecimal::add);

// Nachher:
// Summe aller Producer: negative Produktion + positiver Verbrauch = Netto-Produktion
BigDecimal netProduction = producers.stream()
    .map(m -> BigDecimal.valueOf(m.getTotal()))
    .reduce(BigDecimal.ZERO, BigDecimal::add);

// Netto-Produktion ist negativ, für Verteilung brauchen wir positiven Wert
BigDecimal solarProduction = netProduction.abs();

// Falls Netto-Produktion positiv ist (Verbrauch > Produktion), gibt es nichts zu verteilen
if (netProduction.compareTo(BigDecimal.ZERO) >= 0) {
    solarProduction = BigDecimal.ZERO;
}
```

**Auswirkung:**
- Solarsteuerung wird automatisch berücksichtigt
- Wenn der Steuerungsverbrauch die Produktion übersteigt, wird nichts verteilt

---

### Schritt 3: Verteilungsalgorithmen anpassen (falls nötig)
**Dateien:**
- `backend-service/src/main/java/ch/nacht/SolarDistribution.java`
- `backend-service/src/main/java/ch/nacht/ProportionalConsumptionDistribution.java`

**Prüfung:** Die Algorithmen erwarten bereits positive `solarProduction` Werte. Wenn Schritt 2 korrekt umgesetzt wird (Umwandlung in positiven Wert), sind keine Änderungen an den Algorithmen nötig.

---

### Schritt 4: StatistikService anpassen
**Datei:** `backend-service/src/main/java/ch/nacht/service/StatistikService.java`

Die Summen-Queries liefern nun negative Werte für Producer. Für die Anzeige werden **Absolutwerte** verwendet:

```java
// In berechneMonatsStatistik():
Double summeProducerTotal = messwerteRepository.sumTotalByEinheitTypAndZeitBetween(
        EinheitTyp.PRODUCER, vonDateTime, bisDateTime);
Double summeProducerZev = messwerteRepository.sumZevByEinheitTypAndZeitBetween(
        EinheitTyp.PRODUCER, vonDateTime, bisDateTime);

// Für Anzeige absolutieren
dto.setSummeProducerTotal(summeProducerTotal != null ? Math.abs(summeProducerTotal) : 0.0);
dto.setSummeProducerZev(summeProducerZev != null ? Math.abs(summeProducerZev) : 0.0);
```

---

### Schritt 5: Vergleiche in StatistikService
**Datei:** `backend-service/src/main/java/ch/nacht/service/StatistikService.java`

Da in Schritt 4 bereits Absolutwerte im DTO gespeichert werden, funktionieren die Vergleiche unverändert:

```java
private void vergleicheSummen(MonatsStatistikDTO dto) {
    // Werte sind bereits absolutiert
    Double summeC = dto.getSummeProducerZev();  // bereits abs()
    Double summeD = dto.getSummeConsumerZev();
    Double summeE = dto.getSummeConsumerZevCalculated();
    // ... Rest bleibt unverändert
}
```

**Auch `ermittleTageAbweichungen()` anpassen:** Die Tages-Summen für Producer müssen ebenfalls absolutiert werden.

---

### Schritt 6: Tests anpassen
**Dateien:**
- `backend-service/src/test/java/ch/nacht/SolarDistributionTest.java`
- `backend-service/src/test/java/ch/nacht/ProportionalConsumptionDistributionTest.java`

**Prüfung:** Die Unit-Tests für die Verteilungsalgorithmen sollten unverändert bleiben, da sie bereits positive Produktionswerte erwarten.

**Neue Tests hinzufügen:**
- Test für Import mit negativen Producer-Werten
- Test für Netto-Produktion (Produktion + Steuerungsverbrauch)
- Test für den Fall, dass Verbrauch > Produktion

---

### Schritt 7: Integration Tests
**Datei:** `backend-service/src/test/java/ch/nacht/repository/EinheitRepositoryIT.java` (und ggf. neue IT-Klassen)

Neue Integrationstests für:
- CSV-Import mit negativen Werten
- Berechnung mit mehreren Producern (Solar + Steuerung)
- Statistik mit negativen Producer-Werten

---

### Schritt 8: Daten-Migration
**Datei:** `backend-service/src/main/resources/db/migration/V[N]__negate_producer_values.sql`

**Flyway-Migration:**
```sql
-- Negiere alle Producer-Werte (total und zev)
UPDATE messwerte m
SET total = -total,
    zev = -zev
FROM einheit e
WHERE m.einheit_id = e.id
  AND e.typ = 'PRODUCER';
```

**Hinweis:** Die Migrationsnummer `[N]` muss die nächste verfügbare Nummer sein.

---

## Reihenfolge der Umsetzung

1. **Schritt 1:** Import anpassen (entfernen von `Math.abs()`)
2. **Schritt 2:** Verteilungsberechnung anpassen
3. **Schritt 4 + 5:** StatistikService anpassen (Absolutwerte für Anzeige/Vergleich)
4. **Schritt 6 + 7:** Tests schreiben/anpassen
5. **Schritt 8:** Daten-Migration (Flyway-Skript)

---

## Risiken und Mitigationen

| Risiko | Mitigation |
|--------|------------|
| Migration schlägt fehl | Backup vor Migration, Rollback-Skript bereithalten |
| Vergleiche in Statistik fehlerhaft | Umfassende Tests vor Deployment |
| Doppelte Migration | Flyway verhindert doppelte Ausführung automatisch |

---

## Testplan

1. **Unit Tests:** Verteilungsalgorithmen mit positiven Werten (unverändert)
2. **Unit Tests:** Import-Service mit verschiedenen Vorzeichen
3. **Integration Tests:** Kompletter Workflow (Import → Berechnung → Statistik)
4. **Manueller Test:** CSV mit echten Daten importieren und Statistik prüfen

---

## Schätzung

| Schritt | Aufwand |
|---------|---------|
| Klärung Fragen | - |
| Import anpassen | Klein |
| Verteilung anpassen | Mittel |
| Statistik anpassen | Mittel |
| Tests | Mittel |
| Frontend (falls nötig) | Klein |
| **Gesamt** | **Mittel** |
