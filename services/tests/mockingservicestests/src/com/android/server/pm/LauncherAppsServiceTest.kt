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

package com.android.server.pm

import android.app.ActivityManagerInternal
import android.app.usage.UsageStatsManagerInternal
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.IOnAppsChangedListener
import android.content.pm.LauncherUserInfo
import android.content.pm.PackageInstaller.SessionInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManagerInternal
import android.content.pm.ParceledListSlice
import android.content.pm.ResolveInfo
import android.content.pm.ShortcutServiceInternal
import android.content.pm.UserInfo
import android.graphics.Rect
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.platform.app.InstrumentationRegistry
import com.android.server.LocalServices
import com.android.server.extendedtestutils.wheneverStatic
import com.android.server.testutils.mock
import com.android.server.wm.ActivityTaskManagerInternal
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.stub

/**
 * Build/Install/Run:
 * atest FrameworksMockingServicesTests:com.android.server.pm.LauncherAppsServiceTest
 */
@RunWith(TestParameterInjector::class)
class LauncherAppsServiceTest : PackageHelperTestBase() {
    @get:Rule
    val setFlagsRule = SetFlagsRule()

    private val context = InstrumentationRegistry.getInstrumentation().context

    private val mockUsmInternal: UsageStatsManagerInternal = mock()
    private val mockAmInternal: ActivityManagerInternal = mock()
    private val mockAtmInternal: ActivityTaskManagerInternal = mock()
    private val mockSsInternal: ShortcutServiceInternal = mock()
    private val mockPmInternal: PackageManagerInternal = mock()
    private val mockUserInfo: UserInfo = mock()

    private lateinit var launcherAppsService: TestLauncherAppsImpl
    private lateinit var mockUmInternal: UserManagerInternal

    @Before
    @Throws(Exception::class)
    override fun setup() {
        super.setup()

        mockUmInternal = rule.mocks().userManagerInternal
        mockUmInternal.stub {
            on { userInfos } doReturn arrayOf(mockUserInfo)
            on { getUserInfo(eq(TEST_USER_ID)) } doReturn mockUserInfo
        }

        wheneverStatic { LocalServices.getService(UserManagerInternal::class.java) }
            .thenReturn(mockUmInternal)
        wheneverStatic { LocalServices.getService(UsageStatsManagerInternal::class.java) }
            .thenReturn(mockUsmInternal)
        wheneverStatic { LocalServices.getService(ActivityManagerInternal::class.java) }
            .thenReturn(mockAmInternal)
        wheneverStatic { LocalServices.getService(ActivityTaskManagerInternal::class.java) }
            .thenReturn(mockAtmInternal)
        wheneverStatic { LocalServices.getService(ShortcutServiceInternal::class.java) }
            .thenReturn(mockSsInternal)
        wheneverStatic { LocalServices.getService(PackageManagerInternal::class.java) }
            .thenReturn(mockPmInternal)

        launcherAppsService = TestLauncherAppsImpl(context)
    }

    @Test
    @EnableFlags(android.security.Flags.FLAG_APP_LOCK_APIS)
    @Throws(Exception::class)
    fun getApplicationInfo_queriesAppLockInfoIfHasPermission(
        @TestParameter isLockAppsPermissionGranted: Boolean,
    ) {
        val requestedPackage = TEST_PACKAGE_2
        launcherAppsService.isLockAppsPermissionGranted = isLockAppsPermissionGranted

        launcherAppsService.getApplicationInfo(
            TEST_PACKAGE_1, requestedPackage, /* flags= */ 0, TEST_USER_HANDLE
        )

        verifyCapturedFlagsHaveGetAppLockInfoFlag(isLockAppsPermissionGranted) {
            verify(mockPmInternal).getApplicationInfo(
                eq(requestedPackage), capture(), anyInt(), eq(TEST_USER_ID)
            )
        }
    }

    @Test
    @DisableFlags(android.security.Flags.FLAG_APP_LOCK_APIS)
    @Throws(Exception::class)
    fun getApplicationInfo_withAppLockApisDisabled_doesNotQueryAppLockInfo(
        @TestParameter isLockAppsPermissionGranted: Boolean,
    ) {
        val requestedPackage = TEST_PACKAGE_2
        launcherAppsService.isLockAppsPermissionGranted = isLockAppsPermissionGranted

        launcherAppsService.getApplicationInfo(
            TEST_PACKAGE_1, requestedPackage, /* flags= */ 0, TEST_USER_HANDLE
        )

        verifyCapturedFlagsDoNotHaveGetAppLockInfoFlag {
            verify(mockPmInternal).getApplicationInfo(
                eq(requestedPackage), capture(), anyInt(), eq(TEST_USER_ID)
            )
        }
    }

    @Test
    @EnableFlags(android.security.Flags.FLAG_APP_LOCK_APIS)
    @Throws(Exception::class)
    fun resolveLauncherActivityInternal_queriesAppLockInfoIfHasPermission(
        @TestParameter isLockAppsPermissionGranted: Boolean,
    ) {
        val testComponent = ComponentName(context, LauncherAppsServiceTest::class.java)
        launcherAppsService.isLockAppsPermissionGranted = isLockAppsPermissionGranted

        launcherAppsService.resolveLauncherActivityInternal(
            TEST_PACKAGE_1, testComponent, TEST_USER_HANDLE
        )

        verifyCapturedFlagsHaveGetAppLockInfoFlag(isLockAppsPermissionGranted) {
            verify(mockPmInternal).getActivityInfo(
                eq(testComponent), capture(), anyInt(), eq(TEST_USER_ID)
            )
        }
    }

    @Test
    @DisableFlags(android.security.Flags.FLAG_APP_LOCK_APIS)
    @Throws(Exception::class)
    fun resolveLauncherActivityInternal_withAppLockApisDisabled_doesNotQueryAppLockInfo() {
        val testComponent = ComponentName(context, LauncherAppsServiceTest::class.java)

        launcherAppsService.resolveLauncherActivityInternal(
            TEST_PACKAGE_1, testComponent, TEST_USER_HANDLE
        )

        verifyCapturedFlagsDoNotHaveGetAppLockInfoFlag {
            verify(mockPmInternal).getActivityInfo(
                eq(testComponent), capture(), anyInt(), eq(TEST_USER_ID)
            )
        }
    }

    @Test
    @EnableFlags(android.security.Flags.FLAG_APP_LOCK_APIS)
    fun getApplicationInfoListForAllArchivedApps_queriesAppLockInfoIfHasPermission(
        @TestParameter isLockAppsPermissionGranted: Boolean,
    ) {
        launcherAppsService.isLockAppsPermissionGranted = isLockAppsPermissionGranted

        launcherAppsService.getApplicationInfoListForAllArchivedApps(TEST_USER_HANDLE)

        verifyCapturedFlagsHaveGetAppLockInfoFlag(isLockAppsPermissionGranted) {
            verify(mockPmInternal).getInstalledApplicationsCrossUser(
                capture(), eq(TEST_USER_ID), anyInt()
            )
        }
    }

    @Test
    @DisableFlags(android.security.Flags.FLAG_APP_LOCK_APIS)
    fun getApplicationInfoListForAllArchivedApps_withAppLockApisDisabled_doesNotQueryAppLockInfo() {
        launcherAppsService.getApplicationInfoListForAllArchivedApps(TEST_USER_HANDLE)

        verifyCapturedFlagsDoNotHaveGetAppLockInfoFlag {
            verify(mockPmInternal).getInstalledApplicationsCrossUser(
                capture(), eq(TEST_USER_ID), anyInt()
            )
        }
    }

    @Test
    @EnableFlags(android.security.Flags.FLAG_APP_LOCK_APIS)
    fun getApplicationInfoForArchivedApp_queriesAppLockInfoIfHasPermission(
        @TestParameter isLockAppsPermissionGranted: Boolean,
    ) {
        launcherAppsService.isLockAppsPermissionGranted = isLockAppsPermissionGranted

        launcherAppsService.getApplicationInfoForArchivedApp(TEST_PACKAGE_1, TEST_USER_HANDLE)

        verifyCapturedFlagsHaveGetAppLockInfoFlag(isLockAppsPermissionGranted) {
            verify(mockPmInternal).getApplicationInfo(
                eq(TEST_PACKAGE_1), capture(), anyInt(), eq(TEST_USER_ID)
            )
        }
    }

    @Test
    @DisableFlags(android.security.Flags.FLAG_APP_LOCK_APIS)
    fun getApplicationInfoForArchivedApp_withAppLockApisDisabled_doesNotQueryAppLockInfo() {
        launcherAppsService.getApplicationInfoForArchivedApp(TEST_PACKAGE_1, TEST_USER_HANDLE)

        verifyCapturedFlagsDoNotHaveGetAppLockInfoFlag {
            verify(mockPmInternal).getApplicationInfo(
                eq(TEST_PACKAGE_1), capture(), anyInt(), eq(TEST_USER_ID)
            )
        }
    }

    @Test
    @EnableFlags(android.security.Flags.FLAG_APP_LOCK_APIS)
    fun queryIntentLauncherActivities_queriesAppLockInfoIfHasPermission(
        @TestParameter
        isLockAppsPermissionGranted: Boolean
    ) {
        launcherAppsService.isLockAppsPermissionGranted = isLockAppsPermissionGranted
        val testIntent = Intent()

        launcherAppsService.queryIntentLauncherActivities(
            testIntent, TEST_CALLING_UID, TEST_USER_HANDLE
        )

        verifyCapturedFlagsHaveGetAppLockInfoFlag(isLockAppsPermissionGranted) {
            verify(mockPmInternal).queryIntentActivities(
                eq(testIntent), any(), capture(), eq(TEST_CALLING_UID), eq(TEST_USER_ID)
            )
        }
    }

    @Test
    @DisableFlags(android.security.Flags.FLAG_APP_LOCK_APIS)
    fun queryIntentLauncherActivities_withAppLockApisDisabled_doesNotQueryAppLockInfo() {
        val testIntent = Intent()

        launcherAppsService.queryIntentLauncherActivities(
            testIntent, TEST_CALLING_UID, TEST_USER_HANDLE
        )

        verifyCapturedFlagsDoNotHaveGetAppLockInfoFlag {
            verify(mockPmInternal).queryIntentActivities(
                eq(testIntent), any(), capture(), eq(TEST_CALLING_UID), eq(TEST_USER_ID)
            )
        }
    }


    @Test
    @EnableFlags(android.security.Flags.FLAG_APP_LOCK_APIS)
    @Throws(Exception::class)
    fun getLauncherActivities_queriesAppLockInfoIfHasPermission(
        @TestParameter specificPackageProvided: Boolean,
        @TestParameter isLockAppsPermissionGranted: Boolean,
    ) {
        testGetLauncherActivities_queriesAppLockInfoIfAppLockApisEnabledAndPermissionGranted(
            appLockApisEnabled = true, isLockAppsPermissionGranted, specificPackageProvided
        )
    }

    @Test
    @DisableFlags(android.security.Flags.FLAG_APP_LOCK_APIS)
    @Throws(Exception::class)
    fun getLauncherActivities_withAppLockApisDisabled_doesNotQueryAppLockInfo(
        @TestParameter specificPackageProvided: Boolean,
        @TestParameter isLockAppsPermissionGranted: Boolean,
    ) {
        testGetLauncherActivities_queriesAppLockInfoIfAppLockApisEnabledAndPermissionGranted(
            appLockApisEnabled = false, isLockAppsPermissionGranted, specificPackageProvided
        )
    }

    private fun testGetLauncherActivities_queriesAppLockInfoIfAppLockApisEnabledAndPermissionGranted(
        appLockApisEnabled: Boolean,
        @TestParameter isLockAppsPermissionGranted: Boolean,
        specificPackageProvided: Boolean,
    ) {
        if (specificPackageProvided) {
            verifyGetLauncherActivitiesForSpecificPackage(
                appLockApisEnabled,
                isLockAppsPermissionGranted
            )
        } else {
            verifyGetLauncherActivitiesForAllPackages(
                appLockApisEnabled,
                isLockAppsPermissionGranted
            )
        }
    }

    private fun verifyGetLauncherActivitiesForSpecificPackage(
        appLockApisEnabled: Boolean,
        isLockAppsPermissionGranted: Boolean,
    ) {
        val requestedPackage = TEST_PACKAGE_2
        launcherAppsService.isLockAppsPermissionGranted = isLockAppsPermissionGranted
        mockPackageVisibilityAndProfileAccess(
            TEST_USER_ID, requestedPackage, isProfileAccessible = true, isPackageFiltered = false
        )

        launcherAppsService.getLauncherActivities(
            TEST_PACKAGE_1, requestedPackage, TEST_USER_HANDLE
        )

        // The service first queries for launcher activities.
        verifyCapturedFlagsForAppLockInfoFlag(appLockApisEnabled, isLockAppsPermissionGranted) {
            verify(mockPmInternal).queryIntentActivities(
                any(), any(), capture(), anyInt(), eq(TEST_USER_ID)
            )
        }

        // It also queries ApplicationInfo for hidden app logic.
        verifyCapturedFlagsForAppLockInfoFlag(appLockApisEnabled, isLockAppsPermissionGranted) {
            verify(mockPmInternal, times(2)).getApplicationInfo(
                eq(requestedPackage), capture(), anyInt(), eq(TEST_USER_ID)
            )
        }
    }

    private fun verifyGetLauncherActivitiesForAllPackages(
        appLockApisEnabled: Boolean,
        isLockAppsPermissionGranted: Boolean,
    ) {
        launcherAppsService.isLockAppsPermissionGranted = isLockAppsPermissionGranted
        launcherAppsService.getLauncherActivities(
            TEST_PACKAGE_1, /* packageName= */ null, TEST_USER_HANDLE
        )

        verifyCapturedFlagsForAppLockInfoFlag(appLockApisEnabled, isLockAppsPermissionGranted) {
            verify(mockPmInternal).getInstalledApplications(
                capture(), eq(TEST_USER_ID), anyInt()
            )
        }
    }

    @Test
    @EnableFlags(android.security.Flags.FLAG_APP_LOCK_APIS)
    @Throws(Exception::class)
    fun onPackageChanged_respectsVisibilityAndProfileAccessForAppLockState(
        @TestParameter isProfileAccessible: Boolean,
        @TestParameter isPackageFiltered: Boolean,
        @TestParameter appLockEnabled: Boolean
    ) {
        testOnPackageChanged_respectsVisibilityAndProfileAccessForAppLockState(
            appLockApisEnabled = true, isProfileAccessible, isPackageFiltered, appLockEnabled
        )
    }

    @Test
    @DisableFlags(android.security.Flags.FLAG_APP_LOCK_APIS)
    @Throws(Exception::class)
    fun onPackageChanged_withAppLockApisDisabled_doesNotTriggerListenerForAppLockState(
        @TestParameter isProfileAccessible: Boolean,
        @TestParameter isPackageFiltered: Boolean,
        @TestParameter appLockEnabled: Boolean
    ) {
        testOnPackageChanged_respectsVisibilityAndProfileAccessForAppLockState(
            appLockApisEnabled = false, isProfileAccessible, isPackageFiltered, appLockEnabled
        )
    }

    private fun testOnPackageChanged_respectsVisibilityAndProfileAccessForAppLockState(
        appLockApisEnabled: Boolean,
        isProfileAccessible: Boolean,
        isPackageFiltered: Boolean,
        appLockEnabled: Boolean
    ) {
        val callingPackage = TEST_PACKAGE_1
        val changedPackage = TEST_PACKAGE_2
        val changedUserId = TEST_USER_ID
        val onPackageChangedLatch = CountDownLatch(1)
        val listener = TestOnAppsChangedListener(onPackageChangedLatch)
        val spyPackageMonitor = spy(launcherAppsService.mPackageMonitor)
        spyPackageMonitor.stub {
            on { changingUserId } doReturn changedUserId
        }
        mockPackageVisibilityAndProfileAccess(
            changedUserId, changedPackage, isProfileAccessible, isPackageFiltered
        )
        launcherAppsService.addOnAppsChangedListener(callingPackage, listener)

        if (appLockEnabled) {
            spyPackageMonitor.onPackageAppLockEnabled(changedPackage)
        } else {
            spyPackageMonitor.onPackageAppLockDisabled(changedPackage)
        }

        val onPackageChangedTriggered =
            onPackageChangedLatch.await(PACKAGE_CALLBACK_LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)

        if (appLockApisEnabled && isProfileAccessible && !isPackageFiltered) {
            assertPackageChangedTriggered(
                onPackageChangedTriggered, listener, changedUserId, changedPackage
            )
        } else {
            assertPackageChangedNotTriggered(onPackageChangedTriggered, listener)
        }

        // Clean up.
        launcherAppsService.removeOnAppsChangedListener(listener)
    }

    @Test
    fun startShortcut_passesCallingActivityTokenToAtm() {
        mockPinnedShortcut(TEST_USER_ID, TEST_PACKAGE_2)
        val callingActivityToken = Binder()

        val result = launcherAppsService.startShortcut(
            callingActivityToken,
            TEST_PACKAGE_1, /* callingPackage */
            TEST_PACKAGE_2, /* packageName */
            null, /* featureId */
            TEST_SHORTCUT_ID,
            null, /* sourceBounds */
            null, /* startActivityOptions */
            TEST_USER_ID, /* targetUserId */
        )

        assertThat(result).isTrue()
        verify(mockAtmInternal).startActivitiesAsPackage(
            eq(callingActivityToken), /* callingActivityToken */
            eq(TEST_PACKAGE_2), /* packageName */
            isNull(), /* featureId */
            eq(TEST_USER_ID),
            any(), /* intents */
            any(), /* bOptions */
        )
    }

    @Test
    fun startActivityAsUser_passesCallingActivityTokenToAtm() {
        val component = ComponentName(TEST_PACKAGE_2, TEST_ACTIVITY)
        mockLaunchableActivity(component)
        val callingActivityToken = Binder()

        launcherAppsService.startActivityAsUser(
            callingActivityToken,
            context.iApplicationThread,
            TEST_PACKAGE_1, /* callingPackage */
            null, /* callingFeatureId */
            component,
            Rect(0, 0, 100, 100), /* sourceBounds */
            Bundle(), /* opts */
            TEST_USER_HANDLE, /* user */
        )

        verify(mockAtmInternal).startActivityAsUser(
            eq(context.iApplicationThread),
            eq(TEST_PACKAGE_1), /* callingPackage */
            isNull(), /* callingFeatureId */
            any(), /* intent */
            eq(callingActivityToken), /* resultTo */
            anyInt(), /* flags */
            any(), /* bOptions */
            eq(TEST_USER_ID),
        )
    }

    @Test
    fun startSessionDetailsActivityAsUser_passesCallingActivityTokenToAtm() {
        val callingActivityToken = Binder()

        launcherAppsService.startSessionDetailsActivityAsUser(
            callingActivityToken,
            context.iApplicationThread,
            TEST_PACKAGE_1, /* callingPackage */
            null, /* callingFeatureId */
            SessionInfo(), /* sessionInfo */
            Rect(0, 0, 100, 100), /* sourceBounds */
            Bundle(), /* opts */
            TEST_USER_HANDLE,
        )

        verify(mockAtmInternal).startActivityAsUser(
            eq(context.iApplicationThread),
            eq(TEST_PACKAGE_1), /* callingPackage */
            isNull(), /* callingFeatureId */
            any(), /* intent */
            eq(callingActivityToken), /* resultTo */
            anyInt(), /* flags */
            any(), /* bOptions */
            eq(TEST_USER_ID),
        )
    }

    @Test
    fun showAppDetailsAsUser_passesCallingActivityTokenToAtm() {
        val component = ComponentName(TEST_PACKAGE_2, TEST_ACTIVITY)
        mockPackageVisibilityAndProfileAccess(
            userId = TEST_USER_ID,
            packageName = TEST_PACKAGE_2,
            isProfileAccessible = true,
            isPackageFiltered = false,
        )
        val callingActivityToken = Binder()

        launcherAppsService.showAppDetailsAsUser(
            callingActivityToken,
            mock(), /* caller */
            TEST_PACKAGE_1, /* callingPackage */
            null, /* callingFeatureId */
            component,
            null, /* sourceBounds */
            null, /* opts */
            TEST_USER_HANDLE,
        )

        verify(mockAtmInternal).startActivityAsUser(
            any(), /* caller */
            eq(TEST_PACKAGE_1), /* callingPackage */
            isNull(), /* callingFeatureId */
            any(), /* intent */
            eq(callingActivityToken), /* resultTo */
            anyInt(), /* startFlags */
            any(), /* options */
            eq(TEST_USER_ID),
        )
    }

    private fun verifyCapturedFlagsHaveGetAppLockInfoFlag(
        appLocksPermissionGranted: Boolean,
        verification: KArgumentCaptor<Long>.() -> Unit,
    ) {
        verifyCapturedFlagsForAppLockInfoFlag(
            appLockApisEnabled = true,
            appLocksPermissionGranted,
            verification
        )
    }

    private fun verifyCapturedFlagsDoNotHaveGetAppLockInfoFlag(
        verification: KArgumentCaptor<Long>.() -> Unit
    ) {
        verifyCapturedFlagsForAppLockInfoFlag(
            appLockApisEnabled = false,
            appLocksPermissionGranted = true,
            verification
        )
    }

    private fun verifyCapturedFlagsForAppLockInfoFlag(
        appLockApisEnabled: Boolean,
        appLocksPermissionGranted: Boolean,
        verification: KArgumentCaptor<Long>.() -> Unit,
    ) {
        val flagsArgCaptor = argumentCaptor<Long>()
        verification(flagsArgCaptor)
        val expectedFlags = PackageManager.GET_APP_LOCK_INFO
        for (capturedFlags in flagsArgCaptor.allValues) {
            if (appLockApisEnabled && appLocksPermissionGranted) {
                assertThat(capturedFlags and expectedFlags).isEqualTo(expectedFlags)
            } else {
                assertThat(capturedFlags and expectedFlags).isEqualTo(0L)
            }
        }
    }

    private fun assertPackageChangedTriggered(
        wasTriggered: Boolean,
        listener: TestOnAppsChangedListener,
        expectedUserId: Int,
        expectedPackageName: String
    ) {
        assertThat(wasTriggered).isTrue()
        assertThat(listener.changedUser?.identifier).isEqualTo(expectedUserId)
        assertThat(listener.changedPackageName).isEqualTo(expectedPackageName)
    }

    private fun assertPackageChangedNotTriggered(
        wasTriggered: Boolean,
        listener: TestOnAppsChangedListener
    ) {
        assertThat(wasTriggered).isFalse()
        assertThat(listener.changedUser).isNull()
        assertThat(listener.changedPackageName).isNull()
    }

    private fun mockPinnedShortcut(targetUserId: Int, packageName: String) {
        mockSsInternal.stub {
            on {
                isPinnedByCaller(
                    anyInt(),
                    any(),
                    eq(packageName),
                    any(),
                    eq(targetUserId),
                )
            } doReturn true
            on {
                createShortcutIntents(
                    anyInt(),
                    any(),
                    eq(packageName),
                    any(),
                    eq(targetUserId),
                    anyInt(),
                    anyInt(),
                )
            } doReturn arrayOf(Intent())
        }
    }

    private fun mockLaunchableActivity(component: ComponentName) {
        val activityInfo = ActivityInfo().apply {
            exported = true
            name = component.className
            packageName = component.packageName
        }
        val resolvedApp = ResolveInfo().apply { this.activityInfo = activityInfo }
        mockPmInternal.stub {
            on {
                queryIntentActivities(
                    any(),
                    anyOrNull(),
                    anyLong(),
                    anyInt(),
                    anyInt(),
                )
            } doReturn listOf(resolvedApp)
        }
    }

    private fun mockPackageVisibilityAndProfileAccess(
        userId: Int, packageName: String, isProfileAccessible: Boolean, isPackageFiltered: Boolean
    ) {
        mockUmInternal.stub {
            on {
                isProfileAccessible(
                    anyInt(), /* callingUserId */
                    eq(userId),
                    anyString(), /* debugMsg */
                    anyBoolean(), /* throwSecurityException */
                )
            } doReturn isProfileAccessible
        }
        mockPmInternal.stub {
            on {
                filterAppAccess(
                    eq(packageName),
                    anyInt(), /* callingUserId */
                    eq(userId),
                    eq(false), /* filterUninstalled */
                )
            } doReturn isPackageFiltered
        }
    }

    private class TestOnAppsChangedListener(val onPackageChangedLatch: CountDownLatch) :
        IOnAppsChangedListener {
        var changedUser: UserHandle? = null
        var changedPackageName: String? = null

        @Throws(RemoteException::class)
        override fun onPackageChanged(user: UserHandle?, packageName: String?) {
            changedUser = user
            changedPackageName = packageName
            onPackageChangedLatch.countDown()
        }

        // Other oneway methods are empty for this test's purpose.
        @Throws(RemoteException::class)
        override fun onPackageRemoved(user: UserHandle?, packageName: String?) {
        }

        @Throws(RemoteException::class)
        override fun onPackageAdded(user: UserHandle?, packageName: String?) {
        }

        @Throws(RemoteException::class)
        override fun onPackagesAvailable(
            user: UserHandle?, packageNames: Array<String?>?, replacing: Boolean
        ) {
        }

        @Throws(RemoteException::class)
        override fun onPackagesUnavailable(
            user: UserHandle?, packageNames: Array<String?>?, replacing: Boolean
        ) {
        }

        @Throws(RemoteException::class)
        override fun onPackagesSuspended(
            user: UserHandle?, packageNames: Array<String?>?, launcherExtras: Bundle?
        ) {
        }

        @Throws(RemoteException::class)
        override fun onPackagesUnsuspended(user: UserHandle?, packageNames: Array<String?>?) {
        }

        @Throws(RemoteException::class)
        override fun onShortcutChanged(
            user: UserHandle?, packageName: String?, shortcuts: ParceledListSlice<*>?
        ) {
        }

        @Throws(RemoteException::class)
        override fun onPackageLoadingProgressChanged(
            user: UserHandle?, packageName: String?, progress: Float
        ) {
        }

        @Throws(RemoteException::class)
        override fun onUserConfigChanged(launcherUserInfo: LauncherUserInfo?) {
        }

        override fun asBinder(): IBinder? {
            return Binder()
        }
    }

    private class TestLauncherAppsImpl(context: Context) : LauncherAppsService.LauncherAppsImpl(
        context
    ) {
        var isLockAppsPermissionGranted = false

        override fun verifyCallingPackage(callingPackage: String?, callerUid: Int) {
            // Skip package verification for tests.
        }

        override fun hasLockAppsPermission(callingPid: Int, callingUid: Int): Boolean {
            return isLockAppsPermissionGranted
        }
    }

    companion object {
        private const val PACKAGE_CALLBACK_LATCH_TIMEOUT_MS = 500L
        private const val TEST_CALLING_UID = 12345
        private const val TEST_ACTIVITY = "TestActivity"
        private const val TEST_SHORTCUT_ID = "Test Shortcut"
        private val TEST_USER_HANDLE = UserHandle.of(TEST_USER_ID)
    }
}
