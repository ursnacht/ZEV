# Kebabmenü - Umsetzungsplan

## Übersicht

| Aspekt | Details |
|--------|---------|
| **Spezifikation** | [Kebabmenü.md](Kebabmenü.md) |
| **Betroffene Komponenten** | Design-System, EinheitListComponent, TarifListComponent, TranslationEditorComponent |
| **Technologie** | Angular 19, CSS |
| **Geschätzter Aufwand** | Klein (reine UI-Änderung) |

---

## Phasen-Übersicht

| Phase | Beschreibung | Status |
|-------|--------------|--------|
| 1 | [Design-System: CSS-Styles für Kebabmenü](#phase-1-design-system-css-styles) | ✅ Abgeschlossen |
| 2 | [Angular: KebabMenuComponent erstellen](#phase-2-angular-kebabmenucomponent) | ✅ Abgeschlossen |
| 3 | [Integration: Listen-Komponenten anpassen](#phase-3-integration-listen-komponenten) | ✅ Abgeschlossen |
| 4 | [Tests und Validierung](#phase-4-tests-und-validierung) | ⬜ Offen |

**Legende:** ⬜ Offen | 🔄 In Arbeit | ✅ Abgeschlossen

---

## Phase 1: Design-System: CSS-Styles

**Ziel:** CSS-Komponente für das Kebabmenü im Design-System erstellen

### Aufgaben

| Nr | Aufgabe | Status |
|----|---------|--------|
| 1.1 | Neuen Ordner `design-system/src/components/kebab-menu/` erstellen | ✅ |
| 1.2 | CSS-Datei `kebab-menu.css` mit Styles erstellen | ✅ |
| 1.3 | CSS in `components/index.css` importieren | ✅ |
| 1.4 | Design-System neu builden | ✅ |

### CSS-Struktur

```css
/* Kebab Button */
.zev-kebab-button { ... }
.zev-kebab-button__dot { ... }

/* Dropdown Menu */
.zev-kebab-menu { ... }
.zev-kebab-menu--open { ... }
.zev-kebab-menu__item { ... }
.zev-kebab-menu__item:hover { ... }
.zev-kebab-menu__item--danger { ... }
```

### Design-Spezifikation

| Element | Wert |
|---------|------|
| Kebab-Button Grösse | 32x32px |
| Punkt-Grösse | 4x4px, border-radius: 50% |
| Punkt-Abstand | 3px |
| Menü-Breite | min-width: 140px |
| Menü-Hintergrund | var(--color-primary-dark) |
| Menü-Schatten | var(--shadow-card) |
| Item-Padding | var(--spacing-xs) var(--spacing-md) |
| Hover-Hintergrund | var(--color-primary-hover) |

---

## Phase 2: Angular: KebabMenuComponent

**Ziel:** Wiederverwendbare Angular-Komponente erstellen

### Aufgaben

| Nr | Aufgabe | Status |
|----|---------|--------|
| 2.1 | Komponente erstellen | ✅ |
| 2.2 | Template mit Kebab-Button und Dropdown implementieren | ✅ |
| 2.3 | Input für Menüpunkte (items) definieren | ✅ |
| 2.4 | Output für Klick-Events (itemClick) definieren | ✅ |
| 2.5 | Click-Outside-Detection implementieren | ✅ |
| 2.6 | ESC-Taste Handler implementieren | ✅ |

### Komponenten-Interface

```typescript
// kebab-menu.component.ts
export interface KebabMenuItem {
  label: string;           // Anzeigetext (Translation-Key)
  action: string;          // Action-Identifier ('edit', 'delete')
  danger?: boolean;        // Für Löschen-Button (rot)
}

@Component({
  selector: 'app-kebab-menu',
  ...
})
export class KebabMenuComponent {
  @Input() items: KebabMenuItem[] = [];
  @Output() itemClick = new EventEmitter<string>();

  isOpen = false;

  toggle(): void { ... }
  close(): void { ... }
  onItemClick(action: string): void { ... }
}
```

### Template-Struktur

```html
<div class="zev-kebab-container">
  <button class="zev-kebab-button" (click)="toggle()">
    <span class="zev-kebab-button__dot"></span>
    <span class="zev-kebab-button__dot"></span>
    <span class="zev-kebab-button__dot"></span>
  </button>

  <ul class="zev-kebab-menu" [class.zev-kebab-menu--open]="isOpen">
    <li *ngFor="let item of items">
      <button class="zev-kebab-menu__item"
              [class.zev-kebab-menu__item--danger]="item.danger"
              (click)="onItemClick(item.action)">
        {{ item.label | translate }}
      </button>
    </li>
  </ul>
</div>
```

---

## Phase 3: Integration: Listen-Komponenten

**Ziel:** Bestehende Listen von Buttons auf Kebabmenü umstellen

### Aufgaben

| Nr | Aufgabe | Status |
|----|---------|--------|
| 3.1 | `einheit-list.component.html` anpassen | ✅ |
| 3.2 | `einheit-list.component.ts` Handler anpassen | ✅ |
| 3.3 | `tarif-list.component.html` anpassen | ✅ |
| 3.4 | `tarif-list.component.ts` Handler anpassen | ✅ |
| 3.5 | `translation-editor.component.html` anpassen | ✅ |
| 3.6 | `translation-editor.component.ts` Handler anpassen | ✅ |

### Vorher/Nachher Einheitenverwaltung

**Vorher:**
```html
<td>
  <div class="zev-table-actions">
    <button class="zev-button zev-button--secondary" (click)="onEdit(einheit)">
      {{ 'BEARBEITEN' | translate }}
    </button>
    <button class="zev-button zev-button--danger" (click)="onDelete(einheit.id)">
      {{ 'LOESCHEN' | translate }}
    </button>
  </div>
</td>
```

**Nachher:**
```html
<td>
  <app-kebab-menu
    [items]="menuItems"
    (itemClick)="onMenuAction($event, einheit)">
  </app-kebab-menu>
</td>
```

### Menüpunkte-Definition

```typescript
// In der Komponente
menuItems: KebabMenuItem[] = [
  { label: 'BEARBEITEN', action: 'edit' },
  { label: 'LOESCHEN', action: 'delete', danger: true }
];

onMenuAction(action: string, item: any): void {
  switch (action) {
    case 'edit':
      this.onEdit(item);
      break;
    case 'delete':
      this.onDelete(item.id);
      break;
  }
}
```

### Spezialfall: Translation-Editor

Der Translation-Editor hat "Speichern" und "Löschen" statt "Bearbeiten" und "Löschen":

```typescript
menuItems: KebabMenuItem[] = [
  { label: 'SAVE', action: 'save' },
  { label: 'DELETE', action: 'delete', danger: true }
];
```

---

## Phase 4: Tests und Validierung

**Ziel:** Sicherstellen, dass das Kebabmenü korrekt funktioniert

### Aufgaben

| Nr | Aufgabe | Status |
|----|---------|--------|
| 4.1 | Manuell: Kebabmenü in Einheitenverwaltung testen | ⬜ |
| 4.2 | Manuell: Kebabmenü in Tarifverwaltung testen | ⬜ |
| 4.3 | Manuell: Kebabmenü in Übersetzungen testen | ⬜ |
| 4.4 | Manuell: Click-Outside-Schliessen testen | ⬜ |
| 4.5 | Manuell: ESC-Taste testen | ⬜ |
| 4.6 | Manuell: Nur ein Menü gleichzeitig offen | ⬜ |
| 4.7 | E2E-Tests anpassen (falls vorhanden) | ⬜ |

### Testfälle

| Test | Erwartetes Ergebnis |
|------|---------------------|
| Klick auf Kebab-Icon | Menü öffnet sich |
| Klick auf "Bearbeiten" | Formular öffnet sich, Menü schliesst |
| Klick auf "Löschen" | Bestätigungsdialog erscheint |
| Klick ausserhalb | Menü schliesst sich |
| ESC-Taste | Menü schliesst sich |
| Klick auf anderes Kebab | Erstes Menü schliesst, zweites öffnet |

---

## Dateien

| Datei | Änderung |
|-------|----------|
| `design-system/src/components/kebab-menu/kebab-menu.css` | Neu |
| `design-system/src/components/index.css` | Import hinzufügen |
| `frontend-service/src/app/components/kebab-menu/` | Neue Komponente |
| `frontend-service/src/app/components/einheit-list/` | Anpassen |
| `frontend-service/src/app/components/tarif-list/` | Anpassen |
| `frontend-service/src/app/components/translation-editor/` | Anpassen |

---

## Referenzen

- [Navbar CSS](../design-system/src/components/navigation/navbar.css) - Vorlage für Hover-Styles
- Material Design Guidelines: [Menus](https://m3.material.io/components/menus/overview)
