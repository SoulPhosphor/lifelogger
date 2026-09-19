package com.datadragon.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.datadragon.app.data.AutoBackupRunner
import com.datadragon.app.navigation.DataDragonNavHost
import com.datadragon.app.ui.theme.DataDragonTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Automatic Backup's daily check. Costs a few SharedPreferences reads
        // when nothing is due, which is almost every launch; only a backup that
        // is genuinely due leaves the main thread, and it never holds up the UI
        // below.
        lifecycleScope.launch { AutoBackupRunner(applicationContext).runIfDue() }
        enableEdgeToEdge()
        setContent {
            DataDragonTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DataDragonNavHost()
                }
            }
        }
    }
}
