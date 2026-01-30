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

package com.android.systemui.statusbar.ui.viewmodel

import android.graphics.Rect
import android.platform.test.annotations.EnableFlags
import android.view.View
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.view.AppearanceRegion
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.statusbar.StatusBarRegionSampling
import com.android.systemui.statusbar.domain.interactor.fakeStatusBarRegionSamplingInteractor
import com.android.systemui.statusbar.ui.viewmodel.StatusBarRegionSamplingViewModel.RegionSamplingHelperFactory.Purpose
import com.android.systemui.testKosmos
import com.android.wm.shell.shared.handles.RegionSamplingHelper
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.Job
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(StatusBarRegionSampling.FLAG_NAME)
class StatusBarRegionSamplingViewModelTest : SysuiTestCase() {
    private val activationJob = Job()

    private val startContainerBounds = Rect(0, 20, 500, 100)
    private val startIconBounds = Rect(100, 30, 200, 60)
    private val endContainerBounds = Rect(500, 20, 1000, 100)
    private val endIconBounds = Rect(600, 30, 700, 60)
    // Sampling bounds of each side should be the same as containerBounds, but with the
    // top equal to the corresponding side's iconBounds.bottom.
    private val expectedStartSideSamplingBounds = Rect(0, 60, 500, 100)
    private val expectedEndSideSamplingBounds = Rect(500, 60, 1000, 100)
    // AppearanceRegion bounds of each side should be the same as containerBounds, but with
    // the top equal to 0.
    private val expectedStartSideAppearanceRegionBounds = Rect(0, 0, 500, 100)
    private val expectedEndSideAppearanceRegionBounds = Rect(500, 0, 1000, 100)

    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply {
            mockStatusBarStartSideContainerView = mockViewWithBounds(startContainerBounds)
            mockStatusBarStartSideIconView = mockViewWithBounds(startIconBounds)
            mockStatusBarEndSideContainerView = mockViewWithBounds(endContainerBounds)
            mockStatusBarEndSideIconView = mockViewWithBounds(endIconBounds)
        }

    private fun mockViewWithBounds(bounds: Rect): View {
        return mock<View> {
            on { getBoundsOnScreen(any()) } doAnswer
                {
                    val boundsOutput = it.arguments[0] as Rect
                    boundsOutput.set(bounds)
                    return@doAnswer
                }
        }
    }

    private fun View.triggerOnLayoutChange() {
        val captor = argumentCaptor<View.OnLayoutChangeListener>()
        verify(this, atLeastOnce()).addOnLayoutChangeListener(captor.capture())
        captor.allValues.forEach { it.onLayoutChange(this, 0, 0, 0, 0, 0, 0, 0, 0) }
    }

    private val Kosmos.underTest by Kosmos.Fixture { kosmos.statusBarRegionSamplingViewModel }

    @Before
    fun setUp() {
        kosmos.underTest.activateIn(kosmos.testScope, activationJob)
    }

    @Test
    fun regionSamplingDisabled_doesNotRegisterRegionSamplingHelpers() =
        kosmos.runTest {
            fakeStatusBarRegionSamplingInteractor.setRegionSamplingEnabled(false)

            verify(mockRegionSamplingHelperFactory, never())
                .create(any(), any(), any(), any(), any())
        }

    @Test
    fun regionSamplingEnabled_afterActivationCancelled_doesNotRegisterRegionSamplingHelpers() =
        kosmos.runTest {
            activationJob.cancel()
            fakeStatusBarRegionSamplingInteractor.setRegionSamplingEnabled(true)

            verify(mockRegionSamplingHelperFactory, never())
                .create(any(), any(), any(), any(), any())
        }

    @Test
    fun regionSamplingBecomesDisabled_stopsRegionSamplingHelpers_setsEmptySampledRegions() =
        kosmos.runTest {
            val mockStartRegionSamplingHelper = mock<RegionSamplingHelper>()
            val mockEndRegionSamplingHelper = mock<RegionSamplingHelper>()
            whenever(
                    mockRegionSamplingHelperFactory.create(
                        any(),
                        any(),
                        any(),
                        any(),
                        eq(Purpose.START_SIDE),
                    )
                )
                .thenReturn(mockStartRegionSamplingHelper)
            whenever(
                    mockRegionSamplingHelperFactory.create(
                        any(),
                        any(),
                        any(),
                        any(),
                        eq(Purpose.END_SIDE),
                    )
                )
                .thenReturn(mockEndRegionSamplingHelper)
            fakeStatusBarRegionSamplingInteractor.setRegionSamplingEnabled(true)

            fakeStatusBarRegionSamplingInteractor.setRegionSamplingEnabled(false)

            verify(mockStartRegionSamplingHelper).stop()
            verify(mockEndRegionSamplingHelper).stop()
            assertThat(fakeStatusBarRegionSamplingInteractor.sampledAppearanceRegions).isEmpty()
        }

    @Test
    fun sampledRegion_usesSamplingBoundsFromContainerAndIconBounds() =
        kosmos.runTest {
            fakeStatusBarRegionSamplingInteractor.setRegionSamplingEnabled(true)
            mockStatusBarStartSideContainerView.triggerOnLayoutChange()
            mockStatusBarEndSideContainerView.triggerOnLayoutChange()

            val startSideSamplingBounds =
                getRegionSamplingHelperCallback(Purpose.START_SIDE)
                    .getSampledRegion(mockStatusBarAttachStateView)
            val endSideSamplingBounds =
                getRegionSamplingHelperCallback(Purpose.END_SIDE)
                    .getSampledRegion(mockStatusBarAttachStateView)

            assertThat(startSideSamplingBounds).isEqualTo(expectedStartSideSamplingBounds)
            assertThat(endSideSamplingBounds).isEqualTo(expectedEndSideSamplingBounds)
        }

    @Test
    fun onRegionDarknessChanged_isRegionDarkTrue_setAppearanceDarkStatusBars() =
        kosmos.runTest {
            fakeStatusBarRegionSamplingInteractor.setRegionSamplingEnabled(true)
            mockStatusBarStartSideContainerView.triggerOnLayoutChange()
            mockStatusBarEndSideContainerView.triggerOnLayoutChange()

            getRegionSamplingHelperCallback(Purpose.START_SIDE).onRegionDarknessChanged(true)
            getRegionSamplingHelperCallback(Purpose.END_SIDE).onRegionDarknessChanged(true)

            val startSideAppearanceRegion = getSampledAppearanceRegion(Purpose.START_SIDE)
            assertThat(startSideAppearanceRegion.appearance and APPEARANCE_LIGHT_STATUS_BARS)
                .isEqualTo(0)
            val endSideAppearanceRegion = getSampledAppearanceRegion(Purpose.END_SIDE)
            assertThat(endSideAppearanceRegion.appearance and APPEARANCE_LIGHT_STATUS_BARS)
                .isEqualTo(0)
        }

    @Test
    fun onRegionDarknessChanged_isRegionDarkFalse_setsAppearanceLightStatusBars() =
        kosmos.runTest {
            fakeStatusBarRegionSamplingInteractor.setRegionSamplingEnabled(true)
            mockStatusBarStartSideContainerView.triggerOnLayoutChange()
            mockStatusBarEndSideContainerView.triggerOnLayoutChange()

            getRegionSamplingHelperCallback(Purpose.START_SIDE).onRegionDarknessChanged(false)
            getRegionSamplingHelperCallback(Purpose.END_SIDE).onRegionDarknessChanged(false)

            val startSideAppearanceRegion = getSampledAppearanceRegion(Purpose.START_SIDE)
            assertThat(startSideAppearanceRegion.appearance and APPEARANCE_LIGHT_STATUS_BARS)
                .isEqualTo(APPEARANCE_LIGHT_STATUS_BARS)
            val endSideAppearanceRegion = getSampledAppearanceRegion(Purpose.END_SIDE)
            assertThat(endSideAppearanceRegion.appearance and APPEARANCE_LIGHT_STATUS_BARS)
                .isEqualTo(APPEARANCE_LIGHT_STATUS_BARS)
        }

    @Test
    fun onRegionDarknessChanged_isRegionDarkMixed_setsAppearanceMixedStatusBars() =
        kosmos.runTest {
            fakeStatusBarRegionSamplingInteractor.setRegionSamplingEnabled(true)
            mockStatusBarStartSideContainerView.triggerOnLayoutChange()
            mockStatusBarEndSideContainerView.triggerOnLayoutChange()

            getRegionSamplingHelperCallback(Purpose.START_SIDE).onRegionDarknessChanged(false)
            getRegionSamplingHelperCallback(Purpose.END_SIDE).onRegionDarknessChanged(true)

            val startSideAppearanceRegion = getSampledAppearanceRegion(Purpose.START_SIDE)
            assertThat(startSideAppearanceRegion.appearance and APPEARANCE_LIGHT_STATUS_BARS)
                .isEqualTo(APPEARANCE_LIGHT_STATUS_BARS)
            val endSideAppearanceRegion = getSampledAppearanceRegion(Purpose.END_SIDE)
            assertThat(endSideAppearanceRegion.appearance and APPEARANCE_LIGHT_STATUS_BARS)
                .isEqualTo(0)
        }

    private fun Kosmos.getRegionSamplingHelperCallback(
        purpose: Purpose
    ): RegionSamplingHelper.SamplingCallback {
        val captor = argumentCaptor<RegionSamplingHelper.SamplingCallback>()
        verify(mockRegionSamplingHelperFactory)
            .create(any(), captor.capture(), any(), any(), eq(purpose))
        return captor.lastValue
    }

    private fun Kosmos.getSampledAppearanceRegion(purpose: Purpose): AppearanceRegion {
        val appearanceRegions = fakeStatusBarRegionSamplingInteractor.sampledAppearanceRegions!!
        val expectedBounds =
            when (purpose) {
                Purpose.START_SIDE -> expectedStartSideAppearanceRegionBounds
                Purpose.END_SIDE -> expectedEndSideAppearanceRegionBounds
            }
        val result = appearanceRegions.filter { it!!.bounds == expectedBounds }
        assertWithMessage("Unable to find region with bounds $expectedBounds in $appearanceRegions")
            .that(result)
            .hasSize(1)
        return result[0]!!
    }
}
