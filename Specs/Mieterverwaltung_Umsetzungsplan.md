# Umsetzungsplan: Mieterverwaltung

## Zusammenfassung

Implementierung einer eigenständigen Mieterverwaltung mit Mietdauer (Mietbeginn/Mietende). Mieter werden in einer separaten Tabelle gespeichert und einer Einheit zugeordnet. Dies ermöglicht bei Mieterwechseln innerhalb eines Quartals die Erstellung von anteiligen Quartalsrechnungen für jeden Mieter. Das bestehende Feld `mietername` in der Einheit-Tabelle wird entfernt.

---

## Betroffene Komponenten

| Typ | Datei | Änderungsart |
|-----|-------|--------------|
| DB Migration | `backend-service/src/main/resources/db/migration/V36__Create_Mieter_Table.sql` | Neu |
| DB Migration | `backend-service/src/main/resources/db/migration/V37__Remove_Mietername_From_Einheit.sql` | Neu |
| DB Migration | `backend-service/src/main/resources/db/migration/V38__Add_Mieter_Translations.sql` | Neu |
| DB Migration | `backend-service/src/main/resources/db/migration/V39__Mieter_Address_Required.sql` | Neu |
| Backend Entity | `backend-service/src/main/java/ch/nacht/entity/Mieter.java` | Neu |
| Backend Entity | `backend-service/src/main/java/ch/nacht/entity/Einheit.java` | Änderung |
| Backend Repository | `backend-service/src/main/java/ch/nacht/repository/MieterRepository.java` | Neu |
| Backend Service | `backend-service/src/main/java/ch/nacht/service/MieterService.java` | Neu |
| Backend Controller | `backend-service/src/main/java/ch/nacht/controller/MieterController.java` | Neu |
| Frontend Model | `frontend-service/src/app/models/mieter.model.ts` | Neu |
| Frontend Model | `frontend-service/src/app/models/einheit.model.ts` | Änderung |
| Frontend Service | `frontend-service/src/app/services/mieter.service.ts` | Neu |
| Frontend Component | `frontend-service/src/app/components/mieter-list/mieter-list.component.ts` | Neu |
| Frontend Component | `frontend-service/src/app/components/mieter-form/mieter-form.component.ts` | Neu |
| Frontend Routing | `frontend-service/src/app/app.routes.ts` | Änderung |
| Frontend Navigation | `frontend-service/src/app/app.component.html` | Änderung |
| Backend DTO | `backend-service/src/main/java/ch/nacht/dto/RechnungDTO.java` | Änderung |
| Backend Service | `backend-service/src/main/java/ch/nacht/service/RechnungService.java` | Änderung |

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. DB-Migration Mieter-Tabelle | Flyway-Migration V36 für neue Mieter-Tabelle mit Validierungen |
| [x] | 2. DB-Migration Einheit bereinigen | Flyway-Migration V37: mietername-Feld aus Einheit entfernen |
| [x] | 3. Backend-Entity Mieter | Entity-Klasse mit Validierungen erstellen |
| [x] | 4. Backend-Entity Einheit anpassen | mietername-Feld aus Einheit-Entity entfernen |
| [x] | 5. Backend-Repository | MieterRepository mit Overlap-Prüfung erstellen |
| [x] | 6. Backend-Service | MieterService mit Geschäftslogik und Validierungen |
| [x] | 7. Backend-Controller | REST-Endpunkte für CRUD-Operationen |
| [x] | 8. Frontend-Model | TypeScript-Interface für Mieter |
| [x] | 9. Frontend-Model Einheit anpassen | mietername aus Einheit-Interface entfernen |
| [x] | 10. Frontend-Service | Angular-Service für API-Calls |
| [x] | 11. Frontend-Komponenten | MieterListComponent und MieterFormComponent |
| [x] | 12. Routing | Route /mieter in app.routes.ts hinzufügen |
| [x] | 13. Navigation | Menüeintrag "Mieterverwaltung" hinzufügen |
| [x] | 14. Übersetzungen | Flyway-Migration V38 für Translation-Keys |
| [x] | 15. RechnungDTO anpassen | Mieter-Adressfelder hinzufügen (mieterStrasse, mieterPlzOrt) |
| [x] | 16. RechnungService anpassen | Pro Mieter einer Einheit im Quartal eine separate Rechnung erstellen |
| [x] | 17. Adressfelder als Pflicht | Strasse, PLZ und Ort als Pflichtfelder (Backend + Frontend + Migration V39) |

---

## Detailbeschreibung der Phasen

### Phase 1: DB-Migration Mieter-Tabelle

**Datei:** `backend-service/src/main/resources/db/migration/V36__Create_Mieter_Table.sql`

```sql
CREATE SEQUENCE IF NOT EXISTS zev.mieter_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE zev.mieter (
    id BIGINT PRIMARY KEY DEFAULT nextval('zev.mieter_seq'),
    org_id UUID NOT NULL,
    name VARCHAR(150) NOT NULL,
    strasse VARCHAR(150),
    plz VARCHAR(20),
    ort VARCHAR(100),
    mietbeginn DATE NOT NULL,
    mietende DATE,
    einheit_id BIGINT NOT NULL,
    CONSTRAINT fk_mieter_einheit FOREIGN KEY (einheit_id) REFERENCES zev.einheit(id),
    CONSTRAINT mieter_datum_check CHECK (mietende IS NULL OR mietende > mietbeginn)
);

CREATE INDEX idx_mieter_einheit ON zev.mieter (einheit_id);
CREATE INDEX idx_mieter_org ON zev.mieter (org_id);
CREATE INDEX idx_mieter_zeitraum ON zev.mieter (einheit_id, mietbeginn, mietende);
```

### Phase 2: DB-Migration Einheit bereinigen

**Datei:** `backend-service/src/main/resources/db/migration/V37__Remove_Mietername_From_Einheit.sql`

```sql
ALTER TABLE zev.einheit DROP COLUMN IF EXISTS mietername;
```

### Phase 3: Backend-Entity Mieter

**Datei:** `backend-service/src/main/java/ch/nacht/entity/Mieter.java`

```java
@Entity
@Table(name = "mieter", schema = "zev")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class Mieter {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mieter_seq")
    @SequenceGenerator(name = "mieter_seq", sequenceName = "zev.mieter_seq", allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must not exceed 150 characters")
    @Column(name = "name", length = 150, nullable = false)
    private String name;

    @Size(max = 150, message = "Strasse must not exceed 150 characters")
    @Column(name = "strasse", length = 150)
    private String strasse;

    @Size(max = 20, message = "PLZ must not exceed 20 characters")
    @Column(name = "plz", length = 20)
    private String plz;

    @Size(max = 100, message = "Ort must not exceed 100 characters")
    @Column(name = "ort", length = 100)
    private String ort;

    @NotNull(message = "Mietbeginn is required")
    @Column(name = "mietbeginn", nullable = false)
    private LocalDate mietbeginn;

    @Column(name = "mietende")
    private LocalDate mietende;

    @NotNull(message = "Einheit is required")
    @Column(name = "einheit_id", nullable = false)
    private Long einheitId;

    // Getters, Setters, Constructors
}
```

### Phase 4: Backend-Entity Einheit anpassen

**Datei:** `backend-service/src/main/java/ch/nacht/entity/Einheit.java`

Entfernen:
- Feld `mietername`
- Getter `getMietername()`
- Setter `setMietername()`
- mietername aus `toString()`

### Phase 5: Backend-Repository

**Datei:** `backend-service/src/main/java/ch/nacht/repository/MieterRepository.java`

```java
public interface MieterRepository extends JpaRepository<Mieter, Long> {

    List<Mieter> findAllByOrderByEinheitIdAscMietbeginnDesc();

    List<Mieter> findByEinheitIdOrderByMietbeginnDesc(Long einheitId);

    // Prüft ob sich Mietzeiten für eine Einheit überschneiden
    @Query("SELECT COUNT(m) > 0 FROM Mieter m WHERE m.einheitId = :einheitId " +
           "AND m.id != :excludeId " +
           "AND m.mietbeginn < COALESCE(:mietende, DATE '9999-12-31') " +
           "AND COALESCE(m.mietende, DATE '9999-12-31') > :mietbeginn")
    boolean existsOverlappingMieter(
        @Param("einheitId") Long einheitId,
        @Param("mietbeginn") LocalDate mietbeginn,
        @Param("mietende") LocalDate mietende,
        @Param("excludeId") Long excludeId
    );

    // Prüft ob ein anderer Mieter ohne Mietende existiert (für dieselbe Einheit)
    @Query("SELECT COUNT(m) > 0 FROM Mieter m WHERE m.einheitId = :einheitId " +
           "AND m.id != :excludeId " +
           "AND m.mietende IS NULL")
    boolean existsOtherMieterWithoutMietende(
        @Param("einheitId") Long einheitId,
        @Param("excludeId") Long excludeId
    );

    // Mieter für ein Quartal (für Rechnungserstellung)
    @Query("SELECT m FROM Mieter m WHERE m.einheitId = :einheitId " +
           "AND m.mietbeginn <= :quartalEnde " +
           "AND (m.mietende IS NULL OR m.mietende >= :quartalBeginn) " +
           "ORDER BY m.mietbeginn")
    List<Mieter> findByEinheitIdAndQuartal(
        @Param("einheitId") Long einheitId,
        @Param("quartalBeginn") LocalDate quartalBeginn,
        @Param("quartalEnde") LocalDate quartalEnde
    );
}
```

### Phase 6: Backend-Service

**Datei:** `backend-service/src/main/java/ch/nacht/service/MieterService.java`

```java
@Service
public class MieterService {

    private final MieterRepository mieterRepository;
    private final OrganizationContextService organizationContextService;
    private final HibernateFilterService hibernateFilterService;

    public List<Mieter> getAllMieter() {
        hibernateFilterService.enableOrgFilter();
        return mieterRepository.findAllByOrderByEinheitIdAscMietbeginnDesc();
    }

    public Optional<Mieter> getMieterById(Long id) {
        hibernateFilterService.enableOrgFilter();
        return mieterRepository.findById(id);
    }

    public Mieter saveMieter(Mieter mieter) {
        hibernateFilterService.enableOrgFilter();

        // Validierung: Mietende nach Mietbeginn
        if (mieter.getMietende() != null && !mieter.getMietende().isAfter(mieter.getMietbeginn())) {
            throw new IllegalArgumentException("Mietende muss nach Mietbeginn liegen");
        }

        Long excludeId = mieter.getId() != null ? mieter.getId() : -1L;

        // Validierung: Keine überlappenden Mietzeiten
        if (mieterRepository.existsOverlappingMieter(
                mieter.getEinheitId(),
                mieter.getMietbeginn(),
                mieter.getMietende(),
                excludeId)) {
            throw new IllegalArgumentException("Mietzeit überschneidet sich mit bestehendem Mieter");
        }

        // Validierung: Nur der aktuellste Mieter darf kein Mietende haben
        if (mieter.getMietende() == null) {
            if (mieterRepository.existsOtherMieterWithoutMietende(mieter.getEinheitId(), excludeId)) {
                throw new IllegalArgumentException("Es existiert bereits ein aktueller Mieter ohne Mietende");
            }
        }

        if (mieter.getId() == null) {
            mieter.setOrgId(organizationContextService.getCurrentOrgId());
        }

        return mieterRepository.save(mieter);
    }

    public boolean deleteMieter(Long id) {
        hibernateFilterService.enableOrgFilter();
        if (mieterRepository.existsById(id)) {
            mieterRepository.deleteById(id);
            return true;
        }
        return false;
    }

    // Für Rechnungserstellung: Alle Mieter einer Einheit in einem Quartal
    public List<Mieter> getMieterForQuartal(Long einheitId, LocalDate quartalBeginn, LocalDate quartalEnde) {
        hibernateFilterService.enableOrgFilter();
        return mieterRepository.findByEinheitIdAndQuartal(einheitId, quartalBeginn, quartalEnde);
    }
}
```

### Phase 7: Backend-Controller

**Datei:** `backend-service/src/main/java/ch/nacht/controller/MieterController.java`

```java
@RestController
@RequestMapping("/api/mieter")
@PreAuthorize("hasRole('zev_admin')")
public class MieterController {

    private final MieterService mieterService;

    @GetMapping
    public List<Mieter> getAllMieter() { ... }

    @GetMapping("/{id}")
    public ResponseEntity<Mieter> getMieterById(@PathVariable Long id) { ... }

    @PostMapping
    public ResponseEntity<Mieter> createMieter(@Valid @RequestBody Mieter mieter) { ... }

    @PutMapping("/{id}")
    public ResponseEntity<Mieter> updateMieter(@PathVariable Long id, @Valid @RequestBody Mieter mieter) { ... }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMieter(@PathVariable Long id) { ... }
}
```

### Phase 8: Frontend-Model Mieter

**Datei:** `frontend-service/src/app/models/mieter.model.ts`

```typescript
export interface Mieter {
  id?: number;
  name: string;
  strasse?: string;
  plz?: string;
  ort?: string;
  mietbeginn: string;  // ISO date format: YYYY-MM-DD
  mietende?: string;   // ISO date format: YYYY-MM-DD, optional
  einheitId: number;
}
```

### Phase 9: Frontend-Model Einheit anpassen

**Datei:** `frontend-service/src/app/models/einheit.model.ts`

```typescript
export interface Einheit {
  id?: number;
  name: string;
  typ: EinheitTyp;
  // mietername entfernt
  messpunkt?: string;
}
```

### Phase 10: Frontend-Service

**Datei:** `frontend-service/src/app/services/mieter.service.ts`

```typescript
@Injectable({ providedIn: 'root' })
export class MieterService {
  private apiUrl = '/api/mieter';

  constructor(private http: HttpClient) {}

  getAllMieter(): Observable<Mieter[]> { ... }
  getMieterById(id: number): Observable<Mieter> { ... }
  createMieter(mieter: Mieter): Observable<Mieter> { ... }
  updateMieter(id: number, mieter: Mieter): Observable<Mieter> { ... }
  deleteMieter(id: number): Observable<void> { ... }
}
```

### Phase 11: Frontend-Komponenten

**Dateien:**
- `frontend-service/src/app/components/mieter-list/mieter-list.component.ts`
- `frontend-service/src/app/components/mieter-list/mieter-list.component.html`
- `frontend-service/src/app/components/mieter-list/mieter-list.component.css`
- `frontend-service/src/app/components/mieter-form/mieter-form.component.ts`
- `frontend-service/src/app/components/mieter-form/mieter-form.component.html`
- `frontend-service/src/app/components/mieter-form/mieter-form.component.css`

Aufbau analog zur Tarifverwaltung:
- Tabelle mit Spalten: Einheit, Name, Strasse, PLZ/Ort, Mietbeginn, Mietende, Aktionen
- Inline-Formular für Erstellen/Bearbeiten
- Dropdown für Einheit-Auswahl (nur CONSUMER-Einheiten)
- Kebab-Menü mit Bearbeiten, Kopieren, Löschen
- Datums-Validierung im Frontend

### Phase 12: Routing

**Änderung in:** `frontend-service/src/app/app.routes.ts`

```typescript
{ path: 'mieter', component: MieterListComponent, canActivate: [AuthGuard], data: { roles: ['zev_admin'] } },
```

### Phase 13: Navigation

**Änderung in:** `frontend-service/src/app/app.component.html`

Menüeintrag unterhalb "Tarife":

```html
<a routerLink="/mieter" routerLinkActive="zev-navbar__link--active" class="zev-navbar__link">
  {{ 'MIETERVERWALTUNG' | translate }}
</a>
```

### Phase 14: Übersetzungen

**Datei:** `backend-service/src/main/resources/db/migration/V38__Add_Mieter_Translations.sql`

```sql
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('MIETERVERWALTUNG', 'Mieterverwaltung', 'Tenant Management'),
('MIETER', 'Mieter', 'Tenants'),
('NEUER_MIETER', 'Neuer Mieter', 'New Tenant'),
('MIETER_BEARBEITEN', 'Mieter bearbeiten', 'Edit Tenant'),
('MIETER_LOESCHEN_BESTAETIGUNG', 'Möchten Sie diesen Mieter wirklich löschen?', 'Do you really want to delete this tenant?'),
('MIETBEGINN', 'Mietbeginn', 'Lease Start'),
('MIETENDE', 'Mietende', 'Lease End'),
('STRASSE', 'Strasse', 'Street'),
('PLZ', 'PLZ', 'Postal Code'),
('ORT', 'Ort', 'City'),
('MIETER_UEBERSCHNEIDUNG', 'Mietzeit überschneidet sich mit bestehendem Mieter', 'Lease period overlaps with existing tenant'),
('MIETENDE_NACH_MIETBEGINN', 'Mietende muss nach Mietbeginn liegen', 'Lease end must be after lease start'),
('AKTUELLER_MIETER_EXISTIERT', 'Es existiert bereits ein aktueller Mieter ohne Mietende', 'A current tenant without lease end already exists')
ON CONFLICT (key) DO NOTHING;
```

### Phase 15: RechnungDTO anpassen

**Datei:** `backend-service/src/main/java/ch/nacht/dto/RechnungDTO.java`

Neue Felder für vollständige Mieter-Adresse hinzufügen:

```java
// Mieter-Daten (statt nur mietername)
private Long mieterId;
private String mieterName;      // ersetzt mietername
private String mieterStrasse;
private String mieterPlzOrt;

// Getters und Setters hinzufügen
```

Das bisherige Feld `mietername` wird durch `mieterName` ersetzt (konsistente Benennung).

### Phase 16: RechnungService anpassen

**Datei:** `backend-service/src/main/java/ch/nacht/service/RechnungService.java`

Die Methode `berechneRechnungen` muss angepasst werden, um bei Mieterwechseln mehrere Rechnungen pro Einheit zu erstellen:

```java
@Transactional(readOnly = true)
public List<RechnungDTO> berechneRechnungen(List<Long> einheitIds, LocalDate von, LocalDate bis) {
    hibernateFilterService.enableOrgFilter();

    // Validate tariff coverage
    tarifService.validateTarifAbdeckung(von, bis);

    List<RechnungDTO> rechnungen = new ArrayList<>();

    for (Long einheitId : einheitIds) {
        einheitRepository.findById(einheitId).ifPresent(einheit -> {
            if (einheit.getTyp() == EinheitTyp.CONSUMER) {
                // Alle Mieter dieser Einheit im Quartal holen
                List<Mieter> mieter = mieterService.getMieterForQuartal(einheitId, von, bis);

                if (mieter.isEmpty()) {
                    // Kein Mieter: Rechnung ohne Mieter-Daten erstellen
                    RechnungDTO rechnung = berechneRechnung(einheit, null, von, bis);
                    rechnungen.add(rechnung);
                } else {
                    // Für jeden Mieter eine separate Rechnung erstellen
                    for (Mieter m : mieter) {
                        // Effektiver Zeitraum = Schnittmenge von Quartal und Mietdauer
                        LocalDate effektivVon = m.getMietbeginn().isBefore(von) ? von : m.getMietbeginn();
                        LocalDate effektivBis = (m.getMietende() == null || m.getMietende().isAfter(bis))
                            ? bis : m.getMietende();

                        RechnungDTO rechnung = berechneRechnung(einheit, m, effektivVon, effektivBis);
                        rechnungen.add(rechnung);
                    }
                }
            }
        });
    }

    return rechnungen;
}

/**
 * Berechnet eine Rechnung für eine Einheit und einen optionalen Mieter.
 */
public RechnungDTO berechneRechnung(Einheit einheit, Mieter mieter, LocalDate von, LocalDate bis) {
    RechnungDTO rechnung = new RechnungDTO();

    // Unit information
    rechnung.setEinheitId(einheit.getId());
    rechnung.setEinheitName(einheit.getName());
    rechnung.setMesspunkt(einheit.getMesspunkt());

    // Mieter information (falls vorhanden)
    if (mieter != null) {
        rechnung.setMieterId(mieter.getId());
        rechnung.setMieterName(mieter.getName());
        rechnung.setMieterStrasse(mieter.getStrasse());
        rechnung.setMieterPlzOrt(
            (mieter.getPlz() != null ? mieter.getPlz() : "") + " " +
            (mieter.getOrt() != null ? mieter.getOrt() : "")
        );
    }

    // Time period (effektiver Zeitraum für diesen Mieter)
    rechnung.setVon(von);
    rechnung.setBis(bis);

    // ... Rest der Berechnung bleibt gleich
}
```

**Wichtige Änderungen:**
1. `MieterService` als Dependency hinzufügen
2. Für jeden Mieter wird der effektive Rechnungszeitraum berechnet (Schnittmenge von Quartal und Mietdauer)
3. Die Messwerte werden nur für den effektiven Zeitraum des Mieters summiert
4. Bei Leerstand (kein Mieter) wird eine Rechnung ohne Mieter-Daten erstellt

---

## Validierungen

### Frontend-Validierungen
1. **Pflichtfelder:** Name, Mietbeginn, Einheit müssen ausgefüllt sein
2. **Datumsprüfung:** Falls Mietende erfasst, muss es nach Mietbeginn liegen
3. **Datumsformat:** Schweizer Format (dd.MM.yyyy) in der Anzeige, ISO-Format für API

### Backend-Validierungen
1. **Pflichtfelder:** Name, Mietbeginn, Einheit-ID müssen vorhanden sein
2. **Datumsprüfung:** `mietende IS NULL OR mietende > mietbeginn`
3. **Keine Überlappung:** Mietzeiten derselben Einheit dürfen sich nicht überschneiden
4. **Nur ein aktueller Mieter:** Pro Einheit darf nur der Mieter mit dem jüngsten Mietbeginn kein Mietende haben
5. **Feldlängen:** Name max. 150, Strasse max. 150, PLZ max. 20, Ort max. 100 Zeichen

---

## Offene Punkte / Annahmen

1. **Annahme:** Bei der Rechnungserstellung wird die Mietdauer mit dem Quartal geschnitten - ein Mieter erhält nur für die Tage eine Rechnung, in denen er tatsächlich Mieter war
2. **Annahme:** Leerstände sind erlaubt (keine Pflicht zur lückenlosen Belegung) - es wird dann eine Rechnung ohne Mieter-Daten erstellt
3. **Annahme:** Nur CONSUMER-Einheiten können Mieter haben (Dropdown zeigt nur CONSUMER)
4. **Annahme:** Die Einheit-Dropdown zeigt Name + ID zur eindeutigen Identifikation
5. **Annahme:** Das Feld mietername wird sofort entfernt, ohne Datenmigration
6. **Annahme:** Bei Mieterwechsel innerhalb eines Quartals werden mehrere Rechnungen für dieselbe Einheit erstellt (eine pro Mieter)
