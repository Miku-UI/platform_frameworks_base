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

package android.content.pm

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInstaller.SessionInfo
import android.os.Binder
import android.os.IBinder
import android.os.UserHandle
import android.os.UserManager
import android.platform.test.annotations.Presubmit
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.verify
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

/**
 * Tests for [LauncherApps].
 *
 * Build/Install/Run:
 * atest FrameworksCoreTests:LauncherAppsTest
 */
@Presubmit
@RunWith(JUnit4::class)
class LauncherAppsTest {
    private val token: IBinder = Binder()
    private val packageManager: PackageManager = mock()
    private val userManager: UserManager = mock {
        on { isManagedProfile } doReturn false
    }
    private val context: Context = mock {
        on { activityToken } doReturn token
        on { getSystemServiceName(UserManager::class.java) } doReturn Context.USER_SERVICE
        on { getSystemService(Context.USER_SERVICE) } doReturn userManager
        on { packageManager } doReturn packageManager
        on { packageName } doReturn TEST_PACKAGE_NAME
    }
    private val service: ILauncherApps = mock()
    private val launcherApps = LauncherApps(context, service)

    @Test
    fun startMainActivity_propagatesCallerToken() {
        launcherApps.startMainActivity(
            TEST_COMPONENT,
            USER_HANDLE,
            null /* sourceBounds */,
            null /* opts */,
        )

        verify(service).startActivityAsUser(
            eq(token) /* callingActivityToken */,
            any() /* caller */,
            eq(TEST_PACKAGE_NAME),
            any() /* callingFeatureId */,
            eq(TEST_COMPONENT),
            any() /* sourceBounds */,
            any() /* opts */,
            eq(USER_HANDLE),
        )
    }

    @Test
    fun startPackageInstallerSessionDetailsActivity_propagatesCallerToken() {
        val sessionInfo = SessionInfo().apply { userId = USER_ID }

        launcherApps.startPackageInstallerSessionDetailsActivity(
            sessionInfo,
            null /* sourceBounds */,
            null /* opts */,
        )

        verify(service).startSessionDetailsActivityAsUser(
            eq(token) /* callingActivityToken */,
            any() /* caller */,
            eq(TEST_PACKAGE_NAME),
            any() /* callingFeatureId */,
            eq(sessionInfo),
            any() /* sourceBounds */,
            any() /* opts */,
            eq(USER_HANDLE),
        )
    }

    @Test
    fun startAppDetailsActivity_propagatesCallerToken() {
        launcherApps.startAppDetailsActivity(
            TEST_COMPONENT,
            USER_HANDLE,
            null /* sourceBounds */,
            null /* opts */,
        )

        verify(service).showAppDetailsAsUser(
            eq(token) /* callingActivityToken */,
            any() /* caller */,
            eq(TEST_PACKAGE_NAME),
            any() /* callingFeatureId */,
            eq(TEST_COMPONENT),
            any() /* sourceBounds */,
            any() /* opts */,
            eq(USER_HANDLE),
        )
    }

    @Test
    fun startShortcut_propagatesCallerToken() {
        service.stub {
            on {
                startShortcut(
                    anyOrNull() /* callerToken */,
                    anyString() /* callingPackage */,
                    anyString() /* packageName */,
                    anyOrNull() /* featureId */,
                    anyString() /* shortcutId */,
                    anyOrNull() /* sourceBounds */,
                    anyOrNull() /* startActivityOptions */,
                    anyInt() /* userId */,
                )
            } doReturn true
        }

        launcherApps.startShortcut(
            TEST_PACKAGE_NAME,
            TEST_SHORTCUT_ID,
            null /* sourceBounds */,
            null /* startActivityOptions */,
            USER_HANDLE,
        )

        verify(service).startShortcut(
            eq(token) /* callingActivityToken */,
            eq(TEST_PACKAGE_NAME),
            eq(TEST_PACKAGE_NAME),
            any() /* featureId */,
            eq(TEST_SHORTCUT_ID),
            any() /* sourceBounds */,
            any() /* startActivityOptions */,
            eq(USER_ID),
        )
    }

    companion object {
        private const val TEST_PACKAGE_NAME = "com.android.test"
        private const val TEST_ACTIVITY_NAME = "Activity"
        private const val USER_ID = 10
        private const val TEST_SHORTCUT_ID = "Test Shortcut"
        private val USER_HANDLE = UserHandle.of(USER_ID)
        private val TEST_COMPONENT = ComponentName(TEST_PACKAGE_NAME, TEST_ACTIVITY_NAME)
    }
}
