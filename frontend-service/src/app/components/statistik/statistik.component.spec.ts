import { createSpyObj, SpyObj } from '../../../testing/spy';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { fakeAsync, tick } from '../../../testing/fake-async';
import { StatistikComponent } from './statistik.component';
import { IconComponent } from '../icon/icon.component';
import { StatistikService } from '../../services/statistik.service';
import { TranslationService } from '../../services/translation.service';
import { Statistik, MonatsStatistik, EinheitSummen } from '../../models/statistik.model';
import { of, throwError } from 'rxjs';

describe('StatistikComponent', () => {
  let component: StatistikComponent;
  let fixture: ComponentFixture<StatistikComponent>;
  let statistikServiceSpy: SpyObj<StatistikService>;
  let translationServiceSpy: SpyObj<TranslationService>;

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
    bezugVonVnb: 140,
    ruecklieferung: 1000,
    bilanzBezug: 140,
    bilanzRuecklieferung: 1000,
    bilanzBezugName: 'Bezug VNB',
    bilanzRuecklieferungName: 'Rücklieferung VNB',
    summenCDGleich: true,
    differenzCD: 0,
    summenCEGleich: false,
    differenzCE: -10,
    summenDEGleich: true,
    differenzDE: 0,
    bezugBilanzGleich: true,
    bezugBilanzDifferenz: 0,
    ruecklieferungBilanzGleich: true,
    ruecklieferungBilanzDifferenz: 0,
    tageAbweichungen: [],
    einheitSummen: [],
    autarkiegrad: 0.8333,
    eigenverbrauchsquote: 0.8,
    netzbezugsquote: 0.1667,
    einspeisequote: 0.2,
    zevEigenverbrauch: 750,
    batterieNetto: 100,
    batterieGeladen: 120,
    batterieEntladen: 20,
    batterieWirkungsgrad: 0.1667,
    batterieKennzahlenVerfuegbar: true,
    batterieGemessen: false,
    autarkiegradGemessen: 0.8444,
    netzbezugsquoteGemessen: 0.1556,
    bilanzKennzahlenVerfuegbar: true,
    bilanzBezugLueckenhaft: false,
    verteilungLueckenhaft: false
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
    statistikServiceSpy = createSpyObj<StatistikService>('StatistikService', ['getStatistik', 'exportPdf', 'exportCsv']);
    statistikServiceSpy.getStatistik.mockReturnValue(of(mockStatistik));
    statistikServiceSpy.exportPdf.mockReturnValue(of(new Blob(['pdf'], { type: 'application/pdf' })));
    statistikServiceSpy.exportCsv.mockReturnValue(of(new Blob(['csv'], { type: 'text/csv' })));

    translationServiceSpy = createSpyObj<TranslationService>('TranslationService', ['translate', 'getCurrentLanguage']);
    translationServiceSpy.translate.mockImplementation((key: string) => key);
    translationServiceSpy.getCurrentLanguage.mockReturnValue('de');

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

  /** Rendert die Statistik mit genau den angegebenen offenen Panels (Monatsindex bzw. GESAMT). */
  function rendere(statistik: Statistik, offen: number[] = []): HTMLElement {
    component.statistik = statistik;
    component.loading = false;
    component.expandedMonthPanels.clear();
    offen.forEach(i => component.expandedMonthPanels.add(i));
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function heuteKnopf(): HTMLButtonElement {
    const knoepfe = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button'));
    const heute = knoepfe.find(b => b.textContent?.trim() === 'HEUTE');
    expect(heute).toBeTruthy();
    return heute as HTMLButtonElement;
  }

  describe('initialization', () => {
    it('should set default date range on init', () => {
      expect(component.dateFrom).toBeTruthy();
      expect(component.dateTo).toBeTruthy();
      expect(component.dateFrom.length).toBe(10); // YYYY-MM-DD
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

    it('should initialize statistik as null', () => {
      expect(component.statistik).toBeNull();
    });

    it('should initialize loading as false', () => {
      expect(component.loading).toBe(false);
    });

    it('should initialize expandedGlobalDetails as false', () => {
      expect(component.expandedGlobalDetails).toBe(false);
    });

    it('should initialize expandedMonths as empty set', () => {
      expect(component.expandedMonths.size).toBe(0);
    });

    it('should start with every month panel collapsed', () => {
      // Alle zugeklappt: Ein Quartal liefert drei Monate, aufgeklappt waere die Seite mehrere
      // Bildschirmseiten lang.
      expect(component.expandedMonthPanels.size).toBe(0);
      expect(component.isMonthPanelExpanded(0)).toBe(false);
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

  describe('onHeute', () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('should set dateFrom and dateTo to today', () => {
      vi.setSystemTime(new Date(2026, 9, 7, 14, 30)); // 07.10.2026, 14:30 Ortszeit

      component.onHeute();

      expect(component.dateFrom).toBe('2026-10-07');
      expect(component.dateTo).toBe('2026-10-07');
    });

    /**
     * Das <b>lokale</b> Tagesdatum, nicht das UTC-Datum.
     *
     * <p>00:30 Ortszeit ist im Sommer 22:30 UTC des Vortags. Über {@code toISOString()} stünde
     * dann der 06.10. im Feld — ein Fehler, der nur in der ersten Stunde nach Mitternacht auffällt.
     */
    it('should use the local date shortly after midnight', () => {
      vi.setSystemTime(new Date(2026, 9, 7, 0, 30)); // 07.10.2026, 00:30 Ortszeit

      component.onHeute();

      expect(component.dateFrom).toBe('2026-10-07');
    });

    /** Geändert am 14.10.2026: „Heute" zeigt die Statistik sofort an. */
    it('should load statistics for today immediately', () => {
      vi.setSystemTime(new Date(2026, 9, 7, 9, 0));
      statistikServiceSpy.getStatistik.mockClear();

      component.onHeute();

      expect(statistikServiceSpy.getStatistik).toHaveBeenCalledWith('2026-10-07', '2026-10-07');
    });

    /** Ein zweiter Klick aktualisiert - für den laufenden Tag kommen laufend Messwerte dazu. */
    it('should reload when today is already shown', () => {
      vi.setSystemTime(new Date(2026, 9, 7, 9, 0));
      component.onHeute();
      statistikServiceSpy.getStatistik.mockClear();

      component.onHeute();

      expect(statistikServiceSpy.getStatistik).toHaveBeenCalledTimes(1);
    });

    it('should disable the button while loading', () => {
      component.loading = true;
      fixture.detectChanges();
      expect(heuteKnopf().disabled).toBe(true);

      component.loading = false;
      fixture.detectChanges();
      expect(heuteKnopf().disabled).toBe(false);
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
      expect(component.loading).toBe(false);
    });

    /** Geaendert am 14.10.2026: keine Erfolgsmeldung - die Statistik selbst ist die Rueckmeldung. */
    it('should not show a message after loading', () => {
      component.onSubmit();
      expect(component.message).toBe('');
      expect(component.messageType).toBe('');
    });

    it('should clear a previous error message when loading again', () => {
      component.showMessage('FEHLER_BEIM_LADEN_DER_DATEN: alt', 'error');
      component.onSubmit();
      expect(component.message).toBe('');
    });

    it('should clear expandedMonths on submit', () => {
      component.expandedMonths.add(0);
      component.onSubmit();
      expect(component.expandedMonths.size).toBe(0);
    });

    it('should collapse all month panels on submit', () => {
      // Sonst bliebe nach einem Zeitraumwechsel ein Panel offen, das jetzt einen anderen
      // Monat zeigt.
      component.expandedMonthPanels.add(0);
      component.expandedMonthPanels.add(2);
      component.onSubmit();
      expect(component.isMonthPanelExpanded(0)).toBe(false);
      expect(component.isMonthPanelExpanded(2)).toBe(false);
    });

    /** Das Gesamt-Panel startet aufgeklappt — auch nach jedem neuen Abruf. */
    it('should expand the total panel on submit', () => {
      component.toggleMonthPanel(component.GESAMT);   // vorher zugeklappt
      component.onSubmit();
      expect(component.isMonthPanelExpanded(component.GESAMT)).toBe(true);
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
      statistikServiceSpy.getStatistik.mockReturnValue(throwError(() => new Error('Network error')));
      component.onSubmit();
      expect(component.messageType).toBe('error');
      expect(component.message).toContain('FEHLER_BEIM_LADEN_DER_DATEN');
      expect(component.message).toContain('Network error');
    });

    it('should set loading to false on error', () => {
      statistikServiceSpy.getStatistik.mockReturnValue(throwError(() => new Error('fail')));
      component.onSubmit();
      expect(component.loading).toBe(false);
    });

    it('should set statistik to null before loading', () => {
      component.statistik = mockStatistik;
      // Use a delayed observable to verify intermediate state
      let statistikDuringLoad: Statistik | null = null;
      statistikServiceSpy.getStatistik.mockImplementation(() => {
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
      vi.spyOn(window.URL, 'createObjectURL').mockReturnValue('blob:fake-url');
      vi.spyOn(window.URL, 'revokeObjectURL').mockImplementation(() => {});
      const linkSpy = createSpyObj<HTMLAnchorElement>('a', ['click']);
      vi.spyOn(document, 'createElement').mockReturnValue(linkSpy as unknown as HTMLAnchorElement);
    });

    afterEach(() => {
      vi.restoreAllMocks();
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
      statistikServiceSpy.exportPdf.mockReturnValue(throwError(() => new Error('PDF error')));
      component.exportPdf();
      expect(component.messageType).toBe('error');
      expect(component.message).toContain('FEHLER_BEIM_EXPORT');
      expect(component.message).toContain('PDF error');
    });
  });

  describe('onDownloadCsv', () => {
    const consumerEinheit: EinheitSummen = {
      einheitId: 42,
      einheitName: 'Wohnung 1',
      einheitTyp: 'CONSUMER',
      summeTotal: 123.456,
      summeZev: 100.5,
      summeZevCalculated: 99.9
    };

    let linkSpy: SpyObj<HTMLAnchorElement>;

    beforeEach(() => {
      vi.spyOn(window.URL, 'createObjectURL').mockReturnValue('blob:fake-url');
      vi.spyOn(window.URL, 'revokeObjectURL').mockImplementation(() => {});
      linkSpy = createSpyObj<HTMLAnchorElement>('a', ['click']);
      vi.spyOn(document, 'createElement').mockReturnValue(linkSpy as unknown as HTMLAnchorElement);
    });

    afterEach(() => {
      vi.restoreAllMocks();
    });

    it('should call exportCsv with einheitId, monat.von, monat.bis and current language', () => {
      component.onDownloadCsv(mockMonat, consumerEinheit);
      expect(translationServiceSpy.getCurrentLanguage).toHaveBeenCalled();
      expect(statistikServiceSpy.exportCsv).toHaveBeenCalledWith(42, '2024-02-01', '2024-02-29', 'de');
    });

    it('should trigger a download on success', () => {
      component.onDownloadCsv(mockMonat, consumerEinheit);
      expect(window.URL.createObjectURL).toHaveBeenCalled();
      expect(linkSpy.click).toHaveBeenCalled();
      expect(window.URL.revokeObjectURL).toHaveBeenCalledWith('blob:fake-url');
    });

    it('should build filename from sanitized unit name and yyyy-MM', () => {
      // Space is not in [A-Za-z0-9._-] and is therefore replaced with '_'
      component.onDownloadCsv(mockMonat, consumerEinheit);
      expect(linkSpy.download).toBe('verbrauch_Wohnung_1_2024-02.csv');
    });

    it('should sanitize special characters in the unit name', () => {
      const einheit: EinheitSummen = { ...consumerEinheit, einheitName: 'Wohnung A/B' };
      component.onDownloadCsv(mockMonat, einheit);
      expect(linkSpy.download).toBe('verbrauch_Wohnung_A_B_2024-02.csv');
    });

    /**
     * Über mehrere Monate (Gesamt-Panel) trägt der Name den Zeitraum.
     *
     * <p>Mit dem Monat von `von` allein hiesse ein Quartalsexport `…_2024-01.csv`, als enthielte er
     * nur den Januar.
     */
    it('should name a multi-month export after its range', () => {
      const gesamt: MonatsStatistik = { ...mockMonat, monat: 0, jahr: 0, von: '2024-01-01', bis: '2024-03-31' };
      component.onDownloadCsv(gesamt, consumerEinheit);
      expect(linkSpy.download).toBe('verbrauch_Wohnung_1_2024-01-01_2024-03-31.csv');
      expect(statistikServiceSpy.exportCsv).toHaveBeenCalledWith(42, '2024-01-01', '2024-03-31', 'de');
    });

    it('should show translated error message on service failure', () => {
      statistikServiceSpy.exportCsv.mockReturnValue(throwError(() => new Error('CSV error')));
      component.onDownloadCsv(mockMonat, consumerEinheit);
      expect(component.messageType).toBe('error');
      expect(component.message).toBe('EXPORT_CSV_FEHLER');
    });
  });

  describe('toggleMonthPanel', () => {
    it('should expand a collapsed month panel', () => {
      component.toggleMonthPanel(0);
      expect(component.isMonthPanelExpanded(0)).toBe(true);
    });

    it('should collapse an expanded month panel', () => {
      component.toggleMonthPanel(0);
      component.toggleMonthPanel(0);
      expect(component.isMonthPanelExpanded(0)).toBe(false);
    });

    it('should handle multiple month panels independently', () => {
      component.toggleMonthPanel(0);
      component.toggleMonthPanel(1);
      component.toggleMonthPanel(0);
      expect(component.isMonthPanelExpanded(0)).toBe(false);
      expect(component.isMonthPanelExpanded(1)).toBe(true);
    });

    it('should not touch the details toggle of the same month', () => {
      // Zwei unabhaengige Zustaende: das Panel selbst und die Detail-Sektion darin.
      component.toggleMonthPanel(0);
      expect(component.isMonthExpanded(0)).toBe(false);
    });
  });

  describe('toggleMonthDetails', () => {
    it('should add index to expandedMonths when not present', () => {
      component.toggleMonthDetails(0);
      expect(component.expandedMonths.has(0)).toBe(true);
    });

    it('should remove index from expandedMonths when already present', () => {
      component.expandedMonths.add(0);
      component.toggleMonthDetails(0);
      expect(component.expandedMonths.has(0)).toBe(false);
    });

    it('should handle multiple months independently', () => {
      component.toggleMonthDetails(0);
      component.toggleMonthDetails(1);
      expect(component.expandedMonths.has(0)).toBe(true);
      expect(component.expandedMonths.has(1)).toBe(true);
      component.toggleMonthDetails(0);
      expect(component.expandedMonths.has(0)).toBe(false);
      expect(component.expandedMonths.has(1)).toBe(true);
    });
  });

  describe('isMonthExpanded', () => {
    it('should return false when month is not expanded', () => {
      expect(component.isMonthExpanded(0)).toBe(false);
    });

    it('should return true when month is expanded', () => {
      component.expandedMonths.add(0);
      expect(component.isMonthExpanded(0)).toBe(true);
    });
  });

  describe('toggleGlobalDetails', () => {
    it('should toggle expandedGlobalDetails from false to true', () => {
      component.expandedGlobalDetails = false;
      component.toggleGlobalDetails();
      expect(component.expandedGlobalDetails).toBe(true);
    });

    it('should toggle expandedGlobalDetails from true to false', () => {
      component.expandedGlobalDetails = true;
      component.toggleGlobalDetails();
      expect(component.expandedGlobalDetails).toBe(false);
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
        translationServiceSpy.translate.mockClear();
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

    it('should group thousands with apostrophe', () => {
      expect(component.formatNumber(1234.567)).toBe('1\'234.567');
    });

    it('should group millions with two apostrophes', () => {
      expect(component.formatNumber(1234567)).toBe('1\'234\'567.000');
    });

    it('should format negative numbers with sign before grouped value', () => {
      expect(component.formatNumber(-1234.5)).toBe('-1\'234.500');
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

    it('should group thousands and prefix positive with "+"', () => {
      expect(component.formatDifferenz(1234.567)).toBe('+1\'234.567');
    });

    it('should group thousands for negative values without "+" prefix', () => {
      expect(component.formatDifferenz(-1234567)).toBe('-1\'234\'567.000');
    });
  });

  describe('hasAbweichungen', () => {
    it('should return false when all sums are equal', () => {
      const monat: MonatsStatistik = { ...mockMonat, summenCDGleich: true, summenCEGleich: true, summenDEGleich: true };
      expect(component.hasAbweichungen(monat)).toBe(false);
    });

    it('should return true when CD sums differ', () => {
      const monat: MonatsStatistik = { ...mockMonat, summenCDGleich: false, summenCEGleich: true, summenDEGleich: true };
      expect(component.hasAbweichungen(monat)).toBe(true);
    });

    it('should return true when CE sums differ', () => {
      const monat: MonatsStatistik = { ...mockMonat, summenCDGleich: true, summenCEGleich: false, summenDEGleich: true };
      expect(component.hasAbweichungen(monat)).toBe(true);
    });

    it('should return true when DE sums differ', () => {
      const monat: MonatsStatistik = { ...mockMonat, summenCDGleich: true, summenCEGleich: true, summenDEGleich: false };
      expect(component.hasAbweichungen(monat)).toBe(true);
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

  describe('getKennzahlen', () => {
    // en-dash (U+2013) used by the component for null values
    const ENDASH = '–';

    const monatOhneBatterie: MonatsStatistik = {
      ...mockMonat,
      batterieKennzahlenVerfuegbar: false,
      batterieGemessen: false,
      batterieNetto: null,
      batterieGeladen: null,
      batterieEntladen: null,
      batterieWirkungsgrad: null
    };

    const monatOhneBilanz: MonatsStatistik = {
      ...monatOhneBatterie,
      bilanzKennzahlenVerfuegbar: false,
      autarkiegradGemessen: null,
      netzbezugsquoteGemessen: null
    };

    it('should return 5 rows without battery and without a measured grid supply', () => {
      expect(component.getKennzahlen(monatOhneBilanz).length).toBe(5);
    });

    it('should return 7 rows when only the measured grid supply is available', () => {
      expect(component.getKennzahlen(monatOhneBatterie).length).toBe(7);
    });

    it('should return 11 rows when battery and measured grid supply are available', () => {
      expect(component.getKennzahlen(mockMonat).length).toBe(11);
    });

    it('should mark the battery figures as calculated when they come from the balance', () => {
      const batterie = component.getKennzahlen(mockMonat)
        .filter(z => z.labelKey.startsWith('KENNZAHL_BATTERIE'));

      expect(batterie.length).toBe(4);
      expect(batterie.every(z => z.berechnet)).toBe(true);
    });

    it('should not mark the battery figures when they are measured at the storage unit', () => {
      // Ein Zaehlerstand ist kein Residuum der Bilanz - der Hinweis "berechnet" waere falsch.
      const monat = { ...mockMonat, batterieGemessen: true };

      const batterie = component.getKennzahlen(monat)
        .filter(z => z.labelKey.startsWith('KENNZAHL_BATTERIE'));

      expect(batterie.length).toBe(4);
      expect(batterie.some(z => z.berechnet)).toBe(false);
    });

    it('should not include any battery rows when not available', () => {
      const labels = component.getKennzahlen(monatOhneBatterie).map(z => z.labelKey);
      expect(labels).toEqual([
        'KENNZAHL_AUTARKIEGRAD',
        'KENNZAHL_AUTARKIEGRAD_GEMESSEN',
        'KENNZAHL_EIGENVERBRAUCHSQUOTE',
        'KENNZAHL_NETZBEZUGSQUOTE',
        'KENNZAHL_NETZBEZUGSQUOTE_GEMESSEN',
        'KENNZAHL_EINSPEISEQUOTE',
        'KENNZAHL_ZEV_EIGENVERBRAUCH'
      ]);
    });

    it('should omit the measured rows when there is no balance unit', () => {
      const labels = component.getKennzahlen(monatOhneBilanz).map(z => z.labelKey);
      expect(labels).toEqual([
        'KENNZAHL_AUTARKIEGRAD',
        'KENNZAHL_EIGENVERBRAUCHSQUOTE',
        'KENNZAHL_NETZBEZUGSQUOTE',
        'KENNZAHL_EINSPEISEQUOTE',
        'KENNZAHL_ZEV_EIGENVERBRAUCH'
      ]);
    });

    it('should place each measured figure right after its calculated counterpart', () => {
      // Nebeneinander gelesen faellt die Differenz auf - und genau die ist der Batterie-Anteil.
      const labels = component.getKennzahlen(mockMonat).map(z => z.labelKey);
      expect(labels.indexOf('KENNZAHL_AUTARKIEGRAD_GEMESSEN'))
        .toBe(labels.indexOf('KENNZAHL_AUTARKIEGRAD') + 1);
      expect(labels.indexOf('KENNZAHL_NETZBEZUGSQUOTE_GEMESSEN'))
        .toBe(labels.indexOf('KENNZAHL_NETZBEZUGSQUOTE') + 1);
    });

    it('should format the measured figures as percentages', () => {
      const zeilen = component.getKennzahlen(mockMonat);
      const gemessen = zeilen.find(z => z.labelKey === 'KENNZAHL_AUTARKIEGRAD_GEMESSEN');
      expect(gemessen?.value).toBe('84.4');
      expect(gemessen?.unit).toBe('%');
      // Gemessen ist nicht geschaetzt - der "berechnet"-Hinweis gehoert hier nicht hin.
      expect(gemessen?.berechnet).toBe(false);
    });

    it('should mark the measured figures when the grid supply has gaps', () => {
      const mitLuecke = { ...mockMonat, bilanzBezugLueckenhaft: true };
      const zeilen = component.getKennzahlen(mitLuecke);

      expect(zeilen.find(z => z.labelKey === 'KENNZAHL_AUTARKIEGRAD_GEMESSEN')?.luecke).toBe(true);
      expect(zeilen.find(z => z.labelKey === 'KENNZAHL_NETZBEZUGSQUOTE_GEMESSEN')?.luecke).toBe(true);
    });

    it('should mark the calculated figures too when the distribution skipped intervals', () => {
      // Im Bilanzmodus stammt auch der ZEV-Anteil aus den Bilanzdaten - dieselbe Luecke verzerrt
      // beide Seiten, in entgegengesetzte Richtungen.
      const mitLuecke = { ...mockMonat, bilanzBezugLueckenhaft: true, verteilungLueckenhaft: true };
      const zeilen = component.getKennzahlen(mitLuecke);

      expect(zeilen.find(z => z.labelKey === 'KENNZAHL_AUTARKIEGRAD')?.luecke).toBe(true);
      expect(zeilen.find(z => z.labelKey === 'KENNZAHL_NETZBEZUGSQUOTE')?.luecke).toBe(true);
    });

    it('should leave the calculated figures unmarked outside the balance mode', () => {
      // Ohne Bilanzmodus kommt der ZEV-Anteil aus der Producer-Verteilung und ist von fehlenden
      // BEZUG-Werten nicht betroffen.
      const mitLuecke = { ...mockMonat, bilanzBezugLueckenhaft: true, verteilungLueckenhaft: false };
      const zeilen = component.getKennzahlen(mitLuecke);

      expect(zeilen.find(z => z.labelKey === 'KENNZAHL_AUTARKIEGRAD')?.luecke).toBe(false);
      expect(zeilen.find(z => z.labelKey === 'KENNZAHL_AUTARKIEGRAD_GEMESSEN')?.luecke).toBe(true);
    });

    it('should explain each gap with its own direction', () => {
      // Zwei Hinweise, weil die Verzerrung in entgegengesetzte Richtungen zeigt: gemessen zu
      // guenstig, gerechnet zu unguenstig - der wahre Wert liegt dazwischen.
      const mitLuecke = { ...mockMonat, bilanzBezugLueckenhaft: true, verteilungLueckenhaft: true };
      const zeilen = component.getKennzahlen(mitLuecke);

      expect(zeilen.find(z => z.labelKey === 'KENNZAHL_AUTARKIEGRAD')?.lueckeHintKey)
        .toBe('KENNZAHL_LUECKE_VERTEILUNG_HINWEIS');
      expect(zeilen.find(z => z.labelKey === 'KENNZAHL_AUTARKIEGRAD_GEMESSEN')?.lueckeHintKey)
        .toBe('KENNZAHL_LUECKE_MESSUNG_HINWEIS');
    });

    it('should not mark anything when the grid supply is complete', () => {
      const zeilen = component.getKennzahlen(mockMonat);
      expect(zeilen.find(z => z.labelKey === 'KENNZAHL_AUTARKIEGRAD_GEMESSEN')?.luecke).toBe(false);
      expect(zeilen.find(z => z.labelKey === 'KENNZAHL_AUTARKIEGRAD')?.luecke).toBe(false);
    });

    it('should include battery rows in order when available', () => {
      const labels = component.getKennzahlen(mockMonat).map(z => z.labelKey);
      expect(labels).toEqual([
        'KENNZAHL_AUTARKIEGRAD',
        'KENNZAHL_AUTARKIEGRAD_GEMESSEN',
        'KENNZAHL_EIGENVERBRAUCHSQUOTE',
        'KENNZAHL_NETZBEZUGSQUOTE',
        'KENNZAHL_NETZBEZUGSQUOTE_GEMESSEN',
        'KENNZAHL_EINSPEISEQUOTE',
        'KENNZAHL_ZEV_EIGENVERBRAUCH',
        'KENNZAHL_BATTERIE_NETTO',
        'KENNZAHL_BATTERIE_GELADEN',
        'KENNZAHL_BATTERIE_ENTLADEN',
        'KENNZAHL_BATTERIE_WIRKUNGSGRAD'
      ]);
    });

    it('should derive hintKey by appending _HINWEIS to labelKey', () => {
      component.getKennzahlen(mockMonat).forEach(zeile => {
        expect(zeile.hintKey).toBe(zeile.labelKey + '_HINWEIS');
      });
    });

    it('should format quota rows as percent with 1 decimal and "%" unit', () => {
      const zeilen = component.getKennzahlen(mockMonat);
      const autarkie = zeilen.find(z => z.labelKey === 'KENNZAHL_AUTARKIEGRAD')!;
      expect(autarkie.value).toBe('83.3'); // 0.8333 * 100
      expect(autarkie.unit).toBe('%');

      const eigenverbrauch = zeilen.find(z => z.labelKey === 'KENNZAHL_EIGENVERBRAUCHSQUOTE')!;
      expect(eigenverbrauch.value).toBe('80.0'); // 0.8 * 100
      expect(eigenverbrauch.unit).toBe('%');
    });

    it('should render quota rows as "–" with empty unit when value is null', () => {
      const monat: MonatsStatistik = { ...mockMonat, autarkiegrad: null };
      const autarkie = component.getKennzahlen(monat).find(z => z.labelKey === 'KENNZAHL_AUTARKIEGRAD')!;
      expect(autarkie.value).toBe(ENDASH);
      expect(autarkie.unit).toBe('');
    });

    it('should format ZEV-Eigenverbrauch with 3 decimals and "kWh" unit', () => {
      const zeile = component.getKennzahlen(mockMonat).find(z => z.labelKey === 'KENNZAHL_ZEV_EIGENVERBRAUCH')!;
      expect(zeile.value).toBe('750.000');
      expect(zeile.unit).toBe('kWh');
    });

    it('should render ZEV-Eigenverbrauch as "–" when null', () => {
      const monat: MonatsStatistik = { ...mockMonat, zevEigenverbrauch: null };
      const zeile = component.getKennzahlen(monat).find(z => z.labelKey === 'KENNZAHL_ZEV_EIGENVERBRAUCH')!;
      expect(zeile.value).toBe(ENDASH);
      expect(zeile.unit).toBe('');
    });

    it('should mark quota and ZEV rows as not calculated (berechnet=false)', () => {
      const zeilen = component.getKennzahlen(mockMonat);
      const nichtBerechnet = zeilen.slice(0, 5);
      nichtBerechnet.forEach(z => expect(z.berechnet).toBe(false));
    });

    it('should mark battery rows as calculated (berechnet=true)', () => {
      // Ueber den Namen statt ueber die Position: Vor den Batterie-Zeilen stehen inzwischen
      // sieben Zeilen, ein `slice(5)` waere beim naechsten Zusatz wieder falsch.
      const batterieZeilen = component.getKennzahlen(mockMonat)
        .filter(z => z.labelKey.startsWith('KENNZAHL_BATTERIE_'));
      expect(batterieZeilen.length).toBe(4);
      batterieZeilen.forEach(z => expect(z.berechnet).toBe(true));
    });

    it('should prefix positive Batterie-Netto with "+" and unit "kWh"', () => {
      const zeile = component.getKennzahlen(mockMonat).find(z => z.labelKey === 'KENNZAHL_BATTERIE_NETTO')!;
      expect(zeile.value).toBe('+100.000'); // batterieNetto = 100
      expect(zeile.unit).toBe('kWh');
    });

    it('should not prefix negative Batterie-Netto with "+"', () => {
      const monat: MonatsStatistik = { ...mockMonat, batterieNetto: -50 };
      const zeile = component.getKennzahlen(monat).find(z => z.labelKey === 'KENNZAHL_BATTERIE_NETTO')!;
      expect(zeile.value).toBe('-50.000');
    });

    it('should format Batterie-Geladen/-Entladen as kWh with 3 decimals', () => {
      const zeilen = component.getKennzahlen(mockMonat);
      const geladen = zeilen.find(z => z.labelKey === 'KENNZAHL_BATTERIE_GELADEN')!;
      const entladen = zeilen.find(z => z.labelKey === 'KENNZAHL_BATTERIE_ENTLADEN')!;
      expect(geladen.value).toBe('120.000');
      expect(geladen.unit).toBe('kWh');
      expect(entladen.value).toBe('20.000');
      expect(entladen.unit).toBe('kWh');
    });

    it('should format Batterie-Wirkungsgrad as percent with 1 decimal', () => {
      const zeile = component.getKennzahlen(mockMonat).find(z => z.labelKey === 'KENNZAHL_BATTERIE_WIRKUNGSGRAD')!;
      expect(zeile.value).toBe('16.7'); // 0.1667 * 100
      expect(zeile.unit).toBe('%');
    });

    it('should group thousands in a large ZEV-Eigenverbrauch value', () => {
      const monat: MonatsStatistik = { ...mockMonat, zevEigenverbrauch: 12345.678 };
      const zeile = component.getKennzahlen(monat).find(z => z.labelKey === 'KENNZAHL_ZEV_EIGENVERBRAUCH')!;
      expect(zeile.value).toBe('12\'345.678');
    });
  });

  describe('isBilanz', () => {
    it('should return false when statistik is null', () => {
      component.statistik = null;
      expect(component.isBilanz).toBe(false);
    });

    it('should return false when verteilmodus is PRODUCER_MESSUNG', () => {
      component.statistik = { ...mockStatistik, verteilmodus: 'PRODUCER_MESSUNG' };
      expect(component.isBilanz).toBe(false);
    });

    it('should return false when verteilmodus is undefined', () => {
      component.statistik = { ...mockStatistik, verteilmodus: undefined };
      expect(component.isBilanz).toBe(false);
    });

    it('should return true when verteilmodus is BILANZ', () => {
      component.statistik = { ...mockStatistik, verteilmodus: 'BILANZ' };
      expect(component.isBilanz).toBe(true);
    });
  });

  describe('message auto-dismiss', () => {
    it('should auto-dismiss success messages after 5s', fakeAsync(() => {
      // Das Laden zeigt seit dem 14.10.2026 keine Erfolgsmeldung mehr - direkt erzeugt
      component.showMessage('OK', 'success');
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

  // ==================== Gesamt-Panel (Specs/Statistik.md) ====================

  describe('Gesamt-Panel', () => {
    const gesamt: MonatsStatistik = {
      ...mockMonat, jahr: 0, monat: 0, von: '2024-01-01', bis: '2024-03-31'
    };

    function zeige(statistik: Statistik): HTMLElement {
      component.statistik = statistik;
      component.loading = false;
      component.expandedMonthPanels.clear();
      component.expandedMonthPanels.add(component.GESAMT);
      fixture.detectChanges();
      return fixture.nativeElement as HTMLElement;
    }

    it('should render the total panel above the months', () => {
      const el = zeige({ ...mockStatistik, gesamt });

      const panels = el.querySelectorAll('.zev-panel--month');
      expect(panels.length).toBe(2);
      expect(panels[0].textContent).toContain('STATISTIK_GESAMTER_ZEITRAUM');
    });

    it('should show the total panel expanded and the months collapsed', () => {
      const el = zeige({ ...mockStatistik, gesamt });

      expect(el.querySelector('#gesamt')).not.toBeNull();
      expect(el.querySelector('#monat-0')).toBeNull();
    });

    /**
     * Gesamt-Panel und Monat verwenden <b>dieselbe</b> Vorlage. Zwei Kopien liefen beim nächsten
     * Ausbau eines Monats unbemerkt auseinander.
     */
    it('should render the same content as a month', () => {
      const el = zeige({ ...mockStatistik, gesamt });

      const inhaltGesamt = el.querySelector('#gesamt .zev-table--bars');
      expect(inhaltGesamt).not.toBeNull();
    });

    it('should show the selected range in the header', () => {
      const el = zeige({ ...mockStatistik, gesamt });

      const kopf = el.querySelectorAll('.zev-panel--month')[0].querySelector('.zev-collapsible__header');
      expect(kopf?.textContent).toContain('STATISTIK_GESAMTER_ZEITRAUM');
      expect(kopf?.querySelector('.zev-panel__subtitle')?.textContent?.trim())
        .toBe('(01.01.2024 - 31.03.2024)');
    });

    it('should not render the total panel without totals', () => {
      const el = zeige({ ...mockStatistik, gesamt: undefined });

      expect(el.querySelectorAll('.zev-panel--month').length).toBe(1);
    });

    /** „Details anzeigen" hat im Gesamt-Panel einen eigenen Zustand — nicht den von Monat 0. */
    it('should keep the details state separate from the first month', () => {
      component.toggleMonthDetails(component.GESAMT);

      expect(component.isMonthExpanded(component.GESAMT)).toBe(true);
      expect(component.isMonthExpanded(0)).toBe(false);
    });
  });

  describe('Ladeanzeige', () => {
    it('should show the loading indicator while loading', () => {
      component.loading = true;
      fixture.detectChanges();
      const anzeige = fixture.nativeElement.querySelector('app-loading-indicator');
      expect(anzeige).not.toBeNull();
      expect(anzeige.textContent).toContain('STATISTIK_WIRD_BERECHNET');
    });

    it('should not show the loading indicator when idle', () => {
      component.loading = false;
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('app-loading-indicator')).toBeNull();
    });
  });

  describe('Blättern', () => {
    it('should move a month forward and load immediately', () => {
      const laden = vi.spyOn(component, 'onSubmit').mockImplementation(() => {});
      component.dateFrom = '2026-09-01';
      component.dateTo = '2026-09-30';

      component.onBlaettern(1);

      expect(component.dateFrom).toBe('2026-10-01');
      expect(component.dateTo).toBe('2026-10-31');
      expect(laden).toHaveBeenCalled();
    });

    it('should move a day backwards', () => {
      vi.spyOn(component, 'onSubmit').mockImplementation(() => {});
      component.dateFrom = '2026-10-13';
      component.dateTo = '2026-10-13';

      component.onBlaettern(-1);

      expect(component.dateFrom).toBe('2026-10-12');
      expect(component.dateTo).toBe('2026-10-12');
    });

    it('should not move while loading or with an invalid range', () => {
      const laden = vi.spyOn(component, 'onSubmit').mockImplementation(() => {});
      component.dateFrom = '2026-10-14';
      component.dateTo = '2026-10-01';
      component.onBlaettern(1);
      expect(component.dateFrom).toBe('2026-10-14');

      component.dateFrom = '2026-10-01';
      component.dateTo = '2026-10-07';
      component.loading = true;
      component.onBlaettern(1);
      expect(component.dateFrom).toBe('2026-10-01');
      expect(laden).not.toHaveBeenCalled();
    });

    it('should render chevron icons with translated title and aria-label', () => {
      const pruefe = (id: string, icon: string, key: string) => {
        const knopf = fixture.debugElement.query(By.css('#' + id));
        expect(knopf).not.toBeNull();
        const el = knopf.nativeElement as HTMLButtonElement;
        expect(el.getAttribute('title')).toBe(key);
        expect(el.getAttribute('aria-label')).toBe(key);
        const symbol = knopf.query(By.directive(IconComponent));
        expect(symbol.componentInstance.name).toBe(icon);
      };

      pruefe('zeitraumZurueck', 'chevron-left', 'ZEITRAUM_ZURUECK');
      pruefe('zeitraumVor', 'chevron-right', 'ZEITRAUM_VOR');
      expect(translationServiceSpy.translate).toHaveBeenCalledWith('ZEITRAUM_ZURUECK');
      expect(translationServiceSpy.translate).toHaveBeenCalledWith('ZEITRAUM_VOR');
    });
  });

  // ==================== Monats-Panels (FR-10) ====================

  describe('Monats-Panels', () => {
    it('should show month name, year, range and status dot in a collapsed header', () => {
      const el = rendere(mockStatistik);

      expect(el.querySelector('#monat-0')).toBeNull();
      const kopf = el.querySelector('.zev-panel--month .zev-collapsible__header') as HTMLElement;
      expect(kopf.textContent).toContain('FEBRUAR 2024');
      expect(kopf.querySelector('.zev-panel__subtitle')?.textContent?.trim())
        .toBe('(01.02.2024 - 29.02.2024)');
      expect(kopf.querySelector('.zev-status-dot')?.classList).toContain('zev-status-dot--success');
    });

    it('should show an error dot in the collapsed header of an incomplete month', () => {
      const el = rendere({ ...mockStatistik, monate: [{ ...mockMonat, datenVollstaendig: false }] });

      const kopf = el.querySelector('.zev-panel--month .zev-collapsible__header') as HTMLElement;
      expect(kopf.querySelector('.zev-status-dot')?.classList).toContain('zev-status-dot--error');
    });
  });

  // ==================== Hinweise in der Summen-Tabelle (FR-9) ====================

  describe('Summen-Tabelle Hinweise', () => {
    function zeilen(el: HTMLElement): HTMLTableRowElement[] {
      return Array.from(el.querySelectorAll('#monat-0 .zev-table--bars tbody tr'));
    }

    it('should give each of the five standard rows its own hint', () => {
      const ohneBilanz: MonatsStatistik = {
        ...mockMonat, bilanzBezugName: null, bilanzRuecklieferungName: null
      };
      const el = rendere({ ...mockStatistik, monate: [ohneBilanz] }, [0]);

      const titel = zeilen(el).map(z => z.getAttribute('title'));
      expect(titel).toEqual([
        'PRODUKTION_TOTAL_HINWEIS',
        'VERBRAUCH_TOTAL_HINWEIS',
        'ZEV_PRODUCER_HINWEIS',
        'ZEV_CONSUMER_HINWEIS',
        'ZEV_CONSUMER_BERECHNET_HINWEIS'
      ]);
      expect(new Set(titel).size).toBe(5);
    });

    it('should add hinted balance rows only when balance units exist', () => {
      const el = rendere(mockStatistik, [0]);

      const titel = zeilen(el).map(z => z.getAttribute('title'));
      expect(titel.length).toBe(7);
      expect(titel.slice(5)).toEqual([
        'STATISTIK_BILANZ_RUECKLIEFERUNG_HINWEIS',
        'STATISTIK_BILANZ_BEZUG_HINWEIS'
      ]);
    });

    it('should not render empty tooltips', () => {
      const ohneBilanz: MonatsStatistik = {
        ...mockMonat, bilanzBezugName: null, bilanzRuecklieferungName: null
      };
      for (const statistik of [mockStatistik, { ...mockStatistik, monate: [ohneBilanz] }]) {
        const el = rendere(statistik, [0]);
        for (const zeile of zeilen(el)) {
          expect(zeile.getAttribute('title')?.trim()).toBeTruthy();
        }
      }
    });
  });

  // ==================== Summen-Vergleich je Verteilmodus (FR-12 bis FR-16) ====================

  describe('Summen-Vergleich', () => {
    function vergleich(el: HTMLElement): { titel: boolean; eintraege: number } {
      const ueberschriften = Array.from(el.querySelectorAll('#monat-0 h4')).map(h => h.textContent?.trim());
      return {
        titel: ueberschriften.includes('SUMMEN_VERGLEICH'),
        eintraege: el.querySelectorAll('#monat-0 .zev-comparison-item').length
      };
    }

    it('should hide the sum comparison in the balance mode', () => {
      const el = rendere({ ...mockStatistik, verteilmodus: 'BILANZ' }, [0]);

      expect(vergleich(el)).toEqual({ titel: false, eintraege: 0 });
      expect(el.querySelector('#monat-0')?.textContent).toContain('STATISTIK_KENNZAHLEN');
    });

    it('should show the sum comparison in the producer measurement mode', () => {
      const el = rendere({ ...mockStatistik, verteilmodus: 'PRODUCER_MESSUNG' }, [0]);

      const ergebnis = vergleich(el);
      expect(ergebnis.titel).toBe(true);
      expect(ergebnis.eintraege).toBeGreaterThan(0);
      expect(el.querySelector('#monat-0')?.textContent).toContain('STATISTIK_KENNZAHLEN');
    });
  });
});
