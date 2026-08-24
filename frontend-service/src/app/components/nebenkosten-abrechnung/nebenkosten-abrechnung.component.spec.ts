import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { createSpyObj, SpyObj } from '../../../testing/spy';
import { fakeAsync, tick } from '../../../testing/fake-async';
import { NebenkostenAbrechnungComponent } from './nebenkosten-abrechnung.component';
import { NebenkostenService } from '../../services/nebenkosten.service';
import { TranslationService } from '../../services/translation.service';
import { NkAbrechnung } from '../../models/nebenkosten.model';

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
    anzahlWohnungen: 9, abgerechnet: false
  };

  const abrechnung2025: NkAbrechnung = {
    id: 2, bezeichnung: 'Abrechnung 2025', datumVon: '2025-01-01', datumBis: '2025-12-31',
    anzahlWohnungen: 9, abgerechnet: true
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
      'createAbrechnung', 'updateAbrechnung', 'setAbgerechnet', 'deleteAbrechnung'
    ]);
    nebenkostenServiceSpy.getAllAbrechnungen.mockReturnValue(of([abrechnung2026, abrechnung2025]));
    nebenkostenServiceSpy.setAbgerechnet.mockReturnValue(of(abrechnung2026));
    nebenkostenServiceSpy.deleteAbrechnung.mockReturnValue(of(void 0));
    // Wird gebraucht, sobald die Maske gerendert wird: Sie ist eine echte Kindkomponente und
    // laedt in ngOnInit ihre Vorlage.
    nebenkostenServiceSpy.getVorlage.mockReturnValue(of({
      abrechnung: { ...abrechnung2026, id: undefined },
      positionen: [], zusaetze: [], akonto: [], anzahlWohnungenVorschlag: 9
    }));
    nebenkostenServiceSpy.getAbrechnungDetail.mockReturnValue(of({
      abrechnung: abrechnung2026,
      positionen: [], zusaetze: [], akonto: [], anzahlWohnungenVorschlag: 9
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
});
