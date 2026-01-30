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

package com.android.server.vibrator;

import static com.google.common.truth.Truth.assertThat;

import android.hardware.vibrator.IVibrator;
import android.os.VibratorInfo;
import android.os.test.TestLooper;

import com.android.server.vibrator.VintfHalVibratorManager.LegacyHalVibratorManager;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Test class for {@link LegacyHalVibratorManager}. */
public class LegacyHalVibratorManagerTest {
    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Mock HalVibratorManager.Callbacks mHalCallbackMock;
    @Mock HalVibrator.Callbacks mHalVibratorCallbackMock;

    private final TestLooper mTestLooper = new TestLooper();
    private final HalVibratorManagerHelper mHelper =
            new HalVibratorManagerHelper(mTestLooper.getLooper());

    @Test
    public void init_returnsAllFalseExceptVibratorIds() {
        HalVibratorManager manager = mHelper.newLegacyVibratorManager();
        manager.init(mHalCallbackMock, mHalVibratorCallbackMock);

        assertThat(manager.getVibratorIds()).asList()
                .containsExactly(VintfHalVibratorManager.DEFAULT_VIBRATOR_ID).inOrder();
        assertThat(manager.getCapabilities()).isEqualTo(0);
        assertThat(manager.prepareSynced(new int[] { 1 })).isFalse();
        assertThat(manager.triggerSynced(1)).isFalse();
        assertThat(manager.cancelSynced()).isFalse();
        assertThat(manager.startSession(1, new int[] { 2 })).isFalse();
        assertThat(manager.endSession(1, false)).isFalse();
    }

    @Test
    public void init_initializesVibrators() {
        HalVibratorManager manager = mHelper.newLegacyVibratorManager();
        manager.init(mHalCallbackMock, mHalVibratorCallbackMock);

        int defaultVibratorId = VintfHalVibratorManager.DEFAULT_VIBRATOR_ID;
        assertThat(mHelper.getVibratorHelper(defaultVibratorId).isInitialized()).isTrue();
    }

    @Test
    public void onSystemReady_triggersDefaultVibratorOnSystemReady() {
        int defaultVibratorId = VintfHalVibratorManager.DEFAULT_VIBRATOR_ID;
        HalVibratorManager manager = mHelper.newLegacyVibratorManager();
        mHelper.getVibratorHelper(defaultVibratorId)
                .setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        mHelper.getVibratorHelper(defaultVibratorId).setLoadInfoToFail();
        manager.init(mHalCallbackMock, mHalVibratorCallbackMock);

        assertThat(manager.getVibrator(defaultVibratorId).getInfo())
                .isEqualTo(new VibratorInfo.Builder(defaultVibratorId).build());

        manager.onSystemReady();

        assertThat(manager.getVibrator(defaultVibratorId).getInfo().getCapabilities())
                .isEqualTo(IVibrator.CAP_EXTERNAL_CONTROL);
    }

    @Test
    public void getVibrator_returnsVibratorOnlyForDefaultValidId() {
        int defaultVibratorId = VintfHalVibratorManager.DEFAULT_VIBRATOR_ID;
        HalVibratorManager manager = mHelper.newLegacyVibratorManager();

        assertThat(manager.getVibrator(defaultVibratorId - 1)).isNull();
        assertThat(manager.getVibrator(defaultVibratorId)).isNotNull();
        assertThat(manager.getVibrator(defaultVibratorId).getInfo().getId())
                .isEqualTo(defaultVibratorId);
        assertThat(manager.getVibrator(defaultVibratorId + 1)).isNull();
    }
}
