# MesswerteUpload: Mehrere Dateien gleichzeitig importieren

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Der Benutzer soll mehrere CSV-Dateien in einem einzigen Upload-Vorgang importieren können, anstatt jede Datei einzeln hochzuladen.
* **Warum machen wir das:** Beim monatlichen Messwerte-Import müssen typischerweise viele Dateien (eine pro Einheit) hochgeladen werden. Der bisherige Einzeldatei-Workflow ist zeitaufwändig und fehleranfällig.
* **Aktueller Stand:** Aktuell kann nur eine Datei auf einmal in die Dropzone gezogen werden. Pro Datei wird: (1) Einheit per KI automatisch erkannt, (2) Datum aus dem Dateiinhalt gelesen, (3) Die Datei einzeln importiert.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?

### FR-1: Ablauf / Flow
1. Der Benutzer zieht eine oder mehrere CSV-Dateien in die Dropzone (oder klickt und wählt mehrere Dateien via Dateidialog aus).
2. Für jede Datei wird automatisch im Hintergrund:
   a. Die Einheit per KI aus dem Dateinamen bestimmt (wie bisher, parallel für alle Dateien)
   b. Das Datum aus dem Dateiinhalt gelesen (wie bisher)
3. Die Dateien werden in einer Tabelle/Liste unterhalb der Dropzone angezeigt. Jede Zeile enthält:
   - Dateiname
   - Erkannte Einheit (Dropdown zum manuellen Ändern)
   - Erkanntes Datum (Datumsfeld zum manuellen Ändern)
   - Status-Anzeige (KI erkennt / automatisch erkannt / bitte prüfen / Fehler)
   - Entfernen-Button (X) für die einzelne Datei
4. Der Benutzer kann Einheit und Datum pro Datei manuell anpassen.
5. Der Benutzer klickt auf den "Importieren"-Button, um alle Dateien in der Liste zu importieren.
6. Während des Imports wird ein Fortschrittsindikator pro Datei angezeigt (hochgeladen / Fehler).
7. Am Ende wird eine Zusammenfassung angezeigt (z.B. "5 von 5 Dateien erfolgreich importiert").

### FR-2: Dateiliste (Staging-Bereich)
* Die Liste zeigt alle noch zu importierenden Dateien an.
* Jede Zeile ist editierbar: Einheit (Select) und Datum (Date-Input).
* Jede Zeile hat einen Entfernen-Button, um die Datei vor dem Import aus der Liste zu löschen.
* Neue Dateien können nachträglich zur Dropzone hinzugezogen werden – sie werden zur bestehenden Liste hinzugefügt (kein Reset der Liste).
* Doppelte Dateinamen werden abgelehnt (Fehlermeldung, Datei wird nicht zur Liste hinzugefügt).

### FR-3: Importvorgang
* Der "Importieren"-Button ist nur aktiv, wenn mindestens eine Datei in der Liste ist, kein KI-Matching mehr läuft, und alle Dateien eine Einheit und ein Datum haben.
* Beim Import werden alle Dateien sequenziell oder parallel an den bestehenden Backend-Endpunkt `POST /api/messwerte/upload` gesendet.
* Der Status jeder Datei in der Liste wird während des Imports aktualisiert (Spinner / Erfolg / Fehler).
* Bei einem Fehler bei einer Datei werden die restlichen Dateien trotzdem importiert.
* Nach erfolgreichem Import einer Datei wird diese aus der Liste entfernt (oder als "importiert" markiert – siehe Offene Fragen).
* Nach Abschluss des Imports erscheint eine Zusammenfassungsmeldung.

### FR-4: Rückwärtskompatibilität
* Der bestehende Backend-Endpunkt `POST /api/messwerte/upload` (einzelne Datei) wird unverändert weiterverwendet.
* Es wird kein neuer Batch-Endpunkt benötigt; der Frontend-Client ruft den bestehenden Endpunkt pro Datei auf.

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] Der Benutzer kann mehrere CSV-Dateien gleichzeitig in die Dropzone ziehen
* [ ] Der Benutzer kann mehrere Dateien via Dateidialog (multi-select) auswählen
* [ ] Jede gedropte Datei erscheint als Zeile in der Dateiliste mit Dateiname, Einheit-Dropdown und Datums-Feld
* [ ] Die Einheit wird automatisch per KI erkannt und im Dropdown vorgewählt
* [ ] Das Datum wird automatisch aus dem Dateiinhalt gelesen und im Datumsfeld vorausgefüllt
* [ ] Der Benutzer kann Einheit und Datum pro Datei manuell ändern
* [ ] Der Benutzer kann einzelne Dateien aus der Liste entfernen (X-Button)
* [ ] Neue Dateien können zur bestehenden Liste hinzugefügt werden, ohne die vorhandenen zu löschen
* [ ] Doppelte Dateien (gleicher Dateiname) werden nicht zur Liste hinzugefügt und eine Fehlermeldung wird angezeigt
* [ ] Nicht-CSV-Dateien werden abgelehnt mit einer Fehlermeldung
* [ ] Der "Importieren"-Button ist deaktiviert, solange KI-Matching läuft oder eine Datei keine Einheit/kein Datum hat
* [ ] Beim Klicken auf "Importieren" werden alle Dateien der Liste importiert
* [ ] Der Import-Status jeder Datei wird in der Liste angezeigt (in Bearbeitung / Erfolg / Fehler)
* [ ] Bei einem Fehler bei einer Datei werden die anderen Dateien trotzdem importiert
* [ ] Nach dem Import wird eine Zusammenfassung angezeigt (z.B. "5 von 5 erfolgreich")
* [ ] Die Dateiliste ist nach dem vollständigen Import leer (nur erfolgreich importierte Dateien werden entfernt)
* [ ] Der Status "Einheit automatisch erkannt" (grün) und "Bitte prüfen" (gelb) wird pro Datei angezeigt

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* Das KI-Matching für alle Dateien soll parallel gestartet werden (nicht sequenziell), damit die Wartezeit bei N Dateien nicht N × 2s beträgt.
* Jede einzelne Datei darf maximal 2 Sekunden für das KI-Matching benötigen (wie bisher).
* Der Import-Button reagiert sofort; Importe können parallel oder sequenziell durchgeführt werden.

### NFR-2: Sicherheit
* Zugriff nur für eingeloggte Benutzer mit Rolle `zev` oder `zev_admin` (wie bisher, Route `/upload`).
* Nur CSV-Dateien werden akzeptiert (Validierung im Frontend und Backend).

### NFR-3: Kompatibilität
* Der bestehende Backend-Endpunkt `POST /api/messwerte/upload` bleibt unverändert.
* Keine Datenbankmigrationen erforderlich.
* Das bestehende KI-Matching (EinheitMatchingService, `/api/einheiten/match`) wird unverändert weiterverwendet.

## 5. Edge Cases & Fehlerbehandlung
* **Leere Dropzone:** Kein "Importieren"-Button sichtbar oder deaktiviert; Meldung "Keine Dateien ausgewählt".
* **Nicht-CSV-Datei:** Datei wird nicht zur Liste hinzugefügt; Fehlermeldung pro abgelehnter Datei.
* **Doppelter Dateiname:** Datei wird nicht zur Liste hinzugefügt; Fehlermeldung ("Datei 'xyz.csv' bereits in der Liste").
* **KI nicht erreichbar:** Einheit bleibt ungesetzt, Status zeigt Fehler; Benutzer muss Einheit manuell wählen; Import-Button bleibt deaktiviert bis alle Einheiten gesetzt sind.
* **Datum nicht erkennbar:** Datumsfeld bleibt leer; Benutzer muss Datum manuell eingeben; Import-Button bleibt deaktiviert.
* **Netzwerkfehler beim Import:** Die betroffene Datei erhält Status "Fehler" mit Fehlermeldung; andere Dateien werden weiter importiert; Fehlschläge bleiben in der Liste (nicht entfernt) damit der Benutzer es erneut versuchen kann.
* **Sehr viele Dateien (>20):** Kein technisches Limit, aber die Liste scrollt; evtl. wird eine Warnung angezeigt (offene Frage).
* **Import bereits laufend:** "Importieren"-Button wird während des Imports deaktiviert.
* **Alle Dateien haben denselben Dateinamen:** Nur die erste wird zur Liste hinzugefügt, die weiteren werden mit Fehlermeldung abgelehnt.

## 6. Abhängigkeiten & betroffene Funktionalität
* **Voraussetzungen:**
  - KI-Matching (`EinheitMatchingService`, `/api/einheiten/match`) muss funktionieren (bereits implementiert, Spec: `Messwerte-mit-KI.md`)
  - Backend-Upload-Endpunkt `POST /api/messwerte/upload` (MesswerteController) bereits vorhanden
* **Betroffener Code (Frontend):**
  - `frontend-service/src/app/components/messwerte-upload/messwerte-upload.component.ts` – vollständige Überarbeitung
  - `frontend-service/src/app/components/messwerte-upload/messwerte-upload.component.html` – vollständige Überarbeitung
  - `frontend-service/src/app/components/messwerte-upload/messwerte-upload.component.css` – Styles für die neue Dateiliste
* **Betroffener Code (Backend):** Keine Änderungen erforderlich
* **Betroffener Code (Design System):** Ggf. neue CSS-Klassen für die Dateiliste, falls nicht bereits durch `.zev-table` abgedeckt
* **Übersetzungen:** Neue i18n-Keys nötig (neue Flyway-Migration)
* **Datenmigration:** Nicht erforderlich

## 7. Abgrenzung / Out of Scope
* Kein neuer Batch-Endpunkt im Backend – der bestehende Einzeldatei-Endpunkt wird pro Datei aufgerufen.
* Keine Persistierung des Dateilisten-Zustands über Browser-Sessions hinweg.
* Kein automatischer Import ohne Benutzerbestätigung.
* Keine Fortschrittsanzeige innerhalb einer einzelnen Datei (nur Status: lädt / fertig / Fehler).
* Keine Vorschau des CSV-Inhalts.
* Kein Drag-and-Drop zum Umsortieren der Dateireihenfolge.

## 8. Offene Fragen
* **Verhalten nach Import:** Sollen erfolgreich importierte Dateien sofort aus der Liste verschwinden, oder sollen sie mit Status "Importiert" (grün) sichtbar bleiben, bevor der Benutzer die Liste manuell leert? Annahme: sofort entfernen.
* **Maximale Anzahl Dateien:** Gibt es ein sinnvolles Limit (z.B. 20 Dateien gleichzeitig)? Annahme: kein Limit, aber Hinweis bei mehr als 20.
* **Parallelität des Imports:** Sollen alle Importe gleichzeitig gestartet werden (parallel) oder nacheinander (sequenziell, um den Server nicht zu überlasten)? Annahme: sequenziell.
* **Fehlerhafte Dateien nach Import:** Sollen fehlgeschlagene Dateien in der Liste bleiben (für erneuten Versuch) oder soll der Benutzer sie erneut droppen müssen? Annahme: in der Liste bleiben.
