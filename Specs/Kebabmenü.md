# Kebabmenü

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Anzeige eines Kebabmenüs zur Auswahl von Aktionen anstelle von Buttons.
* **Warum machen wir das:** Die Darstellung von Listen kann so kompakter erfolgen.
* **Aktueller Stand:** In Listen werden Buttons zum Bearbeiten und Löschen von Einträgen angezeigt.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?
### FR-1: Ablauf / Flow
1. Der User klickt auf das Kebabmenü (⋮)
2. Es erscheint ein kleines Menü mit den Befehlen "Bearbeiten" und "Löschen"
3. Das Menü schliesst sich bei:
   - Klick auf einen Menüpunkt
   - Klick ausserhalb des Menüs
   - Drücken der ESC-Taste

### FR-2: Persistierung
* keine

### FR-3: Layout
* Das kleine Menü soll ähnlich dargestellt (inkl. Hover) werden wie das Hauptmenü, nur etwas kompakter
* Menü öffnet sich unterhalb des Kebab-Icons, rechtsbündig ausgerichtet
* Die Listen können so auch kompakter angezeigt werden
* Passe die folgenden Listen an:
  * Einheitenverwaltung
  * Tarifverwaltung
  * Übersetzungen

### FR-4: Komponenten-Struktur
* Wiederverwendbare Angular-Komponente `KebabMenuComponent`
* CSS-Styles im Design-System unter `components/kebab-menu/`
* Inputs: Liste von Menüpunkten mit Label und Action
* Output: Event wenn ein Menüpunkt geklickt wird

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] Beim Klicken des Kebabmenüs öffnet sich ein kleines Menü mit den Befehlen "Bearbeiten" und "Löschen"
* [ ] Durch Klicken von "Bearbeiten" kann der Eintrag bearbeitet werden
* [ ] Durch Klicken von "Löschen" wird der Eintrag nach einer Kontrollfrage gelöscht
* [ ] Durch Klicken neben dem Menü verschwindet es wieder
* [ ] Durch Tippen der ESC-Taste verschwindet das Menü
* [ ] Nur ein Kebabmenü ist gleichzeitig geöffnet
* [ ] Einheitenverwaltung verwendet Kebabmenü statt Buttons
* [ ] Tarifverwaltung verwendet Kebabmenü statt Buttons
* [ ] Übersetzungen verwendet Kebabmenü statt Buttons

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Beim Klicken auf das Kebabmenü darf es keine Verzögerung geben

### NFR-2: Sicherheit
* Es gelten die gleichen Sicherheitsmerkmale wie bisher

### NFR-3: Kompatibilität
* Funktioniert in allen modernen Browsern (Chrome, Firefox, Safari, Edge)

## 5. Edge Cases & Fehlerbehandlung
* Wenn ein Menü bereits offen ist und ein anderes Kebabmenü geklickt wird, schliesst sich das erste

## 6. Abgrenzung / Out of Scope
* Keine Touch-Gesten (Swipe)
* Keine Keyboard-Navigation innerhalb des Menüs (ausser ESC zum Schliessen)

## 7. Offene Fragen
* -
