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

### Datei-Struktur (exakt einhalten)
```typescript
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { XxxComponent } from './xxx.component';
import { XxxService } from '../../services/xxx.service';
import { TranslationService } from '../../services/translation.service';
import { of, throwError } from 'rxjs';

describe('XxxComponent', () => {
  let component: XxxComponent;
  let fixture: ComponentFixture<XxxComponent>;
  let xxxServiceSpy: jasmine.SpyObj<XxxService>;

  const mockData = { /* ... */ };

  beforeEach(async () => {
    xxxServiceSpy = jasmine.createSpyObj('XxxService', ['getAll', 'create', 'update', 'delete']);
    xxxServiceSpy.getAll.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [XxxComponent],
      providers: [
        { provide: XxxService, useValue: xxxServiceSpy },
        { provide: TranslationService, useValue: { translate: (k: string) => k } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(XxxComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should load data on init', () => {
      expect(xxxServiceSpy.getAll).toHaveBeenCalled();
    });
  });

  describe('methodName', () => {
    it('should description', () => {
      // Arrange
      // Act
      // Assert
    });
  });
});
```

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