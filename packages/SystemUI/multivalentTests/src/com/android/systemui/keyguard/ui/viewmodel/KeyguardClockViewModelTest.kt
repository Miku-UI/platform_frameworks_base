/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.keyguard.ui.viewmodel

import android.content.res.Configuration
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.ui.data.repository.fakeConfigurationRepository
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardClockRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.data.repository.keyguardClockRepository
import com.android.systemui.keyguard.shared.model.ClockSize
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.ui.viewmodel.KeyguardClockViewModel.ClockLayout
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.plugins.keyguard.ui.clocks.ClockConfig
import com.android.systemui.plugins.keyguard.ui.clocks.ClockController
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceConfig
import com.android.systemui.plugins.keyguard.ui.clocks.ClockFaceController
import com.android.systemui.res.R
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.statusbar.notification.data.repository.activeNotificationListRepository
import com.android.systemui.statusbar.notification.data.repository.setActiveNotifs
import com.android.systemui.statusbar.ui.fakeSystemBarUtilsProxy
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.whenever
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class KeyguardClockViewModelTest(flags: FlagsParameterization) : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { keyguardClockViewModel }
    private val res = context.resources

    @Mock private lateinit var clockController: ClockController
    @Mock private lateinit var largeClock: ClockFaceController
    @Mock private lateinit var smallClock: ClockFaceController
    @Mock private lateinit var mockConfiguration: Configuration

    private var config = ClockConfig("TEST", "Test", "")
    private var faceConfig = ClockFaceConfig()

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)

        whenever(clockController.largeClock).thenReturn(largeClock)
        whenever(clockController.smallClock).thenReturn(smallClock)
        whenever(clockController.config).thenAnswer { config }
        whenever(largeClock.config).thenAnswer { faceConfig }
        whenever(smallClock.config).thenAnswer { faceConfig }
        kosmos.fakeConfigurationRepository.onConfigurationChange(mockConfiguration)
    }

    @Test
    fun currentClockLayout_splitShadeOn_clockCentered_largeClock() =
        kosmos.runTest {
            val currentClockLayout by collectLastValue(underTest.currentClockLayout)

            enableSplitShade()
            activeNotificationListRepository.setActiveNotifs(0)
            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.AOD,
                KeyguardState.LOCKSCREEN,
            )
            keyguardClockRepository.setClockSize(ClockSize.LARGE)

            assertThat(currentClockLayout).isEqualTo(ClockLayout.LARGE_CLOCK)
        }

    @Test
    @EnableSceneContainer
    fun currentClockLayout_splitShadeOn_clockNotCentered_largeClock_splitShadeLargeClock() =
        kosmos.runTest {
            val currentClockLayout by collectLastValue(underTest.currentClockLayout)

            enableSplitShade()
            activeNotificationListRepository.setActiveNotifs(1)
            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.AOD,
                KeyguardState.LOCKSCREEN,
            )
            keyguardClockRepository.setClockSize(ClockSize.LARGE)

            assertThat(currentClockLayout).isEqualTo(ClockLayout.SPLIT_SHADE_LARGE_CLOCK)
        }

    @Test
    @EnableSceneContainer
    fun currentClockLayout_splitShadeOn_clockNotCentered_forceSmallClock_splitShadeSmallClock() =
        kosmos.runTest {
            val currentClockLayout by collectLastValue(underTest.currentClockLayout)

            enableSplitShade()
            activeNotificationListRepository.setActiveNotifs(1)
            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.AOD,
                KeyguardState.LOCKSCREEN,
            )
            fakeKeyguardClockRepository.setClockSize(ClockSize.SMALL)

            assertThat(currentClockLayout).isEqualTo(ClockLayout.SPLIT_SHADE_SMALL_CLOCK)
        }

    @Test
    @EnableSceneContainer
    fun currentClockLayout_singleShade_withNotifs_smallClock() =
        kosmos.runTest {
            val currentClockLayout by collectLastValue(underTest.currentClockLayout)

            enableSingleShade()
            activeNotificationListRepository.setActiveNotifs(1)

            assertThat(currentClockLayout).isEqualTo(ClockLayout.SMALL_CLOCK)
        }

    @Test
    fun currentClockLayout_singleShade_withoutNotifs_largeClock() =
        kosmos.runTest {
            val currentClockLayout by collectLastValue(underTest.currentClockLayout)

            enableSingleShade()
            activeNotificationListRepository.setActiveNotifs(0)

            assertThat(currentClockLayout).isEqualTo(ClockLayout.LARGE_CLOCK)
        }

    @Test
    fun hasCustomPositionUpdatedAnimation_withConfigTrue_isTrue() =
        kosmos.runTest {
            val hasCustomPositionUpdatedAnimation by
                collectLastValue(underTest.hasCustomPositionUpdatedAnimation)

            keyguardClockRepository.setClockSize(ClockSize.LARGE)
            faceConfig = ClockFaceConfig(hasCustomPositionUpdatedAnimation = true)
            fakeKeyguardClockRepository.setCurrentClock(clockController)

            assertThat(hasCustomPositionUpdatedAnimation).isEqualTo(true)
        }

    @Test
    fun hasCustomPositionUpdatedAnimation_withConfigFalse_isFalse() =
        kosmos.runTest {
            val hasCustomPositionUpdatedAnimation by
                collectLastValue(underTest.hasCustomPositionUpdatedAnimation)

            keyguardClockRepository.setClockSize(ClockSize.LARGE)
            faceConfig = ClockFaceConfig(hasCustomPositionUpdatedAnimation = false)
            fakeKeyguardClockRepository.setCurrentClock(clockController)

            assertThat(hasCustomPositionUpdatedAnimation).isEqualTo(false)
        }

    @Test
    fun isLargeClockVisible_whenLargeClockSize_isTrue() =
        kosmos.runTest {
            val value by collectLastValue(underTest.isLargeClockVisible)
            keyguardClockRepository.setClockSize(ClockSize.LARGE)
            assertThat(value).isEqualTo(true)
        }

    @Test
    @DisableSceneContainer
    fun isLargeClockVisible_whenSmallClockSize_isFalse() =
        kosmos.runTest {
            val value by collectLastValue(underTest.isLargeClockVisible)
            keyguardClockRepository.setClockSize(ClockSize.SMALL)
            assertThat(value).isEqualTo(false)
        }

    @Test
    @EnableSceneContainer
    fun testSmallClockTop_splitShade_sceneContainerOn() =
        kosmos.runTest {
            enableSplitShade()
            fakeSystemBarUtilsProxy.fakeKeyguardStatusBarHeight = KEYGUARD_STATUS_BAR_HEIGHT

            val expected =
                res.getDimensionPixelSize(R.dimen.keyguard_split_shade_top_margin) -
                    KEYGUARD_STATUS_BAR_HEIGHT
            assertThat(underTest.getSmallClockTopMargin()).isEqualTo(expected)
        }

    @Test
    @DisableSceneContainer
    fun testSmallClockTop_splitShade_sceneContainerOff() =
        kosmos.runTest {
            enableSplitShade()
            fakeSystemBarUtilsProxy.fakeKeyguardStatusBarHeight = KEYGUARD_STATUS_BAR_HEIGHT

            assertThat(underTest.getSmallClockTopMargin())
                .isEqualTo(res.getDimensionPixelSize(R.dimen.keyguard_split_shade_top_margin))
        }

    @Test
    @EnableSceneContainer
    fun testSmallClockTop_singleShade_sceneContainerOn() =
        kosmos.runTest {
            enableSingleShade()
            fakeSystemBarUtilsProxy.fakeKeyguardStatusBarHeight = KEYGUARD_STATUS_BAR_HEIGHT

            assertThat(underTest.getSmallClockTopMargin())
                .isEqualTo(res.getDimensionPixelSize(R.dimen.keyguard_clock_top_margin))
        }

    @Test
    @DisableSceneContainer
    fun testSmallClockTop_singleShade_sceneContainerOff() =
        kosmos.runTest {
            enableSingleShade()
            fakeSystemBarUtilsProxy.fakeKeyguardStatusBarHeight = KEYGUARD_STATUS_BAR_HEIGHT

            val expected =
                res.getDimensionPixelSize(R.dimen.keyguard_clock_top_margin) +
                    KEYGUARD_STATUS_BAR_HEIGHT
            assertThat(underTest.getSmallClockTopMargin()).isEqualTo(expected)
        }

    @Test
    @DisableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun dateWeatherBelowSmallClock_smartspacelayoutflag_off_true() =
        kosmos.runTest {
            val result by collectLastValue(underTest.shouldDateWeatherBeBelowSmallClock)

            assertThat(result).isTrue()
        }

    @Test
    @DisableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun dateWeatherBelowLargeClock_smartspacelayoutflag_off_false() =
        kosmos.runTest {
            val result by collectLastValue(underTest.shouldDateWeatherBeBelowLargeClock)

            assertThat(result).isFalse()
        }

    @Test
    @EnableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun dateWeatherBelowSmallClock_defaultFontAndDisplaySize_shadeLayoutNotWide_false() =
        kosmos.runTest {
            enableSingleShade()
            val fontScale = 1.0f
            val screenWidthDp = 347
            mockConfiguration.fontScale = fontScale
            mockConfiguration.screenWidthDp = screenWidthDp

            val result by collectLastValue(underTest.shouldDateWeatherBeBelowSmallClock)

            assertThat(result).isFalse()
        }

    @Test
    @EnableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun dateWeatherBelowLargeClock_variousFontAndDisplaySize_true() =
        kosmos.runTest {
            mockConfiguration.fontScale = 1.0f
            mockConfiguration.screenWidthDp = 347
            val result1 by collectLastValue(underTest.shouldDateWeatherBeBelowLargeClock)
            assertThat(result1).isTrue()

            mockConfiguration.fontScale = 1.2f
            mockConfiguration.screenWidthDp = 347
            val result2 by collectLastValue(underTest.shouldDateWeatherBeBelowLargeClock)
            assertThat(result2).isTrue()

            mockConfiguration.fontScale = 1.7f
            mockConfiguration.screenWidthDp = 412
            val result3 by collectLastValue(underTest.shouldDateWeatherBeBelowLargeClock)
            assertThat(result3).isTrue()
        }

    @Test
    @EnableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun dateWeatherBelowLargeClock_variousFontAndDisplaySize_false() =
        kosmos.runTest {
            enableSingleShade()
            mockConfiguration.fontScale = 1.0f
            mockConfiguration.screenWidthDp = 310
            val result1 by collectLastValue(underTest.shouldDateWeatherBeBelowLargeClock)
            assertThat(result1).isFalse()

            mockConfiguration.fontScale = 1.5f
            mockConfiguration.screenWidthDp = 347
            val result2 by collectLastValue(underTest.shouldDateWeatherBeBelowLargeClock)
            assertThat(result2).isFalse()

            mockConfiguration.fontScale = 2.0f
            mockConfiguration.screenWidthDp = 411
            val result3 by collectLastValue(underTest.shouldDateWeatherBeBelowLargeClock)
            assertThat(result3).isFalse()
        }

    @Test
    @EnableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun dateWeatherBelowSmallClock_numberOverlapClock_variousFontAndDisplaySize_true() =
        kosmos.runTest {
            config = ClockConfig("DIGITAL_CLOCK_NUMBEROVERLAP", "Test", "")
            fakeKeyguardClockRepository.setCurrentClock(clockController)
            enableSingleShade()
            mockConfiguration.fontScale = 0.85f
            mockConfiguration.screenWidthDp = 376
            val result3 by collectLastValue(underTest.shouldDateWeatherBeBelowSmallClock)
            assertThat(result3).isTrue()
        }

    @Test
    @EnableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun dateWeatherBelowLargeClock_metroClock_variousFontAndDisplaySize_false() =
        kosmos.runTest {
            config = ClockConfig("DIGITAL_CLOCK_METRO", "Test", "")
            fakeKeyguardClockRepository.setCurrentClock(clockController)
            mockConfiguration.fontScale = 0.85f
            mockConfiguration.screenWidthDp = 375
            val result3 by collectLastValue(underTest.shouldDateWeatherBeBelowLargeClock)
            assertThat(result3).isFalse()
        }

    @Test
    @EnableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun dateWeatherBelowSmallClock_variousFontAndDisplaySize_shadeLayoutNotWide_false() =
        kosmos.runTest {
            enableSingleShade()
            mockConfiguration.fontScale = 1.0f
            mockConfiguration.screenWidthDp = 347
            val result1 by collectLastValue(underTest.shouldDateWeatherBeBelowSmallClock)
            assertThat(result1).isFalse()

            mockConfiguration.fontScale = 1.2f
            mockConfiguration.screenWidthDp = 347
            val result2 by collectLastValue(underTest.shouldDateWeatherBeBelowSmallClock)
            assertThat(result2).isFalse()

            mockConfiguration.fontScale = 1.7f
            mockConfiguration.screenWidthDp = 412
            val result3 by collectLastValue(underTest.shouldDateWeatherBeBelowSmallClock)
            assertThat(result3).isFalse()
        }

    @Test
    @EnableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun dateWeatherBelowSmallClock_variousFontAndDisplaySize_shadeLayoutWide_false() =
        kosmos.runTest {
            enableSplitShade()
            mockConfiguration.fontScale = 1.0f
            mockConfiguration.screenWidthDp = 694
            val result1 by collectLastValue(underTest.shouldDateWeatherBeBelowSmallClock)
            assertThat(result1).isFalse()

            mockConfiguration.fontScale = 1.2f
            mockConfiguration.screenWidthDp = 694
            val result2 by collectLastValue(underTest.shouldDateWeatherBeBelowSmallClock)
            assertThat(result2).isFalse()

            mockConfiguration.fontScale = 1.7f
            mockConfiguration.screenWidthDp = 824
            val result3 by collectLastValue(underTest.shouldDateWeatherBeBelowSmallClock)
            assertThat(result3).isFalse()
        }

    @Test
    @EnableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun dateWeatherBelowSmallClock_variousFontAndDisplaySize_shadeLayoutNotWide_true() =
        kosmos.runTest {
            enableSingleShade()
            mockConfiguration.fontScale = 1.0f
            mockConfiguration.screenWidthDp = 310
            val result1 by collectLastValue(underTest.shouldDateWeatherBeBelowSmallClock)
            assertThat(result1).isTrue()

            mockConfiguration.fontScale = 1.5f
            mockConfiguration.screenWidthDp = 347
            val result2 by collectLastValue(underTest.shouldDateWeatherBeBelowSmallClock)
            assertThat(result2).isTrue()

            mockConfiguration.fontScale = 2.0f
            mockConfiguration.screenWidthDp = 411
            val result3 by collectLastValue(underTest.shouldDateWeatherBeBelowSmallClock)
            assertThat(result3).isTrue()
        }

    @Test
    @EnableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun dateWeatherBelowSmallClock_variousFontAndDisplaySize_shadeLayoutWide_true() =
        kosmos.runTest {
            enableSplitShade()
            mockConfiguration.fontScale = 1.0f
            mockConfiguration.screenWidthDp = 620
            val result1 by collectLastValue(underTest.shouldDateWeatherBeBelowSmallClock)
            assertThat(result1).isTrue()

            mockConfiguration.fontScale = 1.5f
            mockConfiguration.screenWidthDp = 694
            val result2 by collectLastValue(underTest.shouldDateWeatherBeBelowSmallClock)
            assertThat(result2).isTrue()

            mockConfiguration.fontScale = 2.0f
            mockConfiguration.screenWidthDp = 822
            val result3 by collectLastValue(underTest.shouldDateWeatherBeBelowSmallClock)
            assertThat(result3).isTrue()
        }

    companion object {
        private const val KEYGUARD_STATUS_BAR_HEIGHT = 20

        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }
    }
}
