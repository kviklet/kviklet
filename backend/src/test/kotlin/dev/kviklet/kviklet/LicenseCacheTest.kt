package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.LicenseAdapter
import dev.kviklet.kviklet.service.LicenseService
import dev.kviklet.kviklet.service.dto.LicenseFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
class LicenseCacheTest {

    @Autowired private lateinit var licenseService: LicenseService

    @Autowired private lateinit var licenseAdapter: LicenseAdapter

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @AfterEach
    fun tearDown() {
        licenseAdapter.deleteAll()
    }

    @Test
    fun `a valid license is cached instead of being read again on every check`() {
        installLicense()
        assertThat(licenseService.getActiveLicense()).isNotNull

        // Behind the adapter's back, so only a cache can still know the license.
        jdbcTemplate.update("DELETE FROM license")

        assertThat(licenseService.getActiveLicense()).isNotNull
    }

    @Test
    fun `without a valid license every check reads the licenses again`() {
        assertThat(licenseService.getActiveLicense()).isNull()

        // Behind the adapter's back too, like an upload on another instance.
        jdbcTemplate.update(
            "INSERT INTO license (id, file_content, file_name, created_at) VALUES ('other-instance', ?, 'l.json', ?)",
            LICENSE_JSON,
            LocalDateTime.now(),
        )

        assertThat(licenseService.getActiveLicense()).isNotNull
    }

    @Test
    fun `changing the licenses drops the cached one`() {
        installLicense()
        assertThat(licenseService.getActiveLicense()).isNotNull

        licenseAdapter.deleteAll()

        assertThat(licenseService.getActiveLicense()).isNull()
    }

    private fun installLicense() {
        licenseAdapter.createLicense(
            LicenseFile(fileContent = LICENSE_JSON, fileName = "test-license.json", createdAt = LocalDateTime.now()),
        )
    }

    companion object {
        // Same test license as in McpOAuthTest
        private val LICENSE_JSON = """
            {
                "license_data":{"max_users":2,"expiry_date":"2100-01-01","test_license":true},
                "signature":"E3cqrsVzWccsyWwIeCE2J4Mn/eHyP8j4T05Q4o2dtXH1lhum71rEyPqv9MLn//IcVGsLBY6MwWJGxxa+IBqZTvx0fkLix7e44BRJ5xnV83WzZbKyacNCsNqYEbNpeRcDmtC0pbk7/OSff8VDs5xdqWl7zsI+HA5KNdw878BZKVxusHkHhLtxOhHtbm7Gvcyia4XE86USTWUMYf6aCgNkQgRSOnTo5Zrs+vBUvgSI33l3XyBDx+cQcr9Mell2ytOYrTxQ4zUbRkzcsQtGRTHbh8uXQb5wS389F0zQWSLh7RrCRuaEZ0IDTt8tFkN+72fZ64504bsSR9mNgkgKTv/FvQiVCppKO8vpW0T0hg2xziXMnNSJ3MbihcNlpFsz9C2SEnGm18rQ4UagnLCWTqhz5DtWCxeaAExIT261o6J/wBwlsHHMJRiDaLo/cQOLVOUm43psOt4nlTdbijPoKhBejBuSgqSxTid1R7+8YaFlco/SaprzEspWHcOcVIPUN2jk"
            }
        """.trimIndent()
    }
}
