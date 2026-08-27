package ch.nacht.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.persistence.Entity;

import java.util.Set;

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

    @Nested
    @DisplayName("Security-Invarianten")
    class SecurityRules {

        /**
         * Jeder REST-Endpoint muss autorisiert sein – entweder per klassen-weitem
         * @PreAuthorize oder einzeln je Mapping-Methode. Verhindert vergessene
         * Autorisierung (Broken Access Control, OWASP A01).
         * Ausnahme: PingController (/ping) ist bewusst oeffentlich.
         */
        @Test
        @DisplayName("Jeder Controller-Endpoint ist mit @PreAuthorize abgesichert")
        void everyEndpointMustBeAuthorized() {
            ArchCondition<JavaClass> authorizeEveryEndpoint =
                new ArchCondition<>("@PreAuthorize auf Klassenebene oder auf jeder Mapping-Methode haben") {
                    @Override
                    public void check(JavaClass controller, ConditionEvents events) {
                        // Klassen-weites @PreAuthorize deckt alle Methoden ab
                        if (controller.isAnnotatedWith(PreAuthorize.class)) {
                            return;
                        }
                        for (JavaMethod method : controller.getMethods()) {
                            boolean isEndpoint = method.isMetaAnnotatedWith(RequestMapping.class);
                            if (isEndpoint && !method.isAnnotatedWith(PreAuthorize.class)) {
                                events.add(SimpleConditionEvent.violated(method,
                                    method.getFullName() + " ist ein Endpoint ohne @PreAuthorize"));
                            }
                        }
                    }
                };

            ArchRule rule = classes()
                .that().resideInAPackage("..controller..")
                .and().areAnnotatedWith(RestController.class)
                .and().haveSimpleNameNotContaining("Ping") // PingController: bewusst public (/ping)
                .should(authorizeEveryEndpoint);

            rule.check(importedClasses);
        }

        /**
         * Autorisierung ist permission-basiert: @PreAuthorize auf Controllern verwendet
         * ausschliesslich hasAuthority('<permission>'), keine Fachrollen-Checks
         * (hasRole/hasAnyRole). Siehe Specs/Composite-Roles.md.
         */
        @Test
        @DisplayName("Controller-@PreAuthorize verwenden Permissions (hasAuthority), nicht Fachrollen")
        void preAuthorizeShouldUsePermissionsNotRoles() {
            ArchCondition<JavaClass> useHasAuthorityOnly =
                new ArchCondition<>("nur hasAuthority(...) statt hasRole/hasAnyRole in @PreAuthorize verwenden") {
                    @Override
                    public void check(JavaClass controller, ConditionEvents events) {
                        if (controller.isAnnotatedWith(PreAuthorize.class)) {
                            reportIfRoleBased(controller.getAnnotationOfType(PreAuthorize.class).value(),
                                controller, controller.getSimpleName(), events);
                        }
                        for (JavaMethod method : controller.getMethods()) {
                            if (method.isAnnotatedWith(PreAuthorize.class)) {
                                reportIfRoleBased(method.getAnnotationOfType(PreAuthorize.class).value(),
                                    controller, method.getFullName(), events);
                            }
                        }
                    }

                    private void reportIfRoleBased(String expression, JavaClass owner, String location,
                                                   ConditionEvents events) {
                        if (expression.contains("hasRole(") || expression.contains("hasAnyRole(")) {
                            events.add(SimpleConditionEvent.violated(owner,
                                location + " verwendet Fachrollen-Check statt Permission: " + expression));
                        }
                    }
                };

            ArchRule rule = classes()
                .that().resideInAPackage("..controller..")
                .and().areAnnotatedWith(RestController.class)
                .should(useHasAuthorityOnly);

            rule.check(importedClasses);
        }

        /**
         * Jede mandantenfaehige Entity muss ein org_id-Feld tragen (Mandanten-Trennung).
         * Translation = global (keine Mandanten-Daten), Organisation = der Mandant selbst.
         *
         * <p><b>Deny by default:</b> Ausgenommen sind nur die namentlich aufgefuehrten Entities.
         * Eine neu hinzukommende Entity ist automatisch erfasst — und das ist der Zweck der Regel.
         */
        @Test
        @DisplayName("Jede Entity hat ein org_id-Feld (Mandanten-Trennung)")
        void everyEntityMustHaveOrgId() {
            ArchCondition<JavaClass> haveOrgIdField =
                new ArchCondition<>("ein Feld 'orgId' fuer die Mandanten-Trennung haben") {
                    @Override
                    public void check(JavaClass entity, ConditionEvents events) {
                        boolean hasOrgId = entity.getAllFields().stream()
                            .anyMatch(field -> field.getName().equals("orgId"));
                        if (!hasOrgId) {
                            events.add(SimpleConditionEvent.violated(entity,
                                entity.getSimpleName() + " hat kein org_id-Feld (Mandanten-Trennung)"));
                        }
                    }
                };

            // Entities ohne Mandantenbezug - namentlich, damit die Ausnahme in der Regel steht
            // und nicht aus einem Namensmuster entsteht.
            Set<String> ohneMandantenbezug = Set.of(
                // Marktdaten: Die Einspeisepreise der BKW sind fuer alle Mandanten identisch, eine
                // Kopie je Mandant waere redundant, und der taegliche Abruf-Job hat keinen
                // Mandantenkontext (Specs/Preiszeitreihe.md, FR-2). Geschuetzt ist der Zugriff
                // ueber die Permission tarife:manage am Controller.
                "Preiszeitreihe");

            ArchRule rule = classes()
                .that().resideInAPackage("..entity..")
                .and().areAnnotatedWith(Entity.class)
                .and().haveSimpleNameNotContaining("Translation")
                .and().haveSimpleNameNotContaining("Organisation")
                .and(new DescribedPredicate<JavaClass>("tragen Mandantendaten") {
                    @Override
                    public boolean test(JavaClass entity) {
                        return !ohneMandantenbezug.contains(entity.getSimpleName());
                    }
                })
                .should(haveOrgIdField);

            rule.check(importedClasses);
        }

        /**
         * Die Nebenkostenabrechnung haengt an einem Feature-Flag. Geprueft wird er per explizitem
         * Aufruf am Anfang jeder Service-Methode (Specs/Nebenkosten/Abrechnung.md, FR-6) - kein
         * Aspect, keine eigene Annotation.
         *
         * <p>Der Preis dieses Entscheids ist, dass eine neue Methode den Aufruf vergessen kann und
         * dann ungeschuetzt ist: Das Menue bliebe verborgen, die API aber ueber jeden HTTP-Client
         * erreichbar. Diese Regel faengt genau das ab.
         *
         * <p><b>Geltungsbereich: alle Services mit dem Praefix {@code Nk}</b> — nicht nur
         * {@code NkAbrechnung*}. Bis zur Rechnungserstellung war der Bereich auf jenes Praefix
         * eingeschraenkt; {@code NkRechnungService} waere stillschweigend uebersprungen worden
         * (Specs/Nebenkosten/RechnungenGenerieren.md, FR-9). Deny by default, wie bei der
         * {@code findById}-Regel daneben.
         *
         * <p><b>Ausgenommen</b> sind namentlich die Services, die <b>keine Mandantendaten laden</b>
         * und deshalb nichts zu schuetzen haben: Sie rechnen bzw. formen nur, was ihnen uebergeben
         * wird. Ein {@code pruefeFeatureFlag()} waere dort Zierde — und die Ausnahme steht hier
         * sichtbar, statt sich aus einem Praefix zu ergeben.
         */
        @Test
        @DisplayName("Jede oeffentliche Methode der NK-Services prueft den Feature-Flag")
        void nebenkostenServicesMustCheckFeatureFlag() {
            ArchCondition<JavaClass> checkFeatureFlag =
                new ArchCondition<>("in jeder oeffentlichen Methode pruefeFeatureFlag() aufrufen") {
                    @Override
                    public void check(JavaClass service, ConditionEvents events) {
                        for (JavaMethod method : service.getMethods()) {
                            if (!method.getModifiers().contains(JavaModifier.PUBLIC)) {
                                continue;
                            }
                            boolean prueft = method.getMethodCallsFromSelf().stream()
                                .anyMatch(call -> call.getTarget().getName().equals("pruefeFeatureFlag"));
                            if (!prueft) {
                                events.add(SimpleConditionEvent.violated(method,
                                    method.getFullName() + " prueft den Feature-Flag nicht"));
                            }
                        }
                    }
                };

            // Services ohne Mandantenzugriff: reine Rechen- bzw. Formatierungslogik.
            Set<String> ohneMandantenzugriff = Set.of(
                "NkBerechnungService",    // reine Funktionen auf Eingabedaten, keine Persistenz
                "NkRechnungPdfService"    // fuellt nur das Template mit uebergebenen Daten
            );

            ArchRule rule = classes()
                .that().resideInAPackage("..service..")
                .and().haveSimpleNameStartingWith("Nk")
                .and(new DescribedPredicate<JavaClass>("laden Mandantendaten") {
                    @Override
                    public boolean test(JavaClass service) {
                        return !ohneMandantenzugriff.contains(service.getSimpleName());
                    }
                })
                .should(checkFeatureFlag);

            rule.check(importedClasses);
        }

        /**
         * Dieselbe Regel fuer die Preiszeitreihe (Specs/Preiszeitreihe.md, FR-6): Jede oeffentliche
         * Methode prueft das Feature-Flag {@code PREISZEITREIHE}.
         *
         * <p>Ohne sie waere der Flag reine Kosmetik — das Panel auf der Tarifseite bliebe verborgen,
         * die API aber ueber jeden HTTP-Client erreichbar. Und ohne <b>Regel</b> kann eine spaeter
         * ergaenzte Methode die Pruefung stillschweigend auslassen.
         *
         * <p><b>Ausgenommen</b> sind namentlich die Klassen, die <b>keinen Mandantenkontext haben</b>:
         * Sie laufen auch im geplanten Job, wo kein Benutzer angemeldet ist und es folglich keine
         * {@code org_id} gibt. Sie exponieren nichts nach aussen — der Controller spricht
         * ausschliesslich mit {@code PreiszeitreiheService}.
         */
        @Test
        @DisplayName("Jede oeffentliche Methode der Preiszeitreihe-Services prueft den Feature-Flag")
        void preiszeitreiheServicesMustCheckFeatureFlag() {
            ArchCondition<JavaClass> checkFeatureFlag =
                new ArchCondition<>("in jeder oeffentlichen Methode pruefeFeatureFlag() aufrufen") {
                    @Override
                    public void check(JavaClass service, ConditionEvents events) {
                        for (JavaMethod method : service.getMethods()) {
                            if (!method.getModifiers().contains(JavaModifier.PUBLIC)) {
                                continue;
                            }
                            boolean prueft = method.getMethodCallsFromSelf().stream()
                                .anyMatch(call -> call.getTarget().getName().equals("pruefeFeatureFlag"));
                            if (!prueft) {
                                events.add(SimpleConditionEvent.violated(method,
                                    method.getFullName() + " prueft den Feature-Flag nicht"));
                            }
                        }
                    }
                };

            // Ohne Mandantenkontext: laufen auch im geplanten Job.
            Set<String> ohneMandantenkontext = Set.of(
                "PreiszeitreiheAbrufService",   // Beschaffung; prueft das Flag ueber die Mandantenmenge
                "PreiszeitreiheDownloadJob");   // entscheidet ueber die Mandantenmenge

            ArchRule rule = classes()
                .that().resideInAPackage("..service..")
                .and().haveSimpleNameStartingWith("Preiszeitreihe")
                .and(new DescribedPredicate<JavaClass>("haben einen Mandantenkontext") {
                    @Override
                    public boolean test(JavaClass service) {
                        return !ohneMandantenkontext.contains(service.getSimpleName());
                    }
                })
                .should(checkFeatureFlag);

            rule.check(importedClasses);
        }

        /**
         * Repositories mandantenweiter Entities werden nie ueber {@code findById} gelesen.
         *
         * <p><b>Warum:</b> Hibernate wendet {@code @Filter} auf Abfragen an, <b>nicht</b> auf das
         * Laden ueber den Primaerschluessel. {@code findById} liefert deshalb auch Datensaetze
         * fremder Mandanten, obwohl der Filter eingeschaltet ist — empirisch belegt in
         * {@code NkAbrechnungRepositoryIT.findByIdUmgehtDenMandantenfilter_bekannteLuecke}. Eine
         * von aussen kommende ID darf daher nur ueber eine abgeleitete Abfrage geladen werden
         * ({@code findFirstById}), die der Filter erfasst. Broken Access Control ueber
         * Mandantengrenzen, OWASP A01.
         *
         * <p><b>Deny by default:</b> Ausgenommen sind nur die drei Repositories, deren Entity
         * bewusst <b>keinen</b> {@code @Filter} traegt — ein neu hinzukommendes Repository ist
         * automatisch erfasst.
         */
        @Test
        @DisplayName("Services lesen mandantenweite Entities nie ueber findById")
        void servicesMustNotUseFindByIdOnFilteredRepositories() {
            // Entities ohne @Filter: global bzw. nicht mandantenspezifisch.
            Set<String> ungefiltert = Set.of(
                "OrganisationRepository", "TranslationRepository", "MetrikRepository");

            // Der Aggregations-Job laeuft bewusst mandantenuebergreifend ueber alle
            // unverarbeiteten Rohdaten und schaltet den Filter gar nicht ein; die einheitId
            // stammt dort aus dem Repository und nicht von einem Aufrufer.
            Set<String> ausnahmen = Set.of("ZaehlerAggregationService");

            ArchCondition<JavaClass> keinFindById =
                new ArchCondition<>("mandantenweite Entities nicht ueber findById laden") {
                    @Override
                    public void check(JavaClass service, ConditionEvents events) {
                        if (ausnahmen.contains(service.getSimpleName())) {
                            return;
                        }
                        for (JavaMethod method : service.getMethods()) {
                            method.getMethodCallsFromSelf().stream()
                                .filter(call -> call.getTarget().getName().equals("findById"))
                                .filter(call -> call.getTargetOwner().getSimpleName()
                                    .endsWith("Repository"))
                                .filter(call -> !ungefiltert.contains(
                                    call.getTargetOwner().getSimpleName()))
                                .forEach(call -> events.add(SimpleConditionEvent.violated(method,
                                    method.getFullName() + " liest "
                                        + call.getTargetOwner().getSimpleName()
                                        + " ueber findById — der Mandantenfilter greift dort nicht."
                                        + " Stattdessen findFirstById verwenden.")));
                        }
                    }
                };

            ArchRule rule = classes()
                .that().resideInAPackage("..service..")
                .should(keinFindById);

            rule.check(importedClasses);
        }
    }
}
