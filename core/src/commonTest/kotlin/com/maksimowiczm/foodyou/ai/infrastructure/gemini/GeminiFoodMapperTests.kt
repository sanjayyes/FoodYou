package com.maksimowiczm.foodyou.ai.infrastructure.gemini

import com.maksimowiczm.foodyou.common.domain.VolumeUnit
import com.maksimowiczm.foodyou.common.domain.food.AbsoluteQuantity
import com.maksimowiczm.foodyou.common.domain.food.Nutrient
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.common.domain.grams
import com.maksimowiczm.foodyou.common.domain.kilocalories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeminiFoodMapperTests {

    @Test
    fun energy_should_be_derived_from_macronutrients_when_absent() {
        val energy = deriveEnergyKilocalories(10.0, 20.0, 5.0)

        assertEquals(165.0, energy) // 10*4 + 20*4 + 5*9
    }

    @Test
    fun reported_energy_should_win_over_derived_energy() {
        val entry =
            GeminiParsedFood(
                    name = "Rice",
                    energyKilocalories = 130.0,
                    proteinsGrams = 2.7,
                    carbohydratesGrams = 28.0,
                    fatsGrams = 0.3,
                )
                .toDomain()

        assertEquals(130.0, entry.nutritionFacts.energy.value?.kilocalories)
    }

    @Test
    fun nutrition_should_stay_normalized_to_100g() {
        val entry =
            GeminiParsedFood(
                    name = "Chicken breast",
                    portionWeightGrams = 180.0,
                    proteinsGrams = 31.0,
                    carbohydratesGrams = 0.0,
                    fatsGrams = 3.6,
                )
                .toDomain()

        // The 180 g portion is recorded separately; the facts remain per 100 g.
        assertEquals(31.0, entry.nutritionFacts.proteins.value?.grams)
        assertEquals(180.0, (entry.portion as AbsoluteQuantity.Weight).weight.grams)
    }

    @Test
    fun solid_portion_should_not_be_liquid() {
        val entry = GeminiParsedFood(name = "Apple", portionWeightGrams = 150.0).toDomain()

        assertTrue(entry.portion is AbsoluteQuantity.Weight)
        assertFalse(entry.isLiquid)
    }

    @Test
    fun volume_portion_should_be_liquid() {
        val entry = GeminiParsedFood(name = "Milk", portionVolumeMilliliters = 250.0).toDomain()
        val portion = entry.portion

        assertTrue(portion is AbsoluteQuantity.Volume)
        assertTrue(entry.isLiquid)
        assertEquals(250.0, portion.volume.milliliters)
        assertEquals(VolumeUnit.Milliliters, portion.volume.unit)
    }

    @Test
    fun weight_portion_should_take_precedence_over_volume() {
        val entry =
            GeminiParsedFood(
                    name = "Soup",
                    portionWeightGrams = 300.0,
                    portionVolumeMilliliters = 300.0,
                )
                .toDomain()

        assertTrue(entry.portion is AbsoluteQuantity.Weight)
    }

    @Test
    fun missing_portion_should_stay_null() {
        val entry = GeminiParsedFood(name = "Mystery food").toDomain()

        assertNull(entry.portion)
        assertFalse(entry.isLiquid)
    }

    @Test
    fun non_positive_measurements_should_be_treated_as_unknown() {
        val entry =
            GeminiParsedFood(
                    name = "Nothing",
                    portionWeightGrams = 0.0,
                    portionVolumeMilliliters = -5.0,
                    proteinsGrams = 0.0,
                    energyKilocalories = 0.0,
                )
                .toDomain()

        assertNull(entry.portion)
        assertNull(entry.nutritionFacts[Nutrient.Proteins].value)
    }

    @Test
    fun unknown_optional_nutrients_should_be_incomplete() {
        val entry = GeminiParsedFood(name = "Rice", proteinsGrams = 2.7).toDomain()

        assertEquals(NutrientValue.Complete(2.7.grams), entry.nutritionFacts[Nutrient.Proteins])
        assertNull(entry.nutritionFacts[Nutrient.Salt].value)
        assertTrue(entry.nutritionFacts[Nutrient.Salt] is NutrientValue.Incomplete)
    }

    @Test
    fun name_should_be_trimmed() {
        assertEquals("Rice", GeminiParsedFood(name = "  Rice  ").toDomain().foodName)
    }
}
