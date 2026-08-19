import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Tarifposition } from '../../models/tarifposition.model';
import { Tarif, TarifTyp, mengeneinheitKey } from '../../models/tarif.model';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { IconComponent } from '../icon/icon.component';

/**
 * Formular für eine Tarifposition.
 *
 * Jahr und Quartal bewusst als zwei einfache Dropdowns: Die `QuarterSelectorComponent`
 * arbeitet mit Datumsbereichen (von/bis), die Position speichert aber Jahr + Quartal.
 */
@Component({
  selector: 'app-tarifposition-form',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, IconComponent],
  templateUrl: './tarifposition-form.component.html',
  styleUrls: ['./tarifposition-form.component.css']
})
export class TarifpositionFormComponent implements OnInit {
  @Input() position: Tarifposition | null = null;
  @Input() einheitId: number | null = null;
  /** Messpunkt (RFID) der gewaehlten Einheit - belegt die Quell-Referenz vor. */
  @Input() messpunkt = '';
  @Input() tarife: Tarif[] = [];
  @Output() save = new EventEmitter<Tarifposition>();
  @Output() cancel = new EventEmitter<void>();

  formData: Tarifposition = {
    einheitId: 0,
    tarifId: 0,
    jahr: new Date().getFullYear(),
    quartal: 1,
    menge: 0
  };

  jahrOptionen: number[] = [];
  readonly quartalOptionen = [1, 2, 3, 4];

  ngOnInit(): void {
    const aktuellesJahr = new Date().getFullYear();
    // Zwei Jahre zurueck bis ein Jahr voraus - deckt Nacherfassung und Vorausplanung ab.
    this.jahrOptionen = [aktuellesJahr - 2, aktuellesJahr - 1, aktuellesJahr, aktuellesJahr + 1];

    if (this.position) {
      this.formData = { ...this.position };
      if (!this.jahrOptionen.includes(this.formData.jahr)) {
        this.jahrOptionen = [this.formData.jahr, ...this.jahrOptionen].sort((a, b) => a - b);
      }
    } else {
      this.formData = {
        einheitId: this.einheitId ?? 0,
        tarifId: this.tarife.length === 1 ? (this.tarife[0].id ?? 0) : 0,
        jahr: aktuellesJahr,
        quartal: this.aktuellesQuartal(),
        menge: 0,
        quellReferenz: this.messpunkt || undefined
      };
    }
  }

  /** Tariftyp des gewaehlten Tarifs - bestimmt Mengeneinheit und Hinweistext. */
  get gewaehlterTariftyp(): TarifTyp | undefined {
    return this.tarife.find(t => t.id === this.formData.tarifId)?.tariftyp as TarifTyp | undefined;
  }

  /** Uebersetzungs-Key der Mengeneinheit: Grundgebuehr zaehlt Monate, Ladestrom kWh. */
  get mengeneinheit(): string {
    return mengeneinheitKey(this.gewaehlterTariftyp);
  }

  /** Hinweis unter dem Mengenfeld, passend zur Mengeneinheit. */
  get mengeHinweis(): string {
    return this.gewaehlterTariftyp === TarifTyp.GRUNDGEBUEHR
      ? 'TARIFPOSITION_MENGE_HINT_MONATE'
      : 'TARIFPOSITION_MENGE_HINT';
  }

  /**
   * Schrittweite des Mengenfelds. Monate werden ganzzahlig erfasst - ein Spinner, der 0.001
   * anbietet, waere hier irrefuehrend.
   */
  get mengeSchritt(): number {
    return this.gewaehlterTariftyp === TarifTyp.GRUNDGEBUEHR ? 1 : 0.001;
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
      this.formData.einheitId &&
      this.formData.tarifId &&
      this.formData.jahr &&
      this.formData.quartal &&
      this.formData.menge !== null &&
      this.formData.menge !== undefined &&
      this.formData.menge >= 0
    );
  }

  private aktuellesQuartal(): number {
    return Math.floor(new Date().getMonth() / 3) + 1;
  }
}
