package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.ConnectionAdapter
import dev.kviklet.kviklet.db.ConnectionRepository
import dev.kviklet.kviklet.db.ConnectionType
import dev.kviklet.kviklet.db.EncryptionConfigProperties
import dev.kviklet.kviklet.db.ReviewConfig
import dev.kviklet.kviklet.service.dto.AuthenticationType
import dev.kviklet.kviklet.service.dto.ConnectionId
import dev.kviklet.kviklet.service.dto.DatabaseProtocol
import dev.kviklet.kviklet.service.dto.DatasourceConnection
import dev.kviklet.kviklet.service.dto.DatasourceType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.*

@SpringBootTest
@ActiveProfiles("test")
class ConnectionEncryptionTest(
    @Autowired val connectionRepository: ConnectionRepository,
    @Autowired val connectionAdapter: ConnectionAdapter,
    @Autowired val encryptionConfig: EncryptionConfigProperties,
) {

    companion object {
        private const val TEST_ENCRYPTION_KEY = "testEncryptionKey123"
        private const val NEW_ENCRYPTION_KEY = "newEncryptionKey456"
    }

    @BeforeEach
    fun setUp() {
        encryptionConfig.enabled = true
        encryptionConfig.key = EncryptionConfigProperties.KeyProperties(
            current = TEST_ENCRYPTION_KEY,
            previous = null,
        )
    }

    @AfterEach
    fun tearDown() {
        connectionRepository.deleteAll()
    }

    private fun createTestDatasourceConnection(
        connectionId: ConnectionId = ConnectionId(UUID.randomUUID().toString()),
        displayName: String,
        username: String,
        password: String,
        databaseName: String,
        port: Int = 5432,
        type: DatasourceType = DatasourceType.POSTGRESQL,
        protocol: DatabaseProtocol = DatabaseProtocol.POSTGRESQL,
    ): DatasourceConnection = connectionAdapter.createDatasourceConnection(
        connectionId = connectionId,
        displayName = displayName,
        authenticationType = AuthenticationType.USER_PASSWORD,
        databaseName = databaseName,
        maxExecutions = null,
        username = username,
        password = password,
        description = "Test description",
        reviewConfig = ReviewConfig(numTotalRequired = 1, fourEyesRequired = false),
        port = port,
        hostname = "localhost",
        type = type,
        protocol = protocol,
        additionalJDBCOptions = "",
    ) as DatasourceConnection

    @Test
    fun `test behavior when encryption is disabled`() {
        encryptionConfig.enabled = false

        val connection = createTestDatasourceConnection(
            displayName = "Unencrypted Connection",
            username = "unencrypteduser",
            password = "unencryptedpassword",
            databaseName = "unencrypteddb",
        )

        val storedConnection = connectionRepository.findById(connection.id.toString()).get()
        storedConnection.storedUsername shouldBe "unencrypteduser"
        storedConnection.storedPassword shouldBe "unencryptedpassword"
        storedConnection.isEncrypted shouldBe false

        val retrievedConnection = connectionAdapter.getConnection(connection.id) as DatasourceConnection
        retrievedConnection.username shouldBe "unencrypteduser"
        retrievedConnection.password shouldBe "unencryptedpassword"
    }

    @Test
    fun `test encryption and decryption with encryption enabled`() {
        val connection = createTestDatasourceConnection(
            displayName = "Test Connection",
            username = "testuser",
            password = "testpassword",
            databaseName = "testdb",
        )

        val storedConnection = connectionRepository.findById(connection.id.toString()).get()
        storedConnection.storedUsername shouldNotBe "testuser"
        storedConnection.storedPassword shouldNotBe "testpassword"
        storedConnection.isEncrypted shouldBe true

        val retrievedConnection = connectionAdapter.getConnection(connection.id) as DatasourceConnection
        retrievedConnection.username shouldBe "testuser"
        retrievedConnection.password shouldBe "testpassword"
    }

    @Test
    fun `test key rotation`() {
        val connection = createTestDatasourceConnection(
            displayName = "Rotation Test Connection",
            username = "rotationuser",
            password = "rotationpassword",
            databaseName = "rotationdb",
        )

        val initialStoredConnection = connectionRepository.findById(connection.id.toString()).get()
        val initialEncryptedUsername = initialStoredConnection.storedUsername
        val initialEncryptedPassword = initialStoredConnection.storedPassword

        encryptionConfig.key = EncryptionConfigProperties.KeyProperties(
            current = NEW_ENCRYPTION_KEY,
            previous = TEST_ENCRYPTION_KEY,
        )

        val retrievedConnection = connectionAdapter.getConnection(connection.id) as DatasourceConnection

        val updatedStoredConnection = connectionRepository.findById(connection.id.toString()).get()
        updatedStoredConnection.storedUsername shouldNotBe initialEncryptedUsername
        updatedStoredConnection.storedPassword shouldNotBe initialEncryptedPassword
        updatedStoredConnection.storedUsername shouldNotBe "rotationuser"
        updatedStoredConnection.storedPassword shouldNotBe "rotationpassword"
        updatedStoredConnection.isEncrypted shouldBe true

        retrievedConnection.username shouldBe "rotationuser"
        retrievedConnection.password shouldBe "rotationpassword"
    }

    @Test
    fun `test partial encryption when updating a single field`() {
        val connection = createTestDatasourceConnection(
            displayName = "Partial Update Connection",
            username = "initialuser",
            password = "initialpassword",
            databaseName = "partialdb",
        )

        val initialStoredConnection = connectionRepository.findById(connection.id.toString()).get()
        val initialEncryptedUsername = initialStoredConnection.username

        connectionAdapter.updateDatasourceConnection(
            id = connection.id,
            displayName = "Partial Update Connection",
            description = "Partial update test description",
            type = DatasourceType.POSTGRESQL,
            protocol = DatabaseProtocol.POSTGRESQL,
            maxExecutions = null,
            hostname = "localhost",
            port = 5432,
            username = "updateduser",
            password = "initialpassword",
            databaseName = "partialdb",
            reviewConfig = ReviewConfig(numTotalRequired = 1, fourEyesRequired = false) ,
            additionalJDBCOptions = "",
        )

        val updatedStoredConnection = connectionRepository.findById(connection.id.toString()).get()
        updatedStoredConnection.storedUsername shouldNotBe initialEncryptedUsername
        updatedStoredConnection.storedUsername shouldNotBe "updateduser"
        updatedStoredConnection.isEncrypted shouldBe true

        val retrievedConnection = connectionAdapter.getConnection(connection.id) as DatasourceConnection
        retrievedConnection.username shouldBe "updateduser"
        retrievedConnection.password shouldBe "initialpassword"
    }

    @Test
    fun `test listConnections decrypts all connections correctly`() {
        val connection1 = createTestDatasourceConnection(
            displayName = "Test Connection 1",
            username = "testuser1",
            password = "testpassword1",
            databaseName = "testdb1",
        )

        val connection2 = createTestDatasourceConnection(
            displayName = "Test Connection 2",
            username = "testuser2",
            password = "testpassword2",
            databaseName = "testdb2",
        )

        val storedConnection1 = connectionRepository.findById(connection1.id.toString()).get()
        val storedConnection2 = connectionRepository.findById(connection2.id.toString()).get()

        storedConnection1.storedUsername shouldNotBe "testuser1"
        storedConnection1.storedPassword shouldNotBe "testpassword1"
        storedConnection1.isEncrypted shouldBe true

        storedConnection2.storedUsername shouldNotBe "testuser2"
        storedConnection2.storedPassword shouldNotBe "testpassword2"
        storedConnection2.isEncrypted shouldBe true

        val listedConnections = connectionAdapter.listConnections()

        listedConnections.size shouldBe 2

        val listedConnection1 = listedConnections.find { it.id == connection1.id } as DatasourceConnection
        val listedConnection2 = listedConnections.find { it.id == connection2.id } as DatasourceConnection

        listedConnection1.username shouldBe "testuser1"
        listedConnection1.password shouldBe "testpassword1"

        listedConnection2.username shouldBe "testuser2"
        listedConnection2.password shouldBe "testpassword2"
    }

    @Test
    fun `test Kubernetes connection creation and retrieval with encryption enabled`() {
        val connectionId = ConnectionId(UUID.randomUUID().toString())
        val kubernetesConnection = connectionAdapter.createKubernetesConnection(
            connectionId = connectionId,
            displayName = "Test Kubernetes Connection",
            description = "Test Kubernetes description",
            reviewConfig = ReviewConfig(numTotalRequired = 2, fourEyesRequired = false),
            maxExecutions = 10,
        )

        kubernetesConnection.id shouldBe connectionId
        kubernetesConnection.displayName shouldBe "Test Kubernetes Connection"
        kubernetesConnection.description shouldBe "Test Kubernetes description"
        kubernetesConnection.reviewConfig.numTotalRequired shouldBe 2
        kubernetesConnection.maxExecutions shouldBe 10

        val retrievedConnection = connectionAdapter.getConnection(connectionId)

        retrievedConnection.id shouldBe connectionId
        retrievedConnection.displayName shouldBe "Test Kubernetes Connection"
        retrievedConnection.description shouldBe "Test Kubernetes description"
        retrievedConnection.reviewConfig.numTotalRequired shouldBe 2
        retrievedConnection.maxExecutions shouldBe 10

        val storedConnection = connectionRepository.findById(connectionId.toString()).get()
        storedConnection.connectionType shouldBe ConnectionType.KUBERNETES
        storedConnection.isEncrypted shouldBe true
        storedConnection.storedUsername shouldBe null
        storedConnection.storedPassword shouldBe null
    }

    @Test
    fun `test enabling encryption for existing unencrypted connection`() {
        // Disable encryption initially
        encryptionConfig.enabled = false

        // Create an unencrypted connection
        val connection = createTestDatasourceConnection(
            displayName = "Initially Unencrypted Connection",
            username = "unencrypteduser",
            password = "unencryptedpassword",
            databaseName = "unencrypteddb",
        )

        // Verify the connection is stored unencrypted
        val initialStoredConnection = connectionRepository.findById(connection.id.toString()).get()
        initialStoredConnection.storedUsername shouldBe "unencrypteduser"
        initialStoredConnection.storedPassword shouldBe "unencryptedpassword"
        initialStoredConnection.isEncrypted shouldBe false

        // Enable encryption
        encryptionConfig.enabled = true
        encryptionConfig.key = EncryptionConfigProperties.KeyProperties(
            current = TEST_ENCRYPTION_KEY,
            previous = null,
        )

        // Retrieve the connection, which should trigger encryption
        val retrievedConnection = connectionAdapter.getConnection(connection.id) as DatasourceConnection

        // Verify the retrieved connection has decrypted credentials
        retrievedConnection.username shouldBe "unencrypteduser"
        retrievedConnection.password shouldBe "unencryptedpassword"

        // Verify the stored connection is now encrypted
        val updatedStoredConnection = connectionRepository.findById(connection.id.toString()).get()
        updatedStoredConnection.storedUsername shouldNotBe "unencrypteduser"
        updatedStoredConnection.storedPassword shouldNotBe "unencryptedpassword"
        updatedStoredConnection.isEncrypted shouldBe true

        // Retrieve the connection again to ensure it can be decrypted correctly
        val secondRetrievedConnection = connectionAdapter.getConnection(connection.id) as DatasourceConnection
        secondRetrievedConnection.username shouldBe "unencrypteduser"
        secondRetrievedConnection.password shouldBe "unencryptedpassword"
    }
}
