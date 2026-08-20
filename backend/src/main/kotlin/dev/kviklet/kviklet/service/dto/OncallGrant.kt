package dev.kviklet.kviklet.service.dto

import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Resource
import dev.kviklet.kviklet.security.SecuredDomainObject
import java.time.Duration
import java.time.LocalDateTime

enum class OncallGrantKind {
    ONCALL,
    OUTAGE,
    ;

    fun displayName(): String = when (this) {
        ONCALL -> "On-call"
        OUTAGE -> "Outage"
    }
}

data class OncallGrant(
    val id: String? = null,
    val userId: String,
    val kind: OncallGrantKind,
    val reason: String? = null,
    val startsAt: LocalDateTime,
    val endsAt: LocalDateTime,
    val bypassApproval: Boolean,
    val grantedByUserId: String,
    val createdAt: LocalDateTime = utcTimeNow(),
    val revokedAt: LocalDateTime? = null,
    val approvedAt: LocalDateTime? = null,
    val approvedByUserId: String? = null,
    val durationMinutes: Long = Duration.between(startsAt, endsAt).toMinutes().coerceAtLeast(1),
) : SecuredDomainObject {
    fun isPending(): Boolean = revokedAt == null && approvedAt == null

    fun isActive(now: LocalDateTime = utcTimeNow()): Boolean =
        revokedAt == null &&
            approvedAt != null &&
            !now.isBefore(startsAt) &&
            now.isBefore(endsAt)

    fun status(): String = when {
        isActive() -> "ACTIVE"
        isPending() -> "PENDING"
        else -> "ENDED"
    }

    override fun getSecuredObjectId(): String = userId

    override fun getDomainObjectType(): Resource = Resource.USER

    override fun getRelated(resource: Resource): SecuredDomainObject? =
        if (resource == Resource.USER) this else null

    companion object {
        val ALL_CONNECTION_POLICIES = listOf(
            Policy(action = Permission.DATASOURCE_CONNECTION_GET.getPermissionString(), resource = "*"),
            Policy(action = Permission.EXECUTION_REQUEST_GET.getPermissionString(), resource = "*"),
            Policy(action = Permission.EXECUTION_REQUEST_EDIT.getPermissionString(), resource = "*"),
            Policy(action = Permission.EXECUTION_REQUEST_EXECUTE.getPermissionString(), resource = "*"),
            Policy(action = Permission.EXECUTION_REQUEST_REVIEW.getPermissionString(), resource = "*"),
        )
    }
}
