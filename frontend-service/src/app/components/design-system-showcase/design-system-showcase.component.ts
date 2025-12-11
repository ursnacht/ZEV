import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe } from '../../pipes/translate.pipe';

@Component({
  selector: 'app-design-system-showcase',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  templateUrl: './design-system-showcase.component.html',
  styleUrl: './design-system-showcase.component.css'
})
export class DesignSystemShowcaseComponent {
  collapsibleOpen = false;

  toggleCollapsible(): void {
    this.collapsibleOpen = !this.collapsibleOpen;
  }
}
