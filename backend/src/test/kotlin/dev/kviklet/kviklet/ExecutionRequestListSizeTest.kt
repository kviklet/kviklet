package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.UserHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles

/** Postgres allows at most 65,535 parameters per statement, so no list query may bind one per request. */
@SpringBootTest
@ActiveProfiles("test")
class ExecutionRequestListSizeTest {

    @Autowired private lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @Autowired private lateinit var executionRequestHelper: ExecutionRequestHelper

    @Autowired private lateinit var connectionHelper: ConnectionHelper

    @Autowired private lateinit var userHelper: UserHelper

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private val count = 70_000

    @AfterEach
    fun tearDown() {
        jdbcTemplate.update("DELETE FROM event")
        jdbcTemplate.update("DELETE FROM execution_request")
        connectionHelper.deleteAll()
        userHelper.deleteAll()
    }

    @Test
    fun `listing more requests than Postgres allows parameters works, with and without a limit`() {
        val connection = connectionHelper.createDummyConnection()
        val request = executionRequestHelper.createExecutionRequest(
            author = userHelper.createUser(),
            connection = connection,
        )
        copyRequest(request.request.id.toString())

        val all = executionRequestAdapter.listExecutionRequestsFiltered(connectionIds = setOf(connection.id))
        assertThat(all).hasSize(count + 1)
        assertThat(all.first().request.id).isEqualTo(request.request.id)

        val page = executionRequestAdapter.listExecutionRequestsFiltered(
            connectionIds = setOf(connection.id),
            limit = count,
        )
        assertThat(page).hasSize(count)
        assertThat(page.map { it.request.createdAt }).isSortedAccordingTo(reverseOrder())
    }

    /** Inserts copies of the request, each a second older than the one before. */
    private fun copyRequest(id: String) {
        val columns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns WHERE table_name = 'execution_request'",
            String::class.java,
        )
        val values = columns.map {
            when (it) {
                "id" -> "'copy-' || g"
                "created_at" -> "created_at - g * interval '1 second'"
                else -> "\"$it\""
            }
        }
        jdbcTemplate.update(
            "INSERT INTO execution_request (${columns.joinToString { "\"$it\"" }}) " +
                "SELECT ${values.joinToString()} FROM execution_request, generate_series(1, $count) g WHERE id = ?",
            id,
        )
    }
}
