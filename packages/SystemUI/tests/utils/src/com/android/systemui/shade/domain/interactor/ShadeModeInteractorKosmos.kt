/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.shade.domain.interactor

import android.content.testableContext
import android.provider.Settings
import androidx.compose.ui.Alignment
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.display.data.repository.displayStateRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.log.table.logcatTableLogBuffer
import com.android.systemui.res.R
import com.android.systemui.shade.data.repository.shadeConfigRepository
import com.android.systemui.shade.shared.flag.DualShadeFlag
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.shared.settings.data.repository.secureSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

val Kosmos.shadeModeInteractor by Fixture {
    ShadeModeInteractorImpl(
        applicationScope = applicationCoroutineScope,
        shadeConfigRepository = shadeConfigRepository,
        tableLogBuffer = logcatTableLogBuffer(this, "sceneFrameworkTableLogBuffer"),
    )
}

var Kosmos.fakeShadeModeInteractor: FakeShadeModeInteractorImpl by Fixture {
    FakeShadeModeInteractorImpl()
}

/*
 * A fake ShadeModeInteractor for lightweight testing; for classes that are instantiated with a
 * ShadeModeInteractor but whose tests don't need to mutate the internal state of applicationScope,
 * shadeConfigRepository, or tableLogBuffer.
 *
 * Example usage via its Fixture:
 *
 * private val fakeShadeModeInteractor = kosmos.fakeShadeModeInteractor
 *
 * // To enable Split Shade for a unit test
 * fakeShadeModeInteractor.shadeMode = MutableStateFlow(ShadeMode.Split)
 *
 */
class FakeShadeModeInteractorImpl : ShadeModeInteractor {
    override var shadeMode: StateFlow<ShadeMode> = MutableStateFlow(ShadeMode.Dual)
    override val isFullWidthShade: StateFlow<Boolean> = MutableStateFlow(false)
    override val notificationStackHorizontalAlignment: StateFlow<Alignment.Horizontal> =
        MutableStateFlow(Alignment.CenterHorizontally)
    override var isSplitShade: Boolean = false
}

val Kosmos.shadeMode by Fixture { shadeModeInteractor.shadeMode }

// TODO(b/391578667): Make this user-aware once supported by FakeSecureSettingsRepository.
/**
 * Enables the Dual Shade setting, and (optionally) sets the shade layout to be wide (`true`) or
 * narrow (`false`).
 *
 * In a wide layout, notifications and quick settings shades each take up only half the screen
 * width. In a narrow layout, they each take up the entire screen width.
 *
 * @param enabledBySetting Whether to enable it via the user-visible setting (`true`), or by default
 *   (`false`).
 */
fun Kosmos.enableDualShade(wideLayout: Boolean? = null, enabledBySetting: Boolean = true) {
    check(DualShadeFlag.isEnabled) {
        "Dual Shade not supported when ${DualShadeFlag.FLAG_NAME} is disabled."
    }
    if (enabledBySetting) {
        overrideResource(com.android.settingslib.R.bool.config_useDualShadeSetting, true)
        runBlocking { secureSettingsRepository.setBoolean(Settings.Secure.DUAL_SHADE, true) }
    } else {
        overrideResource(com.android.settingslib.R.bool.config_useDualShadeSetting, false)
    }
    fakeConfigurationRepository.onAnyConfigurationChange()

    if (wideLayout != null) {
        overrideLargeScreenResources(isLargeScreen = wideLayout)
        displayStateRepository.setIsWideScreen(wideLayout)
    }
}

// TODO(b/391578667): Make this user-aware once supported by FakeSecureSettingsRepository.
fun Kosmos.disableDualShade(disabledBySetting: Boolean = true) {
    if (disabledBySetting) {
        overrideResource(com.android.settingslib.R.bool.config_useDualShadeSetting, true)
        runBlocking { secureSettingsRepository.setBoolean(Settings.Secure.DUAL_SHADE, false) }
    } else {
        overrideResource(com.android.settingslib.R.bool.config_useDualShadeSetting, false)
        overrideResource(R.bool.config_dualShadeEnabledByDefault, false)
    }
    fakeConfigurationRepository.onAnyConfigurationChange()
}

fun Kosmos.enableSingleShade(wideLayout: Boolean = false) {
    disableDualShade()
    overrideLargeScreenResources(isLargeScreen = wideLayout)
    overrideResource(R.bool.config_use_split_notification_shade, false)
    fakeConfigurationRepository.onConfigurationChange()
    displayStateRepository.setIsWideScreen(wideLayout)
    displayStateRepository.setIsLargeScreen(false)
}

fun Kosmos.enableSplitShade() {
    check(!DualShadeFlag.isEnabled) {
        "Split Shade not supported when ${DualShadeFlag.FLAG_NAME} is enabled."
    }
    disableDualShade()
    overrideLargeScreenResources(isLargeScreen = true)
    displayStateRepository.setIsWideScreen(true)
    displayStateRepository.setIsLargeScreen(true)
}

private fun Kosmos.overrideLargeScreenResources(isLargeScreen: Boolean) {
    overrideResource(R.bool.config_isFullWidthShade, !isLargeScreen)
    overrideResource(R.bool.config_use_split_notification_shade, isLargeScreen)
    overrideResource(R.bool.config_use_large_screen_shade_header, isLargeScreen)
    fakeConfigurationRepository.onConfigurationChange()
    fakeConfigurationRepository.onAnyConfigurationChange()
}

private fun Kosmos.overrideResource(id: Int, value: Boolean) {
    testableContext.orCreateTestableResources.addOverride(id, value)
}
