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

package com.android.systemui.screencapture.common.domain.interactor

import com.android.systemui.screencapture.common.ScreenCaptureUiScope
import com.android.systemui.screencapture.common.data.repository.ScreenCaptureLabelRepository
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureRecentTask
import javax.inject.Inject

/** Interactor for fetching app labels. */
@ScreenCaptureUiScope
class ScreenCaptureLabelInteractor
@Inject
constructor(private val repository: ScreenCaptureLabelRepository) {
    /** Fetch the label for the given [task]. */
    suspend fun loadLabel(task: ScreenCaptureRecentTask): Result<CharSequence> =
        if (task.component == null) {
            Result.failure(IllegalArgumentException("Component cannot be null"))
        } else {
            repository.loadLabel(task.component, task.userId)
        }
}
