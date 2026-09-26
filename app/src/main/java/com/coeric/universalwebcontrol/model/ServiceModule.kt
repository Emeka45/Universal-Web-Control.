package com.coeric.universalwebcontrol.model

data class ServiceModule(
    val id: String,
    val name: String,
    val description: String,
    val available: Boolean = false
)
