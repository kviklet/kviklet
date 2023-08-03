package com.example.executiongate.controller

import com.example.executiongate.db.GroupAdapter
import com.example.executiongate.service.dto.Group
import com.example.executiongate.service.dto.Permission
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.validation.Valid


data class CreateGroupRequest(
    val name: String,
    val description: String
)

data class PermissionResponse(
    val scope: String,
    val permissions: List<String>
)

data class GroupResponse(
    val id: String,
    val name: String,
    val description: String,
    val permissions: List<PermissionResponse>
) {
    companion object {
        fun fromDto(dto: Group): GroupResponse {
            return GroupResponse(
                id = dto.id,
                name = dto.name,
                description = dto.description,
                permissions = permissionsToPermissionResponse(dto.permissions)
            )
        }
    }
}

data class GroupsResponse(
    val groups: List<GroupResponse>
) {
    companion object {
        fun fromGroups(groups: List<Group>): GroupsResponse {
            return GroupsResponse(
                groups = groups.map { GroupResponse.fromDto(it) }
            )
        }
    }
}

fun permissionsToPermissionResponse(permissions: Set<Permission>): List<PermissionResponse> {
    return permissions.groupBy { it.scope }
        .entries
        .map { (scope, permissionList) ->
            PermissionResponse(
                scope = scope,
                permissions = permissionList.map { it.action }
            )
        }
}

fun permissionsTopermissionString(permissions: Set<Permission>): String {
    return permissions.groupBy { it.action }
        .entries
        .joinToString(separator = ";") { (action, permissionList) ->
            "$action:${permissionList.joinToString(separator = ",") { it.scope }}"
        }
}
@RestController()
@Validated
@RequestMapping("/groups")
class GroupController(private val groupAdapter: GroupAdapter) {

    @GetMapping("/:id")
    fun getGroup(id: String): GroupResponse {
        val group = groupAdapter.findById(id)
        return GroupResponse.fromDto(group)
    }

    @GetMapping("/")
    fun getAllGroups(): GroupsResponse {
        val groups = groupAdapter.findAll()
        return GroupsResponse.fromGroups(groups)
    }

    @PostMapping("/")
    fun createGroup(@Valid @RequestBody createGroupRequest: CreateGroupRequest): GroupResponse {
        val savedGroup = groupAdapter.create(
            Group(
                name = createGroupRequest.name,
                description = createGroupRequest.description
            )
        )
        return GroupResponse.fromDto(savedGroup)
    }

    @DeleteMapping("/:id")
    fun deleteGroup(id: String) {
        groupAdapter.delete(id)
    }
}