import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { SystemmeldungQuery, SystemmeldungService } from './systemmeldung.service';
import { Systemmeldung, SystemmeldungSeite } from '../models/systemmeldung.model';

/**
 * Tests des HTTP-Zugriffs auf die Systemmeldungen (`Specs/Systemmeldungen.md`).
 *
 * Der Schwerpunkt liegt auf dem Aufbau der Query-Parameter: Filter, Sortierung und Paginierung
 * werden **serverseitig** angewandt (FR-1.3/1.4/1.12), der Service ist also die einzige Stelle,
 * an der aus dem Zustand der Maske eine Abfrage wird. Ein falsch weggelassener oder falsch
 * benannter Parameter führt zu einer stillschweigend anderen Liste.
 */
describe('SystemmeldungService', () => {
  let service: SystemmeldungService;
  let httpMock: HttpTestingController;
  const apiUrl = 'http://localhost:8090/api/systemmeldungen';

  const mockMeldung: Systemmeldung = {
    id: 7,
    level: 'ERROR',
    kategorie: 'SYSTEMMELDUNG_KATEGORIE_BILANZMODELL',
    meldungKey: 'BILANZMODELL_KEINE_BILANZDATEN',
    parameter: '15.01.2024 10:15',
    erstmalsAufgetreten: '2024-01-15T10:15:00',
    zuletztAufgetreten: '2024-01-16T10:15:00',
    erledigt: false,
    erledigtAm: null,
    erledigtAutomatisch: false,
    zaehler: 3
  };

  const mockSeite: SystemmeldungSeite = { items: [mockMeldung], hatMehr: true, page: 0 };

  /** Basis-Query — die Pflichtfelder, die die Maske immer setzt. */
  const query = (ergaenzung: Partial<SystemmeldungQuery> = {}): SystemmeldungQuery => ({
    page: 0,
    size: 50,
    sortSpalte: 'zuletztAufgetreten',
    sortRichtung: 'DESC',
    ...ergaenzung
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [SystemmeldungService]
    });
    service = TestBed.inject(SystemmeldungService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  // ==================== getSeite ====================

  it('should request a page with the mandatory parameters', () => {
    let ergebnis: SystemmeldungSeite | undefined;
    service.getSeite(query()).subscribe({ next: seite => (ergebnis = seite) });

    const req = httpMock.expectOne(
      r => r.url === apiUrl
        && r.params.get('page') === '0'
        && r.params.get('size') === '50'
        && r.params.get('sortSpalte') === 'zuletztAufgetreten'
        && r.params.get('sortRichtung') === 'DESC');
    expect(req.request.method).toBe('GET');
    req.flush(mockSeite);

    expect(ergebnis?.items.length).toBe(1);
    expect(ergebnis?.hatMehr).toBe(true);
  });

  /**
   * `erledigt` ist dreiwertig: `undefined` heisst „Alle" und darf **nicht** als Parameter
   * mitgehen — sonst filterte der Server auf einen Wert, den der Benutzer nie gewählt hat.
   */
  it('should omit erledigt when the filter is "Alle"', () => {
    service.getSeite(query({ erledigt: undefined })).subscribe();

    const req = httpMock.expectOne(r => r.url === apiUrl);
    expect(req.request.params.has('erledigt')).toBe(false);
    req.flush(mockSeite);
  });

  it('should send erledigt=false for the "Offene" filter', () => {
    service.getSeite(query({ erledigt: false })).subscribe();

    const req = httpMock.expectOne(r => r.url === apiUrl);
    expect(req.request.params.get('erledigt')).toBe('false');
    req.flush(mockSeite);
  });

  it('should send erledigt=true for the "Erledigte" filter', () => {
    service.getSeite(query({ erledigt: true })).subscribe();

    const req = httpMock.expectOne(r => r.url === apiUrl);
    expect(req.request.params.get('erledigt')).toBe('true');
    req.flush(mockSeite);
  });

  it('should send kategorie and level when set', () => {
    service.getSeite(query({
      kategorie: 'SYSTEMMELDUNG_KATEGORIE_MQTT',
      level: 'WARN'
    })).subscribe();

    const req = httpMock.expectOne(r => r.url === apiUrl);
    expect(req.request.params.get('kategorie')).toBe('SYSTEMMELDUNG_KATEGORIE_MQTT');
    expect(req.request.params.get('level')).toBe('WARN');
    req.flush(mockSeite);
  });

  it('should omit kategorie and level when unset', () => {
    service.getSeite(query()).subscribe();

    const req = httpMock.expectOne(r => r.url === apiUrl);
    expect(req.request.params.has('kategorie')).toBe(false);
    expect(req.request.params.has('level')).toBe(false);
    req.flush(mockSeite);
  });

  it('should pass through page and sort for a later page', () => {
    service.getSeite(query({ page: 3, size: 25, sortSpalte: 'level', sortRichtung: 'ASC' }))
      .subscribe();

    const req = httpMock.expectOne(r => r.url === apiUrl);
    expect(req.request.params.get('page')).toBe('3');
    expect(req.request.params.get('size')).toBe('25');
    expect(req.request.params.get('sortSpalte')).toBe('level');
    expect(req.request.params.get('sortRichtung')).toBe('ASC');
    req.flush(mockSeite);
  });

  it('should surface a server error to the caller', () => {
    let fehler: unknown;
    service.getSeite(query()).subscribe({ next: () => {}, error: e => (fehler = e) });

    httpMock.expectOne(r => r.url === apiUrl)
      .flush({ error: 'FEHLER' }, { status: 500, statusText: 'Server Error' });

    expect(fehler).toBeTruthy();
  });

  // ==================== getKategorien ====================

  it('should load the categories', () => {
    let kategorien: string[] | undefined;
    service.getKategorien().subscribe({ next: k => (kategorien = k) });

    const req = httpMock.expectOne(`${apiUrl}/kategorien`);
    expect(req.request.method).toBe('GET');
    req.flush(['SYSTEMMELDUNG_KATEGORIE_BILANZMODELL', 'SYSTEMMELDUNG_KATEGORIE_MQTT']);

    expect(kategorien?.length).toBe(2);
  });

  // ==================== setErledigt ====================

  it('should send erledigt as a query parameter, not as a body', () => {
    // Der Endpunkt ist ein PUT ohne Rumpf; der Wert steht im Parameter.
    service.setErledigt(7, true).subscribe();

    const req = httpMock.expectOne(r => r.url === `${apiUrl}/7/erledigt`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.params.get('erledigt')).toBe('true');
    expect(req.request.body).toBeNull();
    req.flush({ ...mockMeldung, erledigt: true });
  });

  it('should send erledigt=false when reopening', () => {
    service.setErledigt(7, false).subscribe();

    const req = httpMock.expectOne(r => r.url === `${apiUrl}/7/erledigt`);
    expect(req.request.params.get('erledigt')).toBe('false');
    req.flush(mockMeldung);
  });

  /** Der Reopen-Konflikt kommt als 400 mit Übersetzungs-Key und muss den Aufrufer erreichen. */
  it('should surface the reopen conflict to the caller', () => {
    let fehler: { error?: { error?: string } } | undefined;
    service.setErledigt(7, false).subscribe({ next: () => {}, error: e => (fehler = e) });

    httpMock.expectOne(r => r.url === `${apiUrl}/7/erledigt`)
      .flush({ error: 'SYSTEMMELDUNG_REOPEN_KONFLIKT' },
        { status: 400, statusText: 'Bad Request' });

    expect(fehler?.error?.error).toBe('SYSTEMMELDUNG_REOPEN_KONFLIKT');
  });

  // ==================== Löschen ====================

  it('should delete a single entry by id', () => {
    service.deleteSystemmeldung(7).subscribe();

    const req = httpMock.expectOne(`${apiUrl}/7`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  /**
   * Das Aufräumen geht an den literalen Pfad `/erledigt` — nicht an `/{id}`.
   *
   * Beide sind `DELETE` unter demselben Präfix; ein Tippfehler hier löschte statt aller
   * erledigten Meldungen einen einzelnen Eintrag oder gar nichts.
   */
  it('should delete all resolved entries via the literal path', () => {
    let anzahl: number | undefined;
    service.deleteErledigte().subscribe({ next: r => (anzahl = r.anzahl) });

    const req = httpMock.expectOne(`${apiUrl}/erledigt`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ anzahl: 4 });

    expect(anzahl).toBe(4);
  });
});
