# Umsetzungsplan: Spaltenbreite von Tabellen veränderbar

## Zusammenfassung

Implementierung einer Column-Resize-Funktionalität für alle Tabellen im Frontend (Einheiten, Tarife, Übersetzungen). Der Benutzer kann durch Ziehen eines Resize-Handles die Spaltenbreite verändern. Die Änderungen werden nicht persistiert und gelten nur für die aktuelle Session.

## Betroffene Komponenten

### Design System (CSS)
| Datei | Aktion | Beschreibung |
|-------|--------|--------------|
| `design-system/src/components/table/table.css` | Ändern | Resize-Handle Styles und Cursor-Styles hinzufügen |
| `design-system/src/tokens/tokens.css` | Ändern | Neue Token für Resize-Handle (Farbe, Breite) |

### Frontend - Neue Directive
| Datei | Aktion | Beschreibung |
|-------|--------|--------------|
| `frontend-service/src/app/directives/column-resize.directive.ts` | Neu | Angular Directive für Resize-Logik |

### Frontend - Komponenten (HTML-Anpassungen)
| Datei | Aktion | Beschreibung |
|-------|--------|--------------|
| `frontend-service/src/app/components/einheit-list/einheit-list.component.html` | Ändern | Directive auf Tabelle anwenden |
| `frontend-service/src/app/components/tarif-list/tarif-list.component.html` | Ändern | Directive auf Tabelle anwenden |
| `frontend-service/src/app/components/translation-editor/translation-editor.component.html` | Ändern | `translation-table` auf `zev-table` migrieren, Directive anwenden |
| `frontend-service/src/app/components/translation-editor/translation-editor.component.css` | Ändern | `translation-table` Styles entfernen |

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Design Tokens | Neue CSS-Variablen für Resize-Handle in `tokens.css` |
| [x] | 2. Table CSS | Resize-Handle Styles in `table.css` hinzufügen |
| [x] | 3. Column Resize Directive | Angular Directive mit Drag-Logik erstellen |
| [x] | 4. Einheit-List | Directive auf Einheiten-Tabelle anwenden |
| [x] | 5. Tarif-List | Directive auf Tarif-Tabelle anwenden |
| [x] | 6. Translation-Editor | CSS-Klasse migrieren und Directive anwenden |
| [ ] | 7. Design System Build | Design System neu bauen |

## Technische Details

### Phase 1: Design Tokens
Neue Variablen in `design-system/src/tokens/tokens.css`:
```css
/* Table Resize */
--table-resize-handle-width: 4px;
--table-resize-handle-color: var(--color-gray-400);
--table-resize-handle-hover-color: var(--color-primary);
--table-column-min-width: 50px;
```

### Phase 2: Table CSS
Neue Styles in `design-system/src/components/table/table.css`:
```css
/* Resize Handle */
.zev-table th {
  position: relative;
}

.zev-table__resize-handle {
  position: absolute;
  right: 0;
  top: 0;
  bottom: 0;
  width: var(--table-resize-handle-width);
  background-color: var(--table-resize-handle-color);
  cursor: col-resize;
}

.zev-table__resize-handle:hover {
  background-color: var(--table-resize-handle-hover-color);
}

/* Während des Resizens */
.zev-table--resizing {
  user-select: none;
  cursor: col-resize;
}
```

### Phase 3: Column Resize Directive
Die Directive `appColumnResize` wird auf `<table class="zev-table">` angewendet:

**Funktionalität:**
- Fügt automatisch Resize-Handles zu allen `<th>` Elementen hinzu (außer der letzten Spalte)
- Implementiert Drag-Logik mit `mousedown`, `mousemove`, `mouseup` Events
- Verhindert Resize unter Mindestbreite (Breite des Header-Textes)
- Auto-Fit bei Doppelklick: Passt Spalte automatisch an den breitesten Inhalt an

**API:**
```typescript
@Directive({
  selector: '[appColumnResize]'
})
export class ColumnResizeDirective implements AfterViewInit {
  // Initialisiert Resize-Handles nach View-Init
}
```

### Phase 4-6: Komponenten-Anpassungen
Anwendung der Directive auf alle drei Tabellen:
```html
<table class="zev-table" appColumnResize>
```

### Phase 6 zusätzlich: Translation-Editor Migration
- `translation-table` CSS-Klasse durch `zev-table` ersetzen
- Duplizierte Styles in `translation-editor.component.css` entfernen

## Validierungen

### Frontend
| Regel | Beschreibung |
|-------|--------------|
| Mindestbreite | Spalte darf nicht schmaler als Header-Text werden |
| Letzte Spalte | Kein Resize-Handle auf letzter Spalte |
| Doppelklick | Auto-Fit: Spalte passt sich an breitesten Inhalt an |

## Nicht-funktionale Anforderungen

| Anforderung | Umsetzung |
|-------------|-----------|
| Performance | `requestAnimationFrame` für flüssiges Resizing |
| Cursor | `col-resize` Cursor während des Ziehens |
| Keine Persistierung | Kein LocalStorage, kein Backend-Call |

## Offene Punkte / Annahmen

| Punkt | Status | Anmerkung |
|-------|--------|-----------|
| Resize-Handle Design | Annahme | Grauer senkrechter Strich (4px breit), immer sichtbar |
| Besseres Icon | Offen | Laut Spec: "Gibt es ein besseres Icon?" - aktuell einfacher Strich |
| Statistik-Tabellen | Annahme | Werden nicht angepasst (read-only Datenansicht) |
| Rechnungen-Tabelle | Annahme | Wird nicht angepasst (temporäre Daten) |

## Abhängigkeiten

- Design System muss nach CSS-Änderungen neu gebaut werden (`npm run build` in `design-system/`)
- Keine Backend-Änderungen erforderlich
- Keine Datenbankmigrationen erforderlich
- Keine neuen Übersetzungen erforderlich
