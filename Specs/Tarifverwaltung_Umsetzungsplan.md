# Umsetzungsplan: Tarifverwaltung

## Zusammenfassung

Implementierung einer Tarifverwaltung mit Gültigkeitszeiträumen. Tarife werden in der Datenbank gespeichert und bei der Rechnungsgenerierung werden die für den jeweiligen Zeitpunkt gültigen Tarife verwendet. Es können mehrere Tarifzeilen pro Typ auf einer Rechnung erscheinen, wenn sich der Tarif innerhalb des Rechnungszeitraums ändert.

---

## Phase 1: Backend - Datenbank und Entity

### 1.1 Flyway Migration für Tarif-Tabelle

**Datei:** `backend-service/src/main/resources/db/migration/V22__Create_Tarif_Table.sql`

```sql
CREATE SEQUENCE IF NOT EXISTS zev.tarif_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE zev.tarif (
    id BIGINT PRIMARY KEY DEFAULT nextval('zev.tarif_seq'),
    bezeichnung VARCHAR(30) NOT NULL,
    tariftyp VARCHAR(10) NOT NULL CHECK (tariftyp IN ('ZEV', 'VNB')),
    preis NUMERIC(10, 5) NOT NULL,
    gueltig_von DATE NOT NULL,
    gueltig_bis DATE NOT NULL,
    CONSTRAINT tarif_gueltig_check CHECK (gueltig_von <= gueltig_bis)
);

CREATE INDEX idx_tarif_typ_gueltig ON zev.tarif (tariftyp, gueltig_von, gueltig_bis);
```

### 1.2 Entity-Klasse

**Datei:** `backend-service/src/main/java/ch/nacht/entity/Tarif.java`

```java
@Entity
@Table(name = "tarif", schema = "zev")
public class Tarif {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tarif_seq")
    @SequenceGenerator(name = "tarif_seq", sequenceName = "zev.tarif_seq", allocationSize = 1)
    private Long id;

    @NotBlank
    @Size(max = 30)
    @Column(name = "bezeichnung", length = 30, nullable = false)
    private String bezeichnung;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tariftyp", length = 10, nullable = false)
    private TarifTyp tariftyp;

    @NotNull
    @Column(name = "preis", precision = 10, scale = 5, nullable = false)
    private BigDecimal preis;

    @NotNull
    @Column(name = "gueltig_von", nullable = false)
    private LocalDate gueltigVon;

    @NotNull
    @Column(name = "gueltig_bis", nullable = false)
    private LocalDate gueltigBis;

    // Getters, Setters, Constructors
}
```

### 1.3 Enum für Tariftyp

**Datei:** `backend-service/src/main/java/ch/nacht/entity/TarifTyp.java`

```java
public enum TarifTyp {
    ZEV,  // Strombezug aus dem ZEV (messwerte.zev_calculated)
    VNB   // Strombezug vom Verteilnetzbetreiber (messwerte.total - messwerte.zev_calculated)
}
```

---

## Phase 2: Backend - Repository und Service

### 2.1 Repository

**Datei:** `backend-service/src/main/java/ch/nacht/repository/TarifRepository.java`

```java
public interface TarifRepository extends JpaRepository<Tarif, Long> {

    // Alle Tarife eines Typs, die einen bestimmten Zeitraum überlappen
    @Query("SELECT t FROM Tarif t WHERE t.tariftyp = :typ " +
           "AND t.gueltigVon <= :bis AND t.gueltigBis >= :von " +
           "ORDER BY t.gueltigVon")
    List<Tarif> findByTariftypAndZeitraumOverlapping(
        @Param("typ") TarifTyp typ,
        @Param("von") LocalDate von,
        @Param("bis") LocalDate bis
    );

    // Prüfen auf Überlappung (für Validierung)
    @Query("SELECT COUNT(t) > 0 FROM Tarif t WHERE t.tariftyp = :typ " +
           "AND t.id != :excludeId " +
           "AND t.gueltigVon <= :bis AND t.gueltigBis >= :von")
    boolean existsOverlappingTarif(
        @Param("typ") TarifTyp typ,
        @Param("von") LocalDate von,
        @Param("bis") LocalDate bis,
        @Param("excludeId") Long excludeId
    );

    List<Tarif> findAllByOrderByTariftypAscGueltigVonDesc();
}
```

### 2.2 Service

**Datei:** `backend-service/src/main/java/ch/nacht/service/TarifService.java`

```java
@Service
public class TarifService {

    private final TarifRepository tarifRepository;

    public List<Tarif> getAllTarife() { ... }

    public Tarif saveTarif(Tarif tarif) {
        // Validierung: Keine Überlappung
        Long excludeId = tarif.getId() != null ? tarif.getId() : -1L;
        if (tarifRepository.existsOverlappingTarif(
                tarif.getTariftyp(),
                tarif.getGueltigVon(),
                tarif.getGueltigBis(),
                excludeId)) {
            throw new IllegalArgumentException("Tarif überschneidet sich mit bestehendem Tarif");
        }
        return tarifRepository.save(tarif);
    }

    public void deleteTarif(Long id) { ... }

    // Für Rechnungsberechnung: Alle gültigen Tarife im Zeitraum
    public List<Tarif> getTarifeForZeitraum(TarifTyp typ, LocalDate von, LocalDate bis) {
        return tarifRepository.findByTariftypAndZeitraumOverlapping(typ, von, bis);
    }

    // Prüft ob für gesamten Zeitraum Tarife vorhanden sind
    public void validateTarifAbdeckung(LocalDate von, LocalDate bis) {
        // Prüft für ZEV und VNB ob jeder Tag abgedeckt ist
        // Wirft Exception wenn Lücken vorhanden
    }
}
```

### 2.3 Controller

**Datei:** `backend-service/src/main/java/ch/nacht/controller/TarifController.java`

```java
@RestController
@RequestMapping("/api/tarife")
@PreAuthorize("hasRole('zev_admin')")
public class TarifController {

    @GetMapping
    public List<Tarif> getAllTarife() { ... }

    @PostMapping
    public ResponseEntity<Tarif> createTarif(@Valid @RequestBody Tarif tarif) { ... }

    @PutMapping("/{id}")
    public ResponseEntity<Tarif> updateTarif(@PathVariable Long id, @Valid @RequestBody Tarif tarif) { ... }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTarif(@PathVariable Long id) { ... }
}
```

---

## Phase 3: Backend - Rechnungsberechnung anpassen

### 3.1 Neues DTO für Tarifzeilen

**Datei:** `backend-service/src/main/java/ch/nacht/dto/TarifZeileDTO.java`

```java
public class TarifZeileDTO {
    private String bezeichnung;
    private LocalDate von;
    private LocalDate bis;
    private double menge;      // kWh (gerundet)
    private double preis;      // CHF/kWh
    private double betrag;     // CHF
    private TarifTyp typ;
}
```

### 3.2 RechnungDTO erweitern

**Änderung in:** `backend-service/src/main/java/ch/nacht/dto/RechnungDTO.java`

```java
// Ersetze einzelne zev*/ewb* Felder durch Liste:
private List<TarifZeileDTO> tarifZeilen = new ArrayList<>();

// Behalte für Abwärtskompatibilität (deprecated):
// private double zevMenge, zevPreis, zevBetrag, ewbMenge, ewbPreis, ewbBetrag;
```

### 3.3 RechnungService anpassen

**Änderung in:** `backend-service/src/main/java/ch/nacht/service/RechnungService.java`

Neue Logik für Rechnungsberechnung:

```java
public RechnungDTO berechneRechnung(Einheit einheit, LocalDate von, LocalDate bis) {
    // 1. Validiere Tarifabdeckung
    tarifService.validateTarifAbdeckung(von, bis);

    // 2. Hole alle gültigen Tarife für ZEV und VNB
    List<Tarif> zevTarife = tarifService.getTarifeForZeitraum(TarifTyp.ZEV, von, bis);
    List<Tarif> vnbTarife = tarifService.getTarifeForZeitraum(TarifTyp.VNB, von, bis);

    // 3. Für jeden Tarif-Zeitraum: Summe der Messwerte berechnen
    List<TarifZeileDTO> zeilen = new ArrayList<>();

    for (Tarif tarif : zevTarife) {
        LocalDate tarifVon = max(von, tarif.getGueltigVon());
        LocalDate tarifBis = min(bis, tarif.getGueltigBis());

        Double sumZev = messwerteRepository.sumZevCalculatedByEinheitAndZeitBetween(
            einheit, tarifVon.atStartOfDay(), tarifBis.plusDays(1).atStartOfDay());

        double menge = Math.round(sumZev != null ? sumZev : 0.0);
        double betrag = menge * tarif.getPreis().doubleValue();

        zeilen.add(new TarifZeileDTO(tarif, tarifVon, tarifBis, menge, betrag));
    }

    // Analog für VNB-Tarife mit (total - zev_calculated)

    // 4. Summe aller Zeilen = totalBetrag
    // 5. Rundung auf 5 Rappen
}
```

### 3.4 Repository-Methode ergänzen

**Änderung in:** `backend-service/src/main/java/ch/nacht/repository/MesswerteRepository.java`

Sicherstellen, dass die bestehenden Methoden mit dem neuen Zeitbereich-Splitting funktionieren.

---

## Phase 4: Backend - RechnungPdfService anpassen

### 4.1 PDF-Generierung für multiple Tarifzeilen

**Änderung in:** `backend-service/src/main/java/ch/nacht/service/RechnungPdfService.java`

Die Tabelle für Tarifzeilen muss dynamisch generiert werden:

```html
<table>
  <thead>
    <tr>
      <th>Bezeichnung</th>
      <th>Zeitraum</th>
      <th>Menge (kWh)</th>
      <th>Preis (CHF/kWh)</th>
      <th>Betrag (CHF)</th>
    </tr>
  </thead>
  <tbody>
    <!-- Loop über alle TarifZeileDTO -->
    <tr>
      <td>vZEV PV Tarif</td>
      <td>01.01. - 31.01.2024</td>
      <td class="number">150</td>
      <td class="number">0.20000</td>
      <td class="number">30.00</td>
    </tr>
    <!-- ... weitere Zeilen ... -->
  </tbody>
</table>
```

---

## Phase 5: Backend - Konfiguration bereinigen

### 5.1 application.yml bereinigen

**Änderung in:** `backend-service/src/main/resources/application.yml`

Entferne den `tarif`-Block:

```yaml
rechnung:
  zahlungsfrist: "30 Tage"
  iban: "CH70 0630 0016 9464 5991 0"
  steller:
    ...
  adresse:
    ...
  # tarif: ENTFERNEN
```

### 5.2 RechnungConfig anpassen

**Änderung in:** `backend-service/src/main/java/ch/nacht/config/RechnungConfig.java`

Entferne die innere Klassen `Tarif` und `TarifDetails` sowie das Feld `tarif`.

---

## Phase 6: Frontend - Tarifverwaltung

### 6.1 Model

**Datei:** `frontend-service/src/app/models/tarif.model.ts`

```typescript
export interface Tarif {
  id?: number;
  bezeichnung: string;
  tariftyp: 'ZEV' | 'VNB';
  preis: number;
  gueltigVon: string;  // YYYY-MM-DD
  gueltigBis: string;  // YYYY-MM-DD
}
```

### 6.2 Service

**Datei:** `frontend-service/src/app/services/tarif.service.ts`

```typescript
@Injectable({ providedIn: 'root' })
export class TarifService {
  private apiUrl = '/api/tarife';

  getAllTarife(): Observable<Tarif[]> { ... }
  createTarif(tarif: Tarif): Observable<Tarif> { ... }
  updateTarif(id: number, tarif: Tarif): Observable<Tarif> { ... }
  deleteTarif(id: number): Observable<void> { ... }
}
```

### 6.3 Komponenten

**Dateien:**
- `frontend-service/src/app/components/tarif-list/tarif-list.component.ts`
- `frontend-service/src/app/components/tarif-list/tarif-list.component.html`
- `frontend-service/src/app/components/tarif-list/tarif-list.component.css`
- `frontend-service/src/app/components/tarif-form/tarif-form.component.ts`
- `frontend-service/src/app/components/tarif-form/tarif-form.component.html`
- `frontend-service/src/app/components/tarif-form/tarif-form.component.css`

Aufbau analog zu Einheiten-Verwaltung:
- Tabelle mit Sortierung
- Inline-Formular für Erstellen/Bearbeiten
- Löschen mit Bestätigung
- Validierung für Überlappung (Fehlermeldung vom Backend anzeigen)

### 6.4 Routing

**Änderung in:** `frontend-service/src/app/app.routes.ts`

```typescript
{ path: 'tarife', component: TarifListComponent, canActivate: [AuthGuard], data: { roles: ['zev_admin'] } },
```

### 6.5 Navigation

**Änderung in:** `frontend-service/src/app/app.component.html`

Menüeintrag unterhalb "Einheiten":

```html
<a routerLink="/tarife" routerLinkActive="zev-navbar__link--active" class="zev-navbar__link">
  {{ 'TARIFE' | translate }}
</a>
```

---

## Phase 7: Übersetzungen

### 7.1 Flyway Migration

**Datei:** `backend-service/src/main/resources/db/migration/V23__Add_Tarif_Translations.sql`

```sql
INSERT INTO translation (key, deutsch, englisch) VALUES
('TARIFE', 'Tarife', 'Tariffs'),
('TARIFVERWALTUNG', 'Tarifverwaltung', 'Tariff Management'),
('BEZEICHNUNG', 'Bezeichnung', 'Description'),
('TARIFTYP', 'Tariftyp', 'Tariff Type'),
('PREIS', 'Preis (CHF/kWh)', 'Price (CHF/kWh)'),
('GUELTIG_VON', 'Gültig von', 'Valid from'),
('GUELTIG_BIS', 'Gültig bis', 'Valid until'),
('TARIF_UEBERSCHNEIDUNG', 'Tarif überschneidet sich mit bestehendem Tarif', 'Tariff overlaps with existing tariff'),
('TARIF_LUECKE', 'Für den Zeitraum fehlen gültige Tarife', 'Missing valid tariffs for the period'),
('ZEV_TARIF', 'ZEV (Eigenverbrauch)', 'ZEV (Self-consumption)'),
('VNB_TARIF', 'VNB (Netzbezug)', 'Grid (Network supply)'),
('NEUER_TARIF', 'Neuer Tarif', 'New Tariff'),
('TARIF_BEARBEITEN', 'Tarif bearbeiten', 'Edit Tariff'),
('TARIF_LOESCHEN_BESTAETIGUNG', 'Möchten Sie diesen Tarif wirklich löschen?', 'Do you really want to delete this tariff?'),
('ZEITRAUM', 'Zeitraum', 'Period')
ON CONFLICT (key) DO NOTHING;
```

---

## Umsetzungsreihenfolge

| # | Aufgabe | Abhängigkeit |
|---|---------|--------------|
| 1 | Flyway Migration V22 (Tarif-Tabelle) | - |
| 2 | Entity Tarif + TarifTyp Enum | 1 |
| 3 | TarifRepository | 2 |
| 4 | TarifService mit Validierung | 3 |
| 5 | TarifController | 4 |
| 6 | TarifZeileDTO erstellen | - |
| 7 | RechnungDTO anpassen | 6 |
| 8 | RechnungService anpassen | 4, 7 |
| 9 | RechnungPdfService anpassen | 8 |
| 10 | RechnungConfig bereinigen | 8 |
| 11 | application.yml bereinigen | 10 |
| 12 | Frontend: Model + Service | 5 |
| 13 | Frontend: TarifListComponent | 12 |
| 14 | Frontend: TarifFormComponent | 12 |
| 15 | Frontend: Routing + Navigation | 13, 14 |
| 16 | Flyway Migration V23 (Übersetzungen) | - |

---

## Validierungen

### Backend-Validierungen
1. **Pflichtfelder:** Alle Felder müssen ausgefüllt sein
2. **Datumsprüfung:** `gueltig_von <= gueltig_bis`
3. **Keine Überlappung:** Pro Tariftyp darf nur ein Tarif für einen Tag gültig sein
4. **Preis:** Positiver Wert mit max. 5 Nachkommastellen

### Rechnungs-Validierung
1. **Tarifabdeckung:** Für jeden Tag des Rechnungszeitraums muss ein ZEV- und ein VNB-Tarif existieren
2. **Fehlermeldung:** Bei Lücken wird eine aussagekräftige Fehlermeldung angezeigt

---

## Offene Punkte / Annahmen

1. **Annahme:** Die Bezeichnung des Tarifs wird auf der Rechnung angezeigt, nicht "ZEV" oder "VNB"
2. **Annahme:** Bei der Rechnungsanzeige werden die Zeilen nach Tariftyp und dann nach Datum sortiert
3. **Annahme:** Die alten Tarife aus application.yml werden nicht automatisch migriert (Admin erfasst manuell)
4. **Annahme:** Das Löschen eines Tarifs ist immer möglich (keine Prüfung auf verwendete Rechnungen, da Rechnungen nicht persistiert werden)
