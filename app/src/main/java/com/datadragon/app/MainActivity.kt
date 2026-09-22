package com.datadragon.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.datadragon.app.data.AutoBackupError
import com.datadragon.app.data.AutoBackupService
import com.datadragon.app.navigation.DataDragonNavHost
import com.datadragon.app.navigation.Routes
import com.datadragon.app.ui.theme.DataDragonTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val autoBackup = AutoBackupService.get(this)
        setContent {
            DataDragonTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    DataDragonNavHost(navController = navController)

                    // Shown once for each new folder-access failure that blocked
                    // a needed automatic backup. Retried save or verification
                    // failures never raise it.
                    val autoBackupState by autoBackup.state.collectAsStateWithLifecycle()
                    val body = when (autoBackupState.error) {
                        AutoBackupError.PERMISSION_LOST ->
                            "Data Dragon no longer has access to your backup folder. Open settings and select the folder again to restore access."
                        AutoBackupError.FOLDER_MISSING ->
                            "Your backup folder can't be found. It may have been moved, deleted, or disconnected. Open settings to choose a new backup folder."
                        else -> null
                    }
                    if (autoBackupState.accessNoticePending && body != null) {
                        AlertDialog(
                            onDismissRequest = autoBackup::consumeAccessNotice,
                            title = { Text("Automatic Backup Failed") },
                            text = { Text(body) },
                            dismissButton = {
                                TextButton(onClick = autoBackup::consumeAccessNotice) { Text("Not Now") }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    autoBackup.consumeAccessNotice()
                                    navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                                }) { Text("Open Settings") }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AutoBackupService.get(this).onForeground()
    }

    override fun onStop() {
        super.onStop()
        AutoBackupService.get(this).onBackground()
    }
}
