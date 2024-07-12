package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.service.IdResolver
import org.aopalliance.aop.Advice
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation
import org.springframework.aop.Advisor
import org.springframework.aop.Pointcut
import org.springframework.aop.PointcutAdvisor
import org.springframework.aop.framework.AopInfrastructureBean
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut
import org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Role
import org.springframework.core.Ordered
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Component
import java.util.function.Supplier

enum class Resource(val resourceName: String) {
    DATASOURCE_CONNECTION("datasource_connection"),
    EXECUTION_REQUEST("execution_request"),
    ROLE("role"),
    USER("user"),
    CONFIGURATION("configuration"),
}

enum class Permission(
    val resource: Resource,
    // if action is null, only the parent requiredPermission is checked
    val action: String?,
    val requiredPermission: Permission?,
) {
    CONFIGURATION_GET(Resource.CONFIGURATION, "get", null),
    CONFIGURATION_EDIT(Resource.CONFIGURATION, "edit", CONFIGURATION_GET),

    DATASOURCE_CONNECTION_GET(Resource.DATASOURCE_CONNECTION, "get", null),
    DATASOURCE_CONNECTION_EDIT(Resource.DATASOURCE_CONNECTION, "edit", DATASOURCE_CONNECTION_GET),
    DATASOURCE_CONNECTION_CREATE(Resource.DATASOURCE_CONNECTION, "create", DATASOURCE_CONNECTION_GET),

    EXECUTION_REQUEST_GET(Resource.EXECUTION_REQUEST, "get", DATASOURCE_CONNECTION_GET),
    EXECUTION_REQUEST_EDIT(Resource.EXECUTION_REQUEST, "edit", EXECUTION_REQUEST_GET),
    EXECUTION_REQUEST_REVIEW(Resource.EXECUTION_REQUEST, "review", EXECUTION_REQUEST_GET),
    EXECUTION_REQUEST_EXECUTE(Resource.EXECUTION_REQUEST, "execute", EXECUTION_REQUEST_GET),

    ROLE_GET(Resource.ROLE, "get", null),
    ROLE_EDIT(Resource.ROLE, "edit", ROLE_GET),

    USER_GET(Resource.USER, "get", null),
    USER_EDIT(Resource.USER, "edit", USER_GET),
    USER_CREATE(Resource.USER, "create", USER_GET),
    USER_EDIT_ROLES(Resource.USER, "edit_roles", USER_GET),
    ;

    fun getPermissionString(): String = "${this.resource.resourceName}:${this.action}"
}

interface SecuredDomainId {
    override fun toString(): String
}

interface SecuredDomainObject {
    fun getSecuredObjectId(): String?
    fun getDomainObjectType(): Resource
    fun getRelated(resource: Resource): SecuredDomainObject?
    fun auth(permission: Permission, userDetails: UserDetailsWithId, policies: List<PolicyGrantedAuthority>): Boolean =
        true
}

@Target(AnnotationTarget.FUNCTION)
@Retention
annotation class Policy(val permission: Permission, val checkIsPresentOnly: Boolean = false)

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
class MethodSecurityConfig(private val idResolver: IdResolver) {

    @Bean
    @Role(ROLE_INFRASTRUCTURE)
    fun authorizationManagerBeforeMethodInterception(manager: MyAuthorizationManager): Advisor =
        AuthorizationManagerInterceptor(
            AnnotationMatchingPointcut(null, Policy::class.java, true),
            manager,
            idResolver,
        )
}

@Component
class MyAuthorizationManager(val userAdapter: UserAdapter) {
    fun check(
        authentication: Supplier<Authentication?>,
        invocation: MethodInvocation,
        returnObject: Any? = null,
    ): AuthorizationDecision {
        val policyAnnotation: Policy = AnnotationUtils.findAnnotation(invocation.method, Policy::class.java)!!
        // If there is no SecurityContext the method has been called from within the application
        // or from tests without a request, therefore we allow it
        val auth = authentication.get() ?: return AuthorizationDecision(true)

        var permissionToCheck: Permission = policyAnnotation.permission

        val userDetailsWithId = when (auth.principal) {
            is UserDetailsWithId -> auth.principal as UserDetailsWithId
            is OidcUser -> (auth.principal as CustomOidcUser).getUserDetails()
            is String -> return AuthorizationDecision(false) // anonymous user
            else -> throw RuntimeException("Unknown principal type: ${auth.principal.javaClass}")
        }

        val user = userAdapter.findById(userDetailsWithId.id)

        val policies = user.roles.map { role -> role.policies.map { PolicyGrantedAuthority(it) } }.flatten()

        if (policyAnnotation.checkIsPresentOnly) {
            if (policies.vote(permissionToCheck).isAllowed()) {
                return AuthorizationDecision(true)
            }
        }

        if ((returnObject !is SecuredDomainObject) && (returnObject != null)) {
            throw Exception("Expected SecuredDomainObject, got $returnObject.")
        }

        var securedObject: SecuredDomainObject? = returnObject as SecuredDomainObject?

        do {
            if (!policies.vote(permissionToCheck, securedObject).isAllowed()) {
                return AuthorizationDecision(false)
            }
            if (returnObject?.auth(permissionToCheck, userDetailsWithId, policies) == false) {
                return AuthorizationDecision(false)
            }
        } while ((permissionToCheck.requiredPermission != null).also {
                if (it) {
                    permissionToCheck = permissionToCheck.requiredPermission!!
                    securedObject = securedObject?.getRelated(permissionToCheck.resource)
                }
            }
        )
        return AuthorizationDecision(true)
    }
}

class AuthorizationManagerInterceptor(
    private val pointcut: Pointcut,
    private val authorizationManager: MyAuthorizationManager,
    private val idResolver: IdResolver,
) : Ordered,
    MethodInterceptor,
    PointcutAdvisor,
    AopInfrastructureBean {

    private val authentication: Supplier<Authentication?> = Supplier {
        SecurityContextHolder.getContextHolderStrategy().context.authentication
    }

    override fun getOrder(): Int = 500

    override fun getAdvice(): Advice = this

    override fun getPointcut(): Pointcut = this.pointcut

    override fun invoke(invocation: MethodInvocation): Any? {
        attemptPreAuthorization(invocation)
        val returnedObject = invocation.proceed()
        return attemptPostAuthorization(invocation, returnedObject)
    }

    private fun attemptPostAuthorization(invocation: MethodInvocation, returnedObject: Any?): Any? =
        when (returnedObject) {
            is Collection<*> -> {
                filterCollection(invocation, returnedObject as MutableCollection<*>)
            }

            null -> {
                null
            }

            else -> {
                if (!authorizationManager.check(authentication, invocation, returnedObject).isGranted) {
                    throw AccessDeniedException("Access Denied")
                }
                returnedObject
            }
        }

    private fun <T> filterCollection(
        invocation: MethodInvocation,
        filterTarget: MutableCollection<T>,
    ): MutableCollection<T> {
        val retain: MutableList<T> = ArrayList(filterTarget.size)
        for (filterObject in filterTarget) {
            if (authorizationManager.check(authentication, invocation, filterObject).isGranted) {
                retain.add(filterObject)
            }
        }
        filterTarget.clear()
        filterTarget.addAll(retain)
        return retain
    }

    private fun attemptPreAuthorization(mi: MethodInvocation) {
        val domainIds: List<SecuredDomainId> = mi.arguments.filterIsInstance<SecuredDomainId>()

        if (domainIds.isEmpty()) {
            if (!authorizationManager.check(authentication, mi).isGranted) {
                throw AccessDeniedException("Access Denied")
            }
        } else if (domainIds.size == 1) {
            if (!authorizationManager.check(authentication, mi, idResolver.resolve(domainIds[0])).isGranted) {
                throw AccessDeniedException("Access Denied")
            }
        } else {
            throw IllegalStateException("Only one SecuredDomainId is allowed per method.")
        }
    }
}
