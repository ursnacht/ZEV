import { createSpyObj, SpyObj } from '../../../testing/spy';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { fakeAsync, tick } from '../../../testing/fake-async';
import { RechnungenComponent } from './rechnungen.component';
import { EinheitService } from '../../services/einheit.service';
import { RechnungService, GeneratedRechnung, GenerateResponse } from '../../services/rechnung.service';
import { TranslationService } from '../../services/translation.service';
import { Einheit, EinheitTyp } from '../../models/einheit.model';
import { of, throwError } from 'rxjs';

describe('RechnungenComponent', () => {
  let component: RechnungenComponent;
  let fixture: ComponentFixture<RechnungenComponent>;
  let einheitServiceSpy: SpyObj<EinheitService>;
  let rechnungServiceSpy: SpyObj<RechnungService>;
  let translationServiceSpy: SpyObj<TranslationService>;

  const mockConsumer: Einheit = { id: 1, name: 'Wohnung A', typ: EinheitTyp.CONSUMER };
  const mockConsumer2: Einheit = { id: 2, name: 'Wohnung B', typ: EinheitTyp.CONSUMER };
  const mockProducer: Einheit = { id: 3, name: 'Solar Anlage', typ: EinheitTyp.PRODUCER };

  const mockEinheiten: Einheit[] = [mockProducer, mockConsumer2, mockConsumer];

  const mockRechnung: GeneratedRechnung = {
    einheitId: 1,
    einheitName: 'Wohnung A',
    mieterName: 'Max Mustermann',
    endBetrag: 123.45,
    filename: 'rechnung-1.pdf',
    downloadKey: 'key-1'
  };

  const mockGenerateResponse: GenerateResponse = {
    rechnungen: [mockRechnung],
    count: 1
  };

  beforeEach(async () => {
    einheitServiceSpy = createSpyObj<EinheitService>('EinheitService', ['getAllEinheiten']);
    einheitServiceSpy.getAllEinheiten.mockReturnValue(of(mockEinheiten));

    rechnungServiceSpy = createSpyObj<RechnungService>('RechnungService', ['generateRechnungen', 'downloadRechnung']);
    rechnungServiceSpy.generateRechnungen.mockReturnValue(of(mockGenerateResponse));
    rechnungServiceSpy.downloadRechnung.mockReturnValue(undefined);

    translationServiceSpy = createSpyObj<TranslationService>('TranslationService', ['translate', 'getCurrentLanguage']);
    translationServiceSpy.translate.mockImplementation((key: string) => key);
    translationServiceSpy.getCurrentLanguage.mockReturnValue('de');

    await TestBed.configureTestingModule({
      imports: [RechnungenComponent],
      providers: [
        { provide: EinheitService, useValue: einheitServiceSpy },
        { provide: RechnungService, useValue: rechnungServiceSpy },
        { provide: TranslationService, useValue: translationServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(RechnungenComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    beforeEach(() => {
      vi.useFakeTimers();
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('should set default dates', () => {
      expect(component.dateFrom).toBeTruthy();
      expect(component.dateTo).toBeTruthy();
      expect(component.dateFrom.length).toBe(10);
      expect(component.dateTo.length).toBe(10);
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

    it('should preselect Q3 when current quarter is Q4', () => {
      vi.setSystemTime(new Date(2026, 10, 1)); // 01.11.2026 → Q4 → Vorquartal Q3/2026
      component.ngOnInit();
      expect(component.dateFrom).toBe('2026-07-01');
      expect(component.dateTo).toBe('2026-09-30');
    });
  });

  describe('onSelectionChange', () => {
    it('should update selectedEinheitIds from emitted einheiten', () => {
      component.onSelectionChange([mockConsumer, mockConsumer2]);
      expect(component.selectedEinheitIds.has(1)).toBe(true);
      expect(component.selectedEinheitIds.has(2)).toBe(true);
      expect(component.selectedEinheitIds.size).toBe(2);
    });

    it('should clear selectedEinheitIds when empty array emitted', () => {
      component.onSelectionChange([mockConsumer]);
      component.onSelectionChange([]);
      expect(component.selectedEinheitIds.size).toBe(0);
    });
  });

  describe('onDateFromChange', () => {
    it('should set dateTo to last day of dateFrom month', () => {
      component.dateFrom = '2024-02-15';
      component.onDateFromChange();
      expect(component.dateTo).toBe('2024-02-29');
    });

    it('should set dateTo for non-leap year February', () => {
      component.dateFrom = '2023-02-10';
      component.onDateFromChange();
      expect(component.dateTo).toBe('2023-02-28');
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

  describe('canGenerate', () => {
    it('should return false when dateFrom is empty', () => {
      component.dateFrom = '';
      component.dateTo = '2024-12-31';
      component.selectedEinheitIds.add(1);
      expect(component.canGenerate()).toBe(false);
    });

    it('should return false when dateTo is empty', () => {
      component.dateFrom = '2024-01-01';
      component.dateTo = '';
      component.selectedEinheitIds.add(1);
      expect(component.canGenerate()).toBe(false);
    });

    it('should return false when no einheiten selected', () => {
      component.dateFrom = '2024-01-01';
      component.dateTo = '2024-12-31';
      expect(component.canGenerate()).toBe(false);
    });

    it('should return false when generating is in progress', () => {
      component.dateFrom = '2024-01-01';
      component.dateTo = '2024-12-31';
      component.selectedEinheitIds.add(1);
      component.generating = true;
      expect(component.canGenerate()).toBe(false);
    });

    it('should return true when all conditions are met', () => {
      component.dateFrom = '2024-01-01';
      component.dateTo = '2024-12-31';
      component.selectedEinheitIds.add(1);
      expect(component.canGenerate()).toBe(true);
    });
  });

  describe('onGenerate', () => {
    beforeEach(() => {
      component.dateFrom = '2024-01-01';
      component.dateTo = '2024-12-31';
      component.selectedEinheitIds.add(1);
    });

    it('should call rechnungService.generateRechnungen with correct params', () => {
      component.onGenerate();
      expect(rechnungServiceSpy.generateRechnungen).toHaveBeenCalledWith({
        von: '2024-01-01',
        bis: '2024-12-31',
        einheitIds: [1],
        sprache: 'de'
      });
    });

    it('should populate generatedRechnungen on success', () => {
      component.onGenerate();
      expect(component.generatedRechnungen).toEqual([mockRechnung]);
    });

    it('should show success message on success', fakeAsync(() => {
      component.onGenerate();
      expect(component.messageType).toBe('success');
      expect(component.message).toContain('RECHNUNGEN_GENERIERT');
      tick(5000);
      expect(component.message).toBe('');
    }));

    it('should show error message on failure', () => {
      rechnungServiceSpy.generateRechnungen.mockReturnValue(
        throwError(() => ({ error: { error: 'Tarif fehlt' } }))
      );
      component.onGenerate();
      expect(component.messageType).toBe('error');
      expect(component.message).toContain('Tarif fehlt');
    });

    it('should show error message when canGenerate is false', () => {
      component.selectedEinheitIds.clear();
      component.onGenerate();
      expect(component.messageType).toBe('error');
      expect(rechnungServiceSpy.generateRechnungen).not.toHaveBeenCalled();
    });

    it('should show error when dateFrom is after dateTo', () => {
      component.dateFrom = '2024-12-31';
      component.dateTo = '2024-01-01';
      component.onGenerate();
      expect(component.messageType).toBe('error');
      expect(rechnungServiceSpy.generateRechnungen).not.toHaveBeenCalled();
    });

    it('should reset generating flag after success', () => {
      component.onGenerate();
      expect(component.generating).toBe(false);
    });

    it('should reset generating flag after error', () => {
      rechnungServiceSpy.generateRechnungen.mockReturnValue(
        throwError(() => ({ message: 'error' }))
      );
      component.onGenerate();
      expect(component.generating).toBe(false);
    });
  });

  describe('onDownload', () => {
    it('should call rechnungService.downloadRechnung', () => {
      component.onDownload(mockRechnung);
      expect(rechnungServiceSpy.downloadRechnung).toHaveBeenCalledWith('key-1', 'rechnung-1.pdf');
    });
  });

  describe('formatBetrag', () => {
    it('should format number with two decimal places', () => {
      expect(component.formatBetrag(123.45)).toBe('123.45');
    });

    it('should pad with zeros for whole numbers', () => {
      expect(component.formatBetrag(100)).toBe('100.00');
    });

    it('should truncate beyond two decimal places', () => {
      expect(component.formatBetrag(1.234)).toBe('1.23');
    });
  });

  describe('getTotalBetrag', () => {
    it('should return 0 when no rechnungen generated', () => {
      component.generatedRechnungen = [];
      expect(component.getTotalBetrag()).toBe(0);
    });

    it('should sum endBetrag of all generated rechnungen', () => {
      component.generatedRechnungen = [
        { ...mockRechnung, endBetrag: 100.50 },
        { ...mockRechnung, endBetrag: 49.50 },
        { ...mockRechnung, endBetrag: 0.25 }
      ];
      expect(component.getTotalBetrag()).toBeCloseTo(150.25, 2);
    });

    it('should match formatBetrag output for the total', () => {
      component.generatedRechnungen = [
        { ...mockRechnung, endBetrag: 10 },
        { ...mockRechnung, endBetrag: 5.5 }
      ];
      expect(component.formatBetrag(component.getTotalBetrag())).toBe('15.50');
    });
  });

  describe('error message persistence', () => {
    it('should not auto-dismiss error messages', fakeAsync(() => {
      component.dateFrom = '';
      component.dateTo = '2024-12-31';
      component.selectedEinheitIds.add(1);
      component.onGenerate();
      expect(component.messageType).toBe('error');
      tick(5000);
      expect(component.messageType).toBe('error');
      expect(component.message).not.toBe('');
    }));

    it('should auto-dismiss success messages after 5s', fakeAsync(() => {
      component.dateFrom = '2024-01-01';
      component.dateTo = '2024-12-31';
      component.selectedEinheitIds.add(1);
      component.onGenerate();
      expect(component.messageType).toBe('success');
      tick(5000);
      expect(component.message).toBe('');
      expect(component.messageType).toBe('');
    }));
  });
});
