import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { createSpyObj, SpyObj } from '../../../testing/spy';
import { fakeAsync, tick } from '../../../testing/fake-async';
import { NebenkostenAbrechnungFormComponent } from './nebenkosten-abrechnung-form.component';
import { NebenkostenService } from '../../services/nebenkosten.service';
import { TranslationService } from '../../services/translation.service';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import {
  NkAbrechnungDetail,
  NkPosition,
  NkPositionsart,
  NkUmlageInfo,
  NkZeile,
  leerePosition
} from '../../models/nebenkosten.model';
// `Mengeneinheit` lebt im Tarif-Modell und wird von der Nebenkostenabrechnung mitbenutzt.
import { Mengeneinheit } from '../../models/tarif.model';

/**
 * Tests der Bearbeitungsmaske (Specs/Nebenkosten/Abrechnung.md, FR-7).
 *
 * Die Testdaten sind bewusst so geformt, wie sie **vom Server** kommen: nicht gesetzte Felder
 * als `null`, nicht als `undefined`. Genau daran ist die Bedienbarkeit der Mengenfelder schon
 * einmal gescheitert — eine Prüfung auf `undefined` sah `null` und sperrte jede Zeile.
 */
describe('NebenkostenAbrechnungFormComponent', () => {
  let component: NebenkostenAbrechnungFormComponent;
  let fixture: ComponentFixture<NebenkostenAbrechnungFormComponent>;
  let nebenkostenServiceSpy: SpyObj<NebenkostenService>;

  /** Antwort von `GET /{id}`, so wie Jackson sie schickt. */
  const serverDetail = {
    abrechnung: {
      id: 1,
      bezeichnung: 'Nebenkosten 2026',
      datumVon: '2026-01-01',
      datumBis: '2026-12-31',
      anzahlWohnungen: 2,
      anzahlPersonen: 2,
      abgerechnet: false,
      erstelltAm: '2026-01-05T10:00:00'
    },
    positionen: [
      {
        id: 10,
        art: 'UMLAGE',
        bezeichnung: 'Allgemeinstrom',
        reihenfolge: 1,
        einheit: 'KWH',
        totalbetrag: 900,
        gesamtmenge: null,
        betragProEinheit: null,
        prozentsatz: null,
        verbraeuche: []
      },
      {
        id: 11,
        art: 'VERBRAUCH',
        bezeichnung: 'Warmwasser',
        reihenfolge: 2,
        einheit: 'M3',
        totalbetrag: null,
        gesamtmenge: null,
        betragProEinheit: 4.5,
        prozentsatz: null,
        verbraeuche: [{ mieterId: 100, menge: 12 }]
      }
    ],
    zusaetze: [],
    akonto: [{ id: 5, mieterId: 100, anzahlMonate: 12, betragProMonat: 100, korrektur: 0 }],
    personen: [],
    anzahlWohnungenVorschlag: 2,
    anzahlPersonenVorschlag: 2,
    berechnung: {
      nenner: 730,
      summeTage: 365,
      nennerPerson: 730,
      summePersonenTage: 365,
      umlagen: [{
        positionId: 10,
        bezeichnung: 'Allgemeinstrom',
        totalbetrag: 900,
        summeVerteilt: 450,
        nichtVerteilt: 450,
        rundungsdifferenz: 0
      }],
      mieter: [{
        mieterId: 100,
        name: 'Anna Beispiel',
        tage: 365,
        anzahlPersonen: 1,
        personenTage: 365,
        ohneWohnung: false,
        kostentotal: 504,
        akontoAnzahlMonate: 12,
        akontoBetragProMonat: 100,
        akontoKorrektur: 0,
        akontoTotal: 1200,
        saldo: -696,
        zeilen: [
          {
            positionId: 10,
            zusatzId: null,
            art: 'UMLAGE',
            reihenfolge: 1,
            bezeichnung: 'Allgemeinstrom',
            einheit: 'KWH',
            menge: null,
            betragProEinheit: null,
            prozentsatz: null,
            betrag: 450
          },
          {
            positionId: 11,
            zusatzId: null,
            art: 'VERBRAUCH',
            reihenfolge: 2,
            bezeichnung: 'Warmwasser',
            einheit: 'M3',
            menge: 12,
            betragProEinheit: 4.5,
            prozentsatz: null,
            betrag: 54
          }
        ]
      }]
    }
  } as unknown as NkAbrechnungDetail;

  /**
   * Öffnet den Block des Mieters — beim Laden sind alle geschlossen.
   *
   * `whenStable`, weil `ngModel` den Anfangswert erst in einem Microtask ins Feld schreibt; ohne
   * das Warten stünde im DOM noch der leere Ausgangswert.
   */
  async function oeffneMieterblock(mieterId = 100): Promise<void> {
    component.toggleMieter(mieterId);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function mengenFelder(): HTMLInputElement[] {
    return Array.from(
      fixture.nativeElement.querySelectorAll('.nk-mieterzeilen input[type="number"]'));
  }

  beforeEach(async () => {
    nebenkostenServiceSpy = createSpyObj<NebenkostenService>('NebenkostenService', [
      'getAllAbrechnungen', 'getAbrechnungDetail', 'getVorlage',
      'createAbrechnung', 'updateAbrechnung', 'setAbgerechnet', 'deleteAbrechnung'
    ]);
    nebenkostenServiceSpy.getAbrechnungDetail.mockReturnValue(of(structuredClone(serverDetail)));
    nebenkostenServiceSpy.getVorlage.mockReturnValue(of(structuredClone(serverDetail)));
    nebenkostenServiceSpy.updateAbrechnung.mockReturnValue(of(structuredClone(serverDetail)));

    await TestBed.configureTestingModule({
      imports: [NebenkostenAbrechnungFormComponent],
      providers: [
        { provide: NebenkostenService, useValue: nebenkostenServiceSpy },
        { provide: TranslationService, useValue: { translate: (k: string) => k } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(NebenkostenAbrechnungFormComponent);
    component = fixture.componentInstance;
    component.abrechnungId = 1;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should load the detail of the given billing', () => {
      expect(nebenkostenServiceSpy.getAbrechnungDetail).toHaveBeenCalledWith(1);
      expect(component.kopf.bezeichnung).toBe('Nebenkosten 2026');
      expect(component.positionen.length).toBe(2);
    });

    it('should keep all tenant blocks collapsed', () => {
      expect(component.istMieterOffen(100)).toBe(false);
      expect(fixture.nativeElement.querySelector('.zev-collapsible__content')).toBeNull();
    });
  });

  describe('istZusatzZeile', () => {
    it('should not treat a server line with zusatzId null as an additional line', () => {
      const zeile = component.berechnung!.mieter[0].zeilen[1];
      expect(zeile.zusatzId).toBeNull();
      expect(component.istZusatzZeile(zeile)).toBe(false);
    });

    it('should treat a line with a zusatzId as an additional line', () => {
      expect(component.istZusatzZeile({ ...component.berechnung!.mieter[0].zeilen[1], zusatzId: 7 }))
        .toBe(true);
    });
  });

  describe('Verbrauchsmengen', () => {
    it('should render an editable quantity field right after loading', async () => {
      await oeffneMieterblock();

      const felder = mengenFelder();
      expect(felder.length).toBe(1);
      expect(felder[0].disabled).toBe(false);
      expect(felder[0].value).toBe('12');
    });

    it('should write a typed quantity back into the position', async () => {
      await oeffneMieterblock();

      const feld = mengenFelder()[0];
      feld.value = '20';
      feld.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      const verbrauch = component.positionen[1].verbraeuche.find(v => v.mieterId === 100);
      expect(verbrauch?.menge).toBe(20);
    });

    it('should recalculate while typing, not only on blur', async () => {
      await oeffneMieterblock();

      const feld = mengenFelder()[0];
      feld.value = '20';
      feld.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      // 20 x 4.50 = 90.00 - ohne Verlassen des Feldes.
      const zeile = component.berechnung!.mieter[0].zeilen
        .find(z => z.bezeichnung === 'Warmwasser');
      expect(zeile?.betrag).toBe(90);
    });

    it('should offer an empty quantity field when nothing is recorded yet', async () => {
      // Der Normalfall einer frisch angelegten Verbrauchsposition: noch keine Menge je Mieter.
      const ohneMenge = structuredClone(serverDetail) as any;
      ohneMenge.positionen[1].verbraeuche = [];
      ohneMenge.berechnung.mieter[0].zeilen[1].menge = null;
      ohneMenge.berechnung.mieter[0].zeilen[1].betrag = 0;
      nebenkostenServiceSpy.getAbrechnungDetail.mockReturnValue(of(ohneMenge));

      component.ngOnInit();
      await oeffneMieterblock();

      const felder = mengenFelder();
      expect(felder.length).toBe(1);
      expect(felder[0].disabled).toBe(false);
      expect(felder[0].value).toBe('');
    });

    it('should clear the recorded quantity when the field is emptied', async () => {
      await oeffneMieterblock();

      const feld = mengenFelder()[0];
      feld.value = '';
      feld.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      const verbrauch = component.positionen[1].verbraeuche.find(v => v.mieterId === 100);
      expect(verbrauch?.menge).toBeNull();
    });

    it('should not offer a quantity field on an allocation line', async () => {
      await oeffneMieterblock();

      // Nur die Verbrauchszeile hat ein Feld - die Umlagezeile ist berechnet.
      expect(mengenFelder().length).toBe(1);
    });

    it('should lock the quantity field when the billing is closed', async () => {
      component.kopf.abgerechnet = true;
      await oeffneMieterblock();

      expect(mengenFelder().length).toBe(0);
    });
  });

  describe('Positionsart ANTEIL', () => {
    /** Heizkosten: Totalbetrag an der Position, Prozentsatz je Mieter. */
    function mitAnteilsposition(prozent: number | null): void {
      const detail = structuredClone(serverDetail) as any;
      detail.positionen = [{
        id: 20, art: 'ANTEIL', bezeichnung: 'Heizkosten', reihenfolge: 1,
        einheit: null, totalbetrag: 2400, gesamtmenge: null, betragProEinheit: null,
        prozentsatz: null,
        verbraeuche: prozent === null ? [] : [{ mieterId: 100, menge: prozent }]
      }];
      detail.berechnung.umlagen = [{
        positionId: 20, bezeichnung: 'Heizkosten', art: 'ANTEIL', totalbetrag: 2400,
        summeVerteilt: prozent === null ? 0 : 2400 * prozent / 100,
        nichtVerteilt: 0, rundungsdifferenz: 0, summeProzent: prozent ?? 0
      }];
      detail.berechnung.mieter[0].zeilen = [{
        positionId: 20, zusatzId: null, art: 'ANTEIL', reihenfolge: 1, bezeichnung: 'Heizkosten',
        einheit: null, menge: null, betragProEinheit: null, prozentsatz: prozent,
        betrag: prozent === null ? 0 : 2400 * prozent / 100
      }];
      nebenkostenServiceSpy.getAbrechnungDetail.mockReturnValue(of(detail));
      component.ngOnInit();
    }

    it('should offer an editable percentage field per tenant', async () => {
      mitAnteilsposition(60);
      await oeffneMieterblock();

      const felder = mengenFelder();
      expect(felder.length).toBe(1);
      expect(felder[0].disabled).toBe(false);
      expect(felder[0].value).toBe('60');
    });

    it('should label the unit column with a percent sign', async () => {
      mitAnteilsposition(60);
      await oeffneMieterblock();

      const zellen = Array.from(
        fixture.nativeElement.querySelectorAll('.nk-mieterzeilen tbody td'))
        .map(el => (el as HTMLElement).textContent?.trim());
      expect(zellen).toContain('%');
    });

    it('should recalculate the amount from the typed percentage', async () => {
      mitAnteilsposition(60);
      await oeffneMieterblock();

      const feld = mengenFelder()[0];
      feld.value = '50';
      feld.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      // 2400.00 x 50% = 1200.00
      expect(component.berechnung!.mieter[0].zeilen[0].betrag).toBe(1200);
    });

    it('should flag a share total other than 100 percent', async () => {
      mitAnteilsposition(60);
      await oeffneMieterblock();

      const info = component.berechnung!.umlagen[0];
      expect(component.summeProzentStimmt(info)).toBe(false);
    });

    it('should accept a share total of exactly 100 percent', () => {
      expect(component.summeProzentStimmt({ summeProzent: 100 } as any)).toBe(true);
      // 33.333 x 3 = 99.999 - auf drei Nachkommastellen erfasst, also keine echte Abweichung.
      expect(component.summeProzentStimmt({ summeProzent: 99.9999 } as any)).toBe(true);
    });
  });

  describe('Speichern', () => {
    function speichernButtons(): HTMLButtonElement[] {
      return Array.from(fixture.nativeElement.querySelectorAll('button'))
        .filter(b => (b as HTMLElement).textContent?.includes('SPEICHERN')) as HTMLButtonElement[];
    }

    it('should offer save at the top and at the end of the form', () => {
      // Bei dreissig Mieterbloecken liegt das Ende der Maske mehrere Bildschirmseiten entfernt.
      expect(speichernButtons().length).toBe(2);
    });

    it('should save from the button at the top as well', () => {
      speichernButtons()[0].click();

      expect(nebenkostenServiceSpy.updateAbrechnung).toHaveBeenCalledWith(1, expect.anything());
    });

    it('should hide the upper save button when the billing is closed', () => {
      component.kopf.abgerechnet = true;
      fixture.detectChanges();

      // Unten bleibt die Schaltflaeche stehen, aber gesperrt - oben verschwindet sie zusammen mit
      // "Position hinzufuegen". Die Zeile selbst bleibt: "Zurueck zur Uebersicht" steht dort auch
      // auf einer abgeschlossenen Abrechnung.
      const buttons = speichernButtons();
      expect(buttons.length).toBe(1);
      expect(buttons[0].disabled).toBe(true);
    });
  });

  describe('Zurück zur Übersicht', () => {
    function zurueckButtons(): HTMLButtonElement[] {
      return Array.from(fixture.nativeElement.querySelectorAll('button'))
        .filter(b => (b as HTMLElement).textContent?.includes('NK_ZURUECK_UEBERSICHT')) as HTMLButtonElement[];
    }

    it('should offer the way back at the top and at the end of the form', () => {
      // Aus demselben Grund wie das zweite Speichern: Bei dreissig Mieterbloecken liegt das Ende
      // der Maske mehrere Bildschirmseiten entfernt.
      expect(zurueckButtons().length).toBe(2);
    });

    it('should go back from the button at the top as well', () => {
      const closed = vi.spyOn(component.closed, 'emit');

      zurueckButtons()[0].click();

      expect(closed).toHaveBeenCalled();
    });

    /**
     * Auf einer abgeschlossenen Abrechnung ist der Weg zurück der einzige Grund, überhaupt eine
     * Schaltfläche zu suchen — er bleibt deshalb stehen, während „Position hinzufügen" und das
     * obere Speichern verschwinden.
     */
    it('should keep the upper way back when the billing is closed', () => {
      component.kopf.abgerechnet = true;
      fixture.detectChanges();

      expect(zurueckButtons().length).toBe(2);
      expect(zurueckButtons()[0].disabled).toBe(false);
    });

    it('should drop "Position hinzufügen" when the billing is closed', () => {
      component.kopf.abgerechnet = true;
      fixture.detectChanges();

      const hinzufuegen = Array.from(fixture.nativeElement.querySelectorAll('button'))
        .filter(b => (b as HTMLElement).textContent?.includes('NK_POSITION_HINZUFUEGEN'));
      expect(hinzufuegen.length).toBe(0);
    });
  });

  /**
   * „Abbrechen" verwirft die nicht gespeicherten Änderungen und bleibt in der Maske.
   *
   * Vorher tat es dasselbe wie „Zurück zur Übersicht" — zwei Schaltflächen mit identischem
   * Verhalten, von denen eine ein Versprechen gab, das sie nicht hielt.
   */
  describe('onAbbrechen', () => {
    it('should reload the billing instead of leaving the form', () => {
      const closed = vi.spyOn(component.closed, 'emit');
      nebenkostenServiceSpy.getAbrechnungDetail.mockClear();

      component.onAbbrechen();

      expect(nebenkostenServiceSpy.getAbrechnungDetail).toHaveBeenCalledWith(1);
      expect(closed).not.toHaveBeenCalled();
    });

    it('should discard unsaved changes', () => {
      component.kopf.bezeichnung = 'Halb getippt';

      component.onAbbrechen();

      // Der Stand des Servers, nicht der getippte.
      expect(component.kopf.bezeichnung).toBe('Nebenkosten 2026');
    });

    it('should confirm the discarding with a message', () => {
      component.onAbbrechen();

      expect(component.message).toBe('NK_AENDERUNGEN_VERWORFEN');
      expect(component.messageType).toBe('success');
    });

    it('should clear field errors of a failed save attempt', () => {
      component.speichernVersucht = true;

      component.onAbbrechen();

      expect(component.speichernVersucht).toBe(false);
    });

    /** Eine noch nicht gespeicherte Abrechnung hat keinen Stand, auf den man zurückfallen könnte. */
    it('should close the form for a new billing', () => {
      const closed = vi.spyOn(component.closed, 'emit');
      component.abrechnungId = null;
      nebenkostenServiceSpy.getAbrechnungDetail.mockClear();

      component.onAbbrechen();

      expect(closed).toHaveBeenCalled();
      expect(nebenkostenServiceSpy.getAbrechnungDetail).not.toHaveBeenCalled();
    });

    /**
     * Schlägt das Laden fehl, steht die Fehlermeldung — und **bleibt** stehen.
     *
     * Würde die Erfolgsmeldung vor dem Ergebnis gezeigt, nähme ihr Fünf-Sekunden-Timer die
     * Fehlermeldung mit: ein Fehler, der von selbst verschwindet.
     */
    it('should show the load error instead of a success message', fakeAsync(() => {
      nebenkostenServiceSpy.getAbrechnungDetail.mockReturnValue(throwError(() => ({ status: 500 })));

      component.onAbbrechen();

      expect(component.message).toBe('NK_FEHLER_LADEN');
      expect(component.messageType).toBe('error');

      tick(5000);

      expect(component.message).toBe('NK_FEHLER_LADEN');
    }));

    /** Der Timer der Erfolgsmeldung räumt nur die eigene Meldung ab. */
    it('should not clear a later error message after five seconds', fakeAsync(() => {
      component.onAbbrechen();
      expect(component.message).toBe('NK_AENDERUNGEN_VERWORFEN');

      nebenkostenServiceSpy.getAbrechnungDetail.mockReturnValue(throwError(() => ({ status: 500 })));
      component.ladeDetail(1);
      expect(component.message).toBe('NK_FEHLER_LADEN');

      tick(5000);

      expect(component.message).toBe('NK_FEHLER_LADEN');
    }));
  });

  describe('Ausrichtung der Mengeneinheit', () => {
    /**
     * Die Mengeneinheit steht bei jeder Positionsart in derselben Rasterspalte.
     *
     * Geprüft wird die Zuordnung zur Spalte über die Klasse — die tatsächliche Ausrichtung ist
     * eine Frage des Stylesheets und im jsdom nicht messbar. Ohne diese Klasse rutschte die
     * Mengeneinheit einer Verbrauchsposition unter die *Gesamtmenge* der Umlage darüber.
     */
    function einheitFelder(): HTMLElement[] {
      return Array.from(fixture.nativeElement.querySelectorAll('.nk-positionen__feld--einheit'));
    }

    it('should assign the unit field of a consumption position to its own column', () => {
      component.positionen = [{
        art: NkPositionsart.VERBRAUCH, bezeichnung: 'Wasser', einheit: Mengeneinheit.M3,
        totalbetrag: null, gesamtmenge: null, betragProEinheit: 1.85, prozentsatz: null,
        verbraeuche: []
      }];
      fixture.detectChanges();

      expect(einheitFelder().length).toBe(1);
    });

    it('should assign the unit field of an allocation position to the same column', () => {
      component.positionen = [{
        art: NkPositionsart.UMLAGE, bezeichnung: 'Allgemeinstrom', einheit: Mengeneinheit.CHF,
        totalbetrag: 900, gesamtmenge: null, betragProEinheit: null, prozentsatz: null,
        verbraeuche: []
      }];
      fixture.detectChanges();

      expect(einheitFelder().length).toBe(1);
    });

    it('should not offer a unit field for a share or surcharge position', () => {
      component.positionen = [{
        art: NkPositionsart.ANTEIL, bezeichnung: 'Heizkosten', einheit: null,
        totalbetrag: 1000, gesamtmenge: null, betragProEinheit: null, prozentsatz: null,
        verbraeuche: []
      }];
      fixture.detectChanges();

      expect(einheitFelder().length).toBe(0);
    });
  });

  describe('Fehlermeldungen', () => {
    function feldfehler(): string[] {
      return Array.from(fixture.nativeElement.querySelectorAll('.zev-form-error'))
        .map(el => (el as HTMLElement).textContent?.trim() ?? '');
    }

    it('should not show field errors before the first save attempt', () => {
      // Eine frisch geoeffnete Maske ist zwangslaeufig unvollstaendig; rote Meldungen tadelten
      // den Benutzer fuer etwas, das er noch gar nicht getan hat.
      component.kopf.bezeichnung = '';
      component.kopf.anzahlWohnungen = null;
      fixture.detectChanges();

      expect(component.speichernVersucht).toBe(false);
      expect(feldfehler().length).toBe(0);
    });

    it('should show field errors after pressing save with invalid input', () => {
      component.kopf.bezeichnung = '';
      component.kopf.anzahlWohnungen = null;

      component.onSpeichern();
      fixture.detectChanges();

      expect(component.speichernVersucht).toBe(true);
      expect(feldfehler().length).toBeGreaterThan(0);
      expect(component.message).toBe('NK_FEHLER_EINGABEN');
      expect(component.messageType).toBe('error');
    });

    /**
     * Eine unberührte Maske für eine neue Abrechnung meldet Feldfehler statt zu werfen.
     *
     * <p>Bewusst auf einer **frischen** Komponente: Die Vorlage setzt den Kopf nicht mehr zurück
     * (sie überschrieb sonst die ersten Eingaben), ein `ngOnInit()` auf der geladenen Instanz
     * liesse deren Werte also stehen. Der ursprüngliche Fall bleibt derselbe — die Vorlage kommt
     * serverförmig, mit `null` statt `''`, und ein `bezeichnung.trim()` darauf würde werfen.
     */
    it('should report the errors of a completely untouched template', () => {
      const vorlage = {
        abrechnung: {
          id: null, bezeichnung: null, datumVon: null, datumBis: null,
          anzahlWohnungen: null, anzahlPersonen: null, abgerechnet: false
        },
        positionen: [], zusaetze: [], akonto: [], berechnung: null,
        personen: [],
        anzahlWohnungenVorschlag: null,
        anzahlPersonenVorschlag: null
      };
      nebenkostenServiceSpy.getVorlage.mockReturnValue(of(vorlage as any));

      const neu = TestBed.createComponent(NebenkostenAbrechnungFormComponent);
      neu.componentInstance.abrechnungId = null;
      neu.detectChanges();

      expect(() => neu.componentInstance.onSpeichern()).not.toThrow();
      expect(neu.componentInstance.message).toBe('NK_FEHLER_EINGABEN');
      expect(neu.componentInstance.istGueltig()).toBe(false);
      expect(nebenkostenServiceSpy.createAbrechnung).not.toHaveBeenCalled();
    });

    it('should not send a request when the input is invalid', () => {
      component.kopf.bezeichnung = '';

      component.onSpeichern();

      expect(nebenkostenServiceSpy.updateAbrechnung).not.toHaveBeenCalled();
      expect(nebenkostenServiceSpy.createAbrechnung).not.toHaveBeenCalled();
    });

    it('should render the message with a dismiss marker and clear it on click', () => {
      component.kopf.bezeichnung = '';
      component.onSpeichern();
      fixture.detectChanges();

      const meldung = fixture.nativeElement.querySelector('.zev-message--error');
      expect(meldung).not.toBeNull();
      expect(meldung.querySelector('.zev-message__dismiss')).not.toBeNull();

      meldung.click();
      fixture.detectChanges();

      expect(component.message).toBe('');
      expect(fixture.nativeElement.querySelector('.zev-message--error')).toBeNull();
    });
  });

  describe('umlageInfoFuer', () => {
    it('should find the control figures by database id, not by position in the list', () => {
      const umlageZeile = component.berechnung!.mieter[0].zeilen[0];
      expect(component.umlageInfoFuer(umlageZeile)?.nichtVerteilt).toBe(450);
    });
  });

  // ==================== Laden ====================

  describe('Laden', () => {

    it('should load the template when no billing id is given', () => {
      const neu = TestBed.createComponent(NebenkostenAbrechnungFormComponent);
      neu.componentInstance.abrechnungId = null;
      neu.detectChanges();

      expect(nebenkostenServiceSpy.getVorlage).toHaveBeenCalled();
      expect(neu.componentInstance.laedt).toBe(false);
    });

    /**
     * Die Vorlage bringt die vorgeschlagene Anzahl Wohnungen mit.
     *
     * Ohne diese Übernahme müsste der Benutzer den Nenner der Umlage raten — und ein zu kleiner
     * Nenner lässt den Server das Speichern abweisen (FR-2).
     */
    it('should adopt the suggested number of flats from the template', () => {
      const vorlage = structuredClone(serverDetail) as NkAbrechnungDetail;
      vorlage.abrechnung.anzahlWohnungen = null;
      vorlage.anzahlWohnungenVorschlag = 9;
      nebenkostenServiceSpy.getVorlage.mockReturnValue(of(vorlage));

      const neu = TestBed.createComponent(NebenkostenAbrechnungFormComponent);
      neu.componentInstance.abrechnungId = null;
      neu.detectChanges();

      expect(neu.componentInstance.kopf.anzahlWohnungen).toBe(9);
    });

    /**
     * **Eine spät eintreffende Vorlage überschreibt die ersten Eingaben nicht.**
     *
     * Die Maske ist bedienbar, während die Vorlage unterwegs ist — `laedt` steuert kein
     * Rendering. Vorher setzte die Antwort per `uebernehme()` den ganzen Kopf zurück: Der Benutzer
     * tippte eine Bezeichnung, und sie war wieder weg. Zwei Fälle der E2E-Suite scheiterten daran
     * beim ersten Speichern mit „Eingaben prüfen".
     */
    it('should not overwrite typed input when the template arrives late', () => {
      const spaet = new Subject<NkAbrechnungDetail>();
      nebenkostenServiceSpy.getVorlage.mockReturnValue(spaet);

      const neu = TestBed.createComponent(NebenkostenAbrechnungFormComponent);
      neu.componentInstance.abrechnungId = null;
      neu.detectChanges();

      // Der Benutzer tippt, bevor die Antwort da ist.
      neu.componentInstance.kopf.bezeichnung = 'Nebenkosten 2087';
      neu.componentInstance.kopf.datumVon = '2087-01-01';
      neu.componentInstance.kopf.datumBis = '2087-12-31';
      neu.componentInstance.kopf.anzahlWohnungen = 99;

      const vorlage = structuredClone(serverDetail) as NkAbrechnungDetail;
      vorlage.abrechnung.bezeichnung = '';
      vorlage.abrechnung.datumVon = '';
      vorlage.abrechnung.datumBis = '';
      vorlage.anzahlWohnungenVorschlag = 9;
      spaet.next(vorlage);

      expect(neu.componentInstance.kopf.bezeichnung).toBe('Nebenkosten 2087');
      expect(neu.componentInstance.kopf.datumVon).toBe('2087-01-01');
      expect(neu.componentInstance.kopf.datumBis).toBe('2087-12-31');
      expect(neu.componentInstance.kopf.anzahlWohnungen).toBe(99);
      expect(neu.componentInstance.istGueltig()).toBe(true);
    });

    /** Der Vorschlag greift nur, wo noch keine Anzahl steht. */
    it('should still adopt the suggestion when nothing was typed', () => {
      const spaet = new Subject<NkAbrechnungDetail>();
      nebenkostenServiceSpy.getVorlage.mockReturnValue(spaet);

      const neu = TestBed.createComponent(NebenkostenAbrechnungFormComponent);
      neu.componentInstance.abrechnungId = null;
      neu.detectChanges();

      const vorlage = structuredClone(serverDetail) as NkAbrechnungDetail;
      vorlage.anzahlWohnungenVorschlag = 7;
      spaet.next(vorlage);

      expect(neu.componentInstance.kopf.anzahlWohnungen).toBe(7);
      expect(neu.componentInstance.anzahlWohnungenVorschlag).toBe(7);
    });

    it('should report an error when loading the detail fails', () => {
      nebenkostenServiceSpy.getAbrechnungDetail.mockReturnValue(throwError(() => new Error('kaputt')));

      const neu = TestBed.createComponent(NebenkostenAbrechnungFormComponent);
      neu.componentInstance.abrechnungId = 1;
      neu.detectChanges();

      expect(neu.componentInstance.message).toBe('NK_FEHLER_LADEN');
      expect(neu.componentInstance.laedt).toBe(false);
    });

    it('should report an error when loading the template fails', () => {
      nebenkostenServiceSpy.getVorlage.mockReturnValue(throwError(() => new Error('kaputt')));

      const neu = TestBed.createComponent(NebenkostenAbrechnungFormComponent);
      neu.componentInstance.abrechnungId = null;
      neu.detectChanges();

      expect(neu.componentInstance.message).toBe('NK_FEHLER_LADEN');
      expect(neu.componentInstance.laedt).toBe(false);
    });
  });

  // ==================== Zustand der Maske ====================

  describe('gesperrt und hatMieterbloecke', () => {

    it('should not be locked while the billing is open', () => {
      expect(component.gesperrt).toBe(false);
    });

    it('should be locked once the billing is closed', () => {
      component.kopf.abgerechnet = true;

      expect(component.gesperrt).toBe(true);
    });

    it('should report tenant blocks only when the server delivered rental days', () => {
      expect(component.hatMieterbloecke).toBe(true);

      component.mieterTage = [];

      expect(component.hatMieterbloecke).toBe(false);
    });
  });

  // ==================== Aufklappen der Mieterblöcke ====================

  describe('Mieterblöcke', () => {

    it('should open and close a tenant block on toggle', () => {
      expect(component.istMieterOffen(100)).toBe(false);

      component.toggleMieter(100);
      expect(component.istMieterOffen(100)).toBe(true);

      component.toggleMieter(100);
      expect(component.istMieterOffen(100)).toBe(false);
    });

    it('should keep the blocks of other tenants closed', () => {
      component.toggleMieter(100);

      expect(component.istMieterOffen(200)).toBe(false);
    });
  });

  // ==================== Hinweise ====================

  describe('Hinweise', () => {

    it('should hide the explanatory hint permanently and remember it', () => {
      component.dismissHinweisErstSpeichern();

      expect(component.hinweisErstSpeichernSichtbar).toBe(false);
      expect(localStorage.getItem('zev.nebenkosten.hinweisErstSpeichern')).toBe('true');
    });

    it('should not show the explanatory hint again after it was dismissed', () => {
      localStorage.setItem('zev.nebenkosten.hinweisErstSpeichern', 'true');

      const neu = TestBed.createComponent(NebenkostenAbrechnungFormComponent);
      neu.componentInstance.abrechnungId = 1;
      neu.detectChanges();

      expect(neu.componentInstance.hinweisErstSpeichernSichtbar).toBe(false);
    });

    /**
     * Ein gesperrter Speicher darf die Maske nicht mitnehmen.
     *
     * In einem privaten Fenster oder bei blockierten Site-Daten wirft schon der Zugriff. Der
     * Hinweis erscheint dann beim nächsten Öffnen erneut — unschön, aber bedienbar.
     */
    it('should survive a localStorage that throws', () => {
      const setItem = vi.spyOn(Storage.prototype, 'setItem')
        .mockImplementation(() => { throw new Error('gesperrt'); });
      const getItem = vi.spyOn(Storage.prototype, 'getItem')
        .mockImplementation(() => { throw new Error('gesperrt'); });

      expect(() => component.dismissHinweisErstSpeichern()).not.toThrow();
      expect(component.hinweisErstSpeichernSichtbar).toBe(false);

      const neu = TestBed.createComponent(NebenkostenAbrechnungFormComponent);
      neu.componentInstance.abrechnungId = 1;
      expect(() => neu.detectChanges()).not.toThrow();
      expect(neu.componentInstance.hinweisErstSpeichernSichtbar).toBe(true);

      setItem.mockRestore();
      getItem.mockRestore();
    });

    it('should hide the lock hint for the open form only', () => {
      component.dismissHinweisAbgerechnet();

      expect(component.hinweisAbgerechnetSichtbar).toBe(false);
    });

    /** Je Mieter einzeln: Wer einen Hinweis wegklickt, meint diesen einen Block. */
    it('should dismiss the no-flat hint per tenant', () => {
      expect(component.hinweisOhneWohnungSichtbar(100)).toBe(true);

      component.dismissHinweisOhneWohnung(100);

      expect(component.hinweisOhneWohnungSichtbar(100)).toBe(false);
      expect(component.hinweisOhneWohnungSichtbar(200)).toBe(true);
    });
  });

  // ==================== Positionen ====================

  describe('Positionen', () => {

    it('should add a position as UMLAGE and recalculate', () => {
      const vorher = component.positionen.length;

      component.onPositionHinzufuegen();

      expect(component.positionen.length).toBe(vorher + 1);
      expect(component.positionen.at(-1)?.art).toBe(NkPositionsart.UMLAGE);
    });

    it('should remove the position at the given index', () => {
      component.onPositionEntfernen(0);

      expect(component.positionen.length).toBe(1);
      expect(component.positionen[0].bezeichnung).toBe('Warmwasser');
    });

    /**
     * Beim Wechsel der Art werden die Felder der alten Art geleert.
     *
     * Ohne das Abräumen bliebe ein Totalbetrag an einer Zuschlagszeile hängen: unsichtbar, weil
     * die Maske ihn ausblendet, aber im Rumpf des Requests — und dort läuft er in den
     * CHECK-Constraint der Datenbank.
     */
    it('should clear the fields of the previous art on change', () => {
      const position = component.positionen[0];
      expect(position.totalbetrag).toBe(900);

      position.art = NkPositionsart.ZUSCHLAG;
      component.onArtChange(position);

      expect(position.totalbetrag).toBeNull();
      expect(position.gesamtmenge).toBeNull();
      expect(position.betragProEinheit).toBeNull();
      expect(position.prozentsatz).toBeNull();
      expect(position.verbraeuche).toEqual([]);
    });

    it('should drop the unit for an art that carries none', () => {
      const position = component.positionen[0];
      position.art = NkPositionsart.ZUSCHLAG;

      component.onArtChange(position);

      expect(position.einheit).toBeNull();
    });

    it('should keep a unit for an art that needs one', () => {
      const position = component.positionen[0];
      position.art = NkPositionsart.VERBRAUCH;

      component.onArtChange(position);

      expect(position.einheit).toBe(Mengeneinheit.M3);
    });

    /** Die Reihenfolge bestimmt die Bemessungsgrundlage der Zuschläge — Verschieben rechnet neu. */
    it('should reorder the positions on drop', () => {
      component.onDrop({ previousIndex: 0, currentIndex: 1 } as CdkDragDrop<NkPosition[]>);

      expect(component.positionen.map(p => p.bezeichnung))
        .toEqual(['Warmwasser', 'Allgemeinstrom']);
    });
  });

  // ==================== Stabile Zeilen-Identität ====================

  describe('trackZeile', () => {

    it('should key a saved line by its database id', () => {
      expect(component.trackZeile(0, { id: 42 })).toBe('id-42');
    });

    it('should key an unsaved line by its index', () => {
      expect(component.trackZeile(3, {})).toBe('neu-3');
    });

    /**
     * Zwei ungespeicherte Zeilen dürfen nicht denselben Schlüssel tragen — sonst wirft Angular
     * NG0955 (doppelte Keys) und die Tabelle bricht.
     */
    it('should give unsaved lines distinct keys', () => {
      expect(component.trackZeile(0, {})).not.toBe(component.trackZeile(1, {}));
    });
  });

  // ==================== Bemessungsgrundlage eines Zuschlags ====================

  describe('bemessungsgrundlage', () => {

    it('should name all positions above the given index', () => {
      expect(component.bemessungsgrundlage(2)).toBe('Allgemeinstrom, Warmwasser');
    });

    it('should report that nothing is above the first position', () => {
      expect(component.bemessungsgrundlage(0)).toBe('NK_KEINE_ZEILEN_DAVOR');
    });

    it('should skip positions without a label', () => {
      // Eine frisch hinzugefuegte Zeile hat noch keine Bezeichnung und gehoert nicht in die
      // Aufzaehlung - sonst stuende dort ein leerer Eintrag mit Komma.
      component.positionen[0].bezeichnung = '';

      expect(component.bemessungsgrundlage(2)).toBe('Warmwasser');
    });
  });

  // ==================== Mengen je Mieter ====================

  describe('mengeFuer und onMengeChange', () => {

    it('should return the recorded quantity of a tenant', () => {
      expect(component.mengeFuer(component.positionen[1], 100)).toBe(12);
    });

    it('should return null when nothing is recorded for that tenant', () => {
      expect(component.mengeFuer(component.positionen[1], 999)).toBeNull();
    });

    it('should create the row when the tenant has none yet', () => {
      const position = component.positionen[1];

      component.onMengeChange(position, 200, 7);

      expect(position.verbraeuche).toContainEqual({ mieterId: 200, menge: 7 });
    });

    it('should overwrite an existing quantity', () => {
      const position = component.positionen[1];

      component.onMengeChange(position, 100, 20);

      expect(component.mengeFuer(position, 100)).toBe(20);
    });

    /**
     * Ein leeres Feld heisst „nicht erfasst" und nicht „null Kubikmeter".
     *
     * Über `ngModelChange` kommt je nach Browser ein leerer String statt `null`; beides muss zu
     * `null` werden, sonst stünde eine erfasste 0 dort, wo der Benutzer nichts angegeben hat.
     */
    it('should treat an empty string and null as "not recorded"', () => {
      const position = component.positionen[1];

      component.onMengeChange(position, 100, '');
      expect(component.mengeFuer(position, 100)).toBeNull();

      component.onMengeChange(position, 100, 5);
      component.onMengeChange(position, 100, null);
      expect(component.mengeFuer(position, 100)).toBeNull();
    });

    it('should accept a numeric string from the input field', () => {
      const position = component.positionen[1];

      component.onMengeChange(position, 100, '8.5');

      expect(component.mengeFuer(position, 100)).toBe(8.5);
    });
  });

  // ==================== Zusatzzeilen ====================

  describe('Zusatzzeilen', () => {

    it('should return only the extra lines of the given tenant', () => {
      component.zusaetze = [
        { mieterId: 100, bezeichnung: 'A', einheit: Mengeneinheit.STUECK, menge: 1, betragProEinheit: 1 },
        { mieterId: 200, bezeichnung: 'B', einheit: Mengeneinheit.STUECK, menge: 1, betragProEinheit: 1 }
      ];

      expect(component.zusaetzeFuer(100).map(z => z.bezeichnung)).toEqual(['A']);
    });

    /**
     * Eine neue Zusatzzeile landet am Ende der Kaskade.
     *
     * Ein Zuschlag rechnet auf alles, was über ihm steht. Eine neu eingefügte Zeile darf
     * bestehende Zuschläge nicht rückwirkend verändern, solange der Benutzer sie nicht bewusst
     * nach vorne schiebt.
     */
    it('should append a new extra line behind all positions', () => {
      component.onZusatzHinzufuegen(100);

      const neu = component.zusaetzeFuer(100)[0];
      expect(neu.reihenfolge).toBe(component.positionen.length + 1);
      expect(neu.einheit).toBe(Mengeneinheit.STUECK);
      expect(neu.menge).toBeNull();
    });

    it('should count the existing extra lines of the tenant for the order', () => {
      component.onZusatzHinzufuegen(100);
      component.onZusatzHinzufuegen(100);

      expect(component.zusaetzeFuer(100).map(z => z.reihenfolge))
        .toEqual([component.positionen.length + 1, component.positionen.length + 2]);
    });

    it('should remove exactly the given extra line', () => {
      component.onZusatzHinzufuegen(100);
      component.onZusatzHinzufuegen(100);
      const zuLoeschen = component.zusaetzeFuer(100)[0];

      component.onZusatzEntfernen(zuLoeschen);

      expect(component.zusaetze).not.toContain(zuLoeschen);
      expect(component.zusaetzeFuer(100).length).toBe(1);
    });

    it('should ignore an extra line that is no longer in the list', () => {
      const fremd = {
        mieterId: 100, bezeichnung: 'weg', einheit: Mengeneinheit.STUECK,
        menge: null, betragProEinheit: null
      };
      const vorher = component.zusaetze.length;

      component.onZusatzEntfernen(fremd);

      expect(component.zusaetze.length).toBe(vorher);
    });
  });

  // ==================== Akonto ====================

  describe('akontoFuer', () => {

    it('should return the recorded prepayment of a tenant', () => {
      expect(component.akontoFuer(100).betragProMonat).toBe(100);
    });

    /**
     * Fehlt eine Akonto-Zeile, wird sie aus dem Vorschlag des Servers angelegt **und in die
     * Liste aufgenommen**.
     *
     * Nur so überlebt der Vorschlag das Speichern: Ein unveränderter Block fiele sonst beim
     * nächsten Laden auf 0 zurück, obwohl die Maske einen Wert anzeigte.
     */
    it('should create a prepayment row from the server suggestion', () => {
      component.akonto = [];
      component.berechnung = {
        nenner: 730, summeTage: 365, nennerPerson: 730, summePersonenTage: 365, umlagen: [],
        mieter: [{
          mieterId: 100, name: 'Anna Beispiel', tage: 365, anzahlPersonen: 1,
          personenTage: 365, ohneWohnung: false, zeilen: [],
          kostentotal: 0, akontoAnzahlMonate: 11, akontoBetragProMonat: 150,
          akontoKorrektur: -20, akontoTotal: 0, saldo: 0
        }]
      };

      const akonto = component.akontoFuer(100);

      expect(akonto.anzahlMonate).toBe(11);
      expect(akonto.betragProMonat).toBe(150);
      expect(akonto.korrektur).toBe(-20);
      expect(component.akonto).toContain(akonto);
    });

    it('should create an empty prepayment row when the server has no suggestion', () => {
      component.akonto = [];
      component.berechnung = null;

      const akonto = component.akontoFuer(999);

      expect(akonto.anzahlMonate).toBeNull();
      expect(akonto.betragProMonat).toBeNull();
      expect(akonto.korrektur).toBe(0);
    });

    it('should return the same row on repeated calls', () => {
      const erste = component.akontoFuer(500);

      expect(component.akontoFuer(500)).toBe(erste);
      expect(component.akonto.filter(a => a.mieterId === 500).length).toBe(1);
    });
  });

  // ==================== Gültigkeit ====================

  describe('istGueltig und istZeitraumGueltig', () => {

    it('should accept the loaded billing', () => {
      expect(component.istGueltig()).toBe(true);
    });

    it('should reject a label of only whitespace', () => {
      component.kopf.bezeichnung = '   ';

      expect(component.istGueltig()).toBe(false);
    });

    it('should accept a period of a single day', () => {
      // von == bis ist ein gueltiger Zeitraum; die Pruefung darf nicht auf "kleiner" bestehen.
      component.kopf.datumVon = '2026-05-01';
      component.kopf.datumBis = '2026-05-01';

      expect(component.istGueltig()).toBe(true);
      expect(component.istZeitraumGueltig()).toBe(true);
    });

    it('should reject a period that ends before it starts', () => {
      component.kopf.datumVon = '2026-12-31';
      component.kopf.datumBis = '2026-01-01';

      expect(component.istGueltig()).toBe(false);
      expect(component.istZeitraumGueltig()).toBe(false);
    });

    it('should treat a half-filled period as not yet wrong', () => {
      // Waehrend des Tippens fehlt das zweite Datum - das ist kein Fehler, nur unvollstaendig.
      component.kopf.datumBis = '';

      expect(component.istZeitraumGueltig()).toBe(true);
      expect(component.istGueltig()).toBe(false);
    });

    it('should reject fewer than one flat', () => {
      component.kopf.anzahlWohnungen = 0;
      expect(component.istGueltig()).toBe(false);

      component.kopf.anzahlWohnungen = null;
      expect(component.istGueltig()).toBe(false);

      component.kopf.anzahlWohnungen = 1;
      expect(component.istGueltig()).toBe(true);
    });
  });

  // ==================== Anzeige ====================

  describe('Formatierung', () => {

    /**
     * Ein fehlender Betrag ist 0.00, eine fehlende Menge bleibt leer.
     *
     * Die Asymmetrie ist gewollt: Ein Betrag von null Franken ist eine Aussage, eine nicht
     * erfasste Menge ist keine — dort stünde sonst „0.000", wo der Benutzer nichts angegeben hat.
     */
    it('should show a missing amount as zero and a missing quantity as empty', () => {
      expect(component.betrag(null)).toBe('0.00');
      expect(component.betrag(undefined)).toBe('0.00');
      expect(component.menge(null)).toBe('');
      expect(component.menge(undefined)).toBe('');
    });

    it('should format amounts with two and quantities with three decimals', () => {
      expect(component.betrag(1234.5)).toBe("1'234.50");
      expect(component.menge(12.3456)).toBe('12.346');
    });

    it('should show a recorded zero quantity', () => {
      // Eine erfasste 0 ist eine Aussage und darf nicht wie "nicht erfasst" aussehen.
      expect(component.menge(0)).toBe('0.000');
    });
  });

  // ==================== Zeilen-Zuordnung ====================

  describe('istEingebbareZeile', () => {

    const zeile = (art: string, zusatzId: number | null = null) =>
      ({ art, zusatzId, reihenfolge: 1, bezeichnung: '', betrag: 0 } as unknown as NkZeile);

    it('should accept a consumption and a share line', () => {
      expect(component.istEingebbareZeile(zeile('VERBRAUCH'))).toBe(true);
      expect(component.istEingebbareZeile(zeile('ANTEIL'))).toBe(true);
    });

    it('should refuse a calculated line', () => {
      expect(component.istEingebbareZeile(zeile('UMLAGE'))).toBe(false);
      expect(component.istEingebbareZeile(zeile('ZUSCHLAG'))).toBe(false);
    });

    it('should refuse an extra line — it carries its own fields', () => {
      expect(component.istEingebbareZeile(zeile('VERBRAUCH', 5))).toBe(false);
    });

    it('should refuse every line once the billing is closed', () => {
      component.kopf.abgerechnet = true;

      expect(component.istEingebbareZeile(zeile('VERBRAUCH'))).toBe(false);
    });
  });

  describe('positionZuZeile', () => {

    it('should find the position by its database id', () => {
      const zeile = { positionId: 11, reihenfolge: 99 } as unknown as NkZeile;

      expect(component.positionZuZeile(zeile)?.bezeichnung).toBe('Warmwasser');
    });

    it('should fall back to the order for an unsaved position', () => {
      // Vor dem ersten Speichern hat die Position keine ID; die Vorschau fuehrt sie ueber die
      // Listenposition.
      const zeile = { positionId: null, reihenfolge: 1 } as unknown as NkZeile;

      expect(component.positionZuZeile(zeile)?.bezeichnung).toBe('Allgemeinstrom');
    });
  });

  describe('summeProzentStimmt', () => {

    const info = (summeProzent: number) => ({ summeProzent } as unknown as NkUmlageInfo);

    it('should accept exactly one hundred percent', () => {
      expect(component.summeProzentStimmt(info(100))).toBe(true);
    });

    /**
     * Die Toleranz von 0.0005 fängt **Gleitkomma-Rauschen** ab, nicht eine echte Lücke.
     *
     * Das ist geprüftes Ist-Verhalten und kein Zufall: Der Prozentsatz je Mieter wird mit drei
     * Nachkommastellen erfasst ({@code nk_verbrauch.menge}, `NUMERIC(12,3)`), genau 100 % ist
     * also erreichbar — 33.334 + 33.333 + 33.333. Deshalb *soll* 99.999 als Abweichung
     * erscheinen, statt stillschweigend durchzugehen.
     */
    it('should accept floating point noise around one hundred', () => {
      expect(component.summeProzentStimmt(info(99.9999999))).toBe(true);
      expect(component.summeProzentStimmt(info(100.0000001))).toBe(true);
    });

    it('should flag a real deviation, however small', () => {
      // 33.333 x 3 - erreichbar waere 100.000 ueber 33.334 + 33.333 + 33.333.
      expect(component.summeProzentStimmt(info(99.999))).toBe(false);
      expect(component.summeProzentStimmt(info(99.99))).toBe(false);
      expect(component.summeProzentStimmt(info(120))).toBe(false);
    });
  });

  // ==================== Verlassen und Speichern ====================

  describe('Verlassen', () => {

    /**
     * „Abbrechen" verlässt die Maske **nicht** mehr, sondern lädt neu — Einzelheiten in
     * `describe('onAbbrechen')`. Hier steht nur die Abgrenzung: Von den beiden Schaltflächen
     * verlässt allein „Zurück zur Übersicht" die Maske.
     */
    it('should not emit closed on cancel of a saved billing', () => {
      const geschlossen = vi.fn();
      component.closed.subscribe(geschlossen);

      component.onAbbrechen();

      expect(geschlossen).not.toHaveBeenCalled();
    });

    it('should emit closed on the way back to the list', () => {
      const geschlossen = vi.fn();
      component.closed.subscribe(geschlossen);

      component.onZurueckZurUebersicht();

      expect(geschlossen).toHaveBeenCalled();
    });
  });

  /**
   * „Zurück zur Übersicht" sichert und geht: „ich bin fertig" heisst beides.
   *
   * Der Weg hinaus ist damit nicht mehr verlustfrei zu haben — genau das war gewollt. Scheitert
   * das Speichern, bleibt die Maske stehen: Ein Verlassen würde die Eingaben verwerfen, die
   * gerade gesichert werden sollten.
   */
  describe('onZurueckZurUebersicht', () => {
    it('should save before leaving', () => {
      const geschlossen = vi.fn();
      component.closed.subscribe(geschlossen);
      component.kopf.bezeichnung = 'Geändert';

      component.onZurueckZurUebersicht();

      expect(nebenkostenServiceSpy.updateAbrechnung).toHaveBeenCalledWith(1, expect.anything());
      expect(geschlossen).toHaveBeenCalled();
    });

    it('should not leave when the input is invalid', () => {
      const geschlossen = vi.fn();
      component.closed.subscribe(geschlossen);
      component.kopf.bezeichnung = '';

      component.onZurueckZurUebersicht();

      expect(nebenkostenServiceSpy.updateAbrechnung).not.toHaveBeenCalled();
      expect(geschlossen).not.toHaveBeenCalled();
      expect(component.message).toBe('NK_FEHLER_EINGABEN');
    });

    it('should not leave when the server rejects the save', () => {
      const geschlossen = vi.fn();
      component.closed.subscribe(geschlossen);
      nebenkostenServiceSpy.updateAbrechnung.mockReturnValue(
        throwError(() => ({ error: 'NK_FEHLER_ZEITRAUM' })));

      component.onZurueckZurUebersicht();

      expect(geschlossen).not.toHaveBeenCalled();
      expect(component.message).toBe('NK_FEHLER_ZEITRAUM');
    });

    /** Auf einer abgeschlossenen Abrechnung gibt es nichts zu speichern — der Server wiese es ab. */
    it('should leave without saving when the billing is closed', () => {
      const geschlossen = vi.fn();
      component.closed.subscribe(geschlossen);
      component.kopf.abgerechnet = true;

      component.onZurueckZurUebersicht();

      expect(nebenkostenServiceSpy.updateAbrechnung).not.toHaveBeenCalled();
      expect(geschlossen).toHaveBeenCalled();
    });

    it('should create a new billing before leaving', () => {
      const geschlossen = vi.fn();
      component.closed.subscribe(geschlossen);
      component.abrechnungId = null;
      nebenkostenServiceSpy.createAbrechnung.mockReturnValue(
        of({ ...serverDetail.abrechnung, id: 7 }));

      component.onZurueckZurUebersicht();

      expect(nebenkostenServiceSpy.createAbrechnung).toHaveBeenCalled();
      expect(nebenkostenServiceSpy.updateAbrechnung).toHaveBeenCalledWith(7, expect.anything());
      expect(geschlossen).toHaveBeenCalled();
    });

    /**
     * Beide Bereiche stellen ihre Schaltflächen gleich dar: Der Modifier `--equal` setzte
     * `flex: 1` auf jeden Button und zog sie über die ganze Zeile — die obere Zeile tut das nicht.
     */
    it('should not stretch the buttons across the row', () => {
      const aktionen = fixture.nativeElement.querySelector('.zev-form-actions') as HTMLElement;

      expect(aktionen).toBeTruthy();
      expect(aktionen.classList.contains('zev-form-actions--equal')).toBe(false);
    });
  });

  describe('Speichern der Details', () => {

    it('should send the complete state and report success', () => {
      component.onSpeichern();

      expect(nebenkostenServiceSpy.updateAbrechnung).toHaveBeenCalled();
      const [id, detail] = nebenkostenServiceSpy.updateAbrechnung.mock.calls[0];
      expect(id).toBe(1);
      expect(detail.positionen.length).toBe(2);
      expect(detail.akonto.length).toBeGreaterThan(0);
      expect(component.messageType).toBe('success');
    });

    it('should announce the save to the list', () => {
      const gespeichert = vi.fn();
      component.saved.subscribe(gespeichert);

      component.onSpeichern();

      expect(gespeichert).toHaveBeenCalled();
    });

    /**
     * Der Fehlerschlüssel des Servers wird wörtlich angezeigt.
     *
     * Nur so erfährt der Benutzer, warum abgewiesen wurde — etwa dass die Anzahl Wohnungen zu
     * klein erfasst ist. Eine generische Meldung liesse ihn im Dunkeln.
     */
    it('should show the server error key when saving is rejected', () => {
      nebenkostenServiceSpy.updateAbrechnung.mockReturnValue(
        throwError(() => ({ error: 'NK_FEHLER_ANZAHL_WOHNUNGEN' })));

      component.onSpeichern();

      expect(component.message).toBe('NK_FEHLER_ANZAHL_WOHNUNGEN');
      expect(component.messageType).toBe('error');
    });

    it('should fall back to a generic message when the error carries no key', () => {
      nebenkostenServiceSpy.updateAbrechnung.mockReturnValue(throwError(() => ({})));

      component.onSpeichern();

      expect(component.message).toBe('NK_FEHLER_SPEICHERN');
      expect(component.messageType).toBe('error');
    });

    it('should adopt the server values after saving', () => {
      const antwort = structuredClone(serverDetail) as NkAbrechnungDetail;
      antwort.abrechnung.bezeichnung = 'Vom Server umbenannt';
      nebenkostenServiceSpy.updateAbrechnung.mockReturnValue(of(antwort));

      component.onSpeichern();

      expect(component.kopf.bezeichnung).toBe('Vom Server umbenannt');
    });
  });

  describe('Umlage pro Person', () => {

    it('should default the number of persons to the proposal', () => {
      // "Default = Anzahl Wohnungen": Der Server schlaegt beide Zahlen gleich vor.
      expect(component.kopf.anzahlPersonen).toBe(2);
    });

    /**
     * Eine **neue** Abrechnung: Die Anzahl Personen zieht der Anzahl Wohnungen nach.
     *
     * „Default = Anzahl Wohnungen" heisst der WERT des Feldes, nicht der Vorschlag des Servers.
     * Ohne das Nachziehen blieb die Zahl auf dem Vorschlag stehen — eine Umlage pro Person
     * verteilte dann anders als eine Umlage pro Wohnung, obwohl noch nichts erfasst war. Genau
     * das hat der E2E-Test aufgedeckt (99 Wohnungen im Test, 9 vorgeschlagene Einheiten).
     */
    it('should let the number of persons follow the apartments on a new billing', () => {
      const vorlage = structuredClone(serverDetail) as NkAbrechnungDetail;
      vorlage.abrechnung.anzahlWohnungen = null;
      vorlage.abrechnung.anzahlPersonen = null;
      vorlage.anzahlWohnungenVorschlag = 9;
      vorlage.anzahlPersonenVorschlag = 9;
      nebenkostenServiceSpy.getVorlage.mockReturnValue(of(vorlage));

      const neu = TestBed.createComponent(NebenkostenAbrechnungFormComponent);
      neu.componentInstance.abrechnungId = null;
      neu.detectChanges();
      expect(neu.componentInstance.kopf.anzahlPersonen).toBe(9);

      neu.componentInstance.kopf.anzahlWohnungen = 99;
      neu.componentInstance.onAnzahlWohnungenChange();

      expect(neu.componentInstance.kopf.anzahlPersonen).toBe(99);
    });

    it('should stop following once the number of persons is set by hand', () => {
      const vorlage = structuredClone(serverDetail) as NkAbrechnungDetail;
      vorlage.abrechnung.anzahlWohnungen = null;
      vorlage.abrechnung.anzahlPersonen = null;
      nebenkostenServiceSpy.getVorlage.mockReturnValue(of(vorlage));

      const neu = TestBed.createComponent(NebenkostenAbrechnungFormComponent);
      neu.componentInstance.abrechnungId = null;
      neu.detectChanges();

      neu.componentInstance.kopf.anzahlPersonen = 5;
      neu.componentInstance.onAnzahlPersonenChange();
      neu.componentInstance.kopf.anzahlWohnungen = 99;
      neu.componentInstance.onAnzahlWohnungenChange();

      expect(neu.componentInstance.kopf.anzahlPersonen).toBe(5);
    });

    it('should not follow on a saved billing', () => {
      // Die geladene Abrechnung traegt ihre eigene Zahl (Fixture: 2). Eine Korrektur der
      // Wohnungszahl darf die Personenumlage nicht stillschweigend verschieben.
      component.kopf.anzahlWohnungen = 99;
      component.onAnzahlWohnungenChange();

      expect(component.kopf.anzahlPersonen).toBe(2);
    });

    it('should reject saving without a number of persons', () => {
      component.kopf.anzahlPersonen = null;
      expect(component.istGueltig()).toBe(false);
    });

    it('should reject a number of persons below one', () => {
      component.kopf.anzahlPersonen = 0;
      expect(component.istGueltig()).toBe(false);
    });

    it('should create a person entry with the default on first access', () => {
      const eintrag = component.personFuer(4711);

      expect(eintrag.anzahlPersonen).toBe(1);
      expect(component.personen).toContain(eintrag);
    });

    it('should return the same entry on repeated access', () => {
      const erste = component.personFuer(4711);
      erste.anzahlPersonen = 3;

      // Sonst verloere ngModel den eingegebenen Wert bei der naechsten Neuberechnung.
      expect(component.personFuer(4711)).toBe(erste);
      expect(component.personFuer(4711).anzahlPersonen).toBe(3);
    });

    it('should show the per-tenant field only with a per-person item', () => {
      component.positionen = [];
      expect(component.hatPersonenumlage).toBe(false);

      component.positionen = [leerePosition(NkPositionsart.UMLAGE_PERSON)];
      expect(component.hatPersonenumlage).toBe(true);
    });

    it('should not report a per-person item for a plain allocation', () => {
      component.positionen = [leerePosition(NkPositionsart.UMLAGE)];
      expect(component.hatPersonenumlage).toBe(false);
    });

    it('should offer the new type in the dropdown', () => {
      expect(component.positionsarten).toContain(NkPositionsart.UMLAGE_PERSON);
    });

    it('should give the new type a unit like a plain allocation', () => {
      // UMLAGE_PERSON braucht wie UMLAGE eine Mengeneinheit - der CHECK-Constraint verlangt sie.
      expect(leerePosition(NkPositionsart.UMLAGE_PERSON).einheit).not.toBeNull();
    });

    it('should feed the person numbers into the preview', () => {
      component.positionen = [{
        ...leerePosition(NkPositionsart.UMLAGE_PERSON),
        id: 77, bezeichnung: 'Gruenabfuhr', totalbetrag: 1000
      }];
      component.kopf.anzahlPersonen = 4;
      component.personFuer(100).anzahlPersonen = 4;

      component.rechne();

      // Ein Mieter mit 4 von 4 Personen traegt den ganzen Betrag.
      const zeile = component.berechnung?.mieter[0].zeilen[0];
      expect(zeile?.betrag).toBe(1000);
      expect(component.berechnung?.mieter[0].personenTage).toBe(4 * 365);
    });
  });
});
