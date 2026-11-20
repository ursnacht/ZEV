import { Pipe, PipeTransform } from '@angular/core';
import { Einheit } from '../models/einheit.model';
import { TranslationService } from '../services/translation.service';
import { EinheitTypPipe } from './einheit-typ.pipe';

/**
 * Angular pipe für die Bezeichnung einer oder mehrerer Einheiten samt gemeinsamem Messpunkt
 * (Specs/EChart.md, FR-8).
 *
 * Der Typ wird über die {@link EinheitTypPipe} übersetzt — die Zuordnung Typ → Text steht damit
 * weiterhin an einer Stelle. Nicht `pure`, damit ein Sprachwechsel die Bezeichnung erneuert.
 *
 * @example
 * {{ [bezug, ruecklieferung] | messpunktGruppe:'Bilanz' }}
 * // 'Bezug von BKW [Bezug] / Rücklieferung an BKW [Rücklieferung] / [Bilanz]'
 * {{ [wohnung] | messpunktGruppe }}
 * // 'Wohnung A [Konsument]'
 */
@Pipe({
  name: 'messpunktGruppe',
  standalone: true,
  pure: false
})
export class MesspunktGruppePipe implements PipeTransform {
  private readonly einheitTyp: EinheitTypPipe;

  constructor(translationService: TranslationService) {
    this.einheitTyp = new EinheitTypPipe(translationService);
  }

  /**
   * @param einheiten - die Einheiten in der Reihenfolge der Anzeige
   * @param messpunkt - gemeinsamer Messpunkt; fehlt er, entfällt der letzte Teil
   * @returns `Name [Typ] / Name [Typ] / [Messpunkt]`
   */
  transform(einheiten: Einheit[], messpunkt?: string): string {
    const teile = einheiten.map(e => `${e.name} [${this.einheitTyp.transform(e.typ)}]`);
    if (messpunkt) {
      teile.push(`[${messpunkt}]`);
    }
    return teile.join(' / ');
  }
}
