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
*   Setze die Standard-Toolchain für Spring Boot ein: JUnit 5 als Test-Runner, das Framework Mockito zur Simulation von Abhängigkeiten und AssertJ für lesbare, flüssige Assertions.
    * Unit Tests: JUnit 5, Mockito (@InjectMocks, @Mock, @InjectMocks), AssertJ. Verwende ArgumentCaptor für die Validierung von komplexen Datenstrukturen. Weise auf den Gebrauch von @MockBean hin, so dass die Architektur verbessert werden kann (Trennung Gechäftslogik und Spring Infrastruktur).
    * Integration Tests: Verwende "Spring Test Slices" (@WebMvcTest, @JsonTest, @DataJpaTest für die das Testen der Persistenz-Schickt), um den Spring Application Context zu minimieren und so die Testgeschwindigkeit zu erhöhen. Verwende Testcontainers und deaktiviere die Autokonfiguration der In-Memory-Datenbanken. Vermeide also @SpringBootTest.
    * E2E Tests: RestTestClient für Backend-API-Tests, Playwright für Frontend-E2E-Tests. Beide sollen über Maven getriggert werden (z.B. mittels `frontend-maven-plugin` oder `exec-maven-plugin` in einer `verify` oder `e2e-test` Phase).
    * Namenskonventionen (Backend):
        * Unit Tests: `*Test.java` (werden von Surefire ausgeführt)
        * Integration Tests: `*IT.java` (werden von Failsafe ausgeführt)
*   Phasen-Trennung im Maven-basierten Build-System:
    *   Maven Surefire Plugin für die Ausführung der schnellen Unit Tests in der Maven-Phase test.
    *   Maven Failsafe Plugin für die Ausführung der Integration Tests in der Build-Phase integration-test. Implementiere in der post-integration-test-Phase ein sauberes Teardown (z.B. Testcontainer herunterfahren), um Ressourcenlecks zu vermeiden.
*   Frontend Testing (Angular):
    *   Unit Tests: Verwende Jasmine als Testing-Framework und Karma als Test-Runner (Angular Standard). Fokus auf Komponenten-Logik, Services und Pipes.
    *   Integration: Teste das Zusammenspiel von Parent/Child-Komponenten und Services mittels TestBed.
    *   Namenskonvention: `*.spec.ts` für alle Frontend-Tests.
