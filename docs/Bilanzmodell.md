# Bilanzmodell – Fachliche Beschreibung

> Fachliche Erläuterung des Abrechnungsmodells „Bilanzmessung". Die technische Spezifikation steht in [`Specs/Bilanzmodell.md`](../Specs/Bilanzmodell.md).

## Kurzfassung

Das **Bilanzmodell** ist eine alternative Methode, um in einem ZEV (Zusammenschluss zum Eigenverbrauch) den **selbst verbrauchten Solarstrom** auf die einzelnen Mieter zu verteilen und abzurechnen. Statt die gemessene **Produktion** der Solaranlage zu verteilen, leitet das Bilanzmodell den Eigenverbrauch aus dem **Zähler am Netzanschluss** (dem Verrechnungszähler des Netzbetreibers) ab.

Das Modell ist **pro Mandant (ZEV) wählbar**. Standard bleibt das bisherige Modell „Producer-Messung".

## Ausgangslage: zwei Abrechnungsmodelle

| | **Producer-Messung** (Standard) | **Bilanzmodell** |
|---|---|---|
| Was wird verteilt? | Die gemessene **Produktion** der Solaranlage(n) | Der aus der **Netz-Bilanz** abgeleitete Eigenverbrauch |
| Grundlage | Producer-Zähler | Bilanzzähler am Netzanschluss (Bezug/Rücklieferung) |
| Batterie & Verluste | müssten separat modelliert werden | **automatisch enthalten** |
| Stimmt mit VNB-Rechnung überein? | nur mit Reconciliation-Aufwand | **per Konstruktion exakt** |

Beide Modelle bestimmen am Ende dasselbe: für jeden Mieter, welcher Teil seines Verbrauchs aus dem ZEV (günstiger interner Tarif) und welcher Teil aus dem Netz (Netzbetreiber-Tarif) stammt.

## Grundidee

Am **Netzanschluss** des Gebäudes misst der Verrechnungszähler des Netzbetreibers zwei Dinge:

- **Bezug** – Strom, der aus dem öffentlichen Netz **bezogen** wird.
- **Rücklieferung** – überschüssiger Solarstrom, der **ins Netz eingespeist** wird.

Der zentrale Gedanke: Alles, was die Mieter **verbrauchen**, aber **nicht** aus dem Netz bezogen wurde, muss **intern** (aus der Solaranlage und/oder dem Batteriespeicher) gedeckt worden sein. Das ist der ZEV-Eigenverbrauch:

> **ZEV-Eigenverbrauch = Gesamtverbrauch aller Mieter − Netzbezug**

(Kann der Wert rechnerisch negativ werden – z. B. wenn eine Batterie aus dem Netz geladen wird –, wird er auf 0 begrenzt.)

Dieser Eigenverbrauch wird anschliessend – genau wie im bisherigen Modell – nach dem gewählten Verteilschlüssel auf die Mieter aufgeteilt.

## Rechenbeispiel (ein 15-Minuten-Intervall)

Angenommen in einem Intervall:

- Wohnung A verbraucht 5 kWh, Wohnung B verbraucht 5 kWh → **Gesamtverbrauch = 10 kWh**
- Der Bilanzzähler zeigt **Netzbezug = 4 kWh**

Daraus folgt:

- **ZEV-Eigenverbrauch = 10 − 4 = 6 kWh** (intern aus Solar/Batterie gedeckt)
- Verteilung (gleichmässig): je Wohnung **3 kWh ZEV-Anteil**
- **Netz-Anteil** je Wohnung: 5 − 3 = 2 kWh, zusammen **4 kWh**

Die Summe der Netz-Anteile (4 kWh) entspricht **exakt** dem gemessenen Netzbezug (4 kWh). Genau das ist der Kern des Bilanzmodells: Die interne Abrechnung summiert sich **verrechnungstreu** auf die externe Rechnung des Netzbetreibers – es entsteht keine Differenz.

Über eine ganze Abrechnungsperiode (z. B. ein Quartal) wird diese Rechnung für jedes 15-Minuten-Intervall durchgeführt und aufsummiert.

## Verteilschlüssel

Der ZEV-Eigenverbrauch wird mit demselben Verfahren verteilt wie bisher:

- **Gleichmässig (EQUAL_SHARE):** alle Mieter erhalten einen gleich grossen Anteil (jeweils begrenzt auf ihren eigenen Verbrauch).
- **Proportional (PROPORTIONAL):** Mieter mit höherem Verbrauch erhalten proportional mehr vom Eigenverbrauch.

In beiden Fällen kann ein Mieter nie mehr ZEV-Anteil erhalten, als er selbst verbraucht hat.

## Auswirkung auf die Rechnung

Für die Mieter ändert sich an der **Rechnungsstruktur nichts**:

- **ZEV-Anteil** → zum (günstigeren) ZEV-Tarif
- **Netz-Anteil** (Verbrauch − ZEV-Anteil) → zum Netzbetreiber-Tarif (VNB)

Nur die **Herkunft** des ZEV-Anteils ändert sich (aus der Bilanz statt aus der Producer-Verteilung). Die Solaranlagen-Betreiber (Producer) erhalten wie bisher nur eine Grundgebühr-Rechnung; die Bilanz-Zähler selbst werden nicht verrechnet.

## Vorteile

- **Verrechnungstreue:** Die Summe der intern abgerechneten Netz-Anteile entspricht exakt der Rechnung des Netzbetreibers – keine ungeklärte Differenz.
- **Batteriespeicher & Verluste automatisch berücksichtigt:** Weil nur „Verbrauch minus Netzbezug" zählt, sind Batterieladung/-entladung, Wirkungsgradverluste und weitere Effekte „hinter dem Zähler" bereits korrekt enthalten, ohne sie einzeln modellieren zu müssen.
- **Nähe zur Realität:** Der Verrechnungszähler des Netzbetreibers gilt als „Ground Truth" des tatsächlichen Netzaustauschs.

## Voraussetzungen

- Am Netzanschluss muss ein **zuverlässiger Bilanzzähler** vorhanden sein, dessen Werte (Bezug und Rücklieferung) ins System gelangen – per CSV-Upload oder automatisch über die MQTT-Anbindung.
- Fehlt der Bilanzzähler bzw. dessen Daten, kann im Bilanzmodell nicht abgerechnet werden (siehe „Grenzfälle").

## Wann welches Modell?

- **Bilanzmodell**, wenn ein Bilanzzähler am Netzanschluss vorhanden ist und/oder ein **Batteriespeicher** im Spiel ist bzw. die interne Abrechnung exakt zur Netzbetreiber-Rechnung passen soll.
- **Producer-Messung** (Standard), wenn kein Bilanzzähler vorhanden ist und die Verteilung direkt aus der gemessenen Produktion erfolgen soll.

Die Wahl trifft der Betreiber bewusst je ZEV; es gibt **keine** automatische Umschaltung.

## Bedienung

- Der Modus wird in den **Einstellungen** des jeweiligen ZEV gewählt („Verteilmodus": *Producer-Messung* oder *Bilanzmessung*). Dafür ist die Berechtigung zum Bearbeiten der Einstellungen nötig.
- Der gewählte Modus wirkt auf **jede** anschliessende Solarverteilung – sowohl bei der manuell ausgelösten Berechnung als auch bei der automatischen Verarbeitung nach dem Eintreffen neuer Zählerdaten.
- In der **Statistik** wird der aktuell wirksame Modus angezeigt.

## Auswirkung auf die Statistik

- Der aktive Verteilmodus ist in der Statistik sichtbar.
- Im Bilanzmodus wird zusätzlich die **tatsächlich gemessene Rücklieferung** aus dem Bilanzzähler ausgewiesen.
- Hinweis: Die bisherigen Plausibilitäts-Vergleiche „berechneter Bezug ↔ Bilanz-Bezug" werden im Bilanzmodus **naturgemäss immer aufgehen** (die Verteilung ist ja aus der Bilanz abgeleitet). Sie verlieren dort ihre Kontrollfunktion; ein Hinweis macht das kenntlich.

## Grenzfälle

- **Fehlende Bilanzdaten:** Fehlt der Bilanzzähler oder fehlen dessen Messwerte für ein Intervall, wird der Verteillauf **abgebrochen** (es werden keine unvollständigen Werte geschrieben). Bei manueller Auslösung erscheint eine Fehlermeldung mit Angabe des betroffenen Zeitpunkts; beim automatischen Lauf wird der betroffene ZEV übersprungen und ein Fehler protokolliert, die übrigen laufen weiter.
- **Netzbezug grösser als Verbrauch** (z. B. Batterie lädt nachts aus dem Netz): Der ZEV-Eigenverbrauch für dieses Intervall ist 0 – niemand erhält einen ZEV-Anteil.
- **Fehlende Rücklieferungs-Messung:** beeinträchtigt die Abrechnung **nicht** (nur eine statistische Kennzahl bleibt unvollständig).
- **Nachträglicher Moduswechsel:** Eine erneute Berechnung überschreibt die Verteilwerte für den gewählten Zeitraum. Bereits erstellte Rechnungen werden **nicht** automatisch korrigiert – der Betreiber wird auf diese Auswirkung hingewiesen.

## Abgrenzung

- **Keine kWh-Vergütung an Producer** – Solaranlagen-Betreiber bleiben bei der Grundgebühr.
- **Keine explizite Batterie-Modellierung nötig** – die Batterie ist im Bilanzmodell implizit über den Netzanschluss abgebildet. (Eine explizite Speicher-Variante ist separat beschrieben und für andere Mandanten gedacht.)
- **Rückwärtskompatibel:** Solange der Modus nicht umgestellt wird, verhält sich das System unverändert wie mit dem Standardmodell.

---

*Technische Details (Datenmodell, Verteilalgorithmus, Fehlerbehandlung, Tests): siehe [`Specs/Bilanzmodell.md`](../Specs/Bilanzmodell.md) und [`Specs/Bilanzmodell_Umsetzungsplan.md`](../Specs/Bilanzmodell_Umsetzungsplan.md).*
