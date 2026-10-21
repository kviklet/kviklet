package dev.kviklet.kviklet

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private const val API_KEY_SCHEME = "apiKey"

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        // Relative to the spec at /[api/]v3/api-docs, so "Try it out" targets the backend whether or not
        // it sits behind the /api prefix. springdoc's generated server URL drops the prefix and the port.
        .servers(listOf(Server().url("..")))
        .components(
            Components().addSecuritySchemes(
                API_KEY_SCHEME,
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .description(
                        "Kviklet API key (enterprise license required). Without one, the browser session " +
                            "cookie authenticates GET requests; other methods also need the X-Kviklet-Request header.",
                    ),
            ),
        )
        .addSecurityItem(SecurityRequirement().addList(API_KEY_SCHEME))
}
