# MQTT-Integration

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Das ZEV-Backend abonniert einen MQTT-Broker und übernimmt automatisch die von einem Raspberry-Pi-Gateway gelieferten Zähler-Messwerte in die Datenbank — als Alternative/Ergänzung zum manuellen CSV-Upload für die angebundenen Zähler.
* **Warum machen wir das:**
  - Automatisierte, zeitnahe Datenerfassung statt manueller CSV-Uploads.
  - Weniger Fehlerquellen (kein manuelles Hochladen/Zuordnen).
  - Grundlage für spätere Live-Auswertungen.
* **Aktueller Stand:**
  - Messwerte kommen heute per **CSV-Upload** (`MesswerteService.processCsvUpload`, Spalten `zeit,total,zev` je Einheit, inkl. KI-gestütztem Einheiten-Matching). `zev_calculated` wird separat durch die **Solarverteilung** (`SolarDistribution`) berechnet.
  - Die Zähler werden physisch bereits über einen **Raspberry Pi** ausgelesen (3× Wago via Modbus TCP, 1× BKW via gPlug) und heute per **SFTP** bereitgestellt (siehe `docs/Netzwerk-Topologie-Hene.md`).
  - Ziel-Topologie: Der Pi ist MQTT-**Publisher**, der **Broker (Mosquitto) läuft auf dem NAS** (Variante B der Topologie-Doku), das ZEV-Backend ist **Subscriber**.
  - Es gibt **noch keinen** MQTT-Code / keine MQTT-Dependency im Backend.

> **Designentscheidung – absolute Zählerstände statt Deltas:** Der Pi überträgt die **absoluten (kumulativen) Zählerstände**, nicht berechnete Deltas. Die **Delta-/Intervall-Berechnung erfolgt im Backend**. Vorteil: **verlusttolerant** — geht eine MQTT-Nachricht verloren, bleibt die Gesamtsumme korrekt (die Differenz zweier empfangener Stände erfasst den Verbrauch dazwischen weiterhin vollständig); es sinkt nur kurz die zeitliche Auflösung. Dadurch entfallen strenge QoS-/Pufferungs-Anforderungen (kein zwingender Store-and-Forward-Bridge, Variante C).

> **Rollentrennung (siehe Topologie-Doku):** Ein MQTT-Broker verteilt nur Nachrichten und liest selbst keine Zähler. Das Auslesen (Modbus/gPlug) + Publish der Zählerstände macht ein **separater Reader-/Publisher-Prozess auf dem Pi** (`Specs/Pi-Gateway-Software.md`) — nicht Teil dieses Backend-Features (Out of Scope).

## 2. Funktionale Anforderungen (FR)

### FR-1: MQTT-Broker-Anbindung (Subscriber)
1. Das ZEV-Backend verbindet sich beim Start automatisch zum konfigurierten Broker (Mosquitto auf dem NAS).
2. Verbindungsparameter (URL, Username, Passwort, Client-ID, QoS) über `application.yml` / Environment — **keine Secrets im Code**.
3. Subscribe mit Wildcard auf alle Messwert-Topics (FR-2).
4. Bei Verbindungsabbruch automatischer Reconnect mit Exponential Backoff.

> **Profil-Aktivierung (`@Profile("mqtt")`):** Das Bean, das sich mit dem Broker verbindet und die Messwerte einliest (MQTT-Subscriber/Inbound-Adapter samt Message-Handler), ist **nur aktiv, wenn das Spring-Boot-Profil `mqtt` gesetzt ist** (`@Profile("mqtt")`; aktiviert via `SPRING_PROFILES_ACTIVE=mqtt` bzw. `spring.profiles.active`). Ohne dieses Profil (Default, Tests, lokale Entwicklung) startet **kein** MQTT-Client — es wird keine Broker-Verbindung aufgebaut, das Backend läuft unverändert nur mit CSV-Upload. So bleibt die MQTT-Anbindung optional und Umgebungen ohne erreichbaren Broker starten fehlerfrei.

### FR-2: Topic-Struktur
```
zev/{orgId}/{messpunkt}/messwert
```
| Segment | Beschreibung | Beispiel |
|---------|--------------|----------|
| `zev` | Root-Namespace | `zev` |
| `{orgId}` | Mandanten-ID (`org_id`) der Einheit | `42` |
| `{messpunkt}` | Messpunkt-Bezeichnung der Einheit (`einheit.messpunkt`) | `ID742-Wohnung-1` |
| `messwert` | Message-Typ | `messwert` |

* Backend abonniert `zev/+/+/messwert`.
* Der Pi adressiert die Einheit über den **Messpunkt** (physisch bekannt), nicht über die interne `einheit.id`. Das Backend löst die Einheit über **(`org_id`, `messpunkt`)** auf.
* `{orgId}` folgt vorerst dem bestehenden `Messwerte`-Modell (internes `org_id` als `BIGINT`); Alternative (Keycloak-Alias/UUID) siehe Offene Fragen.

### FR-3: Payload-Format (JSON, absolute Zählerstände)
Der Pi publiziert die **absoluten kumulativen Zählerstände** zum Messzeitpunkt:
```json
{
  "timestamp": "2026-06-19T14:30:00+02:00",
  "zaehlerstandBezug": 12345.678,
  "zaehlerstandEinspeisung": 4321.000
}
```
| Feld | Typ | Pflicht | Beschreibung |
|------|-----|---------|--------------|
| `timestamp` | ISO 8601 (lokale Zeit mit Offset) | Ja | Zeitpunkt der Messung, z. B. `2026-06-19T14:30:00+02:00` |
| `zaehlerstandBezug` | Decimal (kWh, kumulativ) | Ja | Absoluter Zählerstand Bezug/Verbrauch (monoton steigend) |
| `zaehlerstandEinspeisung` | Decimal (kWh, kumulativ) | Ja | Absoluter Zählerstand Einspeisung (monoton steigend) |

> **Zeitzone (Wire = lokale Zeit mit Offset, Speicherung verbatim):** Der Pi sendet `timestamp`
> als **lokale Zeit mit UTC-Offset** (ISO 8601, z. B. `2026-06-19T14:30:00+02:00`) – eindeutig
> (kein DST-Doppelstunden-Problem). Das Backend übernimmt die lokale Wanduhrzeit **verbatim**
> (`OffsetDateTime.toLocalDateTime()`, ohne Zeitzonen-Umrechnung) und speichert sie in
> `zaehler_rohdaten.zeit` – konsistent mit der `messwerte`-Tabelle/dem CSV-Upload (naive lokale
> Zeit). Der Ingest ist damit **unabhängig** von der Backend-Zeitzone. Der Aggregations-Job nutzt
> `LocalDateTime.now()`; dafür muss der Container/JVM in der lokalen Zone laufen
> (`TZ=Europe/Zurich`, in den `docker-compose*.yml` gesetzt), damit `now()` auf demselben
> Raster liegt wie die gespeicherten Zeitstempel.

> Die Stände sind **kumulativ und monoton** (Ausnahme: Zähler-Reset/-tausch, siehe Aggregation/Edge Cases). Die Abbildung auf `messwerte` ist entschieden (FR-6.4): `total = ΔBezug − ΔEinspeisung` (vorzeichenbehaftet), `zev = 0` (Sentinel), `zev_calculated` bleibt Ergebnis der Solarverteilung (die `zev = 0` durch `zev_calculated` ersetzt, FR-9).

### FR-4: Backend MQTT-Subscriber-Verarbeitung

> **Ereignisgesteuert (Push), kein Polling:** Der Empfang läuft über einen **message-driven
> Inbound-Adapter** (`MqttPahoMessageDrivenChannelAdapter`), der beim Start eine dauerhafte
> Broker-Subscription öffnet. Eintreffende Nachrichten werden über einen Channel an einen
> `@ServiceActivator`-Handler gepusht, der die Verarbeitung (unten) anstösst — es gibt dafür
> **keinen** Scheduled-/Polling-Task. Der `@Scheduled`-Task existiert nur für die Aggregation (FR-6).

Pro eingehender Nachricht:
1. Topic parsen → `orgId` + `messpunkt`.
2. JSON validieren (Pflichtfelder, Typen, Zählerstände nicht negativ).
3. Einheit über **`messpunkt` + `orgId`** auflösen (Multi-Tenancy-Prüfung).
4. Bei Erfolg: Rohdatensatz (absolute Stände) in `zaehler_rohdaten` (FR-5).
5. Bei Validierungs-/Zuordnungsfehlern: Warnung loggen, Nachricht verwerfen (keine Exception nach aussen).
6. Verarbeitung asynchron / non-blocking.

> **Kein JWT im Ingest-Pfad:** MQTT-Nachrichten laufen ohne User-Request/JWT. Der Mandantenkontext (`org_id`) darf **nicht** aus `OrganizationContextService` (JWT) stammen, sondern wird aus dem Topic abgeleitet, gegen die Einheit geprüft und beim Persistieren explizit gesetzt (Hibernate-`orgFilter` entsprechend versorgen).

### FR-5: Rohdaten-Persistierung (absolute Zählerstände)
Neue Flyway-Migration (`V[n]__create_zaehler_rohdaten.sql`):
```sql
CREATE TABLE zev.zaehler_rohdaten (
    id                        BIGSERIAL PRIMARY KEY,
    org_id                    BIGINT NOT NULL,
    einheit_id                BIGINT NOT NULL REFERENCES zev.einheit(id),
    zeit                      TIMESTAMP NOT NULL,
    zaehlerstand_bezug        DECIMAL(14,4) NOT NULL,
    zaehlerstand_einspeisung  DECIMAL(14,4) NOT NULL,
    empfangen_am              TIMESTAMP DEFAULT NOW(),
    verarbeitet               BOOLEAN DEFAULT FALSE,
    verarbeitet_am            TIMESTAMP,
    CONSTRAINT uk_zaehler_rohdaten UNIQUE (einheit_id, zeit)
);
CREATE INDEX idx_zaehler_rohdaten_unverarbeitet
    ON zev.zaehler_rohdaten(verarbeitet, zeit) WHERE verarbeitet = FALSE;
```
* Speichert **absolute Stände** (nicht Deltas); `DECIMAL(14,4)` für grosse kumulative Werte.
* `org_id` als `BIGINT` analog zur `messwerte`-Tabelle (`Messwerte.orgId` = `Long`); Hibernate-`@Filter` `orgFilter` wie bei allen Entities.
* Duplikat (gleiche `einheit_id` + `zeit`): Upsert.

### FR-6: Scheduled Aggregation (15-Minuten-Job) — Delta-Bildung im Backend
1. Scheduled Job (`0 5,20,35,50 * * * *`, d.h. jeweils **5 Minuten nach** der Viertelstunde) ermittelt das abgeschlossene 15-Minuten-Intervall. Der 5-Minuten-Versatz gibt spät eintreffenden MQTT-Nachrichten Zeit; die **verarbeiteten Intervallgrenzen bleiben quartalsgenau** (`:00/:15/:30/:45`).
2. Pro Einheit **je Register die Differenz** über die Intervallgrenze bilden: `ΔBezug` und `ΔEinspeisung` = `(letzter Stand ≤ Intervallende) − (Referenzstand = letzter Stand ≤ vorheriges Intervallende)`.
3. **Reset/Überlauf/Zählertausch:** Die Guard-Prüfung gilt **pro Register** — ist `ΔBezug` **oder** `ΔEinspeisung` < 0, wird das jeweilige Register-Delta **nicht negativ** übernommen (auf 0 gesetzt), die Referenz neu gesetzt und WARN geloggt. (Wichtig: nicht am Vorzeichen von `total` prüfen, da negatives `total` bei Einspeisung legitim ist – siehe Mapping unten.)
4. **Mapping auf `messwerte`** (`zeit`, `total`, `einheit`, `org_id`) gemäss Entscheidung (§8):
   * `total` = **vorzeichenbehafteter Netto-Wert**: `total = ΔBezug − ΔEinspeisung`. Bezug überwiegt → **positiv** (Verbrauch/Consumer); Einspeisung überwiegt → **negativ** (Produktion/Producer). Producer vs. Consumer wird also **über das Vorzeichen** unterschieden, ohne `einheit.typ`-Verzweigung.
   * `zev`:
     * **Consumer** (`einheit.typ = CONSUMER`): **0** (Sentinel „nicht gemessen"; wird nicht aus MQTT befüllt). Die Solarverteilung ersetzt `0` später durch `zev_calculated` (FR-9).
     * **Producer** (`einheit.typ = PRODUCER`): **`zev = total`** — die gesamte Produktion des Intervalls wird als ZEV-relevanter Wert übernommen, damit die **Statistik die Produktion korrekt ausweist**. Dieser Wert ist ≠ 0 (ausser bei `total = 0`) und wird daher von der Solarverteilung **nicht** überschrieben (FR-9 greift nur bei `zev = 0`).
     * **Keine** Tabellen-/Constraint-Änderung nötig – `messwerte.zev` bleibt `NOT NULL`.
     * Dies ist die **einzige** typ-abhängige Verzweigung im Ingest; die `total`-Bildung bleibt vorzeichenbasiert ohne `einheit.typ`-Verzweigung.
   * `zev_calculated` = **NULL** beim Ingest; wird erst durch die **Solarverteilung** (`SolarDistribution`) berechnet. Diese setzt anschliessend `zev = zev_calculated`, sofern `zev = 0` (FR-9).
5. Speichern mit `quelle = 'MQTT'` (FR-7); Insert/Upsert.
6. Verarbeitete Rohdaten markieren: `verarbeitet = TRUE`, `verarbeitet_am = NOW()`; Referenzstand fortschreiben.
7. **Unmittelbar nach der Aggregation** wird **je Mandant (`org_id`)** die **Solarverteilung** (FR-9) für den **behandelten Zeitraum** ausgeführt — d.h. für das Intervall `[frühestes … spätestes verarbeitetes Intervallende]` des jeweiligen Mandanten. Dadurch tragen die frisch aggregierten Messwerte sofort ihr `zev_calculated` (und – wo `zev = 0` – `zev`), ohne manuellen Anstoss über die UI.
   * Aufruf im `ZaehlerAggregationService` über `MesswerteService.calculateSolarDistributionForOrg(orgId, von, bis, algorithmus)`. Da **kein JWT/Request-Kontext** vorliegt, wird der Hibernate-`orgFilter` **explizit** mit der `org_id` versorgt (nicht aus `OrganizationContextService`); **kein** Fortschritts-Tracking.
   * **Algorithmus:** **`PROPORTIONAL`** (Verbraucher erhalten proportional zu ihrem Verbrauch); keine mandantenspezifische Wahl.
   * Fehler der Verteilung werden **pro Mandant** geloggt und brechen die Aggregation der übrigen Mandanten nicht ab.

> **Profil-Aktivierung (`@Profile("mqtt")`):** Das Bean, das die Rohdaten periodisch aus `zaehler_rohdaten` liest und aggregiert (der `@Scheduled`-Aggregations-Job samt zugehörigem Service), ist **nur aktiv, wenn das Spring-Boot-Profil `mqtt` gesetzt ist** (`@Profile("mqtt")`; aktiviert via `SPRING_PROFILES_ACTIVE=mqtt` bzw. `spring.profiles.active`) — analog zum MQTT-Subscriber (FR-1). Ohne dieses Profil (Default, Tests, lokale Entwicklung) läuft **kein** Aggregations-Job; die `messwerte`-Tabelle wird ausschliesslich über den CSV-Upload befüllt.

> **Verlusttoleranz:** Fehlt in einem Intervall eine Meldung, fällt der Verbrauch ins nächste Intervall mit Meldung — die **Gesamtsumme bleibt korrekt**, nur die zeitliche Auflösung sinkt kurzzeitig. Das ist der zentrale Vorteil absoluter Stände gegenüber Deltas.

### FR-7: Erweiterung der `messwerte`-Tabelle (Quelle)
```sql
ALTER TABLE zev.messwerte ADD COLUMN IF NOT EXISTS quelle VARCHAR(20) DEFAULT 'CSV';
-- Mögliche Werte: 'CSV', 'MQTT', 'API'
```
Bestehende Zeilen erhalten per Default `'CSV'` (rückwärtskompatibel). **Keine** Änderung an `messwerte.zev` nötig: Der MQTT-Ingest setzt `zev = 0` (Sentinel) für Consumer bzw. `zev = total` für Producer (FR-6.4); die Spalte bleibt `NOT NULL`. Die Solarverteilung ersetzt `zev = 0` später durch `zev_calculated` (FR-9). Damit bleiben alle bestehenden Konsumenten von `messwerte.zev` (Statistik/Rechnung) unverändert kompatibel.

### FR-8: Monitoring & Status
1. Prometheus-Metriken (bestehende Infrastruktur, siehe `Specs/Metriken.md` / `MetricsService`):
   - `zev_mqtt_messages_received_total`, `..._processed_total`, `..._failed_total` (Counter)
   - `zev_mqtt_last_message_timestamp` (Gauge)
   - `zev_aggregation_runs_total` (Counter), `zev_aggregation_last_run_timestamp` (Gauge)
2. Health-Indicator `/actuator/health/mqtt` zeigt den Broker-Verbindungsstatus.

### FR-9: Anpassung Solarverteilung (`zev`-Fallback)
1. Die Solarverteilung (`SolarDistribution`) berechnet wie bisher `zev_calculated` pro Consumer/Intervall.
2. **Neu:** Nach der Berechnung wird `zev = zev_calculated` gesetzt, **sofern `zev = 0`** (Sentinel für „nicht gemessen", von MQTT-importierten Consumer-Werten). Bereits gesetzte `zev`-Werte (z. B. aus CSV oder MQTT-**Producer**-Werte mit `zev = total`, `zev ≠ 0`) bleiben unverändert.
3. Dadurch tragen MQTT-Werte nach der Verteilung denselben `zev`-Wert wie `zev_calculated`; CSV-Werte behalten ihren gemessenen Anteil.

> **Kante:** Ein *gemessener* Wert von genau `zev = 0` (z. B. Nachtstunde ohne ZEV-Bezug aus CSV) würde ebenfalls durch `zev_calculated` ersetzt. Da `zev_calculated` in solchen Fällen ohnehin ~0 ist, ist der Effekt vernachlässigbar; falls doch relevant, kann die Unterscheidung über `quelle = 'MQTT'` statt über `zev = 0` erfolgen (Umsetzungsdetail).

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

**MQTT-Empfang:**
* [ ] Backend verbindet sich beim Start automatisch zum Broker und abonniert `zev/+/+/messwert`.
* [ ] Gültige Nachricht → Zeile in `zaehler_rohdaten` mit korrektem `org_id` + `einheit_id` (aufgelöst über `messpunkt`) und den absoluten Ständen.
* [ ] Ungültiges JSON / fehlende Pflichtfelder → WARN, kein Insert.
* [ ] Unbekannter Messpunkt → abgelehnt (WARN, kein Insert).
* [ ] `orgId` passt nicht zur Einheit → verworfen (Security-Log) — Mandanten-Isolation gewährleistet.
* [ ] Negativer Zählerstand → verworfen.
* [ ] Duplikat (Einheit + `zeit`) → Upsert.
* [ ] Nach Broker-Abbruch automatischer Reconnect.

**15-Minuten-Aggregation:**
* [ ] Job läuft um :05, :20, :35, :50; verarbeitet werden die quartalsgenauen Intervalle (:00–:15, :15–:30, …); `ΔBezug`/`ΔEinspeisung` je Register korrekt als **Differenz** gebildet.
* [ ] `total = ΔBezug − ΔEinspeisung` korrekt vorzeichenbehaftet (Bezug → positiv, Einspeisung → negativ).
* [ ] `zev` wird beim MQTT-Ingest für **Consumer** auf `0` gesetzt (Sentinel), für **Producer** auf `total`; `messwerte.zev` bleibt `NOT NULL`.
* [ ] Für eine **Producer**-Einheit gilt nach der Aggregation `zev = total` (und wird von der Solarverteilung nicht überschrieben, da `zev ≠ 0`).
* [ ] Aggregierte Werte in `messwerte` mit `quelle = 'MQTT'`.
* [ ] Verarbeitete Rohdaten `verarbeitet = TRUE`; bereits verarbeitete werden nicht erneut aggregiert.
* [ ] Leeres Intervall (keine neue Meldung) → kein `messwerte`-Eintrag (kein Nullwert).
* [ ] Verlorene Zwischen-Nachricht → Gesamtsumme über die betroffenen Intervalle bleibt korrekt (verlusttolerant).
* [ ] Zähler-Reset/Rücksprung **pro Register** (`ΔBezug` bzw. `ΔEinspeisung` < 0) → betroffenes Delta auf 0, Referenz neu gesetzt, WARN. (Ein negatives `total` aus Einspeisung ist dagegen legitim und wird übernommen.)
* [ ] `zev_calculated` wird durch den Ingest nicht verändert; Solarverteilung funktioniert unverändert.
* [ ] Nach der Solarverteilung gilt `zev = zev_calculated`, wo `zev = 0` war (MQTT-Werte); gemessene CSV-Werte (`zev ≠ 0`) bleiben unverändert (FR-9).
* [ ] Nach jedem Aggregationslauf wird je betroffenem Mandant die Solarverteilung für den behandelten Zeitraum automatisch ausgeführt (`calculateSolarDistributionForOrg`, `orgFilter` explizit gesetzt); ohne verarbeitetes Intervall erfolgt kein Verteilungsaufruf. Ein Fehler bei einem Mandanten bricht die übrigen nicht ab.

**Monitoring & Kompatibilität:**
* [ ] MQTT-Metriken unter `/actuator/prometheus`; `/actuator/health/mqtt` zeigt Status.
* [ ] Bestehender CSV-Upload unverändert nutzbar; Werte beider Quellen gleichwertig.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Verarbeitung einer Nachricht < 100 ms; asynchron/non-blocking; der Aggregations-Job blockiert die MQTT-Verarbeitung nicht.

### NFR-2: Sicherheit & Mandantenfähigkeit
* **Kein Endbenutzer-UI / nicht rollenbasiert:** Der Ingest ist ein Backend-Hintergrunddienst (keine `zev_user`/`zev_admin`-Route, kein `@PreAuthorize`). Ein späteres Status-/Konfig-UI wäre über das Permission-Modell (`Specs/Composite-Roles.md`) zu schützen (Out of Scope).
* **Broker-Auth:** Username/Passwort (nur via Env), **ACLs pro Mandant** (Pi darf nur in seine `org_id` publizieren); getrennte Broker-User für Publisher (Pi) und Backend-Subscriber.
* **TLS (Port 8883)** empfohlen, zusätzlich zum VPN-Tunnel (Defense-in-Depth).
* **Mandanten-Isolation:** `org_id` aus dem Topic wird gegen die Einheit geprüft; Rohdaten/Messwerte tragen `org_id` und unterliegen dem Hibernate-`orgFilter`. Da kein JWT vorliegt, wird der Filter explizit mit dem topic-abgeleiteten `org_id` versorgt.

### NFR-3: Zuverlässigkeit
* **Verlusttoleranz durch absolute Zählerstände:** Eine verlorene Nachricht führt **nicht** zu Datenverlust (die Differenzbildung ist selbstheilend). Daher genügt **QoS 0/1**; ein aufwändiger Store-and-Forward-Puffer/Bridge (Variante C) ist **nicht erforderlich** (Broker auf dem NAS = Variante B genügt).
* Duplikate idempotent über Unique-Constraint (`einheit_id`, `zeit`).
* Bei DB-Fehler: Rohdaten nicht als verarbeitet markieren, erneuter Versuch.

### NFR-4: Kompatibilität
* Bestehender CSV-Upload bleibt funktionsfähig; CSV- und MQTT-Werte werden fachlich gleich behandelt (gleiche `messwerte`-Tabelle), unterscheidbar über `quelle`.
* `quelle`-Default `'CSV'` → rückwärtskompatibel, keine Breaking Changes.
* Neue Tabelle/Spalte via **Flyway-Migration** (Namenskonvention `V[XX]__…`).

## 5. Edge Cases & Fehlerbehandlung
| Szenario | Verhalten |
|----------|-----------|
| Ungültiges/leeres JSON, fehlende Pflichtfelder | WARN, verwerfen |
| Unbekannter Messpunkt | WARN, verwerfen |
| `orgId` passt nicht zur Einheit | Security-WARN, verwerfen (kein Cross-Tenant-Write) |
| Negativer Zählerstand | WARN, verwerfen |
| Timestamp in der Zukunft | WARN, trotzdem speichern |
| Duplikat (Einheit + `zeit`) | Upsert |
| Verlorene Nachricht(en) | kein Datenverlust — Differenz zum nächsten Stand deckt die Lücke; nur Auflösungsverlust |
| Register-Stand < Referenz (Reset/Überlauf/Zählertausch) | Aggregation **je Register**: betroffenes Delta (`ΔBezug`/`ΔEinspeisung`) auf 0, Referenz neu setzen, WARN (negatives `total` aus Einspeisung bleibt gültig) |
| Broker nicht erreichbar | Reconnect mit Exponential Backoff |
| DB nicht erreichbar | Nachricht nicht markieren, Retry |
| Keine neue Meldung im Intervall | kein `messwerte`-Eintrag |
| Job-Ausfall/Neustart | nächster Lauf verarbeitet alle unverarbeiteten Rohdaten |
| Bereits aggregierter `messwerte`-Eintrag | Upsert statt Doppel-Insert |
| Leere Zähler-/Einheitenliste | kein Fehler; nichts zu verarbeiten |

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:**
  - Einheiten (`einheit`) inkl. `messpunkt` müssen existieren (keine automatische Geräteregistrierung).
  - Mosquitto-Broker auf dem NAS betriebsbereit (siehe `docs/Netzwerk-Topologie-Hene.md`, Abschnitt „MQTT-Broker auf dem NAS betreiben").
  - VPN-Verbindung zwischen Pi und NAS; **separater Reader-/Publisher-Prozess** auf dem Pi (Out of Scope, `Specs/Pi-Gateway-Software.md`).
* **Neue Dependencies (Backend):** `spring-integration-mqtt` + Eclipse Paho (`org.eclipse.paho.client.mqttv3`).
* **Backend-Konfiguration (`application.yml`):** `mqtt.broker.url/username/password`, `mqtt.topics.messwerte=zev/+/+/messwert`, `mqtt.qos` (Secrets via Env). Der MQTT-Subscriber ist über das Spring-Profil **`mqtt`** zugeschaltet (`@Profile("mqtt")`, siehe FR-1); ohne aktives Profil wird keine Broker-Verbindung aufgebaut.
* **Neuer Code:** MQTT-Subscriber/Handler, Topic-/Payload-Parser, `ZaehlerRohdaten`-Entity + Repository, Aggregations-/Delta-Service (Scheduled), Health-Indicator, Prometheus-Metriken.
* **Erweiterung:** `messwerte` um `quelle`.
* **Wiederverwendet:** `Einheit`/`Messwerte`, `SolarDistribution` (unverändert), `MetricsService`, Grafana/Prometheus-Stack.
* **Datenmigration:** Keine Bestandsmigration; nur neue Tabelle + `quelle`-Spalte.
* **i18n:** Kein UI-Text in diesem Feature (reiner Backend-Ingest). Falls später Status-UI: Texte via `TranslationService`.
* **Abhängige Dokumente:** `docs/Netzwerk-Topologie-Hene.md` (Topologie/Broker-Setup/Varianten A/B/C), `Specs/Pi-Gateway-Software.md` (Publisher), `Specs/Metriken.md` (Prometheus-Infrastruktur).

## 7. Abgrenzung / Out of Scope
* **Pi-Gateway-Software** (Auslesen Wago/Modbus TCP + BKW/gPlug, Publish der Zählerstände) — separat auf dem Pi implementiert/konfiguriert (`Specs/Pi-Gateway-Software.md`). Die **Delta-Bildung** liegt hingegen im Backend (FR-6).
* Frontend-Live-Anzeige / WebSocket-Bridge.
* Admin-UI für MQTT-Konfiguration / Broker-/ACL-Verwaltung.
* Automatische Einheiten-/Geräteregistrierung; bidirektionale Befehle an Zähler.
* TLS-Zertifikatsverwaltung (manuell, siehe Topologie-Doku).
* Aufbewahrungs-/Cleanup-Strategie für Rohdaten (eigene Spec).
* Beibehaltung des SFTP-Pfads als Fallback — nicht Gegenstand dieser Spec.
* Änderung der `zev_calculated`-**Berechnungslogik** (Verteilalgorithmus) — bleibt wie bisher. **Ausnahme (FR-9):** ein kleiner Zusatz setzt `zev = zev_calculated`, wo `zev = 0` (MQTT-Sentinel) — die eigentliche Verteilung ändert sich nicht.

## 8. Offene Fragen
* [x] **Register → `messwerte`-Mapping:** übertragen wird die **Wirkenergie (kWh)** — Bezug **OBIS 1.8.0** (`zaehlerstandBezug`), Einspeisung **OBIS 2.8.0** (`zaehlerstandEinspeisung`); **keine** Leistung/Blind-/Scheingrössen (siehe `Pi-Gateway-Software.md`). → **Mapping entschieden (siehe FR-6):** `total = ΔBezug − ΔEinspeisung` (**vorzeichenbehaftet**: positiv = Verbrauch, negativ = Einspeisung); `zev = 0` (Sentinel, keine Tabellen-Änderung); `zev_calculated = NULL` beim Ingest, erst durch die Solarverteilung, die dann `zev = zev_calculated` setzt, wo `zev = 0` (FR-9).
* [x] **`org_id`-Typ & Topic-Kennung:** → **internes `org_id` (BIGINT)**, konsistent mit `messwerte`/`zaehler_rohdaten` und der Pi-Config. Kein UUID-Mapping.
* [x] **Messpunkt-Eindeutigkeit:** → **`messpunkt` ist pro `org_id` eindeutig**; Auflösung über `(org_id, messpunkt)` (Unique-Constraint auf `einheit`).
* [x] **Producer vs. Consumer** (`einheit.typ`): → Der **`total`** wird **über das Vorzeichen** gebildet (Bezug positiv, Einspeisung negativ), **ohne** `einheit.typ`-Verzweigung und **ohne** getrennte Register/Topics. **Nachtrag:** Für den **`zev`**-Wert gibt es eine typ-abhängige Verzweigung — Producer: `zev = total`, Consumer: `zev = 0` (Sentinel) — damit die Statistik die Produktion korrekt ausweist (FR-6.4).
* [ ] **Reset/Überlauf-Policy:** Genaue Erkennung (Schwellwert für „Rücksprung") **pro Register** (Bezug/Einspeisung) und Umgang (Referenz neu setzen, Delta = 0)? (Grundsatz in FR-6.3 festgelegt; Schwellwert-Details offen.)
* [ ] **Prosumer im selben Intervall:** Verhalten, wenn `ΔBezug` **und** `ΔEinspeisung` > 0 (Netmetering)? Aktuelle Festlegung: Netto `total = ΔBezug − ΔEinspeisung` (FR-6.4) — genügt das fachlich?
* [x] **`zev`-Behandlung / Verträglichkeit:** → **`zev = 0` beim Ingest** (kein NULL, kein Schema-Eingriff); die Solarverteilung ersetzt `0` durch `zev_calculated` (FR-9). Bestehende `zev`-Konsumenten bleiben kompatibel. Rest-Kante (gemessenes echtes `0`) siehe FR-9-Hinweis.
* [ ] **Rohdaten-Retention:** Cleanup-Job nötig, ab welchem Alter (absolute Stände wachsen unbegrenzt)? --> folgt später
* [x] **Mehrfach-Quelle pro Einheit:** Verhalten, wenn für dieselbe Einheit + Zeitpunkt sowohl CSV- als auch MQTT-Daten vorliegen? → **Warnung loggen, MQTT hat Vorrang.**
