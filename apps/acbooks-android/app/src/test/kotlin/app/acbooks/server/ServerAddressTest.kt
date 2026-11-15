package app.acbooks.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerAddressTest {
    @Test
    fun preservesReverseProxyPathAndPort() {
        val address = requireNotNull(serverBaseAddress(" https://books.example.com:8443/library/api/v1/ "))
        assertEquals("https://books.example.com:8443/library/api/v1/auth/device-login",
            address.endpoint(ServerEndpoint.Login).toExternalForm())
        assertEquals("https://books.example.com:8443/library/api/v1/auth/me",
            address.endpoint(ServerEndpoint.Account).toExternalForm())
    }

    @Test
    fun rejectsInvalidOrUnsafeAddressParts() {
        listOf(
            "books.example.com",
            "http://books.example.com",
            "https://name:password@books.example.com",
            "https://books.example.com?token=secret",
            "https://books.example.com#fragment",
            "https://books.example.com:65536",
            "https:///api/v1",
        ).forEach { assertNull(it, serverBaseAddress(it)) }
    }
}
