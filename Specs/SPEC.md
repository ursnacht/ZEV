# [Titel des Features / der Änderung]

## 1. Ziel & Kontext
* **Was soll erreicht werden:** (Kurze Zusammenfassung)
* **Warum machen wir das:** (Business Value / Hintergrund)
* **Aktueller Stand:** Wie funktioniert es jetzt? (Falls relevant)

## 2. Funktionale Anforderungen (Functional Requirements)
* **User Story:** Als [Rolle] möchte ich [Aktion], damit [Nutzen].
* **Ablauf / Flow:**
  1. User klickt auf...
  2. System validiert...
  3. System speichert...
* **Akzeptanzkriterien (Definition of Done):**
  *   [ ] Muss X können
  *   [ ] Darf Y nicht zulassen
  *   [ ] Muss Fehlermeldung Z anzeigen, wenn...

## 3. Technische Spezifikationen (Technical Specs)
* **API-Änderungen:**
  * `POST /api/resource`: Request/Response Body Definition (JSON)
* **Datenmodell:** Welche Tabellen/Felder müssen geändert oder erstellt werden?
* **Algorithmen/Logik:** Spezielle Berechnungsregeln (wie beim Solar-Algorithmus).

## 4. Nicht-funktionale Anforderungen
* Performance (z.B. "muss unter 200ms antworten")
* Sicherheit (z.B. "nur für Admin sichtbar")
* Kompatibilität

## 5. Edge Cases & Fehlerbehandlung
* Was passiert bei leeren Eingaben?
* Was passiert bei Netzwerkfehlern?