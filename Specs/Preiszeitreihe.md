# Preiszeitreihe

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Die **dynamischen Einspeisepreise** der BKW werden täglich automatisch bezogen, in einer Zeitreihe gespeichert und auf der Seite **Tarife** unterhalb der Tarifliste als Diagramm dargestellt. Der Benutzer wählt die dargestellte Spanne über **TAG / WOCHE / MONAT** oder über **Datum von / Datum bis**, kann **blättern** und **zoomen**.
* **Warum machen wir das:** Vorbereitung auf dynamische Einspeisetarife. Heute rechnet die Anwendung mit **festen** Preisen je Tarif und Zeitraum (`tarif`, `Specs/Tarifverwaltung.md`); künftige Einspeisevergütungen sind viertelstündlich variabel. Bevor die Abrechnung darauf umgestellt werden kann, braucht es die **Datengrundlage samt Historie** — und die entsteht nur, wenn ab jetzt täglich gesammelt wird: Die Quelle liefert **nur das laufende Fenster** (ca. 24 Stunden), eine Vergangenheit lässt sich später **nicht nachladen**. Jeder Tag ohne Abruf ist eine dauerhafte Lücke.
* **Aktueller Stand:**
  - Es gibt keine Preiszeitreihe. Preise stehen ausschliesslich als konstanter `preis` je `tarif`-Datensatz mit Gültigkeitszeitraum.
  - Kein Backend-Code ruft heute eine externe REST-API auf (die KI-Funktionen laufen über Spring AI, nicht über einen eigenen HTTP-Client). Ein HTTP-Client für Fremd-APIs ist **neu**.
  - Geplante Jobs existieren (`SystemmeldungCleanupJob`, `ZaehlerAggregationService`), `@EnableScheduling` ist aktiv — das Muster ist vorhanden.
  - Diagramme im Frontend laufen über **chart.js 4.5.1** (`messwerte-chart`). Für dieses Feature ist **ECharts** gefordert (Vorgabe); chart.js bleibt unverändert im Einsatz (siehe §7).

### Datenquelle (verifiziert am 27.08.2026)

`GET https://api.bkw.ch/api/dyntariffs/v1/Tariffs` (öffentlich, ohne Authentisierung), Antwort:

```json
{
  "publication_timestamp": "2026-08-27T13:50:00Z",
  "prices": [
    {
      "start_timestamp": "2026-08-26T22:00:00Z",
      "end_timestamp":   "2026-08-26T22:15:00Z",
      "feed_in": [ { "unit": "CHF_kWh", "value": 0.138 } ]
    }
  ]
}
```

* **Auflösung:** 15 Minuten — dasselbe Raster wie `messwerte`.
* **Zeitstempel:** ISO 8601 in **UTC** (`Z`).
* **Umfang:** 96 Einträge ≈ 24 Stunden (im Beispiel 26.08. 22:00Z bis 27.08. 22:00Z), also das laufende und das kommende Tarifintervall — **keine Historie**.
* **Einheit:** `CHF_kWh`. `feed_in` ist ein **Array**, enthält heute aber genau einen Eintrag.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Ablauf / Flow

**Automatischer Abruf**
1. Ein geplanter Job läuft **täglich um 02:00** (Cron über `application.yml` konfigurierbar).
2. Der Job ermittelt, welche Organisationen das Feature-Flag `PREISZEITREIHE` aktiv haben. Ist es **bei keiner** aktiv, endet er ohne HTTP-Aufruf (Log auf `debug`).
3. Der Job ruft die BKW-API ab, wandelt die Einträge um und schreibt sie per **Upsert** (FR-2).
4. Erfolg wird auf `info` protokolliert (abgerufen / neu / aktualisiert). Ein Fehlschlag erzeugt eine Systemmeldung (FR-7).

**Manueller Abruf**
1. Der Benutzer öffnet **Tarife** (`/tarife`, Permission `tarife:manage`).
2. Unterhalb der Tarifliste steht der Bereich **Einspeisepreise** mit der Schaltfläche **Herunterladen**.
3. Klick → derselbe Ablauf wie der Job (Schritte 3–4), synchron.
4. Das Diagramm lädt danach die Werte der aktuellen Auswahl neu; eine Erfolgsmeldung nennt die Zahl der neuen und aktualisierten Werte sowie den Publikationszeitpunkt der Quelle.

**Darstellung**
1. Beim Öffnen der Seite zeigt das Diagramm die Auswahl **TAG** = heute.
2. Der Benutzer wechselt auf **WOCHE** / **MONAT** oder setzt **Datum von / Datum bis**.
3. Mit **‹ / ›** blättert er um genau eine Spanne zurück/vor (Tag / Woche / Monat bzw. die Länge des gewählten Zeitraums).
4. Innerhalb der geladenen Spanne kann er mit Maus/Touch **zoomen und verschieben** (ECharts `dataZoom`), ohne dass neu geladen wird.

### FR-2: Persistierung

Neue Tabelle `zev.preiszeitreihe` (Flyway `V129__Create_Preiszeitreihe.sql`):

| Spalte | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `id` | `bigserial` | ja | Technischer Schlüssel |
| `zeit_von` | `timestamp` | ja | Intervallbeginn, **UTC** |
| `zeit_bis` | `timestamp` | ja | Intervallende, **UTC** |
| `preis` | `numeric(10,5)` | ja | Einspeisepreis in CHF/kWh — dieselbe Präzision wie `tarif.preis` (`NUMERIC(10,5)`, V22). **Darf 0 und negativ sein** |
| `publikation` | `timestamp` | **nein** | `publication_timestamp` der Quelle, UTC — Herkunftsnachweis des Werts |
| `aktualisiert_am` | `timestamp` | ja | Zeitpunkt des letzten Schreibens (Default `now()`) |

* **Eindeutigkeit:** `UNIQUE (zeit_von)` (`uq_preiszeitreihe_zeit_von`) — Grundlage des Upsert.
* **Upsert:** `INSERT ... ON CONFLICT (zeit_von) DO UPDATE SET zeit_bis, preis, publikation, aktualisiert_am` — als natives Statement analog `DebitorRepository.upsert`. Der Konfliktschlüssel muss **genau** dem Unique-Constraint entsprechen. Ein erneuter Abruf desselben Tages ist damit idempotent, eine Preiskorrektur der Quelle überschreibt den alten Wert.
* **Kein `org_id`:** Bewusste, begründete Ausnahme von der Multi-Tenancy-Regel aus `Specs/generell.md`. Die BKW-Preise sind für alle Mandanten identisch; eine Kopie je Mandant wäre redundant, und der geplante Job hat keinen Mandantenkontext (siehe `ZaehlerAggregationService`: dort kommt `org_id` aus den verarbeiteten Entities, hier gibt es keine). Die Tabelle trägt **kein** `@Filter` und **keinen** `hibernateFilterService.enableOrgFilter()`-Aufruf; der Zugriff ist über die Permission geschützt (NFR-2). Ein `org_id` wäre nachträglich additiv einführbar (NFR-3).
* **Die ArchUnit-Regel braucht eine namentliche Ausnahme.** `SecurityRules.everyEntityMustHaveOrgId()` (`ArchitectureTest.java`) nimmt heute nur Entities mit `Translation` oder `Organisation` im Namen aus; eine Entity ohne `orgId` lässt den Test sonst **rot** werden. Die Ausnahme ist Teil dieser Umsetzung und gehört **namentlich und begründet** in die Regel — nach dem Muster von `ohneMandantenzugriff` in `nebenkostenServicesMustCheckFeatureFlag`, damit sie sichtbar in der Regel steht und nicht aus einem Namensmuster entsteht. Ein neu hinzukommendes Entity bleibt damit weiterhin automatisch erfasst (Deny by default).
* **Hinweis zur `findById`-Regel:** `servicesMustNotUseFindByIdOnFilteredRepositories` führt die Repositories ungefilterter Entities in einer Whitelist (`ungefiltert`). Lädt der Service je einen Einzelsatz über `findById`, muss `PreiszeitreiheRepository` dort ergänzt werden. Die hier beschriebenen Zugriffe brauchen kein `findById` (Abfrage nach Zeitspanne, Upsert nach `zeit_von`).
* **Zeitzone:** Gespeichert wird **UTC, verbatim aus der Quelle**. Grund: Bei lokaler Zeit (Europe/Zurich) bricht der Unique-Schlüssel an der Zeitumstellung — in der Nacht der Rückstellung tritt die Stunde 02:00–03:00 **zweimal** auf, vier Viertelstundenwerte kollidierten mit vier anderen und würden einander überschreiben; in der Nacht der Vorstellung fehlt sie. Die Umrechnung auf Europe/Zurich passiert erst bei der Darstellung.
* **`publikation` ist optional.** Fehlt der `publication_timestamp` in der Antwort oder ist er unlesbar, bleibt die Spalte **leer** — die Preise werden trotzdem gespeichert. Zwei Gründe: Ein `NOT NULL` hätte den gesamten Abruf scheitern lassen und 96 fehlerfreie Preise verworfen, nur weil ein Metadatum fehlt; und ein ersatzweise eingesetzter Abrufzeitpunkt wäre eine **erfundene** Herkunftsangabe. Wann geschrieben wurde, steht ohnehin in `aktualisiert_am`.
* **0 und negative Preise sind gültig — kein Vorzeichen-Wächter.** Bei Überangebot (viel Sonne, wenig Last) kostet das Einspeisen Geld, statt Ertrag zu bringen; die Quelle liefert dann einen negativen Wert. V129 hatte hier fälschlich `CHECK (preis >= 0)`, **V132 entfernt den Constraint wieder**. Eine Prüfung auf `>= 0` liesse den Abruf genau in jenen Stunden scheitern, die für eine Steuerung am interessantesten sind — und ein abgewiesener Abruf ist eine dauerhafte Lücke (§1). Es gibt deshalb weder im Backend noch in der Datenbank eine Vorzeichenprüfung.
* **Keine Retention:** Die Historie wächst unbegrenzt (35'040 Zeilen/Jahr — für PostgreSQL vernachlässigbar). Preise sind Marktdaten, keine Personendaten; `Specs/Datenaufbewahrung.md` braucht dafür keine Frist. Kein Löschjob.

### FR-3: Layout

**Platzierung:** Am Ende von `tarif-list.component.html`, nach der Tarifliste und **vor** `<app-tarif-form>`, als eigene Komponente `<app-preiszeitreihe-chart>`; sichtbar nur mit aktivem Flag (`*appFeature="'PREISZEITREIHE'"`, FR-6).

**Aufbau (von oben nach unten):**
1. Titel **Einspeisepreise** mit Icon `bar-chart-2`.
2. **Eine einzige Steuerzeile** (`zev-date-range-row`) — alle Bedienelemente stehen **auf gleicher Höhe**, in dieser Reihenfolge von links nach rechts:
   1. **Tag / Woche / Monat** als Toggle-Buttons (`zev-toggle-button`, die aktive Auswahl markiert — dasselbe Muster wie die Quartalsschaltflächen).
   2. **‹** und **›** zum Blättern (`chevron-left` / `chevron-right`).
   3. **Datum von** / **Datum bis** (`input type="date"`, IDs `preisVon` / `preisBis`); eine Eingabe hier hebt die Tag/Woche/Monat-Markierung auf.
   4. **Linie / Balken** als Toggle-Buttons — die Darstellungsart des Diagramms.
   5. Schaltfläche **Herunterladen** (`zev-button--primary`, Icon `download`).

   Die gemeinsame Höhe (38 px für Eingabefelder, Schaltflächen und Toggle-Buttons) und die untere Ausrichtung kommen aus `zev-date-range-row` im Design System — nicht aus komponenteneigenem CSS. Zusammengehörende Toggle-Buttons stehen dichter beieinander als die Gruppen untereinander, damit erkennbar bleibt, was eine Auswahl bildet. Bei schmalen Fenstern bricht die Zeile um.
3. Diagramm in `zev-panel--chart` (dieselbe Klasse wie `messwerte-chart`), Höhe verstellbar, Breite 100 %.
4. Meldungsbereich (`zev-message--success` / `--error`) über der Steuerzeile; Erfolgsmeldungen verschwinden nach 5 Sekunden, Fehlermeldungen bleiben.

**Diagramm:**
* **Bibliothek ECharts** (`echarts`, Apache-2.0), modular über `echarts/core` mit `LineChart`, **`BarChart`**, `GridComponent`, `TooltipComponent`, `DataZoomComponent` und `CanvasRenderer` — kein Voll-Import. **Beide** Diagrammtypen müssen registriert sein: Ein nicht registrierter Typ zeichnet stillschweigend nichts, ECharts meldet ihn nicht als Fehler.
* **Nachgeladen statt mitgeliefert:** Der Import erfolgt **dynamisch** (`await import('echarts/core')` beim Initialisieren der Komponente), nicht als statischer Import am Dateikopf. Grund: `/tarife` ist eine **eager** Route (`app.routes.ts`), ein statischer Import landete also im Initial-Bundle und jede Seite der Anwendung lüde ECharts mit (NFR-1). Solange die Bibliothek lädt, zeigt das Panel einen Ladezustand; scheitert das Nachladen, erscheint eine Fehlermeldung und die Seite bleibt bedienbar.
* **Zwei Darstellungsarten, umschaltbar** (Default **Linie**):
  * **Linie:** **reine** Stufenlinie (`step: 'end'`, **ohne** `areaStyle`) — ein Preis gilt für die **ganze** Viertelstunde; eine interpolierte Linie behauptete einen stetigen Verlauf, den es nicht gibt. Keine Flächenfüllung: Eine gefüllte Fläche liest sich als Summe über die Zeit, und aufsummierte Preise sind sinnlos.
  * **Balken:** je Intervall ein Balken (`type: 'bar'`, `barMaxWidth`) — betont die einzelne Viertelstunde statt des Verlaufs.
  * Das Umschalten zeichnet nur neu und lädt **nicht** nach: Es ändert sich die Sicht, nicht die Daten. Die Wahl gilt für die Sitzung und wird nicht gespeichert.
* **x-Achse:** Zeit in Europe/Zurich; **y-Achse:** CHF/kWh.
* **Zoom:** `dataZoom` mit `inside` (Mausrad/Touch) **und** `slider` (Griff unter der Achse).
* **Tooltip:** Zeitpunkt (`dd.MM.yyyy HH:mm`) und Preis im **Schweizer Zahlenformat** (`Specs/generell.md` §Zahlenformatierung): Punkt als Dezimaltrennzeichen, Hochkomma als Tausendertrennzeichen, 5 Nachkommastellen, Fehlwert `–`. Formatierung über `formatSwissNumber()` aus `src/app/utils/number-utils.ts` — **keine** `toLocaleString()`, kein ECharts-eigenes `valueFormatter` mit Locale.
* **Leere Auswahl:** Statt eines leeren Diagramms der Hinweis `KEINE_PREISE_VORHANDEN`.
* **Dark Mode:** Achsen-, Gitter- und Linienfarben aus den Design-Tokens (`Specs/DarkMode.md`), nicht hart kodiert.

### FR-4: REST-Endpunkte

`PreiszeitreiheController`, `@RequestMapping("/api/preiszeitreihe")`, `@PreAuthorize("hasAuthority('tarife:manage')")` auf Klassenebene:

| Methode | Pfad | Zweck | Antwort |
|---|---|---|---|
| `GET` | `/api/preiszeitreihe?von=&bis=` | Werte einer Spanne (Datum inklusive, lokale Tagesgrenzen) | `List<PreiszeitreihePunktDTO>` mit `zeit` (lokal, ISO) und `preis` |
| `POST` | `/api/preiszeitreihe/download` | Manueller Abruf | `PreiszeitreiheDownloadDTO`: `abgerufen`, `neu`, `aktualisiert`, `publikation` |

* Beide Endpunkte prüfen das Feature-Flag (FR-6) und antworten bei deaktiviertem Flag mit `403`.
* `GET` ohne Treffer liefert `200` und eine leere Liste (kein `404`).
* Ungültige oder vertauschte Datumsangaben → `400` mit lesbarer Meldung (`von` nach `bis`).
* Die Spanne ist auf **maximal 366 Tage** begrenzt; darüber `400`. Ohne Grenze zieht ein getippter Bereich die ganze Historie in eine Antwort.
* **Umrechnung der Spanne (Datum → UTC):** `von` und `bis` sind **Datumsangaben in Europe/Zurich**, beide inklusive. Der Server bildet sie auf `von 00:00` bis `bis 24:00` Ortszeit ab und rechnet **beide Grenzen nach UTC** um, bevor er die Tabelle abfragt. Rückwärts gilt dasselbe: `zeit` im DTO ist die **nach Europe/Zurich umgerechnete** Ortszeit des gespeicherten UTC-Werts. An den Umstellungstagen ergibt das korrekt **92** bzw. **100** Werte pro Tag.
* **Statuscodes des Downloads** (eindeutig, damit Maske und E2E darauf prüfen können):
  * `200` — Abruf gelungen (auch mit übersprungenen Einzelwerten, deren Zahl in der Antwort steht).
  * `403` — Feature-Flag aus.
  * `502` — die **Quelle** hat versagt: nicht erreichbar, Zeitüberschreitung, HTTP ≥ 400, unlesbares Format, fremde Einheit, unplausible Menge. Der Fehler liegt nicht beim Aufrufer.
  * `400` — lokale Validierung (z.B. fehlende oder ungültige Konfiguration der Quell-URL).
  * Rumpf immer **Klartext**, kein Objekt (ein Objekt erscheint in der Maske als `[object Object]`).

### FR-5: Abruf und Umwandlung

* HTTP über Springs **`RestClient`** (in Spring Boot 4 vorhanden, kein neues Framework), Timeouts: Verbindung 5 s, Lesen 10 s.
* Basis-URL aus `application.yml` (`preiszeitreihe.url`), damit sie ohne Neubau umgestellt werden kann.
* **Kein Retry.** Ein Fehlschlag wird gemeldet (FR-7); der nächste Lauf oder die Schaltfläche holt nach — solange dieselbe Quelle dasselbe Fenster liefert, entsteht kein Verlust.
* Umwandlung je Eintrag aus `prices`:
  * `start_timestamp` / `end_timestamp` → `zeit_von` / `zeit_bis` (UTC).
  * Preis aus dem **ersten** `feed_in`-Element. Ist das Array leer, wird der Eintrag **übersprungen** und gezählt (Log auf `warn`) — ein fehlender Preis darf nicht als `0.00000` in der Reihe landen, das wäre eine Falschaussage.
  * **Übersprungen wird nur, was fehlt — nicht, was ungewohnt aussieht.** Ein geliefertes `0` oder ein negativer Wert ist ein gültiger Marktpreis und wird übernommen (FR-2).
  * Ist `unit` **nicht** `CHF_kWh`, wird der **gesamte** Abruf abgewiesen (FR-7). Eine stillschweigende Übernahme in fremder Einheit (z.B. Rp./kWh) verfälschte die Reihe um Faktor 100.
* Einträge mit fehlendem `start_timestamp`, fehlendem `value` oder negativem Intervall (`end` ≤ `start`) werden übersprungen und gezählt.
* **Schreiben in aufsteigender `zeit_von`-Reihenfolge.** Job und Schaltfläche können gleichzeitig dieselben Zeilen schreiben; nehmen beide die Sperren in derselben Reihenfolge, kann kein Deadlock entstehen — der zweite wartet. Eine beliebige Reihenfolge riskierte ein `40P01`, und das kommt als `CannotAcquireLockException` durch, die **kein** Handler auf eine lesbare Antwort abbildet (§5).
* **Obergrenze:** maximal **10'000** Einträge je Abruf (NFR-2). Liefert die Quelle mehr, gilt die Antwort als unplausibel und wird wie ein Formatfehler behandelt (FR-7, `502`) — nichts wird gespeichert.

### FR-6: Feature-Flag

* Neues Flag `PREISZEITREIHE` in `FeatureFlag` mit **Default `false`** und Beschreibungs-Key `FEATURE_FLAG_PREISZEITREIHE` — Muster wie `NEBENKOSTENABRECHNUNG`.
* **Frontend:** Der Bereich auf `/tarife` erscheint nur bei aktivem Flag (`*appFeature`).
* **Backend:** Beide Endpunkte prüfen das Flag serverseitig (`FeatureDisabledException` → `403`). Ohne diese Prüfung wäre das Flag reine Kosmetik — die API bliebe über jeden HTTP-Client erreichbar (dieselbe Begründung wie in `NkAbrechnungService.pruefeFeatureFlag`).
* **Job:** läuft nur, wenn mindestens eine Organisation das Flag aktiv hat. Dafür braucht `FeatureFlagService` eine neue **Methode** „Organisationen mit aktivem Flag". **Keine** neue Repository-Abfrage: Die Overrides hängen an der `Organisation` selbst (der Service arbeitet bereits mit `OrganisationRepository`), die Methode iteriert `findAll()` und fragt je Organisation `isEnabled(orgId, flag)` — damit greift automatisch auch ein später geänderter globaler Default.
* **ArchUnit-Schutz:** Wie bei der Nebenkostenabrechnung wird die Flag-Prüfung durch eine Regel abgesichert (Vorbild `nebenkostenServicesMustCheckFeatureFlag`): Jede öffentliche Methode der `Preiszeitreihe*`-Services im Paket `..service..` ruft `pruefeFeatureFlag()`. Ohne Regel kann eine später ergänzte Methode die Prüfung stillschweigend auslassen — das Menü bliebe verborgen, die API aber offen.

### FR-7: Fehlerbehandlung und Systemmeldung

**Jeder Fehlschlag erzeugt eine Systemmeldung** — der Abruf läuft überwiegend unbeobachtet um 02:00, ein Fehler nur im Log wäre praktisch unsichtbar, und jeder ausgefallene Tag ist eine dauerhafte Lücke (§1).

| Auslöser | Level | Meldungs-Key | Parameter |
|---|---|---|---|
| Quelle nicht erreichbar, Zeitüberschreitung, HTTP ≥ 400 | `WARN` | `PREISZEITREIHE_ABRUF_FEHLER` | Kurzgrund samt HTTP-Status |
| Antwort unlesbar / `prices` fehlt | `WARN` | `PREISZEITREIHE_ABRUF_FEHLER` | „unerwartetes Format" |
| Fremde Einheit (`unit != CHF_kWh`) | `WARN` | `PREISZEITREIHE_ABRUF_FEHLER` | gelieferte Einheit |
| Einzelne Einträge übersprungen (leeres `feed_in`, fehlende Felder, `end` ≤ `start`) | `WARN` | `PREISZEITREIHE_WERTE_UEBERSPRUNGEN` | Anzahl und Zeitraum der übersprungenen Intervalle |

* **Kategorie:** `SYSTEMMELDUNG_KATEGORIE_PREISZEITREIHE` (neue Konstante in `SystemmeldungService`), damit der Filter auf `/systemmeldungen` sie trennt.
* **Je Organisation mit aktivem Flag:** `SystemmeldungService.erfasse(orgId, …)` ist mandantenbezogen, und `/systemmeldungen` zeigt nur den eigenen Mandanten — eine Meldung ohne `org_id` sähe niemand. Der Job schreibt sie deshalb für jede Organisation, die das Flag aktiv hat (dieselbe Menge, die den Job überhaupt auslöst, FR-6).
* **Keine Meldungsflut:** `erfasse` fasst gleiche `meldungKey` im selben Mandanten zusammen — ein offener Eintrag wird hochgezählt (`zaehler`, `zuletzt_aufgetreten`) statt vervielfacht. Zehn Tage Ausfall ergeben **einen** Eintrag mit Zähler 10.
* **Selbstheilung:** Ein erfolgreicher Abruf ruft `SystemmeldungService.autoResolve(orgId, "PREISZEITREIHE_ABRUF_FEHLER")` — die offene Meldung gilt als erledigt, sobald es wieder läuft. Für `PREISZEITREIHE_WERTE_UEBERSPRUNGEN` gilt dasselbe, sobald ein Abruf ohne übersprungene Einträge durchläuft. Beide Methoden existieren bereits; es braucht keinen neuen Service-Code.
* **Übersprungene Einträge sind kein Abbruch:** Der Abruf gilt als erfolgreich, die gelieferten Werte werden gespeichert, die Zahl steht in der Antwort — die Meldung dokumentiert die Lücke. Vorbild ist `BILANZMODELL_INTERVALLE_UEBERSPRUNGEN`, das genau so verfährt.
* **Manueller Abruf:** erzeugt dieselbe Systemmeldung **und** gibt eine lesbare Fehlermeldung an die Maske zurück — mit dem in FR-4 festgelegten Status (`502` bei einem Fehler der Quelle, `400` bei fehlender Konfiguration) und immer als Klartext-Rumpf.
* **Parameter auf 500 Zeichen kürzen.** `systemmeldung.parameter` ist `VARCHAR(500)` (V86). Ein langer Fehlertext der Quelle — HTML-Fehlerseite, Stacktrace, Proxy-Meldung — sprengte die Spalte, und das Melden des Fehlers scheiterte am Fehler selbst. Der Kurzgrund wird deshalb vor dem Schreiben abgeschnitten.
* Neue Übersetzungs-Keys für beide Meldungen samt Kategorie in der Migration `V130` (DE/EN).

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

**Abruf und Speicherung**
* [x] Ein manueller Abruf über **Herunterladen** speichert die 96 Viertelstundenwerte des gelieferten Fensters und meldet Anzahl neu/aktualisiert samt Publikationszeitpunkt.
* [x] Ein **zweiter** Abruf derselben Daten erzeugt **keine** zusätzlichen Zeilen (Upsert über `zeit_von`); ein geänderter Preis derselben Viertelstunde **überschreibt** den alten Wert.
* [x] Die gespeicherten Zeitstempel sind **UTC**, verbatim aus der Quelle (`start_timestamp` → `zeit_von`).
* [x] Der Preis wird mit 5 Nachkommastellen gespeichert (`0.138` → `0.13800`).
* [x] Ein **negativer** Preis (z.B. `-0.025`) wird gespeichert und im Diagramm angezeigt — nicht übersprungen und nicht abgewiesen.
* [x] Ein Preis von `0` wird gespeichert (und ist von „kein Preis geliefert" unterschieden: dieser Eintrag wird übersprungen).
* [x] `publikation` trägt den `publication_timestamp` der Antwort.
* [x] Fehlt `publication_timestamp` oder ist er unlesbar, werden die Preise **trotzdem** gespeichert und `publikation` bleibt leer.
* [x] Liefert die Quelle mehr als 10'000 Einträge, wird **nichts** gespeichert; der Abruf antwortet mit `502` und erzeugt eine Systemmeldung.
* [x] Liefert die Quelle eine andere `unit` als `CHF_kWh`, wird **kein** Wert gespeichert und der Abruf als Fehler gemeldet.
* [x] Ein Eintrag mit leerem `feed_in` wird übersprungen, nicht als `0` gespeichert; die Antwort nennt die Zahl der übersprungenen Einträge.
* [x] Der geplante Job läuft täglich um 02:00 (Default-Cron `0 0 2 * * *`) und ist über `application.yml` umstellbar.
* [x] Hat **keine** Organisation das Flag aktiv, führt der Job **keinen** HTTP-Aufruf aus.
* [x] Ein fehlgeschlagener Abruf erzeugt je Organisation mit aktivem Flag eine `WARN`-Systemmeldung mit Kategorie `SYSTEMMELDUNG_KATEGORIE_PREISZEITREIHE` und Key `PREISZEITREIHE_ABRUF_FEHLER`.
* [x] Ein **zweiter** Fehlschlag erzeugt **keinen** zweiten Eintrag, sondern erhöht `zaehler` und `zuletzt_aufgetreten` des offenen Eintrags.
* [x] Ein erfolgreicher Abruf setzt die offene Fehlermeldung automatisch auf erledigt (`autoResolve`).
* [x] Werden einzelne Einträge übersprungen, entsteht eine `WARN`-Systemmeldung `PREISZEITREIHE_WERTE_UEBERSPRUNGEN` mit Anzahl und Zeitraum; die übrigen Werte sind gespeichert.
* [x] Ein Abruf ohne übersprungene Einträge setzt eine offene `PREISZEITREIHE_WERTE_UEBERSPRUNGEN`-Meldung auf erledigt.
* [x] Auch der **manuelle** Abruf erzeugt bei einem Fehler die Systemmeldung (nicht nur die Meldung in der Maske).

**Darstellung**
* [x] Der Bereich **Einspeisepreise** erscheint auf `/tarife` unterhalb der Tarifliste.
* [x] Beim Öffnen zeigt das Diagramm den **heutigen Tag**.
* [x] **Tag / Woche / Monat** setzen die Spanne; die aktive Auswahl ist visuell markiert.
* [x] Bereichsauswahl, Pfeile, Datumsfelder, Darstellungsumschaltung und **Herunterladen** stehen in **einer** Zeile und haben dieselbe Höhe (38 px).
* [x] Die Umschaltung **Linie / Balken** steht zwischen **Datum bis** und **Herunterladen**.
* [x] Beim Öffnen ist **Linie** aktiv; ein Klick auf **Balken** zeigt Balken je Viertelstunde, ein Klick auf **Linie** wieder die Stufenlinie.
* [x] Die Linie ist eine **reine** Linie ohne Flächenfüllung.
* [x] Beide Darstellungen zeichnen tatsächlich etwas — keine der beiden bleibt leer.
* [x] Das Umschalten der Darstellung löst **keinen** HTTP-Aufruf aus.
* [x] **Datum von / Datum bis** setzen eine freie Spanne und heben die TAG/WOCHE/MONAT-Markierung auf.
* [x] **‹ / ›** verschieben die Spanne um genau ihre Länge; die Werte werden neu geladen.
* [x] Mausrad/Touch zoomt innerhalb der geladenen Spanne, der Slider verschiebt den Ausschnitt — **ohne** neuen HTTP-Aufruf.
* [x] Die Linie ist eine **Stufenlinie** (ein Preis gilt für die ganze Viertelstunde).
* [x] Tooltip und Achsenbeschriftung zeigen Zahlen im Schweizer Format (Punkt als Dezimal-, Hochkomma als Tausendertrennzeichen), `–` bei Fehlwerten.
* [x] Ohne Werte in der Spanne erscheint der Hinweis `KEINE_PREISE_VORHANDEN` statt eines leeren Diagramms.
* [x] Das Diagramm ist im Dark Mode lesbar (Farben aus Design-Tokens).
* [x] Alle Texte des Bereichs kommen aus dem `TranslationService` (kein hartkodierter Text), DE und EN vorhanden.
* [x] ECharts liegt **nicht** im Initial-Bundle: Nach dem Produktionsbau wächst `main-*.js` um weniger als 20 kB, die Bibliothek steht in einem eigenen Chunk.
* [x] Scheitert das Nachladen der Bibliothek, erscheint eine Fehlermeldung und die Seite bleibt bedienbar.
* [ ] `GET` über einen Monat (2'976 Punkte) antwortet in unter 500 ms (im Integrationstest gemessen).

**Sicherheit und Flag**
* [x] Ohne Permission `tarife:manage` liefern `GET /api/preiszeitreihe` und `POST /api/preiszeitreihe/download` **403**.
* [x] Bei deaktiviertem Flag `PREISZEITREIHE` liefern beide Endpunkte **403**, auch mit `tarife:manage`.
* [x] Bei deaktiviertem Flag ist der Bereich auf `/tarife` **nicht** sichtbar.
* [x] Das Flag ist unter `/einstellungen` je Mandant schaltbar (nur `zev_admin`, Permission `featureflags:manage`).

**Fehlerfälle**
* [x] Ist die Quelle nicht erreichbar, bleibt die Maske bedienbar und zeigt eine lesbare Fehlermeldung (kein `[object Object]`, keine leere Meldung).
* [x] `GET` mit `von` nach `bis` liefert **400** mit lesbarer Meldung.
* [x] `GET` mit einer Spanne über 366 Tagen liefert **400**.
* [x] `GET` auf eine Spanne ohne Daten liefert **200** und eine leere Liste.
* [x] `GET` liefert die Zeitpunkte in Ortszeit (Europe/Zurich), korrekt aus UTC umgerechnet; ein Tag mit Zeitumstellung liefert **92** bzw. **100** Werte.
* [x] Ein Fehler der **Quelle** ergibt `502`, eine fehlende/ungültige Konfiguration `400` — beide mit Klartext-Rumpf.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Ein Monat sind bis zu **2'976** Punkte (31 × 96). `GET` antwortet dafür in **< 500 ms**, das Diagramm rendert in **< 1 s**. Keine serverseitige Verdichtung — die Rohauflösung ist der Zweck der Reihe.
* Die Abfrage läuft über einen Index auf `zeit_von` (der Unique-Constraint genügt).
* Der Abruf der Quelle beansprucht das Backend nicht messbar (eine Anfrage, 96 Einträge, einmal täglich).
* **ECharts gehört nicht ins Initial-Bundle.** Das Haupt-Bundle liegt heute bei ~**950 kB** gegen die Budgets in `angular.json` (`maximumWarning: 1mb`, `maximumError: 2mb`); ECharts statisch importiert überschreitet die Warnschwelle und verlangsamt **jede** Seite, auch ohne Diagramm. Deshalb wird die Bibliothek **dynamisch nachgeladen** (FR-3) und liegt in einem eigenen Chunk. Prüfkriterium nach dem Build: `main-*.js` wächst um **< 20 kB**, ECharts erscheint in einem separaten Chunk.

### NFR-2: Sicherheit
* **Permission `tarife:manage`** (Fachrollen `org_admin`, `zev_admin`) für Anzeige **und** Download — dieselbe Permission, die die Seite `/tarife` schützt. `zev_user` hat keinen Zugriff.
* Das Feature-Flag wird **serverseitig** geprüft, nicht nur im Menü/Template.
* Die Quell-URL steht in der Konfiguration, nicht im Code; sie enthält **keine** Zugangsdaten (öffentliche API). Sollte die Quelle je einen Schlüssel verlangen, gehört er in `.env` und **nicht** ins Repository (`Specs/generell.md`).
* Ausgehender Aufruf nur nach **HTTPS** und nur auf die konfigurierte URL; keine Weiterleitung auf einen anderen Host folgen.
* Die Antwort der Quelle wird als **Fremddaten** behandelt: Feldprüfung, Einheitsprüfung, Begrenzung der verarbeiteten Einträge (max. 10'000 pro Abruf), keine Übernahme unbekannter Felder.

### NFR-3: Kompatibilität
* Rein additiv: neue Tabelle, neue Endpunkte, neues Flag, neue Komponente. Kein bestehender Datensatz und kein bestehender Endpunkt ändert sich.
* Bestehende Abrechnung und Tarifverwaltung bleiben unberührt — die Reihe ist **Datengrundlage**, noch keine Berechnungsquelle (§7).
* Die Tabelle ist jederzeit löschbar (`DROP TABLE`), ohne dass andere Daten fehlen; es gibt keine Fremdschlüssel auf sie.
* Ein späteres `org_id` ist additiv nachrüstbar (Spalte + Backfill auf die bestehende Organisation + erweiterter Unique-Schlüssel).
* chart.js bleibt für `messwerte-chart` im Einsatz; beide Bibliotheken laufen parallel (§7, §8).

## 5. Edge Cases & Fehlerbehandlung

| Fall | Erwartetes Verhalten |
|---|---|
| Quelle nicht erreichbar / Zeitüberschreibung | Kein Schreiben, Systemmeldung `WARN`, lesbare Fehlermeldung beim manuellen Abruf, Maske bleibt bedienbar |
| HTTP 4xx/5xx der Quelle | wie oben, Status im Meldungsparameter |
| Antwort ist kein gültiges JSON / `prices` fehlt | wie oben, Meldung nennt „unerwartetes Format" |
| `prices` ist leer | Abruf gilt als erfolgreich mit 0 Werten; Hinweis „keine Preise geliefert" in der Maske, Systemmeldung `PREISZEITREIHE_WERTE_UEBERSPRUNGEN` mit Anzahl 0 entfällt |
| Negativer Preis oder Preis `0` | Wird gespeichert und dargestellt — gültiger Marktwert, keine Prüfung auf das Vorzeichen (FR-2) |
| Einzelner Eintrag ohne `feed_in`/`value` | Eintrag übersprungen und gezählt, Rest wird gespeichert, `WARN`-Systemmeldung `PREISZEITREIHE_WERTE_UEBERSPRUNGEN` |
| Fremde Einheit (`unit != CHF_kWh`) | **Kompletter** Abruf abgewiesen, nichts gespeichert |
| Doppelte `start_timestamp` in einer Antwort | Letzter Eintrag gewinnt (Upsert), kein Abbruch |
| Zwei Abrufe gleichzeitig (Job und Schaltfläche) | Beide laufen durch; der Upsert ist idempotent, der spätere Schreibvorgang gewinnt. Weil beide in aufsteigender `zeit_von`-Reihenfolge schreiben (FR-5), entsteht kein Deadlock — der zweite wartet auf die Sperre |
| `publication_timestamp` fehlt oder ist unlesbar | Preise werden gespeichert, `publikation` bleibt leer (FR-2); kein Abbruch, keine erfundene Herkunftsangabe |
| Quelle liefert unplausibel viele Einträge (> 10'000) | Nichts wird gespeichert, `502`, Systemmeldung wie bei einem Formatfehler |
| Fehlertext der Quelle länger als 500 Zeichen | Kurzgrund wird gekürzt gespeichert; das Melden scheitert nicht am eigenen Fehler (FR-7) |
| Zeitumstellung (März/Oktober) | Unkritisch, weil UTC gespeichert wird. Die Darstellung zeigt an diesen Tagen 92 bzw. 100 Werte — das ist korrekt und kein Fehler |
| Leere Spanne / Zukunft gewählt | `200` + leere Liste, Hinweis `KEINE_PREISE_VORHANDEN` |
| Lücke in der Historie (Ausfall über Tage) | Das Diagramm zeigt die Lücke als Unterbruch der Linie (`connectNulls: false`), nicht als Verbindung über die Lücke hinweg |
| Feature-Flag mitten in der Sitzung deaktiviert | Nächster Aufruf `403`; die Maske zeigt die Fehlermeldung, der Bereich verschwindet nach dem Neuladen |
| Benutzer ohne `tarife:manage` ruft die API direkt | `403`, keine Daten |
| Sehr grosse Spanne (366 Tage, ~35'000 Punkte) | `400` bei > 366 Tagen; innerhalb der Grenze wird geliefert, das Rendern übernimmt `dataZoom` |

## 6. Abhängigkeiten & betroffene Funktionalität

**Voraussetzungen**
* `FeatureFlag` / `FeatureFlagService` / `*appFeature` (`Specs/FeatureFlag.md`) — vorhanden, wird um ein Flag und eine Abfrage erweitert.
* `SystemmeldungService` (`Specs/Systemmeldungen.md`) — vorhanden: `erfasse` (mit Zusammenfassung gleicher Keys) und `autoResolve` (Selbstheilung) werden unverändert genutzt; neu sind eine Kategorie und zwei Meldungs-Keys.
* `TranslationService` und Übersetzungen (`Specs/Übersetzungsverwaltung.md`) — neue Keys per Migration mit `ON CONFLICT (key) DO NOTHING`.
* Design System (`zev-panel`, `zev-panel--chart`, `zev-button`, `zev-date-range-row`, `zev-message`) — vorhanden; neue wiederverwendbare Styles gehören ins Design System, nicht in die Komponente.
* `@EnableScheduling` — bereits aktiv (`BackendServiceApplication`).
* Container-Zeitzone `TZ=Europe/Zurich` für die Darstellung serverseitig gebildeter lokaler Zeiten.

**Neue Abhängigkeit**
* npm-Paket **`echarts`** (Apache-2.0). Erscheint in SBOM und auf der Seite **Lizenzen** (`Specs/SBOM.md`) — dort nach dem Einbau prüfen.

**Betroffener Code**
| Datei | Änderung |
|---|---|
| `db/migration/V129__Create_Preiszeitreihe.sql` | neu: Tabelle, Unique-Constraint, Spaltenkommentare |
| `db/migration/V130__Add_Preiszeitreihe_Translations.sql` | neu: Übersetzungen DE/EN |
| `db/migration/V131__Add_Preiszeitreihe_Darstellung_Translations.sql` | neu: `DARSTELLUNG_LINIE`, `DARSTELLUNG_BALKEN` — eigene Migration, weil V130 bereits ausgeführt war |
| `db/migration/V132__Preiszeitreihe_Negative_Preise.sql` | neu: entfernt `ck_preiszeitreihe_preis` aus V129 — negative Preise sind zulässig (FR-2) |
| `entity/Preiszeitreihe.java`, `repository/PreiszeitreiheRepository.java` | neu (Vorlage `Tarif`/`TarifRepository`, Upsert nach `DebitorRepository`) |
| `service/PreiszeitreiheService.java`, `service/PreiszeitreiheDownloadJob.java` | neu. Der Job trägt **`@Component`**, nicht `@Service` — sonst bricht `NamingConventionTests.servicesShouldEndWithService()`. Vorbild `SystemmeldungCleanupJob` |
| `controller/PreiszeitreiheController.java`, `dto/Preiszeitreihe*DTO.java` | neu (Vorlage `TarifController`) |
| `entity/FeatureFlag.java` | Flag `PREISZEITREIHE` |
| `service/FeatureFlagService.java` (+ Repository) | Abfrage „Organisationen mit aktivem Flag" |
| `service/SystemmeldungService.java` | Konstante `KATEGORIE_PREISZEITREIHE` (nur die Konstante — `erfasse` und `autoResolve` genügen unverändert) |
| `application.yml` | `preiszeitreihe.url`, `preiszeitreihe.download.cron` |
| `components/preiszeitreihe-chart/` | neu: Komponente, Template, CSS, Spec |
| `components/tarif-list/tarif-list.component.html` | Einbindung am Seitenende, hinter `*appFeature` |
| `services/preiszeitreihe.service.ts`, `models/preiszeitreihe.model.ts` | neu (Vorlage `tarif.service.ts` / `tarif.model.ts`) |
| `frontend-service/package.json` | `echarts` |
| `test/.../architecture/ArchitectureTest.java` | zwei Regeln: namentliche Ausnahme in `everyEntityMustHaveOrgId` (FR-2) und neue Flag-Regel für `Preiszeitreihe*`-Services (FR-6) |
| `Specs/Berechtigungen.md` | neue Controller-Zeile (`PreiszeitreiheController` → `tarife:manage`) |

**Tests**
* Backend: `PreiszeitreiheServiceTest` (Umwandlung, Einheitsprüfung, übersprungene Einträge, Fehlerpfade), `PreiszeitreiheControllerTest` (`@WebMvcTest`: Status, Validierung, Flag), `PreiszeitreiheRepositoryIT` (Upsert-Idempotenz mit Testcontainers), `ControllerAuthorizationTest` um die neuen Endpunkte.
* Frontend: Service-Spec (HTTP), Komponenten-Spec (Spannenlogik TAG/WOCHE/MONAT, Blättern, leere Liste, Fehlermeldung). Das ECharts-Objekt wird gemockt — kein echtes Rendern in jsdom.
* E2E: Sichtbarkeit hinter dem Flag, Spannenwechsel, Blättern, Download-Schaltfläche. **Nur Chromium** und `serial`: Die Suite schaltet ein mandantenweites Flag: Zwei Browser-Projekte schalten es einander mitten im Lauf ab (Vorbild `feature-flag-upload.spec.ts`).

**Datenmigration**
* Keine. Die Tabelle startet leer; die Historie entsteht ab dem ersten Abruf.

## 7. Abgrenzung / Out of Scope

* **Keine Verwendung in der Abrechnung.** Rechnungen, Nebenkosten und Debitoren rechnen weiter mit den festen Tarifen. Die Reihe ist Vorbereitung, kein Preisersatz.
* **Kein Bezugspreis.** Gespeichert wird nur `feed_in`. Liefert die Quelle später Bezugspreise, braucht es eine Spalte oder eine Preisart-Dimension (§8).
* **Keine anderen Anbieter.** Nur die BKW-API; kein Mapping mehrerer Netzbetreiber, keine Auswahl der Quelle je Mandant.
* **Keine Rückholung von Vergangenheit.** Die Quelle liefert nur das laufende Fenster; Lücken vor der Inbetriebnahme bleiben.
* **Keine Migration von chart.js zu ECharts.** `messwerte-chart` bleibt auf chart.js; beide Bibliotheken sind parallel im Bundle (§8).
* **Keine Prognose, keine Kennzahlen** (Mittelwert, Min/Max, Preis-Ranking) im Diagramm — nur die Reihe.
* **Kein Export** (CSV/PDF) der Reihe.
* **Keine Benachrichtigung** bei Preisschwellen.
* **Kein neuer Menüeintrag und keine neue Route.** Das Diagramm lebt auf `/tarife`; `app.component.html` und `app.routes.ts` bleiben unverändert.
* **Keine Retention/Verdichtung** (bewusst, FR-2).

## 8. Offene Fragen

* **Zwei Diagramm-Bibliotheken:** chart.js (bestehend) und ECharts (neu) liegen danach parallel im Bundle. Soll `messwerte-chart` später auf ECharts wandern (eine Bibliothek weniger, aber Aufwand und Regressionsrisiko) — oder bleibt es dauerhaft bei zwei? → *Annahme für diese Umsetzung: es bleibt bei zwei, eine Migration ist ein eigenes Vorhaben.*
* **Anbieter-Bindung:** Die Tabelle hat keine Spalte „Quelle/Netzbetreiber". Käme ein zweiter Anbieter, wäre sie Teil des Eindeutigkeitsschlüssels. → *Annahme: ein Anbieter, additiv nachrüstbar.*
* **Historie der Quelle:** Ob die BKW-API Parameter für einen Zeitraum kennt (und damit Lücken nachladbar wären), ist nicht dokumentiert und wurde nicht geprüft. Falls ja, wäre ein „Nachladen von–bis" eine sinnvolle Ergänzung. → *Annahme: nein, nur das laufende Fenster.*
* **Zeitpunkt 02:00:** Die Quelle publiziert laut `publication_timestamp` im Tagesverlauf (Beispiel 13:50Z) und liefert ein Fenster bis 22:00Z. Ob 02:00 lokal den vollständigen Vortag **und** den laufenden Tag erfasst, zeigt erst der Betrieb; eventuell braucht es einen zweiten Lauf am Abend. → *Annahme: ein Lauf um 02:00, Cron konfigurierbar; ein zweiter Lauf ist eine Zeile Konfiguration.*
* **Verhalten bei Preiskorrekturen:** Der Upsert überschreibt ohne Spur. Soll eine Korrektur nachvollziehbar bleiben (Historisierung je `publikation`)? → *Annahme: nein, der letzte Stand genügt; `publikation` und `aktualisiert_am` zeigen die Herkunft.*
* **Rechtliche Nutzungsbedingungen** der BKW-API (Nutzungsrechte, Rate Limits, Verfügbarkeitszusagen) sind nicht geprüft. Vor produktivem Dauerbetrieb klären.
