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

## 3. Technische Spezifikationen (Technical Specs)
* Die Wahl des Quartals soll über einen schönen Button erfolgen oder gibt es elegantere Möglichkeiten?
* Bezeichnung der Quartale: "Q<x>/<Jahr>" mit x als Nummer des Quartals (1-4) und Jahr vierstellig.
* Bestimmung der Quartale: das aktuelle Datum bestimmt das letzte der 5 angezeigten Quartale.

## 4. Nicht-funktionale Anforderungen
* Änderungen an Styles immer im desgin-systen vornehmen.
