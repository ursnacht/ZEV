# Abrechnung (Nebenkosten)

> **Teil des Bereichs Nebenkosten.** Das Grundgerüst — Feature-Flag, Menü, Permission — ist in
> [`Nebenkosten.md`](./Nebenkosten.md) beschrieben und bereits umgesetzt. Dieses Dokument füllt
> den Menüpunkt **Abrechnung** mit Inhalt.

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Eine Nebenkostenabrechnung je Zeitraum erfassen und je Mieter
  abrechnen. Die Kosten entstehen in drei Formen — als **Umlage** (Gesamtkosten nach Schlüssel
  verteilt), als **Verbrauch** (gemessene Menge je Mieter) und als **Zuschlag** (Prozent auf die
  Summe). Gegen die Kosten werden die **Akonto-Zahlungen** des Mieters gestellt; daraus ergibt
  sich Nachzahlung oder Guthaben.
* **Warum machen wir das:** Nebenkosten werden heute ausserhalb der Anwendung geführt und von
  Hand verteilt. Die Daten dafür — Mieter, Mietdauer, Einheiten — liegen bereits im System.
* **Aktueller Stand:**
  - `/nebenkosten/abrechnung` existiert als **leere Gerüstseite** (`Nebenkosten.md`, FR-4).
  - Der Feature-Flag `NEBENKOSTENABRECHNUNG` und die Permission `nebenkosten:manage` sind
    umgesetzt; die Sichtbarkeit im Menü hängt an beiden.
  - `Mieter` trägt `mietbeginn`/`mietende` und die zugeordneten Einheiten — daraus lässt sich
    ableiten, wer in einem Zeitraum abzurechnen ist.

### Entscheid: NK-Positionen leben in der Abrechnung, nicht in `zev.tarif`

Die offene Frage aus der Basis-Spec („eigener Tariftyp oder `ZUSATZ` mitbenutzen?") ist damit
beantwortet: **keins von beidem.** Eine NK-Position trägt ihre Bezeichnung und ihren Betrag
selbst und gilt nur für **eine** Abrechnung. Ein Tarif hat eine Gültigkeitsdauer und wird von
vielen Abrechnungen geteilt — das passt hier nicht: „Heizkosten 2026" ist kein Tarif, sondern
eine Zeile in genau einer Abrechnung.

**Folge:** Der Menüpunkt **Nebenkosten → Tarifpositionen** wird nicht gebraucht. Er bleibt
vorerst stehen (Entscheid vom 22.08.2026) und ist mit dieser Spec **nicht** Gegenstand der
Umsetzung; über sein Entfernen wird separat entschieden.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Ablauf / Flow

1. Der Benutzer öffnet **Nebenkosten → Abrechnung** und sieht die Liste aller Abrechnungen,
   **nach Datum absteigend** (neuste zuoberst).
2. Unterhalb der Tabelle legt er über **„Neue Abrechnung erstellen"** eine Abrechnung an.
3. In der Bearbeitungsmaske erfasst er **Bezeichnung**, **Datum von/bis**, die **Anzahl
   Wohnungen** (Nenner der Umlage, FR-2) und die **allgemeinen Positionen** (Umlage, Verbrauch,
   Zuschlag).
4. Darunter erscheint **je Mieter** ein Block mit dessen Positionen: Umlage- und Zuschlagszeilen
   berechnet und schreibgeschützt, Verbrauchszeilen zur Eingabe der Menge, dazu frei erfassbare
   **Zusatzpositionen** und die **Akonto-Angaben**.
5. **Speichern** schreibt die Abrechnung samt aller Positionen.
6. Ist die Abrechnung fertig, setzt der Benutzer das Flag **„abgerechnet"** — direkt in der
   Liste oder in der Maske. Damit ist sie **schreibgeschützt**.
7. Das Zurücksetzen des Flags gibt sie wieder frei und wird **rückgefragt**.

### FR-2: Die drei Positionsarten

| Art | Zweck | Erfasst wird | Je Mieter |
|---|---|---|---|
| **UMLAGE** | Gesamtkosten nach Schlüssel verteilt (Allgemeinstrom, Regenwasser) | Bezeichnung, Totalbetrag, Gesamtmenge *(optional)*, Einheit | **berechnet**, schreibgeschützt |
| **VERBRAUCH** | gemessene Menge je Mieter (Warmwasser, Heizung) | Bezeichnung, Einheit, Betrag pro Einheit | **Menge eingeben**, Betrag berechnet |
| **ZUSCHLAG** | Prozent auf die Summe (Verwaltungskosten) | Bezeichnung, Prozentsatz | **berechnet**, schreibgeschützt |

**Verteilung bei UMLAGE (Entscheid):** **zeitanteilig**, wobei der Nenner die **theoretisch
mögliche** Mietdauer aller Wohnungen ist — nicht die tatsächliche.

```
Anzahl Wohnungen = erfasstes Feld der Abrechnung (Entscheid, siehe unten)
Tage im Zeitraum = datum_bis - datum_von + 1

Nenner   = Anzahl Wohnungen x Tage im Zeitraum
Tage(i)  = Σ über die Einheiten des Mieters i: Tage, an denen er sie im Zeitraum gemietet hat

Betrag je Mieter i = Totalbetrag x Tage(i) / Nenner
Menge  je Mieter i = Gesamtmenge x Tage(i) / Nenner    (nur wenn Gesamtmenge erfasst ist)
```

**Die Anzahl Wohnungen wird erfasst, nicht abgeleitet (Entscheid).** Sie steht als Feld in den
Angaben zur Abrechnung (FR-7). Vorbelegt wird sie mit der Zahl der `CONSUMER`-Einheiten des
Mandanten — das ist ein **Vorschlag**, kein Zwang: Eine Wohnung, die nicht an der
Nebenkostenabrechnung teilnimmt, oder eine Liegenschaft, die nicht deckungsgleich mit dem
Mandanten ist, lässt sich so abbilden.

Gibt es **keine** `CONSUMER`-Einheiten, bleibt das Feld **leer** (Entscheid) — nicht `0`. Eine
vorgeschlagene `0` verstiesse gegen den eigenen CHECK-Constraint und liesse jede neue Abrechnung
sofort in eine Fehlermeldung laufen, ohne dass der Benutzer etwas falsch gemacht hätte.

Damit bleibt die Abrechnung von den Einheiten **unabhängig** — passend zum Entscheid in
Abschnitt 1, dass eine Abrechnung ihre Zahlen selbst trägt.

Sind alle Wohnungen den ganzen Zeitraum vermietet, ergibt sich für jeden derselbe Anteil —
„1/9 Allgemeinstrom" entsteht bei `Anzahl Wohnungen = 9` von selbst, ohne dass etwas zu
erfassen wäre.

Gerechnet wird in **Tagen**, nicht in Monaten: Ein Wechsel zur Monatsmitte wäre sonst nicht
abbildbar. (Die Akonto-Monate in FR-4 sind davon unabhängig und bleiben Monate.)

Die Gesamtmenge ist **optional** — ohne sie bleibt die Mengenspalte beim Mieter leer und es wird
nur der Betrag verteilt.

**Mieter mit mehreren Wohnungen** tragen entsprechend mehr: Ihre Tage zählen je Einheit. Wer
zwei Wohnungen das ganze Jahr mietet, trägt zwei Anteile.

**Leerstand geht zu Lasten des Eigentümers (Entscheid).** Weil der Nenner die mögliche und
nicht die tatsächliche Mietdauer ist, bleibt bei Leerstand ein Teil des Totalbetrags
**unverteilt**.

```
Beispiel: 9 Wohnungen, Zeitraum 365 Tage, Allgemeinstrom 900.00 CHF
Wohnung 5 steht 90 Tage leer.

Nenner = 9 x 365 = 3'285
Mieter mit voller Dauer = 900.00 x 365 / 3'285 = 100.00 CHF
Summe aller Mieter      = 900.00 x 3'195 / 3'285 = 875.34 CHF
Nicht verteilt          =  24.66 CHF   (Leerstandsanteil)
```

**Der unverteilte Anteil muss sichtbar sein.** Die Maske weist ihn je Umlageposition getrennt
aus („nicht verteilt: 24.66 CHF"), sonst gehen 24.66 CHF unbemerkt verloren und niemand kann
die Abrechnung gegen den Beleg des Lieferanten prüfen. Er ist **fachlich** begründet und darf
nicht mit der Rundungsdifferenz aus FR-5 vermischt werden — beide werden getrennt angezeigt.

**Zu klein erfasste Anzahl Wohnungen wird abgewiesen.** Weil die Zahl frei eingegeben wird, kann
sie kleiner sein als die tatsächliche Belegung — etwa `5` bei neun ganzjährigen Mietern. Dann
überstiege die Summe der verteilten Beträge den Totalbetrag, und die Mieter zahlten gemeinsam
mehr als angefallen ist. Das System prüft deshalb:

```
Σ Tage(i) über alle Mieter  <=  Nenner
```

Ist die Bedingung verletzt, wird das Speichern mit einer Meldung abgewiesen, die beide Werte
nennt. Der umgekehrte Fall — mehr Wohnungen als belegt — ist zulässig und genau der Leerstand.

**Berechnung bei ZUSCHLAG (Entscheid): kaskadierend.** Ein Zuschlag rechnet auf die Summe aller
Zeilen **vor** ihm:

```
Zuschlag(p) je Mieter = (Summe aller Zeilen dieses Mieters mit kleinerer reihenfolge)
                        x Prozentsatz(p) / 100
```

Ein zweiter Zuschlag schliesst den ersten damit ein.

**Eine Reihenfolge über beide Tabellen.** „Zeilen" meint sowohl die allgemeinen Positionen
(`nk_position`) als auch die Zusatzpositionen des Mieters (`nk_zusatz`) — beide führen eine
`reihenfolge` im **selben Nummernraum** je Abrechnung. Ohne das wären Zusatzpositionen von der
Kaskade nicht erfasst und das Ergebnis nicht definiert.

Da die eine Nummer je Abrechnung, die andere je Abrechnung **und Mieter** eindeutig ist, können
sich die Nummern über die Tabellen hinweg treffen. **Bei Gleichstand kommt die allgemeine
Position vor der Zusatzposition** — damit ist die Berechnung deterministisch.

**Folge: `reihenfolge` ist fachlich tragend**, nicht bloss Anzeigereihenfolge — sie bestimmt das
Ergebnis. Die Oberfläche muss die Positionen deshalb ordnen lassen (FR-7).

### FR-3: Positionen je Mieter

Abgerechnet werden alle Mieter, deren Mietverhältnis den Zeitraum **berührt**
(`mietbeginn <= datum_bis` und (`mietende` leer oder `mietende >= datum_von`)).

Je Mieter erscheinen in dieser Reihenfolge:

1. **Umlage-Zeilen** — Bezeichnung, Menge, Einheit, Betrag; alles schreibgeschützt.
2. **Verbrauchs-Zeilen** — Bezeichnung und Einheit schreibgeschützt, **Menge eingebbar**,
   Betrag = Menge × Betrag pro Einheit.
3. **Zusatz-Zeilen** — frei erfassbar: Bezeichnung, Einheit, Menge und **Betrag pro Einheit**;
   der Zeilenbetrag ist `Menge × Betrag pro Einheit`, gerechnet wie bei `VERBRAUCH` (Entscheid).
   Über eine Schaltfläche „Position hinzufügen" am Ende des Mieterblocks angelegt, je Zeile über
   ein Löschsymbol entfernbar.
4. **Zuschlags-Zeilen** — berechnet, schreibgeschützt.
5. **Kostentotal** des Mieters.
6. **Akonto-Block** und **Saldo** (FR-4).

### FR-4: Akonto

Je Abrechnung und Mieter werden erfasst:

| Feld | Herkunft |
|---|---|
| **Betrag pro Monat** (B) | vorbelegt aus dem neuen Mieter-Stammdatum `akonto_pro_monat`, überschreibbar |
| **Anzahl Monate** (A) | vorbelegt aus der Mietdauer im Abrechnungszeitraum, **anteilig** gerechnet (Entscheid), überschreibbar |
| **Korrekturbetrag** (K) | frei, darf negativ sein |

```
Akonto total = A x B + K
Saldo        = Kostentotal - Akonto total
```

**Anzahl Monate anteilig (Entscheid).** Ein angebrochener Monat zählt **nicht** voll, sondern
mit seinem Anteil. Gerechnet wird je Kalendermonat, weil Monate unterschiedlich lang sind:

```
A = Σ über alle Monate des Zeitraums:
      (Miettage in diesem Monat) / (Tage dieses Monats)

auf 2 Nachkommastellen gerundet  ->  passt zu NUMERIC(5,2)
```

Beispiel: Mietbeginn 15. Februar, Zeitraum ab 1. Januar → Januar 0, Februar 14/28 = 0.50,
ab März je 1.00. Für einen Zeitraum bis 30. Juni ergibt das `A = 4.50`.

Der Wert ist ein **Vorschlag** und bleibt überschreibbar — wer lieber ganze Monate verrechnet,
trägt sie von Hand ein.

Ein **positiver Saldo ist eine Nachzahlung**, ein negativer ein **Guthaben**; die Anzeige
benennt beides ausdrücklich, statt nur ein Vorzeichen zu zeigen.

Neues Feld an `zev.mieter`: `akonto_pro_monat NUMERIC(10,2)`, **nullable** — Bestandsmieter haben
keinen Wert, dann bleibt das Feld in der Abrechnung leer und ist von Hand zu füllen.

### FR-5: Persistierung

Fünf neue Tabellen, alle mit `org_id` (serverseitig gesetzt, nie aus dem Request) und
`@Filter(orgFilter)`.

**Mengeneinheit erweitern (Entscheid).** Das bestehende Enum `ch.nacht.entity.Mengeneinheit`
kennt `KWH`, `MONAT`, `STUECK`. Es wird um **`M3`** ergaenzt und von den NK-Tabellen mitbenutzt,
statt ein zweites Einheiten-Enum einzufuehren.

> **Achtung, DDL:** `ck_tarif_mengeneinheit` auf `zev.tarif` zaehlt die erlaubten Werte auf
> (`KWH`, `MONAT`, `STUECK`). Der Constraint ist in derselben Migration anzupassen, sonst
> schlaegt jeder Tarif mit der neuen Einheit fehl. In den NK-Tabellen gilt fuer `einheit`
> derselbe Wertebereich per eigenem CHECK-Constraint; `MONAT` ist dort fachlich sinnlos, wird
> aber nicht ausgeschlossen - die Einschraenkung waere Willkuer ohne Nutzen.

> **Achtung, Frontend:** Es gibt ein **zweites** Enum `Mengeneinheit` in
> `frontend-service/src/app/models/tarif.model.ts` (Zeile 12) und zwei Ableitungsfunktionen mit
> `else`-Zweig auf kWh:
> * `mengeneinheitKey()` (Zeile 63-65) endet mit `… : 'KWH'`
> * `preisEinheitKey()` (Zeile 28-33) ebenso
>
> Ein unbekannter Wert fällt damit stillschweigend auf **„kWh"** zurück — `M3` würde also
> als Kilowattstunden beschriftet. Kein Compiler-Fehler, keine Ausnahme: Die Zahl stimmt, die
> Einheit daneben ist falsch. Beide Funktionen und das Enum sind mitzuziehen, dazu der
> Übersetzungsschlüssel der neuen Einheit.
>
> Das ist der Preis des Entscheids „Enum erweitern statt zweites einführen": **alle** bestehenden
> Auswertungen müssen mit.

**`zev.nk_abrechnung`**

| Spalte | Typ | Pflicht | Bemerkung |
|---|---|---|---|
| `id` | BIGINT | ✅ | PK |
| `org_id` | BIGINT | ✅ | FK `zev.organisation` |
| `bezeichnung` | VARCHAR(150) | ✅ | z.B. „Nebenkostenabrechnung 2026" |
| `datum_von` | DATE | ✅ | |
| `datum_bis` | DATE | ✅ | `CHECK (datum_von <= datum_bis)` |
| `anzahl_wohnungen` | INTEGER | ✅ | `CHECK (anzahl_wohnungen > 0)`; bildet den Nenner der Umlage (FR-2) |
| `abgerechnet` | BOOLEAN | ✅ | Default `false` |
| `erstellt_am` | TIMESTAMP | ✅ | |

**`zev.nk_position`** — allgemeine Positionen einer Abrechnung

| Spalte | Typ | Pflicht | Bemerkung |
|---|---|---|---|
| `id`, `org_id` | BIGINT | ✅ | |
| `abrechnung_id` | BIGINT | ✅ | FK, `ON DELETE CASCADE` |
| `art` | VARCHAR(20) | ✅ | `UMLAGE` \| `VERBRAUCH` \| `ZUSCHLAG`, CHECK-Constraint |
| `bezeichnung` | VARCHAR(150) | ✅ | |
| `reihenfolge` | INTEGER | ✅ | Anzeigereihenfolge |
| `einheit` | VARCHAR(20) | ❌ | `M3` \| `KWH` \| `STUECK`; bei `ZUSCHLAG` leer |
| `totalbetrag` | NUMERIC(12,2) | ❌ | nur `UMLAGE` |
| `gesamtmenge` | NUMERIC(12,3) | ❌ | nur `UMLAGE`, optional |
| `betrag_pro_einheit` | NUMERIC(12,4) | ❌ | nur `VERBRAUCH` |
| `prozentsatz` | NUMERIC(5,2) | ❌ | nur `ZUSCHLAG` |

`UNIQUE (abrechnung_id, reihenfolge, org_id)` — die Reihenfolge bestimmt das Ergebnis der
Zuschlagskaskade (FR-2); zwei Positionen mit derselben Nummer machten sie nicht-deterministisch.

Die art-abhängigen Pflichtfelder werden per **CHECK-Constraint** erzwungen, nicht nur im
Service — sonst entstehen bei einem Fehler im Code Zeilen, die sich nicht mehr berechnen lassen:

```sql
CHECK (
  (art = 'UMLAGE'    AND totalbetrag IS NOT NULL AND einheit IS NOT NULL
                     AND betrag_pro_einheit IS NULL AND prozentsatz IS NULL)
  OR (art = 'VERBRAUCH' AND betrag_pro_einheit IS NOT NULL AND einheit IS NOT NULL
                     AND totalbetrag IS NULL AND gesamtmenge IS NULL AND prozentsatz IS NULL)
  OR (art = 'ZUSCHLAG'  AND prozentsatz IS NOT NULL
                     AND totalbetrag IS NULL AND gesamtmenge IS NULL
                     AND betrag_pro_einheit IS NULL AND einheit IS NULL)
)
```

**`zev.nk_verbrauch`** — erfasste Menge je Mieter zu einer Verbrauchsposition

| Spalte | Typ | Pflicht | Bemerkung |
|---|---|---|---|
| `id`, `org_id` | BIGINT | ✅ | |
| `position_id` | BIGINT | ✅ | FK `nk_position`, `ON DELETE CASCADE` |
| `mieter_id` | BIGINT | ✅ | FK `zev.mieter`, `ON DELETE RESTRICT` (Loeschschutz, s.o.) |
| `menge` | NUMERIC(12,3) | ✅ | `CHECK (menge >= 0)` |

`UNIQUE (position_id, mieter_id, org_id)`

**`zev.nk_zusatz`** — frei erfasste Position eines Mieters

| Spalte | Typ | Pflicht | Bemerkung |
|---|---|---|---|
| `id`, `org_id` | BIGINT | ✅ | |
| `abrechnung_id` | BIGINT | ✅ | FK, `ON DELETE CASCADE` |
| `mieter_id` | BIGINT | ✅ | FK `zev.mieter`, `ON DELETE RESTRICT` (Loeschschutz, s.o.) |
| `reihenfolge` | INTEGER | ✅ | gleicher Nummernraum wie `nk_position` (FR-2) |
| `bezeichnung` | VARCHAR(150) | ✅ | |
| `einheit` | VARCHAR(20) | ✅ | `M3` \| `KWH` \| `STUECK` |
| `menge` | NUMERIC(12,3) | ✅ | `CHECK (menge >= 0)` |
| `betrag_pro_einheit` | NUMERIC(12,4) | ✅ | Zeilenbetrag = `menge × betrag_pro_einheit` |

`UNIQUE (abrechnung_id, mieter_id, reihenfolge, org_id)`

**`zev.nk_akonto`** — Akonto je Abrechnung und Mieter

| Spalte | Typ | Pflicht | Bemerkung |
|---|---|---|---|
| `id`, `org_id` | BIGINT | ✅ | |
| `abrechnung_id` | BIGINT | ✅ | FK, `ON DELETE CASCADE` |
| `mieter_id` | BIGINT | ✅ | FK `zev.mieter`, `ON DELETE RESTRICT` (Loeschschutz, s.o.) |
| `anzahl_monate` | NUMERIC(5,2) | ✅ | `CHECK (>= 0)`; Dezimalstellen für angebrochene Monate |
| `betrag_pro_monat` | NUMERIC(10,2) | ✅ | `CHECK (>= 0)` |
| `korrektur` | NUMERIC(10,2) | ✅ | Default `0`, darf negativ sein |

`UNIQUE (abrechnung_id, mieter_id, org_id)`

**Berechnete Werte werden nicht gespeichert.** Umlage-, Zuschlags- und Summenbeträge ergeben
sich jederzeit aus den erfassten Daten. Sie zu speichern hiesse, zwei Wahrheiten zu pflegen.

**Löschschutz statt Einfrieren (Entscheid):** Damit die Zahlen einer abgeschlossenen Abrechnung
stabil bleiben, verweisen `nk_verbrauch`, `nk_zusatz` und `nk_akonto` mit **`ON DELETE RESTRICT`**
auf `zev.mieter` — ein Mieter, der in einer Abrechnung vorkommt, lässt sich nicht mehr löschen.

Das ist nötig, weil die Annahme „ein Mieter kann ohnehin nicht gelöscht werden" **nicht
zutrifft**: `MieterService.deleteMieter` (Zeile 159-165) blockiert nur, wenn **Tarifpositionen**
an den Einheiten des Mieters hängen. Ein Mieter ohne solche Positionen ist löschbar, und die
Fremdschlüssel von `debitor` und `mieter_einheit` auf `zev.mieter` stehen auf `CASCADE` —
seine Debitoreneinträge verschwinden dabei stillschweigend mit.

Mit `RESTRICT` bleibt die Fehlermeldung beim Löschversuch das Signal, statt dass eine
abgeschlossene Abrechnung im Nachhinein andere Zahlen zeigt. Ein Einfrieren der berechneten
Beträge erübrigt sich damit.

### Geldtyp und Rundung

**Alle Betraege als `BigDecimal`**, nie als `double`. Der benachbarte `RechnungService` rechnet
mit `double` (Zeilen 231, 275, 337) - dieses Muster darf hier **nicht** uebernommen werden, weil
die Vorgabe unten sonst nicht einzuhalten ist.

**Auf 1 Rappen, je Zeile** (Entscheid): `setScale(2, RoundingMode.HALF_UP)` je Zeilenbetrag. Jeder Zeilenbetrag wird einzeln kaufmännisch auf zwei
Nachkommastellen gerundet; Summen entstehen aus den bereits gerundeten Zeilen.

> **Folge, die in Kauf genommen wird:** Die Summe der auf die Mieter verteilten Umlagebeträge
> kann vom erfassten Totalbetrag abweichen — bei `n` Mietern um bis zu `n/2` Rappen. Bei neun
> Mietern und 900.00 CHF Allgemeinstrom sind das höchstens 4 Rappen. Nicht zu verwechseln mit
> dem **Leerstandsanteil** aus FR-2: Der ist fachlich begründet und um Grössenordnungen
> grösser; beide werden getrennt ausgewiesen.
>
> **Die Differenz wird NICHT ausgeglichen (Entscheid).** Sie einem Mieter zuzuschlagen wäre
> willkürlich und im Beleg nicht erklärbar; die Abweichung von wenigen Rappen ist der
> geringere Schaden. Die Oberfläche zeigt den erfassten Totalbetrag und die Summe der
> verteilten Beträge nebeneinander, damit die Differenz sichtbar und nicht überraschend ist.

### FR-6: REST-Endpunkte

Unter `/api/nebenkosten/abrechnungen`, Klassen-Annotation
`@PreAuthorize("hasAuthority('nebenkosten:manage')")`.

| HTTP | Pfad | Zweck |
|---|---|---|
| `GET` | `/api/nebenkosten/abrechnungen` | Liste, nach `datum_von` absteigend |
| `GET` | `/api/nebenkosten/abrechnungen/{id}` | Abrechnung **samt** Positionen, Mieterzeilen und berechneten Beträgen |
| `POST` | `/api/nebenkosten/abrechnungen` | anlegen → `201` |
| `PUT` | `/api/nebenkosten/abrechnungen/{id}` | Abrechnung samt Positionen speichern |
| `PATCH` | `/api/nebenkosten/abrechnungen/{id}/abgerechnet` | Flag setzen/zurücksetzen |
| `DELETE` | `/api/nebenkosten/abrechnungen/{id}` | löschen → `204`, unbekannt → `404` |

**Feature-Flag serverseitig prüfen.** Dies sind die **ersten** NK-Endpunkte; damit greift die
Regel aus `Nebenkosten.md` FR-2 zum ersten Mal: Ist `NEBENKOSTENABRECHNUNG` für den Mandanten
aus, antworten alle Endpunkte mit `403`. Ohne das wäre der Flag reine Kosmetik — die API bliebe
über einen HTTP-Client erreichbar.

**Umsetzung: expliziter Aufruf** (Entscheid) am Anfang **jeder** Service-Methode, direkt neben
`hibernateFilterService.enableOrgFilter()`:

```java
pruefeFeatureFlag();   // wirft, wenn NEBENKOSTENABRECHNUNG fuer den Mandanten aus ist
```

Die Bausteine sind vorhanden: `FeatureFlagService.isEnabled(orgId, FeatureFlag)` und
`getCurrentOrgId()`. Kein Aspect, keine eigene Annotation — der explizite Aufruf ist im
Service sichtbar und folgt dem bestehenden Muster des Org-Filters.

> Der Preis ist bekannt: Wird der Aufruf in einer neuen Methode vergessen, ist sie ungeschuetzt.
> Ein Architekturtest (ArchUnit) kann das absichern - siehe `docs/archunit-tests.md`.

### FR-7: Layout

**Liste** (`/nebenkosten/abrechnung`)
* `zev-table` mit Bezeichnung, Datum von, Datum bis, „abgerechnet" und Kebab-Menü.
* „abgerechnet" ist **direkt in der Tabelle** als Checkbox bedienbar. Beim **Deaktivieren**
  erscheint eine Rückfrage („Abrechnung wieder zur Bearbeitung freigeben?"), beim Aktivieren
  nicht.
* Kebab-Menü: **Bearbeiten** und **Löschen**; Löschen mit Rückfrage.
* **Unterhalb** der Tabelle die Schaltfläche „Neue Abrechnung erstellen"
  (`zev-button--primary`).
* Leerstate: „Keine Abrechnungen erfasst".

**Bearbeitungsmaske** — drei Bereiche untereinander:

1. **Angaben zur Abrechnung** — Bezeichnung, Datum von/bis, **Anzahl Wohnungen** und Flag
   „abgerechnet". Die Anzahl Wohnungen ist mit der Zahl der `CONSUMER`-Einheiten vorbelegt und
   überschreibbar; ein Hinweis nennt ihre Wirkung („bildet den Nenner der Umlage").
2. **Allgemeine Positionen** — Tabelle mit Auswahl der Art je Zeile. Die Eingabefelder richten
   sich nach der Art: Bei `UMLAGE` erscheinen Totalbetrag, Gesamtmenge und Einheit, bei
   `VERBRAUCH` Einheit und Betrag pro Einheit, bei `ZUSCHLAG` nur der Prozentsatz. Nicht
   zutreffende Felder werden **ausgeblendet**, nicht bloss gesperrt. Zeilen lassen sich
   hinzufügen und entfernen.
   * **Ordnen per Drag & Drop** (Entscheid): Die Zeilen werden mit der Maus verschoben; die
     `reihenfolge` ergibt sich aus der Position in der Liste und wird beim Speichern neu
     durchnummeriert. Ein Anfasser-Symbol macht die Zeile als verschiebbar erkennbar.
   * Weil die Reihenfolge das Ergebnis der Zuschlagskaskade bestimmt (FR-2), zeigt die Tabelle
     die Zuschlagszeilen mit ihrer Bemessungsgrundlage an — sonst ist beim Verschieben nicht
     erkennbar, was sich gerade ändert.
   * Zusatzpositionen je Mieter sind innerhalb ihres Mieterblocks ebenso verschiebbar.
3. **Positionen je Mieter** — je Mieter ein `zev-panel` mit dem Namen als Titel, darin die
   Zeilen nach FR-3, der Akonto-Block und der Saldo.

* Beträge werden bei jeder Eingabe **sofort neu gerechnet** (clientseitig), ohne Speichern.
  Diese Rechnung ist eine **Vorschau**: Massgebend ist das Backend. **Nach dem Speichern laedt
  die Maske die Antwort des Servers und zeigt dessen Werte an**, nicht die selbst gerechneten.
  Weicht die Vorschau ab, wird das im selben Moment sichtbar statt monatelang unbemerkt zu
  bleiben - die Regeln existieren zwangslaeufig zweimal (Java und TypeScript).
* **Speichern**-Schaltfläche am Ende der Maske.
* Ist die Abrechnung `abgerechnet`, sind **alle** Eingabefelder gesperrt und ein Hinweis erklärt
  warum; nur das Flag selbst bleibt bedienbar.
* Alle Texte über `TranslationService`; Zahlenformat nach `Specs/generell.md`.

**Benötigte Übersetzungsschlüssel** (Migration ab V117, je deutsch **und** englisch):

| Bereich | Schlüssel |
|---|---|
| Seite und Liste | `NK_ABRECHNUNGEN`, `NK_ABRECHNUNG_NEU`, `NK_KEINE_ABRECHNUNGEN`, `NK_ABGERECHNET` |
| Angaben zur Abrechnung | `NK_ANZAHL_WOHNUNGEN`, `NK_ANZAHL_WOHNUNGEN_HINT`, `NK_FEHLER_ANZAHL_WOHNUNGEN_ZU_KLEIN` |
| Rückfragen | `NK_ABGERECHNET_ZURUECKSETZEN_FRAGE`, `NK_ABRECHNUNG_LOESCHEN_FRAGE` |
| Positionsarten | `NK_ART_UMLAGE`, `NK_ART_VERBRAUCH`, `NK_ART_ZUSCHLAG` |
| Positionsfelder | `NK_TOTALBETRAG`, `NK_GESAMTMENGE`, `NK_BETRAG_PRO_EINHEIT`, `NK_PROZENTSATZ`, `NK_REIHENFOLGE` |
| Mieterblock | `NK_POSITION_HINZUFUEGEN`, `NK_KOSTENTOTAL`, `NK_KEINE_MIETER`, `NK_MIETER_OHNE_EINHEIT` |
| Akonto und Saldo | `NK_AKONTO`, `NK_AKONTO_PRO_MONAT`, `NK_ANZAHL_MONATE`, `NK_KORREKTUR`, `NK_AKONTO_TOTAL`, `NK_NACHZAHLUNG`, `NK_GUTHABEN` |
| Sperre | `NK_GESPERRT_HINWEIS`, `NK_FEHLER_ABGERECHNET` |
| Mengeneinheit | Schlüssel für **`M3`** (die bestehenden `KWH`, `MONATE`, `STUECK` gibt es bereits) |

Die Liste ist der Mindestumfang; beim Umsetzen ergänzte Schlüssel folgen derselben Migration.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

**Liste**
* [ ] Die Liste zeigt die Abrechnungen nach `datum_von` absteigend, neuste zuoberst.
* [ ] Ohne Abrechnungen erscheint ein Leerstate, keine leere Tabelle.
* [ ] Die Schaltfläche „Neue Abrechnung erstellen" steht **unterhalb** der Tabelle.
* [ ] „abgerechnet" lässt sich direkt in der Tabelle setzen — ohne Rückfrage.
* [ ] Das **Zurücksetzen** von „abgerechnet" fragt zurück; bei Abbruch bleibt das Flag gesetzt.
* [ ] Das Kebab-Menü bietet Bearbeiten und Löschen; Löschen fragt zurück.
* [ ] Das Löschen einer Abrechnung entfernt ihre Positionen, Verbrauchsmengen, Zusatzpositionen
      und Akonto-Zeilen mit.

**Positionen**
* [ ] Eine `UMLAGE`-Position verlangt Totalbetrag und Einheit; die Gesamtmenge ist optional.
* [ ] Waren alle Mieter den ganzen Zeitraum da, erhält jeder denselben Anteil (`Totalbetrag / n`).
* [ ] War ein Mieter nur einen Teil des Zeitraums da, erhält er **zeitanteilig** weniger —
      geprüft mit einem Mieter, der zur Monatsmitte auszieht (Tagesgenauigkeit).
* [ ] Bei erfasster Gesamtmenge wird auch die Menge zeitanteilig verteilt; ohne sie bleibt die
      Mengenspalte leer.
* [ ] Eine `VERBRAUCH`-Position verlangt Einheit und Betrag pro Einheit; der Mieterbetrag ist
      `Menge × Betrag pro Einheit`.
* [ ] Eine `ZUSCHLAG`-Position verlangt nur den Prozentsatz; Einheit und Beträge sind ausgeblendet.
* [ ] Der Zuschlag rechnet auf die Summe aller Positionen desselben Mieters mit **kleinerer
      `reihenfolge`** — ein zweiter Zuschlag schliesst den ersten damit ein (Kaskade).
* [ ] Eine Positionszeile lässt sich per **Drag & Drop** verschieben; ein Anfasser-Symbol weist
      darauf hin.
* [ ] Nach dem Speichern ist die `reihenfolge` lückenlos neu durchnummeriert.
* [ ] Das Umordnen der Positionen ändert das Ergebnis eines Zuschlags nachvollziehbar.
* [ ] Tragen eine allgemeine Position und eine Zusatzposition **dieselbe** `reihenfolge`, zählt
      die allgemeine zuerst — das Ergebnis ist damit reproduzierbar (FR-2).
* [ ] Der Nenner ist `Anzahl Wohnungen × Tage im Zeitraum` — **nicht** die Summe der
      tatsächlichen Miettage.
* [ ] Die Anzahl Wohnungen ist in den Angaben zur Abrechnung erfassbar und mit der Zahl der
      `CONSUMER`-Einheiten vorbelegt.
* [ ] Eine Anzahl Wohnungen von `0` oder weniger wird abgewiesen.
* [ ] Hat der Mandant keine `CONSUMER`-Einheiten, ist das Feld **leer** vorbelegt, nicht `0`.
* [ ] Ein Mieter ohne zugeordnete Einheit erhaelt keinen Umlageanteil und wird in der Maske
      mit einem Hinweis gekennzeichnet; das Speichern bleibt moeglich.
* [ ] Ist die Anzahl Wohnungen kleiner als die tatsächliche Belegung (`Σ Tage(i) > Nenner`),
      wird das Speichern mit einer Meldung abgewiesen, die beide Werte nennt.
* [ ] Eine Änderung der Anzahl Wohnungen ändert alle Umlagebeträge sofort, ohne Speichern.
* [ ] Nach dem Speichern zeigt die Maske die vom **Server** gelieferten Betraege, nicht die
      clientseitig gerechnete Vorschau.
* [ ] Steht eine Wohnung zeitweise leer, bleibt ihr Anteil **unverteilt**; die übrigen Mieter
      zahlen dadurch **nicht** mehr.
* [ ] Der unverteilte Leerstandsanteil wird je Umlageposition ausgewiesen.
* [ ] Ein Mieter mit zwei Wohnungen trägt den doppelten Anteil.
* [ ] Jeder Zeilenbetrag ist auf **1 Rappen** gerundet; Summen entstehen aus gerundeten Zeilen.
* [ ] Rundungsdifferenz und Leerstandsanteil werden **getrennt** ausgewiesen und nicht vermischt.
* [ ] Eine Position mit art-fremden Feldern wird von der Datenbank abgewiesen (CHECK-Constraint),
      nicht nur vom Service.

**Je Mieter**
* [ ] Abgerechnet werden genau die Mieter, deren Mietverhältnis den Zeitraum berührt.
* [ ] Umlage- und Zuschlagszeilen sind schreibgeschützt.
* [ ] Bei Verbrauchszeilen ist ausschliesslich die Menge eingebbar.
* [ ] Zusatzpositionen lassen sich hinzufügen und einzeln wieder entfernen; ihr Zeilenbetrag ist
      `Menge × Betrag pro Einheit` wie bei `VERBRAUCH`.
* [ ] Das Kostentotal ist die Summe aller Zeilen des Mieters.
* [ ] Ein Mieter, der in einer Abrechnung vorkommt, lässt sich **nicht mehr löschen**
      (`ON DELETE RESTRICT`); der Löschversuch endet in einer verständlichen Meldung.

**Akonto**
* [ ] „Betrag pro Monat" ist aus `mieter.akonto_pro_monat` vorbelegt und überschreibbar.
* [ ] Fehlt das Stammdatum, bleibt das Feld leer und die Abrechnung ist trotzdem speicherbar.
* [ ] „Anzahl Monate" ist aus der Mietdauer im Zeitraum vorbelegt und überschreibbar.
* [ ] Ein angebrochener Monat zählt **anteilig**, nicht voll — Mietbeginn 15. Februar in einem
      Zeitraum bis 30. Juni ergibt `A = 4.50`.
* [ ] `Akonto total = A × B + K`; ein negativer Korrekturbetrag ist zulässig.
* [ ] Der Saldo ist als **Nachzahlung** oder **Guthaben** benannt, nicht nur als Vorzeichen.

**Sperre**
* [ ] Bei gesetztem „abgerechnet" sind alle Eingabefelder der Maske gesperrt.
* [ ] `PUT` und `DELETE` auf eine abgerechnete Abrechnung werden mit einer verständlichen
      Meldung abgewiesen.
* [ ] `PATCH .../abgerechnet` bleibt möglich und gibt die Abrechnung wieder frei.

**i18n**
* [ ] Alle sichtbaren Texte stammen aus dem `TranslationService`; keine fest verdrahteten Strings.
* [ ] Jeder neue Schluessel hat einen deutschen **und** einen englischen Text.
* [ ] Die Uebersetzungsmigration ist wiederholbar (`ON CONFLICT (key) DO NOTHING`).
* [ ] Die drei Positionsarten erscheinen uebersetzt (`NK_ART_UMLAGE`, `NK_ART_VERBRAUCH`,
      `NK_ART_ZUSCHLAG`), nicht als technische Enum-Namen.
* [ ] Saldo-Beschriftungen unterscheiden **Nachzahlung** und **Guthaben** in beiden Sprachen.

**Sicherheit und Mandantenfähigkeit**
* [ ] Ohne `nebenkosten:manage` antworten alle Endpunkte mit `403`.
* [ ] Bei ausgeschaltetem Feature-Flag antworten alle Endpunkte mit `403` — auch mit Permission.
* [ ] Ein Benutzer sieht ausschliesslich Abrechnungen seines Mandanten; eine im Request
      mitgeschickte `orgId` wird ignoriert.
* [ ] Der Zugriff auf eine fremde Abrechnung liefert `404`, nicht deren Daten.
* [ ] **Jede** öffentliche Methode des NK-Service prüft den Feature-Flag — abgesichert durch einen
      ArchUnit-Test, nicht durch Sichtprüfung. Der explizite Aufruf (FR-6) lässt sich sonst in
      einer neuen Methode vergessen, ohne dass es auffällt.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Die Bearbeitungsmaske lädt in unter einer Sekunde bei 30 Mietern und 20 Positionen.
* Die Neuberechnung bei einer Eingabe erfolgt **clientseitig** ohne Server-Rundreise.
* `GET .../{id}` liefert alles in **einem** Aufruf — kein Nachladen je Mieter.

### NFR-2: Sicherheit
* Permission `nebenkosten:manage` (bereits vorhanden), `@PreAuthorize` auf Klassenebene.
* Feature-Flag `NEBENKOSTENABRECHNUNG` **serverseitig** geprüft (FR-6).
* Mandanten-Isolation über `@Filter(orgFilter)`; `org_id` serverseitig gesetzt.
* Frontend: Route mit `data.permissions: ['nebenkosten:manage']` und `FeatureFlagGuard`
  (bereits umgesetzt).

### NFR-3: Kompatibilität
* Additiv: fünf neue Tabellen, ein neues nullable Feld an `zev.mieter`. Bestehende Abrechnung,
  Tarife und Rechnungen bleiben unberührt.
* `mieter.akonto_pro_monat` ist nullable — Bestandsdaten bleiben gültig, keine Migration nötig.
* Die Gerüstseite aus `Nebenkosten.md` wird ersetzt.

## 5. Edge Cases & Fehlerbehandlung

* **Mieter ohne zugeordnete Einheit:** `Tage(i)` ist dann `0` - der Mieter traegt **keinen**
  Umlage- und Zuschlagsanteil, waehrend Verbrauch, Zusatzpositionen und Akonto normal
  rechnen. Zulaessig, aber fast immer ein Datenfehler: Die Maske zeigt bei diesem Mieter
  einen Hinweis, dass ihm keine Einheit zugeordnet ist (Entscheid). Das Speichern wird
  nicht verhindert - die Zuordnung nachzutragen ist Sache der Mieterverwaltung.
* **Keine CONSUMER-Einheiten vorhanden:** Das Feld Anzahl Wohnungen bleibt leer statt `0`
  (FR-2). Das Speichern verlangt eine Eingabe > 0; die Meldung nennt das Feld.
* **Anzahl Wohnungen zu klein erfasst:** Ist `Σ Tage(i) > Nenner`, wuerden die Mieter zusammen
  mehr als den Totalbetrag tragen. Das Speichern wird abgewiesen (FR-2), die Maske nennt die
  erfasste Anzahl und die tatsaechlich belegten Wohnungs-Tage.
* **Keine Mieter im Zeitraum:** Die Maske zeigt einen Hinweis statt leerer Mieterblöcke. Umlage-
  und Zuschlagsbeträge sind dann nicht berechenbar — die Division durch `n = 0` muss abgefangen
  sein und darf keinen Fehler erzeugen.
* **Leere Positionsliste:** Speichern ist zulässig; die Abrechnung ist dann eine leere Hülle.
* **Verbrauchsmenge nicht erfasst:** Zählt als `0`, nicht als Fehler — sonst liesse sich eine
  Abrechnung nicht zwischenspeichern.
* **Mieter wird gelöscht:** Der Löschversuch **scheitert**, sobald der Mieter in einer Abrechnung
  vorkommt (`ON DELETE RESTRICT`, FR-5). Die Abrechnung bleibt unverändert; der Benutzer erhält
  eine verständliche Meldung aus `MieterService.deleteMieter` — nicht den Datenbankfehler.
* **Position wird entfernt:** Ihre Verbrauchsmengen verschwinden mit (`ON DELETE CASCADE`).
  Eine Rückfrage weist darauf hin, wenn bereits Mengen erfasst sind.
* **Zeitraum nachträglich geändert:** Die Menge der Mieter kann sich ändern. Erfasste Mengen zu
  nicht mehr beteiligten Mietern bleiben stehen, werden aber nicht mehr angezeigt und nicht
  gerechnet — sie leben wieder auf, wenn der Zeitraum zurückgeändert wird.
* **Netzwerkfehler:** `zev-message--error`, bleibt bis zum Wegklicken stehen; Erfolgsmeldung
  blendet nach 5 s aus.
* **Ungültige Eingaben:** negative Menge, negativer Totalbetrag, Prozentsatz ausserhalb 0–100,
  `datum_von > datum_bis` → Feldfehler, kein Request.
* **Gleichzeitige Bearbeitung:** Letzter Schreibvorgang gewinnt. Bei einer Abrechnung, die
  inzwischen `abgerechnet` ist, wird der `PUT` abgewiesen — das ist der praktisch relevante Fall.

## 6. Abhängigkeiten & betroffene Funktionalität

**Voraussetzungen**
* [`Nebenkosten.md`](./Nebenkosten.md) — Flag, Menü, Permission (umgesetzt).
* `zev.mieter` mit `mietbeginn`/`mietende`, `zev.organisation`.

**Betroffener Code**
* Neu: Entities, Repositories, `NkAbrechnungService`, `NkAbrechnungController`, DTOs für die
  zusammengesetzte Antwort aus FR-6.
* `Mieter` — neues Feld `akonto_pro_monat`; Mieter-Formular und -Liste ergänzen.
* `MieterRepository` — Abfrage „alle Mieter, deren Mietverhältnis einen Zeitraum berührt"
  (heute gibt es nur die einheitsbezogene Variante `findByEinheitIdAndQuartal`).
* `EinheitRepository` — **neue** Zählung nach Typ (`countByTyp`) für die Vorbelegung der Anzahl
  Wohnungen. Vorhanden sind nur `findAllByOrderByNameAsc`, `findFirstByTyp` und `existsByTyp` —
  eine Zählung gibt es nicht.
* `MieterService.getEinheitIds` (Zeile 194) bzw. `MieterEinheitRepository.findEinheitIdsByMieterId`
  (Zeile 57) — **vorhanden**, wird für `Tage(i) = Σ über die Einheiten des Mieters` genutzt.
* `MieterService.deleteMieter` — Löschschutz erweitern: Kommt der Mieter in einer NK-Abrechnung
  vor, wird das mit einer verständlichen Meldung abgewiesen. Ohne das schlägt erst der
  Datenbank-Constraint zu (`ON DELETE RESTRICT`, FR-5) und der Benutzer sieht einen
  `DataIntegrityViolationException` statt eines Hinweises. Vorbild ist die bestehende Prüfung
  auf Tarifpositionen (Zeilen 159–165).
* `Mengeneinheit` (Backend-Enum) — neuer Wert `M3`; Flyway-Migration für
  `ck_tarif_mengeneinheit` (FR-5).
* `frontend-service/src/app/models/tarif.model.ts` — Enum `Mengeneinheit` um `M3` erweitern
  **und** `mengeneinheitKey()` sowie `preisEinheitKey()` anpassen; beide fallen sonst auf `'KWH'`
  zurück und beschriften Kubikmeter als Kilowattstunden (FR-5).
* Ersetzt: `nebenkosten-abrechnung.component` (bisher Gerüst).
* Neue Flyway-Migrationen ab **V117**: fünf Tabellen, `mieter.akonto_pro_monat`, Übersetzungen.

**Datenmigration**
* Keine. Neue Tabellen starten leer, das neue Mieter-Feld ist nullable.

## 7. Abgrenzung / Out of Scope

* **Kein PDF und kein Versand.** Die Abrechnung wird erfasst und berechnet, nicht ausgegeben.
  Ein Ausdruck je Mieter folgt als eigene Spec.
* **Keine Debitorenbuchung.** Der Saldo wird angezeigt, aber nicht in die Debitorenkontrolle
  übernommen.
* **Keine Verteilschlüssel ausser gleichteilig.** Wertquote, Fläche und Personenzahl sind nicht
  Teil dieser Ausbaustufe (siehe Abschnitt 8).
* **Keine Vorjahresübernahme** von Positionen.
* **Der Menüpunkt „Tarifpositionen"** bleibt unverändert stehen (Entscheid) — er wird hier weder
  gefüllt noch entfernt und soll später möglicherweise für die Rechnungsgenerierung umbenannt
  werden.

## 8. Offene Fragen

Keine. Alle Punkte sind entschieden und in den Abschnitten 2 bis 7 eingearbeitet:

| Frage | Entscheid | Wo |
|---|---|---|
| Verteilung bei Umlage | zeitanteilig nach Tagen | FR-2 |
| Leerstand | Nenner = moegliche Mietdauer; Anteil bleibt unverteilt | FR-2 |
| Nenner der Umlage | Feld `Anzahl Wohnungen` in der Abrechnung, vorbelegt | FR-2, FR-7 |
| Mehrere Zuschlaege | kaskadierend nach `reihenfolge` | FR-2 |
| Gleichstand der Reihenfolge | allgemeine Position vor Zusatzposition | FR-2 |
| Zusatzposition | `Menge x Betrag pro Einheit` wie Verbrauch | FR-3 |
| Akonto-Monate | anteilig je Kalendermonat, 2 Nachkommastellen | FR-4 |
| Rundung | 1 Rappen je Zeile, Differenz bleibt stehen | FR-5 |
| Geldtyp | `BigDecimal`, `RoundingMode.HALF_UP` | FR-5 |
| Loeschschutz Mieter | `ON DELETE RESTRICT` + Pruefung im Service | FR-5 |
| Mengeneinheit | bestehendes Enum um `M3` erweitern | FR-5 |
| Feature-Flag im Backend | expliziter Aufruf je Service-Methode | FR-6 |
| Ordnen der Positionen | Drag & Drop | FR-7 |
| Flag `abgerechnet` | Schreibschutz | FR-1, FR-7 |
