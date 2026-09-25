# Ladeplanung

## 1. Ziel & Kontext - Warum wird das Feature benötigt?

* **Was soll erreicht werden:** Die Entscheidung, welcher PV-Überschuss in den Speicher geht, wird
  über eine **Merit-Order über den Resttag** getroffen statt über eine Regelkaskade je Intervall.
  Die Anlage speichert damit die Kilowattstunden mit dem **tiefsten Einspeisepreis** — und weiss
  dabei, wie viel Kapazität noch frei ist und wie viel Überschuss noch kommt.

* **Warum machen wir das:** Die bestehende Regel (`Specs/Einspeisesteuerung.md`, FR-2) vergleicht
  zwei Preise und kennt **keine Mengen**. Sie kann deshalb nicht unterscheiden zwischen „das Tal am
  Nachmittag ist erreichbar" und „die Sonne reicht bis dahin nicht mehr". Jede der drei Ergänzungen
  der letzten Woche war ein Flicken an derselben Stelle:

  | Ergänzung | wogegen | was eigentlich fehlte |
  |---|---|---|
  | `WARTEN_AUF_TAL` (zweite Bedingung) | wartete auf sich selbst | Zeitkopplung |
  | Mindest-Preisabstand (FR-2b) | sperrte für einen halben Rappen | Verhältnis von Nutzen zu Menge |
  | `SOC_TIEF` (FR-2a) | Speicher lief leer | Kapazität als Grösse |

  Am **18.09. und 21.09.2026** sperrte die Steuerung bis 15:00, weil am Nachmittag ein echtes Tal
  lag. Beide Male ging es auf — beide Male nur, weil der Nachmittag sonnig blieb. Die Regel konnte
  das nicht wissen; eine Mengenrechnung kann es.

* **Aktueller Stand:** `SteuerRegelService.entscheide(...)` wertet fünf Regeln je Intervall aus und
  liefert zwei Sollzustände (`batterieladung`, `einspeisung`, je `FREI`/`GESPERRT`). Der Job
  `SteuerungService.werteIntervallAus(...)` läuft viertelstündlich, schreibt einen
  `zev.steuerentscheid` und **schaltet nichts** (Trockenlauf).

  > **Die Prognose ist seit dem 25.09.2026 umgesetzt — die Ladeplanung noch nicht.** Bewusst in
  > dieser Reihenfolge: Einstrahlung wird abgerufen (FR-2), in eine erwartete Erzeugung umgerechnet
  > (FR-3) und im Diagramm neben der gemessenen Produktion gezeigt (FR-7). Sie **entscheidet
  > nichts**. Erst wenn sich über einige Wochen zeigt, dass die Vorhersage für diese Anlage taugt,
  > lohnt sich die Merit-Order darauf — trüge sie nicht, wäre eine Ladeplanung auf schlechten
  > Zahlen schlechter als das heutige Regelwerk.
  >
  > Umgesetzt sind damit: FR-2 (Abruf, Job, Tabelle), FR-3 **ohne Lastprofil** (gelernter Faktor,
  > erwartete Erzeugung), FR-6 (Standort- und Ausrichtungskonfiguration), FR-7 (Endpunkt, Diagramm,
  > Tabellenspalten, Namensnennung) und die davon berührten Übersetzungen aus FR-9.
  >
  > **Offen:** FR-1 (Merit-Order), FR-3-Lastprofil, FR-4 (Rückfall-Kennzeichen), FR-5 (die Spalten
  > am Entscheid samt DDL für `regel`) — und die sieben Übersetzungen, die zu diesen Teilen
  > gehören (`VERFAHREN`, `MERIT_ORDER`, `REGEL`, `RANG`, `RANG_BENOETIGT`, `KAPAZITAET_FREI`,
  > `OHNE_PROGNOSE`). FR-8 ist **zurückgenommen**, nicht offen.
  >
  > Das Lastprofil aus FR-3 fehlt ebenfalls noch — es wird erst für den erwarteten **Überschuss**
  > gebraucht, und der erst für die Merit-Order.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Ablauf / Flow

Je Intervall, im selben Job-Lauf wie heute (`0 6,21,36,51 * * * *`):

1. **Ladezustand lesen** — letzter `SOC` aus `zev.geraetezustand` (`Specs/Gerätezustand.md`).
2. **Freie Kapazität** bestimmen: `kapazitaet * (1 − soc/100)`.

   > **`kapazitaet` ist die nutzbare Kapazität** aus der Konfiguration, und `soc` bezieht sich auf
   > dieselbe Grösse — beides so, wie der Wechselrichter den Ladezustand meldet. Der
   > Mindest-Ladezustand aus `SOC_TIEF` zählt **nicht** ab: Er ist eine Untergrenze fürs Entladen,
   > keine Reserve, die beim Laden fehlte.
3. **Resttag prognostizieren** — je Intervall des Ortstages **ab dem ausgewerteten**
   (einschliesslich): erwarteter PV-Überschuss (FR-3) und Einspeisepreis (aus
   `zev.preiszeitreihe`).

   > **„Ausgewertet", nicht „laufend".** Der Job läuft zur Minute 6 und wertet das eben
   > abgeschlossene Intervall aus — um 12:06 also 11:45–12:00 (`SteuerungJob`). Das ist ein
   > vergangenes Intervall. Es **gehört in die Merit-Order**, denn über seinen Entscheid wird
   > gerade befunden; der Resttag beginnt bei ihm, nicht bei „jetzt". Der Unterschied ist eine
   > Viertelstunde — aber ohne die Festlegung wäre nicht bestimmt, ob das Intervall, um das es
   > geht, überhaupt in der Auswahl steht.
4. **Merit-Order bilden:** Intervalle nach Einspeisepreis **aufsteigend** sortieren.
5. **Auffüllen**, bis die freie Kapazität gedeckt ist. Die so gewählten Intervalle sind der
   **Ladeplan**.
6. **Entscheid für das ausgewertete Intervall:** liegt es im Plan → `batterieladung = FREI`, sonst
   `GESPERRT`.
7. **Entscheid festhalten** wie bisher, um die Plangrössen erweitert (FR-5).

> **Warum eine Merit-Order und kein Solver.** Solange alle gespeicherten Kilowattstunden denselben
> Wert haben (`speicherwert`) und keine Leistungsgrenze bindet, ist das Auffüllen nach Preis
> **beweisbar optimal** — es liefert dasselbe Ergebnis wie ein lineares Programm, in zwanzig Zeilen
> und ohne Abhängigkeit. Ein LP wird erst nötig, wenn Nebenbedingungen koppeln (siehe §7).

**Welche Regeln neben der Merit-Order bestehen bleiben:**

| Regel | unter Merit-Order |
|---|---|
| `PREIS_NEGATIV` | **bleibt**, aber nur für die **Einspeisung**: Sie setzt `einspeisung = GESPERRT` und überlässt die Ladung der Merit-Order. Negative Preise stehen dort ohnehin ganz vorn. |
| `SOC_TIEF` | **bleibt, vorgeschaltet.** Fällt der Ladezustand unter den Mindestwert, ist `batterieladung = FREI`, ohne dass die Merit-Order gefragt wird. Die Begründung gilt unverändert: Ein leerer Speicher deckt den Hausbedarf aus dem Netz zu rund 0.35 CHF/kWh, und dagegen steht kein Gewinn aus dem Warten an. |
| `EINSPEISEN_LOHNT` | **entfällt.** Ihre Aussage — „bei diesem Preis lohnt Einspeisen mehr als Speichern" — ist in der Merit-Order enthalten: Ein Intervall mit hohem Preis landet hinten und fällt aus dem Plan. Die separate Regel wäre eine zweite Antwort auf dieselbe Frage. |
| `WARTEN_AUF_TAL`, Mindestabstand, `KEIN_UEBERSCHUSS`, `LADEN` | **entfallen.** Sie sind die Näherung, die das Verfahren ersetzt. |

> **`PREIS_NEGATIV` bricht damit die Auswertung nicht mehr ab**, anders als heute
> (`Specs/Einspeisesteuerung.md`, FR-2): Sie entscheidet nur noch über die Einspeisung, die Ladung
> bestimmt die Merit-Order. Das ist eine Verhaltensänderung und keine blosse Umstellung.

### FR-2: Einstrahlungsprognose beschaffen

Die erwartete Einstrahlung kommt von **Open-Meteo**, Modell `meteoswiss_icon_ch1` (MeteoSchweiz,
1 km Gitter).

**Abgefragt wird `global_tilted_irradiance`** — die Einstrahlung auf die **geneigte Modulfläche**
in W/m², im **15-Minuten-Raster**:

```
https://api.open-meteo.com/v1/forecast
  ?latitude=<breite>&longitude=<laenge>
  &tilt=<neigung>&azimuth=<azimut>
  &minutely_15=global_tilted_irradiance
  &timezone=Europe/Zurich
  &models=meteoswiss_icon_ch1
  &forecast_days=2
```

> **Warum `global_tilted_irradiance` und nicht `cloud_cover`.** Aus Bewölkung die Erzeugung zu
> schätzen hiesse, ein Strahlungsmodell nachzubauen — Sonnenstand, Einfallswinkel, diffuser Anteil.
> Die API liefert das Ergebnis dieser Rechnung direkt und mit dem Wettermodell darin.

**Ein eigener Job holt die Prognose stündlich** und legt sie in `zev.einstrahlungsprognose` ab
(Upsert auf `org_id, zeit`). Der Steuerungs-Job liest **nur aus der Datenbank**.

> **Diese Entkopplung ist die Bedingung für den externen Dienst überhaupt.** Hinge der Abruf im
> Job-Pfad, nähme jede Störung von Open-Meteo die Steuerung mit. So gilt: API nicht erreichbar →
> die letzte bekannte Prognose gilt weiter; gar keine Prognose → Rückfall auf die Regelkaskade
> (FR-4). Dasselbe Muster wie bei `zev.preiszeitreihe`, die ebenfalls von aussen kommt und
> gespeichert wird.

> **Zeitbezug — hier ist genau hinzusehen.** Mit `timezone=Europe/Zurich` liefert die API
> **Ortszeit**, und der Zeitstempel eines `minutely_15`-Eintrags ist der **Beginn** des Intervalls.
> Das ist derselbe Bezug wie `steuerentscheid.zeit_von` und damit ohne Umrechnung verwendbar —
> **anders** als `messwerte.zeit` (Intervall**ende**) und `preiszeitreihe.zeit_von` (UTC). In
> diesem Feature sind schon drei Zeitkonventionen nebeneinander zum Fehler geworden
> (`Specs/Einspeisesteuerung.md`, FR-3); die Spalte trägt deshalb einen Kommentar, der den Bezug
> ausschreibt.

**Abgerufen wird für zwei Tage** (`forecast_days=2`), damit ein Ausfall über Nacht nicht sofort
zum Rückfall führt.

**Bedarf:** 24 Aufrufe je Tag und Mandant. Der freie Tarif erlaubt 10'000.

### FR-3: Von der Einstrahlung zur erwarteten Erzeugung

**Der Umrechnungsfaktor wird gelernt, nicht konfiguriert:**

```
faktor = Σ erzeugung_ist(i) / Σ gti(i)        über die letzten n Tage, nur Intervalle mit gti > 0
prognose(i) = gti(i) * faktor
```

* `erzeugung_ist` ist die **verrechnete** Erzeugung:
  `produktion + speicher_ladung − speicher_entladung`, nie negativ.

  > **Nicht der blosse Zählerwert.** Der Erzeugungszähler sieht nur, was der Hybrid-Wechselrichter
  > wechselstromseitig abgibt; was gleichstromseitig in die Batterie fliesst, passiert ihn nie
  > (`Specs/Einspeisesteuerung.md`, FR-5b). An zwei gemessenen Tagen fehlten so 12 % und 8 %.
  > Ein daraus gelernter Faktor wäre zu tief — und ein zu tiefer Faktor liesse die spätere
  > Merit-Order dauerhaft „die Restsonne reicht nicht" schliessen und grundsätzlich laden. Das
  > Verfahren wirkte wie abgeschaltet, ohne dass ein Fehler sichtbar wäre.
  >
  > **Ohne `SPEICHER`-Einheit** ist der Zuschlag 0, und es bleibt beim Zählerwert — dort ist er
  > auch richtig.
* Das Verhältnis der Summen ist eine Regression durch den Ursprung, gewichtet nach Einstrahlung —
  helle Intervalle zählen mehr, und genau auf die kommt es an.

> **Warum gelernt und nicht aus Nennleistung gerechnet.** Ein Faktor aus kWp und Performance Ratio
> wäre eine Annahme; der gelernte Faktor enthält **Verschattung, Verschmutzung, Modulalterung und
> Ausrichtungsfehler** ohne dass sie jemand erfassen muss. Er korrigiert sich zudem von selbst,
> wenn sich an der Anlage etwas ändert. Der Preis dafür: Er braucht Historie (FR-4).

> **Zeitversatz beachten — für Faktor und Lastprofil gleichermassen.** `messwerte.zeit` trägt das
> Intervall**ende**, `einstrahlungsprognose.zeit` den **Beginn**. Ohne die Verschiebung um eine
> Viertelstunde wird der Faktor versetzt gelernt, und das sieht man der Zahl nicht an. Dieselbe
> Falle wie in FR-2.

**Lastprofil** je Intervall aus der eigenen Historie: **Median** über die gleichen Wochentage
innerhalb der letzten `historieTage` (bei der Vorgabe 28 also vier Stichproben je Intervall) aus
`zev.messwerte` (`CONSUMER`). Median statt Mittel, weil ein einzelner Ausreisser sonst das ganze
Profil verschiebt.

> **Bezugsgrösse ist der Zeitraum, nicht die Anzahl Stichproben.** `historieTage` zählt Kalendertage
> zurück; wie viele gleiche Wochentage darin liegen, ergibt sich daraus. Unter **7 Tagen** liegt
> keiner darin — dann gibt es kein Lastprofil und es gilt der Rückfall (FR-4).

**Erwarteter Überschuss** je Intervall: `max(0, prognose − lastprofil)`.

> **Die Genauigkeit muss nicht gross sein.** Gebraucht wird nicht eine kWh-genaue Vorhersage,
> sondern die Antwort auf „reicht die Restsonne, um die freie Kapazität zu füllen". Am 21.09.2026
> standen 18 kWh erwarteter Überschuss gegen 10.6 kWh freie Kapazität — diese Antwort ist gegen
> 30 % Prognosefehler robust.

### FR-4: Rückfall auf die Regelkaskade

Liegt **keine brauchbare Prognose** vor, entscheidet die bestehende Regelkaskade
(`Specs/Einspeisesteuerung.md`, FR-2). Das gilt bei:

* fehlender Standort- oder Ausrichtungskonfiguration (FR-6),
* fehlender Einstrahlungsprognose für den Resttag,
* fehlendem Umrechnungsfaktor (zu wenig Historie, siehe unten),
* fehlender `SPEICHER`-Einheit, fehlendem Ladezustand oder fehlender Batteriekapazität
  (**auch bei Kapazität 0** — sie ergäbe eine freie Kapazität von 0 und damit dauerhaft einen
  leeren Plan),
* **fehlenden Preisen für den Resttag** — ohne sie gibt es keine Reihenfolge, und eine willkürliche
  wäre schlimmer als das Regelwerk,
* fehlendem Lastprofil (siehe FR-3).

**Der Umrechnungsfaktor braucht mindestens 150 Intervalle mit `gti > 0`** im Lernfenster — grob
drei sonnige Tage. Darunter wäre er aus zu wenigen Punkten geschätzt, und ein zu kleiner Faktor
liesse die Steuerung dauerhaft zu früh laden.

Der Entscheid hält fest, **welches Verfahren** ihn gefällt hat (FR-5). Ohne diese Angabe liesse
sich ein Protokoll später nicht lesen: Dieselbe Spalte `regel` trüge zwei verschiedene Bedeutungen.

### FR-5: Persistierung

**Neue Tabelle `zev.einstrahlungsprognose`:**

| Spalte | Typ | Bedeutung |
|---|---|---|
| `id` | BIGINT | Schlüssel |
| `org_id` | BIGINT | Mandant (`@Filter(orgFilter)`) |
| `zeit` | TIMESTAMP | **Beginn** des 15-Minuten-Intervalls, **Ortszeit** |
| `gti` | NUMERIC(8,2) | Einstrahlung auf die Modulfläche in W/m² |
| `abgerufen_am` | TIMESTAMP | wann die Prognose geholt wurde |

Alle Spalten **NOT NULL**; `gti` mit `CHECK (gti >= 0)`. `org_id` wird **serverseitig** gesetzt —
der Abruf-Job kennt den Mandanten, ein Client ist daran nicht beteiligt.

Unique auf `(org_id, zeit)`; Upsert beim Abruf. `abgerufen_am` bleibt, weil sonst nicht
unterscheidbar wäre, ob ein Wert von heute früh oder von vorgestern stammt.

**`zev.steuerentscheid` wird erweitert** (alle Spalten **nullable**, weil sie beim Rückfall und bei
Entscheiden aus der Zeit davor fehlen):

| Spalte | Typ | Bedeutung |
|---|---|---|
| `verfahren` | VARCHAR(20) | `MERIT_ORDER` oder `REGEL` — welches Verfahren entschied |
| `regel` | (bestehend) | trägt bei Merit-Order den neuen Wert **`LADEPLAN`** — die Spalte ist `NOT NULL` mit CHECK-Constraint (V145, erweitert in V160), der Wert braucht also eine **DDL-Migration**, kein blosses Enum |
| `prognose_ueberschuss` | NUMERIC(12,3) | erwarteter Überschuss **dieses** Intervalls in kWh |
| `gti` | NUMERIC(8,2) | Einstrahlung in W/m², die dem Entscheid zugrunde lag |
| `prognose_faktor` | NUMERIC(12,8) | gelernter Umrechnungsfaktor zum Zeitpunkt des Entscheids — **acht** Nachkommastellen, weil der Wert in der Grössenordnung 0.005 liegt und bei sechs nur vier signifikante Stellen blieben |
| `rang` | INTEGER | Platz des Intervalls in der Merit-Order des Resttages |
| `rang_benoetigt` | INTEGER | wie viele Intervalle gebraucht wurden, um die Kapazität zu decken |
| `kapazitaet_frei` | NUMERIC(12,3) | freie Kapazität in kWh beim Entscheid |

> **Warum `rang` und `rang_benoetigt` statt eines Kennzeichens.** „Rang 34, gebraucht werden 12"
> erklärt den Entscheid vollständig und macht ihn nachprüfbar — ein blosses `GESPERRT` nicht. Das
> Entscheidungsprotokoll ist der Kernwert der Ausbaustufe (`Specs/Einspeisesteuerung.md`, FR-5);
> es soll durch das neue Verfahren **gewinnen**, nicht verlieren.

Multi-Tenancy unverändert: `org_id`, `@Filter(orgFilter)`, Upsert auf `(org_id, zeit_von)`.

### FR-6: Konfiguration je Mandant

Neu im Block `steuerung` von `organisation.konfiguration` (`jsonb`, keine Schema-Migration):

| Feld | Eingabe | Validierung |
|---|---|---|
| Breitengrad | Zahl, ° | −90 bis 90; leer → Rückfall |
| Längengrad | Zahl, ° | −180 bis 180; leer → Rückfall |
| Azimut der Anlage | Zahl, ° | 0 = Süd, −90 = Ost, 90 = West (Open-Meteo-Konvention); −180 bis 180 |
| Neigung | Zahl, ° | 0 = flach, 90 = senkrecht; 0–90 |
| Tage für Lastprofil und Faktor | Ganzzahl | 1–56; Vorgabe 28 |

> **Die Azimut-Konvention ist die von Open-Meteo**, nicht die meteorologische (0 = Nord). Wer sie
> verwechselt, richtet die Anlage rechnerisch nach Norden — die Prognose wäre dann dauerhaft zu
> tief, der gelernte Faktor gliche es teilweise aus, und der Fehler bliebe unbemerkt. Der
> Feldhinweis schreibt die Konvention deshalb aus.

> **Nennleistung und Performance Ratio entfallen** — beides steckt im gelernten Faktor (FR-3).

> **Warum der Standort nicht aus der Adresse abgeleitet wird.** Eine Geokodierung wäre ein zweiter
> externer Dienst für eine einmalige Angabe. Zwei Zahlen aus der Karte abzulesen ist weniger Aufwand
> als die Fehlerbehandlung dafür.

### FR-7: Anzeige

**Endpunkt:** `GET /api/einspeisesteuerung/prognose?datum=<ISO-Datum>` liefert die Prognose eines
Ortstages (Einstrahlung, erwartete Erzeugung, Faktor je Intervall). Berechtigung wie die übrigen
Endpunkte der Steuerung: `@PreAuthorize("hasAuthority('tarife:manage')")`, dazu die Flag-Prüfung.

> **Eigener Endpunkt, nicht Teil der Entscheide.** Die Prognose beschreibt die **Zukunft**;
> Entscheide gibt es nur für abgeschlossene Intervalle. Hinge sie daran, wäre der Resttag nie
> sichtbar — also genau der Teil, um den es geht.

Die Tagesansicht der Einspeisesteuerung (`Specs/Einspeisesteuerung.md`, FR-5) bleibt, ergänzt um:

* Spalte **Verfahren** in der Protokolltabelle — sonst stünden Merit-Order- und Regel-Entscheide
  ununterscheidbar nebeneinander.
* Spalten **Rang**, **benötigt**, **Einstrahlung**, **Prognose** und **Kapazität frei**.

**Im Diagramm: die Prognose als gestrichelte Kurve neben der gemessenen Produktion**, auf
**derselben kWh-Achse**. Damit lässt sich am Abend unmittelbar ablesen, wie gut die Vorhersage war
— die einzige Rückmeldung, die das Verfahren über sich selbst gibt.

> **Gezeichnet wird die erwartete Erzeugung (kWh), nicht die Einstrahlung (W/m²).** Zwei Gründe:
>
> 1. **Vergleichbarkeit.** Nur die erwartete Erzeugung lässt sich direkt gegen die gemessene
>    Produktionskurve halten. Die Einstrahlung liefe in einer anderen Grössenordnung und
>    beantwortete die Frage „lag die Prognose richtig" nur über einen Zwischenschritt im Kopf.
> 2. **Die Achsen sind voll.** Das Diagramm führt bereits drei y-Achsen (Preis links, kWh rechts,
>    Ladezustand rechts aussen). Eine vierte für W/m² hat dort keinen Platz — der rechte Rand
>    musste für die dritte schon von 60 auf 115 Pixel wachsen, und der Tooltip war mit dreizehn
>    Zeilen an der Grenze (`Specs/Einspeisesteuerung.md`, FR-5).
>
> **Der Tooltip ist damit an der Grenze.** Er trug schon dreizehn Zeilen bei 11 px, und ECharts
> schneidet einen zu hohen Tooltip **oben** ab statt ihn scrollen zu lassen
> (`Specs/Einspeisesteuerung.md`, FR-5). Die zwei neuen Zeilen erscheinen deshalb **nur**, wenn für
> das Intervall eine Prognose vorliegt. Kommt eine weitere Grösse dazu, ist Weglassen die richtige
> Antwort, nicht noch kleinere Schrift.

> **Die Einstrahlung bleibt trotzdem sichtbar** — als eigene Spalte in der Protokolltabelle und im
> Tooltip. Dort steht sie neben dem Umrechnungsfaktor, und beide zusammen erklären, wie aus
> W/m² die erwartete Kilowattstunde wurde. Wer der Prognose misstraut, kann das nachrechnen.

* Der Hinweis auf den Trockenlauf bleibt unverändert stehen.

**Namensnennung:** Open-Meteo-Daten stehen unter **CC BY 4.0**. Die Anwendung nennt die Quelle im
bestehenden Lizenz-Bereich (`LizenzenComponent`) — „Wetterdaten von Open-Meteo.com (CC BY 4.0)",
mit Verweis auf MeteoSchweiz als Modellbetreiber.

### FR-8: Rückrechnung — bewusst **nicht** mit historischer Prognose

`POST /simulation` und `GET /entscheide/simuliert` (`Specs/Einspeisesteuerung.md`, FR-6/6a) rechnen
das Merit-Order-Verfahren **nicht** nach. Für zurückliegende Tage liefert die Rückrechnung
weiterhin Regel-Entscheide und weist das über `verfahren = REGEL` aus.

> **Warum das eine Einschränkung ist und keine Auslassung.** `zev.einstrahlungsprognose` hält je
> Intervall **eine** Zeile (Upsert auf `org_id, zeit`), und der stündliche Abruf deckt mit
> `forecast_days=2` auch bereits vergangene Intervalle ab. Für einen zurückliegenden Zeitpunkt
> steht dort also die **zuletzt** geholte Fassung — und die kennt das Wetter, das inzwischen
> eingetreten ist. Eine Rückrechnung damit hätte Wissen, das zum Entscheidungszeitpunkt nicht
> vorlag, und **überschätzte den Nutzen des Verfahrens systematisch**. Als Kalibrierwerkzeug wäre
> sie damit nicht bloss ungenau, sondern irreführend.
>
> **Der Ausweg wäre, Revisionen zu versionieren** — `abgerufen_am` in den Schlüssel, je Intervall
> also bis zu 24 Zeilen am Tag. Das ist bewusst **nicht** umgesetzt: Es kostet Speicher für etwas,
> das erst gebraucht wird, wenn die Merit-Order überhaupt entscheidet. Kommt dieser Schritt, ist
> die Versionierung **Voraussetzung** dafür, nicht Beiwerk.

**Der gelernte Faktor hat dieselbe Grenze in schwächerer Form.** Er wird über die Tage **vor** dem
gefragten ermittelt, nutzt also keine Messwerte des Tages selbst — insoweit ist er rekonstruierbar.
Er stützt sich aber auf Prognosewerte, die inzwischen überschrieben sein können.

### FR-9: Übersetzungen

Neue Schlüssel (Flyway, `ON CONFLICT (key) DO NOTHING`), deutsch **mit Umlauten**:

| Schlüssel | Deutsch | Englisch |
|---|---|---|
| `LADEPLANUNG_VERFAHREN` | Verfahren | Method |
| `LADEPLANUNG_MERIT_ORDER` | Ladeplan | Charge plan |
| `LADEPLANUNG_REGEL` | Regelwerk | Rule set |
| `LADEPLANUNG_RANG` | Rang | Rank |
| `LADEPLANUNG_RANG_BENOETIGT` | benötigt | needed |
| `LADEPLANUNG_PROGNOSE` | Erwartete Erzeugung | Expected generation |
| `LADEPLANUNG_EINSTRAHLUNG` | Einstrahlung | Irradiance |
| `LADEPLANUNG_KAPAZITAET_FREI` | Kapazität frei | Free capacity |
| `LADEPLANUNG_BREITENGRAD` | Breitengrad | Latitude |
| `LADEPLANUNG_LAENGENGRAD` | Längengrad | Longitude |
| `LADEPLANUNG_AZIMUT` | Ausrichtung (0 = Süd, −90 = Ost, 90 = West) | Orientation (0 = south, −90 = east, 90 = west) |
| `LADEPLANUNG_NEIGUNG` | Neigung (0 = flach, 90 = senkrecht) | Tilt (0 = flat, 90 = vertical) |
| `LADEPLANUNG_HISTORIE_TAGE` | Tage für Lastprofil und Umrechnungsfaktor | Days for load profile and conversion factor |
| `LADEPLANUNG_OHNE_PROGNOSE` | Ohne Prognose — entschieden nach Regelwerk | No forecast — decided by rule set |
| `LADEPLANUNG_PROGNOSE_FEHLER` | Die Einstrahlungsprognose konnte nicht abgerufen werden | Could not fetch the irradiance forecast |
| `LADEPLANUNG_QUELLE` | Wetterdaten von Open-Meteo.com (CC BY 4.0), Modell MeteoSchweiz ICON-CH1 | Weather data from Open-Meteo.com (CC BY 4.0), model MeteoSwiss ICON-CH1 |

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)

**Merit-Order**

* [ ] Reicht der erwartete Überschuss des Resttages **nicht**, um die freie Kapazität zu füllen,
      ist das laufende Intervall im Plan — es wird geladen, auch bei hohem Preis.
* [ ] Reicht er, sind nur die günstigsten Intervalle im Plan; ein teureres davor wird gesperrt.
* [ ] Bei gleicher Kapazität und gleichem Überschuss entscheidet **allein** der Einspeisepreis.
* [ ] Ist die Batterie voll (freie Kapazität 0), ist der Plan **leer** und jedes Intervall gesperrt.
* [ ] Ein Intervall **ohne** erwarteten Überschuss belegt keinen Platz im Plan.
* [ ] **Bei negativem Preis** ist `einspeisung = GESPERRT`, über die Ladung entscheidet die
      Merit-Order — nicht mehr pauschal `FREI` wie heute.
* [ ] **Unter dem Mindest-Ladezustand** ist `batterieladung = FREI`, ohne dass der Plan gefragt wird.
* [ ] Der Plan umfasst das **ausgewertete** Intervall und alle folgenden des gleichen Ortstages —
      keine früheren.

**Der Fall, der das Verfahren ausgelöst hat**

* [ ] **18.09./21.09.-Lage** (Tal am Nachmittag, Restsonne reicht): gesperrt bis zum Tal — wie die
      Regelkaskade.
* [ ] **Dieselbe Lage mit bewölktem Nachmittag** (Restsonne reicht nicht): das laufende Intervall
      ist im Plan, es wird **vorgeladen**. Genau hier unterscheiden sich die beiden Verfahren.

**Prognose-Abruf**

* [ ] Der Abruf-Job schreibt je Intervall **einen** Datensatz; ein zweiter Lauf überschreibt ihn,
      statt einen weiteren anzulegen.
* [ ] `zeit` trägt den **Beginn** des Intervalls in **Ortszeit** und ist ohne Umrechnung mit
      `steuerentscheid.zeit_von` vergleichbar.
* [ ] Ist die API nicht erreichbar, bleibt die vorhandene Prognose stehen; der Fehler erscheint als
      Systemmeldung und der Job läuft beim nächsten Mal weiter.
* [ ] Der Steuerungs-Job ruft **keine** externe Schnittstelle auf.
* [ ] Der Abruf erfolgt je Mandant mit dessen Standort und Ausrichtung.

**Umrechnungsfaktor**

* [ ] Der Faktor ist das Verhältnis der Summen (gemessene Erzeugung zu Einstrahlung) über die
      letzten *n* Tage, nur über Intervalle mit `gti > 0`.
* [ ] Mit weniger als **150 Intervallen mit `gti > 0`** im Lernfenster gibt es **keinen** Faktor
      → Rückfall.
* [ ] Bei `historieTage < 7` gibt es **kein** Lastprofil → Rückfall.
* [ ] Der erwartete Überschuss ist nie negativ.
* [ ] Das Lastprofil ist der **Median** der letzten *n* gleichen Wochentage, nicht der Mittelwert.

**Rückfall**

* [ ] Fehlt Standort, Ausrichtung, Prognose, Faktor, Lastprofil, Kapazität, Speicher-Einheit,
      Ladezustand **oder Preise für den Resttag**, entscheidet die Regelkaskade — und der Entscheid
      trägt `verfahren = REGEL`.
* [ ] Eine Batteriekapazität von **0** wirkt wie eine fehlende.
* [ ] Ein Entscheid nach Merit-Order trägt `verfahren = MERIT_ORDER` **und** Rang, benötigte
      Anzahl, Einstrahlung, Prognose, Faktor und freie Kapazität.
* [ ] Aus Einstrahlung und Faktor lässt sich die erwartete Erzeugung nachrechnen — die drei Werte
      stehen im selben Datensatz.

**Anzeige**

* [ ] Die Tabelle nennt je Intervall das Verfahren; Merit-Order- und Regel-Entscheide sind
      unterscheidbar.
* [ ] Das Diagramm zeigt die **erwartete Erzeugung** als eigene, gestrichelte Linie neben der
      gemessenen Produktion, auf **derselben** kWh-Achse.
* [ ] Das Diagramm bekommt **keine vierte y-Achse**.
* [ ] Die **Einstrahlung in W/m²** steht in der Protokolltabelle und im Tooltip, nicht im Diagramm.
* [ ] Ein Tag ohne Prognose zeigt die Linie **nicht**, statt sie auf 0 zu zeichnen.
* [ ] Fehlt die Prognose nur für einzelne Intervalle, setzt die Linie dort **aus**, statt über die
      Lücke gezogen zu werden.
* [ ] Der Lizenz-Bereich nennt Open-Meteo und die CC-BY-4.0-Lizenz.

**Rückrechnung**

* [ ] Die Rückrechnung liefert für zurückliegende Tage **Regel-Entscheide** und kennzeichnet sie
      als solche.
* [ ] Sie stützt sich **nicht** auf `zev.einstrahlungsprognose` — dort stünde die nachträglich
      geholte Fassung.

**Sicherheit und Flag**

* [ ] Alle Endpunkte verlangen `tarife:manage` (403 ohne).
* [ ] Bei ausgeschaltetem Feature-Flag `EINSPEISESTEUERUNG` antworten sie mit `403`, und der
      Abruf-Job läuft nicht.
* [ ] Es wird **nichts** geschaltet: kein MQTT-Publish, kein Schreibpfad zur Anlage.

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Ein Job-Lauf je Organisation bleibt unter **200 ms**. Die Merit-Order sortiert höchstens 96
  Einträge; Prognose, Lastprofil und die Historie für den Faktor werden **je einmal** aggregiert
  gelesen, nicht je Intervall.
* **Der Faktor kostet am meisten:** Er liest `historieTage` Tage Messwerte und Prognose (bei 28
  Tagen rund 2'700 Intervalle je Reihe), und zwar bei **jedem** Lauf. Ein Zwischenspeicher je Tag
  wäre naheliegend, ist aber nicht festgelegt — erst messen, dann optimieren.
* Der Abruf-Job läuft **stündlich** und ausserhalb des Steuerungs-Laufs. Zeitüberschreitung beim
  HTTP-Aufruf: 10 s, danach Abbruch mit Meldung.
* Die Rückrechnung über 366 Tage bleibt in derselben Grössenordnung wie heute.

### NFR-2: Sicherheit
* Endpunkte wie bisher: `@PreAuthorize("hasAuthority('tarife:manage')")`, Route über `AuthGuard`
  mit `data.permissions`.
* Die Konfiguration je Mandant ist nur mit `einstellungen:write` änderbar (`org_admin`,
  `zev_admin`).
* Multi-Tenancy: Prognose, Faktor und Plan werden **je Mandant** gerechnet; `zev.einstrahlungs-
  prognose` trägt `org_id` und den Hibernate-Filter.
* **Nach aussen gehen nur Koordinaten und Ausrichtung** — keine Verbrauchs-, Erzeugungs- oder
  Mieterdaten. Der Abruf ist ein `GET` ohne Authentifizierung und ohne Nutzlast.

### NFR-3: Kompatibilität
* Die bestehende Regelkaskade bleibt vollständig erhalten und ist der Rückfall — kein Mandant
  verliert Funktion, wenn er die neue Konfiguration nicht erfasst.
* Alte Entscheide bleiben lesbar; die neuen Spalten sind dort leer.
* Keine Änderung an `zev.messwerte`, `zev.geraetezustand` oder `zev.preiszeitreihe`.
* **Lizenz:** Der freie Tarif von Open-Meteo gilt für **nicht-kommerzielle** Nutzung (Stand
  25.09.2026 bestätigt) mit 10'000 Aufrufen je Tag und ohne Verfügbarkeitszusage. Wird ZEV an
  Dritte abgegeben, ist ein kostenpflichtiges Abo nötig — technisch ändert sich dabei nur der
  Endpunkt.

### NFR-4: Nachvollziehbarkeit
* Jeder Entscheid trägt die Grössen, aus denen er entstand (FR-5) — wie bisher gilt: Bei einer
  Steuerung ist das *Warum* wichtiger als das *Was*.
* Der Job protokolliert je Lauf auf `INFO`: Verfahren, freie Kapazität, Rang, benötigte Anzahl,
  Umrechnungsfaktor.
* Der Abruf-Job protokolliert Anzahl geschriebener Intervalle und den abgedeckten Zeitraum.

## 5. Edge Cases & Fehlerbehandlung

* **API nicht erreichbar oder Zeitüberschreitung:** Die vorhandene Prognose bleibt gültig;
  Systemmeldung. Erst wenn sie den Resttag nicht mehr abdeckt, greift der Rückfall.
* **API liefert unerwartete Struktur:** Der Abruf wird verworfen, nichts überschrieben. Eine halb
  geschriebene Prognose wäre schlimmer als eine veraltete.
* **Keine Preise für den Resttag:** Ohne Preise gibt es keine Reihenfolge → Rückfall, nicht etwa
  eine willkürliche Auswahl.
* **Batterie voll:** Plan leer, alles gesperrt — der Überschuss geht ins Netz.
* **Batterie leer und wenig Sonne:** Alle Intervalle im Plan; es wird durchgehend geladen.
* **Letztes Intervall des Tages:** Der Resttag besteht nur aus dem ausgewerteten Intervall; der
  Plan enthält also höchstens dieses eine.
* **Zeitumstellung:** Wie in `Specs/Einspeisesteuerung.md` §5 — die doppelte Stunde im Herbst
  trägt denselben Schlüssel; Prognose und Plan werden für den zweiten Durchgang überschrieben.
* **Negative Preise im Resttag:** Sie stehen in der Merit-Order ganz vorn und werden zuerst
  gewählt — fachlich richtig. `PREIS_NEGATIV` sperrt davon unabhängig die **Einspeisung**.
* **Prognose grob daneben:** Der Entscheid bleibt gültig; das Protokoll hält Prognose und
  gemessenen Wert nebeneinander, sodass sich der Fehler im Nachhinein beziffern lässt.
* **Standort oder Ausrichtung geändert:** Alte Prognosezeilen bleiben stehen und gehen weiter in
  den Faktor ein, ohne zu wissen, zu welcher Konfiguration sie gehören. **Bewusst hingenommen** —
  eine Konfigurationsänderung ist ein seltener Einzelfall, und der Faktor wächst binnen
  `historieTage` wieder heraus. Wer sofort saubere Werte will, löscht die Tabelle für den Mandanten.
* **Aufbewahrung:** `zev.einstrahlungsprognose` wächst um 96 Zeilen je Tag und Mandant — rund
  35'000 im Jahr. Es gibt **keine** Bereinigung; die Reihe ist die einzige Grundlage, um später zu
  beurteilen, wie gut die Prognose war.
* **Schnee auf den Modulen:** Die Einstrahlung ist hoch, die Erzeugung null. Der gelernte Faktor
  sinkt über die Tage und fängt es teilweise auf — aber verzögert. Als offene Frage vermerkt.

## 6. Abhängigkeiten & betroffene Funktionalität

* **Voraussetzungen:**
  * `Specs/Einspeisesteuerung.md` — Job, Entscheid-Tabelle, Tagesansicht, Rückrechnung
  * `Specs/Gerätezustand.md` — der Ladezustand (`zev.geraetezustand`, Grösse `SOC`)
  * `Specs/Batteriespeicher.md` — die Einheit vom Typ `SPEICHER`
  * `zev.preiszeitreihe` — Einspeisepreise, in **UTC** geführt
  * `Specs/Einstellungen.md` — die Maske, in der die neue Konfiguration gepflegt wird
  * Ausgehende HTTPS-Verbindung zu `api.open-meteo.com` aus dem Backend-Container

* **Betroffener Code:**
  * `SteuerungService` — Ablauf je Intervall, Rückrechnung
  * `SteuerRegelService` — bleibt unverändert als Rückfall
  * **neu:** `EinstrahlungsprognoseAbrufService` und `EinstrahlungsprognoseJob` (Abruf, Upsert),
    `ProduktionsprognoseService` (Faktor, Lastprofil, erwarteter Überschuss), `LadeplanService`
    (Merit-Order)
  * `Specs/Berechtigungen.md` — der neue Endpunkt gehört in die Matrix
  * **neu:** Entity, Repository und Migration für `zev.einstrahlungsprognose`
  * `Steuerentscheid` / `SteuerentscheidDTO` / Upsert — sechs neue Spalten **und** ein neuer
    erlaubter Wert für `regel` (DDL, siehe FR-5)
  * `SteuerKonfigurationDTO` — fünf neue Felder
  * Frontend: Tagesansicht (Tabelle, Diagramm), Einstellungsmaske, Lizenz-Bereich

* **Datenmigration:** Keine. Neue Spalten sind nullable, alte Entscheide bleiben unverändert. Die
  Prognose-Tabelle beginnt leer und füllt sich ab dem ersten Abruf.

## 7. Abgrenzung / Out of Scope

* **Das Schalten selbst.** Weiterhin Trockenlauf — kein MQTT-Publish, kein Wechselrichter-Zugriff.
  Wie es ginge, steht in `Specs/Solinteg_Modbus_Register.md`.
* **Ein Solver (LP/MILP).** Die Merit-Order ist optimal, solange gespeicherte Kilowattstunden
  gleich viel wert sind und keine Leistungsgrenze bindet. Ein LP wird erst nötig bei:
  Ladeleistungsgrenzen, Netzladung bei negativen Preisen, Degradationskosten je Zyklus, oder
  gesteuerter **Entladung**. Dann käme `ojAlgo` in Frage (Apache 2.0, reines Java, kein JNI) — aber
  erst dann, nicht vorsorglich.
* **Eigenes Strahlungsmodell.** Die Klarhimmel-Rechnung aus Sonnenstand entfällt; die API liefert
  die Einstrahlung auf die Modulfläche direkt. Als Rückfall bei API-Ausfall wäre sie erwägenswert,
  steht aber in keinem Verhältnis zum Nutzen — dafür gibt es die Regelkaskade.
* **Mehrere Modulfelder je Anlage.** Ein Standort, eine Ausrichtung. Eine Ost-West-Anlage würde
  ungenauer prognostiziert; der gelernte Faktor mittelt das.
* **Steuerung der Entladung.** Das Verfahren plant nur, was **hinein** geht.
* **Mehrtägige Planung.** Der Horizont endet am Ortstag, wie schon der Tiefstpreis heute.
* **Prognose des Preises.** Die Preiszeitreihe liegt für den Tag vor; sie wird nicht extrapoliert.
* **Automatische Wahl des Verfahrens je Mandant.** Merit-Order gilt, sobald die Voraussetzungen
  erfüllt sind — es gibt keinen Schalter dafür.

## 8. Offene Fragen

* **Wie viele Tage für Lastprofil und Faktor?** Vorgabe 28. Für den Faktor sind vermutlich weniger
  besser (er soll aktuellen Zuständen folgen), für das Lastprofil mehr. Getrennte Werte wären
  denkbar — vorerst einer, bis die Daten etwas anderes nahelegen.
* **Wie schnell soll der Faktor auf Schnee oder Verschmutzung reagieren?** Ein gleitendes Mittel
  über 28 Tage reagiert träge. Eine Gewichtung nach Aktualität wäre möglich, ist aber ohne
  Beobachtung geraten.
* **Soll der Plan gespeichert werden oder nur der Rang?** Die Spec sieht nur Rang und benötigte
  Anzahl vor — der ganze Plan wäre 96 Werte je Intervall. Reicht das zum Nachvollziehen?
* **Ab wann ist das Verfahren besser?** Die Rückrechnung kann beide Verfahren über dieselbe
  Historie vergleichen. Offen ist, welche Kennzahl entscheidet: verschobene Energie, vermiedener
  Netzbezug in Franken, oder die Zahl der Tage, an denen der Speicher am Abend nicht voll war.
* **Bleibt die Regelkaskade dauerhaft?** Sie ist Rückfall — ob sie nach einer Erprobungszeit
  entfällt oder bestehen bleibt, ist noch nicht entschieden.
* **Wie oft ändert sich die Prognose wirklich?** `meteoswiss_icon_ch1` wird alle drei Stunden neu
  gerechnet. Stündlicher Abruf ist damit grosszügig; halbtägig wäre zu selten. Ob es dazwischen
  etwas gibt, das die Aufrufe spürbar senkt, ist nicht geprüft — bei 24 von 10'000 erlaubten aber
  auch nicht dringend.
