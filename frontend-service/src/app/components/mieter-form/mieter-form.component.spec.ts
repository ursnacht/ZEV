import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MieterFormComponent } from './mieter-form.component';
import { Mieter } from '../../models/mieter.model';
import { Einheit, EinheitTyp } from '../../models/einheit.model';
import { TranslationService } from '../../services/translation.service';

describe('MieterFormComponent', () => {
  let component: MieterFormComponent;
  let fixture: ComponentFixture<MieterFormComponent>;

  const mockTranslationService = {
    translate: (key: string) => key
  };

  const mockEinheiten: Einheit[] = [
    { id: 1, name: 'Wohnung A', typ: EinheitTyp.CONSUMER },
    { id: 2, name: 'Wohnung B', typ: EinheitTyp.CONSUMER }
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MieterFormComponent],
      providers: [
        { provide: TranslationService, useValue: mockTranslationService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MieterFormComponent);
    component = fixture.componentInstance;
    component.einheiten = mockEinheiten;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should initialize with default form data when no mieter input', () => {
      expect(component.formData.name).toBe('');
      expect(component.formData.strasse).toBe('');
      expect(component.formData.plz).toBe('');
      expect(component.formData.ort).toBe('');
      expect(component.formData.einheitId).toBe(0);
    });

    it('should set default mietbeginn to today', () => {
      const today = new Date().toISOString().split('T')[0];
      expect(component.formData.mietbeginn).toBe(today);
    });

    it('should populate form with mieter data when input is provided', () => {
      const inputMieter: Mieter = {
        id: 1,
        name: 'Max Muster',
        strasse: 'Musterstr. 1',
        plz: '8000',
        ort: 'Zürich',
        mietbeginn: '2024-01-01',
        mietende: '2025-12-31',
        einheitId: 1
      };

      component.mieter = inputMieter;
      component.ngOnInit();

      expect(component.formData.name).toBe('Max Muster');
      expect(component.formData.strasse).toBe('Musterstr. 1');
      expect(component.formData.plz).toBe('8000');
      expect(component.formData.ort).toBe('Zürich');
      expect(component.formData.mietbeginn).toBe('2024-01-01');
      expect(component.formData.mietende).toBe('2025-12-31');
      expect(component.formData.einheitId).toBe(1);
    });
  });

  describe('isFormValid', () => {
    it('should return false when name is empty', () => {
      component.formData = {
        name: '',
        strasse: 'Str. 1',
        plz: '8000',
        ort: 'Zürich',
        mietbeginn: '2024-01-01',
        einheitId: 1
      };
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when name is only whitespace', () => {
      component.formData = {
        name: '   ',
        strasse: 'Str. 1',
        plz: '8000',
        ort: 'Zürich',
        mietbeginn: '2024-01-01',
        einheitId: 1
      };
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when strasse is empty', () => {
      component.formData = {
        name: 'Max',
        strasse: '',
        plz: '8000',
        ort: 'Zürich',
        mietbeginn: '2024-01-01',
        einheitId: 1
      };
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when plz is empty', () => {
      component.formData = {
        name: 'Max',
        strasse: 'Str. 1',
        plz: '',
        ort: 'Zürich',
        mietbeginn: '2024-01-01',
        einheitId: 1
      };
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when ort is empty', () => {
      component.formData = {
        name: 'Max',
        strasse: 'Str. 1',
        plz: '8000',
        ort: '',
        mietbeginn: '2024-01-01',
        einheitId: 1
      };
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when mietbeginn is empty', () => {
      component.formData = {
        name: 'Max',
        strasse: 'Str. 1',
        plz: '8000',
        ort: 'Zürich',
        mietbeginn: '',
        einheitId: 1
      };
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when einheitId is 0', () => {
      component.formData = {
        name: 'Max',
        strasse: 'Str. 1',
        plz: '8000',
        ort: 'Zürich',
        mietbeginn: '2024-01-01',
        einheitId: 0
      };
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return false when mietende is before mietbeginn', () => {
      component.formData = {
        name: 'Max',
        strasse: 'Str. 1',
        plz: '8000',
        ort: 'Zürich',
        mietbeginn: '2024-12-31',
        mietende: '2024-01-01',
        einheitId: 1
      };
      expect(component.isFormValid()).toBeFalse();
    });

    it('should return true when all fields are valid', () => {
      component.formData = {
        name: 'Max Muster',
        strasse: 'Musterstr. 1',
        plz: '8000',
        ort: 'Zürich',
        mietbeginn: '2024-01-01',
        einheitId: 1
      };
      expect(component.isFormValid()).toBeTrue();
    });

    it('should return true when mietende is not set', () => {
      component.formData = {
        name: 'Max',
        strasse: 'Str. 1',
        plz: '8000',
        ort: 'Zürich',
        mietbeginn: '2024-01-01',
        mietende: '',
        einheitId: 1
      };
      expect(component.isFormValid()).toBeTrue();
    });
  });

  describe('isDateRangeValid', () => {
    it('should return true when mietende is not set', () => {
      component.formData.mietbeginn = '2024-01-01';
      component.formData.mietende = '';
      expect(component.isDateRangeValid()).toBeTrue();
    });

    it('should return true when both dates are empty', () => {
      component.formData.mietbeginn = '';
      component.formData.mietende = '';
      expect(component.isDateRangeValid()).toBeTrue();
    });

    it('should return true when mietende is after mietbeginn', () => {
      component.formData.mietbeginn = '2024-01-01';
      component.formData.mietende = '2024-12-31';
      expect(component.isDateRangeValid()).toBeTrue();
    });

    it('should return false when mietende is before mietbeginn', () => {
      component.formData.mietbeginn = '2024-12-31';
      component.formData.mietende = '2024-01-01';
      expect(component.isDateRangeValid()).toBeFalse();
    });

    it('should return false when mietende equals mietbeginn', () => {
      component.formData.mietbeginn = '2024-06-15';
      component.formData.mietende = '2024-06-15';
      expect(component.isDateRangeValid()).toBeFalse();
    });
  });

  describe('events', () => {
    it('should emit save event with form data on valid submit', () => {
      const saveSpy = spyOn(component.save, 'emit');
      component.formData = {
        name: 'Max Muster',
        strasse: 'Musterstr. 1',
        plz: '8000',
        ort: 'Zürich',
        mietbeginn: '2024-01-01',
        einheitId: 1
      };

      component.onSubmit();

      expect(saveSpy).toHaveBeenCalled();
    });

    it('should not emit save event on invalid submit', () => {
      const saveSpy = spyOn(component.save, 'emit');
      component.formData = {
        name: '',
        strasse: '',
        plz: '',
        ort: '',
        mietbeginn: '',
        einheitId: 0
      };

      component.onSubmit();

      expect(saveSpy).not.toHaveBeenCalled();
    });

    it('should emit cancel event', () => {
      const cancelSpy = spyOn(component.cancel, 'emit');

      component.onCancel();

      expect(cancelSpy).toHaveBeenCalled();
    });

    it('should convert empty mietende to undefined on submit', () => {
      const saveSpy = spyOn(component.save, 'emit');
      component.formData = {
        name: 'Max',
        strasse: 'Str. 1',
        plz: '8000',
        ort: 'Zürich',
        mietbeginn: '2024-01-01',
        mietende: '',
        einheitId: 1
      };

      component.onSubmit();

      const emittedMieter = saveSpy.calls.first().args[0] as Mieter;
      expect(emittedMieter.mietende).toBeUndefined();
    });
  });

  describe('getEinheitDisplayName', () => {
    it('should format einheit name with ID', () => {
      const einheit: Einheit = { id: 1, name: 'Wohnung A', typ: EinheitTyp.CONSUMER };
      expect(component.getEinheitDisplayName(einheit)).toBe('Wohnung A (ID: 1)');
    });
  });
});
