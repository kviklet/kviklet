package com.example.executiongate.controller

import com.example.executiongate.db.RoleAdapter
import com.example.executiongate.service.dto.Role
import com.example.executiongate.service.dto.Policy
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


data class CreateGroupRequest(
    val name: String,
    val description: String
)

data class PolicyResponse(
    val id: String,
    val action: String,
    val effect: String,
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
    val groups: List<RoleResponse>
) {
    companion object {
        fun fromGroups(groups: List<Role>): RolesResponse {
            return RolesResponse(
                groups = groups.map { RoleResponse.fromDto(it) }
            )
        }
    }
}


fun permissionsToPermissionString(policies: Set<Policy>): String {
    return policies.map { "${it.effect}:${it.action} on ${it.resource}" }.joinToString { ";" }
}

@RestController()
@Validated
@RequestMapping("/groups")
class GroupController(private val roleAdapter: RoleAdapter) {

    @GetMapping("/:id")
    fun getGroup(id: String): RoleResponse {
        val group = roleAdapter.findById(id)
        return RoleResponse.fromDto(group)
    }

    @GetMapping("/")
    fun getAllGroups(): List<RoleResponse> {
        val groups = roleAdapter.findAll()
        return groups.map { RoleResponse.fromDto(it) }
    }

    @PostMapping("/")
    fun createGroup(@Valid @RequestBody createGroupRequest: CreateGroupRequest): RoleResponse {
        val savedGroup = roleAdapter.create(
            Role(
                name = createGroupRequest.name,
                description = createGroupRequest.description
            )
        )
        return RoleResponse.fromDto(savedGroup)
    }

    @DeleteMapping("/:id")
    fun deleteGroup(id: String) {
        roleAdapter.delete(id)
    }

}