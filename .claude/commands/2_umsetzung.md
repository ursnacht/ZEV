# Umsetzung

Setze einen Umsetzungsplan schrittweise um.

## Input
* Umsetzungsplan: $ARGUMENTS (z.B. `Specs/Tarifverwaltung_Umsetzungsplan.md` oder kurz `Tarifverwaltung`)

## Vorgehen
1. **Lies den Umsetzungsplan** - Verstehe alle Phasen und deren Status
2. **Identifiziere nächste Phase** - Finde die erste Phase mit `[ ]` (nicht erledigt)
3. **Implementiere die Phase** - Setze die beschriebenen Änderungen um (Patterns unten beachten!)
4. **Kompiliere und prüfe** - Stelle sicher, dass der Code kompiliert (siehe Validierung)
5. **Aktualisiere den Status** - Markiere die Phase mit `[x]` als erledigt
6. **Wiederhole** - Fahre mit der nächsten Phase fort

## Konventionen
* **Design System (WICHTIG):**
    * **Immer zuerst prüfen:** Vor dem Erstellen neuer CSS-Styles im Design System nachschauen (`design-system/src/components/`)
    * **Verfügbare Komponenten:** Siehe `Specs/generell.md` (Abschnitt Design System) für die vollständige Liste
    * **Neue Styles ins Design System:** Wiederverwendbare Styles gehören in `design-system/src/components/`
    * **Komponenten-CSS minimal halten:** Nur komponentenspezifische Styles, keine Duplikate
    * **Design System bauen:** Nach Änderungen `cd design-system && npm run build`
    * **Design System Showcase:** bei neu erstellten Komponenten oder Styles ergänzen
* **Datenbank:** Flyway-Migrationen in `backend-service/src/main/resources/db/migration/`
  * **Naming:** Migrations `V[nummer]__[beschreibung].sql`
* **Code-Vorlagen:** Verwende die Vorlagen aus CLAUDE.md (Abschnitt "Code-Vorlagen für deterministische Generierung")

## Validierung nach jeder Phase
* **Backend-Änderungen:** `cd backend-service && mvn compile -q` (muss fehlerfrei kompilieren)
* **Frontend-Änderungen:** `cd frontend-service && npx ng build --configuration=development 2>&1 | head -20` (muss fehlerfrei kompilieren)
* **Design-System-Änderungen:** `cd design-system && npm run build`

## Wichtige Regeln
* **Keine Tests erstellen** - Tests werden separat mit anderen Commands erstellt
* **Inkrementell arbeiten** - Eine Phase nach der anderen abschliessen
* **Status aktuell halten** - Umsetzungsplan nach jeder Phase aktualisieren
* **Kompilierbarkeit sicherstellen** - Nach jeder Phase validieren (siehe oben)
* **Deterministische Patterns** - Code MUSS exakt den unten definierten Patterns folgen

---

## Deterministische Code-Patterns

### Import-Reihenfolge (Backend)

Immer in dieser Reihenfolge, getrennt durch Leerzeilen:

```java
// 1. Package-Deklaration
package ch.nacht.[layer];

// 2. Projekt-Imports (alphabetisch)
import ch.nacht.entity.*;
import ch.nacht.repository.*;
import ch.nacht.service.*;

// 3. Framework-Imports (alphabetisch)
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.*;
import org.slf4j.*;
import org.springframework.*;

// 4. Java-Standard-Imports
import java.math.*;
import java.time.*;
import java.util.*;
```

---

### Entity-Struktur

```java
/**
 * Entity representing a [Beschreibung].
 */
@Entity
@Table(name = "[tabellenname]", schema = "zev")
@Filter(name = "orgFilter", condition = "org_id = :orgId")
public class [Entity] {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "[entity]_seq")
    @SequenceGenerator(name = "[entity]_seq", sequenceName = "zev.[entity]_seq", allocationSize = 1)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    // Business-Felder mit Validierungs-Annotationen (@NotBlank, @NotNull, @Size, @Positive)
    // Reihenfolge: id, orgId, dann Business-Felder

    // 1. No-args Constructor
    public [Entity]() {}

    // 2. Constructor mit Pflichtfeldern (ohne id und orgId)
    public [Entity](...) { }

    // 3. Getters/Setters in Feld-Reihenfolge

    // 4. toString()
    @Override
    public String toString() {
        return "[Entity]{id=" + id + ", ...}";
    }
}
```

---

### Repository-Struktur

```java
/**
 * Repository for [Entity] entities.
 */
@Repository
public interface [Entity]Repository extends JpaRepository<[Entity], Long> {

    // 1. Spring Data Finder-Methoden
    List<[Entity]> findAllByOrderBy[Field]Asc();

    // 2. Custom @Query-Methoden mit Javadoc
    /**
     * [Beschreibung]
     */
    @Query("SELECT t FROM [Entity] t WHERE ...")
    [ReturnType] [methodName](@Param("param") Type param);
}
```

---

### Service-Struktur

```java
/**
 * Service for managing [entities].
 */
@Service
public class [Entity]Service {

    private static final Logger log = LoggerFactory.getLogger([Entity]Service.class);

    // Dependencies: immer diese drei + weitere
    private final [Entity]Repository [entity]Repository;
    private final OrganizationContextService organizationContextService;
    private final HibernateFilterService hibernateFilterService;

    // Constructor-Injection (kein @Autowired)
    public [Entity]Service([Entity]Repository [entity]Repository,
                           OrganizationContextService organizationContextService,
                           HibernateFilterService hibernateFilterService) {
        this.[entity]Repository = [entity]Repository;
        this.organizationContextService = organizationContextService;
        this.hibernateFilterService = hibernateFilterService;
    }

    // Methoden-Reihenfolge: getAll → getById → save → delete → Business-Logic

    @Transactional(readOnly = true)
    public List<[Entity]> getAll[Entities]() {
        hibernateFilterService.enableOrgFilter();
        return [entity]Repository.findAllByOrderBy...();
    }

    @Transactional(readOnly = true)
    public Optional<[Entity]> get[Entity]ById(Long id) {
        hibernateFilterService.enableOrgFilter();
        return [entity]Repository.findById(id);
    }

    @Transactional
    public [Entity] save[Entity]([Entity] [entity]) {
        hibernateFilterService.enableOrgFilter();
        log.info("Saving [entity]: {}", [entity]);

        // Validierungslogik (wirft IllegalArgumentException)
        Long excludeId = [entity].getId() != null ? [entity].getId() : -1L;
        // ...

        // OrgId setzen bei neuen Entities
        if ([entity].getId() == null) {
            [entity].setOrgId(organizationContextService.getCurrentOrgId());
        }

        [Entity] saved = [entity]Repository.save([entity]);
        log.info("[Entity] saved with ID: {}", saved.getId());
        return saved;
    }

    @Transactional
    public boolean delete[Entity](Long id) {
        hibernateFilterService.enableOrgFilter();
        if ([entity]Repository.existsById(id)) {
            [entity]Repository.deleteById(id);
            log.info("Deleted [entity] with ID: {}", id);
            return true;
        }
        log.warn("[Entity] not found for deletion: {}", id);
        return false;
    }
}
```

**Verbindliche Regeln:**
* `hibernateFilterService.enableOrgFilter()` am Anfang JEDER Methode
* `@Transactional(readOnly = true)` für Lese-Methoden
* `@Transactional` für Schreib-Methoden
* Validierungsfehler → `throw new IllegalArgumentException("Meldung")`
* Neue Entities: `orgId` setzen via `organizationContextService.getCurrentOrgId()`
* Delete: gibt `boolean` zurück (true = gelöscht, false = nicht gefunden)
* Logging: `log.info()` für Operationen, `log.warn()` für nicht-gefunden

---

### Controller-Struktur

```java
/**
 * REST controller for managing [entities].
 */
@RestController
@RequestMapping("/api/[entities]")
@PreAuthorize("hasRole('[rolle]')")
public class [Entity]Controller {

    private static final Logger log = LoggerFactory.getLogger([Entity]Controller.class);
    private final [Entity]Service [entity]Service;

    public [Entity]Controller([Entity]Service [entity]Service) {
        this.[entity]Service = [entity]Service;
        log.info("[Entity]Controller initialized");
    }

    // Methoden-Reihenfolge: GET all → GET by id → POST → PUT → DELETE

    @GetMapping
    public List<[Entity]> getAll[Entities]() {
        log.info("Fetching all [entities]");
        List<[Entity]> [entities] = [entity]Service.getAll[Entities]();
        log.info("Retrieved {} [entities]", [entities].size());
        return [entities];
    }

    @GetMapping("/{id}")
    public ResponseEntity<[Entity]> get[Entity]ById(@PathVariable Long id) {
        log.info("Fetching [entity] with id: {}", id);
        return [entity]Service.get[Entity]ById(id)
                .map([e] -> {
                    log.info("Found [entity]: {}", [e].get[NameField]());
                    return ResponseEntity.ok([e]);
                })
                .orElseGet(() -> {
                    log.warn("[Entity] not found with id: {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    @PostMapping
    public ResponseEntity<?> create[Entity](@Valid @RequestBody [Entity] [entity]) {
        log.info("Creating new [entity]: {}", [entity].get[NameField]());
        try {
            [Entity] saved = [entity]Service.save[Entity]([entity]);
            log.info("Created [entity] with id: {}", saved.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to create [entity]: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update[Entity](@PathVariable Long id, @Valid @RequestBody [Entity] [entity]) {
        log.info("Updating [entity] with id: {}", id);
        if (![entity]Service.get[Entity]ById(id).isPresent()) {
            log.warn("Cannot update - [entity] not found with id: {}", id);
            return ResponseEntity.notFound().build();
        }
        try {
            [entity].setId(id);
            [Entity] updated = [entity]Service.save[Entity]([entity]);
            log.info("Successfully updated [entity]: {}", updated.get[NameField]());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to update [entity]: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete[Entity](@PathVariable Long id) {
        log.info("Deleting [entity] with id: {}", id);
        if ([entity]Service.delete[Entity](id)) {
            log.info("Successfully deleted [entity] with id: {}", id);
            return ResponseEntity.noContent().build();
        } else {
            log.warn("Cannot delete - [entity] not found with id: {}", id);
            return ResponseEntity.notFound().build();
        }
    }
}
```

**Verbindliche Regeln:**
* `@PreAuthorize` auf Klassen-Ebene
* GET all: gibt `List` direkt zurück (kein ResponseEntity)
* GET by id: `Optional.map()` / `orElseGet()` Pattern
* POST: gibt `HttpStatus.CREATED` zurück
* PUT: prüft Existenz, dann Update
* DELETE: gibt `204 No Content` oder `404 Not Found` zurück
* POST/PUT: `catch (IllegalArgumentException e)` → `badRequest().body(e.getMessage())`
* Alle Return-Types: `ResponseEntity<?>` für create/update, `ResponseEntity<Void>` für delete

---

### Frontend Import-Reihenfolge

```typescript
// 1. Angular Core
import { Component, OnInit, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

// 2. Projekt-Services
import { [Entity]Service } from '../../services/[entity].service';
import { TranslationService } from '../../services/translation.service';

// 3. Projekt-Models
import { [Entity] } from '../../models/[entity].model';

// 4. Projekt-Komponenten und Pipes
import { [Entity]FormComponent } from '../[entity]-form/[entity]-form.component';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { SwissDatePipe } from '../../pipes/swiss-date.pipe';
import { KebabMenuComponent, KebabMenuItem } from '../kebab-menu/kebab-menu.component';
import { ColumnResizeDirective } from '../../directives/column-resize.directive';
import { IconComponent } from '../icon/icon.component';
```

---

### Frontend Model-Struktur

```typescript
// Enums vor Interfaces, mit expliziten String-Werten
export enum [EnumName] {
  VALUE1 = 'VALUE1',
  VALUE2 = 'VALUE2'
}

export interface [Model] {
  id?: number;              // immer optional
  field1: string;
  dateField: string;        // ISO-Format: YYYY-MM-DD
  optionalField?: string;   // optionale Felder mit ?
}
```

---

### Frontend Service-Struktur

```typescript
@Injectable({ providedIn: 'root' })
export class [Entity]Service {
  private apiUrl = 'http://localhost:8090/api/[entities]';

  constructor(private http: HttpClient) {}

  // Methoden-Reihenfolge: getAll → getById → create → update → delete
  getAll[Entities](): Observable<[Entity][]> {
    return this.http.get<[Entity][]>(this.apiUrl);
  }

  get[Entity]ById(id: number): Observable<[Entity]> {
    return this.http.get<[Entity]>(`${this.apiUrl}/${id}`);
  }

  create[Entity]([entity]: [Entity]): Observable<[Entity]> {
    return this.http.post<[Entity]>(this.apiUrl, [entity]);
  }

  update[Entity](id: number, [entity]: [Entity]): Observable<[Entity]> {
    return this.http.put<[Entity]>(`${this.apiUrl}/${id}`, [entity]);
  }

  delete[Entity](id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
```

---

### Frontend List-Component-Struktur

```typescript
@Component({
  selector: 'app-[entity]-list',
  standalone: true,
  imports: [CommonModule, [Entity]FormComponent, TranslatePipe, SwissDatePipe,
            KebabMenuComponent, ColumnResizeDirective, IconComponent],
  templateUrl: './[entity]-list.component.html',
  styleUrls: ['./[entity]-list.component.css']
})
export class [Entity]ListComponent implements OnInit {

  // 1. Daten
  [entities]: [Entity][] = [];
  selected[Entity]: [Entity] | null = null;
  showForm = false;

  // 2. Messages
  message = '';
  messageType: 'success' | 'error' = 'success';

  // 3. Sortierung
  sortColumn: string | null = '[defaultField]';
  sortDirection: 'asc' | 'desc' = 'asc';

  // 4. Kebab-Menü
  menuItems: KebabMenuItem[] = [
    { label: 'BEARBEITEN', action: 'edit', icon: 'edit-2' },
    { label: 'KOPIEREN', action: 'copy', icon: 'copy' },
    { label: 'LOESCHEN', action: 'delete', danger: true, icon: 'trash-2' }
  ];

  constructor(
    private [entity]Service: [Entity]Service,
    private translationService: TranslationService
  ) {}

  // Methoden-Reihenfolge:
  // ngOnInit → load → onCreateNew → onEdit → onCopy → onDelete →
  // onMenuAction → onFormSubmit → onFormCancel → onSort → showMessage → dismissMessage

  ngOnInit(): void {
    this.load[Entities]();
  }

  load[Entities](): void {
    this.[entity]Service.getAll[Entities]().subscribe({
      next: (data) => { this.[entities] = data; },
      error: (error) => { this.showMessage('FEHLER_LADEN', 'error'); }
    });
  }

  onCreateNew(): void {
    this.selected[Entity] = null;
    this.showForm = true;
  }

  onEdit([entity]: [Entity]): void {
    this.selected[Entity] = { ...[entity] };
    this.showForm = true;
  }

  onCopy([entity]: [Entity]): void {
    const { id, ...[entity]OhneId } = [entity];
    this.selected[Entity] = { ...[entity]OhneId } as [Entity];
    this.showForm = true;
  }

  onDelete(id: number | undefined): void {
    if (!id) return;
    if (confirm(this.translationService.translate('[ENTITY]_LOESCHEN_BESTAETIGUNG'))) {
      this.[entity]Service.delete[Entity](id).subscribe({
        next: () => {
          this.showMessage('[ENTITY]_GELOESCHT', 'success');
          this.load[Entities]();
        },
        error: () => { this.showMessage('FEHLER_LOESCHEN', 'error'); }
      });
    }
  }

  onMenuAction(action: string, [entity]: [Entity]): void {
    switch (action) {
      case 'edit': this.onEdit([entity]); break;
      case 'copy': this.onCopy([entity]); break;
      case 'delete': this.onDelete([entity].id); break;
    }
  }

  onFormSubmit([entity]: [Entity]): void {
    if ([entity].id) {
      this.[entity]Service.update[Entity]([entity].id, [entity]).subscribe({
        next: () => {
          this.showMessage('[ENTITY]_AKTUALISIERT', 'success');
          this.showForm = false;
          this.load[Entities]();
        },
        error: (error) => {
          const errorMsg = error.error || 'FEHLER_AKTUALISIEREN';
          this.showMessage(errorMsg, 'error');
        }
      });
    } else {
      this.[entity]Service.create[Entity]([entity]).subscribe({
        next: () => {
          this.showMessage('[ENTITY]_ERSTELLT', 'success');
          this.showForm = false;
          this.load[Entities]();
        },
        error: (error) => {
          const errorMsg = error.error || 'FEHLER_ERSTELLEN';
          this.showMessage(errorMsg, 'error');
        }
      });
    }
  }

  onFormCancel(): void {
    this.showForm = false;
    this.selected[Entity] = null;
  }

  onSort(column: string): void {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
    // Sort-Logik
  }

  private showMessage(message: string, type: 'success' | 'error'): void {
    this.message = message;
    this.messageType = type;
    if (type === 'success') {
      setTimeout(() => { this.message = ''; }, 5000);
    }
  }

  dismissMessage(): void {
    this.message = '';
  }
}
```

**Verbindliche Regeln:**
* `subscribe({ next, error })` Objekt-Syntax (nicht `.subscribe(data => ...)`)
* Success-Messages verschwinden nach 5 Sekunden, Error-Messages bleiben
* Kopieren: `const { id, ...ohneId } = entity` Pattern
* Error-Handling: `error.error || 'FALLBACK_KEY'` (Backend-Meldung oder Fallback)
* Nach Mutation immer `load[Entities]()` aufrufen

---

### Frontend Form-Component-Struktur

```typescript
@Component({
  selector: 'app-[entity]-form',
  standalone: true,
  imports: [FormsModule, TranslatePipe, IconComponent],
  templateUrl: './[entity]-form.component.html',
  styleUrls: ['./[entity]-form.component.css']
})
export class [Entity]FormComponent implements OnInit {
  @Input() [entity]: [Entity] | null = null;
  @Output() save = new EventEmitter<[Entity]>();
  @Output() cancel = new EventEmitter<void>();

  formData: [Entity] = { /* Default-Werte */ };

  ngOnInit(): void {
    if (this.[entity]) {
      this.formData = { ...this.[entity] };
    } else {
      this.formData = { /* Default-Werte */ };
    }
  }

  onSubmit(): void {
    if (this.isFormValid()) {
      this.save.emit(this.formData);
    }
  }

  onCancel(): void {
    this.cancel.emit();
  }

  isFormValid(): boolean {
    return !!(
      this.formData.field1 &&
      this.formData.field2
      // weitere Pflichtfelder
    );
  }
}
```

**Verbindliche Regeln:**
* Kommunikation via `@Input` / `@Output` (kein direkter Service-Aufruf)
* `isFormValid()` mit `!!()` Pattern
* `ngOnInit`: Input kopieren oder Defaults setzen
* Form emittiert Events, Parent handhabt HTTP-Calls

---

### Frontend HTML-Template-Syntax

Immer neue Angular Control-Flow-Syntax verwenden:

```html
<!-- Bedingte Anzeige: @if (NICHT *ngIf) -->
@if (message) {
  <div class="zev-message" [ngClass]="'zev-message--' + messageType">
    <span>{{ message | translate }}</span>
  </div>
}

<!-- Listen: @for mit track (NICHT *ngFor) -->
@for ([entity] of [entities]; track [entity]) {
  <tr>...</tr>
}

<!-- Leere Liste -->
@if ([entities].length === 0) {
  <p>{{ 'KEINE_DATEN' | translate }}</p>
}

<!-- Formular-Titel: Erstellen vs. Bearbeiten -->
<h2>{{ formData.id ? ('[ENTITY]_BEARBEITEN' | translate) : ('NEUER_[ENTITY]' | translate) }}</h2>

<!-- Submit-Button: Erstellen vs. Aktualisieren -->
<button type="submit" class="zev-button zev-button--primary" [disabled]="!isFormValid()">
  <app-icon [name]="formData.id ? 'check' : 'plus'"></app-icon>
  {{ formData.id ? ('AKTUALISIEREN' | translate) : ('ERSTELLEN' | translate) }}
</button>

<!-- Alle Texte über TranslatePipe -->
{{ 'TRANSLATION_KEY' | translate }}

<!-- Sortierbare Tabelle mit appColumnResize -->
<table class="zev-table" appColumnResize>
  <thead>
    <tr>
      <th class="zev-table__header--sortable" (click)="onSort('field')">
        {{ 'FIELD' | translate }}
        @if (sortColumn === 'field') {
          <span>{{ sortDirection === 'asc' ? '▲' : '▼' }}</span>
        }
      </th>
    </tr>
  </thead>
</table>

<!-- Kebab-Menü in Tabelle -->
<td>
  <app-kebab-menu [items]="menuItems" (itemClick)="onMenuAction($event, entity)">
  </app-kebab-menu>
</td>
```

---

## Referenz
* Specs/generell.md - Allgemeine Anforderungen (i18n, Design System, Multi-Tenancy)
* CLAUDE.md - Projekt-Architektur, Build-Commands und Code-Vorlagen
