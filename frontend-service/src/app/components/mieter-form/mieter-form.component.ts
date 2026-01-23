import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { Mieter } from '../../models/mieter.model';
import { Einheit } from '../../models/einheit.model';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-mieter-form',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, IconComponent],
  templateUrl: './mieter-form.component.html',
  styleUrls: ['./mieter-form.component.css']
})
export class MieterFormComponent implements OnInit {
  @Input() mieter: Mieter | null = null;
  @Input() einheiten: Einheit[] = [];
  @Output() save = new EventEmitter<Mieter>();
  @Output() cancel = new EventEmitter<void>();

  formData: Mieter = {
    name: '',
    strasse: '',
    plz: '',
    ort: '',
    mietbeginn: '',
    mietende: '',
    einheitId: 0
  };

  ngOnInit(): void {
    if (this.mieter) {
      this.formData = { ...this.mieter };
    } else {
      // Set default mietbeginn to today
      const today = new Date().toISOString().split('T')[0];
      this.formData.mietbeginn = today;
    }
  }

  onSubmit(): void {
    if (this.isFormValid()) {
      // Convert empty mietende to undefined
      const mieterToSave = { ...this.formData };
      if (!mieterToSave.mietende) {
        mieterToSave.mietende = undefined;
      }
      this.save.emit(mieterToSave);
    }
  }

  onCancel(): void {
    this.cancel.emit();
  }

  isFormValid(): boolean {
    return !!(
      this.formData.name.trim() &&
      this.formData.mietbeginn &&
      this.formData.einheitId > 0 &&
      this.isDateRangeValid()
    );
  }

  isDateRangeValid(): boolean {
    if (!this.formData.mietbeginn || !this.formData.mietende) {
      return true; // mietende is optional
    }
    return this.formData.mietende > this.formData.mietbeginn;
  }

  getEinheitDisplayName(einheit: Einheit): string {
    return `${einheit.name} (ID: ${einheit.id})`;
  }
}
