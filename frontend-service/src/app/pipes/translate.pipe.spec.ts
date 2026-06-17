import { createSpyObj, SpyObj } from '../../testing/spy';
import { TestBed } from '@angular/core/testing';
import { TranslatePipe } from './translate.pipe';
import { TranslationService } from '../services/translation.service';

describe('TranslatePipe', () => {
  let pipe: TranslatePipe;
  let translationServiceSpy: SpyObj<TranslationService>;

  beforeEach(() => {
    translationServiceSpy = createSpyObj<TranslationService>('TranslationService', ['translate']);

    TestBed.configureTestingModule({
      providers: [
        TranslatePipe,
        { provide: TranslationService, useValue: translationServiceSpy }
      ]
    });

    pipe = TestBed.inject(TranslatePipe);
  });

  it('should create an instance', () => {
    expect(pipe).toBeTruthy();
  });

  describe('transform', () => {
    it('should return the translated string for a known key', () => {
      translationServiceSpy.translate.mockReturnValue('Einheit');

      const result = pipe.transform('einheit.title');

      expect(result).toBe('Einheit');
      expect(translationServiceSpy.translate).toHaveBeenCalledWith('einheit.title');
    });

    it('should return the key when translation is missing', () => {
      translationServiceSpy.translate.mockReturnValue('some.missing.key');

      const result = pipe.transform('some.missing.key');

      expect(result).toBe('some.missing.key');
    });

    it('should return empty string when translation is empty', () => {
      translationServiceSpy.translate.mockReturnValue('');

      const result = pipe.transform('empty.key');

      expect(result).toBe('');
    });

    it('should delegate to TranslationService.translate', () => {
      translationServiceSpy.translate.mockReturnValue('Tarife');

      pipe.transform('tarif.list.title');

      expect(translationServiceSpy.translate).toHaveBeenCalledExactlyOnceWith('tarif.list.title');
    });

    it('should return updated translation after language change', () => {
      translationServiceSpy.translate.mockReturnValue('Units');

      const resultEn = pipe.transform('einheit.title');
      expect(resultEn).toBe('Units');

      translationServiceSpy.translate.mockReturnValue('Einheiten');

      const resultDe = pipe.transform('einheit.title');
      expect(resultDe).toBe('Einheiten');
    });
  });
});
