// This file is not MIT licensed
package dev.kviklet.kviklet.security.mcp

import dev.kviklet.kviklet.security.CsrfHeaderFilter
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.LicenseService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.json.JsonMapper
import java.net.URI

/**
 * Answers 402 on every MCP and authorization server endpoint unless an enterprise license is
 * installed, before anything else in the chain runs. 402 is what Kviklet's other enterprise features
 * answer without a license too (see EnterpriseFeatureException).
 */
class McpLicenseFilter(private val licenseService: LicenseService) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (licenseService.getActiveLicense() == null) {
            response.sendError(HttpServletResponse.SC_PAYMENT_REQUIRED, "The MCP server requires an enterprise license")
            return
        }
        chain.doFilter(request, response)
    }
}

/**
 * Makes the request look like it arrived on the backend's public URL.
 *
 * The OAuth metadata, the issuer, the token audience and the `resource_metadata` challenge are all
 * absolute URLs built from the request, and an MCP client compares them with the URL it was given.
 * Behind the bundled nginx (`app.in-docker`) that public URL is Kviklet's base URL plus `/api`. A
 * configured `kviklet.baseUrl` is used as is, so it doesn't matter which host, port or scheme the
 * proxies in front of Kviklet pass on; without one it falls back to the request's own origin. The
 * rest of the backend spells out the `/api` prefix where it builds public URLs too (see SamlConfig).
 * Without [publicBackendUrl], e.g. in local development where the backend is reached directly, the
 * request is passed on unchanged.
 *
 * Also exposes the request through [RequestContextHolder], which the MCP security library reads the
 * request from when it validates the token audience.
 */
class McpPublicUrlFilter(private val publicBackendUrl: ((HttpServletRequest) -> String)?) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val publicRequest = publicBackendUrl?.let { PublicRequest(request, URI.create(it(request))) } ?: request
        val previousAttributes = RequestContextHolder.getRequestAttributes()
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(publicRequest, response))
        try {
            chain.doFilter(publicRequest, response)
        } finally {
            RequestContextHolder.setRequestAttributes(previousAttributes)
        }
    }

    private class PublicRequest(request: HttpServletRequest, private val publicUrl: URI) :
        HttpServletRequestWrapper(request) {
        private val pathPrefix = publicUrl.rawPath.orEmpty().trimEnd('/')

        override fun getScheme(): String = publicUrl.scheme

        override fun isSecure(): Boolean = publicUrl.scheme == "https"

        override fun getServerName(): String = publicUrl.host

        override fun getServerPort(): Int = when {
            publicUrl.port != -1 -> publicUrl.port
            publicUrl.scheme == "https" -> 443
            else -> 80
        }

        override fun getContextPath(): String = pathPrefix + super.getContextPath()

        override fun getRequestURI(): String = pathPrefix + super.getRequestURI()

        override fun getRequestURL(): StringBuffer =
            StringBuffer("${publicUrl.scheme}://${publicUrl.rawAuthority}$requestURI")
    }
}

/**
 * Hands the authorization server a principal it can store: the user's id and nothing else.
 *
 * The authorization server persists the authenticated principal of every in-flight authorization as
 * JSON and only deserializes allow-listed types. Kviklet's session principal is not one of them, and
 * the user's roles do not belong in the token store anyway: [McpJwtAuthenticationConverter] loads them
 * fresh for every MCP request. The principal's name becomes the token's `sub`, so tokens are issued
 * for the user id. The replacement only lives for this request; the session keeps its principal.
 */
class McpAuthorizationPrincipalFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val user = SecurityContextHolder.getContext().authentication?.principal as? UserDetailsWithId
        if (user == null) {
            chain.doFilter(request, response)
            return
        }
        val strategy = SecurityContextHolder.getContextHolderStrategy()
        val sessionContext = strategy.context
        val context = strategy.createEmptyContext()
        context.authentication = UsernamePasswordAuthenticationToken.authenticated(user.id, null, emptyList())
        strategy.context = context
        try {
            chain.doFilter(request, response)
        } finally {
            strategy.context = sessionContext
        }
    }
}

/**
 * Lets the Kviklet consent screen submit the user's decision with `fetch`.
 *
 * The decision is a POST to the authorization endpoint, which answers with a redirect back to the MCP
 * client. That POST must carry the `X-Kviklet-Request` header like any other state-changing request
 * from the frontend (see [CsrfHeaderFilter]); a plain form submission from another site could
 * otherwise approve a client in the user's name. Since `fetch` cannot hand a cross-origin redirect to
 * the page, the redirect is turned into a JSON body the page then navigates to.
 */
class McpConsentSubmissionFilter(private val jsonMapper: JsonMapper) : OncePerRequestFilter() {
    private val consentSubmission = PathPatternRequestMatcher.withDefaults().matcher(
        HttpMethod.POST,
        AUTHORIZATION_ENDPOINT,
    )

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !consentSubmission.matches(request)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (request.getHeader(CsrfHeaderFilter.CSRF_HEADER_NAME) == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Missing ${CsrfHeaderFilter.CSRF_HEADER_NAME} header")
            return
        }
        chain.doFilter(request, RedirectAsJsonResponse(response, jsonMapper))
    }

    private class RedirectAsJsonResponse(response: HttpServletResponse, private val jsonMapper: JsonMapper) :
        HttpServletResponseWrapper(response) {
        override fun sendRedirect(location: String) {
            status = HttpServletResponse.SC_OK
            contentType = MediaType.APPLICATION_JSON_VALUE
            writer.write(jsonMapper.writeValueAsString(mapOf("redirectUri" to location)))
            flushBuffer()
        }

        override fun sendRedirect(location: String, clearBuffer: Boolean) = sendRedirect(location)

        override fun sendRedirect(location: String, sc: Int) = sendRedirect(location)

        override fun sendRedirect(location: String, sc: Int, clearBuffer: Boolean) = sendRedirect(location)
    }

    companion object {
        const val AUTHORIZATION_ENDPOINT = "/oauth2/authorize"
    }
}
