// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.core

import java.net.URLDecoder

// Parses a connection's additionalOptions -- the query-string tail of its JDBC URL ("?a=b&c=d") -- into a
// map, so a ProxyProtocol can pick out the options its upstream driver should honor. Values are URL-decoded
// like the JDBC drivers themselves decode them; a malformed entry (no '=', or an undecodable value) is
// skipped rather than failing the parse, because the full string still reaches the real JDBC executor
// unchanged and only the allowlisted keys matter here.
fun parseAdditionalOptions(additionalOptions: String): Map<String, String> {
    val params = additionalOptions.trim().removePrefix("?")
    if (params.isEmpty()) return emptyMap()
    return params.split("&")
        .mapNotNull { param ->
            val separator = param.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val key = param.substring(0, separator).trim()
            val value = urlDecodeOrRaw(param.substring(separator + 1).trim())
            if (key.isEmpty()) null else key to value
        }
        .toMap()
}

private fun urlDecodeOrRaw(value: String): String = try {
    URLDecoder.decode(value, Charsets.UTF_8)
} catch (e: IllegalArgumentException) {
    value
}
