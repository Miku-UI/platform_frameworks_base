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

package com.android.systemui.growth.domain.interactor

import android.content.Intent
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.FakeAuthenticationRepository
import com.android.systemui.authentication.domain.interactor.authenticationInteractor
import com.android.systemui.broadcast.mockBroadcastSender
import com.android.systemui.deviceentry.domain.interactor.deviceEntryInteractor
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.domain.startable.sceneContainerStartable
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.never
import org.mockito.kotlin.times

@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
@SmallTest
class GrowthInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val broadcastSender by lazy { kosmos.mockBroadcastSender }
    private lateinit var underTest: GrowthInteractor

    @Captor private lateinit var intentArgumentCaptor: ArgumentCaptor<Intent>
    @Captor private lateinit var userHandleArgumentCaptor: ArgumentCaptor<UserHandle>

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        kosmos.sceneContainerStartable.start()
    }

    @Test
    fun onDeviceEnteredDirectly_sendBroadcast_withAllConfigs() =
        kosmos.runTest {
            overrideResources(GROWTH_APP_PACKAGE_NAME, GROWTH_RECEIVER_CLASS_NAME)
            underTest = kosmos.growthInteractor
            underTest.activateIn(kosmos.testScope)
            setDeviceEntered()
            advanceTimeBy(GROWTH_BROADCAST_DELAY.plus(DURATION_50_MILLIS))
            runCurrent()

            verify(broadcastSender, times(1))
                .sendBroadcastAsUser(
                    capture(intentArgumentCaptor),
                    capture(userHandleArgumentCaptor)
                )
            assertThat(userHandleArgumentCaptor.value).isEqualTo(UserHandle.CURRENT)
            assertThat(intentArgumentCaptor.value.action)
                .isEqualTo(GrowthInteractor.ACTION_DEVICE_ENTERED_DIRECTLY)
            assertThat(intentArgumentCaptor.value.`package`).isNull()
            assertThat(intentArgumentCaptor.value.component?.packageName)
                .isEqualTo(GROWTH_APP_PACKAGE_NAME)
            assertThat(intentArgumentCaptor.value.component?.className)
                .isEqualTo(GROWTH_RECEIVER_CLASS_NAME)
        }

    @Test
    fun onDeviceEnteredDirectly_sendBroadcast_withEmptyPackageName() =
        kosmos.runTest {
            overrideResources("", GROWTH_RECEIVER_CLASS_NAME)
            underTest = kosmos.growthInteractor
            underTest.activateIn(kosmos.testScope)
            setDeviceEntered()
            advanceTimeBy(GROWTH_BROADCAST_DELAY.plus(DURATION_50_MILLIS))
            runCurrent()

            verify(broadcastSender, times(1))
                .sendBroadcastAsUser(
                    capture(intentArgumentCaptor),
                    capture(userHandleArgumentCaptor)
                )
            assertThat(userHandleArgumentCaptor.value).isEqualTo(UserHandle.CURRENT)
            assertThat(intentArgumentCaptor.value.action)
                .isEqualTo(GrowthInteractor.ACTION_DEVICE_ENTERED_DIRECTLY)
            assertThat(intentArgumentCaptor.value.`package`).isNull()
            assertThat(intentArgumentCaptor.value.component).isNull()
        }

    @Test
    fun onDeviceEnteredDirectly_sendBroadcast_withEmptyReceiverClassName() =
        kosmos.runTest {
            overrideResources(GROWTH_APP_PACKAGE_NAME, "")
            underTest = kosmos.growthInteractor
            underTest.activateIn(kosmos.testScope)
            setDeviceEntered()
            advanceTimeBy(GROWTH_BROADCAST_DELAY.plus(DURATION_50_MILLIS))
            runCurrent()

            verify(broadcastSender, times(1))
                .sendBroadcastAsUser(
                    capture(intentArgumentCaptor),
                    capture(userHandleArgumentCaptor)
                )
            assertThat(userHandleArgumentCaptor.value).isEqualTo(UserHandle.CURRENT)
            assertThat(intentArgumentCaptor.value.action)
                .isEqualTo(GrowthInteractor.ACTION_DEVICE_ENTERED_DIRECTLY)
            assertThat(intentArgumentCaptor.value.`package`).isNull()
            assertThat(intentArgumentCaptor.value.component).isNull()
        }

    @Test
    fun onDeviceEnteredDirectly_sendBroadcast_withEmptyConfigs() =
        kosmos.runTest {
            overrideResources("", "")
            underTest = kosmos.growthInteractor
            underTest.activateIn(kosmos.testScope)
            setDeviceEntered()
            advanceTimeBy(GROWTH_BROADCAST_DELAY.plus(DURATION_50_MILLIS))
            runCurrent()

            verify(broadcastSender, times(1))
                .sendBroadcastAsUser(
                    capture(intentArgumentCaptor),
                    capture(userHandleArgumentCaptor)
                )
            assertThat(userHandleArgumentCaptor.value).isEqualTo(UserHandle.CURRENT)
            assertThat(intentArgumentCaptor.value.action)
                .isEqualTo(GrowthInteractor.ACTION_DEVICE_ENTERED_DIRECTLY)
            assertThat(intentArgumentCaptor.value.`package`).isNull()
            assertThat(intentArgumentCaptor.value.component).isNull()
        }

    @Test
    fun onDeviceNotEnteredDirectly_doNotSendBroadcast_onLockscreen() =
        kosmos.runTest {
            overrideResources(GROWTH_APP_PACKAGE_NAME, GROWTH_RECEIVER_CLASS_NAME)
            underTest = kosmos.growthInteractor
            underTest.activateIn(kosmos.testScope)
            setDeviceEntered()
            advanceTimeBy(GROWTH_BROADCAST_DELAY.plus(DURATION_50_MILLIS))
            clearInvocations(broadcastSender)

            setScene(Scenes.Lockscreen)
            advanceTimeBy(GROWTH_BROADCAST_DELAY.plus(DURATION_50_MILLIS))
            verify(broadcastSender, never()).sendBroadcastAsUser(any(), any())
        }

    @Test
    fun onDeviceEnteredDirectly_doNotSendBroadcast_lockedBeforeDelay() =
        kosmos.runTest {
            overrideResources(GROWTH_APP_PACKAGE_NAME, GROWTH_RECEIVER_CLASS_NAME)
            underTest = kosmos.growthInteractor
            underTest.activateIn(kosmos.testScope)
            setDeviceEntered()
            advanceTimeBy(DURATION_50_MILLIS)
            runCurrent()
            verify(broadcastSender, never()).sendBroadcastAsUser(any(), any())

            setScene(Scenes.Lockscreen)
            advanceTimeBy(GROWTH_BROADCAST_DELAY.plus(DURATION_50_MILLIS))
            runCurrent()
            verify(broadcastSender, never()).sendBroadcastAsUser(any(), any())
        }

    private fun overrideResources(packageName: String, receiverClassName: String) {
        overrideResource(R.string.config_growthAppPackageName, packageName)
        overrideResource(R.string.config_growthReceiverClassName, receiverClassName)
        overrideResource(R.integer.config_growthBroadcastDelayMillis, DELAY_200_MILLIS)
    }

    private suspend fun Kosmos.setDeviceEntered() {
        val currentScene by collectLastValue(kosmos.sceneInteractor.currentScene)
        val isDeviceEnteredDirectly by
            collectLastValue(kosmos.deviceEntryInteractor.isDeviceEnteredDirectly)
        assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
        assertThat(isDeviceEnteredDirectly).isFalse()

        // Authenticate with PIN to unlock and dismiss the lockscreen.
        kosmos.authenticationInteractor.authenticate(FakeAuthenticationRepository.DEFAULT_PIN)
        kosmos.runCurrent()

        assertThat(currentScene).isEqualTo(Scenes.Gone)
        assertThat(isDeviceEnteredDirectly).isTrue()
    }

    private fun Kosmos.setScene(sceneKey: SceneKey) {
        kosmos.sceneInteractor.changeScene(sceneKey, "test")
        kosmos.sceneInteractor.setTransitionState(
            flowOf<ObservableTransitionState>(ObservableTransitionState.Idle(sceneKey))
        )
        kosmos.runCurrent()
    }

    companion object {
        private const val GROWTH_APP_PACKAGE_NAME = "com.android.systemui.growth"
        private const val GROWTH_RECEIVER_CLASS_NAME = "com.android.systemui.growth.GrowthReceiver"
        private const val DELAY_200_MILLIS = 200
        private val GROWTH_BROADCAST_DELAY = DELAY_200_MILLIS.milliseconds
        private val DURATION_50_MILLIS = 50.milliseconds
    }
}
