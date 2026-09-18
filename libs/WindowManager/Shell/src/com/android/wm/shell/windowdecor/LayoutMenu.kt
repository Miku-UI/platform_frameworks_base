/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.ColorInt
import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.os.Bundle
import android.util.StateSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_HOVER_ENTER
import android.view.MotionEvent.ACTION_HOVER_EXIT
import android.view.MotionEvent.ACTION_HOVER_MOVE
import android.view.MotionEvent.ACTION_OUTSIDE
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.view.SurfaceControlViewHost
import android.view.View
import android.view.View.OnClickListener
import android.view.View.OnTouchListener
import android.view.View.SCALE_Y
import android.view.View.TRANSLATION_Y
import android.view.View.TRANSLATION_Z
import android.view.WindowManager
import android.view.WindowlessWindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.widget.Button
import android.widget.TextView
import android.window.TaskConstants
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.animation.addListener
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.android.window.flags.Flags
import com.android.wm.shell.R
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.InputMethod
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.getInputMethodFromMotionEvent
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum.A11Y_MAXIMIZE_MENU_MAXIMIZE
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum.A11Y_MAXIMIZE_MENU_RESIZE_LEFT
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum.A11Y_MAXIMIZE_MENU_RESIZE_RIGHT
import com.android.wm.shell.desktopmode.common.ToggleTaskSizeInteraction.AmbiguousSource
import com.android.wm.shell.desktopmode.isTaskMaximized
import com.android.wm.shell.shared.animation.Interpolators.EMPHASIZED_DECELERATE
import com.android.wm.shell.shared.animation.Interpolators.FAST_OUT_LINEAR_IN
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.windowdecor.additionalviewcontainer.AdditionalViewHostViewContainer
import com.android.wm.shell.windowdecor.common.DecorThemeUtil
import com.android.wm.shell.windowdecor.common.OPACITY_12
import com.android.wm.shell.windowdecor.common.OPACITY_40
import com.android.wm.shell.windowdecor.common.OPACITY_60
import com.android.wm.shell.windowdecor.common.withAlpha
import java.util.function.Supplier

/**
 * Menu that appears when user long clicks the layout button. Gives the user the option to maximize
 * the task or restore previous task bounds from the maximized state and to snap the task to the
 * right or left half of the screen.
 */
class LayoutMenu(
    private val syncQueue: SyncTransactionQueue,
    private val rootTdaOrganizer: RootTaskDisplayAreaOrganizer,
    private val displayController: DisplayController,
    private val windowDecorationActions: WindowDecorationActions,
    private val taskInfo: RunningTaskInfo,
    private val decorWindowContext: Context,
    private val positionSupplier: (Int, Int) -> Point,
    private val transactionSupplier: Supplier<Transaction> = Supplier { Transaction() },
    private val desktopModeUiEventLogger: DesktopModeUiEventLogger,
    private val splitScreenController: SplitScreenController,
) {
    private var layoutMenu: AdditionalViewHostViewContainer? = null
    private var layoutMenuView: LayoutMenuView? = null
    private lateinit var viewHost: SurfaceControlViewHost
    private lateinit var leash: SurfaceControl
    private val cornerRadius =
        loadDimensionPixelSize(R.dimen.desktop_mode_layout_menu_corner_radius).toFloat()
    private lateinit var menuPosition: Point
    private val menuPadding = loadDimensionPixelSize(R.dimen.desktop_mode_menu_padding)

    /** Position the menu relative to the caption's position. */
    fun positionMenu(t: Transaction) {
        menuPosition =
            positionSupplier(
                layoutMenuView?.measureWidth() ?: 0,
                layoutMenuView?.measureHeight() ?: 0,
            )
        t.setPosition(leash, menuPosition.x.toFloat(), menuPosition.y.toFloat())
    }

    /** Creates and shows the maximize window. */
    fun show(
        isTaskInImmersiveMode: Boolean,
        showImmersiveOption: Boolean,
        showSnapOptions: Boolean,
        onHoverListener: (Boolean) -> Unit,
        onOutsideTouchListener: () -> Unit,
        onLayoutMenuClickedListener: () -> Unit,
    ) {
        if (layoutMenu != null) return
        createLayoutMenu(
            isTaskInImmersiveMode = isTaskInImmersiveMode,
            showImmersiveOption = showImmersiveOption,
            showSnapOptions = showSnapOptions,
            windowDecorationActions = windowDecorationActions,
            onHoverListener = onHoverListener,
            onOutsideTouchListener = onOutsideTouchListener,
            onLayoutMenuClickedListener = onLayoutMenuClickedListener,
        )
        layoutMenuView?.let { view ->
            view.animateOpenMenu(onEnd = { view.requestAccessibilityFocus() })
        }
    }

    /** Closes the maximize window and releases its view. */
    fun close(onEnd: () -> Unit) {
        val view = layoutMenuView
        val menu = layoutMenu
        if (view == null) {
            menu?.releaseView()
        } else {
            view.animateCloseMenu(
                onEnd = {
                    menu?.releaseView()
                    onEnd.invoke()
                }
            )
        }
        layoutMenu = null
        layoutMenuView = null
    }

    /** Create a layout menu that is attached to the display area. */
    private fun createLayoutMenu(
        isTaskInImmersiveMode: Boolean,
        showImmersiveOption: Boolean,
        showSnapOptions: Boolean,
        windowDecorationActions: WindowDecorationActions,
        onHoverListener: (Boolean) -> Unit,
        onOutsideTouchListener: () -> Unit,
        onLayoutMenuClickedListener: () -> Unit,
    ) {
        val t = transactionSupplier.get()
        val builder = SurfaceControl.Builder()
        rootTdaOrganizer.attachToDisplayArea(taskInfo.displayId, builder)
        leash = builder.setName("Maximize Menu").setContainerLayer().build()
        val windowManager =
            WindowlessWindowManager(
                taskInfo.configuration,
                leash,
                null, // HostInputToken
            )
        viewHost =
            SurfaceControlViewHost(
                decorWindowContext,
                displayController.getDisplay(taskInfo.displayId),
                windowManager,
                "LayoutMenu",
            )
        layoutMenuView =
            LayoutMenuView(
                    context = decorWindowContext,
                    windowDecorationActions = windowDecorationActions,
                    desktopModeUiEventLogger = desktopModeUiEventLogger,
                    sizeToggleDirection = getSizeToggleDirection(),
                    immersiveConfig =
                        if (showImmersiveOption) {
                            LayoutMenuView.ImmersiveConfig.Visible(
                                getImmersiveToggleDirection(isTaskInImmersiveMode)
                            )
                        } else {
                            LayoutMenuView.ImmersiveConfig.Hidden
                        },
                    showSnapOptions = showSnapOptions,
                    menuPadding = menuPadding,
                    splitScreenController = splitScreenController,
                )
                .also { menuView ->
                    menuView.bind(taskInfo)
                    menuView.onMenuHoverListener = onHoverListener
                    menuView.onOutsideTouchListener = onOutsideTouchListener
                    menuView.onLayoutMenuClickedListener = onLayoutMenuClickedListener
                    val menuWidth = menuView.measureWidth()
                    val menuHeight = menuView.measureHeight()
                    menuPosition = positionSupplier(menuWidth, menuHeight)
                    val lp =
                        WindowManager.LayoutParams(
                            menuWidth,
                            menuHeight,
                            WindowManager.LayoutParams.TYPE_APPLICATION,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                            PixelFormat.TRANSPARENT,
                        )
                    lp.title = "Maximize Menu for Task=" + taskInfo.taskId
                    lp.setTrustedOverlay()
                    viewHost.setView(menuView.rootView, lp)
                }

        // Bring menu to front when open
        t.setLayer(leash, TaskConstants.TASK_CHILD_LAYER_FLOATING_MENU)
            .setPosition(leash, menuPosition.x.toFloat(), menuPosition.y.toFloat())
            .setCornerRadius(leash, cornerRadius)
            .show(leash)
        layoutMenu = AdditionalViewHostViewContainer(leash, viewHost, transactionSupplier)

        syncQueue.runInSync { transaction ->
            transaction.merge(t)
            t.close()
        }
    }

    private fun getSizeToggleDirection(): LayoutMenuView.SizeToggleDirection {
        val displayId = taskInfo.displayId
        val displayLayout =
            displayController.getDisplayLayout(displayId)
                ?: error("Could not get display layout for display=$displayId")
        val maximized = isTaskMaximized(taskInfo, displayLayout)
        return if (maximized) LayoutMenuView.SizeToggleDirection.RESTORE
        else LayoutMenuView.SizeToggleDirection.MAXIMIZE
    }

    private fun getImmersiveToggleDirection(
        isTaskImmersive: Boolean
    ): LayoutMenuView.ImmersiveToggleDirection =
        if (isTaskImmersive) {
            LayoutMenuView.ImmersiveToggleDirection.EXIT
        } else {
            LayoutMenuView.ImmersiveToggleDirection.ENTER
        }

    private fun loadDimensionPixelSize(resourceId: Int): Int {
        return if (resourceId == Resources.ID_NULL) {
            0
        } else {
            decorWindowContext.resources.getDimensionPixelSize(resourceId)
        }
    }

    /**
     * The view within the Layout Menu, presents maximize, restore and snap-to-side options for
     * resizing a Task.
     */
    class LayoutMenuView(
        context: Context,
        private val windowDecorationActions: WindowDecorationActions,
        private val desktopModeUiEventLogger: DesktopModeUiEventLogger,
        private val sizeToggleDirection: SizeToggleDirection,
        immersiveConfig: ImmersiveConfig,
        showSnapOptions: Boolean,
        private val menuPadding: Int,
        private val splitScreenController: SplitScreenController,
    ) : OnClickListener, OnTouchListener {
        val rootView =
            LayoutInflater.from(context)
                .inflate(
                    if (Flags.enableConsolidatedWindowOptions())
                        R.layout.desktop_mode_window_decor_layout_menu
                    else R.layout.desktop_mode_window_decor_layout_menu_legacy,
                    null, /* root */
                ) as CaptionMenuLayout
        private val container = requireViewById(R.id.container)
        private val immersiveToggleContainer =
            requireViewById(R.id.layout_menu_immersive_toggle_container) as View
        private val immersiveToggleButtonText =
            requireViewById(R.id.layout_menu_immersive_toggle_button_text) as TextView
        private val immersiveToggleButton =
            requireViewById(R.id.layout_menu_immersive_toggle_button) as Button
        private val sizeToggleContainer =
            requireViewById(R.id.layout_menu_size_toggle_container) as View
        private val sizeToggleButtonText =
            requireViewById(R.id.layout_menu_size_toggle_button_text) as TextView
        private val sizeToggleButton =
            requireViewById(R.id.layout_menu_size_toggle_button) as Button
        private val snapContainer = requireViewById(R.id.layout_menu_snap_container) as View
        private val snapWindowText = requireViewById(R.id.layout_menu_snap_window_text) as TextView
        private val snapButtonsLayout = requireViewById(R.id.layout_menu_snap_menu_layout)
        private val windowingPillView =
            if (Flags.enableConsolidatedWindowOptions())
                requireViewById(R.id.windowing_pill) as WindowingPillView
            else null

        // If layout direction is RTL, layout menu will be mirrored, switching the order of the
        // snap right/left buttons.
        val isRtl: Boolean =
            (context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL)
        private val snapRightButton =
            if (isRtl) {
                requireViewById(R.id.layout_menu_snap_left_button) as Button
            } else {
                requireViewById(R.id.layout_menu_snap_right_button) as Button
            }
        private val snapLeftButton =
            if (isRtl) {
                requireViewById(R.id.layout_menu_snap_right_button) as Button
            } else {
                requireViewById(R.id.layout_menu_snap_left_button) as Button
            }

        private val menuButtons =
            listOf(snapLeftButton, snapRightButton, immersiveToggleButton, sizeToggleButton)

        private val decorThemeUtil = DecorThemeUtil(context)

        private val outlineRadius =
            context.resources.getDimensionPixelSize(
                R.dimen.desktop_mode_layout_menu_buttons_outline_radius
            )
        private val outlineStroke =
            context.resources.getDimensionPixelSize(
                R.dimen.desktop_mode_layout_menu_buttons_outline_stroke
            )
        private val fillRadius =
            context.resources.getDimensionPixelSize(
                R.dimen.desktop_mode_layout_menu_buttons_fill_radius
            )

        private val immersiveFillPadding =
            context.resources.getDimensionPixelSize(
                R.dimen.desktop_mode_layout_menu_immersive_button_fill_padding
            )
        private val maximizeFillPaddingDefault =
            context.resources.getDimensionPixelSize(
                R.dimen.desktop_mode_layout_menu_snap_and_maximize_buttons_fill_padding
            )
        private val maximizeRestoreFillPaddingVertical =
            context.resources.getDimensionPixelSize(
                R.dimen.desktop_mode_layout_menu_restore_button_fill_vertical_padding
            )
        private val maximizeRestoreFillPaddingHorizontal =
            context.resources.getDimensionPixelSize(
                R.dimen.desktop_mode_layout_menu_restore_button_fill_horizontal_padding
            )
        private val maximizeFillPaddingRect =
            Rect(
                maximizeFillPaddingDefault,
                maximizeFillPaddingDefault,
                maximizeFillPaddingDefault,
                maximizeFillPaddingDefault,
            )
        private val maximizeRestoreFillPaddingRect =
            Rect(
                maximizeRestoreFillPaddingHorizontal,
                maximizeRestoreFillPaddingVertical,
                maximizeRestoreFillPaddingHorizontal,
                maximizeRestoreFillPaddingVertical,
            )
        private val immersiveFillPaddingRect =
            Rect(
                immersiveFillPadding,
                immersiveFillPadding,
                immersiveFillPadding,
                immersiveFillPadding,
            )

        private val hoverTempRect = Rect()
        private var menuAnimatorSet: AnimatorSet? = null
        private lateinit var taskInfo: RunningTaskInfo
        private lateinit var style: MenuStyle
        /** The last input method used to interact with the menu's view. */
        private var lastInputMethod: InputMethod = InputMethod.UNKNOWN_INPUT_METHOD

        /** Invoked whenever the hover state of the menu changes. */
        var onMenuHoverListener: ((Boolean) -> Unit)? = null
        /** Invoked whenever a click occurs outside the menu */
        var onOutsideTouchListener: (() -> Unit)? = null
        /** Invoked whenever a click occurs outside the menu */
        var onLayoutMenuClickedListener: (() -> Unit)? = null

        init {
            rootView.onInterceptHoverListener = { event ->
                // The overlay covers the entire menu, so it's a convenient way to monitor whether
                // the menu is hovered as a whole or not.
                when (event.action) {
                    ACTION_HOVER_ENTER -> onMenuHoverListener?.invoke(true)
                    ACTION_HOVER_EXIT -> onMenuHoverListener?.invoke(false)
                }

                // Also check if the hover falls within the snap options layout, to manually
                // set the left/right state based on the event's position.
                // TODO(b/346440693): this manual hover tracking is needed for left/right snap
                //  because its view/background(s) don't support selector states. Look into whether
                //  that can be added to avoid manual tracking. Also because these button
                //  colors/state logic is only being applied on hover events, but there's pressed,
                //  focused and selected states that should be responsive too.
                val snapLayoutBoundsRelToOverlay =
                    hoverTempRect.also { rect ->
                        snapButtonsLayout.getDrawingRect(rect)
                        rootView.offsetDescendantRectToMyCoords(snapButtonsLayout, rect)
                    }
                if (event.action == ACTION_HOVER_ENTER || event.action == ACTION_HOVER_MOVE) {
                    if (snapLayoutBoundsRelToOverlay.contains(event.x.toInt(), event.y.toInt())) {
                        // Hover is inside the snap layout, anything left of center is the left
                        // snap, and anything right of center is right snap.
                        val layoutCenter = snapLayoutBoundsRelToOverlay.centerX()
                        if (event.x < layoutCenter) {
                            updateSplitSnapSelection(SnapToHalfSelection.LEFT)
                        } else {
                            updateSplitSnapSelection(SnapToHalfSelection.RIGHT)
                        }
                    } else {
                        // Any other hover is outside the snap layout, so neither is selected.
                        updateSplitSnapSelection(SnapToHalfSelection.NONE)
                    }
                }
            }

            immersiveToggleContainer.isGone = immersiveConfig is ImmersiveConfig.Hidden
            sizeToggleContainer.isVisible = true
            snapContainer.isGone = !showSnapOptions

            menuButtons.forEach {
                it.setOnClickListener(this)
                it.setOnTouchListener(this)
            }

            rootView.setOnTouchListener { _, event ->
                if (event.actionMasked == ACTION_OUTSIDE) {
                    onOutsideTouchListener?.invoke()
                    return@setOnTouchListener false
                }
                true
            }

            sizeToggleButton.accessibilityDelegate =
                object : View.AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfo,
                    ) {

                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.addAction(
                            AccessibilityAction(
                                AccessibilityAction.ACTION_CLICK.id,
                                context.getString(
                                    R.string.layout_menu_talkback_action_maximize_restore_text
                                ),
                            )
                        )
                        host.isClickable = true
                    }

                    override fun performAccessibilityAction(
                        host: View,
                        action: Int,
                        args: Bundle?,
                    ): Boolean {
                        if (action == AccessibilityAction.ACTION_CLICK.id) {
                            desktopModeUiEventLogger.log(taskInfo, A11Y_MAXIMIZE_MENU_MAXIMIZE)
                            windowDecorationActions.onMaximizeOrRestore(
                                taskInfo.taskId,
                                AmbiguousSource.MAXIMIZE_MENU,
                                InputMethod.ACCESSIBILITY,
                            )
                        }
                        return super.performAccessibilityAction(host, action, args)
                    }
                }

            snapLeftButton.accessibilityDelegate =
                object : View.AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfo,
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.addAction(
                            AccessibilityAction(
                                AccessibilityAction.ACTION_CLICK.id,
                                context.getString(
                                    R.string.layout_menu_talkback_action_snap_left_text
                                ),
                            )
                        )
                        host.isClickable = true
                    }

                    override fun performAccessibilityAction(
                        host: View,
                        action: Int,
                        args: Bundle?,
                    ): Boolean {
                        if (action == AccessibilityAction.ACTION_CLICK.id) {
                            desktopModeUiEventLogger.log(taskInfo, A11Y_MAXIMIZE_MENU_RESIZE_LEFT)
                            windowDecorationActions.onLeftSnap(
                                taskInfo.taskId,
                                InputMethod.ACCESSIBILITY,
                            )
                        }
                        return super.performAccessibilityAction(host, action, args)
                    }
                }

            snapRightButton.accessibilityDelegate =
                object : View.AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfo,
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.addAction(
                            AccessibilityAction(
                                AccessibilityAction.ACTION_CLICK.id,
                                context.getString(
                                    R.string.layout_menu_talkback_action_snap_right_text
                                ),
                            )
                        )
                        host.isClickable = true
                    }

                    override fun performAccessibilityAction(
                        host: View,
                        action: Int,
                        args: Bundle?,
                    ): Boolean {
                        if (action == AccessibilityAction.ACTION_CLICK.id) {
                            desktopModeUiEventLogger.log(taskInfo, A11Y_MAXIMIZE_MENU_RESIZE_RIGHT)
                            windowDecorationActions.onRightSnap(
                                taskInfo.taskId,
                                InputMethod.ACCESSIBILITY,
                            )
                        }
                        return super.performAccessibilityAction(host, action, args)
                    }
                }

            // Maximize/restore button.
            val sizeToggleBtnTextId =
                if (sizeToggleDirection == SizeToggleDirection.RESTORE)
                    R.string.desktop_mode_layout_menu_restore_button_text
                else R.string.desktop_mode_layout_menu_maximize_button_text
            val sizeToggleBtnText = context.resources.getText(sizeToggleBtnTextId)
            sizeToggleButton.contentDescription = sizeToggleBtnText
            sizeToggleButtonText.text = sizeToggleBtnText

            val sizeToggleBtnA11yTextId =
                if (sizeToggleDirection == SizeToggleDirection.RESTORE)
                    R.string.app_header_talkback_action_restore_button_text
                else R.string.app_header_talkback_action_maximize_button_text
            val sizeToggleBtnA11yText = context.resources.getText(sizeToggleBtnA11yTextId)
            ViewCompat.replaceAccessibilityAction(
                sizeToggleButton,
                AccessibilityActionCompat.ACTION_CLICK,
                sizeToggleBtnA11yText,
                null,
            )

            // Immersive enter/exit button.
            if (immersiveConfig is ImmersiveConfig.Visible) {
                val immersiveToggleBtnTextId =
                    when (immersiveConfig.direction) {
                        ImmersiveToggleDirection.ENTER -> {
                            R.string.desktop_mode_layout_menu_immersive_button_text
                        }

                        ImmersiveToggleDirection.EXIT -> {
                            R.string.desktop_mode_layout_menu_immersive_restore_button_text
                        }
                    }
                val immersiveToggleBtnText = context.resources.getText(immersiveToggleBtnTextId)
                immersiveToggleButton.contentDescription = immersiveToggleBtnText
                immersiveToggleButtonText.text = immersiveToggleBtnText

                val immersiveToggleBtnA11yTextId =
                    when (immersiveConfig.direction) {
                        ImmersiveToggleDirection.ENTER -> {
                            R.string.layout_menu_talkback_action_immersive_enter_text
                        }

                        ImmersiveToggleDirection.EXIT -> {
                            R.string.layout_menu_talkback_action_immersive_restore_text
                        }
                    }
                val immersiveToggleBtnA11yText =
                    context.resources.getText(immersiveToggleBtnA11yTextId)
                ViewCompat.replaceAccessibilityAction(
                    immersiveToggleButton,
                    AccessibilityActionCompat.ACTION_CLICK,
                    immersiveToggleBtnA11yText,
                    null,
                )
            }

            // To prevent aliasing.
            sizeToggleButton.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            sizeToggleButtonText.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            immersiveToggleButton.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            immersiveToggleButtonText.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

            if (Flags.enableConsolidatedWindowOptions()) {
                requireWindowingPillView()
                    .initialize(
                        windowDecorationActions,
                        desktopModeUiEventLogger,
                        /* shouldShowDesktopModeButton= */ true,
                        onPillItemClicked = { onLayoutMenuClickedListener?.invoke() },
                    )
            }
        }

        override fun onClick(v: View) {
            when (v.id) {
                R.id.layout_menu_immersive_toggle_button -> {
                    windowDecorationActions.onImmersiveOrRestore(taskInfo)
                }
                R.id.layout_menu_size_toggle_button -> {
                    windowDecorationActions.onMaximizeOrRestore(
                        taskInfo.taskId,
                        AmbiguousSource.MAXIMIZE_MENU,
                        lastInputMethod,
                    )
                }
                R.id.layout_menu_snap_right_button -> {
                    windowDecorationActions.onRightSnap(taskInfo.taskId, lastInputMethod)
                }
                R.id.layout_menu_snap_left_button -> {
                    windowDecorationActions.onLeftSnap(taskInfo.taskId, lastInputMethod)
                }
            }
            onLayoutMenuClickedListener?.invoke()
        }

        override fun onTouch(v: View, ev: MotionEvent): Boolean {
            lastInputMethod = getInputMethodFromMotionEvent(ev)
            return false
        }

        /** Bind the menu views to the new [RunningTaskInfo] data. */
        fun bind(taskInfo: RunningTaskInfo) {
            this.taskInfo = taskInfo
            this.style = calculateMenuStyle(taskInfo)

            if (Flags.enableConsolidatedWindowOptions()) {
                container.background.setTint(style.backgroundColor)
            } else {
                rootView.background.setTint(style.backgroundColor)
            }

            // Maximize option.
            sizeToggleButton.background = style.maximizeOption.drawable
            sizeToggleButtonText.setTextColor(style.textColor)

            // Immersive option.
            immersiveToggleButton.background = style.immersiveOption.drawable
            immersiveToggleButtonText.setTextColor(style.textColor)

            // Snap options.
            snapWindowText.setTextColor(style.textColor)
            updateSplitSnapSelection(SnapToHalfSelection.NONE)

            if (Flags.enableConsolidatedWindowOptions()) {
                val isInSplitScreen = splitScreenController.isTaskInSplitScreen(taskInfo.taskId)
                requireWindowingPillView().bind(taskInfo, isInSplitScreen)
                requireWindowingPillView().background.setTint(style.backgroundColor)
            }
        }

        /** Animate the opening of the menu */
        fun animateOpenMenu(onEnd: () -> Unit) {
            val animatingControls =
                mutableListOf<View>(
                    sizeToggleButton,
                    sizeToggleButtonText,
                    immersiveToggleButton,
                    immersiveToggleButtonText,
                    snapButtonsLayout,
                    snapWindowText,
                )
            if (Flags.enableConsolidatedWindowOptions()) {
                animatingControls.addAll(requireWindowingPillView().children.toList())
            }
            animatingControls.forEach { view ->
                view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                // Alpha animation is delayed by [CONTROLS_ALPHA_OPEN_MENU_ANIMATION_DELAY_MS]. So
                // we here reset the alpha value to 0, so that the animation can start from 0 and
                // fade in.
                view.alpha = 0.0f
            }
            menuAnimatorSet = AnimatorSet()
            val animators =
                mutableListOf<Animator>(
                    ValueAnimator.ofFloat(STARTING_MENU_HEIGHT_SCALE, 1f).apply {
                        duration = OPEN_MENU_HEIGHT_ANIMATION_DURATION_MS
                        interpolator = EMPHASIZED_DECELERATE
                        addUpdateListener {
                            // Animate padding so that controls stay pinned to the bottom of
                            // the menu.
                            val value = animatedValue as Float
                            val topPadding = menuPadding - ((1 - value) * measureHeight()).toInt()
                            container.setPadding(menuPadding, topPadding, menuPadding, menuPadding)
                        }
                    },
                    ValueAnimator.ofFloat(1 / STARTING_MENU_HEIGHT_SCALE, 1f).apply {
                        duration = OPEN_MENU_HEIGHT_ANIMATION_DURATION_MS
                        interpolator = EMPHASIZED_DECELERATE
                        addUpdateListener {
                            // Scale up the children of the layout menu so that the menu
                            // scale is cancelled out and only the background is scaled.
                            val value = animatedValue as Float
                            animatingControls.forEach { view -> view.scaleY = value }
                        }
                    },
                    ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = ALPHA_ANIMATION_DURATION_MS
                        startDelay = CONTROLS_ALPHA_OPEN_MENU_ANIMATION_DELAY_MS
                        addUpdateListener {
                            val value = animatedValue as Float
                            animatingControls.forEach { view -> view.alpha = value }
                        }
                    },
                )

            val animatingContainers = mutableListOf<View>()
            if (Flags.enableConsolidatedWindowOptions()) {
                animatingContainers.add(container)
                animatingContainers.add(requireWindowingPillView())
            } else {
                animatingContainers.add(rootView)
            }
            animatingContainers.forEach { view ->
                animators.add(
                    ObjectAnimator.ofFloat(view, SCALE_Y, STARTING_MENU_HEIGHT_SCALE, 1f).apply {
                        duration = OPEN_MENU_HEIGHT_ANIMATION_DURATION_MS
                        interpolator = EMPHASIZED_DECELERATE
                    }
                )
                animators.add(
                    ObjectAnimator.ofFloat(
                            view,
                            TRANSLATION_Y,
                            (STARTING_MENU_HEIGHT_SCALE - 1) * view.measureHeight(),
                            0f,
                        )
                        .apply {
                            duration = OPEN_MENU_HEIGHT_ANIMATION_DURATION_MS
                            interpolator = EMPHASIZED_DECELERATE
                        }
                )
                animators.add(
                    ObjectAnimator.ofInt(view.background, "alpha", 0, MAX_DRAWABLE_ALPHA_VALUE)
                        .apply { duration = ALPHA_ANIMATION_DURATION_MS }
                )
                animators.add(
                    ObjectAnimator.ofFloat(view, TRANSLATION_Z, 0f, MENU_Z_TRANSLATION).apply {
                        duration = ELEVATION_ANIMATION_DURATION_MS
                        startDelay = CONTROLS_ALPHA_OPEN_MENU_ANIMATION_DELAY_MS
                    }
                )
            }

            menuAnimatorSet?.playTogether(animators)
            menuAnimatorSet?.addListener(
                onEnd = {
                    animatingControls.forEach { view ->
                        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    }
                    onEnd.invoke()
                }
            )
            menuAnimatorSet?.start()
        }

        /** Animate the closing of the menu */
        fun animateCloseMenu(onEnd: (() -> Unit)) {
            val animatingControls =
                mutableListOf<View>(
                    sizeToggleButton,
                    sizeToggleButtonText,
                    immersiveToggleButton,
                    immersiveToggleButtonText,
                    snapButtonsLayout,
                    snapWindowText,
                )
            if (Flags.enableConsolidatedWindowOptions()) {
                animatingControls.addAll(requireWindowingPillView().children.toList())
            }
            animatingControls.forEach { view -> view.setLayerType(View.LAYER_TYPE_HARDWARE, null) }
            cancelAnimation()
            menuAnimatorSet = AnimatorSet()
            val animators =
                mutableListOf<Animator>(
                    ValueAnimator.ofFloat(1f, STARTING_MENU_HEIGHT_SCALE).apply {
                        duration = CLOSE_MENU_HEIGHT_ANIMATION_DURATION_MS
                        interpolator = FAST_OUT_LINEAR_IN
                        addUpdateListener {
                            // Animate padding so that controls stay pinned to the bottom of
                            // the menu.
                            val value = animatedValue as Float
                            val topPadding = menuPadding - ((1 - value) * measureHeight()).toInt()
                            container.setPadding(menuPadding, topPadding, menuPadding, menuPadding)
                        }
                    },
                    ValueAnimator.ofFloat(1f, 1 / STARTING_MENU_HEIGHT_SCALE).apply {
                        duration = CLOSE_MENU_HEIGHT_ANIMATION_DURATION_MS
                        interpolator = FAST_OUT_LINEAR_IN
                        addUpdateListener {
                            // Scale up the children of the layout menu so that the menu
                            // scale is cancelled out and only the background is scaled.
                            val value = animatedValue as Float
                            animatingControls.forEach { view -> view.scaleY = value }
                        }
                    },
                    ValueAnimator.ofFloat(1f, 0f).apply {
                        duration = ALPHA_ANIMATION_DURATION_MS
                        addUpdateListener {
                            val value = animatedValue as Float
                            animatingControls.forEach { view -> view.alpha = value }
                        }
                    },
                )

            val animatingContainers = mutableListOf<View>()
            if (Flags.enableConsolidatedWindowOptions()) {
                animatingContainers.add(container)
                animatingContainers.add(requireWindowingPillView())
            } else {
                animatingContainers.add(rootView)
            }
            animatingContainers.forEach { view ->
                animators.add(
                    ObjectAnimator.ofFloat(view, SCALE_Y, 1f, STARTING_MENU_HEIGHT_SCALE).apply {
                        duration = CLOSE_MENU_HEIGHT_ANIMATION_DURATION_MS
                        interpolator = FAST_OUT_LINEAR_IN
                    }
                )
                animators.add(
                    ObjectAnimator.ofFloat(
                            view,
                            TRANSLATION_Y,
                            0f,
                            (STARTING_MENU_HEIGHT_SCALE - 1) * view.measureHeight(),
                        )
                        .apply {
                            duration = CLOSE_MENU_HEIGHT_ANIMATION_DURATION_MS
                            interpolator = FAST_OUT_LINEAR_IN
                        }
                )
                animators.add(
                    ObjectAnimator.ofInt(view.background, "alpha", MAX_DRAWABLE_ALPHA_VALUE, 0)
                        .apply {
                            startDelay = CONTAINER_ALPHA_CLOSE_MENU_ANIMATION_DELAY_MS
                            duration = ALPHA_ANIMATION_DURATION_MS
                        }
                )
                animators.add(
                    ObjectAnimator.ofFloat(view, TRANSLATION_Z, MENU_Z_TRANSLATION, 0f).apply {
                        duration = ELEVATION_ANIMATION_DURATION_MS
                    }
                )
            }

            menuAnimatorSet?.playTogether(animators)
            menuAnimatorSet?.addListener(
                onEnd = {
                    animatingControls.forEach { view ->
                        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    }
                    onEnd?.invoke()
                }
            )
            menuAnimatorSet?.start()
        }

        /** Request that the accessibility service focus on the menu. */
        fun requestAccessibilityFocus() {
            // Focus the first button in the menu by default.
            if (immersiveToggleContainer.isVisible) {
                immersiveToggleButton.post {
                    immersiveToggleButton.sendAccessibilityEvent(
                        AccessibilityEvent.TYPE_VIEW_FOCUSED
                    )
                }
                return
            }
            sizeToggleButton.post {
                sizeToggleButton.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
            }
        }

        /** Cancel the menu animation. */
        private fun cancelAnimation() {
            menuAnimatorSet?.cancel()
        }

        /** Update the view state to a new snap to half selection. */
        private fun updateSplitSnapSelection(selection: SnapToHalfSelection) {
            when (selection) {
                SnapToHalfSelection.NONE -> deactivateSnapOptions()
                SnapToHalfSelection.LEFT -> activateSnapOption(activateLeft = true)
                SnapToHalfSelection.RIGHT -> activateSnapOption(activateLeft = false)
            }
        }

        private fun calculateMenuStyle(taskInfo: RunningTaskInfo): MenuStyle {
            val colorScheme = decorThemeUtil.getColorScheme(taskInfo)
            val menuBackgroundColor = colorScheme.surfaceContainerLow.toArgb()
            return MenuStyle(
                backgroundColor = menuBackgroundColor,
                textColor = colorScheme.onSurface.toArgb(),
                maximizeOption =
                    MenuStyle.MaximizeOption(
                        drawable =
                            createMaximizeOrImmersiveDrawable(
                                menuBackgroundColor,
                                colorScheme,
                                fillPadding =
                                    when (sizeToggleDirection) {
                                        SizeToggleDirection.MAXIMIZE -> maximizeFillPaddingRect
                                        SizeToggleDirection.RESTORE ->
                                            maximizeRestoreFillPaddingRect
                                    },
                            )
                    ),
                immersiveOption =
                    MenuStyle.ImmersiveOption(
                        drawable =
                            createMaximizeOrImmersiveDrawable(
                                menuBackgroundColor,
                                colorScheme,
                                fillPadding = immersiveFillPaddingRect,
                            )
                    ),
                snapOptions =
                    MenuStyle.SnapOptions(
                        inactiveSnapSideColor = colorScheme.outlineVariant.toArgb(),
                        semiActiveSnapSideColor =
                            colorScheme.primary.toArgb().withAlpha(OPACITY_40),
                        activeSnapSideColor = colorScheme.primary.toArgb(),
                        inactiveStrokeColor =
                            colorScheme.outlineVariant.toArgb().withAlpha(OPACITY_60),
                        activeStrokeColor = colorScheme.primary.toArgb(),
                        inactiveBackgroundColor = menuBackgroundColor,
                        activeBackgroundColor = colorScheme.primary.toArgb().withAlpha(OPACITY_12),
                    ),
            )
        }

        /** Measure width of the root view of this menu. */
        fun measureWidth(): Int {
            rootView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            return rootView.measuredWidth
        }

        /** Measure height of the root view of this menu. */
        fun measureHeight(): Int {
            return rootView.measureHeight()
        }

        private fun View.measureHeight(): Int {
            measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            return measuredHeight
        }

        private fun deactivateSnapOptions() {
            // TODO(b/346440693): the background/colorStateList set on these buttons is overridden
            //  to a static resource & color on manually tracked hover events, which defeats the
            //  point of state lists and selector states. Look into whether changing that is
            //  possible, similar to the maximize option. Also to include support for the
            //  semi-active state (when the "other" snap option is selected).
            val snapSideColorList =
                ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_pressed),
                        intArrayOf(android.R.attr.state_focused),
                        intArrayOf(android.R.attr.state_selected),
                        intArrayOf(),
                    ),
                    intArrayOf(
                        style.snapOptions.activeSnapSideColor,
                        style.snapOptions.activeSnapSideColor,
                        style.snapOptions.activeSnapSideColor,
                        style.snapOptions.inactiveSnapSideColor,
                    ),
                )
            snapLeftButton.background?.setTintList(snapSideColorList)
            snapRightButton.background?.setTintList(snapSideColorList)
            with(snapButtonsLayout) {
                setBackgroundResource(R.drawable.desktop_mode_layout_menu_layout_background)
                (background as GradientDrawable).apply {
                    setColor(style.snapOptions.inactiveBackgroundColor)
                    setStroke(outlineStroke, style.snapOptions.inactiveStrokeColor)
                }
            }
        }

        private fun activateSnapOption(activateLeft: Boolean) {
            // Regardless of which side is active, the background of the snap options layout (that
            // includes both sides) is considered "active".
            with(snapButtonsLayout) {
                setBackgroundResource(
                    R.drawable.desktop_mode_layout_menu_layout_background_on_hover
                )
                (background as GradientDrawable).apply {
                    setColor(style.snapOptions.activeBackgroundColor)
                    setStroke(outlineStroke, style.snapOptions.activeStrokeColor)
                }
            }
            if (activateLeft) {
                // Highlight snap left button, partially highlight the other side.
                snapLeftButton.background.setTint(style.snapOptions.activeSnapSideColor)
                snapRightButton.background.setTint(style.snapOptions.semiActiveSnapSideColor)
            } else {
                // Highlight snap right button, partially highlight the other side.
                snapRightButton.background.setTint(style.snapOptions.activeSnapSideColor)
                snapLeftButton.background.setTint(style.snapOptions.semiActiveSnapSideColor)
            }
        }

        private fun createMaximizeOrImmersiveDrawable(
            @ColorInt menuBackgroundColor: Int,
            colorScheme: ColorScheme,
            fillPadding: Rect,
        ): StateListDrawable {
            val activeStrokeAndFill = colorScheme.primary.toArgb()
            val activeBackground = colorScheme.primary.toArgb().withAlpha(OPACITY_12)
            val activeDrawable =
                createMaximizeOrImmersiveButtonDrawable(
                    strokeColor = activeStrokeAndFill,
                    fillColor = activeStrokeAndFill,
                    backgroundColor = activeBackground,
                    // Add a mask with the menu background's color because the active background
                    // color is
                    // semi transparent, otherwise the transparency will reveal the stroke/fill
                    // color
                    // behind it.
                    backgroundMask = menuBackgroundColor,
                    fillPadding = fillPadding,
                )
            return StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), activeDrawable)
                addState(intArrayOf(android.R.attr.state_focused), activeDrawable)
                addState(intArrayOf(android.R.attr.state_selected), activeDrawable)
                addState(intArrayOf(android.R.attr.state_hovered), activeDrawable)
                // Inactive drawable.
                addState(
                    StateSet.WILD_CARD,
                    createMaximizeOrImmersiveButtonDrawable(
                        strokeColor = colorScheme.outlineVariant.toArgb().withAlpha(OPACITY_60),
                        fillColor = colorScheme.outlineVariant.toArgb(),
                        backgroundColor = colorScheme.surfaceContainerLow.toArgb(),
                        backgroundMask = null, // not needed because the bg color is fully opaque
                        fillPadding = fillPadding,
                    ),
                )
            }
        }

        private fun createMaximizeOrImmersiveButtonDrawable(
            @ColorInt strokeColor: Int,
            @ColorInt fillColor: Int,
            @ColorInt backgroundColor: Int,
            @ColorInt backgroundMask: Int?,
            fillPadding: Rect,
        ): LayerDrawable {
            val layers = mutableListOf<Drawable>()
            // First (bottom) layer, effectively the button's border ring once its inner shape is
            // covered by the next layers.
            layers.add(
                ShapeDrawable().apply {
                    shape =
                        RoundRectShape(
                            FloatArray(8) { outlineRadius.toFloat() },
                            null /* inset */,
                            null, /* innerRadii */
                        )
                    paint.color = strokeColor
                    paint.style = Paint.Style.FILL
                }
            )
            // Second layer, a mask for the next (background) layer if needed because of
            // transparency.
            backgroundMask?.let { color ->
                layers.add(
                    ShapeDrawable().apply {
                        shape =
                            RoundRectShape(
                                FloatArray(8) { outlineRadius.toFloat() },
                                null /* inset */,
                                null, /* innerRadii */
                            )
                        paint.color = color
                        paint.style = Paint.Style.FILL
                    }
                )
            }
            // Third layer, the "background" padding between the border and the fill.
            layers.add(
                ShapeDrawable().apply {
                    shape =
                        RoundRectShape(
                            FloatArray(8) { outlineRadius.toFloat() },
                            null /* inset */,
                            null, /* innerRadii */
                        )
                    paint.color = backgroundColor
                    paint.style = Paint.Style.FILL
                }
            )
            // Final layer, the inner most rounded-rect "fill".
            layers.add(
                ShapeDrawable().apply {
                    shape =
                        RoundRectShape(
                            FloatArray(8) { fillRadius.toFloat() },
                            null /* inset */,
                            null, /* innerRadii */
                        )
                    paint.color = fillColor
                    paint.style = Paint.Style.FILL
                }
            )

            return LayerDrawable(layers.toTypedArray()).apply {
                when (numberOfLayers) {
                    3 -> {
                        setLayerInset(1, outlineStroke)
                        setLayerInset(
                            2,
                            fillPadding.left,
                            fillPadding.top,
                            fillPadding.right,
                            fillPadding.bottom,
                        )
                    }
                    4 -> {
                        setLayerInset(intArrayOf(1, 2), outlineStroke)
                        setLayerInset(
                            3,
                            fillPadding.left,
                            fillPadding.top,
                            fillPadding.right,
                            fillPadding.bottom,
                        )
                    }
                    else -> error("Unexpected number of layers: $numberOfLayers")
                }
            }
        }

        private fun LayerDrawable.setLayerInset(index: IntArray, inset: Int) {
            for (i in index) {
                setLayerInset(i, inset, inset, inset, inset)
            }
        }

        private fun LayerDrawable.setLayerInset(index: Int, inset: Int) {
            setLayerInset(index, inset, inset, inset, inset)
        }

        private fun requireViewById(id: Int) = rootView.requireViewById<View>(id)

        private fun requireWindowingPillView() =
            checkNotNull(windowingPillView) {
                "windowingPillView should not be null with flag enabled"
            }

        /** The style to apply to the menu. */
        data class MenuStyle(
            @ColorInt val backgroundColor: Int,
            @ColorInt val textColor: Int,
            val maximizeOption: MaximizeOption,
            val immersiveOption: ImmersiveOption,
            val snapOptions: SnapOptions,
        ) {
            data class MaximizeOption(val drawable: StateListDrawable)

            data class ImmersiveOption(val drawable: StateListDrawable)

            data class SnapOptions(
                @ColorInt val inactiveSnapSideColor: Int,
                @ColorInt val semiActiveSnapSideColor: Int,
                @ColorInt val activeSnapSideColor: Int,
                @ColorInt val inactiveStrokeColor: Int,
                @ColorInt val activeStrokeColor: Int,
                @ColorInt val inactiveBackgroundColor: Int,
                @ColorInt val activeBackgroundColor: Int,
            )
        }

        /** The possible selection states of the half-snap menu option. */
        enum class SnapToHalfSelection {
            NONE,
            LEFT,
            RIGHT,
        }

        /** The possible immersive configs for this menu instance. */
        sealed class ImmersiveConfig {
            data class Visible(val direction: ImmersiveToggleDirection) : ImmersiveConfig()

            data object Hidden : ImmersiveConfig()
        }

        /** The possible selection states of the size toggle button in the layout menu. */
        enum class SizeToggleDirection {
            MAXIMIZE,
            RESTORE,
        }

        /** The possible selection states of the immersive toggle button in the layout menu. */
        enum class ImmersiveToggleDirection {
            ENTER,
            EXIT,
        }
    }

    companion object {
        // Open menu animation constants
        private const val ALPHA_ANIMATION_DURATION_MS = 50L
        private const val MAX_DRAWABLE_ALPHA_VALUE = 255
        private const val STARTING_MENU_HEIGHT_SCALE = 0.8f
        private const val OPEN_MENU_HEIGHT_ANIMATION_DURATION_MS = 300L
        private const val CLOSE_MENU_HEIGHT_ANIMATION_DURATION_MS = 200L
        private const val ELEVATION_ANIMATION_DURATION_MS = 50L
        private const val CONTROLS_ALPHA_OPEN_MENU_ANIMATION_DELAY_MS = 33L
        private const val CONTAINER_ALPHA_CLOSE_MENU_ANIMATION_DELAY_MS = 33L
        private const val MENU_Z_TRANSLATION = 1f
    }
}

/** A factory interface to create a [LayoutMenu]. */
interface LayoutMenuFactory {
    fun create(
        syncQueue: SyncTransactionQueue,
        rootTdaOrganizer: RootTaskDisplayAreaOrganizer,
        displayController: DisplayController,
        windowDecorationActions: WindowDecorationActions,
        taskInfo: RunningTaskInfo,
        decorWindowContext: Context,
        positionSupplier: (Int, Int) -> Point,
        transactionSupplier: Supplier<Transaction>,
        desktopModeUiEventLogger: DesktopModeUiEventLogger,
        splitScreenController: SplitScreenController,
    ): LayoutMenu
}

/** A [LayoutMenuFactory] implementation that creates a [LayoutMenu]. */
object DefaultLayoutMenuFactory : LayoutMenuFactory {
    override fun create(
        syncQueue: SyncTransactionQueue,
        rootTdaOrganizer: RootTaskDisplayAreaOrganizer,
        displayController: DisplayController,
        windowDecorationActions: WindowDecorationActions,
        taskInfo: RunningTaskInfo,
        decorWindowContext: Context,
        positionSupplier: (Int, Int) -> Point,
        transactionSupplier: Supplier<Transaction>,
        desktopModeUiEventLogger: DesktopModeUiEventLogger,
        splitScreenController: SplitScreenController,
    ): LayoutMenu {
        return LayoutMenu(
            syncQueue,
            rootTdaOrganizer,
            displayController,
            windowDecorationActions,
            taskInfo,
            decorWindowContext,
            positionSupplier,
            transactionSupplier,
            desktopModeUiEventLogger,
            splitScreenController,
        )
    }
}
