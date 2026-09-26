package com.maksimowiczm.foodyou.ai.domain

import com.maksimowiczm.foodyou.common.domain.food.AbsoluteQuantity
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts

/**
 * A single food reconstructed from a natural language description.
 *
 * @property foodName display name for the food
 * @property portion the amount the user described, or `null` when it could not be determined
 * @property nutritionFacts macronutrients normalized to 100 g, matching the convention used
 *   throughout the food domain
 */
data class ParsedFoodEntry(
    val foodName: String,
    val portion: AbsoluteQuantity?,
    val nutritionFacts: NutritionFacts,
) {
    /**
     * Whether the food is measured by volume.
     *
     * Derived from [portion] rather than stored separately, so the two can never disagree.
     */
    val isLiquid: Boolean
        get() = portion is AbsoluteQuantity.Volume
}
