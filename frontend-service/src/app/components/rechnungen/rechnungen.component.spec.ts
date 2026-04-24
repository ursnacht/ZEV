import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { RechnungenComponent } from './rechnungen.component';
import { EinheitService } from '../../services/einheit.service';
import { RechnungService, GeneratedRechnung, GenerateResponse } from '../../services/rechnung.service';
import { TranslationService } from '../../services/translation.service';
import { Einheit, EinheitTyp } from '../../models/einheit.model';
import { of, throwError } from 'rxjs';

describe('RechnungenComponent', () => {
  let component: RechnungenComponent;
  let fixture: ComponentFixture<RechnungenComponent>;
  let einheitServiceSpy: jasmine.SpyObj<EinheitService>;
  let rechnungServiceSpy: jasmine.SpyObj<RechnungService>;
  let translationServiceSpy: jasmine.SpyObj<TranslationService>;

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
    einheitServiceSpy = jasmine.createSpyObj('EinheitService', ['getAllEinheiten']);
    einheitServiceSpy.getAllEinheiten.and.returnValue(of(mockEinheiten));

    rechnungServiceSpy = jasmine.createSpyObj('RechnungService', ['generateRechnungen', 'downloadRechnung']);
    rechnungServiceSpy.generateRechnungen.and.returnValue(of(mockGenerateResponse));
    rechnungServiceSpy.downloadRechnung.and.returnValue(undefined);

    translationServiceSpy = jasmine.createSpyObj('TranslationService', ['translate', 'getCurrentLanguage']);
    translationServiceSpy.translate.and.callFake((key: string) => key);
    translationServiceSpy.getCurrentLanguage.and.returnValue('de');

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
    it('should set default dates to previous month', () => {
      expect(component.dateFrom).toBeTruthy();
      expect(component.dateTo).toBeTruthy();
      expect(component.dateFrom.length).toBe(10);
      expect(component.dateTo.length).toBe(10);
    });
  });

  describe('onSelectionChange', () => {
    it('should update selectedEinheitIds from emitted einheiten', () => {
      component.onSelectionChange([mockConsumer, mockConsumer2]);
      expect(component.selectedEinheitIds.has(1)).toBeTrue();
      expect(component.selectedEinheitIds.has(2)).toBeTrue();
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
      expect(component.canGenerate()).toBeFalse();
    });

    it('should return false when dateTo is empty', () => {
      component.dateFrom = '2024-01-01';
      component.dateTo = '';
      component.selectedEinheitIds.add(1);
      expect(component.canGenerate()).toBeFalse();
    });

    it('should return false when no einheiten selected', () => {
      component.dateFrom = '2024-01-01';
      component.dateTo = '2024-12-31';
      expect(component.canGenerate()).toBeFalse();
    });

    it('should return false when generating is in progress', () => {
      component.dateFrom = '2024-01-01';
      component.dateTo = '2024-12-31';
      component.selectedEinheitIds.add(1);
      component.generating = true;
      expect(component.canGenerate()).toBeFalse();
    });

    it('should return true when all conditions are met', () => {
      component.dateFrom = '2024-01-01';
      component.dateTo = '2024-12-31';
      component.selectedEinheitIds.add(1);
      expect(component.canGenerate()).toBeTrue();
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
      rechnungServiceSpy.generateRechnungen.and.returnValue(
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
      expect(component.generating).toBeFalse();
    });

    it('should reset generating flag after error', () => {
      rechnungServiceSpy.generateRechnungen.and.returnValue(
        throwError(() => ({ message: 'error' }))
      );
      component.onGenerate();
      expect(component.generating).toBeFalse();
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
