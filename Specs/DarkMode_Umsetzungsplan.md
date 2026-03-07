# Umsetzungsplan: DarkMode

## Zusammenfassung

Implementierung eines Dark/Light Mode für die ZEV-Anwendung. Die Umschaltung erfolgt über einen Toggle-Button in der Navbar (direkt sichtbar, oberhalb des Hamburger-Icons) sowie im Hamburger-Menü (oberhalb der Sprachwahl). Das Theme wird via `data-theme="dark"` Attribut auf `<html>` gesteuert und im `localStorage` persistiert. Die Dark-Mode-Farbpalette orientiert sich an Bootstrap/Material-Referenzwerten. Das Feature ist rein Frontend-seitig – kein Backend-Eingriff.

---

## Betroffene Komponenten

| Typ | Datei | Änderungsart |
|-----|-------|--------------|
| Design System Tokens | `design-system/src/tokens/tokens.css` | Änderung |
| Design System Tokens | `design-system/src/tokens/tokens.ts` | Änderung |
| Design System Navigation | `design-system/src/components/navigation/navbar.css` | Änderung |
| Design System Button | `design-system/src/components/button/button.css` | Änderung |
| Design System Card | `design-system/src/components/card/card.css` | Änderung |
| Design System Table | `design-system/src/components/table/table.css` | Änderung |
| Design System Form | `design-system/src/components/form/form.css` | Änderung |
| Design System Message | `design-system/src/components/message/message.css` | Änderung |
| Design System Panel | `design-system/src/components/panel/panel.css` | Änderung |
| Design System Typography | `design-system/src/components/typography/typography.css` | Änderung |
| Design System Status | `design-system/src/components/status/status.css` | Änderung |
| Design System Container | `design-system/src/components/container/container.css` | Änderung |
| Angular Service | `frontend-service/src/app/services/theme.service.ts` | Neu |
| Angular Component | `frontend-service/src/app/components/navigation/navigation.component.ts` | Änderung |
| Angular Component | `frontend-service/src/app/components/navigation/navigation.component.html` | Änderung |
| Angular HTML | `frontend-service/src/index.html` | Änderung |
| DB Migration | `backend-service/src/main/resources/db/migration/V48__Add_DarkMode_Translations.sql` | Neu |

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
|  [x]   | 1. Design System: Dark-Mode-Tokens | Dark-Mode-Farbpalette in `tokens.css` und `tokens.ts` ergänzen (Selektor `[data-theme="dark"]`) |
|  [x]   | 2. Design System: Komponenten-CSS | Dark-Mode-Overrides für alle Komponenten-CSS-Dateien ergänzen |
|  [x]   | 3. Design System Build | `cd design-system && npm run build` ausführen |
|  [x]   | 4. ThemeService | Angular-Service für Theme-Logik (localStorage, prefers-color-scheme, html-Attribut) |
|  [x]   | 5. FOUC-Prävention | Inline-Script in `index.html` zum Setzen des Themes vor erstem Paint |
|  [x]   | 6. Navigation: Toggle-Button | Toggle in Navbar (direkt sichtbar) und im Menü (oberhalb Sprachwahl) |
|  [x]   | 7. Übersetzungen | Flyway-Migration V48 für `DARK_MODE` und `LIGHT_MODE` Keys |

---

## Detailbeschreibung der Phasen

### Phase 1: Design System – Dark-Mode-Tokens

**Datei:** `design-system/src/tokens/tokens.css`

Dark-Mode-Block additiv ergänzen. Alle Farben folgen Bootstrap 5 / Material Design Dark-Theme-Referenzwerten:

```css
[data-theme="dark"] {
  /* Backgrounds */
  --color-white: #1e1e2e;
  --color-gray-50: #181825;
  --color-gray-100: #1e1e2e;
  --color-gray-200: #27273a;
  --color-gray-300: #313244;
  --color-gray-400: #45475a;
  --color-gray-500: #585b70;
  --color-gray-600: #a6adc8;
  --color-gray-700: #bac2de;
  --color-gray-800: #cdd6f4;
  --color-gray-900: #cdd6f4;

  /* Primary (etwas heller für Dark Mode) */
  --color-primary: #69db7c;
  --color-primary-hover: #51cf66;
  --color-primary-light: #8ce99a;
  --color-primary-dark: #2f9e44;

  /* Secondary */
  --color-secondary: #74c0fc;
  --color-secondary-hover: #4dabf7;
  --color-secondary-active: #339af0;
  --color-secondary-light: #a5d8ff;
  --color-secondary-dark: #1c7ed6;

  /* Danger */
  --color-danger: #ff6b6b;
  --color-danger-hover: #fa5252;
  --color-danger-light: #ffa8a8;
  --color-danger-dark: #e03131;

  /* Success */
  --color-success-bg: #1c3829;
  --color-success-text: #69db7c;
  --color-success-border: #2f9e44;

  /* Error */
  --color-error-bg: #3b1219;
  --color-error-text: #ff6b6b;
  --color-error-border: #c92a2a;

  /* Shadows */
  --shadow-card: 0 2px 4px rgba(0, 0, 0, 0.4);

  /* Table resize handle */
  --table-resize-handle-color: var(--color-gray-400);
}

@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    /* Gleiche Werte wie [data-theme="dark"] – wird automatisch bei fehlender Präferenz gesetzt */
    --color-white: #1e1e2e;
    /* ... alle Dark-Mode-Variablen wiederholen ... */
  }
}

@media print {
  /* Drucken immer im Light Mode */
  [data-theme="dark"] {
    --color-white: #ffffff;
    --color-gray-50: #f8f8f8;
    /* ... alle Light-Mode-Werte wiederholen ... */
  }
}
```

**Datei:** `design-system/src/tokens/tokens.ts`

Dark-Mode-Token-Objekt ergänzen:

```typescript
export const darkColors = {
  neutral: {
    white: '#1e1e2e',
    // ...
  },
  // ...
} as const;
```

### Phase 2: Design System – Komponenten-CSS

Für jede Komponenten-CSS-Datei prüfen, ob Farben als Tokens (`var(--color-*)`) referenziert werden. Hardcodierte Farbwerte auf Tokens umstellen. Dark-Mode-Overrides sind dann automatisch durch Phase 1 abgedeckt – nur Ausnahmen brauchen explizite Overrides.

Explizite `[data-theme="dark"]`-Blöcke nötig für Sonderfälle (z.B. Box-Shadows, Borders mit `rgba`):

```css
/* Beispiel: table.css */
[data-theme="dark"] .zev-table th {
  border-bottom-color: var(--color-gray-400);
}

[data-theme="dark"] .zev-table tr:hover {
  background-color: var(--color-gray-200);
}
```

### Phase 3: Design System Build

```bash
cd design-system && npm run build
```

Sicherstellen, dass keine Build-Fehler auftreten.

### Phase 4: ThemeService

**Datei:** `frontend-service/src/app/services/theme.service.ts`

```typescript
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly STORAGE_KEY = 'zev-theme';
  private _isDarkMode = signal(false);

  readonly isDarkMode = this._isDarkMode.asReadonly();

  constructor() {
    this.initTheme();
  }

  private initTheme(): void {
    const stored = this.loadFromStorage();
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const isDark = stored !== null ? stored === 'dark' : prefersDark;
    this.applyTheme(isDark);
  }

  toggleTheme(): void {
    this.applyTheme(!this._isDarkMode());
  }

  private applyTheme(dark: boolean): void {
    this._isDarkMode.set(dark);
    document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
    this.saveToStorage(dark ? 'dark' : 'light');
  }

  private loadFromStorage(): string | null {
    try {
      return localStorage.getItem(this.STORAGE_KEY);
    } catch {
      return null;
    }
  }

  private saveToStorage(theme: string): void {
    try {
      localStorage.setItem(this.STORAGE_KEY, theme);
    } catch {
      // localStorage nicht verfügbar – ignorieren
    }
  }
}
```

### Phase 5: FOUC-Prävention

**Datei:** `frontend-service/src/index.html`

Inline-Script im `<head>` vor allen anderen Styles einfügen:

```html
<script>
  (function() {
    try {
      var theme = localStorage.getItem('zev-theme');
      if (!theme) {
        theme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
      }
      document.documentElement.setAttribute('data-theme', theme);
    } catch(e) {}
  })();
</script>
```

### Phase 6: Navigation – Toggle-Button

**Datei:** `frontend-service/src/app/components/navigation/navigation.component.ts`

`ThemeService` injecten, `toggleDarkMode()` und `isDarkMode` ergänzen (Muster analog zu `switchLanguage()`):

```typescript
private readonly themeService = inject(ThemeService);

readonly isDarkMode = this.themeService.isDarkMode;

toggleDarkMode(): void {
  this.themeService.toggleTheme();
}
```

**Datei:** `frontend-service/src/app/components/navigation/navigation.component.html`

1. Toggle direkt in der Navbar (sichtbar ohne Menü öffnen), neben dem Hamburger-Button:

```html
<div class="zev-navbar__right">
  <!-- Dark-Mode-Toggle (direkt sichtbar) -->
  <button class="zev-hamburger" (click)="toggleDarkMode()" [attr.aria-label]="isDarkMode() ? ('LIGHT_MODE' | translate) : ('DARK_MODE' | translate)">
    <app-icon [name]="isDarkMode() ? 'sun' : 'moon'"></app-icon>
  </button>
  <!-- Hamburger-Menü-Button -->
  <button class="zev-hamburger" (click)="toggleMenu()" ...>
```

2. Toggle im Menü (oberhalb Sprachwahl):

```html
<li>
  <button (click)="toggleDarkMode()" class="zev-navbar__link">
    <app-icon [name]="isDarkMode() ? 'sun' : 'moon'"></app-icon>
    {{ (isDarkMode() ? 'LIGHT_MODE' : 'DARK_MODE') | translate }}
  </button>
</li>
<li>
  <button (click)="switchLanguage()" class="zev-navbar__link">
    ...
```

### Phase 7: Übersetzungen

**Datei:** `backend-service/src/main/resources/db/migration/V48__Add_DarkMode_Translations.sql`

```sql
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('DARK_MODE', 'Dark Mode', 'Dark Mode'),
('LIGHT_MODE', 'Light Mode', 'Light Mode')
ON CONFLICT (key) DO NOTHING;
```

---

## Validierungen

### Frontend
* `localStorage`-Zugriff immer in try/catch (Private Browsing)
* Ungültiger Wert in localStorage → Fallback auf `prefers-color-scheme`
* Theme-Initialisierung vor erstem Angular-Render (FOUC-Prävention via Inline-Script)

### Design System
* Alle Komponenten-CSS dürfen keine hardcodierten Hex-Farbwerte enthalten (nur `var(--color-*)`)
* Druckausgabe (`@media print`) ignoriert Dark Mode

---

## Offene Punkte / Annahmen

1. **Annahme:** Dark-Mode-Palette basiert auf Catppuccin Mocha / Bootstrap 5.3 Dark-Referenzwerten (dunkle Blautöne als Neutral-Palette)
2. **Annahme:** Das Dark-Mode-Icon in der Navbar (ausserhalb des Menüs) verwendet die bestehende `.zev-hamburger`-Klasse für konsistentes Styling – ggf. eigene CSS-Klasse nötig
3. **Offen:** Sollen Chart.js-Farben im Dark Mode angepasst werden? → Gemäss Spec Out of Scope
4. **Annahme:** `Signal`-basierter `ThemeService` (Angular 21 Signals), kein `BehaviorSubject`
5. **Annahme:** Der `@media (prefers-color-scheme: dark)`-Block in `tokens.css` wiederholt alle Dark-Mode-Werte (kein CSS-Variablen-Sharing über Media Queries möglich)
