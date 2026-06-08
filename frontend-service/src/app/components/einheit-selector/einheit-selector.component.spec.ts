import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EinheitSelectorComponent } from './einheit-selector.component';
import { EinheitService } from '../../services/einheit.service';
import { TranslationService } from '../../services/translation.service';
import { Einheit, EinheitTyp } from '../../models/einheit.model';
import { of } from 'rxjs';

describe('EinheitSelectorComponent', () => {
  let component: EinheitSelectorComponent;
  let fixture: ComponentFixture<EinheitSelectorComponent>;
  let einheitServiceSpy: jasmine.SpyObj<EinheitService>;

  const consumer1: Einheit = { id: 1, name: 'Wohnung A', typ: EinheitTyp.CONSUMER };
  const consumer2: Einheit = { id: 2, name: 'Wohnung B', typ: EinheitTyp.CONSUMER };
  const producer: Einheit = { id: 3, name: 'Solar Anlage', typ: EinheitTyp.PRODUCER };

  beforeEach(async () => {
    einheitServiceSpy = jasmine.createSpyObj('EinheitService', ['getAllEinheiten']);
    einheitServiceSpy.getAllEinheiten.and.returnValue(of([]));

    const translationServiceSpy = jasmine.createSpyObj('TranslationService', ['translate']);
    translationServiceSpy.translate.and.callFake((key: string) => key);

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
  });

  describe('onSelectAllToggle (default - alle)', () => {
    it('should select all einheiten including producers', () => {
      component.onSelectAllToggle();
      expect(component.selectedEinheitIds.has(1)).toBeTrue();
      expect(component.selectedEinheitIds.has(2)).toBeTrue();
      expect(component.selectedEinheitIds.has(3)).toBeTrue();
      expect(component.allSelected()).toBeTrue();
    });

    it('should clear selection on second toggle', () => {
      component.onSelectAllToggle();
      component.onSelectAllToggle();
      expect(component.selectedEinheitIds.size).toBe(0);
      expect(component.allSelected()).toBeFalse();
    });
  });

  describe('onSelectAllToggle (onlyConsumers)', () => {
    beforeEach(() => {
      component.onlyConsumers = true;
    });

    it('should select ONLY consumers, not producers', () => {
      component.onSelectAllToggle();
      expect(component.selectedEinheitIds.has(1)).toBeTrue();
      expect(component.selectedEinheitIds.has(2)).toBeTrue();
      expect(component.selectedEinheitIds.has(3)).toBeFalse();
    });

    it('should report allSelected as true when all consumers are selected', () => {
      component.onSelectAllToggle();
      expect(component.allSelected()).toBeTrue();
      expect(component.someSelected()).toBeFalse();
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
  });
});
