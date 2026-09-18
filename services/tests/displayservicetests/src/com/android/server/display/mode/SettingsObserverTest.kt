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
 */

package com.android.server.display.mode

import android.content.Context
import android.content.ContextWrapper
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.provider.Settings
import android.testing.TestableContext
import android.util.SparseArray
import android.util.SparseBooleanArray
import android.view.Display
import android.view.SurfaceControl.RefreshRateRange
import android.view.SurfaceControl.RefreshRateRanges
import android.view.SurfaceControl.WorkDuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.internal.util.test.FakeSettingsProvider
import com.android.server.display.DisplayDeviceConfig
import com.android.server.display.ModeRequestManager
import com.android.server.display.config.RefreshRateData
import com.android.server.display.config.SupportedModeData
import com.android.server.display.config.createRefreshRateData
import com.android.server.display.feature.DisplayManagerFlags
import com.android.server.display.feature.flags.Flags
import com.android.server.display.mode.DisplayModeDirector.DisplayDeviceConfigProvider
import com.android.server.display.mode.SupportedRefreshRatesVote.RefreshRates
import com.android.server.testutils.TestHandler
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

private val RANGE_NO_LIMIT = RefreshRateRange(0f, Float.POSITIVE_INFINITY)
private val RANGE_0_60 = RefreshRateRange(0f, 60f)
private val RANGE_0_90 = RefreshRateRange(0f, 90f)
private val RANGE_0_120 = RefreshRateRange(0f, 120f)
private val RANGE_60_90 = RefreshRateRange(60f, 90f)
private val RANGE_60_120 = RefreshRateRange(60f, 120f)
private val RANGE_60_INF = RefreshRateRange(60f, Float.POSITIVE_INFINITY)
private val RANGE_90_90 = RefreshRateRange(90f, 90f)
private val RANGE_90_120 = RefreshRateRange(90f, 120f)
private val RANGE_90_INF = RefreshRateRange(90f, Float.POSITIVE_INFINITY)

private val RANGES_NO_LIMIT = RefreshRateRanges(RANGE_NO_LIMIT, RANGE_NO_LIMIT)
private val RANGES_NO_LIMIT_60 = RefreshRateRanges(RANGE_NO_LIMIT, RANGE_0_60)
private val RANGES_NO_LIMIT_90 = RefreshRateRanges(RANGE_NO_LIMIT, RANGE_0_90)
private val RANGES_NO_LIMIT_120 = RefreshRateRanges(RANGE_NO_LIMIT, RANGE_0_120)
private val RANGES_90 = RefreshRateRanges(RANGE_0_90, RANGE_0_90)
private val RANGES_120 = RefreshRateRanges(RANGE_0_120, RANGE_0_120)
private val RANGES_90_60 = RefreshRateRanges(RANGE_0_90, RANGE_0_60)
private val RANGES_90TO90 = RefreshRateRanges(RANGE_90_90, RANGE_90_90)
private val RANGES_90TO120 = RefreshRateRanges(RANGE_90_120, RANGE_90_120)
private val RANGES_60TO120_60TO90 = RefreshRateRanges(RANGE_60_120, RANGE_60_90)
private val RANGES_MIN90 = RefreshRateRanges(RANGE_90_INF, RANGE_90_INF)
private val RANGES_MIN90_90TO120 = RefreshRateRanges(RANGE_90_INF, RANGE_90_120)
private val RANGES_MIN60_60TO90 = RefreshRateRanges(RANGE_60_INF, RANGE_60_90)
private val RANGES_MIN90_90TO90 = RefreshRateRanges(RANGE_90_INF, RANGE_90_90)

private val LOW_POWER_GLOBAL_VOTE = Vote.forRenderFrameRates(0f, 60f)
private val LOW_POWER_REFRESH_RATE_DATA = createRefreshRateData(
    lowPowerSupportedModes = listOf(SupportedModeData(60f, 60f), SupportedModeData(60f, 240f)),
    lowPowerWorkDurations = WorkDuration(10, 10, 10)
    )
private val LOW_POWER_EMPTY_REFRESH_RATE_DATA = createRefreshRateData()
private val EXPECTED_SUPPORTED_MODES_VOTE = SupportedRefreshRatesVote(
    listOf(RefreshRates(60f, 60f), RefreshRates(60f, 240f)))
private val EXPECTED_WORK_DURATIONS_VOTE = WorkDurationsVote(WorkDuration(10, 10, 10))

@SmallTest
@RunWith(TestParameterInjector::class)
class SettingsObserverTest {
    @get:Rule
    val mockitoRule = MockitoJUnit.rule()

    @get:Rule
    val settingsProviderRule = FakeSettingsProvider.rule()

    @get:Rule
    val setFlagRule = SetFlagsRule()

    @get:Rule
    val mContext = TestableContext(
        InstrumentationRegistry.getInstrumentation().targetContext)

    private lateinit var spyContext: Context
    private val mockInjector = mock<DisplayModeDirector.Injector>()
    private val mockFlags = mock<DisplayManagerFlags>()
    private val mockDeviceConfig = mock<DisplayDeviceConfig>()
    private val mockDisplayDeviceConfigProvider = mock<DisplayDeviceConfigProvider>()

    private val testHandler = TestHandler(null)
    private val mockModeRequestManager = mock<ModeRequestManager>()

    @Before
    fun setUp() {
        spyContext = Mockito.spy(ContextWrapper(ApplicationProvider.getApplicationContext()))
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_WORK_DURATIONS)
    fun testLowPowerMode(@TestParameter testCase: LowPowerTestCase) {
        whenever(spyContext.contentResolver)
                .thenReturn(settingsProviderRule.mockContentResolver(null))
        val lowPowerModeSetting = if (testCase.lowPowerModeEnabled) 1 else 0
        Settings.Global.putInt(
                spyContext.contentResolver, Settings.Global.LOW_POWER_MODE, lowPowerModeSetting)

        val displayModeDirector = DisplayModeDirector(
            spyContext, testHandler, mockInjector, mockFlags,
            mockDisplayDeviceConfigProvider, mockModeRequestManager)
        val ddcByDisplay = SparseArray<DisplayDeviceConfig>()
        whenever(mockDeviceConfig.refreshRateData).thenReturn(testCase.refreshRateData)
        ddcByDisplay.put(Display.DEFAULT_DISPLAY, mockDeviceConfig)
        displayModeDirector.injectDisplayDeviceConfigByDisplay(ddcByDisplay)
        val settingsObserver = displayModeDirector.SettingsObserver(
                spyContext, testHandler, mockFlags)

        settingsObserver.onChange(
                false, Settings.Global.getUriFor(Settings.Global.LOW_POWER_MODE), 1)

        assertThat(displayModeDirector.getVote(VotesStorage.GLOBAL_ID,
                Vote.PRIORITY_LOW_POWER_MODE_RENDER_RATE)).isEqualTo(testCase.globalVote)
        assertThat(displayModeDirector.getVote(Display.DEFAULT_DISPLAY,
            Vote.PRIORITY_LOW_POWER_MODE_MODES)).isEqualTo(testCase.displayVote)
    }

    enum class LowPowerTestCase(
        val refreshRateData: RefreshRateData,
        val lowPowerModeEnabled: Boolean,
        internal val globalVote: Vote?,
        internal val displayVote: Vote?
    ) {
        ALL_ENABLED(LOW_POWER_REFRESH_RATE_DATA, true,
            LOW_POWER_GLOBAL_VOTE, EXPECTED_SUPPORTED_MODES_VOTE),
        LOW_POWER_OFF(LOW_POWER_REFRESH_RATE_DATA, false,
            null, null),
        EMPTY_REFRESH_LOW_POWER_ON(LOW_POWER_EMPTY_REFRESH_RATE_DATA, true,
            LOW_POWER_GLOBAL_VOTE, null),
        EMPTY_REFRESH__LOW_POWER_OFF(LOW_POWER_EMPTY_REFRESH_RATE_DATA, false,
            null, null),
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_WORK_DURATIONS)
    fun testSettingsRefreshRates(@TestParameter testCase: SettingsRefreshRateTestCase) {
        val displayModeDirector = DisplayModeDirector(
            spyContext, testHandler, mockInjector, mockFlags,
            mockDisplayDeviceConfigProvider, mockModeRequestManager)

        val modes = arrayOf(
            Display.Mode(1, 1000, 1000, 60f),
            Display.Mode(2, 1000, 1000, 90f),
            Display.Mode(3, 1000, 1000, 120f)
        )
        displayModeDirector.injectSupportedModesByDisplay(SparseArray<Array<Display.Mode>>().apply {
            append(Display.DEFAULT_DISPLAY, modes)
        })
        displayModeDirector.injectDefaultModeByDisplay(SparseArray<Display.Mode>().apply {
            append(Display.DEFAULT_DISPLAY, modes[0])
        })

        val specs = displayModeDirector.getDesiredDisplayModeSpecsWithInjectedFpsSettings(
            testCase.minRefreshRate, testCase.peakRefreshRate, testCase.defaultRefreshRate)

        assertWithMessage("Primary RefreshRateRanges: ")
                .that(specs.primary).isEqualTo(testCase.expectedPrimaryRefreshRateRanges)
        assertWithMessage("App RefreshRateRanges: ")
                .that(specs.appRequest).isEqualTo(testCase.expectedAppRefreshRateRanges)
    }

    /**
     * Votes considered:
     * priority: PRIORITY_USER_SETTING_PEAK_REFRESH_RATE (also used for appRanged)
     * condition: peakRefreshRatePhysicalLimitEnabled, peakRR > 0
     * vote: physical(minRR, peakRR)
     *
     * priority: PRIORITY_USER_SETTING_PEAK_RENDER_FRAME_RATE (also used for appRanged)
     * condition: peakRR > 0
     * vote: render(minRR, peakRR)
     *
     * priority: PRIORITY_USER_SETTING_MIN_RENDER_FRAME_RATE
     * condition: -
     * vote: render(minRR, INF)
     *
     * priority: PRIORITY_DEFAULT_RENDER_FRAME_RATE
     * condition: defaultRR > 0
     * vote: render(0, defaultRR)
     *
     * 0 considered not set
     *
     * For this test:
     * primary physical rate:
     *          (minRR, peakRefreshRatePhysicalLimitEnabled ? max(minRR, peakRR) : INF)
     * primary render rate : (minRR, min(defaultRR, max(minRR, peakRR)))
     *
     * app physical rate: (0, peakRefreshRatePhysicalLimitEnabled ? max(minRR, peakRR) : INF)
     * app render rate: (0, max(minRR, peakRR))
     */
    enum class SettingsRefreshRateTestCase(
        val minRefreshRate: Float,
        val peakRefreshRate: Float,
        val defaultRefreshRate: Float,
        val expectedPrimaryRefreshRateRanges: RefreshRateRanges,
        val expectedAppRefreshRateRanges: RefreshRateRanges,
    ) {
        NO_LIMIT_WITH_PHYSICAL_RR(0f, 0f, 0f, RANGES_NO_LIMIT, RANGES_NO_LIMIT),
        LIMITS_0_0_90_WITH_PHYSICAL_RR(0f, 0f, 90f, RANGES_NO_LIMIT_90, RANGES_NO_LIMIT),
        LIMITS_0_90_0_WITH_PHYSICAL_RR(0f, 90f, 0f, RANGES_90, RANGES_90),
        LIMITS_0_90_60_WITH_PHYSICAL_RR(0f, 90f, 60f, RANGES_90_60, RANGES_90),
        LIMITS_0_90_120_WITH_PHYSICAL_RR(0f, 90f, 120f, RANGES_90, RANGES_90),
        LIMITS_90_0_0_WITH_PHYSICAL_RR(90f, 0f, 0f, RANGES_MIN90, RANGES_NO_LIMIT),
        LIMITS_90_0_120_WITH_PHYSICAL_RR(90f, 0f, 120f, RANGES_MIN90_90TO120, RANGES_NO_LIMIT),
        LIMITS_90_0_60_WITH_PHYSICAL_RR(90f, 0f, 60f, RANGES_MIN90, RANGES_NO_LIMIT),
        LIMITS_90_120_0_WITH_PHYSICAL_RR(90f, 120f, 0f, RANGES_90TO120, RANGES_120),
        LIMITS_90_60_0_WITH_PHYSICAL_RR(90f, 60f, 0f, RANGES_90TO90, RANGES_90),
        LIMITS_60_120_90_WITH_PHYSICAL_RR(60f, 120f, 90f, RANGES_60TO120_60TO90, RANGES_120),
    }

    @Test
    fun testSettingsRefreshRates_peakRefreshRateIgnoredForArr() {
        whenever(mockFlags.hasArrSupportFlag()).thenReturn(true)
        val displayModeDirector = DisplayModeDirector(
            spyContext, testHandler, mockInjector, mockFlags,
            mockDisplayDeviceConfigProvider, mockModeRequestManager)
        displayModeDirector.injectHasArrSupport(SparseBooleanArray().apply {
            append(Display.DEFAULT_DISPLAY, true)
        })

        val modes = arrayOf(
            Display.Mode(1, 1000, 1000, 60f),
            Display.Mode(2, 1000, 1000, 90f),
            Display.Mode(3, 1000, 1000, 120f)
        )
        displayModeDirector.injectSupportedModesByDisplay(SparseArray<Array<Display.Mode>>().apply {
            append(Display.DEFAULT_DISPLAY, modes)
        })
        displayModeDirector.injectDefaultModeByDisplay(SparseArray<Display.Mode>().apply {
            append(Display.DEFAULT_DISPLAY, modes[0])
        })

        val specs = displayModeDirector.getDesiredDisplayModeSpecsWithInjectedFpsSettings(
            0f, 90f, 120f)

        // Peak refresh rate is not added to physical limit, however it is added to render limit
        assertWithMessage("Primary RefreshRateRanges: ")
            .that(specs.primary).isEqualTo(RANGES_NO_LIMIT_90)
        assertWithMessage("App RefreshRateRanges: ")
            .that(specs.appRequest).isEqualTo(RANGES_NO_LIMIT_90)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_WORK_DURATIONS)
    fun testLowPowerMode_workDurationsInVotes() {
        val configDefaultPeakRefreshRateId = 0x10e0075
        mContext.orCreateTestableResources.addOverride(configDefaultPeakRefreshRateId, 60)

        Settings.Global.putInt(
            mContext.contentResolver, Settings.Global.LOW_POWER_MODE, 1)

        val displayModeDirector = DisplayModeDirector(
            mContext, testHandler, mockInjector, mockFlags, mockDisplayDeviceConfigProvider,
            mockModeRequestManager)
        val ddcByDisplay = SparseArray<DisplayDeviceConfig>()
        whenever(mockDeviceConfig.refreshRateData).thenReturn(LOW_POWER_REFRESH_RATE_DATA)
        ddcByDisplay.put(0, mockDeviceConfig)
        displayModeDirector.injectDisplayDeviceConfigByDisplay(ddcByDisplay)
        val settingsObserver = displayModeDirector.SettingsObserver(
            mContext, testHandler, mockFlags)

        settingsObserver.onChange(
            false, Settings.Global.getUriFor(Settings.Global.LOW_POWER_MODE), 1)

        assertThat(displayModeDirector.getVote(0, Vote.PRIORITY_LOW_POWER_MODE_MODES))
            .isEqualTo(CombinedVote(
                listOf(
                    EXPECTED_SUPPORTED_MODES_VOTE,
                    EXPECTED_WORK_DURATIONS_VOTE
                )
            ))
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_WORK_DURATIONS)
    fun testLowPowerMode_workDurationsDisabled() {
        val configDefaultPeakRefreshRateId = 0x10e0075
        mContext.orCreateTestableResources.addOverride(configDefaultPeakRefreshRateId, 60)

        Settings.Global.putInt(
            mContext.contentResolver, Settings.Global.LOW_POWER_MODE, 1)

        val displayModeDirector = DisplayModeDirector(
            mContext, testHandler, mockInjector, mockFlags,
            mockDisplayDeviceConfigProvider, mockModeRequestManager)
        val ddcByDisplay = SparseArray<DisplayDeviceConfig>()
        whenever(mockDeviceConfig.refreshRateData).thenReturn(LOW_POWER_REFRESH_RATE_DATA)
        ddcByDisplay.put(0, mockDeviceConfig)
        displayModeDirector.injectDisplayDeviceConfigByDisplay(ddcByDisplay)
        val settingsObserver = displayModeDirector.SettingsObserver(
            mContext, testHandler, mockFlags)

        settingsObserver.onChange(
            false, Settings.Global.getUriFor(Settings.Global.LOW_POWER_MODE), 1)

        // no combined vote with work durations
        assertThat(displayModeDirector.getVote(0, Vote.PRIORITY_LOW_POWER_MODE_MODES))
            .isEqualTo(EXPECTED_SUPPORTED_MODES_VOTE)
    }
}