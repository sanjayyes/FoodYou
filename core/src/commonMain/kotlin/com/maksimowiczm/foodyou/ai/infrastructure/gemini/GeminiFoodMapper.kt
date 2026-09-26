package com.maksimowiczm.foodyou.ai.infrastructure.gemini

import com.maksimowiczm.foodyou.ai.domain.ParsedFoodEntry
import com.maksimowiczm.foodyou.common.domain.VolumeUnit
import com.maksimowiczm.foodyou.common.domain.Weight
import com.maksimowiczm.foodyou.common.domain.food.AbsoluteQuantity
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.grams
import com.maksimowiczm.foodyou.common.domain.kilocalories
import com.maksimowiczm.foodyou.common.domain.toVolume

private const val KILOCALORIES_PER_GRAM_PROTEIN = 4.0
private const val KILOCALORIES_PER_GRAM_CARBOHYDRATE = 4.0
private const val KILOCALORIES_PER_GRAM_FAT = 9.0

/**
 * Derives energy from macronutrients using Atwater factors.
 *
 * Used when the model does not report energy itself.
 */
internal fun deriveEnergyKilocalories(
    proteinsGrams: Double,
    carbohydratesGrams: Double,
    fatsGrams: Double,
): Double =
    proteinsGrams * KILOCALORIES_PER_GRAM_PROTEIN +
        carbohydratesGrams * KILOCALORIES_PER_GRAM_CARBOHYDRATE +
        fatsGrams * KILOCALORIES_PER_GRAM_FAT

/** Maps a model response onto the food domain, dropping implausible values. */
internal fun GeminiParsedFood.toDomain(): ParsedFoodEntry {
    val portion =
        portionWeightGrams.orIfPositive()?.let { AbsoluteQuantity.Weight(it.grams) }
            ?: portionVolumeMilliliters.orIfPositive()?.let {
                AbsoluteQuantity.Volume(it.toVolume(VolumeUnit.Milliliters))
            }

    val energy =
        energyKilocalories.orIfPositive()
            ?: deriveEnergyKilocalories(
                proteinsGrams = proteinsGrams,
                carbohydratesGrams = carbohydratesGrams,
                fatsGrams = fatsGrams,
            )

    return ParsedFoodEntry(
        foodName = name.trim(),
        portion = portion,
        nutritionFacts =
            NutritionFacts(
                proteins = proteinsGrams.asWeight(),
                carbohydrates = carbohydratesGrams.asWeight(),
                fats = fatsGrams.asWeight(),
                energy = NutrientValue.Complete(energy.kilocalories),
                sugars = sugarsGrams.asWeight(),
                dietaryFiber = dietaryFiberGrams.asWeight(),
                salt = saltGrams.asWeight(),
            ),
    )
}

/** Returns the value only when it is a usable, strictly positive measurement. */
private fun Double?.orIfPositive(): Double? = this?.takeIf { it.isFinite() && it > 0.0 }

private fun Double?.asWeight(): NutrientValue<Weight> =
    when (val weight = orIfPositive()) {
        null -> NutrientValue.Incomplete(null)
        else -> NutrientValue.Complete(weight.grams)
    }
