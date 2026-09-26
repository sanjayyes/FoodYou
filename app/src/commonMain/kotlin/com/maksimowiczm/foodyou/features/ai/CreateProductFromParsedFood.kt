package com.maksimowiczm.foodyou.features.ai

import com.maksimowiczm.foodyou.ai.domain.ParsedFoodEntry
import com.maksimowiczm.foodyou.common.domain.food.FoodName
import com.maksimowiczm.foodyou.userproduct.application.UserProductService
import com.maksimowiczm.foodyou.userproduct.domain.UserProduct
import com.maksimowiczm.foodyou.userproduct.domain.UserProductCommand
import com.maksimowiczm.foodyou.userproduct.domain.UserProductId

/**
 * Persists a [ParsedFoodEntry] as a user product.
 *
 * The parsed portion becomes the product's serving quantity; the nutrition facts are already
 * normalized to 100 g, which is what the food domain expects.
 */
class CreateProductFromParsedFood(private val userProductService: UserProductService) {

    /** Creates the product and returns its id. */
    suspend operator fun invoke(entry: ParsedFoodEntry): UserProductId {
        val id = UserProductId()

        userProductService.handle(
            id = id,
            command =
                UserProductCommand.Create(
                    product =
                        UserProduct(
                            id = id,
                            name = FoodName(fallback = entry.foodName),
                            brand = null,
                            barcode = null,
                            note = null,
                            image = null,
                            nutritionFacts = entry.nutritionFacts,
                            servingQuantity = entry.portion,
                            packageQuantity = null,
                            isLiquid = entry.isLiquid,
                        )
                ),
        )

        return id
    }
}
