# Übersetzungstexte löschen

## 1. Ziel & Kontext
*   Übersetzungstexte sollen gelöscht werden können, so dass ich die Texte löschen kann, die nicht mehr verwendet werden.

## 2. Funktionale Anforderungen (Functional Requirements)
*   Als Admin möchte ich die Übersetzungstexte löschen, damit ich die Texte löschen kann, die nicht mehr verwendet werden.
*   **Ablauf / Flow:**
    1. Admin klickt auf "Übersetzungstexte"
    2. Admin sucht den zu löschenden Text
    3. Admin klickt auf "Löschen"
    4. Es erscheint eine Bestätigungsmeldung
    5. Admin bestätigt die Bestätigungsmeldung und der Eintrag wird gelöscht
    
## 3. Technische Spezifikationen (Technical Specs)
*   **API-Änderungen:**
    *  `DELETE /api/translations/{id}`
*   Am Datenmodell muss nichts geändert werden

## 4. Nicht-funktionale Anforderungen
*   Performance (z.B. "muss unter 200ms antworten")
*   Sicherheit (z.B. "nur für Admin sichtbar")
*   Kompatibilität

## 5. Edge Cases & Fehlerbehandlung
*   Was passiert bei leeren Eingaben?
*   Was passiert bei Netzwerkfehlern?