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

package com.android.systemui.qs.panels.ui.viewmodel.toolbar

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel
import com.android.systemui.deviceentry.data.repository.fakeDeviceEntryRepository
import com.android.systemui.deviceentry.shared.model.DeviceUnlockStatus
import com.android.systemui.globalactions.data.repository.globalActionsRepository
import com.android.systemui.globalactions.shared.model.GlobalActionType
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.policy.data.repository.fakeDeviceProvisioningRepository
import com.android.systemui.testKosmosNew
import com.android.systemui.user.data.repository.fakeUserRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PowerMenuViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()

    private val Kosmos.underTest by
        Kosmos.Fixture { powerMenuViewModelFactory.create().apply { activateIn(testScope) } }

    @Before
    fun setup() {
        // Default to a secure method so "isLockable" is true when the device is unlocked.
        kosmos.fakeAuthenticationRepository.setAuthenticationMethod(AuthenticationMethodModel.Pin)
        // Default to provisioned so actions aren't filtered out by provisioning
        kosmos.fakeDeviceProvisioningRepository.setDeviceProvisioned(true)
    }

    @Test
    fun items_lockSupported_containsLockAction() =
        kosmos.runTest {
            // GIVEN lock is a possible action, AND device is unlocked/entered
            globalActionsRepository.possibleGlobalActions = listOf(GlobalActionType.LOCK)
            setUnlocked(true)
            switchToScene(Scenes.Gone)

            // THEN items contains the lock action
            assertThat(underTest.visibleActions.map { it.key })
                .containsExactly(GlobalActionType.LOCK)
        }

    @Test
    fun items_lockNotSupported_isEmpty() =
        kosmos.runTest {
            // GIVEN lock is NOT a possible action
            globalActionsRepository.possibleGlobalActions = emptyList()
            setUnlocked(true)
            switchToScene(Scenes.Gone)

            // THEN items is empty
            assertThat(underTest.visibleActions).isEmpty()
        }

    @Test
    fun items_lockStateUpdates() =
        kosmos.runTest {
            // GIVEN lock is supported and is configured to be blocked when not lockable
            globalActionsRepository.possibleGlobalActions = listOf(GlobalActionType.LOCK)
            globalActionsRepository.lockedDeviceStateBlockList = listOf(GlobalActionType.LOCK)

            // GIVEN device is initially locked
            setUnlocked(false)
            switchToScene(Scenes.Lockscreen)

            // VERIFY LOCK item is NOT present (filtered out by interactor because we are already
            // locked)
            assertThat(underTest.visibleActions.map { it.key })
                .doesNotContain(GlobalActionType.LOCK)

            // WHEN device is unlocked and entered
            setUnlocked(true)
            switchToScene(Scenes.Gone)

            // THEN LOCK item IS present
            assertThat(underTest.visibleActions.map { it.key }).contains(GlobalActionType.LOCK)

            // WHEN re-locked
            setUnlocked(false)
            switchToScene(Scenes.Lockscreen)

            // THEN LOCK item is removed again
            assertThat(underTest.visibleActions.map { it.key })
                .doesNotContain(GlobalActionType.LOCK)
        }

    @Test
    fun items_logoutEnabled_containsLogoutAction() =
        kosmos.runTest {
            // GIVEN logout is possible AND enabled by user manager
            globalActionsRepository.possibleGlobalActions = listOf(GlobalActionType.LOGOUT)
            fakeUserRepository.setUserManagerLogoutEnabled(true)

            // THEN items contains the logout action
            assertThat(underTest.visibleActions.map { it.key }).contains(GlobalActionType.LOGOUT)
        }

    @Test
    fun items_shutdownEnabled_containsShutdownAction() =
        kosmos.runTest {
            // GIVEN shutdown is possible
            globalActionsRepository.possibleGlobalActions = listOf(GlobalActionType.POWER)

            // THEN items contains the shutdown action
            assertThat(underTest.visibleActions.map { it.key }).contains(GlobalActionType.POWER)
        }

    @Test
    fun items_restartEnabled_containsRestartAction() =
        kosmos.runTest {
            // GIVEN restart is possible
            globalActionsRepository.possibleGlobalActions = listOf(GlobalActionType.RESTART)

            // THEN items contains the restart action
            assertThat(underTest.visibleActions.map { it.key }).contains(GlobalActionType.RESTART)
        }

    @Test
    fun items_orderedCorrectly() =
        kosmos.runTest {
            // GIVEN all actions are possible.
            globalActionsRepository.possibleGlobalActions =
                listOf(
                    GlobalActionType.POWER,
                    GlobalActionType.LOGOUT,
                    GlobalActionType.LOCK,
                    GlobalActionType.RESTART,
                )
            // GIVEN all necessary conditions are met for them to be available
            fakeUserRepository.setUserManagerLogoutEnabled(true)
            setUnlocked(true)
            switchToScene(Scenes.Gone)

            // THEN items are in the fixed order: Lock -> Logout -> Restart -> Power
            assertThat(underTest.visibleActions.map { it.key })
                .containsExactly(
                    GlobalActionType.LOCK,
                    GlobalActionType.LOGOUT,
                    GlobalActionType.RESTART,
                    GlobalActionType.POWER,
                )
                .inOrder()
        }

    private fun Kosmos.setUnlocked(isUnlocked: Boolean) {
        fakeDeviceEntryRepository.deviceUnlockStatus.value =
            DeviceUnlockStatus(isUnlocked = isUnlocked, deviceUnlockSource = null)
    }

    private fun Kosmos.switchToScene(scene: SceneKey) {
        sceneInteractor.changeScene(scene, "test")
        sceneInteractor.setTransitionState(flowOf(ObservableTransitionState.Idle(scene)))
    }
}
