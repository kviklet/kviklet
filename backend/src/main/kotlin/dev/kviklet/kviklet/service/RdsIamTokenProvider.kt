package dev.kviklet.kviklet.service

import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.rds.RdsUtilities
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

// Mints the short-lived password an RDS IAM connection presents. Every place that dials an IAM connection
// (the JDBC executor's pool, each proxied upstream connection) goes through this, so the AWS-specific part
// is one implementation that tests can swap for a fake.
interface RdsIamTokenProvider {
    // A token is valid for 15 minutes from generation and is bound to exactly this host, port and user.
    fun generateToken(hostname: String, port: Int, username: String, roleArn: String? = null): String
}

@Component
class AwsRdsIamTokenProvider(
    private val baseCredentialsProvider: AwsCredentialsProvider = DefaultCredentialsProvider.create(),
) : RdsIamTokenProvider {
    private val logger = LoggerFactory.getLogger(javaClass)

    // One signer per (region, role): an assumed-role provider refreshes its STS credentials in the
    // background, so it must be shared across connections instead of rebuilt (and re-assumed) per token.
    private val utilities = ConcurrentHashMap<Pair<Region, String?>, RdsUtilities>()

    override fun generateToken(hostname: String, port: Int, username: String, roleArn: String?): String {
        val region = rdsRegionFromHost(hostname)
        val rdsUtilities = utilities.computeIfAbsent(Pair(region, roleArn?.ifEmpty { null })) { (r, role) ->
            RdsUtilities.builder()
                .region(r)
                .credentialsProvider(credentialsProviderFor(r, role))
                .build()
        }
        return rdsUtilities.generateAuthenticationToken { builder ->
            builder.hostname(hostname)
                .port(port)
                .username(username)
        }
    }

    private fun credentialsProviderFor(region: Region, roleArn: String?): AwsCredentialsProvider {
        if (roleArn == null) {
            logger.info("Using default credentials for authentication")
            return baseCredentialsProvider
        }
        logger.info("Using IAM role {} for authentication", roleArn)
        return StsAssumeRoleCredentialsProvider.builder()
            .asyncCredentialUpdateEnabled(true)
            .stsClient(StsClient.builder().region(region).credentialsProvider(baseCredentialsProvider).build())
            .refreshRequest { r ->
                r.roleArn(roleArn).roleSessionName("KvikletRdsIamSession").build()
            }
            .build()
    }
}

// The AWS region encoded in an RDS endpoint: <db-instance>.<account-hash>.<region>.rds.amazonaws.com
fun rdsRegionFromHost(host: String): Region {
    val parts = host.split(".")
    if (parts.size < 5 ||
        parts[parts.size - 3] != "rds" ||
        parts[parts.size - 2] != "amazonaws" ||
        parts[parts.size - 1] != "com"
    ) {
        throw IllegalArgumentException(
            "Invalid RDS endpoint format. Expected: <db-instance>.<region>.rds.amazonaws.com",
        )
    }

    val regionString = parts[parts.size - 4]
    return try {
        Region.of(regionString)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid AWS region: $regionString", e)
    }
}

// A Hikari pool whose password is a freshly minted RDS IAM token on every physical connect: Hikari calls
// getPassword() each time it opens a connection. RDS checks the token only then, so an established
// connection outliving its 15-minute token is fine and no special pool lifetime is needed.
class AwsIamDataSource(
    private val tokenProvider: RdsIamTokenProvider,
    private val username: String,
    private val roleArn: String? = null,
) : HikariDataSource() {
    private val uri: URI by lazy { URI.create(jdbcUrl.removePrefix("jdbc:")) }

    override fun getPassword(): String = tokenProvider.generateToken(uri.host, uri.port, username, roleArn)
}
