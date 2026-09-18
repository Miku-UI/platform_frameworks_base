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
 * limitations under the License
 */

package com.android.server.supervision

import android.Manifest.permission.BYPASS_ROLE_QUALIFICATION
import android.Manifest.permission.MANAGE_ROLE_HOLDERS
import android.Manifest.permission.MANAGE_SUPERVISION
import android.app.Activity
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PropertyInvalidatedCache
import android.app.StatsManager
import android.app.admin.DevicePolicyManager
import android.app.admin.DevicePolicyManagerInternal
import android.app.role.OnRoleHoldersChangedListener
import android.app.role.RoleManager
import android.app.supervision.ISupervisionListener
import android.app.supervision.PackageUsagePolicy
import android.app.supervision.Policy
import android.app.supervision.SupervisionManager
import android.app.supervision.SupervisionRecoveryInfo
import android.app.supervision.SupervisionRecoveryInfo.STATE_PENDING
import android.app.supervision.SupervisionRecoveryInfo.STATE_VERIFIED
import android.app.supervision.flags.Flags
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.pm.PackageManagerInternal
import android.content.pm.ResolveInfo
import android.content.pm.UserInfo
import android.content.pm.UserInfo.FLAG_FOR_TESTING
import android.content.pm.UserInfo.FLAG_FULL
import android.content.pm.UserInfo.FLAG_MAIN
import android.content.pm.UserInfo.FLAG_SYSTEM
import android.content.res.Resources
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.PersistableBundle
import android.os.Process
import android.os.UserHandle
import android.os.UserHandle.MIN_SECONDARY_USER_ID
import android.os.UserHandle.USER_SYSTEM
import android.os.UserManager
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.platform.test.flag.junit.SetFlagsRule
import android.provider.Settings
import android.provider.Settings.Secure.BROWSER_CONTENT_FILTERS_ENABLED
import android.provider.Settings.Secure.SEARCH_CONTENT_FILTERS_ENABLED
import android.util.StatsEvent
import android.util.Xml
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.frameworks.mockingservicestests.R as ServicestestsR
import com.android.internal.R as R
import com.android.internal.util.FrameworkStatsLog
import com.android.server.LocalServices
import com.android.server.ServiceThread
import com.android.server.SystemService
import com.android.server.SystemService.TargetUser
import com.android.server.appbinding.AppBindingConstants
import com.android.server.appbinding.AppBindingService
import com.android.server.appbinding.AppServiceConnection
import com.android.server.appbinding.finders.SupervisionAppServiceFinder
import com.android.server.pm.UserFilter
import com.android.server.pm.UserManagerInternal
import com.android.server.supervision.SupervisionService.ACTION_CONFIRM_SUPERVISION_CREDENTIALS
import com.android.server.supervision.SupervisionService.SETTINGS_PACKAGE_NAME
import com.android.server.supervision.SupervisionUserData.PolicyData
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.DataOutputStream
import java.io.File
import java.io.FileDescriptor
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.Executor
import java.util.function.Consumer
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.timeout
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [SupervisionService].
 *
 * Run with `atest SupervisionServiceTest`.
 */
@RunWith(AndroidJUnit4::class)
class SupervisionServiceTest {
    @get:Rule val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule val mocks: MockitoRule = MockitoJUnit.rule()
    @get:Rule val setFlagsRule = SetFlagsRule()
    @get:Rule val serviceThreadRule = ServiceThreadRule()

    @Mock private lateinit var mockDpmInternal: DevicePolicyManagerInternal
    @Mock private lateinit var mockKeyguardManager: KeyguardManager
    @Mock private lateinit var mockPackageManager: PackageManager
    @Mock private lateinit var mockPackageManagerInternal: PackageManagerInternal
    @Mock private lateinit var mockUserManagerInternal: UserManagerInternal
    @Mock private lateinit var mockAppBindingService: AppBindingService
    @Mock private lateinit var mockNotificationManager: NotificationManager
    @Mock private lateinit var mockDpm: DevicePolicyManager

    private lateinit var context: SupervisionContextWrapper
    private lateinit var injector: TestInjector
    private lateinit var lifecycle: SupervisionService.Lifecycle
    private lateinit var service: SupervisionService
    private lateinit var mResources: Resources

    @Before
    fun setUp() {

        PropertyInvalidatedCache.setTestMode(true)
        mResources = InstrumentationRegistry.getInstrumentation().getContext().getResources()
        context =
            SupervisionContextWrapper(
                InstrumentationRegistry.getInstrumentation().context,
                mockKeyguardManager,
                mockPackageManager,
                mockNotificationManager,
                mockDpm,
            )

        LocalServices.removeServiceForTest(DevicePolicyManagerInternal::class.java)
        LocalServices.addService(DevicePolicyManagerInternal::class.java, mockDpmInternal)

        LocalServices.removeServiceForTest(UserManagerInternal::class.java)
        LocalServices.addService(UserManagerInternal::class.java, mockUserManagerInternal)

        LocalServices.removeServiceForTest(PackageManagerInternal::class.java)
        LocalServices.addService(PackageManagerInternal::class.java, mockPackageManagerInternal)

        LocalServices.removeServiceForTest(AppBindingService::class.java)
        LocalServices.addService(AppBindingService::class.java, mockAppBindingService)

        // Creating a temporary folder to enable access to SupervisionSettings.
        SupervisionSettings.getInstance()
            .changeDirForTesting(Files.createTempDirectory("tempSupervisionFolder").toFile())

        // Simulate that this test has the BYPASS_ROLE_QUALIFICATION permission. This is needed to
        // bypass the system uid check in setSupervisionEnabled. This is the permission the
        // supervision CTS tests use to enable supervision.
        context.permissions[BYPASS_ROLE_QUALIFICATION] = PERMISSION_GRANTED
        // Manage supervision permission is needed for policy related APIs.
        context.permissions[MANAGE_SUPERVISION] = PERMISSION_GRANTED

        injector = TestInjector(context, serviceThreadRule.serviceThread)
        service = SupervisionService(injector)
        lifecycle = SupervisionService.Lifecycle(context, service)
        lifecycle.registerProfileOwnerListener()

        // TODO: b/427453821 Remove after converting SupervisionSettings from being a singleton.
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()
        service.setSupervisionRecoveryInfo(null)
    }

    @After
    @Throws(Exception::class)
    fun tearDown() {
        PropertyInvalidatedCache.setTestMode(false)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SYNC_WITH_DPM)
    fun onUserStarting_supervisionAppIsProfileOwner_enablesSupervision() {
        setSupervisionEnabledForUserInternal(USER_ID, false)
        whenever(mockDpmInternal.getProfileOwnerAsUser(USER_ID))
            .thenReturn(ComponentName(systemSupervisionPackage, "MainActivity"))

        simulateUserStarting(USER_ID)
        injector.awaitServiceThreadIdle()

        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isTrue()
        assertThat(service.getActiveSupervisionAppPackage(USER_ID))
            .isEqualTo(systemSupervisionPackage)
    }

    @Test
    @Ignore("Failing because supervisionProfileOwnerComponent is returning null")
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SYNC_WITH_DPM)
    fun onUserStarting_legacyProfileOwnerComponent_enablesSupervision() {
        setSupervisionEnabledForUserInternal(USER_ID, false)
        whenever(mockDpmInternal.getProfileOwnerAsUser(USER_ID))
            .thenReturn(supervisionProfileOwnerComponent)

        simulateUserStarting(USER_ID)

        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isTrue()
        assertThat(service.getActiveSupervisionAppPackage(USER_ID))
            .isEqualTo(supervisionProfileOwnerComponent?.packageName)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SYNC_WITH_DPM)
    fun onUserStarting_userPreCreated_doesNotEnableSupervision() {
        setSupervisionEnabledForUserInternal(USER_ID, false)
        whenever(mockDpmInternal.getProfileOwnerAsUser(USER_ID))
            .thenReturn(ComponentName(systemSupervisionPackage, "MainActivity"))

        simulateUserStarting(USER_ID, preCreated = true)

        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()
        assertThat(service.getActiveSupervisionAppPackage(USER_ID)).isNull()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SYNC_WITH_DPM)
    fun onUserStarting_supervisionAppIsNotProfileOwner_doesNotEnableSupervision() {
        setSupervisionEnabledForUserInternal(USER_ID, false)
        whenever(mockDpmInternal.getProfileOwnerAsUser(USER_ID))
            .thenReturn(ComponentName("other.package", "MainActivity"))

        simulateUserStarting(USER_ID)

        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()
        assertThat(service.getActiveSupervisionAppPackage(USER_ID)).isNull()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SYNC_WITH_DPM)
    fun onUserStarting_supervisionNotEnabled_doesNotApplyRestriction() {
        // Sets supervision not enabled.
        setSupervisionEnabledForUserInternal(USER_ID, false)

        // Starts the user.
        simulateUserStarting(USER_ID)
        injector.awaitServiceThreadIdle()

        // Verifies restriction not enabled.
        verify(mockDpmInternal)
            .setUserRestrictionForUser(
                SupervisionManager.SUPERVISION_SYSTEM_ENTITY,
                UserManager.DISALLOW_FACTORY_RESET,
                /* enabled= */ false,
                USER_ID,
            )
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SYNC_WITH_DPM)
    fun onUserStarting_supervisionEnabled_hasPendingRecoveryInfo_doesNotRestrictFactoryReset() {
        // Sets supervision recovery info to pending.
        setSupervisionRecoveryInfo(state = STATE_PENDING)
        setSupervisionEnabledForUserInternal(USER_ID, true)
        clearInvocations(mockDpmInternal)

        // Starts the user.
        simulateUserStarting(USER_ID)
        injector.awaitServiceThreadIdle()

        // Verifies restriction not enabled.
        verify(mockDpmInternal)
            .setUserRestrictionForUser(
                SupervisionManager.SUPERVISION_SYSTEM_ENTITY,
                UserManager.DISALLOW_FACTORY_RESET,
                /* enabled= */ false,
                USER_ID,
            )
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SYNC_WITH_DPM)
    fun onUserStarting_supervisionEnabled_hasSupervisionRoleHolders_doesNotRestrictFactoryReset() {
        // Sets supervision recovery info and supervision role holders.
        injector.setRoleHoldersAsUser(
            RoleManager.ROLE_SUPERVISION,
            UserHandle.of(USER_ID),
            listOf("com.example.supervisionapp1"),
        )
        setSupervisionRecoveryInfo(state = STATE_VERIFIED)
        setSupervisionEnabledForUserInternal(USER_ID, true)
        clearInvocations(mockDpmInternal)

        // Starts the user.
        simulateUserStarting(USER_ID)
        injector.awaitServiceThreadIdle()

        // Verifies restriction not enabled.
        verify(mockDpmInternal)
            .setUserRestrictionForUser(
                SupervisionManager.SUPERVISION_SYSTEM_ENTITY,
                UserManager.DISALLOW_FACTORY_RESET,
                /* enabled= */ false,
                USER_ID,
            )
    }

    @Test
    fun onUserStarting_supervisionEnabled_hasVerifiedRecoveryInfo_restrictsFactoryReset() {
        // Sets supervision recovery info.
        setSupervisionRecoveryInfo(state = STATE_VERIFIED)
        setSupervisionEnabledForUserInternal(USER_ID, true)
        clearInvocations(mockDpmInternal)

        // Starts the user.
        simulateUserStarting(USER_ID)
        injector.awaitServiceThreadIdle()

        // Verifies restriction is enabled.
        verify(mockDpmInternal)
            .setUserRestrictionForUser(
                SupervisionManager.SUPERVISION_SYSTEM_ENTITY,
                UserManager.DISALLOW_FACTORY_RESET,
                /* enabled= */ true,
                USER_ID,
            )
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SYNC_WITH_DPM)
    fun profileOwnerChanged_supervisionAppIsProfileOwner_enablesSupervision() {
        setSupervisionEnabledForUserInternal(USER_ID, false)
        whenever(mockDpmInternal.getProfileOwnerAsUser(USER_ID))
            .thenReturn(ComponentName(systemSupervisionPackage, "MainActivity"))

        broadcastProfileOwnerChanged(USER_ID)

        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isTrue()
        assertThat(service.getActiveSupervisionAppPackage(USER_ID))
            .isEqualTo(systemSupervisionPackage)
    }

    @Test
    @Ignore("Failing because supervisionProfileOwnerComponent is returning null")
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SYNC_WITH_DPM)
    fun profileOwnerChanged_legacyProfileOwnerComponent_enablesSupervision() {
        setSupervisionEnabledForUserInternal(USER_ID, false)
        whenever(mockDpmInternal.getProfileOwnerAsUser(USER_ID))
            .thenReturn(supervisionProfileOwnerComponent)

        broadcastProfileOwnerChanged(USER_ID)

        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isTrue()
        assertThat(service.getActiveSupervisionAppPackage(USER_ID))
            .isEqualTo(supervisionProfileOwnerComponent?.packageName)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SYNC_WITH_DPM)
    fun profileOwnerChanged_supervisionAppIsNotProfileOwner_doesNotDisableSupervision() {
        setSupervisionEnabledForUserInternal(USER_ID, true)
        whenever(mockDpmInternal.getProfileOwnerAsUser(USER_ID))
            .thenReturn(ComponentName("other.package", "MainActivity"))

        broadcastProfileOwnerChanged(USER_ID)

        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isTrue()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SYNC_WITH_DPM)
    fun profileOwnerChanged_supervisionAppIsNotProfileOwner_doesNotEnableSupervision() {
        setSupervisionEnabledForUserInternal(USER_ID, false)
        whenever(mockDpmInternal.getProfileOwnerAsUser(USER_ID))
            .thenReturn(ComponentName("other.package", "MainActivity"))

        broadcastProfileOwnerChanged(USER_ID)

        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()
        assertThat(service.getActiveSupervisionAppPackage(USER_ID)).isNull()
    }

    @Test
    fun isActiveSupervisionApp_supervisionUid_supervisionEnabled_returnsTrue() {
        whenever(mockPackageManager.getPackagesForUid(APP_UID))
            .thenReturn(arrayOf(systemSupervisionPackage))
        setSupervisionEnabledForUser(USER_ID, true)

        assertThat(service.mInternal.isActiveSupervisionApp(APP_UID)).isTrue()
    }

    @Test
    fun isActiveSupervisionApp_supervisionUid_supervisionNotEnabled_returnsFalse() {
        whenever(mockPackageManager.getPackagesForUid(APP_UID))
            .thenReturn(arrayOf(systemSupervisionPackage))
        setSupervisionEnabledForUser(USER_ID, false)

        assertThat(service.mInternal.isActiveSupervisionApp(APP_UID)).isFalse()
    }

    @Test
    fun isActiveSupervisionApp_notSupervisionUid_returnsFalse() {
        whenever(mockPackageManager.getPackagesForUid(APP_UID)).thenReturn(arrayOf())

        assertThat(service.mInternal.isActiveSupervisionApp(APP_UID)).isFalse()
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_SUPERVISION_SETTINGS_UI_UPDATES)
    fun setSupervisionEnabledForUser_clearsContentFilters() {
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()
        putSecureSetting(BROWSER_CONTENT_FILTERS_ENABLED, 1)
        putSecureSetting(SEARCH_CONTENT_FILTERS_ENABLED, 1)

        setSupervisionEnabledForUser(USER_ID, true)
        assertThat(getSecureSetting(BROWSER_CONTENT_FILTERS_ENABLED)).isEqualTo(1)
        assertThat(getSecureSetting(SEARCH_CONTENT_FILTERS_ENABLED)).isEqualTo(1)

        setSupervisionEnabledForUser(USER_ID, false)
        assertThat(getSecureSetting(BROWSER_CONTENT_FILTERS_ENABLED)).isEqualTo(-1)
        assertThat(getSecureSetting(SEARCH_CONTENT_FILTERS_ENABLED)).isEqualTo(-1)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_SETTINGS_UI_UPDATES)
    fun setSupervisionEnabledForUser_permanentlyClearsContentFilters() {
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()
        putSecureSetting(BROWSER_CONTENT_FILTERS_ENABLED, 1)
        putSecureSetting(SEARCH_CONTENT_FILTERS_ENABLED, 1)

        setSupervisionEnabledForUser(USER_ID, true)
        assertThat(getSecureSetting(BROWSER_CONTENT_FILTERS_ENABLED)).isEqualTo(1)
        assertThat(getSecureSetting(SEARCH_CONTENT_FILTERS_ENABLED)).isEqualTo(1)

        setSupervisionEnabledForUser(USER_ID, false)
        assertThat(getSecureSetting(BROWSER_CONTENT_FILTERS_ENABLED)).isEqualTo(0)
        assertThat(getSecureSetting(SEARCH_CONTENT_FILTERS_ENABLED)).isEqualTo(0)

        setSupervisionEnabledForUser(USER_ID, true)
        assertThat(getSecureSetting(BROWSER_CONTENT_FILTERS_ENABLED)).isEqualTo(0)
        assertThat(getSecureSetting(SEARCH_CONTENT_FILTERS_ENABLED)).isEqualTo(0)
    }

    @Test
    fun setSupervisionEnabledForUser_noPermission_throwsException() {
        injector.setCallingUid(1234)
        context.permissions[BYPASS_ROLE_QUALIFICATION] = PERMISSION_DENIED
        assertFailsWith<SecurityException> { setSupervisionEnabledForUser(USER_ID, false) }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_REMOVE_POLICIES_ON_SUPERVISION_DISABLE)
    fun setSupervisionEnabledForUser_removesPoliciesWhenDisabling() {
        for ((role, packageName) in supervisionRoleHolders) {
            injector.setRoleHoldersAsUser(role, UserHandle.of(USER_ID), listOf(packageName))
        }

        setSupervisionEnabledForUser(USER_ID, false)

        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()
        verify(mockDpmInternal)
            .removePoliciesForAdmins(
                eq(USER_ID),
                argThat { toSet() == supervisionRoleHolders.values.toSet() },
            )
        verify(mockDpmInternal)
            .removeLocalPoliciesForSystemEntities(
                eq(USER_ID),
                eq(SupervisionService.SYSTEM_ENTITIES),
            )
        for (packageName in supervisionRoleHolders.values) {
            verify(mockPackageManagerInternal)
                .unsuspendForSuspendingPackage(eq(packageName), eq(USER_ID), eq(USER_ID))
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_REMOVE_POLICIES_ON_SUPERVISION_DISABLE)
    fun setSupervisionEnabledForUser_doesntRemovePoliciesWhenEnabling() {
        setSupervisionEnabledForUser(USER_ID, true)

        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isTrue()
        verify(mockDpmInternal, never()).removePoliciesForAdmins(any(), any())
        verify(mockDpmInternal, never()).removeLocalPoliciesForSystemEntities(any(), any())
    }

    @Test
    fun setSupervisionEnabledForUser_hasVerifiedRecoveryInfo_restrictsFactoryResetWhenEnabling() {
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()

        setSupervisionRecoveryInfo(state = STATE_VERIFIED)
        setSupervisionEnabledForUser(USER_ID, true)

        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isTrue()
        verify(mockDpmInternal)
            .setUserRestrictionForUser(
                SupervisionManager.SUPERVISION_SYSTEM_ENTITY,
                UserManager.DISALLOW_FACTORY_RESET,
                /* enabled= */ true,
                USER_ID,
            )
    }

    @Test
    fun setSupervisionEnabledForUser_hasPendingRecoveryInfo_doesNotRestrictFactoryReset() {
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()

        setSupervisionRecoveryInfo(state = STATE_PENDING)
        setSupervisionEnabledForUser(USER_ID, true)

        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isTrue()
        verify(mockDpmInternal)
            .setUserRestrictionForUser(
                SupervisionManager.SUPERVISION_SYSTEM_ENTITY,
                UserManager.DISALLOW_FACTORY_RESET,
                /* enabled= */ false,
                USER_ID,
            )
    }

    @Test
    fun setSupervisionEnabledForUser_hasSupervisionRoleHolders_doesNotRestrictFactoryReset() {
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()
        injector.setRoleHoldersAsUser(
            RoleManager.ROLE_SUPERVISION,
            UserHandle.of(USER_ID),
            listOf("com.example.supervisionapp1"),
        )
        clearInvocations(mockDpmInternal)
        setSupervisionEnabledForUser(USER_ID, true)
        setSupervisionRecoveryInfo(state = STATE_VERIFIED)
        injector.awaitServiceThreadIdle()

        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isTrue()
        verify(mockDpmInternal)
            .setUserRestrictionForUser(
                SupervisionManager.SUPERVISION_SYSTEM_ENTITY,
                UserManager.DISALLOW_FACTORY_RESET,
                /* enabled= */ false,
                USER_ID,
            )
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_SUPERVISION_SETTINGS_UI_UPDATES)
    fun setSupervisionEnabledForUser_internal_clearsContentFilters() {
        putSecureSetting(BROWSER_CONTENT_FILTERS_ENABLED, 1)
        putSecureSetting(SEARCH_CONTENT_FILTERS_ENABLED, 0)
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()

        setSupervisionEnabledForUserInternal(USER_ID, true)
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isTrue()
        assertThat(getSecureSetting(BROWSER_CONTENT_FILTERS_ENABLED)).isEqualTo(1)
        assertThat(getSecureSetting(SEARCH_CONTENT_FILTERS_ENABLED)).isEqualTo(0)

        setSupervisionEnabledForUserInternal(USER_ID, false)
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()
        assertThat(getSecureSetting(BROWSER_CONTENT_FILTERS_ENABLED)).isEqualTo(-1)
        assertThat(getSecureSetting(SEARCH_CONTENT_FILTERS_ENABLED)).isEqualTo(0)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_SETTINGS_UI_UPDATES)
    fun setSupervisionEnabledForUser_internal_permanentlyClearsContentFilters() {
        putSecureSetting(BROWSER_CONTENT_FILTERS_ENABLED, 1)
        putSecureSetting(SEARCH_CONTENT_FILTERS_ENABLED, 0)
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()

        setSupervisionEnabledForUserInternal(USER_ID, true)
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isTrue()
        assertThat(getSecureSetting(BROWSER_CONTENT_FILTERS_ENABLED)).isEqualTo(1)
        assertThat(getSecureSetting(SEARCH_CONTENT_FILTERS_ENABLED)).isEqualTo(0)

        setSupervisionEnabledForUserInternal(USER_ID, false)
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()
        assertThat(getSecureSetting(BROWSER_CONTENT_FILTERS_ENABLED)).isEqualTo(0)
        assertThat(getSecureSetting(SEARCH_CONTENT_FILTERS_ENABLED)).isEqualTo(0)

        setSupervisionEnabledForUserInternal(USER_ID, true)
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isTrue()
        assertThat(getSecureSetting(BROWSER_CONTENT_FILTERS_ENABLED)).isEqualTo(0)
        assertThat(getSecureSetting(SEARCH_CONTENT_FILTERS_ENABLED)).isEqualTo(0)
    }

    @Test
    fun setSupervisionLockscreenEnabledForUser() {
        var userData = service.getUserDataLocked(USER_ID)
        assertThat(userData.supervisionLockScreenEnabled).isFalse()
        assertThat(userData.supervisionLockScreenOptions).isNull()

        service.mInternal.setSupervisionLockscreenEnabledForUser(USER_ID, true, PersistableBundle())
        userData = service.getUserDataLocked(USER_ID)
        assertThat(userData.supervisionLockScreenEnabled).isTrue()
        assertThat(userData.supervisionLockScreenOptions).isNotNull()

        service.mInternal.setSupervisionLockscreenEnabledForUser(USER_ID, false, null)
        userData = service.getUserDataLocked(USER_ID)
        assertThat(userData.supervisionLockScreenEnabled).isFalse()
        assertThat(userData.supervisionLockScreenOptions).isNull()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_VERIFY_SUPERVISION_ROLE_HOLDERS_BEFORE_DESTROYING_ESCROW_TOKEN)
    fun isEscrowTokenRequired() {
        injector.setRoleHoldersAsUser(
            RoleManager.ROLE_SUPERVISION,
            UserHandle.of(USER_ID),
            listOf(),
        )
        assertThat(service.mInternal.isEscrowTokenRequired(USER_ID)).isFalse()

        injector.setRoleHoldersAsUser(
            RoleManager.ROLE_SUPERVISION,
            UserHandle.of(USER_ID),
            listOf("com.example.supervisionapp1"),
        )
        assertThat(service.mInternal.isEscrowTokenRequired(USER_ID)).isTrue()
    }

    @Test
    fun createConfirmSupervisionCredentialsIntent() {
        service.mInternal.setSupervisionEnabledForUser(context.getUserId(), true)
        whenever(mockUserManagerInternal.getSupervisingProfileId()).thenReturn(SUPERVISING_USER_ID)
        whenever(mockKeyguardManager.isDeviceSecure(SUPERVISING_USER_ID)).thenReturn(true)

        val intent =
            checkNotNull(service.createConfirmSupervisionCredentialsIntent(context.getUserId()))
        assertThat(intent.getAction()).isEqualTo(ACTION_CONFIRM_SUPERVISION_CREDENTIALS)
        assertThat(intent.getPackage()).isEqualTo(SETTINGS_PACKAGE_NAME)
    }

    @Test
    fun createConfirmSupervisionCredentialsIntent_supervisionNotEnabled_returnsNull() {
        service.mInternal.setSupervisionEnabledForUser(context.getUserId(), false)
        whenever(mockUserManagerInternal.getSupervisingProfileId()).thenReturn(SUPERVISING_USER_ID)
        whenever(mockKeyguardManager.isDeviceSecure(SUPERVISING_USER_ID)).thenReturn(true)

        assertThat(service.createConfirmSupervisionCredentialsIntent(context.getUserId())).isNull()
    }

    @Test
    fun createConfirmSupervisionCredentialsIntent_noSupervisingUserOrSupervisionApps_returnsNull() {
        service.mInternal.setSupervisionEnabledForUser(context.getUserId(), true)
        whenever(mockUserManagerInternal.getSupervisingProfileId()).thenReturn(UserHandle.USER_NULL)

        assertThat(service.createConfirmSupervisionCredentialsIntent(context.getUserId())).isNull()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_SETTINGS_UI_UPDATES)
    fun createConfirmSupervisionCredentialsIntent_hasSupervisionAppApprovalActivity_returnsValidIntent() {
        val userId = context.getUserId()
        service.mInternal.setSupervisionEnabledForUser(userId, true)
        whenever(mockUserManagerInternal.getSupervisingProfileId()).thenReturn(UserHandle.USER_NULL)
        injector.setRoleHoldersAsUser(
            RoleManager.ROLE_SUPERVISION,
            UserHandle.of(userId),
            listOf(PACKAGE_NAME),
        )
        mockSupervisionApprovalActivity(PACKAGE_NAME)

        val intent = checkNotNull(service.createConfirmSupervisionCredentialsIntent(userId))

        assertThat(intent.getAction()).isEqualTo(ACTION_CONFIRM_SUPERVISION_CREDENTIALS)
        assertThat(intent.getPackage()).isEqualTo(SETTINGS_PACKAGE_NAME)
    }

    @Test
    fun createConfirmSupervisionCredentialsIntent_supervisingUserMissingSecureLock_noSupervisionApps_returnsNull() {
        service.mInternal.setSupervisionEnabledForUser(context.getUserId(), true)
        whenever(mockUserManagerInternal.getSupervisingProfileId()).thenReturn(SUPERVISING_USER_ID)
        whenever(mockKeyguardManager.isDeviceSecure(SUPERVISING_USER_ID)).thenReturn(false)

        assertThat(service.createConfirmSupervisionCredentialsIntent(context.getUserId())).isNull()
    }

    @Test
    fun shouldAllowBypassingSupervisionRoleQualification_noPermission_throwsException() {
        injector.setCallingUid(1234)
        context.permissions[MANAGE_ROLE_HOLDERS] = PERMISSION_DENIED
        assertFailsWith<SecurityException> {
            service.shouldAllowBypassingSupervisionRoleQualification()
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
    fun setShouldAllowBypassingSupervisionRoleQualification_succeeds() {
        context.permissions[MANAGE_ROLE_HOLDERS] = PERMISSION_GRANTED

        service.setShouldAllowBypassingSupervisionRoleQualification(true)
        assertThat(service.shouldAllowBypassingSupervisionRoleQualification()).isTrue()

        service.setShouldAllowBypassingSupervisionRoleQualification(false)
        assertThat(service.shouldAllowBypassingSupervisionRoleQualification()).isFalse()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
    fun setShouldAllowBypassingSupervisionRoleQualification_noPermission_throwsException() {
        injector.setCallingUid(1234)
        context.permissions[BYPASS_ROLE_QUALIFICATION] = PERMISSION_DENIED
        assertFailsWith<SecurityException> {
            service.setShouldAllowBypassingSupervisionRoleQualification(true)
        }
    }

    @Test
    fun setSupervisionRecoveryInfo() {
        addDefaultAndTestUsers()
        assertThat(service.supervisionRecoveryInfo).isNull()

        val recoveryInfo =
            SupervisionRecoveryInfo(
                "email",
                "default",
                STATE_PENDING,
                PersistableBundle().apply { putString("id", "id") },
            )
        service.setSupervisionRecoveryInfo(recoveryInfo)

        assertThat(service.supervisionRecoveryInfo).isNotNull()
        assertThat(service.supervisionRecoveryInfo.accountType).isEqualTo(recoveryInfo.accountType)
        assertThat(service.supervisionRecoveryInfo.accountName).isEqualTo(recoveryInfo.accountName)
        assertThat(service.supervisionRecoveryInfo.accountData.getString("id"))
            .isEqualTo(recoveryInfo.accountData.getString("id"))
        assertThat(service.supervisionRecoveryInfo.state).isEqualTo(recoveryInfo.state)
    }

    @Test
    fun setSupervisionRecoveryInfo_supervisionEnabled_restrictsFactoryReset() {
        addDefaultAndTestUsers()
        setSupervisionEnabledForUser(USER_ID, true)
        setSupervisionRecoveryInfo(state = STATE_VERIFIED)

        verify(mockDpmInternal)
            .setUserRestrictionForUser(
                SupervisionManager.SUPERVISION_SYSTEM_ENTITY,
                UserManager.DISALLOW_FACTORY_RESET,
                /* enabled= */ true,
                USER_ID,
            )
    }

    @Test
    fun setSupervisionRecoveryInfo_toNull_supervisionEnabled_unrestrictsFactoryReset() {
        addDefaultAndTestUsers()
        setSupervisionEnabledForUser(USER_ID, true)

        service.setSupervisionRecoveryInfo(null)

        // Once for the initial setSupervisionEnabledForUser, once for the
        // setSupervisionRecoveryInfo(null).
        verify(mockDpmInternal, times(2))
            .setUserRestrictionForUser(
                SupervisionManager.SUPERVISION_SYSTEM_ENTITY,
                UserManager.DISALLOW_FACTORY_RESET,
                /* enabled= */ false,
                USER_ID,
            )
    }

    @Test
    fun setSupervisionRecoveryInfo_supervisionEnabled_hasSupervisionRoleHolders_doesNotRestrictFactoryReset() {
        addDefaultAndTestUsers()
        injector.setRoleHoldersAsUser(
            RoleManager.ROLE_SUPERVISION,
            UserHandle.of(USER_ID),
            listOf("com.example.supervisionapp1"),
        )
        clearInvocations(mockDpmInternal)
        setSupervisionEnabledForUser(USER_ID, true)
        setSupervisionRecoveryInfo(state = STATE_VERIFIED)
        injector.awaitServiceThreadIdle()

        // Once for the initial setSupervisionEnabledForUser, again for the
        // setSupervisionRecoveryInfo, and a third time for onRoleHoldersChanged
        verify(mockDpmInternal, times(2))
            .setUserRestrictionForUser(
                SupervisionManager.SUPERVISION_SYSTEM_ENTITY,
                UserManager.DISALLOW_FACTORY_RESET,
                /* enabled= */ false,
                USER_ID,
            )
    }

    @Test
    fun setSupervisionRecoveryInfo_noPermission_throwsException() {
        injector.setCallingUid(9876)
        assertFailsWith<SecurityException> { service.setSupervisionRecoveryInfo(null) }
    }

    @Test
    fun getSupervisionRecoveryInfo_noPermission_throwsException() {
        injector.setCallingUid(9876)
        assertFailsWith<SecurityException> { service.getSupervisionRecoveryInfo() }
    }

    @Test
    fun hasSupervisionCredentials() {
        whenever(mockUserManagerInternal.getSupervisingProfileId()).thenReturn(SUPERVISING_USER_ID)
        whenever(mockKeyguardManager.isDeviceSecure(SUPERVISING_USER_ID)).thenReturn(true)

        assertThat(service.hasSupervisionCredentials()).isTrue()
    }

    @Test
    fun hasSupervisionCredentials_noSupervisingUser_returnsFalse() {
        whenever(mockUserManagerInternal.getSupervisingProfileId()).thenReturn(UserHandle.USER_NULL)

        assertThat(service.hasSupervisionCredentials()).isFalse()
    }

    @Test
    fun hasSupervisionCredentials_supervisingUserMissingSecureLock_returnsFalse() {
        whenever(mockUserManagerInternal.getSupervisingProfileId()).thenReturn(SUPERVISING_USER_ID)
        whenever(mockKeyguardManager.isDeviceSecure(SUPERVISING_USER_ID)).thenReturn(false)

        assertThat(service.hasSupervisionCredentials()).isFalse()
    }

    @Test
    fun registerAndUnregisteSupervisionListener() {
        val (listener, binder) = registerSupervisionListenerForUser(USER_ID)
        unregisterSupervisionListener(listener, binder)
    }

    @Test
    fun setSupervisionEnabledForUser_notifiesSupervisionListeners_multipleUsers() {
        val listeners = buildMap {
            userData.keys.forEach { userId ->
                put(userId, registerSupervisionListenerForUser(userId))
            }
        }

        // Ensure that listeners registered for USER_ALL are notified
        val anyUserListeners = buildMap {
            put(UserHandle.USER_ALL, registerSupervisionListenerForUser(UserHandle.USER_ALL))
        }

        listeners.forEach { userId, (listener, binder) ->
            setSupervisionEnabledForUser(userId, true, listeners + anyUserListeners)
            setSupervisionEnabledForUser(userId, false, listeners + anyUserListeners)
            clearInvocations(listener)
        }

        listeners.forEach { userId, (listener, binder) ->
            unregisterSupervisionListener(listener, binder)
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_REMOVE_POLICIES_ON_SUPERVISION_DISABLE)
    fun onRoleHoldersChanged_removesPackageSuspensionForRemovedRoleHolder() {
        val packageName = "com.example.supervisionapp"
        val roleName = RoleManager.ROLE_SUPERVISION
        injector.setRoleHoldersAsUser(
            roleName,
            UserHandle.of(USER_ID),
            listOf(packageName, "com.example.supervisionapp2", "com.example.supervisionapp3"),
        )

        injector.setRoleHoldersAsUser(roleName, UserHandle.of(USER_ID), emptyList())

        verify(mockPackageManagerInternal)
            .unsuspendForSuspendingPackage(eq(packageName), eq(USER_ID), eq(USER_ID))
    }

    private fun registerSupervisionListenerForUser(
        userId: Int
    ): Pair<ISupervisionListener, IBinder> {
        val listener = mock<ISupervisionListener>()
        val binder = mock<IBinder>()

        whenever(listener.asBinder()).thenReturn(binder)

        assertThat(service.mSupervisionListeners).doesNotContainKey(binder)

        service.registerSupervisionListener(userId, listener)
        assertThat(service.mSupervisionListeners).containsKey(binder)

        return Pair(listener, binder)
    }

    private fun unregisterSupervisionListener(listener: ISupervisionListener, binder: IBinder) {
        service.unregisterSupervisionListener(listener)
        assertThat(service.mSupervisionListeners).doesNotContainKey(binder)
    }

    /**
     * Sets the supervision enabled state for a specific user and verifies that the listeners are
     * notified.
     */
    private fun setSupervisionEnabledForUser(
        userId: Int,
        enabled: Boolean,
        listeners: SupervisionListenerMap = emptyMap(),
    ) {
        service.setSupervisionEnabledForUser(userId, enabled)
        assertThat(service.isSupervisionEnabledForUser(userId)).isEqualTo(enabled)

        injector.awaitServiceThreadIdle()

        if (!enabled) {
            assertThat(service.getUserDataLocked(userId).policies).isEmpty()
        }
        verifySupervisionListeners(userId, enabled, listeners)
    }

    /**
     * Using `mInternal`, sets the supervision enabled state for a specific user and verifies that
     * the listeners are notified.
     */
    private fun setSupervisionEnabledForUserInternal(
        userId: Int,
        enabled: Boolean,
        listeners: SupervisionListenerMap = emptyMap(),
    ) {
        service.mInternal.setSupervisionEnabledForUser(userId, enabled)
        assertThat(service.isSupervisionEnabledForUser(userId)).isEqualTo(enabled)

        injector.awaitServiceThreadIdle()

        if (!enabled) {
            assertThat(service.getUserDataLocked(userId).policies).isEmpty()
        }
        verifySupervisionListeners(userId, enabled, listeners)
    }

    private fun setSupervisionRecoveryInfo(
        @SupervisionRecoveryInfo.State state: Int,
        accountData: PersistableBundle? = null,
    ) {
        val recoveryInfo = SupervisionRecoveryInfo("email", "default", state, accountData)
        service.setSupervisionRecoveryInfo(recoveryInfo)
    }

    private fun verifySupervisionListeners(
        expectedUserId: Int,
        enabled: Boolean,
        listeners: SupervisionListenerMap,
    ) {
        val timeoutMillis = 10.seconds.inWholeMilliseconds
        listeners.forEach { userId, (listener, binder) ->
            when (userId) {
                expectedUserId,
                UserHandle.USER_ALL -> {
                    verify(listener, timeout(timeoutMillis))
                        .onSetSupervisionEnabled(eq(expectedUserId), eq(enabled))
                }
                else -> {
                    verify(listener, never()).onSetSupervisionEnabled(any(), any())
                }
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_REMOVE_POLICIES_ON_SUPERVISION_DISABLE)
    fun clearPackageSuspensions_unsuspendsSupervisionPackages() {
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()
        for ((role, packageName) in supervisionRoleHolders) {
            injector.setRoleHoldersAsUser(role, UserHandle.of(USER_ID), listOf(packageName))
        }

        setSupervisionEnabledForUser(USER_ID, false)

        for (packageName in supervisionRoleHolders.values) {
            verify(mockPackageManagerInternal)
                .unsuspendForSuspendingPackage(eq(packageName), eq(USER_ID), eq(USER_ID))
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_REMOVE_POLICIES_ON_SUPERVISION_DISABLE)
    fun clearPackageSuspensions_noSupervisionPackages_doesNothing() {
        setSupervisionEnabledForUser(USER_ID, false)

        verify(mockPackageManagerInternal, never())
            .unsuspendForSuspendingPackage(any(), any(), any())
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_SETTINGS_UI_UPDATES)
    fun canLaunchPinRecovery_noEmailAndAlternativeRecoveryMethods_returnsFalse() {
        injector.setRoleHoldersAsUser(
            RoleManager.ROLE_SUPERVISION,
            UserHandle.of(USER_ID),
            listOf(),
        )
        assertThat(service.canLaunchPinRecovery(USER_ID)).isFalse()
    }

    @Test
    fun canLaunchPinRecovery_hasPendingEmailButNoAlternativeRecoveryMethods_returnsTrue() {
        injector.setRoleHoldersAsUser(
            RoleManager.ROLE_SUPERVISION,
            UserHandle.of(USER_ID),
            listOf(),
        )
        setSupervisionRecoveryInfo(state = STATE_PENDING)
        assertThat(service.canLaunchPinRecovery(USER_ID)).isTrue()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_SETTINGS_UI_UPDATES)
    fun canLaunchPinRecovery_hasVerifiedEmailButNoAlternativeRecoveryMethods_returnsTrue() {
        injector.setRoleHoldersAsUser(
            RoleManager.ROLE_SUPERVISION,
            UserHandle.of(USER_ID),
            listOf(),
        )
        setSupervisionRecoveryInfo(state = STATE_VERIFIED)
        assertThat(service.canLaunchPinRecovery(USER_ID)).isTrue()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_SETTINGS_UI_UPDATES)
    fun canLaunchPinRecovery_noEmailButHasAlternativeRecoveryMethods_returnsTrue() {
        val supervisionPackage = "com.example.supervisionapp"
        injector.setRoleHoldersAsUser(
            RoleManager.ROLE_SUPERVISION,
            UserHandle.of(USER_ID),
            listOf(supervisionPackage),
        )
        mockSupervisionApprovalActivity(supervisionPackage)

        assertThat(service.canLaunchPinRecovery(USER_ID)).isTrue()
    }

    @Test
    @RequiresFlagsEnabled(
        Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS,
        Flags.FLAG_ENABLE_SUPERVISION_PACKAGE_USAGE_APIS,
    )
    fun setPolicy_packagePolicyTypeBlocked_unsuspendsAndHidesPackage() {
        setAndVerifyPackagePolicy(PACKAGE_POLICY_BLOCKED)
    }

    @Test
    @RequiresFlagsEnabled(
        Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS,
        Flags.FLAG_ENABLE_SUPERVISION_PACKAGE_USAGE_APIS,
    )
    fun setPolicy_packagePolicyTypeAllowed_unsuspendsAndUnhidesPackage() {
        setAndVerifyPackagePolicy(PACKAGE_POLICY_ALLOWED)
    }

    @Test
    @RequiresFlagsEnabled(
        Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS,
        Flags.FLAG_ENABLE_SUPERVISION_PACKAGE_USAGE_APIS,
    )
    fun setPolicy_packagePolicyTypeTimeLimit_suspendsAndUnhidesPackage() {
        setAndVerifyPackagePolicy(
            PACKAGE_POLICY_TIME_LIMIT_BUILDER.setTimeLimit(Duration.ofHours(1)).build()
        )
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_SUPERVISION_PACKAGE_USAGE_APIS)
    fun setPolicy_packagePolicyTypeBlocked_withoutPackageUsageApis_doesNotUnsuspendPackage() {
        setAndVerifyPackagePolicy(PACKAGE_POLICY_BLOCKED)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_SUPERVISION_PACKAGE_USAGE_APIS)
    fun setPolicy_packagePolicyTypeAllowed_withoutPackageUsageApis_doesNotUnsuspendPackage() {
        setAndVerifyPackagePolicy(PACKAGE_POLICY_ALLOWED)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
    fun setPolicy_packagePolicyBlocked_sendsNotification() {
        // triggerBootCompleted() is required for the PackageMonitor to be registered.
        triggerBootCompleted()
        // The app will be hidden after the policy is set.
        setApplicationHiddenSetting(PACKAGE_NAME, hidden = true)

        // The policy is set to blocked.
        val policy = setAndVerifyPackagePolicy(PACKAGE_POLICY_BLOCKED)

        // After the policy is set, the app is hidden.
        simulatePackageDisappeared(PACKAGE_NAME, USER_ID)

        // A notification is sent since the app is hidden.
        verifyApplicationHiddenNotification(
            PACKAGE_NAME,
            hidden = true,
            expectedIntent = getSupervisionSettingsIntent(),
        )
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
    fun setPolicy_packagePolicyBlocked_noPendingNotification_doesNotSendNotification() {
        // triggerBootCompleted() is required for the PackageMonitor to be registered.
        triggerBootCompleted()
        // The app will be hidden after the policy is set.
        setApplicationHiddenSetting(PACKAGE_NAME, hidden = true)

        // The policy is set to blocked.
        val policy = setAndVerifyPackagePolicy(PACKAGE_POLICY_BLOCKED)

        // Verify that hasPendingNotification is true, then manually set it to false.
        val policyData = getPolicyData(policy)
        assertThat(policyData?.hasPendingNotification).isTrue()
        policyData?.hasPendingNotification = false
        // After the policy is set, the app is hidden.
        simulatePackageDisappeared(PACKAGE_NAME, USER_ID)

        // No notification is sent.
        verify(mockNotificationManager, never())
            .notifyAsUser(any(), any<Int>(), any<Notification>(), eq(UserHandle.of(USER_ID)))
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
    fun setPolicy_packagePolicyUnblocked_sendsNotification() {
        // triggerBootCompleted() is required for the PackageMonitor to be registered.
        triggerBootCompleted()
        // The app will not be hidden after the policy is set.
        setApplicationHiddenSetting(PACKAGE_NAME, hidden = false)
        val launchIntent = mockPackageLaunchIntent(PACKAGE_NAME)

        // The policy is set to unblocked.
        val policy = setAndVerifyPackagePolicy(PACKAGE_POLICY_ALLOWED)

        // After the policy is set, the app is unhidden.
        simulatePackageAppeared(PACKAGE_NAME, USER_ID)

        // A notification is sent since the app is unhidden.
        verifyApplicationHiddenNotification(
            PACKAGE_NAME,
            hidden = false,
            expectedIntent = launchIntent,
        )
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
    fun setPolicy_packagePolicyUnblocked_noAppIntent_notificationHasNoIntent() {
        // triggerBootCompleted() is required for the PackageMonitor to be registered.
        triggerBootCompleted()
        // The app will not be hidden after the policy is set.
        setApplicationHiddenSetting(PACKAGE_NAME, hidden = false)
        mockPackageLaunchIntent(PACKAGE_NAME, launchIntent = null)

        // The policy is set to unblocked.
        val policy = setAndVerifyPackagePolicy(PACKAGE_POLICY_ALLOWED)

        // After the policy is set, the app is unhidden.
        simulatePackageAppeared(PACKAGE_NAME, USER_ID)

        // A notification is sent since the app is unhidden.
        verifyApplicationHiddenNotification(PACKAGE_NAME, hidden = false, expectedIntent = null)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
    fun setPolicy_packagePolicyBlocked_packageNotFound_doesNotSendNotification() {
        // triggerBootCompleted() is required for the PackageMonitor to be registered.
        triggerBootCompleted()
        // The app will be hidden after the policy is set.
        setApplicationHiddenSetting(PACKAGE_NAME, hidden = true)
        // But then, the app is not found.
        whenever(
                mockPackageManager.getApplicationInfoAsUser(
                    PACKAGE_NAME,
                    PackageManager.MATCH_UNINSTALLED_PACKAGES,
                    UserHandle.of(USER_ID),
                )
            )
            .thenThrow(PackageManager.NameNotFoundException())

        // The policy is set to blocked.
        val policy = setAndVerifyPackagePolicy(PACKAGE_POLICY_BLOCKED)

        // The package disappeared callback is received for some reason.
        simulatePackageDisappeared(PACKAGE_NAME, USER_ID)

        // No notification is sent since the app is not found.
        verify(mockNotificationManager, never())
            .notifyAsUser(any(), any<Int>(), any<Notification>(), eq(UserHandle.of(USER_ID)))
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
    fun setPolicy_packagePolicyUnblocked_stillHidden_doesNotSendNotification() {
        // triggerBootCompleted() is required for the PackageMonitor to be registered.
        triggerBootCompleted()
        // Even after policy is set, the app will still be hidden
        setApplicationHiddenSetting(PACKAGE_NAME, hidden = true)

        // The policy is set to unblocked.
        val policy = setAndVerifyPackagePolicy(PACKAGE_POLICY_ALLOWED)

        // The package disappeared callback is received for some reason.
        simulatePackageDisappeared(PACKAGE_NAME, USER_ID)

        // No notification is sent since the app is still hidden.
        verify(mockNotificationManager, never())
            .notifyAsUser(any(), any<Int>(), any<Notification>(), eq(UserHandle.of(USER_ID)))
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
    fun getPolicies_returnsExpectedPolicies() {

        // Verify that when no policies are set, an empty list is returned.
        val policies = service.getPolicies(USER_ID)
        assertThat(policies).isEmpty()

        val policy = setAndVerifyPackagePolicy(PACKAGE_POLICY_BLOCKED)

        val policiesAfterInsertion = service.getPolicies(USER_ID)
        assertThat(policiesAfterInsertion).containsExactly(policy)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
    fun setPolicy_nonSystemCallerWithoutPermission_throwsSecurityException() {
        injector.setCallingUid(1234) // Non-system uid.
        context.permissions[MANAGE_SUPERVISION] = PERMISSION_DENIED

        assertFailsWith<SecurityException> { service.setPolicy(USER_ID, PACKAGE_POLICY_BLOCKED) }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
    fun getPolicies_nonSystemCallerWithoutPermission_throwsSecurityException() {
        injector.setCallingUid(1234) // Non-system uid.
        context.permissions[MANAGE_SUPERVISION] = PERMISSION_DENIED

        assertFailsWith<SecurityException> { service.getPolicies(USER_ID) }
    }

    @Test
    fun hasValidRecoveryMethod_noVerifiedEmailAndAlternativeRecoveryMethods_returnsFalse() {
        injector.setRoleHoldersAsUser(
            RoleManager.ROLE_SUPERVISION,
            UserHandle.of(USER_ID),
            listOf(),
        )
        setSupervisionRecoveryInfo(state = STATE_PENDING)
        assertThat(service.hasValidRecoveryMethod(USER_ID)).isFalse()
    }

    @Test
    fun hasValidRecoveryMethod_hasVerifiedEmailButNoAlternativeRecoveryMethods_returnsTrue() {
        injector.setRoleHoldersAsUser(
            RoleManager.ROLE_SUPERVISION,
            UserHandle.of(USER_ID),
            listOf(),
        )
        setSupervisionRecoveryInfo(state = STATE_VERIFIED)
        assertThat(service.hasValidRecoveryMethod(USER_ID)).isTrue()
    }

    @Test
    fun hasValidRecoveryMethod_noVerifiedEmailButHasAlternativeRecoveryMethods_returnsTrue() {
        val supervisionPackage = "com.example.supervisionapp"
        injector.setRoleHoldersAsUser(
            RoleManager.ROLE_SUPERVISION,
            UserHandle.of(USER_ID),
            listOf(supervisionPackage),
        )
        mockSupervisionApprovalActivity(supervisionPackage)
        whenever(mockPackageManager.getComponentEnabledSetting(any()))
            .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        setSupervisionRecoveryInfo(state = STATE_PENDING)

        assertThat(service.hasValidRecoveryMethod(USER_ID)).isTrue()
    }

    private fun setAndVerifyPackagePolicy(policy: PackageUsagePolicy): PackageUsagePolicy {
        service.setPolicy(USER_ID, policy)
        injector.awaitServiceThreadIdle()

        val expectedSuspended = policy.type == PackageUsagePolicy.TYPE_TIME_LIMIT
        if (Flags.enableSupervisionPackageUsageApis()) {
            verifyPackageSuspendedByAdmin(expectedSuspended)
        } else {
            verifyPackageSuspendedByAdminNotCalled()
        }

        val expectedHidden = policy.type == PackageUsagePolicy.TYPE_BLOCKED
        verify(mockDpmInternal)
            .setApplicationHiddenBySystem(
                eq(SupervisionManager.SUPERVISION_SYSTEM_ENTITY),
                eq(policy.packageName),
                eq(USER_ID),
                eq(expectedHidden),
            )

        verifySupervisionAppServiceEvent(USER_ID) {
            // Supervision apps are notified of the policy change.
            verify(it).onPolicyChanged(eq(policy))
        }
        return policy
    }

    private fun verifyPackageSuspendedByAdmin(suspended: Boolean) {
        verify(mockPackageManagerInternal)
            .setPackagesSuspendedByAdmin(eq(USER_ID), eq(arrayOf(PACKAGE_NAME)), eq(suspended))
    }

    private fun verifyPackageSuspendedByAdminNotCalled() {
        verify(mockPackageManagerInternal, never()).setPackagesSuspendedByAdmin(any(), any(), any())
    }

    private fun verifyApplicationHiddenNotification(
        packageName: String,
        hidden: Boolean,
        expectedIntent: Intent?,
    ) {
        val notificationCaptor = argumentCaptor<Notification>()
        verify(mockNotificationManager)
            .notifyAsUser(
                any(),
                any<Int>(),
                notificationCaptor.capture(),
                eq(UserHandle.of(USER_ID)),
            )
        val notification = notificationCaptor.firstValue
        assertThat(notification.channelId)
            .isEqualTo(
                com.android.internal.notification.SystemNotificationChannels.PARENTAL_CONTROLS
            )
        assertThat(notification.extras.getString(Notification.EXTRA_TITLE))
            .isEqualTo(context.getString(R.string.supervision_blocked_app_title))
        val expectedText =
            if (hidden) {
                context.getString(R.string.supervision_blocked_app_content, "Test App")
            } else {
                context.getString(R.string.supervision_unblocked_app_content, "Test App")
            }
        assertThat(notification.extras.getString(Notification.EXTRA_TEXT)).isEqualTo(expectedText)
        assertThat(notification.extras.getString(Notification.EXTRA_SUBSTITUTE_APP_NAME))
            .isEqualTo(context.getString(R.string.notification_channel_parental_controls))

        if (expectedIntent == null) {
            assertThat(notification.contentIntent).isNull()
        } else {
            val expectedPendingIntent =
                PendingIntent.getActivityAsUser(
                    context,
                    0,
                    expectedIntent,
                    PendingIntent.FLAG_IMMUTABLE,
                    null,
                    UserHandle.of(USER_ID),
                )
            assertThat(notification.contentIntent).isEqualTo(expectedPendingIntent)
        }
    }

    private fun mockPackageLaunchIntent(
        packageName: String,
        launchIntent: Intent? = Intent("android.intent.action.MAIN").setPackage(packageName),
    ): Intent? {
        whenever(mockPackageManager.getLaunchIntentForPackage(packageName)).thenReturn(launchIntent)
        return launchIntent
    }

    private fun getSupervisionSettingsIntent(): Intent {
        return Intent(Settings.ACTION_SUPERVISION_SETTINGS).setPackage(SETTINGS_PACKAGE_NAME)
    }

    private fun simulatePackageAppeared(packageName: String, userId: Int) {
        val intent =
            Intent(Intent.ACTION_PACKAGE_ADDED).apply {
                data = Uri.fromParts("package", packageName, null)
                putExtra(Intent.EXTRA_USER_HANDLE, userId)
            }
        service.mPackageMonitor.onReceive(context, intent)
        injector.awaitServiceThreadIdle()
    }

    private fun simulatePackageDisappeared(packageName: String, userId: Int) {
        val intent =
            Intent(Intent.ACTION_PACKAGE_REMOVED).apply {
                data = Uri.fromParts("package", packageName, null)
                putExtra(Intent.EXTRA_USER_HANDLE, userId)
            }
        service.mPackageMonitor.onReceive(context, intent)
        injector.awaitServiceThreadIdle()
    }

    private fun setApplicationHiddenSetting(packageName: String, hidden: Boolean) {
        val appInfo = mock<ApplicationInfo>()
        whenever(
                mockPackageManager.getApplicationInfoAsUser(
                    packageName,
                    PackageManager.MATCH_UNINSTALLED_PACKAGES,
                    UserHandle.of(USER_ID),
                )
            )
            .thenReturn(appInfo)
        whenever(appInfo.loadLabel(mockPackageManager)).thenReturn("Test App")
        whenever(
                mockPackageManager.getApplicationHiddenSettingAsUser(
                    packageName,
                    UserHandle.of(USER_ID),
                )
            )
            .thenReturn(hidden)
    }

    private fun verifySupervisionAppServiceEvent(
        userId: Int,
        verifyListener: (ISupervisionListener) -> Unit,
    ) {
        val mockAppServiceFinder = mock<SupervisionAppServiceFinder>()
        val mockSupervisionListener = mock<ISupervisionListener>()
        val mockBinder = mock<IBinder>()
        // Associate the listener with the binder.
        whenever(mockSupervisionListener.asBinder()).thenReturn(mockBinder)
        // The finder returns the listener when asked for the interface.
        whenever(mockAppServiceFinder.asInterface(mockBinder)).thenReturn(mockSupervisionListener)
        val fakeComponentName = ComponentName(PACKAGE_NAME, "FooClass")
        val fakeServiceConnection =
            AppServiceConnection(
                /* context= */ context,
                /* userId= */ userId,
                /* constants= */ AppBindingConstants.initializeFromString(""),
                /* handler= */ serviceThreadRule.serviceThread.threadHandler,
                /* finder= */ mockAppServiceFinder,
                /* packageName= */ PACKAGE_NAME,
                /* componentName= */ fakeComponentName,
            )
        // Simulate the service connection being bound.
        fakeServiceConnection.bind()
        // Simulate the service connection callback, making the listener available.
        fakeServiceConnection
            .getServiceConnectionForTest()
            .onServiceConnected(fakeComponentName, mockBinder)

        // Verify the app binding service was called correctly.
        val consumerCaptor = argumentCaptor<Consumer<AppServiceConnection>>()
        verify(mockAppBindingService)
            .dispatchAppServiceEvent(
                eq(SupervisionAppServiceFinder::class.java),
                eq(userId),
                consumerCaptor.capture(),
            )
        // Simulate the app binding service callback.
        consumerCaptor.firstValue.accept(fakeServiceConnection)

        // Verify the listener was called correctly.
        verifyListener(mockSupervisionListener)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
    fun onDisableSupervision_clearsPolicies() {
        val policy = setAndVerifyPackagePolicy(PACKAGE_POLICY_BLOCKED)

        assertThat(service.getUserDataLocked(USER_ID).policies).isNotEmpty()

        setSupervisionEnabledForUser(USER_ID, false)
    }

    @Test
    fun querySupervisionApprovalActivities_returnsSortedEnabledActivities() {
        val supervisionPackage = "com.example.supervisionapp"
        injector.setRoleHoldersAsUser(
            RoleManager.ROLE_SUPERVISION,
            UserHandle.of(USER_ID),
            listOf(supervisionPackage),
        )
        val resolveInfo1 =
            supervisionApprovalActivityResolveInfo(supervisionPackage, "Use supervision app")
        val resolveInfo2 =
            supervisionApprovalActivityResolveInfo(
                supervisionPackage,
                "Use another supervision app",
            )
        mockSupervisionApprovalActivities(supervisionPackage, listOf(resolveInfo1, resolveInfo2))

        val result = service.querySupervisionApprovalActivities(USER_ID)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.loadLabel(mockPackageManager).toString() })
            .containsExactly("Use another supervision app", "Use supervision app")
            .inOrder()
    }

    private val systemSupervisionPackage: String
        get() = context.getResources().getString(R.string.config_systemSupervision)

    private val supervisionProfileOwnerComponent: ComponentName?
        get() =
            context
                .getResources()
                .getString(R.string.config_defaultSupervisionProfileOwnerComponent)
                .let(ComponentName::unflattenFromString)

    private fun simulateUserStarting(userId: Int, preCreated: Boolean = false) {
        val userInfo = UserInfo(userId, "tempUser", 0)
        userInfo.preCreated = preCreated
        lifecycle.onUserStarting(TargetUser(userInfo))
    }

    private fun broadcastProfileOwnerChanged(userId: Int) {
        val intent = Intent(DevicePolicyManager.ACTION_PROFILE_OWNER_CHANGED)
        context.sendBroadcastAsUser(intent, UserHandle.of(userId))
    }

    private fun addDefaultAndTestUsersHsum() {
        whenever(mockUserManagerInternal.isHeadlessSystemUserMode()).thenReturn(true)
        val userInfos =
            userData.map { (userId, flags) ->
                UserInfo(userId, "user$userId", USER_ICON, flags, USER_TYPE)
            }
        whenever(mockUserManagerInternal.getUsers(any<Boolean>())).thenReturn(userInfos)
    }

    private fun addDefaultAndTestUsers() {
        whenever(mockUserManagerInternal.isHeadlessSystemUserMode()).thenReturn(false)
        val userInfos =
            mapOf(
                    USER_SYSTEM to (FLAG_SYSTEM or FLAG_MAIN or FLAG_FULL),
                    MIN_SECONDARY_USER_ID to (FLAG_FULL or FLAG_FOR_TESTING),
                )
                .map { (userId, flags) ->
                    UserInfo(userId, "user$userId", USER_ICON, flags, USER_TYPE)
                }
        whenever(mockUserManagerInternal.getUsers(any<Boolean>())).thenReturn(userInfos)
    }

    @Test
    fun getUsersThatRequirePlatformCredential_usersHaveNoSupervisionRoleHolders_returnsUsers() {
        setSupervisionEnabledForUserInternal(USER_ID, true)
        setSupervisionEnabledForUserInternal(USER_ID_SECONDARY, true)
        injector.setRoleHoldersAsUser(
            RoleManager.ROLE_SUPERVISION,
            UserHandle.of(USER_ID),
            emptyList(),
        )
        injector.setRoleHoldersAsUser(
            RoleManager.ROLE_SUPERVISION,
            UserHandle.of(USER_ID_SECONDARY),
            emptyList(),
        )
        whenever(mockUserManagerInternal.getUsers(any<UserFilter>()))
            .thenReturn(
                listOf(
                    UserInfo(USER_ID, "user0", USER_ICON, FLAG_FULL, USER_TYPE),
                    UserInfo(USER_ID_SECONDARY, "user1", USER_ICON, FLAG_FULL, USER_TYPE),
                )
            )

        val result = service.getUsersThatRequirePlatformCredential()

        assertThat(result).hasSize(2)
        assertThat(result.map { it.id }).containsExactly(USER_ID, USER_ID_SECONDARY)
    }

    @Test
    fun getUsersThatRequirePlatformCredential_multipleRoleHolders_onlyOneRoleHolderHasSupervisionActivity_returnsUser() {
        setSupervisionEnabledForUserInternal(USER_ID, true)

        val supervisionPackage = "com.example.supervisionapp"
        val supervisionPackage2 = "com.example.supervisionapp2"

        injector.setRoleHoldersAsUser(
            RoleManager.ROLE_SUPERVISION,
            UserHandle.of(USER_ID),
            listOf(supervisionPackage, supervisionPackage2),
        )
        whenever(mockUserManagerInternal.getUsers(any<UserFilter>()))
            .thenReturn(listOf(UserInfo(USER_ID, "user0", USER_ICON, FLAG_FULL, USER_TYPE)))
        // Only return a supervision activity for one supervision package.
        mockSupervisionApprovalActivity(supervisionPackage)

        val result = service.getUsersThatRequirePlatformCredential()

        assertThat(result.map { it.id }).containsExactly(USER_ID)
    }

    @Test
    fun getUsersThatRequirePlatformCredential_returnsUsersWithNoSupervisionActivities() {
        val supervisionPackage = "com.example.supervisionapp"
        val supervisionPackage2 = "com.example.supervisionapp2"

        setSupervisionEnabledForUserInternal(USER_ID, true)
        setSupervisionEnabledForUserInternal(USER_ID_SECONDARY, true)
        injector.setRoleHoldersAsUser(
            RoleManager.ROLE_SUPERVISION,
            UserHandle.of(USER_ID),
            listOf(supervisionPackage),
        )
        injector.setRoleHoldersAsUser(
            RoleManager.ROLE_SUPERVISION,
            UserHandle.of(USER_ID_SECONDARY),
            listOf(supervisionPackage2),
        )

        whenever(mockUserManagerInternal.getUsers(any<UserFilter>()))
            .thenReturn(
                listOf(
                    UserInfo(USER_ID, "user0", USER_ICON, FLAG_FULL, USER_TYPE),
                    UserInfo(USER_ID_SECONDARY, "user1", USER_ICON, FLAG_FULL, USER_TYPE),
                )
            )
        // Only return a supervision activity for one supervision package.
        mockSupervisionApprovalActivity(supervisionPackage)

        val result = service.getUsersThatRequirePlatformCredential()

        assertThat(result).hasSize(1)
        assertThat(result.map { it.id }).containsExactly(USER_ID_SECONDARY)
    }

    @Test
    fun getUsersThatRequirePlatformCredential_filtersOutUnsupervisedUsers() {
        val supervisionPackage = "com.example.supervisionapp"

        setSupervisionEnabledForUserInternal(USER_ID, true)
        setSupervisionEnabledForUserInternal(USER_ID_SECONDARY, false)
        injector.setRoleHoldersAsUser(
            RoleManager.ROLE_SUPERVISION,
            UserHandle.of(USER_ID),
            listOf(supervisionPackage),
        )

        // Set up 2 users on the device
        whenever(mockUserManagerInternal.getUsers(any<UserFilter>()))
            .thenReturn(
                listOf(
                    UserInfo(USER_ID, "user0", USER_ICON, FLAG_FULL, USER_TYPE),
                    UserInfo(USER_ID_SECONDARY, "user1", USER_ICON, FLAG_FULL, USER_TYPE),
                )
            )
        // Return no supervision approval activity for supervised user
        whenever(
                mockPackageManager.queryIntentActivities(
                    argThat { intent: Intent ->
                        intent.action == SupervisionManager.ACTION_CONFIRM_SUPERVISION_APPROVAL &&
                            intent.`package` == supervisionPackage
                    },
                    any<Int>(),
                )
            )
            .thenReturn(emptyList<ResolveInfo>())

        val result = service.getUsersThatRequirePlatformCredential()

        assertThat(result).hasSize(1)
        assertThat(result.map { it.id }).containsExactly(USER_ID)
    }

    private fun supervisionApprovalActivityResolveInfo(
        supervisionPackageName: String,
        label: String = "Generic label",
    ): ResolveInfo {
        val activityInfo =
            ActivityInfo().apply {
                packageName = supervisionPackageName
                applicationInfo = ApplicationInfo()
            }
        return ResolveInfo().apply {
            this.activityInfo = activityInfo
            nonLocalizedLabel = label
        }
    }

    private fun mockSupervisionApprovalActivity(packageName: String) {
        mockSupervisionApprovalActivities(
            packageName,
            listOf(supervisionApprovalActivityResolveInfo(packageName)),
        )
    }

    private fun mockSupervisionApprovalActivities(
        packageName: String,
        activities: List<ResolveInfo>,
    ) {
        whenever(
                mockPackageManager.queryIntentActivities(
                    argThat { intent: Intent ->
                        intent.action == SupervisionManager.ACTION_CONFIRM_SUPERVISION_APPROVAL &&
                            intent.`package` == packageName
                    },
                    any<Int>(),
                )
            )
            .thenReturn(activities)
    }

    private fun addDefaultAndFullUsers() {
        val userInfos =
            userData.map { (userId, flags) ->
                UserInfo(userId, "user$userId", USER_ICON, flags, USER_TYPE)
            } + UserInfo(USER_ID, "user$USER_ID", USER_ICON, FLAG_FULL, USER_TYPE)
        whenever(mockUserManagerInternal.getUsers(any<Boolean>())).thenReturn(userInfos)
    }

    private fun putSecureSetting(name: String, value: Int) {
        Settings.Secure.putIntForUser(context.contentResolver, name, value, USER_ID)
    }

    private fun getSecureSetting(name: String): Int {
        return Settings.Secure.getIntForUser(context.contentResolver, name, USER_ID)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
    fun onBootCompleted_upgradeNeeded_performsUpgrade() {
        // Setup settings file with old version
        val settings = loadSupervisionSettings(ServicestestsR.xml.supervision_settings_v0, 0)
        val userInfo = UserInfo(USER_ID, "testuser", 0)
        whenever(mockUserManagerInternal.getUsers(false)).thenReturn(listOf(userInfo))

        // Trigger boot phase
        triggerBootCompleted()

        // Verify upgrade happened
        verify(mockDpmInternal)
            .clearHiddenApplicationsForRole(RoleManager.ROLE_SUPERVISION, USER_ID)
        verify(mockDpmInternal)
            .clearHiddenApplicationsForRole(RoleManager.ROLE_SYSTEM_SUPERVISION, USER_ID)
        assertThat(settings.version).isEqualTo(SupervisionSettings.VERSION)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
    fun onBootCompleted_upgradeFails_doesNotUpdateVersion() {
        // Setup settings file with old version
        val settings = loadSupervisionSettings(ServicestestsR.xml.supervision_settings_v0, 0)

        withLocalServiceRemoved(DevicePolicyManagerInternal::class.java) {
            triggerBootCompleted()

            // Verify upgrade did not happen and version is not updated
            assertThat(settings.version).isEqualTo(0)
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
    fun onBootCompleted_sameVersion_doesNotUpgrade() {
        val settings =
            loadSupervisionSettings(
                ServicestestsR.xml.supervision_settings_current_version,
                SupervisionSettings.VERSION,
            )

        // Trigger boot phase
        triggerBootCompleted()

        // Verify upgrade did not happen
        verify(mockDpmInternal, never()).removePoliciesForAdmins(any(), any())
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_MANAGER_POLICY_APIS)
    fun dump_includesVersionInfo() {
        loadSupervisionSettings(ServicestestsR.xml.supervision_settings_dump, 5)

        val dumpOutput = dumpSupervisionService()

        assertThat(dumpOutput).contains("version: 5")
        assertThat(dumpOutput).contains("previousVersion: 2")
        assertThat(dumpOutput).contains("upgraded: true")
    }

    private fun dumpSupervisionService(): String {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        // Check dump permission.
        context.permissions[android.Manifest.permission.DUMP] = PERMISSION_GRANTED
        service.dump(FileDescriptor(), printWriter, null)
        printWriter.flush()
        return stringWriter.toString()
    }

    private fun triggerBootCompleted() {
        lifecycle.onBootPhase(SystemService.PHASE_BOOT_COMPLETED)
        injector.awaitServiceThreadIdle()
    }

    private fun <T : Any> withLocalServiceRemoved(serviceClass: Class<T>, action: () -> Unit) {
        val originalService: T? = LocalServices.getService(serviceClass)
        LocalServices.removeServiceForTest(serviceClass)
        try {
            action()
        } finally {
            if (originalService != null) {
                LocalServices.addService(serviceClass, originalService)
            } else {
                LocalServices.removeServiceForTest(serviceClass)
            }
        }
    }

    private fun getPolicyData(policy: Policy): PolicyData? {
        return service.getUserDataLocked(USER_ID).policies[policy.policyKey]
    }

    private fun loadSupervisionSettings(resId: Int, expectedVersion: Int): SupervisionSettings {
        val tempDir = Files.createTempDirectory("tempSupervisionFolder").toFile()
        val settingsFile = File(tempDir, "supervision_settings.xml")
        settingsFile.outputStream().use { fileOutputStream ->
            val dataOutputStream = DataOutputStream(fileOutputStream)
            val xmlIn = mResources.getXml(resId)
            val xmlOut = Xml.newBinarySerializer()

            xmlOut.setOutput(dataOutputStream, StandardCharsets.UTF_8.name())
            Xml.copy(xmlIn, xmlOut)
            xmlOut.flush()
        }

        val settings = SupervisionSettings.getInstance()
        settings.changeDirForTesting(tempDir)
        settings.loadUserData()
        assertThat(settings.version).isEqualTo(expectedVersion)
        return settings
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SUPERVISION_SETTINGS_UI_UPDATES)
    fun onPullAtom_populatesData() {
        // Mock user info
        whenever(mockUserManagerInternal.getUserInfo(USER_ID))
            .thenReturn(UserInfo(USER_ID, "user0", FLAG_FULL))
        whenever(mockUserManagerInternal.getUserInfo(USER_ID_SECONDARY))
            .thenReturn(UserInfo(USER_ID_SECONDARY, "user1", FLAG_FULL))

        // Populate SupervisionSettings by enabling supervision
        setSupervisionEnabledForUserInternal(USER_ID, true)
        setSupervisionEnabledForUserInternal(USER_ID_SECONDARY, true)

        whenever(mockUserManagerInternal.getSupervisingProfileId()).thenReturn(SUPERVISING_USER_ID)
        whenever(mockKeyguardManager.isDeviceSecure(SUPERVISING_USER_ID)).thenReturn(true)
        setSupervisionRecoveryInfo(state = STATE_VERIFIED)

        val data = mutableListOf<StatsEvent>()
        val result = service.onPullAtom(FrameworkStatsLog.SUPERVISION_STATE, data)

        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS)
        assertThat(data).hasSize(2) // Expecting 2 atoms, one for each full user
    }

    private companion object {
        const val USER_ID = 0
        const val USER_ID_SECONDARY = 1
        const val APP_UID = USER_ID * UserHandle.PER_USER_RANGE
        const val SUPERVISING_USER_ID = 10
        const val USER_ICON = "user_icon"
        const val USER_TYPE = "fake_user_type"
        const val PACKAGE_NAME = "com.example.supervisionapp"
        const val ROLE_HOLDER_PACKAGE = "com.some.role.holder"
        const val HIDDEN_PACKAGE = "com.some.hidden.app"
        val PACKAGE_POLICY_BLOCKED
            get() =
                PackageUsagePolicy.Builder(PACKAGE_NAME, PackageUsagePolicy.TYPE_BLOCKED).build()

        val PACKAGE_POLICY_ALLOWED
            get() =
                PackageUsagePolicy.Builder(PACKAGE_NAME, PackageUsagePolicy.TYPE_ALLOWED).build()

        val PACKAGE_POLICY_TIME_LIMIT_BUILDER
            get() = PackageUsagePolicy.Builder(PACKAGE_NAME, PackageUsagePolicy.TYPE_TIME_LIMIT)

        val supervisionRoleHolders =
            mapOf(
                RoleManager.ROLE_SYSTEM_SUPERVISION to "com.example.supervisionapp1",
                RoleManager.ROLE_SUPERVISION to "com.example.supervisionapp2",
            )
        val userData: Map<Int, Int> =
            mapOf(
                USER_SYSTEM to FLAG_SYSTEM,
                MIN_SECONDARY_USER_ID to FLAG_MAIN,
                (MIN_SECONDARY_USER_ID + 1) to (FLAG_FULL or FLAG_FOR_TESTING),
            )
    }
}

typealias SupervisionListenerMap = Map<Int, Pair<ISupervisionListener, IBinder>>

private class TestInjector(val context: Context, private val serviceThread: ServiceThread) :
    SupervisionService.Injector(context) {
    private val roleHolders = mutableMapOf<Pair<String, UserHandle>, List<String>>()
    private var roleHoldersChangedListener: OnRoleHoldersChangedListener? = null
    private var callingUid = Process.SYSTEM_UID

    override fun addOnRoleHoldersChangedListenerAsUser(
        executor: Executor,
        listener: OnRoleHoldersChangedListener,
        user: UserHandle,
    ) {
        this.roleHoldersChangedListener = listener
    }

    override fun getRoleHoldersAsUser(roleName: String, user: UserHandle): List<String> {
        return roleHolders[Pair(roleName, user)] ?: emptyList()
    }

    fun setRoleHoldersAsUser(roleName: String, user: UserHandle, packages: List<String>) {
        roleHolders[Pair(roleName, user)] = packages
        roleHoldersChangedListener?.onRoleHoldersChanged(roleName, user)
        awaitServiceThreadIdle()
    }

    override fun getServiceThread(): ServiceThread {
        return serviceThread
    }

    override fun getCallingUid(): Int {
        return callingUid
    }

    fun setCallingUid(callingUid: Int) {
        this.callingUid = callingUid
    }

    /**
     * Awaits for the service thread to become idle, ensuring all pending tasks are completed.
     *
     * This method uses `runWithScissors` with a timeout to wait for the service thread's handler to
     * process all queued tasks. It asserts that the operation completes successfully within the
     * timeout.
     *
     * @see android.os.Handler.runWithScissors
     */
    fun awaitServiceThreadIdle() {
        val timeout = 1.seconds.inWholeMilliseconds
        val success = serviceThread.threadHandler.runWithScissors({}, timeout)
        assertWithMessage("Waiting on the service thread timed out").that(success).isTrue()
    }
}

/**
 * A context wrapper that allows broadcast intents to immediately invoke the receivers without
 * performing checks on the sending user.
 */
private class SupervisionContextWrapper(
    val context: Context,
    val keyguardManager: KeyguardManager,
    val pkgManager: PackageManager,
    val notificationManager: NotificationManager,
    val devicePolicyManager: DevicePolicyManager,
) : ContextWrapper(context) {
    val interceptors = mutableListOf<Pair<BroadcastReceiver, IntentFilter>>()
    val permissions = mutableMapOf<String, Int>()

    override fun getSystemService(name: String): Any? {
        var ret =
            when (name) {
                Context.KEYGUARD_SERVICE -> keyguardManager
                Context.NOTIFICATION_SERVICE -> notificationManager
                Context.DEVICE_POLICY_SERVICE -> devicePolicyManager
                else -> super.getSystemService(name)
            }
        return ret
    }

    override fun createContextAsUser(userHandle: UserHandle, flags: Int): Context {
        return this
    }

    override fun getPackageManager() = pkgManager

    override fun createAttributionContext(attributionTag: String?) = this

    override fun registerReceiverForAllUsers(
        receiver: BroadcastReceiver?,
        filter: IntentFilter,
        broadcastPermission: String?,
        scheduler: Handler?,
    ): Intent? {
        if (receiver != null) {
            interceptors.add(Pair(receiver, filter))
        }
        return null
    }

    override fun sendBroadcastAsUser(intent: Intent, user: UserHandle) {
        val pendingResult =
            BroadcastReceiver.PendingResult(
                Activity.RESULT_OK,
                /* resultData= */ "",
                /* resultExtras= */ null,
                /* type= */ 0,
                /* ordered= */ true,
                /* sticky= */ false,
                /* token= */ null,
                user.identifier,
                /* flags= */ 0,
            )
        for ((receiver, filter) in interceptors) {
            if (filter.match(contentResolver, intent, false, "") > 0) {
                receiver.setPendingResult(pendingResult)
                receiver.onReceive(context, intent)
            }
        }
    }

    override fun checkCallingOrSelfPermission(permission: String): Int {
        return permissions[permission] ?: super.checkCallingOrSelfPermission(permission)
    }
}
