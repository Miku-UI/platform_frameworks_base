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

package com.android.systemui.qs.tiles

import android.os.Handler
import android.platform.test.annotations.EnableFlags
import android.service.quicksettings.Tile
import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.MetricsLogger
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.systemui.InstanceIdSequenceFake
import com.android.systemui.SysuiTestCase
import com.android.systemui.classifier.FalsingManagerFake
import com.android.systemui.flashlight.data.repository.startFlashlightRepository
import com.android.systemui.flashlight.domain.interactor.flashlightInteractor
import com.android.systemui.flashlight.shared.model.FlashlightModel
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.QsEventLoggerFake
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.tileimpl.QSTileImpl.DrawableIconWithRes
import com.android.systemui.qs.tiles.base.domain.model.QSTileInput
import com.android.systemui.qs.tiles.base.shared.model.FakeQSTileConfigProvider
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfigProvider
import com.android.systemui.qs.tiles.base.shared.model.QSTileUserAction
import com.android.systemui.qs.tiles.impl.flashlight.domain.interactor.FlashlightTileUserActionInteractor
import com.android.systemui.qs.tiles.impl.flashlight.domain.interactor.flashlightTileDataInteractor
import com.android.systemui.qs.tiles.impl.flashlight.ui.mapper.flashlightTileMapper
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.PolicyModule
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.capture
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@EnableFlags(com.android.systemui.Flags.FLAG_FLASHLIGHT_STRENGTH)
@SmallTest
class FlashlightTileWithLevelTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    @Captor private lateinit var inputCaptor: ArgumentCaptor<QSTileInput<FlashlightModel>>

    @Mock private lateinit var qsLogger: QSLogger

    @Mock private lateinit var qsHost: QSHost

    @Mock private lateinit var metricsLogger: MetricsLogger

    @Mock private lateinit var statusBarStateController: StatusBarStateController

    @Mock private lateinit var activityStarter: ActivityStarter

    @Mock private lateinit var uiEventLogger: QsEventLogger

    @Mock private lateinit var mockUserActionInteractor: FlashlightTileUserActionInteractor

    private val falsingManager = FalsingManagerFake()
    private lateinit var testableLooper: TestableLooper
    private lateinit var underTest: FlashlightTileWithLevel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testableLooper = TestableLooper.get(this)

        whenever(qsHost.context).thenReturn(mContext)

        underTest =
            FlashlightTileWithLevel(
                qsHost,
                uiEventLogger,
                testableLooper.looper,
                Handler(testableLooper.looper),
                falsingManager,
                metricsLogger,
                statusBarStateController,
                activityStarter,
                qsLogger,
                createAndPopulateQsTileConfigProvider(),
                kosmos.flashlightTileDataInteractor,
                mockUserActionInteractor,
                kosmos.flashlightTileMapper,
            )

        underTest.initialize()
        underTest.setListening(Object(), true)

        testableLooper.processAllMessages()
    }

    @After
    fun tearDown() {
        underTest.destroy()
        testableLooper.processAllMessages()
    }

    @Test
    fun testIcon_whenFlashlightEnabled_isOnState() =
        kosmos.runTest {
            startFlashlightRepository(true)

            flashlightInteractor.setEnabled(true)
            runCurrent()

            val state = QSTile.BooleanState()

            underTest.handleUpdateState(state, /* arg= */ null)

            val resId = R.drawable.qs_flashlight_icon_on
            assertThat(state.icon)
                .isEqualTo(DrawableIconWithRes(mContext.getDrawable(resId), resId))
        }

    @Test
    fun testIcon_whenFlashlightDisabled_isOffState() =
        kosmos.runTest {
            startFlashlightRepository(true)

            flashlightInteractor.setEnabled(false)
            runCurrent()

            val state = QSTile.BooleanState()

            underTest.handleUpdateState(state, /* arg= */ null)

            val resId = R.drawable.qs_flashlight_icon_off
            assertThat(state.icon)
                .isEqualTo(DrawableIconWithRes(mContext.getDrawable(resId), resId))
        }

    @Test
    fun testIcon_whenFlashlightUnavailablePermanently_isOffState() =
        kosmos.runTest {
            startFlashlightRepository(false)
            runCurrent()
            val state = QSTile.BooleanState()

            underTest.handleUpdateState(state, /* arg= */ null)
            runCurrent()

            val resId = R.drawable.qs_flashlight_icon_off
            assertThat(state.icon)
                .isEqualTo(DrawableIconWithRes(mContext.getDrawable(resId), resId))
        }

    @Test
    fun stateUpdatesOnChange() =
        kosmos.runTest {
            startFlashlightRepository(true)

            runCurrent()
            testableLooper.processAllMessages()
            assertThat(underTest.state.state).isEqualTo(Tile.STATE_INACTIVE)

            flashlightInteractor.setEnabled(true)
            runCurrent()
            testableLooper.processAllMessages()

            assertThat(underTest.state.state).isEqualTo(Tile.STATE_ACTIVE)
        }

    @Test
    fun handleUpdateState_withNull_updatesState() =
        kosmos.runTest {
            startFlashlightRepository(true)

            val tileState =
                QSTile.BooleanState().apply {
                    state = Tile.STATE_INACTIVE
                    secondaryLabel = "Old secondary label to be overwritten"
                }
            flashlightInteractor.setLevel(MAX_LEVEL)
            runCurrent()

            underTest.handleUpdateState(tileState, null)

            runCurrent()

            assertThat(tileState.state).isEqualTo(Tile.STATE_ACTIVE)
            assertThat(tileState.secondaryLabel).isEqualTo("100%")
        }

    @Test
    fun click_delegatesToUserActionInteractorClick() =
        kosmos.runTest {
            runCurrent()
            testableLooper.processAllMessages()

            underTest.click(null)
            runCurrent()
            testableLooper.processAllMessages()

            verify(mockUserActionInteractor).handleInput(capture(inputCaptor))

            val action = inputCaptor.value.action

            assertThat(action).isInstanceOf(QSTileUserAction.Click::class.java)
        }

    @Test
    fun secondaryClick_delegatesToUserActionInteractorToggleClick() =
        kosmos.runTest {
            runCurrent()
            testableLooper.processAllMessages()

            underTest.secondaryClick(null)
            runCurrent()
            testableLooper.processAllMessages()

            verify(mockUserActionInteractor).handleInput(capture(inputCaptor))

            val action = inputCaptor.value.action

            assertThat(action).isInstanceOf(QSTileUserAction.ToggleClick::class.java)
        }

    @Test
    fun longClick_delegatesToUserActionInteractorLongClick() =
        kosmos.runTest {
            runCurrent()
            testableLooper.processAllMessages()

            underTest.longClick(null)
            runCurrent()
            testableLooper.processAllMessages()

            verify(mockUserActionInteractor).handleInput(capture(inputCaptor))

            val action = inputCaptor.value.action

            assertThat(action).isInstanceOf(QSTileUserAction.LongClick::class.java)
        }

    @Test
    fun isAvailable_matchesDataInteractor() =
        kosmos.runTest {
            startFlashlightRepository(true)

            runCurrent()
            testableLooper.processAllMessages()

            assertThat(underTest.isAvailable).isTrue()
            assertThat(flashlightTileDataInteractor.isAvailable()).isTrue()
        }

    @Test
    fun isNotAvailable_matchesDataInteractor() =
        kosmos.runTest {
            startFlashlightRepository(false)

            runCurrent()
            testableLooper.processAllMessages()

            assertThat(underTest.isAvailable).isFalse()
            assertThat(flashlightTileDataInteractor.isAvailable()).isFalse()
        }

    companion object {

        private const val MAX_LEVEL = 45

        private val FLASHLIGHT_TILE_SPEC = TileSpec.create(PolicyModule.FLASHLIGHT_TILE_SPEC)

        private fun createAndPopulateQsTileConfigProvider(): QSTileConfigProvider {
            val logger =
                QsEventLoggerFake(UiEventLoggerFake(), InstanceIdSequenceFake(Int.MAX_VALUE))

            return FakeQSTileConfigProvider().apply {
                putConfig(FLASHLIGHT_TILE_SPEC, PolicyModule.provideFlashlightTileConfig(logger))
            }
        }
    }
}
