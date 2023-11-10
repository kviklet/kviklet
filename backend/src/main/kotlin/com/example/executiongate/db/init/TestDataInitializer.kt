package com.example.executiongate.db.init

import com.example.executiongate.db.DatasourceConnectionEntity
import com.example.executiongate.db.DatasourceConnectionRepository
import com.example.executiongate.db.DatasourceEntity
import com.example.executiongate.db.DatasourceRepository
import com.example.executiongate.db.ExecutionRequestEntity
import com.example.executiongate.db.ExecutionRequestRepository
import com.example.executiongate.db.PolicyEntity
import com.example.executiongate.db.PolicyRepository
import com.example.executiongate.db.ReviewConfig
import com.example.executiongate.db.RoleEntity
import com.example.executiongate.db.RoleRepository
import com.example.executiongate.db.UserEntity
import com.example.executiongate.db.UserRepository
import com.example.executiongate.service.dto.AuthenticationType
import com.example.executiongate.service.dto.DatasourceType
import com.example.executiongate.service.dto.PolicyEffect
import com.example.executiongate.service.dto.RequestType
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.concurrent.ThreadLocalRandom

@Configuration
@Profile("local", "e2e")
class TestDataInitializer(
    private val datasourceRepository: DatasourceRepository,
    private val datasourceConnectionRepository: DatasourceConnectionRepository,
    private val executionRequestRepository: ExecutionRequestRepository,
    private val roleRepository: RoleRepository,
    private val policyRepository: PolicyRepository,
) {

    // Helper function to generate an ExecutionRequestEntity
    fun generateExecutionRequest(
        connection: DatasourceConnectionEntity,
        titlePrefix: String,
        index: Int,
        author: UserEntity,
    ): ExecutionRequestEntity {
        val title = "$titlePrefix Execution Request $index"
        val description = "Description of the $titlePrefix execution request $index"
        val statement = "Select * from test;"
        val readOnly = ThreadLocalRandom.current().nextBoolean()
        val executionStatus = if (ThreadLocalRandom.current().nextBoolean()) "SUCCESS" else "PENDING"
        return ExecutionRequestEntity(
            connection = connection,
            title = title,
            type = RequestType.SingleQuery,
            description = description,
            statement = statement,
            readOnly = readOnly,
            executionStatus = executionStatus,
            author = author,
            events = mutableSetOf(),
        )
    }

    fun generateRole(savedUser: UserEntity) {
        val role = RoleEntity(
            id = "r1",
            description = "This is a test role",
            policies = emptySet(),
        )
        val savedRole = roleRepository.saveAndFlush(role)
        val policyEntity = PolicyEntity(
            role = savedRole,
            action = "*",
            effect = PolicyEffect.ALLOW,
            resource = "*",
        )
        policyRepository.saveAndFlush(policyEntity)

        savedUser.roles += savedRole
    }

    @Bean
    fun initializer(userRepository: UserRepository, passwordEncoder: PasswordEncoder): ApplicationRunner {
        return ApplicationRunner { _ ->

            if (userRepository.findAll().isNotEmpty()) {
                return@ApplicationRunner
            }

            val user = UserEntity(
                email = "nils@opsgate.dev",
                fullName = "Admin User",
                password = passwordEncoder.encode("testPassword"),
            )

            val savedUser = userRepository.saveAndFlush(user)

            // Create a datasource
            val datasource1 = DatasourceEntity(
                id = "test-datasource",
                displayName = "Test Datasource",
                type = DatasourceType.POSTGRESQL,
                hostname = "localhost",
                port = 5432,
                datasourceConnections = emptySet(),
            )

            // Save the datasource
            val savedDatasource1 = datasourceRepository.saveAndFlush(datasource1)

            // Create connections linked to the saved datasource
            val connection1 = DatasourceConnectionEntity(
                id = "test-connection",
                datasource = savedDatasource1,
                displayName = "Test Connection",
                databaseName = null,
                authenticationType = AuthenticationType.USER_PASSWORD,
                description = "This is a localhost connection",
                username = "postgres",
                password = "postgres",
                reviewConfig = ReviewConfig(numTotalRequired = 1),
            )

            // Save the connection
            val savedConnection1 = datasourceConnectionRepository.saveAndFlush(connection1)

            val request1 = generateExecutionRequest(savedConnection1, "First", 1, savedUser)

            executionRequestRepository.save(request1)

            generateRole(savedUser)
            userRepository.saveAndFlush(savedUser)
        }
    }
}
