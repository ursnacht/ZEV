import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { EinheitService, EinheitMatchResponse } from '../../services/einheit.service';
import { Einheit } from '../../models/einheit.model';
import { TranslationService } from '../../services/translation.service';

import { TranslatePipe } from '../../pipes/translate.pipe';

@Component({
  selector: 'app-messwerte-upload',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './messwerte-upload.component.html',
  styleUrls: ['./messwerte-upload.component.css']
})
export class MesswerteUploadComponent implements OnInit {
  date: string = '';
  einheitId: number | null = null;
  einheiten: Einheit[] = [];
  file: File | null = null;
  uploading = false;
  message = '';
  messageType: 'success' | 'error' | 'warning' | '' = '';
  isDragOver = false;

  // KI-Matching properties
  isMatching = false;
  matchResult: EinheitMatchResponse | null = null;
  private readonly CONFIDENCE_THRESHOLD = 0.8;

  constructor(
    private http: HttpClient,
    private einheitService: EinheitService,
    private translationService: TranslationService
  ) { }

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
        if (this.einheiten.length > 0) {
          this.einheitId = this.einheiten[0].id || null;
        }
      },
      error: (error) => {
        this.showMessage('Fehler beim Laden der Einheiten: ' + error.message, 'error');
      }
    });
  }

  onFileChange(event: any): void {
    const files = event.target.files;
    if (files.length > 0) {
      this.handleFile(files[0]);
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
      const file = files[0];
      if (file.name.endsWith('.csv')) {
        this.handleFile(file);
      } else {
        this.showMessage(this.translationService.translate('NUR_CSV_DATEIEN'), 'error');
      }
    }
  }

  private handleFile(file: File): void {
    this.file = file;
    this.extractDateFromFile(file);
    this.matchEinheitByFilename(file.name);
  }

  private matchEinheitByFilename(filename: string): void {
    this.isMatching = true;
    this.matchResult = null;

    this.einheitService.matchEinheitByFilename(filename).subscribe({
      next: (result) => {
        this.isMatching = false;
        this.matchResult = result;

        if (result.matched && result.einheitId && result.confidence > this.CONFIDENCE_THRESHOLD) {
          // High confidence match - auto-select
          this.einheitId = result.einheitId;
          this.showMessage(
            this.translationService.translate('EINHEIT_ERKANNT') + ': ' + result.einheitName,
            'success'
          );
        } else if (result.matched && result.confidence <= this.CONFIDENCE_THRESHOLD) {
          // Low confidence match - suggest but don't auto-select
          if (result.einheitId) {
            this.einheitId = result.einheitId;
          }
          this.showMessage(
            this.translationService.translate('EINHEIT_BITTE_PRUEFEN'),
            'warning'
          );
        } else if (!result.matched && result.message) {
          // Error from backend (e.g. invalid API key, service unavailable)
          this.showMessage(result.message, 'error');
        }
      },
      error: (error) => {
        this.isMatching = false;
        this.matchResult = null;
        // Show error message from HTTP error
        const message = error.error?.message || error.message ||
          this.translationService.translate('KI_NICHT_VERFUEGBAR');
        this.showMessage(message, 'error');
      }
    });
  }

  removeFile(event: Event): void {
    event.stopPropagation();
    this.file = null;
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    if (fileInput) {
      fileInput.value = '';
    }
  }

  formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  }

  private extractDateFromFile(file: File): void {
    const reader = new FileReader();
    reader.onload = (e) => {
      const content = e.target?.result as string;
      if (content) {
        const lines = content.split('\n');
        if (lines.length >= 2) {
          const secondLine = lines[1];
          const firstColumn = secondLine.split(',')[0];
          const parsedDate = this.parseEnglishDate(firstColumn);
          if (parsedDate) {
            this.date = parsedDate;
          }
        }
      }
    };
    reader.readAsText(file);
  }

  private parseEnglishDate(dateStr: string): string | null {
    // Parse format: "Tue Jul 01 2025" (EEE MMM dd yyyy)
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

  onSubmit(): void {
    if (!this.date || !this.einheitId || !this.file) {
      this.showMessage(this.translationService.translate('BITTE_ALLE_FELDER_AUSFUELLEN'), 'error');
      return;
    }

    this.uploading = true;
    const formData = new FormData();
    formData.append('date', this.date);
    formData.append('einheitId', this.einheitId.toString());
    formData.append('file', this.file);

    this.http.post<any>('http://localhost:8090/api/messwerte/upload', formData).subscribe({
      next: (response) => {
        if (response.status === 'success') {
          this.showMessage(`Erfolgreich! ${response.count} Messwerte für ${response.einheitName} hochgeladen.`, 'success');
          this.resetForm();
        } else {
          this.showMessage(`Fehler: ${response.message}`, 'error');
        }
        this.uploading = false;
      },
      error: (error) => {
        this.showMessage(`Fehler: ${error.message}`, 'error');
        this.uploading = false;
      }
    });
  }

  private showMessage(message: string, type: 'success' | 'error' | 'warning'): void {
    this.message = message;
    this.messageType = type;
    setTimeout(() => {
      this.message = '';
      this.messageType = '';
    }, 5000);
  }

  private resetForm(): void {
    // Keep the date and einheit for the next upload
    this.file = null;
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    if (fileInput) {
      fileInput.value = '';
    }
  }
}
