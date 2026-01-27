import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { EinheitListComponent } from './einheit-list.component';
import { EinheitService } from '../../services/einheit.service';
import { TranslationService } from '../../services/translation.service';
import { Einheit, EinheitTyp } from '../../models/einheit.model';
import { of, throwError } from 'rxjs';

describe('EinheitListComponent', () => {
  let component: EinheitListComponent;
  let fixture: ComponentFixture<EinheitListComponent>;
  let einheitServiceSpy: jasmine.SpyObj<EinheitService>;
  let translationServiceSpy: jasmine.SpyObj<TranslationService>;

  const mockEinheiten: Einheit[] = [
    { id: 1, name: 'Wohnung A', typ: EinheitTyp.CONSUMER, messpunkt: 'MP-001' },
    { id: 2, name: 'Solaranlage', typ: EinheitTyp.PRODUCER, messpunkt: 'MP-002' }
  ];

  beforeEach(async () => {
    einheitServiceSpy = jasmine.createSpyObj('EinheitService', [
      'getAllEinheiten', 'createEinheit', 'updateEinheit', 'deleteEinheit'
    ]);
    translationServiceSpy = jasmine.createSpyObj('TranslationService', ['translate']);

    einheitServiceSpy.getAllEinheiten.and.returnValue(of(mockEinheiten));
    translationServiceSpy.translate.and.callFake((key: string) => key);

    await TestBed.configureTestingModule({
      imports: [EinheitListComponent],
      providers: [
        { provide: EinheitService, useValue: einheitServiceSpy },
        { provide: TranslationService, useValue: translationServiceSpy }
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
      expect(component.showForm).toBeFalse();
    });

    it('should have default sort by name ascending', () => {
      expect(component.sortColumn).toBe('name');
      expect(component.sortDirection).toBe('asc');
    });

    it('should have menu items for edit and delete', () => {
      expect(component.menuItems.length).toBe(2);
      expect(component.menuItems[0].action).toBe('edit');
      expect(component.menuItems[1].action).toBe('delete');
    });
  });

  describe('loadEinheiten', () => {
    it('should update einheiten array on success', () => {
      component.einheiten = [];
      component.loadEinheiten();
      expect(component.einheiten).toEqual(mockEinheiten);
    });

    it('should show error message on failure', () => {
      einheitServiceSpy.getAllEinheiten.and.returnValue(throwError(() => new Error('Network error')));
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
      expect(component.showForm).toBeTrue();
    });
  });

  describe('onEdit', () => {
    it('should set selectedEinheit and show form', () => {
      const einheit = mockEinheiten[0];
      component.onEdit(einheit);
      expect(component.selectedEinheit).toEqual(einheit);
      expect(component.showForm).toBeTrue();
    });

    it('should create a copy of the einheit', () => {
      const einheit = mockEinheiten[0];
      component.onEdit(einheit);
      expect(component.selectedEinheit).not.toBe(einheit);
    });
  });

  describe('onDelete', () => {
    beforeEach(() => {
      spyOn(window, 'confirm').and.returnValue(true);
      einheitServiceSpy.deleteEinheit.and.returnValue(of(undefined as any));
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
      einheitServiceSpy.getAllEinheiten.calls.reset();
      component.onDelete(1);
      expect(einheitServiceSpy.getAllEinheiten).toHaveBeenCalled();
    });

    it('should show success message after delete', () => {
      component.onDelete(1);
      expect(component.message).toBe('EINHEIT_GELOESCHT');
      expect(component.messageType).toBe('success');
    });

    it('should not delete when user cancels', () => {
      (window.confirm as jasmine.Spy).and.returnValue(false);
      component.onDelete(1);
      expect(einheitServiceSpy.deleteEinheit).not.toHaveBeenCalled();
    });

    it('should show error message on delete failure', () => {
      einheitServiceSpy.deleteEinheit.and.returnValue(throwError(() => new Error('Delete failed')));
      component.onDelete(1);
      expect(component.message).toContain('Fehler beim Löschen');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onMenuAction', () => {
    it('should call onEdit for edit action', () => {
      spyOn(component, 'onEdit');
      component.onMenuAction('edit', mockEinheiten[0]);
      expect(component.onEdit).toHaveBeenCalledWith(mockEinheiten[0]);
    });

    it('should call onDelete for delete action', () => {
      spyOn(component, 'onDelete');
      component.onMenuAction('delete', mockEinheiten[0]);
      expect(component.onDelete).toHaveBeenCalledWith(mockEinheiten[0].id);
    });
  });

  describe('onFormSubmit - create', () => {
    const newEinheit: Einheit = {
      name: 'Wohnung B',
      typ: EinheitTyp.CONSUMER
    };

    beforeEach(() => {
      einheitServiceSpy.createEinheit.and.returnValue(of({ ...newEinheit, id: 3 }));
    });

    it('should call createEinheit when einheit has no id', () => {
      component.onFormSubmit(newEinheit);
      expect(einheitServiceSpy.createEinheit).toHaveBeenCalledWith(newEinheit);
    });

    it('should hide form after successful create', () => {
      component.showForm = true;
      component.onFormSubmit(newEinheit);
      expect(component.showForm).toBeFalse();
    });

    it('should show success message after create', () => {
      component.onFormSubmit(newEinheit);
      expect(component.message).toBe('EINHEIT_ERSTELLT');
      expect(component.messageType).toBe('success');
    });

    it('should reload einheiten after create', () => {
      einheitServiceSpy.getAllEinheiten.calls.reset();
      component.onFormSubmit(newEinheit);
      expect(einheitServiceSpy.getAllEinheiten).toHaveBeenCalled();
    });

    it('should show error message on create failure', () => {
      einheitServiceSpy.createEinheit.and.returnValue(throwError(() => new Error('Create failed')));
      component.onFormSubmit(newEinheit);
      expect(component.message).toContain('Fehler beim Erstellen');
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
      einheitServiceSpy.updateEinheit.and.returnValue(of(existingEinheit));
    });

    it('should call updateEinheit when einheit has id', () => {
      component.onFormSubmit(existingEinheit);
      expect(einheitServiceSpy.updateEinheit).toHaveBeenCalledWith(1, existingEinheit);
    });

    it('should hide form after successful update', () => {
      component.showForm = true;
      component.onFormSubmit(existingEinheit);
      expect(component.showForm).toBeFalse();
    });

    it('should show success message after update', () => {
      component.onFormSubmit(existingEinheit);
      expect(component.message).toBe('EINHEIT_AKTUALISIERT');
      expect(component.messageType).toBe('success');
    });

    it('should show error message on update failure', () => {
      einheitServiceSpy.updateEinheit.and.returnValue(throwError(() => new Error('Update failed')));
      component.onFormSubmit(existingEinheit);
      expect(component.message).toContain('Fehler beim Aktualisieren');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onFormCancel', () => {
    it('should hide form and clear selected einheit', () => {
      component.showForm = true;
      component.selectedEinheit = mockEinheiten[0];
      component.onFormCancel();
      expect(component.showForm).toBeFalse();
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
      einheitServiceSpy.createEinheit.and.returnValue(of({
        id: 3, name: 'Test', typ: EinheitTyp.CONSUMER
      }));

      component.onFormSubmit({ name: 'Test', typ: EinheitTyp.CONSUMER });

      expect(component.message).toBeTruthy();
      tick(5000);
      expect(component.message).toBe('');
    }));
  });
});
