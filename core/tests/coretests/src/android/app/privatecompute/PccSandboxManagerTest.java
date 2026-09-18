/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.app.privatecompute;

import static android.app.privatecompute.flags.Flags.FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import android.app.privatecompute.PccSandboxManager.Injector;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;
import java.util.Set;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class PccSandboxManagerTest {

    private static final long SECOND_TO_NS = TimeUnit.SECONDS.toNanos(1);

    @Rule(order = 0)
    public final MockitoRule mockito = MockitoJUnit.rule();

    @Rule(order = 1)
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private PccSandboxManager mPccSandboxManager;
    @Mock private Context mContext;
    @Mock private IPccSandboxManager mService;
    @Mock private Injector mInjector;

    @Before
    public void setUp() {
        mPccSandboxManager = new PccSandboxManager(mService, mContext, mInjector);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testWriteToAuditLog_withoutBatching_callsCorrectMethod() throws RemoteException {
        when(mInjector.auditModeBatchingEnabled()).thenReturn(false);
        when(mInjector.auditModeMaxBatchSize()).thenReturn(1);
        when(mInjector.auditModeFlushAgeNanos()).thenReturn(1L * SECOND_TO_NS);
        when(mInjector.clientTimestampNanos()).thenReturn(0L); // 0 ns

        mPccSandboxManager.writeToAuditLog(new PersistableBundle());

        verify(mService).writeToAuditLog(any(), any());
        verify(mService, never()).batchWriteToAuditLog(any(), any());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testWriteToAuditLog_withBatching_callsCorrectMethod() throws RemoteException {
        when(mInjector.auditModeBatchingEnabled()).thenReturn(true);
        when(mInjector.auditModeMaxBatchSize()).thenReturn(1);
        when(mInjector.auditModeFlushAgeNanos()).thenReturn(1L * SECOND_TO_NS);
        when(mInjector.clientTimestampNanos()).thenReturn(0L).thenReturn(1L); // 0 and 1 ns

        mPccSandboxManager.writeToAuditLog(new PersistableBundle());

        verify(mService, never()).writeToAuditLog(any(), any());
        verify(mService).batchWriteToAuditLog(any(), any());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testWriteToAuditLog_withBatching_batchesCorrectly() throws RemoteException {
        int maxBatchSize = 3;
        when(mInjector.auditModeBatchingEnabled()).thenReturn(true);
        when(mInjector.auditModeMaxBatchSize()).thenReturn(maxBatchSize);
        when(mInjector.auditModeFlushAgeNanos()).thenReturn(1L * SECOND_TO_NS);
        // Times are 0, 1, 2, 3, 4, 5 ns. No items are flushed due to being old.
        when(mInjector.clientTimestampNanos())
                .thenReturn(0L)
                .thenReturn(1L)
                .thenReturn(2L)
                .thenReturn(3L)
                .thenReturn(4L)
                .thenReturn(5L);

        // add 2 items, none are flushed.
        mPccSandboxManager.writeToAuditLog(new PersistableBundle());
        mPccSandboxManager.writeToAuditLog(new PersistableBundle());

        verify(mService, never()).batchWriteToAuditLog(any(), any());

        // add 3rd item, batch is full, so we flush.
        mPccSandboxManager.writeToAuditLog(new PersistableBundle());
        verify(mService, times(1)).batchWriteToAuditLog(any(), any());

        // add items 4-5, no flush.
        mPccSandboxManager.writeToAuditLog(new PersistableBundle());
        mPccSandboxManager.writeToAuditLog(new PersistableBundle());
        verify(mService, times(1)).batchWriteToAuditLog(any(), any());

        // add 6th item, batch is full, so we flush.
        mPccSandboxManager.writeToAuditLog(new PersistableBundle());
        verify(mService, times(2)).batchWriteToAuditLog(any(), any());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testWriteToAuditLog_withBatching_flushesWhenOldItemIsPresent()
            throws RemoteException {
        when(mInjector.auditModeBatchingEnabled()).thenReturn(true);
        when(mInjector.auditModeMaxBatchSize()).thenReturn(3);
        when(mInjector.auditModeFlushAgeNanos()).thenReturn(1L * SECOND_TO_NS);
        when(mInjector.clientTimestampNanos()).thenReturn(0L).thenReturn(2L * SECOND_TO_NS);

        mPccSandboxManager.writeToAuditLog(new PersistableBundle()); // add item at 0 sec
        verify(mService, never()).batchWriteToAuditLog(any(), any());

        mPccSandboxManager.writeToAuditLog(new PersistableBundle()); // add item at 2 sec, flush
        verify(mService).batchWriteToAuditLog(any(), any());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_PCC_FRAMEWORK_SUPPORT)
    public void testWriteToAuditLog_withBatching_isThreadSafe() throws Exception {
        int maxBatchSize = 10;
        int numThreads = 5;
        int logsPerThread = 20;
        int totalLogs = numThreads * logsPerThread;
        int expectedFlushes = totalLogs / maxBatchSize;
        final AtomicLong fakeClock = new AtomicLong(0);
        when(mInjector.auditModeBatchingEnabled()).thenReturn(true);
        when(mInjector.auditModeMaxBatchSize()).thenReturn(maxBatchSize);
        // Set a long flush time so only batch size triggers flushes.
        when(mInjector.auditModeFlushAgeNanos()).thenReturn(Long.MAX_VALUE);
        when(mInjector.clientTimestampNanos()).thenAnswer(inv -> fakeClock.getAndIncrement());
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        for (int i = 0; i < totalLogs; i++) {
            var unused = executor.submit(() -> {
                mPccSandboxManager.writeToAuditLog(new PersistableBundle());
            });
        }
        executor.shutdown();

        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        verify(mService, times(expectedFlushes)).batchWriteToAuditLog(any(), any());
    }
}
