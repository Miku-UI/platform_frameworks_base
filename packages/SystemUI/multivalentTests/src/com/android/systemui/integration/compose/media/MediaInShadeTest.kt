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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
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
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.media.controls.shared.model.MediaButton
import com.android.systemui.media.remedia.data.repository.fakeActiveMediaData
import com.android.systemui.media.remedia.data.repository.fakePausedMediaDataWithCustomActions
import com.android.systemui.media.remedia.data.repository.mediaPlayActionButton
import com.android.systemui.media.remedia.data.repository.setFakeCurrentMediaData
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.notificationRulesParentViewModelFactory
import com.android.systemui.qs.composefragment.dagger.usingMediaInComposeFragment
import com.android.systemui.scene.session.shared.SessionStorage
import com.android.systemui.scene.session.ui.composable.SaveableSession
import com.android.systemui.scene.session.ui.composable.Session
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.shade.ui.composable.ShadeScene
import com.android.systemui.shade.ui.composable.WithStatusIconContext
import com.android.systemui.shade.ui.viewmodel.shadeSceneContentViewModelFactory
import com.android.systemui.shade.ui.viewmodel.shadeUserActionsViewModelFactory
import com.android.systemui.statusbar.notification.stack.ui.view.notificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.notificationsPlaceholderViewModelFactory
import com.android.systemui.statusbar.phone.ui.tintedIconManagerFactory
import com.android.systemui.testKosmos
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class MediaInShadeTest : SysuiTestCase() {
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun umoInSingleShade() =
        kosmos.runTest {
            setFakeCurrentMediaData(listOf(fakeActiveMediaData))
            usingMediaInComposeFragment = true
            enableSingleShade()

            // Set the shade content.
            composeTestRule.setContent {
                PlatformTheme {
                    WithStatusIconContext(tintedIconManagerFactory) {
                        with(scene) {
                            TestContentScope(currentScene = Scenes.Shade) { Content(Modifier) }
                        }
                    }
                }
            }
            runCurrent()
            composeTestRule.waitForIdle()

            // Verify that the UMO exists.
            composeTestRule
                .onNodeWithContentDescription(EXPECTED_UMO_CONTENT_DESC, substring = true)
                .assertExists()
        }

    @Test
    fun umoInSplitShade() =
        kosmos.runTest {
            setFakeCurrentMediaData(listOf(fakeActiveMediaData))
            usingMediaInComposeFragment = true
            enableSplitShade()

            // Set the shade content.
            composeTestRule.setContent {
                PlatformTheme {
                    WithStatusIconContext(tintedIconManagerFactory) {
                        with(scene) {
                            TestContentScope(currentScene = Scenes.Shade) { Content(Modifier) }
                        }
                    }
                }
            }
            runCurrent()
            composeTestRule.waitForIdle()

            // Verify that the split shade qs exists.
            composeTestRule.onNodeWithTag(SPLIT_SHADE_QS_TAG).assertExists()
            // Verify that the UMO exists.
            composeTestRule
                .onNodeWithContentDescription(EXPECTED_UMO_CONTENT_DESC, substring = true)
                .assertExists()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun multipleMediaInSingleShade() =
        kosmos.runTest {
            // Add a new media object to current media list.
            setFakeCurrentMediaData(
                listOf(fakeActiveMediaData, fakePausedMediaDataWithCustomActions)
            )
            usingMediaInComposeFragment = true
            enableSingleShade()

            // Set the shade content.
            composeTestRule.setContent {
                PlatformTheme {
                    WithStatusIconContext(tintedIconManagerFactory) {
                        with(scene) {
                            TestContentScope(currentScene = Scenes.Shade) { Content(Modifier) }
                        }
                    }
                }
            }
            runCurrent()
            composeTestRule.waitForIdle()

            // Verify that the UMO exists.
            composeTestRule
                .onNodeWithContentDescription(EXPECTED_UMO_CONTENT_DESC, substring = true)
                .assertExists()

            // Swipe to see the second media.
            composeTestRule
                .onNodeWithContentDescription(EXPECTED_UMO_CONTENT_DESC, substring = true)
                .performTouchInput { swipeLeft() }

            runCurrent()
            composeTestRule.waitForIdle()

            // Verify that the second media exists.
            composeTestRule
                .onNodeWithContentDescription(EXPECTED_SECOND_MEDIA_CONTENT_DESC, substring = true)
                .assertExists()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testUpdateMedia() =
        kosmos.runTest {
            // Add a media object to current media list.
            setFakeCurrentMediaData(mutableStateListOf(fakeActiveMediaData))
            usingMediaInComposeFragment = true
            enableSingleShade()

            // Set the shade content.
            composeTestRule.setContent {
                PlatformTheme {
                    WithStatusIconContext(tintedIconManagerFactory) {
                        with(scene) {
                            TestContentScope(currentScene = Scenes.Shade) { Content(Modifier) }
                        }
                    }
                }
            }
            runCurrent()
            composeTestRule.waitForIdle()

            // Verify that the UMO exists.
            composeTestRule
                .onNodeWithContentDescription(EXPECTED_UMO_CONTENT_DESC, substring = true)
                .assertExists()
            // Verify that the pause button is displayed.
            composeTestRule
                .onNodeWithContentDescription(PAUSE_BUTTON_CONTENT_DESC)
                .assertIsDisplayed()

            val updatedFakeMediaData =
                fakeActiveMediaData.copy(
                    semanticActions = MediaButton(playOrPause = mediaPlayActionButton),
                    isPlaying = false,
                )

            // Update media.
            setFakeCurrentMediaData(mutableStateListOf(updatedFakeMediaData))
            composeTestRule.waitForIdle()

            // Verify that the play button is now displayed.
            composeTestRule
                .onNodeWithContentDescription(PLAY_BUTTON_CONTENT_DESC)
                .assertIsDisplayed()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun openGutsOnNonDismissibleMedia() =
        kosmos.runTest {
            setFakeCurrentMediaData(listOf(fakeActiveMediaData.copy(isClearable = false)))
            usingMediaInComposeFragment = true
            enableSingleShade()

            // Set the shade content.
            composeTestRule.setContent {
                PlatformTheme {
                    WithStatusIconContext(tintedIconManagerFactory) {
                        with(scene) {
                            TestContentScope(currentScene = Scenes.Shade) { Content(Modifier) }
                        }
                    }
                }
            }
            runCurrent()
            composeTestRule.waitForIdle()

            // Verify that the UMO exists.
            composeTestRule
                .onNodeWithContentDescription(EXPECTED_UMO_CONTENT_DESC, substring = true)
                .assertExists()

            // Long press to trigger Guts.
            composeTestRule
                .onNodeWithContentDescription(EXPECTED_UMO_CONTENT_DESC, substring = true)
                .performTouchInput { longClick() }
            composeTestRule.waitForIdle()

            // Verify that the Hide button doesn't exist.
            composeTestRule.onNodeWithText(HIDE_BUTTON_TEXT).assertDoesNotExist()

            // Click the Cancel button.
            composeTestRule.onNodeWithText(CANCEL_BUTTON_TEXT).performTouchInput { click() }
            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithContentDescription(EXPECTED_UMO_CONTENT_DESC, substring = true)
                .assertExists()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun openGutsOnDismissibleMedia() =
        kosmos.runTest {
            setFakeCurrentMediaData(listOf(fakePausedMediaDataWithCustomActions))
            usingMediaInComposeFragment = true
            enableSingleShade()

            // Set the shade content.
            composeTestRule.setContent {
                PlatformTheme {
                    WithStatusIconContext(tintedIconManagerFactory) {
                        with(scene) {
                            TestContentScope(currentScene = Scenes.Shade) { Content(Modifier) }
                        }
                    }
                }
            }
            runCurrent()
            composeTestRule.waitForIdle()

            // Verify that the UMO exists.
            composeTestRule
                .onNodeWithContentDescription(EXPECTED_SECOND_MEDIA_CONTENT_DESC, substring = true)
                .assertExists()

            // Long press to trigger Guts.
            composeTestRule
                .onNodeWithContentDescription(EXPECTED_SECOND_MEDIA_CONTENT_DESC, substring = true)
                .performTouchInput { longClick() }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText(HIDE_BUTTON_TEXT).assertIsDisplayed()
        }

    private companion object {
        const val SPLIT_SHADE_QS_TAG = "element:SplitShadeQuickSettings"
        const val EXPECTED_UMO_CONTENT_DESC = "Fake_Music_Player"
        const val EXPECTED_SECOND_MEDIA_CONTENT_DESC = "Fake_Audio_Player"
        const val PLAY_BUTTON_CONTENT_DESC = "Play"
        const val PAUSE_BUTTON_CONTENT_DESC = "Pause"
        const val HIDE_BUTTON_TEXT = "Hide"
        const val CANCEL_BUTTON_TEXT = "Cancel"
    }
}
