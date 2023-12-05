package dev.kviklet.kviklet

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import jakarta.persistence.Entity

@AnalyzeClasses(packages = ["dev.kviklet.kviklet"], importOptions = [DoNotIncludeTests::class])
class ArchitectureTest {

    @ArchTest
    val entityRule = classes().that().areAnnotatedWith(Entity::class.java)
        .should().onlyBeAccessed().byAnyPackage("..db..")
}
