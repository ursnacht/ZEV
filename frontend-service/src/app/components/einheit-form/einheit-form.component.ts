import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Einheit, EinheitTyp } from '../../models/einheit.model';

import { TranslatePipe } from '../../pipes/translate.pipe';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-einheit-form',
  standalone: true,
  imports: [FormsModule, TranslatePipe, IconComponent],
  templateUrl: './einheit-form.component.html',
  styleUrls: ['./einheit-form.component.css']
})
export class EinheitFormComponent implements OnInit {
  @Input() einheit: Einheit | null = null;
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
    { value: EinheitTyp.LADESTATION, label: 'TYP_LADESTATION' }
  ];

  readonly EinheitTyp = EinheitTyp;

  ngOnInit(): void {
    if (this.einheit) {
      this.formData = { ...this.einheit };
      // Bestandsdaten vor V123 tragen das Feld nicht - dann gilt der Standard "ist eine Wohnung".
      this.formData.nebenkostenRelevant = this.einheit.nebenkostenRelevant ?? true;
    }
  }

  onSubmit(): void {
    if (this.formData.name.trim()) {
      this.save.emit(this.formData);
    }
  }

  onCancel(): void {
    this.cancel.emit();
  }
}
