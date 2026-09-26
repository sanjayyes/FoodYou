package com.maksimowiczm.foodyou.features.settings

import com.maksimowiczm.foodyou.features.settings.ai.AiSettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf

internal fun Module.settings() {
    viewModelOf(::SettingsViewModel)
    viewModelOf(::AiSettingsViewModel)
}
