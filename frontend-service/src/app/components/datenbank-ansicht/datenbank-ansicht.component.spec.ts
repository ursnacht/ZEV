import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { createSpyObj, SpyObj } from '../../../testing/spy';
import { DatenbankAnsichtComponent } from './datenbank-ansicht.component';
import { DatenbankService } from '../../services/datenbank.service';
import { DatenbankFilterHistorieService } from '../../services/datenbank-filter-historie.service';
import { DatenbankAbfrageResponse } from '../../models/datenbank.model';
import { TranslationService } from '../../services/translation.service';

/**
 * Unit-Tests für {@link DatenbankAnsichtComponent}.
 *
 * <p>Schwerpunkt ist das Zusammenspiel von Standard-Filter und Sortierung. Der Standard-Filter
 * bringt seit der Ergänzung um {@code ORDER BY id DESC} eine Sortierklausel im Filtertext mit.
 * Bliebe sie beim Klick auf eine Spaltenüberschrift stehen, hängte das Backend ein
 * <b>zweites</b> {@code ORDER BY} an — ein Syntaxfehler, der als Fehlermeldung in der
 * Oberfläche landet. Genau diese Kante wird hier gepinnt.
 */
describe('DatenbankAnsichtComponent', () => {
  let component: DatenbankAnsichtComponent;
  let fixture: ComponentFixture<DatenbankAnsichtComponent>;
  let datenbankServiceSpy: SpyObj<DatenbankService>;
  let historieServiceSpy: SpyObj<DatenbankFilterHistorieService>;

  const mockAntwort: DatenbankAbfrageResponse = {
    spalten: ['id', 'name', 'org_id'],
    zeilen: [['2', 'Parterre rechts', '2'], ['1', 'Parterre links', '2']],
    seite: 0,
    groesse: 50,
    hatMehr: false
  };

  beforeEach(async () => {
    datenbankServiceSpy = createSpyObj<DatenbankService>('DatenbankService', [
      'getTabellen', 'getStandardFilter', 'abfrage'
    ]);
    datenbankServiceSpy.getTabellen.mockReturnValue(of(['einheit', 'mieter']));
    datenbankServiceSpy.getStandardFilter.mockReturnValue(of('org_id = 42 ORDER BY id DESC'));
    datenbankServiceSpy.abfrage.mockReturnValue(of(mockAntwort));

    historieServiceSpy = createSpyObj<DatenbankFilterHistorieService>(
      'DatenbankFilterHistorieService', ['getHistorie', 'addFilter']);
    historieServiceSpy.getHistorie.mockReturnValue([]);
    historieServiceSpy.addFilter.mockReturnValue([]);

    await TestBed.configureTestingModule({
      imports: [DatenbankAnsichtComponent],
      providers: [
        { provide: DatenbankService, useValue: datenbankServiceSpy },
        { provide: DatenbankFilterHistorieService, useValue: historieServiceSpy },
        { provide: TranslationService, useValue: { translate: (k: string) => k } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DatenbankAnsichtComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should load the table list on init', () => {
      expect(datenbankServiceSpy.getTabellen).toHaveBeenCalled();
      expect(component.tabellen).toEqual(['einheit', 'mieter']);
    });

    it('should show an error message when the table list fails', () => {
      datenbankServiceSpy.getTabellen.mockReturnValue(throwError(() => new Error('offline')));

      component.loadTabellen();

      expect(component.messageType).toBe('error');
    });
  });

  describe('onTabelleChange', () => {
    it('should prefill the standard filter including the default sort', () => {
      component.selectedTabelle = 'einheit';

      component.onTabelleChange();

      expect(datenbankServiceSpy.getStandardFilter).toHaveBeenCalledWith('einheit');
      expect(component.whereClause).toBe('org_id = 42 ORDER BY id DESC');
    });

    it('should discard the previous result', () => {
      component.result = mockAntwort;
      component.selectedTabelle = 'einheit';

      component.onTabelleChange();

      expect(component.result).toBeNull();
    });

    it('should leave the filter empty when no table is selected', () => {
      component.selectedTabelle = '';

      component.onTabelleChange();

      expect(component.whereClause).toBe('');
      expect(datenbankServiceSpy.getStandardFilter).not.toHaveBeenCalled();
    });

    it('should keep the filter empty when the standard filter cannot be loaded', () => {
      // Der Standard-Filter ist optional - ein Fehler darf die Ansicht nicht blockieren
      datenbankServiceSpy.getStandardFilter.mockReturnValue(throwError(() => new Error('403')));
      component.selectedTabelle = 'einheit';

      component.onTabelleChange();

      expect(component.whereClause).toBe('');
      expect(component.messageType).not.toBe('error');
    });
  });

  describe('onSort', () => {
    beforeEach(() => {
      component.selectedTabelle = 'einheit';
      component.whereClause = 'org_id = 42 ORDER BY id DESC';
    });

    it('should strip the ORDER BY from the filter before sorting', () => {
      component.onSort('name');

      // Sonst stuenden zwei ORDER BY im erzeugten SQL
      expect(component.whereClause).toBe('org_id = 42');
      expect(datenbankServiceSpy.abfrage).toHaveBeenCalledWith(expect.objectContaining({
        where: 'org_id = 42',
        sortSpalte: 'name',
        sortRichtung: 'ASC'
      }));
    });

    it('should send no filter at all when it consisted only of the sort clause', () => {
      // Tabellen ohne org_id bekommen einen Filter, der nur aus der Sortierung besteht
      component.whereClause = 'ORDER BY id DESC';

      component.onSort('name');

      expect(component.whereClause).toBe('');
      expect(datenbankServiceSpy.abfrage).toHaveBeenCalledWith(expect.objectContaining({
        where: undefined
      }));
    });

    it('should strip the ORDER BY regardless of case', () => {
      component.whereClause = 'org_id = 42 order by id desc';

      component.onSort('name');

      expect(component.whereClause).toBe('org_id = 42');
    });

    it('should leave a filter without ORDER BY untouched', () => {
      component.whereClause = 'name LIKE \'Parterre%\'';

      component.onSort('name');

      expect(component.whereClause).toBe('name LIKE \'Parterre%\'');
    });

    it('should toggle the direction when the same column is clicked again', () => {
      component.onSort('name');
      expect(component.sortRichtung).toBe('ASC');

      component.onSort('name');

      expect(component.sortRichtung).toBe('DESC');
    });

    it('should start ascending when a different column is clicked', () => {
      component.onSort('name');
      component.onSort('name');
      expect(component.sortRichtung).toBe('DESC');

      component.onSort('org_id');

      expect(component.sortSpalte).toBe('org_id');
      expect(component.sortRichtung).toBe('ASC');
    });

    it('should jump back to the first page', () => {
      component.page = 3;

      component.onSort('name');

      expect(component.page).toBe(0);
    });
  });

  describe('onAnzeigen', () => {
    beforeEach(() => {
      component.selectedTabelle = 'einheit';
      component.whereClause = 'org_id = 42 ORDER BY id DESC';
    });

    it('should send the filter unchanged, sort clause included', () => {
      component.onAnzeigen();

      expect(datenbankServiceSpy.abfrage).toHaveBeenCalledWith(expect.objectContaining({
        tabelle: 'einheit',
        where: 'org_id = 42 ORDER BY id DESC',
        page: 0,
        sortSpalte: undefined,
        sortRichtung: undefined
      }));
    });

    it('should reset a previously chosen sort column', () => {
      component.sortSpalte = 'name';
      component.sortRichtung = 'DESC';

      component.onAnzeigen();

      expect(component.sortSpalte).toBeNull();
      expect(component.sortRichtung).toBe('ASC');
    });

    it('should store the executed filter in the history', () => {
      component.onAnzeigen();

      expect(historieServiceSpy.addFilter)
        .toHaveBeenCalledWith('einheit', 'org_id = 42 ORDER BY id DESC');
    });

    it('should show the backend message on error', () => {
      datenbankServiceSpy.abfrage.mockReturnValue(
        throwError(() => ({ error: 'DATENBANK_ABFRAGE_FEHLER' })));

      component.onAnzeigen();

      expect(component.result).toBeNull();
      expect(component.message).toBe('DATENBANK_ABFRAGE_FEHLER');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onFilterLeeren', () => {
    it('should clear the filter field', () => {
      component.whereClause = 'org_id = 42 ORDER BY id DESC';

      component.onFilterLeeren();

      expect(component.whereClause).toBe('');
    });
  });

  describe('pagination', () => {
    beforeEach(() => {
      component.selectedTabelle = 'einheit';
      component.result = { ...mockAntwort, hatMehr: true };
    });

    it('should advance to the next page when more rows exist', () => {
      component.onNaechsteSeite();

      expect(component.page).toBe(1);
    });

    it('should stay on the last page when no more rows exist', () => {
      component.result = { ...mockAntwort, hatMehr: false };

      component.onNaechsteSeite();

      expect(component.page).toBe(0);
    });

    it('should not page below zero', () => {
      component.onVorherigeSeite();

      expect(component.page).toBe(0);
    });
  });
});
