import { createSpyObj, SpyObj } from '../../../testing/spy';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MesswerteChartComponent } from './messwerte-chart.component';
import { MesswerteService, MesswertData } from '../../services/messwerte.service';
import { EinheitService } from '../../services/einheit.service';
import { TranslationService } from '../../services/translation.service';
import { Einheit, EinheitTyp, MesspunktGruppe } from '../../models/einheit.model';
import { By } from '@angular/platform-browser';
import { IconComponent } from '../icon/icon.component';
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

  const mockBezug: Einheit = { id: 3, name: 'Bezug von BKW', typ: EinheitTyp.BEZUG, messpunkt: 'Bilanz' };
  const mockRuecklieferung: Einheit = {
    id: 4, name: 'Rücklieferung an BKW', typ: EinheitTyp.RUECKLIEFERUNG, messpunkt: 'Bilanz'
  };
  const bilanz: MesspunktGruppe = { messpunkt: 'Bilanz', einheiten: [mockBezug, mockRuecklieferung] };

  /** Zugriff auf die privaten Bausteine — Testcode darf das, Produktivcode nicht. */
  function privat(): {
    optionen: (data: MesswertData[]) => Record<string, unknown>;
    optionenMesspunkt: (reihen: { einheit: Einheit; daten: MesswertData[] }[]) => Record<string, unknown>;
    legende: (key: string, summe: number, kwh: string) => string;
    reihe: (name: string, daten: number[][], farbe?: string) => Record<string, unknown>;
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
      component as unknown as { createChartsSequentially: (d: Map<number, MesswertData[]>) => Promise<void> },
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
      expect(component.charts[0].einheiten).toEqual([mockConsumer]);
      expect(component.charts[0].messpunkt).toBeUndefined();
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

  /** Bedienung wie in der Statistik (Specs/EChart.md, FR-1a). */
  describe('Bedienung wie Statistik', () => {
    function knopf(id: string): HTMLButtonElement {
      return fixture.debugElement.query(By.css('#' + id)).nativeElement as HTMLButtonElement;
    }

    it('should place the submit button next to the quarter buttons', () => {
      const anzeigen = fixture.debugElement.query(By.css('app-quarter-selector #grafikAnzeigen'));
      expect(anzeigen).not.toBeNull();
      expect((anzeigen.nativeElement as HTMLButtonElement).type).toBe('submit');
      expect(fixture.debugElement.queryAll(By.css('button[type="submit"]')).length).toBe(1);
      expect(fixture.debugElement.query(By.css('.zev-date-range-row #grafikAnzeigen'))).toBeNull();
    });

    it('should keep the submit button disabled until a unit is selected', () => {
      fixture.detectChanges();
      expect(knopf('grafikAnzeigen').disabled).toBe(true);

      component.selectedEinheiten = [mockConsumer];
      fixture.detectChanges();
      expect(knopf('grafikAnzeigen').disabled).toBe(false);

      component.loading = true;
      fixture.detectChanges();
      expect(knopf('grafikAnzeigen').disabled).toBe(true);
    });

    it('should offer paging and today in the date row, without pdf export', () => {
      const zeile = fixture.debugElement.query(By.css('.zev-date-range-row'));
      const ids = zeile.queryAll(By.css('button')).map(b => (b.nativeElement as HTMLButtonElement).id);
      expect(ids).toEqual(['zeitraumZurueck', 'zeitraumVor', 'zeitraumHeute']);
      expect(fixture.nativeElement.textContent).not.toContain('PDF_EXPORTIEREN');
    });

    it('should render chevron icons with translated title and aria-label', () => {
      const pruefe = (id: string, icon: string, key: string) => {
        const el = knopf(id);
        expect(el.getAttribute('title')).toBe(key);
        expect(el.getAttribute('aria-label')).toBe(key);
        const symbol = fixture.debugElement.query(By.css('#' + id)).query(By.directive(IconComponent));
        expect(symbol.componentInstance.name).toBe(icon);
      };
      pruefe('zeitraumZurueck', 'chevron-left', 'ZEITRAUM_ZURUECK');
      pruefe('zeitraumVor', 'chevron-right', 'ZEITRAUM_VOR');
      expect(knopf('zeitraumHeute').textContent).toContain('HEUTE');
    });

    it('should disable paging and today without a unit or while loading', () => {
      fixture.detectChanges();
      expect(knopf('zeitraumVor').disabled).toBe(true);
      expect(knopf('zeitraumHeute').disabled).toBe(true);

      component.selectedEinheiten = [mockConsumer];
      fixture.detectChanges();
      expect(knopf('zeitraumVor').disabled).toBe(false);
      expect(knopf('zeitraumHeute').disabled).toBe(false);

      component.loading = true;
      fixture.detectChanges();
      expect(knopf('zeitraumZurueck').disabled).toBe(true);
      expect(knopf('zeitraumHeute').disabled).toBe(true);
    });

    describe('onBlaettern', () => {
      it('should move a quarter forward and load immediately', () => {
        component.selectedEinheiten = [mockConsumer];
        component.dateFrom = '2026-07-01';
        component.dateTo = '2026-09-30';

        component.onBlaettern(1);

        expect(component.dateFrom).toBe('2026-10-01');
        expect(component.dateTo).toBe('2026-12-31');
        expect(messwerteServiceSpy.getMesswerteByEinheit).toHaveBeenCalledWith(1, '2026-10-01', '2026-12-31');
      });

      it('should move a day backwards', () => {
        component.selectedEinheiten = [mockConsumer];
        component.dateFrom = '2026-10-13';
        component.dateTo = '2026-10-13';

        component.onBlaettern(-1);

        expect(component.dateFrom).toBe('2026-10-12');
        expect(component.dateTo).toBe('2026-10-12');
      });

      it('should not move without a unit, while loading or with an invalid range', () => {
        component.dateFrom = '2026-10-01';
        component.dateTo = '2026-10-07';
        component.onBlaettern(1);
        expect(component.dateFrom).toBe('2026-10-01');

        component.selectedEinheiten = [mockConsumer];
        component.loading = true;
        component.onBlaettern(1);
        expect(component.dateFrom).toBe('2026-10-01');

        component.loading = false;
        component.dateFrom = '2026-10-14';
        component.onBlaettern(1);
        expect(component.dateFrom).toBe('2026-10-14');
        expect(messwerteServiceSpy.getMesswerteByEinheit).not.toHaveBeenCalled();
      });
    });

    describe('onHeute', () => {
      beforeEach(() => {
        vi.useFakeTimers();
      });

      afterEach(() => {
        vi.useRealTimers();
      });

      it('should set both dates to today and load immediately', () => {
        vi.setSystemTime(new Date(2026, 9, 7, 14, 30));
        component.selectedEinheiten = [mockConsumer];

        component.onHeute();

        expect(component.dateFrom).toBe('2026-10-07');
        expect(component.dateTo).toBe('2026-10-07');
        expect(messwerteServiceSpy.getMesswerteByEinheit).toHaveBeenCalledWith(1, '2026-10-07', '2026-10-07');
      });

      /** 00:30 Ortszeit ist im Sommer 22:30 UTC des Vortags - toISOString() lieferte den 06.10. */
      it('should use the local date shortly after midnight', () => {
        vi.setSystemTime(new Date(2026, 9, 7, 0, 30));
        component.selectedEinheiten = [mockConsumer];

        component.onHeute();

        expect(component.dateFrom).toBe('2026-10-07');
      });

      it('should reload when today is already shown', () => {
        vi.setSystemTime(new Date(2026, 9, 7, 9, 0));
        component.selectedEinheiten = [mockConsumer];
        component.onHeute();
        messwerteServiceSpy.getMesswerteByEinheit.mockClear();

        component.onHeute();

        expect(messwerteServiceSpy.getMesswerteByEinheit).toHaveBeenCalledTimes(1);
      });
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

    it('should name the y axis from the translation service and leave the time axis untitled', () => {
      const optionen = privat().optionen(messwerte);

      expect((optionen['yAxis'] as { name: string })['name']).toBe('KWH');
      // Kein Titel an der Zeitachse: Er stand mitten im Zoom-Schieber.
      expect((optionen['xAxis'] as { name?: string })['name']).toBeUndefined();
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

  // Specs/EChart.md, FR-8: zusaetzliches Diagramm je Messpunkt mit mehreren Einheiten
  describe('Messpunkt-Diagramm', () => {
    const bezugWerte: MesswertData[] = [
      { zeit: '2026-01-15T10:00:00', total: 1.5, zev: 0 },
      { zeit: '2026-01-15T10:15:00', total: 0, zev: 0 }
    ];
    const ruecklieferungWerte: MesswertData[] = [
      { zeit: '2026-01-15T10:00:00', total: 0, zev: 0 },
      { zeit: '2026-01-15T10:15:00', total: -2.25, zev: 0 }
    ];

    it('should enable the submit button with only a messpunkt selected', () => {
      component.onGruppenChange([bilanz]);
      fixture.detectChanges();

      expect(component.hatAuswahl).toBe(true);
      expect(fixture.nativeElement.querySelector('#grafikAnzeigen').disabled).toBe(false);
      expect(component.kannBlaettern).toBe(true);
      // Auch Blaettern und "Heute" zaehlen den Messpunkt als Auswahl (FR-1a.5).
      expect(fixture.nativeElement.querySelector('#zeitraumVor').disabled).toBe(false);
      expect(fixture.nativeElement.querySelector('#zeitraumHeute').disabled).toBe(false);
    });

    it('should load both einheiten of a selected messpunkt and add one chart for it', () => {
      component.selectedGruppen = [bilanz];
      component.dateFrom = '2026-01-01';
      component.dateTo = '2026-03-31';

      component.onSubmit();

      expect(messwerteServiceSpy.getMesswerteByEinheit).toHaveBeenCalledTimes(2);
      expect(messwerteServiceSpy.getMesswerteByEinheit).toHaveBeenCalledWith(3, '2026-01-01', '2026-03-31');
      expect(messwerteServiceSpy.getMesswerteByEinheit).toHaveBeenCalledWith(4, '2026-01-01', '2026-03-31');
      expect(component.charts.length).toBe(1);
      expect(component.charts[0].messpunkt).toBe('Bilanz');
      expect(component.charts[0].einheiten).toEqual([mockBezug, mockRuecklieferung]);
    });

    it('should load an einheit only once when it is selected alone and as part of its messpunkt', () => {
      messwerteServiceSpy.getMesswerteByEinheit.mockReturnValue(of(messwerte));
      component.selectedEinheiten = [mockConsumer, mockBezug];
      component.selectedGruppen = [bilanz];

      component.onSubmit();

      // Wohnung, Bezug, Ruecklieferung - der Bezug nicht zweimal.
      expect(messwerteServiceSpy.getMesswerteByEinheit).toHaveBeenCalledTimes(3);
      // Erst die Einheiten, danach der Messpunkt.
      expect(component.charts.map(c => c.schluessel)).toEqual(['1', '3', 'messpunkt-0']);
      // Gemeldet werden die geladenen Einheiten (3) und ihre Datenpunkte (3 x 2), nicht die Diagramme.
      expect(component.message).toBe('6 DATENPUNKTE_FUER 3 EINHEITEN_GELADEN');
    });

    it('should title the messpunkt panel with both einheiten and the messpunkt', () => {
      component.selectedEinheiten = [mockConsumer];
      component.selectedGruppen = [bilanz];

      component.onSubmit();
      fixture.detectChanges();

      const titel: HTMLElement[] = Array.from(
        fixture.nativeElement.querySelectorAll('.zev-panel--chart .zev-panel__title'));
      expect(titel.map(t => t.textContent?.trim())).toEqual([
        'Wohnung A [KONSUMENT]',
        'Bezug von BKW [TYP_BEZUG] / Rücklieferung an BKW [TYP_RUECKLIEFERUNG] / [Bilanz]'
      ]);
      expect(fixture.nativeElement.querySelector('#chart-messpunkt-0')).not.toBeNull();
    });

    it('should draw one series per einheit with its stored sign and without a ZEV series', () => {
      const optionen = privat().optionenMesspunkt([
        { einheit: mockBezug, daten: bezugWerte },
        { einheit: mockRuecklieferung, daten: ruecklieferungWerte }
      ]);
      const series = optionen['series'] as { name: string; data: number[][]; lineStyle: { color?: string } }[];

      expect(series.length).toBe(2);
      // Name der Einheit samt Summe im Schweizer Format - kein TOTAL/ZEV.
      expect(series[0].name).toBe('Bezug von BKW (Σ 1.500 KWH)');
      expect(series[1].name).toBe('Rücklieferung an BKW (Σ -2.250 KWH)');
      // Nicht gespiegelt: Die Ruecklieferung ist negativ gespeichert und bleibt es.
      // Je Reihe die beiden Messwerte, dazu der Abschlusspunkt mit dem letzten Wert.
      expect(series[0].data.map(p => p[1])).toEqual([1.5, 0, 0]);
      expect(series[1].data.map(p => p[1])).toEqual([0, -2.25, -2.25]);
      // Zwei verschiedene Reihenfarben, beide gesetzt.
      expect(series[0].lineStyle.color).toBeTruthy();
      expect(series[1].lineStyle.color).toBeTruthy();
      expect(series[0].lineStyle.color).not.toBe(series[1].lineStyle.color);
    });

    it('should share axes, zoom and step line with the single-unit chart', () => {
      const einzeln = privat().optionen(messwerte);
      const messpunkt = privat().optionenMesspunkt([{ einheit: mockBezug, daten: bezugWerte }]);

      expect(JSON.stringify(messpunkt['dataZoom'])).toBe(JSON.stringify(einzeln['dataZoom']));
      expect((messpunkt['xAxis'] as { type: string; name: string }).type).toBe('time');
      expect((messpunkt['yAxis'] as { name: string }).name).toBe('KWH');
      // Einzeilig blaetternd: Mit den Namen der Einheiten braeche die Legende sonst um.
      expect((messpunkt['legend'] as { type: string }).type).toBe('scroll');
      const reihe = (messpunkt['series'] as Record<string, unknown>[])[0];
      expect(reihe['step']).toBe('end');
      expect(reihe['sampling']).toBe('lttb');
    });

    it('should leave the colour of a third series to the library', () => {
      const dritte: Einheit = { id: 9, name: 'Dritte', typ: EinheitTyp.CONSUMER, messpunkt: 'Bilanz' };
      const optionen = privat().optionenMesspunkt([
        { einheit: mockBezug, daten: [] },
        { einheit: mockRuecklieferung, daten: [] },
        { einheit: dritte, daten: [] }
      ]);
      const series = optionen['series'] as { lineStyle: { color?: string }; itemStyle?: unknown }[];

      // Keine dritte Token-Farbe vorhanden - lieber die Palette von ECharts als zweimal dieselbe.
      expect(series[2].lineStyle.color).toBeUndefined();
      expect(series[2].itemStyle).toBeUndefined();
    });

    it('should draw an empty series when an einheit has no measurements', () => {
      const optionen = privat().optionenMesspunkt([
        { einheit: mockBezug, daten: bezugWerte },
        { einheit: mockRuecklieferung, daten: [] }
      ]);
      const series = optionen['series'] as { name: string; data: number[][] }[];

      expect(series[1].data).toEqual([]);
      expect(series[1].name).toBe('Rücklieferung an BKW (Σ 0.000 KWH)');
    });
  });

  // Specs/EChart.md, FR-2: Auch das letzte Intervall ist zu sehen
  describe('Abschlusspunkt', () => {
    const tooltip = (optionen: Record<string, unknown>) =>
      (optionen['tooltip'] as { formatter: (p: { seriesName: string; value: number[] }[]) => string })
        .formatter;

    it('should draw a single measurement as one quarter-hour step', () => {
      // Ohne Abschlusspunkt hat die Stufenlinie nur EINEN Punkt und zeichnet nichts - die Legende
      // nennt aber eine Summe. So sah eine Einheit mit genau einem Intervall aus.
      const einer: MesswertData[] = [{ zeit: '2026-11-21T23:15:00', total: 0.14, zev: 0 }];
      const start = new Date('2026-11-21T23:15:00').getTime();

      const serien = privat().optionen(einer)['series'] as { name: string; data: number[][] }[];

      expect(serien[0].data).toEqual([[start, 0.14], [start + 15 * 60 * 1000, 0.14, 1]]);
      // Die Summe kommt aus dem Messwert, der Abschlusspunkt zaehlt nicht doppelt.
      expect(serien[0].name).toBe('TOTAL (Σ 0.140 KWH)');
    });

    it('should extend every series by the end of its last interval', () => {
      const serien = privat().optionen(messwerte)['series'] as { data: number[][] }[];
      const ende = new Date('2026-01-15T10:15:00').getTime() + 15 * 60 * 1000;

      expect(serien[0].data.length).toBe(3);
      expect(serien[0].data[2]).toEqual([ende, 2.25, 1]);
      // Die ZEV-Reihe ist gespiegelt - der Abschlusspunkt traegt den gespiegelten Wert.
      expect(serien[1].data[2]).toEqual([ende, -1.25, 1]);
    });

    it('should not add a point to an empty series', () => {
      const serien = privat().optionen([])['series'] as { data: number[][] }[];

      expect(serien[0].data).toEqual([]);
    });

    it('should leave the closing point out of the tooltip', () => {
      const optionen = privat().optionen(messwerte);
      const serien = optionen['series'] as { name: string; data: number[][] }[];

      // Ueber einem Messwert: Zeit und Wert wie bisher.
      const echt = tooltip(optionen)([{ seriesName: 'Total', value: serien[0].data[1] }]);
      expect(echt).toContain('15.01.2026 10:15');
      expect(echt).toContain('Total: 2.250 KWH');

      // Ueber dem Abschlusspunkt: nichts - er ist kein Messwert.
      expect(tooltip(optionen)([{ seriesName: 'Total', value: serien[0].data[2] }])).toBe('');
    });
  });

  describe('ngOnDestroy', () => {
    it('should dispose every instance and disconnect every observer', () => {
      const dispose = vi.fn();
      const disconnect = vi.fn();
      component.charts = [{
        schluessel: '1', einheiten: [mockConsumer],
        instanz: { dispose } as unknown as import('echarts/core').ECharts
      }];
      (component as unknown as { observers: Map<string, { disconnect: () => void }> })
        .observers.set('1', { disconnect });

      component.ngOnDestroy();

      // Ohne Freigabe waechst der Speicher mit jedem "Anzeigen" - die Seite erlaubt beliebig
      // viele Diagramme.
      expect(dispose).toHaveBeenCalled();
      expect(disconnect).toHaveBeenCalled();
    });
  });
});
