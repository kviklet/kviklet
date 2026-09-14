package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.LicenseAdapter
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.helper.ExecutionRequestFactory
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.proxy.core.ProxyServer
import dev.kviklet.kviklet.proxy.core.ProxySession
import dev.kviklet.kviklet.security.AccountDeactivatedException
import dev.kviklet.kviklet.security.IdpIdentifier
import dev.kviklet.kviklet.security.UserAuthService
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.LicenseFile
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.DisabledException
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserDeactivationTest {

    @Autowired
    private lateinit var userHelper: UserHelper

    @Autowired
    private lateinit var roleHelper: RoleHelper

    @Autowired
    private lateinit var userAdapter: UserAdapter

    @Autowired
    private lateinit var userAuthService: UserAuthService

    @Autowired
    private lateinit var licenseAdapter: LicenseAdapter

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    @Qualifier("postgresProxyServer")
    private lateinit var postgresProxyServer: ProxyServer

    @AfterEach
    fun tearDown() {
        postgresProxyServer.expireSessions { true }
        licenseAdapter.deleteAll()
        userHelper.deleteAll()
        roleHelper.deleteAll()
    }

    private fun setStatus(userId: String, active: Boolean, cookie: jakarta.servlet.http.Cookie) = mockMvc.perform(
        put("/users/$userId/status").cookie(cookie)
            .content("""{"active": $active}""")
            .contentType("application/json"),
    )

    private fun attemptLogin(email: String, password: String = "123456") = mockMvc.perform(
        post("/login")
            .content("""{"email": "$email", "password": "$password"}""")
            .contentType("application/json"),
    )

    @Test
    fun `deactivating a user ends their session, blocks login and keeps the record`() {
        userHelper.createUser(permissions = listOf("*"))
        val adminCookie = userHelper.login(mockMvc = mockMvc)
        val user = userHelper.createUser(permissions = listOf("*"), email = "dev@example.com")
        val userCookie = userHelper.login(email = user.email, mockMvc = mockMvc)

        mockMvc.perform(get("/status").cookie(userCookie)).andExpect(status().isOk)

        setStatus(user.getId()!!, active = false, cookie = adminCookie)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id", `is`(user.getId())))
            .andExpect(jsonPath("$.active", `is`(false)))

        // The existing session no longer works (the stored session is purged on deactivation) ...
        mockMvc.perform(get("/status").cookie(userCookie))
            .andExpect(status().isUnauthorized)

        // ... and neither does logging in again.
        attemptLogin(user.email)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message", `is`(AccountDeactivatedException.MESSAGE)))

        // The record, its roles and its history stay for auditing; the list still contains the user.
        val storedUser = userAdapter.findById(user.getId()!!)
        assertThat(storedUser.active).isFalse()
        assertThat(
            storedUser.roles.map {
                it.getId()
            },
        ).containsExactlyInAnyOrderElementsOf(user.roles.map { it.getId() })
        mockMvc.perform(get("/users/").cookie(adminCookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.users[?(@.id == '${user.getId()}')].active", `is`(listOf(false))))
    }

    @Test
    fun `wrong password on a deactivated account is reported as bad credentials`() {
        userHelper.createUser(permissions = listOf("*"))
        val adminCookie = userHelper.login(mockMvc = mockMvc)
        val user = userHelper.createUser(permissions = listOf("*"), email = "dev@example.com")
        setStatus(user.getId()!!, active = false, cookie = adminCookie).andExpect(status().isOk)

        attemptLogin(user.email, password = "wrong-password")
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message", not(containsString("deactivated"))))
    }

    @Test
    fun `a live session of a deactivated user is ended even if the session store was not purged`() {
        val user = userHelper.createUser(permissions = listOf("*"))
        val userCookie = userHelper.login(email = user.email, mockMvc = mockMvc)
        mockMvc.perform(get("/status").cookie(userCookie)).andExpect(status().isOk)

        // Flip the flag directly, bypassing the listener that deletes stored sessions.
        userAdapter.updateUser(user.copy(active = false))

        mockMvc.perform(get("/status").cookie(userCookie)).andExpect(status().isUnauthorized)
        // The session was invalidated, not just refused: it stays unusable after reactivation.
        userAdapter.updateUser(user.copy(active = true))
        mockMvc.perform(get("/status").cookie(userCookie)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `reactivation restores the same account`() {
        userHelper.createUser(permissions = listOf("*"))
        val adminCookie = userHelper.login(mockMvc = mockMvc)
        val user = userHelper.createUser(permissions = listOf("*"), email = "dev@example.com")

        setStatus(user.getId()!!, active = false, cookie = adminCookie).andExpect(status().isOk)
        attemptLogin(user.email).andExpect(status().isUnauthorized)

        setStatus(user.getId()!!, active = true, cookie = adminCookie)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id", `is`(user.getId())))
            .andExpect(jsonPath("$.active", `is`(true)))
            .andExpect(jsonPath("$.roles.length()", `is`(user.roles.size)))

        attemptLogin(user.email).andExpect(status().isOk)
    }

    @Test
    fun `users cannot deactivate themselves`() {
        val admin = userHelper.createUser(permissions = listOf("*"))
        val adminCookie = userHelper.login(mockMvc = mockMvc)

        setStatus(admin.getId()!!, active = false, cookie = adminCookie)
            .andExpect(status().isBadRequest)
        assertThat(userAdapter.findById(admin.getId()!!).active).isTrue()
    }

    @Test
    fun `changing the status requires the edit roles permission`() {
        userHelper.createUser(permissions = listOf("user:edit", "user:get"), resources = listOf("*", "*"))
        val cookie = userHelper.login(mockMvc = mockMvc)
        val user = userHelper.createUser(permissions = listOf("*"), email = "dev@example.com")

        setStatus(user.getId()!!, active = false, cookie = cookie).andExpect(status().isForbidden)
        assertThat(userAdapter.findById(user.getId()!!).active).isTrue()
    }

    @Test
    fun `creating a user with the email of a deactivated account points to reactivation`() {
        userHelper.createUser(permissions = listOf("*"))
        val adminCookie = userHelper.login(mockMvc = mockMvc)
        val user = userHelper.createUser(permissions = listOf("*"), email = "dev@example.com")
        setStatus(user.getId()!!, active = false, cookie = adminCookie).andExpect(status().isOk)

        mockMvc.perform(
            post("/users/").cookie(adminCookie).content(
                """
                {
                    "email": "${user.email}",
                    "password": "123456",
                    "fullName": "Dev Again"
                }
                """.trimIndent(),
            ).contentType("application/json"),
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.message", containsString("Reactivate")))
    }

    @Test
    fun `users can no longer be deleted`() {
        userHelper.createUser(permissions = listOf("*"))
        val adminCookie = userHelper.login(mockMvc = mockMvc)
        val user = userHelper.createUser(permissions = listOf("*"), email = "dev@example.com")

        mockMvc.perform(delete("/users/${user.getId()}").cookie(adminCookie))
            .andExpect(status().isMethodNotAllowed)
        assertThat(userAdapter.findById(user.getId()!!).getId()).isEqualTo(user.getId())
    }

    @Test
    fun `license seats are counted against active users only`() {
        installTwoSeatLicense()
        userHelper.createUser(permissions = listOf("*"))
        val adminCookie = userHelper.login(mockMvc = mockMvc)
        val second = userHelper.createUser(permissions = listOf("*"), email = "second@example.com")

        // Both seats are taken.
        createUserRequest("third@example.com", adminCookie).andExpect(status().isBadRequest)

        // Deactivating frees a seat ...
        setStatus(second.getId()!!, active = false, cookie = adminCookie).andExpect(status().isOk)
        createUserRequest("third@example.com", adminCookie).andExpect(status().isOk)

        // ... which reactivation then cannot take back while it is occupied.
        setStatus(second.getId()!!, active = true, cookie = adminCookie)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message", containsString("License")))
        assertThat(userAdapter.findById(second.getId()!!).active).isFalse()
    }

    @Test
    fun `sso login of a deactivated user is refused without touching the account`() {
        val user = userAdapter.createUser(
            User(email = "sso@example.com", fullName = "SSO User", subject = "old-subject", active = false),
        )

        // Same identity as before.
        assertThatThrownBy {
            userAuthService.findOrCreateUser(IdpIdentifier.Oidc("old-subject"), "sso@example.com", "SSO User")
        }.isInstanceOf(DisabledException::class.java)
            .hasMessage(AccountDeactivatedException.MESSAGE)

        // Recreated IdP account with the same email: still refused, and the new identity is not attached.
        assertThatThrownBy {
            userAuthService.findOrCreateUser(IdpIdentifier.Oidc("new-subject"), "sso@example.com", "SSO User")
        }.isInstanceOf(DisabledException::class.java)

        val stored = userAdapter.findById(user.getId()!!)
        assertThat(stored.active).isFalse()
        assertThat(stored.subject).isEqualTo("old-subject")

        // After reactivation the usual email migration attaches the new identity.
        userAdapter.updateUser(stored.copy(active = true))
        val migrated = userAuthService.findOrCreateUser(
            IdpIdentifier.Oidc("new-subject"),
            "sso@example.com",
            "SSO User",
        )
        assertThat(migrated.getId()).isEqualTo(user.getId())
        assertThat(migrated.subject).isEqualTo("new-subject")
    }

    @Test
    fun `sso provisioning of a new user needs a free seat`() {
        installTwoSeatLicense()
        userHelper.createUser(permissions = listOf("*"))
        val adminCookie = userHelper.login(mockMvc = mockMvc)
        val second = userHelper.createUser(permissions = listOf("*"), email = "second@example.com")

        assertThatThrownBy {
            userAuthService.findOrCreateUser(IdpIdentifier.Oidc("new-person"), "new@example.com", "New Person")
        }.hasMessageContaining("License")

        setStatus(second.getId()!!, active = false, cookie = adminCookie).andExpect(status().isOk)
        val created = userAuthService.findOrCreateUser(
            IdpIdentifier.Oidc("new-person"),
            "new@example.com",
            "New Person",
        )
        assertThat(created.active).isTrue()
    }

    @Test
    fun `deactivating a user ends their proxy sessions and leaves the others alone`() {
        userHelper.createUser(permissions = listOf("*"))
        val adminCookie = userHelper.login(mockMvc = mockMvc)
        val user = userHelper.createUser(permissions = listOf("*"), email = "dev@example.com")
        val other = userHelper.createUser(permissions = listOf("*"), email = "other@example.com")
        val session = postgresProxyServer.registerSession(proxySession("dev-session", user.getId()!!), expiresAt = null)
        val bystander = postgresProxyServer.registerSession(
            proxySession("other-session", other.getId()!!),
            expiresAt = null,
        )

        setStatus(user.getId()!!, active = false, cookie = adminCookie).andExpect(status().isOk)

        assertThat(session.active).isFalse()
        assertThat(bystander.active).isTrue()
    }

    @Test
    fun `editing a deactivated user keeps them deactivated`() {
        userHelper.createUser(permissions = listOf("*"))
        val adminCookie = userHelper.login(mockMvc = mockMvc)
        val user = userHelper.createUser(permissions = listOf("*"), email = "dev@example.com")
        setStatus(user.getId()!!, active = false, cookie = adminCookie).andExpect(status().isOk)

        // Profile edit ...
        mockMvc.perform(
            patch("/users/${user.getId()}").cookie(adminCookie)
                .content("""{"fullName": "Renamed"}""")
                .contentType("application/json"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.fullName", `is`("Renamed")))
            .andExpect(jsonPath("$.active", `is`(false)))

        // ... and role edit, which takes the other update path.
        val roleIds = user.roles.map { "\"${it.getId()}\"" }.joinToString(",")
        mockMvc.perform(
            patch("/users/${user.getId()}").cookie(adminCookie)
                .content("""{"roles": [$roleIds]}""")
                .contentType("application/json"),
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.active", `is`(false)))

        assertThat(userAdapter.findById(user.getId()!!).active).isFalse()
        attemptLogin(user.email).andExpect(status().isUnauthorized)
    }

    private fun proxySession(username: String, userId: String) = ProxySession(
        username = username,
        password = "pw",
        executionRequest = ExecutionRequestFactory().createDatasourceExecutionRequest(),
        userId = userId,
        targetHost = "localhost",
        targetPort = 5432,
        databaseName = "testdb",
        datasourceType = DatasourceType.POSTGRESQL,
        authenticationDetails = AuthenticationDetails.UserPassword("test", "test"),
    )

    private fun createUserRequest(email: String, cookie: jakarta.servlet.http.Cookie) = mockMvc.perform(
        post("/users/").cookie(cookie).content(
            """
            {
                "email": "$email",
                "password": "123456",
                "fullName": "Some User"
            }
            """.trimIndent(),
        ).contentType("application/json"),
    )

    private fun installTwoSeatLicense() {
        licenseAdapter.deleteAll()
        // WARNING: This is a test-only license limited to 2 users with test_license flag.
        // DO NOT use in production. Real licenses must be obtained from Kviklet.
        // NOTE: max_users MUST stay 2 — the signature below is only valid for max_users:2.
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
