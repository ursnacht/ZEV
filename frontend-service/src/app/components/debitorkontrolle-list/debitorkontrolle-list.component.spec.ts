import { createSpyObj, SpyObj } from '../../../testing/spy';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { fakeAsync, tick } from '../../../testing/fake-async';
import { DebitorkontrolleListComponent } from './debitorkontrolle-list.component';
import { DebitorService } from '../../services/debitor.service';
import { EinheitService } from '../../services/einheit.service';
import { MieterService } from '../../services/mieter.service';
import { TranslationService } from '../../services/translation.service';
import { Debitor } from '../../models/debitor.model';
import { Einheit, EinheitTyp } from '../../models/einheit.model';
import { Mieter } from '../../models/mieter.model';
import { of, throwError } from 'rxjs';

describe('DebitorkontrolleListComponent', () => {
  let component: DebitorkontrolleListComponent;
  let fixture: ComponentFixture<DebitorkontrolleListComponent>;
  let debitorServiceSpy: SpyObj<DebitorService>;
  let einheitServiceSpy: SpyObj<EinheitService>;
  let mieterServiceSpy: SpyObj<MieterService>;
  let translationServiceSpy: SpyObj<TranslationService>;

  const mockEinheiten: Einheit[] = [
    { id: 1, name: 'EG links', typ: EinheitTyp.CONSUMER },
    { id: 2, name: 'OG rechts', typ: EinheitTyp.CONSUMER },
    { id: 3, name: 'Solaranlage', typ: EinheitTyp.PRODUCER }
  ];

  const mockMieter: Mieter[] = [
    { id: 10, name: 'Max Muster', strasse: 'Musterstr. 1', plz: '8000', ort: 'Zürich', mietbeginn: '2024-01-01', einheitId: 1 },
    { id: 11, name: 'Anna Test', strasse: 'Testweg 2', plz: '3000', ort: 'Bern', mietbeginn: '2024-06-01', einheitId: 2 }
  ];

  const mockDebitoren: Debitor[] = [
    { id: 1, mieterId: 10, mieterName: 'Max Muster', einheitName: 'EG links', betrag: 123.45, datumVon: '2025-01-01', datumBis: '2025-03-31' },
    { id: 2, mieterId: 11, mieterName: 'Anna Test', einheitName: 'OG rechts', betrag: 87.60, datumVon: '2025-01-01', datumBis: '2025-03-31', zahldatum: '2025-02-15' }
  ];

  beforeEach(async () => {
    debitorServiceSpy = createSpyObj<DebitorService>('DebitorService', [
      'getDebitoren', 'createDebitor', 'updateDebitor', 'deleteDebitor'
    ]);
    einheitServiceSpy = createSpyObj<EinheitService>('EinheitService', ['getAllEinheiten']);
    mieterServiceSpy = createSpyObj<MieterService>('MieterService', ['getAllMieter']);
    translationServiceSpy = createSpyObj<TranslationService>('TranslationService', ['translate']);

    debitorServiceSpy.getDebitoren.mockReturnValue(of(mockDebitoren));
    debitorServiceSpy.createDebitor.mockReturnValue(of(mockDebitoren[0]));
    debitorServiceSpy.updateDebitor.mockReturnValue(of(mockDebitoren[0]));
    debitorServiceSpy.deleteDebitor.mockReturnValue(of(void 0));
    einheitServiceSpy.getAllEinheiten.mockReturnValue(of(mockEinheiten));
    mieterServiceSpy.getAllMieter.mockReturnValue(of(mockMieter));
    translationServiceSpy.translate.mockImplementation((key: string) => key);

    await TestBed.configureTestingModule({
      imports: [DebitorkontrolleListComponent],
      providers: [
        { provide: DebitorService, useValue: debitorServiceSpy },
        { provide: EinheitService, useValue: einheitServiceSpy },
        { provide: MieterService, useValue: mieterServiceSpy },
        { provide: TranslationService, useValue: translationServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DebitorkontrolleListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should load debitoren on init', () => {
      expect(debitorServiceSpy.getDebitoren).toHaveBeenCalled();
      expect(component.debitoren.length).toBe(2);
    });

    it('should load einheiten on init', () => {
      expect(einheitServiceSpy.getAllEinheiten).toHaveBeenCalled();
    });

    it('should load mieter on init', () => {
      expect(mieterServiceSpy.getAllMieter).toHaveBeenCalled();
    });

    it('should filter einheiten to CONSUMER only', () => {
      expect(component.einheiten.length).toBe(2);
      expect(component.einheiten.every(e => e.typ === EinheitTyp.CONSUMER)).toBe(true);
    });

    it('should not show form initially', () => {
      expect(component.showForm).toBe(false);
    });

    it('should have default sort by mieterName ascending', () => {
      expect(component.sortColumn).toBe('mieterName');
      expect(component.sortDirection).toBe('asc');
    });

    it('should set default quarter dates', () => {
      expect(component.dateFrom).toBeTruthy();
      expect(component.dateTo).toBeTruthy();
    });

    describe('default quarter', () => {
      beforeEach(() => {
        vi.useFakeTimers();
      });

      afterEach(() => {
        vi.useRealTimers();
      });

      it('should preselect the previous quarter', () => {
        vi.setSystemTime(new Date(2026, 4, 15)); // 15.05.2026 → Q2 → Vorquartal Q1/2026
        component.ngOnInit();
        expect(component.dateFrom).toBe('2026-01-01');
        expect(component.dateTo).toBe('2026-03-31');
      });

      it('should preselect Q4 of previous year when current quarter is Q1', () => {
        vi.setSystemTime(new Date(2026, 1, 10)); // 10.02.2026 → Q1 → Vorquartal Q4/2025
        component.ngOnInit();
        expect(component.dateFrom).toBe('2025-10-01');
        expect(component.dateTo).toBe('2025-12-31');
      });

      it('should load debitoren for the previous quarter', () => {
        vi.setSystemTime(new Date(2026, 4, 15));
        debitorServiceSpy.getDebitoren.mockClear();
        component.ngOnInit();
        expect(debitorServiceSpy.getDebitoren).toHaveBeenCalledWith('2026-01-01', '2026-03-31');
      });
    });

    it('should have base menu items (edit, heute, gestern, delete) for unpaid entries', () => {
      const items = component.getMenuItems({ mieterId: 10, betrag: 1, datumVon: '2025-01-01', datumBis: '2025-03-31' });
      expect(items.map(i => i.action)).toEqual(['edit', 'heute', 'gestern', 'delete']);
    });
  });

  describe('getMenuItems', () => {
    it('should not include zahldatumLoeschen when no zahldatum is set', () => {
      const items = component.getMenuItems({ mieterId: 10, betrag: 1, datumVon: '2025-01-01', datumBis: '2025-03-31' });
      expect(items.some(i => i.action === 'zahldatumLoeschen')).toBe(false);
    });

    it('should include zahldatumLoeschen when zahldatum is set', () => {
      const items = component.getMenuItems({ mieterId: 10, betrag: 1, datumVon: '2025-01-01', datumBis: '2025-03-31', zahldatum: '2025-04-10' });
      expect(items.map(i => i.action)).toEqual(['edit', 'heute', 'gestern', 'zahldatumLoeschen', 'delete']);
    });
  });

  describe('loadDebitoren', () => {
    it('should update debitoren array on success', () => {
      component.debitoren = [];
      component.loadDebitoren();
      expect(component.debitoren).toEqual(expect.arrayContaining(mockDebitoren));
    });

    it('should show error message on failure', () => {
      debitorServiceSpy.getDebitoren.mockReturnValue(throwError(() => new Error('Network error')));
      component.loadDebitoren();
      expect(component.message).toBe('FEHLER_LADEN_DEBITOREN');
      expect(component.messageType).toBe('error');
    });

    it('should not call service when dateFrom is empty', () => {
      debitorServiceSpy.getDebitoren.mockClear();
      component.dateFrom = '';
      component.loadDebitoren();
      expect(debitorServiceSpy.getDebitoren).not.toHaveBeenCalled();
    });

    it('should not call service when dateTo is empty', () => {
      debitorServiceSpy.getDebitoren.mockClear();
      component.dateTo = '';
      component.loadDebitoren();
      expect(debitorServiceSpy.getDebitoren).not.toHaveBeenCalled();
    });
  });

  describe('loadEinheiten', () => {
    it('should show error message on failure', () => {
      einheitServiceSpy.getAllEinheiten.mockReturnValue(throwError(() => new Error('Network error')));
      component.loadEinheiten();
      expect(component.message).toBe('FEHLER_LADEN_EINHEITEN');
      expect(component.messageType).toBe('error');
    });
  });

  describe('loadMieter', () => {
    it('should update mieter array on success', () => {
      component.mieter = [];
      component.loadMieter();
      expect(component.mieter).toEqual(mockMieter);
    });

    it('should show error message on failure', () => {
      mieterServiceSpy.getAllMieter.mockReturnValue(throwError(() => new Error('Network error')));
      component.loadMieter();
      expect(component.message).toBe('FEHLER_LADEN_MIETER');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onQuarterSelected', () => {
    it('should update dateFrom and dateTo and reload', () => {
      debitorServiceSpy.getDebitoren.mockClear();
      component.onQuarterSelected({ von: '2025-04-01', bis: '2025-06-30' });
      expect(component.dateFrom).toBe('2025-04-01');
      expect(component.dateTo).toBe('2025-06-30');
      expect(debitorServiceSpy.getDebitoren).toHaveBeenCalled();
    });
  });

  describe('onDateChange', () => {
    it('should reload debitoren', () => {
      debitorServiceSpy.getDebitoren.mockClear();
      component.onDateChange();
      expect(debitorServiceSpy.getDebitoren).toHaveBeenCalled();
    });
  });

  describe('onCreateNew', () => {
    it('should set selectedDebitor to null and show form', () => {
      component.selectedDebitor = mockDebitoren[0];
      component.onCreateNew();
      expect(component.selectedDebitor).toBeNull();
      expect(component.showForm).toBe(true);
    });
  });

  describe('onEdit', () => {
    it('should set selectedDebitor and show form', () => {
      component.onEdit(mockDebitoren[0]);
      expect(component.selectedDebitor).toEqual(mockDebitoren[0]);
      expect(component.showForm).toBe(true);
    });

    it('should create a copy of the debitor, not a reference', () => {
      component.onEdit(mockDebitoren[0]);
      expect(component.selectedDebitor).not.toBe(mockDebitoren[0]);
    });
  });

  describe('onDelete', () => {
    beforeEach(() => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
    });

    it('should do nothing when id is undefined', () => {
      component.onDelete(undefined);
      expect(debitorServiceSpy.deleteDebitor).not.toHaveBeenCalled();
    });

    it('should show confirm dialog with translated message', () => {
      component.onDelete(1);
      expect(translationServiceSpy.translate).toHaveBeenCalledWith('DEBITOR_LOESCHEN_BESTAETIGUNG');
    });

    it('should call deleteDebitor on confirm', () => {
      component.onDelete(1);
      expect(debitorServiceSpy.deleteDebitor).toHaveBeenCalledWith(1);
    });

    it('should reload debitoren after successful delete', () => {
      debitorServiceSpy.getDebitoren.mockClear();
      component.onDelete(1);
      expect(debitorServiceSpy.getDebitoren).toHaveBeenCalled();
    });

    it('should show success message after delete', () => {
      component.onDelete(1);
      expect(component.message).toBe('DEBITOR_GELOESCHT');
      expect(component.messageType).toBe('success');
    });

    it('should not call deleteDebitor when user cancels', () => {
      (window.confirm as unknown as ReturnType<typeof vi.fn>).mockReturnValue(false);
      component.onDelete(1);
      expect(debitorServiceSpy.deleteDebitor).not.toHaveBeenCalled();
    });

    it('should show error message on delete failure', () => {
      debitorServiceSpy.deleteDebitor.mockReturnValue(throwError(() => new Error('Delete failed')));
      component.onDelete(1);
      expect(component.message).toBe('FEHLER_LOESCHEN_DEBITOR');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onMenuAction', () => {
    it('should call onEdit for edit action', () => {
      vi.spyOn(component, 'onEdit').mockImplementation(() => {});
      component.onMenuAction('edit', mockDebitoren[0]);
      expect(component.onEdit).toHaveBeenCalledWith(mockDebitoren[0]);
    });

    it('should call onDelete for delete action', () => {
      vi.spyOn(component, 'onDelete').mockImplementation(() => {});
      component.onMenuAction('delete', mockDebitoren[0]);
      expect(component.onDelete).toHaveBeenCalledWith(mockDebitoren[0].id);
    });

    it('should call setZahldatum with 0 for heute action', () => {
      vi.spyOn(component, 'setZahldatum').mockImplementation(() => {});
      component.onMenuAction('heute', mockDebitoren[0]);
      expect(component.setZahldatum).toHaveBeenCalledWith(mockDebitoren[0], 0);
    });

    it('should call setZahldatum with 1 for gestern action', () => {
      vi.spyOn(component, 'setZahldatum').mockImplementation(() => {});
      component.onMenuAction('gestern', mockDebitoren[0]);
      expect(component.setZahldatum).toHaveBeenCalledWith(mockDebitoren[0], 1);
    });

    it('should call setZahldatum with null for zahldatumLoeschen action', () => {
      vi.spyOn(component, 'setZahldatum').mockImplementation(() => {});
      component.onMenuAction('zahldatumLoeschen', mockDebitoren[1]);
      expect(component.setZahldatum).toHaveBeenCalledWith(mockDebitoren[1], null);
    });
  });

  describe('setZahldatum', () => {
    const offen: Debitor = { id: 1, mieterId: 10, betrag: 123.45, datumVon: '2025-01-01', datumBis: '2025-03-31' };
    const bezahlt: Debitor = { id: 2, mieterId: 11, betrag: 87.60, datumVon: '2025-01-01', datumBis: '2025-03-31', zahldatum: '2025-02-15' };

    beforeEach(() => {
      vi.useFakeTimers();
      vi.setSystemTime(new Date(2025, 5, 16)); // 16.06.2025
      debitorServiceSpy.updateDebitor.mockReturnValue(of(offen));
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it('should do nothing when debitor has no id', () => {
      component.setZahldatum({ mieterId: 10, betrag: 1, datumVon: '2025-01-01', datumBis: '2025-03-31' }, 0);
      expect(debitorServiceSpy.updateDebitor).not.toHaveBeenCalled();
    });

    it('should set zahldatum to today for offsetDays 0', () => {
      component.setZahldatum(offen, 0);
      expect(debitorServiceSpy.updateDebitor).toHaveBeenCalledWith(1,
        expect.objectContaining({ id: 1, zahldatum: '2025-06-16' }));
    });

    it('should set zahldatum to yesterday for offsetDays 1', () => {
      component.setZahldatum(offen, 1);
      expect(debitorServiceSpy.updateDebitor).toHaveBeenCalledWith(1,
        expect.objectContaining({ id: 1, zahldatum: '2025-06-15' }));
    });

    it('should clear zahldatum when offsetDays is null', () => {
      component.setZahldatum(bezahlt, null);
      expect(debitorServiceSpy.updateDebitor).toHaveBeenCalledWith(2,
        expect.objectContaining({ id: 2, zahldatum: undefined }));
    });

    it('should show success message and reload on success', () => {
      debitorServiceSpy.getDebitoren.mockClear();
      component.setZahldatum(offen, 0);
      expect(component.message).toBe('DEBITOR_AKTUALISIERT');
      expect(component.messageType).toBe('success');
      expect(debitorServiceSpy.getDebitoren).toHaveBeenCalled();
    });

    it('should show error message on failure', () => {
      debitorServiceSpy.updateDebitor.mockReturnValue(throwError(() => ({ error: 'ZAHLDATUM_VOR_DATUM_BIS' })));
      component.setZahldatum(offen, 0);
      expect(component.message).toBe('ZAHLDATUM_VOR_DATUM_BIS');
      expect(component.messageType).toBe('error');
    });

    it('should show default error key when no error body', () => {
      debitorServiceSpy.updateDebitor.mockReturnValue(throwError(() => ({})));
      component.setZahldatum(offen, 0);
      expect(component.message).toBe('FEHLER_AKTUALISIEREN_DEBITOR');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onFormSubmit - create', () => {
    const newDebitor: Debitor = {
      mieterId: 10,
      betrag: 150.00,
      datumVon: '2025-04-01',
      datumBis: '2025-06-30'
    };

    beforeEach(() => {
      debitorServiceSpy.createDebitor.mockReturnValue(of({ ...newDebitor, id: 3 }));
    });

    it('should call createDebitor when debitor has no id', () => {
      component.onFormSubmit(newDebitor);
      expect(debitorServiceSpy.createDebitor).toHaveBeenCalledWith(newDebitor);
    });

    it('should hide form after successful create', () => {
      component.showForm = true;
      component.onFormSubmit(newDebitor);
      expect(component.showForm).toBe(false);
    });

    it('should show success message after create', () => {
      component.onFormSubmit(newDebitor);
      expect(component.message).toBe('DEBITOR_ERSTELLT');
      expect(component.messageType).toBe('success');
    });

    it('should show error message on create failure', () => {
      debitorServiceSpy.createDebitor.mockReturnValue(throwError(() => ({ error: 'CUSTOM_ERROR' })));
      component.onFormSubmit(newDebitor);
      expect(component.message).toBe('CUSTOM_ERROR');
      expect(component.messageType).toBe('error');
    });

    it('should show default error key when no error body', () => {
      debitorServiceSpy.createDebitor.mockReturnValue(throwError(() => ({})));
      component.onFormSubmit(newDebitor);
      expect(component.message).toBe('FEHLER_ERSTELLEN_DEBITOR');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onFormSubmit - update', () => {
    const existingDebitor: Debitor = {
      id: 1,
      mieterId: 10,
      betrag: 123.45,
      datumVon: '2025-01-01',
      datumBis: '2025-03-31',
      zahldatum: '2025-04-10'
    };

    beforeEach(() => {
      debitorServiceSpy.updateDebitor.mockReturnValue(of(existingDebitor));
    });

    it('should call updateDebitor when debitor has id', () => {
      component.onFormSubmit(existingDebitor);
      expect(debitorServiceSpy.updateDebitor).toHaveBeenCalledWith(1, existingDebitor);
    });

    it('should hide form after successful update', () => {
      component.showForm = true;
      component.onFormSubmit(existingDebitor);
      expect(component.showForm).toBe(false);
    });

    it('should show success message after update', () => {
      component.onFormSubmit(existingDebitor);
      expect(component.message).toBe('DEBITOR_AKTUALISIERT');
      expect(component.messageType).toBe('success');
    });

    it('should show error message on update failure', () => {
      debitorServiceSpy.updateDebitor.mockReturnValue(throwError(() => ({ error: 'UPDATE_ERROR' })));
      component.onFormSubmit(existingDebitor);
      expect(component.message).toBe('UPDATE_ERROR');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onFormCancel', () => {
    it('should hide form and clear selected debitor', () => {
      component.showForm = true;
      component.selectedDebitor = mockDebitoren[0];
      component.onFormCancel();
      expect(component.showForm).toBe(false);
      expect(component.selectedDebitor).toBeNull();
    });
  });

  describe('onSort', () => {
    it('should toggle sort direction when clicking same column', () => {
      component.sortColumn = 'mieterName';
      component.sortDirection = 'asc';
      component.onSort('mieterName');
      expect(component.sortDirection).toBe('desc');
    });

    it('should reset direction to asc when switching column', () => {
      component.sortColumn = 'mieterName';
      component.sortDirection = 'desc';
      component.onSort('betrag');
      expect(component.sortColumn).toBe('betrag');
      expect(component.sortDirection).toBe('asc');
    });

    it('should sort debitoren by mieterName ascending', () => {
      component.debitoren = [...mockDebitoren];
      component.sortColumn = null;
      component.onSort('mieterName');
      expect(component.debitoren[0].mieterName).toBe('Anna Test');
      expect(component.debitoren[1].mieterName).toBe('Max Muster');
    });

    it('should sort debitoren by mieterName descending', () => {
      component.debitoren = [...mockDebitoren];
      component.sortColumn = 'mieterName';
      component.sortDirection = 'asc';
      component.onSort('mieterName');
      expect(component.debitoren[0].mieterName).toBe('Max Muster');
      expect(component.debitoren[1].mieterName).toBe('Anna Test');
    });

    it('should sort by status (offen before bezahlt) ascending', () => {
      component.debitoren = [
        { ...mockDebitoren[1] }, // bezahlt
        { ...mockDebitoren[0] }  // offen
      ];
      component.sortColumn = null;
      component.onSort('status');
      // offen (1) > bezahlt (0) so ascending: bezahlt first
      expect(component.debitoren[0].zahldatum).toBeDefined();
    });
  });

  describe('isOffen', () => {
    it('should return true when zahldatum is not set', () => {
      const debitor: Debitor = { mieterId: 10, betrag: 100, datumVon: '2025-01-01', datumBis: '2025-03-31' };
      expect(component.isOffen(debitor)).toBe(true);
    });

    it('should return false when zahldatum is set', () => {
      const debitor: Debitor = { mieterId: 10, betrag: 100, datumVon: '2025-01-01', datumBis: '2025-03-31', zahldatum: '2025-04-01' };
      expect(component.isOffen(debitor)).toBe(false);
    });
  });

  describe('getEinheitName', () => {
    it('should return einheit name for known mieter', () => {
      expect(component.getEinheitName(10)).toBe('EG links');
    });

    it('should return empty string for unknown mieter', () => {
      expect(component.getEinheitName(999)).toBe('');
    });
  });

  describe('showMessage', () => {
    it('should set message and type', () => {
      component.showMessage('TEST_MSG', 'error');
      expect(component.message).toBe('TEST_MSG');
      expect(component.messageType).toBe('error');
    });
  });

  describe('dismissMessage', () => {
    it('should clear message', () => {
      component.message = 'Some message';
      component.dismissMessage();
      expect(component.message).toBe('');
    });
  });

  describe('message timeout', () => {
    it('should auto-dismiss success message after 5 seconds', fakeAsync(() => {
      debitorServiceSpy.createDebitor.mockReturnValue(of({ id: 3, mieterId: 10, betrag: 50, datumVon: '2025-01-01', datumBis: '2025-03-31' }));
      component.onFormSubmit({ mieterId: 10, betrag: 50, datumVon: '2025-01-01', datumBis: '2025-03-31' });

      expect(component.message).toBe('DEBITOR_ERSTELLT');
      tick(5000);
      expect(component.message).toBe('');
    }));

    it('should not auto-clear error message', fakeAsync(() => {
      debitorServiceSpy.getDebitoren.mockReturnValue(throwError(() => new Error('Network error')));
      component.loadDebitoren();

      expect(component.message).toBe('FEHLER_LADEN_DEBITOREN');
      tick(5000);
      expect(component.message).toBe('FEHLER_LADEN_DEBITOREN');
    }));
  });

  describe('isSelected', () => {
    it('should return false when id is undefined', () => {
      expect(component.isSelected(undefined)).toBe(false);
    });

    it('should return false when id is not in selectedIds', () => {
      component.selectedIds.clear();
      expect(component.isSelected(1)).toBe(false);
    });

    it('should return true when id is in selectedIds', () => {
      component.selectedIds.add(1);
      expect(component.isSelected(1)).toBe(true);
    });
  });

  describe('allSelected', () => {
    it('should return false when debitoren is empty', () => {
      component.debitoren = [];
      expect(component.allSelected()).toBe(false);
    });

    it('should return false when no items are selected', () => {
      component.debitoren = mockDebitoren;
      component.selectedIds.clear();
      expect(component.allSelected()).toBe(false);
    });

    it('should return false when only some items are selected', () => {
      component.debitoren = mockDebitoren;
      component.selectedIds.clear();
      component.selectedIds.add(1);
      expect(component.allSelected()).toBe(false);
    });

    it('should return true when all items are selected', () => {
      component.debitoren = mockDebitoren;
      component.selectedIds.clear();
      mockDebitoren.forEach(d => { if (d.id) component.selectedIds.add(d.id); });
      expect(component.allSelected()).toBe(true);
    });
  });

  describe('someSelected', () => {
    it('should return false when no items are selected', () => {
      component.debitoren = mockDebitoren;
      component.selectedIds.clear();
      expect(component.someSelected()).toBe(false);
    });

    it('should return true when some but not all items are selected', () => {
      component.debitoren = mockDebitoren;
      component.selectedIds.clear();
      component.selectedIds.add(1);
      expect(component.someSelected()).toBe(true);
    });

    it('should return false when all items are selected', () => {
      component.debitoren = mockDebitoren;
      component.selectedIds.clear();
      mockDebitoren.forEach(d => { if (d.id) component.selectedIds.add(d.id); });
      expect(component.someSelected()).toBe(false);
    });
  });

  describe('onToggleSelect', () => {
    it('should do nothing when id is undefined', () => {
      component.selectedIds.clear();
      component.onToggleSelect(undefined);
      expect(component.selectedIds.size).toBe(0);
    });

    it('should add id to selectedIds when not selected', () => {
      component.selectedIds.clear();
      component.onToggleSelect(1);
      expect(component.selectedIds.has(1)).toBe(true);
    });

    it('should remove id from selectedIds when already selected', () => {
      component.selectedIds.add(1);
      component.onToggleSelect(1);
      expect(component.selectedIds.has(1)).toBe(false);
    });
  });

  describe('onToggleSelectAll', () => {
    it('should select all debitoren when not all are selected', () => {
      component.debitoren = mockDebitoren;
      component.selectedIds.clear();
      component.onToggleSelectAll();
      expect(component.selectedIds.size).toBe(mockDebitoren.length);
      mockDebitoren.forEach(d => {
        if (d.id) expect(component.selectedIds.has(d.id)).toBe(true);
      });
    });

    it('should deselect all when all are already selected', () => {
      component.debitoren = mockDebitoren;
      mockDebitoren.forEach(d => { if (d.id) component.selectedIds.add(d.id); });
      component.onToggleSelectAll();
      expect(component.selectedIds.size).toBe(0);
    });

    it('should select all when only some are selected', () => {
      component.debitoren = mockDebitoren;
      component.selectedIds.clear();
      component.selectedIds.add(1);
      component.onToggleSelectAll();
      expect(component.selectedIds.size).toBe(mockDebitoren.length);
    });
  });

  describe('onDeleteSelected', () => {
    beforeEach(() => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      component.debitoren = mockDebitoren;
      component.selectedIds.clear();
      mockDebitoren.forEach(d => { if (d.id) component.selectedIds.add(d.id); });
    });

    it('should do nothing when no ids are selected', () => {
      component.selectedIds.clear();
      component.onDeleteSelected();
      expect(debitorServiceSpy.deleteDebitor).not.toHaveBeenCalled();
    });

    it('should show confirm dialog with count', () => {
      component.onDeleteSelected();
      expect(translationServiceSpy.translate).toHaveBeenCalledWith('DEBITOREN_LOESCHEN_BESTAETIGUNG');
    });

    it('should not delete when user cancels', () => {
      (window.confirm as unknown as ReturnType<typeof vi.fn>).mockReturnValue(false);
      component.onDeleteSelected();
      expect(debitorServiceSpy.deleteDebitor).not.toHaveBeenCalled();
    });

    it('should call deleteDebitor for each selected id', () => {
      component.onDeleteSelected();
      expect(debitorServiceSpy.deleteDebitor).toHaveBeenCalledWith(1);
      expect(debitorServiceSpy.deleteDebitor).toHaveBeenCalledWith(2);
    });

    it('should show success message and reload after deleting all', () => {
      debitorServiceSpy.getDebitoren.mockClear();
      component.onDeleteSelected();
      expect(component.message).toBe('DEBITOREN_GELOESCHT');
      expect(component.messageType).toBe('success');
      expect(debitorServiceSpy.getDebitoren).toHaveBeenCalled();
    });

    it('should show error message and reload when deletion fails', () => {
      debitorServiceSpy.deleteDebitor.mockReturnValue(throwError(() => new Error('Delete failed')));
      debitorServiceSpy.getDebitoren.mockClear();
      component.onDeleteSelected();
      expect(component.message).toBe('FEHLER_SAMMEL_LOESCHEN_DEBITOR');
      expect(component.messageType).toBe('error');
      expect(debitorServiceSpy.getDebitoren).toHaveBeenCalled();
    });
  });

  describe('loadDebitoren - selection reset', () => {
    it('should clear selectedIds when debitoren are reloaded', () => {
      component.selectedIds.add(1);
      component.selectedIds.add(2);
      component.loadDebitoren();
      expect(component.selectedIds.size).toBe(0);
    });
  });
});
