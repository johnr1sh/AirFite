package dev.johnrish.pocketengineer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderEndpointTest {
    @Test
    fun normalizesValidHttpsBaseUrl() {
        assertEquals("https://api.example.com/v1", ProviderEndpoint.normalize("  https://api.example.com/v1/  "))
    }

    @Test
    fun acceptsHttpsHostWithoutPath() {
        assertEquals("https://api.example.com", ProviderEndpoint.normalize("https://api.example.com"))
    }

    @Test
    fun rejectsNonHttpsScheme() {
        assertNull(ProviderEndpoint.normalize("http://api.example.com/v1"))
    }

    @Test
    fun rejectsCredentialBearingUrl() {
        assertNull(ProviderEndpoint.normalize("https://user:password@api.example.com/v1"))
    }

    @Test
    fun rejectsQueryAndFragment() {
        assertNull(ProviderEndpoint.normalize("https://api.example.com/v1?key=value"))
        assertNull(ProviderEndpoint.normalize("https://api.example.com/v1#fragment"))
    }

    @Test
    fun rejectsMissingHostAndInvalidPort() {
        assertNull(ProviderEndpoint.normalize("https:///v1"))
        assertNull(ProviderEndpoint.normalize("https://api.example.com:99999/v1"))
    }

    @Test
    fun rejectsWhitespaceAndMalformedUrls() {
        assertNull(ProviderEndpoint.normalize("https://api example.com/v1"))
        assertNull(ProviderEndpoint.normalize("not a url"))
    }
}
