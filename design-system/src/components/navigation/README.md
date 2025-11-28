# Navigation Component

Navigation-Bar für die ZEV-Anwendung.

## Verwendung

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

## Klassen

- `zev-navbar`: Haupt-Container
- `zev-navbar__container`: Innerer Container mit max-width
- `zev-navbar__title`: Anwendungstitel
- `zev-navbar__menu`: Navigations-Liste
- `zev-navbar__link`: Navigations-Link
- `zev-navbar__link--active`: Aktiver Link (blau hervorgehoben)

## States

- `:hover`: Halbtransparenter weißer Hintergrund
- `--active`: Blauer Hintergrund für aktive Links
