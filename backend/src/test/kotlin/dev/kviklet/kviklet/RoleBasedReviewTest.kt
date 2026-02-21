package dev.kviklet.kviklet

import dev.kviklet.kviklet.helper.ConnectionFactory
import dev.kviklet.kviklet.helper.EventFactory
import dev.kviklet.kviklet.helper.ExecutionRequestDetailsFactory
import dev.kviklet.kviklet.helper.ExecutionRequestFactory
import dev.kviklet.kviklet.helper.RoleFactory
import dev.kviklet.kviklet.helper.UserFactory
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.ReviewConfig
import dev.kviklet.kviklet.service.dto.ReviewStatus
import dev.kviklet.kviklet.service.dto.RoleRequirement
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
class RoleBasedReviewTest {

    private val executionRequestDetailsFactory = ExecutionRequestDetailsFactory()
    private val executionRequestFactory = ExecutionRequestFactory()
    private val eventFactory = EventFactory()
    private val userFactory = UserFactory()
    private val roleFactory = RoleFactory()
    private val connectionFactory = ConnectionFactory()

    private val t1 = LocalDateTime.of(2025, 1, 1, 10, 0)
    private val t2 = LocalDateTime.of(2025, 1, 1, 11, 0)
    private val t3 = LocalDateTime.of(2025, 1, 1, 12, 0)

    @Test
    fun `request with only numTotalRequired is approved when count is met`() {
        val connection = connectionFactory.createDatasourceConnection(
            reviewConfig = ReviewConfig(numTotalRequired = 2),
        )
        val request = executionRequestFactory.createDatasourceExecutionRequest(connection = connection)
        val approver1 = userFactory.createUser()
        val approver2 = userFactory.createUser()
        val events = mutableSetOf<Event>(
            eventFactory.createReviewApprovedEvent(request = request, author = approver1),
            eventFactory.createReviewApprovedEvent(request = request, author = approver2),
        )
        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )
        details.resolveReviewStatus() shouldBe ReviewStatus.APPROVED
    }

    @Test
    fun `request needs specific role approval - non-role approver not sufficient`() {
        val dbaRole = roleFactory.createRole(name = "DBA")
        val devRole = roleFactory.createRole(name = "Developer")

        val connection = connectionFactory.createDatasourceConnection(
            reviewConfig = ReviewConfig(
                numTotalRequired = 1,
                roleRequirements = listOf(RoleRequirement(roleId = dbaRole.getId()!!, numRequired = 1)),
            ),
        )
        val request = executionRequestFactory.createDatasourceExecutionRequest(connection = connection)

        // Approve with a developer (non-DBA)
        val developer = userFactory.createUser(roles = setOf(devRole))
        val events = mutableSetOf<Event>(
            eventFactory.createReviewApprovedEvent(request = request, author = developer),
        )
        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )
        details.resolveReviewStatus() shouldBe ReviewStatus.AWAITING_APPROVAL
    }

    @Test
    fun `request needs specific role approval - role approver is sufficient`() {
        val dbaRole = roleFactory.createRole(name = "DBA")

        val connection = connectionFactory.createDatasourceConnection(
            reviewConfig = ReviewConfig(
                numTotalRequired = 1,
                roleRequirements = listOf(RoleRequirement(roleId = dbaRole.getId()!!, numRequired = 1)),
            ),
        )
        val request = executionRequestFactory.createDatasourceExecutionRequest(connection = connection)

        val dba = userFactory.createUser(roles = setOf(dbaRole))
        val events = mutableSetOf<Event>(
            eventFactory.createReviewApprovedEvent(request = request, author = dba),
        )
        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )
        details.resolveReviewStatus() shouldBe ReviewStatus.APPROVED
    }

    @Test
    fun `request needs multiple role approvals`() {
        val dbaRole = roleFactory.createRole(name = "DBA")
        val securityRole = roleFactory.createRole(name = "Security")

        val connection = connectionFactory.createDatasourceConnection(
            reviewConfig = ReviewConfig(
                numTotalRequired = 2,
                roleRequirements = listOf(
                    RoleRequirement(roleId = dbaRole.getId()!!, numRequired = 1),
                    RoleRequirement(roleId = securityRole.getId()!!, numRequired = 1),
                ),
            ),
        )
        val request = executionRequestFactory.createDatasourceExecutionRequest(connection = connection)

        // 1 DBA + 1 Security -> approved
        val dba = userFactory.createUser(roles = setOf(dbaRole))
        val security = userFactory.createUser(roles = setOf(securityRole))
        val events = mutableSetOf<Event>(
            eventFactory.createReviewApprovedEvent(request = request, author = dba),
            eventFactory.createReviewApprovedEvent(request = request, author = security),
        )
        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )
        details.resolveReviewStatus() shouldBe ReviewStatus.APPROVED
    }

    @Test
    fun `request needs multiple role approvals - missing one role`() {
        val dbaRole = roleFactory.createRole(name = "DBA")
        val securityRole = roleFactory.createRole(name = "Security")

        val connection = connectionFactory.createDatasourceConnection(
            reviewConfig = ReviewConfig(
                numTotalRequired = 2,
                roleRequirements = listOf(
                    RoleRequirement(roleId = dbaRole.getId()!!, numRequired = 1),
                    RoleRequirement(roleId = securityRole.getId()!!, numRequired = 1),
                ),
            ),
        )
        val request = executionRequestFactory.createDatasourceExecutionRequest(connection = connection)

        // 2 DBAs -> not approved (missing security)
        val dba1 = userFactory.createUser(roles = setOf(dbaRole))
        val dba2 = userFactory.createUser(roles = setOf(dbaRole))
        val events = mutableSetOf<Event>(
            eventFactory.createReviewApprovedEvent(request = request, author = dba1),
            eventFactory.createReviewApprovedEvent(request = request, author = dba2),
        )
        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )
        details.resolveReviewStatus() shouldBe ReviewStatus.AWAITING_APPROVAL
    }

    @Test
    fun `user with multiple roles satisfies multiple role requirements`() {
        val dbaRole = roleFactory.createRole(name = "DBA")
        val securityRole = roleFactory.createRole(name = "Security")
        val devRole = roleFactory.createRole(name = "Developer")

        val connection = connectionFactory.createDatasourceConnection(
            reviewConfig = ReviewConfig(
                numTotalRequired = 2,
                roleRequirements = listOf(
                    RoleRequirement(roleId = dbaRole.getId()!!, numRequired = 1),
                    RoleRequirement(roleId = securityRole.getId()!!, numRequired = 1),
                ),
            ),
        )
        val request = executionRequestFactory.createDatasourceExecutionRequest(connection = connection)

        // 1 user with DBA+Security + 1 Developer -> approved (2 total, both role requirements met)
        val multiRoleUser = userFactory.createUser(roles = setOf(dbaRole, securityRole))
        val developer = userFactory.createUser(roles = setOf(devRole))
        val events = mutableSetOf<Event>(
            eventFactory.createReviewApprovedEvent(request = request, author = multiRoleUser),
            eventFactory.createReviewApprovedEvent(request = request, author = developer),
        )
        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )
        details.resolveReviewStatus() shouldBe ReviewStatus.APPROVED
    }

    @Test
    fun `single user with multiple roles but insufficient total count`() {
        val dbaRole = roleFactory.createRole(name = "DBA")
        val securityRole = roleFactory.createRole(name = "Security")

        val connection = connectionFactory.createDatasourceConnection(
            reviewConfig = ReviewConfig(
                numTotalRequired = 2,
                roleRequirements = listOf(
                    RoleRequirement(roleId = dbaRole.getId()!!, numRequired = 1),
                    RoleRequirement(roleId = securityRole.getId()!!, numRequired = 1),
                ),
            ),
        )
        val request = executionRequestFactory.createDatasourceExecutionRequest(connection = connection)

        // 1 user with DBA+Security -> not approved (only 1 total, need 2)
        val multiRoleUser = userFactory.createUser(roles = setOf(dbaRole, securityRole))
        val events = mutableSetOf<Event>(
            eventFactory.createReviewApprovedEvent(request = request, author = multiRoleUser),
        )
        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )
        details.resolveReviewStatus() shouldBe ReviewStatus.AWAITING_APPROVAL
    }

    @Test
    fun `numTotalRequired acts as floor`() {
        val dbaRole = roleFactory.createRole(name = "DBA")
        val devRole = roleFactory.createRole(name = "Developer")

        val connection = connectionFactory.createDatasourceConnection(
            reviewConfig = ReviewConfig(
                numTotalRequired = 3,
                roleRequirements = listOf(RoleRequirement(roleId = dbaRole.getId()!!, numRequired = 1)),
            ),
        )
        val request = executionRequestFactory.createDatasourceExecutionRequest(connection = connection)

        // 1 DBA + 1 other -> not approved (only 2 total, need 3)
        val dba = userFactory.createUser(roles = setOf(dbaRole))
        val dev = userFactory.createUser(roles = setOf(devRole))
        val events = mutableSetOf<Event>(
            eventFactory.createReviewApprovedEvent(request = request, author = dba),
            eventFactory.createReviewApprovedEvent(request = request, author = dev),
        )
        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )
        details.resolveReviewStatus() shouldBe ReviewStatus.AWAITING_APPROVAL

        // 1 DBA + 2 others -> approved
        val dev2 = userFactory.createUser(roles = setOf(devRole))
        val allEvents = mutableSetOf<Event>(
            eventFactory.createReviewApprovedEvent(request = request, author = dba),
            eventFactory.createReviewApprovedEvent(request = request, author = dev),
            eventFactory.createReviewApprovedEvent(request = request, author = dev2),
        )
        val detailsWithThree = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = allEvents,
        )
        detailsWithThree.resolveReviewStatus() shouldBe ReviewStatus.APPROVED
    }

    @Test
    fun `empty roleRequirements array treated as null`() {
        val connection = connectionFactory.createDatasourceConnection(
            reviewConfig = ReviewConfig(numTotalRequired = 2, roleRequirements = emptyList()),
        )
        val request = executionRequestFactory.createDatasourceExecutionRequest(connection = connection)
        val approver1 = userFactory.createUser()
        val approver2 = userFactory.createUser()
        val events = mutableSetOf<Event>(
            eventFactory.createReviewApprovedEvent(request = request, author = approver1),
            eventFactory.createReviewApprovedEvent(request = request, author = approver2),
        )
        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )
        // No role checks, just total count
        details.resolveReviewStatus() shouldBe ReviewStatus.APPROVED
    }

    @Test
    fun `getApprovalProgress returns correct progress info`() {
        val dbaRole = roleFactory.createRole(name = "DBA")
        val securityRole = roleFactory.createRole(name = "Security")

        val connection = connectionFactory.createDatasourceConnection(
            reviewConfig = ReviewConfig(
                numTotalRequired = 3,
                roleRequirements = listOf(
                    RoleRequirement(roleId = dbaRole.getId()!!, numRequired = 1),
                    RoleRequirement(roleId = securityRole.getId()!!, numRequired = 1),
                ),
            ),
        )
        val request = executionRequestFactory.createDatasourceExecutionRequest(connection = connection)

        // 1 DBA approved, no security
        val dba = userFactory.createUser(roles = setOf(dbaRole))
        val events = mutableSetOf<Event>(
            eventFactory.createReviewApprovedEvent(request = request, author = dba),
        )
        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )
        val progress = details.getApprovalProgress()
        progress.totalRequired shouldBe 3
        progress.totalCurrent shouldBe 1
        progress.roleProgress.size shouldBe 2

        // Find DBA and Security progress
        val dbaProgress = progress.roleProgress.find { it.roleId == dbaRole.getId() }!!
        dbaProgress.numRequired shouldBe 1
        dbaProgress.numCurrent shouldBe 1

        val securityProgress = progress.roleProgress.find { it.roleId == securityRole.getId() }!!
        securityProgress.numRequired shouldBe 1
        securityProgress.numCurrent shouldBe 0
    }

    @Test
    fun `getApprovalProgress returns all satisfied when complete`() {
        val dbaRole = roleFactory.createRole(name = "DBA")

        val connection = connectionFactory.createDatasourceConnection(
            reviewConfig = ReviewConfig(
                numTotalRequired = 1,
                roleRequirements = listOf(RoleRequirement(roleId = dbaRole.getId()!!, numRequired = 1)),
            ),
        )
        val request = executionRequestFactory.createDatasourceExecutionRequest(connection = connection)

        val dba = userFactory.createUser(roles = setOf(dbaRole))
        val events = mutableSetOf<Event>(
            eventFactory.createReviewApprovedEvent(request = request, author = dba),
        )
        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )
        val progress = details.getApprovalProgress()
        progress.totalRequired shouldBe 1
        progress.totalCurrent shouldBe 1
        progress.roleProgress.size shouldBe 1
        progress.roleProgress[0].numRequired shouldBe 1
        progress.roleProgress[0].numCurrent shouldBe 1
    }

    @Test
    fun `getApprovalProgress returns empty roleProgress when no role requirements`() {
        val connection = connectionFactory.createDatasourceConnection(
            reviewConfig = ReviewConfig(numTotalRequired = 2, roleRequirements = null),
        )
        val request = executionRequestFactory.createDatasourceExecutionRequest(connection = connection)

        val approver1 = userFactory.createUser()
        val events = mutableSetOf<Event>(
            eventFactory.createReviewApprovedEvent(request = request, author = approver1),
        )
        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )
        val progress = details.getApprovalProgress()
        progress.totalRequired shouldBe 2
        progress.totalCurrent shouldBe 1
        progress.roleProgress shouldBe emptyList()
    }

    @Test
    fun `getApprovalProgress includes approver names`() {
        val dbaRole = roleFactory.createRole(name = "DBA")

        val connection = connectionFactory.createDatasourceConnection(
            reviewConfig = ReviewConfig(
                numTotalRequired = 1,
                roleRequirements = listOf(RoleRequirement(roleId = dbaRole.getId()!!, numRequired = 1)),
            ),
        )
        val request = executionRequestFactory.createDatasourceExecutionRequest(connection = connection)

        val dba = userFactory.createUser(fullName = "John Doe", email = "john@example.com", roles = setOf(dbaRole))
        val events = mutableSetOf<Event>(
            eventFactory.createReviewApprovedEvent(request = request, author = dba),
        )
        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )
        val progress = details.getApprovalProgress()
        progress.roleProgress.size shouldBe 1
        progress.roleProgress[0].approverNames.size shouldBe 1
        progress.roleProgress[0].approverNames[0] shouldBe "John Doe (john@example.com)"
    }

    @Test
    fun `approver who later requests changes is not counted as approver`() {
        val connection = connectionFactory.createDatasourceConnection(
            reviewConfig = ReviewConfig(numTotalRequired = 1),
        )
        val request = executionRequestFactory.createDatasourceExecutionRequest(connection = connection)
        val reviewer = userFactory.createUser(fullName = "Alice", email = "alice@example.com")

        val events = mutableSetOf<Event>(
            eventFactory.createReviewApprovedEvent(request = request, author = reviewer, createdAt = t1),
            eventFactory.createReviewRequestedChangeEvent(request = request, author = reviewer, createdAt = t2),
        )
        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )

        val progress = details.getApprovalProgress()
        progress.totalCurrent shouldBe 0
        progress.changeRequestedBy.size shouldBe 1
        progress.changeRequestedBy[0] shouldBe "Alice"

        details.resolveReviewStatus() shouldBe ReviewStatus.CHANGE_REQUESTED
    }

    @Test
    fun `approver who requests changes then re-approves is counted`() {
        val connection = connectionFactory.createDatasourceConnection(
            reviewConfig = ReviewConfig(numTotalRequired = 1),
        )
        val request = executionRequestFactory.createDatasourceExecutionRequest(connection = connection)
        val reviewer = userFactory.createUser()

        val events = mutableSetOf<Event>(
            eventFactory.createReviewApprovedEvent(request = request, author = reviewer, createdAt = t1),
            eventFactory.createReviewRequestedChangeEvent(request = request, author = reviewer, createdAt = t2),
            eventFactory.createReviewApprovedEvent(request = request, author = reviewer, createdAt = t3),
        )
        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )

        val progress = details.getApprovalProgress()
        progress.totalCurrent shouldBe 1
        progress.changeRequestedBy.shouldBeEmpty()

        details.resolveReviewStatus() shouldBe ReviewStatus.APPROVED
    }

    @Test
    fun `mixed approver and change requester with role requirements`() {
        val dbaRole = roleFactory.createRole(name = "DBA")

        val connection = connectionFactory.createDatasourceConnection(
            reviewConfig = ReviewConfig(
                numTotalRequired = 2,
                roleRequirements = listOf(RoleRequirement(roleId = dbaRole.getId()!!, numRequired = 1)),
            ),
        )
        val request = executionRequestFactory.createDatasourceExecutionRequest(connection = connection)

        val dbaA = userFactory.createUser(fullName = "DBA Alice", email = "dba-a@example.com", roles = setOf(dbaRole))
        val dbaB = userFactory.createUser(fullName = "DBA Bob", email = "dba-b@example.com", roles = setOf(dbaRole))

        val events = mutableSetOf<Event>(
            eventFactory.createReviewApprovedEvent(request = request, author = dbaA, createdAt = t1),
            eventFactory.createReviewApprovedEvent(request = request, author = dbaB, createdAt = t2),
            eventFactory.createReviewRequestedChangeEvent(request = request, author = dbaB, createdAt = t3),
        )
        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )

        val progress = details.getApprovalProgress()
        progress.totalCurrent shouldBe 1
        progress.changeRequestedBy.size shouldBe 1
        progress.changeRequestedBy[0] shouldBe "DBA Bob"

        val dbaProgress = progress.roleProgress.find { it.roleId == dbaRole.getId() }!!
        dbaProgress.numCurrent shouldBe 1

        details.resolveReviewStatus() shouldBe ReviewStatus.CHANGE_REQUESTED
    }
}
