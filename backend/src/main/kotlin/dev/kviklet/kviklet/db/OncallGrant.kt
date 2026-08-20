package dev.kviklet.kviklet.db

import dev.kviklet.kviklet.db.util.BaseEntity
import dev.kviklet.kviklet.service.dto.OncallGrant
import dev.kviklet.kviklet.service.dto.OncallGrantKind
import dev.kviklet.kviklet.service.dto.utcTimeNow
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Entity
@Table(name = "oncall_grant")
class OncallGrantEntity(
    @Column(name = "user_id", nullable = false)
    var userId: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var kind: OncallGrantKind = OncallGrantKind.ONCALL,

    @Column(columnDefinition = "TEXT")
    var reason: String? = null,

    @Column(name = "starts_at", nullable = false)
    var startsAt: LocalDateTime = utcTimeNow(),

    @Column(name = "ends_at", nullable = false)
    var endsAt: LocalDateTime = utcTimeNow(),

    @Column(name = "bypass_approval", nullable = false)
    var bypassApproval: Boolean = false,

    @Column(name = "granted_by_user_id", nullable = false)
    var grantedByUserId: String = "",

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = utcTimeNow(),

    @Column(name = "revoked_at")
    var revokedAt: LocalDateTime? = null,

    @Column(name = "approved_at")
    var approvedAt: LocalDateTime? = null,

    @Column(name = "approved_by_user_id")
    var approvedByUserId: String? = null,

    @Column(name = "duration_minutes", nullable = false)
    var durationMinutes: Long = 60,
) : BaseEntity() {
    fun toDto() = OncallGrant(
        id = id,
        userId = userId,
        kind = kind,
        reason = reason,
        startsAt = startsAt,
        endsAt = endsAt,
        bypassApproval = bypassApproval,
        grantedByUserId = grantedByUserId,
        createdAt = createdAt,
        revokedAt = revokedAt,
        approvedAt = approvedAt,
        approvedByUserId = approvedByUserId,
        durationMinutes = durationMinutes,
    )
}

interface OncallGrantRepository : JpaRepository<OncallGrantEntity, String> {
    @Query(
        "SELECT g FROM OncallGrantEntity g WHERE g.userId = :userId AND g.revokedAt IS NULL " +
            "AND g.approvedAt IS NOT NULL AND g.startsAt <= :now AND g.endsAt > :now",
    )
    fun findActiveForUser(@Param("userId") userId: String, @Param("now") now: LocalDateTime): List<OncallGrantEntity>

    @Query(
        "SELECT g FROM OncallGrantEntity g WHERE g.userId IN :userIds AND g.revokedAt IS NULL " +
            "AND g.approvedAt IS NOT NULL AND g.startsAt <= :now AND g.endsAt > :now",
    )
    fun findActiveForUsers(
        @Param("userIds") userIds: Collection<String>,
        @Param("now") now: LocalDateTime,
    ): List<OncallGrantEntity>

    @Query(
        "SELECT g FROM OncallGrantEntity g WHERE g.userId = :userId AND g.revokedAt IS NULL " +
            "AND g.approvedAt IS NULL",
    )
    fun findPendingForUser(@Param("userId") userId: String): List<OncallGrantEntity>

    @Query(
        "SELECT g FROM OncallGrantEntity g WHERE g.userId IN :userIds AND g.revokedAt IS NULL " +
            "AND g.approvedAt IS NULL",
    )
    fun findPendingForUsers(@Param("userIds") userIds: Collection<String>): List<OncallGrantEntity>

    @Query(
        "SELECT g FROM OncallGrantEntity g WHERE g.userId = :userId AND g.revokedAt IS NULL",
    )
    fun findOpenForUser(@Param("userId") userId: String): List<OncallGrantEntity>
}

@Service
class OncallGrantAdapter(private val oncallGrantRepository: OncallGrantRepository) {

    @Transactional(readOnly = true)
    fun findActiveForUser(userId: String, now: LocalDateTime = utcTimeNow()): OncallGrant? =
        oncallGrantRepository.findActiveForUser(userId, now).maxByOrNull { it.endsAt }?.toDto()

    @Transactional(readOnly = true)
    fun findActiveForUsers(userIds: Collection<String>, now: LocalDateTime = utcTimeNow()): Map<String, OncallGrant> {
        if (userIds.isEmpty()) return emptyMap()
        return oncallGrantRepository.findActiveForUsers(userIds, now)
            .map { it.toDto() }
            .groupBy { it.userId }
            .mapValues { (_, grants) -> grants.maxBy { it.endsAt } }
    }

    @Transactional(readOnly = true)
    fun findPendingForUser(userId: String): OncallGrant? =
        oncallGrantRepository.findPendingForUser(userId).maxByOrNull { it.createdAt }?.toDto()

    @Transactional(readOnly = true)
    fun findPendingForUsers(userIds: Collection<String>): Map<String, OncallGrant> {
        if (userIds.isEmpty()) return emptyMap()
        return oncallGrantRepository.findPendingForUsers(userIds)
            .map { it.toDto() }
            .groupBy { it.userId }
            .mapValues { (_, grants) -> grants.maxBy { it.createdAt } }
    }

    @Transactional
    fun save(grant: OncallGrant): OncallGrant {
        val entity = if (grant.id != null) {
            oncallGrantRepository.findById(grant.id).orElseGet { OncallGrantEntity() }.apply {
                id = grant.id
            }
        } else {
            OncallGrantEntity()
        }
        entity.userId = grant.userId
        entity.kind = grant.kind
        entity.reason = grant.reason
        entity.startsAt = grant.startsAt
        entity.endsAt = grant.endsAt
        entity.bypassApproval = grant.bypassApproval
        entity.grantedByUserId = grant.grantedByUserId
        entity.createdAt = grant.createdAt
        entity.revokedAt = grant.revokedAt
        entity.approvedAt = grant.approvedAt
        entity.approvedByUserId = grant.approvedByUserId
        entity.durationMinutes = grant.durationMinutes
        return oncallGrantRepository.save(entity).toDto()
    }

    @Transactional
    fun revokeOpenForUser(userId: String, now: LocalDateTime = utcTimeNow()) {
        oncallGrantRepository.findOpenForUser(userId).forEach { grant ->
            grant.revokedAt = now
            oncallGrantRepository.save(grant)
        }
    }
}
