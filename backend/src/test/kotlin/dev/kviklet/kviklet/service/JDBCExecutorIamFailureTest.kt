package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.ErrorQueryResult
import dev.kviklet.kviklet.service.dto.ExecutionRequestId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sts.model.StsException

// An IAM token that cannot be minted fails inside Hikari's connection attempt, before any network traffic,
// so the executor's handling of it can be verified without a database or an AWS account.
class JDBCExecutorIamFailureTest {
    private class FailingTokenProvider(private val failure: RuntimeException) : RdsIamTokenProvider {
        override fun generateToken(hostname: String, port: Int, username: String, roleArn: String?): String =
            throw failure
    }

    private val stsMessage = "Roles may not be assumed by root accounts (Service: Sts, Status Code: 403)"
    private val executor = JDBCExecutor(FailingTokenProvider(StsException.builder().message(stsMessage).build()))
    private val rdsUrl = "jdbc:mysql://mydb.abc123.eu-central-1.rds.amazonaws.com:3306/testdb"
    private val auth = AuthenticationDetails.AwsIam(
        username = "iamdbuser",
        roleArn = "arn:aws:iam::123456789012:role/kviklet-rds",
    )

    @Test
    fun `a connection test reports a token failure as a failed test carrying the STS message`() {
        val result = executor.testCredentials(rdsUrl, auth)

        assertFalse(result.success)
        assertTrue(result.message.contains("Could not obtain an RDS IAM authentication token"), result.message)
        assertTrue(result.message.contains(stsMessage), result.message)
    }

    @Test
    fun `an execution reports a token failure as an error result instead of throwing`() {
        val results = executor.execute(ExecutionRequestId("5Wb9WJxCxej5W1Rt6cTBV5"), rdsUrl, auth, "SELECT 1")

        assertEquals(1, results.size)
        val error = results.single() as ErrorQueryResult
        assertTrue(error.message.contains(stsMessage), error.message)
    }

    @Test
    fun `a dry run reports a token failure as an error result instead of throwing`() {
        val results = executor.executeDryRun(ExecutionRequestId("5Wb9WJxCxej5W1Rt6cTBV5"), rdsUrl, auth, "SELECT 1")

        val error = results.single() as ErrorQueryResult
        assertTrue(error.message.contains(stsMessage), error.message)
    }

    @Test
    fun `a connection test against a host that is not an RDS endpoint is a failed test, not an error`() {
        val result = executor.testCredentials("jdbc:mysql://db.internal:3306/testdb", auth)

        assertFalse(result.success)
        assertTrue(result.message.contains("Invalid RDS endpoint format"), result.message)
    }
}
