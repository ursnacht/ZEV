import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { FeatureFlagService } from './feature-flag.service';
import { FeatureFlagAdmin, FeatureFlagMap, FeatureFlagQuelle } from '../models/feature-flag.model';

describe('FeatureFlagService', () => {
  let service: FeatureFlagService;
  let httpMock: HttpTestingController;
  const apiUrl = 'http://localhost:8090/api/feature-flags';

  const mockFlags: FeatureFlagMap = {
    MESSWERTE_UPLOAD: true,
    OTHER_FLAG: false
  };

  const mockAdminFlags: FeatureFlagAdmin[] = [
    {
      key: 'MESSWERTE_UPLOAD',
      beschreibungKey: 'FEATURE_FLAG_MESSWERTE_UPLOAD',
      defaultWert: true,
      effektiv: true,
      quelle: FeatureFlagQuelle.DEFAULT
    }
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [FeatureFlagService]
    });
    service = TestBed.inject(FeatureFlagService);
    httpMock = TestBed.inject(HttpTestingController);

    // Der Konstruktor löst sofort einen initialen GET aus (load()); diesen hier abarbeiten,
    // damit httpMock.verify() nicht über eine offene Anfrage stolpert.
    httpMock.expectOne(apiUrl).flush({});
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should trigger an initial load in the constructor', () => {
    // Der initiale Request wurde bereits im beforeEach mit {} beantwortet.
    expect(service.isEnabled('MESSWERTE_UPLOAD')).toBe(false);
  });

  describe('load', () => {
    it('should GET the effective flags and expose them via isEnabled', () => {
      service.load().subscribe(flags => {
        expect(flags).toEqual(mockFlags);
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('GET');
      req.flush(mockFlags);

      expect(service.isEnabled('MESSWERTE_UPLOAD')).toBe(true);
      expect(service.isEnabled('OTHER_FLAG')).toBe(false);
    });

    it('should fall back to an empty map on error (no failing observable)', () => {
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

      let result: FeatureFlagMap | undefined;
      service.load().subscribe(flags => (result = flags));

      const req = httpMock.expectOne(apiUrl);
      req.flush('boom', { status: 500, statusText: 'Server Error' });

      expect(result).toEqual({});
      expect(service.isEnabled('MESSWERTE_UPLOAD')).toBe(false);
      expect(consoleSpy).toHaveBeenCalled();
    });

    it('should treat a null response body as an empty map', () => {
      service.load().subscribe();

      const req = httpMock.expectOne(apiUrl);
      req.flush(null);

      expect(service.isEnabled('MESSWERTE_UPLOAD')).toBe(false);
    });
  });

  describe('isEnabled', () => {
    it('should return false for unknown/not-yet-loaded keys', () => {
      expect(service.isEnabled('DOES_NOT_EXIST')).toBe(false);
    });

    it('should return false for a flag explicitly set to false', () => {
      service.load().subscribe();
      httpMock.expectOne(apiUrl).flush({ MESSWERTE_UPLOAD: false });

      expect(service.isEnabled('MESSWERTE_UPLOAD')).toBe(false);
    });
  });

  describe('getAdminFlags', () => {
    it('should GET the admin view of all flags', () => {
      service.getAdminFlags().subscribe(flags => {
        expect(flags).toEqual(mockAdminFlags);
      });

      const req = httpMock.expectOne(`${apiUrl}/admin`);
      expect(req.request.method).toBe('GET');
      req.flush(mockAdminFlags);
    });
  });

  describe('setFlag', () => {
    it('should PUT the override and reload the effective flags', () => {
      service.setFlag('MESSWERTE_UPLOAD', false).subscribe();

      const putReq = httpMock.expectOne(`${apiUrl}/MESSWERTE_UPLOAD`);
      expect(putReq.request.method).toBe('PUT');
      expect(putReq.request.body).toEqual({ enabled: false });
      putReq.flush(null);

      // Reload nach erfolgreichem PUT
      const reloadReq = httpMock.expectOne(apiUrl);
      expect(reloadReq.request.method).toBe('GET');
      reloadReq.flush({ MESSWERTE_UPLOAD: false });

      expect(service.isEnabled('MESSWERTE_UPLOAD')).toBe(false);
    });
  });

  describe('resetFlag', () => {
    it('should DELETE the override and reload the effective flags', () => {
      service.resetFlag('MESSWERTE_UPLOAD').subscribe();

      const deleteReq = httpMock.expectOne(`${apiUrl}/MESSWERTE_UPLOAD`);
      expect(deleteReq.request.method).toBe('DELETE');
      deleteReq.flush(null);

      // Reload nach erfolgreichem DELETE
      const reloadReq = httpMock.expectOne(apiUrl);
      expect(reloadReq.request.method).toBe('GET');
      reloadReq.flush({ MESSWERTE_UPLOAD: true });

      expect(service.isEnabled('MESSWERTE_UPLOAD')).toBe(true);
    });
  });
});
