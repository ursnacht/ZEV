import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TarifFormComponent } from './tarif-form.component';
import { Tarif, TarifTyp } from '../../models/tarif.model';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { TranslationService } from '../../services/translation.service';

describe('TarifFormComponent', () => {
  let component: TarifFormComponent;
  let fixture: ComponentFixture<TarifFormComponent>;

  const mockTranslationService = {
    translate: (key: string) => key
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TarifFormComponent],
      providers: [
        { provide: TranslationService, useValue: mockTranslationService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(TarifFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should initialize with default form data when no tarif input', () => {
      expect(component.formData.bezeichnung).toBe('');
      expect(component.formData.tariftyp).toBe(TarifTyp.ZEV);
      expect(component.formData.preis).toBe(0);
    });

    it('should set default dates to current year', () => {
      const currentYear = new Date().getFullYear();
      expect(component.formData.gueltigVon).toBe(`${currentYear}-01-01`);
      expect(component.formData.gueltigBis).toBe(`${currentYear}-12-31`);
    });

    it('should populate form with tarif data when input is provided', () => {
      const inputTarif: Tarif = {
        id: 1,
        bezeichnung: 'Test Tarif',
        tariftyp: TarifTyp.VNB,
        preis: 0.35,
        gueltigVon: '2024-06-01',
        gueltigBis: '2024-12-31'
      };

      component.tarif = inputTarif;
      component.ngOnInit();

      expect(component.formData.bezeichnung).toBe('Test Tarif');
      expect(component.formData.tariftyp).toBe(TarifTyp.VNB);
      expect(component.formData.preis).toBe(0.35);
      expect(component.formData.gueltigVon).toBe('2024-06-01');
      expect(component.formData.gueltigBis).toBe('2024-12-31');
    });

    it('should have two tarif type options', () => {
      expect(component.tarifTypOptions.length).toBe(2);
      expect(component.tarifTypOptions[0].value).toBe(TarifTyp.ZEV);
      expect(component.tarifTypOptions[1].value).toBe(TarifTyp.VNB);
    });
  });

  describe('isFormValid', () => {
    it('should return false when bezeichnung is empty', () => {
      component.formData = {
        bezeichnung: '',
        tariftyp: TarifTyp.ZEV,
        preis: 0.20,
        gueltigVon: '2024-01-01',
        gueltigBis: '2024-12-31'
      };
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when bezeichnung is only whitespace', () => {
      component.formData = {
        bezeichnung: '   ',
        tariftyp: TarifTyp.ZEV,
        preis: 0.20,
        gueltigVon: '2024-01-01',
        gueltigBis: '2024-12-31'
      };
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when preis is zero', () => {
      component.formData = {
        bezeichnung: 'Test',
        tariftyp: TarifTyp.ZEV,
        preis: 0,
        gueltigVon: '2024-01-01',
        gueltigBis: '2024-12-31'
      };
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when preis is negative', () => {
      component.formData = {
        bezeichnung: 'Test',
        tariftyp: TarifTyp.ZEV,
        preis: -0.10,
        gueltigVon: '2024-01-01',
        gueltigBis: '2024-12-31'
      };
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when gueltigVon is empty', () => {
      component.formData = {
        bezeichnung: 'Test',
        tariftyp: TarifTyp.ZEV,
        preis: 0.20,
        gueltigVon: '',
        gueltigBis: '2024-12-31'
      };
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when gueltigBis is empty', () => {
      component.formData = {
        bezeichnung: 'Test',
        tariftyp: TarifTyp.ZEV,
        preis: 0.20,
        gueltigVon: '2024-01-01',
        gueltigBis: ''
      };
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when gueltigVon is after gueltigBis', () => {
      component.formData = {
        bezeichnung: 'Test',
        tariftyp: TarifTyp.ZEV,
        preis: 0.20,
        gueltigVon: '2024-12-31',
        gueltigBis: '2024-01-01'
      };
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return true when all fields are valid', () => {
      component.formData = {
        bezeichnung: 'Test Tarif',
        tariftyp: TarifTyp.ZEV,
        preis: 0.195,
        gueltigVon: '2024-01-01',
        gueltigBis: '2024-12-31'
      };
      expect(component.isFormValid()).toBeTrue();
    });

    it('should return true when gueltigVon equals gueltigBis', () => {
      component.formData = {
        bezeichnung: 'Test',
        tariftyp: TarifTyp.ZEV,
        preis: 0.20,
        gueltigVon: '2024-06-15',
        gueltigBis: '2024-06-15'
      };
      expect(component.isFormValid()).toBeTrue();
    });
  });

  describe('isDateRangeValid', () => {
    it('should return true when dates are not entered yet', () => {
      component.formData.gueltigVon = '';
      component.formData.gueltigBis = '';
      expect(component.isDateRangeValid()).toBeTrue();
    });

    it('should return true when only gueltigVon is empty', () => {
      component.formData.gueltigVon = '';
      component.formData.gueltigBis = '2024-12-31';
      expect(component.isDateRangeValid()).toBeTrue();
    });

    it('should return true when gueltigVon is before gueltigBis', () => {
      component.formData.gueltigVon = '2024-01-01';
      component.formData.gueltigBis = '2024-12-31';
      expect(component.isDateRangeValid()).toBeTrue();
    });

    it('should return false when gueltigVon is after gueltigBis', () => {
      component.formData.gueltigVon = '2024-12-31';
      component.formData.gueltigBis = '2024-01-01';
      expect(component.isDateRangeValid()).toBeFalse();
    });
  });

  describe('events', () => {
    it('should emit save event with form data on valid submit', () => {
      const saveSpy = spyOn(component.save, 'emit');
      component.formData = {
        bezeichnung: 'Test Tarif',
        tariftyp: TarifTyp.ZEV,
        preis: 0.195,
        gueltigVon: '2024-01-01',
        gueltigBis: '2024-12-31'
      };

      component.onSubmit();

      expect(saveSpy).toHaveBeenCalledWith(component.formData);
    });

    it('should not emit save event on invalid submit', () => {
      const saveSpy = spyOn(component.save, 'emit');
      component.formData = {
        bezeichnung: '',
        tariftyp: TarifTyp.ZEV,
        preis: 0,
        gueltigVon: '',
        gueltigBis: ''
      };

      component.onSubmit();

      expect(saveSpy).not.toHaveBeenCalled();
    });

    it('should emit cancel event', () => {
      const cancelSpy = spyOn(component.cancel, 'emit');

      component.onCancel();

      expect(cancelSpy).toHaveBeenCalled();
    });
  });
});
