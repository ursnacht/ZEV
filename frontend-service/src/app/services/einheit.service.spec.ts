import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { EinheitService, EinheitMatchResponse } from './einheit.service';
import { Einheit, EinheitTyp } from '../models/einheit.model';

describe('EinheitService', () => {
  let service: EinheitService;
  let httpMock: HttpTestingController;
  const apiUrl = 'http://localhost:8090/api/einheit';

  const mockEinheit: Einheit = {
    id: 1,
    name: 'Allgemein',
    typ: EinheitTyp.CONSUMER,
    mietername: 'Test Mieter',
    messpunkt: 'MP-001'
  };

  const mockEinheiten: Einheit[] = [
    mockEinheit,
    {
      id: 2,
      name: '1. Stock links',
      typ: EinheitTyp.CONSUMER
    },
    {
      id: 3,
      name: 'PV-Anlage',
      typ: EinheitTyp.PRODUCER
    }
  ];

  const mockMatchResponse: EinheitMatchResponse = {
    einheitId: 1,
    einheitName: 'Allgemein',
    confidence: 0.9,
    matched: true,
    message: null
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [EinheitService]
    });
    service = TestBed.inject(EinheitService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getAllEinheiten', () => {
    it('should return all einheiten', () => {
      service.getAllEinheiten().subscribe(einheiten => {
        expect(einheiten.length).toBe(3);
        expect(einheiten).toEqual(mockEinheiten);
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('GET');
      req.flush(mockEinheiten);
    });

    it('should return empty array when no einheiten exist', () => {
      service.getAllEinheiten().subscribe(einheiten => {
        expect(einheiten.length).toBe(0);
        expect(einheiten).toEqual([]);
      });

      const req = httpMock.expectOne(apiUrl);
      req.flush([]);
    });
  });

  describe('getEinheitById', () => {
    it('should return a single einheit by id', () => {
      const einheitId = 1;

      service.getEinheitById(einheitId).subscribe(einheit => {
        expect(einheit).toEqual(mockEinheit);
        expect(einheit.id).toBe(einheitId);
      });

      const req = httpMock.expectOne(`${apiUrl}/${einheitId}`);
      expect(req.request.method).toBe('GET');
      req.flush(mockEinheit);
    });
  });

  describe('createEinheit', () => {
    it('should create a new einheit', () => {
      const newEinheit: Einheit = {
        name: 'Neue Einheit',
        typ: EinheitTyp.CONSUMER
      };

      const createdEinheit = { ...newEinheit, id: 4 };

      service.createEinheit(newEinheit).subscribe(einheit => {
        expect(einheit).toEqual(createdEinheit);
        expect(einheit.id).toBe(4);
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(newEinheit);
      req.flush(createdEinheit);
    });
  });

  describe('updateEinheit', () => {
    it('should update an existing einheit', () => {
      const updatedEinheit: Einheit = {
        ...mockEinheit,
        name: 'Allgemein Updated'
      };

      service.updateEinheit(mockEinheit.id!, updatedEinheit).subscribe(einheit => {
        expect(einheit).toEqual(updatedEinheit);
        expect(einheit.name).toBe('Allgemein Updated');
      });

      const req = httpMock.expectOne(`${apiUrl}/${mockEinheit.id}`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updatedEinheit);
      req.flush(updatedEinheit);
    });
  });

  describe('deleteEinheit', () => {
    it('should delete an einheit', () => {
      const einheitId = 1;

      service.deleteEinheit(einheitId).subscribe(response => {
        expect(response).toBeNull();
      });

      const req = httpMock.expectOne(`${apiUrl}/${einheitId}`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('matchEinheitByFilename', () => {
    it('should return matched einheit for valid filename', () => {
      const filename = '2025-07-allg.csv';

      service.matchEinheitByFilename(filename).subscribe(response => {
        expect(response).toEqual(mockMatchResponse);
        expect(response.matched).toBeTrue();
        expect(response.einheitId).toBe(1);
        expect(response.einheitName).toBe('Allgemein');
        expect(response.confidence).toBe(0.9);
      });

      const req = httpMock.expectOne(`${apiUrl}/match`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ filename });
      req.flush(mockMatchResponse);
    });

    it('should return not matched for unknown filename', () => {
      const filename = '2025-07-unknown.csv';
      const notMatchedResponse: EinheitMatchResponse = {
        einheitId: null,
        einheitName: null,
        confidence: 0,
        matched: false,
        message: 'Keine passende Einheit gefunden'
      };

      service.matchEinheitByFilename(filename).subscribe(response => {
        expect(response).toEqual(notMatchedResponse);
        expect(response.matched).toBeFalse();
        expect(response.einheitId).toBeNull();
        expect(response.message).toBe('Keine passende Einheit gefunden');
      });

      const req = httpMock.expectOne(`${apiUrl}/match`);
      expect(req.request.method).toBe('POST');
      req.flush(notMatchedResponse);
    });

    it('should return error message when service unavailable', () => {
      const filename = '2025-07-test.csv';
      const errorResponse: EinheitMatchResponse = {
        einheitId: null,
        einheitName: null,
        confidence: 0,
        matched: false,
        message: 'KI-Service nicht verfügbar'
      };

      service.matchEinheitByFilename(filename).subscribe(response => {
        expect(response).toEqual(errorResponse);
        expect(response.matched).toBeFalse();
        expect(response.message).toBe('KI-Service nicht verfügbar');
      });

      const req = httpMock.expectOne(`${apiUrl}/match`);
      req.flush(errorResponse);
    });

    it('should send correct request body', () => {
      const filename = '2025-08-pv-anlage.csv';

      service.matchEinheitByFilename(filename).subscribe();

      const req = httpMock.expectOne(`${apiUrl}/match`);
      expect(req.request.body).toEqual({ filename: '2025-08-pv-anlage.csv' });
      req.flush(mockMatchResponse);
    });
  });
});
