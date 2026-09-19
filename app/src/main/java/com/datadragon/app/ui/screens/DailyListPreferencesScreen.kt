package com.datadragon.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.data.CelebrationIcon
import com.datadragon.app.ui.DailyListViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyListPreferencesScreen(
    onBack: () -> Unit,
    viewModel: DailyListViewModel = viewModel(),
) {
    val heading by viewModel.heading.collectAsStateWithLifecycle()
    val autoRenew by viewModel.autoRenew.collectAsStateWithLifecycle()
    val showCompleted by viewModel.showCompleted.collectAsStateWithLifecycle()
    val showCurrentUnfinished by viewModel.showCurrentUnfinished.collectAsStateWithLifecycle()
    val showPastUnfinished by viewModel.showPastUnfinished.collectAsStateWithLifecycle()
    val autoTrashPast by viewModel.autoTrashPast.collectAsStateWithLifecycle()
    val autoReopen by viewModel.autoReopen.collectAsStateWithLifecycle()
    val allowTitle by viewModel.allowTitle.collectAsStateWithLifecycle()
    val celebrationEnabled by viewModel.celebrationEnabled.collectAsStateWithLifecycle()
    val celebrationIcon by viewModel.celebrationIcon.collectAsStateWithLifecycle()
    val protectFavorited by viewModel.protectFavorited.collectAsStateWithLifecycle()
    val retentionRaw by viewModel.retentionRaw.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily List Preferences") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = heading,
                onValueChange = viewModel::setHeading,
                singleLine = true,
                label = { Text("Name showed at the top of your daily lists") },
                placeholder = { Text("Daily Tasks") },
                modifier = Modifier.fillMaxWidth(),
            )
            PreferenceToggle(autoRenew, viewModel::setAutoRenew, "Automatically renew daily list items that weren't completed.")
            PreferenceToggle(showCompleted, viewModel::setShowCompleted, "Show completed list items in main view.")
            PreferenceToggle(showCurrentUnfinished, viewModel::setShowCurrentUnfinished, "Show current dates uncompleted list items in main view.")
            PreferenceToggle(showPastUnfinished, viewModel::setShowPastUnfinished, "Show past dates uncompleted list items in main view")
            PreferenceToggle(autoTrashPast, viewModel::setAutoTrashPast, "Automatically trash uncompleted items from past days")
            PreferenceToggle(autoReopen, viewModel::setAutoReopen, "Automatically show current daily list when app is started.")
            PreferenceToggle(allowTitle, viewModel::setAllowTitle, "Allow creating title for daily lists.")
            PreferenceToggle(celebrationEnabled, viewModel::setCelebrationEnabled, "Mark days all tasks were completed with an icon on the home screen.")
            if (celebrationEnabled) {
                CelebrationChoice(celebrationIcon, viewModel::setCelebrationIcon)
            }
            PreferenceToggle(protectFavorited, viewModel::setProtectFavorited, "Protect favorited days.")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Auto delete daily lists older then (", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = retentionRaw,
                    onValueChange = viewModel::setRetentionRaw,
                    singleLine = true,
                    modifier = Modifier.width(72.dp),
                )
                Text(") days.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PreferenceToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CelebrationChoice(selected: CelebrationIcon, onSelected: (CelebrationIcon) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("Celebration icon:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.clickable { expanded = true }.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected.label)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                CelebrationIcon.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { onSelected(option); expanded = false },
                    )
                }
            }
        }
    }
}
