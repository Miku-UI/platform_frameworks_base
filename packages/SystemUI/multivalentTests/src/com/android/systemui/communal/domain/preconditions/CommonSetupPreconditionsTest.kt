/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.communal.domain.preconditions

import android.content.Intent
import android.content.packageManager
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.data.repository.fakePackageChangeRepository
import com.android.systemui.common.domain.interactor.packageChangeInteractor
import com.android.systemui.common.shared.model.PackageChangeModel
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.power.data.repository.fakePowerRepository
import com.android.systemui.power.data.repository.powerRepository
import com.android.systemui.settings.FakeUserTracker
import com.android.systemui.settings.userTracker
import com.android.systemui.statusbar.pipeline.shared.data.repository.connectivityRepository
import com.android.systemui.statusbar.pipeline.shared.data.repository.fake
import com.android.systemui.statusbar.policy.deviceProvisionedController
import com.android.systemui.statusbar.policy.fakeDeviceProvisionedController
import com.android.systemui.testKosmos
import com.android.systemui.user.domain.interactor.selectedUserInteractor
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.whenever
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.verify

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class CommonSetupPreconditionsTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    private val Kosmos.underTest by
        Kosmos.Fixture {
            CommonSetupPreconditionsImpl(
                context = context,
                deviceProvisionedController = kosmos.deviceProvisionedController,
                packageChangeInteractor = kosmos.packageChangeInteractor,
                selectedUserInteractor = kosmos.selectedUserInteractor,
                userContextProvider = kosmos.userTracker,
                keyguardInteractor = kosmos.keyguardInteractor,
                connectivityRepository = kosmos.connectivityRepository,
                powerRepository = kosmos.powerRepository,
                bgScope = kosmos.testScope.backgroundScope,
                bgDispatcher = kosmos.testDispatcher,
            )
        }

    private val mainUserHandle = UserHandle.of(0)

    companion object {
        private const val TEST_APP_ID = 10000
        private const val MIN_DREAM_COUNT = 4
    }

    @Before
    fun setUp() {
        kosmos.userTracker = FakeUserTracker(onCreateCurrentUserContext = { context })
        context.setMockPackageManager(kosmos.packageManager)

        // Default valid state
        kosmos.fakeDeviceProvisionedController.deviceProvisioned = true
        kosmos.fakeDeviceProvisionedController.currentUser = 0
        kosmos.fakeDeviceProvisionedController.setUserSetup(0, true)
        kosmos.connectivityRepository.fake.setCarrierMergedConnected(validated = true)
        kosmos.keyguardInteractor.setDreaming(false)
        kosmos.fakePowerRepository.setInteractive(true)
        setEnoughDreamsAvailable()
    }

    private fun setEnoughDreamsAvailable() {
        val dreams = List(MIN_DREAM_COUNT) { ResolveInfo() }
        whenever(kosmos.packageManager.queryIntentServices(any(Intent::class.java), anyInt()))
            .thenReturn(dreams)
        whenever(
                kosmos.packageManager.queryIntentServices(
                    any(Intent::class.java),
                    any<PackageManager.ResolveInfoFlags>(),
                )
            )
            .thenReturn(dreams)
    }

    private fun setNotEnoughDreamsAvailable() {
        val dreams = emptyList<ResolveInfo>()
        whenever(kosmos.packageManager.queryIntentServices(any(Intent::class.java), anyInt()))
            .thenReturn(dreams)
        whenever(
                kosmos.packageManager.queryIntentServices(
                    any(Intent::class.java),
                    any<PackageManager.ResolveInfoFlags>(),
                )
            )
            .thenReturn(dreams)
    }

    @Test
    fun allMet_whenAllConditionsMet_isTrue() =
        kosmos.runTest {
            val allMet by collectLastValue(underTest.allMet)
            assertThat(allMet).isTrue()
        }

    @Test
    fun allMet_whenDeviceNotProvisioned_isFalse() =
        kosmos.runTest {
            fakeDeviceProvisionedController.deviceProvisioned = false
            val allMet by collectLastValue(underTest.allMet)

            assertThat(allMet).isFalse()
        }

    @Test
    fun allMet_whenUserNotSetup_isFalse() =
        kosmos.runTest {
            fakeDeviceProvisionedController.setUserSetup(0, false)
            val allMet by collectLastValue(underTest.allMet)

            assertThat(allMet).isFalse()
        }

    @Test
    fun allMet_whenNetworkNotValidated_isFalse() =
        kosmos.runTest {
            connectivityRepository.fake.setCarrierMergedConnected(validated = false)
            val allMet by collectLastValue(underTest.allMet)

            assertThat(allMet).isFalse()
        }

    @Test
    fun allMet_whenDreaming_isFalse() =
        kosmos.runTest {
            keyguardInteractor.setDreaming(true)
            testScope.runCurrent()
            val allMet by collectLastValue(underTest.allMet)

            assertThat(allMet).isFalse()
        }

    @Test
    fun allMet_whenNotInteractive_isFalse() =
        kosmos.runTest {
            fakePowerRepository.setInteractive(false)
            testScope.runCurrent()
            val allMet by collectLastValue(underTest.allMet)

            assertThat(allMet).isFalse()
        }

    @Test
    fun allMet_whenNotEnoughDreams_isFalse() =
        kosmos.runTest {
            setNotEnoughDreamsAvailable()
            val allMet by collectLastValue(underTest.allMet)

            assertThat(allMet).isFalse()
        }

    @Test
    fun allMet_whenSufficientDreams_isTrue() =
        kosmos.runTest {
            setEnoughDreamsAvailable()
            val allMet by collectLastValue(underTest.allMet)

            assertThat(allMet).isTrue()
        }

    @Test
    fun allMet_updatesOnPackageAdded() =
        kosmos.runTest {
            // Start with not enough dreams
            setNotEnoughDreamsAvailable()
            val allMet by collectLastValue(underTest.allMet)
            assertThat(allMet).isFalse()

            // After a package is added, we now have enough dreams.
            setEnoughDreamsAvailable()
            clearInvocations(kosmos.packageManager)
            fakePackageChangeRepository.notifyChange(
                PackageChangeModel.Installed(
                    packageName = "com.android.settings",
                    packageUid = UserHandle.getUid(mainUserHandle.identifier, TEST_APP_ID),
                )
            )
            testScope.advanceTimeBy(1000L)
            verify(kosmos.packageManager, atLeastOnce())
                .queryIntentServices(any(Intent::class.java), anyInt())
            assertThat(allMet).isTrue()
        }

    @Test
    fun allMet_updatesOnPackageRemoved() =
        kosmos.runTest {
            // Start with enough dreams
            setEnoughDreamsAvailable()
            val allMet by collectLastValue(underTest.allMet)
            assertThat(allMet).isTrue()

            // After a package is removed, we no longer have enough.
            setNotEnoughDreamsAvailable()
            clearInvocations(kosmos.packageManager)
            fakePackageChangeRepository.notifyChange(
                PackageChangeModel.Uninstalled(
                    packageName = "com.android.settings",
                    packageUid = UserHandle.getUid(mainUserHandle.identifier, TEST_APP_ID),
                )
            )
            testScope.advanceTimeBy(1000L)
            verify(kosmos.packageManager, atLeastOnce())
                .queryIntentServices(any(Intent::class.java), anyInt())
            assertThat(allMet).isFalse()
        }

    @Test
    fun allMet_networkDebounced() =
        kosmos.runTest {
            // Start with valid network
            connectivityRepository.fake.setCarrierMergedConnected(validated = true)
            val allMet by collectLastValue(underTest.allMet)

            // Let debounce settle
            testScope.advanceTimeBy(1000L)
            assertThat(allMet).isTrue()

            // Become invalid
            connectivityRepository.fake.setCarrierMergedConnected(validated = false)
            testScope.runCurrent()
            // Should be false immediately
            assertThat(allMet).isFalse()

            // Become valid again
            connectivityRepository.fake.setCarrierMergedConnected(validated = true)
            testScope.runCurrent()
            // Should still be false due to debounce
            assertThat(allMet).isFalse()

            // Advance past debounce
            testScope.advanceTimeBy(600L)
            assertThat(allMet).isTrue()
        }

    @Test
    fun listenerRemoved_whenFlowCancelled() =
        kosmos.runTest {
            val initialCallbackCount = kosmos.fakeDeviceProvisionedController.callbackCount

            // Launching the flow should cause a listener to be registered.
            val job = underTest.allMet.launchIn(testScope)
            testScope.runCurrent()

            // Verify a callback was added.
            assertThat(kosmos.fakeDeviceProvisionedController.callbackCount)
                .isEqualTo(initialCallbackCount + 1)

            // Cancelling the job should cause the listener to be removed.
            job.cancel()
            testScope.runCurrent()

            // Verify the callback was removed.
            assertThat(kosmos.fakeDeviceProvisionedController.callbackCount)
                .isEqualTo(initialCallbackCount)
        }
}
