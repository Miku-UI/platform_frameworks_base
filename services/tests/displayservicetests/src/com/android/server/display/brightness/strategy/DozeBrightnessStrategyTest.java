/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.display.brightness.strategy;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.hardware.display.DisplayManagerInternal;
import android.hardware.display.DisplayManagerInternal.DisplayPowerRequest;
import android.testing.TestableContext;
import android.view.Display;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.display.DisplayBrightnessState;
import com.android.server.display.brightness.BrightnessEvent;
import com.android.server.display.brightness.BrightnessReason;
import com.android.server.display.brightness.StrategyExecutionRequest;
import com.android.server.display.feature.DisplayManagerFlags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DozeBrightnessStrategyTest {
    private static final float DOZE_SCALE_FACTOR = 0.15f;
    private static final float DEFAULT_DOZE_BRIGHTNESS = 0.31f;

    private DozeBrightnessStrategy mDozeBrightnessModeStrategy;
    private BrightnessEvent mBrightnessEvent = new BrightnessEvent(Display.DEFAULT_DISPLAY);

    @Mock
    private DisplayManagerFlags mFlags;

    @Rule
    public final TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getContext());

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        mContext.getOrCreateTestableResources().addOverride(
                com.android.internal.R.fraction.config_screenAutoBrightnessDozeScaleFactor,
                DOZE_SCALE_FACTOR);
        DozeBrightnessStrategy.Injector injector = new DozeBrightnessStrategy.Injector() {
            @Override
            public BrightnessEvent getBrightnessEvent(int displayId) {
                return mBrightnessEvent;
            }
        };
        mDozeBrightnessModeStrategy = new DozeBrightnessStrategy(injector, mFlags, mContext,
                Display.DEFAULT_DISPLAY, DEFAULT_DOZE_BRIGHTNESS);
    }

    @Test
    public void testUpdateBrightness_DozeBrightnessFromPowerRequest() {
        DisplayPowerRequest displayPowerRequest = new DisplayPowerRequest();
        float dozeScreenBrightness = 0.2f;
        displayPowerRequest.dozeScreenBrightness = dozeScreenBrightness;
        BrightnessReason brightnessReason = new BrightnessReason();
        brightnessReason.setReason(BrightnessReason.REASON_DOZE);
        DisplayBrightnessState expectedDisplayBrightnessState =
                new DisplayBrightnessState.Builder()
                        .setBrightness(dozeScreenBrightness)
                        .setBrightnessReason(brightnessReason)
                        .setDisplayBrightnessStrategyName(mDozeBrightnessModeStrategy.getName())
                        .build();
        DisplayBrightnessState updatedDisplayBrightnessState =
                mDozeBrightnessModeStrategy.updateBrightness(
                        new StrategyExecutionRequest(displayPowerRequest,
                                /* currentScreenBrightness= */ 0.2f,
                                /* userSetBrightnessChanged= */ false,
                                /* isStylusBeingUsed */ false, /* offloadSession= */ null));
        assertEquals(updatedDisplayBrightnessState, expectedDisplayBrightnessState);
    }

    @Test
    public void testUpdateBrightness_ManualDozeBrightness() {
        when(mFlags.isDisplayOffloadEnabled()).thenReturn(true);
        DisplayPowerRequest displayPowerRequest = new DisplayPowerRequest();
        float currentScreenBrightness = 0.2f;
        BrightnessReason brightnessReason = new BrightnessReason();
        brightnessReason.setReason(BrightnessReason.REASON_DOZE_MANUAL);
        mBrightnessEvent.setFlags(BrightnessEvent.FLAG_DOZE_SCALE);
        DisplayBrightnessState expectedDisplayBrightnessState =
                new DisplayBrightnessState.Builder()
                        .setBrightness(currentScreenBrightness * DOZE_SCALE_FACTOR)
                        .setBrightnessReason(brightnessReason)
                        .setDisplayBrightnessStrategyName(mDozeBrightnessModeStrategy.getName())
                        .setBrightnessEvent(mBrightnessEvent)
                        .build();
        DisplayBrightnessState updatedDisplayBrightnessState =
                mDozeBrightnessModeStrategy.updateBrightness(
                        new StrategyExecutionRequest(displayPowerRequest, currentScreenBrightness,
                                /* userSetBrightnessChanged= */ false,
                                /* isStylusBeingUsed= */ false,
                                mock(DisplayManagerInternal.DisplayOffloadSession.class)));
        assertEquals(expectedDisplayBrightnessState, updatedDisplayBrightnessState);
    }

    @Test
    public void testUpdateBrightness_DefaultDozeBrightness() {
        DisplayPowerRequest displayPowerRequest = new DisplayPowerRequest();
        BrightnessReason brightnessReason = new BrightnessReason();
        brightnessReason.setReason(BrightnessReason.REASON_DOZE_DEFAULT);
        DisplayBrightnessState expectedDisplayBrightnessState =
                new DisplayBrightnessState.Builder()
                        .setBrightness(DEFAULT_DOZE_BRIGHTNESS)
                        .setBrightnessReason(brightnessReason)
                        .setDisplayBrightnessStrategyName(mDozeBrightnessModeStrategy.getName())
                        .build();
        DisplayBrightnessState updatedDisplayBrightnessState =
                mDozeBrightnessModeStrategy.updateBrightness(
                        new StrategyExecutionRequest(displayPowerRequest, DEFAULT_DOZE_BRIGHTNESS,
                                /* userSetBrightnessChanged= */ false,
                                /* isStylusBeingUsed= */ false,
                                mock(DisplayManagerInternal.DisplayOffloadSession.class)));
        assertEquals(expectedDisplayBrightnessState, updatedDisplayBrightnessState);
    }
}
