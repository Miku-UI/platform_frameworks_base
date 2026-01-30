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

package com.android.systemui.wallpapers.domain.interactor

import android.hardware.display.DisplayManagerGlobal
import android.view.Display
import android.view.DisplayAdjustments
import android.view.DisplayInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.keyguard.keyguardDisplayManager
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.policy.data.repository.fakeDeviceProvisioningRepository
import com.android.systemui.testKosmos
import com.android.systemui.wallpapers.domain.interactor.DisplayWallpaperPresentationInteractor.WallpaperPresentationType.KEYGUARD
import com.android.systemui.wallpapers.domain.interactor.DisplayWallpaperPresentationInteractor.WallpaperPresentationType.NONE
import com.android.systemui.wallpapers.domain.interactor.DisplayWallpaperPresentationInteractor.WallpaperPresentationType.PROVISIONING
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@SmallTest
@RunWith(AndroidJUnit4::class)
class DisplayWallpaperPresentationInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val deviceUnlockedInteractor = kosmos.keyguardInteractor
    private val fakeKeyguardRepository = kosmos.fakeKeyguardRepository
    private val deviceProvisioningRepository = kosmos.fakeDeviceProvisioningRepository
    private val keyguardDisplayManager = kosmos.keyguardDisplayManager
    private val testDisplayInfo = DisplayInfo()
    private val testDisplay: Display =
        Display(
            DisplayManagerGlobal.getInstance(),
            /* displayId= */ 2,
            testDisplayInfo,
            DisplayAdjustments.DEFAULT_DISPLAY_ADJUSTMENTS,
        )
    private val wallpaperPresentationInteractor =
        DisplayWallpaperPresentationInteractorImpl(
            display = testDisplay,
            displayCoroutineScope = kosmos.testScope.backgroundScope,
            keyguardInteractor = { deviceUnlockedInteractor },
            deviceProvisioningRepository = { deviceProvisioningRepository },
            keyguardDisplayManager = { keyguardDisplayManager },
        )

    @Before
    fun setUp() {
        fakeKeyguardRepository.setKeyguardShowing(isShowing = false)
        deviceProvisioningRepository.setDeviceProvisioned(true)
    }

    @Test
    fun presentationFactoryFlow_unlocked_provisioned_none() =
        kosmos.runTest {
            fakeKeyguardRepository.setKeyguardShowing(isShowing = false)
            deviceProvisioningRepository.setDeviceProvisioned(true)

            val actual by collectLastValue(wallpaperPresentationInteractor.presentationFactoryFlow)
            assertThat(actual).isEqualTo(NONE)
        }

    @Test
    fun presentationFactoryFlow_locked_provisioned_displayCompatible_keyguard() =
        kosmos.runTest {
            fakeKeyguardRepository.setKeyguardShowing(isShowing = true)
            deviceProvisioningRepository.setDeviceProvisioned(true)

            val actual by collectLastValue(wallpaperPresentationInteractor.presentationFactoryFlow)
            assertThat(actual).isEqualTo(KEYGUARD)
        }

    @Test
    fun presentationFactoryFlow_locked_provisioned_displayIncompatible_none() =
        kosmos.runTest {
            testDisplayInfo.flags = Display.FLAG_PRIVATE
            fakeKeyguardRepository.setKeyguardShowing(isShowing = true)
            deviceProvisioningRepository.setDeviceProvisioned(true)

            val actual by collectLastValue(wallpaperPresentationInteractor.presentationFactoryFlow)
            assertThat(actual).isEqualTo(NONE)
        }

    @Test
    fun presentationFactoryFlow_provisioning_locked_displayCompatible_provisioning() =
        kosmos.runTest {
            deviceProvisioningRepository.setDeviceProvisioned(false)
            fakeKeyguardRepository.setKeyguardShowing(isShowing = true)

            val actual by collectLastValue(wallpaperPresentationInteractor.presentationFactoryFlow)
            assertThat(actual).isEqualTo(PROVISIONING)
        }

    @Test
    fun presentationFactoryFlow_provisioning_unlocked_displayCompatible_provisioning() =
        kosmos.runTest {
            deviceProvisioningRepository.setDeviceProvisioned(false)
            fakeKeyguardRepository.setKeyguardShowing(isShowing = false)

            val actual by collectLastValue(wallpaperPresentationInteractor.presentationFactoryFlow)
            assertThat(actual).isEqualTo(PROVISIONING)
        }

    @Test
    fun presentationFactoryFlow_provisioning_displayIncompatible_none() =
        kosmos.runTest {
            testDisplayInfo.flags = Display.FLAG_PRIVATE
            deviceProvisioningRepository.setDeviceProvisioned(false)

            val actual by collectLastValue(wallpaperPresentationInteractor.presentationFactoryFlow)
            assertThat(actual).isEqualTo(NONE)
        }
}
