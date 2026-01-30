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

package com.android.systemui.media.controls.domain.interactor

import android.os.UserHandle
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.deviceentry.domain.interactor.deviceEntryInteractor
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.SuccessFingerprintAuthenticationStatus
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.media.controls.domain.pipeline.interactor.MediaCarouselInteractor
import com.android.systemui.media.controls.domain.pipeline.interactor.mediaCarouselInteractor
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.remedia.data.repository.MediaPipelineRepository
import com.android.systemui.media.remedia.data.repository.mediaPipelineRepository
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.testKosmos
import com.android.systemui.util.settings.fakeSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidJUnit4::class)
class MediaCarouselInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val testScope = kosmos.testScope
    private val transitionRepository = kosmos.fakeKeyguardTransitionRepository
    private lateinit var mediaPipelineRepository: MediaPipelineRepository

    private lateinit var underTest: MediaCarouselInteractor

    @Before
    fun setUp() {
        mediaPipelineRepository = kosmos.mediaPipelineRepository
        underTest = kosmos.mediaCarouselInteractor
        underTest.start()
    }

    @Test
    fun addUserMediaEntry_activeThenInactivate() =
        testScope.runTest {
            val hasActiveMedia by collectLastValue(underTest.hasActiveMedia)

            val userMedia = MediaData(active = true)

            mediaPipelineRepository.addCurrentUserMediaEntry(userMedia)

            assertThat(hasActiveMedia).isTrue()
            assertThat(underTest.hasActiveMedia()).isTrue()
            assertThat(underTest.hasAnyMedia()).isTrue()

            mediaPipelineRepository.addCurrentUserMediaEntry(userMedia.copy(active = false))

            assertThat(hasActiveMedia).isFalse()
            assertThat(underTest.hasActiveMedia()).isFalse()
            assertThat(underTest.hasAnyMedia()).isTrue()
        }

    @Test
    fun addInactiveUserMediaEntry_thenRemove() =
        testScope.runTest {
            val hasActiveMedia by collectLastValue(underTest.hasActiveMedia)

            val userMedia = MediaData(active = false)
            val instanceId = userMedia.instanceId

            mediaPipelineRepository.addCurrentUserMediaEntry(userMedia)

            assertThat(hasActiveMedia).isFalse()
            assertThat(underTest.hasActiveMedia()).isFalse()
            assertThat(underTest.hasAnyMedia()).isTrue()

            assertThat(mediaPipelineRepository.removeCurrentUserMediaEntry(instanceId, userMedia))
                .isTrue()

            assertThat(hasActiveMedia).isFalse()
            assertThat(underTest.hasActiveMedia()).isFalse()
            assertThat(underTest.hasAnyMedia()).isFalse()
        }

    @Test
    fun hasAnyMedia_noMediaSet_returnsFalse() =
        testScope.runTest { assertThat(underTest.hasAnyMedia()).isFalse() }

    @Test
    fun hasActiveMedia_noMediaSet_returnsFalse() =
        testScope.runTest { assertThat(underTest.hasActiveMedia()).isFalse() }

    @DisableSceneContainer
    @Test
    fun onLockscreen_mediaAllowed_lockedAndHidden_returnsFalse() =
        testScope.runTest {
            val isLockedAndHidden by collectLastValue(underTest.isLockedAndHidden)

            transitionRepository.sendTransitionSteps(
                from = KeyguardState.GONE,
                to = KeyguardState.LOCKSCREEN,
                this,
            )

            assertThat(isLockedAndHidden).isFalse()
        }

    @DisableSceneContainer
    @Test
    fun onLockscreen_mediaNotAllowed_lockedAndHidden_returnsTrue() =
        testScope.runTest {
            val isLockedAndHidden by collectLastValue(underTest.isLockedAndHidden)

            kosmos.fakeSettings.putBoolForUser(
                Settings.Secure.MEDIA_CONTROLS_LOCK_SCREEN,
                false,
                UserHandle.USER_CURRENT,
            )
            transitionRepository.sendTransitionSteps(
                from = KeyguardState.GONE,
                to = KeyguardState.LOCKSCREEN,
                this,
            )

            assertThat(isLockedAndHidden).isTrue()
        }

    @DisableSceneContainer
    @Test
    fun onKeyguardGone_mediaAllowed_lockedAndHidden_returnsFalse() =
        testScope.runTest {
            val isLockedAndHidden by collectLastValue(underTest.isLockedAndHidden)

            transitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.GONE,
                this,
            )

            assertThat(isLockedAndHidden).isFalse()
        }

    @DisableSceneContainer
    @Test
    fun onKeyguardGone_mediaNotAllowed_lockedAndHidden_returnsFalse() =
        testScope.runTest {
            val isLockedAndHidden by collectLastValue(underTest.isLockedAndHidden)

            kosmos.fakeSettings.putBoolForUser(
                Settings.Secure.MEDIA_CONTROLS_LOCK_SCREEN,
                false,
                UserHandle.USER_CURRENT,
            )
            transitionRepository.sendTransitionSteps(
                from = KeyguardState.LOCKSCREEN,
                to = KeyguardState.GONE,
                this,
            )

            assertThat(isLockedAndHidden).isFalse()
        }

    @DisableSceneContainer
    @Test
    fun goingToDozing_mediaAllowed_lockedAndHidden_returnsFalse() =
        testScope.runTest {
            val isLockedAndHidden by collectLastValue(underTest.isLockedAndHidden)

            transitionRepository.sendTransitionSteps(
                from = KeyguardState.GONE,
                to = KeyguardState.DOZING,
                this,
            )

            assertThat(isLockedAndHidden).isFalse()
        }

    @DisableSceneContainer
    @Test
    fun goingToDozing_mediaNotAllowed_lockedAndHidden_returnsTrue() =
        testScope.runTest {
            val isLockedAndHidden by collectLastValue(underTest.isLockedAndHidden)

            kosmos.fakeSettings.putBoolForUser(
                Settings.Secure.MEDIA_CONTROLS_LOCK_SCREEN,
                false,
                UserHandle.USER_CURRENT,
            )
            transitionRepository.sendTransitionSteps(
                from = KeyguardState.GONE,
                to = KeyguardState.DOZING,
                this,
            )

            assertThat(isLockedAndHidden).isTrue()
        }

    @EnableSceneContainer
    @Test
    fun deviceNotEntered_mediaNotAllowed_lockedAndHidden() =
        testScope.runTest {
            val isLockedAndHidden by collectLastValue(underTest.isLockedAndHidden)

            kosmos.fakeSettings.putBoolForUser(
                Settings.Secure.MEDIA_CONTROLS_LOCK_SCREEN,
                false,
                UserHandle.USER_CURRENT,
            )
            setDeviceEntered(false)

            assertThat(isLockedAndHidden).isTrue()
        }

    @EnableSceneContainer
    @Test
    fun deviceNotEntered_mediaAllowed_notLockedAndHidden() =
        testScope.runTest {
            val isLockedAndHidden by collectLastValue(underTest.isLockedAndHidden)

            setDeviceEntered(false)

            assertThat(isLockedAndHidden).isFalse()
        }

    @EnableSceneContainer
    @Test
    fun deviceEntered_mediaNotAllowed_notLockedAndHidden() =
        testScope.runTest {
            val isLockedAndHidden by collectLastValue(underTest.isLockedAndHidden)

            kosmos.fakeSettings.putBoolForUser(
                Settings.Secure.MEDIA_CONTROLS_LOCK_SCREEN,
                false,
                UserHandle.USER_CURRENT,
            )
            setDeviceEntered(true)

            assertThat(isLockedAndHidden).isFalse()
        }

    @EnableSceneContainer
    @Test
    fun deviceEntered_mediaAllowed_notLockedAndHidden() =
        testScope.runTest {
            val isLockedAndHidden by collectLastValue(underTest.isLockedAndHidden)

            setDeviceEntered(true)

            assertThat(isLockedAndHidden).isFalse()
        }

    private fun TestScope.setDeviceEntered(isEntered: Boolean) {
        if (isEntered) {
            // Unlock the device, marking the device as entered
            kosmos.fakeDeviceEntryFingerprintAuthRepository.setAuthenticationStatus(
                SuccessFingerprintAuthenticationStatus(0, true)
            )
            runCurrent()
        }
        setScene(
            if (isEntered) {
                Scenes.Gone
            } else {
                Scenes.Lockscreen
            }
        )
        assertThat(kosmos.deviceEntryInteractor.isDeviceEntered.value).isEqualTo(isEntered)
    }

    private fun TestScope.setScene(key: SceneKey) {
        kosmos.sceneInteractor.changeScene(key, "test")
        kosmos.sceneInteractor.setTransitionState(
            MutableStateFlow<ObservableTransitionState>(ObservableTransitionState.Idle(key))
        )
        runCurrent()
    }
}
