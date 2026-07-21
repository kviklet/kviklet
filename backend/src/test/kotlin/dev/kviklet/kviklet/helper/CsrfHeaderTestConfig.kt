package dev.kviklet.kviklet.helper

import dev.kviklet.kviklet.security.CsrfHeaderFilter
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders

/**
 * Adds the CSRF header to every MockMvc request by default, mirroring what the
 * frontend does for all API calls. Tests that need to exercise requests without
 * the header (e.g. CsrfHeaderFilterTest) build their own MockMvc instance.
 */
@AutoConfiguration
class CsrfHeaderTestConfig {

    @Bean
    fun csrfHeaderMockMvcCustomizer(): MockMvcBuilderCustomizer = MockMvcBuilderCustomizer { builder ->
        builder.defaultRequest(
            MockMvcRequestBuilders.get("/").header(CsrfHeaderFilter.CSRF_HEADER_NAME, "true"),
        )
    }
}
