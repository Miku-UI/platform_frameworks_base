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

package com.android.systemui.screencapture.common.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureAppContent
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope

/** Data for a UI to display app content. */
class AppContentViewModel
@AssistedInject
constructor(
    @Assisted override val model: ScreenCaptureAppContent,
    @Application private val context: Context,
) : TargetViewModel, HydratedActivatable() {

    override var icon by mutableStateOf<Result<Bitmap>?>(null)
        private set

    override val label: Result<CharSequence>? = Result.success(model.label)

    override val thumbnail: Result<Bitmap>? =
        model.thumbnail?.let { Result.success(it) }
            ?: Result.failure(
                IllegalStateException(
                    "No thumbnail for content ${model.packageName} ${model.contentId}"
                )
            )

    override val backgroundColorOpaque: Color = Color.Black

    override suspend fun onActivated() {
        coroutineScope {
            launchTraced("AppContentViewModel#icon") {
                val drawable =
                    try {
                        model.icon?.loadDrawable(context)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        null
                    }
                        ?: try {
                            context.packageManager.getApplicationIcon(model.packageName)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            null
                        }

                icon =
                    if (drawable != null) {
                        Result.success(drawable.toBitmap())
                    } else {
                        Result.failure(
                            IllegalStateException("No icon available for ${model.packageName}")
                        )
                    }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(model: ScreenCaptureAppContent): AppContentViewModel
    }
}
