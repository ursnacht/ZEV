import { Component } from '@angular/core';
import { TranslatePipe } from '../../pipes/translate.pipe';

@Component({
  selector: 'app-design-system-showcase',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './design-system-showcase.component.html',
  styleUrl: './design-system-showcase.component.css'
})
export class DesignSystemShowcaseComponent {

}
