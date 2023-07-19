package com.example.executiongate.controller

import com.example.executiongate.db.GroupAdapter
import com.example.executiongate.service.dto.Group
import com.example.executiongate.service.dto.Permission
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


data class CreateGroupRequest(
    val name: String,
    val description: String,
    val permissionString: String
)


data class GroupResponse(
    val id: String,
    val name: String,
    val description: String,
    val permissions: String
) {
    companion object {
        fun fromDto(dto: Group): GroupResponse {
            return GroupResponse(
                id = dto.id,
                name = dto.name,
                description = dto.description,
                permissions = permissionsTopermissionString(dto.permissions)
            )
        }
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
    fun getAllGroups(): List<GroupResponse> {
        val groups = groupAdapter.findAll()
        return groups.map { GroupResponse.fromDto(it) }
    }
}