/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.screencapture.record.camera.domain.interactor

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.domain.interactor.SharedPreferencesInteractor
import com.android.systemui.screencapture.common.ScreenCapture
import com.android.systemui.screencapture.common.ScreenCaptureScope
import com.android.systemui.screencapture.common.ScreenCaptureStartable
import com.android.systemui.screencapture.record.domain.interactor.ScreenCaptureRecordParametersInteractor
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

private const val FILE_NAME = "screen_capture_prefs"
private const val HINTS_COUNT_NAME = "hints_count"

private const val HINT_MAX_SHOW_COUNT = 2

@ScreenCaptureScope
class ScreenCaptureCameraHintInteractor
@Inject
constructor(
    prefsInteractor: SharedPreferencesInteractor,
    @Background private val backgroundContext: CoroutineContext,
    @ScreenCapture private val coroutineScope: CoroutineScope,
    private val screenCaptureRecordParametersInteractor: ScreenCaptureRecordParametersInteractor,
) : ScreenCaptureStartable {

    private val prefs: Flow<SharedPreferences> =
        prefsInteractor
            .sharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .flowOn(backgroundContext)
            .stateIn(coroutineScope, SharingStarted.WhileSubscribed(), null)
            .filterNotNull()
    private var _shouldShowHint: Boolean by mutableStateOf(false)
    val shouldShowHint: Boolean by derivedStateOf {
        _shouldShowHint && screenCaptureRecordParametersInteractor.shouldShowFrontCamera
    }

    override suspend fun start() {
        prefs
            .onEach { _shouldShowHint = it.getHintsCount() < HINT_MAX_SHOW_COUNT }
            .launchIn(coroutineScope)
    }

    suspend fun onHintShown() {
        _shouldShowHint = false
        withContext(backgroundContext) {
            with(prefs.first()) {
                edit(commit = true) { putInt(HINTS_COUNT_NAME, getHintsCount() + 1) }
            }
        }
    }

    private fun SharedPreferences.getHintsCount(): Int = getInt(HINTS_COUNT_NAME, 0)
}
