# MQTT-Integration

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Das ZEV-Backend abonniert einen MQTT-Broker und übernimmt automatisch die von einem Raspberry-Pi-Gateway gelieferten Zähler-Messwerte in die Datenbank — als Alternative/Ergänzung zum manuellen CSV-Upload für die angebundenen Zähler.
* **Warum machen wir das:**
  - Automatisierte, zeitnahe Datenerfassung statt manueller CSV-Uploads.
  - Weniger Fehlerquellen (kein manuelles Hochladen/Zuordnen).
  - Grundlage für spätere Live-Auswertungen.
* **Ausgangslage:**
  - Ohne dieses Feature kommen Messwerte per **CSV-Upload** (`MesswerteService.processCsvUpload`, Spalten `zeit,total,zev` je Einheit, inkl. KI-gestütztem Einheiten-Matching). `zev_calculated` wird separat durch die **Solarverteilung** (`SolarDistribution`) berechnet.
  - Die Zähler bei Hene werden über einen **Raspberry Pi** ausgelesen (15× Wago über das Kommunikationsmodul WAGO 879-9000, der BKW-Bilanzzähler über WhatWatt Go, die Batterie am Wechselrichter – alle per Modbus TCP); vor MQTT wurden die Daten per **SFTP** bereitgestellt. Topologie: `docs/Netzwerk-Topologie-Hene.md`.
  - Topologie: Der Pi ist MQTT-**Publisher**, der **Broker (Mosquitto) läuft auf dem NAS** (Variante B der Topologie-Doku), das ZEV-Backend ist **Subscriber**.
  - **Nachbarhaus:** Das Nachbarhaus wird in den ZEV bei Hene integriert. Seine Smartmeter tragen je einen **gPlugK**, der selbst per MQTT publiziert. Die gPlugs brauchen **keine Pi-Gateway-Software**: Sie publizieren im LAN an einen Mosquitto auf dem Pi, der `gPlug/#` per Bridge an den Broker auf dem NAS weiterleitet, und senden ihr eigenes Meldungsformat auf ein **zweites Topic** (`gPlug/…`), das das Backend zusätzlich abonniert (FR-10).

> **Designentscheidung – absolute Zählerstände statt Deltas:** Der Pi überträgt die **absoluten (kumulativen) Zählerstände**, nicht berechnete Deltas. Die **Delta-/Intervall-Berechnung erfolgt im Backend**. Vorteil: **verlusttolerant** — geht eine MQTT-Nachricht verloren, bleibt die Gesamtsumme korrekt (die Differenz zweier empfangener Stände erfasst den Verbrauch dazwischen weiterhin vollständig); es sinkt nur kurz die zeitliche Auflösung. Dadurch entfallen strenge QoS-/Pufferungs-Anforderungen (kein zwingender Store-and-Forward-Bridge, Variante C).

> **Rollentrennung (siehe Topologie-Doku):** Ein MQTT-Broker verteilt nur Nachrichten und liest selbst keine Zähler. Das Auslesen (Modbus/gPlug) + Publish der Zählerstände macht ein **separater Reader-/Publisher-Prozess auf dem Pi** (`Specs/Pi-Gateway-Software.md`) — nicht Teil dieses Backend-Features (Out of Scope).

## 2. Funktionale Anforderungen (FR)

### FR-1: MQTT-Broker-Anbindung (Subscriber)
1. Das ZEV-Backend verbindet sich beim Start automatisch zum konfigurierten Broker (Mosquitto auf dem NAS).
2. Verbindungsparameter (URL, Username, Passwort, Client-ID, QoS) über `application.yml` / Environment — **keine Secrets im Code**.
3. Subscribe mit Wildcard auf alle Messwert-Topics (FR-2) und auf die gPlug-Topics (FR-10) – über **dieselbe** Verbindung und Sitzung (eine Client-ID).
4. Bei Verbindungsabbruch automatischer Reconnect mit Exponential Backoff.
5. **Dauerhafte Sitzung** (`mqtt.clean-session`, Standard `false`, Env `MQTT_CLEAN_SESSION`): Der Broker behält das Abo des Backends über dessen Neustart hinweg und stellt die QoS-1-Nachrichten zu, die in der Zwischenzeit eintrafen. So bleibt bei einem Update auch die 15-Minuten-Auflösung erhalten, nicht nur die Energie. Voraussetzung: Die Client-ID wird je Broker von **genau einer** Backend-Instanz verwendet; der Broker begrenzt die Warteschlange (`max_queued_messages`, siehe `docs/Netzwerk-Topologie-Hene.md`).
6. **Log-Meldung nach einem Neustart ist erwartet:** Hat der Broker Nachrichten gepuffert, erscheint beim Verbinden `Error subscribing to [zev/+/+/messwert] – Timed out waiting for a response from the server (32000)`. Sie ist harmlos: Spring wartet im Paho-Thread, der auch die eintreffenden Nachrichten verarbeitet, auf die Bestätigung des Abos (SUBACK); der Broker stellt aber zuerst die gepufferten Nachrichten zu, die Bestätigung kommt erst nach Ablauf der Wartezeit (`setCompletionTimeout(5000)`) durch. Das Abo besteht trotzdem, weil es in der Sitzung liegt – und gepufferte Nachrichten gibt es nur bei bestehender Sitzung. Ohne Sitzung (erster Start, abgelaufen, geleertes Broker-Volume) ist nichts gepuffert, und das Abo wird sofort bestätigt.
   > Eine längere Wartezeit hilft nicht, und das Abo ausserhalb des Paho-Threads abzusetzen, wäre für eine harmlose Meldung zu viel Eingriff (Hintergrund: Anhang A.4).

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
* Der Pi adressiert die Einheit über den **Messpunkt** (physisch bekannt), nicht über die interne `einheit.id`. Das Backend löst die Einheit über **(`org_id`, `messpunkt`)** auf. Ein Messpunkt kann **mehrere** Einheiten treffen: `BEZUG` und `RUECKLIEFERUNG` dürfen sich einen Bilanzmesspunkt teilen, die Meldung wird dann je Einheit auf ihr Register projiziert (`Specs/Bilanzmesspunkt.md`, FR-2.3/2.4). Eindeutig erzwungen ist der Messpunkt nur für Ladestationen (`V104`), die der Ingest ausschliesst (Anhang A.6).
* `{orgId}` ist das interne `org_id` (`BIGINT`), konsistent mit `messwerte`, `zaehler_rohdaten` und der Pi-Config; kein UUID-Mapping (Anhang A.2).

### FR-3: Payload-Format (JSON, absolute Zählerstände)
Der Pi publiziert die **absoluten kumulativen Zählerstände** zum Messzeitpunkt:
```json
{
  "timestamp": "2026-06-19T14:30:00+02:00",
  "zaehlerstandBezug": 12345.678,
  "zaehlerstandEinspeisung": 4321.000,
  "zustand": { "soc": 87.5 },
  "seriennummer": "WAGO-8791234"
}
```
| Feld | Typ | Pflicht | Beschreibung |
|------|-----|---------|--------------|
| `timestamp` | ISO 8601 (lokale Zeit mit Offset) | Ja | Zeitpunkt der Messung, z. B. `2026-06-19T14:30:00+02:00` |
| `zaehlerstandBezug` | Decimal (kWh, kumulativ) | Ja | Absoluter Zählerstand Bezug/Verbrauch (monoton steigend), Wirkenergie **OBIS 1.8.0** |
| `zaehlerstandEinspeisung` | Decimal (kWh, kumulativ) | Ja | Absoluter Zählerstand Einspeisung (monoton steigend), Wirkenergie **OBIS 2.8.0** |
| `zustand` | Objekt: Grössenname → Zahl | **Nein** | **Momentanwerte** des Geräts, z. B. `{"soc": 87.5}` (`Specs/Gerätezustand.md`). Wird nur gesendet, wenn konfiguriert — **nie als leeres Objekt**. Welche Grössen es gibt, entscheidet das Backend; ein unbekannter Name wird dort verworfen, ohne die Nachricht zu kosten |
| `seriennummer` | String (max. 64) | **Nein** | Seriennummer des liefernden Zählers (aus der Pi-Config je `messpunkt`); Signal für die **Zählertausch-Erkennung** (`Specs/Zaehlertausch-Erkennung.md`). Wird nur gesendet, wenn konfiguriert; fehlt sie, gilt das bisherige Verhalten |

> Übertragen wird nur die **Wirkenergie** – keine Leistung, Blind- oder Scheingrössen (siehe `Specs/Pi-Gateway-Software.md`).

> **`zustand` ist kein Zählerstand** und wird nirgends aggregiert. Die Pflichtfelder sind kumulativ,
> das Backend bildet daraus Deltas je 15-Minuten-Intervall; ein Ladezustand beschreibt einen
> Zeitpunkt. Er landet in einer eigenen Tabelle und rührt weder Aggregation noch Abrechnung an.
>
> **Ein ungültiger Wert darin verwirft die Nachricht nicht.** Die Zählerstände sind die Hauptsache;
> der Zustandswert wird verworfen, gemeldet — und die Energiemengen gespeichert.

> **Vertrags-Regel:** Die **Pflichtfelder** sind bindend und dürfen sich nicht ändern. **Additive
> optionale Felder** (wie `seriennummer`) sind erlaubt: Der Backend-Parser toleriert unbekannte
> Felder, daher ist die Deploy-Reihenfolge Pi/Backend in beide Richtungen unkritisch.

> **Zeitzone (Wire = lokale Zeit mit Offset, Speicherung verbatim):** Der Pi sendet `timestamp`
> als **lokale Zeit mit UTC-Offset** (ISO 8601, z. B. `2026-06-19T14:30:00+02:00`) – eindeutig
> (kein DST-Doppelstunden-Problem). Das Backend übernimmt die lokale Wanduhrzeit **verbatim**
> (`OffsetDateTime.toLocalDateTime()`, ohne Zeitzonen-Umrechnung) und speichert sie in
> `zaehler_rohdaten.zeit` – konsistent mit der `messwerte`-Tabelle/dem CSV-Upload (naive lokale
> Zeit). Der Ingest ist damit **unabhängig** von der Backend-Zeitzone. Der Aggregations-Job nutzt
> `LocalDateTime.now()`; dafür muss der Container/JVM in der lokalen Zone laufen
> (`TZ=Europe/Zurich`, in den `docker-compose*.yml` gesetzt), damit `now()` auf demselben
> Raster liegt wie die gespeicherten Zeitstempel.

> Die Stände sind **kumulativ und monoton** (Ausnahme: Zähler-Reset/-tausch, siehe Aggregation/Edge Cases). Die Abbildung auf `messwerte` regelt FR-6.4, den `zev`-Fallback FR-9.

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
    seriennummer              VARCHAR(64),   -- ergänzt mit V92, Specs/Zaehlertausch-Erkennung.md
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
4. **Mapping auf `messwerte`** (`zeit`, `total`, `einheit`, `org_id`) gemäss Entscheidung (Anhang A.2):
   * `zeit` = **Beginn** des Intervalls, Ortszeit (Europe/Zurich) ohne Zone — der Wert für
     10:00–10:15 steht unter `10:00`, wie beim CSV-Import (`Specs/Messwerte-Zeitkonvention.md`,
     FR-1; Hintergrund: Anhang A.3).
   * `total` = **vorzeichenbehafteter Netto-Wert**: `total = ΔBezug − ΔEinspeisung`. Bezug überwiegt → **positiv** (Verbrauch/Consumer); Einspeisung überwiegt → **negativ** (Produktion/Producer). Producer vs. Consumer wird also **über das Vorzeichen** unterschieden, ohne `einheit.typ`-Verzweigung.
   * `zev`:
     * **Consumer** (`einheit.typ = CONSUMER`): **0** (Sentinel „nicht gemessen"; wird nicht aus MQTT befüllt). Die Solarverteilung ersetzt `0` später durch `zev_calculated` (FR-9).
     * **Producer** (`einheit.typ = PRODUCER`): **`zev = total`** beim Ingest — vorläufig die gesamte Produktion des Intervalls. Die Solarverteilung ersetzt ihn danach durch die **im ZEV verbrauchte** Produktion (FR-9.4); die Statistik weist so die Produktion korrekt aus (Hintergrund: Anhang A.8).
     * **Keine** Tabellen-/Constraint-Änderung nötig – `messwerte.zev` bleibt `NOT NULL`.
     * Dies ist die **einzige** typ-abhängige Verzweigung im Ingest; die `total`-Bildung bleibt vorzeichenbasiert ohne `einheit.typ`-Verzweigung.
   * `zev_calculated` = **NULL** beim Ingest; wird erst durch die **Solarverteilung** (`SolarDistribution`) berechnet. Diese setzt anschliessend `zev = zev_calculated`, sofern `zev = 0` (FR-9).
5. Speichern mit `quelle = 'MQTT'` (FR-7); Insert/Upsert. Liegt für Einheit und `zeit` bereits ein CSV-Wert vor: Warnung loggen, **MQTT hat Vorrang** (Anhang A.2).
6. Verarbeitete Rohdaten markieren: `verarbeitet = TRUE`, `verarbeitet_am = NOW()`; Referenzstand fortschreiben.
7. **Unmittelbar nach der Aggregation** wird **je Mandant (`org_id`)** die **Solarverteilung** (FR-9) für den **behandelten Zeitraum** ausgeführt — d.h. für die Spanne `[frühester Intervall-Beginn … spätester Intervall-Beginn]` des jeweiligen Mandanten (die Stempel der erzeugten Messwerte, `Specs/Messwerte-Zeitkonvention.md`, FR-2) (echte Spanne, auch wenn nur ein 15-Minuten-Intervall verarbeitet wurde). Dadurch tragen die frisch aggregierten Messwerte sofort ihr `zev_calculated` (und – wo `zev = 0` – `zev`), ohne manuellen Anstoss über die UI.
   * Aufruf im `ZaehlerAggregationService` über `MesswerteService.calculateSolarDistributionForOrg(orgId, von, bis, algorithmus)`. Da **kein JWT/Request-Kontext** vorliegt, wird der Hibernate-`orgFilter` **explizit** mit der `org_id` versorgt (nicht aus `OrganizationContextService`); **kein** Fortschritts-Tracking.
   * **Algorithmus:** **`PROPORTIONAL`** (Verbraucher erhalten proportional zu ihrem Verbrauch); keine mandantenspezifische Wahl.
   * Fehler der Verteilung werden **pro Mandant** geloggt und brechen die Aggregation der übrigen Mandanten nicht ab.

> **Profil-Aktivierung:** Der `@Scheduled`-Aggregations-Job samt Service ist wie der Subscriber nur mit Profil `mqtt` aktiv (FR-1). Ohne Profil läuft **kein** Aggregations-Job; die `messwerte`-Tabelle wird ausschliesslich über den CSV-Upload befüllt.

> **Verlusttoleranz:** Fehlt in einem Intervall eine Meldung, fällt der Verbrauch ins nächste Intervall mit Meldung — die **Gesamtsumme bleibt korrekt**, nur die zeitliche Auflösung sinkt kurzzeitig. Das ist der zentrale Vorteil absoluter Stände gegenüber Deltas.

### FR-7: Erweiterung der `messwerte`-Tabelle (Quelle)
```sql
ALTER TABLE zev.messwerte ADD COLUMN IF NOT EXISTS quelle VARCHAR(20) DEFAULT 'CSV';
-- Mögliche Werte: 'CSV', 'MQTT', 'API'
```
Bestehende Zeilen erhalten per Default `'CSV'` (rückwärtskompatibel). **Keine** Änderung an `messwerte.zev` nötig (`zev`-Werte: FR-6.4, Fallback: FR-9); alle bestehenden Konsumenten von `messwerte.zev` (Statistik/Rechnung) bleiben unverändert kompatibel.

### FR-8: Monitoring & Status
1. Prometheus-Metriken (bestehende Infrastruktur, siehe `Specs/Metriken.md` / `MetricsService`):
   - `zev_mqtt_messages_received_total`, `..._processed_total`, `..._failed_total` (Counter)
   - `zev_mqtt_last_message_timestamp` (Gauge)
   - `zev_aggregation_runs_total` (Counter), `zev_aggregation_last_run_timestamp` (Gauge)
2. Health-Indicator `/actuator/health/mqtt` zeigt den Broker-Verbindungsstatus.
3. **Zähler-Ausfälle als Systemmeldung** (`SystemmeldungService`, Kategorie `SYSTEMMELDUNG_KATEGORIE_MQTT`): Der Aggregations-Job erkennt fehlende Zählerdaten am Abstand zwischen Referenz-Stand (letzter Stand vor dem Intervall) und Intervallstart und meldet sie mandantenbezogen:
   * **Genau ein** Aggregationsintervall ohne Daten → **`INFO`** (`MQTT_ZAEHLER_LUECKE`). Der Verbrauch geht nicht verloren – er fällt gebündelt in das Folgeintervall.
   * **Mehrere** aufeinanderfolgende Intervalle ohne Daten → **`WARN`** (`MQTT_ZAEHLER_AUSFALL`). Hier geht die 15-Minuten-Auflösung verloren: der gesamte Lückenverbrauch erscheint als Spitze in einem Intervall und **verzerrt die intervallweise Solarverteilung** zwischen den Consumern (die Einzelsumme des Zählers bleibt korrekt).
   * **Keine** Meldung bei einzelnen ausgefallenen Messungen *innerhalb* eines Intervalls (z.B. 1 von 3 bei `publish_interval: 5m`): Da **absolute** Zählerstände übertragen werden, ist das Delta identisch – der Ausfall ist folgenlos und darf kein Rauschen erzeugen.
   * Meldungstext/Kategorie via `TranslationService`; der Parameter nennt Einheit, Zeitraum und Anzahl fehlender Intervalle. Deduplizierung je Mandant + Meldungs-Key durch den bestehenden Zähler-Mechanismus (`erfasse`); **kein** Auto-Resolve – die Meldung dokumentiert ein vergangenes Ereignis und wird vom Betreiber als erledigt markiert.

4. **Rücksprung eines Zählerstands als Systemmeldung** (`MQTT_ZAEHLER_RUECKSPRUNG`, **`WARN`**): Ein Zählerstand kann nicht sinken. Sinkt er doch — Zählertausch ohne Seriennummernwechsel, Überlauf, fehlerhafte Übertragung —, wird das Delta des betroffenen Registers auf `0` gesetzt.
   * **Die Energie dieses Intervalls geht dabei verloren**, anders als bei einer Datenlücke: Dort fällt der Verbrauch gebündelt ins Folgeintervall, hier fehlt er dauerhaft.
   * Bei einem `PRODUCER` sinkt damit die **ausgewiesene Produktion**, ohne dass der Anlage etwas fehlt. Genau das ist der Grund für die Meldung: Bis dahin stand der Fall nur in einer Logzeile und war im Betrieb unsichtbar — eine zu tief erscheinende Produktion liess sich nicht davon unterscheiden, dass die Anlage wirklich weniger lieferte.
   * Der Parameter nennt Einheit, Register, Intervall und die **verworfene Menge**. Ohne den Betrag liesse sich nicht abschätzen, ob es um Rundung oder um Kilowattstunden geht.
   * Eine fehlgeschlagene Meldung bricht die Aggregation **nicht** ab — dieselbe Abwägung wie beim Zählerwechsel.

### FR-9: Anpassung Solarverteilung (`zev`-Fallback)
1. Die Solarverteilung (`SolarDistribution`) berechnet wie bisher `zev_calculated` pro Consumer/Intervall.
2. **Neu:** Nach der Berechnung wird bei Consumern `zev = zev_calculated` gesetzt, **sofern `zev = 0`** (Sentinel für „nicht gemessen", von MQTT-importierten Consumer-Werten). Bereits gesetzte `zev`-Werte von Consumern (z. B. aus CSV) bleiben unverändert.
3. Dadurch tragen MQTT-Werte nach der Verteilung denselben `zev`-Wert wie `zev_calculated`; CSV-Werte behalten ihren gemessenen Anteil.
4. **Producer:** Bei MQTT-Producern (`quelle = 'MQTT'`) setzt die Verteilung `zev` auf die **tatsächlich verteilte Menge** des Zeitpunkts (negativ; bei mehreren Producern anteilig nach Produktion). Die Algorithmen kappen die Zuteilung am Verbrauch, der Rest ist Rücklieferung. CSV-Producer behalten ihren gemessenen `zev`.

> **Kante:** Ein *gemessener* Wert von genau `zev = 0` (z. B. Nachtstunde ohne ZEV-Bezug aus CSV) würde ebenfalls durch `zev_calculated` ersetzt. Da `zev_calculated` in solchen Fällen ohnehin ~0 ist, ist der Effekt vernachlässigbar; falls doch relevant, kann die Unterscheidung über `quelle = 'MQTT'` statt über `zev = 0` erfolgen (Umsetzungsdetail).

### FR-10: gPlug-Anbindung (zweites Topic)
Die Smartmeter im Nachbarhaus tragen einen **gPlugK** (Auslesekopf für die Kundenschnittstelle des Smartmeters, Firmware auf Tasmota-Basis). Er publiziert die Zählerdaten selbst per MQTT. Das Backend liest diese Meldungen **direkt** – ohne Pi-Gateway-Software und ohne Umformatierung. Weil der gPlug weder VPN noch TLS beherrscht, erreicht er den NAS-Broker über einen **Mosquitto auf dem Pi**, der nur `gPlug/#` per Bridge weiterleitet und die Meldungen nicht verändert (`docs/Netzwerk-Topologie-Hene.md`).

> **Grundsatz:** Auf dem gPlug wird **nur die MQTT-Anbindung konfiguriert** (Host, Port, Benutzer, Passwort, Topic, FullTopic, Sendeintervall). An der Meldungsstruktur und am Ausleseskript ändert sich **nichts**. Die Übersetzung in das interne Format leistet das Backend.
>
> Darum trägt der gPlug den Mandanten und den Messpunkt im **Topic** statt im Payload: Das Topic ist Konfiguration, der Payload wäre ein Eingriff ins Skript.

1. **Konfiguration am gPlug** (Tasmota-Konsole oder Web-Oberfläche „Configure MQTT“):
   | Einstellung | Wert | Bemerkung |
   |-------------|------|-----------|
   | Host / Port | Broker auf dem Pi (LAN-IP des Pi), `1883` | Bridge zum NAS, siehe oben |
   | User / Password | eigener Broker-User auf dem Pi-Broker, je gPlug oder für alle gPlugs eines Hauses | nie der Backend- oder Pi-Gateway-User (NFR-2) |
   | Topic (`%topic%`) | **`messpunkt`** der Einheit, z. B. `ID742-Nachbar-1` | ohne `/`, `+`, `#` und Leerzeichen |
   | FullTopic | **`gPlug/{orgId}/%topic%/%prefix%/`**, z. B. `gPlug/42/%topic%/%prefix%/` | `{orgId}` ist hier ein fester Text |
   | TelePeriod | **`60`** (Sekunden) | Takt der `SENSOR`-Meldung, Begründung unten |
2. **Topic-Struktur:** Daraus entsteht für die Zählermeldung
   ```
   gPlug/{orgId}/{messpunkt}/tele/SENSOR
   ```
   | Segment | Beschreibung | Beispiel |
   |---------|--------------|----------|
   | `gPlug` | Root-Namespace der gPlug-Meldungen (Gross-/Kleinschreibung beachten) | `gPlug` |
   | `{orgId}` | Mandanten-ID (`org_id`) wie in FR-2 | `42` |
   | `{messpunkt}` | Messpunkt der Einheit (`einheit.messpunkt`) = Tasmota-Topic | `ID742-Nachbar-1` |
   | `tele` | Tasmota-Präfix für periodische Meldungen | `tele` |
   | `SENSOR` | Meldung mit den Zählerwerten | `SENSOR` |

   * Das Backend abonniert `gPlug/+/+/tele/SENSOR` (Konfiguration `mqtt.topic-gplug`, Env `MQTT_TOPIC_GPLUG`; leer = kein gPlug-Abo).
   * Die übrigen Tasmota-Meldungen (`tele/STATE`, `tele/LWT`, `tele/INFO…`, `stat/…`) werden **nicht** abonniert.
3. **Meldungsstruktur des gPlug** (Tasmota-`SENSOR`-Meldung; das Objekt `z` liefert das Ausleseskript des gPlug):
   ```json
   {
     "Time": "2026-10-20T14:30:00",
     "z": {
       "SMid": "12345678",
       "Pi": 0.512, "Po": 0.000,
       "Pi1": 0.170, "Pi2": 0.210, "Pi3": 0.132,
       "I1": 0.80, "I2": 0.95, "I3": 0.61,
       "U1": 231, "U2": 230, "U3": 232,
       "Ei": 12345.678, "Eo": 4321.000,
       "Ei1": 8000.123, "Ei2": 4345.555, "Eo1": 3000.000, "Eo2": 1321.000
     }
   }
   ```
   | Feld | Bedeutung | Einheit | Verwendung im Backend |
   |------|-----------|---------|------------------------|
   | `Time` | Zeitpunkt der Meldung, **lokale Zeit ohne Offset** (Uhr des gPlug, per NTP) | ISO 8601 | → `zeit` (verbatim, wie FR-3) |
   | `z.Ei` | Wirkenergie Bezug kumulativ (OBIS 1.8.0) | kWh | → `zaehlerstand_bezug` |
   | `z.Eo` | Wirkenergie Einspeisung kumulativ (OBIS 2.8.0) | kWh | → `zaehlerstand_einspeisung`; fehlt es, gilt `0` (Zähler ohne Einspeiseregister) |
   | `z.SMid` | Kennung des Smartmeters | – | → `seriennummer` (Zählertausch-Erkennung, `Specs/Zaehlertausch-Erkennung.md`), gekürzt auf 64 Zeichen; fehlt es, gilt das bisherige Verhalten |
   | `z.Ei1`/`z.Ei2`, `z.Eo1`/`z.Eo2` | Bezug/Einspeisung je Tarif (Hoch-/Niedertarif) | kWh | nicht verwendet (die Summe steht in `Ei`/`Eo`) |
   | `z.Pi`/`z.Po`, `z.Pi1..3`/`z.Po1..3` | Momentanleistung Bezug/Einspeisung gesamt bzw. je Phase | kW | nicht verwendet |
   | `z.I1..3`, `z.U1..3` | Strom bzw. Spannung je Phase | A bzw. V | nicht verwendet |

   * Welche Felder ein gPlug liefert, hängt vom Smartmeter ab. Pflicht sind nur `Time` und `z.Ei`. Unbekannte und nicht verwendete Felder werden ignoriert (Vertrags-Regel wie FR-3).
   * Die Feldnamen entsprechen der gPlug-Dokumentation. Vor der Umsetzung ist eine **echte Meldung** aus dem Nachbarhaus mitzuschneiden (`mosquitto_sub -v -t 'gPlug/#'`) und gegen die Tabelle zu prüfen (Offene Fragen).
4. **Verarbeitung:** Das Backend übersetzt die gPlug-Meldung in die interne Form von FR-3 (`timestamp`, `zaehlerstandBezug`, `zaehlerstandEinspeisung`, `seriennummer`; kein `zustand`). Ab dort läuft **derselbe Weg** wie beim Pi: Einheiten über (`org_id`, `messpunkt`) auflösen, Ladestationen ausschliessen, Bilanz-Typen auf ihr Register projizieren, Upsert in `zaehler_rohdaten` (FR-4, FR-5), dieselben Metriken (FR-8.1).
   * **Keine** neue Tabelle, **keine** Migration. Aggregation (FR-6), Ausfall- und Rücksprungmeldungen (FR-8.3, FR-8.4) und Solarverteilung (FR-9) sehen keinen Unterschied zwischen Pi- und gPlug-Rohdaten. Die aggregierten Messwerte tragen `quelle = 'MQTT'`.
   * Die Zeit hat keinen Offset: Sie wird als lokale Wanduhrzeit **verbatim** übernommen, genau wie der Pi-Zeitstempel nach `toLocalDateTime()`.
5. **Plausibilität** zusätzlich zu FR-4.2:
   * `Time` vor dem **01.01.2020** → WARN, verwerfen. Tasmota sendet vor der ersten NTP-Synchronisation eine Zeit ab 1970.
   * `z.Ei` **und** `z.Eo` beide `0` → WARN, verwerfen. Nach einem Neustart meldet der gPlug Nullwerte, bis das erste Telegramm des Smartmeters gelesen ist.
     > **Warum verwerfen statt speichern:** Ein solcher Nullstand zwischen zwei echten Ständen löste zuerst einen Rücksprung aus (Delta 0, FR-6.3) und im nächsten Intervall einen Sprung um den **ganzen Zählerstand**, der als Verbrauch verbucht würde.
6. **Sendetakt:** `TelePeriod 60` liefert etwa eine Meldung pro Minute, wie der Pi. Häufigere Meldungen verbessern nichts, weil das Backend auf 15 Minuten aggregiert. Sie vervielfachen aber `zaehler_rohdaten` und die Broker-Warteschlange (NFR-3). Seltenere Meldungen (bis 300 s) sind zulässig. Ab etwa 900 s fallen einzelne Intervalle leer aus und lösen `MQTT_ZAEHLER_LUECKE` aus (FR-8.3).

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

**MQTT-Empfang:**
* [x] Backend verbindet sich beim Start automatisch zum Broker und abonniert `zev/+/+/messwert`. (Test: MqttConfigTest#mqttInbound_AbonniertKonfiguriertesTopicMitQos, #applicationMqttYml_OhneUmgebungsvariable_TopicDefault)
* [x] Gültige Nachricht → Zeile in `zaehler_rohdaten` mit korrektem `org_id` + `einheit_id` (aufgelöst über `messpunkt`) und den absoluten Ständen. (Test: MqttIngestServiceTest#handle_ValidMessage_SavesRohdaten)
* [x] Ungültiges JSON / fehlende Pflichtfelder → WARN, kein Insert. (Test: MqttIngestServiceTest#handle_InvalidJson_Discarded, #handle_EmptyPayload_Discarded, #handle_MissingTimestamp_Discarded, #handle_MissingBezug_Discarded, #handle_MissingEinspeisung_Discarded)
* [x] Unbekannter Messpunkt → abgelehnt (WARN, kein Insert). (Test: MqttIngestServiceTest#handle_UnknownMesspunkt_Discarded)
* [x] `orgId` passt nicht zur Einheit → verworfen wie ein unbekannter Messpunkt (WARN, kein Insert) — Mandanten-Isolation gewährleistet. (Test: ZaehlerRohdatenRepositoryIT#findAllByOrgIdAndMesspunkt_WrongOrg_ReturnsEmpty; Test: MqttIngestServiceTest#handle_UnknownMesspunkt_Discarded)
* [x] Negativer Zählerstand → verworfen. (Test: MqttIngestServiceTest#handle_NegativeBezug_Discarded, #handle_NegativeEinspeisung_Discarded)
* [x] Duplikat (Einheit + `zeit`) → Upsert. (Test: MqttIngestServiceTest#handle_DuplicateEinheitAndZeit_UpdatesInsteadOfInsert)
* [x] Nach Broker-Abbruch automatischer Reconnect. (Test: MqttConfigTest#mqttClientFactory_AutomatischerReconnektAktiv)

**15-Minuten-Aggregation:**
* [x] Job läuft um :05, :20, :35, :50; verarbeitet werden die quartalsgenauen Intervalle (:00–:15, :15–:30, …); `ΔBezug`/`ΔEinspeisung` je Register korrekt als **Differenz** gebildet. (Test: ZaehlerAggregationServiceTest#aggregiere_Zeitplan_FuenfMinutenNachJederViertelstunde, #aggregiere_NeuerMesswert_ZeitIstIntervallbeginn, #aggregiere_MarkiertRohdatenBisIntervallende, #aggregiere_BezugUeberwiegt_TotalPositiv, #aggregiere_MehrereIntervalle_VerteilfensterVonFruehestemBisSpaetestemBeginn)
* [x] `total = ΔBezug − ΔEinspeisung` korrekt vorzeichenbehaftet (Bezug → positiv, Einspeisung → negativ). (Test: ZaehlerAggregationServiceTest#aggregiere_BezugUeberwiegt_TotalPositiv, #aggregiere_EinspeisungUeberwiegt_TotalNegativ)
* [x] `zev` wird beim MQTT-Ingest für **Consumer** auf `0` gesetzt (Sentinel), für **Producer** auf `total`; `messwerte.zev` bleibt `NOT NULL`. (Test: ZaehlerAggregationServiceTest#aggregiere_BezugUeberwiegt_TotalPositiv, #aggregiere_Producer_ZevGleichTotal)
* [x] Für eine **Producer**-Einheit gilt nach der Aggregation `zev = total`; die Solarverteilung ersetzt ihn bei MQTT-Werten durch die im ZEV verbrauchte Menge, CSV-Producer bleiben unverändert. (Test: ZaehlerAggregationServiceTest#aggregiere_Producer_ZevGleichTotal; Test: MesswerteServiceTest#calculateSolarDistribution_MqttProducer_ZevIstVerteilteMenge, #calculateSolarDistribution_CsvProducer_ZevBleibtGemessenerWert)
* [x] Aggregierte Werte in `messwerte` mit `quelle = 'MQTT'`. (Test: ZaehlerAggregationServiceTest#aggregiere_BezugUeberwiegt_TotalPositiv)
* [x] Verarbeitete Rohdaten `verarbeitet = TRUE`; bereits verarbeitete werden nicht erneut aggregiert. (Test: ZaehlerAggregationServiceTest#aggregiere_ProcessedInterval_MarkiertRohdatenVerarbeitet, ZaehlerRohdatenRepositoryIT#markVerarbeitet_MarksRowsUpToBoundary, ZaehlerRohdatenRepositoryIT#findEinheitIdsWithUnverarbeitet_ReturnsOnlyUnprocessed)
* [x] Leeres Intervall (keine neue Meldung) → kein `messwerte`-Eintrag (kein Nullwert). (Test: ZaehlerAggregationServiceTest#aggregiere_LeeresIntervall_KeinEintrag)
* [x] Verlorene Zwischen-Nachricht → Gesamtsumme über die betroffenen Intervalle bleibt korrekt (verlusttolerant). (Test: ZaehlerAggregationServiceTest#aggregiere_EinIntervallOhneDaten_ErfasstInfoMeldung)
* [x] **Wiederaufnahme nach mehrtägigem Unterbruch** → die Differenz zum letzten Vor-Unterbruch-Stand deckt den gesamten Zeitraum; **keine kWh gehen verloren**, der Gesamtbetrag erscheint gebündelt im ersten Intervall mit Meldung nach Wiederaufnahme (Referenz wird unabhängig vom `verarbeitet`-Flag über den Zeitstempel aufgelöst). (Test: ZaehlerAggregationServiceTest#aggregiere_WiederaufnahmeNachUnterbruch_GesamterVerbrauchImErstenIntervall, ZaehlerRohdatenRepositoryIT#findFirstByZeitLessThanEqual_ReturnsLatestAtOrBefore)
* [ ] Zähler-Reset/Rücksprung **pro Register** (`ΔBezug` bzw. `ΔEinspeisung` < 0) → betroffenes Delta auf 0, Referenz neu gesetzt, WARN **und Systemmeldung `MQTT_ZAEHLER_RUECKSPRUNG` mit der verworfenen Menge**. (Ein negatives `total` aus Einspeisung ist dagegen legitim und wird übernommen.) (Test: ZaehlerAggregationServiceTest#aggregiere_RegisterRuecksprung_DeltaAufNull, #aggregiere_RegisterRuecksprungBezug_ErfasstWarnSystemmeldungMitVerworfenerMenge, #aggregiere_RegisterRuecksprungEinspeisung_MeldetRegisterEinspeisung, #aggregiere_RuecksprungBeiderRegister_ZweiMeldungenTotalNull, #aggregiere_KeinRuecksprung_KeineRuecksprungMeldung, #aggregiere_RuecksprungMeldungSchlaegtFehl_MesswertWirdTrotzdemGespeichert, #aggregiere_EinspeisungUeberwiegt_TotalNegativ)
* [x] `zev_calculated` wird durch den Ingest nicht verändert; Solarverteilung funktioniert unverändert. (Test: ZaehlerAggregationServiceTest#aggregiere_BezugUeberwiegt_TotalPositiv, #aggregiere_Producer_ZevGleichTotal)
* [x] Nach der Solarverteilung gilt `zev = zev_calculated`, wo `zev = 0` war (MQTT-Werte); gemessene CSV-Werte (`zev ≠ 0`) bleiben unverändert (FR-9). (Test: MesswerteServiceTest#calculateSolarDistribution_CsvConsumer_ZevBleibtGemessenZevCalculatedGesetzt, #calculateSolarDistribution_Bilanz_CsvConsumer_ZevBleibtGemessenZevCalculatedGesetzt, #calculateSolarDistribution_Bilanz_VerteiltSMaxConsumerMinusBezug)
* [x] Nach jedem Aggregationslauf wird je betroffenem Mandant die Solarverteilung für den behandelten Zeitraum automatisch ausgeführt (`calculateSolarDistributionForOrg`, `orgFilter` explizit gesetzt); ohne verarbeitetes Intervall erfolgt kein Verteilungsaufruf. Ein Fehler bei einem Mandanten bricht die übrigen nicht ab. (Test: ZaehlerAggregationServiceTest#aggregiere_RuftSolarverteilungFuerBehandeltenZeitraum, #aggregiere_MehrereIntervalle_VerteilfensterVonFruehestemBisSpaetestemBeginn, #aggregiere_KeinMesswert_KeineSolarverteilung, #aggregiere_VerteilungWirftIllegalState_UebrigeMandantenLaufenWeiter, #aggregiere_VerteilungWirftRuntimeException_UebrigeMandantenLaufenWeiter, #aggregiere_FehlerNurBeiEinemMandant_AndererWirdVerteilt; Test: MesswerteServiceTest#calculateSolarDistributionForOrg_AktiviertOrgFilterFuerExpliziteOrg)

**Verbindung:**
* [x] Ohne `MQTT_CLEAN_SESSION` verbindet sich das Backend mit dauerhafter Sitzung (`cleanSession = false`); mit `MQTT_CLEAN_SESSION=true` ohne (Test: MqttConfigTest#applicationMqttYml_OhneUmgebungsvariable_DauerhafteSitzung, MqttConfigTest#mqttClientFactory_CleanSessionFalse_DauerhafteSitzung, MqttConfigTest#mqttClientFactory_CleanSessionTrue_SitzungOhnePuffer)

**gPlug (FR-10):**
* [ ] Mit gesetztem `mqtt.topic-gplug` abonniert das Backend zusätzlich `gPlug/+/+/tele/SENSOR` über dieselbe Verbindung; ist es leer, entsteht kein gPlug-Abo.
* [ ] Gültige gPlug-Meldung auf `gPlug/{orgId}/{messpunkt}/tele/SENSOR` → Zeile in `zaehler_rohdaten` mit `org_id` und `einheit_id` (über `messpunkt` aufgelöst), `zeit` = `Time` verbatim, `zaehlerstand_bezug` = `z.Ei`, `zaehlerstand_einspeisung` = `z.Eo`, `seriennummer` = `z.SMid`.
* [ ] Fehlt `z.Eo`, wird die Einspeisung als `0` gespeichert; fehlt `z.SMid`, bleibt `seriennummer` leer.
* [ ] Fehlt `Time` oder `z.Ei`, oder ist die Meldung kein JSON → WARN, kein Insert, Zähler `..._failed_total` steigt.
* [ ] `Time` vor dem 01.01.2020 → WARN, kein Insert.
* [ ] `z.Ei = 0` und `z.Eo = 0` → WARN, kein Insert.
* [ ] Unbekannter Messpunkt, fremde `orgId` und Ladestation werden wie bei der Pi-Meldung verworfen; ein geteilter Bilanzmesspunkt wird je Einheit auf ihr Register projiziert.
* [ ] Nicht verwendete Felder (Leistung, Strom, Spannung, Tarifregister, unbekannte Felder) verhindern das Speichern nicht.
* [ ] Aus gPlug-Rohdaten aggregierte Messwerte unterscheiden sich nicht von denen des Pi (`quelle = 'MQTT'`, gleiche Delta-, Lücken- und Rücksprunglogik).

**Monitoring & Kompatibilität:**
* [ ] MQTT-Metriken unter `/actuator/prometheus`; `/actuator/health/mqtt` zeigt Status. (Test: MqttMetricsTest#konstruktor_RegistriertAlleMetrikenGemaessFr81, #recordReceived_ErhoehtCounterUndSetztZeitstempel, #recordAggregationRun_ErhoehtCounterUndSetztZeitstempel; Test: MqttHealthIndicatorTest#health_AdapterLaeuft_Up, #health_AdapterGestoppt_Down)
* [x] **Genau ein** Intervall ohne Zählerdaten → Systemmeldung `MQTT_ZAEHLER_LUECKE` mit Level **INFO**, Kategorie `SYSTEMMELDUNG_KATEGORIE_MQTT`, Parameter mit Einheiten-Name und Zeitraum; der Messwert des Folgeintervalls enthält den gesamten Lückenverbrauch. (Test: ZaehlerAggregationServiceTest#aggregiere_EinIntervallOhneDaten_ErfasstInfoMeldung)
* [x] **Mehrere** aufeinanderfolgende Intervalle ohne Zählerdaten → Systemmeldung `MQTT_ZAEHLER_AUSFALL` mit Level **WARN**; der Parameter nennt die Anzahl fehlender Intervalle. (Test: ZaehlerAggregationServiceTest#aggregiere_MehrereIntervalleOhneDaten_ErfasstWarnMeldung)
* [x] **Keine** Systemmeldung, wenn der Referenz-Stand im unmittelbar vorangehenden Intervall liegt (lückenloser Betrieb) — insbesondere **nicht** bei einzelnen ausgefallenen Messungen innerhalb eines Intervalls. (Test: ZaehlerAggregationServiceTest#aggregiere_LueckenlosOhneAusfall_KeineSystemmeldung, #aggregiere_ReferenzMittenImVorangehendenIntervall_KeineSystemmeldung, #aggregiere_ReferenzKurzVorIntervallstart_KeineSystemmeldung)
* [x] Meldungen sind mandantenbezogen (`org_id` aus der Einheit) und werden je Meldungs-Key dedupliziert (Zähler + „zuletzt aufgetreten"); Texte via `TranslationService` (DE/EN). (Test: ZaehlerAggregationServiceTest#aggregiere_EinIntervallOhneDaten_ErfasstInfoMeldung, SystemmeldungServiceTest#erfasse_OffenerEintragVorhanden_ErhoehtZaehlerStattNeuemEintrag; Nachweis: V93__Add_Zaehler_Ausfall_Translations.sql, V151__Add_Zaehler_Ruecksprung_Translation.sql)
* [x] Bestehender CSV-Upload unverändert nutzbar; Werte beider Quellen gleichwertig. (Test: MesswerteServiceTest#processCsvUpload_ValidCsv_ReturnsSuccess, #processBilanzCsvUpload_ValidCsv_SavesBezugAndRuecklieferung, #calculateSolarDistribution_Bilanz_CsvProducer_ZevBleibtGemessen)

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Verarbeitung einer Nachricht < 100 ms; asynchron/non-blocking; der Aggregations-Job blockiert die MQTT-Verarbeitung nicht.

### NFR-2: Sicherheit & Mandantenfähigkeit
* **Kein Endbenutzer-UI / nicht rollenbasiert:** Der Ingest ist ein Backend-Hintergrunddienst (keine `zev_user`/`zev_admin`-Route, kein `@PreAuthorize`). Ein späteres Status-/Konfig-UI wäre über das Permission-Modell (`Specs/Composite-Roles.md`) zu schützen (Out of Scope).
* **Broker-Auth:** Username/Passwort (nur via Env), **ACLs pro Mandant** (Pi darf nur in seine `org_id` publizieren); getrennte Broker-User für Publisher (Pi) und Backend-Subscriber.
* **TLS (Port 8883)** empfohlen, zusätzlich zum VPN-Tunnel (Defense-in-Depth).
* **gPlug (FR-10):** eigener Broker-User auf dem Pi-Broker mit ACL nur auf seinen Mandanten, z. B. `topic write gPlug/42/+/tele/#` und `topic read gPlug/42/+/cmnd/#`; die Bridge meldet sich am NAS mit einem eigenen User an, der nur `gPlug/42/+/tele/#` schreiben darf. Der Backend-User darf `gPlug/+/+/tele/SENSOR` nur **lesen**; er publiziert nie auf `cmnd/…` (das wären Befehle an den gPlug). Die Standard-Firmware des gPlug spricht **kein TLS**: Er publiziert deshalb nur im lokalen Netz an den Pi-Broker; den Weg zum NAS übernimmt die Bridge über das VPN.
* **Mandanten-Isolation:** `org_id` aus dem Topic wird gegen die Einheit geprüft; Rohdaten/Messwerte tragen `org_id` und unterliegen dem Hibernate-`orgFilter`. Da kein JWT vorliegt, wird der Filter explizit mit dem topic-abgeleiteten `org_id` versorgt.

### NFR-3: Zuverlässigkeit
* **Verlusttoleranz durch absolute Zählerstände:** Eine verlorene Nachricht führt **nicht** zu Datenverlust (die Differenzbildung ist selbstheilend). Daher genügt **QoS 0/1**; ein aufwändiger Store-and-Forward-Puffer/Bridge (Variante C) ist **nicht erforderlich** (Broker auf dem NAS = Variante B genügt).
* **Neustart des Backends:** Die dauerhafte Sitzung (FR-1.5) lässt den Broker die Nachrichten puffern, solange das Backend fehlt. Nach dem Wiederverbinden kann eine Nachricht doppelt ankommen (QoS 1 = mindestens einmal); das ist unkritisch, siehe nächster Punkt.
* **gPlug publiziert mit QoS 0.** Mosquitto puffert QoS-0-Nachrichten für eine dauerhafte Sitzung nur mit `queue_qos0_messages true`. Ohne diese Einstellung gehen die gPlug-Meldungen während eines Backend-Neustarts verloren: Die Energie bleibt dank absoluter Stände erhalten, die 15-Minuten-Auflösung nicht (wie vor FR-1.5). Die Einstellung gehört in die `mosquitto.conf` auf dem NAS. `max_queued_messages` ist um die gPlug-Meldungen zu erhöhen (etwa 60 pro gPlug und Stunde).
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
| `orgId` passt nicht zur Einheit | WARN wie „Unbekannter Messpunkt“, verwerfen (kein Cross-Tenant-Write) |
| Negativer Zählerstand | WARN, verwerfen |
| Timestamp in der Zukunft | WARN, trotzdem speichern |
| Duplikat (Einheit + `zeit`) | Upsert |
| Verlorene Nachricht(en) | kein Datenverlust — Differenz zum nächsten Stand deckt die Lücke; nur Auflösungsverlust. **Monitoring (FR-8.3):** einzelne Messung innerhalb eines Intervalls → keine Meldung; ein Intervall ohne Daten → INFO; mehrere → WARN |
| Wiederaufnahme nach **längerem Unterbruch** (Pi/Broker offline, ggf. mehrere Tage) | **kein Datenverlust** — die Differenz zum letzten Vor-Unterbruch-Stand deckt den gesamten Zeitraum; der kumulierte Verbrauch fällt gebündelt als **ein** grosser `total` in das erste Intervall mit Meldung nach Wiederaufnahme (Auflösungsverlust, bewusster Trade-off der absoluten Stände). Der Referenzstand wird über `findFirstByEinheitIdAndZeitLessThanEqualOrderByZeitDesc` **unabhängig vom `verarbeitet`-Flag** gefunden; der Catch-up-Loop startet erst beim ersten unverarbeiteten Stand (die leeren Intervalle des Unterbruchs werden nicht durchlaufen). **Voraussetzung:** der Vor-Unterbruch-Stand existiert noch (Retention darf ihn nicht löschen, s. §8). |
| Register-Stand < Referenz (Reset/Überlauf/Zählertausch) | Aggregation **je Register**: betroffenes Delta (`ΔBezug`/`ΔEinspeisung`) auf 0, Referenz neu setzen, WARN (negatives `total` aus Einspeisung bleibt gültig) |
| Broker nicht erreichbar | Reconnect mit Exponential Backoff |
| Backend-Neustart (Update) | Broker puffert die Nachrichten in der dauerhaften Sitzung und stellt sie beim Wiederverbinden zu; die Intervalle bleiben vollständig. Läuft die Warteschlange über (`max_queued_messages`) oder startet auch der Broker neu (ohne `persistence`), gilt der Fall „Verlorene Nachricht(en)“ |
| DB nicht erreichbar | Nachricht nicht markieren, Retry |
| Keine neue Meldung im Intervall | kein `messwerte`-Eintrag |
| Job-Ausfall/Neustart | nächster Lauf verarbeitet alle unverarbeiteten Rohdaten |
| Bereits aggregierter `messwerte`-Eintrag | Upsert statt Doppel-Insert |
| Leere Zähler-/Einheitenliste | kein Fehler; nichts zu verarbeiten |
| gPlug: Uhr noch nicht synchronisiert (`Time` vor 2020) | WARN, verwerfen (FR-10.5) |
| gPlug: Nullwerte nach Neustart (`Ei = 0` und `Eo = 0`) | WARN, verwerfen (FR-10.5) |
| gPlug: `Time` ohne Offset in der doppelten Stunde der Umstellung auf Winterzeit | Upsert überschreibt den ersten Stand derselben Wanduhrzeit; die Energie bleibt dank absoluter Stände erhalten, die Auflösung dieser Stunde nicht (wie beim CSV-Import) |
| gPlug: falsches Tasmota-Topic (Messpunkt nicht angelegt) | wie „Unbekannter Messpunkt“: WARN, verwerfen |
| gPlug: Smartmeter getauscht | neue `SMid` → neue Baseline (`Specs/Zaehlertausch-Erkennung.md`) |
| gPlug: Backend-Neustart ohne `queue_qos0_messages` | Meldungen verloren, Fall „Verlorene Nachricht(en)“ (NFR-3) |

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:**
  - Einheiten (`einheit`) inkl. `messpunkt` müssen existieren (keine automatische Geräteregistrierung).
  - Mosquitto-Broker auf dem NAS betriebsbereit (siehe `docs/Netzwerk-Topologie-Hene.md`, Abschnitt „MQTT-Broker auf dem NAS betreiben").
  - VPN-Verbindung zwischen Pi und NAS; **separater Reader-/Publisher-Prozess** auf dem Pi (Out of Scope, `Specs/Pi-Gateway-Software.md`).
* **Neue Dependencies (Backend):** `spring-integration-mqtt` + Eclipse Paho (`org.eclipse.paho.client.mqttv3`).
* **Backend-Konfiguration (`application.yml`):** `mqtt.broker.url/username/password`, `mqtt.client-id`, `mqtt.topic=zev/+/+/messwert` (Env `MQTT_TOPIC`), `mqtt.qos`, `mqtt.clean-session` (Secrets via Env). Der MQTT-Subscriber ist über das Spring-Profil **`mqtt`** zugeschaltet (`@Profile("mqtt")`, siehe FR-1); ohne aktives Profil wird keine Broker-Verbindung aufgebaut.
* **Neuer Code:** MQTT-Subscriber/Handler, Topic-/Payload-Parser, `ZaehlerRohdaten`-Entity + Repository, Aggregations-/Delta-Service (Scheduled), Health-Indicator, Prometheus-Metriken.
* **Erweiterung:** `messwerte` um `quelle`.
* **Wiederverwendet:** `Einheit`/`Messwerte`, `SolarDistribution` (unverändert), `MetricsService`, Grafana/Prometheus-Stack.
* **Datenmigration:** Keine Bestandsmigration; nur neue Tabelle + `quelle`-Spalte.
* **i18n:** Kein UI-Text in diesem Feature (reiner Backend-Ingest). Falls später Status-UI: Texte via `TranslationService`.
* **gPlug (FR-10):** Konfiguration `mqtt.topic-gplug` (Env `MQTT_TOPIC_GPLUG`, Default `gPlug/+/+/tele/SENSOR`); Einheiten des Nachbarhauses mit `messpunkt` = Tasmota-Topic; Mosquitto auf dem Pi mit Bridge `gPlug/# out` zum NAS, Broker-User und ACL für die gPlugs (Pi) und die Bridge (NAS); `queue_qos0_messages true` auf beiden Brokern und angepasstes `max_queued_messages` (Einrichtung: `docs/Netzwerk-Topologie-Hene.md`). Kein UI-Text, keine Migration.
* **Abhängige Dokumente:** `docs/Netzwerk-Topologie-Hene.md` (Topologie/Broker-Setup/Varianten A/B/C), `Specs/Pi-Gateway-Software.md` (Publisher), `Specs/Metriken.md` (Prometheus-Infrastruktur).

## 7. Abgrenzung / Out of Scope
* **Pi-Gateway-Software** (Auslesen Wago/Modbus TCP + BKW/gPlug, Publish der Zählerstände) — separat auf dem Pi implementiert/konfiguriert (`Specs/Pi-Gateway-Software.md`). Die **Delta-Bildung** liegt hingegen im Backend (FR-6).
* Frontend-Live-Anzeige / WebSocket-Bridge.
* Admin-UI für MQTT-Konfiguration / Broker-/ACL-Verwaltung.
* Automatische Einheiten-/Geräteregistrierung; bidirektionale Befehle an Zähler.
* TLS-Zertifikatsverwaltung (manuell, siehe Topologie-Doku).
* Aufbewahrungs-/Cleanup-Strategie für Rohdaten (eigene Spec).
* SFTP-Pfad als Fallback — abgelöst, nicht Gegenstand dieser Spec.
* **gPlug:** Änderungen an Firmware, Ausleseskript oder Meldungsstruktur des gPlug; Auswertung der Momentanwerte (Leistung, Strom, Spannung) und der Tarifregister; die übrigen Tasmota-Meldungen (`STATE`, `LWT`, `INFO`); Befehle an den gPlug. Der `gplug_reader` der Pi-Software (`Specs/Pi-Gateway-Software.md`) ist ein anderer Weg und bleibt davon unberührt.
* Änderung der `zev_calculated`-**Berechnungslogik** (Verteilalgorithmus) — bleibt wie bisher. **Ausnahme (FR-9):** ein kleiner Zusatz setzt `zev = zev_calculated`, wo `zev = 0` (MQTT-Sentinel) — die eigentliche Verteilung ändert sich nicht.

## 8. Offene Fragen
* [ ] **Reset/Überlauf-Policy:** Genaue Erkennung (Schwellwert für „Rücksprung") **pro Register** (Bezug/Einspeisung) und Umgang (Referenz neu setzen, Delta = 0)? (Grundsatz in FR-6.3 festgelegt; Schwellwert-Details offen.) — **Betrieblicher Sonderfall Zählertausch:** siehe `docs/Zaehlertausch.md` (Einheit behalten, `messpunkt` anpassen, Offline-Zeit kurz halten, Zählerstände protokollieren; der Reset-Guard nullt **genau ein** Übergangsintervall — unabhängig von der Uhrzeit).
* [ ] **Zählertausch-Erkennung (Blindspot):** Der Reset-Guard erkennt einen Tausch nur am **negativen** Delta. Startet der neue Zähler **höher** als der alte endete, ist das Delta positiv → der Sprung wird als echter Verbrauch verbucht (Bogus-Wert, keine Warnung). Aus den Werten allein nicht zuverlässig erkennbar → **explizites Signal nötig.** **Lösung spezifiziert in `Specs/Zaehlertausch-Erkennung.md`:** optionales Payload-Feld **`seriennummer`** (aus der Pi-Config je `messpunkt`) + neue Spalte `seriennummer` auf `zaehler_rohdaten`; die Aggregation setzt bei Serien-Wechsel eine neue Baseline (kein Delta über die Grenze, robust in beide Richtungen), bei fehlender Serie greift das heutige Verhalten. (Interim-Vorgehen: Anhang A.2.)
* [ ] **Prosumer im selben Intervall:** Verhalten, wenn `ΔBezug` **und** `ΔEinspeisung` > 0 (Netmetering)? Aktuelle Festlegung: Netto `total = ΔBezug − ΔEinspeisung` (FR-6.4) — genügt das fachlich?
* [ ] **Rohdaten-Retention:** Cleanup-Job nötig, ab welchem Alter (absolute Stände wachsen unbegrenzt)? --> **aufgenommen in `Specs/Datenaufbewahrung.md`** (FR-1/FR-2, Default 24 Monate; der Referenzstand-Guard unten ist dort als Akzeptanzkriterium verankert). **Wichtig (Wechselwirkung mit der Wiederaufnahme nach Unterbruch):** Ein Cleanup darf den **letzten Stand vor einer noch unverarbeiteten Lücke** nicht löschen — dieser ist die Referenz für die überbrückende Delta-Bildung nach Wiederaufnahme; wird er gelöscht, ginge der Verbrauch des Unterbruchs verloren (`referenz == null` → kein Messwert). Sichere Regel: nur **verarbeitete** Rohdaten löschen und pro Einheit mindestens den jüngsten Stand behalten.
* [ ] **gPlug-Meldung verifizieren (FR-10.3):** Stimmen Objektname `z`, die Feldnamen und die Einheit kWh bei den Smartmetern im Nachbarhaus? Liefert `SMid` eine stabile Zählerkennung? Vor der Umsetzung eine echte Meldung mitschneiden.
* [ ] **Uhr des gPlug:** Sind Zeitzone und Sommerzeit (Tasmota `Timezone 99` mit Regeln für Europe/Zurich) sowie NTP eingestellt? Sonst ist `Time` um eine Stunde verschoben.
* [ ] **Einheiten des Nachbarhauses:** Welche Typen haben die neuen Zähler (Consumer, Bilanzzähler, Producer)? Gehören sie zum bestehenden Mandanten von Hene (gleiche `orgId` im FullTopic)?

## Anhang A – Entscheidungen und Verlauf

### A.1 Ausgangslage bei der Spezifikation (Abschnitt 1)
Bei der Spezifikation stand in „Aktueller Stand“: „Es gibt **noch keinen** MQTT-Code / keine MQTT-Dependency im Backend.“ Die Zähler-Daten wurden damals „heute per **SFTP** bereitgestellt“, und die MQTT-Topologie war „Ziel-Topologie“. Umgesetzt wurde das Feature gemäss `Specs/MQTT-Integration_Umsetzungsplan.md`.

### A.2 Beantwortete offene Fragen (Abschnitt 8)
* **Register → `messwerte`-Mapping:** übertragen wird die **Wirkenergie (kWh)** — Bezug **OBIS 1.8.0** (`zaehlerstandBezug`), Einspeisung **OBIS 2.8.0** (`zaehlerstandEinspeisung`); **keine** Leistung/Blind-/Scheingrössen (siehe `Pi-Gateway-Software.md`). → **Mapping entschieden (siehe FR-6):** `total = ΔBezug − ΔEinspeisung` (**vorzeichenbehaftet**: positiv = Verbrauch, negativ = Einspeisung); `zev = 0` (Sentinel, keine Tabellen-Änderung); `zev_calculated = NULL` beim Ingest, erst durch die Solarverteilung, die dann `zev = zev_calculated` setzt, wo `zev = 0` (FR-9). Jetzt in FR-3 (OBIS) und FR-6.4.
* **`org_id`-Typ & Topic-Kennung:** FR-2 sagte zuvor: „`{orgId}` folgt vorerst dem bestehenden `Messwerte`-Modell (internes `org_id` als `BIGINT`); Alternative (Keycloak-Alias/UUID) siehe Offene Fragen.“ → **internes `org_id` (BIGINT)**, konsistent mit `messwerte`/`zaehler_rohdaten` und der Pi-Config. Kein UUID-Mapping. Jetzt in FR-2.
* **Messpunkt-Eindeutigkeit:** → **`messpunkt` ist pro `org_id` eindeutig**; Auflösung über `(org_id, messpunkt)` (Unique-Constraint auf `einheit`). Jetzt in FR-2.
* **Producer vs. Consumer** (`einheit.typ`): → Der **`total`** wird **über das Vorzeichen** gebildet (Bezug positiv, Einspeisung negativ), **ohne** `einheit.typ`-Verzweigung und **ohne** getrennte Register/Topics. **Nachtrag:** Für den **`zev`**-Wert gibt es eine typ-abhängige Verzweigung — Producer: `zev = total`, Consumer: `zev = 0` (Sentinel) — damit die Statistik die Produktion korrekt ausweist. Steht in FR-6.4.
* **`zev`-Behandlung / Verträglichkeit:** → **`zev = 0` beim Ingest** (kein NULL, kein Schema-Eingriff); die Solarverteilung ersetzt `0` durch `zev_calculated` (FR-9). Bestehende `zev`-Konsumenten bleiben kompatibel. Rest-Kante (gemessenes echtes `0`) siehe FR-9-Hinweis. Steht in FR-6.4, FR-7 und FR-9.
* **Mehrfach-Quelle pro Einheit:** Verhalten, wenn für dieselbe Einheit + Zeitpunkt sowohl CSV- als auch MQTT-Daten vorliegen? → **Warnung loggen, MQTT hat Vorrang.** Jetzt in FR-6.5.
* **Zählertausch-Erkennung, Interim:** Bis zur Umsetzung von `Specs/Zaehlertausch-Erkennung.md` galt: neuen Zähler auf Endstand voreinstellen oder Übergangsintervall manuell korrigieren.
* FR-3 nannte die Abbildung auf `messwerte` „entschieden (FR-6.4): `total = ΔBezug − ΔEinspeisung` (vorzeichenbehaftet), `zev = 0` (Sentinel), `zev_calculated` bleibt Ergebnis der Solarverteilung (die `zev = 0` durch `zev_calculated` ersetzt, FR-9)“; FR-7 wiederholte: „Der MQTT-Ingest setzt `zev = 0` (Sentinel) für Consumer bzw. `zev = total` für Producer (FR-6.4); die Spalte bleibt `NOT NULL`. Die Solarverteilung ersetzt `zev = 0` später durch `zev_calculated` (FR-9).“ Beides steht jetzt nur noch in FR-6.4 und FR-9.
* FR-6 wiederholte die Profil-Aktivierung von FR-1 ausführlich („Das Bean, das die Rohdaten periodisch aus `zaehler_rohdaten` liest und aggregiert …, ist **nur aktiv, wenn das Spring-Boot-Profil `mqtt` gesetzt ist** (`@Profile("mqtt")`; aktiviert via `SPRING_PROFILES_ACTIVE=mqtt` bzw. `spring.profiles.active`)“); jetzt ein Verweis auf FR-1.

### A.3 Zeitstempel der Aggregation (FR-6.4)
Bis zur Umstellung durch `Specs/Messwerte-Zeitkonvention.md` stempelte die MQTT-Erfassung das **Ende** des Intervalls; diese Spec liess den Stempel offen, und so entstand eine zweite Konvention neben dem CSV-Import (Beginn).

### A.4 Log-Meldung „Timed out (32000)“ (FR-1.6, 19.10.2026)
Am 19.10.2026 lokal beobachtet. Eine Wartezeit von 30 s statt 5 s (Commit `4d8911b`, zurückgenommen) änderte nichts: Der Fehler kam nach 30 s statt nach 5 s, und die gepufferten Nachrichten stauten sich entsprechend länger. Die Meldung ganz zu vermeiden, hiesse, das Abo ausserhalb des Paho-Threads abzusetzen – für eine harmlose Meldung zu viel Eingriff.

### A.5 gPlug-Anbindung (FR-10, 20.10.2026)
Am 20.10.2026 ergänzt (Commit `6d55231`): Das Nachbarhaus wird in den ZEV bei Hene integriert. Vorgabe des Users: Auf den gPlugs wird nur der MQTT-Broker konfiguriert, die Meldungsstruktur bleibt; ein zweites Topic (`gPlug`) nimmt die Meldungen auf, das Backend liest sie aus und speichert die Rohdaten.

### A.6 Korrekturen nach der Konsolidierung (FR-2, FR-5, Abschnitt 6, 20.10.2026)
Bei der Konsolidierung fielen drei Abweichungen zum Code auf; auf Wunsch des Users korrigiert:
* **FR-2, Messpunkt-Eindeutigkeit:** Die beantwortete Frage (A.2) sagte „`messpunkt` ist pro `org_id` eindeutig (Unique-Constraint auf `einheit`)“. Das gilt seit dem Bilanzmesspunkt nicht mehr: `BEZUG` und `RUECKLIEFERUNG` teilen sich einen Messpunkt, der Ingest löst eine Liste auf (`EinheitRepository.findAllByOrgIdAndMesspunkt`). `V104` legt den Unique-Index bewusst nur partiell für Ladestationen an.
* **FR-5:** Die SQL-Vorlage enthielt die Spalte `seriennummer` nicht, die `V92` mit der Zählertausch-Erkennung ergänzte.
* **Abschnitt 6:** Die Konfiguration hiess dort `mqtt.topics.messwerte`; im Code (`application-mqtt.yml`, `MqttConfig`) heisst sie `mqtt.topic`. `mqtt.client-id` fehlte.

### A.7 Topologie Hene bereinigt, Netzwerkweg der gPlugs (Abschnitt 1, FR-10, NFR-2, 20.10.2026)
* Abschnitt 1 nannte „3× Wago via Modbus TCP, 1× BKW via gPlug“ und „heute per **SFTP** bereitgestellt“. Stand laut User: 15 Wago-Zähler, Bilanz vom BKW-Zähler über WhatWatt Go, Batterie per Modbus am Wechselrichter; SFTP ist durch MQTT abgelöst. Abschnitt 7 sagte „Beibehaltung des SFTP-Pfads als Fallback — nicht Gegenstand dieser Spec“.
* Offene Frage beantwortet: „**Netzwerkweg gPlug → NAS:** Liegen die gPlugs im selben Netz wie der NAS-Broker, oder braucht das Nachbarhaus einen VPN-Zugang? Ohne TLS-Unterstützung der gPlug-Firmware darf der Weg nicht offen über das Internet führen (NFR-2).“ → Die ca. 5 gPlugs stehen im LAN bei Hene. Sie publizieren an einen Mosquitto auf dem Pi, der nur `gPlug/#` per Bridge über das VPN an den NAS weiterleitet (Entscheidung des Users, Alternativen waren ein Router-VPN oder ein NAS im selben LAN). Zuvor stand in FR-10: Host „NAS-Broker, `1883`“, „direkt an den Broker auf dem NAS“, „ohne Pi“; in NFR-2: „Der Weg zum Broker muss deshalb im lokalen Netz oder im VPN liegen“. „Ohne Pi“ meint jetzt: ohne Pi-Gateway-Software.

### A.8 Producer-`zev` und „Security-Log“ (FR-6.4, FR-9, Abschnitte 3 und 5, 21.10.2026)
Beim Akzeptanzkriterien-Check vom 20.10.2026 wichen zwei Stellen vom Code ab; auf Wunsch des Users an den Code angepasst:
* **Producer-`zev`:** FR-6.4 sagte „die gesamte Produktion des Intervalls wird als ZEV-relevanter Wert übernommen … Dieser Wert ist ≠ 0 (ausser bei `total = 0`) und wird daher von der Solarverteilung **nicht** überschrieben (FR-9 greift nur bei `zev = 0`)“, FR-9.2 „Bereits gesetzte `zev`-Werte (z. B. aus CSV oder MQTT-**Producer**-Werte mit `zev = total`, `zev ≠ 0`) bleiben unverändert“, das Kriterium „Für eine **Producer**-Einheit gilt nach der Aggregation `zev = total` (und wird von der Solarverteilung nicht überschrieben, da `zev ≠ 0`)“. Seit Commit `ea2d1de` (16.07.2026, „Producer-zev = im ZEV konsumierte Produktion (statt Gesamtproduktion)“) setzt `MesswerteService.aktualisiereProducerZev` bei MQTT-Producern die verteilte Menge; die Spec war nicht nachgeführt. Neu FR-9.4.
* **Fremde `orgId`:** Das Kriterium verlangte „verworfen (Security-Log)“, der Edge Case „Security-WARN“. Weil die Einheit über (`org_id`, `messpunkt`) gesucht wird, ist eine fremde `orgId` ohne mandantenübergreifende Abfrage nicht von einem unbekannten Messpunkt zu unterscheiden; der Ingest meldet beides gleich. Die Isolation selbst ist gewährleistet.

