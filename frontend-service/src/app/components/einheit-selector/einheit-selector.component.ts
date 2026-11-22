import { Component, OnInit, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EinheitService } from '../../services/einheit.service';
import { Einheit, EinheitTyp, MesspunktGruppe } from '../../models/einheit.model';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { MesspunktGruppePipe } from '../../pipes/messpunkt-gruppe.pipe';
import { messpunktGruppen } from '../../utils/messpunkt-gruppen';

@Component({
  selector: 'app-einheit-selector',
  standalone: true,
  imports: [CommonModule, TranslatePipe, MesspunktGruppePipe],
  templateUrl: './einheit-selector.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./einheit-selector.component.css']
})
export class EinheitSelectorComponent implements OnInit {
  /**
   * Wenn true, selektiert "Alle auswählen" nur die rechnungsfähigen Einheiten
   * (Konsumenten und Ladestationen) — z.B. für Rechnungen.
   */
  @Input() onlyConsumers = false;
  /**
   * Wenn true, steht nach den Einheiten je Messpunkt mit mehreren Einheiten ein Zusatzeintrag
   * (z.B. Bezug / Rücklieferung desselben Zählers) — für die Messwerte-Grafik
   * (Specs/Grafiken.md, FR-8). "Alle auswählen" schliesst diese Einträge dann ein.
   */
  @Input() mitMesspunktGruppen = false;
  @Output() selectionChange = new EventEmitter<Einheit[]>();
  /** Die gewählten Messpunkt-Gruppen; nur mit {@link mitMesspunktGruppen}. */
  @Output() gruppenChange = new EventEmitter<MesspunktGruppe[]>();

  readonly EinheitTyp = EinheitTyp;
  einheiten: Einheit[] = [];
  selectedEinheitIds: Set<number> = new Set();
  /** Gewählte Gruppen, geschlüsselt nach Messpunkt. */
  selectedMesspunkte: Set<string> = new Set();

  /** Messpunkte mit mehreren Einheiten; leer, solange {@link mitMesspunktGruppen} aus ist. */
  get gruppen(): MesspunktGruppe[] {
    return this.mitMesspunktGruppen ? messpunktGruppen(this.einheiten) : [];
  }

  /** Einheiten, die "Alle auswählen" betreffen (alle bzw. nur Konsumenten). */
  get selectableEinheiten(): Einheit[] {
    return this.onlyConsumers
      ? this.einheiten.filter(
          e => e.typ === EinheitTyp.CONSUMER || e.typ === EinheitTyp.LADESTATION)
      : this.einheiten;
  }

  constructor(private einheitService: EinheitService) {}

  ngOnInit(): void {
    this.einheitService.getAllEinheiten().subscribe({
      next: (data) => {
        // Konsumenten zuerst, innerhalb beider Gruppen nach Name. Der Rang muss fuer BEIDE Seiten
        // gelten: Der fruehere Vergleich lieferte fuer zwei Nicht-Konsumenten verschiedenen Typs
        // in beiden Richtungen 1 - die Reihenfolge hing dann vom Browser ab (Firefox zeigte die
        // Ruecklieferung vor dem Bezug, Chromium umgekehrt).
        const rang = (e: Einheit) => e.typ === EinheitTyp.CONSUMER ? 0 : 1;
        this.einheiten = data.sort((a, b) =>
          rang(a) - rang(b) || (a.name || '').localeCompare(b.name || ''));
      },
      error: () => {}
    });
  }

  allSelected(): boolean {
    const selectable = this.selectableEinheiten;
    return selectable.length > 0 && selectable.every(e => e.id != null && this.selectedEinheitIds.has(e.id))
      && this.gruppen.every(g => this.selectedMesspunkte.has(g.messpunkt));
  }

  someSelected(): boolean {
    const selectedCount = this.selectableEinheiten.filter(e => e.id != null && this.selectedEinheitIds.has(e.id)).length
      + this.gruppen.filter(g => this.selectedMesspunkte.has(g.messpunkt)).length;
    return selectedCount > 0 && selectedCount < this.selectableEinheiten.length + this.gruppen.length;
  }

  onSelectAllToggle(): void {
    if (this.allSelected()) {
      this.selectableEinheiten.forEach(e => { if (e.id) this.selectedEinheitIds.delete(e.id); });
      this.gruppen.forEach(g => this.selectedMesspunkte.delete(g.messpunkt));
    } else {
      this.selectableEinheiten.forEach(e => { if (e.id) this.selectedEinheitIds.add(e.id); });
      this.gruppen.forEach(g => this.selectedMesspunkte.add(g.messpunkt));
    }
    this.emitSelection();
  }

  onGruppeToggle(messpunkt: string): void {
    if (this.selectedMesspunkte.has(messpunkt)) {
      this.selectedMesspunkte.delete(messpunkt);
    } else {
      this.selectedMesspunkte.add(messpunkt);
    }
    this.emitSelection();
  }

  onEinheitToggle(einheitId: number): void {
    if (this.selectedEinheitIds.has(einheitId)) {
      this.selectedEinheitIds.delete(einheitId);
    } else {
      this.selectedEinheitIds.add(einheitId);
    }
    this.emitSelection();
  }

  private emitSelection(): void {
    this.selectionChange.emit(this.einheiten.filter(e => e.id && this.selectedEinheitIds.has(e.id)));
    if (this.mitMesspunktGruppen) {
      this.gruppenChange.emit(this.gruppen.filter(g => this.selectedMesspunkte.has(g.messpunkt)));
    }
  }
}
