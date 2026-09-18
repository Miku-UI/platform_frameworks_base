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

package com.android.server.display.brightness.clamper;

import static com.android.server.display.DisplayBrightnessState.CUSTOM_ANIMATION_RATE_NOT_SET;
import static com.android.server.display.brightness.clamper.ClamperTestUtilsKt.createDisplayDeviceData;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.hardware.display.BrightnessInfo;
import android.hardware.display.DisplayManagerInternal;
import android.os.IBinder;
import android.os.PowerManager;
import android.testing.TestableContext;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.display.BrightnessSynchronizer;
import com.android.server.display.DisplayBrightnessState;
import com.android.server.display.DisplayDeviceConfig;
import com.android.server.display.plugin.PluginManager;
import com.android.server.display.plugin.types.MaxBrightnessCapOverride;
import com.android.server.testutils.TestHandler;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Objects;

public class BrightnessPluginModifierTest {

    private static final float BRIGHTNESS_CAP = 0.5f;
    private static final String DISPLAY_ID = "displayId";
    private static final int NO_MODIFIER = 0;

    @Mock
    private BrightnessClamperController.ClamperChangeListener mMockClamperChangeListener;
    @Mock
    private DisplayManagerInternal.DisplayPowerRequest mMockRequest;
    @Mock
    private PluginManager mPluginManager;
    @Mock
    private DisplayDeviceConfig mMockDisplayDeviceConfig;
    @Mock
    private IBinder mMockDisplayBinder;

    private final TestHandler mTestHandler = new TestHandler(null);
    private TestInjector mInjector;
    private BrightnessPluginModifier mModifier;
    private AutoCloseable mMockitoSession;

    @Rule
    public final TestableContext mContext =
            new TestableContext(InstrumentationRegistry.getInstrumentation().getContext());

    @Before
    public void setUp() {
        mMockitoSession = MockitoAnnotations.openMocks(this);
        mInjector = new TestInjector(mPluginManager);
        BrightnessClamperController.DisplayDeviceData dummyData = createDisplayDeviceData(
                mMockDisplayDeviceConfig, mMockDisplayBinder, DISPLAY_ID);
        mModifier = new BrightnessPluginModifier(mTestHandler, mMockClamperChangeListener,
                mInjector, dummyData);
        mTestHandler.flush();
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
    }

    @Test
    public void testPluginUpdateClampsBrightness() {
        // Simulate a plugin update
        mInjector.notifyPluginChanged(
                new MaxBrightnessCapOverride(BRIGHTNESS_CAP, CUSTOM_ANIMATION_RATE_NOT_SET));
        mTestHandler.flush();

        // Verify the clamper was notified
        verify(mMockClamperChangeListener).onChanged();

        // Verify the modifier clamps the brightness correctly
        assertModifierState(BRIGHTNESS_CAP, CUSTOM_ANIMATION_RATE_NOT_SET, true);
    }

    @Test
    public void testPluginUpdateToDefaultDoesNotClamp() {
        // Set an initial cap
        mInjector.notifyPluginChanged(
                new MaxBrightnessCapOverride(BRIGHTNESS_CAP, 1.0f));
        mTestHandler.flush();
        clearInvocations(mMockClamperChangeListener);

        // Simulate a plugin update to the default (no cap)
        mInjector.notifyPluginChanged(
                new MaxBrightnessCapOverride(PowerManager.BRIGHTNESS_MAX,
                        CUSTOM_ANIMATION_RATE_NOT_SET));
        mTestHandler.flush();

        // Verify the clamper was notified
        verify(mMockClamperChangeListener).onChanged();

        // Verify the modifier does not clamp the brightness
        assertModifierState(PowerManager.BRIGHTNESS_MAX, CUSTOM_ANIMATION_RATE_NOT_SET, false);
    }

    @Test
    public void testNoOpUpdateDoesNotNotify() {
        // Set an initial cap
        mInjector.notifyPluginChanged(
                new MaxBrightnessCapOverride(BRIGHTNESS_CAP, CUSTOM_ANIMATION_RATE_NOT_SET));
        mTestHandler.flush();
        clearInvocations(mMockClamperChangeListener);

        // Simulate another plugin update with the same value
        mInjector.notifyPluginChanged(
                new MaxBrightnessCapOverride(BRIGHTNESS_CAP, CUSTOM_ANIMATION_RATE_NOT_SET));
        mTestHandler.flush();

        // Verify the clamper was NOT notified
        verify(mMockClamperChangeListener, never()).onChanged();
    }

    @Test
    public void testDoesNotOverrideLowerCapFromOtherModifier() {
        // Another Modifier has set a lower state
        BrightnessClamperController.ModifiersAggregatedState aggregatedState =
                new BrightnessClamperController.ModifiersAggregatedState();
        aggregatedState.mMaxBrightness = 0.3f;
        aggregatedState.mMaxBrightnessReason = BrightnessInfo.BRIGHTNESS_MAX_REASON_THERMAL;

        mInjector.notifyPluginChanged(
                new MaxBrightnessCapOverride(BRIGHTNESS_CAP, CUSTOM_ANIMATION_RATE_NOT_SET));
        mTestHandler.flush();
        mModifier.applyStateChange(aggregatedState);

        // Assert that the lower cap from the other modifier is respected
        assertThat(aggregatedState.mMaxBrightness).isEqualTo(0.3f);
        assertThat(aggregatedState.mMaxBrightnessReason)
                .isEqualTo(BrightnessInfo.BRIGHTNESS_MAX_REASON_THERMAL);
    }

    @Test
    public void testListenerIsRegisteredOnInitialization() {
        // Assert that the listener was registered with the correct ID during setUp
        assertThat(mInjector.getRegisteredUniqueId()).isEqualTo(DISPLAY_ID);
    }

    @Test
    public void testStopUnregistersListener() {
        // Pre-condition: listener should be registered
        assertThat(mInjector.getRegisteredUniqueId()).isNotNull();

        // Act
        mModifier.stop();

        // Assert
        assertThat(mInjector.getRegisteredUniqueId()).isNull();
    }

    private void assertModifierState(float maxBrightness, float animationRate, boolean isActive) {
        BrightnessClamperController.ModifiersAggregatedState
                modifierState = new BrightnessClamperController.ModifiersAggregatedState();
        DisplayBrightnessState.Builder stateBuilder = DisplayBrightnessState.builder();
        stateBuilder.setMaxBrightness(PowerManager.BRIGHTNESS_MAX);

        int maxBrightnessReason =
                isActive
                        ? BrightnessInfo.BRIGHTNESS_MAX_REASON_PLUGIN
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
        assertWithMessage("DisplayBrightnessState has different customAnimationRate")
                .that(stateBuilder.getCustomAnimationRate()).isEqualTo(animationRate);
        assertWithMessage("DisplayBrightnessState has different brightnessMaxReason")
                .that(stateBuilder.getBrightnessMaxReason())
                .isEqualTo(maxBrightnessReason);
        assertWithMessage("DisplayBrightnessState has different brightnessReason modifier")
                .that(stateBuilder.getBrightnessReason().getModifier())
                .isEqualTo(NO_MODIFIER);
    }

    private static class TestInjector extends BrightnessPluginModifier.Injector {
        private PluginManager.PluginChangeListener<MaxBrightnessCapOverride> mListener;
        private String mRegisteredUniqueId;

        TestInjector(PluginManager pluginManager) {
            super(pluginManager);
        }

        @Override
        void registerMaxBrightnessCapOverrideListener(String uniqueDisplayId,
                PluginManager.PluginChangeListener<MaxBrightnessCapOverride> listener) {
            mRegisteredUniqueId = uniqueDisplayId;
            mListener = listener;
        }

        @Override
        void unregisterMaxBrightnessCapOverrideListener(String uniqueDisplayId,
                PluginManager.PluginChangeListener<MaxBrightnessCapOverride> listener) {
            if (Objects.equals(mRegisteredUniqueId, uniqueDisplayId)) {
                mRegisteredUniqueId = null;
                mListener = null;
            }
        }

        void notifyPluginChanged(MaxBrightnessCapOverride value) {
            if (mListener != null) {
                mListener.onChanged(value);
            }
        }

        String getRegisteredUniqueId() {
            return mRegisteredUniqueId;
        }
    }
}
