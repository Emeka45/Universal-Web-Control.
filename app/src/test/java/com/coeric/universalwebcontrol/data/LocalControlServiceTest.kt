package com.coeric.universalwebcontrol.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalControlServiceTest {
    @Test
    fun exposesExpectedServiceCatalogueAsDisconnected() {
        val modules = LocalControlService().modules()

        assertEquals(
            listOf("workers", "pages", "dns", "domains", "analytics", "ai", "security", "account"),
            modules.map { it.id }
        )
        assertFalse(modules.any { it.available })
    }
}
