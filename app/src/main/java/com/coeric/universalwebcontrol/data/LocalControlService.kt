package com.coeric.universalwebcontrol.data

import com.coeric.universalwebcontrol.model.ServiceModule

class LocalControlService : ControlService {
    override fun modules(): List<ServiceModule> = listOf(
        ServiceModule("workers", "Workers", "Deploy and manage edge applications."),
        ServiceModule("pages", "Pages", "Manage web projects and deployments."),
        ServiceModule("dns", "DNS", "Manage DNS records and zones."),
        ServiceModule("domains", "Domains", "View and manage supported domains."),
        ServiceModule("analytics", "Analytics", "Inspect traffic and service metrics."),
        ServiceModule("ai", "AI", "Access supported AI and model services."),
        ServiceModule("security", "Security", "Review supported security controls."),
        ServiceModule("account", "Account", "Manage connection and account settings.")
    )
}
