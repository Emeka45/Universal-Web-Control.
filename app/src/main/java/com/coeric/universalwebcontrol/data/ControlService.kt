package com.coeric.universalwebcontrol.data

import com.coeric.universalwebcontrol.model.ServiceModule

interface ControlService {
    fun modules(): List<ServiceModule>
}
