package com.datadragon.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datadragon.app.ui.UpdateViewModel
import com.datadragon.app.ui.components.AppButton
import com.datadragon.app.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    viewModel: UpdateViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("About", textAlign = TextAlign.Center) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.KeyboardDoubleArrowLeft, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(AppTheme.spacing.screenInset),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.rowInset),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                viewModel.versionLine,
                style = AppTheme.textStyles.settingTitle,
                textAlign = TextAlign.Center,
            )
            AppButton(
                onClick = viewModel::checkForUpdates,
                enabled = !state.working,
            ) {
                Text("Check for Updates")
            }
            state.status?.let { status ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.working) {
                        CircularProgressIndicator(modifier = Modifier.size(AppTheme.sizes.loadingIndicator), strokeWidth = AppTheme.sizes.loadingStroke)
                        Spacer(Modifier.width(AppTheme.spacing.related))
                    }
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
