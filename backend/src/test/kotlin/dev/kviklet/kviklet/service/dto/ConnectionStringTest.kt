package dev.kviklet.kviklet.service.dto

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ConnectionStringTest {

    private fun connection(type: DatasourceType, auth: AuthenticationDetails, additionalOptions: String = "") =
        DatasourceConnection(
            id = ConnectionId("test-connection"),
            displayName = "Test Connection",
            description = "",
            reviewConfig = ReviewConfig(numTotalRequired = 1),
            maxExecutions = null,
            databaseName = "testdb",
            authenticationType = when (auth) {
                is AuthenticationDetails.UserPassword -> AuthenticationType.USER_PASSWORD
                is AuthenticationDetails.AwsIam -> AuthenticationType.AWS_IAM
            },
            auth = auth,
            port = 3306,
            hostname = "db.example.com",
            type = type,
            protocol = type.toProtocol(),
            additionalOptions = additionalOptions,
            dumpsEnabled = false,
            temporaryAccessEnabled = true,
            explainEnabled = false,
            storeResults = false,
            dryRunEnabled = false,
            dryRunRequiresApproval = true,
        )

    private val iamAuth = AuthenticationDetails.AwsIam(username = "iamdbuser")

    @Test
    fun `postgres IAM connection string requires ssl`() {
        connection(DatasourceType.POSTGRESQL, iamAuth).getConnectionString() shouldBe
            "jdbc:postgresql://db.example.com:3306/testdb?sslmode=require"
    }

    @Test
    fun `mysql IAM connection string requires ssl`() {
        connection(DatasourceType.MYSQL, iamAuth).getConnectionString() shouldBe
            "jdbc:mysql://db.example.com:3306/testdb?sslMode=REQUIRED"
    }

    @Test
    fun `mariadb IAM connection string requires ssl`() {
        connection(DatasourceType.MARIADB, iamAuth).getConnectionString() shouldBe
            "jdbc:mariadb://db.example.com:3306/testdb?sslMode=trust"
    }

    @Test
    fun `mariadb IAM connection string appends ssl to additional options`() {
        connection(DatasourceType.MARIADB, iamAuth, additionalOptions = "?connectTimeout=5000")
            .getConnectionString() shouldBe
            "jdbc:mariadb://db.example.com:3306/testdb?connectTimeout=5000&sslMode=trust"
    }

    @Test
    fun `mssql IAM connection string is not supported`() {
        assertThrows<IllegalArgumentException> {
            connection(DatasourceType.MSSQL, iamAuth).getConnectionString()
        }
    }

    @Test
    fun `mariadb user password connection string`() {
        connection(
            DatasourceType.MARIADB,
            AuthenticationDetails.UserPassword(username = "root", password = "root"),
        ).getConnectionString() shouldBe "jdbc:mariadb://db.example.com:3306/testdb"
    }
}
