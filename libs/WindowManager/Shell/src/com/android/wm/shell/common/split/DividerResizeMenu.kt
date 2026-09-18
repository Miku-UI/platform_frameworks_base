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
package com.android.wm.shell.common.split

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.R
import com.android.wm.shell.common.split.DividerSnapAlgorithm.SnapTarget
import com.android.wm.shell.protolog.ShellProtoLogGroup

/** A popup menu containing accessibility buttons that allow resizing the split screen. */
class DividerResizeMenu(context: Context) : FrameLayout(context) {
    @VisibleForTesting val prevButton: ImageView = ImageView(context)

    @VisibleForTesting val nextButton: ImageView = ImageView(context)

    /** @return `true` if the menu is currently visible or animating in. */
    var isShowing: Boolean = false
        private set

    private var mSplitLayout: SplitLayout? = null
    private val mTempRect = Rect()
    private var mButtonSize = 0
    private var mButtonGap = 0
    private var mVisibilityCallback: (() -> Unit)? = null
    private val mHideRunnable = Runnable { hide() }

    init {
        setupButton(prevButton)
        setupButton(nextButton)
        addView(prevButton)
        addView(nextButton)
        clipChildren = false
        clipToPadding = false

        prevButton.setOnClickListener { snapToPrev() }
        nextButton.setOnClickListener { snapToNext() }
    }

    /**
     * Initializes the menu with the necessary dependencies.
     *
     * @param splitLayout The [SplitLayout] used to calculate snap targets and execute snaps.
     */
    fun setup(splitLayout: SplitLayout?, callback: (() -> Unit)?) {
        mSplitLayout = splitLayout
        mVisibilityCallback = callback
        mButtonSize = resources.getDimensionPixelSize(R.dimen.split_divider_button_size)
        mButtonGap = resources.getDimensionPixelSize(R.dimen.split_divider_button_margin)
        updateButtonSize(prevButton)
        updateButtonSize(nextButton)
    }

    private fun setupButton(button: ImageView) {
        button.scaleType = ImageView.ScaleType.FIT_CENTER
        val a =
            context.obtainStyledAttributes(
                intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
            )
        button.background = a.getDrawable(0)
        a.recycle()
        button.visibility = GONE
        button.alpha = 0f
    }

    private fun updateButtonSize(button: ImageView) {
        if (mButtonSize > 0) {
            val lp = button.layoutParams as LayoutParams
            lp.width = mButtonSize
            lp.height = mButtonSize
            lp.gravity = Gravity.CENTER
            button.layoutParams = lp
        }
    }

    /** Updates button positions and fades in the resize menu buttons. */
    fun show() {
        if (isShowing) {
            resetHideTimer()
            return
        }
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN, "Showing DividerResizeMenu")
        updateButtons()

        val set = AnimatorSet()
        set.playTogether(
            ObjectAnimator.ofFloat(prevButton, ALPHA, 1f),
            ObjectAnimator.ofFloat(nextButton, ALPHA, 1f),
        )
        set.setDuration(TOUCH_ANIMATION_DURATION)
        set.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    isShowing = true
                    prevButton.visibility = VISIBLE
                    nextButton.visibility = VISIBLE
                    prevButton.alpha = 0f
                    nextButton.alpha = 0f
                    mVisibilityCallback?.invoke()
                    resetHideTimer()
                }
            }
        )
        set.start()
    }

    /** Fades out the resize menu buttons. */
    fun hide() {
        if (!isShowing) return
        removeCallbacks(mHideRunnable)
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN, "Hiding DividerResizeMenu")

        val set = AnimatorSet()
        set.playTogether(
            ObjectAnimator.ofFloat(prevButton, ALPHA, 0f),
            ObjectAnimator.ofFloat(nextButton, ALPHA, 0f),
        )
        set.setDuration(TOUCH_ANIMATION_DURATION)
        set.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    prevButton.visibility = GONE
                    nextButton.visibility = GONE
                    isShowing = false
                    mVisibilityCallback?.invoke()
                }
            }
        )
        set.start()
    }

    /**
     * Recalculates the positions and icons of the resize buttons based on the current split state.
     */
    fun updateButtons() {
        val layout = mSplitLayout ?: return
        resetHideTimer()
        val isLeftRightSplit = layout.isLeftRightSplit
        val position = layout.dividerPosition
        val snapAlgorithm = layout.mDividerSnapAlgorithm

        val prevTarget = snapAlgorithm.snapToPrev(position)
        val nextTarget = snapAlgorithm.snapToNext(position)

        updateButtonIcon(prevButton, prevTarget, true, /* isPrev */ isLeftRightSplit)
        updateButtonIcon(nextButton, nextTarget, false, /* isPrev */ isLeftRightSplit)

        val handleWidth =
            resources.getDimensionPixelSize(
                if (isLeftRightSplit) R.dimen.split_divider_handle_height
                else R.dimen.split_divider_handle_width
            )
        val handleHeight =
            resources.getDimensionPixelSize(
                if (isLeftRightSplit) R.dimen.split_divider_handle_width
                else R.dimen.split_divider_handle_height
            )

        if (isLeftRightSplit) {
            // Vertical divider: place buttons left and right of the handle
            prevButton.translationX = -handleWidth / 2f - mButtonSize / 2f - mButtonGap
            prevButton.translationY = 0f
            nextButton.translationX = handleWidth / 2f + mButtonSize / 2f + mButtonGap
            nextButton.translationY = 0f
        } else {
            // Horizontal divider: place buttons above and below the handle
            prevButton.translationX = 0f
            prevButton.translationY = -handleHeight / 2f - mButtonSize / 2f - mButtonGap
            nextButton.translationX = 0f
            nextButton.translationY = handleHeight / 2f + mButtonSize / 2f + mButtonGap
        }
    }

    private fun updateButtonIcon(
        button: ImageView,
        target: SnapTarget,
        isPrev: Boolean,
        isLeftRightSplit: Boolean,
    ) {
        val layout = mSplitLayout ?: return
        val snapAlgorithm = layout.mDividerSnapAlgorithm
        val isDismiss =
            target === snapAlgorithm.dismissStartTarget || target === snapAlgorithm.dismissEndTarget

        if (isDismiss) {
            button.setImageResource(R.drawable.split_ic_divider_menu_close)
            button.rotation = 0f
        } else {
            button.setImageResource(R.drawable.split_ic_divider_menu_arrow)
            if (isLeftRightSplit) {
                button.rotation = (if (isPrev) 0 else 180).toFloat()
            } else {
                button.rotation = (if (isPrev) 90 else 270).toFloat()
            }
        }
    }

    /** Snaps the divider to the previous valid split target (e.g., left or top). */
    private fun snapToPrev() {
        resetHideTimer()
        val layout = mSplitLayout ?: return
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN, "DividerResizeMenu: Snap to previous")
        val position = layout.dividerPosition
        val target = layout.mDividerSnapAlgorithm.snapToPrev(position)
        layout.snapToTarget(position, target)
    }

    /** Snaps the divider to the next valid split target (e.g., right or bottom). */
    private fun snapToNext() {
        resetHideTimer()
        val layout = mSplitLayout ?: return
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN, "DividerResizeMenu: Snap to next")
        val position = layout.dividerPosition
        val target = layout.mDividerSnapAlgorithm.snapToNext(position)
        layout.snapToTarget(position, target)
    }

    /**
     * Checks if the given screen coordinates fall within the bounds of any visible menu button.
     *
     * @param x The screen-relative X coordinate.
     * @param y The screen-relative Y coordinate.
     * @return `true` if the touch is on a menu button, `false` otherwise.
     */
    fun isTouchInside(x: Float, y: Float): Boolean {
        if (!isShowing) return false
        return isTouchInView(prevButton, x, y) || isTouchInView(nextButton, x, y)
    }

    private fun isTouchInView(view: View?, x: Float, y: Float): Boolean {
        if (view == null || view.visibility != VISIBLE) return false
        view.getGlobalVisibleRect(mTempRect)
        return mTempRect.contains(x.toInt(), y.toInt())
    }

    private fun resetHideTimer() {
        removeCallbacks(mHideRunnable)
        postDelayed(mHideRunnable, HIDE_TIMEOUT_MS)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(mHideRunnable)
    }

    @VisibleForTesting
    fun setVisible(visible: Boolean) {
        isShowing = visible
    }

    companion object {
        private const val TOUCH_ANIMATION_DURATION: Long = 150
        private const val HIDE_TIMEOUT_MS: Long = 3000
    }
}
