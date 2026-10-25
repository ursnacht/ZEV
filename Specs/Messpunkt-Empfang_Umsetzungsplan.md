# Umsetzungsplan: Messpunkt-Empfang

## Zusammenfassung

Der MQTT-Ingest hält je Mandant und Messpunkt fest, wann zuletzt eine Meldung ankam, mit welchen
Zählerständen und mit welchem Ergebnis (gespeichert, unbekannter Messpunkt, verworfen mit Grund).
Eine neue Admin-Ansicht zeigt diese Zeilen zusammen mit Einheiten, die noch nie gesendet haben, und
bietet für unbekannte Messpunkte „Einheit anlegen“ an. So lässt sich ein neuer Zähler in Betrieb
nehmen, ohne Aggregation, Abrechnung oder Steuerung zu berühren (`Specs/Messpunkt-Empfang.md`).

---

## Betroffene Komponenten

### Backend – neu
| Datei | Zweck |
|---|---|
| `backend-service/src/main/resources/db/migration/V186__Create_Messpunkt_Empfang.sql` | Sequenz, Tabelle, Constraints, Kommentare |
| `backend-service/src/main/resources/db/migration/V187__Add_Messpunkt_Empfang_Translations.sql` | Übersetzungen |
| `backend-service/src/main/java/ch/nacht/entity/MesspunktEmpfang.java` | Entity (Vorlage `Tarif.java`) |
| `backend-service/src/main/java/ch/nacht/entity/MesspunktEmpfangQuelle.java` | Enum `PI_GATEWAY`, `GPLUG` |
| `backend-service/src/main/java/ch/nacht/entity/MesspunktEmpfangErgebnis.java` | Enum `GESPEICHERT`, `UNBEKANNTER_MESSPUNKT`, `UNGUELTIG` |
| `backend-service/src/main/java/ch/nacht/repository/MesspunktEmpfangRepository.java` | Upsert (native), `findFirstById`, Liste, Cleanup (native) |
| `backend-service/src/main/java/ch/nacht/service/MesspunktEmpfangService.java` | Festhalten (`REQUIRES_NEW`), Liste mit Einheiten und Kein Empfang, Löschen, Aufräumen |
| `backend-service/src/main/java/ch/nacht/service/MesspunktEmpfangCleanupJob.java` | Täglicher Job (Vorlage `SystemmeldungCleanupJob`) |
| `backend-service/src/main/java/ch/nacht/controller/MesspunktEmpfangController.java` | `GET`, `DELETE` (Muster `EinheitController`) |
| `backend-service/src/main/java/ch/nacht/dto/MesspunktEmpfangDTO.java` | Antwort (Vorlage `DebitorDTO.java`) |

### Backend – geändert
| Datei | Änderung |
|---|---|
| `backend-service/src/main/java/ch/nacht/service/MqttIngestService.java` | Ergebnis je Meldung (FR-1.3), Quelle, Stände; Festhalten nach Transaktionsende (FR-1.6) |
| `backend-service/src/main/resources/application.yml` | `messpunkt-empfang.retention.cron` |
| `backend-service/src/test/java/ch/nacht/architecture/ArchitectureTest.java` | Regel: nur `MesspunktEmpfangService` greift auf das Repository zu |
| `backend-service/src/test/java/ch/nacht/FlywaySchemaIT.java` | CHECK- und Unique-Constraints der neuen Tabelle |

### Frontend – neu
| Datei | Zweck |
|---|---|
| `frontend-service/src/app/models/messpunkt-empfang.model.ts` | Model (Vorlage `tarif.model.ts`) |
| `frontend-service/src/app/services/messpunkt-empfang.service.ts` | `getAll`, `delete` (Vorlage `tarif.service.ts`) |
| `frontend-service/src/app/components/messpunkt-empfang/` | List-Component (Vorlage `mieter-list`) |

### Frontend – geändert
| Datei | Änderung |
|---|---|
| `frontend-service/src/app/app.routes.ts` | Route `/messpunkt-empfang` |
| `frontend-service/src/app/components/navigation/navigation.component.ts` | Eintrag in `adminEintraege` nach `/einheiten` |
| `frontend-service/src/app/components/icon/icons.ts` | Feather-Icon `radio` (fehlt bisher) |
| `frontend-service/src/app/utils/date-utils.ts` | `formatSwissDateTime` mit optionalen Sekunden |
| `frontend-service/src/app/components/einheit-list/` | `Specs/Einheitenverwaltung.md` FR-8: `?neu=1&messpunkt=…` |

### Dokumentation
| Datei | Änderung |
|---|---|
| `Specs/Berechtigungen.md` | Endpunkt-Referenz um `MesspunktEmpfangController` |
| `Specs/Icons.md` | `radio` in die Icon-Liste |

---

## Phasen

| Status | Phase                          | Beschreibung |
|--------|--------------------------------|--------------|
|  [x]   | 1. DB-Migration                | `V186__Create_Messpunkt_Empfang.sql` (siehe 1) |
|  [x]   | 2. Backend-Entity & Repository | Entity, Enums, Repository mit Upsert und Cleanup-Query (siehe 2) |
|  [x]   | 3. Backend-Service             | `MesspunktEmpfangService`: Festhalten, Liste, Löschen, Aufräumen (siehe 3) |
|  [x]   | 4. Ingest                      | `MqttIngestService`: Ergebnis ermitteln und nach Transaktionsende festhalten (siehe 4) |
|  [x]   | 5. Cleanup-Job                 | `MesspunktEmpfangCleanupJob` + `application.yml` (siehe 5) |
|  [x]   | 6. Backend-Controller & DTO    | `GET` / `DELETE`, DTO (siehe 6) |
|  [x]   | 7. ArchUnit                    | Regel aus NFR-2 in `ArchitectureTest` |
|  [x]   | 8. Frontend-Model & Service    | `messpunkt-empfang.model.ts`, `messpunkt-empfang.service.ts` |
|  [x]   | 9. Frontend-Utility & Icon     | `formatSwissDateTime` mit Sekunden; Icon `radio` in `icons.ts` |
|  [x]   | 10. Frontend-Komponente        | `MesspunktEmpfangComponent` (siehe 10) |
|  [x]   | 11. Routing                    | Änderung in: `frontend-service/src/app/app.routes.ts` |
|  [x]   | 12. Navigation                 | Änderung in: `frontend-service/src/app/components/navigation/navigation.component.ts` (`adminEintraege`) |
|  [x]   | 13. Einheitenverwaltung FR-8   | `einheit-list` liest `?neu=1&messpunkt=…` (siehe 13) |
|  [x]   | 14. Übersetzungen              | Datei: `backend-service/src/main/resources/db/migration/V187__Add_Messpunkt_Empfang_Translations.sql` |
|  [x]   | 15. Dokumentation              | `Specs/Berechtigungen.md`, ggf. `Specs/Icons.md` |

Tests sind nicht Teil dieser Phasen (`/3`–`/5`). Für `/3` vorgemerkt: zwei Transaktions-ITs nach
Vorlage `SteuerungServiceTransaktionIT`, die `MqttIngestService` per `@Import` einbinden (er ist
`@Profile("mqtt")`).

---

## 1. DB-Migration

**Datei:** `backend-service/src/main/resources/db/migration/V186__Create_Messpunkt_Empfang.sql`
(Nummer vor dem Anlegen prüfen; letzte vorhandene: V185)

Spaltentabelle aus der Spec (verbindlich):

| Spalte                     | Typ          | Pflicht | Constraints                                                       |
|----------------------------|--------------|---------|-------------------------------------------------------------------|
| `id`                       | BIGINT       | ja      | PRIMARY KEY, Sequenz `zev.messpunkt_empfang_seq` (wie `Tarif`: `SEQUENCE`, `allocationSize = 1`) |
| `org_id`                   | BIGINT       | ja      | siehe Multi-Tenancy                                               |
| `messpunkt`                | VARCHAR(255) | ja      | länger als `zev.einheit.messpunkt` (50), weil auch unbekannte Messpunkte erscheinen; über 255 Zeichen siehe FR-1.5 |
| `quelle`                   | VARCHAR(20)  | ja      | CHECK in (`PI_GATEWAY`, `GPLUG`)                                  |
| `erster_empfang`           | TIMESTAMPTZ  | ja      | absoluter Zeitpunkt, Backend-Uhr (FR-1.8)                         |
| `letzter_empfang`          | TIMESTAMPTZ  | ja      | dto.                                                              |
| `anzahl_meldungen`         | BIGINT       | ja      | CHECK `anzahl_meldungen >= 1`                                     |
| `zaehlerstand_bezug`       | NUMERIC      | nein    | bewusst unbeschränkt, anders als `zev.zaehler_rohdaten` (`DECIMAL(14,4)`): Auch ungültige Stände sollen sich festhalten lassen, ohne dass der Insert scheitert; `NULL` siehe FR-1.4 |
| `zaehlerstand_einspeisung` | NUMERIC      | nein    | dto.                                                              |
| `ergebnis`                 | VARCHAR(30)  | ja      | CHECK in (`GESPEICHERT`, `UNBEKANNTER_MESSPUNKT`, `UNGUELTIG`)    |
| `grund`                    | VARCHAR(255) | nein    | CHECK `(ergebnis = 'UNGUELTIG') = (grund IS NOT NULL)`            |

* **Primary Key:** `id`
* **Foreign Keys:** keine – kein FK auf `zev.einheit` (FR-1.7) und bewusst auch keiner auf
  `zev.organisation` (siehe Annahme A1).
* **Unique:** (`org_id`, `messpunkt`)
* **Multi-Tenancy:** ja, `org_id` aus dem Topic.

```sql
CREATE SEQUENCE IF NOT EXISTS zev.messpunkt_empfang_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE zev.messpunkt_empfang (
    id                       BIGINT PRIMARY KEY DEFAULT nextval('zev.messpunkt_empfang_seq'),
    org_id                   BIGINT       NOT NULL,
    messpunkt                VARCHAR(255) NOT NULL,
    quelle                   VARCHAR(20)  NOT NULL,
    erster_empfang           TIMESTAMPTZ  NOT NULL,
    letzter_empfang          TIMESTAMPTZ  NOT NULL,
    anzahl_meldungen         BIGINT       NOT NULL,
    zaehlerstand_bezug       NUMERIC,
    zaehlerstand_einspeisung NUMERIC,
    ergebnis                 VARCHAR(30)  NOT NULL,
    grund                    VARCHAR(255),
    CONSTRAINT uq_messpunkt_empfang_org_messpunkt UNIQUE (org_id, messpunkt),
    CONSTRAINT ck_messpunkt_empfang_quelle   CHECK (quelle IN ('PI_GATEWAY', 'GPLUG')),
    CONSTRAINT ck_messpunkt_empfang_ergebnis CHECK (ergebnis IN ('GESPEICHERT', 'UNBEKANNTER_MESSPUNKT', 'UNGUELTIG')),
    CONSTRAINT ck_messpunkt_empfang_anzahl   CHECK (anzahl_meldungen >= 1),
    CONSTRAINT ck_messpunkt_empfang_grund    CHECK ((ergebnis = 'UNGUELTIG') = (grund IS NOT NULL))
);

COMMENT ON TABLE  zev.messpunkt_empfang IS 'Letzter MQTT-Empfang je Mandant und Messpunkt, auch ohne Einheit (Specs/Messpunkt-Empfang.md)';
COMMENT ON COLUMN zev.messpunkt_empfang.org_id IS 'Mandant aus dem MQTT-Topic; bewusst ohne FK, siehe Spec FR-3.2';
COMMENT ON COLUMN zev.messpunkt_empfang.messpunkt IS 'Messpunkt aus dem Topic, ggf. ohne zugeordnete Einheit';
-- … je Spalte ein Kommentar (Konvention 2_umsetzung)
```

## 2. Entity & Repository

* `MesspunktEmpfang` nach Vorlage `Tarif.java`: `@SequenceGenerator(sequenceName = "zev.messpunkt_empfang_seq", allocationSize = 1)`,
  `@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"org_id", "messpunkt"}))`,
  `@FilterDef`/`@Filter` wie die übrigen Entities. Zeitfelder `Instant`, Stände `BigDecimal`,
  Quelle und Ergebnis als `@Enumerated(EnumType.STRING)`.
* `MesspunktEmpfangRepository`:
  * `upsert(orgId, messpunkt, quelle, zeitpunkt, bezug, einspeisung, ergebnis, grund)` – `@Modifying`
    native Query `INSERT … VALUES (nextval('zev.messpunkt_empfang_seq'), …, 1, …) ON CONFLICT
    (org_id, messpunkt) DO UPDATE SET letzter_empfang = EXCLUDED.letzter_empfang, quelle = …,
    zaehlerstand_bezug = …, zaehlerstand_einspeisung = …, ergebnis = …, grund = …,
    anzahl_meldungen = zev.messpunkt_empfang.anzahl_meldungen + 1`.
  * `findAllByOrderByLetzterEmpfangDesc()` (gefiltert), `findFirstById(Long id)`.
  * `loescheVerwaisteVor(Instant grenze)` – `@Modifying` native Query, **mandantenübergreifend**:
    `DELETE … WHERE letzter_empfang < :grenze AND NOT EXISTS (SELECT 1 FROM zev.einheit e WHERE
    e.org_id = m.org_id AND e.messpunkt = m.messpunkt AND e.typ <> 'LADESTATION')`.

## 3. Service

`MesspunktEmpfangService` (Vorlage `TarifService.java`):
* `halteFest(Empfang empfang)` – `@Transactional(propagation = REQUIRES_NEW)`, org-explizit, ruft
  `upsert`; Zeitpunkt `Instant.now()` (über eine injizierbare `Clock`, damit Tests das Alter fixieren
  können).
* `getAll()` – `enableOrgFilter()`, lädt Empfangszeilen und **einmal** die Einheiten des Mandanten
  mit Messpunkt (ohne Ladestationen); baut die DTOs: Einheiten-Namen je Messpunkt, `sekundenSeitEmpfang
  = max(0, Duration.between(letzter, now))`, und ergänzt Kein-Empfang-Zeilen für Einheiten-Messpunkte
  ohne Empfang. Sortierung bleibt dem Frontend überlassen (FR-2.6).
* `delete(Long id)` – `enableOrgFilter()`, `findFirstById` → `boolean`.
* `raeumeAuf()` – `loescheVerwaisteVor(now − 30 Tage)`, loggt die Anzahl.

## 4. Ingest (`MqttIngestService`)

* Ein internes Ergebnisobjekt (`orgId`, `messpunkt`, `quelle`, `bezug`, `einspeisung`, `ergebnis`,
  `grund`) wird in `handle` mitgeführt, sobald das Topic gelesen ist; Topic unlesbar, Messpunkt leer
  oder über 255 Zeichen → kein Objekt (FR-1.5).
* Prüfreihenfolge und Gründe exakt nach FR-1.3; Gründe als Konstanten. JSON- und Typfehler aus
  `readValue` sowie `DateTimeParseException` der gPlug-Zeit in den Lesemethoden abfangen →
  `Payload nicht lesbar`. Allgemeines `catch` → `Verarbeitung fehlgeschlagen`.
* `GESPEICHERT` nur, wenn `upsertRohdaten` für mindestens eine Einheit gelaufen ist.
* Festhalten: `TransactionSynchronizationManager.registerSynchronization(…afterCompletion(status))` –
  bei `STATUS_ROLLED_BACK` Ergebnis zu `UNGUELTIG` / `Speichern fehlgeschlagen`; danach
  `messpunktEmpfangService.halteFest(…)` in `try/catch` mit `WARN`. Ohne aktive Synchronisation
  (`isSynchronizationActive() == false`) direkt festhalten.
* Stände nach FR-1.4: beim gPlug fehlendes `z.Eo` als `null` vermerken, auch wenn die Rohdaten `0`
  speichern.

## 5. Cleanup-Job

`MesspunktEmpfangCleanupJob` nach Vorlage `SystemmeldungCleanupJob`, **ohne** `@Profile`:
`@Scheduled(cron = "${messpunkt-empfang.retention.cron:0 30 3 * * *}", zone = "Europe/Zurich")`,
ruft `messpunktEmpfangService.raeumeAuf()`. In `application.yml`:
```yaml
messpunkt-empfang:
  retention:
    cron: "0 30 3 * * *"
```

## 6. Controller & DTO

Endpunkttabelle aus der Spec (verbindlich):

| Methode | Pfad                          | Permission      | Request-Body | Response                    | Status-Codes    |
|---------|-------------------------------|-----------------|--------------|-----------------------------|-----------------|
| GET     | `/api/messpunkt-empfang`      | `einheit:read`  | –            | `List<MesspunktEmpfangDTO>` | 200 / 403       |
| DELETE  | `/api/messpunkt-empfang/{id}` | `einheit:write` | –            | –                           | 204 / 403 / 404 |

* Klasse `@PreAuthorize("hasAuthority('einheit:write')")`, `GET` mit `einheit:read` überschrieben
  (wie `EinheitController`), ohne `@Profile`.
* `MesspunktEmpfangDTO` (Vorlage `DebitorDTO.java`): `id`, `messpunkt`, `einheiten`, `quelle`,
  `ersterEmpfang`, `letzterEmpfang` (als Ortszeit `Europe/Zurich`, `LocalDateTime`), 
  `sekundenSeitEmpfang`, `anzahlMeldungen`, `zaehlerstandBezug`, `zaehlerstandEinspeisung`,
  `ergebnis`, `grund`, `keinEmpfang`. Kein `orgId`.

## 10. Frontend-Komponente

`MesspunktEmpfangComponent` nach Vorlage `mieter-list` (Sortierung per Spaltenklick):
* Laden über den Service, `subscribe({ next, error })`; Fehler → `FEHLER_BEIM_LADEN_DER_DATEN`.
* Standardsortierung FR-2.6 (ohne Einheit → Kein Empfang → übrige); sortierbare Spalten wie in FR-2.6.
* Ergebnis-Badge nach Tabelle FR-2.4, Alters-Badge nach FR-2.5 (Schwellen 300 s / 3600 s, Texte
  abgerundet in Min./Std./Tagen) – als reine Funktionen, damit sie einzeln testbar sind.
* Kebab-Spalte nur mit `einheit:write` (Permission-Prüfung wie in `einheit-list`); Einträge nach
  FR-2.8; „Einheit anlegen“ navigiert auf `/einheiten?neu=1&messpunkt=<encodeURIComponent>`.
* Löschen mit `confirm(MESSPUNKT_EMPFANG_LOESCHEN_BESTAETIGEN)`, Erfolg 5 s, Fehler bleibt.
* Design-System-Klassen gemäss Spec; kein neues CSS ohne Prüfung.

## 13. Einheitenverwaltung FR-8

In `einheit-list` nach dem Laden `ActivatedRoute.snapshot.queryParamMap` lesen: Bei `neu=1` und
`einheit:write` das Formular wie `onCreateNew` öffnen, Messpunkt übernehmen, sofern 1–50 Zeichen;
danach `router.navigate([], { queryParams: {}, replaceUrl: true })`. Das Formular braucht dafür
einen vorausgefüllten Messpunkt (Input-Objekt mit gesetztem `messpunkt`, sonst Defaults aus FR-3.2).

## 14. Übersetzungen

Keys aus der Spec (verbindlich, deutsche Texte mit Umlauten), `ON CONFLICT (key) DO NOTHING`:

| Key                                       | Deutsch                                   | Englisch                              |
|-------------------------------------------|-------------------------------------------|---------------------------------------|
| `MESSPUNKT_EMPFANG`                       | Messpunkt-Empfang                         | Meter point reception                 |
| `MESSPUNKT_EMPFANG_KEINE_DATEN`           | Keine Messpunkte vorhanden.               | No meter points available.            |
| `MESSPUNKT_EMPFANG_LETZTER`               | Letzter Empfang                           | Last received                         |
| `MESSPUNKT_EMPFANG_ERSTER`                | Erster Empfang                            | First received                        |
| `MESSPUNKT_EMPFANG_ANZAHL`                | Meldungen                                 | Messages                              |
| `MESSPUNKT_EMPFANG_QUELLE`                | Quelle                                    | Source                                |
| `MESSPUNKT_EMPFANG_QUELLE_PI_GATEWAY`     | Pi-Gateway                                | Pi gateway                            |
| `MESSPUNKT_EMPFANG_QUELLE_GPLUG`          | gPlug                                     | gPlug                                 |
| `MESSPUNKT_EMPFANG_STAND_BEZUG`           | Zählerstand Bezug (kWh)                   | Meter reading consumption (kWh)       |
| `MESSPUNKT_EMPFANG_STAND_EINSPEISUNG`     | Zählerstand Einspeisung (kWh)             | Meter reading feed-in (kWh)           |
| `MESSPUNKT_EMPFANG_ERGEBNIS`              | Ergebnis                                  | Result                                |
| `MESSPUNKT_EMPFANG_GESPEICHERT`           | Gespeichert                               | Stored                                |
| `MESSPUNKT_EMPFANG_UNBEKANNTER_MESSPUNKT` | Keine Einheit zugeordnet                  | No unit assigned                      |
| `MESSPUNKT_EMPFANG_UNGUELTIG`             | Verworfen                                 | Rejected                              |
| `MESSPUNKT_EMPFANG_KEIN_EMPFANG`          | Kein Empfang                              | Nothing received                      |
| `MESSPUNKT_EMPFANG_VOR_MINUTEN`           | vor {0} Min.                              | {0} min ago                           |
| `MESSPUNKT_EMPFANG_VOR_STUNDEN`           | vor {0} Std.                              | {0} h ago                             |
| `MESSPUNKT_EMPFANG_VOR_TAGEN`             | vor {0} Tagen                             | {0} days ago                          |
| `MESSPUNKT_EMPFANG_EINHEIT_ANLEGEN`       | Einheit anlegen                           | Create unit                           |
| `MESSPUNKT_EMPFANG_LOESCHEN_BESTAETIGEN`  | Eintrag löschen? Sendet der Messpunkt weiter, erscheint er wieder. | Delete entry? It reappears if the meter point keeps sending. |
| `MESSPUNKT_EMPFANG_GELOESCHT`             | Eintrag gelöscht                          | Entry deleted                         |
| `MESSPUNKT_EMPFANG_FEHLER_LOESCHEN`       | Eintrag konnte nicht gelöscht werden      | Entry could not be deleted            |

Wiederverwendet (vorhanden): `MESSPUNKT`, `EINHEITEN`, `AKTUALISIEREN`, `LOESCHEN`,
`FEHLER_BEIM_LADEN_DER_DATEN`.

---

## Validierungen

Aus der Spec-Tabelle «Validierungen» übernommen:

| Feld | Regel | FE / BE | Fehlermeldung (Translation-Key) |
|------|-------|---------|---------------------------------|
| n/a  | Keine Benutzereingaben ausser Löschen; die Werte stammen aus dem Ingest | – | – |

Ergänzend die Prüfungen des Ingests (keine Benutzer-Validierung, sondern Ergebnis der Meldung):
Prüfreihenfolge und Gründe nach Spec FR-1.3. In der Einheitenverwaltung (FR-8) wird ein Messpunkt
aus der URL nur übernommen, wenn er 1–50 Zeichen hat.

---

## Offene Punkte / Annahmen

**Aus Spec Abschnitt 8 (beantwortet):**
* Lesen mit `einheit:read` – keine Personendaten.
* „Einheit anlegen“ gehört zur ersten Umsetzung.
* Menü: Admin-Bereich direkt unter Einheiten.
* Schwellen 5 / 60 Minuten und Aufbewahrung 30 Tage passen.
* Einheiten ohne Empfang erscheinen als Kein Empfang.
* Zwei Badges: Ergebnis und Alter.
* Festhalten nach Abschluss der Ingest-Transaktion in eigener Transaktion; Prüfreihenfolge FR-1.3.
* Empfangszeiten als `TIMESTAMPTZ` / `Instant`; `GESPEICHERT` nur bei geschriebenen Rohdaten und
  gelungenem Commit.

**Annahmen dieses Plans:**
* **A1 – Kein FK auf `zev.organisation`.** Die übrigen Mandantentabellen haben einen (z.B.
  `V46__Migrate_Org_Id_To_Organisation_FK.sql`). Hier nicht: Die Spec sieht Zeilen mit einer
  `org_id` ohne Mandanten ausdrücklich vor (FR-3.2, NFR-2), und ein FK liesse das Festhalten für
  solche Topics scheitern. Ein Fehler wäre zwar folgenlos (FR-1.6), die Zeile aber auch für den
  Betreiber unsichtbar – das ist ohnehin so. Falls ein FK gewünscht ist: Spec und Job anpassen.
* **A2 – Icon `radio`** fehlt in `frontend-service/src/app/components/icon/icons.ts` und wird aus
  Feather ergänzt.
* **A3 – `Clock`** wird im Service injiziert, damit Alter und 30-Tage-Grenze testbar sind. Eine
  `Clock`-Bean gibt es noch nicht; sie kommt als `@Bean Clock clock() { return Clock.system(Ortszeit.ZONE); }`
  in eine bestehende Konfigurationsklasse. Die Umrechnung für die Anzeige nutzt `Ortszeit.ZONE`.
* **A4 – Sortierung im Frontend.** Das Backend liefert unsortiert bzw. nach letztem Empfang; die
  Gruppen-Sortierung FR-2.6 macht die Komponente.

---

## Protokoll der Umsetzung (25.10.2026)

Alle 15 Phasen umgesetzt; Backend kompiliert, Frontend-Build grün.

* **Ingest:** Der Empfangsvermerk wird in `handle` mitgeführt und im `finally` an
  `halteEmpfangFest` übergeben; dieser registriert eine `TransactionSynchronization` und hält in
  `afterCompletion` über `MesspunktEmpfangService.halteFest` (`REQUIRES_NEW`) fest. JSON- und
  Typfehler fangen die Lesemethoden jetzt selbst ab (`JacksonException`), statt sie ins allgemeine
  `catch` laufen zu lassen; Metriken und Logzeilen bleiben gleich.
* **gPlug-Reihenfolge:** Eine nicht lesbare `Time` wird vor der Pflichtfeldprüfung erkannt
  (FR-1.3: Lesbarkeit vor Vollständigkeit).
* **Neue Bean `Clock`** in `config/ZeitConfig.java` (A3).
* **Bestehende Tests angepasst** (nur Verdrahtung, kein neues Verhalten geprüft):
  `MqttIngestServiceTest` (Mock `MesspunktEmpfangService`, 79 grün), `einheit-list.component.spec.ts`
  (Stub `ActivatedRoute`), `navigation.component.spec.ts` (sieben Admin-Einträge).
* **Noch offen:** Tests zu den 45 Akzeptanzkriterien (`/3`, `/4`, `/5`), insbesondere die beiden
  Transaktions-ITs; `FlywaySchemaIT` für die neuen Constraints.

## Nachtrag 1 — Letzte Meldung ansehen, Spalte „Erster Empfang“ verschoben (25.10.2026)

* **Payload festhalten (FR-1.9):** `V188__Messpunkt_Empfang_Letzte_Meldung.sql` (neue Spalte
  `letzte_meldung TEXT`, Kommentar, Übersetzungen `MESSPUNKT_EMPFANG_LETZTE_MELDUNG`,
  `MESSPUNKT_EMPFANG_KEINE_MELDUNG`, `SCHLIESSEN`) – V186/V187 waren lokal schon ausgeführt. Der
  Ingest gibt den Payload im Empfangsvermerk mit; `MesspunktEmpfangService.kuerze` begrenzt ihn
  auf 10'000 Zeichen.
* **Ansehen (FR-2.9):** `GET /api/messpunkt-empfang/{id}/letzte-meldung` (`einheit:read`,
  `MesspunktEmpfangMeldungDTO`). Kebab-Eintrag „Letzte Meldung anzeigen“ für jede Zeile mit
  Empfang, auch ohne `einheit:write`; die Kebab-Spalte steht deshalb immer. Anzeige in einer Zeile
  unter der gewählten als `.zev-code-block` (neu im Design System: `typography.css`, Showcase,
  `Specs/generell.md`).
* **Spalte „Erster Empfang“** steht jetzt vor „Letzter Empfang“ (FR-2.3).
* **Tests:** `MesspunktEmpfangServiceTest` (9), `MesspunktEmpfangControllerTest` (5),
  `MesspunktEmpfangRepositoryIT` (2, Upsert gegen PostgreSQL), `ControllerAuthorizationTest` (+2),
  `MqttIngestServiceTest` (+2), `messpunkt-empfang.component.spec.ts` (18),
  `einheit-list.component.spec.ts` (+3) und `einheit-form.component.spec.ts` (+1) für FR-8.
* **Aus `/9`:** Die Einheitenverwaltung übernimmt den Messpunkt jetzt über `@Input() messpunktVorgabe`
  des Formulars, statt dessen Vorgaben zu kopieren; die Länge 50 steht einmal als
  `MAX_MESSPUNKT_LAENGE` in `einheit.model.ts`.
* **Sichtprüfung (25.10.2026):** Bei 390 px lief die zehnspaltige Tabelle über die Seite hinaus;
  sie steht jetzt in `.zev-table-scroll`. Geprüft mit im Browser vorgegebenen Zeilen und Meldungen
  (`page.route`), weil der Testuser lokal keinen Empfang hat: Spaltenreihenfolge, Kebab-Einträge,
  JSON eingerückt unter der Zeile, Markup als Text, Badges, Dark Mode, schmale Breite.

## Nachtrag 2 — Backend-Tests (`/3`, 25.10.2026)

Tests zu allen Backend-Kriterien, darunter die zwei Transaktions-ITs aus FR-1.6
(`MqttIngestServiceTransaktionIT`, `MqttIngestServiceFesthaltenTransaktionIT`, beide mit
Gegenprobe), `FlywaySchemaIT` für V186, `MesspunktEmpfangRepositoryIT` (Upsert, Aufräumen,
Mandantenfilter) und `MesspunktEmpfangCleanupJobTest`. Zwei Befunde behoben, beide FR-1.3 #1:
* Ein **gPlug-Feld vom falschen Typ** (`"Ei":"abc"`, `"Time":12345`) ergab `Pflichtfeld fehlt`;
  jetzt `Payload nicht lesbar`. Nur ein fehlendes Feld ist ein fehlendes Pflichtfeld.
* Der **Pi-Payload `null`** (JSON-Literal) lief in eine NullPointerException und damit auf
  `Verarbeitung fehlgeschlagen`; jetzt `Payload nicht lesbar`.

`MqttIngestService` kommt in den Transaktions-ITs als `@Bean` aus einer `@TestConfiguration` statt
per `@Import`: `@Import` werte die `@Profile("mqtt")`-Bedingung ebenfalls aus.

