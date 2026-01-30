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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.hardware.vibrator.IVibrator;
import android.hardware.vibrator.IVibratorManager;
import android.os.test.TestLooper;
import android.os.vibrator.Flags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Base test class for {@link HalVibratorManager} implementations. */
public abstract class HalVibratorManagerTestCase {
    @Rule public MockitoRule rule = MockitoJUnit.rule();
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock HalVibratorManager.Callbacks mHalCallbackMock;
    @Mock HalVibrator.Callbacks mHalVibratorCallbackMock;

    final TestLooper mTestLooper = new TestLooper();
    final HalVibratorManagerHelper mHelper = new HalVibratorManagerHelper(mTestLooper.getLooper());

    abstract HalVibratorManager newVibratorManager();

    HalVibratorManager newInitializedVibratorManager() {
        HalVibratorManager manager = newVibratorManager();
        manager.init(mHalCallbackMock, mHalVibratorCallbackMock);
        manager.onSystemReady();
        return manager;
    }

    @Test
    public void init_initializesCapabilitiesAndVibratorIds() {
        mHelper.setCapabilities(IVibratorManager.CAP_SYNC);
        mHelper.setVibratorIds(new int[] {1, 2});
        HalVibratorManager manager = newVibratorManager();
        manager.init(mHalCallbackMock, mHalVibratorCallbackMock);

        assertThat(mHelper.getConnectCount()).isEqualTo(1);
        assertThat(manager.getCapabilities()).isEqualTo(IVibratorManager.CAP_SYNC);
        assertThat(manager.getVibratorIds()).isEqualTo(new int[] {1, 2});
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void init_initializesHalAndClearSyncedAndSessions() {
        mHelper.setCapabilities(IVibratorManager.CAP_SYNC, IVibratorManager.CAP_START_SESSIONS);
        mHelper.setVibratorIds(new int[] {1, 2});
        HalVibratorManager manager = newVibratorManager();
        manager.init(mHalCallbackMock, mHalVibratorCallbackMock);

        assertThat(mHelper.getConnectCount()).isEqualTo(1);
        assertThat(mHelper.getCancelSyncedCount()).isEqualTo(1);
        assertThat(mHelper.getClearSessionsCount()).isEqualTo(1);
    }

    @Test
    public void init_withNullVibratorIds_returnsEmptyArray() {
        mHelper.setVibratorIds(null);
        HalVibratorManager manager = newVibratorManager();
        manager.init(mHalCallbackMock, mHalVibratorCallbackMock);
        assertThat(manager.getVibratorIds()).isEmpty();
    }

    @Test
    public void init_initializesAllVibrators() {
        mHelper.setVibratorIds(new int[] {1, 2});
        HalVibratorManager manager = newVibratorManager();
        manager.init(mHalCallbackMock, mHalVibratorCallbackMock);

        assertThat(mHelper.getVibratorHelper(1).isInitialized()).isTrue();
        assertThat(mHelper.getVibratorHelper(2).isInitialized()).isTrue();
    }

    @Test
    public void onSystemReady_triggersAllVibratorsOnSystemReady() {
        mHelper.setVibratorIds(new int[] {1, 2});
        mHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        mHelper.getVibratorHelper(2).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        mHelper.getVibratorHelper(2).setLoadInfoToFail();
        HalVibratorManager manager = newVibratorManager();
        manager.init(mHalCallbackMock, mHalVibratorCallbackMock);

        assertThat(manager.getVibrator(1).getInfo().getId()).isEqualTo(1);
        assertThat(manager.getVibrator(2).getInfo().getId()).isEqualTo(2);

        manager.onSystemReady();

        // Capabilities from vibrator 2 reloaded after failure.
        assertThat(manager.getVibrator(1).getInfo().getCapabilities())
                .isEqualTo(IVibrator.CAP_EXTERNAL_CONTROL);
        assertThat(manager.getVibrator(2).getInfo().getCapabilities())
                .isEqualTo(IVibrator.CAP_AMPLITUDE_CONTROL);
    }

    @Test
    public void hasCapability_checksAllFlagBits() {
        mHelper.setCapabilities(IVibratorManager.CAP_SYNC, IVibratorManager.CAP_START_SESSIONS);
        HalVibratorManager manager = newInitializedVibratorManager();

        assertThat(manager.hasCapability(IVibratorManager.CAP_SYNC)).isTrue();
        assertThat(manager.hasCapability(
                IVibratorManager.CAP_SYNC | IVibratorManager.CAP_START_SESSIONS)).isTrue();
        assertThat(manager.hasCapability(
                IVibratorManager.CAP_SYNC | IVibratorManager.CAP_PREPARE_ON)).isFalse();
        assertThat(manager.hasCapability(IVibratorManager.CAP_TRIGGER_CALLBACK)).isFalse();
    }

    @Test
    public void getVibrator_validVibratorId_returnsValidVibrators() {
        mHelper.setVibratorIds(new int[] {1, 2});
        HalVibratorManager manager = newInitializedVibratorManager();
        assertThat(manager.getVibrator(1)).isNotNull();
        assertThat(manager.getVibrator(1).getInfo().getId()).isEqualTo(1);
        assertThat(manager.getVibrator(2)).isNotNull();
        assertThat(manager.getVibrator(2).getInfo().getId()).isEqualTo(2);
    }

    @Test
    public void getVibrator_beforeInit_returnsNull() {
        mHelper.setVibratorIds(new int[] {1, 2});
        HalVibratorManager manager = newVibratorManager();
        assertThat(manager.getVibrator(1)).isNull();
        assertThat(manager.getVibrator(2)).isNull();
    }

    @Test
    public void getVibrator_badVibratorId_returnsNull() {
        mHelper.setVibratorIds(new int[] {1, 2});
        HalVibratorManager manager = newInitializedVibratorManager();
        assertThat(manager.getVibrator(3)).isNull();
        assertThat(manager.getVibrator(-1)).isNull();
    }

    @Test
    public void prepareSynced_withCapabilityAndValidVibrators_returnsTrue() {
        mHelper.setCapabilities(IVibratorManager.CAP_SYNC);
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();

        assertThat(manager.prepareSynced(new int[] {1})).isTrue();
        assertThat(manager.prepareSynced(new int[] {1, 3})).isTrue();
        assertThat(manager.prepareSynced(new int[] {1, 2, 3})).isTrue();
        assertThat(mHelper.getPrepareSyncedCount()).isEqualTo(3);
    }

    @Test
    public void prepareSynced_withCapabilityAndBadVibrators_returnsFalse() {
        mHelper.setCapabilities(IVibratorManager.CAP_SYNC);
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();

        assertThat(manager.prepareSynced(new int[0])).isFalse();
        assertThat(manager.prepareSynced(new int[] {4})).isFalse();
        assertThat(manager.prepareSynced(new int[] {1, 4})).isFalse();
    }

    @Test
    public void prepareSynced_withoutCapability_returnsFalse() {
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();

        assertThat(manager.prepareSynced(new int[] {1})).isFalse();
        assertThat(manager.prepareSynced(new int[] {1, 3})).isFalse();
        assertThat(manager.prepareSynced(new int[] {1, 2, 3})).isFalse();
    }

    @Test
    public void prepareSynced_failure_returnsFalse() {
        mHelper.setCapabilities(IVibratorManager.CAP_SYNC);
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        mHelper.setPrepareSyncedToFail();
        HalVibratorManager manager = newInitializedVibratorManager();

        assertThat(manager.prepareSynced(new int[] {1})).isFalse();
        assertThat(manager.prepareSynced(new int[] {1, 3})).isFalse();
        assertThat(manager.prepareSynced(new int[] {1, 2, 3})).isFalse();
    }

    @Test
    public void triggerSynced_withCapability_returnsTrue() {
        mHelper.setCapabilities(IVibratorManager.CAP_SYNC);
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();
        assertThat(manager.triggerSynced(/* vibrationId= */ 1)).isTrue();
        assertThat(mHelper.getTriggerSyncedCount()).isEqualTo(1);
    }

    @Test
    public void triggerSynced_withoutCapability_returnsFalse() {
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();
        assertThat(manager.triggerSynced(/* vibrationId= */ 1)).isFalse();
    }

    @Test
    public void triggerSynced_failure_returnsFalse() {
        mHelper.setCapabilities(IVibratorManager.CAP_SYNC);
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        mHelper.setTriggerSyncedToFail();
        HalVibratorManager manager = newInitializedVibratorManager();
        assertThat(manager.triggerSynced(/* vibrationId= */ 1)).isFalse();
    }

    @Test
    public void triggerSynced_triggerCallback_returnsVibrationId() {
        mHelper.setCapabilities(IVibratorManager.CAP_SYNC, IVibratorManager.CAP_TRIGGER_CALLBACK);
        mHelper.setVibratorIds(new int[] {1, 2});
        HalVibratorManager manager = newInitializedVibratorManager();

        long vibrationId = 1;
        assertThat(manager.triggerSynced(vibrationId)).isTrue();

        mHelper.endLastSyncedVibration();
        mTestLooper.dispatchAll();

        verify(mHalCallbackMock).onSyncedVibrationComplete(eq(vibrationId));
    }

    @Test
    public void cancelSynced_withCapability_returnsTrue() {
        mHelper.setCapabilities(IVibratorManager.CAP_SYNC);
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();
        assertThat(manager.cancelSynced()).isTrue();
    }

    @Test
    public void cancelSynced_withoutCapability_returnsFalse() {
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();
        assertThat(manager.cancelSynced()).isFalse();
    }

    @Test
    public void startSession_withCapabilityAndValidVibrators_returnsTrue() {
        mHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();

        assertThat(manager.startSession(/* sessionId= */ 1, new int[] {1})).isTrue();
        assertThat(manager.startSession(/* sessionId= */ 2, new int[] {1, 3})).isTrue();
        assertThat(manager.startSession(/* sessionId= */ 3, new int[] {1, 2, 3})).isTrue();
        assertThat(mHelper.getStartSessionCount()).isEqualTo(3);
    }

    @Test
    public void startSession_withCapabilityAndBadVibrators_returnsFalse() {
        mHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();

        assertThat(manager.startSession(/* sessionId= */ 1, new int[] {4})).isFalse();
        assertThat(manager.startSession(/* sessionId= */ 2, new int[] {1, 5})).isFalse();
    }

    @Test
    public void startSession_withoutCapability_returnsFalse() {
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();

        assertThat(manager.startSession(/* sessionId= */ 1, new int[] {1})).isFalse();
        assertThat(manager.startSession(/* sessionId= */ 2, new int[] {1, 3})).isFalse();
        assertThat(manager.startSession(/* sessionId= */ 3, new int[] {1, 2, 3})).isFalse();
    }

    @Test
    public void startSession_failure_returnsFalse() {
        mHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        mHelper.setStartSessionToFail();
        HalVibratorManager manager = newInitializedVibratorManager();

        assertThat(manager.startSession(/* sessionId= */ 1, new int[] {1})).isFalse();
        assertThat(manager.startSession(/* sessionId= */ 2, new int[] {1, 3})).isFalse();
        assertThat(manager.startSession(/* sessionId= */ 3, new int[] {1, 2, 3})).isFalse();
    }

    @Test
    public void endSession_withCapability_returnsTrue() {
        mHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();

        long sessionId = 1;
        assertThat(manager.startSession(sessionId, new int[] { 1 })).isTrue();
        assertThat(manager.endSession(sessionId, /* shouldAbort= */ false)).isTrue();
        assertThat(manager.endSession(sessionId, /* shouldAbort= */ true)).isTrue();
    }

    @Test
    public void endSession_withoutCapability_returnsFalse() {
        mHelper.setVibratorIds(new int[] {1, 2, 3});
        HalVibratorManager manager = newInitializedVibratorManager();

        assertThat(manager.endSession(/* sessionId= */ 1, /* shouldAbort= */ true)).isFalse();
        assertThat(manager.endSession(/* sessionId= */ 2, /* shouldAbort= */ false)).isFalse();
    }

    @Test
    public void endSession_returnsSessionId() {
        mHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHelper.setVibratorIds(new int[] {1, 2});
        HalVibratorManager manager = newInitializedVibratorManager();

        long sessionId = 1;
        assertThat(manager.startSession(sessionId, new int[] { 1 })).isTrue();

        manager.endSession(sessionId, /* shouldAbort= */ false);
        mTestLooper.dispatchAll();

        verify(mHalCallbackMock).onVibrationSessionComplete(eq(sessionId));
    }

    @Test
    public void abortSession_returnsSessionId() {
        mHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHelper.setVibratorIds(new int[] {1, 2});
        HalVibratorManager manager = newInitializedVibratorManager();

        long sessionId = 1;
        assertThat(manager.startSession(sessionId, new int[] { 1 })).isTrue();

        manager.endSession(sessionId, /* shouldAbort= */ true);
        mTestLooper.dispatchAll();

        verify(mHalCallbackMock).onVibrationSessionComplete(eq(sessionId));
    }

    @Test
    public void endSessionFromHal_returnsSessionId() {
        mHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHelper.setVibratorIds(new int[] {1, 2});
        HalVibratorManager manager = newInitializedVibratorManager();

        long sessionId = 1;
        assertThat(manager.startSession(sessionId, new int[] { 1 })).isTrue();

        mHelper.endLastSessionAbruptly();
        mTestLooper.dispatchAll();

        verify(mHalCallbackMock).onVibrationSessionComplete(eq(sessionId));
    }
}
