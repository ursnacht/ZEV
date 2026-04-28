import { Component, OnInit, Output, EventEmitter } from '@angular/core';
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
  @Output() selectionChange = new EventEmitter<Einheit[]>();

  readonly EinheitTyp = EinheitTyp;
  einheiten: Einheit[] = [];
  selectedEinheitIds: Set<number> = new Set();

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
    return this.einheiten.length > 0 && this.selectedEinheitIds.size === this.einheiten.length;
  }

  someSelected(): boolean {
    return this.selectedEinheitIds.size > 0 && this.selectedEinheitIds.size < this.einheiten.length;
  }

  onSelectAllToggle(): void {
    if (this.allSelected()) {
      this.selectedEinheitIds.clear();
    } else {
      this.einheiten.forEach(e => { if (e.id) this.selectedEinheitIds.add(e.id); });
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
