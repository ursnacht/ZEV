import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  Mengeneinheit, Tarif, TarifTyp, TARIFTYPEN_MIT_MENGENEINHEIT, preisEinheitKey, waehlbareMengeneinheiten
} from '../../models/tarif.model';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-tarif-form',
  standalone: true,
  imports: [FormsModule, TranslatePipe, IconComponent],
  templateUrl: './tarif-form.component.html',
  styleUrls: ['./tarif-form.component.css']
})
export class TarifFormComponent implements OnInit {
  @Input() tarif: Tarif | null = null;
  @Output() save = new EventEmitter<Tarif>();
  @Output() cancel = new EventEmitter<void>();

  formData: Tarif = {
    bezeichnung: '',
    tariftyp: TarifTyp.ZEV,
    preis: 0,
    gueltigVon: '',
    gueltigBis: '',
    produzentVerrechnen: false
  };

  readonly TarifTyp = TarifTyp;
  readonly Mengeneinheit = Mengeneinheit;

  /**
   * Nur die Werte, keine Beschriftungen: Die Anzeigetexte kommen aus dem TranslationService
   * (`TARIFTYP_<wert>`), wie in der Tarif-Liste. Frueher standen sie hier hartcodiert und waren
   * damit die einzigen deutschen Texte im Formular (Specs/generell.md, i18n).
   */
  tarifTypOptions: TarifTyp[] = [
    TarifTyp.ZEV, TarifTyp.VNB, TarifTyp.GRUNDGEBUEHR, TarifTyp.LADESTROM, TarifTyp.ZUSATZ
  ];

  /**
   * Auswahl der Mengeneinheit - nur bei Tariftypen mit eigener Einheit sichtbar, die Werte je Typ
   * (ZUSATZ: kWh, Monat, Stueck; GRUNDGEBUEHR: Monat oder Tag). Die Werte sind zugleich die
   * Uebersetzungs-Keys.
   */
  get mengeneinheitOptions(): Mengeneinheit[] {
    return waehlbareMengeneinheiten(this.formData.tariftyp);
  }

  /** Wahr, wenn der gewaehlte Typ eine eigene Mengeneinheit traegt (ZUSATZ, GRUNDGEBUEHR). */
  get brauchtMengeneinheit(): boolean {
    return TARIFTYPEN_MIT_MENGENEINHEIT.includes(this.formData.tariftyp);
  }

  /**
   * Uebersetzungs-Key der Bezugsgroesse des Preises: "CHF pro kWh/Monat/Stueck". Leer, solange
   * ein Tarif mit freier Einheit noch keine gewaehlt hat - dann steht nur "CHF" da.
   */
  get preisEinheit(): string {
    return preisEinheitKey(this.formData.tariftyp, this.formData.mengeneinheit);
  }

  /** Typ, zu dem die aktuelle Mengeneinheit gehoert - um echte Typwechsel zu erkennen. */
  private letzterTyp?: TarifTyp;

  /**
   * Setzt die Mengeneinheit bei einem echten Typwechsel neu: Grundgebuehr "pro Monat" (das
   * bisherige Verhalten), alle anderen leer.
   *
   * Bewusst neu statt "behalten, wenn zulaessig": Monat ist auch bei ZUSATZ zulaessig. Wer erst
   * Grundgebuehr und dann ZUSATZ waehlte, bekam sonst still die Vorbelegung der Grundgebuehr -
   * bei ZUSATZ soll die Einheit aber bewusst gewaehlt werden (E2E tarifpositionen.spec.ts).
   */
  onTariftypChange(): void {
    if (this.formData.tariftyp === this.letzterTyp) {
      return;
    }
    this.letzterTyp = this.formData.tariftyp;
    this.formData.mengeneinheit =
      this.formData.tariftyp === TarifTyp.GRUNDGEBUEHR ? Mengeneinheit.MONAT : undefined;
  }

  ngOnInit(): void {
    if (this.tarif) {
      this.formData = { ...this.tarif };
      // Grundgebuehr ohne Angabe gilt pro Monat - so zeigt die Auswahl den wirksamen Wert
      if (this.formData.tariftyp === TarifTyp.GRUNDGEBUEHR && !this.formData.mengeneinheit) {
        this.formData.mengeneinheit = Mengeneinheit.MONAT;
      }
    } else {
      // Set default dates: current year start to end
      const now = new Date();
      const year = now.getFullYear();
      this.formData.gueltigVon = `${year}-01-01`;
      this.formData.gueltigBis = `${year}-12-31`;
    }
    this.letzterTyp = this.formData.tariftyp;
  }

  onSubmit(): void {
    if (this.isFormValid()) {
      this.save.emit(this.formData);
    }
  }

  onCancel(): void {
    this.cancel.emit();
  }

  isFormValid(): boolean {
    return !!(
      this.formData.bezeichnung.trim() &&
      this.formData.tariftyp &&
      this.formData.preis > 0 &&
      this.formData.gueltigVon &&
      this.formData.gueltigBis &&
      this.formData.gueltigVon <= this.formData.gueltigBis &&
      // Mengeneinheit ist Pflicht, sobald der Typ eine eigene traegt
      (!this.brauchtMengeneinheit || !!this.formData.mengeneinheit)
    );
  }

  isDateRangeValid(): boolean {
    if (!this.formData.gueltigVon || !this.formData.gueltigBis) {
      return true; // Don't show error if dates not entered yet
    }
    return this.formData.gueltigVon <= this.formData.gueltigBis;
  }
}
