# Intelligenter Upload der Messwerte

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Die Anwendung soll vom Dateinamen die Einheit automatisch ableiten können.
* **Warum machen wir das:** Die Wahl der richtigen Datei zur gewählten Einheit ist fehleranfällig und mühsam und soll daher automatisiert werden.
* **Aktueller Stand:** Aktuell muss die Datei und die Einheit durch den Benutzer manuell ausgewählt werden.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?
### FR-1: Ablauf / Flow
1. Der Benutzer wählt eine Datei auf der Seite /upload (wie bisher)
2. Die Anwendung bestimmt aus dem Dateinamen die richtige Einheit und zeigt sie an
3. Die Anwendung bestimmt das Datum (wie bisher)
4. Der Benutzer kann gewählte Einheit und Datum prüfen (wie bisher)
5. Der Benutzer klickt den Button "Importieren" (wie bisher)
6. Die Datei wird eingelesen (wie bisher)

### FR-2: Nutzung von KI
* Für die Bestimmung der Einheit aus dem Dateinamen soll KI (Claude) verwendet werden.
* Die Namen der Einheiten und der Dateien sind nicht vorgegeben. Trotzdem soll versucht werden ein Match zu finden.

### FR-3: Beispiele von Dateinamen
* 2025-07-1-li.csv -> 1. Stock links
* 2025-08-allg.csv -> Allgemein
* 2025-08-Allgemein.csv -> Allgemein
* 2025-05-pv-anlage.csv -> PV-Anlage (Producer)
* 2025-04-pv.csv -> PV-Anlage (Producer)
* 2025-07-p-li.csv -> Parterre links

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] Das System kann aus dem Dateinamen die Einheit korrekt bestimmen
* [ ] Exceptions im Backend werden im Frontend angezeigt


## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Die Bestimmung der Einheit darf nicht länger als 2 Sekunden dauern.

### NFR-2: Sicherheit
* wie bisher 


## 5. Edge Cases & Fehlerbehandlung
* Wenn die Einheit nicht bestimmt werden kann, soll eine Fehlermeldung angezeigt werden
* Wenn die KI nicht erreichbar ist, soll eine Fehlermeldung angezeigt werden

## 6. Abgrenzung / Out of Scope
* Die Messdaten werden NIE automatisch hochgeladen

## 7. Offene Fragen
* Wie kann KI genutzt werden?