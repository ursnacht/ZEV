# Statistikseite

## 1. Ziel & Kontext
*   Es soll eine Statistikseite realisiert werden.
*   Mit der neuen Seite kann ich mir immer einen Überblick über die Messdaten und Verteilungen verschaffen.
*   Aktuell muss ich in die Datenbank schauen oder die Messwerte anzeigen, wenn ich sehen will, welche Daten und Verteilungen vorhanden sind.

## 2. Funktionale Anforderungen (Functional Requirements)
* Gib einen Überblick über die Daten:
  * "Messwerte vorhanden bis" mit Angabe des letzten Datums, für das Daten vorhanden sind
  * Angabe ob für alle Consumer und Producer Daten vorliegen oder Lücken vorhanden sind 
* Als Benutzer möchte ich pro Monat folgendes sehen:
    * Angabe Monat
    * Angabe ob für jeden Tag im Monat Messdaten vorhanden sind, d.h. ist der Monat **vollständig**?
    * Zeige die Summen aller Consumer und Producer: 
      * Summe von total
      * Summe von zev
      * Summe von zev_calculated
    * Vergleiche, ob Summe zev aller Producer gleich der Summe aller Consumer ist
    * Vergleiche, ob Summe zev_calculated aller Producer gleich der Summe aller Consumer ist
    * Vergleiche, ob Summe zev gleich der Summe zev_calculated ist
    * Liste die Tage auf, für die die Summen nicht gleich sind
    * Vielleicht hast du eine gute Idee, das graphisch darzustellen
*   Es soll ein Filter vorhanden sein, über den ich den Datumsbereich wählen kann, der angezeigt werden soll (analog zur Seite Messwerte)
    * als Default soll der vorherige Monat gesetzt sein
*   Die Seite soll im Menu wählbar sein
*   Alle Texte mehrsprachig machen und in die Datenbank aufnehmen

## 3. Technische Spezifikationen (Technical Specs)
*   Design System verwenden
*   Design an bisherige Seiten anlehnen

## 4. Nicht-funktionale Anforderungen
*   Sicherheit: Die Seite kann mit der Rolle "zev" aufgerufen werden 
*   Erstelle Tests wo sinnvoll und hilfreich
*   Logging

## 5. Verschiedenes
*   Beachte die Anweisungen in den Dateien generell.md und AutomatisierteTests.md
