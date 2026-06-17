import { TranslationService } from '../services/translation.service';
import { TarifLuecke } from '../models/tarif.model';

/**
 * Renders a tariff coverage gap as a translated string, e.g.
 * "ZEV-Tarif fehlt für 01.01.2024 (und weitere)".
 *
 * The tariff type maps to the translation key `TARIF_LUECKE_<typ>` and the
 * "more gaps" hint to `TARIF_LUECKE_WEITERE`; the date is language-neutral.
 */
export function formatTarifLuecke(luecke: TarifLuecke, translationService: TranslationService): string {
  const typLabel = translationService.translate('TARIF_LUECKE_' + luecke.tarifTyp);
  const weitere = luecke.weitere ? ' ' + translationService.translate('TARIF_LUECKE_WEITERE') : '';
  return `${typLabel} ${luecke.datum}${weitere}`;
}
