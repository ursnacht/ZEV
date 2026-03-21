import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { RechnungService, GenerateRequest, GenerateResponse, GeneratedRechnung } from './rechnung.service';

describe('RechnungService', () => {
  let service: RechnungService;
  let httpMock: HttpTestingController;
  const apiUrl = 'http://localhost:8090/api/rechnungen';

  const mockRechnung: GeneratedRechnung = {
    einheitId: 1,
    einheitName: 'Wohnung 1',
    mieterName: 'Max Muster',
    endBetrag: 125.50,
    filename: 'Wohnung_1.pdf',
    downloadKey: 'Wohnung_1'
  };

  const mockResponse: GenerateResponse = {
    rechnungen: [mockRechnung],
    count: 1
  };

  const mockRequest: GenerateRequest = {
    von: '2024-01-01',
    bis: '2024-03-31',
    einheitIds: [1, 2],
    sprache: 'de'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [RechnungService]
    });
    service = TestBed.inject(RechnungService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('generateRechnungen', () => {
    it('should POST to generate endpoint and return response', () => {
      service.generateRechnungen(mockRequest).subscribe(response => {
        expect(response).toEqual(mockResponse);
        expect(response.count).toBe(1);
        expect(response.rechnungen.length).toBe(1);
      });

      const req = httpMock.expectOne(`${apiUrl}/generate`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(mockRequest);
      req.flush(mockResponse);
    });

    it('should pass the correct request body including einheitIds', () => {
      service.generateRechnungen(mockRequest).subscribe();

      const req = httpMock.expectOne(`${apiUrl}/generate`);
      expect(req.request.body.von).toBe('2024-01-01');
      expect(req.request.body.bis).toBe('2024-03-31');
      expect(req.request.body.einheitIds).toEqual([1, 2]);
      expect(req.request.body.sprache).toBe('de');
      req.flush(mockResponse);
    });

    it('should return response with multiple rechnungen', () => {
      const multiResponse: GenerateResponse = {
        rechnungen: [
          mockRechnung,
          { ...mockRechnung, einheitId: 2, einheitName: 'Wohnung 2', downloadKey: 'Wohnung_2' }
        ],
        count: 2
      };

      service.generateRechnungen(mockRequest).subscribe(response => {
        expect(response.count).toBe(2);
        expect(response.rechnungen.length).toBe(2);
      });

      const req = httpMock.expectOne(`${apiUrl}/generate`);
      req.flush(multiResponse);
    });

    it('should return empty response when no rechnungen generated', () => {
      const emptyResponse: GenerateResponse = { rechnungen: [], count: 0 };

      service.generateRechnungen(mockRequest).subscribe(response => {
        expect(response.count).toBe(0);
        expect(response.rechnungen).toEqual([]);
      });

      const req = httpMock.expectOne(`${apiUrl}/generate`);
      req.flush(emptyResponse);
    });
  });

  describe('downloadRechnung', () => {
    it('should make GET request to download endpoint with blob response type', () => {
      spyOn(window.URL, 'createObjectURL').and.returnValue('blob:fake-url');
      spyOn(window.URL, 'revokeObjectURL');
      const linkSpy = jasmine.createSpyObj('a', ['click']);
      spyOn(document, 'createElement').and.returnValue(linkSpy as HTMLAnchorElement);

      service.downloadRechnung('Wohnung_1', 'Wohnung_1.pdf');

      const req = httpMock.expectOne(`${apiUrl}/download/Wohnung_1`);
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('blob');
      req.flush(new Blob(['%PDF-1.4'], { type: 'application/pdf' }));
    });

    it('should set correct filename on the download link', () => {
      spyOn(window.URL, 'createObjectURL').and.returnValue('blob:fake-url');
      spyOn(window.URL, 'revokeObjectURL');
      const linkSpy = jasmine.createSpyObj('a', ['click']);
      spyOn(document, 'createElement').and.returnValue(linkSpy as HTMLAnchorElement);

      service.downloadRechnung('Wohnung_1', 'Wohnung_1.pdf');

      const req = httpMock.expectOne(`${apiUrl}/download/Wohnung_1`);
      req.flush(new Blob(['%PDF']));

      expect(linkSpy.download).toBe('Wohnung_1.pdf');
      expect(linkSpy.click).toHaveBeenCalled();
    });

    it('should revoke object URL after download', () => {
      const revokeUrlSpy = spyOn(window.URL, 'revokeObjectURL');
      spyOn(window.URL, 'createObjectURL').and.returnValue('blob:fake-url');
      const linkSpy = jasmine.createSpyObj('a', ['click']);
      spyOn(document, 'createElement').and.returnValue(linkSpy as HTMLAnchorElement);

      service.downloadRechnung('Wohnung_1', 'Wohnung_1.pdf');

      const req = httpMock.expectOne(`${apiUrl}/download/Wohnung_1`);
      req.flush(new Blob(['%PDF']));

      expect(revokeUrlSpy).toHaveBeenCalledWith('blob:fake-url');
    });

    it('should handle download error gracefully without throwing', () => {
      const consoleSpy = spyOn(console, 'error');

      service.downloadRechnung('invalid_key', 'invalid.pdf');

      const req = httpMock.expectOne(`${apiUrl}/download/invalid_key`);
      req.error(new ProgressEvent('error'));

      expect(consoleSpy).toHaveBeenCalledWith('Download failed:', jasmine.anything());
    });
  });
});
