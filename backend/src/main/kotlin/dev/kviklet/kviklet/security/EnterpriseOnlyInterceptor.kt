// This file is not MIT licensed
package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.service.LicenseService
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation

/**
 * Interceptor that checks for a valid enterprise license before allowing method execution.
 */
class EnterpriseOnlyInterceptor(private val licenseService: LicenseService) : MethodInterceptor {

    override fun invoke(invocation: MethodInvocation): Any? {
        // Check for annotation on method first, then on class
        val annotation = invocation.method.getAnnotation(EnterpriseOnly::class.java)
            ?: invocation.method.declaringClass.getAnnotation(EnterpriseOnly::class.java)

        if (annotation != null) {
            val license = licenseService.getActiveLicense()
            if (license == null || !license.isValid()) {
                throw EnterpriseFeatureException(
                    "Enterprise license required for: ${annotation.feature}. " +
                        "Please install a valid license file to use this feature.",
                )
            }
        }

        return invocation.proceed()
    }
}
