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

  /** Klappt nach oben auf, wenn bis zum Fensterrand unter dem Knopf zu wenig Platz ist. */
  nachOben = false;

  /**
   * Lage des Menues im **Fenster** (`position: fixed`, `.zev-kebab-menu--fixed`).
   *
   * Fest statt absolut: Ein absolut positioniertes Menue schnitt jeder Vorfahre mit `overflow`
   * ab - ein Panel, eine scrollende Tabelle (`.zev-table-scroll`, Spaltenbreiten-Direktive). In
   * einer kurzen Tabelle passte es dort weder nach unten noch nach oben, und der oberste Eintrag
   * war nicht anklickbar (Specs/Messpunkt-Empfang.md, Anhang A.2). Fest positioniert liegt es
   * ueber der Seite; beim Scrollen und bei Groessenaenderung wird die Lage neu berechnet, damit es
   * am Knopf bleibt.
   *
   * <p><b>Nicht schliessen beim Scrollen:</b> Das war der erste Ansatz. Ein Scroll-Ereignis kommt
   * aber oft erst nach dem Klick an - wenn der Browser den Knopf eben ins Bild geholt hat
   * (Playwright tut das vor jedem Klick, ein Touchpad mit Traegheit ebenso). Das Menue schloss sich
   * dann gleich nach dem Oeffnen, und die E2E-Suiten konnten ihre Testdaten nicht mehr loeschen.
   */
  position = { top: 0, left: 0 };

  private documentClickListener: ((e: MouseEvent) => void) | null = null;
  private escapeKeyListener: ((e: KeyboardEvent) => void) | null = null;
  private scrollListener: (() => void) | null = null;

  constructor(
    private elementRef: ElementRef,
    private cdr: ChangeDetectorRef,
    private ngZone: NgZone
  ) {}

  toggle(): void {
    this.isOpen = !this.isOpen;
    if (this.isOpen) {
      this.lege();
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
   * Setzt Richtung und Lage im Fenster: rechtsbuendig unter dem Knopf, oder darueber, wenn bis
   * zum Fensterrand unten zu wenig Platz ist und oben mehr. Abschneidende Vorfahren zaehlen nicht
   * mehr - das feste Menue liegt ueber ihnen.
   */
  private lege(): void {
    const host: HTMLElement = this.elementRef.nativeElement;
    const knopf = host.querySelector('.zev-kebab-button') as HTMLElement | null;
    const menue = host.querySelector('.zev-kebab-menu') as HTMLElement | null;
    if (!knopf || !menue) {
      return;
    }
    const rechteck = knopf.getBoundingClientRect();
    const hoehe = menue.offsetHeight;
    const platzUnten = window.innerHeight - rechteck.bottom;
    const platzOben = rechteck.top;
    this.nachOben = platzUnten < hoehe && platzOben > platzUnten;
    this.position = {
      top: this.nachOben ? rechteck.top - hoehe : rechteck.bottom,
      // Rechtsbuendig zum Knopf, aber nie links aus dem Fenster
      left: Math.max(0, (rechteck.right ?? 0) - menue.offsetWidth)
    };
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
      // Fest positioniert wandert das Menue nicht von selbst mit - also neu legen. Capture, damit
      // auch das Scrollen einer Tabelle oder eines Panels zaehlt.
      this.scrollListener = () => this.ngZone.run(() => {
        this.lege();
        this.cdr.markForCheck();
      });
      document.addEventListener('click', this.documentClickListener, true);
      document.addEventListener('keydown', this.escapeKeyListener);
      window.addEventListener('scroll', this.scrollListener, true);
      window.addEventListener('resize', this.scrollListener);
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
    if (this.scrollListener) {
      window.removeEventListener('scroll', this.scrollListener, true);
      window.removeEventListener('resize', this.scrollListener);
      this.scrollListener = null;
    }
  }

  ngOnDestroy(): void {
    this.removeListeners();
  }
}
