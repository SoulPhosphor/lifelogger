package com.datadragon.lifelogger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private data class PlaceholderLog(
    val id: Long,
    val name: String,
    val subtitle: String,
)

private val placeholderLogs = listOf(
    PlaceholderLog(1, "My Log", "14 entries · last entry today"),
    PlaceholderLog(2, "Another Log", "3 entries · last entry Jun 20"),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DataDragonApp()
        }
    }
}

@Composable
private fun DataDragonApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "home") {
                composable("home") {
                    HomeScreen(
                        onSettings = { navController.navigate("settings") },
                        onBackup = { navController.navigate("backup") },
                        onCreateLog = { navController.navigate("create-log") },
                        onOpenLog = { logId -> navController.navigate("entries/$logId") },
                        onNewEntry = { logId -> navController.navigate("new-entry/$logId") },
                    )
                }
                composable("create-log") { CreateLogScreen(onBack = navController::popBackStack) }
                composable(
                    route = "entries/{logId}",
                    arguments = listOf(navArgument("logId") { type = NavType.LongType }),
                ) { backStackEntry ->
                    val logId = backStackEntry.arguments?.getLong("logId") ?: 1L
                    EntryListScreen(
                        log = placeholderLogs.firstOrNull { it.id == logId } ?: placeholderLogs.first(),
                        onBack = navController::popBackStack,
                        onNewEntry = { navController.navigate("new-entry/$logId") },
                    )
                }
                composable(
                    route = "new-entry/{logId}",
                    arguments = listOf(navArgument("logId") { type = NavType.LongType }),
                ) { NewEntryScreen(onBack = navController::popBackStack) }
                composable("settings") { SimpleMessageScreen(title = "Settings", onBack = navController::popBackStack) }
                composable("backup") { BackupScreen(onBack = navController::popBackStack) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    onSettings: () -> Unit,
    onBackup: () -> Unit,
    onCreateLog: () -> Unit,
    onOpenLog: (Long) -> Unit,
    onNewEntry: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data Dragon") },
                navigationIcon = {
                    Row {
                        IconButton(onClick = onSettings) { Text("⚙") }
                        IconButton(onClick = onBackup) { Text("↓") }
                    }
                },
                actions = { IconButton(onClick = onCreateLog) { Text("+") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            )
        },
    ) { padding ->
        if (placeholderLogs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No logs yet.\nTap  +  (top right) to create\nyour first one.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(placeholderLogs) { log ->
                    LogRow(log = log, onOpenLog = { onOpenLog(log.id) }, onNewEntry = { onNewEntry(log.id) })
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: PlaceholderLog, onOpenLog: () -> Unit, onNewEntry: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenLog)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = log.name,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                )
                Button(onClick = onNewEntry) { Text("+") }
            }
            Text(log.subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateLogScreen(onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var formMarkdown by remember { mutableStateOf("") }
    var showFieldTypes by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { BasicTopBar(title = "New Log", onBack = onBack, actionText = "Save") },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Log name")
            OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth())
            Text("Description (optional)")
            OutlinedTextField(value = description, onValueChange = { description = it }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Define fields", modifier = Modifier.weight(1f))
                TextButton(onClick = { showFieldTypes = !showFieldTypes }) { Text("Field types ▸") }
            }
            if (showFieldTypes) {
                Text("text — a single line of text\nmultiline — multi-line text box\ndate — month/day/year picker\ntime — 12-hour time with AM/PM\ndropdown — pick one item from a list\nscale — pick a number in a range\nyesno — Yes / No / Unknown / Not Applicable\nnumber — type a number\nmultiple — pick several items")
            }
            OutlinedTextField(
                value = formMarkdown,
                onValueChange = { formMarkdown = it },
                modifier = Modifier.fillMaxWidth().height(160.dp),
                placeholder = { Text("Paste Form Markdown here…") },
            )
            Button(onClick = { }) { Text("Preview form") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryListScreen(log: PlaceholderLog, onBack: () -> Unit, onNewEntry: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(log.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    Row {
                        IconButton(onClick = onBack) { Text("<") }
                        IconButton(onClick = { }) { Text("↓") }
                        IconButton(onClick = { }) { Text("🗑") }
                    }
                },
                actions = { IconButton(onClick = onNewEntry) { Text("+") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { EntryRow("Jun 27, 2:14 PM", "Field 1 value · Field 2 value", "Notes preview text here…") }
            item { EntryRow("Jun 26, 11:20 PM", "Field 1 value · Field 2 value", null) }
        }
    }
}

@Composable
private fun EntryRow(dateTime: String, summary: String, notes: String?) {
    Card(modifier = Modifier.fillMaxWidth().clickable { }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dateTime, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("🗑")
            }
            Text(summary)
            if (notes != null) Text(notes)
        }
    }
}

@Composable
private fun NewEntryScreen(onBack: () -> Unit) {
    Scaffold(topBar = { BasicTopBar(title = "New Entry", onBack = onBack, actionText = "Save") }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Date / time:  Jun 27, 2026, 2:14 PM (auto)")
            Text("Rating")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (1..5).forEach { Button(onClick = { }) { Text("$it") } } }
            Text("Category")
            Button(onClick = { }, modifier = Modifier.fillMaxWidth()) { Text("Dropdown selection ▼") }
            Text("Notes")
            OutlinedTextField(value = "", onValueChange = { }, modifier = Modifier.fillMaxWidth().height(120.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupScreen(onBack: () -> Unit) {
    Scaffold(topBar = { BasicTopBar(title = "Backup all data", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("This saves every log and every entry into a single .json file you can store safely.")
            Spacer(Modifier.height(24.dp))
            Button(onClick = { }) { Text("Back up now") }
        }
    }
}

@Composable
private fun SimpleMessageScreen(title: String, onBack: () -> Unit) {
    Scaffold(topBar = { BasicTopBar(title = title, onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Time format")
            Text("( ) 12-hour (2:14 PM)")
            Text("( ) 24-hour (14:14)")
            Spacer(Modifier.height(32.dp))
            Text("Restore from backup")
            Button(onClick = { }) { Text("Choose backup file…") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BasicTopBar(title: String, onBack: () -> Unit, actionText: String? = null) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = onBack) { Text("<") } },
        actions = { if (actionText != null) TextButton(onClick = { }) { Text(actionText) } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    )
}
