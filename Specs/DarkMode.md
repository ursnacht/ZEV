# DarkMode

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Die Anwendung soll einen Dark Mode unterstützen, der vom Benutzer manuell ein- und ausgeschaltet werden kann.
* **Warum machen wir das:** Verbesserte Lesbarkeit bei schlechten Lichtverhältnissen, Schonung der Augen und Akkuleistung bei OLED-Displays.
* **Aktueller Stand:** Die Anwendung verwendet ausschliesslich einen Light Mode mit fest definierten Farb-Tokens in `design-system/src/tokens/tokens.css` und `tokens.ts`. Es existieren keine Dark-Mode-Variablen.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Umschalten Dark/Light Mode
1. Der Benutzer öffnet das Hamburger-Menü in der Navigation.
2. Oberhalb des Sprachumschalters (Sprachwahl) erscheint ein Toggle-Button für den Dark/Light Mode.
3. Der Benutzer klickt auf den Toggle.
4. Die gesamte Anwendung wechselt sofort in den Dark bzw. Light Mode.
5. Die Präferenz wird im `localStorage` gespeichert und beim nächsten Seitenaufruf wiederhergestellt.

### FR-2: System-Präferenz berücksichtigen
* Beim ersten Aufruf der Anwendung (kein gespeicherter Wert) wird die Systempräferenz des Betriebssystems (`prefers-color-scheme: dark`) als Standard übernommen.

### FR-3: Token-Implementierung
* Im Design System werden für alle bestehenden semantischen Farbvariablen Dark-Mode-Gegenstücke definiert.
* Die Umschaltung erfolgt über das Setzen der CSS-Klasse `dark-mode` auf dem `<html>`-Element oder via `data-theme="dark"` Attribut.
* Das Design System stellt einen `prefers-color-scheme`-Media-Query-Block bereit, der automatisch greift, wenn kein expliziter Override gesetzt ist.

### FR-4: Toggle-Button in Navigation
* Der Toggle zeigt das passende Icon:
  * Light Mode aktiv → Mond-Icon (`moon`), um Dark Mode anzubieten
  * Dark Mode aktiv → Sonnen-Icon (`sun`), um Light Mode anzubieten
* Der Button verwendet `zev-navbar__link`-Klasse (konsistent mit Sprachumschalter).
* Label via `TranslatePipe`: `DARK_MODE` / `LIGHT_MODE`.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] Im Hamburger-Menü erscheint ein Dark-Mode-Toggle oberhalb des Sprachumschalters
* [ ] Ein Klick auf den Toggle wechselt die gesamte Anwendung zwischen Dark und Light Mode
* [ ] Im Dark Mode sind alle UI-Komponenten (Navbar, Cards, Tabellen, Buttons, Formulare, Messages) korrekt eingefärbt und lesbar
* [ ] Die gewählte Präferenz bleibt nach einem Seiten-Reload erhalten (localStorage)
* [ ] Beim ersten Aufruf ohne gespeicherte Präferenz wird die Systempräferenz (`prefers-color-scheme`) verwendet
* [ ] Das Toggle-Icon wechselt korrekt (Mond im Light Mode, Sonne im Dark Mode)
* [ ] Der Toggle-Button ist mit `TranslatePipe` übersetzt (Deutsch + Englisch)
* [ ] Alle Design-System-Komponenten sind im Dark Mode getestet und korrekt dargestellt (kein weisser Blitz beim Laden)

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Der Theme-Wechsel erfolgt ohne Seiten-Reload, rein über CSS-Custom-Properties.
* Kein sichtbares Flackern (FOUC) beim initialen Laden: Theme wird vor dem ersten Paint gesetzt (Script im `<head>`).

### NFR-2: Sicherheit
* Keine Authentifizierungsanforderung – der Toggle ist für alle eingeloggten Benutzer (`zev` und `zev_admin`) verfügbar.
* Die Präferenz wird nur im `localStorage` des Browsers gespeichert, keine Serverübertragung.

### NFR-3: Kompatibilität
* Die bestehende Light-Mode-Darstellung darf sich nicht verändern.
* Alle bestehenden Komponenten-Styles bleiben unverändert; Dark-Mode-Overrides werden additiv über `[data-theme="dark"]`-Selektor hinzugefügt.

## 5. Edge Cases & Fehlerbehandlung
* **localStorage nicht verfügbar** (z.B. Private Browsing ohne Zugriff): Fallback auf `prefers-color-scheme`, keine Fehlermeldung.
* **Ungültiger Wert in localStorage:** Ignorieren und Systempräferenz verwenden.
* **Druckansicht:** Dark Mode via `@media print` deaktivieren (Drucken immer im Light Mode).
* **PDF-Export / JasperReports:** Nicht betroffen – PDFs werden serverseitig generiert und ignorieren das Theme.
* **Charts (Chart.js):** Chart-Farben müssen im Dark Mode ebenfalls angepasst werden (Achsen, Gridlines, Labels).

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** Keine – rein additives Feature.
* **Betroffener Code:**
  * `design-system/src/tokens/tokens.css` – Dark-Mode-Token-Block ergänzen
  * `design-system/src/tokens/tokens.ts` – Dark-Mode-Token-Objekt ergänzen
  * `design-system/src/components/*/` – Alle Komponenten-CSS-Dateien prüfen, ob Farben über Tokens referenziert werden (nicht hardcodiert)
  * `frontend-service/src/app/components/navigation/navigation.component.html` – Toggle-Button einfügen (oberhalb Sprachumschalter)
  * `frontend-service/src/app/components/navigation/navigation.component.ts` – Toggle-Logik, localStorage-Persistierung
  * `frontend-service/src/index.html` – Inline-Script im `<head>` zur FOUC-Prävention
  * Flyway-Migration: Neue Übersetzungskeys `DARK_MODE`, `LIGHT_MODE`
* **Datenmigration:** Keine.

## 7. Abgrenzung / Out of Scope
* Keine benutzerspezifische Speicherung der Präferenz in der Datenbank (kein Backend-Eingriff).
* Keine automatische Umschaltung basierend auf Tageszeit.
* Keine individuellen Farbschemata / Themes über Dark/Light hinaus.
* Dark-Mode-Anpassungen für externe Bibliotheken (z.B. Keycloak Login-Seite) sind nicht Bestandteil.

## 8. Offene Fragen
* Soll das Dark-Mode-Icon im Hamburger-Menü auch ausserhalb des geöffneten Menüs sichtbar sein (z.B. direkt in der Navbar neben dem Hamburger-Icon)? --> gute Idee, so umsetzen
* Welche konkreten Dark-Mode-Farbwerte sollen verwendet werden (eigene Palette oder Bootstrap/Material-Referenz)? --> Bootstrap/Material-Referenz
* Sollen Charts (Chart.js) automatisch auf Dark-Mode-Farben umgestellt werden, oder bleibt das explizit Out of Scope? --> Charts bleiben out of scope
