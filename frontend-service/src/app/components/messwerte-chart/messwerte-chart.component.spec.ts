import { createSpyObj, SpyObj } from '../../../testing/spy';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MesswerteChartComponent } from './messwerte-chart.component';
import { MesswerteService, MesswertData } from '../../services/messwerte.service';
import { EinheitService } from '../../services/einheit.service';
import { TranslationService } from '../../services/translation.service';
import { Einheit, EinheitTyp } from '../../models/einheit.model';
import { of, throwError } from 'rxjs';

/**
 * Unit-Tests der Messwerte-Grafik (`Specs/EChart.md`).
 *
 * <p><b>Das Zeichnen ist gestubbt.</b> ECharts wird dynamisch nachgeladen und braucht ein gemessenes
 * Element mit Canvas — in jsdom gibt es beides nicht. Ohne den Stub liefe nach dem Testende ein
 * `setTimeout` in eine Zeichnung, deren Fehler niemand mehr zuordnet. Geprüft wird deshalb die
 * Logik, und die Diagramm-Optionen werden als **reine Funktion** aufgerufen.
 *
 * <p>Dass beide Datenreihen tatsächlich gezeichnet werden, zeigt nur der E2E-Test
 * (`tests/messwerte-grafik.spec.ts`) — er vergleicht die bemalten Pixel eines Zeitraums mit und ohne
 * Messwerte.
 */
describe('MesswerteChartComponent', () => {
  let component: MesswerteChartComponent;
  let fixture: ComponentFixture<MesswerteChartComponent>;
  let messwerteServiceSpy: SpyObj<MesswerteService>;
  let einheitServiceSpy: SpyObj<EinheitService>;
  let translationServiceSpy: SpyObj<TranslationService>;
  let zeichnenSpy: ReturnType<typeof vi.spyOn>;

  const mockConsumer: Einheit = { id: 1, name: 'Wohnung A', typ: EinheitTyp.CONSUMER };
  const mockProducer: Einheit = { id: 2, name: 'Solar Anlage', typ: EinheitTyp.PRODUCER };

  const messwerte: MesswertData[] = [
    { zeit: '2026-01-15T10:00:00', total: 1.5, zev: 0.5 },
    { zeit: '2026-01-15T10:15:00', total: 2.25, zev: 1.25 }
  ];

  /** Zugriff auf die privaten Bausteine — Testcode darf das, Produktivcode nicht. */
  function privat(): {
    optionen: (data: MesswertData[]) => Record<string, unknown>;
    legende: (key: string, summe: number, kwh: string) => string;
    reihe: (name: string, daten: number[][], farbe: string) => Record<string, unknown>;
  } {
    return component as unknown as ReturnType<typeof privat>;
  }

  beforeEach(async () => {
    messwerteServiceSpy = createSpyObj<MesswerteService>('MesswerteService', ['getMesswerteByEinheit']);
    messwerteServiceSpy.getMesswerteByEinheit.mockReturnValue(of([]));

    einheitServiceSpy = createSpyObj<EinheitService>('EinheitService', ['getAllEinheiten']);
    einheitServiceSpy.getAllEinheiten.mockReturnValue(of([mockConsumer, mockProducer]));

    translationServiceSpy = createSpyObj<TranslationService>('TranslationService', ['translate', 'getCurrentLanguage']);
    translationServiceSpy.translate.mockImplementation((key: string) => key);
    translationServiceSpy.getCurrentLanguage.mockReturnValue('de');

    await TestBed.configureTestingModule({
      imports: [MesswerteChartComponent],
      providers: [
        { provide: MesswerteService, useValue: messwerteServiceSpy },
        { provide: EinheitService, useValue: einheitServiceSpy },
        { provide: TranslationService, useValue: translationServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MesswerteChartComponent);
    component = fixture.componentInstance;
    // Stub VOR detectChanges: `onSubmit` zeichnet sonst wirklich und fasst ECharts an.
    zeichnenSpy = vi.spyOn(
      component as unknown as { createChartsSequentially: (r: MesswertData[][]) => Promise<void> },
      'createChartsSequentially').mockResolvedValue(undefined);
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
    it('should update selectedEinheiten from emitted einheiten', () => {
      component.onSelectionChange([mockConsumer]);
      expect(component.selectedEinheiten).toEqual([mockConsumer]);
    });
  });

  describe('onQuarterSelected', () => {
    it('should set dateFrom and dateTo from event', () => {
      component.onQuarterSelected({ von: '2024-01-01', bis: '2024-03-31' });
      expect(component.dateFrom).toBe('2024-01-01');
      expect(component.dateTo).toBe('2024-03-31');
    });
  });

  describe('onDateFromChange', () => {
    it('should set dateTo to last day of dateFrom month', () => {
      component.dateFrom = '2024-02-15';
      component.onDateFromChange();
      expect(component.dateTo).toBe('2024-02-29');
    });

    it('should not change dateTo when dateFrom is empty', () => {
      const prevDateTo = component.dateTo;
      component.dateFrom = '';
      component.onDateFromChange();
      expect(component.dateTo).toBe(prevDateTo);
    });
  });

  describe('onSubmit', () => {
    it('should show error when no einheiten selected', () => {
      component.selectedEinheiten = [];
      component.onSubmit();
      expect(component.messageType).toBe('error');
      expect(messwerteServiceSpy.getMesswerteByEinheit).not.toHaveBeenCalled();
    });

    it('should show error when dateFrom is after dateTo', () => {
      component.selectedEinheiten = [mockConsumer];
      component.dateFrom = '2024-12-31';
      component.dateTo = '2024-01-01';
      component.onSubmit();
      expect(component.messageType).toBe('error');
      expect(messwerteServiceSpy.getMesswerteByEinheit).not.toHaveBeenCalled();
    });

    it('should load messwerte for selected einheiten', () => {
      component.selectedEinheiten = [mockConsumer];
      component.dateFrom = '2024-01-01';
      component.dateTo = '2024-03-31';
      component.onSubmit();
      expect(messwerteServiceSpy.getMesswerteByEinheit).toHaveBeenCalledWith(1, '2024-01-01', '2024-03-31');
    });

    it('should create one chart entry per selected einheit', () => {
      component.selectedEinheiten = [mockConsumer, mockProducer];
      component.onSubmit();

      expect(component.charts.length).toBe(2);
      expect(component.charts[0].einheitName).toBe('Wohnung A');
      expect(component.charts[0].instanz).toBeNull();
      expect(zeichnenSpy).toHaveBeenCalled();
    });

    it('should report the number of loaded data points', () => {
      messwerteServiceSpy.getMesswerteByEinheit.mockReturnValue(of(messwerte));
      component.selectedEinheiten = [mockConsumer];

      component.onSubmit();

      expect(component.messageType).toBe('success');
      expect(component.message).toContain('2');
      expect(component.loading).toBe(false);
    });

    it('should show a readable error when loading fails', () => {
      messwerteServiceSpy.getMesswerteByEinheit
        .mockReturnValue(throwError(() => ({ message: 'Netzwerkfehler' })));
      component.selectedEinheiten = [mockConsumer];

      component.onSubmit();

      expect(component.messageType).toBe('error');
      expect(component.message).toContain('Netzwerkfehler');
      expect(component.message).not.toContain('[object Object]');
      expect(component.loading).toBe(false);
    });
  });

  describe('optionen', () => {
    it('should build two series, the ZEV one mirrored below zero', () => {
      const optionen = privat().optionen(messwerte);
      const serien = optionen['series'] as { name: string; data: number[][] }[];

      expect(serien).toHaveLength(2);
      // Total unveraendert, ZEV negativ - so unterscheidet die Grafik Bezug und Eigenverbrauch.
      expect(serien[0].data[0][1]).toBe(1.5);
      expect(serien[1].data[0][1]).toBe(-0.5);
    });

    it('should turn the timestamps into numbers for the time axis', () => {
      const serien = privat().optionen(messwerte)['series'] as { data: number[][] }[];

      expect(serien[0].data[0][0]).toBe(new Date('2026-01-15T10:00:00').getTime());
      expect((privat().optionen(messwerte)['xAxis'] as { type: string })['type']).toBe('time');
    });

    it('should offer zoom inside and as a slider', () => {
      const dataZoom = privat().optionen(messwerte)['dataZoom'] as { type: string }[];

      expect(dataZoom.map(z => z.type)).toEqual(['inside', 'slider']);
    });

    it('should name the axes from the translation service', () => {
      const optionen = privat().optionen(messwerte);

      expect((optionen['xAxis'] as { name: string })['name']).toBe('ZEIT');
      expect((optionen['yAxis'] as { name: string })['name']).toBe('KWH');
    });

    it('should work with an empty data set', () => {
      const serien = privat().optionen([])['series'] as { data: number[][] }[];

      expect(serien).toHaveLength(2);
      expect(serien[0].data).toEqual([]);
    });
  });

  describe('legende', () => {
    it('should combine the translated name with the swiss formatted sum', () => {
      // 1234.5 -> 1'234.500: Hochkomma als Tausendertrennzeichen, drei Nachkommastellen.
      expect(privat().legende('TOTAL', 1234.5, 'kWh')).toBe("TOTAL (Σ 1'234.500 kWh)");
    });

    it('should keep the sign of a negative sum', () => {
      expect(privat().legende('ZEV', -12.25, 'kWh')).toBe('ZEV (Σ -12.250 kWh)');
    });
  });

  describe('reihe', () => {
    it('should build a step line with sampling and without symbols', () => {
      const reihe = privat().reihe('Total', [[1, 2]], '#4CAF50');

      expect(reihe['type']).toBe('line');
      expect(reihe['step']).toBe('end');
      expect(reihe['sampling']).toBe('lttb');
      expect(reihe['showSymbol']).toBe(false);
      expect(reihe['connectNulls']).toBe(false);
      // Keine Flaechenfuellung: Eine gefuellte Flaeche liest sich als Summe ueber die Zeit.
      expect(reihe['areaStyle']).toBeUndefined();
    });
  });

  describe('ngOnDestroy', () => {
    it('should dispose every instance and disconnect every observer', () => {
      const dispose = vi.fn();
      const disconnect = vi.fn();
      component.charts = [{
        einheitId: 1, einheitName: 'Wohnung A', einheitTyp: 'CONSUMER',
        instanz: { dispose } as unknown as import('echarts/core').ECharts
      }];
      (component as unknown as { observers: Map<number, { disconnect: () => void }> })
        .observers.set(1, { disconnect });

      component.ngOnDestroy();

      // Ohne Freigabe waechst der Speicher mit jedem "Anzeigen" - die Seite erlaubt beliebig
      // viele Diagramme.
      expect(dispose).toHaveBeenCalled();
      expect(disconnect).toHaveBeenCalled();
    });
  });
});
