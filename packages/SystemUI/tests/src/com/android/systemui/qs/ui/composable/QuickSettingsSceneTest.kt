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

package com.android.systemui.qs.ui.composable

import android.testing.TestableLooper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.TestContentScope
import com.android.compose.theme.PlatformTheme
import com.android.systemui.SysuiTestCase
import com.android.systemui.compose.modifiers.resIdToTestTag
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.qs.composefragment.dagger.usingMediaInComposeFragment
import com.android.systemui.qs.pipeline.domain.interactor.currentTilesInteractor
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.ui.viewmodel.quickSettingsSceneContentViewModelFactory
import com.android.systemui.qs.ui.viewmodel.quickSettingsUserActionsViewModelFactory
import com.android.systemui.scene.session.shared.SessionStorage
import com.android.systemui.scene.session.ui.composable.SaveableSession
import com.android.systemui.scene.session.ui.composable.Session
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.ui.composable.WithStatusIconContext
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.notificationsPlaceholderViewModelFactory
import com.android.systemui.statusbar.phone.ui.tintedIconManagerFactory
import com.android.systemui.testKosmos
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
@EnableSceneContainer
class QuickSettingsSceneTest : SysuiTestCase() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val kosmos = testKosmos()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testViewHierarchy() = kosmos.runTest {
        val shadeSession =
            object : SaveableSession, Session by Session(SessionStorage()) {
                @Composable
                override fun <T : Any> rememberSaveableSession(
                    vararg inputs: Any?,
                    saver: Saver<T, out Any>,
                    key: String?,
                    init: () -> T,
                ): T = rememberSession(key, inputs = inputs, init = init)
            }

        usingMediaInComposeFragment = true

        currentTilesInteractor.setTiles(
            listOf(
                TileSpec.create("internet"),
                TileSpec.create("bt"),
            )
        )

        testScope.runCurrent()

        val scene =
            QuickSettingsScene(
                shadeSession = shadeSession,
                notificationStackScrollView = { mock(NotificationScrollView::class.java) },
                notificationsPlaceholderViewModelFactory =
                    notificationsPlaceholderViewModelFactory,
                actionsViewModelFactory = quickSettingsUserActionsViewModelFactory,
                contentViewModelFactory = quickSettingsSceneContentViewModelFactory,
                jankMonitor = interactionJankMonitor,
            )

        composeTestRule.setContent {
            PlatformTheme {
                WithStatusIconContext(tintedIconManagerFactory) {
                    with(scene) {
                        TestContentScope(currentScene = Scenes.QuickSettings) { Content(Modifier) }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        // Verify that the brightness slider exists.
        composeTestRule.onNodeWithTag(resIdToTestTag("brightness_slider")).assertExists()

        // Verify that the tiles exist.
        composeTestRule.onNodeWithTag("element:internet").assertExists()
        composeTestRule.onNodeWithTag("element:bt").assertExists()
    }
}
