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

package com.android.systemui.qs.panels.domain.interactor

import android.content.ComponentName
import androidx.annotation.VisibleForTesting
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.qs.panels.data.model.TextFeedbackRequestModel
import com.android.systemui.qs.panels.data.repository.ToggleTextFeedbackRepository
import com.android.systemui.qs.panels.domain.model.TextFeedbackModel
import com.android.systemui.qs.pipeline.data.repository.InstalledTilesComponentRepository
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfigProvider
import com.android.systemui.settings.UserTracker
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
@SysUISingleton
class TextFeedbackInteractor
@Inject
constructor(
    private val repository: ToggleTextFeedbackRepository,
    private val qsConfigProvider: QSTileConfigProvider,
    private val installedTilesComponentRepository: InstalledTilesComponentRepository,
    private val userTracker: UserTracker,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) : ExclusiveActivatable() {

    /**
     * The data for the current Text Feedback for a tile to show. The feedback will go back to
     * [TextFeedbackModel.NoFeedback] after [CLEAR_DELAY] if no new request is sent through
     * [requestShowFeedback]. Alternatively, if a new request is passed (possibly for the same
     * [TileSpec]), this expiration timer will be restarted.
     */
    val textFeedback =
        repository.textFeedback
            .transformLatest {
                emit(it)
                if (it != TextFeedbackRequestModel.NoFeedback) {
                    delay(CLEAR_DELAY)
                    repository.clearTextFeedback()
                }
            }
            .distinctUntilChanged()
            .mapLatest {
                when (it) {
                    is TextFeedbackRequestModel.NoFeedback -> TextFeedbackModel.NoFeedback
                    is TextFeedbackRequestModel.FeedbackForTile -> {
                        val spec = it.tile
                        when {
                            spec is TileSpec.CustomTileSpec ->
                                loadCustomTileDefaults(spec.componentName)
                            qsConfigProvider.hasConfig(spec.spec) -> {
                                val config = qsConfigProvider.getConfig(spec.spec)
                                TextFeedbackModel.TextFeedback(
                                    nameResId = config.uiConfig.labelRes,
                                    iconResId = config.uiConfig.iconRes,
                                )
                            }
                            else -> TextFeedbackModel.NoFeedback
                        }
                    }
                }
            }

    /**
     * Requests that text feedback is shown for [tile]. The text feedback will disappear after some
     * time, or change if a new request arrives.
     */
    fun requestShowFeedback(tile: TileSpec) {
        repository.setTextFeedback(tile)
    }

    private suspend fun loadCustomTileDefaults(componentName: ComponentName): TextFeedbackModel {
        return withContext(backgroundDispatcher) {
            val serviceInfo =
                installedTilesComponentRepository
                    .getInstalledTilesServiceInfos(userTracker.userId)
                    .firstOrNull { it.componentName == componentName }
                    ?: return@withContext TextFeedbackModel.NoFeedback
            val packageManager = userTracker.userContext.packageManager
            val label = serviceInfo.loadLabel(packageManager)
            val icon =
                serviceInfo.loadIcon(packageManager)
                    ?: return@withContext TextFeedbackModel.NoFeedback
            TextFeedbackModel.LoadedTextFeedback(label.toString(), Icon.Loaded(icon, null))
        }
    }

    override suspend fun onActivated(): Nothing {
        repository.activate()
    }

    companion object {
        /** 2 second delay before clearing the current feedback. Matches short Toast delay */
        @VisibleForTesting val CLEAR_DELAY = 2.seconds
    }
}
