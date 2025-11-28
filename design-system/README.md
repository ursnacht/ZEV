# ZEV Design System

Professionelles Design System für die ZEV (Zusammenschluss zum Eigenverbrauch) Anwendung.

## Features

- 🎨 **Design Tokens**: TypeScript + CSS Custom Properties für Type-Safety
- 📦 **Komponenten-Bibliothek**: Wiederverwendbare UI-Komponenten
- 📚 **Storybook**: Interaktive Dokumentation und Playground
- 🔧 **Build-Tools**: Optimierte CSS und TypeScript Builds
- 📖 **Dokumentation**: Umfassende Guides und Beispiele

## Installation

```bash
npm install @zev/design-system
```

## Schnellstart

### CSS Import

In deiner Angular `angular.json`:

```json
{
  "architect": {
    "build": {
      "options": {
        "styles": [
          "node_modules/@zev/design-system/dist/index.css",
          "src/styles.css"
        ]
      }
    }
  }
}
```

### TypeScript Tokens

```typescript
import { colors, spacing, typography } from '@zev/design-system';

const primaryColor = colors.primary.base; // '#4CAF50'
const basePadding = spacing.lg; // '20px'
```

## Komponenten

### Button

```html
<!-- Primary Button -->
<button class="zev-button zev-button--primary">Click Me</button>

<!-- Secondary Button -->
<button class="zev-button zev-button--secondary">Secondary</button>

<!-- Danger Button -->
<button class="zev-button zev-button--danger">Delete</button>

<!-- Disabled Button -->
<button class="zev-button zev-button--primary" disabled>Disabled</button>

<!-- Compact Button -->
<button class="zev-button zev-button--primary zev-button--compact">Compact</button>
```

### Navigation

```html
<nav class="zev-navbar">
  <div class="zev-navbar__container">
    <h1 class="zev-navbar__title">ZEV Management</h1>
    <ul class="zev-navbar__menu">
      <li><a href="/upload" class="zev-navbar__link zev-navbar__link--active">Upload</a></li>
      <li><a href="/einheiten" class="zev-navbar__link">Einheiten</a></li>
      <li><a href="/calculation" class="zev-navbar__link">Berechnung</a></li>
      <li><a href="/chart" class="zev-navbar__link">Diagramm</a></li>
    </ul>
  </div>
</nav>
```

## Design Tokens

### Farben

```typescript
colors.primary.base        // '#4CAF50'
colors.secondary.base      // '#2196F3'
colors.danger.base         // '#f44336'
colors.neutral.gray100     // '#f5f5f5'
```

### Spacing

```typescript
spacing.xxs   // '5px'
spacing.xs    // '8px'
spacing.sm    // '10px'
spacing.md    // '15px'
spacing.lg    // '20px'
spacing.xl    // '30px'
spacing.xxl   // '40px'
```

### Typografie

```typescript
typography.fontSize.xs      // '12px'
typography.fontSize.base    // '14px'
typography.fontSize.xl      // '24px'
typography.fontWeight.bold  // 700
```

## Entwicklung

### Projekt installieren

```bash
npm install
```

### Storybook starten

```bash
npm run storybook
```

Öffne [http://localhost:6006](http://localhost:6006) im Browser.

### Build erstellen

```bash
npm run build
```

Output in `dist/` Verzeichnis.

## Migrations-Guide

Siehe [stories/Migration.mdx](stories/Migration.mdx) für detaillierte Migrations-Anleitung.

## Projekt-Struktur

```
design-system/
├── src/
│   ├── tokens/           # Design Tokens (TS + CSS)
│   ├── foundations/      # Basis-Styles
│   ├── components/       # UI-Komponenten
│   ├── utilities/        # Utility-Klassen
│   └── index.ts/css      # Haupt-Entry-Points
├── .storybook/           # Storybook-Konfiguration
├── stories/              # Dokumentations-Stories
├── scripts/              # Build-Scripts
└── dist/                 # Build-Output
```

## Browser-Support

- Chrome (letzte 2 Versionen)
- Firefox (letzte 2 Versionen)
- Safari (letzte 2 Versionen)
- Edge (letzte 2 Versionen)

## Lizenz

ISC
