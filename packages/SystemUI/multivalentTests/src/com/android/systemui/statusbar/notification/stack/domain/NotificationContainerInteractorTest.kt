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

package com.android.systemui.statusbar.notification.stack.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.notification.data.repository.UnconfinedFakeHeadsUpRowRepository
import com.android.systemui.statusbar.notification.headsup.PinnedStatus
import com.android.systemui.statusbar.notification.stack.data.repository.headsUpNotificationRepository
import com.android.systemui.statusbar.notification.stack.domain.interactor.notificationContainerInteractor
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class NotificationContainerInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val underTest
        get() = kosmos.notificationContainerInteractor

    private fun expandShade() {
        kosmos.sceneInteractor.snapToScene(Scenes.Shade, "test")
        kosmos.sceneInteractor.setTransitionState(
            flowOf(ObservableTransitionState.Idle(Scenes.Shade))
        )
    }

    private fun collapseShade() {
        kosmos.sceneInteractor.snapToScene(Scenes.Lockscreen, "test")
        kosmos.sceneInteractor.setTransitionState(
            flowOf(ObservableTransitionState.Idle(Scenes.Lockscreen))
        )
    }

    private fun pinNotif() {
        kosmos.headsUpNotificationRepository.setNotifications(
            listOf(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "key",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedByUser),
                )
            )
        )
    }

    @Test
    fun activate_collapsed_noUnpinAll() =
        kosmos.runTest {
            // GIVEN one pinned notif in COLLAPSED shade
            pinNotif()
            collapseShade()

            // WHEN the interactor is activated
            underTest.activateIn(testScope)

            // THEN unpinAll is NOT called because there was no false->true transition
            assertThat(headsUpNotificationRepository.orderedHeadsUpRows.value[0].pinnedStatus.value)
                .isEqualTo(PinnedStatus.PinnedByUser)
        }

    @Test
    fun activate_expanded_noUnpinAll() =
        kosmos.runTest {
            // GIVEN one pinned notif in EXPANDED shade
            pinNotif()
            expandShade()

            // WHEN the interactor is activated
            underTest.activateIn(testScope)

            // THEN unpinAll is NOT called because there was no false->true transition
            assertThat(headsUpNotificationRepository.orderedHeadsUpRows.value[0].pinnedStatus.value)
                .isEqualTo(PinnedStatus.PinnedByUser)
        }

    @Test
    fun activate_collapsedToExpanded_unpinAll() =
        kosmos.runTest {
            // GIVEN one pinned notif in COLLAPSED shade
            pinNotif()
            collapseShade()
            underTest.activateIn(testScope)

            // WHEN the shade EXPANDS
            expandShade()

            // THEN unpinned IS called
            assertThat(headsUpNotificationRepository.orderedHeadsUpRows.value[0].pinnedStatus.value)
                .isEqualTo(PinnedStatus.NotPinned)
        }

    @Test
    fun activate_expandedToCollapsed_noUnpin() =
        kosmos.runTest {
            // GIVEN one pinned notif in EXPANDED shade
            pinNotif()
            expandShade()
            underTest.activateIn(testScope)

            // WHEN the shade COLLAPSES
            collapseShade()

            // THEN unpinned is NOT called
            assertThat(headsUpNotificationRepository.orderedHeadsUpRows.value[0].pinnedStatus.value)
                .isEqualTo(PinnedStatus.PinnedByUser)
        }
}
