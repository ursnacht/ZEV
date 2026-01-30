# Umsetzungsplan: Startseite

## Zusammenfassung

Implementierung einer neuen Startseite, die nach dem Login angezeigt wird. Die Seite zeigt ein passendes Bild (Sonnen-Illustration passend zum ZEV-Thema) und die Navigation ist standardmässig aufgeklappt, um dem Benutzer einen schnellen Überblick über die verfügbaren Funktionen zu geben.

---

## Betroffene Komponenten

| Typ | Datei | Aktion |
|-----|-------|--------|
| Angular Component | `frontend-service/src/app/components/startseite/startseite.component.ts` | Neu |
| Angular Component | `frontend-service/src/app/components/startseite/startseite.component.html` | Neu |
| Angular Component | `frontend-service/src/app/components/startseite/startseite.component.css` | Neu |
| Angular Routing | `frontend-service/src/app/app.routes.ts` | Änderung |
| Angular Navigation | `frontend-service/src/app/components/navigation/navigation.component.ts` | Änderung |
| Angular Navigation | `frontend-service/src/app/components/navigation/navigation.component.html` | Änderung |
| Asset | `frontend-service/src/assets/solar-home.svg` | Neu |
| Flyway Migration | `backend-service/src/main/resources/db/migration/V[XX]__Add_Startseite_Translations.sql` | Neu |

---

## Phasen-Tabelle

| Status | Phase | Beschreibung |
|--------|-------|--------------|
| [x] | 1. Asset erstellen | SVG-Bild für Startseite: `frontend-service/src/assets/solar-home.svg` |
| [x] | 2. Startseite-Komponente | Neue Komponente: `frontend-service/src/app/components/startseite/` |
| [x] | 3. Navigation anpassen | Logik für automatisches Aufklappen auf Startseite hinzufügen |
| [x] | 4. Routing anpassen | Redirect von `/` auf `/startseite` ändern |
| [x] | 5. Navigation-Link | Home-Icon als Link zur Startseite in Navigation hinzufügen |
| [x] | 6. Übersetzungen | Flyway-Migration für neue Übersetzungskeys |

---

## Detaillierte Umsetzung

### Phase 1: Asset erstellen

**Datei:** `frontend-service/src/assets/solar-home.svg`

Ein dekoratives SVG-Bild, das zum ZEV-Thema (Solarenergie/Eigenverbrauch) passt. Beispielsweise:
- Sonne mit Strahlen
- Haus mit Solarpanels
- Stilisiertes Energie-Symbol

Das Bild sollte die Projektfarben verwenden:
- Grün (#4CAF50) - Hauptfarbe
- Blau (#2196F3) - Akzentfarbe
- Gelb (#FFEB3B) - Sonnenfarbe

### Phase 2: Startseite-Komponente

**Dateien:**
- `frontend-service/src/app/components/startseite/startseite.component.ts`
- `frontend-service/src/app/components/startseite/startseite.component.html`
- `frontend-service/src/app/components/startseite/startseite.component.css`

```typescript
// startseite.component.ts
@Component({
  selector: 'app-startseite',
  standalone: true,
  imports: [TranslatePipe, IconComponent],
  templateUrl: './startseite.component.html',
  styleUrls: ['./startseite.component.css']
})
export class StartseiteComponent implements OnInit {
  ngOnInit(): void {
    // Navigation aufklappen via Service oder Event
  }
}
```

```html
<!-- startseite.component.html -->
<div class="zev-container startseite">
  <div class="startseite__hero">
    <img src="assets/solar-home.svg" alt="ZEV Solar" class="startseite__image">
    <h1 class="startseite__title">
      <app-icon name="sun" size="lg"></app-icon>
      {{ 'WILLKOMMEN_ZEV' | translate }}
    </h1>
    <p class="startseite__subtitle">{{ 'STARTSEITE_BESCHREIBUNG' | translate }}</p>
  </div>
</div>
```

### Phase 3: Navigation anpassen

**Datei:** `frontend-service/src/app/components/navigation/navigation.component.ts`

Zwei Optionen:

**Option A: Router-basiert (empfohlen)**
```typescript
import { Router, NavigationEnd } from '@angular/router';

export class NavigationComponent implements OnInit {
  constructor(private router: Router) {}

  ngOnInit() {
    // Bei Navigation zur Startseite: Menu öffnen
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd)
    ).subscribe((event: NavigationEnd) => {
      if (event.url === '/' || event.url === '/startseite') {
        this.isMenuOpen = true;
      }
    });

    // Initial prüfen
    if (this.router.url === '/' || this.router.url === '/startseite') {
      this.isMenuOpen = true;
    }
  }
}
```

**Option B: Service-basiert**
Ein NavigationService, der den Menu-Status steuert.

### Phase 4: Routing anpassen

**Datei:** `frontend-service/src/app/app.routes.ts`

```typescript
export const routes: Routes = [
  { path: '', redirectTo: '/startseite', pathMatch: 'full' },
  { path: 'startseite', component: StartseiteComponent, canActivate: [AuthGuard], data: { roles: ['zev', 'zev_admin'] } },
  // ... bestehende Routes
];
```

### Phase 5: Navigation-Link

**Datei:** `frontend-service/src/app/components/navigation/navigation.component.html`

Home-Icon als ersten Link in der Navigation hinzufügen:

```html
<li>
  <a routerLink="/startseite" routerLinkActive="zev-navbar__link--active" class="zev-navbar__link"
    (click)="closeMenu()">
    <app-icon name="home"></app-icon>
    {{ 'STARTSEITE' | translate }}
  </a>
</li>
```

### Phase 6: Übersetzungen

**Datei:** `backend-service/src/main/resources/db/migration/V[XX]__Add_Startseite_Translations.sql`

```sql
INSERT INTO zev.translation (key, deutsch, englisch) VALUES
('STARTSEITE', 'Startseite', 'Home'),
('WILLKOMMEN_ZEV', 'Willkommen bei ZEV Management', 'Welcome to ZEV Management'),
('STARTSEITE_BESCHREIBUNG', 'Verwalten Sie Ihre Solarstrom-Gemeinschaft effizient und fair.', 'Manage your solar energy community efficiently and fairly.')
ON CONFLICT (key) DO NOTHING;
```

---

## Validierungen

### Frontend-Validierungen
- Keine spezifischen Validierungen erforderlich (reine Anzeigeseite)

### Backend-Validierungen
- Keine (kein Backend-Code erforderlich)

---

## Offene Punkte / Annahmen

1. **Annahme:** Die Navigation wird nur auf der Startseite automatisch aufgeklappt, nicht auf anderen Seiten
2. **Annahme:** Das SVG-Bild wird als statisches Asset eingebunden (kein dynamischer Inhalt)
3. **Annahme:** Die Startseite ist für alle authentifizierten Benutzer zugänglich (Rollen: `zev`, `zev_admin`)
4. **Entschieden:** Ein Home-Icon wird als erster Link in der Navigation hinzugefügt
5. **Entschieden:** Das Bild wird als SVG-Illustration erstellt (passend zum ZEV-Thema)
6. **Annahme:** Das SVG-Bild ist responsiv und passt sich der Bildschirmgrösse an
