# Erstelle Frontend-Unit-Tests

Erstelle Frontend-Unit-Tests für die Angular-Komponente oder den Service, die aus Anforderungen generiert worden sind.

## Input

* **Feature-Name**: $ARGUMENTS (z.B. `Debitorkontrolle`) → liest `Specs/[Feature-Name].md`  
  Falls nicht angegeben: aus dem Konversations-Kontext ableiten (z.B. wenn zuvor `/0_anforderungen` oder `/1_umsetzungsplan` ausgeführt wurde); nur wenn unklar: nachfragen.

---

## Sub-Agent Ausführung

> **Als Sub-Agent:** Überspringe diesen Abschnitt und fahre direkt mit **Vorgehen** fort. Analysiere NUR:
> 1. Die Anforderungen in `Specs/[Feature-Name].md`
> 2. Den tatsächlich implementierten Code
> 3. Bestehende Tests als Vorlage

Starte einen neuen Sub-Agenten mit dem `Agent`-Tool:

- **description:** `"Frontend-Unit-Tests: [Feature-Name]"`
- **prompt:**

```
Du erstellst Frontend-Unit-Tests für die Angular-Komponente oder den Service, die aus Anforderungen generiert worden sind.
Feature: [Feature-Name]

Lies: .claude/commands/4_frontend-unit-tests.md
Fahre ab Abschnitt "Vorgehen" fort.
```

- **Hinweis:** Ersetze `[Feature-Name]` im `prompt` mit dem tatsächlichen Wert aus `$ARGUMENTS` (oder dem abgeleiteten Kontext).

---

## Vorgehen

### Phase 1: Unabhängige Code-Analyse
1. Lies die Anforderungen `Specs/[Feature-Name].md`
2. Finde alle relevanten Implementierungs-Dateien mit Glob/Grep:
   - `frontend-service/src/app/**/*.ts`
3. Analysiere die Ziel-Datei:
   - Public Methoden, Inputs (`@Input`), Outputs (`@Output`)
   - Abhängigkeiten (injizierte Services)
   - Template-Bindings und Event-Handler
4. Identifiziere Edge Cases aus dem Code selbst (Validierungen, Error-Handler, Conditional Logic)

### Phase 2: Test-Gap-Analyse
1. Prüfe existierende Tests (`*.spec.ts` im gleichen Ordner)
2. Vergleiche mit Spec-Anforderungen und implementiertem Code
3. Liste fehlende Test-Cases auf

### Phase 3: Test-Erstellung
1. Erstelle Tests für fehlende Cases (Vorlagen unten beachten)
2. Führe Tests aus: `npm test -- --include=<pfad/zur/xxx.spec.ts>` (Vitest, single-run; `--include` mehrfach angebbar). **Ohne `--include`** deutet `ng test` den Pfad als Projektnamen und bricht mit `Invalid values: Argument: project` ab.
3. Behebe Fehler bis Tests grün sind
4. Zum Schluss die **komplette** Suite grün laufen lassen: `npm test`

## Testpyramide
* **Unit Tests:** 70-80% der Tests (dieser Command)
* **E2E Tests:** 5-10% der Tests (separater Command)

---

## Test-Anforderungen
* **Tool:** **Vitest** (Runner `@angular/build:unit-test`, `runner: "vitest"`, jsdom) – **kein Karma/Jasmine mehr**.
* **Namenskonvention:** `*.spec.ts` (gleicher Ordner wie Quell-Datei)
* Mocke alle externen Abhängigkeiten (Services, HTTP-Calls)
* Teste: Initialisierung, Inputs/Outputs, public Methoden, Edge Cases, Fehlerbehandlung
* Keine E2E-Aspekte (DOM-Interaktion, Routing) - nur isolierte Logik

## Test-Infrastruktur (Vitest) — verbindlich beachten
* **Globals aktiv:** `describe`, `it`, `expect`, `beforeEach`, `afterEach`, `vi` sind global verfügbar – **nicht** importieren.
* **Spies statt Jasmine:** `createSpyObj`/`SpyObj` aus `src/testing/spy.ts` verwenden (Ersatz für `jasmine.createSpyObj`/`jasmine.SpyObj`).
  * `import { createSpyObj, SpyObj } from '<relativer-pfad>/testing/spy';`
  * `spy.method.and.returnValue(x)` → `spy.method.mockReturnValue(x)`
  * `spy.method.and.callFake(fn)` → `spy.method.mockImplementation(fn)`
  * `spy.method.and.throwError(...)` → `spy.method.mockImplementation(() => { throw ... })`
  * `spy.calls.reset()` → `spy.mockClear()`
* **`vi.spyOn` ruft standardmäßig DURCH** (anders als Jasmine): zum Stubben immer `.mockImplementation(() => {})` bzw. `.mockReturnValue(...)` anhängen (z.B. `vi.spyOn(window, 'confirm').mockReturnValue(true)`).
* **Matcher:** `toBeTrue()`/`toBeFalse()` gibt es NICHT → `toBe(true)`/`toBe(false)`. `jasmine.any(...)`/`jasmine.objectContaining(...)` → `expect.any(...)`/`expect.objectContaining(...)`.
* **Async/Timer:** zone.js patcht Vitest NICHT → Angulars `fakeAsync`/`tick`/`waitForAsync` werfen „ProxyZone not found".
  * `fakeAsync`/`tick` aus `src/testing/fake-async.ts` importieren (Vitest-Faketimer-Shim), **nicht** aus `@angular/core/testing`.
  * `waitForAsync(() => {...})` → `async () => { await ... }`.
* **jsdom-Shims** in `src/test-setup.ts` bereits vorhanden: `URL.createObjectURL`/`revokeObjectURL`, `DataTransfer`, `DragEvent`. Für Download-Tests genügt i.d.R. `vi.spyOn(URL, 'createObjectURL')`/`vi.spyOn(document, 'createElement')`.
* **HTTP:** `HttpClientTestingModule` + `HttpTestingController` werden weiterhin verwendet (kein `provideHttpClientTesting`).

## Service Tests

### Datei-Struktur (exakt einhalten)
```typescript
import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { XxxService } from './xxx.service';
import { Xxx } from '../models/xxx.model';

describe('XxxService', () => {
  let service: XxxService;
  let httpMock: HttpTestingController;
  const apiUrl = 'http://localhost:8090/api/xxx';

  const mockXxx: Xxx = {
    id: 1,
    // ... alle Properties
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [XxxService]
    });
    service = TestBed.inject(XxxService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('methodName', () => {
    it('should description', () => {
      service.methodName().subscribe(result => {
        expect(result).toEqual(...);
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('GET');
      req.flush(mockData);
    });
  });
});
```

### Pflicht-Tests für HTTP-Services
| Methode | Tests |
|---------|-------|
| getAll() | `should return all items` |
| getById(id) | `should return single item by id` |
| create(item) | `should create new item` |
| update(id, item) | `should update existing item` |
| delete(id) | `should delete item` |


## Component Tests

### List-Component Test (exakt einhalten)
```typescript
import { createSpyObj, SpyObj } from '../../../testing/spy';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { fakeAsync, tick } from '../../../testing/fake-async';
import { XxxListComponent } from './xxx-list.component';
import { XxxService } from '../../services/xxx.service';
import { TranslationService } from '../../services/translation.service';
import { of, throwError } from 'rxjs';

describe('XxxListComponent', () => {
  let component: XxxListComponent;
  let fixture: ComponentFixture<XxxListComponent>;

  // Services IMMER als SpyObj (aus testing/spy) mocken
  let xxxServiceSpy: SpyObj<XxxService>;
  let translationServiceSpy: SpyObj<TranslationService>;

  // Testdaten als Konstanten
  const mockItems: Xxx[] = [
    { id: 1, /* ... alle Properties */ },
    { id: 2, /* ... alle Properties */ }
  ];

  beforeEach(async () => {
    // SpyObj mit ALLEN Methoden die im Component verwendet werden
    xxxServiceSpy = createSpyObj<XxxService>('XxxService', [
      'getAllXxx', 'createXxx', 'updateXxx', 'deleteXxx'
    ]);
    xxxServiceSpy.getAllXxx.mockReturnValue(of(mockItems));
    xxxServiceSpy.createXxx.mockReturnValue(of(mockItems[0]));
    xxxServiceSpy.updateXxx.mockReturnValue(of(mockItems[0]));
    xxxServiceSpy.deleteXxx.mockReturnValue(of(void 0));

    // TranslationService: translate gibt Key zurück
    translationServiceSpy = createSpyObj<TranslationService>('TranslationService', ['translate']);
    translationServiceSpy.translate.mockImplementation((key: string) => key);

    // Standalone Component wird in imports importiert (NICHT in declarations)
    await TestBed.configureTestingModule({
      imports: [XxxListComponent],
      providers: [
        { provide: XxxService, useValue: xxxServiceSpy },
        { provide: TranslationService, useValue: translationServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(XxxListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  // Test-Gruppen: initialization → CRUD → sorting → messages

  describe('initialization', () => {
    it('should load items on init', () => {
      expect(xxxServiceSpy.getAllXxx).toHaveBeenCalled();
      expect(component.items.length).toBe(2);
    });
  });

  describe('onCreateNew', () => {
    it('should set showForm to true and selectedItem to null', () => {
      component.onCreateNew();
      expect(component.showForm).toBe(true);
      expect(component.selectedItem).toBeNull();
    });
  });

  describe('onEdit', () => {
    it('should set showForm to true with copied item', () => {
      component.onEdit(mockItems[0]);
      expect(component.showForm).toBe(true);
      expect(component.selectedItem).toEqual(mockItems[0]);
      expect(component.selectedItem).not.toBe(mockItems[0]); // Kopie, nicht Referenz
    });
  });

  describe('onFormSubmit', () => {
    it('should call create for new item', () => {
      const newItem = { /* ... ohne id */ } as Xxx;
      component.onFormSubmit(newItem);
      expect(xxxServiceSpy.createXxx).toHaveBeenCalledWith(newItem);
    });

    it('should call update for existing item', () => {
      component.onFormSubmit(mockItems[0]);
      expect(xxxServiceSpy.updateXxx).toHaveBeenCalledWith(mockItems[0].id, mockItems[0]);
    });

    it('should show error message on failure', () => {
      xxxServiceSpy.createXxx.mockReturnValue(throwError(() => ({ error: 'Fehler' })));
      component.onFormSubmit({ /* ... ohne id */ } as Xxx);
      expect(component.messageType).toBe('error');
    });
  });

  describe('onDelete', () => {
    it('should call delete and reload', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      component.onDelete(1);
      expect(xxxServiceSpy.deleteXxx).toHaveBeenCalledWith(1);
    });

    it('should not call delete when cancelled', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(false);
      component.onDelete(1);
      expect(xxxServiceSpy.deleteXxx).not.toHaveBeenCalled();
    });
  });

  describe('messages', () => {
    it('should auto-dismiss success message after 5s', fakeAsync(() => {
      component.showForm = false;
      xxxServiceSpy.deleteXxx.mockReturnValue(of(void 0));
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      component.onDelete(1);
      expect(component.message).not.toBe('');
      tick(5000);
      expect(component.message).toBe('');
    }));
  });
});
```

> **Import-Pfad-Hinweis:** `../../../testing/spy` und `../../../testing/fake-async` gelten für Komponenten unter `src/app/components/<name>/`. Tiefe je nach Ordnerebene anpassen (Service unter `src/app/services/` → `../../testing/...`).

### Form-Component Test (exakt einhalten)
```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { XxxFormComponent } from './xxx-form.component';
import { TranslationService } from '../../services/translation.service';

describe('XxxFormComponent', () => {
  let component: XxxFormComponent;
  let fixture: ComponentFixture<XxxFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [XxxFormComponent],
      providers: [
        { provide: TranslationService, useValue: { translate: (k: string) => k } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(XxxFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should set default values when no input', () => {
      expect(component.formData).toBeDefined();
      // Prüfe Default-Werte
    });

    it('should copy input data when provided', () => {
      const input = { id: 1, /* ... */ };
      component.item = input;
      component.ngOnInit();
      expect(component.formData).toEqual(input);
      expect(component.formData).not.toBe(input); // Kopie
    });
  });

  describe('isFormValid', () => {
    it('should return false when required field empty', () => {
      component.formData = { /* leere Felder */ } as Xxx;
      expect(component.isFormValid()).toBe(false);
    });

    it('should return true when all required fields filled', () => {
      component.formData = { /* alle Felder */ };
      expect(component.isFormValid()).toBe(true);
    });
  });

  describe('events', () => {
    it('should emit save on valid submit', () => {
      vi.spyOn(component.save, 'emit');
      component.formData = { /* gültige Daten */ };
      component.onSubmit();
      expect(component.save.emit).toHaveBeenCalledWith(component.formData);
    });

    it('should not emit save on invalid submit', () => {
      vi.spyOn(component.save, 'emit');
      component.formData = { /* ungültige Daten */ } as Xxx;
      component.onSubmit();
      expect(component.save.emit).not.toHaveBeenCalled();
    });

    it('should emit cancel', () => {
      vi.spyOn(component.cancel, 'emit');
      component.onCancel();
      expect(component.cancel.emit).toHaveBeenCalled();
    });
  });
});
```

**Verbindliche Regeln für Component-Tests:**
* Standalone Components in `imports` (nicht `declarations`)
* Services als `createSpyObj<T>(...)` aus `src/testing/spy.ts` mit ALLEN verwendeten Methoden (nicht `jasmine.createSpyObj`)
* TranslationService-Mock: `translate: (k: string) => k` (gibt Key zurück); bei `SpyObj` via `.mockImplementation((k) => k)`
* Testdaten als `const` oben im describe-Block
* Prüfe dass `onEdit` eine Kopie erstellt (nicht die Referenz)
* Teste Success- und Error-Pfade bei CRUD-Operationen
* `fakeAsync`/`tick` aus `src/testing/fake-async.ts` für setTimeout-Tests (Message auto-dismiss)
* Browser-/Global-Spies mit `vi.spyOn(...)` + `.mockReturnValue/.mockImplementation` (ruft sonst durch)

### Naming-Konvention für describe/it Blöcke

```typescript
describe('XxxComponent', () => {
  describe('initialization', () => {
    it('should load data on init', ...);
    it('should set default values', ...);
  });

  describe('methodName', () => {
    it('should do something when condition', ...);
    it('should throw error when invalid', ...);
  });

  describe('events', () => {
    it('should emit save event with form data', ...);
    it('should emit cancel event', ...);
  });
});
```

---

## Pflicht-Tests pro Komponenten-Typ

### Form-Komponenten
| Aspekt | Tests |
|--------|-------|
| Initialisierung | `should set default values`, `should populate with input data` |
| Validierung | `should return false when field empty`, `should return true when valid` |
| Events | `should emit save on valid submit`, `should not emit on invalid`, `should emit cancel` |

### List-Komponenten
| Aspekt | Tests |
|--------|-------|
| Initialisierung | `should load items on init`, `should show empty state` |
| CRUD | `should call create`, `should call update`, `should call delete` |
| Sortierung | `should toggle sort direction`, `should sort by column` |
| Messages | `should show success message`, `should show error message` |


---

## Test-Daten & Mocking

* **Unit Tests:** Mocks für alle externen Abhängigkeiten (Services, HTTP-Calls)
* **Fixtures:** Wiederverwendbare Testdaten als Konstanten im Test

---

## Ausführung

Nach dem Erstellen der Tests ausführen und Fehler beheben bis alle Tests grün sind (Vitest, jsdom, single-run):

| Befehl | Beschreibung |
|--------|--------------|
| `npm test` | Alle Tests (Vitest, single-run, headless jsdom) |
| `npm test -- --include=src/app/services/tarif.service.spec.ts` | Einzelne Test-Datei (`--include` mehrfach angebbar) |
| `npm run test:watch` | Watch-Modus (lokale Entwicklung) |
| `npm run test:coverage` | Mit Coverage-Report (`coverage/frontend-service`) |