import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslationService, Translation } from '../../services/translation.service';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { KebabMenuComponent, KebabMenuItem } from '../kebab-menu/kebab-menu.component';
import { ColumnResizeDirective } from '../../directives/column-resize.directive';
import { IconComponent } from '../icon/icon.component';
import { toCsv, parseCsv } from '../../utils/csv-utils';

/** Kopfzeile der Export-/Import-CSV. */
const CSV_HEADER = ['key', 'deutsch', 'englisch'];

@Component({
  selector: 'app-translation-editor',
  standalone: true,
  imports: [FormsModule, TranslatePipe, KebabMenuComponent, ColumnResizeDirective, IconComponent],
  templateUrl: './translation-editor.component.html',
  styleUrls: ['./translation-editor.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TranslationEditorComponent implements OnInit {
  translations: Translation[] = [];
  filteredTranslations: Translation[] = [];
  searchTerm = '';
  formTranslation: Translation = { key: '', deutsch: '', englisch: '' };
  editMode = false;
  loading = false;
  sortColumn: 'key' | 'deutsch' | 'englisch' | null = 'key';
  sortDirection: 'asc' | 'desc' = 'asc';

  /** Rückmeldung für Import/Export (bereits übersetzter Text). */
  message = '';
  messageType: 'success' | 'error' = 'success';

  /** Import-Option: bestehende Keys überschreiben (true) oder überspringen (false). Default: Ja. */
  importUeberschreiben = true;

  menuItems: KebabMenuItem[] = [
    { label: 'BEARBEITEN', action: 'edit', icon: 'edit-2' },
    { label: 'LOESCHEN', action: 'delete', danger: true, icon: 'trash-2' }
  ];

  constructor(
    private translationService: TranslationService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    this.loadTranslations();
  }

  loadTranslations() {
    this.loading = true;
    this.cdr.markForCheck();
    this.translationService.getAllTranslations().subscribe({
      next: (data) => {
        this.translations = data;
        this.applyFilter();
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load translations', err);
        this.loading = false;
        this.cdr.markForCheck();
      }
    });
  }

  onSearchChange() {
    this.applyFilter();
  }

  clearSearch() {
    this.searchTerm = '';
    this.applyFilter();
  }

  private applyFilter() {
    if (!this.searchTerm.trim()) {
      this.filteredTranslations = this.translations;
    } else {
      const term = this.searchTerm.toLowerCase();
      this.filteredTranslations = this.translations.filter(t =>
        t.key.toLowerCase().includes(term) ||
        (t.deutsch && t.deutsch.toLowerCase().includes(term)) ||
        (t.englisch && t.englisch.toLowerCase().includes(term))
      );
    }
    this.applySorting();
  }

  submitForm() {
    if (!this.formTranslation.key) return;

    if (this.editMode) {
      this.translationService.saveTranslation(this.formTranslation).subscribe({
        next: () => {
          const idx = this.translations.findIndex(t => t.key === this.formTranslation.key);
          if (idx >= 0) {
            this.translations[idx] = { ...this.formTranslation };
          }
          this.resetForm();
          this.applyFilter();
          this.cdr.markForCheck();
        },
        error: (err) => console.error('Failed to save translation', err)
      });
    } else {
      this.translationService.createTranslation(this.formTranslation).subscribe({
        next: (saved) => {
          this.translations.push(saved);
          this.resetForm();
          this.applyFilter();
          this.cdr.markForCheck();
        },
        error: (err) => console.error('Failed to create translation', err)
      });
    }
  }

  editTranslation(translation: Translation) {
    this.formTranslation = { ...translation };
    this.editMode = true;
    this.cdr.markForCheck();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  resetForm() {
    this.formTranslation = { key: '', deutsch: '', englisch: '' };
    this.editMode = false;
  }

  deleteTranslation(translation: Translation) {
    const confirmMessage = this.translationService.translate('CONFIRM_DELETE_TRANSLATION')
      .replace('{key}', translation.key);

    if (!confirm(confirmMessage)) {
      return;
    }

    this.translationService.deleteTranslation(translation.key).subscribe({
      next: () => {
        this.translations = this.translations.filter(t => t.key !== translation.key);
        this.applyFilter();
        this.cdr.markForCheck();
      },
      error: (err) => console.error('Failed to delete translation', err)
    });
  }

  /** Exportiert alle Übersetzungen als CSV-Datei (Komma-getrennt, clientseitig, verlustfrei). */
  exportTranslations(): void {
    const rows = [
      CSV_HEADER,
      ...this.translations.map(t => [t.key ?? '', t.deutsch ?? '', t.englisch ?? ''])
    ];
    // UTF-8-BOM voranstellen, damit Excel Umlaute korrekt erkennt
    const csv = '﻿' + toCsv(rows);
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'uebersetzungen.csv';
    link.click();
    window.URL.revokeObjectURL(url);
  }

  /** Liest die gewählte CSV-Datei ein und importiert die enthaltenen Übersetzungen. */
  onImportFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const list = this.csvRowsToTranslations(parseCsv(reader.result as string));
        if (list.length === 0) {
          this.showMessage(this.translationService.translate('TRANSLATION_IMPORT_FORMAT_FEHLER'), 'error');
          return;
        }
        this.doImport(list);
      } catch {
        this.showMessage(this.translationService.translate('TRANSLATION_IMPORT_FORMAT_FEHLER'), 'error');
      } finally {
        // Zurücksetzen, damit dieselbe Datei erneut gewählt werden kann
        input.value = '';
      }
    };
    reader.readAsText(file);
  }

  /** Wandelt geparste CSV-Zeilen in Übersetzungen um; eine optionale Kopfzeile wird übersprungen. */
  private csvRowsToTranslations(rows: string[][]): Translation[] {
    let dataRows = rows;
    if (dataRows.length > 0) {
      const head = dataRows[0].map(c => (c ?? '').trim().toLowerCase());
      if (head[0] === 'key' && head[1] === 'deutsch' && head[2] === 'englisch') {
        dataRows = dataRows.slice(1);
      }
    }
    return dataRows.map(r => ({
      key: (r[0] ?? '').trim(),
      deutsch: r[1] ?? '',
      englisch: r[2] ?? ''
    }));
  }

  private doImport(list: Translation[]): void {
    this.translationService.importTranslations(list, this.importUeberschreiben).subscribe({
      next: (res) => {
        this.showMessage(
          this.translationService.translate('TRANSLATION_IMPORT_ERFOLG').replace('{count}', String(res.importiert)),
          'success'
        );
        this.loadTranslations();
      },
      error: (err) => {
        const key = typeof err.error === 'string' ? err.error : 'TRANSLATION_IMPORT_FEHLER';
        this.showMessage(this.translationService.translate(key), 'error');
      }
    });
  }

  dismissMessage(): void {
    this.message = '';
    this.cdr.markForCheck();
  }

  private showMessage(text: string, type: 'success' | 'error'): void {
    this.message = text;
    this.messageType = type;
    this.cdr.markForCheck();
    if (type === 'success') {
      // Erfolg automatisch ausblenden (Projektkonvention: Erfolg 5s, Fehler bleibt)
      setTimeout(() => this.dismissMessage(), 5000);
    }
  }

  onMenuAction(action: string, translation: Translation): void {
    switch (action) {
      case 'edit':
        this.editTranslation(translation);
        break;
      case 'delete':
        this.deleteTranslation(translation);
        break;
    }
  }

  onSort(column: 'key' | 'deutsch' | 'englisch'): void {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
    this.applySorting();
  }

  private applySorting() {
    const column = this.sortColumn;
    if (!column) return;

    this.filteredTranslations.sort((a, b) => {
      const aValue = (a[column] || '').toLowerCase();
      const bValue = (b[column] || '').toLowerCase();

      if (aValue < bValue) return this.sortDirection === 'asc' ? -1 : 1;
      if (aValue > bValue) return this.sortDirection === 'asc' ? 1 : -1;
      return 0;
    });
  }
}
