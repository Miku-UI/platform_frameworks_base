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

package com.android.server.ondeviceintelligence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.FgThread;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class OnDeviceIntelligenceManagerServiceTest {

    @Mock Context mMockContext;
    @Mock ActivityManager mMockActivityManager;
    @Mock Resources mMockResources;
    @Mock PackageManager mMockPackageManager;
    @Mock ServiceInfo mMockServiceInfo;

    private OnDeviceIntelligenceManagerService mService;
    private ActivityManager.OnUidImportanceListener mUidImportanceListener;

    private static final int TEST_UID_1 = 12345;
    private static final int TEST_UID_2 = 54321;
    private static final String TEST_SERVICE_NAME = "com.test.package/.TestService";
    private static final ComponentName TEST_COMPONENT_NAME =
            ComponentName.unflattenFromString(TEST_SERVICE_NAME);

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mMockContext.getSystemService(ActivityManager.class)).thenReturn(mMockActivityManager);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockContext.getUser()).thenReturn(UserHandle.SYSTEM);
        when(mMockContext.bindServiceAsUser(any(), any(), anyInt(), any(UserHandle.class)))
                .thenReturn(true);
        when(mMockContext.createContextAsUser(any(UserHandle.class), anyInt()))
                .thenReturn(mMockContext);
        when(mMockResources.getString(
                        com.android.internal.R.string
                                .config_defaultOnDeviceSandboxedInferenceService))
                .thenReturn(TEST_SERVICE_NAME);
        when(mMockResources.getString(
                        com.android.internal.R.string.config_defaultOnDeviceIntelligenceService))
                .thenReturn(TEST_SERVICE_NAME);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.getServiceInfo(any(), anyInt())).thenReturn(mMockServiceInfo);
        mMockServiceInfo.permission =
                Manifest.permission.BIND_ON_DEVICE_SANDBOXED_INFERENCE_SERVICE;
        mMockServiceInfo.flags = ServiceInfo.FLAG_ISOLATED_PROCESS;

        mService = new OnDeviceIntelligenceManagerService(mMockContext);
        mService.ensureRemoteInferenceServiceInitialized(true);

        mService.mIsServiceEnabled = true;
        mService.mActivityManager = mMockActivityManager;

        mService.onBootPhase(OnDeviceIntelligenceManagerService.PHASE_SYSTEM_SERVICES_READY);

        ArgumentCaptor<ActivityManager.OnUidImportanceListener> listenerCaptor =
                ArgumentCaptor.forClass(ActivityManager.OnUidImportanceListener.class);
        verify(mMockActivityManager).addOnUidImportanceListener(listenerCaptor.capture(), anyInt());
        mUidImportanceListener = listenerCaptor.getValue();
        assertNotNull(mUidImportanceListener);
    }

    @Test
    public void testTrackJob_foregroundUid_createsHighPriorityBinding() throws Exception {
        when(mMockActivityManager.getUidImportance(TEST_UID_1))
                .thenReturn(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);

        mService.trackJobElevated(TEST_UID_1);

        assertNotNull(mService.mHighPriorityConnection);
        assertTrue(mService.mHighPriorityUids.contains(TEST_UID_1));
        assertEquals(1, (int) mService.mJobCountsByUid.get(TEST_UID_1));
    }

    @Test
    public void testTrackJob_backgroundUid_doesNotCreateHighPriorityBinding() throws Exception {
        when(mMockActivityManager.getUidImportance(TEST_UID_1))
                .thenReturn(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED);

        mService.trackJobElevated(TEST_UID_1);

        assertNull(mService.mHighPriorityConnection);
        assertTrue(mService.mHighPriorityUids.isEmpty());
    }

    @Test
    public void testUntrackJob_lastJob_releasesHighPriorityBinding() throws Exception {
        when(mMockActivityManager.getUidImportance(TEST_UID_1))
                .thenReturn(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
        mService.trackJobElevated(TEST_UID_1);
        assertNotNull(mService.mHighPriorityConnection);

        mService.untrackJobElevated(TEST_UID_1);

        assertNull(mService.mHighPriorityConnection);
        assertTrue(mService.mHighPriorityUids.isEmpty());
        assertNull(mService.mJobCountsByUid.get(TEST_UID_1));
    }

    @Test
    public void testMultipleUids_sharedHighPriorityBinding() throws Exception {
        when(mMockActivityManager.getUidImportance(TEST_UID_1))
                .thenReturn(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
        when(mMockActivityManager.getUidImportance(TEST_UID_2))
                .thenReturn(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);

        mService.trackJobElevated(TEST_UID_1);
        RemoteOnDeviceSandboxedInferenceService conn1 = mService.mHighPriorityConnection;
        assertNotNull(conn1);

        mService.trackJobElevated(TEST_UID_2);
        RemoteOnDeviceSandboxedInferenceService conn2 = mService.mHighPriorityConnection;
        assertNotNull(conn2);
        assertEquals(conn1, conn2); // Check it's the same shared connection

        assertTrue(mService.mHighPriorityUids.contains(TEST_UID_1));
        assertTrue(mService.mHighPriorityUids.contains(TEST_UID_2));

        mService.untrackJobElevated(TEST_UID_1);
        assertNotNull(mService.mHighPriorityConnection); // Still should be alive

        mService.untrackJobElevated(TEST_UID_2);
        assertNull(mService.mHighPriorityConnection); // Now should be gone
    }

    @Test
    public void testUidImportanceListener_toForeground_createsBinding() throws Exception {
        when(mMockActivityManager.getUidImportance(TEST_UID_1))
                .thenReturn(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED);
        mService.trackJobElevated(TEST_UID_1);
        assertNull(mService.mHighPriorityConnection);

        mUidImportanceListener.onUidImportance(
                TEST_UID_1, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);

        waitForForegroundThread();
        assertNotNull(mService.mHighPriorityConnection);
        assertTrue(mService.mHighPriorityUids.contains(TEST_UID_1));
    }

    @Test
    public void testUidImportanceListener_toForeground_noJobs_doesNotCreateBinding()
            throws Exception {
        // Ensure no jobs are tracked for this UID
        assertTrue(mService.mJobCountsByUid.isEmpty());

        // Notify listener that UID is in foreground
        mUidImportanceListener.onUidImportance(
                TEST_UID_1, ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);

        waitForForegroundThread();
        // Verify that no high-priority connection is created
        assertNull(mService.mHighPriorityConnection);
        assertTrue(mService.mHighPriorityUids.isEmpty());
    }

    @Test
    public void testUidImportanceListener_toBackground_releasesBinding() throws Exception {
        when(mMockActivityManager.getUidImportance(TEST_UID_1))
                .thenReturn(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
        mService.trackJobElevated(TEST_UID_1);
        assertNotNull(mService.mHighPriorityConnection);

        mUidImportanceListener.onUidImportance(
                TEST_UID_1, ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED);

        waitForForegroundThread();
        assertNull(mService.mHighPriorityConnection);
        assertTrue(mService.mHighPriorityUids.isEmpty());
    }

    private void waitForForegroundThread() throws InterruptedException, TimeoutException {
        final CountDownLatch latch = new CountDownLatch(1);
        FgThread.getHandler().post(latch::countDown);
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new TimeoutException("Timed out waiting for foreground thread.");
        }
    }
}
