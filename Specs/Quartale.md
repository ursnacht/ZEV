# Auswahl Quartal für Zeitbereich

## 1. Ziel & Kontext
* **Was:** Es soll eine Möglichkeit implementiert werden, die die Wahl der letzten 5 Quartale ab dem heutigen Zeitpunkt ermöglicht.
* **Warum:** So kann der Benutzer sehr schnell den Zeitbereich wählen.
* **Aktueller Stand:** Aktuell muss der Benutzer immer zwei Daten wählen.

## 2. Funktionale Anforderungen (Functional Requirements)
* **User Story:** Als Benutzer möchte ich die Möglichkeit haben eines der letzten 5 Quartal zu wählen, damit ich nicht zwei Daten für den Zeitbereich bestimmen muss.
* **Ablauf / Flow:**
  1. Der Benutzer klickt auf ein Quartal
  2. Datum von und Datum bis werden automatisch gesetzt, können aber immer noch angepasst werden
* **User Story:** Als Benutzer möchte ich, dass beim Öffnen der Seiten "Messwerte Grafik" (`/chart`) und "Solarberechnung" (`/solar-calculation`) das vorangehende Quartal vorselektiert ist, damit ich den häufigsten Auswertungszeitraum nicht manuell wählen muss.
  * **Akzeptanzkriterien:**
    * Beim Öffnen von `/chart` bzw. `/solar-calculation` sind "Datum von" und "Datum bis" mit dem ersten bzw. letzten Tag des vorangehenden Quartals (relativ zum aktuellen Datum) vorbelegt.
    * Der entsprechende Quartal-Button ist beim Öffnen als aktiv markiert.
    * Jahreswechsel: Befindet sich das aktuelle Datum im Q1, wird Q4 des Vorjahres vorselektiert.
    * Der Zeitraum bleibt danach manuell änderbar (Quartal-Buttons und Datumsfelder funktionieren wie bisher).
    * Hinweis: Das gleiche Default-Verhalten ist für `/rechnungen` in `RechnungenGenerieren.md`, für `/debitoren` in `Debitorkontrolle.md` und für `/statistik` in `Statistik.md` spezifiziert.

## 3. Technische Spezifikationen (Technical Specs)
* Die Wahl des Quartals soll über einen schönen Button erfolgen oder gibt es elegantere Möglichkeiten?
* Bezeichnung der Quartale: "Q<x>/<Jahr>" mit x als Nummer des Quartals (1-4) und Jahr vierstellig.
* Bestimmung der Quartale: das aktuelle Datum bestimmt das letzte der 5 angezeigten Quartale.

## 4. Nicht-funktionale Anforderungen
* Änderungen an Styles immer im desgin-systen vornehmen.
