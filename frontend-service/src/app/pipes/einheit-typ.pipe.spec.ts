import { createSpyObj, SpyObj } from '../../testing/spy';
import { TestBed } from '@angular/core/testing';
import { EinheitTypPipe } from './einheit-typ.pipe';
import { EinheitTyp } from '../models/einheit.model';
import { TranslationService } from '../services/translation.service';

describe('EinheitTypPipe', () => {
  let pipe: EinheitTypPipe;
  let translationServiceSpy: SpyObj<TranslationService>;

  beforeEach(() => {
    translationServiceSpy = createSpyObj<TranslationService>('TranslationService', ['translate']);
    translationServiceSpy.translate.mockImplementation((key: string) => key);

    TestBed.configureTestingModule({
      providers: [
        EinheitTypPipe,
        { provide: TranslationService, useValue: translationServiceSpy }
      ]
    });

    pipe = TestBed.inject(EinheitTypPipe);
  });

  it('should create an instance', () => {
    expect(pipe).toBeTruthy();
  });

  describe('transform', () => {
    it('should map CONSUMER to KONSUMENT', () => {
      expect(pipe.transform(EinheitTyp.CONSUMER)).toBe('KONSUMENT');
    });

    it('should map PRODUCER to PRODUZENT', () => {
      expect(pipe.transform(EinheitTyp.PRODUCER)).toBe('PRODUZENT');
    });

    it('should map BEZUG to TYP_BEZUG', () => {
      expect(pipe.transform(EinheitTyp.BEZUG)).toBe('TYP_BEZUG');
    });

    it('should map RUECKLIEFERUNG to TYP_RUECKLIEFERUNG', () => {
      expect(pipe.transform(EinheitTyp.RUECKLIEFERUNG)).toBe('TYP_RUECKLIEFERUNG');
    });

    it('should map LADESTATION to TYP_LADESTATION', () => {
      expect(pipe.transform(EinheitTyp.LADESTATION)).toBe('TYP_LADESTATION');
    });

    it('should not label a LADESTATION as PRODUZENT', () => {
      // Der Default-Zweig faengt PRODUCER und alles Unbekannte ab - ein neuer Typ ohne
      // eigenen Fall erschiene faelschlich als "Produzent" (Specs/Ladestationen.md).
      expect(pipe.transform(EinheitTyp.LADESTATION)).not.toBe('PRODUZENT');
    });

    it('should use the translation service for every type', () => {
      pipe.transform(EinheitTyp.LADESTATION);
      expect(translationServiceSpy.translate).toHaveBeenCalledWith('TYP_LADESTATION');
    });

    it('should fall back to PRODUZENT for an unknown type', () => {
      expect(pipe.transform('UNBEKANNT')).toBe('PRODUZENT');
    });

    it('should fall back to PRODUZENT for an empty value', () => {
      expect(pipe.transform('')).toBe('PRODUZENT');
    });
  });
});
