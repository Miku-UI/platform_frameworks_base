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
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.TestContentScope
import com.android.compose.theme.PlatformTheme
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.integration.SystemUiIntegrationTest
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.media.remedia.data.repository.fakeActiveMediaData
import com.android.systemui.media.remedia.data.repository.setFakeCurrentMediaData
import com.android.systemui.notifications.intelligence.rules.ui.viewmodel.notificationRulesParentViewModelFactory
import com.android.systemui.qs.composefragment.dagger.usingMediaInComposeFragment
import com.android.systemui.qs.ui.composable.QuickSettingsScene
import com.android.systemui.qs.ui.composable.QuickSettingsShadeOverlay
import com.android.systemui.qs.ui.viewmodel.quickSettingsContainerViewModelFactory
import com.android.systemui.qs.ui.viewmodel.quickSettingsSceneContentViewModelFactory
import com.android.systemui.qs.ui.viewmodel.quickSettingsShadeOverlayActionsViewModelFactory
import com.android.systemui.qs.ui.viewmodel.quickSettingsShadeOverlayContentViewModelFactory
import com.android.systemui.qs.ui.viewmodel.quickSettingsUserActionsViewModelFactory
import com.android.systemui.scene.session.shared.SessionStorage
import com.android.systemui.scene.session.ui.composable.SaveableSession
import com.android.systemui.scene.session.ui.composable.Session
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.ui.composable.WithSceneContainerPreloadedResources
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.ui.composable.WithStatusIconContext
import com.android.systemui.statusbar.notification.stack.ui.view.notificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.notificationsPlaceholderViewModelFactory
import com.android.systemui.statusbar.phone.ui.tintedIconManagerFactory
import com.android.systemui.testKosmos
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
@EnableSceneContainer
@EnableFlags(Flags.FLAG_DUAL_SHADE)
// TODO(b/494578600) Remove DisableFlags once fixed
@DisableFlags(Flags.FLAG_STATUS_BAR_MOBILE_ICON_KAIROS)
@SystemUiIntegrationTest
class MediaInQuickSettingsTest : SysuiTestCase() {
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
        QuickSettingsScene(
            shadeSession = shadeSession,
            notificationStackScrollView = { kosmos.notificationScrollView },
            notificationsPlaceholderViewModelFactory =
                kosmos.notificationsPlaceholderViewModelFactory,
            actionsViewModelFactory = kosmos.quickSettingsUserActionsViewModelFactory,
            contentViewModelFactory = kosmos.quickSettingsSceneContentViewModelFactory,
            notificationRulesParentViewModelFactory =
                kosmos.notificationRulesParentViewModelFactory,
            jankMonitor = kosmos.interactionJankMonitor,
        )

    private val overlay =
        QuickSettingsShadeOverlay(
            actionsViewModelFactory = kosmos.quickSettingsShadeOverlayActionsViewModelFactory,
            contentViewModelFactory = kosmos.quickSettingsShadeOverlayContentViewModelFactory,
            quickSettingsContainerViewModelFactory = kosmos.quickSettingsContainerViewModelFactory,
            notificationStackScrollView = { kosmos.notificationScrollView },
            notificationsPlaceholderViewModelFactory =
                kosmos.notificationsPlaceholderViewModelFactory,
        )

    @Before
    fun setup() {
        kosmos.setFakeCurrentMediaData(listOf(kosmos.fakeActiveMediaData))
    }

    @Test
    fun umoInQuickSettings() =
        kosmos.runTest {
            usingMediaInComposeFragment = true
            enableSingleShade()

            // Set the quick settings content.
            composeTestRule.setContent {
                PlatformTheme {
                    WithStatusIconContext(tintedIconManagerFactory) {
                        with(scene) {
                            TestContentScope(currentScene = Scenes.QuickSettings) {
                                Content(Modifier)
                            }
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
    fun umoInQuickSettingsOverlay() =
        kosmos.runTest {
            usingMediaInComposeFragment = true
            enableDualShade()

            // Set the quick settings overlay content.
            composeTestRule.setContent {
                WithSceneContainerPreloadedResources {
                    PlatformTheme {
                        WithStatusIconContext(tintedIconManagerFactory) {
                            with(overlay) {
                                TestContentScope(currentScene = Scenes.Gone) { Content(Modifier) }
                            }
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

    private companion object {
        const val EXPECTED_UMO_CONTENT_DESC = "Fake_Music_Player"
    }
}
