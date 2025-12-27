import { Component, Input, Output, EventEmitter, ElementRef, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe } from '../../pipes/translate.pipe';

export interface KebabMenuItem {
  label: string;
  action: string;
  danger?: boolean;
}

@Component({
  selector: 'app-kebab-menu',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  templateUrl: './kebab-menu.component.html'
})
export class KebabMenuComponent {
  @Input() items: KebabMenuItem[] = [];
  @Output() itemClick = new EventEmitter<string>();

  isOpen = false;

  constructor(private elementRef: ElementRef) {}

  toggle(): void {
    this.isOpen = !this.isOpen;
  }

  close(): void {
    this.isOpen = false;
  }

  onItemClick(action: string): void {
    this.itemClick.emit(action);
    this.close();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.close();
    }
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    this.close();
  }
}
