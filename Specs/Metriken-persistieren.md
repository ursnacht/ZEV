# Metriken persistieren

## 1. Ziel & Kontext
*   Die Werte der Metriken sollen in der Anwendung persistiert werden.
*   Ich möchte dies, damit die Werte der Metriken nach einem Rebuild und Restart nicht verloren sind.

## 2. Funktionale Anforderungen (Functional Requirements)
*   Jedesmal wenn eine Metrik verändert wird, wird sie in der Datenbank gespeichert.  
*   Beim Start des Backends, werden die Metriken aus den Werten in der Datenbank initialisiert.

## 3. Technische Spezifikationen (Technical Specs)
*   Neue Datenbanktabelle: metriken
*   Spalten: id, zeitstempel, name, value (JSON)
*   In der Spalte name wird der name der Metrik gespeichert
*   In der Spalte value wird der Wert der Metrik in JSON-Format gespeichert
*   Zähler-Metriken müssen als Gauge registriert werden, so dass der Wert gesetzt werden kann 

## 4. Nicht-funktionale Anforderungen
*   Beachte die generellen Anweisungen in Specs/generell.md