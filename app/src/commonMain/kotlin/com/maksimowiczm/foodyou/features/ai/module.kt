package com.maksimowiczm.foodyou.features.ai

import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf

internal fun Module.ai() {
    viewModelOf(::AiFoodInputViewModel)
    factoryOf(::CreateProductFromParsedFood)
}
