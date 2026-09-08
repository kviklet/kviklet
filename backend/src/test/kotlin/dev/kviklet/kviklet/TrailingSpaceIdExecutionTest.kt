package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.ConnectionRepository
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.db.ExecutionRequestEntity
import dev.kviklet.kviklet.db.ExecutionRequestRepository
import dev.kviklet.kviklet.db.ExecutionRequestType
import dev.kviklet.kviklet.db.ReviewPayload
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserRepository
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.Connection
import dev.kviklet.kviklet.service.dto.ExecuteEvent
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import dev.kviklet.kviklet.service.dto.ExecutionStatus
import dev.kviklet.kviklet.service.dto.RequestType
import dev.kviklet.kviklet.service.dto.ReviewAction
import dev.kviklet.kviklet.service.dto.ReviewStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * IdGenerator pads 21-character ids to 22 with a trailing space, and browsers drop that space from
 * the URL. Because the id columns are CHAR(22), Postgres still matches the row for the trimmed id,
 * but Hibernate then holds two managed instances of the same request within one persistence context
 * (keyed by the trimmed and by the padded id). Only one of them gets the new execute event added to
 * its events collection, so the request must not be resolved through that collection.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// Production keeps the persistence context open for the whole request; the test profile turns
// that off, and the stale collection only shows up with it on.
@TestPropertySource(properties = ["spring.jpa.open-in-view=true"])
class TrailingSpaceIdExecutionTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userHelper: UserHelper

    @Autowired private lateinit var roleHelper: RoleHelper

    @Autowired private lateinit var connectionHelper: ConnectionHelper

    @Autowired private lateinit var executionRequestHelper: ExecutionRequestHelper

    @Autowired private lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @Autowired private lateinit var executionRequestRepository: ExecutionRequestRepository

    @Autowired private lateinit var connectionRepository: ConnectionRepository

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var testUser: User
    private lateinit var testReviewer: User
    private lateinit var testConnection: Connection

    companion object {
        val db: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:11.1"))
            .withUsername("root")
            .withPassword("root")
            .withReuse(true)
            .withDatabaseName("test_db")

        init {
            db.start()
        }
    }

    @BeforeEach
    fun setup() {
        testUser = userHelper.createUser(permissions = listOf("*"))
        testReviewer = userHelper.createUser(permissions = listOf("*"))
        testConnection = connectionHelper.createPostgresConnection(db)
    }

    @AfterEach
    fun tearDown() {
        executionRequestHelper.deleteAll()
        connectionHelper.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
    }

    private val paddedId = "Huiyz7BRyvAJpyALHFXTZ "
    private val trimmedId = paddedId.trim()

    private fun createApprovedRequestWithPaddedId() {
        TransactionTemplate(transactionManager).execute {
            val connection = connectionRepository.findByIdOrNull(testConnection.id.toString())!!
            val author = userRepository.findByIdOrNull(testUser.getId()!!)!!
            val entity = ExecutionRequestEntity(
                connection = connection,
                title = "Padded id",
                executionType = RequestType.SingleExecution,
                description = "",
                statement = "SELECT 1;",
                executionStatus = ExecutionStatus.EXECUTABLE,
                reviewStatus = ReviewStatus.AWAITING_APPROVAL,
                events = mutableSetOf(),
                author = author,
                executionRequestType = ExecutionRequestType.DATASOURCE,
            )
            // IdGenerator keeps an assigned 22-character id
            entity.id = paddedId
            executionRequestRepository.save(entity)
        }
        executionRequestAdapter.addEvent(
            ExecutionRequestId(paddedId),
            testReviewer.getId()!!,
            ReviewPayload(action = ReviewAction.APPROVE, comment = "lgtm"),
        )
    }

    @Test
    fun `executing through the trimmed id stores the results`() {
        createApprovedRequestWithPaddedId()
        val cookie = userHelper.login(email = testUser.email, mockMvc = mockMvc)

        mockMvc.perform(
            post("/execution-requests/$trimmedId/execute")
                .cookie(cookie)
                .contentType("application/json"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.results[0].columns[0].label").value("?column?"))

        val details = executionRequestAdapter.getExecutionRequestDetails(ExecutionRequestId(trimmedId))
        val execution = details.events.filterIsInstance<ExecuteEvent>().single()
        assertEquals(1, execution.results.size)
        assertEquals(ExecutionStatus.EXECUTED, details.resolveExecutionStatus())
    }
}
