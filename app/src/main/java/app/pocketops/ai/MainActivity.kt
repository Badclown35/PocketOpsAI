package app.pocketops.ai

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rikka.shizuku.Shizuku

data class AppEntry(
    val label: String,
    val packageName: String,
    val isSystem: Boolean
)

class MainActivity : ComponentActivity() {
    private val requestCode = 4107

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PocketOpsScreen(
                    scanApps = { loadApps() },
                    shizukuStatus = { getShizukuStatus() },
                    requestShizuku = { requestShizukuPermission() }
                )
            }
        }
    }

    private fun loadApps(): List<AppEntry> {
        return packageManager.getInstalledApplications(0)
            .map {
                AppEntry(
                    label = packageManager.getApplicationLabel(it).toString(),
                    packageName = it.packageName,
                    isSystem = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun getShizukuStatus(): String {
        return try {
            when {
                !Shizuku.pingBinder() -> "Shizuku service is not running"
                Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED ->
                    "Connected · permission granted"
                else -> "Service found · permission not granted"
            }
        } catch (_: Exception) {
            "Shizuku status unavailable"
        }
    }

    private fun requestShizukuPermission() {
        try {
            if (Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (_: Exception) {
            // The UI will continue to show the current connection state.
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketOpsScreen(
    scanApps: () -> List<AppEntry>,
    shizukuStatus: () -> String,
    requestShizuku: () -> Unit
) {
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var scanned by remember { mutableStateOf(false) }
    var shizuku by remember { mutableStateOf(shizukuStatus()) }
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PocketOps AI")
                        Text("Device audit · Local-first", style = MaterialTheme.typography.labelSmall)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("⌂") },
                    label = { Text("Overview") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("▦") },
                    label = { Text("Apps") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Text("✦") },
                    label = { Text("AI audit") }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> Overview(
                modifier = Modifier.padding(padding),
                appCount = apps.size,
                scanned = scanned,
                shizuku = shizuku,
                onRefreshShizuku = { shizuku = shizukuStatus() },
                onRequestShizuku = requestShizuku,
                onScan = {
                    apps = scanApps()
                    scanned = true
                }
            )
            1 -> AppList(
                modifier = Modifier.padding(padding),
                apps = apps,
                scanned = scanned,
                onScan = {
                    apps = scanApps()
                    scanned = true
                }
            )
            else -> AiAuditPlaceholder(Modifier.padding(padding))
        }
    }
}

@Composable
private fun Overview(
    modifier: Modifier,
    appCount: Int,
    scanned: Boolean,
    shizuku: String,
    onRefreshShizuku: () -> Unit,
    onRequestShizuku: () -> Unit,
    onScan: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Device overview", style = MaterialTheme.typography.headlineSmall)
            Text("Start with a read-only scan. Nothing is removed or changed.")
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Shizuku connection", style = MaterialTheme.typography.titleMedium)
                    Text(shizuku)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onRefreshShizuku) { Text("Refresh") }
                        Button(onClick = onRequestShizuku) { Text("Request access") }
                    }
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Installed applications", style = MaterialTheme.typography.titleMedium)
                    Text(if (scanned) "$appCount apps discovered" else "Not scanned yet")
                    Button(onClick = onScan) { Text("Scan installed apps") }
                }
            }
        }
        item {
            Text(
                "Safety mode: recommendations only. Destructive actions are not implemented in this starter build.",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AppList(
    modifier: Modifier,
    apps: List<AppEntry>,
    scanned: Boolean,
    onScan: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("App inventory", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onScan) { Text("Rescan") }
        }
        if (!scanned) {
            Text("Run a scan from Overview to populate this list.")
        } else {
            Text("${apps.size} installed apps · no changes made")
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    ListItem(
                        headlineContent = { Text(app.label) },
                        supportingContent = { Text(app.packageName) },
                        trailingContent = {
                            Text(
                                if (app.isSystem) "System" else "User",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AiAuditPlaceholder(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Claude AI audit", style = MaterialTheme.typography.headlineSmall)
        Text("The AI connection is intentionally not active yet.")
        Text("Next milestone:")
        Text("1. Send a minimized app inventory to a private backend.")
        Text("2. Receive typed recommendations with reasons and confidence.")
        Text("3. Show each proposed action for your approval.")
        Text("4. Validate the action locally before execution.")
        Text(
            "Do not put an Anthropic API key in this APK. The backend proxy will hold it.",
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
