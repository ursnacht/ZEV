import { createSpyObj, SpyObj } from '../../../testing/spy';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { fakeAsync, tick } from '../../../testing/fake-async';
import { Router } from '@angular/router';
import { EinheitListComponent } from './einheit-list.component';
import { EinheitService } from '../../services/einheit.service';
import { TranslationService } from '../../services/translation.service';
import { Einheit, EinheitTyp } from '../../models/einheit.model';
import { of, throwError } from 'rxjs';
import Keycloak from 'keycloak-js';

describe('EinheitListComponent', () => {
  let component: EinheitListComponent;
  let fixture: ComponentFixture<EinheitListComponent>;
  let einheitServiceSpy: SpyObj<EinheitService>;
  let translationServiceSpy: SpyObj<TranslationService>;
  let routerSpy: SpyObj<Router>;

  /** Realm-Rollen des Test-Benutzers; die Komponente liest sie beim Erzeugen. */
  let rollen: string[];
  const keycloakMock = { hasRealmRole: (rolle: string) => rollen.includes(rolle) } as unknown as Keycloak;

  const mockLadestation: Einheit = {
    id: 3, name: 'Ladestation 1', typ: EinheitTyp.LADESTATION, messpunkt: 'RFID-04711'
  };

  const mockEinheiten: Einheit[] = [
    { id: 1, name: 'Wohnung A', typ: EinheitTyp.CONSUMER, messpunkt: 'MP-001' },
    { id: 2, name: 'Solaranlage', typ: EinheitTyp.PRODUCER, messpunkt: 'MP-002' }
  ];

  beforeEach(async () => {
    rollen = ['einheit:read', 'einheit:write'];
    einheitServiceSpy = createSpyObj<EinheitService>('EinheitService', [
      'getAllEinheiten', 'createEinheit', 'updateEinheit', 'deleteEinheit'
    ]);
    translationServiceSpy = createSpyObj<TranslationService>('TranslationService', ['translate']);
    routerSpy = createSpyObj<Router>('Router', ['navigate']);

    einheitServiceSpy.getAllEinheiten.mockReturnValue(of(mockEinheiten));
    translationServiceSpy.translate.mockImplementation((key: string) => key);
    routerSpy.navigate.mockResolvedValue(true);

    await TestBed.configureTestingModule({
      imports: [EinheitListComponent],
      providers: [
        { provide: EinheitService, useValue: einheitServiceSpy },
        { provide: TranslationService, useValue: translationServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: Keycloak, useValue: keycloakMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(EinheitListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should load einheiten on init', () => {
      expect(einheitServiceSpy.getAllEinheiten).toHaveBeenCalled();
      expect(component.einheiten.length).toBe(2);
    });

    it('should not show form initially', () => {
      expect(component.showForm).toBe(false);
    });

    it('should have default sort by name ascending', () => {
      expect(component.sortColumn).toBe('name');
      expect(component.sortDirection).toBe('asc');
    });

    it('should have menu items for edit, tarifpositionen and delete', () => {
      expect(component.menuItems.length).toBe(3);
      expect(component.menuItems[0].action).toBe('edit');
      expect(component.menuItems[1].action).toBe('tarifpositionen');
      expect(component.menuItems[2].action).toBe('delete');
    });
  });

  describe('loadEinheiten', () => {
    it('should update einheiten array on success', () => {
      component.einheiten = [];
      component.loadEinheiten();
      expect(component.einheiten).toEqual(mockEinheiten);
    });

    it('should show error message on failure', () => {
      einheitServiceSpy.getAllEinheiten.mockReturnValue(throwError(() => new Error('Network error')));
      component.loadEinheiten();
      expect(component.message).toContain('Fehler beim Laden');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onCreateNew', () => {
    it('should set selectedEinheit to null and show form', () => {
      component.selectedEinheit = mockEinheiten[0];
      component.onCreateNew();
      expect(component.selectedEinheit).toBeNull();
      expect(component.showForm).toBe(true);
    });
  });

  describe('onEdit', () => {
    it('should set selectedEinheit and show form', () => {
      const einheit = mockEinheiten[0];
      component.onEdit(einheit);
      expect(component.selectedEinheit).toEqual(einheit);
      expect(component.showForm).toBe(true);
    });

    it('should create a copy of the einheit', () => {
      const einheit = mockEinheiten[0];
      component.onEdit(einheit);
      expect(component.selectedEinheit).not.toBe(einheit);
    });
  });

  describe('onDelete', () => {
    beforeEach(() => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      einheitServiceSpy.deleteEinheit.mockReturnValue(of(undefined as any));
    });

    it('should do nothing when id is undefined', () => {
      component.onDelete(undefined);
      expect(einheitServiceSpy.deleteEinheit).not.toHaveBeenCalled();
    });

    it('should call confirm dialog', () => {
      component.onDelete(1);
      expect(window.confirm).toHaveBeenCalled();
      expect(translationServiceSpy.translate).toHaveBeenCalledWith('CONFIRM_DELETE_EINHEIT');
    });

    it('should delete einheit on confirm', () => {
      component.onDelete(1);
      expect(einheitServiceSpy.deleteEinheit).toHaveBeenCalledWith(1);
    });

    it('should reload einheiten after successful delete', () => {
      einheitServiceSpy.getAllEinheiten.mockClear();
      component.onDelete(1);
      expect(einheitServiceSpy.getAllEinheiten).toHaveBeenCalled();
    });

    it('should show success message after delete', () => {
      component.onDelete(1);
      expect(component.message).toBe('EINHEIT_GELOESCHT');
      expect(component.messageType).toBe('success');
    });

    it('should not delete when user cancels', () => {
      (window.confirm as unknown as ReturnType<typeof vi.fn>).mockReturnValue(false);
      component.onDelete(1);
      expect(einheitServiceSpy.deleteEinheit).not.toHaveBeenCalled();
    });

    it('should show error message on delete failure', () => {
      einheitServiceSpy.deleteEinheit.mockReturnValue(throwError(() => new Error('Delete failed')));
      component.onDelete(1);
      expect(component.message).toBe('FEHLER_LOESCHEN_EINHEIT');
      expect(component.messageType).toBe('error');
    });

    it('should show the server message when the unit is still assigned to tenants', () => {
      // Der Loeschschutz liefert die Anzahl im Body - die soll den Benutzer erreichen
      // (Specs/Ladestationen.md), nicht der generische Fallback.
      einheitServiceSpy.deleteEinheit.mockReturnValue(throwError(() => ({
        error: { error: 'Einheit kann nicht gelöscht werden: 2 Mieter zugeordnet' }
      })));
      component.onDelete(1);
      expect(component.message).toContain('2 Mieter zugeordnet');
      expect(component.messageType).toBe('error');
    });

    it('should show a plain string error body as message', () => {
      einheitServiceSpy.deleteEinheit.mockReturnValue(throwError(() => ({
        error: 'EINHEIT_HAT_MIETER'
      })));
      component.onDelete(1);
      expect(component.message).toBe('EINHEIT_HAT_MIETER');
      expect(component.messageType).toBe('error');
    });

    it('should not reload the einheiten when the delete is rejected', () => {
      einheitServiceSpy.deleteEinheit.mockReturnValue(throwError(() => ({
        error: { error: 'Einheit kann nicht gelöscht werden: 2 Mieter zugeordnet' }
      })));
      einheitServiceSpy.getAllEinheiten.mockClear();
      component.onDelete(1);
      expect(einheitServiceSpy.getAllEinheiten).not.toHaveBeenCalled();
    });

    it('should delete a ladestation without assigned tenants', () => {
      component.onDelete(mockLadestation.id);
      expect(einheitServiceSpy.deleteEinheit).toHaveBeenCalledWith(3);
      expect(component.message).toBe('EINHEIT_GELOESCHT');
    });
  });

  describe('onMenuAction', () => {
    it('should call onEdit for edit action', () => {
      vi.spyOn(component, 'onEdit').mockImplementation(() => {});
      component.onMenuAction('edit', mockEinheiten[0]);
      expect(component.onEdit).toHaveBeenCalledWith(mockEinheiten[0]);
    });

    it('should call onDelete for delete action', () => {
      vi.spyOn(component, 'onDelete').mockImplementation(() => {});
      component.onMenuAction('delete', mockEinheiten[0]);
      expect(component.onDelete).toHaveBeenCalledWith(mockEinheiten[0].id);
    });

    it('should call onTarifpositionen for the tarifpositionen action', () => {
      vi.spyOn(component, 'onTarifpositionen').mockImplementation(() => {});
      component.onMenuAction('tarifpositionen', mockLadestation);
      expect(component.onTarifpositionen).toHaveBeenCalledWith(mockLadestation);
    });

    it('should ignore an unknown action', () => {
      vi.spyOn(component, 'onEdit').mockImplementation(() => {});
      vi.spyOn(component, 'onDelete').mockImplementation(() => {});
      vi.spyOn(component, 'onTarifpositionen').mockImplementation(() => {});

      component.onMenuAction('unbekannt', mockEinheiten[0]);

      expect(component.onEdit).not.toHaveBeenCalled();
      expect(component.onDelete).not.toHaveBeenCalled();
      expect(component.onTarifpositionen).not.toHaveBeenCalled();
    });
  });

  describe('onTarifpositionen', () => {
    it('should navigate to the tarifpositionen page with the einheitId', () => {
      // Der Kebab-Eintrag ist von der Mieter- in die Einheiten-Verwaltung gewandert, weil die
      // Position an der Einheit haengt (Specs/Ladestationen.md FR-3).
      component.onTarifpositionen(mockLadestation);
      expect(routerSpy.navigate).toHaveBeenCalledWith(
        ['/tarifpositionen'], { queryParams: { einheitId: 3 } });
    });
  });

  describe('onFormSubmit - create', () => {
    const newEinheit: Einheit = {
      name: 'Wohnung B',
      typ: EinheitTyp.CONSUMER
    };

    beforeEach(() => {
      einheitServiceSpy.createEinheit.mockReturnValue(of({ ...newEinheit, id: 3 }));
    });

    it('should call createEinheit when einheit has no id', () => {
      component.onFormSubmit(newEinheit);
      expect(einheitServiceSpy.createEinheit).toHaveBeenCalledWith(newEinheit);
    });

    it('should hide form after successful create', () => {
      component.showForm = true;
      component.onFormSubmit(newEinheit);
      expect(component.showForm).toBe(false);
    });

    it('should show success message after create', () => {
      component.onFormSubmit(newEinheit);
      expect(component.message).toBe('EINHEIT_ERSTELLT');
      expect(component.messageType).toBe('success');
    });

    it('should reload einheiten after create', () => {
      einheitServiceSpy.getAllEinheiten.mockClear();
      component.onFormSubmit(newEinheit);
      expect(einheitServiceSpy.getAllEinheiten).toHaveBeenCalled();
    });

    it('should show error message on create failure', () => {
      einheitServiceSpy.createEinheit.mockReturnValue(throwError(() => new Error('Create failed')));
      component.onFormSubmit(newEinheit);
      expect(component.message).toContain('Fehler beim Erstellen');
      expect(component.messageType).toBe('error');
    });

    it('should create a ladestation with the RFID in messpunkt', () => {
      const ladestation: Einheit = {
        name: 'Ladestation 2', typ: EinheitTyp.LADESTATION, messpunkt: 'RFID-04712'
      };
      einheitServiceSpy.createEinheit.mockReturnValue(of({ ...ladestation, id: 4 }));

      component.onFormSubmit(ladestation);

      expect(einheitServiceSpy.createEinheit).toHaveBeenCalledWith(ladestation);
      expect(component.message).toBe('EINHEIT_ERSTELLT');
    });

    it('should translate the server key when the RFID is already used', () => {
      // Die RFID ist je Mandant eindeutig (Specs/Ladestationen.md FR-2); der Server meldet
      // den Uebersetzungs-Key im Body.
      einheitServiceSpy.createEinheit.mockReturnValue(throwError(() => ({
        error: { error: 'EINHEIT_MESSPUNKT_DUPLIKAT' }
      })));

      component.onFormSubmit({ name: 'Ladestation 2', typ: EinheitTyp.LADESTATION, messpunkt: 'RFID-04711' });

      expect(translationServiceSpy.translate).toHaveBeenCalledWith('EINHEIT_MESSPUNKT_DUPLIKAT');
      expect(component.message).toBe('EINHEIT_MESSPUNKT_DUPLIKAT');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onFormSubmit - update', () => {
    const existingEinheit: Einheit = {
      id: 1,
      name: 'Wohnung A Updated',
      typ: EinheitTyp.CONSUMER
    };

    beforeEach(() => {
      einheitServiceSpy.updateEinheit.mockReturnValue(of(existingEinheit));
    });

    it('should call updateEinheit when einheit has id', () => {
      component.onFormSubmit(existingEinheit);
      expect(einheitServiceSpy.updateEinheit).toHaveBeenCalledWith(1, existingEinheit);
    });

    it('should hide form after successful update', () => {
      component.showForm = true;
      component.onFormSubmit(existingEinheit);
      expect(component.showForm).toBe(false);
    });

    it('should show success message after update', () => {
      component.onFormSubmit(existingEinheit);
      expect(component.message).toBe('EINHEIT_AKTUALISIERT');
      expect(component.messageType).toBe('success');
    });

    it('should show error message on update failure', () => {
      einheitServiceSpy.updateEinheit.mockReturnValue(throwError(() => new Error('Update failed')));
      component.onFormSubmit(existingEinheit);
      expect(component.message).toContain('Fehler beim Aktualisieren');
      expect(component.messageType).toBe('error');
    });

    it('should translate the server key when the RFID is already used', () => {
      einheitServiceSpy.updateEinheit.mockReturnValue(throwError(() => ({
        error: { error: 'EINHEIT_MESSPUNKT_DUPLIKAT' }
      })));

      component.onFormSubmit({ ...mockLadestation, messpunkt: 'RFID-04712' });

      expect(translationServiceSpy.translate).toHaveBeenCalledWith('EINHEIT_MESSPUNKT_DUPLIKAT');
      expect(component.message).toBe('EINHEIT_MESSPUNKT_DUPLIKAT');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onFormCancel', () => {
    it('should hide form and clear selected einheit', () => {
      component.showForm = true;
      component.selectedEinheit = mockEinheiten[0];
      component.onFormCancel();
      expect(component.showForm).toBe(false);
      expect(component.selectedEinheit).toBeNull();
    });
  });

  describe('onSort', () => {
    beforeEach(() => {
      component.einheiten = [...mockEinheiten];
    });

    it('should toggle sort direction when clicking same column', () => {
      component.sortColumn = 'name';
      component.sortDirection = 'asc';
      component.onSort('name');
      expect(component.sortDirection).toBe('desc');
    });

    it('should set new column and reset direction to asc', () => {
      component.sortColumn = 'name';
      component.sortDirection = 'desc';
      component.onSort('typ');
      expect(component.sortColumn).toBe('typ');
      expect(component.sortDirection).toBe('asc');
    });

    it('should sort einheiten by name ascending', () => {
      component.sortColumn = null;
      component.onSort('name');
      expect(component.einheiten[0].name).toBe('Solaranlage');
      expect(component.einheiten[1].name).toBe('Wohnung A');
    });

    it('should sort einheiten by typ descending', () => {
      component.sortColumn = 'typ';
      component.onSort('typ');
      expect(component.einheiten[0].typ).toBe(EinheitTyp.PRODUCER);
      expect(component.einheiten[1].typ).toBe(EinheitTyp.CONSUMER);
    });

    it('should list ladestationen alongside the other types', () => {
      // Die Einheiten-Verwaltung blendet nichts aus - Ladestationen erscheinen wie jede
      // andere Einheit (Specs/Ladestationen.md FR-3).
      einheitServiceSpy.getAllEinheiten.mockReturnValue(of([...mockEinheiten, mockLadestation]));
      component.loadEinheiten();
      expect(component.einheiten.length).toBe(3);
      expect(component.einheiten.some(e => e.typ === EinheitTyp.LADESTATION)).toBe(true);
    });

    it('should sort by messpunkt so RFIDs are groupable', () => {
      component.einheiten = [mockLadestation, ...mockEinheiten];
      component.sortColumn = null;
      component.onSort('messpunkt');
      expect(component.einheiten.map(e => e.messpunkt)).toEqual(['MP-001', 'MP-002', 'RFID-04711']);
    });

    it('should sort by typ with LADESTATION included', () => {
      component.einheiten = [...mockEinheiten, mockLadestation];
      component.sortColumn = null;
      component.onSort('typ');
      // Sortiert nach dem angezeigten Text; der Mock liefert die Keys KONSUMENT < PRODUZENT < TYP_LADESTATION
      expect(component.einheiten.map(e => e.typ)).toEqual([
        EinheitTyp.CONSUMER, EinheitTyp.PRODUCER, EinheitTyp.LADESTATION
      ]);
    });

    it('should handle null values in sort', () => {
      component.einheiten = [
        { id: 1, name: 'A', typ: EinheitTyp.CONSUMER, messpunkt: undefined },
        { id: 2, name: 'B', typ: EinheitTyp.CONSUMER, messpunkt: 'MP-001' }
      ];
      component.sortColumn = null;
      component.onSort('messpunkt');
      expect(component.einheiten[0].messpunkt).toBe('MP-001');
    });
  });

  describe('message timeout', () => {
    it('should clear message after 5 seconds', fakeAsync(() => {
      einheitServiceSpy.createEinheit.mockReturnValue(of({
        id: 3, name: 'Test', typ: EinheitTyp.CONSUMER
      }));

      component.onFormSubmit({ name: 'Test', typ: EinheitTyp.CONSUMER });

      expect(component.message).toBeTruthy();
      tick(5000);
      expect(component.message).toBe('');
    }));
  });

  // Specs/Einheitenverwaltung.md, Abschnitt 3: Formular nach Abweisung, Sortierung, Darstellung
  describe('rejected save', () => {
    it('should keep the form open when create fails', () => {
      component.showForm = true;
      einheitServiceSpy.createEinheit.mockReturnValue(throwError(() => ({ error: { error: 'EINHEIT_BILANZ_TYP_EXISTIERT' } })));

      component.onFormSubmit({ name: 'Netzbezug 2', typ: EinheitTyp.BEZUG });

      expect(component.showForm).toBe(true);
      expect(component.messageType).toBe('error');
    });

    it('should keep the form open when update fails', () => {
      component.showForm = true;
      einheitServiceSpy.updateEinheit.mockReturnValue(throwError(() => new Error('Update failed')));

      component.onFormSubmit(mockEinheiten[0]);

      expect(component.showForm).toBe(true);
      expect(component.messageType).toBe('error');
    });
  });

  describe('onSort - empty values', () => {
    it('should keep empty values at the end when sorting descending', () => {
      component.einheiten = [
        { id: 1, name: 'A', typ: EinheitTyp.CONSUMER, messpunkt: undefined },
        { id: 2, name: 'B', typ: EinheitTyp.CONSUMER, messpunkt: 'MP-001' },
        { id: 3, name: 'C', typ: EinheitTyp.CONSUMER, messpunkt: 'MP-002' }
      ];

      component.onSort('messpunkt');
      component.onSort('messpunkt');

      expect(component.sortDirection).toBe('desc');
      expect(component.einheiten.map(e => e.messpunkt)).toEqual(['MP-002', 'MP-001', undefined]);
    });
  });

  describe('template', () => {
    const element = (): HTMLElement => fixture.nativeElement;

    it('should show the columns ID, Name, Typ, Messpunkt and a kebab menu per row', () => {
      const koepfe = Array.from(element().querySelectorAll('.zev-table thead th'))
        .map(th => th.textContent?.replace(/[▲▼]/g, '').trim());

      expect(koepfe).toEqual(['ID', 'NAME', 'TYP', 'MESSPUNKT', '']);
      expect(element().querySelectorAll('.zev-table tbody tr app-kebab-menu').length).toBe(mockEinheiten.length);
    });

    it('should show "-" for an empty messpunkt', () => {
      einheitServiceSpy.getAllEinheiten.mockReturnValue(of([{ id: 9, name: 'Ohne', typ: EinheitTyp.CONSUMER }]));
      component.loadEinheiten();
      fixture.detectChanges();

      const zellen = element().querySelectorAll('.zev-table tbody tr td');
      expect(zellen[3].textContent?.trim()).toBe('-');
    });

    it('should show KEINE_EINHEITEN_VORHANDEN instead of the table when the list is empty', () => {
      einheitServiceSpy.getAllEinheiten.mockReturnValue(of([]));
      component.loadEinheiten();
      fixture.detectChanges();

      expect(element().querySelector('.zev-table')).toBeNull();
      expect(element().textContent).toContain('KEINE_EINHEITEN_VORHANDEN');
    });
  });

  // Specs/Einheitenverwaltung.md, Anhang A.6: Antworten auf die offenen Fragen
  describe('without einheit:write', () => {
    beforeEach(() => {
      rollen = ['einheit:read'];
      fixture = TestBed.createComponent(EinheitListComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should hide the create button', () => {
      expect(fixture.nativeElement.textContent).not.toContain('NEUE_EINHEIT_ERSTELLEN');
    });

    it('should offer only the tarifpositionen entry in the kebab menu', () => {
      expect(component.menuItems.map(i => i.action)).toEqual(['tarifpositionen']);
    });
  });

  describe('with einheit:write', () => {
    it('should show the create button and all kebab entries', () => {
      expect(fixture.nativeElement.textContent).toContain('NEUE_EINHEIT_ERSTELLEN');
      expect(component.menuItems.map(i => i.action)).toEqual(['edit', 'tarifpositionen', 'delete']);
    });
  });

  describe('translated server messages', () => {
    it('should translate the field message of a Bean-Validation error', () => {
      component.showForm = true;
      einheitServiceSpy.createEinheit.mockReturnValue(throwError(() => ({
        status: 400, message: 'Http failure response', error: { name: 'EINHEIT_NAME_LAENGE' }
      })));

      component.onFormSubmit({ name: 'A', typ: EinheitTyp.CONSUMER });

      expect(translationServiceSpy.translate).toHaveBeenCalledWith('EINHEIT_NAME_LAENGE');
      expect(component.message).toBe('EINHEIT_NAME_LAENGE');
      expect(component.showForm).toBe(true);
    });

    it('should translate the delete fallback FEHLER_LOESCHEN_EINHEIT', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      einheitServiceSpy.deleteEinheit.mockReturnValue(throwError(() => ({ status: 404, error: null })));

      component.onDelete(1);

      expect(translationServiceSpy.translate).toHaveBeenCalledWith('FEHLER_LOESCHEN_EINHEIT');
      expect(component.messageType).toBe('error');
    });

    it('should translate EINHEIT_HAT_MESSWERTE when the unit still has measurements', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      einheitServiceSpy.deleteEinheit.mockReturnValue(throwError(() => ({
        status: 400, error: { error: 'EINHEIT_HAT_MESSWERTE' }
      })));

      component.onDelete(1);

      expect(translationServiceSpy.translate).toHaveBeenCalledWith('EINHEIT_HAT_MESSWERTE');
      expect(component.message).toBe('EINHEIT_HAT_MESSWERTE');
    });
  });

  describe('onSort - typ by displayed text', () => {
    it('should sort the type column by the translated label, not by the enum value', () => {
      // Englische Texte: Die Enum-Reihenfolge (BEZUG < CONSUMER < PRODUCER) weicht davon ab
      const texte: Record<string, string> = { KONSUMENT: 'Consumer', TYP_BEZUG: 'Grid purchase', PRODUZENT: 'Producer' };
      translationServiceSpy.translate.mockImplementation((key: string) => texte[key] ?? key);
      component.einheiten = [
        { id: 1, name: 'P', typ: EinheitTyp.PRODUCER },
        { id: 2, name: 'B', typ: EinheitTyp.BEZUG },
        { id: 3, name: 'C', typ: EinheitTyp.CONSUMER }
      ];

      component.onSort('typ');

      expect(component.einheiten.map(e => e.typ))
        .toEqual([EinheitTyp.CONSUMER, EinheitTyp.BEZUG, EinheitTyp.PRODUCER]);
    });
  });

  describe('loadEinheiten - keeps the chosen sort', () => {
    it('should apply the chosen sort after reloading', () => {
      component.onSort('name');
      expect(component.sortDirection).toBe('desc');

      einheitServiceSpy.getAllEinheiten.mockReturnValue(of([
        { id: 1, name: 'Anna', typ: EinheitTyp.CONSUMER },
        { id: 2, name: 'Zora', typ: EinheitTyp.CONSUMER }
      ]));
      component.loadEinheiten();

      expect(component.einheiten.map(e => e.name)).toEqual(['Zora', 'Anna']);
    });
  });
});
