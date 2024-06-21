package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.service.RoleService
import dev.kviklet.kviklet.service.dto.Policy
import dev.kviklet.kviklet.service.dto.PolicyEffect
import dev.kviklet.kviklet.service.dto.Role
import dev.kviklet.kviklet.service.dto.RoleId
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class CreateRoleRequest(val name: String, val description: String, val policies: Set<PolicyPayload>?)

data class UpdateRoleRequest(
    val id: String,
    val name: String?,
    val description: String?,
    val policies: Set<PolicyPayload>?,
)

data class PolicyPayload(val id: String?, val action: String, val effect: PolicyEffect, val resource: String)

data class PolicyResponse(val id: String, val action: String, val effect: PolicyEffect, val resource: String) {
    companion object {
        fun fromDto(policy: Policy) = PolicyResponse(
            id = policy.id!!,
            action = policy.action,
            effect = policy.effect,
            resource = policy.resource,
        )
    }
}

data class RoleResponse(
    val id: String,
    val name: String,
    val description: String,
    val policies: List<PolicyResponse>,
    val isDefault: Boolean = false,
) {
    companion object {
        fun fromDto(dto: Role): RoleResponse = RoleResponse(
            id = dto.getId()!!,
            name = dto.name,
            description = dto.description,
            policies = dto.policies.map { PolicyResponse.fromDto(it) },
            isDefault = dto.isDefault,
        )
    }
}

data class RolesResponse(val roles: List<RoleResponse>) {
    companion object {
        fun fromRoles(roles: List<Role>): RolesResponse = RolesResponse(
            roles = roles.map { RoleResponse.fromDto(it) },
        )
    }
}

@RestController()
@Validated
@RequestMapping("/roles")
class RoleController(private val roleService: RoleService) {

    @GetMapping("/{id}")
    fun getRole(@PathVariable id: String): RoleResponse {
        val role = roleService.getRole(RoleId(id))
        return RoleResponse.fromDto(role)
    }

    @GetMapping("/")
    fun getAllRoles(): RolesResponse {
        val roles = roleService.getAllRoles()
        return RolesResponse.fromRoles(roles)
    }

    @PostMapping("/")
    fun createRole(
        @Valid @RequestBody
        createRoleRequest: CreateRoleRequest,
    ): RoleResponse {
        val savedRole = roleService.createRole(
            name = createRoleRequest.name,
            description = createRoleRequest.description,
            policies = createRoleRequest.policies?.map {
                Policy(
                    id = it.id,
                    action = it.action,
                    effect = it.effect,
                    resource = it.resource,
                )
            }?.toSet(),
        )
        return RoleResponse.fromDto(savedRole)
    }

    @PatchMapping("/{id}")
    fun updateRole(
        @PathVariable id: String,
        @Valid @RequestBody
        updateRoleRequest: UpdateRoleRequest,
    ): RoleResponse {
        val updatedRole = roleService.updateRole(
            RoleId(id),
            updateRoleRequest.name,
            updateRoleRequest.description,
            updateRoleRequest.policies?.map {
                Policy(
                    id = it.id,
                    action = it.action,
                    effect = it.effect,
                    resource = it.resource,
                )
            }?.toSet(),
        )
        return RoleResponse.fromDto(updatedRole)
    }

    @DeleteMapping("/{id}")
    fun deleteRole(@PathVariable id: String) {
        roleService.deleteRole(RoleId(id))
    }
}
