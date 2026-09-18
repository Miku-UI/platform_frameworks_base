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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.pm.ActivityInfo.LAUNCH_MULTIPLE;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManagerInternal;
import android.content.pm.SigningDetails;
import android.provider.DeviceConfig;

import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.android.server.pm.PackageArchiver;
import com.android.server.pm.UserManagerInternal;
import com.android.server.pm.pkg.AndroidPackage;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

// NOTE: methods on this class were copied "as-is from ActivityStarterTests, so it's possible that
// they're doing more work than needed for other subclasses. If you realize that's the case, feel
// free to "trim" the logic here and move it to ActivityStarterTests.
/**
 * Base class for all {@link ActivityStarter} tests.
 */
@RunWith(WindowTestRunner.class)
abstract class ActivityStarterTestBase extends WindowTestsBase {

    @Rule
    public final TestName name = new TestName();

    protected final DeviceConfigStateHelper mDeviceConfig = new DeviceConfigStateHelper(
            DeviceConfig.NAMESPACE_WINDOW_MANAGER);

    protected ActivityStartController mController;
    protected ActivityMetricsLogger mActivityMetricsLogger;
    protected PackageManagerInternal mMockPackageManager;
    protected final UserManagerInternal mMockUmi = mock(UserManagerInternal.class);
    protected final UserHelper mMockUserHelper = mock(UserHelper.class);
    protected AppOpsManager mAppOpsManager;

    @Before
    public final void setFixtures() throws Exception {
        mController = mock(ActivityStartController.class);
        BackgroundActivityStartController balController =
                new BackgroundActivityStartController(mAtm, mSupervisor);
        doReturn(balController).when(mAtm.mTaskSupervisor).getBackgroundActivityLaunchController();
        mActivityMetricsLogger = mAtm.mTaskSupervisor.getActivityMetricsLogger();
        spyOn(mActivityMetricsLogger);
        mAppOpsManager = mAtm.getAppOpsManager();
        doReturn(AppOpsManager.MODE_DEFAULT).when(mAppOpsManager).checkOpNoThrow(
                eq(AppOpsManager.OP_SYSTEM_EXEMPT_FROM_ACTIVITY_BG_START_RESTRICTION),
                anyInt(), any());
    }

    @After
    public final void shutdownDeviceConfig() throws Exception {
        mDeviceConfig.close();
    }

    protected final ActivityStarter prepareStarter(@Intent.Flags int launchFlags) {
        return prepareStarter(launchFlags, /* mockGetRootTask= */ true, LAUNCH_MULTIPLE);
    }

    protected final ActivityStarter prepareStarter(@Intent.Flags int launchFlags,
            boolean mockGetRootTask) {
        return prepareStarter(launchFlags, mockGetRootTask, LAUNCH_MULTIPLE);
    }

    /**
     * Creates a {@link ActivityStarter} with default parameters and necessary mocks.
     *
     * @param launchFlags The intent flags to launch activity.
     * @param mockGetRootTask Whether to mock {@link RootWindowContainer#getOrCreateRootTask} for
     *                           always launching to the testing stack. Set to false when allowing
     *                           the activity can be launched to any stack that is decided by real
     *                           implementation.
     * @return A {@link ActivityStarter} with default setup.
     */
    protected final ActivityStarter prepareStarter(@Intent.Flags int launchFlags,
            boolean mockGetRootTask, int launchMode) {
        return prepareStarter(launchFlags, mockGetRootTask, launchMode, mMockUserHelper);
    }

    /**
     * Same as {@link #prepareStarter(int, boolean, int)}, but explicitly setting the
     * {@code UserHelper} (instead of using {@link #mMockUserHelper}).
     */
    protected final ActivityStarter prepareStarter(@Intent.Flags int launchFlags,
            boolean mockGetRootTask, int launchMode, UserHelper userHelper) {

        // always allow test to start activity.
        doReturn(true).when(mSupervisor).checkStartAnyActivityPermission(
                any(), any(), any(), anyInt(), anyInt(), anyInt(), any(), any(),
                anyBoolean(), anyBoolean(), any(), any(), any());

        if (mockGetRootTask) {
            // Instrument the stack and task used.
            final Task stack = mRootWindowContainer.getDefaultTaskDisplayArea()
                    .createRootTask(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD,
                            true /* onTop */);

            // Direct starter to use spy stack.
            doReturn(stack).when(mRootWindowContainer)
                    .getOrCreateRootTask(any(), any(), any(), anyBoolean());
            doReturn(stack).when(mRootWindowContainer).getOrCreateRootTask(any(), any(), any(),
                    any(), anyBoolean(), any(), anyInt(), anyInt());
        }

        // Set up mock package manager internal and make sure no unmocked methods are called
        mMockPackageManager = mock(PackageManagerInternal.class,
                invocation -> {
                    throw new RuntimeException("Not stubbed");
                });
        doReturn(null).when(mMockPackageManager).getDefaultHomeActivity(anyInt());
        doReturn(mMockPackageManager).when(mAtm).getPackageManagerInternalLocked();
        doReturn(true).when(mMockPackageManager).isSameApp(anyString(), anyInt(), anyInt());
        doReturn("packageName").when(mMockPackageManager).getNameForUid(anyInt());
        doReturn(false).when(mMockPackageManager).isInstantAppInstallerComponent(any());
        doReturn(null).when(mMockPackageManager).resolveIntent(any(), any(), anyLong(), anyLong(),
                anyInt(), anyBoolean(), anyInt(), anyInt());
        doReturn(new ComponentName("", "")).when(mMockPackageManager).getSystemUiServiceComponent();

        // Never review permissions
        doReturn(false).when(mMockPackageManager).isPermissionsReviewRequired(any(), anyInt());
        doNothing().when(mMockPackageManager).grantImplicitAccess(
                anyInt(), any(), anyInt(), anyInt(), anyBoolean());
        doNothing().when(mMockPackageManager).notifyPackageUse(anyString(), anyInt());
        doReturn(mock(PackageArchiver.class)).when(mMockPackageManager).getPackageArchiver();

        final AndroidPackage mockPackage = mock(AndroidPackage.class);
        final SigningDetails signingDetails = mock(SigningDetails.class);
        doReturn(mockPackage).when(mMockPackageManager).getPackage(anyInt());
        doReturn(signingDetails).when(mockPackage).getSigningDetails();
        doReturn(false).when(signingDetails).hasAncestorOrSelfWithDigest(any());

        doReturn(mMockUmi).when(mAtm).getUserManagerInternal();
        doReturn(false).when(mMockPackageManager).isPackageAppLockEnabled(any(), anyInt());

        final Intent intent = new Intent();
        intent.addFlags(launchFlags);
        intent.setComponent(ActivityBuilder.getDefaultComponent());

        final ActivityInfo info = new ActivityInfo();

        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = ActivityBuilder.getDefaultComponent().getPackageName();
        info.launchMode = launchMode;

        return new ActivityStarter(mController, mAtm,
                mAtm.mTaskSupervisor, mock(ActivityStartInterceptor.class), userHelper)
                .setReason(name.getMethodName())
                .setIntent(intent)
                .setActivityInfo(info);
    }
}
