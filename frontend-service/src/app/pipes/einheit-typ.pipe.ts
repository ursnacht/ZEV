import { Pipe, PipeTransform } from '@angular/core';
import { EinheitTyp } from '../models/einheit.model';
import { TranslationService } from '../services/translation.service';

@Pipe({
  name: 'einheitTyp',
  standalone: true,
  pure: false
})
export class EinheitTypPipe implements PipeTransform {
  constructor(private translationService: TranslationService) {}

  transform(typ: string): string {
    switch (typ) {
      case EinheitTyp.CONSUMER:
        return this.translationService.translate('KONSUMENT');
      case EinheitTyp.BEZUG:
        return this.translationService.translate('TYP_BEZUG');
      case EinheitTyp.RUECKLIEFERUNG:
        return this.translationService.translate('TYP_RUECKLIEFERUNG');
      default:
        return this.translationService.translate('PRODUZENT');
    }
  }
}
