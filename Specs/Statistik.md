## Statistikseite

### 1. Ziel & Kontext
* Es soll eine Statistikseite realisiert werden.
* Mit der neuen Seite kann ich mir einen Überblick über die Messdaten und Verteilungen verschaffen.
* Aktuell muss ich in die Datenbank schauen oder die Messwerte anzeigen, wenn ich sehen will, welche Daten und Verteilungen vorhanden sind.

### 2. Funktionale Anforderungen (Functional Requirements)
* Es soll ein Filter vorhanden sein, über den ich den Datumsbereich wählen kann, der angezeigt werden soll (analog zur Seite Messwerte)
    * als Default soll beim Öffnen der Seite das vorangehende Quartal gesetzt sein
      * "Datum von" und "Datum bis" sind mit dem ersten bzw. letzten Tag des vorangehenden Quartals (relativ zum aktuellen Datum) vorbelegt
      * der entsprechende Quartal-Button im Quartal-Selektor ist aktiv markiert
      * Jahreswechsel: im Q1 wird Q4 des Vorjahres vorselektiert
      * der Zeitraum bleibt manuell änderbar
    * **Schaltfläche „Heute"** zwischen dem Feld „Bis Datum" und „Anzeigen": setzt „Datum von" **und** „Datum bis" auf das heutige Datum (Ortszeit).
      * Sie **setzt nur den Zeitraum**, sie lädt nicht — wie der Quartal-Selektor. Ausgelöst wird weiterhin mit „Anzeigen". So verhalten sich alle Wege, einen Zeitraum zu wählen, gleich.
      * Deaktiviert, solange der Zeitraum bereits heute–heute ist — sonst wäre der Klick folgenlos, ohne dass man es vorher sähe (wie „Heute" in der Einspeisesteuerung).
      * Icon `calendar`, `zev-button--secondary`; Text über den bestehenden Schlüssel `HEUTE` (seit V65) — **keine** neue Übersetzung, keine Migration.
      * Der Quartal-Selektor zeigt danach kein Quartal mehr als aktiv: Ein einzelner Tag ist kein Quartal.
      * **Akzeptanzkriterien:**
        * [ ] Die Schaltfläche „Heute" steht zwischen „Bis Datum" und „Anzeigen".
        * [ ] Ein Klick setzt „Datum von" und „Datum bis" auf das heutige Datum.
        * [ ] Das Datum ist das **lokale** Tagesdatum, nicht das UTC-Datum — kurz nach Mitternacht darf nicht der Vortag erscheinen.
        * [ ] Ein Klick lädt **keine** Statistik; erst „Anzeigen" tut das.
        * [ ] Ist der Zeitraum bereits heute–heute, ist die Schaltfläche deaktiviert.
* Gib einen Überblick über die Daten:
  * "Messwerte vorhanden bis" mit Angabe des letzten Datums, für das Daten vorhanden sind
  * Angabe, ob für alle Consumer und Producer Daten bis zum Datum "Messwerte vorhanden bis" vorliegen oder Lücken vorhanden sind, d.h. Daten von Einheiten fehlen oder Tage fehlen 
* Als Benutzer möchte ich pro Monat folgendes sehen:
  * Angabe Zeitbereich
  * Angabe ob für jeden Tag im Bereich Messdaten vorhanden sind, d.h. gibt es für alle Einheiten Daten?
  * Zeige die folgenden Summen an: 
    * Summe A: Summe total aller Producer (Produktion)
    * Summe B: Summe total aller Consumer (Verbrauch Total)
    * Summe C: Summe zev aller Producer (Verbrauch Anteil ZEV)
    * Summe D: Summe zev aller Consumer (Verbrauch Anteil ZEV)
    * Summe E: Summe zev_calculated aller Consumer (Verbrauch Anteil ZEV)
  * Berechne folgende zwei Werte (kWh) – *(revidiert: sie werden **nicht mehr als Zeilen** in der Werte-Tabelle angezeigt, sondern nur noch im **Summen-Vergleich** gegen die Bilanz-Einheiten verwendet, siehe `Specs/Bilanzmesspunkt.md` FR-4/FR-5.7)*:
    * **Bezug von VNB** = Verbrauch (Consumer Total) − **gemessener** zev der Consumer (`summeConsumerTotal − summeConsumerZev`; revidiert – vorher `zev_berechnet`, neu Messung gegen Messung)
    * **Rücklieferung** = Produktion (Producer Total) − zev der Producer (`summeProducerTotal − summeProducerZev`, jeweils Absolutwerte)
    * Fachlich: *Bezug von VNB* = aus dem Netz bezogener Rest des Verbrauchs; *Rücklieferung* = ins Netz eingespeister Überschuss der Produktion.
  * Existieren **Bilanz-Einheiten** (Typen `BEZUG`/`RUECKLIEFERUNG`, siehe `Specs/Bilanzmesspunkt.md` FR-5.7), erscheinen **stattdessen** deren gemessene Bilanz-Summen als Zeilen in der Werte-Tabelle (Beschriftung = **Einheiten-Name**, mit Balken, Web + PDF); ohne Bilanz-Einheiten entfallen diese Zeilen und die zugehörigen Bilanz-Vergleiche.
  * Vergleiche, ob Summe C = Summe D
  * Vergleiche, ob Summe C = Summe E
  * Vergleiche, ob Summe D = Summe E
  * Liste die Tage auf, für die mind. eine Summe nicht gleich sind
* Vielleicht hast du eine gute Idee, das graphisch darzustellen
* Die Seite soll im Menu wählbar sein
* Alle Texte mehrsprachig machen und in die Datenbank aufnehmen
* **Darstellung numerischer Werte:** Alle kWh-Werte in den Tabellen (Summen-Tabelle, „Summen pro Einheit", Tage-mit-Abweichungen) werden **rechtsbündig** dargestellt – konsistent mit der PDF-Ausgabe. Umgesetzt über die Design-System-Klasse `.zev-table__number`.
* **Erklärender Hinweis je visualisiertem Wert (Nachtrag):** Jede Zeile der Summen-Tabelle mit Balken (Spalten „Beschreibung / Visualisierung / Wert") trägt einen erklärenden Tooltip (natives `title`-Attribut auf dem `<tr>`), der die Bedeutung des dargestellten Werts kurz erläutert – **analog zu den Kennzahlen** (`Specs/Statistik-Kennzahlen.md` FR-3.5).
  * Betroffen sind alle Zeilen: Produktion (Total), Verbrauch (Total), ZEV Produzent, ZEV Konsumenten, ZEV Konsumenten (berechnet) sowie – falls vorhanden – die beiden **Bilanz-Zeilen** (`BEZUG`/`RUECKLIEFERUNG`).
  * Für die Bilanz-Zeilen ist der Hinweis **generisch** formuliert (die Beschriftung ist der Einheiten-Name bzw. im Bilanzmodus „Rücklieferung (gemessen)"), also unabhängig vom konkreten Einheiten-Namen.
  * Die Hinweistexte kommen via `TranslationService` (DE/EN), Key-Konvention `<LABEL_KEY>_HINWEIS` wie bei den Kennzahlen.
  * Rein additiv: **kein** neues CSS (natives `title`), keine Änderung an Werten, Balken oder der PDF-Ausgabe (Tooltips existieren nur im Web).
  * **Akzeptanzkriterien:**
    * [ ] Hovern über eine Zeile der Summen-Tabelle zeigt einen erklärenden Hinweis; jede der fünf Standard-Zeilen hat einen **eigenen**, inhaltlich passenden Text.
    * [ ] Sind Bilanz-Einheiten vorhanden, tragen auch deren Zeilen einen Hinweis; fehlen sie, entfallen die Zeilen samt Hinweis (keine leeren Tooltips).
    * [ ] Die Hinweise sind in DE und EN vorhanden und werden über den `TranslationService` geladen (keine Hardcodings im Template).
    * [ ] Alle Hinweis-Keys sind per Flyway-Migration angelegt: Die `TranslatePipe` fällt bei fehlendem Key auf den **Key-Namen** zurück (`TranslationService.translate`), ein Tooltip wie „PRODUKTION_TOTAL_HINWEIS" wäre also direkt sichtbar und gilt als Fehler.
    * [ ] Werte, Balkenlängen, Sortierung und PDF-Ausgabe sind unverändert.
* **Monats-Panels aufklappbar (Nachtrag):** Die Monate eines Zeitraums (bei der Vorbelegung „Vorquartal" drei) werden **aufklappbar** dargestellt und sind beim Laden **alle zugeklappt**.
  * Ausgeklappt füllt ein einzelner Monat (Balken-Tabelle, Kennzahlen, Summen-Vergleich, „Summen pro Einheit", Details) rund eine Bildschirmseite; drei Monate untereinander machen die Seite unübersichtlich. Zugeklappt passt der ganze Zeitraum auf einen Blick.
  * Darstellung wie bei „Details anzeigen" auf derselben Seite und bei den Mieter-Blöcken der Nebenkostenabrechnung (`Specs/Nebenkosten/Abrechnung.md`): Design-System-Baustein **Collapsible** (`.zev-collapsible`), also eine klickbare Kopfleiste mit Dreieck rechts.
  * Die Kopfleiste zeigt Datenstatus-Punkt, Monatsname, Jahr und den Zeitraum – die geschlossene Ansicht sagt damit weiterhin aus, ob der Monat vollständige Daten hat.
  * Jeder Monat wird **einzeln** auf- und zugeklappt; ein erneuter Klick schliesst ihn wieder.
  * Ein neuer Abruf über „Anzeigen" setzt alle Monate wieder auf zugeklappt – sonst bliebe ein Panel offen, das anschliessend einen anderen Monat zeigt.
  * Die **Detail-Sektion innerhalb** eines Monats („Details anzeigen" für fehlende Einheiten/Tage und Tage mit Abweichungen) bleibt davon unberührt und behält ihren eigenen Zustand.
  * Rein visuell: **keine** Änderung an Werten, Berechnungen oder der **PDF-Ausgabe** (das PDF enthält weiterhin alle Monate vollständig), **keine** neuen Texte (Monatsname und Zeitraum stehen bereits in der Kopfzeile).
  * **Akzeptanzkriterien:**
    * [ ] Nach dem Laden einer Statistik sind alle Monats-Panels zugeklappt; die Kopfzeilen aller Monate sind sichtbar, aber kein Monatsinhalt (Balken-Tabelle, Kennzahlen, „Summen pro Einheit").
    * [ ] Ein Klick auf die Kopfleiste eines Monats klappt genau diesen Monat auf; die übrigen bleiben zugeklappt.
    * [ ] Ein erneuter Klick auf dieselbe Kopfleiste klappt den Monat wieder zu.
    * [ ] Die Kopfleiste zeigt auch im zugeklappten Zustand Monatsname, Jahr, Zeitraum und den Datenstatus-Punkt.
    * [ ] Kopfleiste und Inhalt verwenden den bestehenden Collapsible-Baustein des Design Systems – kein eigenes CSS für die Statistik-Seite.
    * [ ] Ein erneuter Abruf über „Anzeigen" klappt alle Monate wieder zu.
    * [ ] Der Zustand von „Details anzeigen" innerhalb eines Monats wird durch das Auf-/Zuklappen des Monats nicht verändert.
    * [ ] Die PDF-Ausgabe ist unverändert (alle Monate vollständig).
* **Gesamt-Panel über den gewählten Zeitraum**
  * Oberhalb der Monats-Panels steht ein weiteres Panel **„Gesamter Zeitraum"** mit denselben Inhalten wie ein Monat (Summentabelle, Kennzahlen, Vergleiche, Summen pro Einheit, Details) — aber über den **ganzen** gewählten Zeitraum. Kopfzeile: Datenstatus-Punkt, Titel, Zeitraum von–bis.
  * Es startet **aufgeklappt**, die Monate zugeklappt, und lässt sich wie ein Monat zu- und aufklappen. Auch nach jedem neuen Abruf über „Anzeigen" ist es wieder aufgeklappt.
  * **Gerechnet, nicht addiert.** Das Backend berechnet das Panel mit **derselben** Methode wie einen Monat, nur über `von`–`bis`. Summen liessen sich aus den Monaten addieren, die **Quoten** (Autarkiegrad, Eigenverbrauchsquote, Netzbezugs- und Einspeisequote) und der **Batterie-Wirkungsgrad** aber nicht: Ein Mittel aus Monatsquoten gewichtete einen trüben Monat gleich wie einen mit zehnmal mehr Energie. Aus den Gesamtsummen gerechnet, mit denselben Formeln, können Monat und Gesamt nicht auseinanderlaufen.
  * **Auch bei nur einem Monat** wird es angezeigt. Sein Inhalt gleicht dann dem Monat — dafür sieht man die Zahlen sofort, ohne aufzuklappen.
  * **Eine Vorlage für beide Panels** (Frontend, `ng-template`): Zwei Kopien liefen beim nächsten Ausbau eines Monats unbemerkt auseinander.
  * **Auch im PDF**, als erster Block vor den Monaten — mit **demselben** Detail-Band wie ein Monat, nur mit der Überschrift „Gesamter Zeitraum (von – bis)". Wie auf der Seite eine Vorlage für beide: Zwei Bänder liefen beim nächsten Ausbau auseinander.
    * Das Template erkennt das Gesamt an `monat == 0`. Die Überschrift rief zuvor `Month.of(monat)` auf, und `Month.of(0)` wirft — ohne die Anpassung bräche der ganze Export ab.
    * Die Bänder kommen aus einer **neuen** Liste (Gesamt + Monate), nicht aus der ergänzten Monatsliste der Statistik: Die stammt aus dem Cache, und das Gesamt stünde dort sonst dauerhaft — die Seite zeigte es danach ein zweites Mal als vermeintlichen Monat.
  * **Aufwand:** eine zusätzliche Berechnung je Abruf (bei einem Quartal vier statt drei); das Ergebnis liegt im bestehenden Statistik-Cache.
  * Neuer Übersetzungsschlüssel `STATISTIK_GESAMTER_ZEITRAUM` (V171).
  * **Akzeptanzkriterien:**
    * [ ] Oberhalb der Monats-Panels steht ein Panel „Gesamter Zeitraum" mit dem gewählten Zeitraum von–bis in der Kopfzeile.
    * [ ] Es ist nach dem Laden aufgeklappt, die Monate sind zugeklappt.
    * [ ] Es zeigt dieselben Bereiche wie ein aufgeklappter Monat.
    * [ ] Die Summen entsprechen dem ganzen Zeitraum; die Quoten sind aus den **Gesamtsummen** gerechnet, nicht aus den Monatsquoten gemittelt.
    * [ ] Bei angebrochenen Monaten am Rand trägt es genau den gewählten Zeitraum (z. B. 15.01.–10.02.).
    * [ ] „Details anzeigen" im Gesamt-Panel hat einen eigenen Zustand, unabhängig von den Monaten.
    * [ ] Das PDF beginnt nach der Übersicht mit dem Block „Gesamter Zeitraum (von – bis)", danach folgen die Monate; Inhalt wie ein Monatsblock.
    * [ ] Die Überschrift ist übersetzt („Entire period" auf Englisch).
    * [ ] Ein PDF-Export ändert die Monatsliste der Seite nicht — auch nach mehreren Exporten erscheint das Gesamt-Panel auf der Seite genau einmal.
    * [ ] Der CSV-Download im Gesamt-Panel exportiert den ganzen Zeitraum; der Dateiname trägt dann von und bis statt eines Monats.

### 3. Technische Spezifikationen (Technical Specs)
* Verwende das Design System
* Neue Designs in das Design System aufnehmen 
* Design an bisherige Seiten anlehnen

### 4. Nicht-funktionale Anforderungen
* Sicherheit: Die Seite kann mit der Rolle "zev" aufgerufen werden 
* Sinnvolles Logging
* Erstelle sinnvolle und hilfreiche Tests erst auf Anweisung

### 5. Verschiedenes
* Beachte die Anweisungen in den Dateien Specs/generell.md und Specs/AutomatisierteTests.md
