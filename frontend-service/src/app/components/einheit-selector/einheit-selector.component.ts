import { Component, OnInit, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EinheitService } from '../../services/einheit.service';
import { Einheit, EinheitTyp } from '../../models/einheit.model';
import { TranslatePipe } from '../../pipes/translate.pipe';
import { EinheitTypPipe } from '../../pipes/einheit-typ.pipe';

@Component({
  selector: 'app-einheit-selector',
  standalone: true,
  imports: [CommonModule, TranslatePipe, EinheitTypPipe],
  templateUrl: './einheit-selector.component.html',
  styleUrls: ['./einheit-selector.component.css']
})
export class EinheitSelectorComponent implements OnInit {
  /** Wenn true, selektiert "Alle auswählen" nur Konsumenten (z.B. für Rechnungen). */
  @Input() onlyConsumers = false;
  @Output() selectionChange = new EventEmitter<Einheit[]>();

  readonly EinheitTyp = EinheitTyp;
  einheiten: Einheit[] = [];
  selectedEinheitIds: Set<number> = new Set();

  /** Einheiten, die "Alle auswählen" betreffen (alle bzw. nur Konsumenten). */
  get selectableEinheiten(): Einheit[] {
    return this.onlyConsumers
      ? this.einheiten.filter(e => e.typ === EinheitTyp.CONSUMER)
      : this.einheiten;
  }

  constructor(private einheitService: EinheitService) {}

  ngOnInit(): void {
    this.einheitService.getAllEinheiten().subscribe({
      next: (data) => {
        this.einheiten = data.sort((a, b) => {
          if (a.typ !== b.typ) return a.typ === EinheitTyp.CONSUMER ? -1 : 1;
          return (a.name || '').localeCompare(b.name || '');
        });
      },
      error: () => {}
    });
  }

  allSelected(): boolean {
    const selectable = this.selectableEinheiten;
    return selectable.length > 0 && selectable.every(e => e.id != null && this.selectedEinheitIds.has(e.id));
  }

  someSelected(): boolean {
    const selectedCount = this.selectableEinheiten.filter(e => e.id != null && this.selectedEinheitIds.has(e.id)).length;
    return selectedCount > 0 && selectedCount < this.selectableEinheiten.length;
  }

  onSelectAllToggle(): void {
    if (this.allSelected()) {
      this.selectableEinheiten.forEach(e => { if (e.id) this.selectedEinheitIds.delete(e.id); });
    } else {
      this.selectableEinheiten.forEach(e => { if (e.id) this.selectedEinheitIds.add(e.id); });
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
  }
}
