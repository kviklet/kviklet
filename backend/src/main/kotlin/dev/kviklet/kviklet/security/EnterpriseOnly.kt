// This file is not MIT licensed
package dev.kviklet.kviklet.security

/**
 * Annotation to mark methods or classes that require a valid enterprise license.
 * When applied, the method will only be accessible if a valid enterprise license is present.
 *
 * @param feature Optional description of the enterprise feature for error messages
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class EnterpriseOnly(val feature: String = "Enterprise Feature")
