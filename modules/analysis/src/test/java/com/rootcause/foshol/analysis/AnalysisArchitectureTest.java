package com.rootcause.foshol.analysis;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "com.rootcause.foshol.analysis", importOptions = ImportOption.DoNotIncludeTests.class)
class AnalysisArchitectureTest {

    @ArchTest
    static final ArchRule noStructuredTaskScope = noClasses()
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("java.util.concurrent.StructuredTaskScope")
            .because("ANALYSIS-FR-021 forbids preview StructuredTaskScope");

    @ArchTest
    static final ArchRule domainHasNoSpring = noClasses()
            .that()
            .resideInAPackage("com.rootcause.foshol.analysis.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "com.fasterxml.jackson..")
            .because("COMMON-ARCH-004: no Spring, JPA or Jackson in domain");

    @ArchTest
    static final ArchRule webDoesNotImportDomain = noClasses()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..domain..")
            .because("controllers must not touch the domain model directly")
            .allowEmptyShould(true);
}
