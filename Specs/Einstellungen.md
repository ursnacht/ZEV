# Einstellungen pro Mandant

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Die Konfiguration, die jetzt im `application.yml` steht, wird in die Datenbank verschoben werden.
* **Warum machen wir das:** So kann jeder Mandant seine Einstellungen eigenständig bearbeiten und speichern.  
* **Aktueller Stand:** Aktuell gibt es nur eine globale Konfiguration im `application.yml` im backend-service.

## 2. Funktionale Anforderungen (FR) - Was soll das System tun?
### FR-1: Ablauf / Flow
1. Der Admin kann in der Navigation den Menüpunkt "Einstellungen" wählen. Der neue Menüpunkt liegt vor den "Übersetzungen".
2. Das System präsentiert eine neue Seite `Einstellungen`, auf der der Admin die Daten erfassen und bearbeiten kann:
   * Rechnung
     * Zahlungsfrist
     * iban
     * steller
       * name
       * strasse
       * plz
       * ort
3. Das System speichert die Daten in der Datenbank.
4. Das System verwendet bei der Rechnungsgenerierung die Daten neu aus der Datenbank.

### FR-2: Persistierung
* Die Daten werden in einer neuen DB-Tabelle `einstellungen` gespeichert.
  * Spalten
    * org_id
    * konfiguration
* Die Konfiguration aus `application.yml` wird als JSON-Struktur pro Mandant gespeichert:
```
  {
    "rechnung": {
      "zahlungsfrist": "30 Tage",
      "iban": "CH70 0630 0016 9464 5991 0",
      "steller": {
        "name": "Urs Nacht",
        "strasse": "Hangstrasse 14a",
        "plz": "3044",
        "ort": "Innerberg"
      },
    }
  }
```
* Die ganze YAML-Struktur `rechnung` in `application.yml` und der entsprechende Code kann gelöscht werden. 
  * Die Adresse wird auch nicht mehr benötigt, da die Adresse bei den Mietern konfiguriert ist. 

### FR-3: Layout
* Verwende das design-system
* Verwende ein neues Icon: wenn möglich Zahnrad, sonst Tool

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt? (testbar)
* [ ] neuer Menüpunkt in der Navigation `Einstellungen`
* [ ] neue Seite `Einstellungen` zum Bearbeiten der Konfiguration
* [ ] Die Konfiguration wird pro Mandant gespeichert
* [ ] Alle Felder sind Pflichtfelder


## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Performance
* -

### NFR-2: Sicherheit
* Benötigte Rolle: `zev_admin`  

### NFR-3: Kompatibilität
* Die Rechnungsgenerierung muss neu die Daten aus der Datenbank beziehen


## 5. Edge Cases & Fehlerbehandlung
* -

## 6. Abgrenzung / Out of Scope
* -

## 7. Offene Fragen
* -