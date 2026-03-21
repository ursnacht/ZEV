import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { StatistikService } from './statistik.service';
import { Statistik } from '../models/statistik.model';

describe('StatistikService', () => {
  let service: StatistikService;
  let httpMock: HttpTestingController;
  const apiUrl = 'http://localhost:8090/api/statistik';

  const mockStatistik: Statistik = {
    messwerteBisDate: '2024-03-31',
    datenVollstaendig: true,
    fehlendeEinheiten: [],
    fehlendeTage: [],
    monate: [
      {
        jahr: 2024,
        monat: 1,
        von: '2024-01-01',
        bis: '2024-01-31',
        datenVollstaendig: true,
        fehlendeEinheiten: [],
        fehlendeTage: [],
        summeProducerTotal: 1200,
        summeConsumerTotal: 950,
        summeProducerZev: 800,
        summeConsumerZev: 800,
        summeConsumerZevCalculated: 798,
        summenCDGleich: true,
        differenzCD: 0,
        summenCEGleich: true,
        differenzCE: 2,
        summenDEGleich: true,
        differenzDE: 0,
        tageAbweichungen: [],
        einheitSummen: []
      }
    ],
    toleranz: 0.01
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [StatistikService]
    });
    service = TestBed.inject(StatistikService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getStatistik', () => {
    it('should return statistik for given date range', () => {
      service.getStatistik('2024-01-01', '2024-03-31').subscribe(statistik => {
        expect(statistik).toEqual(mockStatistik);
        expect(statistik.datenVollstaendig).toBeTrue();
        expect(statistik.monate.length).toBe(1);
      });

      const req = httpMock.expectOne(r =>
        r.url === apiUrl &&
        r.params.get('von') === '2024-01-01' &&
        r.params.get('bis') === '2024-03-31'
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockStatistik);
    });

    it('should include von and bis as query parameters', () => {
      service.getStatistik('2024-04-01', '2024-06-30').subscribe();

      const req = httpMock.expectOne(r => r.url === apiUrl);
      expect(req.request.params.get('von')).toBe('2024-04-01');
      expect(req.request.params.get('bis')).toBe('2024-06-30');
      req.flush(mockStatistik);
    });

    it('should return statistik with incomplete data flag', () => {
      const incompleteStatistik: Statistik = {
        ...mockStatistik,
        datenVollstaendig: false,
        fehlendeEinheiten: ['Wohnung 3']
      };

      service.getStatistik('2024-01-01', '2024-03-31').subscribe(statistik => {
        expect(statistik.datenVollstaendig).toBeFalse();
        expect(statistik.fehlendeEinheiten).toContain('Wohnung 3');
      });

      const req = httpMock.expectOne(r => r.url === apiUrl);
      req.flush(incompleteStatistik);
    });

    it('should return statistik with empty monate when no data', () => {
      const emptyStatistik: Statistik = {
        ...mockStatistik,
        monate: []
      };

      service.getStatistik('2024-01-01', '2024-03-31').subscribe(statistik => {
        expect(statistik.monate.length).toBe(0);
      });

      const req = httpMock.expectOne(r => r.url === apiUrl);
      req.flush(emptyStatistik);
    });
  });

  describe('getLetztesMessdatum', () => {
    it('should return the last measurement date as string', () => {
      service.getLetztesMessdatum().subscribe(datum => {
        expect(datum).toBe('"2024-03-31"');
      });

      const req = httpMock.expectOne(`${apiUrl}/letztes-datum`);
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('text');
      req.flush('"2024-03-31"');
    });

    it('should return null when no measurements exist', () => {
      service.getLetztesMessdatum().subscribe(datum => {
        expect(datum).toBe('');
      });

      const req = httpMock.expectOne(`${apiUrl}/letztes-datum`);
      req.flush('');
    });
  });

  describe('exportPdf', () => {
    it('should make GET request with blob response type', () => {
      const mockBlob = new Blob(['%PDF-1.4'], { type: 'application/pdf' });

      service.exportPdf('2024-01-01', '2024-03-31', 'de').subscribe(blob => {
        expect(blob).toEqual(mockBlob);
      });

      const req = httpMock.expectOne(r =>
        r.url === `${apiUrl}/export/pdf` &&
        r.params.get('von') === '2024-01-01' &&
        r.params.get('bis') === '2024-03-31' &&
        r.params.get('sprache') === 'de'
      );
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('blob');
      req.flush(mockBlob);
    });

    it('should include all query parameters', () => {
      service.exportPdf('2024-04-01', '2024-06-30', 'fr').subscribe();

      const req = httpMock.expectOne(r => r.url === `${apiUrl}/export/pdf`);
      expect(req.request.params.get('von')).toBe('2024-04-01');
      expect(req.request.params.get('bis')).toBe('2024-06-30');
      expect(req.request.params.get('sprache')).toBe('fr');
      req.flush(new Blob());
    });

    it('should use de as default sprache when not provided', () => {
      service.exportPdf('2024-01-01', '2024-03-31').subscribe();

      const req = httpMock.expectOne(r => r.url === `${apiUrl}/export/pdf`);
      expect(req.request.params.get('sprache')).toBe('de');
      req.flush(new Blob());
    });
  });
});
