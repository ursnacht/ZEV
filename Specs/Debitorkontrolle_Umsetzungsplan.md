# Umsetzungsplan: Debitorkontrolle

## Zusammenfassung

Implementierung einer persistenten Debitorenkontrolle: Beim Generieren von Rechnungen werden automatisch Debitor-Einträge für Rechnungen mit Mieter gespeichert (Upsert). Eine neue Seite `/debitoren` erlaubt die manuelle Pflege der Einträge (CRUD) mit QuarterSelector-Filter und ermöglicht insbesondere die Erfassung des Zahldatums zur Nachverfolgung offener vs. bezahlter Rechnungen.

---

## Betroffene Komponenten

| Typ | Datei | Änderungsart |
|-----|-------|--------------|
| DB Migration | `backend-service/src/main/resources/db/migration/V55__Create_Debitor_Table.sql` | Neu |
| DB Migration | `backend-service/src/main/resources/db/migration/V56__Add_Debitor_Translations.sql` | Neu |
| Backend Entity | `backend-service/src/main/java/ch/nacht/entity/Debitor.java` | Neu |
| Backend DTO | `backend-service/src/main/java/ch/nacht/dto/DebitorDTO.java` | Neu |
| Backend Repository | `backend-service/src/main/java/ch/nacht/repository/DebitorRepository.java` | Neu |
| Backend Service | `backend-service/src/main/java/ch/nacht/service/DebitorService.java` | Neu |
| Backend Controller | `backend-service/src/main/java/ch/nacht/controller/DebitorController.java` | Neu |
| Backend Controller | `backend-service/src/main/java/ch/nacht/controller/RechnungController.java` | Änderung |
| Frontend Model | `frontend-service/src/app/models/debitor.model.ts` | Neu |
| Frontend Service | `frontend-service/src/app/services/debitor.service.ts` | Neu |
| Frontend Component | `frontend-service/src/app/components/debitorkontrolle-list/debitorkontrolle-list.component.ts` | Neu |
| Frontend Component | `frontend-service/src/app/components/debitorkontrolle-list/debitorkontrolle-list.component.html` | Neu |
| Frontend Component | `frontend-service/src/app/components/debitorkontrolle-list/debitorkontrolle-list.component.css` | Neu |
| Frontend Component | `frontend-service/src/app/components/debitorkontrolle-form/debitorkontrolle-form.component.ts` | Neu |
| Frontend Component | `frontend-service/src/app/components/debitorkontrolle-form/debitorkontrolle-form.component.html` | Neu |
| Frontend Component | `frontend-service/src/app/components/debitorkontrolle-form/debitorkontrolle-form.component.css` | Neu |
| Frontend Routing | `frontend-service/src/app/app.routes.ts` | Änderung |
| Frontend Navigation | `frontend-service/src/app/components/navigation/navigation.component.html` | Änderung |

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [ ] | 1. DB-Migration: Debitor-Tabelle | V55: Neue Tabelle `debitor` mit Unique-Constraint und CASCADE |
| [ ] | 2. Backend-Entity | `Debitor.java` mit `@Filter` und Validierungen |
| [ ] | 3. Backend-DTO | `DebitorDTO.java` mit `mieterName` und `einheitName` (JOIN-Felder) |
| [ ] | 4. Backend-Repository | `DebitorRepository.java` mit JOIN-Query und Upsert |
| [ ] | 5. Backend-Service | `DebitorService.java` mit CRUD und Validierungslogik |
| [ ] | 6. Backend-Controller | `DebitorController.java` mit GET/POST/PUT/DELETE |
| [ ] | 7. RechnungController anpassen | `DebitorService` injizieren, Upsert nach PDF-Generierung aufrufen |
| [ ] | 8. Frontend-Model | `debitor.model.ts` |
| [ ] | 9. Frontend-Service | `debitor.service.ts` mit API-Calls |
| [ ] | 10. Frontend-Komponenten | List- und Form-Komponente |
| [ ] | 11. Routing | Route `/debitoren` in `app.routes.ts` |
| [ ] | 12. Navigation | Menüeintrag nach "Rechnungen" in `navigation.component.html` |
| [ ] | 13. Übersetzungen | V56: Translation-Keys für alle UI-Texte |

---

## Detailbeschreibung der Phasen

### Phase 1: DB-Migration Debitor-Tabelle

**Datei:** `backend-service/src/main/resources/db/migration/V55__Create_Debitor_Table.sql`

```sql
CREATE SEQUENCE IF NOT EXISTS zev.debitor_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE zev.debitor (
    id          BIGINT  PRIMARY KEY DEFAULT nextval('zev.debitor_seq'),
    mieter_id   BIGINT  NOT NULL,
    betrag      NUMERIC(10, 2) NOT NULL,
    datum_von   DATE    NOT NULL,
    datum_bis   DATE    NOT NULL,
    zahldatum   DATE,
    org_id      BIGINT  NOT NULL,
    CONSTRAINT fk_debitor_mieter FOREIGN KEY (mieter_id) REFERENCES zev.mieter(id) ON DELETE CASCADE,
    CONSTRAINT fk_debitor_org    FOREIGN KEY (org_id)    REFERENCES zev.organisation(id),
    CONSTRAINT debitor_betrag_check CHECK (betrag > 0),
    CONSTRAINT debitor_datum_check  CHECK (datum_von <= datum_bis),
    CONSTRAINT uq_debitor_mieter_von_org UNIQUE (mieter_id, datum_von, org_id)
);

CREATE INDEX idx_debitor_org_datum ON zev.debitor (org_id, datum_von);
CREATE INDEX idx_debitor_mieter    ON zev.debitor (mieter_id);
```

### Phase 2: Backend-Entity

**Vorlage:** `backend-service/src/main/java/ch/nacht/entity/Tarif.java`

**Datei:** `backend-service/src/main/java/ch/nacht/entity/Debitor.java`

```java
@Entity
@Table(name = "debitor", schema = "zev")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class Debitor {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "debitor_seq")
    @SequenceGenerator(name = "debitor_seq", sequenceName = "zev.debitor_seq", allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @NotNull(message = "Mieter is required")
    @Column(name = "mieter_id", nullable = false)
    private Long mieterId;

    @NotNull(message = "Betrag is required")
    @DecimalMin(value = "0.01", message = "Betrag must be greater than 0")
    @Column(name = "betrag", nullable = false, precision = 10, scale = 2)
    private BigDecimal betrag;

    @NotNull(message = "Datum von is required")
    @Column(name = "datum_von", nullable = false)
    private LocalDate datumVon;

    @NotNull(message = "Datum bis is required")
    @Column(name = "datum_bis", nullable = false)
    private LocalDate datumBis;

    @Column(name = "zahldatum")
    private LocalDate zahldatum;

    // Getters, Setters
}
```

### Phase 3: Backend-DTO

**Datei:** `backend-service/src/main/java/ch/nacht/dto/DebitorDTO.java`

```java
public class DebitorDTO {
    private Long id;
    private Long mieterId;
    private String mieterName;    // per JOIN auf mieter
    private String einheitName;   // per JOIN auf einheit
    private BigDecimal betrag;
    private LocalDate datumVon;
    private LocalDate datumBis;
    private LocalDate zahldatum;
    // Getters, Setters
}
```

### Phase 4: Backend-Repository

**Vorlage:** `backend-service/src/main/java/ch/nacht/repository/TarifRepository.java`

**Datei:** `backend-service/src/main/java/ch/nacht/repository/DebitorRepository.java`

```java
public interface DebitorRepository extends JpaRepository<Debitor, Long> {

    // Liste gefiltert nach Quartal (Hibernate-Filter für org_id aktiv)
    @Query("SELECT d FROM Debitor d WHERE d.datumVon >= :von AND d.datumVon <= :bis ORDER BY d.datumVon")
    List<Debitor> findByDatumVonBetween(
        @Param("von") LocalDate von,
        @Param("bis") LocalDate bis
    );

    // Upsert: Eintrag anlegen oder Betrag/datumBis aktualisieren (nur wenn zahldatum noch leer)
    @Modifying
    @Query(value = """
        INSERT INTO zev.debitor (mieter_id, betrag, datum_von, datum_bis, zahldatum, org_id)
        VALUES (:mieterId, :betrag, :datumVon, :datumBis, NULL, :orgId)
        ON CONFLICT (mieter_id, datum_von, org_id)
        DO UPDATE SET betrag = EXCLUDED.betrag, datum_bis = EXCLUDED.datum_bis
        WHERE zev.debitor.zahldatum IS NULL
        """, nativeQuery = true)
    void upsert(
        @Param("mieterId") Long mieterId,
        @Param("betrag") BigDecimal betrag,
        @Param("datumVon") LocalDate datumVon,
        @Param("datumBis") LocalDate datumBis,
        @Param("orgId") Long orgId
    );
}
```

### Phase 5: Backend-Service

**Vorlage:** `backend-service/src/main/java/ch/nacht/service/TarifService.java`

**Datei:** `backend-service/src/main/java/ch/nacht/service/DebitorService.java`

```java
@Service
public class DebitorService {

    private final DebitorRepository debitorRepository;
    private final MieterRepository mieterRepository;
    private final EinheitRepository einheitRepository;
    private final OrganizationContextService organizationContextService;
    private final HibernateFilterService hibernateFilterService;

    // Alle Debitoren im Quartal (mit JOIN-Felder mieterName, einheitName)
    public List<DebitorDTO> getDebitoren(LocalDate von, LocalDate bis) {
        hibernateFilterService.enableOrgFilter();
        List<Debitor> debitoren = debitorRepository.findByDatumVonBetween(von, bis);
        return debitoren.stream().map(this::toDTO).toList();
    }

    // Upsert beim automatischen Erstellen (aus RechnungController)
    @Transactional
    public void upsertFromRechnung(Long mieterId, BigDecimal betrag, LocalDate datumVon, LocalDate datumBis) {
        Long orgId = organizationContextService.getCurrentOrgId();
        debitorRepository.upsert(mieterId, betrag, datumVon, datumBis, orgId);
    }

    // Manuell erstellen
    @Transactional
    public DebitorDTO create(DebitorDTO dto) {
        validate(dto);
        Debitor debitor = new Debitor();
        debitor.setOrgId(organizationContextService.getCurrentOrgId());
        debitor.setMieterId(dto.getMieterId());
        debitor.setBetrag(dto.getBetrag());
        debitor.setDatumVon(dto.getDatumVon());
        debitor.setDatumBis(dto.getDatumBis());
        debitor.setZahldatum(dto.getZahldatum());
        return toDTO(debitorRepository.save(debitor));
    }

    // Bearbeiten
    @Transactional
    public DebitorDTO update(Long id, DebitorDTO dto) {
        hibernateFilterService.enableOrgFilter();
        Debitor debitor = debitorRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Debitor not found: " + id));
        validate(dto);
        debitor.setMieterId(dto.getMieterId());
        debitor.setBetrag(dto.getBetrag());
        debitor.setDatumVon(dto.getDatumVon());
        debitor.setDatumBis(dto.getDatumBis());
        debitor.setZahldatum(dto.getZahldatum());
        return toDTO(debitorRepository.save(debitor));
    }

    // Löschen
    @Transactional
    public boolean delete(Long id) {
        hibernateFilterService.enableOrgFilter();
        if (debitorRepository.existsById(id)) {
            debitorRepository.deleteById(id);
            return true;
        }
        return false;
    }

    private void validate(DebitorDTO dto) {
        if (dto.getBetrag() == null || dto.getBetrag().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Betrag muss grösser als 0 sein");
        if (dto.getDatumVon() == null || dto.getDatumBis() == null)
            throw new IllegalArgumentException("Datum von und bis sind Pflicht");
        if (dto.getDatumVon().isAfter(dto.getDatumBis()))
            throw new IllegalArgumentException("Datum von muss vor oder gleich Datum bis liegen");
        if (dto.getZahldatum() != null && dto.getZahldatum().isBefore(dto.getDatumBis()))
            throw new IllegalArgumentException("Zahldatum darf nicht vor Datum bis liegen");
    }

    private DebitorDTO toDTO(Debitor d) {
        DebitorDTO dto = new DebitorDTO();
        dto.setId(d.getId());
        dto.setMieterId(d.getMieterId());
        dto.setBetrag(d.getBetrag());
        dto.setDatumVon(d.getDatumVon());
        dto.setDatumBis(d.getDatumBis());
        dto.setZahldatum(d.getZahldatum());
        // JOIN-Felder: mieterName und einheitName aus Repository laden
        mieterRepository.findById(d.getMieterId()).ifPresent(m -> {
            dto.setMieterName(m.getName());
            einheitRepository.findById(m.getEinheitId()).ifPresent(e -> dto.setEinheitName(e.getName()));
        });
        return dto;
    }
}
```

### Phase 6: Backend-Controller

**Vorlage:** `backend-service/src/main/java/ch/nacht/controller/TarifController.java`

**Datei:** `backend-service/src/main/java/ch/nacht/controller/DebitorController.java`

```java
@RestController
@RequestMapping("/api/debitoren")
@PreAuthorize("hasRole('zev_admin')")
public class DebitorController {

    private final DebitorService debitorService;

    @GetMapping
    public List<DebitorDTO> getDebitoren(
            @RequestParam LocalDate von,
            @RequestParam LocalDate bis) {
        return debitorService.getDebitoren(von, bis);
    }

    @PostMapping
    public ResponseEntity<DebitorDTO> create(@Valid @RequestBody DebitorDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(debitorService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DebitorDTO> update(@PathVariable Long id, @Valid @RequestBody DebitorDTO dto) {
        return ResponseEntity.ok(debitorService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return debitorService.delete(id)
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }
}
```

### Phase 7: RechnungController anpassen

**Datei:** `backend-service/src/main/java/ch/nacht/controller/RechnungController.java`

`DebitorService` als Dependency hinzufügen. Nach der erfolgreichen PDF-Generierung, wenn `rechnung.getMieterId() != null`, Upsert aufrufen:

```java
// Nach: rechnungStorageService.store(key, pdf);
if (rechnung.getMieterId() != null) {
    debitorService.upsertFromRechnung(
        rechnung.getMieterId(),
        BigDecimal.valueOf(rechnung.getEndBetrag()).setScale(2, RoundingMode.HALF_UP),
        rechnung.getVon(),
        rechnung.getBis()
    );
}
```

Die bestehende Response-Struktur bleibt unverändert. Der `generateRechnungen`-Endpunkt wird `@Transactional` (nicht nur `readOnly`).

### Phase 8: Frontend-Model

**Vorlage:** `frontend-service/src/app/models/tarif.model.ts`

**Datei:** `frontend-service/src/app/models/debitor.model.ts`

```typescript
export interface Debitor {
  id?: number;
  mieterId: number;
  mieterName?: string;    // vom Backend per JOIN geliefert
  einheitName?: string;   // vom Backend per JOIN geliefert
  betrag: number;
  datumVon: string;       // ISO: YYYY-MM-DD
  datumBis: string;       // ISO: YYYY-MM-DD
  zahldatum?: string;     // ISO: YYYY-MM-DD, optional
}
```

### Phase 9: Frontend-Service

**Vorlage:** `frontend-service/src/app/services/tarif.service.ts`

**Datei:** `frontend-service/src/app/services/debitor.service.ts`

```typescript
@Injectable({ providedIn: 'root' })
export class DebitorService {
  private apiUrl = '/api/debitoren';

  constructor(private http: HttpClient) {}

  getDebitoren(von: string, bis: string): Observable<Debitor[]> {
    return this.http.get<Debitor[]>(this.apiUrl, { params: { von, bis } });
  }

  createDebitor(debitor: Debitor): Observable<Debitor> {
    return this.http.post<Debitor>(this.apiUrl, debitor);
  }

  updateDebitor(id: number, debitor: Debitor): Observable<Debitor> {
    return this.http.put<Debitor>(`${this.apiUrl}/${id}`, debitor);
  }

  deleteDebitor(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
```

### Phase 10: Frontend-Komponenten

**Vorlage:** `frontend-service/src/app/components/mieter-list/` und `mieter-form/`

**Neue Dateien:**
- `frontend-service/src/app/components/debitorkontrolle-list/debitorkontrolle-list.component.ts/.html/.css`
- `frontend-service/src/app/components/debitorkontrolle-form/debitorkontrolle-form.component.ts/.html/.css`

**List-Komponente:**
- `QuarterSelectorComponent` + manuelle Von/Bis-Felder (analog `RechnungenComponent`)
- Button "Neu erfassen"
- Tabelle mit Spalten: Mieter (Name + Einheitname), Betrag, Datum von, Datum bis, Zahldatum, Status, Aktionen
- Status: `zahldatum ? 'Bezahlt' : 'Offen'` (CSS-Klasse für Einfärbung)
- Kebab-Menü: Bearbeiten, Löschen
- Leerstate-Meldung wenn keine Einträge
- Löschen mit `window.confirm()` / Design-System-Confirm-Dialog

**Form-Komponente:**
- Mieter-Dropdown: lädt alle Mieter via `MieterService.getAllMieter()` + Einheiten via `EinheitService`; Anzeige `${mieter.name} (${einheitName})`
- Bei Mieter-Auswahl: Einheitname automatisch als read-only Feld befüllen
- Felder: Betrag, Datum von, Datum bis, Zahldatum (optional)
- Input/Output: `@Input() debitor`, `@Output() save`, `@Output() cancel`

### Phase 11: Routing

**Datei:** `frontend-service/src/app/app.routes.ts`

Import und Route hinzufügen (nach `/rechnungen`):

```typescript
import { DebitorkontrolleListComponent } from './components/debitorkontrolle-list/debitorkontrolle-list.component';

{ path: 'debitoren', component: DebitorkontrolleListComponent, canActivate: [AuthGuard], data: { roles: ['zev_admin'] } },
```

### Phase 12: Navigation

**Datei:** `frontend-service/src/app/components/navigation/navigation.component.html`

Neues `<li>` direkt nach dem Rechnungen-Eintrag (Zeile 53):

```html
<li>
  <a routerLink="/debitoren" routerLinkActive="zev-navbar__link--active" class="zev-navbar__link"
    (click)="closeMenu()">
    <app-icon name="credit-card"></app-icon>
    {{ 'DEBITORKONTROLLE' | translate }}
  </a>
</li>
```

### Phase 13: Übersetzungen

**Datei:** `backend-service/src/main/resources/db/migration/V56__Add_Debitor_Translations.sql`

```sql
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('DEBITORKONTROLLE',               'Debitorkontrolle',                    'Debtor Control'),
('NEUER_DEBITOR',                  'Neuer Eintrag',                       'New Entry'),
('DEBITOR_BEARBEITEN',             'Eintrag bearbeiten',                  'Edit Entry'),
('DEBITOR_LOESCHEN_BESTAETIGUNG',  'Soll dieser Eintrag gelöscht werden?','Delete this entry?'),
('ZAHLDATUM',                      'Zahldatum',                           'Payment Date'),
('DATUM_VON',                      'Datum von',                           'Date from'),
('DATUM_BIS',                      'Datum bis',                           'Date to'),
('STATUS_OFFEN',                   'Offen',                               'Open'),
('STATUS_BEZAHLT',                 'Bezahlt',                             'Paid'),
('KEINE_DEBITOREN',                'Keine Debitoren für diesen Zeitraum', 'No debtors for this period'),
('BETRAG_UNGUELTIG',               'Betrag muss grösser als 0 sein',      'Amount must be greater than 0'),
('ZAHLDATUM_VOR_DATUM_BIS',        'Zahldatum darf nicht vor Datum bis liegen', 'Payment date cannot be before end date')
ON CONFLICT (key) DO NOTHING;
```

---

## Validierungen

### Frontend-Validierungen
1. **Mieter:** Pflicht (Dropdown-Auswahl)
2. **Betrag:** Pflicht, > 0, numerisch
3. **Datum von:** Pflicht
4. **Datum bis:** Pflicht, ≥ Datum von
5. **Zahldatum:** Optional; wenn gesetzt, ≥ Datum bis (keine Vorauszahlung)

### Backend-Validierungen
1. `betrag > 0` (DB-Constraint + Service)
2. `datum_von <= datum_bis` (DB-Constraint + Service)
3. `zahldatum IS NULL OR zahldatum >= datum_bis` (Service)
4. `mieter_id` NOT NULL (DB-Constraint + `@NotNull`)
5. Upsert überschreibt Betrag/Datum-bis nur wenn `zahldatum` noch NULL

---

## Offene Punkte / Annahmen

1. **Annahme:** `@Transactional` wird auf `RechnungController.generateRechnungen()` gesetzt (war bisher nicht annotiert), damit Debitor-Upsert und PDF-Generierung in einer Transaktion laufen.
2. **Annahme:** Feather-Icon `credit-card` für den Navigations-Menüeintrag.
3. **Annahme:** Mieter-Dropdown zeigt alle Mieter des Mandanten (auch mit Mietende), damit auch historische Einträge manuell erfasst werden können.
4. **Annahme:** Status-Spalte wird nur im Frontend berechnet (kein DB-Feld), da er sich direkt aus `zahldatum IS NULL` ergibt.
5. **Annahme:** Nächste freie Flyway-Migrationsnummer ist V55 (letzte: `V54__Add_TarifTyp_Translations.sql`).
