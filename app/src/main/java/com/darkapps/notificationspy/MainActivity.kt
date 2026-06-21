package com.darkapps.notificationspy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.darkapps.notificationspy.ui.theme.NotificationSpyTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
                .contains(packageName)
        ) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        handleDeepLink(intent)
        enableEdgeToEdge()
        setContent {
            NotificationSpyTheme {
                MainScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        if (intent.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        if (uri.scheme == "notificationspy" && uri.host == "webhook") {
            uri.getQueryParameter("url")?.takeIf { it.isNotBlank() }?.let { url ->
                vm.proposeWebhookUrl(url)
            }
        }
    }
}

private enum class Tab { HISTORY, APPS, SETTINGS }

@Composable
fun MainScreen(vm: AppViewModel = viewModel()) {
    val webhookUrl by vm.webhookUrl.collectAsState()
    val pendingWebhookUrl by vm.pendingWebhookUrl.collectAsState()
    var showQrScanner by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (webhookUrl.isBlank()) {
            SetupScreen(vm, onScanQr = { showQrScanner = true })
        } else {
            TabbedScreen(vm, onScanQr = { showQrScanner = true })
        }

        if (showQrScanner) {
            QrScanScreen(
                onQrDetected = { raw ->
                    showQrScanner = false
                    parseWebhookFromQr(raw)?.let { vm.proposeWebhookUrl(it) }
                },
                onDismiss = { showQrScanner = false }
            )
        }
    }

    pendingWebhookUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { vm.discardPendingWebhookUrl() },
            title = { Text("Set webhook?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The scanned QR code contains the following webhook URL:")
                    Text(
                        url,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                Button(onClick = { vm.confirmPendingWebhookUrl() }) {
                    Text("Set")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.discardPendingWebhookUrl() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun parseWebhookFromQr(raw: String): String? {
    if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
    return runCatching {
        val uri = Uri.parse(raw)
        if (uri.scheme == "notificationspy" && uri.host == "webhook") {
            uri.getQueryParameter("url")?.takeIf { it.isNotBlank() }
        } else null
    }.getOrNull()
}

@Composable
private fun SetupScreen(vm: AppViewModel, onScanQr: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Welcome", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Enter a webhook URL to start forwarding notifications",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))
            WebhookForm(vm = vm, onScanQr = onScanQr)
            Spacer(Modifier.height(24.dp))
            QrFormatHint()
        }
    }
}

@Composable
private fun TabbedScreen(vm: AppViewModel, onScanQr: () -> Unit) {
    var selectedTab by remember { mutableStateOf(Tab.HISTORY) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == Tab.HISTORY,
                    onClick = { selectedTab = Tab.HISTORY },
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("History") }
                )
                NavigationBarItem(
                    selected = selectedTab == Tab.APPS,
                    onClick = { selectedTab = Tab.APPS },
                    icon = { Icon(Icons.Default.Apps, contentDescription = null) },
                    label = { Text("Apps") }
                )
                NavigationBarItem(
                    selected = selectedTab == Tab.SETTINGS,
                    onClick = { selectedTab = Tab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                Tab.HISTORY -> HistoryTab(vm)
                Tab.APPS -> AppsTab(vm)
                Tab.SETTINGS -> SettingsTab(vm, onScanQr = onScanQr)
            }
        }
    }
}

@Composable
private fun WebhookForm(vm: AppViewModel, onScanQr: () -> Unit) {
    val savedUrl by vm.webhookUrl.collectAsState()
    var urlInput by remember(savedUrl) { mutableStateOf(savedUrl) }

    OutlinedTextField(
        value = urlInput,
        onValueChange = { urlInput = it },
        label = { Text("Webhook URL") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("https://example.com/webhook") }
    )
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
    ) {
        OutlinedButton(onClick = onScanQr) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Scan QR")
        }
        Button(
            onClick = { vm.saveWebhookUrl(urlInput) },
            enabled = urlInput.trim() != savedUrl && urlInput.isNotBlank()
        ) {
            Text("Save")
        }
    }
}

@Composable
private fun QrFormatHint() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("QR code format", style = MaterialTheme.typography.labelMedium)
            Text(
                "To open the app directly from a QR scan, encode:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "notificationspy://webhook?url=YOUR_WEBHOOK_URL",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Text(
                "Or simply encode a plain https:// URL to scan in-app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistoryTab(vm: AppViewModel) {
    val events by vm.events.collectAsState()
    val appNames by vm.packageNameToAppName.collectAsState()

    if (events.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No events yet", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(events, key = { it.id }) { event ->
            EventRow(
                event = event,
                appName = appNames[event.packageName] ?: event.packageName,
                onRetry = { vm.retryEvent(event) }
            )
        }
    }
}

@Composable
private fun EventRow(event: EventEntity, appName: String, onRetry: () -> Unit) {
    val statusColor = when (event.status) {
        EventStatus.SUCCESS -> Color(0xFF4CAF50)
        EventStatus.FAILED -> Color(0xFFF44336)
        EventStatus.RETRYING -> Color(0xFFFF9800)
        else -> Color(0xFF9E9E9E)
    }
    val timeStr = remember(event.timestamp) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(event.timestamp))
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(appName, style = MaterialTheme.typography.labelMedium)
                if (event.notificationText.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(event.notificationText, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    timeStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Surface(color = statusColor, shape = MaterialTheme.shapes.small) {
                    Text(
                        event.status,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (event.retryCount > 0) {
                    Text(
                        "attempt ${event.retryCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (event.status == EventStatus.FAILED) {
                    TextButton(
                        onClick = onRetry,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("Retry", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppsTab(vm: AppViewModel) {
    val installedApps by vm.installedApps.collectAsState()
    val allowedPackages by vm.allowedPackages.collectAsState()

    if (installedApps.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val sortedApps = remember(installedApps, allowedPackages) {
        installedApps.sortedWith(
            compareByDescending<InstalledApp> { it.packageName in allowedPackages }
                .thenBy { it.appName }
        )
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(sortedApps, key = { it.packageName }) { app ->
            ListItem(
                headlineContent = { Text(app.appName) },
                supportingContent = {
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                },
                trailingContent = {
                    Switch(
                        checked = app.packageName in allowedPackages,
                        onCheckedChange = { enabled -> vm.toggleApp(app.packageName, enabled) }
                    )
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun SettingsTab(vm: AppViewModel, onScanQr: () -> Unit) {
    val lastEvent by vm.events.collectAsState()
    val lastStatus = lastEvent.firstOrNull()?.status

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Webhook", style = MaterialTheme.typography.titleLarge)
        WebhookForm(vm = vm, onScanQr = onScanQr)

        if (lastStatus != null) {
            val statusColor = when (lastStatus) {
                EventStatus.SUCCESS -> Color(0xFF4CAF50)
                EventStatus.FAILED -> Color(0xFFF44336)
                EventStatus.RETRYING -> Color(0xFFFF9800)
                else -> Color(0xFF9E9E9E)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Last delivery:", style = MaterialTheme.typography.bodyMedium)
                Surface(color = statusColor, shape = MaterialTheme.shapes.small) {
                    Text(
                        lastStatus,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        QrFormatHint()

        if (BuildConfig.DEBUG) {
            OutlinedButton(
                onClick = { vm.seedDummyData() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Seed dummy data")
            }
        }
    }
}
