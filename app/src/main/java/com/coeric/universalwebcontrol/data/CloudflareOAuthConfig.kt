package com.coeric.universalwebcontrol.data

object CloudflareOAuthConfig {
    const val CLIENT_ID = "REPLACE_WITH_CLOUDFLARE_CLIENT_ID"
    const val REDIRECT_URI = "universalwebcontrol://oauth/callback"
    const val SCOPE = "account.read"
    const val AUTHORIZATION_ENDPOINT = "https://dash.cloudflare.com/oauth2/auth"
    const val TOKEN_ENDPOINT = "https://dash.cloudflare.com/oauth2/token"
    const val USERINFO_ENDPOINT = "https://dash.cloudflare.com/oauth2/userinfo"
}
