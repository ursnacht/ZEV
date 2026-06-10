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
     * **"Alle auswählen" selektiert NUR Konsumenten** (keine Produzenten), da Rechnungen ausschliesslich für Konsumenten generiert werden.
  4. Button "Generieren"
  5. Die Rechnungen können nach der Generierung einzeln heruntergeladen werden, solange die Seite nicht verlassen wird.
* Als Admin möchte ich oben in der Liste der generierten Rechnungen das **Total der Beträge** sehen, damit ich die Gesamtsumme der generierten Rechnungen auf einen Blick erfasse.
  * **Akzeptanzkriterien:**
    * Über der Tabelle der generierten Rechnungen wird ein Total angezeigt (Summe der Endbeträge aller generierten Rechnungen, in CHF, zwei Nachkommastellen).
    * Das Total wird nur angezeigt, wenn mindestens eine Rechnung generiert wurde.
    * Der Text "Gesamtbetrag" ist mehrsprachig (Translation-Key `GESAMTBETRAG`).
* Als Admin möchte ich, dass "Alle auswählen" auf der Rechnungen-Seite ausschliesslich Konsumenten markiert.
  * **Akzeptanzkriterien:**
    * Klick auf "Alle auswählen" selektiert alle Konsumenten, jedoch keine Produzenten.
    * Die "Alle auswählen"-Checkbox gilt als vollständig markiert, wenn alle Konsumenten selektiert sind (Produzenten werden dabei ignoriert).
    * Das Verhalten gilt nur auf der Rechnungen-Seite; andere Seiten (z.B. Grafiken/Messwerte) selektieren weiterhin alle Einheiten.
* Als Admin möchte ich, dass beim Öffnen der Seite "Rechnungen" (`/rechnungen`) das vorangehende Quartal vorselektiert ist, damit ich den häufigsten Abrechnungszeitraum nicht manuell wählen muss.
  * **Akzeptanzkriterien:**
    * Beim Öffnen von `/rechnungen` sind "Datum von" und "Datum bis" mit dem ersten bzw. letzten Tag des vorangehenden Quartals (relativ zum aktuellen Datum) vorbelegt.
    * Der entsprechende Quartal-Button im Quartal-Selektor ist beim Öffnen als aktiv markiert.
    * Jahreswechsel: Befindet sich das aktuelle Datum im Q1, wird Q4 des Vorjahres vorselektiert.
    * Der Zeitraum bleibt danach manuell änderbar (Quartal-Buttons und Datumsfelder funktionieren wie bisher).

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
* Verwende auf der Seite "Rechnungen" den gleichen Style wie auf der Seite /chart aus dem design-system

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
  