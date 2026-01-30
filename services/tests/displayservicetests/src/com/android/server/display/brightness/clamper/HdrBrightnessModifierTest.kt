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

package com.android.server.display.brightness.clamper

import android.content.Context
import android.database.ContentObserver
import android.hardware.display.DisplayManagerInternal
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager.BRIGHTNESS_MAX
import android.provider.Settings
import android.util.Spline
import android.view.SurfaceControlHdrLayerInfoListener
import androidx.test.filters.SmallTest
import com.android.internal.display.BrightnessUtils
import com.android.server.display.DisplayBrightnessState
import com.android.server.display.DisplayBrightnessState.BRIGHTNESS_NOT_SET
import com.android.server.display.DisplayBrightnessState.CUSTOM_ANIMATION_RATE_NOT_SET
import com.android.server.display.DisplayDeviceConfig
import com.android.server.display.brightness.clamper.BrightnessClamperController.ClamperChangeListener
import com.android.server.display.brightness.clamper.BrightnessClamperController.ModifiersAggregatedState
import com.android.server.display.brightness.clamper.HdrBrightnessModifier.DEFAULT_MAX_HDR_SDR_RATIO
import com.android.server.display.brightness.clamper.HdrBrightnessModifier.Injector
import com.android.server.display.config.HdrBrightnessData
import com.android.server.display.config.createHdrBrightnessData
import com.android.server.display.feature.DisplayManagerFlags
import com.android.server.display.plugin.PluginManager
import com.android.server.display.plugin.types.HdrBoostOverride
import com.android.server.testutils.OffsettableClock
import com.android.server.testutils.TestHandler
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

private const val SEND_TIME_TOLERANCE: Long = 100

@SmallTest
class HdrBrightnessModifierTest {

    private val stoppedClock = OffsettableClock.Stopped()
    private val testHandler = TestHandler(null, stoppedClock)
    private val testInjector = TestInjector()
    private val mockChangeListener = mock<ClamperChangeListener>()
    private val mockDisplayDeviceConfig = mock<DisplayDeviceConfig>()
    private val mockDisplayBinder = mock<IBinder>()
    private val mockDisplayBinderOther = mock<IBinder>()
    private val mockSpline = mock<Spline>()
    private val mockRequest = mock<DisplayManagerInternal.DisplayPowerRequest>()
    private val mockFlags = mock<DisplayManagerFlags>()

    private lateinit var modifier: HdrBrightnessModifier
    private val dummyData = createDisplayDeviceData(mockDisplayDeviceConfig, mockDisplayBinder)

    @Test
    fun changeListenerIsNotCalledOnInit() {
        initHdrModifier()

        verify(mockChangeListener, never()).onChanged()
    }

    @Test
    fun hdrListenerRegisteredOnInit_hdrDataPresent() {
        initHdrModifier()

        assertThat(testInjector.registeredHdrListener).isNotNull()
        assertThat(testInjector.registeredToken).isEqualTo(mockDisplayBinder)
    }

    @Test
    fun hdrListenerNotRegisteredOnInit_hdrDataMissing() {
        initHdrModifier(hdrBrightnessData = null)

        assertThat(testInjector.registeredHdrListener).isNull()
        assertThat(testInjector.registeredToken).isNull()
    }

    @Test
    fun unsubscribeHdrListener_displayChangedWithNoHdrData() {
        initHdrModifier()

        whenever(mockDisplayDeviceConfig.hdrBrightnessData).thenReturn(null)
        modifier.onDisplayChanged(dummyData)

        assertThat(testInjector.registeredHdrListener).isNull()
        assertThat(testInjector.registeredToken).isNull()
        verify(mockChangeListener, never()).onChanged()
    }

    @Test
    fun resubscribesHdrListener_displayChangedWithDifferentToken() {
        initHdrModifier()

        modifier.onDisplayChanged(
            createDisplayDeviceData(mockDisplayDeviceConfig, mockDisplayBinderOther))

        assertThat(testInjector.registeredHdrListener).isNotNull()
        assertThat(testInjector.registeredToken).isEqualTo(mockDisplayBinderOther)
        verify(mockChangeListener, never()).onChanged()
    }

    @Test
    fun contentObserversNotRegisteredOnInit_hdrDataMissing() {
        initHdrModifier(null)

        assertThat(testInjector.registeredLowPowerModeSettingObserver).isNull()
        assertThat(testInjector.registeredHdrSettingsObserver).isNull()
    }

    @Test
    fun lowPowerSettingObserverNotRegisteredOnInit_allowedInLowPowerMode() {
        initHdrModifier(createHdrBrightnessData(allowInLowPowerMode = true))

        assertThat(testInjector.registeredLowPowerModeSettingObserver).isNull()
    }

    @Test
    fun contentObserversRegisteredOnInit_flagDisabled() {
        initHdrModifier(createHdrBrightnessData(allowInLowPowerMode = false))

        assertThat(testInjector.registeredLowPowerModeSettingObserver).isNotNull()
        assertThat(testInjector.registeredHdrSettingsObserver).isNull()
    }

    @Test
    fun contentObserversRegisteredOnInit_flagEnabled() {
        whenever(mockFlags.isHdrBrightnessSettingEnabled()).thenReturn(true)
        initHdrModifier(createHdrBrightnessData(allowInLowPowerMode = false))

        assertThat(testInjector.registeredLowPowerModeSettingObserver).isNotNull()
        assertThat(testInjector.registeredHdrSettingsObserver).isNotNull()
    }

    @Test
    fun testNoHdrMode() {
        initHdrModifier()
        // screen size = 10_000
        setupDisplay(width = 100, height = 100, hdrBrightnessData = createHdrBrightnessData(
            minimumHdrPercentOfScreenForNbm = 0.5f,
            minimumHdrPercentOfScreenForHbm = 0.7f,
            sdrToHdrRatioSpline = mockSpline
        ))

        // hdr size = 900
        setupHdrLayer(width = 30, height = 30)

        assertModifierState()
    }

    @Test
    fun testNbmHdrMode() {
        initHdrModifier()
        // screen size = 10_000
        val transitionPoint = 0.55f
        setupDisplay(
            width = 100, height = 100, hdrBrightnessData = createHdrBrightnessData(
                minimumHdrPercentOfScreenForNbm = 0.5f,
                minimumHdrPercentOfScreenForHbm = 0.7f,
                transitionPoint = transitionPoint,
                sdrToHdrRatioSpline = mockSpline
            )
        )
        // hdr size = 5_100
        setupHdrLayer(width = 100, height = 51)

        whenever(
            mockDisplayDeviceConfig.getHdrBrightnessFromSdr(
                /* brightness= */ 0f, MAX_HDR_RATIO, /* ratioScaleFactor= */ 1f, mockSpline
            )
        ).thenReturn(0.85f)

        assertModifierState(
            maxBrightness = transitionPoint,
            hdrRatio = MAX_HDR_RATIO,
            hdrBrightness = transitionPoint,
            spline = mockSpline
        )
    }

    @Test
    fun testHbmHdrMode() {
        initHdrModifier()
        // screen size = 10_000
        setupDisplay(
            width = 100, height = 100, hdrBrightnessData = createHdrBrightnessData(
                minimumHdrPercentOfScreenForNbm = 0.5f,
                minimumHdrPercentOfScreenForHbm = 0.7f,
                transitionPoint = 0.55f,
                sdrToHdrRatioSpline = mockSpline
            )
        )
        // hdr size = 7_100
        setupHdrLayer(width = 100, height = 71)

        val expectedHdrBrightness = 0.92f
        whenever(
            mockDisplayDeviceConfig.getHdrBrightnessFromSdr(
                /* brightness= */ 0f, MAX_HDR_RATIO, /* ratioScaleFactor= */ 1f, mockSpline
            )
        ).thenReturn(expectedHdrBrightness)

        assertModifierState(
            hdrRatio = MAX_HDR_RATIO,
            hdrBrightness = expectedHdrBrightness,
            spline = mockSpline
        )
    }

    @Test
    fun testDisplayChange_noHdrContent() {
        initHdrModifier()
        setupDisplay(width = 100, height = 100)
        assertModifierState()
        clearInvocations(mockChangeListener)
        // display change, new instance of HdrBrightnessData
        setupDisplay(width = 100, height = 100)

        assertModifierState()
        verify(mockChangeListener, never()).onChanged()
    }

    @Test
    fun testDisplayChange_hdrContent() {
        initHdrModifier()
        setupDisplay(width = 100, height = 100)
        setupHdrLayer(width = 100, height = 100, maxHdrRatio = 5f)
        assertModifierState(
            hdrBrightness = 0f,
            hdrRatio = 5f,
            spline = mockSpline
        )
        clearInvocations(mockChangeListener)
        // display change, new instance of HdrBrightnessData
        setupDisplay(width = 100, height = 100)

        assertModifierState(
            hdrBrightness = 0f,
            hdrRatio = 5f,
            spline = mockSpline
        )
        // new instance of HdrBrightnessData received, notify listener
        verify(mockChangeListener).onChanged()
    }

    @Test
    fun testSetAmbientLux_decreaseAboveMaxBrightnessLimitNoHdr() {
        initHdrModifier()
        modifier.setAmbientLux(1000f)
        setupDisplay(width = 100, height = 100, hdrBrightnessData = createHdrBrightnessData(
            maxBrightnessLimits = mapOf(Pair(500f, 0.6f))
        ))

        modifier.setAmbientLux(500f)
        // verify debounce is not scheduled
        assertThat(testHandler.hasMessagesOrCallbacks()).isFalse()

        assertModifierState()
        verify(mockDisplayDeviceConfig, never()).getHdrBrightnessFromSdr(any(), any(), any())
    }

    @Test
    fun testSetAmbientLux_decreaseAboveMaxBrightnessLimitWithHdr() {
        initHdrModifier()
        modifier.setAmbientLux(1000f)
        setupDisplay(
            width = 200, height = 200, hdrBrightnessData = createHdrBrightnessData(
                maxBrightnessLimits = mapOf(Pair(500f, 0.6f)),
                sdrToHdrRatioSpline = mockSpline
            )
        )
        setupHdrLayer(width = 200, height = 200)

        modifier.setAmbientLux(500f)

        // verify debounce is not scheduled
        assertThat(testHandler.hasMessagesOrCallbacks()).isFalse()

        val hdrBrightnessFromSdr = 0.83f
        whenever(
            mockDisplayDeviceConfig.getHdrBrightnessFromSdr(
                /* brightness= */ 0f,
                MAX_HDR_RATIO,
                /* ratioScaleFactor= */ 1f,
                mockSpline
            )
        ).thenReturn(hdrBrightnessFromSdr)

        assertModifierState(
            hdrBrightness = hdrBrightnessFromSdr,
            spline = mockSpline,
            hdrRatio = MAX_HDR_RATIO
        )
    }

    @Test
    fun testSetAmbientLux_decreaseBelowMaxBrightnessLimitNoHdr() {
        initHdrModifier()
        modifier.setAmbientLux(1000f)
        setupDisplay(width = 100, height = 100, hdrBrightnessData = createHdrBrightnessData(
            maxBrightnessLimits = mapOf(Pair(500f, 0.6f))
        ))

        modifier.setAmbientLux(499f)
        // verify debounce is not scheduled
        assertThat(testHandler.hasMessagesOrCallbacks()).isFalse()

        assertModifierState()
        verify(mockDisplayDeviceConfig, never()).getHdrBrightnessFromSdr(any(), any(), any())
    }

    @Test
    fun testSetAmbientLux_decreaseBelowMaxBrightnessLimitWithHdr() {
        initHdrModifier()
        modifier.setAmbientLux(1000f)
        val maxBrightness = 0.6f
        val brightnessDecreaseDebounceMillis = 2800L
        val animationRate = 0.01f
        setupDisplay(
            width = 200, height = 200, hdrBrightnessData = createHdrBrightnessData(
                maxBrightnessLimits = mapOf(Pair(500f, maxBrightness)),
                brightnessDecreaseDebounceMillis = brightnessDecreaseDebounceMillis,
                screenBrightnessRampDecrease = animationRate,
                sdrToHdrRatioSpline = mockSpline,
            )
        )
        setupHdrLayer(width = 200, height = 200)

        modifier.setAmbientLux(499f)

        val hdrBrightnessFromSdr = 0.83f
        whenever(
            mockDisplayDeviceConfig.getHdrBrightnessFromSdr(/* brightness= */ 0f,
                MAX_HDR_RATIO, /* ratioScaleFactor= */ 1f, mockSpline
            )
        ).thenReturn(
            hdrBrightnessFromSdr
        )
        // debounce with brightnessDecreaseDebounceMillis, no changes to the state just yet
        assertModifierState(
            hdrBrightness = hdrBrightnessFromSdr,
            spline = mockSpline,
            hdrRatio = MAX_HDR_RATIO
        )

        // verify debounce is scheduled
        assertThat(testHandler.hasMessagesOrCallbacks()).isTrue()
        val msgInfo = testHandler.pendingMessages.peek()
        assertSendTime(brightnessDecreaseDebounceMillis, msgInfo!!.sendTime)
        clearInvocations(mockChangeListener)

        // triggering debounce, state changes
        testHandler.flush()

        verify(mockChangeListener).onChanged()

        assertModifierState(
            hdrBrightness = maxBrightness,
            spline = mockSpline,
            hdrRatio = MAX_HDR_RATIO,
            maxBrightness = maxBrightness,
            animationRate = animationRate
        )
    }

    @Test
    fun testLowPower_notAllowedInLowPower() {
        initHdrModifier()
        setupDisplay(width = 100, height = 100, hdrBrightnessData = createHdrBrightnessData(
            allowInLowPowerMode = false
        ))
        setupHdrLayer(width = 100, height = 100)
        clearInvocations(mockChangeListener)

        testInjector.isLowPower = true
        testInjector.registeredLowPowerModeSettingObserver!!.onChange(true)

        verify(mockChangeListener).onChanged()
        assertModifierState()
    }

    @Test
    fun lowPowerModeChange_noModeChange() {
        initHdrModifier()
        setupDisplay(width = 100, height = 100, hdrBrightnessData = createHdrBrightnessData(
            allowInLowPowerMode = false
        ))
        testInjector.registeredLowPowerModeSettingObserver!!.onChange(true)

        verifyNoInteractions(mockChangeListener)
    }

    @Test
    fun hdrBrightnessEnabledChanged() {
        whenever(mockFlags.isHdrBrightnessSettingEnabled()).thenReturn(true)
        initHdrModifier()
        setupDisplay(width = 100, height = 100, hdrBrightnessData = createHdrBrightnessData(
            allowInLowPowerMode = false
        ))
        setupHdrLayer(width = 100, height = 100)
        clearInvocations(mockChangeListener)

        testInjector.hdrBrightnessEnabled = false
        testInjector.registeredHdrSettingsObserver!!.onChange(true,
            hdrBrightnessEnabledSetting)

        verify(mockChangeListener).onChanged()
        assertModifierState()

        clearInvocations(mockChangeListener)

        val expectedHdrBrightness = 0.96f
        whenever(mockDisplayDeviceConfig.getHdrBrightnessFromSdr(/* brightness= */ 0f,
            MAX_HDR_RATIO, /* ratioScaleFactor= */ 1f, /* sdrToHdrSpline= */ null))
            .thenReturn(expectedHdrBrightness)
        testInjector.hdrBrightnessEnabled = true
        testInjector.registeredHdrSettingsObserver!!.onChange(true,
            hdrBrightnessEnabledSetting)

        verify(mockChangeListener).onChanged()
        assertModifierState(hdrRatio = MAX_HDR_RATIO, hdrBrightness = expectedHdrBrightness,
            ratioScaleFactor = 1f)
    }

    @Test
    fun hdrBrightnessEnabledChanged_noHdrMode() {
        whenever(mockFlags.isHdrBrightnessSettingEnabled()).thenReturn(true)
        initHdrModifier()
        setupDisplay(width = 100, height = 100, hdrBrightnessData = createHdrBrightnessData(
            allowInLowPowerMode = false
        ))
        clearInvocations(mockChangeListener)
        testInjector.registeredHdrSettingsObserver!!.onChange(true,
            hdrBrightnessEnabledSetting)

        verifyNoInteractions(mockChangeListener)
    }

    @Test
    fun hdrBrightnessBoostLevelChanged() {
        whenever(mockFlags.isHdrBrightnessSettingEnabled()).thenReturn(true)
        initHdrModifier()
        setupDisplay(width = 100, height = 100, hdrBrightnessData = createHdrBrightnessData(
            allowInLowPowerMode = false
        ))
        setupHdrLayer(width = 100, height = 100)
        clearInvocations(mockChangeListener)

        var expectedHdrBrightness = 0.92f
        var brightnessBoostLevel = 0.3f
        var ratioScaleFactor = BrightnessUtils.convertGammaToLinear(brightnessBoostLevel)
        whenever(mockDisplayDeviceConfig.getHdrBrightnessFromSdr(/* brightness= */ 0f,
            MAX_HDR_RATIO, ratioScaleFactor, /* sdrToHdrSpline= */ null))
            .thenReturn(expectedHdrBrightness)
        testInjector.hdrBrBoostLevel = brightnessBoostLevel
        testInjector.registeredHdrSettingsObserver!!.onChange(true,
            hdrBrightnessBoostLevelSetting)

        verify(mockChangeListener).onChanged()
        assertModifierState(hdrRatio = MAX_HDR_RATIO, hdrBrightness = expectedHdrBrightness,
            ratioScaleFactor = ratioScaleFactor)

        clearInvocations(mockChangeListener)

        expectedHdrBrightness = 0.98f
        brightnessBoostLevel = 0.65f
        ratioScaleFactor = BrightnessUtils.convertGammaToLinear(brightnessBoostLevel)
        whenever(mockDisplayDeviceConfig.getHdrBrightnessFromSdr(/* brightness= */ 0f,
            MAX_HDR_RATIO, ratioScaleFactor, /* sdrToHdrSpline= */ null))
            .thenReturn(expectedHdrBrightness)
        testInjector.hdrBrBoostLevel = brightnessBoostLevel
        testInjector.registeredHdrSettingsObserver!!.onChange(true,
            hdrBrightnessBoostLevelSetting)

        verify(mockChangeListener).onChanged()
        assertModifierState(hdrRatio = MAX_HDR_RATIO, hdrBrightness = expectedHdrBrightness,
            ratioScaleFactor = ratioScaleFactor)
    }

    @Test
    fun hdrBrightnessBoostLevelChanged_noHdrMode() {
        whenever(mockFlags.isHdrBrightnessSettingEnabled()).thenReturn(true)
        initHdrModifier()
        setupDisplay(width = 100, height = 100, hdrBrightnessData = createHdrBrightnessData(
            allowInLowPowerMode = false
        ))
        clearInvocations(mockChangeListener)
        testInjector.registeredHdrSettingsObserver!!.onChange(true,
            hdrBrightnessBoostLevelSetting)

        verifyNoInteractions(mockChangeListener)
    }

    @Test
    fun stopUnregistersHdrListener() {
        hdrListenerRegisteredOnInit_hdrDataPresent()

        modifier.stop()

        assertThat(testInjector.registeredHdrListener).isNull()
        assertThat(testInjector.registeredToken).isNull()
    }

    @Test
    fun stopUnregistersContentObservers() {
        contentObserversRegisteredOnInit_flagEnabled()

        modifier.stop()

        assertThat(testInjector.registeredLowPowerModeSettingObserver).isNull()
        assertThat(testInjector.registeredHdrSettingsObserver).isNull()
    }

    @Test
    fun hdrBoostOverrideListener_registeredOnInit() {
        initHdrModifier()

        assertThat(testInjector.registeredHdrOverrideListener).isNotNull()
        assertThat(testInjector.registeredUniqueId).isEqualTo(dummyData.uniqueDisplayId)
    }

    @Test
    fun hdrBoostOverrideListener_unregisteredOnStop() {
        initHdrModifier()

        modifier.stop()

        assertThat(testInjector.registeredHdrOverrideListener).isNull()
        assertThat(testInjector.registeredUniqueId).isNull()
    }

    @Test
    fun hdrBoostOverrideListener_noHdrData_notRegistered() {
        initHdrModifier(hdrBrightnessData = null)

        assertThat(testInjector.registeredHdrOverrideListener).isNull()
        assertThat(testInjector.registeredUniqueId).isNull()
    }

    @Test
    fun hdrBoostOverrideListener_displayChangedWithNoHdrData_unregister() {
        initHdrModifier()

        whenever(mockDisplayDeviceConfig.hdrBrightnessData).thenReturn(null)
        modifier.onDisplayChanged(dummyData)

        assertThat(testInjector.registeredHdrListener).isNull()
        assertThat(testInjector.registeredToken).isNull()
    }

    @Test
    fun hdrBoostOverrideListener_displayChangedWithDifferentDevice() {
        val otherDisplayId = "otherDisplayUniqueId"
        initHdrModifier()
        modifier.onDisplayChanged(createDisplayDeviceData(
            mockDisplayDeviceConfig, mockDisplayBinder, otherDisplayId))

        assertThat(testInjector.registeredHdrListener).isNotNull()
        assertThat(testInjector.registeredUniqueId).isEqualTo(otherDisplayId)
    }

    @Test
    fun testHdrBrightnessOverride_hdrOff() {
        initHdrModifier()
        // screen size = 10_000
        setupDisplay(
            width = 100, height = 100, hdrBrightnessData = createHdrBrightnessData(
                minimumHdrPercentOfScreenForNbm = 0.5f,
                minimumHdrPercentOfScreenForHbm = 0.7f,
                transitionPoint = 0.55f,
                sdrToHdrRatioSpline = mockSpline
            )
        )
        // hdr size = 7_100
        setupHdrLayer(width = 100, height = 71)
        // override hdr boost
        testInjector.registeredHdrOverrideListener!!.onChanged(HdrBoostOverride.forHdrOff())
        testHandler.flush()

        assertModifierState()
    }

    @Test
    fun testHdrBrightnessOverride_ratioSet() {
        initHdrModifier()
        // screen size = 10_000
        setupDisplay(
            width = 100, height = 100, hdrBrightnessData = createHdrBrightnessData(
                minimumHdrPercentOfScreenForNbm = 0.5f,
                minimumHdrPercentOfScreenForHbm = 0.7f,
                transitionPoint = 0.55f,
                sdrToHdrRatioSpline = mockSpline
            )
        )
        // hdr size = 7_100
        setupHdrLayer(width = 100, height = 71)
        // override hdr boost
        val expectedSdrHdrRatio = 7.0f
        val expectedBrightness = 0.94f
        whenever(mockDisplayDeviceConfig
            .getHdrBrightnessFromSdr(0f, expectedSdrHdrRatio))
            .thenReturn(expectedBrightness)
        testInjector.registeredHdrOverrideListener!!.onChanged(
            HdrBoostOverride.forSdrHdrRatio(expectedSdrHdrRatio))
        testHandler.flush()

        assertModifierState(hdrRatio = expectedSdrHdrRatio, hdrBrightness = expectedBrightness)
    }

    // Helper functions
    private fun setupHdrLayer(width: Int = 100, height: Int = 100,
        maxHdrRatio: Float = MAX_HDR_RATIO
    ) {
        testInjector.registeredHdrListener!!.onHdrInfoChanged(
            mockDisplayBinder, 1, width, height, 0, maxHdrRatio
        )
        testHandler.flush()
    }

    private fun setupDisplay(
        width: Int = 100,
        height: Int = 100,
        hdrBrightnessData: HdrBrightnessData? = createHdrBrightnessData(
            minimumHdrPercentOfScreenForNbm = 0.5f,
            minimumHdrPercentOfScreenForHbm = 0.7f,
            transitionPoint = 0.68f,
            sdrToHdrRatioSpline = mockSpline
        )
    ) {
        whenever(mockDisplayDeviceConfig.hdrBrightnessData).thenReturn(hdrBrightnessData)
        modifier.onDisplayChanged(createDisplayDeviceData(
            mockDisplayDeviceConfig, mockDisplayBinder,
            width = width,
            height = height
        ))
    }

    private fun initHdrModifier(hdrBrightnessData: HdrBrightnessData? = createHdrBrightnessData()) {
        whenever(mockDisplayDeviceConfig.hdrBrightnessData).thenReturn(hdrBrightnessData)
        modifier =
            HdrBrightnessModifier(
                testHandler,
                mockFlags,
                mockChangeListener,
                testInjector,
                dummyData
            )
        testHandler.flush()
    }

    // MsgInfo.sendTime is calculated first by adding SystemClock.uptimeMillis()
    // (in Handler.sendMessageDelayed) and then by subtracting SystemClock.uptimeMillis()
    // (in TestHandler.sendMessageAtTime, there might be several milliseconds difference between
    // SystemClock.uptimeMillis() calls, and subtracted value might be greater than added.
    private fun assertSendTime(expectedTime: Long, sendTime: Long) {
        assertThat(sendTime).isAtMost(expectedTime)
        assertThat(sendTime).isGreaterThan(expectedTime - SEND_TIME_TOLERANCE)
    }

    private fun assertModifierState(
        maxBrightness: Float = BRIGHTNESS_MAX,
        hdrRatio: Float = DEFAULT_MAX_HDR_SDR_RATIO,
        spline: Spline? = null,
        hdrBrightness: Float = BRIGHTNESS_NOT_SET,
        animationRate: Float = CUSTOM_ANIMATION_RATE_NOT_SET,
        ratioScaleFactor: Float = 1f
    ) {
        val modifierState = ModifiersAggregatedState()
        modifier.applyStateChange(modifierState)

        assertWithMessage("ModifiersAggregatedState has different mMaxHdrBrightness")
            .that(modifierState.mMaxHdrBrightness).isEqualTo(maxBrightness)
        assertWithMessage("ModifiersAggregatedState has different mMaxDesiredHdrRatio")
            .that(modifierState.mMaxDesiredHdrRatio).isEqualTo(hdrRatio)
        assertWithMessage("ModifiersAggregatedState has different mSdrHdrRatioSpline")
            .that(modifierState.mSdrHdrRatioSpline).isEqualTo(spline)
        assertWithMessage("ModifiersAggregatedState has different mHdrRatioScaleFactor")
            .that(modifierState.mHdrRatioScaleFactor).isEqualTo(ratioScaleFactor)

        val stateBuilder = DisplayBrightnessState.builder()
        modifier.apply(mockRequest, stateBuilder)

        assertWithMessage("DisplayBrightnessState has different hdrBrightness")
            .that(stateBuilder.hdrBrightness).isEqualTo(hdrBrightness)
        assertWithMessage("DisplayBrightnessState has different customAnimationRate")
            .that(stateBuilder.customAnimationRate).isEqualTo(animationRate)
    }

    internal class TestInjector() : Injector(mock<Context>(), mock<PluginManager>()) {
        var registeredHdrListener: SurfaceControlHdrLayerInfoListener? = null
        var registeredToken: IBinder? = null
        var registeredLowPowerModeSettingObserver: ContentObserver? = null
        var registeredHdrSettingsObserver: ContentObserver? = null
        var registeredHdrOverrideListener: PluginManager.PluginChangeListener<HdrBoostOverride>? =
            null
        var registeredUniqueId: String? = null

        var isLowPower = false
        var hdrBrightnessEnabled = true
        var hdrBrBoostLevel = 1f

        override fun registerHdrBoostOverrideListener(
            uniqueDisplayId: String,
            listener: PluginManager.PluginChangeListener<HdrBoostOverride>
        ) {
            registeredUniqueId = uniqueDisplayId
            registeredHdrOverrideListener = listener
        }

        override fun unregisterHdrBoostOverrideListener(
            uniqueDisplayId: String,
            listener: PluginManager.PluginChangeListener<HdrBoostOverride>
        ) {
            registeredUniqueId = null
            registeredHdrOverrideListener = null
        }

        override fun registerHdrListener(
            listener: SurfaceControlHdrLayerInfoListener, token: IBinder
        ) {
            registeredHdrListener = listener
            registeredToken = token
        }

        override fun unregisterHdrListener(
            listener: SurfaceControlHdrLayerInfoListener, token: IBinder
        ) {
            registeredHdrListener = null
            registeredToken = null
        }

        override fun registerLowPowerModeSettingObserver(observer: ContentObserver) {
            registeredLowPowerModeSettingObserver = observer
        }

        override fun unregisterLowPowerModeSettingObserver(observer: ContentObserver?) {
            registeredLowPowerModeSettingObserver = null
        }

        override fun registerHdrSettingsObserver(observer: ContentObserver) {
            registeredHdrSettingsObserver = observer
        }

        override fun unregisterHdrSettingObserver(observer: ContentObserver?) {
            registeredHdrSettingsObserver = null
        }

        override fun isLowPowerMode(): Boolean {
            return isLowPower
        }

        override fun isHdrBrightnessEnabled(): Boolean {
            return hdrBrightnessEnabled
        }

        override fun getHdrBrightnessBoostLevel(): Float {
            return hdrBrBoostLevel
        }
    }

    companion object {
        private val hdrBrightnessEnabledSetting = Settings.Secure.getUriFor(
            Settings.Secure.HDR_BRIGHTNESS_ENABLED
        )
        private val hdrBrightnessBoostLevelSetting: Uri = Settings.Secure.getUriFor(
            Settings.Secure.HDR_BRIGHTNESS_BOOST_LEVEL
        )
        private const val MAX_HDR_RATIO = 8f
    }
}