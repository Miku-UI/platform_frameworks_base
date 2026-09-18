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

package com.android.systemui.statusbar.quickactions.assistant.domain.interactor

import android.content.ComponentName
import android.content.res.mainResources
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.deviceentry.domain.interactor.deviceEntryInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.res.R
import com.android.systemui.scene.SceneHelper.setDeviceEntered
import com.android.systemui.statusbar.policy.data.repository.fakeDeviceProvisioningRepository
import com.android.systemui.statusbar.policy.data.repository.fakeUserSetupRepository
import com.android.systemui.statusbar.policy.domain.interactor.deviceProvisioningInteractor
import com.android.systemui.statusbar.policy.domain.interactor.userSetupInteractor
import com.android.systemui.statusbar.quickactions.assistant.data.repository.fakeAssistantRepository
import com.android.systemui.statusbar.quickactions.assistant.shared.model.AssistantIconSharedModel
import com.android.systemui.testKosmosNew
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.user.domain.interactor.userLogoutInteractor
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AssistantIconInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val userRepository = kosmos.fakeUserRepository
    private val userSetupRepository = kosmos.fakeUserSetupRepository
    private val deviceProvisioningRepository = kosmos.fakeDeviceProvisioningRepository
    private val assistantRepository = kosmos.fakeAssistantRepository

    private val Kosmos.underTest by
        Kosmos.Fixture {
            AssistantIconInteractorImpl(
                resources = kosmos.mainResources,
                scope = kosmos.applicationCoroutineScope,
                userSetupInteractor = userSetupInteractor,
                userLogoutInteractor = userLogoutInteractor,
                deviceProvisioningInteractor = deviceProvisioningInteractor,
                deviceEntryInteractor = deviceEntryInteractor,
                assistantRepository = assistantRepository,
            )
        }

    @Before
    fun setup() {
        overrideResource(R.string.config_statusBarAssistantPackage, ASSISTANT_PACKAGE)
        setUserLoggedIn(true)
        userSetupRepository.setUserSetUp(true)
        deviceProvisioningRepository.setDeviceProvisioned(true)
    }

    @Test
    fun model_isDefault_whenDeviceNotEntered() =
        kosmos.runTest {
            assistantRepository.setAssistInfo(ComponentName(ASSISTANT_PACKAGE, ASSISTANT_CLASS))

            val assistInfo by collectLastValue(assistantRepository.assistInfo)
            val deviceEntered by collectLastValue(deviceEntryInteractor.isDeviceEntered)
            val assistantIconSharedModel by collectLastValue(underTest.assistantIconSharedModel)

            assertThat(assistInfo).isNotNull()
            assertThat(assistInfo!!.packageName).isEqualTo(ASSISTANT_PACKAGE)

            assertThat(deviceEntered).isFalse()

            assertThat(assistantIconSharedModel).isEqualTo(DEFAULT_MODEL)
        }

    @Test
    fun model_isDefault_whenUserNotSetup() =
        kosmos.runTest {
            assistantRepository.setAssistInfo(ComponentName(ASSISTANT_PACKAGE, ASSISTANT_CLASS))
            setDeviceEntered()
            userSetupRepository.setUserSetUp(false)

            val assistantIconSharedModel by collectLastValue(underTest.assistantIconSharedModel)

            assertThat(assistantIconSharedModel).isEqualTo(DEFAULT_MODEL)
        }

    @Test
    fun model_isDefault_whenDeviceNotProvisioned() =
        kosmos.runTest {
            assistantRepository.setAssistInfo(ComponentName(ASSISTANT_PACKAGE, ASSISTANT_CLASS))
            setDeviceEntered()
            deviceProvisioningRepository.setDeviceProvisioned(false)

            val assistantIconSharedModel by collectLastValue(underTest.assistantIconSharedModel)

            assertThat(assistantIconSharedModel).isEqualTo(DEFAULT_MODEL)
        }

    @Test
    fun model_isDefault_whenUserSignedOut() =
        kosmos.runTest {
            assistantRepository.setAssistInfo(ComponentName(ASSISTANT_PACKAGE, ASSISTANT_CLASS))
            setDeviceEntered()
            setUserLoggedIn(false)

            val assistInfo by collectLastValue(assistantRepository.assistInfo)
            val deviceEntered by collectLastValue(deviceEntryInteractor.isDeviceEntered)
            val userLoggedIn by collectLastValue(userLogoutInteractor.isLogoutEnabled)
            val assistantIconSharedModel by collectLastValue(underTest.assistantIconSharedModel)

            assertThat(assistInfo).isNotNull()
            assertThat(assistInfo!!.packageName).isEqualTo(ASSISTANT_PACKAGE)
            assertThat(deviceEntered).isTrue()

            assertThat(userLoggedIn).isFalse()

            assertThat(assistantIconSharedModel).isEqualTo(DEFAULT_MODEL)
        }

    @Test
    fun model_isDefault_whenAssistPackageNameIsEmpty() =
        kosmos.runTest {
            assistantRepository.setAssistInfo(ComponentName("", ASSISTANT_CLASS))
            setDeviceEntered()

            val assistInfo by collectLastValue(assistantRepository.assistInfo)
            val deviceEntered by collectLastValue(deviceEntryInteractor.isDeviceEntered)
            val assistantIconSharedModel by collectLastValue(underTest.assistantIconSharedModel)

            assertThat(assistInfo).isNotNull()
            assertThat(assistInfo!!.packageName).isEmpty()

            assertThat(deviceEntered).isTrue()

            assertThat(assistantIconSharedModel).isEqualTo(DEFAULT_MODEL)
        }

    @Test
    fun isStatusBarAssistantPackage_isFalse_whenPackageNameNotMatch() =
        kosmos.runTest {
            assistantRepository.setAssistInfo(ComponentName(ASSISTANT_PACKAGE_2, ASSISTANT_CLASS))
            setDeviceEntered()

            val assistantIconSharedModel by collectLastValue(underTest.assistantIconSharedModel)

            assertThat(assistantIconSharedModel!!.assistInfo).isNotNull()
            assertThat(assistantIconSharedModel!!.isStatusBarAssistantPackage).isFalse()
        }

    @Test
    fun isStatusBarAssistantPackage_isTrue_whenPackageNameMatch() =
        kosmos.runTest {
            assistantRepository.setAssistInfo(ComponentName(ASSISTANT_PACKAGE, ASSISTANT_CLASS))
            setDeviceEntered()

            val assistantIconSharedModel by collectLastValue(underTest.assistantIconSharedModel)

            assertThat(assistantIconSharedModel!!.assistInfo).isNotNull()
            assertThat(assistantIconSharedModel!!.isStatusBarAssistantPackage).isTrue()
        }

    private fun setUserLoggedIn(isLoggedIn: Boolean) {
        // Note: logout enabled means the user is currently in logged in status.
        userRepository.setUserManagerLogoutEnabled(isLoggedIn)
    }

    private companion object {
        private const val ASSISTANT_PACKAGE = "the.assistant.app"
        private const val ASSISTANT_PACKAGE_2 = "the.assistant.app2"
        private const val ASSISTANT_CLASS = "the.assistant.app.class"
        private val DEFAULT_MODEL =
            AssistantIconSharedModel(
                assistInfo = null,
                isStatusBarAssistantPackage = false,
                isAssistShown = false,
            )
    }
}
