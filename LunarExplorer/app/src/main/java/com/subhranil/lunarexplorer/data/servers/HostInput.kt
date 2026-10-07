package com.subhranil.lunarexplorer.data.servers

import java.net.URI

/**
 * Turns what the user typed ("192.168.1.20", "files.example.com", "https://x:8443/") plus an
 * optional port into a base URL like `http://192.168.1.20:1234`. Returns null if it isn't valid.
 */
fun normalizeBaseUrl(hostInput: String, portInput: String): String? {
    val raw = hostInput.trim().trimEnd('/')
    if (raw.isBlank() || raw.any { it.isWhitespace() }) return null

    val hasScheme = raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)
    if (!hasScheme && "://" in raw) return null
    val uri = runCatching { URI(if (hasScheme) raw else "http://$raw") }.getOrNull() ?: return null

    val scheme = uri.scheme?.lowercase() ?: return null
    val host = uri.host ?: return null
    if (!uri.rawPath.isNullOrEmpty() || uri.rawQuery != null || uri.rawFragment != null) return null

    val port = if (portInput.isBlank()) {
        uri.port.takeIf { it in 1..65535 }
    } else {
        portInput.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    }
    val hostPart = if (':' in host && !host.startsWith("[")) "[$host]" else host
    return if (port != null) "$scheme://$hostPart:$port" else "$scheme://$hostPart"
}

/** Splits a saved base URL back into the editor's host and port fields. */
fun splitBaseUrl(baseUrl: String): Pair<String, String> {
    val uri = runCatching { URI(baseUrl) }.getOrNull() ?: return baseUrl to ""
    val host = if (uri.scheme == "https") "https://${uri.host}" else uri.host.orEmpty()
    val port = uri.port.takeIf { it > 0 }?.toString().orEmpty()
    return host to port
}
