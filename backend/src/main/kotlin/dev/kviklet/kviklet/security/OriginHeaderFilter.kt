import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import java.util.*

class OriginHeaderFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // Removing the Origin header from the request to prevent CORS issues when hosting behind proxies.
        // Especially using TCP level proxies, wont set any redirect headers, so spring cant know the correct origin to compare.
        // The spring security feature of comparing the origin header to the request origin, is a spring security special feature
        // and not a standard CORS feature, so it is safe to disable this. As long as the response headers are set correctly
        // The browser will still enforce the CORS policy.
        val modifiedRequest = object : HttpServletRequestWrapper(request) {
            override fun getHeader(name: String): String? =
                if (name.equals("Origin", ignoreCase = true)) null else super.getHeader(name)

            override fun getHeaders(name: String): Enumeration<String> = if (name.equals("Origin", ignoreCase = true)) {
                Collections.enumeration(listOf())
            } else {
                super.getHeaders(name)
            }
        }
        filterChain.doFilter(modifiedRequest, response)
    }
}
