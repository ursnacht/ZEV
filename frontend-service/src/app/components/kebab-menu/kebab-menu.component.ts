import { Component, Input, Output, EventEmitter, ElementRef, ChangeDetectionStrategy, ChangeDetectorRef, NgZone, OnDestroy } from '@angular/core';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { IconComponent } from '../icon/icon.component';

export interface KebabMenuItem {
  label: string;
  action: string;
  danger?: boolean;
  icon?: string;
}

@Component({
  selector: 'app-kebab-menu',
  standalone: true,
  imports: [TranslatePipe, IconComponent],
  templateUrl: './kebab-menu.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class KebabMenuComponent implements OnDestroy {
  @Input() items: KebabMenuItem[] = [];
  @Output() itemClick = new EventEmitter<string>();

  isOpen = false;

  private documentClickListener: ((e: MouseEvent) => void) | null = null;
  private escapeKeyListener: ((e: KeyboardEvent) => void) | null = null;

  constructor(
    private elementRef: ElementRef,
    private cdr: ChangeDetectorRef,
    private ngZone: NgZone
  ) {}

  toggle(): void {
    this.isOpen = !this.isOpen;
    if (this.isOpen) {
      this.addListeners();
    } else {
      this.removeListeners();
    }
  }

  close(): void {
    if (!this.isOpen) return;
    this.isOpen = false;
    this.removeListeners();
    this.cdr.markForCheck();
  }

  onItemClick(action: string): void {
    this.itemClick.emit(action);
    this.close();
  }

  private addListeners(): void {
    this.ngZone.runOutsideAngular(() => {
      this.documentClickListener = (event: MouseEvent) => {
        if (!this.elementRef.nativeElement.contains(event.target)) {
          this.ngZone.run(() => this.close());
        }
      };
      this.escapeKeyListener = (event: KeyboardEvent) => {
        if (event.key === 'Escape') {
          this.ngZone.run(() => this.close());
        }
      };
      document.addEventListener('click', this.documentClickListener, true);
      document.addEventListener('keydown', this.escapeKeyListener);
    });
  }

  private removeListeners(): void {
    if (this.documentClickListener) {
      document.removeEventListener('click', this.documentClickListener, true);
      this.documentClickListener = null;
    }
    if (this.escapeKeyListener) {
      document.removeEventListener('keydown', this.escapeKeyListener);
      this.escapeKeyListener = null;
    }
  }

  ngOnDestroy(): void {
    this.removeListeners();
  }
}
