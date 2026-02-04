# Spring Boot 4 Upgrade

## 1. Ziel & Kontext - Warum wird das Feature benötigt?
* **Was soll erreicht werden:** Upgrade der gesamten ZEV-Anwendung von Spring Boot 3.5.6 auf Spring Boot 4.0.x (mit Spring Framework 7, Spring Security 7, Jakarta EE 11)
* **Warum machen wir das:** Spring Boot 3.5 erreicht End-of-Support im Juni 2026. Spring Boot 4.0 bringt Modularisierung, JSpecify Null-Safety, Jackson 3, JUnit 6 und verbesserte AOT/GraalVM-Unterstützung.
* **Aktueller Stand:** Spring Boot 3.5.6, Java 21, Jakarta EE (bereits migriert von javax)

## 2. Funktionale Anforderungen (FR)

### FR-1: Ablauf
1. Drittbibliotheken auf Kompatibilität prüfen (Spring Boot Admin, Spring AI, JasperReports)
2. Parent POM auf Spring Boot 4.0.x aktualisieren
3. Starter-Umbenennungen durchführen (Modularisierung)
4. Test-Annotationen migrieren (@MockBean → @MockitoBean)
5. Jackson-Kompatibilität sicherstellen
6. Alle Tests durchlaufen lassen (Unit, Integration, E2E)
7. Docker-Images und docker-compose.yml validieren

### FR-2: Betroffene Module
* **backend-service** — Hauptaufwand: Starter-Renames, Security, Tests, Jackson, Spring AI
* **admin-service** — Spring Boot Admin Server Upgrade
* **frontend-service** — Starter-Renames in Maven POM
* **design-system** — Kein Spring-Code, nicht betroffen

### FR-3: Starter-Umbenennungen (Spring Boot 4 Modularisierung)

| Alt (3.x) | Neu (4.0) | Modul |
|---|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` | backend, frontend, admin |
| `spring-boot-starter-aop` | `spring-boot-starter-aspectj` | backend |
| `spring-boot-starter-oauth2-resource-server` | `spring-boot-starter-security-oauth2-resource-server` | backend |
| `spring-boot-starter-test` | `spring-boot-starter-test-classic` (Übergang) oder modulare Starter | backend |

## 3. Akzeptanzkriterien - Wann ist die Anforderung erfüllt?
* [x] Alle Maven-Module kompilieren mit Spring Boot 4.0.x
* [x] Alle Unit-Tests (24 Testklassen) laufen grün
* [x] Alle Integration-Tests (5 IT-Klassen) laufen grün
* [x] Alle E2E-Tests (Playwright) laufen grün
* [x] `docker-compose up --build` startet ohne Fehler
* [x] Keycloak-Authentifizierung funktioniert
* [x] PDF-Generierung (JasperReports) funktioniert
* [x] Spring AI / Claude-Integration funktioniert

## 4. Nicht-funktionale Anforderungen (NFR)

### NFR-1: Kompatibilität
* Keine Änderungen am Datenbankschema nötig
* Keine Flyway-Migrationen nötig
* REST-API bleibt unverändert (JSON-Format)
* Frontend (Angular) ist nicht betroffen

### NFR-2: Risiko
* Drittbibliotheken müssen kompatible Versionen haben
* Jackson 3 kann Serialisierungs-Verhalten ändern

## 5. Edge Cases & Fehlerbehandlung
* Falls Spring Boot Admin 3.4.1 nicht mit Spring Boot 4 kompatibel ist: Admin-Service vorübergehend deaktivieren oder auf kompatible Version warten
* Falls Spring AI 1.0.0-M6 nicht kompatibel ist: Spring AI vorübergehend durch direkten Anthropic SDK-Aufruf ersetzen

## 6. Abgrenzung / Out of Scope
* Kein GraalVM Native Image Build
* Keine Migration auf modulare Starter (classic Starter als Übergangslösung erlaubt)
* Keine Änderungen am Frontend (Angular)
* Kein Upgrade auf JUnit 6 (JUnit 5 bleibt kompatibel)

## 7. Offene Fragen
* Ist Spring Boot Admin (de.codecentric) in einer Spring Boot 4-kompatiblen Version verfügbar?
* Gibt es eine Spring AI Version die Spring Framework 7 unterstützt?
* Ist JasperReports 6.21.0 kompatibel mit Jakarta EE 11 / Servlet 6.1?
