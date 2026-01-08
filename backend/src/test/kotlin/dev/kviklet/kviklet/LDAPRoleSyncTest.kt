package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.LicenseAdapter
import dev.kviklet.kviklet.db.RoleAdapter
import dev.kviklet.kviklet.db.RoleSyncConfigAdapter
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.service.dto.LicenseFile
import dev.kviklet.kviklet.service.dto.Role
import dev.kviklet.kviklet.service.dto.RoleId
import dev.kviklet.kviklet.service.dto.SyncMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.MountableFile
import java.nio.file.Files
import java.time.Duration
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class LDAPRoleSyncTest {

    @Autowired
    private lateinit var userAdapter: UserAdapter

    @Autowired
    private lateinit var licenseAdapter: LicenseAdapter

    @Autowired
    private lateinit var roleAdapter: RoleAdapter

    @Autowired
    private lateinit var roleSyncConfigAdapter: RoleSyncConfigAdapter

    @Autowired
    private lateinit var mockMvc: MockMvc

    companion object {
        @Container
        val ldapContainer = GenericContainer("osixia/openldap:1.5.0")
            .withExposedPorts(389, 636)
            .withEnv(
                mapOf(
                    "LDAP_ORGANISATION" to "Test Org",
                    "LDAP_DOMAIN" to "example.org",
                    "LDAP_ADMIN_PASSWORD" to "admin",
                    "LDAP_CONFIG_PASSWORD" to "config",
                    "LDAP_LOG_LEVEL" to "256",
                ),
            )
            .waitingFor(Wait.forLogMessage(".*Start OpenLDAP.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2))

        @BeforeAll
        @JvmStatic
        fun setupLdap() {
            Thread.sleep(2000)

            // Create people OU
            val ouLdifContent = """
                dn: ou=people,dc=example,dc=org
                objectClass: organizationalUnit
                ou: people
            """.trimIndent()

            executeLdif(ouLdifContent, "ou-data")

            // Create groups OU
            val groupsOuLdifContent = """
                dn: ou=groups,dc=example,dc=org
                objectClass: organizationalUnit
                ou: groups
            """.trimIndent()

            executeLdif(groupsOuLdifContent, "groups-ou-data")

            // Generate password hash
            val hashResult = ldapContainer.execInContainer("slappasswd", "-s", "password")
            val passwordHash = hashResult.stdout.trim()

            // Create user
            val userLdifContent = """
                dn: uid=john.doe,ou=people,dc=example,dc=org
                objectClass: inetOrgPerson
                cn: John Doe
                sn: Doe
                uid: john.doe
                mail: john.doe@example.org
                userPassword: $passwordHash
            """.trimIndent()

            executeLdif(userLdifContent, "user-data")

            // Create developers group with john.doe as member
            val developersGroupLdifContent = """
                dn: cn=developers,ou=groups,dc=example,dc=org
                objectClass: groupOfNames
                cn: developers
                member: uid=john.doe,ou=people,dc=example,dc=org
            """.trimIndent()

            executeLdif(developersGroupLdifContent, "developers-group-data")

            // Enable memberOf overlay (needed for memberOf attribute to be populated)
            enableMemberOfOverlay()
        }

        private fun executeLdif(ldifContent: String, prefix: String) {
            val tempFile = Files.createTempFile(prefix, ".ldif")
            Files.write(tempFile, ldifContent.toByteArray())
            ldapContainer.copyFileToContainer(
                MountableFile.forHostPath(tempFile),
                "/tmp/$prefix.ldif",
            )
            ldapContainer.execInContainer(
                "ldapadd",
                "-x",
                "-D",
                "cn=admin,dc=example,dc=org",
                "-w",
                "admin",
                "-f",
                "/tmp/$prefix.ldif",
            )
            Files.delete(tempFile)
        }

        private fun enableMemberOfOverlay() {
            // The memberOf overlay needs to be enabled for the memberOf attribute to be auto-populated
            // For osixia/openldap, we need to load the memberof module and configure it
            val memberOfModuleLdif = """
                dn: cn=module{0},cn=config
                changetype: modify
                add: olcModuleLoad
                olcModuleLoad: memberof
            """.trimIndent()

            val tempFile = Files.createTempFile("memberof-module", ".ldif")
            Files.write(tempFile, memberOfModuleLdif.toByteArray())
            ldapContainer.copyFileToContainer(
                MountableFile.forHostPath(tempFile),
                "/tmp/memberof-module.ldif",
            )

            // Try to add the module (may fail if already loaded, which is fine)
            ldapContainer.execInContainer(
                "ldapmodify",
                "-Y",
                "EXTERNAL",
                "-H",
                "ldapi:///",
                "-f",
                "/tmp/memberof-module.ldif",
            )
            Files.delete(tempFile)

            // Configure memberOf overlay on the database
            val memberOfOverlayLdif = """
                dn: olcOverlay=memberof,olcDatabase={1}mdb,cn=config
                objectClass: olcOverlayConfig
                objectClass: olcMemberOf
                olcOverlay: memberof
                olcMemberOfRefInt: TRUE
                olcMemberOfDangling: ignore
                olcMemberOfGroupOC: groupOfNames
                olcMemberOfMemberAD: member
                olcMemberOfMemberOfAD: memberOf
            """.trimIndent()

            val overlayTempFile = Files.createTempFile("memberof-overlay", ".ldif")
            Files.write(overlayTempFile, memberOfOverlayLdif.toByteArray())
            ldapContainer.copyFileToContainer(
                MountableFile.forHostPath(overlayTempFile),
                "/tmp/memberof-overlay.ldif",
            )

            ldapContainer.execInContainer(
                "ldapadd",
                "-Y",
                "EXTERNAL",
                "-H",
                "ldapi:///",
                "-f",
                "/tmp/memberof-overlay.ldif",
            )
            Files.delete(overlayTempFile)

            // Re-add the user to the group to trigger memberOf update
            val modifyGroupLdif = """
                dn: cn=developers,ou=groups,dc=example,dc=org
                changetype: modify
                delete: member
                member: uid=john.doe,ou=people,dc=example,dc=org
                -
                add: member
                member: uid=john.doe,ou=people,dc=example,dc=org
            """.trimIndent()

            val modifyTempFile = Files.createTempFile("modify-group", ".ldif")
            Files.write(modifyTempFile, modifyGroupLdif.toByteArray())
            ldapContainer.copyFileToContainer(
                MountableFile.forHostPath(modifyTempFile),
                "/tmp/modify-group.ldif",
            )

            ldapContainer.execInContainer(
                "ldapmodify",
                "-x",
                "-D",
                "cn=admin,dc=example,dc=org",
                "-w",
                "admin",
                "-f",
                "/tmp/modify-group.ldif",
            )
            Files.delete(modifyTempFile)
        }

        @JvmStatic
        @DynamicPropertySource
        fun configureLdapProperties(registry: DynamicPropertyRegistry) {
            registry.add("ldap.url") { "ldap://${ldapContainer.host}:${ldapContainer.getMappedPort(389)}" }
            registry.add("ldap.base") { "dc=example,dc=org" }
            registry.add("ldap.principal") { "cn=admin,dc=example,dc=org" }
            registry.add("ldap.password") { "admin" }
            registry.add("ldap.enabled") { "true" }
            registry.add("ldap.userOu") { "people" }
            registry.add("ldap.uniqueIdentifierAttribute") { "uid" }
            registry.add("ldap.emailAttribute") { "mail" }
            registry.add("ldap.fullNameAttribute") { "cn" }
        }
    }

    private var developerRole: Role? = null

    @BeforeEach
    fun setUp() {
        // Add test license (same as SAMLTest)
        val licenseJson = """
            {
                "license_data":{"max_users":10,"expiry_date":"2100-01-01","test_license":true},
                "signature":"E3cqrsVzWccsyWwIeCE2J4Mn/eHyP8j4T05Q4o2dtXH1lhum71rEyPqv9MLn//IcVGsLBY6MwWJGxxa+IBqZTvx0fkLix7e44BRJ5xnV83WzZbKyacNCsNqYEbNpeRcDmtC0pbk7/OSff8VDs5xdqWl7zsI+HA5KNdw878BZKVxusHkHhLtxOhHtbm7Gvcyia4XE86USTWUMYf6aCgNkQgRSOnTo5Zrs+vBUvgSI33l3XyBDx+cQcr9Mell2ytOYrTxQ4zUbRkzcsQtGRTHbh8uXQb5wS389F0zQWSLh7RrCRuaEZ0IDTt8tFkN+72fZ64504bsSR9mNgkgKTv/FvQiVCppKO8vpW0T0hg2xziXMnNSJ3MbihcNlpFsz9C2SEnGm18rQ4UagnLCWTqhz5DtWCxeaAExIT261o6J/wBwlsHHMJRiDaLo/cQOLVOUm43psOt4nlTdbijPoKhBejBuSgqSxTid1R7+8YaFlco/SaprzEspWHcOcVIPUN2jk"
            }
        """.trimIndent()

        val licenseFile = LicenseFile(
            fileContent = licenseJson,
            fileName = "test-license.json",
            createdAt = LocalDateTime.now(),
        )
        licenseAdapter.createLicense(licenseFile)

        // Create test role for mapping
        developerRole = roleAdapter.create(
            Role(
                id = null,
                name = "Developer",
                description = "Developer role for testing",
                policies = emptySet(),
            ),
        )

        // Setup role sync config
        roleSyncConfigAdapter.updateConfig(
            enabled = true,
            syncMode = SyncMode.FULL_SYNC,
            groupsAttribute = "memberOf",
        )

        // Add mapping: developers group -> Developer role
        roleSyncConfigAdapter.addMapping("developers", developerRole!!.getId()!!)
    }

    @AfterEach
    fun cleanup() {
        userAdapter.deleteAll()
        licenseAdapter.deleteAll()
        // Reset role sync config
        roleSyncConfigAdapter.updateConfig(enabled = false)
        // Clean up mappings
        roleSyncConfigAdapter.deleteAllMappings()
        // Only delete the test-created role, not all roles (the default role must remain)
        developerRole?.let { roleAdapter.delete(RoleId(it.getId()!!)) }
        developerRole = null
    }

    @Test
    fun `LDAP login syncs roles from memberOf groups`() {
        // Login via LDAP
        mockMvc.perform(
            post("/login")
                .content(
                    """
                    {
                        "email": "john.doe",
                        "password": "password"
                    }
                    """.trimIndent(),
                )
                .contentType("application/json"),
        ).andExpect(status().isOk)

        // Verify user was created with Developer role
        val user = userAdapter.findByEmail("john.doe@example.org")
        assertThat(user).isNotNull
        assertThat(user?.ldapIdentifier).isEqualTo("john.doe")

        // Check that user has the Developer role (from role sync) and default role
        val roleNames = user?.roles?.map { it.name }
        assertThat(roleNames).contains("Developer")
    }

    @Test
    fun `LDAP login without license skips role sync and user gets default role only`() {
        // Remove the license
        licenseAdapter.deleteAll()

        // Login via LDAP
        mockMvc.perform(
            post("/login")
                .content(
                    """
                    {
                        "email": "john.doe",
                        "password": "password"
                    }
                    """.trimIndent(),
                )
                .contentType("application/json"),
        ).andExpect(status().isOk)

        // Verify user was created
        val user = userAdapter.findByEmail("john.doe@example.org")
        assertThat(user).isNotNull
        assertThat(user?.ldapIdentifier).isEqualTo("john.doe")

        // Check that user only has the default role (role sync was skipped)
        val roleNames = user?.roles?.map { it.name }
        assertThat(roleNames).doesNotContain("Developer")
        // User should have the default role
        assertThat(roleNames).isNotEmpty
    }
}
