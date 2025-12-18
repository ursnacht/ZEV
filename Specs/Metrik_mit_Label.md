# Metrik mit einem Label/Tag

## 1. Ziel & Kontext
* Die Metrik "zev.messdaten.upload.letzter_zeitpunkt" soll mit einem tag (label) `einheit` ergänzt werden
* Mit diesem Use Case soll in Grafana sichtbar gemacht werden, für welche Einheit der letzte Upload gemacht wurde.

## 2. Funktionale Anforderungen (FR)

### FR-1: Metrik mit Einheit-Label
* Die bestehende Metrik `zev.messdaten.upload.letzter_zeitpunkt` wird um ein tag `einheit` erweitert
* Der Wert des Tags enthält den Namen der Einheit: Feld `name` aus der Einheit-Entity, für die zuletzt verwendete Einheit
* Beispiel: `zev_messdaten_upload_letzter_zeitpunkt{einheit="1. Stock li"} = 1734432000`

### FR-2: Persistierung
* Es soll nur das letzte Tag zusammen mit dem Zeitstempel gespeichert werden und nicht alle unterschiedlichen Tags
* Beim Anwendungsstart wird das Tag zusammen mit dem Zeitstempel aus der Datenbank geladen und in der Metrik abgelegt

### FR-3: Grafana Dashboard
* Das bestehende Dashboard zeigt zum letzten Upload-Zeitpunkt zusätzlich die Einheit an

### FR-4: Grafana Alerting
* Momentan noch kein Alerting. Folgt später.


## 3. Technische Spezifikationen (TS)

### TS-1: Grafana Dashboard
* Das Grafana Dashboard anpassen


## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Label-Sanitisierung
* Einheit-Namen werden für Metriken sanitisiert (Sonderzeichen entfernen).

### NFR-2: Kompatibilität
* Die globale Metrik ohne Label muss aus Prometheus gelöscht werden. Anleitung dazu erstellen.
