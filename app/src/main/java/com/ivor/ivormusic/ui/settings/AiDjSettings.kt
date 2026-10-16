package com.ivor.ivormusic.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivor.ivormusic.R
import com.ivor.ivormusic.data.AiDjRepository

class AiDjSettingsViewModel(application: Application) : AndroidViewModel(application) {
    val repository = AiDjRepository(application)
    val hasApiKey = repository.hasApiKey
    val prompt = repository.prompt
    val trackCount = repository.trackCount
}

@Composable
internal fun AiDjHubRow(
    onClick: () -> Unit,
    selected: Boolean = false,
    model: AiDjSettingsViewModel = viewModel(),
) {
    val hasKey by model.hasApiKey.collectAsState()
    SettingsHubRow(
        icon = Icons.Rounded.AutoAwesome,
        title = stringResource(R.string.aidj_title),
        value = if (hasKey) stringResource(R.string.aidj_configured)
        else stringResource(R.string.aidj_not_configured),
        onClick = onClick,
        tint = MaterialTheme.colorScheme.tertiary,
        explanation = stringResource(R.string.aidj_intro),
        selected = selected,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiDjSettingsPage(
    onBack: () -> Unit,
    model: AiDjSettingsViewModel = viewModel(),
) {
    val repository = model.repository
    val context = LocalContext.current
    val hasKey by model.hasApiKey.collectAsState()
    val prompt by model.prompt.collectAsState()
    val trackCount by model.trackCount.collectAsState()

    // Credentials never enter saved-instance-state or navigation arguments.
    var keyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var promptInput by remember { mutableStateOf(prompt) }
    var promptDirty by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.aidj_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.aidj_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Google AI API key.
            Text(
                stringResource(R.string.aidj_api_key_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        if (hasKey) stringResource(R.string.aidj_api_key_set_placeholder)
                        else stringResource(R.string.aidj_api_key_hint)
                    )
                },
                visualTransformation = if (showKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                singleLine = true,
                trailingIcon = {
                    TextButton(onClick = { showKey = !showKey }) {
                        Text(
                            if (showKey) stringResource(R.string.aidj_hide)
                            else stringResource(R.string.aidj_show)
                        )
                    }
                },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        repository.setApiKey(keyInput)
                        keyInput = ""
                    },
                    enabled = keyInput.isNotBlank(),
                ) {
                    Text(stringResource(R.string.aidj_save_key))
                }
                if (hasKey) {
                    OutlinedButton(onClick = {
                        repository.clearApiKey()
                        keyInput = ""
                    }) {
                        Text(stringResource(R.string.aidj_remove_key))
                    }
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            TextButton(
                onClick = {
                    try {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://aistudio.google.com/apikey"),
                            )
                        )
                    } catch (_: Exception) { }
                }
            ) {
                Text(stringResource(R.string.aidj_get_key))
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }

            // Custom DJ prompt.
            Text(
                stringResource(R.string.aidj_prompt_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = if (promptDirty) promptInput else prompt,
                onValueChange = {
                    promptInput = it
                    promptDirty = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                minLines = 4,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        repository.setPrompt(promptInput)
                        promptDirty = false
                    },
                    enabled = promptDirty,
                ) {
                    Text(stringResource(R.string.aidj_save_prompt))
                }
                TextButton(onClick = {
                    repository.resetPrompt()
                    promptInput = AiDjRepository.DEFAULT_PROMPT
                    promptDirty = false
                }) {
                    Text(stringResource(R.string.aidj_reset_prompt))
                }
            }

            // Track count.
            Text(
                stringResource(R.string.aidj_track_count_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiDjRepository.TRACK_COUNT_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = trackCount == option,
                        onClick = { repository.setTrackCount(option) },
                        label = { Text(option.toString()) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.aidj_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
