import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { LizenzenService } from './lizenzen.service';
import { Lizenz } from '../models/lizenzen.model';

describe('LizenzenService', () => {
  let service: LizenzenService;
  let httpMock: HttpTestingController;
  const apiUrl = 'http://localhost:8090/api/lizenzen';
  const assetsUrl = 'assets/frontend-licenses.json';

  const mockLizenz: Lizenz = {
    name: 'spring-core',
    version: '6.0.0',
    license: 'Apache-2.0',
    publisher: 'Pivotal',
    url: 'https://spring.io',
    hashes: [{ algorithm: 'SHA-1', value: 'abc123' }]
  };

  const mockLizenzen: Lizenz[] = [
    mockLizenz,
    {
      name: 'jackson-core',
      version: '2.15.0',
      license: 'Apache-2.0',
      publisher: 'FasterXML',
      url: null,
      hashes: []
    }
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [LizenzenService]
    });
    service = TestBed.inject(LizenzenService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getBackendLizenzen', () => {
    it('should return backend lizenzen via GET', () => {
      service.getBackendLizenzen().subscribe(result => {
        expect(result).toEqual(mockLizenzen);
        expect(result.length).toBe(2);
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('GET');
      req.flush(mockLizenzen);
    });

    it('should return empty array when backend returns empty list', () => {
      service.getBackendLizenzen().subscribe(result => {
        expect(result).toEqual([]);
      });

      const req = httpMock.expectOne(apiUrl);
      req.flush([]);
    });

    it('should use correct API URL', () => {
      service.getBackendLizenzen().subscribe();

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.url).toBe(apiUrl);
      req.flush([]);
    });
  });

  describe('getFrontendLizenzen', () => {
    it('should return frontend lizenzen from assets via GET', () => {
      service.getFrontendLizenzen().subscribe(result => {
        expect(result).toEqual(mockLizenzen);
        expect(result.length).toBe(2);
      });

      const req = httpMock.expectOne(assetsUrl);
      expect(req.request.method).toBe('GET');
      req.flush(mockLizenzen);
    });

    it('should return empty array when assets file returns empty list', () => {
      service.getFrontendLizenzen().subscribe(result => {
        expect(result).toEqual([]);
      });

      const req = httpMock.expectOne(assetsUrl);
      req.flush([]);
    });

    it('should use correct assets URL', () => {
      service.getFrontendLizenzen().subscribe();

      const req = httpMock.expectOne(assetsUrl);
      expect(req.request.url).toBe(assetsUrl);
      req.flush([]);
    });
  });
});
