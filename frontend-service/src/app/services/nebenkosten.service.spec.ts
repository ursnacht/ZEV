import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NebenkostenService } from './nebenkosten.service';
import { NkAbrechnung, NkAbrechnungDetail, NkPositionsart } from '../models/nebenkosten.model';
// `Mengeneinheit` lebt im Tarif-Modell und wird von der Nebenkostenabrechnung mitbenutzt
// (Abrechnung.md, FR-5: ein Enum statt zwei).
import { Mengeneinheit } from '../models/tarif.model';

/**
 * Tests des HTTP-Zugriffs auf die Nebenkostenabrechnungen
 * (`Specs/Nebenkosten/Abrechnung.md`, FR-6).
 *
 * Der Service ist schmal, trägt aber eine Eigenheit, die ihn prüfenswert macht: Der **gesamte**
 * Stand einer Abrechnung — Kopf, Positionen, Zusatzzeilen und Akonto — geht in **einem** `PUT`,
 * und derselbe Typ kommt als Antwort zurück. Verliert dieser Rumpf unterwegs ein Feld, gehen
 * Erfassungen still verloren; deshalb prüfen die Tests hier den Rumpf und nicht nur die URL.
 */
describe('NebenkostenService', () => {
  let service: NebenkostenService;
  let httpMock: HttpTestingController;
  const apiUrl = 'http://localhost:8090/api/nebenkosten/abrechnungen';

  const mockAbrechnung: NkAbrechnung = {
    id: 7,
    bezeichnung: 'Nebenkosten 2026',
    datumVon: '2026-01-01',
    datumBis: '2026-12-31',
    anzahlWohnungen: 9,
    abgerechnet: false
  };

  const mockDetail: NkAbrechnungDetail = {
    abrechnung: mockAbrechnung,
    positionen: [{
      id: 1,
      art: NkPositionsart.UMLAGE,
      bezeichnung: 'Allgemeinstrom',
      reihenfolge: 1,
      einheit: Mengeneinheit.CHF,
      totalbetrag: 900,
      gesamtmenge: null,
      betragProEinheit: null,
      prozentsatz: null,
      verbraeuche: [{ mieterId: 100, menge: null }]
    }],
    zusaetze: [{
      id: 2,
      mieterId: 100,
      reihenfolge: 2,
      bezeichnung: 'Gartenpflege',
      einheit: Mengeneinheit.STUECK,
      menge: 3,
      betragProEinheit: 25
    }],
    akonto: [{ id: 3, mieterId: 100, anzahlMonate: 12, betragProMonat: 150, korrektur: 0 }],
    anzahlWohnungenVorschlag: 9
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [NebenkostenService]
    });
    service = TestBed.inject(NebenkostenService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  // ==================== Liste ====================

  it('should load all billings', () => {
    let ergebnis: NkAbrechnung[] | undefined;
    service.getAllAbrechnungen().subscribe({ next: a => (ergebnis = a) });

    const req = httpMock.expectOne(apiUrl);
    expect(req.request.method).toBe('GET');
    req.flush([mockAbrechnung]);

    expect(ergebnis?.length).toBe(1);
    expect(ergebnis?.[0].bezeichnung).toBe('Nebenkosten 2026');
  });

  it('should return an empty list without error', () => {
    let ergebnis: NkAbrechnung[] | undefined;
    service.getAllAbrechnungen().subscribe({ next: a => (ergebnis = a) });

    httpMock.expectOne(apiUrl).flush([]);

    expect(ergebnis).toEqual([]);
  });

  /**
   * Der Feature-Flag wird serverseitig geprüft (FR-6); ein 403 muss den Aufrufer erreichen,
   * damit die Maske nicht mit leerer Liste tut, als sei alles in Ordnung.
   */
  it('should surface a 403 from the disabled feature flag', () => {
    let fehler: { status?: number } | undefined;
    service.getAllAbrechnungen().subscribe({ next: () => {}, error: e => (fehler = e) });

    httpMock.expectOne(apiUrl)
      .flush({ error: 'FEATURE_FLAG_DEAKTIVIERT' }, { status: 403, statusText: 'Forbidden' });

    expect(fehler?.status).toBe(403);
  });

  // ==================== Detail und Vorlage ====================

  it('should load a billing detail by id', () => {
    let ergebnis: NkAbrechnungDetail | undefined;
    service.getAbrechnungDetail(7).subscribe({ next: d => (ergebnis = d) });

    const req = httpMock.expectOne(`${apiUrl}/7`);
    expect(req.request.method).toBe('GET');
    req.flush(mockDetail);

    expect(ergebnis?.abrechnung.id).toBe(7);
    expect(ergebnis?.positionen.length).toBe(1);
    expect(ergebnis?.zusaetze.length).toBe(1);
    expect(ergebnis?.akonto.length).toBe(1);
  });

  it('should report a missing billing to the caller', () => {
    let fehler: { status?: number } | undefined;
    service.getAbrechnungDetail(99).subscribe({ next: () => {}, error: e => (fehler = e) });

    httpMock.expectOne(`${apiUrl}/99`)
      .flush({}, { status: 404, statusText: 'Not Found' });

    expect(fehler?.status).toBe(404);
  });

  /**
   * Die Vorlage geht an den literalen Pfad `/vorlage` — nicht an `/{id}`.
   *
   * Beide sind `GET` unter demselben Präfix. Ein Tippfehler hier führte zu einer Abfrage nach
   * einer Abrechnung mit der ID „vorlage", und die Maske für eine neue Abrechnung blieb leer.
   */
  it('should load the template from the literal path', () => {
    let ergebnis: NkAbrechnungDetail | undefined;
    service.getVorlage().subscribe({ next: d => (ergebnis = d) });

    const req = httpMock.expectOne(`${apiUrl}/vorlage`);
    expect(req.request.method).toBe('GET');
    req.flush({ ...mockDetail, abrechnung: { ...mockAbrechnung, id: undefined } });

    expect(ergebnis?.abrechnung.id).toBeUndefined();
    expect(ergebnis?.anzahlWohnungenVorschlag).toBe(9);
  });

  it('should accept a template without a suggested number of flats', () => {
    // `null` heisst: Der Mandant hat keine nebenkostenrelevanten Wohnungen (FR-2). Die Maske
    // muss das tragen, statt eine Zahl zu erwarten.
    let ergebnis: NkAbrechnungDetail | undefined;
    service.getVorlage().subscribe({ next: d => (ergebnis = d) });

    httpMock.expectOne(`${apiUrl}/vorlage`)
      .flush({ ...mockDetail, anzahlWohnungenVorschlag: null });

    expect(ergebnis?.anzahlWohnungenVorschlag).toBeNull();
  });

  // ==================== Anlegen ====================

  it('should create a billing and send the header as body', () => {
    const neu: NkAbrechnung = {
      bezeichnung: 'Nebenkosten 2027',
      datumVon: '2027-01-01',
      datumBis: '2027-12-31',
      anzahlWohnungen: 9,
      abgerechnet: false
    };
    let ergebnis: NkAbrechnung | undefined;
    service.createAbrechnung(neu).subscribe({ next: a => (ergebnis = a) });

    const req = httpMock.expectOne(apiUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(neu);
    req.flush({ ...neu, id: 8 });

    expect(ergebnis?.id).toBe(8);
  });

  it('should surface a rejected period to the caller', () => {
    let fehler: { error?: { error?: string } } | undefined;
    service.createAbrechnung(mockAbrechnung)
      .subscribe({ next: () => {}, error: e => (fehler = e) });

    httpMock.expectOne(apiUrl)
      .flush({ error: 'NK_FEHLER_ZEITRAUM' }, { status: 400, statusText: 'Bad Request' });

    expect(fehler?.error?.error).toBe('NK_FEHLER_ZEITRAUM');
  });

  // ==================== Speichern: der vollständige Stand ====================

  /**
   * Der `PUT` trägt den **gesamten** Stand. Dieser Test ist der wichtigste der Datei: Fehlt im
   * Rumpf eine der vier Listen, verliert der Benutzer beim Speichern stillschweigend seine
   * Erfassungen — die Maske zeigt danach die Antwort des Servers, also genau das, was ankam.
   */
  it('should send header, positions, extras and prepayments in one request', () => {
    service.updateAbrechnung(7, mockDetail).subscribe();

    const req = httpMock.expectOne(`${apiUrl}/7`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.abrechnung.bezeichnung).toBe('Nebenkosten 2026');
    expect(req.request.body.positionen.length).toBe(1);
    expect(req.request.body.zusaetze.length).toBe(1);
    expect(req.request.body.akonto.length).toBe(1);
    // Die eingebetteten Mengen je Mieter gehören zur Position und dürfen nicht verloren gehen.
    expect(req.request.body.positionen[0].verbraeuche.length).toBe(1);
    req.flush(mockDetail);
  });

  it('should return the server values after saving', () => {
    // Das Backend ist massgebend: Es vergibt die Reihenfolge neu und liefert die Berechnung.
    let ergebnis: NkAbrechnungDetail | undefined;
    service.updateAbrechnung(7, mockDetail).subscribe({ next: d => (ergebnis = d) });

    httpMock.expectOne(`${apiUrl}/7`).flush({
      ...mockDetail,
      berechnung: { nenner: 3285, summeTage: 3285, mieter: [], umlagen: [] }
    });

    expect(ergebnis?.berechnung?.nenner).toBe(3285);
  });

  it('should surface a rejected save of a closed billing', () => {
    // FR-7: Ein `PUT` auf eine abgerechnete Abrechnung wird abgewiesen.
    let fehler: { error?: { error?: string } } | undefined;
    service.updateAbrechnung(7, mockDetail)
      .subscribe({ next: () => {}, error: e => (fehler = e) });

    httpMock.expectOne(`${apiUrl}/7`)
      .flush({ error: 'NK_FEHLER_ABGERECHNET' }, { status: 400, statusText: 'Bad Request' });

    expect(fehler?.error?.error).toBe('NK_FEHLER_ABGERECHNET');
  });

  // ==================== Flag „abgerechnet" ====================

  /**
   * Eigener Endpunkt mit `PATCH`: Das Flag ist als einziges Feld auch auf einer abgeschlossenen
   * Abrechnung änderbar. Ginge es über den `PUT`, wäre das Wieder-Öffnen unmöglich — der `PUT`
   * weist eine abgerechnete Abrechnung ab.
   */
  it('should set the closed flag via its own PATCH endpoint', () => {
    let ergebnis: NkAbrechnung | undefined;
    service.setAbgerechnet(7, true).subscribe({ next: a => (ergebnis = a) });

    const req = httpMock.expectOne(`${apiUrl}/7/abgerechnet`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ abgerechnet: true });
    req.flush({ ...mockAbrechnung, abgerechnet: true });

    expect(ergebnis?.abgerechnet).toBe(true);
  });

  it('should reopen a billing by sending false', () => {
    service.setAbgerechnet(7, false).subscribe();

    const req = httpMock.expectOne(`${apiUrl}/7/abgerechnet`);
    expect(req.request.body).toEqual({ abgerechnet: false });
    req.flush({ ...mockAbrechnung, abgerechnet: false });
  });

  // ==================== Löschen ====================

  it('should delete a billing by id', () => {
    let fertig = false;
    service.deleteAbrechnung(7).subscribe({ next: () => (fertig = true) });

    const req = httpMock.expectOne(`${apiUrl}/7`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);

    expect(fertig).toBe(true);
  });

  it('should surface a rejected delete of a closed billing', () => {
    let fehler: { error?: { error?: string } } | undefined;
    service.deleteAbrechnung(7).subscribe({ next: () => {}, error: e => (fehler = e) });

    httpMock.expectOne(`${apiUrl}/7`)
      .flush({ error: 'NK_FEHLER_ABGERECHNET' }, { status: 400, statusText: 'Bad Request' });

    expect(fehler?.error?.error).toBe('NK_FEHLER_ABGERECHNET');
  });

  // ============ Rechnungen (Specs/Nebenkosten/RechnungenGenerieren.md, FR-6) ============

  describe('erzeugeRechnungen', () => {
    const mockLauf = {
      abrechnungId: 7,
      bezeichnung: 'Nebenkosten 2026',
      von: '2026-01-01',
      bis: '2026-12-31',
      anzahlRechnungen: 2,
      anzahlForderungen: 1,
      summeForderungen: 812.35,
      rechnungen: [
        {
          mieterId: 45, mieterName: 'Max Muster', saldo: 812.35,
          forderungGebucht: true, filename: 'Nebenkosten_2026_Max_Muster.pdf', fehler: null
        },
        {
          mieterId: 46, mieterName: 'Erika Beispiel', saldo: -480,
          forderungGebucht: false, filename: 'Nebenkosten_2026_Erika_Beispiel.pdf', fehler: null
        }
      ]
    };

    /**
     * Eigener Endpunkt im NK-Bereich, **nicht** `POST /api/rechnungen/generate`: Dessen Antwort
     * wäre sonst je Rechnungsart eine andere geworden.
     */
    it('should post to the NK route of the billing', () => {
      let ergebnis: typeof mockLauf | undefined;
      service.erzeugeRechnungen(7).subscribe(r => (ergebnis = r as typeof mockLauf));

      const req = httpMock.expectOne(`${apiUrl}/7/rechnungen`);
      expect(req.request.method).toBe('POST');
      req.flush(mockLauf);

      expect(ergebnis).toEqual(mockLauf);
    });

    it('should default the language to German', () => {
      service.erzeugeRechnungen(7).subscribe();

      const req = httpMock.expectOne(`${apiUrl}/7/rechnungen`);
      expect(req.request.body).toEqual({ sprache: 'de' });
      req.flush(mockLauf);
    });

    it('should pass the given language', () => {
      service.erzeugeRechnungen(7, 'en').subscribe();

      const req = httpMock.expectOne(`${apiUrl}/7/rechnungen`);
      expect(req.request.body).toEqual({ sprache: 'en' });
      req.flush(mockLauf);
    });

    it('should keep null apart from undefined in the result', () => {
      // Jackson schickt `null`; ein `!== undefined` auf `fehler` würde immer zutreffen und jede
      // Zeile als fehlerhaft lesen.
      let ergebnis: typeof mockLauf | undefined;
      service.erzeugeRechnungen(7).subscribe(r => (ergebnis = r as typeof mockLauf));

      httpMock.expectOne(`${apiUrl}/7/rechnungen`).flush(mockLauf);

      expect(ergebnis!.rechnungen[0].fehler).toBeNull();
    });

    it('should surface a rejected run of an open billing', () => {
      let fehler: { error?: { error?: string } } | undefined;
      service.erzeugeRechnungen(7).subscribe({ next: () => {}, error: e => (fehler = e) });

      httpMock.expectOne(`${apiUrl}/7/rechnungen`)
        .flush({ error: 'NK_FEHLER_NICHT_ABGERECHNET' },
          { status: 400, statusText: 'Bad Request' });

      expect(fehler?.error?.error).toBe('NK_FEHLER_NICHT_ABGERECHNET');
    });

    it('should surface a 403 when the feature flag is off', () => {
      let status: number | undefined;
      service.erzeugeRechnungen(7).subscribe({ next: () => {}, error: e => (status = e.status) });

      httpMock.expectOne(`${apiUrl}/7/rechnungen`)
        .flush({ error: 'FEATURE_FLAG_DEAKTIVIERT' }, { status: 403, statusText: 'Forbidden' });

      expect(status).toBe(403);
    });
  });

  describe('ladeRechnungPdf', () => {
    it('should get the pdf as a blob from the NK route', () => {
      let blob: Blob | undefined;
      service.ladeRechnungPdf(7, 45).subscribe(b => (blob = b));

      const req = httpMock.expectOne(`${apiUrl}/7/rechnungen/45/pdf`);
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('blob');
      req.flush(new Blob(['%PDF']));

      expect(blob).toBeInstanceOf(Blob);
    });

    /**
     * Der Service löst den Download **nicht** selbst aus, sondern liefert das Blob: Nach 30
     * Minuten ist das PDF weg, und der Aufrufer muss darauf einen Hinweis zeigen können.
     */
    it('should surface an expired pdf as an error', () => {
      let status: number | undefined;
      service.ladeRechnungPdf(7, 45).subscribe({ next: () => {}, error: e => (status = e.status) });

      httpMock.expectOne(`${apiUrl}/7/rechnungen/45/pdf`)
        .flush(null, { status: 404, statusText: 'Not Found' });

      expect(status).toBe(404);
    });
  });
});
