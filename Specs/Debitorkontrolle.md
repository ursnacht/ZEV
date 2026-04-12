# Debitorkontrolle

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Eine persistente Übersicht der ausgestellten Rechnungen (Debitoren) mit Möglichkeit zur manuellen Pflege, insbesondere zur Erfassung des Zahldatums.
* **Warum machen wir das:** Aktuell werden Rechnungen nur temporär generiert und nicht gespeichert. Für eine vollständige Debitorenkontrolle müssen die generierten Rechnungen persistent gespeichert und nachverfolgt werden können (offene vs. bezahlte Rechnungen).
* **Aktueller Stand:** Rechnungen werden generiert und können heruntergeladen werden, aber nicht persistent gespeichert. Nach dem Verlassen der Seite sind die Daten verloren.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Ablauf / Flow
1. Beim Generieren von Rechnungen (POST `/api/rechnungen/generate`) erstellt das System automatisch je einen Debitor-Eintrag pro generierter Rechnung **mit Mieter** (Produzenten und Leerstand werden übersprungen).
2. Der Benutzer öffnet die Seite "Debitorkontrolle" aus dem Menü.
3. Der Benutzer wählt ein Quartal mit dem Quartal-Selektor (analog Rechnungserstellung, `QuarterSelectorComponent`).
4. Das System zeigt eine Liste aller Debitor-Einträge für das gewählte Quartal an.
5. Der Benutzer kann einzelne Einträge über das Kebab-Menü bearbeiten oder löschen.
6. Beim Bearbeiten kann insbesondere das Zahldatum erfasst werden.
7. Neue Einträge können manuell über den Button "Neu erfassen" hinzugefügt werden.

### FR-2: Persistierung
* Neue Datenbanktabelle `debitor` mit folgenden Spalten:
  * `id` (Sequenz, PK)
  * `mieter_id` (FK → `zev.mieter.id`, Pflicht, `ON DELETE CASCADE`)
  * `betrag` (numeric(10,2), CHF, Pflicht)
  * `datum_von` (date, Pflicht)
  * `datum_bis` (date, Pflicht)
  * `zahldatum` (date, optional)
  * `org_id` (FK → `zev.organisation.id`, Pflicht, Multi-Tenancy)
* Unique-Constraint: `UNIQUE (mieter_id, datum_von, org_id)` – ermöglicht idempotentes Upsert beim Generieren
* `ON DELETE CASCADE` auf `mieter_id`: Wird ein Mieter gelöscht, werden zugehörige Debitor-Einträge automatisch mitgelöscht
* Der GET-Endpunkt liefert im `DebitorDTO` die Felder `mieterName` und `einheitName` per JOIN auf `mieter` und `einheit` (keine Denormalisierung)
* Flyway-Migration für die neue Tabelle
* REST-Endpunkte unter `/api/debitoren` (`zev_admin`-Rolle):
  * `GET /api/debitoren?von=...&bis=...` – Liste gefiltert nach Quartal
  * `POST /api/debitoren` – Manuell erstellen
  * `PUT /api/debitoren/{id}` – Bearbeiten
  * `DELETE /api/debitoren/{id}` – Löschen

### FR-3: Automatische Erstellung beim Rechnungsgenerieren
* Nach erfolgreicher PDF-Generierung einer Rechnung mit Mieter erstellt das System automatisch einen Debitor-Eintrag.
* **Nur Rechnungen mit Mieter** werden als Debitor erfasst. Rechnungen für Produzenten oder leerstehende Einheiten (kein Mieter) werden ignoriert.
* Die Felder werden aus der generierten Rechnung übernommen: `mieter_id`, `betrag`, `datum_von`, `datum_bis`.
* Das `zahldatum` bleibt initial leer.
* Wird für denselben `(mieter_id, datum_von, org_id)` ein Eintrag erneut generiert, wird der bestehende Eintrag aktualisiert (Upsert), sofern `zahldatum` noch nicht gesetzt ist.
* Die Erstellung erfolgt innerhalb derselben Transaktion wie die Rechnungsgenerierung; schlägt die Generierung fehl, wird kein Eintrag erstellt.

### FR-4: Layout
* Neue Route `/debitoren` mit Komponente `DebitorkontrolleComponent`
* Neuer Menüeintrag "Debitorkontrolle" (unter "Rechnungen")
* **Seite:**
  1. Quartal-Selektor oben (`QuarterSelectorComponent` + manuelle Von/Bis-Datumfelder, analog `/rechnungen`)
  2. Button "Neu erfassen" (öffnet Formular)
  3. Tabelle mit Debitor-Einträgen (analog Mieterverwaltung):
     * Mieter (Name + Einheitname, z.B. "Max Muster (EG links)")
     * Betrag (CHF, rechtsbündig)
     * Datum von (Schweizer Format `dd.MM.yyyy`)
     * Datum bis (Schweizer Format)
     * Zahldatum (Schweizer Format, "-" wenn leer)
     * Status ("Bezahlt" / "Offen", abgeleitet vom Zahldatum)
     * Aktionen (Kebab-Menü: Bearbeiten, Löschen)
  4. Leerstate-Meldung wenn keine Einträge vorhanden
* **Formular** (Inline, analog Mieterverwaltung):
  * Mieter-Name (Auswahl Mieter mit Dropdown-Liste mit Name Mieter und Einheit, Pflicht)
  * Einheit-Name (read-only, wird abgefüllt wenn Mieter gewählt wird)
  * Betrag CHF (Zahl, Pflicht, > 0)
  * Datum von (Datum, Pflicht)
  * Datum bis (Datum, Pflicht)
  * Zahldatum (Datum, optional)

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] Beim Generieren von Rechnungen wird für jede Rechnung **mit Mieter** automatisch ein Debitor-Eintrag persistiert (Produzenten und Leerstand werden übersprungen)
* [ ] Die Seite "Debitorkontrolle" ist aus dem Menü mit der Rolle `zev_admin` erreichbar
* [ ] Die Quartal-Auswahl filtert die angezeigte Liste korrekt (Einträge mit `datum_von` im gewählten Quartal)
* [ ] Die Tabelle zeigt Mieter mit Name und Einheitname in einer Spalte an
* [ ] Einträge ohne Zahldatum werden als "Offen" angezeigt, Einträge mit Zahldatum als "Bezahlt"
* [ ] Debitor-Einträge können manuell über das Formular erstellt werden
* [ ] Debitor-Einträge können bearbeitet werden (insbesondere Zahldatum kann nacherfasst werden)
* [ ] Debitor-Einträge können mit Bestätigung gelöscht werden
* [ ] `betrag` ist Pflichtfeld (positiver Wert)
* [ ] `datum_von` und `datum_bis` sind Pflichtfelder; `datum_von` muss ≤ `datum_bis` sein
* [ ] `zahldatum` ist optional; wenn angegeben, muss es ≥ `datum_bis` sein (keine Vorauszahlung)
* [ ] Löschen eines Debitor-Eintrags erfordert eine Bestätigung (Confirm-Dialog)
* [ ] Bei leerer Liste wird eine Hinweismeldung angezeigt ("Keine Debitoren für diesen Zeitraum")
* [ ] Netzwerkfehler werden als Fehlermeldung angezeigt (bleibt bis dismissed)
* [ ] Alle UI-Texte kommen via `TranslationService` (i18n)
* [ ] Multi-Tenancy: Jeder Mandant sieht ausschliesslich seine eigenen Debitor-Einträge
* [ ] `org_id` wird serverseitig aus dem JWT gesetzt, nicht vom Client übergeben
* [ ] Die Darstellung verwendet das Design System

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Die API-Abfrage der Debitoren pro Quartal soll in unter 500ms antworten

### NFR-2: Sicherheit
* Die Debitorkontrolle ist ausschliesslich mit der Rolle `zev_admin` aufrufbar (`@PreAuthorize`)
* `org_id` wird serverseitig via `OrganizationContextService` gesetzt

### NFR-3: Kompatibilität
* Der bestehende Endpunkt `POST /api/rechnungen/generate` wird um die Debitor-Persistierung erweitert; bestehende Response-Struktur bleibt unverändert
* Neue Tabelle `debitor` ist unabhängig von den temporären In-Memory-Rechnungsdaten (`RechnungStorageService`)

## 5. Edge Cases & Fehlerbehandlung
* **Rechnungsgenerierung schlägt fehl:** Kein Debitor-Eintrag wird erstellt (Transaktionssicherheit)
* **Mehrfache Rechnungsgenerierung für dieselbe Periode:** Bestehende Debitor-Einträge werden aktualisiert, fehlende erstellt
* **Mieter wurde nach Rechnungsgenerierung gelöscht:** Debitoreintrag ebenfalls löschen
* **Manuell erstellter Eintrag:** `mieter_id` ist Pflicht
* **Leere Liste für gewähltes Quartal:** Meldung "Keine Debitoren für diesen Zeitraum" anzeigen
* **Netzwerkfehler:** Fehlermeldung anzeigen (Error-Message bleibt bis dismissed, Success auto-dismiss 5s)
* **Löschen:** Bestätigungsdialog vor dem Löschen
* **Ungültiger Betrag (0 oder negativ):** Validierungsfehler im Formular

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:**
  * `Mieterverwaltung` (Mieter-Entität mit `org_id`)
  * `RechnungenGenerieren` (`RechnungController`, `RechnungService`, `RechnungDTO`)
  * `Quartale` (`QuarterSelectorComponent`)
  * `Multi-Tenancy` (`OrganizationContextService`, `HibernateFilterService`)
* **Betroffener Code:**
  * `RechnungController.generateRechnungen()` – nach erfolgreicher PDF-Generierung Debitor-Einträge persistieren
  * `RechnungService` – liefert `endBetrag`, `mieterId`, `mieterName`, `einheitName`
  * Navigation/Menü (`NavigationComponent`) – neuer Menüeintrag "Debitorkontrolle"
  * Angular Routing – neue Route `/debitoren`
* **Neue Dateien (Backend):** `Debitor.java`, `DebitorRepository.java`, `DebitorService.java`, `DebitorController.java`, `DebitorDTO.java`
* **Neue Dateien (Frontend):** `debitor.model.ts`, `debitor.service.ts`, `debitorkontrolle/` (List + Form Komponente)
* **Datenmigration:** Keine (neue Tabelle, Flyway-Migration V55+)

## 7. Abgrenzung / Out of Scope
* Kein automatisches Mahnwesen oder Mahnworkflow
* Kein CSV/Excel-Export der Debitorenliste
* Keine Buchhaltungsintegration (z.B. FIBU-Export)
* Kein expliziter Status-Enum (z.B. "Teilzahlung", "Storno") – Status wird nur aus Zahldatum abgeleitet

## 8. Offene Fragen
* Keine offenen Fragen.
