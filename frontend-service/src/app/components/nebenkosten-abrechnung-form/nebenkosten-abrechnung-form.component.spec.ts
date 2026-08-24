import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { createSpyObj, SpyObj } from '../../../testing/spy';
import { NebenkostenAbrechnungFormComponent } from './nebenkosten-abrechnung-form.component';
import { NebenkostenService } from '../../services/nebenkosten.service';
import { TranslationService } from '../../services/translation.service';
import { NkAbrechnungDetail } from '../../models/nebenkosten.model';

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
    anzahlWohnungenVorschlag: 2,
    berechnung: {
      nenner: 730,
      summeTage: 365,
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

      // Unten bleibt die Schaltflaeche stehen, aber gesperrt - oben verschwindet sie mit der
      // ganzen Zeile, zu der auch "Position hinzufuegen" gehoert.
      const buttons = speichernButtons();
      expect(buttons.length).toBe(1);
      expect(buttons[0].disabled).toBe(true);
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

    it('should report the errors of a completely untouched template', () => {
      // Die Vorlage einer neuen Abrechnung kommt serverfoermig: bezeichnung, datumVon und
      // datumBis sind `null`, nicht ''. Ohne Normalisierung wirft `bezeichnung.trim()` und der
      // Klick auf Speichern bliebe ohne jede sichtbare Wirkung.
      const vorlage = {
        abrechnung: {
          id: null, bezeichnung: null, datumVon: null, datumBis: null,
          anzahlWohnungen: null, abgerechnet: false
        },
        positionen: [], zusaetze: [], akonto: [], berechnung: null,
        anzahlWohnungenVorschlag: null
      };
      nebenkostenServiceSpy.getVorlage.mockReturnValue(of(vorlage as any));
      component.abrechnungId = null;
      component.ngOnInit();

      expect(() => component.onSpeichern()).not.toThrow();
      expect(component.message).toBe('NK_FEHLER_EINGABEN');
      expect(component.istGueltig()).toBe(false);
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
});
