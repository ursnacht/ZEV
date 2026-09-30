import { ComponentFixture, TestBed } from '@angular/core/testing';
import { createSpyObj, SpyObj } from '../../../testing/spy';
import { of, throwError } from 'rxjs';
import { EinspeisesteuerungComponent } from './einspeisesteuerung.component';
import { EinspeisesteuerungService } from '../../services/einspeisesteuerung.service';
import { PreiszeitreiheService } from '../../services/preiszeitreihe.service';
import { TranslationService } from '../../services/translation.service';
import { Prognosepunkt, Steuerentscheid } from '../../models/einspeisesteuerung.model';
import { PreiszeitreihePunkt } from '../../models/preiszeitreihe.model';

/**
 * Unit-Tests der Einspeisesteuerung (Specs/Einspeisesteuerung.md, FR-5).
 *
 * <p><b>Das Zeichnen ist gestubbt</b>, wie bei der Preiszeitreihe: ECharts wird dynamisch
 * nachgeladen und braucht ein gemessenes Element samt Canvas, das jsdom nicht hat. Geprüft wird
 * die Logik, die die Serien und den Tooltip erzeugt — als reine Funktionen.
 *
 * <p><b>Worauf es hier besonders ankommt.</b> Drei Reihen unterschiedlicher Länge liegen auf
 * derselben Zeitachse: Preise für den ganzen Tag, Entscheide nur für abgeschlossene Intervalle,
 * die Prognose mit Lücken. Fehler in dieser Zuordnung erzeugen keine Ausnahme und keine leere
 * Fläche — sie zeigen plausible Werte an der falschen Stelle. Genau das ist in diesem Feature
 * schon zweimal passiert.
 */
describe('EinspeisesteuerungComponent', () => {
  let component: EinspeisesteuerungComponent;
  let fixture: ComponentFixture<EinspeisesteuerungComponent>;

  let steuerungServiceSpy: SpyObj<EinspeisesteuerungService>;
  let preiszeitreiheServiceSpy: SpyObj<PreiszeitreiheService>;
  let translationServiceSpy: SpyObj<TranslationService>;

  /** Ein Entscheid mit allen Pflichtfeldern; einzelne werden je Test überschrieben. */
  function entscheid(zeit: string, ueberschreibungen: Partial<Steuerentscheid> = {}): Steuerentscheid {
    return {
      zeit,
      preis: 0.12,
      preisTiefRest: 0.08,
      produktion: 1.5,
      verbrauch: 0.4,
      bezug: 0,
      ruecklieferung: 0,
      soc: 50,
      speicherLadung: null,
      speicherEntladung: null,
      ueberschuss: 1.1,
      regel: 'LADEN',
      batterieladung: 'FREI',
      einspeisung: 'FREI',
      schwellwert: 0.15,
      speicherwert: 0.1,
      socMinimum: 20,
      socHysterese: 5,
      mindestAbstand: 0.02,
      ...ueberschreibungen
    } as Steuerentscheid;
  }

  function preispunkt(zeit: string, preis: number): PreiszeitreihePunkt {
    return { zeit, preis };
  }

  function prognosepunkt(zeit: string, gti: number,
                         erwarteteErzeugung: number | null = 0.5): Prognosepunkt {
    return { zeit, gti, erwarteteErzeugung, faktor: 0.005 };
  }

  /** Zugriff auf die privaten Bausteine — Testcode darf das, Produktivcode nicht. */
  function privat(): {
    zeichne: () => Promise<void>;
    preisReihe: () => (number | null)[][];
    prognoseReihe: () => (number | null)[][];
    tooltip: (zeitpunkt: number) => string;
    optionen: () => { series: { name: string, data: unknown[] }[] };
  } {
    return component as unknown as ReturnType<typeof privat>;
  }

  /** Millisekunden eines Ortszeit-Zeitstempels — dieselbe Umrechnung wie im Diagramm. */
  function ms(zeit: string): number {
    return new Date(zeit).getTime();
  }

  beforeEach(async () => {
    steuerungServiceSpy = createSpyObj<EinspeisesteuerungService>('EinspeisesteuerungService', [
      'getEntscheide', 'getEntscheideSimuliert', 'getPrognose', 'simuliere'
    ]);
    steuerungServiceSpy.getEntscheide.mockReturnValue(of([]));
    steuerungServiceSpy.getPrognose.mockReturnValue(of([]));

    preiszeitreiheServiceSpy = createSpyObj<PreiszeitreiheService>('PreiszeitreiheService', [
      'getPunkte', 'download'
    ]);
    preiszeitreiheServiceSpy.getPunkte.mockReturnValue(of([]));

    translationServiceSpy = createSpyObj<TranslationService>('TranslationService', ['translate']);
    translationServiceSpy.translate.mockImplementation((key: string) => key);

    await TestBed.configureTestingModule({
      imports: [EinspeisesteuerungComponent],
      providers: [
        { provide: EinspeisesteuerungService, useValue: steuerungServiceSpy },
        { provide: PreiszeitreiheService, useValue: preiszeitreiheServiceSpy },
        { provide: TranslationService, useValue: translationServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(EinspeisesteuerungComponent);
    component = fixture.componentInstance;
    // Stub VOR detectChanges: ngOnInit laedt und wuerde sonst ECharts anfassen.
    vi.spyOn(privat(), 'zeichne').mockResolvedValue(undefined);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  // ==================== Laden ====================

  describe('initialization', () => {
    it('should load decisions, forecast and prices on init', () => {
      expect(steuerungServiceSpy.getEntscheide).toHaveBeenCalledWith(component.datum);
      expect(steuerungServiceSpy.getPrognose).toHaveBeenCalledWith(component.datum);
      expect(preiszeitreiheServiceSpy.getPunkte).toHaveBeenCalledWith(component.datum,
        component.datum);
    });

    /**
     * Die Preise werden fuer **einen** Tag geholt, von und bis gleich.
     *
     * <p>Der Endpunkt nimmt eine Spanne, beide Grenzen einschliesslich. Ein zweiter Tag brachte
     * Punkte ausserhalb der Achse mit — sichtbar erst am Rand des Diagramms.
     */
    it('should request prices for exactly the displayed day', () => {
      const aufruf = preiszeitreiheServiceSpy.getPunkte.mock.calls[0];

      expect(aufruf[0]).toBe(aufruf[1]);
    });
  });

  /**
   * <b>Ein Fehler beim Preisabruf bleibt stumm.</b> Er darf die Seite nicht mit einer Meldung
   * belegen: Das Protokoll ist der Kern der Ansicht und bleibt ohne die Preiskurve vollstaendig
   * lesbar. Dasselbe gilt fuer die Prognose.
   */
  describe('error handling', () => {
    it('should keep the page usable when the price request fails', () => {
      preiszeitreiheServiceSpy.getPunkte.mockReturnValue(throwError(() => new Error('kaputt')));
      steuerungServiceSpy.getEntscheide.mockReturnValue(of([entscheid('2026-09-30T08:00:00')]));

      component.ladeTag();

      expect(component.preise).toEqual([]);
      expect(component.entscheide.length).toBe(1);
      expect(component.message).toBe('');
    });

    it('should show a message when the decisions fail to load', () => {
      steuerungServiceSpy.getEntscheide.mockReturnValue(
        throwError(() => ({ error: 'STEUERUNG_FEHLER_LADEN' })));

      component.ladeTag();

      expect(component.entscheide).toEqual([]);
      expect(component.messageType).toBe('error');
    });
  });

  // ==================== Die Preisreihe ====================

  describe('preisReihe', () => {
    /**
     * <b>Der Kern der Aenderung.</b> Die Preise decken den ganzen Tag ab, die Entscheide nur die
     * abgeschlossenen Intervalle. Kaeme die Kurve weiter aus den Entscheiden, endete sie mittags —
     * und gerade der Resttag ist die Frage, an der sich Warten oder Laden entscheidet.
     */
    it('should span the whole day even when decisions end at noon', () => {
      component.entscheide = [entscheid('2026-09-30T00:00:00'), entscheid('2026-09-30T00:15:00')];
      component.preise = [
        preispunkt('2026-09-30T00:00:00', 0.10),
        preispunkt('2026-09-30T00:15:00', 0.11),
        preispunkt('2026-09-30T12:00:00', 0.18),
        preispunkt('2026-09-30T23:45:00', 0.14)
      ];

      const reihe = privat().preisReihe();

      expect(reihe.length).toBe(4);
      expect(reihe[3]).toEqual([ms('2026-09-30T23:45:00'), 0.14]);
    });

    /**
     * Ohne Preise greift der Rueckfall auf die Entscheide.
     *
     * <p>Er ist keine Zier: Das Flag {@code PREISZEITREIHE} ist ein anderes als das der
     * Einspeisesteuerung, und die Abfrage kann scheitern. Dann soll die Kurve aussehen wie
     * vorher — nicht verschwinden.
     */
    it('should fall back to the prices in the decisions', () => {
      component.entscheide = [entscheid('2026-09-30T08:00:00', { preis: 0.155 })];
      component.preise = [];

      const reihe = privat().preisReihe();

      expect(reihe).toEqual([[ms('2026-09-30T08:00:00'), 0.155]]);
    });

    /** Ein negativer Preis bleibt erhalten — er ist die Begruendung fuer `PREIS_NEGATIV`. */
    it('should keep negative prices', () => {
      component.preise = [preispunkt('2026-09-30T12:00:00', -0.002)];

      expect(privat().preisReihe()[0][1]).toBe(-0.002);
    });

    /** Die Preisreihe landet auch wirklich in der ersten Serie des Diagramms. */
    it('should feed the price series of the chart', () => {
      component.entscheide = [entscheid('2026-09-30T00:00:00')];
      component.preise = [
        preispunkt('2026-09-30T00:00:00', 0.10),
        preispunkt('2026-09-30T23:45:00', 0.14)
      ];

      expect(privat().optionen().series[0].data.length).toBe(2);
    });
  });

  // ==================== Der Tooltip ====================

  describe('tooltip', () => {
    /**
     * <b>Aufgeloest ueber die Zeit, nicht ueber den Datenindex.</b>
     *
     * <p>Zuvor nahm der Tooltip {@code params[0].dataIndex} — den Index in der <i>Preisreihe</i> —
     * und schlug damit in den Entscheiden nach. Solange beide Reihen gleich lang waren, stimmte
     * das. Deckt die Preisreihe den ganzen Tag ab, zeigt derselbe Index in beiden Reihen auf
     * verschiedene Uhrzeiten.
     *
     * <p>Dieser Test stellt genau das her: Der Entscheid von 12:00 steht an Position 0 der
     * Entscheide, aber an Position 48 der Preise. Ueber den Index gesucht, kaeme der Entscheid von
     * 12:00 beim Preispunkt 00:00 heraus — plausible Zahlen zur falschen Zeit, und niemand saehe
     * es.
     */
    it('should resolve the decision by time, not by index', () => {
      component.entscheide = [entscheid('2026-09-30T12:00:00', { produktion: 3.737 })];
      component.preise = [
        preispunkt('2026-09-30T00:00:00', 0.10),
        preispunkt('2026-09-30T12:00:00', 0.18)
      ];

      const beiMittag = privat().tooltip(ms('2026-09-30T12:00:00'));
      const beiMitternacht = privat().tooltip(ms('2026-09-30T00:00:00'));

      expect(beiMittag).toContain('3.737');
      expect(beiMitternacht).not.toContain('3.737');
    });

    /**
     * Fuer ein Intervall des Resttages nennt der Tooltip Zeit, Preis und den Hinweis.
     *
     * <p>Ohne ihn wirkte die Kurve dort tot, und es bliebe offen, ob die Steuerung ausgefallen ist
     * oder das Intervall schlicht noch nicht an der Reihe war.
     */
    it('should show price and a hint for an interval without a decision', () => {
      component.entscheide = [entscheid('2026-09-30T08:00:00')];
      component.preise = [preispunkt('2026-09-30T20:00:00', 0.19)];

      const text = privat().tooltip(ms('2026-09-30T20:00:00'));

      expect(text).toContain('STEUERUNG_NOCH_KEIN_ENTSCHEID');
      expect(text).toContain('PREIS_CHF_KWH');
    });

    /** Ein Zeitpunkt ohne Entscheid UND ohne Preis ergibt nichts — kein leerer Rahmen. */
    it('should return nothing for a time with neither decision nor price', () => {
      component.entscheide = [];
      component.preise = [];

      expect(privat().tooltip(ms('2026-09-30T20:00:00'))).toBe('');
    });

    /** Liegt ein Entscheid vor, gilt er — auch wenn fuer dieselbe Zeit ein Preispunkt existiert. */
    it('should prefer the decision over the bare price', () => {
      component.entscheide = [entscheid('2026-09-30T08:00:00')];
      component.preise = [preispunkt('2026-09-30T08:00:00', 0.19)];

      const text = privat().tooltip(ms('2026-09-30T08:00:00'));

      expect(text).not.toContain('STEUERUNG_NOCH_KEIN_ENTSCHEID');
      expect(text).toContain('STEUERUNG_UEBERSCHUSS');
    });
  });

  // ==================== Die Prognosereihe ====================

  describe('prognoseReihe', () => {
    /**
     * <b>Luecken werden ausdruecklich mit {@code null} belegt.</b>
     *
     * <p>{@code connectNulls: false} greift nur bei einem gesetzten {@code null}. Fehlt eine ganze
     * Zeile — und genau so kommen Luecken aus der Datenbank —, zieht ECharts eine Gerade zwischen
     * den Nachbarn: Das Fehlen einer Vorhersage saehe aus wie eine Vorhersage.
     */
    it('should fill gaps with explicit null', () => {
      component.prognose = [
        prognosepunkt('2026-09-30T10:00:00', 200, 1.0),
        // 10:15 fehlt
        prognosepunkt('2026-09-30T10:30:00', 400, 2.0)
      ];

      const reihe = privat().prognoseReihe();

      expect(reihe.length).toBe(3);
      expect(reihe[1]).toEqual([ms('2026-09-30T10:15:00'), null]);
    });

    /** Ohne Prognose bleibt die Reihe leer — nicht eine Reihe aus lauter `null`. */
    it('should return an empty series without forecast data', () => {
      component.prognose = [];

      expect(privat().prognoseReihe()).toEqual([]);
    });

    /**
     * Eine erwartete Erzeugung von {@code null} — der Faktor ist noch nicht gelernt — bleibt
     * {@code null} und wird <b>nicht</b> zu 0.
     *
     * <p>Eine 0 hiesse „nichts erwartet". Der Grund ist aber „noch nicht gelernt", und das ist eine
     * andere Aussage: Die erste wuerde die spaetere Merit-Order zum Laden bewegen.
     */
    it('should keep a null expectation as null', () => {
      component.prognose = [prognosepunkt('2026-09-30T10:00:00', 200, null)];

      expect(privat().prognoseReihe()).toEqual([[ms('2026-09-30T10:00:00'), null]]);
    });
  });

  // ==================== Die verrechnete Produktion ====================

  describe('produktionVerrechnet', () => {
    /** Zaehlerwert plus Ladung minus Entladung — was in die Batterie ging, hat die Anlage erzeugt. */
    it('should add charging and subtract discharging', () => {
      const e = entscheid('2026-09-30T12:00:00',
        { produktion: 0.337, speicherLadung: 3.4, speicherEntladung: 0 });

      expect(component.produktionVerrechnet(e)).toBeCloseTo(3.737, 3);
    });

    /**
     * Das Ergebnis wird bei 0 gekappt.
     *
     * <p>Nachts meldet der Erzeugungszaehler durch die Rundung auf eine Nachkommastelle kleine
     * Werte, waehrend die Batterie entlaedt. Ohne die Kappung zeigte die Kurve eine negative
     * Produktion — der Fall vom 18.09.2026.
     */
    it('should never go below zero', () => {
      const e = entscheid('2026-09-30T02:00:00',
        { produktion: 0.2, speicherLadung: 0, speicherEntladung: 1.5 });

      expect(component.produktionVerrechnet(e)).toBe(0);
    });
  });
});
