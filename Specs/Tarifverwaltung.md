# Tarifverwaltung

## 1. Ziel & Kontext
* **Was soll erreicht werden?** Die Tarife können sich von Quartal zu Quartal ändern. Dies muss berücksichtigt werden.
* **Warum machen wir das?** Beim Erstellen der Rechnungen müssen die Tarife verwendet werden, die für den Zeitraum der Rechnung gültig sind.
* **Aktueller Stand:** Die Tarife sind ohne Gültigkeitsbereich in application.yml spezifiziert.

## 2. Funktionale Anforderungen (Functional Requirements)
* **User Story:** Als Admin möchte ich zu den Tarifen auch die Gültigkeit (von, bis) angeben können, damit beim Erstellen der Rechnungen die richtigen Tarife verwendet werden.
* **Ablauf / Flow:**
  1. Der User kann im Menü die Seite "Tarifverwaltung" aufrufen (im Menü unterhalb /einheiten).
  2. In der Tarifverwaltung können bestehende Tarife bearbeitet oder gelöscht und neue Tarife hinzugefügt werden.
  3. Beim Erstellen von Rechnungen werden die für den Zeitbereich der Rechnung gültigen Tarife verwendet.

## 3. Technische Spezifikationen (Technical Specs)
* **DB-Änderungen:**
  * Das System speichert die Tarife in der Datenbank in einer neuen Tabelle "tarife".
  * Die Tabelle enthält folgende Spalten:
    * ID aus einer Sequenz
    * Bezeichnung: maximale Länge 30 Zeichen, Beispiele: "vZEV PV Tarif" oder "Strombezug EWB"
    * Tariftyp
      * "ZEV" für Strombezug aus dem ZEV, bisher rechnung.tarif.zev (messwerte.zev_calculated)
      * "VNB" für Strombezug vom Verteilnetzbetreiber, bisher rechnung.tarif.ewb (messwerte.total - messwerte.zev_calculated)
    * Tarif: Genauigkeit 5 Nachkommastellen
    * gueltig_von: Datum
    * gueltig_bis: Datum
  * Alle Spalten sind Pflicht 
* Rechnungen 
  * Für den Zeitbereich einer Rechnung (i.d.R. ein Quartal) können mehrere Tarife gültig sein, da diese monatlich angepasst werden.
  * Für die Berechnung des Totals muss für jeden Zeitpunkt (messwerte.zeit) der gültige Tarif verwendet werden.
  * Auf der Rechnung können somit mehrere Zeilen für den Strombezug vom ZEV und vom VNB nötig sein.
  * Rundungen der Mengen auf den Rechnungen beibehalten
* Die aktuellen Spezifikationen der beiden Tarife rechnung.tarif aus application.yml entfernen.

## 4. Nicht-funktionale Anforderungen
* Sicherheit: die Tarifverwaltung ist nur mit der Rolle zev_admin aufrufbar
* Optimierte Berechnung der Rechnungsbeträge: Gehe für die Berechnung der Kosten nicht Zeile für Zeile der messwerte.zeit vor, sondern bilde Teil-Zeitbereiche gemäss Gültigkeit der Tarife.

## 5. Edge Cases & Fehlerbehandlung
* Es muss für den gesamten Zeitbereich der Rechnung ein gültiger Tarif vorhanden sein. Falls dies nicht der Fall ist, soll eine Fehlermeldung angezeigt werden. 
