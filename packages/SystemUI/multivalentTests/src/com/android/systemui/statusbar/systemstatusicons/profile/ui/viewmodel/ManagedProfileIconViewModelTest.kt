/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.systemstatusicons.profile.ui.viewmodel

import android.content.testableContext
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.deviceentry.data.repository.fakeDeviceEntryRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.domain.startable.sceneContainerStartable
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.policy.profile.data.repository.managedProfileRepository
import com.android.systemui.statusbar.policy.profile.shared.model.ProfileInfo
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(SystemStatusIconsInCompose.FLAG_NAME)
class ManagedProfileIconViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by
        Kosmos.Fixture {
            managedProfileIconViewModelFactory.create(kosmos.testableContext).apply {
                activateIn(kosmos.testScope)
            }
        }

    @Before
    fun setUp() {
        kosmos.sceneContainerStartable.start()
    }

    @Test
    fun icon_managedProfileNotActive_outputsNull() =
        kosmos.runTest { assertThat(underTest.icon).isNull() }

    @Test
    fun icon_managedProfileActive_outputsIcon() =
        kosmos.runTest {
            // GIVEN keyguard is unlocked
            setDeviceAsEntered()

            // WHEN managed profile becomes active
            managedProfileRepository.currentProfileInfo.value = testProfileInfo

            // THEN the correct icon is output
            assertThat(underTest.icon).isEqualTo(expectedManagedProfileIcon)
        }

    @Test
    fun icon_updatesWhenManagedProfileStatusChanges() =
        kosmos.runTest {
            // GIVEN keyguard is unlocked
            setDeviceAsEntered()
            assertThat(underTest.icon).isNull()

            // WHEN managed profile becomes active
            managedProfileRepository.currentProfileInfo.value = testProfileInfo

            // THEN the icon is visible
            assertThat(underTest.icon).isEqualTo(expectedManagedProfileIcon)

            // WHEN managed profile becomes inactive
            managedProfileRepository.currentProfileInfo.value = null

            // THEN the icon is hidden
            assertThat(underTest.icon).isNull()
        }

    private fun Kosmos.setDeviceAsEntered() {
        fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.None)
        fakeDeviceEntryRepository.setLockscreenEnabled(false)
        setCurrentScene(Scenes.Gone)
    }

    private fun Kosmos.setCurrentScene(scene: SceneKey) {
        sceneInteractor.changeScene(scene, "test")
        sceneInteractor.setTransitionState(flowOf(ObservableTransitionState.Idle(scene)))
    }

    companion object {
        private const val TEST_USER_ID = 10
        private const val TEST_ICON_RES_ID = 12345
        private const val TEST_ACCESSIBILITY_STRING = "Work profile"

        private val testProfileInfo =
            ProfileInfo(
                userId = TEST_USER_ID,
                iconResId = TEST_ICON_RES_ID,
                contentDescription = TEST_ACCESSIBILITY_STRING,
            )

        private val expectedManagedProfileIcon =
            Icon.Resource(
                resId = TEST_ICON_RES_ID,
                contentDescription = ContentDescription.Loaded(TEST_ACCESSIBILITY_STRING),
            )
    }
}
