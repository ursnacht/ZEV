import { Directive, TemplateRef, ViewContainerRef, effect, inject, input } from '@angular/core';
import { FeatureFlagService } from '../services/feature-flag.service';

/**
 * Struktur-Direktive: rendert den Inhalt nur, wenn das angegebene Feature-Flag aktiv ist.
 * Reagiert reaktiv auf das Laden/Ändern der Flags (Signal im FeatureFlagService).
 *
 * Verwendung: `<li *appFeature="'MESSWERTE_UPLOAD'">...</li>`
 */
@Directive({
  selector: '[appFeature]',
  standalone: true
})
export class FeatureFlagDirective {
  /** Technischer Flag-Key. */
  readonly appFeature = input.required<string>();

  private readonly templateRef = inject(TemplateRef<unknown>);
  private readonly viewContainer = inject(ViewContainerRef);
  private readonly featureFlagService = inject(FeatureFlagService);

  private hasView = false;

  constructor() {
    effect(() => {
      const enabled = this.featureFlagService.isEnabled(this.appFeature());
      this.updateView(enabled);
    });
  }

  private updateView(enabled: boolean): void {
    if (enabled && !this.hasView) {
      this.viewContainer.createEmbeddedView(this.templateRef);
      this.hasView = true;
    } else if (!enabled && this.hasView) {
      this.viewContainer.clear();
      this.hasView = false;
    }
  }
}
