# Metrik mit einem Label

## 1. Ziel & Kontext
* Metriken dienen der Überwachung / Monitoring der Anwendung.
* Mit Hilfe der Metriken können wir Fehlverhalten der Benutzer und der Anwendung feststellen und mit Grafana Alarmierung auslösen.
* Die Metrik "zev.messdaten.upload.letzter_zeitpunkt" soll mit einem Label als Name der Einheit ergänzt werden.
* **Ziel:** Pro Einheit soll sichtbar sein, wann der letzte Messdaten-Upload stattfand, um fehlende Uploads frühzeitig zu erkennen.

## 2. Funktionale Anforderungen (Functional Requirements)

### FR-1: Metrik mit Einheit-Label
* Die bestehende Metrik `zev.messdaten.upload.letzter_zeitpunkt` wird um ein Label `einheit` erweitert.
* Das Label enthält den Namen der Einheit (Feld `name` aus der Einheit-Entity).
* Beispiel: `zev_messdaten_upload_letzter_zeitpunkt{einheit="Wohnung 1"} = 1734432000`

### FR-2: Persistierung
* Der letzte Upload-Zeitpunkt pro Einheit wird in der Datenbank persistiert.
* Beim Anwendungsstart werden die Metriken aus der Datenbank geladen.

### FR-3: Grafana Dashboard
* Das bestehende Dashboard zeigt den letzten Upload-Zeitpunkt pro Einheit an.
* Eine Tabellen-Ansicht ermöglicht den Überblick über alle Einheiten.

### FR-4: Grafana Alerting
* Ein Alert wird ausgelöst, wenn eine Einheit länger als 24 Stunden keine Messdaten hochgeladen hat.
* Der Alert enthält den Namen der betroffenen Einheit.
* Severity: Warning

## 3. Technische Spezifikationen (Technical Specs)

### Metrik-Definition (Micrometer)
```java
Gauge.builder("zev.messdaten.upload.letzter_zeitpunkt", ...)
    .tag("einheit", einheitName)
    .description("Unix-Timestamp des letzten Messdaten-Uploads")
    .register(meterRegistry);
```

### Prometheus-Format
```
zev_messdaten_upload_letzter_zeitpunkt{einheit="Wohnung 1"} 1734432000
zev_messdaten_upload_letzter_zeitpunkt{einheit="Wohnung 2"} 1734431000
zev_messdaten_upload_letzter_zeitpunkt{einheit="Solar Panel"} 1734430000
```

### Betroffene Komponenten
* `MetricsService.java` - Metrik-Registrierung und -Aktualisierung
* `MesswerteController.java` - Aufruf mit Einheit-Name
* `MetrikRepository.java` - Persistierung
* `zev-dashboard.json` - Grafana Dashboard
* Neue Datei für Alerting-Regeln

## 4. Nicht-funktionale Anforderungen

### NFR-1: Kardinalität
* Die Anzahl der Einheiten sollte unter 100 bleiben, um Prometheus-Performance nicht zu beeinträchtigen.

### NFR-2: Label-Sanitisierung
* Einheit-Namen werden für Metriken sanitisiert (Sonderzeichen entfernen).

### NFR-3: Abwärtskompatibilität
* Die globale Metrik ohne Label bleibt erhalten für bestehende Dashboards.

## 5. Testing

### Unit-Tests
* Test für `recordMessdatenUpload(String einheitName)`
* Test für mehrere Einheiten gleichzeitig
* Test für Persistierung und Laden beim Start

### Integration-Tests
* Upload durchführen und Metrik über Actuator-Endpoint prüfen

### Manueller Test
* Grafana-Dashboard und Alerting im Docker-Compose-Stack validieren

## 6. Umsetzungsplan

Siehe [Metrik_mit_Label_Umsetzungsplan.md](./Metrik_mit_Label_Umsetzungsplan.md)
