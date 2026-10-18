import { createSpyObj, SpyObj } from '../../../testing/spy';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { fakeAsync, tick } from '../../../testing/fake-async';
import { TarifListComponent } from './tarif-list.component';
import { TarifService } from '../../services/tarif.service';
import { TranslationService } from '../../services/translation.service';
import { Mengeneinheit, Tarif, TarifTyp, ValidationResult } from '../../models/tarif.model';
import { of, throwError } from 'rxjs';
import { By } from '@angular/platform-browser';
import { CdkDropList, CdkDropListGroup } from '@angular/cdk/drag-drop';

describe('TarifListComponent', () => {
  let component: TarifListComponent;
  let fixture: ComponentFixture<TarifListComponent>;
  let tarifServiceSpy: SpyObj<TarifService>;
  let translationServiceSpy: SpyObj<TranslationService>;

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
    tarifServiceSpy = createSpyObj<TarifService>('TarifService', [
      'getAllTarife', 'createTarif', 'updateTarif', 'deleteTarif', 'validateQuartale', 'validateJahre',
      'verschiebeTarif'
    ]);
    translationServiceSpy = createSpyObj<TranslationService>('TranslationService', ['translate']);

    tarifServiceSpy.getAllTarife.mockReturnValue(of(mockTarife));
    translationServiceSpy.translate.mockImplementation((key: string) => key);

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
      expect(component.showForm).toBe(false);
    });

    it('should open only the panel of the current year', () => {
      expect(component.offen).toEqual({ zukuenftig: false, aktuell: true, alt: false });
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
      tarifServiceSpy.getAllTarife.mockReturnValue(throwError(() => new Error('Network error')));
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
      expect(component.showForm).toBe(true);
    });
  });

  describe('onEdit', () => {
    it('should set selectedTarif and show form', () => {
      const tarif = mockTarife[0];
      component.onEdit(tarif);
      expect(component.selectedTarif).toEqual(tarif);
      expect(component.showForm).toBe(true);
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
      expect(component.showForm).toBe(true);
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
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      tarifServiceSpy.deleteTarif.mockReturnValue(of(undefined));
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
      tarifServiceSpy.getAllTarife.mockClear();
      component.onDelete(1);
      expect(tarifServiceSpy.getAllTarife).toHaveBeenCalled();
    });

    it('should show success message after delete', () => {
      component.onDelete(1);
      expect(component.message).toBe('TARIF_GELOESCHT');
      expect(component.messageType).toBe('success');
    });

    it('should not delete when user cancels', () => {
      (window.confirm as unknown as ReturnType<typeof vi.fn>).mockReturnValue(false);
      component.onDelete(1);
      expect(tarifServiceSpy.deleteTarif).not.toHaveBeenCalled();
    });

    it('should show error message on delete failure', () => {
      tarifServiceSpy.deleteTarif.mockReturnValue(throwError(() => new Error('Delete failed')));
      component.onDelete(1);
      expect(component.message).toBe('FEHLER_LOESCHEN_TARIF');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onMenuAction', () => {
    it('should call onEdit for edit action', () => {
      vi.spyOn(component, 'onEdit').mockImplementation(() => {});
      component.onMenuAction('edit', mockTarife[0]);
      expect(component.onEdit).toHaveBeenCalledWith(mockTarife[0]);
    });

    it('should call onCopy for copy action', () => {
      vi.spyOn(component, 'onCopy').mockImplementation(() => {});
      component.onMenuAction('copy', mockTarife[0]);
      expect(component.onCopy).toHaveBeenCalledWith(mockTarife[0]);
    });

    it('should call onDelete for delete action', () => {
      vi.spyOn(component, 'onDelete').mockImplementation(() => {});
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
      tarifServiceSpy.createTarif.mockReturnValue(of({ ...newTarif, id: 3 }));
    });

    it('should call createTarif when tarif has no id', () => {
      component.onFormSubmit(newTarif);
      expect(tarifServiceSpy.createTarif).toHaveBeenCalledWith(newTarif);
    });

    it('should hide form after successful create', () => {
      component.showForm = true;
      component.onFormSubmit(newTarif);
      expect(component.showForm).toBe(false);
    });

    it('should show success message after create', () => {
      component.onFormSubmit(newTarif);
      expect(component.message).toBe('TARIF_ERSTELLT');
      expect(component.messageType).toBe('success');
    });

    it('should reload tarife after create', () => {
      tarifServiceSpy.getAllTarife.mockClear();
      component.onFormSubmit(newTarif);
      expect(tarifServiceSpy.getAllTarife).toHaveBeenCalled();
    });

    it('should show error message on create failure', () => {
      tarifServiceSpy.createTarif.mockReturnValue(throwError(() => ({ error: 'CUSTOM_ERROR' })));
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
      tarifServiceSpy.updateTarif.mockReturnValue(of(existingTarif));
    });

    it('should call updateTarif when tarif has id', () => {
      component.onFormSubmit(existingTarif);
      expect(tarifServiceSpy.updateTarif).toHaveBeenCalledWith(1, existingTarif);
    });

    it('should hide form after successful update', () => {
      component.showForm = true;
      component.onFormSubmit(existingTarif);
      expect(component.showForm).toBe(false);
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
      expect(component.showForm).toBe(false);
      expect(component.selectedTarif).toBeNull();
    });
  });

  describe('gruppiere', () => {
    const jahr = new Date().getFullYear();
    const t = (id: number, von: string, bis: string): Tarif => ({
      id, bezeichnung: 'T' + id, tariftyp: TarifTyp.VNB, preis: 0.1, gueltigVon: von, gueltigBis: bis
    });

    it('should assign tariffs by validity to future, current and past', () => {
      const gruppen = component.gruppiere([
        t(1, `${jahr + 1}-01-01`, `${jahr + 1}-12-31`),
        t(2, `${jahr}-04-01`, `${jahr}-06-30`),
        t(3, `${jahr - 1}-01-01`, `${jahr - 1}-12-31`)
      ]);
      expect(gruppen.zukuenftig.map(x => x.id)).toEqual([1]);
      expect(gruppen.aktuell.map(x => x.id)).toEqual([2]);
      expect(gruppen.alt.map(x => x.id)).toEqual([3]);
    });

    it('should put a tariff spanning into the current year into the current panel', () => {
      // 01.10. Vorjahr bis 31.12. dieses Jahres: gilt heute noch
      const gruppen = component.gruppiere([t(4, `${jahr - 1}-10-01`, `${jahr}-12-31`)]);
      expect(gruppen.aktuell.map(x => x.id)).toEqual([4]);
    });

    it('should treat the year boundaries as part of the current year', () => {
      const gruppen = component.gruppiere([
        t(5, `${jahr - 1}-01-01`, `${jahr}-01-01`),   // endet am 1. Januar
        t(6, `${jahr}-12-31`, `${jahr + 1}-12-31`)     // beginnt am 31. Dezember
      ]);
      expect(gruppen.aktuell.map(x => x.id)).toEqual([5, 6]);
    });

    it('should keep the server order within a panel', () => {
      const gruppen = component.gruppiere([
        t(9, `${jahr}-01-01`, `${jahr}-12-31`),
        t(7, `${jahr}-01-01`, `${jahr}-12-31`)
      ]);
      expect(gruppen.aktuell.map(x => x.id)).toEqual([9, 7]);
    });
  });

  describe('Darstellung der Panels', () => {
    const jahr = new Date().getFullYear();
    const tarifeAllerPanels: Tarif[] = [
      { id: 11, bezeichnung: 'Messtarif', tariftyp: TarifTyp.GRUNDGEBUEHR, preis: 5, produzentVerrechnen: true,
        gueltigVon: `${jahr}-01-01`, gueltigBis: `${jahr}-12-31` },
      { id: 12, bezeichnung: 'Netz', tariftyp: TarifTyp.GRUNDGEBUEHR, preis: 3, produzentVerrechnen: false,
        gueltigVon: `${jahr}-01-01`, gueltigBis: `${jahr}-12-31` },
      { id: 13, bezeichnung: 'ZEV Strom', tariftyp: TarifTyp.ZEV, preis: 0.2,
        gueltigVon: `${jahr}-01-01`, gueltigBis: `${jahr}-12-31` },
      { id: 14, bezeichnung: 'ZEV kuenftig', tariftyp: TarifTyp.ZEV, preis: 0.2,
        gueltigVon: `${jahr + 1}-01-01`, gueltigBis: `${jahr + 1}-12-31` },
      { id: 15, bezeichnung: 'ZEV alt', tariftyp: TarifTyp.ZEV, preis: 0.2,
        gueltigVon: `${jahr - 1}-01-01`, gueltigBis: `${jahr - 1}-12-31` }
    ];

    beforeEach(() => {
      tarifServiceSpy.getAllTarife.mockReturnValue(of(tarifeAllerPanels));
      component.loadTarife();
      component.offen = { zukuenftig: true, aktuell: true, alt: true };
      fixture.detectChanges();
    });

    const zeile = (bezeichnung: string): HTMLTableRowElement =>
      Array.from(fixture.nativeElement.querySelectorAll('tbody tr') as NodeListOf<HTMLTableRowElement>)
        .find(tr => tr.textContent?.includes(bezeichnung))!;

    // Specs/Tarifverwaltung.md, FR-7: Spalte "Produzenten"
    it('should show the producer flag as a disabled checkbox for the Grundgebuehr', () => {
      const an = zeile('Messtarif').querySelector('input[type="checkbox"]') as HTMLInputElement;
      const aus = zeile('Netz').querySelector('input[type="checkbox"]') as HTMLInputElement;
      expect(an.disabled).toBe(true);
      expect(an.checked).toBe(true);
      expect(aus.disabled).toBe(true);
      expect(aus.checked).toBe(false);
    });

    it('should show a dash instead of the producer flag for other tariff types', () => {
      const zev = zeile('ZEV Strom');
      expect(zev.querySelector('input[type="checkbox"]')).toBeNull();
      expect(zev.textContent).toContain('–');
    });

    // Specs/Tarifverwaltung.md, FR-4: verschoben wird nur innerhalb eines Panels
    it('should give each panel its own drop list that is not connected to the others', () => {
      const listen = fixture.debugElement.queryAll(By.directive(CdkDropList))
        .map(d => d.injector.get(CdkDropList));
      expect(listen.length).toBe(3);
      expect(listen.map(l => l.data)).toEqual(
        [component.gruppen.zukuenftig, component.gruppen.aktuell, component.gruppen.alt]);
      for (const liste of listen) {
        expect(liste.connectedTo).toEqual([]);
      }
      // Auch keine gemeinsame Gruppe, die alle Listen stillschweigend verbinden wuerde
      expect(fixture.debugElement.queryAll(By.directive(CdkDropListGroup)).length).toBe(0);
    });
  });

  describe('toggleGruppe', () => {
    it('should open and close a panel', () => {
      component.toggleGruppe('zukuenftig');
      expect(component.offen.zukuenftig).toBe(true);
      component.toggleGruppe('zukuenftig');
      expect(component.offen.zukuenftig).toBe(false);
    });
  });

  describe('onDrop', () => {
    const jahr = new Date().getFullYear();
    const aktuelle: Tarif[] = [1, 2, 3].map(id => ({
      id, bezeichnung: 'T' + id, tariftyp: TarifTyp.VNB, preis: 0.1,
      gueltigVon: `${jahr}-01-01`, gueltigBis: `${jahr}-12-31`
    }));

    beforeEach(() => {
      component.gruppen = { zukuenftig: [], aktuell: [...aktuelle], alt: [] };
      tarifServiceSpy.getAllTarife.mockClear();
    });

    it('should move a tariff up before its new successor and reload', () => {
      tarifServiceSpy.verschiebeTarif.mockReturnValue(of(void 0));

      component.onDrop('aktuell', { previousIndex: 2, currentIndex: 0 } as any);

      // 3 steht jetzt vor 1 (die Liste selbst ist danach neu geladen)
      expect(tarifServiceSpy.verschiebeTarif).toHaveBeenCalledWith(3, { vor: 1 });
      expect(component.messageType).toBe('success');
      expect(component.message).toBe('TARIF_REIHENFOLGE_GESPEICHERT');
      expect(tarifServiceSpy.getAllTarife).toHaveBeenCalled();
    });

    it('should move a tariff down behind its new predecessor', () => {
      tarifServiceSpy.verschiebeTarif.mockReturnValue(of(void 0));

      component.onDrop('aktuell', { previousIndex: 0, currentIndex: 1 } as any);

      // 1 steht jetzt hinter 2
      expect(tarifServiceSpy.verschiebeTarif).toHaveBeenCalledWith(1, { nach: 2 });
    });

    it('should do nothing when dropped at the same place', () => {
      component.onDrop('aktuell', { previousIndex: 1, currentIndex: 1 } as any);
      expect(tarifServiceSpy.verschiebeTarif).not.toHaveBeenCalled();
    });

    it('should show an error and reload the saved order on failure', () => {
      tarifServiceSpy.verschiebeTarif.mockReturnValue(throwError(() => ({ status: 400 })));

      component.onDrop('aktuell', { previousIndex: 0, currentIndex: 2 } as any);

      expect(component.messageType).toBe('error');
      expect(component.message).toBe('FEHLER_REIHENFOLGE_TARIF');
      expect(tarifServiceSpy.getAllTarife).toHaveBeenCalled();
    });
  });

  describe('formatPreis', () => {
    it('should format price with 5 decimal places', () => {
      expect(component.formatPreis(0.195)).toBe('0.19500');
      expect(component.formatPreis(0.34192)).toBe('0.34192');
    });

    it('should group thousands with an apostrophe (Swiss format)', () => {
      expect(component.formatPreis(1234.5)).toBe('1\'234.50000');
    });
  });

  describe('preisEinheit', () => {
    // Die Spaltenueberschrift sagte fest "(CHF/kWh)" - schon fuer die Grundgebuehr falsch.
    const tarif = (tariftyp: TarifTyp, mengeneinheit?: Mengeneinheit): Tarif => ({
      id: 1, bezeichnung: 'Test', tariftyp, preis: 1,
      gueltigVon: '2026-01-01', gueltigBis: '2026-12-31', mengeneinheit
    });

    it('should use kWh for ZEV, VNB and Ladestrom', () => {
      for (const typ of [TarifTyp.ZEV, TarifTyp.VNB, TarifTyp.LADESTROM]) {
        expect(component.preisEinheit(tarif(typ))).toBe('KWH');
      }
    });

    it('should use the month for the Grundgebuehr', () => {
      expect(component.preisEinheit(tarif(TarifTyp.GRUNDGEBUEHR))).toBe('MONAT');
      expect(component.preisEinheit(tarif(TarifTyp.GRUNDGEBUEHR, Mengeneinheit.MONAT))).toBe('MONAT');
    });

    it('should use the day for a Grundgebuehr per day', () => {
      expect(component.preisEinheit(tarif(TarifTyp.GRUNDGEBUEHR, Mengeneinheit.TAG))).toBe('TAG');
    });

    it('should use the unit of a ZUSATZ tariff', () => {
      expect(component.preisEinheit(tarif(TarifTyp.ZUSATZ, Mengeneinheit.STUECK))).toBe('STUECK');
      expect(component.preisEinheit(tarif(TarifTyp.ZUSATZ, Mengeneinheit.MONAT))).toBe('MONAT');
    });
  });

  describe('message timeout', () => {
    it('should clear message after 5 seconds', fakeAsync(() => {
      tarifServiceSpy.createTarif.mockReturnValue(of({
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
      luecken: []
    };

    const invalidResult: ValidationResult = {
      valid: false,
      luecken: [{ periode: 'Q1/2024', luecken: [{ tarifTyp: 'VNB', datum: '01.01.2024', weitere: false }] }]
    };

    it('should call validateQuartale on service', () => {
      tarifServiceSpy.validateQuartale.mockReturnValue(of(validResult));
      component.onValidateQuartale();
      expect(tarifServiceSpy.validateQuartale).toHaveBeenCalled();
    });

    it('should show success message when valid', () => {
      tarifServiceSpy.validateQuartale.mockReturnValue(of(validResult));
      component.onValidateQuartale();
      expect(component.message).toBe('VALIDIERUNG_ERFOLGREICH');
      expect(component.messageType).toBe('success');
      expect(component.validationErrors).toEqual([]);
    });

    it('should show error message with errors when invalid', () => {
      tarifServiceSpy.validateQuartale.mockReturnValue(of(invalidResult));
      component.onValidateQuartale();
      expect(component.message).toBe('VALIDIERUNG_FEHLER');
      expect(component.messageType).toBe('error');
      expect(component.validationErrors.length).toBe(1);
      expect(component.validationErrors[0]).toContain('Q1/2024');
    });

    it('should set message as persistent when validation fails', () => {
      tarifServiceSpy.validateQuartale.mockReturnValue(of(invalidResult));
      component.onValidateQuartale();
      expect(component.messagePersistent).toBe(true);
    });

    it('should show error message on service error', () => {
      tarifServiceSpy.validateQuartale.mockReturnValue(throwError(() => new Error('Network error')));
      component.onValidateQuartale();
      expect(component.message).toBe('FEHLER_VALIDIERUNG');
      expect(component.messageType).toBe('error');
    });
  });

  describe('onValidateJahre', () => {
    const validResult: ValidationResult = {
      valid: true,
      luecken: []
    };

    const invalidResult: ValidationResult = {
      valid: false,
      luecken: [
        { periode: '2024', luecken: [{ tarifTyp: 'VNB', datum: '01.01.2024', weitere: false }] },
        { periode: '2025', luecken: [{ tarifTyp: 'ZEV', datum: '01.01.2025', weitere: false }] }
      ]
    };

    it('should call validateJahre on service', () => {
      tarifServiceSpy.validateJahre.mockReturnValue(of(validResult));
      component.onValidateJahre();
      expect(tarifServiceSpy.validateJahre).toHaveBeenCalled();
    });

    it('should show success message when valid', () => {
      tarifServiceSpy.validateJahre.mockReturnValue(of(validResult));
      component.onValidateJahre();
      expect(component.message).toBe('VALIDIERUNG_ERFOLGREICH');
      expect(component.messageType).toBe('success');
    });

    it('should show multiple errors when invalid', () => {
      tarifServiceSpy.validateJahre.mockReturnValue(of(invalidResult));
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
      expect(component.messagePersistent).toBe(false);
    });
  });

  describe('persistent message behavior', () => {
    it('should not auto-clear persistent messages', fakeAsync(() => {
      const invalidResult: ValidationResult = {
        valid: false,
        luecken: [{ periode: 'Q1/2024', luecken: [{ tarifTyp: 'ZEV', datum: '01.01.2024', weitere: false }] }]
      };
      tarifServiceSpy.validateQuartale.mockReturnValue(of(invalidResult));

      component.onValidateQuartale();

      expect(component.message).toBe('VALIDIERUNG_FEHLER');
      expect(component.messagePersistent).toBe(true);

      tick(5000);

      // Message should still be visible (persistent)
      expect(component.message).toBe('VALIDIERUNG_FEHLER');
    }));

    it('should auto-clear success messages', fakeAsync(() => {
      const validResult: ValidationResult = {
        valid: true,
        luecken: []
      };
      tarifServiceSpy.validateQuartale.mockReturnValue(of(validResult));

      component.onValidateQuartale();

      expect(component.message).toBe('VALIDIERUNG_ERFOLGREICH');
      expect(component.messagePersistent).toBe(false);

      tick(5000);

      // Success message should be cleared after timeout
      expect(component.message).toBe('');
    }));
  });

  describe('preisEinheit mit Jahrespreis', () => {
    it('should show the price per year', () => {
      const tarif: Tarif = {
        id: 5, bezeichnung: 'Messtarif', tariftyp: TarifTyp.GRUNDGEBUEHR, preis: 120,
        gueltigVon: '2026-01-01', gueltigBis: '2026-12-31',
        mengeneinheit: Mengeneinheit.MONAT, preisProJahr: true
      };
      expect(component.preisEinheit(tarif)).toBe('JAHR');
    });
  });
});
