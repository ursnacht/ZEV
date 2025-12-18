# Umsetzungsplan: Metrik mit Label/Tag

## Zusammenfassung

Die Metrik `zev.messdaten.upload.letzter_zeitpunkt` wird um ein Label `einheit` erweitert, das den Namen der zuletzt verwendeten Einheit enthält. Es wird nur **ein** Label-Wert gespeichert (die letzte Einheit), nicht pro Einheit separate Metriken.

## Akzeptanzkriterien

- [ ] Nach einem Messdaten-Upload zeigt Prometheus die Metrik mit dem korrekten Einheit-Label
- [ ] Nach Neustart der Anwendung wird das Label aus der Datenbank geladen
- [ ] Grafana zeigt den Zeitpunkt und den Einheit-Namen an
- [ ] Einheit-Namen mit Sonderzeichen werden korrekt sanitisiert
- [ ] Anleitung zum Bereinigen alter Prometheus-Daten ist dokumentiert

## Annahmen & Klärungen

| Frage | Antwort |
|-------|---------|
| Was bei leerem Einheit-Namen? | Fallback auf "unbekannt" |
| Was bei gelöschter Einheit? | Label bleibt bis zum nächsten Upload |
| Technik für dynamisches Label? | Gauge bei jedem Upload neu registrieren |
| Alerting? | Nicht in dieser Phase |

## Abgrenzung / Out of Scope

- Separate Metriken pro Einheit (nur letzte Einheit wird gespeichert)
- Grafana Alerting (folgt später)
- Historisierung der Upload-Einheiten

---

## Phasen-Übersicht

| Phase | Beschreibung | Status |
|-------|--------------|--------|
| 1 | Backend: MetricsService erweitern | ⬜ Offen |
| 2 | Backend: Controller anpassen | ⬜ Offen |
| 3 | Grafana: Dashboard anpassen | ⬜ Offen |
| 4 | Dokumentation: Prometheus bereinigen | ⬜ Offen |

**Legende:** ⬜ Offen | 🔄 In Bearbeitung | ✅ Abgeschlossen

---

## Phase 1: Backend - MetricsService erweitern

### Ziel
Die Gauge-Metrik soll dynamisch mit einem `einheit`-Label registriert werden.

### Aufgaben
1. Feld `AtomicReference<String> letzteUploadEinheit` hinzufügen
2. Feld `Gauge letzterMessdatenUploadGauge` für Referenz auf aktuelle Gauge
3. Methode `registerUploadGauge(String einheitName)` erstellen
4. Methode `sanitizeEinheitName(String name)` erstellen
5. Methode `recordMessdatenUpload(String einheitName)` erstellen
6. Persistierung erweitern: Einheit-Name im JSON speichern
7. Laden erweitern: Einheit-Name aus JSON lesen und Gauge registrieren

### Betroffene Dateien
- `backend-service/src/main/java/ch/nacht/service/MetricsService.java`

### Technische Details

**Gauge neu registrieren:**
```java
private void registerUploadGauge(String einheitName) {
    if (letzterMessdatenUploadGauge != null) {
        meterRegistry.remove(letzterMessdatenUploadGauge);
    }
    letzterMessdatenUploadGauge = Gauge.builder(METRIC_MESSDATEN_UPLOAD_ZEITPUNKT, ...)
            .tag("einheit", einheitName)
            .register(meterRegistry);
}
```

**JSON-Struktur für Persistierung:**
```json
{
  "value": "2025-12-18T10:30:00",
  "einheit": "1. Stock li"
}
```

---

## Phase 2: Backend - Controller anpassen

### Ziel
Der MesswerteController ruft beim Upload den Einheit-Namen ab und übergibt ihn an MetricsService.

### Aufgaben
1. `EinheitService` in MesswerteController injizieren
2. Im Upload-Endpoint: Einheit-Namen via `einheitService.getEinheitById()` abrufen
3. `metricsService.recordMessdatenUpload(einheitName)` aufrufen

### Betroffene Dateien
- `backend-service/src/main/java/ch/nacht/controller/MesswerteController.java`

### Code-Änderung
```java
String einheitName = einheitService.getEinheitById(einheitId)
        .map(Einheit::getName)
        .orElse("unbekannt");
metricsService.recordMessdatenUpload(einheitName);
```

---

## Phase 3: Grafana - Dashboard anpassen

### Ziel
Das Panel "Letzter Messdaten-Upload" zeigt zusätzlich den Einheit-Namen an.

### Aufgaben
1. Query anpassen: `legendFormat: "{{einheit}}"`
2. Panel-Option: `textMode: "value_and_name"` für Anzeige von Wert und Label

### Betroffene Dateien
- `grafana/provisioning/dashboards/zev-dashboard.json`

### Prometheus-Query
```promql
zev_messdaten_upload_letzter_zeitpunkt * 1000
```
Mit Legend Format `{{einheit}}` wird der Label-Wert angezeigt.

---

## Phase 4: Dokumentation - Prometheus bereinigen

### Ziel
Anleitung erstellen, wie die alte Metrik ohne Label aus Prometheus gelöscht wird.

### Optionen

**Option A: Prometheus-Volume löschen (Entwicklung)**
```bash
docker-compose stop prometheus
docker volume rm zev_prometheus-data
docker-compose up -d prometheus
```

**Option B: Admin API (falls aktiviert)**
```bash
curl -X POST -g 'http://localhost:9090/api/v1/admin/tsdb/delete_series?match[]={__name__="zev_messdaten_upload_letzter_zeitpunkt",einheit=""}'
curl -X POST http://localhost:9090/api/v1/admin/tsdb/clean_tombstones
```

**Option C: Admin API aktivieren**
```yaml
prometheus:
  command:
    - '--web.enable-admin-api'
```

---

## Risiken und Mitigationen

| Risiko | Wahrscheinlichkeit | Auswirkung | Mitigation |
|--------|-------------------|------------|------------|
| Einheit-Namen mit Sonderzeichen | Mittel | Niedrig | Namen sanitisieren |
| Alte Metrik ohne Label in Prometheus | Hoch | Niedrig | Prometheus TSDB bereinigen |
| Gauge-Neuregistrierung schlägt fehl | Niedrig | Mittel | Try-Catch und Logging |

---

## Abhängigkeiten

```
Phase 1 (MetricsService)
    ↓
Phase 2 (Controller)
    ↓
Phase 3 (Dashboard)
    ↓
Phase 4 (Dokumentation)
```

---

## Testplan

| Test | Typ | Beschreibung |
|------|-----|--------------|
| MetricsService Unit Test | Unit | `recordMessdatenUpload()` setzt Label korrekt |
| Sanitisierung Test | Unit | Sonderzeichen werden entfernt/ersetzt |
| Persistierung Test | Integration | Label wird in DB gespeichert und geladen |
| E2E Upload Test | E2E | Nach Upload zeigt Grafana den Einheit-Namen |
