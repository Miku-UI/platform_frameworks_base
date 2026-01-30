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

package com.android.systemui.keyguard.ui.composable.elements

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.dimensionResource
import com.android.compose.animation.scene.ElementContentScope
import com.android.systemui.keyguard.ui.viewmodel.LockscreenLowerRegionViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.log.dagger.KeyguardBlueprintLog
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElement
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.IndicationArea
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Shortcuts
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementProvider
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope.Companion.LockscreenElement
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import javax.inject.Inject
import kotlin.collections.List

/** Provides a combined element for all lockscreen ui above the lock icon */
class LockscreenLowerRegionElementProvider
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    @KeyguardBlueprintLog private val blueprintLog: LogBuffer,
    private val viewModelFactory: LockscreenLowerRegionViewModel.Factory,
) : LockscreenElementProvider {
    private val logger = Logger(blueprintLog, "LockscreenLowerRegionElementProvider")
    override val elements: List<LockscreenElement> by lazy { listOf(LowerRegionElement()) }

    private inner class LowerRegionElement : LockscreenElement {
        override val key = LockscreenElementKeys.Region.Lower
        override val context = this@LockscreenLowerRegionElementProvider.context

        @Composable
        override fun LockscreenScope<ElementContentScope>.LockscreenElement() {
            val viewModel = rememberViewModel("LockscreenLowerRegion") { viewModelFactory.create() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.navigationBarsPadding()
                        .fillMaxWidth()
                        .padding(
                            horizontal =
                                dimensionResource(R.dimen.keyguard_affordance_horizontal_offset)
                        ),
            ) {
                Box(
                    Modifier.graphicsLayer { translationX = viewModel.unfoldTranslations.start }
                        .wrapContentHeight(Alignment.Bottom, unbounded = true)
                ) {
                    LockscreenElement(Shortcuts.Start)
                }

                Box(Modifier.weight(1f)) { LockscreenElement(IndicationArea) }

                Box(
                    Modifier.graphicsLayer { translationX = viewModel.unfoldTranslations.end }
                        .wrapContentHeight(Alignment.Bottom, unbounded = true)
                ) {
                    LockscreenElement(Shortcuts.End)
                }
            }
        }
    }
}
