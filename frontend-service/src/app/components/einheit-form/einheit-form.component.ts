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
    typ: EinheitTyp.CONSUMER
  };

  einheitTypOptions = [
    { value: EinheitTyp.PRODUCER, label: 'Producer' },
    { value: EinheitTyp.CONSUMER, label: 'Consumer' }
  ];

  ngOnInit(): void {
    if (this.einheit) {
      this.formData = { ...this.einheit };
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
