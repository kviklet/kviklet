package dev.kviklet.kviklet.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class LoginRedirectTargetFilterTest {

    private val filter = LoginRedirectTargetFilter()

    private fun run(request: MockHttpServletRequest): MockHttpServletRequest {
        val chain = MockFilterChain()
        filter.doFilter(request, MockHttpServletResponse(), chain)
        assertThat(chain.request).describedAs("filter passes the request on").isNotNull()
        return request
    }

    private fun storedTarget(request: MockHttpServletRequest): String? =
        request.getSession(false)?.getAttribute(LoginRedirectTargetFilter.SESSION_ATTRIBUTE) as String?

    @Test
    fun `stores the target when an oauth2 login is started`() {
        val request = MockHttpServletRequest("GET", "/oauth2/authorization/google")
        request.addParameter("redirect", "/requests/abc?tab=comments#top")

        run(request)

        assertThat(storedTarget(request)).isEqualTo("/requests/abc?tab=comments#top")
    }

    @Test
    fun `stores the target when a saml login is started`() {
        val request = MockHttpServletRequest("GET", "/saml2/authenticate/saml")
        request.addParameter("redirect", "/settings/users")

        run(request)

        assertThat(storedTarget(request)).isEqualTo("/settings/users")
    }

    @Test
    fun `ignores the parameter on other endpoints`() {
        val request = MockHttpServletRequest("GET", "/status")
        request.addParameter("redirect", "/settings/users")

        run(request)

        assertThat(request.getSession(false)).isNull()
    }

    @Test
    fun `does not create a session without a target`() {
        val request = MockHttpServletRequest("GET", "/oauth2/authorization/google")

        run(request)

        assertThat(request.getSession(false)).isNull()
    }

    @Test
    fun `a login without a target forgets an earlier one`() {
        val request = MockHttpServletRequest("GET", "/oauth2/authorization/google")
        request.getSession(true)!!.setAttribute(LoginRedirectTargetFilter.SESSION_ATTRIBUTE, "/settings/users")

        run(request)

        assertThat(storedTarget(request)).isNull()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://evil.example/requests",
            "//evil.example/requests",
            "/\\evil.example",
            "requests/abc",
            "javascript:alert(1)",
            "/requests\r\nSet-Cookie: x=y",
            "",
        ],
    )
    fun `drops targets that could leave the site`(target: String) {
        val request = MockHttpServletRequest("GET", "/oauth2/authorization/google")
        request.addParameter("redirect", target)

        run(request)

        assertThat(storedTarget(request)).isNull()
    }

    @Test
    fun `consume returns the target once`() {
        val request = MockHttpServletRequest()
        request.getSession(true)!!.setAttribute(LoginRedirectTargetFilter.SESSION_ATTRIBUTE, "/new")

        assertThat(LoginRedirectTargetFilter.consume(request)).isEqualTo("/new")
        assertThat(LoginRedirectTargetFilter.consume(request)).isNull()
    }

    @Test
    fun `consume does not create a session`() {
        val request = MockHttpServletRequest()

        assertThat(LoginRedirectTargetFilter.consume(request)).isNull()
        assertThat(request.getSession(false)).isNull()
    }
}
