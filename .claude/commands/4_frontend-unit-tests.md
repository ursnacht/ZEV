# Erstelle Frontend-Unit-Tests

Erstelle Unit Tests für die angegebene Angular-Komponente oder Service.

## Input
* Ziel-Datei: $ARGUMENTS (z.B. `einheit-form.component.ts` oder `einheit.service.ts`)

---

## Unabhängige Ausführung

Dieser Skill arbeitet UNABHÄNGIG vom Kontext der aktuellen Session.

**Analysiere NUR:**
1. Die Spec-Datei (falls angegeben)
2. Den tatsächlich implementierten Code
3. Bestehende Tests als Vorlage

**IGNORIERE** jeglichen Kontext aus der vorherigen Konversation.

---

## Vorgehen

### Phase 1: Unabhängige Code-Analyse
1. Lies die Spec-Datei (falls vorhanden) - extrahiere Anforderungen
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
2. Führe Tests aus: `npm test -- --include=**/xxx.spec.ts --no-watch --browsers=ChromeHeadless`
3. Behebe Fehler bis Tests grün sind

## Testpyramide
* **Unit Tests:** 70-80% der Tests (dieser Command)
* **E2E Tests:** 5-10% der Tests (separater Command)

---

## Test-Anforderungen
* **Tool:** Jasmine mit Karma
* **Namenskonvention:** `*.spec.ts` (gleicher Ordner wie Quell-Datei)
* Mocke alle externen Abhängigkeiten (Services, HTTP-Calls)
* Teste: Initialisierung, Inputs/Outputs, public Methoden, Edge Cases, Fehlerbehandlung
* Keine E2E-Aspekte (DOM-Interaktion, Routing) - nur isolierte Logik

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
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { XxxListComponent } from './xxx-list.component';
import { XxxService } from '../../services/xxx.service';
import { TranslationService } from '../../services/translation.service';
import { of, throwError } from 'rxjs';

describe('XxxListComponent', () => {
  let component: XxxListComponent;
  let fixture: ComponentFixture<XxxListComponent>;

  // Services IMMER als jasmine.SpyObj mocken
  let xxxServiceSpy: jasmine.SpyObj<XxxService>;
  let translationServiceSpy: jasmine.SpyObj<TranslationService>;

  // Testdaten als Konstanten
  const mockItems: Xxx[] = [
    { id: 1, /* ... alle Properties */ },
    { id: 2, /* ... alle Properties */ }
  ];

  beforeEach(async () => {
    // SpyObj mit ALLEN Methoden die im Component verwendet werden
    xxxServiceSpy = jasmine.createSpyObj('XxxService', [
      'getAllXxx', 'createXxx', 'updateXxx', 'deleteXxx'
    ]);
    xxxServiceSpy.getAllXxx.and.returnValue(of(mockItems));
    xxxServiceSpy.createXxx.and.returnValue(of(mockItems[0]));
    xxxServiceSpy.updateXxx.and.returnValue(of(mockItems[0]));
    xxxServiceSpy.deleteXxx.and.returnValue(of(void 0));

    // TranslationService: translate gibt Key zurück
    translationServiceSpy = jasmine.createSpyObj('TranslationService', ['translate']);
    translationServiceSpy.translate.and.callFake((key: string) => key);

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
      expect(component.showForm).toBeTrue();
      expect(component.selectedItem).toBeNull();
    });
  });

  describe('onEdit', () => {
    it('should set showForm to true with copied item', () => {
      component.onEdit(mockItems[0]);
      expect(component.showForm).toBeTrue();
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
      xxxServiceSpy.createXxx.and.returnValue(throwError(() => ({ error: 'Fehler' })));
      component.onFormSubmit({ /* ... ohne id */ } as Xxx);
      expect(component.messageType).toBe('error');
    });
  });

  describe('onDelete', () => {
    it('should call delete and reload', () => {
      spyOn(window, 'confirm').and.returnValue(true);
      component.onDelete(1);
      expect(xxxServiceSpy.deleteXxx).toHaveBeenCalledWith(1);
    });

    it('should not call delete when cancelled', () => {
      spyOn(window, 'confirm').and.returnValue(false);
      component.onDelete(1);
      expect(xxxServiceSpy.deleteXxx).not.toHaveBeenCalled();
    });
  });

  describe('messages', () => {
    it('should auto-dismiss success message after 5s', fakeAsync(() => {
      component.showForm = false;
      xxxServiceSpy.deleteXxx.and.returnValue(of(void 0));
      spyOn(window, 'confirm').and.returnValue(true);
      component.onDelete(1);
      expect(component.message).not.toBe('');
      tick(5000);
      expect(component.message).toBe('');
    }));
  });
});
```

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
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return true when all required fields filled', () => {
      component.formData = { /* alle Felder */ };
      expect(component.isFormValid()).toBeTrue();
    });
  });

  describe('events', () => {
    it('should emit save on valid submit', () => {
      spyOn(component.save, 'emit');
      component.formData = { /* gültige Daten */ };
      component.onSubmit();
      expect(component.save.emit).toHaveBeenCalledWith(component.formData);
    });

    it('should not emit save on invalid submit', () => {
      spyOn(component.save, 'emit');
      component.formData = { /* ungültige Daten */ } as Xxx;
      component.onSubmit();
      expect(component.save.emit).not.toHaveBeenCalled();
    });

    it('should emit cancel', () => {
      spyOn(component.cancel, 'emit');
      component.onCancel();
      expect(component.cancel.emit).toHaveBeenCalled();
    });
  });
});
```

**Verbindliche Regeln für Component-Tests:**
* Standalone Components in `imports` (nicht `declarations`)
* Services als `jasmine.createSpyObj` mit ALLEN verwendeten Methoden
* TranslationService-Mock: `translate: (k: string) => k` (gibt Key zurück)
* Testdaten als `const` oben im describe-Block
* Prüfe dass `onEdit` eine Kopie erstellt (nicht die Referenz)
* Teste Success- und Error-Pfade bei CRUD-Operationen
* `fakeAsync`/`tick` für setTimeout-Tests (Message auto-dismiss)

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

## Hinweise für Tester
### Unit Tests interaktiv ausführen

`cd frontend-service`

* Alle Tests einmalig (headless)
  * `npm.cmd test -- --browsers=ChromeHeadless --watch=false`
* Tests mit Live-Browser (interaktiv)
  * `npm.cmd test`
* Tests im Watch-Mode (re-run bei Änderungen)
  * `npm.cmd test -- --watch=true`
* Einzelne Test-Datei
  * `npm.cmd test -- --include=**/tarif.service.spec.ts`
* Mit Code-Coverage Report
  * `npm.cmd test -- --code-coverage --browsers=ChromeHeadless --watch=false`

### npm test ausführen
`cd frontend-service`
`npm.cmd test`

#### Was passiert?
1. Chrome öffnet sich - Ein Browser-Fenster erscheint mit der Karma-Testseite
2. Tests laufen automatisch - Ergebnisse werden im Browser und Terminal angezeigt
3. Watch-Mode aktiv - Bei Dateiänderungen laufen Tests automatisch neu

#### Browser-Ansicht
Die Karma-Seite zeigt:
* Grüne Punkte = erfolgreiche Tests
* Rote Punkte = fehlgeschlagene Tests
* Klick auf einen Test zeigt Details

#### Beenden
Drücke Ctrl+C im Terminal, um Karma zu stoppen.

#### Ohne Browser-Fenster (headless)
`npm.cmd test -- --browsers=ChromeHeadless --watch=false`

Dies ist nützlich für CI/CD oder wenn du kein Browser-Fenster möchtest.