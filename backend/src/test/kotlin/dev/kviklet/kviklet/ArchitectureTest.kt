package dev.kviklet.kviklet

import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import dev.kviklet.kviklet.security.Policy
import jakarta.persistence.Entity
import org.springframework.scheduling.annotation.Async

@AnalyzeClasses(packages = ["dev.kviklet.kviklet"], importOptions = [DoNotIncludeTests::class])
class ArchitectureTest {

    @ArchTest
    val entityRule = classes().that().areAnnotatedWith(Entity::class.java)
        .should().onlyBeAccessed().byAnyPackage("..db..")

    @ArchTest
    val policyAnnotationRule = methods()
        .that().arePublic()
        .and().areDeclaredInClassesThat().resideInAPackage("..service..")
        .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Service")
        .and().doNotHaveModifier(JavaModifier.SYNTHETIC)
        .and().areNotAnnotatedWith(Async::class.java)
        .should().beAnnotatedWith(Policy::class.java)
}
