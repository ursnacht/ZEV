import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { PreiszeitreiheService } from './preiszeitreihe.service';
import { PreiszeitreiheDownload, PreiszeitreihePunkt } from '../models/preiszeitreihe.model';

describe('PreiszeitreiheService', () => {
  let service: PreiszeitreiheService;
  let httpMock: HttpTestingController;
  const apiUrl = 'http://localhost:8090/api/preiszeitreihe';

  const mockPunkte: PreiszeitreihePunkt[] = [
    { zeit: '2026-01-15T11:00:00', preis: 0.138 },
    { zeit: '2026-01-15T11:15:00', preis: 0.142 }
  ];

  const mockDownload: PreiszeitreiheDownload = {
    abgerufen: 96,
    neu: 90,
    aktualisiert: 6,
    uebersprungen: 0,
    publikation: '2026-08-27T15:50:00'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [PreiszeitreiheService]
    });
    service = TestBed.inject(PreiszeitreiheService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getPunkte', () => {
    it('should return the points of a period', () => {
      let ergebnis: PreiszeitreihePunkt[] | undefined;
      service.getPunkte('2026-01-15', '2026-01-15').subscribe(punkte => ergebnis = punkte);

      const req = httpMock.expectOne(`${apiUrl}?von=2026-01-15&bis=2026-01-15`);
      expect(req.request.method).toBe('GET');
      req.flush(mockPunkte);

      expect(ergebnis).toEqual(mockPunkte);
    });

    it('should pass von and bis as query parameters', () => {
      service.getPunkte('2026-03-01', '2026-03-31').subscribe();

      // Die Parameter stehen in der URL (String-Aufbau wie in den uebrigen Services dieses
      // Projekts), nicht in HttpParams - deshalb wird hier urlWithParams geprueft.
      const req = httpMock.expectOne(r => r.url.startsWith(apiUrl));
      expect(req.request.urlWithParams).toBe(`${apiUrl}?von=2026-03-01&bis=2026-03-31`);
      req.flush([]);
    });

    it('should return an empty list when nothing is stored', () => {
      let ergebnis: PreiszeitreihePunkt[] | undefined;
      service.getPunkte('2026-01-15', '2026-01-15').subscribe(punkte => ergebnis = punkte);

      httpMock.expectOne(() => true).flush([]);

      expect(ergebnis).toEqual([]);
    });

    it('should propagate the server error text', () => {
      let fehler: unknown;
      service.getPunkte('2026-01-20', '2026-01-15')
        .subscribe({ error: (e) => fehler = e });

      httpMock.expectOne(() => true).flush('Datum von muss vor oder gleich Datum bis liegen',
        { status: 400, statusText: 'Bad Request' });

      // Der Rumpf ist Klartext - die Maske zeigt ihn direkt an (kein [object Object]).
      expect((fehler as { error: string }).error)
        .toBe('Datum von muss vor oder gleich Datum bis liegen');
    });
  });

  describe('download', () => {
    it('should post to the download endpoint', () => {
      let ergebnis: PreiszeitreiheDownload | undefined;
      service.download().subscribe(r => ergebnis = r);

      const req = httpMock.expectOne(`${apiUrl}/download`);
      expect(req.request.method).toBe('POST');
      req.flush(mockDownload);

      expect(ergebnis).toEqual(mockDownload);
    });

    it('should accept a null publication date', () => {
      // Das Backend schickt null (nicht undefined), wenn die Quelle keinen Stand nennt.
      let ergebnis: PreiszeitreiheDownload | undefined;
      service.download().subscribe(r => ergebnis = r);

      httpMock.expectOne(`${apiUrl}/download`)
        .flush({ ...mockDownload, publikation: null });

      expect(ergebnis?.publikation).toBeNull();
    });

    it('should propagate a 502 from the source', () => {
      let fehler: { status: number; error: string } | undefined;
      service.download().subscribe({ error: (e) => fehler = e });

      httpMock.expectOne(`${apiUrl}/download`)
        .flush('Die Quelle der Einspeisepreise ist nicht erreichbar',
          { status: 502, statusText: 'Bad Gateway' });

      expect(fehler?.status).toBe(502);
      expect(fehler?.error).toContain('nicht erreichbar');
    });

    it('should propagate a 403 when the feature flag is off', () => {
      let fehler: { status: number } | undefined;
      service.download().subscribe({ error: (e) => fehler = e });

      httpMock.expectOne(`${apiUrl}/download`)
        .flush({ error: 'FEATURE_FLAG_DEAKTIVIERT' }, { status: 403, statusText: 'Forbidden' });

      expect(fehler?.status).toBe(403);
    });
  });
});
