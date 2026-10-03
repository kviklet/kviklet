// This file is not MIT licensed
package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.service.ApiKeyService
import dev.kviklet.kviklet.service.EntityNotFound
import dev.kviklet.kviklet.service.dto.Role
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

private const val API_KEY_HEADER = "Authorization"

@Component
class ApiKeyAuthFilter(
    private val apiKeyService: ApiKeyService,
    private val passwordEncoder: PasswordEncoder,
    private val eventStreamingService: dev.kviklet.kviklet.service.EventStreamingService? = null,
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val apiKey = request.getHeader(API_KEY_HEADER)

        // Only process if we have an Authorization header that looks like an API key
        if (apiKey == null || !apiKey.startsWith("Bearer ")) {
            chain.doFilter(request, response)
            return
        }

        try {
            val apiKeyWithoutBearer = apiKey.removePrefix("Bearer").trim()
            val apiKeyDto = apiKeyService.checkKey(apiKeyWithoutBearer)
            if (apiKeyDto == null) {
                eventStreamingService?.emit(
                    "authentication.api_key",
                    "authentication",
                    "failure",
                    SecurityEventRequestFilter.fields(request) + mapOf("reason" to "invalid_or_expired"),
                    authentication = null,
                )
                chain.doFilter(request, response)
                return
            }

            val apiKeyId = apiKeyDto.id!!

            // Update last used timestamp
            apiKeyService.updateLastUsed(apiKeyId.toString())

            // Get the user associated with this API key
            val user = apiKeyDto.user
            val userId = user.getId() ?: throw EntityNotFound(
                "User not found",
                "User not found for API key",
            )

            // Create authentication token
            val policies = user.roles.flatMap(Role::policies).map(::PolicyGrantedAuthority)
            val userDetails = UserDetailsWithId(
                userId,
                user.email,
                user.password ?: "default password",
                policies,
            )
            val authentication = ApiKeyAuthentication(userDetails, policies)

            // Set authentication in context
            SecurityContextHolder.getContext().authentication = authentication
        } catch (e: EntityNotFound) {
            eventStreamingService?.emit(
                "authentication.api_key",
                "authentication",
                "failure",
                SecurityEventRequestFilter.fields(request) + mapOf("reason" to "authentication_failed"),
                authentication = null,
            )
            logger.warn("Error finding user for API key: ${e.message}")
        } catch (e: IllegalArgumentException) {
            eventStreamingService?.emit(
                "authentication.api_key",
                "authentication",
                "failure",
                SecurityEventRequestFilter.fields(request) + mapOf("reason" to "authentication_failed"),
                authentication = null,
            )
            logger.debug("Invalid API key format: ${e.message}")
        } catch (e: Exception) {
            eventStreamingService?.emit(
                "authentication.api_key",
                "authentication",
                "failure",
                SecurityEventRequestFilter.fields(request) + mapOf("reason" to "authentication_failed"),
                authentication = null,
            )
            logger.error("Error authenticating with API key", e)
        }

        chain.doFilter(request, response)
    }
}
