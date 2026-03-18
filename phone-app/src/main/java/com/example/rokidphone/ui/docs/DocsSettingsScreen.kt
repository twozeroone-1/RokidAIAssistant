package com.example.rokidphone.ui.docs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rokidphone.R
import com.example.rokidphone.data.AnswerMode
import com.example.rokidphone.data.ApiSettings
import com.example.rokidphone.data.DocsHealthStatus
import com.example.rokidphone.data.NetworkProfile
import com.example.rokidphone.data.toAnythingLlmSettings
import com.example.rokidphone.service.rag.AnythingLlmRagService
import com.example.rokidphone.ui.ApiKeyField
import com.example.rokidphone.ui.SettingsRowWithSwitch
import com.example.rokidphone.ui.SettingsSection
import kotlinx.coroutines.launch

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun DocsSettingsScreen(
    settings: ApiSettings,
    onSettingsChange: (ApiSettings) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isTestingConnection by androidx.compose.runtime.remember { mutableStateOf(false) }
    val connectionFailedFallback = stringResource(R.string.connection_failed_generic)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.docs_assistant)) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSection(title = stringResource(R.string.default_answer_mode)) {
                    SelectionChipRow(
                        entries = listOf(
                            SelectionEntry(
                                label = stringResource(R.string.general_ai_mode),
                                selected = settings.answerMode == AnswerMode.GENERAL_AI,
                                onClick = { onSettingsChange(settings.copy(answerMode = AnswerMode.GENERAL_AI)) },
                            ),
                            SelectionEntry(
                                label = stringResource(R.string.docs_mode),
                                selected = settings.answerMode == AnswerMode.DOCS,
                                onClick = { onSettingsChange(settings.copy(answerMode = AnswerMode.DOCS)) },
                            ),
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.docs_mode_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.network_profile)) {
                    SelectionChipRow(
                        entries = listOf(
                            SelectionEntry(
                                label = stringResource(R.string.network_fast),
                                selected = settings.networkProfile == NetworkProfile.FAST,
                                onClick = { onSettingsChange(settings.copy(networkProfile = NetworkProfile.FAST)) },
                            ),
                            SelectionEntry(
                                label = stringResource(R.string.network_slow),
                                selected = settings.networkProfile == NetworkProfile.SLOW,
                                onClick = { onSettingsChange(settings.copy(networkProfile = NetworkProfile.SLOW)) },
                            ),
                            SelectionEntry(
                                label = stringResource(R.string.network_auto),
                                selected = settings.networkProfile == NetworkProfile.AUTO,
                                onClick = { onSettingsChange(settings.copy(networkProfile = NetworkProfile.AUTO)) },
                            ),
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.network_auto_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.anythingllm_settings_title)) {
                    SettingsRowWithSwitch(
                        title = stringResource(R.string.docs_runtime_enabled),
                        subtitle = stringResource(R.string.docs_runtime_enabled_hint),
                        checked = settings.anythingLlmRuntimeEnabled,
                        onCheckedChange = { onSettingsChange(settings.copy(anythingLlmRuntimeEnabled = it)) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = settings.anythingLlmServerUrl,
                        onValueChange = { onSettingsChange(settings.copy(anythingLlmServerUrl = it)) },
                        label = { Text(stringResource(R.string.anythingllm_server_url)) },
                        placeholder = { Text("https://anythingllm.example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ApiKeyField(
                        label = stringResource(R.string.anythingllm_api_key),
                        value = settings.anythingLlmApiKey,
                        onValueChange = { onSettingsChange(settings.copy(anythingLlmApiKey = it)) },
                        isActive = settings.anythingLlmApiKey.isNotBlank()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = settings.anythingLlmWorkspaceSlug,
                        onValueChange = { onSettingsChange(settings.copy(anythingLlmWorkspaceSlug = it)) },
                        label = { Text(stringResource(R.string.anythingllm_workspace_slug)) },
                        placeholder = { Text("ops-docs") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                isTestingConnection = true
                                val service = AnythingLlmRagService()
                                val result = service.checkHealth(settings.toAnythingLlmSettings())
                                result.onSuccess { health ->
                                    onSettingsChange(
                                        settings.copy(
                                            anythingLlmLastHealthStatus = health.status,
                                            anythingLlmLastHealthMessage = health.message,
                                            anythingLlmRecentFailureCount = 0,
                                        )
                                    )
                                }.onFailure { error ->
                                    onSettingsChange(
                                        settings.copy(
                                            anythingLlmLastHealthStatus = DocsHealthStatus.UNHEALTHY,
                                            anythingLlmLastHealthMessage = error.message ?: connectionFailedFallback,
                                            anythingLlmRecentFailureCount = settings.anythingLlmRecentFailureCount + 1,
                                        )
                                    )
                                }
                                isTestingConnection = false
                            }
                        },
                        enabled = !isTestingConnection,
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(stringResource(R.string.test_connection))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    HealthStatusCard(
                        status = settings.anythingLlmLastHealthStatus,
                        message = settings.anythingLlmLastHealthMessage,
                        workspaceSlug = settings.anythingLlmWorkspaceSlug,
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.docs_behavior_title)) {
                    Text(
                        text = stringResource(R.string.docs_behavior_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private data class SelectionEntry(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun SelectionChipRow(entries: List<SelectionEntry>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        entries.forEach { entry ->
            FilterChip(
                selected = entry.selected,
                onClick = entry.onClick,
                label = { Text(entry.label) }
            )
        }
    }
}

@Composable
private fun HealthStatusCard(
    status: DocsHealthStatus,
    message: String,
    workspaceSlug: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = when (status) {
                    DocsHealthStatus.HEALTHY -> stringResource(R.string.docs_status_healthy)
                    DocsHealthStatus.UNHEALTHY -> stringResource(R.string.docs_status_unhealthy)
                    DocsHealthStatus.UNKNOWN -> stringResource(R.string.docs_status_unknown)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (workspaceSlug.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.anythingllm_workspace_label, workspaceSlug),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (message.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
