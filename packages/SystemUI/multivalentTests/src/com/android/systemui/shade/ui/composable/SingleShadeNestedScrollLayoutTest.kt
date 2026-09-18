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

package com.android.systemui.shade.ui.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.TestContentScope
import com.android.compose.gesture.effect.rememberOffsetOverscrollEffect
import com.android.internal.jank.Cuj.CUJ_NOTIFICATION_SHADE_SCROLL_FLING
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.scene.session.shared.SessionStorage
import com.android.systemui.scene.session.ui.composable.SaveableSession
import com.android.systemui.scene.session.ui.composable.Session
import com.android.systemui.statusbar.notification.stack.data.repository.notificationPlaceholderRepository
import com.android.systemui.statusbar.notification.stack.domain.interactor.notificationStackAppearanceInteractor
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.notificationsPlaceholderViewModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class SingleShadeNestedScrollLayoutTest : SysuiTestCase() {
    @get:Rule val rule = createComposeRule()

    private val kosmos = testKosmos()

    // region Layout tests
    @Test
    fun scrimAtRest_laidOutCorrectly() =
        kosmos.runTest {
            // Given: Scrim sits at its default position (QQS fully visible).
            setTestContent(contentHeight = { LayoutSize })

            // Initial layout placement
            assertNode(TAG_LAYOUT, width = LayoutSize, height = LayoutSize, top = 0.dp)
            // StatusBar is matching its size constraints and is placed on top
            assertNode(TAG_SB, width = LayoutSize, height = StatusBarHeight, top = 0.dp)
            // Headers are matching their size constraints and are placed below the StatusBar
            assertNode(TAG_HEADER, width = LayoutSize, height = HeaderHeight, top = StatusBarHeight)
            // The Scrim is filling the Layout size, and is sitting below the Headers
            assertScrimAtRest()
        }

    @Test
    fun scrimScrolledToTop_laidOutCorrectly() =
        kosmos.runTest {
            // Given the content is tall enough to scroll, and scrim is at rest
            setTestContent(contentHeight = { 1000.dp })
            assertScrimAtRest()

            // When swiped up
            swipeScrimUp()

            // Then scrim moves up
            assertScrimScrolledToTop()
        }

    @Test
    fun scrimScrolledUpAndDown() =
        kosmos.runTest {
            // Given a scrim tall enough to scroll, and is at rest
            setTestContent(contentHeight = { 1000.dp })

            // When swiped up
            swipeScrimUp()
            // Then scrim moves up
            assertScrimScrolledToTop()

            // When swiped down
            swipeScrimDown()
            // Then the scrim returns to rest
            assertScrimAtRest()
        }

    @Test
    fun scrimDoesNotScrollWhenContentIsShort() =
        kosmos.runTest {
            // Given content that is shorter than the available space (LayoutSize - SB - Header)
            setTestContent(contentHeight = { 100.dp })
            assertScrimAtRest()

            // When swiped up
            swipeScrimUp()

            // Then it should STILL be at rest (did not move because content is short)
            assertScrimAtRest()
        }

    @Test
    fun scrimSnapsToRestWhenContentShrinks() =
        kosmos.runTest {
            // Given tall content, scrolled all the way to the top
            var contentHeight by mutableStateOf(1000.dp)
            setTestContent(contentHeight = { contentHeight })
            swipeScrimUp()
            assertScrimScrolledToTop()

            // When content suddenly becomes short (shorter than available space at rest)
            contentHeight = 100.dp
            rule.waitForIdle()

            // Then the scrim snaps back to rest automatically
            assertScrimAtRest()
        }

    @Test
    fun scrimAtRest_isSwipeDownExpandAllowed_true() =
        kosmos.runTest {
            lateinit var isSwipeDownExpandAllowed: () -> Boolean

            // Given: Scrim sits at its default position (QQS fully visible).
            setTestContent { onHeightChanged: (Int) -> Unit, isScrimAtRest: () -> Boolean ->
                // Capture the lambda, so it can be called outside setTestContent
                isSwipeDownExpandAllowed = isScrimAtRest

                // Emulate the content structure of the real shade.
                Box(
                    Modifier.fillMaxWidth().height(LayoutSize).onSizeChanged {
                        onHeightChanged(it.height)
                    }
                )
            }

            assertThat(isSwipeDownExpandAllowed()).isTrue()
        }

    @Test
    fun scrimScrolledToTop__isSwipeDownExpandAllowed_false() =
        kosmos.runTest {
            lateinit var isSwipeDownExpandAllowed: () -> Boolean

            // Given the content is tall enough to scroll, and scrim is at rest
            setTestContent { onHeightChanged: (Int) -> Unit, isScrimAtRest: () -> Boolean ->
                // Capture the lambda, so it can be called outside setTestContent
                isSwipeDownExpandAllowed = isScrimAtRest

                // Emulate the content structure of the real shade.
                Box(
                    Modifier.fillMaxWidth().height(1000.dp).onSizeChanged {
                        onHeightChanged(it.height)
                    }
                )
            }
            assertThat(isSwipeDownExpandAllowed()).isTrue()

            // When swiped up
            swipeScrimUp()

            // Then scrim moves up
            assertThat(isSwipeDownExpandAllowed()).isFalse()
        }

    @Test
    fun afterScrimSnapsToRestAfterContentShrinks_isSwipeDownExpandAllowed_true() =
        kosmos.runTest {
            lateinit var isSwipeDownExpandAllowed: () -> Boolean

            // Given tall content, scrolled all the way to the top
            var contentHeight by mutableStateOf(1000.dp)

            setTestContent { onHeightChanged: (Int) -> Unit, isScrimAtRest: () -> Boolean ->
                // Capture the lambda, so it can be called outside setTestContent
                isSwipeDownExpandAllowed = isScrimAtRest

                // Emulate the content structure of the real shade.
                Box(
                    Modifier.fillMaxWidth().height(contentHeight).onSizeChanged {
                        onHeightChanged(it.height)
                    }
                )
            }
            swipeScrimUp()
            assertThat(isSwipeDownExpandAllowed()).isFalse()

            // When content suddenly becomes short (shorter than available space at rest)
            contentHeight = 100.dp
            rule.waitForIdle() // wait for compose

            // Then the gesture is allowed again
            assertThat(isSwipeDownExpandAllowed()).isTrue()
        }

    // endregion

    // region Side effect tests
    @Test
    fun scrollState_scrimAtRest() =
        kosmos.runTest {
            val contentScrollState by
                collectLastValue(notificationPlaceholderRepository.shadeScrollState)

            // When the scrim is at rest
            setTestContent(contentHeight = { LayoutSize })
            runCurrent()

            // Then the content is "scrolledToTop"
            assertThat(contentScrollState?.isScrolledToTop).isTrue()
            assertThat(contentScrollState?.scrollPosition).isEqualTo(0)
        }

    @Test
    fun scrollState_scrimScrolledToTop() =
        kosmos.runTest {
            val contentScrollState by
                collectLastValue(notificationPlaceholderRepository.shadeScrollState)

            // Given the content is tall enough to scroll, and the scrim is at rest
            setTestContent(contentHeight = { 1000.dp })
            runCurrent()
            assertThat(contentScrollState?.isScrolledToTop).isTrue()

            // When the scrim is swiped up
            swipeScrimUp()
            runCurrent()

            // Then the content is not "scrolledToTop" anymore
            assertThat(contentScrollState?.isScrolledToTop).isFalse()
        }

    @Test
    fun scrollState_scrimDoesNotScrollToTop_whileExpandingNotificationGesture() =
        kosmos.runTest {
            val contentScrollState by
                collectLastValue(notificationPlaceholderRepository.shadeScrollState)

            // Given the content is tall enough to scroll, and the scrim is at rest
            setTestContent(contentHeight = { 1000.dp })

            // Given the current gesture is expanding a notification
            kosmos.notificationStackAppearanceInteractor.setCurrentGestureExpandingNotif(true)
            kosmos.notificationsPlaceholderViewModel.activateIn(kosmos.testScope)
            runCurrent()

            // When the scrim is swiped up
            swipeScrimUp()
            // Then scrim does NOT move up
            assertScrimAtRest()
            assertThat(contentScrollState?.isScrolledToTop).isFalse()

            // When the current gesture is no longer expanding a notification
            kosmos.notificationStackAppearanceInteractor.setCurrentGestureExpandingNotif(false)
            runCurrent()

            // When the scrim is swiped up
            swipeScrimUp()
            // Then scrim moves up
            assertScrimScrolledToTop()
        }

    @Test
    fun scrollState_whenScrimSnapsToRestAfterContentShrinks() =
        kosmos.runTest {
            val contentScrollState by
                collectLastValue(notificationPlaceholderRepository.shadeScrollState)

            // Given tall content, scrolled all the way to the top
            var contentHeight by mutableStateOf(1000.dp)
            setTestContent(contentHeight = { contentHeight })
            swipeScrimUp()
            runCurrent()
            assertThat(contentScrollState?.isScrolledToTop).isFalse()

            // When content suddenly becomes short (shorter than available space at rest)
            contentHeight = 100.dp
            rule.waitForIdle() // wait for compose
            runCurrent() // wait for the flow

            // Then the content is "scrolledToTop" again
            assertThat(contentScrollState?.isScrolledToTop).isTrue()
        }

    @Test
    fun scrollState_scrimScrollingUpdatesJankMonitor() =
        kosmos.runTest {
            val contentScrollState by
                collectLastValue(notificationPlaceholderRepository.shadeScrollState)

            // Given the content is tall enough to scroll, and the scrim is at rest
            setTestContent(contentHeight = { 1000.dp })
            runCurrent()
            assertThat(contentScrollState?.isScrolledToTop).isTrue()

            // When the scrim is swiped up
            reset(kosmos.interactionJankMonitor)
            swipeScrimUp()
            runCurrent()

            // Then the jankMonitor is called with begin/end
            verify(kosmos.interactionJankMonitor, times(1))
                .begin(any(), eq(CUJ_NOTIFICATION_SHADE_SCROLL_FLING))
            verify(kosmos.interactionJankMonitor, times(1))
                .end(eq(CUJ_NOTIFICATION_SHADE_SCROLL_FLING))
        }

    // endregion

    // region Setup Helpers
    private fun setTestContent(contentHeight: () -> Dp) =
        setTestContent { onHeightChanged: (Int) -> Unit, _ ->
            // Emulate the content structure of the real shade.
            Box(
                Modifier.fillMaxWidth().height(contentHeight()).onSizeChanged {
                    onHeightChanged(it.height)
                }
            )
        }

    private fun setTestContent(
        scrollingContent:
            @Composable
            (onContentHeightChanged: (Int) -> Unit, isScrimAtRest: () -> Boolean) -> Unit
    ) {
        rule.setContent {
            val contentScrollState = rememberScrollState()
            val contentOverscrollEffect = rememberOffsetOverscrollEffect()
            TestContentScope {
                SingleShadeNestedScrollLayout(
                    modifier = Modifier.testTag(TAG_LAYOUT).size(LayoutSize),
                    shadeSession = rememberShadeSession(),
                    viewModel = kosmos.notificationsPlaceholderViewModel,
                    contentScrollState = contentScrollState,
                    scrollingContentOverscrollEffect = rememberOffsetOverscrollEffect(),
                    shortContentOverscrollEffect = rememberOffsetOverscrollEffect(),
                    jankMonitor = kosmos.interactionJankMonitor,
                    statusBarHeader = {
                        Box(Modifier.testTag(TAG_SB).fillMaxWidth().height(StatusBarHeight))
                    },
                    mediaAndQqsHeader = {
                        Box(Modifier.testTag(TAG_HEADER).fillMaxWidth().height(HeaderHeight))
                    },
                    scrollableScrim = { onHeightChanged: (Int) -> Unit, isScrimAtRest: () -> Boolean
                        ->
                        // This box must be scrollable, for the parent's NestedScrollConnection
                        Box(
                            Modifier.testTag(TAG_SCRIM)
                                .fillMaxSize()
                                .verticalScroll(contentScrollState, contentOverscrollEffect)
                        ) {
                            // Emulate the content structure of the real shade.
                            scrollingContent(onHeightChanged, isScrimAtRest)
                        }
                    },
                    cutoutInsetsProvider = { null },
                )
            }
        }
        rule.waitForIdle()
    }

    @Composable
    private fun rememberShadeSession() =
        object : SaveableSession, Session by Session(SessionStorage()) {
            @Composable
            override fun <T : Any> rememberSaveableSession(
                vararg inputs: Any?,
                saver: Saver<T, out Any>,
                key: String?,
                init: () -> T,
            ): T = rememberSession(key, inputs = inputs, init = init)
        }

    // endregion

    // region Interaction Helpers
    private fun swipeScrimUp() {
        rule.onNodeWithTag(TAG_SCRIM).performTouchInput { swipeUp(startY = bottom, endY = top) }
        rule.waitForIdle()
    }

    private fun swipeScrimDown() {
        rule.onNodeWithTag(TAG_SCRIM).performTouchInput { swipeDown(startY = top, endY = bottom) }
        rule.waitForIdle()
    }

    // endregion

    // region Assertion Helpers
    private fun assertScrimAtRest() {
        // Scrim at its default position at rest. Placed below the Status Bar AND the Headers
        rule
            .onNodeWithTag(TAG_SCRIM)
            .assertTopPositionInRootIsEqualTo(StatusBarHeight + HeaderHeight)
            // And fills the layout size
            .assertWidthIsEqualTo(LayoutSize)
            .assertHeightIsEqualTo(LayoutSize - StatusBarHeight - HeaderHeight)
    }

    private fun assertScrimScrolledToTop() {
        // Scrim Scrolled all the way to top, covers the Headers, placed directly below Status Bar
        rule
            .onNodeWithTag(TAG_SCRIM)
            .assertTopPositionInRootIsEqualTo(StatusBarHeight)
            // And fills the layout size
            .assertWidthIsEqualTo(LayoutSize)
            .assertHeightIsEqualTo(LayoutSize - StatusBarHeight)
    }

    @Suppress("SameParameterValue") // it's a test let's be explicit about asserted values
    private fun assertNode(tag: String, width: Dp, height: Dp, top: Dp) {
        rule
            .onNodeWithTag(tag)
            .assertWidthIsEqualTo(width)
            .assertHeightIsEqualTo(height)
            .assertTopPositionInRootIsEqualTo(top)
    }

    // endregion

    companion object {
        private val LayoutSize = 300.dp
        private val StatusBarHeight = 30.dp
        private val HeaderHeight = 70.dp

        private const val TAG_LAYOUT = "layout"
        private const val TAG_SB = "sb"
        private const val TAG_HEADER = "qqs"
        private const val TAG_SCRIM = "scrim"
    }
}
