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

### FR-2: Die vier Positionsarten

| Art | Zweck | Erfasst wird | Je Mieter |
|---|---|---|---|
| **UMLAGE** | Gesamtkosten nach Schlüssel verteilt (Allgemeinstrom, Regenwasser) | Bezeichnung, Totalbetrag, Gesamtmenge *(optional)*, Einheit | **berechnet**, schreibgeschützt |
| **UMLAGE_PERSON** | wie UMLAGE, aber nach **Köpfen** verteilt (Grünabfuhr) | wie UMLAGE | **berechnet**, schreibgeschützt |
| **VERBRAUCH** | gemessene Menge je Mieter (Warmwasser) | Bezeichnung, Einheit, Betrag pro Einheit | **Menge eingeben**, Betrag berechnet |
| **ANTEIL** | Gesamtkosten nach einem von aussen vorgegebenen Schlüssel (Heizkosten) | Bezeichnung, **Totalbetrag** | **Prozentsatz eingeben**, Betrag berechnet |
| **ZUSCHLAG** | Prozent auf die Summe (Verwaltungskosten) | Bezeichnung, Prozentsatz | **berechnet**, schreibgeschützt |

**Berechnung bei ANTEIL:**

```
Betrag je Mieter i = Totalbetrag x Prozentsatz(i) / 100
```

Für Kosten, deren Verteilschlüssel **von aussen** kommt — etwa aus der Abrechnung eines
Wärmezählerdienstes. Weder eine zeitanteilige Umlage noch eine Menge mal Preis bilden das ab: Der
Schlüssel ist bereits das Ergebnis einer fremden Rechnung und wird nur noch angewandt.

**Abgrenzung zu den benachbarten Arten**, damit die Auswahl eindeutig bleibt:

* **gegen ZUSCHLAG:** Dort steht **ein** Prozentsatz an der Position und rechnet auf die Summe der
  Zeilen davor. Hier trägt **jeder Mieter seinen eigenen**, bezogen auf den Totalbetrag.
* **gegen UMLAGE:** Dort ergibt sich der Schlüssel aus den Miettagen, ein Leerstand lässt einen
  Anteil unverteilt. Hier ist der Schlüssel vorgegeben und von den Miettagen **unabhängig**.

**Die Summe der Prozentsätze sollte 100% ergeben.** Sie wird als **Kontrollzahl** neben der
Position ausgewiesen und bei Abweichung hervorgehoben — aber **nicht erzwungen** (Entscheid):
Wer neun Mieter erfasst, muss zwischenspeichern können, ohne dass die Summe schon stimmt. Dieselbe
Überlegung wie bei der nicht erfassten Verbrauchsmenge (Abschnitt 5).

Was zu 100% fehlt, erscheint als **nicht verteilt** — dieselbe Spalte wie der Leerstandsanteil der
Umlage, nur mit anderer Ursache.

**Der Prozentsatz je Mieter steht in `zev.nk_verbrauch.menge`** — derselben Zeile wie eine erfasste
Verbrauchsmenge. Es ist genau ein Wert je Position und Mieter; die Bedeutung ergibt sich aus der
Art der Position. Eine zweite Spalte hätte bei jeder Zeile eine davon leer gelassen.

**Verteilung bei UMLAGE (Entscheid):** **zeitanteilig**, wobei der Nenner die **theoretisch
mögliche** Mietdauer aller Wohnungen ist — nicht die tatsächliche.

```
Anzahl Wohnungen = erfasstes Feld der Abrechnung (Entscheid, siehe unten)
Tage im Zeitraum = datum_bis - datum_von + 1

Nenner   = Anzahl Wohnungen x Tage im Zeitraum
Tage(i)  = Σ über die Wohnungen des Mieters i: Tage, an denen er sie im Zeitraum gemietet hat

Betrag je Mieter i = Totalbetrag x Tage(i) / Nenner
Menge  je Mieter i = Gesamtmenge x Tage(i) / Nenner    (nur wenn Gesamtmenge erfasst ist)
```

**Die Anzahl Wohnungen wird erfasst, nicht abgeleitet (Entscheid).** Sie steht als Feld in den
Angaben zur Abrechnung (FR-7). Vorbelegt wird sie mit der Zahl der `CONSUMER`-Einheiten, die als
**Wohnung gekennzeichnet** sind — das ist ein **Vorschlag**, kein Zwang: Eine Wohnung, die nicht
an der Nebenkostenabrechnung teilnimmt, oder eine Liegenschaft, die nicht deckungsgleich mit dem
Mandanten ist, lässt sich so abbilden.

**Ein Kennzeichen an der Einheit entscheidet (Entscheid).** `zev.einheit` trägt das Feld
`nebenkosten_relevant`; gezählt werden nur `CONSUMER`-Einheiten, bei denen es gesetzt ist.

Unter den Verbrauchern stehen auch Messpunkte, die keine Wohnung sind — Allgemeinstrom,
Eigenverbrauch der PV-Anlage. Zählten sie mit, wäre der Nenner zu gross und bei **jeder** Umlage
bliebe ein Anteil unverteilt, als stünde eine Wohnung leer. Der Fehler wäre still: Die Zahlen
sähen plausibel aus, nur die Summe stimmte nicht mit dem Beleg des Lieferanten überein.

**Warum ein eigenes Feld und nicht die Mieterzuordnung?** Die naheliegende Regel „nur Verbraucher
mit Mieter" trägt nicht: Solche Messpunkte sind in der Praxis dem **Eigentümer** als Mieter
zugeordnet, damit ihr Stromverbrauch verrechnet wird — sie kämen also durch. Umgekehrt ist eine
leer stehende Wohnung ohne aktuelle Zuordnung sehr wohl eine Wohnung; genau darauf beruht der
Leerstandsanteil. Die Zuordnung beantwortet die Frage schlicht nicht.

Das Kennzeichen ist bei einer neuen Einheit **gesetzt**: Die Ausnahme wird abgewählt, nicht die
Regel. In der Einheiten-Maske erscheint es nur beim Typ `CONSUMER` — bei den übrigen Typen stellt
sich die Frage nicht.

> **Startbelegung der Migration:** Gesetzt wird das Kennzeichen für jeden `CONSUMER`, der je einem
> Mieter zugeordnet war; alles andere wird abgewählt. Das ist eine einmalige Annäherung aus dem,
> was bekannt ist — ab dann ist allein das Kennzeichen massgebend. Ein Verbraucher, der einem
> Mieter zugeordnet und trotzdem keine Wohnung ist, muss einmalig von Hand abgewählt werden.

**Zähler und Nenner verwenden zwingend dieselbe Regel.** Auch `Tage(i)` zählt nur Einheiten mit
gesetztem Kennzeichen — nicht alle `CONSUMER`-Einheiten des Mieters.

Diese Symmetrie ist keine Feinheit, sondern die Bedingung dafür, dass die Rechnung aufgeht. Als
der Nenner das Kennzeichen auswertete und der Zähler noch nicht, stieg die Summe der Miettage über
den Nenner, und **jedes** Speichern wurde abgewiesen. Wäre die Prüfung `Σ Tage(i) <= Nenner` nicht
da gewesen, hätte der Eigentümer für seinen Allgemeinstrom-Messpunkt still einen Wohnungsanteil an
jeder Umlage erhalten.

Ein Mieter, dem nach dieser Regel keine Wohnung bleibt, erscheint mit **0 Tagen** und dem Hinweis
„keine Wohnung zugeordnet" — er trägt keinen Umlageanteil, seine Verbrauchs- und Zusatzpositionen
und sein Akonto rechnen aber normal weiter.

Gibt es **keine** so gekennzeichneten `CONSUMER`-Einheiten, bleibt das Feld **leer** (Entscheid) — nicht
`0`. Eine vorgeschlagene `0` verstiesse gegen den eigenen CHECK-Constraint und liesse jede neue
Abrechnung sofort in eine Fehlermeldung laufen, ohne dass der Benutzer etwas falsch gemacht hätte.

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

### Umlage pro Person (Nachtrag)

Nicht alle Gesamtkosten fallen nach Wohnungen an. Die **Grünabfuhr** ist der typische Fall: Sie
richtet sich nach Köpfen, nicht nach Türen. Dafür gibt es die Positionsart **`UMLAGE_PERSON`**.
Die bestehende `UMLAGE` bleibt unverändert — Name, Beschriftung und Rechnung.

```
Anzahl Personen = erfasstes Feld der Abrechnung (Vorschlag: Anzahl Wohnungen)
Personen(i)     = erfasste Personen je Wohnung des Mieters i (Vorgabe 1)

Nenner        = Anzahl Personen x Tage im Zeitraum
PersonenTage(i) = Tage(i) x Personen(i)        (Tage(i) wie bei UMLAGE, also inkl. Wohnungen)

Betrag je Mieter i = Totalbetrag x PersonenTage(i) / Nenner
Menge  je Mieter i = Gesamtmenge x PersonenTage(i) / Nenner   (nur wenn Gesamtmenge erfasst ist)
```

**Alles bleibt gleich, solange nichts erfasst wird (Entscheid).** Die Anzahl Personen wird mit der
**Anzahl Wohnungen** vorbelegt, die Personen je Mieter mit **1**.

Gemeint ist der **Wert des Feldes** „Anzahl Wohnungen", nicht der Vorschlag des Servers (die Zahl
der nebenkostenrelevanten Einheiten). In einer **neuen** Abrechnung zieht die Anzahl Personen deshalb
nach, solange sie nicht selbst erfasst wurde; ab dem ersten Speichern trägt die Abrechnung ihre
eigene Zahl und folgt nicht mehr — sonst verschöbe eine Korrektur der Wohnungszahl stillschweigend
die Personenumlage einer bestehenden Abrechnung. Bleiben beide Vorgaben stehen, ist
`PersonenTage(i) = Tage(i)` und `Nenner` derselbe wie bei der Wohnungsumlage — eine Umlage pro
Person rechnet dann **genau** wie eine Umlage pro Wohnung. Das ist Absicht: Eine bestehende
Abrechnung ändert ihre Zahlen nicht, bloss weil es die neue Art gibt, und die neue Art ist ohne
Vorbereitung benutzbar.

**„Personen je Wohnung" heisst je Wohnung (Annahme).** Die Zahl liegt über `Tage(i)` und damit über
den Wohnungen: Wer zwei Wohnungen mit je drei Personen mietet, trägt sechs Anteile. Für den
Regelfall — ein Mieter, eine Wohnung — ist der Unterschied ohne Belang; bei mehreren Wohnungen ist
diese Lesart die einzige, die zur Beschriftung passt.

**Die Personenzahl gehört zur Abrechnung, nicht zum Mieter (Entscheid).** Ein Haushalt wächst und
schrumpft; eine abgeschlossene Abrechnung muss ihre Zahlen behalten. Gespeichert wird sie in
`zev.nk_person` je Abrechnung und Mieter — derselbe Grundsatz, aus dem die Anzahl Wohnungen am Kopf
der Abrechnung steht und nicht aus den Einheiten abgeleitet wird.

**Gespeichert wird nur, was von der Vorgabe abweicht (Entscheid).** Eine erfasste `1` erzeugt keine
Zeile. Sonst entstünde für jeden Mieter jeder Abrechnung eine Zeile, nur um „1" festzuhalten — und
weil `nk_person` wie die übrigen Nebenkosten-Tabellen mit `ON DELETE RESTRICT` auf den Mieter zeigt,
wäre danach kein Mieter mehr löschbar, der überhaupt in einer Abrechnung vorkommt.

**Zu klein erfasste Anzahl Personen wird abgewiesen** — dieselbe Regel wie bei den Wohnungen:

```
Σ (Tage(i) x Personen(i)) über alle Mieter  <=  Anzahl Personen x Tage im Zeitraum
```

Geprüft wird **nur, wenn die Abrechnung mindestens eine Position der Art `UMLAGE_PERSON` enthält.**
Ohne eine solche hat die Personenzahl keine Wirkung; eine Fehlermeldung dazu wäre eine Sperre ohne
Gegenwert. Wie bei den Wohnungen bleibt der umgekehrte Fall zulässig und ergibt einen unverteilten
Anteil zu Lasten des Eigentümers.

**Fehlt die Anzahl Personen im Rumpf, gilt die Anzahl Wohnungen.** Ein Aufrufer, der das Feld nicht
kennt, bekommt damit dieselbe Rechnung wie vor der Erweiterung — deshalb trägt das Feld bewusst
**kein** `@NotNull` auf der Entity, sondern wird im Service ergänzt; die Spalte ist trotzdem
`NOT NULL`.

**Darstellung:** „Anzahl Wohnungen" und „Anzahl Personen" stehen im Kopf **nebeneinander** in einer
`.zev-form-row`. Das Feld je Mieter erscheint **nur**, wenn die Abrechnung eine Position dieser Art
enthält — ein wirkungsloses Eingabefeld lädt zum Ausfüllen ein und weckt eine falsche Erwartung.

**Beschriftung und Spaltenbreite (Nachtrag):** Die Art `UMLAGE` heisst in der Auswahl
**„Umlage pro Wohnung"** (englisch „Allocation per apartment"). „Umlage" allein sagt nicht mehr,
wonach verteilt wird, sobald es die Umlage pro Person gibt; der Schlüssel `NK_ART_UMLAGE` und die
Enum-Konstante bleiben unverändert. Die erste Spalte der Tabelle „Allgemeine Positionen" ist so
breit, dass die längste Beschriftung vollständig lesbar ist — `.zev-select` ist `width: 100%` und
gibt der Spalte keine Mindestbreite, sie fiele sonst auf die Breite ihrer Überschrift zusammen.

* [ ] Die Auswahl der Verteilart zeigt „Umlage pro Wohnung" vollständig, ohne Abschneiden; ebenso „Umlage pro Person".
* [ ] Die Beschriftung stammt aus einer Migration, nicht nur aus dem Übersetzungs-Editor — eine frisch aufgesetzte Datenbank zeigt denselben Text.

**Akzeptanzkriterien:**
* [ ] Die Auswahl der Positionsart enthält „Umlage pro Person"; „Umlage" ist unverändert benannt.
* [ ] Der Kopf der Abrechnung hat ein Feld „Anzahl Personen"; es steht neben „Anzahl Wohnungen".
* [ ] „Anzahl Personen" ist mit der Anzahl Wohnungen vorbelegt und mindestens 1; sonst wird das Speichern mit einer Meldung abgewiesen.
* [ ] In einer neuen Abrechnung zieht „Anzahl Personen" nach, wenn „Anzahl Wohnungen" geändert wird — bis die Zahl selbst erfasst ist. Bei einer gespeicherten Abrechnung zieht sie nicht mehr nach.
* [ ] Enthält die Abrechnung eine Position der Art „Umlage pro Person", erscheint je Mieter ein Feld „Personen je Wohnung" mit der Vorgabe 1; ohne eine solche Position erscheint es nicht.
* [ ] Eine Umlage pro Person mit den Vorgaben (Personen = Wohnungen, 1 Person je Mieter) ergibt **dieselben** Beträge wie eine Umlage pro Wohnung mit demselben Totalbetrag.
* [ ] Beispiel: 5 Personen, 2 ganzjährige Mieter mit 3 bzw. 2 Personen, Totalbetrag 1'000.00 → 600.00 / 400.00 CHF.
* [ ] Sind weniger Personen erfasst als im Nenner stehen, bleibt der Rest unverteilt und wird als „nicht verteilt" ausgewiesen (wie bei Leerstand).
* [ ] Eine erfasste Gesamtmenge wird nach demselben Schlüssel verteilt.
* [ ] `Σ (Tage(i) x Personen(i)) > Anzahl Personen x Tage` wird beim Speichern mit einer Meldung abgewiesen, die beide Zahlen nennt — aber nur, wenn eine Position dieser Art vorhanden ist.
* [ ] Eine Personenzahl gleich der Vorgabe 1 wird nicht gespeichert; ein Mieter bleibt dadurch löschbar, solange er nur mit der Vorgabe vorkommt.
* [ ] Ein Rumpf ohne „Anzahl Personen" wird angenommen und mit der Anzahl Wohnungen gerechnet.
* [ ] Bestehende Abrechnungen behalten nach der Migration ihre Beträge (`anzahl_personen = anzahl_wohnungen`).

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
kennt `KWH`, `MONAT`, `STUECK`. Es wird um **`M3`** und **`CHF`** ergaenzt und von den NK-Tabellen
mitbenutzt, statt ein zweites Einheiten-Enum einzufuehren.

**`CHF` („Fr.") ist die Einheit einer Umlage, deren verteilte Grösse selbst ein Betrag ist** —
Grünabfuhr, Versicherungsprämie, Hauswartpauschale. Die Mengenspalte des Mieters trägt dann
denselben Wert wie die Betragsspalte. Das ist gewollt: Es macht sichtbar, dass hier ein Betrag
und keine gemessene Menge verteilt wird, und die Zeile bleibt gegen den Beleg des Lieferanten
prüfbar.

`CHF` und `M3` stehen **nur** in der Nebenkostenabrechnung zur Auswahl, nicht am Tarif: Ein Preis
„CHF pro Fr." wäre keine sinnvolle Angabe.

> **Achtung, DDL:** `ck_tarif_mengeneinheit` auf `zev.tarif` zaehlt die erlaubten Werte auf
> (`KWH`, `MONAT`, `STUECK`). Der Constraint ist in derselben Migration anzupassen, sonst
> schlaegt jeder Tarif mit der neuen Einheit fehl. In den NK-Tabellen gilt fuer `einheit`
> derselbe Wertebereich per eigenem CHECK-Constraint; `MONAT` ist dort fachlich sinnlos, wird
> aber nicht ausgeschlossen - die Einschraenkung waere Willkuer ohne Nutzen.

> **Achtung, Frontend:** Es gibt ein **zweites** Enum `Mengeneinheit` in
> `frontend-service/src/app/models/tarif.model.ts`. Kritisch ist dort `mengeneinheitKey()`:
> Die Funktion endete auf einem `else`-Zweig `'KWH'`, ein unbekannter Wert wäre also
> stillschweigend als **Kilowattstunden** beschriftet worden — kein Compiler-Fehler, keine
> Ausnahme, nur eine falsche Einheit neben einer richtigen Zahl.
>
> `preisEinheitKey()` ist davon **nicht** betroffen: Sie gibt den Enum-Wert direkt als
> Übersetzungsschlüssel zurück und trägt einen neuen Wert von selbst mit.
>
> Mitzuziehen sind also: das Enum, `mengeneinheitKey()` und der Übersetzungsschlüssel der
> neuen Einheit.
>
> Das ist der Preis des Entscheids „Enum erweitern statt zweites einführen": **alle** bestehenden
> Auswertungen müssen mit.

> **Achtung, drei CHECK-Constraints:** Die erlaubten Werte sind an **drei** Stellen aufgezählt —
> `ck_tarif_mengeneinheit`, `ck_nk_position_einheit` und `ck_nk_zusatz_einheit`. Eine neue Einheit
> muss alle drei anfassen, sonst schlägt das Speichern genau dort fehl, wo sie gebraucht wird.
> Bereits ausgeführte Migrationen bleiben unverändert; die Constraints werden in einer neuen
> Migration ersetzt.

**`zev.nk_abrechnung`**

| Spalte | Typ | Pflicht | Bemerkung |
|---|---|---|---|
| `id` | BIGINT | ✅ | PK |
| `org_id` | BIGINT | ✅ | FK `zev.organisation` |
| `bezeichnung` | VARCHAR(150) | ✅ | z.B. „Nebenkostenabrechnung 2026" |
| `datum_von` | DATE | ✅ | |
| `datum_bis` | DATE | ✅ | `CHECK (datum_von <= datum_bis)` |
| `anzahl_wohnungen` | INTEGER | ✅ | `CHECK (anzahl_wohnungen > 0)`; bildet den Nenner der Umlage (FR-2) |
| `anzahl_personen` | INTEGER | ✅ | `CHECK (anzahl_personen > 0)`; Nenner der Umlage pro Person; Vorschlag = `anzahl_wohnungen` |
| `abgerechnet` | BOOLEAN | ✅ | Default `false` |
| `erstellt_am` | TIMESTAMP | ✅ | |

**`zev.nk_position`** — allgemeine Positionen einer Abrechnung

| Spalte | Typ | Pflicht | Bemerkung |
|---|---|---|---|
| `id`, `org_id` | BIGINT | ✅ | |
| `abrechnung_id` | BIGINT | ✅ | FK, `ON DELETE CASCADE` |
| `art` | VARCHAR(20) | ✅ | `UMLAGE` \| `UMLAGE_PERSON` \| `VERBRAUCH` \| `ANTEIL` \| `ZUSCHLAG`, CHECK-Constraint |
| `bezeichnung` | VARCHAR(150) | ✅ | |
| `reihenfolge` | INTEGER | ✅ | Anzeigereihenfolge |
| `einheit` | VARCHAR(20) | ❌ | `M3` \| `CHF` \| `KWH` \| `STUECK`; bei `ZUSCHLAG` und `ANTEIL` leer |
| `totalbetrag` | NUMERIC(12,2) | ❌ | `UMLAGE`, `UMLAGE_PERSON` und `ANTEIL` |
| `gesamtmenge` | NUMERIC(12,3) | ❌ | nur `UMLAGE` und `UMLAGE_PERSON`, optional |
| `betrag_pro_einheit` | NUMERIC(12,4) | ❌ | nur `VERBRAUCH` |
| `prozentsatz` | NUMERIC(5,2) | ❌ | nur `ZUSCHLAG`; bei `ANTEIL` steht er je Mieter in `nk_verbrauch.menge` |

`UNIQUE (abrechnung_id, reihenfolge, org_id)` — die Reihenfolge bestimmt das Ergebnis der
Zuschlagskaskade (FR-2); zwei Positionen mit derselben Nummer machten sie nicht-deterministisch.

Die art-abhängigen Pflichtfelder werden per **CHECK-Constraint** erzwungen, nicht nur im
Service — sonst entstehen bei einem Fehler im Code Zeilen, die sich nicht mehr berechnen lassen:

```sql
CHECK (
  (art IN ('UMLAGE', 'UMLAGE_PERSON')
                     AND totalbetrag IS NOT NULL AND einheit IS NOT NULL
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

**`zev.nk_person`** — Anzahl Personen je Wohnung eines Mieters

| Spalte | Typ | Pflicht | Bemerkung |
|---|---|---|---|
| `id`, `org_id` | BIGINT | ✅ | |
| `abrechnung_id` | BIGINT | ✅ | FK, `ON DELETE CASCADE` |
| `mieter_id` | BIGINT | ✅ | FK `zev.mieter`, `ON DELETE RESTRICT` (Loeschschutz, s.u.) |
| `anzahl_personen` | INTEGER | ✅ | `CHECK (anzahl_personen > 0)`, Default `1` |

`UNIQUE (abrechnung_id, mieter_id, org_id)`

Eigene Tabelle und keine Spalte in `nk_akonto`: Die Personenzahl hat mit dem Akonto nichts zu tun,
auch wenn beide dieselbe Körnung haben. Zeilen entstehen **nur** für Mieter mit einer von 1
abweichenden Zahl.

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
stabil bleiben, verweisen `nk_verbrauch`, `nk_zusatz`, `nk_akonto` und `nk_person` mit
**`ON DELETE RESTRICT`** auf `zev.mieter` — ein Mieter, der in einer Abrechnung vorkommt, lässt sich
nicht mehr löschen.

Das ist nötig, weil die Annahme „ein Mieter kann ohnehin nicht gelöscht werden" **nicht
zutrifft**: `MieterService.deleteMieter` (Zeile 159-165) blockiert nur, wenn **Tarifpositionen**
an den Einheiten des Mieters hängen. Ein Mieter ohne solche Positionen ist löschbar, und die
Fremdschlüssel von `debitor` und `mieter_einheit` auf `zev.mieter` stehen auf `CASCADE` —
seine Debitoreneinträge verschwinden dabei stillschweigend mit.

Mit `RESTRICT` bleibt die Fehlermeldung beim Löschversuch das Signal, statt dass eine
abgeschlossene Abrechnung im Nachhinein andere Zahlen zeigt. Ein Einfrieren der berechneten
Beträge erübrigt sich damit.

### Geldtyp und Rundung

**Alle Betraege als `BigDecimal`**, nie als `double`.

> **Nachtrag vom 24.08.2026 — die Ausnahme ist aufgehoben.** Hier stand, der benachbarte
> `RechnungService` rechne mit `double` und dieses Muster duerfe in der Nebenkostenabrechnung
> nicht uebernommen werden. Umgekehrt entschieden: Statt die Abweichung zu dulden, wurde die
> **Rechnungsgenerierung auf `BigDecimal` gezogen** (`Specs/RechnungenGenerieren.md`,
> Abschnitt 2). Damit gilt der Geldtyp `BigDecimal` einheitlich fuer Nebenkostenabrechnung,
> Quartalsrechnung und Debitorenkontrolle; die Nebenkostenabrechnung ist nicht mehr der
> Sonderfall, sondern die Regel. An dieser Spec aendert das nichts — die Vorgabe war schon
> vorher `BigDecimal`.

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
* **Oberhalb** der Tabelle die Schaltfläche „Neue Abrechnung erstellen"
  (`zev-button--primary`), in einer `zev-button-row` — wie auf allen übrigen Listenseiten
  (Tarife, Einheiten, Mieter). Die Stelle, an der die Schaltfläche erwartet wird, soll nicht von
  der Länge der Liste abhängen.
* Leerstate: „Keine Abrechnungen erfasst".

**Bearbeitungsmaske** — drei Bereiche untereinander:

1. **Angaben zur Abrechnung** — Bezeichnung, Datum von/bis, **Anzahl Wohnungen** und Flag
   „abgerechnet". Die Anzahl Wohnungen ist mit der Zahl der als Wohnung gekennzeichneten
   `CONSUMER`-Einheiten vorbelegt und überschreibbar; ein Hinweis nennt ihre Wirkung („bildet den
   Nenner der Umlage").
2. **Allgemeine Positionen** — Tabelle mit Auswahl der Art je Zeile. Die Eingabefelder richten
   sich nach der Art: Bei `UMLAGE` erscheinen Totalbetrag, Gesamtmenge und Einheit, bei
   `VERBRAUCH` Einheit und Betrag pro Einheit, bei `ANTEIL` nur der Totalbetrag, bei `ZUSCHLAG`
   nur der Prozentsatz. Nicht
   zutreffende Felder werden **ausgeblendet**, nicht bloss gesperrt. Zeilen lassen sich
   hinzufügen und entfernen.
   * **Alle Eingabefelder einer Zeile liegen auf einer Linie** — unabhängig davon, ob über oder
     unter dem Feld Text steht. In dieser Tabelle stehen beschriftete Felder (Totalbetrag,
     Prozentsatz) neben unbeschrifteten (Art, Bezeichnung), und unter manchen steht zusätzlich
     ein Hinweis.

     Umgesetzt ist das über einen **einheitlichen Feldaufbau**: Jedes Feld besteht aus einer
     Titelzeile und der Eingabe darunter; wo keine Beschriftung nötig ist, hält eine leere
     Titelzeile den Platz frei. Hinweise stehen **ausserhalb** dieser Zeile.

     Weder Zentrieren noch Ausrichten an der Unterkante genügt: Beim Zentrieren schweben die
     unbeschrifteten Felder, an der Unterkante richten sie sich am Hinweistext aus statt an den
     Eingaben.
   * **Die art-abhängigen Felder stehen in drei festen Spalten:** Betrag, Menge, Mengeneinheit.
     Jede Art füllt davon nur, was sie kennt — Umlage alle drei, Verbrauch die erste und die
     **dritte**, Anteil und Zuschlag nur die erste. Damit steht die Mengeneinheit bei jeder Art an
     derselben Stelle, und die Auswahlfelder mehrerer Zeilen stehen untereinander.

     Vorher rückten die Felder auf, und die Mengeneinheit einer Verbrauchsposition landete unter
     der *Gesamtmenge* der Umlage darüber. Die Spalten sind deshalb **fest** und nicht
     mitschrumpfend: Eine leere Spalte fiele sonst zusammen, und genau das war das Problem. Auf
     schmalen Schirmen (< 600px) stehen die Felder untereinander — dort ist eine Ausrichtung über
     Zeilen hinweg ohnehin nicht sichtbar.
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
* Am Ende der Maske drei Schaltflächen: **Speichern**, **Abbrechen** und
  **Zurück zur Übersicht**.

  **Speichern** und **Zurück zur Übersicht** erscheinen zusätzlich **oben bei den allgemeinen
  Positionen**, neben „Position hinzufügen". Bei dreissig Mieterblöcken (NFR-1) liegt das Ende der
  Maske mehrere Bildschirmseiten entfernt; wer am Kopf der Abrechnung arbeitet, soll dafür nicht
  ans Ende scrollen müssen. Die oberen lösen dieselbe Aktion aus wie die unteren.

  Die obere Zeile ist bei einer **abgeschlossenen** Abrechnung nicht leer: „Position hinzufügen"
  und „Speichern" fallen dort weg, **„Zurück zur Übersicht" bleibt**. Auf einer schreibgeschützten
  Abrechnung ist der Weg zurück der einzige Grund, überhaupt eine Schaltfläche zu suchen.

  **Abbrechen** verwirft die nicht gespeicherten Änderungen: Es lädt die Abrechnung neu und zeigt
  den Stand des Servers, die Maske bleibt **offen** (Entscheid vom 27.08.2026). Eine Meldung
  bestätigt das — sonst wäre der Klick auf einer unveränderten Maske ohne erkennbare Wirkung.
  Feldfehler eines gescheiterten Speicherversuchs verschwinden mit; sie gehören zu Eingaben, die es
  nicht mehr gibt.

  Bei einer **noch nicht gespeicherten** Abrechnung gibt es keinen Stand, auf den man zurückfallen
  könnte — dort schliesst „Abbrechen" die Maske (Entscheid). Derselbe Knopf hat damit je nach
  Zustand zwei Bedeutungen; das ist in Kauf genommen, weil die Alternative — eine leergeräumte
  Maske — beim Anlegen mehr überrascht als hilft.

  **Vorher taten „Abbrechen" und „Zurück zur Übersicht" dasselbe.** Zwei Schaltflächen mit
  identischem Verhalten, von denen eine ein Versprechen gab, das sie nicht hielt: Verworfen wurde
  nichts, die Maske wurde bloss verlassen.

  **Speichern und zurück** speichert und geht dann zur Liste (Entscheid vom 27.08.2026). „Ich bin
  fertig" heisst beides. Die Schaltfläche hiess bis dahin „Zurück zur Übersicht"; der Text
  verschwieg das Speichern, weshalb er mit V128 nachgezogen wurde. Der Übersetzungsschlüssel
  behält seinen Namen (`NK_ZURUECK_UEBERSICHT`) — ihn umzubenennen hiesse, ihn in Vorlage, Tests
  und E2E mitzuziehen, ohne dass sich am Verhalten etwas ändert. Scheitert das Speichern — ungültige Eingaben oder ein Fehler des Servers —,
  **bleibt die Maske stehen** und zeigt den Grund: Ein Verlassen würde genau die Eingaben verwerfen,
  die gerade gesichert werden sollten.

  Bei einer **abgeschlossenen** Abrechnung gibt es nichts zu speichern; die Felder sind gesperrt,
  und der Server wiese das Schreiben ab. Dort führt der Weg direkt zurück.

  Damit sind die drei Schaltflächen klar getrennt: **Speichern** sichert und bleibt, **Abbrechen**
  verwirft und bleibt, **Zurück zur Übersicht** sichert und geht.

  Speichern lässt die Maske offen, damit die vom Server gelieferten Zahlen geprüft werden können.
  Danach ist „Abbrechen" das falsche Wort für „ich bin fertig" — deshalb der eigene Weg zurück.
  Beide führen heute zur Liste; die Maske hält keinen Zustand, der beim Verlassen verlorenginge,
  weil Gespeichertes gespeichert ist. Getrennt sind sie, weil sie verschiedene **Absichten**
  benennen.
* Ist die Abrechnung `abgerechnet`, sind **alle** Eingabefelder gesperrt und ein Hinweis erklärt
  warum; nur das Flag selbst bleibt bedienbar.
* Alle Texte über `TranslationService`; Zahlenformat nach `Specs/generell.md`.

**Die Mieterblöcke sind sofort nach dem Laden bedienbar.** Mengen, Zusatzzeilen und Akonto lassen
sich eingeben, ohne dass zuvor irgendetwas verändert werden müsste.

Das ist kein selbstverständlicher Punkt, sondern die Lehre aus einem Fehler: Die Maske entscheidet
anhand der Herkunft einer Zeile, ob sie bearbeitbar ist. Solange diese Prüfung auf `undefined`
statt auf `null` verglich, galt **jede** Zeile aus der Serverantwort als schreibgeschützt — Jackson
schickt nicht gesetzte Felder als `null` mit, und `null !== undefined`. Bedienbar wurde die Maske
erst, wenn eine beliebige Eingabe die clientseitige Vorschau neu aufbaute.

**Verbindlich deshalb:** Antworten des Backends liefern nicht gesetzte Felder als `null`. Prüfungen
darauf verwenden `== null` / `!= null`, nie `=== undefined`. Das gilt gleichermassen für die
Zuordnung einer Zeile zu ihren Kontrollzahlen, die über die **Datenbank-ID** läuft und nicht über
die Reihenfolge.

**Eingabefelder binden über `ngModel`.** Die Mengenfelder der Verbrauchszeilen hingen zeitweise an
`[value]` mit `(change)`: Damit wurde erst beim Verlassen des Feldes gerechnet, während FR-7 die
Neuberechnung **bei jeder Eingabe** verlangt. Ausserdem las die Anzeige aus der berechneten Zeile,
die bei jeder Neuberechnung ersetzt wird, statt aus der Position, in der die Menge tatsächlich
steht. Beides bindet jetzt wie im übrigen Formular an das Modell.

Diese Regeln sind durch Unit-Tests der Maske abgesichert, die die Antwort **serverförmig**
nachbilden — mit `null` in den nicht gesetzten Feldern. Ein Test mit `undefined` hätte den Fehler
nicht gefunden.

**Die Mieterblöcke sind aufklappbar und beim Öffnen der Maske alle geschlossen.** Verwendet wird
`zev-collapsible` aus dem Design System.

Bei dreissig Mietern (NFR-1) und je einem Dutzend Zeilen ist die aufgeklappte Maske mehrere
Bildschirmseiten lang; die Angaben zur Abrechnung und die allgemeinen Positionen — der Teil, den
man beim Erfassen zuerst braucht — verschwinden dann nach oben aus dem Blick.

Damit die geschlossene Liste trotzdem aussagekräftig bleibt, zeigt die **Kopfzeile jedes Blocks**
den Namen, die Miettage und den **Saldo** als Nachzahlung oder Guthaben. Ohne den Saldo wäre die
geschlossene Ansicht eine reine Namensliste, und man müsste jeden Block einzeln öffnen, um das
Ergebnis zu sehen.

Der Aufklappzustand gilt nur für die geöffnete Maske und wird nicht gespeichert.

**Hinweismeldungen sind wegklickbar und stehen im Textfluss.** Jede Meldung der Maske
(`zev-message--info`) trägt ein Schliesskreuz und die Klasse `zev-message--statisch`.

Ohne `--statisch` ist eine Meldung im Design System ein **Overlay** (`position: fixed`, oben
mittig). Drei dauerhafte Hinweise übereinander verdecken damit den Seitenanfang und kollidieren
mit den kurzlebigen Erfolgs- und Fehlermeldungen, die denselben Platz belegen. Ein dauerhafter
Hinweis gehört dorthin, wo er gilt — beim Mieterblock, beim gesperrten Formular.

Wie lange eine Meldung weg bleibt, richtet sich danach, was sie sagt:

| Hinweis | Charakter | Bleibt weg |
|---|---|---|
| „Mieterblöcke erscheinen nach dem Speichern" | einmalige Erklärung | **dauerhaft** je Browser (`localStorage`) |
| „Abrechnung ist abgeschlossen" | beschreibt den Zustand **dieser** Abrechnung | für die geöffnete Maske |
| „Mieter ohne Wohnung" | beschreibt den Zustand **dieses** Mieters | für die geöffnete Maske, je Mieter einzeln |

Zustandsabhängige Hinweise dauerhaft auszublenden wäre falsch: Sie erklären, warum ein Feld
gesperrt ist oder ein Betrag fehlt, und müssen beim nächsten betroffenen Datensatz wieder
erscheinen.

**Benötigte Übersetzungsschlüssel** (Migration ab V117, je deutsch **und** englisch):

| Bereich | Schlüssel |
|---|---|
| Seite und Liste | `NK_ABRECHNUNGEN`, `NK_ABRECHNUNG_NEU`, `NK_KEINE_ABRECHNUNGEN`, `NK_ABGERECHNET` |
| Angaben zur Abrechnung | `NK_ANZAHL_WOHNUNGEN`, `NK_ANZAHL_WOHNUNGEN_HINT`, `NK_FEHLER_ANZAHL_WOHNUNGEN_ZU_KLEIN` |
| Rückfragen | `NK_ABGERECHNET_ZURUECKSETZEN_FRAGE`, `NK_ABRECHNUNG_LOESCHEN_FRAGE` |
| Positionsarten | `NK_ART_UMLAGE`, `NK_ART_VERBRAUCH`, `NK_ART_ANTEIL`, `NK_ART_ZUSCHLAG` |
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
      `CONSUMER`-Einheiten vorbelegt, die als Wohnung gekennzeichnet sind.
* [ ] Ein Verbraucher mit abgewähltem Kennzeichen (Allgemeinstrom, PV-Eigenverbrauch) wird nicht
      mitgezählt — auch dann nicht, wenn er einem Mieter zugeordnet ist.
* [ ] Eine Wohnung **ohne** aktuellen Mieter zählt mit; sonst gäbe es keinen Leerstandsanteil.
* [ ] Eine neu angelegte Einheit ist als Wohnung gekennzeichnet.
* [ ] Das Kennzeichen erscheint in der Einheiten-Maske nur beim Typ `CONSUMER`.
* [ ] Eine Anzahl Wohnungen von `0` oder weniger wird abgewiesen.
* [ ] Hat der Mandant keine als Wohnung gekennzeichneten `CONSUMER`-Einheiten, ist das Feld **leer** vorbelegt,
      nicht `0`.
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
* [ ] Die Mengeneinheit **`Fr.`** steht in der Abrechnung zur Auswahl und lässt sich speichern —
      an einer allgemeinen Position wie an einer Zusatzposition.
* [ ] Eine Umlage mit Einheit `Fr.` und erfasster Gesamtmenge zeigt beim Mieter denselben Wert in
      der Mengen- und in der Betragsspalte.
* [ ] `Fr.` erscheint **nicht** in der Einheitenauswahl eines ZUSATZ-Tarifs.

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

**Bedienbarkeit direkt nach dem Laden**
* [ ] Die Mengenfelder der Verbrauchszeilen sind **unmittelbar** nach dem Öffnen einer
      gespeicherten Abrechnung eingebbar — ohne dass zuvor eine Position hinzugefügt oder sonst
      etwas verändert werden muss.
* [ ] Der unverteilte Leerstandsanteil erscheint schon beim ersten Anzeigen neben der
      Umlagezeile, nicht erst nach der ersten Eingabe.
* [ ] Eine Zeile aus einer allgemeinen Position wird **nicht** als Zusatzzeile behandelt, obwohl
      die Serverantwort ihr `zusatzId`-Feld als `null` mitschickt.
* [ ] Eine Verbrauchsposition ohne bisher erfasste Menge zeigt ein **leeres, bedienbares** Feld.
* [ ] Der Betrag der Zeile ändert sich schon **beim Tippen**, nicht erst beim Verlassen des Feldes.
* [ ] Wird das Mengenfeld geleert, gilt die Menge als nicht erfasst (`null`), nicht als `0`.
* [ ] Ein **Neuladen** von `/nebenkosten/abrechnung` zeigt die Anwendung, nicht die
      Whitelabel-Fehlerseite — dasselbe gilt für Lesezeichen und geteilte Links.
* [ ] Eine Datei unterhalb eines Verzeichnisses (`/assets/…`) wird weiterhin als Datei
      ausgeliefert und ergibt bei einem Tippfehler ein `404`, nicht die `index.html`.

**Fehlermeldungen**
* [ ] Eine frisch geöffnete Maske zeigt **keine** Feldfehler, solange nicht gespeichert wurde.
* [ ] Nach einem Klick auf Speichern mit unvollständigen Angaben erscheinen die Feldfehler **und**
      eine Meldung, dass nicht gespeichert wurde.
* [ ] Bei unvollständigen Angaben wird **kein** Request abgeschickt.
* [ ] Jede Meldung trägt ein Schliesskreuz und verschwindet beim Anklicken; Erfolgsmeldungen
      zusätzlich nach fünf Sekunden von selbst.
* [ ] Die Speichern-Schaltfläche ist bei unvollständigen Angaben **nicht** gesperrt — eine graue
      Schaltfläche sagt nicht, was fehlt.

**Darstellung der Positionstabelle**
* [ ] In einer Positionszeile liegen alle Eingabefelder auf derselben Höhe — auch dort, wo über
      dem Feld keine Beschriftung steht.
* [ ] Das gilt auch, wenn **unter** einem Feld ein Hinweis steht (Anteil, Zuschlag): Der Hinweis
      verschiebt die übrigen Felder der Zeile nicht.
* [ ] Das gilt für jede Positionsart, also auch nach einem Wechsel der Art in derselben Zeile.
* [ ] Anfasser und Löschen-Schaltfläche liegen auf derselben Linie wie die Eingabefelder.
* [ ] Die **Mengeneinheit** steht bei jeder Positionsart in derselben Spalte: Bei einer
      Verbrauchsposition erscheint sie unter der Mengeneinheit einer Umlageposition, nicht unter
      deren Gesamtmenge.
* [ ] Der Platz der nicht belegten Spalte bleibt frei — er fällt nicht zusammen, sonst rutschte
      die Mengeneinheit wieder nach links.

**Positionsart ANTEIL**
* [ ] „Anteil (%)" steht in der Auswahl der Positionsart zur Verfügung.
* [ ] An der Position ist nur der **Totalbetrag** erfassbar — keine Einheit, kein Prozentsatz.
* [ ] Je Mieter ist ein **Prozentsatz** eingebbar; der Betrag ist `Totalbetrag × Prozentsatz / 100`.
* [ ] Die Summe der Prozentsätze wird als Kontrollzahl ausgewiesen und bei ≠ 100% hervorgehoben.
* [ ] Eine Abrechnung mit einer Anteilssumme ≠ 100% lässt sich trotzdem **speichern**.
* [ ] Was zu 100% fehlt, erscheint als „nicht verteilt".
* [ ] Der Betrag ist **unabhängig von den Miettagen** — ein Leerstand verändert ihn nicht.
* [ ] Eine Anteilszeile zählt in die Bemessungsgrundlage eines nachfolgenden Zuschlags.
* [ ] Der Wechsel der Art auf `ANTEIL` leert Einheit, Gesamtmenge, Betrag pro Einheit und
      Prozentsatz der Position.

**Zähler und Nenner**
* [ ] Ein Verbraucher mit abgewähltem Kennzeichen zählt weder in den Nenner **noch** in die
      Miettage seines Mieters.
* [ ] Ein Mieter, dem nur solche Einheiten zugeordnet sind, erscheint mit 0 Tagen und Hinweis.
* [ ] Nach dem Abwählen einer Einheit lässt sich eine bestehende Abrechnung weiterhin speichern —
      die Summe der Miettage sinkt mit dem Nenner.

**Schaltflächen**
* [ ] „Neue Abrechnung erstellen" steht **oberhalb** der Tabelle — auch bei leerer Liste.
* [ ] Die Maske bietet **Speichern**, **Abbrechen** und **Zurück zur Übersicht**.
* [ ] Bei den allgemeinen Positionen steht neben „Position hinzufügen" ein zweites **Speichern**,
      das dasselbe tut wie das am Ende der Maske.
* [ ] Bei abgeschlossener Abrechnung erscheint dieses zweite Speichern **nicht** — dort fehlt auch
      „Position hinzufügen".
* [ ] Bei den allgemeinen Positionen steht ausserdem ein zweites **Zurück zur Übersicht**, das
      dasselbe tut wie das am Ende der Maske.
* [ ] Dieses zweite „Zurück zur Übersicht" bleibt bei **abgeschlossener** Abrechnung stehen — es
      ist dort die einzige Schaltfläche der oberen Zeile.
* [ ] „Zurück zur Übersicht" **speichert** und führt dann zur Liste, ohne Rückfrage.
* [ ] Bei ungültigen Eingaben speichert es nicht und **bleibt** in der Maske, mit derselben
      Meldung wie ein gescheitertes Speichern.
* [ ] Weist der Server das Speichern ab, bleibt die Maske ebenfalls stehen.
* [ ] Bei **abgeschlossener** Abrechnung führt es direkt zur Liste, ohne zu speichern.
* [ ] Eine neue Abrechnung wird dabei angelegt und gespeichert, bevor die Maske schliesst.
* [ ] Die Schaltflächen am Ende der Maske sind **nicht** über die ganze Zeile gezogen — sie sehen
      aus wie die oberen bei den allgemeinen Positionen.
* [ ] „Abbrechen" einer **gespeicherten** Abrechnung lädt sie neu, bleibt in der Maske und
      bestätigt das mit einer Meldung.
* [ ] Dabei werden nicht gespeicherte Änderungen verworfen — die Maske zeigt den Stand des Servers.
* [ ] Feldfehler eines vorangegangenen Speicherversuchs verschwinden mit dem Abbrechen.
* [ ] „Abbrechen" einer **neuen**, noch nicht gespeicherten Abrechnung schliesst die Maske.
* [ ] Die Erfolgsmeldung erscheint erst **nach** dem Laden: Schlägt es fehl, steht die
      Fehlermeldung — und sie verschwindet nicht nach fünf Sekunden von selbst.
* [ ] Bei abgeschlossener Abrechnung ist nur **Speichern** gesperrt; beide Wege zurück bleiben
      bedienbar.

**Mieterblöcke aufklappen**
* [ ] Beim Öffnen der Maske sind **alle** Mieterblöcke geschlossen.
* [ ] Ein Klick auf die Kopfzeile öffnet den Block, ein weiterer schliesst ihn.
* [ ] Die Kopfzeile zeigt auch im geschlossenen Zustand Name, Miettage und den Saldo als
      Nachzahlung oder Guthaben.
* [ ] Mehrere Blöcke lassen sich gleichzeitig offen halten.
* [ ] Eine Eingabe in einem offenen Block verändert den Saldo in der Kopfzeile eines anderen
      Blocks nicht — die Blöcke rechnen unabhängig voneinander.
* [ ] Werte eines geschlossenen Blocks gehen beim Speichern **nicht** verloren.

**Hinweismeldungen**
* [ ] Jede Hinweismeldung der Maske trägt ein Schliesskreuz und verschwindet beim Anklicken.
* [ ] Keine Hinweismeldung liegt als Overlay über dem Seitenanfang; sie steht dort, wo sie gilt
      (`zev-message--statisch`).
* [ ] Der Hinweis „Mieterblöcke erscheinen nach dem Speichern" bleibt nach dem Wegklicken auch
      beim nächsten Aufruf verborgen.
* [ ] Der Sperrhinweis erscheint beim Öffnen einer anderen abgeschlossenen Abrechnung wieder,
      auch wenn er zuvor weggeklickt wurde.
* [ ] Der Hinweis „Mieter ohne Wohnung" lässt sich je Mieter einzeln wegklicken; die übrigen
      bleiben stehen.

**Sperre**
* [ ] Bei gesetztem „abgerechnet" sind alle Eingabefelder der Maske gesperrt.
* [ ] `PUT` und `DELETE` auf eine abgerechnete Abrechnung werden mit einer verständlichen
      Meldung abgewiesen.
* [ ] `PATCH .../abgerechnet` bleibt möglich und gibt die Abrechnung wieder frei.

**i18n**
* [ ] Alle sichtbaren Texte stammen aus dem `TranslationService`; keine fest verdrahteten Strings.
* [ ] Jeder neue Schluessel hat einen deutschen **und** einen englischen Text.
* [ ] Die Uebersetzungsmigration ist wiederholbar (`ON CONFLICT (key) DO NOTHING`).
* [ ] Die vier Positionsarten erscheinen uebersetzt (`NK_ART_UMLAGE`, `NK_ART_VERBRAUCH`,
      `NK_ART_ANTEIL`, `NK_ART_ZUSCHLAG`), nicht als technische Enum-Namen.
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
* **`zev.einheit` erhält eine neue Spalte** `nebenkosten_relevant` (FR-2). Sie ist `NOT NULL` mit
  Default `TRUE`; die Migration wählt sie für alles ab, was nicht ein je vermieteter `CONSUMER`
  ist. Die Einheiten-Maske zeigt sie nur bei diesem Typ. Andere Auswertungen der Einheit sind
  nicht betroffen — das Feld wird ausschliesslich für den Vorschlag der Anzahl Wohnungen gelesen.
* Die Gerüstseite aus `Nebenkosten.md` wird ersetzt.

### Betroffen: Auslieferung des Frontends bei verschachtelten Routen

Die Nebenkosten sind der erste Bereich mit **zweistufigen** Routen
(`/nebenkosten/abrechnung`). Dabei kam ein Fehler ans Licht, der alle künftigen verschachtelten
Routen gleichermassen betrifft:

`SpaRedirectController` im `frontend-service` leitete nur **einstufige** Pfade auf die
`index.html` um (`/{path:[^.]*}`). Ein Neuladen von `/nebenkosten/abrechnung` — oder jeder
Aufruf über Lesezeichen, Verlauf oder einen geteilten Link — landete deshalb auf der
Whitelabel-Fehlerseite von Spring Boot statt in der Anwendung.

Die Umleitung deckt nun bis zu **drei** Ebenen ab. Jedes Segment muss punktfrei bleiben: Sonst
fingen die Muster auch `/assets/logo.png` ab und lieferten HTML statt eines Bildes — beziehungsweise
verdeckten einen Tippfehler im Dateinamen mit einer scheinbar funktionierenden Seite.

## 5. Edge Cases & Fehlerbehandlung

* **Mieter ohne zugeordnete Einheit:** `Tage(i)` ist dann `0` - der Mieter traegt **keinen**
  Umlage- und Zuschlagsanteil, waehrend Verbrauch, Zusatzpositionen und Akonto normal
  rechnen. Zulaessig, aber fast immer ein Datenfehler: Die Maske zeigt bei diesem Mieter
  einen Hinweis, dass ihm keine Einheit zugeordnet ist (Entscheid). Das Speichern wird
  nicht verhindert - die Zuordnung nachzutragen ist Sache der Mieterverwaltung.
* **Keine als Wohnung gekennzeichneten CONSUMER-Einheiten vorhanden:** Das Feld Anzahl Wohnungen bleibt leer statt `0`
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
  **und** `mengeneinheitKey()` anpassen; die Funktion fiele sonst auf `'KWH'` zurück und
  beschriftete Kubikmeter als Kilowattstunden. `preisEinheitKey()` ist nicht betroffen (FR-5).
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
| Geldtyp | `BigDecimal`, `RoundingMode.HALF_UP` — seit 24.08.2026 auch in der Quartalsrechnung | FR-5 |
| Loeschschutz Mieter | `ON DELETE RESTRICT` + Pruefung im Service | FR-5 |
| Mengeneinheit | bestehendes Enum um `M3` erweitern | FR-5 |
| Feature-Flag im Backend | expliziter Aufruf je Service-Methode | FR-6 |
| Ordnen der Positionen | Drag & Drop | FR-7 |
| Flag `abgerechnet` | Schreibschutz | FR-1, FR-7 |
