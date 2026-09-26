package com.coeric.universalwebcontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.coeric.universalwebcontrol.data.CloudflareControlService
import com.coeric.universalwebcontrol.ui.UniversalWebControlApp
import com.coeric.universalwebcontrol.ui.theme.UniversalWebControlTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val service = CloudflareControlService(this)
        setContent {
            UniversalWebControlTheme {
                UniversalWebControlApp(service)
            }
        }
    }
}
