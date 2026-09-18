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

package com.android.server.am;

import static android.app.ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND;
import static android.app.ActivityManager.PROCESS_STATE_TOP;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;

import android.Manifest;
import android.app.AppGlobals;
import android.app.AppLockInternal;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.UserInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.UserHandle;
import android.permission.IPermissionManager;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.security.Flags;
import android.util.SparseArray;

import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;

import com.android.server.LocalServices;
import com.android.server.am.psc.MockUtils;
import com.android.server.appop.AppOpsService;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wm.ActivityAssistInfo;
import com.android.server.wm.ActivityTaskManagerInternal;
import com.android.server.wm.ActivityTaskManagerService;

import com.google.common.collect.ImmutableList;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test class for {@link com.android.server.am.AppLockLocalService}.
 *
 * Build/Install/Run:
 * atest FrameworksMockingServicesTests:com.android.server.am.AppLockLocalServiceTest
 */
@Presubmit
@SmallTest
@RunWith(TestParameterInjector.class)
public class AppLockLocalServiceTest {

    private static final String TEST_PACKAGE_1 = "testpackage1";
    private static final String TEST_PACKAGE_2 = "testpackage2";
    private static final String TEST_PACKAGE_3 = "testpackage3";
    private static final int TEST_USER_ID_1 = 1234;
    private static final int TEST_USER_ID_2 = 2234;
    private static final int TEST_USER_ID_3 = 3334;
    private static final int APP_UID = 12345;
    private static final int TEST_UID = UserHandle.getUid(TEST_USER_ID_1, APP_UID);
    private static final ComponentName TEST_COMPONENT = new ComponentName(TEST_PACKAGE_1,
            TEST_PACKAGE_1 + ".Foo");
    private static final ComponentName TEST_COMPONENT_2 = new ComponentName(TEST_PACKAGE_1,
            TEST_PACKAGE_1 + ".FooBar");
    private static final UserInfo TEST_USER_FULL_1 = new UserInfo(TEST_USER_ID_1, "user",
            UserInfo.FLAG_FULL);
    private static final UserInfo TEST_USER_FULL_2 = new UserInfo(TEST_USER_ID_2, "user",
            UserInfo.FLAG_FULL);
    private static final UserInfo TEST_USER_PROFILE_1 = new UserInfo(TEST_USER_ID_3, "user",
            UserInfo.FLAG_PROFILE);

    // TODO(b/454308946): Update timeout to be configurable
    private static final long GRACE_PERIOD_MS = 5000;
    private static final long SHORT_WAIT_MS = 500;

    // TODO(b/302724778): Remove manual JNI load
    static {
        System.loadLibrary("mockingservicestestjni");
    }

    @Rule
    public final ApplicationExitInfoTest.ServiceThreadRule mServiceThreadRule =
            new ApplicationExitInfoTest.ServiceThreadRule();
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    private final Context mContext = getInstrumentation().getTargetContext();
    private final TestPackageLockedStateListener mListener = new TestPackageLockedStateListener();
    private final List<UserInfo> mUserInfos = new ArrayList<>();
    private final List<String> mInstalledPackagesWithAppLockEnabled1 = new ArrayList<>();
    private final List<String> mInstalledPackagesWithAppLockEnabled2 = new ArrayList<>();
    private AppLockLocalService mAppLockLocalService;
    private TestHandler mTestHandler;
    private ActivityManagerService mAms;
    private MockitoSession mMockingSession;
    private AutoCloseable mCloseable;
    private int mCallingUid;
    @Mock
    private AppOpsService mAppOpsService;
    @Mock
    private UserController mUserController;
    @Mock
    private UserManagerInternal mUserManagerInternal;
    @Mock
    private IPackageManager mPackageManager;
    @Mock
    private IPermissionManager mPermissionManager;
    @Mock
    private PackageManagerInternal mPackageManagerInternal;
    @Mock
    private ActivityTaskManagerInternal mActivityTaskManagerInternal;
    @Mock
    private ActivityAssistInfo mActivityAssistInfo;
    @Mock
    private ActivityAssistInfo mActivityAssistInfo2;
    @Mock
    private ActivityInfo mActivityInfo;
    @Mock
    private ActivityInfo mActivityInfo2;

    @Before
    public void setUp() throws Exception {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE);
        mMockingSession = mockitoSession().initMocks(this).mockStatic(AppGlobals.class)
                .strictness(Strictness.LENIENT).startMocking();
        mCloseable = MockitoAnnotations.openMocks(this);

        // Add Local Services
        LocalServices.addService(PackageManagerInternal.class, mPackageManagerInternal);
        LocalServices.addService(ActivityTaskManagerInternal.class, mActivityTaskManagerInternal);
        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.addService(UserManagerInternal.class, mUserManagerInternal);

        // Mock static and package manager calls
        when(AppGlobals.getPermissionManager()).thenReturn(mPermissionManager);
        when(mPackageManager.getPackagesForUid(eq(Process.myUid()))).thenReturn(new String[]{""});
        when(AppGlobals.getPackageManager()).thenReturn(mPackageManager);
        mCallingUid = Process.SYSTEM_UID;

        // Set up ActivityManagerService
        TestActivityManagerServiceInjector amsInjector = new TestActivityManagerServiceInjector(
                mContext);
        mTestHandler = new TestHandler(mServiceThreadRule.getThread().getLooper());
        when(mUserController.isUserOrItsParentRunning(anyInt())).thenReturn(true);
        mAms = new ActivityManagerService(amsInjector, mServiceThreadRule.getThread(),
                mUserController);
        mAms.mActivityTaskManager = new ActivityTaskManagerService(mContext);
        mAms.mAtmInternal = mActivityTaskManagerInternal;
        mAms.mPackageManagerInt = mPackageManagerInternal;
        when(mPackageManagerInternal.getPackageUid(eq(TEST_PACKAGE_1), anyLong(),
                eq(TEST_USER_ID_1))).thenReturn(TEST_UID);
        when(mPackageManagerInternal.getSystemUiServiceComponent()).thenReturn(TEST_COMPONENT);

        // Set up AppLockLocalService
        TestInjector injector = new TestInjector();
        mAppLockLocalService = spy(new AppLockLocalService(mAms, injector));
        LocalServices.addService(AppLockInternal.class, mAppLockLocalService);
        mAppLockLocalService.registerPackageLockedStateListener(mListener);

        when(mActivityAssistInfo.getUserId()).thenReturn(TEST_USER_ID_1);
        when(mActivityAssistInfo.getComponentName()).thenReturn(TEST_COMPONENT);
        when(mActivityAssistInfo2.getUserId()).thenReturn(TEST_USER_ID_1);
        when(mActivityAssistInfo2.getComponentName()).thenReturn(TEST_COMPONENT_2);

        // Set up App Lock Enabled users and packages
        mUserInfos.add(TEST_USER_FULL_1);
        mUserInfos.add(TEST_USER_FULL_2);
        mUserInfos.add(TEST_USER_PROFILE_1);
        mInstalledPackagesWithAppLockEnabled1.add(TEST_PACKAGE_1);
        when(mPackageManagerInternal.getAppLockEnabledPackagesForUser(
                eq(TEST_USER_ID_1))).thenReturn(mInstalledPackagesWithAppLockEnabled1);
        mInstalledPackagesWithAppLockEnabled2.add(TEST_PACKAGE_1);
        mInstalledPackagesWithAppLockEnabled2.add(TEST_PACKAGE_2);
        when(mPackageManagerInternal.getAppLockEnabledPackagesForUser(
                eq(TEST_USER_ID_2))).thenReturn(mInstalledPackagesWithAppLockEnabled2);
        when(mUserManagerInternal.getUsers(
                eq(UserManagerInternal.USER_FILTER_WITH_ALL_COMPLETE_USERS))).thenReturn(
                mUserInfos);
    }

    @After
    public void tearDown() throws Exception {
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
        LocalServices.removeServiceForTest(ActivityTaskManagerInternal.class);
        LocalServices.removeServiceForTest(AppLockInternal.class);
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
        mCloseable.close();
    }

    @Test
    public void getAppLockEnabledPackages_returnsCorrectPackages() throws Exception {
        final String testPackage1 = "test.package.one";
        final String testPackage2 = "test.package.two";
        final String testPackage3 = "test.package.three";
        enableAppLockAndAuthenticate(testPackage1, TEST_USER_ID_1);
        enableAppLockAndAuthenticate(testPackage2, TEST_USER_ID_1);
        enableAppLockAndAuthenticate(testPackage3, TEST_USER_ID_2);

        SparseArray<Set<String>> enabledPackages =
                mAppLockLocalService.getAppLockEnabledPackages();

        assertThat(enabledPackages.size()).isEqualTo(2);

        Set<String> user1Packages = enabledPackages.get(TEST_USER_ID_1);
        assertThat(user1Packages).isNotNull();
        assertThat(user1Packages).containsExactly(testPackage1,
                testPackage2);

        Set<String> user2Packages = enabledPackages.get(TEST_USER_ID_2);
        assertThat(user2Packages).isNotNull();
        assertThat(user2Packages).containsExactly(testPackage3);
    }

    @Test
    public void setAppLockEnabledPackageSuccessfullyAuthenticated_unAuthorizedUid_exception(
            @TestParameter(valuesProvider = UnauthorizedUidProvider.class) int callingUid
    ) {
        mCallingUid = callingUid;

        assertThrows(SecurityException.class,
                () -> mAppLockLocalService.setAppLockEnabledPackageSuccessfullyAuthenticated(
                        TEST_PACKAGE_1, TEST_USER_ID_1));
    }

    @Test
    public void setAppLockEnabledPackageSuccessfullyAuthenticated_authorizedUid_noException(
            @TestParameter(valuesProvider = AuthorizedUidProvider.class) int callingUid
    ) {
        mCallingUid = callingUid;

        mAppLockLocalService.setAppLockEnabledPackageSuccessfullyAuthenticated(TEST_PACKAGE_1,
                TEST_USER_ID_1);
    }

    @Test
    public void isPackageLocked_neverAuthenticatedNoVisibleActivitiesAndInBackground_true() {
        mAppLockLocalService.systemServicesReady();
        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(List.of());
        when(mPackageManagerInternal.getPackageUid(eq(TEST_PACKAGE_1), anyLong(),
                eq(TEST_USER_ID_1))).thenReturn(TEST_UID);
        when(mPackageManagerInternal.getSystemUiServiceComponent()).thenReturn(TEST_COMPONENT);

        setupProcessAndUidRecord(PROCESS_STATE_IMPORTANT_BACKGROUND);

        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE_1, TEST_USER_ID_1)).isTrue();
    }

    @Test
    public void isPackageLocked_appLockNotEnabled_false() {
        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE_2, TEST_USER_ID_1)).isFalse();
    }

    @Test
    public void isPackageLocked_withinAuthTimeout_false() throws Exception {
        enableAppLockAndAuthenticate(TEST_PACKAGE_1, TEST_USER_ID_1);

        // Wait for just under the 5-second timeout.
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(GRACE_PERIOD_MS - 100, TimeUnit.MILLISECONDS);

        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE_1, TEST_USER_ID_1)).isFalse();
    }

    @Test
    public void isPackageLocked_afterAuthTimeout_true() throws Exception {
        enableAppLockAndAuthenticate(TEST_PACKAGE_1, TEST_USER_ID_1);

        // Wait for just under the 5-second timeout.
        CountDownLatch latch = new CountDownLatch(1);
        // TODO(b/454308946): Update timeout to be configurable
        latch.await(5100, TimeUnit.MILLISECONDS);

        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE_1, TEST_USER_ID_1)).isTrue();
    }

    @Test
    public void isPackageLocked_deviceLockedAndUnlockedWithinGracePeriod_true() throws Exception {
        enableAppLockAndAuthenticate(TEST_PACKAGE_1, TEST_USER_ID_1);
        mTestHandler.executeRunnables();

        // Verify unlocked initially
        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE_1, TEST_USER_ID_1)).isFalse();
        assertThat(mListener.mLocked).isFalse();

        // Lock device
        mAppLockLocalService.onDeviceLockedStateChanged(true);
        mTestHandler.executeRunnables();

        // Verify locked immediately
        assertThat(mListener.mLocked).isTrue();

        // Unlock device
        mAppLockLocalService.onDeviceLockedStateChanged(false);

        // Verify still locked, even if within grace period of original auth
        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE_1, TEST_USER_ID_1)).isTrue();
        assertThat(mAppLockLocalService.getLastSuccessfulAuthTimeForLockedPackage(TEST_PACKAGE_1,
                TEST_USER_ID_1)).isEqualTo(-1L);
    }

    @Test
    public void isPackageLocked_activityHasVisibleTaskWithShowWhenLocked_true() throws Exception {
        mAppLockLocalService.systemServicesReady();
        when(mPackageManager.isPackageAppLockEnabled(eq(TEST_PACKAGE_1),
                eq(TEST_USER_ID_1))).thenReturn(true);

        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(
                List.of(mActivityAssistInfo));
        mActivityInfo.flags |= ActivityInfo.FLAG_SHOW_WHEN_LOCKED;
        when(mPackageManagerInternal.getActivityInfo(eq(TEST_COMPONENT), anyLong(), anyInt(),
                eq(TEST_USER_ID_1))).thenReturn(mActivityInfo);

        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE_1, TEST_USER_ID_1)).isTrue();
    }

    @Test
    public void isPackageLocked_activityHasVisibleTaskWithoutShowWhenLocked_false() {
        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(
                List.of(mActivityAssistInfo));
        when(mPackageManagerInternal.getActivityInfo(eq(TEST_COMPONENT), anyLong(), anyInt(),
                eq(TEST_USER_ID_1))).thenReturn(mActivityInfo);

        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE_1, TEST_USER_ID_1)).isFalse();
    }

    @Test
    public void isPackageLocked_activityMultipleVisibleTasksWithoutShowWhenLocked_false() {
        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(
                List.of(mActivityAssistInfo, mActivityAssistInfo));
        when(mPackageManagerInternal.getActivityInfo(eq(TEST_COMPONENT), anyLong(), anyInt(),
                eq(TEST_USER_ID_1))).thenReturn(mActivityInfo);

        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE_1, TEST_USER_ID_1)).isFalse();
    }

    @Test
    public void isPackageLocked_activityMultipleVisibleTasksOneWithShowWhenLocked_false()
            throws Exception {
        when(mPackageManager.isPackageAppLockEnabled(eq(TEST_PACKAGE_1),
                eq(TEST_USER_ID_1))).thenReturn(true);

        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(
                List.of(mActivityAssistInfo, mActivityAssistInfo2));
        mActivityInfo2.flags |= ActivityInfo.FLAG_SHOW_WHEN_LOCKED;
        when(mPackageManagerInternal.getActivityInfo(eq(TEST_COMPONENT), anyLong(), anyInt(),
                eq(TEST_USER_ID_1))).thenReturn(mActivityInfo);
        when(mPackageManagerInternal.getActivityInfo(eq(TEST_COMPONENT_2), anyLong(), anyInt(),
                eq(TEST_USER_ID_1))).thenReturn(mActivityInfo2);

        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE_1, TEST_USER_ID_1)).isFalse();
    }

    @Test
    public void isPackageLocked_packageIsInForegroundWithoutVisibleTasks_false() {
        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(List.of());

        setupProcessAndUidRecord(PROCESS_STATE_TOP);

        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE_1, TEST_USER_ID_1)).isFalse();
    }

    @Test
    public void isPackageLocked_lockJobQueued_false() {
        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(List.of());
        setupProcessAndUidRecord(PROCESS_STATE_IMPORTANT_BACKGROUND);

        mAppLockLocalService.scheduleLockedStateLocked(TEST_PACKAGE_1, TEST_USER_ID_1);

        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE_1, TEST_USER_ID_1)).isFalse();
    }

    @Test
    public void setAppLockAuth_multiplePackagesWithVisibleOverlay_allUnlocked()
            throws Exception {
        mAppLockLocalService.handleAppLockEnabled(TEST_PACKAGE_1, TEST_USER_ID_1);
        mAppLockLocalService.handleAppLockEnabled(TEST_PACKAGE_2, TEST_USER_ID_1);
        mAppLockLocalService.handleAppLockEnabled(TEST_PACKAGE_3, TEST_USER_ID_1);
        when(mActivityTaskManagerInternal.getPackagesWithVisibleAppLockOverlay(TEST_USER_ID_1))
                .thenReturn(Set.of(TEST_PACKAGE_1, TEST_PACKAGE_2));

        mAppLockLocalService.setAppLockEnabledPackageSuccessfullyAuthenticated(TEST_PACKAGE_3,
                TEST_USER_ID_1);
        mTestHandler.executeRunnables();

        assertThat(mListener.mUnlockedPackages).containsExactly(TEST_PACKAGE_1, TEST_PACKAGE_2,
                TEST_PACKAGE_3);
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID_1,
                TEST_PACKAGE_1)).isFalse();
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID_1,
                TEST_PACKAGE_2)).isFalse();
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID_1,
                TEST_PACKAGE_3)).isFalse();
    }

    @Test
    public void setAppLockAuth_noPackagesWithVisibleOverlay_onlyPassedPackageUnlocked()
            throws Exception {
        mAppLockLocalService.handleAppLockEnabled(TEST_PACKAGE_1, TEST_USER_ID_1);
        when(mActivityTaskManagerInternal.getPackagesWithVisibleAppLockOverlay(TEST_USER_ID_1))
                .thenReturn(Set.of());

        mAppLockLocalService.setAppLockEnabledPackageSuccessfullyAuthenticated(TEST_PACKAGE_1,
                TEST_USER_ID_1);
        mTestHandler.executeRunnables();

        assertThat(mListener.mUnlockedPackages).containsExactly(TEST_PACKAGE_1);
        assertThat(mListener.mLocked).isFalse();
        assertThat(mListener.mPackageName).isEqualTo(TEST_PACKAGE_1);
        assertThat(mListener.mUserId).isEqualTo(TEST_USER_ID_1);
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID_1,
                TEST_PACKAGE_1)).isFalse();
    }

    @Test
    public void
            setAppLockAuth_packageWithVisibleOverlayButAppLockDisabled_onlyPassedPackageUnlocked()
            throws Exception {
        mAppLockLocalService.handleAppLockEnabled(TEST_PACKAGE_1, TEST_USER_ID_1);
        when(mActivityTaskManagerInternal.getPackagesWithVisibleAppLockOverlay(TEST_USER_ID_1))
                .thenReturn(Set.of(TEST_PACKAGE_2));

        mAppLockLocalService.setAppLockEnabledPackageSuccessfullyAuthenticated(TEST_PACKAGE_1,
                TEST_USER_ID_1);
        mTestHandler.executeRunnables();

        assertThat(mListener.mUnlockedPackages).containsExactly(TEST_PACKAGE_1);
        assertThat(mListener.mLocked).isFalse();
        assertThat(mListener.mPackageName).isEqualTo(TEST_PACKAGE_1);
        assertThat(mListener.mUserId).isEqualTo(TEST_USER_ID_1);
        assertThat(mListener.mUnlockedPackages).doesNotContain(TEST_PACKAGE_2);
    }

    @Test
    public void handleUidChangeLocked_enqueuedChangedNotProcessRelevant_noUpdate() {
        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(List.of());
        UidRecord record = setupProcessAndUidRecord(PROCESS_STATE_TOP);

        mAppLockLocalService.handleUidChangeLocked(record, TEST_UID, 0, 0);

        assertThat(mListener.hasDefaultValues()).isTrue();
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID_1,
                TEST_PACKAGE_1)).isFalse();
    }

    @Test
    public void handleUidChangeLocked_noPackagesWithAppLockEnabled_noUpdate() {
        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(List.of());

        UidRecord record = setupProcessAndUidRecord(PROCESS_STATE_TOP);

        mAppLockLocalService.handleUidChangeLocked(record, TEST_UID, UidRecord.CHANGE_PROCSTATE,
                PROCESS_STATE_IMPORTANT_BACKGROUND);

        assertThat(mListener.hasDefaultValues()).isTrue();
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID_1,
                TEST_PACKAGE_1)).isFalse();
    }

    @Test
    public void handleUidChangeLocked_nullUidRecord_noUpdate() {
        mAppLockLocalService.handleUidChangeLocked(null, TEST_UID, UidRecord.CHANGE_PROCSTATE,
                PROCESS_STATE_TOP);

        assertThat(mListener.hasDefaultValues()).isTrue();
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID_1,
                TEST_PACKAGE_1)).isFalse();
    }

    @Test
    public void handleUidChangeLocked_packageNewlyLocked_lockJobQueued() throws Exception {
        mAppLockLocalService.systemServicesReady();
        enableAppLockAndAuthenticate(TEST_PACKAGE_1, TEST_USER_ID_1);
        mTestHandler.executeRunnables();
        mListener.reset();
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS);
        UidRecord record = setupProcessAndUidRecord(PROCESS_STATE_IMPORTANT_BACKGROUND);
        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(List.of());

        mAppLockLocalService.handleUidChangeLocked(record, TEST_UID, UidRecord.CHANGE_PROCSTATE,
                PROCESS_STATE_IMPORTANT_BACKGROUND);

        assertThat(mListener.hasDefaultValues()).isTrue(); // Hasn't been sent yet
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID_1,
                TEST_PACKAGE_1)).isTrue();
    }

    @Test
    public void handleUidChangeLocked_packageGoesToBackThenFrontInGracePeriod_lockedJobCanceled()
            throws Exception {
        enableAppLockAndAuthenticate(TEST_PACKAGE_1, TEST_USER_ID_1);
        mTestHandler.executeRunnables();
        mListener.reset();
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS);
        UidRecord record = setupProcessAndUidRecord(PROCESS_STATE_IMPORTANT_BACKGROUND);
        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(List.of());

        mAppLockLocalService.handleUidChangeLocked(record, TEST_UID, UidRecord.CHANGE_PROCSTATE,
                PROCESS_STATE_IMPORTANT_BACKGROUND);
        latch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS);
        UidRecord record2 = setupProcessAndUidRecord(PROCESS_STATE_IMPORTANT_BACKGROUND);

        assertThat(mListener.hasDefaultValues()).isTrue(); // Never got sent to listener
        mAppLockLocalService.handleUidChangeLocked(record2, TEST_UID, UidRecord.CHANGE_PROCSTATE,
                PROCESS_STATE_TOP);
    }

    @Test
    public void handleUidChangeLocked_packageNewlyUnlocked_listenerReceivedUnlockedUpdate()
            throws Exception {
        enableAppLockAndAuthenticate(TEST_PACKAGE_1, TEST_USER_ID_1);
        mTestHandler.executeRunnables();
        UidRecord record = setupProcessAndUidRecord(PROCESS_STATE_IMPORTANT_BACKGROUND);

        mAppLockLocalService.handleUidChangeLocked(record, TEST_UID, UidRecord.CHANGE_PROCSTATE,
                PROCESS_STATE_TOP);
        mTestHandler.executeRunnables();

        assertThat(mListener.mLocked).isFalse();
        assertThat(mListener.mPackageName).isEqualTo(TEST_PACKAGE_1);
        assertThat(mListener.mUserId).isEqualTo(TEST_USER_ID_1);
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID_1,
                TEST_PACKAGE_1)).isFalse();
    }

    @Test
    public void handleUidChangeLocked_packageMovesToBackground_listenerReceivedLockedUpdateDelayed()
            throws Exception {
        mAppLockLocalService.systemServicesReady();
        enableAppLockAndAuthenticate(TEST_PACKAGE_1, TEST_USER_ID_1);
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS);
        UidRecord record = setupProcessAndUidRecord(PROCESS_STATE_IMPORTANT_BACKGROUND);
        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(List.of());
        mTestHandler.executeRunnables();

        mAppLockLocalService.handleUidChangeLocked(record, TEST_UID, UidRecord.CHANGE_PROCSTATE,
                PROCESS_STATE_IMPORTANT_BACKGROUND);
        latch.await(GRACE_PERIOD_MS + SHORT_WAIT_MS, TimeUnit.MILLISECONDS);
        mTestHandler.executeRunnables();

        assertThat(mListener.mLocked).isTrue();
        assertThat(mListener.mPackageName).isEqualTo(TEST_PACKAGE_1);
        assertThat(mListener.mUserId).isEqualTo(TEST_USER_ID_1);
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID_1,
                TEST_PACKAGE_1)).isFalse();
    }

    @Test
    public void handleUidChangeLocked_packageMovesToBackgroundImmediatelyAfterAuth_lockJobQueued()
            throws Exception {
        mAppLockLocalService.systemServicesReady();
        enableAppLockAndAuthenticate(TEST_PACKAGE_1, TEST_USER_ID_1);
        mTestHandler.executeRunnables();

        // Verify the package is unlocked
        assertThat(mListener.mLocked).isFalse();
        assertThat(mListener.mPackageName).isEqualTo(TEST_PACKAGE_1);
        assertThat(mListener.mUserId).isEqualTo(TEST_USER_ID_1);
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID_1,
                TEST_PACKAGE_1)).isFalse();

        // Move the package to the background
        UidRecord record = setupProcessAndUidRecord(PROCESS_STATE_IMPORTANT_BACKGROUND);
        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(List.of());
        mAppLockLocalService.handleUidChangeLocked(record, TEST_UID, UidRecord.CHANGE_PROCSTATE,
                PROCESS_STATE_IMPORTANT_BACKGROUND);

        // Verify the lock job is queued
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID_1,
                TEST_PACKAGE_1)).isTrue();
    }

    @Test
    public void handleUidChangeLocked_alreadyLocked_noOp() throws Exception {
        mAppLockLocalService.systemServicesReady();
        UidRecord record = setupProcessAndUidRecord(PROCESS_STATE_IMPORTANT_BACKGROUND);
        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(List.of());

        // 1. Move package to background and let it become locked.
        mAppLockLocalService.handleUidChangeLocked(record, TEST_UID, UidRecord.CHANGE_PROCSTATE,
                PROCESS_STATE_IMPORTANT_BACKGROUND);
        // The TestHandler in this suite executes delayed runnables immediately upon executing
        // the queue, so we can process it right away without waiting.
        mTestHandler.executeRunnables();

        // Verify it's locked and the listener was notified.
        assertThat(mListener.mLocked).isTrue();
        assertThat(mListener.mPackageName).isEqualTo(TEST_PACKAGE_1);

        // Reset listener state to detect any new notifications.
        mListener.reset();

        // 2. Trigger another UID change while still in the background.
        mAppLockLocalService.handleUidChangeLocked(record, TEST_UID, UidRecord.CHANGE_PROCSTATE,
                PROCESS_STATE_IMPORTANT_BACKGROUND);
        mTestHandler.executeRunnables();

        // 3. Verify that the listener was not notified again, as there was no state change.
        assertThat(mListener.hasDefaultValues()).isTrue();
    }

    @Test
    public void handleLockedState_runnable_abortsIfAppReturnsToForeground() throws Exception {
        mAppLockLocalService.systemServicesReady();
        enableAppLockAndAuthenticate(TEST_PACKAGE_1, TEST_USER_ID_1);
        mTestHandler.executeRunnables();

        // Verify the package is unlocked
        assertThat(mListener.mLocked).isFalse();
        assertThat(mListener.mPackageName).isEqualTo(TEST_PACKAGE_1);
        assertThat(mListener.mUserId).isEqualTo(TEST_USER_ID_1);
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID_1,
                TEST_PACKAGE_1)).isFalse();

        mAppLockLocalService.scheduleLockedStateLocked(TEST_PACKAGE_1, TEST_USER_ID_1);
        UidRecord record = setupProcessAndUidRecord(PROCESS_STATE_TOP);
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS);
        mTestHandler.executeRunnables();

        // Verify the package is still unlocked
        assertThat(mListener.mLocked).isFalse();
        assertThat(mListener.mPackageName).isEqualTo(TEST_PACKAGE_1);
        assertThat(mListener.mUserId).isEqualTo(TEST_USER_ID_1);
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID_1,
                TEST_PACKAGE_1)).isFalse();
    }

    @Test
    public void handleLockedState_runnable_respectsCurrentPackageState() throws Exception {
        mAppLockLocalService.systemServicesReady();
        enableAppLockAndAuthenticate(TEST_PACKAGE_1, TEST_USER_ID_1);
        mTestHandler.executeRunnables();

        // 1. Move package to background and queue a lock job.
        mAppLockLocalService.scheduleLockedStateLocked(TEST_PACKAGE_1, TEST_USER_ID_1);
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID_1,
                TEST_PACKAGE_1)).isTrue();

        // 2. Simulate the app becoming visible (but NOT calling handleUidChangeLocked).
        // For example, it might be visible in split screen.
        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(
                List.of(mActivityAssistInfo));
        when(mPackageManagerInternal.getActivityInfo(eq(TEST_COMPONENT), anyLong(), anyInt(),
                eq(TEST_USER_ID_1))).thenReturn(mActivityInfo);
        // Ensure showWhenLocked is false, so isPackageLocked will return false (unlocked)
        // because there's a visible activity.
        mActivityInfo.flags &= ~ActivityInfo.FLAG_SHOW_WHEN_LOCKED;

        // 3. Execute the queued lock runnable.
        mTestHandler.executeRunnables();

        // 4. Verify that the package was NOT locked because isPackageLocked returned false.
        assertThat(mListener.mLocked).isFalse();
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID_1,
                TEST_PACKAGE_1)).isFalse();
    }

    @Test
    public void onDeviceLocked_allUnlockedPackagesLockedImmediately() throws Exception {
        enableAppLockAndAuthenticate(TEST_PACKAGE_1, TEST_USER_ID_1);
        mTestHandler.executeRunnables();
        UidRecord record = setupProcessAndUidRecord(PROCESS_STATE_IMPORTANT_BACKGROUND);
        mAppLockLocalService.handleUidChangeLocked(record, TEST_UID, UidRecord.CHANGE_PROCSTATE,
                PROCESS_STATE_TOP);
        mTestHandler.executeRunnables();

        mAppLockLocalService.onDeviceLockedStateChanged(true);
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS);
        mTestHandler.executeRunnables();

        assertThat(mListener.mLocked).isTrue();
        assertThat(mListener.mPackageName).isEqualTo(TEST_PACKAGE_1);
        assertThat(mListener.mUserId).isEqualTo(TEST_USER_ID_1);
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID_1,
                TEST_PACKAGE_1)).isFalse();
        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE_1, TEST_USER_ID_1)).isTrue();
        assertThat(mAppLockLocalService.getLastSuccessfulAuthTimeForLockedPackage(TEST_PACKAGE_1,
                TEST_USER_ID_1)).isEqualTo(-1L);
    }

    @Test
    public void onDeviceLocked_multiplePackages_allLockedImmediately() throws Exception {
        mAppLockLocalService.systemServicesReady();
        // 1. Set up multiple packages across users
        enableAppLockAndAuthenticate(TEST_PACKAGE_1, TEST_USER_ID_1);
        enableAppLockAndAuthenticate(TEST_PACKAGE_2, TEST_USER_ID_2);
        mTestHandler.executeRunnables();

        // 2. Verify both are unlocked
        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE_1, TEST_USER_ID_1)).isFalse();
        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE_2, TEST_USER_ID_2)).isFalse();

        // 3. Lock device
        mAppLockLocalService.onDeviceLockedStateChanged(true);
        mTestHandler.executeRunnables();

        // 4. Verify both are locked immediately
        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE_1, TEST_USER_ID_1)).isTrue();
        assertThat(mAppLockLocalService.isPackageLocked(TEST_PACKAGE_2, TEST_USER_ID_2)).isTrue();
        // Verify listeners were notified (the test listener tracks the last notified package)
        assertThat(mListener.mLockedPackages).contains(TEST_PACKAGE_1);
        assertThat(mListener.mLockedPackages).contains(TEST_PACKAGE_2);
    }

    @Test
    public void systemServicesReady_packageMonitorRegistered_appLockEnabledPackagesInitialized() {
        mAppLockLocalService.systemServicesReady();
        SparseArray<Set<String>> appLockEnabledPackages =
                mAppLockLocalService.getAppLockEnabledPackages();

        Set<String> packagesForUser1 = appLockEnabledPackages.get(TEST_USER_ID_1);
        assertThat(packagesForUser1).isNotNull();
        assertThat(packagesForUser1).containsExactly(TEST_PACKAGE_1);

        Set<String> packagesForUser2 = appLockEnabledPackages.get(TEST_USER_ID_2);
        assertThat(packagesForUser2).isNotNull();
        assertThat(packagesForUser2).containsExactly(TEST_PACKAGE_1, TEST_PACKAGE_2);

        assertThat(appLockEnabledPackages.get(TEST_USER_ID_3)).isNull();
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_LOCK_APIS)
    public void onPackageAppLockEnabled_packageAddedToMap() throws Exception {
        mAppLockLocalService.systemServicesReady();
        Intent intent = createPackageAppLockBroadcast(TEST_PACKAGE_2, TEST_USER_ID_1,
                PackageManager.ACTION_PACKAGE_APP_LOCK_ENABLED_STATE_CHANGED, true);
        mAppLockLocalService.mPackageMonitor.doHandlePackageEvent(intent);
        SparseArray<Set<String>> appLockEnabledPackages =
                mAppLockLocalService.getAppLockEnabledPackages();
        mTestHandler.executeRunnables();

        Set<String> packagesForUser1 = appLockEnabledPackages.get(TEST_USER_ID_1);
        assertThat(packagesForUser1).isNotNull();
        assertThat(packagesForUser1).containsExactly(TEST_PACKAGE_1, TEST_PACKAGE_2);

        Set<String> packagesForUser2 = appLockEnabledPackages.get(TEST_USER_ID_2);
        assertThat(packagesForUser2).isNotNull();
        assertThat(packagesForUser2).containsExactly(TEST_PACKAGE_1, TEST_PACKAGE_2);

        assertThat(appLockEnabledPackages.get(TEST_USER_ID_3)).isNull();
    }

    @Test
    public void handleAppLockEnabled_alreadyLocked_notifiesImmediately() throws Exception {
        mAppLockLocalService.systemServicesReady();
        // 1. Mock package in background/not visible
        when(mActivityTaskManagerInternal.getTopVisibleActivities()).thenReturn(List.of());
        setupProcessAndUidRecord(PROCESS_STATE_IMPORTANT_BACKGROUND);

        // 2. Enable App Lock
        mAppLockLocalService.handleAppLockEnabled(TEST_PACKAGE_1, TEST_USER_ID_1);
        mTestHandler.executeRunnables();

        // 3. Verify it notified as locked immediately
        assertThat(mListener.mLocked).isTrue();
        assertThat(mListener.mPackageName).isEqualTo(TEST_PACKAGE_1);
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_LOCK_APIS)
    public void onPackageAppLockDisabled_packageRemovedFromMap() throws Exception {
        mAppLockLocalService.systemServicesReady();
        Intent intent = createPackageAppLockBroadcast(TEST_PACKAGE_2, TEST_USER_ID_2,
                PackageManager.ACTION_PACKAGE_APP_LOCK_ENABLED_STATE_CHANGED, false);
        mAppLockLocalService.mPackageMonitor.doHandlePackageEvent(intent);
        mTestHandler.executeRunnables();
        SparseArray<Set<String>> appLockEnabledPackages =
                mAppLockLocalService.getAppLockEnabledPackages();
        mTestHandler.executeRunnables();

        Set<String> packagesForUser1 = appLockEnabledPackages.get(TEST_USER_ID_1);
        assertThat(packagesForUser1).isNotNull();
        assertThat(packagesForUser1).containsExactly(TEST_PACKAGE_1);

        Set<String> packagesForUser2 = appLockEnabledPackages.get(TEST_USER_ID_2);
        assertThat(packagesForUser2).isNotNull();
        assertThat(packagesForUser2).containsExactly(TEST_PACKAGE_1);
        assertThat(mListener.mPackageName).isEqualTo(TEST_PACKAGE_2);
        assertThat(mListener.mUserId).isEqualTo(TEST_USER_ID_2);

        assertThat(appLockEnabledPackages.get(TEST_USER_ID_3)).isNull();
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_LOCK_APIS)
    public void onPackageAppLockDisabled_pendingLockJob_jobCanceled() throws Exception {
        mAppLockLocalService.systemServicesReady();
        // 1. Queue a delayed lock job.
        mAppLockLocalService.scheduleLockedStateLocked(TEST_PACKAGE_1, TEST_USER_ID_1);
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID_1,
                TEST_PACKAGE_1)).isTrue();

        // 2. Simulate App Lock being disabled for the package.
        Intent intent = createPackageAppLockBroadcast(TEST_PACKAGE_1, TEST_USER_ID_1,
                PackageManager.ACTION_PACKAGE_APP_LOCK_ENABLED_STATE_CHANGED, false);
        mAppLockLocalService.mPackageMonitor.doHandlePackageEvent(intent);
        mTestHandler.executeRunnables();

        // 3. Assert that the pending lock job was canceled.
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID_1,
                TEST_PACKAGE_1)).isFalse();
    }

    @Test
    public void onPackageRemoved_packageRemovedFromMap() throws Exception {
        mAppLockLocalService.systemServicesReady();
        Intent intent = createPackageRemovedBroadcast(TEST_PACKAGE_2, TEST_USER_ID_2,
                Intent.ACTION_PACKAGE_REMOVED);
        mAppLockLocalService.mPackageMonitor.doHandlePackageEvent(intent);
        mTestHandler.executeRunnables();
        SparseArray<Set<String>> appLockEnabledPackages =
                mAppLockLocalService.getAppLockEnabledPackages();

        Set<String> packagesForUser1 = appLockEnabledPackages.get(TEST_USER_ID_1);
        assertThat(packagesForUser1).isNotNull();
        assertThat(packagesForUser1).containsExactly(TEST_PACKAGE_1);

        Set<String> packagesForUser2 = appLockEnabledPackages.get(TEST_USER_ID_2);
        assertThat(packagesForUser2).isNotNull();
        assertThat(packagesForUser2).containsExactly(TEST_PACKAGE_1);

        assertThat(appLockEnabledPackages.get(TEST_USER_ID_3)).isNull();
    }

    @Test
    public void onPackageRemoved_pendingLockJob_jobCanceled() throws Exception {
        mAppLockLocalService.systemServicesReady();
        // 1. Queue a delayed lock job.
        mAppLockLocalService.scheduleLockedStateLocked(TEST_PACKAGE_1, TEST_USER_ID_1);
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID_1,
                TEST_PACKAGE_1)).isTrue();

        // 2. Simulate the package being removed.
        Intent intent = createPackageRemovedBroadcast(TEST_PACKAGE_1, TEST_USER_ID_1,
                Intent.ACTION_PACKAGE_REMOVED);
        mAppLockLocalService.mPackageMonitor.doHandlePackageEvent(intent);
        mTestHandler.executeRunnables();

        // 3. Assert that the pending lock job was canceled.
        assertThat(mAppLockLocalService.packageHasQueuedAppLockedJob(TEST_USER_ID_1,
                TEST_PACKAGE_1)).isFalse();
    }

    private UidRecord setupProcessAndUidRecord(int processState) {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = TEST_PACKAGE_1;
        info.processName = TEST_PACKAGE_1;
        info.uid = TEST_UID;
        final ProcessRecord appRec = new ProcessRecord(mAms, info, info.processName, TEST_UID);
        MockUtils.setCurProcState(appRec, processState);
        MockUtils.setSetProcState(appRec, processState);
        final UidRecord record = new UidRecord(TEST_UID, mAms);
        mAms.mProcessStateController.setUidCurProcState(record, processState);
        record.addProcess(appRec);
        mAms.mProcessList.addProcessNameLocked(appRec);
        mAms.mProcessList.mActiveUids.put(TEST_UID, record);

        return record;
    }

    private Intent createPackageRemovedBroadcast(String packageName, int userId, String action) {
        Intent intent = new Intent(action, Uri.fromParts("package", packageName, null));
        intent.putExtra(Intent.EXTRA_USER_HANDLE, userId);
        return intent;
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

    private void enableAppLockAndAuthenticate(String packageName, int userId) throws Exception {
        mAppLockLocalService.handleAppLockEnabled(packageName, userId);
        mAppLockLocalService.setAppLockEnabledPackageSuccessfullyAuthenticated(packageName, userId);
    }

    private static final class UnauthorizedUidProvider extends TestParameterValuesProvider {
        @Override
        protected List<Integer> provideValues(Context context) {
            return ImmutableList.of(Process.INVALID_UID, APP_UID, TEST_UID);
        }
    }

    private static final class AuthorizedUidProvider extends TestParameterValuesProvider {
        @Override
        protected List<Integer> provideValues(Context context) {
            return ImmutableList.of(
                    Process.SYSTEM_UID,
                    Process.ROOT_UID,
                    UserHandle.getUid(TEST_USER_ID_1, Process.SYSTEM_UID),
                    UserHandle.getUid(TEST_USER_ID_1, Process.ROOT_UID));
        }
    }

    private static class TestHandler extends Handler {
        private final Queue<Runnable> mQueue = new ArrayDeque<>();

        TestHandler(Looper looper) {
            super(looper);
        }

        @Override
        public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
            mQueue.add(msg.getCallback());
            return true;
        }

        void executeRunnables() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            while (!mQueue.isEmpty()) {
                mQueue.poll().run();
            }
            latch.await(SHORT_WAIT_MS, TimeUnit.MILLISECONDS);
        }
    }

    private static class TestPackageLockedStateListener implements
            AppLockInternal.PackageLockedStateListener {
        @Nullable
        Boolean mLocked = null;
        String mPackageName = "";
        int mUserId = -1;
        List<String> mUnlockedPackages = new ArrayList<>();
        List<String> mLockedPackages = new ArrayList<>();

        @Override
        public void onPackageLockedStateChanged(String packageName, int userId, boolean locked) {
            this.mPackageName = packageName;
            this.mUserId = userId;
            this.mLocked = locked;
            if (locked) {
                mLockedPackages.add(packageName);
                mUnlockedPackages.remove(packageName);
            } else {
                mUnlockedPackages.add(packageName);
                mLockedPackages.remove(packageName);
            }
        }

        void reset() {
            mLocked = null;
            mPackageName = "";
            mUserId = -1;
            mUnlockedPackages.clear();
            mLockedPackages.clear();
        }

        boolean hasDefaultValues() {
            return (mLocked == null) && mPackageName.isEmpty() && mUserId == -1
                    && mLockedPackages.isEmpty() && mUnlockedPackages.isEmpty();
        }
    }

    private class TestInjector implements AppLockLocalService.Injector {
        @Override
        public Handler getHandler() {
            return mTestHandler;
        }

        @Override
        public int getCallingUid() {
            return mCallingUid;
        }
    }

    private class TestActivityManagerServiceInjector extends ActivityManagerService.Injector {
        TestActivityManagerServiceInjector(Context context) {
            super(context);
        }

        @Override
        public AppOpsService getAppOpsService(Handler handler) {
            return mAppOpsService;
        }

        @Override
        public Handler getUiHandler(ActivityManagerService service) {
            return mTestHandler;
        }
    }
}
