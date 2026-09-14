package dev.kviklet.kviklet.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class BaseUrlResolverTest {

    private fun request(scheme: String, host: String, port: Int) = MockHttpServletRequest().apply {
        this.scheme = scheme
        serverName = host
        serverPort = port
    }

    @Test
    fun `a configured base url wins over the request origin`() {
        val resolver = BaseUrlResolver("https://kviklet.example.com/")

        assertThat(resolver.resolve(request("http", "localhost", 8081))).isEqualTo("https://kviklet.example.com")
        assertThat(resolver.resolve()).isEqualTo("https://kviklet.example.com")
    }

    @Test
    fun `without configuration the request origin is used`() {
        val resolver = BaseUrlResolver(null)

        assertThat(resolver.resolve(request("http", "localhost", 8081))).isEqualTo("http://localhost:8081")
        assertThat(
            resolver.resolve(request("https", "kviklet.example.com", 443)),
        ).isEqualTo("https://kviklet.example.com")
        assertThat(resolver.resolve(request("http", "kviklet.example.com", 80))).isEqualTo("http://kviklet.example.com")
    }

    @Test
    fun `a blank configuration counts as unset`() {
        val resolver = BaseUrlResolver("  ")

        assertThat(resolver.resolve(request("http", "localhost", 8081))).isEqualTo("http://localhost:8081")
    }
}
