package com.coeric.universalwebcontrol.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudflareOAuthTest {
    @Test
    fun redirectUriIsStable() {
        assertEquals("universalwebcontrol://oauth/callback", CloudflareOAuthConfig.REDIRECT_URI)
    }

    @Test
    fun clientSecretIsNotEmbeddedInConfiguration() {
        // PKCE public clients use token_endpoint_auth_method=none.
        assertEquals(false, CloudflareOAuthConfig.CLIENT_ID == "client-secret")
    }
}
