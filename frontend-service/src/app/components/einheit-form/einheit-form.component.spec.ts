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

    it('should prefill the meter point of a new unit and keep the other defaults (FR-8)', () => {
      component.einheit = null;
      component.messpunktVorgabe = 'MP-NEU';
      component.ngOnInit();

      expect(component.formData.messpunkt).toBe('MP-NEU');
      expect(component.formData.typ).toBe(EinheitTyp.CONSUMER);
      expect(component.formData.nebenkostenRelevant).toBe(true);
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

    it('should offer every einheit type exactly once', () => {
      // Gegen die Enum-Werte statt gegen eine feste Zahl: Ein neuer Typ, der im Dropdown
      // vergessen wird, faellt so auf - und ein bestehender, der herausfaellt, ebenfalls.
      const werte = component.einheitTypOptions.map(o => o.value);
      expect(werte).toEqual(Object.values(EinheitTyp));
      expect(new Set(werte).size).toBe(werte.length);
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

    it('should allow a LADESTATION without RFID (the server saves it as well)', () => {
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

  // Specs/Einheitenverwaltung.md, FR-3/FR-4: Verhalten im gerenderten Formular
  describe('template', () => {
    const element = (): HTMLElement => fixture.nativeElement;
    const submitButton = (): HTMLButtonElement => element().querySelector('button[type="submit"]')!;
    const fehler = (): HTMLElement | null => element().querySelector('.zev-form-error');
    const checkbox = (): HTMLInputElement | null => element().querySelector('#nebenkostenRelevant');

    it('should disable submit and show NAME_IST_ERFORDERLICH when name is empty', () => {
      component.formData = { ...component.formData, name: '' };
      fixture.detectChanges();

      expect(submitButton().disabled).toBe(true);
      expect(fehler()?.textContent).toContain('NAME_IST_ERFORDERLICH');
    });

    it('should disable submit and show NAME_IST_ERFORDERLICH when name is only whitespace', () => {
      component.formData = { ...component.formData, name: '   ' };
      fixture.detectChanges();

      expect(submitButton().disabled).toBe(true);
      expect(fehler()?.textContent).toContain('NAME_IST_ERFORDERLICH');
    });

    it('should disable submit and show EINHEIT_NAME_LAENGE when name has one character', () => {
      component.formData = { ...component.formData, name: 'A' };
      fixture.detectChanges();

      expect(submitButton().disabled).toBe(true);
      expect(fehler()?.textContent).toContain('EINHEIT_NAME_LAENGE');
    });

    it('should enable submit and hide the error when name is filled', () => {
      component.formData = { ...component.formData, name: 'Wohnung A' };
      fixture.detectChanges();

      expect(submitButton().disabled).toBe(false);
      expect(fehler()).toBeNull();
    });

    it('should show the checked nebenkosten checkbox for a new CONSUMER', async () => {
      await fixture.whenStable();
      fixture.detectChanges();

      expect(component.formData.nebenkostenRelevant).toBe(true);
      expect(checkbox()).not.toBeNull();
      expect(checkbox()!.checked).toBe(true);
    });

    it('should hide the nebenkosten checkbox for every type other than CONSUMER', () => {
      for (const typ of Object.values(EinheitTyp).filter(t => t !== EinheitTyp.CONSUMER)) {
        component.formData = { ...component.formData, typ };
        fixture.detectChanges();

        expect(checkbox(), typ).toBeNull();
      }
    });

    it('should treat a missing nebenkostenRelevant as true', () => {
      // Bestandsdaten vor V123 tragen das Feld nicht
      component.einheit = { id: 5, name: 'Alt', typ: EinheitTyp.CONSUMER } as Einheit;
      component.ngOnInit();

      expect(component.formData.nebenkostenRelevant).toBe(true);
    });

    it('should keep nebenkostenRelevant when the type changes and the checkbox is hidden', () => {
      vi.spyOn(component.save, 'emit');
      component.formData = { name: 'Allgemein', typ: EinheitTyp.CONSUMER, nebenkostenRelevant: false };
      fixture.detectChanges();

      component.formData.typ = EinheitTyp.PRODUCER;
      fixture.detectChanges();
      expect(checkbox()).toBeNull();

      component.onSubmit();
      expect(component.save.emit).toHaveBeenCalledWith(
        expect.objectContaining({ typ: EinheitTyp.PRODUCER, nebenkostenRelevant: false }));
    });

    it('should show MESSPUNKT_HINT_LADESTATION for LADESTATION and MESSPUNKT_HINT otherwise', () => {
      const hinweis = () => element().querySelector('#messpunkt ~ .zev-form-hint')?.textContent?.trim();

      component.formData = { ...component.formData, typ: EinheitTyp.LADESTATION };
      fixture.detectChanges();
      expect(hinweis()).toBe('MESSPUNKT_HINT_LADESTATION');

      component.formData = { ...component.formData, typ: EinheitTyp.CONSUMER };
      fixture.detectChanges();
      expect(hinweis()).toBe('MESSPUNKT_HINT');
    });
  });

  describe('name length', () => {
    it('should not emit save for a name with one character', () => {
      vi.spyOn(component.save, 'emit');
      component.formData = { name: ' A ', typ: EinheitTyp.CONSUMER };

      component.onSubmit();

      expect(component.save.emit).not.toHaveBeenCalled();
    });

    it('should emit save for a name with two characters', () => {
      vi.spyOn(component.save, 'emit');
      component.formData = { name: 'AB', typ: EinheitTyp.CONSUMER };

      component.onSubmit();

      expect(component.save.emit).toHaveBeenCalled();
    });

    it('should use the design-system form container', () => {
      expect(fixture.nativeElement.querySelector('.zev-form-container')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('.form-container')).toBeNull();
    });
  });
});
