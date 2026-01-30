/*
 * Copyright (C) 2025 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.keyguard.ui.viewmodel

import android.app.WallpaperColors
import android.content.Context
import android.os.Bundle
import android.os.IBinder
import android.view.Display
import com.android.internal.policy.SystemBarUtils
import com.android.systemui.customization.clocks.R as clocksR
import com.android.systemui.keyguard.domain.interactor.KeyguardPreviewInteractor
import com.android.systemui.plugins.keyguard.ui.clocks.ClockPreviewConfig
import com.android.systemui.res.R as SysuiR
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

@AssistedFactory
interface KeyguardPreviewViewModelFactory {
    fun create(interactor: KeyguardPreviewInteractor): KeyguardPreviewViewModel
}

/** View model for the small clock view, large clock view. */
class KeyguardPreviewViewModel
@AssistedInject
constructor(@Assisted private val interactor: KeyguardPreviewInteractor) {
    val previewContext: Context
        get() = interactor.previewContext

    val request: Bundle
        get() = interactor.request

    val shouldHighlightSelectedAffordance: Boolean
        get() = interactor.shouldHighlightSelectedAffordance

    val hostToken: IBinder?
        get() = interactor.hostToken

    val targetWidth: Int
        get() = interactor.targetWidth

    val targetHeight: Int
        get() = interactor.targetHeight

    val display: Display?
        get() = interactor.display

    val wallpaperColors: WallpaperColors?
        get() = interactor.wallpaperColors

    fun buildPreviewConfig(): ClockPreviewConfig {
        return ClockPreviewConfig(
            isFullWidthShade = interactor.isFullWidthShade,
            isSceneContainerFlagEnabled = SceneContainerFlag.isEnabled,
            statusBarHeight = SystemBarUtils.getStatusBarHeight(previewContext),
            splitShadeTopMargin =
                previewContext.resources.getDimensionPixelSize(
                    SysuiR.dimen.keyguard_split_shade_top_margin
                ),
            clockTopMargin =
                previewContext.resources.getDimensionPixelSize(
                    SysuiR.dimen.keyguard_clock_top_margin
                ),
            statusViewMarginHorizontal =
                previewContext.resources.getDimensionPixelSize(
                    clocksR.dimen.status_view_margin_horizontal
                ),
        )
    }
}
