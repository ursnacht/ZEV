# Generierung Quartalsrechnungen

## 1. Ziel & Kontext
* Generierung von Quartalsrechnungen mit QR-Code für die Konsumenten
* Grund: ZEV-Interne Verrechnung der Strombezüge aller Konsumenten

## 2. Funktionale Anforderungen (Functional Requirements)
* Als Admin möchte ich die QR-Rechnungen für die Konsumenten generieren
* **Ablauf / Flow:**
  1. Wahl "Rechnungen" im Menü
  2. Eingabe des Zeitraumes analog zur Statistikseite mit Berechnung von "Datum bis"
  3. Wahl der Konsumenten mit Checkboxen analog zur Seite "Grafiken Messwerte" inkl. "alle auswählen"
  4. Button "Generieren"
  5. Die Rechnungen können nach der Generierung einzeln heruntergeladen werden, solange die Seite nicht verlassen wird.

## 3. Technische Spezifikationen (Technical Specs)
* Erweiterung der Einheit
  * mit zwei neuen Properties:
    * Mietername
    * Messpunkt (z.B. CH1012501234500000000011000006457)
  * Anpassung Datenbank
  * Anpassung Erfassung Einheit
* Generiere eine Rechnung als Microsoft Word Dokument gemäss Specs/StromRechnungAllgemein.pdf
  * Rechnungsnummer: => nicht notwendig
  * "Datum" => Datum der Rechnungserstellung (heute)
  * rechnung.zahlungsfrist aus application.yml
  * Zeitraum: => gewählter Zeitraum "Datum von" und "Datum bis"
  * rechnung.steller aus application.yml
  * Rechnungsadresse: 
    * Mietername aus Einheit
    * rechnung.adresse.strasse aus application.yml
    * rechnung.adresse.plz + rechnung.adresse.ort aus application.yml
  * Messpunkt aus Einheit
  * Name Einheit
  * Gültig => nicht notwendig
  * rechnung.tarif.zev.bezeichnung aus application.yml
  * rechnung.tarif.zev.preis aus application.yml
  * Total ZEV = Summe von zev_calculated der Einheit im gewählten Zeitraum
  * rechnung.tarif.ewb.bezeichnung aus application.yml
  * rechnung.tarif.ewb.preis aus application.yml
  * Total EWB = (Summe von total der Einheit im Zeitraum) - (Total ZEV im Zeitraum)
  * Seite 2 => nicht notwendig
  * Empfangsschein, Zahlteil mit QR-Code (Adresstyp "S" verwenden)

## 4. Nicht-funktionale Anforderungen
* Die generierten Rechnungen werden nur temporär und nicht in der Datenbank gespeichert
* Alle neuen Texte mehrsprachig und mit flyway in DB-Tabelle translation aufnehmen
* Sicherheit: Rolle zev_admin notwendig

## 5. Referenzen
* Specs/generell.md
* Specs/StromRechnungAllgemein.pdf

## 6. application.yml
```yaml
rechnung:
  zahlungsfrist: 30 Tage
  steller:
    name: Urs Nacht
    strasse: Hangstrasse 14a
    plz: 3044
    ort: Innerberg
  adresse:
    strasse: Mutachstrasse 13
    plz: 3008
    ort: Bern
  tarif:
    zev: 
      bezeichnung: vZEV PV Tarif
      preis: 0.2 CHF / kWh
    ewb:
      bezeichnung: Strombezug EWB
      preis: 0.34192 CHF / kWh
```
  