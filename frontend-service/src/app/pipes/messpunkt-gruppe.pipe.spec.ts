import { createSpyObj, SpyObj } from '../../testing/spy';
import { TestBed } from '@angular/core/testing';
import { MesspunktGruppePipe } from './messpunkt-gruppe.pipe';
import { Einheit, EinheitTyp } from '../models/einheit.model';
import { TranslationService } from '../services/translation.service';

describe('MesspunktGruppePipe', () => {
  let pipe: MesspunktGruppePipe;
  let translationServiceSpy: SpyObj<TranslationService>;

  const bezug: Einheit = { id: 1, name: 'Bezug von BKW', typ: EinheitTyp.BEZUG, messpunkt: 'Bilanz' };
  const ruecklieferung: Einheit = {
    id: 2, name: 'Rücklieferung an BKW', typ: EinheitTyp.RUECKLIEFERUNG, messpunkt: 'Bilanz'
  };

  beforeEach(() => {
    translationServiceSpy = createSpyObj<TranslationService>('TranslationService', ['translate']);
    translationServiceSpy.translate.mockImplementation((key: string) => key);

    TestBed.configureTestingModule({
      providers: [
        MesspunktGruppePipe,
        { provide: TranslationService, useValue: translationServiceSpy }
      ]
    });

    pipe = TestBed.inject(MesspunktGruppePipe);
  });

  it('should create an instance', () => {
    expect(pipe).toBeTruthy();
  });

  describe('transform', () => {
    it('should join the einheiten and append the messpunkt', () => {
      expect(pipe.transform([bezug, ruecklieferung], 'Bilanz'))
        .toBe('Bezug von BKW [TYP_BEZUG] / Rücklieferung an BKW [TYP_RUECKLIEFERUNG] / [Bilanz]');
    });

    it('should render a single einheit without messpunkt as name and type', () => {
      const wohnung: Einheit = { id: 3, name: 'Wohnung A', typ: EinheitTyp.CONSUMER };

      expect(pipe.transform([wohnung])).toBe('Wohnung A [KONSUMENT]');
    });

    it('should translate the type on every call', () => {
      // Nicht `pure`: Nach einem Sprachwechsel muss derselbe Aufruf den neuen Text liefern.
      translationServiceSpy.translate.mockReturnValue('Purchase');

      expect(pipe.transform([bezug], 'Bilanz')).toBe('Bezug von BKW [Purchase] / [Bilanz]');
    });
  });
});
