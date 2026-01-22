import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TarifListComponent } from './tarif-list.component';
import { TarifService } from '../../services/tarif.service';
import { TranslationService } from '../../services/translation.service';
import { Tarif, TarifTyp, ValidationResult } from '../../models/tarif.model';
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
      'getAllTarife', 'createTarif', 'updateTarif', 'deleteTarif', 'validateQuartale', 'validateJahre'
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

    it('should have menu items for edit, copy and delete', () => {
      expect(component.menuItems.length).toBe(3);
      expect(component.menuItems[0].action).toBe('edit');
      expect(component.menuItems[1].action).toBe('copy');
      expect(component.menuItems[2].action).toBe('delete');
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

  describe('onCopy', () => {
    it('should set selectedTarif without id and show form', () => {
      const tarif = mockTarife[0];
      component.onCopy(tarif);
      expect(component.selectedTarif).toBeTruthy();
      expect(component.selectedTarif!.id).toBeUndefined();
      expect(component.showForm).toBeTrue();
    });

    it('should copy all properties except id', () => {
      const tarif = mockTarife[0];
      component.onCopy(tarif);
      expect(component.selectedTarif!.bezeichnung).toBe(tarif.bezeichnung);
      expect(component.selectedTarif!.tariftyp).toBe(tarif.tariftyp);
      expect(component.selectedTarif!.preis).toBe(tarif.preis);
      expect(component.selectedTarif!.gueltigVon).toBe(tarif.gueltigVon);
      expect(component.selectedTarif!.gueltigBis).toBe(tarif.gueltigBis);
    });

    it('should create a new object (not reference)', () => {
      const tarif = mockTarife[0];
      component.onCopy(tarif);
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

    it('should call onCopy for copy action', () => {
      spyOn(component, 'onCopy');
      component.onMenuAction('copy', mockTarife[0]);
      expect(component.onCopy).toHaveBeenCalledWith(mockTarife[0]);
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

  // ==================== Validation Tests ====================

  describe('onValidateQuartale', () => {
    const validResult: ValidationResult = {
      valid: true,
      message: 'Alle Quartale sind vollständig abgedeckt',
      errors: []
    };

    const invalidResult: ValidationResult = {
      valid: false,
      message: 'Validierungsfehler',
      errors: ['Q1/2024: VNB-Tarif fehlt für: 01.01.2024']
    };

    it('should call validateQuartale on service', () => {
      tarifServiceSpy.validateQuartale.and.returnValue(of(validResult));
      component.onValidateQuartale();
      expect(tarifServiceSpy.validateQuartale).toHaveBeenCalled();
    });

    it('should show success message when valid', () => {
      tarifServiceSpy.validateQuartale.and.returnValue(of(validResult));
      component.onValidateQuartale();
      expect(component.message).toBe('VALIDIERUNG_ERFOLGREICH');
      expect(component.messageType).toBe('success');
      expect(component.validationErrors).toEqual([]);
    });

    it('should show error message with errors when invalid', () => {
      tarifServiceSpy.validateQuartale.and.returnValue(of(invalidResult));
      component.onValidateQuartale();
      expect(component.message).toBe('VALIDIERUNG_FEHLER');
      expect(component.messageType).toBe('error');
      expect(component.validationErrors.length).toBe(1);
      expect(component.validationErrors[0]).toContain('Q1/2024');
    });

    it('should set message as persistent when validation fails', () => {
      tarifServiceSpy.validateQuartale.and.returnValue(of(invalidResult));
      component.onValidateQuartale();
      expect(component.messagePersistent).toBeTrue();
    });

    it('should show error message on service error', () => {
      tarifServiceSpy.validateQuartale.and.returnValue(throwError(() => new Error('Network error')));
      component.onValidateQuartale();
      expect(component.message).toBe('FEHLER_VALIDIERUNG');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onValidateJahre', () => {
    const validResult: ValidationResult = {
      valid: true,
      message: 'Alle Jahre sind vollständig abgedeckt',
      errors: []
    };

    const invalidResult: ValidationResult = {
      valid: false,
      message: 'Validierungsfehler',
      errors: ['2024: VNB-Tarif fehlt für: 01.01.2024', '2025: ZEV-Tarif fehlt für: 01.01.2025']
    };

    it('should call validateJahre on service', () => {
      tarifServiceSpy.validateJahre.and.returnValue(of(validResult));
      component.onValidateJahre();
      expect(tarifServiceSpy.validateJahre).toHaveBeenCalled();
    });

    it('should show success message when valid', () => {
      tarifServiceSpy.validateJahre.and.returnValue(of(validResult));
      component.onValidateJahre();
      expect(component.message).toBe('VALIDIERUNG_ERFOLGREICH');
      expect(component.messageType).toBe('success');
    });

    it('should show multiple errors when invalid', () => {
      tarifServiceSpy.validateJahre.and.returnValue(of(invalidResult));
      component.onValidateJahre();
      expect(component.validationErrors.length).toBe(2);
      expect(component.validationErrors[0]).toContain('2024');
      expect(component.validationErrors[1]).toContain('2025');
    });
  });

  describe('dismissMessage', () => {
    it('should clear message and validation errors', () => {
      component.message = 'VALIDIERUNG_FEHLER';
      component.validationErrors = ['Error 1', 'Error 2'];
      component.messagePersistent = true;

      component.dismissMessage();

      expect(component.message).toBe('');
      expect(component.validationErrors).toEqual([]);
      expect(component.messagePersistent).toBeFalse();
    });
  });

  describe('persistent message behavior', () => {
    it('should not auto-clear persistent messages', fakeAsync(() => {
      const invalidResult: ValidationResult = {
        valid: false,
        message: 'Validierungsfehler',
        errors: ['Q1/2024: Error']
      };
      tarifServiceSpy.validateQuartale.and.returnValue(of(invalidResult));

      component.onValidateQuartale();

      expect(component.message).toBe('VALIDIERUNG_FEHLER');
      expect(component.messagePersistent).toBeTrue();

      tick(5000);

      // Message should still be visible (persistent)
      expect(component.message).toBe('VALIDIERUNG_FEHLER');
    }));

    it('should auto-clear success messages', fakeAsync(() => {
      const validResult: ValidationResult = {
        valid: true,
        message: 'Alle Quartale sind vollständig abgedeckt',
        errors: []
      };
      tarifServiceSpy.validateQuartale.and.returnValue(of(validResult));

      component.onValidateQuartale();

      expect(component.message).toBe('VALIDIERUNG_ERFOLGREICH');
      expect(component.messagePersistent).toBeFalse();

      tick(5000);

      // Success message should be cleared after timeout
      expect(component.message).toBe('');
    }));
  });
});
