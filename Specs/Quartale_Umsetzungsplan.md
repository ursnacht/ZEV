# Umsetzungsplan: Auswahl Quartal für Zeitbereich

## Zusammenfassung

Implementierung einer Quartalsauswahl-Komponente, die auf allen Seiten mit Zeitbereichsauswahl (Rechnungen, Messwerte-Chart, Statistik, Solar-Berechnung) verwendet wird. Die Quartal-Buttons werden horizontal über den Datumfeldern angezeigt.

---

## Phase 1: Design System erweitern

### 1.1 Neue CSS-Komponente für Quartal-Buttons

**Datei:** `design-system/src/components/quarter-selector/quarter-selector.css`

```css
/* Quarter Selector - Horizontale Button-Reihe für Quartalsauswahl */
.zev-quarter-selector {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-xs);
  margin-bottom: var(--spacing-md);
}

.zev-quarter-button {
  padding: var(--spacing-xs) var(--spacing-sm);
  border: 1px solid var(--color-gray-300);
  border-radius: var(--radius-sm);
  background-color: var(--color-white);
  color: var(--color-gray-800);
  font-size: var(--font-size-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.zev-quarter-button:hover {
  border-color: var(--color-primary);
  background-color: var(--color-gray-50);
}

.zev-quarter-button--active {
  background-color: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-white);
}
```

### 1.2 Design System Index aktualisieren

**Datei:** `design-system/src/components/index.css`

Import hinzufügen:
```css
@import './quarter-selector/quarter-selector.css';
```

### 1.3 Design System bauen

```bash
cd design-system && npm run build
```

---

## Phase 2: Angular Komponente erstellen

### 2.1 Neue wiederverwendbare Komponente

**Datei:** `frontend-service/src/app/components/quarter-selector/quarter-selector.component.ts`

```typescript
@Component({
  selector: 'app-quarter-selector',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="zev-quarter-selector">
      <button *ngFor="let q of quarters"
              type="button"
              class="zev-quarter-button"
              [class.zev-quarter-button--active]="isSelected(q)"
              (click)="selectQuarter(q)">
        {{ q.label }}
      </button>
    </div>
  `
})
export class QuarterSelectorComponent implements OnInit {
  @Output() quarterSelected = new EventEmitter<{von: string, bis: string}>();
  @Input() selectedVon: string = '';
  @Input() selectedBis: string = '';

  quarters: Quarter[] = [];

  ngOnInit(): void {
    this.quarters = this.calculateQuarters();
  }

  private calculateQuarters(): Quarter[] {
    // Berechne die letzten 5 Quartale ab heute
  }

  selectQuarter(quarter: Quarter): void {
    this.quarterSelected.emit({von: quarter.von, bis: quarter.bis});
  }

  isSelected(quarter: Quarter): boolean {
    return this.selectedVon === quarter.von && this.selectedBis === quarter.bis;
  }
}

interface Quarter {
  label: string;      // z.B. "Q3/2024"
  von: string;        // z.B. "2024-07-01"
  bis: string;        // z.B. "2024-09-30"
}
```

### 2.2 Quartalsberechnung

Logik zur Berechnung der letzten 5 Quartale:

```typescript
private calculateQuarters(): Quarter[] {
  const quarters: Quarter[] = [];
  const today = new Date();

  // Aktuelles Quartal bestimmen
  let year = today.getFullYear();
  let quarter = Math.ceil((today.getMonth() + 1) / 3);

  // 5 Quartale rückwärts
  for (let i = 0; i < 5; i++) {
    const q = this.createQuarter(year, quarter);
    quarters.unshift(q); // Am Anfang einfügen (ältestes zuerst)

    quarter--;
    if (quarter < 1) {
      quarter = 4;
      year--;
    }
  }

  return quarters;
}

private createQuarter(year: number, quarter: number): Quarter {
  const startMonth = (quarter - 1) * 3;
  const von = new Date(year, startMonth, 1);
  const bis = new Date(year, startMonth + 3, 0); // Letzter Tag des Quartals

  return {
    label: `Q${quarter}/${year}`,
    von: this.formatDate(von),
    bis: this.formatDate(bis)
  };
}
```

---

## Phase 3: Integration in bestehende Komponenten

### 3.1 Rechnungen-Komponente

**Datei:** `frontend-service/src/app/components/rechnungen/rechnungen.component.html`

```html
<!-- Vor den Datumfeldern einfügen -->
<app-quarter-selector
  [selectedVon]="dateFrom"
  [selectedBis]="dateTo"
  (quarterSelected)="onQuarterSelected($event)">
</app-quarter-selector>

<div class="zev-date-range-row">
  <!-- Bestehende Datumfelder -->
</div>
```

**Datei:** `frontend-service/src/app/components/rechnungen/rechnungen.component.ts`

```typescript
imports: [CommonModule, FormsModule, TranslatePipe, QuarterSelectorComponent],

onQuarterSelected(event: {von: string, bis: string}): void {
  this.dateFrom = event.von;
  this.dateTo = event.bis;
}
```

### 3.2 Messwerte-Chart-Komponente

Analog zu 3.1 - QuarterSelectorComponent importieren und integrieren.

### 3.3 Statistik-Komponente

Analog zu 3.1 - QuarterSelectorComponent importieren und integrieren.

### 3.4 Solar-Calculation-Komponente

Analog zu 3.1 - QuarterSelectorComponent importieren und integrieren.

---

## Phase 4: Übersetzungen

### 4.1 Flyway Migration

**Datei:** `backend-service/src/main/resources/db/migration/V<next>__add_quarter_translations.sql`

```sql
INSERT INTO translations (key, deutsch, englisch) VALUES
('QUARTAL_WAEHLEN', 'Quartal wählen', 'Select quarter')
ON CONFLICT (key) DO NOTHING;
```

---

## Phase 5: Testing

### 5.1 Unit Tests

**Datei:** `frontend-service/src/app/components/quarter-selector/quarter-selector.component.spec.ts`

- Test: Korrekte Berechnung der 5 Quartale
- Test: Korrektes Label-Format "Q<x>/<Jahr>"
- Test: Korrekte Von/Bis-Daten für jedes Quartal
- Test: Event wird bei Klick ausgelöst
- Test: Active-State wird korrekt gesetzt

### 5.2 E2E Tests

**Datei:** `frontend-service/e2e/rechnungen.spec.ts`

- Test: Quartal-Buttons sind sichtbar
- Test: Klick auf Quartal setzt Datumfelder korrekt
- Test: Datumfelder können nach Quartalsauswahl noch manuell angepasst werden

---

## Umsetzungsreihenfolge

| # | Aufgabe | Abhängigkeit |
|---|---------|--------------|
| 1 | Design System CSS erstellen | - |
| 2 | Design System bauen | 1 |
| 3 | QuarterSelectorComponent erstellen | 2 |
| 4 | Unit Tests für QuarterSelectorComponent | 3 |
| 5 | Integration Rechnungen-Komponente | 3 |
| 6 | Integration Messwerte-Chart-Komponente | 3 |
| 7 | Integration Statistik-Komponente | 3 |
| 8 | Integration Solar-Calculation-Komponente | 3 |
| 9 | Flyway Migration für Übersetzungen | - |
| 10 | E2E Tests | 5-8 |

---

## Offene Fragen / Annahmen

1. **Annahme:** Das aktuelle Quartal ist immer das letzte der 5 angezeigten Quartale, auch wenn es noch nicht abgeschlossen ist.
2. **Annahme:** Die Quartal-Buttons haben keine Tooltip/Hover-Info mit dem genauen Datumsbereich.
3. **Annahme:** Es gibt keine Validierung, ob für das gewählte Quartal Messdaten vorhanden sind.
