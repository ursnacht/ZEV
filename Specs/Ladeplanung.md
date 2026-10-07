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
  > **Stand 05.10.2026: alles umgesetzt ausser dem Schalten.** FR-1 bis FR-7 und FR-9 sind gebaut,
  > die Merit-Order läuft seit dem 04.10. als **Schattenrechnung** (FR-1a) neben der Regelkaskade.
  > FR-8 ist **zurückgenommen**, nicht offen.
  >
  > **Was aussteht, ist die Entscheidung, nicht der Code:** Ob die Merit-Order die Kaskade ersetzt,
  > soll eine Woche Parallelbetrieb beantworten (ab 11.10.2026). Weichen die Verfahren selten
  > voneinander ab, ist der Gewinn klein und das Regelwerk genügt — dann bleibt es, wie es ist.
  >
  > Die **Prognose** hat ihre Bewährungsprobe bestanden: Über fünf Tage trifft sie die Gesamtmenge
  > auf 1.4 %, ohne den jeweiligen Tag zu kennen (FR-3, „Was gemessen wurde").

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
5. **Auffüllen**, bis die freie Kapazität gedeckt ist — und zwar `kapazitaetFrei / 0.95`, weil der
   erwartete Überschuss die Energie **vor** dem Speicher ist. Die so gewählten Intervalle sind der
   **Ladeplan**.

   > **Der Ladewirkungsgrad, gemessen statt geschätzt.** Am 27.09.2026 wurde die Batterie von 44 %
   > auf 100 % geladen. Aus dem Energiezähler gerechnet ergab sich eine Kapazität von rund 43 kWh,
   > konfiguriert sind **40.8** — Verhältnis 1.054, also gut **95 %** Wirkungsgrad. Von 3.0 kWh, die
   > der Zähler sieht, kommen etwa 2.85 im Speicher an; der Rest bleibt als Wärme in Wechselrichter
   > und Zellen.
   >
   > **Warum das nicht vernachlässigt wird**, obwohl 5 % weit innerhalb der Prognoseungenauigkeit
   > liegen: Der Fehler zeigt immer in **dieselbe** Richtung. Ohne den Divisor enthielte der Plan
   > dauerhaft zu wenige Intervalle, und die Batterie wäre am Abend systematisch knapp nicht voll.
   > Prognosefehler mitteln sich über die Tage heraus, dieser nicht.
   >
   > **Kein Konfigurationsfeld.** 0.95 steht als Konstante. Der Wert ist eine Eigenschaft der
   > Hardware, die sich kaum ändert, und er lässt sich jederzeit aus den Daten nachrechnen —
   > `speicher_ladung` gegen `ΔSOC × kapazitaet`. Ein Feld dafür wäre eine Frage, die niemand
   > beantworten kann, ohne genau diese Rechnung zu machen.
6. **Entscheid für das ausgewertete Intervall:** liegt es im Plan → `batterieladung = FREI`, sonst
   `GESPERRT`. **Gibt es nichts zuzuteilen** — kein erwarteter Überschuss oder keine freie
   Kapazität —, gilt `FREI`.
7. **Entscheid festhalten** wie bisher, um die Plangrössen erweitert (FR-5).

### FR-1a: Erste Stufe — Schattenrechnung

**Zunächst entscheidet weiterhin die Regelkaskade.** Die Merit-Order rechnet in jedem Job-Lauf
**mit**, und ihr Ergebnis wird protokolliert (`ladeplan_batterieladung`, FR-5) — es bestimmt den
Entscheid aber **nicht**. `verfahren` trägt in dieser Stufe durchgehend `REGEL`.

> **Warum nicht gleich umschalten.** Der Nutzen des Verfahrens ist bisher nicht gemessen, sondern
> begründet. Die Schattenrechnung liefert die Messung: Nach einer Woche stehen rund 670 Intervalle
> nebeneinander, in denen beide Verfahren unter denselben Preisen, derselben Prognose und
> demselben Ladezustand entschieden haben.
>
> **Und sie liefert sie ehrlich — anders als eine Rückrechnung.** FR-8 ist zurückgenommen, weil
> `zev.einstrahlungsprognose` je Intervall nur die **zuletzt** geholte Fassung hält: Eine
> nachträgliche Auswertung kennte das Wetter, das inzwischen eingetreten ist, und liesse die
> Merit-Order systematisch besser aussehen. Im **Live-Lauf** besteht dieses Problem nicht — dort
> liegt die Prognose in genau der Fassung vor, die zum Entscheidungszeitpunkt galt.
>
> **Der Trockenlauf macht es umsonst.** Da ohnehin nichts geschaltet wird, ist der Unterschied
> zwischen Schattenrechnung und Ersatz allein, welche Zahl in welcher Spalte steht. Das Umschalten
> ist später eine Zeile — und sie beruht dann auf Daten statt auf Zutrauen.

**Woran sich das Umschalten entscheidet,** ist offen (§8). Die Schattenrechnung erzeugt die
Grundlage dafür: Wie oft weichen die Verfahren überhaupt voneinander ab, und wie viel Energie
verschiebt die Abweichung? Weichen sie selten ab, ist der Gewinn klein und das Regelwerk genügt.

> **Der Ladezustand stammt von keinem der beiden Verfahren.** Im Trockenlauf schaltet **niemand**:
> Die Batterie folgt der Eigenverbrauchsoptimierung des Wechselrichters und lädt, sobald Überschuss
> da ist. Am 04.10.2026 stieg der Ladezustand von 49 % um 08:45 stetig auf 100 % um 13:00 — keine
> Sperre wirkte, weder die der Kaskade noch die der Merit-Order.
>
> **Für den Vergleich ist das günstig.** Beide Verfahren sehen zu jedem Zeitpunkt dieselbe
> Ausgangslage, und keines beeinflusst sie. Es gibt keine Asymmetrie zwischen ihnen: Wo sie
> auseinandergehen, ist das ein echter Unterschied im Entscheid, kein Artefakt des Parallelbetriebs.
>
> **Was sich daraus trotzdem nicht ableiten lässt**, ist der Nutzen in Kilowattstunden. Beide planen
> gegen eine Wirklichkeit, in der **keines** von beiden wirkt. Am 04.10. sperrte die Merit-Order
> vormittags (Rang 17 bei 8 benötigten) und hielt Kapazität für die billigen Mittagsstunden frei —
> hätte sie wirklich gesteuert, wäre die Batterie um 13:00 nicht voll gewesen und die Mittagsstunden
> wären nutzbar geblieben. So aber lud die Anlage ungebremst, und der Plan lief ins Leere.
>
> **Die Woche misst also, wie oft und wann die Verfahren verschieden entscheiden — nicht, was das
> wert gewesen wäre.** Für die Frage „lohnt das Umschalten" genügt das: Weichen sie nie ab, gibt es
> nichts zu gewinnen. Weichen sie oft und systematisch ab, ist die Richtung erkennbar — den Betrag
> kennt erst der Wirkbetrieb.
>
> **Warum kein simulierter Ladezustand.** Ihn mitzuführen hiesse, einen hypothetischen
> Anlagenzustand über Tage fortzuschreiben — mit Annahmen über Ladeleistung, Verluste und Entladung,
> die alle nicht gemessen sind. Ein Fehler darin wäre unsichtbar und verfälschte genau die Zahl, die
> entscheiden soll.

> **Warum eine Merit-Order und kein Solver.** Solange alle gespeicherten Kilowattstunden denselben
> Wert haben (`speicherwert`) und keine Leistungsgrenze bindet, ist das Auffüllen nach Preis
> **beweisbar optimal** — es liefert dasselbe Ergebnis wie ein lineares Programm, in zwanzig Zeilen
> und ohne Abhängigkeit. Ein LP wird erst nötig, wenn Nebenbedingungen koppeln (siehe §7).

#### Warum das optimal ist

**Das Argument in drei Sätzen.** Eine gespeicherte Kilowattstunde ist immer gleich viel wert — es
spielt keine Rolle, aus welchem Intervall sie stammt. Was eine Ladung **kostet**, ist deshalb
allein die Einspeisung, auf die man dafür verzichtet. Füllt man die Kapazität mit den Intervallen
der niedrigsten Einspeisepreise, verzichtet man auf die geringstmögliche Vergütung.

**Die Gegenprobe:** Nimmt man aus einem optimalen Plan ein Intervall heraus und ersetzt es durch
eines mit höherem Preis, bleibt die gespeicherte Menge gleich, die entgangene Vergütung steigt.
Jeder solche Tausch verschlechtert das Ergebnis — also gibt es keinen besseren Plan. Das ist ein
Austauschargument, kein Optimierungsverfahren; deshalb genügen zwanzig Zeilen, wo sonst ein Solver
stünde.

**Die Bedingung ist nachgemessen, nicht angenommen.** Der Beweis trägt nur, solange die Batterie
jeden angebotenen Überschuss auch aufnehmen kann. Am 27.09.2026 um 12:00: Erzeugung 3.737 kWh,
Verbrauch 0.353 kWh, Ladung 3.400 kWh — die Batterie nahm den **gesamten** Überschuss. Keine
Leistungsgrenze band.

> **Woran man merkt, dass die Bedingung kippt.** `speicher_ladung` bliebe bei starker Sonne
> mehrfach auf demselben Wert stehen, während `produktion + speicher_ladung − verbrauch` weiter
> steigt — die Batterie nimmt dann nicht mehr alles. Das passiert, wenn die Anlage wächst oder der
> Wechselrichter getauscht wird.
>
> **Dann ist das Verfahren nicht mehr optimal, und zwar lautlos:** Die Merit-Order plante weiter
> Intervalle ein, deren Überschuss gar nicht vollständig in die Batterie passt, und der Plan wäre
> zu kurz. Ab diesem Punkt bräuchte es ein LP (§7) — oder mindestens eine Deckelung des
> Überschusses je Intervall auf die Ladeleistung. Die Prüfung gehört in eine Auswertung, nicht in
> den Job: Sie ist eine Eigenschaft der Anlage, keine des Intervalls.

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

> **Die Zeitzone gehört unkodiert in die URL, und die Antwort wird gegengeprüft.** Am 26.09.2026
> stand `Europe%2FZurich` dort; `RestClient.uri(String)` liest die Zeichenkette als URI-Vorlage und
> kodierte das `%` ein zweites Mal — abgeschickt wurde `Europe%252FZurich`. Open-Meteo antwortete
> mit `400 Bad Request: {"error":true,"reason":"Invalid timezone"}`. Der Abruf schlug **laut** fehl
> und erzeugte eine Systemmeldung; falsche Daten entstanden dabei nicht.
>
> Zusätzlich wird seit dem 27.09.2026 die **gemeldete** Zeitzone geprüft (Feld `timezone` der
> Antwort, das bis dahin ungenutzt im DTO lag). Weicht sie ab, wird nichts geschrieben. Das ist
> eine Rückversicherung für den Fall, dass die API einmal still auf UTC zurückfällt statt
> abzuweisen — dann läge die ganze Prognose zwei Stunden daneben und sähe weiterhin plausibel aus.

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
>
> **Das gilt nur bei grossem Polster — korrigiert am 07.10.2026.** Am 21.09. lag der erwartete
> Überschuss 70 % über dem Bedarf. Am 07.10. waren es nur 25 %, und ein Fehler von 30 % beim
> Überschuss genügte, um die Entscheidung umzukehren (siehe „Was gemessen wurde"). Robust ist das
> Verfahren also nicht gegen einen bestimmten Fehler, sondern nur, solange der Fehler kleiner ist
> als das Polster.

#### Was gemessen wurde (Stand 05.10.2026)

Fünf Tage, jeder bewertet mit dem Faktor, den der Service **an jenem Morgen** hatte — also ohne
Kenntnis des Tages selbst:

| Tag | Einstrahlung | tatsächlich | vorhergesagt | Abweichung |
|---|---|---|---|---|
| 01.10. | 8'158 | 33.84 kWh | 40.64 kWh | −16.7 % |
| 02.10. | 11'904 | 57.42 kWh | 58.11 kWh | −1.2 % |
| 03.10. | 13'272 | 74.67 kWh | 64.68 kWh | +15.4 % |
| 04.10. | 12'322 | 60.38 kWh | 61.37 kWh | −1.6 % |
| 05.10. | 15'828 | 72.89 kWh | 78.68 kWh | −7.4 % |
| **Summe** | | **299.20 kWh** | **303.48 kWh** | **−1.4 %** |

**Über fünf Tage trifft die Prognose die Gesamtmenge auf 1.4 %**, der mittlere absolute Tagesfehler
liegt bei 8.5 %. Beides deutlich innerhalb der 30 %, gegen die das Verfahren robust sein soll.

> **Der Faktor ist nicht der Engpass — das Wettermodell ist es.** Über dieselben fünf Tage
> schwankte der gelernte Faktor zwischen 0.00487 und 0.00498, also um **2.3 %**. Die
> Tagesabweichungen schwankten um ±16 %. Am 01.10. und am 03.10. — den beiden schlechtesten Tagen —
> war der Faktor nahezu identisch; was sich unterschied, war das Wetter gegenüber der Vorhersage.
>
> **Daraus folgt, woran nicht zu arbeiten ist:** Eine Gewichtung nach Aktualität, ein kürzeres
> Lernfenster, eine andere Regression — all das drehte an einer Grösse, die bereits auf 2 % genau
> ist. Die verbleibende Streuung kommt von Open-Meteo, und daran lässt sich nichts ändern.
>
> **Die Prognose ist zudem dort am besten, wo sie gebraucht wird.** An den drei hellsten Tagen lag
> der mittlere absolute Fehler bei 3 %, an den drei trübsten bei 16 %. An trüben Tagen reicht die
> Sonne ohnehin kaum für eine Batterieladung — dann lautet die Antwort „lade, was kommt", und der
> Fehler ändert daran nichts.

> **Nachprüfbar mit dieser Abfrage** (sie bildet das Lernfenster des Service nach — 28 Tage, der
> gefragte Tag ausgeschlossen, mindestens 150 helle Intervalle):
>
> ```sql
> WITH tage AS (
>   SELECT e.zeit::date AS tag, count(*) AS helle, sum(e.gti) AS gti,
>          sum(s.produktion + coalesce(s.speicher_ladung,0)
>              - coalesce(s.speicher_entladung,0)) AS ist
>   FROM zev.einstrahlungsprognose e
>   JOIN zev.steuerentscheid s ON s.zeit_von = e.zeit AND s.org_id = e.org_id
>   WHERE e.gti > 0 GROUP BY 1
> ), historie AS (
>   SELECT tag, gti, ist,
>          sum(helle) OVER w AS helle_davor,
>          sum(ist)   OVER w AS ist_davor,
>          sum(gti)   OVER w AS gti_davor
>   FROM tage WINDOW w AS (ORDER BY tag ROWS BETWEEN 28 PRECEDING AND 1 PRECEDING)
> )
> SELECT to_char(tag, 'YYYY-MM-DD') AS tag, round(gti, 0) AS summe_gti,
>        round(ist, 2) AS ist_kwh,
>        round(CASE WHEN helle_davor >= 150 THEN ist_davor / gti_davor END, 8) AS faktor,
>        round(gti * ist_davor / nullif(gti_davor, 0), 2) AS erwartet_kwh
> FROM historie ORDER BY tag;
> ```
>
> **Den Faktor nicht aus allen Tagen zugleich lernen.** Dann stimmt die Summe per Konstruktion, und
> die Messung bezeugt nur sich selbst.

#### Der 07.10.2026: Erzeugung **und** Verbrauch daneben — und der Plan kippte

Die Messung oben betrifft nur die **Erzeugung**. Die Merit-Order rechnet aber mit dem
**Überschuss**, und der hängt genauso am Verbrauch. Am 07.10. lag die Schattenrechnung zum ersten
Mal eindeutig falsch:

| 08:45–19:00 | erwartet | tatsächlich | Abweichung |
|---|---|---|---|
| Erzeugung | 37.44 kWh | 32.47 kWh | −5.0 kWh (Prognose 15 % zu hoch) |
| Verbrauch | 9.31 kWh | 13.35 kWh | +4.0 kWh (43 % mehr als das Lastprofil) |
| Überschuss | 27.69 kWh | 19.46 kWh | −8.2 kWh (30 % zu hoch) |

Frei waren am Morgen rund 21 kWh, nötig also etwa 22 kWh Überschuss (`/ 0.95`). Erwartet waren
27.7 — der Plan sperrte deshalb den teuren Vormittag bis 12:00, um die billigeren Stunden danach
zu nutzen. Gekommen sind 19.5. Die Regelkaskade sperrte an diesem Tag nicht; die Batterie lud
durchgehend und erreichte trotzdem nur **83 %**. **Hätte die Merit-Order gesteuert, wäre sie noch
leerer geblieben.**

> **Jeder Fehler allein hätte nicht gereicht.** Nur mit der Erzeugungsabweichung wären es 22.7 kWh
> gewesen, nur mit der Verbrauchsabweichung 23.7 — beides knapp genug. Erst zusammen fiel der
> Überschuss unter den Bedarf.
>
> **Damit ist die Aussage „das Wettermodell ist der Engpass" zu eng.** Für die Erzeugung bleibt
> sie richtig. Für den Überschuss ist das **Lastprofil** eine zweite, gleich grosse Fehlerquelle:
> ein Median aus nur vier Stichproben (gleiche Wochentage in 28 Tagen), und am 07.10. lag der
> tatsächliche Verbrauch 43 % darüber.

> **Nachprüfbar mit dieser Abfrage** — mit den bei jedem Entscheid gespeicherten Werten, also dem,
> was das Verfahren zu jenem Zeitpunkt wusste. Den Verbrauch rechnet sie nur für Intervalle mit
> erwartetem Überschuss zurück; anderswo ist das Lastprofil nicht gespeichert.
>
> ```sql
> SELECT round(sum(gti * prognose_faktor), 2)                             AS erzeugung_erwartet,
>        round(sum(produktion + coalesce(speicher_ladung,0)
>                  - coalesce(speicher_entladung,0)), 2)                  AS erzeugung_ist,
>        round(sum(gti * prognose_faktor - prognose_ueberschuss)
>              FILTER (WHERE prognose_ueberschuss > 0), 2)                AS verbrauch_erwartet,
>        round(sum(verbrauch) FILTER (WHERE prognose_ueberschuss > 0), 2) AS verbrauch_ist,
>        round(sum(prognose_ueberschuss), 2)                              AS ueberschuss_erwartet,
>        round(sum(greatest(0, produktion + coalesce(speicher_ladung,0)
>                  - coalesce(speicher_entladung,0) - verbrauch)), 2)     AS ueberschuss_ist
> FROM zev.steuerentscheid
> WHERE zeit_von >= :von AND zeit_von < :bis AND prognose_faktor IS NOT NULL;
> ```
>
> **Einschränkung:** Jeder Entscheid speichert die Prognose seiner **eigenen** Auswertung — für
> spätere Intervalle also eine neuere als die, mit der am Morgen geplant wurde. Ist schon die
> neuere zu optimistisch, war es die frühere sehr wahrscheinlich auch.

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
| `verfahren` | VARCHAR(20) | `MERIT_ORDER` oder `REGEL` — welches Verfahren den **geltenden** Entscheid fällte. In der Schattenrechnung (FR-1a) durchgehend `REGEL` |
| `ladeplan_batterieladung` | VARCHAR(20) | was die Merit-Order **entschieden hätte**: `FREI` oder `GESPERRT`. `NULL`, wenn sie nicht rechnen konnte — dann fehlte eine Voraussetzung (FR-4) |
| `regel` | (bestehend) | trägt bei Merit-Order den neuen Wert **`LADEPLAN`** — die Spalte ist `NOT NULL` mit CHECK-Constraint (V145, erweitert in V160), der Wert braucht also eine **DDL-Migration**, kein blosses Enum. Sie wird **mit der ersten Migration** mitgenommen, obwohl die Schattenrechnung den Wert noch nicht schreibt: Sonst scheiterte das spätere Umschalten an einer vergessenen DDL — im Job, nachts |
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
* [ ] Ist die Batterie voll (freie Kapazität 0), ist der Plan **leer** — und jedes Intervall
      `batterieladung = FREI`, nicht `GESPERRT`.

  > **Korrigiert am 04.10.2026.** Zuerst stand hier „jedes Intervall gesperrt". An jenem Tag war
  > die Batterie um 13:00 voll, und das **billigste Intervall des Tages** (Rang 1) trug `GESPERRT`
  > — eine Sperre, die nichts verhindert, denn in eine volle Batterie lässt sich nichts laden.
  >
  > Entscheidend war die Wirkung auf die Schattenrechnung: Die Regelkaskade entscheidet hier `FREI`,
  > also zählte **jedes** Intervall nach dem Vollwerden als Abweichung — an diesem einen Tag über
  > vierzig. Die Kennzahl, um derentwillen der Parallelbetrieb gebaut wurde, hätte
  > Scheinunterschiede gemessen.
  >
  > Damit gilt dieselbe Regel wie beim fehlenden Überschuss: **Wo nichts zuzuteilen ist, gibt es
  > nichts zu sperren.**
* [ ] Ein Intervall **ohne** erwarteten Überschuss belegt keinen Platz im Plan — und ist
      `batterieladung = FREI`, nicht `GESPERRT`.

  > **Wo nichts zuzuteilen ist, gibt es nichts zu sperren.** Die Merit-Order verteilt knappe
  > Kapazität; ohne erwarteten Überschuss ist die Frage gegenstandslos. Andernfalls stünde jede
  > Nachtstunde auf `GESPERRT`, obwohl nichts zu laden war, und das Protokoll wäre voller
  > Scheinsperren. Die Regelkaskade entscheidet im selben Fall `FREI` (`KEIN_UEBERSCHUSS`) — so
  > weichen die Verfahren nur dort voneinander ab, wo wirklich etwas zu entscheiden war. Für die
  > Schattenrechnung (FR-1a) ist das wesentlich: Sonst zählte jede Nacht als Abweichung und der
  > Vergleich wäre wertlos.
* [ ] Aufgefüllt wird bis **`kapazitaetFrei / 0.95`**, nicht bis `kapazitaetFrei` — der erwartete
      Überschuss ist die Energie vor dem Speicher, und rund 5 % davon kommen dort nie an.

**Schattenrechnung (FR-1a)**

* [ ] `batterieladung` und `einspeisung` stammen in dieser Stufe **unverändert** aus der
      Regelkaskade — die Merit-Order ändert keinen geltenden Entscheid.
* [ ] `verfahren` trägt durchgehend `REGEL`.
* [ ] `ladeplan_batterieladung` trägt, was die Merit-Order entschieden hätte — und `NULL`, wenn
      eine Voraussetzung fehlte.
* [ ] Ein Fehler in der Merit-Order lässt den Entscheid der Kaskade **unberührt**: Der Job schreibt
      ihn trotzdem, mit `ladeplan_batterieladung = NULL`.
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

* **Braucht die Merit-Order einen Sicherheitszuschlag — und wie gross?** Seit dem 07.10.2026 belegt
  (FR-3, „Der 07.10.2026"): Bei knappem Polster genügt ein durchschnittlich schlechter Tag, um den
  Plan umzukehren. Ein Zuschlag hiesse: gesperrt wird erst, wenn der erwartete Überschuss den Bedarf
  um einen Faktor übersteigt (z. B. 1.3); sonst wird geladen. Am 07.10. hätte 1.3 gereicht
  (27.7 < 22 × 1.3 = 28.7, also keine Sperre).

  **Bewusst noch nicht eingebaut.** Der Faktor ist aus den Daten der Testwoche zu bestimmen, nicht
  zu raten: Ist er zu gross, sperrt die Merit-Order fast nie und bringt nichts mehr; ist er zu
  klein, hilft er an Tagen wie dem 07.10. nicht. Zu ermitteln je Tag: erwarteter gegen
  tatsächlichen Überschuss ab der ersten Sperre — das Verhältnis der beiden ist genau die Grösse,
  die der Zuschlag abdecken muss.

* **Wie viele Tage für Lastprofil und Faktor?** Vorgabe 28. Für den Faktor sind vermutlich weniger
  besser (er soll aktuellen Zuständen folgen), für das Lastprofil mehr. Getrennte Werte wären
  denkbar — vorerst einer, bis die Daten etwas anderes nahelegen.
* ~~**Wie schnell soll der Faktor auf Schnee oder Verschmutzung reagieren?**~~ — **beantwortet am
  05.10.2026: Die Frage ist gegenstandslos.** Der Faktor ist nicht der Engpass (siehe FR-3,
  „Was gemessen wurde"). Eine Gewichtung nach Aktualität würde an einer Grösse drehen, die bereits
  auf 2 % genau ist.
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
