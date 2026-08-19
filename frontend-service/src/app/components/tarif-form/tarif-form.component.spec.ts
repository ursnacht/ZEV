import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TarifFormComponent } from './tarif-form.component';
import { Mengeneinheit, Tarif, TarifTyp } from '../../models/tarif.model';
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

    it('should have five tarif type options', () => {
      expect(component.tarifTypOptions).toEqual([
        TarifTyp.ZEV, TarifTyp.VNB, TarifTyp.GRUNDGEBUEHR, TarifTyp.LADESTROM, TarifTyp.ZUSATZ
      ]);
    });

    it('should default produzentVerrechnen to false', () => {
      expect(component.formData.produzentVerrechnen).toBe(false);
    });
  });

  describe('mengeneinheit (nur ZUSATZ)', () => {
    // Nur der frei konfigurierbare Typ traegt eine eigene Mengeneinheit; bei allen anderen
    // folgt sie aus dem Typ (Specs/Tarifpositionen.md FR-1.1).

    it('should not require a unit for the fixed types', () => {
      for (const typ of [TarifTyp.ZEV, TarifTyp.VNB, TarifTyp.GRUNDGEBUEHR, TarifTyp.LADESTROM]) {
        component.formData.tariftyp = typ;
        expect(component.brauchtMengeneinheit).toBe(false);
      }
    });

    it('should require a unit for ZUSATZ', () => {
      component.formData.tariftyp = TarifTyp.ZUSATZ;
      expect(component.brauchtMengeneinheit).toBe(true);
    });

    it('should render the unit dropdown only for ZUSATZ', () => {
      component.formData.tariftyp = TarifTyp.LADESTROM;
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('#mengeneinheit')).toBeNull();

      component.formData.tariftyp = TarifTyp.ZUSATZ;
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('#mengeneinheit')).not.toBeNull();
    });

    it('should offer kWh, month and piece', () => {
      expect(component.mengeneinheitOptions).toEqual([
        Mengeneinheit.KWH, Mengeneinheit.MONAT, Mengeneinheit.STUECK
      ]);
    });

    it('should be invalid while a ZUSATZ tariff has no unit', () => {
      component.formData = {
        bezeichnung: 'Sauna', tariftyp: TarifTyp.ZUSATZ, preis: 5,
        gueltigVon: '2026-01-01', gueltigBis: '2026-12-31'
      };
      expect(component.isFormValid()).toBe(false);

      component.formData.mengeneinheit = Mengeneinheit.STUECK;
      expect(component.isFormValid()).toBe(true);
    });

    it('should discard the unit when switching to a type without one', () => {
      // Sonst bliebe ein unsichtbarer Wert im Formular stehen
      component.formData.tariftyp = TarifTyp.ZUSATZ;
      component.formData.mengeneinheit = Mengeneinheit.MONAT;

      component.formData.tariftyp = TarifTyp.LADESTROM;
      component.onTariftypChange();

      expect(component.formData.mengeneinheit).toBeUndefined();
    });

    it('should keep the unit while staying on ZUSATZ', () => {
      component.formData.tariftyp = TarifTyp.ZUSATZ;
      component.formData.mengeneinheit = Mengeneinheit.MONAT;
      component.onTariftypChange();
      expect(component.formData.mengeneinheit).toBe(Mengeneinheit.MONAT);
    });
  });

  describe('i18n der Auswahllisten', () => {
    // Die Beschriftungen standen frueher hartcodiert im Component und waren damit die einzigen
    // deutschen Texte im Formular (Specs/generell.md). Der Mock liefert den Key zurueck - im
    // gerenderten Markup muss also der Key stehen, nicht ein deutscher Text.

    it('should translate the tariff type options', () => {
      fixture.detectChanges();
      const texte = Array.from(
        fixture.nativeElement.querySelectorAll('#tariftyp option') as NodeListOf<HTMLElement>
      ).map(o => o.textContent?.trim());

      expect(texte).toEqual([
        'TARIFTYP_ZEV', 'TARIFTYP_VNB', 'TARIFTYP_GRUNDGEBUEHR',
        'TARIFTYP_LADESTROM', 'TARIFTYP_ZUSATZ'
      ]);
    });

    it('should translate the unit options', () => {
      component.formData.tariftyp = TarifTyp.ZUSATZ;
      fixture.detectChanges();
      const texte = Array.from(
        fixture.nativeElement.querySelectorAll('#mengeneinheit option') as NodeListOf<HTMLElement>
      ).map(o => o.textContent?.trim());

      // Erste Option ist der Platzhalter
      expect(texte).toEqual(['MENGENEINHEIT_WAEHLEN', 'KWH', 'MONAT', 'STUECK']);
    });
  });

  describe('Preis-Label', () => {
    // Die Bezugsgroesse stand frueher fest als "(CHF/kWh)" im Label - schon fuer die
    // Grundgebuehr falsch (CHF pro Monat).

    const label = () => (fixture.nativeElement.querySelector('label[for="preis"]') as HTMLElement).textContent ?? '';

    it('should name kWh for ZEV, VNB and Ladestrom', () => {
      for (const typ of [TarifTyp.ZEV, TarifTyp.VNB, TarifTyp.LADESTROM]) {
        component.formData.tariftyp = typ;
        expect(component.preisEinheit).toBe('KWH');
      }
    });

    it('should name the month for the Grundgebuehr', () => {
      component.formData.tariftyp = TarifTyp.GRUNDGEBUEHR;
      expect(component.preisEinheit).toBe('MONAT');
      fixture.detectChanges();
      expect(label()).toContain('MONAT');
      expect(label()).not.toContain('KWH');
    });

    it('should name the chosen unit for a ZUSATZ tariff', () => {
      component.formData.tariftyp = TarifTyp.ZUSATZ;
      component.formData.mengeneinheit = Mengeneinheit.STUECK;
      fixture.detectChanges();
      expect(label()).toContain('STUECK');
    });

    it('should show CHF alone while a ZUSATZ tariff has no unit yet', () => {
      // Sonst behauptete das Label kWh, obwohl die Einheit noch offen ist
      component.formData.tariftyp = TarifTyp.ZUSATZ;
      component.formData.mengeneinheit = undefined;
      expect(component.preisEinheit).toBe('');
      fixture.detectChanges();
      expect(label()).toContain('CHF');
      expect(label()).not.toContain('KWH');
    });
  });

  describe('produzentVerrechnen checkbox', () => {
    it('should not render checkbox for ZEV/VNB tariffs', () => {
      component.formData.tariftyp = TarifTyp.ZEV;
      fixture.detectChanges();
      const checkbox = fixture.nativeElement.querySelector('#produzentVerrechnen');
      expect(checkbox).toBeNull();
    });

    it('should render checkbox only for GRUNDGEBUEHR tariffs', () => {
      component.formData.tariftyp = TarifTyp.GRUNDGEBUEHR;
      fixture.detectChanges();
      const checkbox = fixture.nativeElement.querySelector('#produzentVerrechnen');
      expect(checkbox).not.toBeNull();
    });

    it('should preserve produzentVerrechnen from input tarif', () => {
      component.tarif = {
        id: 1,
        bezeichnung: 'Grundgebühr',
        tariftyp: TarifTyp.GRUNDGEBUEHR,
        preis: 5,
        gueltigVon: '2024-01-01',
        gueltigBis: '2024-12-31',
        produzentVerrechnen: true
      };
      component.ngOnInit();
      expect(component.formData.produzentVerrechnen).toBe(true);
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
      expect(component.isFormValid()).toBe(false);
    });

    it('should return false when bezeichnung is only whitespace', () => {
      component.formData = {
        bezeichnung: '   ',
        tariftyp: TarifTyp.ZEV,
        preis: 0.20,
        gueltigVon: '2024-01-01',
        gueltigBis: '2024-12-31'
      };
      expect(component.isFormValid()).toBe(false);
    });

    it('should return false when preis is zero', () => {
      component.formData = {
        bezeichnung: 'Test',
        tariftyp: TarifTyp.ZEV,
        preis: 0,
        gueltigVon: '2024-01-01',
        gueltigBis: '2024-12-31'
      };
      expect(component.isFormValid()).toBe(false);
    });

    it('should return false when preis is negative', () => {
      component.formData = {
        bezeichnung: 'Test',
        tariftyp: TarifTyp.ZEV,
        preis: -0.10,
        gueltigVon: '2024-01-01',
        gueltigBis: '2024-12-31'
      };
      expect(component.isFormValid()).toBe(false);
    });

    it('should return false when gueltigVon is empty', () => {
      component.formData = {
        bezeichnung: 'Test',
        tariftyp: TarifTyp.ZEV,
        preis: 0.20,
        gueltigVon: '',
        gueltigBis: '2024-12-31'
      };
      expect(component.isFormValid()).toBe(false);
    });

    it('should return false when gueltigBis is empty', () => {
      component.formData = {
        bezeichnung: 'Test',
        tariftyp: TarifTyp.ZEV,
        preis: 0.20,
        gueltigVon: '2024-01-01',
        gueltigBis: ''
      };
      expect(component.isFormValid()).toBe(false);
    });

    it('should return false when gueltigVon is after gueltigBis', () => {
      component.formData = {
        bezeichnung: 'Test',
        tariftyp: TarifTyp.ZEV,
        preis: 0.20,
        gueltigVon: '2024-12-31',
        gueltigBis: '2024-01-01'
      };
      expect(component.isFormValid()).toBe(false);
    });

    it('should return true when all fields are valid', () => {
      component.formData = {
        bezeichnung: 'Test Tarif',
        tariftyp: TarifTyp.ZEV,
        preis: 0.195,
        gueltigVon: '2024-01-01',
        gueltigBis: '2024-12-31'
      };
      expect(component.isFormValid()).toBe(true);
    });

    it('should return true when gueltigVon equals gueltigBis', () => {
      component.formData = {
        bezeichnung: 'Test',
        tariftyp: TarifTyp.ZEV,
        preis: 0.20,
        gueltigVon: '2024-06-15',
        gueltigBis: '2024-06-15'
      };
      expect(component.isFormValid()).toBe(true);
    });
  });

  describe('isDateRangeValid', () => {
    it('should return true when dates are not entered yet', () => {
      component.formData.gueltigVon = '';
      component.formData.gueltigBis = '';
      expect(component.isDateRangeValid()).toBe(true);
    });

    it('should return true when only gueltigVon is empty', () => {
      component.formData.gueltigVon = '';
      component.formData.gueltigBis = '2024-12-31';
      expect(component.isDateRangeValid()).toBe(true);
    });

    it('should return true when gueltigVon is before gueltigBis', () => {
      component.formData.gueltigVon = '2024-01-01';
      component.formData.gueltigBis = '2024-12-31';
      expect(component.isDateRangeValid()).toBe(true);
    });

    it('should return false when gueltigVon is after gueltigBis', () => {
      component.formData.gueltigVon = '2024-12-31';
      component.formData.gueltigBis = '2024-01-01';
      expect(component.isDateRangeValid()).toBe(false);
    });
  });

  describe('events', () => {
    it('should emit save event with form data on valid submit', () => {
      const saveSpy = vi.spyOn(component.save, 'emit');
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
      const saveSpy = vi.spyOn(component.save, 'emit');
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
      const cancelSpy = vi.spyOn(component.cancel, 'emit');

      component.onCancel();

      expect(cancelSpy).toHaveBeenCalled();
    });
  });
});
