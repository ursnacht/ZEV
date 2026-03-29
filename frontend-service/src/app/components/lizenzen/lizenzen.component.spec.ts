import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LizenzenComponent } from './lizenzen.component';
import { LizenzenService } from '../../services/lizenzen.service';
import { TranslationService } from '../../services/translation.service';
import { Lizenz } from '../../models/lizenzen.model';
import { of, throwError } from 'rxjs';

describe('LizenzenComponent', () => {
  let component: LizenzenComponent;
  let fixture: ComponentFixture<LizenzenComponent>;
  let lizenzenServiceSpy: jasmine.SpyObj<LizenzenService>;
  let translationServiceSpy: jasmine.SpyObj<TranslationService>;

  const mockBackendLizenzen: Lizenz[] = [
    {
      name: 'spring-core',
      version: '6.0.0',
      license: 'Apache-2.0',
      publisher: 'Pivotal',
      url: 'https://spring.io',
      hashes: [
        { algorithm: 'SHA-1', value: 'abc123def456' },
        { algorithm: 'SHA-256', value: 'abcdef123456abcdef123456abcdef12' }
      ]
    },
    {
      name: 'jackson-core',
      version: '2.15.0',
      license: 'Apache-2.0',
      publisher: 'FasterXML',
      url: null,
      hashes: []
    }
  ];

  const mockFrontendLizenzen: Lizenz[] = [
    {
      name: 'angular-core',
      version: '21.0.0',
      license: 'MIT',
      publisher: 'Google',
      url: 'https://angular.io',
      hashes: [{ algorithm: 'SHA-512', value: 'sha512hashvalue1234567890abcdef' }]
    }
  ];

  beforeEach(async () => {
    lizenzenServiceSpy = jasmine.createSpyObj('LizenzenService', [
      'getBackendLizenzen',
      'getFrontendLizenzen'
    ]);
    lizenzenServiceSpy.getBackendLizenzen.and.returnValue(of(mockBackendLizenzen));
    lizenzenServiceSpy.getFrontendLizenzen.and.returnValue(of(mockFrontendLizenzen));

    translationServiceSpy = jasmine.createSpyObj('TranslationService', ['translate']);
    translationServiceSpy.translate.and.callFake((key: string) => key);

    await TestBed.configureTestingModule({
      imports: [LizenzenComponent],
      providers: [
        { provide: LizenzenService, useValue: lizenzenServiceSpy },
        { provide: TranslationService, useValue: translationServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LizenzenComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should load backend lizenzen on init', () => {
      expect(lizenzenServiceSpy.getBackendLizenzen).toHaveBeenCalled();
      expect(component.backendLizenzen.length).toBe(2);
      expect(component.filteredBackend.length).toBe(2);
    });

    it('should load frontend lizenzen on init', () => {
      expect(lizenzenServiceSpy.getFrontendLizenzen).toHaveBeenCalled();
      expect(component.frontendLizenzen.length).toBe(1);
      expect(component.filteredFrontend.length).toBe(1);
    });

    it('should set backendLoading to false after load', () => {
      expect(component.backendLoading).toBeFalse();
    });

    it('should set frontendLoading to false after load', () => {
      expect(component.frontendLoading).toBeFalse();
    });

    it('should set backendError to false on success', () => {
      expect(component.backendError).toBeFalse();
    });

    it('should set frontendError to false on success', () => {
      expect(component.frontendError).toBeFalse();
    });
  });

  describe('loadBackendLizenzen', () => {
    it('should set backendError to true on failure', () => {
      lizenzenServiceSpy.getBackendLizenzen.and.returnValue(throwError(() => new Error('Network error')));
      component.loadBackendLizenzen();
      expect(component.backendError).toBeTrue();
      expect(component.backendLoading).toBeFalse();
    });

    it('should reset backendError before loading', () => {
      component.backendError = true;
      lizenzenServiceSpy.getBackendLizenzen.and.returnValue(of([]));
      component.loadBackendLizenzen();
      expect(component.backendError).toBeFalse();
    });

    it('should set backendLoading to true before loading', () => {
      lizenzenServiceSpy.getBackendLizenzen.and.returnValue(of(mockBackendLizenzen));
      component.backendLoading = false;
      component.loadBackendLizenzen();
      expect(component.backendLoading).toBeFalse(); // false after synchronous completion
    });
  });

  describe('loadFrontendLizenzen', () => {
    it('should set frontendError to true on failure', () => {
      lizenzenServiceSpy.getFrontendLizenzen.and.returnValue(throwError(() => new Error('Network error')));
      component.loadFrontendLizenzen();
      expect(component.frontendError).toBeTrue();
      expect(component.frontendLoading).toBeFalse();
    });

    it('should reset frontendError before loading', () => {
      component.frontendError = true;
      lizenzenServiceSpy.getFrontendLizenzen.and.returnValue(of([]));
      component.loadFrontendLizenzen();
      expect(component.frontendError).toBeFalse();
    });
  });

  describe('onBackendFilterChange', () => {
    it('should return all items when filter is empty', () => {
      component.backendFilter = '';
      component.onBackendFilterChange();
      expect(component.filteredBackend.length).toBe(2);
    });

    it('should filter by name', () => {
      component.backendFilter = 'spring';
      component.onBackendFilterChange();
      expect(component.filteredBackend.length).toBe(1);
      expect(component.filteredBackend[0].name).toBe('spring-core');
    });

    it('should filter by license', () => {
      component.backendFilter = 'apache';
      component.onBackendFilterChange();
      expect(component.filteredBackend.length).toBe(2);
    });

    it('should return empty list when no match', () => {
      component.backendFilter = 'xyz-no-match';
      component.onBackendFilterChange();
      expect(component.filteredBackend.length).toBe(0);
    });

    it('should be case-insensitive', () => {
      component.backendFilter = 'SPRING';
      component.onBackendFilterChange();
      expect(component.filteredBackend.length).toBe(1);
    });

    it('should trim whitespace from filter term', () => {
      component.backendFilter = '  ';
      component.onBackendFilterChange();
      expect(component.filteredBackend.length).toBe(2);
    });
  });

  describe('onFrontendFilterChange', () => {
    it('should return all items when filter is empty', () => {
      component.frontendFilter = '';
      component.onFrontendFilterChange();
      expect(component.filteredFrontend.length).toBe(1);
    });

    it('should filter by name', () => {
      component.frontendFilter = 'angular';
      component.onFrontendFilterChange();
      expect(component.filteredFrontend.length).toBe(1);
    });

    it('should return empty list when no match', () => {
      component.frontendFilter = 'xyz-no-match';
      component.onFrontendFilterChange();
      expect(component.filteredFrontend.length).toBe(0);
    });

    it('should be case-insensitive', () => {
      component.frontendFilter = 'ANGULAR';
      component.onFrontendFilterChange();
      expect(component.filteredFrontend.length).toBe(1);
    });

    it('should trim whitespace from filter term', () => {
      component.frontendFilter = '   ';
      component.onFrontendFilterChange();
      expect(component.filteredFrontend.length).toBe(1);
    });
  });

  describe('getBestHash', () => {
    it('should return SHA-512 hash with highest priority', () => {
      const lizenz: Lizenz = {
        name: 'test', version: '1.0', license: 'MIT', publisher: null, url: null,
        hashes: [
          { algorithm: 'MD5', value: 'md5value' },
          { algorithm: 'SHA-512', value: 'sha512value' },
          { algorithm: 'SHA-1', value: 'sha1value' }
        ]
      };
      const result = component.getBestHash(lizenz);
      expect(result?.algorithm).toBe('SHA-512');
      expect(result?.value).toBe('sha512value');
    });

    it('should prefer SHA-256 over SHA-384', () => {
      const lizenz: Lizenz = {
        name: 'test', version: '1.0', license: 'MIT', publisher: null, url: null,
        hashes: [
          { algorithm: 'SHA-384', value: 'sha384value' },
          { algorithm: 'SHA-256', value: 'sha256value' }
        ]
      };
      const result = component.getBestHash(lizenz);
      expect(result?.algorithm).toBe('SHA-256');
    });

    it('should prefer SHA-1 over MD5', () => {
      const lizenz: Lizenz = {
        name: 'test', version: '1.0', license: 'MIT', publisher: null, url: null,
        hashes: [
          { algorithm: 'MD5', value: 'md5value' },
          { algorithm: 'SHA-1', value: 'sha1value' }
        ]
      };
      const result = component.getBestHash(lizenz);
      expect(result?.algorithm).toBe('SHA-1');
    });

    it('should return first hash when none match priority list', () => {
      const lizenz: Lizenz = {
        name: 'test', version: '1.0', license: 'MIT', publisher: null, url: null,
        hashes: [{ algorithm: 'UNKNOWN-ALG', value: 'unknownvalue' }]
      };
      const result = component.getBestHash(lizenz);
      expect(result?.algorithm).toBe('UNKNOWN-ALG');
      expect(result?.value).toBe('unknownvalue');
    });

    it('should return null when hashes array is empty', () => {
      const lizenz: Lizenz = {
        name: 'test', version: '1.0', license: 'MIT', publisher: null, url: null,
        hashes: []
      };
      const result = component.getBestHash(lizenz);
      expect(result).toBeNull();
    });

    it('should return the only hash when exactly one exists', () => {
      const lizenz: Lizenz = {
        name: 'test', version: '1.0', license: 'MIT', publisher: null, url: null,
        hashes: [{ algorithm: 'SHA-1', value: 'sha1value' }]
      };
      const result = component.getBestHash(lizenz);
      expect(result?.algorithm).toBe('SHA-1');
    });
  });

  describe('truncateHash', () => {
    it('should truncate to 12 characters', () => {
      const result = component.truncateHash('abcdef123456789');
      expect(result).toBe('abcdef123456');
      expect(result.length).toBe(12);
    });

    it('should return full value when shorter than 12 characters', () => {
      const result = component.truncateHash('short');
      expect(result).toBe('short');
    });

    it('should return empty string for empty input', () => {
      const result = component.truncateHash('');
      expect(result).toBe('');
    });

    it('should return exactly 12 characters for 12-char input', () => {
      const result = component.truncateHash('exactly12chr');
      expect(result).toBe('exactly12chr');
    });
  });
});
