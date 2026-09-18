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

package com.android.server;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.IActivityManager;
import android.app.PropertyInvalidatedCache;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IVold;
import android.os.SystemProperties;
import android.os.UserManager;
import android.os.storage.DiskInfo;
import android.os.storage.VolumeInfo;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.storage.WatchedVolumeInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class StorageManagerServiceTest {

    private final Context mRealContext = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().getTargetContext();
    private StorageManagerService mStorageManagerService;
    private StorageManagerInternal mStorageManagerInternal;
    private UserManager mUserManager;
    private IVold mVold;
    private PackageManager mPackageManager;

    private static final int TEST_USER_ID = 1001;
    private static final int SECOND_TEST_USER_ID = 1002;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .spyStatic(UserManager.class)
            .spyStatic(SystemProperties.class)
            .spyStatic(ActivityManager.class)
            .build();

    @Before
    public void setFixtures() throws Exception {
        PropertyInvalidatedCache.disableForTestMode();

        // Called when WatchedUserStates is constructed
        doNothing().when(() -> UserManager.invalidateIsUserUnlockedCache());

        mVold = mock(IVold.class);

        Context contextSpy = spy(mRealContext);
        mPackageManager = mock(PackageManager.class);
        when(contextSpy.getPackageManager()).thenReturn(mPackageManager);

        mStorageManagerService = spy(new StorageManagerService(contextSpy));
        // Inject mock vold
        doReturn(mVold).when(mStorageManagerService).getVold();

        mStorageManagerInternal = LocalServices.getService(StorageManagerInternal.class);
        assertWithMessage("LocalServices.getService(StorageManagerInternal.class)")
                .that(mStorageManagerInternal).isNotNull();
        mUserManager = mRealContext.getSystemService(UserManager.class);
    }

    @After
    public void tearDown() {
        LocalServices.removeServiceForTest(StorageManagerInternal.class);
    }

    @Test
    public void testWaitForCheckpointReady() throws Exception {
        // Set up that checkpointing is required
        doReturn(true).when(mStorageManagerService).needsCheckpoint();
        com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn("0")
                .when(() -> SystemProperties.get(eq("vold.checkpoint_committed"), anyString()));

        CountDownLatch syncStartedLatch = new CountDownLatch(1);
        CountDownLatch syncFinishLatch = new CountDownLatch(1);
        CountDownLatch callbacksLatch = new CountDownLatch(2);
        AtomicInteger callbackCount = new AtomicInteger(0);

        // When syncStorage is called, release syncStartedLatch and wait until syncFinishLatch
        // is released
        doAnswer(invocation -> {
            syncStartedLatch.countDown();
            assertTrue("Timed out waiting for syncFinishLatch",
                    syncFinishLatch.await(5, TimeUnit.SECONDS));
            return null;
        }).when(mVold).syncStorage();

        Runnable callback1 = () -> {
            callbackCount.incrementAndGet();
            callbacksLatch.countDown();
        };
        Runnable callback2 = () -> {
            callbackCount.incrementAndGet();
            callbacksLatch.countDown();
        };

        // First call: start sync
        assertTrue(mStorageManagerService.waitForCheckpointReady(callback1));

        // Wait until syncStorage starts
        assertTrue("syncStorage should have started",
                syncStartedLatch.await(5, TimeUnit.SECONDS));

        // Second call: sync is still in progress (due to syncFinishLatch), so callback
        // should be added
        assertTrue(mStorageManagerService.waitForCheckpointReady(callback2));

        // Allow sync to finish
        syncFinishLatch.countDown();

        // Verify all callbacks are executed
        assertTrue("All callbacks should be executed",
                callbacksLatch.await(5, TimeUnit.SECONDS));
        assertWithMessage("All callbacks should be executed")
                .that(callbackCount.get()).isEqualTo(2);
    }

    @Test
    public void testMountWithRestrictionFailure() {
        DiskInfo diskInfo = new DiskInfo("diskInfoId", DiskInfo.FLAG_USB);
        VolumeInfo volumeInfo = new VolumeInfo(
                "testVolId", VolumeInfo.TYPE_PUBLIC, diskInfo, "partGuid"
        );
        volumeInfo.mountUserId = TEST_USER_ID;
        WatchedVolumeInfo watchedVolumeInfo = WatchedVolumeInfo.fromVolumeInfo(volumeInfo);
        doReturn(watchedVolumeInfo).when(mStorageManagerService).findVolumeByIdOrThrow(
                "testVolId");
        android.os.UserHandle userHandleForRestriction = android.os.UserHandle.of(TEST_USER_ID);
        when(
                mUserManager.hasUserRestriction(
                        UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA, userHandleForRestriction))
                .thenReturn(true);

        assertThrows(SecurityException.class,
                () -> mStorageManagerService.mount(watchedVolumeInfo.getId()));
    }

    @Test
    public void testMountWithoutRestrictionSuccess() {
        DiskInfo diskInfo = new DiskInfo("diskInfoId", DiskInfo.FLAG_USB);
        VolumeInfo volumeInfo = new VolumeInfo("testVolId", VolumeInfo.TYPE_PUBLIC, diskInfo,
                "partGuid");
        volumeInfo.mountUserId = TEST_USER_ID;
        WatchedVolumeInfo watchedVolumeInfo = WatchedVolumeInfo.fromVolumeInfo(volumeInfo);
        doReturn(watchedVolumeInfo).when(mStorageManagerService).findVolumeByIdOrThrow(
                "testVolId");
        // Still set the restriction for one user, but mount on a different user.
        android.os.UserHandle userHandleForRestriction = android.os.UserHandle.of(
                SECOND_TEST_USER_ID);
        when(
                mUserManager.hasUserRestriction(
                        UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA, userHandleForRestriction))
                .thenReturn(true);

        mStorageManagerService.mount(watchedVolumeInfo.getId());
    }

    @Test
    public void testOnAppOpsChanged_killAppForOpChange_normalUid() throws Exception {
        IActivityManager am = mock(IActivityManager.class);
        doReturn(am).when(() -> ActivityManager.getService());

        int normalUid = 10050;
        when(mPackageManager.getAppUidForPrivateComputeCoreUid(normalUid))
                .thenReturn(android.os.Process.INVALID_UID);

        mStorageManagerInternal.onAppOpsChanged(
                AppOpsManager.OP_MANAGE_EXTERNAL_STORAGE,
                normalUid,
                "com.example.app",
                AppOpsManager.MODE_IGNORED,
                AppOpsManager.MODE_ALLOWED);

        // getAppId(10050) should return 10050
        verify(am).killUid(eq(10050), eq(android.os.UserHandle.USER_ALL), anyString());
    }

    @Test
    public void testOnAppOpsChanged_killAppForOpChange_pccUid() throws Exception {
        IActivityManager am = mock(IActivityManager.class);
        doReturn(am).when(() -> ActivityManager.getService());

        int pccUid = 30000;
        int appUid = 10050;
        when(mPackageManager.getAppUidForPrivateComputeCoreUid(pccUid))
                .thenReturn(appUid);

        mStorageManagerInternal.onAppOpsChanged(
                AppOpsManager.OP_MANAGE_EXTERNAL_STORAGE,
                pccUid,
                "com.example.app",
                AppOpsManager.MODE_IGNORED,
                AppOpsManager.MODE_ALLOWED);

        // getAppId(30000) should return UserHandle.getAppId(10050) = 10050
        verify(am).killUid(eq(10050), eq(android.os.UserHandle.USER_ALL), anyString());
    }
}
