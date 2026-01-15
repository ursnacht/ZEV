import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EinheitFormComponent } from './einheit-form.component';
import { Einheit, EinheitTyp } from '../../models/einheit.model';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { TranslationService } from '../../services/translation.service';

describe('EinheitFormComponent', () => {
  let component: EinheitFormComponent;
  let fixture: ComponentFixture<EinheitFormComponent>;

  const mockTranslationService = {
    translate: (key: string) => key
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EinheitFormComponent],
      providers: [
        { provide: TranslationService, useValue: mockTranslationService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(EinheitFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should initialize with default form data when no einheit input', () => {
      expect(component.formData.name).toBe('');
      expect(component.formData.typ).toBe(EinheitTyp.CONSUMER);
    });

    it('should populate form with einheit data when input is provided', () => {
      const inputEinheit: Einheit = {
        id: 1,
        name: 'Test Einheit',
        typ: EinheitTyp.PRODUCER,
        mietername: 'Max Muster',
        messpunkt: 'MP-001'
      };

      component.einheit = inputEinheit;
      component.ngOnInit();

      expect(component.formData.id).toBe(1);
      expect(component.formData.name).toBe('Test Einheit');
      expect(component.formData.typ).toBe(EinheitTyp.PRODUCER);
      expect(component.formData.mietername).toBe('Max Muster');
      expect(component.formData.messpunkt).toBe('MP-001');
    });

    it('should have two einheit type options', () => {
      expect(component.einheitTypOptions.length).toBe(2);
      expect(component.einheitTypOptions[0].value).toBe(EinheitTyp.PRODUCER);
      expect(component.einheitTypOptions[1].value).toBe(EinheitTyp.CONSUMER);
    });
  });

  describe('form validation', () => {
    it('should be invalid when name is empty', () => {
      component.formData = {
        name: '',
        typ: EinheitTyp.CONSUMER
      };
      expect(component.formData.name.trim()).toBeFalsy();
    });

    it('should be invalid when name is only whitespace', () => {
      component.formData = {
        name: '   ',
        typ: EinheitTyp.CONSUMER
      };
      expect(component.formData.name.trim()).toBeFalsy();
    });

    it('should be valid when name is provided', () => {
      component.formData = {
        name: 'Test Einheit',
        typ: EinheitTyp.CONSUMER
      };
      expect(component.formData.name.trim()).toBeTruthy();
    });
  });

  describe('events', () => {
    it('should emit save event with form data on valid submit', () => {
      const saveSpy = spyOn(component.save, 'emit');
      component.formData = {
        name: 'Test Einheit',
        typ: EinheitTyp.CONSUMER,
        mietername: 'Max Muster',
        messpunkt: 'MP-001'
      };

      component.onSubmit();

      expect(saveSpy).toHaveBeenCalledWith(component.formData);
    });

    it('should not emit save event when name is empty', () => {
      const saveSpy = spyOn(component.save, 'emit');
      component.formData = {
        name: '',
        typ: EinheitTyp.CONSUMER
      };

      component.onSubmit();

      expect(saveSpy).not.toHaveBeenCalled();
    });

    it('should not emit save event when name is only whitespace', () => {
      const saveSpy = spyOn(component.save, 'emit');
      component.formData = {
        name: '   ',
        typ: EinheitTyp.CONSUMER
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

  describe('einheit types', () => {
    it('should allow CONSUMER type', () => {
      component.formData = {
        name: 'Verbraucher 1',
        typ: EinheitTyp.CONSUMER,
        mietername: 'Hans Müller',
        messpunkt: 'MP-100'
      };

      const saveSpy = spyOn(component.save, 'emit');
      component.onSubmit();

      expect(saveSpy).toHaveBeenCalledWith(jasmine.objectContaining({
        typ: EinheitTyp.CONSUMER
      }));
    });

    it('should allow PRODUCER type', () => {
      component.formData = {
        name: 'Solaranlage 1',
        typ: EinheitTyp.PRODUCER,
        messpunkt: 'MP-200'
      };

      const saveSpy = spyOn(component.save, 'emit');
      component.onSubmit();

      expect(saveSpy).toHaveBeenCalledWith(jasmine.objectContaining({
        typ: EinheitTyp.PRODUCER
      }));
    });

    it('should allow messpunkt for PRODUCER type', () => {
      component.formData = {
        name: 'Solaranlage 1',
        typ: EinheitTyp.PRODUCER,
        messpunkt: 'MP-SOLAR-001'
      };

      const saveSpy = spyOn(component.save, 'emit');
      component.onSubmit();

      expect(saveSpy).toHaveBeenCalledWith(jasmine.objectContaining({
        messpunkt: 'MP-SOLAR-001'
      }));
    });
  });

  describe('optional fields', () => {
    it('should allow submission without mietername', () => {
      const saveSpy = spyOn(component.save, 'emit');
      component.formData = {
        name: 'Test Einheit',
        typ: EinheitTyp.CONSUMER
      };

      component.onSubmit();

      expect(saveSpy).toHaveBeenCalled();
    });

    it('should allow submission without messpunkt', () => {
      const saveSpy = spyOn(component.save, 'emit');
      component.formData = {
        name: 'Test Einheit',
        typ: EinheitTyp.CONSUMER
      };

      component.onSubmit();

      expect(saveSpy).toHaveBeenCalled();
    });

    it('should preserve optional fields when editing existing einheit', () => {
      const inputEinheit: Einheit = {
        id: 5,
        name: 'Wohnung 3B',
        typ: EinheitTyp.CONSUMER,
        mietername: 'Familie Schmidt',
        messpunkt: 'MP-3B'
      };

      component.einheit = inputEinheit;
      component.ngOnInit();

      expect(component.formData.mietername).toBe('Familie Schmidt');
      expect(component.formData.messpunkt).toBe('MP-3B');
    });
  });
});
