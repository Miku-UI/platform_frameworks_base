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

package com.android.wm.shell.bubbles.bar

import android.animation.AnimatorTestRule
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.children
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.wm.shell.bubbles.Bubble
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [BubbleBarMenuViewController]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleBarMenuViewControllerTest {

    @get:Rule val animatorTestRule: AnimatorTestRule = AnimatorTestRule(this)
    private lateinit var activityScenario: ActivityScenario<TestActivity>
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var handleView: BubbleBarHandleView
    private lateinit var menuViewController: BubbleBarMenuViewController
    private val listener = TestListener()
    private lateinit var container: FrameLayout

    @Before
    fun setUp() {
        activityScenario = ActivityScenario.launch(TestActivity::class.java)
        activityScenario.onActivity { activity -> container = activity.container }
        handleView = BubbleBarHandleView(context)
        menuViewController = BubbleBarMenuViewController(context, handleView, container)
        menuViewController.setListener(listener)
    }

    @Test
    fun showMenu_immediatelyUpdatesVisibility() {
        activityScenario.onActivity { menuViewController.showMenu(/* animated= */ true) }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertThat(listener.visible).isTrue()

        // advance the animator timer since the actual visibility of the menu is updated in the
        // middle of the animation
        activityScenario.onActivity { animatorTestRule.advanceTimeBy(600) }
        assertThat(menuViewController.isMenuVisible).isTrue()
    }

    @Test
    fun hideMenu_updatesVisibilityAfterAnimationEnds() {
        activityScenario.onActivity { menuViewController.showMenu(/* animated= */ true) }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertThat(listener.visible).isTrue()

        activityScenario.onActivity { animatorTestRule.advanceTimeBy(600) }
        assertThat(menuViewController.isMenuVisible).isTrue()

        activityScenario.onActivity { menuViewController.hideMenu(/* animated= */ true) }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // check that the listener hasn't been notified yet
        assertThat(listener.visible).isTrue()

        activityScenario.onActivity { animatorTestRule.advanceTimeBy(600) }
        assertThat(listener.visible).isFalse()
        assertThat(menuViewController.isMenuVisible).isFalse()
    }

    @Test
    fun showMenu_notAnimated_updatesStateImmediately() {
        // Show the menu without animation
        activityScenario.onActivity {
            menuViewController.showMenu(/* animated= */ false)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // Verify that the state is updated immediately
        assertThat(listener.visible).isTrue()
        assertThat(menuViewController.isMenuVisible).isTrue()
        assertThat(handleView.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun hideMenu_notAnimated_updatesStateImmediately() {
        // Show the menu without animation first
        activityScenario.onActivity {
            menuViewController.showMenu(/* animated= */ false)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // Hide the menu without animation
        activityScenario.onActivity {
            menuViewController.hideMenu(/* animated= */ false)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // Verify that the state is updated immediately
        assertThat(listener.visible).isFalse()
        assertThat(menuViewController.isMenuVisible).isFalse()
        assertThat(handleView.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun showMenu_animationCancelled_onCompleteActionsNotRun() {
        // Start showing the menu with animation
        activityScenario.onActivity {
            menuViewController.showMenu(/* animated= */ true)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // Let the animation run for a bit, but not to completion
        activityScenario.onActivity {
            animatorTestRule.advanceTimeBy(300)
        }

        // Cancel the "show" animation by starting to hide the menu
        activityScenario.onActivity {
            menuViewController.hideMenu(/* animated= */ true)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // Finish the "hide" animation
        activityScenario.onActivity {
            animatorTestRule.advanceTimeBy(600)
        }

        // Find the menu view to check focus.
        val menuView = container.children.find { it is BubbleBarMenuView } as BubbleBarMenuView
        val firstItem = menuView.getChildAt(0)

        // The onComplete action for showMenu requests focus. Since the animation was cancelled,
        // the onComplete action should not have run, and the view should not have focus.
        assertThat(firstItem.isAccessibilityFocused).isFalse()
    }

    @Test
    fun hideMenu_animationCancelled_onCompleteActionsNotRun() {
        // Show the menu and wait for the animation to finish.
        activityScenario.onActivity {
            menuViewController.showMenu(/* animated= */ true)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        activityScenario.onActivity {
            animatorTestRule.advanceTimeBy(600)
        }
        assertThat(listener.visible).isTrue()

        // Start hiding the menu with animation.
        activityScenario.onActivity {
            menuViewController.hideMenu(/* animated= */ true)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // Let the animation run for a bit, but not to completion.
        activityScenario.onActivity {
            animatorTestRule.advanceTimeBy(300)
        }

        // Cancel the "hide" animation by showing the menu again.
        activityScenario.onActivity {
            menuViewController.showMenu(/* animated= */ true)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // The onComplete action for hideMenu notifies the listener that it's not visible.
        // Since the animation was cancelled, this should not have happened and the listener
        // should still think the menu is visible.
        assertThat(listener.visible).isTrue()

        // Finish the "show" animation.
        activityScenario.onActivity {
            animatorTestRule.advanceTimeBy(600)
        }
        // Verify the menu is visible.
        assertThat(menuViewController.isMenuVisible).isTrue()
    }

    private class TestListener : BubbleBarMenuViewController.Listener {

        var visible = false

        override fun onMenuVisibilityChanged(visible: Boolean) {
            this.visible = visible
        }

        override fun onUnBubbleConversation(bubble: Bubble?) {}

        override fun onOpenAppSettings(bubble: Bubble?) {}

        override fun onDismissBubble(bubble: Bubble?) {}

        override fun onMoveToFullscreen(bubble: Bubble?) {}
    }

    class TestActivity : Activity() {
        lateinit var container: FrameLayout

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            container = FrameLayout(applicationContext)
            setContentView(container)
        }
    }
}
