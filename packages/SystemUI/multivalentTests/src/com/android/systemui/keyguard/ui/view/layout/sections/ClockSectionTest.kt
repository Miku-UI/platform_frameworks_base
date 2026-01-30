/*
 * Copyright (C) 2023 The Android Open Source Project
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
 *
 */

package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.res.Resources
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.constraintlayout.widget.ConstraintSet
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.customization.clocks.R as clocksR
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.keyguard.domain.interactor.keyguardBlueprintInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardClockInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardSmartspaceInteractor
import com.android.systemui.keyguard.shared.model.ClockSize
import com.android.systemui.keyguard.ui.viewmodel.aodBurnInViewModel
import com.android.systemui.keyguard.ui.viewmodel.keyguardClockViewModel
import com.android.systemui.keyguard.ui.viewmodel.keyguardRootViewModel
import com.android.systemui.keyguard.ui.viewmodel.keyguardSmartspaceViewModel
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceUntilIdle
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds
import com.android.systemui.res.R
import com.android.systemui.shade.LargeScreenHeaderHelper
import com.android.systemui.shade.data.repository.shadeRepository
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.statusbar.notification.stack.domain.interactor.notificationsKeyguardInteractor
import com.android.systemui.statusbar.policy.fakeConfigurationController
import com.android.systemui.statusbar.ui.fakeSystemBarUtilsProxy
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
@SmallTest
@DisableSceneContainer
class ClockSectionTest : SysuiTestCase() {
    private lateinit var underTest: ClockSection

    private val resources: Resources
        get() = context.resources

    private val SMART_SPACE_DATE_WEATHER_HEIGHT: Int
        get() = resources.getDimensionPixelSize(clocksR.dimen.date_weather_view_height)

    private val ENHANCED_SMART_SPACE_HEIGHT: Int
        get() = resources.getDimensionPixelSize(clocksR.dimen.enhanced_smartspace_height)

    private val KEYGUARD_SMARTSPACE_TOP_OFFSET: Int
        get() {
            return kosmos.fakeSystemBarUtilsProxy.getStatusBarHeight() / 2 +
                context.resources.getDimensionPixelSize(
                    clocksR.dimen.keyguard_smartspace_top_offset
                )
        }

    private val LARGE_CLOCK_TOP_WITHOUT_SMARTSPACE: Int
        get() {
            return kosmos.fakeSystemBarUtilsProxy.getStatusBarHeight() +
                context.resources.getDimensionPixelSize(clocksR.dimen.small_clock_padding_top) +
                context.resources.getDimensionPixelSize(
                    clocksR.dimen.keyguard_smartspace_top_offset
                )
        }

    private val LARGE_CLOCK_TOP: Int
        get() {
            return LARGE_CLOCK_TOP_WITHOUT_SMARTSPACE +
                SMART_SPACE_DATE_WEATHER_HEIGHT +
                ENHANCED_SMART_SPACE_HEIGHT
        }

    private lateinit var kosmos: Kosmos

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)

        kosmos = testKosmos()
        with(kosmos) {
            underTest =
                ClockSection(
                    keyguardClockInteractor,
                    keyguardClockViewModel,
                    context,
                    keyguardSmartspaceViewModel,
                    { keyguardBlueprintInteractor },
                    keyguardRootViewModel,
                    aodBurnInViewModel,
                    largeScreenHeaderHelperLazy = { mock<LargeScreenHeaderHelper>() },
                )
        }
    }

    @Test
    @DisableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun testApplyDefaultConstraints_LargeClock_SplitShade() =
        kosmos.runTest {
            enableSplitShade()
            keyguardClockInteractor.setClockSize(ClockSize.LARGE)
            advanceUntilIdle()

            val cs = ConstraintSet()
            underTest.applyDefaultConstraints(cs)

            cs.assertLargeClock(topMargin = LARGE_CLOCK_TOP)
            cs.assertSmallClock()
        }

    @Test
    @EnableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun testApplyDefaultConstraints_LargeClock_SplitShade_ReactiveVariantsOn() =
        kosmos.runTest {
            enableSplitShade()
            keyguardClockInteractor.setClockSize(ClockSize.LARGE)
            advanceUntilIdle()

            val cs = ConstraintSet()
            underTest.applyDefaultConstraints(cs)

            cs.assertLargeClock(
                topMargin = KEYGUARD_SMARTSPACE_TOP_OFFSET + ENHANCED_SMART_SPACE_HEIGHT
            )
            cs.assertSmallClock()
        }

    @Test
    @DisableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun testApplyDefaultConstraints_LargeClock_SingleShade() =
        kosmos.runTest {
            val legacyUseSplitShade by collectLastValue(shadeRepository.legacyUseSplitShade)
            val isLargeClockVisible by collectLastValue(keyguardClockViewModel.isLargeClockVisible)

            enableSingleShade()
            keyguardClockInteractor.setClockSize(ClockSize.LARGE)
            notificationsKeyguardInteractor.setNotificationsFullyHidden(true)
            keyguardSmartspaceInteractor.setBcSmartspaceVisibility(VISIBLE)
            fakeConfigurationController.notifyConfigurationChanged()
            advanceUntilIdle()

            val cs = ConstraintSet()
            underTest.applyDefaultConstraints(cs)

            cs.assertLargeClock(topMargin = LARGE_CLOCK_TOP)
            cs.assertSmallClock()
        }

    @Test
    @EnableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun testApplyDefaultConstraints_LargeClock_SingleShade_reactiveVariantsOn() =
        kosmos.runTest {
            val legacyUseSplitShade by collectLastValue(shadeRepository.legacyUseSplitShade)
            val isLargeClockVisible by collectLastValue(keyguardClockViewModel.isLargeClockVisible)

            enableSingleShade()
            keyguardClockInteractor.setClockSize(ClockSize.LARGE)
            notificationsKeyguardInteractor.setNotificationsFullyHidden(true)
            keyguardSmartspaceInteractor.setBcSmartspaceVisibility(VISIBLE)
            fakeConfigurationController.notifyConfigurationChanged()
            advanceUntilIdle()

            val cs = ConstraintSet()
            underTest.applyDefaultConstraints(cs)

            cs.assertLargeClock(
                topMargin = KEYGUARD_SMARTSPACE_TOP_OFFSET + ENHANCED_SMART_SPACE_HEIGHT
            )
            cs.assertSmallClock()
        }

    @Test
    @DisableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun testApplyDefaultConstraints_SmallClock_SplitShade() =
        kosmos.runTest {
            val legacyUseSplitShade by collectLastValue(shadeRepository.legacyUseSplitShade)
            val isLargeClockVisible by collectLastValue(keyguardClockViewModel.isLargeClockVisible)

            enableSplitShade()
            keyguardClockInteractor.setClockSize(ClockSize.SMALL)
            notificationsKeyguardInteractor.setNotificationsFullyHidden(true)
            keyguardSmartspaceInteractor.setBcSmartspaceVisibility(VISIBLE)
            fakeConfigurationController.notifyConfigurationChanged()
            advanceUntilIdle()

            val cs = ConstraintSet()
            underTest.applyDefaultConstraints(cs)

            cs.assertLargeClock(topMargin = LARGE_CLOCK_TOP)
            cs.assertSmallClock()
        }

    @Test
    @EnableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun testApplyDefaultConstraints_SmallClock_SplitShade_ReactiveVariantsOn() =
        kosmos.runTest {
            val legacyUseSplitShade by collectLastValue(shadeRepository.legacyUseSplitShade)
            val isLargeClockVisible by collectLastValue(keyguardClockViewModel.isLargeClockVisible)

            enableSplitShade()
            keyguardClockInteractor.setClockSize(ClockSize.SMALL)
            notificationsKeyguardInteractor.setNotificationsFullyHidden(true)
            keyguardSmartspaceInteractor.setBcSmartspaceVisibility(VISIBLE)
            fakeConfigurationController.notifyConfigurationChanged()
            advanceUntilIdle()

            val cs = ConstraintSet()
            underTest.applyDefaultConstraints(cs)

            cs.assertLargeClock(
                topMargin = KEYGUARD_SMARTSPACE_TOP_OFFSET + ENHANCED_SMART_SPACE_HEIGHT
            )
            cs.assertSmallClock()
        }

    @Test
    @DisableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun testApplyDefaultConstraints_SmallClock_SingleShade() =
        kosmos.runTest {
            val legacyUseSplitShade by collectLastValue(shadeRepository.legacyUseSplitShade)
            val isLargeClockVisible by collectLastValue(keyguardClockViewModel.isLargeClockVisible)

            enableSingleShade()
            keyguardClockInteractor.setClockSize(ClockSize.SMALL)
            notificationsKeyguardInteractor.setNotificationsFullyHidden(true)
            keyguardSmartspaceInteractor.setBcSmartspaceVisibility(VISIBLE)
            fakeConfigurationController.notifyConfigurationChanged()
            advanceUntilIdle()

            val cs = ConstraintSet()
            underTest.applyDefaultConstraints(cs)

            cs.assertLargeClock(topMargin = LARGE_CLOCK_TOP)
            cs.assertSmallClock()
        }

    @Test
    @EnableFlags(com.android.systemui.shared.Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT)
    fun testApplyDefaultConstraints_SmallClock_SingleShade_ReactiveVariantsOn() =
        kosmos.runTest {
            val legacyUseSplitShade by collectLastValue(shadeRepository.legacyUseSplitShade)
            val isLargeClockVisible by collectLastValue(keyguardClockViewModel.isLargeClockVisible)

            enableSingleShade()
            keyguardClockInteractor.setClockSize(ClockSize.SMALL)
            notificationsKeyguardInteractor.setNotificationsFullyHidden(true)
            keyguardSmartspaceInteractor.setBcSmartspaceVisibility(VISIBLE)
            fakeConfigurationController.notifyConfigurationChanged()
            advanceUntilIdle()

            val cs = ConstraintSet()
            underTest.applyDefaultConstraints(cs)

            cs.assertLargeClock(
                topMargin = KEYGUARD_SMARTSPACE_TOP_OFFSET + ENHANCED_SMART_SPACE_HEIGHT
            )
            cs.assertSmallClock()
        }

    @Test
    fun testSmartspaceVisible_weatherClockDateAndIconsBarrierBottomBelowBCSmartspace() =
        kosmos.runTest {
            notificationsKeyguardInteractor.setNotificationsFullyHidden(false)
            keyguardSmartspaceInteractor.setBcSmartspaceVisibility(VISIBLE)
            fakeConfigurationController.notifyConfigurationChanged()
            advanceUntilIdle()

            val cs = ConstraintSet()
            underTest.applyDefaultConstraints(cs)
            val referencedIds = cs.getReferencedIds(ClockViewIds.WEATHER_CLOCK_DATE_BARRIER_BOTTOM)
            referencedIds.contentEquals(
                intArrayOf(com.android.systemui.shared.R.id.bc_smartspace_view)
            )
        }

    @Test
    fun testSmartspaceGone_weatherClockDateAndIconsBarrierBottomBelowSmartspaceDateWeather() =
        kosmos.runTest {
            notificationsKeyguardInteractor.setNotificationsFullyHidden(false)
            keyguardSmartspaceInteractor.setBcSmartspaceVisibility(GONE)
            fakeConfigurationController.notifyConfigurationChanged()
            advanceUntilIdle()

            val cs = ConstraintSet()
            underTest.applyDefaultConstraints(cs)
            val referencedIds = cs.getReferencedIds(ClockViewIds.WEATHER_CLOCK_DATE_BARRIER_BOTTOM)
            referencedIds.contentEquals(intArrayOf(ClockViewIds.LOCKSCREEN_CLOCK_VIEW_SMALL))
        }

    @Test
    fun testHasAodIcons_weatherClockDateAndIconsBarrierBottomBelowSmartspaceDateWeather() =
        kosmos.runTest {
            notificationsKeyguardInteractor.setNotificationsFullyHidden(true)
            fakeConfigurationController.notifyConfigurationChanged()
            advanceUntilIdle()

            val cs = ConstraintSet()
            underTest.applyDefaultConstraints(cs)
            val referencedIds = cs.getReferencedIds(ClockViewIds.WEATHER_CLOCK_DATE_BARRIER_BOTTOM)
            referencedIds.contentEquals(
                intArrayOf(
                    com.android.systemui.shared.R.id.bc_smartspace_view,
                    R.id.aod_notification_icon_container,
                )
            )
        }

    private fun ConstraintSet.assertLargeClock(
        targetId: Int = ConstraintSet.PARENT_ID,
        topMargin: Int = 0,
    ) {
        val largeClockConstraint = getConstraint(ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE)
        assertThat(largeClockConstraint.layout.topToTop).isEqualTo(targetId)
        assertThat(largeClockConstraint.layout.topMargin).isEqualTo(topMargin)
    }

    private fun ConstraintSet.assertSmallClock(
        targetId: Int = R.id.small_clock_guideline_top,
        topMargin: Int = 0,
    ) {
        val smallClockGuidelineConstraint = getConstraint(targetId)
        assertThat(smallClockGuidelineConstraint.layout.topToTop).isEqualTo(-1)

        val smallClockConstraint = getConstraint(ClockViewIds.LOCKSCREEN_CLOCK_VIEW_SMALL)
        assertThat(smallClockConstraint.layout.topToBottom).isEqualTo(targetId)
        assertThat(smallClockConstraint.layout.topMargin).isEqualTo(topMargin)
    }
}
