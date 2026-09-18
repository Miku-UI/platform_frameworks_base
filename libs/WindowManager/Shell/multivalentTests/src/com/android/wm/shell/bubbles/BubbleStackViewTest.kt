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

package com.android.wm.shell.bubbles

import android.animation.AnimatorTestRule
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.SurfaceControl
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.internal.logging.InstanceIdSequence
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.internal.protolog.ProtoLog
import com.android.launcher3.icons.BubbleIconFactory
import com.android.wm.shell.Flags
import com.android.wm.shell.R
import com.android.wm.shell.bubbles.BubbleStackView.SurfaceSynchronizer
import com.android.wm.shell.bubbles.Bubbles.BubbleExpandListener
import com.android.wm.shell.bubbles.Bubbles.SysuiProxy
import com.android.wm.shell.bubbles.animation.AnimatableScaleMatrix
import com.android.wm.shell.bubbles.logging.BubbleLogger
import com.android.wm.shell.bubbles.logging.BubbleSessionTracker
import com.android.wm.shell.bubbles.logging.BubbleSessionTrackerImpl
import com.android.wm.shell.bubbles.transitions.BubbleTransitions
import com.android.wm.shell.bubbles.user.data.FakeBubbleUserResolver
import com.android.wm.shell.common.FloatingContentCoordinator
import com.android.wm.shell.common.TestShellExecutor
import com.android.wm.shell.shared.animation.PhysicsAnimatorTestUtils
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify

/**
 * Unit tests for [BubbleStackView].
 *
 * Build/Install/Run:
 * - atest WMShellMultivalentTestsOnDevice:BubbleStackViewTest (on device)
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleStackViewTest {

    @get:Rule val setFlagsRule = SetFlagsRule()
    @get:Rule val animatorTestRule: AnimatorTestRule = AnimatorTestRule(this)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val uiEventLoggerFake = UiEventLoggerFake()

    private lateinit var positioner: BubblePositioner
    private lateinit var bubbleLogger: BubbleLogger
    private lateinit var iconFactory: BubbleIconFactory
    private lateinit var expandedViewManager: FakeBubbleExpandedViewManager
    private lateinit var bubbleStackView: BubbleStackView
    private lateinit var shellExecutor: TestShellExecutor
    private lateinit var windowManager: WindowManager
    private lateinit var bubbleTaskViewFactory: BubbleTaskViewFactory
    private lateinit var bubbleData: BubbleData
    private lateinit var bubbleStackViewManager: FakeBubbleStackViewManager
    private lateinit var surfaceSynchronizer: FakeSurfaceSynchronizer
    private lateinit var sessionTracker: BubbleSessionTracker
    private lateinit var bubbleViewInfoTaskFactory: FakeBubbleViewInfoTaskFactory

    private val sysuiProxy = mock<SysuiProxy>()

    @Before
    fun setUp() {
        PhysicsAnimatorTestUtils.prepareForTest()
        // Disable protolog tool when running the tests from studio
        ProtoLog.REQUIRE_PROTOLOGTOOL = false
        shellExecutor = TestShellExecutor()
        windowManager = context.getSystemService(WindowManager::class.java)!!
        iconFactory =
            BubbleIconFactory(
                context,
                context.resources.getDimensionPixelSize(R.dimen.bubble_size),
                context.resources.getDimensionPixelSize(R.dimen.bubble_badge_size),
                Color.BLACK,
                context.resources.getDimensionPixelSize(
                    com.android.internal.R.dimen.importance_ring_stroke_width
                ),
            )
        positioner = BubblePositioner(context, windowManager)
        bubbleLogger = BubbleLogger(uiEventLoggerFake)
        val instanceIdSequence = InstanceIdSequence(/* instanceIdMax= */ 10)
        sessionTracker = BubbleSessionTrackerImpl(instanceIdSequence, bubbleLogger)
        bubbleData =
            BubbleData(
                context,
                bubbleLogger,
                positioner,
                BubbleEducationController(context),
                FakeBubbleAppInfoProvider(),
                shellExecutor,
                shellExecutor,
            )
        bubbleStackViewManager = FakeBubbleStackViewManager()
        expandedViewManager = FakeBubbleExpandedViewManager()
        bubbleTaskViewFactory = FakeBubbleTaskViewFactory(context, shellExecutor)
        surfaceSynchronizer = FakeSurfaceSynchronizer()
        bubbleViewInfoTaskFactory =
            FakeBubbleViewInfoTaskFactory(
                positioner,
                FakeBubbleAppInfoProvider(),
                directExecutor(),
                directExecutor(),
                FakeBubbleUserResolver(),
            )

        bubbleStackView =
            BubbleStackView(
                context,
                bubbleStackViewManager,
                positioner,
                bubbleData,
                surfaceSynchronizer,
                FloatingContentCoordinator(),
                { sysuiProxy },
                shellExecutor,
                sessionTracker,
            )

        context
            .getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(StackEducationView.PREF_STACK_EDUCATION, true)
            .apply()
    }

    @After
    fun tearDown() {
        PhysicsAnimatorTestUtils.tearDown()
    }

    @Test
    fun addBubble() {
        val bubble = createAndInflateBubble()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)
    }

    @Test
    fun tapBubbleToExpand() {
        val bubble = createAndInflateBubble()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
        }

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)
        var lastUpdate: BubbleData.Update? = null
        val semaphore = Semaphore(0)
        val listener =
            BubbleData.Listener { update ->
                lastUpdate = update
                semaphore.release()
            }
        bubbleData.setListener(listener)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubble.iconView!!.performClick()
            // we're checking the expanded state in BubbleData because that's the source of truth.
            // This will eventually propagate an update back to the stack view, but setting the
            // entire pipeline is outside the scope of a unit test.
            assertThat(bubbleData.isExpanded).isTrue()
            shellExecutor.flushAll()
        }

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(lastUpdate).isNotNull()
        assertThat(lastUpdate!!.expandedChanged).isTrue()
        assertThat(lastUpdate!!.expanded).isTrue()
    }

    @Test
    fun tapBubble_bubbleInvalidOnSmallScreens_shouldMoveToFullscreen() {
        val bubble = createAndInflateBubble()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
        }

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)
        bubble.setIsTaskValidToBubbleOnSmallScreen(false)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubble.iconView!!.performClick()
            assertThat(bubbleData.isExpanded).isFalse()
            shellExecutor.flushAll()
        }

        verify(bubble.taskView).moveToFullscreen()
    }

    @Test
    fun expandStack_imeHidden() {
        val bubble = createAndInflateBubble()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
            bubbleStackView.setSelectedBubble(bubble)
        }

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)

        positioner.setImeVisible(false, 0)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // simulate a request from the bubble data listener to expand the stack
            bubbleStackView.isExpanded = true
            verify(sysuiProxy).onStackExpandChanged(true)
            shellExecutor.flushAll()
        }

        assertThat(bubbleStackViewManager.onImeHidden).isNull()
    }

    @Test
    fun collapseStack_imeHidden() {
        val bubble = createAndInflateBubble()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
            bubbleStackView.setSelectedBubble(bubble)
        }

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)

        positioner.setImeVisible(false, 0)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // simulate a request from the bubble data listener to expand the stack
            bubbleStackView.isExpanded = true
            verify(sysuiProxy).onStackExpandChanged(true)
            shellExecutor.flushAll()
        }

        assertThat(bubbleStackViewManager.onImeHidden).isNull()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // simulate a request from the bubble data listener to collapse the stack
            bubbleStackView.isExpanded = false
            verify(sysuiProxy).onStackExpandChanged(false)
            shellExecutor.flushAll()
        }

        assertThat(bubbleStackViewManager.onImeHidden).isNull()
    }

    @Test
    fun expandStack_sysUiProxyNotifiedImmediately() {
        val bubble = createAndInflateBubble()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
            bubbleStackView.setSelectedBubble(bubble)
        }

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)

        positioner.setImeVisible(false, 0)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // simulate a request from the bubble data listener to expand the stack
            bubbleStackView.isExpanded = true
        }

        verify(sysuiProxy).onStackExpandChanged(true)
    }

    @Test
    fun collapseStack_sysUiProxyNotifiedImmediately() {
        val bubble = createAndInflateBubble()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
            bubbleStackView.setSelectedBubble(bubble)
        }

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)

        positioner.setImeVisible(false, 0)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // simulate a request from the bubble data listener to expand the stack
            bubbleData.isExpanded = true
            bubbleStackView.isExpanded = true
        }

        verify(sysuiProxy).onStackExpandChanged(true)

        positioner.setImeVisible(true, 100)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // simulate a request from the bubble data listener to collapse the stack
            bubbleData.isExpanded = false
            bubbleStackView.isExpanded = false
        }

        verify(sysuiProxy).onStackExpandChanged(false)
    }

    @Test
    fun expandStack_clearsImeRunnable() {
        val bubble = createAndInflateBubble()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
            bubbleStackView.setSelectedBubble(bubble)
        }

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)

        // Set up a pending runnable to be cleared
        bubbleStackViewManager.onImeHidden = Runnable {
            fail("IME runnable should not be called when IME is hidden")
        }

        positioner.setImeVisible(false, 0)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // simulate a request from the bubble data listener to expand the stack
            bubbleStackView.isExpanded = true
            verify(sysuiProxy).onStackExpandChanged(true)
            shellExecutor.flushAll()
        }

        // Ime runnable is reset
        assertThat(bubbleStackViewManager.onImeHidden).isNull()
    }

    @Test
    fun collapseStack_clearsImeRunnable() {
        val bubble = createAndInflateBubble()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
            bubbleStackView.setSelectedBubble(bubble)
        }

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)

        positioner.setImeVisible(false, 0)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // simulate a request from the bubble data listener to expand the stack
            bubbleData.isExpanded = true
            bubbleStackView.isExpanded = true
            verify(sysuiProxy).onStackExpandChanged(true)
            shellExecutor.flushAll()
        }

        // Set up a pending runnable to be cleared
        bubbleStackViewManager.onImeHidden = Runnable {
            fail("IME runnable should not be called when IME is hidden")
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // simulate a request from the bubble data listener to collapse the stack
            bubbleData.isExpanded = false
            bubbleStackView.isExpanded = false
            verify(sysuiProxy).onStackExpandChanged(false)
            shellExecutor.flushAll()
        }

        // Check that the runnable is cleared
        assertThat(bubbleStackViewManager.onImeHidden).isNull()
    }

    @Test
    fun tapDifferentBubble_shouldReorder() {
        surfaceSynchronizer.isActive = false
        val bubble1 = createAndInflateChatBubble(key = "bubble1")
        val bubble2 = createAndInflateChatBubble(key = "bubble2")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble1)
            bubbleStackView.addBubble(bubble2)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertThat(bubbleStackView.bubbleCount).isEqualTo(2)
        assertThat(bubbleData.bubbles).hasSize(2)
        assertThat(bubbleData.selectedBubble).isEqualTo(bubble2)
        assertThat(bubble2.iconView).isNotNull()

        var lastUpdate: BubbleData.Update? = null
        val semaphore = Semaphore(0)
        val listener =
            BubbleData.Listener { update ->
                lastUpdate = update
                semaphore.release()
            }
        bubbleData.setListener(listener)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubble2.iconView!!.performClick()
            assertThat(bubbleData.isExpanded).isTrue()

            bubbleStackView.setSelectedBubble(bubble2)
            bubbleStackView.isExpanded = true
            shellExecutor.flushAll()
        }

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(lastUpdate!!.expanded).isTrue()
        assertThat(lastUpdate!!.bubbles.map { it.key })
            .containsExactly("bubble2", "bubble1")
            .inOrder()

        // wait for idle to allow the animation to start
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // wait for the expansion animation to complete before interacting with the bubbles
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(
            AnimatableScaleMatrix.SCALE_X,
            AnimatableScaleMatrix.SCALE_Y,
        )

        // tap on bubble1 to select it
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubble1.iconView!!.performClick()
            shellExecutor.flushAll()
        }
        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(bubbleData.selectedBubble).isEqualTo(bubble1)

        // tap on bubble1 again to collapse the stack
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // we have to set the selected bubble in the stack view manually because we don't have a
            // listener wired up.
            bubbleStackView.setSelectedBubble(bubble1)
            bubble1.iconView!!.performClick()
            shellExecutor.flushAll()
        }

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(bubbleData.selectedBubble).isEqualTo(bubble1)
        assertThat(bubbleData.isExpanded).isFalse()
        assertThat(lastUpdate!!.orderChanged).isTrue()
        assertThat(lastUpdate!!.bubbles.map { it.key })
            .containsExactly("bubble1", "bubble2")
            .inOrder()
    }

    @Test
    fun tapDifferentBubble_imeVisible_flagEnabled_updatesImmediately() {
        val bubble1 = createAndInflateChatBubble(key = "bubble1")
        val bubble2 = createAndInflateChatBubble(key = "bubble2")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble1)
            bubbleStackView.addBubble(bubble2)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertThat(bubbleStackView.bubbleCount).isEqualTo(2)
        assertThat(bubbleData.bubbles).hasSize(2)
        assertThat(bubbleData.selectedBubble).isEqualTo(bubble2)
        assertThat(bubble2.iconView).isNotNull()

        val expandListener = FakeBubbleExpandListener()
        bubbleStackView.setExpandListener(expandListener)

        var lastUpdate: BubbleData.Update? = null
        val semaphore = Semaphore(0)
        val listener =
            BubbleData.Listener { update ->
                lastUpdate = update
                semaphore.release()
            }
        bubbleData.setListener(listener)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubble2.iconView!!.performClick()
            assertThat(bubbleData.isExpanded).isTrue()

            bubbleStackView.setSelectedBubble(bubble2)
            bubbleStackView.isExpanded = true
            shellExecutor.flushAll()
        }

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(lastUpdate!!.expanded).isTrue()
        assertThat(lastUpdate!!.bubbles.map { it.key })
            .containsExactly("bubble2", "bubble1")
            .inOrder()

        // wait for idle to allow the animation to start
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // wait for the expansion animation to complete before interacting with the bubbles
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(
            AnimatableScaleMatrix.SCALE_X,
            AnimatableScaleMatrix.SCALE_Y,
        )

        // make the IME visible and tap on bubble1 to select it
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            positioner.setImeVisible(true, 100)
            bubble1.iconView!!.performClick()
            // we have to set the selected bubble in the stack view manually because we don't have a
            // listener wired up.
            bubbleStackView.setSelectedBubble(bubble1)
            shellExecutor.flushAll()
        }

        val onImeHidden = bubbleStackViewManager.onImeHidden
        assertThat(onImeHidden).isNull()

        assertThat(expandListener.bubblesExpandedState)
            .isEqualTo(mapOf("bubble1" to true, "bubble2" to false))
        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(bubbleData.selectedBubble).isEqualTo(bubble1)
    }

    @Test
    fun tapDifferentBubble_imeHidden_updatesImmediately() {
        val bubble1 = createAndInflateChatBubble(key = "bubble1")
        val bubble2 = createAndInflateChatBubble(key = "bubble2")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble1)
            bubbleStackView.addBubble(bubble2)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertThat(bubbleStackView.bubbleCount).isEqualTo(2)
        assertThat(bubbleData.bubbles).hasSize(2)
        assertThat(bubbleData.selectedBubble).isEqualTo(bubble2)
        assertThat(bubble2.iconView).isNotNull()

        val expandListener = FakeBubbleExpandListener()
        bubbleStackView.setExpandListener(expandListener)

        var lastUpdate: BubbleData.Update? = null
        val semaphore = Semaphore(0)
        val listener =
            BubbleData.Listener { update ->
                lastUpdate = update
                semaphore.release()
            }
        bubbleData.setListener(listener)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubble2.iconView!!.performClick()
            assertThat(bubbleData.isExpanded).isTrue()

            bubbleStackView.setSelectedBubble(bubble2)
            bubbleStackView.isExpanded = true
            shellExecutor.flushAll()
        }

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(lastUpdate!!.expanded).isTrue()
        assertThat(lastUpdate!!.bubbles.map { it.key })
            .containsExactly("bubble2", "bubble1")
            .inOrder()

        // wait for idle to allow the animation to start
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // wait for the expansion animation to complete before interacting with the bubbles
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(
            AnimatableScaleMatrix.SCALE_X,
            AnimatableScaleMatrix.SCALE_Y,
        )

        // make the IME hidden and tap on bubble1 to select it
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            positioner.setImeVisible(false, 0)
            bubble1.iconView!!.performClick()
            // we have to set the selected bubble in the stack view manually because we don't have a
            // listener wired up.
            bubbleStackView.setSelectedBubble(bubble1)
            shellExecutor.flushAll()
        }

        val onImeHidden = bubbleStackViewManager.onImeHidden
        assertThat(onImeHidden).isNull()

        assertThat(expandListener.bubblesExpandedState)
            .isEqualTo(mapOf("bubble1" to true, "bubble2" to false))
        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(bubbleData.selectedBubble).isEqualTo(bubble1)
    }

    @EnableFlags(Flags.FLAG_ENABLE_OPTIONAL_BUBBLE_OVERFLOW)
    @Test
    fun testCreateStackView_noOverflowContents_noOverflow() {
        bubbleStackView =
            BubbleStackView(
                context,
                bubbleStackViewManager,
                positioner,
                bubbleData,
                null,
                FloatingContentCoordinator(),
                { sysuiProxy },
                shellExecutor,
                sessionTracker,
            )

        assertThat(bubbleData.overflowBubbles).isEmpty()
        val bubbleOverflow = bubbleData.overflow
        // Overflow shouldn't be attached
        assertThat(bubbleStackView.getBubbleIndex(bubbleOverflow)).isEqualTo(-1)
    }

    @EnableFlags(Flags.FLAG_ENABLE_OPTIONAL_BUBBLE_OVERFLOW)
    @Test
    fun testCreateStackView_hasOverflowContents_hasOverflow() {
        // Add a bubble to the overflow
        val bubble1 = createAndInflateChatBubble(key = "bubble1")
        bubbleData.notificationEntryUpdated(bubble1, false, false)
        bubbleData.dismissBubbleWithKey(bubble1.key, Bubbles.DISMISS_USER_GESTURE)
        assertThat(bubbleData.overflowBubbles).isNotEmpty()

        bubbleStackView =
            BubbleStackView(
                context,
                bubbleStackViewManager,
                positioner,
                bubbleData,
                null,
                FloatingContentCoordinator(),
                { sysuiProxy },
                shellExecutor,
                sessionTracker,
            )
        val bubbleOverflow = bubbleData.overflow
        assertThat(bubbleStackView.getBubbleIndex(bubbleOverflow)).isGreaterThan(-1)
    }

    @DisableFlags(Flags.FLAG_ENABLE_OPTIONAL_BUBBLE_OVERFLOW)
    @Test
    fun testCreateStackView_noOverflowContents_hasOverflow() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView =
                BubbleStackView(
                    context,
                    bubbleStackViewManager,
                    positioner,
                    bubbleData,
                    null,
                    FloatingContentCoordinator(),
                    { sysuiProxy },
                    shellExecutor,
                    sessionTracker,
                )
        }
        assertThat(bubbleData.overflowBubbles).isEmpty()
        val bubbleOverflow = bubbleData.overflow
        assertThat(bubbleStackView.getBubbleIndex(bubbleOverflow)).isGreaterThan(-1)
    }

    @EnableFlags(Flags.FLAG_ENABLE_OPTIONAL_BUBBLE_OVERFLOW)
    @Test
    fun showOverflow_true() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.showOverflow(true)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val bubbleOverflow = bubbleData.overflow
        assertThat(bubbleStackView.getBubbleIndex(bubbleOverflow)).isGreaterThan(-1)
    }

    @EnableFlags(Flags.FLAG_ENABLE_OPTIONAL_BUBBLE_OVERFLOW)
    @Test
    fun showOverflow_false() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.showOverflow(true)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val bubbleOverflow = bubbleData.overflow
        assertThat(bubbleStackView.getBubbleIndex(bubbleOverflow)).isGreaterThan(-1)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.showOverflow(false)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // The overflow should've been removed
        assertThat(bubbleStackView.getBubbleIndex(bubbleOverflow)).isEqualTo(-1)
    }

    @DisableFlags(Flags.FLAG_ENABLE_OPTIONAL_BUBBLE_OVERFLOW)
    @Test
    fun showOverflow_ignored() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.showOverflow(false)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // showOverflow should've been ignored, so the overflow would be attached
        val bubbleOverflow = bubbleData.overflow
        assertThat(bubbleStackView.getBubbleIndex(bubbleOverflow)).isGreaterThan(-1)
    }

    @Test
    fun removeFromWindow_stopMonitoringSwipeUpGesture() {
        bubbleStackView = spy(bubbleStackView)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // No way to add to window in the test environment right now so just pretend
            bubbleStackView.onDetachedFromWindow()
        }
        verify(bubbleStackView).stopMonitoringSwipeUpGesture()
    }

    @Test
    fun animateExpand_expandRunsRunnable() {
        bubbleStackView = spy(bubbleStackView)
        val bubble = createAndInflateChatBubble(key = "bubble")

        assertThat(bubble.expandedView).isNotNull()

        var afterTransitionRan = false
        val semaphore = Semaphore(0)

        // Expand animation runs on a delay so wait for it.
        val runnable = Runnable {
            afterTransitionRan = true
            semaphore.release()
        }

        assertThat(bubbleStackView.isExpanded).isFalse()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
            bubbleStackView.setSelectedBubble(bubble)
            bubbleStackView.animateExpand(null, runnable)
            bubbleData.isExpanded = true
            bubbleStackView.isExpanded = true
            shellExecutor.flushAll()
        }

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(bubbleStackView.isExpanded).isTrue()
        assertThat(afterTransitionRan).isTrue()
    }

    @Test
    fun animateExpand_switchRunsRunnable() {
        bubbleStackView = spy(bubbleStackView)
        val bubble = createAndInflateChatBubble(key = "bubble")
        val bubble2 = createAndInflateChatBubble(key = "bubble2")

        var afterTransitionRan = false
        val semaphore = Semaphore(0)

        // Expand animation runs on a delay so wait for it.
        val runnable = Runnable {
            afterTransitionRan = true
            semaphore.release()
        }
        assertThat(bubbleStackView.isExpanded).isFalse()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
            bubbleStackView.addBubble(bubble2)
            bubbleStackView.setSelectedBubble(bubble)
            bubbleData.isExpanded = true
            bubbleStackView.isExpanded = true
            shellExecutor.flushAll()
        }

        assertThat(bubbleStackView.isExpanded).isTrue()
        assertThat(bubbleStackView.expandedBubble!!.key).isEqualTo(bubble.key)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.animateExpand(null, runnable)
            bubbleStackView.setSelectedBubble(bubble2)
            shellExecutor.flushAll()
        }

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(bubbleStackView.isExpanded).isTrue()
        assertThat(bubbleStackView.expandedBubble!!.key).isEqualTo(bubble2.key)
        assertThat(afterTransitionRan).isTrue()
    }

    @Test
    fun canExpandView_true_triggersContinueExpand() {
        bubbleStackView = spy(bubbleStackView)
        val bubble = createAndInflateChatBubble(key = "bubble")
        val bubbleTransition = mock<BubbleTransitions.BubbleTransition>()
        bubble.currentTransition = bubbleTransition

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
        }

        assertThat(bubbleStackView.isExpanded).isFalse()
        assertThat(bubbleStackView.canExpandView(bubble)).isTrue()
        verify(bubbleTransition).continueExpand()
    }

    @Test
    fun canExpandView_false() {
        bubbleStackView = spy(bubbleStackView)
        val bubble = createAndInflateChatBubble(key = "bubble")
        val bubbleTransition = mock<BubbleTransitions.BubbleTransition>()
        bubble.currentTransition = bubbleTransition

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
            bubbleStackView.setSelectedBubble(bubble)
            bubbleData.isExpanded = true
            bubbleStackView.isExpanded = true
            shellExecutor.flushAll()
        }

        assertThat(bubbleStackView.isExpanded).isTrue()
        assertThat(bubbleStackView.expandedBubble!!.key).isEqualTo(bubble.key)
        assertThat(bubbleStackView.canExpandView(bubble)).isFalse()
        verify(bubbleTransition, never()).continueExpand()
    }

    @Test
    fun snapToExpanded() {
        val bubble = createAndInflateChatBubble(key = "bubble")

        assertThat(bubble.expandedView).isNotNull()
        assertThat(bubbleStackView.isExpanded).isFalse()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
            bubbleStackView.setSelectedBubble(bubble)
            bubbleData.isExpanded = true
            bubbleStackView.snapToExpanded()
            bubbleStackView.isExpanded = true
            shellExecutor.flushAll()
        }

        assertThat(bubble.taskView.alpha).isEqualTo(1)
        val expandedViewContainer = bubble.expandedView!!.parent as ViewGroup
        assertThat(expandedViewContainer.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun removeBubble_notExpanded() {
        val bubble1 = createAndInflateChatBubble("key1")
        val bubble2 = createAndInflateChatBubble("key2")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble1)
            bubbleStackView.addBubble(bubble2)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertThat(bubbleStackView.bubbleCount).isEqualTo(2)
        assertThat(bubble1.expandedView).isNotNull()
        assertThat(bubble1.iconView).isNotNull()
        assertThat(bubble2.expandedView).isNotNull()
        assertThat(bubble2.iconView).isNotNull()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // dismiss it in data so it's in the overflow
            bubbleData.dismissBubbleWithKey(bubble1.key, Bubbles.DISMISS_USER_GESTURE)
            // remove it from the stack
            bubbleStackView.removeBubble(bubble1)
            shellExecutor.flushAll()
        }

        // Check that proper changes to removed bubble happened
        assertThat(bubble1.expandedView).isNull()
        // still have bubbles + this was overflowed so should have icon view
        assertThat(bubble1.iconView).isNotNull()

        // And the bubble that is still in the stack is not affected
        assertThat(bubble2.expandedView).isNotNull()
        assertThat(bubble2.iconView).isNotNull()

        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)
    }

    @Test
    fun removeBubble_notExpanded_notOverflowed() {
        val bubble1 = createAndInflateChatBubble("key1")
        val bubble2 = createAndInflateChatBubble("key2")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble1)
            bubbleStackView.addBubble(bubble2)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertThat(bubbleStackView.bubbleCount).isEqualTo(2)
        assertThat(bubble1.expandedView).isNotNull()
        assertThat(bubble1.iconView).isNotNull()
        assertThat(bubble2.expandedView).isNotNull()
        assertThat(bubble2.iconView).isNotNull()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // remove it from stack + data, don't overflow it
            bubbleData.dismissBubbleWithKey(bubble1.key, Bubbles.DISMISS_NO_LONGER_BUBBLE)
            // remove it from the stack
            bubbleStackView.removeBubble(bubble1)
            shellExecutor.flushAll()
        }

        // Check that proper changes to removed bubble happened
        assertThat(bubble1.expandedView).isNull()
        assertThat(bubble1.iconView).isNull() // not in overflow so no icon

        // And the bubble that is still in the stack is not affected
        assertThat(bubble2.expandedView).isNotNull()
        assertThat(bubble2.iconView).isNotNull()

        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)
    }

    @Test
    fun removeLastBubble_notExpanded() {
        val bubble = createAndInflateBubble()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)
        assertThat(bubble.expandedView).isNotNull()
        assertThat(bubble.iconView).isNotNull()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // remove it from the stack + data
            bubbleData.dismissBubbleWithKey(bubble.key, Bubbles.DISMISS_USER_GESTURE)
            bubbleStackView.removeBubble(bubble)
            shellExecutor.flushAll()
        }

        // Last bubble removed, so everything is null
        assertThat(bubble.expandedView).isNull()
        assertThat(bubble.iconView).isNull()

        assertThat(bubbleStackView.bubbleCount).isEqualTo(0)
    }

    @Test
    fun removeBubble_whileExpanded() {
        val bubble1 = createAndInflateChatBubble("key1")
        val bubble2 = createAndInflateChatBubble("key2")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble1)
            bubbleStackView.addBubble(bubble2)
            bubbleStackView.setSelectedBubble(bubble2)
            bubbleData.isExpanded = true
            bubbleStackView.isExpanded = true
            shellExecutor.flushAll()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertThat(bubbleStackView.isExpanded).isTrue()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(2)
        assertThat(bubble1.expandedView).isNotNull()
        assertThat(bubble1.iconView).isNotNull()
        assertThat(bubble2.expandedView).isNotNull()
        assertThat(bubble2.iconView).isNotNull()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // remove it from the stack + data
            bubbleData.dismissBubbleWithKey(bubble2.key, Bubbles.DISMISS_USER_GESTURE)
            bubbleStackView.removeBubble(bubble2)
            // stack would also be told to select the next bubble
            bubbleStackView.setSelectedBubble(bubble1)
            shellExecutor.flushAll()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // Check that proper changes to removed bubble happened
        assertThat(bubble2.expandedView).isNull()
        // still have bubbles + this was overflowed so should have icon view
        assertThat(bubble2.iconView).isNotNull()

        assertThat(bubble1.expandedView).isNotNull()
        assertThat(bubble1.iconView).isNotNull()

        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)
        assertThat(bubbleStackView.isExpanded).isTrue()
    }

    @Test
    fun removeBubble_whileExpanded_notOverflowed() {
        val bubble1 = createAndInflateChatBubble("key1")
        val bubble2 = createAndInflateChatBubble("key2")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble1)
            bubbleStackView.addBubble(bubble2)
            bubbleStackView.setSelectedBubble(bubble2)
            bubbleData.isExpanded = true
            bubbleStackView.isExpanded = true
            shellExecutor.flushAll()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertThat(bubbleStackView.isExpanded).isTrue()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(2)
        assertThat(bubble1.expandedView).isNotNull()
        assertThat(bubble1.iconView).isNotNull()
        assertThat(bubble2.expandedView).isNotNull()
        assertThat(bubble2.iconView).isNotNull()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // remove it from the stack + data; don't overflow it
            bubbleData.dismissBubbleWithKey(bubble2.key, Bubbles.DISMISS_NO_LONGER_BUBBLE)
            bubbleStackView.removeBubble(bubble2)
            // stack would also be told to select the next bubble
            bubbleStackView.setSelectedBubble(bubble1)
            shellExecutor.flushAll()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // Check that proper changes to removed bubble happened
        assertThat(bubble2.expandedView).isNull()
        assertThat(bubble2.iconView).isNull() // not in overflow so null

        assertThat(bubble1.expandedView).isNotNull()
        assertThat(bubble1.iconView).isNotNull()

        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)
        assertThat(bubbleStackView.isExpanded).isTrue()
    }

    @Test
    fun removeLastBubble_whileExpanded() {
        val bubble = createAndInflateBubble()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
            bubbleStackView.setSelectedBubble(bubble)
            bubbleData.isExpanded = true
            bubbleStackView.isExpanded = true
            shellExecutor.flushAll()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertThat(bubbleStackView.isExpanded).isTrue()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)
        assertThat(bubble.expandedView).isNotNull()
        assertThat(bubble.iconView).isNotNull()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // remove it from the stack + data
            bubbleData.dismissBubbleWithKey(bubble.key, Bubbles.DISMISS_USER_GESTURE)
            bubbleStackView.removeBubble(bubble)
            // stack would also be told to collapse when last bubble removed
            bubbleStackView.setExpanded(false)
            // Run the scrim animation
            animatorTestRule.advanceTimeBy(300)
            shellExecutor.flushAll()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // Last bubble removed, so everything is null
        assertThat(bubble.expandedView).isNull()
        assertThat(bubble.iconView).isNull()

        assertThat(bubbleStackView.bubbleCount).isEqualTo(0)
    }

    @Test
    fun removeLastBubble_whileExpanded_addBack() {
        val bubble = createAndInflateBubble()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
            bubbleStackView.setSelectedBubble(bubble)
            bubbleData.isExpanded = true
            bubbleStackView.isExpanded = true
            shellExecutor.flushAll()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertThat(bubbleStackView.isExpanded).isTrue()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)
        assertThat(bubble.expandedView).isNotNull()
        assertThat(bubble.iconView).isNotNull()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // remove it from the data + stack
            bubbleData.dismissBubbleWithKey(bubble.key, Bubbles.DISMISS_USER_GESTURE)
            bubbleStackView.removeBubble(bubble)
            // typically stack would also be told to collapse when last bubble removed
            bubbleStackView.setExpanded(false)
            // Start the scrim animation
            animatorTestRule.advanceTimeBy(100)
            // Add the same bubble back
            bubbleData.notificationEntryUpdated(bubble, false, true)
            bubbleStackView.addBubble(bubble)
            bubbleStackView.setSelectedBubble(bubble)
            // let the scrim animation finish
            animatorTestRule.advanceTimeBy(300)
            shellExecutor.flushAll()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // Check that proper changes to removed bubble happened
        assertThat(bubble.expandedView).isNotNull()
        assertThat(bubble.expandedView!!.contentAlpha).isEqualTo(1)
        assertThat(bubble.expandedView!!.alpha).isEqualTo(1)
        assertThat(bubble.iconView).isNotNull()
        assertThat(bubble.iconView!!.alpha).isEqualTo(1)
        assertThat(bubble.iconView!!.scaleX).isEqualTo(1)
        assertThat(bubble.iconView!!.scaleY).isEqualTo(1)
        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)
    }

    @Test
    fun removeJumpcutSwitchClosingBubble() {
        val closingBubble = createAndInflateBubble()
        val openingBubble = createAndInflateBubble()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(closingBubble)
            bubbleStackView.addBubble(openingBubble)
            bubbleStackView.setSelectedBubble(openingBubble)
            bubbleStackView.hideJumpcutClosingBubble(closingBubble)
            bubbleData.isExpanded = true
            bubbleStackView.isExpanded = true
            shellExecutor.flushAll()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertThat(bubbleStackView.isExpanded).isTrue()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)
        assertThat(closingBubble.expandedView).isNotNull()
        assertThat(closingBubble.iconView).isNotNull()
        assertThat(bubbleStackView.getBubbleIndex(closingBubble)).isEqualTo(-1)
        assertThat(openingBubble.expandedView).isNotNull()
        assertThat(openingBubble.iconView).isNotNull()
        assertThat(bubbleStackView.getBubbleIndex(openingBubble)).isEqualTo(0)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // remove it from the data + stack
            bubbleData.dismissBubbleWithKey(closingBubble.key, Bubbles.DISMISS_USER_GESTURE)
            bubbleStackView.removeBubble(closingBubble)
            // Start the scrim animation
            animatorTestRule.advanceTimeBy(100)
            shellExecutor.flushAll()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // Check that proper changes to removed bubble happened
        assertThat(bubbleStackView.isExpanded).isTrue()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)
        assertThat(closingBubble.expandedView).isNull()
        assertThat(closingBubble.iconView).isNull()
        assertThat(bubbleStackView.getBubbleIndex(closingBubble)).isEqualTo(-1)
        assertThat(openingBubble.expandedView).isNotNull()
        assertThat(openingBubble.iconView).isNotNull()
        assertThat(bubbleStackView.getBubbleIndex(openingBubble)).isEqualTo(0)
    }

    @Test
    fun sessionEventsLogged() {
        val bubble1 = createAndInflateChatBubble("key1")
        val bubble2 = createAndInflateChatBubble("key2")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble1)
            bubbleStackView.addBubble(bubble2)
            bubbleStackView.setSelectedBubble(bubble2)
            bubbleData.isExpanded = true
            bubbleStackView.isExpanded = true
            shellExecutor.flushAll()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val sessionStartEvent =
            uiEventLoggerFake.logs.single {
                it.eventId == BubbleLogger.Event.BUBBLE_SESSION_STARTED.id
            }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.setSelectedBubble(bubble1)
            shellExecutor.flushAll()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val sessionSwitchedFromEvent =
            uiEventLoggerFake.logs.single {
                it.eventId == BubbleLogger.Event.BUBBLE_SESSION_SWITCHED_FROM.id
            }
        val sessionSwitchedToEvent =
            uiEventLoggerFake.logs.single {
                it.eventId == BubbleLogger.Event.BUBBLE_SESSION_SWITCHED_TO.id
            }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleData.isExpanded = false
            bubbleStackView.isExpanded = false
            shellExecutor.flushAll()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val sessionEndEvent =
            uiEventLoggerFake.logs.single {
                it.eventId == BubbleLogger.Event.BUBBLE_SESSION_ENDED.id
            }

        val sessionInstanceIds =
            setOf(
                sessionStartEvent.instanceId,
                sessionSwitchedFromEvent.instanceId,
                sessionSwitchedToEvent.instanceId,
                sessionEndEvent.instanceId,
            )
        assertThat(sessionInstanceIds).hasSize(1)
    }

    @Test
    fun updateBubbleOrder_expanded_removedBubbleNotInStack_isNotReAdded() {
        // 1. Setup: expanded stack with two bubbles.
        val bubble1 = createAndInflateChatBubble(key = "bubble1")
        val bubble2 = createAndInflateChatBubble(key = "bubble2")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble1) // bubble1 at index 0
            bubbleStackView.addBubble(bubble2) // bubble2 at index 0, bubble1 at index 1
            bubbleStackView.setSelectedBubble(bubble2)
            bubbleData.isExpanded = true
            bubbleStackView.isExpanded = true
            shellExecutor.flushAll()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertThat(bubbleStackView.isExpanded).isTrue()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(2)
        // Initial order in container: [bubble2.iconView, bubble1.iconView]

        // 2. Simulate a bubble view being removed from the container before reorder,
        // like in a jumpcut switch.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.hideJumpcutClosingBubble(bubble2)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertThat(bubbleStackView.getBubbleIndex(bubble2)).isEqualTo(-1)
        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)

        // 3. Trigger reorder. The bubble list from BubbleData still contains both bubbles.
        val currentOrderInBubbleData = listOf(bubble2, bubble1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.updateBubbleOrder(currentOrderInBubbleData, false)
            shellExecutor.flushAll()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // 4. Verify. With the fix, the removed bubble (bubble2) should not be re-added.
        // Only bubble1 should remain, at index 0.
        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)
        assertThat(bubbleStackView.getBubbleIndex(bubble1)).isEqualTo(0)
        assertThat(bubbleStackView.getBubbleIndex(bubble2)).isEqualTo(-1)
    }

    @Test
    fun updateBubbleOrder_collapsed_listModifiedDuringAnimation() {
        // 1. Setup: Add two bubbles to the collapsed stack.
        val bubble1 = createAndInflateChatBubble(key = "bubble1")
        val bubble2 = createAndInflateChatBubble(key = "bubble2")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble1) // bubble1 at index 0
            bubbleStackView.addBubble(bubble2) // bubble2 at index 0, bubble1 at index 1
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(2)
        // Initial order in container: [bubble2.iconView, bubble1.iconView]

        // 2. Trigger reorder. New desired order is [bubble1, bubble2]
        val newOrder = listOf(bubble1, bubble2)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.updateBubbleOrder(newOrder, false)
            // The animation starts here. Let's advance time a bit to be in the middle of it.
            animatorTestRule.advanceTimeBy(100)

            // 3. Modify the list during animation: remove bubble1
            bubbleData.dismissBubbleWithKey(bubble1.key, Bubbles.DISMISS_NO_LONGER_BUBBLE)
            bubbleStackView.removeBubble(bubble1)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)
        assertThat(bubbleStackView.getBubbleIndex(bubble1)).isEqualTo(-1)
        assertThat(bubbleStackView.getBubbleIndex(bubble2)).isEqualTo(0)

        // 4. Let the animation finish, which will trigger the reorder runnable
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(500) // Ensure animation is done
            shellExecutor.flushAll()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // 5. Verify the final state. The stack should not have crashed, and the removed
        // bubble should not have been re-added by the reorder runnable.
        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)
        assertThat(bubbleStackView.getBubbleIndex(bubble2)).isEqualTo(0)
        assertThat(bubbleStackView.getBubbleIndex(bubble1)).isEqualTo(-1)
    }

    @Test
    fun animateConvert_expandAnimationRunning_cancelExpand() {
        bubbleStackView = spy(bubbleStackView)
        val bubble = createAndInflateBubble()

        assertThat(bubble.expandedView).isNotNull()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
            bubbleStackView.setSelectedBubble(bubble)
            bubbleData.isExpanded = true
            bubbleStackView.isExpanded = true
            shellExecutor.flushAll()
            animatorTestRule.advanceTimeBy(100)
        }

        assertThat(bubbleStackView.isExpansionAnimating).isTrue()

        var finishCalled = false
        val finishRunnable = Runnable { finishCalled = true }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val startT = SurfaceControl.Transaction()
            val startBounds = Rect(0, 0, 100, 100)
            val snapshot = SurfaceControl.Builder().setName("snapshot").build()
            val taskLeash = SurfaceControl.Builder().setName("taskLeash").build()
            bubbleStackView.animateConvert(
                startT,
                startBounds,
                1f /* startScale */,
                snapshot,
                taskLeash,
                finishRunnable,
            )
        }

        assertThat(bubbleStackView.isExpansionAnimating).isFalse()
        assertThat(bubbleStackView.isSwitchAnimating).isTrue()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            animatorTestRule.advanceTimeBy(400)
        }

        assertThat(bubbleStackView.isSwitchAnimating).isFalse()

        assertThat(bubbleStackView.isExpanded).isTrue()
        assertThat(finishCalled).isTrue()
    }

    @Test
    fun dismissBubble_multipleBubblesInStack_cleanupDeferred() {
        val bubble1 = createAndInflateChatBubble("key1")
        val bubble2 = createAndInflateChatBubble("key2")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble1)
            bubbleStackView.addBubble(bubble2)
            bubbleStackView.setSelectedBubble(bubble2)
            bubbleData.isExpanded = true
            bubbleStackView.isExpanded = true
            shellExecutor.flushAll()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // wait for the expansion animation to complete
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(
            AnimatableScaleMatrix.SCALE_X,
            AnimatableScaleMatrix.SCALE_Y,
        )

        assertThat(bubbleStackView.isExpanded).isTrue()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(2)
        assertThat(bubble2.expandedView).isNotNull()
        assertThat(bubble2.iconView).isNotNull()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView
                .findViewById<View>(R.id.bubble_manage_menu_dismiss_container)
                .performClick()
            shellExecutor.flushAll()
        }
        assertThat(bubbleData.hasBubbleInStackWithKey(bubble2.key)).isFalse()
        assertThat(bubbleData.hasOverflowBubbleWithKey(bubble2.key)).isTrue()
        assertThat(bubble2.isCleanupDeferred).isTrue()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // send the request to remove the bubble
            bubbleStackView.removeBubble(bubble2)
        }
        assertThat(bubble2.expandedView).isNotNull()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // send the request to switch to the next bubble
            bubbleStackView.setSelectedBubble(bubble1)
            shellExecutor.flushAll()
        }

        // wait for the switch animation to complete
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(
            AnimatableScaleMatrix.SCALE_X,
            AnimatableScaleMatrix.SCALE_Y,
        )
        // let the animation end runnable execute
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // check that bubble2 was cleaned up
        assertThat(bubble2.isCleanupDeferred).isFalse()
        assertThat(bubble2.expandedView).isNull()
        // bubble2 was overflowed so it should have an icon view
        assertThat(bubble2.iconView).isNotNull()
    }

    @Test
    fun dismissBubble_singleBubbleInStack_cleanupNotDeferred() {
        val bubble1 = createAndInflateChatBubble("key1")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble1)
            bubbleStackView.setSelectedBubble(bubble1)
            bubbleData.isExpanded = true
            bubbleStackView.isExpanded = true
            shellExecutor.flushAll()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // wait for the expansion animation to complete
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(
            AnimatableScaleMatrix.SCALE_X,
            AnimatableScaleMatrix.SCALE_Y,
        )

        assertThat(bubbleStackView.isExpanded).isTrue()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)
        assertThat(bubble1.expandedView).isNotNull()
        assertThat(bubble1.iconView).isNotNull()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView
                .findViewById<View>(R.id.bubble_manage_menu_dismiss_container)
                .performClick()
            shellExecutor.flushAll()
        }
        assertThat(bubbleData.hasBubbleInStackWithKey(bubble1.key)).isFalse()
        assertThat(bubble1.isCleanupDeferred).isFalse()
    }

    @Test
    fun performAccessibilityAction_move_whenExpanded_setsPositionImmediately() {
        // GIVEN an expanded bubble stack view with one bubble
        val bubble = createAndInflateBubble()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
            bubbleStackView.setSelectedBubble(bubble)
            bubbleData.isExpanded = true
            bubbleStackView.isExpanded = true
            shellExecutor.flushAll()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(
            AnimatableScaleMatrix.SCALE_X,
            AnimatableScaleMatrix.SCALE_Y,
        )
        assertThat(bubbleStackView.isExpanded).isTrue()
        val stackBounds = positioner.getAllowableStackPositionRegion(bubbleStackView.bubbleCount)

        // WHEN the move top left action is performed
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.performAccessibilityActionInternal(R.id.action_move_top_left, null)
        }
        // THEN the stack moves immediately
        assertThat(bubbleStackView.stackPosition.x).isEqualTo(stackBounds.left)
        assertThat(bubbleStackView.stackPosition.y).isEqualTo(stackBounds.top)

        // WHEN the move top right action is performed
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.performAccessibilityActionInternal(R.id.action_move_top_right, null)
        }
        // THEN the stack moves immediately
        assertThat(bubbleStackView.stackPosition.x).isEqualTo(stackBounds.right)
        assertThat(bubbleStackView.stackPosition.y).isEqualTo(stackBounds.top)

        // WHEN the move bottom left action is performed
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.performAccessibilityActionInternal(R.id.action_move_bottom_left, null)
        }
        // THEN the stack moves immediately
        assertThat(bubbleStackView.stackPosition.x).isEqualTo(stackBounds.left)
        assertThat(bubbleStackView.stackPosition.y).isEqualTo(stackBounds.bottom)

        // WHEN the move bottom right action is performed
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.performAccessibilityActionInternal(R.id.action_move_bottom_right, null)
        }
        // THEN the stack moves immediately
        assertThat(bubbleStackView.stackPosition.x).isEqualTo(stackBounds.right)
        assertThat(bubbleStackView.stackPosition.y).isEqualTo(stackBounds.bottom)
    }

    @Test
    fun verifyAccessibilityActions_collapsedStack() {
        val bubble = createAndInflateBubble()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
            bubbleStackView.updateBubblesAccessibilityStates()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertThat(bubbleStackView.isExpanded).isFalse()

        val info = AccessibilityNodeInfo()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.onInitializeAccessibilityNodeInfo(info)
        }

        val actionIds = info.actionList.map { it.id }
        assertThat(actionIds)
            .containsAtLeast(
                R.id.action_move_top_left,
                R.id.action_move_top_right,
                R.id.action_move_bottom_left,
                R.id.action_move_bottom_right,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND.id,
            )
        assertThat(actionIds)
            .doesNotContain(AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE.id)
    }

    @Test
    fun verifyAccessibilityActions_expandedStack_selectedBubble() {
        val bubble = createAndInflateBubble()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
            bubbleStackView.setSelectedBubble(bubble)
            bubbleData.isExpanded = true
            bubbleStackView.isExpanded = true
            bubbleStackView.updateBubblesAccessibilityStates()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val info = AccessibilityNodeInfo()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubble.iconView!!.onInitializeAccessibilityNodeInfo(info)
        }

        val actionIds = info.actionList.map { it.id }
        assertThat(actionIds)
            .containsAtLeast(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE.id,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id,
            )
        assertThat(actionIds)
            .doesNotContain(AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND.id)
    }

    @Test
    fun verifyAccessibilityActions_expandedStack_unselectedBubble() {
        val bubble1 = createAndInflateChatBubble("key1")
        val bubble2 = createAndInflateChatBubble("key2")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble1)
            bubbleStackView.addBubble(bubble2)
            bubbleStackView.setSelectedBubble(bubble1)
            bubbleData.isExpanded = true
            bubbleStackView.isExpanded = true
            bubbleStackView.updateBubblesAccessibilityStates()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // bubble2 is unselected
        val info = AccessibilityNodeInfo()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubble2.iconView!!.onInitializeAccessibilityNodeInfo(info)
        }

        val actionIds = info.actionList.map { it.id }
        assertThat(actionIds)
            .containsAtLeast(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND.id,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id,
            )
        assertThat(actionIds)
            .doesNotContain(AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE.id)
    }

    @Test
    fun verifyAccessibilityActions_expandedStack_selectedOverflow() {
        val bubble = createAndInflateBubble()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
            bubbleStackView.showOverflow(true)
            bubbleStackView.setSelectedBubble(bubbleData.overflow)
            bubbleData.isExpanded = true
            bubbleStackView.isExpanded = true
            bubbleStackView.updateBubblesAccessibilityStates()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val info = AccessibilityNodeInfo()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleData.overflow.iconView!!.onInitializeAccessibilityNodeInfo(info)
        }

        val actionIds = info.actionList.map { it.id }
        assertThat(actionIds).contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE.id)
        assertThat(actionIds)
            .doesNotContain(AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND.id)
        assertThat(actionIds)
            .doesNotContain(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id)
    }

    @Test
    fun verifyAccessibilityActions_expandedStack_unselectedOverflow() {
        val bubble = createAndInflateBubble()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
            bubbleStackView.showOverflow(true)
            bubbleStackView.setSelectedBubble(bubble)
            bubbleData.isExpanded = true
            bubbleStackView.isExpanded = true
            bubbleStackView.updateBubblesAccessibilityStates()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val info = AccessibilityNodeInfo()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleData.overflow.iconView!!.onInitializeAccessibilityNodeInfo(info)
        }

        val actionIds = info.actionList.map { it.id }
        assertThat(actionIds).contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND.id)
        assertThat(actionIds)
            .doesNotContain(AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE.id)
        assertThat(actionIds)
            .doesNotContain(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id)
    }

    @Test
    fun performAccessibilityAction_expand_whenCollapsed_expandStack() {
        val bubble = createAndInflateBubble()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
            bubbleStackView.updateBubblesAccessibilityStates()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertThat(bubbleData.isExpanded).isFalse()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.performAccessibilityAction(AccessibilityNodeInfo.ACTION_EXPAND, null)
        }
        assertThat(bubbleData.isExpanded).isTrue()
    }

    @Test
    fun performAccessibilityAction_dismiss_whenCollapsed_dismissStack() {
        val bubble = createAndInflateBubble()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
            bubbleStackView.updateBubblesAccessibilityStates()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertThat(bubbleData.hasBubbles()).isTrue()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.performAccessibilityAction(AccessibilityNodeInfo.ACTION_DISMISS, null)
        }
        assertThat(bubbleData.hasBubbles()).isFalse()
    }

    @Test
    fun performAccessibilityAction_collapseBubble_whenExpanded_collapseStack() {
        val bubble = createAndInflateBubble()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
            bubbleStackView.setSelectedBubble(bubble)
            bubbleData.isExpanded = true
            bubbleStackView.snapToExpanded()
            bubbleStackView.updateBubblesAccessibilityStates()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertThat(bubbleData.isExpanded).isTrue()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubble.iconView!!.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_COLLAPSE,
                null,
            )
        }
        assertThat(bubbleData.isExpanded).isFalse()
    }

    @Test
    fun performAccessibilityAction_expandBubble_onUnselectedBubble_selectsBubble() {
        val bubble1 = createAndInflateChatBubble("key1")
        val bubble2 = createAndInflateChatBubble("key2")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble1)
            bubbleStackView.addBubble(bubble2)
            bubbleStackView.setSelectedBubble(bubble2)
            bubbleData.isExpanded = true
            bubbleStackView.snapToExpanded()
            bubbleStackView.updateBubblesAccessibilityStates()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertThat(bubbleData.selectedBubble).isEqualTo(bubble2)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // bubble1 is unselected, "expand" action selects it.
            bubble1.iconView!!.performAccessibilityAction(AccessibilityNodeInfo.ACTION_EXPAND, null)
        }
        assertThat(bubbleData.selectedBubble).isEqualTo(bubble1)
    }

    @Test
    fun performAccessibilityAction_dismissBubble_dismissesOneBubble() {
        val bubble1 = createAndInflateChatBubble("key1")
        val bubble2 = createAndInflateChatBubble("key2")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble1)
            bubbleStackView.addBubble(bubble2)
            bubbleStackView.setSelectedBubble(bubble2)
            bubbleData.isExpanded = true
            bubbleStackView.snapToExpanded()
            bubbleStackView.updateBubblesAccessibilityStates()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertThat(bubbleData.hasBubbleInStackWithKey("key1")).isTrue()
        assertThat(bubbleData.hasBubbleInStackWithKey("key2")).isTrue()
        assertThat(bubbleStackView.isExpanded).isTrue()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubble1.iconView!!.performAccessibilityAction(
                AccessibilityNodeInfo.ACTION_DISMISS,
                null,
            )
        }
        assertThat(bubbleData.hasBubbleInStackWithKey("key1")).isFalse()
        assertThat(bubbleData.hasBubbleInStackWithKey("key2")).isTrue()
        assertThat(bubbleData.isExpanded).isTrue()
    }

    private fun createAndInflateChatBubble(key: String): Bubble {
        val icon = Icon.createWithResource(context.resources, R.drawable.bubble_ic_overflow_button)
        val shortcutInfo = ShortcutInfo.Builder(context, "fakeId").setIcon(icon).build()
        val bubble =
            Bubble(
                key,
                shortcutInfo,
                /* desiredHeight= */ 6,
                Resources.ID_NULL,
                "title",
                /* taskId= */ 0,
                "locus",
                /* isDismissable= */ true,
            ) {}
        inflateBubble(bubble)
        return bubble
    }

    private fun createAndInflateBubble(): Bubble {
        val intent = Intent(Intent.ACTION_VIEW).setPackage(context.packageName)
        val icon = Icon.createWithResource(context.resources, R.drawable.bubble_ic_overflow_button)
        val bubble = Bubble.createAppBubble(intent, UserHandle(1), icon)
        inflateBubble(bubble)
        return bubble
    }

    private fun inflateBubble(bubble: Bubble) {
        bubble.setInflateSynchronously(true)
        bubbleData.notificationEntryUpdated(bubble, true, false)

        val semaphore = Semaphore(0)
        val callback: BubbleViewInfoTask.Callback =
            BubbleViewInfoTask.Callback { semaphore.release() }
        bubble.inflate(
            callback,
            context,
            expandedViewManager,
            bubbleTaskViewFactory,
            bubbleStackView,
            null,
            iconFactory,
            false,
            bubbleViewInfoTaskFactory,
        )

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(bubble.isInflated).isTrue()
    }

    private class FakeBubbleStackViewManager : BubbleStackViewManager {
        var onImeHidden: Runnable? = null

        override fun onAllBubblesAnimatedOut() {}

        override fun updateWindowFlagsForBackpress(interceptBack: Boolean) {}

        override fun checkNotificationPanelExpandedState(callback: Consumer<Boolean>) {}

        override fun hideCurrentInputMethod(onImeHidden: Runnable?) {
            this.onImeHidden = onImeHidden
        }

        override fun clearImeHiddenRunnable() {
            this.onImeHidden = null
        }

        override fun isGestureNavigationMode() = false
    }

    private class FakeBubbleExpandListener : BubbleExpandListener {
        val bubblesExpandedState = mutableMapOf<String, Boolean>()

        override fun onBubbleExpandChanged(isExpanding: Boolean, key: String) {
            bubblesExpandedState[key] = isExpanding
        }
    }

    private class FakeSurfaceSynchronizer : SurfaceSynchronizer {
        var isActive = true

        override fun syncSurfaceAndRun(callback: Runnable) {
            if (isActive) callback.run()
        }
    }
}
