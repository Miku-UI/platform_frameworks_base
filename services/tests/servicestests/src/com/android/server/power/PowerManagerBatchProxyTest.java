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

package com.android.server.power;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.os.PowerManagerInternal;
import android.os.test.TestLooper;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.server.am.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Unit tests for {@link PowerManagerBatchProxy}.
 * Build/Install/Run:
 * atest FrameworksServicesTests:PowerManagerBatchProxyTest
 * atest FrameworksServicesTestsRavenwood_ActivityManager:PowerManagerBatchProxyTest
 */
@SmallTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class PowerManagerBatchProxyTest {
    @Rule
    public final SetFlagsRule setFlagsRule = new SetFlagsRule();
    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();

    private static final int UID1 = 1001;
    private static final int UID2 = 1002;
    private static final int UID3 = 1003;
    private static final int UID4 = 1004;
    private static final int TEST_PROC_STATE = 5;

    private TestLooper mTestLooper;
    @Mock
    private PowerManagerInternal mPowerManagerInternal;
    private PowerManagerBatchProxy mProxy;

    @Before
    public void setUp() {
        mTestLooper = new TestLooper();
        mProxy = new PowerManagerBatchProxy(mPowerManagerInternal, mTestLooper.getLooper());
    }

    @Test
    @DisableFlags(Flags.FLAG_BATCH_POWER_MANAGER_CALLS)
    public void testBatching_FlagDisabled() {
        mProxy.startUidChanges();
        verify(mPowerManagerInternal).startUidChanges();

        mProxy.uidActive(UID1);
        verify(mPowerManagerInternal).uidActive(UID1);

        mProxy.uidIdle(UID2);
        verify(mPowerManagerInternal).uidIdle(UID2);

        mProxy.uidGone(UID3);
        verify(mPowerManagerInternal).uidGone(UID3);

        mProxy.updateUidProcState(UID4, TEST_PROC_STATE);
        verify(mPowerManagerInternal).updateUidProcState(UID4, TEST_PROC_STATE);

        mProxy.finishUidChanges();
        verify(mPowerManagerInternal).finishUidChanges();
    }

    @Test
    @EnableFlags(Flags.FLAG_BATCH_POWER_MANAGER_CALLS)
    public void testBatching() {
        mProxy.startUidChanges();
        mProxy.uidActive(UID1);
        mProxy.uidIdle(UID2);
        mProxy.uidGone(UID3);
        mProxy.updateUidProcState(UID4, TEST_PROC_STATE);

        // Verify nothing happened yet before finishUidChanges() method.
        mTestLooper.dispatchAll();
        verify(mPowerManagerInternal, never()).uidActive(UID1);

        // Process the message
        mProxy.finishUidChanges();
        mTestLooper.dispatchAll();

        InOrder inOrder = inOrder(mPowerManagerInternal);
        inOrder.verify(mPowerManagerInternal).startUidChanges();
        inOrder.verify(mPowerManagerInternal).uidActive(UID1);
        inOrder.verify(mPowerManagerInternal).uidIdle(UID2);
        inOrder.verify(mPowerManagerInternal).uidGone(UID3);
        inOrder.verify(mPowerManagerInternal).updateUidProcState(UID4, TEST_PROC_STATE);
        inOrder.verify(mPowerManagerInternal).finishUidChanges();
        verifyNoMoreInteractions(mPowerManagerInternal);
    }

    @Test
    @EnableFlags(Flags.FLAG_BATCH_POWER_MANAGER_CALLS)
    public void testEmptyBatch() {
        mProxy.startUidChanges();
        mProxy.finishUidChanges();
        mTestLooper.dispatchAll();

        InOrder inOrder = inOrder(mPowerManagerInternal);
        inOrder.verify(mPowerManagerInternal).startUidChanges();
        inOrder.verify(mPowerManagerInternal).finishUidChanges();
        verifyNoMoreInteractions(mPowerManagerInternal);
    }

    @Test
    @EnableFlags(Flags.FLAG_BATCH_POWER_MANAGER_CALLS)
    public void testMultipleBatches() {
        // First batch
        mProxy.startUidChanges();
        mProxy.uidActive(UID1);
        mProxy.finishUidChanges();
        mTestLooper.dispatchAll();

        // Second batch
        mProxy.startUidChanges();
        mProxy.uidIdle(UID2);
        mProxy.finishUidChanges();
        mTestLooper.dispatchAll();

        // Third batch
        mProxy.startUidChanges();
        mProxy.uidGone(UID3);
        mProxy.finishUidChanges();
        mTestLooper.dispatchAll();

        InOrder inOrder = inOrder(mPowerManagerInternal);
        inOrder.verify(mPowerManagerInternal).startUidChanges();
        inOrder.verify(mPowerManagerInternal).uidActive(UID1);
        inOrder.verify(mPowerManagerInternal).finishUidChanges();
        inOrder.verify(mPowerManagerInternal).startUidChanges();
        inOrder.verify(mPowerManagerInternal).uidIdle(UID2);
        inOrder.verify(mPowerManagerInternal).finishUidChanges();
        inOrder.verify(mPowerManagerInternal).startUidChanges();
        inOrder.verify(mPowerManagerInternal).uidGone(UID3);
        inOrder.verify(mPowerManagerInternal).finishUidChanges();
    }

    @Test
    @EnableFlags(Flags.FLAG_BATCH_POWER_MANAGER_CALLS)
    public void testBatchesAcrossHandlerDispatches() {
        // First batch
        mProxy.startUidChanges();
        mProxy.uidActive(UID1);
        mProxy.finishUidChanges();

        // Second batch
        mProxy.startUidChanges();
        mTestLooper.dispatchAll(); // Handles the first request during the second batch.
        mProxy.uidIdle(UID2);
        mProxy.finishUidChanges();
        mTestLooper.dispatchAll(); // Handles the second request after the second batch.

        InOrder inOrder = inOrder(mPowerManagerInternal);
        inOrder.verify(mPowerManagerInternal).startUidChanges();
        inOrder.verify(mPowerManagerInternal).uidActive(UID1);
        inOrder.verify(mPowerManagerInternal).finishUidChanges();
        inOrder.verify(mPowerManagerInternal).startUidChanges();
        inOrder.verify(mPowerManagerInternal).uidIdle(UID2);
        inOrder.verify(mPowerManagerInternal).finishUidChanges();
    }

    @Test
    @EnableFlags(Flags.FLAG_BATCH_POWER_MANAGER_CALLS)
    public void testImmediateFlush() {
        mProxy.uidActive(UID1);
        mProxy.uidIdle(UID2);
        mTestLooper.dispatchAll();

        verify(mPowerManagerInternal).uidActive(UID1);
        verify(mPowerManagerInternal).uidIdle(UID2);
        verifyNoMoreInteractions(mPowerManagerInternal);
    }
}
