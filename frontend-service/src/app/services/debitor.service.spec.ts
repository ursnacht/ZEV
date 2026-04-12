import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { DebitorService } from './debitor.service';
import { Debitor } from '../models/debitor.model';

describe('DebitorService', () => {
  let service: DebitorService;
  let httpMock: HttpTestingController;
  const apiUrl = 'http://localhost:8090/api/debitoren';

  const mockDebitor: Debitor = {
    id: 1,
    mieterId: 10,
    mieterName: 'Max Muster',
    einheitName: 'EG links',
    betrag: 123.45,
    datumVon: '2025-01-01',
    datumBis: '2025-03-31',
    zahldatum: undefined
  };

  const mockDebitoren: Debitor[] = [
    mockDebitor,
    {
      id: 2,
      mieterId: 11,
      mieterName: 'Anna Test',
      einheitName: 'OG rechts',
      betrag: 87.60,
      datumVon: '2025-01-01',
      datumBis: '2025-03-31',
      zahldatum: '2025-02-15'
    }
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [DebitorService]
    });
    service = TestBed.inject(DebitorService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getDebitoren', () => {
    it('should return debitoren filtered by date range', () => {
      const von = '2025-01-01';
      const bis = '2025-03-31';

      service.getDebitoren(von, bis).subscribe(debitoren => {
        expect(debitoren.length).toBe(2);
        expect(debitoren).toEqual(mockDebitoren);
      });

      const req = httpMock.expectOne(r =>
        r.url === apiUrl &&
        r.params.get('von') === von &&
        r.params.get('bis') === bis
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockDebitoren);
    });

    it('should return empty array when no debitoren exist for period', () => {
      service.getDebitoren('2020-01-01', '2020-03-31').subscribe(debitoren => {
        expect(debitoren.length).toBe(0);
        expect(debitoren).toEqual([]);
      });

      const req = httpMock.expectOne(r => r.url === apiUrl);
      req.flush([]);
    });

    it('should pass von and bis as query params', () => {
      const von = '2025-04-01';
      const bis = '2025-06-30';

      service.getDebitoren(von, bis).subscribe();

      const req = httpMock.expectOne(r => r.url === apiUrl);
      expect(req.request.params.get('von')).toBe(von);
      expect(req.request.params.get('bis')).toBe(bis);
      req.flush([]);
    });
  });

  describe('createDebitor', () => {
    it('should create a new debitor', () => {
      const newDebitor: Debitor = {
        mieterId: 10,
        betrag: 150.00,
        datumVon: '2025-04-01',
        datumBis: '2025-06-30'
      };
      const createdDebitor = { ...newDebitor, id: 3 };

      service.createDebitor(newDebitor).subscribe(debitor => {
        expect(debitor).toEqual(createdDebitor);
        expect(debitor.id).toBe(3);
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(newDebitor);
      req.flush(createdDebitor);
    });
  });

  describe('updateDebitor', () => {
    it('should update an existing debitor', () => {
      const updatedDebitor: Debitor = { ...mockDebitor, zahldatum: '2025-04-10' };

      service.updateDebitor(1, updatedDebitor).subscribe(debitor => {
        expect(debitor).toEqual(updatedDebitor);
        expect(debitor.zahldatum).toBe('2025-04-10');
      });

      const req = httpMock.expectOne(`${apiUrl}/1`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updatedDebitor);
      req.flush(updatedDebitor);
    });
  });

  describe('deleteDebitor', () => {
    it('should delete a debitor by id', () => {
      service.deleteDebitor(1).subscribe(response => {
        expect(response).toBeNull();
      });

      const req = httpMock.expectOne(`${apiUrl}/1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });
});
