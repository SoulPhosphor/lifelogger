package com.datadragon.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.datadragon.app.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuckyListSettingsScreen(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(topBar = {
        TopAppBar(title = { Text("Lucky List Settings") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AppTheme.spacing.screenInset)) {
            Row(
                modifier = Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange).padding(vertical = AppTheme.spacing.related),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("When spinning again exclude previously selected items.", style = AppTheme.textStyles.settingTitle, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(AppTheme.spacing.rowInset))
                Switch(checked = checked, enabled = enabled, onCheckedChange = null)
            }
        }
    }
}
