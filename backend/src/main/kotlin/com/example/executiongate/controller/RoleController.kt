package com.example.executiongate.controller

import com.example.executiongate.db.RoleAdapter
import com.example.executiongate.service.dto.Role
import com.example.executiongate.service.dto.Policy
import com.example.executiongate.service.dto.PolicyEffect
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


data class CreateRoleRequest(
    val name: String,
    val description: String
)

data class PolicyResponse(
    val id: String,
    val action: String,
    val effect: PolicyEffect,
    val resource: String,
) {
    companion object {
        fun fromDto(policy: Policy) = PolicyResponse(
            id=policy.id,
            action=policy.action,
            effect=policy.effect,
            resource=policy.resource,
        )
    }
}

data class RoleResponse(
    val id: String,
    val name: String,
    val description: String,
    val permissions: List<PolicyResponse>
) {
    companion object {
        fun fromDto(dto: Role): RoleResponse {
            return RoleResponse(
                id = dto.id,
                name = dto.name,
                description = dto.description,
                permissions = dto.policies.map { PolicyResponse.fromDto(it) }
            )
        }
    }
}

data class RolesResponse(
    val roles: List<RoleResponse>
) {
    companion object {
        fun fromRoles(roles: List<Role>): RolesResponse {
            return RolesResponse(
                roles = roles.map { RoleResponse.fromDto(it) }
            )
        }
    }
}


fun permissionsToPermissionString(policies: Set<Policy>): String {
    return policies.map { "${it.effect}:${it.action} on ${it.resource}" }.joinToString { ";" }
}

@RestController()
@Validated
@RequestMapping("/roles")
class RoleController(private val roleAdapter: RoleAdapter) {

    @GetMapping("/:id")
    fun getRole(id: String): RoleResponse {
        val role = roleAdapter.findById(id)
        return RoleResponse.fromDto(role)
    }

    @GetMapping("/")
    fun getAllRoles(): RolesResponse {
        val roles = roleAdapter.findAll()
        return RolesResponse.fromRoles(roles)
    }

    @PostMapping("/")
    fun createRole(@Valid @RequestBody createRoleRequest: CreateRoleRequest): RoleResponse {
        val savedRole = roleAdapter.create(
            Role(
                name = createRoleRequest.name,
                description = createRoleRequest.description
            )
        )
        return RoleResponse.fromDto(savedRole)
    }

    @DeleteMapping("/:id")
    fun deleteRole(id: String) {
        roleAdapter.delete(id)
    }

}