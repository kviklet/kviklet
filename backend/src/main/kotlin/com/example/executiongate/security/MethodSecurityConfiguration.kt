package com.example.executiongate.security

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
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.function.Supplier

interface SecuredDomainObject {
    fun getId(): String
}

@Target(AnnotationTarget.FUNCTION)
@Retention
annotation class Policy(val policy: String)

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
class MethodSecurityConfig {

    @Bean
    @Role(ROLE_INFRASTRUCTURE)
    fun authorizationManagerBeforeMethodInterception(manager: MyAuthorizationManager): Advisor {
        return AuthorizationManagerInterceptor(
            AnnotationMatchingPointcut(null, Policy::class.java, true),
            manager,
        )
    }
}

@Component
class MyAuthorizationManager {
    fun check(
        authentication: Supplier<Authentication>,
        invocation: MethodInvocation,
        returnObject: SecuredDomainObject? = null,
    ): AuthorizationDecision {
        val policyAnnotation: Policy = AnnotationUtils.findAnnotation(invocation.method, Policy::class.java)!!

        val votes = authentication.get().authorities.filterIsInstance<PolicyGrantedAuthority>().map {
            it.vote(action = policyAnnotation.policy, domainObject = returnObject)
        }

        return if (votes.any { it == VoteResult.ALLOW } && votes.none { it == VoteResult.DENY }) {
            AuthorizationDecision(true)
        } else {
            AuthorizationDecision(false)
        }
    }
}

class AuthorizationManagerInterceptor(
    private val pointcut: Pointcut,
    private val authorizationManager: MyAuthorizationManager,
) : Ordered, MethodInterceptor, PointcutAdvisor, AopInfrastructureBean {

    private val authentication: Supplier<Authentication> = Supplier {
        SecurityContextHolder.getContextHolderStrategy().context.authentication
            ?: throw AuthenticationCredentialsNotFoundException(
                "An Authentication object was not found in the SecurityContext",
            )
    }

    override fun getOrder(): Int = 500

    override fun getAdvice(): Advice = this

    override fun getPointcut(): Pointcut = this.pointcut

    override fun invoke(invocation: MethodInvocation): Any? {
        attemptPreAuthorization(invocation)
        val returnedObject = invocation.proceed()
        return attemptPostAuthorization(invocation, returnedObject)
    }

    private fun attemptPostAuthorization(invocation: MethodInvocation, returnedObject: Any?): Any? {
        return if (returnedObject is SecuredDomainObject) {
            if (!authorizationManager.check(authentication, invocation, returnedObject).isGranted) {
                throw AccessDeniedException("Access Denied")
            }
            returnedObject
        } else if (returnedObject is Collection<*>) {
            filterCollection(invocation, returnedObject as MutableCollection<*>)
        } else {
            throw IllegalStateException("Expected SecuredDomainObject, got $returnedObject.")
        }
    }

    private fun <T> filterCollection(
        invocation: MethodInvocation,
        filterTarget: MutableCollection<T>,
    ): MutableCollection<T> {
        val retain: MutableList<T> = ArrayList(filterTarget.size)
        for (filterObject in filterTarget) {
            if (filterObject is SecuredDomainObject) {
                if (authorizationManager.check(authentication, invocation, filterObject).isGranted) {
                    retain.add(filterObject)
                }
            } else {
                throw IllegalStateException("Expected SecuredDomainObject, got $filterObject.")
            }
        }
        filterTarget.clear()
        filterTarget.addAll(retain)
        return retain
    }

    private fun attemptPreAuthorization(mi: MethodInvocation) {
        //        this.eventPublisher.publishAuthorizationEvent<MethodInvocation>(this.authentication, mi, decision)
        if (!authorizationManager.check(authentication, mi).isGranted) {
            throw AccessDeniedException("Access Denied")
        }
    }
}
