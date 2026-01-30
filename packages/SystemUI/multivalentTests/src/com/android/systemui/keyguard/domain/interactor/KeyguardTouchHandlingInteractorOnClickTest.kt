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

package com.android.systemui.keyguard.domain.interactor

import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.deviceentry.shared.FaceAuthUiEvent
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.inputdevice.data.repository.fake
import com.android.systemui.inputdevice.data.repository.pointerDeviceRepository
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFaceAuthRepository
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.statusbar.phone.statusBarKeyguardViewManager
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.description
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class KeyguardTouchHandlingInteractorOnClickTest(private val testScenario: TestScenario) :
    SysuiTestCase() {

    private val kosmos = testKosmos()
    private lateinit var underTest: KeyguardTouchHandlingInteractor

    init {
        mSetFlagsRule.setFlagsParameterization(testScenario.flags)
    }

    @Before
    fun setUp() {
        kosmos.pointerDeviceRepository.fake.setIsAnyPointerConnected(
            testScenario.isAnyPointingDeviceConnected
        )
        underTest = kosmos.keyguardTouchHandlingInteractor
    }

    @Test
    fun onClick() =
        kosmos.runTest {
            collectLastValue(underTest.isMenuVisible)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            runCurrent()
            kosmos.fakeDeviceEntryFaceAuthRepository.canRunFaceAuth.value = testScenario.faceAuth
            if (SceneContainerFlag.isEnabled) {
                assertWithMessage("Unexpectedly, bouncer is a current overlay")
                    .that(currentOverlays)
                    .doesNotContain(Overlays.Bouncer)
            } else {
                verify(
                        kosmos.statusBarKeyguardViewManager,
                        never().description("Unexpectedly requested to show bouncer"),
                    )
                    .showPrimaryBouncer(anyBoolean(), anyString())
            }

            underTest.onClick(100.0f, 100.0f)
            runCurrent()

            if (SceneContainerFlag.isEnabled) {
                if (testScenario.isBouncerNavigationExpected) {
                    assertWithMessage("Bouncer isn't a current overlay")
                        .that(currentOverlays)
                        .contains(Overlays.Bouncer)
                } else {
                    assertWithMessage("Unexpectedly, bouncer is a current overlay")
                        .that(currentOverlays)
                        .doesNotContain(Overlays.Bouncer)
                }
            } else {
                if (testScenario.isBouncerNavigationExpected) {
                    verify(
                            kosmos.statusBarKeyguardViewManager,
                            description("Did not request to show bouncer"),
                        )
                        .showPrimaryBouncer(anyBoolean(), anyString())
                } else {
                    verify(
                            kosmos.statusBarKeyguardViewManager,
                            never().description("Unexpectedly requested to show bouncer"),
                        )
                        .showPrimaryBouncer(anyBoolean(), anyString())
                }
            }

            val runningAuthRequest =
                kosmos.fakeDeviceEntryFaceAuthRepository.runningAuthRequest.value
            if (testScenario.faceAuth) {
                assertThat(runningAuthRequest?.first)
                    .isEqualTo(FaceAuthUiEvent.FACE_AUTH_TRIGGERED_NOTIFICATION_PANEL_CLICKED)
                assertThat(runningAuthRequest?.second).isTrue()
            } else {
                assertThat(runningAuthRequest?.first).isNull()
                assertThat(runningAuthRequest?.second).isNull()
            }
        }

    data class TestScenario(
        val flags: FlagsParameterization,
        val faceAuth: Boolean,
        val isAnyPointingDeviceConnected: Boolean,
    ) {
        val isBouncerNavigationExpected: Boolean = !faceAuth && isAnyPointingDeviceConnected
    }

    companion object {
        @Parameters(name = "{0}")
        @JvmStatic
        fun testScenarios(): List<TestScenario> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer().flatMap { flags ->
                listOf(
                    TestScenario(
                        flags = flags,
                        faceAuth = false,
                        isAnyPointingDeviceConnected = false,
                    ),
                    TestScenario(
                        flags = flags,
                        faceAuth = false,
                        isAnyPointingDeviceConnected = true,
                    ),
                    TestScenario(
                        flags = flags,
                        faceAuth = true,
                        isAnyPointingDeviceConnected = false,
                    ),
                    TestScenario(
                        flags = flags,
                        faceAuth = true,
                        isAnyPointingDeviceConnected = true,
                    ),
                )
            }
        }
    }
}
