package com.example.executiongate.security

import com.example.executiongate.service.DatasourceService
import com.example.executiongate.service.dto.Datasource
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration
import org.springframework.security.core.Authentication
import java.io.Serializable


//@Configuration
//@EnableGlobalMethodSecurity(prePostEnabled = true)
//class MethodSecurityConfiguration(
//    val datasourceService: DatasourceService
//) : GlobalMethodSecurityConfiguration() {
//    override fun createExpressionHandler(): MethodSecurityExpressionHandler {
//        val expressionHandler = DefaultMethodSecurityExpressionHandler()
//        expressionHandler.setPermissionEvaluator(CustomPermissionEvaluator(datasourceService))
//        return expressionHandler
//    }
//}



class CustomPermissionEvaluator(
    val datasourceService: DatasourceService
) : PermissionEvaluator {
    override fun hasPermission(authentication: Authentication?, targetDomainObject: Any?, permission: Any?): Boolean {
        if (authentication == null) {
            return false
        }

        return when (targetDomainObject) {
            is Datasource -> true
            else -> false
        }

    }

    override fun hasPermission(authentication: Authentication?, targetId: Serializable?, targetType: String?, permission: Any?): Boolean {
        return true
    }

}

