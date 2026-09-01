import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { createSpyObj, SpyObj } from '../../../testing/spy';
import { fakeAsync, tick } from '../../../testing/fake-async';
import { NebenkostenAbrechnungComponent } from './nebenkosten-abrechnung.component';
import { NebenkostenService } from '../../services/nebenkosten.service';
import { TranslationService } from '../../services/translation.service';
import { NkAbrechnung, NkAbrechnungDetail, NkRechnungLauf } from '../../models/nebenkosten.model';

/**
 * Tests der Abrechnungsliste (Specs/Nebenkosten/Abrechnung.md, FR-1 und FR-7).
 *
 * <p>Schwerpunkt ist das Flag „abgerechnet": Es ist direkt in der Tabelle bedienbar und fragt
 * <b>nur beim Zurücksetzen</b> nach — das Abschliessen ist jederzeit umkehrbar, das Öffnen einer
 * abgeschlossenen Abrechnung nicht ohne Weiteres.
 */
describe('NebenkostenAbrechnungComponent', () => {
  let component: NebenkostenAbrechnungComponent;
  let fixture: ComponentFixture<NebenkostenAbrechnungComponent>;
  let nebenkostenServiceSpy: SpyObj<NebenkostenService>;

  const abrechnung2026: NkAbrechnung = {
    id: 1, bezeichnung: 'Nebenkosten 2026', datumVon: '2026-01-01', datumBis: '2026-12-31',
    anzahlWohnungen: 9, anzahlPersonen: 9, abgerechnet: false
  };

  const abrechnung2025: NkAbrechnung = {
    id: 2, bezeichnung: 'Abrechnung 2025', datumVon: '2025-01-01', datumBis: '2025-12-31',
    anzahlWohnungen: 9, anzahlPersonen: 9, abgerechnet: true
  };

  /**
   * Ergebnis eines Rechnungslaufs: eine Nachzahlung und ein Guthaben.
   *
   * Der Betrag der Nachzahlung ist vierstellig, damit die Schweizer Formatierung im DOM
   * nachweisbar ist — bei dreistelligen Zahlen fiele ein fehlendes Hochkomma nicht auf.
   */
  const lauf: NkRechnungLauf = {
    abrechnungId: 2,
    bezeichnung: 'Abrechnung 2025',
    von: '2025-01-01',
    bis: '2025-12-31',
    anzahlRechnungen: 2,
    anzahlForderungen: 1,
    summeForderungen: 1234.55,
    rechnungen: [
      {
        mieterId: 45, mieterName: 'Max Muster', saldo: 1234.55,
        forderungGebucht: true, filename: 'Nebenkosten_2025_Max_Muster.pdf', fehler: null
      },
      {
        mieterId: 46, mieterName: 'Erika Beispiel', saldo: -480,
        forderungGebucht: false, filename: 'Nebenkosten_2025_Erika_Beispiel.pdf', fehler: null
      }
    ]
  };

  /**
   * Zahl der ueberlagernden Meldungen (Erfolg/Fehler). Sie sind fixiert positioniert und liegen
   * alle an derselben Stelle - mehr als eine ist immer ein Fehler.
   */
  function ueberlagerndeMeldungen(): number {
    return fixture.nativeElement
      .querySelectorAll('.zev-message--success, .zev-message--error').length;
  }

  /** Klick auf die Inline-Checkbox einer Zeile. */
  function toggle(abrechnung: NkAbrechnung, ziel: boolean): void {
    component.onToggleAbgerechnet(abrechnung, { target: { checked: ziel } } as unknown as Event);
  }

  beforeEach(async () => {
    nebenkostenServiceSpy = createSpyObj<NebenkostenService>('NebenkostenService', [
      'getAllAbrechnungen', 'getAbrechnungDetail', 'getVorlage',
      'createAbrechnung', 'updateAbrechnung', 'setAbgerechnet', 'deleteAbrechnung',
      'erzeugeRechnungen', 'ladeRechnungPdf', 'kopiereAbrechnung'
    ]);
    nebenkostenServiceSpy.erzeugeRechnungen.mockReturnValue(of(lauf));
    nebenkostenServiceSpy.ladeRechnungPdf.mockReturnValue(of(new Blob(['%PDF'])));
    nebenkostenServiceSpy.getAllAbrechnungen.mockReturnValue(of([abrechnung2026, abrechnung2025]));
    nebenkostenServiceSpy.setAbgerechnet.mockReturnValue(of(abrechnung2026));
    nebenkostenServiceSpy.deleteAbrechnung.mockReturnValue(of(void 0));
    // Wird gebraucht, sobald die Maske gerendert wird: Sie ist eine echte Kindkomponente und
    // laedt in ngOnInit ihre Vorlage.
    nebenkostenServiceSpy.getVorlage.mockReturnValue(of({
      abrechnung: { ...abrechnung2026, id: undefined },
      positionen: [], zusaetze: [], akonto: [], personen: [],
      anzahlWohnungenVorschlag: 9, anzahlPersonenVorschlag: 9
    }));
    nebenkostenServiceSpy.getAbrechnungDetail.mockReturnValue(of({
      abrechnung: abrechnung2026,
      positionen: [], zusaetze: [], akonto: [], personen: [],
      anzahlWohnungenVorschlag: 9, anzahlPersonenVorschlag: 9
    }));

    await TestBed.configureTestingModule({
      imports: [NebenkostenAbrechnungComponent],
      providers: [
        { provide: NebenkostenService, useValue: nebenkostenServiceSpy },
        { provide: TranslationService, useValue: { translate: (k: string) => k } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(NebenkostenAbrechnungComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should load the billings on init', () => {
      expect(nebenkostenServiceSpy.getAllAbrechnungen).toHaveBeenCalled();
      expect(component.abrechnungen.length).toBe(2);
    });

    it('should show an error message when loading fails', () => {
      nebenkostenServiceSpy.getAllAbrechnungen.mockReturnValue(throwError(() => ({ status: 500 })));

      component.loadAbrechnungen();

      expect(component.message).toBe('NK_FEHLER_LADEN');
      expect(component.messageType).toBe('error');
    });

    it('should show the empty state without billings', () => {
      nebenkostenServiceSpy.getAllAbrechnungen.mockReturnValue(of([]));

      component.loadAbrechnungen();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.zev-table')).toBeNull();
      expect(fixture.nativeElement.textContent).toContain('NK_KEINE_ABRECHNUNGEN');
    });

    it('should offer the create button above the table, also when empty', () => {
      // Wie auf den uebrigen Listenseiten - die Stelle haengt nicht an der Laenge der Liste.
      nebenkostenServiceSpy.getAllAbrechnungen.mockReturnValue(of([]));
      component.loadAbrechnungen();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.zev-button-row .zev-button--primary'))
        .not.toBeNull();
    });
  });

  describe('abgerechnet umschalten', () => {
    it('should close a billing without asking back', () => {
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);

      toggle(abrechnung2026, true);

      expect(confirmSpy).not.toHaveBeenCalled();
      expect(nebenkostenServiceSpy.setAbgerechnet).toHaveBeenCalledWith(1, true);
      expect(component.message).toBe('NK_ABGERECHNET_GESETZT');
    });

    it('should ask back before reopening a closed billing', () => {
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);

      toggle(abrechnung2025, false);

      expect(confirmSpy).toHaveBeenCalled();
      expect(nebenkostenServiceSpy.setAbgerechnet).toHaveBeenCalledWith(2, false);
      expect(component.message).toBe('NK_ABGERECHNET_GELOEST');
    });

    it('should reload the list when reopening is declined', () => {
      // Sonst bliebe die Checkbox abgehakt stehen, obwohl nichts gespeichert wurde.
      vi.spyOn(window, 'confirm').mockReturnValue(false);
      nebenkostenServiceSpy.getAllAbrechnungen.mockClear();

      toggle(abrechnung2025, false);

      expect(nebenkostenServiceSpy.setAbgerechnet).not.toHaveBeenCalled();
      expect(nebenkostenServiceSpy.getAllAbrechnungen).toHaveBeenCalled();
    });

    it('should reload the list when the server rejects the change', () => {
      nebenkostenServiceSpy.setAbgerechnet.mockReturnValue(
        throwError(() => ({ error: 'NK_FEHLER_ABGERECHNET' })));
      nebenkostenServiceSpy.getAllAbrechnungen.mockClear();

      toggle(abrechnung2026, true);

      expect(component.message).toBe('NK_FEHLER_ABGERECHNET');
      expect(component.messageType).toBe('error');
      expect(nebenkostenServiceSpy.getAllAbrechnungen).toHaveBeenCalled();
    });
  });

  describe('onDelete', () => {
    it('should delete after confirmation and reload', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);

      component.onDelete(1);

      expect(nebenkostenServiceSpy.deleteAbrechnung).toHaveBeenCalledWith(1);
      expect(component.message).toBe('NK_ABRECHNUNG_GELOESCHT');
    });

    it('should not delete when the confirmation is declined', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(false);

      component.onDelete(1);

      expect(nebenkostenServiceSpy.deleteAbrechnung).not.toHaveBeenCalled();
    });

    it('should show the message of the server when deletion fails', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      nebenkostenServiceSpy.deleteAbrechnung.mockReturnValue(
        throwError(() => ({ error: 'NK_FEHLER_LOESCHEN' })));

      component.onDelete(1);

      expect(component.message).toBe('NK_FEHLER_LOESCHEN');
      expect(component.messageType).toBe('error');
    });
  });

  describe('Formular', () => {
    it('should open the form without an id for a new billing', () => {
      component.onCreateNew();

      expect(component.showForm).toBe(true);
      expect(component.selectedId).toBeNull();
    });

    it('should open the form with the id when editing', () => {
      component.onMenuAction('edit', abrechnung2026);

      expect(component.showForm).toBe(true);
      expect(component.selectedId).toBe(1);
    });

    it('should not show a message of its own while the form is open', () => {
      // Beide Meldungen sind fixiert positioniert und laegen exakt uebereinander: Die untere
      // waere verdeckt und nicht mehr wegklickbar. Die Maske meldet selbst - die Liste schweigt.
      component.onCreateNew();
      fixture.detectChanges();

      component.onFormSaved();
      fixture.detectChanges();

      expect(component.message).toBe('');
      // Gezaehlt werden nur die ueberlagernden Meldungen: Der statische Hinweis der Maske
      // ("Bloecke erscheinen nach dem Speichern") steht im Textfluss und stoert nicht.
      expect(ueberlagerndeMeldungen()).toBe(0);
    });

    it('should reload the list after the form has saved', () => {
      component.onCreateNew();
      nebenkostenServiceSpy.getAllAbrechnungen.mockClear();

      component.onFormSaved();

      expect(nebenkostenServiceSpy.getAllAbrechnungen).toHaveBeenCalled();
    });

    it('should hide a pending message when the form opens', () => {
      // Eine noch stehende Meldung der Liste wuerde sonst die Maske ueberlagern.
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      component.onDelete(1);
      expect(component.message).not.toBe('');

      component.onCreateNew();
      fixture.detectChanges();

      expect(ueberlagerndeMeldungen()).toBe(0);
    });

    it('should close the form and reload on close', () => {
      component.onCreateNew();
      nebenkostenServiceSpy.getAllAbrechnungen.mockClear();

      component.onFormClose();

      expect(component.showForm).toBe(false);
      expect(component.selectedId).toBeNull();
      expect(nebenkostenServiceSpy.getAllAbrechnungen).toHaveBeenCalled();
    });
  });

  describe('onSort', () => {
    it('should sort by date descending on load', () => {
      expect(component.sortColumn).toBe('datumVon');
      expect(component.sortDirection).toBe('desc');
    });

    it('should sort by the chosen column', () => {
      component.onSort('bezeichnung');

      expect(component.abrechnungen[0].bezeichnung).toBe('Abrechnung 2025');
    });

    it('should toggle the direction on the same column', () => {
      component.onSort('bezeichnung');
      component.onSort('bezeichnung');

      expect(component.sortDirection).toBe('desc');
      expect(component.abrechnungen[0].bezeichnung).toBe('Nebenkosten 2026');
    });
  });

  describe('messages', () => {
    it('should auto-dismiss a success message after 5s', fakeAsync(() => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      component.onDelete(1);
      expect(component.message).not.toBe('');

      tick(5000);

      expect(component.message).toBe('');
    }));

    it('should keep an error message until it is dismissed', fakeAsync(() => {
      nebenkostenServiceSpy.getAllAbrechnungen.mockReturnValue(throwError(() => ({ status: 500 })));
      component.loadAbrechnungen();

      tick(5000);

      expect(component.message).toBe('NK_FEHLER_LADEN');
      component.dismissMessage();
      expect(component.message).toBe('');
    }));
  });

  // ============ Rechnungen erstellen (Specs/Nebenkosten/RechnungenGenerieren.md) ============

  describe('menuFuer', () => {
    it('should offer "Rechnungen erstellen" only on a closed billing', () => {
      expect(component.menuFuer(abrechnung2025).map(i => i.action)).toContain('rechnungen');
      expect(component.menuFuer(abrechnung2026).map(i => i.action)).not.toContain('rechnungen');
    });

    it('should keep the other entries on an open billing', () => {
      expect(component.menuFuer(abrechnung2026).map(i => i.action))
        .toEqual(['edit', 'kopieren', 'delete']);
    });

    it('should put the dangerous entry last', () => {
      expect(component.menuFuer(abrechnung2025).map(i => i.action))
        .toEqual(['edit', 'kopieren', 'rechnungen', 'delete']);
    });

    /**
     * Dieselbe Zeile liefert **dasselbe Objekt**.
     *
     * Das Kebab-Menü verfolgt seine Einträge über die Objektidentität (`track item`). Eine je
     * Änderungserkennung neu gebaute Liste liesse Angular das Menü in jedem Zyklus neu aufbauen —
     * genau das NG0956, das die Abrechnungsmaske schon einmal gekostet hat.
     */
    it('should return a stable array for repeated calls', () => {
      expect(component.menuFuer(abrechnung2025)).toBe(component.menuFuer(abrechnung2025));
      expect(component.menuFuer(abrechnung2026)).toBe(component.menuFuer(abrechnung2026));
    });

    it('should not share the array between open and closed', () => {
      expect(component.menuFuer(abrechnung2025)).not.toBe(component.menuFuer(abrechnung2026));
    });
  });

  describe('onRechnungenErstellen', () => {
    it('should ask back before creating invoices', () => {
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);

      component.onRechnungenErstellen(abrechnung2025);

      expect(confirmSpy).toHaveBeenCalledWith('NK_CONFIRM_RECHNUNGEN_ERSTELLEN');
      expect(nebenkostenServiceSpy.erzeugeRechnungen).not.toHaveBeenCalled();
    });

    it('should create the invoices after confirmation', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);

      component.onRechnungenErstellen(abrechnung2025);

      expect(nebenkostenServiceSpy.erzeugeRechnungen).toHaveBeenCalledWith(2);
      expect(component.lauf).toEqual(lauf);
      expect(component.laufLaeuft).toBe(false);
    });

    it('should do nothing without an id', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);

      component.onRechnungenErstellen({ ...abrechnung2025, id: undefined });

      expect(nebenkostenServiceSpy.erzeugeRechnungen).not.toHaveBeenCalled();
    });

    it('should mark the run as running until the answer arrives', () => {
      const antwort = new Subject<NkRechnungLauf>();
      nebenkostenServiceSpy.erzeugeRechnungen.mockReturnValue(antwort);
      vi.spyOn(window, 'confirm').mockReturnValue(true);

      component.onRechnungenErstellen(abrechnung2025);
      expect(component.laufLaeuft).toBe(true);

      antwort.next(lauf);
      expect(component.laufLaeuft).toBe(false);
    });

    it('should ignore a second run while one is running', () => {
      nebenkostenServiceSpy.erzeugeRechnungen.mockReturnValue(new Subject<NkRechnungLauf>());
      vi.spyOn(window, 'confirm').mockReturnValue(true);

      component.onRechnungenErstellen(abrechnung2025);
      component.onRechnungenErstellen(abrechnung2025);

      expect(nebenkostenServiceSpy.erzeugeRechnungen).toHaveBeenCalledTimes(1);
    });

    it('should show the message of the server on failure', () => {
      nebenkostenServiceSpy.erzeugeRechnungen.mockReturnValue(
        throwError(() => ({ error: { error: 'NK_FEHLER_NICHT_ABGERECHNET' } })));
      vi.spyOn(window, 'confirm').mockReturnValue(true);

      component.onRechnungenErstellen(abrechnung2025);

      expect(component.message).toBe('NK_FEHLER_NICHT_ABGERECHNET');
      expect(component.messageType).toBe('error');
      expect(component.laufLaeuft).toBe(false);
    });

    it('should fall back to a generic message without a server text', () => {
      nebenkostenServiceSpy.erzeugeRechnungen.mockReturnValue(throwError(() => ({ status: 500 })));
      vi.spyOn(window, 'confirm').mockReturnValue(true);

      component.onRechnungenErstellen(abrechnung2025);

      expect(component.message).toBe('NK_FEHLER_RECHNUNGEN_ERSTELLEN');
    });

    /** Ein halb ersetztes Ergebnis waere schlimmer als ein altes. */
    it('should keep an existing result when the next run fails', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      component.onRechnungenErstellen(abrechnung2025);

      nebenkostenServiceSpy.erzeugeRechnungen.mockReturnValue(throwError(() => ({ status: 500 })));
      component.onRechnungenErstellen(abrechnung2025);

      expect(component.lauf).toEqual(lauf);
    });
  });

  describe('Ergebnis-Panel', () => {
    function panel(): HTMLElement | null {
      return fixture.nativeElement.querySelector('.zev-panel');
    }

    function zeilen(): HTMLElement[] {
      return Array.from(fixture.nativeElement.querySelectorAll('.zev-panel tbody tr'));
    }

    beforeEach(() => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      component.onRechnungenErstellen(abrechnung2025);
      fixture.detectChanges();
    });

    it('should render one row per invoice', () => {
      expect(panel()).toBeTruthy();
      expect(zeilen().length).toBe(2);
    });

    it('should not render before a run', () => {
      component.lauf = null;
      fixture.detectChanges();

      expect(panel()).toBeNull();
    });

    it('should name the counts separately', () => {
      // Bei durchweg Guthaben entstehen Rechnungen, aber keine Forderungen - "0" ist dann kein
      // Fehlschlag, und das muss am Panel ablesbar sein.
      const text = panel()!.textContent!;
      expect(text).toContain('NK_ANZAHL_RECHNUNGEN');
      expect(text).toContain('NK_ANZAHL_FORDERUNGEN');
      expect(text).toContain('NK_SUMME_FORDERUNGEN');
    });

    it('should format amounts in Swiss notation', () => {
      // Punkt als Dezimaltrenner, ASCII-Hochkomma als Tausendertrenner (Specs/generell.md).
      expect(zeilen()[0].textContent).toContain("1'234.55");
    });

    it('should name the sign in words', () => {
      expect(zeilen()[0].textContent).toContain('NK_NACHZAHLUNG');
      expect(zeilen()[1].textContent).toContain('NK_GUTHABEN');
    });

    it('should mark a booked receivable and a credit differently', () => {
      expect(zeilen()[0].querySelector('.zev-status--success')).toBeTruthy();
      expect(zeilen()[1].textContent).toContain('NK_KEINE_FORDERUNG');
      expect(zeilen()[1].querySelector('.zev-status--success')).toBeNull();
    });

    it('should offer a download per row', () => {
      expect(zeilen()[0].querySelector('button')).toBeTruthy();
      expect(zeilen()[1].querySelector('button')).toBeTruthy();
    });

    it('should show the error and no download for a failed tenant', () => {
      component.lauf = {
        ...lauf,
        rechnungen: [{ ...lauf.rechnungen[0], filename: null, fehler: 'NK_FEHLER_RECHNUNG_MIETER' }]
      };
      fixture.detectChanges();

      expect(zeilen()[0].textContent).toContain('NK_FEHLER_RECHNUNG_MIETER');
      expect(zeilen()[0].querySelector('button')).toBeNull();
    });

    /** Ein Ergebnis zu einem Stand, den die Tabelle darueber nicht mehr zeigt, ist irrefuehrend. */
    it('should disappear when the list is reloaded', () => {
      component.loadAbrechnungen();

      expect(component.lauf).toBeNull();
    });

    it('should disappear when the form opens', () => {
      component.onCreateNew();
      expect(component.lauf).toBeNull();

      component.onRechnungenErstellen(abrechnung2025);
      component.onEdit(abrechnung2025);
      expect(component.lauf).toBeNull();
    });
  });

  describe('onRechnungHerunterladen', () => {
    beforeEach(() => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      component.onRechnungenErstellen(abrechnung2025);
    });

    it('should load the pdf of the chosen tenant', () => {
      component.onRechnungHerunterladen(45, 'Nebenkosten_2025_Max_Muster.pdf');

      expect(nebenkostenServiceSpy.ladeRechnungPdf).toHaveBeenCalledWith(2, 45);
    });

    /**
     * Der Klick wird am **Prototyp** abgefangen, nicht durch Ersetzen von
     * `document.createElement`.
     *
     * Ein Stub auf der DOM-Fabrik liefert Angulars eigenem Rendering denselben Knoten zurück und
     * bricht mit `HierarchyRequestError` — nicht in diesem Test, sondern in den nachfolgenden.
     */
    function fangeDownloadAb(): { name: () => string; klicks: () => number } {
      const namen: string[] = [];
      vi.spyOn(HTMLAnchorElement.prototype, 'click')
        .mockImplementation(function (this: HTMLAnchorElement) {
          namen.push(this.download);
        });
      return { name: () => namen[0], klicks: () => namen.length };
    }

    it('should save the blob under the given filename', () => {
      const download = fangeDownloadAb();
      const objectUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:pdf');
      const freigabe = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});

      component.onRechnungHerunterladen(45, 'Nebenkosten_2025_Max_Muster.pdf');

      expect(objectUrl).toHaveBeenCalled();
      expect(download.klicks()).toBe(1);
      expect(download.name()).toBe('Nebenkosten_2025_Max_Muster.pdf');
      // Ohne Freigabe haelt der Browser das Blob bis zum Verlassen der Seite.
      expect(freigabe).toHaveBeenCalledWith('blob:pdf');
    });

    it('should use a fallback name when none is given', () => {
      const download = fangeDownloadAb();
      vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:pdf');

      component.onRechnungHerunterladen(45, null);

      expect(download.name()).toBe('nebenkosten.pdf');
    });

    /**
     * Nach 30 Minuten ist die Ablage leer. Das ist kein Fehler, sondern ein Hinweis: Der Lauf
     * laesst sich wiederholen und ergibt dasselbe PDF.
     */
    it('should show a hint when the pdf has expired', () => {
      nebenkostenServiceSpy.ladeRechnungPdf.mockReturnValue(throwError(() => ({ status: 404 })));

      component.onRechnungHerunterladen(45, 'egal.pdf');

      expect(component.message).toBe('NK_RECHNUNG_ABGELAUFEN');
      expect(component.messageType).toBe('error');
    });

    it('should do nothing without a result', () => {
      component.lauf = null;

      component.onRechnungHerunterladen(45, 'egal.pdf');

      expect(nebenkostenServiceSpy.ladeRechnungPdf).not.toHaveBeenCalled();
    });
  });

  describe('Kopieren', () => {

    /** Antwort des Kopier-Endpunkts: die neue Abrechnung samt leeren Listen. */
    function detailMit(id: number): NkAbrechnungDetail {
      return {
        abrechnung: { ...abrechnung2026, id },
        positionen: [], zusaetze: [], akonto: [], personen: [],
        anzahlWohnungenVorschlag: 9, anzahlPersonenVorschlag: 9
      };
    }

    it('should offer copying on an open billing', () => {
      const aktionen = component.menuFuer(abrechnung2026).map(e => e.action);
      expect(aktionen).toContain('kopieren');
    });

    it('should offer copying on a closed billing too', () => {
      // Genau die ist der typische Ausgangspunkt fuer die naechste Abrechnung.
      const aktionen = component.menuFuer(abrechnung2025).map(e => e.action);
      expect(aktionen).toContain('kopieren');
    });

    it('should copy and open the copy', () => {
      nebenkostenServiceSpy.kopiereAbrechnung.mockReturnValue(of(detailMit(4711)));

      component.onKopieren(abrechnung2026);

      expect(component.selectedId).toBe(4711);
      expect(component.showForm).toBe(true);
      expect(component.messageType).toBe('success');
    });

    it('should append the copy suffix to the description', () => {
      nebenkostenServiceSpy.kopiereAbrechnung.mockReturnValue(of(detailMit(4711)));

      component.onKopieren(abrechnung2026);

      expect(nebenkostenServiceSpy.kopiereAbrechnung).toHaveBeenCalledWith(
        abrechnung2026.id, `${abrechnung2026.bezeichnung} NK_KOPIE_SUFFIX`);
    });

    it('should keep the description within the column width', () => {
      // nk_abrechnung.bezeichnung ist VARCHAR(150). Gekuerzt wird der NAME, nicht der Zusatz -
      // sonst stuende am Ende ein abgeschnittenes "(Kop" und die Kopie waere nicht erkennbar.
      nebenkostenServiceSpy.kopiereAbrechnung.mockReturnValue(of(detailMit(4711)));

      component.onKopieren({ ...abrechnung2026, bezeichnung: 'x'.repeat(200) });

      const gesendet = nebenkostenServiceSpy.kopiereAbrechnung.mock.calls[0][1] as string;
      expect(gesendet.length).toBeLessThanOrEqual(150);
      expect(gesendet.endsWith('NK_KOPIE_SUFFIX')).toBe(true);
    });

    it('should report a failure and stay in the list', () => {
      nebenkostenServiceSpy.kopiereAbrechnung.mockReturnValue(
        throwError(() => ({ error: 'NK_FEHLER_KOPIEREN' })));

      component.onKopieren(abrechnung2026);

      expect(component.messageType).toBe('error');
      expect(component.showForm).toBe(false);
    });

    it('should do nothing without an id', () => {
      component.onKopieren({ ...abrechnung2026, id: undefined });
      expect(nebenkostenServiceSpy.kopiereAbrechnung).not.toHaveBeenCalled();
    });
  });
});
