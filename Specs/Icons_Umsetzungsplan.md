# Umsetzungsplan: Icons für Buttons und Menü

## Zusammenfassung

Integration von Feather Icons in die ZEV-Anwendung, um Buttons, Menüeinträge und die Titelzeile mit passenden Icons zu ergänzen. Die Icons werden als wiederverwendbare Angular-Komponente im Design-System implementiert und konsequent in der gesamten Anwendung eingesetzt.

---

## Betroffene Komponenten

### Design-System (neue Dateien)
| Datei | Beschreibung |
|-------|--------------|
| `design-system/src/components/icon/icon.css` | CSS für Icon-Komponente |
| `design-system/src/icons/` | SVG-Icon-Dateien (Feather Icons) |

### Frontend (neue Dateien)
| Datei | Beschreibung |
|-------|--------------|
| `frontend-service/src/app/components/icon/icon.component.ts` | Angular Icon-Komponente |
| `frontend-service/src/app/components/icon/icon.component.html` | Icon-Template |
| `frontend-service/src/app/components/icon/icon.component.css` | Icon-Styling |
| `frontend-service/src/app/components/icon/icons.ts` | Icon-Registry mit SVG-Pfaden |

### Frontend (zu ändernde Dateien)
| Datei | Änderung |
|-------|----------|
| `frontend-service/src/app/components/navigation/navigation.component.html` | Icons für Menüeinträge und Titel |
| `frontend-service/src/app/components/navigation/navigation.component.css` | Styling für Icons im Menü |
| `frontend-service/src/app/components/tarif-list/tarif-list.component.html` | Icons für Buttons |
| `frontend-service/src/app/components/einheit-list/einheit-list.component.html` | Icons für Buttons |
| `frontend-service/src/app/components/solar-calculation/solar-calculation.component.html` | Icons für Buttons |
| `frontend-service/src/app/components/messwerte-upload/messwerte-upload.component.html` | Icons für Buttons |
| `frontend-service/src/app/components/messwerte-chart/messwerte-chart.component.html` | Icons für Buttons |
| `frontend-service/src/app/components/statistik/statistik.component.html` | Icons für Buttons |
| `frontend-service/src/app/components/rechnungen/rechnungen.component.html` | Icons für Buttons |
| `frontend-service/src/app/components/einheit-form/einheit-form.component.html` | Icons für Buttons |
| `frontend-service/src/app/components/tarif-form/tarif-form.component.html` | Icons für Buttons |
| `frontend-service/src/app/components/translation-editor/translation-editor.component.html` | Icons für Buttons |
| `frontend-service/src/app/components/kebab-menu/kebab-menu.component.html` | Icons für Menüeinträge |
| `design-system/src/components/button/button.css` | Button-Styling für Icon-Integration |

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Icon-Komponente (Design-System) | CSS für Icon-Komponente erstellen |
| [x] | 2. Icon-Komponente (Angular) | Standalone Icon-Component mit Icon-Registry |
| [x] | 3. Icon-SVGs | Benötigte Feather Icons als SVG-Pfade integrieren |
| [x] | 4. Button-Styling | CSS-Anpassungen für Buttons mit Icons |
| [x] | 5. Navigation-Icons | Icons für alle Menüeinträge hinzufügen |
| [x] | 6. Titel-Icon | Sun-Icon in der Titelzeile integrieren |
| [x] | 7. Button-Icons (Listen) | Icons für Buttons in Listen-Komponenten |
| [x] | 8. Button-Icons (Formulare) | Icons für Buttons in Formular-Komponenten |
| [x] | 9. Kebab-Menü Icons | Icons für Aktionen im Dropdown-Menü |
| [x] | 10. Design-System-Showcase | Dokumentation der Icon-Komponente |

---

## Detaillierte Phasenbeschreibung

### Phase 1: Icon-Komponente (Design-System)

**Datei:** `design-system/src/components/icon/icon.css`

```css
.zev-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1em;
  height: 1em;
  vertical-align: middle;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.zev-icon--sm { width: 0.875em; height: 0.875em; }
.zev-icon--md { width: 1em; height: 1em; }
.zev-icon--lg { width: 1.25em; height: 1.25em; }

/* Button-Integration */
.zev-button .zev-icon {
  margin-right: var(--spacing-xs);
}

/* Navbar-Integration */
.zev-navbar__link .zev-icon {
  margin-right: var(--spacing-xs);
}
```

### Phase 2: Icon-Komponente (Angular)

**Datei:** `frontend-service/src/app/components/icon/icon.component.ts`

```typescript
@Component({
  selector: 'app-icon',
  standalone: true,
  template: `<svg [attr.class]="iconClass" [innerHTML]="svgContent"></svg>`,
  styleUrl: './icon.component.css'
})
export class IconComponent {
  @Input() name!: string;
  @Input() size: 'sm' | 'md' | 'lg' = 'md';

  get iconClass(): string {
    return `zev-icon zev-icon--${this.size}`;
  }

  get svgContent(): SafeHtml {
    return this.sanitizer.bypassSecurityTrustHtml(ICONS[this.name] || '');
  }
}
```

### Phase 3: Icon-SVGs

**Datei:** `frontend-service/src/app/components/icon/icons.ts`

Benötigte Icons aus Feather Icons (MIT-Lizenz):

| Icon-Name | Verwendung |
|-----------|------------|
| `sun` | Titel (ZEV = Solarstrom) |
| `upload` | Messwerte Upload |
| `home` | Einheiten Verwaltung |
| `zap` | Solar Distribution |
| `bar-chart-2` | Messwerte Grafik |
| `pie-chart` | Statistik |
| `file-text` | Rechnungen |
| `tag` | Tarife |
| `globe` | Translation Editor |
| `log-out` | Logout |
| `plus` | Neu erstellen |
| `check` | Speichern/Validieren |
| `x` | Abbrechen/Schließen |
| `edit-2` | Bearbeiten |
| `trash-2` | Löschen |
| `download` | Export/PDF |
| `search` | Anzeigen/Suchen |
| `calculator` | Berechnen |

### Phase 4: Button-Styling

**Änderung in:** `design-system/src/components/button/button.css`

```css
.zev-button {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
}

.zev-button .zev-icon {
  flex-shrink: 0;
}
```

### Phase 5: Navigation-Icons

**Änderung in:** `frontend-service/src/app/components/navigation/navigation.component.html`

Beispiel für Menüeintrag mit Icon:
```html
<a routerLink="/upload" class="zev-navbar__link">
  <app-icon name="upload"></app-icon>
  {{ 'MESSWERTE_UPLOAD' | translate }}
</a>
```

Icon-Zuordnung für Menüeinträge:

| Menüeintrag | Icon |
|-------------|------|
| Messwerte Upload | `upload` |
| Einheiten Verwaltung | `home` |
| Solar Distribution | `zap` |
| Messwerte Grafik | `bar-chart-2` |
| Statistik | `pie-chart` |
| Rechnungen | `file-text` |
| Tarife | `tag` |
| Translation Editor | `globe` |
| Logout | `log-out` |

### Phase 6: Titel-Icon

**Änderung in:** `frontend-service/src/app/components/navigation/navigation.component.html`

```html
<h1 class="zev-navbar__title">
  <app-icon name="sun" size="lg"></app-icon>
  {{ 'ZEV_MANAGEMENT' | translate }}
</h1>
```

### Phase 7: Button-Icons (Listen)

Icon-Zuordnung für Buttons in Listen:

| Button-Text | Icon |
|-------------|------|
| Neue Einheit erstellen | `plus` |
| Neuer Tarif erstellen | `plus` |
| Quartale validieren | `check` |
| Jahre validieren | `check` |

### Phase 8: Button-Icons (Formulare)

Icon-Zuordnung für Formular-Buttons:

| Button-Text | Icon |
|-------------|------|
| Speichern | `check` |
| Abbrechen | `x` |
| Hochladen | `upload` |
| Berechnen | `calculator` |
| Anzeigen | `search` |
| PDF Export | `download` |

### Phase 9: Kebab-Menü Icons

**Änderung in:** `frontend-service/src/app/components/kebab-menu/`

Icon-Zuordnung für Kebab-Menü:

| Aktion | Icon |
|--------|------|
| Bearbeiten | `edit-2` |
| Löschen | `trash-2` |

### Phase 10: Design-System-Showcase

**Änderung in:** `frontend-service/src/app/components/design-system-showcase/`

Neuer Abschnitt "Icons" mit:
- Übersicht aller verfügbaren Icons
- Größen-Varianten (sm, md, lg)
- Beispiele für Button- und Menü-Integration

---

## Validierungen

### Frontend-Validierungen
1. **Icon-Name:** Warnung in Konsole wenn Icon-Name nicht existiert
2. **Barrierefreiheit:** Icons in Buttons haben `aria-hidden="true"` (Text ist vorhanden)
3. **Performance:** Icons werden inline als SVG gerendert (kein HTTP-Request)

---

## Offene Punkte / Annahmen

1. **Annahme:** Feather Icons werden als SVG-Pfade direkt in die Anwendung eingebettet (keine externe Abhängigkeit)
2. **Annahme:** Die Icons werden in der gleichen Farbe wie der umgebende Text dargestellt (currentColor)
3. **Annahme:** Die Icon-Größe in Buttons ist relativ zur Schriftgröße (1em)
4. **Annahme:** Das bestehende Favicon (Sun-Icon) bleibt unverändert
5. **Entscheidung:** Feather Icons werden verwendet (MIT-Lizenz, ca. 280 Icons, minimalistischer Stil)
6. **Annahme:** Icons werden nur links vom Text platziert (nie rechts)
7. **Annahme:** Der Sprachschalter (DE/EN) erhält kein Icon (zu kurzer Text)
