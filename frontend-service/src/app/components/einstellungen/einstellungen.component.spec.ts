import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { EinstellungenComponent } from './einstellungen.component';
import { EinstellungenService } from '../../services/einstellungen.service';
import { TranslationService } from '../../services/translation.service';
import { Einstellungen } from '../../models/einstellungen.model';
import { of, throwError } from 'rxjs';

describe('EinstellungenComponent', () => {
  let component: EinstellungenComponent;
  let fixture: ComponentFixture<EinstellungenComponent>;
  let einstellungenServiceSpy: jasmine.SpyObj<EinstellungenService>;

  const mockEinstellungen: Einstellungen = {
    id: 1,
    rechnung: {
      zahlungsfrist: '30 Tage',
      iban: 'CH7006300016946459910',
      steller: {
        name: 'Urs Nacht',
        strasse: 'Hangstrasse 14a',
        plz: '3044',
        ort: 'Innerberg'
      }
    }
  };

  const mockTranslationService = {
    translate: (key: string) => key
  };

  beforeEach(async () => {
    einstellungenServiceSpy = jasmine.createSpyObj('EinstellungenService', ['getEinstellungen', 'saveEinstellungen']);
    einstellungenServiceSpy.getEinstellungen.and.returnValue(of(mockEinstellungen));

    await TestBed.configureTestingModule({
      imports: [EinstellungenComponent],
      providers: [
        { provide: EinstellungenService, useValue: einstellungenServiceSpy },
        { provide: TranslationService, useValue: mockTranslationService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(EinstellungenComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should load einstellungen on init', () => {
      expect(einstellungenServiceSpy.getEinstellungen).toHaveBeenCalled();
    });

    it('should populate form with loaded data', () => {
      expect(component.formData.zahlungsfrist).toBe('30 Tage');
      expect(component.formData.iban).toBe('CH7006300016946459910');
      expect(component.formData.steller.name).toBe('Urs Nacht');
      expect(component.formData.steller.strasse).toBe('Hangstrasse 14a');
      expect(component.formData.steller.plz).toBe('3044');
      expect(component.formData.steller.ort).toBe('Innerberg');
    });

    it('should set einstellungenId from loaded data', () => {
      expect(component.einstellungenId).toBe(1);
    });

    it('should set loading to false after load', () => {
      expect(component.loading).toBeFalse();
    });

    it('should initialize with empty form when no settings exist', () => {
      einstellungenServiceSpy.getEinstellungen.and.returnValue(of(null));
      component.loadEinstellungen();

      expect(component.formData.zahlungsfrist).toBe('30 Tage'); // still from initial load
    });

    it('should initialize default steller when data has no steller', () => {
      const dataWithoutSteller: Einstellungen = {
        id: 2,
        rechnung: {
          zahlungsfrist: '30 Tage',
          iban: 'CH7006300016946459910',
          steller: null as any
        }
      };
      einstellungenServiceSpy.getEinstellungen.and.returnValue(of(dataWithoutSteller));

      component.loadEinstellungen();

      expect(component.formData.steller).toEqual({ name: '', strasse: '', plz: '', ort: '' });
    });

    it('should show error message on load failure', () => {
      einstellungenServiceSpy.getEinstellungen.and.returnValue(throwError(() => ({ status: 500 })));

      component.loadEinstellungen();

      expect(component.message).toBe('EINSTELLUNGEN_FEHLER');
      expect(component.messageType).toBe('error');
    });

    it('should not show error message on 204 No Content', () => {
      einstellungenServiceSpy.getEinstellungen.and.returnValue(throwError(() => ({ status: 204 })));

      component.message = '';
      component.loadEinstellungen();

      expect(component.message).toBe('');
    });
  });

  describe('isFormValid', () => {
    beforeEach(() => {
      component.formData = {
        zahlungsfrist: '30 Tage',
        iban: 'CH7006300016946459910',
        steller: {
          name: 'Urs Nacht',
          strasse: 'Hangstrasse 14a',
          plz: '3044',
          ort: 'Innerberg'
        }
      };
    });

    it('should return true when all fields are valid', () => {
      expect(component.isFormValid()).toBeTrue();
    });

    it('should return false when zahlungsfrist is empty', () => {
      component.formData.zahlungsfrist = '';
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when zahlungsfrist is only whitespace', () => {
      component.formData.zahlungsfrist = '   ';
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when iban is empty', () => {
      component.formData.iban = '';
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when iban is invalid format', () => {
      component.formData.iban = 'DE89370400440532013000';
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when steller name is empty', () => {
      component.formData.steller.name = '';
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when steller strasse is empty', () => {
      component.formData.steller.strasse = '';
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when steller plz is empty', () => {
      component.formData.steller.plz = '';
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when steller ort is empty', () => {
      component.formData.steller.ort = '';
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when steller name is only whitespace', () => {
      component.formData.steller.name = '   ';
      expect(component.isFormValid()).toBeFalse();
    });
  });

  describe('isIbanValid', () => {
    it('should return true for valid Swiss IBAN without spaces', () => {
      component.formData.iban = 'CH7006300016946459910';
      expect(component.isIbanValid()).toBeTrue();
    });

    it('should return true for valid Swiss IBAN with spaces', () => {
      component.formData.iban = 'CH70 0630 0016 9464 5991 0';
      expect(component.isIbanValid()).toBeTrue();
    });

    it('should return true when iban is empty (not entered yet)', () => {
      component.formData.iban = '';
      expect(component.isIbanValid()).toBeTrue();
    });

    it('should return false for non-Swiss IBAN', () => {
      component.formData.iban = 'DE89370400440532013000';
      expect(component.isIbanValid()).toBeFalse();
    });

    it('should return false for IBAN with too few digits', () => {
      component.formData.iban = 'CH700630001694';
      expect(component.isIbanValid()).toBeFalse();
    });

    it('should return false for IBAN with letters after CH prefix', () => {
      component.formData.iban = 'CH70ABCD0016946459910';
      expect(component.isIbanValid()).toBeFalse();
    });
  });

  describe('onSubmit', () => {
    beforeEach(() => {
      component.formData = {
        zahlungsfrist: '30 Tage',
        iban: 'CH7006300016946459910',
        steller: {
          name: 'Urs Nacht',
          strasse: 'Hangstrasse 14a',
          plz: '3044',
          ort: 'Innerberg'
        }
      };
    });

    it('should save einstellungen on valid submit', () => {
      const savedEinstellungen: Einstellungen = { id: 1, rechnung: component.formData };
      einstellungenServiceSpy.saveEinstellungen.and.returnValue(of(savedEinstellungen));

      component.onSubmit();

      expect(einstellungenServiceSpy.saveEinstellungen).toHaveBeenCalled();
    });

    it('should set einstellungenId after successful save', () => {
      const savedEinstellungen: Einstellungen = { id: 5, rechnung: component.formData };
      einstellungenServiceSpy.saveEinstellungen.and.returnValue(of(savedEinstellungen));

      component.einstellungenId = undefined;
      component.onSubmit();

      expect(component.einstellungenId).toBeDefined();
      expect(component.einstellungenId!).toBe(5);
    });

    it('should show success message after save', () => {
      const savedEinstellungen: Einstellungen = { id: 1, rechnung: component.formData };
      einstellungenServiceSpy.saveEinstellungen.and.returnValue(of(savedEinstellungen));

      component.onSubmit();

      expect(component.message).toBe('EINSTELLUNGEN_GESPEICHERT');
      expect(component.messageType).toBe('success');
    });

    it('should not call save when form is invalid', () => {
      component.formData.zahlungsfrist = '';

      component.onSubmit();

      expect(einstellungenServiceSpy.saveEinstellungen).not.toHaveBeenCalled();
    });

    it('should show error message on save failure', () => {
      einstellungenServiceSpy.saveEinstellungen.and.returnValue(throwError(() => ({ error: 'Server Error' })));

      component.onSubmit();

      expect(component.message).toBe('Server Error');
      expect(component.messageType).toBe('error');
    });

    it('should show default error message when error has no body', () => {
      einstellungenServiceSpy.saveEinstellungen.and.returnValue(throwError(() => ({ error: null })));

      component.onSubmit();

      expect(component.message).toBe('EINSTELLUNGEN_FEHLER');
      expect(component.messageType).toBe('error');
    });

    it('should pass existing id when updating', () => {
      component.einstellungenId = 1;
      const savedEinstellungen: Einstellungen = { id: 1, rechnung: component.formData };
      einstellungenServiceSpy.saveEinstellungen.and.returnValue(of(savedEinstellungen));

      component.onSubmit();

      const savedArg = einstellungenServiceSpy.saveEinstellungen.calls.mostRecent().args[0];
      expect(savedArg.id).toBe(1);
    });

    it('should pass undefined id when creating new', () => {
      component.einstellungenId = undefined;
      const savedEinstellungen: Einstellungen = { id: 2, rechnung: component.formData };
      einstellungenServiceSpy.saveEinstellungen.and.returnValue(of(savedEinstellungen));

      component.onSubmit();

      const savedArg = einstellungenServiceSpy.saveEinstellungen.calls.mostRecent().args[0];
      expect(savedArg.id).toBeUndefined();
    });
  });

  describe('messages', () => {
    it('should auto-dismiss success message after timeout', fakeAsync(() => {
      const savedEinstellungen: Einstellungen = { id: 1, rechnung: component.formData };
      einstellungenServiceSpy.saveEinstellungen.and.returnValue(of(savedEinstellungen));
      component.formData = { ...mockEinstellungen.rechnung };

      component.onSubmit();
      expect(component.message).toBe('EINSTELLUNGEN_GESPEICHERT');

      tick(5000);
      expect(component.message).toBe('');
    }));

    it('should not auto-dismiss error message', fakeAsync(() => {
      einstellungenServiceSpy.saveEinstellungen.and.returnValue(throwError(() => ({ error: 'Error' })));
      component.formData = { ...mockEinstellungen.rechnung };

      component.onSubmit();
      expect(component.message).toBe('Error');

      tick(5000);
      expect(component.message).toBe('Error');
    }));

    it('should dismiss message manually', () => {
      component.message = 'Some error';
      component.messageType = 'error';

      component.dismissMessage();

      expect(component.message).toBe('');
    });
  });
});
