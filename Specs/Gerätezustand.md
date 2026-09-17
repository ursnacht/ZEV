# Gerätezustand

> **Zustand, nicht Verbrauch.** Die Werte dieses Features werden **nie aggregiert und nie
> verrechnet**. Sie beschreiben, in welchem Zustand ein Gerät zu einem Zeitpunkt war — ein
> Ladestand von 87 % ist keine Energiemenge, und die Differenz zweier Ladestände ist keine
> Kilowattstunde. Wer sie durch die Delta-Aggregation schickt, erhält „ΔSOC": eine Grösse, die bei
> jedem Ladezyklus das Vorzeichen wechselt und niemandem nützt.

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Eine Zeitreihe für **Momentanwerte** von Geräten — als erste Grösse
  der **Ladezustand (SOC)** des Batteriespeichers. Die Struktur ist so angelegt, dass weitere
  Grössen (SOH, Temperatur, …) **ohne neue Tabelle und ohne neue Spalte** dazukommen.

* **Warum machen wir das:** Die Einspeisesteuerung (`Specs/Einspeisesteuerung.md`) entscheidet
  viertelstündlich, ob die Batterie laden darf — **ohne zu wissen, ob überhaupt noch Platz ist**.
  Die Regel `WARTEN_AUF_TAL` hält Kapazität für das Preistal frei; ist der Speicher um 11:00 bereits
  voll, war das Sperren umsonst. Ist er um 16:00 noch leer und der Tag vorbei, hätte längst geladen
  werden müssen. Diese Lücke ist seit der ersten Umsetzung bekannt und dokumentiert (§8 dort).

  Heute lässt sie sich nur indirekt schliessen: Steigt der Ladezähler trotz Sonne nicht mehr, ist
  die Batterie voll. Das ist ein Schluss über zwei Intervalle statt einer Messung.

* **Aktueller Stand:**
  - **Der Wechselrichter liefert den Wert bereits.** Register `33000` des Solinteg MHT
    (`SOC`, `U16`, `%`, Faktor 100), gelesen über dasselbe Modbus-TCP, über das seit dem 16.09.2026
    Ladung und Entladung kommen.
  - **Es gibt keine Ablage dafür.** `zev.messwerte` führt kumulative Energie je 15-Minuten-Intervall
    (`total = ΔBezug − ΔEinspeisung`), `zev.zaehler_rohdaten` absolute Zählerstände. Beide sind auf
    **Deltas** ausgelegt; ein Momentanwert passt in keine von beiden.
  - **Der MQTT-Vertrag kennt zwei Register.** `ZaehlerMesswertPayloadDTO` trägt `timestamp`,
    `zaehlerstandBezug`, `zaehlerstandEinspeisung` und optional `seriennummer`
    (`Specs/MQTT-Integration.md`, FR-3).
  - **Der Einheiten-Typ `SPEICHER` existiert** seit V152 (`Specs/Batteriespeicher.md`), mit Ladung
    und Entladung auf den beiden Registern.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Transport über den bestehenden MQTT-Vertrag

1. Der Payload auf `zev/{orgId}/{messpunkt}/messwert` wird um ein **optionales Objekt** `zustand`
   erweitert — Grössenname auf Zahlenwert:

   ```json
   {
     "timestamp": "2026-09-17T14:30:00+02:00",
     "zaehlerstandBezug": 1447.5,
     "zaehlerstandEinspeisung": 1336.4,
     "zustand": { "soc": 87.5 },
     "seriennummer": "A112500106232016"
   }
   ```

2. **Generisch und tolerant:** Im DTO ist das eine `Map<String, Object>`, kein Feld je Grösse —
   und bewusst **nicht** `Map<String, BigDecimal>`.

   > **Warum nicht der engere Typ:** Jackson scheitert dann schon beim Deserialisieren an
   > `{"soc": "abc"}`, an `"zustand": 5` oder an einem verschachtelten Objekt. Der Fehler landet im
   > generischen `catch` von `MqttIngestService.handle` — und verwirft die **ganze** Nachricht samt
   > Zählerständen. Das widerspricht der Zusage, dass Zustandswerte Beiwerk sind (FR-1.5, FR-4.4):
   > Ein Tippfehler in der Pi-Konfiguration kostete dann die Energiemengen.
   >
   > Die Umwandlung in eine Zahl passiert deshalb **je Eintrag** im Ingest, nicht beim Parsen.

   > **Warum das zählt:** Die Ablage ist schmal, damit eine neue Grösse **keine Migration** braucht
   > (FR-2). Ein typisiertes Feld `soc` hätte dieses Ziel auf halbem Weg verfehlt: Für `SOH` wären
   > DTO, Ingest **und ein Rollout auf jeden Pi im Feld** nötig gewesen. Mit der Map genügt ein
   > Eintrag in der Registry (FR-3) — und der Pi darf eine Grösse senden, die das Backend noch nicht
   > kennt, ohne dass etwas bricht.
   >
   > Der Wire-Contract ist die Stelle, die sich am teuersten korrigieren lässt: Er läuft über
   > Geräte, die nicht im selben Takt aktualisiert werden wie die Anwendung.

3. **Schlüssel sind kleingeschrieben** (`soc`, `soh`, `temperatur`) und werden beim Ingest
   unabhängig von Gross-/Kleinschreibung auf die Registry abgebildet. In der Tabelle steht der
   Enum-Name (`SOC`).

4. **Kein eigenes Topic.** Die Werte stammen vom selben Gerät, aus demselben Lesezyklus und tragen
   denselben Zeitstempel wie die Zählerstände. Ein zweites Topic würde genau das entkoppeln, und
   die Zuordnung „welcher SOC gehört zu welchem Zählerstand?" müsste über Zeitfenster erfolgen
   statt über denselben Datensatz.

5. **Abwärtskompatibel in beide Richtungen.** Fehlt das Objekt, verhält sich alles wie bisher; ein
   Backend, das es noch nicht kennt, ignoriert es (Jackson, `FAIL_ON_UNKNOWN_PROPERTIES=false` als
   Spring-Vorgabe, im Ingest nicht überschrieben). Pi und Backend können unabhängig aktualisiert
   werden.

6. Die Verarbeitung der Zählerstände bleibt **unverändert**: Rohdaten, Aggregation, Messwerte,
   Verteilung. Die Zustandswerte laufen daneben her.

### FR-2: Persistierung — eine schmale Tabelle

`zev.geraetezustand`, eine Zeile **je Einheit, Zeitpunkt und Grösse**:

| Spalte | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `id` | `bigint` | ja | PK aus Sequenz |
| `org_id` | `bigint` | ja | Mandant; serverseitig aus dem Topic, nie aus dem Payload |
| `einheit_id` | `bigint` | ja | FK auf `zev.einheit(id)`, **`ON DELETE CASCADE`** |
| `zeit` | `timestamp` | ja | Messzeitpunkt in **Ortszeit** (Europe/Zurich) |
| `groesse` | `varchar(20)` | ja | `SOC`, später `SOH`, `TEMPERATUR`, … |
| `wert` | `numeric(12,3)` | ja | Zahlenwert; die Einheit folgt aus `groesse`. Bewusst grosszügig — derselbe Typ trägt Prozent (0–100), Temperatur (−40–100) und später Leistungen im Kilowattbereich |
| `empfangen_am` | `timestamp` | nein | Eingang im Backend, Vorgabe `now()` |

* **`UNIQUE (einheit_id, zeit, groesse)`** — eine wiederholte MQTT-Nachricht **aktualisiert** statt
  zu duplizieren, wie bei `zaehler_rohdaten`.
* **Kein `verarbeitet`-Feld.** Es gibt keinen Verarbeitungsschritt: Der Wert wird gelesen, wo er
  gebraucht wird. Ein Aggregations-Job existiert für diese Tabelle **nicht** und soll auch nicht
  entstehen.
* **`ON DELETE CASCADE`** auf dem FK — anders als bei `zaehler_rohdaten`, das den Default
  (`NO ACTION`) nutzt.

  > **Warum hier anders:** Zählerstände sind Belege einer Abrechnung und dürfen nicht mit der
  > Einheit verschwinden. Ein Ladestand ist kein Beleg; ohne seine Einheit ist er bedeutungslos —
  > `CASCADE` ist dafür die passende Semantik.
  >
  > **Was es nicht leistet:** Das Löschen einer Einheit wird dadurch nicht möglich, wo es heute
  > scheitert. `zaehler_rohdaten` (V72) und `messwerte` (V7) referenzieren `zev.einheit` ohne
  > `ON DELETE`, und eine Einheit mit Zustandswerten hat aus derselben Nachricht immer auch
  > Rohdaten — das Löschen scheitert dann dort. `CASCADE` sorgt nur dafür, dass diese Tabelle
  > **kein weiteres** Hindernis aufbaut.
* **Der FK als `Long`**, nicht als `@ManyToOne` — wie `ZaehlerRohdaten.einheitId`. Im Ingest wird
  die Einheit ohnehin schon aufgelöst; eine Relation brächte nur Lazy-Loading auf dem heissesten
  Pfad des Systems.
* **Rundung auf drei Nachkommastellen ist in Kauf genommen.** Meldet ein Gerät mehr Stellen, rundet
  PostgreSQL still. Bei Prozent und Grad ist die dritte Stelle ohne Aussage; eine Prüfung im Ingest
  wäre Aufwand für eine Genauigkeit, die kein Sensor liefert.
* **`empfangen_am` setzt die Anwendung** (`LocalDateTime.now()`), wie bei `ZaehlerRohdaten` — der
  `DEFAULT now()` ist nur das Netz für einen Weg an ihr vorbei. Sonst hinge der Wert an der Zeitzone
  der Datenbank-Session, und das System hätte seine vierte Zeitkonvention.

**Warum schmal und nicht breit:** Eine Tabelle mit je einer Spalte pro Grösse hätte Zeilen, in denen
nur ein Wert gefüllt ist und alle übrigen leer bleiben — nicht jedes Gerät liefert jede Grösse. Eine
Ladestation hat keinen Ladezustand, ein Zähler keine Zelltemperatur. Der Preis dafür sind die
Prüfungen, die eine typisierte Spalte mitbrächte; FR-3 holt sie zurück.

**Die Einheit (% / °C) steht bewusst nicht in der Tabelle.** Sie folgt aus der Grösse und gehört an
**eine** Stelle — in die Registry (FR-3). Eine Spalte daneben könnte ihr widersprechen.

**Zeitstempel in Ortszeit**, wie `zaehler_rohdaten.zeit`, `messwerte.zeit` und
`steuerentscheid.zeit_von` (seit V147). Eine vierte Zeitkonvention im selben System wäre der
nächste stille Fehler; die Einspeisesteuerung hat drei davon teuer bezahlt.

> Der Spaltenkommentar von `zaehler_rohdaten.zeit` sagt es seit V74 ebenso: „Messzeitpunkt als
> lokale Wanduhrzeit". V72 hatte dort ursprünglich „UTC" stehen.

### FR-3: Registry der Grössen

Die erlaubten Grössen stehen als **Java-Enum** (`Zustandsgroesse`) mit je:
* **Einheit** (`%`, `°C`) — für Anzeige und Protokoll,
* **Wertebereich** (Minimum, Maximum) — für die Validierung,
* **zulässige Einheiten-Typen** — `SOC` und `SOH` nur bei `SPEICHER`.

| Grösse | Einheit | Bereich | gültig bei | Quelle (Solinteg MHT) | `skalierung` |
|---|---|---|---|---|---|
| `SOC` | % | 0–100 | `SPEICHER` | Register `33000`, `U16` | `0.01` |

**Das Enum enthält in dieser Stufe genau diesen einen Eintrag.** Kein `SOH`, kein `TEMPERATUR` —
was nicht umgesetzt wird, steht auch nicht in der Registry, sonst nähme ein Leser an, es sei
verfügbar.

> **Die Struktur ist trotzdem belegt:** Eine zweite Grösse ist ein Enum-Eintrag plus ein Eintrag in
> der Pi-Konfiguration. Weder Migration noch DTO-Änderung noch Rollout — das ist der Unterschied zum
> typisierten Payload (FR-1.2). Als Kandidaten stünden bereit: `SOH` (Register `33001`, `U16`,
> `skalierung 0.01`, 0–100 %, nur `SPEICHER`) und `TEMPERATUR` (`11032`–`11035`, `I16`,
> `skalierung 0.1`, −40–100 °C).

> **`skalierung` ist ein Multiplikator**, wie im `modbus_reader` (`value * register.skalierung`):
> Rohwert × `0.01` = Prozent. Die Protokolldokumentation des Wechselrichters notiert dieselbe
> Grösse umgekehrt als „Faktor 100"; wer sie abschreibt, erhält 8750 statt 87.5.

1. **Ein unbekannter Name wird verworfen**, nicht gespeichert. Die Prüfung liegt im Code, damit eine
   neue Grösse **keine** Migration braucht — genau dafür ist die Tabelle schmal.
2. **Ein Wert ausserhalb des Bereichs wird verworfen** und als Systemmeldung erfasst — Level
   `WARN`, Kategorie `SYSTEMMELDUNG_KATEGORIE_MQTT`, Meldungs-Key
   **`GERAETEZUSTAND_WERT_UNGUELTIG`** (neue Übersetzung, DE/EN, §6). Der Parameter nennt Einheit,
   Grösse, Wert und den erlaubten Bereich. Ein SOC von 500 % ist ein Konfigurations- oder
   Skalierungsfehler; still gespeichert würde er später eine Steuerungsentscheidung tragen.
3. **Eine Grösse am falschen Einheiten-Typ wird verworfen** — ein SOC an einer `CONSUMER`-Einheit
   ist ein Konfigurationsfehler, analog zur bestehenden Register-Projektion beim Ingest
   (`BEZUG` erhält nur den Bezug, `RUECKLIEFERUNG` nur die Einspeisung). Systemmeldung mit
   **eigenem** Key **`GERAETEZUSTAND_TYP_UNGUELTIG`** (`WARN`, Kategorie
   `SYSTEMMELDUNG_KATEGORIE_MQTT`).

   > **Warum ein zweiter Key und nicht derselbe:** `SystemmeldungService.erfasse` dedupliziert nach
   > `(orgId, meldungKey)` und **überschreibt** dabei den Parameter des offenen Eintrags. Unter einem
   > gemeinsamen Key zeigte die Meldung abwechselnd den Bereichs- und den Typfehler — zwei Ursachen,
   > die verschiedene Gegenmassnahmen verlangen: einmal die Skalierung im Pi, einmal die Zuordnung
   > der Einheit.

4. **Kein Auto-Resolve** für beide Meldungen. Sie dokumentieren ein vergangenes Ereignis und werden
   vom Betreiber als erledigt markiert — dieselbe Regel wie bei `MQTT_ZAEHLER_LUECKE`
   (`Specs/MQTT-Integration.md`, FR-8). Ein einzelner Ausreisser schlösse die Meldung sonst wieder,
   bevor sie jemand gesehen hat.

> **Zusätzlich ein CHECK-Constraint auf dem Wertebereich**, als zweite Sicherung gegen einen Weg
> an der Anwendung vorbei (SQL-Import, künftiger zweiter Schreibpfad):
> `CHECK (groesse NOT IN ('SOC','SOH') OR (wert >= 0 AND wert <= 100))`.
> Er nennt nur die Prozent-Grössen und ist damit bei einer neuen °C-Grösse **nicht** anzufassen.
> Auf einen CHECK über `groesse` selbst wird **verzichtet**: Er zählte die erlaubten Werte auf und
> verlangte für jede neue Grösse eine Migration — der Nachteil, den die schmale Tabelle gerade
> vermeiden soll.

### FR-4: Schreiben beim Ingest

1. `MqttIngestService` schreibt den Zustandswert **im selben Transaktionsschritt** wie die Rohdaten:
   dieselbe Nachricht, derselbe Zeitstempel, dieselbe Einheit.
2. **Jeder Eintrag der Map wird einzeln geprüft und geschrieben.** Ein ungültiger Eintrag lässt
   die übrigen unberührt — bei mehreren Grössen soll nicht eine falsche die richtigen mitnehmen.
   Geprüft wird in dieser Reihenfolge: Ist der Wert **in eine Zahl umwandelbar**, ist der Schlüssel
   eine **bekannte Grösse**, passt der **Einheiten-Typ**, liegt der Wert **im Bereich**.
3. **Ein nicht umwandelbarer Wert wird verworfen** wie ein Wert ausserhalb des Bereichs — mit
   `GERAETEZUSTAND_WERT_UNGUELTIG`. `null` gilt als **nicht gemeldet**: keine Zeile, keine Meldung,
   kein Fehler. Ein Gerät, das eine Grösse gerade nicht liefern kann, ist kein Störfall.
4. **Fehlt `zustand` oder ist das Objekt leer, entsteht keine Zeile** — kein `null`, keine `0`.
   Sonst liesse sich später nicht unterscheiden, ob der Speicher leer war oder nichts gemeldet hat.
   Dasselbe Argument wie bei den nullable Bilanzspalten in `steuerentscheid` (V149).
5. Ein verworfener Zustandswert (FR-3) **verwirft nicht die Nachricht**: Die Zählerstände werden
   trotzdem gespeichert. Die Werte sind Beiwerk, die Energiemengen sind die Hauptsache.
6. **Auflösen mehrerer Einheiten je Messpunkt:** Der Ingest löst `(org_id, messpunkt)` auf
   **mehrere** Einheiten auf — Bilanz-Typen teilen sich bewusst einen Messpunkt. Ein Zustandswert
   entsteht davon **höchstens einmal**:
   1. Kommen nur Einheiten in Frage, deren Typ die Grösse laut Registry **nicht** zulässt, wird der
      Wert verworfen (FR-3.3).
   2. Lassen **mehrere** ihn zu, wird er an der Einheit mit der **kleinsten `id`** gespeichert —
      eine willkürliche, aber deterministische Wahl. Ohne sie hinge das Ergebnis an der
      Sortierreihenfolge der Abfrage.

   > Mit `SOC` als einziger Grösse tritt Fall 2 nicht ein: Er gilt nur bei `SPEICHER`, und davon gibt
   > es je Mandant höchstens eine Einheit. Die Regel steht hier für die erste Grösse, die an mehreren
   > Typen zulässig ist — bei `TEMPERATUR` (gültig bei allen) entstünden sonst an einem geteilten
   > Bilanzmesspunkt zwei identische Zeilen.

### FR-5: Lesen

1. **Letzter Wert vor einem Zeitpunkt** je Einheit und Grösse — die Abfrage, die eine Steuerung
   braucht: „Wie voll war die Batterie am Ende des Intervalls?"
2. **Werte einer Zeitspanne** je Einheit und Grösse, aufsteigend — für Verlauf und Diagramm.
3. Beide Abfragen laufen **org-explizit** über `hibernateFilterService.enableOrgFilter(orgId)`.

   > **Nicht die parameterlose Variante:** Der erste Nutzniesser ist der `SteuerungJob`, und der
   > läuft ohne Sicherheitskontext — `enableOrgFilter()` würfe dort `NoOrganizationException`.
   > `SteuerungService.werteIntervallAus` verwendet aus demselben Grund bereits die org-explizite
   > Überladung.
   >
   > Der **Ingest** dagegen aktiviert den Filter gar nicht, wie schon für `zaehler_rohdaten`: Er hat
   > keinen Sicherheitskontext und leitet die `org_id` aus dem Topic ab.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

**Transport**
* [ ] Eine Nachricht mit `"zustand": {"soc": 87.5}` erzeugt einen Zustandswert und die
  Zählerstände wie bisher.
* [ ] Eine Nachricht **ohne** `zustand` — und eine mit leerem Objekt — erzeugt **keine** Zeile in
  `geraetezustand`; die Zählerstände bleiben unverändert.
* [ ] Ein unbekanntes Feld im Payload führt **nicht** zum Verwerfen der Nachricht.
* [ ] Eine Nachricht mit **`soc` und einem unbekannten Schlüssel** erzeugt **eine** Zeile — die
  unbekannte Grösse wird verworfen, `soc` entsteht trotzdem.
* [ ] `"zustand": {"soc": "abc"}` und `"zustand": 5` verwerfen **nur den Zustandswert**; die
  Zählerstände derselben Nachricht werden gespeichert (kein Verlust durch fehlerhaftes JSON).
* [ ] `"zustand": {"soc": null}` erzeugt **keine** Zeile und **keine** Systemmeldung — „nicht
  gemeldet" ist kein Störfall.
* [ ] Der Schlüssel wird unabhängig von der Schreibweise abgebildet (`soc`, `SOC`, `Soc`); in der
  Tabelle steht `SOC`.

**Persistierung**
* [ ] Dieselbe Nachricht zweimal empfangen ergibt **eine** Zeile (Upsert auf
  `(einheit_id, zeit, groesse)`), nicht zwei.
* [ ] `zeit` trägt die **Ortszeit** aus dem Payload, verbatim — geprüft an einem Zeitstempel mit
  Offset `+02:00`.
* [ ] `org_id` stammt aus dem Topic, nicht aus dem Payload.

**Validierung**
* [ ] `soc = 87.5` an einer `SPEICHER`-Einheit wird gespeichert.
* [ ] `soc = 101` wird **verworfen** und erzeugt eine Systemmeldung mit Key
  `GERAETEZUSTAND_WERT_UNGUELTIG`, Level `WARN`, Kategorie `SYSTEMMELDUNG_KATEGORIE_MQTT`; der
  Parameter nennt Einheit, Grösse, Wert und erlaubten Bereich. Die Zählerstände derselben Nachricht
  werden **trotzdem** gespeichert.
* [ ] `soc = -1` wird verworfen.
* [ ] `soc` an einer `CONSUMER`-Einheit wird verworfen und erzeugt eine Meldung mit dem **eigenen**
  Key `GERAETEZUSTAND_TYP_UNGUELTIG` — nicht mit dem der Bereichsverletzung.
* [ ] Keine der beiden Meldungen wird automatisch erledigt; sie bleibt offen, bis der Betreiber sie
  abhakt.
* [ ] Ein unbekannter Grössenname wird verworfen, ohne die Nachricht zu verlieren.
* [ ] Lassen **mehrere** aufgelöste Einheiten dieselbe Grösse zu, entsteht **genau eine** Zeile —
  an der Einheit mit der kleinsten `id` (FR-4.6). Mit `SOC` allein ist der Fall konstruiert; das
  Kriterium sichert die Regel für die erste Grösse, die an mehreren Typen gilt.
* [ ] Ein **unbekannter Messpunkt** verwirft die ganze Nachricht wie bisher — auch den
  Zustandswert.
* [ ] Am Umstellungstag im Herbst überschreibt der zweite Durchgang der Stunde 02:00–03:00 die
  Werte des ersten; der Tag hat 96 statt 100 Viertelstunden mit Werten.

**Lesen**
* [ ] „Letzter Wert vor Zeitpunkt" liefert bei mehreren Werten desselben Intervalls den
  **jüngsten** davor, nicht den ersten.
* [ ] Liegt kein Wert vor dem Zeitpunkt, ist das Ergebnis leer — kein Fehler.
* [ ] Die Zeitspannen-Abfrage liefert **aufsteigend** nach `zeit`, Beginn **einschliesslich**, Ende
  **ausschliesslich** — dieselben Grenzen wie `findByZeitVonBetween` der Steuerentscheide.
* [ ] Zustandswerte eines Mandanten sind für einen anderen **nicht** abrufbar.

**Abgrenzung zu den Messwerten**
* [ ] Die Aggregation (`ZaehlerAggregationService`) liest `geraetezustand` **nicht** und schreibt
  nicht hinein; `messwerte` bleibt unverändert.
* [ ] Ein Zustandswert taucht in **keiner** Statistik-Summe und in **keiner** Abrechnung auf.

**Pi-Gateway** (die Vorarbeit aus §6)
* [ ] Ein Register vom Typ `uint16` wird über **ein** Register gelesen; `float32` und `uint32`
  bleiben unverändert bei zwei.
* [ ] Rohwert `8750` mit `skalierung: 0.01` ergibt `87.5` — nicht `875000`.
* [ ] Der Simulator liefert einen SOC, der in der **Ladephase steigt** und in der Entladephase
  fällt, 0–100 nicht verlässt und zu den Zählerständen derselben Nachricht passt.
* [ ] Ohne konfigurierte Zustandsregister enthält der Payload **kein** `zustand`-Objekt — auch kein
  leeres.
* [ ] **Eine gelöschte Einheit nimmt ihre Zustandswerte mit** (`ON DELETE CASCADE`) — geprüft am
  konstruierten Fall „Zustandswert ohne Rohdaten", weil das Löschen sonst an `zaehler_rohdaten`
  scheitert (FR-2).
* [ ] Ein **direktes `INSERT`** mit `wert = 101` und `groesse = 'SOC'` wird von der Datenbank
  abgewiesen — der CHECK greift auch an der Anwendung vorbei.
* [ ] Die Abfrage „letzter Wert vor Zeitpunkt" nutzt den Index `(einheit_id, groesse, zeit DESC)`
  (`EXPLAIN` zeigt einen Index-Scan, keinen Seq-Scan).

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Eine Zeile je Nachricht und Grösse. Der Pi publiziert bei Hene **im Minutentakt**: rund
  **525'000 Zeilen pro Jahr und Gerät**, in zehn Jahren gut fünf Millionen. Für PostgreSQL mit dem
  Index unten unkritisch und in derselben Grössenordnung wie `zaehler_rohdaten`, das denselben Takt
  hat — aber eine andere Grössenordnung als die 35'000 Zeilen, die `steuerentscheid` im Jahr
  erzeugt. Wer später über Retention nachdenkt, fängt bei diesen beiden Tabellen an.
* **Index auf `(einheit_id, groesse, zeit DESC)`** — bei diesem Volumen keine Feinheit, sondern die
  Voraussetzung dafür, dass „letzter Wert vor Zeitpunkt" ein Index-Scan über wenige Zeilen bleibt
  statt eines Durchlaufs durch die Zeitreihe.
* **Strukturelle Grenze statt Zeitangabe:** Eine Nachricht erzeugt **je Grösse genau ein `INSERT`**
  und **keine zusätzliche Abfrage** — die Einheit ist zu diesem Zeitpunkt bereits aufgelöst. Eine
  Sekundenzahl wäre hier keine Prüfung: „schneller als das Publikationsintervall" wäre bei
  Minutentakt noch mit 59 Sekunden erfüllt.

> **Jeder Wert wird gespeichert, obwohl die Steuerung nur jeden fünfzehnten braucht.** Ein Verlauf
> im Minutentakt zeigt, wann die Batterie voll wurde; ein Wert je Viertelstunde zeigt nur, dass sie
> es ist. Die Sparsamkeit spart wenige hundert Megabyte im Jahrzehnt und kostet genau die Auflösung,
> wegen der man später hinsieht.

### NFR-2: Sicherheit
* **Mandantenfähig:** `org_id` ist Pflicht, die Entity trägt `@Filter(orgFilter)`, jede **lesende**
  Abfrage aktiviert ihn org-explizit (FR-5.3). Der Ingest schreibt ohne Filter und leitet die
  `org_id` aus dem **Topic** ab — nie aus dem Payload.
* **Keine neue Angriffsfläche über MQTT:** Der Broker ist die bestehende Schnittstelle; die
  zusätzliche Grösse ändert daran nichts. Validierung nach FR-3 gilt ausdrücklich als
  Sicherheitsmassnahme — Gerätedaten sind nicht vertrauenswürdig.
* **Lesezugriff über die API:** In dieser Stufe gibt es **keinen** Controller (§7). Kommt später
  eine Ansicht dazu, gilt dieselbe Permission wie für die Einheiten — `einheit:read`, also
  `zev_user` aufwärts.

### NFR-3: Kompatibilität
* **Neue Tabelle, neues optionales Feld** — keine Änderung an `messwerte`, `zaehler_rohdaten` oder
  am bestehenden Payload-Vertrag.
* Ein Pi **ohne** das Feld und ein Backend **mit** der Tabelle arbeiten zusammen; ebenso umgekehrt.
* Die Tabelle lässt sich **rückstandslos löschen**: Kein anderer Datensatz verweist auf sie, keine
  Abrechnung hängt daran.

## 5. Edge Cases & Fehlerbehandlung

| Fall | Verhalten |
|---|---|
| **`zustand` fehlt oder ist leer** | Keine Zeile. Die Zählerstände werden normal verarbeitet. |
| **`soc` ausserhalb 0–100** | Wert verworfen, Systemmeldung (`WARN`), Zählerstände trotzdem gespeichert. Bei dauerhaft falscher Skalierung entsteht der Verstoss **jede Minute** — die Deduplizierung des `SystemmeldungService` (je Mandant und Key) trägt das. |
| **`soc` an falschem Einheiten-Typ** | Wert verworfen; eine Meldung nur beim ersten Auftreten je Mandant (Deduplizierung des `SystemmeldungService`), sonst entsteht bei jeder Nachricht eine. |
| **Unbekannter Grössenname** | Verworfen wie ein unbekanntes Feld — die Nachricht bleibt gültig. |
| **Dieselbe Nachricht erneut** | Upsert auf `(einheit_id, zeit, groesse)`; der Wert wird überschrieben. |
| **Zwei Werte innerhalb eines 15-Minuten-Intervalls** | Beide werden gespeichert. Ein Leser, der einen Wert je Intervall braucht, nimmt den **letzten** (FR-5.1). |
| **Unbekannter Messpunkt** | Wie bisher: Die ganze Nachricht wird verworfen (`MqttIngestService`), der Zustandswert also auch. |
| **Zeitumstellung Herbst** | Die Stunde 02:00–03:00 tritt zweimal auf; der zweite Durchgang überschreibt den ersten (Unique in Ortszeit). Hingenommen wie bei `messwerte` und `steuerentscheid`; nachts ist der Ladestand ohne Folgen. |
| **Keine Werte im Zeitraum** | Leere Liste, kein Fehler — wie ein Tag ohne Entscheide. |
| **Einheit wird gelöscht** | Ihre Zustandswerte verschwinden mit (`ON DELETE CASCADE`). Das Löschen scheitert **nicht** an ihnen — anders als bei `zaehler_rohdaten`, deren FK es abweist. |
| **Mehrere Grössen, eine davon ungültig** | Die gültigen werden gespeichert, die ungültige verworfen und gemeldet. Keine Alles-oder-nichts-Regel: Bei drei Grössen soll nicht eine falsche die beiden richtigen mitnehmen. |

## 6. Abhängigkeiten & betroffene Funktionalität

* **Voraussetzungen:**
  - Einheiten-Typ `SPEICHER` (V152, `Specs/Batteriespeicher.md`) — vorhanden.
  - MQTT-Integration und laufender Ingest (`Specs/MQTT-Integration.md`) — vorhanden.
  - **`uint16` im Pi-Gateway — fehlt.** `SOC` steht in **einem** Register (`U16`), der Reader liest
    fest zwei (`_REGISTERS_PER_WERT = 2`) und kennt nur `float32` und `uint32`. Ohne diese
    Erweiterung kann der Pi den Wert nicht lesen. **Das ist die einzige echte Vorarbeit.**

* **Betroffener Code:**
  - **`pi-gateway`, vier Dateien** — der Wert erreicht den Broker sonst nicht:
    - `gateway/config.py`: `uint16` in `_SUPPORTED_REGISTER_TYPES` (heute `{"float32", "uint32"}`,
      ein unbekannter Typ bricht beim Start ab) und ein Konfigurationsformat für Zustandsregister.
    - `gateway/readers/modbus_reader.py`: `uint16` und **variable Registeranzahl** —
      `_REGISTERS_PER_WERT` ist heute fest `2`, `SOC` belegt eines.
    - `gateway/models.py`: `MeterReading` ist ein `@dataclass(frozen=True)` mit festen Feldern; ohne
      ein Feld für die Zustandswerte kommen sie nicht bis zum Publisher.
    - `gateway/publisher.py`: `_to_payload` baut das JSON aus genau diesen Feldern; `zustand` ist
      dort zu ergänzen — **nur wenn Werte vorliegen**, sonst erhielte jede Nachricht ein leeres
      Objekt.
    - `gateway/readers/sim_reader.py`: SOC-Verlauf, **an die bestehende Lade-/Entladephase
      gekoppelt** — steigt in der Ladephase, fällt in der Entladephase, bleibt in 0–100 und kehrt an
      den Grenzen um. Der Speicher-Modus führt die Phase bereits (`_laedt`, `_phase_rest`); ein
      unabhängig gewürfelter SOC widerspräche den Zählerständen derselben Nachricht.
  - `ZaehlerMesswertPayloadDTO`: Objekt `zustand` als `Map<String, Object>` (FR-1.2).
  - `MqttIngestService`: Validierung und Schreiben (FR-3, FR-4). **Neue Abhängigkeit** auf
    `SystemmeldungService` — der Konstruktor kennt heute nur `EinheitRepository`,
    `ZaehlerRohdatenRepository`, `ObjectMapper` und `MqttMetrics`.
  - **`MqttMetrics` bleibt unberührt:** Ein verworfener Zustandswert ruft **kein** `recordFailed()`
    auf. Die Zähler messen die Verarbeitung von Nachrichten; die Nachricht war gültig, nur ein
    Beiwerk nicht. Sonst sänke die Erfolgsquote wegen eines falsch skalierten Registers.
  - **`Specs/MQTT-Integration.md`, FR-3** ist nachzuführen: Dort steht der Payload-Vertrag als
    verbindliche Tabelle. Ohne die Ergänzung gäbe es zwei Beschreibungen desselben Vertrags, eine
    davon unvollständig.
  - Neu: Entity, Repository, `Zustandsgroesse`-Enum, **zwei** Flyway-Migrationen — eine für die
    Tabelle samt Index, eine für die Übersetzungen `GERAETEZUSTAND_WERT_UNGUELTIG` und
    `GERAETEZUSTAND_TYP_UNGUELTIG` (DE/EN, mit `ON CONFLICT (key) DO NOTHING`, deutscher Text mit
    Umlauten und `ss` statt `ß`).
  - **Nicht betroffen:** `ZaehlerAggregationService`, `MesswerteService`, Statistik, Abrechnung.

* **Datenmigration:** Keine. Die Tabelle beginnt leer; für die Vergangenheit gibt es keine Werte und
  sie lassen sich auch nicht rekonstruieren.

## 7. Abgrenzung / Out of Scope

* **Keine Nutzung durch die Einspeisesteuerung.** Dieses Feature schafft die Datengrundlage; ob und
  wie die Regel den Ladestand auswertet, ist eine Änderung an `Specs/Einspeisesteuerung.md` und
  gehört dorthin. Erst die Messung, dann die Entscheidung darüber.
* **Keine Anzeige.** Kein Diagramm, keine Tabelle, keine Seite. Der Wert liegt in der Datenbank und
  wird über das Repository gelesen.
* **Keine weiteren Grössen** ausser `SOC`. `SOH` und `TEMPERATUR` stehen in der Registry, damit
  sichtbar ist, dass die Struktur trägt — umgesetzt wird nur der Ladestand.
* **Keine Retention.** Wie `zaehler_rohdaten` wird nichts gelöscht. Sollte das Volumen je stören,
  ist es eine eigene Entscheidung für **beide** Tabellen.
* **Keine Aggregation, keine Statistik, keine Abrechnung.** Siehe den Kasten am Anfang.

## 8. Offene Fragen

* ~~**Welche Auflösung braucht der SOC?**~~ **Geklärt:** Der Pi publiziert im **Minutentakt**, die
  Steuerung entscheidet alle 15. Gespeichert wird trotzdem jeder Wert (NFR-1). --> Annahme: Alles speichern, was ankommt — das Volumen ist
  unkritisch, und ein Verlauf ist mehr wert als eine Sparsamkeit, die niemand spürt. --> Annahme OK, der Pi puliziert sogar jede Minute
* ~~**Wird der SOC zum Entscheid mitgeschrieben?**~~ **Geklärt: als nächster Schritt, nicht hier.**
  Eine Spalte in `steuerentscheid` würde erklären, *warum* gesperrt wurde — „bei 95 % Ladestand" ist
  eine andere Aussage als „bei 40 %". Das ist eine Änderung an `Specs/Einspeisesteuerung.md` und
  folgt, sobald die Werte fliessen.
* ~~**Enthält die Registry alle drei Grössen oder nur `SOC`?**~~ **Geklärt: nur `SOC`** (FR-3). Was
  nicht umgesetzt wird, steht auch nicht in der Registry.
* ~~**Welche Einheit gewinnt bei mehreren Treffern je Messpunkt?**~~ **Geklärt: die mit der kleinsten
  `id`** (FR-4.6) — willkürlich, aber deterministisch.
* ~~**Eigener Meldungs-Key für die Typverletzung?**~~ **Geklärt: ja**, `GERAETEZUSTAND_TYP_UNGUELTIG`
  (FR-3.3). Ein gemeinsamer Key zeigte abwechselnd zwei Ursachen, die verschiedene Gegenmassnahmen
  verlangen.

* **Soll `TEMPERATUR` mehrere Sensoren je Gerät unterscheiden?** *(offen — betrifft erst die zweite
  Grösse)* Der Solinteg liefert vier (`11032`–`11035`). Der Schlüssel `(einheit_id, zeit, groesse)`
  lässt nur **einen** Wert je Grösse zu. --> Bei Bedarf entweder eigene Grössen (`TEMPERATUR_1` …
  `TEMPERATUR_4`) oder eine weitere Schlüsselspalte. Nicht Teil dieser Stufe — die Entscheidung fällt
  mit der Anforderung, die Temperatur wirklich braucht. **Durch den generischen Wire-Contract (FR-1)
  ist die erste Variante inzwischen billig:** vier Einträge in der Registry, kein DTO-Feld, kein
  Pi-Rollout.
* ~~**Sollen Zustandswerte über die REST-API lesbar sein?**~~ **Geklärt: bei Bedarf.** Vorerst nur
  Repository-Zugriff; ein Controller kommt mit der Ansicht, die ihn braucht.
* ~~**Deduplizierung der Systemmeldung bei dauerhaft falschem Wert:**~~ **Geklärt: so übernommen.**
  Ein falsch skalierter SOC erzeugt **jede Minute** einen Verstoss; der `SystemmeldungService` zählt
  gleiche Meldungen je Mandant und Key zusammen. An dieser Frequenz ist der Mechanismus nie erprobt
  worden — bleibt als Beobachtungspunkt für die erste Betriebswoche.
