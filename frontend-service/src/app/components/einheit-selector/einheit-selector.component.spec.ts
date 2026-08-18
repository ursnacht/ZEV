import { createSpyObj, SpyObj } from '../../../testing/spy';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EinheitSelectorComponent } from './einheit-selector.component';
import { EinheitService } from '../../services/einheit.service';
import { TranslationService } from '../../services/translation.service';
import { Einheit, EinheitTyp } from '../../models/einheit.model';
import { of, throwError } from 'rxjs';

describe('EinheitSelectorComponent', () => {
  let component: EinheitSelectorComponent;
  let fixture: ComponentFixture<EinheitSelectorComponent>;
  let einheitServiceSpy: SpyObj<EinheitService>;

  const consumer1: Einheit = { id: 1, name: 'Wohnung A', typ: EinheitTyp.CONSUMER };
  const consumer2: Einheit = { id: 2, name: 'Wohnung B', typ: EinheitTyp.CONSUMER };
  const producer: Einheit = { id: 3, name: 'Solar Anlage', typ: EinheitTyp.PRODUCER };
  const ladestation: Einheit = {
    id: 4, name: 'Ladestation 1', typ: EinheitTyp.LADESTATION, messpunkt: 'RFID-04711'
  };

  beforeEach(async () => {
    einheitServiceSpy = createSpyObj<EinheitService>('EinheitService', ['getAllEinheiten']);
    einheitServiceSpy.getAllEinheiten.mockReturnValue(of([]));

    const translationServiceSpy = createSpyObj<TranslationService>('TranslationService', ['translate']);
    translationServiceSpy.translate.mockImplementation((key: string) => key);

    await TestBed.configureTestingModule({
      imports: [EinheitSelectorComponent],
      providers: [
        { provide: EinheitService, useValue: einheitServiceSpy },
        { provide: TranslationService, useValue: translationServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(EinheitSelectorComponent);
    component = fixture.componentInstance;
    // Daten direkt setzen, um ngOnInit-Laden zu umgehen
    component.einheiten = [consumer1, consumer2, producer];
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('selectableEinheiten', () => {
    it('should return all einheiten by default', () => {
      expect(component.selectableEinheiten.length).toBe(3);
    });

    it('should return only consumers when onlyConsumers is true', () => {
      component.onlyConsumers = true;
      expect(component.selectableEinheiten).toEqual([consumer1, consumer2]);
    });

    it('should include ladestationen when onlyConsumers is true', () => {
      // Ein Nutzer ohne Wohnung wird ueber seine Ladestation abgerechnet - die Einheit muss
      // in der Rechnungsmaske waehlbar sein (Specs/Ladestationen.md FR-1.5).
      component.einheiten = [consumer1, producer, ladestation];
      component.onlyConsumers = true;
      expect(component.selectableEinheiten).toEqual([consumer1, ladestation]);
    });

    it('should still exclude producers and bilanz-messpunkte when onlyConsumers is true', () => {
      component.einheiten = [
        consumer1,
        producer,
        ladestation,
        { id: 5, name: 'Bezug', typ: EinheitTyp.BEZUG },
        { id: 6, name: 'Rücklieferung', typ: EinheitTyp.RUECKLIEFERUNG }
      ];
      component.onlyConsumers = true;
      expect(component.selectableEinheiten.map(e => e.id)).toEqual([1, 4]);
    });

    it('should return all einheiten including ladestationen by default', () => {
      component.einheiten = [consumer1, producer, ladestation];
      expect(component.selectableEinheiten.length).toBe(3);
    });
  });

  describe('ngOnInit', () => {
    it('should load the einheiten and sort consumers to the front', () => {
      einheitServiceSpy.getAllEinheiten.mockReturnValue(of([ladestation, producer, consumer2, consumer1]));

      component.ngOnInit();

      expect(component.einheiten.length).toBe(4);
      expect(component.einheiten.slice(0, 2).map(e => e.id)).toEqual([1, 2]);
      expect(component.einheiten.slice(2).map(e => e.id).sort()).toEqual([3, 4]);
    });

    it('should leave the list empty when loading fails', () => {
      einheitServiceSpy.getAllEinheiten.mockReturnValue(throwError(() => new Error('Network error')));

      component.einheiten = [];
      expect(() => component.ngOnInit()).not.toThrow();
      expect(component.einheiten).toEqual([]);
    });
  });

  describe('onEinheitToggle', () => {
    it('should select a ladestation and emit it', () => {
      component.einheiten = [consumer1, ladestation];
      const emitted: Einheit[][] = [];
      component.selectionChange.subscribe(e => emitted.push(e));

      component.onEinheitToggle(4);

      expect(component.selectedEinheitIds.has(4)).toBe(true);
      expect(emitted[0]).toEqual([ladestation]);
    });

    it('should deselect an already selected einheit', () => {
      component.onEinheitToggle(1);
      component.onEinheitToggle(1);
      expect(component.selectedEinheitIds.has(1)).toBe(false);
    });

    it('should report someSelected while only part is chosen', () => {
      component.einheiten = [consumer1, consumer2, ladestation];
      component.onlyConsumers = true;
      component.onEinheitToggle(1);
      expect(component.someSelected()).toBe(true);
      expect(component.allSelected()).toBe(false);
    });
  });

  describe('onSelectAllToggle (default - alle)', () => {
    it('should select all einheiten including producers', () => {
      component.onSelectAllToggle();
      expect(component.selectedEinheitIds.has(1)).toBe(true);
      expect(component.selectedEinheitIds.has(2)).toBe(true);
      expect(component.selectedEinheitIds.has(3)).toBe(true);
      expect(component.allSelected()).toBe(true);
    });

    it('should clear selection on second toggle', () => {
      component.onSelectAllToggle();
      component.onSelectAllToggle();
      expect(component.selectedEinheitIds.size).toBe(0);
      expect(component.allSelected()).toBe(false);
    });
  });

  describe('onSelectAllToggle (onlyConsumers)', () => {
    beforeEach(() => {
      component.onlyConsumers = true;
    });

    it('should select ONLY consumers, not producers', () => {
      component.onSelectAllToggle();
      expect(component.selectedEinheitIds.has(1)).toBe(true);
      expect(component.selectedEinheitIds.has(2)).toBe(true);
      expect(component.selectedEinheitIds.has(3)).toBe(false);
    });

    it('should report allSelected as true when all consumers are selected', () => {
      component.onSelectAllToggle();
      expect(component.allSelected()).toBe(true);
      expect(component.someSelected()).toBe(false);
    });

    it('should emit only the selected consumers', () => {
      const emitted: Einheit[] = [];
      component.selectionChange.subscribe(e => emitted.push(...e));
      component.onSelectAllToggle();
      expect(emitted).toEqual([consumer1, consumer2]);
    });

    it('should clear consumers on second toggle', () => {
      component.onSelectAllToggle();
      component.onSelectAllToggle();
      expect(component.selectedEinheitIds.size).toBe(0);
    });

    it('should also select ladestationen', () => {
      component.einheiten = [consumer1, producer, ladestation];
      component.onSelectAllToggle();
      expect(component.selectedEinheitIds.has(4)).toBe(true);
      expect(component.selectedEinheitIds.has(3)).toBe(false);
      expect(component.allSelected()).toBe(true);
    });
  });
});
