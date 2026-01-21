package ch.nacht.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import jakarta.persistence.Entity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * ArchUnit-Tests zur Sicherstellung der Architektur-Konventionen.
 *
 * Die Architektur folgt dem typischen Spring-Schichten-Modell:
 * Controller -> Service -> Repository -> Entity
 *
 * Config-Klassen bilden eine Querschnittsschicht, die von Services
 * verwendet werden darf und ihrerseits Services verwenden kann
 * (z.B. Interceptors).
 */
@DisplayName("Architektur-Tests")
class ArchitectureTest {

    private static final String BASE_PACKAGE = "ch.nacht";

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setup() {
        importedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);
    }

    @Nested
    @DisplayName("Schichten-Architektur")
    class LayeredArchitectureTests {

        @Test
        @DisplayName("Schichten-Regeln werden eingehalten")
        void layeredArchitectureShouldBeRespected() {
            // Config ist eine Querschnittsschicht, die bidirektional mit Service kommuniziert
            // (Interceptors brauchen Services, Services brauchen Config-Werte)
            // DTOs dürfen Enums aus Entity verwenden (z.B. EinheitTyp, TarifTyp)
            ArchRule rule = layeredArchitecture()
                .consideringAllDependencies()
                .layer("Controller").definedBy("..controller..")
                .layer("Service").definedBy("..service..")
                .layer("Repository").definedBy("..repository..")
                .layer("Entity").definedBy("..entity..")
                .layer("DTO").definedBy("..dto..")
                .layer("Config").definedBy("..config..")
                .layer("Exception").definedBy("..exception..")

                .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
                .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Service", "Config")
                .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
                .whereLayer("Entity").mayOnlyBeAccessedByLayers("Controller", "Service", "Repository", "Config", "DTO")
                .whereLayer("DTO").mayOnlyBeAccessedByLayers("Controller", "Service")
                .whereLayer("Config").mayOnlyBeAccessedByLayers("Controller", "Service", "Config")
                .whereLayer("Exception").mayOnlyBeAccessedByLayers("Controller", "Service", "Config");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Controller sollten nur auf Services zugreifen")
        void controllersShouldOnlyDependOnServices() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..repository..");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Repositories sollten nicht auf Services zugreifen")
        void repositoriesShouldNotDependOnServices() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("..repository..")
                .should().dependOnClassesThat().resideInAPackage("..service..");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Repositories sollten nicht auf Controller zugreifen")
        void repositoriesShouldNotDependOnControllers() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("..repository..")
                .should().dependOnClassesThat().resideInAPackage("..controller..");

            rule.check(importedClasses);
        }
    }

    @Nested
    @DisplayName("Namenskonventionen")
    class NamingConventionTests {

        @Test
        @DisplayName("Controller-Klassen sollten mit 'Controller' enden")
        void controllersShouldEndWithController() {
            ArchRule rule = classes()
                .that().resideInAPackage("..controller..")
                .and().areAnnotatedWith(RestController.class)
                .should().haveSimpleNameEndingWith("Controller");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Service-Klassen sollten mit 'Service' enden")
        void servicesShouldEndWithService() {
            ArchRule rule = classes()
                .that().resideInAPackage("..service..")
                .and().areAnnotatedWith(Service.class)
                .should().haveSimpleNameEndingWith("Service");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Repository-Interfaces sollten mit 'Repository' enden")
        void repositoriesShouldEndWithRepository() {
            ArchRule rule = classes()
                .that().resideInAPackage("..repository..")
                .should().haveSimpleNameEndingWith("Repository");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("DTO-Klassen sollten mit 'DTO' enden")
        void dtosShouldEndWithDTO() {
            ArchRule rule = classes()
                .that().resideInAPackage("..dto..")
                .and().areNotMemberClasses() // Innere Klassen (Builder) ausschliessen
                .should().haveSimpleNameEndingWith("DTO");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Config-Klassen sollten mit 'Config' oder 'Interceptor' enden")
        void configsShouldEndWithConfig() {
            ArchRule rule = classes()
                .that().resideInAPackage("..config..")
                .and().areNotInterfaces()
                .and().areNotAnonymousClasses()
                .and().areNotMemberClasses() // Innere Klassen (Adresse, Steller) ausschliessen
                .should().haveSimpleNameEndingWith("Config")
                .orShould().haveSimpleNameEndingWith("Interceptor");

            rule.check(importedClasses);
        }
    }

    @Nested
    @DisplayName("Annotationen")
    class AnnotationTests {

        @Test
        @DisplayName("Controller sollten mit @RestController annotiert sein")
        void controllersShouldBeAnnotatedWithRestController() {
            ArchRule rule = classes()
                .that().resideInAPackage("..controller..")
                .and().haveSimpleNameEndingWith("Controller")
                .should().beAnnotatedWith(RestController.class);

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Services sollten mit @Service annotiert sein")
        void servicesShouldBeAnnotatedWithService() {
            ArchRule rule = classes()
                .that().resideInAPackage("..service..")
                .and().haveSimpleNameEndingWith("Service")
                .should().beAnnotatedWith(Service.class);

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Repositories sollten Repository-Interfaces sein")
        void repositoriesShouldBeInterfaces() {
            ArchRule rule = classes()
                .that().resideInAPackage("..repository..")
                .and().haveSimpleNameEndingWith("Repository")
                .should().beInterfaces();

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Entities sollten mit @Entity annotiert sein")
        void entitiesShouldBeAnnotatedWithEntity() {
            ArchRule rule = classes()
                .that().resideInAPackage("..entity..")
                .and().areNotEnums()
                .and().haveSimpleNameNotEndingWith("Typ")
                .and().haveSimpleNameNotEndingWith("package-info")
                .should().beAnnotatedWith(Entity.class);

            rule.check(importedClasses);
        }
    }

    @Nested
    @DisplayName("Abhängigkeiten")
    class DependencyTests {

        @Test
        @DisplayName("Entities sollten keine anderen Schichten importieren")
        void entitiesShouldNotImportOtherLayers() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("..entity..")
                .should().dependOnClassesThat().resideInAnyPackage(
                    "..controller..",
                    "..service..",
                    "..dto..",
                    "..config.."
                );

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("DTOs sollten keine Business-Schichten importieren")
        void dtosShouldNotImportBusinessLayers() {
            ArchRule rule = noClasses()
                .that().resideInAPackage("..dto..")
                .should().dependOnClassesThat().resideInAnyPackage(
                    "..controller..",
                    "..service..",
                    "..repository..",
                    "..config.."
                );

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Keine zyklischen Abhängigkeiten zwischen Haupt-Schichten")
        void noCyclicDependenciesBetweenMainLayers() {
            // Controller -> Service -> Repository ist strikt unidirektional
            ArchRule noServiceToController = noClasses()
                .that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAPackage("..controller..");

            ArchRule noRepositoryToService = noClasses()
                .that().resideInAPackage("..repository..")
                .should().dependOnClassesThat().resideInAPackage("..service..");

            ArchRule noRepositoryToController = noClasses()
                .that().resideInAPackage("..repository..")
                .should().dependOnClassesThat().resideInAPackage("..controller..");

            noServiceToController.check(importedClasses);
            noRepositoryToService.check(importedClasses);
            noRepositoryToController.check(importedClasses);
        }
    }

    @Nested
    @DisplayName("Spring-spezifische Regeln")
    class SpringRules {

        @Test
        @DisplayName("Repositories sollten von JpaRepository erben")
        void repositoriesShouldExtendJpaRepository() {
            ArchRule rule = classes()
                .that().resideInAPackage("..repository..")
                .and().haveSimpleNameEndingWith("Repository")
                .should().beAssignableTo(JpaRepository.class);

            rule.check(importedClasses);
        }
    }
}
