package dev.kviklet.kviklet.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import java.net.URLDecoder

// The RDS token is a SigV4-presigned URL computed entirely offline, so with static credentials its wiring
// (host, port, user, region, service scope, lifetime) can be verified without any AWS account. Whether RDS
// accepts the result is only observable against a real instance, see the aws-integration tagged tests.
class AwsRdsIamTokenProviderTest {
    private val provider = AwsRdsIamTokenProvider(
        StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIATESTACCESSKEY", "test-secret")),
    )

    private fun queryParams(token: String): Map<String, String> = token.substringAfter("?")
        .split("&")
        .associate { param ->
            val (key, value) = param.split("=", limit = 2)
            key to URLDecoder.decode(value, Charsets.UTF_8)
        }

    @Test
    fun `token is bound to host, port and user and signed for the rds-db service in the endpoint's region`() {
        val token = provider.generateToken("mydb.abc123.eu-central-1.rds.amazonaws.com", 5432, "iamdbuser")

        assertTrue(token.startsWith("mydb.abc123.eu-central-1.rds.amazonaws.com:5432/?"), token)
        val params = queryParams(token)
        assertEquals("connect", params["Action"])
        assertEquals("iamdbuser", params["DBUser"])
        assertEquals("AWS4-HMAC-SHA256", params["X-Amz-Algorithm"])
        assertEquals("900", params["X-Amz-Expires"])
        val credential = params.getValue("X-Amz-Credential")
        assertTrue(credential.startsWith("AKIATESTACCESSKEY/"), credential)
        assertTrue(credential.endsWith("/eu-central-1/rds-db/aws4_request"), credential)
        assertTrue(params.getValue("X-Amz-Signature").matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `every call mints a fresh token for the requested user`() {
        val host = "mydb.abc123.us-east-1.rds.amazonaws.com"
        val alice = queryParams(provider.generateToken(host, 3306, "alice"))
        val bob = queryParams(provider.generateToken(host, 3306, "bob"))
        assertEquals("alice", alice["DBUser"])
        assertEquals("bob", bob["DBUser"])
        assertTrue(alice["X-Amz-Signature"] != bob["X-Amz-Signature"])
    }

    @Test
    fun `region is parsed from the RDS endpoint`() {
        assertEquals(Region.EU_CENTRAL_1, rdsRegionFromHost("mydb.abc123.eu-central-1.rds.amazonaws.com"))
        assertEquals(Region.US_WEST_2, rdsRegionFromHost("cluster.cluster-xyz.us-west-2.rds.amazonaws.com"))
    }

    @Test
    fun `a host that is not an RDS endpoint is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { rdsRegionFromHost("localhost") }
        assertThrows(IllegalArgumentException::class.java) { rdsRegionFromHost("db.example.com") }
        assertThrows(IllegalArgumentException::class.java) {
            provider.generateToken("db.example.com", 5432, "iamdbuser")
        }
    }
}
