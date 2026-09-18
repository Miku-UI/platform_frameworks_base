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

import android.annotation.IdRes
import android.app.ActivityManager
import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.Region
import android.hardware.input.InputManager
import android.os.Handler
import android.view.Display
import android.view.InsetsSource
import android.view.InsetsState
import android.view.SurfaceControl
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND
import android.view.WindowManager.LayoutParams
import com.android.internal.jank.InteractionJankMonitor
import com.android.testing.wm.util.StubTransaction
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.MultiDisplayDragMoveIndicatorController
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.FakeShellDesktopState
import com.android.wm.shell.desktopmode.ShellDesktopState
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerController
import com.android.wm.shell.recents.PerDisplayRecentsTransitionStateListener
import com.android.wm.shell.shared.desktopmode.DesktopConfig
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.transition.FocusTransitionObserver
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.WindowDecorationTestHelper.TestHeaderDimensions.Companion.CUSTOMIZABLE_REGION_MARGIN_START
import com.android.wm.shell.windowdecor.common.CaptionVisibilityHelper
import com.android.wm.shell.windowdecor.common.DecorThemeUtil
import com.android.wm.shell.windowdecor.common.DrawableInsets
import com.android.wm.shell.windowdecor.common.InputPilferer
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import com.android.wm.shell.windowdecor.common.WindowDecorationGestureExclusionTracker
import com.android.wm.shell.windowdecor.common.viewhost.DefaultWindowDecorViewHost
import com.android.wm.shell.windowdecor.common.viewhost.SurfaceControlViewHostAdapter
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier
import com.android.wm.shell.windowdecor.viewholder.AppHeaderViewHolder
import com.android.wm.shell.windowdecor.viewholder.util.HeaderDimensions
import com.android.wm.shell.windowdecor.viewholder.util.LargeHeaderDimensions
import kotlinx.coroutines.CoroutineScope
import org.mockito.kotlin.mock

/** A test helper to create window decorations. */
object WindowDecorationTestHelper {

    /** Creates a task that should be decorated with an app header. */
    fun createAppHeaderTask(bounds: Rect = Rect(200, 200, 800, 600)): RunningTaskInfo {
        return createFreeformTask(bounds = bounds).apply { isVisible = true }
    }

    /** Creates a task that should be decorated with an opaque (not customizable) app header. */
    fun createOpaqueAppHeaderTask(bounds: Rect = Rect(200, 200, 800, 600)): RunningTaskInfo {
        return createAppHeaderTask(bounds = bounds).apply {
            taskDescription =
                (taskDescription ?: ActivityManager.TaskDescription()).apply {
                    topOpaqueSystemBarsAppearance =
                        topOpaqueSystemBarsAppearance and
                            APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND.inv()
                }
        }
    }

    /** Creates a task that should be decorated with an app header and is being customized. */
    fun createCustomAppHeaderTask(bounds: Rect = Rect(200, 200, 800, 600)): RunningTaskInfo {
        return createAppHeaderTask(bounds = bounds).apply {
            taskDescription =
                (taskDescription ?: ActivityManager.TaskDescription()).apply {
                    topOpaqueSystemBarsAppearance =
                        topOpaqueSystemBarsAppearance or
                            APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND
                }
        }
    }

    /** Adds an inset source of the given type to the [InsetsState]. */
    fun InsetsState.addInsetsSource(@WindowInsets.Type.InsetsType type: Int, visible: Boolean) {
        InsetsSource(/* id= */ 0, type).also {
            it.setVisible(visible)
            addSource(it)
        }
    }

    /** Creates a window decoration. */
    fun createWindowDecoration(
        context: Context,
        taskInfo: RunningTaskInfo,
        windowDecorationFinder: ((Int) -> WindowDecorationWrapper?) = mock(),
        desktopState: DesktopState,
        shellDesktopState: ShellDesktopState =
            FakeShellDesktopState(desktopState as FakeDesktopState),
        desktopConfig: DesktopConfig,
        scope: CoroutineScope,
        handler: Handler,
        executor: ShellExecutor,
        inputPilferer: InputPilferer = TestInputPilferer(),
        inputManager: InputManager = mock(),
        displayController: DisplayController,
        desktopUserRepositories: DesktopUserRepositories,
        splitScreenController: SplitScreenController,
        desktopTasksController: DesktopTasksController,
        taskOperations: TaskOperations,
        desktopModeUiEventLogger: DesktopModeUiEventLogger = mock(),
        windowDecorationActions: WindowDecorationActions = mock(),
        captionVisibilityHelper: CaptionVisibilityHelper = mock(),
        appHeaderViewHolderFactory: AppHeaderViewHolder.Factory =
            AppHeaderViewHolder.DefaultFactory(),
        windowDecorationExclusionTracker: WindowDecorationGestureExclusionTracker = mock(),
        pinnedLayerController: PinnedLayerController = mock(),
        recentsTransitionStateListener: PerDisplayRecentsTransitionStateListener = mock(),
    ): TestWindowDecoration {
        val viewHostSupplier = TestWindowDecorViewHostSupplier(scope)
        val decoration =
            DefaultWindowDecoration(
                taskInfo = taskInfo,
                taskSurface = SurfaceControl(),
                context = context,
                userContext = context,
                displayController = displayController,
                taskResourceLoader = TestWindowDecorTaskResourceLoader(),
                splitScreenController = splitScreenController,
                desktopUserRepositories = desktopUserRepositories,
                taskOrganizer = mock(),
                handler = handler,
                mainExecutor = executor,
                mainDispatcher = mock(),
                mainScope = scope,
                mainImmediateScope = scope,
                bgExecutor = mock(),
                bgScope = scope,
                transitions = mock(),
                choreographer = mock(),
                syncQueue = mock(),
                rootTaskDisplayAreaOrganizer = mock(),
                windowDecorViewHostSupplier = viewHostSupplier,
                multiInstanceHelper = mock(),
                windowDecorCaptionRepository = mock(),
                desktopModeUiEventLogger = desktopModeUiEventLogger,
                desktopModeCompatPolicy = mock(),
                desktopState = desktopState,
                desktopConfig = desktopConfig,
                windowDecorationActions = windowDecorationActions,
                appToWebRepository = mock(),
                captionVisibilityHelper = captionVisibilityHelper,
                windowManagerWrapper = mock(),
                lockTaskChangeListener = mock(),
                surfaceControlTransactionSupplier = { StubTransaction() },
                appHeaderViewHolderFactory = appHeaderViewHolderFactory,
                pinnedLayerController = pinnedLayerController,
                focusTransitionObserver = mock<FocusTransitionObserver>(),
                recentsTransitionStateListener = recentsTransitionStateListener,
                desktopTasksController = mock(),
                decorThemeUtilFactory = DecorThemeUtil.Factory(),
            )
        val wrapped = decoration.wrapped()
        val positioner =
            MultiDisplayVeiledResizeTaskPositioner(
                taskOrganizer = mock<ShellTaskOrganizer>(),
                windowDecoration = wrapped,
                displayController = displayController,
                transactionSupplier = { StubTransaction() },
                transitions = mock<Transitions>(),
                interactionJankMonitor = mock<InteractionJankMonitor>(),
                handler = handler,
                multiDisplayDragMoveIndicatorController =
                    mock<MultiDisplayDragMoveIndicatorController>(),
                desktopState = desktopState,
                desktopTasksController = desktopTasksController,
                desktopUserRepositories = desktopUserRepositories,
            )
        val touchEventListener =
            DesktopModeTouchEventListener(
                context,
                taskInfo,
                positioner,
                windowDecorationFinder,
                desktopTasksController,
                taskOperations,
                desktopModeUiEventLogger,
                windowDecorationActions,
                desktopUserRepositories,
                windowDecorationExclusionTracker,
                inputPilferer,
                inputManager,
                shellDesktopState,
                mock<MultiDisplayDragMoveIndicatorController>(),
                { StubTransaction() },
                mock<DesktopModeWindowDecorViewModel.CaptionTouchStatusListener>(),
                mock<DesktopModeWindowDecorViewModel.AppHandleMotionEventHandler>(),
                pinnedLayerController,
            )
        wrapped.setCaptionListeners(
            onClickListener = touchEventListener,
            gestureInterceptor = touchEventListener,
            onLongClickListener = touchEventListener,
            onGenericMotionListener = touchEventListener,
        )
        wrapped.setDragPositioningCallback(positioner)
        wrapped.setExclusionRegionListener(mock())
        return TestWindowDecoration(decoration, wrapped, viewHostSupplier)
    }

    private fun DefaultWindowDecoration.wrapped(): WindowDecorationWrapper =
        WindowDecorationWrapper.Factory().fromDefaultDecoration(this)

    /** A test supplier for [WindowDecorViewHost]. */
    class TestWindowDecorViewHostSupplier(private val scope: CoroutineScope) :
        WindowDecorViewHostSupplier<WindowDecorViewHost> {
        private val viewHosts = mutableListOf<DefaultWindowDecorViewHost>()

        /** Finds a task's caption view by its id. */
        fun getView(taskId: Int, @IdRes id: Int): View? {
            for (vh in viewHosts) {
                val rootView = vh.viewHostAdapter.viewHost?.view ?: continue
                val lp = rootView.layoutParams as? LayoutParams ?: continue
                if (!lp.title.contains(taskId.toString())) continue
                val view = rootView.findViewById<View>(id) ?: continue
                return view
            }
            return null
        }

        override fun acquire(context: Context, display: Display): WindowDecorViewHost =
            DefaultWindowDecorViewHost(
                    context = context,
                    mainScope = scope,
                    display = display,
                    viewHostAdapter = TestSurfaceControlViewHostAdapter(context, display),
                )
                .also { viewHosts.add(it) }

        override fun release(viewHost: WindowDecorViewHost, t: SurfaceControl.Transaction) {
            viewHosts.remove(viewHost)
        }

        /**
         * A test [SurfaceControlViewHostAdapter] that intercepts [updateView] to remove the
         * [LayoutParams.INPUT_FEATURE_SPY] flag before updating the underlying SCVH. This is useful
         * to avoid a SecurityException thrown by SurfaceControlViewHost/ViewRootImpl when
         * attempting to add a window using [LayoutParams.INPUT_FEATURE_SPY] without the
         * MONITOR_INPUT permission.
         */
        class TestSurfaceControlViewHostAdapter(context: Context, display: Display) :
            SurfaceControlViewHostAdapter(context, display) {
            override fun updateView(view: View, attrs: LayoutParams) {
                val lp = LayoutParams()
                lp.copyFrom(attrs)
                lp.inputFeatures = lp.inputFeatures and LayoutParams.INPUT_FEATURE_SPY.inv()
                super.updateView(view, lp)
            }
        }
    }

    /** A test wrapper for a real window decoration. */
    class TestWindowDecoration(
        val defaultWindowDecoration: DefaultWindowDecoration,
        val wrapped: WindowDecorationWrapper,
        val viewHostSupplier: TestWindowDecorViewHostSupplier,
    ) {
        /** Relayouts the window decoration. */
        fun relayout(taskInfo: RunningTaskInfo) {
            defaultWindowDecoration.relayout(
                taskInfo = taskInfo,
                startT = StubTransaction(),
                finishT = StubTransaction(),
                applyStartTransactionOnDraw = true,
                shouldSetTaskVisibilityPositionAndCrop = false,
                hasGlobalFocus = taskInfo.isFocused,
                displayExclusionRegion = Region.obtain(),
                inSyncWithTransition = true,
                taskSurface = null,
            )
        }

        /** Finds a view by its id. */
        fun findViewById(@IdRes id: Int): View? {
            return viewHostSupplier.getView(
                taskId = defaultWindowDecoration.taskInfo.taskId,
                id = id,
            )
        }

        /** Modifies [outRegion] to include a custom region in this decor's caption. */
        fun addCustomCaptionContent(outRegion: Region, contentWidth: Int) {
            val taskBounds = getBounds()
            // Add a custom area at the start of the customizable region.
            outRegion.union(
                Rect(
                    taskBounds.left + CUSTOMIZABLE_REGION_MARGIN_START,
                    taskBounds.top,
                    taskBounds.left + CUSTOMIZABLE_REGION_MARGIN_START + contentWidth,
                    taskBounds.top + getCaptionHeight(),
                )
            )
        }

        private fun getBounds(): Rect =
            defaultWindowDecoration.taskInfo.configuration.windowConfiguration.bounds

        private fun getCaptionHeight(): Int =
            defaultWindowDecoration.captionController?.getCaptionHeight() ?: 0
    }

    class TestWindowDecorTaskResourceLoader : WindowDecorTaskResourceLoader {
        override suspend fun getNameAndHeaderIcon(
            taskInfo: RunningTaskInfo
        ): Pair<CharSequence, Bitmap> = Pair("Test", mock())

        override fun getNameAndHeaderIcon(
            taskInfo: RunningTaskInfo,
            callback: (CharSequence, Bitmap) -> Unit,
        ) = callback.invoke("Test", mock())

        override suspend fun getVeilIcon(taskInfo: RunningTaskInfo): Bitmap = mock()

        override fun onWindowDecorCreated(taskInfo: RunningTaskInfo) {
            // No-op.
        }

        override fun onWindowDecorClosed(taskInfo: RunningTaskInfo) {
            // No-op.
        }
    }

    /**
     * A test implementation of [AppHeaderViewHolder.Factory] that allows overriding the dimensions
     * used to create an app header.
     */
    class TestAppHeaderViewHolderFactory(private val overrideDimensions: HeaderDimensions) :
        AppHeaderViewHolder.Factory {

        override fun create(
            rootView: View?,
            context: Context,
            windowDecorationActions: WindowDecorationActions,
            gestureInterceptor: WindowDecorLinearLayout.GestureInterceptor,
            onLongClickListener: View.OnLongClickListener,
            onCaptionGenericMotionListener: View.OnGenericMotionListener,
            onMaximizeHoverAnimationFinishedListener: () -> Unit,
            desktopModeUiEventLogger: DesktopModeUiEventLogger,
            dimensions: HeaderDimensions,
            focusTransitionObserver: FocusTransitionObserver,
            decorThemeUtilFactory: DecorThemeUtil.Factory,
        ): AppHeaderViewHolder {
            return AppHeaderViewHolder(
                rootView,
                context,
                windowDecorationActions,
                gestureInterceptor,
                onLongClickListener,
                onCaptionGenericMotionListener,
                onMaximizeHoverAnimationFinishedListener,
                desktopModeUiEventLogger,
                overrideDimensions,
                focusTransitionObserver,
                decorThemeUtilFactory,
            )
        }
    }

    /**
     * A test implementation of [HeaderDimensions] that uses hard-coded values instead of loading
     * them from a context.
     */
    class TestHeaderDimensions
    private constructor(private val largeHeaderDimensions: LargeHeaderDimensions) :
        HeaderDimensions by largeHeaderDimensions {

        constructor(resources: Resources) : this(LargeHeaderDimensions(resources))

        override val height: Int = APP_HEADER_HEIGHT
        override val buttonCornerRadius: Int = 16
        override val appNameMaxWidth: Int = 130
        override val expandMenuErrorImageWidth: Int = 16
        override val expandMenuErrorImageMargin: Int = 8
        override val appChipBackgroundInsets: DrawableInsets = DrawableInsets(0, 0, 0, 0)
        override val minimizeBackgroundInsets: DrawableInsets = DrawableInsets(0, 0, 0, 0)
        override val maximizeBackgroundInsets: DrawableInsets = DrawableInsets(0, 0, 0, 0)
        override val closeBackgroundInsets: DrawableInsets = DrawableInsets(0, 0, 0, 0)
        override val windowControlButtonWidth: Int = BUTTON_SIZE
        override val windowControlButtonHeight: Int = BUTTON_SIZE
        override val windowControlButtonPadding: Rect = Rect(12, 12, 12, 12)
        override val windowControlButtonMarginEnd: Int = 8
        override val customizableRegionMarginStart: Int = CUSTOMIZABLE_REGION_MARGIN_START
        override val customizableRegionMarginEnd: Int = 10
        override val customizableRegionEmptyDragSpace: Int = BUTTON_SIZE

        companion object {
            const val CUSTOMIZABLE_REGION_MARGIN_START = 84
            const val APP_HEADER_HEIGHT = 48
            const val BUTTON_SIZE = 48
        }
    }

    /** A test implementation of [InputPilferer]. */
    class TestInputPilferer : InputPilferer {
        var pilferCallCount = 0
            private set

        var lastPilferedView: View? = null
            private set

        override fun pilferPointers(v: View) {
            pilferCallCount++
            lastPilferedView = v
        }

        fun reset() {
            pilferCallCount = 0
            lastPilferedView = null
        }
    }
}
