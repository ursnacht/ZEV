# Generelle Anforderungen

## 1. Ziel & Kontext
* Hier sind generelle Anforderungen aufgeführt, die bei jeder Umsetzung einer Anforderung berücksichtigt werden sollen.
* Die Anwendung wird so einheitlich erstellt und bekommt ein einheitliches Look & Feel

## 2. Funktionale Anforderungen (Functional Requirements)
* Die funktionalen Anforderungen werden in User Stories aufgeführt.
* Mach Texte immer mehrsprachig
  * Verwende den TranslationService.
  * Füge neue Text-Keys in die Datenbank ein. Verwende "ON CONFLICT (key) DO NOTHING", um Konflikte zu vermeiden.

## 3. Technische Spezifikationen (Technical Specs)
* Verwende immer flyway für die Datenbankmigration
* Berücksichtige immer das Design System
* Ergänze das Design System falls notwendig
* Verwende Spring Boot

## 4. Nicht-funktionale Anforderungen
* Beachte, dass die Benutzer immer mit keycloak authentifiziert sind
* Füge sinnvolles Logging hinzu
* Füge sinnvolles Exception Handling hinzu
* Füge sinnvolle Validierung hinzu
* Füge sinnvolle Performance Optimierung hinzu
* Füge sinnvolle Sicherheit hinzu

## 5. Testing
* Ergänze die Tests wo nötig (Unit Tests, Integration Tests, End-to-End Tests). 
* Beachte immer auch die Datei Specs/AutomatisierteTests.md
