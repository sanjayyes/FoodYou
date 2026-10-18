package com.maksimowiczm.foodyou.features.settings.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.ai.domain.DEFAULT_GEMINI_MODEL
import com.maksimowiczm.foodyou.features.ai.asMessage
import com.maksimowiczm.foodyou.shared.ui.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.shared.ui.utility.LocalAppConfig
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Settings for the Gemini-backed food parsing assistant. */
@Composable
fun AiSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: AiSettingsViewModel = koinViewModel()

    val preferences = viewModel.preferences.collectAsStateWithLifecycle().value
    val connectionState = viewModel.connectionState.collectAsStateWithLifecycle().value

    AiSettingsScreen(
        apiKey = preferences.geminiApiKey.orEmpty(),
        connectionState = connectionState,
        onSaveApiKey = viewModel::saveApiKey,
        onTestConnection = viewModel::testConnection,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun AiSettingsScreen(
    apiKey: String,
    connectionState: AiConnectionState,
    onSaveApiKey: (String) -> Unit,
    onTestConnection: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val appConfig = LocalAppConfig.current

    val apiKeyState = rememberTextFieldState(apiKey)
    var hideKey by rememberSaveable { mutableStateOf(true) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.headline_ai_assistant)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
                scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier.padding(contentPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.description_ai_assistant),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedSecureTextField(
                state = apiKeyState,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.headline_api_key)) },
                supportingText = {
                    Text(stringResource(Res.string.description_gemini_api_key))
                },
                trailingIcon = {
                    IconButton(
                        onClick = { hideKey = !hideKey },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector =
                                if (hideKey) Icons.Outlined.Visibility
                                else Icons.Outlined.VisibilityOff,
                            contentDescription =
                                stringResource(
                                    if (hideKey) Res.string.action_show else Res.string.action_hide
                                ),
                        )
                    }
                },
                textObfuscationMode =
                    if (hideKey) TextObfuscationMode.RevealLastTyped
                    else TextObfuscationMode.Visible,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { onSaveApiKey(apiKeyState.text.toString()) },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(Res.string.action_save))
                }
                TextButton(
                    onClick = { uriHandler.openUri(appConfig.geminiObtainApiKeyUri) },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(Res.string.action_obtain_key))
                }
            }

            OutlinedButton(
                onClick = onTestConnection,
                modifier = Modifier.fillMaxWidth(),
                enabled = apiKey.isNotBlank() && connectionState !is AiConnectionState.Checking,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(Res.string.action_test_connection))
                if (connectionState is AiConnectionState.Checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp).padding(start = 4.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            AiConnectionStatus(connectionState)
        }
    }
}

/**
 * Renders the outcome of the connection test.
 *
 * [Idle] deliberately renders nothing: a result is only ever shown for a test the user just
 * requested, so the screen never claims a key works based on an older session.
 */
@Composable
private fun AiConnectionStatus(state: AiConnectionState) {
    val (text, isError) =
        when (state) {
            AiConnectionState.Idle -> return
            AiConnectionState.Checking ->
                stringResource(Res.string.neutral_ai_connection_checking) to false

            AiConnectionState.Connected ->
                stringResource(Res.string.neutral_ai_connection_ok, DEFAULT_GEMINI_MODEL) to false

            is AiConnectionState.Failed -> state.error.asMessage() to true
        }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color =
            if (isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
