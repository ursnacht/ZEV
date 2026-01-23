# Mieterverwaltung

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Die Mieter sollen unabhängig von der Einheit verwaltet und einer Einheit zugeordnet werden können.
* **Warum machen wir das:** Bei einem Mieterwechsel innerhalb eines Quartals müssen beide Mieter einer Einheit eine Quartalsrechnung erhalten.
* **Aktueller Stand:** Da keine Mietdauer angegeben werden kann, ist nur ein Mieter pro Quartal in einer Einheit möglich.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?
### FR-1: Ablauf / Flow
1. Neue Seite aus dem Menü erreichbar: Mieterverwaltung
2. Das System erlaubt die Verwaltung von Mietern mit folgenden Attributen:
   * Name
   * Strasse
   * PLZ
   * Ort
   * Mietbeginn (Datum), Pflichtattribut
   * Mietende (Datum), optionales Attribut
   * Einheit (Auswahl mit einer Dropdown-Liste). Speichert nur die ID der Einheit.
3. Das System kann für mehrere Mieter einer Einheit während eines Quartals (Mieterwechsel innerhalb des Quartals) eine Rechnung erstellen
4. In der Rechnung wird maximal die Zeitspanne (Mietbeinn, Mietende) berücksichtigt, in der der Mieter auch tatsächlich Mieter war.
5. Wenn das Mietende fehlt, gilt die Miete bis zum Quartalsende. 

### FR-2: Persistierung
* Das System speichert die Mieter in der Datenbank
* Das Attribut "mietername" wird nicht mehr benötigt und soll aus der Einheit entfernt werden. 

### FR-3: Layout
* Vorlage für Layout: Tarifverwaltung

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] Die Mieterverwaltung ist aus dem Menü mit der Rolle "zev_admin" aufrufbar
* [ ] Mieter können erfasst, bearbeitet und gelöscht werden (Kebabmenü)
* [ ] Falls "Mietende" erfasst ist, muss dieses nach dem "Mietbeginn" liegen
* [ ] Die Zeitspannen von mehreren Mietern der gleichen Einheiten dürfen sich nicht überschneiden. 
* [ ] Nur der Mieter mit dem jüngsten Mietbeginn einer Einheit darf kein Mietende haben (aktueller Mieter). Die andern Mieter müssen ein Mietend haben.
* [ ] Bei einem Mieterwechsel innerhalb eines Quartals, muss für jeden Mieter eine Quartalsrechnung erstellt werden.


## 3. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* -

### NFR-2: Sicherheit
* Für die Mieterverwaltung ist die Rolle "zev_admin" notwendig. 

### NFR-3: Kompatibilität
* -

## 4. Edge Cases & Fehlerbehandlung
* -

## 5. Abgrenzung / Out of Scope
* Leerstände sind möglich, d.h. es kann Lücken geben

## 6. Offene Fragen
* -