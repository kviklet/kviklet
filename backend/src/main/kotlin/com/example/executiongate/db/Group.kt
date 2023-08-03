package com.example.executiongate.db

import com.example.executiongate.db.util.BaseEntity
import com.example.executiongate.service.EntityNotFound
import com.example.executiongate.service.dto.EventId
import com.example.executiongate.service.dto.Group
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import javax.persistence.*


@Entity
@Table(name = "usergroups")
data class GroupEntity(
    @Column(nullable = false)
    val name: String = "",
    @Column(nullable = false)
    val description: String = "",

    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    var permissions: Set<PermissionEntity> = HashSet()
) : BaseEntity(
) {
    fun toDto() = Group(
        id = id,
        name = name,
        description = description,
        permissions = permissions.map { it.toDto() }.toSet()
    )
}


interface GroupRepository : JpaRepository<GroupEntity, String>

@Service
class GroupAdapter(
    private val groupRepository: GroupRepository
) {
    fun findById(id: String): Group {
        return groupRepository.findByIdOrNull(id)?.toDto() ?: throw EntityNotFound(
            "Group not found",
            "Group with id $id does not exist"
        )
    }

    fun findByIds(ids: List<String>): List<Group> {
        return groupRepository.findAllById(ids).map { it.toDto() }
    }

    fun findAll(): List<Group> {
        return groupRepository.findAll().map { it.toDto() }
    }

    fun create(group: Group): Group {
        return groupRepository.save(
            GroupEntity(
                name = group.name,
                description = group.description
            )
        ).toDto()
    }

    fun delete(id: String) {
        groupRepository.deleteById(id)
    }
}