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

import android.app.IUriGrantsManager
import android.app.appfunctions.AppFunctionAccessServiceInterface
import android.app.appfunctions.flags.Flags
import android.content.pm.PackageManagerInternal
import android.os.IBinder
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.DeviceConfig
import android.testing.TestableContext
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.modules.utils.testing.ExtendedMockitoRule
import com.android.server.LocalServices
import com.android.server.appfunctions.allowlist.AppFunctionAllowlistReader
import com.android.server.appfunctions.dynamic.MultiUserDynamicAppFunctionRegistry
import com.android.server.appfunctions.reader.AppFunctionMetadataReader
import com.android.server.appinteraction.AppInteractionService
import com.android.server.uri.UriGrantsManagerInternal
import com.android.server.wm.ActivityTaskManagerInternal
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
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

    private val dynamicRegistry = mock<MultiUserDynamicAppFunctionRegistry>()

    private val appInteractionService = mock<AppInteractionService>()

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
            dynamicRegistry,
            appInteractionService,
            mock<AppFunctionMetadataReader>(),
            mock<ActivityTaskManagerInternal>(),
            mock<AppFunctionAllowlistReader>(),
        )

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
}
