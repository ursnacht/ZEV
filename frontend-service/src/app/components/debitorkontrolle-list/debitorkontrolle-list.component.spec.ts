import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
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
  let debitorServiceSpy: jasmine.SpyObj<DebitorService>;
  let einheitServiceSpy: jasmine.SpyObj<EinheitService>;
  let mieterServiceSpy: jasmine.SpyObj<MieterService>;
  let translationServiceSpy: jasmine.SpyObj<TranslationService>;

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
    debitorServiceSpy = jasmine.createSpyObj('DebitorService', [
      'getDebitoren', 'createDebitor', 'updateDebitor', 'deleteDebitor'
    ]);
    einheitServiceSpy = jasmine.createSpyObj('EinheitService', ['getAllEinheiten']);
    mieterServiceSpy = jasmine.createSpyObj('MieterService', ['getAllMieter']);
    translationServiceSpy = jasmine.createSpyObj('TranslationService', ['translate']);

    debitorServiceSpy.getDebitoren.and.returnValue(of(mockDebitoren));
    debitorServiceSpy.createDebitor.and.returnValue(of(mockDebitoren[0]));
    debitorServiceSpy.updateDebitor.and.returnValue(of(mockDebitoren[0]));
    debitorServiceSpy.deleteDebitor.and.returnValue(of(void 0));
    einheitServiceSpy.getAllEinheiten.and.returnValue(of(mockEinheiten));
    mieterServiceSpy.getAllMieter.and.returnValue(of(mockMieter));
    translationServiceSpy.translate.and.callFake((key: string) => key);

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
      expect(component.einheiten.every(e => e.typ === EinheitTyp.CONSUMER)).toBeTrue();
    });

    it('should not show form initially', () => {
      expect(component.showForm).toBeFalse();
    });

    it('should have default sort by mieterName ascending', () => {
      expect(component.sortColumn).toBe('mieterName');
      expect(component.sortDirection).toBe('asc');
    });

    it('should set default quarter dates', () => {
      expect(component.dateFrom).toBeTruthy();
      expect(component.dateTo).toBeTruthy();
    });

    it('should have menu items for edit and delete', () => {
      expect(component.menuItems.length).toBe(2);
      expect(component.menuItems[0].action).toBe('edit');
      expect(component.menuItems[1].action).toBe('delete');
    });
  });

  describe('loadDebitoren', () => {
    it('should update debitoren array on success', () => {
      component.debitoren = [];
      component.loadDebitoren();
      expect(component.debitoren).toEqual(jasmine.arrayContaining(mockDebitoren));
    });

    it('should show error message on failure', () => {
      debitorServiceSpy.getDebitoren.and.returnValue(throwError(() => new Error('Network error')));
      component.loadDebitoren();
      expect(component.message).toBe('FEHLER_LADEN_DEBITOREN');
      expect(component.messageType).toBe('error');
    });

    it('should not call service when dateFrom is empty', () => {
      debitorServiceSpy.getDebitoren.calls.reset();
      component.dateFrom = '';
      component.loadDebitoren();
      expect(debitorServiceSpy.getDebitoren).not.toHaveBeenCalled();
    });

    it('should not call service when dateTo is empty', () => {
      debitorServiceSpy.getDebitoren.calls.reset();
      component.dateTo = '';
      component.loadDebitoren();
      expect(debitorServiceSpy.getDebitoren).not.toHaveBeenCalled();
    });
  });

  describe('loadEinheiten', () => {
    it('should show error message on failure', () => {
      einheitServiceSpy.getAllEinheiten.and.returnValue(throwError(() => new Error('Network error')));
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
      mieterServiceSpy.getAllMieter.and.returnValue(throwError(() => new Error('Network error')));
      component.loadMieter();
      expect(component.message).toBe('FEHLER_LADEN_MIETER');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onQuarterSelected', () => {
    it('should update dateFrom and dateTo and reload', () => {
      debitorServiceSpy.getDebitoren.calls.reset();
      component.onQuarterSelected({ von: '2025-04-01', bis: '2025-06-30' });
      expect(component.dateFrom).toBe('2025-04-01');
      expect(component.dateTo).toBe('2025-06-30');
      expect(debitorServiceSpy.getDebitoren).toHaveBeenCalled();
    });
  });

  describe('onDateChange', () => {
    it('should reload debitoren', () => {
      debitorServiceSpy.getDebitoren.calls.reset();
      component.onDateChange();
      expect(debitorServiceSpy.getDebitoren).toHaveBeenCalled();
    });
  });

  describe('onCreateNew', () => {
    it('should set selectedDebitor to null and show form', () => {
      component.selectedDebitor = mockDebitoren[0];
      component.onCreateNew();
      expect(component.selectedDebitor).toBeNull();
      expect(component.showForm).toBeTrue();
    });
  });

  describe('onEdit', () => {
    it('should set selectedDebitor and show form', () => {
      component.onEdit(mockDebitoren[0]);
      expect(component.selectedDebitor).toEqual(mockDebitoren[0]);
      expect(component.showForm).toBeTrue();
    });

    it('should create a copy of the debitor, not a reference', () => {
      component.onEdit(mockDebitoren[0]);
      expect(component.selectedDebitor).not.toBe(mockDebitoren[0]);
    });
  });

  describe('onDelete', () => {
    beforeEach(() => {
      spyOn(window, 'confirm').and.returnValue(true);
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
      debitorServiceSpy.getDebitoren.calls.reset();
      component.onDelete(1);
      expect(debitorServiceSpy.getDebitoren).toHaveBeenCalled();
    });

    it('should show success message after delete', () => {
      component.onDelete(1);
      expect(component.message).toBe('DEBITOR_GELOESCHT');
      expect(component.messageType).toBe('success');
    });

    it('should not call deleteDebitor when user cancels', () => {
      (window.confirm as jasmine.Spy).and.returnValue(false);
      component.onDelete(1);
      expect(debitorServiceSpy.deleteDebitor).not.toHaveBeenCalled();
    });

    it('should show error message on delete failure', () => {
      debitorServiceSpy.deleteDebitor.and.returnValue(throwError(() => new Error('Delete failed')));
      component.onDelete(1);
      expect(component.message).toBe('FEHLER_LOESCHEN_DEBITOR');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onMenuAction', () => {
    it('should call onEdit for edit action', () => {
      spyOn(component, 'onEdit');
      component.onMenuAction('edit', mockDebitoren[0]);
      expect(component.onEdit).toHaveBeenCalledWith(mockDebitoren[0]);
    });

    it('should call onDelete for delete action', () => {
      spyOn(component, 'onDelete');
      component.onMenuAction('delete', mockDebitoren[0]);
      expect(component.onDelete).toHaveBeenCalledWith(mockDebitoren[0].id);
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
      debitorServiceSpy.createDebitor.and.returnValue(of({ ...newDebitor, id: 3 }));
    });

    it('should call createDebitor when debitor has no id', () => {
      component.onFormSubmit(newDebitor);
      expect(debitorServiceSpy.createDebitor).toHaveBeenCalledWith(newDebitor);
    });

    it('should hide form after successful create', () => {
      component.showForm = true;
      component.onFormSubmit(newDebitor);
      expect(component.showForm).toBeFalse();
    });

    it('should show success message after create', () => {
      component.onFormSubmit(newDebitor);
      expect(component.message).toBe('DEBITOR_ERSTELLT');
      expect(component.messageType).toBe('success');
    });

    it('should show error message on create failure', () => {
      debitorServiceSpy.createDebitor.and.returnValue(throwError(() => ({ error: 'CUSTOM_ERROR' })));
      component.onFormSubmit(newDebitor);
      expect(component.message).toBe('CUSTOM_ERROR');
      expect(component.messageType).toBe('error');
    });

    it('should show default error key when no error body', () => {
      debitorServiceSpy.createDebitor.and.returnValue(throwError(() => ({})));
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
      debitorServiceSpy.updateDebitor.and.returnValue(of(existingDebitor));
    });

    it('should call updateDebitor when debitor has id', () => {
      component.onFormSubmit(existingDebitor);
      expect(debitorServiceSpy.updateDebitor).toHaveBeenCalledWith(1, existingDebitor);
    });

    it('should hide form after successful update', () => {
      component.showForm = true;
      component.onFormSubmit(existingDebitor);
      expect(component.showForm).toBeFalse();
    });

    it('should show success message after update', () => {
      component.onFormSubmit(existingDebitor);
      expect(component.message).toBe('DEBITOR_AKTUALISIERT');
      expect(component.messageType).toBe('success');
    });

    it('should show error message on update failure', () => {
      debitorServiceSpy.updateDebitor.and.returnValue(throwError(() => ({ error: 'UPDATE_ERROR' })));
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
      expect(component.showForm).toBeFalse();
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
      expect(component.isOffen(debitor)).toBeTrue();
    });

    it('should return false when zahldatum is set', () => {
      const debitor: Debitor = { mieterId: 10, betrag: 100, datumVon: '2025-01-01', datumBis: '2025-03-31', zahldatum: '2025-04-01' };
      expect(component.isOffen(debitor)).toBeFalse();
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
      debitorServiceSpy.createDebitor.and.returnValue(of({ id: 3, mieterId: 10, betrag: 50, datumVon: '2025-01-01', datumBis: '2025-03-31' }));
      component.onFormSubmit({ mieterId: 10, betrag: 50, datumVon: '2025-01-01', datumBis: '2025-03-31' });

      expect(component.message).toBe('DEBITOR_ERSTELLT');
      tick(5000);
      expect(component.message).toBe('');
    }));

    it('should not auto-clear error message', fakeAsync(() => {
      debitorServiceSpy.getDebitoren.and.returnValue(throwError(() => new Error('Network error')));
      component.loadDebitoren();

      expect(component.message).toBe('FEHLER_LADEN_DEBITOREN');
      tick(5000);
      expect(component.message).toBe('FEHLER_LADEN_DEBITOREN');
    }));
  });
});
