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

package com.android.server.appfunctions

import android.Manifest
import android.annotation.RequiresPermission
import android.app.IUriGrantsManager
import android.app.appfunctions.AppFunctionAccessServiceInterface
import android.app.appfunctions.flags.Flags
import android.content.pm.PackageManagerInternal
import android.content.pm.Signature
import android.content.pm.SignedPackage
import android.content.pm.UserInfo
import android.os.IBinder
import android.permission.flags.Flags.FLAG_APP_FUNCTION_ACCESS_API_ENABLED
import android.permission.flags.Flags.FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.DeviceConfig
import android.testing.TestableContext
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.internal.R
import com.android.modules.utils.testing.ExtendedMockitoRule
import com.android.server.LocalServices
import com.android.server.SystemService
import com.android.server.SystemService.TargetUser
import com.android.server.uri.UriGrantsManagerInternal
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTION_MANAGER)
class AppFunctionManagerServiceImplTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule
    val extendedMockitoRule =
        ExtendedMockitoRule.Builder(this)
            .mockStatic(LocalServices::class.java)
            .mockStatic(DeviceConfig::class.java)
            .build()

    @get:Rule
    val context: TestableContext =
        spy(TestableContext(ApplicationProvider.getApplicationContext(), null))

    private val appFunctionAccessService = mock<AppFunctionAccessServiceInterface>()
    private val agentAllowlistStorage = mock<AppFunctionAgentAllowlistStorage>()
    private val multiUserAccessHistory = mock<MultiUserAppFunctionAccessHistory>()
    private val agentAllowlistCaptor = argumentCaptor<Set<SignedPackage>>()
    private var allowlistWasEnabled: Boolean = true

    private val serviceImpl =
        AppFunctionManagerServiceImpl(
            context,
            mock<PackageManagerInternal>(),
            appFunctionAccessService,
            mock<IUriGrantsManager>(),
            mock<UriGrantsManagerInternal>().apply {
                whenever(this.newUriPermissionOwner(any())).thenReturn(mock<IBinder>())
            },
            mock<AppFunctionsLoggerWrapper>(),
            agentAllowlistStorage,
            multiUserAccessHistory,
            MoreExecutors.directExecutor(),
        )

    @After
    fun clearState() {
        clearDeviceSettingPackages()
        clearPreloadedAllowlist()
        setDeviceConfigAllowlist(null)
    }

    @Test
    fun testGetLockForPackage_samePackage() {
        val packageName = "com.example.app"
        val lock1 = serviceImpl.getLockForPackage(packageName)
        val lock2 = serviceImpl.getLockForPackage(packageName)

        // Assert that the same lock object is returned for the same package name
        assertThat(lock1).isEqualTo(lock2)
    }

    @Test
    fun testGetLockForPackage_differentPackages() {
        val packageName1 = "com.example.app1"
        val packageName2 = "com.example.app2"
        val lock1 = serviceImpl.getLockForPackage(packageName1)
        val lock2 = serviceImpl.getLockForPackage(packageName2)

        // Assert that different lock objects are returned for different package names
        assertThat(lock1).isNotEqualTo(lock2)
    }

    @Ignore("Hard to deterministically trigger the garbage collector.")
    @Test
    fun testWeakReference_garbageCollected_differentLockAfterGC() = runTest {
        // Create a large number of temporary objects to put pressure on the GC
        val tempObjects = MutableList<Any?>(10000000) { Any() }
        var callingPackage: String? = "com.example.app"
        var lock1: Any? = serviceImpl.getLockForPackage(callingPackage)
        callingPackage = null // Set the key to null
        val lock1Hash = lock1.hashCode()
        lock1 = null

        // Create memory pressure
        repeat(3) {
            for (i in 1..100) {
                "a".repeat(10000)
            }
            System.gc() // Suggest garbage collection
            System.runFinalization()
        }
        // Get the lock again - it should be a different object now
        val lock2 = serviceImpl.getLockForPackage("com.example.app")
        // Assert that the lock objects are different
        assertThat(lock1Hash).isNotEqualTo(lock2.hashCode())
    }

    @RequiresFlagsEnabled(
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    )
    @Test
    fun getAccessFlags_shouldUseOriginalTargetPackage_whenNotDeviceSetting() {
        val targetPackage = "non.device.setting.package"

        serviceImpl.getAccessFlags("agent.package", 0, targetPackage, 0)

        verify(appFunctionAccessService).getAccessFlags(any(), any(), eq(targetPackage), any())
    }

    @RequiresFlagsEnabled(
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    )
    @Test
    fun getAccessFlags_shouldUseAndroidTargetPackage_whenIsDeviceSetting() {
        val targetPackage = "device.setting.package"
        setDeviceSettingPackages(arrayOf(targetPackage))

        serviceImpl.getAccessFlags("agent.package", 0, targetPackage, 0)

        verify(appFunctionAccessService).getAccessFlags(any(), any(), eq("android"), any())
    }

    @RequiresFlagsEnabled(
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    )
    @Test
    fun updateAccessFlags_shouldUseOriginalTargetPackage_whenNotDeviceSetting() {
        val targetPackage = "non.device.setting.package"

        serviceImpl.updateAccessFlags("agent.package", 0, targetPackage, 0, 0, 0)

        verify(appFunctionAccessService)
            .updateAccessFlags(any(), any(), eq(targetPackage), any(), any(), any())
    }

    @RequiresFlagsEnabled(
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    )
    @Test
    fun updateAccessFlags_shouldUseAndroidTargetPackage_whenIsDeviceSetting() {
        val targetPackage = "device.setting.package"
        setDeviceSettingPackages(arrayOf(targetPackage))

        serviceImpl.updateAccessFlags("agent.package", 0, targetPackage, 0, 0, 0)

        verify(appFunctionAccessService)
            .updateAccessFlags(any(), any(), eq("android"), any(), any(), any())
    }

    @RequiresFlagsEnabled(
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    )
    @Test
    fun revokeSelfAccess_shouldUseOriginalTargetPackage_whenNotDeviceSetting() {
        val targetPackage = "non.device.setting.package"

        serviceImpl.revokeSelfAccess(targetPackage)

        verify(appFunctionAccessService).revokeSelfAccess(eq(targetPackage))
    }

    @RequiresFlagsEnabled(
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    )
    @Test
    fun revokeSelfAccess_shouldUseAndroidTargetPackage_whenIsDeviceSetting() {
        val targetPackage = "device.setting.package"
        setDeviceSettingPackages(arrayOf(targetPackage))

        serviceImpl.revokeSelfAccess(targetPackage)

        verify(appFunctionAccessService).revokeSelfAccess(eq("android"))
    }

    @RequiresFlagsEnabled(
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    )
    @Test
    fun getAccessRequestState_shouldUseOriginalTargetPackage_whenNotDeviceSetting() {
        val targetPackage = "non.device.setting.package"

        serviceImpl.getAccessRequestState("agent.package", 0, targetPackage, 0)

        verify(appFunctionAccessService)
            .getAccessRequestState(any(), any(), eq(targetPackage), any())
    }

    @RequiresFlagsEnabled(
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    )
    @Test
    fun getAccessRequestState_shouldUseAndroidTargetPackage_whenIsDeviceSetting() {
        val targetPackage = "device.setting.package"
        setDeviceSettingPackages(arrayOf(targetPackage))

        serviceImpl.getAccessRequestState("agent.package", 0, targetPackage, 0)

        verify(appFunctionAccessService).getAccessRequestState(any(), any(), eq("android"), any())
    }

    @RequiresFlagsEnabled(
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    )
    @Test
    fun getValidTargets_shouldNotHaveAndroidPackage_whenNoDeviceSettingPackage() {
        val nonDeviceSettingPackage = "non.device.setting.package"
        val deviceSettingPackage1 = "device.setting.package1"
        val deviceSettingPackage2 = "device.setting.package2"
        setDeviceSettingPackages(arrayOf(deviceSettingPackage1, deviceSettingPackage2))
        whenever(appFunctionAccessService.getValidTargets(any()))
            .thenReturn(listOf(nonDeviceSettingPackage))

        val validTargets = serviceImpl.getValidTargets(0)

        assertThat(validTargets).containsExactly(nonDeviceSettingPackage)
    }

    @RequiresFlagsEnabled(
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    )
    @Test
    fun getValidTargets_shouldReplaceDeviceSettingPackagesToAndroid() {
        val nonDeviceSettingPackage = "non.device.setting.package"
        val deviceSettingPackage1 = "device.setting.package1"
        val deviceSettingPackage2 = "device.setting.package2"
        setDeviceSettingPackages(arrayOf(deviceSettingPackage1, deviceSettingPackage2))
        whenever(appFunctionAccessService.getValidTargets(any()))
            .thenReturn(
                listOf(nonDeviceSettingPackage, deviceSettingPackage1, deviceSettingPackage2)
            )

        val validTargets = serviceImpl.getValidTargets(0)

        assertThat(validTargets).containsExactly("android", nonDeviceSettingPackage)
    }

    @RequiresFlagsEnabled(
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    )
    @Test
    fun onBootPhase_writeAllowlistToStorage_whenDeviceConfigValid() {
        val signatureString = "com.example.test1:abcdef0123456789"
        setDeviceConfigAllowlist(signatureString)

        serviceImpl.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY)

        verify(agentAllowlistStorage).writeCurrentAllowlist(signatureString)
    }

    @RequiresFlagsEnabled(
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    )
    @Test
    fun onBootPhase_readPreviousAllowlist_whenDeviceConfigInvalid() {
        val validPackages = listOf(SignedPackage("com.valid.package", byteArrayOf()))
        val invalidSignatureString = "com.example.test1:invalid_certificate_string"
        setDeviceConfigAllowlist(invalidSignatureString)
        whenever(agentAllowlistStorage.readPreviousValidAllowlist()).thenReturn(validPackages)

        serviceImpl.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY)

        verify(agentAllowlistStorage).readPreviousValidAllowlist()
        verify(agentAllowlistStorage, never()).writeCurrentAllowlist(invalidSignatureString)
    }

    @RequiresFlagsEnabled(
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    )
    @Test
    @RequiresPermission(Manifest.permission.MANAGE_APP_FUNCTION_ACCESS)
    fun onBootPhase_initiateAgentAllowlistToDeviceConfig_whenDeviceConfigHasValue() {
        setPreloadedAllowlist(arrayOf("com.example.preload1:111111", "com.example.preload2:222222"))
        setDeviceConfigAllowlist("com.example.device1:aaaaaa;com.example.device2:bbbbbb")

        serviceImpl.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY)

        verify(appFunctionAccessService).setAgentAllowlist(agentAllowlistCaptor.capture())
        val capturedSet = agentAllowlistCaptor.firstValue
        val expectedPackage1 =
            SignedPackage("com.example.device1", Signature("aaaaaa").toByteArray())
        val expectedPackage2 =
            SignedPackage("com.example.device2", Signature("bbbbbb").toByteArray())
        assertThat(capturedSet).containsAtLeast(expectedPackage1, expectedPackage2)
    }

    @RequiresFlagsEnabled(
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    )
    @Test
    fun onBootPhase_initiateAgentAllowlistToStored_whenDeviceConfigInvalid() {
        setDeviceConfigAllowlist("invalid-pkg:invalid_certificate_string")
        val stored = SignedPackageParser.parseList("com.example.test2:abcdef0123456789")
        whenever(agentAllowlistStorage.readPreviousValidAllowlist()).thenReturn(stored)
        setPreloadedAllowlist(arrayOf("com.example.preload1:111111", "com.example.preload2:222222"))

        serviceImpl.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY)

        verify(appFunctionAccessService).setAgentAllowlist(agentAllowlistCaptor.capture())
        val capturedSet = agentAllowlistCaptor.firstValue
        val expectedPackage =
            SignedPackage("com.example.test2", Signature("abcdef0123456789").toByteArray())
        assertThat(capturedSet).contains(expectedPackage)
    }

    @RequiresFlagsEnabled(
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    )
    @Test
    fun onBootPhase_initiateAgentAllowlistToStored_whenDeviceConfigNull() {
        setDeviceConfigAllowlist(null)
        val stored = SignedPackageParser.parseList("com.example.test2:abcdef0123456789")
        whenever(agentAllowlistStorage.readPreviousValidAllowlist()).thenReturn(stored)
        setPreloadedAllowlist(arrayOf("com.example.preload1:111111", "com.example.preload2:222222"))

        serviceImpl.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY)

        verify(appFunctionAccessService).setAgentAllowlist(agentAllowlistCaptor.capture())
        val capturedSet = agentAllowlistCaptor.firstValue
        val expectedPackages =
            SignedPackage("com.example.test2", Signature("abcdef0123456789").toByteArray())
        assertThat(capturedSet).contains(expectedPackages)
    }

    @RequiresFlagsEnabled(
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    )
    @Test
    fun onBootPhase_initiateAgentAllowlistToStored_whenDeviceConfigEmpty() {
        setDeviceConfigAllowlist("")
        val stored = SignedPackageParser.parseList("com.example.test2:abcdef0123456789")
        whenever(agentAllowlistStorage.readPreviousValidAllowlist()).thenReturn(stored)
        setPreloadedAllowlist(arrayOf("com.example.preload1:111111", "com.example.preload2:222222"))

        serviceImpl.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY)

        verify(appFunctionAccessService).setAgentAllowlist(agentAllowlistCaptor.capture())
        val capturedSet = agentAllowlistCaptor.firstValue
        val expectedPackages =
            SignedPackage("com.example.test2", Signature("abcdef0123456789").toByteArray())
        assertThat(capturedSet).contains(expectedPackages)
    }

    @RequiresFlagsEnabled(
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    )
    @Test
    fun onBootPhase_initiateAgentAllowlistToPreload_whenDeviceConfigNullNoStored() {
        setDeviceConfigAllowlist(null)
        setPreloadedAllowlist(arrayOf("com.example.preload1:111111", "com.example.preload2:222222"))
        whenever(agentAllowlistStorage.readPreviousValidAllowlist()).thenReturn(null)

        serviceImpl.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY)

        verify(appFunctionAccessService).setAgentAllowlist(agentAllowlistCaptor.capture())
        val capturedSet = agentAllowlistCaptor.firstValue
        val expectedPackage1 =
            SignedPackage("com.example.preload1", Signature("111111").toByteArray())
        val expectedPackage2 =
            SignedPackage("com.example.preload2", Signature("222222").toByteArray())
        assertThat(capturedSet).containsAtLeast(expectedPackage1, expectedPackage2)
    }

    @RequiresFlagsEnabled(
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    )
    @Test
    fun onBootPhase_initiateAgentAllowlistToPreload_whenDeviceConfigEmptyNoStored() {
        setDeviceConfigAllowlist("")
        setPreloadedAllowlist(arrayOf("com.example.preload1:111111", "com.example.preload2:222222"))
        whenever(agentAllowlistStorage.readPreviousValidAllowlist()).thenReturn(null)

        serviceImpl.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY)

        verify(appFunctionAccessService).setAgentAllowlist(agentAllowlistCaptor.capture())
        val capturedSet = agentAllowlistCaptor.firstValue
        val expectedPackage1 =
            SignedPackage("com.example.preload1", Signature("111111").toByteArray())
        val expectedPackage2 =
            SignedPackage("com.example.preload2", Signature("222222").toByteArray())
        assertThat(capturedSet).containsAtLeast(expectedPackage1, expectedPackage2)
    }

    @RequiresFlagsEnabled(
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    )
    @Test
    fun onBootPhase_initiateAgentAllowlistToPreload_whenDeviceConfigInvalidAndNoStored() {
        setDeviceConfigAllowlist("invalid-pkg:invalid_certificate_string")
        whenever(agentAllowlistStorage.readPreviousValidAllowlist()).thenReturn(null)
        setPreloadedAllowlist(arrayOf("com.example.preload1:111111", "com.example.preload2:222222"))

        serviceImpl.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY)

        verify(appFunctionAccessService).setAgentAllowlist(agentAllowlistCaptor.capture())
        val capturedSet = agentAllowlistCaptor.firstValue
        val expectedPackage1 =
            SignedPackage("com.example.preload1", Signature("111111").toByteArray())
        val expectedPackage2 =
            SignedPackage("com.example.preload2", Signature("222222").toByteArray())
        assertThat(capturedSet).containsAtLeast(expectedPackage1, expectedPackage2)
    }

    @RequiresFlagsEnabled(
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    )
    @Test
    fun onUserUnlocked_shouldUnlockTargetUserStorage() {
        val targetUser = TargetUser(UserInfo(context.userId, "testUser", 0))

        serviceImpl.onUserUnlocked(targetUser)

        verify(multiUserAccessHistory, times(1)).onUserUnlocked(eq(targetUser))
    }

    @RequiresFlagsEnabled(
        FLAG_APP_FUNCTION_ACCESS_SERVICE_ENABLED,
        FLAG_APP_FUNCTION_ACCESS_API_ENABLED,
    )
    @Test
    fun onUserStopping_shouldStopTargetUserStorage() {
        val targetUser = TargetUser(UserInfo(context.userId, "testUser", 0))

        serviceImpl.onUserUnlocked(targetUser)
        serviceImpl.onUserStopping(targetUser)

        verify(multiUserAccessHistory, times(1)).onUserStopping(eq(targetUser))
    }

    private fun setDeviceSettingPackages(deviceSettings: Array<String>) {
        context.orCreateTestableResources.addOverride(
            R.array.config_appFunctionDeviceSettingsPackages,
            deviceSettings,
        )
    }

    private fun clearDeviceSettingPackages() {
        context.orCreateTestableResources.removeOverride(
            R.array.config_appFunctionDeviceSettingsPackages
        )
    }

    private fun setPreloadedAllowlist(allowlist: Array<String>) {
        context.orCreateTestableResources.addOverride(
            com.android.internal.R.array.config_defaultAppFunctionAgentAllowlist,
            allowlist,
        )
    }

    private fun clearPreloadedAllowlist() {
        context.orCreateTestableResources.removeOverride(
            com.android.internal.R.array.config_defaultAppFunctionAgentAllowlist
        )
    }

    private fun setDeviceConfigAllowlist(allowlist: String?) {
        whenever(
                DeviceConfig.getString(
                    eq("machine_learning"),
                    eq("allowlisted_app_functions_agents"),
                    anyOrNull(),
                )
            )
            .thenReturn(allowlist)
    }
}
