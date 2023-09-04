package com.example.executiongate

import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import jakarta.persistence.Entity

@AnalyzeClasses(packages = ["com.example.executiongate"])
class ArchitectureTest {

    @ArchTest
    val entityRule = classes().that().areAnnotatedWith(Entity::class.java)
        .should().onlyBeAccessed().byAnyPackage("..db..")
}
