package com.rootcause.foshol;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "com.rootcause.foshol", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

    private static final String[] MODULES = {
        "identity", "intake", "analysis", "knowledge", "review", "notification"
    };

    @ArchTest
    static final ArchRule onlyApiPackageOfAnotherModuleMayBeImported = noClasses()
            .should(dependOnAnotherModulesInternals())
            .because("only the api package of another module may be imported");

    @ArchTest
    static final ArchRule layeredArchitectureRule = layeredArchitecture()
            .consideringOnlyDependenciesInAnyPackage("..foshol..")
            .optionalLayer("web")
            .definedBy("..web..")
            .optionalLayer("application")
            .definedBy("..application..")
            .optionalLayer("domain")
            .definedBy("..domain..")
            .optionalLayer("infrastructure")
            .definedBy("..infrastructure..")
            .whereLayer("web")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("application")
            .mayOnlyBeAccessedByLayers("web", "infrastructure")
            .whereLayer("domain")
            .mayOnlyBeAccessedByLayers("application", "infrastructure", "web");

    @ArchTest
    static final ArchRule domainHasNoSpringJpaJackson = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "com.fasterxml.jackson..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule commandsDoNotDependOnQueries = noClasses()
            .that()
            .resideInAPackage("..application.command..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..application.query..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule queriesDoNotDependOnCommands = noClasses()
            .that()
            .resideInAPackage("..application.query..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..application.command..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule noCrossModuleJpaAssociations = noFields()
            .that()
            .areAnnotatedWith(ManyToOne.class)
            .or()
            .areAnnotatedWith(OneToMany.class)
            .or()
            .areAnnotatedWith(OneToOne.class)
            .or()
            .areAnnotatedWith(ManyToMany.class)
            .should(haveRawTypeInAnotherFosholModule())
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule queriesDoNotLoadAggregates = noClasses()
            .that()
            .resideInAPackage("..application.query..")
            .should()
            .dependOnClassesThat()
            .areAnnotatedWith(Entity.class)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule dtoCommandQueryEventAreRecords = classes()
            .that(namedCommandQueryViewOrEvent())
            .and()
            .resideInAnyPackage("..api..", "..application.command..", "..application.query..", "..web..")
            .should()
            .beRecords();

    @ArchTest
    static final ArchRule controllersDoNotTouchDomain = noClasses()
            .that()
            .resideInAPackage("..web..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..domain..")
            .because("controllers must not touch the domain model directly")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule restControllersDeclareRoleChecks = classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .and()
            .doNotHaveSimpleName("AuthController")
            .should(beSecuredByPreAuthorize())
            .because("every product API must declare RBAC; AuthController is the public login surface");

    @ArchTest
    static final ArchRule noGetPropertyOutsideCommon = noClasses()
            .that()
            .resideOutsideOfPackage("..foshol.common..")
            .should()
            .callMethod(Environment.class, "getProperty", String.class);

    private static ArchCondition<JavaClass> beSecuredByPreAuthorize() {
        return new ArchCondition<JavaClass>("be annotated with @PreAuthorize or annotate every mapped method") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (item.isAnnotatedWith(PreAuthorize.class)) {
                    return;
                }
                for (JavaMethod method : item.getMethods()) {
                    if (!method.getModifiers().contains(JavaModifier.PUBLIC)
                            || !method.isMetaAnnotatedWith(RequestMapping.class)) {
                        continue;
                    }
                    if (!method.isAnnotatedWith(PreAuthorize.class)) {
                        events.add(SimpleConditionEvent.violated(
                                method,
                                method.getFullName() + " is a mapped controller method without @PreAuthorize"));
                    }
                }
            }
        };
    }

    private static DescribedPredicate<JavaClass> namedCommandQueryViewOrEvent() {
        return new DescribedPredicate<JavaClass>("named Command, Query, Request, Response, View or Event") {
            @Override
            public boolean test(JavaClass javaClass) {
                String name = javaClass.getSimpleName();
                return name.endsWith("Command")
                        || name.endsWith("Query")
                        || name.endsWith("Request")
                        || name.endsWith("Response")
                        || name.endsWith("View")
                        || name.endsWith("Event");
            }
        };
    }

    private static ArchCondition<JavaClass> dependOnAnotherModulesInternals() {
        return new ArchCondition<JavaClass>("depend on another module's internals") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String own = moduleOf(item);
                if (own == null) {
                    return;
                }
                item.getDirectDependenciesFromSelf().forEach(dependency -> {
                    JavaClass target = dependency.getTargetClass();
                    String other = moduleOf(target);
                    if (other == null || own.equals(other)) {
                        return;
                    }
                    String pkg = target.getPackageName();
                    boolean internal = pkg.contains("." + other + ".domain")
                            || pkg.contains("." + other + ".application")
                            || pkg.contains("." + other + ".infrastructure")
                            || pkg.contains("." + other + ".web");
                    if (internal) {
                        events.add(SimpleConditionEvent.violated(
                                item, item.getName() + " depends on internal type " + target.getName()));
                    }
                });
            }
        };
    }

    private static ArchCondition<JavaField> haveRawTypeInAnotherFosholModule() {
        return new ArchCondition<JavaField>("have a raw type in another foshol module") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                String own = moduleOf(field.getOwner());
                String other = moduleOf(field.getRawType());
                if (own != null && other != null && !own.equals(other)) {
                    events.add(SimpleConditionEvent.violated(
                            field, field.getFullName() + " associates to " + field.getRawType().getName()));
                }
            }
        };
    }

    private static String moduleOf(JavaClass type) {
        String pkg = type.getPackageName();
        for (String module : MODULES) {
            if (pkg.equals("com.rootcause.foshol." + module)
                    || pkg.startsWith("com.rootcause.foshol." + module + ".")) {
                return module;
            }
        }
        return null;
    }
}
