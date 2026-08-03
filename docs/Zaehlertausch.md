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
- Endstand des alten Zählers und Anfangsstand des neuen Zählers protokollieren (für eine
  eventuelle Korrektur des Grenzintervalls, siehe unten).

### 2. Umschaltzeitpunkt auf eine Viertelstundengrenze legen
Den Tausch möglichst auf `:00` / `:15` / `:30` / `:45` legen.
- Auf der Grenze: kein Teilintervall wird angeschnitten – **kein** Datenverlust.
- Mitten im Intervall: **genau dieses eine Intervall** wird durch den Reset-Guard auf `0` gesetzt
  (kleiner, begrenzter Unterzähler – keine Korruption der übrigen Werte).

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
  genullte Grenzintervall (nur relevant, wenn nicht auf einer Viertelstundengrenze getauscht wurde).

## Sonderfall: exakte Abrechnung über die Tausch-Grenze

Wurde nicht sauber auf einer Viertelstundengrenze getauscht, ist das eine Grenzintervall
unterzählt (auf `0` gesetzt). Bei Bedarf lässt sich der betroffene `messwerte`-Datensatz manuell
korrigieren – aus dem protokollierten Endstand (alt) und Anfangsstand (neu) den tatsächlichen
Verbrauch/Erzeugung dieses Intervalls berechnen und setzen.

## Checkliste

- [ ] Einheit **behalten** (nicht löschen/neu anlegen)
- [ ] Umschaltung möglichst auf `:00/:15/:30/:45`
- [ ] `messpunkt` angepasst, **falls** sich die Kennung ändert (bei geteiltem Bilanzmesspunkt beide Einheiten)
- [ ] MQTT-Topic des neuen Zählers = `zev/{orgId}/{messpunkt}/messwert` (nur MQTT)
- [ ] Alter Endstand / neuer Anfangsstand protokolliert (für optionale Grenzintervall-Korrektur)
- [ ] Nach dem Tausch: Statistik/Log kontrolliert

## Verweise

- `Specs/MQTT-Integration.md` – MQTT-Subscriber, Topic-Struktur, Aggregation
- `Specs/Bilanzmesspunkt.md` – Einheiten-Typen `BEZUG`/`RUECKLIEFERUNG`, geteilter Messpunkt
- `Specs/Bilanzmodell.md` / `docs/Bilanzmodell.md` – Abrechnung aus der Bilanzmessung
- Code: `service/MqttIngestService.java`, `service/ZaehlerAggregationService.java`,
  `entity/Einheit.java` (`messpunkt`)
