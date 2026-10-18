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

---

## Ergänzung 22.01.2026: Tarif kopieren (Spec FR-9)

> Bis 18.10.2026 eigener Plan `Specs/Tarifverwaltung-Tarif-kopieren_Umsetzungsplan.md` zur Spec
> `Specs/Tarifverwaltung-Tarif-kopieren.md`; beide sind in die Tarifverwaltung übernommen. Die
> Nummern unten (FR-1 bis FR-3) sind die der damaligen Spec — heute **FR-9**.

### Zusammenfassung

Erweiterung der bestehenden Tarifverwaltung um eine "Kopieren"-Funktion im Kebab-Menü. Beim Kopieren wird das Tarif-Formular mit den Werten des ausgewählten Tarifs vorausgefüllt geöffnet (ohne ID), sodass der Benutzer einen neuen Tarif basierend auf einem bestehenden erstellen kann.

---

### Betroffene Komponenten

| Typ | Datei | Änderungsart |
|-----|-------|--------------|
| Frontend Component | `frontend-service/src/app/components/tarif-list/tarif-list.component.ts` | Änderung |
| Frontend Icons | `frontend-service/src/app/components/icon/icons.ts` | Änderung |
| DB Migration | `backend-service/src/main/resources/db/migration/V35__Add_Kopieren_Translation.sql` | Neu |

---

### Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Icon hinzufügen | "copy" Icon zur Icon-Registry hinzufügen |
| [x] | 2. Kebab-Menü erweitern | Menüpunkt "Kopieren" zwischen "Bearbeiten" und "Löschen" einfügen |
| [x] | 3. Kopier-Logik implementieren | `onCopy()` Methode implementieren, die Tarif ohne ID ins Formular lädt |
| [x] | 4. Übersetzung hinzufügen | Flyway-Migration für Translation-Key "KOPIEREN" |

---

### Detailbeschreibung der Phasen

#### Phase 1: Icon hinzufügen

**Datei:** `frontend-service/src/app/components/icon/icons.ts`

Feather Icon "copy" hinzufügen:

```typescript
'copy': '<rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>',
```

#### Phase 2: Kebab-Menü erweitern

**Datei:** `frontend-service/src/app/components/tarif-list/tarif-list.component.ts`

Menüpunkte anpassen (Zeile 31-34):

```typescript
menuItems: KebabMenuItem[] = [
  { label: 'BEARBEITEN', action: 'edit', icon: 'edit-2' },
  { label: 'KOPIEREN', action: 'copy', icon: 'copy' },
  { label: 'LOESCHEN', action: 'delete', danger: true, icon: 'trash-2' }
];
```

#### Phase 3: Kopier-Logik implementieren

**Datei:** `frontend-service/src/app/components/tarif-list/tarif-list.component.ts`

Neue Methode `onCopy()` hinzufügen:

```typescript
onCopy(tarif: Tarif): void {
  // Kopie ohne ID erstellen - wird als neuer Tarif behandelt
  const { id, ...tarifOhneId } = tarif;
  this.selectedTarif = { ...tarifOhneId } as Tarif;
  this.showForm = true;
}
```

Switch-Case in `onMenuAction()` erweitern (Zeile 82-91):

```typescript
onMenuAction(action: string, tarif: Tarif): void {
  switch (action) {
    case 'edit':
      this.onEdit(tarif);
      break;
    case 'copy':
      this.onCopy(tarif);
      break;
    case 'delete':
      this.onDelete(tarif.id);
      break;
  }
}
```

#### Phase 4: Übersetzung hinzufügen

**Datei:** `backend-service/src/main/resources/db/migration/V[XX]__Add_Kopieren_Translation.sql`

```sql
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('KOPIEREN', 'Kopieren', 'Copy')
ON CONFLICT (key) DO NOTHING;
```

---

### Validierungen

#### Frontend-Validierungen
- Keine zusätzlichen Validierungen erforderlich
- Bestehende Formular-Validierungen greifen beim kopierten Tarif

#### Backend-Validierungen
- Keine zusätzlichen Validierungen erforderlich
- Bestehende Überlappungsprüfung greift beim Speichern des kopierten Tarifs

---

### Offene Punkte / Annahmen

1. **Annahme:** Beim Kopieren werden alle Felder übernommen ausser der ID
2. **Annahme:** Die Gültigkeitsdaten werden 1:1 übernommen - der Benutzer muss diese ggf. anpassen, um Überlappungskonflikte zu vermeiden
3. **Annahme:** Die Übersetzung "KOPIEREN" existiert noch nicht in der Datenbank
4. **Annahme:** Das Formular zeigt beim kopierten Tarif den Titel "Neuer Tarif" (da keine ID vorhanden)

## Ergänzung 20.03.2026 und 09.06.2026: Grundgebühr und Rechnungen für Produzenten (Spec FR-7, FR-8)

> Bis 18.10.2026 eigener Plan `Specs/Tarifverwaltung-Grundgebuehr_Umsetzungsplan.md` zur Spec
> `Specs/Tarifverwaltung-Grundgebuehr.md`; beide sind in die Tarifverwaltung übernommen. Die
> Nummern unten sind die der damaligen Spec: FR-1, FR-2, FR-4 bis FR-8 → heute **FR-7**,
> FR-3 (Produzenten) → heute **FR-8**. Die „Erweiterung: Konfigurierbare
> Produzenten-Verrechnung (FR-7)" stammt vom 09.06.2026. Die Grundgebühr nach Monaten wurde am
> 10.10.2026 um „pro Tag" ergänzt (siehe unten, Spec FR-3).

### Zusammenfassung

Einführung des neuen Tariftyps `GRUNDGEBUEHR` (monatlicher Festpreis pro Stromzähler). Die Grundgebühr ergänzt die bestehenden kWh-basierten Tariftypen ZEV und VNB und wird pro Einheit (Consumer **und** Producer) in Anzahl ganzer Kalendermonate berechnet. Produzenten erhalten dabei erstmals eigene Rechnungen mit ausschliesslich Grundgebühr-Positionen.

---

### Betroffene Komponenten

| Typ | Datei | Änderungsart |
|-----|-------|--------------|
| Backend Enum | `backend-service/src/main/java/ch/nacht/entity/TarifTyp.java` | Änderung |
| Backend DTO | `backend-service/src/main/java/ch/nacht/dto/TarifZeileDTO.java` | Änderung |
| Backend Service | `backend-service/src/main/java/ch/nacht/service/RechnungService.java` | Änderung |
| Backend Report | `backend-service/src/main/resources/reports/rechnung.jrxml` | Änderung |
| Frontend Enum/Model | `frontend-service/src/app/models/tarif.model.ts` | Änderung |
| Frontend Component | `frontend-service/src/app/components/tarif-form/tarif-form.component.ts` | Änderung |
| Frontend Component | `frontend-service/src/app/components/rechnungen/rechnungen.component.ts` | Änderung |
| Frontend Template | `frontend-service/src/app/components/rechnungen/rechnungen.component.html` | Änderung |
| DB Migration | `backend-service/src/main/resources/db/migration/V49__Add_Grundgebuehr_Translations.sql` | Neu |

> **Keine DB-Migration für TarifTyp nötig:** Die Spalte `tariftyp` in `zev.tarif` ist `VARCHAR`, kein DB-Enum. Der neue Wert `GRUNDGEBUEHR` wird automatisch akzeptiert.

---

### Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Backend – TarifTyp Enum | `GRUNDGEBUEHR` als neuen Wert in `TarifTyp.java` ergänzen |
| [x] | 2. Backend – TarifZeileDTO | Feld `mengeneinheit` (String) hinzufügen; wird für PDF-Zeile benötigt (kWh vs. Monate) |
| [x] | 3. Backend – RechnungService | Grundgebühr-Berechnung (volle Monate) + PRODUCER-Support in `berechneRechnungen` |
| [x] | 4. Backend – rechnung.jrxml | Statisches `CHF / kWh` durch dynamisches `$F{mengeneinheit}` ersetzen; Field deklarieren |
| [x] | 5. Frontend – tarif.model.ts | `GRUNDGEBUEHR` zum `TarifTyp`-Enum ergänzen |
| [x] | 6. Frontend – TarifFormComponent | `GRUNDGEBUEHR`-Option im Tariftyp-Dropdown ergänzen |
| [x] | 7. Frontend – RechnungenComponent | Alle Einheiten (CONSUMER + PRODUCER) laden und mit Typ-Label anzeigen |
| [x] | 8. Übersetzungen | Flyway-Migration V49 für neue Translation-Keys |

---

### Detailbeschreibung der Phasen

#### Phase 1: Backend – TarifTyp Enum

**Datei:** `backend-service/src/main/java/ch/nacht/entity/TarifTyp.java`

```java
public enum TarifTyp {
    ZEV,
    VNB,
    GRUNDGEBUEHR
}
```

---

#### Phase 2: Backend – TarifZeileDTO

**Datei:** `backend-service/src/main/java/ch/nacht/dto/TarifZeileDTO.java`

Neues Feld `mengeneinheit` (String) ergänzen:
- Für ZEV/VNB-Zeilen: `"kWh"`
- Für GRUNDGEBUEHR-Zeilen: `"MONAT"`

**Konstruktor anpassen:**
```java
public TarifZeileDTO(String bezeichnung, LocalDate von, LocalDate bis,
                     double menge, double preis, double betrag,
                     TarifTyp typ, String mengeneinheit) { ... }
```

**Bestehende Aufrufe in `RechnungService.berechneTarifZeilen` anpassen:**
```java
new TarifZeileDTO(
    tarif.getBezeichnung(), effectiveVon, effectiveBis,
    menge, preis, betrag, typ, "kWh"
)
```

---

#### Phase 3: Backend – RechnungService

**Datei:** `backend-service/src/main/java/ch/nacht/service/RechnungService.java`

##### 3a: Neue Methode `berechneGrundgebuehrZeilen`

Berechnet Grundgebühr-Zeilen pro Tarif. Gezählt werden **vollständige Kalendermonate** im Überschneidungszeitraum von Tarifgültigkeit und Rechnungsperiode.

**Definition: vollständiger Kalendermonat** = erster und letzter Tag des Kalendermonats liegen beide im effektiven Zeitraum.

```java
private double berechneGrundgebuehrZeilen(RechnungDTO rechnung,
                                           LocalDate von, LocalDate bis,
                                           List<Tarif> tarife) {
    double total = 0.0;

    for (Tarif tarif : tarife) {
        LocalDate effVon = tarif.getGueltigVon().isBefore(von) ? von : tarif.getGueltigVon();
        LocalDate effBis = tarif.getGueltigBis().isAfter(bis) ? bis : tarif.getGueltigBis();

        int monate = zaehleVolleMonate(effVon, effBis);
        if (monate <= 0) continue;

        double preis = tarif.getPreis().doubleValue();
        double betrag = monate * preis;

        rechnung.addTarifZeile(new TarifZeileDTO(
            tarif.getBezeichnung(), effVon, effBis,
            monate, preis, betrag, TarifTyp.GRUNDGEBUEHR, "MONAT"
        ));
        total += betrag;
    }
    return total;
}

/**
 * Zählt vollständige Kalendermonate im Zeitraum [von, bis] (inklusiv).
 * Ein Monat gilt als vollständig, wenn sein erster und letzter Tag im Zeitraum liegen.
 */
private int zaehleVolleMonate(LocalDate von, LocalDate bis) {
    int count = 0;
    LocalDate monthStart = von.withDayOfMonth(1);
    while (!monthStart.isAfter(bis)) {
        LocalDate monthEnd = monthStart.withDayOfMonth(
            monthStart.getMonth().length(java.time.Year.isLeap(monthStart.getYear()))
        );
        if (!monthStart.isBefore(von) && !monthEnd.isAfter(bis)) {
            count++;
        }
        monthStart = monthStart.plusMonths(1);
    }
    return count;
}
```

##### 3b: `berechneRechnung` um Grundgebühr erweitern

In der bestehenden Methode `berechneRechnung` nach den ZEV/VNB-Zeilen:
```java
// GRUNDGEBUEHR (optional - kein Fehler wenn kein Tarif vorhanden)
List<Tarif> grundgebuehrTarife = tarifService.getTarifeForZeitraum(TarifTyp.GRUNDGEBUEHR, von, bis);
if (!grundgebuehrTarife.isEmpty()) {
    totalBetrag += berechneGrundgebuehrZeilen(rechnung, von, bis, grundgebuehrTarife);
}
```

##### 3c: `berechneRechnungen` – PRODUCER-Support

Aktuell: nur CONSUMER-Einheiten verarbeitet. Neu: PRODUCER-Einheiten erhalten ausschliesslich Grundgebühr-Positionen.

```java
for (Long einheitId : einheitIds) {
    einheitRepository.findById(einheitId).ifPresent(einheit -> {
        if (einheit.getTyp() == EinheitTyp.CONSUMER) {
            // Bestehende Logik unverändert
            List<Mieter> mieter = mieterService.getMieterForQuartal(einheitId, von, bis);
            if (mieter.isEmpty()) {
                rechnungen.add(berechneRechnung(einheit, null, von, bis));
            } else {
                for (Mieter m : mieter) {
                    LocalDate effVon = ...;
                    LocalDate effBis = ...;
                    rechnungen.add(berechneRechnung(einheit, m, effVon, effBis));
                }
            }
        } else if (einheit.getTyp() == EinheitTyp.PRODUCER) {
            // Nur Grundgebühr für Produzenten
            RechnungDTO rechnung = berechneProduzentenRechnung(einheit, von, bis);
            if (!rechnung.getTarifZeilen().isEmpty()) {
                rechnungen.add(rechnung);
            }
        }
    });
}
```

##### 3d: Neue Methode `berechneProduzentenRechnung`

```java
private RechnungDTO berechneProduzentenRechnung(Einheit einheit, LocalDate von, LocalDate bis) {
    RechnungDTO rechnung = new RechnungDTO();
    rechnung.setEinheitId(einheit.getId());
    rechnung.setEinheitName(einheit.getName());
    rechnung.setMesspunkt(einheit.getMesspunkt());
    rechnung.setVon(von);
    rechnung.setBis(bis);
    rechnung.setErstellungsdatum(LocalDate.now());

    List<Tarif> tarife = tarifService.getTarifeForZeitraum(TarifTyp.GRUNDGEBUEHR, von, bis);
    double total = berechneGrundgebuehrZeilen(rechnung, von, bis, tarife);
    double endBetrag = RechnungService.roundTo5Rappen(total);
    rechnung.setTotalBetrag(total);
    rechnung.setRundung(endBetrag - total);
    rechnung.setEndBetrag(endBetrag);

    EinstellungenDTO einstellungen = einstellungenService.getEinstellungenOrThrow();
    RechnungKonfigurationDTO config = einstellungen.getRechnung();
    rechnung.setZahlungsfrist(config.getZahlungsfrist());
    rechnung.setIban(config.getIban());
    RechnungKonfigurationDTO.StellerDTO steller = config.getSteller();
    rechnung.setStellerName(steller.getName());
    rechnung.setStellerStrasse(steller.getStrasse());
    rechnung.setStellerPlzOrt(steller.getPlz() + " " + steller.getOrt());

    return rechnung;
}
```

##### 3e: Tarif-Validierung für Konsumenten einschränken

Die Methode `validateTarifAbdeckung` (für ZEV/VNB) soll nur aufgerufen werden, wenn CONSUMER-Einheiten im Request vorhanden sind:

```java
boolean hasConsumers = einheitIds.stream()
    .anyMatch(id -> einheitRepository.findById(id)
        .map(e -> e.getTyp() == EinheitTyp.CONSUMER)
        .orElse(false));

if (hasConsumers) {
    tarifService.validateTarifAbdeckung(von, bis);
}
```

> **Hinweis:** Performance-Optimierung möglich (Einheiten einmalig laden), aber für die Umsetzung reicht die einfachere Variante.

---

#### Phase 4: Backend – rechnung.jrxml

**Datei:** `backend-service/src/main/resources/reports/rechnung.jrxml`

##### 4a: Field-Deklaration ergänzen

```xml
<field name="mengeneinheit" class="java.lang.String"/>
```

##### 4b: Statisches `CHF / kWh` durch dynamisches Feld ersetzen

Aktuell (staticText):
```xml
<element kind="staticText" ...>
    <text><![CDATA[CHF / kWh]]></text>
```

Neu (textField):
```xml
<element kind="textField" ...>
    <expression><![CDATA["CHF / " + $F{mengeneinheit}]]></expression>
```

> **Build-Hinweis:** Nach Änderung am JRXML muss `mvn compile` ausgeführt werden, damit das compilierte `.jasper`-File neu generiert wird.

---

#### Phase 5: Frontend – tarif.model.ts

**Datei:** `frontend-service/src/app/models/tarif.model.ts`

```typescript
export enum TarifTyp {
  ZEV = 'ZEV',
  VNB = 'VNB',
  GRUNDGEBUEHR = 'GRUNDGEBUEHR'
}
```

---

#### Phase 6: Frontend – TarifFormComponent

**Datei:** `frontend-service/src/app/components/tarif-form/tarif-form.component.ts`

```typescript
tarifTypOptions = [
  { value: TarifTyp.ZEV, label: 'ZEV (Solarstrom)' },
  { value: TarifTyp.VNB, label: 'VNB (Netzstrom)' },
  { value: TarifTyp.GRUNDGEBUEHR, label: 'Grundgebühr (CHF/Monat/Zähler)' }
];
```

> Alle Label-Texte im Template über `TranslatePipe` ausgeben – die hardcodierten Labels hier dienen nur als Fallback im `value`-Binding. Wenn das Template bereits `translate` verwendet, Labels durch Translation-Keys ersetzen.

---

#### Phase 7: Frontend – RechnungenComponent

**Datei:** `frontend-service/src/app/components/rechnungen/rechnungen.component.ts`

##### 7a: Alle Einheiten laden (nicht nur CONSUMER)

```typescript
// Vorher: this.consumers = data.filter(...)
// Neu:
this.einheiten = data
  .sort((a, b) => {
    // Erst nach Typ (CONSUMER vor PRODUCER), dann nach Name
    if (a.typ !== b.typ) return a.typ === EinheitTyp.CONSUMER ? -1 : 1;
    return (a.name || '').localeCompare(b.name || '');
  });
```

- Property `consumers` → umbenennen in `einheiten` (inkl. Template-Referenzen)
- `allSelected()` / `someSelected()` / `onSelectAllToggle()` auf `einheiten` anpassen

##### 7b: Template anpassen

- Sektion-Label von `KONSUMENTEN_WAEHLEN` → `EINHEITEN_WAEHLEN`
- Jede Zeile zeigt Typ-Label in eckigen Klammern: `[Konsument]` / `[Produzent]`
- Verwendung der vorhandenen Translation-Keys `KONSUMENT` / `PRODUZENT`

```html
<!-- Template-Ausschnitt -->
<label>
  <input type="checkbox" [checked]="selectedEinheitIds.has(e.id!)"
         (change)="onEinheitToggle(e.id!)">
  {{ e.name }} [{{ (e.typ === 'CONSUMER' ? 'KONSUMENT' : 'PRODUZENT') | translate }}]
</label>
```

---

#### Phase 8: DB Migration – Übersetzungen

**Datei:** `backend-service/src/main/resources/db/migration/V49__Add_Grundgebuehr_Translations.sql`

```sql
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
-- Neuer Tariftyp
('TARIFTYP_GRUNDGEBUEHR',      'Grundgebühr (CHF/Monat/Zähler)',        'Basic Fee (CHF/month/meter)'),
('TARIFTYP_HINT_GRUNDGEBUEHR', 'Monatlicher Festpreis pro Stromzähler', 'Monthly fixed price per electricity meter'),

-- PDF-Einheit für Grundgebühr
('MONATE',                     'Monate',                                 'Months'),

-- Rechnungsformular (Einheiten statt nur Konsumenten)
('EINHEITEN_WAEHLEN',          'Einheiten auswählen',                   'Select Units'),
('KEINE_EINHEITEN_VORHANDEN',  'Keine Einheiten vorhanden',             'No units available')

ON CONFLICT (key) DO NOTHING;
```

---

### Validierungen

#### Frontend-Validierungen
1. **Tarifformular – unveränderter Bestand:** Preis > 0, alle Pflichtfelder, Datum von ≤ Datum bis
2. **Rechnungsformular:** mind. eine Einheit ausgewählt (bestehend; Logik bleibt unverändert)

#### Backend-Validierungen
1. **TarifService.saveTarif:** Keine überlappenden Tarife desselben Typs (bestehend; gilt auch für GRUNDGEBUEHR automatisch)
2. **RechnungService:** ZEV/VNB-Validierung (`validateTarifAbdeckung`) nur wenn CONSUMER-Einheiten im Request – kein Fehler bei reiner PRODUCER-Selektion
3. **GRUNDGEBUEHR ist optional:** Kein Fehler, wenn kein Tarif vorhanden; Grundgebühr-Zeilen werden einfach nicht hinzugefügt
4. **Volle Monate:** Rechnungszeitraum < 1 Monat → 0 Monate → keine GRUNDGEBUEHR-Zeile

---

### Offene Punkte / Annahmen

1. **Annahme:** `PREIS_HINT` im Tarifformular ("CHF pro kWh") passt nicht für GRUNDGEBUEHR. Da der Hint statisch ist, bleibt er vorerst unverändert – der User sieht den neuen Tariftyp-Label und den Dropdown-Eintrag als ausreichende Kontextualisierung. Falls gewünscht, kann ein typ-spezifischer Hint in einer Folge-Phase ergänzt werden.
2. **Annahme:** Das `.jasper`-File wird via Maven-Build neu kompiliert. Die `jrxml`-Datei ist Source of Truth; das `.jasper`-File wird **nicht** manuell gepflegt.
3. **Annahme:** Produzenten-Rechnungen ohne Grundgebühr (leere tarifZeilen) werden **nicht** generiert (defensive Filterung in `berechneRechnungen`).
4. **Annahme:** Da GRUNDGEBUEHR in der Quartals-/Jahresvalidierung optional ist, wird `validateTarifAbdeckung` **nicht** für GRUNDGEBUEHR aufgerufen – konsistent mit Spec-Entscheid.
5. **Annahme:** Die `mengeneinheit` für kWh-Zeilen wird direkt im bestehenden `berechneTarifZeilen`-Aufruf als Literal `"kWh"` gesetzt (kein separater Translation-Key nötig, da das PDF-Layout bereits `CHF / kWh` als bekannte Einheit zeigt).

---

### Erweiterung: Konfigurierbare Produzenten-Verrechnung (FR-7)

Ein `GRUNDGEBUEHR`-Tarif erhält das Flag `produzentVerrechnen`. Konsumenten zahlen die Grundgebühr immer; Produzenten nur bei `produzentVerrechnen = true`. Standardwert `false` (auch für bestehende Tarife).

#### Betroffene Komponenten

| Typ | Datei | Änderungsart |
|-----|-------|--------------|
| Backend Entity | `backend-service/src/main/java/ch/nacht/entity/Tarif.java` | Änderung |
| Backend Service | `backend-service/src/main/java/ch/nacht/service/RechnungService.java` | Änderung |
| DB Migration | `backend-service/src/main/resources/db/migration/V62__Add_Produzent_Verrechnen_To_Tarif.sql` | Neu |
| DB Migration | `backend-service/src/main/resources/db/migration/V63__Add_Produzent_Verrechnen_Translations.sql` | Neu |
| Frontend Model | `frontend-service/src/app/models/tarif.model.ts` | Änderung |
| Frontend Component | `frontend-service/src/app/components/tarif-form/tarif-form.component.ts` | Änderung |
| Frontend Template | `frontend-service/src/app/components/tarif-form/tarif-form.component.html` | Änderung |
| Frontend Template | `frontend-service/src/app/components/tarif-list/tarif-list.component.html` | Änderung |
| DB Migration | `backend-service/src/main/resources/db/migration/V64__Add_Tarif_Produzent_Spalte_Translations.sql` | Neu |

#### Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | E1. Backend – Entity | Feld `produzentVerrechnen` (Spalte `produzent_verrechnen`, default `false`) in `Tarif.java` |
| [x] | E2. Backend – Migration V62 | Spalte `produzent_verrechnen BOOLEAN NOT NULL DEFAULT FALSE` zu `zev.tarif` |
| [x] | E3. Backend – RechnungService | `berechneProduzentenRechnung` filtert GRUNDGEBUEHR-Tarife auf `produzentVerrechnen = true`; Konsumenten-Pfad unverändert |
| [x] | E4. Frontend – Model | `produzentVerrechnen?: boolean` zum `Tarif`-Interface |
| [x] | E5. Frontend – TarifForm | Default `produzentVerrechnen: false`; Checkbox nur bei Tariftyp `GRUNDGEBUEHR` sichtbar |
| [x] | E6. Übersetzungen V63 | Keys `TARIF_PRODUZENT_VERRECHNEN`, `TARIF_PRODUZENT_VERRECHNEN_HINT` |
| [x] | E7. Frontend – TarifList | Spalte „Produzenten" in der Übersichtstabelle: deaktivierte Checkbox (`[checked]`/`disabled`) für GRUNDGEBUEHR, sonst „–" |
| [x] | E8. Übersetzungen V64 | Keys `TARIF_PRODUZENT_VERRECHNEN_SPALTE`, `JA`, `NEIN` |
| [x] | E9. Tests | RechnungServiceTest (Produzent flagged/not-flagged, Konsument ignoriert Flag); tarif-form spec (Default, Checkbox-Sichtbarkeit) |

#### Detailbeschreibung

**E3 – RechnungService:** Nur der Produzenten-Pfad wird angepasst. In `berechneProduzentenRechnung`:
```java
List<Tarif> tarife = tarifService.getTarifeForZeitraum(TarifTyp.GRUNDGEBUEHR, von, bis).stream()
        .filter(Tarif::isProduzentVerrechnen)
        .toList();
```
Die bestehende defensive Filterung (`if (!rechnung.getTarifZeilen().isEmpty())` in `berechneRechnungen`) sorgt dafür, dass Produzenten ohne freigegebene Tarife keine Rechnung erhalten.

**E5 – TarifForm:** Checkbox via `@if (formData.tariftyp === TarifTyp.GRUNDGEBUEHR)`. Das Enum wird als `readonly TarifTyp = TarifTyp;` im Component exponiert, damit es im Template referenzierbar ist.

## Ergänzung 10.10.2026: Mehrere gleichzeitig gültige Tarife je Typ (Spec FR-2)

Die Regel „3. Keine Überlappung: Pro Tariftyp darf nur ein Tarif für einen Tag gültig sein" gilt nur noch für `LADESTROM`.

| Status | Phase | Beschreibung |
|--------|-------|--------------|
|  [x]   | 1. Tariftypen | `TarifTyp.MEHRFACH_GUELTIG` = ZEV, VNB, GRUNDGEBUEHR, ZUSATZ; neu `UEBERSCHNEIDUNG_JE_BEZEICHNUNG` = ZEV, VNB, GRUNDGEBUEHR. Javadoc der Grundgebühr: die frühere Begründung gegen mehrere Grundgebühren betraf eine Gebühr nur für Ladestationen (bleibt `ZUSATZ`) |
|  [x]   | 2. Repository | `TarifRepository.existsOverlappingTarifMitBezeichnung(typ, bezeichnung, von, bis, excludeId)` mit `LOWER(TRIM(...))`; `findByTariftypAndZeitraumOverlapping` sortiert zusätzlich nach Bezeichnung |
|  [x]   | 3. Service | `TarifService.pruefeUeberschneidung`: je Bezeichnung (ZEV/VNB/Grundgebühr), keine (ZUSATZ), je Typ (LADESTROM) |
|  [x]   | 4. Tests | `TarifServiceTest` (VNB neben VNB, drei Grundgebühren, gleiche Bezeichnung, Ladestrom, Update), `TarifRepositoryIT` (Bezeichnungsvergleich, Sortierung), `RechnungServiceTest` (zwei VNB-Zeilen mit gleicher Menge, drei Grundgebühren), E2E `tarif-verwaltung.spec.ts` (anderer Name zulässig, gleicher Name abgewiesen) |

Keine Migration, keine neuen Übersetzungen: Die Fehlermeldung kommt wie bisher als Text vom Backend.

---

## Ergänzung 10.10.2026: Grundgebühr pro Monat oder pro Tag (Spec FR-3)

| Status | Phase | Beschreibung |
|--------|-------|--------------|
|  [x]   | 1. DB-Migration | `V176__Grundgebuehr_Pro_Tag.sql`: `ck_tarif_mengeneinheit` um `TAG`, bestehende Grundgebühren `MONAT`, Spaltenkommentar, Übersetzungen `TAG`, `TAGE`, `GRUNDGEBUEHR_EINHEIT_HINT` |
|  [x]   | 2. Backend-Entity | `Mengeneinheit.TAG`; `TarifTyp.EIGENE_MENGENEINHEIT` = ZUSATZ, GRUNDGEBUEHR; neu `TarifTyp.GRUNDGEBUEHR_EINHEITEN` = MONAT, TAG |
|  [x]   | 3. Backend-Service | `TarifService.pruefeMengeneinheit`: Grundgebühr ohne Angabe → MONAT, nur MONAT/TAG zulässig. `RechnungService.berechneGrundgebuehrZeilen`: TAG = Tage im Teilzeitraum (inkl.), MONAT = volle Monate. `TarifpositionService.pruefeTariftypZuEinheit` prüft ausdrücklich auf ZUSATZ statt auf `EIGENE_MENGENEINHEIT` (sonst ginge die Grundgebühr dort als Zusatz-Tarif durch). `NkRechnungService`: Zuordnung `TAG` → `TAGE` vervollständigt |
|  [x]   | 4. Frontend | `tarif.model.ts`: `Mengeneinheit.TAG`, `waehlbareMengeneinheiten(typ)`, Grundgebühr in `TARIFTYPEN_MIT_MENGENEINHEIT`; `tarif-form`: Optionen je Typ, Vorbelegung Monat, eigener Hinweis |
|  [x]   | 5. Tests | `TarifServiceTest` (ohne Angabe → Monat, Tag, Stück abgewiesen), `RechnungServiceTest` (92 Tage, 46 Tage ab 16.08., Monat ab 16.08. = 1), `tarif-form.component.spec.ts`, `tarif-list.component.spec.ts` |

---

## Ergänzung 11.10.2026: Reihenfolge der Tarife und Panels (Spec FR-4)

| Status | Phase | Beschreibung |
|--------|-------|--------------|
|  [x]   | 1. DB-Migration | `V178__Tarif_Reihenfolge.sql`: Spalte `reihenfolge` mit Startwert = bisherige Rechnungsreihenfolge, NOT NULL, Kommentar, Übersetzungen |
|  [x]   | 2. Backend-Entity/Repository | `Tarif.reihenfolge`; `TarifRepository`: `findAllByOrderByReihenfolgeAscIdAsc`, `findMaxReihenfolgeByTariftyp`, `findMaxReihenfolge`, `verschiebeReihenfolgeAb` und `verschiebeReihenfolgeBereich` (Bulk-Updates mit ausdrücklicher `org_id`, weil der Hibernate-Filter dort nicht greift) |
|  [x]   | 3. Backend-Service/Controller | `TarifService.saveTarif`: neuer Tarif hinter den letzten seines Typs, Update behält den Platz; `verschiebe(id, vor, nach)`; `PUT /api/tarife/{id}/verschieben?vor=|nach=` (nur die eine Verschiebung — eine erste Fassung schickte die ganze Panel-Reihenfolge, gleichzeitige Sitzungen überschrieben sich damit) |
|  [x]   | 4. Rechnung | `TarifZeileDTO.reihenfolge` (`@JsonIgnore`); `RechnungService.sortiereZeilen` vor jeder Rückgabe |
|  [x]   | 5. Design System | `components/sortable/sortable.css` |
|  [x]   | 6. Frontend | `tarif-list`: drei Panels (nur aktuelles Jahr offen), je eine `cdkDropList`, Griff-Spalte; Spaltensortierung entfernt; `tarif.service.verschiebeTarif` |
|  [x]   | 7. Tests | `TarifServiceTest`, `RechnungServiceTest`, `TarifControllerTest`, `TarifRepositoryIT`; `tarif-list.component.spec.ts`, `tarif.service.spec.ts`; E2E: `oeffneTarifPanels` in `helpers.ts` (Testtarife liegen im zugeklappten Panel „Zukünftige Tarife"), `waitForTableWithData` mit `.first()`, neuer Panel- und Drag-&-Drop-Test in `tarif-verwaltung.spec.ts` |

---

## Ergänzung 11.10.2026: Jahrespreis (Spec FR-6)

| Status | Phase | Beschreibung |
|--------|-------|--------------|
|  [x]   | 1. DB-Migration | `V180__Tarif_Preis_Pro_Jahr.sql`: Spalte `preis_pro_jahr`, Kommentar, Übersetzungen |
|  [x]   | 2. Backend | `Tarif.preisProJahr`; `TarifService.pruefePreisProJahr`; `RechnungService`: Grundgebühr nach Monaten (/12) und nach Tagen (`jahresanteilNachTagen`, je Kalenderjahr), Zusatz-Positionen (/12); `TarifZeileDTO.preisEinheit` und `mengeEinheit` für die Anzeige |
|  [x]   | 3. PDF | `rechnung.jrxml`: Spalten neu verteilt (Bezeichnung 265, Menge 65, Einheit 80 pt), Menge mit Wort, Preis-Einheit „Jahr"; `RechnungZeilenText.ZEICHEN_EINE_ZEILE` 52; Sichtprüfung am gerenderten PDF |
|  [x]   | 4. Frontend | `jahrespreisMoeglich`, `preisEinheitKey(..., proJahr)`; Checkbox in `tarif-form`; Liste „/ Jahr" |
|  [x]   | 5. Tests | `RechnungServiceTest` (Quartale = Jahrespreis nach Monaten/Tagen, Schaltjahr, Jahreswechsel, ab Mitte Quartal, Zusatz), `TarifServiceTest` (zulässig/abgewiesen), `JasperTemplateCompileTest` (Füllen mit „3 Monate"/„CHF / Jahr"), `tarif-form`/`tarif-list`-Specs |
