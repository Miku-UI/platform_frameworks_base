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

package com.android.systemui.qs.panels.data.repository

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.qs.panels.data.model.TextFeedbackRequestModel
import com.android.systemui.qs.pipeline.shared.TileSpec
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Indicates that feedback should be shown for a particular tile. This feedback will be kept unless
 * there are no subscribers to [textFeedback].
 */
@SysUISingleton
class ToggleTextFeedbackRepository @Inject constructor() : ExclusiveActivatable() {

    private val _textFeedback =
        MutableStateFlow<Pair<TextFeedbackRequestModel, Long>>(
            TextFeedbackRequestModel.NoFeedback to NO_FEEDBACK_SEQ_NUMBER
        )

    /** Text feedback to show now (or [TextFeedbackRequestModel.NoFeedback]). */
    val textFeedback: Flow<TextFeedbackRequestModel> = _textFeedback.map { it.first }

    /** Requests that text feedback be shown for [tile]. */
    fun setTextFeedback(tile: TileSpec) {
        _textFeedback.value =
            TextFeedbackRequestModel.FeedbackForTile(tile) to seqNumber.getAndIncrement()
    }

    override suspend fun onActivated(): Nothing {
        _textFeedback.subscriptionCount.collect {
            if (it == 0) {
                clearTextFeedback()
            }
        }
    }

    /** Clears the current set feedback. */
    fun clearTextFeedback() {
        _textFeedback.value = TextFeedbackRequestModel.NoFeedback to NO_FEEDBACK_SEQ_NUMBER
    }

    private val seqNumber = AtomicLong(0L)

    companion object {
        private const val NO_FEEDBACK_SEQ_NUMBER = -1L
    }
}
