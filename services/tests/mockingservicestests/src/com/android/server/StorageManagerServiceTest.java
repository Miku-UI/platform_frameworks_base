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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.PropertyInvalidatedCache;
import android.content.Context;
import android.os.UserManager;
import android.os.storage.DiskInfo;
import android.os.storage.VolumeInfo;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.storage.WatchedVolumeInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class StorageManagerServiceTest {

    private final Context mRealContext = androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation().getTargetContext();
    private StorageManagerService mStorageManagerService;
    private StorageManagerInternal mStorageManagerInternal;
    private UserManager mUserManager;

    private static final int TEST_USER_ID = 1001;
    private static final int SECOND_TEST_USER_ID = 1002;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .spyStatic(UserManager.class)
            .build();

    @Before
    public void setFixtures() {
        PropertyInvalidatedCache.disableForTestMode();

        // Called when WatchedUserStates is constructed
        doNothing().when(() -> UserManager.invalidateIsUserUnlockedCache());

        mStorageManagerService = spy(new StorageManagerService(mRealContext));
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
}
