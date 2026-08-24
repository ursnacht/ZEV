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
* Als Admin möchte ich beim Generieren von Rechnungen bei fehlenden/lückenhaften Tarifen die **gleiche, aussagekräftige Validierungsfehlermeldung** sehen wie bei der Tarifvalidierung in der Tarifverwaltung, statt einer generischen "Internal Server Error"-Meldung.
  * **Akzeptanzkriterien:**
    * Decken die Tarife (ZEV/VNB) den gewählten Zeitraum nicht lückenlos ab, antwortet der Endpunkt `POST /api/rechnungen/generate` mit **HTTP 400** und der Lücken-Meldung aus `TarifService.validateTarifAbdeckung` (z.B. "Für den Zeitraum fehlen gültige Tarife: ZEV-Tarif fehlt für: 01.01.2024").
    * Das Frontend zeigt diese Meldung an (Format "Fehler beim Generieren der Rechnungen: \<Meldung\>"), **nicht** "Internal Server Error".
    * Es wird kein HTTP 500 mehr zurückgegeben, wenn die Validierung fehlschlägt (der `@Transactional`-Endpunkt rollt sauber zurück; die `TarifLueckenException` wird zentral in `GlobalExceptionHandler` auf 400 abgebildet).
    * Die Meldung stammt aus derselben Quelle (`validateTarifAbdeckung`) wie die Validierung in der Tarifverwaltung – identischer Wortlaut.
    * **Mehrsprachigkeit:** Das Backend liefert die Lücken **strukturiert** (`{ error: "FEHLER_TARIF_LUECKEN", luecken: [{ tarifTyp, datum, weitere }] }`); das Frontend setzt die Meldung aus Translation-Keys zusammen (`FEHLER_TARIF_LUECKEN`, `TARIF_LUECKE_ZEV`/`TARIF_LUECKE_VNB`, `TARIF_LUECKE_WEITERE`). Datum (`dd.MM.yyyy`) und Tarif-Typ-Code sind sprachneutral. Keine hartcodierten deutschen Texte mehr.
* Als Entwickler möchte ich, dass die Rechnungsgenerierung **denselben Geldtyp** verwendet wie die Debitorenkontrolle und die Nebenkostenabrechnung, damit ein Betrag auf seinem Weg von der Berechnung über das PDF bis in die Forderung nicht umgerechnet werden muss (Entscheid vom 24.08.2026).
  * **Ausgangslage:** `RechnungDTO` und `TarifZeileDTO` führten Menge, Preis und Betrag als `double`, `Debitor` und die Nebenkostenabrechnung dagegen als `BigDecimal`. `Specs/Nebenkosten/Abrechnung.md` hielt die Abweichung ausdrücklich als Ausnahme fest ("dieses Muster darf hier nicht übernommen werden"). Der Übergang zur Forderung musste deshalb konvertieren, und `double` kann Rappenbeträge nicht exakt darstellen.
  * **Akzeptanzkriterien:**
    * `RechnungDTO.totalBetrag`, `rundung` und `endBetrag` sowie `TarifZeileDTO.menge`, `preis` und `betrag` sind `BigDecimal`. Kein Geldbetrag der Rechnungsgenerierung ist mehr `double`.
    * `RechnungService.roundTo5Rappen` nimmt und liefert `BigDecimal` und rundet mit `RoundingMode.HALF_UP` (kaufmännisch, von Null weg — damit symmetrisch für negative Beträge). Das Ergebnis trägt **zwei Nachkommastellen** und passt damit unverändert in `debitor.betrag` (`NUMERIC(10,2)`).
    * Der Endbetrag wandert **ohne Umrechnung** in die Debitorenkontrolle: `RechnungController` übergibt `rechnung.getEndBetrag()` direkt an `DebitorService.upsertFromRechnung`; das frühere `BigDecimal.valueOf(...).setScale(2, HALF_UP)` entfällt.
    * `tarif.preis` (`NUMERIC(10,5)`) wird **unverändert** in die Tarifzeile übernommen, nicht mehr über `doubleValue()`. Der Zeilenbetrag ist das exakte Produkt `menge x preis`.
    * **Die Rundungsregel bleibt unverändert:** gerundet wird ausschliesslich der Endbetrag, und zwar auf 5 Rappen (Einzahlungsschein). Zeilenbeträge bleiben ungerundet, die Differenz steht weiterhin als `rundung` auf der Rechnung.
    * Die auf der Rechnung ausgewiesenen Beträge sind unverändert — der Umbau ist rein technisch und ändert kein Ergebnis. Nachweis: die Erwartungswerte in `RechnungServiceTest` stimmen **exakt**, ohne die früher nötigen Toleranzen von 0.01.
    * Nur die kWh-Summen aus `MesswerteRepository` bleiben `double` — sie kommen so aus der Datenbank und werden unmittelbar auf ganze kWh gerundet, bevor sie als `BigDecimal` weiterlaufen.
  * **Akzeptanzkriterien PDF:**
    * `reports/rechnung.jrxml` deklariert `menge`, `preis` und `betrag` als `java.math.BigDecimal`; die Feldtypen passen zur Bean, mit der das Template gefüllt wird.
    * Die bedingte Rundungszeile prüft `getRundung().signum() != 0` statt `Math.abs(...) > 0.001`. Die Schwelle war nur nötig, weil `double` die Null nicht exakt trifft.
    * Der Betrag des Einzahlungsscheins wird ohne Umrechnung gesetzt (`bill.setAmount(rechnung.getEndBetrag())`).
    * **Ein Test füllt das Template und exportiert ein PDF**, nicht nur kompilieren: Ein Template kompiliert auch mit Feldtypen, die nicht zur Bean passen — der Fehler kommt erst beim Füllen. Der Test kompiliert dazu aus dem `.jrxml` und nicht aus `rechnung.jasper`, weil dieses Binary erst in der Maven-Phase `prepare-package` entsteht und bei `mvn test` vom vorherigen Lauf stammt.
* **0-Rechnungen (Endbetrag 0.00 CHF, z.B. kein Verbrauch im Zeitraum):** Das PDF wird erzeugt (Beleg für den Mieter), aber es wird **kein Debitor-Eintrag** angelegt (keine Forderung; `debitor.betrag` hat den Constraint `> 0`, siehe `Specs/Debitorkontrolle.md`). Die übrigen Rechnungen des Laufs sind davon nicht betroffen.

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
* **Geldtyp:** Beträge sind durchgehend `BigDecimal`, nie `double` — dieselbe Zusicherung wie in `Specs/Debitorkontrolle.md` und `Specs/Nebenkosten/Abrechnung.md`. Gerundet wird nur der Endbetrag, auf 5 Rappen mit `RoundingMode.HALF_UP`.
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
  