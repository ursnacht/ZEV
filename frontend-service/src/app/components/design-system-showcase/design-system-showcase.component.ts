import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { QuarterSelectorComponent } from '../quarter-selector/quarter-selector.component';

@Component({
  selector: 'app-design-system-showcase',
  standalone: true,
  imports: [CommonModule, TranslatePipe, QuarterSelectorComponent],
  templateUrl: './design-system-showcase.component.html',
  styleUrl: './design-system-showcase.component.css'
})
export class DesignSystemShowcaseComponent {
  collapsibleOpen = false;
  quarterDateFrom = '';
  quarterDateTo = '';

  toggleCollapsible(): void {
    this.collapsibleOpen = !this.collapsibleOpen;
  }

  onQuarterSelected(event: {von: string, bis: string}): void {
    this.quarterDateFrom = event.von;
    this.quarterDateTo = event.bis;
  }
}
