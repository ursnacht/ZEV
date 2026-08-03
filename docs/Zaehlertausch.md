# Betriebsanleitung: Zählertausch (Bilanzzähler und andere Einheiten)

Diese Anleitung beschreibt, wie ein physischer Zähler eines Mandanten ausgetauscht wird,
**ohne Messdaten zu verlieren** und ohne die Statistik/Abrechnung zu zerreissen. Sie gilt für
den **Bilanzzähler** (Einheiten-Typen `BEZUG`/`RUECKLIEFERUNG`) ebenso wie für `PRODUCER`/`CONSUMER`.

## Grundprinzip

- **Messwerte hängen an der Einheit (`einheit_id`), nicht an der Zählernummer.** `messwerte`
  (und beim MQTT-Betrieb auch `zaehler_rohdaten`) referenzieren die Einheit per Fremdschlüssel.
  Solange die **Einheit erhalten bleibt**, bleibt die gesamte Historie erhalten – egal welches
  Gerät physisch verbaut ist.
- **Eingehende Daten werden über `(org_id, messpunkt)` zugeordnet** (`MqttIngestService`,
  `findAllByOrgIdAndMesspunkt`) bzw. beim CSV-Upload über den Messpunkt/KI-Matching. Der
  `messpunkt` auf der Einheit ist der einzige „Anker" für neue Daten.
- **Die MQTT-Aggregation rechnet mit Differenzen absoluter Zählerstände**
  (`ZaehlerAggregationService`). Der **absolute Startwert des neuen Zählers ist damit irrelevant** –
  nur die Differenz aufeinanderfolgender Stände zählt. Für den Zählerstands-Rücksprung beim Tausch
  gibt es einen **Reset-Guard**: negatives Delta → 0 (Log „Rücksprung … Delta auf 0 gesetzt").

## Goldene Regel

> **Die bestehende Einheit behalten. Niemals löschen und neu anlegen.**

Eine neue Einheit für den neuen Zähler anzulegen würde die Historie auf zwei Einheiten aufteilen
(Statistik und Abrechnung brechen an der Tausch-Grenze), und ein Löschen der alten Einheit
gefährdet die verknüpften Messwerte.

## Ablauf

### 1. Vorbereitung
- Ermitteln, ob sich mit dem neuen Zähler die **Messpunkt-/Zählpunkt-Kennung** ändert.
- **Endstand des alten Zählers und Anfangsstand des neuen Zählers protokollieren** (mit Zeitstempel).
  Das ist der Schlüssel zur späteren Korrektur des einen genullten Übergangsintervalls.
- Möglichst einen **lastschwachen Zeitpunkt** wählen (z.B. nachts / geringe Produktion), damit das
  betroffene Intervall wenig reale Energie enthält.

### 2. Physischen Tausch möglichst kurz halten
Während der Zähler offline ist, kommen keine Rohdaten → die betroffenen 15-Min-Intervalle bekommen
schlicht **keinen Messwert** (echte Lücke = Ausfallzeit). Je kürzer die Offline-Zeit, desto weniger
leere Intervalle.

> **Kein Datenschutz durch „Viertelstundengrenze".** Die Aggregation bildet je Intervall die
> Differenz absoluter Zählerstände (Stand am Ende − Stand am Anfang). Da der neue Zähler bei einem
> **niedrigeren** Stand startet, gibt es **genau ein** Übergangsintervall mit `delta < 0`, das der
> Reset-Guard auf `0` setzt – **unabhängig davon, ob der Tausch auf `:00/:15/:30/:45` fällt** (die
> Grenze verschiebt nur, *welches* Intervall es trifft). Auch der Zeitpunkt des Aggregations-Jobs
> (`:05/:20/:35/:50`) ist irrelevant: die Catch-up-Schleife verarbeitet spät eintreffende Rohdaten
> korrekt. Entscheidend ist allein die **Zählerstands-Diskontinuität**, nicht die Uhrzeit.

### 3. Messpunkt anpassen (nur wenn sich die Kennung ändert)
In der **Einheiten-Verwaltung** den `messpunkt` der betroffenen Einheit auf die neue Kennung setzen –
**genau zum Umschaltzeitpunkt**.
- Bleibt die Kennung gleich (nur das Gerät wird getauscht): **nichts zu tun**, neue Daten mappen
  automatisch weiter.
- Teilen sich `BEZUG` **und** `RUECKLIEFERUNG` denselben Bilanzmesspunkt: **beide** Einheiten
  anpassen (denselben neuen `messpunkt` setzen).

### 4. MQTT-/Pi-Konfiguration (nur MQTT-Betrieb)
Sicherstellen, dass der neue Zähler unter dem **gleichen Topic** publiziert wie in `messpunkt`
hinterlegt: `zev/{orgId}/{messpunkt}/messwert`. Ab dem ersten neuen Zählerstand läuft die
Aggregation normal weiter (erstes volles Intervall nach dem Tausch = Differenz zweier neuer
Stände → korrekt).

### 5. Kontrolle nach dem Tausch
- Statistik-Seite für den Umschalttag prüfen: ab dem ersten vollen Intervall nach dem Tausch
  erscheinen wieder plausible Werte.
- Bei MQTT: im Log nach dem Eintrag „Rücksprung … Delta auf 0 gesetzt" suchen – er markiert das
  eine genullte Übergangsintervall. Zusätzlich die während der Offline-Zeit fehlenden Intervalle
  (leer/keine Daten) identifizieren.

## Das eine Übergangsintervall (immer betroffen)

Unabhängig vom Zeitpunkt wird **genau ein** Intervall – jenes, in dem der Zählerstand vom alten
(hohen) auf den neuen (niedrigen) Wert springt – durch den Reset-Guard auf `0` gesetzt (die übrigen
Werte bleiben korrekt). Wenn die Abrechnung dieses Intervall exakt braucht, den betroffenen
`messwerte`-Datensatz **manuell korrigieren**: aus dem protokollierten **Endstand (alt)** und
**Anfangsstand (neu)** den tatsächlichen Verbrauch/Erzeugung dieses Intervalls berechnen und setzen.

> Ausnahme: Wird der neue Zähler auf den **Endstand des alten voreingestellt** (Zählerstands-
> Übernahme), entsteht kein negatives Delta und **kein** genulltes Intervall. Bei Standard-Tauschen
> aber selten.

## ⚠️ Blindspot: neuer Zähler startet HÖHER als der alte

Der Reset-Guard erkennt einen Tausch **nur am negativen Delta** (neuer Stand < alter). Startet der
neue Zähler mit einem **höheren** absoluten Stand als der alte endete, ist das Delta **positiv** und
wird als **echter Verbrauch/Erzeugung verbucht** – ein potenziell grosser Bogus-Wert im
Übergangsintervall, **ohne** Log-Warnung. Aus den Werten allein ist ein Tausch also **nicht
zuverlässig erkennbar**.

**Bis eine explizite Tausch-Erkennung existiert (Zähler-Seriennummer im Payload oder
Wechsel-Marker), zwingend eine der folgenden Massnahmen:**
- Neuen Zähler beim Einbau **auf den Endstand des alten voreinstellen** (Zählerstands-Übernahme) →
  Kontinuität, kein Delta-Problem; **oder**
- Nach **jedem** Tausch das Übergangsintervall **manuell prüfen/korrigieren** – bei höherem Start
  warnt weder Log noch Reset-Guard.

> Geplante nachhaltige Lösung: Zähler-ID (Seriennummer) im MQTT-Payload + `zaehler_rohdaten`; die
> Aggregation setzt bei Serien-Wechsel eine neue Baseline (robust in beide Richtungen). Siehe
> offene Frage in `Specs/MQTT-Integration.md`.

## Checkliste

- [ ] Einheit **behalten** (nicht löschen/neu anlegen)
- [ ] Alter Endstand / neuer Anfangsstand **protokolliert** (mit Zeitstempel) – für die Korrektur des Übergangsintervalls
- [ ] Physischen Tausch **kurz** halten (wenig Offline-Zeit = wenige leere Intervalle); lastschwacher Zeitpunkt
- [ ] `messpunkt` angepasst, **falls** sich die Kennung ändert (bei geteiltem Bilanzmesspunkt beide Einheiten)
- [ ] MQTT-Topic des neuen Zählers = `zev/{orgId}/{messpunkt}/messwert` (nur MQTT)
- [ ] Nach dem Tausch: Statistik/Log kontrolliert (leere Intervalle der Offline-Zeit + genulltes Übergangsintervall)

## Verweise

- `Specs/MQTT-Integration.md` – MQTT-Subscriber, Topic-Struktur, Aggregation
- `Specs/Bilanzmesspunkt.md` – Einheiten-Typen `BEZUG`/`RUECKLIEFERUNG`, geteilter Messpunkt
- `Specs/Bilanzmodell.md` / `docs/Bilanzmodell.md` – Abrechnung aus der Bilanzmessung
- Code: `service/MqttIngestService.java`, `service/ZaehlerAggregationService.java`,
  `entity/Einheit.java` (`messpunkt`)
