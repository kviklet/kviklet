package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.db.RoleAdapter
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.service.LicenseService
import dev.kviklet.kviklet.service.RoleSyncService
import dev.kviklet.kviklet.service.dto.Role
import dev.kviklet.kviklet.telemetry.LoginMethod
import dev.kviklet.kviklet.telemetry.Telemetry
import dev.kviklet.kviklet.telemetry.TelemetryEvent
import dev.kviklet.kviklet.telemetry.UserCreated
import dev.kviklet.kviklet.telemetry.UserMigratedToSso
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class UserAuthServiceTelemetryTest {

    private val userAdapter = mockk<UserAdapter>()
    private val roleAdapter = mockk<RoleAdapter>()
    private val licenseService = mockk<LicenseService>(relaxed = true)
    private val roleSyncService = mockk<RoleSyncService>()
    private val telemetry = mockk<Telemetry>(relaxed = true)
    private val service = UserAuthService(userAdapter, roleAdapter, licenseService, roleSyncService, telemetry)

    private val defaultRole = Role(name = "Default", description = "", policies = emptySet())

    init {
        every { licenseService.getActiveLicense() } returns null
        every { roleAdapter.findById(Role.DEFAULT_ROLE_ID) } returns defaultRole
        every { roleSyncService.resolveRoles(any(), any(), any()) } answers { secondArg() }
        every { userAdapter.createOrUpdateUser(any()) } answers { firstArg() }
        every { userAdapter.findBySubject(any()) } returns null
        every { userAdapter.findByLdapIdentifier(any()) } returns null
        every { userAdapter.findBySamlNameId(any()) } returns null
        every { userAdapter.findByGithubId(any()) } returns null
        every { userAdapter.findByEmail(any()) } returns null
    }

    @Test
    fun `a user created on first sso login is reported with the provider's method`() {
        service.findOrCreateUser(IdpIdentifier.Oidc("sub-1"), "new@example.com", "New User")

        verify(exactly = 1) { telemetry.track(UserCreated(LoginMethod.OIDC)) }
        verify(exactly = 0) { telemetry.track(ofType<UserMigratedToSso>()) }
    }

    @Test
    fun `a password user signing in through ldap for the first time is reported as migrated`() {
        every { userAdapter.findByEmail("old@example.com") } returns
            User(email = "old@example.com", password = "hash", roles = setOf(defaultRole))

        service.findOrCreateUser(IdpIdentifier.Ldap("old"), "old@example.com", null)

        verify(exactly = 1) { telemetry.track(UserMigratedToSso(LoginMethod.LDAP)) }
        verify(exactly = 0) { telemetry.track(ofType<UserCreated>()) }
    }

    @Test
    fun `a returning sso user reports nothing`() {
        every { userAdapter.findBySamlNameId("name-1") } returns
            User(email = "back@example.com", samlNameId = "name-1", roles = setOf(defaultRole))

        service.findOrCreateUser(IdpIdentifier.Saml("name-1"), "back@example.com", null)

        verify(exactly = 0) { telemetry.track(any<TelemetryEvent>()) }
    }

    @Test
    fun `github sign-ins count as oidc`() {
        service.findOrCreateUser(IdpIdentifier.GitHub("42"), "gh@example.com", "GH")

        verify(exactly = 1) { telemetry.track(UserCreated(LoginMethod.OIDC)) }
    }
}
