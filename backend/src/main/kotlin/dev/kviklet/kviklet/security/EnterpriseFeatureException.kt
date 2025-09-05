// This file is not MIT licensed
package dev.kviklet.kviklet.security

/**
 * Exception thrown when an enterprise-only feature is accessed without a valid license.
 */
class EnterpriseFeatureException(message: String = "This feature requires a valid enterprise license") :
    RuntimeException(message)
