package dev.kviklet.kviklet.helper

import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.WebRequest
import com.gargoylesoftware.htmlunit.WebResponse
import com.gargoylesoftware.htmlunit.WebResponseData
import com.gargoylesoftware.htmlunit.util.NameValuePair
import com.gargoylesoftware.htmlunit.util.WebConnectionWrapper

/**
 * Answers requests to the frontend dev server (localhost:5173) with an empty page, so the
 * final redirect of an SSO login can be asserted without a running frontend. Installs itself
 * as the client's connection.
 */
class FrontendStub(webClient: WebClient) : WebConnectionWrapper(webClient) {

    var lastFrontendUrl: String? = null

    override fun getResponse(request: WebRequest): WebResponse {
        if (request.url.port == 5173) {
            lastFrontendUrl = request.url.toString()
            val data = WebResponseData(
                "<html><body></body></html>".toByteArray(),
                200,
                "OK",
                listOf(NameValuePair("Content-Type", "text/html")),
            )
            return WebResponse(data, request, 0)
        }
        return super.getResponse(request)
    }
}
