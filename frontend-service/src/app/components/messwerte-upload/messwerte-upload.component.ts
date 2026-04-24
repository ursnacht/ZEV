import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { WithMessage } from '../../utils/with-message';
import { EinheitService } from '../../services/einheit.service';
import { Einheit } from '../../models/einheit.model';
import { TranslationService } from '../../services/translation.service';

import { TranslatePipe } from '../../pipes/translate.pipe';
import { IconComponent } from '../icon/icon.component';

export type UploadStatus = 'pending' | 'matching' | 'ready' | 'uploading' | 'done' | 'error';

export interface UploadEntry {
  file: File;
  einheitId: number | null;
  date: string;
  status: UploadStatus;
  errorMessage: string | null;
  matchConfidence: number | null;
}

@Component({
  selector: 'app-messwerte-upload',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, IconComponent],
  templateUrl: './messwerte-upload.component.html',
  styleUrls: ['./messwerte-upload.component.css']
})
export class MesswerteUploadComponent extends WithMessage implements OnInit {
  einheiten: Einheit[] = [];
  entries: UploadEntry[] = [];
  importing = false;

  isDragOver = false;

  constructor(
    private http: HttpClient,
    private einheitService: EinheitService,
    private translationService: TranslationService
  ) { super(); }

  ngOnInit(): void {
    this.loadEinheiten();
  }

  loadEinheiten(): void {
    this.einheitService.getAllEinheiten().subscribe({
      next: (data) => {
        this.einheiten = data.sort((a, b) => {
          const nameA = (a.name || '').toLowerCase();
          const nameB = (b.name || '').toLowerCase();
          return nameA.localeCompare(nameB);
        });
      },
      error: (error) => {
        this.showMessage('Fehler beim Laden der Einheiten: ' + error.message, 'error');
      }
    });
  }

  onFileChange(event: any): void {
    const files = event.target.files;
    if (files && files.length > 0) {
      this.addFiles(files);
      event.target.value = '';
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;

    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      this.addFiles(files);
    }
  }

  removeEntry(entry: UploadEntry): void {
    this.entries = this.entries.filter(e => e !== entry);
  }

  get isAnyMatching(): boolean {
    return this.entries.some(e => e.status === 'matching');
  }

  get canImport(): boolean {
    return (
      this.entries.length > 0 &&
      !this.importing &&
      !this.isAnyMatching &&
      this.entries.every(e => e.einheitId !== null && e.date !== '' && e.status !== 'uploading')
    );
  }

  importAll(): void {
    const toImport = this.entries.filter(e => e.status === 'ready' || e.status === 'error');
    if (toImport.length === 0) return;

    this.importing = true;
    let successCount = 0;
    const total = toImport.length;

    const processNext = (index: number): void => {
      if (index >= toImport.length) {
        this.importing = false;
        this.entries = this.entries.filter(e => e.status !== 'done');
        this.showMessage(
          this.translationService.translate('DATEIEN_IMPORT_ERGEBNIS')
            .replace('{success}', String(successCount))
            .replace('{total}', String(total)),
          successCount === total ? 'success' : 'error'
        );
        return;
      }

      const entry = toImport[index];
      entry.status = 'uploading';

      const formData = new FormData();
      formData.append('date', entry.date);
      formData.append('einheitId', entry.einheitId!.toString());
      formData.append('file', entry.file);

      this.http.post<any>('http://localhost:8090/api/messwerte/upload', formData).subscribe({
        next: (response) => {
          if (response.status === 'success') {
            entry.status = 'done';
            successCount++;
          } else {
            entry.status = 'error';
            entry.errorMessage = response.message;
          }
          processNext(index + 1);
        },
        error: (error) => {
          entry.status = 'error';
          entry.errorMessage = error.message;
          processNext(index + 1);
        }
      });
    };

    processNext(0);
  }

  formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  }

  private addFiles(files: FileList | File[]): void {
    const fileArray = Array.from(files as FileList);
    const newFiles = fileArray.filter(f => {
      if (!f.name.endsWith('.csv')) {
        this.showMessage(`${f.name}: ${this.translationService.translate('NUR_CSV_DATEIEN')}`, 'error');
        return false;
      }
      if (this.entries.some(e => e.file.name === f.name)) {
        this.showMessage(
          `${f.name}: ${this.translationService.translate('DATEI_BEREITS_IN_LISTE')}`, 'error'
        );
        return false;
      }
      return true;
    });

    const newEntries: UploadEntry[] = newFiles.map(f => ({
      file: f,
      einheitId: this.einheiten.length > 0 ? (this.einheiten[0].id ?? null) : null,
      date: '',
      status: 'matching' as UploadStatus,
      errorMessage: null,
      matchConfidence: null,
    }));

    this.entries = [...this.entries, ...newEntries];

    newEntries.forEach(entry => {
      this.extractDateFromFile(entry);
      this.matchEinheitForEntry(entry);
    });
  }

  private matchEinheitForEntry(entry: UploadEntry): void {
    this.einheitService.matchEinheitByFilename(entry.file.name).subscribe({
      next: (result) => {
        entry.matchConfidence = result.confidence;
        if (result.matched && result.einheitId) {
          entry.einheitId = result.einheitId;
        }
        entry.status = 'ready';
      },
      error: () => {
        entry.matchConfidence = null;
        entry.status = 'ready';
      }
    });
  }

  private extractDateFromFile(entry: UploadEntry): void {
    const reader = new FileReader();
    reader.onload = (e) => {
      const content = e.target?.result as string;
      if (content) {
        const lines = content.split('\n');
        if (lines.length >= 2) {
          const parsedDate = this.parseEnglishDate(lines[1].split(',')[0]);
          if (parsedDate) {
            entry.date = parsedDate;
          }
        }
      }
    };
    reader.readAsText(entry.file);
  }

  private parseEnglishDate(dateStr: string): string | null {
    const months: { [key: string]: string } = {
      'Jan': '01', 'Feb': '02', 'Mar': '03', 'Apr': '04',
      'May': '05', 'Jun': '06', 'Jul': '07', 'Aug': '08',
      'Sep': '09', 'Oct': '10', 'Nov': '11', 'Dec': '12'
    };

    const trimmed = dateStr.trim();
    const parts = trimmed.split(' ');
    if (parts.length >= 4) {
      const month = months[parts[1]];
      const day = parts[2].padStart(2, '0');
      const year = parts[3];
      if (month && day && year) {
        return `${year}-${month}-${day}`;
      }
    }
    return null;
  }

}
