package com.maksimowiczm.foodyou.features.ai

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.ai.domain.ParsedFoodEntry
import com.maksimowiczm.foodyou.capabilities.foodbrowsing.FoodSearchListItem
import com.maksimowiczm.foodyou.common.domain.food.Nutrient
import com.maksimowiczm.foodyou.shared.ui.utility.QuantityFormatter.stringResource
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Lets the user describe a meal in plain language and turn the result into a product.
 *
 * All nutrition values are per 100 g, the convention the food domain uses.
 */
@Composable
fun AiFoodInputDialog(
    onDismissRequest: () -> Unit,
    onFoodSelected: (ParsedFoodEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AiFoodInputViewModel = koinViewModel()

    LaunchedEffect(Unit) { viewModel.reset() }

    AiFoodInputDialog(
        query = viewModel.query.collectAsStateWithLifecycle().value,
        uiState = viewModel.uiState.collectAsStateWithLifecycle().value,
        onQueryChange = viewModel::onQueryChange,
        onParse = viewModel::parse,
        onFoodSelected = onFoodSelected,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    )
}

@Composable
private fun AiFoodInputDialog(
    query: String,
    uiState: AiFoodInputUiState,
    onQueryChange: (String) -> Unit,
    onParse: () -> Unit,
    onFoodSelected: (ParsedFoodEntry) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
        title = { Text(stringResource(Res.string.headline_ai_food_input)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (uiState) {
                    AiFoodInputUiState.Input ->
                        OutlinedTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(Res.string.label_food_description)) },
                            placeholder = {
                                Text(stringResource(Res.string.placeholder_food_description))
                            },
                            minLines = 2,
                            maxLines = 4,
                        )

                    AiFoodInputUiState.Loading -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            text = stringResource(Res.string.neutral_ai_parsing),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    is AiFoodInputUiState.Error ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(Res.string.headline_something_went_wrong),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = uiState.error.asMessage(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                    is AiFoodInputUiState.Success ->
                        if (uiState.entries.isEmpty()) {
                            Text(
                                text = stringResource(Res.string.neutral_ai_no_results),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(uiState.entries, key = { it.foodName }) { entry ->
                                    ParsedFoodListItem(
                                        entry = entry,
                                        onClick = { onFoodSelected(entry) },
                                    )
                                }
                            }
                        }
                }
            }
        },
        confirmButton = {
            when (uiState) {
                AiFoodInputUiState.Input ->
                    FilledTonalButton(
                        onClick = onParse,
                        shapes = ButtonDefaults.shapes(),
                        enabled = query.isNotBlank(),
                    ) {
                        Text(stringResource(Res.string.action_parse))
                    }

                is AiFoodInputUiState.Error ->
                    FilledTonalButton(onClick = onParse, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(Res.string.action_retry))
                    }

                AiFoodInputUiState.Loading -> CircularProgressIndicator()

                is AiFoodInputUiState.Success ->
                    TextButton(onClick = onDismissRequest, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(Res.string.action_close))
                    }
            }
        },
        dismissButton = {
            if (uiState !is AiFoodInputUiState.Success) {
                TextButton(onClick = onDismissRequest, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        },
    )
}

@Composable
private fun ParsedFoodListItem(entry: ParsedFoodEntry, onClick: () -> Unit) {
    val nutritionFacts = entry.nutritionFacts
    val interactionSource = remember { MutableInteractionSource() }

    FoodSearchListItem(
        headline = entry.foodName,
        proteins = nutritionFacts[Nutrient.Proteins].value,
        carbohydrates = nutritionFacts[Nutrient.Carbohydrates].value,
        fats = nutritionFacts[Nutrient.Fats].value,
        energy = nutritionFacts.energy.value,
        quantity = {
            entry.portion?.let { portion -> Text(portion.stringResource()) }
        },
        image = null,
        onClick = onClick,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.medium,
    )
}
