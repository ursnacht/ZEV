# Erweiterung Tarifverwaltung

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Es soll möglich sein, einen neuen Tarif durch Kopieren eines bestehenden Tarifes zu erfassen.
* **Warum machen wir das:** Benutzerfreundlichkeit
* **Aktueller Stand:** Bei einem neuen Tarif müssen alle Felder manuell erfasst werden.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?
### FR-1: Ablauf / Flow
1. Der Benutzer wählt einen Tarif, den er kopieren möchte und wählt im Kebab-Menü: "Kopieren"
2. Das System wechselt in die Maske "Neuer Tarif"
3. Die Felder enthalten die Werte des kopierten Tarifes
4. Der Benutzer kann Anpassungen machen und "Erfassen" oder "Abbrechen" klicken

### FR-2: Persistierung
* - 

### FR-3: Layout
* Kebabmenü wird mit Menüpunkt "Kopieren" erweitert
* Entsprechendes Icon verwenden

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] Kebabmenü hat weiteren Eintrag "Kopieren": Bearbeiten - **Kopieren** - Löschen
* [ ] Die Felder sind mit den Daten des kopierten Tarifes abgefüllt
* [ ] Der Benutzer kann "Erfassen" oder "Abbrechen" klicken


## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* -

### NFR-2: Sicherheit
* wie bisher

### NFR-3: Kompatibilität
* -


## 5. Edge Cases & Fehlerbehandlung
* -

## 6. Abgrenzung / Out of Scope
* Es wird keine automatische Validierung durchgeführt.

## 7. Offene Fragen
* -