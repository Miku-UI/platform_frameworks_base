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

package com.android.systemui.screencapture.record.largescreen.ui.viewmodel

import android.annotation.DrawableRes
import android.annotation.SuppressLint
import android.content.Context
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.UiBackground
import com.android.systemui.res.R as SystemRes
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

data class ScreenCaptureIcons(
    val screenshotToolbar: Icon,
    val screenshotToolbarUnselected: Icon,
    val screenRecord: Icon,
    val fullscreen: Icon,
    val region: Icon,
    val appWindow: Icon,
    val moreOptions: Icon,
    val showClicks: Icon,
    val deviceAudio: Icon,
    val microphone: Icon,
    val frontCamera: Icon,
)

class ScreenCaptureIconProvider
@Inject
constructor(
    @Application private val context: Context,
    @UiBackground private val uiBackgroundContext: CoroutineContext,
) {
    private val _icons = MutableStateFlow<ScreenCaptureIcons?>(null)

    /** Static set of icons used in the UI. */
    val icons = _icons.asStateFlow()

    /** Loads all icon drawables in the UI background thread and emits them to [icons]. */
    suspend fun collectIcons() {
        flow {
                emit(
                    ScreenCaptureIcons(
                        screenshotToolbar = loadIcon(SystemRes.drawable.ic_screen_capture_camera),
                        screenshotToolbarUnselected =
                            loadIcon(SystemRes.drawable.ic_screen_capture_camera_outline),
                        screenRecord = loadIcon(SystemRes.drawable.ic_screenrecord),
                        fullscreen = loadIcon(SystemRes.drawable.ic_screen_capture_fullscreen),
                        region = loadIcon(SystemRes.drawable.ic_screen_capture_region),
                        appWindow = loadIcon(SystemRes.drawable.ic_screen_capture_window),
                        moreOptions = loadIcon(SystemRes.drawable.ic_settings),
                        showClicks = loadIcon(SystemRes.drawable.ic_web_traffic),
                        deviceAudio = loadIcon(SystemRes.drawable.ic_speaker_rounded),
                        microphone = loadIcon(SystemRes.drawable.ic_mic_expressive),
                        frontCamera = loadIcon(SystemRes.drawable.ic_person_filled),
                    )
                )
            }
            .collect(_icons)
    }

    /**
     * Load the icon drawables in the UI background thread to avoid loading them on the main UI
     * thread which can cause UI jank or dropped frames.
     */
    @SuppressLint("UseCompatLoadingForDrawables")
    private suspend fun loadIcon(@DrawableRes resourceId: Int): Icon.Loaded {
        val drawable = withContext(uiBackgroundContext) { context.getDrawable(resourceId)!! }
        return Icon.Loaded(drawable = drawable, resId = resourceId, contentDescription = null)
    }
}
