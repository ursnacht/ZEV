import { createSpyObj, SpyObj } from '../../../testing/spy';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import Keycloak from 'keycloak-js';
import { MesswerteChartComponent } from './messwerte-chart.component';
import { MesswerteService, MesswertData } from '../../services/messwerte.service';
import { EinheitService } from '../../services/einheit.service';
import { TranslationService } from '../../services/translation.service';
import { Einheit, EinheitTyp, MesspunktGruppe } from '../../models/einheit.model';
import { By } from '@angular/platform-browser';
import { IconComponent } from '../icon/icon.component';
import { of, throwError } from 'rxjs';
import { PreiszeitreiheService } from '../../services/preiszeitreihe.service';
import { PreiszeitreihePunkt } from '../../models/preiszeitreihe.model';
import { FeatureFlagService } from '../../services/feature-flag.service';

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

  let preiszeitreiheServiceSpy: SpyObj<PreiszeitreiheService>;
  /** Permissions des Testbenutzers; `tarife:manage` gibt die Checkbox „Einspeisetarif" frei. */
  let rollen: string[];
  /** Feature-Flag PREISZEITREIHE, als Signal wie im Betrieb. */
  const preiszeitreiheAktiv = signal(true);

  const preise: PreiszeitreihePunkt[] = [
    { zeit: '2026-01-15T10:00:00', preis: 0.08125 },
    { zeit: '2026-01-15T10:15:00', preis: 0.1234567 }
  ];

  /** Zugriff auf die privaten Bausteine — Testcode darf das, Produktivcode nicht. */
  function privat(): {
    optionen: (data: MesswertData[], preise?: PreiszeitreihePunkt[]) => Record<string, unknown>;
    optionenMesspunkt: (reihen: { einheit: Einheit; daten: MesswertData[] }[],
                        preise?: PreiszeitreihePunkt[],
                        produzentenPositiv?: boolean) => Record<string, unknown>;
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

    preiszeitreiheServiceSpy = createSpyObj<PreiszeitreiheService>('PreiszeitreiheService', ['getPunkte']);
    preiszeitreiheServiceSpy.getPunkte.mockReturnValue(of(preise));
    rollen = ['tarife:manage'];
    preiszeitreiheAktiv.set(true);

    await TestBed.configureTestingModule({
      imports: [MesswerteChartComponent],
      providers: [
        { provide: MesswerteService, useValue: messwerteServiceSpy },
        { provide: EinheitService, useValue: einheitServiceSpy },
        { provide: TranslationService, useValue: translationServiceSpy },
        { provide: PreiszeitreiheService, useValue: preiszeitreiheServiceSpy },
        { provide: Keycloak, useValue: { hasRealmRole: (rolle: string) => rollen.includes(rolle) } },
        {
          provide: FeatureFlagService,
          useValue: { isEnabled: (flag: string) => flag === 'PREISZEITREIHE' && preiszeitreiheAktiv() }
        }
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

    it('should give six series their own token colour and leave a seventh to the library', () => {
      const reihen = [1, 2, 3, 4, 5, 6, 7].map(n => ({
        einheit: { id: 100 + n, name: `Einheit ${n}`, typ: EinheitTyp.CONSUMER } as Einheit,
        daten: [] as MesswertData[]
      }));

      const series = privat().optionenMesspunkt(reihen)['series'] as
        { lineStyle: { color?: string }; itemStyle?: unknown }[];

      // Sechs verschiedene Farben: Zwei Kurven im selben Ton waeren in der Legende nicht zu
      // unterscheiden.
      const farben = series.slice(0, 6).map(reihe => reihe.lineStyle.color);
      expect(farben.every(farbe => !!farbe)).toBe(true);
      expect(new Set(farben).size).toBe(6);
      // Danach ist die Token-Palette erschoepft - lieber die von ECharts als eine Farbe doppelt.
      expect(series[6].lineStyle.color).toBeUndefined();
      expect(series[6].itemStyle).toBeUndefined();
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

  // Specs/EChart.md, FR-9: alle gewaehlten Einheiten in einem Diagramm
  describe('Vergleich', () => {
    const vergleichButton = (): HTMLButtonElement =>
      fixture.nativeElement.querySelector('#grafikVergleich');
    const panelTitel = (): string[] => Array.from(
      fixture.nativeElement.querySelectorAll('.zev-panel--chart .zev-panel__title') as NodeListOf<HTMLElement>)
      .map(titel => (titel.textContent ?? '').replace(/\s+/g, ' ').trim());

    it('should offer the button next to the submit button, disabled below two einheiten', () => {
      const slot: HTMLElement = fixture.nativeElement.querySelector('app-quarter-selector');
      expect(slot.querySelector('#grafikAnzeigen')).not.toBeNull();
      expect(slot.querySelector('#grafikVergleich')).not.toBeNull();
      expect(vergleichButton().textContent).toContain('GRAFIK_VERGLEICH');
      expect(vergleichButton().disabled).toBe(true);
      // Rechts von "Anzeigen", als sekundaere Schaltflaeche - und kein zweiter Submit-Button:
      // Enter im Formular soll weiterhin "Anzeigen" ausloesen.
      const knoepfe = Array.from(slot.querySelectorAll('button')).map(knopf => knopf.id);
      expect(knoepfe.indexOf('grafikVergleich')).toBe(knoepfe.indexOf('grafikAnzeigen') + 1);
      expect(vergleichButton().classList.contains('zev-button--secondary')).toBe(true);
      expect(vergleichButton().type).toBe('button');

      // Eine Einheit ist kein Vergleich.
      component.onSelectionChange([mockConsumer]);
      fixture.detectChanges();
      expect(component.kannVergleichen).toBe(false);
      expect(vergleichButton().disabled).toBe(true);

      component.onSelectionChange([mockConsumer, mockProducer]);
      fixture.detectChanges();
      expect(vergleichButton().disabled).toBe(false);
    });

    it('should disable the comparison while loading or without a complete period', () => {
      component.onSelectionChange([mockConsumer, mockProducer]);
      fixture.detectChanges();
      expect(vergleichButton().disabled).toBe(false);

      component.loading = true;
      fixture.detectChanges();
      expect(vergleichButton().disabled).toBe(true);

      component.loading = false;
      component.dateTo = '';
      fixture.detectChanges();
      expect(vergleichButton().disabled).toBe(true);
    });

    it('should enable the comparison for a single messpunkt with two einheiten', () => {
      component.onGruppenChange([bilanz]);
      fixture.detectChanges();

      expect(component.kannVergleichen).toBe(true);
      expect(vergleichButton().disabled).toBe(false);
    });

    it('should replace the single charts by one chart with all selected einheiten', () => {
      component.selectedEinheiten = [mockConsumer, mockProducer];
      component.onSubmit();
      fixture.detectChanges();
      expect(component.charts.length).toBe(2);

      vergleichButton().click();
      fixture.detectChanges();

      // Die zwei einzelnen Diagramme sind weg, es steht genau eines.
      expect(component.charts.length).toBe(1);
      expect(component.charts[0].vergleich).toBe(true);
      expect(component.charts[0].einheiten).toEqual([mockConsumer, mockProducer]);
      expect(fixture.nativeElement.querySelectorAll('.zev-panel--chart').length).toBe(1);
      expect(fixture.nativeElement.querySelector('#chart-vergleich')).not.toBeNull();
      expect(panelTitel()).toEqual(['GRAFIK_VERGLEICH: 2 EINHEITEN']);
    });

    it('should load each einheit once, also when it is selected through its messpunkt too', () => {
      messwerteServiceSpy.getMesswerteByEinheit.mockReturnValue(of(messwerte));
      component.selectedEinheiten = [mockConsumer, mockBezug];
      component.selectedGruppen = [bilanz];

      component.onVergleich();

      // Wohnung, Bezug, Ruecklieferung - der Bezug nicht zweimal, weder geladen noch gezeichnet.
      expect(messwerteServiceSpy.getMesswerteByEinheit).toHaveBeenCalledTimes(3);
      expect(component.charts[0].einheiten).toEqual([mockConsumer, mockBezug, mockRuecklieferung]);
      expect(component.message).toBe('6 DATENPUNKTE_FUER 3 EINHEITEN_GELADEN');
    });

    it('should mirror only producers, including the sum, and leave a zero untouched', () => {
      const produktion: MesswertData[] = [
        { zeit: '2026-01-15T10:00:00', total: 0, zev: 0 },
        { zeit: '2026-01-15T10:15:00', total: -1.288, zev: -1 }
      ];
      const ruecklieferung: MesswertData[] = [{ zeit: '2026-01-15T10:15:00', total: -2.25, zev: 0 }];

      const serien = privat().optionenMesspunkt([
        { einheit: mockConsumer, daten: messwerte },
        { einheit: mockProducer, daten: produktion },
        { einheit: mockRuecklieferung, daten: ruecklieferung }
      ], [], true)['series'] as { name: string; data: number[][] }[];

      // Verbraucher unveraendert.
      expect(serien[0].data.map(p => p[1])).toEqual([1.5, 2.25, 2.25]);
      // Produzent gespiegelt: positiv, in Reihe und Summe. Die 0 bleibt eine 0 (kein -0).
      expect(serien[1].data.map(p => p[1])).toEqual([0, 1.288, 1.288]);
      expect(Object.is(serien[1].data[0][1], -0)).toBe(false);
      expect(serien[1].name).toBe('Solar Anlage (Σ 1.288 KWH)');
      // Die Ruecklieferung ist kein Produzent und bleibt unter der Nulllinie.
      expect(serien[2].data.map(p => p[1])).toEqual([-2.25, -2.25]);
      expect(serien[2].name).toBe('Rücklieferung an BKW (Σ -2.250 KWH)');
    });

    it('should not mirror producers unless asked to', () => {
      const produktion: MesswertData[] = [{ zeit: '2026-01-15T10:15:00', total: -1.288, zev: -1 }];

      const serien = privat().optionenMesspunkt([{ einheit: mockProducer, daten: produktion }])['series'] as
        { name: string; data: number[][] }[];

      expect(serien[0].data.map(p => p[1])).toEqual([-1.288, -1.288]);
      expect(serien[0].name).toBe('Solar Anlage (Σ -1.288 KWH)');
    });

    it('should return to the single charts on Anzeigen', () => {
      component.selectedEinheiten = [mockConsumer, mockProducer];
      component.onVergleich();
      expect(component.modus).toBe('vergleich');

      component.onSubmit();

      expect(component.modus).toBe('einzeln');
      expect(component.charts.map(c => c.schluessel)).toEqual(['1', '2']);
    });

    it('should keep the comparison when paging and jumping to today', () => {
      component.selectedEinheiten = [mockConsumer, mockProducer];
      component.dateFrom = '2026-10-13';
      component.dateTo = '2026-10-13';
      component.onVergleich();

      component.onBlaettern(1);
      expect(component.dateFrom).toBe('2026-10-14');
      expect(component.charts.map(c => c.schluessel)).toEqual(['vergleich']);

      component.onHeute();
      expect(component.charts.map(c => c.schluessel)).toEqual(['vergleich']);
    });

    it('should stay with the single charts when paging after returning from the comparison', () => {
      component.selectedEinheiten = [mockConsumer, mockProducer];
      component.dateFrom = '2026-10-13';
      component.dateTo = '2026-10-13';
      component.onVergleich();
      component.onSubmit();

      component.onBlaettern(1);

      expect(component.charts.map(c => c.schluessel)).toEqual(['1', '2']);
    });

    it('should fall back to the single chart when the selection shrank below two einheiten', () => {
      // Nach dem Vergleich die Auswahl auf eine Einheit verkleinert, dann geblaettert: Ein
      // "Vergleich: 1 Einheiten" mit einer Linie waere keiner.
      component.selectedEinheiten = [mockConsumer, mockProducer];
      component.dateFrom = '2026-10-13';
      component.dateTo = '2026-10-13';
      component.onVergleich();

      component.onSelectionChange([mockConsumer]);
      component.onBlaettern(1);

      expect(component.modus).toBe('einzeln');
      expect(component.charts.map(c => c.schluessel)).toEqual(['1']);
      expect(component.charts[0].vergleich).toBeUndefined();
    });

    it('should do nothing without two einheiten', () => {
      component.selectedEinheiten = [mockConsumer];

      component.onVergleich();

      expect(messwerteServiceSpy.getMesswerteByEinheit).not.toHaveBeenCalled();
      expect(component.modus).toBe('einzeln');
      expect(component.charts).toEqual([]);
    });
  });

  // Specs/EChart.md, FR-10: Einspeisetarif einblenden
  describe('Einspeisetarif', () => {
    const checkbox = (): HTMLInputElement | null =>
      fixture.nativeElement.querySelector('#grafikEinspeisetarif');
    type Reihe = { id?: string; name: string; yAxisIndex?: number; step?: string; data: number[][];
      lineStyle: { color?: string; type?: string } };

    it('should offer the checkbox right of the comparison button, unchecked', () => {
      const slot: HTMLElement = fixture.nativeElement.querySelector('app-quarter-selector');
      const ids = Array.from(slot.querySelectorAll('button, input[type="checkbox"]')).map(e => e.id);

      expect(ids.indexOf('grafikEinspeisetarif')).toBe(ids.indexOf('grafikVergleich') + 1);
      expect(checkbox()!.checked).toBe(false);
      expect(slot.querySelector('label[for="grafikEinspeisetarif"]')?.textContent)
        .toContain('GRAFIK_EINSPEISETARIF');
    });

    it('should hide the checkbox without the permission tarife:manage', () => {
      // Die Preiszeitreihe verlangt tarife:manage - ohne sie liefe jeder Klick in ein 403.
      rollen = [];
      const ohneRecht = TestBed.createComponent(MesswerteChartComponent);
      ohneRecht.detectChanges();

      expect(ohneRecht.componentInstance.darfEinspeisetarif).toBe(false);
      expect(ohneRecht.nativeElement.querySelector('#grafikEinspeisetarif')).toBeNull();
      // Die uebrigen Schaltflaechen bleiben.
      expect(ohneRecht.nativeElement.querySelector('#grafikVergleich')).not.toBeNull();
    });

    it('should hide the checkbox while the feature PREISZEITREIHE is off', async () => {
      preiszeitreiheAktiv.set(false);
      fixture.detectChanges();
      await fixture.whenStable();

      expect(checkbox()).toBeNull();
    });

    it('should disable the checkbox while loading', () => {
      // Ein Umschalten mitten im Laden startete einen zweiten Lauf neben dem ersten.
      component.loading = true;
      fixture.detectChanges();
      expect(checkbox()!.disabled).toBe(true);

      component.loading = false;
      fixture.detectChanges();
      expect(checkbox()!.disabled).toBe(false);
    });

    it('should not ask for prices while the checkbox is off', () => {
      component.selectedEinheiten = [mockConsumer];

      component.onSubmit();

      expect(preiszeitreiheServiceSpy.getPunkte).not.toHaveBeenCalled();
      expect(component.messageType).toBe('success');
    });

    it('should only remember the choice while no chart is shown', () => {
      component.selectedEinheiten = [mockConsumer];

      checkbox()!.click();

      expect(component.mitEinspeisetarif).toBe(true);
      expect(messwerteServiceSpy.getMesswerteByEinheit).not.toHaveBeenCalled();
      expect(preiszeitreiheServiceSpy.getPunkte).not.toHaveBeenCalled();
    });

    it('should load the prices of the period once, together with the measurements', () => {
      messwerteServiceSpy.getMesswerteByEinheit.mockReturnValue(of(messwerte));
      component.selectedEinheiten = [mockConsumer, mockProducer];
      component.dateFrom = '2026-01-15';
      component.dateTo = '2026-01-16';
      component.mitEinspeisetarif = true;

      component.onSubmit();

      // Ein Abruf fuer alle Diagramme, nicht einer je Einheit.
      expect(preiszeitreiheServiceSpy.getPunkte).toHaveBeenCalledTimes(1);
      expect(preiszeitreiheServiceSpy.getPunkte).toHaveBeenCalledWith('2026-01-15', '2026-01-16');
      expect(component.charts.length).toBe(2);
      expect(component.message).toBe('4 DATENPUNKTE_FUER 2 EINHEITEN_GELADEN');
      expect((component as unknown as { preise: PreiszeitreihePunkt[] }).preise).toEqual(preise);
    });

    it('should reload with and without prices when toggled while charts are shown', () => {
      component.selectedEinheiten = [mockConsumer];
      component.onSubmit();
      fixture.detectChanges();
      expect(messwerteServiceSpy.getMesswerteByEinheit).toHaveBeenCalledTimes(1);

      checkbox()!.click();
      expect(messwerteServiceSpy.getMesswerteByEinheit).toHaveBeenCalledTimes(2);
      expect(preiszeitreiheServiceSpy.getPunkte).toHaveBeenCalledTimes(1);

      // Waehrend des Ladens ist die Checkbox gesperrt - erst die Ansicht nachziehen.
      fixture.detectChanges();
      checkbox()!.click();
      expect(messwerteServiceSpy.getMesswerteByEinheit).toHaveBeenCalledTimes(3);
      // Abgewaehlt: kein weiterer Preisabruf, und die Preise des letzten Laufs sind weg.
      expect(preiszeitreiheServiceSpy.getPunkte).toHaveBeenCalledTimes(1);
      expect((component as unknown as { preise: PreiszeitreihePunkt[] }).preise).toEqual([]);
    });

    it('should ask for the prices once in the comparison too', () => {
      component.selectedEinheiten = [mockConsumer, mockProducer];
      component.mitEinspeisetarif = true;

      component.onVergleich();

      expect(component.charts.map(c => c.schluessel)).toEqual(['vergleich']);
      expect(preiszeitreiheServiceSpy.getPunkte).toHaveBeenCalledTimes(1);
    });

    it('should still show the measurements and report the cause when the prices fail', () => {
      // Das Backend antwortet im Klartext, z.B. bei einer Spanne ueber 366 Tage.
      preiszeitreiheServiceSpy.getPunkte.mockReturnValue(
        throwError(() => ({ status: 400, error: 'Zeitraum zu lang' })));
      messwerteServiceSpy.getMesswerteByEinheit.mockReturnValue(of(messwerte));
      component.selectedEinheiten = [mockConsumer];
      component.mitEinspeisetarif = true;

      component.onSubmit();

      expect(component.charts.length).toBe(1);
      expect(component.loading).toBe(false);
      expect(component.message).toBe('Zeitraum zu lang');
      expect(component.messageType).toBe('error');
    });

    it('should show the key of the backend when the failure carries one', () => {
      // So antwortet der GlobalExceptionHandler, z.B. bei abgeschaltetem Feature (403).
      preiszeitreiheServiceSpy.getPunkte.mockReturnValue(
        throwError(() => ({ status: 403, error: { error: 'FEATURE_FLAG_DEAKTIVIERT' } })));
      component.selectedEinheiten = [mockConsumer];
      component.mitEinspeisetarif = true;

      component.onSubmit();

      // Der Key geht durch translate() - im Test liefert der Mock ihn unveraendert zurueck.
      expect(translationServiceSpy.translate).toHaveBeenCalledWith('FEATURE_FLAG_DEAKTIVIERT');
      expect(component.message).toBe('FEATURE_FLAG_DEAKTIVIERT');
      expect(component.messageType).toBe('error');
      expect(component.charts.length).toBe(1);
    });

    it('should not ask for prices once the feature is switched off, even with the box ticked', () => {
      // Abgeschaltet waehrend das Haekchen sitzt: Die Checkbox verschwindet, das Haekchen liesse
      // sich nicht mehr entfernen - ohne die Wache liefe jedes Laden in ein 403.
      component.selectedEinheiten = [mockConsumer];
      component.mitEinspeisetarif = true;
      preiszeitreiheAktiv.set(false);

      component.onSubmit();

      expect(preiszeitreiheServiceSpy.getPunkte).not.toHaveBeenCalled();
      expect(component.messageType).toBe('success');
    });

    it('should fall back to PREISE_ABRUF_FEHLGESCHLAGEN when the failure carries no text', () => {
      preiszeitreiheServiceSpy.getPunkte.mockReturnValue(
        throwError(() => ({ status: 502, error: { message: 'x' } })));
      component.selectedEinheiten = [mockConsumer];
      component.mitEinspeisetarif = true;

      component.onSubmit();

      // Kein [object Object] - ein Objekt-Rumpf ist kein Text.
      expect(component.message).toBe('PREISE_ABRUF_FEHLGESCHLAGEN');
      expect(component.messageType).toBe('error');
    });

    it('should warn when the period has no prices', () => {
      preiszeitreiheServiceSpy.getPunkte.mockReturnValue(of([]));
      component.selectedEinheiten = [mockConsumer];
      component.mitEinspeisetarif = true;

      component.onSubmit();

      expect(component.charts.length).toBe(1);
      expect(component.message).toBe('KEINE_PREISE_VORHANDEN');
      expect(component.messageType).toBe('warning');
    });

    it('should add the tariff as a dashed step line on its own right axis', () => {
      const optionen = privat().optionen(messwerte, preise);
      const achsen = optionen['yAxis'] as { name: string; position?: string }[];
      const serien = optionen['series'] as Reihe[];
      const tarif = serien[serien.length - 1];

      expect(achsen.length).toBe(2);
      expect(achsen[0].name).toBe('KWH');
      expect(achsen[1].name).toBe('PREIS_CHF_KWH');
      expect(achsen[1].position).toBe('right');
      // Total und ZEV bleiben, der Tarif kommt als dritte Reihe dazu.
      expect(serien.length).toBe(3);
      expect(tarif.name).toBe('GRAFIK_EINSPEISETARIF_CHF_KWH');
      expect(tarif.yAxisIndex).toBe(1);
      expect(tarif.step).toBe('end');
      expect(tarif.lineStyle.type).toBe('dashed');
      // Die Preise unveraendert, dazu der Abschlusspunkt des letzten Intervalls.
      expect(tarif.data.map(p => p[1])).toEqual([0.08125, 0.1234567, 0.1234567]);
      // Die rechte Achse braucht Platz fuer ihre Beschriftung.
      expect((optionen['grid'] as { right: number }).right).toBe(70);
      // Die Messreihen bleiben auf der linken Achse.
      expect(serien[0].yAxisIndex).toBeUndefined();
    });

    it('should draw the tariff in neutral grey and keep the grid to the energy axis', () => {
      // In jsdom greifen die Rueckfallwerte der Tokens: --color-gray-600 -> #666666.
      const optionen = privat().optionen(messwerte, preise);
      const achsen = optionen['yAxis'] as { splitLine: { show?: boolean } }[];
      const serien = optionen['series'] as Reihe[];

      expect(serien[2].lineStyle.color).toBe('#666666');
      // Zwei Gitter uebereinander waeren unlesbar: Die Hilfslinien gehoeren der Energie.
      expect(achsen[0].splitLine.show).toBeUndefined();
      expect(achsen[1].splitLine.show).toBe(false);
    });

    it('should still show the comparison when the prices fail', () => {
      preiszeitreiheServiceSpy.getPunkte.mockReturnValue(throwError(() => ({ status: 502 })));
      component.selectedEinheiten = [mockConsumer, mockProducer];
      component.mitEinspeisetarif = true;

      component.onVergleich();

      expect(component.charts.map(c => c.schluessel)).toEqual(['vergleich']);
      expect(component.message).toBe('PREISE_ABRUF_FEHLGESCHLAGEN');
    });

    it('should leave the chart unchanged without prices', () => {
      const optionen = privat().optionen(messwerte, []);

      expect(Array.isArray(optionen['yAxis'])).toBe(false);
      expect((optionen['series'] as Reihe[]).length).toBe(2);
      expect((optionen['grid'] as { right: number }).right).toBe(20);
    });

    it('should add the tariff to charts with one series per einheit too', () => {
      const optionen = privat().optionenMesspunkt([
        { einheit: mockBezug, daten: messwerte },
        { einheit: mockRuecklieferung, daten: messwerte }
      ], preise);
      const serien = optionen['series'] as Reihe[];

      expect(serien.length).toBe(3);
      expect(serien[2].id).toBe('einspeisetarif');
      // Grau und gestrichelt - keine Farbe der Reihenpalette.
      expect(serien.slice(0, 2).map(r => r.lineStyle.color)).not.toContain(serien[2].lineStyle.color);
    });

    it('should format the tariff in the tooltip as a price with five decimals', () => {
      const optionen = privat().optionen(messwerte, preise);
      const formatter = (optionen['tooltip'] as {
        formatter: (p: { seriesName: string; seriesId?: string; value: number[] }[]) => string;
      }).formatter;

      const text = formatter([
        { seriesName: 'Total', value: [0, 1.5] },
        { seriesName: 'Einspeisetarif (CHF/kWh)', seriesId: 'einspeisetarif', value: [0, 0.1234567] }
      ]);

      expect(text).toContain('Total: 1.500 KWH');
      // Fuenf Stellen wie in der Preiszeitreihe, ohne angehaengtes kWh.
      expect(text).toContain('Einspeisetarif (CHF/kWh): 0.12346');
      expect(text).not.toContain('0.12346 KWH');
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

  describe('Achsenbeschriftung', () => {
    it('should format the y axis labels in Swiss format', () => {
      const yAchse = privat().optionen(messwerte)['yAxis'] as
        { axisLabel: { formatter: (wert: number) => string } };

      // Hochkomma als Tausender-, Punkt als Dezimaltrennzeichen - auch an der Achse.
      expect(yAchse.axisLabel.formatter(1234.5)).toBe("1'234.500");
      expect(yAchse.axisLabel.formatter(-0.25)).toBe('-0.250');
    });

    it('should format tooltip values from 1000 with the thousands separator', () => {
      const optionen = privat().optionen(messwerte);
      const formatter = (optionen['tooltip'] as
        { formatter: (p: { seriesName: string; value: number[] }[]) => string }).formatter;

      expect(formatter([{ seriesName: 'Total', value: [0, 12345.6789] }]))
        .toContain("Total: 12'345.679 KWH");
    });
  });

  // Specs/EChart.md, FR-7: Nachladen der Bibliothek scheitert
  describe('Nachladen scheitert', () => {
    /** Wartet die Kette aus `onSubmit` ab: laden (scheitert), melden. */
    const warte = async (): Promise<void> => {
      await fixture.whenStable();
      await new Promise(resolve => setTimeout(resolve, 0));
      fixture.detectChanges();
    };
    let lader: ReturnType<typeof vi.fn<() => Promise<null>>>;

    beforeEach(() => {
      // Hier laeuft das echte Zeichnen - bis zum Lader, der nichts liefert.
      zeichnenSpy.mockRestore();
      lader = vi.fn<() => Promise<null>>().mockResolvedValue(null);
      (component as unknown as { lade: () => Promise<null> }).lade = lader;
    });

    it('should report DIAGRAMM_NICHT_LADBAR once and keep the page usable', async () => {
      messwerteServiceSpy.getMesswerteByEinheit.mockReturnValue(of(messwerte));
      component.selectedEinheiten = [mockConsumer, mockProducer];
      fixture.detectChanges();

      component.onSubmit();
      await warte();

      // Eine Meldung fuer zwei Einheiten, nicht eine je Einheit.
      expect(lader).toHaveBeenCalledTimes(1);
      expect(component.message).toBe('DIAGRAMM_NICHT_LADBAR');
      expect(component.messageType).toBe('error');
      expect(component.bibliothekFehlt).toBe(true);
      expect(component.bibliothekLaedt).toBe(false);
      // Die Panels stehen mit dem Hinweis, gezeichnet ist nichts.
      const hinweise: HTMLElement[] = Array.from(
        fixture.nativeElement.querySelectorAll('.messwerte-chart__hinweis'));
      expect(hinweise.map(h => h.textContent?.trim())).toEqual(
        ['DIAGRAMM_NICHT_LADBAR', 'DIAGRAMM_NICHT_LADBAR']);
      expect(component.charts.every(c => c.instanz === null)).toBe(true);
      // Die Seite bleibt bedienbar: nicht im Ladezustand, Anzeigen und Blaettern frei.
      expect(component.loading).toBe(false);
      expect(fixture.nativeElement.querySelector('#grafikAnzeigen').disabled).toBe(false);
      expect(component.kannBlaettern).toBe(true);
    });

    it('should not try to load again after a failure', async () => {
      component.selectedEinheiten = [mockConsumer];

      component.onSubmit();
      await warte();
      component.onSubmit();
      await warte();

      // Der Fehlschlag ist gemerkt: Der zweite Klick laedt die Messwerte, aber nicht die Bibliothek.
      expect(messwerteServiceSpy.getMesswerteByEinheit).toHaveBeenCalledTimes(2);
      expect(lader).toHaveBeenCalledTimes(1);
    });
  });

  // Specs/EChart.md, FR-7: Groessenanpassung
  describe('createChart', () => {
    let behaelter: HTMLElement;
    let beobachtet: Element[];
    let beobachterCallback: () => void;

    beforeEach(() => {
      behaelter = document.createElement('div');
      behaelter.id = 'chart-1';
      document.body.appendChild(behaelter);
      beobachtet = [];
      // jsdom kennt keinen ResizeObserver - und der Test will seinen Callback selbst ausloesen.
      vi.stubGlobal('ResizeObserver', class {
        constructor(callback: () => void) { beobachterCallback = callback; }
        observe(element: Element): void { beobachtet.push(element); }
        disconnect(): void { /* nichts */ }
      });
    });

    afterEach(() => {
      behaelter.remove();
      vi.unstubAllGlobals();
    });

    it('should observe the container and resize the chart when it changes', () => {
      const instanz = { setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn() };
      const init = vi.fn().mockReturnValue(instanz);
      (component as unknown as { echarts: unknown }).echarts = { init };
      const chartData = { schluessel: '1', einheiten: [mockConsumer], instanz: null };

      (component as unknown as {
        createChart: (c: typeof chartData, d: Map<number, MesswertData[]>) => void;
      }).createChart(chartData, new Map([[1, messwerte]]));

      // Gezeichnet in den Behaelter der Einheit, beobachtet wird genau dieser.
      expect(init).toHaveBeenCalledWith(behaelter);
      expect(instanz.setOption).toHaveBeenCalledTimes(1);
      expect(beobachtet).toEqual([behaelter]);
      expect(chartData.instanz).toBe(instanz);

      // Aendert der Behaelter seine Groesse, misst das Diagramm neu - ohne das bliebe es bei der
      // Groesse vom ersten Zeichnen.
      expect(instanz.resize).not.toHaveBeenCalled();
      beobachterCallback();
      expect(instanz.resize).toHaveBeenCalledTimes(1);
    });

    it('should draw one series per einheit into the comparison chart', () => {
      behaelter.id = 'chart-vergleich';
      const instanz = { setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn() };
      (component as unknown as { echarts: unknown }).echarts = { init: vi.fn().mockReturnValue(instanz) };
      const chartData = {
        schluessel: 'vergleich', einheiten: [mockConsumer, mockProducer], vergleich: true, instanz: null
      };

      // Die Produktion ist negativ gespeichert - so, wie sie aus dem Backend kommt.
      const produktion: MesswertData[] = [
        { zeit: '2026-01-15T10:00:00', total: -0.5, zev: -0.5 },
        { zeit: '2026-01-15T10:15:00', total: -1.25, zev: -1 }
      ];

      (component as unknown as {
        createChart: (c: typeof chartData, d: Map<number, MesswertData[]>) => void;
      }).createChart(chartData, new Map([[1, messwerte], [2, produktion]]));

      const optionen = instanz.setOption.mock.calls[0][0] as { series: { name: string; data: number[][] }[] };
      // Je Einheit ihr Total unter ihrem Namen - keine ZEV-Reihe. Die Produktion steht POSITIV:
      // Reihe und Summe sind gespiegelt, damit sie neben dem Verbrauch vergleichbar ist.
      expect(optionen.series.map(reihe => reihe.name)).toEqual(
        ['Wohnung A (Σ 3.750 KWH)', 'Solar Anlage (Σ 1.750 KWH)']);
      expect(optionen.series[0].data.map(punkt => punkt[1])).toEqual([1.5, 2.25, 2.25]);
      expect(optionen.series[1].data.map(punkt => punkt[1])).toEqual([0.5, 1.25, 1.25]);
    });

    it('should keep the stored sign of a producer in a messpunkt chart', () => {
      // Nur der Vergleich spiegelt. Truegen ein Produzent und eine andere Einheit denselben
      // Messpunkt, bliebe dort das gespeicherte Vorzeichen (FR-8.5).
      behaelter.id = 'chart-messpunkt-0';
      const instanz = { setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn() };
      (component as unknown as { echarts: unknown }).echarts = { init: vi.fn().mockReturnValue(instanz) };
      const chartData = {
        schluessel: 'messpunkt-0', einheiten: [mockProducer, mockConsumer], messpunkt: 'MP', instanz: null
      };
      const produktion: MesswertData[] = [{ zeit: '2026-01-15T10:00:00', total: -0.5, zev: -0.5 }];

      (component as unknown as {
        createChart: (c: typeof chartData, d: Map<number, MesswertData[]>) => void;
      }).createChart(chartData, new Map([[2, produktion], [1, messwerte]]));

      const optionen = instanz.setOption.mock.calls[0][0] as { series: { name: string; data: number[][] }[] };
      expect(optionen.series[0].name).toBe('Solar Anlage (Σ -0.500 KWH)');
      expect(optionen.series[0].data.map(punkt => punkt[1])).toEqual([-0.5, -0.5]);
    });

    it('should draw the loaded tariff into the chart', () => {
      const instanz = { setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn() };
      (component as unknown as { echarts: unknown }).echarts = { init: vi.fn().mockReturnValue(instanz) };
      (component as unknown as { preise: PreiszeitreihePunkt[] }).preise = preise;
      const chartData = { schluessel: '1', einheiten: [mockConsumer], instanz: null };

      (component as unknown as {
        createChart: (c: typeof chartData, d: Map<number, MesswertData[]>) => void;
      }).createChart(chartData, new Map([[1, messwerte]]));

      const optionen = instanz.setOption.mock.calls[0][0] as { series: { id?: string }[] };
      expect(optionen.series.map(reihe => reihe.id)).toEqual([undefined, undefined, 'einspeisetarif']);
    });

    it('should do nothing without a container', () => {
      const init = vi.fn();
      (component as unknown as { echarts: unknown }).echarts = { init };

      (component as unknown as {
        createChart: (c: unknown, d: Map<number, MesswertData[]>) => void;
      }).createChart({ schluessel: 'gibt-es-nicht', einheiten: [mockConsumer], instanz: null }, new Map());

      expect(init).not.toHaveBeenCalled();
      expect(beobachtet).toEqual([]);
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
