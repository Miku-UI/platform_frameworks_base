/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.os.Build
import com.android.server.testutils.any
import com.android.server.testutils.spy
import com.android.server.testutils.whenever
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import kotlin.test.assertFailsWith

@RunWith(JUnit4::class)
class PackageFreezerTest {

    companion object {
        const val TEST_PACKAGE = "com.android.test.package"
        const val TEST_REASON = "test reason"
        const val TEST_EXIT_REASON = 1
        const val TEST_USER_ID = 0
    }

    @Rule
    @JvmField
    val rule = MockSystemRule()

    lateinit var pms: PackageManagerService

    private fun createPackageManagerService(vararg stageExistingPackages: String):
            PackageManagerService {
        stageExistingPackages.forEach {
            rule.system().stageScanExistingPackage(it, 1L,
                rule.system().dataAppDirectory)
        }

        val sdkVersion = Build.VERSION_CODES.CUR_DEVELOPMENT
        val sdkVersionFull = if (android.sdk.Flags.majorMinorVersioningScheme())
            Build.parseFullVersion(sdkVersion.toString())
        else
            0
        var pms = PackageManagerService(rule.mocks().injector,
            false /*factoryTest*/,
            MockSystem.DEFAULT_VERSION_INFO.fingerprint,
            false /*isEngBuild*/,
            false /*isUserDebugBuild*/,
            sdkVersion,
            Build.VERSION.INCREMENTAL,
            sdkVersionFull)
        rule.system().validateFinalState()
        return pms
    }

    private fun frozenMessage(packageName: String) = "Package $packageName is currently frozen!"

    private fun <T : Throwable> assertThrowContainsMessage(
        exceptionClass: kotlin.reflect.KClass<T>,
        message: String,
        block: () -> Unit
    ) {
        assertThat(assertFailsWith(exceptionClass, block).message).contains(message)
    }

    private fun checkPackageStartable() {
        pms.checkPackageStartable(pms.snapshotComputer(), TEST_PACKAGE, TEST_USER_ID)
    }

    @Before
    @Throws(Exception::class)
    fun setup() {
        rule.system().stageNominalSystemState()
        pms = spy(createPackageManagerService(TEST_PACKAGE))
        whenever(pms.killApplication(any(), any(), any(), any(), any()))
        whenever(pms.killApplicationSync(any(), any(), any(), any(), any()))
        whenever(pms.stopAndKillApplication(any(), any(), any(), any(), any()))
    }

    @Test
    fun freezePackage() {
        val freezer = PackageFreezer(TEST_PACKAGE, TEST_USER_ID, TEST_REASON, pms,
                TEST_EXIT_REASON, null /* request */)
        verify(pms, times(1))
            .killApplication(eq(TEST_PACKAGE), any(), eq(TEST_USER_ID), eq(TEST_REASON),
                    eq(TEST_EXIT_REASON))

        assertThrowContainsMessage(SecurityException::class, frozenMessage(TEST_PACKAGE)) {
            checkPackageStartable()
        }

        freezer.close()
        checkPackageStartable()
    }

    @Test
    fun freezePackage_twice() {
        val freezer1 = PackageFreezer(TEST_PACKAGE, TEST_USER_ID, TEST_REASON, pms,
                TEST_EXIT_REASON, null  /* request */)
        val freezer2 = PackageFreezer(TEST_PACKAGE, TEST_USER_ID, TEST_REASON, pms,
                TEST_EXIT_REASON, null  /* request */)
        verify(pms, times(2))
            .killApplication(eq(TEST_PACKAGE), any(), eq(TEST_USER_ID), eq(TEST_REASON),
                    eq(TEST_EXIT_REASON))

        assertThrowContainsMessage(SecurityException::class, frozenMessage(TEST_PACKAGE)) {
            checkPackageStartable()
        }

        freezer1.close()
        assertThrowContainsMessage(SecurityException::class, frozenMessage(TEST_PACKAGE)) {
            checkPackageStartable()
        }

        freezer2.close()
        checkPackageStartable()
    }

    @Test
    fun freezePackage_withoutClosing() {
        var freezer: PackageFreezer? = PackageFreezer(TEST_PACKAGE, TEST_USER_ID, TEST_REASON, pms,
                TEST_EXIT_REASON, null  /* request */)
        verify(pms, times(1))
            .killApplication(eq(TEST_PACKAGE), any(), eq(TEST_USER_ID), eq(TEST_REASON),
                    eq(TEST_EXIT_REASON))

        assertThrowContainsMessage(SecurityException::class, frozenMessage(TEST_PACKAGE)) {
            checkPackageStartable()
        }

        freezer = null
        System.gc()
        System.runFinalization()

        checkPackageStartable()
    }

    @Test
    fun freezePackage_waitAppKilled() {
        // This constructor should result in a call to killApplicationSync
        val freezer = PackageFreezer(TEST_PACKAGE, TEST_USER_ID, TEST_REASON, pms,
                TEST_EXIT_REASON, null /* request */, true /* waitAppKilled */)
        verify(pms, times(1))
                .killApplicationSync(eq(TEST_PACKAGE), any(), eq(TEST_USER_ID), eq(TEST_REASON),
                        eq(TEST_EXIT_REASON))

        assertThrowContainsMessage(SecurityException::class, frozenMessage(TEST_PACKAGE)) {
            checkPackageStartable()
        }

        freezer.close()
        checkPackageStartable()
    }

    @Test
    fun freezePackage_waitAppStopped() {
        // This constructor should result in a call to stopAndKillApplication
        val freezer = PackageFreezer(TEST_PACKAGE, TEST_USER_ID, TEST_REASON, pms,
                TEST_EXIT_REASON, null /* request */, false /* waitAppKilled */,
                true /* waitAppStopped */)
        verify(pms, times(1))
                .stopAndKillApplication(eq(TEST_PACKAGE), any(), eq(TEST_USER_ID), eq(TEST_REASON),
                        eq(TEST_EXIT_REASON))

        assertThrowContainsMessage(SecurityException::class, frozenMessage(TEST_PACKAGE)) {
            checkPackageStartable()
        }

        freezer.close()
        checkPackageStartable()
    }

    @Test
    fun freezePackage_withInstallRequest() {
        // Verifies that onFreezeStarted is called but onStopAndKillStarted is not when
        // an InstallRequest is provided without waitAppStopped.
        val installRequest = mock(InstallRequest::class.java)
        val freezer = PackageFreezer(TEST_PACKAGE, TEST_USER_ID, TEST_REASON, pms,
                TEST_EXIT_REASON, installRequest)

        // Verify killApplication is called and metrics are started correctly.
        verify(pms, times(1))
            .killApplication(eq(TEST_PACKAGE), any(), eq(TEST_USER_ID), eq(TEST_REASON),
                    eq(TEST_EXIT_REASON))
        verify(installRequest, times(1)).onFreezeStarted()
        verify(installRequest, never()).onStopAndKillStarted()

        // Ensure package is frozen.
        assertThrowContainsMessage(SecurityException::class, frozenMessage(TEST_PACKAGE)) {
            checkPackageStartable()
        }

        // Close the freezer and verify metrics are completed.
        freezer.close()
        verify(installRequest, times(1)).onFreezeCompleted()
        checkPackageStartable()
    }

    @Test
    fun freezePackage_waitAppKilled_withInstallRequest() {
        // Verifies that onFreezeStarted is called but onStopAndKillStarted is not when
        // an InstallRequest is provided with waitAppKilled but not waitAppStopped.
        val installRequest = mock(InstallRequest::class.java)
        val freezer = PackageFreezer(TEST_PACKAGE, TEST_USER_ID, TEST_REASON, pms,
                TEST_EXIT_REASON, installRequest, true /* waitAppKilled */)

        // Verify killApplicationSync is called and metrics are started correctly.
        verify(pms, times(1))
                .killApplicationSync(eq(TEST_PACKAGE), any(), eq(TEST_USER_ID), eq(TEST_REASON),
                        eq(TEST_EXIT_REASON))
        verify(installRequest, times(1)).onFreezeStarted()
        verify(installRequest, never()).onStopAndKillStarted()

        // Ensure package is frozen.
        assertThrowContainsMessage(SecurityException::class, frozenMessage(TEST_PACKAGE)) {
            checkPackageStartable()
        }

        // Close the freezer and verify metrics are completed.
        freezer.close()
        verify(installRequest, times(1)).onFreezeCompleted()
        checkPackageStartable()
    }

    @Test
    fun freezePackage_waitAppStopped_withInstallRequest() {
        // Verifies that both onFreezeStarted and onStopAndKillStarted are called when
        // an InstallRequest is provided and waitAppStopped is true.
        val installRequest = mock(InstallRequest::class.java)
        val freezer = PackageFreezer(TEST_PACKAGE, TEST_USER_ID, TEST_REASON, pms,
                TEST_EXIT_REASON, installRequest, false /* waitAppKilled */,
                true /* waitAppStopped */)

        // Verify stopAndKillApplication is called and metrics are started correctly.
        verify(pms, times(1))
                .stopAndKillApplication(eq(TEST_PACKAGE), any(), eq(TEST_USER_ID), eq(TEST_REASON),
                        eq(TEST_EXIT_REASON))
        verify(installRequest, times(1)).onFreezeStarted()
        verify(installRequest, times(1)).onStopAndKillStarted()

        // Ensure package is frozen.
        assertThrowContainsMessage(SecurityException::class, frozenMessage(TEST_PACKAGE)) {
            checkPackageStartable()
        }

        // Close the freezer and verify metrics are completed.
        freezer.close()
        verify(installRequest, times(1)).onFreezeCompleted()
        checkPackageStartable()
    }

    @Test
    fun stubFreezer_withInstallRequest() {
        // Verifies that the stub freezer correctly handles InstallRequest metrics.
        val installRequest = mock(InstallRequest::class.java)
        val freezer = PackageFreezer(pms, installRequest)

        // Verify metrics are started, but no kill calls are made.
        verify(installRequest, times(1)).onFreezeStarted()
        verify(pms, never()).killApplication(any(), any(), any(), any(), any())
        verify(pms, never()).killApplicationSync(any(), any(), any(), any(), any())
        verify(pms, never()).stopAndKillApplication(any(), any(), any(), any(), any())

        // Closing should complete the metrics.
        freezer.close()
        verify(installRequest, times(1)).onFreezeCompleted()
    }
}
