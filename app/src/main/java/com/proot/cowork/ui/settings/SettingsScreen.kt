package com.proot.cowork.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proot.cowork.R
import com.proot.cowork.data.prefs.SettingsRepository
import com.proot.cowork.ui.components.CoworkCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(settingsRepository),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.savedMessage) {
        uiState.savedMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSavedMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
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
        ) {
            SettingsSectionHeader(
                icon = { Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = stringResource(R.string.settings_api_section),
            )
            CoworkCard(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = uiState.baseUrl,
                    onValueChange = viewModel::onBaseUrlChange,
                    label = { Text(stringResource(R.string.api_base_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("https://openrouter.ai/api/v1") },
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.apiKey,
                    onValueChange = viewModel::onApiKeyChange,
                    label = { Text(stringResource(R.string.api_key)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = uiState.model,
                    onValueChange = viewModel::onModelChange,
                    label = { Text(stringResource(R.string.model_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    placeholder = { Text("openrouter/owl-alpha") },
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.save))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.settings_providers_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    icon: @Composable () -> Unit,
    title: String,
) {
    RowWithIcon(icon = icon, title = title)
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun RowWithIcon(icon: @Composable () -> Unit, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
