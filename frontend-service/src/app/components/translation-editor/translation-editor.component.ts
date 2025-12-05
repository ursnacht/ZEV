import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslationService, Translation } from '../../services/translation.service';
import { TranslatePipe } from '../../pipes/translate.pipe';

@Component({
  selector: 'app-translation-editor',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './translation-editor.component.html',
  styleUrls: ['./translation-editor.component.css']
})
export class TranslationEditorComponent implements OnInit {
  translations: Translation[] = [];
  newTranslation: Translation = { key: '', deutsch: '', englisch: '' };
  loading = false;
  sortColumn: 'key' | 'deutsch' | 'englisch' | null = 'key';
  sortDirection: 'asc' | 'desc' = 'asc';

  constructor(private translationService: TranslationService) { }

  ngOnInit() {
    this.loadTranslations();
  }

  loadTranslations() {
    this.loading = true;
    this.translationService.getAllTranslations().subscribe({
      next: (data) => {
        this.translations = data;
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load translations', err);
        this.loading = false;
      }
    });
  }

  saveTranslation(translation: Translation) {
    this.translationService.saveTranslation(translation).subscribe({
      next: () => {
        // Optional: Show success message
      },
      error: (err) => console.error('Failed to save translation', err)
    });
  }

  createTranslation() {
    if (!this.newTranslation.key) return;

    this.translationService.createTranslation(this.newTranslation).subscribe({
      next: (saved) => {
        this.translations.push(saved);
        this.newTranslation = { key: '', deutsch: '', englisch: '' };
      },
      error: (err) => console.error('Failed to create translation', err)
    });
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
      },
      error: (err) => console.error('Failed to delete translation', err)
    });
  }

  onSort(column: 'key' | 'deutsch' | 'englisch'): void {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }

    this.translations.sort((a, b) => {
      let aValue: any = a[column];
      let bValue: any = b[column];

      if (aValue === null || aValue === undefined) return 1;
      if (bValue === null || bValue === undefined) return -1;

      if (typeof aValue === 'string') {
        aValue = aValue.toLowerCase();
        bValue = bValue.toLowerCase();
      }

      if (aValue < bValue) {
        return this.sortDirection === 'asc' ? -1 : 1;
      }
      if (aValue > bValue) {
        return this.sortDirection === 'asc' ? 1 : -1;
      }
      return 0;
    });
  }
}
