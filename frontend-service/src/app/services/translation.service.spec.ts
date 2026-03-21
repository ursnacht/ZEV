import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TranslationService, Translation } from './translation.service';

describe('TranslationService', () => {
  let service: TranslationService;
  let httpMock: HttpTestingController;
  const apiUrl = 'http://localhost:8090/api/translations';

  const mockTranslations: Record<string, Record<string, string>> = {
    de: {
      'hello': 'Hallo',
      'goodbye': 'Auf Wiedersehen'
    },
    en: {
      'hello': 'Hello',
      'goodbye': 'Goodbye'
    }
  };

  const mockTranslationList: Translation[] = [
    { key: 'hello', deutsch: 'Hallo', englisch: 'Hello' },
    { key: 'goodbye', deutsch: 'Auf Wiedersehen', englisch: 'Goodbye' }
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [TranslationService]
    });
    service = TestBed.inject(TranslationService);
    httpMock = TestBed.inject(HttpTestingController);
    // Intercept the constructor's loadTranslations() call
    const initReq = httpMock.expectOne(apiUrl);
    initReq.flush(mockTranslations);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('translate', () => {
    it('should return translation for known key in current language', () => {
      // Default language is 'de'
      expect(service.translate('hello')).toBe('Hallo');
    });

    it('should return key when translation not found', () => {
      expect(service.translate('unknown.key')).toBe('unknown.key');
    });

    it('should return translation in English after language switch', () => {
      service.setLanguage('en');
      expect(service.translate('hello')).toBe('Hello');
    });
  });

  describe('setLanguage', () => {
    it('should set currentLang signal to "en"', () => {
      service.setLanguage('en');
      expect(service.currentLang()).toBe('en');
    });

    it('should set currentLang signal back to "de"', () => {
      service.setLanguage('en');
      service.setLanguage('de');
      expect(service.currentLang()).toBe('de');
    });
  });

  describe('getCurrentLanguage', () => {
    it('should return "de" as default language', () => {
      expect(service.getCurrentLanguage()).toBe('de');
    });

    it('should return the language after switching', () => {
      service.setLanguage('en');
      expect(service.getCurrentLanguage()).toBe('en');
    });
  });

  describe('loadTranslations', () => {
    it('should make GET request to /api/translations', () => {
      service.loadTranslations();

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('GET');
      req.flush(mockTranslations);
    });

    it('should set translationsLoaded to true on success', (done) => {
      service.loadTranslations();

      const req = httpMock.expectOne(apiUrl);
      req.flush(mockTranslations);

      service.translationsLoaded.subscribe(loaded => {
        if (loaded) {
          expect(loaded).toBeTrue();
          done();
        }
      });
    });
  });

  describe('getAllTranslations', () => {
    it('should return list of all translations', () => {
      service.getAllTranslations().subscribe(translations => {
        expect(translations.length).toBe(2);
        expect(translations).toEqual(mockTranslationList);
      });

      const req = httpMock.expectOne(`${apiUrl}/list`);
      expect(req.request.method).toBe('GET');
      req.flush(mockTranslationList);
    });

    it('should return empty array when no translations exist', () => {
      service.getAllTranslations().subscribe(translations => {
        expect(translations.length).toBe(0);
        expect(translations).toEqual([]);
      });

      const req = httpMock.expectOne(`${apiUrl}/list`);
      req.flush([]);
    });
  });

  describe('saveTranslation', () => {
    it('should make PUT request when translation key exists', () => {
      const translation: Translation = { key: 'hello', deutsch: 'Hallo aktualisiert', englisch: 'Hello updated' };

      service.saveTranslation(translation).subscribe(result => {
        expect(result).toEqual(translation);
      });

      const req = httpMock.expectOne(`${apiUrl}/${translation.key}`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(translation);
      req.flush(translation);
    });

    it('should make POST request when translation key is empty', () => {
      const newTranslation: Translation = { key: '', deutsch: 'Neu', englisch: 'New' };
      const saved: Translation = { key: 'neu', deutsch: 'Neu', englisch: 'New' };

      service.saveTranslation(newTranslation).subscribe(result => {
        expect(result).toEqual(saved);
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(newTranslation);
      req.flush(saved);
    });
  });

  describe('createTranslation', () => {
    it('should make POST request to create a new translation', () => {
      const newTranslation: Translation = { key: 'new.key', deutsch: 'Neu', englisch: 'New' };
      const created: Translation = { key: 'new.key', deutsch: 'Neu', englisch: 'New' };

      service.createTranslation(newTranslation).subscribe(result => {
        expect(result).toEqual(created);
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(newTranslation);
      req.flush(created);
    });
  });

  describe('deleteTranslation', () => {
    it('should make DELETE request and then reload translations', () => {
      const key = 'hello';

      service.deleteTranslation(key).subscribe();

      const deleteReq = httpMock.expectOne(`${apiUrl}/${key}`);
      expect(deleteReq.request.method).toBe('DELETE');
      deleteReq.flush(null);

      // After delete, loadTranslations() is called via tap()
      const reloadReq = httpMock.expectOne(apiUrl);
      expect(reloadReq.request.method).toBe('GET');
      reloadReq.flush(mockTranslations);
    });
  });
});
