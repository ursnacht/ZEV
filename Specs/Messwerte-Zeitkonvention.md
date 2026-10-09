# Messwerte-Zeitkonvention

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Jeder Messwert in `zev.messwerte` trägt als `zeit` den **Beginn**
  seines 15-Minuten-Intervalls, in Ortszeit (Europe/Zurich) — unabhängig davon, woher er stammt.
  Der Wert für 10:00–10:15 steht unter `10:00`.

* **Warum machen wir das:** Heute stehen in derselben Tabelle zwei Konventionen nebeneinander, und
  die Programmteile setzen jeweils eine davon voraus:

  | Quelle | Mandant | `messwerte.zeit` heute |
  |---|---|---|
  | MQTT-Live-Erfassung (`ZaehlerAggregationService`) | Hene | **Ende** des Intervalls |
  | CSV-Import, Bilanz-CSV (`MesswerteService`) | Mut13 (nur lokal) | **Beginn** des Intervalls |

  | Programmteil | setzt voraus |
  |---|---|
  | Statistik (Monate, Gesamt, Summen pro Einheit, PDF, CSV-Export) | Beginn |
  | Stromrechnungen (`RechnungService`, Mengen je Tarif) | Beginn |
  | Messwert-Anzeige | Beginn |
  | Einspeisesteuerung (`SteuerungService`), Produktionsprognose, Lastprofil | Ende — per Verschiebung um 15 Minuten umgerechnet |

  **Folge auf Hene:** Statistik, Stromrechnungen und Messwert-Anzeige sind an **jeder**
  Zeitraumgrenze um eine Viertelstunde verschoben. Die letzte Viertelstunde eines Tages, Monats
  oder Quartals zählt zum Folgezeitraum, die erste des Vorzeitraums zum aktuellen.

  > **Beispiel 08.10.2026, Verbrauch:** Statistik **31.405 kWh**, Einspeisesteuerung
  > **31.477 kWh**. Die Statistik zählt 23:45–24:00 des 7.10. (0.222 kWh) mit und lässt
  > 23:45–24:00 des 8.10. (0.294 kWh) weg. Die Einspeisesteuerung ist richtig.

  **Warum Beginn und nicht Ende** (Variante A): Statistik, Stromrechnungen und Messwert-Anzeige —
  also gerade der Abrechnungsteil — sind für Beginn bereits richtig und bleiben **unverändert**.
  Und `messwerte`, `steuerentscheid.zeit_von` und `einstrahlungsprognose.zeit` sprechen danach
  dieselbe Sprache. Die Unterscheidung „Messwert trägt das Ende, Entscheid den Beginn" war in der
  Einspeisesteuerung und der Ladeplanung mehrfach Fehlerquelle (`Specs/Einspeisesteuerung.md`,
  FR-3; `Specs/Ladeplanung.md`, FR-3) — sie entfällt.

* **Aktueller Stand:**
  * `ZaehlerAggregationService.verarbeiteIntervall` schreibt `upsertMesswert(einheit, ende, total)`.
  * `MesswerteService.processCsvUpload` und `processBilanzCsvUpload` zählen von `00:00` des
    gewählten Tages in Viertelstunden weiter — der erste Wert steht unter `00:00`.
  * `SteuerungService` und `ProduktionsprognoseService` lesen Messwerte mit einem um 15 Minuten
    verschobenen Fenster und rechnen den Zeitstempel auf den Beginn zurück.
  * **`Specs/MQTT-Integration.md`, FR-6, legt nicht fest, welcher Zeitpunkt in `messwerte.zeit`
    kommt.** Die Spec beschreibt Intervallgrenzen und Deltas, aber nicht den Stempel — diese Lücke
    hat die zweite Konvention entstehen lassen.
  * Produktiv betroffen ist nur **Hene** (Mandant „Scherli", ausschliesslich `quelle = 'MQTT'`,
    rund 141'000 Zeilen ab 14.07.2026 09:45, wachsend um 18 Einheiten × 96 je Tag). Mut13 gibt es nur lokal, und seine
    Daten tragen bereits den Beginn. **Neue Installationen sind nicht betroffen** — sie erfassen
    von Anfang an mit dem neuen Code.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Die Konvention

`zev.messwerte.zeit` ist der **Beginn** des 15-Minuten-Intervalls, dessen Menge `total` (und `zev`,
`zev_calculated`) beschreibt, als **Ortszeit** ohne Zone (`LocalDateTime`, Europe/Zurich).

* Gilt für **jede** Quelle (`CSV`, `MQTT`, `API`).
* Ein Tag umfasst die Zeitstempel `00:00` bis `23:45`; ein Zeitraum `[von, bis]` die Stempel
  `>= von 00:00` und `< bis+1 00:00`. Genau so fragen Statistik und Rechnungen heute schon ab.
* Die Konvention wird festgeschrieben, damit sie nicht erneut auseinanderläuft: im Javadoc der
  Entity `Messwerte` und in `Specs/MQTT-Integration.md`, FR-6. Auf Hene setzt das Umstellungsskript
  (FR-5) zusätzlich den Spaltenkommentar `zev.messwerte.zeit`.

> **Nicht betroffen sind Momentanwerte.** `zev.geraetezustand` (Ladezustand) und
> `zev.zaehler_rohdaten` (Zählerstände) halten Zustände zu einem **Zeitpunkt**, keine Mengen über
> ein Intervall. Für sie gibt es keinen Beginn; sie bleiben, wie sie sind. Der Ladezustand eines
> Entscheids ist weiterhin der letzte Wert **vor dem Intervallende**.

### FR-2: Live-Erfassung stempelt den Beginn

`ZaehlerAggregationService.verarbeiteIntervall` schreibt den Messwert unter dem **Intervallbeginn**:
`upsertMesswert(einheit, start, total)`.

* Die Delta-Bildung bleibt unverändert: `letzter Stand ≤ Intervallende − letzter Stand ≤
  Intervallbeginn`. Nur der **Stempel** wechselt.
* **Fenster der anschliessenden Solarverteilung:** `orgBis` wird der **späteste Intervallbeginn**
  statt des spätesten Intervallendes; das Fenster ist `[frühester Intervallbeginn, spätester
  Intervallbeginn]`. Mit dem Ende als obere Grenze nähme das inklusive `BETWEEN` von
  `findDistinctZeitBetween` den Stempel des Folgeintervalls mit (harmlos, aber unnötig).
* Systemmeldungen (Datenlücke, Zählerwechsel, negatives Delta) bleiben unverändert — sie nennen
  Zählerstände und Intervallgrenzen, keine Messwert-Stempel.
* Trifft die Erfassung auf einen bestehenden **CSV**-Wert mit gleichem Stempel, überschreibt sie ihn
  wie bisher mit Warnung. **Neu ist das fachlich richtig:** Beide meinen jetzt dieselbe
  Viertelstunde. Heute meinten sie verschiedene — das Überschreiben ersetzte einen Wert durch den
  eines anderen Intervalls.

### FR-3: Einspeisesteuerung ohne Verschiebung

In `SteuerungService` entfallen die Verschiebungen um 15 Minuten **bei allen Zugriffen auf
`messwerte`**:

| Stelle | heute | künftig |
|---|---|---|
| `messungFuer(zeitVon)` — Messung eines Intervalls | Fenster `[zeitVon+15, zeitVon+30)` | `[zeitVon, zeitVon+15)` |
| `speicherFuer(zeitVon)` — Ladung/Entladung eines Intervalls | Fenster ab `zeitVon+15` | ab `zeitVon` |
| `rechneNach(...)` — Rückrechnung über Tage | Fenster `+15`, Stempel `−15` | Fenster und Stempel unverschoben |
| `reichereSpeicherAn(...)` — Speichermengen je Tag für die Entscheid-Anzeige | Fenster `+15`, Schlüssel `−15` | unverschoben |

**Unverändert bleiben:** der Ladezustand (`socAmIntervallende`, Anzeige-Zuordnung über
`lowerEntry(zeit + 15)`) — er ist ein Momentanwert, siehe FR-1 —, das Vorintervall des Entscheids
(`steuerentscheid`, bereits Beginn) und die Preise (`preiszeitreihe`, UTC, eigene Umrechnung).

Der Block „ZEITBEZÜGE" im Klassenkommentar von `SteuerungService` wird auf FR-1 umgeschrieben.

### FR-4: Produktionsprognose ohne Verschiebung

In `ProduktionsprognoseService` entfallen die Verschiebungen bei:

* **Lernfenster des Umrechnungsfaktors** (`sumBilanzKomponentenPerZeitBetween`): Fenster
  `[von, bis)` statt `[von+15, bis+15)`, Zuordnung zur Einstrahlung über den Stempel selbst statt
  `Stempel − 15`.
* **Speicherfluss je Intervall** (`speicherflussJeIntervall`): Fenster und Schlüssel unverschoben.
* **Lastprofil:** Zuordnung zur Tageszeit über den Stempel selbst.

Die Javadoc-Hinweise „`messwerte.zeit` trägt das Intervall-Ende" (u. a. `MesswerteRepository`)
werden entfernt bzw. auf FR-1 umgeschrieben.

### FR-5: Einmalige Datenumstellung auf Hene

Die bestehenden **MQTT**-Messwerte von Hene werden **einmalig** um 15 Minuten zurückgestempelt —
**mit einem SQL-Skript, nicht mit Flyway**. Neue Installationen haben keine alten Daten; eine
Flyway-Migration liefe dort ins Leere und bliebe für immer im Migrationsverlauf.

**Datei:** `scripts/messwerte-zeit-intervallbeginn.sql`. Ausgeführt **vom User**, von Hand, im
Rahmen des Ausrollens (Reihenfolge siehe NFR-3):

```sh
sudo docker exec -i postgres sh -c 'psql -v ON_ERROR_STOP=1 -U $POSTGRES_USER -d zev' \
  < scripts/messwerte-zeit-intervallbeginn.sql
```

**Inhalt — ein einziger `DO`-Block.** Ein `DO`-Block ist atomar: Bricht er ab, ist nichts
verändert, unabhängig davon, ob `psql` mit `ON_ERROR_STOP` oder `--single-transaction` aufgerufen
wurde. Die Prüfungen stehen **vor** dem `UPDATE`.

```sql
DO $$
DECLARE
    kollisionen bigint;
    beispiel    text;
    verschoben  bigint;
BEGIN
    -- 1. Schon ausgeführt? Das Skript setzt am Ende den Spaltenkommentar.
    IF col_description('zev.messwerte'::regclass,
           (SELECT attnum FROM pg_attribute
             WHERE attrelid = 'zev.messwerte'::regclass AND attname = 'zeit')) LIKE 'Beginn%' THEN
        RAISE EXCEPTION 'Bereits umgestellt: zev.messwerte.zeit trägt schon den Intervallbeginn';
    END IF;

    -- 2. Kollision: Eine MQTT-Zeile landet auf einer Zeile, die NICHT mitwandert.
    --    (MQTT gegen MQTT kann nicht kollidieren — alle wandern gemeinsam.)
    SELECT count(*),
           min(format('Einheit %s, %s', b.einheit_id, to_char(b.zeit, 'YYYY-MM-DD HH24:MI')))
      INTO kollisionen, beispiel
      FROM zev.messwerte a
      JOIN zev.messwerte b
        ON b.einheit_id = a.einheit_id
       AND b.zeit = a.zeit - INTERVAL '15 minutes'
       AND b.quelle <> 'MQTT'
     WHERE a.quelle = 'MQTT';
    IF kollisionen > 0 THEN
        RAISE EXCEPTION '% MQTT-Messwerte fielen auf bestehende Nicht-MQTT-Werte, z. B. %',
            kollisionen, beispiel;
    END IF;

    -- 3. Umstellen
    UPDATE zev.messwerte SET zeit = zeit - INTERVAL '15 minutes' WHERE quelle = 'MQTT';
    GET DIAGNOSTICS verschoben = ROW_COUNT;

    COMMENT ON COLUMN zev.messwerte.zeit IS
        'Beginn des 15-Minuten-Intervalls, Ortszeit Europe/Zurich ohne Zone';

    RAISE NOTICE '% MQTT-Messwerte auf den Intervallbeginn umgestellt', verschoben;
END $$;
```

* **Nur `quelle = 'MQTT'`.** CSV-Werte tragen bereits den Beginn und bleiben unberührt.
* **Mit der Zeile wandern `total`, `zev` und `zev_calculated`.** Die Solarverteilung muss nicht neu
  laufen: Sie verteilt je Zeitstempel innerhalb eines Mandanten, und alle MQTT-Zeilen eines
  Mandanten verschieben sich gemeinsam — die Gruppen bleiben dieselben, nur ihr Stempel ändert sich.
* **Warum die Kollisionsprüfung:** Auf `messwerte` gibt es **keine** Eindeutigkeitsregel für
  `(einheit_id, zeit)` — ohne die Prüfung entstünden stillschweigend Doppelzeilen, die Statistik
  und Rechnung doppelt zählten. Die Verschiebung aller MQTT-Zeilen um denselben Betrag ist unter
  sich eindeutig; neue Doppelzeilen können nur gegenüber Zeilen anderer Quelle entstehen. Geprüft
  am 09.10.2026: Hene hat ausschliesslich MQTT-Zeilen, lokal gibt es keine solche Überschneidung.
* **Warum die Wiederholungssperre:** Ein zweiter Lauf verschöbe alles um weitere 15 Minuten —
  ohne Fehlermeldung, weil die Kollisionsprüfung dabei nichts findet. Ohne Flyway gibt es keinen
  Migrationsverlauf, der das verhindert; der Spaltenkommentar übernimmt die Rolle. Auf Hene ist er
  heute leer (geprüft 09.10.2026).
* **Bewusst nicht geprüft: „Der neue Code hat schon geschrieben."** Läuft das Skript erst, nachdem
  die neue Version Messwerte erfasst hat, verschiebt es auch deren — bereits richtige — Stempel,
  ohne Meldung. Das Skript erkennt das nicht; der Schutz ist die Reihenfolge in NFR-3, die der User
  beim Ausrollen auf Hene einhält (Entscheid 09.10.2026). **Das Skript läuft nur, solange noch die
  alte Version die Messwerte geschrieben hat.**

**Lokale Entwicklungsdatenbank:** Sie enthält neben den Mut13-CSV-Daten 8'964 MQTT-Zeilen aus der
Testerfassung (10.07.–09.10.2026). Ohne Umstellung lägen sie nach dem Code-Wechsel eine
Viertelstunde versetzt. Nötig ist die Umstellung nicht. Wer sie will, führt das Skript **vor** dem
ersten Start der neuen Version aus (`docker exec` ohne `sudo`) — danach nicht mehr, aus demselben
Grund wie auf Hene.

**Keine Schemaänderung.** `steuerentscheid`, `einstrahlungsprognose`, `geraetezustand`,
`zaehler_rohdaten` und `preiszeitreihe` bleiben unverändert.

### FR-6: Persistierung

Keine neue Tabelle, keine neue Spalte. Änderungen: Daten der MQTT-Zeilen und Spaltenkommentar
`messwerte.zeit` auf Hene (FR-5, Skript). Dazu eine Flyway-Migration, die **nur** einen Kommentar
anpasst: `V173__Einstrahlungsprognose_Zeit_Kommentar.sql` (präzisiert durch
`V174__Einstrahlungsprognose_Zeit_Kommentar_Praezisierung.sql`) korrigiert den Spaltenkommentar von
`einstrahlungsprognose.zeit` aus V166, der `messwerte.zeit` noch „Intervallende" nennt. Den
Kommentar auf `messwerte.zeit` setzt sie **nicht** — an ihm erkennt das Skript, dass es schon
gelaufen ist. Mandantenfähigkeit unverändert (`org_id` bleibt, das Skript
verschiebt nur `zeit`).

### FR-7: Layout

Keine UI-Änderung. Sichtbare Wirkungen auf Hene nach der Umstellung:

* **Statistik** und **Stromrechnungen**: Werte an Zeitraumgrenzen ändern sich um eine
  Viertelstunde (siehe Abschnitt 1).
* **Messwert-Anzeige**: Die Punkte liegen 15 Minuten früher — am Beginn ihres Intervalls, wie bei
  CSV-Daten.
* **„Messwerte vorhanden bis"** (Statistik): Ein vollständiger Tag endet künftig bei `23:45`
  desselben Tages statt bei `00:00` des Folgetags — die Anzeige nennt den richtigen Tag.
* **Einspeisesteuerung** und **Prognose**: unverändert — sie rechneten bereits richtig.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

**Konvention und Live-Erfassung**

* [x] Ein von der Live-Erfassung erzeugter Messwert für das Intervall 10:00–10:15 steht unter
      `zeit = 10:00`.
* [x] Die Menge dieses Messwerts ist unverändert `letzter Stand ≤ 10:15 − letzter Stand ≤ 10:00`.
* [x] Die anschliessende Solarverteilung läuft über `[frühester Intervallbeginn, spätester
      Intervallbeginn]`; der Stempel des Folgeintervalls liegt nicht mehr im Fenster.
* [x] Erfasst die Live-Erfassung ein Intervall erneut (spät eintreffende Rohdaten), aktualisiert sie
      den Messwert unter dem Intervallbeginn; es entsteht keine zweite Zeile.
* [x] Javadoc der Entity `Messwerte` und `Specs/MQTT-Integration.md`, FR-6 (inkl. FR-6.7),
      nennen die Konvention „Beginn, Ortszeit".
* [x] Kein Kommentar im Code sagt mehr, `messwerte.zeit` trage das Intervallende: Klassenkommentar
      „ZEITBEZÜGE" in `SteuerungService`, Javadoc in `ProduktionsprognoseService` und
      `MesswerteRepository` sind auf FR-1 umgeschrieben.
* [x] `Specs/Einspeisesteuerung.md` (FR-3) und `Specs/Ladeplanung.md` (FR-3) beschreiben keinen
      Zeitversatz zwischen `messwerte` und `steuerentscheid` bzw. `einstrahlungsprognose` mehr.

**Steuerung und Prognose ohne Verschiebung (Unit-Tests)**

* [x] `werteIntervallAus(org, 10:00)` liest Produktion, Verbrauch, Ladung und Entladung aus den
      Messwerten mit Stempel `10:00` (nicht `10:15`).
* [x] `getEntscheideSimuliert` und `simuliere` ordnen den Messwert mit Stempel `10:00` dem
      Intervall `10:00` zu (`rechneNach`, `reichereSpeicherAn`).
* [x] Der Umrechnungsfaktor ordnet den Messwert mit Stempel `10:00` der Einstrahlung mit Stempel
      `10:00` zu; Speicherfluss und Lastprofil ebenso.

**Umstellungsskript (Integrationstest)**

* [x] Nach dem Skript ist jede MQTT-Zeile um genau 15 Minuten früher gestempelt als zuvor;
      `total`, `zev`, `zev_calculated`, `einheit_id`, `org_id` und `quelle` sind unverändert.
* [x] Anzahl der MQTT-Zeilen und Summe von `total` je Einheit sind vor und nach dem Skript gleich.
* [x] CSV-Zeilen sind unverändert (Stempel und Werte).
* [x] Nach dem Skript gibt es kein `(einheit_id, zeit)`, das häufiger vorkommt als vorher.
* [x] Liegt für eine Einheit eine CSV-Zeile genau 15 Minuten vor einer MQTT-Zeile, bricht das
      Skript mit einer Meldung ab, die Anzahl und ein Beispiel nennt; die Datenbank ist unverändert
      (Stempel, Werte und Spaltenkommentar).
* [x] Ein zweiter Lauf bricht mit „Bereits umgestellt" ab und verändert nichts.
* [x] Ohne MQTT-Zeilen läuft das Skript fehlerfrei durch; die CSV-Zeilen sind unverändert, der
      Spaltenkommentar ist gesetzt. (Die `NOTICE` mit der Anzahl ist Bedienerhilfe in `psql` und
      wird im Test nicht geprüft.)
* [x] Nach dem Skript trägt `zev.messwerte.zeit` den Spaltenkommentar aus FR-5.

**Gleichstand der Auswertungen (auf Hene, nach der Umstellung)**

> **Tagesverbrauch der Statistik** heisst hier: `summeConsumerTotal` der Statistik mit
> `von = bis = Tag` (`StatistikService`, `sumTotalByEinheitTypAndZeitBetween` über die
> `CONSUMER`-Einheiten). Die Summen sind `double`; „gleich" heisst deshalb auf **0.001 kWh**.

* [x] Statistik und Einspeisesteuerung zeigen für den 08.10.2026 denselben Verbrauch:
      **31.477 kWh**.
* [x] Für jeden Tag vom 14.07. bis zum Tag vor dem Ausrollen ist der Tagesverbrauch der Statistik
      gleich der Summe `total` der MQTT-Messwerte der `CONSUMER`-Einheiten, die **vor** der
      Umstellung die Stempel `Tag 00:15` bis `Folgetag 00:00` trugen (gegen das Backup prüfbar).
* [x] Für jeden Tag vom **13.09. bis 08.10.2026** **ausser** 13.09., 14.09., 15.09., 17.09.,
      18.09., 20.09. und 03.10. stimmt der Tagesverbrauch der Statistik mit der Summe
      `steuerentscheid.verbrauch` überein.

  > **Warum fester Zeitraum und Ausnahmen.** Stand 09.10.2026 stimmen 19 von 26 Tagen bereits
  > heute mit dem korrigierten Fenster exakt. An fünf Tagen fehlen Entscheide (Neustarts; am
  > 13.09. nur 28 Intervalle), am 14.09. und 20.09. trafen Messwerte nach dem Entscheid noch ein —
  > obwohl 96 Entscheide vorliegen. Der Entscheid hält einen Schnappschuss, und `zev.messwerte`
  > hat keinen Erfassungszeitpunkt, mit dem sich das nachträglich unterscheiden liesse. Für spätere
  > Tage lässt sich deshalb keine sichere Regel angeben; ausserdem sind Ausrolltag (Lücke, NFR-3)
  > und 25.10. (Zeitumstellung) ohnehin Sonderfälle. Diese Abweichungen liegen in der Steuerung,
  > nicht in der Konvention.
* [x] Die nachgerechnete Tagesansicht (`rechneNach` + `reichereSpeicherAn`) liefert an denselben
      Tagen dieselben Werte für Produktion, Verbrauch, Ladung und Entladung wie die gespeicherten
      Entscheide (0.001 kWh).
* [x] „Messwerte vorhanden bis" (Statistik, `messwerteBisDate`) nennt nach einem vollständig
      erfassten Tag diesen Tag, nicht den Folgetag.
* [x] Der gelernte Umrechnungsfaktor für einen gegebenen Tag ist vor und nach der Umstellung
      gleich (die Zuordnung Messwert ↔ Einstrahlung ändert sich nicht, nur ihr Weg).
* [x] Das Lastprofil eines gegebenen Tages ist vor und nach der Umstellung gleich.

**Unverändert**

* [x] Für Mut13 (lokal) liefern Statistik und Stromrechnungen vor und nach der Umstellung
      dieselben Werte.
* [x] Der Ladezustand eines Entscheids ist vor und nach der Umstellung derselbe.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Das Skript ist **ein** `UPDATE` über rund 141'000 Zeilen; der Index `idx_messwerte_zeit` muss
  dabei mitgepflegt werden. Die Kollisionsprüfung ist ein Join über `einheit_id` und `zeit`, für
  den es nur die Einzelindizes gibt (V7, V8). Bei dieser Datenmenge: Sekunden, nicht Minuten.
* Laufzeitverhalten der Jobs unverändert.

### NFR-2: Sicherheit
* Keine neuen Endpunkte, keine geänderten Berechtigungen (`@PreAuthorize` unverändert):

  | Funktion | Permission | Rollen |
  |---|---|---|
  | Statistik | `statistik:read` | `zev_user`, `org_admin`, `zev_admin` |
  | Rechnungen | `rechnungen:manage` | `zev_user`, `org_admin`, `zev_admin` |
  | Einspeisesteuerung | `tarife:manage` | `org_admin`, `zev_admin` |
* Mandanten-Isolation unverändert: Das Skript verschiebt nur `zeit`, `org_id` bleibt.
* Das Skript braucht weder Passwort noch Benutzername — es läuft im Postgres-Container unter
  `$POSTGRES_USER` (wie `scripts/db-backup.sh`).

### NFR-3: Kompatibilität und Ausrollen auf Hene

**Kompatibilität:** REST-Endpunkte und DTOs bleiben unverändert. Was sich ändert, sind die Werte:
Zeitstempel von MQTT-Messwerten liegen 15 Minuten früher — in der Messwert-Anzeige und im
CSV-Export der Statistik ebenso wie in der Datenbank.

**Ausrollen — die Reihenfolge ist zwingend.** Weder alter Code auf umgestellten Daten noch neuer
Code auf alten Daten darf auch nur ein Intervall erfassen. Neuer Code schreibt das Intervall
`[t, t+15)` unter `t` — dort steht bei alten Daten noch das Vorintervall `[t−15, t)`, und
`upsertMesswert` **überschreibt** es; ab da liegen beide Konventionen gemischt vor. Alter Code nach
dem Skript schreibt unter `t+15`, wo der neue Code später überschreibt. Beides lässt sich
nachträglich nicht sauber trennen.

Ablauf auf dem NAS (`ssh <user>@<nas-host>`, `cd /volume1/docker/zev`; `sudo` nur, wo `docker`
es dort braucht):

0. **Vorher, bei laufendem Betrieb:** Images der neuen Version bauen, übertragen und laden
   (`docs/Deploy-Hene.md`, Schritte 1 und 2 „Images laden"). `docker load` berührt den laufenden
   Container nicht. So fällt der Build nicht in die Ausfallzeit.
1. **Alte Version anhalten:**
   ```sh
   sudo docker stop backend-service
   ```
2. **Backup ziehen und prüfen** (`docs/Datenbank-Backup.md`) — der Rückweg ist der Restore:
   ```sh
   DOCKER="sudo docker" ./scripts/db-backup.sh /volume1/backup/zev
   DOCKER="sudo docker" ./scripts/db-restore.sh --nur-pruefen /volume1/backup/zev/zev-<JJJJ-MM-TT_HHMM>.dump
   ```
3. **Skript ausführen** (Befehl in FR-5). Erwartet: `NOTICE: … MQTT-Messwerte auf den
   Intervallbeginn umgestellt` mit der Zeilenzahl von
   `SELECT count(*) FROM zev.messwerte WHERE quelle = 'MQTT'`.
4. **Neue Version starten:**
   ```sh
   sudo docker compose up -d
   ```

* **Datenlücke während der Ausfallzeit (hingenommen).** Backend und Pi verbinden sich mit Clean
  Session (`MqttConfig`, `pi-gateway/gateway/publisher.py`); Zählerstände, die zwischen 1. und 4.
  gesendet werden, gehen verloren. Nach dem Start meldet die Erfassung eine Datenlücke, und das
  erste Intervall danach erhält das Delta der ganzen Ausfallzeit (Sammel-Intervall). Das ist bei
  jedem Update so und für Verteilung und Steuerung hinnehmbar (Entscheid 09.10.2026); Schritt 0
  hält die Ausfallzeit auf wenige Minuten.
* Bereits gespeicherte Steuerentscheide, Einstrahlungsprognosen und Ladeplan-Schattenwerte bleiben
  gültig: Sie tragen ihre eigenen, schon heute am Beginn orientierten Zeitstempel und Schnappschüsse
  der Messwerte. Die Auswertung der Schattenrechnung (ab 11.10.2026) liest nur `steuerentscheid`
  und `einstrahlungsprognose` und ist nicht betroffen.

## 5. Edge Cases & Fehlerbehandlung

* **Keine MQTT-Zeilen** (z. B. reine CSV-Installation): Das Skript verschiebt nichts und läuft
  fehlerfrei durch.
* **Überschneidung MQTT/CSV** bei derselben Einheit: Abbruch des Skripts (FR-5), nichts verändert.
  Auflösung von Hand — klären, welcher Wert gilt —, dann erneut ausführen.
* **Skript zweimal ausgeführt:** Der zweite Lauf bricht ab (Spaltenkommentar), nichts verändert.
* **Skript vergessen, neue Version gestartet:** Das Grenzintervall wird überschrieben, danach
  liegen beide Konventionen gemischt vor (NFR-3). Das Skript jetzt nachzuholen **verschlimmert**
  es: Es verschiebt auch die schon richtig gestempelten neuen Zeilen, ohne Meldung (FR-5, „Bewusst
  nicht geprüft"). Rückweg: Backup aus Schritt 2 zurückspielen und den Ablauf ab Schritt 3
  wiederholen; Messwerte seit dem Backup gehen dabei verloren.
* **Datenlücke beim Ausrollen:** siehe NFR-3 — hingenommen, wie bei jedem Update.
* **Spät eintreffende Rohdaten nach der Umstellung:** Die Aggregation verarbeitet ein Intervall
  erneut und findet den bestehenden Messwert unter dem Beginn — sie aktualisiert ihn, statt einen
  zweiten anzulegen.
* **Früheste Zeile** (Hene: 14.07.2026 09:45) wird `09:30`. Zeilen mit Stempel `00:00` gehören
  zum Vortag (Intervall 23:45–24:00) und wandern dorthin — das ist gewollt und genau die Korrektur
  der Tagesgrenze.
* **Zeitumstellung:** Die Zeitstempel sind Ortszeit ohne Zone. In der Nacht der Umstellung auf
  Winterzeit (25.10.2026) gibt es die Stunde 02:00–03:00 zweimal.
  * **MQTT:** Vorbestehend, das Verhalten ist nicht spezifiziert. Auch die Rohdaten tragen
    Ortszeit ohne Zone; die Stände beider Durchgänge mischen sich in der Delta-Bildung, und
    `upsertMesswert` schreibt pro Einheit und Stempel höchstens eine Zeile. Die Werte dieser
    Stunde sind unzuverlässig — heute wie nach der Umstellung. Die Kollisionsprüfung des Skripts
    vergleicht MQTT nur mit anderen Quellen und ist davon nicht berührt.
  * **CSV:** Doppelzeilen möglich (siehe Abschnitt 7).
* **Ungültige Eingaben, Netzwerkfehler:** nicht berührt — es gibt keine neue Eingabe und keinen
  neuen Aufruf.

## 6. Abhängigkeiten & betroffene Funktionalität

* **Code (geändert):**
  * `service/ZaehlerAggregationService.java` — Stempel `start`, `orgBis`
  * `service/SteuerungService.java` — vier Zugriffe auf `messwerte` (FR-3), Klassenkommentar
    „ZEITBEZÜGE"
  * `service/ProduktionsprognoseService.java` — Faktor, Speicherfluss, Lastprofil (FR-4)
  * `entity/Messwerte.java`, `repository/MesswerteRepository.java` (Javadoc der
    Zeitraum-Abfragen) — Javadoc
* **Neu:** `scripts/messwerte-zeit-intervallbeginn.sql` (FR-5); `V173__Einstrahlungsprognose_Zeit_Kommentar.sql` und `V174__Einstrahlungsprognose_Zeit_Kommentar_Praezisierung.sql` (nur Kommentar, FR-6). Keine Flyway-Migration für die Daten.
* **Code (unverändert, Ergebnisse auf Hene ändern sich):** `StatistikService`,
  `StatistikPdfService`, `RechnungService`, `MesswerteService` (Anzeige, CSV-Import, Verteilung).
* **Nicht betroffen:** Nebenkostenabrechnung (liest keine Messwerte), Preiszeitreihe,
  Gerätezustand, Zählerrohdaten.
* **Tests:**
  * `ZaehlerAggregationServiceTest` — Stempel und Verteilfenster anpassen.
  * `SteuerungServiceTest` — **neu** (gibt es noch nicht). Über die öffentlichen Methoden:
    `werteIntervallAus` (deckt `messungFuer` und `speicherFuer`), `getEntscheideSimuliert`
    (`rechneNach` und `reichereSpeicherAn`), `simuliere` (`rechneNach` über mehrere Tage).
  * `ProduktionsprognoseServiceTest` — die bestehenden Zeitversatz-Tests werden zu Tests der
    **fehlenden** Verschiebung.
  * **Integrationstest für das Skript** (`*IT`, Testcontainers): Datenbank mit allen
    Flyway-Migrationen, Testdaten einfügen (MQTT, CSV, Kollisionsfall), Skriptdatei aus
    `scripts/` **als Ganzes** per JDBC ausführen (nicht über `ScriptUtils` — das trennt an `;`
    und zerlegt den `DO`-Block). Prüft Verschiebung, CSV unberührt, Abbruch bei Kollision mit
    unveränderter Datenbank, Abbruch beim zweiten Lauf.
* **Specs nachzuführen:**
  * `Specs/MQTT-Integration.md` — FR-6 (Stempel = Beginn) **inkl. FR-6.7** (Verteilfenster
    „spätestes Intervall-Ende" → „spätester Intervallbeginn")
  * `Specs/Einspeisesteuerung.md` — FR-3 (Zeitbezüge)
  * `Specs/Ladeplanung.md` — FR-3 (Zeitversatz), insbesondere die Hinweise „`messwerte.zeit`
    (Intervall**ende**)" und „Zeitversatz beachten — für Faktor und Lastprofil"
  * `Specs/Zaehlertausch-Erkennung.md` — nur Begriffe prüfen
* **Betrieb:** Reihenfolge, Backup und Skript gemäss NFR-3; der Ablauf wird zusätzlich im Kopf
  des Skripts als Kommentar festgehalten.
* **Keine neuen Übersetzungen.**

## 7. Abgrenzung / Out of Scope

* **Flyway-Migration für die Datenumstellung.** Bewusst nicht: Die Umstellung betrifft nur die
  Bestandsdaten von Hene; neue Installationen sind nicht betroffen (FR-5).
* **Eindeutigkeitsregel `UNIQUE (einheit_id, zeit)`.** Wünschenswert, folgt später als eigenes
  Thema. Voraussetzung ist, die acht lokalen Mut13-Duplikate (nächster Punkt) zu bereinigen. Die
  Kollisionsprüfung in FR-5 deckt den Fall der Umstellung ab.
* **Bilanz-CSV-Import und Zeitumstellung.** Der Import ignoriert die Zeitstempel in der Datei und
  zählt von `00:00` in Viertelstunden weiter. Bei Mut13 entstanden so Doppelzeilen an
  Monatsanfängen und am 29.03.2026 03:00–03:45. Eigenes Thema, vorbestehend, betrifft nur lokale
  Daten.
* **Doppelte Stunde bei der Winterzeit-Umstellung** (Ortszeit ohne Zone). Vorbestehend, siehe
  Abschnitt 5.
* **Korrektur bereits versandter Rechnungen.** Rechnungen werden nicht gespeichert, sondern bei
  Bedarf berechnet. Der User erzeugt die Rechnungen von Hene nach der Umstellung neu; an den
  Quartalsgrenzen können sie um eine Viertelstunde abweichen — wegen der Rundung auf ganze kWh
  meist um nichts, im Einzelfall um 1 kWh. Das Neu-Erzeugen **überschreibt** dabei die Beträge
  offener Debitoren (`debitor.betrag`, solange `zahldatum` leer ist; auf Hene 10 ZEV-Debitoren
  für Q3 2026, alle offen). Das ist gewollt (Entscheid 09.10.2026).
* **Preiszeitreihe** bleibt in UTC; ihre Umrechnung ist unabhängig von dieser Konvention.

## 8. Offene Fragen

Keine. Geklärt am 09.10.2026:

* Rechnungen werden neu erzeugt; offene Debitorbeträge dürfen sich dabei ändern.
* Die Eindeutigkeitsregel folgt später.
* Die Datenumstellung ist ein einmaliges Skript ausserhalb von Flyway, ohne Schutz gegen einen
  Lauf nach dem Start der neuen Version — der User hält die Reihenfolge aus NFR-3 ein.
* Datenlücke und Sammel-Intervall während der Ausfallzeit sind hinnehmbar.
