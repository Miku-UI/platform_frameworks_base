/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.display.brightness.clamper;

import static com.android.internal.display.BrightnessSynchronizer.floatEquals;
import static com.android.server.display.BrightnessMappingStrategy.INVALID_LUX;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.hardware.display.BrightnessInfo;
import android.hardware.display.DisplayManagerInternal;
import android.os.PowerManager;

import androidx.test.filters.SmallTest;

import com.android.internal.annotations.Keep;
import com.android.server.display.AutomaticBrightnessController;
import com.android.server.display.DisplayBrightnessState;
import com.android.server.display.DisplayDeviceConfig;
import com.android.server.display.brightness.BrightnessReason;
import com.android.server.testutils.TestHandler;

import com.google.common.collect.ImmutableMap;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

@SmallTest
@RunWith(JUnitParamsRunner.class)
public class BrightnessMaxLuxModifierTest {
    private final TestHandler mHandler = new TestHandler(null);

    private BrightnessMaxLuxModifier mModifier;

    private AutoCloseable mMocksCloseable;

    @Mock
    private BrightnessClamperController.ClamperChangeListener mChangeListener;

    @Mock
    private DisplayManagerInternal.DisplayPowerRequest mMockRequest;

    @Mock
    private BrightnessClamperController.DisplayDeviceData mMockData;

    @Before
    public void setUp() {
        mMocksCloseable = MockitoAnnotations.openMocks(this);
    }

    @After
    public void tearDown() throws Exception {
        mMocksCloseable.close();
    }

    @Keep
    private static Object[][] brightnessData() {
        return new Object[][]{
                // no brightness config
                {0, AutomaticBrightnessController.AUTO_BRIGHTNESS_DISABLED, new HashMap<>(),
                        PowerManager.BRIGHTNESS_MAX},
                {0, AutomaticBrightnessController.AUTO_BRIGHTNESS_ENABLED, new HashMap<>(),
                        PowerManager.BRIGHTNESS_MAX},
                {100, AutomaticBrightnessController.AUTO_BRIGHTNESS_ENABLED, new HashMap<>(),
                        PowerManager.BRIGHTNESS_MAX},
                // Auto brightness - on, config only for default
                {100, AutomaticBrightnessController.AUTO_BRIGHTNESS_ENABLED, ImmutableMap.of(
                        DisplayDeviceConfig.BrightnessLimitMapType.DEFAULT,
                        ImmutableMap.of(99f, 0.1f, 101f, 0.2f)
                ), 0.2f},
                // Auto brightness - off, config only for default
                {100, AutomaticBrightnessController.AUTO_BRIGHTNESS_DISABLED, ImmutableMap.of(
                        DisplayDeviceConfig.BrightnessLimitMapType.DEFAULT,
                        ImmutableMap.of(99f, 0.1f, 101f, 0.2f)
                ), 0.2f},
                // Auto brightness - off, config only for adaptive
                {100, AutomaticBrightnessController.AUTO_BRIGHTNESS_DISABLED, ImmutableMap.of(
                        DisplayDeviceConfig.BrightnessLimitMapType.ADAPTIVE,
                        ImmutableMap.of(99f, 0.1f, 101f, 0.2f)
                ), PowerManager.BRIGHTNESS_MAX},
                // Auto brightness - on, config only for adaptive
                {100, AutomaticBrightnessController.AUTO_BRIGHTNESS_ENABLED, ImmutableMap.of(
                        DisplayDeviceConfig.BrightnessLimitMapType.ADAPTIVE,
                        ImmutableMap.of(99f, 0.1f, 101f, 0.2f)
                ), 0.2f},
                // Auto brightness - on, config for both
                {100, AutomaticBrightnessController.AUTO_BRIGHTNESS_ENABLED, ImmutableMap.of(
                        DisplayDeviceConfig.BrightnessLimitMapType.DEFAULT,
                        ImmutableMap.of(99f, 0.1f, 101f, 0.2f),
                        DisplayDeviceConfig.BrightnessLimitMapType.ADAPTIVE,
                        ImmutableMap.of(99f, 0.3f, 101f, 0.4f)
                ), 0.4f},
                // Auto brightness - off, config for both
                {100, AutomaticBrightnessController.AUTO_BRIGHTNESS_DISABLED, ImmutableMap.of(
                        DisplayDeviceConfig.BrightnessLimitMapType.DEFAULT,
                        ImmutableMap.of(99f, 0.1f, 101f, 0.2f),
                        DisplayDeviceConfig.BrightnessLimitMapType.ADAPTIVE,
                        ImmutableMap.of(99f, 0.3f, 101f, 0.4f)
                ), 0.2f},
                // Auto brightness - on, config for both, ambient high
                {1000, AutomaticBrightnessController.AUTO_BRIGHTNESS_ENABLED, ImmutableMap.of(
                        DisplayDeviceConfig.BrightnessLimitMapType.DEFAULT,
                        ImmutableMap.of(1000f, 0.1f, 2000f, 0.2f),
                        DisplayDeviceConfig.BrightnessLimitMapType.ADAPTIVE,
                        ImmutableMap.of(99f, 0.3f, 101f, 0.4f)
                ), PowerManager.BRIGHTNESS_MAX},
        };
    }

    @Test
    @Parameters(method = "brightnessData")
    public void testReturnsCorrectMaxBrightness(float ambientLux, int autoBrightnessState,
            Map<DisplayDeviceConfig.BrightnessLimitMapType, Map<Float, Float>> maxBrightnessConfig,
            float brightnessCap) {
        setUpModifier(ambientLux, autoBrightnessState, maxBrightnessConfig);

        if (floatEquals(brightnessCap, PowerManager.BRIGHTNESS_MAX)) {
            // No cap, modifier inactive
            assertModifierState(PowerManager.BRIGHTNESS_MAX, PowerManager.BRIGHTNESS_MAX,
                    PowerManager.BRIGHTNESS_MAX, /* isActive= */ false);
        } else {
            // Current brightness below cap
            assertModifierState(brightnessCap / 2, brightnessCap,
                    brightnessCap / 2, /* isActive= */ true);

            // Current brightness above cap
            assertModifierState(PowerManager.BRIGHTNESS_MAX, brightnessCap,
                    brightnessCap, /* isActive= */ true);
        }
    }

    @Test
    @Parameters(method = "brightnessData")
    public void testShouldListenToLightSensor(float ambientLux, int autoBrightnessState,
            Map<DisplayDeviceConfig.BrightnessLimitMapType, Map<Float, Float>> maxBrightnessConfig,
            float brightnessCap) {
        setUpModifier(ambientLux, autoBrightnessState, maxBrightnessConfig);

        boolean defaultConfig = maxBrightnessConfig.containsKey(
                DisplayDeviceConfig.BrightnessLimitMapType.DEFAULT);
        boolean adaptiveConfig = maxBrightnessConfig.containsKey(
                DisplayDeviceConfig.BrightnessLimitMapType.ADAPTIVE);
        boolean autoBrightnessEnabled =
                autoBrightnessState == AutomaticBrightnessController.AUTO_BRIGHTNESS_ENABLED;
        boolean expectedShouldListenToLightSensor =
                defaultConfig || (autoBrightnessEnabled && adaptiveConfig);
        assertThat(mModifier.shouldListenToLightSensor()).isEqualTo(
                expectedShouldListenToLightSensor);
    }

    @Test
    public void testAmbientLuxChange() {
        setUpModifier(INVALID_LUX, AutomaticBrightnessController.AUTO_BRIGHTNESS_DISABLED,
                (Map<DisplayDeviceConfig.BrightnessLimitMapType, Map<Float, Float>>)
                        brightnessData()[3][2]);
        assertModifierState(/* unclampedBrightness= */ 0.3f, PowerManager.BRIGHTNESS_MAX,
                /* clampedBrightness= */ 0.3f, /* isActive= */ false);
        clearInvocations(mChangeListener);

        mModifier.setAmbientLux(100);

        assertModifierState(/* unclampedBrightness= */ 0.3f, /* maxBrightness= */ 0.2f,
                /* clampedBrightness= */ 0.2f, /* isActive= */ true);
        verify(mChangeListener).onChanged();
    }

    @Test
    public void testOnDisplayChanged() {
        setUpModifier(100, AutomaticBrightnessController.AUTO_BRIGHTNESS_ENABLED,
                (Map<DisplayDeviceConfig.BrightnessLimitMapType, Map<Float, Float>>)
                        brightnessData()[0][2]);
        assertModifierState(/* unclampedBrightness= */ 0.3f, PowerManager.BRIGHTNESS_MAX,
                /* clampedBrightness= */ 0.3f, /* isActive= */ false);
        clearInvocations(mChangeListener);

        when(mMockData.getMaxBrightnessLimits()).thenReturn(
                (Map<DisplayDeviceConfig.BrightnessLimitMapType, Map<Float, Float>>)
                        brightnessData()[3][2]);
        mModifier.onDisplayChanged(mMockData);
        mHandler.flush();

        assertModifierState(/* unclampedBrightness= */ 0.3f, /* maxBrightness= */ 0.2f,
                /* clampedBrightness= */ 0.2f, /* isActive= */ true);
        verify(mChangeListener).onChanged();
    }

    @Test
    public void testAutoBrightnessStateChange() {
        setUpModifier(100, AutomaticBrightnessController.AUTO_BRIGHTNESS_ENABLED,
                (Map<DisplayDeviceConfig.BrightnessLimitMapType, Map<Float, Float>>)
                        brightnessData()[7][2]);
        assertModifierState(/* unclampedBrightness= */ 0.3f, /* maxBrightness= */ 0.4f,
                /* clampedBrightness= */ 0.3f, /* isActive= */ true);
        clearInvocations(mChangeListener);

        mModifier.setAutoBrightnessState(AutomaticBrightnessController.AUTO_BRIGHTNESS_DISABLED);

        assertModifierState(/* unclampedBrightness= */ 0.3f, /* maxBrightness= */ 0.2f,
                /* clampedBrightness= */ 0.2f, /* isActive= */ true);
        verify(mChangeListener).onChanged();
    }

    @Test
    public void testHbmAllowed() {
        setUpModifier(100, AutomaticBrightnessController.AUTO_BRIGHTNESS_DISABLED,
                (Map<DisplayDeviceConfig.BrightnessLimitMapType, Map<Float, Float>>)
                        brightnessData()[3][2]);
        assertModifierState(/* unclampedBrightness= */ 0.3f, /* maxBrightness= */ 0.2f,
                /* clampedBrightness= */ 0.2f, /* isActive= */ true);
        clearInvocations(mChangeListener);

        mModifier.setIsHbmAllowed(true);

        assertModifierState(PowerManager.BRIGHTNESS_MAX, PowerManager.BRIGHTNESS_MAX,
                PowerManager.BRIGHTNESS_MAX, /* isActive= */ false);
        verify(mChangeListener).onChanged();
    }

    private void setUpModifier(float ambientLux, int autoBrightnessState,
            Map<DisplayDeviceConfig.BrightnessLimitMapType, Map<Float, Float>>
                    maxBrightnessConfig) {
        when(mMockData.getMaxBrightnessLimits()).thenReturn(maxBrightnessConfig);
        mModifier = new BrightnessMaxLuxModifier(mHandler, mChangeListener, mMockData);
        mModifier.setAmbientLux(ambientLux);
        mModifier.setAutoBrightnessState(autoBrightnessState);
        mHandler.flush();
    }

    private void assertModifierState(float unclampedBrightness, float maxBrightness,
            float clampedBrightness, boolean isActive) {
        BrightnessClamperController.ModifiersAggregatedState
                modifierState = new BrightnessClamperController.ModifiersAggregatedState();
        DisplayBrightnessState.Builder stateBuilder = DisplayBrightnessState.builder();
        stateBuilder.setBrightness(unclampedBrightness);
        stateBuilder.setIsSlowChange(false);

        int maxBrightnessReason = isActive ? BrightnessInfo.BRIGHTNESS_MAX_REASON_LUX
                : BrightnessInfo.BRIGHTNESS_MAX_REASON_NONE;
        int modifier = unclampedBrightness > maxBrightness ? BrightnessReason.MODIFIER_MAX_LUX : 0;

        mModifier.applyStateChange(modifierState);
        assertThat(floatEquals(maxBrightness, modifierState.mMaxBrightness)).isTrue();
        assertThat(modifierState.mMaxBrightnessReason).isEqualTo(maxBrightnessReason);

        mModifier.apply(mMockRequest, stateBuilder);
        assertThat(floatEquals(maxBrightness, stateBuilder.getMaxBrightness())).isTrue();
        assertThat(floatEquals(clampedBrightness, stateBuilder.getBrightness())).isTrue();
        assertThat(stateBuilder.getBrightnessMaxReason()).isEqualTo(maxBrightnessReason);
        assertThat(stateBuilder.getBrightnessReason().getModifier()).isEqualTo(modifier);
        assertThat(stateBuilder.isSlowChange()).isEqualTo(false);
    }
}
