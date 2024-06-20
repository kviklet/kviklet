package dev.kviklet.kviklet

import dev.kviklet.kviklet.helper.ExecutionRequestDetailsFactory
import dev.kviklet.kviklet.service.dto.ReviewStatus
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class ExecutionRequestTest {

    private val executionRequestDetailsFactory = ExecutionRequestDetailsFactory()

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
        val details = executionRequestDetailsFactory.createExecutionRequestDetails(
            events = mutableSetOf(executionRequestDetailsFactory.createReviewRejectedEvent()),
        )
        assert(details.resolveReviewStatus() == ReviewStatus.REJECTED)
    }
}
