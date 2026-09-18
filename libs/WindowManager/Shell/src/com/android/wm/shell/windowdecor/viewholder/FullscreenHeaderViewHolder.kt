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
package com.android.wm.shell.windowdecor.viewholder

import android.annotation.ColorInt
import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLongClickListener
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.window.DesktopModeFlags
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import com.android.window.flags.Flags
import com.android.wm.shell.R
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.InputMethod
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum.A11Y_ACTION_EXIT_FULLSCREEN
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum.A11Y_ACTION_RESIZE_LEFT
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum.A11Y_ACTION_RESIZE_RIGHT
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum.A11Y_APP_WINDOW_CLOSE_BUTTON
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum.A11Y_APP_WINDOW_MINIMIZE_BUTTON
import com.android.wm.shell.shared.FocusTransitionListener
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource
import com.android.wm.shell.transition.FocusTransitionObserver
import com.android.wm.shell.windowdecor.MaximizeButtonView
import com.android.wm.shell.windowdecor.WindowDecorLinearLayout
import com.android.wm.shell.windowdecor.WindowDecorationActions
import com.android.wm.shell.windowdecor.common.DecorThemeUtil
import com.android.wm.shell.windowdecor.common.OPACITY_100
import com.android.wm.shell.windowdecor.common.OPACITY_55
import com.android.wm.shell.windowdecor.common.OPACITY_65
import com.android.wm.shell.windowdecor.common.Theme
import com.android.wm.shell.windowdecor.common.createBackgroundDrawable
import com.android.wm.shell.windowdecor.extension.identityHashCode
import com.android.wm.shell.windowdecor.extension.isLightCaptionBarAppearance
import com.android.wm.shell.windowdecor.extension.throttleFirstClicks
import com.android.wm.shell.windowdecor.viewholder.util.HeaderDimensions

/**
 * A desktop mode window decoration used when the window is fullscreen. It hosts finer controls such
 * as a close window button and an "app info" section to pull up additional controls.
 */
class FullscreenHeaderViewHolder(
    private val context: Context,
    windowDecorationActions: WindowDecorationActions,
    gestureInterceptor: WindowDecorLinearLayout.GestureInterceptor,
    private val onLongClickListener: OnLongClickListener,
    onCaptionGenericMotionListener: View.OnGenericMotionListener,
    onExitFullscreenHoverAnimationFinishedListener: () -> Unit,
    private val desktopModeUiEventLogger: DesktopModeUiEventLogger,
    private val dimensions: HeaderDimensions,
    private val focusTransitionObserver: FocusTransitionObserver,
) : WindowDecorationViewHolder<FullscreenHeaderViewHolder.HeaderData>() {

    data class HeaderData(
        val taskInfo: RunningTaskInfo,
        val hasGlobalFocus: Boolean,
        val enableMaximizeLongClick: Boolean,
        val isCaptionVisible: Boolean,
    ) : Data()

    private val decorThemeUtil = DecorThemeUtil(context)
    private val lightColors = dynamicLightColorScheme(context)
    private val darkColors = dynamicDarkColorScheme(context)

    override val rootView =
        LayoutInflater.from(context).inflate(R.layout.desktop_mode_fullscreen_header, null)
            as WindowDecorLinearLayout
    private val captionView: WindowDecorLinearLayout =
        rootView.requireViewById(R.id.desktop_mode_caption)
    private val captionHandle: View = rootView.requireViewById(R.id.caption_handle)
    private val openMenuButton: View = rootView.requireViewById(R.id.open_menu_button)
    private val closeWindowButton: ImageButton = rootView.requireViewById(R.id.close_window)
    private val expandMenuButton: ImageButton = rootView.requireViewById(R.id.expand_menu_button)
    // Reuse MaximizeButtonView to open layout menu on hover
    private val exitFullscreenButtonView: MaximizeButtonView =
        rootView.requireViewById(R.id.exit_fullscreen_button_view)
    private val exitFullscreenWindowButton: ImageButton =
        exitFullscreenButtonView.requireViewById(R.id.maximize_window)
    private val minimizeWindowButton: ImageButton = rootView.requireViewById(R.id.minimize_window)
    private val appNameTextView: TextView = rootView.requireViewById(R.id.application_name)
    private val appIconImageView: ImageView = rootView.requireViewById(R.id.application_icon)
    private val expandMenuErrorImageView: ImageView =
        rootView.requireViewById(R.id.expand_menu_error)

    /** The width of the exit fullscreen button view. */
    val exitFullscreenButtonWidth: Int
        get() = exitFullscreenButtonView.width

    private val a11yAnnounceTextExitFullscreen: String =
        context.getString(R.string.fullscreen_header_talkback_action_exit_fullscreen_button_text)
    private val a11yAnnounceTextMinimizing: String =
        context.getString(R.string.desktop_mode_talkback_state_minimizing)
    private val a11yAnnounceTextClosing: String =
        context.getString(R.string.desktop_mode_talkback_state_closing)
    private lateinit var a11yAnnounceTextFocused: String

    private lateinit var currentTaskInfo: RunningTaskInfo

    init {
        if (!Flags.interceptTouchEventForAppHeaderDragMove()) {
            captionView.setOnTouchListener(gestureInterceptor)
            captionHandle.setOnTouchListener(gestureInterceptor)
            closeWindowButton.setOnTouchListener(gestureInterceptor)
            exitFullscreenWindowButton.setOnTouchListener(gestureInterceptor)
            minimizeWindowButton.setOnTouchListener(gestureInterceptor)
            openMenuButton.setOnTouchListener(gestureInterceptor)
        }

        closeWindowButton.throttleFirstClicks(CLICK_DELAY) { v ->
            windowDecorationActions.onClose(currentTaskInfo)
        }
        exitFullscreenWindowButton.throttleFirstClicks(CLICK_DELAY) { v ->
            windowDecorationActions.onToDesktop(
                currentTaskInfo.taskId,
                DesktopModeTransitionSource.FULLSCREEN_HEADER,
            )
        }
        minimizeWindowButton.throttleFirstClicks(CLICK_DELAY) { v ->
            windowDecorationActions.onMinimize(currentTaskInfo)
        }
        openMenuButton.throttleFirstClicks(CLICK_DELAY) { v ->
            windowDecorationActions.onOpenHandleMenu(currentTaskInfo.taskId)
        }

        exitFullscreenWindowButton.setOnGenericMotionListener(onCaptionGenericMotionListener)
        exitFullscreenWindowButton.onLongClickListener = onLongClickListener
        exitFullscreenButtonView.onHoverAnimationFinishedListener =
            onExitFullscreenHoverAnimationFinishedListener

        openMenuButton.layoutParams =
            openMenuButton.layoutParams.apply { height = dimensions.windowControlButtonHeight }
        listOf(minimizeWindowButton, closeWindowButton).forEach { button ->
            button.layoutParams =
                button.layoutParams.apply {
                    width = dimensions.windowControlButtonWidth
                    height = dimensions.windowControlButtonHeight
                }
            button.setPadding(
                dimensions.windowControlButtonPadding.left,
                dimensions.windowControlButtonPadding.top,
                dimensions.windowControlButtonPadding.right,
                dimensions.windowControlButtonPadding.bottom,
            )
        }
        exitFullscreenButtonView.setDimensions(
            dimensions.windowControlButtonWidth,
            dimensions.windowControlButtonHeight,
            Rect(
                dimensions.windowControlButtonPadding.left,
                dimensions.windowControlButtonPadding.top,
                dimensions.windowControlButtonPadding.right,
                dimensions.windowControlButtonPadding.bottom,
            ),
        )

        val a11yActionSnapLeft =
            AccessibilityAction(
                R.id.action_snap_left,
                context.getString(R.string.desktop_mode_a11y_action_snap_left),
            )
        val a11yActionSnapRight =
            AccessibilityAction(
                R.id.action_snap_right,
                context.getString(R.string.desktop_mode_a11y_action_snap_right),
            )
        val a11yActionExitFullscreen =
            AccessibilityAction(
                R.id.action_exit_fullscreen,
                context.getString(R.string.desktop_mode_a11y_action_exit_fullscreen),
            )

        captionHandle.accessibilityDelegate =
            object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfo,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.addAction(a11yActionSnapLeft)
                    info.addAction(a11yActionSnapRight)
                    info.addAction(a11yActionExitFullscreen)
                    info.liveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                    info.isScreenReaderFocusable = false
                }

                override fun performAccessibilityAction(
                    host: View,
                    action: Int,
                    args: Bundle?,
                ): Boolean {
                    when (action) {
                        R.id.action_snap_left -> {
                            desktopModeUiEventLogger.log(currentTaskInfo, A11Y_ACTION_RESIZE_LEFT)
                            windowDecorationActions.onLeftSnap(
                                currentTaskInfo.taskId,
                                InputMethod.ACCESSIBILITY,
                            )
                        }
                        R.id.action_snap_right -> {
                            desktopModeUiEventLogger.log(currentTaskInfo, A11Y_ACTION_RESIZE_RIGHT)
                            windowDecorationActions.onRightSnap(
                                currentTaskInfo.taskId,
                                InputMethod.ACCESSIBILITY,
                            )
                        }
                        R.id.action_exit_fullscreen -> {
                            desktopModeUiEventLogger.log(
                                currentTaskInfo,
                                A11Y_ACTION_EXIT_FULLSCREEN,
                            )
                            windowDecorationActions.onToDesktop(
                                currentTaskInfo.taskId,
                                DesktopModeTransitionSource.APP_HANDLE_MENU_BUTTON,
                            )
                        }
                    }

                    return super.performAccessibilityAction(host, action, args)
                }
            }
        exitFullscreenWindowButton.accessibilityDelegate =
            object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfo,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.addAction(AccessibilityAction.ACTION_CLICK)
                    info.addAction(a11yActionSnapLeft)
                    info.addAction(a11yActionSnapRight)
                    info.addAction(a11yActionExitFullscreen)
                    host.isClickable = true
                }

                override fun performAccessibilityAction(
                    host: View,
                    action: Int,
                    args: Bundle?,
                ): Boolean {
                    when (action) {
                        AccessibilityAction.ACTION_CLICK.id -> {
                            // clear focus before clicking so that focus can be captured by resize
                            // veil, which is necessary due to a race condition bug where after
                            // clicking maximize, a11y focus wouldn't go back to maximize button
                            host.clearFocus()
                            host.clearAccessibilityFocus()
                            host.requestFocus()
                            desktopModeUiEventLogger.log(
                                currentTaskInfo,
                                DesktopModeUiEventLogger.DesktopUiEventEnum
                                    .A11Y_ACTION_EXIT_FULLSCREEN,
                            )
                            captionHandle.stateDescription = a11yAnnounceTextExitFullscreen
                        }
                        R.id.action_snap_left -> {
                            desktopModeUiEventLogger.log(currentTaskInfo, A11Y_ACTION_RESIZE_LEFT)
                            windowDecorationActions.onLeftSnap(
                                currentTaskInfo.taskId,
                                InputMethod.ACCESSIBILITY,
                            )
                        }
                        R.id.action_snap_right -> {
                            desktopModeUiEventLogger.log(currentTaskInfo, A11Y_ACTION_RESIZE_RIGHT)
                            windowDecorationActions.onRightSnap(
                                currentTaskInfo.taskId,
                                InputMethod.ACCESSIBILITY,
                            )
                        }
                        R.id.action_exit_fullscreen -> {
                            desktopModeUiEventLogger.log(
                                currentTaskInfo,
                                A11Y_ACTION_EXIT_FULLSCREEN,
                            )
                            windowDecorationActions.onToDesktop(
                                currentTaskInfo.taskId,
                                DesktopModeTransitionSource.FULLSCREEN_HEADER,
                            )
                        }
                    }

                    return super.performAccessibilityAction(host, action, args)
                }
            }

        closeWindowButton.accessibilityDelegate =
            object : View.AccessibilityDelegate() {
                override fun performAccessibilityAction(
                    host: View,
                    action: Int,
                    args: Bundle?,
                ): Boolean {
                    when (action) {
                        AccessibilityAction.ACTION_CLICK.id -> {
                            captionHandle.stateDescription = a11yAnnounceTextClosing
                            desktopModeUiEventLogger.log(
                                currentTaskInfo,
                                A11Y_APP_WINDOW_CLOSE_BUTTON,
                            )
                        }
                    }

                    return super.performAccessibilityAction(host, action, args)
                }
            }

        minimizeWindowButton.accessibilityDelegate =
            object : View.AccessibilityDelegate() {
                override fun performAccessibilityAction(
                    host: View,
                    action: Int,
                    args: Bundle?,
                ): Boolean {
                    when (action) {
                        AccessibilityAction.ACTION_CLICK.id -> {
                            captionHandle.stateDescription = a11yAnnounceTextMinimizing
                            desktopModeUiEventLogger.log(
                                currentTaskInfo,
                                A11Y_APP_WINDOW_MINIMIZE_BUTTON,
                            )
                        }
                    }

                    return super.performAccessibilityAction(host, action, args)
                }
            }

        // Update a11y announcement to say "double tap to open menu"
        ViewCompat.replaceAccessibilityAction(
            openMenuButton,
            AccessibilityActionCompat.ACTION_CLICK,
            context.getString(R.string.app_handle_chip_accessibility_announce),
            null,
        )

        // Update a11y announcement to say "double tap to minimize app window"
        ViewCompat.replaceAccessibilityAction(
            minimizeWindowButton,
            AccessibilityActionCompat.ACTION_CLICK,
            context.getString(R.string.app_header_talkback_action_minimize_button_text),
            null,
        )

        // Update a11y announcement to say "double tap to close app window"
        ViewCompat.replaceAccessibilityAction(
            closeWindowButton,
            AccessibilityActionCompat.ACTION_CLICK,
            context.getString(R.string.app_header_talkback_action_close_button_text),
            null,
        )
    }

    override fun bindData(data: HeaderData) {
        bindData(data.taskInfo, data.hasGlobalFocus, data.isCaptionVisible)
    }

    /** Announces app window name as "focused" via Talkback */
    fun a11yAnnounceFocused() {
        captionHandle.stateDescription = a11yAnnounceTextFocused
    }

    /** Sets the app's name in the header. */
    fun setAppName(name: CharSequence) {
        appNameTextView.text = name
        populateA11yStrings(name)
    }

    /** Populates string variables from string templates which rely on app name */
    private fun populateA11yStrings(name: CharSequence) {
        openMenuButton.contentDescription =
            context.getString(R.string.desktop_mode_header_chip_text, name)

        closeWindowButton.contentDescription = context.getString(R.string.close_button_text, name)
        minimizeWindowButton.contentDescription =
            context.getString(R.string.minimize_button_text, name)
        exitFullscreenWindowButton.contentDescription =
            context.getString(R.string.exit_fullscreen_button_text, name)

        a11yAnnounceTextFocused =
            context.getString(R.string.desktop_mode_talkback_state_focused, name)
    }

    /** Sets the app's icon in the header. */
    fun setAppIcon(icon: Bitmap) {
        appIconImageView.setImageBitmap(icon)
    }

    private fun bindData(
        taskInfo: RunningTaskInfo,
        hasGlobalFocus: Boolean,
        isCaptionVisible: Boolean,
    ) {
        currentTaskInfo = taskInfo
        val header = fillHeaderInfo(taskInfo, hasGlobalFocus)
        val headerStyle = getHeaderStyle(header)

        if (DesktopModeFlags.ENABLE_DESKTOP_APP_HANDLE_ANIMATION.isTrue()) {
            setCaptionVisibility(isCaptionVisible)
        }

        // Caption Background
        when (headerStyle.background) {
            is HeaderStyle.Background.Opaque -> {
                captionView.setBackgroundColor(headerStyle.background.color)
            }
            HeaderStyle.Background.Transparent -> {
                captionView.setBackgroundColor(Color.TRANSPARENT)
            }
        }

        // Caption Foreground
        val foregroundColor = headerStyle.foreground.color
        val foregroundAlpha = headerStyle.foreground.opacity
        val colorStateList = ColorStateList.valueOf(foregroundColor).withAlpha(foregroundAlpha)
        // App chip.
        openMenuButton.apply {
            val isRestartMenuEnabledForDisplayMove =
                currentTaskInfo.appCompatTaskInfo.isRestartMenuEnabledForDisplayMove
            background =
                createBackgroundDrawable(
                    color = foregroundColor,
                    cornerRadius = dimensions.buttonCornerRadius,
                    drawableInsets = dimensions.appChipBackgroundInsets,
                )
            expandMenuButton.imageTintList = colorStateList
            expandMenuErrorImageView.visibility =
                if (isRestartMenuEnabledForDisplayMove) View.VISIBLE else View.GONE
            appNameTextView.apply {
                isVisible = true
                setTextColor(colorStateList)
                maxWidth =
                    if (isRestartMenuEnabledForDisplayMove) {
                        dimensions.appNameMaxWidth -
                            dimensions.expandMenuErrorImageWidth -
                            dimensions.expandMenuErrorImageMargin
                    } else {
                        dimensions.appNameMaxWidth
                    }
            }
            appIconImageView.imageAlpha = foregroundAlpha
            defaultFocusHighlightEnabled = false
        }
        // Minimize button.
        minimizeWindowButton.apply {
            imageTintList = colorStateList
            background =
                createBackgroundDrawable(
                    color = foregroundColor,
                    cornerRadius = dimensions.buttonCornerRadius,
                    drawableInsets = dimensions.minimizeBackgroundInsets,
                )
        }
        // Exit fullscreen button.
        exitFullscreenButtonView.apply {
            setAnimationTints(
                darkMode = header.appTheme == Theme.DARK,
                iconForegroundColor = colorStateList,
                baseForegroundColor = foregroundColor,
                backgroundDrawable =
                    createBackgroundDrawable(
                        color = foregroundColor,
                        cornerRadius = dimensions.buttonCornerRadius,
                        drawableInsets = dimensions.maximizeBackgroundInsets,
                    ),
            )
        }
        // Close button.
        closeWindowButton.apply {
            imageTintList = colorStateList
            background =
                createBackgroundDrawable(
                    color = foregroundColor,
                    cornerRadius = dimensions.buttonCornerRadius,
                    drawableInsets = dimensions.closeBackgroundInsets,
                )
        }
        exitFullscreenButtonView.hoverDisabled = false
        exitFullscreenWindowButton.onLongClickListener = onLongClickListener
    }

    private fun setCaptionVisibility(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        captionView.visibility = v
    }

    override fun onHandleMenuOpened() {}

    override fun onHandleMenuClosed() {}

    fun onExitFullscreenWindowHoverExit() {
        exitFullscreenButtonView.cancelHoverAnimation()
    }

    fun onExitFullscreenWindowHoverEnter() {
        if (FocusTransitionListener.isDisplayLocalIsFocusedMigrationEnabled()) {
            if (!focusTransitionObserver.isFocusedOnDisplay(currentTaskInfo)) return
        } else {
            if (!currentTaskInfo.isFocused) return
        }
        exitFullscreenButtonView.startHoverAnimation()
    }

    fun runOnAppChipGlobalLayout(runnable: () -> Unit) {
        // Wait for app chip to be inflated before notifying repository.
        openMenuButton.post { runnable() }
    }

    fun getAppChipLocationInWindow(): Rect {
        val appChipBoundsInWindow = IntArray(2)
        openMenuButton.getLocationInWindow(appChipBoundsInWindow)

        return Rect(
            /* left = */ appChipBoundsInWindow[0],
            /* top = */ appChipBoundsInWindow[1],
            /* right = */ appChipBoundsInWindow[0] + openMenuButton.width,
            /* bottom = */ appChipBoundsInWindow[1] + openMenuButton.height,
        )
    }

    fun requestAccessibilityFocus() {
        // Slight delay so that after maximizing, everything has settled before resetting focus
        exitFullscreenWindowButton.postDelayed(250) {
            exitFullscreenWindowButton.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        }
    }

    /** Returns the current position of the exit fullscreen button relative to the caption. */
    fun getExitFullscreenButtonPosition(): IntArray {
        val exitFullscreenButtonLocation = IntArray(2)
        exitFullscreenWindowButton.getLocationInWindow(exitFullscreenButtonLocation)
        return exitFullscreenButtonLocation
    }

    private fun getHeaderStyle(header: Header): HeaderStyle {
        return HeaderStyle(
            background = getHeaderBackground(header),
            foreground = getHeaderForeground(header),
        )
    }

    private fun getHeaderBackground(header: Header): HeaderStyle.Background {
        return when (header.appTheme) {
            Theme.LIGHT -> {
                if (header.isFocused) {
                    HeaderStyle.Background.Opaque(lightColors.secondaryContainer.toArgb())
                } else {
                    HeaderStyle.Background.Opaque(lightColors.surfaceContainerLow.toArgb())
                }
            }
            Theme.DARK -> {
                if (header.isFocused) {
                    HeaderStyle.Background.Opaque(darkColors.surfaceContainerHigh.toArgb())
                } else {
                    HeaderStyle.Background.Opaque(darkColors.surfaceDim.toArgb())
                }
            }
        }
    }

    private fun getHeaderForeground(header: Header): HeaderStyle.Foreground {
        return when (header.appTheme) {
            Theme.LIGHT -> {
                if (header.isFocused) {
                    HeaderStyle.Foreground(
                        color = lightColors.onSecondaryContainer.toArgb(),
                        opacity = OPACITY_100,
                    )
                } else {
                    HeaderStyle.Foreground(
                        color = lightColors.onSecondaryContainer.toArgb(),
                        opacity = OPACITY_65,
                    )
                }
            }
            Theme.DARK -> {
                if (header.isFocused) {
                    HeaderStyle.Foreground(
                        color = darkColors.onSurface.toArgb(),
                        opacity = OPACITY_100,
                    )
                } else {
                    HeaderStyle.Foreground(
                        color = darkColors.onSurface.toArgb(),
                        opacity = OPACITY_55,
                    )
                }
            }
        }
    }

    private fun fillHeaderInfo(taskInfo: RunningTaskInfo, hasGlobalFocus: Boolean): Header {
        return Header(
            appTheme = decorThemeUtil.getAppTheme(taskInfo),
            isFocused = hasGlobalFocus,
            isAppearanceCaptionLight = taskInfo.isLightCaptionBarAppearance,
        )
    }

    private data class Header(
        val appTheme: Theme,
        val isFocused: Boolean,
        val isAppearanceCaptionLight: Boolean,
    )

    private data class HeaderStyle(val background: Background, val foreground: Foreground) {
        data class Foreground(@ColorInt val color: Int, val opacity: Int)

        sealed class Background {
            data object Transparent : Background()

            data class Opaque(@ColorInt val color: Int) : Background()
        }
    }

    override fun setTaskFocusState(taskFocusState: Boolean) {
        (rootView as WindowDecorLinearLayout).setTaskFocusState(taskFocusState)
    }

    override fun close() {
        // Should not fire long press events after closing the window decoration.
        exitFullscreenWindowButton.cancelLongPress()
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun toString(): String {
        return "FullscreenHeaderViewHolder(rootView=${rootView.identityHashCode.toHexString()})"
    }

    companion object {
        private const val CLICK_DELAY: Long = 500
    }

    /** Factory for creating [FullscreenHeaderViewHolder] instances. */
    interface Factory {
        fun create(
            context: Context,
            windowDecorationActions: WindowDecorationActions,
            gestureInterceptor: WindowDecorLinearLayout.GestureInterceptor,
            onLongClickListener: OnLongClickListener,
            onCaptionGenericMotionListener: View.OnGenericMotionListener,
            onExitFullscreenHoverAnimationFinishedListener: () -> Unit,
            desktopModeUiEventLogger: DesktopModeUiEventLogger,
            dimensions: HeaderDimensions,
            focusTransitionObserver: FocusTransitionObserver,
        ): FullscreenHeaderViewHolder
    }

    /** The default factory for creating [FullscreenHeaderViewHolder] instances. */
    class DefaultFactory : Factory {
        override fun create(
            context: Context,
            windowDecorationActions: WindowDecorationActions,
            gestureInterceptor: WindowDecorLinearLayout.GestureInterceptor,
            onLongClickListener: OnLongClickListener,
            onCaptionGenericMotionListener: View.OnGenericMotionListener,
            onExitFullscreenHoverAnimationFinishedListener: () -> Unit,
            desktopModeUiEventLogger: DesktopModeUiEventLogger,
            dimensions: HeaderDimensions,
            focusTransitionObserver: FocusTransitionObserver,
        ): FullscreenHeaderViewHolder =
            FullscreenHeaderViewHolder(
                context,
                windowDecorationActions,
                gestureInterceptor,
                onLongClickListener,
                onCaptionGenericMotionListener,
                onExitFullscreenHoverAnimationFinishedListener,
                desktopModeUiEventLogger,
                dimensions,
                focusTransitionObserver,
            )
    }
}
