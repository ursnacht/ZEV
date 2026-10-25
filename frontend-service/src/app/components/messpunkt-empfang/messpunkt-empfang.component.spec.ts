import { createSpyObj, SpyObj } from '../../../testing/spy';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import Keycloak from 'keycloak-js';
import { fakeAsync, tick } from '../../../testing/fake-async';
import {
  MesspunktEmpfangComponent, altersFarbe, altersText, ergebnisFarbe, formatierePayload
} from './messpunkt-empfang.component';
import { MesspunktEmpfangService } from '../../services/messpunkt-empfang.service';
import { TranslationService } from '../../services/translation.service';
import { MesspunktEmpfang } from '../../models/messpunkt-empfang.model';

describe('MesspunktEmpfangComponent', () => {
  let component: MesspunktEmpfangComponent;
  let fixture: ComponentFixture<MesspunktEmpfangComponent>;
  let serviceSpy: SpyObj<MesspunktEmpfangService>;
  let translationServiceSpy: SpyObj<TranslationService>;
  let routerSpy: SpyObj<Router>;

  /** Realm-Rollen des Test-Benutzers; die Komponente liest sie beim Erzeugen. */
  let rollen: string[];
  const keycloakMock = { hasRealmRole: (rolle: string) => rollen.includes(rolle) } as unknown as Keycloak;

  function zeile(ueberschreibungen: Partial<MesspunktEmpfang>): MesspunktEmpfang {
    return {
      id: 1, messpunkt: 'MP-001', einheiten: ['Wohnung 1'], quelle: 'PI_GATEWAY',
      ersterEmpfang: '2026-10-25T08:00:00', letzterEmpfang: '2026-10-25T09:59:30',
      sekundenSeitEmpfang: 30, anzahlMeldungen: 120, zaehlerstandBezug: 1234.5,
      zaehlerstandEinspeisung: 12, ergebnis: 'GESPEICHERT', grund: null, keinEmpfang: false,
      ...ueberschreibungen
    };
  }

  const mitEmpfang = zeile({});
  const ohneEinheit = zeile({ id: 2, messpunkt: 'MP-NEU', einheiten: [], ergebnis: 'UNBEKANNTER_MESSPUNKT' });
  const keinEmpfang = zeile({
    id: null, messpunkt: 'MP-STUMM', einheiten: ['Wohnung 2'], quelle: null, ersterEmpfang: null,
    letzterEmpfang: null, sekundenSeitEmpfang: null, anzahlMeldungen: null, zaehlerstandBezug: null,
    zaehlerstandEinspeisung: null, ergebnis: null, keinEmpfang: true
  });

  async function erzeuge(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [MesspunktEmpfangComponent],
      providers: [
        { provide: MesspunktEmpfangService, useValue: serviceSpy },
        { provide: TranslationService, useValue: translationServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: Keycloak, useValue: keycloakMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MesspunktEmpfangComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  const aktionen = (z: MesspunktEmpfang) => component.menuItemsFuer(component.zeilen.find(
    x => x.messpunkt === z.messpunkt)!).map(item => item.action);

  beforeEach(() => {
    rollen = ['einheit:read', 'einheit:write'];
    serviceSpy = createSpyObj<MesspunktEmpfangService>('MesspunktEmpfangService', [
      'getAllMesspunktEmpfang', 'getLetzteMeldung', 'deleteMesspunktEmpfang'
    ]);
    serviceSpy.getAllMesspunktEmpfang.mockReturnValue(of([mitEmpfang, ohneEinheit, keinEmpfang]));
    serviceSpy.getLetzteMeldung.mockReturnValue(of({ payload: '{"zaehlerstandBezug":1234.5}' }));
    translationServiceSpy = createSpyObj<TranslationService>('TranslationService', ['translate']);
    translationServiceSpy.translate.mockImplementation((key: string) => key);
    routerSpy = createSpyObj<Router>('Router', ['navigate']);
  });

  it('should create', async () => {
    await erzeuge();
    expect(component).toBeTruthy();
  });

  describe('Kebab-Menü (FR-2.8)', () => {
    it('should offer the last message for every row with reception, also without einheit:write', async () => {
      rollen = ['einheit:read'];
      await erzeuge();

      expect(aktionen(mitEmpfang)).toEqual(['letzte-meldung']);
      expect(aktionen(ohneEinheit)).toEqual(['letzte-meldung']);
    });

    it('should not offer the last message for a row without reception', async () => {
      await erzeuge();

      expect(aktionen(keinEmpfang)).not.toContain('letzte-meldung');
    });

    it('should hide create and delete without einheit:write', async () => {
      rollen = ['einheit:read'];
      await erzeuge();

      for (const z of [mitEmpfang, ohneEinheit, keinEmpfang]) {
        expect(aktionen(z)).not.toContain('einheit-anlegen');
        expect(aktionen(z)).not.toContain('delete');
      }
    });

    it('should offer delete only for rows with reception', async () => {
      await erzeuge();

      expect(aktionen(mitEmpfang)).toContain('delete');
      expect(aktionen(keinEmpfang)).not.toContain('delete');
    });

    it('should render no kebab button for a row without entries', async () => {
      rollen = ['einheit:read'];
      await erzeuge();

      const kebabs = fixture.nativeElement.querySelectorAll('app-kebab-menu');
      // Zwei Zeilen mit Empfang, die Kein-Empfang-Zeile hat keinen Eintrag
      expect(kebabs.length).toBe(2);
    });
  });

  describe('Spaltenreihenfolge (FR-2.3)', () => {
    it('should place first reception before last reception', async () => {
      await erzeuge();

      const koepfe = Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('thead th'))
        .map(th => th.textContent!.trim());
      expect(koepfe.findIndex(k => k.startsWith('MESSPUNKT_EMPFANG_ERSTER')))
        .toBe(koepfe.findIndex(k => k.startsWith('MESSPUNKT_EMPFANG_LETZTER')) - 1);
    });
  });

  describe('onLetzteMeldung (FR-2.9)', () => {
    it('should show the payload as indented JSON below the row', async () => {
      await erzeuge();

      component.onMenuAction('letzte-meldung', mitEmpfang);
      fixture.detectChanges();

      expect(serviceSpy.getLetzteMeldung).toHaveBeenCalledWith(1);
      const block: HTMLElement = fixture.nativeElement.querySelector('pre.zev-code-block');
      expect(block.textContent).toBe('{\n  "zaehlerstandBezug": 1234.5\n}');
      // Direkt unter der gewaehlten Zeile, nicht irgendwo in der Tabelle
      const zeilen = Array.from<HTMLTableRowElement>(fixture.nativeElement.querySelectorAll('tbody tr'));
      const gewaehlt = zeilen.findIndex(tr => tr.cells[0]?.textContent?.trim() === 'MP-001');
      expect(gewaehlt).toBeGreaterThanOrEqual(0);
      expect(zeilen[gewaehlt + 1].querySelector('pre.zev-code-block')).toBe(block);
    });

    it('should show text that is not JSON unchanged', async () => {
      serviceSpy.getLetzteMeldung.mockReturnValue(of({ payload: 'kein-json' }));
      await erzeuge();

      component.onLetzteMeldung(mitEmpfang);
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('pre.zev-code-block').textContent).toBe('kein-json');
    });

    it('should show a hint when no payload is stored', async () => {
      serviceSpy.getLetzteMeldung.mockReturnValue(of({ payload: null }));
      await erzeuge();

      component.onLetzteMeldung(mitEmpfang);
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('pre.zev-code-block')).toBeNull();
      expect(fixture.nativeElement.textContent).toContain('MESSPUNKT_EMPFANG_KEINE_MELDUNG');
    });

    it('should render markup in the payload as text, not as HTML', async () => {
      serviceSpy.getLetzteMeldung.mockReturnValue(of({ payload: '<b>x</b>' }));
      await erzeuge();

      component.onLetzteMeldung(mitEmpfang);
      fixture.detectChanges();

      const block: HTMLElement = fixture.nativeElement.querySelector('pre.zev-code-block');
      expect(block.querySelector('b')).toBeNull();
      expect(block.textContent).toBe('<b>x</b>');
    });

    it('should keep at most one message open', async () => {
      await erzeuge();

      component.onLetzteMeldung(mitEmpfang);
      component.onLetzteMeldung(ohneEinheit);
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelectorAll('pre.zev-code-block').length).toBe(1);
      expect(component.offeneMeldung?.messpunkt).toBe('MP-NEU');
    });

    it('should close the message with Schliessen', async () => {
      await erzeuge();
      component.onLetzteMeldung(mitEmpfang);
      fixture.detectChanges();

      const schliessen = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button'))
        .find(b => b.textContent!.includes('SCHLIESSEN'))!;
      schliessen.click();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('pre.zev-code-block')).toBeNull();
    });

    it('should close the message on Aktualisieren', async () => {
      await erzeuge();
      component.onLetzteMeldung(mitEmpfang);

      component.loadMesspunktEmpfang();
      fixture.detectChanges();

      expect(component.offeneMeldung).toBeNull();
      expect(fixture.nativeElement.querySelector('pre.zev-code-block')).toBeNull();
    });

    it('should show an error message when loading fails', async () => {
      serviceSpy.getLetzteMeldung.mockReturnValue(throwError(() => new Error('500')));
      await erzeuge();

      component.onLetzteMeldung(mitEmpfang);

      expect(component.message).toBe('FEHLER_BEIM_LADEN_DER_DATEN');
      expect(component.messageType).toBe('error');
      expect(component.offeneMeldung).toBeNull();
    });
  });

  describe('formatierePayload', () => {
    it('should indent valid JSON with two spaces', () => {
      expect(formatierePayload('{"a":1}')).toBe('{\n  "a": 1\n}');
    });

    it('should return other text unchanged', () => {
      expect(formatierePayload('Ei=0')).toBe('Ei=0');
    });

    it('should keep null as null', () => {
      expect(formatierePayload(null)).toBeNull();
    });
  });

  /** Zellen der gerenderten Zeile zum Messpunkt (ohne die aufgeklappte Meldungszeile). */
  const zellen = (messpunkt: string): HTMLTableCellElement[] => {
    const tr = Array.from<HTMLTableRowElement>(fixture.nativeElement.querySelectorAll('tbody tr'))
      .find(r => r.cells[0]?.textContent?.trim() === messpunkt)!;
    return Array.from(tr.cells);
  };
  /** Spalten wie im Template: Messpunkt, Einheiten, Quelle, Erster, Letzter, Bezug, Einspeisung, Anzahl, Ergebnis, Kebab */
  const SPALTE = { einheiten: 1, quelle: 2, erster: 3, letzter: 4, bezug: 5, einspeisung: 6, anzahl: 7, ergebnis: 8 };

  describe('altersFarbe (FR-2.5)', () => {
    it('should be success below 300 s', () => {
      expect(altersFarbe(0)).toBe('success');
      expect(altersFarbe(299)).toBe('success');
    });

    it('should be warning from 300 s up to and including 3600 s', () => {
      expect(altersFarbe(300)).toBe('warning');
      expect(altersFarbe(3600)).toBe('warning');
    });

    it('should be error above 3600 s', () => {
      expect(altersFarbe(3601)).toBe('error');
      expect(altersFarbe(5 * 86400)).toBe('error');
    });
  });

  describe('altersText (FR-2.5)', () => {
    it('should show rounded-down minutes up to and including 3600 s', () => {
      expect(altersText(0)).toEqual({ key: 'MESSPUNKT_EMPFANG_VOR_MINUTEN', wert: 0 });
      expect(altersText(59)).toEqual({ key: 'MESSPUNKT_EMPFANG_VOR_MINUTEN', wert: 0 });
      expect(altersText(299)).toEqual({ key: 'MESSPUNKT_EMPFANG_VOR_MINUTEN', wert: 4 });
      expect(altersText(300)).toEqual({ key: 'MESSPUNKT_EMPFANG_VOR_MINUTEN', wert: 5 });
      expect(altersText(3600)).toEqual({ key: 'MESSPUNKT_EMPFANG_VOR_MINUTEN', wert: 60 });
    });

    it('should show rounded-down hours from 3601 s up to below 48 hours', () => {
      expect(altersText(3601)).toEqual({ key: 'MESSPUNKT_EMPFANG_VOR_STUNDEN', wert: 1 });
      expect(altersText(2 * 3600 - 1)).toEqual({ key: 'MESSPUNKT_EMPFANG_VOR_STUNDEN', wert: 1 });
      expect(altersText(48 * 3600 - 1)).toEqual({ key: 'MESSPUNKT_EMPFANG_VOR_STUNDEN', wert: 47 });
    });

    it('should show rounded-down days from 48 hours', () => {
      expect(altersText(48 * 3600)).toEqual({ key: 'MESSPUNKT_EMPFANG_VOR_TAGEN', wert: 2 });
      expect(altersText(3 * 86400 - 1)).toEqual({ key: 'MESSPUNKT_EMPFANG_VOR_TAGEN', wert: 2 });
      expect(altersText(30 * 86400)).toEqual({ key: 'MESSPUNKT_EMPFANG_VOR_TAGEN', wert: 30 });
    });

    it('should insert the number into the translated text', async () => {
      translationServiceSpy.translate.mockImplementation((key: string) =>
        key === 'MESSPUNKT_EMPFANG_VOR_STUNDEN' ? 'vor {0} Std.' : key);
      await erzeuge();

      expect(component.alter(zeile({ sekundenSeitEmpfang: 3601 }))).toBe('vor 1 Std.');
    });
  });

  describe('Alters-Badge im Template (FR-2.5)', () => {
    it('should render the age badge with its colour for a row with reception', async () => {
      serviceSpy.getAllMesspunktEmpfang.mockReturnValue(of([
        zeile({ sekundenSeitEmpfang: 3600 })
      ]));
      await erzeuge();

      const badge: HTMLElement = zellen('MP-001')[SPALTE.letzter].querySelector('.zev-status')!;
      expect(badge.classList).toContain('zev-status--warning');
      expect(badge.textContent!.trim()).toBe('MESSPUNKT_EMPFANG_VOR_MINUTEN');
    });

    it('should render no age badge for a row without reception', async () => {
      await erzeuge();

      expect(zellen('MP-STUMM')[SPALTE.letzter].querySelector('.zev-status')).toBeNull();
    });
  });

  describe('ergebnisFarbe (FR-2.4)', () => {
    it('should map each result to its colour', () => {
      expect(ergebnisFarbe(zeile({ ergebnis: 'GESPEICHERT' }))).toBe('success');
      expect(ergebnisFarbe(zeile({ ergebnis: 'UNBEKANNTER_MESSPUNKT' }))).toBe('warning');
      expect(ergebnisFarbe(zeile({ ergebnis: 'UNGUELTIG', grund: 'Payload nicht lesbar' }))).toBe('error');
    });

    it('should be error for a row without reception', () => {
      expect(ergebnisFarbe(keinEmpfang)).toBe('error');
    });

    it('should render text and colour of the result badge', async () => {
      await erzeuge();

      const badge = (messpunkt: string): HTMLElement =>
        zellen(messpunkt)[SPALTE.ergebnis].querySelector('.zev-status')!;
      expect(badge('MP-001').textContent!.trim()).toBe('MESSPUNKT_EMPFANG_GESPEICHERT');
      expect(badge('MP-001').classList).toContain('zev-status--success');
      expect(badge('MP-NEU').textContent!.trim()).toBe('MESSPUNKT_EMPFANG_UNBEKANNTER_MESSPUNKT');
      expect(badge('MP-NEU').classList).toContain('zev-status--warning');
      expect(badge('MP-STUMM').textContent!.trim()).toBe('MESSPUNKT_EMPFANG_KEIN_EMPFANG');
      expect(badge('MP-STUMM').classList).toContain('zev-status--error');
    });

    it('should show the reason next to UNGUELTIG', async () => {
      serviceSpy.getAllMesspunktEmpfang.mockReturnValue(of([
        zeile({ ergebnis: 'UNGUELTIG', grund: 'Payload nicht lesbar' })
      ]));
      await erzeuge();

      const zelle = zellen('MP-001')[SPALTE.ergebnis];
      expect(zelle.querySelector('.zev-status')!.textContent!.trim()).toBe('MESSPUNKT_EMPFANG_UNGUELTIG');
      expect(zelle.querySelector('.zev-status')!.classList).toContain('zev-status--error');
      expect(zelle.textContent).toContain('Payload nicht lesbar');
    });
  });

  describe('Formatierung (FR-2.3)', () => {
    it('should show the last reception with seconds and the first without', async () => {
      await erzeuge();

      const letzter = zellen('MP-001')[SPALTE.letzter].textContent!.trim();
      expect(letzter.startsWith('25.10.2026 09:59:30')).toBe(true);
      expect(zellen('MP-001')[SPALTE.erster].textContent!.trim()).toBe('25.10.2026 08:00');
    });

    it('should show the meter readings with three decimals in Swiss format', async () => {
      await erzeuge();

      expect(zellen('MP-001')[SPALTE.bezug].textContent!.trim()).toBe('1\'234.500');
      expect(zellen('MP-001')[SPALTE.einspeisung].textContent!.trim()).toBe('12.000');
    });

    it('should show the source as translated text', async () => {
      translationServiceSpy.translate.mockImplementation((key: string) =>
        key === 'MESSPUNKT_EMPFANG_QUELLE_PI_GATEWAY' ? 'Pi-Gateway' : key);
      await erzeuge();

      expect(zellen('MP-001')[SPALTE.quelle].textContent!.trim()).toBe('Pi-Gateway');
    });

    it('should show a dash for missing source, times, readings, count and units', async () => {
      serviceSpy.getAllMesspunktEmpfang.mockReturnValue(of([
        keinEmpfang, zeile({ messpunkt: 'MP-OHNE', einheiten: [], zaehlerstandEinspeisung: null })
      ]));
      await erzeuge();

      const stumm = zellen('MP-STUMM');
      for (const spalte of [SPALTE.quelle, SPALTE.erster, SPALTE.letzter, SPALTE.bezug, SPALTE.einspeisung, SPALTE.anzahl]) {
        expect(stumm[spalte].textContent!.trim()).toBe('–');
      }
      expect(zellen('MP-OHNE')[SPALTE.einheiten].textContent!.trim()).toBe('–');
      expect(zellen('MP-OHNE')[SPALTE.einspeisung].textContent!.trim()).toBe('–');
    });

    it('should list all units of a meter point', async () => {
      serviceSpy.getAllMesspunktEmpfang.mockReturnValue(of([
        zeile({ einheiten: ['Allgemein', 'Wohnung 1'] })
      ]));
      await erzeuge();

      expect(zellen('MP-001')[SPALTE.einheiten].textContent!.trim()).toBe('Allgemein, Wohnung 1');
    });
  });

  describe('Sortierung (FR-2.6)', () => {
    const uebrigeAlt = zeile({ id: 10, messpunkt: 'MP-ALT', sekundenSeitEmpfang: 900 });
    const uebrigeNeu = zeile({ id: 11, messpunkt: 'MP-FRISCH', sekundenSeitEmpfang: 10 });
    const ohneEinheitAlt = zeile({
      id: 12, messpunkt: 'MP-X', einheiten: [], sekundenSeitEmpfang: 500, ergebnis: 'UNBEKANNTER_MESSPUNKT'
    });
    const ohneEinheitNeu = zeile({
      id: 13, messpunkt: 'MP-Y', einheiten: [], sekundenSeitEmpfang: 20, ergebnis: 'UNBEKANNTER_MESSPUNKT'
    });
    const keinEmpfangZ = { ...keinEmpfang, messpunkt: 'MP-Z' };
    const keinEmpfangA = { ...keinEmpfang, messpunkt: 'MP-A' };

    beforeEach(() => {
      serviceSpy.getAllMesspunktEmpfang.mockReturnValue(of([
        uebrigeAlt, keinEmpfangZ, ohneEinheitAlt, uebrigeNeu, keinEmpfangA, ohneEinheitNeu
      ]));
    });

    it('should sort without unit first, then no reception, then the others', async () => {
      await erzeuge();

      // Gruppen mit Empfang nach letztem Empfang absteigend, Kein Empfang nach Messpunkt aufsteigend
      expect(component.zeilen.map(z => z.messpunkt)).toEqual([
        'MP-Y', 'MP-X', 'MP-A', 'MP-Z', 'MP-FRISCH', 'MP-ALT'
      ]);
      expect(component.sortColumn).toBeNull();
    });

    it('should sort by column and toggle the direction on a second click', async () => {
      await erzeuge();

      component.onSort('messpunkt');
      expect(component.sortDirection).toBe('asc');
      expect(component.zeilen.map(z => z.messpunkt)).toEqual([
        'MP-A', 'MP-ALT', 'MP-FRISCH', 'MP-X', 'MP-Y', 'MP-Z'
      ]);

      component.onSort('messpunkt');
      expect(component.sortDirection).toBe('desc');
      expect(component.zeilen.map(z => z.messpunkt)).toEqual([
        'MP-Z', 'MP-Y', 'MP-X', 'MP-FRISCH', 'MP-ALT', 'MP-A'
      ]);
    });

    it('should sort when the column header is clicked', async () => {
      await erzeuge();

      const kopf = Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('thead th'))
        .find(th => th.textContent!.trim().startsWith('MESSPUNKT_EMPFANG_ANZAHL'))!;
      kopf.click();
      fixture.detectChanges();

      expect(component.sortColumn).toBe('anzahlMeldungen');
      expect(kopf.querySelector('.zev-table__sort-indicator')!.textContent).toBe('▲');
    });

    it('should put rows without a value at the end', async () => {
      await erzeuge();

      component.onSort('quelle');

      expect(component.zeilen.slice(-2).map(z => z.keinEmpfang)).toEqual([true, true]);
    });

    it('should sort the result with no reception as its own value', async () => {
      await erzeuge();

      component.onSort('ergebnis');

      // GESPEICHERT < KEIN_EMPFANG < UNBEKANNTER_MESSPUNKT
      expect(component.zeilen.map(z => component.ergebnisKey(z))).toEqual([
        'MESSPUNKT_EMPFANG_GESPEICHERT', 'MESSPUNKT_EMPFANG_GESPEICHERT',
        'MESSPUNKT_EMPFANG_KEIN_EMPFANG', 'MESSPUNKT_EMPFANG_KEIN_EMPFANG',
        'MESSPUNKT_EMPFANG_UNBEKANNTER_MESSPUNKT', 'MESSPUNKT_EMPFANG_UNBEKANNTER_MESSPUNKT'
      ]);
    });
  });

  describe('Laden (FR-2.7)', () => {
    it('should load the rows on init', async () => {
      await erzeuge();

      expect(serviceSpy.getAllMesspunktEmpfang).toHaveBeenCalledTimes(1);
      expect(component.zeilen.length).toBe(3);
    });

    it('should reload the list on Aktualisieren', async () => {
      await erzeuge();
      serviceSpy.getAllMesspunktEmpfang.mockReturnValue(of([mitEmpfang]));

      const knopf = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button'))
        .find(b => b.textContent!.includes('AKTUALISIEREN'))!;
      knopf.click();
      fixture.detectChanges();

      expect(serviceSpy.getAllMesspunktEmpfang).toHaveBeenCalledTimes(2);
      expect(component.zeilen.map(z => z.messpunkt)).toEqual(['MP-001']);
      expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(1);
    });

    it('should show the empty state without rows', async () => {
      serviceSpy.getAllMesspunktEmpfang.mockReturnValue(of([]));
      await erzeuge();

      expect(fixture.nativeElement.querySelector('table')).toBeNull();
      expect(fixture.nativeElement.textContent).toContain('MESSPUNKT_EMPFANG_KEINE_DATEN');
    });

    it('should not show the empty state when there are rows', async () => {
      await erzeuge();

      expect(fixture.nativeElement.textContent).not.toContain('MESSPUNKT_EMPFANG_KEINE_DATEN');
    });

    it('should show an error message when loading fails', async () => {
      serviceSpy.getAllMesspunktEmpfang.mockReturnValue(throwError(() => new Error('500')));
      await erzeuge();

      expect(component.message).toBe('FEHLER_BEIM_LADEN_DER_DATEN');
      expect(component.messageType).toBe('error');
      expect(fixture.nativeElement.querySelector('.zev-message--error').textContent)
        .toContain('FEHLER_BEIM_LADEN_DER_DATEN');
    });
  });

  describe('Einheit anlegen (FR-2.8)', () => {
    it('should offer creating a unit only for rows without unit', async () => {
      await erzeuge();

      expect(aktionen(ohneEinheit)).toContain('einheit-anlegen');
      expect(aktionen(mitEmpfang)).not.toContain('einheit-anlegen');
      expect(aktionen(keinEmpfang)).not.toContain('einheit-anlegen');
    });

    it('should offer creating a unit up to 50 characters but not beyond', async () => {
      const mit50 = zeile({ id: 20, messpunkt: 'M'.repeat(50), einheiten: [] });
      const mit51 = zeile({ id: 21, messpunkt: 'N'.repeat(51), einheiten: [] });
      serviceSpy.getAllMesspunktEmpfang.mockReturnValue(of([mit50, mit51]));
      await erzeuge();

      expect(aktionen(mit50)).toContain('einheit-anlegen');
      expect(aktionen(mit51)).not.toContain('einheit-anlegen');
      // Löschen bleibt auch beim zu langen Messpunkt möglich
      expect(aktionen(mit51)).toContain('delete');
    });

    it('should open the unit management with prefilled meter point', async () => {
      await erzeuge();

      component.onMenuAction('einheit-anlegen', ohneEinheit);

      expect(routerSpy.navigate).toHaveBeenCalledWith(['/einheiten'],
        { queryParams: { neu: 1, messpunkt: 'MP-NEU' } });
    });
  });

  describe('onDelete (FR-3)', () => {
    it('should delete after confirmation, show a success message and reload', async () => {
      await erzeuge();
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      serviceSpy.deleteMesspunktEmpfang.mockReturnValue(of(void 0));
      serviceSpy.getAllMesspunktEmpfang.mockReturnValue(of([ohneEinheit, keinEmpfang]));

      component.onMenuAction('delete', mitEmpfang);

      expect(window.confirm).toHaveBeenCalledWith('MESSPUNKT_EMPFANG_LOESCHEN_BESTAETIGEN');
      expect(serviceSpy.deleteMesspunktEmpfang).toHaveBeenCalledWith(1);
      expect(component.message).toBe('MESSPUNKT_EMPFANG_GELOESCHT');
      expect(component.messageType).toBe('success');
      expect(serviceSpy.getAllMesspunktEmpfang).toHaveBeenCalledTimes(2);
      expect(component.zeilen.map(z => z.messpunkt)).not.toContain('MP-001');
    });

    it('should not call delete when cancelled', async () => {
      await erzeuge();
      vi.spyOn(window, 'confirm').mockReturnValue(false);

      component.onDelete(mitEmpfang);

      expect(serviceSpy.deleteMesspunktEmpfang).not.toHaveBeenCalled();
      expect(component.zeilen.map(z => z.messpunkt)).toContain('MP-001');
    });

    it('should not ask for confirmation for a row without reception', async () => {
      await erzeuge();
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
      // Ohne restoreMocks bleibt der Spy aus vorherigen Tests bestehen, samt seinen Aufrufen
      confirmSpy.mockClear();

      component.onDelete(keinEmpfang);

      expect(confirmSpy).not.toHaveBeenCalled();
      expect(serviceSpy.deleteMesspunktEmpfang).not.toHaveBeenCalled();
    });

    it('should show an error message and keep the row when deleting fails', async () => {
      await erzeuge();
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      serviceSpy.deleteMesspunktEmpfang.mockReturnValue(throwError(() => ({ status: 500 })));

      component.onDelete(mitEmpfang);

      expect(component.message).toBe('MESSPUNKT_EMPFANG_FEHLER_LOESCHEN');
      expect(component.messageType).toBe('error');
      expect(serviceSpy.getAllMesspunktEmpfang).toHaveBeenCalledTimes(1);
      expect(component.zeilen.map(z => z.messpunkt)).toContain('MP-001');
    });

    it('should show the error key from the backend when there is one', async () => {
      await erzeuge();
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      serviceSpy.deleteMesspunktEmpfang.mockReturnValue(throwError(() => ({ error: { error: 'NICHT_GEFUNDEN' } })));

      component.onDelete(mitEmpfang);

      expect(component.message).toBe('NICHT_GEFUNDEN');
    });

    it('should auto-dismiss the success message after 5s', async () => {
      await erzeuge();
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      serviceSpy.deleteMesspunktEmpfang.mockReturnValue(of(void 0));

      fakeAsync(() => {
        component.onDelete(mitEmpfang);
        expect(component.message).toBe('MESSPUNKT_EMPFANG_GELOESCHT');
        tick(4999);
        expect(component.message).toBe('MESSPUNKT_EMPFANG_GELOESCHT');
        tick(1);
        expect(component.message).toBe('');
      })();
    });

    it('should keep the error message until dismissed', async () => {
      await erzeuge();
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      serviceSpy.deleteMesspunktEmpfang.mockReturnValue(throwError(() => ({ status: 500 })));

      fakeAsync(() => {
        component.onDelete(mitEmpfang);
        tick(10000);
        expect(component.message).toBe('MESSPUNKT_EMPFANG_FEHLER_LOESCHEN');
      })();
    });
  });
});
