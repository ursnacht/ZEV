import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { MesswerteService, CalculationResponse, MesswertData } from './messwerte.service';

describe('MesswerteService', () => {
  let service: MesswerteService;
  let httpMock: HttpTestingController;
  const apiUrl = 'http://localhost:8090/api/messwerte';

  const mockCalculationResponse: CalculationResponse = {
    status: 'success',
    algorithm: 'EQUAL_SHARE',
    processedTimestamps: 10,
    processedRecords: 50,
    dateFrom: '2024-01-01T00:00:00',
    dateTo: '2024-01-31T23:59:59',
    totalSolarProduced: 100.5,
    totalDistributed: 95.2
  };

  const mockMesswertData: MesswertData[] = [
    { zeit: '2024-01-15T00:00', total: 5.0, zevCalculated: 1.8 },
    { zeit: '2024-01-15T00:15', total: 3.0, zevCalculated: 1.2 }
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [MesswerteService]
    });
    service = TestBed.inject(MesswerteService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('calculateDistribution', () => {
    it('should call POST with correct params', () => {
      service.calculateDistribution('2024-01-01', '2024-01-31', 'EQUAL_SHARE').subscribe(result => {
        expect(result).toEqual(mockCalculationResponse);
      });

      const req = httpMock.expectOne(
        `${apiUrl}/calculate-distribution?dateFrom=2024-01-01&dateTo=2024-01-31&algorithm=EQUAL_SHARE`
      );
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toBeNull();
      req.flush(mockCalculationResponse);
    });

    it('should use EQUAL_SHARE as default algorithm', () => {
      service.calculateDistribution('2024-01-01', '2024-01-31').subscribe();

      const req = httpMock.expectOne(
        `${apiUrl}/calculate-distribution?dateFrom=2024-01-01&dateTo=2024-01-31&algorithm=EQUAL_SHARE`
      );
      expect(req.request.method).toBe('POST');
      req.flush(mockCalculationResponse);
    });

    it('should pass PROPORTIONAL algorithm', () => {
      service.calculateDistribution('2024-01-01', '2024-01-31', 'PROPORTIONAL').subscribe();

      const req = httpMock.expectOne(
        `${apiUrl}/calculate-distribution?dateFrom=2024-01-01&dateTo=2024-01-31&algorithm=PROPORTIONAL`
      );
      expect(req.request.method).toBe('POST');
      req.flush(mockCalculationResponse);
    });

    it('should return error response on failure', () => {
      const errorResponse: CalculationResponse = {
        status: 'error',
        processedTimestamps: 0,
        processedRecords: 0,
        dateFrom: '',
        dateTo: '',
        totalSolarProduced: 0,
        totalDistributed: 0,
        message: 'Calculation failed'
      };

      service.calculateDistribution('2024-01-01', '2024-01-31').subscribe(result => {
        expect(result.status).toBe('error');
        expect(result.message).toBe('Calculation failed');
      });

      const req = httpMock.expectOne(
        `${apiUrl}/calculate-distribution?dateFrom=2024-01-01&dateTo=2024-01-31&algorithm=EQUAL_SHARE`
      );
      req.flush(errorResponse);
    });
  });

  describe('getMesswerteByEinheit', () => {
    it('should call GET with correct params', () => {
      service.getMesswerteByEinheit(1, '2024-01-01', '2024-01-31').subscribe(result => {
        expect(result).toEqual(mockMesswertData);
        expect(result.length).toBe(2);
      });

      const req = httpMock.expectOne(
        `${apiUrl}/by-einheit?einheitId=1&dateFrom=2024-01-01&dateTo=2024-01-31`
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockMesswertData);
    });

    it('should return empty array when no data', () => {
      service.getMesswerteByEinheit(1, '2024-01-01', '2024-01-31').subscribe(result => {
        expect(result).toEqual([]);
        expect(result.length).toBe(0);
      });

      const req = httpMock.expectOne(
        `${apiUrl}/by-einheit?einheitId=1&dateFrom=2024-01-01&dateTo=2024-01-31`
      );
      req.flush([]);
    });

    it('should pass einheitId as string parameter', () => {
      service.getMesswerteByEinheit(42, '2024-01-01', '2024-01-31').subscribe();

      const req = httpMock.expectOne(
        `${apiUrl}/by-einheit?einheitId=42&dateFrom=2024-01-01&dateTo=2024-01-31`
      );
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });
  });
});
