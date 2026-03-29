import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LizenzenService } from '../../services/lizenzen.service';
import { Lizenz, LizenzHash } from '../../models/lizenzen.model';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-lizenzen',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, IconComponent],
  templateUrl: './lizenzen.component.html',
  styleUrls: ['./lizenzen.component.css']
})
export class LizenzenComponent implements OnInit {
  backendLizenzen: Lizenz[] = [];
  frontendLizenzen: Lizenz[] = [];
  filteredBackend: Lizenz[] = [];
  filteredFrontend: Lizenz[] = [];

  backendFilter = '';
  frontendFilter = '';

  backendLoading = true;
  frontendLoading = true;
  backendError = false;
  frontendError = false;

  private readonly HASH_PRIORITY = ['SHA-512', 'SHA-256', 'SHA-384', 'SHA-1', 'MD5'];

  constructor(private lizenzenService: LizenzenService) {}

  ngOnInit(): void {
    this.loadBackendLizenzen();
    this.loadFrontendLizenzen();
  }

  loadBackendLizenzen(): void {
    this.backendLoading = true;
    this.backendError = false;
    this.lizenzenService.getBackendLizenzen().subscribe({
      next: (data) => {
        this.backendLizenzen = data;
        this.filteredBackend = data;
        this.backendLoading = false;
      },
      error: () => {
        this.backendError = true;
        this.backendLoading = false;
      }
    });
  }

  loadFrontendLizenzen(): void {
    this.frontendLoading = true;
    this.frontendError = false;
    this.lizenzenService.getFrontendLizenzen().subscribe({
      next: (data) => {
        this.frontendLizenzen = data;
        this.filteredFrontend = data;
        this.frontendLoading = false;
      },
      error: () => {
        this.frontendError = true;
        this.frontendLoading = false;
      }
    });
  }

  onBackendFilterChange(): void {
    const term = this.backendFilter.toLowerCase().trim();
    if (!term) {
      this.filteredBackend = this.backendLizenzen;
    } else {
      this.filteredBackend = this.backendLizenzen.filter(l =>
        l.name.toLowerCase().includes(term) ||
        l.license.toLowerCase().includes(term)
      );
    }
  }

  onFrontendFilterChange(): void {
    const term = this.frontendFilter.toLowerCase().trim();
    if (!term) {
      this.filteredFrontend = this.frontendLizenzen;
    } else {
      this.filteredFrontend = this.frontendLizenzen.filter(l =>
        l.name.toLowerCase().includes(term) ||
        l.license.toLowerCase().includes(term)
      );
    }
  }

  getBestHash(lizenz: Lizenz): LizenzHash | null {
    for (const alg of this.HASH_PRIORITY) {
      const h = lizenz.hashes.find(h => h.algorithm === alg);
      if (h) return h;
    }
    return lizenz.hashes.length > 0 ? lizenz.hashes[0] : null;
  }

  truncateHash(value: string): string {
    return value.substring(0, 12);
  }
}
