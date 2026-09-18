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

package com.android.wm.shell.windowdecor

import android.annotation.ColorInt
import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.util.AttributeSet
import android.view.Display.DEFAULT_DISPLAY
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import androidx.compose.ui.graphics.toArgb
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isGone
import com.android.window.flags.Flags
import com.android.wm.shell.R
import com.android.wm.shell.common.split.SplitScreenUtils
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger
import com.android.wm.shell.shared.bubbles.BubbleFlagHelper
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource
import com.android.wm.shell.windowdecor.common.DecorThemeUtil
import com.android.wm.shell.windowdecor.common.DrawableInsets
import com.android.wm.shell.windowdecor.common.createBackgroundDrawable
import com.android.wm.shell.windowdecor.extension.isFullscreen
import com.android.wm.shell.windowdecor.extension.isPinned
import com.android.wm.shell.windowdecor.extension.isRtl

/**
 * The "windowing pill" shown in the desktop mode handle and maximize menus. It consists of buttons
 * to change the windowing mode of a task.
 */
class WindowingPillView(private val context: Context, attrs: AttributeSet) :
    ConstraintLayout(context, attrs), OnClickListener {
    // Insets for ripple effect of Windowing Pill buttons
    private val iconButtondrawableShiftInset =
        context.resources.getDimensionPixelSize(
            R.dimen.desktop_mode_handle_menu_icon_button_ripple_inset_shift
        )
    private val iconButtondrawableBaseInset =
        context.resources.getDimensionPixelSize(
            R.dimen.desktop_mode_handle_menu_icon_button_ripple_inset_base
        )
    private val iconButtonRippleRadius =
        context.resources.getDimensionPixelSize(
            R.dimen.desktop_mode_handle_menu_icon_button_ripple_radius
        )
    private val iconButtonDrawableInsetsBase =
        DrawableInsets(
            t = iconButtondrawableBaseInset,
            b = iconButtondrawableBaseInset,
            l = iconButtondrawableBaseInset,
            r = iconButtondrawableBaseInset,
        )
    private val iconButtonDrawableInsetsLeft =
        DrawableInsets(
            t = iconButtondrawableBaseInset,
            b = iconButtondrawableBaseInset,
            l = iconButtondrawableShiftInset,
            r = 0,
        )
    private val iconButtonDrawableInsetsRight =
        DrawableInsets(
            t = iconButtondrawableBaseInset,
            b = iconButtondrawableBaseInset,
            l = 0,
            r = iconButtondrawableShiftInset,
        )
    private val iconButtonDrawableInsetStart
        get() = if (context.isRtl) iconButtonDrawableInsetsRight else iconButtonDrawableInsetsLeft

    private val iconButtonDrawableInsetEnd
        get() = if (context.isRtl) iconButtonDrawableInsetsLeft else iconButtonDrawableInsetsRight

    private val decorThemeUtil = DecorThemeUtil(context)
    private lateinit var style: MenuStyle

    private val fullscreenBtn: ImageButton
    private val splitscreenBtn: ImageButton
    private val floatingBtn: ImageButton
    private val desktopBtn: ImageButton

    private lateinit var taskInfo: RunningTaskInfo
    private lateinit var windowDecorationActions: WindowDecorationActions
    private lateinit var desktopModeUiEventLogger: DesktopModeUiEventLogger
    private var shouldShowDesktopModeButton = false

    private lateinit var onPillItemClicked: (() -> Unit)

    init {
        LayoutInflater.from(context)
            .inflate(R.layout.desktop_mode_window_decor_windowing_pill, this, true)

        fullscreenBtn = requireViewById<ImageButton>(R.id.fullscreen_button)
        splitscreenBtn = requireViewById<ImageButton>(R.id.split_screen_button)
        floatingBtn = requireViewById<ImageButton>(R.id.floating_button)
        desktopBtn = requireViewById<ImageButton>(R.id.desktop_button)
    }

    /**
     * Initializes the view. This method must be called unless the view is invisible.
     *
     * @param windowDecorationActions The [WindowDecorationActions] to perform windowing actions.
     * @param desktopModeUiEventLogger The logger for desktop mode UI events.
     * @param shouldShowDesktopModeButton Whether to show the desktop mode button.
     * @param onPillItemClicked A lambda to be invoked when any pill item is clicked.
     */
    fun initialize(
        windowDecorationActions: WindowDecorationActions,
        desktopModeUiEventLogger: DesktopModeUiEventLogger,
        shouldShowDesktopModeButton: Boolean,
        onPillItemClicked: (() -> Unit),
    ) {
        this.windowDecorationActions = windowDecorationActions
        this.desktopModeUiEventLogger = desktopModeUiEventLogger
        this.shouldShowDesktopModeButton = shouldShowDesktopModeButton
        this.onPillItemClicked = onPillItemClicked

        listOf(fullscreenBtn, splitscreenBtn, desktopBtn, floatingBtn).forEach {
            it.setOnClickListener(this)
        }

        desktopBtn.accessibilityDelegate =
            object : View.AccessibilityDelegate() {
                override fun performAccessibilityAction(
                    host: View,
                    action: Int,
                    args: Bundle?,
                ): Boolean {
                    if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.id) {
                        desktopModeUiEventLogger.log(
                            taskInfo,
                            DesktopModeUiEventLogger.DesktopUiEventEnum
                                .A11Y_APP_HANDLE_MENU_DESKTOP_VIEW,
                        )
                    }
                    return super.performAccessibilityAction(host, action, args)
                }
            }

        fullscreenBtn.accessibilityDelegate =
            object : View.AccessibilityDelegate() {
                override fun performAccessibilityAction(
                    host: View,
                    action: Int,
                    args: Bundle?,
                ): Boolean {
                    if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.id) {
                        desktopModeUiEventLogger.log(
                            taskInfo,
                            DesktopModeUiEventLogger.DesktopUiEventEnum
                                .A11Y_APP_HANDLE_MENU_FULLSCREEN,
                        )
                    }
                    return super.performAccessibilityAction(host, action, args)
                }
            }

        splitscreenBtn.accessibilityDelegate =
            object : View.AccessibilityDelegate() {
                override fun performAccessibilityAction(
                    host: View,
                    action: Int,
                    args: Bundle?,
                ): Boolean {
                    if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.id) {
                        desktopModeUiEventLogger.log(
                            taskInfo,
                            DesktopModeUiEventLogger.DesktopUiEventEnum
                                .A11Y_APP_HANDLE_MENU_SPLIT_SCREEN,
                        )
                    }
                    return super.performAccessibilityAction(host, action, args)
                }
            }

        with(context) {
            // Update a11y announcement out to say "double tap to enter Fullscreen"
            ViewCompat.replaceAccessibilityAction(
                fullscreenBtn,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                getString(
                    R.string.app_handle_menu_accessibility_announce,
                    getString(R.string.fullscreen_text),
                ),
                null,
            )

            // Update a11y announcement out to say "double tap to enter Desktop View"
            ViewCompat.replaceAccessibilityAction(
                desktopBtn,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                getString(
                    R.string.app_handle_menu_accessibility_announce,
                    getString(R.string.desktop_text),
                ),
                null,
            )

            // Update a11y announcement to say "double tap to enter Split Screen"
            ViewCompat.replaceAccessibilityAction(
                splitscreenBtn,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                getString(
                    R.string.app_handle_menu_accessibility_announce,
                    getString(R.string.split_screen_text),
                ),
                null,
            )
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.fullscreen_button -> {
                windowDecorationActions.onToFullscreen(taskInfo.taskId)
            }
            R.id.split_screen_button -> {
                windowDecorationActions.onToSplitScreen(taskInfo.taskId)
            }
            R.id.desktop_button -> {
                windowDecorationActions.onToDesktop(
                    taskInfo.taskId,
                    DesktopModeTransitionSource.APP_HANDLE_MENU_BUTTON,
                )
            }
            R.id.floating_button -> {
                windowDecorationActions.onToFloat(taskInfo.taskId)
            }
        }
        onPillItemClicked?.invoke()
    }

    /** Binds the menu views to the new data. */
    fun bind(taskInfo: RunningTaskInfo, isInSplitScreen: Boolean) {
        this.taskInfo = taskInfo
        this.style = calculateMenuStyle(taskInfo)

        background.setTint(style.backgroundColor)

        if (!BubbleFlagHelper.enableBubbleToFullscreen() || taskInfo.isFreeform) {
            floatingBtn.visibility = View.GONE
        }

        if (!Flags.enableNonDefaultDisplaySplitBugfix() && taskInfo.displayId != DEFAULT_DISPLAY) {
            splitscreenBtn.visibility = View.GONE
        }

        // Currently split screen doesn't override task windowing mode when it's entering split.
        // So a split task can have WINDOWING_MODE_FULLSCREEN. Use `isInSplitScreen` to check if
        // the task is in split screen.
        fullscreenBtn.isSelected = taskInfo.isFullscreen && !isInSplitScreen
        fullscreenBtn.isEnabled = !taskInfo.isFullscreen || isInSplitScreen
        fullscreenBtn.imageTintList = style.buttonColor
        splitscreenBtn.isSelected = isInSplitScreen
        splitscreenBtn.isEnabled = !isInSplitScreen
        splitscreenBtn.imageTintList = style.buttonColor
        floatingBtn.isSelected = taskInfo.isPinned
        floatingBtn.isEnabled = !taskInfo.isPinned
        floatingBtn.imageTintList = style.buttonColor
        desktopBtn.isGone = !shouldShowDesktopModeButton
        desktopBtn.isSelected = taskInfo.isFreeform
        desktopBtn.isEnabled = !taskInfo.isFreeform
        desktopBtn.imageTintList = style.buttonColor

        fullscreenBtn.apply {
            background =
                createBackgroundDrawable(
                    color = style.textColor,
                    cornerRadius = iconButtonRippleRadius,
                    drawableInsets = iconButtonDrawableInsetStart,
                )
        }

        splitscreenBtn.apply {
            background =
                createBackgroundDrawable(
                    color = style.textColor,
                    cornerRadius = iconButtonRippleRadius,
                    drawableInsets = iconButtonDrawableInsetsBase,
                )
        }
        updateSplitScreenButtonOrientation(taskInfo.configuration)

        floatingBtn.apply {
            background =
                createBackgroundDrawable(
                    color = style.textColor,
                    cornerRadius = iconButtonRippleRadius,
                    drawableInsets = iconButtonDrawableInsetsBase,
                )
        }

        desktopBtn.apply {
            background =
                createBackgroundDrawable(
                    color = style.textColor,
                    cornerRadius = iconButtonRippleRadius,
                    drawableInsets = iconButtonDrawableInsetEnd,
                )
        }
    }

    /** Update the split screen button (horizontal vs. vertical split) orientation. */
    fun updateSplitScreenButtonOrientation(configuration: Configuration) {
        splitscreenBtn.rotation =
            if (
                SplitScreenUtils.isLeftRightSplit(
                    SplitScreenUtils.allowLeftRightSplitInPortrait(context.resources),
                    configuration,
                    taskInfo.displayId,
                )
            ) {
                0f
            } else {
                90f
            }
    }

    private data class MenuStyle(
        @ColorInt val backgroundColor: Int,
        @ColorInt val textColor: Int,
        val buttonColor: ColorStateList,
    )

    private fun calculateMenuStyle(taskInfo: RunningTaskInfo): MenuStyle {
        val colorScheme = decorThemeUtil.getColorScheme(taskInfo)
        return MenuStyle(
            backgroundColor = colorScheme.surfaceBright.toArgb(),
            textColor = colorScheme.onSurface.toArgb(),
            buttonColor =
                ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_pressed),
                        intArrayOf(android.R.attr.state_focused),
                        intArrayOf(android.R.attr.state_selected),
                        intArrayOf(),
                    ),
                    intArrayOf(
                        colorScheme.onSurface.toArgb(),
                        colorScheme.onSurface.toArgb(),
                        colorScheme.primary.toArgb(),
                        colorScheme.onSurface.toArgb(),
                    ),
                ),
        )
    }
}
