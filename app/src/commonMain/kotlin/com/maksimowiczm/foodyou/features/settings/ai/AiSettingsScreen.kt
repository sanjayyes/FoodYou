package com.maksimowiczm.foodyou.features.settings.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val selectedModel = viewModel.selectedModel.collectAsStateWithLifecycle().value
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    LaunchedEffect(Unit) { viewModel.refreshModels() }

    AiSettingsScreen(
        apiKey = preferences.geminiApiKey.orEmpty(),
        selectedModel = selectedModel,
        isDefaultModel = preferences.geminiModel == null,
        uiState = uiState,
        onSaveApiKey = viewModel::saveApiKey,
        onSelectModel = viewModel::selectModel,
        onRefreshModels = viewModel::refreshModels,
        onDismissError = viewModel::dismissError,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun AiSettingsScreen(
    apiKey: String,
    selectedModel: String,
    isDefaultModel: Boolean,
    uiState: AiSettingsUiState,
    onSaveApiKey: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onRefreshModels: () -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val appConfig = LocalAppConfig.current

    val apiKeyState = rememberTextFieldState(apiKey)
    var hideKey by rememberSaveable { mutableStateOf(true) }
    var showModelPicker by rememberSaveable { mutableStateOf(false) }

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

            uiState.error?.let { error ->
                Text(
                    text = error.asMessage(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            SegmentedListItem(
                onClick = {
                    onDismissError()
                    onRefreshModels()
                    showModelPicker = true
                },
                modifier = Modifier.fillMaxWidth(),
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                colors =
                    ListItemDefaults.segmentedColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                leadingContent = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                trailingContent = {
                    if (uiState.isLoadingModels) CircularProgressIndicator()
                },
                content = { Text(stringResource(Res.string.headline_gemini_model)) },
                supportingContent = {
                    Text(
                        text =
                            if (isDefaultModel)
                                stringResource(Res.string.neutral_ai_default_model, selectedModel)
                            else selectedModel
                    )
                },
            )
        }
    }

    if (showModelPicker) {
        ModelPickerDialog(
            models = uiState.models,
            selectedModel = selectedModel,
            isLoading = uiState.isLoadingModels,
            onSelect = {
                onSelectModel(it)
                showModelPicker = false
            },
            onDismissRequest = { showModelPicker = false },
        )
    }
}

@Composable
private fun ModelPickerDialog(
    models: List<String>,
    selectedModel: String,
    isLoading: Boolean,
    onSelect: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Res.string.headline_select_model)) },
        text = {
            when {
                isLoading -> CircularProgressIndicator()
                models.isEmpty() ->
                    Text(
                        text = stringResource(Res.string.description_model_requires_api_key),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                else ->
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        items(models) { model ->
                            ListItem(
                                headlineContent = { Text(model) },
                                modifier = Modifier.clickable { onSelect(model) },
                                trailingContent = {
                                    RadioButton(
                                        selected = model == selectedModel,
                                        onClick = null,
                                    )
                                },
                            )
                        }
                    }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}
