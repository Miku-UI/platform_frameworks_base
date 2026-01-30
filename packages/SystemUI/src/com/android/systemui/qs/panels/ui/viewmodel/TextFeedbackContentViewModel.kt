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

package com.android.systemui.qs.panels.ui.viewmodel

import android.content.Context
import android.graphics.drawable.Animatable
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.qualifiers.UiBackground
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.qs.panels.domain.interactor.TextFeedbackInteractor
import com.android.systemui.qs.panels.domain.model.TextFeedbackModel
import com.android.systemui.qs.pipeline.shared.TileSpec
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class TextFeedbackContentViewModel
@AssistedInject
constructor(
    private val interactor: TextFeedbackInteractor,
    @UiBackground private val loadingDispatcher: CoroutineDispatcher,
    @Assisted private val context: Context,
) : ExclusiveActivatable() {
    private val hydrator = Hydrator("TextFeedbackViewModel.hydrator")

    val textFeedback: TextFeedbackViewModel by
        hydrator.hydratedStateOf(
            traceName = "textFeedback",
            initialValue = TextFeedbackViewModel.NoFeedback,
            source = interactor.textFeedback.map { it.load(context) }.flowOn(loadingDispatcher),
        )

    fun requestShowFeedback(tile: TileSpec) {
        interactor.requestShowFeedback(tile)
    }

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    @AssistedFactory
    interface Factory {
        fun create(context: Context): TextFeedbackContentViewModel
    }

    companion object {
        fun TextFeedbackModel.load(context: Context): TextFeedbackViewModel {
            return when (this) {
                is TextFeedbackModel.NoFeedback -> TextFeedbackViewModel.NoFeedback
                is TextFeedbackModel.TextFeedback ->
                    TextFeedbackViewModel.LoadedTextFeedback(
                        text = context.getString(nameResId),
                        icon =
                            Icon.Loaded(
                                context.getDrawable(iconResId)!!.also {
                                    if (it is Animatable) {
                                        it.start()
                                        it.stop()
                                    }
                                },
                                contentDescription = null,
                                resId = iconResId,
                            ),
                    )
                is TextFeedbackModel.LoadedTextFeedback ->
                    TextFeedbackViewModel.LoadedTextFeedback(text = label, icon = icon)
            }
        }
    }
}
