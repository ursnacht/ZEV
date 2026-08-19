import { createSpyObj, SpyObj } from '../../../testing/spy';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { fakeAsync, tick } from '../../../testing/fake-async';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TarifpositionListComponent } from './tarifposition-list.component';
import { TarifpositionService } from '../../services/tarifposition.service';
import { TarifService } from '../../services/tarif.service';
import { EinheitService } from '../../services/einheit.service';
import { MieterService } from '../../services/mieter.service';
import { TranslationService } from '../../services/translation.service';
import { Erfassungsart, Tarifposition } from '../../models/tarifposition.model';
import { Tarif, TarifTyp } from '../../models/tarif.model';
import { Einheit, EinheitTyp } from '../../models/einheit.model';
import { Mieter } from '../../models/mieter.model';

const HINWEIS_STORAGE_KEY = 'zev.tarifposition.hinweisAusgeblendet';

describe('TarifpositionListComponent', () => {
  let component: TarifpositionListComponent;
  let fixture: ComponentFixture<TarifpositionListComponent>;
  let tarifpositionServiceSpy: SpyObj<TarifpositionService>;
  let tarifServiceSpy: SpyObj<TarifService>;
  let einheitServiceSpy: SpyObj<EinheitService>;
  let mieterServiceSpy: SpyObj<MieterService>;
  let translationServiceSpy: SpyObj<TranslationService>;

  /** Query-Parameter der Route – je Test vor dem Erzeugen der Komponente setzbar. */
  let queryParams: Record<string, string>;

  const mockEinheiten: Einheit[] = [
    { id: 1, name: 'Zwahlen Ladestation', typ: EinheitTyp.LADESTATION, messpunkt: 'RFID-01' },
    { id: 2, name: 'Anders Ladestation', typ: EinheitTyp.LADESTATION, messpunkt: 'RFID-02' }
  ];

  /** Nur Einheit 1 hat einen Mieter – Einheit 2 dient als Ladestation ohne Zuordnung. */
  const mockMieter: Mieter[] = [
    { id: 5, name: 'Zwahlen', mietbeginn: '2020-01-01', einheitIds: [1] }
  ];

  const mockTarife: Tarif[] = [
    {
      id: 3, bezeichnung: 'Ladestrom 2026', tariftyp: TarifTyp.LADESTROM,
      preis: 0.35, gueltigVon: '2026-01-01', gueltigBis: '2026-12-31'
    },
    {
      id: 1, bezeichnung: 'ZEV Tarif', tariftyp: TarifTyp.ZEV,
      preis: 0.195, gueltigVon: '2026-01-01', gueltigBis: '2026-12-31'
    },
    {
      id: 2, bezeichnung: 'VNB Tarif', tariftyp: TarifTyp.VNB,
      preis: 0.342, gueltigVon: '2026-01-01', gueltigBis: '2026-12-31'
    }
  ];

  const mockPositionen: Tarifposition[] = [
    {
      id: 10, einheitId: 1, tarifId: 3, tarifBezeichnung: 'Ladestrom 2026', tarifPreis: 0.35,
      jahr: 2026, quartal: 4, menge: 100, erfassungsart: Erfassungsart.MANUELL, quellReferenz: 'LP-01'
    },
    {
      id: 11, einheitId: 1, tarifId: 3, tarifBezeichnung: 'Ladestrom 2027', tarifPreis: 0.38,
      jahr: 2027, quartal: 1, menge: 50, erfassungsart: Erfassungsart.IMPORT, quellReferenz: 'LP-01'
    }
  ];

  /** Frische Kopien, damit die In-Place-Sortierung der Komponente die Fixtures nicht verändert. */
  const positionenKopie = () => mockPositionen.map(p => ({ ...p }));

  const createComponent = (): void => {
    fixture = TestBed.createComponent(TarifpositionListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  beforeEach(async () => {
    localStorage.clear();
    queryParams = {};

    tarifpositionServiceSpy = createSpyObj<TarifpositionService>('TarifpositionService', [
      'getByEinheit', 'createTarifposition', 'updateTarifposition', 'deleteTarifposition'
    ]);
    tarifServiceSpy = createSpyObj<TarifService>('TarifService', ['getAllTarife']);
    einheitServiceSpy = createSpyObj<EinheitService>('EinheitService', ['getAllEinheiten']);
    mieterServiceSpy = createSpyObj<MieterService>('MieterService', ['getAllMieter']);
    translationServiceSpy = createSpyObj<TranslationService>('TranslationService', ['translate']);

    tarifpositionServiceSpy.getByEinheit.mockImplementation(() => of(positionenKopie()));
    tarifpositionServiceSpy.createTarifposition.mockReturnValue(of(mockPositionen[0]));
    tarifpositionServiceSpy.updateTarifposition.mockReturnValue(of(mockPositionen[0]));
    tarifpositionServiceSpy.deleteTarifposition.mockReturnValue(of(undefined));
    tarifServiceSpy.getAllTarife.mockReturnValue(of(mockTarife));
    einheitServiceSpy.getAllEinheiten.mockReturnValue(of(mockEinheiten));
    mieterServiceSpy.getAllMieter.mockReturnValue(of(mockMieter));
    translationServiceSpy.translate.mockImplementation((key: string) => key);

    const routeStub = {
      snapshot: {
        queryParamMap: {
          get: (key: string) => queryParams[key] ?? null
        }
      }
    };

    await TestBed.configureTestingModule({
      imports: [TarifpositionListComponent],
      providers: [
        { provide: TarifpositionService, useValue: tarifpositionServiceSpy },
        { provide: TarifService, useValue: tarifServiceSpy },
        { provide: EinheitService, useValue: einheitServiceSpy },
        { provide: MieterService, useValue: mieterServiceSpy },
        { provide: TranslationService, useValue: translationServiceSpy },
        { provide: ActivatedRoute, useValue: routeStub }
      ]
    }).compileComponents();

    createComponent();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should load ladestationen on init', () => {
      expect(einheitServiceSpy.getAllEinheiten).toHaveBeenCalled();
      expect(component.ladestationen.length).toBe(2);
    });

    it('should sort the ladestationen by name', () => {
      expect(component.ladestationen[0].name).toBe('Anders Ladestation');
      expect(component.ladestationen[1].name).toBe('Zwahlen Ladestation');
    });

    it('should load tarife on init', () => {
      expect(tarifServiceSpy.getAllTarife).toHaveBeenCalled();
    });

    it('should only offer manually captured tariff types', () => {
      // Erfassbar ist LADESTROM; ZEV, VNB und GRUNDGEBUEHR nicht - deren Mengen kommen aus
      // Messwerten bzw. der Laufzeit (Begruendung siehe TarifTyp.GRUNDGEBUEHR).
      const typen = component.tarife.map(t => t.tariftyp);
      expect(typen).toContain(TarifTyp.LADESTROM);
      expect(typen).not.toContain(TarifTyp.GRUNDGEBUEHR);
      expect(typen).not.toContain(TarifTyp.ZEV);
      expect(typen).not.toContain(TarifTyp.VNB);
    });

    it('should not preselect an einheit without query parameter', () => {
      expect(component.selectedEinheitId).toBeNull();
      expect(tarifpositionServiceSpy.getByEinheit).not.toHaveBeenCalled();
      expect(component.positionen).toEqual([]);
    });

    it('should not show form initially', () => {
      expect(component.showForm).toBe(false);
    });

    it('should default to quartal sorted descending (newest first)', () => {
      expect(component.sortColumn).toBe('quartal');
      expect(component.sortDirection).toBe('desc');
    });

    it('should have menu items for edit, copy and delete', () => {
      expect(component.menuItems.length).toBe(3);
      expect(component.menuItems[0].action).toBe('edit');
      expect(component.menuItems[1].action).toBe('copy');
      expect(component.menuItems[2].action).toBe('delete');
    });

    it('should show error message when loading einheiten fails', () => {
      einheitServiceSpy.getAllEinheiten.mockReturnValue(throwError(() => new Error('Network error')));
      component.loadLadestationen();
      expect(component.message).toBe('FEHLER_LADEN_EINHEITEN');
      expect(component.messageType).toBe('error');
    });

    it('should show error message when loading tarife fails', () => {
      tarifServiceSpy.getAllTarife.mockReturnValue(throwError(() => new Error('Network error')));
      component.loadTarife();
      expect(component.message).toBe('FEHLER_LADEN_TARIFE');
      expect(component.messageType).toBe('error');
    });
  });

  describe('einheiten-auswahl', () => {
    it('should offer only einheiten of type LADESTATION', () => {
      // Einzig die Seite Tarifpositionen zeigt ausschliesslich Ladestationen
      // (Specs/Ladestationen.md FR-3).
      einheitServiceSpy.getAllEinheiten.mockReturnValue(of([
        { id: 5, name: 'Wohnung A', typ: EinheitTyp.CONSUMER, messpunkt: 'MP-001' },
        { id: 6, name: 'Solaranlage', typ: EinheitTyp.PRODUCER },
        { id: 7, name: 'Bezug', typ: EinheitTyp.BEZUG, messpunkt: 'MP-BEZUG' },
        { id: 8, name: 'Rücklieferung', typ: EinheitTyp.RUECKLIEFERUNG, messpunkt: 'MP-BEZUG' },
        ...mockEinheiten
      ]));

      component.loadLadestationen();

      expect(component.ladestationen.map(e => e.id)).toEqual([2, 1]);
      expect(component.ladestationen.every(e => e.typ === EinheitTyp.LADESTATION)).toBe(true);
    });

    it('should leave the list empty when no ladestation exists', () => {
      einheitServiceSpy.getAllEinheiten.mockReturnValue(of([
        { id: 5, name: 'Wohnung A', typ: EinheitTyp.CONSUMER }
      ]));

      component.loadLadestationen();

      expect(component.ladestationen).toEqual([]);
      expect(component.selectedEinheitId).toBeNull();
    });

    it('should ignore a query parameter pointing at a non-ladestation', () => {
      einheitServiceSpy.getAllEinheiten.mockReturnValue(of([
        { id: 5, name: 'Wohnung A', typ: EinheitTyp.CONSUMER },
        ...mockEinheiten
      ]));
      queryParams['einheitId'] = '5';
      createComponent();

      expect(component.selectedEinheitId).toBeNull();
      expect(tarifpositionServiceSpy.getByEinheit).not.toHaveBeenCalled();
    });
  });

  describe('selectedEinheitMesspunkt', () => {
    it('should return the RFID of the selected ladestation', () => {
      // Die Quell-Referenz wird aus dem messpunkt der Einheit vorbelegt
      // (Specs/Ladestationen.md FR-1.3).
      component.selectedEinheitId = 1;
      expect(component.selectedEinheitMesspunkt).toBe('RFID-01');
    });

    it('should return an empty string when nothing is selected', () => {
      component.selectedEinheitId = null;
      expect(component.selectedEinheitMesspunkt).toBe('');
    });

    it('should return an empty string for an unknown einheit', () => {
      component.selectedEinheitId = 999;
      expect(component.selectedEinheitMesspunkt).toBe('');
    });

    it('should return an empty string when the ladestation has no messpunkt', () => {
      component.ladestationen = [{ id: 1, name: 'Ohne RFID', typ: EinheitTyp.LADESTATION }];
      component.selectedEinheitId = 1;
      expect(component.selectedEinheitMesspunkt).toBe('');
    });
  });

  describe('mengeneinheit', () => {
    // Die Liste mischt beide Typen, die Einheit steht deshalb je Zeile
    // (Specs/Ladestromtarif.md FR-6).

    it('should use kWh for a ladestrom position', () => {
      expect(component.mengeneinheit({ ...mockPositionen[0], tarifTyp: TarifTyp.LADESTROM })).toBe('KWH');
    });

    it('should use months for a grundgebuehr position', () => {
      expect(component.mengeneinheit({ ...mockPositionen[0], tarifTyp: TarifTyp.GRUNDGEBUEHR })).toBe('MONATE');
    });

    it('should fall back to kWh when the type is missing', () => {
      expect(component.mengeneinheit({ ...mockPositionen[0], tarifTyp: undefined })).toBe('KWH');
    });

    it('should show months without decimals and kWh with three', () => {
      expect(component.mengeNachkommastellen(
        { ...mockPositionen[0], tarifTyp: TarifTyp.GRUNDGEBUEHR })).toBe(0);
      expect(component.mengeNachkommastellen(
        { ...mockPositionen[0], tarifTyp: TarifTyp.LADESTROM })).toBe(3);
    });
  });

  describe('einheitOhneMieter', () => {
    // Specs/Ladestationen.md, Edge Case "Ladestations-Einheit ohne zugeordneten Mieter":
    // Positionen sind erfassbar, erscheinen aber auf keiner Rechnung - die entsteht je Mieter.

    it('should load the mieter assignments on init', () => {
      expect(mieterServiceSpy.getAllMieter).toHaveBeenCalled();
    });

    it('should be false when no einheit is selected', () => {
      component.selectedEinheitId = null;
      expect(component.einheitOhneMieter).toBe(false);
    });

    it('should be false for an einheit with an assigned mieter', () => {
      component.selectedEinheitId = 1;
      expect(component.einheitOhneMieter).toBe(false);
    });

    it('should be true for an einheit without any assigned mieter', () => {
      component.selectedEinheitId = 2;
      expect(component.einheitOhneMieter).toBe(true);
    });

    it('should recognise an einheit assigned as second unit of a mieter', () => {
      // Ein Mieter kann Wohnung UND Ladestation haben - die zweite Zuordnung zaehlt genauso
      mieterServiceSpy.getAllMieter.mockReturnValue(of([
        { id: 5, name: 'Zwahlen', mietbeginn: '2020-01-01', einheitIds: [9, 2] }
      ]));
      createComponent();
      component.selectedEinheitId = 2;
      expect(component.einheitOhneMieter).toBe(false);
    });

    it('should stay silent when the mieter could not be loaded', () => {
      // Der Hinweis ist Zusatzinformation: lieber keiner als ein falscher
      mieterServiceSpy.getAllMieter.mockReturnValue(throwError(() => new Error('403')));
      createComponent();
      component.selectedEinheitId = 2;
      expect(component.einheitOhneMieter).toBe(false);
      expect(component.message).toBe('');
    });

    it('should render the hint for an einheit without mieter', () => {
      // Der Uebersetzungsschluessel existierte, wurde aber nirgends verwendet - deshalb
      // hier gegen das gerenderte Template pruefen und nicht nur gegen den Getter.
      component.selectedEinheitId = 2;
      fixture.detectChanges();
      const text = fixture.nativeElement.textContent as string;
      expect(text).toContain('TARIFPOSITION_EINHEIT_OHNE_MIETER_HINT');
    });

    it('should not render the hint for an einheit with mieter', () => {
      component.selectedEinheitId = 1;
      fixture.detectChanges();
      const text = fixture.nativeElement.textContent as string;
      expect(text).not.toContain('TARIFPOSITION_EINHEIT_OHNE_MIETER_HINT');
    });
  });

  describe('einheit preselection via query parameter', () => {
    it('should preselect the einheit from ?einheitId and load the positions', () => {
      queryParams['einheitId'] = '1';
      createComponent();

      expect(component.selectedEinheitId).toBe(1);
      expect(tarifpositionServiceSpy.getByEinheit).toHaveBeenCalledWith(1);
      expect(component.positionen.length).toBe(2);
    });

    it('should ignore an unknown einheitId', () => {
      queryParams['einheitId'] = '999';
      createComponent();

      expect(component.selectedEinheitId).toBeNull();
      expect(tarifpositionServiceSpy.getByEinheit).not.toHaveBeenCalled();
    });

    it('should ignore a non-numeric einheitId', () => {
      queryParams['einheitId'] = 'abc';
      createComponent();

      expect(component.selectedEinheitId).toBeNull();
      expect(tarifpositionServiceSpy.getByEinheit).not.toHaveBeenCalled();
    });
  });

  describe('mehrfachverrechnungs-hinweis', () => {
    it('should be visible by default', () => {
      expect(component.hinweisSichtbar).toBe(true);
    });

    it('should hide the hinweis on dismiss', () => {
      component.dismissHinweis();
      expect(component.hinweisSichtbar).toBe(false);
    });

    it('should remember the dismissal in localStorage', () => {
      component.dismissHinweis();
      expect(localStorage.getItem(HINWEIS_STORAGE_KEY)).toBe('true');
    });

    it('should stay hidden on the next visit', () => {
      component.dismissHinweis();
      createComponent();
      expect(component.hinweisSichtbar).toBe(false);
    });

    it('should stay visible when localStorage holds another value', () => {
      localStorage.setItem(HINWEIS_STORAGE_KEY, 'false');
      createComponent();
      expect(component.hinweisSichtbar).toBe(true);
    });

    it('should still hide the hinweis when localStorage is unavailable', () => {
      const setItemSpy = vi.spyOn(Storage.prototype, 'setItem')
        .mockImplementation(() => { throw new Error('localStorage disabled'); });

      expect(() => component.dismissHinweis()).not.toThrow();
      expect(component.hinweisSichtbar).toBe(false);

      setItemSpy.mockRestore();
    });

    it('should show the hinweis when reading localStorage throws', () => {
      const getItemSpy = vi.spyOn(Storage.prototype, 'getItem')
        .mockImplementation(() => { throw new Error('localStorage disabled'); });

      createComponent();
      expect(component.hinweisSichtbar).toBe(true);

      getItemSpy.mockRestore();
    });
  });

  describe('loadPositionen', () => {
    it('should clear positions and skip the request when no einheit is selected', () => {
      component.positionen = positionenKopie();
      component.selectedEinheitId = null;

      component.loadPositionen();

      expect(component.positionen).toEqual([]);
      expect(tarifpositionServiceSpy.getByEinheit).not.toHaveBeenCalled();
    });

    it('should load the positions of the selected einheit', () => {
      component.selectedEinheitId = 1;
      component.loadPositionen();

      expect(tarifpositionServiceSpy.getByEinheit).toHaveBeenCalledWith(1);
      expect(component.positionen.length).toBe(2);
    });

    it('should show error message on failure', () => {
      tarifpositionServiceSpy.getByEinheit.mockReturnValue(throwError(() => new Error('Network error')));
      component.selectedEinheitId = 1;

      component.loadPositionen();

      expect(component.message).toBe('FEHLER_LADEN_TARIFPOSITIONEN');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onEinheitChange', () => {
    beforeEach(() => {
      component.selectedEinheitId = 1;
    });

    it('should hide the form and clear the selected position', () => {
      component.showForm = true;
      component.selectedPosition = { ...mockPositionen[0] };

      component.onEinheitChange();

      expect(component.showForm).toBe(false);
      expect(component.selectedPosition).toBeNull();
    });

    it('should clear a pending message', () => {
      component.message = 'FEHLER_LADEN_TARIFPOSITIONEN';
      component.messagePersistent = true;

      component.onEinheitChange();

      expect(component.message).toBe('');
      expect(component.messagePersistent).toBe(false);
    });

    it('should reload the positions of the new einheit', () => {
      component.onEinheitChange();
      expect(tarifpositionServiceSpy.getByEinheit).toHaveBeenCalledWith(1);
    });
  });

  describe('onCreateNew', () => {
    it('should show the form without a selected position', () => {
      component.selectedPosition = { ...mockPositionen[0] };
      component.onCreateNew();
      expect(component.selectedPosition).toBeNull();
      expect(component.showForm).toBe(true);
    });
  });

  describe('onEdit', () => {
    it('should show the form with the position', () => {
      component.onEdit(mockPositionen[0]);
      expect(component.selectedPosition).toEqual(mockPositionen[0]);
      expect(component.showForm).toBe(true);
    });

    it('should create a copy of the position (not the reference)', () => {
      component.onEdit(mockPositionen[0]);
      expect(component.selectedPosition).not.toBe(mockPositionen[0]);
    });

    it('should keep the id so the form updates the existing position', () => {
      component.onEdit(mockPositionen[0]);
      expect(component.selectedPosition!.id).toBe(10);
    });
  });

  describe('onCopy', () => {
    it('should show the form without an id', () => {
      component.onCopy(mockPositionen[0]);
      expect(component.showForm).toBe(true);
      expect(component.selectedPosition!.id).toBeUndefined();
    });

    it('should copy all values except the id', () => {
      const original = mockPositionen[0];
      component.onCopy(original);

      expect(component.selectedPosition!.einheitId).toBe(original.einheitId);
      expect(component.selectedPosition!.tarifId).toBe(original.tarifId);
      expect(component.selectedPosition!.jahr).toBe(original.jahr);
      expect(component.selectedPosition!.quartal).toBe(original.quartal);
      expect(component.selectedPosition!.menge).toBe(original.menge);
      expect(component.selectedPosition!.quellReferenz).toBe(original.quellReferenz);
    });

    it('should not modify the source position', () => {
      const original = { ...mockPositionen[0] };
      component.onCopy(mockPositionen[0]);
      expect(mockPositionen[0]).toEqual(original);
      expect(component.selectedPosition).not.toBe(mockPositionen[0]);
    });
  });

  describe('onDelete', () => {
    beforeEach(() => {
      component.selectedEinheitId = 1;
      vi.spyOn(window, 'confirm').mockReturnValue(true);
    });

    it('should do nothing when the id is undefined', () => {
      component.onDelete(undefined);
      expect(window.confirm).not.toHaveBeenCalled();
      expect(tarifpositionServiceSpy.deleteTarifposition).not.toHaveBeenCalled();
    });

    it('should ask for confirmation', () => {
      component.onDelete(10);
      expect(window.confirm).toHaveBeenCalled();
      expect(translationServiceSpy.translate).toHaveBeenCalledWith('CONFIRM_DELETE_TARIFPOSITION');
    });

    it('should delete the position on confirm', () => {
      component.onDelete(10);
      expect(tarifpositionServiceSpy.deleteTarifposition).toHaveBeenCalledWith(10);
    });

    it('should show a success message and reload', () => {
      tarifpositionServiceSpy.getByEinheit.mockClear();
      component.onDelete(10);
      expect(component.message).toBe('TARIFPOSITION_GELOESCHT');
      expect(component.messageType).toBe('success');
      expect(tarifpositionServiceSpy.getByEinheit).toHaveBeenCalled();
    });

    it('should not delete when the user cancels', () => {
      (window.confirm as unknown as ReturnType<typeof vi.fn>).mockReturnValue(false);
      component.onDelete(10);
      expect(tarifpositionServiceSpy.deleteTarifposition).not.toHaveBeenCalled();
    });

    it('should show the server error message on failure', () => {
      tarifpositionServiceSpy.deleteTarifposition
        .mockReturnValue(throwError(() => ({ error: 'TARIFPOSITION_REFERENZIERT' })));
      component.onDelete(10);
      expect(component.message).toBe('TARIFPOSITION_REFERENZIERT');
      expect(component.messageType).toBe('error');
    });

    it('should fall back to the generic error message', () => {
      tarifpositionServiceSpy.deleteTarifposition
        .mockReturnValue(throwError(() => new Error('Network error')));
      component.onDelete(10);
      expect(component.message).toBe('FEHLER_LOESCHEN_TARIFPOSITION');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onMenuAction', () => {
    it('should call onEdit for the edit action', () => {
      vi.spyOn(component, 'onEdit').mockImplementation(() => {});
      component.onMenuAction('edit', mockPositionen[0]);
      expect(component.onEdit).toHaveBeenCalledWith(mockPositionen[0]);
    });

    it('should call onCopy for the copy action', () => {
      vi.spyOn(component, 'onCopy').mockImplementation(() => {});
      component.onMenuAction('copy', mockPositionen[0]);
      expect(component.onCopy).toHaveBeenCalledWith(mockPositionen[0]);
    });

    it('should call onDelete with the id for the delete action', () => {
      vi.spyOn(component, 'onDelete').mockImplementation(() => {});
      component.onMenuAction('delete', mockPositionen[0]);
      expect(component.onDelete).toHaveBeenCalledWith(10);
    });

    it('should ignore an unknown action', () => {
      vi.spyOn(component, 'onEdit').mockImplementation(() => {});
      vi.spyOn(component, 'onCopy').mockImplementation(() => {});
      vi.spyOn(component, 'onDelete').mockImplementation(() => {});

      component.onMenuAction('unbekannt', mockPositionen[0]);

      expect(component.onEdit).not.toHaveBeenCalled();
      expect(component.onCopy).not.toHaveBeenCalled();
      expect(component.onDelete).not.toHaveBeenCalled();
    });
  });

  describe('onFormSubmit - create', () => {
    const newPosition: Tarifposition = {
      einheitId: 1, tarifId: 3, jahr: 2026, quartal: 2, menge: 42
    };

    beforeEach(() => {
      component.selectedEinheitId = 1;
    });

    it('should call createTarifposition when the position has no id', () => {
      component.onFormSubmit(newPosition);
      expect(tarifpositionServiceSpy.createTarifposition).toHaveBeenCalledWith(newPosition);
      expect(tarifpositionServiceSpy.updateTarifposition).not.toHaveBeenCalled();
    });

    it('should hide the form and show a success message', () => {
      component.showForm = true;
      component.onFormSubmit(newPosition);
      expect(component.showForm).toBe(false);
      expect(component.message).toBe('TARIFPOSITION_ERSTELLT');
      expect(component.messageType).toBe('success');
    });

    it('should reload the positions after create', () => {
      tarifpositionServiceSpy.getByEinheit.mockClear();
      component.onFormSubmit(newPosition);
      expect(tarifpositionServiceSpy.getByEinheit).toHaveBeenCalledWith(1);
    });

    it('should show the duplicate message from the server and keep the form open', () => {
      tarifpositionServiceSpy.createTarifposition
        .mockReturnValue(throwError(() => ({ error: 'TARIFPOSITION_DUPLIKAT' })));
      component.showForm = true;

      component.onFormSubmit(newPosition);

      expect(component.message).toBe('TARIFPOSITION_DUPLIKAT');
      expect(component.messageType).toBe('error');
      expect(component.showForm).toBe(true);
    });

    it('should fall back to the generic error message', () => {
      tarifpositionServiceSpy.createTarifposition
        .mockReturnValue(throwError(() => new Error('Network error')));
      component.onFormSubmit(newPosition);
      expect(component.message).toBe('FEHLER_ERSTELLEN_TARIFPOSITION');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onFormSubmit - update', () => {
    const existingPosition: Tarifposition = { ...mockPositionen[0], menge: 250 };

    beforeEach(() => {
      component.selectedEinheitId = 1;
    });

    it('should call updateTarifposition when the position has an id', () => {
      component.onFormSubmit(existingPosition);
      expect(tarifpositionServiceSpy.updateTarifposition).toHaveBeenCalledWith(10, existingPosition);
      expect(tarifpositionServiceSpy.createTarifposition).not.toHaveBeenCalled();
    });

    it('should hide the form and show a success message', () => {
      component.showForm = true;
      component.onFormSubmit(existingPosition);
      expect(component.showForm).toBe(false);
      expect(component.message).toBe('TARIFPOSITION_AKTUALISIERT');
      expect(component.messageType).toBe('success');
    });

    it('should reload the positions after update', () => {
      tarifpositionServiceSpy.getByEinheit.mockClear();
      component.onFormSubmit(existingPosition);
      expect(tarifpositionServiceSpy.getByEinheit).toHaveBeenCalledWith(1);
    });

    it('should show the server error message and keep the form open', () => {
      tarifpositionServiceSpy.updateTarifposition
        .mockReturnValue(throwError(() => ({ error: 'TARIFPOSITION_DUPLIKAT' })));
      component.showForm = true;

      component.onFormSubmit(existingPosition);

      expect(component.message).toBe('TARIFPOSITION_DUPLIKAT');
      expect(component.showForm).toBe(true);
    });

    it('should fall back to the generic error message', () => {
      tarifpositionServiceSpy.updateTarifposition
        .mockReturnValue(throwError(() => new Error('Network error')));
      component.onFormSubmit(existingPosition);
      expect(component.message).toBe('FEHLER_AKTUALISIEREN_TARIFPOSITION');
    });
  });

  describe('onFormCancel', () => {
    it('should hide the form and clear the selected position', () => {
      component.showForm = true;
      component.selectedPosition = { ...mockPositionen[0] };

      component.onFormCancel();

      expect(component.showForm).toBe(false);
      expect(component.selectedPosition).toBeNull();
    });
  });

  describe('onSort', () => {
    beforeEach(() => {
      component.positionen = positionenKopie();
    });

    it('should toggle the direction when clicking the same column', () => {
      component.sortColumn = 'menge';
      component.sortDirection = 'asc';
      component.onSort('menge');
      expect(component.sortDirection).toBe('desc');
    });

    it('should start ascending when switching to another column', () => {
      component.sortColumn = 'quartal';
      component.sortDirection = 'desc';
      component.onSort('menge');
      expect(component.sortColumn).toBe('menge');
      expect(component.sortDirection).toBe('asc');
    });

    it('should sort by quartal chronologically across year boundaries', () => {
      component.sortColumn = null;
      component.sortDirection = 'asc';
      component.onSort('quartal');

      expect(component.positionen[0].jahr).toBe(2026);
      expect(component.positionen[0].quartal).toBe(4);
      expect(component.positionen[1].jahr).toBe(2027);
      expect(component.positionen[1].quartal).toBe(1);
    });

    it('should sort by quartal descending across year boundaries', () => {
      component.sortColumn = 'quartal';
      component.sortDirection = 'asc';
      component.onSort('quartal');

      expect(component.positionen[0].jahr).toBe(2027);
      expect(component.positionen[1].jahr).toBe(2026);
    });

    it('should sort by menge', () => {
      component.sortColumn = null;
      component.onSort('menge');
      expect(component.positionen.map(p => p.menge)).toEqual([50, 100]);
    });

    it('should sort by tarifPreis', () => {
      component.sortColumn = null;
      component.onSort('tarifPreis');
      expect(component.positionen.map(p => p.tarifPreis)).toEqual([0.35, 0.38]);
    });

    it('should sort by the calculated betrag', () => {
      component.sortColumn = null;
      component.onSort('betrag');
      // 50 * 0.38 = 19 vor 100 * 0.35 = 35
      expect(component.positionen[0].id).toBe(11);
      expect(component.positionen[1].id).toBe(10);
    });

    it('should sort by tarifBezeichnung case-insensitively', () => {
      component.positionen = [
        { ...mockPositionen[0], tarifBezeichnung: 'zeta' },
        { ...mockPositionen[1], tarifBezeichnung: 'Alpha' }
      ];
      component.sortColumn = null;
      component.onSort('tarifBezeichnung');
      expect(component.positionen.map(p => p.tarifBezeichnung)).toEqual(['Alpha', 'zeta']);
    });

    it('should sort by erfassungsart', () => {
      component.sortColumn = null;
      component.onSort('erfassungsart');
      expect(component.positionen.map(p => p.erfassungsart))
        .toEqual([Erfassungsart.IMPORT, Erfassungsart.MANUELL]);
    });

    it('should put positions without a value last', () => {
      component.positionen = [
        { ...mockPositionen[0], erfassungsart: undefined },
        { ...mockPositionen[1], erfassungsart: Erfassungsart.MANUELL }
      ];
      component.sortColumn = null;
      component.onSort('erfassungsart');
      expect(component.positionen[0].erfassungsart).toBe(Erfassungsart.MANUELL);
      expect(component.positionen[1].erfassungsart).toBeUndefined();
    });
  });

  describe('sort persistence', () => {
    beforeEach(() => {
      component.selectedEinheitId = 1;
      vi.spyOn(window, 'confirm').mockReturnValue(true);
    });

    it('should keep the chosen sorting after loading', () => {
      component.sortColumn = 'menge';
      component.sortDirection = 'asc';

      component.loadPositionen();

      expect(component.positionen.map(p => p.menge)).toEqual([50, 100]);
    });

    it('should keep the chosen sorting after creating a position', () => {
      component.sortColumn = 'menge';
      component.sortDirection = 'asc';

      component.onFormSubmit({ einheitId: 1, tarifId: 3, jahr: 2026, quartal: 1, menge: 1 });

      expect(component.sortColumn).toBe('menge');
      expect(component.positionen.map(p => p.menge)).toEqual([50, 100]);
    });

    it('should keep the chosen sorting after deleting a position', () => {
      component.sortColumn = 'menge';
      component.sortDirection = 'desc';

      component.onDelete(10);

      expect(component.sortDirection).toBe('desc');
      expect(component.positionen.map(p => p.menge)).toEqual([100, 50]);
    });

    it('should leave the order untouched when no sort column is set', () => {
      component.sortColumn = null;

      component.loadPositionen();

      expect(component.positionen.map(p => p.id)).toEqual([10, 11]);
    });
  });

  describe('berechneBetrag', () => {
    it('should multiply menge with the tarif price', () => {
      expect(component.berechneBetrag(mockPositionen[0])).toBeCloseTo(35, 5);
    });

    it('should return 0 when the price is missing', () => {
      expect(component.berechneBetrag({ ...mockPositionen[0], tarifPreis: undefined })).toBe(0);
    });

    it('should return 0 when the menge is missing', () => {
      expect(component.berechneBetrag({ ...mockPositionen[0], menge: undefined as unknown as number }))
        .toBe(0);
    });

    it('should return 0 for a zero menge', () => {
      expect(component.berechneBetrag({ ...mockPositionen[0], menge: 0 })).toBe(0);
    });
  });

  describe('fehlertext', () => {
    const newPosition: Tarifposition = { einheitId: 1, tarifId: 3, jahr: 2026, quartal: 2, menge: 42 };

    beforeEach(() => {
      component.selectedEinheitId = 1;
    });

    it('should render the bean-validation map instead of [object Object]', () => {
      // Die Bean-Validation des DTO liefert `{ feld: meldung }` - direkt angezeigt ergaebe
      // das "[object Object]".
      tarifpositionServiceSpy.createTarifposition.mockReturnValue(throwError(() => ({
        error: { menge: 'muss groesser oder gleich 0 sein', quartal: 'muss zwischen 1 und 4 liegen' }
      })));

      component.onFormSubmit(newPosition);

      expect(component.message).not.toContain('[object Object]');
      expect(component.message).toContain('muss groesser oder gleich 0 sein');
      expect(component.message).toContain('muss zwischen 1 und 4 liegen');
      expect(component.message).toContain('; ');
    });

    it('should render a single field message without a separator', () => {
      tarifpositionServiceSpy.createTarifposition.mockReturnValue(throwError(() => ({
        error: { menge: 'muss groesser oder gleich 0 sein' }
      })));

      component.onFormSubmit(newPosition);

      expect(component.message).toBe('muss groesser oder gleich 0 sein');
    });

    it('should pass through a server message wrapped in an error property', () => {
      tarifpositionServiceSpy.createTarifposition.mockReturnValue(throwError(() => ({
        error: { error: 'Tarifposition existiert bereits fuer Q2/2026' }
      })));

      component.onFormSubmit(newPosition);

      expect(component.message).toBe('Tarifposition existiert bereits fuer Q2/2026');
    });

    it('should fall back when the body holds no string values', () => {
      tarifpositionServiceSpy.createTarifposition.mockReturnValue(throwError(() => ({
        error: { status: 400, details: { feld: 'menge' } }
      })));

      component.onFormSubmit(newPosition);

      expect(component.message).toBe('FEHLER_ERSTELLEN_TARIFPOSITION');
    });

    it('should fall back for an empty body object', () => {
      tarifpositionServiceSpy.createTarifposition.mockReturnValue(throwError(() => ({ error: {} })));

      component.onFormSubmit(newPosition);

      expect(component.message).toBe('FEHLER_ERSTELLEN_TARIFPOSITION');
    });

    it('should fall back for a blank string body', () => {
      tarifpositionServiceSpy.createTarifposition.mockReturnValue(throwError(() => ({ error: '   ' })));

      component.onFormSubmit(newPosition);

      expect(component.message).toBe('FEHLER_ERSTELLEN_TARIFPOSITION');
    });

    it('should fall back when the body is null', () => {
      tarifpositionServiceSpy.createTarifposition.mockReturnValue(throwError(() => ({ error: null })));

      component.onFormSubmit(newPosition);

      expect(component.message).toBe('FEHLER_ERSTELLEN_TARIFPOSITION');
    });

    it('should render the validation map on update as well', () => {
      tarifpositionServiceSpy.updateTarifposition.mockReturnValue(throwError(() => ({
        error: { menge: 'muss groesser oder gleich 0 sein' }
      })));

      component.onFormSubmit({ ...mockPositionen[0], menge: -1 });

      expect(component.message).toBe('muss groesser oder gleich 0 sein');
    });

    it('should render the validation map on delete as well', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      tarifpositionServiceSpy.deleteTarifposition.mockReturnValue(throwError(() => ({
        error: { error: 'Position wird noch referenziert' }
      })));

      component.onDelete(10);

      expect(component.message).toBe('Position wird noch referenziert');
    });
  });

  describe('messages', () => {
    beforeEach(() => {
      component.selectedEinheitId = 1;
    });

    it('should auto-dismiss a success message after 5 seconds', fakeAsync(() => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);

      component.onDelete(10);

      expect(component.message).toBe('TARIFPOSITION_GELOESCHT');
      expect(component.messagePersistent).toBe(false);
      tick(5000);
      expect(component.message).toBe('');
    }));

    it('should keep an error message until it is dismissed', fakeAsync(() => {
      tarifpositionServiceSpy.getByEinheit.mockReturnValue(throwError(() => new Error('Network error')));

      component.loadPositionen();

      expect(component.messagePersistent).toBe(true);
      tick(5000);
      expect(component.message).toBe('FEHLER_LADEN_TARIFPOSITIONEN');
    }));

    it('should clear message and persistence flag on dismissMessage', () => {
      component.message = 'FEHLER_LADEN_TARIFPOSITIONEN';
      component.messagePersistent = true;

      component.dismissMessage();

      expect(component.message).toBe('');
      expect(component.messagePersistent).toBe(false);
    });
  });
});
