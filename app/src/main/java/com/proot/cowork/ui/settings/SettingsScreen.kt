package com.proot.cowork.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proot.cowork.R
import com.proot.cowork.data.prefs.SettingsRepository
import com.proot.cowork.ui.components.CoworkCard
import com.proot.cowork.ui.design.CoworkTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(settingsRepository)),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.savedMessage) {
        uiState.savedMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearSavedMessage() }
    }

    Scaffold(
        containerColor = CoworkTokens.Bg,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.SemiBold, color = CoworkTokens.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CoworkTokens.TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CoworkTokens.Bg),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSectionHeader(Icons.Default.Key, stringResource(R.string.settings_api_section))
            CoworkCard {
                SettingsField(stringResource(R.string.api_base_url), uiState.baseUrl, viewModel::onBaseUrlChange)
                Spacer(Modifier.height(12.dp))
                SettingsField(stringResource(R.string.api_key), uiState.apiKey, viewModel::onApiKeyChange, password = true)
                Spacer(Modifier.height(12.dp))
                SettingsField(stringResource(R.string.model_name), uiState.model, viewModel::onModelChange)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier.fillMaxWidth(),
                    shape = CoworkTokens.ShapeCard,
                    colors = ButtonDefaults.buttonColors(containerColor = CoworkTokens.Mint, contentColor = CoworkTokens.SpeakFg),
                ) { Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold) }
            }

            SettingsSectionHeader(Icons.Default.Storage, stringResource(R.string.settings_rootfs_section))
            CoworkCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, null, tint = CoworkTokens.Mint, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Ubuntu", fontWeight = FontWeight.SemiBold, color = CoworkTokens.TextPrimary)
                        Spacer(Modifier.size(8.dp))
                        Surface(shape = CoworkTokens.ShapePill, color = CoworkTokens.Mint.copy(alpha = 0.15f)) {
                            Text(stringResource(R.string.imported_badge), Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = CoworkTokens.Mint, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                        }
                    }
                    Icon(Icons.Default.Delete, null, tint = CoworkTokens.TextMuted)
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.rootfs_meta), color = CoworkTokens.TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.container_health), color = CoworkTokens.TextSecondary, fontWeight = FontWeight.Medium)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(CoworkTokens.Mint))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.container_running), color = CoworkTokens.Mint, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HealthTile("CPU", "2.3%", Modifier.weight(1f))
                    HealthTile("Memory", "156 MB", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HealthTile("Display", ":0", Modifier.weight(1f))
                    HealthTile("Uptime", "00:04:23", Modifier.weight(1f))
                }
            }

            SettingsSectionHeader(Icons.Default.Layers, stringResource(R.string.settings_proot_section))
            Text(stringResource(R.string.settings_providers_hint), color = CoworkTokens.TextMuted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = CoworkTokens.Mint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(8.dp))
        Text(title, color = CoworkTokens.Mint, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SettingsField(label: String, value: String, onChange: (String) -> Unit, password: Boolean = false) {
    Text(label, color = CoworkTokens.TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = CoworkTokens.ShapeCard,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CoworkTokens.Border,
            unfocusedBorderColor = CoworkTokens.Border,
            focusedContainerColor = CoworkTokens.SurfaceElevated,
            unfocusedContainerColor = CoworkTokens.SurfaceElevated,
            focusedTextColor = CoworkTokens.TextPrimary,
            unfocusedTextColor = CoworkTokens.TextPrimary,
        ),
    )
}

@Composable
private fun HealthTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.border(1.dp, CoworkTokens.Border, CoworkTokens.ShapeCard),
        shape = CoworkTokens.ShapeCard,
        color = CoworkTokens.SurfaceElevated,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, color = CoworkTokens.TextMuted, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            Text(value, color = CoworkTokens.TextPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}
