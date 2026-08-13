import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import Keycloak from 'keycloak-js';
import { WithMessage } from '../../utils/with-message';
import { EinstellungenService } from '../../services/einstellungen.service';
import { Einstellungen, RechnungKonfiguration, Steller } from '../../models/einstellungen.model';
import { FeatureFlagService } from '../../services/feature-flag.service';
import { FeatureFlagAdmin, FeatureFlagQuelle } from '../../models/feature-flag.model';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { TranslationService } from '../../services/translation.service';
import { IconComponent } from '../icon/icon.component';
import { DatenbankAnsichtComponent } from '../datenbank-ansicht/datenbank-ansicht.component';

@Component({
  selector: 'app-einstellungen',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, IconComponent, DatenbankAnsichtComponent],
  templateUrl: './einstellungen.component.html',
  styleUrls: ['./einstellungen.component.css']
})
export class EinstellungenComponent extends WithMessage implements OnInit {
  formData: RechnungKonfiguration = {
    zahlungsfrist: '',
    iban: '',
    steller: {
      name: '',
      strasse: '',
      plz: '',
      ort: ''
    },
    verteilmodus: 'PRODUCER_MESSUNG'
  };

  einstellungenId: number | undefined;

  loading = true;

  featureFlags: FeatureFlagAdmin[] = [];
  readonly Quelle = FeatureFlagQuelle;

  /**
   * Nur mit der Permission {@code featureflags:manage} dürfen Feature-Flags verwaltet werden
   * (Fachrolle {@code zev_admin}). {@code org_admin} darf die Einstellungen bearbeiten
   * ({@code einstellungen:write}), sieht die Feature-Flag-Sektion aber nicht.
   */
  readonly canManageFeatureFlags = inject(Keycloak).hasRealmRole('featureflags:manage');

  /**
   * Die Datenbank-Ansicht ist ausschliesslich für {@code zev_admin} bestimmt
   * (Permission {@code datenbank:read}). {@code org_admin} sieht sie nicht.
   */
  readonly canViewDatenbank = inject(Keycloak).hasRealmRole('datenbank:read');

  constructor(
    private einstellungenService: EinstellungenService,
    private featureFlagService: FeatureFlagService,
    private translationService: TranslationService
  ) { super(); }

  ngOnInit(): void {
    this.loadEinstellungen();
    if (this.canManageFeatureFlags) {
      this.loadFeatureFlags();
    }
  }

  loadFeatureFlags(): void {
    this.featureFlagService.getAdminFlags().subscribe({
      next: (flags) => this.featureFlags = flags,
      error: () => this.showMessage('FEATURE_FLAG_FEHLER', 'error')
    });
  }

  onToggleFlag(flag: FeatureFlagAdmin, event: Event): void {
    const enabled = (event.target as HTMLInputElement).checked;
    this.featureFlagService.setFlag(flag.key, enabled).subscribe({
      next: () => {
        this.showMessage('FEATURE_FLAG_GESPEICHERT', 'success');
        this.loadFeatureFlags();
      },
      error: (error) => {
        this.showMessage(error.error || 'FEATURE_FLAG_FEHLER', 'error');
        this.loadFeatureFlags();
      }
    });
  }

  onResetFlag(flag: FeatureFlagAdmin): void {
    this.featureFlagService.resetFlag(flag.key).subscribe({
      next: () => {
        this.showMessage('FEATURE_FLAG_ZURUECKGESETZT', 'success');
        this.loadFeatureFlags();
      },
      error: (error) => {
        this.showMessage(error.error || 'FEATURE_FLAG_FEHLER', 'error');
        this.loadFeatureFlags();
      }
    });
  }

  loadEinstellungen(): void {
    this.loading = true;
    this.einstellungenService.getEinstellungen().subscribe({
      next: (data) => {
        if (data && data.rechnung) {
          this.einstellungenId = data.id;
          this.formData = { ...data.rechnung };
          // Ensure steller object exists
          if (!this.formData.steller) {
            this.formData.steller = { name: '', strasse: '', plz: '', ort: '' };
          }
          // Bestandsmandanten ohne Feld → Default PRODUCER_MESSUNG
          if (!this.formData.verteilmodus) {
            this.formData.verteilmodus = 'PRODUCER_MESSUNG';
          }
        }
        this.loading = false;
      },
      error: (error) => {
        // 204 No Content is not an error - it means no settings exist yet
        if (error.status !== 204) {
          this.showMessage('EINSTELLUNGEN_FEHLER', 'error');
        }
        this.loading = false;
      }
    });
  }

  onSubmit(): void {
    if (!this.isFormValid()) {
      return;
    }

    const einstellungen: Einstellungen = {
      id: this.einstellungenId,
      rechnung: this.formData
    };

    this.einstellungenService.saveEinstellungen(einstellungen).subscribe({
      next: (saved) => {
        this.einstellungenId = saved.id;
        this.showMessage('EINSTELLUNGEN_GESPEICHERT', 'success');
      },
      error: (error) => {
        const errorMsg = error.error || 'EINSTELLUNGEN_FEHLER';
        this.showMessage(errorMsg, 'error');
      }
    });
  }

  isFormValid(): boolean {
    return !!(
      this.formData.zahlungsfrist?.trim() &&
      this.formData.iban?.trim() &&
      this.isIbanValid() &&
      this.formData.steller?.name?.trim() &&
      this.formData.steller?.strasse?.trim() &&
      this.formData.steller?.plz?.trim() &&
      this.formData.steller?.ort?.trim()
    );
  }

  isIbanValid(): boolean {
    if (!this.formData.iban) {
      return true; // Don't show error if not entered yet
    }
    // Swiss IBAN: CH + 2 check digits + 17 alphanumeric chars (with optional spaces)
    const cleanIban = this.formData.iban.replace(/\s/g, '');
    const ibanRegex = /^CH[0-9]{2}[0-9]{17}$/;
    return ibanRegex.test(cleanIban);
  }

}
