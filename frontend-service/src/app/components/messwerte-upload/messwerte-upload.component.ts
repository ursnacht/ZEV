import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-messwerte-upload',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './messwerte-upload.component.html',
  styleUrls: ['./messwerte-upload.component.css']
})
export class MesswerteUploadComponent {
  date: string = '';
  typ: string = 'PRODUCER';
  file: File | null = null;
  uploading = false;
  message = '';
  messageType: 'success' | 'error' | '' = '';

  constructor(private http: HttpClient) {}

  onFileChange(event: any): void {
    const files = event.target.files;
    if (files.length > 0) {
      this.file = files[0];
    }
  }

  onSubmit(): void {
    if (!this.date || !this.file) {
      this.showMessage('Bitte alle Felder ausfüllen', 'error');
      return;
    }

    this.uploading = true;
    const formData = new FormData();
    formData.append('date', this.date);
    formData.append('typ', this.typ);
    formData.append('file', this.file);

    this.http.post<any>('http://localhost:8080/api/messwerte/upload', formData).subscribe({
      next: (response) => {
        if (response.status === 'success') {
          this.showMessage(`Erfolgreich! ${response.count} Messwerte hochgeladen.`, 'success');
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
    this.typ = 'PRODUCER';
    this.file = null;
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    if (fileInput) {
      fileInput.value = '';
    }
  }
}
