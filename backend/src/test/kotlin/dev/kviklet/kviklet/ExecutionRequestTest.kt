package dev.kviklet.kviklet

import dev.kviklet.kviklet.helper.EventFactory
import dev.kviklet.kviklet.helper.ExecutionRequestDetailsFactory
import dev.kviklet.kviklet.helper.ExecutionRequestFactory
import dev.kviklet.kviklet.helper.UserFactory
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.ExecutionStatus
import dev.kviklet.kviklet.service.dto.RequestType
import dev.kviklet.kviklet.service.dto.ReviewStatus
import dev.kviklet.kviklet.service.dto.utcTimeNow
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
class ExecutionRequestTest {

    private val executionRequestDetailsFactory = ExecutionRequestDetailsFactory()
    private val executionRequestFactory = ExecutionRequestFactory()
    private val eventFactory = EventFactory()
    private val userFactory = UserFactory()

    private val t1 = LocalDateTime.of(2021, 1, 1, 0, 0)
    private val t2 = LocalDateTime.of(2021, 1, 2, 0, 0)

    @Test
    fun `test review status with one approval`() {
        val details = executionRequestDetailsFactory.createExecutionRequestDetails()
        assert(details.resolveReviewStatus() == ReviewStatus.APPROVED)
    }

    @Test
    fun `test review status with no approvals`() {
        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            events = mutableSetOf(),
        )
        assert(details.resolveReviewStatus() == ReviewStatus.AWAITING_APPROVAL)
    }

    @Test
    fun `test review status with one rejection`() {
        val request = executionRequestFactory.createDatasourceExecutionRequest()
        val events = mutableSetOf<Event>(eventFactory.createReviewRejectedEvent(request = request))
        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )
        assert(details.resolveReviewStatus() == ReviewStatus.REJECTED)
    }

    @Test
    fun `test review status with one change and then approval`() {
        val request = executionRequestFactory.createDatasourceExecutionRequest()
        val reviewer = userFactory.createUser()
        val events = mutableSetOf<Event>(
            eventFactory.createReviewRequestedChangeEvent(request = request, author = reviewer, createdAt = t1),
            eventFactory.createReviewApprovedEvent(request = request, author = reviewer, createdAt = t2),
        )
        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )
        assert(details.resolveReviewStatus() == ReviewStatus.APPROVED)
    }

    @Test
    fun `test review status with one change requested and approval by different person`() {
        val request = executionRequestFactory.createDatasourceExecutionRequest()
        val reviewer1 = userFactory.createUser()
        val reviewer2 = userFactory.createUser()
        val events = mutableSetOf<Event>(
            eventFactory.createReviewRequestedChangeEvent(request = request, author = reviewer1, createdAt = t1),
            eventFactory.createReviewApprovedEvent(request = request, author = reviewer2, createdAt = t2),
        )
        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )
        assert(details.resolveReviewStatus() == ReviewStatus.CHANGE_REQUESTED)
    }

    @Test
    fun `test review status with approval and then execution error`() {
        val request = executionRequestFactory.createDatasourceExecutionRequest()
        val reviewer = userFactory.createUser()
        val executor = userFactory.createUser()
        val t3 = LocalDateTime.of(2021, 1, 3, 0, 0)
        val events = mutableSetOf<Event>(
            eventFactory.createReviewApprovedEvent(request = request, author = reviewer, createdAt = t1),
            eventFactory.createExecuteEventWithError(request = request, author = executor, createdAt = t3),
        )
        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )
        assert(details.resolveReviewStatus() == ReviewStatus.AWAITING_APPROVAL)
    }

    @Test
    fun `test execution status changes from ACTIVE to EXECUTED when max duration expires`() {
        // Create a temporary access request with 30 minute duration
        val request = executionRequestFactory.createDatasourceExecutionRequest(
            type = RequestType.TemporaryAccess,
            temporaryAccessDuration = Duration.ofMinutes(30),
        )

        // Create an execute event that happened 45 minutes ago (exceeding the 30 minute limit)
        val executor = userFactory.createUser()
        val executeTime = utcTimeNow().minusMinutes(45)
        val events = mutableSetOf<Event>(
            eventFactory.createReviewApprovedEvent(
                request = request,
                author = executor,
                createdAt = executeTime.minusMinutes(1),
            ),
            eventFactory.createExecuteEvent(request = request, author = executor, createdAt = executeTime),
        )

        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )

        // Since 45 minutes have passed and the limit was 30 minutes, status should be EXECUTED
        assert(details.resolveExecutionStatus() == ExecutionStatus.EXECUTED)
    }

    @Test
    fun `test execution status remains ACTIVE when max duration has not expired`() {
        // Create a temporary access request with 60 minute duration
        val request = executionRequestFactory.createDatasourceExecutionRequest(
            type = RequestType.TemporaryAccess,
            temporaryAccessDuration = Duration.ofMinutes(60),
        )

        // Create an execute event that happened 30 minutes ago (within the 60 minute limit)
        val executor = userFactory.createUser()
        val executeTime = utcTimeNow().minusMinutes(30)
        val events = mutableSetOf<Event>(
            eventFactory.createReviewApprovedEvent(
                request = request,
                author = executor,
                createdAt = executeTime.minusMinutes(1),
            ),
            eventFactory.createExecuteEvent(request = request, author = executor, createdAt = executeTime),
        )

        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )

        // Since only 30 minutes have passed and the limit is 60 minutes, status should be ACTIVE
        assert(details.resolveExecutionStatus() == ExecutionStatus.ACTIVE)
    }

    @Test
    fun `test execution status remains ACTIVE for infinite access (null duration)`() {
        // Create a temporary access request with null duration (infinite access)
        val request = executionRequestFactory.createDatasourceExecutionRequest(
            type = RequestType.TemporaryAccess,
            temporaryAccessDuration = null,
        )

        // Create an execute event that happened 5 days ago
        val executor = userFactory.createUser()
        val executeTime = utcTimeNow().minusDays(5)
        val events = mutableSetOf<Event>(
            eventFactory.createReviewApprovedEvent(
                request = request,
                author = executor,
                createdAt = executeTime.minusMinutes(1),
            ),
            eventFactory.createExecuteEvent(request = request, author = executor, createdAt = executeTime),
        )

        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            request = request,
            events = events,
        )

        // With infinite access (null duration), status should always be ACTIVE regardless of time passed
        assert(details.resolveExecutionStatus() == ExecutionStatus.ACTIVE)
    }
}
