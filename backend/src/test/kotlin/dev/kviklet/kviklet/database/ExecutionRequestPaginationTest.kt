package dev.kviklet.kviklet.database

import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.ExecutionStatus
import dev.kviklet.kviklet.service.dto.ReviewStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@ActiveProfiles("test")
class ExecutionRequestPaginationTest {

    @Autowired
    private lateinit var executionRequestHelper: ExecutionRequestHelper

    @Autowired
    private lateinit var connectionHelper: ConnectionHelper

    @Autowired
    private lateinit var userHelper: UserHelper

    @AfterEach
    fun cleanup() {
        executionRequestHelper.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
    }

    @BeforeEach
    fun setup() {
        // Clean up before each test
        executionRequestHelper.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
    }

    @Test
    fun `test pagination returns correct page size`() {
        val testUser = userHelper.createUser()
        val connection = connectionHelper.createDummyConnection()

        // Create 25 execution requests
        repeat(25) { index ->
            executionRequestHelper.createExecutionRequest(
                author = testUser,
                description = "Test request $index",
                connection = connection,
            )
            // Small delay to ensure different createdAt timestamps
            Thread.sleep(10)
        }

        // Fetch first page with limit 10
        val entities = executionRequestHelper.listExecutionRequestsFiltered(
            reviewStatuses = null,
            executionStatuses = null,
            connectionId = null,
            after = null,
            limit = 10,
        )

        assertThat(entities.size).isEqualTo(10)
    }

    @Test
    fun `test pagination hasMore flag is correct`() {
        val testUser = userHelper.createUser()
        val connection = connectionHelper.createDummyConnection()

        // Create 15 execution requests
        repeat(15) {
            executionRequestHelper.createExecutionRequest(
                author = testUser,
                description = "Test request",
                connection = connection,
            )
            Thread.sleep(10)
        }

        // Fetch with limit 10 - should have more
        // We fetch limit + 1 to check for hasMore
        val resultWithMore = executionRequestHelper.listExecutionRequestsFiltered(
            reviewStatuses = null,
            executionStatuses = null,
            connectionId = null,
            after = null,
            limit = 11,  // limit + 1
        )

        // If we get 11 results, hasMore should be true
        assertThat(resultWithMore.size).isEqualTo(11)

        // Fetch with limit 20 - should not have more
        val resultWithoutMore = executionRequestHelper.listExecutionRequestsFiltered(
            reviewStatuses = null,
            executionStatuses = null,
            connectionId = null,
            after = null,
            limit = 20,
        )

        assertThat(resultWithoutMore.size).isEqualTo(15)
    }

    @Test
    @Transactional
    fun `test cursor-based pagination works correctly`() {
        val testUser = userHelper.createUser()
        val connection = connectionHelper.createDummyConnection()

        // Create 30 execution requests
        repeat(30) { index ->
            executionRequestHelper.createExecutionRequest(
                author = testUser,
                description = "Test request $index",
                connection = connection,
            )
            Thread.sleep(10)
        }

        // Fetch first page (limit + 1 to check for hasMore)
        val firstPage = executionRequestHelper.listExecutionRequestsFiltered(
            reviewStatuses = null,
            executionStatuses = null,
            connectionId = null,
            after = null,
            limit = 11,  // limit + 1
        )

        assertThat(firstPage.size).isEqualTo(11) // limit + 1

        // Get the cursor (createdAt of the 10th item) by converting to DTO
        val tenthEntity = firstPage[9]
        val tenthDto = executionRequestHelper.toDetailDto(tenthEntity)
        val cursor = tenthDto.request.createdAt

        // Fetch second page using cursor (limit + 1 to check for hasMore)
        val secondPage = executionRequestHelper.listExecutionRequestsFiltered(
            reviewStatuses = null,
            executionStatuses = null,
            connectionId = null,
            after = cursor,
            limit = 11,  // limit + 1
        )

        assertThat(secondPage.size).isEqualTo(11) // limit + 1

        // Ensure no overlap between pages
        val firstPageIds = firstPage.take(10).map { it.id }.toSet()
        val secondPageIds = secondPage.take(10).map { it.id }.toSet()
        assertThat(firstPageIds.intersect(secondPageIds)).isEmpty()
    }

    @Test
    fun `test filtering by reviewStatus`() {
        val testUser = userHelper.createUser()
        val connection = connectionHelper.createDummyConnection()

        // Create requests with different review statuses
        repeat(10) {
            executionRequestHelper.createExecutionRequest(
                author = testUser,
                description = "Awaiting approval",
                connection = connection,
                reviewStatus = ReviewStatus.AWAITING_APPROVAL,
            )
            Thread.sleep(10)
        }

        repeat(5) {
            executionRequestHelper.createExecutionRequest(
                author = testUser,
                description = "Approved",
                connection = connection,
                reviewStatus = ReviewStatus.APPROVED,
            )
            Thread.sleep(10)
        }

        // Filter by AWAITING_APPROVAL
        val awaitingResults = executionRequestHelper.listExecutionRequestsFiltered(
            reviewStatuses = setOf(ReviewStatus.AWAITING_APPROVAL),
            executionStatuses = null,
            connectionId = null,
            after = null,
            limit = 20,
        )

        assertThat(awaitingResults.size).isEqualTo(10)
        assertThat(awaitingResults.all { it.reviewStatus == ReviewStatus.AWAITING_APPROVAL }).isTrue()

        // Filter by APPROVED
        val approvedResults = executionRequestHelper.listExecutionRequestsFiltered(
            reviewStatuses = setOf(ReviewStatus.APPROVED),
            executionStatuses = null,
            connectionId = null,
            after = null,
            limit = 20,
        )

        assertThat(approvedResults.size).isEqualTo(5)
        assertThat(approvedResults.all { it.reviewStatus == ReviewStatus.APPROVED }).isTrue()
    }

    @Test
    fun `test filtering by executionStatus`() {
        val testUser = userHelper.createUser()
        val connection = connectionHelper.createDummyConnection()

        // Create requests with different execution statuses
        repeat(8) {
            executionRequestHelper.createExecutionRequest(
                author = testUser,
                description = "Executable",
                connection = connection,
                executionStatus = ExecutionStatus.EXECUTABLE,
            )
            Thread.sleep(10)
        }

        repeat(3) {
            executionRequestHelper.createExecutionRequest(
                author = testUser,
                description = "Executed",
                connection = connection,
                executionStatus = ExecutionStatus.EXECUTED,
            )
            Thread.sleep(10)
        }

        // Filter by EXECUTABLE
        val executableResults = executionRequestHelper.listExecutionRequestsFiltered(
            reviewStatuses = null,
            executionStatuses = setOf(ExecutionStatus.EXECUTABLE),
            connectionId = null,
            after = null,
            limit = 20,
        )

        assertThat(executableResults.size).isEqualTo(8)
        assertThat(executableResults.all { it.executionStatus == ExecutionStatus.EXECUTABLE }).isTrue()

        // Filter by EXECUTED
        val executedResults = executionRequestHelper.listExecutionRequestsFiltered(
            reviewStatuses = null,
            executionStatuses = setOf(ExecutionStatus.EXECUTED),
            connectionId = null,
            after = null,
            limit = 20,
        )

        assertThat(executedResults.size).isEqualTo(3)
        assertThat(executedResults.all { it.executionStatus == ExecutionStatus.EXECUTED }).isTrue()
    }

    @Test
    fun `test filtering by connectionId`() {
        val testUser = userHelper.createUser()
        val connection1 = connectionHelper.createDummyConnection()
        val connection2 = connectionHelper.createDummyConnection()

        // Create requests for connection1
        repeat(7) {
            executionRequestHelper.createExecutionRequest(
                author = testUser,
                description = "Connection 1",
                connection = connection1,
            )
            Thread.sleep(10)
        }

        // Create requests for connection2
        repeat(4) {
            executionRequestHelper.createExecutionRequest(
                author = testUser,
                description = "Connection 2",
                connection = connection2,
            )
            Thread.sleep(10)
        }

        // Filter by connection1
        val connection1Results = executionRequestHelper.listExecutionRequestsFiltered(
            reviewStatuses = null,
            executionStatuses = null,
            connectionId = connection1.id,
            after = null,
            limit = 20,
        )

        assertThat(connection1Results.size).isEqualTo(7)
        assertThat(connection1Results.all { it.connection.id == connection1.id.toString() }).isTrue()

        // Filter by connection2
        val connection2Results = executionRequestHelper.listExecutionRequestsFiltered(
            reviewStatuses = null,
            executionStatuses = null,
            connectionId = connection2.id,
            after = null,
            limit = 20,
        )

        assertThat(connection2Results.size).isEqualTo(4)
        assertThat(connection2Results.all { it.connection.id == connection2.id.toString() }).isTrue()
    }

    @Test
    fun `test combined filters`() {
        val testUser = userHelper.createUser()
        val connection1 = connectionHelper.createDummyConnection()
        val connection2 = connectionHelper.createDummyConnection()

        // Create various requests with different combinations
        repeat(5) {
            executionRequestHelper.createExecutionRequest(
                author = testUser,
                description = "C1 Awaiting Executable",
                connection = connection1,
                reviewStatus = ReviewStatus.AWAITING_APPROVAL,
                executionStatus = ExecutionStatus.EXECUTABLE,
            )
            Thread.sleep(10)
        }

        repeat(3) {
            executionRequestHelper.createExecutionRequest(
                author = testUser,
                description = "C1 Approved Executable",
                connection = connection1,
                reviewStatus = ReviewStatus.APPROVED,
                executionStatus = ExecutionStatus.EXECUTABLE,
            )
            Thread.sleep(10)
        }

        repeat(2) {
            executionRequestHelper.createExecutionRequest(
                author = testUser,
                description = "C2 Awaiting Executable",
                connection = connection2,
                reviewStatus = ReviewStatus.AWAITING_APPROVAL,
                executionStatus = ExecutionStatus.EXECUTABLE,
            )
            Thread.sleep(10)
        }

        // Filter: connection1 + AWAITING_APPROVAL + EXECUTABLE
        val results = executionRequestHelper.listExecutionRequestsFiltered(
            reviewStatuses = setOf(ReviewStatus.AWAITING_APPROVAL),
            executionStatuses = setOf(ExecutionStatus.EXECUTABLE),
            connectionId = connection1.id,
            after = null,
            limit = 20,
        )

        assertThat(results.size).isEqualTo(5)
        assertThat(results.all {
            it.connection.id == connection1.id.toString() &&
                it.reviewStatus == ReviewStatus.AWAITING_APPROVAL &&
                it.executionStatus == ExecutionStatus.EXECUTABLE
        }).isTrue()
    }

    @Test
    @Transactional
    fun `test results are ordered by createdAt descending`() {
        val testUser = userHelper.createUser()
        val connection = connectionHelper.createDummyConnection()

        repeat(10) {
            executionRequestHelper.createExecutionRequest(
                author = testUser,
                description = "Test request",
                connection = connection,
            )
            Thread.sleep(10)
        }

        val results = executionRequestHelper.listExecutionRequestsFiltered(
            reviewStatuses = null,
            executionStatuses = null,
            connectionId = null,
            after = null,
            limit = 20,
        )

        // Convert to DTOs to access createdAt
        val dtos = results.map {
            executionRequestHelper.toDetailDto(it)
        }

        // Verify results are in descending order (newest first)
        for (i in 0 until dtos.size - 1) {
            assertThat(dtos[i].request.createdAt.isAfter(dtos[i + 1].request.createdAt) ||
                dtos[i].request.createdAt.isEqual(dtos[i + 1].request.createdAt)).isTrue()
        }
    }
}
