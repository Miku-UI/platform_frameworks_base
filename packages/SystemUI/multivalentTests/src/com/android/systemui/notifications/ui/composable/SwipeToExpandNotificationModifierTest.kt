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

import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorTestRule
import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.gesture.effect.rememberOffsetOverscrollEffect
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.statusbar.notification.row.ExpandableView
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class SwipeToExpandNotificationModifierTest : SysuiTestCase() {
    @get:Rule val rule = createComposeRule()
    @get:Rule val animatorTestRule = AnimatorTestRule(this)

    private var callback = FakeExpandHelperCallback()

    @Test
    fun testDragDown_expandTarget() {
        val target = FakeExpandableTarget(context, collapsedHeight = 100, expandedHeight = 300)
        callback.childAtPosition = target

        setTestContent()

        rule.onNodeWithTag(EXPANDABLE_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, 100f)) // Large enough to exceed the slop.
        }

        assertThat(callback.isExpanding).isTrue()
        assertThat(target.userSwipingToExpand).isTrue()

        rule.onNodeWithTag(EXPANDABLE_TAG).performTouchInput {
            moveBy(Offset(0f, 200f))
            up()
        }
        rule.waitForIdle() // Wait for any any animation to settle.
        assertThat(target.userExpanded).isTrue()
        assertThat(target.userSwipingToExpand).isFalse()
        assertThat(callback.isExpanding).isFalse()
    }

    @Test
    fun testDragUp_doesNotExpand() {
        val target = FakeExpandableTarget(context, collapsedHeight = 100, expandedHeight = 200)
        callback.childAtPosition = target

        setTestContent()

        rule.onNodeWithTag(EXPANDABLE_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, -100f))
            up()
        }
        rule.waitForIdle() // Wait for any any animation to settle.

        assertThat(callback.isExpanding).isFalse()
        assertThat(target.userSwipingToExpand).isFalse()
        assertThat(target.userExpanded).isFalse()
    }

    @Test
    fun testDisabled_doesNotExpand() {
        val target = FakeExpandableTarget(context, collapsedHeight = 100, expandedHeight = 200)
        callback.childAtPosition = target

        setTestContent(gestureEnabled = { false })

        rule.onNodeWithTag(EXPANDABLE_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, 150f))
            up()
        }
        rule.waitForIdle() // Wait for any any animation to settle.

        assertThat(callback.isExpanding).isFalse()
        assertThat(target.userSwipingToExpand).isFalse()
        assertThat(target.userExpanded).isFalse()
    }

    @Test
    fun testDragUpPastMin_clamped() {
        val target = FakeExpandableTarget(context, collapsedHeight = 100, expandedHeight = 300)
        callback.childAtPosition = target

        setTestContent()

        rule.onNodeWithTag(EXPANDABLE_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, 100f)) // Drag down to expand a bit.
            moveBy(Offset(0f, -200f)) // Drag back up past collapsed height.
        }
        assertThat(callback.isExpanding).isTrue()
        assertThat(target.userSwipingToExpand).isTrue()

        rule.onNodeWithTag(EXPANDABLE_TAG).performTouchInput { up() }
        rule.waitForIdle() // Wait for any any animation to settle.
        assertThat(target.userSwipingToExpand).isFalse()
        assertThat(target.userExpanded).isFalse()
    }

    @Test
    fun testDragDown_updatesHeightImmediatelyOnFirstFrame() {
        val target = FakeExpandableTarget(context, collapsedHeight = 100, expandedHeight = 300)
        callback.childAtPosition = target

        setTestContent()

        rule.onNodeWithTag(EXPANDABLE_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, 50f))
        }

        assertThat(callback.isExpanding).isTrue()
        assertThat(target.actualHeight).isGreaterThan(100)

        // Clean up gesture
        rule.onNodeWithTag(EXPANDABLE_TAG).performTouchInput { up() }
    }

    @Test
    fun testDragDown_playsHapticFeedbackOnce() {
        val target = FakeExpandableTarget(context, collapsedHeight = 100, expandedHeight = 300)
        callback.childAtPosition = target

        setTestContent()

        rule.onNodeWithTag(EXPANDABLE_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, 50f)) // Exceed slop, trigger expansion
        }

        assertThat(callback.hapticPlayCount).isEqualTo(1)

        rule.onNodeWithTag(EXPANDABLE_TAG).performTouchInput {
            moveBy(Offset(0f, 50f)) // Continue dragging
        }

        // Clean up gesture
        rule.onNodeWithTag(EXPANDABLE_TAG).performTouchInput { up() }

        // Haptic should NOT be played again on subsequent input events
        assertThat(callback.hapticPlayCount).isEqualTo(1)
    }

    @Test
    fun testDragUpThenDown_expandsSuccessfully() {
        val target = FakeExpandableTarget(context, collapsedHeight = 100, expandedHeight = 300)
        callback.childAtPosition = target

        setTestContent()

        rule.onNodeWithTag(EXPANDABLE_TAG).performTouchInput {
            down(center)
            // Move up first.
            moveBy(Offset(0f, -20f))

            // Now drag downwards.
            moveBy(Offset(0f, 100f))
        }

        assertThat(callback.isExpanding).isTrue()
        assertThat(target.actualHeight).isGreaterThan(100)

        // Clean up gesture
        rule.onNodeWithTag(EXPANDABLE_TAG).performTouchInput { up() }
    }

    @Test
    fun testFlingUpAfterFullyExpanded_staysExpanded() {
        val target = FakeExpandableTarget(context, collapsedHeight = 100, expandedHeight = 300)
        callback.childAtPosition = target

        setTestContent()

        rule.onNodeWithTag(EXPANDABLE_TAG).performTouchInput {
            down(center)

            // Drag down fully to trigger the `hasFullyExpanded` latch.
            moveBy(Offset(0f, 300f), delayMillis = 100)

            // Fling upward and release immediately.
            // Generate a strong negative velocity by dragging in just 16ms.
            moveBy(Offset(0f, -100f), delayMillis = 16)
            up()
        }

        rule.waitForIdle() // Wait for any any animation to settle.

        // Verify the target stayed expanded despite the upward fling.
        assertThat(target.userExpanded).isTrue()
        assertThat(target.actualHeight).isEqualTo(300)
        assertThat(callback.isExpanding).isFalse()
    }

    private fun setTestContent(gestureEnabled: () -> Boolean = { true }) =
        rule.setContent {
            Box(
                modifier =
                    Modifier.testTag(EXPANDABLE_TAG)
                        .fillMaxSize()
                        .swipeToExpandNotification(
                            callback = callback,
                            overscrollEffect = rememberOffsetOverscrollEffect(),
                            layoutCoordinatesProvider = { FakeLayoutCoordinates() },
                            allowStartGesture = gestureEnabled,
                            velocityThresholdPx = VELOCITY_THRESHOLD,
                            distanceThresholdPx = DISTANCE_THRESHOLD,
                        )
            )
        }

    companion object {
        const val EXPANDABLE_TAG = "expadable_tag"
        private const val VELOCITY_THRESHOLD = 100f // px/sec
        private const val DISTANCE_THRESHOLD = 20f // px
    }
}

private class FakeExpandHelperCallback : SwipeToExpandCallback {
    var childAtPosition: FakeExpandableTarget? = null
    var canChildbeExpanded = true
    var isExpanding = false
        private set

    var hapticPlayCount = 0
        private set

    override fun getChildAtRawPosition(x: Float, y: Float): ExpandableView? {
        return childAtPosition
    }

    override fun canChildBeExpanded(v: View?): Boolean {
        return canChildbeExpanded
    }

    override fun setUserExpandedChild(v: View?, userExpanded: Boolean) {
        if (v is FakeExpandableTarget) {
            v.userExpanded = userExpanded
        }
    }

    override fun setUserSwipingToExpand(v: View?, isUserSwiping: Boolean) {
        if (v is FakeExpandableTarget) {
            v.userSwipingToExpand = isUserSwiping
        }
    }

    override fun expansionStateChanged(isExpanding: Boolean) {
        this.isExpanding = isExpanding
    }

    override fun setExpansionCancelled(v: View?) {
        if (v is FakeExpandableTarget) {
            v.expansionCancelled = true
        }
    }

    override fun playExpandStartHaptic() {
        hapticPlayCount++
    }
}

private class FakeExpandableTarget(
    context: Context,
    private val collapsedHeight: Int,
    private val expandedHeight: Int,
) : ExpandableView(context, null) {

    var userExpanded = false
    var userSwipingToExpand = false
    var expansionCancelled = false

    init {
        actualHeight = collapsedHeight
    }

    override fun getCollapsedHeight(): Int = collapsedHeight

    override fun getMaxContentHeight(): Int = expandedHeight

    @Suppress("DEPRECATION")
    override fun setActualHeight(actualHeight: Int) {
        super.setActualHeight(actualHeight, false)
    }

    override fun setFinalActualHeight(childHeight: Int) {
        actualHeight = childHeight
    }

    override fun performRemoveAnimation(
        duration: Long,
        delay: Long,
        translationDirection: Float,
        isHeadsUpAnimation: Boolean,
        isHeadsUpCycling: Boolean,
        onStartedRunnable: Runnable?,
        onFinishedRunnable: Runnable?,
        animationListener: AnimatorListenerAdapter?,
        clipSide: ClipSide?,
    ): Long = 0L

    override fun performAddAnimation(
        delay: Long,
        duration: Long,
        isHeadsUpAppear: Boolean,
        isHeadsUpCycling: Boolean,
        onEndRunnable: Runnable?,
    ) {}
}

private class FakeLayoutCoordinates(
    override val size: IntSize = IntSize(1000, 1000),
    override val isAttached: Boolean = true,
    override val parentCoordinates: LayoutCoordinates? = null,
    override val parentLayoutCoordinates: LayoutCoordinates? = null,
    override val providedAlignmentLines: Set<AlignmentLine> = emptySet(),
    val transform: (Offset) -> Offset = { it },
) : LayoutCoordinates {
    override fun get(alignmentLine: AlignmentLine): Int = 0

    override fun localBoundingBoxOf(
        sourceCoordinates: LayoutCoordinates,
        clipBounds: Boolean,
    ): Rect = Rect.Zero

    override fun localPositionOf(
        sourceCoordinates: LayoutCoordinates,
        relativeToSource: Offset,
    ): Offset = transform(relativeToSource)

    override fun localToRoot(relativeToLocal: Offset): Offset = transform(relativeToLocal)

    override fun localToWindow(relativeToLocal: Offset): Offset = transform(relativeToLocal)

    override fun windowToLocal(relativeToWindow: Offset): Offset = transform(relativeToWindow)
}
