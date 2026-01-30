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

package com.android.server.appwindowlayout;

import static android.content.pm.PackageManager.USER_MIN_ASPECT_RATIO_FULLSCREEN;
import static android.content.pm.PackageManager.USER_MIN_ASPECT_RATIO_SPLIT_SCREEN;
import static android.content.pm.PackageManager.USER_MIN_ASPECT_RATIO_UNSET;

import static com.android.server.appwindowlayout.AppWindowLayoutSettingsRestoreStorage.RESTORE_TIME_STAGED_DATA_PREFS;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.content.pm.IPackageManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.LocalServices;
import com.android.server.testutils.FakeSharedPreferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * Unit tests for the {@link AppWindowLayoutSettingsService}.
 *
 * To run this test: atest FrameworksServicesTests_android_server_appwindowlayout
 */
@RunWith(AndroidJUnit4.class)
public class AppWindowLayoutSettingsServiceTest {
    private static final String DEFAULT_PACKAGE_NAME = "com.android.testapp";
    private static final String ANOTHER_PACKAGE_NAME = "com.android.anothertestapp";

    private Context mContext;
    private FakeSharedPreferences mFakeUserAspectRatioSharedPreferences;
    private FakeSharedPreferences mFakeRestoreTimeSharedPreferences;
    private IPackageManager mIPackageManager;

    private AppWindowLayoutSettingsService mService;
    private AppWindowLayoutSettingsPackageMonitor mPackageMonitor;
    private String mTestPackageName;
    private int mUserId;

    @Before
    public void setUp() throws Exception {
        mContext = spy(ApplicationProvider.getApplicationContext());
        mTestPackageName = mContext.getPackageName();
        mUserId = mContext.getUserId();
        mIPackageManager = mock(IPackageManager.class);

        setupMockSharedPreferences();
        mPackageMonitor = spy(new AppWindowLayoutSettingsPackageMonitor());
        mService = new AppWindowLayoutSettingsService(mContext, mIPackageManager, mPackageMonitor);
        mService.onStart();
    }

    @After
    public void tearDown() {
        if (mFakeUserAspectRatioSharedPreferences != null) {
            mFakeUserAspectRatioSharedPreferences.edit().clear().commit();
        }
        if (mFakeRestoreTimeSharedPreferences != null) {
            mFakeRestoreTimeSharedPreferences.edit().clear().commit();
        }
        LocalServices.removeServiceForTest(AppWindowLayoutSettingsService.class);
    }

    @Test
    public void testOnStart_serviceAdded() {
        assertEquals(mService, LocalServices.getService(AppWindowLayoutSettingsService.class));
    }

    @Test
    public void testAwaitPackageInstallForAspectRatio_savedPkgAspectRatioInSharedPreferences() {
        mService.awaitPackageInstallForAspectRatio(DEFAULT_PACKAGE_NAME, mUserId,
                USER_MIN_ASPECT_RATIO_FULLSCREEN);

        assertEquals(USER_MIN_ASPECT_RATIO_FULLSCREEN, mFakeUserAspectRatioSharedPreferences.getInt(
                DEFAULT_PACKAGE_NAME, USER_MIN_ASPECT_RATIO_UNSET));
    }

    @Test
    public void testAwaitPackageInstallForAspectRatio_registersPackageMonitor() {
        mService.awaitPackageInstallForAspectRatio(DEFAULT_PACKAGE_NAME, mUserId,
                USER_MIN_ASPECT_RATIO_FULLSCREEN);
        verify(mPackageMonitor).register(eq(mContext), any(Looper.class), eq(UserHandle.ALL),
                eq(true));
    }

    @Test
    public void testOnPackageAdded_savedUserAspectRatioExists_setUserMinAspectRatio()
            throws RemoteException {
        final int userId = mContext.getUserId();
        mService.awaitPackageInstallForAspectRatio(DEFAULT_PACKAGE_NAME, userId,
                USER_MIN_ASPECT_RATIO_FULLSCREEN);

        sendPackageAddedEvent(DEFAULT_PACKAGE_NAME);

        verify(mIPackageManager).setUserMinAspectRatio(DEFAULT_PACKAGE_NAME, userId,
                USER_MIN_ASPECT_RATIO_FULLSCREEN);
    }

    @Test
    public void testOnPackageAdded_setAllAspectRatios_stopListeningForPackageUpdates() {
        mService.awaitPackageInstallForAspectRatio(DEFAULT_PACKAGE_NAME, mUserId,
                USER_MIN_ASPECT_RATIO_FULLSCREEN);

        // AppWindowLayoutSettingsService should remove the package data from storage after
        // processing package-added.
        sendPackageAddedEvent(DEFAULT_PACKAGE_NAME);

        // As the only package data has been processed, stop listening to package updates.
        verify(mPackageMonitor).unregister();
    }

    @Test
    public void testOnPackageAdded_twoPackagesButOnlyOneInstalled_stillRegisteredPackageUpdates() {
        mService.awaitPackageInstallForAspectRatio(DEFAULT_PACKAGE_NAME, mUserId,
                USER_MIN_ASPECT_RATIO_FULLSCREEN);
        mService.awaitPackageInstallForAspectRatio(ANOTHER_PACKAGE_NAME, mUserId,
                USER_MIN_ASPECT_RATIO_SPLIT_SCREEN);

        // AppWindowLayoutSettingsService should remove the package data from storage after
        // processing package-added.
        sendPackageAddedEvent(DEFAULT_PACKAGE_NAME);

        // As the only package data has been processed, stop listening to package updates.
        verify(mPackageMonitor, never()).unregister();
    }

    @Test
    public void testOnPackageAdded_noUserAspectRatioExists_noUserAspectRatioIsSet()
            throws Exception {
        sendPackageAddedEvent(DEFAULT_PACKAGE_NAME);

        verify(mIPackageManager, never()).setUserMinAspectRatio(anyString(), anyInt(), anyInt());
    }

    @Test
    public void testOnPackageAdded_userAlreadyChangedAspectRatio_nothingIsSet()
            throws RemoteException {
        doReturn(USER_MIN_ASPECT_RATIO_SPLIT_SCREEN).when(mIPackageManager).getUserMinAspectRatio(
                DEFAULT_PACKAGE_NAME, mUserId);
        mService.awaitPackageInstallForAspectRatio(DEFAULT_PACKAGE_NAME, mUserId,
                USER_MIN_ASPECT_RATIO_FULLSCREEN);

        sendPackageAddedEvent(DEFAULT_PACKAGE_NAME);

        verify(mIPackageManager, never()).setUserMinAspectRatio(DEFAULT_PACKAGE_NAME, mUserId,
                USER_MIN_ASPECT_RATIO_FULLSCREEN);
    }

    private void setupMockSharedPreferences() {
        mFakeUserAspectRatioSharedPreferences = new FakeSharedPreferences();
        mFakeRestoreTimeSharedPreferences = new FakeSharedPreferences();
        doReturn(mContext).when(mContext).createDeviceProtectedStorageContext();
        doReturn(mFakeUserAspectRatioSharedPreferences).when(mContext).getSharedPreferences(
                eq(createFile(
                        AppWindowLayoutSettingsRestoreStorage.ASPECT_RATIO_STAGED_DATA_PREFS)),
                eq(Context.MODE_PRIVATE));
        doReturn(mFakeRestoreTimeSharedPreferences).when(mContext).getSharedPreferences(
                eq(createFile(RESTORE_TIME_STAGED_DATA_PREFS)),
                eq(Context.MODE_PRIVATE));
    }

    @NonNull
    private File createFile(@NonNull String filename) {
        return new File(Environment.getDataSystemDeDirectory(mUserId), mTestPackageName + "."
                + filename);
    }

    private void sendPackageAddedEvent(@NonNull String packageName) {
        Intent intent = new Intent(Intent.ACTION_PACKAGE_ADDED);
        intent.setData(Uri.fromParts("package", packageName, null));
        intent.putExtra(Intent.EXTRA_USER_HANDLE, mUserId);
        intent.putExtra(Intent.EXTRA_UID, mContext.getApplicationInfo().uid);
        intent.putExtra(Intent.EXTRA_REPLACING, false);
        mPackageMonitor.doHandlePackageEvent(intent);
    }
}
