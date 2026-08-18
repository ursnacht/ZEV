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
    message: null,
    bilanz: false
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

  describe('createEinheit - ladestation', () => {
    it('should send the RFID in the messpunkt field', () => {
      // Die RFID steht im bestehenden Feld messpunkt (Specs/Ladestationen.md FR-2).
      const ladestation: Einheit = {
        name: 'Ladestation 1',
        typ: EinheitTyp.LADESTATION,
        messpunkt: 'RFID-04711'
      };
      const created = { ...ladestation, id: 5 };

      service.createEinheit(ladestation).subscribe(einheit => {
        expect(einheit.typ).toBe(EinheitTyp.LADESTATION);
        expect(einheit.messpunkt).toBe('RFID-04711');
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(ladestation);
      req.flush(created);
    });

    it('should propagate the server error when the RFID is already used', () => {
      let errorStatus = 0;

      service.createEinheit({ name: 'Ladestation 2', typ: EinheitTyp.LADESTATION, messpunkt: 'RFID-04711' })
        .subscribe({
          next: () => { throw new Error('should not succeed'); },
          error: (error) => { errorStatus = error.status; }
        });

      const req = httpMock.expectOne(apiUrl);
      req.flush({ error: 'EINHEIT_MESSPUNKT_DUPLIKAT' }, { status: 400, statusText: 'Bad Request' });

      expect(errorStatus).toBe(400);
    });
  });

  describe('deleteEinheit - loeschschutz', () => {
    it('should propagate the server message when tenants are still assigned', () => {
      let body: { error?: string } | undefined;

      service.deleteEinheit(1).subscribe({
        next: () => { throw new Error('should not succeed'); },
        error: (error) => { body = error.error; }
      });

      const req = httpMock.expectOne(`${apiUrl}/1`);
      req.flush(
        { error: 'Einheit kann nicht gelöscht werden: 2 Mieter zugeordnet' },
        { status: 409, statusText: 'Conflict' }
      );

      expect(body?.error).toContain('2 Mieter zugeordnet');
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
        expect(response.matched).toBe(true);
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
        message: 'Keine passende Einheit gefunden',
        bilanz: false
      };

      service.matchEinheitByFilename(filename).subscribe(response => {
        expect(response).toEqual(notMatchedResponse);
        expect(response.matched).toBe(false);
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
        message: 'KI-Service nicht verfügbar',
        bilanz: false
      };

      service.matchEinheitByFilename(filename).subscribe(response => {
        expect(response).toEqual(errorResponse);
        expect(response.matched).toBe(false);
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
