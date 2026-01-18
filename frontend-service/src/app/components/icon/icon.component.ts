import { Component, Input } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { ICONS } from './icons';

@Component({
  selector: 'app-icon',
  standalone: true,
  templateUrl: './icon.component.html'
})
export class IconComponent {
  @Input() name!: string;
  @Input() size: 'sm' | 'md' | 'lg' = 'md';

  constructor(private sanitizer: DomSanitizer) {}

  get iconClass(): string {
    return `zev-icon zev-icon--${this.size}`;
  }

  get svgContent(): SafeHtml {
    const iconPath = ICONS[this.name];
    if (!iconPath) {
      console.warn(`Icon "${this.name}" not found in icon registry`);
      return '';
    }
    return this.sanitizer.bypassSecurityTrustHtml(iconPath);
  }
}
