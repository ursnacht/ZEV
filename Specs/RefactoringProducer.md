# Refactoring Producer und Solarstromverteilung

## 1. Ziel & Kontext
* Beim Importieren der Messwerte sollen nicht automatisch die absoluten Werte gespeichert werden.
* Üblicherweise sind die Werte der Producer negativ. Diese sollen so in der Datenbank gespeichert werden. 
* Die Steuerung der Solaranlage benötigt ebenfalls Strom. Diese Werte sind ebenfalls vom Typ "Producer", aber positiv.
* Bevor der Solarstrom verteilt werden soll, muss zuerst der Verbrauch der Solarsteuerung "abgezogen" werden.
* D.h. wenn die Werte aller Producer summiert werden, bevor sie verteilt werden, wird der Verbrauch der Solarsteuerung automatisch berücksichtigt.

## 2. Funktionale Anforderungen (Functional Requirements)
* Speicherung der Werte der Producer wie sie in der Datei stehen. Also nicht absolute Werte speichern.
* Summe aller Produzenten bilden, bevor dieser Solarstrom verteilt wird.
* Anpassung der beiden Verteilalgorithmen, so dass die nun negativen Werte der Produzenten korrekt berücksichtig werden.
* Statistikseite anpassen, so dass die Vergleiche immer noch gemacht werden können.

## 3. Technische Spezifikationen (Technical Specs)
* Tests entsprechend korrigieren.
