import { ComponentFixture, TestBed } from '@angular/core/testing';
import { createSpyObj, SpyObj } from '../../../testing/spy';
import { fakeAsync, tick } from '../../../testing/fake-async';
import { of, throwError } from 'rxjs';
import { PreiszeitreiheChartComponent } from './preiszeitreihe-chart.component';
import { PreiszeitreiheService } from '../../services/preiszeitreihe.service';
import { TranslationService } from '../../services/translation.service';
import { PreiszeitreiheDownload, PreiszeitreihePunkt } from '../../models/preiszeitreihe.model';

/**
 * Unit-Tests der Preiszeitreihe-Komponente (Specs/Preiszeitreihe.md, FR-3).
 *
 * <p><b>Das Zeichnen ist gestubbt.</b> ECharts wird zur Laufzeit dynamisch nachgeladen und braucht
 * ein gemessenes Element samt Canvas — in jsdom gibt es beides nicht. Geprüft wird deshalb die
 * Logik: Spannen, Blättern, Laden, Meldungen, und die Serien-Optionen als reine Funktion. Dass die
 * Bibliothek beide Serientypen wirklich registriert hat, kann nur ein E2E-Test zeigen — genau
 * daran ist das Balken-Diagramm zuerst gescheitert (nicht registrierter Typ zeichnet stumm nichts).
 */
describe('PreiszeitreiheChartComponent', () => {
  let component: PreiszeitreiheChartComponent;
  let fixture: ComponentFixture<PreiszeitreiheChartComponent>;

  let preiszeitreiheServiceSpy: SpyObj<PreiszeitreiheService>;
  let translationServiceSpy: SpyObj<TranslationService>;
  let zeichneSpy: ReturnType<typeof vi.spyOn>;

  const mockPunkte: PreiszeitreihePunkt[] = [
    { zeit: '2026-01-15T11:00:00', preis: 0.138 },
    { zeit: '2026-01-15T11:15:00', preis: 0.142 }
  ];

  const mockDownload: PreiszeitreiheDownload = {
    abgerufen: 96,
    neu: 90,
    aktualisiert: 6,
    uebersprungen: 0,
    publikation: '2026-08-27T15:50:00'
  };

  /** Zugriff auf die privaten Bausteine — Testcode darf das, Produktivcode nicht. */
  function privat(): {
    zeichne: () => Promise<void>;
    serie: (daten: number[][], farben: { linie: string }) => Record<string, unknown>;
    optionen: () => Record<string, unknown>;
  } {
    return component as unknown as ReturnType<typeof privat>;
  }

  function iso(datum: Date): string {
    const zwei = (w: number) => String(w).padStart(2, '0');
    return `${datum.getFullYear()}-${zwei(datum.getMonth() + 1)}-${zwei(datum.getDate())}`;
  }

  beforeEach(async () => {
    preiszeitreiheServiceSpy = createSpyObj<PreiszeitreiheService>('PreiszeitreiheService', [
      'getPunkte', 'download'
    ]);
    preiszeitreiheServiceSpy.getPunkte.mockReturnValue(of(mockPunkte));
    preiszeitreiheServiceSpy.download.mockReturnValue(of(mockDownload));

    translationServiceSpy = createSpyObj<TranslationService>('TranslationService', ['translate']);
    translationServiceSpy.translate.mockImplementation((key: string) =>
      key === 'PREISE_HERUNTERGELADEN' ? '{0} neu, {1} geaendert, Stand {2}' : key);

    await TestBed.configureTestingModule({
      imports: [PreiszeitreiheChartComponent],
      providers: [
        { provide: PreiszeitreiheService, useValue: preiszeitreiheServiceSpy },
        { provide: TranslationService, useValue: translationServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PreiszeitreiheChartComponent);
    component = fixture.componentInstance;
    // Stub VOR detectChanges: ngOnInit laedt und wuerde sonst ECharts anfassen.
    zeichneSpy = vi.spyOn(component as unknown as { zeichne: () => Promise<void> }, 'zeichne')
      .mockResolvedValue(undefined);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should start with the day view on today', () => {
      const heute = iso(new Date());

      expect(component.spanne).toBe('TAG');
      expect(component.von).toBe(heute);
      expect(component.bis).toBe(heute);
    });

    it('should load the points on init', () => {
      expect(preiszeitreiheServiceSpy.getPunkte).toHaveBeenCalledWith(component.von, component.bis);
      expect(component.punkte).toEqual(mockPunkte);
      expect(component.laedt).toBe(false);
    });

    it('should start with the line view', () => {
      expect(component.darstellung).toBe('LINIE');
    });
  });

  describe('setzeSpanne', () => {
    it('should set a full week from monday to sunday', () => {
      component.setzeSpanne('WOCHE');

      const von = new Date(component.von);
      const bis = new Date(component.bis);
      // 1 = Montag, 0 = Sonntag: Die Woche beginnt am Montag und endet am Sonntag.
      expect(von.getDay()).toBe(1);
      expect(bis.getDay()).toBe(0);
      expect(Math.round((bis.getTime() - von.getTime()) / 86400000)).toBe(6);
    });

    it('should set the current month from the first to the last day', () => {
      component.setzeSpanne('MONAT');

      const von = new Date(component.von);
      const bis = new Date(component.bis);
      expect(von.getDate()).toBe(1);
      // Der Folgetag von 'bis' liegt im naechsten Monat - also ist 'bis' der letzte Tag.
      expect(new Date(bis.getTime() + 86400000).getDate()).toBe(1);
      expect(von.getMonth()).toBe(bis.getMonth());
    });

    it('should reload for the new period', () => {
      preiszeitreiheServiceSpy.getPunkte.mockClear();

      component.setzeSpanne('MONAT');

      expect(preiszeitreiheServiceSpy.getPunkte)
        .toHaveBeenCalledWith(component.von, component.bis);
    });

    it('should keep the selected span visible', () => {
      component.setzeSpanne('WOCHE');
      expect(component.spanne).toBe('WOCHE');
    });
  });

  describe('blaettere', () => {
    it('should move a day view one day back', () => {
      component.setzeSpanne('TAG');
      const gestern = new Date();
      gestern.setDate(gestern.getDate() - 1);

      component.blaettere(-1);

      expect(component.von).toBe(iso(gestern));
      expect(component.bis).toBe(iso(gestern));
    });

    it('should move a week view seven days back', () => {
      component.setzeSpanne('WOCHE');
      const vorher = new Date(component.von);

      component.blaettere(-1);

      const nachher = new Date(component.von);
      expect(Math.round((vorher.getTime() - nachher.getTime()) / 86400000)).toBe(7);
      expect(nachher.getDay()).toBe(1);
    });

    it('should move a month view to the whole previous month', () => {
      component.spanne = 'MONAT';
      component.von = '2026-03-01';
      component.bis = '2026-03-31';

      component.blaettere(-1);

      // Februar 2026 hat 28 Tage - das Ende wird neu berechnet, nicht verschoben.
      expect(component.von).toBe('2026-02-01');
      expect(component.bis).toBe('2026-02-28');
    });

    it('should move a month view forward across the year boundary', () => {
      component.spanne = 'MONAT';
      component.von = '2026-12-01';
      component.bis = '2026-12-31';

      component.blaettere(1);

      expect(component.von).toBe('2027-01-01');
      expect(component.bis).toBe('2027-01-31');
    });

    it('should move a free period by its own length', () => {
      component.spanne = 'FREI';
      component.von = '2026-05-10';
      component.bis = '2026-05-12'; // drei Tage

      component.blaettere(1);

      expect(component.von).toBe('2026-05-13');
      expect(component.bis).toBe('2026-05-15');
    });

    it('should reload after paging', () => {
      preiszeitreiheServiceSpy.getPunkte.mockClear();

      component.blaettere(1);

      expect(preiszeitreiheServiceSpy.getPunkte).toHaveBeenCalled();
    });
  });

  describe('onDatumChange', () => {
    it('should switch the span to FREI and reload', () => {
      preiszeitreiheServiceSpy.getPunkte.mockClear();
      component.von = '2026-05-01';
      component.bis = '2026-05-31';

      component.onDatumChange();

      expect(component.spanne).toBe('FREI');
      expect(preiszeitreiheServiceSpy.getPunkte).toHaveBeenCalledWith('2026-05-01', '2026-05-31');
    });
  });

  describe('lade', () => {
    it('should reject a reversed period without calling the server', () => {
      preiszeitreiheServiceSpy.getPunkte.mockClear();
      component.von = '2026-05-31';
      component.bis = '2026-05-01';

      component.lade();

      expect(preiszeitreiheServiceSpy.getPunkte).not.toHaveBeenCalled();
      expect(component.messageType).toBe('error');
      expect(component.message).toBe('PREISE_ZEITRAUM_VERTAUSCHT');
    });

    it('should do nothing without dates', () => {
      preiszeitreiheServiceSpy.getPunkte.mockClear();
      component.von = '';
      component.bis = '';

      component.lade();

      expect(preiszeitreiheServiceSpy.getPunkte).not.toHaveBeenCalled();
    });

    it('should show the server message on failure and clear the points', () => {
      preiszeitreiheServiceSpy.getPunkte
        .mockReturnValue(throwError(() => ({ error: 'Der Zeitraum darf höchstens 366 Tage umfassen' })));

      component.lade();

      expect(component.punkte).toEqual([]);
      expect(component.laedt).toBe(false);
      expect(component.messageType).toBe('error');
      expect(component.message).toBe('Der Zeitraum darf höchstens 366 Tage umfassen');
    });

    it('should fall back to a translation key when the server sends no text', () => {
      preiszeitreiheServiceSpy.getPunkte.mockReturnValue(throwError(() => ({ status: 500 })));

      component.lade();

      expect(component.message).toBe('FEHLER_BEIM_LADEN_DER_DATEN');
    });

    it('should keep an empty result as an empty list', () => {
      preiszeitreiheServiceSpy.getPunkte.mockReturnValue(of([]));

      component.lade();

      expect(component.punkte).toEqual([]);
      expect(component.messageType).not.toBe('error');
    });
  });

  describe('onHerunterladen', () => {
    it('should report counts and the source timestamp', () => {
      component.onHerunterladen();

      expect(component.messageType).toBe('success');
      // 15:50 aus dem ISO-String, im Format dd.MM.yyyy HH:mm.
      expect(component.message).toBe('90 neu, 6 geaendert, Stand 27.08.2026 15:50');
      expect(component.laedtHerunter).toBe(false);
    });

    it('should show a dash when the source names no timestamp', () => {
      preiszeitreiheServiceSpy.download
        .mockReturnValue(of({ ...mockDownload, publikation: null }));

      component.onHerunterladen();

      expect(component.message).toContain('Stand –');
    });

    it('should reload the view after a download', () => {
      preiszeitreiheServiceSpy.getPunkte.mockClear();

      component.onHerunterladen();

      expect(preiszeitreiheServiceSpy.getPunkte).toHaveBeenCalled();
    });

    it('should show the server text on a failed download', () => {
      preiszeitreiheServiceSpy.download.mockReturnValue(
        throwError(() => ({ status: 502, error: 'Die Quelle ist nicht erreichbar' })));

      component.onHerunterladen();

      expect(component.messageType).toBe('error');
      expect(component.message).toBe('Die Quelle ist nicht erreichbar');
      expect(component.laedtHerunter).toBe(false);
    });

    it('should fall back to a translation key without a server text', () => {
      preiszeitreiheServiceSpy.download.mockReturnValue(throwError(() => ({ status: 0 })));

      component.onHerunterladen();

      expect(component.message).toBe('PREISE_ABRUF_FEHLGESCHLAGEN');
    });
  });

  describe('setzeDarstellung', () => {
    it('should switch to bars without reloading', () => {
      preiszeitreiheServiceSpy.getPunkte.mockClear();

      component.setzeDarstellung('BALKEN');

      expect(component.darstellung).toBe('BALKEN');
      // Entscheidend: Es aendert sich die Sicht, nicht die Daten.
      expect(preiszeitreiheServiceSpy.getPunkte).not.toHaveBeenCalled();
    });

    it('should redraw when the view changes', () => {
      zeichneSpy.mockClear();

      component.setzeDarstellung('BALKEN');

      expect(zeichneSpy).toHaveBeenCalled();
    });

    it('should do nothing when the same view is selected again', () => {
      zeichneSpy.mockClear();

      component.setzeDarstellung('LINIE');

      expect(zeichneSpy).not.toHaveBeenCalled();
    });
  });

  describe('serie', () => {
    const daten = [[1, 0.138], [2, 0.142]];
    const farben = { linie: '#4CAF50' };

    it('should build a step line without an area fill', () => {
      component.darstellung = 'LINIE';

      const serie = privat().serie(daten, farben);

      expect(serie['type']).toBe('line');
      expect(serie['step']).toBe('end');
      // Keine Flaechenfuellung: Eine gefuellte Flaeche liest sich als Summe ueber die Zeit,
      // und aufsummierte Preise sind sinnlos. Genau das war zuerst falsch.
      expect(serie['areaStyle']).toBeUndefined();
    });

    it('should build bars with a maximum width', () => {
      component.darstellung = 'BALKEN';

      const serie = privat().serie(daten, farben);

      expect(serie['type']).toBe('bar');
      expect(serie['barMaxWidth']).toBe(24);
    });

    it('should not connect gaps in the line', () => {
      component.darstellung = 'LINIE';

      expect(privat().serie(daten, farben)['connectNulls']).toBe(false);
    });
  });

  describe('optionen', () => {
    it('should offer zoom inside and as a slider', () => {
      const optionen = privat().optionen();

      const dataZoom = optionen['dataZoom'] as { type: string }[];
      expect(dataZoom.map(z => z.type)).toEqual(['inside', 'slider']);
    });

    it('should use a time axis', () => {
      expect((privat().optionen()['xAxis'] as { type: string })['type']).toBe('time');
    });

    it('should turn the points into timestamps', () => {
      const serien = privat().optionen()['series'] as { data: number[][] }[];

      expect(serien[0].data).toHaveLength(2);
      expect(serien[0].data[0][0]).toBe(new Date('2026-01-15T11:00:00').getTime());
      expect(serien[0].data[0][1]).toBe(0.138);
    });
  });

  describe('messages', () => {
    it('should auto-dismiss the success message after 5s', fakeAsync(() => {
      component.onHerunterladen();
      expect(component.message).not.toBe('');

      tick(5000);

      expect(component.message).toBe('');
    }));

    it('should keep an error message', fakeAsync(() => {
      preiszeitreiheServiceSpy.download.mockReturnValue(
        throwError(() => ({ error: 'Die Quelle ist nicht erreichbar' })));

      component.onHerunterladen();
      tick(5000);

      expect(component.message).toBe('Die Quelle ist nicht erreichbar');
    }));

    it('should clear the message when dismissed', () => {
      component.onHerunterladen();

      component.dismissMessage();

      expect(component.message).toBe('');
      expect(component.messageType).toBe('');
    });
  });
});
