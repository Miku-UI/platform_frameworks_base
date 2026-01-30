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
 * limitations under the License.
 */

package com.android.systemui.keyguard.data.repository

import android.app.WallpaperColors
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.IBinder
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.keyguard.shared.model.ClockSizeSetting
import com.android.systemui.plugins.keyguard.ui.clocks.ClockAxisStyle
import com.android.systemui.plugins.keyguard.ui.clocks.ClockController
import com.android.systemui.plugins.keyguard.ui.clocks.ClockSettings
import com.android.systemui.shared.clocks.ClockRegistry
import com.android.systemui.shared.quickaffordance.shared.model.KeyguardPreviewConstants.KEY_CLOCK_ID
import com.android.systemui.shared.quickaffordance.shared.model.KeyguardPreviewConstants.KEY_CLOCK_STYLE
import com.android.systemui.shared.quickaffordance.shared.model.KeyguardPreviewConstants.KEY_COLORS
import com.android.systemui.shared.quickaffordance.shared.model.KeyguardPreviewConstants.KEY_DISPLAY_ID
import com.android.systemui.shared.quickaffordance.shared.model.KeyguardPreviewConstants.KEY_HIDE_CLOCK
import com.android.systemui.shared.quickaffordance.shared.model.KeyguardPreviewConstants.KEY_HIGHLIGHT_QUICK_AFFORDANCES
import com.android.systemui.shared.quickaffordance.shared.model.KeyguardPreviewConstants.KEY_HOST_TOKEN
import com.android.systemui.shared.quickaffordance.shared.model.KeyguardPreviewConstants.KEY_VIEW_HEIGHT
import com.android.systemui.shared.quickaffordance.shared.model.KeyguardPreviewConstants.KEY_VIEW_WIDTH
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@AssistedFactory
interface KeyguardPreviewRepositoryFactory {
    fun create(bundle: Bundle): KeyguardPreviewRepository
}

class KeyguardPreviewRepository
@AssistedInject
constructor(
    @Application context: Context,
    private val clockRepository: KeyguardClockRepository,
    private val clockRegistry: ClockRegistry,
    private val displayManager: DisplayManager,
    @Assisted val request: Bundle,
) {
    val hostToken: IBinder? = request.getBinder(KEY_HOST_TOKEN)
    val targetWidth: Int = request.getInt(KEY_VIEW_WIDTH)
    val targetHeight: Int = request.getInt(KEY_VIEW_HEIGHT)
    val shouldHighlightSelectedAffordance: Boolean =
        request.getBoolean(KEY_HIGHLIGHT_QUICK_AFFORDANCES, false)

    val displayId = request.getInt(KEY_DISPLAY_ID, DEFAULT_DISPLAY)
    val display: Display? = displayManager.getDisplay(displayId)
    val previewContext: Context =
        display?.let { ContextThemeWrapper(context.createDisplayContext(it), context.getTheme()) }
            ?: context

    /** [shouldHideClock] here means that we never create and bind the clock views */
    val shouldHideClock: Boolean = request.getBoolean(KEY_HIDE_CLOCK, false)
    val wallpaperColors: WallpaperColors? = request.getParcelable(KEY_COLORS)

    val requestedClockSize: MutableStateFlow<ClockSizeSetting?> = MutableStateFlow(null)

    val overrideClockSettings: ClockSettings? = run {
        val clockId = request.getString(KEY_CLOCK_ID)
        if (clockId == null) return@run null

        // clock seed color handled by KeyguardPreviewRenderer.updateClockAppearance
        return@run ClockSettings(
            clockId = clockId,
            axes =
                ClockAxisStyle {
                    request.getBundle(KEY_CLOCK_STYLE)?.let { bundle ->
                        bundle.keySet().forEach { key -> put(key, bundle.getFloat(key)) }
                    }
                },
        )
    }

    val previewClock: Flow<ClockController> =
        overrideClockSettings?.let { settings ->
            flow { emit(clockRegistry.createPreviewClockAsync(previewContext, settings).await()) }
        }
            ?: clockRepository.currentClockId.map {
                // We should create a new instance for each collect call cause in preview, the same
                // clock will be attached to a different parent view at the same time.
                clockRegistry.createCurrentClock(previewContext)
            }
}
