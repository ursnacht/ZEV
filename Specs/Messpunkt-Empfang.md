# Messpunkt-Empfang

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Eine Ansicht zeigt je Messpunkt, ob und wann zuletzt MQTT-Meldungen
  ankamen, mit welchem Zählerstand und mit welchem Ergebnis (gespeichert oder verworfen, mit Grund).
  Sie zeigt auch Messpunkte, die **keiner Einheit** zugeordnet sind, und Einheiten, deren Messpunkt
  **noch nie** gesendet hat.
* **Warum machen wir das:** Ein neuer Zähler (Pi-Gateway oder gPlug) soll sich in Betrieb nehmen
  lassen, ohne Aggregation, Abrechnung oder Steuerung zu berühren. Wer einen Zähler anschliesst, will
  zuerst wissen: Kommen die Daten an, unter welchem Messpunkt, und sind sie gültig?
  > **Warum keine Einheit im „Testbetrieb“.** Erwogen wurde ein Flag auf der Einheit, das die
  > Aggregation überspringt. Eine Einheit ohne Messwerte erschiene aber in Statistik, Rechnungen,
  > Nebenkosten und Solarverteilung mit 0. Jede dieser Stellen hätte sie zusätzlich ausblenden
  > müssen, und ein vergessenes Flag liesse die Einheit still aus der Abrechnung fallen. Die
  > Empfangsanzeige beantwortet dieselbe Frage, ohne den Datenfluss zu verändern (Entscheid vom
  > 25.10.2026).
* **Ausgangslage:** Der Ingest (`MqttIngestService`, `Specs/MQTT-Integration.md` FR-4) verwirft
  Meldungen eines unbekannten Messpunkts mit einer Logzeile (`WARN`). Ebenso verworfene Meldungen mit
  fehlendem Pflichtfeld, negativem Zählerstand, nicht synchronisierter gPlug-Uhr oder `Ei = Eo = 0`.
  Ob ein frisch angeschlossener Zähler sendet, sieht man heute nur im Backend-Log oder per
  `mosquitto_sub` auf dem Broker. Ausfälle bekannter Zähler meldet erst der Aggregations-Job
  (`MQTT_ZAEHLER_LUECKE` / `_AUSFALL`), also frühestens nach einem Intervall.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Empfang festhalten (Ingest)
1. Der Ingest hält für **jede** Meldung, deren Topic sich lesen lässt (Mandant und Messpunkt
   bekannt), den Empfang in `zev.messpunkt_empfang` fest – je (`org_id`, `messpunkt`) **eine** Zeile
   (Upsert).
2. Festgehalten werden: Zeit des ersten und des letzten Empfangs, Anzahl Meldungen seit dem ersten
   Empfang, Quelle (`PI_GATEWAY` / `GPLUG`), die zuletzt gemeldeten Zählerstände (Bezug, Einspeisung),
   das **Ergebnis** der letzten Meldung mit Grund und die **letzte Meldung selbst** (Payload, FR-1.9).
3. **Ergebnis und Prüfreihenfolge.** Die Prüfungen laufen in der Reihenfolge des Ingests; die
   **erste**, die scheitert, bestimmt das Ergebnis:

   | # | Prüfung                                               | Ergebnis                | `grund`                          |
   |---|-------------------------------------------------------|-------------------------|----------------------------------|
   | 1 | Payload lässt sich lesen: gültiges JSON, Felder vom richtigen Typ, gPlug-`Time` als Zeit lesbar | `UNGUELTIG` | `Payload nicht lesbar` |
   | 2 | Pflichtfelder vorhanden (Zeit, Zählerstände)          | `UNGUELTIG`             | `Pflichtfeld fehlt`              |
   | 3 | gPlug: Uhr synchronisiert                             | `UNGUELTIG`             | `gPlug-Uhr nicht synchronisiert` |
   | 4 | gPlug: nicht `Ei = Eo = 0`                            | `UNGUELTIG`             | `Ei = Eo = 0`                    |
   | 5 | Zählerstände nicht negativ                            | `UNGUELTIG`             | `negativer Zählerstand`          |
   | 6 | Eine Einheit (ausser Ladestation) trägt den Messpunkt | `UNBEKANNTER_MESSPUNKT` | –                                |
   | 7 | Rohdaten für mindestens eine Einheit geschrieben **und** Transaktion committet | `GESPEICHERT` | – |
   | – | Jede andere Exception im Ingest (allgemeines `catch`) | `UNGUELTIG`             | `Verarbeitung fehlgeschlagen`    |
   | – | Commit der Ingest-Transaktion scheitert               | `UNGUELTIG`             | `Speichern fehlgeschlagen`       |

   **`GESPEICHERT` heisst: Der Ingest hat Rohdaten geschrieben, und der Commit ist gelungen.** Ein
   gelungener Commit allein genügt nicht: Das allgemeine `catch` beendet die Transaktion normal,
   auch wenn die Meldung verworfen wurde. Der Ingest vermerkt deshalb das Ergebnis der Prüfungen
   selbst; der Commit kann es nur noch zu `Speichern fehlgeschlagen` verschlechtern.

   > Ein ungültiger Payload eines unbekannten Messpunkts zeigt also `UNGUELTIG`, nicht
   > `UNBEKANNTER_MESSPUNKT`: Erst eine gültige Meldung sagt, ob der Messpunkt passt.

   Die Gründe sind feste Texte (Konstanten), keine Exception-Meldungen; `grund` bleibt damit unter
   255 Zeichen.
4. **Zählerstände je Ergebnis:** Bei `GESPEICHERT` und `UNBEKANNTER_MESSPUNKT` die gemeldeten Stände.
   Bei `UNGUELTIG` die Stände, die sich lesen liessen – auch ein negativer oder `0`; fehlt ein
   Register oder ist der Payload nicht lesbar, ist die Spalte `NULL`. Bei `Speichern fehlgeschlagen`
   und `Verarbeitung fehlgeschlagen` die gemeldeten Stände, soweit lesbar. Ein beim gPlug fehlendes
   `z.Eo` erscheint als `NULL` – so wie gemeldet, auch wenn die Rohdaten dafür `0` speichern. Stände
   einer früheren Meldung bleiben **nicht** stehen: Die Zeile zeigt immer genau die letzte Meldung.
5. Meldungen, deren Topic sich **nicht** lesen lässt (kein Mandant, keine Zahl als Mandant, kein
   oder ein leerer Messpunkt, z.B. `zev/1//messwert`), werden nicht festgehalten – es gibt keine Zeile, der man sie zuordnen
   könnte. Sie bleiben in Log und Metriken (`Specs/MQTT-Integration.md`, FR-8). Ebenso Messpunkte
   **länger als 255 Zeichen**: `WARN` im Log, keine Zeile.
   > Kürzen hiesse, zwei verschiedene Messpunkte könnten auf dieselbe Zeile fallen.
6. **Eigene Transaktion, nach dem Ingest.** Das Festhalten läuft **nach Abschluss** der
   Ingest-Transaktion (`TransactionSynchronization.afterCompletion`) in einer **eigenen** Transaktion
   (`REQUIRES_NEW`, wie `SystemmeldungService.erfasse`). Ohne aktive Transaktion wird direkt
   festgehalten.
   * Nur so spiegelt `GESPEICHERT` den **tatsächlichen** Commit, und ein gescheiterter Commit
     erscheint als `Speichern fehlgeschlagen` statt als `GESPEICHERT`.
   * Scheitert das Festhalten selbst, wird es mit `WARN` protokolliert. Rohdaten, Zustandswerte und
     Metriken der Meldung sind dann bereits committet und bleiben unberührt.
   > Eine Anzeige darf die Messung nie kosten. Im Ingest-Transaktionsschritt selbst ginge das nicht:
   > Ein werfender Upsert setzte die Transaktion auf rollback-only, und die Rohdaten wären weg.
7. Der Empfang ist **unabhängig** von Einheiten: Wird eine Einheit angelegt, gelöscht oder ihr
   Messpunkt geändert, bleibt die Zeile bestehen. Das Ergebnis ändert sich mit der nächsten Meldung;
   die Spalte Einheiten wird beim Lesen aufgelöst und zeigt die Zuordnung sofort.
8. Die **Empfangszeit** ist die Backend-Uhr als **absoluter Zeitpunkt** (`Instant`, gespeichert als
   `TIMESTAMPTZ`), nicht die Zeit im Payload.
   > **Absolut, nicht zonenlose Ortszeit.** Das Alter (FR-2.5) ist eine Differenz zweier Zeitpunkte.
   > Über zonenlose Ortszeit gerechnet, wäre es in der Nacht der Umstellung auf Winterzeit um bis zu
   > 60 Minuten falsch oder sogar negativ (02:59 Sommerzeit bis 02:10 Winterzeit ergäbe −49 statt
   > +11 Minuten). Die Konvention der zonenlosen Ortszeit (`Specs/Messwerte-Zeitkonvention.md`) gilt
   > für Messwerte und ihre Intervalle; dies hier ist ein technischer Empfangszeitpunkt. Angezeigt
   > wird er in Ortszeit `Europe/Zurich`.
   > Die Gerätezeit kann falsch sein – genau das ist einer der Gründe, eine Meldung zu verwerfen
   > (gPlug-Uhr nicht synchronisiert). Die Frage „kommt etwas an?“ beantwortet nur die Empfangszeit.
9. **Letzte Meldung (Payload).** Festgehalten wird der Payload der letzten Meldung als Text, so wie
   er ankam – bei **jedem** Ergebnis, gerade auch bei `UNGUELTIG`: Ein unlesbarer Payload ist genau
   der Fall, in dem man ihn sehen will. Höchstens **10'000 Zeichen**; ein längerer wird gekürzt und
   endet mit dem Hinweis `…[gekürzt]`. Ein leerer Payload wird als leerer Text festgehalten.
   > Die Grenze schützt die Tabelle vor einem fehlkonfigurierten Gerät, das grosse Meldungen schickt.
   > Pi-Gateway und gPlug senden einige hundert Zeichen.

### FR-2: Empfangsanzeige
1. User öffnet über das Menü **Admin › Messpunkt-Empfang** (direkt unter Einheiten).
2. **Zeilen:** die Vereinigung aus
   * allen Zeilen von `zev.messpunkt_empfang` des eigenen Mandanten und
   * allen Einheiten des Mandanten mit gesetztem `messpunkt` (ohne Ladestationen), deren Messpunkt
     dort **keine** Zeile hat – Status **Kein Empfang**.

   Ein Messpunkt erscheint genau einmal. Einheiten ohne `messpunkt` erscheinen nicht.
   > Gerade „Einheit angelegt, aber es kommt nichts“ ist der häufigste Fall bei der Inbetriebnahme.
3. **Spalten** in dieser Reihenfolge – der erste Empfang steht vor dem letzten, damit sich die
   Zeitspanne von links nach rechts liest (Hintergrund: Anhang A.1):
   * **Messpunkt**
   * **Einheiten** – Namen der Einheiten mit diesem Messpunkt (ohne Ladestationen), kommagetrennt,
     sonst „–“
   * **Quelle** – `Pi-Gateway` bzw. `gPlug`; bei Kein Empfang „–“
   * **Erster Empfang** – `dd.MM.yyyy HH:mm` (eigene Spalte)
   * **Letzter Empfang** – Zeitpunkt `dd.MM.yyyy HH:mm:ss` in Ortszeit und darunter das
     **Alters-Badge** (FR-2.5)
   * **Zählerstand Bezug** und **Zählerstand Einspeisung** – kWh, 3 Nachkommastellen, Schweizer
     Format; `NULL` als „–“
   * **Meldungen** – Anzahl seit dem ersten Empfang
   * **Ergebnis** – **Ergebnis-Badge** (FR-2.4), bei `UNGUELTIG` gefolgt vom Grund
4. **Ergebnis-Badge** (`.zev-status`):

   | Ergebnis                | Text (Key)                                | Farbe     |
   |-------------------------|-------------------------------------------|-----------|
   | `GESPEICHERT`           | `MESSPUNKT_EMPFANG_GESPEICHERT`           | `success` |
   | `UNBEKANNTER_MESSPUNKT` | `MESSPUNKT_EMPFANG_UNBEKANNTER_MESSPUNKT` | `warning` |
   | `UNGUELTIG`             | `MESSPUNKT_EMPFANG_UNGUELTIG`             | `error`   |
   | Kein Empfang            | `MESSPUNKT_EMPFANG_KEIN_EMPFANG`          | `error`   |

5. **Alters-Badge** in der Spalte Letzter Empfang. Das **Backend** liefert `sekundenSeitEmpfang`
   als Differenz der absoluten Zeitpunkte (`Instant.now()` minus `letzter_empfang`, FR-1.8); das
   Frontend rechnet nicht selbst. Ein negativer Wert (Serveruhr zurückgestellt) wird auf `0`
   geklemmt.
   > Unabhängig von einer falsch gehenden Client-Uhr und – dank absoluter Zeitpunkte – von der
   > Umstellung auf Winterzeit.

   | Alter                       | Farbe     | Text                                                       |
   |-----------------------------|-----------|------------------------------------------------------------|
   | unter 300 s                 | `success` | `vor {0} Min.` (abgerundet, also auch `vor 0 Min.`)        |
   | 300 s bis einschl. 3600 s   | `warning` | `vor {0} Min.`                                             |
   | über 3600 s                 | `error`   | `vor {0} Std.` bis unter 48 Std., ab 48 Std. `vor {0} Tagen` – beides abgerundet |

   Bei genau 3600 s steht also `vor 60 Min.` in `warning`, ab 3601 s `vor 1 Std.` in `error` – gewollt.
   > 5 Minuten: Das Pi-Gateway sendet minütlich, gPlug alle 10 bis 60 Sekunden. Nach 5 Minuten
   > ohne Meldung ist mehr als ein einzelner Aussetzer passiert.

   Bei Kein Empfang entfällt das Alters-Badge.
6. **Sortierung** per Spaltenklick (wie `mieter-list`) auf Messpunkt, Einheiten, Quelle, Letzter
   Empfang, Meldungen, Erster Empfang und Ergebnis; die Zählerstände sind nicht sortierbar. Standard: zuerst Messpunkte **ohne** Einheit,
   dann **Kein Empfang**, dann die übrigen; innerhalb der Gruppen mit Empfang nach letztem Empfang
   absteigend, Kein Empfang nach Messpunkt aufsteigend.
   > Ohne Einheit und Kein Empfang zuerst: Genau diese Zeilen sucht, wer einen Zähler in Betrieb
   > nimmt.
7. Schaltfläche **Aktualisieren** (`AKTUALISIEREN`, `zev-button--compact`) lädt die Tabelle neu. Kein
   automatisches Nachladen.
8. **Kebab-Menü** je Zeile mit den Einträgen, die für die Zeile und die Permissions des Users gelten:
   * **Letzte Meldung anzeigen** (`MESSPUNKT_EMPFANG_LETZTE_MELDUNG`, Icon `eye`) – bei jeder Zeile
     mit Empfang, schon mit `einheit:read`, siehe FR-2.9.
   * **Einheit anlegen** – nur mit `einheit:write`, – nur bei einer Zeile **ohne** Einheit und mit Messpunkt von höchstens
     50 Zeichen (Länge von `zev.einheit.messpunkt`). Öffnet die Einheitenverwaltung mit geöffnetem
     Formular und vorausgefülltem Messpunkt (`/einheiten?neu=1&messpunkt=<messpunkt>`, URL-kodiert),
     siehe `Specs/Einheitenverwaltung.md` FR-8.
     > Länger als 50 Zeichen: Das Formular schnitte den Messpunkt still ab, und die neue Einheit
     > passte wieder nicht. Der Eintrag fehlt dann; der Messpunkt ist ohnehin falsch konfiguriert.
   * **Löschen** (`LOESCHEN`) – nur mit `einheit:write` und nur bei Zeilen mit Empfang
     (Kein-Empfang-Zeilen haben keine Empfangszeile), siehe FR-3.1.

   Hat eine Zeile keinen Eintrag, erscheint dort kein Kebab-Knopf. Die Kebab-Spalte steht immer da:
   Jeder User mit Zugriff auf die Seite kann eine Meldung ansehen.
9. **Letzte Meldung anzeigen.** Der Eintrag lädt den Payload (`GET …/{id}/letzte-meldung`) und
   zeigt ihn in einer Zeile direkt unter der gewählten, über die ganze Tabellenbreite:
   * als `.zev-code-block` (Design System, Festbreitenschrift, Umbruch langer Zeilen);
   * ist der Payload gültiges JSON, eingerückt formatiert (2 Leerzeichen), sonst unverändert;
   * ohne festgehaltenen Payload (Zeilen von vor der Erweiterung) `MESSPUNKT_EMPFANG_KEINE_MELDUNG`;
   * darüber die Schaltfläche **Schliessen** (`SCHLIESSEN`, `zev-button--compact`); es ist immer
     höchstens **eine** Meldung offen – eine zweite ersetzt die erste;
   * **Aktualisieren** schliesst die offene Meldung, weil sie danach veraltet sein kann;
   * scheitert das Laden, erscheint `FEHLER_BEIM_LADEN_DER_DATEN`.
   > Der Payload kommt vom Gerät und ist **nie** als HTML zu rendern – nur als Text (Angular-
   > Interpolation), damit ein präparierter Payload kein Markup einschleusen kann.

### FR-3: Aufräumen
1. Ein User mit `einheit:write` kann eine Zeile **löschen** (Kebab-Menü, Bestätigung
   `MESSPUNKT_EMPFANG_LOESCHEN_BESTAETIGEN`). Danach Erfolgsmeldung `MESSPUNKT_EMPFANG_GELOESCHT`
   (verschwindet nach 5 s). Sendet der Messpunkt weiter, entsteht die Zeile mit der nächsten
   Meldung neu, mit neuem erstem Empfang und `anzahl_meldungen = 1`.
2. Ein täglicher Job (`MesspunktEmpfangCleanupJob`, ruft wie `SystemmeldungCleanupJob` eine
   Service-Methode auf; diese löscht **mandantenübergreifend** per nativer Query ohne `orgFilter`,
   mit `NOT EXISTS` auf `zev.einheit` ohne Ladestationen; Cron
   `${messpunkt-empfang.retention.cron:0 30 3 * * *}`, `zone = "Europe/Zurich"`) löscht Zeilen, deren
   Messpunkt **keiner** Einheit des Mandanten (ausser Ladestationen) zugeordnet ist und deren
   `letzter_empfang` **strikt vor** `jetzt − 30 Tage` liegt. Zeilen mit Einheit bleiben – ihr Alter
   ist selbst eine Auskunft. Zeilen einer `org_id`, die keinem Mandanten entspricht, haben nie eine
   Einheit und fallen ebenso nach 30 Tagen weg.
   > Sonst sammeln sich Tippfehler aus der Inbetriebnahme (`Wohnung-1` statt `Wohnung1`) für immer an.

### Datenmodell & Persistierung
* **Tabelle:** `zev.messpunkt_empfang`
* **Spalten:**

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
| `letzte_meldung`           | TEXT         | nein    | Payload der letzten Meldung, höchstens 10'000 Zeichen (FR-1.9); `NULL` bei Zeilen von vor V188 |

* **Primary Key:** `id`
* **Foreign Keys:** n/a – bewusst **kein** FK auf `zev.einheit`: Der Empfang besteht gerade auch ohne
  Einheit (FR-1.7). Die Einheiten werden beim Lesen über (`org_id`, `messpunkt`) zugeordnet.
* **Unique-Constraints:** (`org_id`, `messpunkt`) – für den Upsert. Zusätzlich als
  `@UniqueConstraint` an der Entity und die Sequenz per `@SequenceGenerator`, weil die ITs das
  Schema aus dem Mapping bauen; die CHECKs prüft
  `FlywaySchemaIT` gegen das Flyway-Schema.
* **Upsert:** native Query `INSERT … VALUES (nextval('zev.messpunkt_empfang_seq'), …) ON CONFLICT
  (org_id, messpunkt) DO UPDATE SET …` – kein Lesen vor dem Schreiben; bei einem Konflikt verfällt
  der gezogene Sequenzwert, das ist hinnehmbar:
  `letzter_empfang`, Quelle, Stände, Ergebnis, Grund und letzte Meldung werden überschrieben, `anzahl_meldungen`
  um 1 erhöht, `erster_empfang` bleibt.
* **Multi-Tenancy (`org_id`):** **ja** — mandantenspezifische Daten. Der Ingest setzt `org_id` aus dem
  Topic (kein Request-Kontext); Lese- und Löschmethoden laufen mit
  `hibernateFilterService.enableOrgFilter()` und `@Filter` an der Entity.
* **Flyway-Migration:** `V186__Create_Messpunkt_Empfang.sql` (Tabelle, Constraints,
  Spaltenkommentare), `V187__Add_Messpunkt_Empfang_Translations.sql`, dazu
  `V188__Messpunkt_Empfang_Letzte_Meldung.sql` (Spalte `letzte_meldung` samt Kommentar und
  Übersetzungen der Erweiterung).

### API-Endpunkte

| Methode | Pfad                          | Permission      | Request-Body | Response                    | Status-Codes    |
|---------|-------------------------------|-----------------|--------------|-----------------------------|-----------------|
| GET     | `/api/messpunkt-empfang`      | `einheit:read`  | –            | `List<MesspunktEmpfangDTO>` | 200 / 403       |
| GET     | `/api/messpunkt-empfang/{id}/letzte-meldung` | `einheit:read` | – | `MesspunktEmpfangMeldungDTO` | 200 / 403 / 404 |
| DELETE  | `/api/messpunkt-empfang/{id}` | `einheit:write` | –            | –                           | 204 / 403 / 404 |

* **`MesspunktEmpfangDTO`:** `id` (`null` bei Kein Empfang), `messpunkt`, `einheiten: List<String>`,
  `quelle`, `ersterEmpfang`, `letzterEmpfang` (beide als Ortszeit `Europe/Zurich` serialisiert),
  `sekundenSeitEmpfang` (Long, ≥ 0), `anzahlMeldungen`,
  `zaehlerstandBezug`, `zaehlerstandEinspeisung`, `ergebnis`, `grund`, `keinEmpfang` (boolean). Bei
  Kein Empfang sind alle Empfangsfelder `null`. **Kein** `orgId` und **kein** Payload: Die Liste
  bleibt schlank, der Payload kommt einzeln, wenn ihn jemand ansehen will.
* **`MesspunktEmpfangMeldungDTO`:** `payload` (String, `null` ohne festgehaltenen Payload).
  `GET …/letzte-meldung` sucht wie `DELETE` über `findFirstById` unter dem `orgFilter`; fremde
  oder unbekannte ID → 404.
* **Autorisierung** wie `EinheitController`: `@PreAuthorize("hasAuthority('einheit:write')")` auf der
  Klasse, beide `GET` mit `@PreAuthorize("hasAuthority('einheit:read')")` überschrieben.
* **`DELETE`** sucht über `findFirstById` mit aktivem `orgFilter` (Muster `TarifRepository`), nicht
  über `findById`, das den Filter umgeht. Fremde oder unbekannte ID → 404.
* **Profile:** Controller, Service und Cleanup-Job laufen **ohne** `@Profile("mqtt")` (wie
  `SystemmeldungCleanupJob`): Die Ansicht soll auch ohne MQTT erreichbar sein und zeigt dann Kein
  Empfang. Nur das Festhalten im Ingest hängt am MQTT-Profil.

### Validierungen

| Feld | Regel | FE / BE | Fehlermeldung (Translation-Key) |
|------|-------|---------|---------------------------------|
| n/a  | Keine Benutzereingaben ausser Löschen; die Werte stammen aus dem Ingest | – | – |

### Übersetzungen (i18n)

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
| `MESSPUNKT_EMPFANG_LETZTE_MELDUNG`        | Letzte Meldung anzeigen                   | Show last message                     |
| `MESSPUNKT_EMPFANG_KEINE_MELDUNG`         | Keine Meldung gespeichert.                | No message stored.                    |
| `SCHLIESSEN`                              | Schliessen                                | Close                                 |

* **Wiederverwendet (vorhanden):** `MESSPUNKT`, `EINHEITEN` (Spaltenkopf), `AKTUALISIEREN`,
  `LOESCHEN` (Kebab), `FEHLER_BEIM_LADEN_DER_DATEN` (Laden scheitert).
* Die Gründe bei `UNGUELTIG` (FR-1.3) bleiben technischer Klartext wie in den Logzeilen – sie richten
  sich an den, der den Zähler konfiguriert.

### Layout & Navigation
* **Route:** `/messpunkt-empfang` → `MesspunktEmpfangComponent` (`AuthGuard`, `data: { permissions: ['einheit:read'] }`)
* **Menüeintrag:** im Admin-Bereich direkt unter **Einheiten** – Eintrag in `adminEintraege`
  (`navigation.component.ts`) nach `/einheiten`, Feather-Icon `radio`
* **Seitentitel:** `MESSPUNKT_EMPFANG` mit Feather-Icon `radio`
* **Design-System-Klassen:** `.zev-container`, `.zev-table` (sortierbar wie `mieter-list`) in
  `.zev-table-scroll` (zehn Spalten: auf schmalen Bildschirmen scrollt die Tabelle, nicht die Seite),
  `.zev-status--success/--warning/--error` für beide Badges, `.zev-kebab-menu`, `.zev-message`,
  `.zev-button--compact` für Aktualisieren und Schliessen, `.zev-code-block` für die Meldung (neu
  im Design System, `typography.css`, samt Showcase und `Specs/generell.md`). Kein weiteres CSS ohne
  Prüfung des Design Systems.
* **Datumsformat:** `formatSwissDateTime` (`utils/date-utils.ts`) liefert nur `HH:mm`; für
  `dd.MM.yyyy HH:mm:ss` bekommt die Utility einen Parameter für Sekunden, samt Unit-Test.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

### Empfang festhalten
* [x] Eine gültige Meldung eines bekannten Messpunkts legt die Zeile mit `GESPEICHERT`, Quelle,
      Zählerständen, `anzahl_meldungen = 1` und `erster_empfang = letzter_empfang` an.
      (Test: MqttIngestServiceTransaktionIT#handle_CommitGelingt_ZeileGespeichertMitBackendUhr;
      MqttIngestServiceTest#handle_GueltigeMeldung_VermerkGespeichertMitQuelleUndStaenden;
      MesspunktEmpfangRepositoryIT#upsert_ErsteMeldung_LegtZeileMitPayloadAn)
* [x] Eine zweite Meldung aktualisiert dieselbe Zeile: `anzahl_meldungen = 2`, `letzter_empfang` und
      Stände neu, `erster_empfang` unverändert.
      (Test: MesspunktEmpfangRepositoryIT#upsert_ZweiteMeldung_UeberschreibtPayloadUndZaehltHoch)
* [x] Pi-Gateway-Meldungen tragen Quelle `PI_GATEWAY`, gPlug-Meldungen `GPLUG`.
      (Test: MqttIngestServiceTest#handle_GueltigeMeldung_VermerkGespeichertMitQuelleUndStaenden;
      MqttIngestServiceTest#handle_GplugGueltig_VermerkGespeichertQuelleGplug)
* [x] Eine Meldung eines unbekannten Messpunkts legt eine Zeile mit `UNBEKANNTER_MESSPUNKT` an und
      speichert **keine** Rohdaten.
      (Test: MqttIngestServiceTest#handle_UnbekannterMesspunkt_VermerkUnbekanntMitStaendenOhneRohdaten;
      MqttIngestServiceTest#handle_GplugUnbekannterMesspunkt_VermerkUnbekannt)
* [x] Ein Messpunkt, den nur eine Ladestation trägt, gilt als unbekannt.
      (Test: MqttIngestServiceTest#handle_NurLadestationAmMesspunkt_VermerkUnbekannterMesspunkt)
* [x] Jeder Grund aus FR-1.3 (Payload nicht lesbar, Pflichtfeld fehlt, gPlug-Uhr nicht
      synchronisiert, `Ei = Eo = 0`, negativer Zählerstand) ergibt `UNGUELTIG` mit genau diesem
      `grund`; ein Feld vom falschen Typ und eine unlesbare gPlug-`Time` ergeben `Payload nicht lesbar`.
      (Test: MqttIngestServiceTest#handle_KeinJson_VermerkPayloadNichtLesbarOhneStaende;
      MqttIngestServiceTest#handle_GplugKeinJson_VermerkPayloadNichtLesbar;
      MqttIngestServiceTest#handle_FeldVomFalschenTyp_VermerkPayloadNichtLesbar;
      MqttIngestServiceTest#handle_GplugUnlesbareTime_VermerkPayloadNichtLesbarMitStaenden;
      MqttIngestServiceTest#handle_OhneTimestamp_VermerkPflichtfeldFehltMitLesbarenStaenden;
      MqttIngestServiceTest#handle_GplugOhneEi_VermerkPflichtfeldFehltBezugNull;
      MqttIngestServiceTest#handle_GplugUhrNichtSynchronisiert_VermerkGplugUhr;
      MqttIngestServiceTest#handle_GplugEiUndEoNull_VermerkEiEoNullMitNullStaenden;
      MqttIngestServiceTest#handle_NegativerStand_VermerkNegativMitNegativemStand;
      Prüfreihenfolge: MqttIngestServiceTest#handle_FalscherTypUndPflichtfeldFehlt_LesbarkeitZuerst,
      #handle_GplugUnlesbareTimeOhneEi_LesbarkeitVorVollstaendigkeit,
      #handle_PflichtfeldFehltUndNegativ_PflichtfeldZuerst,
      #handle_GplugUhrNichtSynchronisiertUndNullen_UhrZuerst;
      MqttIngestServiceTest#handle_GplugFeldVomFalschenTyp_VermerkPayloadNichtLesbar;
      MqttIngestServiceTest#handle_PayloadJsonNull_VermerkPayloadNichtLesbar)
* [x] Wirft der Ingest im allgemeinen `catch` (z.B. beim Schreiben der Rohdaten), zeigt die Zeile
      `UNGUELTIG` / `Verarbeitung fehlgeschlagen` – nicht `GESPEICHERT`, obwohl die Transaktion
      committet. (Test: MqttIngestServiceTest#handle_AllgemeinesCatch_VermerkVerarbeitungFehlgeschlagenNichtGespeichert)
* [x] Ein beim gPlug fehlendes `z.Eo` erscheint in der Zeile als `NULL`.
      (Test: MqttIngestServiceTest#handle_GplugOhneEo_VermerkEinspeisungNullRohdatenNull;
      MqttIngestServiceTest#handle_GplugEiNullOhneEo_VermerkEiEoNullEinspeisungNull)
* [x] Ein ungültiger Payload eines unbekannten Messpunkts ergibt `UNGUELTIG`, nicht
      `UNBEKANNTER_MESSPUNKT` (Prüfreihenfolge).
      (Test: MqttIngestServiceTest#handle_UngueltigerPayloadUnbekannterMesspunkt_VermerkUngueltig)
* [x] Bei `UNGUELTIG` stehen die lesbaren Stände (auch negativ) in der Zeile; bei nicht lesbarem
      Payload sind beide `NULL`, auch wenn eine frühere Meldung Stände trug.
      (Test: MqttIngestServiceTest#handle_NegativerStand_VermerkNegativMitNegativemStand;
      MqttIngestServiceTest#handle_KeinJson_VermerkPayloadNichtLesbarOhneStaende;
      MesspunktEmpfangRepositoryIT#upsert_UnlesbareMeldungNachStaenden_StaendeNull)
* [x] Eine Meldung mit unlesbarem Topic, leerem Messpunkt oder einem Messpunkt über 255 Zeichen legt
      **keine** Zeile an. (Test: MqttIngestServiceTest#handle_TopicNichtLesbar_KeinVermerk;
      MqttIngestServiceTest#handle_OhneTopic_KeinVermerk;
      MqttIngestServiceTest#handle_MesspunktUeber255Zeichen_KeinVermerk;
      MqttIngestServiceTest#handle_MesspunktGenau255Zeichen_Vermerk)
* [x] `letzter_empfang` ist die Backend-Uhr als absoluter Zeitpunkt, nicht die Zeit aus dem Payload.
      (Test: MesspunktEmpfangServiceTest#halteFest_ZeitpunktIstBackendUhr;
      MqttIngestServiceTransaktionIT#handle_CommitGelingt_ZeileGespeichertMitBackendUhr)
* [x] Scheitert der Commit der Ingest-Transaktion, zeigt die Zeile `UNGUELTIG` /
      `Speichern fehlgeschlagen`, nicht `GESPEICHERT` (Transaktions-IT nach Vorlage
      `SteuerungServiceTransaktionIT`, mit Gegenprobe).
      (Test: MqttIngestServiceTransaktionIT#handle_CommitScheitert_ZeileZeigtSpeichernFehlgeschlagen;
      MqttIngestServiceTest#handle_MitTransaktionRollback_VermerkSpeichernFehlgeschlagen)
* [x] Scheitert das Festhalten, sind die Rohdaten der Meldung trotzdem gespeichert (Transaktions-IT
      gegen PostgreSQL, nicht nur Mock; mit Gegenprobe).
      (Test: MqttIngestServiceFesthaltenTransaktionIT#handle_FesthaltenWirft_RohdatenSindTrotzdemGespeichert;
      MqttIngestServiceTest#handle_FesthaltenWirft_RohdatenGespeichertKeineException)
* [x] Der Upsert läuft per `ON CONFLICT` ohne vorheriges Lesen: Zwei aufeinanderfolgende Meldungen
      ergeben eine Zeile mit `anzahl_meldungen = 2` (IT gegen PostgreSQL).
      (Test: MesspunktEmpfangRepositoryIT#upsert_ZweiteMeldung_UeberschreibtPayloadUndZaehltHoch)
* [x] Löschen einer Einheit oder Ändern ihres Messpunkts lässt die Empfangszeile bestehen.
      (Test: FlywaySchemaIT#messpunktEmpfang_EinheitGeaendertUndGeloescht_ZeileBleibt)
* [x] Die CHECK-Constraints (Quelle, Ergebnis, Grund genau bei `UNGUELTIG`, Anzahl ≥ 1) und der
      Unique-Constraint bestehen im Flyway-Schema (`FlywaySchemaIT`).
      (Test: FlywaySchemaIT#messpunktEmpfang_UnbekannteQuelle_WirdAbgewiesen;
      FlywaySchemaIT#messpunktEmpfang_UnbekanntesErgebnis_WirdAbgewiesen;
      FlywaySchemaIT#messpunktEmpfang_GrundNichtGenauBeiUngueltig_WirdAbgewiesen;
      FlywaySchemaIT#messpunktEmpfang_AnzahlNull_WirdAbgewiesen;
      FlywaySchemaIT#messpunktEmpfang_DoppelterMesspunkt_WirdAbgewiesen;
      FlywaySchemaIT#messpunktEmpfang_GueltigeZeilen_WerdenAngenommen)
* [x] Nur `MesspunktEmpfangService` greift auf `MesspunktEmpfangRepository` zu (ArchUnit).
      (Test: ArchitectureTest#nurServiceGreiftAufMesspunktEmpfangRepositoryZu)

### Anzeige
* [x] Die Seite ist über Admin › Messpunkt-Empfang erreichbar und zeigt nur Zeilen des eigenen
      Mandanten. (Test Backend: MesspunktEmpfangRepositoryIT#findAllByOrderByLetzterEmpfangDesc_UnterOrgFilter_NurEigenerMandant;
      MesspunktEmpfangServiceTest#getAllMesspunktEmpfang_EmpfangUndEinheiten_ErgibtVereinigung;
      Test Frontend: navigation.component.spec.ts › Admin-Untermenü › should list the seven admin entries in order inside the submenu;
      E2E: messpunkt-empfang.spec.ts › Messpunkt-Empfang - Navigation › should open the view via Admin menu with title and columns in order)
* [x] Eine Einheit mit Messpunkt ohne Empfangszeile erscheint als **Kein Empfang** (rotes
      Ergebnis-Badge, leere Empfangsspalten, kein Alters-Badge); eine Einheit ohne Messpunkt und eine
      Ladestation erscheinen nicht.
      (Test Backend: MesspunktEmpfangServiceTest#getAllMesspunktEmpfang_EinheitOhneEmpfang_ZeileKeinEmpfangOhneEmpfangsfelder;
      MesspunktEmpfangServiceTest#getAllMesspunktEmpfang_EmpfangUndEinheiten_ErgibtVereinigung;
      MesspunktEmpfangServiceTest#getAllMesspunktEmpfang_NurLadestationAmMesspunkt_KeineKeinEmpfangZeile;
      Test Frontend: messpunkt-empfang.component.spec.ts › ergebnisFarbe (FR-2.4) › should render text and colour of the result badge;
      Formatierung (FR-2.3) › should show a dash for missing source, times, readings, count and units;
      Alters-Badge im Template (FR-2.5) › should render no age badge for a row without reception;
      E2E: messpunkt-empfang.spec.ts › Messpunkt-Empfang - Kein Empfang › should show a newly created unit with meter point as Kein Empfang)
* [x] Ein Messpunkt mit mehreren Einheiten (Bilanzmesspunkt) erscheint einmal, mit allen Einheiten.
      (Test: MesspunktEmpfangServiceTest#getAllMesspunktEmpfang_EmpfangUndEinheiten_ErgibtVereinigung)
* [x] Die Spalte Einheiten zeigt die aktuelle Zuordnung, auch wenn die Einheit erst nach der letzten
      Meldung angelegt wurde; ohne Zuordnung steht „–“.
      (Test Backend: MesspunktEmpfangServiceTest#getAllMesspunktEmpfang_EinheitErstNachEmpfangAngelegt_ZeigtAktuelleZuordnung;
      Test Frontend: messpunkt-empfang.component.spec.ts › Formatierung (FR-2.3) › should list all units of a meter point;
      should show a dash for missing source, times, readings, count and units)
* [x] Das Ergebnis-Badge trägt Text und Farbe gemäss FR-2.4; bei `UNGUELTIG` steht der Grund daneben.
      (Test: messpunkt-empfang.component.spec.ts › ergebnisFarbe (FR-2.4) › should map each result to its colour;
      should be error for a row without reception; should render text and colour of the result badge;
      should show the reason next to UNGUELTIG)
* [x] Das Backend liefert `sekundenSeitEmpfang` als Differenz absoluter Zeitpunkte; über die
      Umstellung auf Winterzeit hinweg stimmt das Alter, und ein negativer Wert wird zu `0`.
      (Test: MesspunktEmpfangServiceTest#getAllMesspunktEmpfang_UeberWinterzeitUmstellung_AlterAusAbsolutenZeitpunkten;
      MesspunktEmpfangServiceTest#getAllMesspunktEmpfang_LetzterEmpfangInDerZukunft_AlterAufNullGeklemmt;
      MesspunktEmpfangServiceTest#getAllMesspunktEmpfang_Alter_SekundenSeitLetztemEmpfang)
* [x] Das Alters-Badge folgt FR-2.5, einschliesslich der Grenzen 300 s und 3600 s und der
      abgerundeten Texte in Minuten, Stunden und Tagen.
      (Test: messpunkt-empfang.component.spec.ts › altersFarbe (FR-2.5) › should be success below 300 s; should be warning from
      300 s up to and including 3600 s; should be error above 3600 s; altersText (FR-2.5) › should show
      rounded-down minutes up to and including 3600 s; should show rounded-down hours from 3601 s up to
      below 48 hours; should show rounded-down days from 48 hours; should insert the number into the
      translated text; Alters-Badge im Template (FR-2.5) › should render the age badge with its colour
      for a row with reception)
* [x] Die Spalte Erster Empfang steht direkt vor Letzter Empfang. (Test: messpunkt-empfang.component.spec.ts ›
      Spaltenreihenfolge (FR-2.3) › should place first reception before last reception;
      E2E: messpunkt-empfang.spec.ts › Messpunkt-Empfang - Navigation › should open the view via Admin menu with title and columns in order)
* [x] Letzter Empfang erscheint als `dd.MM.yyyy HH:mm:ss` in Ortszeit (`formatSwissDateTime` mit
      Sekunden, eigener Unit-Test), Zählerstände mit drei Nachkommastellen im Schweizer Format, die
      Quelle als übersetzter Text; fehlende Quelle und `NULL`-Stände als „–“.
      (Test: date-utils.spec.ts › formatSwissDateTime › should append padded seconds when mitSekunden
      is true; should omit the seconds by default; should return the fallback with mitSekunden as well;
      messpunkt-empfang.component.spec.ts › Formatierung (FR-2.3) › should show the last reception with seconds and the first
      without; should show the meter readings with three decimals in Swiss format; should show the
      source as translated text; should show a dash for missing source, times, readings, count and units)
* [x] Standardsortierung: ohne Einheit, dann Kein Empfang, dann übrige, je Gruppe wie in FR-2.6;
      Spaltenklick sortiert um. (Test: messpunkt-empfang.component.spec.ts › Sortierung (FR-2.6) › should sort without unit
      first, then no reception, then the others; should sort by column and toggle the direction on a
      second click; should sort when the column header is clicked; should put rows without a value at
      the end; should sort the result with no reception as its own value)
* [x] **Aktualisieren** lädt die Liste neu. (Test: messpunkt-empfang.component.spec.ts › Laden (FR-2.7) › should reload the list on Aktualisieren)
* [x] Ohne Zeilen erscheint `MESSPUNKT_EMPFANG_KEINE_DATEN`. (Test: messpunkt-empfang.component.spec.ts › Laden (FR-2.7) ›
      should show the empty state without rows; should not show the empty state when there are rows)
* [x] Scheitert das Laden, erscheint `FEHLER_BEIM_LADEN_DER_DATEN` als Fehlermeldung.
      (Test: messpunkt-empfang.component.spec.ts › Laden (FR-2.7) › should show an error message when loading fails)
* [x] Der Menüeintrag steht im Admin-Bereich direkt nach Einheiten, mit Icon `radio`.
      (Test: navigation.component.spec.ts › Admin-Untermenü › should place Messpunkt-Empfang directly
      after Einheiten with icon radio; should list the seven admin entries in order inside the submenu)
* [x] Ohne MQTT-Profil ist die Ansicht erreichbar und zeigt Einheiten mit Messpunkt als Kein Empfang.
      (Test Backend: MesspunktEmpfangCleanupJobTest#komponenten_OhneMqttProfil;
      MesspunktEmpfangServiceTest#getAllMesspunktEmpfang_EinheitOhneEmpfang_ZeileKeinEmpfangOhneEmpfangsfelder)

### Letzte Meldung
* [x] Der Ingest hält den Payload der letzten Meldung bei jedem Ergebnis fest, auch bei
      `UNGUELTIG` und bei nicht lesbarem Payload; die nächste Meldung überschreibt ihn.
      (Test: MqttIngestServiceTest#handle_GueltigeMeldung_HaeltPayloadFest;
      MqttIngestServiceTest#handle_UnlesbarerPayload_HaeltPayloadTrotzdemFest;
      MesspunktEmpfangServiceTest#halteFest_UebergibtPayloadAnUpsert;
      MesspunktEmpfangRepositoryIT#upsert_ZweiteMeldung_UeberschreibtPayloadUndZaehltHoch)
* [x] Ein Payload über 10'000 Zeichen wird gekürzt gespeichert und endet mit `…[gekürzt]`.
      (Test: MesspunktEmpfangServiceTest#halteFest_PayloadUeber10000Zeichen_WirdGekuerztMitHinweis;
      MesspunktEmpfangServiceTest#kuerze_Genau10000Zeichen_Unveraendert)
* [x] `GET …/{id}/letzte-meldung` liefert den Payload; ohne festgehaltenen Payload ist er `null`.
      (Test: MesspunktEmpfangControllerTest#getLetzteMeldung_Found_ReturnsPayload;
      MesspunktEmpfangControllerTest#getLetzteMeldung_OhnePayload_ReturnsNull;
      MesspunktEmpfangServiceTest#getLetzteMeldung_OhnePayload_ReturnsNullPayload)
* [x] **Letzte Meldung anzeigen** erscheint bei jeder Zeile mit Empfang, auch ohne
      `einheit:write`, und bei Kein-Empfang-Zeilen nicht. (Test: messpunkt-empfang.component.spec.ts › Kebab-Menü (FR-2.8) ›
      should offer the last message for every row with reception, also without einheit:write;
      should not offer the last message for a row without reception)
* [x] Die Meldung erscheint in einer Zeile unter der gewählten; gültiges JSON eingerückt, anderer
      Text unverändert, ohne Payload `MESSPUNKT_EMPFANG_KEINE_MELDUNG`. (Test: messpunkt-empfang.component.spec.ts ›
      onLetzteMeldung (FR-2.9) › should show the payload as indented JSON below the row; should
      show text that is not JSON unchanged; should show a hint when no payload is stored;
      E2E: messpunkt-empfang.spec.ts › Messpunkt-Empfang - Letzte Meldung › should show the last message below the row, replace it and close it)
* [x] Es ist höchstens eine Meldung offen; **Schliessen** und **Aktualisieren** schliessen sie.
      (Test: messpunkt-empfang.component.spec.ts › onLetzteMeldung (FR-2.9) › should keep at most one message open; should close
      the message with Schliessen; should close the message on Aktualisieren;
      E2E: messpunkt-empfang.spec.ts › Messpunkt-Empfang - Letzte Meldung › should show the last message below the row, replace it and close it)
* [x] Scheitert das Laden der Meldung, erscheint `FEHLER_BEIM_LADEN_DER_DATEN`.
      (Test: messpunkt-empfang.component.spec.ts › onLetzteMeldung (FR-2.9) › should show an error message when loading fails)
* [x] Ein Payload mit Markup (z.B. `<b>x</b>`) erscheint als Text, nicht als HTML.
      (Test: messpunkt-empfang.component.spec.ts › onLetzteMeldung (FR-2.9) › should render markup in the payload as text, not as HTML;
      E2E: messpunkt-empfang.spec.ts › Messpunkt-Empfang - Letzte Meldung › should show the last message below the row, replace it and close it)

### Einheit anlegen
* [x] **Einheit anlegen** erscheint nur bei Zeilen ohne Einheit mit Messpunkt bis 50 Zeichen und nur
      mit `einheit:write`. (Test: messpunkt-empfang.component.spec.ts › Einheit anlegen (FR-2.8) › should offer creating a unit
      only for rows without unit; should offer creating a unit up to 50 characters but not beyond;
      Kebab-Menü (FR-2.8) › should hide create and delete without einheit:write)
* [x] Der Eintrag öffnet die Einheitenverwaltung mit geöffnetem Formular und vorausgefülltem
      Messpunkt (`Specs/Einheitenverwaltung.md` FR-8). (Test: messpunkt-empfang.component.spec.ts › Einheit anlegen (FR-2.8) ›
      should open the unit management with prefilled meter point; einheit-list.component.spec.ts ›
      neue Einheit per URL (FR-8) › should open the form with the meter point from the URL and clear the
      parameters; einheit-form.component.spec.ts › should prefill the meter point of a new unit and keep
      the other defaults (FR-8);
      E2E: messpunkt-empfang.spec.ts › Messpunkt-Empfang - Einheit anlegen › should open the unit form with the meter point prefilled from a row without unit)

### Aufräumen
* [x] Löschen entfernt die Zeile nach Bestätigung und zeigt `MESSPUNKT_EMPFANG_GELOESCHT`;
      abgebrochen bleibt sie. (Test: messpunkt-empfang.component.spec.ts › onDelete (FR-3) › should delete after confirmation,
      show a success message and reload; should not call delete when cancelled; should auto-dismiss the
      success message after 5s)
* [x] Scheitert das Löschen, erscheint `MESSPUNKT_EMPFANG_FEHLER_LOESCHEN`, und die Zeile bleibt.
      (Test: messpunkt-empfang.component.spec.ts › onDelete (FR-3) › should show an error message and keep the row when deleting
      fails; should keep the error message until dismissed)
* [x] **Löschen** erscheint nur mit `einheit:write` und nur bei Zeilen mit Empfang; eine Zeile ohne
      Kebab-Eintrag hat keinen Kebab-Knopf. (Test: messpunkt-empfang.component.spec.ts › Kebab-Menü (FR-2.8) › should offer delete
      only for rows with reception; should hide create and delete without einheit:write; should
      render no kebab button for a row without entries)
* [x] Nach dem Löschen legt die nächste Meldung die Zeile neu an, mit `anzahl_meldungen = 1`.
      (Test: MesspunktEmpfangRepositoryIT#upsert_NachLoeschen_LegtZeileNeuAn)
* [x] Der Job löscht Zeilen ohne Einheit, deren letzter Empfang strikt vor `jetzt − 30 Tage` liegt;
      eine Zeile mit genau 30 Tagen und Zeilen mit Einheit bleiben. Er läuft über alle Mandanten
      und ohne MQTT-Profil.
      (Test: MesspunktEmpfangRepositoryIT#loescheVerwaisteVor_LoeschtNurZeilenOhneEinheitStriktVorDerGrenze;
      MesspunktEmpfangServiceTest#raeumeAuf_LoeschtVorJetztMinus30Tagen;
      MesspunktEmpfangCleanupJobTest#bereinige_RuftRaeumeAuf;
      MesspunktEmpfangCleanupJobTest#komponenten_OhneMqttProfil)

### Sicherheit & API
* [x] Route `/messpunkt-empfang` ist mit `AuthGuard` und `einheit:read` geschützt.
      (Test: app.routes.spec.ts › messpunkt-empfang › should protect the route with AuthGuard and einheit:read)
* [x] `GET /api/messpunkt-empfang` ohne `einheit:read` → 403.
      (Test: ControllerAuthorizationTest#getAllMesspunktEmpfang_withoutEinheitRead_forbidden;
      ControllerAuthorizationTest#getAllMesspunktEmpfang_withEinheitRead_ok)
* [x] `DELETE /api/messpunkt-empfang/{id}` ohne `einheit:write` → 403 (z.B. `zev_user`).
      (Test: ControllerAuthorizationTest#deleteMesspunktEmpfang_withReadPermissionOnly_forbidden;
      ControllerAuthorizationTest#deleteMesspunktEmpfang_withEinheitWrite_noContent)
* [x] `DELETE` und `GET …/letzte-meldung` auf eine nicht existierende oder fremde Zeile → 404.
      (Test: MesspunktEmpfangControllerTest#getLetzteMeldung_NotFound_Returns404;
      MesspunktEmpfangControllerTest#deleteMesspunktEmpfang_NotFound_Returns404;
      MesspunktEmpfangServiceTest#getLetzteMeldung_NotFound_ReturnsEmpty;
      MesspunktEmpfangServiceTest#deleteMesspunktEmpfang_NotFound_ReturnsFalse;
      MesspunktEmpfangRepositoryIT#findFirstById_FremdeZeileUnterOrgFilter_Leer)
* [x] `GET …/letzte-meldung` ohne `einheit:read` → 403.
      (Test: ControllerAuthorizationTest#getLetzteMeldung_withoutEinheitRead_forbidden;
      ControllerAuthorizationTest#getLetzteMeldung_withEinheitRead_ok)
* [x] Ohne `einheit:write` enthält das Kebab-Menü weder **Einheit anlegen** noch **Löschen**.
      (Test: messpunkt-empfang.component.spec.ts › Kebab-Menü (FR-2.8) › should hide create and delete without einheit:write)
* [x] Die Antwort enthält kein `orgId`.
      (Test: MesspunktEmpfangControllerTest#getAllMesspunktEmpfang_AntwortOhneOrgIdUndPayload)

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Das Festhalten ist **ein** Upsert je Meldung in einer eigenen, kurzen Transaktion – keine
  vorgelagerte Abfrage. Bei rund einer Meldung pro Minute und Messpunkt ist die zusätzliche
  Transaktion vernachlässigbar; sie ist der Preis dafür, dass die Anzeige die Messung nie kostet
  (FR-1.6).
* `GET` liefert die ganze Liste ohne Paging (erwartet: unter 100 Zeilen je Mandant) in unter 200 ms.
  Die Einheiten werden mit **einer** Abfrage je Aufruf geladen, nicht je Zeile.

### NFR-2: Sicherheit
* **Backend:** wie `EinheitController` – Klasse `einheit:write`, `GET` mit `einheit:read`.
* **Frontend:** Route mit `AuthGuard` und `data.permissions: ['einheit:read']`; die Kebab-Einträge
  **Einheit anlegen** und **Löschen** nur mit `einheit:write`.
* **Payload als Text:** Der Payload stammt vom Gerät und wird nur per Interpolation angezeigt, nie
  über `innerHTML` (FR-2.9).
* **Fachrollen:** Lesen `zev_user`, `org_admin`, `zev_admin`; Löschen `org_admin`, `zev_admin`
  (`Specs/Berechtigungen.md`, bestehende Permissions, keine neue).
* **Mandanten-Isolation:** `org_id` stammt aus dem Topic, das die Broker-ACL je Mandant einschränkt
  (`docs/Netzwerk-Topologie-Hene.md`). Zeilen mit einer `org_id`, die keinem Mandanten entspricht,
  sieht niemand; der Job räumt sie auf (FR-3.2).
* **Architektur:** Eine ArchUnit-Regel in `ArchitectureTest` stellt sicher, dass nur
  `MesspunktEmpfangService` auf `MesspunktEmpfangRepository` zugreift; Ingest und Cleanup-Job gehen
  über den Service.

### NFR-3: Kompatibilität
* Kein Einfluss auf Rohdaten, Aggregation, Messwerte, Systemmeldungen und Metriken.
* Die Tabelle startet leer; es gibt keine Datenmigration. Einheiten mit Messpunkt erscheinen sofort
  als Kein Empfang, bis ihre erste Meldung eintrifft.

## 5. Edge Cases & Fehlerbehandlung

| Fall | Verhalten |
|------|-----------|
| **Leere Liste** (keine Empfangszeilen, keine Einheit mit Messpunkt) | Hinweis `MESSPUNKT_EMPFANG_KEINE_DATEN` |
| **Netzwerkfehler beim Laden** | `FEHLER_BEIM_LADEN_DER_DATEN` als `.zev-message--error`, bleibt bis geschlossen |
| **Netzwerkfehler beim Löschen** | `MESSPUNKT_EMPFANG_FEHLER_LOESCHEN`, Zeile bleibt |
| **Zeile wird gelöscht, während der Messpunkt sendet** | Die nächste Meldung legt sie neu an (FR-3.1) |
| **Messpunkt mit mehreren Einheiten** (Bilanzmesspunkt) | Eine Zeile, alle Einheiten in der Spalte |
| **Einheit erhält nachträglich den Messpunkt** | Spalte Einheiten zeigt die Zuordnung sofort; das Ergebnis bleibt `UNBEKANNTER_MESSPUNKT` bis zur nächsten Meldung |
| **Messpunkt über 50 Zeichen ohne Einheit** | Kein Eintrag „Einheit anlegen“ (FR-2.8) |
| **Messpunkt über 255 Zeichen** | Keine Zeile, `WARN` im Log (FR-1.5) |
| **Viele Messpunkte durch Fehlkonfiguration** (z.B. Seriennummer im Topic) | Kein Limit; der 30-Tage-Job räumt auf, Löschen ist einzeln möglich |
| **Commit der Ingest-Transaktion scheitert** | `UNGUELTIG` / `Speichern fehlgeschlagen` (FR-1.6) |
| **Festhalten scheitert** | `WARN` im Log; Rohdaten bleiben gespeichert (FR-1.6) |
| **Doppelte Stunde der Winterzeit-Umstellung** | Absolute Zeitpunkte: Das Alter stimmt, das Badge springt nicht um 60 Minuten (FR-1.8, FR-2.5) |
| **Serveruhr zurückgestellt** (Alter negativ) | `sekundenSeitEmpfang = 0` (FR-2.5) |
| **Gültiges JSON mit falschem Feldtyp** | `UNGUELTIG` / `Payload nicht lesbar` (FR-1.3) |
| **Exception im allgemeinen `catch`** | `UNGUELTIG` / `Verarbeitung fehlgeschlagen`, nicht `GESPEICHERT` (FR-1.3) |
| **Backend ohne MQTT-Profil** | Ansicht erreichbar; alle Einheiten mit Messpunkt als Kein Empfang |
| **Backend-Neustart** | Keine Wirkung; die Daten liegen in der Datenbank |
| **Gleichzeitige Meldungen desselben Messpunkts** | Kommt im Betrieb kaum vor (der MQTT-Adapter liefert sequentiell auf einem Thread); der Upsert per `ON CONFLICT` ist trotzdem atomar und zählt beide |
| **Ungültige oder fremde ID beim Löschen** | 404, nicht 500 |
| **Zeile von vor der Erweiterung** (kein Payload) | `MESSPUNKT_EMPFANG_KEINE_MELDUNG` (FR-2.9) |
| **Payload über 10'000 Zeichen** | Gekürzt, mit `…[gekürzt]` am Ende (FR-1.9) |
| **Payload mit Markup** | Als Text angezeigt (FR-2.9) |
| **Meldung offen, dann Aktualisieren** | Die Meldung schliesst sich (FR-2.9) |

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:** MQTT-Ingest (`Specs/MQTT-Integration.md`, FR-4, FR-10), Einheiten mit
  `messpunkt`, Permissions `einheit:read` / `einheit:write`.
* **Betroffener Code:**
  * `MqttIngestService`: Ergebnis je Meldung ermitteln (FR-1.3) und nach Abschluss der Transaktion
    festhalten (FR-1.6). Die Lesemethoden (`leseZevMeldung`, `leseGplugMeldung`) liefern Mandant,
    Messpunkt, Quelle und Grund auch bei verworfener Meldung; der JSON-Fehler wird dort abgefangen
    statt erst im allgemeinen `catch`.
  * Neu: Entity `MesspunktEmpfang`, `MesspunktEmpfangRepository` (Upsert per native Query,
    `findFirstById`), `MesspunktEmpfangService` (Festhalten mit `REQUIRES_NEW`, Liste, Löschen),
    `MesspunktEmpfangController`, `MesspunktEmpfangCleanupJob`, `MesspunktEmpfangDTO`,
    `MesspunktEmpfangMeldungDTO`.
  * Design System: `.zev-code-block` in `typography.css`, Showcase, Klassenliste in
    `Specs/generell.md`.
  * Frontend: Model, Service, `MesspunktEmpfangComponent`, Route, Eintrag in `adminEintraege`,
    `formatSwissDateTime` mit Sekunden.
  * **Einheitenverwaltung:** neue Anforderung `Specs/Einheitenverwaltung.md` FR-8 – die Liste liest
    `?neu=1&messpunkt=…`, öffnet das Formular für eine neue Einheit und füllt den Messpunkt vor.
  * `Specs/Berechtigungen.md`: Endpunkt-Referenz um `MesspunktEmpfangController` ergänzen.
  * `ArchitectureTest`: Regel aus NFR-2.
  * `application.yml`: `messpunkt-empfang.retention.cron`.
  * **Tests:** Die Transaktions-ITs (Vorlage `SteuerungServiceTransaktionIT`) binden
    `MqttIngestService` per `@Import` ein – er ist `@Profile("mqtt")` und wäre sonst nicht im
    Kontext.
* **Datenmigration:** keine.

## 7. Abgrenzung / Out of Scope
* **Kein Testbetrieb der Einheit**, kein Ausblenden in Aggregation, Statistik oder Abrechnung
  (Begründung in Abschnitt 1).
* Kein Verlauf: nur der **letzte** Stand je Messpunkt, keine Liste einzelner Meldungen. Den Verlauf
  bekannter Messpunkte zeigen Rohdaten und Messwerte.
* Keine Systemmeldung für unbekannte Messpunkte – die Ansicht ersetzt sie.
* Kein automatisches Nachladen, keine Push-Aktualisierung.
* Keine Anzeige von Gerätezustandswerten (`Specs/Gerätezustand.md`).
* Meldungen mit unlesbarem Topic und Messpunkte über 255 Zeichen (FR-1.5).
* Keine Übersetzung der Gründe bei `UNGUELTIG`.

## 8. Offene Fragen

* Reicht `einheit:read` zum Lesen, oder soll die Ansicht wie die Systemmeldungen nur Admins zeigen?
  --> `einheit:read` genügt. Die Ansicht enthält keine Personendaten.
* Soll **Einheit anlegen** (FR-2.8) Teil der ersten Umsetzung sein oder später kommen? Es verlangt
  eine Erweiterung der Einheitenverwaltung.
  --> Gleich mitnehmen: Bei der Inbetriebnahme ist es der nächste Handgriff, und ein abgetippter
  Messpunkt mit Tippfehler erzeugte wieder eine verwaiste Zeile.
* Menüplatz: unter **Einheiten** oder im Admin-Bereich bei den Systemmeldungen?
  --> Im Admin-Bereich direkt unter **Einheiten**.
* Schwellen für die Badge-Farben (5 / 60 Minuten) und die Aufbewahrung (30 Tage) – passend?
  --> Passt.
* Sollen auch Einheiten erscheinen, deren Messpunkt noch nie gesendet hat?
  --> Ja, als **Kein Empfang** (FR-2.2): der häufigste Fall bei der Inbetriebnahme.
* Ein Badge oder zwei?
  --> Zwei: Ergebnis-Badge (FR-2.4) und Alters-Badge (FR-2.5). Eine frische ungültige Meldung soll
  nicht grün erscheinen.
* Wann wird das Ergebnis festgehalten, und was gilt bei JSON- und Speicherfehlern?
  --> Nach Abschluss der Ingest-Transaktion in eigener Transaktion; Prüfreihenfolge und Gründe in
  FR-1.3 (Anforderungs-Review vom 25.10.2026).
* Wie wird das Alter über die Umstellung auf Winterzeit richtig, und was gilt bei Fehlern im
  allgemeinen `catch`?
  --> Empfangszeiten als `TIMESTAMPTZ` / `Instant` (FR-1.8); `GESPEICHERT` nur bei geschriebenen
  Rohdaten und gelungenem Commit, sonst `Verarbeitung fehlgeschlagen` (FR-1.3). Zweites
  Anforderungs-Review vom 25.10.2026.

## Anhang A – Entscheidungen und Verlauf

### A.1 Letzte Meldung und Spaltenreihenfolge (FR-1.9, FR-2.3, FR-2.8, FR-2.9, 25.10.2026)
Auf Wunsch des Users lässt sich die letzte Meldung (Payload) über das Kebab-Menü ansehen. Dafür
hält der Ingest den Payload fest (FR-1.9), und das Kebab-Menü gibt es nicht mehr nur mit
`einheit:write`: Vorher lautete FR-2.8 „Kebab-Menü je Zeile, **nur mit Permission
`einheit:write`**; ohne sie entfällt die Kebab-Spalte ganz“, und das Kriterium „Ohne
`einheit:write` zeigt die Tabelle keine Kebab-Spalte“ galt. Das Ansehen ist ein Lesezugriff und
braucht nur `einheit:read`.

Die Spalte **Erster Empfang** stand vorher nach **Meldungen** (Reihenfolge: Messpunkt, Einheiten,
Quelle, Letzter Empfang, Zählerstände, Meldungen, Erster Empfang, Ergebnis). Sie steht jetzt vor
**Letzter Empfang**, damit sich die Zeitspanne von links nach rechts liest.

### A.2 Kebab-Menü in kurzen Tabellen abgeschnitten (FR-2.8, 25.10.2026)
Die E2E-Tests fanden: Mit nur ein bis zwei Zeilen war das Kebab-Menü der obersten Zeile nicht
bedienbar. Die Tabelle steht in `.zev-table-scroll` (`overflow-x: auto`, damit auch `overflow-y`
nicht `visible`); das absolut positionierte Menü passte darin weder nach unten noch nach oben, es
klappte nach oben auf und wurde vom Rahmen abgeschnitten – „Letzte Meldung anzeigen“ war nicht
anklickbar. Gerade bei der Inbetriebnahme, wenn erst ein, zwei Messpunkte ohne Einheit dastehen.
Behoben im gemeinsamen Kebab-Menü: Es liegt fest im Fenster (`.zev-kebab-menu--fixed`,
`position: fixed`); beim Scrollen und bei Grössenänderung wird seine Lage neu berechnet, damit es
am Knopf bleibt. Das gilt für alle Listen, auch für Datenbank-Ansicht und Einspeisesteuerung, die
denselben Rahmen nutzen. Ein erster Ansatz schloss das Menü beim Scrollen; das scheiterte, weil
der Browser den Knopf vor dem Klick ins Bild holt und das Scroll-Ereignis erst nach dem Öffnen
ankommt – das Menü war sofort wieder zu, und die E2E-Suiten konnten ihre Testdaten nicht löschen.

