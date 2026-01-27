import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MieterListComponent } from './mieter-list.component';
import { MieterService } from '../../services/mieter.service';
import { EinheitService } from '../../services/einheit.service';
import { TranslationService } from '../../services/translation.service';
import { Mieter } from '../../models/mieter.model';
import { Einheit, EinheitTyp } from '../../models/einheit.model';
import { of, throwError } from 'rxjs';

describe('MieterListComponent', () => {
  let component: MieterListComponent;
  let fixture: ComponentFixture<MieterListComponent>;
  let mieterServiceSpy: jasmine.SpyObj<MieterService>;
  let einheitServiceSpy: jasmine.SpyObj<EinheitService>;
  let translationServiceSpy: jasmine.SpyObj<TranslationService>;

  const mockEinheiten: Einheit[] = [
    { id: 1, name: 'Wohnung A', typ: EinheitTyp.CONSUMER },
    { id: 2, name: 'Solaranlage', typ: EinheitTyp.PRODUCER },
    { id: 3, name: 'Wohnung B', typ: EinheitTyp.CONSUMER }
  ];

  const mockMieter: Mieter[] = [
    { id: 1, name: 'Max Muster', strasse: 'Musterstr. 1', plz: '8000', ort: 'Zürich', mietbeginn: '2024-01-01', einheitId: 1 },
    { id: 2, name: 'Anna Test', strasse: 'Testweg 2', plz: '3000', ort: 'Bern', mietbeginn: '2024-06-01', mietende: '2025-05-31', einheitId: 3 }
  ];

  beforeEach(async () => {
    mieterServiceSpy = jasmine.createSpyObj('MieterService', [
      'getAllMieter', 'createMieter', 'updateMieter', 'deleteMieter'
    ]);
    einheitServiceSpy = jasmine.createSpyObj('EinheitService', ['getAllEinheiten']);
    translationServiceSpy = jasmine.createSpyObj('TranslationService', ['translate']);

    mieterServiceSpy.getAllMieter.and.returnValue(of(mockMieter));
    einheitServiceSpy.getAllEinheiten.and.returnValue(of(mockEinheiten));
    translationServiceSpy.translate.and.callFake((key: string) => key);

    await TestBed.configureTestingModule({
      imports: [MieterListComponent],
      providers: [
        { provide: MieterService, useValue: mieterServiceSpy },
        { provide: EinheitService, useValue: einheitServiceSpy },
        { provide: TranslationService, useValue: translationServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MieterListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should load mieter on init', () => {
      expect(mieterServiceSpy.getAllMieter).toHaveBeenCalled();
      expect(component.mieter.length).toBe(2);
    });

    it('should load einheiten on init', () => {
      expect(einheitServiceSpy.getAllEinheiten).toHaveBeenCalled();
    });

    it('should filter einheiten to CONSUMER only', () => {
      expect(component.einheiten.length).toBe(2);
      expect(component.einheiten.every(e => e.typ === EinheitTyp.CONSUMER)).toBeTrue();
    });

    it('should not show form initially', () => {
      expect(component.showForm).toBeFalse();
    });

    it('should have default sort by einheitId ascending', () => {
      expect(component.sortColumn).toBe('einheitId');
      expect(component.sortDirection).toBe('asc');
    });

    it('should have menu items for edit, copy and delete', () => {
      expect(component.menuItems.length).toBe(3);
      expect(component.menuItems[0].action).toBe('edit');
      expect(component.menuItems[1].action).toBe('copy');
      expect(component.menuItems[2].action).toBe('delete');
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

  describe('loadEinheiten', () => {
    it('should show error message on failure', () => {
      einheitServiceSpy.getAllEinheiten.and.returnValue(throwError(() => new Error('Network error')));
      component.loadEinheiten();
      expect(component.message).toBe('FEHLER_LADEN_EINHEITEN');
      expect(component.messageType).toBe('error');
    });
  });

  describe('getEinheitName', () => {
    it('should return einheit name when found', () => {
      expect(component.getEinheitName(1)).toBe('Wohnung A');
    });

    it('should return ID fallback when not found', () => {
      expect(component.getEinheitName(999)).toBe('ID: 999');
    });
  });

  describe('onCreateNew', () => {
    it('should set selectedMieter to null and show form', () => {
      component.selectedMieter = mockMieter[0];
      component.onCreateNew();
      expect(component.selectedMieter).toBeNull();
      expect(component.showForm).toBeTrue();
    });
  });

  describe('onEdit', () => {
    it('should set selectedMieter and show form', () => {
      const mieter = mockMieter[0];
      component.onEdit(mieter);
      expect(component.selectedMieter).toEqual(mieter);
      expect(component.showForm).toBeTrue();
    });

    it('should create a copy of the mieter', () => {
      const mieter = mockMieter[0];
      component.onEdit(mieter);
      expect(component.selectedMieter).not.toBe(mieter);
    });
  });

  describe('onCopy', () => {
    it('should set selectedMieter without id and show form', () => {
      const mieter = mockMieter[0];
      component.onCopy(mieter);
      expect(component.selectedMieter).toBeTruthy();
      expect(component.selectedMieter!.id).toBeUndefined();
      expect(component.showForm).toBeTrue();
    });

    it('should copy all properties except id', () => {
      const mieter = mockMieter[0];
      component.onCopy(mieter);
      expect(component.selectedMieter!.name).toBe(mieter.name);
      expect(component.selectedMieter!.strasse).toBe(mieter.strasse);
      expect(component.selectedMieter!.einheitId).toBe(mieter.einheitId);
    });
  });

  describe('onDelete', () => {
    beforeEach(() => {
      spyOn(window, 'confirm').and.returnValue(true);
      mieterServiceSpy.deleteMieter.and.returnValue(of(undefined as any));
    });

    it('should do nothing when id is undefined', () => {
      component.onDelete(undefined);
      expect(mieterServiceSpy.deleteMieter).not.toHaveBeenCalled();
    });

    it('should call confirm dialog', () => {
      component.onDelete(1);
      expect(window.confirm).toHaveBeenCalled();
      expect(translationServiceSpy.translate).toHaveBeenCalledWith('MIETER_LOESCHEN_BESTAETIGUNG');
    });

    it('should delete mieter on confirm', () => {
      component.onDelete(1);
      expect(mieterServiceSpy.deleteMieter).toHaveBeenCalledWith(1);
    });

    it('should reload mieter after successful delete', () => {
      mieterServiceSpy.getAllMieter.calls.reset();
      component.onDelete(1);
      expect(mieterServiceSpy.getAllMieter).toHaveBeenCalled();
    });

    it('should show success message after delete', () => {
      component.onDelete(1);
      expect(component.message).toBe('MIETER_GELOESCHT');
      expect(component.messageType).toBe('success');
    });

    it('should not delete when user cancels', () => {
      (window.confirm as jasmine.Spy).and.returnValue(false);
      component.onDelete(1);
      expect(mieterServiceSpy.deleteMieter).not.toHaveBeenCalled();
    });

    it('should show error message on delete failure', () => {
      mieterServiceSpy.deleteMieter.and.returnValue(throwError(() => new Error('Delete failed')));
      component.onDelete(1);
      expect(component.message).toBe('FEHLER_LOESCHEN_MIETER');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onMenuAction', () => {
    it('should call onEdit for edit action', () => {
      spyOn(component, 'onEdit');
      component.onMenuAction('edit', mockMieter[0]);
      expect(component.onEdit).toHaveBeenCalledWith(mockMieter[0]);
    });

    it('should call onCopy for copy action', () => {
      spyOn(component, 'onCopy');
      component.onMenuAction('copy', mockMieter[0]);
      expect(component.onCopy).toHaveBeenCalledWith(mockMieter[0]);
    });

    it('should call onDelete for delete action', () => {
      spyOn(component, 'onDelete');
      component.onMenuAction('delete', mockMieter[0]);
      expect(component.onDelete).toHaveBeenCalledWith(mockMieter[0].id);
    });
  });

  describe('onFormSubmit - create', () => {
    const newMieter: Mieter = {
      name: 'Neuer Mieter',
      strasse: 'Neue Str. 1',
      plz: '4000',
      ort: 'Basel',
      mietbeginn: '2025-01-01',
      einheitId: 1
    };

    beforeEach(() => {
      mieterServiceSpy.createMieter.and.returnValue(of({ ...newMieter, id: 3 }));
    });

    it('should call createMieter when mieter has no id', () => {
      component.onFormSubmit(newMieter);
      expect(mieterServiceSpy.createMieter).toHaveBeenCalledWith(newMieter);
    });

    it('should hide form after successful create', () => {
      component.showForm = true;
      component.onFormSubmit(newMieter);
      expect(component.showForm).toBeFalse();
    });

    it('should show success message after create', () => {
      component.onFormSubmit(newMieter);
      expect(component.message).toBe('MIETER_ERSTELLT');
      expect(component.messageType).toBe('success');
    });

    it('should show error message on create failure', () => {
      mieterServiceSpy.createMieter.and.returnValue(throwError(() => ({ error: 'CUSTOM_ERROR' })));
      component.onFormSubmit(newMieter);
      expect(component.message).toBe('CUSTOM_ERROR');
      expect(component.messageType).toBe('error');
    });

    it('should show default error message when no error body', () => {
      mieterServiceSpy.createMieter.and.returnValue(throwError(() => ({})));
      component.onFormSubmit(newMieter);
      expect(component.message).toBe('FEHLER_ERSTELLEN_MIETER');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onFormSubmit - update', () => {
    const existingMieter: Mieter = {
      id: 1,
      name: 'Max Muster Updated',
      strasse: 'Musterstr. 1',
      plz: '8000',
      ort: 'Zürich',
      mietbeginn: '2024-01-01',
      einheitId: 1
    };

    beforeEach(() => {
      mieterServiceSpy.updateMieter.and.returnValue(of(existingMieter));
    });

    it('should call updateMieter when mieter has id', () => {
      component.onFormSubmit(existingMieter);
      expect(mieterServiceSpy.updateMieter).toHaveBeenCalledWith(1, existingMieter);
    });

    it('should hide form after successful update', () => {
      component.showForm = true;
      component.onFormSubmit(existingMieter);
      expect(component.showForm).toBeFalse();
    });

    it('should show success message after update', () => {
      component.onFormSubmit(existingMieter);
      expect(component.message).toBe('MIETER_AKTUALISIERT');
      expect(component.messageType).toBe('success');
    });

    it('should show error message on update failure', () => {
      mieterServiceSpy.updateMieter.and.returnValue(throwError(() => ({ error: 'UPDATE_ERROR' })));
      component.onFormSubmit(existingMieter);
      expect(component.message).toBe('UPDATE_ERROR');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onFormCancel', () => {
    it('should hide form and clear selected mieter', () => {
      component.showForm = true;
      component.selectedMieter = mockMieter[0];
      component.onFormCancel();
      expect(component.showForm).toBeFalse();
      expect(component.selectedMieter).toBeNull();
    });
  });

  describe('onSort', () => {
    it('should toggle sort direction when clicking same column', () => {
      component.sortColumn = 'name';
      component.sortDirection = 'asc';
      component.onSort('name');
      expect(component.sortDirection).toBe('desc');
    });

    it('should set new column and reset direction to asc', () => {
      component.sortColumn = 'name';
      component.sortDirection = 'desc';
      component.onSort('strasse');
      expect(component.sortColumn).toBe('strasse');
      expect(component.sortDirection).toBe('asc');
    });

    it('should sort mieter by name ascending', () => {
      component.sortColumn = null;
      component.onSort('name');
      expect(component.mieter[0].name).toBe('Anna Test');
      expect(component.mieter[1].name).toBe('Max Muster');
    });
  });

  describe('formatPlzOrt', () => {
    it('should format plz and ort together', () => {
      expect(component.formatPlzOrt(mockMieter[0])).toBe('8000 Zürich');
    });

    it('should handle missing plz', () => {
      const mieter: Mieter = { name: 'Test', ort: 'Bern', mietbeginn: '2024-01-01', einheitId: 1 };
      expect(component.formatPlzOrt(mieter)).toBe('Bern');
    });

    it('should handle missing ort', () => {
      const mieter: Mieter = { name: 'Test', plz: '3000', mietbeginn: '2024-01-01', einheitId: 1 };
      expect(component.formatPlzOrt(mieter)).toBe('3000');
    });

    it('should handle missing plz and ort', () => {
      const mieter: Mieter = { name: 'Test', mietbeginn: '2024-01-01', einheitId: 1 };
      expect(component.formatPlzOrt(mieter)).toBe('');
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
    it('should clear success message after 5 seconds', fakeAsync(() => {
      mieterServiceSpy.createMieter.and.returnValue(of({
        id: 3, name: 'Test', mietbeginn: '2024-01-01', einheitId: 1
      }));

      component.onFormSubmit({ name: 'Test', mietbeginn: '2024-01-01', einheitId: 1 });

      expect(component.message).toBeTruthy();
      tick(5000);
      expect(component.message).toBe('');
    }));

    it('should not auto-clear error messages', fakeAsync(() => {
      mieterServiceSpy.getAllMieter.and.returnValue(throwError(() => new Error('Network error')));
      component.loadMieter();

      expect(component.message).toBe('FEHLER_LADEN_MIETER');
      tick(5000);
      expect(component.message).toBe('FEHLER_LADEN_MIETER');
    }));
  });
});
