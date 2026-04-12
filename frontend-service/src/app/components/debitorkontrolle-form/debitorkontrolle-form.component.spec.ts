import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebitorkontrolleFormComponent } from './debitorkontrolle-form.component';
import { Debitor } from '../../models/debitor.model';
import { Einheit, EinheitTyp } from '../../models/einheit.model';
import { Mieter } from '../../models/mieter.model';
import { TranslationService } from '../../services/translation.service';

describe('DebitorkontrolleFormComponent', () => {
  let component: DebitorkontrolleFormComponent;
  let fixture: ComponentFixture<DebitorkontrolleFormComponent>;

  const mockTranslationService = {
    translate: (key: string) => key
  };

  const mockEinheiten: Einheit[] = [
    { id: 1, name: 'EG links', typ: EinheitTyp.CONSUMER },
    { id: 2, name: 'OG rechts', typ: EinheitTyp.CONSUMER }
  ];

  const mockMieter: Mieter[] = [
    { id: 10, name: 'Max Muster', strasse: 'Musterstr. 1', plz: '8000', ort: 'Zürich', mietbeginn: '2024-01-01', einheitId: 1 },
    { id: 11, name: 'Anna Test', strasse: 'Testweg 2', plz: '3000', ort: 'Bern', mietbeginn: '2024-06-01', einheitId: 2 }
  ];

  const validDebitor: Debitor = {
    id: 1,
    mieterId: 10,
    betrag: 123.45,
    datumVon: '2025-01-01',
    datumBis: '2025-03-31',
    zahldatum: undefined
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DebitorkontrolleFormComponent],
      providers: [
        { provide: TranslationService, useValue: mockTranslationService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DebitorkontrolleFormComponent);
    component = fixture.componentInstance;
    component.einheiten = mockEinheiten;
    component.mieter = mockMieter;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should initialize with default empty form when no debitor input', () => {
      expect(component.formData.mieterId).toBe(0);
      expect(component.formData.betrag).toBe(0);
    });

    it('should set datumVon and datumBis to today by default', () => {
      const today = new Date().toISOString().split('T')[0];
      expect(component.formData.datumVon).toBe(today);
      expect(component.formData.datumBis).toBe(today);
    });

    it('should populate form data when debitor input is provided', () => {
      component.debitor = validDebitor;
      component.ngOnInit();

      expect(component.formData.mieterId).toBe(10);
      expect(component.formData.betrag).toBe(123.45);
      expect(component.formData.datumVon).toBe('2025-01-01');
      expect(component.formData.datumBis).toBe('2025-03-31');
    });

    it('should create a copy of the input debitor, not a reference', () => {
      component.debitor = validDebitor;
      component.ngOnInit();

      expect(component.formData).not.toBe(validDebitor);
    });

    it('should update selectedEinheitName when debitor is provided', () => {
      component.debitor = validDebitor;
      component.ngOnInit();

      expect(component.selectedEinheitName).toBe('EG links');
    });
  });

  describe('isFormValid', () => {
    it('should return false when mieterId is 0', () => {
      component.formData = { mieterId: 0, betrag: 100, datumVon: '2025-01-01', datumBis: '2025-03-31' };
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when betrag is 0', () => {
      component.formData = { mieterId: 10, betrag: 0, datumVon: '2025-01-01', datumBis: '2025-03-31' };
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when betrag is negative', () => {
      component.formData = { mieterId: 10, betrag: -5, datumVon: '2025-01-01', datumBis: '2025-03-31' };
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when datumVon is empty', () => {
      component.formData = { mieterId: 10, betrag: 100, datumVon: '', datumBis: '2025-03-31' };
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when datumBis is empty', () => {
      component.formData = { mieterId: 10, betrag: 100, datumVon: '2025-01-01', datumBis: '' };
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when datumVon is after datumBis', () => {
      component.formData = { mieterId: 10, betrag: 100, datumVon: '2025-03-31', datumBis: '2025-01-01' };
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when zahldatum is before datumBis', () => {
      component.formData = { mieterId: 10, betrag: 100, datumVon: '2025-01-01', datumBis: '2025-03-31', zahldatum: '2025-02-01' };
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return true when all required fields are valid', () => {
      component.formData = { mieterId: 10, betrag: 100, datumVon: '2025-01-01', datumBis: '2025-03-31' };
      expect(component.isFormValid()).toBeTrue();
    });

    it('should return true when zahldatum equals datumBis', () => {
      component.formData = { mieterId: 10, betrag: 100, datumVon: '2025-01-01', datumBis: '2025-03-31', zahldatum: '2025-03-31' };
      expect(component.isFormValid()).toBeTrue();
    });

    it('should return true when zahldatum is after datumBis', () => {
      component.formData = { mieterId: 10, betrag: 100, datumVon: '2025-01-01', datumBis: '2025-03-31', zahldatum: '2025-04-15' };
      expect(component.isFormValid()).toBeTrue();
    });

    it('should return true when zahldatum is not set', () => {
      component.formData = { mieterId: 10, betrag: 100, datumVon: '2025-01-01', datumBis: '2025-03-31', zahldatum: undefined };
      expect(component.isFormValid()).toBeTrue();
    });
  });

  describe('isZahldatumValid', () => {
    it('should return true when zahldatum is not set', () => {
      component.formData.datumBis = '2025-03-31';
      component.formData.zahldatum = undefined;
      expect(component.isZahldatumValid()).toBeTrue();
    });

    it('should return true when zahldatum equals datumBis', () => {
      component.formData.datumBis = '2025-03-31';
      component.formData.zahldatum = '2025-03-31';
      expect(component.isZahldatumValid()).toBeTrue();
    });

    it('should return true when zahldatum is after datumBis', () => {
      component.formData.datumBis = '2025-03-31';
      component.formData.zahldatum = '2025-04-10';
      expect(component.isZahldatumValid()).toBeTrue();
    });

    it('should return false when zahldatum is before datumBis', () => {
      component.formData.datumBis = '2025-03-31';
      component.formData.zahldatum = '2025-02-01';
      expect(component.isZahldatumValid()).toBeFalse();
    });
  });

  describe('onMieterChange', () => {
    it('should update selectedEinheitName when valid mieter selected', () => {
      component.formData.mieterId = 10;
      component.onMieterChange();
      expect(component.selectedEinheitName).toBe('EG links');
    });

    it('should clear selectedEinheitName for unknown mieter', () => {
      component.formData.mieterId = 999;
      component.onMieterChange();
      expect(component.selectedEinheitName).toBe('');
    });
  });

  describe('getMieterDisplayName', () => {
    it('should return name with einheit in parentheses', () => {
      const result = component.getMieterDisplayName(mockMieter[0]);
      expect(result).toBe('Max Muster (EG links)');
    });

    it('should return only name when einheit not found', () => {
      const mieterWithoutEinheit: Mieter = { id: 99, name: 'Unbekannt', mietbeginn: '2024-01-01', einheitId: 999 };
      const result = component.getMieterDisplayName(mieterWithoutEinheit);
      expect(result).toBe('Unbekannt');
    });
  });

  describe('events', () => {
    it('should emit save event with form data on valid submit', () => {
      const saveSpy = spyOn(component.save, 'emit');
      component.formData = { mieterId: 10, betrag: 100, datumVon: '2025-01-01', datumBis: '2025-03-31' };

      component.onSubmit();

      expect(saveSpy).toHaveBeenCalled();
    });

    it('should not emit save event on invalid submit', () => {
      const saveSpy = spyOn(component.save, 'emit');
      component.formData = { mieterId: 0, betrag: 0, datumVon: '', datumBis: '' };

      component.onSubmit();

      expect(saveSpy).not.toHaveBeenCalled();
    });

    it('should convert mieterId to Number on save', () => {
      const saveSpy = spyOn(component.save, 'emit');
      component.formData = { mieterId: '10' as any, betrag: 100, datumVon: '2025-01-01', datumBis: '2025-03-31' };

      component.onSubmit();

      const emitted = saveSpy.calls.first().args[0] as Debitor;
      expect(typeof emitted.mieterId).toBe('number');
      expect(emitted.mieterId).toBe(10);
    });

    it('should convert betrag to Number on save', () => {
      const saveSpy = spyOn(component.save, 'emit');
      component.formData = { mieterId: 10, betrag: '99.90' as any, datumVon: '2025-01-01', datumBis: '2025-03-31' };

      component.onSubmit();

      const emitted = saveSpy.calls.first().args[0] as Debitor;
      expect(typeof emitted.betrag).toBe('number');
      expect(emitted.betrag).toBe(99.90);
    });

    it('should convert empty zahldatum to undefined on save', () => {
      const saveSpy = spyOn(component.save, 'emit');
      component.formData = { mieterId: 10, betrag: 100, datumVon: '2025-01-01', datumBis: '2025-03-31', zahldatum: '' };

      component.onSubmit();

      const emitted = saveSpy.calls.first().args[0] as Debitor;
      expect(emitted.zahldatum).toBeUndefined();
    });

    it('should emit cancel event', () => {
      const cancelSpy = spyOn(component.cancel, 'emit');
      component.onCancel();
      expect(cancelSpy).toHaveBeenCalled();
    });
  });
});
