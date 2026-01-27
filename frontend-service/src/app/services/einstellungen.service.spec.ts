import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { EinstellungenService } from './einstellungen.service';
import { Einstellungen } from '../models/einstellungen.model';

describe('EinstellungenService', () => {
  let service: EinstellungenService;
  let httpMock: HttpTestingController;
  const apiUrl = 'http://localhost:8090/api/einstellungen';

  const mockEinstellungen: Einstellungen = {
    id: 1,
    rechnung: {
      zahlungsfrist: '30 Tage',
      iban: 'CH7006300016946459910',
      steller: {
        name: 'Urs Nacht',
        strasse: 'Hangstrasse 14a',
        plz: '3044',
        ort: 'Innerberg'
      }
    }
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [EinstellungenService]
    });
    service = TestBed.inject(EinstellungenService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getEinstellungen', () => {
    it('should return einstellungen when configured', () => {
      service.getEinstellungen().subscribe(result => {
        expect(result).toEqual(mockEinstellungen);
        expect(result!.id).toBe(1);
        expect(result!.rechnung.zahlungsfrist).toBe('30 Tage');
        expect(result!.rechnung.iban).toBe('CH7006300016946459910');
        expect(result!.rechnung.steller.name).toBe('Urs Nacht');
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('GET');
      req.flush(mockEinstellungen);
    });

    it('should return null when no settings exist', () => {
      service.getEinstellungen().subscribe(result => {
        expect(result).toBeNull();
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('GET');
      req.flush(null);
    });
  });

  describe('saveEinstellungen', () => {
    it('should save new einstellungen', () => {
      const newEinstellungen: Einstellungen = {
        rechnung: {
          zahlungsfrist: '30 Tage',
          iban: 'CH7006300016946459910',
          steller: {
            name: 'Urs Nacht',
            strasse: 'Hangstrasse 14a',
            plz: '3044',
            ort: 'Innerberg'
          }
        }
      };

      const savedEinstellungen = { ...newEinstellungen, id: 1 };

      service.saveEinstellungen(newEinstellungen).subscribe(result => {
        expect(result).toEqual(savedEinstellungen);
        expect(result.id).toBe(1);
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(newEinstellungen);
      req.flush(savedEinstellungen);
    });

    it('should update existing einstellungen', () => {
      const updatedEinstellungen: Einstellungen = {
        ...mockEinstellungen,
        rechnung: {
          ...mockEinstellungen.rechnung,
          zahlungsfrist: '60 Tage'
        }
      };

      service.saveEinstellungen(updatedEinstellungen).subscribe(result => {
        expect(result).toEqual(updatedEinstellungen);
        expect(result.rechnung.zahlungsfrist).toBe('60 Tage');
      });

      const req = httpMock.expectOne(apiUrl);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updatedEinstellungen);
      req.flush(updatedEinstellungen);
    });
  });
});
