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

package com.android.wm.shell.windowdecor.caption

import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.os.Handler
import android.os.Trace
import android.view.Display
import android.view.MotionEvent
import android.view.SurfaceControl
import android.view.View
import android.view.View.OnLongClickListener
import android.view.WindowInsets
import android.window.TaskSnapshot
import android.window.WindowContainerTransaction
import com.android.app.tracing.traceSection
import com.android.window.flags.Flags
import com.android.wm.shell.R
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.apptoweb.AppToWebRepository
import com.android.wm.shell.apptoweb.DialogLifecycleListener
import com.android.wm.shell.apptoweb.OpenByDefaultDialog
import com.android.wm.shell.apptoweb.canShowAppLinks
import com.android.wm.shell.apptoweb.isBrowserApp
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.MultiInstanceHelper
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.desktopmode.CaptionState
import com.android.wm.shell.desktopmode.CaptionState.FullscreenHeader
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.WindowDecorCaptionRepository
import com.android.wm.shell.shared.FocusTransitionListener
import com.android.wm.shell.shared.annotations.ShellBackgroundThread
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.transition.FocusTransitionObserver
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.DefaultLayoutMenuFactory
import com.android.wm.shell.windowdecor.DesktopHeaderManageWindowsMenu
import com.android.wm.shell.windowdecor.HandleMenu
import com.android.wm.shell.windowdecor.HandleMenu.Companion.shouldShowChangeAspectRatioButton
import com.android.wm.shell.windowdecor.HandleMenu.Companion.shouldShowGameControlsButton
import com.android.wm.shell.windowdecor.HandleMenu.Companion.shouldShowRestartButton
import com.android.wm.shell.windowdecor.HandleMenu.HandleMenuFactory
import com.android.wm.shell.windowdecor.HandleMenuController
import com.android.wm.shell.windowdecor.LayoutMenu
import com.android.wm.shell.windowdecor.LayoutMenuController
import com.android.wm.shell.windowdecor.LayoutMenuFactory
import com.android.wm.shell.windowdecor.ManageWindowsMenuController
import com.android.wm.shell.windowdecor.WindowDecorLinearLayout
import com.android.wm.shell.windowdecor.WindowDecoration2.RelayoutParams
import com.android.wm.shell.windowdecor.WindowDecorationActions
import com.android.wm.shell.windowdecor.WindowManagerWrapper
import com.android.wm.shell.windowdecor.common.DecorThemeUtil
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier
import com.android.wm.shell.windowdecor.extension.requestingImmersive
import com.android.wm.shell.windowdecor.viewholder.FullscreenHeaderViewHolder
import com.android.wm.shell.windowdecor.viewholder.FullscreenHeaderViewHolder.HeaderData
import com.android.wm.shell.windowdecor.viewholder.WindowDecorationViewHolder
import com.android.wm.shell.windowdecor.viewholder.util.LargeHeaderDimensions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.launch

/**
 * Controller for the fullscreen header. Creates, updates, and removes the views of the caption and
 * its menus.
 */
class FullscreenHeaderController(
    taskInfo: RunningTaskInfo,
    windowDecorViewHostSupplier: WindowDecorViewHostSupplier<WindowDecorViewHost>,
    private val userContext: Context,
    private val displayController: DisplayController,
    private val taskResourceLoader: WindowDecorTaskResourceLoader,
    private val splitScreenController: SplitScreenController,
    private val desktopUserRepositories: DesktopUserRepositories,
    private val transitions: Transitions,
    private val taskSurface: SurfaceControl,
    private val decorationSurface: SurfaceControl,
    taskOrganizer: ShellTaskOrganizer,
    @ShellMainThread private val mainHandler: Handler,
    @ShellMainThread private val mainDispatcher: MainCoroutineDispatcher,
    @ShellMainThread private val mainScope: CoroutineScope,
    @ShellBackgroundThread bgScope: CoroutineScope,
    private val syncQueue: SyncTransactionQueue,
    private val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    private val windowManagerWrapper: WindowManagerWrapper,
    private val multiInstanceHelper: MultiInstanceHelper,
    private val windowDecorCaptionRepository: WindowDecorCaptionRepository,
    private val desktopModeUiEventLogger: DesktopModeUiEventLogger,
    private val desktopState: DesktopState,
    private val windowDecorationActions: WindowDecorationActions,
    private val decorWindowContext: Context,
    private val gestureInterceptor: WindowDecorLinearLayout.GestureInterceptor,
    private val onLongClickListener: OnLongClickListener,
    private val onCaptionGenericMotionListener: View.OnGenericMotionListener,
    private val appToWebRepository: AppToWebRepository,
    private val focusTransitionObserver: FocusTransitionObserver,
    private val layoutMenuFactory: LayoutMenuFactory = DefaultLayoutMenuFactory,
    private val handleMenuFactory: HandleMenuFactory = HandleMenuFactory,
    private val decorThemeUtilFactory: DecorThemeUtil.Factory,
    private val fullscreenHeaderViewHolderFactory: FullscreenHeaderViewHolder.Factory =
        FullscreenHeaderViewHolder.DefaultFactory(),
    private val surfaceControlBuilderSupplier: () -> SurfaceControl.Builder = {
        SurfaceControl.Builder()
    },
    private val surfaceControlTransactionSupplier: () -> SurfaceControl.Transaction = {
        SurfaceControl.Transaction()
    },
) :
    CaptionController<WindowDecorLinearLayout>(
        taskInfo,
        windowDecorViewHostSupplier,
        taskOrganizer,
        bgScope,
    ),
    LayoutMenuController,
    HandleMenuController,
    ManageWindowsMenuController {

    override val captionType = CaptionType.FULLSCREEN_HEADER

    private lateinit var viewHolder: FullscreenHeaderViewHolder

    private var handleMenu: HandleMenu? = null
    private var openByDefaultDialog: OpenByDefaultDialog? = null
    private var layoutMenu: LayoutMenu? = null
    private var manageWindowsMenu: DesktopHeaderManageWindowsMenu? = null

    override val layoutMenuController = this
    override val handleMenuController = this
    override val manageWindowsMenuController = this

    override val isLayoutMenuActive: Boolean
        get() = layoutMenu != null

    private var handleMenuCreationJob: Job? = null
    override val isHandleMenuActive: Boolean
        get() = handleMenu != null

    private val isOpenByDefaultDialogActive
        get() = openByDefaultDialog != null

    private val closeLayoutMenuRunnable = Runnable { closeLayoutMenu() }
    private val dimensions = LargeHeaderDimensions(decorWindowContext.resources)

    private var isLayoutMenuHovered = false
    private var isFullscreenHeaderExitFullscreenButtonHovered = false
    private var loadAppInfoJob: Job? = null

    override fun relayout(
        params: RelayoutParams,
        parentContainer: SurfaceControl,
        display: Display,
        decorWindowContext: Context,
        startT: SurfaceControl.Transaction,
        finishT: SurfaceControl.Transaction,
        wct: WindowContainerTransaction,
    ): CaptionRelayoutResult =
        traceSection(
            traceTag = Trace.TRACE_TAG_WINDOW_MANAGER,
            name = "FullscreenHeaderController#relayout",
        ) {
            val isTaskFocused =
                if (FocusTransitionListener.isDisplayLocalIsFocusedMigrationEnabled()) {
                    focusTransitionObserver.isFocusedOnDisplay(taskInfo)
                } else {
                    taskInfo.isFocused
                }

            // If we get a relayout call while hovering over exit fullscreen button in the
            // fullscreen header but the task has lost focus, explicitly cancel the hover (since we
            // don't get a HOVER_EXIT signal in this case).
            if (!isTaskFocused && isFullscreenHeaderExitFullscreenButtonHovered) {
                setHeaderMaximizeButtonHovered(hovered = false)
                onLayoutButtonHoverExit()
            }

            val captionLayout =
                super.relayout(
                    params,
                    parentContainer,
                    display,
                    decorWindowContext,
                    startT,
                    finishT,
                    wct,
                )
            handleMenu?.relayout(
                startT,
                taskInfo.configuration,
                captionLayout.captionX,
                // Add top padding to the caption Y so that the menu is shown over what is the
                // actual contents of the caption, ignoring padding.
                captionLayout.captionY + captionLayout.captionTopPadding,
            )

            updateViewHolder(params.hasGlobalFocus)

            if (!params.hasGlobalFocus) {
                closeHandleMenu()
                closeManageWindowsMenu()
                closeLayoutMenu()
            }

            notifyCaptionStateChanged()
            return captionLayout
        }

    override fun getCaptionTopPadding(): Int {
        val displayInsetsState = displayController.getInsetsState(taskInfo.displayId)
        val taskBounds = taskInfo.configuration.windowConfiguration.bounds
        val systemBarInsets =
            displayInsetsState.calculateInsets(
                /* frame= */ taskBounds,
                /* hostBounds= */ taskBounds,
                /* types= */ WindowInsets.Type.systemBars() and
                    WindowInsets.Type.captionBar().inv(),
                /* ignoreVisibility= */ false,
            )
        return systemBarInsets.top
    }

    private fun notifyCaptionStateChanged() {
        if (!isCaptionVisible || !hasGlobalFocus) {
            notifyNoCaption()
            return
        }
        viewHolder.runOnAppChipGlobalLayout { notifyFullscreenHeaderStateChanged() }
    }

    private fun notifyNoCaption() {
        windowDecorCaptionRepository.notifyCaptionChanged(CaptionState.NoCaption(taskInfo.taskId))
    }

    private fun notifyFullscreenHeaderStateChanged() {
        val appChipPositionInWindow = viewHolder.getAppChipLocationInWindow()
        val taskBounds = taskInfo.configuration.windowConfiguration.bounds
        val appChipGlobalPosition =
            Rect(appChipPositionInWindow).apply { offset(taskBounds.left, taskBounds.top) }
        val captionState =
            FullscreenHeader(
                runningTaskInfo = taskInfo,
                isHeaderMenuExpanded = isHandleMenuActive,
                globalAppChipBounds = appChipGlobalPosition,
                isFocused = hasGlobalFocus,
            )

        windowDecorCaptionRepository.notifyCaptionChanged(captionState)
    }

    private fun calculateLayoutMenuPosition(menuWidth: Int, menuHeight: Int): Point {
        val position = Point()
        val displayLayout =
            displayController.getDisplayLayout(taskInfo.displayId) ?: return position

        val displayWidth = displayLayout.width()
        val displayHeight = displayLayout.height()
        val captionHeight = getCaptionHeight()

        val exitFullscreenButtonLocation = viewHolder.getExitFullscreenButtonPosition()

        var menuLeft =
            exitFullscreenButtonLocation[0] - (menuWidth - viewHolder.exitFullscreenButtonWidth) / 2
        var menuTop = captionHeight
        val menuRight = menuLeft + menuWidth
        val menuBottom = menuTop + menuHeight

        // If the menu is out of screen bounds, shift it as needed
        if (menuLeft < 0) {
            menuLeft = 0
        } else if (menuRight > displayWidth) {
            menuLeft = (displayWidth - menuWidth)
        }
        if (menuBottom > displayHeight) {
            menuTop = (displayHeight - menuHeight)
        }

        return Point(menuLeft, menuTop)
    }

    /** Create and display layout menu window */
    override fun createLayoutMenu() {
        if (isLayoutMenuActive) return
        desktopModeUiEventLogger.log(
            taskInfo,
            DesktopUiEventEnum.DESKTOP_WINDOW_MAXIMIZE_BUTTON_REVEAL_MENU,
        )
        layoutMenu =
            layoutMenuFactory
                .create(
                    syncQueue = syncQueue,
                    rootTdaOrganizer = rootTaskDisplayAreaOrganizer,
                    displayController = displayController,
                    windowDecorationActions = windowDecorationActions,
                    taskInfo = taskInfo,
                    decorWindowContext = decorWindowContext,
                    positionSupplier = { width, height ->
                        calculateLayoutMenuPosition(width, height)
                    },
                    transactionSupplier = surfaceControlTransactionSupplier,
                    desktopModeUiEventLogger = desktopModeUiEventLogger,
                    splitScreenController = splitScreenController,
                )
                .apply {
                    show(
                        isTaskInImmersiveMode = false,
                        showImmersiveOption = taskInfo.requestingImmersive,
                        showSnapOptions = taskInfo.isResizeable,
                        onHoverListener = { hovered: Boolean ->
                            isLayoutMenuHovered = hovered
                            onLayoutButtonHoverStateChanged()
                        },
                        onOutsideTouchListener = { closeLayoutMenu() },
                        onLayoutMenuClickedListener = { closeLayoutMenu() },
                    )
                }
    }

    /** Set whether the header's exit fullscreen button is hovered. */
    override fun setHeaderMaximizeButtonHovered(hovered: Boolean) {
        isFullscreenHeaderExitFullscreenButtonHovered = hovered
        onLayoutButtonHoverStateChanged()
    }

    /**
     * Called when either one of the exit fullscreen button in the header or the layout menu has
     * change its hover state.
     */
    override fun onLayoutButtonHoverStateChanged() {
        if (!isLayoutMenuHovered && !isFullscreenHeaderExitFullscreenButtonHovered) {
            // Neither is hovered, close the menu.
            if (isLayoutMenuActive) {
                mainHandler.postDelayed(closeLayoutMenuRunnable, CLOSE_LAYOUT_MENU_DELAY_MS)
            }
            return
        }
        // At least one of the two is hovered, cancel the close if needed.
        mainHandler.removeCallbacks(closeLayoutMenuRunnable)
    }

    /** Close the layout menu window if open. */
    override fun closeLayoutMenu() {
        layoutMenu?.close {
            // Request the accessibility service to refocus on the exit fullscreen button after
            // closing the menu.
            a11yFocusExitFullscreenButton()
        }
        layoutMenu = null
    }

    /** Request direct a11y focus on the exit fullscreen button */
    fun a11yFocusExitFullscreenButton() = viewHolder.requestAccessibilityFocus()

    private fun createOpenByDefaultDialog() {
        if (isOpenByDefaultDialogActive) return
        openByDefaultDialog =
            OpenByDefaultDialog(
                decorWindowContext,
                userContext,
                transitions,
                taskInfo,
                taskSurface,
                displayController,
                taskResourceLoader,
                surfaceControlTransactionSupplier,
                mainScope,
                object : DialogLifecycleListener {
                    override fun onDialogDismissed() {
                        openByDefaultDialog = null
                    }
                },
                desktopModeUiEventLogger,
            )
    }

    /**
     * Called when there is a [MotionEvent.ACTION_HOVER_EXIT] on the maximize window button.
     *
     * TODO(b/409648813): Move all hover logic to view holder
     */
    override fun onLayoutButtonHoverExit() {
        viewHolder.onExitFullscreenWindowHoverExit()
    }

    /**
     * Called when there is a [MotionEvent.ACTION_HOVER_ENTER] on the maximize window button.
     *
     * TODO(b/409648813): Move all hover logic to view holder
     */
    override fun onLayoutButtonHoverEnter() {
        viewHolder.onExitFullscreenWindowHoverEnter()
    }

    /** Updates app info and creates and displays handle menu window. */
    override fun createHandleMenu(minimumInstancesFound: Boolean) {
        if (isHandleMenuActive || handleMenuCreationJob?.isActive == true) return
        handleMenuCreationJob =
            mainScope.launch {
                val isBrowserApp = isBrowserApp()
                val appToWebIntent =
                    if (canShowAppLinks(display, desktopState)) {
                        appToWebRepository.getAppToWebIntent(taskInfo, isBrowserApp)
                    } else {
                        // Skip request for assist content as it is only used for links, which are
                        // not supported
                        null
                    }
                createHandleMenu(
                    appToWebData = HandleMenu.AppToWebData(isBrowserApp, appToWebIntent),
                    minimumInstancesFound = minimumInstancesFound,
                )
            }
    }

    /** Creates and shows the handle menu. */
    private fun createHandleMenu(
        appToWebData: HandleMenu.AppToWebData?,
        minimumInstancesFound: Boolean,
    ) {
        val supportsMultiInstance =
            multiInstanceHelper.supportsMultiInstanceSplit(taskInfo.baseActivity, taskInfo.userId)
        val shouldShowManageWindowsButton = supportsMultiInstance && minimumInstancesFound
        val shouldShowChangeAspectRatioButton = shouldShowChangeAspectRatioButton(taskInfo)
        val shouldShowGameControlsButton =
            shouldShowGameControlsButton(decorWindowContext, taskInfo)
        val shouldShowRestartButton = shouldShowRestartButton(taskInfo)
        viewHolder.onHandleMenuOpened()
        handleMenu =
            handleMenuFactory
                .create(
                    mainDispatcher = mainDispatcher,
                    mainScope = mainScope,
                    context = decorWindowContext,
                    taskInfo = taskInfo,
                    parentSurface = decorationSurface,
                    display = display,
                    windowManagerWrapper = windowManagerWrapper,
                    windowDecorationActions = windowDecorationActions,
                    taskResourceLoader = taskResourceLoader,
                    // TODO(b/409648813): Have handle menus use [CaptionType]
                    layoutResId = R.layout.desktop_mode_fullscreen_header,
                    splitScreenController = splitScreenController,
                    shouldShowWindowingPill =
                        !Flags.enableConsolidatedWindowOptions() &&
                            desktopState.canEnterDesktopModeOrShowAppHandle,
                    shouldShowNewWindowButton = supportsMultiInstance,
                    shouldShowManageWindowsButton = shouldShowManageWindowsButton,
                    shouldShowChangeAspectRatioButton = shouldShowChangeAspectRatioButton,
                    shouldShowGameControlsButton = shouldShowGameControlsButton,
                    shouldShowDesktopModeButton =
                        desktopState.isDesktopModeSupportedOnDisplay(display),
                    shouldShowRestartButton = shouldShowRestartButton,
                    appToWebData = appToWebData,
                    desktopModeUiEventLogger = desktopModeUiEventLogger,
                    captionView = viewHolder.rootView,
                    captionWidth = captionLayoutResult.captionWidth,
                    captionHeight = captionLayoutResult.captionHeight,
                    captionX = captionLayoutResult.captionX,
                    // Add top padding to the caption Y so that the menu is shown over what is the
                    // actual contents of the caption, ignoring padding.
                    captionY = captionLayoutResult.captionY + captionLayoutResult.captionTopPadding,
                    decorThemeUtilFactory = decorThemeUtilFactory,
                )
                .apply {
                    show(
                        openInAppOrBrowserClickListener = { intent ->
                            windowDecorationActions.onOpenInBrowser(taskInfo.taskId, intent)
                            appToWebRepository.onCapturedLinkUsed(taskInfo.taskId)
                            windowDecorCaptionRepository.onAppToWebUsage()
                        },
                        onOpenByDefaultClickListener = { createOpenByDefaultDialog() },
                        onCloseMenuClickListener = { closeHandleMenu() },
                        onOutsideTouchListener = { closeHandleMenu() },
                        onHandleMenuClicked = { closeHandleMenu() },
                        forceShowSystemBars = false,
                    )
                }
        notifyCaptionStateChanged()
    }

    /** Close the handle menu window. */
    private fun closeHandleMenu() {
        if (!isHandleMenuActive) return
        viewHolder.onHandleMenuClosed()
        handleMenu?.close()
        handleMenu = null
        notifyCaptionStateChanged()
    }

    private fun isBrowserApp(): Boolean =
        taskInfo.baseActivity?.let { isBrowserApp(userContext, it.packageName, userContext.userId) }
            ?: false

    /** Creates and shows the manage windows menu. */
    override fun createManageWindowsMenu(snapshotList: ArrayList<Pair<Int, TaskSnapshot>>) {
        // The menu uses display-wide coordinates for positioning, so make position the sum
        // of task position and caption position.
        val taskBounds = taskInfo.configuration.windowConfiguration.bounds
        manageWindowsMenu =
            DesktopHeaderManageWindowsMenu(
                callerTaskInfo = taskInfo,
                x = taskBounds.left + captionLayoutResult.captionX,
                y =
                    taskBounds.top +
                        captionLayoutResult.captionY +
                        captionLayoutResult.captionTopPadding,
                displayController = displayController,
                rootTdaOrganizer = rootTaskDisplayAreaOrganizer,
                context = decorWindowContext,
                desktopUserRepositories = desktopUserRepositories,
                surfaceControlBuilderSupplier = surfaceControlBuilderSupplier,
                surfaceControlTransactionSupplier = surfaceControlTransactionSupplier,
                snapshotList = snapshotList,
                onIconClickListener = { requestedTaskId ->
                    closeManageWindowsMenu()
                    windowDecorationActions.onOpenInstance(taskInfo, requestedTaskId)
                },
                onOutsideClickListener = { closeManageWindowsMenu() },
            )
    }

    private fun closeManageWindowsMenu() {
        manageWindowsMenu?.animateClose()
        manageWindowsMenu = null
    }

    /** Update the view holder for fullscreen header. */
    private fun updateViewHolder(hasGlobalFocus: Boolean) =
        traceSection("FullscreenHeaderController#updateViewHolder") {
            val displayId = taskInfo.displayId
            val displayLayout = displayController.getDisplayLayout(displayId) ?: return@traceSection
            viewHolder.bindData(HeaderData(taskInfo, hasGlobalFocus, true, isCaptionVisible))
        }

    override fun createCaptionView(): WindowDecorationViewHolder<HeaderData> {
        val fullscreenHeaderViewHolder =
            fullscreenHeaderViewHolderFactory.create(
                // View holder should inflate the caption's root view
                context = decorWindowContext,
                windowDecorationActions = windowDecorationActions,
                gestureInterceptor = gestureInterceptor,
                onLongClickListener = onLongClickListener,
                onCaptionGenericMotionListener = onCaptionGenericMotionListener,
                onExitFullscreenHoverAnimationFinishedListener = { createLayoutMenu() },
                desktopModeUiEventLogger = desktopModeUiEventLogger,
                dimensions = dimensions,
                focusTransitionObserver = focusTransitionObserver,
            )

        loadAppInfoJob =
            mainScope.launch {
                val (name, icon) = taskResourceLoader.getNameAndHeaderIcon(taskInfo)
                viewHolder.setAppName(name)
                viewHolder.setAppIcon(icon)
                notifyCaptionStateChanged()
            }
        viewHolder = fullscreenHeaderViewHolder
        return fullscreenHeaderViewHolder
    }

    override fun getCaptionHeight(): Int = dimensions.height + getCaptionTopPadding()

    override fun getCaptionWidth(): Int =
        taskInfo.getConfiguration().windowConfiguration.bounds.width()

    override val occludingElements: List<OccludingElement> = emptyList()

    /**
     * Announces that the app window is now being focused for accessibility. This is used after a
     * window is minimized/closed, and a new app window gains focus.
     */
    fun a11yAnnounceFocused() {
        viewHolder.a11yAnnounceFocused()
    }

    override fun close(wct: WindowContainerTransaction, t: SurfaceControl.Transaction): Boolean {
        loadAppInfoJob?.cancel()
        closeHandleMenu()
        closeManageWindowsMenu()
        closeLayoutMenu()
        viewHolder.close()
        notifyNoCaption()
        openByDefaultDialog?.dismiss()
        return super.close(wct, t)
    }

    private companion object {
        private const val TAG = "FullscreenHeaderController"
        private const val CLOSE_LAYOUT_MENU_DELAY_MS = 150L
    }
}
