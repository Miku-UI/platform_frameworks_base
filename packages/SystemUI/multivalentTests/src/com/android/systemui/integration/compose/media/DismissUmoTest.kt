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

package com.android.systemui.media

import android.platform.test.annotations.DisableFlags
import android.testing.TestableLooper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.TestContentScope
import com.android.compose.theme.PlatformTheme
import com.android.systemui.Flags
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.integration.SystemUiIntegrationTest
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.testScope
import com.android.systemui.media.remedia.data.repository.fakeDismissibleMediaData
import com.android.systemui.media.remedia.data.repository.setFakeCurrentMediaData
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.notificationRulesParentViewModelFactory
import com.android.systemui.qs.composefragment.dagger.usingMediaInComposeFragment
import com.android.systemui.scene.session.shared.SessionStorage
import com.android.systemui.scene.session.ui.composable.SaveableSession
import com.android.systemui.scene.session.ui.composable.Session
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.ui.composable.ShadeScene
import com.android.systemui.shade.ui.composable.WithStatusIconContext
import com.android.systemui.shade.ui.viewmodel.shadeSceneContentViewModelFactory
import com.android.systemui.shade.ui.viewmodel.shadeUserActionsViewModelFactory
import com.android.systemui.statusbar.notification.stack.ui.view.notificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.notificationsPlaceholderViewModelFactory
import com.android.systemui.statusbar.phone.ui.tintedIconManagerFactory
import com.android.systemui.testKosmos
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
@EnableSceneContainer
@DisableFlags(FLAG_DUAL_SHADE)
// TODO(b/494578600) Remove DisableFlags once fixed
@DisableFlags(Flags.FLAG_STATUS_BAR_MOBILE_ICON_KAIROS)
@SystemUiIntegrationTest
/** Tests for dismissing the Universal Media Object (UMO) by swiping it in the shade. */
class DismissUmoTest : SysuiTestCase() {
    @get:Rule val composeTestRule = createComposeRule()

    private val kosmos = testKosmos()
    private val shadeSession =
        object : SaveableSession, Session by Session(SessionStorage()) {
            @Composable
            override fun <T : Any> rememberSaveableSession(
                vararg inputs: Any?,
                saver: Saver<T, out Any>,
                key: String?,
                init: () -> T,
            ): T = rememberSession(key, inputs = inputs, init = init)
        }
    private val scene =
        ShadeScene(
            shadeSession = shadeSession,
            notificationStackScrollView = { kosmos.notificationScrollView },
            actionsViewModelFactory = kosmos.shadeUserActionsViewModelFactory,
            contentViewModelFactory = kosmos.shadeSceneContentViewModelFactory,
            notificationsPlaceholderViewModelFactory =
                kosmos.notificationsPlaceholderViewModelFactory,
            notificationRulesParentViewModelFactory =
                kosmos.notificationRulesParentViewModelFactory,
            jankMonitor = kosmos.interactionJankMonitor,
        )

    private fun Kosmos.runTest(body: suspend TestScope.() -> Unit) =
        this.testScope.runTest { body() }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun dismissUmo_bySwipingLeftInShade() {
        dismissUmo_bySwiping(
            swipeAction = { swipeLeft(startX = right, endX = left, durationMillis = 200) }
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun dismissUmo_bySwipingRightInShade() {
        dismissUmo_bySwiping(
            swipeAction = { swipeRight(startX = left, endX = right, durationMillis = 200) }
        )
    }

    private fun dismissUmo_bySwiping(swipeAction: TouchInjectionScope.() -> Unit) =
        kosmos.runTest {
            kosmos.usingMediaInComposeFragment = true
            kosmos.enableSingleShade()

            // Step 1: Set a dismissible media item as the current media.
            kosmos.setFakeCurrentMediaData(listOf(kosmos.fakeDismissibleMediaData))

            // Step 2: Set the compose content, starting from the Gone scene.
            composeTestRule.setContent {
                PlatformTheme {
                    WithStatusIconContext(kosmos.tintedIconManagerFactory) {
                        with(scene) {
                            TestContentScope(currentScene = Scenes.Gone) { Content(Modifier) }
                        }
                    }
                }
            }
            kosmos.runCurrent()
            composeTestRule.waitForIdle()

            // Step 3: Open the shade.
            composeTestRule.swipeDownFromTopCenter()
            kosmos.runCurrent()
            composeTestRule.waitForIdle()

            // Assert that the UMO is visible before swiping
            composeTestRule
                .onNodeWithContentDescription(EXPECTED_UMO_CONTENT_DESC, substring = true)
                .assertExists()

            // Step 4: Swipe the UMO to dismiss it.
            composeTestRule
                .onNodeWithContentDescription(EXPECTED_UMO_CONTENT_DESC, substring = true)
                .performTouchInput(swipeAction)
            kosmos.runCurrent()
            composeTestRule.waitForIdle()

            // Step 5: Assert that the UMO is gone.
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                // Confirm PLAYER is no longer displayed.
                try {
                    composeTestRule
                        .onNodeWithContentDescription(EXPECTED_UMO_CONTENT_DESC, substring = true)
                        .assertIsNotDisplayed()
                    true
                } catch (e: AssertionError) {
                    false
                }
            }
        }

    private companion object {
        private const val EXPECTED_UMO_CONTENT_DESC = "Fake_Dismissible_Music_Player"
    }
}
