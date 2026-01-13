# Validierung der Tarife

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Es soll geprüft werden, dass die Tarife die Zeitachse lückenlos abdecken.
* **Warum machen wir das:** Schon beim Erfassen der Tarife möchte ich sicher sein, dass es keine Lücken in der Tarifabdeckung gibt.
* **Aktueller Stand:** Jetzt wird erst beim Generieren der Rechnungen geprüft, ob es Lücken gibt.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?
### FR-1: Ablauf / Flow
1. Beim Tarifmanagement (/tarife) kann der Benutzer auf einen neuen Button "Quartale validieren" klicken
2. Das System prüft für alle Quartale, für die es mind. einen Tarif gibt, ob das ganze Quartal mit diesem Tariftyp abgedeckt wird.
3. Platziere einen zweiten Button "Jahre validieren", der die Prüfung für das ganze Jahr macht.
4. Prüfe nur Jahre, die mind. einen Tarif haben. 
5. Die Prüfung erfolgt immer für alle Tariftypen.
6. Zeige bei Validierungsfehlern eine Fehlermeldung an, die die Lücke oder die Lücken auflistet.

### FR-2: Persistierung
* Es findet keine Persistierung statt.

### FR-3: Layout
* Platziere die neuen Buttons rechts vom Button zum Erfassen eines neuen Tarifes.
* Verwende die Farbe --color-secondary aus dem Design System
* Verwende das Schweizer Datumsformat dd.MM.yyyy

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] Die Button stehen rechts vom grünen Button zum Erfassen neuer Tarife.
* [ ] Die Validierung deckt immer ganze Quartale ab, für die mind. ein Tarif existiert.
* [ ] Es wird eine Fehlermeldung angezeigt, wenn eine Validierung fehlschlägt.


## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Die Validierung muss innerhalb einer Sekunde erfolgen.

### NFR-2: Sicherheit
* Die Validierung benötigt die gleiche Rolle wie die Erfassung eines neuen Tarifes. 

### NFR-3: Kompatibilität
* Verwende die im TarifService bereits vorhandene Methode validateTarifAbdeckung.


## 5. Edge Cases & Fehlerbehandlung
* Die angezeigt Fehlermeldung soll nicht automatisch verschwinden, kann aber durch Klicken entfernt werden.

## 6. Abgrenzung / Out of Scope
* Validierung keine Zeitbereiche (Quartale, Jahre), die keinen einzigen Tarif haben.

## 7. Offene Fragen
* Habe ich etwas vergessen?