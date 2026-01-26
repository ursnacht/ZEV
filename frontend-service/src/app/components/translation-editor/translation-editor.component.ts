import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslationService, Translation } from '../../services/translation.service';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { KebabMenuComponent, KebabMenuItem } from '../kebab-menu/kebab-menu.component';
import { ColumnResizeDirective } from '../../directives/column-resize.directive';
import { IconComponent } from '../icon/icon.component';

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

  menuItems: KebabMenuItem[] = [
    { label: 'BEARBEITEN', action: 'edit', icon: 'edit-2' },
    { label: 'DELETE', action: 'delete', danger: true, icon: 'trash-2' }
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
