// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.proxy.core.ProxyServer
import dev.kviklet.kviklet.proxy.helpers.MySqlProxyInstance
import dev.kviklet.kviklet.proxy.helpers.mysqlProxyServerFactory
import dev.kviklet.kviklet.service.dto.DatasourceType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties

@SpringBootTest
@ActiveProfiles("test")
class MySqlProxyAuthTest {
    @Autowired
    lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @Autowired
    lateinit var eventAdapter: EventAdapter
    private val startedProxies = mutableListOf<ProxyServer>()

    companion object {
        private val mysqlContainer: MySQLContainer<*> = MySQLContainer(DockerImageName.parse("mysql:8.2")).apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }
        private val mariadbContainer: MariaDBContainer<*> = MariaDBContainer(DockerImageName.parse("mariadb:11.4"))
            .apply {
                withDatabaseName("testdb")
                withUsername("test")
                withPassword("test")
            }

        @JvmStatic
        @BeforeAll
        fun startContainers() {
            Startables.deepStart(listOf(mysqlContainer, mariadbContainer)).join()
        }

        @JvmStatic
        @AfterAll
        fun stopContainers() {
            mysqlContainer.stop()
            mariadbContainer.stop()
        }

        @JvmStatic
        fun datasourceTypes() = listOf(DatasourceType.MYSQL, DatasourceType.MARIADB)

        private fun container(type: DatasourceType): JdbcDatabaseContainer<*> = when (type) {
            DatasourceType.MYSQL -> mysqlContainer
            DatasourceType.MARIADB -> mariadbContainer
            else -> throw IllegalArgumentException("$type is not served by the MySQL proxy")
        }
    }

    @AfterEach
    fun tearDown() {
        startedProxies.forEach { it.shutdownServer() }
        startedProxies.clear()
    }

    private fun startProxy(
        type: DatasourceType,
        username: String = "proxyUser",
        password: String = "proxyPassword",
    ): MySqlProxyInstance {
        val instance = mysqlProxyServerFactory(
            container(type),
            type,
            executionRequestAdapter,
            eventAdapter,
            username = username,
            password = password,
        )
        startedProxies.add(instance.proxy)
        return instance
    }

    private fun connectWith(proxy: MySqlProxyInstance, user: String, password: String?): SQLException =
        assertThrows<SQLException> {
            val props = Properties()
            props.setProperty("user", user)
            password?.let { props.setProperty("password", it) }
            DriverManager.getConnection(proxy.connectionString, props)
        }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `MySQL proxy must require password-based authentication`(type: DatasourceType) {
        val proxy = startProxy(type)
        val throwable = connectWith(proxy, proxy.username, null)
        assertEquals(1045, throwable.errorCode)
        assertEquals("28000", throwable.sqlState)
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `MySQL proxy must use externally injected username and password`(type: DatasourceType) {
        val randomUsername = getRandomString(8)
        val randomPassword = getRandomString(16)
        val proxy = startProxy(type, username = randomUsername, password = randomPassword)
        assertDoesNotThrow {
            proxy.connect(randomUsername, randomPassword).use { conn ->
                conn.createStatement().executeQuery("SELECT 1").close()
            }
        }
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `MySQL proxy must reject bad username and password`(type: DatasourceType) {
        val proxy = startProxy(type, username = "notuser", password = "notpass")
        val throwable = connectWith(proxy, getRandomString(8), getRandomString(16))
        assertEquals(1045, throwable.errorCode)
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `the correct username with a wrong password fails with 1045, not a connection reset`(type: DatasourceType) {
        val username = getRandomString(8)
        val proxy = startProxy(type, username = username, password = getRandomString(16))
        val throwable = connectWith(proxy, username, "definitely-the-wrong-password")
        assertEquals(1045, throwable.errorCode)
        assertEquals("28000", throwable.sqlState)
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `a wrong username with the correct password is rejected`(type: DatasourceType) {
        val password = getRandomString(16)
        val proxy = startProxy(type, username = getRandomString(8), password = password)
        val throwable = connectWith(proxy, "wronguser", password)
        assertEquals(1045, throwable.errorCode)
    }

    @ParameterizedTest
    @MethodSource("datasourceTypes")
    fun `an unknown username is indistinguishable from a wrong password`(type: DatasourceType) {
        val validUsername = getRandomString(8)
        val proxy = startProxy(type, username = validUsername, password = getRandomString(16))

        val unknownUsername = getRandomString(8)
        val unknownUserFailure = connectWith(proxy, unknownUsername, "some-password")
        val wrongPasswordFailure = connectWith(proxy, validUsername, "some-password")

        // Same error code, SQL state and message shape: a probe cannot tell an unknown user from a wrong
        // password, so valid temp usernames cannot be enumerated.
        assertEquals(wrongPasswordFailure.errorCode, unknownUserFailure.errorCode)
        assertEquals(wrongPasswordFailure.sqlState, unknownUserFailure.sqlState)
        assertEquals(
            normalizeAccessDeniedMessage(wrongPasswordFailure.message!!, validUsername),
            normalizeAccessDeniedMessage(unknownUserFailure.message!!, unknownUsername),
        )
    }

    // Strips the parts of an access-denied message that legitimately differ per attempt: the echoed
    // username and the driver's connection id prefix (the MariaDB driver prepends "(conn=N)").
    private fun normalizeAccessDeniedMessage(message: String, username: String): String = message
        .replace(username, "<user>")
        .replace(Regex("\\(conn=-?\\d+\\)"), "")
        .trim()
}
