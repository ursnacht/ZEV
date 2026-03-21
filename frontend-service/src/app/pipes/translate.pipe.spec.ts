import { TestBed } from '@angular/core/testing';
import { TranslatePipe } from './translate.pipe';
import { TranslationService } from '../services/translation.service';

describe('TranslatePipe', () => {
  let pipe: TranslatePipe;
  let translationServiceSpy: jasmine.SpyObj<TranslationService>;

  beforeEach(() => {
    translationServiceSpy = jasmine.createSpyObj('TranslationService', ['translate']);

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
      translationServiceSpy.translate.and.returnValue('Einheit');

      const result = pipe.transform('einheit.title');

      expect(result).toBe('Einheit');
      expect(translationServiceSpy.translate).toHaveBeenCalledWith('einheit.title');
    });

    it('should return the key when translation is missing', () => {
      translationServiceSpy.translate.and.returnValue('some.missing.key');

      const result = pipe.transform('some.missing.key');

      expect(result).toBe('some.missing.key');
    });

    it('should return empty string when translation is empty', () => {
      translationServiceSpy.translate.and.returnValue('');

      const result = pipe.transform('empty.key');

      expect(result).toBe('');
    });

    it('should delegate to TranslationService.translate', () => {
      translationServiceSpy.translate.and.returnValue('Tarife');

      pipe.transform('tarif.list.title');

      expect(translationServiceSpy.translate).toHaveBeenCalledOnceWith('tarif.list.title');
    });

    it('should return updated translation after language change', () => {
      translationServiceSpy.translate.and.returnValue('Units');

      const resultEn = pipe.transform('einheit.title');
      expect(resultEn).toBe('Units');

      translationServiceSpy.translate.and.returnValue('Einheiten');

      const resultDe = pipe.transform('einheit.title');
      expect(resultDe).toBe('Einheiten');
    });
  });
});
