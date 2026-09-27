package com.coeric.universalwebcontrol.data

object CloudflareOAuthConfig {
    // Public OAuth client identifier. Replace this placeholder after creating
    // the Cloudflare OAuth client. Never put a client secret in the APK.
    const val CLIENT_ID = "REPLACE_WITH_CLOUDFLARE_CLIENT_ID"

    const val REDIRECT_URI = "universalwebcontrol://oauth/callback"

    // Keep the first release least-privileged. Additional Cloudflare API
    // permissions will be requested only when their features are implemented.
    const val SCOPE = "account.read"

    const val AUTHORIZATION_ENDPOINT = "https://dash.cloudflare.com/oauth2/auth"
    const val TOKEN_ENDPOINT = "https://dash.cloudflare.com/oauth2/token"
    const val USERINFO_ENDPOINT = "https://dash.cloudflare.com/oauth2/userinfo"
    const val REVOKE_ENDPOINT = "https://dash.cloudflare.com/oauth2/revoke"
    const val LOGOUT_ENDPOINT = "https://dash.cloudflare.com/oauth2/logout"
}
