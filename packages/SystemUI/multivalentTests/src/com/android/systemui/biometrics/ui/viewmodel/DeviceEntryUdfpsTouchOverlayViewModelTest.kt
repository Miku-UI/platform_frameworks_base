/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.biometrics.ui.viewmodel

import android.platform.test.annotations.EnableFlags
import android.security.Flags.FLAG_SECURE_LOCK_DEVICE
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.bouncer.data.repository.fakeKeyguardBouncerRepository
import com.android.systemui.flags.Flags
import com.android.systemui.flags.fakeFeatureFlagsClassic
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.ui.viewmodel.fakeDeviceEntryIconViewModelTransition
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.securelockdevice.data.repository.fakeSecureLockDeviceRepository
import com.android.systemui.statusbar.phone.SystemUIDialogManager
import com.android.systemui.statusbar.phone.mockSystemUIDialogManager
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
class DeviceEntryUdfpsTouchOverlayViewModelTest : SysuiTestCase() {
    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply {
            fakeFeatureFlagsClassic.apply { set(Flags.FULL_SCREEN_USER_SWITCHER, true) }
        }
    private val Kosmos.underTest by Kosmos.Fixture { deviceEntryUdfpsTouchOverlayViewModel }

    @Captor
    private lateinit var sysuiDialogListenerCaptor: ArgumentCaptor<SystemUIDialogManager.Listener>

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
    }

    @Test
    fun dialogShowing_shouldHandleTouchesFalse() =
        kosmos.runTest {
            val shouldHandleTouches by collectLastValue(underTest.shouldHandleTouches)

            fakeDeviceEntryIconViewModelTransition.setDeviceEntryParentViewAlpha(1f)

            verify(mockSystemUIDialogManager).registerListener(sysuiDialogListenerCaptor.capture())
            sysuiDialogListenerCaptor.value.shouldHideAffordances(true)

            assertThat(shouldHandleTouches).isFalse()
        }

    @Test
    fun transitionAlphaIsSmall_shouldHandleTouchesFalse() =
        kosmos.runTest {
            val shouldHandleTouches by collectLastValue(underTest.shouldHandleTouches)

            fakeDeviceEntryIconViewModelTransition.setDeviceEntryParentViewAlpha(.3f)

            verify(mockSystemUIDialogManager).registerListener(sysuiDialogListenerCaptor.capture())
            sysuiDialogListenerCaptor.value.shouldHideAffordances(false)

            assertThat(shouldHandleTouches).isFalse()
        }

    @Test
    fun alphaFullyShowing_noDialog_shouldHandleTouchesTrue() =
        kosmos.runTest {
            val shouldHandleTouches by collectLastValue(underTest.shouldHandleTouches)

            fakeDeviceEntryIconViewModelTransition.setDeviceEntryParentViewAlpha(1f)

            verify(mockSystemUIDialogManager).registerListener(sysuiDialogListenerCaptor.capture())
            sysuiDialogListenerCaptor.value.shouldHideAffordances(false)

            assertThat(shouldHandleTouches).isTrue()
        }

    @Test
    fun deviceEntryViewAlphaZero_alternateBouncerVisible_shouldHandleTouchesTrue() =
        kosmos.runTest {
            val shouldHandleTouches by collectLastValue(underTest.shouldHandleTouches)

            fakeDeviceEntryIconViewModelTransition.setDeviceEntryParentViewAlpha(0f)

            fakeKeyguardBouncerRepository.setAlternateVisible(true)
            assertThat(shouldHandleTouches).isTrue()
        }

    @Test
    fun transitioningToDozing_shouldHandleTouchesTrue() =
        kosmos.runTest {
            val shouldHandleTouches by collectLastValue(underTest.shouldHandleTouches)

            fakeKeyguardTransitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.DOZING,
                testScope = testScope,
            )
            assertThat(shouldHandleTouches).isTrue()
        }

    @EnableFlags(FLAG_SECURE_LOCK_DEVICE)
    @Test
    fun shouldHandleTouchesTrue_duringSecureLockDeviceBiometricAuth() =
        kosmos.runTest {
            val shouldHandleTouches by collectLastValue(underTest.shouldHandleTouches)

            fakeDeviceEntryIconViewModelTransition.setDeviceEntryParentViewAlpha(0f)

            fakeSecureLockDeviceRepository.onSecureLockDeviceEnabled()
            fakeSecureLockDeviceRepository.onSuccessfulPrimaryAuth()

            assertThat(shouldHandleTouches).isTrue()
        }
}
