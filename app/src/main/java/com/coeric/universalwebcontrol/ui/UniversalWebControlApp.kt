package com.coeric.universalwebcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.coeric.universalwebcontrol.data.CloudflareControlService
import com.coeric.universalwebcontrol.model.ServiceModule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalWebControlApp(service: CloudflareControlService) {
    var selected by remember { mutableStateOf<ServiceModule?>(null) }
    var connected by remember { mutableStateOf(service.isConnected()) }
    var message by remember { mutableStateOf<String?>(null) }
    val modules = service.modules()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                connected = service.isConnected()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Universal Web Control")
                        Text(
                            "Cloudflare control bridge",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Cloudflare connection", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (connected) "Connected" else "Not connected",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            AssistChip(
                                onClick = {
                                    if (connected) {
                                        service.disconnect()
                                        connected = false
                                    } else {
                                        service.beginAuthorization().onFailure {
                                            message = it.message ?: "Unable to start Cloudflare authorization."
                                        }
                                    }
                                },
                                label = { Text(if (connected) "Disconnect" else "Connect") }
                            )
                        }
                        Text(
                            if (connected) {
                                service.session()?.name?.let { "Signed in as $it" }
                                    ?: service.session()?.email?.let { "Signed in as $it" }
                                    ?: "Cloudflare authorization is active."
                            } else if (service.isConfigured()) {
                                "Sign in with Cloudflare to enable live controls."
                            } else {
                                "OAuth client setup is required before the Connect button can start authorization."
                            },
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Text("Services", style = MaterialTheme.typography.titleLarge)

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 145.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(modules) { module ->
                        ServiceCard(module) { selected = module }
                    }
                }
            }
        }
    }

    selected?.let { module ->
        ServiceDetail(module = module, onDismiss = { selected = null })
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("Connection") },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { message = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun ServiceCard(module: ServiceModule, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(module.name, style = MaterialTheme.typography.titleMedium)
            Text(
                module.description,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                if (module.available) "Connected" else "Requires connection",
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun ServiceDetail(module: ServiceModule, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(module.name) },
        text = {
            Text(
                if (module.available) {
                    "Cloudflare authorization is active for this service catalogue."
                } else {
                    "Connect your Cloudflare account to enable live controls."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
