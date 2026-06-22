package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.LicenseAdapter
import dev.kviklet.kviklet.db.RoleAdapter
import dev.kviklet.kviklet.db.RoleSyncConfigAdapter
import dev.kviklet.kviklet.service.RoleSyncService
import dev.kviklet.kviklet.service.dto.LicenseFile
import dev.kviklet.kviklet.service.dto.Role
import dev.kviklet.kviklet.service.dto.RoleId
import dev.kviklet.kviklet.service.dto.SyncMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Verifies that a single IdP group can map to multiple Kviklet roles (#479), and that on
 * login a user receives the union of all roles mapped to their groups (#478).
 */
@SpringBootTest
@ActiveProfiles("test")
class RoleSyncMappingTest {

    @Autowired
    private lateinit var roleSyncConfigAdapter: RoleSyncConfigAdapter

    @Autowired
    private lateinit var roleAdapter: RoleAdapter

    @Autowired
    private lateinit var roleSyncService: RoleSyncService

    @Autowired
    private lateinit var licenseAdapter: LicenseAdapter

    private val createdRoleIds = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        // Clear any licenses left over from other tests (the test database is reused), so that
        // getActiveLicense() doesn't trip over an unrelated invalid license during resolveRoles.
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
            LicenseFile(
                fileContent = licenseJson,
                fileName = "test-license.json",
                createdAt = LocalDateTime.now(),
            ),
        )

        roleSyncConfigAdapter.updateConfig(
            enabled = true,
            syncMode = SyncMode.FULL_SYNC,
            groupsAttribute = "groups",
        )
    }

    @AfterEach
    fun tearDown() {
        roleSyncConfigAdapter.deleteAllMappings()
        roleSyncConfigAdapter.updateConfig(enabled = false)
        licenseAdapter.deleteAll()
        createdRoleIds.forEach { roleAdapter.delete(RoleId(it)) }
        createdRoleIds.clear()
    }

    private fun createRole(name: String): Role {
        val role = roleAdapter.create(
            Role(
                id = null,
                name = name,
                description = "$name role for testing",
                policies = emptySet(),
            ),
        )
        createdRoleIds.add(role.getId()!!)
        return role
    }

    @Test
    fun `a single IdP group can be mapped to multiple roles`() {
        val developer = createRole("Developer")
        val reviewer = createRole("Reviewer")

        // Before #479 the second mapping threw a unique-constraint violation.
        roleSyncConfigAdapter.addMapping("platform-team", developer.getId()!!)
        roleSyncConfigAdapter.addMapping("platform-team", reviewer.getId()!!)

        val mappings = roleSyncConfigAdapter.getMappingsByGroupNames(listOf("platform-team"))

        assertThat(mappings).hasSize(2)
        assertThat(mappings.map { it.roleName }).containsExactlyInAnyOrder("Developer", "Reviewer")
    }

    @Test
    @Transactional // resolveRoles maps lazy role policies; keep the Hibernate session open
    fun `on login a user receives the union of all roles mapped to their group`() {
        val developer = createRole("Developer")
        val reviewer = createRole("Reviewer")
        roleSyncConfigAdapter.addMapping("platform-team", developer.getId()!!)
        roleSyncConfigAdapter.addMapping("platform-team", reviewer.getId()!!)

        val resolved = roleSyncService.resolveRoles(
            idpGroups = listOf("platform-team"),
            existingRoles = emptySet(),
            isNewUser = true,
        )

        assertThat(resolved.map { it.name }).contains("Developer", "Reviewer")
    }

    @Test
    fun `the same group-role pair cannot be added twice`() {
        val developer = createRole("Developer")
        roleSyncConfigAdapter.addMapping("platform-team", developer.getId()!!)

        val secondInsert = runCatching {
            roleSyncConfigAdapter.addMapping("platform-team", developer.getId()!!)
        }

        assertThat(secondInsert.isFailure).isTrue()
    }
}
