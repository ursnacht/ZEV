import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { EinheitService } from '../../services/einheit.service';
import { Einheit } from '../../models/einheit.model';

@Component({
  selector: 'app-messwerte-upload',
  standalone: true,
  imports: [CommonModule, FormsModule],
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
  messageType: 'success' | 'error' | '' = '';

  constructor(
    private http: HttpClient,
    private einheitService: EinheitService
  ) {}

  ngOnInit(): void {
    this.loadEinheiten();
  }

  loadEinheiten(): void {
    this.einheitService.getAllEinheiten().subscribe({
      next: (data) => {
        this.einheiten = data;
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
      this.file = files[0];
    }
  }

  onSubmit(): void {
    if (!this.date || !this.einheitId || !this.file) {
      this.showMessage('Bitte alle Felder ausfüllen', 'error');
      return;
    }

    this.uploading = true;
    const formData = new FormData();
    formData.append('date', this.date);
    formData.append('einheitId', this.einheitId.toString());
    formData.append('file', this.file);

    this.http.post<any>('http://localhost:8080/api/messwerte/upload', formData).subscribe({
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

  private showMessage(message: string, type: 'success' | 'error'): void {
    this.message = message;
    this.messageType = type;
    setTimeout(() => {
      this.message = '';
      this.messageType = '';
    }, 5000);
  }

  private resetForm(): void {
    this.date = '';
    this.einheitId = this.einheiten.length > 0 ? this.einheiten[0].id || null : null;
    this.file = null;
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    if (fileInput) {
      fileInput.value = '';
    }
  }
}
