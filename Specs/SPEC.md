# Titel des Features / der Änderung

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** (Kurze Zusammenfassung)
* **Warum machen wir das:** (Business Value / Hintergrund)
* **Aktueller Stand:** Wie funktioniert es jetzt? (Falls relevant)

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?
### FR-1: Ablauf / Flow
1. User klickt auf...
2. System validiert...
3. System speichert...

### FR-2: Persistierung
*

### FR-3: Layout
*

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] Muss X können
* [ ] Darf Y nicht zulassen
* [ ] Muss Fehlermeldung Z anzeigen, wenn...

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* z.B. "muss unter 200ms antworten"

### NFR-2: Sicherheit
* z.B. "nur für Admin sichtbar" (`zev_admin` Rolle)

### NFR-3: Kompatibilität
* z.B. Datenbank/Daten müssen rückwärtskompatibel sein/können gelöscht werden

## 5. Edge Cases & Fehlerbehandlung
Typische Fälle prüfen:
* Was passiert bei leeren Eingaben / leeren Listen?
* Was passiert bei Netzwerkfehlern?
* Was passiert beim Löschen von referenzierten Daten?
* Was passiert bei gleichzeitiger Bearbeitung?
* Was passiert bei ungültigen / doppelten Werten?

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** Welche Features/Tabellen müssen bereits existieren?
* **Betroffener Code:** Welcher bestehende Code muss angepasst werden?
* **Datenmigration:** Müssen bestehende Daten migriert oder transformiert werden?

## 7. Abgrenzung / Out of Scope
* Was wird bewusst **nicht** umgesetzt?

## 8. Offene Fragen
* Punkte die noch geklärt werden müssen bevor die Umsetzung startet
