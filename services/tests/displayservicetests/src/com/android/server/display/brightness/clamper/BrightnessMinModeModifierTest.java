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

package com.android.server.display.brightness.clamper;

import static com.android.server.display.brightness.clamper.BrightnessMinModeModifier.MINMODE_ACTIVE;
import static com.android.server.display.brightness.clamper.BrightnessMinModeModifier.MINMODE_INACTIVE;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.hardware.display.BrightnessInfo;
import android.hardware.display.DisplayManagerInternal;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.testing.TestableContext;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.display.BrightnessSynchronizer;
import com.android.server.display.DisplayBrightnessState;
import com.android.server.display.DisplayDeviceConfig;
import com.android.server.display.brightness.BrightnessReason;
import com.android.server.display.brightness.clamper.BrightnessClamperController.ModifiersAggregatedState;
import com.android.server.testutils.TestHandler;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class BrightnessMinModeModifierTest {
    private static final int NO_MODIFIER = 0;
    private static final float BRIGHTNESS_CAP = 0.3f;
    private static final String DISPLAY_ID = "displayId";

    @Mock private BrightnessClamperController.ClamperChangeListener mMockClamperChangeListener;
    @Mock private DisplayManagerInternal.DisplayPowerRequest mMockRequest;
    @Mock private DisplayDeviceConfig mMockDisplayDeviceConfig;
    @Mock private IBinder mMockBinder;

    @Rule
    public final TestableContext mContext =
            new TestableContext(InstrumentationRegistry.getInstrumentation().getContext());

    private final TestHandler mTestHandler = new TestHandler(null);
    private final TestInjector mInjector = new TestInjector();
    private BrightnessMinModeModifier mModifier;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mModifier =
                new BrightnessMinModeModifier(
                        mInjector,
                        mTestHandler,
                        mContext,
                        mMockClamperChangeListener,
                        () -> BRIGHTNESS_CAP);
        mTestHandler.flush();
    }

    @Test
    public void testMinModeOff() {
        setMinModeEnabled(false);
        assertModifierState(
                /* currentBrightness= */ 0.5f,
                /* currentSlowChange= */ true,
                /* maxBrightness= */ PowerManager.BRIGHTNESS_MAX,
                /* brightness= */ 0.5f,
                /* isActive= */ false,
                /* isSlowChange= */ true);
        verify(mMockClamperChangeListener).onChanged();
    }

    @Test
    public void testMinModeOn() {
        setMinModeEnabled(true);
        assertModifierState(
                /* currentBrightness= */ 0.5f,
                /* currentSlowChange= */ true,
                /* maxBrightness= */ BRIGHTNESS_CAP,
                /* brightness= */ BRIGHTNESS_CAP,
                /* isActive= */ true,
                /* isSlowChange= */ false);
        verify(mMockClamperChangeListener).onChanged();
    }

    @Test
    public void testOnDisplayChanged() {
        setMinModeEnabled(true);
        clearInvocations(mMockClamperChangeListener);
        float newBrightnessCap = 0.61f;
        onDisplayChange(newBrightnessCap);
        mTestHandler.flush();

        assertModifierState(
                /* currentBrightness= */ 0.5f,
                /* currentSlowChange= */ true,
                /* maxBrightness= */ newBrightnessCap,
                /* brightness= */ 0.5f,
                /* isActive= */ true,
                /* isSlowChange= */ false);
        verify(mMockClamperChangeListener).onChanged();
    }

    private void setMinModeEnabled(boolean enabled) {
        Settings.Global.putInt(
                mContext.getContentResolver(),
                Settings.Global.MINMODE_ACTIVE,
                enabled ? MINMODE_ACTIVE : MINMODE_INACTIVE);
        mInjector.notifyMinModeChanged();
        mTestHandler.flush();
    }

    private void onDisplayChange(float brightnessCap) {
        when(mMockDisplayDeviceConfig.getBrightnessCapForMinMode()).thenReturn(brightnessCap);
        mModifier.onDisplayChanged(
                ClamperTestUtilsKt.createDisplayDeviceData(
                        mMockDisplayDeviceConfig,
                        mMockBinder,
                        DISPLAY_ID,
                        DisplayDeviceConfig.DEFAULT_ID));
    }

    private void assertModifierState(
            float currentBrightness,
            boolean currentSlowChange,
            float maxBrightness,
            float brightness,
            boolean isActive,
            boolean isSlowChange) {
        ModifiersAggregatedState modifierState = new ModifiersAggregatedState();
        DisplayBrightnessState.Builder stateBuilder = DisplayBrightnessState.builder();
        stateBuilder.setBrightness(currentBrightness);
        stateBuilder.setIsSlowChange(currentSlowChange);

        int maxBrightnessReason =
                isActive
                        ? BrightnessInfo.BRIGHTNESS_MAX_REASON_MINMODE
                        : BrightnessInfo.BRIGHTNESS_MAX_REASON_NONE;

        mModifier.applyStateChange(modifierState);
        assertWithMessage("ModifiersAggregatedState has different mMaxBrightness")
                .that(modifierState.mMaxBrightness)
                .isEqualTo(maxBrightness);
        assertWithMessage("ModifiersAggregatedState has different mMaxBrightnessReason")
                .that(modifierState.mMaxBrightnessReason)
                .isEqualTo(maxBrightnessReason);

        mModifier.apply(mMockRequest, stateBuilder);

        assertWithMessage("DisplayBrightnessState has different maxBrightness")
                .that(stateBuilder.getMaxBrightness())
                .isWithin(BrightnessSynchronizer.EPSILON)
                .of(maxBrightness);
        assertWithMessage("DisplayBrightnessState has different brightness")
                .that(stateBuilder.getBrightness())
                .isWithin(BrightnessSynchronizer.EPSILON)
                .of(brightness);
        assertWithMessage("DisplayBrightnessState has different brightnessMaxReason")
                .that(stateBuilder.getBrightnessMaxReason())
                .isEqualTo(maxBrightnessReason);
        assertWithMessage("DisplayBrightnessState has different brightnessReason modifier")
                .that(stateBuilder.getBrightnessReason().getModifier())
                .isEqualTo(NO_MODIFIER);
        assertWithMessage("DisplayBrightnessState has different isSlowChange")
                .that(stateBuilder.isSlowChange())
                .isEqualTo(isSlowChange);
    }

    private static class TestInjector extends BrightnessMinModeModifier.Injector {

        private ContentObserver mObserver;

        @Override
        void registerMinModeSettingObserver(
                @NonNull ContentResolver cr, @NonNull ContentObserver observer) {
            mObserver = observer;
        }

        private void notifyMinModeChanged() {
            if (mObserver != null) {
                mObserver.dispatchChange(
                        /* selfChange= */ false,
                        Settings.Global.getUriFor(Settings.Global.MINMODE_ACTIVE));
            }
        }
    }
}
