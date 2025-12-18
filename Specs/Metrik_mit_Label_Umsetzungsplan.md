# Umsetzungsplan: Metrik mit Label/Tag

## Zusammenfassung

Die Metrik `zev.messdaten.upload.letzter_zeitpunkt` soll um ein Label `einheit` erweitert werden, das den Namen der zuletzt verwendeten Einheit enthält. Es wird nur **ein** Label-Wert gespeichert (die letzte Einheit), nicht pro Einheit separate Metriken.

## Klärungen

- **Scope:** Nur ein Label für die zuletzt verwendete Einheit
- **Label-Wert:** Das Feld `name` der Einheit wird als Label-Wert verwendet
- **Technik:** Bei jedem Upload wird die Gauge neu registriert mit dem aktuellen Einheit-Namen
- **Alerting:** Kein Alerting in dieser Phase

---

## Phasen-Übersicht

| Phase | Beschreibung | Status |
|-------|--------------|--------|
| 1 | Backend: MetricsService erweitern | ✅ Abgeschlossen |
| 2 | Backend: Controller anpassen | ✅ Abgeschlossen |
| 3 | Grafana: Dashboard anpassen | ✅ Abgeschlossen |
| 4 | Prometheus: Alte Metrik bereinigen | ⬜ Offen (manuell) |

**Legende:** ⬜ Offen | 🔄 In Bearbeitung | ✅ Abgeschlossen

---

## Phase 1: Backend - MetricsService erweitern

### Aufgaben
1. **Feld für aktuellen Einheit-Namen hinzufügen**
   - `AtomicReference<String> letzteUploadEinheit`

2. **Gauge dynamisch neu registrieren**
   - MeterRegistry als Feld speichern
   - Bei `recordMessdatenUpload(String einheitName)`: alte Gauge entfernen, neue mit Tag registrieren

3. **Persistierung erweitern**
   - Einheit-Name zusammen mit Zeitstempel im JSON speichern
   - Beim Laden: Einheit-Name aus JSON lesen und Gauge mit Tag registrieren

4. **Sanitisierung des Einheit-Namens**
   - Sonderzeichen entfernen/ersetzen für Prometheus-Kompatibilität

### Betroffene Dateien
- `backend-service/src/main/java/ch/nacht/service/MetricsService.java`

### Code-Änderungen

```java
// Neue Felder
private final MeterRegistry meterRegistry;
private final AtomicReference<String> letzteUploadEinheit = new AtomicReference<>(null);
private Gauge letzterMessdatenUploadGauge;

// Neue Methode
@Transactional
public void recordMessdatenUpload(String einheitName) {
    long newTotal = messdatenUploadTotal.incrementAndGet();
    Instant now = Instant.now();
    letzterMessdatenUpload.set(now);

    // Einheit-Name sanitisieren und speichern
    String sanitizedName = sanitizeEinheitName(einheitName);
    letzteUploadEinheit.set(sanitizedName);

    // Gauge neu registrieren mit aktuellem Label
    reRegisterUploadGauge(sanitizedName);

    // Persistieren (inkl. Einheit-Name)
    persistMetric(METRIC_MESSDATEN_UPLOAD_TOTAL, newTotal);
    persistTimestampMetricWithEinheit(METRIC_MESSDATEN_UPLOAD_ZEITPUNKT, now, sanitizedName);
}

private void reRegisterUploadGauge(String einheitName) {
    // Alte Gauge entfernen falls vorhanden
    if (letzterMessdatenUploadGauge != null) {
        meterRegistry.remove(letzterMessdatenUploadGauge);
    }
    // Neue Gauge mit Label registrieren
    letzterMessdatenUploadGauge = Gauge.builder(METRIC_MESSDATEN_UPLOAD_ZEITPUNKT, letzterMessdatenUpload,
                    ref -> ref.get() != null ? ref.get().getEpochSecond() : 0)
            .tag("einheit", einheitName)
            .description("Unix-Timestamp des letzten Messdaten-Uploads")
            .register(meterRegistry);
}

private String sanitizeEinheitName(String name) {
    if (name == null) return "unbekannt";
    // Nur alphanumerische Zeichen, Leerzeichen, Punkte und Bindestriche erlauben
    return name.replaceAll("[^a-zA-Z0-9äöüÄÖÜß .\\-]", "_");
}
```

### JSON-Struktur für Persistierung
```json
{
  "value": "2025-12-18T10:30:00",
  "einheit": "1. Stock li"
}
```

---

## Phase 2: Backend - Controller anpassen

### Aufgaben
1. **EinheitService injizieren**
2. **Einheit-Namen beim Upload abrufen**
3. **MetricsService mit Einheit-Namen aufrufen**

### Betroffene Dateien
- `backend-service/src/main/java/ch/nacht/controller/MesswerteController.java`

### Code-Änderungen

```java
// Import hinzufügen
import ch.nacht.entity.Einheit;
import ch.nacht.service.EinheitService;

// Feld hinzufügen
private final EinheitService einheitService;

// Konstruktor anpassen
public MesswerteController(MesswerteService messwerteService, MetricsService metricsService,
                           EinheitService einheitService) {
    this.messwerteService = messwerteService;
    this.metricsService = metricsService;
    this.einheitService = einheitService;
}

// Im Upload-Endpoint
String einheitName = einheitService.getEinheitById(einheitId)
        .map(Einheit::getName)
        .orElse("unbekannt");
metricsService.recordMessdatenUpload(einheitName);
```

---

## Phase 3: Grafana - Dashboard anpassen

### Aufgaben
1. **Panel "Letzter Messdaten-Upload" anpassen**
   - Einheit-Name aus Label anzeigen
   - Query anpassen für Label-Extraktion

### Betroffene Dateien
- `grafana/provisioning/dashboards/zev-dashboard.json`

### Prometheus-Query
```promql
# Zeitstempel mit Einheit-Label
zev_messdaten_upload_letzter_zeitpunkt * 1000

# Label-Wert in Grafana anzeigen via Legend: {{einheit}}
```

### Panel-Anpassung
- Legend Format: `{{einheit}}`
- Oder: Transformation um Label als separate Spalte anzuzeigen

---

## Phase 4: Prometheus - Alte Metrik bereinigen

### Problem
Nach der Einführung des `einheit`-Labels existiert die alte Metrik ohne Label noch in Prometheus. Dies kann zu Verwirrung führen.

### Option 1: Prometheus-Volume vollständig löschen (empfohlen für Entwicklung)

```bash
# Docker-Container stoppen
docker-compose stop prometheus

# Prometheus-Volume löschen
docker volume rm zev_prometheus-data

# Prometheus neu starten
docker-compose up -d prometheus
```

### Option 2: Selektives Löschen über Admin API

Falls die Admin API aktiviert ist:

```bash
# Alte Metrik ohne Label löschen
curl -X POST -g 'http://localhost:9090/api/v1/admin/tsdb/delete_series?match[]={__name__="zev_messdaten_upload_letzter_zeitpunkt",einheit=""}'

# Tombstones bereinigen
curl -X POST http://localhost:9090/api/v1/admin/tsdb/clean_tombstones
```

### Option 3: Admin API in Docker-Compose aktivieren

Falls die Admin API nicht aktiviert ist:

```yaml
prometheus:
  command:
    - '--config.file=/etc/prometheus/prometheus.yml'
    - '--storage.tsdb.path=/prometheus'
    - '--web.enable-lifecycle'
    - '--web.enable-admin-api'  # Diese Zeile hinzufügen
```

---

## Technische Details

### Metrik-Struktur in Prometheus
```
zev_messdaten_upload_letzter_zeitpunkt{einheit="1. Stock li"} 1734518400
```

### Datenbank-Struktur (metriken-Tabelle)
| name | value | zeitstempel |
|------|-------|-------------|
| zev.messdaten.upload.letzter_zeitpunkt | {"value":"2025-12-18T10:30:00","einheit":"1. Stock li"} | 2025-12-18 10:30:00 |

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
Phase 4 (Prometheus bereinigen)
```
