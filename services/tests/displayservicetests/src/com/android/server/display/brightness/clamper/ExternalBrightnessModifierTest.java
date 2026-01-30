/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.display.brightness.clamper;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.hardware.display.BrightnessInfo;
import android.testing.TestableContext;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.display.BrightnessSynchronizer;
import com.android.server.display.DisplayBrightnessState;
import com.android.server.testutils.TestHandler;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ExternalBrightnessModifierTest {

    @Mock private BrightnessClamperController.ClamperChangeListener mMockClamperChangeListener;

    @Rule
    public final TestableContext mContext =
            new TestableContext(InstrumentationRegistry.getInstrumentation().getContext());

    private final TestHandler mTestHandler = new TestHandler(null);
    private ExternalBrightnessModifier mModifier;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mModifier = new ExternalBrightnessModifier(mTestHandler, mMockClamperChangeListener);
        mTestHandler.flush();
    }

    @Test
    public void testSetBrightnessCap_lowerThanCurrentBrightness_appliesCap() {
        mModifier.setBrightnessCap(/* cap= */ 0.5f, BrightnessInfo.BRIGHTNESS_MAX_REASON_MODES);
        applyAndAssert(
                /* currentBrightness= */ 0.6f,
                /* currentMaxBrightness= */ 0.9f,
                /* currentSlowChange= */ true,
                /* expectedBrightness= */ 0.5f,
                /* expectedMaxBrightness= */ 0.5f,
                /* expectedBrightnessReason= */ BrightnessInfo.BRIGHTNESS_MAX_REASON_MODES,
                /* expectedIsSlowChange= */ false);
        verify(mMockClamperChangeListener).onChanged();
    }

    @Test
    public void testSetBrightnessCap_higherThanCurrentBrightness_doesNotApplyCap() {
        mModifier.setBrightnessCap(/* cap= */ 0.5f, BrightnessInfo.BRIGHTNESS_MAX_REASON_MODES);
        applyAndAssert(
                /* currentBrightness= */ 0.3f,
                /* currentMaxBrightness= */ 0.4f,
                /* currentSlowChange= */ true,
                /* expectedBrightness= */ 0.3f,
                /* expectedMaxBrightness= */ 0.4f,
                /* expectedBrightnessReason= */ BrightnessInfo.BRIGHTNESS_MAX_REASON_NONE,
                /* expectedIsSlowChange= */ true);
        verify(mMockClamperChangeListener).onChanged();
    }

    @Test
    public void testSetBrightnessCap_appliesLowestCap() {
        mModifier.setBrightnessCap(/* cap= */ 0.5f, BrightnessInfo.BRIGHTNESS_MAX_REASON_MODES);
        applyAndAssert(
                /* currentBrightness= */ 1f,
                /* currentMaxBrightness= */ 1f,
                /* currentSlowChange= */ true,
                /* expectedBrightness= */ 0.5f,
                /* expectedMaxBrightness= */ 0.5f,
                /* expectedBrightnessReason= */ BrightnessInfo.BRIGHTNESS_MAX_REASON_MODES,
                /* expectedIsSlowChange= */ false);
        verify(mMockClamperChangeListener).onChanged();
        clearInvocations(mMockClamperChangeListener);

        mModifier.setBrightnessCap(/* cap= */ 0.7f, BrightnessInfo.BRIGHTNESS_MAX_REASON_THERMAL);
        applyAndAssert(
                /* currentBrightness= */ 1f,
                /* currentMaxBrightness= */ 1f,
                /* currentSlowChange= */ true,
                /* expectedBrightness= */ 0.5f,
                /* expectedMaxBrightness= */ 0.5f,
                /* expectedBrightnessReason= */ BrightnessInfo.BRIGHTNESS_MAX_REASON_MODES,
                /* expectedIsSlowChange= */ true);
        verify(mMockClamperChangeListener, never()).onChanged();

        mModifier.setBrightnessCap(/* cap= */ 0.45f, BrightnessInfo.BRIGHTNESS_MAX_REASON_POWER_IC);
        applyAndAssert(
                /* currentBrightness= */ 1f,
                /* currentMaxBrightness= */ 1f,
                /* currentSlowChange= */ true,
                /* expectedBrightness= */ 0.45f,
                /* expectedMaxBrightness= */ 0.45f,
                /* expectedBrightnessReason= */ BrightnessInfo.BRIGHTNESS_MAX_REASON_POWER_IC,
                /* expectedIsSlowChange= */ true);
        verify(mMockClamperChangeListener).onChanged();
    }

    @Test
    public void testSetBrightnessCap_removesCap() {
        mModifier.setBrightnessCap(/* cap= */ 0.5f, BrightnessInfo.BRIGHTNESS_MAX_REASON_MODES);
        applyAndAssert(
                /* currentBrightness= */ 0.6f,
                /* currentMaxBrightness= */ 0.9f,
                /* currentSlowChange= */ true,
                /* expectedBrightness= */ 0.5f,
                /* expectedMaxBrightness= */ 0.5f,
                /* expectedBrightnessReason= */ BrightnessInfo.BRIGHTNESS_MAX_REASON_MODES,
                /* expectedIsSlowChange= */ false);
        verify(mMockClamperChangeListener).onChanged();
        clearInvocations(mMockClamperChangeListener);

        // Apply cap of 1f to check if it's removed or not. Wrong values of current / max
        // brightness are intentionally supplied here to ensure that cap is not applied
        mModifier.setBrightnessCap(/* cap= */ 1f, BrightnessInfo.BRIGHTNESS_MAX_REASON_MODES);
        applyAndAssert(
                /* currentBrightness= */ 1.1f,
                /* currentMaxBrightness= */ 1.1f,
                /* currentSlowChange= */ true,
                /* expectedBrightness= */ 1.1f,
                /* expectedMaxBrightness= */ 1.1f,
                /* expectedBrightnessReason= */ BrightnessInfo.BRIGHTNESS_MAX_REASON_NONE,
                /* expectedIsSlowChange= */ true);
        verify(mMockClamperChangeListener).onChanged();
    }

    @Test
    public void testSetBrightnessCap_badValueIgnored() {
        mModifier.setBrightnessCap(/* cap= */ 0.5f, BrightnessInfo.BRIGHTNESS_MAX_REASON_MODES);
        applyAndAssert(
                /* currentBrightness= */ 1f,
                /* currentMaxBrightness= */ 1f,
                /* currentSlowChange= */ true,
                /* expectedBrightness= */ 0.5f,
                /* expectedMaxBrightness= */ 0.5f,
                /* expectedBrightnessReason= */ BrightnessInfo.BRIGHTNESS_MAX_REASON_MODES,
                /* expectedIsSlowChange= */ false);
        verify(mMockClamperChangeListener).onChanged();
        clearInvocations(mMockClamperChangeListener);

        mModifier.setBrightnessCap(/* cap= */ 7f, BrightnessInfo.BRIGHTNESS_MAX_REASON_THERMAL);
        applyAndAssert(
                /* currentBrightness= */ 1f,
                /* currentMaxBrightness= */ 1f,
                /* currentSlowChange= */ true,
                /* expectedBrightness= */ 0.5f,
                /* expectedMaxBrightness= */ 0.5f,
                /* expectedBrightnessReason= */ BrightnessInfo.BRIGHTNESS_MAX_REASON_MODES,
                /* expectedIsSlowChange= */ true);
        mModifier.setBrightnessCap(/* cap= */ -1f, BrightnessInfo.BRIGHTNESS_MAX_REASON_POWER_IC);
        applyAndAssert(
                /* currentBrightness= */ 1f,
                /* currentMaxBrightness= */ 1f,
                /* currentSlowChange= */ true,
                /* expectedBrightness= */ 0.5f,
                /* expectedMaxBrightness= */ 0.5f,
                /* expectedBrightnessReason= */ BrightnessInfo.BRIGHTNESS_MAX_REASON_MODES,
                /* expectedIsSlowChange= */ true);
        verify(mMockClamperChangeListener, never()).onChanged();
    }

    private void applyAndAssert(
            float currentBrightness,
            float currentMaxBrightness,
            boolean currentSlowChange,
            float expectedBrightness,
            float expectedMaxBrightness,
            int expectedBrightnessReason,
            boolean expectedIsSlowChange) {
        mTestHandler.flush();

        BrightnessClamperController.ModifiersAggregatedState modifierState =
                new BrightnessClamperController.ModifiersAggregatedState();
        modifierState.mMaxBrightness = currentMaxBrightness;
        mModifier.applyStateChange(modifierState);
        assertThat(modifierState.mMaxBrightness).isEqualTo(expectedMaxBrightness);
        assertThat(modifierState.mMaxBrightnessReason).isEqualTo(expectedBrightnessReason);

        DisplayBrightnessState.Builder stateBuilder = DisplayBrightnessState.builder();
        stateBuilder.setBrightness(currentBrightness);
        stateBuilder.setMaxBrightness(currentMaxBrightness);
        stateBuilder.setIsSlowChange(currentSlowChange);
        mModifier.apply(/* request= */ null, stateBuilder);

        assertThat(stateBuilder.getMaxBrightness())
                .isWithin(BrightnessSynchronizer.EPSILON)
                .of(expectedMaxBrightness);
        assertThat(stateBuilder.getBrightness())
                .isWithin(BrightnessSynchronizer.EPSILON)
                .of(expectedBrightness);
        assertThat(stateBuilder.getBrightnessMaxReason()).isEqualTo(expectedBrightnessReason);
        assertThat(stateBuilder.isSlowChange()).isEqualTo(expectedIsSlowChange);
    }
}
