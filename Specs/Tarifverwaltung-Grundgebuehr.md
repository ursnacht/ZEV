# Tarifverwaltung – Grundgebühr (Monatlicher Preis pro Stromzähler)

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Einführung eines neuen Tariftyps „Grundgebühr" (monatlicher Festpreis pro Stromzähler), der zusätzlich zu den verbrauchsabhängigen ZEV- und VNB-Tarifen auf den Rechnungen ausgewiesen und verrechnet wird.
* **Warum machen wir das:** In der Praxis verlangen Energieversorger neben dem Arbeitspreis (CHF/kWh) auch eine Grundgebühr pro Zähler und Monat. Diese muss sowohl für Konsumenten als auch für Produzenten abgerechnet werden.
* **Aktueller Stand:** Es existieren nur die Tariftypen `ZEV` (Arbeitspreis ZEV-Anteil) und `VNB` (Arbeitspreis Netzbezug), die pro kWh berechnet werden. Produzenten erhalten aktuell keine Rechnungen.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Neuer Tariftyp
* Der `TarifTyp`-Enum erhält den neuen Wert `GRUNDGEBUEHR`.
* In der Tarifverwaltung kann ein Tarif vom Typ `GRUNDGEBUEHR` erfasst werden.
* Der `preis`-Wert eines `GRUNDGEBUEHR`-Tarifes bezeichnet den Festpreis **pro Stromzähler und Monat** (CHF/Monat).
* `bezeichnung`, `preis`, `gueltig_von` und `gueltig_bis` werden wie bei den anderen Tariftypen erfasst; Genauigkeit `preis` 5 Nachkommastellen.

### FR-2: Berechnung der Grundgebühr auf Rechnungen
1. Beim Generieren einer Rechnung werden für den Rechnungszeitraum alle gültigen `GRUNDGEBUEHR`-Tarife ermittelt.
2. Für jeden `GRUNDGEBUEHR`-Tarif wird die Anzahl der vollständigen **Kalendermonate** innerhalb des Überschneidungszeitraums von Tarifgültigkeit und Rechnungszeitraum gezählt.
3. Der Rechnungsbetrag für diesen Tarifblock = Anzahl Monate × Preis.
4. Die Grundgebühr wird auf **jede Einheit** (CONSUMER und PRODUCER) angewendet.

### FR-3: Rechnungen für Produzenten
* Produzenten (`EinheitTyp.PRODUCER`) erhalten ebenfalls Rechnungen, die ausschliesslich `GRUNDGEBUEHR`-Positionen enthalten.
* Im Rechnungsformular (Frontend) können Produzenten analog zu Konsumenten ausgewählt werden (separate Checkbox-Gruppe oder gemeinsame Liste mit Typanzeige).
* Produzenten haben keinen Mieter; die Rechnung wird auf die Einheit selbst ausgestellt (kein Mieterblock auf dem PDF).

### FR-4: Darstellung auf der Rechnung (PDF)
* `GRUNDGEBUEHR`-Zeilen werden nach den `ZEV`- und `VNB`-Zeilen aufgeführt.
* Spalten: Bezeichnung | Zeitraum (von–bis) | Menge (Anzahl Monate, Einheit „Monate") | Preis (CHF/Monat) | Betrag (CHF)
* Das Gesamttotal (`endBetrag`) enthält alle drei Tariftypen inkl. Grundgebühr.

### FR-5: Tarifverwaltung UI
* In der Tariferfassung ist `GRUNDGEBUEHR` als Option im Dropdown für Tariftyp vorhanden.
* Bestehende Tarifzeilen in der Liste zeigen den Tariftyp (Spalte bereits vorhanden).

### FR-6: Persistierung
* Kein neues Datenbankschema nötig; die bestehende `tarif`-Tabelle wird verwendet.
* Neue Flyway-Migration zum Hinzufügen des Enum-Wertes (sofern als DB-Enum gespeichert; bei Varchar-Spalte entfällt dies).

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] In der Tarifverwaltung kann ein Tarif mit Typ „Grundgebühr" erfasst, bearbeitet und gelöscht werden.
* [ ] In der Dropdown-Liste der Tariftypen erscheint der Eintrag „Grundgebühr".
* [ ] Beim Generieren von Rechnungen wird die Grundgebühr für Konsumenten und Produzenten berechnet.
* [ ] Die Anzahl Monate wird korrekt berechnet (z. B. 01.01.–31.03. = 3 Monate).
* [ ] Läuft ein `GRUNDGEBUEHR`-Tarif mitten in einer Rechnungsperiode ab, werden zwei separate Zeilen erzeugt (analog ZEV/VNB).
* [ ] Produzenten erscheinen im Rechnungsformular und können ausgewählt werden.
* [ ] Für Produzenten wird eine Rechnung mit nur `GRUNDGEBUEHR`-Positionen generiert (keine ZEV/VNB-Zeilen).
* [ ] Der `endBetrag` auf der Rechnung enthält die Grundgebühr.
* [ ] Fehlt ein `GRUNDGEBUEHR`-Tarif für den Rechnungszeitraum, wird keine Grundgebühr berechnet (kein Fehler, da der Typ optional ist – siehe Offene Fragen).
* [ ] Alle neuen UI-Texte kommen aus dem `TranslationService` (keine hardcodierten Strings).

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Die Berechnung der Grundgebühr darf die Gesamtlaufzeit der Rechnungsgenerierung nicht merklich verlängern (keine zusätzlichen DB-Queries pro Zeile; Tarife werden einmalig geladen).

### NFR-2: Sicherheit
* Tarifverwaltung (inkl. `GRUNDGEBUEHR`) ist nur mit Rolle `zev_admin` zugänglich.
* Rechnungsgenerierung für Produzenten ebenfalls nur mit Rolle `zev_admin`.

### NFR-3: Kompatibilität
* Bestehende Rechnungen für Konsumenten ohne `GRUNDGEBUEHR`-Tarife müssen weiterhin korrekt berechnet werden.
* Das `TarifZeileDTO` wird erweitert, um die Einheit der Menge (kWh vs. Monate) darzustellen, ohne bestehende Felder zu ändern.
* Die Tarifvalidierung (`validateTarifAbdeckung`) wird um den Typ `GRUNDGEBUEHR` erweitert (separater Validierungsaufruf).

### NFR-4: Multi-Tenancy
* Alle `GRUNDGEBUEHR`-Tarife tragen `org_id`; Hibernate-Filter wird angewendet.

## 5. Edge Cases & Fehlerbehandlung
* **Kein `GRUNDGEBUEHR`-Tarif vorhanden:** Keine Fehlermeldung; Grundgebühr wird mit 0 berechnet (optional – siehe Offene Fragen).
* **Teilmonat (Mieter zieht Mitte Monat ein/aus):** Nur vollständige Kalendermonate werden berechnet. Ein Monat gilt als vollständig, wenn der erste und letzte Tag des Kalendermonats im effektiven Zeitraum liegen.
* **Rechnungszeitraum < 1 Monat:** Grundgebühr = 0 Monate = 0 CHF; Zeile wird nicht ausgegeben.
* **Produzent ohne Messwerte:** Rechnung wird trotzdem mit `GRUNDGEBUEHR`-Positionen generiert (keine ZEV/VNB-Zeilen = 0 kWh).
* **Netzwerkfehler beim PDF-Download:** Bestehende Fehlerbehandlung greift (keine Spezialbehandlung nötig).
* **Produzent mit Mieter:** Nicht vorgesehen (Produzenten haben keine Mieter); kein Mieterblock auf der Produzenten-Rechnung.

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** `Tarifverwaltung.md` (Tarif-CRUD, `TarifService`), `RechnungenGenerieren.md` (Rechnungsgenerierung mit Jasper-PDF)
* **Betroffener Code:**
  * `TarifTyp.java` – neuer Enum-Wert `GRUNDGEBUEHR`
  * `TarifService.java` – Laden von `GRUNDGEBUEHR`-Tarifen; ggf. Anpassung `validateTarifAbdeckung`
  * `RechnungService.java` – `berechneRechnung`/`berechneTarifZeilen` um Grundgebühr-Logik erweitern; `berechneRechnungen` muss PRODUCER-Einheiten verarbeiten
  * `RechnungDTO.java` / `TarifZeileDTO.java` – ggf. Feld für Mengeneinheit ergänzen
  * `rechnung.jrxml` – neue Zeilen-Darstellung für `GRUNDGEBUEHR` (Spalte „Monate" statt kWh)
  * `TarifFormComponent` – Dropdown-Option `GRUNDGEBUEHR`
  * `RechnungenComponent` – Produzenten-Auswahl ergänzen
  * `TarifValidierung` (`validateTarifAbdeckung`) – optionale Erweiterung für `GRUNDGEBUEHR`
* **Datenmigration:** Keine bestehenden Daten müssen migriert werden; neuer Tariftyp wird nur für neue Tarif-Einträge genutzt.

## 7. Abgrenzung / Out of Scope
* Automatische Aufteilung der Grundgebühr bei Mieterwechsel innerhalb eines Monats (wird nur auf volle Monate gerechnet).
* Separate Grundgebühr-Berechnung nach Einheitsgrösse oder Leistung (immer Festpreis).
* Anzeige der Grundgebühr auf der Statistikseite.
* Historisierung von Rechnungen in der Datenbank (bereits out of scope gemäss `RechnungenGenerieren.md`).
* Grundgebühr für Produzenten auf bestehenden Konsumenten-Rechnungen (Produzenten erhalten eigene, separate Rechnungen).

## 8. Offene Fragen
* **Pflicht oder Optional:** Muss zwingend ein `GRUNDGEBUEHR`-Tarif vorhanden sein, damit Rechnungen generiert werden können, oder ist der Typ optional (kein Fehler wenn kein Tarif vorhanden)? --> Tariftyp ist optional
* **Ganze oder partielle Monate:** Wird bei Teilmonaten (Mieter-Eintritt/-Austritt mid-month) anteilig oder auf volle Monate gerundet? (Annahme in dieser Spec: volle Monate.) --> volle Monate
* **Produzenten im Rechnungsformular:** Werden Produzenten in der gleichen Auswahlliste wie Konsumenten angezeigt (mit Typ-Label), oder gibt es eine separate Checkbox-Gruppe? --> in der gleichen Liste mit Typ-Label
* **Tarifvalidierung für `GRUNDGEBUEHR`:** Soll der neue Tariftyp in der bestehenden Quartals-/Jahresvalidierung mitgeprüft werden (Pflichtabdeckung), oder ist er optional? --> Tariftyp ist optional
* **PDF-Layout:** Kann der `GRUNDGEBUEHR`-Block im gleichen Zeilenlayout wie ZEV/VNB dargestellt werden (Bezeichnung | Zeitraum | Monate | CHF/Monat | Betrag), oder ist ein eigener Abschnitt gewünscht? --> KEIN eigener Abschnitt
