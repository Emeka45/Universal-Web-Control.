package com.coeric.universalwebcontrol.data

import android.content.Context
import com.coeric.universalwebcontrol.model.ServiceModule

class CloudflareControlService(context: Context) : ControlService {
    private val oauth = CloudflareOAuthManager(context.applicationContext)

    override fun modules(): List<ServiceModule> {
        val connected = oauth.isConnected()
        return listOf(
            ServiceModule("workers", "Workers", "Deploy and manage edge applications.", connected),
            ServiceModule("pages", "Pages", "Manage web projects and deployments.", connected),
            ServiceModule("dns", "DNS", "Manage DNS records and zones.", connected),
            ServiceModule("domains", "Domains", "View and manage supported domains.", connected),
            ServiceModule("analytics", "Analytics", "Inspect traffic and service metrics.", connected),
            ServiceModule("ai", "AI", "Access supported AI and model services.", connected),
            ServiceModule("security", "Security", "Review supported security controls.", connected),
            ServiceModule("account", "Account", "Manage connection and account settings.", connected)
        )
    }

    fun isConfigured() = oauth.isConfigured()
    fun isConnected() = oauth.isConnected()
    fun beginAuthorization(): Result<Unit> = oauth.beginAuthorization()
    fun disconnect() = oauth.disconnect()
    fun session() = oauth.session()
}
