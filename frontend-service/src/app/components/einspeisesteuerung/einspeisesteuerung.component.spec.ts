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
      verfahren: 'REGEL',
      ladeplanBatterieladung: null,
      prognoseUeberschuss: null,
      gti: null,
      prognoseFaktor: null,
      rang: null,
      rangBenoetigt: null,
      kapazitaetFrei: null,
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
    optionen: () => {
      series: {
        name: string,
        data: unknown[],
        itemStyle?: { opacity?: number },
        markArea?: { itemStyle?: { opacity?: number } }
      }[],
      grid: { bottom: number }
    };
    achseMinMitBaendern: () => number;
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

  // ==================== Die Schattenrechnung (FR-1a) ====================

  describe('Schattenrechnung', () => {
    /** Ein Entscheid, fuer den die Merit-Order gerechnet hat. */
    function mitLadeplan(zeit: string, ladeplan: string, batterieladung = 'FREI') {
      return entscheid(zeit, {
        batterieladung: batterieladung as 'FREI' | 'GESPERRT',
        ladeplanBatterieladung: ladeplan,
        rang: 34,
        rangBenoetigt: 12
      });
    }

    describe('hatLadeplan', () => {
      it('should be false when no interval was planned', () => {
        component.entscheide = [entscheid('2026-10-03T10:00:00')];

        expect(component.hatLadeplan()).toBe(false);
      });

      it('should be true as soon as one interval has a result', () => {
        component.entscheide = [
          entscheid('2026-10-03T10:00:00'),
          mitLadeplan('2026-10-03T10:15:00', 'GESPERRT')
        ];

        expect(component.hatLadeplan()).toBe(true);
      });
    });

    /**
     * <b>Die Kennzahl der Schattenrechnung.</b> Weichen die Verfahren selten ab, ist der Gewinn
     * klein und das Regelwerk genuegt — dann waere das Umschalten die falsche Entscheidung.
     */
    describe('abweichungen', () => {
      it('should count only intervals where both methods disagree', () => {
        component.entscheide = [
          mitLadeplan('2026-10-03T10:00:00', 'GESPERRT', 'FREI'),   // abweichend
          mitLadeplan('2026-10-03T10:15:00', 'FREI', 'FREI'),       // gleich
          mitLadeplan('2026-10-03T10:30:00', 'FREI', 'GESPERRT'),   // abweichend
          entscheid('2026-10-03T10:45:00')                          // nicht gerechnet
        ];

        expect(component.abweichungen()).toBe(2);
      });

      /**
       * Ein nicht gerechnetes Intervall ist <b>keine</b> Abweichung.
       *
       * <p>Sonst zaehlte jede Nacht und jeder Rueckfall mit, und die Kennzahl saehe nach einem
       * grossen Unterschied aus, wo gar nichts verglichen wurde.
       */
      it('should not count intervals the merit order could not plan', () => {
        component.entscheide = [
          entscheid('2026-10-03T02:00:00', { batterieladung: 'FREI' }),
          entscheid('2026-10-03T02:15:00', { batterieladung: 'GESPERRT' })
        ];

        expect(component.abweichungen()).toBe(0);
      });
    });

    describe('Zustandsband', () => {
      /** Ohne Schattenrechnung bleibt es bei zwei Baendern — wie vor der Ladeplanung. */
      it('should not draw the shadow band without planning results', () => {
        component.entscheide = [entscheid('2026-10-03T10:00:00')];

        const namen = privat().optionen().series.map(s => s.name);

        expect(namen).not.toContain('STEUERUNG_LADEPLAN_GESPERRT');
        expect(namen).toContain('STEUERUNG_EINSPEISUNG');
      });

      it('should draw the shadow band when planning results exist', () => {
        component.entscheide = [mitLadeplan('2026-10-03T10:00:00', 'GESPERRT')];

        const namen = privat().optionen().series.map(s => s.name);

        expect(namen).toContain('STEUERUNG_LADEPLAN_GESPERRT');
        expect(namen).toContain('STEUERUNG_BATTERIELADUNG');
        expect(namen).toContain('STEUERUNG_EINSPEISUNG');
      });

      /**
       * <b>Legendensymbol und Flaeche tragen denselben Stil.</b>
       *
       * <p>Die Legende zeichnet aus dem {@code itemStyle} der SERIE, die Flaeche aus dem der
       * {@code markArea}. Zuerst stand die Deckkraft nur bei der Flaeche: In der Legende sahen
       * beide Baender gleich aus, obwohl sie es im Diagramm nicht sind — wer sie las, ordnete das
       * falsche Band zu.
       */
      it('should style legend symbol and area identically', () => {
        component.entscheide = [mitLadeplan('2026-10-03T10:00:00', 'GESPERRT', 'GESPERRT')];

        const serien = privat().optionen().series;
        const batterie = serien.find(s => s.name === 'STEUERUNG_BATTERIELADUNG');
        const schatten = serien.find(s => s.name === 'STEUERUNG_LADEPLAN_GESPERRT');

        expect(batterie?.itemStyle?.opacity).toBe(batterie?.markArea?.itemStyle?.opacity);
        expect(schatten?.itemStyle?.opacity).toBe(schatten?.markArea?.itemStyle?.opacity);
      });

      /** Das Schattenband ist blasser — es zeigt einen Zustand, der gegolten HAETTE. */
      it('should draw the shadow band fainter than the real one', () => {
        component.entscheide = [mitLadeplan('2026-10-03T10:00:00', 'GESPERRT', 'GESPERRT')];

        const serien = privat().optionen().series;
        const batterie = serien.find(s => s.name === 'STEUERUNG_BATTERIELADUNG');
        const schatten = serien.find(s => s.name === 'STEUERUNG_LADEPLAN_GESPERRT');

        expect(schatten!.itemStyle!.opacity!).toBeLessThan(batterie!.itemStyle!.opacity!);
      });

      /**
       * <b>Die Legende braucht mit dem siebten Eintrag zwei Zeilen</b> — und wuchs zuvor nach oben
       * in die Zeitachse hinein: Die Uhrzeiten standen mitten in den Legendentexten.
       *
       * <p>Nicht fest auf den groesseren Wert gesetzt, weil sonst an jedem Tag ohne
       * Schattenrechnung ein leerer Streifen bliebe.
       */
      it('should reserve more room below for the two-line legend', () => {
        component.entscheide = [entscheid('2026-10-03T10:00:00')];
        const ohne = privat().optionen().grid.bottom;

        component.entscheide = [mitLadeplan('2026-10-03T10:00:00', 'GESPERRT')];
        const mit = privat().optionen().grid.bottom;

        expect(mit).toBeGreaterThan(ohne);
      });

      /**
       * <b>Die Achse reicht nur dann tiefer, wenn das dritte Band wirklich da ist.</b>
       *
       * <p>Fest auf drei Ebenen gesetzt bliebe an jedem Tag vor der Schattenrechnung ein leerer
       * Streifen unter den Baendern — und die Kurven waeren flacher, ohne Grund.
       */
      it('should lower the axis only when the shadow band is drawn', () => {
        component.entscheide = [entscheid('2026-10-03T10:00:00')];
        const ohne = privat().achseMinMitBaendern();

        component.entscheide = [mitLadeplan('2026-10-03T10:00:00', 'GESPERRT')];
        const mit = privat().achseMinMitBaendern();

        expect(mit).toBeLessThan(ohne);
      });
    });

    describe('tooltip', () => {
      /** Ohne Ergebnis der Merit-Order bleibt der Tooltip so hoch wie bisher. */
      it('should omit the charging plan line when nothing was planned', () => {
        component.entscheide = [entscheid('2026-10-03T10:00:00')];

        expect(privat().tooltip(ms('2026-10-03T10:00:00')))
          .not.toContain('STEUERUNG_LADEPLAN');
      });

      it('should show state and rank in a single line', () => {
        component.entscheide = [mitLadeplan('2026-10-03T10:00:00', 'GESPERRT', 'GESPERRT')];

        const text = privat().tooltip(ms('2026-10-03T10:00:00'));

        expect(text).toContain('STEUERUNG_LADEPLAN');
        expect(text).toContain('34/12');
      });

      /**
       * Eine Abweichung wird hervorgehoben.
       *
       * <p>Sie ist der Ertrag der Schattenrechnung und ginge in einer Liste aus dreizehn Zeilen
       * sonst unter.
       */
      it('should mark a disagreement between the two methods', () => {
        component.entscheide = [mitLadeplan('2026-10-03T10:00:00', 'GESPERRT', 'FREI')];

        const text = privat().tooltip(ms('2026-10-03T10:00:00'));

        expect(text).toContain('STEUERUNG_ABWEICHUNG');
        expect(text).toContain('<b>');
      });

      it('should not mark agreement', () => {
        component.entscheide = [mitLadeplan('2026-10-03T10:00:00', 'FREI', 'FREI')];

        expect(privat().tooltip(ms('2026-10-03T10:00:00')))
          .not.toContain('STEUERUNG_ABWEICHUNG');
      });
    });
  });

  // ==================== Tagessummen (FR-5) ====================

  describe('Tagessummen', () => {
    /**
     * Die Produktion wird <b>verrechnet</b> summiert — wie die gelbe Kurve. Sonst stünde unter
     * „Produktion (mit Speicher)" eine Zahl, die zu keiner Kurve passt.
     */
    it('should sum the production including battery charging', () => {
      component.entscheide = [
        entscheid('2026-10-07T12:00:00', { produktion: 0.3, speicherLadung: 1.2, speicherEntladung: 0 }),
        entscheid('2026-10-07T12:15:00', { produktion: 0.5, speicherLadung: 0, speicherEntladung: 0 })
      ];

      expect(component.summeProduktion()).toBeCloseTo(2.0, 3);
    });

    it('should sum the consumption', () => {
      component.entscheide = [
        entscheid('2026-10-07T12:00:00', { verbrauch: 0.4 }),
        entscheid('2026-10-07T12:15:00', { verbrauch: 0.6 })
      ];

      expect(component.summeVerbrauch()).toBeCloseTo(1.0, 3);
    });

    /** Ohne Entscheide „–", nicht 0: Der Grund ist „noch nichts ausgewertet", nicht „nichts produziert". */
    it('should report no sum without decisions', () => {
      component.entscheide = [];

      expect(component.summeProduktion()).toBeNull();
      expect(component.summeVerbrauch()).toBeNull();
      expect(component.summeAnzeige(component.summeProduktion())).toBe('–');
    });

    it('should sum the expected generation over the whole day', () => {
      component.prognose = [
        prognosepunkt('2026-10-07T10:00:00', 200, 1.0),
        prognosepunkt('2026-10-07T20:00:00', 300, 1.5)   // noch in der Zukunft - zählt mit
      ];

      expect(component.summeErwarteteErzeugung()).toBeCloseTo(2.5, 3);
    });

    /** Ohne gelernten Faktor „–" — eine 0 sähe aus wie „keine Sonne erwartet". */
    it('should report no expected sum while no factor is learned', () => {
      component.prognose = [prognosepunkt('2026-10-07T10:00:00', 200, null)];

      expect(component.summeErwarteteErzeugung()).toBeNull();
    });

    it('should format a sum with unit', () => {
      expect(component.summeAnzeige(12.3456)).toBe('12.346 kWh');
    });

    describe('summeBis', () => {
      afterEach(() => {
        vi.useRealTimers();
      });

      /**
       * Heute: das <b>Ende</b> des letzten ausgewerteten Intervalls. Ein Intervall ab 14:15 läuft
       * bis 14:30 — mit dem Beginn stünde da eine Viertelstunde zu früh.
       */
      it('should name the end of the last interval today', () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date(2026, 9, 7, 14, 40));
        component.datum = '2026-10-07';
        component.entscheide = [
          entscheid('2026-10-07T14:00:00'),
          entscheid('2026-10-07T14:15:00')
        ];

        expect(component.summeBis()).toBe('STEUERUNG_BIS_ZEIT'.replace('{0}', '14:30'));
      });

      /** Ein vergangener Tag ist vollständig — kein Zusatz. */
      it('should add nothing for a past day', () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date(2026, 9, 7, 14, 40));
        component.datum = '2026-10-06';
        component.entscheide = [entscheid('2026-10-06T23:45:00')];

        expect(component.summeBis()).toBe('');
      });
    });

    describe('summeErwarteteErzeugungBisJetzt', () => {
      afterEach(() => {
        vi.useRealTimers();
      });

      /**
       * Nur die Viertelstunden, die auch die Produktionssumme enthält — erst dann sind die beiden
       * vergleichbar. Der Punkt um 15:00 liegt nach dem letzten Entscheid und zählt nicht.
       */
      it('should sum the forecast up to the last evaluated interval', () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date(2026, 9, 7, 14, 40));
        component.datum = '2026-10-07';
        component.entscheide = [entscheid('2026-10-07T14:00:00'), entscheid('2026-10-07T14:15:00')];
        component.prognose = [
          prognosepunkt('2026-10-07T14:00:00', 200, 1.0),
          prognosepunkt('2026-10-07T14:15:00', 200, 0.5),
          prognosepunkt('2026-10-07T15:00:00', 200, 9.9)
        ];

        expect(component.summeErwarteteErzeugungBisJetzt()).toBeCloseTo(1.5, 3);
        expect(component.summeErwarteteErzeugung()).toBeCloseTo(11.4, 3);
      });

      /** Vergangener Tag: Sie wäre gleich der Tagessumme — keine eigene Zeile. */
      it('should be null for a past day', () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date(2026, 9, 7, 14, 40));
        component.datum = '2026-10-06';
        component.entscheide = [entscheid('2026-10-06T23:45:00')];
        component.prognose = [prognosepunkt('2026-10-06T12:00:00', 200, 1.0)];

        expect(component.summeErwarteteErzeugungBisJetzt()).toBeNull();
      });

      it('should be null while no factor is learned', () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date(2026, 9, 7, 14, 40));
        component.datum = '2026-10-07';
        component.entscheide = [entscheid('2026-10-07T14:00:00')];
        component.prognose = [prognosepunkt('2026-10-07T14:00:00', 200, null)];

        expect(component.summeErwarteteErzeugungBisJetzt()).toBeNull();
      });
    });

    it('should show the totals panel between chart and table', () => {
      component.loading = false;
      component.entscheide = [entscheid('2026-10-07T12:00:00', { verbrauch: 0.4 })];
      component.prognose = [prognosepunkt('2026-10-07T12:00:00', 200, 1.0)];
      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector('#summe-verbrauch')?.textContent).toContain('0.400 kWh');
      expect(el.querySelector('#summe-prognose')?.textContent).toContain('STEUERUNG_GANZER_TAG');

      // Reihenfolge: Diagramm, dann Summen, dann Protokoll
      const panelSumme = el.querySelector('#summe-verbrauch')!;
      const tabelle = el.querySelector('table.zev-table')!;
      expect(panelSumme.compareDocumentPosition(tabelle) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });
  });
});
