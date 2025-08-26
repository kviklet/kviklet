package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.service.ApiKeyService
import dev.kviklet.kviklet.service.EntityNotFound
import dev.kviklet.kviklet.service.UserService
import dev.kviklet.kviklet.service.dto.Role
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

private const val API_KEY_HEADER = "Authorization"

@Component
class ApiKeyAuthFilter(
    private val apiKeyService: ApiKeyService,
    private val userService: UserService,
    private val passwordEncoder: PasswordEncoder,
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val apiKey = request.getHeader(API_KEY_HEADER)

        if (apiKey == null) {
            chain.doFilter(request, response)
            return
        }

        try {
            val apiKeyWithoutBearer = apiKey.removePrefix("Bearer").trim()
            val apiKeyDto = apiKeyService.checkKey(apiKeyWithoutBearer)
            if (apiKeyDto == null) {
                chain.doFilter(request, response)
                return
            }

            val apiKeyId = apiKeyDto.id!!

            // Update last used timestamp
            apiKeyService.updateLastUsed(apiKeyId.toString())

            // Get the user associated with this API key
            val user = userService.getUser(apiKeyDto.userId)
            val userId = user.getId() ?: throw EntityNotFound(
                "User not found",
                "User with ID ${apiKeyDto.userId} not found",
            )

            // Create authentication token
            val policies = user.roles.flatMap(Role::policies).map(::PolicyGrantedAuthority)
            val userDetails = UserDetailsWithId(userId, user.email, user.password, policies)
            val authentication = UsernamePasswordAuthenticationToken(userDetails, user.password, policies)
            SecurityContextHolder.getContext().authentication = authentication
        } catch (e: EntityNotFound) {
            logger.error("Error finding API key", e)
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid API key format", e)
        } catch (e: Exception) {
            logger.error("Error authenticating with API key", e)
        }

        chain.doFilter(request, response)
    }
}
