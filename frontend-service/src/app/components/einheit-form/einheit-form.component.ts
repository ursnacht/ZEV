import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Einheit, EinheitTyp, MAX_MESSPUNKT_LAENGE } from '../../models/einheit.model';

import { TranslatePipe } from '../../pipes/translate.pipe';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-einheit-form',
  standalone: true,
  imports: [FormsModule, TranslatePipe, IconComponent],
  templateUrl: './einheit-form.component.html'
})
export class EinheitFormComponent implements OnInit {
  @Input() einheit: Einheit | null = null;
  /**
   * Vorbelegter Messpunkt einer **neuen** Einheit (Specs/Einheitenverwaltung.md, FR-8); die
   * übrigen Vorgaben bleiben die des Formulars. Wird ignoriert, wenn `einheit` gesetzt ist.
   */
  @Input() messpunktVorgabe: string | null = null;
  @Output() save = new EventEmitter<Einheit>();
  @Output() cancel = new EventEmitter<void>();

  formData: Einheit = {
    name: '',
    typ: EinheitTyp.CONSUMER,
    // Eine neue Wohnung nimmt an der Nebenkostenabrechnung teil; die Ausnahme wird abgewaehlt.
    nebenkostenRelevant: true
  };

  einheitTypOptions = [
    { value: EinheitTyp.PRODUCER, label: 'PRODUZENT' },
    { value: EinheitTyp.CONSUMER, label: 'KONSUMENT' },
    { value: EinheitTyp.BEZUG, label: 'TYP_BEZUG' },
    { value: EinheitTyp.RUECKLIEFERUNG, label: 'TYP_RUECKLIEFERUNG' },
    { value: EinheitTyp.LADESTATION, label: 'TYP_LADESTATION' },
    { value: EinheitTyp.SPEICHER, label: 'TYP_SPEICHER' }
  ];

  readonly EinheitTyp = EinheitTyp;
  readonly MAX_MESSPUNKT_LAENGE = MAX_MESSPUNKT_LAENGE;

  ngOnInit(): void {
    if (!this.einheit && this.messpunktVorgabe) {
      this.formData.messpunkt = this.messpunktVorgabe;
    }
    if (this.einheit) {
      this.formData = { ...this.einheit };
      // Bestandsdaten vor V123 tragen das Feld nicht - dann gilt der Standard "ist eine Wohnung".
      this.formData.nebenkostenRelevant = this.einheit.nebenkostenRelevant ?? true;
    }
  }

  /** 2 bis 30 Zeichen, wie `@Size` an der Entity - sonst weist erst das Backend ab. */
  isNameGueltig(): boolean {
    return this.formData.name.trim().length >= 2;
  }

  onSubmit(): void {
    if (this.isNameGueltig()) {
      this.save.emit(this.formData);
    }
  }

  onCancel(): void {
    this.cancel.emit();
  }
}
