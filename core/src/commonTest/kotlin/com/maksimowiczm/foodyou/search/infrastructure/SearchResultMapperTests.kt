package com.maksimowiczm.foodyou.search.infrastructure

import com.maksimowiczm.foodyou.common.domain.WeightUnit
import com.maksimowiczm.foodyou.common.domain.food.FoodName
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.toWeight
import com.maksimowiczm.foodyou.search.domain.SearchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

/**
 * Covers the search-index projection, including the round trip that a user product must survive.
 */
class SearchResultMapperTests {

    private val mapper = SearchResultMapper()

    @Test
    fun a_name_with_only_a_fallback_must_survive_the_round_trip() {
        // The AI food input creates products named with nothing but a fallback, so this is the
        // shape it actually produces. It used to persist as an all-null row and then throw
        // NoSuchElementException on read.
        val product =
            SearchResult.UserProduct(
                id = Uuid.random(),
                name = FoodName(fallback = "Egg, whole, raw"),
                brand = null,
                barcode = null,
                note = null,
                image = null,
                nutritionFacts = NutritionFacts(),
                packageQuantity = null,
                servingQuantity = null,
                isLiquid = false,
            )

        val entity = mapper.toEntity(product)
        val restored = mapper.toDomain(entity)

        assertEquals("Egg, whole, raw", (restored as SearchResult.UserProduct).name.fallback)
    }

    @Test
    fun a_translated_name_should_keep_its_translation() {
        val product =
            SearchResult.UserProduct(
                id = Uuid.random(),
                name = FoodName(english = "Egg", fallback = "Ei"),
                brand = null,
                barcode = null,
                note = null,
                image = null,
                nutritionFacts = NutritionFacts(),
                packageQuantity = null,
                servingQuantity = null,
                isLiquid = false,
            )

        val restored = mapper.toDomain(mapper.toEntity(product))

        assertEquals("Egg", (restored as SearchResult.UserProduct).name.english)
        // Pre-existing upstream lossiness, left alone here: the entity has no fallback column, so
        // the round trip reconstructs fallback from the first translated name present.
        assertEquals("Egg", restored.name.fallback)
    }

    @Test
    fun a_fallback_only_name_should_be_findable_after_persistence() {
        val product =
            SearchResult.UserProduct(
                id = Uuid.random(),
                name = FoodName(fallback = "Rye toast"),
                brand = null,
                barcode = null,
                note = null,
                image = null,
                nutritionFacts = NutritionFacts(),
                packageQuantity = null,
                servingQuantity = null,
                isLiquid = false,
            )

        val entity = mapper.toEntity(product)

        // FoodNameEntity.fallback derives from whichever column is set; an empty row throws.
        assertNotNull(entity.name.english ?: entity.name.german ?: entity.name.fallback)
        assertEquals("Rye toast", entity.name.fallback)
    }

    @Test
    fun a_recipe_with_only_a_fallback_should_survive_the_round_trip() {
        val recipe =
            SearchResult.UserRecipe(
                id = Uuid.random(),
                name = FoodName(fallback = "Porridge"),
                note = null,
                image = null,
                nutritionFacts = NutritionFacts(),
                servingWeight = 0.0.toWeight(WeightUnit.Grams),
                totalWeight = 0.0.toWeight(WeightUnit.Grams),
            )

        val restored = mapper.toDomain(mapper.toEntity(recipe))

        assertEquals("Porridge", (restored as SearchResult.UserRecipe).name.fallback)
    }
}
