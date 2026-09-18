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

import android.app.supervision.SupervisionManagerInternal
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import android.content.pm.UserInfo
import android.os.Binder
import android.os.Process
import android.os.UserHandle
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.security.Flags
import android.util.ArrayMap
import android.util.ArraySet
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.internal.pm.parsing.pkg.AndroidPackageInternal
import com.android.internal.pm.pkg.component.ParsedActivity
import com.android.internal.pm.pkg.component.ParsedActivityImpl
import com.android.internal.pm.pkg.component.ParsedIntentInfoImpl
import com.android.server.LocalServices
import com.android.server.extendedtestutils.wheneverStatic
import com.android.server.pm.pkg.PackageStateInternal
import com.android.server.pm.pkg.PackageUserStateInternal
import com.android.server.testutils.mock
import com.android.server.testutils.whenever
import com.google.common.truth.Truth.assertThat
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider
import java.util.function.Consumer
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

/**
 * Build/Install/Run:
 * atest FrameworksMockingServicesTests:com.android.server.pm.AppLockPackageHelperTest
 */
@RunWith(TestParameterInjector::class)
class AppLockPackageHelperTest : PackageHelperTestBase() {

    @get:Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    private lateinit var appLockPackageHelper: AppLockPackageHelper
    private val testInjector = TestInjector()
    private val testLauncherIntentFilter =
        IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
    private val testLauncherIntentFilters: Array<IntentFilter> = arrayOf(testLauncherIntentFilter)
    private val activities =
        listOf<ParsedActivity>(createActivity(TEST_PACKAGE_NAME, testLauncherIntentFilters))
    private val mockSnapshot: Computer = mock()
    private val mockPackageStateInternal1: PackageStateInternal = mock()
    private val mockPackageStateInternal2: PackageStateInternal = mock()
    private val packageStates = ArrayMap<String, PackageStateInternal>()
    private val mockPackageUserStateInternal1: PackageUserStateInternal = mock()
    private val mockPackageUserStateInternal2: PackageUserStateInternal = mock()
    private val mockAndroidPackage: AndroidPackageInternal = mock()
    private val mockAndroidPackage2: AndroidPackageInternal = mock()
    private var availableFeatures = ArrayMap<String, FeatureInfo>(1)

    val userInfo: UserInfo = mock()
    val smInternal: SupervisionManagerInternal = mock()

    @Before
    @Throws(Exception::class)
    override fun setup() {
        super.setup()

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation
            .adoptShellPermissionIdentity("android.permission.GET_INTENT_SENDER_INTENT")

        appLockPackageHelper =
            AppLockPackageHelper(instrumentation.context, pms, broadcastHelper, testInjector)
        whenever(mockSnapshot.getPackageStateInternal(eq(EXEMPT_PACKAGE_NAME)))
            .thenReturn(mockPackageStateInternal1)
        whenever(mockSnapshot.getPackageStateInternal(eq(TEST_PACKAGE_NAME)))
            .thenReturn(mockPackageStateInternal1)
        packageStates.put(TEST_PACKAGE_NAME, mockPackageStateInternal1)
        packageStates.put(TEST_PACKAGE_NAME_2, mockPackageStateInternal2)
        whenever(mockSnapshot.packageStates).thenReturn(packageStates)
        whenever(mockPackageStateInternal1.getUserStateOrDefault(
            eq(TEST_USER_ID_1))).thenReturn(mockPackageUserStateInternal1)
        whenever(mockPackageStateInternal2.getUserStateOrDefault(
            eq(TEST_USER_ID_1))).thenReturn(mockPackageUserStateInternal2)

        whenever(mockPackageStateInternal1.pkg).thenReturn(mockAndroidPackage)
        whenever(mockPackageStateInternal2.pkg).thenReturn(mockAndroidPackage2)
        whenever(mockAndroidPackage.packageName).thenReturn(TEST_PACKAGE_NAME)
        whenever(mockAndroidPackage2.packageName).thenReturn(TEST_PACKAGE_NAME_2)
        whenever(mockAndroidPackage.activities).thenReturn(activities)
        whenever(rule.mocks().systemConfig.appLockExemptPackages).thenReturn(
            ArraySet<String>().apply { add(EXEMPT_PACKAGE_NAME) })
        whenever(rule.mocks().systemConfig.availableFeatures).thenReturn(availableFeatures)
        whenever(userInfo.isFull).thenReturn(true)
        whenever(smInternal.isSupervisionEnabledForUser(anyInt())).thenReturn(false)
        whenever(rule.mocks().userManagerInternal.getUserInfo(TEST_USER_ID_1))
            .thenReturn(userInfo)
        wheneverStatic { LocalServices.getService(UserManagerInternal::class.java) }
            .thenReturn(rule.mocks().userManagerInternal)
        wheneverStatic { LocalServices.getService(SupervisionManagerInternal::class.java) }
            .thenReturn(smInternal)
    }

    @After
    fun tearDown() {
        availableFeatures.clear()
    }

    @Test
    fun getAppLockEnabledPackages_returnsAppLockEnabledPackages() {
        whenever(mockPackageUserStateInternal1.isAppLockEnabled).thenReturn(true)
        whenever(mockPackageUserStateInternal2.isAppLockEnabled).thenReturn(false)

        assertThat(
            appLockPackageHelper.getAppLockEnabledPackages(
                mockSnapshot,
                TEST_USER_ID_1
            )
        ).containsExactly(TEST_PACKAGE_NAME)
    }

    @Test
    @Throws(Exception::class)
    fun setPackageAppLockEnabled_systemConfigExemptApp(
        @TestParameter currentEnabledState: Boolean,
        @TestParameter newEnabledState: Boolean,
    ) {
        setupTestForSetPackageAppLockEnabled(
            deviceSecure = true,
            currentEnabledState
        )

        assertThat(
            appLockPackageHelper.setPackageAppLockEnabled(
                this::mockSnapshot,
                EXEMPT_PACKAGE_NAME,
                TEST_USER_ID_1,
                newEnabledState,
                Process.SYSTEM_UID,
                Binder.getCallingPid(),
            )
        ).isEqualTo(!newEnabledState)
        // Disk write occurs only if the current state was true, as exempt apps cannot have App Lock
        // enabled.
        if (currentEnabledState) {
            verifyDiskWriteAndPackageUpdate(stateToWrite = false, EXEMPT_PACKAGE_NAME)
        } else {
            verifyNoWriteOrPackageUpdate()
        }
    }

    @Test
    @Throws(Exception::class)
    fun setPackageAppLockEnabled_headlessApp(
        @TestParameter currentEnabledState: Boolean,
        @TestParameter newEnabledState: Boolean,
    ) {
        whenever(mockAndroidPackage.activities).thenReturn(
            listOf<ParsedActivity?>()
        )
        setupTestForSetPackageAppLockEnabled(
            deviceSecure = true,
            currentEnabledState
        )

        assertThat(
            appLockPackageHelper.setPackageAppLockEnabled(
                this::mockSnapshot,
                TEST_PACKAGE_NAME,
                TEST_USER_ID_1,
                newEnabledState,
                Process.SYSTEM_UID,
                        Binder.getCallingPid(),
            )
        ).isEqualTo(!newEnabledState)
        // Disk write occurs only if the current state was true, as headless apps cannot have App
        // Lock enabled.
        if (currentEnabledState) {
            verifyDiskWriteAndPackageUpdate(stateToWrite = false)
        } else {
            verifyNoWriteOrPackageUpdate()
        }
    }

    @Test
    @Throws(Exception::class)
    fun setPackageAppLockEnabled_noUser_false(
        @TestParameter currentEnabledState: Boolean,
        @TestParameter newEnabledState: Boolean,
    ) {
        whenever(rule.mocks().userManagerInternal.getUserInfo(TEST_USER_ID_1))
            .thenReturn(null)
        setupTestForSetPackageAppLockEnabled(
            deviceSecure = true,
            currentEnabledState
        )

        assertThat(
            appLockPackageHelper.setPackageAppLockEnabled(
                this::mockSnapshot,
                TEST_PACKAGE_NAME,
                TEST_USER_ID_1,
                newEnabledState,
                Process.SYSTEM_UID,
                Binder.getCallingPid(),
            )
        ).isEqualTo(!newEnabledState)
        // Disk write occurs only if the current state was true, as profile users cannot have App
        // Lock enabled.
        if (currentEnabledState) {
            verifyDiskWriteAndPackageUpdate(stateToWrite = false)
        } else {
            verifyNoWriteOrPackageUpdate()
        }
    }

    @Test
    @Throws(Exception::class)
    fun setPackageAppLockEnabled_profileUser(
        @TestParameter currentEnabledState: Boolean,
        @TestParameter newEnabledState: Boolean,
    ) {
        whenever(userInfo.isFull).thenReturn(false)
        setupTestForSetPackageAppLockEnabled(
            deviceSecure = true,
            currentEnabledState
        )

        assertThat(
            appLockPackageHelper.setPackageAppLockEnabled(
                this::mockSnapshot,
                TEST_PACKAGE_NAME,
                TEST_USER_ID_1,
                newEnabledState,
                Process.SYSTEM_UID,
                Binder.getCallingPid(),
            )
        ).isEqualTo(!newEnabledState)
        // Disk write occurs only if the current state was true, as profile users cannot have App
        // Lock enabled.
        if (currentEnabledState) {
            verifyDiskWriteAndPackageUpdate(stateToWrite = false, TEST_PACKAGE_NAME)
        } else {
            verifyNoWriteOrPackageUpdate()
        }
    }

    @Test
    @Throws(Exception::class)
    fun setPackageAppLockEnabled_supervisedUser(
        @TestParameter currentEnabledState: Boolean,
        @TestParameter newEnabledState: Boolean,
    ) {
        whenever(smInternal.isSupervisionEnabledForUser(eq(TEST_USER_ID_1)))
            .thenReturn(true)
        setupTestForSetPackageAppLockEnabled(
            deviceSecure = true,
            currentEnabledState
        )

        assertThat(
            appLockPackageHelper.setPackageAppLockEnabled(
                this::mockSnapshot,
                TEST_PACKAGE_NAME,
                TEST_USER_ID_1,
                newEnabledState,
                Process.SYSTEM_UID,
                Binder.getCallingPid(),
            )
        ).isEqualTo(!newEnabledState)
        // Disk write occurs only if the current state was true, as supervised users cannot have App
        // Lock enabled.
        if (currentEnabledState) {
            verifyDiskWriteAndPackageUpdate(stateToWrite = false)
        } else {
            verifyNoWriteOrPackageUpdate()
        }
    }

    @Test
    @Throws(Exception::class)
    fun setPackageAppLockEnabled_deviceNotSecure_false(
        @TestParameter currentEnabledState: Boolean,
        @TestParameter newEnabledState: Boolean,
    ) {
        setupTestForSetPackageAppLockEnabled(
            deviceSecure = false,
            currentEnabledState
        )

        assertThat(
            appLockPackageHelper.setPackageAppLockEnabled(
                this::mockSnapshot,
                TEST_PACKAGE_NAME,
                TEST_USER_ID_1,
                newEnabledState,
                Process.SYSTEM_UID,
                Binder.getCallingPid(),
            )
        ).isEqualTo(!newEnabledState)
        // Disk write occurs only if the current state was true, as App Lock is not supported on
        // insecure devices.
        if (currentEnabledState) {
            verifyDiskWriteAndPackageUpdate(stateToWrite = false)
        } else {
            verifyNoWriteOrPackageUpdate()
        }
    }

    @Test
    @Throws(Exception::class)
    fun setPackageAppLockEnabled_unauthorizedUidNoPermission_throwsSecurityException_noDiskWrite(
        @TestParameter currentEnabledState: Boolean,
        @TestParameter newEnabledState: Boolean,
        @TestParameter(valuesProvider = UnauthorizedUidProvider::class) callingUid: Int,
    ) {
        setupTestForSetPackageAppLockEnabled(
            deviceSecure = true,
            currentEnabledState
        )

        assertFailsWith<SecurityException> {
            appLockPackageHelper.setPackageAppLockEnabled(
                this::mockSnapshot,
                TEST_PACKAGE_NAME,
                TEST_USER_ID_1,
                newEnabledState,
                callingUid,
                Binder.getCallingPid(),
            )
        }
        verifyNoWriteOrPackageUpdate()
    }

    // @RequiresFlagsEnabled must be used instead of @EnableFlags here because TEST_LOCK_APPS,
    // which is gated by the flag, is a signature permission, and thus cannot be granted at runtime.
    // @RequiresFlagsEnabled therefore ensures this only runs on devices with the flag enabled.
    @Test
    @Throws(Exception::class)
    @RequiresFlagsEnabled(Flags.FLAG_APP_LOCK_APIS)
    fun setPackageAppLockEnabled_unauthorizedUidWithTestPermission_noSecurityException_success() {
        val currentEnabledState = false
        val newEnabledState = true
        setupTestForSetPackageAppLockEnabled(
            deviceSecure = true,
            currentEnabledState
        )

        runWithShellPermissionIdentity {
            appLockPackageHelper.setPackageAppLockEnabled(
                this::mockSnapshot,
                TEST_PACKAGE_NAME,
                TEST_USER_ID_1,
                newEnabledState,
                Process.SHELL_UID,
                Binder.getCallingPid(),
            )
        }
        verifyDiskWriteAndPackageUpdate(newEnabledState)
    }

    @Test
    fun setPackageAppLockEnabled(
        @TestParameter currentEnabledState: Boolean,
        @TestParameter newEnabledState: Boolean,
        @TestParameter appLockSupportedForSecureDevice: Boolean,
        @TestParameter(valuesProvider = AuthorizedUidProvider::class) callingUid: Int,
    ) {
        val targetState = appLockSupportedForSecureDevice && newEnabledState
        val expectDiskWrite = currentEnabledState != targetState
        // The value gets successfully set to the desired state if the desired state is false, or if
        // the desired state is true for a supported, secure device
        val successfullySet = !newEnabledState || appLockSupportedForSecureDevice
        setupTestForSetPackageAppLockEnabled(
            deviceSecure = true,
            currentEnabledState
        )

        if (appLockSupportedForSecureDevice) {
            whenever(mockAndroidPackage.activities).thenReturn(activities)
        } else {
            whenever(mockAndroidPackage.activities)
                .thenReturn(mutableListOf<ParsedActivity?>())
        }

        assertThat(
            appLockPackageHelper.setPackageAppLockEnabled(
                this::mockSnapshot,
                TEST_PACKAGE_NAME,
                TEST_USER_ID_1,
                newEnabledState,
                callingUid,
                Binder.getCallingPid(),
            )
        ).isEqualTo(successfullySet)
        if (expectDiskWrite) {
            verifyDiskWriteAndPackageUpdate(targetState)
        } else {
            verifyNoWriteOrPackageUpdate()
        }
    }

    @Test
    @Throws(Exception::class)
    fun isPackageAppLockEnabled_appLockIsEnabled_true(
        @TestParameter(valuesProvider = AuthorizedUidProvider::class) callingUid: Int,
    ) {
        whenever(mockPackageUserStateInternal1.isAppLockEnabled).thenReturn(true)

        assertThat(
            appLockPackageHelper.isPackageAppLockEnabled(
                mockSnapshot, TEST_PACKAGE_NAME, TEST_USER_ID_1, callingUid
            )
        ).isTrue()
    }

    @Test
    @Throws(Exception::class)
    fun isPackageAppLockEnabled_notSystemOrRootUid_throwsException(
        @TestParameter(valuesProvider = UnauthorizedUidProvider::class) callingUid: Int,
    ) {
        whenever(mockPackageUserStateInternal1.isAppLockEnabled).thenReturn(true)

        assertFailsWith<SecurityException> {
            appLockPackageHelper.isPackageAppLockEnabled(
                mockSnapshot, TEST_PACKAGE_NAME, TEST_USER_ID_1, callingUid
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun isPackageAppLockEnabled_appLockIsDisabled_false(
        @TestParameter(valuesProvider = AuthorizedUidProvider::class) callingUid: Int,
    ) {
        whenever(mockPackageUserStateInternal1.isAppLockEnabled).thenReturn(false)

        assertThat(
            appLockPackageHelper.isPackageAppLockEnabled(
                mockSnapshot, TEST_PACKAGE_NAME, TEST_USER_ID_1, callingUid

            )
        ).isFalse()
    }

    @Test
    @Throws(Exception::class)
    fun getEnableAppLockIntentForPackage_appLockIsNotSupported_nullIntent() {
        whenever(mockAndroidPackage.activities).thenReturn(
            listOf<ParsedActivity?>()
        )

        assertThat(
            appLockPackageHelper.getEnableAppLockIntentForPackage(
                mockSnapshot, TEST_PACKAGE_NAME, TEST_USER_ID_1, /* enabled= */ true
            )
        ).isNull()
    }

    @Test
    @Throws(Exception::class)
    fun getEnableAppLockIntentForPackage_appLockAlreadySetToTargetState_nullIntent() {
        whenever(mockPackageUserStateInternal1.isAppLockEnabled).thenReturn(false)

        assertThat(
            appLockPackageHelper.getEnableAppLockIntentForPackage(
                mockSnapshot, TEST_PACKAGE_NAME, TEST_USER_ID_1, /* enabled= */ false,
            )
        ).isNull()
    }

    @Test
    @Throws(Exception::class)
    fun getEnableAppLockIntentForPackage_pendingIntent(@TestParameter newEnabledState: Boolean) {
        whenever(mockPackageUserStateInternal1.isAppLockEnabled).thenReturn(!newEnabledState)
        testInjector.setDeviceSecure(true)

        val intent = assertNotNull(
            appLockPackageHelper.getEnableAppLockIntentForPackage(
                mockSnapshot, TEST_PACKAGE_NAME, TEST_USER_ID_1, newEnabledState
            ), "Pending intent was expected to not be null"
        ).intent

        assertThat(intent.action).isEqualTo(PackageManager.ACTION_SET_APP_LOCK)
        assertThat(intent.hasExtra(PackageManager.EXTRA_APP_LOCK_NEW_STATE)).isTrue()
        assertThat(
            intent.getBooleanExtra(
                PackageManager.EXTRA_APP_LOCK_NEW_STATE,
                false
            )
        ).isEqualTo(newEnabledState)
        assertThat(intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)).isEqualTo(TEST_PACKAGE_NAME)
    }

    private fun verifyDiskWriteAndPackageUpdate(
        stateToWrite: Boolean,
        packageName: String = TEST_PACKAGE_NAME
    ) {
        verify(pms).commitPackageStateMutation(any(), any())
        verify(pms).scheduleWritePackageRestrictions(eq(TEST_USER_ID_1))
        verify(broadcastHelper).sendPackageAppLockStateChangedForUser(
            eq(packageName),
            eq(TEST_USER_ID_1),
            eq(stateToWrite)
        )
    }

    private fun verifyNoWriteOrPackageUpdate() {
        verify(pms, never()).commitPackageStateMutation(any(), any())
        verify(pms, never()).scheduleWritePackageRestrictions(anyInt())
        verify(broadcastHelper, never()).sendPackageAppLockStateChangedForUser(
            any(),
            anyInt(),
            anyBoolean()
        )
    }

    @Test
    fun isAppLockSupported_true() {
        assertThat(
            AppLockPackageHelper.isAppLockSupported(
                TEST_PACKAGE_NAME, TEST_USER_ID_1, activities
            )
        ).isTrue()
    }

    @Test
    fun isAppLockSupported_onWatch_false() {
        addSystemConfigFeature(PackageManager.FEATURE_WATCH)

        assertThat(
            AppLockPackageHelper.isAppLockSupported(
                TEST_PACKAGE_NAME, TEST_USER_ID_1, activities
            )
        ).isFalse()
    }

    @Test
    fun isAppLockSupported_onAutomotive_false() {
        addSystemConfigFeature(PackageManager.FEATURE_AUTOMOTIVE)

        assertThat(
            AppLockPackageHelper.isAppLockSupported(
                TEST_PACKAGE_NAME, TEST_USER_ID_1, activities
            )
        ).isFalse()
    }

    @Test
    fun isAppLockSupported_onTV_false() {
        addSystemConfigFeature(PackageManager.FEATURE_LEANBACK)

        assertThat(
            AppLockPackageHelper.isAppLockSupported(
                TEST_PACKAGE_NAME, TEST_USER_ID_1, activities
            )
        ).isFalse()
    }

    @Test
    fun isAppLockSupported_appLockExempt_false() {
        assertThat(
            AppLockPackageHelper.isAppLockSupported(
                EXEMPT_PACKAGE_NAME, TEST_USER_ID_1, activities
            )
        ).isFalse()
    }

    @Test
    fun isAppLockSupported_headlessApp_false() {
        assertThat(
            AppLockPackageHelper.isAppLockSupported(
                TEST_PACKAGE_NAME, TEST_USER_ID_1, ArrayList<ParsedActivity?>()
            )
        ).isFalse()
    }

    @Test
    fun isAppLockSupported_profileUser_false() {
        whenever(userInfo.isFull).thenReturn(false)

        assertThat(
            AppLockPackageHelper.isAppLockSupported(
                TEST_PACKAGE_NAME, TEST_USER_ID_1, ArrayList<ParsedActivity?>()
            )
        ).isFalse()
    }

    @Test
    fun isAppLockSupported_isSupervisedUser_false() {
        whenever(smInternal.isSupervisionEnabledForUser(eq(TEST_USER_ID_1))).thenReturn(true)

        assertThat(
            AppLockPackageHelper.isAppLockSupported(
                TEST_PACKAGE_NAME, TEST_USER_ID_1, ArrayList<ParsedActivity?>()
            )
        ).isFalse()
    }

    @Test
    fun reportLockCredentialChanged_deviceSecure_doesNothing() {
        setupTestForSetPackageAppLockEnabled(
            deviceSecure = true,
            currentEnabledState = true,
        )

        appLockPackageHelper.reportLockCredentialChanged(mockSnapshot, TEST_USER_ID_1)

        verifyNoWriteOrPackageUpdate()
    }

    @Test
    fun reportLockCredentialChanged_deviceNotSecure_appLockDisabledForUser() {
        setupTestForSetPackageAppLockEnabled(
            deviceSecure = true,
            currentEnabledState = true,
        )
        testInjector.setDeviceSecure(false)

        appLockPackageHelper.reportLockCredentialChanged(mockSnapshot, TEST_USER_ID_1)

        verifyDiskWriteAndPackageUpdate(stateToWrite = false)
    }

    private fun addSystemConfigFeature(feature: String) {
        availableFeatures.put(feature, FeatureInfo())
    }

    private fun setupTestForSetPackageAppLockEnabled(
        deviceSecure: Boolean,
        currentEnabledState: Boolean,
    ) {
        testInjector.setDeviceSecure(deviceSecure)
        whenever(mockPackageUserStateInternal1.isAppLockEnabled).thenReturn(currentEnabledState)
    }

    private class TestInjector : AppLockPackageHelper.Injector {
        private var mIsDeviceSecure = false

        fun setDeviceSecure(isDeviceSecure: Boolean) {
            mIsDeviceSecure = isDeviceSecure
        }

        override fun isDeviceSecure(context: Context?, userId: Int): Boolean {
            return mIsDeviceSecure
        }
    }

    private class UnauthorizedUidProvider : TestParameterValuesProvider() {
        override fun provideValues(context: Context): List<Int> {
            return listOf(Process.INVALID_UID, APP_UID, UserHandle.getUid(TEST_USER_ID_1, APP_UID))
        }
    }

    private class AuthorizedUidProvider : TestParameterValuesProvider() {
        override fun provideValues(context: Context): List<Int> {
            return listOf(
                Process.SYSTEM_UID,
                Process.ROOT_UID,
                UserHandle.getUid(TEST_USER_ID_1, Process.SYSTEM_UID),
                UserHandle.getUid(TEST_USER_ID_1, Process.ROOT_UID),
            )
        }
    }

    companion object {
        private const val TEST_PACKAGE_NAME = "testpackagename"
        private const val TEST_PACKAGE_NAME_2 = "testpackagename2"
        private const val EXEMPT_PACKAGE_NAME = "exempt.package.name"
        private const val TEST_USER_ID_1 = 1
        private const val APP_UID = 12345
        private fun createActivity(
            packageName: String, filters: Array<IntentFilter>
        ): ParsedActivity {
            val activity = ParsedActivityImpl()
            activity.packageName = packageName
            for (filter in filters) {
                val info = ParsedIntentInfoImpl()
                val intentInfoFilter = info.intentFilter
                if (filter.countActions() > 0) {
                    filter.actionsIterator().forEachRemaining(Consumer { action: String? ->
                        intentInfoFilter.addAction(action)
                    })
                }
                if (filter.countCategories() > 0) {
                    filter.categoriesIterator().forEachRemaining(Consumer { category: String? ->
                        intentInfoFilter.addCategory(category)
                    })
                }
                if (filter.countDataAuthorities() > 0) {
                    filter.authoritiesIterator()
                        .forEachRemaining(Consumer { ent: IntentFilter.AuthorityEntry? ->
                            intentInfoFilter.addDataAuthority(ent)
                        })
                }
                if (filter.countDataSchemes() > 0) {
                    filter.schemesIterator().forEachRemaining(Consumer { scheme: String? ->
                        intentInfoFilter.addDataScheme(scheme)
                    })
                }
                activity.addIntent(info)
                activity.isExported = true
                activity.isEnabled = true
            }
            return activity
        }
    }
}
