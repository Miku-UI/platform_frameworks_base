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
import android.app.Activity
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.app.admin.DevicePolicyManagerInternal
import android.app.role.OnRoleHoldersChangedListener
import android.app.role.RoleManager
import android.app.supervision.ISupervisionListener
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
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.pm.PackageManagerInternal
import android.content.pm.UserInfo
import android.content.pm.UserInfo.FLAG_FOR_TESTING
import android.content.pm.UserInfo.FLAG_FULL
import android.content.pm.UserInfo.FLAG_MAIN
import android.content.pm.UserInfo.FLAG_SYSTEM
import android.os.Handler
import android.os.IBinder
import android.os.PersistableBundle
import android.os.UserHandle
import android.os.UserHandle.MIN_SECONDARY_USER_ID
import android.os.UserHandle.USER_SYSTEM
import android.os.UserManager
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.platform.test.flag.junit.SetFlagsRule
import android.provider.Settings
import android.provider.Settings.Secure.BROWSER_CONTENT_FILTERS_ENABLED
import android.provider.Settings.Secure.SEARCH_CONTENT_FILTERS_ENABLED
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.internal.R
import com.android.server.LocalServices
import com.android.server.ServiceThread
import com.android.server.SystemService.TargetUser
import com.android.server.pm.UserManagerInternal
import com.android.server.supervision.SupervisionService.ACTION_CONFIRM_SUPERVISION_CREDENTIALS
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.nio.file.Files
import java.util.concurrent.Executor
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
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

    private lateinit var context: SupervisionContextWrapper
    private lateinit var injector: TestInjector
    private lateinit var lifecycle: SupervisionService.Lifecycle
    private lateinit var service: SupervisionService

    @Before
    fun setUp() {
        context =
            SupervisionContextWrapper(
                InstrumentationRegistry.getInstrumentation().context,
                mockKeyguardManager,
                mockPackageManager,
            )

        LocalServices.removeServiceForTest(DevicePolicyManagerInternal::class.java)
        LocalServices.addService(DevicePolicyManagerInternal::class.java, mockDpmInternal)

        LocalServices.removeServiceForTest(UserManagerInternal::class.java)
        LocalServices.addService(UserManagerInternal::class.java, mockUserManagerInternal)

        LocalServices.removeServiceForTest(PackageManagerInternal::class.java)
        LocalServices.addService(PackageManagerInternal::class.java, mockPackageManagerInternal)

        // Creating a temporary folder to enable access to SupervisionSettings.
        SupervisionSettings.getInstance()
            .changeDirForTesting(Files.createTempDirectory("tempSupervisionFolder").toFile())

        // Simulate that this test has the BYPASS_ROLE_QUALIFICATION permission. This is needed to
        // bypass the system uid check in setSupervisionEnabled. This is the permission the
        // supervision CTS tests use to enable supervision.
        context.permissions[BYPASS_ROLE_QUALIFICATION] = PERMISSION_GRANTED

        injector = TestInjector(context, serviceThreadRule.serviceThread)
        service = SupervisionService(injector)
        lifecycle = SupervisionService.Lifecycle(context, service)
        lifecycle.registerProfileOwnerListener()

        // TODO: b/427453821 Remove after converting SupervisionSettings from being a singleton.
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_SYNC_WITH_DPM)
    fun onUserStarting_supervisionAppIsProfileOwner_enablesSupervision() {
        setSupervisionEnabledForUserInternal(USER_ID, false)
        whenever(mockDpmInternal.getProfileOwnerAsUser(USER_ID))
            .thenReturn(ComponentName(systemSupervisionPackage, "MainActivity"))

        simulateUserStarting(USER_ID)

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
    fun setSupervisionEnabledForUser_noPermission_throwsException() {
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
    fun createConfirmSupervisionCredentialsIntent() {
        service.mInternal.setSupervisionEnabledForUser(context.getUserId(), true)
        whenever(mockUserManagerInternal.getSupervisingProfileId()).thenReturn(SUPERVISING_USER_ID)
        whenever(mockKeyguardManager.isDeviceSecure(SUPERVISING_USER_ID)).thenReturn(true)

        val intent =
            checkNotNull(service.createConfirmSupervisionCredentialsIntent(context.getUserId()))
        assertThat(intent.action).isEqualTo(ACTION_CONFIRM_SUPERVISION_CREDENTIALS)
        assertThat(intent.getPackage()).isEqualTo("com.android.settings")
    }

    @Test
    fun createConfirmSupervisionCredentialsIntent_supervisionNotEnabled_returnsNull() {
        service.mInternal.setSupervisionEnabledForUser(context.getUserId(), false)
        whenever(mockUserManagerInternal.getSupervisingProfileId()).thenReturn(SUPERVISING_USER_ID)
        whenever(mockKeyguardManager.isDeviceSecure(SUPERVISING_USER_ID)).thenReturn(true)

        assertThat(service.createConfirmSupervisionCredentialsIntent(context.getUserId())).isNull()
    }

    @Test
    fun createConfirmSupervisionCredentialsIntent_noSupervisingUser_returnsNull() {
        service.mInternal.setSupervisionEnabledForUser(context.getUserId(), true)
        whenever(mockUserManagerInternal.getSupervisingProfileId()).thenReturn(UserHandle.USER_NULL)

        assertThat(service.createConfirmSupervisionCredentialsIntent(context.getUserId())).isNull()
    }

    @Test
    fun createConfirmSupervisionCredentialsIntent_supervisingUserMissingSecureLock_returnsNull() {
        service.mInternal.setSupervisionEnabledForUser(context.getUserId(), true)
        whenever(mockUserManagerInternal.getSupervisingProfileId()).thenReturn(SUPERVISING_USER_ID)
        whenever(mockKeyguardManager.isDeviceSecure(SUPERVISING_USER_ID)).thenReturn(false)

        assertThat(service.createConfirmSupervisionCredentialsIntent(context.getUserId())).isNull()
    }

    @Test
    fun shouldAllowBypassingSupervisionRoleQualification_returnsTrue() {
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()
        assertThat(service.shouldAllowBypassingSupervisionRoleQualification()).isTrue()

        addDefaultAndTestUsers()
        assertThat(service.shouldAllowBypassingSupervisionRoleQualification()).isTrue()
    }

    @Test
    fun shouldAllowBypassingSupervisionRoleQualification_returnsFalse() {
        setSupervisionEnabledForUser(USER_ID, false)
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isFalse()
        assertThat(service.shouldAllowBypassingSupervisionRoleQualification()).isTrue()

        addDefaultAndTestUsers()
        assertThat(service.shouldAllowBypassingSupervisionRoleQualification()).isTrue()

        // Enabling supervision on any user will disallow bypassing
        setSupervisionEnabledForUser(USER_ID, true)
        assertThat(service.isSupervisionEnabledForUser(USER_ID)).isTrue()
        assertThat(service.shouldAllowBypassingSupervisionRoleQualification()).isFalse()

        // Adding non-default users should also disallow bypassing
        addDefaultAndFullUsers()
        assertThat(service.shouldAllowBypassingSupervisionRoleQualification()).isFalse()

        // Turning off supervision with non-default users should still disallow bypassing
        setSupervisionEnabledForUser(USER_ID, false)
        assertThat(service.shouldAllowBypassingSupervisionRoleQualification()).isFalse()
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PERSISTENT_SUPERVISION_SETTINGS)
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
    @RequiresFlagsEnabled(Flags.FLAG_PERSISTENT_SUPERVISION_SETTINGS)
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
    @RequiresFlagsEnabled(Flags.FLAG_PERSISTENT_SUPERVISION_SETTINGS)
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
    @RequiresFlagsEnabled(Flags.FLAG_PERSISTENT_SUPERVISION_SETTINGS)
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

        // Once for the initial setSupervisionEnabledForUser, and again for the
        // setSupervisionRecoveryInfo.
        verify(mockDpmInternal, times(2))
            .setUserRestrictionForUser(
                SupervisionManager.SUPERVISION_SYSTEM_ENTITY,
                UserManager.DISALLOW_FACTORY_RESET,
                /* enabled= */ false,
                USER_ID,
            )
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
    @EnableFlags(Flags.FLAG_ENABLE_SUPERVISION_APP_SERVICE)
    fun registerAndUnregisteSupervisionListener() {
        val (listener, binder) = registerSupervisionListenerForUser(USER_ID)
        unregisterSupervisionListener(listener, binder)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_SUPERVISION_APP_SERVICE)
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
        injector.setRoleHoldersAsUser(roleName, UserHandle.of(USER_ID), listOf(packageName,
            "com.example.supervisionapp2", "com.example.supervisionapp3"))

        injector.setRoleHoldersAsUser(roleName, UserHandle.of(USER_ID), emptyList())
        injector.awaitServiceThreadIdle()

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

    private fun addDefaultAndTestUsers() {
        val userInfos =
            userData.map { (userId, flags) ->
                UserInfo(userId, "user$userId", USER_ICON, flags, USER_TYPE)
            }
        whenever(mockUserManagerInternal.getUsers(any())).thenReturn(userInfos)
    }

    private fun addDefaultAndFullUsers() {
        val userInfos =
            userData.map { (userId, flags) ->
                UserInfo(userId, "user$userId", USER_ICON, flags, USER_TYPE)
            } + UserInfo(USER_ID, "user$USER_ID", USER_ICON, FLAG_FULL, USER_TYPE)
        whenever(mockUserManagerInternal.getUsers(any())).thenReturn(userInfos)
    }

    private fun putSecureSetting(name: String, value: Int) {
        Settings.Secure.putIntForUser(context.contentResolver, name, value, USER_ID)
    }

    private fun getSecureSetting(name: String): Int {
        return Settings.Secure.getIntForUser(context.contentResolver, name, USER_ID)
    }

    private companion object {
        const val USER_ID = 0
        const val APP_UID = USER_ID * UserHandle.PER_USER_RANGE
        const val SUPERVISING_USER_ID = 10
        const val USER_ICON = "user_icon"
        const val USER_TYPE = "fake_user_type"
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
    }

    override fun getServiceThread(): ServiceThread {
        return serviceThread
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
) : ContextWrapper(context) {
    val interceptors = mutableListOf<Pair<BroadcastReceiver, IntentFilter>>()
    val permissions = mutableMapOf<String, Int>()

    override fun getSystemService(name: String): Any? {
        var ret =
            when (name) {
                Context.KEYGUARD_SERVICE -> keyguardManager
                else -> super.getSystemService(name)
            }
        return ret
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
