package dev.kviklet.kviklet

import dev.kviklet.kviklet.helper.EventFactory
import dev.kviklet.kviklet.helper.ExecutionRequestDetailsFactory
import dev.kviklet.kviklet.helper.ExecutionRequestFactory
import dev.kviklet.kviklet.helper.UserFactory
import dev.kviklet.kviklet.service.dto.Event
import dev.kviklet.kviklet.service.dto.ReviewStatus
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
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
}
