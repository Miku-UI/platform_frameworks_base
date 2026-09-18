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

package com.android.systemui.notifications.ui.composable

import android.testing.TestableLooper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.TestContentScope
import com.android.compose.theme.PlatformTheme
import com.android.systemui.SysuiTestCase
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.kosmos.testScope
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.notificationRulesShadeStateViewModelFactory
import com.android.systemui.scene.session.shared.SessionStorage
import com.android.systemui.scene.session.ui.composable.SaveableSession
import com.android.systemui.scene.session.ui.composable.Session
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.notification.stack.domain.interactor.notificationStackAppearanceInteractor
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.notificationsPlaceholderViewModelFactory
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
class NotificationsTest : SysuiTestCase() {

    @get:Rule val composeTestRule = createComposeRule()
    private val kosmos = testKosmos()

    @Test
    fun scrimBounds_reportedWhenPlaced_clearedWhenUnplaced() {
        val viewModel = kosmos.notificationsPlaceholderViewModelFactory.create(Scenes.Shade)
        val stackAppearanceInteractor = kosmos.notificationStackAppearanceInteractor
        var isPlaced by mutableStateOf(true)

        composeTestRule.setContent {
            PlatformTheme {
                TestContentScope {
                    // Custom layout that allows us to measure the content but
                    // conditionally skip placing it
                    PlaceableContainer(place = isPlaced) {
                        ScrollingNotificationPanel(
                            tag = "Test",
                            shadeSession = shadeSession(),
                            stackScrollView = mock(NotificationScrollView::class.java),
                            viewModel = viewModel,
                            notificationRulesShadeStateViewModel =
                                kosmos.notificationRulesShadeStateViewModelFactory.create(),
                            jankMonitor = interactionJankMonitor,
                            shouldPunchHoleBehindScrim = false,
                            isTransparencyEnabled = false,
                            stackTopPadding = 0.dp,
                            stackBottomPadding = { 0.dp },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // Place and compose => bounds should be reported
        composeTestRule.waitForIdle()
        assertThat(stackAppearanceInteractor.notificationShadeScrimBounds.value).isNotNull()

        // Unplace (but still composed)
        // Set isPlaced=false to run onUnplaced but NOT onDispose
        // to reproduce state where the view is active but hidden by the transition
        isPlaced = false
        composeTestRule.waitForIdle()
        assertThat(stackAppearanceInteractor.notificationShadeScrimBounds.value).isNull()

        // Place again => recovers and reports bounds again
        isPlaced = true
        composeTestRule.waitForIdle()
        assertThat(stackAppearanceInteractor.notificationShadeScrimBounds.value).isNotNull()
    }

    /**
     * A layout that always measures its children, but only places them if [shouldPlace] is true.
     * This allows simulating the state where a node is attached/composed but not visible/placed.
     */
    @Composable
    private fun PlaceableContainer(
        shouldPlace: Boolean,
        content: @Composable () -> Unit
    ) {
        Layout(content) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                if (shouldPlace) {
                    placeables.forEach { it.place(0, 0) }
                }
            }
        }
    }

    @Composable
    private fun shadeSession(): SaveableSession {
        return remember {
            object : SaveableSession, Session by Session(SessionStorage()) {
                @Composable
                override fun <T : Any> rememberSaveableSession(
                    vararg inputs: Any?,
                    saver: Saver<T, out Any>,
                    key: String?,
                    init: () -> T,
                ): T = rememberSession(key, inputs = inputs, init = init)
            }
        }
    }
}
