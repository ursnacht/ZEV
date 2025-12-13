import { Component, OnInit, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe } from '../../pipes/translate.pipe';

interface Quarter {
  label: string;      // z.B. "Q3/2024"
  von: string;        // z.B. "2024-07-01"
  bis: string;        // z.B. "2024-09-30"
}

@Component({
  selector: 'app-quarter-selector',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  template: `
    <div class="zev-quarter-selector">
      <span class="zev-quarter-selector__label">{{ 'QUARTAL_WAEHLEN' | translate }}:</span>
      <button *ngFor="let q of quarters"
              type="button"
              class="zev-quarter-button"
              [class.zev-quarter-button--active]="isSelected(q)"
              (click)="selectQuarter(q)">
        {{ q.label }}
      </button>
    </div>
  `
})
export class QuarterSelectorComponent implements OnInit {
  @Input() selectedVon: string = '';
  @Input() selectedBis: string = '';
  @Output() quarterSelected = new EventEmitter<{von: string, bis: string}>();

  quarters: Quarter[] = [];

  ngOnInit(): void {
    this.quarters = this.calculateQuarters();
  }

  /**
   * Berechnet die letzten 5 Quartale ab dem aktuellen Datum.
   * Das aktuelle Quartal ist das letzte in der Liste.
   */
  private calculateQuarters(): Quarter[] {
    const quarters: Quarter[] = [];
    const today = new Date();

    // Aktuelles Quartal bestimmen
    let year = today.getFullYear();
    let quarter = Math.ceil((today.getMonth() + 1) / 3);

    // 5 Quartale rückwärts berechnen, dann umkehren für chronologische Reihenfolge
    for (let i = 0; i < 5; i++) {
      const q = this.createQuarter(year, quarter);
      quarters.unshift(q); // Am Anfang einfügen (ältestes zuerst)

      quarter--;
      if (quarter < 1) {
        quarter = 4;
        year--;
      }
    }

    return quarters;
  }

  /**
   * Erstellt ein Quarter-Objekt für das angegebene Jahr und Quartal.
   */
  private createQuarter(year: number, quarter: number): Quarter {
    const startMonth = (quarter - 1) * 3;
    const von = new Date(year, startMonth, 1);
    const bis = new Date(year, startMonth + 3, 0); // Letzter Tag des Quartals

    return {
      label: `Q${quarter}/${year}`,
      von: this.formatDate(von),
      bis: this.formatDate(bis)
    };
  }

  /**
   * Formatiert ein Datum als YYYY-MM-DD String.
   */
  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  /**
   * Wird aufgerufen, wenn ein Quartal ausgewählt wird.
   */
  selectQuarter(quarter: Quarter): void {
    this.quarterSelected.emit({ von: quarter.von, bis: quarter.bis });
  }

  /**
   * Prüft, ob das angegebene Quartal aktuell ausgewählt ist.
   */
  isSelected(quarter: Quarter): boolean {
    return this.selectedVon === quarter.von && this.selectedBis === quarter.bis;
  }
}
