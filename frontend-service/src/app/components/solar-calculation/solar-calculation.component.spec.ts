import { createSpyObj, SpyObj } from '../../../testing/spy';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { fakeAsync, tick } from '../../../testing/fake-async';
import { SolarCalculationComponent } from './solar-calculation.component';
import { MesswerteService, CalculationResponse } from '../../services/messwerte.service';
import { TranslationService } from '../../services/translation.service';
import { of, throwError } from 'rxjs';

describe('SolarCalculationComponent', () => {
  let component: SolarCalculationComponent;
  let fixture: ComponentFixture<SolarCalculationComponent>;
  let messwerteServiceSpy: SpyObj<MesswerteService>;
  let translationServiceSpy: SpyObj<TranslationService>;

  const mockCalculationResponse: CalculationResponse = {
    status: 'success',
    algorithm: 'PROPORTIONAL',
    processedTimestamps: 720,
    processedRecords: 2160,
    dateFrom: '2024-02-01',
    dateTo: '2024-02-29',
    totalSolarProduced: 1234.567,
    totalDistributed: 1234.567,
    message: 'Berechnung erfolgreich'
  };

  beforeEach(async () => {
    messwerteServiceSpy = createSpyObj<MesswerteService>('MesswerteService', ['calculateDistribution']);
    messwerteServiceSpy.calculateDistribution.mockReturnValue(of(mockCalculationResponse));

    translationServiceSpy = createSpyObj<TranslationService>('TranslationService', ['translate', 'getCurrentLanguage']);
    translationServiceSpy.translate.mockImplementation((key: string) => key);
    translationServiceSpy.getCurrentLanguage.mockReturnValue('de');

    await TestBed.configureTestingModule({
      imports: [SolarCalculationComponent],
      providers: [
        { provide: MesswerteService, useValue: messwerteServiceSpy },
        { provide: TranslationService, useValue: translationServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SolarCalculationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should set default dates', () => {
      expect(component.dateFrom).toBeTruthy();
      expect(component.dateTo).toBeTruthy();
      expect(component.dateFrom.length).toBe(10);
      expect(component.dateTo.length).toBe(10);
    });

    describe('default quarter', () => {
      beforeEach(() => {
        vi.useFakeTimers();
      });

      afterEach(() => {
        vi.useRealTimers();
      });

      it('should preselect the previous quarter', () => {
        vi.setSystemTime(new Date(2026, 4, 15)); // 15.05.2026 → Q2 → Vorquartal Q1/2026
        component.ngOnInit();
        expect(component.dateFrom).toBe('2026-01-01');
        expect(component.dateTo).toBe('2026-03-31');
      });

      it('should preselect Q4 of previous year when current quarter is Q1', () => {
        vi.setSystemTime(new Date(2026, 1, 10)); // 10.02.2026 → Q1 → Vorquartal Q4/2025
        component.ngOnInit();
        expect(component.dateFrom).toBe('2025-10-01');
        expect(component.dateTo).toBe('2025-12-31');
      });
    });

    it('should initialize algorithm as PROPORTIONAL', () => {
      expect(component.algorithm).toBe('PROPORTIONAL');
    });

    it('should initialize calculating as false', () => {
      expect(component.calculating).toBe(false);
    });

    it('should initialize result as null', () => {
      expect(component.result).toBeNull();
    });

    it('should initialize message as empty string', () => {
      expect(component.message).toBe('');
    });

    it('should initialize messageType as empty string', () => {
      expect(component.messageType).toBe('');
    });
  });

  describe('onDateFromChange', () => {
    it('should set dateTo to last day of dateFrom month', () => {
      component.dateFrom = '2024-02-15';
      component.onDateFromChange();
      expect(component.dateTo).toBe('2024-02-29'); // 2024 is a leap year
    });

    it('should set dateTo for non-leap year February', () => {
      component.dateFrom = '2023-02-10';
      component.onDateFromChange();
      expect(component.dateTo).toBe('2023-02-28');
    });

    it('should set dateTo to last day of December', () => {
      component.dateFrom = '2024-12-01';
      component.onDateFromChange();
      expect(component.dateTo).toBe('2024-12-31');
    });

    it('should set dateTo to last day of January', () => {
      component.dateFrom = '2024-01-10';
      component.onDateFromChange();
      expect(component.dateTo).toBe('2024-01-31');
    });

    it('should not change dateTo when dateFrom is empty', () => {
      component.dateTo = '2024-01-31';
      component.dateFrom = '';
      component.onDateFromChange();
      expect(component.dateTo).toBe('2024-01-31');
    });
  });

  describe('onQuarterSelected', () => {
    it('should set dateFrom and dateTo from event', () => {
      component.onQuarterSelected({ von: '2024-01-01', bis: '2024-03-31' });
      expect(component.dateFrom).toBe('2024-01-01');
      expect(component.dateTo).toBe('2024-03-31');
    });

    it('should overwrite existing dates', () => {
      component.dateFrom = '2023-01-01';
      component.dateTo = '2023-12-31';
      component.onQuarterSelected({ von: '2024-04-01', bis: '2024-06-30' });
      expect(component.dateFrom).toBe('2024-04-01');
      expect(component.dateTo).toBe('2024-06-30');
    });
  });

  describe('onSubmit', () => {
    beforeEach(() => {
      component.dateFrom = '2024-02-01';
      component.dateTo = '2024-02-29';
      component.algorithm = 'PROPORTIONAL';
    });

    it('should call calculateDistribution with correct params', () => {
      component.onSubmit();
      expect(messwerteServiceSpy.calculateDistribution).toHaveBeenCalledWith(
        '2024-02-01',
        '2024-02-29',
        'PROPORTIONAL'
      );
    });

    it('should set result on success', () => {
      component.onSubmit();
      expect(component.result).toEqual(mockCalculationResponse);
    });

    it('should show success message on success', () => {
      component.onSubmit();
      expect(component.messageType).toBe('success');
      expect(component.message).toBe('BERECHNUNG_ERFOLGREICH');
    });

    it('should set calculating to false after success', () => {
      component.onSubmit();
      expect(component.calculating).toBe(false);
    });

    it('should set result to null before calculating', () => {
      component.result = mockCalculationResponse;
      let resultDuringCalc: CalculationResponse | null = null;
      messwerteServiceSpy.calculateDistribution.mockImplementation(() => {
        resultDuringCalc = component.result;
        return of(mockCalculationResponse);
      });
      component.onSubmit();
      expect(resultDuringCalc).toBeNull();
    });

    it('should show error message when dateFrom is empty', () => {
      component.dateFrom = '';
      component.onSubmit();
      expect(component.messageType).toBe('error');
      expect(component.message).toBe('BITTE_BEIDE_DATEN_AUSFUELLEN');
      expect(messwerteServiceSpy.calculateDistribution).not.toHaveBeenCalled();
    });

    it('should show error message when dateTo is empty', () => {
      component.dateTo = '';
      component.onSubmit();
      expect(component.messageType).toBe('error');
      expect(component.message).toBe('BITTE_BEIDE_DATEN_AUSFUELLEN');
      expect(messwerteServiceSpy.calculateDistribution).not.toHaveBeenCalled();
    });

    it('should show error message when dateFrom is after dateTo', () => {
      component.dateFrom = '2024-12-31';
      component.dateTo = '2024-01-01';
      component.onSubmit();
      expect(component.messageType).toBe('error');
      expect(component.message).toBe('START_VOR_END_DATUM');
      expect(messwerteServiceSpy.calculateDistribution).not.toHaveBeenCalled();
    });

    it('should show error message on service error', () => {
      messwerteServiceSpy.calculateDistribution.mockReturnValue(throwError(() => new Error('Network error')));
      component.onSubmit();
      expect(component.messageType).toBe('error');
      expect(component.message).toContain('Network error');
    });

    it('should set calculating to false on error', () => {
      messwerteServiceSpy.calculateDistribution.mockReturnValue(throwError(() => new Error('fail')));
      component.onSubmit();
      expect(component.calculating).toBe(false);
    });

    it('should show error message when response status is not success', () => {
      const failResponse: CalculationResponse = {
        ...mockCalculationResponse,
        status: 'error',
        message: 'Kein Tarif gefunden'
      };
      messwerteServiceSpy.calculateDistribution.mockReturnValue(of(failResponse));
      component.onSubmit();
      expect(component.messageType).toBe('error');
      expect(component.message).toContain('Kein Tarif gefunden');
    });

    it('should not set result when response status is not success', () => {
      const failResponse: CalculationResponse = {
        ...mockCalculationResponse,
        status: 'error',
        message: 'Fehler'
      };
      messwerteServiceSpy.calculateDistribution.mockReturnValue(of(failResponse));
      component.onSubmit();
      expect(component.result).toBeNull();
    });

    it('should set calculating to false even when status is not success', () => {
      const failResponse: CalculationResponse = {
        ...mockCalculationResponse,
        status: 'error',
        message: 'Fehler'
      };
      messwerteServiceSpy.calculateDistribution.mockReturnValue(of(failResponse));
      component.onSubmit();
      expect(component.calculating).toBe(false);
    });
  });

  describe('message auto-dismiss', () => {
    beforeEach(() => {
      component.dateFrom = '2024-02-01';
      component.dateTo = '2024-02-29';
    });

    it('should auto-dismiss success messages after 5s', fakeAsync(() => {
      component.onSubmit();
      expect(component.messageType).toBe('success');
      tick(5000);
      expect(component.message).toBe('');
      expect(component.messageType).toBe('');
    }));

    it('should not auto-dismiss error messages', fakeAsync(() => {
      component.dateFrom = '';
      component.onSubmit();
      expect(component.messageType).toBe('error');
      tick(5000);
      expect(component.messageType).toBe('error');
      expect(component.message).not.toBe('');
    }));
  });
});
