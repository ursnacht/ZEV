import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TarifpositionService } from './tarifposition.service';
import { Erfassungsart, Tarifposition } from '../models/tarifposition.model';

describe('TarifpositionService', () => {
  let service: TarifpositionService;
  let httpMock: HttpTestingController;
  const apiUrl = 'http://localhost:8090/api/tarifpositionen';

  const mockPosition: Tarifposition = {
    id: 1,
    mieterId: 7,
    mieterName: 'Max Muster',
    tarifId: 3,
    tarifBezeichnung: 'Ladestrom 2026',
    tarifPreis: 0.35,
    jahr: 2026,
    quartal: 3,
    menge: 123.456,
    erfassungsart: Erfassungsart.MANUELL,
    quellReferenz: 'LP-01',
    bemerkung: 'Beleg 42'
  };

  const mockPositionen: Tarifposition[] = [
    mockPosition,
    {
      id: 2,
      mieterId: 7,
      tarifId: 3,
      tarifBezeichnung: 'Ladestrom 2026',
      tarifPreis: 0.35,
      jahr: 2026,
      quartal: 4,
      menge: 80,
      erfassungsart: Erfassungsart.IMPORT,
      quellReferenz: 'LP-01'
    }
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [TarifpositionService]
    });
    service = TestBed.inject(TarifpositionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getByMieter', () => {
    it('should return all positions of a mieter', () => {
      service.getByMieter(7).subscribe(positionen => {
        expect(positionen.length).toBe(2);
        expect(positionen).toEqual(mockPositionen);
      });

      const req = httpMock.expectOne(r => r.url === apiUrl);
      expect(req.request.method).toBe('GET');
      req.flush(mockPositionen);
    });

    it('should send the mieterId as query parameter', () => {
      service.getByMieter(7).subscribe();

      const req = httpMock.expectOne(r => r.url === apiUrl);
      expect(req.request.params.get('mieterId')).toBe('7');
      req.flush([]);
    });

    it('should return empty array when the mieter has no positions', () => {
      service.getByMieter(7).subscribe(positionen => {
        expect(positionen).toEqual([]);
      });

      const req = httpMock.expectOne(r => r.url === apiUrl);
      req.flush([]);
    });
  });

  describe('createTarifposition', () => {
    it('should create a new tarifposition', () => {
      const newPosition: Tarifposition = {
        mieterId: 7,
        tarifId: 3,
        jahr: 2026,
        quartal: 1,
        menge: 55.5,
        quellReferenz: 'LP-01'
      };
      const createdPosition = { ...newPosition, id: 9 };

      service.createTarifposition(newPosition).subscribe(position => {
        expect(position).toEqual(createdPosition);
        expect(position.id).toBe(9);
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(newPosition);
      req.flush(createdPosition);
    });

    it('should propagate the server error on duplicate positions', () => {
      let errorStatus = 0;

      service.createTarifposition({ mieterId: 7, tarifId: 3, jahr: 2026, quartal: 3, menge: 1 })
        .subscribe({
          next: () => { throw new Error('should not succeed'); },
          error: (error) => { errorStatus = error.status; }
        });

      const req = httpMock.expectOne(apiUrl);
      req.flush('TARIFPOSITION_DUPLIKAT', { status: 400, statusText: 'Bad Request' });

      expect(errorStatus).toBe(400);
    });
  });

  describe('updateTarifposition', () => {
    it('should update an existing tarifposition', () => {
      const updatedPosition: Tarifposition = { ...mockPosition, menge: 200 };

      service.updateTarifposition(mockPosition.id!, updatedPosition).subscribe(position => {
        expect(position).toEqual(updatedPosition);
        expect(position.menge).toBe(200);
      });

      const req = httpMock.expectOne(`${apiUrl}/${mockPosition.id}`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updatedPosition);
      req.flush(updatedPosition);
    });
  });

  describe('deleteTarifposition', () => {
    it('should delete a tarifposition', () => {
      service.deleteTarifposition(1).subscribe(response => {
        expect(response).toBeNull();
      });

      const req = httpMock.expectOne(`${apiUrl}/1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });
});
