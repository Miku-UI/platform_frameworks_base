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
import android.graphics.drawable.TestStubDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.qs.panels.domain.model.TextFeedbackModel
import com.android.systemui.qs.pipeline.data.repository.FakeInstalledTilesComponentRepository
import com.android.systemui.qs.pipeline.data.repository.fakeInstalledTilesRepository
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfig
import com.android.systemui.qs.tiles.base.shared.model.populateQsTileConfigProvider
import com.android.systemui.qs.tiles.impl.airplane.qsAirplaneModeTileConfig
import com.android.systemui.qs.tiles.impl.flashlight.qsFlashlightTileConfig
import com.android.systemui.settings.fakeUserTracker
import com.android.systemui.settings.userTracker
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.mock
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.runner.RunWith
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class TextFeedbackInteractorTest : SysuiTestCase() {
    private val kosmos =
        testKosmos().apply {
            populateQsTileConfigProvider()
            whenever(fakeUserTracker.userContext.packageManager).thenReturn(mock())
        }

    private val underTest = kosmos.textFeedbackInteractor

    @Test
    fun noFeedback() =
        with(kosmos) {
            runTest {
                val textFeedback by collectLastValue(underTest.textFeedback)

                assertThat(textFeedback).isEqualTo(TextFeedbackModel.NoFeedback)
            }
        }

    @Test
    fun knownTileTextFeedback() =
        with(kosmos) {
            runTest {
                val textFeedback by collectLastValue(underTest.textFeedback)

                underTest.requestShowFeedback(qsAirplaneModeTileConfig.tileSpec)
                runCurrent()

                assertThat(textFeedback).isEqualTo(qsAirplaneModeTileConfig.toTextFeedbackModel())
            }
        }

    @Test
    fun unknownTileTextFeedback_noFeedback() =
        with(kosmos) {
            runTest {
                val textFeedback by collectLastValue(underTest.textFeedback)

                underTest.requestShowFeedback(TileSpec.create("unknown"))
                runCurrent()

                assertThat(textFeedback).isEqualTo(TextFeedbackModel.NoFeedback)
            }
        }

    @Test
    fun customTile_notInstalled_NoFeedback() =
        with(kosmos) {
            runTest {
                val textFeedback by collectLastValue(underTest.textFeedback)

                underTest.requestShowFeedback(CUSTOM_TILE_SPEC)
                runCurrent()

                assertThat(textFeedback).isEqualTo(TextFeedbackModel.NoFeedback)
            }
        }

    @Test
    fun customTile_installed_loadedIconAndLabel() =
        with(kosmos) {
            runTest {
                val textFeedback by collectLastValue(underTest.textFeedback)
                fakeInstalledTilesRepository.setInstalledServicesForUser(
                    userTracker.userId,
                    listOf(
                        FakeInstalledTilesComponentRepository.ServiceInfo(
                            CUSTOM_TILE_SPEC.componentName,
                            serviceName = SERVICE_NAME,
                            serviceIcon = SERVICE_ICON,
                        )
                    ),
                )
                runCurrent()

                underTest.requestShowFeedback(CUSTOM_TILE_SPEC)
                runCurrent()

                assertThat(textFeedback)
                    .isEqualTo(
                        TextFeedbackModel.LoadedTextFeedback(
                            label = SERVICE_NAME,
                            icon = Icon.Loaded(SERVICE_ICON, null),
                        )
                    )
            }
        }

    @Test
    fun customTile_installed_differentUser_noFeedback() =
        with(kosmos) {
            runTest {
                val textFeedback by collectLastValue(underTest.textFeedback)
                fakeInstalledTilesRepository.setInstalledServicesForUser(
                    userTracker.userId + 1,
                    listOf(
                        FakeInstalledTilesComponentRepository.ServiceInfo(
                            CUSTOM_TILE_SPEC.componentName,
                            serviceName = SERVICE_NAME,
                            serviceIcon = SERVICE_ICON,
                        )
                    ),
                )
                runCurrent()

                underTest.requestShowFeedback(CUSTOM_TILE_SPEC)
                runCurrent()

                assertThat(textFeedback).isEqualTo(TextFeedbackModel.NoFeedback)
            }
        }

    @Test
    fun feedbackRequestRemainsVisibleForSomeTime() =
        with(kosmos) {
            runTest {
                val feedbackRequest by collectLastValue(underTest.textFeedback)

                underTest.requestShowFeedback(qsAirplaneModeTileConfig.tileSpec)
                runCurrent()

                testScope.advanceTimeBy(TextFeedbackInteractor.CLEAR_DELAY - 1.milliseconds)

                assertThat(feedbackRequest)
                    .isEqualTo(qsAirplaneModeTileConfig.toTextFeedbackModel())
            }
        }

    @Test
    fun feedbackRequest_afterClearDelay_noFeedback() =
        with(kosmos) {
            runTest {
                val feedbackRequest by collectLastValue(underTest.textFeedback)

                underTest.requestShowFeedback(qsAirplaneModeTileConfig.tileSpec)
                runCurrent()

                testScope.advanceTimeBy(TextFeedbackInteractor.CLEAR_DELAY + 1.milliseconds)
                runCurrent()

                assertThat(feedbackRequest).isEqualTo(TextFeedbackModel.NoFeedback)
            }
        }

    @Test
    fun feedbackRequest_thenAnotherFeedbackRequest_timerRestarted() =
        with(kosmos) {
            runTest {
                val textFeedback by collectLastValue(underTest.textFeedback)

                underTest.requestShowFeedback(qsAirplaneModeTileConfig.tileSpec)
                runCurrent()

                testScope.advanceTimeBy(TextFeedbackInteractor.CLEAR_DELAY - 1.milliseconds)
                runCurrent()

                underTest.requestShowFeedback(qsFlashlightTileConfig.tileSpec)

                assertThat(textFeedback).isEqualTo(qsFlashlightTileConfig.toTextFeedbackModel())

                testScope.advanceTimeBy(TextFeedbackInteractor.CLEAR_DELAY - 1.milliseconds)

                assertThat(textFeedback).isEqualTo(qsFlashlightTileConfig.toTextFeedbackModel())

                testScope.advanceTimeBy(2.milliseconds)

                assertThat(textFeedback).isEqualTo(TextFeedbackModel.NoFeedback)
            }
        }

    @Test
    fun feedbackRequest_thenSameTileRequest_timerRestarted() =
        with(kosmos) {
            runTest {
                val textFeedback by collectLastValue(underTest.textFeedback)

                underTest.requestShowFeedback(qsAirplaneModeTileConfig.tileSpec)
                runCurrent()

                testScope.advanceTimeBy(TextFeedbackInteractor.CLEAR_DELAY - 1.milliseconds)
                runCurrent()

                underTest.requestShowFeedback(qsAirplaneModeTileConfig.tileSpec)
                testScope.advanceTimeBy(TextFeedbackInteractor.CLEAR_DELAY - 1.milliseconds)

                assertThat(textFeedback).isEqualTo(qsAirplaneModeTileConfig.toTextFeedbackModel())

                testScope.advanceTimeBy(2.milliseconds)

                assertThat(textFeedback).isEqualTo(TextFeedbackModel.NoFeedback)
            }
        }

    companion object {
        private fun QSTileConfig.toTextFeedbackModel(): TextFeedbackModel.TextFeedback {
            return TextFeedbackModel.TextFeedback(uiConfig.labelRes, uiConfig.iconRes)
        }

        private val CUSTOM_TILE_SPEC = TileSpec.create(ComponentName("pkg", "srv"))
        private val SERVICE_NAME = "TileService"
        private val SERVICE_ICON = TestStubDrawable("tile_service_icon")
    }
}
