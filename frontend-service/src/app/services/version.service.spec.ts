import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { VersionInfo, VersionService } from './version.service';

describe('VersionService', () => {
  let service: VersionService;
  let httpMock: HttpTestingController;

  const mockVersion: VersionInfo = {
    schemaVersion: '188',
    buildTime: '26.10.2026 10:09',
    springBootVersion: '4.1.2'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [VersionService]
    });
    service = TestBed.inject(VersionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getVersion', () => {
    it('should return schema version, build time and Spring Boot version', () => {
      service.getVersion().subscribe(result => {
        expect(result).toEqual(mockVersion);
      });

      const req = httpMock.expectOne(r => r.url.endsWith('/api/version'));
      expect(req.request.method).toBe('GET');
      req.flush(mockVersion);
    });
  });
});
