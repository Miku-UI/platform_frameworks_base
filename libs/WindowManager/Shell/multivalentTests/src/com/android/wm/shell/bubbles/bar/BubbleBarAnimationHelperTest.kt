/*
 * Copyright (C) 2024 The Android Open Source Project
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
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.graphics.Insets
import android.graphics.Outline
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.bubbles.Bubble
import com.android.wm.shell.bubbles.BubbleExpandedViewManager
import com.android.wm.shell.bubbles.BubbleOverflow
import com.android.wm.shell.bubbles.BubblePositioner
import com.android.wm.shell.bubbles.FakeBubbleExpandedViewManager
import com.android.wm.shell.bubbles.FakeBubbleFactory
import com.android.wm.shell.bubbles.FakeBubbleTaskViewFactory
import com.android.wm.shell.bubbles.logging.BubbleLogger
import com.android.wm.shell.common.TestShellExecutor
import com.android.wm.shell.shared.bubbles.DeviceConfig
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.verify

/**
 * Tests for [BubbleBarAnimationHelper]
 *
 * Build/Install/Run:
 * - Robolectric: atest WMShellRobolectricTests:BubbleBarAnimationHelperTest
 * - On device: atest WMShellMultivalentTestsOnDevice:BubbleBarAnimationHelperTest
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleBarAnimationHelperTest {

    @get:Rule val animatorTestRule: AnimatorTestRule = AnimatorTestRule(this)
    private lateinit var activityScenario: ActivityScenario<TestActivity>

    companion object {
        const val SCREEN_WIDTH = 2000
        const val SCREEN_HEIGHT = 1000
        val isRobolectricTest by lazy {
            try {
                Class.forName("org.robolectric.Robolectric")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
        }
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var animationHelper: BubbleBarAnimationHelper
    private lateinit var bubblePositioner: BubblePositioner
    private lateinit var expandedViewManager: BubbleExpandedViewManager
    private lateinit var bubbleLogger: BubbleLogger
    private lateinit var mainExecutor: TestShellExecutor
    private lateinit var bgExecutor: TestShellExecutor
    private lateinit var bubbleTaskViewFactory: FakeBubbleTaskViewFactory
    private lateinit var container: FrameLayout

    @Before
    fun setUp() {
        ProtoLog.REQUIRE_PROTOLOGTOOL = false
        ProtoLog.init()
        activityScenario = ActivityScenario.launch(TestActivity::class.java)
        activityScenario.onActivity { activity -> container = activity.container }
        val windowManager = context.getSystemService(WindowManager::class.java)
        bubblePositioner = BubblePositioner(context, windowManager)
        bubblePositioner.setShowingInBubbleBar(true)
        val deviceConfig =
            DeviceConfig(
                windowBounds = Rect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT),
                isLargeScreen = true,
                isSmallTablet = false,
                isLandscape = true,
                isRtl = false,
                insets = Insets.of(10, 20, 30, 40),
            )
        bubblePositioner.update(deviceConfig)
        bubblePositioner.updateBubbleBarTopOnScreen(200)
        expandedViewManager = FakeBubbleExpandedViewManager()
        bubbleLogger = BubbleLogger(UiEventLoggerFake())
        mainExecutor = TestShellExecutor()
        bgExecutor = TestShellExecutor()
        bubbleTaskViewFactory = FakeBubbleTaskViewFactory(context, mainExecutor)

        animationHelper = BubbleBarAnimationHelper(context, bubblePositioner, mainExecutor)
    }

    @After
    fun tearDown() {
        bgExecutor.flushAll()
        mainExecutor.flushAll()
    }

    @Test
    fun animateSwitch_bubbleToBubble_oldHiddenNewShown() {
        val fromBubble = createBubble(key = "from").initialize(container)
        val toBubble = createBubble(key = "to").initialize(container)

        val semaphore = Semaphore(0)
        val after = Runnable { semaphore.release() }

        activityScenario.onActivity {
            animationHelper.animateSwitch(
                fromBubble,
                toBubble,
                /* shouldApplyAsJumpcut= */ false,
                after,
            )
            animatorTestRule.advanceTimeBy(1000)
        }
        getInstrumentation().waitForIdleSync()

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(fromBubble.bubbleBarExpandedView?.visibility).isEqualTo(View.INVISIBLE)
        assertThat(fromBubble.bubbleBarExpandedView?.alpha).isEqualTo(0f)
        assertThat(fromBubble.bubbleBarExpandedView?.captionView?.alpha).isEqualTo(0f)
        assertThat(fromBubble.bubbleBarExpandedView?.isSurfaceZOrderedOnTop).isFalse()

        assertThat(toBubble.bubbleBarExpandedView?.visibility).isEqualTo(View.VISIBLE)
        assertThat(toBubble.bubbleBarExpandedView?.alpha).isEqualTo(1f)
        assertThat(toBubble.bubbleBarExpandedView?.captionView?.alpha).isEqualTo(1f)
        assertThat(toBubble.bubbleBarExpandedView?.handleView?.alpha).isEqualTo(1f)
        assertThat(toBubble.bubbleBarExpandedView?.isSurfaceZOrderedOnTop).isFalse()
    }

    @Test
    fun animateSwitch_bubbleToBubble_oldHiddenNewShown_applyAsJumpcut() {
        val fromBubble = createBubble(key = "from").initialize(container)
        val toBubble = createBubble(key = "to").initialize(container)

        val semaphore = Semaphore(0)
        val after = Runnable { semaphore.release() }

        activityScenario.onActivity {
            animationHelper.animateSwitch(
                fromBubble,
                toBubble,
                /* shouldApplyAsJumpcut= */ true,
                after,
            )
        }
        getInstrumentation().waitForIdleSync()

        if (!isRobolectricTest) {
            // The endRunnable is on TransactionCommittedListener, which won't be trigger with the
            // shadow implementation of SurfaceControl.

            // Wait for surface transaction applied.
            getInstrumentation().getUiAutomation().syncInputTransactions()
            mainExecutor.flushAll()

            assertThat(semaphore.tryAcquire()).isTrue()
        }

        assertThat(fromBubble.bubbleBarExpandedView?.visibility).isEqualTo(View.INVISIBLE)
        assertThat(fromBubble.bubbleBarExpandedView?.alpha).isEqualTo(0f)
        assertThat(fromBubble.bubbleBarExpandedView?.captionView?.alpha).isEqualTo(0f)
        assertThat(fromBubble.bubbleBarExpandedView?.isSurfaceZOrderedOnTop).isFalse()

        assertThat(toBubble.bubbleBarExpandedView?.visibility).isEqualTo(View.VISIBLE)
        assertThat(toBubble.bubbleBarExpandedView?.alpha).isEqualTo(1f)
        assertThat(toBubble.bubbleBarExpandedView?.captionView?.alpha).isEqualTo(1f)
        assertThat(toBubble.bubbleBarExpandedView?.handleView?.alpha).isEqualTo(1f)
        assertThat(toBubble.bubbleBarExpandedView?.isSurfaceZOrderedOnTop).isFalse()
    }

    @Test
    fun animateSwitch_quickSwitch_newlySelectedIsVisible() {
        val bubbleA = createBubble(key = "A").initialize(container)
        val bubbleB = createBubble(key = "B").initialize(container)

        activityScenario.onActivity {
            // Start an animation from A to B
            animationHelper.animateSwitch(
                bubbleA,
                bubbleB,
                /* shouldApplyAsJumpcut= */ false,
                /* endRunnable= */ null,
            )
            // This simulates Bubble B has finished animating out
            bubbleB.bubbleBarExpandedView!!.visibility = View.INVISIBLE
            // Let it run for a bit, but not finish
            animatorTestRule.advanceTimeBy(100)
        }
        getInstrumentation().waitForIdleSync()

        // Assert that even though we manually set it to INVISIBLE, the animation start
        // callback has corrected it to VISIBLE.
        assertThat(bubbleB.bubbleBarExpandedView?.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun animateSwitch_bubbleToBubble_handleColorTransferred() {
        val fromBubble = createBubble(key = "from").initialize(container)
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isSystemDark = uiMode == Configuration.UI_MODE_NIGHT_YES
        fromBubble.bubbleBarExpandedView!!
            .handleView
            .updateHandleColor(/* isRegionDark= */ isSystemDark, /* animated= */ false)
        val toBubble = createBubble(key = "to").initialize(container)

        activityScenario.onActivity {
            animationHelper.animateSwitch(
                fromBubble,
                toBubble,
                /* shouldApplyAsJumpcut= */ false,
                /* endRunnable= */ null,
            )
            animatorTestRule.advanceTimeBy(1000)
        }
        getInstrumentation().waitForIdleSync()

        assertThat(toBubble.bubbleBarExpandedView!!.handleView.handleColor)
            .isEqualTo(fromBubble.bubbleBarExpandedView!!.handleView.handleColor)
    }

    @Test
    fun animateSwitch_bubbleToBubble_triggersTaskViewLayout() {
        val fromBubble = createBubble("from").initialize(container)
        val toBubble = createBubble("to").initialize(container)

        activityScenario.onActivity {
            animationHelper.animateSwitch(
                fromBubble,
                toBubble,
                /* shouldApplyAsJumpcut= */ false,
            ) {}
            // Start the animation, but don't finish
            animatorTestRule.advanceTimeBy(100)
        }
        getInstrumentation().waitForIdleSync()

        // Clear invocations to ensure that task view layout happens after animation ends
        val taskView = toBubble.taskView
        clearInvocations(taskView)
        getInstrumentation().runOnMainSync { animatorTestRule.advanceTimeBy(900) }
        getInstrumentation().waitForIdleSync()

        verify(taskView, atLeastOnce()).layout(any<Int>(), any<Int>(), any<Int>(), any<Int>())
    }

    @Test
    fun animateSwitch_bubbleToOverflow_oldHiddenNewShown() {
        val fromBubble = createBubble(key = "from").initialize(container)
        val overflow = createOverflow().initialize(container)

        val semaphore = Semaphore(0)
        val after = Runnable { semaphore.release() }

        activityScenario.onActivity {
            animationHelper.animateSwitch(
                fromBubble,
                overflow,
                /* shouldApplyAsJumpcut= */ false,
                after,
            )
            animatorTestRule.advanceTimeBy(1000)
        }
        getInstrumentation().waitForIdleSync()

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(fromBubble.bubbleBarExpandedView?.visibility).isEqualTo(View.INVISIBLE)
        assertThat(fromBubble.bubbleBarExpandedView?.alpha).isEqualTo(0f)
        assertThat(fromBubble.bubbleBarExpandedView?.captionView?.alpha).isEqualTo(0f)
        assertThat(fromBubble.bubbleBarExpandedView?.isSurfaceZOrderedOnTop).isFalse()

        assertThat(overflow.bubbleBarExpandedView?.visibility).isEqualTo(View.VISIBLE)
        assertThat(overflow.bubbleBarExpandedView?.alpha).isEqualTo(1f)
        assertThat(overflow.bubbleBarExpandedView?.captionView?.alpha).isEqualTo(1f)
        assertThat(overflow.bubbleBarExpandedView?.handleView?.alpha).isEqualTo(1f)
    }

    @Test
    fun animateSwitch_overflowToBubble_oldHiddenNewShown() {
        val overflow = createOverflow().initialize(container)
        val toBubble = createBubble(key = "to").initialize(container)

        val semaphore = Semaphore(0)
        val after = Runnable { semaphore.release() }

        activityScenario.onActivity {
            animationHelper.animateSwitch(
                overflow,
                toBubble,
                /* shouldApplyAsJumpcut= */ false,
                after,
            )
            animatorTestRule.advanceTimeBy(1000)
        }
        getInstrumentation().waitForIdleSync()

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(overflow.bubbleBarExpandedView?.visibility).isEqualTo(View.INVISIBLE)
        assertThat(overflow.bubbleBarExpandedView?.alpha).isEqualTo(0f)
        assertThat(overflow.bubbleBarExpandedView?.captionView?.alpha).isEqualTo(0f)

        assertThat(toBubble.bubbleBarExpandedView?.visibility).isEqualTo(View.VISIBLE)
        assertThat(toBubble.bubbleBarExpandedView?.alpha).isEqualTo(1f)
        assertThat(toBubble.bubbleBarExpandedView?.captionView?.alpha).isEqualTo(1f)
        assertThat(toBubble.bubbleBarExpandedView?.handleView?.alpha).isEqualTo(1f)
        assertThat(toBubble.bubbleBarExpandedView?.isSurfaceZOrderedOnTop).isFalse()
    }

    @Test
    fun animateToRestPosition_triggersTaskViewLayout() {
        val bubble = createBubble("key").initialize(container)

        val semaphore = Semaphore(0)
        val after = Runnable { semaphore.release() }

        activityScenario.onActivity {
            animationHelper.animateExpansion(bubble, after)
            animatorTestRule.advanceTimeBy(1000)
        }
        getInstrumentation().waitForIdleSync()
        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()

        getInstrumentation().runOnMainSync {
            animationHelper.animateToRestPosition()
            animatorTestRule.advanceTimeBy(100)
        }
        // Clear invocations to ensure that task view layout happens after animation ends
        val taskView = bubble.taskView
        clearInvocations(taskView)
        getInstrumentation().runOnMainSync { animatorTestRule.advanceTimeBy(900) }
        getInstrumentation().waitForIdleSync()

        verify(taskView, atLeastOnce()).layout(any<Int>(), any<Int>(), any<Int>(), any<Int>())
    }

    @Test
    fun animateExpansion() {
        val bubble = createBubble(key = "b1").initialize(container)
        val bbev = bubble.bubbleBarExpandedView!!

        val semaphore = Semaphore(0)
        val after = Runnable { semaphore.release() }

        activityScenario.onActivity {
            bbev.onTaskCreated()
            animationHelper.animateExpansion(bubble, after)
            animatorTestRule.advanceTimeBy(1000)
        }
        getInstrumentation().waitForIdleSync()

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(bbev.alpha).isEqualTo(1)
    }

    @Test
    fun animateExpansion_cancelCallsEndCallback() {
        val bubble = createBubble(key = "b1").initialize(container)
        val bbev = bubble.bubbleBarExpandedView!!

        val semaphore = Semaphore(0)
        var afterCalled = false
        val after = Runnable {
            afterCalled = true
            semaphore.release()
        }

        activityScenario.onActivity {
            bbev.onTaskCreated()
            animationHelper.animateExpansion(bubble, after)
            animationHelper.cancelAnimations()
        }
        getInstrumentation().waitForIdleSync()

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(bbev.animationMatrix).isNull()
        assertThat(afterCalled).isTrue()
    }

    @Test
    fun animateExpansion_withPendingAnimation() {
        val bubble = createBubble("key").initialize(container)
        val bbev = bubble.bubbleBarExpandedView!!

        val semaphore = Semaphore(0)
        var afterCalled = false
        val after = Runnable {
            afterCalled = true
            semaphore.release()
        }

        var expandedViewWithPendingAnimationBefore: BubbleBarExpandedView? = null
        var expandedViewWithPendingAnimationAfter: BubbleBarExpandedView? = null

        activityScenario.onActivity {
            bbev.onTaskCreated()
            // Make the TaskView invisible so that the animation waits on TaskView visibility.
            bbev.bubbleTaskView!!.listener.onTaskVisibilityChanged(0, false /* visible */)
            animationHelper.animateExpansion(bubble, after)
            expandedViewWithPendingAnimationBefore =
                animationHelper.expandedViewWithPendingAnimation

            animationHelper.cancelAnimations()
            expandedViewWithPendingAnimationAfter = animationHelper.expandedViewWithPendingAnimation
        }
        getInstrumentation().waitForIdleSync()

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(bbev.animationMatrix).isNull()
        assertThat(afterCalled).isTrue()
        assertThat(expandedViewWithPendingAnimationBefore).isEqualTo(bbev)
        assertThat(expandedViewWithPendingAnimationAfter).isNull()
    }

    @Test
    fun onImeTopChanged_noOverlap() {
        val bubble = createBubble(key = "b1").initialize(container)
        val bbev = bubble.bubbleBarExpandedView!!

        val semaphore = Semaphore(0)
        val after = Runnable { semaphore.release() }

        activityScenario.onActivity {
            bbev.onTaskCreated()
            animationHelper.animateExpansion(bubble, after)
            animatorTestRule.advanceTimeBy(1000)
        }
        getInstrumentation().waitForIdleSync()

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()

        activityScenario.onActivity {
            // notify that the IME top coordinate is greater than the bottom of the expanded view.
            // there's no overlap so it should not be clipped.
            animationHelper.onImeTopChanged(bbev.contentBottomOnScreen * 2)
        }
        val outline = Outline()
        bbev.outlineProvider.getOutline(bbev, outline)
        assertThat(outline.mRect.bottom).isEqualTo(bbev.height)
    }

    @Test
    fun onImeTopChanged_overlapsWithExpandedView() {
        val bubble = createBubble(key = "b1").initialize(container)
        val bbev = bubble.bubbleBarExpandedView!!

        val semaphore = Semaphore(0)
        val after = Runnable { semaphore.release() }

        activityScenario.onActivity {
            bbev.onTaskCreated()
            animationHelper.animateExpansion(bubble, after)
            animatorTestRule.advanceTimeBy(1000)
        }
        getInstrumentation().waitForIdleSync()

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()

        activityScenario.onActivity {
            // notify that the IME top coordinate is less than the bottom of the expanded view,
            // meaning it overlaps with it so we should be clipping the expanded view.
            animationHelper.onImeTopChanged(bbev.contentBottomOnScreen - 10)
        }
        val outline = Outline()
        bbev.outlineProvider.getOutline(bbev, outline)
        assertThat(outline.mRect.bottom).isEqualTo(bbev.height - 10)
    }

    private fun createBubble(key: String): Bubble {
        val bubble = FakeBubbleFactory.createChatBubble(context, key)
        val bubbleTaskView = bubble.getOrCreateBubbleTaskView(bubbleTaskViewFactory)
        bubbleTaskView.listener.onTaskCreated(/* taskId= */ 1, ComponentName("package", "class"))

        FakeBubbleFactory.createExpandedView(
            context,
            bubblePositioner,
            expandedViewManager,
            bubble,
            bubbleTaskView,
            mainExecutor,
            bgExecutor,
            bubbleLogger,
        )
        return bubble
    }

    private fun createOverflow(): BubbleOverflow {
        val overflow = BubbleOverflow(context, bubblePositioner)
        overflow.initializeForBubbleBar(expandedViewManager, bubblePositioner)
        return overflow
    }

    private fun Bubble.initialize(container: ViewGroup): Bubble {
        activityScenario.onActivity { container.addView(bubbleBarExpandedView) }
        // Mark taskView's visible
        bubbleBarExpandedView!!.onContentVisibilityChanged(true)
        return this
    }

    private fun BubbleOverflow.initialize(container: ViewGroup): BubbleOverflow {
        activityScenario.onActivity { container.addView(bubbleBarExpandedView) }
        return this
    }

    class TestActivity : Activity() {
        lateinit var container: FrameLayout

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            container = FrameLayout(applicationContext)
            container.layoutParams = LayoutParams(50, 50)
            setContentView(container)
        }
    }
}
