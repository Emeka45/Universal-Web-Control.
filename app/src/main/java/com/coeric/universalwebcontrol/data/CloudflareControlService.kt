package com.coeric.universalwebcontrol.data

import android.content.Context
import com.coeric.universalwebcontrol.model.ServiceModule

class CloudflareControlService(context: Context) : ControlService {
    private val api = CloudflareOAuthManager(context.applicationContext)

    override fun modules(): List<ServiceModule> {
        val connected = api.isConnected()
        return listOf(
            ServiceModule("workers", "Workers", "Deploy and manage edge applications.", connected),
            ServiceModule("pages", "Pages", "Manage web projects and deployments.", connected),
            ServiceModule("dns", "DNS", "Read and manage DNS records.", connected),
            ServiceModule("domains", "Domains", "View and manage supported domains.", connected),
            ServiceModule("analytics", "Analytics", "Inspect traffic and service metrics.", connected),
            ServiceModule("ai", "AI", "Access supported AI and model services.", connected),
            ServiceModule("security", "Security", "Review supported security controls.", connected),
            ServiceModule("account", "Account", "Manage connection and account settings.", connected)
        )
    }

    fun isConfigured() = api.isConfigured()
    fun isConnected() = api.isConnected()
    suspend fun connectWithApiToken(token: String) = api.connectWithApiToken(token)
    suspend fun verifyStoredToken() = api.verifyStoredToken()
    suspend fun listZones() = api.listZones()
    fun disconnect() = api.disconnectLocally()
    suspend fun revoke() = api.revoke()
    fun session() = api.session()
}
