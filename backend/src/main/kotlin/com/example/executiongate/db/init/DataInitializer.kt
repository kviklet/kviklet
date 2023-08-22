package com.example.executiongate.db.init

import com.example.executiongate.db.*
import com.example.executiongate.service.dto.AuthenticationType
import com.example.executiongate.service.dto.DatasourceType
import com.example.executiongate.service.dto.Policy
import com.example.executiongate.service.dto.ReviewStatus
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.concurrent.ThreadLocalRandom

@Configuration
class DataInitializer(
        private val datasourceRepository: DatasourceRepository,
        private val datasourceConnectionRepository: DatasourceConnectionRepository,
        private val executionRequestRepository: ExecutionRequestRepository,
        private val roleRepository: RoleRepository,
        private val policyRepository: PolicyRepository
) {


    // Helper function to generate a random SQL statement
    fun randomSQL(): String {
        val tables = listOf("users", "orders", "products")
        val table = tables[ThreadLocalRandom.current().nextInt(tables.size)]
        // return "SELECT * FROM $table;"
        return "SELECT 1;"
    }

    // Helper function to generate an ExecutionRequestEntity
    fun generateExecutionRequest(
            connection: DatasourceConnectionEntity,
            titlePrefix: String,
            index: Int,
            author: UserEntity
    ): ExecutionRequestEntity {
        val title = "$titlePrefix Execution Request $index"
        val description = "Description of the $titlePrefix execution request $index"
        val statement = "Select * from test;"
        val readOnly = ThreadLocalRandom.current().nextBoolean()
        val reviewStatus = if (ThreadLocalRandom.current().nextBoolean()) ReviewStatus.APPROVED else ReviewStatus.AWAITING_APPROVAL
        val executionStatus = if (ThreadLocalRandom.current().nextBoolean()) "SUCCESS" else "PENDING"
        return ExecutionRequestEntity(
                connection = connection,
                title = title,
                description = description,
                statement = statement,
                readOnly = readOnly,
                reviewStatus = reviewStatus,
                executionStatus = executionStatus,
                author = author,
                events = mutableSetOf()
        )
    }

    fun generateDatasourceEntity(
            displayName: String,
            type: DatasourceType,
            hostname: String,
            port: Int,
            numConnections: Int
    ): DatasourceEntity {
        val datasource = DatasourceEntity(
                displayName = displayName,
                type = type,
                hostname = hostname,
                port = port,
                datasourceConnections = emptySet()
        )
        val savedDatasource = datasourceRepository.saveAndFlush(datasource)
        val connections = mutableSetOf<DatasourceConnectionEntity>()
        for (i in 1..numConnections) {
            val connection = DatasourceConnectionEntity(
                    displayName = "Connection $i",
                    authenticationType = AuthenticationType.USER_PASSWORD,
                    username = "test",
                    description = "This is connection $i it is used for some purpose or another" +
                            "and is very important to the business",
                    password = "test",
                    reviewConfig = ReviewConfig(numTotalRequired = 1),
                    datasource = savedDatasource
            )
            connections.add(connection)
        }
        datasourceConnectionRepository.saveAll(connections)
        return datasource
    }

    fun generateRole() {
        val role = RoleEntity(
                name = "Test Role",
                description = "This is a test role",
                policies = emptySet()
        )
        val savedRole = roleRepository.saveAndFlush(role)
        val policies = emptySet<PolicyEntity>()
        savedRole.policies = policies
        roleRepository.saveAndFlush(savedRole)
    }

    @Bean
    fun initializer(userRepository: UserRepository, passwordEncoder: PasswordEncoder): ApplicationRunner {
        return ApplicationRunner { _ ->

            if (userRepository.findAll().isNotEmpty()) {
                return@ApplicationRunner
            }

            val user = UserEntity(
                    email = "testUser@example.com",
                    fullName = "Admin User",
                    password = passwordEncoder.encode("testPassword")
            )

            val savedUser = userRepository.saveAndFlush(user)

            // Create a datasource
            val datasource1 = DatasourceEntity(
                    displayName = "Test Datasource",
                    type = DatasourceType.POSTGRESQL,
                    hostname = "localhost",
                    port = 5432,
                    datasourceConnections = emptySet()
            )

            // Save the datasource
            val savedDatasource1 = datasourceRepository.saveAndFlush(datasource1)

            // Create connections linked to the saved datasource
            val connection1 = DatasourceConnectionEntity(
                    datasource = savedDatasource1,
                    displayName = "Test Connection",
                    authenticationType = AuthenticationType.USER_PASSWORD,
                    description = "This is a localhost connection",
                    username = "postgres",
                    password = "postgres",
                    reviewConfig = ReviewConfig(numTotalRequired = 1)
            )

            // Save the connection
            val savedConnection1 = datasourceConnectionRepository.saveAndFlush(connection1)

            val request1 = generateExecutionRequest(savedConnection1, "First", 1, savedUser)

            executionRequestRepository.save(request1)
            generateRole()

        }
    }
}
