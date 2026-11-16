package app.acbooks.server

import java.net.URI
import java.net.URISyntaxException
import java.net.URL

internal fun serverBaseAddress(input: String): URI? {
    val address = try {
        URI(input.trim())
    } catch (_: URISyntaxException) {
        return null
    }
    if (!address.scheme.equals("https", ignoreCase = true) || address.host.isNullOrEmpty() ||
        address.rawUserInfo != null || address.rawQuery != null || address.rawFragment != null ||
        (address.port != -1 && address.port !in 1..65535)
    ) return null

    val base = address.normalize().toASCIIString().trimEnd('/')
    return URI(base.removeSuffix("/api/v1"))
}

internal enum class ServerEndpoint(val path: String) {
    Health("/api/v1/health"),
    Login("/api/v1/auth/device-login"),
    Account("/api/v1/auth/me"),
    Avatar("/api/v1/auth/me/avatar"),
    Logout("/api/v1/auth/logout"),
}

internal fun URI.endpoint(endpoint: ServerEndpoint): URL = URI(toASCIIString() + endpoint.path).toURL()
