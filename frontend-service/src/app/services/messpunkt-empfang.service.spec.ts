import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { MesspunktEmpfangService } from './messpunkt-empfang.service';
import { MesspunktEmpfang, MesspunktEmpfangMeldung } from '../models/messpunkt-empfang.model';

describe('MesspunktEmpfangService', () => {
  let service: MesspunktEmpfangService;
  let httpMock: HttpTestingController;
  const apiUrl = 'http://localhost:8090/api/messpunkt-empfang';

  const mockMesspunktEmpfang: MesspunktEmpfang = {
    id: 1,
    messpunkt: 'MP-001',
    einheiten: ['Wohnung 1'],
    quelle: 'PI_GATEWAY',
    ersterEmpfang: '2026-10-25T08:00:00',
    letzterEmpfang: '2026-10-25T09:59:30',
    sekundenSeitEmpfang: 30,
    anzahlMeldungen: 120,
    zaehlerstandBezug: 1234.5,
    zaehlerstandEinspeisung: 12,
    ergebnis: 'GESPEICHERT',
    grund: null,
    keinEmpfang: false
  };

  const mockMesspunktEmpfaenge: MesspunktEmpfang[] = [
    mockMesspunktEmpfang,
    {
      id: null,
      messpunkt: 'MP-STUMM',
      einheiten: ['Wohnung 2'],
      quelle: null,
      ersterEmpfang: null,
      letzterEmpfang: null,
      sekundenSeitEmpfang: null,
      anzahlMeldungen: null,
      zaehlerstandBezug: null,
      zaehlerstandEinspeisung: null,
      ergebnis: null,
      grund: null,
      keinEmpfang: true
    }
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [MesspunktEmpfangService]
    });
    service = TestBed.inject(MesspunktEmpfangService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getAllMesspunktEmpfang', () => {
    it('should return all items', () => {
      service.getAllMesspunktEmpfang().subscribe(result => {
        expect(result).toEqual(mockMesspunktEmpfaenge);
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('GET');
      req.flush(mockMesspunktEmpfaenge);
    });

    it('should return an empty list', () => {
      service.getAllMesspunktEmpfang().subscribe(result => {
        expect(result).toEqual([]);
      });

      const req = httpMock.expectOne(apiUrl);
      req.flush([]);
    });
  });

  describe('getLetzteMeldung', () => {
    it('should return the payload of the last message by id', () => {
      const meldung: MesspunktEmpfangMeldung = { payload: '{"zaehlerstandBezug":1234.5}' };

      service.getLetzteMeldung(1).subscribe(result => {
        expect(result).toEqual(meldung);
      });

      const req = httpMock.expectOne(`${apiUrl}/1/letzte-meldung`);
      expect(req.request.method).toBe('GET');
      req.flush(meldung);
    });

    it('should return null as payload when none is stored', () => {
      service.getLetzteMeldung(2).subscribe(result => {
        expect(result.payload).toBeNull();
      });

      const req = httpMock.expectOne(`${apiUrl}/2/letzte-meldung`);
      req.flush({ payload: null });
    });
  });

  describe('deleteMesspunktEmpfang', () => {
    it('should delete item', () => {
      let fertig = false;
      service.deleteMesspunktEmpfang(1).subscribe(() => {
        fertig = true;
      });

      const req = httpMock.expectOne(`${apiUrl}/1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
      expect(fertig).toBe(true);
    });

    it('should pass on a 404 error', () => {
      let status = 0;
      service.deleteMesspunktEmpfang(99).subscribe({
        next: () => { throw new Error('should have failed'); },
        error: (error) => { status = error.status; }
      });

      const req = httpMock.expectOne(`${apiUrl}/99`);
      req.flush(null, { status: 404, statusText: 'Not Found' });
      expect(status).toBe(404);
    });
  });
});
