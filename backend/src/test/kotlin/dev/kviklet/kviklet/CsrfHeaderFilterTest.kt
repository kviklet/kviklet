package dev.kviklet.kviklet

import dev.kviklet.kviklet.helper.RoleHelper
import dev.kviklet.kviklet.helper.UserHelper
import dev.kviklet.kviklet.security.CsrfHeaderFilter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.session.web.http.SessionRepositoryFilter
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * Tests for the CSRF custom header requirement on the session-authenticated chain.
 *
 * Builds its own MockMvc without the CsrfHeaderTestConfig customizer, so requests
 * do NOT carry the X-Kviklet-Request header unless a test adds it explicitly.
 */
@SpringBootTest
@ActiveProfiles("test")
class CsrfHeaderFilterTest {

    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var userHelper: UserHelper

    @Autowired
    private lateinit var roleHelper: RoleHelper

    private lateinit var mockMvc: MockMvc

    private val createUserJson = """
        {
            "email": "someemail@email.de",
            "password": "123456",
            "fullName": "Some User"
        }
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        // The session repository filter is added manually since only @AutoConfigureMockMvc
        // registers servlet filter beans automatically; without it login sets no SESSION cookie
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters<DefaultMockMvcBuilder>(context.getBean(SessionRepositoryFilter::class.java))
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()
    }

    @AfterEach
    fun tearDown() {
        userHelper.deleteAll()
        roleHelper.deleteAll()
    }

    @Test
    fun `mutating request without csrf header is rejected`() {
        userHelper.createUser(permissions = listOf("*"))
        val cookie = userHelper.login(mockMvc = mockMvc)

        mockMvc.perform(
            post("/users/").cookie(cookie).content(createUserJson).contentType("application/json"),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `mutating request with csrf header succeeds`() {
        userHelper.createUser(permissions = listOf("*"))
        val cookie = userHelper.login(mockMvc = mockMvc)

        mockMvc.perform(
            post("/users/").cookie(cookie).content(createUserJson).contentType("application/json")
                .header(CsrfHeaderFilter.CSRF_HEADER_NAME, "true"),
        ).andExpect(status().isOk)
    }

    @Test
    fun `read request without csrf header succeeds`() {
        userHelper.createUser(permissions = listOf("*"))
        val cookie = userHelper.login(mockMvc = mockMvc)

        mockMvc.perform(get("/users/").cookie(cookie))
            .andExpect(status().isOk)
    }

    @Test
    fun `login endpoint is exempt from csrf header requirement`() {
        userHelper.createUser(permissions = listOf("*"))

        // UserHelper.login posts to /login without the header and asserts a 200
        userHelper.login(mockMvc = mockMvc)
    }

    @Test
    fun `bearer token requests are not subject to the csrf header requirement`() {
        // The API key chain handles Bearer requests: an invalid key is rejected with
        // 401 by ApiKeyAuthFilter, not 403 by CsrfHeaderFilter
        mockMvc.perform(
            post("/users/").content(createUserJson).contentType("application/json")
                .header("Authorization", "Bearer invalid-api-key-12345"),
        ).andExpect(status().isUnauthorized)
    }
}
