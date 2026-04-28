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
    return typ === EinheitTyp.CONSUMER
      ? this.translationService.translate('KONSUMENT')
      : this.translationService.translate('PRODUZENT');
  }
}
