import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TarifListComponent } from './tarif-list.component';
import { TarifService } from '../../services/tarif.service';
import { TranslationService } from '../../services/translation.service';
import { Tarif, TarifTyp } from '../../models/tarif.model';
import { of, throwError } from 'rxjs';

describe('TarifListComponent', () => {
  let component: TarifListComponent;
  let fixture: ComponentFixture<TarifListComponent>;
  let tarifServiceSpy: jasmine.SpyObj<TarifService>;
  let translationServiceSpy: jasmine.SpyObj<TranslationService>;

  const mockTarife: Tarif[] = [
    {
      id: 1,
      bezeichnung: 'ZEV Tarif',
      tariftyp: TarifTyp.ZEV,
      preis: 0.195,
      gueltigVon: '2024-01-01',
      gueltigBis: '2024-06-30'
    },
    {
      id: 2,
      bezeichnung: 'VNB Tarif',
      tariftyp: TarifTyp.VNB,
      preis: 0.342,
      gueltigVon: '2024-01-01',
      gueltigBis: '2024-12-31'
    }
  ];

  beforeEach(async () => {
    tarifServiceSpy = jasmine.createSpyObj('TarifService', [
      'getAllTarife', 'createTarif', 'updateTarif', 'deleteTarif'
    ]);
    translationServiceSpy = jasmine.createSpyObj('TranslationService', ['translate']);

    tarifServiceSpy.getAllTarife.and.returnValue(of(mockTarife));
    translationServiceSpy.translate.and.callFake((key: string) => key);

    await TestBed.configureTestingModule({
      imports: [TarifListComponent],
      providers: [
        { provide: TarifService, useValue: tarifServiceSpy },
        { provide: TranslationService, useValue: translationServiceSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(TarifListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('initialization', () => {
    it('should load tarife on init', () => {
      expect(tarifServiceSpy.getAllTarife).toHaveBeenCalled();
      expect(component.tarife.length).toBe(2);
    });

    it('should not show form initially', () => {
      expect(component.showForm).toBeFalse();
    });

    it('should have default sort by tariftyp ascending', () => {
      expect(component.sortColumn).toBe('tariftyp');
      expect(component.sortDirection).toBe('asc');
    });

    it('should have menu items for edit and delete', () => {
      expect(component.menuItems.length).toBe(2);
      expect(component.menuItems[0].action).toBe('edit');
      expect(component.menuItems[1].action).toBe('delete');
    });
  });

  describe('loadTarife', () => {
    it('should update tarife array on success', () => {
      component.tarife = [];
      component.loadTarife();
      expect(component.tarife).toEqual(mockTarife);
    });

    it('should show error message on failure', () => {
      tarifServiceSpy.getAllTarife.and.returnValue(throwError(() => new Error('Network error')));
      component.loadTarife();
      expect(component.message).toBe('FEHLER_LADEN_TARIFE');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onCreateNew', () => {
    it('should set selectedTarif to null and show form', () => {
      component.selectedTarif = mockTarife[0];
      component.onCreateNew();
      expect(component.selectedTarif).toBeNull();
      expect(component.showForm).toBeTrue();
    });
  });

  describe('onEdit', () => {
    it('should set selectedTarif and show form', () => {
      const tarif = mockTarife[0];
      component.onEdit(tarif);
      expect(component.selectedTarif).toEqual(tarif);
      expect(component.showForm).toBeTrue();
    });

    it('should create a copy of the tarif', () => {
      const tarif = mockTarife[0];
      component.onEdit(tarif);
      expect(component.selectedTarif).not.toBe(tarif);
    });
  });

  describe('onDelete', () => {
    beforeEach(() => {
      spyOn(window, 'confirm').and.returnValue(true);
      tarifServiceSpy.deleteTarif.and.returnValue(of(undefined));
    });

    it('should do nothing when id is undefined', () => {
      component.onDelete(undefined);
      expect(tarifServiceSpy.deleteTarif).not.toHaveBeenCalled();
    });

    it('should call confirm dialog', () => {
      component.onDelete(1);
      expect(window.confirm).toHaveBeenCalled();
      expect(translationServiceSpy.translate).toHaveBeenCalledWith('CONFIRM_DELETE_TARIF');
    });

    it('should delete tarif on confirm', () => {
      component.onDelete(1);
      expect(tarifServiceSpy.deleteTarif).toHaveBeenCalledWith(1);
    });

    it('should reload tarife after successful delete', () => {
      tarifServiceSpy.getAllTarife.calls.reset();
      component.onDelete(1);
      expect(tarifServiceSpy.getAllTarife).toHaveBeenCalled();
    });

    it('should show success message after delete', () => {
      component.onDelete(1);
      expect(component.message).toBe('TARIF_GELOESCHT');
      expect(component.messageType).toBe('success');
    });

    it('should not delete when user cancels', () => {
      (window.confirm as jasmine.Spy).and.returnValue(false);
      component.onDelete(1);
      expect(tarifServiceSpy.deleteTarif).not.toHaveBeenCalled();
    });

    it('should show error message on delete failure', () => {
      tarifServiceSpy.deleteTarif.and.returnValue(throwError(() => new Error('Delete failed')));
      component.onDelete(1);
      expect(component.message).toBe('FEHLER_LOESCHEN_TARIF');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onMenuAction', () => {
    it('should call onEdit for edit action', () => {
      spyOn(component, 'onEdit');
      component.onMenuAction('edit', mockTarife[0]);
      expect(component.onEdit).toHaveBeenCalledWith(mockTarife[0]);
    });

    it('should call onDelete for delete action', () => {
      spyOn(component, 'onDelete');
      component.onMenuAction('delete', mockTarife[0]);
      expect(component.onDelete).toHaveBeenCalledWith(mockTarife[0].id);
    });
  });

  describe('onFormSubmit - create', () => {
    const newTarif: Tarif = {
      bezeichnung: 'New Tarif',
      tariftyp: TarifTyp.ZEV,
      preis: 0.20,
      gueltigVon: '2025-01-01',
      gueltigBis: '2025-12-31'
    };

    beforeEach(() => {
      tarifServiceSpy.createTarif.and.returnValue(of({ ...newTarif, id: 3 }));
    });

    it('should call createTarif when tarif has no id', () => {
      component.onFormSubmit(newTarif);
      expect(tarifServiceSpy.createTarif).toHaveBeenCalledWith(newTarif);
    });

    it('should hide form after successful create', () => {
      component.showForm = true;
      component.onFormSubmit(newTarif);
      expect(component.showForm).toBeFalse();
    });

    it('should show success message after create', () => {
      component.onFormSubmit(newTarif);
      expect(component.message).toBe('TARIF_ERSTELLT');
      expect(component.messageType).toBe('success');
    });

    it('should reload tarife after create', () => {
      tarifServiceSpy.getAllTarife.calls.reset();
      component.onFormSubmit(newTarif);
      expect(tarifServiceSpy.getAllTarife).toHaveBeenCalled();
    });

    it('should show error message on create failure', () => {
      tarifServiceSpy.createTarif.and.returnValue(throwError(() => ({ error: 'CUSTOM_ERROR' })));
      component.onFormSubmit(newTarif);
      expect(component.message).toBe('CUSTOM_ERROR');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onFormSubmit - update', () => {
    const existingTarif: Tarif = {
      id: 1,
      bezeichnung: 'Updated Tarif',
      tariftyp: TarifTyp.ZEV,
      preis: 0.21,
      gueltigVon: '2024-01-01',
      gueltigBis: '2024-12-31'
    };

    beforeEach(() => {
      tarifServiceSpy.updateTarif.and.returnValue(of(existingTarif));
    });

    it('should call updateTarif when tarif has id', () => {
      component.onFormSubmit(existingTarif);
      expect(tarifServiceSpy.updateTarif).toHaveBeenCalledWith(1, existingTarif);
    });

    it('should hide form after successful update', () => {
      component.showForm = true;
      component.onFormSubmit(existingTarif);
      expect(component.showForm).toBeFalse();
    });

    it('should show success message after update', () => {
      component.onFormSubmit(existingTarif);
      expect(component.message).toBe('TARIF_AKTUALISIERT');
      expect(component.messageType).toBe('success');
    });
  });

  describe('onFormCancel', () => {
    it('should hide form and clear selected tarif', () => {
      component.showForm = true;
      component.selectedTarif = mockTarife[0];
      component.onFormCancel();
      expect(component.showForm).toBeFalse();
      expect(component.selectedTarif).toBeNull();
    });
  });

  describe('onSort', () => {
    beforeEach(() => {
      component.tarife = [...mockTarife];
    });

    it('should toggle sort direction when clicking same column', () => {
      component.sortColumn = 'bezeichnung';
      component.sortDirection = 'asc';
      component.onSort('bezeichnung');
      expect(component.sortDirection).toBe('desc');
    });

    it('should set new column and reset direction to asc', () => {
      component.sortColumn = 'bezeichnung';
      component.sortDirection = 'desc';
      component.onSort('preis');
      expect(component.sortColumn).toBe('preis');
      expect(component.sortDirection).toBe('asc');
    });

    it('should sort tarife by bezeichnung ascending', () => {
      component.onSort('bezeichnung');
      expect(component.tarife[0].bezeichnung).toBe('VNB Tarif');
      expect(component.tarife[1].bezeichnung).toBe('ZEV Tarif');
    });

    it('should sort tarife by preis descending', () => {
      component.sortColumn = 'preis';
      component.onSort('preis');
      expect(component.tarife[0].preis).toBe(0.342);
      expect(component.tarife[1].preis).toBe(0.195);
    });
  });

  describe('formatDate', () => {
    it('should format date string to Swiss format', () => {
      const result = component.formatDate('2024-06-15');
      expect(result).toContain('15');
      expect(result).toContain('6') ;
      expect(result).toContain('2024');
    });

    it('should return dash for empty date', () => {
      expect(component.formatDate('')).toBe('-');
    });
  });

  describe('formatPreis', () => {
    it('should format price with 5 decimal places', () => {
      expect(component.formatPreis(0.195)).toBe('0.19500');
      expect(component.formatPreis(0.34192)).toBe('0.34192');
    });
  });

  describe('getTarifTypLabel', () => {
    it('should return ZEV label for ZEV type', () => {
      expect(component.getTarifTypLabel(TarifTyp.ZEV)).toBe('ZEV (Solarstrom)');
    });

    it('should return VNB label for VNB type', () => {
      expect(component.getTarifTypLabel(TarifTyp.VNB)).toBe('VNB (Netzstrom)');
    });
  });

  describe('message timeout', () => {
    it('should clear message after 5 seconds', fakeAsync(() => {
      tarifServiceSpy.createTarif.and.returnValue(of({
        id: 1,
        bezeichnung: 'Test',
        tariftyp: TarifTyp.ZEV,
        preis: 0.20,
        gueltigVon: '2025-01-01',
        gueltigBis: '2025-12-31'
      }));

      component.onFormSubmit({
        bezeichnung: 'Test',
        tariftyp: TarifTyp.ZEV,
        preis: 0.20,
        gueltigVon: '2025-01-01',
        gueltigBis: '2025-12-31'
      });

      expect(component.message).toBeTruthy();
      tick(5000);
      expect(component.message).toBe('');
    }));
  });
});
