import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TarifpositionFormComponent } from './tarifposition-form.component';
import { TranslationService } from '../../services/translation.service';
import { Erfassungsart, Tarifposition } from '../../models/tarifposition.model';
import { Tarif, TarifTyp } from '../../models/tarif.model';

describe('TarifpositionFormComponent', () => {
  let component: TarifpositionFormComponent;
  let fixture: ComponentFixture<TarifpositionFormComponent>;

  const mockTranslationService = {
    translate: (key: string) => key
  };

  const mockTarife: Tarif[] = [
    {
      id: 3,
      bezeichnung: 'Ladestrom 2026',
      tariftyp: TarifTyp.LADESTROM,
      preis: 0.35,
      gueltigVon: '2026-01-01',
      gueltigBis: '2026-12-31'
    },
    {
      id: 4,
      bezeichnung: 'Ladestrom 2027',
      tariftyp: TarifTyp.LADESTROM,
      preis: 0.38,
      gueltigVon: '2027-01-01',
      gueltigBis: '2027-12-31'
    }
  ];

  const aktuellesJahr = new Date().getFullYear();
  const aktuellesQuartal = Math.floor(new Date().getMonth() / 3) + 1;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TarifpositionFormComponent],
      providers: [
        { provide: TranslationService, useValue: mockTranslationService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(TarifpositionFormComponent);
    component = fixture.componentInstance;
    component.einheitId = 7;
    component.tarife = mockTarife;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should set default values when no position is provided', () => {
      expect(component.formData.einheitId).toBe(7);
      expect(component.formData.tarifId).toBe(0);
      expect(component.formData.jahr).toBe(aktuellesJahr);
      expect(component.formData.quartal).toBe(aktuellesQuartal);
      expect(component.formData.menge).toBe(0);
      expect(component.formData.id).toBeUndefined();
    });

    it('should offer the last two years, the current one and the next one', () => {
      expect(component.jahrOptionen).toEqual([
        aktuellesJahr - 2, aktuellesJahr - 1, aktuellesJahr, aktuellesJahr + 1
      ]);
    });

    it('should offer all four quarters', () => {
      expect(component.quartalOptionen).toEqual([1, 2, 3, 4]);
    });

    it('should fall back to einheitId 0 when no mieter is given', () => {
      const fresh = TestBed.createComponent(TarifpositionFormComponent).componentInstance;
      fresh.tarife = mockTarife;
      fresh.ngOnInit();
      expect(fresh.formData.einheitId).toBe(0);
    });

    it('should preselect the tarif when exactly one is available', () => {
      component.tarife = [mockTarife[0]];
      component.ngOnInit();
      expect(component.formData.tarifId).toBe(3);
    });

    it('should not preselect a tarif when several are available', () => {
      component.tarife = mockTarife;
      component.ngOnInit();
      expect(component.formData.tarifId).toBe(0);
    });

    it('should not preselect a tarif when none is available', () => {
      component.tarife = [];
      component.ngOnInit();
      expect(component.formData.tarifId).toBe(0);
    });

    it('should leave quellReferenz undefined for a new position', () => {
      component.ngOnInit();
      expect(component.formData.quellReferenz).toBeUndefined();
    });
  });

  describe('initialization with position input', () => {
    const existingPosition: Tarifposition = {
      id: 5,
      einheitId: 7,
      tarifId: 3,
      jahr: aktuellesJahr,
      quartal: 2,
      menge: 123.456,
      erfassungsart: Erfassungsart.MANUELL,
      quellReferenz: 'LP-01',
      bemerkung: 'Beleg 42'
    };

    it('should populate the form with the given position', () => {
      component.position = existingPosition;
      component.ngOnInit();
      expect(component.formData).toEqual(existingPosition);
    });

    it('should copy the position instead of referencing it', () => {
      component.position = existingPosition;
      component.ngOnInit();
      expect(component.formData).not.toBe(existingPosition);
    });

    it('should add a jahr outside the default options and keep the list sorted', () => {
      component.position = { ...existingPosition, jahr: 2019 };
      component.ngOnInit();
      expect(component.jahrOptionen).toContain(2019);
      expect(component.jahrOptionen).toEqual([...component.jahrOptionen].sort((a, b) => a - b));
    });

    it('should not duplicate a jahr that is already in the options', () => {
      component.position = { ...existingPosition, jahr: aktuellesJahr };
      component.ngOnInit();
      expect(component.jahrOptionen.filter(j => j === aktuellesJahr).length).toBe(1);
    });

    it('should keep a copied position without id (create mode)', () => {
      const { id, ...kopie } = existingPosition;
      component.position = kopie as Tarifposition;
      component.ngOnInit();
      expect(component.formData.id).toBeUndefined();
      expect(component.formData.jahr).toBe(existingPosition.jahr);
      expect(component.formData.quartal).toBe(existingPosition.quartal);
      expect(component.formData.menge).toBe(existingPosition.menge);
      expect(component.formData.quellReferenz).toBe('LP-01');
      expect(component.formData.bemerkung).toBe('Beleg 42');
    });
  });

  describe('isFormValid', () => {
    const validData: Tarifposition = {
      einheitId: 7,
      tarifId: 3,
      jahr: 2026,
      quartal: 1,
      menge: 10
    };

    it('should return true when all required fields are set', () => {
      component.formData = { ...validData };
      expect(component.isFormValid()).toBe(true);
    });

    it('should return false when no mieter is set', () => {
      component.formData = { ...validData, einheitId: 0 };
      expect(component.isFormValid()).toBe(false);
    });

    it('should return false when no tarif is selected', () => {
      component.formData = { ...validData, tarifId: 0 };
      expect(component.isFormValid()).toBe(false);
    });

    it('should return false when jahr is missing', () => {
      component.formData = { ...validData, jahr: 0 };
      expect(component.isFormValid()).toBe(false);
    });

    it('should return false when quartal is missing', () => {
      component.formData = { ...validData, quartal: 0 };
      expect(component.isFormValid()).toBe(false);
    });

    it('should return false when menge is negative', () => {
      component.formData = { ...validData, menge: -1 };
      expect(component.isFormValid()).toBe(false);
    });

    it('should return false when menge is null', () => {
      component.formData = { ...validData, menge: null as unknown as number };
      expect(component.isFormValid()).toBe(false);
    });

    it('should return false when menge is undefined (cleared input)', () => {
      component.formData = { ...validData, menge: undefined as unknown as number };
      expect(component.isFormValid()).toBe(false);
    });

    it('should return true when menge is zero', () => {
      component.formData = { ...validData, menge: 0 };
      expect(component.isFormValid()).toBe(true);
    });

    it('should return true when menge has three decimals', () => {
      component.formData = { ...validData, menge: 123.456 };
      expect(component.isFormValid()).toBe(true);
    });
  });

  describe('events', () => {
    const validData: Tarifposition = {
      einheitId: 7,
      tarifId: 3,
      jahr: 2026,
      quartal: 1,
      menge: 10
    };

    it('should emit save with the form data on valid submit', () => {
      const saveSpy = vi.spyOn(component.save, 'emit');
      component.formData = { ...validData };

      component.onSubmit();

      expect(saveSpy).toHaveBeenCalledWith(component.formData);
    });

    it('should not emit save on invalid submit', () => {
      const saveSpy = vi.spyOn(component.save, 'emit');
      component.formData = { ...validData, tarifId: 0 };

      component.onSubmit();

      expect(saveSpy).not.toHaveBeenCalled();
    });

    it('should not emit save when menge is negative', () => {
      const saveSpy = vi.spyOn(component.save, 'emit');
      component.formData = { ...validData, menge: -5 };

      component.onSubmit();

      expect(saveSpy).not.toHaveBeenCalled();
    });

    it('should emit cancel', () => {
      const cancelSpy = vi.spyOn(component.cancel, 'emit');

      component.onCancel();

      expect(cancelSpy).toHaveBeenCalled();
    });
  });
});
