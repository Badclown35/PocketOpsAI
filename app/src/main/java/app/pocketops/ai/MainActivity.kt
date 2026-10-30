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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import rikka.shizuku.Shizuku

data class AppEntry(
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
    val isEnabled: Boolean
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
                    isSystem = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    isEnabled = it.enabled
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
            else -> AiAuditScreen(Modifier.padding(padding), apps, scanned)
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
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    if (app.isSystem) "System" else "User",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    if (app.isEnabled) "Enabled" else "Disabled",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (app.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AiAuditScreen(
    modifier: Modifier,
    apps: List<AppEntry>,
    scanned: Boolean
) {
    var result by remember {
        mutableStateOf("Scan your apps, then start an audit.")
    }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("PocketOps local audit", style = MaterialTheme.typography.headlineSmall)
        Text("Rule-based screening · Runs locally")
        Text(if (scanned) "${apps.size} apps ready" else "Scan apps from Overview first")

        Button(
            enabled = scanned && !loading,
            onClick = {
                loading = true
                result = "Analyzing installed apps..."
                scope.launch {
                    result = try {
                        withContext(Dispatchers.IO) {
                            analyzeApps(apps)
                        }
                    } catch (e: Exception) {
                        "Connection failed: ${e.message ?: "Unknown error"}\n\nMake sure the Termux analyzer is running."
                    }
                    loading = false
                }
            }
        ) {
            Text(if (loading) "Analyzing..." else "Analyze apps")
        }

        Text(result, style = MaterialTheme.typography.bodyMedium)

        Text(
            "Recommendations only. No apps will be removed or disabled.",
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}


private fun analyzeApps(apps: List<AppEntry>): String {
    val connection = URL("http://127.0.0.1:8787/analyze")
        .openConnection() as HttpURLConnection

    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")

        val payload = JSONObject().put(
            "apps",
            JSONArray().apply {
                apps.forEach { app ->
                    put(
                        JSONObject()
                            .put("packageName", app.packageName)
                            .put("label", app.label)
                            .put("isSystem", app.isSystem)
                    )
                }
            }
        )

        connection.outputStream.use {
            it.write(payload.toString().toByteArray(Charsets.UTF_8))
        }

        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${connection.responseCode}")
        }

        val response = connection.inputStream.bufferedReader().use {
            it.readText()
        }

        val analysis = JSONObject(
            JSONObject(response).getString("analysis")
        )
        val recommendations = analysis.getJSONArray("recommendations")

        return buildString {
            append(analysis.getString("summary"))
            append("\n\nRECOMMENDATIONS\n")

            for (i in 0 until recommendations.length()) {
                val item = recommendations.getJSONObject(i)
                append("\n${item.getString("label")}")
                append(" · ${item.getString("risk").uppercase()}")
                append("\n${item.getString("action")}")
                append("\n${item.getString("reason")}\n")
            }
        }
    } finally {
        connection.disconnect()
    }
}
