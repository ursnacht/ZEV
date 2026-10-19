import { createSpyObj, SpyObj } from '../../../testing/spy';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChangeDetectorRef } from '@angular/core';
import { TranslationEditorComponent } from './translation-editor.component';
import { TranslationService, Translation } from '../../services/translation.service';
import { of, throwError } from 'rxjs';

/**
 * Specs/Übersetzungsverwaltung.md, Abschnitt 3 (Anzeige & Suche, Erstellen, Bearbeiten, Löschen,
 * Export / Import).
 */
describe('TranslationEditorComponent', () => {
  let component: TranslationEditorComponent;
  let fixture: ComponentFixture<TranslationEditorComponent>;
  let translationServiceSpy: SpyObj<TranslationService>;

  const mockTranslations: Translation[] = [
    { key: 'SPEICHERN', deutsch: 'Speichern', englisch: 'Save' },
    { key: 'ABBRECHEN', deutsch: 'Abbrechen', englisch: 'Cancel' },
    { key: 'LOESCHEN', deutsch: 'Löschen', englisch: 'Delete' }
  ];

  /** OnPush: nach direkten Zustandsaenderungen die Ansicht neu zeichnen. */
  const render = () => {
    fixture.componentRef.injector.get(ChangeDetectorRef).markForCheck();
    fixture.detectChanges();
  };
  const element = (): HTMLElement => fixture.nativeElement;
  const keys = () => component.filteredTranslations.map(t => t.key);

  beforeEach(async () => {
    translationServiceSpy = createSpyObj<TranslationService>('TranslationService', [
      'getAllTranslations', 'createTranslation', 'saveTranslation', 'deleteTranslation',
      'importTranslations', 'translate'
    ]);
    translationServiceSpy.getAllTranslations.mockReturnValue(of(mockTranslations.map(t => ({ ...t }))));
    translationServiceSpy.createTranslation.mockImplementation((t: Translation) => of({ ...t }));
    translationServiceSpy.saveTranslation.mockImplementation((t: Translation) => of({ ...t }));
    translationServiceSpy.deleteTranslation.mockReturnValue(of(void 0));
    translationServiceSpy.importTranslations.mockReturnValue(of({ importiert: 2 }));
    translationServiceSpy.translate.mockImplementation((key: string) => key);

    await TestBed.configureTestingModule({
      imports: [TranslationEditorComponent],
      providers: [{ provide: TranslationService, useValue: translationServiceSpy }]
    }).compileComponents();

    fixture = TestBed.createComponent(TranslationEditorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  // vi.spyOn auf document/window/URL sonst ueber den Test hinaus aktiv
  afterEach(() => vi.restoreAllMocks());

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Anzeige & Sortierung', () => {
    it('should show the translations sorted by key ascending on load', () => {
      expect(keys()).toEqual(['ABBRECHEN', 'LOESCHEN', 'SPEICHERN']);
      const ersteSpalte = Array.from(element().querySelectorAll('.zev-table tbody tr td:first-child'))
        .map(td => td.textContent?.trim());
      expect(ersteSpalte).toEqual(['ABBRECHEN', 'LOESCHEN', 'SPEICHERN']);
    });

    it('should reverse the direction on a second click on the same header', () => {
      component.onSort('key');
      expect(component.sortDirection).toBe('desc');
      expect(keys()).toEqual(['SPEICHERN', 'LOESCHEN', 'ABBRECHEN']);

      component.onSort('key');
      expect(component.sortDirection).toBe('asc');
      expect(keys()).toEqual(['ABBRECHEN', 'LOESCHEN', 'SPEICHERN']);
    });

    it('should sort ascending by a newly clicked column', () => {
      component.onSort('englisch');

      expect(component.sortColumn).toBe('englisch');
      expect(component.sortDirection).toBe('asc');
      expect(component.filteredTranslations.map(t => t.englisch)).toEqual(['Cancel', 'Delete', 'Save']);
    });

    it('should hide the table when there are no translations', () => {
      translationServiceSpy.getAllTranslations.mockReturnValue(of([]));
      component.loadTranslations();
      render();

      expect(element().querySelector('.zev-table')).toBeNull();
    });
  });

  describe('Suche', () => {
    it('should filter live and case-insensitive by key, deutsch and englisch; an empty search shows all', () => {
      component.searchTerm = 'speich';
      component.onSearchChange();
      expect(keys()).toEqual(['SPEICHERN']);

      component.searchTerm = 'LÖSCH';
      component.onSearchChange();
      expect(keys()).toEqual(['LOESCHEN']);

      component.searchTerm = 'cancel';
      component.onSearchChange();
      expect(keys()).toEqual(['ABBRECHEN']);

      component.searchTerm = '   ';
      component.onSearchChange();
      expect(keys()).toEqual(['ABBRECHEN', 'LOESCHEN', 'SPEICHERN']);
    });

    it('should show the counter "filtered / total"', () => {
      component.searchTerm = 'save';
      component.onSearchChange();
      render();

      expect(element().querySelector('.search-count')?.textContent?.trim()).toBe('1 / 3');
    });
  });

  describe('Erstellen', () => {
    const submitButton = (): HTMLButtonElement =>
      element().querySelector('.zev-form-actions .zev-button--primary')!;

    it('should disable the submit button while the key is empty', () => {
      component.formTranslation = { key: '', deutsch: 'Text', englisch: 'Text' };
      render();
      expect(submitButton().disabled).toBe(true);

      component.formTranslation = { key: 'NEU', deutsch: '', englisch: '' };
      render();
      expect(submitButton().disabled).toBe(false);
    });

    it('should not call the backend without a key', () => {
      component.formTranslation = { key: '', deutsch: 'Text', englisch: 'Text' };
      component.submitForm();

      expect(translationServiceSpy.createTranslation).not.toHaveBeenCalled();
    });

    it('should show the new entry and reset the form after creating', () => {
      component.formTranslation = { key: 'NEU_KEY', deutsch: 'Neu', englisch: 'New' };
      component.submitForm();

      expect(keys()).toContain('NEU_KEY');
      expect(component.formTranslation).toEqual({ key: '', deutsch: '', englisch: '' });
      expect(component.editMode).toBe(false);
    });

    it('should not add a second row when the backend rejects an existing key (409)', () => {
      vi.spyOn(console, 'error').mockImplementation(() => {});
      translationServiceSpy.createTranslation.mockReturnValue(throwError(() => ({ status: 409 })));
      component.formTranslation = { key: 'SPEICHERN', deutsch: 'X', englisch: 'X' };

      component.submitForm();

      expect(component.translations.filter(t => t.key === 'SPEICHERN').length).toBe(1);
      expect(component.translations.find(t => t.key === 'SPEICHERN')?.deutsch).toBe('Speichern');
    });
  });

  describe('Bearbeiten', () => {
    it('should prefill the form with a copy of the row and make the key readonly', () => {
      const zeile = component.translations.find(t => t.key === 'SPEICHERN')!;
      vi.spyOn(window, 'scrollTo').mockImplementation(() => {});

      component.editTranslation(zeile);
      render();

      expect(component.formTranslation).toEqual(zeile);
      expect(component.formTranslation).not.toBe(zeile);
      expect(component.editMode).toBe(true);
      const keyInput = element().querySelector('.zev-form-container input') as HTMLInputElement;
      expect(keyInput.readOnly).toBe(true);
    });

    it('should reset the form without saving on cancel', () => {
      vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
      component.editTranslation(component.translations[0]);
      component.formTranslation.deutsch = 'Geändert';

      component.resetForm();

      expect(component.formTranslation).toEqual({ key: '', deutsch: '', englisch: '' });
      expect(component.editMode).toBe(false);
      expect(translationServiceSpy.saveTranslation).not.toHaveBeenCalled();
    });
  });

  describe('Löschen', () => {
    it('should ask with the key name in the confirmation', () => {
      translationServiceSpy.translate.mockImplementation((key: string) =>
        key === 'CONFIRM_DELETE_TRANSLATION' ? 'Übersetzung «{key}» löschen?' : key);
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);

      component.deleteTranslation(component.translations.find(t => t.key === 'LOESCHEN')!);

      expect(confirmSpy).toHaveBeenCalledWith('Übersetzung «LOESCHEN» löschen?');
      expect(translationServiceSpy.deleteTranslation).not.toHaveBeenCalled();
    });
  });

  describe('Export', () => {
    it('should download uebersetzungen.csv with BOM, header and all translations', async () => {
      let blob: Blob | undefined;
      vi.spyOn(URL, 'createObjectURL').mockImplementation((b: Blob | MediaSource) => {
        blob = b as Blob;
        return 'blob:test';
      });
      vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
      const link = document.createElement('a');
      const clickSpy = vi.spyOn(link, 'click').mockImplementation(() => {});
      vi.spyOn(document, 'createElement').mockReturnValue(link);

      component.exportTranslations();

      expect(link.download).toBe('uebersetzungen.csv');
      expect(clickSpy).toHaveBeenCalled();
      // blob.text() verschluckt das BOM beim Dekodieren - deshalb die Bytes pruefen
      const bytes = new Uint8Array(await blob!.arrayBuffer());
      expect(Array.from(bytes.slice(0, 3))).toEqual([0xEF, 0xBB, 0xBF]);
      const text = await blob!.text();
      expect(text.startsWith('key,deutsch,englisch')).toBe(true);
      expect(text).toContain('SPEICHERN,Speichern,Save');
      expect(text).toContain('LOESCHEN,Löschen,Delete');
    });

    it('should disable the export button when there are no translations', () => {
      const exportButton = (): HTMLButtonElement => element().querySelector('.zev-table-actions button')!;
      expect(exportButton().disabled).toBe(false);

      translationServiceSpy.getAllTranslations.mockReturnValue(of([]));
      component.loadTranslations();
      render();

      expect(exportButton().disabled).toBe(true);
    });
  });

  describe('Import', () => {
    const importiere = (inhalt: string) => {
      const input = { files: [new File([inhalt], 'import.csv', { type: 'text/csv' })], value: 'C:\\fakepath\\import.csv' };
      component.onImportFileSelected({ target: input } as unknown as Event);
      return input;
    };

    it('should accept only csv files and default to overwrite = yes', () => {
      const fileInput = element().querySelector('input[type="file"]') as HTMLInputElement;
      expect(fileInput.accept).toContain('.csv');
      expect(component.importUeberschreiben).toBe(true);
    });

    it('should open the file dialog when clicking the import button', () => {
      const fileInput = element().querySelector('input[type="file"]') as HTMLInputElement;
      const clickSpy = vi.spyOn(fileInput, 'click').mockImplementation(() => {});
      const importButton = Array.from(element().querySelectorAll('.zev-table-actions button'))
        .find(b => b.textContent?.includes('TRANSLATION_IMPORT')) as HTMLButtonElement;

      importButton.click();

      expect(clickSpy).toHaveBeenCalled();
    });

    it('should offer two radios Ja/Nein, preselect Ja and switch to false on Nein', async () => {
      await fixture.whenStable();
      render();
      const radios = Array.from(element().querySelectorAll('input[name="importUeberschreiben"]')) as HTMLInputElement[];
      const labels = radios.map(r => r.parentElement?.textContent?.trim());

      expect(labels).toEqual(['JA', 'NEIN']);
      expect(radios[0].checked).toBe(true);
      expect(radios[1].checked).toBe(false);

      radios[1].click();
      await fixture.whenStable();

      expect(component.importUeberschreiben).toBe(false);
    });

    it('should import the rows, report the count, reload the list and reset the file input', async () => {
      translationServiceSpy.translate.mockImplementation((key: string) =>
        key === 'TRANSLATION_IMPORT_ERFOLG' ? '{count} Übersetzungen importiert' : key);
      translationServiceSpy.getAllTranslations.mockClear();
      component.importUeberschreiben = false;

      const input = importiere('key,deutsch,englisch\r\nNEU_A,A,A\r\nNEU_B,B,B\r\n');

      await vi.waitFor(() => expect(translationServiceSpy.importTranslations).toHaveBeenCalled());
      expect(translationServiceSpy.importTranslations).toHaveBeenCalledWith([
        { key: 'NEU_A', deutsch: 'A', englisch: 'A' },
        { key: 'NEU_B', deutsch: 'B', englisch: 'B' }
      ], false);
      expect(component.message).toBe('2 Übersetzungen importiert');
      expect(component.messageType).toBe('success');
      expect(translationServiceSpy.getAllTranslations).toHaveBeenCalled();
      expect(input.value).toBe('');
    });

    it('should show an error and not call the backend for a file with only the header', async () => {
      const input = importiere('key,deutsch,englisch\r\n');

      await vi.waitFor(() => expect(component.message).toBe('TRANSLATION_IMPORT_FORMAT_FEHLER'));
      expect(component.messageType).toBe('error');
      expect(translationServiceSpy.importTranslations).not.toHaveBeenCalled();
      expect(input.value).toBe('');
    });
  });
});
