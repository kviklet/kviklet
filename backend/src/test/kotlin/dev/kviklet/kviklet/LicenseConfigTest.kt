package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.RoleAdapter
import dev.kviklet.kviklet.helper.ExecutionRequestHelper
import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import org.hamcrest.CoreMatchers.`is`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

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

    @Test
    fun `test license config`() {
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

        mockMvc.perform(
            post("/config/").cookie(cookie).contentType(
                "application/json",
            ).content(
                """
               {
                   "licenseKey": "1234567890"
                }
                """.trimIndent(),
            ),
        ).andExpect(status().isOk)

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
