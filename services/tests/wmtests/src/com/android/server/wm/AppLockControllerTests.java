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

package com.android.server.wm;

import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AppLockInternal;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.security.Flags;
import android.util.ArraySet;
import android.util.SparseArray;

import androidx.test.filters.MediumTest;

import com.android.internal.os.BackgroundThread;
import com.android.server.LocalServices;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.Set;

/**
 * Build/Install/Run:
 * atest WmTests:AppLockControllerTests
 */
@EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
@MediumTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppLockControllerTests extends WindowTestsBase {

    private AppLockController mAppLockController;
    private RecentTasks mRecentTasks;
    private AppLockInternal mMockAppLockInternal;

    @Before
    public void setUp() throws Exception {
        mMockAppLockInternal = LocalServices.getService(AppLockInternal.class);
        when(mMockAppLockInternal.getAppLockEnabledPackages()).thenReturn(new SparseArray<>());
        clearInvocations(mMockAppLockInternal);

        mAppLockController = new AppLockController(mWm);
        spyOn(mAppLockController);

        mRecentTasks = mAtm.getRecentTasks();
        spyOn(mRecentTasks);
    }

    @Test
    public void testSystemReady_registersPackageMonitor() {
        spyOn(mAppLockController.mPackageMonitor);

        mAppLockController.systemReady();

        verify(mAppLockController.mPackageMonitor).register(eq(mWm.mContext), eq(UserHandle.ALL),
                eq(BackgroundThread.getHandler()));
    }

    @Test
    public void testSystemReady_registersPackageLockedStateListener() {
        assertThat(captureAppLockListenerInSystemReady()).isNotNull();
    }

    @Test
    public void testSystemReady_initializesLockedPackages() {
        final SparseArray<Set<String>> appLockEnabledPackages = new SparseArray<>();
        final Set<String> user1Packages = new ArraySet<>();
        user1Packages.add(TEST_PACKAGE_1);
        appLockEnabledPackages.put(TEST_USER_ID_1, user1Packages);
        final Set<String> user2Packages = new ArraySet<>();
        user2Packages.add(TEST_PACKAGE_2);
        appLockEnabledPackages.put(TEST_USER_ID_2, user2Packages);

        when(mMockAppLockInternal.getAppLockEnabledPackages()).thenReturn(appLockEnabledPackages);

        mAppLockController.systemReady();

        verify(mMockAppLockInternal).getAppLockEnabledPackages();
        assertThat(mAppLockController.isPackageLockedByAppLockLocked(TEST_PACKAGE_1,
                TEST_USER_ID_1)).isTrue();
        assertThat(mAppLockController.isPackageLockedByAppLockLocked(TEST_PACKAGE_2,
                TEST_USER_ID_2)).isTrue();
        // Packages that were not in the App Lock enabled list are not locked.
        assertThat(mAppLockController.isPackageLockedByAppLockLocked(TEST_PACKAGE_2,
                TEST_USER_ID_1)).isFalse();
        assertThat(mAppLockController.isPackageLockedByAppLockLocked(TEST_PACKAGE_1,
                TEST_USER_ID_2)).isFalse();
    }

    @Test
    public void testIsPackageLockedByAppLock_packageIsLocked() {
        final AppLockInternal.PackageLockedStateListener listener =
                captureAppLockListenerInSystemReady();

        try {
            listener.onPackageLockedStateChanged(TEST_PACKAGE_2, TEST_USER_ID_1, true);

            assertThat(mAppLockController.isPackageLockedByAppLockLocked(TEST_PACKAGE_2,
                    TEST_USER_ID_1)).isTrue();
        } finally {
            listener.onPackageLockedStateChanged(TEST_PACKAGE_2, TEST_USER_ID_1, false);
        }
    }

    @Test
    public void testIsPackageLockedByAppLock_packageIsUnlocked() {
        final AppLockInternal.PackageLockedStateListener listener =
                captureAppLockListenerInSystemReady();

        try {
            listener.onPackageLockedStateChanged(TEST_PACKAGE_2, TEST_USER_ID_1, true);
            listener.onPackageLockedStateChanged(TEST_PACKAGE_2, TEST_USER_ID_1, false);

            assertThat(mAppLockController.isPackageLockedByAppLockLocked(TEST_PACKAGE_2,
                    TEST_USER_ID_1)).isFalse();
        } finally {
            listener.onPackageLockedStateChanged(TEST_PACKAGE_2, TEST_USER_ID_1, false);
        }
    }

    @Test
    public void testIsPackageLockedByAppLock_differentPackageUpdateDoesNotAffectState() {
        final AppLockInternal.PackageLockedStateListener listener =
                captureAppLockListenerInSystemReady();

        try {
            listener.onPackageLockedStateChanged(TEST_PACKAGE_1, TEST_USER_ID_1, false);

            assertThat(mAppLockController.isPackageLockedByAppLockLocked(TEST_PACKAGE_2,
                    TEST_USER_ID_1)).isFalse();
        } finally {
            listener.onPackageLockedStateChanged(TEST_PACKAGE_1, TEST_USER_ID_1, true);
        }
    }

    @Test
    public void testIsPackageLockedByAppLock_differentUserUpdateDoesNotAffectState() {
        final AppLockInternal.PackageLockedStateListener listener =
                captureAppLockListenerInSystemReady();

        try {
            listener.onPackageLockedStateChanged(TEST_PACKAGE_2, TEST_USER_ID_1, true);

            assertThat(mAppLockController.isPackageLockedByAppLockLocked(TEST_PACKAGE_1,
                    TEST_USER_ID_2)).isFalse();
        } finally {
            listener.onPackageLockedStateChanged(TEST_PACKAGE_2, TEST_USER_ID_1, false);
        }
    }

    @Test
    public void testAddLockedByAppLockActivityOverlayLocked() {
        final ActivityRecord mockActivity = mock(ActivityRecord.class);
        final AppLockOverlayController appLockOverlayController =
                mAppLockController.mAppLockOverlayController;
        spyOn(appLockOverlayController);

        mAppLockController.addLockedByAppLockActivityOverlayLocked(mockActivity);

        verify(appLockOverlayController).addLockedByAppLockActivityOverlay(mockActivity);
    }

    @Test
    public void testIsActivityLockedByAppLockLocked_activityIsNotLocked_returnsFalse() {
        final ActivityRecord mockActivity = mock(ActivityRecord.class);
        final AppLockOverlayController appLockOverlayController =
                mAppLockController.mAppLockOverlayController;
        spyOn(appLockOverlayController);
        doReturn(false).when(appLockOverlayController).isActivityLockedByAppLock(mockActivity);

        final boolean result = mAppLockController.isActivityLockedByAppLockLocked(mockActivity);

        verify(appLockOverlayController).isActivityLockedByAppLock(mockActivity);
        assertThat(result).isFalse();
    }

    @Test
    public void testIsActivityLockedByAppLockLocked_activityIsLocked_returnsTrue() {
        final ActivityRecord mockActivity = mock(ActivityRecord.class);
        final AppLockOverlayController appLockOverlayController =
                mAppLockController.mAppLockOverlayController;
        spyOn(appLockOverlayController);
        doReturn(true).when(appLockOverlayController).isActivityLockedByAppLock(mockActivity);

        final boolean result = mAppLockController.isActivityLockedByAppLockLocked(mockActivity);

        verify(appLockOverlayController).isActivityLockedByAppLock(mockActivity);
        assertThat(result).isTrue();
    }

    @Test
    public void testGetPackagesWithVisibleAppLockOverlayLocked() {
        final AppLockOverlayController appLockOverlayController =
                mAppLockController.mAppLockOverlayController;
        spyOn(appLockOverlayController);
        final ArraySet<String> expectedPackages = new ArraySet<>();
        expectedPackages.add(TEST_PACKAGE_1);
        expectedPackages.add(TEST_PACKAGE_2);
        doReturn(expectedPackages).when(appLockOverlayController)
                .getPackagesWithVisibleAppLockOverlay(TEST_USER_ID_1);

        final Set<String> result =
                mAppLockController.getPackagesWithVisibleAppLockOverlayLocked(TEST_USER_ID_1);

        verify(appLockOverlayController).getPackagesWithVisibleAppLockOverlay(TEST_USER_ID_1);
        assertThat(result).containsExactly(TEST_PACKAGE_1, TEST_PACKAGE_2);
    }

    @Test
    public void testOnPackageLockedStateChanged_packageLocked_callsLockActivitiesTasks() {
        final AppLockInternal.PackageLockedStateListener listener =
                captureAppLockListenerInSystemReady();
        final AppLockOverlayController appLockOverlayController =
                mAppLockController.mAppLockOverlayController;
        spyOn(appLockOverlayController);

        listener.onPackageLockedStateChanged(DEFAULT_COMPONENT_PACKAGE_NAME, TEST_USER_ID_1, true);

        verify(appLockOverlayController).lockActivitiesTasksForAppLock(
                DEFAULT_COMPONENT_PACKAGE_NAME, TEST_USER_ID_1);
    }

    @Test
    public void testOnPackageLockedStateChanged_packageUnlocked_doesNotCallLockActivitiesTasks() {
        final AppLockInternal.PackageLockedStateListener listener =
                captureAppLockListenerInSystemReady();
        final AppLockOverlayController appLockOverlayController =
                mAppLockController.mAppLockOverlayController;
        listener.onPackageLockedStateChanged(DEFAULT_COMPONENT_PACKAGE_NAME, TEST_USER_ID_1, true);
        spyOn(appLockOverlayController);

        listener.onPackageLockedStateChanged(DEFAULT_COMPONENT_PACKAGE_NAME, TEST_USER_ID_1, false);

        verify(appLockOverlayController, never()).lockActivitiesTasksForAppLock(anyString(),
                anyInt());
    }

    @Test
    public void testOnPackageLockedStateChanged_updatesAllPackageWindowsHiddenState() {
        final int testUserId = 0;
        final WindowState win1 = newWindowBuilder("overlayWindow1", TYPE_APPLICATION_OVERLAY)
                .setOwningPackage(TEST_PACKAGE_1)
                .setOwnerId(10123)
                .build();
        final WindowState win2 = newWindowBuilder("overlayWindow2", TYPE_APPLICATION)
                .setOwningPackage(TEST_PACKAGE_1)
                .setOwnerId(10123)
                .build();
        final WindowState win3 = newWindowBuilder("overlayWindow3", TYPE_APPLICATION_OVERLAY)
                .setOwningPackage(TEST_PACKAGE_2)
                .setOwnerId(10456)
                .build();

        assertThat(win1).isNotNull();
        assertThat(win2).isNotNull();
        assertThat(win3).isNotNull();
        spyOn(win1);
        spyOn(win2);
        spyOn(win3);

        // Simulate package being locked by App Lock and expect package's windows' state updated.
        final AppLockInternal.PackageLockedStateListener listener =
                captureAppLockListenerInSystemReady();
        listener.onPackageLockedStateChanged(TEST_PACKAGE_1, testUserId, true);
        verify(win1).setHiddenWhileLockedByAppLock(true);
        verify(win2).setHiddenWhileLockedByAppLock(true);
        verify(win3, never()).setHiddenWhileLockedByAppLock(true);

        // Simulate package being unlocked by App Lock and expect package's windows' state updated.
        listener.onPackageLockedStateChanged(TEST_PACKAGE_1, testUserId, false);
        verify(win1).setHiddenWhileLockedByAppLock(false);
        verify(win2).setHiddenWhileLockedByAppLock(false);
        verify(win3, never()).setHiddenWhileLockedByAppLock(false);
    }

    @Test
    public void testPackageMonitorOnPackageAppLockEnabled() {
        final Intent intent = createPackageAppLockBroadcast(TEST_PACKAGE_1, TEST_USER_ID_1,
                PackageManager.ACTION_PACKAGE_APP_LOCK_ENABLED_STATE_CHANGED, true);
        mAppLockController.mPackageMonitor.doHandlePackageEvent(intent);

        verify(mRecentTasks).onPackageAppLockEnabledChanged(TEST_PACKAGE_1, TEST_USER_ID_1, true);
    }

    @Test
    public void testPackageMonitorOnPackageAppLockDisabled() {
        final Intent intent = createPackageAppLockBroadcast(TEST_PACKAGE_1, TEST_USER_ID_1,
                PackageManager.ACTION_PACKAGE_APP_LOCK_ENABLED_STATE_CHANGED, false);
        mAppLockController.mPackageMonitor.doHandlePackageEvent(intent);

        verify(mRecentTasks).onPackageAppLockEnabledChanged(TEST_PACKAGE_1, TEST_USER_ID_1, false);
    }

    private AppLockInternal.PackageLockedStateListener captureAppLockListenerInSystemReady() {
        final ArgumentCaptor<AppLockInternal.PackageLockedStateListener> listenerCaptor =
                ArgumentCaptor.forClass(AppLockInternal.PackageLockedStateListener.class);
        mAppLockController.systemReady();
        verify(mMockAppLockInternal).registerPackageLockedStateListener(
                listenerCaptor.capture());
        return listenerCaptor.getValue();
    }

    private Intent createPackageAppLockBroadcast(String packageName, int userId, String action,
            boolean enabled) {
        Intent intent = new Intent(action, Uri.fromParts("package", packageName, null));
        intent.putExtra(Intent.EXTRA_USER_HANDLE, userId);
        final Bundle extras = new Bundle();
        extras.putBoolean(PackageManager.EXTRA_APP_LOCK_NEW_STATE, enabled);
        intent.putExtras(extras);
        return intent;
    }
}
