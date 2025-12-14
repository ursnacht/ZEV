# Generelle Anforderungen

## 1. Ziel & Kontext
* **Was soll erreicht werden?** Hier sind generelle Anforderungen aufgeführt, die bei jeder Umsetzung einer Anforderung berücksichtigt werden sollen.
* **Warum machen wir das?** Die Anwendung wird so einheitlich erstellt und bekommt ein einheitliches Look & Feel.

## 2. Funktionale Anforderungen (Functional Requirements)
* Die funktionalen Anforderungen werden in User Stories aufgeführt.
* Mach Texte immer mehrsprachig
  * Verwende den TranslationService.
  * Füge neue Text-Keys in die Datenbank ein. Verwende "ON CONFLICT (key) DO NOTHING", um Konflikte zu vermeiden.

## 3. Technische Spezifikationen (Technical Specs)
* Verwende Spring Boot
* Verwende immer flyway für die Datenbankmigration
* Styles
  * Berücksichtige immer das Design System im Maven Module design-system und verwende möglichst bestehende Styles.
  * Füge neue Styles in das Design System ein
  * Ergänze bei neuen Styles den Design System Showcase

## 4. Nicht-funktionale Anforderungen
* Beachte, dass die Benutzer immer mit keycloak authentifiziert sind
* Füge sinnvolles Logging hinzu
* Verwende korrektes Exceptionhandling: Exceptions im Backend immer abfangen und eine sinnvolle Fehlermeldungen ins Log schreiben und an das Frontend melden.
* Füge sinnvolle Validierung hinzu
* Füge sinnvolle Performance Optimierung hinzu
* Füge sinnvolle Sicherheit hinzu

## 5. Testing
* Ergänze die Tests wo nötig (Unit Tests, Integration Tests, End-to-End Tests). 
* Beachte immer auch die Datei Specs/AutomatisierteTests.md für weitere Anforderungen bezüglich Testing
