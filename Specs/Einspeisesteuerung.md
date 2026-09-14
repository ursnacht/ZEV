# Einspeisesteuerung

> **Diese Ausbaustufe schaltet nichts.** Sie rechnet die Steuerentscheide mit, schreibt sie
> auf und macht sie sichtbar — ein Trockenlauf. Der Schreibpfad zur Anlage ist ausdrücklich
> **nicht** Teil dieser Spec (§7). Erst wenn das Protokoll über Wochen plausibel aussieht, lohnt
> die Diskussion über das tatsächliche Schalten.

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Eine Steuerung entscheidet viertelstündlich über zwei Grössen —
  **darf die Batterie laden?** und **darf eingespiesen werden?** — auf Basis der dynamischen
  Einspeisepreise des laufenden Tages. Jeder Entscheid wird samt seinen Eingangsgrössen
  protokolliert, als Tagesdiagramm mit Zustandsbändern dargestellt und lässt sich über vergangene
  Tage mit verändertem Schwellwert **nachrechnen**.

* **Warum machen wir das:** Die Einspeisevergütung schwankt viertelstündlich und ist mittags
  regelmässig am tiefsten — an einzelnen Tagen sogar negativ. Eine Batterie kann diesen Unterschied
  nutzen, aber ihre Kapazität ist begrenzt: Wer sie morgens füllt, kann sie mittags nicht mehr
  füllen. Bevor eine solche Steuerung wirklich schaltet, muss nachvollziehbar sein, **was sie tun
  würde und warum** — und mit welchem Schwellwert sie sich richtig verhält. Beides lässt sich nur
  an Daten zeigen, nicht am Reissbrett.

* **Aktueller Stand:**
  - **Preise liegen vor:** `zev.preiszeitreihe` (`Specs/Preiszeitreihe.md`) sammelt seit dem
    27.08.2026 viertelstündliche Einspeisepreise, täglich um 02:00 abgerufen. Die Quelle liefert
    das laufende **und das kommende** Tarifintervall — der Mittagspreis ist am Morgen also bereits
    bekannt. Das ist die Voraussetzung, auf der die ganze Regel steht.
  - **Messwerte liegen vor:** `zev.messwerte` (`zeit`, `total`, `zev`, `einheit_id`, `org_id`,
    `quelle`) im selben 15-Minuten-Raster, über MQTT (`Specs/MQTT-Integration.md`) oder CSV.
    Produktion = Einheiten vom Typ `PRODUCER`, Verbrauch = `CONSUMER`.
    > **`total` ist vorzeichenbehaftet** (`ΔBezug − ΔEinspeisung`, `Specs/Batteriespeicher.md`):
    > `PRODUCER` und `RUECKLIEFERUNG` stehen **negativ** in der Tabelle, `CONSUMER` und `BEZUG`
    > positiv. In den Daten geprüft (01.–13.09.): 158 von 158 Producer-Werten negativ, alle
    > Consumer-Werte positiv. Wer die Produktion ohne Vorzeichenwechsel addiert, erhält einen
    > Überschuss von **immer 0** — die Steuerung liefe dann stumm ins Leere. Siehe FR-2.
  - **Die Batterie ist vorhanden, aber nicht erfasst:** Bei Hene steht ein **Pylontech-Speicher
    mit 20 kWh** an einem Wechselrichter **MHT-30K-100**. `Specs/Batteriespeicher.md` beschreibt
    den Einheiten-Typ `SPEICHER` mit Ladung/Entladung über den bestehenden MQTT-Pfad — in der
    Datenbank existiert aber **keine** Einheit dieses Typs (13 `CONSUMER`, 2 `PRODUCER`, je 2
    `BEZUG`, `RUECKLIEFERUNG`, `LADESTATION`). Die Batterie ist damit in den Daten unsichtbar; wie
    der Ladezustand aus dem Wechselrichter zu holen ist, wird noch geklärt (§8).
  - **Es gibt keinen Schreibpfad zur Anlage.** MQTT läuft ausschliesslich lesend; das Backend
    abonniert Messwerte und publiziert nichts.
  - **Kein Steuerungscode.** Geplante Jobs sind etabliert (`PreiszeitreiheDownloadJob`,
    `SystemmeldungCleanupJob`, `@EnableScheduling` aktiv), ECharts ist im Frontend im Einsatz
    (`preiszeitreihe-chart`, dynamisch nachgeladen).

### Was die vorhandenen Daten zeigen (Stand 11.09.2026, 9 vollständige Tage)

| | morgens 07–10 | mittags 11–15 |
|---|---|---|
| Spanne über 9 Tage | 0.086 – 0.203 | **−0.001 – 0.159** |
| Tage unter 0.05 mittags | — | **2 von 9** |

Mittags war der Preis an **allen neun** Tagen tiefer als morgens, im Mittel um 20–30 %. Am
30.08. lag er im Minus. Die Grundannahme trägt also; der Schwellwert ist die offene Grösse (§8).

### Der wirtschaftliche Massstab (entschieden)

**Ziel ist, den Netzbezug zu minimieren und den Einspeiseertrag zu maximieren.** Damit ist der
Massstab einer gespeicherten Kilowattstunde der **vermiedene Netzbezug** (`0.34936`), nicht der
ZEV-Tarif — abzüglich Lade- und Entladeverlusten rund **`0.31`**.

Daraus folgt eine Rangfolge, die man kennen muss, bevor man die Regeln liest: Der höchste je
gemessene Einspeisepreis liegt bei `0.238`, also **unter** `0.31`. **Speichern schlägt Einspeisen
damit immer** — solange die Preise in der bisherigen Spanne bleiben.

Das macht die Steuerung nicht überflüssig, sondern schärft sie: Die Frage ist nicht **ob**
gespeichert wird, sondern **wann**. Die Batterie fasst 20 kWh; wer sie morgens mit Strom zu `0.15`
füllt, muss mittags zu `0.02` einspeisen, weil kein Platz mehr ist. Umgekehrt herum verdient
dieselbe Anlage an denselben Kilowattstunden mehr. **Die knappe Ressource ist die Kapazität, nicht
die Gelegenheit** — und genau darauf zielt Regel 4.

Regel 3 („Einspeisen lohnt mehr") wird mit diesem Massstab **praktisch nie auslösen**. Sie bleibt
als Wächter für den Fall, dass Einspeisepreise über den Bezugspreis steigen — in
Knappheitssituationen kommt das vor. Wird der Massstab später auf den ZEV-Tarif umgestellt
(`speicherwert` = `0.18`), greift sie regelmässig; dafür ist der Wert konfigurierbar.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Ablauf / Flow

**Laufende Auswertung (Trockenlauf)**
1. Ein geplanter Job läuft **alle 15 Minuten, jeweils eine Minute nach der Aggregierung**
   (`0 6,21,36,51 * * * *`, über `application.yml` konfigurierbar).
   > **Der Zeitpunkt hängt an der Aggregierung, nicht am Intervallende.**
   > `ZaehlerAggregationService.aggregiere()` läuft um `:05/:20/:35/:50` und schreibt **erst dort**
   > die Messwerte des gerade abgeschlossenen Quartals. Ein Lauf um `:01` — die erste Fassung —
   > liest Daten, die es noch nicht gibt: Der Entscheid bezieht sich faktisch auf ein älteres
   > Intervall, und von aussen sieht es aus, als hinke die Steuerung um eine Viertelstunde nach.
   > Ändert sich der Takt der Aggregierung, ist dieser hier mitzuziehen.
2. Er ermittelt die Organisationen mit aktivem Feature-Flag `EINSPEISESTEUERUNG`. Ist es bei
   keiner aktiv, endet er ohne Arbeit (Log auf `debug`).
3. Je Organisation wertet er das **zuletzt abgeschlossene** 15-Minuten-Intervall aus: Er liest die
   Messwerte dieses Intervalls und die Preise des laufenden Tages, wendet die Regel (FR-2) an und
   schreibt **einen** Entscheid (FR-3).
4. Es wird **nichts geschaltet**. Der Entscheid ist eine Aussage darüber, was die Steuerung tun
   *würde*.

**Ansehen**
1. Der Benutzer öffnet **Einspeisesteuerung** (`/einspeisesteuerung`).
2. Die Seite zeigt den **heutigen Tag**: Preisverlauf, Produktion, Verbrauch und darunter die
   beiden Zustandsbänder (FR-5).
3. Über eine Datumswahl blättert er zu einem beliebigen früheren Tag.
4. Unter dem Diagramm steht das **Entscheidungsprotokoll** als Tabelle — je Intervall eine Zeile
   mit Eingangsgrössen, der ausgelösten Regel und den beiden Sollzuständen.

**Nachrechnen**
1. Auf derselben Seite gibt der Benutzer einen **abweichenden Schwellwert** ein und wählt einen
   Zeitraum (Standard: die vorhandene Historie).
2. Das System rechnet die Regel über diesen Zeitraum **neu** — ohne gespeicherte Entscheide zu
   verändern — und zeigt das Ergebnis (FR-6).
3. Der Benutzer verändert den Schwellwert und sieht sofort, wie sich Auslösungen und Ergebnis
   verschieben.

### FR-2: Die Regel

Zwei voneinander **unabhängige** Sollzustände je Intervall. Beide beziehen sich ausschliesslich
auf den **PV-Überschuss** dieses Intervalls — also auf die Energie, die weder verbraucht noch
anderweitig gebunden ist:

| Sollzustand | Wert | Bedeutung |
|---|---|---|
| `batterieladung` | `FREI` | Der Überschuss **darf** in die Batterie geladen werden. |
| | **`GESPERRT`** | Der Überschuss soll **nicht** in die Batterie; er geht stattdessen ins Netz. |
| `einspeisung` | `FREI` | Der Überschuss **darf** ins Netz eingespiesen werden. |
| | **`GESPERRT`** | Es soll **nicht** eingespiesen werden. |

`GESPERRT` bei der Batterieladung ist damit kein Abschalten, sondern eine **Umlenkung**: nicht
jetzt speichern, sondern einspeisen. In beiden Fällen, in denen die Sperre auftritt (Regeln 3 und
4), steht `einspeisung` folgerichtig auf `FREI`.

> **Was die Zustände ausdrücklich nicht sagen:**
> * Nichts über das **Entladen** der Batterie. Die Steuerung entscheidet nur über das Laden.
> * Nichts über den **Füllstand**. Die Batterie ist in den Daten nicht erfasst — es gibt keine
>   Einheit vom Typ `SPEICHER` und damit keinen Ladezustand (§1, §8). Die Regel entscheidet **ohne
>   Rückkopplung**: Sie weiss nicht, ob die Batterie längst voll ist. Ein `FREI` heisst deshalb
>   „von der Regel her erlaubt", nicht „es wird geladen".
> * Nichts über tatsächliche Schaltvorgänge. Diese Ausbaustufe ist ein Trockenlauf (§7).

Die Kennzahl `energie_verschoben` (FR-6) zählt genau die Überschuss-Kilowattstunden aus Intervallen
mit gesperrter Ladung — die Menge also, die die Regel vom Speicher weg ins Netz lenken würde.

Ausgewertet in dieser Reihenfolge; die erste zutreffende Regel bestimmt den Entscheid und wird als
`regel` protokolliert:

| # | Bedingung | `batterieladung` | `einspeisung` | Schlüssel |
|---|---|---|---|---|
| 1 | Preis **jetzt** < 0 | `FREI` | **`GESPERRT`** | `PREIS_NEGATIV` |
| 2 | kein PV-Überschuss (Produktion ≤ Verbrauch) | `FREI` | `FREI` | `KEIN_UEBERSCHUSS` |
| 3 | Preis **jetzt** ≥ Speicherwert | **`GESPERRT`** | `FREI` | `EINSPEISEN_LOHNT` |
| 4 | erwarteter Tiefstpreis **heute noch** < Schwellwert **und** < Preis **jetzt** | **`GESPERRT`** | `FREI` | `WARTEN_AUF_TAL` |
| 5 | sonst | `FREI` | `FREI` | `LADEN` |

**Regel 1 — negativer Preis.** Einspeisen kostet dann Geld. Die Batterie darf laden (sie nimmt
Energie auf, die sonst abgeregelt würde); eingespiesen wird nichts.

**Regel 2 — kein Überschuss.** Ohne Überschuss gibt es nichts zu entscheiden. Der Fall wird
trotzdem protokolliert: Eine Lücke im Protokoll liesse später offen, ob die Steuerung lief.

**Regel 3 — Einspeisen lohnt mehr.** Liegt die Vergütung über dem Wert, den eine gespeicherte
Kilowattstunde später bringt (`speicherwert`, Vorgabe **`0.31`** = vermiedener Netzbezug abzüglich
Verlusten), ist Einspeisen die bessere Verwendung. **Mit dieser Vorgabe löst die Regel praktisch
nie aus** (höchster gemessener Preis `0.238`); sie ist der Wächter für Knappheitspreise und für
einen später geänderten Massstab — siehe §1.

**Regel 4 — auf das Tal warten.** Das ist der Kern. Ist **heute noch** ein Intervall zu erwarten,
das **billiger als jetzt** ist und unter dem Schwellwert liegt, wird die knappe Batteriekapazität
dafür freigehalten, statt sie jetzt mit teurerem Strom zu füllen.

> **Beide Bedingungen sind nötig — die zweite fehlte zuerst.** Der Schwellwert sagt, ob sich das
> Warten überhaupt lohnt; der Vergleich mit dem aktuellen Preis sagt, ob es etwas gibt, worauf sich
> warten lässt. Ohne ihn sperrte die Regel weiter, sobald das Tal **erreicht** war: Am 14.09.2026
> stand bei Hene von 13:00 bis 14:30 der aktuelle Preis (0.161) gleich dem Tiefstpreis des
> Resttages — die Steuerung wartete auf sich selbst. 27.9 kWh Überschuss, mehr als die Batterie
> fasst, gingen im Preistal ins Netz; geladen wurde ab 14:45, als der Preis auf 0.170 gestiegen
> war. Die Regel bewirkte damit das Gegenteil ihrer Absicht.
>
> Der gefährliche Fall ist nicht die Preisdifferenz, sondern die **leere Batterie**: Liegt das
> Tagestief am späten Nachmittag und folgt kein Überschuss mehr, sperrt die Regel bis zum Tal und
> hat dann nichts mehr zu laden.
>
> **Ohne Preis wird nicht gesperrt** — ein Vergleich ohne die eine Seite ist keiner.

**Regel 5 — laden.** Kommt kein Tal mehr, wird geladen, sobald Überschuss da ist. Das deckt den
bewölkten Tag ab: Ein **hoher** Mittagspreis bedeutet, dass der ganze Markt wenig Solarstrom
erwartet — dann ist die Gelegenheit knapp, nicht die Kapazität, und jede Kilowattstunde gehört in
die Batterie.

> **Der Preis ist ein Wetterbericht in Franken — für den ganzen Markt, nicht für euer Dach.** Es
> kann bei euch sonnig sein, während Deutschland unter Wolken liegt. Die Regel wird an einzelnen
> Tagen danebenliegen; das ist keine Fehlfunktion, sondern die Grenze der Datenlage. Wer das
> Protokoll liest, soll es wissen.

**Definition „erwarteter Tiefstpreis heute noch":** das Minimum der Preise aller Intervalle des
laufenden Tages (Ortszeit Europe/Zurich), deren Beginn **nach** dem ausgewerteten Intervall liegt.
Bewusst nicht ein festes Mittagsfenster: Die Frage ist „kommt noch etwas Billigeres?", und die
Antwort darauf ist am Nachmittag eine andere als am Morgen. Liegen für den Rest des Tages **keine**
Preise vor, gilt Regel 4 als nicht erfüllt (§5).

**Zeitbezüge** — die Steuerung rechnet durchgehend in **Ortszeit**:

| Quelle | Zone | Bedeutung von `zeit` |
|---|---|---|
| `messwerte.zeit` | **Ortszeit** (die Aggregierung rechnet mit `LocalDateTime.now()`) | Intervall**ende** |
| `zaehler_rohdaten.zeit` | **Ortszeit** | Zeitpunkt der Messung |
| `steuerentscheid.zeit_von` | **Ortszeit** | Intervall**beginn** |
| `preiszeitreihe.zeit_von` | **UTC** | Intervall**beginn** |

> **Wer die Zonen gleichsetzt, erhält Zahlen, die plausibel aussehen und zu verschiedenen
> Zeitpunkten gehören:** bis zu zwei Stunden Zonenversatz plus eine Viertelstunde
> Anfang-gegen-Ende. Genau das ist in der ersten Umsetzung passiert — ein Entscheid trug den Preis
> von 11:45 Ortszeit neben der Produktion von 09:30–09:45. Nichts daran wirkte falsch.

**Deshalb gilt seit V147 eine Konvention statt zweier:** `steuerentscheid.zeit_von` liegt in
Ortszeit wie die beiden anderen Zeitreihen des Systems. Die Preiszeitreihe bleibt der einzige
Fremdkörper — sie wird verbatim von der Börse übernommen und gehört nicht der Steuerung. Umgerechnet
wird deshalb **nur noch der Preis**, an drei Stellen: `preisFuer`, `tiefstpreisRestDesTages` und —
einmalig beim Bündeln — `preiseJeOrtstag`. Dahinter ist alles Ortszeit.

> **Warum nicht umgekehrt alles nach UTC?** Weil `messwerte` und `zaehler_rohdaten` bereits
> Ortszeit führen und nicht zur Disposition stehen. Eine dritte Konvention für eine einzelne neue
> Tabelle hat mehr gekostet, als sie wert war.

**Überschuss** — und hier ist das Vorzeichen entscheidend:

```
Produktion  =  −Σ total(PRODUCER)      ← Vorzeichenwechsel, Producer stehen negativ
Verbrauch   =   Σ total(CONSUMER)
Überschuss  =  max(0, Produktion − Verbrauch)
```

> **Ohne den Vorzeichenwechsel ist der Überschuss immer 0** und die Steuerung läuft stumm ins
> Leere: Sie schreibt lauter Entscheide mit `KEIN_UEBERSCHUSS`, ohne dass je ein Fehler sichtbar
> würde. `total` ist `ΔBezug − ΔEinspeisung`; eine einspeisende Anlage hat deshalb ein negatives
> `total` (§1). Ein Test mit **negativen** Producer-Werten gehört zu dieser Anforderung.

`SPEICHER`, `BEZUG`, `RUECKLIEFERUNG` und `LADESTATION` zählen **nicht** mit: Sie messen nicht die
Erzeugung, sondern deren Folgen. Insbesondere `RUECKLIEFERUNG` wäre eine Doppelzählung — sie ist
das, was nach der Steuerung übrig bleibt, nicht ihre Eingangsgrösse.

**Konfiguration je Mandant** (Entscheid) — in `organisation.konfiguration` (`jsonb`), nicht in
`.env`:

```json
"steuerung": {
  "schwellwert": 0.05,
  "speicherwert": 0.31,
  "batteriekapazitaet": 20.0
}
```

| Feld | Vorgabe | Bedeutung |
|---|---|---|
| `schwellwert` | `0.05` | Schwellwert für Regel 4, CHF/kWh |
| `speicherwert` | `0.31` | Wert einer gespeicherten kWh, CHF/kWh (Regel 3) |
| `batteriekapazitaet` | — | Nutzbare Kapazität in kWh; **nur dokumentierend** in dieser Ausbaustufe (keine Regel wertet sie aus), aber Voraussetzung jeder späteren Ertragsrechnung |

**Warum je Mandant und nicht in `.env`:** Der Schwellwert hängt an der Anlage — an Batteriegrösse,
PV-Leistung und Verbrauchsprofil. Zwei Mandanten haben verschiedene Werte, und eine
Umgebungsvariable kann das nicht abbilden. Der Umweg über `.env` und eine spätere Migration wird
damit gespart.

* Fehlt der Block `steuerung` ganz, gelten die Vorgaben; der Job läuft, statt zu scheitern. Ein
  Mandant, der das Flag einschaltet, muss nicht zuerst konfigurieren.
* Beide Preisschwellen dürfen **negativ** sein; es gibt keinen Vorzeichen-Wächter (dieselbe
  Begründung wie bei `preiszeitreihe.preis`).
* Der **Takt des Jobs** bleibt in `application.yml` (`ZEV_STEUERUNG_CRON`, Vorgabe
  `0 1,16,31,46 * * * *`): Er betrifft den Betrieb der Anwendung, nicht die Anlage eines Mandanten.

### FR-3: Persistierung

Neue Tabelle `zev.steuerentscheid` (Flyway `V<nächste freie>__Create_Steuerentscheid.sql` — **die höchste vergebene Nummer vor dem Anlegen prüfen**; `Specs/Batteriespeicher.md` braucht ebenfalls eine Migration, und wer zuerst umsetzt, nimmt die nächste):

| Spalte | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `id` | `bigserial` | ja | Technischer Schlüssel |
| `org_id` | `bigint` | ja | Mandant — serverseitig gesetzt, nie aus dem Request |
| `zeit_von` | `timestamp` | ja | Beginn des ausgewerteten Intervalls, **Ortszeit** |
| `preis` | `numeric(10,5)` | **nein** | Einspeisepreis des Intervalls; leer, wenn kein Preis vorlag |
| `preis_tief_rest` | `numeric(10,5)` | **nein** | Erwarteter Tiefstpreis für den Rest des Tages |
| `produktion` | `numeric(12,3)` | ja | Summe der `PRODUCER` im Intervall, kWh |
| `verbrauch` | `numeric(12,3)` | ja | Summe der `CONSUMER` im Intervall, kWh |
| `ueberschuss` | `numeric(12,3)` | ja | `max(0, produktion − verbrauch)`, kWh |
| `regel` | `varchar(30)` | ja | Ausgelöste Regel (`PREIS_NEGATIV`, …, `LADEN`) |
| `batterieladung` | `varchar(10)` | ja | `FREI` \| `GESPERRT` |
| `einspeisung` | `varchar(10)` | ja | `FREI` \| `GESPERRT` |
| `schwellwert` | `numeric(10,5)` | ja | Der **beim Entscheid geltende** Schwellwert |
| `speicherwert` | `numeric(10,5)` | ja | Der **beim Entscheid geltende** Speicherwert |
| `erstellt_am` | `timestamp` | ja | Zeitpunkt des Schreibens (Default `now()`) |

* **Eindeutigkeit:** `UNIQUE (org_id, zeit_von)` — Grundlage des Upsert. Ein zweiter Lauf über
  dasselbe Intervall überschreibt den Entscheid, statt ihn zu verdoppeln.
* **`org_id` ist Pflicht**, mit `@Filter(orgFilter)` und `hibernateFilterService.enableOrgFilter()`
  in jeder Service-Methode.
  > **Im Job die parametrisierte Variante `enableOrgFilter(orgId)`.** Die parameterlose zieht die
  > `orgId` aus dem Sicherheitskontext, den ein geplanter Job nicht hat — sie liefe dort ins Leere.
  > Die Organisationen kommen aus `featureFlagService.getOrgIdsMitAktivemFlag(...)`; dasselbe
  > Muster verwendet `PreiszeitreiheDownloadJob`. In den Endpunkten bleibt es bei der
  > parameterlosen Variante, dort gibt es einen Benutzer. Anders als die Preiszeitreihe (mandantenübergreifend gültige
  Marktdaten) sind Entscheide **anlagenspezifisch**: Sie hängen an Produktion und Verbrauch einer
  bestimmten Liegenschaft.
* **Beide Schwellen werden mitgeschrieben.** Ohne sie wäre ein alter Entscheid nach einer
  Änderung der Konfiguration nicht mehr erklärbar — man sähe die Wirkung und wüsste die Ursache
  nicht. Da die Werte jetzt je Mandant in der Datenbank stehen und über die Maske änderbar sind,
  wiegt das schwerer als bei einer Umgebungsvariablen, die selten angefasst wird.
* **Zeitzone: Ortszeit** (Europe/Zurich, seit V147) — wie `zev.messwerte.zeit` und
  `zev.zaehler_rohdaten.zeit`. Eine Umrechnung für die Darstellung entfällt damit.
  > **Der Preis dieser Wahl, ausdrücklich benannt:** An der Umstellung auf Winterzeit (nächstes Mal
  > **25.10.2026**) gibt es die Stunde 02:00–03:00 zweimal. Beide Durchgänge tragen denselben
  > `zeit_von`; der Upsert auf `(org_id, zeit_von)` überschreibt daher die vier Entscheide des
  > ersten. Der Tag hat 96 statt 100 Entscheide.
  >
  > Das ist hingenommen, weil es fachlich folgenlos ist — nachts um 2 Uhr gibt es keinen
  > Solarüberschuss, und die Steuerung schaltet ohnehin nichts. `zev.messwerte` kann dieselben vier
  > Intervalle ohnehin nicht abbilden, auf **beiden** Eingangswegen:
  > * **CSV** (`MesswerteService`) zählt die Zeit ab Mitternacht in 96 Schritten hoch; die Datei
  >   enthält gar keine Zeitstempel. Am 26.10.2025 stehen dort folgerichtig 96 statt 100 Messwerte.
  > * **MQTT** (`ZaehlerAggregationService.upsertMesswert`) sucht per `findByEinheitAndZeit` und
  >   überschreibt den gefundenen Wert — der zweite Durchgang verdrängt den ersten.
  >
  > Anders zu verfahren als die beiden bestehenden Zeitreihen hätte die Steuerung zur Ausnahme
  > gemacht, ohne den Verlust irgendwo zu verhindern.
  >
  > **Vor jeder Auswertung in SQL:** `zeit_von` ist `timestamp without time zone` und enthält
  > **Ortszeit** — also direkt lesbar, ohne `AT TIME ZONE`. Wer hier umrechnet, verschiebt den Wert
  > um zwei Stunden. (Für `preiszeitreihe.zeit_von` gilt weiterhin das Gegenteil: dort ist
  > `zeit_von AT TIME ZONE 'UTC' AT TIME ZONE 'Europe/Zurich'` nötig.)
* **Keine Retention in dieser Ausbaustufe:** 35'040 Zeilen je Mandant und Jahr sind für PostgreSQL
  vernachlässigbar. Entscheide sind Betriebsdaten, keine Personendaten.

### FR-4: REST-Endpunkte

`SteuerungController`, `@RequestMapping("/api/einspeisesteuerung")`,
`@PreAuthorize("hasAuthority('tarife:manage')")` auf Klassenebene:

| Methode | Pfad | Zweck | Antwort |
|---|---|---|---|
| `GET` | `/entscheide?datum=` | Entscheide **eines Tages** samt Preis, Produktion, Verbrauch | `List<SteuerentscheidDTO>` |
| `GET` | `/entscheide/simuliert?datum=&schwellwert=&speicherwert=` | derselbe Tag, **nachgerechnet** mit abweichenden Schwellen (FR-6a) | `List<SteuerentscheidDTO>` |
| `POST` | `/simulation` | Nachrechnen mit abweichendem Schwellwert | `SimulationDTO` |

* Beide Endpunkte prüfen das Feature-Flag und antworten bei deaktiviertem Flag mit `403`.
* `GET` ohne Treffer liefert `200` und eine leere Liste (kein `404`).
* `datum` ist ein Datum in **Europe/Zurich** und wird direkt auf `00:00`–`24:00` desselben Tages
  abgebildet — keine Umrechnung mehr nötig, da die Entscheide in Ortszeit liegen. Am Umstellungstag
  im Herbst ergibt das **96** statt 100 Entscheide (siehe FR-3 zur doppelten Stunde), im Frühling
  **92**.
* `POST /simulation` nimmt `von`, `bis`, `schwellwert` und optional `speicherwert`. Der Zeitraum
  ist auf **366 Tage** begrenzt (`400` darüber), `von` nach `bis` ergibt `400` mit lesbarem Text.
* Fehlerrümpfe immer **Klartext**, kein Objekt — ein Objekt erscheint in der Maske als
  `[object Object]`.

### FR-5: Layout — Tagesansicht

**Platzierung:** Eigene Seite `/einspeisesteuerung`, eigener Menüeintrag **Einspeisesteuerung**
(Icon `activity`), sichtbar nur mit aktivem Flag **und** Permission (`*appFeature` + `*appPermission`,
wie der NK-Eintrag).

**Aufbau von oben nach unten:**
1. Titel **Einspeisesteuerung** mit Icon.
2. **Eine Steuerzeile** (`zev-date-range-row`, wie bei der Preiszeitreihe): Datumswahl,
   **‹ / ›** zum Blättern um je einen Tag, Feld **Schwellwert** und Schaltfläche **Nachrechnen**.
3. **Diagramm** in `zev-panel--chart` (ECharts, dynamisch nachgeladen wie
   `preiszeitreihe-chart`):
   * **Stufenlinie** Einspeisepreis (CHF/kWh, linke y-Achse) — `step: 'end'`, ohne Flächenfüllung:
     Ein Preis gilt für die ganze Viertelstunde.
   * **Flächen** Produktion und Verbrauch (kWh, rechte y-Achse).
   * **Zwei Zustandsbänder** unter der x-Achse, je eines für `batterieladung` und `einspeisung`:
     ein durchgehender Balken über die Zeitachse, eingefärbt nach Zustand. Sie sind der Kern der
     ganzen Ansicht — auf einen Blick liest man „ab 09:15 Ladung gesperrt, ab 11:30 frei,
     12:00–13:15 Einspeisung gesperrt".
     > **Umgesetzt als `markArea`, nicht als Balkenserie.** Je Block gesperrter Intervalle ein
     > Rechteck von `zeit` bis `zeit + 15min`, auf einer festen Ebene unterhalb der Nulllinie.
     >
     > **Balken taugen dafür nicht** — das war die erste Umsetzung und sie zeigte falsche Zeiten:
     > * Auf einer Zeitachse **zentriert** ECharts den Balken auf seinen Datenpunkt. Das Band lag
     >   damit 7½ Minuten zu früh, während die Preislinie (`step: 'end'`) das Intervall korrekt
     >   ab seinem Beginn abdeckte.
     > * Mehrere Balkenserien ordnet ECharts **nebeneinander** an, nicht übereinander. Die beiden
     >   Bänder beschrieben dasselbe Intervall, wurden aber eine **Viertelstunde** auseinander
     >   gezeichnet — aufgefallen ist es nur, weil beide Zustände einmal gleichzeitig gesperrt
     >   waren.
     >
     > **`MarkAreaComponent` ist in `ladeECharts()` zu registrieren.** Der Einwand gegen das
     > zusätzliche Modul hat sich in der Messung nicht bestätigt: `echarts/components` wird als
     > **ganzer** Chunk nachgeladen (642 kB, vorher wie nachher identisch) — registriert wird nur,
     > was davon benutzt wird. Das Modul kostet null Bytes.
     >
     > **Ein nicht registriertes Modul zeichnet stumm nichts**; ECharts meldet es nicht als Fehler.
     > Man sähe ein Diagramm ohne Bänder und suchte den Fehler in den Daten. Deshalb prüft
     > `echarts-loader.spec.ts` die Existenz jedes registrierten Exports.
     >
     > **Die Mengen-Achse braucht ein festes `min`.** Anders als eine Datenserie spannt `markArea`
     > die Skala **nicht** auf: Ohne `min` endete die Achse bei 0 und die Bänder wären unsichtbar.
   * Farben aus den Design-Tokens, **nicht** hart kodiert (`Specs/DarkMode.md`).
   * Tooltip mit Zeitpunkt (`dd.MM.yyyy HH:mm`), Preis und beiden Zuständen; Zahlen über
     `formatSwissNumber()`, **kein** `toLocaleString()`.
4. **Entscheidungsprotokoll** als `zev-table` unterhalb des Diagramms: Zeit, Preis, erwarteter
   Tiefstpreis, Produktion, Verbrauch, Überschuss, Regel, Batterieladung, Einspeisung. Beträge und
   Mengen rechtsbündig (`zev-table__number`).
   * Die Spalte **Regel** zeigt den übersetzten Klartext, nicht den Schlüssel.
   * Leerer Tag: Hinweis `STEUERUNG_KEINE_ENTSCHEIDE` statt einer leeren Tabelle.

### FR-6: Nachrechnen über die Historie

`POST /simulation` rechnet die Regel über den gewählten Zeitraum neu — aus `zev.preiszeitreihe` und
`zev.messwerte`, **ohne** gespeicherte Entscheide zu lesen oder zu verändern.

Das Ergebnis nennt je Schwellwert:

| Grösse | Bedeutung |
|---|---|
| `tage` | ausgewertete Tage |
| `intervalle` | ausgewertete Intervalle |
| `je Regel` | wie oft jede Regel ausgelöst hat — **nur über Intervalle mit Überschuss** (Entscheid) |
| `stunden_ladung_gesperrt` | Dauer, in der die Ladung gesperrt gewesen wäre |
| `stunden_einspeisung_gesperrt` | dito für die Einspeisung |
| `energie_verschoben` | Überschuss-kWh in Intervallen mit gesperrter Ladung — die Energie, die die Regel vom Speicher weg in die Einspeisung lenkt |

> **Warum nur Intervalle mit Überschuss gezählt werden:** Regel 1 (`PREIS_NEGATIV`) wird vor der
> Überschussprüfung ausgewertet und greift deshalb auch nachts — in jeder Viertelstunde mit
> negativem Preis, obwohl gar nichts einzuspeisen ist. Das Verhalten ist folgenlos und bleibt so
> (die Sperre ist dann wirkungslos), aber in einer Zählung, mit der ein Schwellwert kalibriert
> werden soll, wäre es irreführend: `PREIS_NEGATIV` stünde vielfach über den Fällen, in denen die
> Steuerung tatsächlich etwas entschieden hat. **Die Tagesansicht (FR-5) zeigt weiterhin alle
> Intervalle** — dort ist die Vollständigkeit der Zweck, hier die Aussagekraft.

**Bewusst keine Ertragsrechnung in dieser Ausbaustufe.** Ein belastbarer Vergleich „mit Regel gegen
ohne Regel" in Franken bräuchte ein Batteriemodell (Kapazität, Wirkungsgrad, Ladeleistung) **und**
gemessene Lade-/Entladedaten — und beides fehlt heute (§1, §8). Eine Zahl in Franken, die auf
geratener Kapazität beruht, sähe belastbarer aus als sie ist. Die oben genannten Grössen genügen,
um den Schwellwert einzugrenzen: Sie zeigen, **wie oft** und **wie lange** die Regel greift und
**wie viel Energie** sie bewegt.

### FR-6a: Der angezeigte Tag wird mitgerechnet

Die Kennzahlen aus FR-6 sagen, **wie oft** ein Schwellwert gesperrt hätte — nicht **wann**. Genau
das ist aber die Frage, an der man einen Schwellwert beurteilt: Trifft er das Preistal, oder sperrt
er morgens um 8?

Deshalb wird beim Nachrechnen **auch der gerade angezeigte Tag** mit demselben Schwellwert
gerechnet und im Diagramm samt Tabelle gezeigt. Die Zustandsbänder (FR-5) zeigen dann, wann
Batterieladung und Einspeisung gesperrt **wären**.

* **Es wird nichts gespeichert.** Die Aufzeichnung bleibt unberührt; gerechnet wird aus Preisen und
  Messwerten, wie in FR-6.
* **Derselbe Rechenweg wie die Kennzahlen.** Tagesansicht und Zählung dürfen sich nicht
  widersprechen — beide gehen durch dieselbe Methode (`SteuerungService.rechneNach`).
* **Anders als die Zählung enthält die Tagesansicht alle Intervalle**, auch die ohne Überschuss:
  Sie zeichnet einen Verlauf, und eine Lücke darin wäre irreführend (FR-5).
* **Der Zustand bleibt beim Blättern erhalten.** Wer nach dem Nachrechnen einen Tag zurückgeht,
  sieht auch diesen nachgerechnet. Sonst fiele die Ansicht unbemerkt auf die Aufzeichnung zurück
  und widerspräche der Auswertung darunter.
* **Die Ansicht ist gekennzeichnet.** Ein stehender Hinweis nennt den erprobten Schwellwert und
  sagt, dass nichts gespeichert wird — Diagramm und Tabelle sehen sonst genauso aus wie beim
  Protokoll und wären nicht zu unterscheiden.
* **Ein Weg zurück:** Die Schaltfläche „Aufzeichnung zeigen" holt die gespeicherten Entscheide
  zurück. Die Kennzahlen bleiben dabei stehen — sie beziehen sich auf die ganze Historie, nicht auf
  den angezeigten Tag.

> **Eigener Endpunkt statt eines optionalen Parameters auf `/entscheide`:** Beide liefern dieselbe
> Form, meinen aber Verschiedenes — Protokoll gegen Hypothese. Wer den Parameter übersieht, hielte
> eine Rechnung für eine Aufzeichnung.
>
> **Und nicht als Teil der Antwort von `/simulation`:** Beim Blättern müsste sonst die ganze
> Rückrechnung über bis zu 366 Tage erneut laufen, um einen einzelnen Tag zu zeigen.

### FR-7: Einstellungen je Mandant

Die drei Werte aus FR-2 werden in der bestehenden Maske **Einstellungen** gepflegt
(`Specs/Einstellungen.md`), in einem eigenen Abschnitt **Einspeisesteuerung**:

| Feld | Eingabe | Validierung |
|---|---|---|
| Schwellwert | Zahl, CHF/kWh | 5 Nachkommastellen; **negativ erlaubt** |
| Speicherwert | Zahl, CHF/kWh | 5 Nachkommastellen; **negativ erlaubt** |
| Batteriekapazität | Zahl, kWh | ≥ 0; leer erlaubt |

* Der Abschnitt erscheint **nur bei aktivem Feature-Flag** — sonst stünden Felder da, die nichts
  bewirken.
* Gespeichert wird in `organisation.konfiguration` unter dem Schlüssel `steuerung`. **Keine
  Schema-Migration** — die Spalte ist `jsonb`.

  > **`steuerung` MUSS als Feld in `RechnungKonfigurationDTO` stehen — sonst geht es verloren.**
  > `EinstellungenService.toJson()` schreibt mit `objectMapper.writeValueAsString(dto)` die
  > **gesamte** Spalte neu, aus einem DTO, das nur kennt, was es als Feld hat. Ein unbekannter
  > Block überlebt das erste Speichern der Rechnungsdaten nicht: Die Werte wären weg, der Job liefe
  > still mit den Vorgaben weiter, und im Protokoll stünde ein plausibel aussehender Schwellwert
  > von `0.05`.
  >
  > Das abbildende DTO ist **`RechnungKonfigurationDTO`** (dort steht bereits `verteilmodus`),
  > nicht `EinstellungenDTO` — jenes trägt nur `id` und `rechnung`. Der Name des DTO passt schon
  > heute nicht mehr zu seinem Inhalt; das ist hinzunehmen, aber beim Lesen zu wissen.
  >
  > **Pflichttest:** Rechnungsdaten speichern und danach prüfen, dass die Steuerungswerte noch
  > dastehen. Ohne ihn fällt der Verlust erst im Betrieb auf — und dann sieht es aus wie ein
  > Steuerungsfehler, nicht wie ein Speicherfehler.
* Der Job liest die Werte **bei jedem Lauf** neu. Eine Änderung wirkt ab dem nächsten Intervall,
  ohne Neustart.
* Bestehende Organisationen haben den Schlüssel nicht; es gelten die Vorgaben aus FR-2. Das
  Speichern der Einstellungen legt ihn an.

### FR-8: Feature-Flag `EINSPEISESTEUERUNG`

* Neuer Wert im Enum `FeatureFlag`, **Vorgabe `false`** — wie `PREISZEITREIHE` und
  `NEBENKOSTENABRECHNUNG`.
* Der Job prüft ihn je Organisation, beide Endpunkte prüfen ihn und werfen sonst
  `FeatureDisabledException` → **403**; Menüeintrag und Seite hängen im Frontend daran.
* Die Prüfung im Service ist die tragende: Der Endpunkt ist über jeden HTTP-Client erreichbar.
  Die ArchUnit-Regel `nebenkostenServicesMustCheckFeatureFlag` ist das Muster.

### FR-9: Übersetzungen

Neue Schlüssel (Flyway `V<nächste freie>__Add_Einspeisesteuerung_Translations.sql`,
`ON CONFLICT (key) DO NOTHING`), deutsch **mit Umlauten**:

| Schlüssel | Deutsch | Englisch |
|---|---|---|
| `EINSPEISESTEUERUNG` | Einspeisesteuerung | Feed-in control |
| `STEUERUNG_SCHWELLWERT` | Schwellwert | Threshold |
| `STEUERUNG_NACHRECHNEN` | Nachrechnen | Recalculate |
| `STEUERUNG_SIMULIERTE_ANSICHT` | Nachgerechnete Ansicht — keine Aufzeichnung. Es wird nichts gespeichert. Schwellwert: | Recalculated view — not a recording. Nothing is stored. Threshold: |
| `STEUERUNG_AUFZEICHNUNG_ZEIGEN` | Aufzeichnung zeigen | Show recording |
| `STEUERUNG_BATTERIELADUNG` | Batterieladung | Battery charging |
| `STEUERUNG_EINSPEISUNG` | Einspeisung | Feed-in |
| `STEUERUNG_FREI` | Frei | Enabled |
| `STEUERUNG_GESPERRT` | Gesperrt | Blocked |
| `STEUERUNG_UEBERSCHUSS` | Überschuss | Surplus |
| `STEUERUNG_TIEFSTPREIS_REST` | Tiefstpreis heute noch | Lowest price remaining today |
| `STEUERUNG_REGEL` | Regel | Rule |
| `STEUERUNG_REGEL_PREIS_NEGATIV` | Preis negativ — nicht einspeisen | Negative price — no feed-in |
| `STEUERUNG_REGEL_KEIN_UEBERSCHUSS` | Kein Überschuss | No surplus |
| `STEUERUNG_REGEL_EINSPEISEN_LOHNT` | Einspeisen lohnt mehr als speichern | Feed-in beats storage |
| `STEUERUNG_REGEL_WARTEN_AUF_TAL` | Auf günstigeres Intervall warten | Waiting for a cheaper interval |
| `STEUERUNG_REGEL_LADEN` | Laden | Charge |
| `STEUERUNG_KEINE_ENTSCHEIDE` | Für diesen Tag liegen keine Entscheide vor | No decisions for this day |
| `STEUERUNG_ENERGIE_VERSCHOBEN` | Verschobene Energie | Energy redirected |
| `STEUERUNG_SPEICHERWERT` | Wert einer gespeicherten kWh | Value of a stored kWh |
| `STEUERUNG_BATTERIEKAPAZITAET` | Batteriekapazität | Battery capacity |
| `FEATURE_FLAG_EINSPEISESTEUERUNG` | Einspeisesteuerung (Trockenlauf) | Feed-in control (dry run) |

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

**Regel**
* [ ] Bei negativem Preis ist `einspeisung = GESPERRT` und `batterieladung = FREI`.
* [ ] Ohne Überschuss (Produktion ≤ Verbrauch) sind **beide** Zustände `FREI`, und der Entscheid wird trotzdem geschrieben.
* [ ] Liegt der Preis über dem Speicherwert, ist `batterieladung = GESPERRT` und `einspeisung = FREI`.
* [ ] Ist für den Rest des Tages ein Preis unter dem Schwellwert zu erwarten, ist `batterieladung = GESPERRT`.
* [ ] Ist kein solcher Preis mehr zu erwarten, ist `batterieladung = FREI` — auch bei hohem aktuellem Preis unterhalb des Speicherwerts.
* [ ] Die Regeln werden in der Reihenfolge 1–5 geprüft; die erste zutreffende bestimmt den Entscheid und steht in `regel`.
* [ ] Der Tiefstpreis berücksichtigt **nur** Intervalle, die **nach** dem ausgewerteten liegen, und nur solche des **gleichen Ortstages**.
* [ ] Der Überschuss zählt ausschliesslich `PRODUCER` und `CONSUMER`.
* [ ] **Der Überschuss wird aus negativen Producer-Werten korrekt gebildet:** Bei `total = −10` (Producer) und `total = 4` (Consumer) ergibt sich ein Überschuss von **6**, nicht 0 und nicht −14.
* [ ] Ein Testfall mit realistischen Vorzeichen (Producer negativ, Consumer positiv) ist vorhanden.

**Persistierung**
* [ ] Ein zweiter Lauf über dasselbe Intervall erzeugt **keinen** zweiten Datensatz, sondern überschreibt (Upsert auf `org_id, zeit_von`).
* [ ] Jeder Entscheid trägt den beim Entscheid geltenden Schwellwert.
* [ ] `zeit_von` ist in **Ortszeit** gespeichert und wird von der Tagesansicht unverändert angezeigt.
* [ ] Ein SQL-`SELECT` auf `steuerentscheid.zeit_von` liefert ohne `AT TIME ZONE` die Ortszeit, die auch in der Maske steht.
* [ ] Am Tag der Zeitumstellung liefert die Tagesansicht 92 bzw. 100 Intervalle, ohne dass ein Schlüssel kollidiert.
* [ ] Entscheide eines Mandanten sind für einen anderen Mandanten nicht abrufbar.

**Ansicht**
* [ ] Die Seite zeigt beim Öffnen den heutigen Tag.
* [ ] Das Diagramm zeigt Preis, Produktion, Verbrauch und **zwei** Zustandsbänder.
* [ ] Ein Wechsel des Zustands ist im Band an der richtigen Viertelstunde sichtbar.
* [ ] **Beide Bänder decken dieselbe Zeitspanne deckungsgleich ab**, wenn beide Zustände im selben Intervall `GESPERRT` sind — kein horizontaler Versatz zwischen ihnen.
* [ ] Ein Band beginnt am **Beginn** seines ersten Intervalls und endet am **Ende** seines letzten (`zeit + 15min`), deckungsgleich mit dem Stufenverlauf der Preislinie.
* [ ] Jedes Band liegt auf seiner **festen Ebene**, unabhängig davon, ob das andere gesetzt ist.
* [ ] Eine Lücke in den Entscheiden unterbricht das Band, statt überbrückt zu werden.
* [ ] Die Protokolltabelle nennt je Intervall die Regel im Klartext, nicht den Schlüssel.
* [ ] Ein Tag ohne Entscheide zeigt den Hinweis statt einer leeren Tabelle.
* [ ] Beträge erscheinen im Schweizer Format (`0.05`, `1'234.50`), unabhängig von der Browser-Locale.

**Nachrechnen**
* [ ] Ein abweichender Schwellwert verändert die gespeicherten Entscheide **nicht**.
* [ ] Ein tieferer Schwellwert führt zu **weniger** Intervallen mit `WARTEN_AUF_TAL` (monoton).
* [ ] **Steht der aktuelle Preis bereits auf dem Tiefstpreis des Resttages, wird `LADEN` entschieden, nicht `WARTEN_AUF_TAL`** — es gibt nichts, worauf sich warten liesse.
* [ ] Liegt der Tiefstpreis des Resttages **über** dem aktuellen Preis, wird geladen, auch wenn er unter dem Schwellwert liegt.
* [ ] Fehlt der aktuelle Preis, greift Regel 4 **nicht**.
* [ ] Das Ergebnis nennt Tage, Intervalle, Auslösungen je Regel und die verschobene Energie.
* [ ] Ein Zeitraum über 366 Tage wird mit `400` abgewiesen.
* [ ] **Nach dem Nachrechnen zeigen Diagramm und Tabelle denselben Tag mit dem erprobten Schwellwert** — die Zustandsbänder sagen, *wann* gesperrt worden wäre.
* [ ] Die nachgerechnete Ansicht ist als solche **gekennzeichnet** und nennt den erprobten Schwellwert.
* [ ] Ein Tageswechsel (Datumsfeld oder ‹ / ›) behält die nachgerechnete Ansicht bei.
* [ ] „Aufzeichnung zeigen" holt die gespeicherten Entscheide zurück; die Kennzahlen bleiben stehen.
* [ ] Tagesansicht und Kennzahlen widersprechen sich nicht: Beide entstehen aus **einem** Rechenweg.
* [ ] Die nachgerechnete Tagesansicht enthält auch Intervalle **ohne** Überschuss — anders als die Zählung.

**Verhalten ohne Daten** (aus §5)
* [ ] Fehlt der Preis für das ausgewertete Intervall, wird der Entscheid trotzdem geschrieben, `preis` bleibt leer, und **keine** Sperre wird gesetzt.
* [ ] Liegen für den Rest des Tages keine Preise vor, bleibt `preis_tief_rest` leer und Regel 4 greift **nicht**.
* [ ] Fehlen die Messwerte des Intervalls, wird der Entscheid mit Produktion, Verbrauch und Überschuss `0` geschrieben — die Lücke ist damit sichtbar statt unsichtbar.
* [ ] Ein Datum in der Zukunft liefert eine leere Liste mit dem Hinweis, keinen Fehler.
* [ ] Wird das Flag mitten am Tag eingeschaltet, beginnen die Entscheide beim nächsten Lauf; die Ansicht zeigt den unvollständigen Tag, ohne Vollständigkeit zu behaupten.

**Konfiguration**
* [ ] Schwellwert, Speicherwert und Batteriekapazität sind in den Einstellungen je Mandant pflegbar.
* [ ] Fehlt der Block `steuerung` in `organisation.konfiguration`, gelten die Vorgaben und der Job läuft trotzdem.
* [ ] Eine Änderung wirkt **ab dem nächsten Intervall**, ohne Neustart der Anwendung.
* [ ] Zwei Mandanten mit verschiedenen Schwellwerten erhalten für dasselbe Intervall verschiedene Entscheide.
* [ ] Jeder Entscheid trägt den zum Zeitpunkt geltenden Schwellwert **und** Speicherwert.
* [ ] Der Abschnitt in den Einstellungen erscheint nur bei aktivem Feature-Flag.
* [ ] Der Job läuft eine Minute **nach der Aggregierung** (`0 6,21,36,51 * * * *`), nicht nach dem Intervallende.
* [ ] Der ausgewertete Zeitraum ist das Intervall, das die Aggregierung soeben geschrieben hat — um 12:06 also 11:45–12:00.
* [ ] Die Messwerte werden über das Intervall**ende** gesucht, der Entscheid trägt den Intervall**beginn** — beide in Ortszeit, ohne Zonenrechnung.
* [ ] Der **Preis** ist die einzige Grösse, die umgerechnet wird (`PreiszeitreiheZeit.nachUtc`); im Job an zwei, in der Rückrechnung an einer Stelle.
* [ ] Preis und Messwerte eines Entscheids gehören zum **selben** Zeitpunkt — prüfbar, indem ein Entscheid gegen `messwerte` und `preiszeitreihe` gegengerechnet wird.

**Sicherheit und Flag**
* [ ] Ohne `tarife:manage` sind beide Endpunkte nicht aufrufbar (403), auch bei aktivem Flag.
* [ ] Bei ausgeschaltetem Flag antworten beide Endpunkte mit `403`, und der Menüeintrag fehlt.
* [ ] Der Job führt ohne aktiven Flag keine Abfrage aus.
* [ ] Es wird **nichts** geschaltet: Es existiert kein MQTT-Publish und kein anderer Schreibpfad zur Anlage.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Ein Job-Lauf wertet **ein** Intervall je Organisation aus und liest dafür höchstens 96 Preise und
  die Messwerte eines Intervalls — unter 200 ms je Organisation.
* Die Tagesansicht lädt höchstens 100 Entscheide; das Diagramm zeichnet ohne spürbare Verzögerung.
* ECharts wird **dynamisch nachgeladen** (`await import(...)`), damit die Bibliothek nicht ins
  Initial-Bundle gerät — dieselbe Begründung wie bei `preiszeitreihe-chart`.
* Das Nachrechnen über 366 Tage liest höchstens 35'000 Preise und die zugehörigen Messwerte; es
  läuft synchron und soll unter 5 Sekunden bleiben. Darüber ist die Zeitraumgrenze zu senken.

### NFR-2: Sicherheit
* Beide Endpunkte: `@PreAuthorize("hasAuthority('tarife:manage')")` — **`org_admin` und
  `zev_admin`**, nicht `zev_user`. Begründung: Die Ansicht zeigt und verändert die
  Bewirtschaftungslogik der Anlage, nicht Verbrauchsdaten eines Mieters. Sie teilt ihre
  Datengrundlage mit der Preiszeitreihe, die dieselbe Permission verwendet (§8 nennt die
  Alternative).
* `org_id` wird **serverseitig** aus dem Organisationskontext gesetzt, nie aus dem Request
  übernommen.
* Die Schwellwerte stehen in `organisation.konfiguration` und sind damit **je Mandant getrennt**;
  ein Mandant kann die Werte eines anderen weder lesen noch ändern. Das Speichern läuft über den
  bestehenden Weg der Einstellungen und dessen Permission (`einstellungen:write`).
* Das Nachrechnen ist rein lesend und kann von einem Benutzer nicht dazu gebracht werden, gespeicherte Entscheide zu verändern.

### NFR-3: Kompatibilität
* Neue Tabelle, neue Endpunkte, neues Flag — **keine** Änderung an bestehenden Tabellen.
  Bestehende Funktionen bleiben unberührt.
* Bei ausgeschaltetem Flag verhält sich die Anwendung exakt wie heute; kein zusätzlicher Job-Lauf,
  keine zusätzliche Abfrage.
* Die Tabelle lässt sich rückstandslos löschen: Kein anderer Datensatz verweist auf sie.
* `zev.messwerte` und `zev.preiszeitreihe` werden **nur gelesen**.

### NFR-4: Nachvollziehbarkeit im Log

Der Job protokolliert auf `INFO` nach demselben Muster wie `ZaehlerAggregationService.aggregiere()`
— Start, ein Eintrag je bearbeitetem Mandant, Abschluss mit Anzahl:

| Ereignis | Stufe | Inhalt |
|---|---|---|
| Lauf beginnt | `INFO` | `Steuerung start` |
| je Mandant | `INFO` | Org, Intervall in Ortszeit |
| je Entscheid | `INFO` | Preis, Tiefstpreis, Produktion, Verbrauch, Überschuss, Regel, beide Zustände |
| keine Messwerte | `WARN` | Intervall in Ortszeit — die Lücke wird gemeldet, nicht nur als `0` abgebildet |
| kein Preis | `WARN` | Intervall, mit dem Hinweis, dass die Regeln 1 und 3 nicht greifen |
| Lauf je Mandant fehlgeschlagen | `ERROR` | Org, Intervall, Meldung samt Stacktrace |
| Abschluss | `INFO` | Anzahl erzeugter Entscheide |

> **Warum die Eingangsgrössen im Log stehen:** Ein Entscheid ohne die Zahlen, aus denen er
> entstand, lässt sich im Nachhinein nicht prüfen — und genau diese Prüfung war nötig, um den
> Zeitversatz zu finden (§2, FR-1). Seit alle Zeitangaben in Ortszeit stehen, genügt **ein**
> Zeitbezug im Log; vorher mussten UTC und Ortszeit nebeneinander stehen, um einen Versatz
> überhaupt sichtbar zu machen.

> **Warum `INFO` und nicht `DEBUG`:** Bei 96 Läufen am Tag und einem Eintrag je Mandant bleibt das
> Volumen weit unter dem der Aggregierung, die je Einheit **und** Intervall auf `INFO` schreibt. Auf
> `DEBUG` wäre im Betrieb nicht zu sehen, ob die Steuerung überhaupt läuft.

## 5. Edge Cases & Fehlerbehandlung

| Fall | Verhalten |
|---|---|
| **Keine Preise für das Intervall** | Entscheid wird trotzdem geschrieben, `preis` bleibt leer, Regel `KEIN_UEBERSCHUSS` bzw. `LADEN` je nach Überschuss. Ohne Preis darf die Steuerung nicht sperren — Nichtstun ist der sichere Zustand. |
| **Keine Preise für den Rest des Tages** | Regel 4 gilt als **nicht** erfüllt (`preis_tief_rest` leer). Ein fehlender Blick nach vorne ist kein Grund zu warten. |
| **Keine Messwerte für das Intervall** | Produktion und Verbrauch `0`, Überschuss `0` → Regel 2. Der Entscheid wird geschrieben, damit die Lücke **sichtbar** ist statt unsichtbar. |
| **Nachrechnen für einen Tag ohne Messwerte** | Leere Liste; die Ansicht zeigt den Hinweis „keine Entscheide“ wie bei einem Tag ohne Aufzeichnung. Kein Fehler — der Tag hat schlicht keine Grundlage. |
| **Messwerte treffen verspätet ein** (MQTT-Ausfall) | Der nächste Lauf überschreibt den Entscheid des betroffenen Intervalls per Upsert. Ein Nachlauf über ältere Intervalle ist **nicht** vorgesehen (§7). |
| **Leerer Tag in der Ansicht** | Hinweis `STEUERUNG_KEINE_ENTSCHEIDE`, kein leeres Diagrammgerüst. |
| **Datum in der Zukunft** | Leere Liste mit demselben Hinweis, kein Fehler. |
| **`von` nach `bis`** beim Nachrechnen | `400` mit lesbarem Klartext. |
| **Zeitraum über 366 Tage** | `400`. |
| **Zeitumstellung Frühling** | Die Stunde 02:00–03:00 entfällt; der Ortstag hat 92 Intervalle. Es entsteht eine Lücke, sonst nichts. |
| **Zeitumstellung Herbst** | Die Stunde 02:00–03:00 tritt zweimal auf. Beide Durchgänge tragen denselben `zeit_von`, der zweite überschreibt den ersten per Upsert: **96 statt 100 Entscheide**. Bewusst hingenommen — nachts gibt es keinen Überschuss, und `zev.messwerte` verliert dieselben vier Intervalle (nachgeprüft am 26.10.2025). Siehe FR-3. |
| **Job-Lauf überschneidet sich** | Der Job ist nicht reentrant; ein zweiter Lauf desselben Intervalls ist durch das Upsert folgenlos. |
| **Datenbank nicht erreichbar** | Der Job protokolliert den Fehler und endet; der nächste Lauf versucht es erneut. Keine Systemmeldung je Lauf — bei 96 Läufen am Tag wäre das eine Flut. |
| **Flag mitten am Tag eingeschaltet** | Die Entscheide beginnen ab dem nächsten Lauf; der Tag ist unvollständig, und die Ansicht zeigt das, ohne zu behaupten, es sei nichts passiert. |

## 6. Abhängigkeiten & betroffene Funktionalität

**Voraussetzungen**
* `zev.preiszeitreihe` mit laufendem Abruf (`Specs/Preiszeitreihe.md`) — **vorhanden**, Flag
  `PREISZEITREIHE` muss beim Mandanten aktiv sein, sonst fehlen die Preise.
* `zev.messwerte` mit Produktion und Verbrauch im 15-Minuten-Raster — **vorhanden**.
* **Empfohlen, nicht zwingend:** eine Einheit vom Typ `SPEICHER` (`Specs/Batteriespeicher.md`).
  Ohne sie funktioniert die Steuerung, aber man sieht nicht, ob ein „Ladung gesperrt" etwas bewirkt
  hat oder ins Leere lief, weil die Batterie ohnehin voll war. Für den Ertragsvergleich einer
  späteren Ausbaustufe ist sie unverzichtbar.

**Betroffener Code**
| Ort | Änderung |
|---|---|
| `FeatureFlag` (Enum) | neuer Wert `EINSPEISESTEUERUNG`, Vorgabe `false` |
| `app.routes.ts` | neue Route `/einspeisesteuerung` mit `AuthGuard` + `FeatureFlagGuard` |
| `navigation.component.html` | neuer Menüeintrag hinter Flag und Permission |
| `Specs/Berechtigungen.md` | `SteuerungController` in die Endpunkt-Matrix |
| `application.yml` | nur der Cron-Ausdruck (`ZEV_STEUERUNG_CRON`) |
| `EinstellungenDTO` / `EinstellungenService` | neuer Block `steuerung` in `organisation.konfiguration` |
| `einstellungen.component.*` | neuer Abschnitt hinter dem Feature-Flag (FR-7) |
| `Specs/Einstellungen.md` | die drei neuen Felder |
| `ArchitectureTest` | keine Ausnahme nötig — die Entity trägt `org_id` |

**Datenmigration:** keine. Die Tabelle beginnt leer; es gibt keine Altdaten, die zu Entscheiden
umzuformen wären. Die Historie entsteht ab dem ersten Lauf — **das Nachrechnen (FR-6) füllt diese
Lücke nicht**, es rechnet nur und speichert nichts.

## 7. Abgrenzung / Out of Scope

* **Das Schalten selbst.** Kein MQTT-Publish, kein Wechselrichter-Zugriff, keine Anlagensteuerung.
  Diese Ausbaustufe beobachtet.
* **Ertragsrechnung in Franken** (FR-6): braucht gemessene Lade-/Entladedaten und den
  Ladezustand. Die Kapazität (20 kWh) ist zwar bekannt und konfigurierbar, aber **keine Regel
  wertet sie in dieser Ausbaustufe aus** — sie steht dort für die nächste.
* **Ladezustand der Batterie.** Weder gemessen noch geschätzt. Die Regel kommt ohne ihn aus; die
  *Beurteilung* ihrer Wirkung nicht — das ist der Preis dieser Ausbaustufe. Die Anbindung an den
  Wechselrichter (MHT-30K-100) wird parallel geklärt (§8) und ist eine eigene Anforderung.
* **Anlegen der `SPEICHER`-Einheit.** Bereits in `Specs/Batteriespeicher.md` beschrieben.
* **Nachlauf über vergangene Intervalle.** Der Job wertet nur das zuletzt abgeschlossene Intervall
  aus. Verspätete Messwerte korrigieren den Entscheid beim nächsten Lauf, aber es gibt kein
  Aufholen über Stunden hinweg.
* **Vergleich verschiedener Regelvarianten.** Das Nachrechnen dreht den **Schwellwert**, nicht die
  Regelstruktur.
* **Prognose der eigenen Produktion** (Wetterdienst, Historie). Die Steuerung verwendet den Preis
  als einzigen Blick nach vorne.
* **Automatische Empfehlung eines Schwellwerts.** Das Nachrechnen liefert Zahlen; die Wahl trifft
  ein Mensch.

## 8. Offene Fragen

**Beantwortet (13.09.2026) — die Entscheide stecken im Text oben:**

| Frage | Entscheid | Wirkung |
|---|---|---|
| Massstab für `speicherwert` | **Netzbezug minimieren, Einspeiseertrag maximieren** → `0.31` | §1, FR-2; Regel 3 löst praktisch nie aus |
| Schwellwert `0.05` brauchbar? | **empirisch aus der Aufzeichnung ermitteln** | bestätigt FR-6 als Zweck |
| Batterie und Ladezustand | **Pylontech 20 kWh, Wechselrichter MHT-30K-100**; Anbindung wird geklärt | §1, Kapazität konfigurierbar |
| Reicht `tarife:manage`? | **ja** | NFR-2 |
| Schwellwert in `.env` oder DB? | **gleich je Mandant in `organisation.konfiguration`** | FR-2, **neue FR-7** |
| Takt des Jobs | **1 Minute nach Intervallende** | FR-1 |

**Weiterhin offen:**

* **Wie kommt der Ladezustand aus dem MHT-30K-100?** Wird geklärt. Sobald er über den Pi
  mitkommt (`Specs/Pi-Gateway-Software.md`), lässt sich die Wirkung der Regel direkt beurteilen
  statt aus Energiedeltas zu schätzen — und die Ertragsrechnung wird möglich. Bis dahin bleibt
  offen, ob ein „Ladung gesperrt" gewirkt hat oder ins Leere lief, weil die Batterie ohnehin voll
  war.

* **Ist die Kapazität bei Hene überhaupt knapp?** Das ist die Frage, an der die ganze Regel 4
  hängt: Sie verschiebt Ladung in die billigste Stunde, weil 20 kWh nicht für den ganzen
  Tagesüberschuss reichen. Bleibt der tägliche Überschuss **unter** 20 kWh, ist nie eine
  Entscheidung nötig — dann lädt die Batterie ohnehin alles, und die Steuerung hätte nichts zu
  tun. Ich kann das nicht prüfen: In der hiesigen Datenbank stehen Testdaten (Tagesverbräuche bis
  1'600 kWh), nicht die Anlage von Hene. **Die Aufzeichnung beantwortet es in den ersten Tagen** —
  und das ist ein guter Grund, mit dem Trockenlauf zu beginnen, bevor irgendetwas gebaut wird, das
  schaltet. --> das ist schwierig zu beantworten: im Sommer reicht die Kapazität aus, solange nicht allzu viele E-Autos geladen werden. Im Winter wird sie wohl eher knapp werden.

* **Soll die `SPEICHER`-Einheit gleich angelegt werden?** Sie ist in
  `Specs/Batteriespeicher.md` fertig spezifiziert und wäre die Voraussetzung dafür, die Wirkung zu
  sehen. Ausserhalb des Scopes dieser Spec, aber sinnvollerweise davor oder parallel.
