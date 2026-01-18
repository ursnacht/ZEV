import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Tarif, TarifTyp } from '../../models/tarif.model';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-tarif-form',
  standalone: true,
  imports: [FormsModule, TranslatePipe, IconComponent],
  templateUrl: './tarif-form.component.html',
  styleUrls: ['./tarif-form.component.css']
})
export class TarifFormComponent implements OnInit {
  @Input() tarif: Tarif | null = null;
  @Output() save = new EventEmitter<Tarif>();
  @Output() cancel = new EventEmitter<void>();

  formData: Tarif = {
    bezeichnung: '',
    tariftyp: TarifTyp.ZEV,
    preis: 0,
    gueltigVon: '',
    gueltigBis: ''
  };

  tarifTypOptions = [
    { value: TarifTyp.ZEV, label: 'ZEV (Solarstrom)' },
    { value: TarifTyp.VNB, label: 'VNB (Netzstrom)' }
  ];

  ngOnInit(): void {
    if (this.tarif) {
      this.formData = { ...this.tarif };
    } else {
      // Set default dates: current year start to end
      const now = new Date();
      const year = now.getFullYear();
      this.formData.gueltigVon = `${year}-01-01`;
      this.formData.gueltigBis = `${year}-12-31`;
    }
  }

  onSubmit(): void {
    if (this.isFormValid()) {
      this.save.emit(this.formData);
    }
  }

  onCancel(): void {
    this.cancel.emit();
  }

  isFormValid(): boolean {
    return !!(
      this.formData.bezeichnung.trim() &&
      this.formData.tariftyp &&
      this.formData.preis > 0 &&
      this.formData.gueltigVon &&
      this.formData.gueltigBis &&
      this.formData.gueltigVon <= this.formData.gueltigBis
    );
  }

  isDateRangeValid(): boolean {
    if (!this.formData.gueltigVon || !this.formData.gueltigBis) {
      return true; // Don't show error if dates not entered yet
    }
    return this.formData.gueltigVon <= this.formData.gueltigBis;
  }
}
