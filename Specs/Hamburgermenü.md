# Hamburgermenü

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Ein einheitlich gestaltetes Menü, in dem die täglich gebrauchten Seiten vorne stehen und die selten gebrauchten Admin-Seiten in einem zugeklappten Untermenü „Admin“ liegen.
* **Warum machen wir das:** Das Menü ist mit über 15 Einträgen lang geworden. Solarverteilung, Systemmeldungen, Einstellungen, Übersetzungen und Lizenzen braucht man selten; zugeklappt verkürzen sie das Menü, ohne zu verschwinden.
* **Ausgangslage:** Alle Einträge stehen flach untereinander. Die meisten Einträge zeigt das Menü unabhängig von den Permissions; erst die Route weist ab.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Einheitliche Einträge
1. Alle Einträge – auch Dark Mode, Sprachwahl und Logout – sind gleich gestaltet und werden beim Überfahren mit der Maus gleich hervorgehoben (`.zev-navbar__link`).

### FR-2: Untermenü „Admin“
1. Das Menü enthält einen Eintrag **„Admin“** (Feather-Icon `tool`) mit den Untereinträgen, in dieser Reihenfolge:

   | Untereintrag     | Route                | Permission (aus `app.routes.ts`) | Icon        |
   |------------------|----------------------|----------------------------------|-------------|
   | Solarverteilung  | `/solar-calculation` | `messwerte:write`                | `zap`       |
   | Systemmeldungen  | `/systemmeldungen`   | `systemmeldungen:read`           | `file-text` |
   | Einstellungen    | `/einstellungen`     | `einstellungen:write`            | `settings`  |
   | Übersetzungen    | `/translations`      | `translations:manage`            | `globe`     |
   | Lizenzen         | `/lizenzen`          | `lizenzen:read`                  | `shield`    |

   Diese Einträge stehen nicht mehr in der ersten Ebene. „Admin“ steht nach „Einheiten“, vor Dark Mode, Sprachwahl und Logout.
2. **Sichtbarkeit:** Ein Untereintrag erscheint nur mit der Permission seiner Route. „Admin“ erscheint nur, wenn **mindestens ein** Untereintrag erlaubt ist.
   > Die Permissions liest das Menü aus der Routen-Konfiguration, damit Menü und Route nicht unterschiedlich entscheiden. Da `zev_user` alle `*:read`-Permissions hat, sieht jeder angemeldete Benutzer „Admin“ mit mindestens Systemmeldungen und Lizenzen.
3. **Zugeklappt:** „Admin“ ist zugeklappt – beim Laden, nach jeder Navigation und bei jedem Öffnen des Menüs. Ein Klick auf „Admin“ klappt auf bzw. zu; „Admin“ selbst navigiert nicht. Nach einem Klick auf einen Eintrag ausserhalb von „Admin“ ist es also zugeklappt, wie nach einem Neuladen.
   > Auch ein Klick auf die bereits aktive Seite klappt zu: Er löst keine Navigation aus, deshalb gilt die Regel auch beim Öffnen des Menüs. (Hintergrund: Anhang A.3)
4. **Ausnahme:** Liegt die aktuelle Seite im Untermenü, ist es aufgeklappt – sonst wäre der aktive Eintrag nicht zu sehen. Ein Klick auf einen Untereintrag lässt „Admin“ deshalb offen.
5. **Zugeklappte Einträge sind nicht erreichbar:** weder sichtbar noch per Tastatur fokussierbar.

### Datenmodell & Persistierung
* n/a – reine Darstellung, der Aufklapp-Zustand wird nicht gespeichert.
* **Multi-Tenancy (`org_id`):** n/a – keine Daten.
* **Flyway-Migration:** `V184__Add_Admin_Menu_Translations.sql` (nur Übersetzung).

### API-Endpunkte
* n/a – keine neuen Endpunkte.

### Validierungen
* n/a – keine Eingaben.

### Übersetzungen (i18n)

| Key     | Deutsch | Englisch |
|---------|---------|----------|
| `ADMIN` | Admin   | Admin    |

Die Untereinträge behalten ihre bestehenden Keys (`SOLAR_DISTRIBUTION`, `SYSTEMMELDUNGEN`, `EINSTELLUNGEN`, `TRANSLATION_EDITOR`, `LIZENZEN`).

### Layout & Navigation
* **Menüeintrag:** `frontend-service/src/app/components/navigation/navigation.component.html`
* **Design-System-Klassen:** Untermenü aus `design-system/src/components/navigation/navbar.css`: `.zev-navbar__submenu-toggle` (+ `--open`), `.zev-navbar__submenu-label`, `.zev-navbar__submenu-icon`, `.zev-navbar__submenu` (+ `--open`). Kein Komponenten-CSS.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

### Einheitliche Einträge
* [x] Dark Mode, Sprachwahl und Logout tragen dieselbe Klasse `.zev-navbar__link` wie die übrigen Einträge (Nachweis: navigation.component.html:149, :155, :161)

### Untermenü „Admin“
* [x] Mit allen Permissions zeigt „Admin“ die fünf Untereinträge in der Reihenfolge Solarverteilung, Systemmeldungen, Einstellungen, Übersetzungen, Lizenzen; keiner davon steht in der ersten Ebene (Test: navigation.component.spec.ts › Admin-Untermenü › should list the five admin entries in order inside the submenu)
* [x] Ohne die Permission eines Untereintrags fehlt dieser Untereintrag, die übrigen bleiben (Test: navigation.component.spec.ts › Admin-Untermenü › should show only the entries the user is permitted to open; navigation.component.spec.ts › Admin-Untermenü › should take the permissions of the entries from the routes)
* [x] Ohne jede der fünf Permissions fehlt „Admin“ ganz (Test: navigation.component.spec.ts › Admin-Untermenü › should hide Admin when no entry is permitted)
* [x] „Admin“ ist nach dem Laden zugeklappt; ein Klick klappt auf, ein zweiter zu (Test: navigation.component.spec.ts › Admin-Untermenü › should start collapsed and toggle on click)
* [x] Liegt die aufgerufene Seite im Untermenü, ist es aufgeklappt (Test: navigation.component.spec.ts › Admin-Untermenü › should open the submenu when the current page lies in it; navigation.component.spec.ts › Admin-Untermenü › should keep the submenu closed on a page outside of it)
* [x] Nach einem Klick auf einen Eintrag ausserhalb von „Admin“ ist „Admin“ zugeklappt; nach einem Klick auf einen Untereintrag bleibt es offen (Test: navigation.component.spec.ts › Admin-Untermenü › should collapse Admin after navigating to a page outside of it; navigation.component.spec.ts › Admin-Untermenü › should keep Admin open after navigating to a page inside of it)
* [x] Ein von Hand aufgeklapptes „Admin“ ist beim nächsten Öffnen des Menüs wieder zugeklappt, auch ohne Navigation (Test: navigation.component.spec.ts › Admin-Untermenü › should collapse a manually opened Admin when the menu is opened again)
* [x] Die Untereinträge navigieren über das Menü (E2E: systemmeldungen.spec.ts › Systemmeldungen - Seite und Filter › should be reachable from the menu and render the filter row; einstellungen.spec.ts › Einstellungen - Navigation and Display › should navigate to Einstellungen page and display form)
* [x] Ein zugeklapptes Untermenü ist unsichtbar (`visibility: hidden`), seine Einträge also nicht fokussierbar (Nachweis: design-system/src/components/navigation/navbar.css:186-203, `.zev-navbar__submenu`)

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* n/a – reine Darstellung.

### NFR-2: Sicherheit
* Das Ausblenden ist **Bedienkomfort, keine Sicherheit**: Geschützt bleiben die Routen (`AuthGuard`, `data.permissions`) und die Endpunkte (`@PreAuthorize`).
* **Fachrollen:** siehe `Specs/Berechtigungen.md`.

### NFR-3: Kompatibilität
* Routen und URLs bleiben unverändert; Lesezeichen funktionieren weiter.

## 5. Edge Cases & Fehlerbehandlung
* Benutzer ohne jede der fünf Permissions → kein „Admin“ (theoretisch; `zev_user` hat `lizenzen:read`).
* Aufruf einer Admin-Seite per URL oder Neuladen → Untermenü beim Öffnen des Menüs aufgeklappt (FR-2.4).
* Klick auf die bereits aktive Seite ausserhalb von „Admin“ → keine Navigation; „Admin“ ist beim nächsten Öffnen des Menüs zugeklappt (FR-2.3).
* Rollen ändern sich in der Sitzung → wirkt erst nach erneutem Anmelden (Rollen stehen im Token, `PermissionDirective`).

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** Untermenü-Variante im Design System (`navbar.css`), `PermissionDirective`, Permissions der Routen in `app.routes.ts`.
* **Betroffener Code:** `navigation.component.html/.ts`, `icons.ts` (Icon `tool`), `navbar.css`, E2E-Helfer `oeffneUntermenue`/`navigateViaMenu` in `tests/helpers.ts`.
* **Datenmigration:** keine.

## 7. Abgrenzung / Out of Scope
* Weitere Untermenüs oder eine Umgruppierung der übrigen Einträge.
* Merken des Aufklapp-Zustands über das Schliessen des Menüs hinaus.
* Datenbank-Ansicht und Feature-Flags erscheinen weiterhin nicht im Menü.

## 8. Offene Fragen
* keine

## Anhang A – Entscheidungen und Verlauf

### A.1 Ursprüngliche Fassung (FR-1)
Die Spec bestand zuerst nur aus der Anforderung, dass Sprachwahl und Logout beim Überfahren wie die übrigen Einträge hervorgehoben werden („Das Menu soll einheitlich gestaltet werden“). Sie ist als FR-1 erhalten; die Spec wurde für das Admin-Untermenü auf die Struktur von `SPEC.md` gebracht.

### A.2 Warum ein Untermenü, obwohl das Nebenkosten-Untermenü entfernt wurde (FR-2, 17.10.2026)
Das Untermenü „Nebenkosten“ wurde entfernt, weil es die einzige echte Seite hinter einem zusätzlichen Klick verbarg. Hier ist der zusätzliche Klick gewollt: Die fünf Admin-Seiten werden selten gebraucht, und das Menü wird um vier Einträge kürzer. Die Untermenü-Mechanik (Komponente, Design System, E2E-Helfer) war dafür aufbewahrt worden.

### A.3 Zuklappen nach einem Klick ausserhalb (FR-2.3, 17.10.2026)
Vorher galt: „Admin“ ist standardmässig zugeklappt und klappt auf, wenn die aufgerufene Seite darin liegt. Einmal aufgeklappt, blieb es aber über jede Navigation hinweg offen – auch nach einem Klick auf einen Eintrag ausserhalb. Zugeklappt war es erst nach einem Neuladen. Gewünscht ist, dass sich ein Klick wie ein Neuladen verhält. Der Zustand ergibt sich deshalb jetzt bei jeder Navigation und bei jedem Öffnen des Menüs aus der aktuellen Seite.
