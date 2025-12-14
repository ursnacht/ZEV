# [Automatisiertes Testing]

## 1. Ziel & Kontext
*   Die Anwendung soll automatisiert getestet werden. 
*   Wir tun dies, um sicher zu sein, dass bei Änderungen die Anwendung immer noch so funktioniert, wie sie soll.
*   Die Testpyramide soll beachtet werden, die eine quantitative Verteilung der Testarten verlangt:
    * Unit Tests: 70-80%
    * Integration Tests: 15-20%
    * End-to-End Tests: 5-10%
*   Die automatisierte Teststrategie muss nahtlos in den Build-Prozess integriert werden, wobei Unit- und Integrationstests strikt voneinander getrennt ablaufen müssen. Dies gewährleistet eine schnelle Ausführung der Unit Tests und eine sichere Verwaltung der Ressourcen für die Integrationstests.

## 2. Technische Spezifikationen (Technical Specs)
*   Setze die Standard-Toolchain für Spring Boot ein
    * Unit Tests: JUnit mit Jupiter und Assertions
      * Namenskonventionen: `*Test.java`
      * Werden von Surefire ausgeführt
    * Integration Tests: Verwende "Spring Test Slices" (@WebMvcTest, @JsonTest, @DataJpaTest für die das Testen der Persistenz-Schicht), um den Spring Application Context zu minimieren und so die Testgeschwindigkeit zu erhöhen. 
      * Verwende Testcontainers und deaktiviere die Autokonfiguration der In-Memory-Datenbanken. Vermeide die Verwendung von @SpringBootTest.
      * Verwende ArgumentCaptor für die Validierung von komplexen Datenstrukturen. Weise auf den Gebrauch von @MockBean hin, so dass die Architektur verbessert werden kann (Trennung Gechäftslogik und Spring Infrastruktur).
      * Namenskonvention: `*IT.java`
      * Werden von Failsafe ausgeführt
      * Implementiere in der post-integration-test-Phase ein sauberes Teardown (z.B. Testcontainer herunterfahren, Testdaten löschen), um Ressourcenlecks zu vermeiden. 
    * E2E Tests: RestTestClient für Backend-API-Tests, Playwright für Frontend-E2E-Tests. Beide sollen über Maven getriggert werden (z.B. mittels `frontend-maven-plugin` in einer `verify` oder `integration-test` Phase).
        *  Namenskonvention: `*.spec.ts` für alle Frontend-Tests.
