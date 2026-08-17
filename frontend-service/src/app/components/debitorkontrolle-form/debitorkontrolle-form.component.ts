import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Debitor } from '../../models/debitor.model';
import { Einheit } from '../../models/einheit.model';
import { Mieter } from '../../models/mieter.model';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-debitorkontrolle-form',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, IconComponent],
  templateUrl: './debitorkontrolle-form.component.html',
  styleUrls: ['./debitorkontrolle-form.component.css']
})
export class DebitorkontrolleFormComponent implements OnInit {
  @Input() debitor: Debitor | null = null;
  @Input() mieter: Mieter[] = [];
  @Input() einheiten: Einheit[] = [];
  @Output() save = new EventEmitter<Debitor>();
  @Output() cancel = new EventEmitter<void>();

  formData: Debitor = {
    mieterId: 0,
    betrag: 0,
    datumVon: '',
    datumBis: '',
    zahldatum: undefined
  };

  selectedEinheitName: string = '';
  readonly Number = Number;

  ngOnInit(): void {
    if (this.debitor) {
      this.formData = { ...this.debitor };
      this.updateEinheitName();
    } else {
      const today = new Date().toISOString().split('T')[0];
      this.formData.datumVon = today;
      this.formData.datumBis = today;
    }
  }

  onMieterChange(): void {
    this.updateEinheitName();
  }

  private updateEinheitName(): void {
    const m = this.mieter.find(x => x.id === Number(this.formData.mieterId));
    if (m) {
      this.selectedEinheitName = this.einheitNamen(m);
    } else {
      this.selectedEinheitName = '';
    }
  }

  getMieterDisplayName(m: Mieter): string {
    const namen = this.einheitNamen(m);
    return namen ? `${m.name} (${namen})` : m.name;
  }

  /** Namen aller Einheiten eines Mieters - er kann Wohnung und Ladestation(en) haben. */
  private einheitNamen(m: Mieter): string {
    return (m.einheitIds ?? [])
      .map(id => this.einheiten.find(x => x.id === id)?.name)
      .filter((name): name is string => !!name)
      .join(', ');
  }


  onSubmit(): void {
    if (this.isFormValid()) {
      const debitorToSave: Debitor = {
        ...this.formData,
        mieterId: Number(this.formData.mieterId),
        betrag: Number(this.formData.betrag),
        zahldatum: this.formData.zahldatum || undefined
      };
      this.save.emit(debitorToSave);
    }
  }

  onCancel(): void {
    this.cancel.emit();
  }

  isFormValid(): boolean {
    return !!(
      Number(this.formData.mieterId) > 0 &&
      Number(this.formData.betrag) > 0 &&
      this.formData.datumVon &&
      this.formData.datumBis &&
      this.formData.datumVon <= this.formData.datumBis &&
      this.isZahldatumValid()
    );
  }

  isZahldatumValid(): boolean {
    if (!this.formData.zahldatum) return true;
    return this.formData.zahldatum >= this.formData.datumBis;
  }
}
