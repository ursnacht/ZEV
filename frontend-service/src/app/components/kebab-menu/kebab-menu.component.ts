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

  /**
   * Klappt nach oben auf, wenn unter dem Knopf zu wenig Platz ist - sonst schnitte ein Panel mit
   * `overflow: hidden` oder eine scrollende Tabelle (Spaltenbreiten-Direktive) das Menue der unteren
   * Zeilen ab, oder es ragte unter den Fensterrand.
   */
  nachOben = false;

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
      this.nachOben = this.mussNachObenAufklappen();
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

  /**
   * Vergleicht den Platz unter und ueber dem Knopf - jeweils bis zum naechsten Vorfahren, der
   * abschneidet (`overflow` nicht `visible`), hoechstens bis zum Fensterrand. Nach oben nur, wenn
   * das Menue unten nicht passt und oben mehr Platz ist.
   */
  private mussNachObenAufklappen(): boolean {
    const host: HTMLElement = this.elementRef.nativeElement;
    const knopf = host.querySelector('.zev-kebab-button') as HTMLElement | null;
    const menue = host.querySelector('.zev-kebab-menu') as HTMLElement | null;
    if (!knopf || !menue) {
      return false;
    }
    const rechteck = knopf.getBoundingClientRect();
    const hoehe = menue.offsetHeight;
    let unten = window.innerHeight;
    let oben = 0;
    for (let el = host.parentElement; el; el = el.parentElement) {
      const stil = getComputedStyle(el);
      if ([stil.overflow, stil.overflowX, stil.overflowY].some(w => !!w && w !== 'visible')) {
        const rand = el.getBoundingClientRect();
        unten = Math.min(unten, rand.bottom);
        oben = Math.max(oben, rand.top);
      }
    }
    const platzUnten = unten - rechteck.bottom;
    const platzOben = rechteck.top - oben;
    return platzUnten < hoehe && platzOben > platzUnten;
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
