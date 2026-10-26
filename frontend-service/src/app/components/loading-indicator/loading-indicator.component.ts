import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { TranslatePipe } from '../../pipes/translate.pipe';

/**
 * Ladeanzeige für länger dauernde Abfragen: Spinner mit Text (Specs/Statistik.md, FR „Ladeanzeige").
 *
 * Das Aussehen kommt aus dem Design System (`.zev-loading`, `spinner.css`), auch die
 * Einblendverzögerung: Antwortet der Server schneller als 300 ms, wird nichts sichtbar - die
 * Anzeige blitzt nicht auf.
 *
 * Verwendung: `@if (loading) { <app-loading-indicator text="STATISTIK_WIRD_BERECHNET" /> }`
 */
@Component({
  selector: 'app-loading-indicator',
  standalone: true,
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <div class="zev-loading" [class.zev-loading--inline]="inline" role="status" aria-live="polite">
      <span class="zev-spinner" aria-hidden="true"></span>
      <span class="zev-loading__text">{{ text | translate }}</span>
    </div>
  `
})
export class LoadingIndicatorComponent {
  /** Übersetzungs-Key des Texts neben dem Spinner. */
  @Input() text = 'LADE_DATEN';

  /** Kompakt in einer Zeile statt zentriert mit grossem Spinner. */
  @Input() inline = false;
}
