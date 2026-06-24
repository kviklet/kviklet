package dev.kviklet.kviklet.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

class RequestLoggingFilterTest {

    private val filter = RequestLoggingFilter()

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
        MDC.clear()
    }

    /** Captures the MDC values that were visible while the downstream chain ran. */
    private fun runFilter(request: HttpServletRequest, response: HttpServletResponse): Map<String, String?> {
        val captured = mutableMapOf<String, String?>()
        val chain = FilterChain { _, _ ->
            listOf(
                LoggingKeys.REQUEST_ID,
                LoggingKeys.USER_ID,
                LoggingKeys.HTTP_METHOD,
                LoggingKeys.PATH,
            ).forEach { captured[it] = MDC.get(it) }
        }
        filter.doFilter(request, response, chain)
        return captured
    }

    @Test
    fun `generates a request id and echoes it in the response header`() {
        val request = MockHttpServletRequest("GET", "/api/connections")
        val response = MockHttpServletResponse()

        val captured = runFilter(request, response)

        assertThat(captured[LoggingKeys.REQUEST_ID]).isNotBlank()
        assertThat(captured[LoggingKeys.HTTP_METHOD]).isEqualTo("GET")
        assertThat(captured[LoggingKeys.PATH]).isEqualTo("/api/connections")
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(captured[LoggingKeys.REQUEST_ID])
    }

    @Test
    fun `honours an inbound request id`() {
        val request = MockHttpServletRequest("POST", "/api/executions")
        request.addHeader("X-Request-Id", "upstream-correlation-id")
        val response = MockHttpServletResponse()

        val captured = runFilter(request, response)

        assertThat(captured[LoggingKeys.REQUEST_ID]).isEqualTo("upstream-correlation-id")
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("upstream-correlation-id")
    }

    @Test
    fun `adds the authenticated user id to the context`() {
        val principal = UserDetailsWithId("user-42", "user@example.com", "secret", emptyList())
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, emptyList())

        val captured = runFilter(MockHttpServletRequest("GET", "/api/me"), MockHttpServletResponse())

        assertThat(captured[LoggingKeys.USER_ID]).isEqualTo("user-42")
    }

    @Test
    fun `omits the user id when unauthenticated`() {
        val captured = runFilter(MockHttpServletRequest("GET", "/login"), MockHttpServletResponse())

        assertThat(captured[LoggingKeys.USER_ID]).isNull()
    }

    @Test
    fun `clears the mdc after the request completes`() {
        runFilter(MockHttpServletRequest("GET", "/api/connections"), MockHttpServletResponse())

        assertThat(MDC.get(LoggingKeys.REQUEST_ID)).isNull()
        assertThat(MDC.get(LoggingKeys.USER_ID)).isNull()
        assertThat(MDC.get(LoggingKeys.HTTP_METHOD)).isNull()
        assertThat(MDC.get(LoggingKeys.PATH)).isNull()
    }
}
