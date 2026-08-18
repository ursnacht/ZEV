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
        messpunkt: 'MP-001'
      };

      component.einheit = inputEinheit;
      component.ngOnInit();

      expect(component.formData.id).toBe(1);
      expect(component.formData.name).toBe('Test Einheit');
      expect(component.formData.typ).toBe(EinheitTyp.PRODUCER);
      expect(component.formData.messpunkt).toBe('MP-001');
    });

    it('should have five einheit type options', () => {
      expect(component.einheitTypOptions.length).toBe(5);
      expect(component.einheitTypOptions[0].value).toBe(EinheitTyp.PRODUCER);
      expect(component.einheitTypOptions[1].value).toBe(EinheitTyp.CONSUMER);
      expect(component.einheitTypOptions[2].value).toBe(EinheitTyp.BEZUG);
      expect(component.einheitTypOptions[3].value).toBe(EinheitTyp.RUECKLIEFERUNG);
      expect(component.einheitTypOptions[4].value).toBe(EinheitTyp.LADESTATION);
    });

    it('should label the ladestation option with its own translation key', () => {
      const option = component.einheitTypOptions.find(o => o.value === EinheitTyp.LADESTATION);
      expect(option?.label).toBe('TYP_LADESTATION');
    });

    it('should expose the EinheitTyp enum for the template hint', () => {
      // Der Messpunkt-Hinweis wechselt im Template auf den RFID-Text, sobald der Typ
      // LADESTATION gewaehlt ist (Specs/Ladestationen.md FR-3).
      expect(component.EinheitTyp.LADESTATION).toBe(EinheitTyp.LADESTATION);
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
      const saveSpy = vi.spyOn(component.save, 'emit');
      component.formData = {
        name: 'Test Einheit',
        typ: EinheitTyp.CONSUMER,
        messpunkt: 'MP-001'
      };

      component.onSubmit();

      expect(saveSpy).toHaveBeenCalledWith(component.formData);
    });

    it('should not emit save event when name is empty', () => {
      const saveSpy = vi.spyOn(component.save, 'emit');
      component.formData = {
        name: '',
        typ: EinheitTyp.CONSUMER
      };

      component.onSubmit();

      expect(saveSpy).not.toHaveBeenCalled();
    });

    it('should not emit save event when name is only whitespace', () => {
      const saveSpy = vi.spyOn(component.save, 'emit');
      component.formData = {
        name: '   ',
        typ: EinheitTyp.CONSUMER
      };

      component.onSubmit();

      expect(saveSpy).not.toHaveBeenCalled();
    });

    it('should emit cancel event', () => {
      const cancelSpy = vi.spyOn(component.cancel, 'emit');

      component.onCancel();

      expect(cancelSpy).toHaveBeenCalled();
    });
  });

  describe('einheit types', () => {
    it('should allow CONSUMER type', () => {
      component.formData = {
        name: 'Verbraucher 1',
        typ: EinheitTyp.CONSUMER,
        messpunkt: 'MP-100'
      };

      const saveSpy = vi.spyOn(component.save, 'emit');
      component.onSubmit();

      expect(saveSpy).toHaveBeenCalledWith(expect.objectContaining({
        typ: EinheitTyp.CONSUMER
      }));
    });

    it('should allow PRODUCER type', () => {
      component.formData = {
        name: 'Solaranlage 1',
        typ: EinheitTyp.PRODUCER,
        messpunkt: 'MP-200'
      };

      const saveSpy = vi.spyOn(component.save, 'emit');
      component.onSubmit();

      expect(saveSpy).toHaveBeenCalledWith(expect.objectContaining({
        typ: EinheitTyp.PRODUCER
      }));
    });

    it('should allow LADESTATION type', () => {
      component.formData = {
        name: 'Ladestation 1',
        typ: EinheitTyp.LADESTATION,
        messpunkt: 'RFID-04711'
      };

      const saveSpy = vi.spyOn(component.save, 'emit');
      component.onSubmit();

      expect(saveSpy).toHaveBeenCalledWith(expect.objectContaining({
        typ: EinheitTyp.LADESTATION
      }));
    });

    it('should emit the RFID in the messpunkt field for LADESTATION', () => {
      // Die RFID steht im bestehenden Feld messpunkt - kein eigenes Attribut
      // (Specs/Ladestationen.md FR-2).
      component.formData = {
        name: 'Ladestation 1',
        typ: EinheitTyp.LADESTATION,
        messpunkt: 'RFID-04711'
      };

      const saveSpy = vi.spyOn(component.save, 'emit');
      component.onSubmit();

      expect(saveSpy).toHaveBeenCalledWith(expect.objectContaining({
        messpunkt: 'RFID-04711'
      }));
    });

    it('should allow a LADESTATION without RFID (server rejects it, not the form)', () => {
      component.formData = {
        name: 'Ladestation ohne RFID',
        typ: EinheitTyp.LADESTATION
      };

      const saveSpy = vi.spyOn(component.save, 'emit');
      component.onSubmit();

      expect(saveSpy).toHaveBeenCalled();
    });

    it('should keep the RFID when editing an existing LADESTATION', () => {
      component.einheit = {
        id: 9,
        name: 'Ladestation 1',
        typ: EinheitTyp.LADESTATION,
        messpunkt: 'RFID-04711'
      };
      component.ngOnInit();

      expect(component.formData.typ).toBe(EinheitTyp.LADESTATION);
      expect(component.formData.messpunkt).toBe('RFID-04711');
    });

    it('should allow messpunkt for PRODUCER type', () => {
      component.formData = {
        name: 'Solaranlage 1',
        typ: EinheitTyp.PRODUCER,
        messpunkt: 'MP-SOLAR-001'
      };

      const saveSpy = vi.spyOn(component.save, 'emit');
      component.onSubmit();

      expect(saveSpy).toHaveBeenCalledWith(expect.objectContaining({
        messpunkt: 'MP-SOLAR-001'
      }));
    });
  });

  describe('optional fields', () => {
    it('should allow submission without messpunkt', () => {
      const saveSpy = vi.spyOn(component.save, 'emit');
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
        messpunkt: 'MP-3B'
      };

      component.einheit = inputEinheit;
      component.ngOnInit();

      expect(component.formData.messpunkt).toBe('MP-3B');
    });
  });
});
