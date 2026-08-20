package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.OncallGrantAdapter
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.db.UserId
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.service.dto.OncallGrant
import dev.kviklet.kviklet.service.dto.OncallGrantKind
import dev.kviklet.kviklet.service.dto.utcTimeNow
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

@Service
class OncallGrantService(
    private val oncallGrantAdapter: OncallGrantAdapter,
    private val userAdapter: UserAdapter,
    private val executionRequestAdapter: ExecutionRequestAdapter,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    companion object {
        val MIN_DURATION: Duration = Duration.ofMinutes(15)
        val MAX_DURATION: Duration = Duration.ofDays(10)
    }

    @Transactional
    @Policy(Permission.USER_GET)
    fun startGrant(
        userId: UserId,
        kind: OncallGrantKind,
        durationMinutes: Long,
        reason: String?,
        bypassApproval: Boolean?,
        actorUserId: String,
    ): OncallGrant {
        val actor = requireManager(actorUserId)
        userAdapter.findById(userId.toString())
        val duration = validatedDuration(durationMinutes)
        val now = utcTimeNow()
        oncallGrantAdapter.revokeOpenForUser(userId.toString(), now)
        val effectiveBypass = bypassApproval ?: (kind == OncallGrantKind.OUTAGE)
        val grant = oncallGrantAdapter.save(
            OncallGrant(
                userId = userId.toString(),
                kind = kind,
                reason = reason?.takeIf { it.isNotBlank() },
                startsAt = now,
                endsAt = now.plus(duration),
                bypassApproval = effectiveBypass,
                grantedByUserId = actor.getId()!!,
                createdAt = now,
                approvedAt = now,
                approvedByUserId = actor.getId()!!,
                durationMinutes = duration.toMinutes(),
            ),
        )
        refreshAuthorRequestStatuses(userId.toString())
        return grant
    }

    @Transactional
    @Policy(Permission.USER_GET)
    fun requestGrant(
        userId: UserId,
        kind: OncallGrantKind,
        durationMinutes: Long,
        reason: String?,
        bypassApproval: Boolean?,
        actorUserId: String,
    ): OncallGrant {
        if (actorUserId != userId.toString()) {
            throw AccessDeniedException("You can only request on-call / outage access for yourself")
        }
        userAdapter.findById(userId.toString())
        val duration = validatedDuration(durationMinutes)
        val now = utcTimeNow()
        if (oncallGrantAdapter.findActiveForUser(userId.toString(), now) != null) {
            throw IllegalArgumentException("This user already has active on-call / outage access")
        }
        oncallGrantAdapter.findPendingForUser(userId.toString())?.let { pending ->
            oncallGrantAdapter.save(pending.copy(revokedAt = now))
        }
        val requester = userAdapter.findById(actorUserId)
        val effectiveBypass = bypassApproval ?: (kind == OncallGrantKind.OUTAGE)
        val grant = oncallGrantAdapter.save(
            OncallGrant(
                userId = userId.toString(),
                kind = kind,
                reason = reason?.takeIf { it.isNotBlank() },
                startsAt = now,
                endsAt = now.plus(duration),
                bypassApproval = effectiveBypass,
                grantedByUserId = actorUserId,
                createdAt = now,
                approvedAt = null,
                approvedByUserId = null,
                durationMinutes = duration.toMinutes(),
            ),
        )
        applicationEventPublisher.publishEvent(
            OncallGrantRequestedEvent(
                grantId = grant.id!!,
                requesterName = requester.fullName?.takeIf { it.isNotBlank() } ?: requester.email,
                kind = grant.kind.displayName(),
                durationMinutes = grant.durationMinutes,
                reason = grant.reason,
            ),
        )
        return grant
    }

    @Transactional
    @Policy(Permission.USER_GET)
    fun approvePending(userId: UserId, actorUserId: String): OncallGrant {
        val actor = requireManager(actorUserId)
        val pending = oncallGrantAdapter.findPendingForUser(userId.toString())
            ?: throw IllegalArgumentException("No pending on-call / outage request for this user")
        val now = utcTimeNow()
        val duration = Duration.ofMinutes(pending.durationMinutes)
        val grant = oncallGrantAdapter.save(
            pending.copy(
                startsAt = now,
                endsAt = now.plus(duration),
                approvedAt = now,
                approvedByUserId = actor.getId()!!,
            ),
        )
        refreshAuthorRequestStatuses(userId.toString())
        return grant
    }

    @Transactional
    @Policy(Permission.USER_GET)
    fun revokeOpen(userId: UserId, actorUserId: String) {
        requireManager(actorUserId)
        userAdapter.findById(userId.toString())
        oncallGrantAdapter.revokeOpenForUser(userId.toString())
        refreshAuthorRequestStatuses(userId.toString())
    }

    private fun requireManager(actorUserId: String): User {
        val actor = userAdapter.findById(actorUserId)
        if (!actor.canManageOncallGrants()) {
            throw AccessDeniedException("Only managers or admins can approve on-call / outage access")
        }
        return actor
    }

    private fun validatedDuration(durationMinutes: Long): Duration {
        val duration = Duration.ofMinutes(durationMinutes)
        if (duration < MIN_DURATION || duration > MAX_DURATION) {
            throw IllegalArgumentException(
                "Duration must be between ${MIN_DURATION.toMinutes()} minutes and ${MAX_DURATION.toDays()} days",
            )
        }
        return duration
    }

    private fun refreshAuthorRequestStatuses(userId: String) {
        executionRequestAdapter.listExecutionRequestsFiltered(authorId = userId).forEach { details ->
            val requestId = details.request.id ?: return@forEach
            executionRequestAdapter.updateExecutionRequest(
                id = requestId,
                reviewStatus = details.resolveReviewStatus(),
                executionStatus = details.resolveExecutionStatus(),
            )
        }
    }
}
