package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.LicenseAdapter
import dev.kviklet.kviklet.db.RoleAdapter
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.service.dto.LicenseFile
import org.hamcrest.CoreMatchers.`is`
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LicenseConfigTest {

    @Autowired
    private lateinit var roleAdapter: RoleAdapter

    @Autowired
    private lateinit var userHelper: UserHelper

    @Autowired
    private lateinit var roleHelper: RoleHelper

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var executionRequestHelper: ExecutionRequestHelper

    @Autowired
    lateinit var licenseAdapter: LicenseAdapter

    @AfterEach
    fun cleanup() {
        userHelper.deleteAll()
        licenseAdapter.deleteAll()
    }

    @Test
    fun `the config shows the license in force, not a newer expired one`() {
        userHelper.createUser(permissions = listOf("*"))
        val cookie = userHelper.login(mockMvc = mockMvc)
        val now = LocalDateTime.now()
        licenseAdapter.createLicense(
            LicenseFile(
                fileContent = """{"license_data":{"max_users":2,"expiry_date":"2100-01-01","test_license":true},""" +
                    """"signature":"E3cqrsVzWccsyWwIeCE2J4Mn/eHyP8j4T05Q4o2dtXH1lhum71rEyPqv9MLn//IcVGsLBY6MwWJGxxa+IBqZTvx0fkLix7e44BRJ5xnV83WzZbKyacNCsNqYEbNpeRcDmtC0pbk7/OSff8VDs5xdqWl7zsI+HA5KNdw878BZKVxusHkHhLtxOhHtbm7Gvcyia4XE86USTWUMYf6aCgNkQgRSOnTo5Zrs+vBUvgSI33l3XyBDx+cQcr9Mell2ytOYrTxQ4zUbRkzcsQtGRTHbh8uXQb5wS389F0zQWSLh7RrCRuaEZ0IDTt8tFkN+72fZ64504bsSR9mNgkgKTv/FvQiVCppKO8vpW0T0hg2xziXMnNSJ3MbihcNlpFsz9C2SEnGm18rQ4UagnLCWTqhz5DtWCxeaAExIT261o6J/wBwlsHHMJRiDaLo/cQOLVOUm43psOt4nlTdbijPoKhBejBuSgqSxTid1R7+8YaFlco/SaprzEspWHcOcVIPUN2jk"}""",
                fileName = "valid.json",
                createdAt = now,
            ),
        )
        licenseAdapter.createLicense(
            LicenseFile(
                fileContent = """{"license_data":{"max_users":20,"expiry_date":"2024-01-01"},""" +
                    """"signature":"VWT9Bo5HuN4Y+bkT6FK7EtrCXavJhyI+Tk62FnfbzkJaqyOOAh3qnyFMGz12enLQjmMLIwaqyoogjkOeJXezyfEcJRFDTadk3IdXYMZWz8MPMHVHbGmvA18+5vJRZ1cXnNClEkHg3aanbNgtRNlFzp2fen5UVUCAwn/oI0MlQqxwR6qyi2ZST5v0s1PhhI20Byo7gjCDMjOtpWSsn0rTLeDsADS7WR5/26+VhHvt3s1AQlJ6n0gOGOWBruluZkO/NOqIqF2y9NSufJjuF8WkbJt9JxhmPApScMa+nCuqDgDRXIAQa0I65xCwMWixDMz9S/vLb5XlqlJZhGJnC1KKMUzOB87wFtVFmrkhvxSaOH/ejX8rrTDMKyG9ztlKZf+qiff0nZ7GWkHltmc+YLQPoBJt39aDrpW75/2GOQKWLHjwcaB45EPzEv7Tsz4gG5eqWG4kMG3UigJKrEQwoUp10LEMH0YIOFO9WAcjNQB7RpND91VvDESsBKUbwtS/J3rG"}""",
                fileName = "expired.json",
                createdAt = now.plusMinutes(1),
            ),
        )

        mockMvc.perform(get("/config/").cookie(cookie))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.licenseValid", `is`(true)))
            .andExpect(jsonPath("$.validUntil", `is`("2100-01-01")))
            .andExpect(jsonPath("$.allowedUsers", `is`(2)))
    }

    @Test
    fun `test upload license file on valid date`() {
        val date = LocalDate.of(2023, 1, 1)
        mockStatic(LocalDate::class.java, Mockito.CALLS_REAL_METHODS).use {
            `when`(LocalDate.now()).thenReturn(date)

            userHelper.createUser(permissions = listOf("*"))
            val cookie = userHelper.login(mockMvc = mockMvc)

            mockMvc.perform(
                get("/status").cookie(cookie).contentType(
                    "application/json",
                ),
            ).andExpect(status().isOk)
                .andExpect(
                    jsonPath("$.licenseValid", `is`(false)),
                )

            val fileContent = """{"license_data":{"max_users":20,"expiry_date":"2024-01-01"},
            |"signature": "VWT9Bo5HuN4Y+bkT6FK7EtrCXavJhyI+Tk62FnfbzkJaqyOOAh3qnyFMGz12enLQjmMLIwaqyoogjkOeJXezyfEcJRFDTadk3IdXYMZWz8MPMHVHbGmvA18+5vJRZ1cXnNClEkHg3aanbNgtRNlFzp2fen5UVUCAwn/oI0MlQqxwR6qyi2ZST5v0s1PhhI20Byo7gjCDMjOtpWSsn0rTLeDsADS7WR5/26+VhHvt3s1AQlJ6n0gOGOWBruluZkO/NOqIqF2y9NSufJjuF8WkbJt9JxhmPApScMa+nCuqDgDRXIAQa0I65xCwMWixDMz9S/vLb5XlqlJZhGJnC1KKMUzOB87wFtVFmrkhvxSaOH/ejX8rrTDMKyG9ztlKZf+qiff0nZ7GWkHltmc+YLQPoBJt39aDrpW75/2GOQKWLHjwcaB45EPzEv7Tsz4gG5eqWG4kMG3UigJKrEQwoUp10LEMH0YIOFO9WAcjNQB7RpND91VvDESsBKUbwtS/J3rG"}
            """.trimMargin()
            val mockFile = MockMultipartFile(
                "file",
                "license.json",
                "application/json",
                fileContent.toByteArray(),
            )

            mockMvc.perform(multipart("/config/license/").file(mockFile).cookie(cookie))
                .andExpect(status().isOk)

            mockMvc.perform(
                get("/status").cookie(cookie).contentType(
                    "application/json",
                ),
            ).andExpect(status().isOk)
                .andExpect(
                    jsonPath("$.licenseValid", `is`(true)),
                )
        }
    }

    @Test
    fun `test upload license file on invalid date`() {
        userHelper.createUser(permissions = listOf("*"))
        val cookie = userHelper.login(mockMvc = mockMvc)

        mockMvc.perform(
            get("/status").cookie(cookie).contentType(
                "application/json",
            ),
        ).andExpect(status().isOk)
            .andExpect(
                jsonPath("$.licenseValid", `is`(false)),
            )

        val fileContent = """{"license_data":{"max_users":20,"expiry_date":"2024-01-01"},
            |"signature": "VWT9Bo5HuN4Y+bkT6FK7EtrCXavJhyI+Tk62FnfbzkJaqyOOAh3qnyFMGz12enLQjmMLIwaqyoogjkOeJXezyfEcJRFDTadk3IdXYMZWz8MPMHVHbGmvA18+5vJRZ1cXnNClEkHg3aanbNgtRNlFzp2fen5UVUCAwn/oI0MlQqxwR6qyi2ZST5v0s1PhhI20Byo7gjCDMjOtpWSsn0rTLeDsADS7WR5/26+VhHvt3s1AQlJ6n0gOGOWBruluZkO/NOqIqF2y9NSufJjuF8WkbJt9JxhmPApScMa+nCuqDgDRXIAQa0I65xCwMWixDMz9S/vLb5XlqlJZhGJnC1KKMUzOB87wFtVFmrkhvxSaOH/ejX8rrTDMKyG9ztlKZf+qiff0nZ7GWkHltmc+YLQPoBJt39aDrpW75/2GOQKWLHjwcaB45EPzEv7Tsz4gG5eqWG4kMG3UigJKrEQwoUp10LEMH0YIOFO9WAcjNQB7RpND91VvDESsBKUbwtS/J3rG"}
        """.trimMargin()
        val mockFile = MockMultipartFile(
            "file",
            "license.json",
            "application/json",
            fileContent.toByteArray(),
        )

        mockMvc.perform(multipart("/config/license/").file(mockFile).cookie(cookie))
            .andExpect(status().isOk)

        mockMvc.perform(
            get("/status").cookie(cookie).contentType(
                "application/json",
            ),
        ).andExpect(status().isOk)
            .andExpect(
                jsonPath("$.licenseValid", `is`(false)),
            )
    }

    @Test
    fun `test upload invalid license file`() {
        userHelper.createUser(permissions = listOf("*"))
        val cookie = userHelper.login(mockMvc = mockMvc)

        val fileContent = "{\"license_data\": {\"max_users\": 20, \"expiry_date\": \"2024-01-01\"}," +
            " \"signature\": \"somesignature\"}"
        val mockFile = MockMultipartFile(
            "file",
            "license.json",
            "application/json",
            fileContent.toByteArray(),
        )

        mockMvc.perform(multipart("/config/license/").file(mockFile).cookie(cookie))
            .andExpect(status().isBadRequest)
    }
}
