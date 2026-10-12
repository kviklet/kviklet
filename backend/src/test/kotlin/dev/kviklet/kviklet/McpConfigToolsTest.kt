package dev.kviklet.kviklet

import com.ninjasquad.springmockk.MockkBean
import dev.kviklet.kviklet.db.LicenseAdapter
import dev.kviklet.kviklet.db.RoleSyncConfigAdapter
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.helper.ConnectionHelper
import dev.kviklet.kviklet.helper.McpClientHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.ToolCallResult
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.LicenseFile
import dev.kviklet.kviklet.service.dto.Policy
import dev.kviklet.kviklet.service.dto.Role
import dev.kviklet.kviklet.service.dto.RoleSyncConfig
import dev.kviklet.kviklet.shell.KubernetesApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime

/** The configuration tools, called the way an agent calls them: through /mcp with an OAuth access token. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class McpConfigToolsTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var mcp: McpClientHelper

    @Autowired private lateinit var userHelper: UserHelper

    @Autowired private lateinit var roleHelper: RoleHelper

    @Autowired private lateinit var connectionHelper: ConnectionHelper

    @Autowired private lateinit var licenseAdapter: LicenseAdapter

    @Autowired private lateinit var roleSyncConfigAdapter: RoleSyncConfigAdapter

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    // Declared exactly as in McpToolsTest, so both share one application context.
    @MockkBean
    private lateinit var kubernetesApi: KubernetesApi

    // The test license allows two users, so each test has one seat left after the admin's.
    private lateinit var admin: User
    private lateinit var roleSync: RoleSyncConfig

    @BeforeEach
    fun setUp() {
        admin = userHelper.createUser(permissions = listOf("*"))
        installLicense()
        roleSync = roleSyncConfigAdapter.getConfig()
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.update("DELETE FROM oauth2_authorization")
        jdbcTemplate.update("DELETE FROM oauth2_registered_client")
        jdbcTemplate.update("DELETE FROM api_keys")
        jdbcTemplate.update("DELETE FROM role_sync_mapping")
        jdbcTemplate.update("DELETE FROM configuration WHERE key IN ('slackUrl', 'teamsUrl', 'proxyEnabled')")
        roleSyncConfigAdapter.updateConfig(roleSync.enabled, roleSync.syncMode, roleSync.groupsAttribute)
        connectionHelper.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
        licenseAdapter.deleteAll()
    }

    @Test
    fun `tools are listed with hints that tell clients which ones to confirm`() {
        val tools = mcp.listTools(mockMvc, token(admin)).associateBy { it["name"].asString() }
        fun hint(tool: String, name: String) = tools.getValue(tool)["annotations"][name].asBoolean()

        listOf("get_connection", "list_roles", "list_users", "get_role_sync", "list_api_keys", "get_config").forEach {
            assertThat(hint(it, "readOnlyHint")).describedAs(it).isTrue()
        }
        listOf("create_database_connection", "create_role", "create_user", "create_api_key").forEach {
            assertThat(hint(it, "destructiveHint")).describedAs(it).isFalse()
        }
        listOf(
            "update_database_connection",
            "update_role",
            "delete_role",
            "set_user_roles",
            "set_user_active",
            "update_role_sync",
            "delete_api_key",
            "update_config",
        ).forEach { assertThat(hint(it, "destructiveHint")).describedAs(it).isTrue() }
    }

    @Test
    fun `an agent creates a database connection without a password`() {
        val created = call(
            admin,
            "create_database_connection",
            mapOf(
                "id" to "orders-prod",
                "displayName" to "Orders",
                "engine" to "POSTGRESQL",
                "hostname" to "db.internal",
                "port" to 5432,
                "username" to "kviklet",
                "requiredReviews" to 2,
                "category" to "Production",
            ),
        ).json!!

        assertThat(created["protocol"].asString()).isEqualTo("POSTGRESQL")
        assertThat(created["hasPassword"].asBoolean()).isFalse()
        assertThat(created["requiredReviews"].asInt()).isEqualTo(2)
        assertThat(created["maxExecutions"].asInt()).isEqualTo(1)
        assertThat(created["canEditConnection"].asBoolean()).isTrue()
        assertThat(created["url"].asString()).endsWith("/settings/connections/orders-prod")
        assertThat(created.has("password")).isFalse()

        val listed = call(admin, "list_connections").json!!.single()
        assertThat(listed["canEditConnection"].asBoolean()).isTrue()
        assertThat(strings(call(admin, "list_connection_categories").json!!)).containsExactly("Production")
    }

    @Test
    fun `connection settings are validated like in the UI`() {
        val result = call(
            admin,
            "create_kubernetes_connection",
            mapOf("id" to "has spaces", "displayName" to "Cluster", "requiredReviews" to 1),
        )

        assertThat(result.isError).isTrue()
        assertThat(result.text).contains("id: Only alphanumeric and dashes (-) allowed")
    }

    @Test
    fun `update_database_connection changes only the given fields`() {
        val connection = connectionHelper.createPostgresConnection(McpToolsTest.db, maxTemporaryAccessDuration = 60)
        val id = connection.getId()
        call(admin, "update_database_connection", mapOf("id" to id, "category" to "Staging"))

        val updated = call(
            admin,
            "update_database_connection",
            mapOf(
                "id" to id,
                "engine" to "MYSQL",
                "category" to "",
                "maxTemporaryAccessMinutes" to 0,
                "password" to "secret",
            ),
        ).json!!

        assertThat(updated["engine"].asString()).isEqualTo("MYSQL")
        assertThat(updated["protocol"].asString()).isEqualTo("MYSQL")
        assertThat(updated["category"].isNull).isTrue()
        assertThat(updated["maxTemporaryAccessMinutes"].isNull).isTrue()
        assertThat(updated["hasPassword"].asBoolean()).isTrue()
        assertThat(updated["hostname"].asString()).isEqualTo(McpToolsTest.db.host)
        assertThat(updated["displayName"].asString()).isEqualTo(connection.displayName)
    }

    @Test
    fun `connection tools point at the tool for the other kind`() {
        val connection = connectionHelper.createKubernetesConnection()

        val result = call(admin, "update_database_connection", mapOf("id" to connection.getId(), "port" to 1))

        assertThat(result.isError).isTrue()
        assertThat(result.text).contains("update_kubernetes_connection")
    }

    @Test
    fun `an agent creates and changes a Kubernetes connection`() {
        val created = call(
            admin,
            "create_kubernetes_connection",
            mapOf("id" to "cluster", "displayName" to "Cluster", "requiredReviews" to 1),
        ).json!!
        assertThat(created["kind"].asString()).isEqualTo("KUBERNETES")
        assertThat(created["kubernetesExecTimeoutMinutes"].asInt()).isEqualTo(60)
        assertThat(created["engine"].isNull).isTrue()

        val updated = call(
            admin,
            "update_kubernetes_connection",
            mapOf("id" to "cluster", "requiredReviews" to 0, "kubernetesExecTimeoutMinutes" to 5),
        ).json!!
        assertThat(updated["requiredReviews"].asInt()).isEqualTo(0)
        assertThat(updated["kubernetesExecTimeoutMinutes"].asInt()).isEqualTo(5)
        assertThat(updated["displayName"].asString()).isEqualTo("Cluster")
    }

    @Test
    fun `roles are created and changed with permission flags`() {
        val permissions = mapOf(
            "users" to mapOf("read" to true),
            "connections" to listOf(mapOf("selector" to "prod-*", "read" to true, "review" to true)),
        )
        val created = call(
            admin,
            "create_role",
            mapOf("name" to "Reviewers", "description" to "Review prod", "permissions" to permissions),
        ).json!!
        val id = created["id"].asString()
        assertThat(created["permissions"]["users"]["read"].asBoolean()).isTrue()
        assertThat(created["permissions"]["connections"][0]["review"].asBoolean()).isTrue()
        assertThat(created["permissions"]["connections"][0]["write"].asBoolean()).isFalse()
        assertThat(created["policies"].isNull).isTrue()
        assertThat(created["url"].asString()).endsWith("/settings/roles/$id")

        val renamed = call(admin, "update_role", mapOf("id" to id, "name" to "Prod reviewers")).json!!
        assertThat(renamed["name"].asString()).isEqualTo("Prod reviewers")
        assertThat(renamed["permissions"]["connections"][0]["selector"].asString()).isEqualTo("prod-*")

        val admins = call(admin, "update_role", mapOf("id" to id, "permissions" to mapOf("isAdmin" to true))).json!!
        assertThat(admins["permissions"]["isAdmin"].asBoolean()).isTrue()
        assertThat(admins["permissions"]["connections"].size()).isEqualTo(0)

        call(admin, "delete_role", mapOf("id" to id))
        assertThat(call(admin, "get_role", mapOf("id" to id)).isError).isTrue()
    }

    @Test
    fun `roles that flags can't express show their policies and keep them`() {
        val role = roleHelper.createRole(
            name = "Custom",
            policies = setOf(Policy(action = "execution_request:get", resource = "orders")),
        )

        val fetched = call(admin, "get_role", mapOf("id" to role.getId())).json!!
        val refused = call(admin, "update_role", mapOf("id" to role.getId(), "permissions" to mapOf<String, Any>()))
        val renamed = call(admin, "update_role", mapOf("id" to role.getId(), "name" to "Renamed")).json!!

        assertThat(fetched["permissions"].isNull).isTrue()
        assertThat(fetched["policies"][0]["action"].asString()).isEqualTo("execution_request:get")
        assertThat(refused.isError).isTrue()
        assertThat(refused.text).contains("change them in Kviklet")
        assertThat(renamed["policies"][0]["resource"].asString()).isEqualTo("orders")
    }

    @Test
    fun `unknown ids are tool errors that name the id`() {
        val role = call(admin, "get_role", mapOf("id" to "missing-role"))
        val connection = call(admin, "get_connection", mapOf("id" to "missing-connection"))

        assertThat(role.isError).isTrue()
        assertThat(role.text).contains("missing-role")
        assertThat(connection.isError).isTrue()
        assertThat(connection.text).contains("missing-connection")
    }

    @Test
    fun `the default role can't be deleted`() {
        val result = call(admin, "delete_role", mapOf("id" to Role.DEFAULT_ROLE_ID.toString()))

        assertThat(result.isError).isTrue()
        val defaultRole = call(admin, "list_roles").json!!.single { it["isDefault"].asBoolean() }
        assertThat(defaultRole["permissions"]["users"]["read"].asBoolean()).isTrue()
        assertThat(defaultRole["permissions"]["connections"][0]["selector"].asString()).isEqualTo("*")
    }

    @Test
    fun `an agent creates a user, gives them a role and deactivates them`() {
        val role = roleHelper.createRole(name = "Developers")

        val created = call(
            admin,
            "create_user",
            mapOf("email" to "new@example.com", "password" to "changeme", "fullName" to "New User"),
        ).json!!
        val userId = created["id"].asString()
        assertThat(values(created["roles"], "id")).containsExactly(Role.DEFAULT_ROLE_ID.toString())

        val withRole = call(
            admin,
            "set_user_roles",
            mapOf("userId" to userId, "roleIds" to listOf(Role.DEFAULT_ROLE_ID.toString(), role.getId())),
        ).json!!
        assertThat(values(withRole["roles"], "name")).contains("Developers")

        val withoutDefault = call(admin, "set_user_roles", mapOf("userId" to userId, "roleIds" to listOf(role.getId())))
        assertThat(withoutDefault.isError).isTrue()

        val deactivated = call(admin, "set_user_active", mapOf("userId" to userId, "active" to false)).json!!
        assertThat(deactivated["active"].asBoolean()).isFalse()
        val listed = call(admin, "list_users").json!!.single { it["id"].asString() == userId }
        assertThat(listed["active"].asBoolean()).isFalse()

        val self = call(admin, "set_user_active", mapOf("userId" to admin.getId(), "active" to false))
        assertThat(self.isError).isTrue()
    }

    @Test
    fun `new users need a password of six characters`() {
        val result = call(
            admin,
            "create_user",
            mapOf("email" to "new@example.com", "password" to "short", "fullName" to "New User"),
        )

        assertThat(result.isError).isTrue()
        assertThat(result.text).contains("password")
    }

    @Test
    fun `an agent configures the role sync`() {
        val role = roleHelper.createRole(name = "Developers")

        val config = call(
            admin,
            "update_role_sync",
            mapOf("enabled" to true, "syncMode" to "ADDITIVE"),
        ).json!!
        assertThat(config["enabled"].asBoolean()).isTrue()
        assertThat(config["syncMode"].asString()).isEqualTo("ADDITIVE")
        assertThat(config["groupsAttribute"].asString()).isEqualTo(roleSync.groupsAttribute)

        val mapping = call(
            admin,
            "add_role_sync_mapping",
            mapOf("idpGroupName" to "devs", "roleId" to role.getId()),
        ).json!!
        assertThat(mapping["roleName"].asString()).isEqualTo("Developers")
        assertThat(call(admin, "get_role_sync").json!!["mappings"].size()).isEqualTo(1)

        call(admin, "delete_role_sync_mapping", mapOf("id" to mapping["id"].asString()))
        assertThat(call(admin, "get_role_sync").json!!["mappings"].size()).isEqualTo(0)
    }

    @Test
    fun `an API key is shown once, when it is created`() {
        val created = call(admin, "create_api_key", mapOf("name" to "CI", "expiresInDays" to 30)).json!!
        assertThat(created["key"].asString()).isNotBlank()
        assertThat(created["owner"].asString()).isEqualTo(admin.email)
        assertThat(created["expiresAt"].isNull).isFalse()

        val listed = call(admin, "list_api_keys").json!!.single()
        assertThat(listed["name"].asString()).isEqualTo("CI")
        assertThat(listed["key"].isNull).isTrue()

        call(admin, "delete_api_key", mapOf("id" to created["id"].asString()))
        assertThat(call(admin, "list_api_keys").json!!.size()).isEqualTo(0)
    }

    @Test
    fun `an agent sets the notification webhooks and turns on the proxy`() {
        val updated = call(
            admin,
            "update_config",
            mapOf("slackUrl" to "https://hooks.slack.com/x", "proxyEnabled" to true),
        ).json!!
        assertThat(updated["slackUrl"].asString()).isEqualTo("https://hooks.slack.com/x")
        assertThat(updated["proxyEnabled"].asBoolean()).isTrue()
        assertThat(updated["license"]["valid"].asBoolean()).isTrue()
        assertThat(updated["license"]["seats"].asInt()).isEqualTo(2)

        val cleared = call(admin, "update_config", mapOf("slackUrl" to "")).json!!
        assertThat(cleared["slackUrl"].isNull).isTrue()
        assertThat(cleared["proxyEnabled"].asBoolean()).isTrue()
        assertThat(call(admin, "get_config").json!!["slackUrl"].isNull).isTrue()
    }

    @Test
    fun `users without the permissions can't change the configuration`() {
        val user = userHelper.createUser(permissions = listOf("datasource_connection:get"))
        roleHelper.removeDefaultRolePermissions()

        val denied = listOf(
            "create_database_connection" to mapOf(
                "id" to "x",
                "displayName" to "x",
                "engine" to "POSTGRESQL",
                "hostname" to "h",
                "port" to 1,
                "username" to "u",
                "requiredReviews" to 1,
            ),
            "create_kubernetes_connection" to mapOf("id" to "x", "displayName" to "x", "requiredReviews" to 1),
            "list_roles" to emptyMap(),
            "create_role" to mapOf("name" to "x", "description" to "x"),
            "list_users" to emptyMap(),
            "create_user" to mapOf("email" to "x@example.com", "password" to "123456", "fullName" to "x"),
            "set_user_active" to mapOf("userId" to admin.getId(), "active" to false),
            "get_role_sync" to emptyMap(),
            "list_api_keys" to emptyMap(),
            "create_api_key" to mapOf("name" to "x"),
            "get_config" to emptyMap(),
            "update_config" to mapOf("proxyEnabled" to true),
        )

        denied.forEach { (tool, arguments) ->
            assertThat(call(user, tool, arguments).isError).describedAs(tool).isTrue()
        }
    }

    private fun call(user: User, tool: String, arguments: Map<String, Any?> = emptyMap()): ToolCallResult =
        mcp.callTool(mockMvc, token(user), tool, arguments)

    private val tokens = mutableMapOf<String, String>()

    private fun token(user: User): String = tokens.getOrPut(user.getId()!!) { mcp.obtainAccessToken(mockMvc, user) }

    private fun strings(node: JsonNode): List<String> = (0 until node.size()).map { node[it].asString() }

    private fun values(node: JsonNode, field: String): List<String> =
        (0 until node.size()).map { node[it][field].asString() }

    private fun installLicense() {
        // Same test license as in McpOAuthTest
        val licenseJson = """
            {
                "license_data":{"max_users":2,"expiry_date":"2100-01-01","test_license":true},
                "signature":"E3cqrsVzWccsyWwIeCE2J4Mn/eHyP8j4T05Q4o2dtXH1lhum71rEyPqv9MLn//IcVGsLBY6MwWJGxxa+IBqZTvx0fkLix7e44BRJ5xnV83WzZbKyacNCsNqYEbNpeRcDmtC0pbk7/OSff8VDs5xdqWl7zsI+HA5KNdw878BZKVxusHkHhLtxOhHtbm7Gvcyia4XE86USTWUMYf6aCgNkQgRSOnTo5Zrs+vBUvgSI33l3XyBDx+cQcr9Mell2ytOYrTxQ4zUbRkzcsQtGRTHbh8uXQb5wS389F0zQWSLh7RrCRuaEZ0IDTt8tFkN+72fZ64504bsSR9mNgkgKTv/FvQiVCppKO8vpW0T0hg2xziXMnNSJ3MbihcNlpFsz9C2SEnGm18rQ4UagnLCWTqhz5DtWCxeaAExIT261o6J/wBwlsHHMJRiDaLo/cQOLVOUm43psOt4nlTdbijPoKhBejBuSgqSxTid1R7+8YaFlco/SaprzEspWHcOcVIPUN2jk"
            }
        """.trimIndent()
        licenseAdapter.createLicense(
            LicenseFile(fileContent = licenseJson, fileName = "test-license.json", createdAt = LocalDateTime.now()),
        )
    }
}
