package dev.kviklet.kviklet.database

import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.UserHelper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class ExecutionRequestAdapterTest {

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

    @Test
    fun `test saving request with long description`() {
        val testUser = userHelper.createUser()
        val connection = connectionHelper.createDummyConnection()
        executionRequestHelper.createExecutionRequest(
            author = testUser,
            description = "A test request with a long description that is longer than 255 characters. " +
                "This is a test to see if the database can handle long descriptions." +
                "This is a test to see if the database can handle long descriptions." +
                "This is a test to see if the database can handle long descriptions." +
                "This is a test to see if the database can handle long descriptions." +
                "This is a test to see if the database can handle long descriptions.",
            connection = connection,
        )
    }
}
