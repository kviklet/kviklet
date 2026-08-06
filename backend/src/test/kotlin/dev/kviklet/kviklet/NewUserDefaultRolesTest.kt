package dev.kviklet.kviklet

import com.ninjasquad.springmockk.MockkBean
import dev.kviklet.kviklet.db.ConfigurationAdapter
import dev.kviklet.kviklet.db.RoleSyncConfigAdapter
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.security.IdpIdentifier
import dev.kviklet.kviklet.security.UserAuthService
import dev.kviklet.kviklet.service.LicenseService
import dev.kviklet.kviklet.service.RoleService
import dev.kviklet.kviklet.service.UserService
import dev.kviklet.kviklet.service.dto.Configuration
import dev.kviklet.kviklet.service.dto.License
import dev.kviklet.kviklet.service.dto.LicenseFile
import dev.kviklet.kviklet.service.dto.RoleId
import dev.kviklet.kviklet.service.dto.SyncMode
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.`is`
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NewUserDefaultRolesTest {

    @MockkBean lateinit var licenseService: LicenseService

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userHelper: UserHelper
    @Autowired private lateinit var roleHelper: RoleHelper
    @Autowired private lateinit var userAdapter: UserAdapter
    @Autowired private lateinit var configurationAdapter: ConfigurationAdapter
    @Autowired private lateinit var roleSyncConfigAdapter: RoleSyncConfigAdapter
    @Autowired private lateinit var userAuthService: UserAuthService
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var roleService: RoleService

    @BeforeEach
    fun setUp() {
        every { licenseService.getActiveLicense() } returns null
        every { licenseService.getLicenses() } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        userHelper.deleteAll()
        roleHelper.deleteAll()
        configurationAdapter.setConfiguration("newUserRoleIds", "")
        roleSyncConfigAdapter.updateConfig(enabled = false)
        roleSyncConfigAdapter.deleteAllMappings()
    }


    @Nested
    inner class PasswordRegistration {
        @Test
        fun `new user gets assigned configured default roles on registration`() {
            val extraRole = roleHelper.createRole(
                name = "Analyst",
                permissions = listOf("datasource_connection:get"),
            )

            configurationAdapter.setConfiguration(
                Configuration(teamsUrl = null, slackUrl = null, newUserRoleIds = listOf(extraRole.getId()!!)),
            )

            userService.createUser(
                email = "newuser@example.com",
                password = "password123",
                fullName = "New User",
            )

            val user = userAdapter.findByEmail("newuser@example.com")
            assertThat(user).isNotNull
            val roleNames = user!!.roles.map { it.name }
            assertThat(roleNames).contains("Analyst")
            assertThat(user.roles.any { it.isDefault }).isTrue()
        }

        @Test
        fun `new user without configuration only has the default role`() {
            userService.createUser(
                email = "bareuser@example.com",
                password = "password123",
                fullName = "Bare User",
            )

            val user = userAdapter.findByEmail("bareuser@example.com")
            assertThat(user).isNotNull
            assertThat(user!!.roles).hasSize(1)
            assertThat(user.roles.single().isDefault).isTrue()
        }

        @Test
        fun `multiple configured default roles are all assigned to new users`() {
            val roleA = roleHelper.createRole(name = "RoleA", permissions = listOf("datasource_connection:get"))
            val roleB = roleHelper.createRole(name = "RoleB", permissions = listOf("execution_request:get"))
            configurationAdapter.setConfiguration(
                Configuration(teamsUrl = null, slackUrl = null, newUserRoleIds = listOf(roleA.getId()!!, roleB.getId()!!)),
            )

            userService.createUser(
                email = "multiuser@example.com",
                password = "password123",
                fullName = "Multi User",
            )

            val user = userAdapter.findByEmail("multiuser@example.com")
            assertThat(user).isNotNull
            val roleNames = user!!.roles.map { it.name }
            assertThat(roleNames).containsAll(listOf("RoleA", "RoleB"))
            assertThat(user.roles.any { it.isDefault }).isTrue()
        }

        @Test
        fun `invalid role IDs in config are silently ignored and user still gets the default role`() {
            configurationAdapter.setConfiguration(
                Configuration(teamsUrl = null, slackUrl = null, newUserRoleIds = listOf("non-existent-role-id")),
            )

            userService.createUser(
                email = "robustuser@example.com",
                password = "password123",
                fullName = "Robust User",
            )

            val user = userAdapter.findByEmail("robustuser@example.com")
            assertThat(user).isNotNull
            assertThat(user!!.roles).hasSize(1)
            assertThat(user.roles.single().isDefault).isTrue()
        }

        @Test
        fun `new user created via HTTP POST users API receives configured default roles`() {
            userHelper.createUser(permissions = listOf("*"))
            val adminCookie = userHelper.login(mockMvc = mockMvc)

            val extraRole = roleHelper.createRole(name = "Viewer", permissions = listOf("datasource_connection:get"))
            configurationAdapter.setConfiguration(
                Configuration(teamsUrl = null, slackUrl = null, newUserRoleIds = listOf(extraRole.getId()!!)),
            )

            mockMvc.perform(
                post("/users/")
                    .cookie(adminCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"email": "newhttp@example.com", "password": "pass123", "fullName": "HTTP User"}""",
                    ),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.roles.length()", `is`(2)))
                .andExpect(jsonPath("$.roles[*].name", hasItem("Viewer")))
        }
    }

    @Nested
    inner class RoleDeletion {
        @Test
        fun `deleting a role that is in newUserRoleIds removes it from the configuration`() {
            val role = roleHelper.createRole(name = "Removable", permissions = listOf("datasource_connection:get"))
            val roleId = role.getId()!!
            configurationAdapter.setConfiguration(Configuration(teamsUrl = null, slackUrl = null, newUserRoleIds = listOf(roleId)))

            assertThat(configurationAdapter.getConfiguration().newUserRoleIds).contains(roleId)
            roleService.deleteRole(RoleId(roleId))
            assertThat(configurationAdapter.getConfiguration().newUserRoleIds).doesNotContain(roleId)
        }

        @Test
        fun `deleting a role not in newUserRoleIds leaves newUserRoleIds unchanged`() {
            val keptRole = roleHelper.createRole(name = "Kept", permissions = listOf("datasource_connection:get"))
            val unrelatedRole = roleHelper.createRole(name = "Unrelated", permissions = listOf("role:get"))
            configurationAdapter.setConfiguration(Configuration(teamsUrl = null, slackUrl = null, newUserRoleIds = listOf(keptRole.getId()!!)))

            roleService.deleteRole(RoleId(unrelatedRole.getId()!!))

            assertThat(configurationAdapter.getConfiguration().newUserRoleIds)
                .containsExactly(keptRole.getId()!!)
        }

        @Test
        fun `deleting a role via HTTP removes it from newUserRoleIds config`() {
            userHelper.createUser(permissions = listOf("*"))
            val adminCookie = userHelper.login(mockMvc = mockMvc)

            val role = roleHelper.createRole(name = "TempRole", permissions = listOf("datasource_connection:get"))
            val roleId = role.getId()!!
            configurationAdapter.setConfiguration(Configuration(teamsUrl = null, slackUrl = null, newUserRoleIds = listOf(roleId)))

            mockMvc.perform(
                delete("/roles/$roleId").cookie(adminCookie),
            ).andExpect(status().isOk)

            mockMvc.perform(
                get("/config/").cookie(adminCookie),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.newUserRoleIds.length()", `is`(0)))
        }

        @Test
        fun `deleting one of multiple newUserRoleIds keeps the remaining ones`() {
            val roleA = roleHelper.createRole(name = "RoleA", permissions = listOf("datasource_connection:get"))
            val roleB = roleHelper.createRole(name = "RoleB", permissions = listOf("execution_request:get"))
            configurationAdapter.setConfiguration(
                Configuration(teamsUrl = null, slackUrl = null, newUserRoleIds = listOf(roleA.getId()!!, roleB.getId()!!)),
            )

            roleService.deleteRole(RoleId(roleA.getId()!!))

            val config = configurationAdapter.getConfiguration()
            assertThat(config.newUserRoleIds).doesNotContain(roleA.getId()!!)
            assertThat(config.newUserRoleIds).contains(roleB.getId()!!)
        }
    }

    @Nested
    inner class ConfigApi {

        @Test
        fun `saved newUserRoleIds are returned by the config API`() {
            userHelper.createUser(permissions = listOf("*"))
            val adminCookie = userHelper.login(mockMvc = mockMvc)

            val role = roleHelper.createRole(name = "Configured", permissions = listOf("datasource_connection:get"))

            mockMvc.perform(
                put("/config/")
                    .cookie(adminCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"newUserRoleIds": ["${role.getId()}"]}"""),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.newUserRoleIds.length()", `is`(1)))
                .andExpect(jsonPath("$.newUserRoleIds[0]", `is`(role.getId())))

            mockMvc.perform(get("/config/").cookie(adminCookie))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.newUserRoleIds.length()", `is`(1)))
                .andExpect(jsonPath("$.newUserRoleIds[0]", `is`(role.getId())))
        }

        @Test
        fun `saving config with empty newUserRoleIds clears previous configuration`() {
            userHelper.createUser(permissions = listOf("*"))
            val adminCookie = userHelper.login(mockMvc = mockMvc)

            val role = roleHelper.createRole(name = "ToRemove", permissions = listOf("datasource_connection:get"))
            configurationAdapter.setConfiguration(Configuration(teamsUrl = null, slackUrl = null, newUserRoleIds = listOf(role.getId()!!)))

            mockMvc.perform(
                put("/config/")
                    .cookie(adminCookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"newUserRoleIds": []}"""),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.newUserRoleIds.length()", `is`(0)))
        }
    }

    @Nested
    inner class RoleSyncInteraction {

        @BeforeEach
        fun setUpLicense() {
            every { licenseService.getActiveLicense() } returns License(
                file = LicenseFile(fileContent = "", fileName = "test.json", createdAt = LocalDateTime.now()),
                validUntil = LocalDate.now().plusYears(1),
                createdAt = LocalDateTime.now(),
                allowedUsers = 100u,
            )
        }

        /**
         * Password-based registration goes through UserService which does NOT call RoleSyncService,
         * so configured newUserRoleIds are always applied regardless of the sync settings.
         *
         * IdP-based logins (OIDC/SAML/LDAP) go through UserAuthService which calls RoleSyncService.resolveRoles
         * after creating the user. The sync mode determines which roles survive:
         *
         *  - DISABLED    → resolveRoles returns only the default role (newUserRoleIds ignored)
         *  - FULL_SYNC   → resolveRoles returns mapped roles + default (newUserRoleIds ignored)
         *  - ADDITIVE    → resolveRoles returns existing + mapped + default (newUserRoleIds kept when groups match)
         *  - FIRST_LOGIN_ONLY (new user)    → resolveRoles returns mapped roles + default
         *  - FIRST_LOGIN_ONLY (existing user) → resolveRoles returns existingRoles unchanged
         */

        @Test
        fun `password-based new user always gets configured default roles regardless of role sync settings`() {
            val extraRole = roleHelper.createRole(name = "SyncRole", permissions = listOf("datasource_connection:get"))
            configurationAdapter.setConfiguration(
                Configuration(teamsUrl = null, slackUrl = null, newUserRoleIds = listOf(extraRole.getId()!!)),
            )
            roleSyncConfigAdapter.updateConfig(enabled = true, syncMode = SyncMode.FULL_SYNC)

            userService.createUser(
                email = "passuser@example.com",
                password = "pass123",
                fullName = "Pass User",
            )

            val user = userAdapter.findByEmail("passuser@example.com")
            assertThat(user!!.roles.map { it.name }).contains("SyncRole")
        }

        @Test
        fun `new IdP user with role sync disabled receives only the default role`() {
            val extraRole = roleHelper.createRole(name = "OidcDefault", permissions = listOf("datasource_connection:get"))
            configurationAdapter.setConfiguration(
                Configuration(teamsUrl = null, slackUrl = null, newUserRoleIds = listOf(extraRole.getId()!!)),
            )
            // role sync remains disabled (setUp ensures this)

            userAuthService.findOrCreateUser(
                idpIdentifier = IdpIdentifier.Oidc("subject-sync-disabled"),
                email = "oidc-disabled@example.com",
                fullName = "OIDC Disabled",
            )

            val user = userAdapter.findByEmail("oidc-disabled@example.com")
            assertThat(user).isNotNull
            // With sync disabled, resolveRoles returns only the default role for new users
            assertThat(user!!.roles).hasSize(1)
            assertThat(user.roles.single().isDefault).isTrue()
        }

        @Test
        fun `new IdP user with FULL_SYNC mode receives synced roles and the configured default roles are overridden`() {
            val extraRole = roleHelper.createRole(name = "ExtraDefault", permissions = listOf("datasource_connection:get"))
            val syncedRole = roleHelper.createRole(name = "SyncedGroup", permissions = listOf("execution_request:get"))
            configurationAdapter.setConfiguration(
                Configuration(teamsUrl = null, slackUrl = null, newUserRoleIds = listOf(extraRole.getId()!!)),
            )
            roleSyncConfigAdapter.updateConfig(enabled = true, syncMode = SyncMode.FULL_SYNC, groupsAttribute = "groups")
            roleSyncConfigAdapter.addMapping("dev-team", syncedRole.getId()!!)

            userAuthService.findOrCreateUser(
                idpIdentifier = IdpIdentifier.Oidc("subject-full-sync"),
                email = "oidc-fullsync@example.com",
                fullName = "OIDC Full Sync",
                idpGroups = listOf("dev-team"),
            )

            val user = userAdapter.findByEmail("oidc-fullsync@example.com")
            assertThat(user).isNotNull
            val roleNames = user!!.roles.map { it.name }
            // FULL_SYNC: resolved = mappedRoles + defaultRole (newUserRoleIds not preserved)
            assertThat(roleNames).contains("SyncedGroup")
            assertThat(roleNames).doesNotContain("ExtraDefault")
            assertThat(user.roles.any { it.isDefault }).isTrue()
        }

        @Test
        fun `new IdP user with ADDITIVE sync mode retains configured default roles alongside synced roles`() {
            val extraRole = roleHelper.createRole(name = "AdditiveDefault", permissions = listOf("datasource_connection:get"))
            val syncedRole = roleHelper.createRole(name = "AdditiveGroup", permissions = listOf("execution_request:get"))
            configurationAdapter.setConfiguration(
                Configuration(teamsUrl = null, slackUrl = null, newUserRoleIds = listOf(extraRole.getId()!!)),
            )
            roleSyncConfigAdapter.updateConfig(enabled = true, syncMode = SyncMode.ADDITIVE, groupsAttribute = "groups")
            roleSyncConfigAdapter.addMapping("ops-team", syncedRole.getId()!!)

            userAuthService.findOrCreateUser(
                idpIdentifier = IdpIdentifier.Oidc("subject-additive"),
                email = "oidc-additive@example.com",
                fullName = "OIDC Additive",
                idpGroups = listOf("ops-team"),
            )

            val user = userAdapter.findByEmail("oidc-additive@example.com")
            assertThat(user).isNotNull
            val roleNames = user!!.roles.map { it.name }
            // ADDITIVE: resolved = existingRoles (includes extraRole) + mappedRoles + defaultRole
            assertThat(roleNames).contains("AdditiveDefault")
            assertThat(roleNames).contains("AdditiveGroup")
            assertThat(user.roles.any { it.isDefault }).isTrue()
        }

        @Test
        fun `new IdP user with FIRST_LOGIN_ONLY sync mode gets synced roles on first login`() {
            val syncedRole = roleHelper.createRole(name = "FirstLoginGroup", permissions = listOf("execution_request:get"))
            roleSyncConfigAdapter.updateConfig(
                enabled = true,
                syncMode = SyncMode.FIRST_LOGIN_ONLY,
                groupsAttribute = "groups",
            )
            roleSyncConfigAdapter.addMapping("sre-team", syncedRole.getId()!!)

            userAuthService.findOrCreateUser(
                idpIdentifier = IdpIdentifier.Oidc("subject-first-login"),
                email = "oidc-firstlogin@example.com",
                fullName = "OIDC First Login",
                idpGroups = listOf("sre-team"),
            )

            val user = userAdapter.findByEmail("oidc-firstlogin@example.com")
            assertThat(user).isNotNull
            val roleNames = user!!.roles.map { it.name }
            assertThat(roleNames).contains("FirstLoginGroup")
            assertThat(user.roles.any { it.isDefault }).isTrue()
        }

        @Test
        fun `existing IdP user with FIRST_LOGIN_ONLY sync mode keeps their roles unchanged on subsequent logins`() {
            val syncedRole = roleHelper.createRole(name = "FirstLoginSynced", permissions = listOf("execution_request:get"))
            roleSyncConfigAdapter.updateConfig(
                enabled = true,
                syncMode = SyncMode.FIRST_LOGIN_ONLY,
                groupsAttribute = "groups",
            )
            roleSyncConfigAdapter.addMapping("sre-team", syncedRole.getId()!!)

            // First login: user is created and gets the "sre-team" mapped role
            userAuthService.findOrCreateUser(
                idpIdentifier = IdpIdentifier.Oidc("subject-first-login-existing"),
                email = "oidc-existing@example.com",
                fullName = "OIDC Existing",
                idpGroups = listOf("sre-team"),
            )
            val rolesAfterFirstLogin = userAdapter.findByEmail("oidc-existing@example.com")!!.roles.map { it.name }
            assertThat(rolesAfterFirstLogin).contains("FirstLoginSynced")

            // Remove the group mapping to simulate the user leaving the IdP group
            roleSyncConfigAdapter.deleteAllMappings()

            // Second login: no groups match but FIRST_LOGIN_ONLY should keep existing roles
            userAuthService.findOrCreateUser(
                idpIdentifier = IdpIdentifier.Oidc("subject-first-login-existing"),
                email = "oidc-existing@example.com",
                fullName = "OIDC Existing",
                idpGroups = listOf("sre-team"),
            )

            val rolesAfterSecondLogin = userAdapter.findByEmail("oidc-existing@example.com")!!.roles.map { it.name }
            assertThat(rolesAfterSecondLogin).contains("FirstLoginSynced")
        }
    }
}
