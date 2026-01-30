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

import android.graphics.drawable.TestStubDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.qs.panels.domain.model.TextFeedbackModel
import com.android.systemui.qs.panels.ui.viewmodel.TextFeedbackContentViewModel.Companion.load
import com.android.systemui.qs.tiles.base.shared.model.populateQsTileConfigProvider
import com.android.systemui.qs.tiles.impl.airplane.qsAirplaneModeTileConfig
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class TextFeedbackContentViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().apply { populateQsTileConfigProvider() }

    private val underTest =
        kosmos.textFeedbackContentViewModel.apply { activateIn(kosmos.testScope) }

    @Test
    fun default_NoFeedback() =
        with(kosmos) {
            runTest {
                assertThat(underTest.textFeedback).isEqualTo(TextFeedbackViewModel.NoFeedback)
            }
        }

    @Test
    fun tileFeedback_IconAndLabelLoaded() =
        with(kosmos) {
            runTest {
                underTest.requestShowFeedback(qsAirplaneModeTileConfig.tileSpec)
                runCurrent()

                assertThat(underTest.textFeedback)
                    .isEqualTo(
                        TextFeedbackViewModel.LoadedTextFeedback(
                            text = context.getString(qsAirplaneModeTileConfig.uiConfig.labelRes),
                            icon =
                                Icon.Loaded(
                                    context.getDrawable(
                                        qsAirplaneModeTileConfig.uiConfig.iconRes
                                    )!!,
                                    contentDescription = null,
                                    resId = qsAirplaneModeTileConfig.uiConfig.iconRes,
                                ),
                        )
                    )
            }
        }

    @Test
    fun loadFeedback_alreadyLoaded() =
        with(kosmos) {
            runTest {
                val label = "label"
                val icon = TestStubDrawable("drawable")

                val model = TextFeedbackModel.LoadedTextFeedback(label, Icon.Loaded(icon, null))

                assertThat(model.load(context))
                    .isEqualTo(
                        TextFeedbackViewModel.LoadedTextFeedback(label, Icon.Loaded(icon, null))
                    )
            }
        }
}
