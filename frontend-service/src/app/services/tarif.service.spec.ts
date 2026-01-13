import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TarifService } from './tarif.service';
import { Tarif, TarifTyp, ValidationResult } from '../models/tarif.model';

describe('TarifService', () => {
  let service: TarifService;
  let httpMock: HttpTestingController;
  const apiUrl = 'http://localhost:8090/api/tarife';

  const mockTarif: Tarif = {
    id: 1,
    bezeichnung: 'Test Tarif',
    tariftyp: TarifTyp.ZEV,
    preis: 0.195,
    gueltigVon: '2024-01-01',
    gueltigBis: '2024-12-31'
  };

  const mockTarife: Tarif[] = [
    mockTarif,
    {
      id: 2,
      bezeichnung: 'VNB Tarif',
      tariftyp: TarifTyp.VNB,
      preis: 0.342,
      gueltigVon: '2024-01-01',
      gueltigBis: '2024-12-31'
    }
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [TarifService]
    });
    service = TestBed.inject(TarifService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getAllTarife', () => {
    it('should return all tarife', () => {
      service.getAllTarife().subscribe(tarife => {
        expect(tarife.length).toBe(2);
        expect(tarife).toEqual(mockTarife);
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('GET');
      req.flush(mockTarife);
    });

    it('should return empty array when no tarife exist', () => {
      service.getAllTarife().subscribe(tarife => {
        expect(tarife.length).toBe(0);
        expect(tarife).toEqual([]);
      });

      const req = httpMock.expectOne(apiUrl);
      req.flush([]);
    });
  });

  describe('getTarifById', () => {
    it('should return a single tarif by id', () => {
      const tarifId = 1;

      service.getTarifById(tarifId).subscribe(tarif => {
        expect(tarif).toEqual(mockTarif);
        expect(tarif.id).toBe(tarifId);
      });

      const req = httpMock.expectOne(`${apiUrl}/${tarifId}`);
      expect(req.request.method).toBe('GET');
      req.flush(mockTarif);
    });
  });

  describe('createTarif', () => {
    it('should create a new tarif', () => {
      const newTarif: Tarif = {
        bezeichnung: 'Neuer Tarif',
        tariftyp: TarifTyp.ZEV,
        preis: 0.20,
        gueltigVon: '2025-01-01',
        gueltigBis: '2025-12-31'
      };

      const createdTarif = { ...newTarif, id: 3 };

      service.createTarif(newTarif).subscribe(tarif => {
        expect(tarif).toEqual(createdTarif);
        expect(tarif.id).toBe(3);
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(newTarif);
      req.flush(createdTarif);
    });
  });

  describe('updateTarif', () => {
    it('should update an existing tarif', () => {
      const updatedTarif: Tarif = {
        ...mockTarif,
        preis: 0.21
      };

      service.updateTarif(mockTarif.id!, updatedTarif).subscribe(tarif => {
        expect(tarif).toEqual(updatedTarif);
        expect(tarif.preis).toBe(0.21);
      });

      const req = httpMock.expectOne(`${apiUrl}/${mockTarif.id}`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updatedTarif);
      req.flush(updatedTarif);
    });
  });

  describe('deleteTarif', () => {
    it('should delete a tarif', () => {
      const tarifId = 1;

      service.deleteTarif(tarifId).subscribe(response => {
        expect(response).toBeNull();
      });

      const req = httpMock.expectOne(`${apiUrl}/${tarifId}`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('validateQuartale', () => {
    const validResult: ValidationResult = {
      valid: true,
      message: 'Alle Quartale sind vollständig abgedeckt',
      errors: []
    };

    const invalidResult: ValidationResult = {
      valid: false,
      message: 'Validierungsfehler',
      errors: ['Q1/2024: VNB-Tarif fehlt für: 01.01.2024']
    };

    it('should call POST with modus=quartale', () => {
      service.validateQuartale().subscribe(result => {
        expect(result).toEqual(validResult);
      });

      const req = httpMock.expectOne(`${apiUrl}/validate?modus=quartale`);
      expect(req.request.method).toBe('POST');
      req.flush(validResult);
    });

    it('should return validation result with errors', () => {
      service.validateQuartale().subscribe(result => {
        expect(result.valid).toBeFalse();
        expect(result.errors.length).toBe(1);
        expect(result.errors[0]).toContain('Q1/2024');
      });

      const req = httpMock.expectOne(`${apiUrl}/validate?modus=quartale`);
      req.flush(invalidResult);
    });
  });

  describe('validateJahre', () => {
    const validResult: ValidationResult = {
      valid: true,
      message: 'Alle Jahre sind vollständig abgedeckt',
      errors: []
    };

    const invalidResult: ValidationResult = {
      valid: false,
      message: 'Validierungsfehler',
      errors: ['2024: VNB-Tarif fehlt für: 01.01.2024', '2025: ZEV-Tarif fehlt für: 01.01.2025']
    };

    it('should call POST with modus=jahre', () => {
      service.validateJahre().subscribe(result => {
        expect(result).toEqual(validResult);
      });

      const req = httpMock.expectOne(`${apiUrl}/validate?modus=jahre`);
      expect(req.request.method).toBe('POST');
      req.flush(validResult);
    });

    it('should return validation result with multiple errors', () => {
      service.validateJahre().subscribe(result => {
        expect(result.valid).toBeFalse();
        expect(result.errors.length).toBe(2);
        expect(result.errors[0]).toContain('2024');
        expect(result.errors[1]).toContain('2025');
      });

      const req = httpMock.expectOne(`${apiUrl}/validate?modus=jahre`);
      req.flush(invalidResult);
    });
  });
});
