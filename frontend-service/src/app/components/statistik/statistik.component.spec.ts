import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { StatistikComponent } from './statistik.component';
import { StatistikService } from '../../services/statistik.service';
import { TranslationService } from '../../services/translation.service';
import { Statistik, MonatsStatistik } from '../../models/statistik.model';
import { of, throwError } from 'rxjs';

describe('StatistikComponent', () => {
  let component: StatistikComponent;
  let fixture: ComponentFixture<StatistikComponent>;
  let statistikServiceSpy: jasmine.SpyObj<StatistikService>;
  let translationServiceSpy: jasmine.SpyObj<TranslationService>;

  const mockMonat: MonatsStatistik = {
    jahr: 2024,
    monat: 2,
    von: '2024-02-01',
    bis: '2024-02-29',
    datenVollstaendig: true,
    fehlendeEinheiten: [],
    fehlendeTage: [],
    summeProducerTotal: 1000,
    summeConsumerTotal: 900,
    summeProducerZev: 800,
    summeConsumerZev: 750,
    summeConsumerZevCalculated: 760,
    summenCDGleich: true,
    differenzCD: 0,
    summenCEGleich: false,
    differenzCE: -10,
    summenDEGleich: true,
    differenzDE: 0,
    tageAbweichungen: [],
    einheitSummen: []
  };

  const mockStatistik: Statistik = {
    messwerteBisDate: '2024-02-29',
    datenVollstaendig: true,
    fehlendeEinheiten: [],
    fehlendeTage: [],
    monate: [mockMonat],
    toleranz: 0.5
  };

  beforeEach(async () => {
    statistikServiceSpy = jasmine.createSpyObj('StatistikService', ['getStatistik', 'exportPdf']);
    statistikServiceSpy.getStatistik.and.returnValue(of(mockStatistik));
    statistikServiceSpy.exportPdf.and.returnValue(of(new Blob(['pdf'], { type: 'application/pdf' })));

    translationServiceSpy = jasmine.createSpyObj('TranslationService', ['translate', 'getCurrentLanguage']);
    translationServiceSpy.translate.and.callFake((key: string) => key);
    translationServiceSpy.getCurrentLanguage.and.returnValue('de');

    await TestBed.configureTestingModule({
      imports: [StatistikComponent],
      providers: [
        { provide: StatistikService, useValue: statistikServiceSpy },
        { provide: TranslationService, useValue: translationServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(StatistikComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should set default date range on init', () => {
      expect(component.dateFrom).toBeTruthy();
      expect(component.dateTo).toBeTruthy();
      expect(component.dateFrom.length).toBe(10); // YYYY-MM-DD
      expect(component.dateTo.length).toBe(10);
    });

    it('should set dateFrom to first day of previous month', () => {
      const now = new Date();
      const expectedYear = now.getMonth() === 0 ? now.getFullYear() - 1 : now.getFullYear();
      const expectedMonth = now.getMonth() === 0 ? 12 : now.getMonth();
      const expectedDay = '01';
      const expectedDateFrom = `${expectedYear}-${String(expectedMonth).padStart(2, '0')}-${expectedDay}`;
      expect(component.dateFrom).toBe(expectedDateFrom);
    });

    it('should initialize statistik as null', () => {
      expect(component.statistik).toBeNull();
    });

    it('should initialize loading as false', () => {
      expect(component.loading).toBeFalse();
    });

    it('should initialize expandedGlobalDetails as false', () => {
      expect(component.expandedGlobalDetails).toBeFalse();
    });

    it('should initialize expandedMonths as empty set', () => {
      expect(component.expandedMonths.size).toBe(0);
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

    it('should not change dateTo when dateFrom is empty', () => {
      const prevDateTo = component.dateTo;
      component.dateFrom = '';
      component.onDateFromChange();
      expect(component.dateTo).toBe(prevDateTo);
    });
  });

  describe('onQuarterSelected', () => {
    it('should set dateFrom and dateTo from event', () => {
      component.onQuarterSelected({ von: '2024-01-01', bis: '2024-03-31' });
      expect(component.dateFrom).toBe('2024-01-01');
      expect(component.dateTo).toBe('2024-03-31');
    });
  });

  describe('onSubmit', () => {
    beforeEach(() => {
      component.dateFrom = '2024-02-01';
      component.dateTo = '2024-02-29';
    });

    it('should call getStatistik with correct params', () => {
      component.onSubmit();
      expect(statistikServiceSpy.getStatistik).toHaveBeenCalledWith('2024-02-01', '2024-02-29');
    });

    it('should set statistik on success', () => {
      component.onSubmit();
      expect(component.statistik).toEqual(mockStatistik);
    });

    it('should set loading to false after success', () => {
      component.onSubmit();
      expect(component.loading).toBeFalse();
    });

    it('should show success message with month count', () => {
      component.onSubmit();
      expect(component.messageType).toBe('success');
      expect(component.message).toContain('1');
      expect(component.message).toContain('MONATE_GELADEN');
    });

    it('should clear expandedMonths on submit', () => {
      component.expandedMonths.add(0);
      component.onSubmit();
      expect(component.expandedMonths.size).toBe(0);
    });

    it('should show error message when dateFrom is empty', () => {
      component.dateFrom = '';
      component.onSubmit();
      expect(component.messageType).toBe('error');
      expect(statistikServiceSpy.getStatistik).not.toHaveBeenCalled();
    });

    it('should show error message when dateTo is empty', () => {
      component.dateTo = '';
      component.onSubmit();
      expect(component.messageType).toBe('error');
      expect(statistikServiceSpy.getStatistik).not.toHaveBeenCalled();
    });

    it('should show error message when dateFrom is after dateTo', () => {
      component.dateFrom = '2024-12-31';
      component.dateTo = '2024-01-01';
      component.onSubmit();
      expect(component.messageType).toBe('error');
      expect(statistikServiceSpy.getStatistik).not.toHaveBeenCalled();
    });

    it('should show error message on service failure', () => {
      statistikServiceSpy.getStatistik.and.returnValue(throwError(() => new Error('Network error')));
      component.onSubmit();
      expect(component.messageType).toBe('error');
      expect(component.message).toContain('FEHLER_BEIM_LADEN_DER_DATEN');
      expect(component.message).toContain('Network error');
    });

    it('should set loading to false on error', () => {
      statistikServiceSpy.getStatistik.and.returnValue(throwError(() => new Error('fail')));
      component.onSubmit();
      expect(component.loading).toBeFalse();
    });

    it('should set statistik to null before loading', () => {
      component.statistik = mockStatistik;
      // Use a delayed observable to verify intermediate state
      let statistikDuringLoad: Statistik | null = null;
      statistikServiceSpy.getStatistik.and.callFake(() => {
        statistikDuringLoad = component.statistik;
        return of(mockStatistik);
      });
      component.onSubmit();
      expect(statistikDuringLoad).toBeNull();
    });
  });

  describe('exportPdf', () => {
    beforeEach(() => {
      component.dateFrom = '2024-02-01';
      component.dateTo = '2024-02-29';
    });

    it('should call exportPdf with correct params', () => {
      component.exportPdf();
      expect(statistikServiceSpy.exportPdf).toHaveBeenCalledWith('2024-02-01', '2024-02-29', 'de');
    });

    it('should show error message when dateFrom is empty', () => {
      component.dateFrom = '';
      component.exportPdf();
      expect(component.messageType).toBe('error');
      expect(statistikServiceSpy.exportPdf).not.toHaveBeenCalled();
    });

    it('should show error message when dateTo is empty', () => {
      component.dateTo = '';
      component.exportPdf();
      expect(component.messageType).toBe('error');
      expect(statistikServiceSpy.exportPdf).not.toHaveBeenCalled();
    });

    it('should show error message on service failure', () => {
      statistikServiceSpy.exportPdf.and.returnValue(throwError(() => new Error('PDF error')));
      component.exportPdf();
      expect(component.messageType).toBe('error');
      expect(component.message).toContain('FEHLER_BEIM_EXPORT');
      expect(component.message).toContain('PDF error');
    });
  });

  describe('toggleMonthDetails', () => {
    it('should add index to expandedMonths when not present', () => {
      component.toggleMonthDetails(0);
      expect(component.expandedMonths.has(0)).toBeTrue();
    });

    it('should remove index from expandedMonths when already present', () => {
      component.expandedMonths.add(0);
      component.toggleMonthDetails(0);
      expect(component.expandedMonths.has(0)).toBeFalse();
    });

    it('should handle multiple months independently', () => {
      component.toggleMonthDetails(0);
      component.toggleMonthDetails(1);
      expect(component.expandedMonths.has(0)).toBeTrue();
      expect(component.expandedMonths.has(1)).toBeTrue();
      component.toggleMonthDetails(0);
      expect(component.expandedMonths.has(0)).toBeFalse();
      expect(component.expandedMonths.has(1)).toBeTrue();
    });
  });

  describe('isMonthExpanded', () => {
    it('should return false when month is not expanded', () => {
      expect(component.isMonthExpanded(0)).toBeFalse();
    });

    it('should return true when month is expanded', () => {
      component.expandedMonths.add(0);
      expect(component.isMonthExpanded(0)).toBeTrue();
    });
  });

  describe('toggleGlobalDetails', () => {
    it('should toggle expandedGlobalDetails from false to true', () => {
      component.expandedGlobalDetails = false;
      component.toggleGlobalDetails();
      expect(component.expandedGlobalDetails).toBeTrue();
    });

    it('should toggle expandedGlobalDetails from true to false', () => {
      component.expandedGlobalDetails = true;
      component.toggleGlobalDetails();
      expect(component.expandedGlobalDetails).toBeFalse();
    });
  });

  describe('getMonthName', () => {
    it('should translate January (monat=1)', () => {
      component.getMonthName(1);
      expect(translationServiceSpy.translate).toHaveBeenCalledWith('JANUAR');
    });

    it('should translate December (monat=12)', () => {
      component.getMonthName(12);
      expect(translationServiceSpy.translate).toHaveBeenCalledWith('DEZEMBER');
    });

    it('should translate February (monat=2)', () => {
      component.getMonthName(2);
      expect(translationServiceSpy.translate).toHaveBeenCalledWith('FEBRUAR');
    });

    it('should return translated key for each month', () => {
      const expectedKeys = ['JANUAR', 'FEBRUAR', 'MAERZ', 'APRIL', 'MAI', 'JUNI',
        'JULI', 'AUGUST', 'SEPTEMBER', 'OKTOBER', 'NOVEMBER', 'DEZEMBER'];
      expectedKeys.forEach((key, index) => {
        translationServiceSpy.translate.calls.reset();
        component.getMonthName(index + 1);
        expect(translationServiceSpy.translate).toHaveBeenCalledWith(key);
      });
    });
  });

  describe('getStatusClass', () => {
    it('should return success class when vollstaendig is true', () => {
      expect(component.getStatusClass(true)).toBe('zev-status-dot--success');
    });

    it('should return error class when vollstaendig is false', () => {
      expect(component.getStatusClass(false)).toBe('zev-status-dot--error');
    });
  });

  describe('getComparisonStatusClass', () => {
    it('should return success class when isEqual is true', () => {
      expect(component.getComparisonStatusClass(true)).toBe('zev-status-dot--success');
    });

    it('should return error class when isEqual is false', () => {
      expect(component.getComparisonStatusClass(false)).toBe('zev-status-dot--error');
    });
  });

  describe('formatNumber', () => {
    it('should return "-" for null', () => {
      expect(component.formatNumber(null)).toBe('-');
    });

    it('should return "-" for undefined', () => {
      expect(component.formatNumber(undefined)).toBe('-');
    });

    it('should format number with 3 decimal places', () => {
      expect(component.formatNumber(1.23456)).toBe('1.235');
    });

    it('should pad with zeros for whole numbers', () => {
      expect(component.formatNumber(100)).toBe('100.000');
    });

    it('should format zero correctly', () => {
      expect(component.formatNumber(0)).toBe('0.000');
    });
  });

  describe('formatDifferenz', () => {
    it('should return "-" for null', () => {
      expect(component.formatDifferenz(null)).toBe('-');
    });

    it('should return "-" for undefined', () => {
      expect(component.formatDifferenz(undefined)).toBe('-');
    });

    it('should prefix positive numbers with "+"', () => {
      expect(component.formatDifferenz(5.123)).toBe('+5.123');
    });

    it('should not add prefix to negative numbers', () => {
      expect(component.formatDifferenz(-3.456)).toBe('-3.456');
    });

    it('should prefix zero with "+"', () => {
      expect(component.formatDifferenz(0)).toBe('+0.000');
    });
  });

  describe('hasAbweichungen', () => {
    it('should return false when all sums are equal', () => {
      const monat: MonatsStatistik = { ...mockMonat, summenCDGleich: true, summenCEGleich: true, summenDEGleich: true };
      expect(component.hasAbweichungen(monat)).toBeFalse();
    });

    it('should return true when CD sums differ', () => {
      const monat: MonatsStatistik = { ...mockMonat, summenCDGleich: false, summenCEGleich: true, summenDEGleich: true };
      expect(component.hasAbweichungen(monat)).toBeTrue();
    });

    it('should return true when CE sums differ', () => {
      const monat: MonatsStatistik = { ...mockMonat, summenCDGleich: true, summenCEGleich: false, summenDEGleich: true };
      expect(component.hasAbweichungen(monat)).toBeTrue();
    });

    it('should return true when DE sums differ', () => {
      const monat: MonatsStatistik = { ...mockMonat, summenCDGleich: true, summenCEGleich: true, summenDEGleich: false };
      expect(component.hasAbweichungen(monat)).toBeTrue();
    });
  });

  describe('getBarWidth', () => {
    it('should return 100 for the maximum value', () => {
      const width = component.getBarWidth(mockMonat.summeProducerTotal, mockMonat);
      expect(width).toBe(100);
    });

    it('should return 0 when all values are 0', () => {
      const zeroMonat: MonatsStatistik = {
        ...mockMonat,
        summeProducerTotal: 0,
        summeConsumerTotal: 0,
        summeProducerZev: 0,
        summeConsumerZev: 0,
        summeConsumerZevCalculated: 0
      };
      expect(component.getBarWidth(0, zeroMonat)).toBe(0);
    });

    it('should return proportional width for non-max values', () => {
      // summeConsumerTotal = 900, summeProducerTotal = 1000 (max)
      const width = component.getBarWidth(mockMonat.summeConsumerTotal, mockMonat);
      expect(width).toBe(90);
    });
  });

  describe('getBarColor', () => {
    it('should return green for type A (Producer Total)', () => {
      expect(component.getBarColor('A')).toBe('#4CAF50');
    });

    it('should return blue for type B (Consumer Total)', () => {
      expect(component.getBarColor('B')).toBe('#2196F3');
    });

    it('should return orange for type C (Producer ZEV)', () => {
      expect(component.getBarColor('C')).toBe('#FF9800');
    });

    it('should return purple for type D (Consumer ZEV)', () => {
      expect(component.getBarColor('D')).toBe('#9C27B0');
    });

    it('should return cyan for type E (Consumer ZEV Calculated)', () => {
      expect(component.getBarColor('E')).toBe('#00BCD4');
    });
  });

  describe('message auto-dismiss', () => {
    it('should auto-dismiss success messages after 5s', fakeAsync(() => {
      component.dateFrom = '2024-02-01';
      component.dateTo = '2024-02-29';
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
