# Spaltenbreite von Tabellen veränderbar

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** In den Tabellen im Frontend der Einheiten, Tarif und Übersetzungen soll die Spaltenbreite durch den User verändert werden können. 
* **Warum machen wir das:** Bessere User Experience.
* **Aktueller Stand:** Die Spaltenbreite kann nicht verändert werden.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?
### FR-1: Ablauf / Flow
1. Durch Ziehen mit der Maus (drag) eines senkrechten Striches im Titel von Tabellen, kann die Spaltenbreite verändert werden.
2. Beim Loslassen des Mausbuttons (drop) verbleibt die Spalte in der entsprechenden Breite.
3. Die andern Spalten ändern ihre Breite nicht, wenn ich eine Spaltenbreite verändere.

### FR-2: Persistierung
* Die Spaltenbreite soll nicht gespeichert werden. 
* Beim Wiederöffnen oder Refreshen der Seite, wird die Tabelle wieder im ursprünglichen Zustand dargestellt.

### FR-3: Layout
* Der Strich zum Ziehen steht ganz rechts in der Spalte, d.h. unmittelbar links des Titels der nachfolgenden Spalte.
* Der Strich ist senkrecht, grau und fein und wird immer angezeigt und nicht erst, wenn die Maus darüberfährt.
* Der Mauszeiger soll während des Ziehens entsprechend dargestellt werden.
* Die letzte Spalte (ganz rechts) hat keinen Strich und kann nicht verändert werden.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] Der Benutzer kann mit der Maus den Strich ziehen und die Spaltenbreite verändern.
* [ ] Beim Loslassen der Maustaste behält die Spalte die gewählte Breite.


## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Beim Ziehen des Striches darf die Benutzeroberfläche nicht flackern oder ruckeln.

### NFR-2: Sicherheit
* Alle Benutzer mit Zugriff auf die Tabellen dürfen die Spaltenbreite verändern.  

### NFR-3: Kompatibilität
* -


## 5. Edge Cases & Fehlerbehandlung
* Die Spalte darf nicht schmaler als die Breite des Titels gemacht werden können.


## 6. Abgrenzung / Out of Scope
* Bei einem Doppelklick auf den Strich soll nichts passieren.

## 7. Offene Fragen
* Gibt es ein besseres Icon als der senkrechte Strich zum Ziehen?