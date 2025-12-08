## Statistikseite

### 1. Ziel & Kontext
* Es soll eine Statistikseite realisiert werden.
* Mit der neuen Seite kann ich mir einen Überblick über die Messdaten und Verteilungen verschaffen.
* Aktuell muss ich in die Datenbank schauen oder die Messwerte anzeigen, wenn ich sehen will, welche Daten und Verteilungen vorhanden sind.

### 2. Funktionale Anforderungen (Functional Requirements)
* Es soll ein Filter vorhanden sein, über den ich den Datumsbereich wählen kann, der angezeigt werden soll (analog zur Seite Messwerte)
    * als Default soll der vorherige Monat gesetzt sein
* Gib einen Überblick über die Daten:
  * "Messwerte vorhanden bis" mit Angabe des letzten Datums, für das Daten vorhanden sind
  * Angabe, ob für alle Consumer und Producer Daten bis zum Datum "Messwerte vorhanden bis" vorliegen oder Lücken vorhanden sind, d.h. Daten von Einheiten fehlen oder Tage fehlen 
* Als Benutzer möchte ich pro Monat folgendes sehen:
  * Angabe Zeitbereich
  * Angabe ob für jeden Tag im Bereich Messdaten vorhanden sind, d.h. gibt es für alle Einheiten Daten?
  * Zeige die folgenden Summen an: 
    * Summe A: Summe total aller Producer (Produktion)
    * Summe B: Summe total aller Consumer (Verbrauch Total)
    * Summe C: Summe zev aller Producer (Verbrauch Anteil ZEV)
    * Summe D: Summe zev aller Consumer (Verbrauch Anteil ZEV)
    * Summe E: Summe zev_calculated aller Consumer (Verbrauch Anteil ZEV)
  * Vergleiche, ob Summe C = Summe D
  * Vergleiche, ob Summe C = Summe E
  * Vergleiche, ob Summe D = Summe E
  * Liste die Tage auf, für die mind. eine Summe nicht gleich sind
* Vielleicht hast du eine gute Idee, das graphisch darzustellen
* Die Seite soll im Menu wählbar sein
* Alle Texte mehrsprachig machen und in die Datenbank aufnehmen

### 3. Technische Spezifikationen (Technical Specs)
* Verwende das Design System
* Neue Designs in das Design System aufnehmen 
* Design an bisherige Seiten anlehnen

### 4. Nicht-funktionale Anforderungen
* Sicherheit: Die Seite kann mit der Rolle "zev" aufgerufen werden 
* Sinnvolles Logging
* Erstelle sinnvolle und hilfreiche Tests erst auf Anweisung

### 5. Verschiedenes
* Beachte die Anweisungen in den Dateien Specs/generell.md und Specs/AutomatisierteTests.md
