package com.coeric.universalwebcontrol

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.coeric.universalwebcontrol.data.CloudflareOAuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OAuthCallbackActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val callback = intent?.data ?: run {
            finish()
            return
        }

        scope.launch {
            val result = CloudflareOAuthManager(applicationContext).completeAuthorization(callback)
            result.onFailure {
                Toast.makeText(
                    this@OAuthCallbackActivity,
                    it.message ?: "Cloudflare connection failed.",
                    Toast.LENGTH_LONG
                ).show()
            }.onSuccess {
                Toast.makeText(
                    this@OAuthCallbackActivity,
                    "Cloudflare connected.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            finish()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
