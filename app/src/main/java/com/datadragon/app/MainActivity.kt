package com.datadragon.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.datadragon.app.recovery.RecoveryExporter
import com.datadragon.app.ui.theme.DataDragonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The recovery build's entry point.
 *
 * This is deliberately *not* the normal app. It does not call
 * `DataDragonNavHost()`, does not build the navigation graph, and does not
 * create any ViewModel, repository or DAO. Nothing reachable from here
 * initializes Room or opens the database — the only work this build does is
 * copy raw private files into a ZIP the user chooses a location for.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DataDragonTheme {
                RecoveryScreen()
            }
        }
    }
}

@Composable
private fun RecoveryScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val saveDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            exporting = true
            status = null
            scope.launch {
                // NonCancellable so a configuration change part way through can
                // never truncate the archive the user is relying on.
                val message = withContext(Dispatchers.IO + NonCancellable) {
                    try {
                        val result = RecoveryExporter.export(context, uri)
                        if (result.failed == 0) {
                            "Recovery ZIP saved successfully. ${result.copied} files copied."
                        } else {
                            "Recovery ZIP saved, but ${result.failed} files could not be copied. " +
                                "See ${RecoveryExporter.MANIFEST_NAME}."
                        }
                    } catch (t: Throwable) {
                        "Recovery export failed. No source files were deleted or modified."
                    }
                }
                status = message
                exporting = false
            }
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Data Dragon Recovery",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "This recovery build does not open the app database. It copies the " +
                    "existing private app files into a ZIP so they can be examined safely.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = { saveDocument.launch(RecoveryExporter.suggestedFileName()) },
                enabled = !exporting,
            ) {
                Text("Export Recovery ZIP")
            }
            if (exporting) {
                CircularProgressIndicator()
            }
            status?.let {
                Text(text = it, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
