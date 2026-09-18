/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.desktopmode

import android.Manifest.permission.SYSTEM_ALERT_WINDOW
import android.app.ActivityManager
import android.app.ActivityManager.RecentTaskInfo
import android.app.ActivityManager.RunningTaskInfo
import android.app.ActivityOptions
import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.WindowConfiguration
import android.app.WindowConfiguration.ACTIVITY_TYPE_HOME
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ActivityInfo.CONFIG_DENSITY
import android.content.pm.ActivityInfo.LAUNCH_SINGLE_INSTANCE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.UserInfo
import android.content.pm.UserInfo.FLAG_FULL
import android.content.pm.UserInfo.FLAG_MAIN
import android.content.pm.UserInfo.FLAG_PROFILE
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.UserHandle
import android.os.UserManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableContext
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.INVALID_DISPLAY
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceControl
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_START_LOCK_TASK_MODE
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.widget.Toast
import android.window.DisplayAreaInfo
import android.window.IWindowContainerToken
import android.window.RemoteTransition
import android.window.TaskSnapshotManager
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_LAUNCH_TASK
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_PENDING_INTENT
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REMOVE_TASK
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REORDER
import android.window.WindowContainerTransaction.HierarchyOp.LAUNCH_KEY_TASK_ID
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn
import com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession
import com.android.dx.mockito.inline.extended.ExtendedMockito.never
import com.android.dx.mockito.inline.extended.StaticMockitoSession
import com.android.internal.jank.InteractionJankMonitor
import com.android.internal.policy.DesktopModeCompatPolicy
import com.android.testing.wm.util.MockToken
import com.android.window.flags.Flags
import com.android.window.flags.Flags.FLAG_CLOSE_FULLSCREEN_AND_SPLITSCREEN_KEYBOARD_SHORTCUT
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE
import com.android.window.flags.Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION
import com.android.window.flags.Flags.FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY
import com.android.wm.shell.R
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.bubbles.BubbleController
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.HomeIntentProvider
import com.android.wm.shell.common.LockTaskChangeListener
import com.android.wm.shell.common.MultiInstanceHelper
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.common.UserProfileContexts
import com.android.wm.shell.common.transition.TransitionStateHolder
import com.android.wm.shell.desktopmode.DesktopImmersiveController.ExitResult
import com.android.wm.shell.desktopmode.DesktopMixedTransitionHandler.PendingMixedTransition
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.EnterReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ExitReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.InputMethod
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.MinimizeReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ResizeTrigger
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.UnminimizeReason
import com.android.wm.shell.desktopmode.DesktopTasksController.SnapPosition
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFullscreenTask
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createHomeTask
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createSplitScreenTask
import com.android.wm.shell.desktopmode.ExitDesktopTaskTransitionHandler.FULLSCREEN_ANIMATION_DURATION
import com.android.wm.shell.desktopmode.clientfullscreenrequest.DesktopFullscreenRequestHandler
import com.android.wm.shell.desktopmode.common.ToggleTaskSizeInteraction
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.desktopmode.data.DesktopRepositoryInitializer
import com.android.wm.shell.desktopmode.data.TopTransparentFullscreenTaskData
import com.android.wm.shell.desktopmode.data.persistence.Desktop
import com.android.wm.shell.desktopmode.data.persistence.DesktopPersistentRepository
import com.android.wm.shell.desktopmode.desktopfirst.DESKTOP_FIRST_DISPLAY_WINDOWING_MODE
import com.android.wm.shell.desktopmode.desktopfirst.TOUCH_FIRST_DISPLAY_WINDOWING_MODE
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityTokenProvider
import com.android.wm.shell.desktopmode.desktopwallpaperactivity.DesktopWallpaperActivityUtils
import com.android.wm.shell.desktopmode.multidesks.DeskSwitchTransitionHandler
import com.android.wm.shell.desktopmode.multidesks.DeskTransition
import com.android.wm.shell.desktopmode.multidesks.DesksController
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.desktopmode.multidesks.DesksTransitionObserver
import com.android.wm.shell.desktopmode.multidesks.PreserveDisplayRequestHandler
import com.android.wm.shell.draganddrop.DragAndDropController
import com.android.wm.shell.freeform.FreeformTaskTransitionStarter
import com.android.wm.shell.fullscreen.FullscreenDisconnectHandler
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerController
import com.android.wm.shell.pip2.phone.PipScheduler
import com.android.wm.shell.pip2.phone.PipTransitionState
import com.android.wm.shell.recents.RecentTasksController
import com.android.wm.shell.recents.RecentsTransitionHandler
import com.android.wm.shell.recents.RecentsTransitionStateListener
import com.android.wm.shell.recents.RecentsTransitionStateListener.TRANSITION_STATE_ANIMATING
import com.android.wm.shell.recents.RecentsTransitionStateListener.TRANSITION_STATE_REQUESTED
import com.android.wm.shell.shared.R as SharedR
import com.android.wm.shell.shared.TransactionPool
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource.UNKNOWN
import com.android.wm.shell.shared.desktopmode.FakeDesktopConfig
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.shared.split.SplitScreenConstants
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_INDEX_UNDEFINED
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT
import com.android.wm.shell.splitscreen.SplitMultiDisplayProvider
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.sysui.OverviewVisibilityChangeListener
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.FocusTransitionObserver
import com.android.wm.shell.transition.OneShotRemoteHandler
import com.android.wm.shell.transition.TestRemoteTransition
import com.android.wm.shell.transition.TransitionLeashManager
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TransitionHandler
import com.android.wm.shell.windowdecor.DesktopModeWindowDecorViewModelTestsBase.Companion.HOME_LAUNCHER_PACKAGE_NAME
import com.android.wm.shell.windowdecor.tiling.SnapEventHandler
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.Optional
import java.util.function.Consumer
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.isA
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.mockito.verification.VerificationMode

/**
 * Test class for {@link DesktopTasksController}
 *
 * Usage: atest WMShellUnitTests:DesktopTasksControllerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
@ExperimentalCoroutinesApi
@EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
class DesktopTasksControllerTest : ShellTestCase() {

    @Mock lateinit var testExecutor: ShellExecutor
    @Mock lateinit var shellCommandHandler: ShellCommandHandler
    @Mock lateinit var shellController: ShellController
    @Mock lateinit var displayController: DisplayController
    @Mock lateinit var displayLayout: DisplayLayout
    @Mock lateinit var display: Display
    @Mock lateinit var shellTaskOrganizer: ShellTaskOrganizer
    @Mock lateinit var syncQueue: SyncTransactionQueue
    @Mock lateinit var rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer
    @Mock lateinit var transitions: Transitions
    @Mock lateinit var transitionsLeashManager: TransitionLeashManager
    @Mock lateinit var keyguardManager: KeyguardManager
    @Mock lateinit var mReturnToDragStartAnimator: ReturnToDragStartAnimator
    @Mock lateinit var desktopMixedTransitionHandler: DesktopMixedTransitionHandler
    @Mock lateinit var exitDesktopTransitionHandler: ExitDesktopTaskTransitionHandler
    @Mock lateinit var enterDesktopTransitionHandler: EnterDesktopTaskTransitionHandler
    @Mock lateinit var dragAndDropTransitionHandler: DesktopModeDragAndDropTransitionHandler
    @Mock
    lateinit var toggleResizeDesktopTaskTransitionHandler: ToggleResizeDesktopTaskTransitionHandler
    @Mock lateinit var dragToDesktopTransitionHandler: DragToDesktopTransitionHandler
    @Mock lateinit var mMockDesktopImmersiveController: DesktopImmersiveController
    @Mock lateinit var mDesktopFullscreenRequestHandler: DesktopFullscreenRequestHandler
    @Mock lateinit var splitScreenController: SplitScreenController
    @Mock lateinit var splitMultiDisplayProvider: SplitMultiDisplayProvider
    @Mock lateinit var recentsTransitionHandler: RecentsTransitionHandler
    @Mock lateinit var dragAndDropController: DragAndDropController
    @Mock lateinit var multiInstanceHelper: MultiInstanceHelper
    @Mock lateinit var desktopModeVisualIndicator: DesktopModeVisualIndicator
    @Mock lateinit var recentTasksController: RecentTasksController
    @Mock lateinit var snapEventHandler: SnapEventHandler
    @Mock lateinit var pipScheduler: PipScheduler
    @Mock private lateinit var mockInteractionJankMonitor: InteractionJankMonitor
    @Mock private lateinit var mockSurface: SurfaceControl
    @Mock private lateinit var freeformTaskTransitionStarter: FreeformTaskTransitionStarter
    @Mock private lateinit var mockHandler: Handler
    @Mock private lateinit var focusTransitionObserver: FocusTransitionObserver
    @Mock private lateinit var desktopModeEventLogger: DesktopModeEventLogger
    @Mock private lateinit var desktopModeUiEventLogger: DesktopModeUiEventLogger
    @Mock lateinit var persistentRepository: DesktopPersistentRepository
    @Mock lateinit var motionEvent: MotionEvent
    @Mock lateinit var repositoryInitializer: DesktopRepositoryInitializer
    @Mock private lateinit var mockToast: Toast
    private lateinit var mockitoSession: StaticMockitoSession
    @Mock private lateinit var bubbleController: BubbleController
    @Mock private lateinit var desktopRemoteListener: DesktopRemoteListener
    @Mock private lateinit var userManager: UserManager
    @Mock
    private lateinit var desktopWallpaperActivityTokenProvider:
        DesktopWallpaperActivityTokenProvider
    @Mock
    private lateinit var overviewToDesktopTransitionObserver: OverviewToDesktopTransitionObserver
    @Mock private lateinit var desksOrganizer: DesksOrganizer
    @Mock private lateinit var userProfileContexts: UserProfileContexts
    @Mock private lateinit var desksTransitionsObserver: DesksTransitionObserver
    @Mock private lateinit var packageManager: PackageManager
    @Mock private lateinit var windowDragTransitionHandler: WindowDragTransitionHandler
    @Mock private lateinit var deskSwitchTransitionHandler: DeskSwitchTransitionHandler
    @Mock
    private lateinit var moveToDisplayTransitionHandler: DesktopModeMoveToDisplayTransitionHandler
    @Mock private lateinit var mockAppOpsManager: AppOpsManager
    @Mock private lateinit var visualIndicatorUpdateScheduler: VisualIndicatorUpdateScheduler
    @Mock private lateinit var taskSnapshotManager: TaskSnapshotManager
    @Mock private lateinit var transactionPool: TransactionPool
    @Mock private lateinit var pipTransitionState: PipTransitionState
    @Mock private lateinit var surfaceControlTransaction: SurfaceControl.Transaction
    @Mock private lateinit var lockTaskChangeListener: LockTaskChangeListener
    @Mock private lateinit var launcherApps: LauncherApps
    @Mock private lateinit var transitionStateHolder: TransitionStateHolder
    @Mock private lateinit var fullscreenDisconnectHandler: FullscreenDisconnectHandler
    @Mock private lateinit var pipDisconnectHandler: PipDisplayDisconnectHandler
    @Mock private lateinit var pinnedLayerController: PinnedLayerController
    @Mock private lateinit var desktopTasksTransitionObserver: DesktopTasksTransitionObserver
    @Mock private lateinit var desktopAnimationConfiguration: DesktopAnimationConfiguration
    @Mock private lateinit var desktopWallpaperActivityUtils: DesktopWallpaperActivityUtils

    private lateinit var controller: DesktopTasksController
    private lateinit var shellInit: ShellInit
    private lateinit var displayDisconnectTransitionHandler: DisplayDisconnectTransitionHandler
    private lateinit var taskRepository: DesktopRepository
    private lateinit var userRepositories: DesktopUserRepositories
    private lateinit var desktopTasksLimiter: DesktopTasksLimiter
    private lateinit var recentsTransitionStateListener: RecentsTransitionStateListener
    private lateinit var desktopModeCompatPolicy: DesktopModeCompatPolicy
    private lateinit var spyContext: TestableContext
    private lateinit var homeIntentProvider: HomeIntentProvider
    private lateinit var desktopState: FakeDesktopState
    private lateinit var shellDesktopState: FakeShellDesktopState
    private lateinit var desktopConfig: FakeDesktopConfig
    private lateinit var desksController: DesksController
    private lateinit var snapController: SnapController

    private val shellExecutor = TestShellExecutor()
    private val bgExecutor = TestShellExecutor()
    private val testScope = TestScope()
    private val testScopeImmediate = TestScope()

    // Mock running tasks are registered here so we can get the list from mock shell task organizer
    private val runningTasks = mutableListOf<RunningTaskInfo>()

    private val DEFAULT_USER_ID = ActivityManager.getCurrentUser()
    private val DEFAULT_USER_WORK_PROFILE_ID = 100
    private val SECONDARY_USER_ID = DEFAULT_USER_ID + 1

    private val SECONDARY_DISPLAY_ID = 1
    private val DISPLAY_DIMENSION_SHORT = 1600
    private val DISPLAY_DIMENSION_LONG = 2560
    private val DEFAULT_LANDSCAPE_BOUNDS = Rect(358, 93, 2201, 1245)
    private val DEFAULT_PORTRAIT_BOUNDS = Rect(224, 193, 1376, 2036)
    private val RESIZABLE_LANDSCAPE_BOUNDS = Rect(25, 435, 1575, 1635)
    private val RESIZABLE_PORTRAIT_BOUNDS = Rect(680, 93, 1880, 1245)
    private val UNRESIZABLE_LANDSCAPE_BOUNDS = Rect(25, 448, 1575, 1611)
    private val UNRESIZABLE_PORTRAIT_BOUNDS = Rect(848, 93, 1712, 1245)
    private val wallpaperToken = MockToken().token()
    private val homeComponentName = ComponentName(HOME_LAUNCHER_PACKAGE_NAME, /* class */ "")
    private val secondDisplayArea =
        DisplayAreaInfo(MockToken().token(), SECOND_DISPLAY, /* featureId= */ 0)

    @Before
    fun setUp() {
        mockitoSession =
            mockitoSession()
                .strictness(Strictness.LENIENT)
                .spyStatic(Toast::class.java)
                .startMocking()

        desktopState = FakeDesktopState()
        shellDesktopState = FakeShellDesktopState(desktopState)
        desktopConfig = FakeDesktopConfig()
        desktopState.canEnterDesktopMode = true

        spyContext = spy(mContext)
        spyContext.setMockPackageManager(packageManager)
        shellInit = spy(ShellInit(testExecutor))
        whenever(shellController.currentUserId).thenReturn(DEFAULT_USER_ID)
        whenever(shellController.currentUserProfiles)
            .thenReturn(
                listOf(
                    UserInfo(DEFAULT_USER_ID, "default", FLAG_FULL or FLAG_MAIN),
                    UserInfo(DEFAULT_USER_WORK_PROFILE_ID, "default_work", FLAG_PROFILE),
                )
            )
        whenever(desktopAnimationConfiguration.toDesktopAnimationDurationMs)
            .thenReturn(TO_DESKTOP_ANIM_DURATION)

        userRepositories =
            DesktopUserRepositories(
                shellInit,
                shellController,
                persistentRepository,
                repositoryInitializer,
                testScope.backgroundScope,
                testScope.backgroundScope,
                userManager,
                desktopState,
                desktopConfig,
            )
        snapController = SnapController()
        desktopTasksLimiter =
            DesktopTasksLimiter(
                transitions,
                userRepositories,
                shellTaskOrganizer,
                desksOrganizer,
                desktopMixedTransitionHandler,
                snapController,
                MAX_TASK_LIMIT,
            )
        desktopModeCompatPolicy = spy(DesktopModeCompatPolicy(spyContext))
        homeIntentProvider = HomeIntentProvider(context)

        mContext
            .getOrCreateTestableResources()
            .addOverride(SharedR.integer.to_desktop_animation_duration_ms, TO_DESKTOP_ANIM_DURATION)

        whenever(shellTaskOrganizer.getRunningTasks(anyInt())).thenAnswer { runningTasks }
        whenever(shellTaskOrganizer.getRunningTasks()).thenAnswer { runningTasks }
        whenever(transitions.leashManager).thenReturn(transitionsLeashManager)
        whenever(transitions.startTransition(anyInt(), any(), anyOrNull())).thenAnswer { Binder() }
        whenever(enterDesktopTransitionHandler.moveToDesktop(any(), any())).thenAnswer { Binder() }
        whenever(exitDesktopTransitionHandler.startTransition(any(), any(), any(), any()))
            .thenReturn(Binder())

        mockStartLaunchTransition()
        whenever(deskSwitchTransitionHandler.startTransition(any(), any(), any(), any(), any()))
            .thenReturn(Binder())
        whenever(displayController.getDisplayLayout(anyInt())).thenReturn(displayLayout)
        whenever(displayController.getDisplayContext(anyInt())).thenReturn(mContext)
        whenever(displayController.getDisplay(anyInt())).thenReturn(display)
        whenever(displayController.getDisplayUniqueId(SECONDARY_DISPLAY_ID))
            .thenReturn(SECOND_DISPLAY_UNIQUE_ID)
        whenever(displayController.getDisplayUniqueId(DEFAULT_DISPLAY))
            .thenReturn(DEFAULT_DISPLAY_UNIQUE_ID)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(STABLE_BOUNDS)
        }
        whenever(displayLayout.width()).thenReturn(STABLE_BOUNDS.width())
        whenever(displayLayout.height()).thenReturn(STABLE_BOUNDS.height())
        whenever(displayLayout.densityDpi()).thenReturn(160)
        whenever(runBlocking { persistentRepository.readDesktop(any(), any()) })
            .thenReturn(Desktop.getDefaultInstance())
        whenever(display.type).thenReturn(Display.TYPE_INTERNAL)
        doReturn(mockToast).`when` { Toast.makeText(any(), anyInt(), anyInt()) }

        val tda = DisplayAreaInfo(MockToken().token(), DEFAULT_DISPLAY, 0)
        tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
        whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)).thenReturn(tda)
        whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(SECONDARY_DISPLAY_ID))
            .thenReturn(tda)
        whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(SECOND_DISPLAY))
            .thenReturn(secondDisplayArea)
        whenever(
                mMockDesktopImmersiveController.exitImmersiveIfApplicable(
                    any(),
                    any<RunningTaskInfo>(),
                    any(),
                )
            )
            .thenReturn(ExitResult.NoExit)
        whenever(
                mMockDesktopImmersiveController.exitImmersiveIfApplicable(
                    any(),
                    anyInt(),
                    anyOrNull(),
                    any(),
                )
            )
            .thenReturn(ExitResult.NoExit)
        whenever(desktopWallpaperActivityTokenProvider.getToken()).thenReturn(wallpaperToken)
        whenever(desktopWallpaperActivityUtils.hasDesktopWallpaperActivityEnabled(any()))
            .thenReturn(true)
        whenever(userProfileContexts[anyInt()]).thenReturn(context)
        whenever(userProfileContexts.getOrCreate(anyInt())).thenReturn(context)
        whenever(freeformTaskTransitionStarter.startPipTransition(any())).thenReturn(Binder())
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECONDARY_DISPLAY_ID))
        whenever(transactionPool.acquire()).thenReturn(surfaceControlTransaction)
        whenever(splitScreenController.multiDisplayProvider).thenReturn(splitMultiDisplayProvider)

        desksController =
            DesksController(
                shellController,
                userRepositories,
                desktopConfig,
                desktopState,
                displayController,
                desksOrganizer,
                shellTaskOrganizer,
            )

        controller = createController()
        controller.setSplitScreenController(splitScreenController)
        controller.freeformTaskTransitionStarter = freeformTaskTransitionStarter

        displayDisconnectTransitionHandler =
            DisplayDisconnectTransitionHandler(
                transitions = transitions,
                shellInit = shellInit,
                splitScreenController = Optional.of(splitScreenController),
                desktopTasksController = Optional.of(controller),
                fullscreenDisconnectHandler = Optional.of(fullscreenDisconnectHandler),
                pinnedLayerController = Optional.of(pinnedLayerController),
                pipDisplayDisconnectHandler = Optional.of(pipDisconnectHandler),
            )

        shellInit.init()

        val captor = argumentCaptor<RecentsTransitionStateListener>()
        verify(recentsTransitionHandler).addTransitionStateListener(captor.capture())
        recentsTransitionStateListener = captor.firstValue

        snapController.start(snapEventHandler)
        controller.setPipScheduler(pipScheduler)

        taskRepository = userRepositories.current
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = DEFAULT_DISPLAY)
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = DEFAULT_DISPLAY)
        shellDesktopState.canBeWindowDropTarget = true

        doReturn(HOME_LAUNCHER_PACKAGE_NAME)
            .whenever(desktopModeCompatPolicy)
            .getDefaultHomePackage(any())

        whenever(pipDisconnectHandler.onDisplayDisconnect(anyInt(), anyInt()))
            .thenReturn(WindowContainerTransaction())
    }

    private fun createController() =
        DesktopTasksController(
            spyContext,
            desktopAnimationConfiguration,
            shellInit,
            shellCommandHandler,
            shellController,
            displayController,
            shellTaskOrganizer,
            syncQueue,
            rootTaskDisplayAreaOrganizer,
            dragAndDropController,
            transitions,
            keyguardManager,
            mReturnToDragStartAnimator,
            desktopMixedTransitionHandler,
            enterDesktopTransitionHandler,
            exitDesktopTransitionHandler,
            dragAndDropTransitionHandler,
            toggleResizeDesktopTaskTransitionHandler,
            dragToDesktopTransitionHandler,
            mMockDesktopImmersiveController,
            mDesktopFullscreenRequestHandler,
            userRepositories,
            repositoryInitializer,
            recentsTransitionHandler,
            multiInstanceHelper,
            shellExecutor,
            testScope.backgroundScope,
            testScopeImmediate.backgroundScope,
            bgExecutor,
            Optional.of(desktopTasksLimiter),
            recentTasksController,
            mockInteractionJankMonitor,
            mockHandler,
            focusTransitionObserver,
            desktopModeEventLogger,
            desktopModeUiEventLogger,
            desktopWallpaperActivityTokenProvider,
            Optional.of(bubbleController),
            overviewToDesktopTransitionObserver,
            desksOrganizer,
            desksTransitionsObserver,
            userProfileContexts,
            desktopModeCompatPolicy,
            windowDragTransitionHandler,
            deskSwitchTransitionHandler,
            moveToDisplayTransitionHandler,
            homeIntentProvider,
            shellDesktopState,
            desktopConfig,
            visualIndicatorUpdateScheduler,
            taskSnapshotManager,
            transactionPool,
            Optional.of(pipTransitionState),
            lockTaskChangeListener,
            launcherApps,
            transitionStateHolder,
            desksController,
            desktopTasksTransitionObserver,
            snapController,
            desktopRemoteListener,
            desktopWallpaperActivityUtils,
        )

    @After
    fun tearDown() {
        mockitoSession.finishMocking()

        runningTasks.clear()
        testScope.cancel()
    }

    @Test
    fun instantiate_addInitCallback() {
        verify(shellInit).addInitCallback(any(), any<DesktopTasksController>())
    }

    @Test
    fun isAnyTaskMaximizedOrDoubleTiled_maximized_returnTrue() {
        val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
        val task = setUpFreeformTask(bounds = stableBounds)

        assertTrue(
            controller.isAnyTaskMaximizedOrDoubleTiled(
                displayId = DEFAULT_DISPLAY,
                userId = taskRepository.userId,
            )
        )
    }

    @Test
    fun isAnyTaskMaximizedOrDoubleTiled_maximizedButExcluded_returnFalse() {
        val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
        val task = setUpFreeformTask(bounds = stableBounds)

        assertFalse(
            controller.isAnyTaskMaximizedOrDoubleTiled(
                displayId = DEFAULT_DISPLAY,
                userId = taskRepository.userId,
                excludeTaskId = task.taskId,
            )
        )
    }

    @Test
    fun isAnyTaskMaximizedOrDoubleTiled_maximizedButInvisible_returnFalse() {
        val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
        val task = setUpFreeformTask(bounds = stableBounds)
        taskRepository.updateTask(
            displayId = DEFAULT_DISPLAY,
            taskId = task.taskId,
            isVisible = false,
            taskBounds = stableBounds,
        )

        assertFalse(
            controller.isAnyTaskMaximizedOrDoubleTiled(
                displayId = DEFAULT_DISPLAY,
                userId = taskRepository.userId,
            )
        )
    }

    @Test
    fun isAnyTaskMaximizedOrDoubleTiled_singleTiled_returnFalse() {
        val task1 = setUpFreeformTask(bounds = TASK_BOUNDS)

        val task2 = setUpFreeformTask(bounds = TASK_BOUNDS)
        taskRepository.addLeftTiledTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            taskId = task2.taskId,
            deskId = DEFAULT_DISPLAY,
        )

        assertFalse(
            controller.isAnyTaskMaximizedOrDoubleTiled(
                displayId = DEFAULT_DISPLAY,
                userId = taskRepository.userId,
            )
        )
    }

    @Test
    fun isAnyTaskMaximizedOrDoubleTiled_doubleTiled_returnTrue() {
        val task1 = setUpFreeformTask(bounds = TASK_BOUNDS)
        taskRepository.addLeftTiledTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            taskId = task1.taskId,
            deskId = DEFAULT_DISPLAY,
        )

        val task2 = setUpFreeformTask(bounds = TASK_BOUNDS)
        taskRepository.addRightTiledTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            taskId = task2.taskId,
            deskId = DEFAULT_DISPLAY,
        )

        assertTrue(
            controller.isAnyTaskMaximizedOrDoubleTiled(
                displayId = DEFAULT_DISPLAY,
                userId = taskRepository.userId,
            )
        )
    }

    @Test
    fun isAnyTaskMaximizedOrDoubleTiled_doubleTiledButExcluded_returnFalse() {
        val task1 = setUpFreeformTask(bounds = TASK_BOUNDS)
        taskRepository.addLeftTiledTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            taskId = task1.taskId,
            deskId = DEFAULT_DISPLAY,
        )

        val task2 = setUpFreeformTask(bounds = TASK_BOUNDS)
        taskRepository.addRightTiledTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            taskId = task2.taskId,
            deskId = DEFAULT_DISPLAY,
        )

        assertFalse(
            controller.isAnyTaskMaximizedOrDoubleTiled(
                displayId = DEFAULT_DISPLAY,
                userId = taskRepository.userId,
                excludeTaskId = task1.taskId,
            )
        )
    }

    @Test
    fun isAnyTaskMaximizedOrDoubleTiled_doubleTiledButInvisible_returnFalse() {
        val task1 = setUpFreeformTask(bounds = TASK_BOUNDS)
        taskRepository.addLeftTiledTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            taskId = task1.taskId,
            deskId = DEFAULT_DISPLAY,
        )

        val task2 = setUpFreeformTask(bounds = TASK_BOUNDS)
        taskRepository.addRightTiledTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            taskId = task2.taskId,
            deskId = DEFAULT_DISPLAY,
        )
        taskRepository.updateTask(
            displayId = DEFAULT_DISPLAY,
            taskId = task2.taskId,
            isVisible = false,
            taskBounds = TASK_BOUNDS,
        )

        assertFalse(
            controller.isAnyTaskMaximizedOrDoubleTiled(
                displayId = DEFAULT_DISPLAY,
                userId = taskRepository.userId,
            )
        )
    }

    @Test
    fun isAnyTaskMaximizedOrDoubleTiled_targetDeskSatisfiesCondition_returnTrue() {
        val activeDeskId = taskRepository.getActiveDeskId(DEFAULT_DISPLAY)!!
        val taskInActiveDesk = setUpFreeformTask(bounds = TASK_BOUNDS, deskId = activeDeskId)

        val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
        val targetDeskId = activeDeskId + 1
        taskRepository.addDesk(DEFAULT_DISPLAY, targetDeskId)
        val taskInTargetDesk = setUpFreeformTask(bounds = stableBounds, deskId = targetDeskId)

        assertTrue(
            controller.isAnyTaskMaximizedOrDoubleTiled(
                displayId = DEFAULT_DISPLAY,
                userId = taskRepository.userId,
                targetDeskId = targetDeskId,
            )
        )
    }

    @Test
    fun isAnyTaskMaximizedOrDoubleTiled_targetDeskDoesNotSatisfyCondition_returnFalse() {
        val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
        val activeDeskId = taskRepository.getActiveDeskId(DEFAULT_DISPLAY)!!
        val taskInActiveDesk = setUpFreeformTask(bounds = stableBounds, deskId = activeDeskId)

        val targetDeskId = activeDeskId + 1
        taskRepository.addDesk(DEFAULT_DISPLAY, targetDeskId)
        val taskInTargetDesk = setUpFreeformTask(bounds = TASK_BOUNDS, deskId = targetDeskId)

        assertFalse(
            controller.isAnyTaskMaximizedOrDoubleTiled(
                displayId = DEFAULT_DISPLAY,
                userId = taskRepository.userId,
                targetDeskId = targetDeskId,
            )
        )
    }

    @Test
    fun isAnyTaskMaximizedOrDoubleTiled_pendingMaximizedTaskBounds_returnTrue() {
        val task = setUpFreeformTask(bounds = TASK_BOUNDS)
        val stableBounds = Rect().also { displayLayout.getStableBounds(it) }

        assertTrue(
            controller.isAnyTaskMaximizedOrDoubleTiled(
                displayId = DEFAULT_DISPLAY,
                userId = taskRepository.userId,
                pendingTaskBounds = stableBounds,
            )
        )
    }

    @Test
    fun doesAnyTaskRequireTaskbarRounding_onlyFreeFormTaskIsRunning_returnFalse() {
        setUpFreeformTask()

        assertThat(
                controller.isAnyTaskMaximizedOrSnapped(
                    displayId = DEFAULT_DISPLAY,
                    userId = taskRepository.userId,
                )
            )
            .isFalse()
    }

    @Test
    fun doesAnyTaskRequireTaskbarRounding_toggleResizeOfFreeFormTask_returnTrue() {
        val task1 = setUpFreeformTask()

        val argumentCaptor = argumentCaptor<Boolean>()
        val displayIdCaptor = argumentCaptor<Int>()
        controller.toggleDesktopTaskSize(
            task1,
            ToggleTaskSizeInteraction(
                ToggleTaskSizeInteraction.Direction.MAXIMIZE,
                ToggleTaskSizeInteraction.Source.HEADER_BUTTON_TO_MAXIMIZE,
                InputMethod.TOUCH,
            ),
        )

        verify(desktopRemoteListener)
            .onTaskbarCornerRoundingUpdate(argumentCaptor.capture(), displayIdCaptor.capture())
        verify(desktopModeEventLogger, times(1))
            .logTaskResizingEnded(
                resizeTrigger = ResizeTrigger.MAXIMIZE_BUTTON,
                inputMethod = InputMethod.TOUCH,
                taskInfo = task1,
                taskWidth = STABLE_BOUNDS.width(),
                taskHeight = STABLE_BOUNDS.height(),
                displayController = displayController,
                deskId = 0,
            )
        assertThat(argumentCaptor.firstValue).isTrue()
        assertThat(displayIdCaptor.firstValue).isEqualTo(task1.displayId)
    }

    @Test
    fun doesAnyTaskRequireTaskbarRounding_fullScreenTaskIsRunning_returnTrue() {
        val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
        setUpFreeformTask(bounds = stableBounds, active = true)
        assertThat(
                controller.isAnyTaskMaximizedOrSnapped(
                    displayId = DEFAULT_DISPLAY,
                    userId = taskRepository.userId,
                )
            )
            .isTrue()
    }

    @Test
    fun doesAnyTaskRequireTaskbarRounding_toggleResizeOfMaximizedTask_returnFalse() {
        val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
        val task1 = setUpFreeformTask(bounds = stableBounds, active = true)

        val argumentCaptor = argumentCaptor<Boolean>()
        val displayIdCaptor = argumentCaptor<Int>()
        controller.toggleDesktopTaskSize(
            task1,
            ToggleTaskSizeInteraction(
                ToggleTaskSizeInteraction.Direction.RESTORE,
                ToggleTaskSizeInteraction.Source.HEADER_BUTTON_TO_RESTORE,
                InputMethod.TOUCH,
            ),
        )

        verify(desktopRemoteListener)
            .onTaskbarCornerRoundingUpdate(argumentCaptor.capture(), displayIdCaptor.capture())
        verify(desktopModeEventLogger, times(1))
            .logTaskResizingEnded(
                eq(ResizeTrigger.MAXIMIZE_BUTTON),
                eq(InputMethod.TOUCH),
                eq(task1),
                anyOrNull(),
                anyOrNull(),
                eq(displayController),
                anyOrNull(),
            )
        assertThat(argumentCaptor.firstValue).isFalse()
        assertThat(displayIdCaptor.firstValue).isEqualTo(task1.displayId)
    }

    @Test
    fun doesAnyTaskRequireTaskbarRounding_splitScreenTaskIsRunning_returnTrue() {
        val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
        setUpFreeformTask(
            bounds = Rect(stableBounds.left, stableBounds.top, 500, stableBounds.bottom)
        )

        assertThat(
                controller.isAnyTaskMaximizedOrSnapped(
                    displayId = DEFAULT_DISPLAY,
                    userId = taskRepository.userId,
                )
            )
            .isTrue()
    }

    @Test
    fun getTopTask_oneTask_excludingTaskId_returnInvalidId() {
        val displayId = 7
        val deskId = displayId
        val userId = taskRepository.userId
        taskRepository.addDesk(displayId, deskId)

        val task = setUpFreeformTask(displayId = displayId, deskId = deskId)
        taskRepository.setActiveDesk(displayId, deskId)

        assertThat(controller.getTopTask(displayId, userId, task.taskId)).isEqualTo(INVALID_TASK_ID)
    }

    @Test
    fun getTopTask_twoTasks_returnTopTask() {
        val displayId = 7
        val deskId = displayId
        val userId = taskRepository.userId
        taskRepository.addDesk(displayId, deskId)

        val otherTask = setUpFreeformTask(displayId = displayId, deskId = deskId)
        val task = setUpFreeformTask(displayId = displayId, deskId = deskId)
        taskRepository.setActiveDesk(displayId, deskId)

        assertThat(controller.getTopTask(displayId, userId, task.taskId))
            .isEqualTo(otherTask.taskId)
        assertThat(controller.getTopTask(displayId, userId)).isEqualTo(task.taskId)
    }

    @Test
    fun getTopTask_multipleTasks_returnTopTask() {
        val displayId = 7
        val deskId = displayId
        val userId = taskRepository.userId
        taskRepository.addDesk(displayId, deskId)

        val otherTask = setUpFreeformTask(displayId = displayId, deskId = deskId)
        val otherTask2 = setUpFreeformTask(displayId = displayId, deskId = deskId)
        val otherTask3 = setUpFreeformTask(displayId = displayId, deskId = deskId)
        val task = setUpFreeformTask(displayId = displayId, deskId = deskId)
        taskRepository.setActiveDesk(displayId, deskId)

        assertThat(controller.getTopTask(displayId, userId, task.taskId))
            .isNotEqualTo(otherTask.taskId)
        assertThat(controller.getTopTask(displayId, userId, task.taskId))
            .isNotEqualTo(otherTask2.taskId)
        assertThat(controller.getTopTask(displayId, userId, task.taskId)).isNotEqualTo(task.taskId)
        assertThat(controller.getTopTask(displayId, userId, task.taskId))
            .isEqualTo(otherTask3.taskId)

        assertThat(controller.getTopTask(displayId, userId)).isNotEqualTo(otherTask.taskId)
        assertThat(controller.getTopTask(displayId, userId)).isNotEqualTo(otherTask2.taskId)
        assertThat(controller.getTopTask(displayId, userId)).isNotEqualTo(otherTask3.taskId)
        assertThat(controller.getTopTask(displayId, userId)).isEqualTo(task.taskId)
    }

    @Test
    fun getTopTask_tasksOnInactiveDesk_noActiveDeskOnDisplay_returnInvalidId() {
        val displayId = 7
        val userId = taskRepository.userId
        val inactiveDeskId = 19
        taskRepository.addDesk(displayId, inactiveDeskId)

        val otherTask = setUpFreeformTask(displayId = displayId, deskId = inactiveDeskId)
        val task = setUpFreeformTask(displayId = displayId, deskId = inactiveDeskId)
        taskRepository.setDeskInactive(inactiveDeskId)

        assertThat(controller.getTopTask(displayId, userId, task.taskId)).isEqualTo(INVALID_TASK_ID)
        assertThat(controller.getTopTask(displayId, userId)).isEqualTo(INVALID_TASK_ID)
    }

    @Test
    fun instantiate_cannotEnterDesktopMode_doNotAddInitCallback() {
        desktopState.canEnterDesktopMode = false
        clearInvocations(shellInit)

        createController()

        verify(shellInit, never()).addInitCallback(any(), any<DesktopTasksController>())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    fun showDesktopApps_deskInactive_bringsToFront_multipleDesksEnabled() {
        whenever(transitions.startTransition(eq(TRANSIT_TO_FRONT), any(), anyOrNull()))
            .thenReturn(Binder())
        val deskId = 0
        // Make desk inactive by activating another desk.
        taskRepository.addDesk(DEFAULT_DISPLAY, deskId = 1)
        taskRepository.setActiveDesk(DEFAULT_DISPLAY, deskId = 1)

        controller.activateDesk(
            deskId = deskId,
            userId = taskRepository.userId,
            remoteTransition = RemoteTransition(TestRemoteTransition()),
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        val wct =
            getLatestWct(type = TRANSIT_TO_FRONT, handlerClass = OneShotRemoteHandler::class.java)
        // Wallpaper is moved to front.
        wct.assertReorderAt(index = 0, wallpaperToken)
        // Desk is activated.
        verify(desksOrganizer).activateDesk(wct, deskId)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY,
    )
    fun showDesktopApps_onSecondaryDisplay_desktopWallpaperEnabled_perDisplayWallpaperEnabled_multipleDesksEnabled_bringsDeskToFront() {
        whenever(transitions.startTransition(eq(TRANSIT_TO_FRONT), any(), anyOrNull()))
            .thenReturn(Binder())
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = 2)
        setUpHomeTask(SECOND_DISPLAY)

        controller.showDesktopApps(
            displayId = SECOND_DISPLAY,
            remoteTransition = RemoteTransition(TestRemoteTransition()),
            transitionSource = UNKNOWN,
        )

        val wct =
            getLatestWct(type = TRANSIT_TO_FRONT, handlerClass = OneShotRemoteHandler::class.java)
        verify(desksOrganizer).activateDesk(wct, deskId = 2)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY,
    )
    fun showDesktopApps_onSecondaryDisplay_desktopWallpaperEnabled_perDisplayWallpaperEnabled_shouldShowWallpaper() {
        whenever(transitions.startTransition(eq(TRANSIT_TO_FRONT), any(), anyOrNull()))
            .thenReturn(Binder())
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        setUpHomeTask(SECOND_DISPLAY)

        controller.showDesktopApps(
            displayId = SECOND_DISPLAY,
            remoteTransition = RemoteTransition(TestRemoteTransition()),
            transitionSource = UNKNOWN,
        )

        val wct =
            getLatestWct(type = TRANSIT_TO_FRONT, handlerClass = OneShotRemoteHandler::class.java)
        wct.assertPendingIntent(desktopWallpaperIntent)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    @DisableFlags(Flags.FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY)
    fun showDesktopApps_onSecondaryDisplay_desktopWallpaperEnabled_shouldNotShowWallpaper() {
        whenever(transitions.startTransition(eq(TRANSIT_TO_FRONT), any(), anyOrNull()))
            .thenReturn(Binder())
        whenever(desktopWallpaperActivityUtils.hasDesktopWallpaperActivityEnabled(any()))
            .thenReturn(false)
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        val homeTask = setUpHomeTask(SECOND_DISPLAY)

        controller.showDesktopApps(
            displayId = SECOND_DISPLAY,
            remoteTransition = RemoteTransition(TestRemoteTransition()),
            transitionSource = UNKNOWN,
        )

        val wct =
            getLatestWct(type = TRANSIT_TO_FRONT, handlerClass = OneShotRemoteHandler::class.java)
        wct.assertWithoutPendingIntent(desktopWallpaperIntent)
    }

    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    fun showDesktopApps_someAppsInvisible_desktopWallpaperEnabled_reordersAll() {
        val task1 = setUpFreeformTask()
        val task2 = setUpFreeformTask()
        markTaskHidden(task1)
        markTaskVisible(task2)

        controller.showDesktopApps(
            displayId = DEFAULT_DISPLAY,
            remoteTransition = RemoteTransition(TestRemoteTransition()),
            transitionSource = UNKNOWN,
        )

        val wct =
            getLatestWct(type = TRANSIT_TO_FRONT, handlerClass = OneShotRemoteHandler::class.java)
        assertThat(wct.hierarchyOps).hasSize(3)
        // Expect order to be from bottom: wallpaper intent, task1, task2
        wct.assertReorderAt(index = 0, wallpaperToken)
        wct.assertReorderAt(index = 1, task1)
        wct.assertReorderAt(index = 2, task2)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    fun showDesktopApps_noActiveTasks_desktopWallpaperEnabled_addsDesktopWallpaper() {
        whenever(desktopWallpaperActivityTokenProvider.getToken()).thenReturn(null)

        controller.showDesktopApps(
            displayId = DEFAULT_DISPLAY,
            remoteTransition = RemoteTransition(TestRemoteTransition()),
            transitionSource = UNKNOWN,
        )
        val wct =
            getLatestWct(type = TRANSIT_TO_FRONT, handlerClass = OneShotRemoteHandler::class.java)
        wct.assertPendingIntentAt(index = 0, desktopWallpaperIntent)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    fun showDesktopApps_twoDisplays_bringsToFrontOnlyOneDisplay_desktopWallpaperEnabled() {
        whenever(transitions.startTransition(eq(TRANSIT_TO_FRONT), any(), anyOrNull()))
            .thenReturn(Binder())
        whenever(desktopWallpaperActivityTokenProvider.getToken()).thenReturn(null)
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        val homeTaskDefaultDisplay = setUpHomeTask(DEFAULT_DISPLAY)
        val taskDefaultDisplay = setUpFreeformTask(DEFAULT_DISPLAY)
        setUpHomeTask(SECOND_DISPLAY)
        val taskSecondDisplay = setUpFreeformTask(SECOND_DISPLAY)
        markTaskHidden(taskDefaultDisplay)
        markTaskHidden(taskSecondDisplay)

        controller.showDesktopApps(
            displayId = DEFAULT_DISPLAY,
            remoteTransition = RemoteTransition(TestRemoteTransition()),
            transitionSource = UNKNOWN,
        )

        val wct =
            getLatestWct(type = TRANSIT_TO_FRONT, handlerClass = OneShotRemoteHandler::class.java)
        // Move home to front
        wct.assertReorderAt(index = 0, homeTaskDefaultDisplay)
        // Add desktop wallpaper activity
        wct.assertPendingIntentAt(index = 1, desktopWallpaperIntent)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    fun showDesktopApps_twoDisplays_bringsToFrontOnlyOneDisplayTasks_desktopWallpaperEnabled_multiDesksEnabled() {
        whenever(transitions.startTransition(eq(TRANSIT_TO_FRONT), any(), anyOrNull()))
            .thenReturn(Binder())
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        val homeTaskDefaultDisplay = setUpHomeTask(DEFAULT_DISPLAY)
        val taskDefaultDisplay = setUpFreeformTask(DEFAULT_DISPLAY)
        setUpHomeTask(SECOND_DISPLAY)
        val taskSecondDisplay = setUpFreeformTask(SECOND_DISPLAY)
        markTaskHidden(taskDefaultDisplay)
        markTaskHidden(taskSecondDisplay)

        controller.showDesktopApps(
            displayId = DEFAULT_DISPLAY,
            remoteTransition = RemoteTransition(TestRemoteTransition()),
            transitionSource = UNKNOWN,
        )

        val wct =
            getLatestWct(type = TRANSIT_TO_FRONT, handlerClass = OneShotRemoteHandler::class.java)
        // Move desktop tasks to front
        verify(desksOrganizer).activateDesk(wct, deskId = DEFAULT_DISPLAY)
    }

    @Test
    fun isAnyDeskActive_noActiveDesk_returnsFalse() {
        taskRepository.setDeskInactive(deskId = 0)

        assertThat(controller.isAnyDeskActive(DEFAULT_DISPLAY)).isFalse()
    }

    @Test
    fun isAnyDeskActive_withActiveDesk_returnsTrue() {
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)

        assertThat(controller.isAnyDeskActive(DEFAULT_DISPLAY)).isTrue()
    }

    @Test
    fun addMoveToDeskTaskChanges_gravityLeft_gravityAppliedOnBounds() {
        setUpLandscapeDisplay()
        val stableBounds = Rect().also { displayLayout.getStableBoundsForDesktopMode(it) }
        val task = setUpFullscreenTask(gravity = Gravity.LEFT)
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        val finalBounds = findBoundsChange(wct, task)
        assertThat(finalBounds!!.left).isEqualTo(stableBounds.left)
    }

    @Test
    fun addMoveToDeskTaskChanges_gravityRight_gravityAppliedOnBounds() {
        setUpLandscapeDisplay()
        val stableBounds = Rect().also { displayLayout.getStableBoundsForDesktopMode(it) }
        val task = setUpFullscreenTask(gravity = Gravity.RIGHT)
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        val finalBounds = findBoundsChange(wct, task)
        assertThat(finalBounds!!.right).isEqualTo(stableBounds.right)
    }

    @Test
    fun addMoveToDeskTaskChanges_gravityTop_gravityAppliedOnBounds() {
        setUpLandscapeDisplay()
        val stableBounds = Rect().also { displayLayout.getStableBoundsForDesktopMode(it) }
        val task = setUpFullscreenTask(gravity = Gravity.TOP)
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        val finalBounds = findBoundsChange(wct, task)
        assertThat(finalBounds!!.top).isEqualTo(stableBounds.top)
    }

    @Test
    fun addMoveToDeskTaskChanges_gravityBottom_gravityAppliedOnBounds() {
        setUpLandscapeDisplay()
        val stableBounds = Rect().also { displayLayout.getStableBoundsForDesktopMode(it) }
        val task = setUpFullscreenTask(gravity = Gravity.BOTTOM)
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        val finalBounds = findBoundsChange(wct, task)
        assertThat(finalBounds!!.bottom).isEqualTo(stableBounds.bottom)
    }

    @Test
    fun addMoveToDeskTaskChanges_newTaskInstance_inheritsClosingInstanceBounds() {
        // Setup existing task.
        val existingTask = setUpFreeformTask(active = true)
        val testComponent = ComponentName(/* package */ "test.package", /* class */ "test.class")
        existingTask.topActivity = testComponent
        existingTask.configuration.windowConfiguration.setBounds(Rect(0, 0, 500, 500))
        // Set up new instance of already existing task.
        val launchingTask =
            setUpFullscreenTask().apply {
                topActivityInfo = ActivityInfo().apply { launchMode = LAUNCH_SINGLE_INSTANCE }
            }
        launchingTask.topActivity = testComponent

        // Move new instance to desktop. By default multi instance is not supported so first
        // instance will close.
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, launchingTask, deskId = 0)

        // New instance should inherit task bounds of old instance.
        assertThat(findBoundsChange(wct, launchingTask))
            .isEqualTo(existingTask.configuration.windowConfiguration.bounds)
    }

    @Test
    fun addMoveToDeskTaskChanges_newTaskInstanceInDifferentUser_doesntInheritBounds() {
        // Setup existing task.
        val existingTask = setUpFreeformTask(active = true)
        val testComponent = ComponentName(/* package */ "test.package", /* class */ "test.class")
        existingTask.topActivity = testComponent
        existingTask.configuration.windowConfiguration.setBounds(Rect(0, 0, 500, 500))
        // Set up new instance of already existing task in a different user profile.
        val launchingTask =
            setUpFullscreenTask().apply {
                topActivityInfo =
                    ActivityInfo().apply {
                        launchMode = LAUNCH_SINGLE_INSTANCE
                        applicationInfo = ApplicationInfo()
                    }
                userId = DEFAULT_USER_WORK_PROFILE_ID
            }
        launchingTask.topActivity = testComponent

        // Move new instance to desktop.
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, launchingTask, deskId = 0)

        // New instance should not inherit task bounds of old instance.
        assertThat(findBoundsChange(wct, launchingTask))
            .isNotEqualTo(existingTask.configuration.windowConfiguration.bounds)
    }

    @Test
    fun handleRequest_newTaskInstance_inheritsClosingInstanceBounds() {
        setUpLandscapeDisplay()
        // Setup existing task.
        val existingTask = setUpFreeformTask(active = true)
        val testComponent = ComponentName(/* package */ "test.package", /* class */ "test.class")
        existingTask.topActivity = testComponent
        existingTask.configuration.windowConfiguration.setBounds(Rect(0, 0, 500, 500))
        // Set up new instance of already existing task.
        val launchingTask =
            setUpFreeformTask(active = false).apply {
                topActivityInfo = ActivityInfo().apply { launchMode = LAUNCH_SINGLE_INSTANCE }
            }
        taskRepository.removeTask(launchingTask.taskId)
        launchingTask.topActivity = testComponent

        // Move new instance to desktop. By default multi instance is not supported so first
        // instance will close.
        val wct = controller.handleRequest(Binder(), createTransition(launchingTask))

        assertNotNull(wct, "should handle request")
        val finalBounds = findBoundsChange(wct, launchingTask)
        // New instance should inherit task bounds of old instance.
        assertThat(finalBounds).isEqualTo(existingTask.configuration.windowConfiguration.bounds)
    }

    @Test
    fun handleRequest_newTaskInstanceInDifferentUser_doesntInheritBounds() {
        setUpLandscapeDisplay()
        // Setup existing task.
        val existingTask = setUpFreeformTask(active = true)
        val testComponent = ComponentName(/* package */ "test.package", /* class */ "test.class")
        existingTask.topActivity = testComponent
        existingTask.configuration.windowConfiguration.setBounds(Rect(0, 0, 500, 500))
        // Set up new instance of already existing task.
        val launchingTask =
            setUpFreeformTask(active = false).apply {
                topActivityInfo = ActivityInfo().apply { launchMode = LAUNCH_SINGLE_INSTANCE }
                userId = 100
            }
        taskRepository.removeTask(launchingTask.taskId)
        launchingTask.topActivity = testComponent

        // Move new instance to desktop.
        val wct = controller.handleRequest(Binder(), createTransition(launchingTask))

        assertNotNull(wct, "should handle request")
        val finalBounds = findBoundsChange(wct, launchingTask)
        // New instance should not inherit task bounds of old instance.
        assertThat(finalBounds).isNotEqualTo(existingTask.configuration.windowConfiguration.bounds)
    }

    @Test
    fun handleRequest_newFreeformTaskLaunch_cascadeApplied() {
        val stableBounds =
            Rect(0, 0, DISPLAY_DIMENSION_LONG, DISPLAY_DIMENSION_SHORT - TASKBAR_FRAME_HEIGHT)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        setUpLandscapeDisplay()

        setUpFreeformTask(bounds = DEFAULT_LANDSCAPE_BOUNDS)
        val freeformTask = setUpFreeformTask(bounds = DEFAULT_LANDSCAPE_BOUNDS, active = false)

        val wct = controller.handleRequest(Binder(), createTransition(freeformTask))

        assertNotNull(wct, "should handle request")
        val finalBounds = findBoundsChange(wct, freeformTask)
        if (Flags.enableSteppedCascading()) {
            assertThat(stableBounds.getDesktopTaskPosition(finalBounds!!))
                .isNotEqualTo(DEFAULT_LANDSCAPE_BOUNDS)
        } else {
            assertThat(stableBounds.getDesktopTaskPosition(finalBounds!!))
                .isEqualTo(DesktopTaskPosition.BottomRight)
        }
    }

    @Test
    fun handleRequest_newFreeformTaskLaunch_boundsSetFromOptions_cascadeNotApplied() {
        setUpLandscapeDisplay()

        setUpFreeformTask(bounds = DEFAULT_LANDSCAPE_BOUNDS)
        val freeformTask =
            setUpFreeformTask(bounds = DEFAULT_LANDSCAPE_BOUNDS, active = false).apply {
                leafTaskBoundsFromOptions = true
            }

        val wct = controller.handleRequest(Binder(), createTransition(freeformTask))

        assertNotNull(wct, "should handle request")
        val finalBounds = findBoundsChange(wct, freeformTask)
        assertThat(finalBounds).isEqualTo(DEFAULT_LANDSCAPE_BOUNDS)
    }

    @Test
    fun handleRequest_newFreeformTaskLaunch_newDesk_desksCascadeIndependently() {
        setUpLandscapeDisplay()
        val stableBounds =
            Rect(0, 0, DISPLAY_DIMENSION_LONG, DISPLAY_DIMENSION_SHORT - TASKBAR_FRAME_HEIGHT)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }

        // Launch freeform tasks in default desk.
        setUpFreeformTask(bounds = DEFAULT_LANDSCAPE_BOUNDS)
        val freeformTask = setUpFreeformTask(bounds = DEFAULT_LANDSCAPE_BOUNDS, active = false)
        controller.handleRequest(Binder(), createTransition(freeformTask))

        // Create new active desk and launch new task.
        taskRepository.addDesk(DEFAULT_DISPLAY, deskId = 2)
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 2)
        val newDeskTask = createFullscreenTask(displayId = DEFAULT_DISPLAY)
        val wct = controller.handleRequest(Binder(), createTransition(newDeskTask))

        // New task should be cascaded independently of tasks in other desks.
        assertNotNull(wct, "should handle request")
        val finalBounds = findBoundsChange(wct, newDeskTask)
        assertThat(stableBounds.getDesktopTaskPosition(finalBounds!!))
            .isEqualTo(DesktopTaskPosition.Center)
    }

    @Test
    fun handleFreeformTaskPlacement_isTargetDeskActive_bringTaskToFront_activatesDesk() {
        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        val activeDeskId = taskRepository.getActiveDeskId(DEFAULT_DISPLAY)
        assertThat(activeDeskId).isNotNull()

        controller.handleFreeformTaskPlacement(
            task = task,
            transition = Binder(),
            targetDisplayId = DEFAULT_DISPLAY,
            suggestedTargetDeskId = activeDeskId!!,
            requestedTaskBounds = null, // triggers bringTaskToFront = true
            requestType = TRANSIT_TO_FRONT,
            enterReason = EnterReason.TASK_LAUNCH,
        )

        // Verify that even though the desk was already active, we call activateDesk to ensure
        // it's brought to the front.
        verify(desksOrganizer).activateDesk(any(), eq(activeDeskId!!), anyBoolean())
    }

    @Test
    fun handleRequest_freeformTaskAlreadyExistsInDesktopMode_cascadeNotApplied() {
        setUpLandscapeDisplay()
        val stableBounds =
            Rect(0, 0, DISPLAY_DIMENSION_LONG, DISPLAY_DIMENSION_SHORT - TASKBAR_FRAME_HEIGHT)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }

        setUpFreeformTask(bounds = DEFAULT_LANDSCAPE_BOUNDS)
        val freeformTask = setUpFreeformTask(bounds = DEFAULT_LANDSCAPE_BOUNDS)

        val wct = controller.handleRequest(Binder(), createTransition(freeformTask))

        assertNull(wct, "should not handle request")
    }

    @Test
    fun addMoveToDeskTaskChanges_activeButClosingTask_cascadeNotApplied() {
        setUpLandscapeDisplay()
        val stableBounds = Rect().also { displayLayout.getStableBoundsForDesktopMode(it) }

        val closingTask = setUpFreeformTask(bounds = DEFAULT_LANDSCAPE_BOUNDS)
        taskRepository.addClosingTask(
            displayId = DEFAULT_DISPLAY,
            deskId = 0,
            taskId = closingTask.taskId,
        )

        val task = setUpFullscreenTask()
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        val finalBounds = findBoundsChange(wct, task)
        assertThat(stableBounds.getDesktopTaskPosition(finalBounds!!))
            .isEqualTo(DesktopTaskPosition.Center)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun addMoveToDeskTaskChanges_positionBottomRight() {
        setUpLandscapeDisplay()
        val stableBounds = Rect().also { displayLayout.getStableBoundsForDesktopMode(it) }

        setUpFreeformTask(bounds = DEFAULT_LANDSCAPE_BOUNDS)

        val task = setUpFullscreenTask()
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        val finalBounds = findBoundsChange(wct, task)
        assertThat(stableBounds.getDesktopTaskPosition(finalBounds!!))
            .isEqualTo(DesktopTaskPosition.BottomRight)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun addMoveToDeskTaskChanges_positionTopLeft() {
        setUpLandscapeDisplay()
        val stableBounds = Rect().also { displayLayout.getStableBoundsForDesktopMode(it) }

        addFreeformTaskAtPosition(DesktopTaskPosition.BottomRight, stableBounds)

        val task = setUpFullscreenTask()
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        val finalBounds = findBoundsChange(wct, task)
        assertThat(stableBounds.getDesktopTaskPosition(finalBounds!!))
            .isEqualTo(DesktopTaskPosition.TopLeft)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun addMoveToDeskTaskChanges_positionBottomLeft() {
        setUpLandscapeDisplay()
        val stableBounds = Rect().also { displayLayout.getStableBoundsForDesktopMode(it) }

        addFreeformTaskAtPosition(DesktopTaskPosition.TopLeft, stableBounds)

        val task = setUpFullscreenTask()
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        val finalBounds = findBoundsChange(wct, task)
        assertThat(stableBounds.getDesktopTaskPosition(finalBounds!!))
            .isEqualTo(DesktopTaskPosition.BottomLeft)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun addMoveToDeskTaskChanges_positionTopRight() {
        setUpLandscapeDisplay()
        val stableBounds = Rect().also { displayLayout.getStableBoundsForDesktopMode(it) }

        addFreeformTaskAtPosition(DesktopTaskPosition.BottomLeft, stableBounds)

        val task = setUpFullscreenTask()
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        val finalBounds = findBoundsChange(wct, task)
        assertThat(stableBounds.getDesktopTaskPosition(finalBounds!!))
            .isEqualTo(DesktopTaskPosition.TopRight)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun addMoveToDeskTaskChanges_positionResetsToCenter() {
        setUpLandscapeDisplay()
        val stableBounds = Rect().also { displayLayout.getStableBoundsForDesktopMode(it) }

        addFreeformTaskAtPosition(DesktopTaskPosition.TopRight, stableBounds)

        val task = setUpFullscreenTask()
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        val finalBounds = findBoundsChange(wct, task)
        assertThat(stableBounds.getDesktopTaskPosition(finalBounds!!))
            .isEqualTo(DesktopTaskPosition.Center)
    }

    @Test
    fun addMoveToDeskTaskChanges_lastWindowSnapLeft_positionResetsToCenter() {
        setUpLandscapeDisplay()
        val stableBounds = Rect().also { displayLayout.getStableBoundsForDesktopMode(it) }

        // Add freeform task with half display size snap bounds at left side.
        setUpFreeformTask(
            bounds = Rect(stableBounds.left, stableBounds.top, 500, stableBounds.bottom)
        )

        val task = setUpFullscreenTask()
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        val finalBounds = findBoundsChange(wct, task)
        assertThat(stableBounds.getDesktopTaskPosition(finalBounds!!))
            .isEqualTo(DesktopTaskPosition.Center)
    }

    @Test
    fun addMoveToDeskTaskChanges_lastWindowSnapRight_positionResetsToCenter() {
        setUpLandscapeDisplay()
        val stableBounds = Rect().also { displayLayout.getStableBoundsForDesktopMode(it) }

        // Add freeform task with half display size snap bounds at right side.
        setUpFreeformTask(
            bounds =
                Rect(
                    stableBounds.right - 500,
                    stableBounds.top,
                    stableBounds.right,
                    stableBounds.bottom,
                )
        )

        val task = setUpFullscreenTask()
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        val finalBounds = findBoundsChange(wct, task)
        assertThat(stableBounds.getDesktopTaskPosition(finalBounds!!))
            .isEqualTo(DesktopTaskPosition.Center)
    }

    @Test
    fun addMoveToDeskTaskChanges_lastWindowMaximised_positionResetsToCenter() {
        setUpLandscapeDisplay()
        val stableBounds = Rect().also { displayLayout.getStableBoundsForDesktopMode(it) }

        // Add maximised freeform task.
        setUpFreeformTask(bounds = Rect(stableBounds))

        val task = setUpFullscreenTask()
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        val finalBounds = findBoundsChange(wct, task)
        assertThat(stableBounds.getDesktopTaskPosition(finalBounds!!))
            .isEqualTo(DesktopTaskPosition.Center)
    }

    @Test
    fun addMoveToDeskTaskChanges_defaultToCenterIfFree() {
        setUpLandscapeDisplay()
        val stableBounds = Rect().also { displayLayout.getStableBoundsForDesktopMode(it) }

        val minTouchTarget =
            context.resources.getDimensionPixelSize(
                R.dimen.freeform_required_visible_empty_space_in_header
            )
        addFreeformTaskAtPosition(
            DesktopTaskPosition.Center,
            stableBounds,
            Rect(0, 0, 1600, 1200),
            Point(0, minTouchTarget + 1),
        )

        val task = setUpFullscreenTask()
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        val finalBounds = findBoundsChange(wct, task)
        assertThat(stableBounds.getDesktopTaskPosition(finalBounds!!))
            .isEqualTo(DesktopTaskPosition.Center)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    fun getInitialBounds_withRememberedBounds_returnsCorrectBounds() {
        setUpLandscapeDisplay()
        val packageName = "com.test.app"
        val task = setUpFreeformTask().apply { baseActivity = ComponentName(packageName, "") }
        val boundsRatio = RectF(0.1f, 0.2f, 0.8f, 0.9f)
        val stableBounds = Rect().also { displayLayout.getStableBoundsForDesktopMode(it) }

        taskRepository.setRememberedBoundsRatio(packageName, boundsRatio)

        val bounds = controller.getInitialBounds(displayLayout, task, 0)

        val expectedBounds =
            Rect(
                (stableBounds.left + stableBounds.width() * boundsRatio.left).toInt(),
                (stableBounds.top + stableBounds.height() * boundsRatio.top).toInt(),
                (stableBounds.left + stableBounds.width() * boundsRatio.right).toInt(),
                (stableBounds.top + stableBounds.height() * boundsRatio.bottom).toInt(),
            )
        assertThat(bounds).isEqualTo(expectedBounds)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    fun getInitialBounds_withRememberedBounds_prioritizeActivityOptions() {
        setUpLandscapeDisplay()
        val packageName = "com.test.app"
        val rememberedTask =
            setUpFreeformTask().apply {
                baseActivity = ComponentName(packageName, "")
                leafTaskBoundsFromOptions = true
            }
        val nonRememberedTask = setUpFreeformTask()
        val boundsRatio = RectF(0.1f, 0.2f, 0.8f, 0.9f)
        val stableBounds = Rect().also { displayLayout.getStableBoundsForDesktopMode(it) }

        taskRepository.setRememberedBoundsRatio(packageName, boundsRatio)

        assertThat(controller.getInitialBounds(displayLayout, rememberedTask, 0))
            .isEqualTo(controller.getInitialBounds(displayLayout, nonRememberedTask, 0))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    fun getInitialBounds_withRememberedBounds_anotherInstanceActive_returnsNull() {
        setUpLandscapeDisplay()
        val packageName = "com.test.app"
        val rememberedTask =
            setUpFreeformTask().apply { baseActivity = ComponentName(packageName, "") }
        val rememberedTaskButDifferentInstance =
            setUpFreeformTask().apply { baseActivity = ComponentName(packageName, "") }
        val nonRememberedTask = setUpFreeformTask()
        val boundsRatio = RectF(0.1f, 0.2f, 0.8f, 0.9f)
        val stableBounds = Rect().also { displayLayout.getStableBoundsForDesktopMode(it) }

        taskRepository.setRememberedBoundsRatio(packageName, boundsRatio)

        assertThat(controller.getInitialBounds(displayLayout, rememberedTask, 0))
            .isEqualTo(controller.getInitialBounds(displayLayout, nonRememberedTask, 0))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    fun getInitialBounds_withRememberedBounds_unresizable_returnsNull() {
        setUpLandscapeDisplay()
        val packageName = "com.test.app"
        val task =
            setUpFreeformTask().apply {
                baseActivity = ComponentName(packageName, "")
                isResizeable = false
            }
        val nonRememberedTask = setUpFreeformTask().apply { isResizeable = false }
        val boundsRatio = RectF(0.1f, 0.2f, 0.8f, 0.9f)

        taskRepository.setRememberedBoundsRatio(packageName, boundsRatio)

        assertThat(controller.getInitialBounds(displayLayout, task, 0))
            .isEqualTo(controller.getInitialBounds(displayLayout, nonRememberedTask, 0))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    fun getInitialBounds_withRememberedBounds_excludeCaptionInsets_returnsNull() {
        setUpLandscapeDisplay()
        val packageName = "com.test.app"
        val task =
            setUpFreeformTask().apply {
                baseActivity = ComponentName(packageName, "")
                appCompatTaskInfo.setIsExcludeCaptionInsets(true)
            }
        val nonRememberedTask = setUpFreeformTask()
        val boundsRatio = RectF(0.1f, 0.2f, 0.8f, 0.9f)

        taskRepository.setRememberedBoundsRatio(packageName, boundsRatio)

        assertThat(controller.getInitialBounds(displayLayout, task, 0))
            .isEqualTo(controller.getInitialBounds(displayLayout, nonRememberedTask, 0))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    fun rememberedBounds_clearedOnRemoved() {
        val callbackCaptor = argumentCaptor<LauncherApps.Callback>()
        verify(launcherApps).registerCallback(callbackCaptor.capture())
        val launcherAppsCallback = callbackCaptor.firstValue
        val packageName = "com.test.app"
        val boundsRatio = RectF(0.1f, 0.2f, 0.8f, 0.9f)

        taskRepository.setRememberedBoundsRatio(packageName, boundsRatio)
        assertThat(taskRepository.getRememberedBoundsRatio(packageName)).isEqualTo(boundsRatio)

        // Package is removed.
        launcherAppsCallback.onPackageRemoved(packageName, UserHandle.of(DEFAULT_USER_ID))
        assertThat(taskRepository.getRememberedBoundsRatio(packageName)).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    fun rememberedBounds_clearedOnUnavailable() {
        val callbackCaptor = argumentCaptor<LauncherApps.Callback>()
        verify(launcherApps).registerCallback(callbackCaptor.capture())
        val launcherAppsCallback = callbackCaptor.firstValue
        val packageName = "com.test.app"
        val boundsRatio = RectF(0.1f, 0.2f, 0.8f, 0.9f)

        taskRepository.setRememberedBoundsRatio(packageName, boundsRatio)
        assertThat(taskRepository.getRememberedBoundsRatio(packageName)).isEqualTo(boundsRatio)

        // Package is unavailable.
        launcherAppsCallback.onPackagesUnavailable(
            arrayOf(packageName),
            UserHandle.of(DEFAULT_USER_ID),
            /* replacing= */ false,
        )
        assertThat(taskRepository.getRememberedBoundsRatio(packageName)).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    fun rememberedBounds_notClearedOnReplaced() {
        val callbackCaptor = argumentCaptor<LauncherApps.Callback>()
        verify(launcherApps).registerCallback(callbackCaptor.capture())
        val launcherAppsCallback = callbackCaptor.firstValue
        val packageName = "com.test.app"
        val boundsRatio = RectF(0.1f, 0.2f, 0.8f, 0.9f)

        taskRepository.setRememberedBoundsRatio(packageName, boundsRatio)
        assertThat(taskRepository.getRememberedBoundsRatio(packageName)).isEqualTo(boundsRatio)

        // Package is just being replaced.
        launcherAppsCallback.onPackagesUnavailable(
            arrayOf(packageName),
            UserHandle.of(DEFAULT_USER_ID),
            /* replacing= */ true,
        )
        assertThat(taskRepository.getRememberedBoundsRatio(packageName)).isEqualTo(boundsRatio)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    @DisableFlags(Flags.FLAG_ENABLE_STEPPED_CASCADING)
    fun addMoveToDeskTaskChanges_rememberedBoundsIsFloating_applyCascading() {
        setUpLandscapeDisplay()
        val stableBounds = Rect().also { displayLayout.getStableBoundsForDesktopMode(it) }
        val packageName = "com.test.app"
        val boundsRatio = RectF(0.1f, 0.2f, 0.8f, 0.9f)
        val rememberedBounds =
            Rect(
                (stableBounds.left + stableBounds.width() * boundsRatio.left).toInt(),
                (stableBounds.top + stableBounds.height() * boundsRatio.top).toInt(),
                (stableBounds.left + stableBounds.width() * boundsRatio.right).toInt(),
                (stableBounds.top + stableBounds.height() * boundsRatio.bottom).toInt(),
            )
        taskRepository.setRememberedBoundsRatio(packageName, boundsRatio)

        setUpFreeformTask(bounds = rememberedBounds)

        val task = setUpFullscreenTask().apply { baseActivity = ComponentName(packageName, "") }
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        val finalBounds = findBoundsChange(wct, task)
        assertThat(stableBounds.getDesktopTaskPosition(finalBounds!!))
            .isEqualTo(DesktopTaskPosition.BottomRight)
        assertThat(finalBounds!!.width()).isEqualTo(rememberedBounds.width())
        assertThat(finalBounds!!.height()).isEqualTo(rememberedBounds.height())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    fun addMoveToDeskTaskChanges_rememberedBoundsIsMaximized_doNotApplyCascading() {
        setUpLandscapeDisplay()
        val stableBounds = Rect().also { displayLayout.getStableBoundsForDesktopMode(it) }
        val packageName = "com.test.app"
        val boundsRatio = RectF(0.0f, 0.0f, 1.0f, 1.0f)
        val rememberedBounds =
            Rect(
                (stableBounds.left + stableBounds.width() * boundsRatio.left).toInt(),
                (stableBounds.top + stableBounds.height() * boundsRatio.top).toInt(),
                (stableBounds.left + stableBounds.width() * boundsRatio.right).toInt(),
                (stableBounds.top + stableBounds.height() * boundsRatio.bottom).toInt(),
            )
        taskRepository.setRememberedBoundsRatio(packageName, boundsRatio)

        setUpFreeformTask(bounds = rememberedBounds)

        val task = setUpFullscreenTask().apply { baseActivity = ComponentName(packageName, "") }
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        val finalBounds = findBoundsChange(wct, task)
        assertThat(stableBounds.getDesktopTaskPosition(finalBounds!!))
            .isEqualTo(DesktopTaskPosition.Maximized)
        assertThat(finalBounds!!).isEqualTo(rememberedBounds)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    fun addMoveToDeskTaskChanges_rememberedBoundsIsRightSnapped_doNotApplyCascading() {
        setUpLandscapeDisplay()
        val stableBounds = Rect().also { displayLayout.getStableBoundsForDesktopMode(it) }
        val packageName = "com.test.app"
        val boundsRatio = RectF(0.5f, 0.0f, 1.0f, 1.0f)
        val rememberedBounds =
            Rect(
                (stableBounds.left + stableBounds.width() * boundsRatio.left).toInt(),
                (stableBounds.top + stableBounds.height() * boundsRatio.top).toInt(),
                (stableBounds.left + stableBounds.width() * boundsRatio.right).toInt(),
                (stableBounds.top + stableBounds.height() * boundsRatio.bottom).toInt(),
            )
        taskRepository.setRememberedBoundsRatio(packageName, boundsRatio)

        setUpFreeformTask(bounds = rememberedBounds)

        val task = setUpFullscreenTask().apply { baseActivity = ComponentName(packageName, "") }
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        val finalBounds = findBoundsChange(wct, task)
        assertThat(stableBounds.getDesktopTaskPosition(finalBounds!!))
            .isEqualTo(DesktopTaskPosition.RightSnapped)
        assertThat(finalBounds!!).isEqualTo(rememberedBounds)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_REMEMBERED_BOUNDS)
    fun addMoveToDeskTaskChanges_rememberedBoundsIsLeftSnapped_doNotApplyCascading() {
        setUpLandscapeDisplay()
        val stableBounds = Rect().also { displayLayout.getStableBoundsForDesktopMode(it) }
        val packageName = "com.test.app"
        val boundsRatio = RectF(0.0f, 0.0f, 0.5f, 1.0f)
        val rememberedBounds =
            Rect(
                (stableBounds.left + stableBounds.width() * boundsRatio.left).toInt(),
                (stableBounds.top + stableBounds.height() * boundsRatio.top).toInt(),
                (stableBounds.left + stableBounds.width() * boundsRatio.right).toInt(),
                (stableBounds.top + stableBounds.height() * boundsRatio.bottom).toInt(),
            )
        taskRepository.setRememberedBoundsRatio(packageName, boundsRatio)

        setUpFreeformTask(bounds = rememberedBounds)

        val task = setUpFullscreenTask().apply { baseActivity = ComponentName(packageName, "") }
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        val finalBounds = findBoundsChange(wct, task)
        assertThat(stableBounds.getDesktopTaskPosition(finalBounds!!))
            .isEqualTo(DesktopTaskPosition.LeftSnapped)
        assertThat(finalBounds!!).isEqualTo(rememberedBounds)
    }

    @Test
    @EnableFlags(Flags.FLAG_REFACTOR_CAPTION_SANDBOXING_TO_CORE)
    fun addMoveToDeskTaskChanges_excludeCaptionFromAppBounds_nonResizableLandscape() {
        setUpLandscapeDisplay()
        val task =
            setUpFullscreenTask(
                isResizable = false,
                screenOrientation = SCREEN_ORIENTATION_LANDSCAPE,
            )
        task.appCompatTaskInfo.setIsExcludeCaptionInsets(true)
        val captionInsets = 60
        whenever(displayLayout.captionBarHeight()).thenReturn(captionInsets)
        val initialAspectRatio = calculateAspectRatio(task)

        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        val finalBounds = findBoundsChange(wct, task)
        finalBounds!!.top += captionInsets
        val finalAspectRatio =
            maxOf(finalBounds.height(), finalBounds.width()) /
                minOf(finalBounds.height(), finalBounds.width()).toFloat()
        assertThat(finalAspectRatio).isWithin(FLOAT_TOLERANCE).of(initialAspectRatio)
    }

    @Test
    @EnableFlags(Flags.FLAG_REFACTOR_CAPTION_SANDBOXING_TO_CORE)
    fun addMoveToDeskTaskChanges_excludeCaptionFromAppBounds_nonResizablePortrait() {
        setUpLandscapeDisplay()
        val task =
            setUpFullscreenTask(
                isResizable = false,
                screenOrientation = SCREEN_ORIENTATION_PORTRAIT,
            )
        task.appCompatTaskInfo.setIsExcludeCaptionInsets(true)
        val captionInsets = 60
        whenever(displayLayout.captionBarHeight()).thenReturn(captionInsets)
        val initialAspectRatio = calculateAspectRatio(task)

        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        val finalBounds = findBoundsChange(wct, task)
        finalBounds!!.top += captionInsets
        val finalAspectRatio =
            maxOf(finalBounds.height(), finalBounds.width()) /
                minOf(finalBounds.height(), finalBounds.width()).toFloat()
        assertThat(finalAspectRatio).isWithin(FLOAT_TOLERANCE).of(initialAspectRatio)
    }

    @Test
    fun launchIntent_taskInDesktopMode_transitionStarted() {
        setUpLandscapeDisplay()
        val freeformTask = setUpFreeformTask()
        mockStartLaunchTransition(transitionType = TRANSIT_OPEN)
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                /* requestCode= */ 0,
                freeformTask.baseIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
                /* options= */ null,
            )

        controller.startLaunchIntentTransition(pendingIntent, Bundle.EMPTY, DEFAULT_DISPLAY)

        val wct = getLatestDesktopMixedTaskWct(type = TRANSIT_OPEN)
        assertThat(wct.hierarchyOps).hasSize(1)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING,
        Flags.FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY,
    )
    fun launchIntent_taskInDesktopMode_onSecondaryDisplayNotInDesktopMode_transitionStarted() {
        setUpLandscapeDisplay()
        taskRepository.addDesk(SECOND_DISPLAY, deskId = 2) // Inactive desk.
        val intent = Intent().setComponent(homeComponentName)
        mockStartLaunchTransition(transitionType = TRANSIT_OPEN)
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                /* requestCode= */ 0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
                /* options= */ null,
            )

        controller.startLaunchIntentTransition(pendingIntent, Bundle.EMPTY, SECOND_DISPLAY)

        val wct = getLatestDesktopMixedTaskWct(type = TRANSIT_OPEN)
        // We expect two actions: open the app and open the wallpaper.
        assertThat(wct.hierarchyOps).hasSize(2)
        val hOps0 = wct.hierarchyOps[0]
        val hOps1 = wct.hierarchyOps[1]
        assertThat(hOps0.type).isEqualTo(HIERARCHY_OP_TYPE_PENDING_INTENT)
        val activityOptions0 = ActivityOptions.fromBundle(hOps0.launchOptions)
        assertThat(activityOptions0.launchDisplayId).isEqualTo(SECOND_DISPLAY)
        assertThat(hOps1.type).isEqualTo(HIERARCHY_OP_TYPE_PENDING_INTENT)
        val activityOptions1 = ActivityOptions.fromBundle(hOps1.launchOptions)
        assertThat(activityOptions1.launchDisplayId).isEqualTo(SECOND_DISPLAY)
    }

    @Test
    fun launchNewTask_topTransparentFullscreenTaskIdPassedToClear() {
        setUpLandscapeDisplay()
        val topTransparentTask =
            setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = DEFAULT_DISPLAY)
        taskRepository.setTopTransparentFullscreenTaskData(DEFAULT_DISPLAY, topTransparentTask)

        val task = setUpFreeformTask()
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                /* requestCode= */ 0,
                task.baseIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
                /* options= */ null,
            )
        controller.startLaunchIntentTransition(pendingIntent, Bundle.EMPTY, DEFAULT_DISPLAY)

        verifyStartLaunchTransition(
            transitionType = TRANSIT_OPEN,
            closingTopTransparentTaskId = topTransparentTask.taskId,
        )
    }

    @Test
    fun addMoveToDeskTaskChanges_landscapeDevice_userFullscreenOverride_defaultPortraitBounds() {
        setUpLandscapeDisplay()
        val task = setUpFullscreenTask(enableUserFullscreenOverride = true)
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        assertThat(findBoundsChange(wct, task)).isEqualTo(DEFAULT_LANDSCAPE_BOUNDS)
    }

    @Test
    fun addMoveToDeskTaskChanges_landscapeDevice_systemFullscreenOverride_defaultPortraitBounds() {
        setUpLandscapeDisplay()
        val task = setUpFullscreenTask(enableSystemFullscreenOverride = true)
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        assertThat(findBoundsChange(wct, task)).isEqualTo(DEFAULT_LANDSCAPE_BOUNDS)
    }

    @Test
    fun addMoveToDeskTaskChanges_landscapeDevice_portraitResizableApp_aspectRatioOverridden() {
        setUpLandscapeDisplay()
        val task =
            setUpFullscreenTask(
                screenOrientation = SCREEN_ORIENTATION_PORTRAIT,
                shouldLetterbox = true,
                aspectRatioOverrideApplied = true,
            )
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        assertThat(findBoundsChange(wct, task)).isEqualTo(UNRESIZABLE_PORTRAIT_BOUNDS)
    }

    @Test
    fun addMoveToDeskTaskChanges_portraitDevice_userFullscreenOverride_defaultPortraitBounds() {
        setUpPortraitDisplay()
        val task = setUpFullscreenTask(enableUserFullscreenOverride = true)
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        assertThat(findBoundsChange(wct, task)).isEqualTo(DEFAULT_PORTRAIT_BOUNDS)
    }

    @Test
    fun addMoveToDeskTaskChanges_portraitDevice_systemFullscreenOverride_defaultPortraitBounds() {
        setUpPortraitDisplay()
        val task = setUpFullscreenTask(enableSystemFullscreenOverride = true)
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        assertThat(findBoundsChange(wct, task)).isEqualTo(DEFAULT_PORTRAIT_BOUNDS)
    }

    @Test
    fun addMoveToDeskTaskChanges_portraitDevice_landscapeResizableApp_aspectRatioOverridden() {
        setUpPortraitDisplay()
        val task =
            setUpFullscreenTask(
                screenOrientation = SCREEN_ORIENTATION_LANDSCAPE,
                deviceOrientation = ORIENTATION_PORTRAIT,
                shouldLetterbox = true,
                aspectRatioOverrideApplied = true,
            )
        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        assertThat(findBoundsChange(wct, task)).isEqualTo(UNRESIZABLE_LANDSCAPE_BOUNDS)
    }

    @Test
    fun addMoveToDeskTaskChanges_inSizeCompatMode_originalAspectRatioMaintained() {
        setUpLandscapeDisplay()
        val task =
            setUpFullscreenTask(
                isResizable = false,
                screenOrientation = SCREEN_ORIENTATION_PORTRAIT,
                deviceOrientation = ORIENTATION_PORTRAIT,
            )
        // Simulate floating size compat mode bounds (same aspect ratio as display without insets).
        task.appCompatTaskInfo.topActivityAppBounds.set(
            0,
            0,
            DISPLAY_DIMENSION_LONG / 2,
            DISPLAY_DIMENSION_SHORT / 2,
        )
        val originalAspectRatio = 1.5f
        task.appCompatTaskInfo.topNonResizableActivityAspectRatio = originalAspectRatio

        val wct = WindowContainerTransaction()
        controller.addMoveToDeskTaskChanges(wct, task, deskId = 0)

        val finalBounds = findBoundsChange(wct, task)
        assertNotNull(finalBounds, "finalBounds should be resolved")
        val finalAspectRatio =
            maxOf(finalBounds.height(), finalBounds.width()) /
                minOf(finalBounds.height(), finalBounds.width()).toFloat()
        assertThat(finalAspectRatio).isWithin(FLOAT_TOLERANCE).of(originalAspectRatio)
    }

    @Test
    fun moveRunningTaskToDesktop_movesTaskToDefaultDesk() =
        testScope.runTest {
            val task = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)

            controller.moveTaskToDefaultDeskAndActivate(task.taskId, transitionSource = UNKNOWN)
            runCurrent()

            val wct = getLatestEnterDesktopWct()
            verify(desksOrganizer).moveTaskToDesk(wct, deskId = 0, task)
        }

    @Test
    fun moveRunningTaskToDesktop_activatesDesk() =
        testScope.runTest {
            val task = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)

            controller.moveTaskToDefaultDeskAndActivate(task.taskId, transitionSource = UNKNOWN)
            runCurrent()

            val wct = getLatestEnterDesktopWct()
            verify(desksOrganizer).activateDesk(wct, deskId = 0)
        }

    @Test
    fun moveRunningTaskToDesktop_triggersEnterDesktopListener() =
        testScope.runTest {
            val task = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)

            controller.moveTaskToDefaultDeskAndActivate(task.taskId, transitionSource = UNKNOWN)
            runCurrent()

            verify(desktopRemoteListener)
                .onEnterDesktopModeTransitionStarted(TO_DESKTOP_ANIM_DURATION)
        }

    @Test
    fun moveToDesk_existingDeskTasksInBackground_cascadeApplied() {
        val stableBounds =
            Rect(0, 0, DISPLAY_DIMENSION_LONG, DISPLAY_DIMENSION_SHORT - TASKBAR_FRAME_HEIGHT)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        setUpLandscapeDisplay()

        val freeformTask = setUpFreeformTask(bounds = DEFAULT_LANDSCAPE_BOUNDS, background = true)
        val recentsFreeformTask =
            createRecentTaskInfo(taskId = freeformTask.taskId, displayId = freeformTask.displayId)
        recentsFreeformTask.lastNonFullscreenBounds = DEFAULT_LANDSCAPE_BOUNDS
        whenever(recentTasksController.findTaskInBackground(anyInt()))
            .thenReturn(recentsFreeformTask)
        val fullscreenTask = setUpFullscreenTask()

        controller.moveTaskToDefaultDeskAndActivate(
            fullscreenTask.taskId,
            transitionSource = UNKNOWN,
        )

        val wct = getLatestEnterDesktopWct()
        assertNotNull(wct, "should move to desk")
        val finalBounds = findBoundsChange(wct, fullscreenTask)
        if (Flags.enableSteppedCascading()) {
            assertThat(stableBounds.getDesktopTaskPosition(finalBounds!!))
                .isNotEqualTo(DEFAULT_LANDSCAPE_BOUNDS)
        } else {
            assertThat(stableBounds.getDesktopTaskPosition(finalBounds!!))
                .isEqualTo(DesktopTaskPosition.BottomRight)
        }
    }

    @Test
    fun moveTaskToDesk_nonDefaultDesk_movesTaskToDesk() {
        val transition = Binder()
        whenever(enterDesktopTransitionHandler.moveToDesktop(any(), any())).thenReturn(transition)
        taskRepository.addDesk(DEFAULT_DISPLAY, deskId = 3)
        val task = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)
        task.isVisible = true

        controller.moveTaskToDesk(taskId = task.taskId, deskId = 3, transitionSource = UNKNOWN)

        val wct = getLatestEnterDesktopWct()
        verify(desksOrganizer).moveTaskToDesk(wct, deskId = 3, task)
    }

    @Test
    fun moveTaskToDesk_nonDefaultDesk_activatesDesk() {
        val transition = Binder()
        whenever(enterDesktopTransitionHandler.moveToDesktop(any(), any())).thenReturn(transition)
        taskRepository.addDesk(DEFAULT_DISPLAY, deskId = 3)
        val task = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)
        task.isVisible = true

        controller.moveTaskToDesk(taskId = task.taskId, deskId = 3, transitionSource = UNKNOWN)

        val wct = getLatestEnterDesktopWct()
        verify(desksOrganizer).activateDesk(wct, deskId = 3)
    }

    @Test
    fun moveTaskToDesk_nonDefaultDesk_triggersEnterDesktopListener() {
        taskRepository.addDesk(DEFAULT_DISPLAY, deskId = 3)
        val task = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)

        controller.moveTaskToDesk(taskId = task.taskId, deskId = 3, transitionSource = UNKNOWN)

        verify(desktopRemoteListener).onEnterDesktopModeTransitionStarted(TO_DESKTOP_ANIM_DURATION)
    }

    @Test
    fun moveTaskToDesk_lockTaskMode_doesNothing() {
        whenever(lockTaskChangeListener.isTaskLocked).thenReturn(true)

        val task = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)

        assertFalse(
            controller.moveTaskToDefaultDeskAndActivate(
                taskId = task.taskId,
                transitionSource = UNKNOWN,
            )
        )
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    fun moveTaskToDesktop_desktopWallpaperDisabled_nonRunningTask_launchesInFreeform() =
        testScope.runTest {
            val task = createRecentTaskInfo(1)
            whenever(shellTaskOrganizer.getRunningTaskInfo(anyInt())).thenReturn(null)
            whenever(recentTasksController.findTaskInBackground(anyInt())).thenReturn(task)
            whenever(desktopWallpaperActivityUtils.hasDesktopWallpaperActivityEnabled(any()))
                .thenReturn(false)

            controller.moveTaskToDefaultDeskAndActivate(task.taskId, transitionSource = UNKNOWN)
            runCurrent()

            with(getLatestEnterDesktopWct()) {
                assertLaunchTaskAt(0, task.taskId, WINDOWING_MODE_FREEFORM)
            }
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    fun moveTaskToDesktop_desktopWallpaperEnabled_nonRunningTask_launchesInFreeform() =
        testScope.runTest {
            whenever(desktopWallpaperActivityTokenProvider.getToken()).thenReturn(null)
            val task = createRecentTaskInfo(1)
            whenever(shellTaskOrganizer.getRunningTaskInfo(anyInt())).thenReturn(null)
            whenever(recentTasksController.findTaskInBackground(anyInt())).thenReturn(task)

            controller.moveTaskToDefaultDeskAndActivate(task.taskId, transitionSource = UNKNOWN)
            runCurrent()

            with(getLatestEnterDesktopWct()) {
                // Add desktop wallpaper activity
                assertPendingIntentAt(index = 0, desktopWallpaperIntent)
                // Launch task
                assertLaunchTaskAt(index = 1, task.taskId, WINDOWING_MODE_FREEFORM)
            }
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    fun moveBackgroundTaskToDesktop_remoteTransition_usesOneShotHandler() =
        testScope.runTest {
            val transitionHandlerArgCaptor = argumentCaptor<TransitionHandler>()
            whenever(
                    transitions.startTransition(
                        anyInt(),
                        any(),
                        transitionHandlerArgCaptor.capture(),
                    )
                )
                .thenReturn(Binder())

            val task = createRecentTaskInfo(1)
            whenever(shellTaskOrganizer.getRunningTaskInfo(anyInt())).thenReturn(null)
            whenever(recentTasksController.findTaskInBackground(anyInt())).thenReturn(task)
            controller.moveTaskToDefaultDeskAndActivate(
                taskId = task.taskId,
                transitionSource = UNKNOWN,
                remoteTransition = RemoteTransition(spy(TestRemoteTransition())),
            )
            runCurrent()

            verify(desktopRemoteListener)
                .onEnterDesktopModeTransitionStarted(TO_DESKTOP_ANIM_DURATION)
            assertIs<OneShotRemoteHandler>(transitionHandlerArgCaptor.firstValue)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER,
    )
    fun moveBackgroundTaskToDesktop_nonDefaultDisplay_reordersHomeAndWallpaperOfNonDefaultDisplay() =
        testScope.runTest {
            val homeTask = setUpHomeTask(displayId = SECOND_DISPLAY)
            val wallpaperToken = MockToken().token()
            whenever(desktopWallpaperActivityTokenProvider.getToken(SECOND_DISPLAY))
                .thenReturn(wallpaperToken)
            taskRepository.addDesk(SECOND_DISPLAY, deskId = 2)
            val task = setUpFreeformTask(displayId = SECOND_DISPLAY, deskId = 2, background = true)

            controller.moveTaskToDefaultDeskAndActivate(
                taskId = task.taskId,
                transitionSource = UNKNOWN,
                remoteTransition = RemoteTransition(spy(TestRemoteTransition())),
            )
            runCurrent()

            val wct = getLatestTransition()
            val homeReorderIndex = wct.indexOfReorder(homeTask, toTop = true)
            val wallpaperReorderIndex = wct.indexOfReorder(wallpaperToken, toTop = true)
            assertThat(homeReorderIndex).isNotEqualTo(-1)
            assertThat(wallpaperReorderIndex).isNotEqualTo(-1)
            // Wallpaper last, to be in front of Home.
            assertThat(wallpaperReorderIndex).isGreaterThan(homeReorderIndex)
        }

    @Test
    fun moveBackgroundTaskToDesktop_invalidDisplay_invalidFocusedDisplay_reordersHomeAndWallpaperInDefaultDisplay() =
        testScope.runTest {
            val task = createRecentTaskInfo(1, INVALID_DISPLAY)
            val homeTask = setUpHomeTask(displayId = DEFAULT_DISPLAY)
            val wallpaperToken = MockToken().token()
            whenever(focusTransitionObserver.globallyFocusedDisplayId).thenReturn(INVALID_DISPLAY)
            whenever(shellTaskOrganizer.getRunningTaskInfo(anyInt())).thenReturn(null)
            whenever(recentTasksController.findTaskInBackground(anyInt())).thenReturn(task)
            whenever(desktopWallpaperActivityTokenProvider.getToken(DEFAULT_DISPLAY))
                .thenReturn(wallpaperToken)

            controller.moveTaskToDefaultDeskAndActivate(
                taskId = task.taskId,
                transitionSource = UNKNOWN,
                remoteTransition = RemoteTransition(TestRemoteTransition()),
            )
            runCurrent()

            val wct = getLatestTransition()
            wct.assertReorder(homeTask)
            wct.assertReorder(wallpaperToken)
        }

    @Test
    @EnableFlags(FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY)
    fun moveBackgroundTaskToDesktop_invalidDisplay_validFocusedDisplay_reordersHomeAndWallpaperInFocusedDisplay() =
        testScope.runTest {
            val task = createRecentTaskInfo(1, INVALID_DISPLAY)
            val focusedDisplayId = 5
            val homeTask = setUpHomeTask(displayId = focusedDisplayId)
            val wallpaperToken = MockToken().token()
            taskRepository.addDesk(displayId = focusedDisplayId, deskId = 5)
            whenever(focusTransitionObserver.globallyFocusedDisplayId).thenReturn(focusedDisplayId)
            whenever(shellTaskOrganizer.getRunningTaskInfo(anyInt())).thenReturn(null)
            whenever(recentTasksController.findTaskInBackground(anyInt())).thenReturn(task)
            whenever(desktopWallpaperActivityTokenProvider.getToken(focusedDisplayId))
                .thenReturn(wallpaperToken)

            controller.moveTaskToDefaultDeskAndActivate(
                taskId = task.taskId,
                transitionSource = UNKNOWN,
                remoteTransition = RemoteTransition(TestRemoteTransition()),
            )
            runCurrent()

            val wct = getLatestTransition()
            wct.assertReorder(homeTask)
            wct.assertReorder(wallpaperToken)
        }

    @Test
    fun moveBackgroundTaskToDesktop_invalidDisplay_invalidFocusedDisplay_activatesDeskInDefaultDisplay() =
        testScope.runTest {
            val task = createRecentTaskInfo(1, INVALID_DISPLAY)
            val deskId = 2
            whenever(focusTransitionObserver.globallyFocusedDisplayId).thenReturn(INVALID_DISPLAY)
            whenever(shellTaskOrganizer.getRunningTaskInfo(anyInt())).thenReturn(null)
            whenever(recentTasksController.findTaskInBackground(anyInt())).thenReturn(task)
            taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = deskId)
            taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = deskId)

            controller.moveTaskToDefaultDeskAndActivate(
                taskId = task.taskId,
                transitionSource = UNKNOWN,
                remoteTransition = RemoteTransition(TestRemoteTransition()),
            )
            runCurrent()

            val wct = getLatestTransition()
            verify(desksOrganizer).activateDesk(wct, deskId = deskId)
        }

    @Test
    fun moveBackgroundTaskToDesktop_invalidDisplay_validFocusedDisplay_activatesDeskInFocusedDisplay() =
        testScope.runTest {
            val task = createRecentTaskInfo(1, INVALID_DISPLAY)
            val focusedDisplayId = 5
            val deskId = 2
            whenever(focusTransitionObserver.globallyFocusedDisplayId).thenReturn(focusedDisplayId)
            whenever(shellTaskOrganizer.getRunningTaskInfo(anyInt())).thenReturn(null)
            whenever(recentTasksController.findTaskInBackground(anyInt())).thenReturn(task)
            taskRepository.addDesk(displayId = focusedDisplayId, deskId = deskId)

            controller.moveTaskToDefaultDeskAndActivate(
                taskId = task.taskId,
                transitionSource = UNKNOWN,
                remoteTransition = RemoteTransition(TestRemoteTransition()),
            )
            runCurrent()

            val wct = getLatestTransition()
            verify(desksOrganizer).activateDesk(wct, deskId = deskId)
        }

    @Test
    fun moveRunningTaskToDesktop_remoteTransition_usesOneShotHandler() =
        testScope.runTest {
            val transitionHandlerArgCaptor = argumentCaptor<TransitionHandler>()
            whenever(
                    transitions.startTransition(
                        anyInt(),
                        any(),
                        transitionHandlerArgCaptor.capture(),
                    )
                )
                .thenReturn(Binder())

            controller.moveTaskToDefaultDeskAndActivate(
                taskId = setUpFullscreenTask().taskId,
                transitionSource = UNKNOWN,
                remoteTransition = RemoteTransition(spy(TestRemoteTransition())),
            )
            runCurrent()

            verify(desktopRemoteListener)
                .onEnterDesktopModeTransitionStarted(TO_DESKTOP_ANIM_DURATION)
            assertIs<OneShotRemoteHandler>(transitionHandlerArgCaptor.firstValue)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER,
    )
    fun moveRunningTaskToDesktop_desktopWallpaperEnabled_multiDesksEnabled() =
        testScope.runTest {
            val freeformTask = setUpFreeformTask()
            val fullscreenTask = setUpFullscreenTask()
            markTaskHidden(freeformTask)

            controller.moveTaskToDefaultDeskAndActivate(
                fullscreenTask.taskId,
                transitionSource = UNKNOWN,
            )
            runCurrent()

            val wct = getLatestEnterDesktopWct()
            wct.assertReorderAt(index = 0, wallpaperToken)
            verify(desksOrganizer).moveTaskToDesk(wct, deskId = 0, fullscreenTask)
            verify(desksOrganizer).activateDesk(wct, deskId = 0)
            verify(desktopRemoteListener)
                .onEnterDesktopModeTransitionStarted(TO_DESKTOP_ANIM_DURATION)
        }

    @Test
    fun moveRunningTaskToDesktop_onlyFreeformTasksFromCurrentDisplayBroughtToFront() =
        testScope.runTest {
            setUpHomeTask(displayId = DEFAULT_DISPLAY)
            val freeformTaskDefault = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
            val fullscreenTaskDefault = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)
            markTaskHidden(freeformTaskDefault)

            taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
            val homeTaskSecond = setUpHomeTask(displayId = SECOND_DISPLAY)
            val freeformTaskSecond = setUpFreeformTask(displayId = SECOND_DISPLAY)
            markTaskHidden(freeformTaskSecond)

            controller.moveTaskToDefaultDeskAndActivate(
                fullscreenTaskDefault.taskId,
                transitionSource = UNKNOWN,
            )
            runCurrent()

            with(getLatestEnterDesktopWct()) {
                // Check that hierarchy operations do not include tasks from second display
                assertThat(hierarchyOps.map { it.container })
                    .doesNotContain(homeTaskSecond.token.asBinder())
                assertThat(hierarchyOps.map { it.container })
                    .doesNotContain(freeformTaskSecond.token.asBinder())
            }
            verify(desktopRemoteListener)
                .onEnterDesktopModeTransitionStarted(TO_DESKTOP_ANIM_DURATION)
        }

    @Test
    fun moveRunningTaskToDesktop_splitTaskExitsSplit_multiDesksEnabled() =
        testScope.runTest {
            val task = setUpSplitScreenTask()

            controller.moveTaskToDefaultDeskAndActivate(task.taskId, transitionSource = UNKNOWN)
            runCurrent()

            val wct = getLatestEnterDesktopWct()
            verify(desksOrganizer).moveTaskToDesk(wct, deskId = 0, task)
            verify(desktopRemoteListener)
                .onEnterDesktopModeTransitionStarted(TO_DESKTOP_ANIM_DURATION)
            verify(splitScreenController)
                .prepareExitSplitScreen(
                    any(),
                    anyInt(),
                    eq(SplitScreenController.EXIT_REASON_DESKTOP_MODE),
                )
        }

    @Test
    fun moveRunningTaskToDesktop_fullscreenTaskDoesNotExitSplit() =
        testScope.runTest {
            val task = setUpFullscreenTask()

            controller.moveTaskToDefaultDeskAndActivate(task.taskId, transitionSource = UNKNOWN)
            runCurrent()

            val wct = getLatestEnterDesktopWct()
            verify(desktopRemoteListener)
                .onEnterDesktopModeTransitionStarted(TO_DESKTOP_ANIM_DURATION)
            verify(splitScreenController, never())
                .prepareExitSplitScreen(
                    any(),
                    anyInt(),
                    eq(SplitScreenController.EXIT_REASON_DESKTOP_MODE),
                )
        }

    @Test
    fun moveToFullscreen_fromDesk_reparentsToTaskDisplayArea() {
        val task = setUpFreeformTask()
        val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!

        controller.moveToFullscreen(task.taskId, transitionSource = UNKNOWN)

        val wct = getLatestExitDesktopWct()
        wct.assertHop(ReparentPredicate(token = task.token, parentToken = tda.token, toTop = true))
    }

    @Test
    fun moveToFullscreen_fromDesk_touchFirst_multipleTasksInDesk_deactivatesDesk() {
        val task1 = setUpFreeformTask()
        val task2 = setUpFreeformTask()
        rootTaskDisplayAreaOrganizer.setTouchFirst(DEFAULT_DISPLAY)

        controller.moveToFullscreen(task2.taskId, transitionSource = UNKNOWN)

        val wct = getLatestExitDesktopWct()
        verify(desksOrganizer).deactivateDesk(wct, deskId = 0)
    }

    @Test
    fun moveToFullscreen_fromDesk_touchFirst_lastTaskInDesk_removesDesk() {
        val task = setUpFreeformTask()
        rootTaskDisplayAreaOrganizer.setTouchFirst(DEFAULT_DISPLAY)

        controller.moveToFullscreen(task.taskId, transitionSource = UNKNOWN)

        val wct = getLatestExitDesktopWct()
        verify(desksOrganizer).removeDesk(wct, deskId = 0, task.userId)
    }

    @Test
    fun moveToFullscreen_fromDesk_desktopFirst_deactivatesDesk() {
        val task = setUpFreeformTask()
        rootTaskDisplayAreaOrganizer.setDesktopFirst(DEFAULT_DISPLAY)

        controller.moveToFullscreen(task.taskId, transitionSource = UNKNOWN)

        val wct = getLatestExitDesktopWct()
        verify(desksOrganizer).deactivateDesk(wct, deskId = 0)
    }

    @Test
    fun moveToFullscreen_tdaFullscreen_windowingModeSetToUndefined() {
        val task = setUpFreeformTask()
        val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
        tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
        controller.moveToFullscreen(task.taskId, transitionSource = UNKNOWN)
        val wct = getLatestExitDesktopWct()
        verify(desktopRemoteListener, times(1))
            .onExitDesktopModeTransitionStarted(
                FULLSCREEN_ANIMATION_DURATION,
                shouldEndUpAtHome = false,
            )
        assertThat(wct.changes[task.token.asBinder()]?.windowingMode)
            .isEqualTo(WINDOWING_MODE_UNDEFINED)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER)
    fun moveToFullscreen_tdaFullscreen_windowingModeUndefined_removesWallpaperActivity_multiDesksEnabled() {
        desktopState.enterDesktopByDefaultOnFreeformDisplay = true
        setUpHomeTask()
        val task = setUpFreeformTask()
        assertNotNull(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY))
            .configuration
            .windowConfiguration
            .windowingMode = WINDOWING_MODE_FULLSCREEN

        controller.moveToFullscreen(task.taskId, transitionSource = UNKNOWN)

        val wct = getLatestExitDesktopWct()
        val taskChange = assertNotNull(wct.changes[task.token.asBinder()])
        verify(desktopRemoteListener)
            .onExitDesktopModeTransitionStarted(
                FULLSCREEN_ANIMATION_DURATION,
                shouldEndUpAtHome = false,
            )
        assertThat(taskChange.windowingMode).isEqualTo(WINDOWING_MODE_UNDEFINED)
        // Removes wallpaper activity when leaving desktop
        wct.assertReorder(wallpaperToken, toTop = false)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER)
    fun moveToFullscreen_tdaFullscreen_windowingModeUndefined_homeBehindFullscreen_multiDesksEnabled() {
        desktopState.enterDesktopByDefaultOnFreeformDisplay = true
        val homeTask = setUpHomeTask()
        val task = setUpFreeformTask()
        assertNotNull(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY))
            .configuration
            .windowConfiguration
            .windowingMode = WINDOWING_MODE_FULLSCREEN

        controller.moveToFullscreen(task.taskId, transitionSource = UNKNOWN)

        val wct = getLatestExitDesktopWct()
        val taskChange = assertNotNull(wct.changes[task.token.asBinder()])
        verify(desktopRemoteListener)
            .onExitDesktopModeTransitionStarted(
                FULLSCREEN_ANIMATION_DURATION,
                shouldEndUpAtHome = false,
            )
        assertThat(taskChange.windowingMode).isEqualTo(WINDOWING_MODE_UNDEFINED)
        // Moves home task behind the fullscreen task
        val homeReorderIndex = wct.indexOfReorder(homeTask, toTop = true)
        val fullscreenReorderIndex = wct.indexOfReorder(task, toTop = true)
        assertThat(homeReorderIndex).isNotEqualTo(-1)
        assertThat(fullscreenReorderIndex).isNotEqualTo(-1)
        assertThat(fullscreenReorderIndex).isGreaterThan(homeReorderIndex)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER)
    fun moveToFullscreen_tdaFreeform_enforcedDesktop_doesNotReorderHome() {
        desktopState.enterDesktopByDefaultOnFreeformDisplay = true
        val homeTask = setUpHomeTask()
        val task = setUpFreeformTask()
        assertNotNull(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY))
            .configuration
            .windowConfiguration
            .windowingMode = WINDOWING_MODE_FREEFORM

        controller.moveToFullscreen(task.taskId, transitionSource = UNKNOWN)

        val wct = getLatestExitDesktopWct()
        verify(desktopRemoteListener)
            .onExitDesktopModeTransitionStarted(
                FULLSCREEN_ANIMATION_DURATION,
                shouldEndUpAtHome = false,
            )
        // Removes wallpaper activity when leaving desktop but doesn't reorder home or the task
        wct.assertReorder(wallpaperToken, toTop = false)
        wct.assertWithoutHop(ReorderPredicate(homeTask.token, toTop = null))
    }

    @Test
    fun moveToFullscreen_tdaFreeform_windowingModeSetToFullscreen() {
        val task = setUpFreeformTask()
        val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
        tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM
        controller.moveToFullscreen(task.taskId, transitionSource = UNKNOWN)
        val wct = getLatestExitDesktopWct()
        assertThat(wct.changes[task.token.asBinder()]?.windowingMode)
            .isEqualTo(WINDOWING_MODE_FULLSCREEN)
        verify(desktopRemoteListener)
            .onExitDesktopModeTransitionStarted(
                FULLSCREEN_ANIMATION_DURATION,
                shouldEndUpAtHome = false,
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER)
    fun moveToFullscreen_tdaFreeform_windowingModeFullscreen_removesWallpaperActivity_multiDesksEnabled() {
        val homeTask = setUpHomeTask()
        val task = setUpFreeformTask()

        assertNotNull(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY))
            .configuration
            .windowConfiguration
            .windowingMode = WINDOWING_MODE_FREEFORM

        controller.moveToFullscreen(task.taskId, transitionSource = UNKNOWN)

        val wct = getLatestExitDesktopWct()
        val taskChange = assertNotNull(wct.changes[task.token.asBinder()])
        assertThat(taskChange.windowingMode).isEqualTo(WINDOWING_MODE_FULLSCREEN)
        verify(desktopRemoteListener)
            .onExitDesktopModeTransitionStarted(
                FULLSCREEN_ANIMATION_DURATION,
                shouldEndUpAtHome = false,
            )
        // Removes wallpaper activity when leaving desktop
        wct.assertReorder(wallpaperToken, toTop = false)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER)
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_FIRST_BASED_DEFAULT_TO_DESKTOP_BUGFIX)
    fun moveToFullscreen_tdaFreeform_windowingModeFullscreen_homeBehindFullscreen_multiDesksEnabled() {
        desktopState.enterDesktopByDefaultOnFreeformDisplay = false
        val homeTask = setUpHomeTask()
        val task = setUpFreeformTask()
        assertNotNull(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY))
            .configuration
            .windowConfiguration
            .windowingMode = WINDOWING_MODE_FREEFORM

        controller.moveToFullscreen(task.taskId, transitionSource = UNKNOWN)

        val wct = getLatestExitDesktopWct()
        val taskChange = assertNotNull(wct.changes[task.token.asBinder()])
        assertThat(taskChange.windowingMode).isEqualTo(WINDOWING_MODE_FULLSCREEN)
        verify(desktopRemoteListener)
            .onExitDesktopModeTransitionStarted(
                FULLSCREEN_ANIMATION_DURATION,
                shouldEndUpAtHome = false,
            )
        // Moves home task behind the fullscreen task
        val homeReorderIndex = wct.indexOfReorder(homeTask, toTop = true)
        val fullscreenReorderIndex = wct.indexOfReorder(task, toTop = true)
        assertThat(homeReorderIndex).isNotEqualTo(-1)
        assertThat(fullscreenReorderIndex).isNotEqualTo(-1)
        assertThat(fullscreenReorderIndex).isGreaterThan(homeReorderIndex)
    }

    @Test
    fun moveToFullscreen_nonExistentTask_doesNothing() {
        controller.moveToFullscreen(999, transitionSource = UNKNOWN)
        verifyExitDesktopWCTNotExecuted()
    }

    @Test
    @EnableFlags(com.android.launcher3.Flags.FLAG_ENABLE_ALT_TAB_KQS_FLATENNING)
    fun moveToFullscreen_fullscreenNonRunningTask_deskActive_cleansUpSourceDisplay() {
        whenever(focusTransitionObserver.globallyFocusedDisplayId).thenReturn(SECOND_DISPLAY)
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = 9)
        taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = 9)
        val task = setUpFullscreenTask(displayId = INVALID_TASK_ID, background = true)

        controller.moveToFullscreen(task.taskId, transitionSource = UNKNOWN)

        val wct = getLatestExitDesktopWct()
        // Verify that cleanup (e.g. resetting the launcher) is performed on the source display
        // where the task originated from.
        wct.assertPendingIntent(launchHomeIntent(SECOND_DISPLAY))
        wct.assertPendingIntentActivityOptionsLaunchDisplayId(SECOND_DISPLAY)
    }

    @Test
    @EnableFlags(com.android.launcher3.Flags.FLAG_ENABLE_ALT_TAB_KQS_FLATENNING)
    fun moveToFullscreen_fullscreenNonRunningTask_noDeskActive_cleansUpSourceDisplay() {
        whenever(focusTransitionObserver.globallyFocusedDisplayId).thenReturn(SECOND_DISPLAY)
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = 9)
        taskRepository.setDeskInactive(deskId = 9)
        val task = setUpFullscreenTask(displayId = INVALID_TASK_ID, background = true)

        controller.moveToFullscreen(task.taskId, transitionSource = UNKNOWN)

        val wct = getLatestExitDesktopWct()
        // Verify that cleanup (e.g. resetting the launcher) is performed on the source display
        // where the task originated from.
        wct.assertPendingIntent(launchHomeIntent(SECOND_DISPLAY))
        wct.assertPendingIntentActivityOptionsLaunchDisplayId(SECOND_DISPLAY)
    }

    @Test
    fun moveToFullscreen_secondDisplayTaskHasFreeform_secondDisplayNotAffected() {
        val taskDefaultDisplay = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        val taskSecondDisplay = setUpFreeformTask(displayId = SECOND_DISPLAY)
        controller.moveToFullscreen(taskDefaultDisplay.taskId, transitionSource = UNKNOWN)

        with(getLatestExitDesktopWct()) {
            assertThat(changes.keys).contains(taskDefaultDisplay.token.asBinder())
            assertThat(changes.keys).doesNotContain(taskSecondDisplay.token.asBinder())
        }
        verify(desktopRemoteListener)
            .onExitDesktopModeTransitionStarted(
                FULLSCREEN_ANIMATION_DURATION,
                shouldEndUpAtHome = false,
            )
    }

    @Test
    @EnableFlags(com.android.launcher3.Flags.FLAG_ENABLE_ALT_TAB_KQS_FLATENNING)
    fun moveToFullscreen_fullscreenTaskWithRemoteTransition_transitToFrontUsesRemoteTransition() {
        val transitionHandlerArgCaptor = argumentCaptor<TransitionHandler>()
        whenever(transitions.startTransition(anyInt(), any(), transitionHandlerArgCaptor.capture()))
            .thenReturn(Binder())

        val task = setUpFullscreenTask()
        assertNotNull(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY))
            .configuration
            .windowConfiguration
            .windowingMode = WINDOWING_MODE_FREEFORM

        controller.moveToFullscreen(
            task.taskId,
            transitionSource = UNKNOWN,
            remoteTransition = RemoteTransition(spy(TestRemoteTransition())),
        )

        val wct =
            getLatestWct(type = TRANSIT_TO_FRONT, handlerClass = OneShotRemoteHandler::class.java)
        assertThat(wct.changes[task.token.asBinder()]?.windowingMode)
            .isEqualTo(WINDOWING_MODE_FULLSCREEN)
        verify(desktopRemoteListener, never()).onEnterDesktopModeTransitionStarted(anyInt())
        assertIs<OneShotRemoteHandler>(transitionHandlerArgCaptor.firstValue)
    }

    @Test
    @EnableFlags(com.android.launcher3.Flags.FLAG_ENABLE_ALT_TAB_KQS_FLATENNING)
    fun moveToFullscreen_backgroundFullscreenTask_launchesFullscreenTask() {
        val task = createRecentTaskInfo(1, INVALID_DISPLAY)
        whenever(recentTasksController.findTaskInBackground(task.taskId)).thenReturn(task)
        whenever(shellTaskOrganizer.getRunningTaskInfo(anyInt())).thenReturn(null)

        whenever(focusTransitionObserver.globallyFocusedDisplayId).thenReturn(SECOND_DISPLAY)
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))

        val transitionHandlerArgCaptor = argumentCaptor<TransitionHandler>()
        whenever(transitions.startTransition(anyInt(), any(), transitionHandlerArgCaptor.capture()))
            .thenReturn(Binder())

        controller.moveToFullscreen(
            task.taskId,
            transitionSource = UNKNOWN,
            remoteTransition = RemoteTransition(spy(TestRemoteTransition())),
        )

        val wct =
            getLatestWct(type = TRANSIT_TO_FRONT, handlerClass = OneShotRemoteHandler::class.java)
        wct.assertLaunchTask(task.taskId, WINDOWING_MODE_FULLSCREEN)
        verify(desktopRemoteListener, never()).onEnterDesktopModeTransitionStarted(anyInt())
        assertIs<OneShotRemoteHandler>(transitionHandlerArgCaptor.firstValue)
    }

    @Test
    @DisableFlags(com.android.launcher3.Flags.FLAG_ENABLE_ALT_TAB_KQS_FLATENNING)
    fun moveToFullscreen_backgroundFullscreenTask_ignoredWhenFlagOff() {
        val task = createRecentTaskInfo(1, INVALID_DISPLAY)
        whenever(recentTasksController.findTaskInBackground(task.taskId)).thenReturn(task)
        whenever(shellTaskOrganizer.getRunningTaskInfo(anyInt())).thenReturn(null)

        controller.moveToFullscreen(task.taskId, transitionSource = UNKNOWN)

        verify(snapEventHandler, never()).removeTaskIfTiled(anyInt(), anyInt())
        verify(transitions, never()).startTransition(anyInt(), any(), any())
        verify(desktopRemoteListener, never()).onEnterDesktopModeTransitionStarted(anyInt())
    }

    @Test
    fun moveTaskToFront_desktopTask_reordersToFront() {
        val task1 = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = 0)
        setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = 0)
        mockStartLaunchTransition(transitionType = TRANSIT_TO_FRONT, taskId = task1.taskId)

        controller.moveTaskToFront(task1, remoteTransition = null)

        verify(desksOrganizer).reorderTaskToFront(any(), eq(0), eq(task1))
    }

    @Test
    fun moveTaskToFront_nonDesktopTask_reordersToFront() {
        val task = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)
        mockStartLaunchTransition(transitionType = TRANSIT_TO_FRONT, taskId = task.taskId)

        controller.moveTaskToFront(task, remoteTransition = null)

        val wct = getLatestDesktopMixedTaskWct(type = TRANSIT_TO_FRONT)
        assertNotNull(wct)
        wct.assertReorder(task = task, toTop = true, includingParents = true)
    }

    @Test
    fun moveTaskToFront_nonDesktopTask_doesNotActivateDesk() {
        val task = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)
        mockStartLaunchTransition(transitionType = TRANSIT_TO_FRONT, taskId = task.taskId)

        controller.moveTaskToFront(task, remoteTransition = null)

        val wct = getLatestDesktopMixedTaskWct(type = TRANSIT_TO_FRONT)
        assertNotNull(wct)
        verify(desksOrganizer, never()).activateDesk(eq(wct), any(), any())
    }

    @Test
    fun moveTaskToFront_bringsTasksOverLimit_separateTaskLimitTransition_minimizeSeparately() {
        val deskId = 0
        setUpHomeTask()
        val freeformTasks =
            (1..MAX_TASK_LIMIT + 1).map { _ ->
                setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = deskId)
            }
        val transition = Binder()
        mockStartLaunchTransition(
            transition = transition,
            transitionType = TRANSIT_TO_FRONT,
            taskId = freeformTasks[0].taskId,
        )

        controller.moveTaskToFront(freeformTasks[0], remoteTransition = null)

        verify(desksOrganizer, never()).minimizeTask(any(), any(), any())
        assertThat(desktopTasksLimiter.getMinimizingTask(transition)).isNull()
        assertThat(desktopTasksLimiter.hasTaskLimitTransitionForTesting(transition)).isTrue()
    }

    @Test
    fun moveTaskToFront_minimizedTask_marksTaskAsUnminimized() {
        val transition = Binder()
        val freeformTask = setUpFreeformTask()
        taskRepository.minimizeTask(DEFAULT_DISPLAY, freeformTask.taskId)
        mockStartLaunchTransition(
            transition = transition,
            transitionType = TRANSIT_TO_FRONT,
            taskId = freeformTask.taskId,
        )

        controller.moveTaskToFront(freeformTask, unminimizeReason = UnminimizeReason.ALT_TAB)

        val task = desktopTasksLimiter.getUnminimizingTask(transition)
        assertThat(task).isNotNull()
        assertThat(task?.taskId).isEqualTo(freeformTask.taskId)
        assertThat(task?.unminimizeReason).isEqualTo(UnminimizeReason.ALT_TAB)
    }

    @Test
    fun handleRequest_minimizedFreeformTask_marksTaskAsUnminimized() {
        val transition = Binder()
        // Create a visible task so we stay in Desktop Mode when minimizing task under test.
        setUpFreeformTask().also { markTaskVisible(it) }
        val freeformTask = setUpFreeformTask()
        taskRepository.minimizeTask(DEFAULT_DISPLAY, freeformTask.taskId)

        controller.handleRequest(transition, createTransition(freeformTask, TRANSIT_OPEN))

        val task = desktopTasksLimiter.getUnminimizingTask(transition)
        assertThat(task).isNotNull()
        assertThat(task?.taskId).isEqualTo(freeformTask.taskId)
        assertThat(task?.unminimizeReason).isEqualTo(UnminimizeReason.TASK_LAUNCH)
    }

    @Test
    fun handleRequest_minimizedFreeformTask_unminimizesTask() {
        val deskId = 5
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = deskId)
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = deskId)

        val transition = Binder()
        // Create a visible task so we stay in Desktop Mode when minimizing task under test.
        setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = deskId).also { markTaskVisible(it) }
        val freeformTask = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = deskId)
        taskRepository.minimizeTask(DEFAULT_DISPLAY, freeformTask.taskId)
        whenever(desksOrganizer.isTaskInDesk(freeformTask.taskId, deskId)).thenReturn(true)

        controller.handleRequest(transition, createTransition(freeformTask, TRANSIT_OPEN))

        verify(desksOrganizer).unminimizeTask(any(), eq(deskId), eq(freeformTask))
    }

    @Test
    fun handleRequest_minimizedFreeformTask_notInDesk_movesToDesk() {
        val deskId = 5
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = deskId)
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = deskId)

        val transition = Binder()
        // Create a visible task so we stay in Desktop Mode when minimizing task under test.
        setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = deskId).also { markTaskVisible(it) }
        val freeformTask = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = deskId)
        taskRepository.minimizeTask(DEFAULT_DISPLAY, freeformTask.taskId)
        whenever(desksOrganizer.isTaskInDesk(freeformTask.taskId, deskId)).thenReturn(false)

        controller.handleRequest(transition, createTransition(freeformTask, TRANSIT_OPEN))

        verify(desksOrganizer).moveTaskToDesk(any(), eq(deskId), eq(freeformTask), eq(false))
    }

    @Test
    fun handleRequest_freeformTask_launchBringsToFront() {
        val deskId = 5
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = deskId)
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = deskId)

        val transition = Binder()
        val freeformTask = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = deskId)

        controller.handleRequest(transition, createTransition(freeformTask, TRANSIT_OPEN))

        verify(desksOrganizer).reorderTaskToFront(any(), eq(deskId), eq(freeformTask))
    }

    @Test
    fun moveTaskToFront_remoteTransition_usesOneshotHandler() {
        setUpHomeTask()
        val freeformTasks = List(MAX_TASK_LIMIT) { setUpFreeformTask() }
        val transitionHandlerArgCaptor = argumentCaptor<TransitionHandler>()
        whenever(transitions.startTransition(anyInt(), any(), transitionHandlerArgCaptor.capture()))
            .thenReturn(Binder())

        controller.moveTaskToFront(freeformTasks[0], RemoteTransition(TestRemoteTransition()))

        assertIs<OneShotRemoteHandler>(transitionHandlerArgCaptor.firstValue)
    }

    @Test
    fun moveTaskToFront_backgroundTask_launchesTask() {
        val task = createRecentTaskInfo(1)
        whenever(shellTaskOrganizer.getRunningTaskInfo(anyInt())).thenReturn(null)
        mockStartLaunchTransition(transitionType = TRANSIT_OPEN)

        controller.moveTaskToFront(task.taskId, unminimizeReason = UnminimizeReason.UNKNOWN)

        val wct = getLatestDesktopMixedTaskWct(type = TRANSIT_OPEN)
        wct.assertLaunchTask(task.taskId, WINDOWING_MODE_FREEFORM)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY)
    fun moveTaskToFront_backgroundTask_launchesTask_launchesToExistingDisplay() {
        val deskId = 2
        val taskId = 1
        val task = createRecentTaskInfo(taskId, displayId = SECOND_DISPLAY)
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = deskId)
        taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = deskId)
        taskRepository.addTaskToDesk(
            displayId = SECOND_DISPLAY,
            deskId = deskId,
            taskId = task.taskId,
            isVisible = true,
            taskBounds = TASK_BOUNDS,
        )
        whenever(shellTaskOrganizer.getRunningTaskInfo(anyInt())).thenReturn(null)
        mockStartLaunchTransition(transitionType = TRANSIT_OPEN)

        controller.moveTaskToFront(task.taskId, unminimizeReason = UnminimizeReason.UNKNOWN)

        val wct = getLatestDesktopMixedTaskWct(type = TRANSIT_OPEN)
        wct.assertLaunchTaskOnDisplay(SECOND_DISPLAY)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY)
    fun moveTaskToFront_backgroundTask_notInDesk_launchesInAssociatedDisplay() {
        val deskId = 2
        val taskId = 1
        val task = createRecentTaskInfo(taskId, displayId = SECOND_DISPLAY)
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = deskId)
        whenever(shellTaskOrganizer.getRunningTaskInfo(taskId)).thenReturn(null)
        whenever(recentTasksController.findTaskInBackground(taskId)).thenReturn(task)
        mockStartLaunchTransition(transitionType = TRANSIT_OPEN)
        desktopState.overrideDesktopModeSupportPerDisplay[SECOND_DISPLAY] = true

        controller.moveTaskToFront(task.taskId, unminimizeReason = UnminimizeReason.UNKNOWN)

        val wct = getLatestDesktopMixedTaskWct(type = TRANSIT_OPEN)
        wct.assertLaunchTaskOnDisplay(SECOND_DISPLAY)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY)
    fun moveTaskToFront_backgroundTask_notInDesk_unsupportedAssociatedDisplay_launchesInFocused() {
        val focusedDisplayId = 10
        val deskId = 2
        val taskId = 1
        val task = createRecentTaskInfo(taskId, displayId = SECOND_DISPLAY)
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = deskId)
        taskRepository.addDesk(displayId = focusedDisplayId, deskId = focusedDisplayId)
        whenever(shellTaskOrganizer.getRunningTaskInfo(taskId)).thenReturn(null)
        whenever(recentTasksController.findTaskInBackground(taskId)).thenReturn(task)
        whenever(focusTransitionObserver.globallyFocusedDisplayId).thenReturn(focusedDisplayId)
        mockStartLaunchTransition(transitionType = TRANSIT_OPEN)
        desktopState.overrideDesktopModeSupportPerDisplay[SECOND_DISPLAY] = false
        desktopState.overrideDesktopModeSupportPerDisplay[focusedDisplayId] = true

        controller.moveTaskToFront(task.taskId, unminimizeReason = UnminimizeReason.UNKNOWN)

        val wct = getLatestDesktopMixedTaskWct(type = TRANSIT_OPEN)
        wct.assertLaunchTaskOnDisplay(focusedDisplayId)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY)
    fun moveTaskToFront_backgroundTask_notInDesk_unsupportedAssociatedAndFocusedDisplay_launchesInSupported() {
        val supportedDisplayId = 11
        val focusedDisplayId = 10
        val deskId = 2
        val taskId = 1
        val task = createRecentTaskInfo(taskId, displayId = SECOND_DISPLAY)
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = deskId)
        taskRepository.addDesk(displayId = focusedDisplayId, deskId = focusedDisplayId)
        taskRepository.addDesk(displayId = supportedDisplayId, deskId = supportedDisplayId)
        whenever(shellTaskOrganizer.getRunningTaskInfo(taskId)).thenReturn(null)
        whenever(recentTasksController.findTaskInBackground(taskId)).thenReturn(task)
        whenever(focusTransitionObserver.globallyFocusedDisplayId).thenReturn(focusedDisplayId)
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(SECOND_DISPLAY, focusedDisplayId, supportedDisplayId))
        mockStartLaunchTransition(transitionType = TRANSIT_OPEN)
        desktopState.overrideDesktopModeSupportPerDisplay[SECOND_DISPLAY] = false
        desktopState.overrideDesktopModeSupportPerDisplay[focusedDisplayId] = false
        desktopState.overrideDesktopModeSupportPerDisplay[supportedDisplayId] = true

        controller.moveTaskToFront(task.taskId, unminimizeReason = UnminimizeReason.UNKNOWN)

        val wct = getLatestDesktopMixedTaskWct(type = TRANSIT_OPEN)
        wct.assertLaunchTaskOnDisplay(supportedDisplayId)
    }

    @Test
    fun moveToNextDisplay_noOtherDisplays() {
        whenever(rootTaskDisplayAreaOrganizer.displayIds).thenReturn(intArrayOf(DEFAULT_DISPLAY))
        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        controller.moveToNextDisplay(task.taskId, EnterReason.UNKNOWN_ENTER)
        verify(transitions, never()).startTransition(anyInt(), any(), anyOrNull())
    }

    @Test
    fun moveToNextDisplay_moveFromFirstToSecondDisplay_multiDesksEnabled() {
        // Set up two display ids
        val targetDeskId = 2
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = targetDeskId)
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))

        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        controller.moveToNextDisplay(task.taskId, EnterReason.UNKNOWN_ENTER)

        verify(desksOrganizer).moveTaskToDesk(any(), eq(targetDeskId), eq(task), eq(false))
    }

    @Test
    fun moveToNextDisplay_moveFromSecondToFirstDisplay_multiDesksEnabled() {
        // Set up two display ids
        val targetDeskId = 0
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        // Create a mock for the target display area: default display
        val defaultDisplayArea = DisplayAreaInfo(MockToken().token(), DEFAULT_DISPLAY, 0)
        whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY))
            .thenReturn(defaultDisplayArea)

        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        val task = setUpFreeformTask(displayId = SECOND_DISPLAY)
        controller.moveToNextDisplay(task.taskId, EnterReason.UNKNOWN_ENTER)

        verify(desksOrganizer).moveTaskToDesk(any(), eq(targetDeskId), eq(task), eq(false))
    }

    @Test
    @EnableFlags(
        FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER,
    )
    fun moveToNextDisplay_wallpaperOnSystemUser_reorderWallpaperToBack() {
        // Set up two display ids
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        // Add a task and a wallpaper
        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY)

        controller.moveToNextDisplay(task.taskId, EnterReason.UNKNOWN_ENTER)

        with(
            getLatestWct(
                type = TRANSIT_CHANGE,
                handlerClass = DesktopModeMoveToDisplayTransitionHandler::class.java,
            )
        ) {
            val wallpaperChange =
                hierarchyOps.find { op -> op.container == wallpaperToken.asBinder() }
            assertNotNull(wallpaperChange)
            assertThat(wallpaperChange.type).isEqualTo(HIERARCHY_OP_TYPE_REORDER)
        }
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY)
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER)
    fun moveToNextDisplay_wallpaperNotOnSystemUser_removeWallpaper() {
        // Set up two display ids
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        // Add a task and a wallpaper
        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY)

        controller.moveToNextDisplay(task.taskId, EnterReason.UNKNOWN_ENTER)

        val wallpaperChange =
            getLatestWct(
                    type = TRANSIT_CHANGE,
                    handlerClass = DesktopModeMoveToDisplayTransitionHandler::class.java,
                )
                .hierarchyOps
                .find { op -> op.container == wallpaperToken.asBinder() }
        assertNotNull(wallpaperChange)
        assertThat(wallpaperChange.type).isEqualTo(HIERARCHY_OP_TYPE_REMOVE_TASK)
    }

    @Test
    fun moveToNextDisplay_sizeInDpPreserved() {
        // Set up two display ids
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = 2)
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        // Two displays have different density
        whenever(displayLayout.densityDpi()).thenReturn(320)
        whenever(displayLayout.width()).thenReturn(2400)
        whenever(displayLayout.height()).thenReturn(1600)
        val secondaryLayout = mock(DisplayLayout::class.java)
        whenever(displayController.getDisplayLayout(SECOND_DISPLAY)).thenReturn(secondaryLayout)
        whenever(secondaryLayout.densityDpi()).thenReturn(160)
        whenever(secondaryLayout.width()).thenReturn(1280)
        whenever(secondaryLayout.height()).thenReturn(720)

        // Place a task with a size of 640x480 at a position where the ratio of the left margin to
        // the right margin is 1:3 and the ratio of top margin to the bottom margin is 1:2.
        val task =
            setUpFreeformTask(displayId = DEFAULT_DISPLAY, bounds = Rect(440, 374, 1080, 854))

        controller.moveToNextDisplay(task.taskId, EnterReason.UNKNOWN_ENTER)

        val taskChange =
            getLatestWct(
                    type = TRANSIT_CHANGE,
                    handlerClass = DesktopModeMoveToDisplayTransitionHandler::class.java,
                )
                .changes[task.token.asBinder()]
        assertNotNull(taskChange)
        // To preserve DP size, pixel size is changed to 320x240. The ratio of the left margin
        // to the right margin and the ratio of the top margin to bottom margin are also
        // preserved.
        assertThat(taskChange.configuration.windowConfiguration.bounds)
            .isEqualTo(Rect(240, 160, 560, 400))
    }

    @Test
    fun moveToNextDisplay_shiftWithinDestinationDisplayBounds() {
        // Set up two display ids
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = 2)
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        // Two displays have different density
        whenever(displayLayout.densityDpi()).thenReturn(320)
        whenever(displayLayout.width()).thenReturn(2400)
        whenever(displayLayout.height()).thenReturn(1600)
        val secondaryLayout = mock(DisplayLayout::class.java)
        whenever(displayController.getDisplayLayout(SECOND_DISPLAY)).thenReturn(secondaryLayout)
        whenever(secondaryLayout.densityDpi()).thenReturn(160)
        whenever(secondaryLayout.width()).thenReturn(1280)
        whenever(secondaryLayout.height()).thenReturn(720)

        // Place a task with a size of 640x480 at a position where the bottom-right corner of the
        // window is outside the source display bounds. The destination display still has enough
        // space to place the window within its bounds.
        val task =
            setUpFreeformTask(displayId = DEFAULT_DISPLAY, bounds = Rect(2000, 1200, 2640, 1680))

        controller.moveToNextDisplay(task.taskId, EnterReason.UNKNOWN_ENTER)

        val taskChange =
            getLatestWct(
                    type = TRANSIT_CHANGE,
                    handlerClass = DesktopModeMoveToDisplayTransitionHandler::class.java,
                )
                .changes[task.token.asBinder()]
        assertNotNull(taskChange)
        assertThat(taskChange.configuration.windowConfiguration.bounds)
            .isEqualTo(Rect(960, 480, 1280, 720))
    }

    @Test
    fun moveToNextDisplay_maximizedTask() {
        // Set up two display ids
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = 2)
        // Two displays have different density
        whenever(displayLayout.densityDpi()).thenReturn(320)
        whenever(displayLayout.width()).thenReturn(1280)
        whenever(displayLayout.height()).thenReturn(960)
        val secondaryLayout = mock(DisplayLayout::class.java)
        whenever(displayController.getDisplayLayout(SECOND_DISPLAY)).thenReturn(secondaryLayout)
        whenever(secondaryLayout.densityDpi()).thenReturn(160)
        whenever(secondaryLayout.width()).thenReturn(1280)
        whenever(secondaryLayout.height()).thenReturn(720)

        // Place a task with a size equals to display size.
        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY, bounds = Rect(0, 0, 1280, 960))

        controller.moveToNextDisplay(task.taskId, EnterReason.UNKNOWN_ENTER)

        val taskChange =
            getLatestWct(
                    type = TRANSIT_CHANGE,
                    handlerClass = DesktopModeMoveToDisplayTransitionHandler::class.java,
                )
                .changes[task.token.asBinder()]
        assertNotNull(taskChange)
        // DP size is preserved. The window is centered in the destination display.
        assertThat(taskChange.configuration.windowConfiguration.bounds)
            .isEqualTo(Rect(320, 120, 960, 600))
    }

    @Test
    fun moveToNextDisplay_maximizeWhenDestinationTooSmall() {
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        // Set up two display ids
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        // Two displays have different density
        whenever(displayLayout.densityDpi()).thenReturn(320)
        whenever(displayLayout.width()).thenReturn(2400)
        whenever(displayLayout.height()).thenReturn(1600)
        val secondaryLayout = mock(DisplayLayout::class.java)
        whenever(displayController.getDisplayLayout(SECOND_DISPLAY)).thenReturn(secondaryLayout)
        whenever(secondaryLayout.densityDpi()).thenReturn(160)
        whenever(secondaryLayout.width()).thenReturn(640)
        whenever(secondaryLayout.height()).thenReturn(480)
        whenever(secondaryLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(0, 0, 640, 480)
        }

        // A task with a size of 1800x1200 is being placed. To preserve DP size,
        // 900x600 pixels are needed, which does not fit in the destination display.
        val task =
            setUpFreeformTask(displayId = DEFAULT_DISPLAY, bounds = Rect(300, 200, 2100, 1400))

        controller.moveToNextDisplay(task.taskId, EnterReason.UNKNOWN_ENTER)

        val taskChange =
            getLatestWct(
                    type = TRANSIT_CHANGE,
                    handlerClass = DesktopModeMoveToDisplayTransitionHandler::class.java,
                )
                .changes[task.token.asBinder()]
        assertNotNull(taskChange)
        assertThat(taskChange.configuration.windowConfiguration.bounds.left).isEqualTo(0)
        assertThat(taskChange.configuration.windowConfiguration.bounds.top).isEqualTo(0)
        assertThat(taskChange.configuration.windowConfiguration.bounds.right).isEqualTo(640)
        assertThat(taskChange.configuration.windowConfiguration.bounds.bottom).isEqualTo(480)
    }

    @Test
    fun moveToNextDisplay_destinationGainGlobalFocus() {
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        // Set up two display ids
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))

        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        controller.moveToNextDisplay(task.taskId, EnterReason.UNKNOWN_ENTER)

        val wct =
            getLatestWct(
                type = TRANSIT_CHANGE,
                handlerClass = DesktopModeMoveToDisplayTransitionHandler::class.java,
            )
        wct.assertReorderAt(
            // Reorder should be the last change so that other hierarchyOps do not change the
            // display focus after moving the destination display top.
            index = wct.hierarchyOps.size - 1,
            task,
            toTop = true,
            includingParents = true,
        )
    }

    @Test
    fun moveToNextDisplay_toDesktopInOtherDisplay_multiDesksEnabled_bringsExistingTasksToFront() {
        val transition = Binder()
        val sourceDeskId = 0
        val targetDeskId = 2
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = targetDeskId)
        taskRepository.setDeskInactive(deskId = targetDeskId)
        // Set up two display ids
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        whenever(transitions.startTransition(eq(TRANSIT_CHANGE), any(), anyOrNull()))
            .thenReturn(transition)
        val task1 = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = sourceDeskId)
        val task2 = setUpFreeformTask(displayId = SECOND_DISPLAY, deskId = targetDeskId)

        controller.moveToNextDisplay(task1.taskId, EnterReason.UNKNOWN_ENTER)

        // Existing desktop task in the target display is moved to front.
        val wct = getLatestTransition()
        assertNotNull(wct)
        verify(desksOrganizer).reorderTaskToFront(wct, targetDeskId, task2)
    }

    @Test
    fun moveToNextDisplay_toDesktopInOtherDisplay_appliesTaskLimitSeparate() {
        val transition = Binder()
        val sourceDeskId = 0
        val targetDeskId = 2
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = targetDeskId)
        taskRepository.setDeskInactive(deskId = targetDeskId)
        val targetDeskTasks =
            (1..MAX_TASK_LIMIT + 1).map { _ ->
                setUpFreeformTask(displayId = SECOND_DISPLAY, deskId = targetDeskId)
            }
        // Set up two display ids
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        whenever(transitions.startTransition(eq(TRANSIT_CHANGE), any(), anyOrNull()))
            .thenReturn(transition)
        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = sourceDeskId)

        controller.moveToNextDisplay(task.taskId, EnterReason.UNKNOWN_ENTER)

        val wct = getLatestTransition()
        assertNotNull(wct)
        assertThat(desktopTasksLimiter.hasTaskLimitTransitionForTesting(transition)).isTrue()
        verify(desksOrganizer, never()).minimizeTask(wct, targetDeskId, targetDeskTasks[0])
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER,
    )
    fun moveToNextDisplay_toDesktopInOtherDisplay_movesHomeAndWallpaperToFront() {
        val homeTask = setUpHomeTask(displayId = SECOND_DISPLAY)
        whenever(desktopWallpaperActivityTokenProvider.getToken(SECOND_DISPLAY))
            .thenReturn(wallpaperToken)
        val transition = Binder()
        val sourceDeskId = 0
        val targetDeskId = 2
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = targetDeskId)
        taskRepository.setDeskInactive(deskId = targetDeskId)
        // Set up two display ids
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        whenever(transitions.startTransition(eq(TRANSIT_CHANGE), any(), anyOrNull()))
            .thenReturn(transition)
        val task1 = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = sourceDeskId)

        controller.moveToNextDisplay(task1.taskId, EnterReason.UNKNOWN_ENTER)

        // Home / Wallpaper should be moved to front as the background of desktop tasks, otherwise
        // fullscreen (non-desktop) tasks could remain visible.
        val wct = getLatestTransition()
        val homeReorderIndex = wct.indexOfReorder(homeTask, toTop = true)
        val wallpaperReorderIndex = wct.indexOfReorder(wallpaperToken, toTop = true)
        assertThat(homeReorderIndex).isNotEqualTo(-1)
        assertThat(wallpaperReorderIndex).isNotEqualTo(-1)
        // Wallpaper last, to be in front of Home.
        assertThat(wallpaperReorderIndex).isGreaterThan(homeReorderIndex)
    }

    @Test
    fun moveToNextDisplay_toDeskInOtherDisplay_movesToDeskAndActivates() {
        val transition = Binder()
        val targetDeskId = 4
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = targetDeskId)
        taskRepository.setDeskInactive(deskId = targetDeskId)
        // Set up two display ids
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        whenever(transitions.startTransition(eq(TRANSIT_CHANGE), any(), anyOrNull()))
            .thenReturn(transition)

        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        taskRepository.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = 0,
            taskId = task.taskId,
            isVisible = true,
            taskBounds = TASK_BOUNDS,
        )
        controller.moveToNextDisplay(task.taskId, EnterReason.UNKNOWN_ENTER)

        verify(desksOrganizer).moveTaskToDesk(any(), eq(targetDeskId), eq(task), eq(false))
        verify(desksOrganizer).activateDesk(any(), eq(targetDeskId), skipReorder = eq(false))
        verify(desksTransitionsObserver)
            .addPendingTransition(
                DeskTransition.ActivateDeskWithTask(
                    token = transition,
                    userId = taskRepository.userId,
                    displayId = SECOND_DISPLAY,
                    deskId = targetDeskId,
                    enterTaskId = task.taskId,
                    enterReason = EnterReason.UNKNOWN_ENTER,
                )
            )
    }

    @Test
    fun moveToNextDisplay_wasLastTaskInSourceDesk_desktopFirst_deactivates() {
        rootTaskDisplayAreaOrganizer.setDesktopFirst(DEFAULT_DISPLAY)
        val transition = Binder()
        val sourceDeskId = 0
        val targetDeskId = 4
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = targetDeskId)
        taskRepository.setDeskInactive(deskId = targetDeskId)
        // Set up two display ids
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        whenever(transitions.startTransition(eq(TRANSIT_CHANGE), any(), anyOrNull()))
            .thenReturn(transition)

        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        taskRepository.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = sourceDeskId,
            taskId = task.taskId,
            isVisible = true,
            taskBounds = TASK_BOUNDS,
        )
        controller.moveToNextDisplay(task.taskId, EnterReason.UNKNOWN_ENTER)

        verify(desksOrganizer).deactivateDesk(any(), eq(sourceDeskId), skipReorder = eq(false))
        verify(desksTransitionsObserver)
            .addPendingTransition(
                DeskTransition.DeactivateDesk(
                    token = transition,
                    userId = taskRepository.userId,
                    deskId = sourceDeskId,
                    displayId = DEFAULT_DISPLAY,
                    switchingUser = false,
                    exitReason = ExitReason.TASK_MOVED_FROM_DESK,
                )
            )
    }

    @Test
    fun moveToNextDisplay_wasLastTaskInSourceDesk_touchFirst_removesDesk() {
        rootTaskDisplayAreaOrganizer.setTouchFirst(DEFAULT_DISPLAY)
        val transition = Binder()
        val sourceDeskId = 0
        val targetDeskId = 4
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = targetDeskId)
        taskRepository.setDeskInactive(deskId = targetDeskId)
        // Set up two display ids
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        whenever(transitions.startTransition(eq(TRANSIT_CHANGE), any(), anyOrNull()))
            .thenReturn(transition)

        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        taskRepository.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = sourceDeskId,
            taskId = task.taskId,
            isVisible = true,
            taskBounds = TASK_BOUNDS,
        )
        controller.moveToNextDisplay(task.taskId, EnterReason.UNKNOWN_ENTER)

        verify(desksOrganizer).removeDesk(any(), eq(sourceDeskId), eq(task.userId))
        verify(desksTransitionsObserver)
            .addPendingTransition(
                argThat {
                    this is DeskTransition.RemoveDesk &&
                        this.token == transition &&
                        this.deskId == sourceDeskId
                }
            )
    }

    @Test
    fun moveToNextDisplay_resetLauncherOnSourceDisplay() {
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        // Set up two display ids
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))

        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        controller.moveToNextDisplay(task.taskId, EnterReason.UNKNOWN_ENTER)

        val wct =
            getLatestWct(
                type = TRANSIT_CHANGE,
                handlerClass = DesktopModeMoveToDisplayTransitionHandler::class.java,
            )
        wct.assertPendingIntent(launchHomeIntent(DEFAULT_DISPLAY))
        wct.assertPendingIntentActivityOptionsLaunchDisplayId(DEFAULT_DISPLAY)
    }

    @Test
    fun moveToNextDisplay_movingToDesktop_sendsTaskbarRoundingUpdate() {
        val transition = Binder()
        val sourceDeskId = 0
        val targetDeskId = 2
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = targetDeskId)
        taskRepository.setDeskInactive(deskId = targetDeskId)
        // Set up two display ids
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        whenever(transitions.startTransition(eq(TRANSIT_CHANGE), any(), anyOrNull()))
            .thenReturn(transition)

        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = sourceDeskId)
        taskRepository.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = sourceDeskId,
            taskId = task.taskId,
            isVisible = true,
            taskBounds = TASK_BOUNDS,
        )
        controller.moveToNextDisplay(task.taskId, EnterReason.UNKNOWN_ENTER)

        verify(desktopRemoteListener).onTaskbarCornerRoundingUpdate(anyBoolean(), anyInt())
    }

    @Test
    fun returnToHomeOrOverview_sendsTaskbarRoundingUpdate() {
        val transition = Binder()
        val deskId = 0
        whenever(transitions.startTransition(eq(TRANSIT_CHANGE), any(), anyOrNull()))
            .thenReturn(transition)
        val wct = WindowContainerTransaction()
        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = deskId)
        taskRepository.addTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            deskId = deskId,
            taskId = task.taskId,
            isVisible = true,
            taskBounds = TASK_BOUNDS,
        )
        controller.performDesktopExitCleanUp(
            wct = wct,
            deskId = deskId,
            displayId = DEFAULT_DISPLAY,
            userId = task.userId,
            willExitDesktop = true,
            removingLastTaskId = null,
            exitReason = ExitReason.RETURN_HOME_OR_OVERVIEW,
        )
        verify(desktopRemoteListener).onTaskbarCornerRoundingUpdate(false, DEFAULT_DISPLAY)
    }

    @Test
    fun moveToNextDesktopDisplay_projectedMode_movesToFullscreenOnDefaultDisplay() {
        // Setup state where a desktop task is running on a secondary display while the device is in
        // projected mode
        desktopState.isProjected = true
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        val defaultDisplayArea = DisplayAreaInfo(MockToken().token(), DEFAULT_DISPLAY, 0)
        whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY))
            .thenReturn(defaultDisplayArea)
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        val task = setUpFreeformTask(displayId = SECOND_DISPLAY)

        controller.moveToNextDesktopDisplay(
            taskId = task.taskId,
            userId = taskRepository.userId,
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        val wct =
            getLatestWct(
                type = TRANSIT_CHANGE,
                handlerClass = DesktopModeMoveToDisplayTransitionHandler::class.java,
            )

        // Verify that the task is reparented to the default display and set to fullscreen mode
        val hierarchyOp =
            wct.hierarchyOps.find { it.container == task.token.asBinder() && it.isReparent }
        assertNotNull(hierarchyOp)
        assertThat(hierarchyOp.newParent).isEqualTo(defaultDisplayArea.token.asBinder())
        val configChange = wct.changes[task.token.asBinder()]
        assertNotNull(configChange)
        assertThat(configChange.windowingMode).isEqualTo(WINDOWING_MODE_FULLSCREEN)
    }

    @Test
    fun moveToNextDesktopDisplay_projectedMode_fromSecondaryDisplay_doesNotCleanUpSourceDisplay() {
        // Setup state where a desktop task is running on a secondary display while the device is in
        // projected mode
        desktopState.isProjected = true
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        val defaultDisplayArea = DisplayAreaInfo(MockToken().token(), DEFAULT_DISPLAY, 0)
        whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY))
            .thenReturn(defaultDisplayArea)
        val deskId = SECOND_DISPLAY
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = deskId)
        taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = deskId)
        val task = setUpFreeformTask(displayId = SECOND_DISPLAY)
        // Explicitly add task to the desk because removing the last task in a desk triggers desktop
        // mode cleanup on default displays, but should not do so on secondary displays.
        taskRepository.addTaskToDesk(
            displayId = SECOND_DISPLAY,
            deskId = deskId,
            taskId = task.taskId,
            isVisible = true,
            taskBounds = TASK_BOUNDS,
        )

        controller.moveToNextDesktopDisplay(
            taskId = task.taskId,
            userId = taskRepository.userId,
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        val wct =
            getLatestWct(
                type = TRANSIT_CHANGE,
                handlerClass = DesktopModeMoveToDisplayTransitionHandler::class.java,
            )

        // Verify that cleanup (e.g. resetting the launcher) is NOT performed on the source
        // secondary display
        wct.assertWithoutPendingIntent(launchHomeIntent(SECOND_DISPLAY))
    }

    @Test
    fun moveToNextDesktopDisplay_projectedMode_movesToDesktopOnConnectedDisplay() {
        // Setup state where a desktop task is running on a secondary display while the device is in
        // projected mode
        desktopState.isProjected = true
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        val task = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)
        doReturn(false).whenever(desktopModeCompatPolicy).shouldDisableDesktopEntryPoints(task)

        controller.moveToNextDesktopDisplay(
            taskId = task.taskId,
            userId = taskRepository.userId,
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        verify(desksOrganizer).moveTaskToDesk(any(), eq(SECOND_DISPLAY), eq(task), eq(false))
    }

    @Test
    fun moveToNextDesktopDisplay_projectedMode_movesToDesktopOnConnectedDisplay_skipTasksWithDisabledEntryPoints() {
        // Setup state where a desktop task is running on a secondary display while the device is in
        // projected mode
        desktopState.isProjected = true
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        val task = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)

        // The task should not have desktop entry points
        doReturn(true).whenever(desktopModeCompatPolicy).shouldDisableDesktopEntryPoints(task)

        controller.moveToNextDesktopDisplay(
            taskId = task.taskId,
            userId = taskRepository.userId,
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        verify(desksOrganizer, never()).moveTaskToDesk(any(), any(), eq(task), any())
    }

    private fun moveToNextDesktopDisplay_moveIifDesktopModeSupportedOnDestination(
        isDesktopModeSupportedOnDestination: Boolean
    ) {
        // Set up two display ids
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))

        // Add desk if destination support desktop
        if (isDesktopModeSupportedOnDestination) {
            taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = 2)
        }

        desktopState.overrideDesktopModeSupportPerDisplay[SECOND_DISPLAY] =
            isDesktopModeSupportedOnDestination

        // Set up a task on the default display
        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY)

        controller.moveToNextDesktopDisplay(
            taskId = task.taskId,
            userId = taskRepository.userId,
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        val verificationMode =
            if (isDesktopModeSupportedOnDestination) {
                times(1)
            } else {
                never()
            }
        verify(transitions, verificationMode)
            .startTransition(
                eq(TRANSIT_CHANGE),
                any<WindowContainerTransaction>(),
                isA(DesktopModeMoveToDisplayTransitionHandler::class.java),
            )
        verify(snapEventHandler, verificationMode).removeTaskIfTiled(task.displayId, task.taskId)
    }

    @Test
    fun moveToNextDesktopDisplay_moveIfDesktopModeSupportedOnDestination() {
        moveToNextDesktopDisplay_moveIifDesktopModeSupportedOnDestination(true)
    }

    @Test
    fun moveToNextDesktopDisplay_dontMoveIfDesktopModeNotSupportedOnDestination() {
        moveToNextDesktopDisplay_moveIifDesktopModeSupportedOnDestination(false)
    }

    @Test
    fun moveToNextDesktopDisplay_dontMoveIfDestinationFocusesFullscreenTask() {
        // Set up displays
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = 2)
        desktopState.overrideDesktopModeSupportPerDisplay[SECOND_DISPLAY] = true

        // Set up a focused fullscreen task on the secondary display
        val fullscreenTask = setUpFullscreenTask(displayId = SECOND_DISPLAY)
        fullscreenTask.isFocused = true

        // Set up a task on the default display
        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY)

        controller.moveToNextDesktopDisplay(
            taskId = task.taskId,
            userId = taskRepository.userId,
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        verify(transitions, never())
            .startTransition(
                eq(TRANSIT_CHANGE),
                any<WindowContainerTransaction>(),
                isA(DesktopModeMoveToDisplayTransitionHandler::class.java),
            )
    }

    @Test
    fun moveToNextDesktopDisplay_moveIfDestinationFocusesDesktopWallpaper() {
        // Set up displays
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = 2)
        desktopState.overrideDesktopModeSupportPerDisplay[SECOND_DISPLAY] = true

        // Set up a focused fullscreen task on the secondary display
        val fullscreenTask = setUpFullscreenTask(displayId = SECOND_DISPLAY)
        fullscreenTask.isFocused = true
        fullscreenTask.baseIntent =
            Intent().apply { component = DesktopWallpaperActivity.wallpaperActivityComponent }

        // Set up a task on the default display
        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY)

        controller.moveToNextDesktopDisplay(
            taskId = task.taskId,
            userId = taskRepository.userId,
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        verify(transitions)
            .startTransition(
                eq(TRANSIT_CHANGE),
                any<WindowContainerTransaction>(),
                isA(DesktopModeMoveToDisplayTransitionHandler::class.java),
            )
    }

    @Test
    fun getTaskWindowingMode() {
        val fullscreenTask = setUpFullscreenTask()
        val freeformTask = setUpFreeformTask()

        assertThat(controller.getTaskWindowingMode(fullscreenTask.taskId))
            .isEqualTo(WINDOWING_MODE_FULLSCREEN)
        assertThat(controller.getTaskWindowingMode(freeformTask.taskId))
            .isEqualTo(WINDOWING_MODE_FREEFORM)
        assertThat(controller.getTaskWindowingMode(999)).isEqualTo(WINDOWING_MODE_UNDEFINED)
    }

    @Test
    fun onDesktopWindowClose_noActiveTasks() {
        val task = setUpFreeformTask(active = false)
        val wct = WindowContainerTransaction()
        controller.onDesktopWindowClose(wct, displayId = DEFAULT_DISPLAY, task)
        // Doesn't modify transaction
        assertThat(wct.hierarchyOps).isEmpty()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY)
    fun onDesktopWindowClose_singleActiveTask_noWallpaperActivityToken_launchesHome() {
        val task = setUpFreeformTask()
        val wct = WindowContainerTransaction()
        whenever(desktopWallpaperActivityTokenProvider.getToken()).thenReturn(null)

        controller.onDesktopWindowClose(wct, displayId = DEFAULT_DISPLAY, task)

        // Should launch home
        wct.assertPendingIntentAt(0, launchHomeIntent(DEFAULT_DISPLAY))
        wct.assertPendingIntentActivityOptionsLaunchDisplayIdAt(0, DEFAULT_DISPLAY)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER)
    fun onDesktopWindowClose_singleActiveTask_hasWallpaperActivityToken() {
        val task = setUpFreeformTask()

        val wct = WindowContainerTransaction()
        controller.onDesktopWindowClose(wct, displayId = DEFAULT_DISPLAY, task)
        // Adds remove wallpaper operation
        wct.assertReorderAt(index = 0, wallpaperToken, toTop = false)
    }

    @Test
    fun onDesktopWindowClose_singleActiveTask_isClosing() {
        val task = setUpFreeformTask()

        taskRepository.addClosingTask(displayId = DEFAULT_DISPLAY, deskId = 0, taskId = task.taskId)

        val wct = WindowContainerTransaction()
        controller.onDesktopWindowClose(wct, displayId = DEFAULT_DISPLAY, task)
        // Doesn't modify transaction
        assertThat(wct.hierarchyOps).isEmpty()
    }

    @Test
    fun onDesktopWindowClose_singleActiveTask_isMinimized() {
        val task = setUpFreeformTask()

        taskRepository.minimizeTask(DEFAULT_DISPLAY, task.taskId)

        val wct = WindowContainerTransaction()
        controller.onDesktopWindowClose(wct, displayId = DEFAULT_DISPLAY, task)
        // Doesn't modify transaction
        assertThat(wct.hierarchyOps).isEmpty()
    }

    @Test
    @EnableFlags(Flags.FLAG_SHOW_HOME_BEHIND_DESKTOP)
    fun onDesktopWindowClose_singleActiveTask_showHomeBehindDesktop_doesNotLaunchHome() {
        desktopState.shouldShowHomeBehindDesktop = true
        val task = setUpFreeformTask()
        val wct = WindowContainerTransaction()

        controller.onDesktopWindowClose(wct, displayId = DEFAULT_DISPLAY, task)

        wct.assertWithoutPendingIntent(launchHomeIntent(DEFAULT_DISPLAY))
    }

    @Test
    fun onDesktopWindowClose_notInDesk_returnsNullOnTransitStart() {
        val task = setUpFreeformTask(deskId = DEFAULT_DESK_ID)
        val wct = WindowContainerTransaction()
        taskRepository.removeDesk(DEFAULT_DESK_ID)

        val runOnTransitStart =
            controller.onDesktopWindowClose(wct, displayId = DEFAULT_DISPLAY, task)

        assertThat(runOnTransitStart).isNull()
    }

    @Test
    fun tilingBroken_onTaskMinimised() {
        val task = setUpFreeformTask()
        val transition = Binder()
        whenever(
                freeformTaskTransitionStarter.startMinimizedModeTransition(
                    any(),
                    anyInt(),
                    anyBoolean(),
                )
            )
            .thenReturn(transition)

        controller.minimizeTask(task, MinimizeReason.TASK_LIMIT)

        verify(snapEventHandler, times(1)).removeTaskIfTiled(task.displayId, task.taskId)
    }

    @Test
    fun onDesktopWindowClose_multipleActiveTasks() {
        val task1 = setUpFreeformTask()
        setUpFreeformTask()

        val wct = WindowContainerTransaction()
        controller.onDesktopWindowClose(wct, displayId = DEFAULT_DISPLAY, task1)
        // Doesn't modify transaction
        assertThat(wct.hierarchyOps).isEmpty()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER)
    fun onDesktopWindowClose_multipleActiveTasks_isOnlyNonClosingTask() {
        val task1 = setUpFreeformTask()
        val task2 = setUpFreeformTask()

        taskRepository.addClosingTask(
            displayId = DEFAULT_DISPLAY,
            deskId = 0,
            taskId = task2.taskId,
        )

        val wct = WindowContainerTransaction()
        controller.onDesktopWindowClose(wct, displayId = DEFAULT_DISPLAY, task1)
        // Adds remove wallpaper operation
        wct.assertReorderAt(index = 0, wallpaperToken, toTop = false)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER)
    fun onDesktopWindowClose_multipleActiveTasks_hasMinimized() {
        val task1 = setUpFreeformTask()
        val task2 = setUpFreeformTask()

        taskRepository.minimizeTask(DEFAULT_DISPLAY, task2.taskId)

        val wct = WindowContainerTransaction()
        controller.onDesktopWindowClose(wct, displayId = DEFAULT_DISPLAY, task1)
        // Adds remove wallpaper operation
        wct.assertReorderAt(index = 0, wallpaperToken, toTop = false)
    }

    @Test
    fun onDesktopWindowClose_lastWindow_desktopFirst_deactivatesDesk() {
        rootTaskDisplayAreaOrganizer.setDesktopFirst(DEFAULT_DISPLAY)
        val task = setUpFreeformTask()
        val wct = WindowContainerTransaction()

        controller.onDesktopWindowClose(wct, displayId = DEFAULT_DISPLAY, task)

        verify(desksOrganizer).deactivateDesk(wct, deskId = 0)
    }

    @Test
    fun onDesktopWindowClose_lastWindow_touchFirst_removesDesk() {
        rootTaskDisplayAreaOrganizer.setTouchFirst(DEFAULT_DISPLAY)
        val task = setUpFreeformTask()
        val wct = WindowContainerTransaction()

        controller.onDesktopWindowClose(wct, displayId = DEFAULT_DISPLAY, task)

        verify(desksOrganizer).removeDesk(wct, deskId = 0, task.userId)
    }

    @Test
    fun onDesktopWindowClose_lastWindow_desktopFirst_forceKeepDesktop() {
        rootTaskDisplayAreaOrganizer.setDesktopFirst(DEFAULT_DISPLAY)
        val task = setUpFreeformTask()
        val wct = WindowContainerTransaction()

        controller.onDesktopWindowClose(
            wct,
            displayId = DEFAULT_DISPLAY,
            task,
            forceKeepDesktop = true,
        )

        verify(desksOrganizer, never()).deactivateDesk(wct, deskId = 0)
    }

    @Test
    fun onDesktopWindowClose_lastWindow_touchFirst_forceKeepDesktop() {
        rootTaskDisplayAreaOrganizer.setTouchFirst(DEFAULT_DISPLAY)
        val task = setUpFreeformTask()
        val wct = WindowContainerTransaction()

        controller.onDesktopWindowClose(
            wct,
            displayId = DEFAULT_DISPLAY,
            task,
            forceKeepDesktop = true,
        )

        verify(desksOrganizer, never()).removeDesk(wct, deskId = 0, task.userId)
    }

    @Test
    fun onDesktopWindowClose_lastWindow_desktopFirst_addsPendingDeactivateTransition() {
        rootTaskDisplayAreaOrganizer.setDesktopFirst(DEFAULT_DISPLAY)
        val task = setUpFreeformTask()
        val wct = WindowContainerTransaction()

        val transition = Binder()
        val runOnTransitStart =
            controller.onDesktopWindowClose(wct, displayId = DEFAULT_DISPLAY, task)
        runOnTransitStart?.invoke(transition)

        verify(desksTransitionsObserver)
            .addPendingTransition(
                DeskTransition.DeactivateDesk(
                    transition,
                    userId = taskRepository.userId,
                    deskId = 0,
                    displayId = DEFAULT_DISPLAY,
                    switchingUser = false,
                    exitReason = ExitReason.TASK_FINISHED,
                )
            )
    }

    @Test
    fun onDesktopWindowClose_lastWindow_touchFirst_addsPendingRemoveTransition() {
        rootTaskDisplayAreaOrganizer.setTouchFirst(DEFAULT_DISPLAY)
        val task = setUpFreeformTask()
        val wct = WindowContainerTransaction()

        val transition = Binder()
        val runOnTransitStart =
            controller.onDesktopWindowClose(wct, displayId = DEFAULT_DISPLAY, task)
        runOnTransitStart?.invoke(transition)

        verify(desksTransitionsObserver)
            .addPendingTransition(
                argThat {
                    this is DeskTransition.RemoveDesk &&
                        this.token == transition &&
                        this.deskId == 0
                }
            )
    }

    @Test
    fun onDesktopWindowClose_lastWindowOnSecondaryDisplay_doesNotExitDesktop() {
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = 2)
        taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = 2)
        val task = setUpFreeformTask(displayId = SECOND_DISPLAY, deskId = 2)
        val wct = WindowContainerTransaction()

        val transition = Binder()
        val runOnTransitStart =
            controller.onDesktopWindowClose(wct, displayId = SECOND_DISPLAY, task)
        runOnTransitStart?.invoke(transition)

        verify(desksOrganizer, never()).deactivateDesk(any(), any(), any())
        verify(desksOrganizer, never()).removeDesk(any(), any(), any())
        verifyNoInteractions(desksTransitionsObserver)
    }

    @Test
    fun onDesktopWindowMinimize_noActiveTask_doesntRemoveWallpaper() {
        val task = setUpFreeformTask(active = false)
        val transition = Binder()
        whenever(
                freeformTaskTransitionStarter.startMinimizedModeTransition(
                    any(),
                    anyInt(),
                    anyBoolean(),
                )
            )
            .thenReturn(transition)

        controller.minimizeTask(task, MinimizeReason.MINIMIZE_BUTTON)

        val captor = argumentCaptor<WindowContainerTransaction>()
        verify(freeformTaskTransitionStarter)
            .startMinimizedModeTransition(captor.capture(), eq(task.taskId), eq(false))
        assertThat(
                captor.firstValue.hierarchyOps.none { hop ->
                    hop.type == HIERARCHY_OP_TYPE_REMOVE_TASK &&
                        hop.container == wallpaperToken.asBinder()
                }
            )
            .isTrue()
    }

    @Test
    fun onDesktopWindowMinimize_lastWindow_dontDeactivateDesk() {
        val task = setUpFreeformTask()
        val transition = Binder()
        whenever(
                freeformTaskTransitionStarter.startMinimizedModeTransition(
                    any(),
                    anyInt(),
                    anyBoolean(),
                )
            )
            .thenReturn(transition)

        controller.minimizeTask(task, MinimizeReason.MINIMIZE_BUTTON)

        val captor = argumentCaptor<WindowContainerTransaction>()
        verify(freeformTaskTransitionStarter)
            .startMinimizedModeTransition(captor.capture(), eq(task.taskId), eq(true))

        assertTrue(captor.firstValue.isEmpty)
    }

    @Test
    fun onDesktopWindowMinimize_lastWindow_dontAddPendingDeactivateTransition() {
        val task = setUpFreeformTask()
        val transition = Binder()
        whenever(
                freeformTaskTransitionStarter.startMinimizedModeTransition(
                    any(),
                    anyInt(),
                    anyBoolean(),
                )
            )
            .thenReturn(transition)

        controller.minimizeTask(task, MinimizeReason.MINIMIZE_BUTTON)

        verifyNoInteractions(desksTransitionsObserver)
    }

    private fun minimizePipTask(task: RunningTaskInfo, appOpsAllowed: Boolean = true) {
        val handler = mock(TransitionHandler::class.java)
        whenever(transitions.dispatchRequest(any(), any(), anyOrNull()))
            .thenReturn(Transitions.RequestResult(WindowContainerTransaction(), handler))
        spyContext.addMockSystemService(Context.APP_OPS_SERVICE, mockAppOpsManager)

        whenever(
                mockAppOpsManager.checkOpNoThrow(
                    eq(AppOpsManager.OP_PICTURE_IN_PICTURE),
                    any(),
                    any(),
                )
            )
            .thenReturn(
                if (appOpsAllowed) AppOpsManager.MODE_ALLOWED else AppOpsManager.MODE_IGNORED
            )

        controller.minimizeTask(task, MinimizeReason.MINIMIZE_BUTTON)
    }

    @Test
    fun onPipTaskMinimize_autoEnterEnabled_startPipTransition() {
        val task = setUpPipTask(autoEnterEnabled = true)

        minimizePipTask(task)

        verify(freeformTaskTransitionStarter).startPipTransition(any())
        verify(freeformTaskTransitionStarter, never())
            .startMinimizedModeTransition(any(), anyInt(), anyBoolean())
    }

    @Test
    fun onPipTaskMinimize_autoEnterDisabled_startMinimizeTransition() {
        val task = setUpPipTask(autoEnterEnabled = false)
        whenever(
                freeformTaskTransitionStarter.startMinimizedModeTransition(
                    any(),
                    anyInt(),
                    anyBoolean(),
                )
            )
            .thenReturn(Binder())

        minimizePipTask(task)

        verify(freeformTaskTransitionStarter)
            .startMinimizedModeTransition(any(), eq(task.taskId), anyBoolean())
        verify(freeformTaskTransitionStarter, never()).startPipTransition(any())
    }

    @Test
    fun onPipTaskMinimize_pipNotAllowedInAppOps_startMinimizeTransition() {
        val task = setUpPipTask(autoEnterEnabled = true)
        whenever(
                freeformTaskTransitionStarter.startMinimizedModeTransition(
                    any(),
                    anyInt(),
                    anyBoolean(),
                )
            )
            .thenReturn(Binder())
        whenever(
                mockAppOpsManager.checkOpNoThrow(
                    eq(AppOpsManager.OP_PICTURE_IN_PICTURE),
                    any(),
                    any(),
                )
            )
            .thenReturn(AppOpsManager.MODE_IGNORED)

        minimizePipTask(task, appOpsAllowed = false)

        verify(freeformTaskTransitionStarter)
            .startMinimizedModeTransition(any(), eq(task.taskId), anyBoolean())
        verify(freeformTaskTransitionStarter, never()).startPipTransition(any())
    }

    @Test
    fun onPipTaskMinimize_autoEnterEnabled_sendsTaskbarRoundingUpdate() {
        val task = setUpPipTask(autoEnterEnabled = true)

        minimizePipTask(task)

        verify(desktopRemoteListener).onTaskbarCornerRoundingUpdate(anyBoolean(), anyInt())
    }

    @Test
    fun onPipTaskMinimize_multiActivity_minimizeParent() {
        val task = setUpPipTask(autoEnterEnabled = true).apply { numActivities = 2 }
        // Add a second task so that entering PiP does not trigger Desktop cleanup
        setUpFreeformTask(deskId = DEFAULT_DISPLAY)

        minimizePipTask(task)

        val arg = argumentCaptor<WindowContainerTransaction>()
        verify(freeformTaskTransitionStarter).startPipTransition(arg.capture())
        verify(desksOrganizer)
            .minimizeTask(wct = arg.lastValue, deskId = DEFAULT_DISPLAY, task = task)
    }

    @Test
    fun onDesktopWindowMinimize_singleActiveTask_noWallpaperActivityToken_doesntRemoveWallpaper() {
        val task = setUpFreeformTask(active = true)
        val transition = Binder()
        whenever(
                freeformTaskTransitionStarter.startMinimizedModeTransition(
                    any(),
                    anyInt(),
                    anyBoolean(),
                )
            )
            .thenReturn(transition)

        controller.minimizeTask(task, MinimizeReason.MINIMIZE_BUTTON)

        val captor = argumentCaptor<WindowContainerTransaction>()
        verify(freeformTaskTransitionStarter)
            .startMinimizedModeTransition(captor.capture(), eq(task.taskId), eq(true))
        assertThat(
                captor.firstValue.hierarchyOps.none { hop ->
                    hop.type == HIERARCHY_OP_TYPE_REMOVE_TASK
                }
            )
            .isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER)
    fun onTaskMinimize_singleActiveTask_hasWallpaperActivityToken_dontRemoveWallpaper() {
        val task = setUpFreeformTask()
        val transition = Binder()
        whenever(
                freeformTaskTransitionStarter.startMinimizedModeTransition(
                    any(),
                    anyInt(),
                    anyBoolean(),
                )
            )
            .thenReturn(transition)

        // The only active task is being minimized.
        controller.minimizeTask(task, MinimizeReason.MINIMIZE_BUTTON)

        val captor = argumentCaptor<WindowContainerTransaction>()
        verify(freeformTaskTransitionStarter)
            .startMinimizedModeTransition(captor.capture(), eq(task.taskId), eq(true))

        assertThat(captor.firstValue.changes).isEmpty()
    }

    @Test
    fun onDesktopWindowMinimize_singleActiveTask_alreadyMinimized_doesntRemoveWallpaper() {
        val task = setUpFreeformTask()
        val transition = Binder()
        whenever(
                freeformTaskTransitionStarter.startMinimizedModeTransition(
                    any(),
                    anyInt(),
                    anyBoolean(),
                )
            )
            .thenReturn(transition)
        taskRepository.minimizeTask(DEFAULT_DISPLAY, task.taskId)

        // The only active task is already minimized.
        controller.minimizeTask(task, MinimizeReason.MINIMIZE_BUTTON)

        val captor = argumentCaptor<WindowContainerTransaction>()
        verify(freeformTaskTransitionStarter)
            .startMinimizedModeTransition(captor.capture(), eq(task.taskId), eq(false))
        assertThat(
                captor.firstValue.hierarchyOps.none { hop ->
                    hop.type == HIERARCHY_OP_TYPE_REMOVE_TASK &&
                        hop.container == wallpaperToken.asBinder()
                }
            )
            .isTrue()
    }

    @Test
    fun onDesktopWindowMinimize_multipleActiveTasks_doesntRemoveWallpaper() {
        val task1 = setUpFreeformTask(active = true)
        setUpFreeformTask(active = true)
        val transition = Binder()
        whenever(
                freeformTaskTransitionStarter.startMinimizedModeTransition(
                    any(),
                    anyInt(),
                    anyBoolean(),
                )
            )
            .thenReturn(transition)

        controller.minimizeTask(task1, MinimizeReason.MINIMIZE_BUTTON)

        val captor = argumentCaptor<WindowContainerTransaction>()
        verify(freeformTaskTransitionStarter)
            .startMinimizedModeTransition(captor.capture(), eq(task1.taskId), eq(false))
        assertThat(
                captor.firstValue.hierarchyOps.none { hop ->
                    hop.type == HIERARCHY_OP_TYPE_REMOVE_TASK &&
                        hop.container == wallpaperToken.asBinder()
                }
            )
            .isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER)
    fun onDesktopWindowMinimize_multipleActiveTasks_minimizesTheOnlyVisibleTask_dontRemoveWallpaper() {
        val task1 = setUpFreeformTask(active = true)
        val task2 = setUpFreeformTask(active = true)
        val transition = Binder()
        whenever(
                freeformTaskTransitionStarter.startMinimizedModeTransition(
                    any(),
                    anyInt(),
                    anyBoolean(),
                )
            )
            .thenReturn(transition)
        taskRepository.minimizeTask(DEFAULT_DISPLAY, task2.taskId)

        // task1 is the only visible task as task2 is minimized.
        controller.minimizeTask(task1, MinimizeReason.MINIMIZE_BUTTON)

        val captor = argumentCaptor<WindowContainerTransaction>()
        verify(freeformTaskTransitionStarter)
            .startMinimizedModeTransition(captor.capture(), eq(task1.taskId), eq(true))

        assertTrue(captor.firstValue.isEmpty)
    }

    @Test
    fun onDesktopWindowMinimize_triesToExitImmersive() {
        val task = setUpFreeformTask()
        val transition = Binder()
        whenever(
                freeformTaskTransitionStarter.startMinimizedModeTransition(
                    any(),
                    anyInt(),
                    anyBoolean(),
                )
            )
            .thenReturn(transition)

        controller.minimizeTask(task, MinimizeReason.MINIMIZE_BUTTON)

        verify(mMockDesktopImmersiveController).exitImmersiveIfApplicable(any(), eq(task), any())
    }

    @Test
    fun onDesktopWindowMinimize_invokesImmersiveTransitionStartCallback() {
        val task = setUpFreeformTask()
        val transition = Binder()
        val runOnTransit = RunOnStartTransitionCallback()
        whenever(
                freeformTaskTransitionStarter.startMinimizedModeTransition(
                    any(),
                    anyInt(),
                    anyBoolean(),
                )
            )
            .thenReturn(transition)
        whenever(mMockDesktopImmersiveController.exitImmersiveIfApplicable(any(), eq(task), any()))
            .thenReturn(
                ExitResult.Exit(exitingTask = task.taskId, runOnTransitionStart = runOnTransit)
            )

        controller.minimizeTask(task, MinimizeReason.MINIMIZE_BUTTON)

        assertThat(runOnTransit.invocations).isEqualTo(1)
        assertThat(runOnTransit.lastInvoked).isEqualTo(transition)
    }

    @Test
    fun onDesktopWindowMinimize_minimizesTask() {
        val task = setUpFreeformTask()
        val transition = Binder()
        val runOnTransit = RunOnStartTransitionCallback()
        whenever(
                freeformTaskTransitionStarter.startMinimizedModeTransition(
                    any(),
                    anyInt(),
                    anyBoolean(),
                )
            )
            .thenReturn(transition)
        whenever(mMockDesktopImmersiveController.exitImmersiveIfApplicable(any(), eq(task), any()))
            .thenReturn(
                ExitResult.Exit(exitingTask = task.taskId, runOnTransitionStart = runOnTransit)
            )

        controller.minimizeTask(task, MinimizeReason.MINIMIZE_BUTTON)

        verify(desksOrganizer).minimizeTask(any(), /* deskId= */ eq(0), eq(task))
    }

    @Test
    fun onDesktopWindowMinimize_triesToStopTiling() {
        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        val transition = Binder()
        whenever(
                freeformTaskTransitionStarter.startMinimizedModeTransition(
                    any(),
                    anyInt(),
                    anyBoolean(),
                )
            )
            .thenReturn(transition)

        controller.minimizeTask(task, MinimizeReason.MINIMIZE_BUTTON)

        verify(snapEventHandler).removeTaskIfTiled(eq(DEFAULT_DISPLAY), eq(task.taskId))
    }

    @Test
    fun onDesktopWindowMinimize_sendsTaskbarRoundingUpdate() {
        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        val transition = Binder()
        whenever(
                freeformTaskTransitionStarter.startMinimizedModeTransition(
                    any(),
                    anyInt(),
                    anyBoolean(),
                )
            )
            .thenReturn(transition)

        controller.minimizeTask(task, MinimizeReason.MINIMIZE_BUTTON)

        verify(desktopRemoteListener).onTaskbarCornerRoundingUpdate(anyBoolean(), anyInt())
    }

    @Test
    fun minimizeMultiActivityPipTask_minimizesTask() {
        val deskId = DEFAULT_DISPLAY
        val task = setUpPipTask(autoEnterEnabled = true, deskId = deskId)
        val wct = WindowContainerTransaction()
        val transition = Binder()

        val runOnTransitStart =
            controller.minimizeMultiActivityPipTask(wct = wct, deskId = deskId, task = task)
        runOnTransitStart.invoke(transition)

        verify(desksOrganizer).minimizeTask(wct = wct, deskId = deskId, task = task)
        val minimizingTaskId =
            assertNotNull(desktopTasksLimiter.getMinimizingTask(transition)?.taskId)
        assertThat(minimizingTaskId).isEqualTo(task.taskId)
    }

    @Test
    fun closeTask_desktop_closesTask() {
        val task = setUpFreeformTask()
        task.baseActivity = ComponentName("mypacakge", "mypacakge.MyActivity")

        val result = controller.closeTask(task)

        assertThat(result).isEqualTo(DesktopTasksController.CloseTaskResult.CLOSED_DESKTOP)
        verify(freeformTaskTransitionStarter).startRemoveTransition(any())
    }

    @Test
    fun closeTask_desktop_minimizedTask_doesNothing() {
        val task = setUpFreeformTask()
        task.baseActivity = ComponentName("mypacakge", "mypacakge.MyActivity")
        userRepositories.getProfile(task.userId).minimizeTask(task.displayId, task.taskId)

        val result = controller.closeTask(task)

        assertThat(result).isEqualTo(DesktopTasksController.CloseTaskResult.NOT_CLOESD_MINIMIZED)
        verifyWCTNotExecuted()
    }

    @Test
    @EnableFlags(FLAG_CLOSE_FULLSCREEN_AND_SPLITSCREEN_KEYBOARD_SHORTCUT)
    fun closeTask_lockTask_doesNothing() {
        val task = setUpFullscreenTask()
        task.baseActivity = ComponentName("mypacakge", "mypacakge.MyActivity")
        whenever(lockTaskChangeListener.isTaskLocked).thenReturn(true)

        val result = controller.closeTask(task)

        assertThat(result).isEqualTo(DesktopTasksController.CloseTaskResult.NOT_CLOSED_TASK_LOCKED)
        verifyWCTNotExecuted()
    }

    @Test
    @EnableFlags(FLAG_CLOSE_FULLSCREEN_AND_SPLITSCREEN_KEYBOARD_SHORTCUT)
    fun closeTask_fullscreen_closesTask() {
        val task = setUpFullscreenTask()
        task.baseActivity = ComponentName("mypacakge", "mypacakge.MyActivity")

        val result = controller.closeTask(task)

        assertThat(result).isEqualTo(DesktopTasksController.CloseTaskResult.CLOSED_FULLSCREEN)

        val wct = getLatestWct(type = TRANSIT_CLOSE)
        assertThat(wct.hierarchyOps).hasSize(1)
        val hierarchyOp = wct.hierarchyOps[0]
        assertThat(hierarchyOp.type).isEqualTo(HIERARCHY_OP_TYPE_REMOVE_TASK)
        assertThat(hierarchyOp.container).isEqualTo(task.token.asBinder())
    }

    @Test
    fun closeTask_splitScreen_requestsCloseTask() {
        val task = createSplitScreenTask()
        task.baseActivity = ComponentName("mypacakge", "mypacakge.MyActivity")
        val otherTask = createSplitScreenTask()

        whenever(splitScreenController.isTaskInSplitScreen(task.taskId)).thenReturn(true)
        whenever(splitScreenController.getSplitPosition(task.taskId))
            .thenReturn(SPLIT_POSITION_TOP_OR_LEFT)
        whenever(splitScreenController.getTaskInfo(SPLIT_POSITION_BOTTOM_OR_RIGHT))
            .thenReturn(otherTask)

        val result = controller.closeTask(task)

        assertThat(result)
            .isEqualTo(DesktopTasksController.CloseTaskResult.CLOSE_REQUESTED_SPLIT_SCREEN)
        verify(splitScreenController).closeTask(eq(task.taskId))
    }

    @Test
    fun handleRequest_fullscreenTask_switchToDesktop_movesTaskToDesk() {
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 5)
        setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = 5)
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 5)

        val fullscreenTask = createFullscreenTask()
        val wct = controller.handleRequest(Binder(), createTransition(fullscreenTask))

        assertNotNull(wct, "should handle request")
        verify(desksOrganizer).moveTaskToDesk(wct = wct, deskId = 5, task = fullscreenTask)
    }

    @Test
    fun handleRequest_fullscreenTaskThatWasInactiveInDesk_touchFirst_tracksDeskRemoval() {
        rootTaskDisplayAreaOrganizer.setTouchFirst(DEFAULT_DISPLAY)
        // Set up and existing desktop task in an active desk.
        val inactiveInDeskTask = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = 0)
        taskRepository.setDeskInactive(deskId = 0)

        // Now the task is launching as fullscreen.
        inactiveInDeskTask.configuration.windowConfiguration.windowingMode =
            WINDOWING_MODE_FULLSCREEN
        val transition = Binder()
        val wct = controller.handleRequest(transition, createTransition(inactiveInDeskTask))

        // Desk is deactivated.
        assertNotNull(wct, "should handle request")
        verify(desksTransitionsObserver)
            .addPendingTransition(
                argThat {
                    this is DeskTransition.RemoveDesk &&
                        this.token == transition &&
                        this.deskId == 0
                }
            )
    }

    @Test
    fun handleRequest_fullscreenTask_deskActive_multiDesksEnabled_movesToDesk() {
        val deskId = 0
        taskRepository.setActiveDesk(DEFAULT_DISPLAY, deskId = deskId)
        setUpHomeTask()
        val fullscreenTask = createFullscreenTask()

        val wct = controller.handleRequest(Binder(), createTransition(fullscreenTask))

        assertNotNull(wct, "should handle request")
        verify(desksOrganizer).moveTaskToDesk(wct, deskId, fullscreenTask)
    }

    @Test
    fun handleRequest_fullscreenTaskWithTaskOnHome_activeDesk_multiDesksEnabled_movesToDesk() {
        val deskId = 0
        setUpHomeTask()
        setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = deskId)
        val fullscreenTask = createFullscreenTask()
        fullscreenTask.baseIntent.setFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME)

        val wct = controller.handleRequest(Binder(), createTransition(fullscreenTask))

        assertNotNull(wct, "should handle request")
        verify(desksOrganizer).moveTaskToDesk(wct, deskId, fullscreenTask)
    }

    @Test
    fun handleRequest_fullscreenTaskToDesk_underTaskLimit_multiDesksEnabled_dontMinimize() {
        val deskId = 5
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = deskId)
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = deskId)
        val freeformTask =
            setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = deskId).also {
                markTaskVisible(it)
            }

        // Launch a fullscreen task while in the desk.
        val fullscreenTask = createFullscreenTask()
        val transition = Binder()
        val wct = controller.handleRequest(transition, createTransition(fullscreenTask))

        assertNotNull(wct)
        verify(desksOrganizer, never()).minimizeTask(eq(wct), eq(deskId), any())
        assertNull(desktopTasksLimiter.getMinimizingTask(transition))
    }

    @Test
    fun handleRequest_fullscreenTaskToDesk_bringsTasksOverLimit_separateMinimizeFlagEnabled_minimizeSeparately() {
        val freeformTasks = (1..MAX_TASK_LIMIT).map { _ -> setUpFreeformTask() }
        freeformTasks.forEach { markTaskVisible(it) }
        val fullscreenTask = createFullscreenTask()
        val transition = Binder()

        val wct = controller.handleRequest(transition, createTransition(fullscreenTask))

        assertThat(wct!!.hierarchyOps.size).isAtMost(1)
        assertThat(desktopTasksLimiter.hasTaskLimitTransitionForTesting(transition)).isTrue()
    }

    @Test
    fun handleRequest_fullscreenTaskWithTaskOnHome_taskAddedToDesk() {
        // A desk with a minimized tasks, and non-minimized tasks already at the task limit.
        val deskId = 5
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = deskId)
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = deskId)

        // Launch a fullscreen task that brings Home to front with it.
        setUpHomeTask()
        val fullscreenTask = createFullscreenTask()
        fullscreenTask.baseIntent.setFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME)
        val wct = controller.handleRequest(Binder(), createTransition(fullscreenTask))

        assertNotNull(wct)
        verify(desksOrganizer).moveTaskToDesk(wct, deskId, fullscreenTask)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER,
    )
    fun handleRequest_fullscreenTaskWithTaskOnHome_activatesDesk() {
        val deskId = 5
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = deskId)
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = deskId)

        // Launch a fullscreen task that brings Home to front with it.
        val homeTask = setUpHomeTask()
        val fullscreenTask = createFullscreenTask()
        fullscreenTask.baseIntent.setFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME)
        val transition = Binder()
        val wct = controller.handleRequest(transition, createTransition(fullscreenTask))

        assertNotNull(wct)
        wct.assertReorder(homeTask, toTop = true)
        wct.assertReorder(wallpaperToken, toTop = true)
        verify(desksOrganizer).activateDesk(wct, deskId)
        verify(desksTransitionsObserver)
            .addPendingTransition(
                DeskTransition.ActivateDeskWithTask(
                    token = transition,
                    userId = taskRepository.userId,
                    displayId = DEFAULT_DISPLAY,
                    deskId = deskId,
                    enterTaskId = fullscreenTask.taskId,
                    enterReason = EnterReason.APP_FREEFORM_INTENT,
                )
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM)
    fun handleRequest_fullscreenTask_noInDesk_enforceDesktop_freeformDisplay_movesToDesk() {
        val deskId = 0
        taskRepository.setDeskInactive(deskId)
        whenever(desktopWallpaperActivityTokenProvider.getToken()).thenReturn(null)
        desktopState.enterDesktopByDefaultOnFreeformDisplay = true
        val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
        tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM

        val fullscreenTask = createFullscreenTask()
        val wct = controller.handleRequest(Binder(), createTransition(fullscreenTask))

        assertNotNull(wct, "should handle request")
        verify(desksOrganizer).moveTaskToDesk(wct, deskId, fullscreenTask)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_DESKTOP_FIRST_BASED_DEFAULT_TO_DESKTOP_BUGFIX,
    )
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM)
    fun handleRequest_fullscreenTask_noInDesk_enforceDesktop_freeformDisplay_movesToDesk_desktopFirst() {
        // Ensure the force enter desktop works when the deprecated flag is off.
        desktopState.enterDesktopByDefaultOnFreeformDisplay = false
        val deskId = 0
        taskRepository.setDeskInactive(deskId)
        whenever(desktopWallpaperActivityTokenProvider.getToken()).thenReturn(null)
        val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
        tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM

        val fullscreenTask = createFullscreenTask()
        val wct = controller.handleRequest(Binder(), createTransition(fullscreenTask))

        assertNotNull(wct, "should handle request")
        verify(desksOrganizer).moveTaskToDesk(wct, deskId, fullscreenTask)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM)
    fun handleRequest_fullscreenTask_noInDesk_enforceDesktop_secondaryDisplay_movesToDesk() {
        val deskId = 5
        taskRepository.addDesk(displayId = SECONDARY_DISPLAY_ID, deskId = deskId)
        taskRepository.setDeskInactive(deskId)
        whenever(desktopWallpaperActivityTokenProvider.getToken()).thenReturn(null)
        desktopState.enterDesktopByDefaultOnFreeformDisplay = true
        assertNotNull(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY))
            .configuration
            .windowConfiguration
            .windowingMode = WINDOWING_MODE_FREEFORM

        val fullscreenTask = createFullscreenTask(displayId = SECONDARY_DISPLAY_ID)
        val wct = controller.handleRequest(Binder(), createTransition(fullscreenTask))

        assertNotNull(wct, "should handle request")
        verify(desksOrganizer).moveTaskToDesk(wct, deskId, fullscreenTask)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_DESKTOP_FIRST_BASED_DEFAULT_TO_DESKTOP_BUGFIX,
        Flags.FLAG_ENABLE_DESKTOP_FIRST_TOP_FULLSCREEN_BUGFIX,
    )
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM)
    fun handleRequest_fullscreenTask_fullscreenFocused_freeformDisplay_returnNull() {
        val deskId = 0
        taskRepository.setDeskInactive(deskId)
        whenever(desktopWallpaperActivityTokenProvider.getToken()).thenReturn(null)
        val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
        tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM

        val focusedFullscreenTask = setUpFullscreenTask()
        whenever(focusTransitionObserver.getFocusedTaskIdOnDisplay(any()))
            .thenReturn(focusedFullscreenTask.taskId)

        val fullscreenTask = createFullscreenTask()

        assertThat(controller.handleRequest(Binder(), createTransition(fullscreenTask))).isNull()
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_DESKTOP_FIRST_BASED_DEFAULT_TO_DESKTOP_BUGFIX,
        Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM,
    )
    fun handleRequest_fullscreenTask_noInDesk_enforceDesktop_desktopFirst_returnNull() {
        val deskId = 0
        taskRepository.setDeskInactive(deskId)
        whenever(desktopWallpaperActivityTokenProvider.getToken()).thenReturn(null)
        desktopState.enterDesktopByDefaultOnFreeformDisplay = true
        val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
        tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM

        val fullscreenTask = createFullscreenTask()
        assertThat(controller.handleRequest(Binder(), createTransition(fullscreenTask))).isNull()
    }

    @Test
    fun handleRequest_fullscreenTask_notInDesk_enforceDesktop_fullscreenDisplay_returnNull() {
        taskRepository.setDeskInactive(deskId = 0)
        desktopState.enterDesktopByDefaultOnFreeformDisplay = true
        val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
        tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN

        val fullscreenTask = createFullscreenTask()
        val wct = controller.handleRequest(Binder(), createTransition(fullscreenTask))

        assertThat(wct).isNull()
    }

    @Test
    fun handleRequest_fullscreenTask_notInDesk_returnNull() {
        taskRepository.setDeskInactive(deskId = 0)
        val fullscreenTask = createFullscreenTask()

        assertThat(controller.handleRequest(Binder(), createTransition(fullscreenTask))).isNull()
    }

    @Test
    fun handleRequest_fullscreenTask_deskInOtherDisplayActive_returnNull() {
        taskRepository.setDeskInactive(deskId = 0)
        val fullscreenTaskDefaultDisplay = createFullscreenTask(displayId = DEFAULT_DISPLAY)
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = 2)
        taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = 2)

        val result =
            controller.handleRequest(Binder(), createTransition(fullscreenTaskDefaultDisplay))

        assertThat(result).isNull()
    }

    @Test
    fun handleRequest_freeform_aboveTaskLimit_separateMinimizeFlagEnabled_minimizeSeparately() {
        val deskId = 0
        val freeformTasks =
            (1..MAX_TASK_LIMIT).map { _ ->
                setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = deskId)
            }
        freeformTasks.forEach { markTaskVisible(it) }
        val newFreeformTask = createFreeformTask()
        val transition = Binder()

        val wct =
            controller.handleRequest(transition, createTransition(newFreeformTask, TRANSIT_OPEN))

        assertNotNull(wct)
        assertThat(wct.hierarchyOps).isEmpty()
        assertThat(desktopTasksLimiter.hasTaskLimitTransitionForTesting(transition)).isTrue()
    }

    @Test
    fun handleRequest_freeformTask_relaunchActiveTask_taskBecomesUndefined() {
        taskRepository.setDeskInactive(deskId = 0)
        val freeformTask = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = 0)
        markTaskHidden(freeformTask)

        val wct = controller.handleRequest(Binder(), createTransition(freeformTask))

        // Should become undefined as the TDA is set to fullscreen. It will inherit from the TDA.
        assertNotNull(wct, "should handle request")
        assertThat(wct.changes[freeformTask.token.asBinder()]?.windowingMode)
            .isEqualTo(WINDOWING_MODE_UNDEFINED)
    }

    @Test
    fun handleRequest_freeformTask_relaunchTask_enforceDesktop_freeformDisplay_noWinModeChange() {
        desktopState.enterDesktopByDefaultOnFreeformDisplay = true
        val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
        tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM

        val freeformTask = setUpFreeformTask()
        markTaskHidden(freeformTask)
        val wct = controller.handleRequest(Binder(), createTransition(freeformTask))

        assertNotNull(wct, "should handle request")
        assertFalse(wct.anyWindowingModeChange(freeformTask.token))
    }

    @Test
    fun handleRequest_freeformTask_relaunchTask_enforceDesktop_fullscreenDisplay_becomesUndefined() {
        desktopState.enterDesktopByDefaultOnFreeformDisplay = true
        val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
        tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
        taskRepository.setDeskInactive(deskId = 0)
        val freeformTask = setUpFreeformTask(DEFAULT_DISPLAY, deskId = 0)
        markTaskHidden(freeformTask)

        val wct = controller.handleRequest(Binder(), createTransition(freeformTask))

        assertNotNull(wct, "should handle request")
        assertThat(wct.changes[freeformTask.token.asBinder()]?.windowingMode)
            .isEqualTo(WINDOWING_MODE_UNDEFINED)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    fun handleRequest_freeformTask_desktopWallpaperEnabled_notInDesk_reorderedToTop() {
        whenever(desktopWallpaperActivityTokenProvider.getToken()).thenReturn(null)
        val deskId = 0
        taskRepository.setDeskInactive(deskId)
        val freeformTask1 = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = deskId)
        val freeformTask2 = createFreeformTask()

        val wct =
            controller.handleRequest(
                Binder(),
                createTransition(freeformTask2, type = TRANSIT_TO_FRONT),
            )

        assertNotNull(wct, "Should handle request")
        verify(desksOrganizer).reorderTaskToFront(wct, deskId, freeformTask1)
        wct.assertReorder(freeformTask2, toTop = true)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    fun handleRequest_freeformTask_dskWallpaperEnabled_freeformOnOtherDisplayOnly_reorderedToTop() {
        taskRepository.setDeskInactive(deskId = 0)
        whenever(desktopWallpaperActivityTokenProvider.getToken()).thenReturn(null)
        val taskDefaultDisplay = createFreeformTask(displayId = DEFAULT_DISPLAY)
        // Second display task
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = 2)
        taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = 2)
        setUpFreeformTask(displayId = SECOND_DISPLAY, deskId = 2)

        val result = controller.handleRequest(Binder(), createTransition(taskDefaultDisplay))

        assertNotNull(result, "Should handle request")
        assertThat(result.hierarchyOps?.size).isEqualTo(2)
        // Add desktop wallpaper activity
        result.assertPendingIntentAt(0, desktopWallpaperIntent)
        // Bring new task to front
        result.assertReorderAt(1, taskDefaultDisplay, toTop = true)
    }

    @Test
    fun handleRequest_freeformTask_alreadyInDesktop_noOverrideDensity_noConfigDensityChange() {
        desktopConfig.useDesktopOverrideDensity = false

        val freeformTask1 = setUpFreeformTask()
        markTaskVisible(freeformTask1)

        val freeformTask2 = createFreeformTask()
        val result =
            controller.handleRequest(
                freeformTask2.token.asBinder(),
                createTransition(freeformTask2),
            )
        assertFalse(result.anyDensityConfigChange(freeformTask2.token))
    }

    @Test
    fun handleRequest_freeformTask_alreadyInDesktop_overrideDensity_hasConfigDensityChange() {
        desktopConfig.useDesktopOverrideDensity = true

        val freeformTask1 = setUpFreeformTask()
        markTaskVisible(freeformTask1)

        val freeformTask2 = createFreeformTask()
        val result =
            controller.handleRequest(
                freeformTask2.token.asBinder(),
                createTransition(freeformTask2),
            )
        assertTrue(result.anyDensityConfigChange(freeformTask2.token))
    }

    @Test
    fun handleRequest_freeformTask_keyguardLocked_returnNull() {
        whenever(keyguardManager.isKeyguardLocked).thenReturn(true)
        val freeformTask = createFreeformTask(displayId = DEFAULT_DISPLAY)

        val result = controller.handleRequest(Binder(), createTransition(freeformTask))

        assertNull(result, "Should NOT handle request")
    }

    @Test
    fun handleRequest_freeformTask_notInDesktop_noForceEnterDesktop_movesTaskToDesk() {
        desktopState.enterDesktopByDefaultOnFreeformDisplay = false
        taskRepository.setDeskInactive(deskId = 0)
        val freeformTask = createFreeformTask(displayId = DEFAULT_DISPLAY)

        val result = controller.handleRequest(Binder(), createTransition(freeformTask))

        assertNotNull(result)
        verify(desksOrganizer).moveTaskToDesk(result, deskId = 0, freeformTask)
    }

    @Test
    fun handleRequest_freeformTask_alreadyInDesktop_noForceEnterDesktop_movesTaskToDesk() {
        desktopState.enterDesktopByDefaultOnFreeformDisplay = false
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val freeformTask = createFreeformTask(displayId = DEFAULT_DISPLAY)

        val result = controller.handleRequest(Binder(), createTransition(freeformTask))

        assertNotNull(result)
        verify(desksOrganizer).moveTaskToDesk(result, deskId = 0, freeformTask)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_FIRST_BASED_DEFAULT_TO_DESKTOP_BUGFIX,
        Flags.FLAG_ENABLE_DESKTOP_FIRST_TOP_FULLSCREEN_BUGFIX,
    )
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM)
    fun handleRequest_freeformTask_fullscreenFocused_freeformDisplay_moveToFullscreen() {
        val deskId = 0
        taskRepository.setDeskInactive(deskId)
        whenever(desktopWallpaperActivityTokenProvider.getToken()).thenReturn(null)
        val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
        tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM

        val focusedFullscreenTask = setUpFullscreenTask()
        whenever(focusTransitionObserver.getFocusedTaskIdOnDisplay(any()))
            .thenReturn(focusedFullscreenTask.taskId)

        val freeformTask = createFreeformTask(displayId = DEFAULT_DISPLAY)

        val wct = controller.handleRequest(Binder(), createTransition(freeformTask))
        assertThat(wct?.changes[freeformTask.token.asBinder()]?.windowingMode)
            .isEqualTo(WINDOWING_MODE_FULLSCREEN)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_PREVENT_DESKTOP_IN_LOCKTASKMODE_BUGFIX)
    fun handleRequest_freeformTask_inLockTaskMode_moveToFullscreen() {
        val deskId = 0
        taskRepository.setDeskInactive(deskId)
        whenever(desktopWallpaperActivityTokenProvider.getToken()).thenReturn(null)
        val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
        tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM
        whenever(lockTaskChangeListener.isTaskLocked).thenReturn(true)

        val freeformTask = createFreeformTask(displayId = DEFAULT_DISPLAY)

        val wct = controller.handleRequest(Binder(), createTransition(freeformTask))
        assertThat(wct?.changes[freeformTask.token.asBinder()]?.windowingMode)
            .isEqualTo(WINDOWING_MODE_FULLSCREEN)
    }

    @Test
    fun handleRequest_notOpenOrToFrontTransition_returnNull() {
        val task =
            TestRunningTaskInfoBuilder()
                .setActivityType(ACTIVITY_TYPE_STANDARD)
                .setWindowingMode(WINDOWING_MODE_FULLSCREEN)
                .build()
        val transition = createTransition(task = task, type = TRANSIT_CLOSE)
        val result = controller.handleRequest(Binder(), transition)
        assertThat(result).isNull()
    }

    @Test
    fun handleRequest_noTriggerTask_returnNull() {
        assertThat(controller.handleRequest(Binder(), createTransition(task = null))).isNull()
    }

    @Test
    fun handleRequest_triggerTaskNotStandard_returnNull() {
        val task = TestRunningTaskInfoBuilder().setActivityType(ACTIVITY_TYPE_HOME).build()
        assertThat(controller.handleRequest(Binder(), createTransition(task))).isNull()
    }

    @Test
    fun handleRequest_triggerTaskNotFullscreenOrFreeform_returnNull() {
        val task =
            TestRunningTaskInfoBuilder()
                .setActivityType(ACTIVITY_TYPE_STANDARD)
                .setWindowingMode(WINDOWING_MODE_MULTI_WINDOW)
                .build()
        assertThat(controller.handleRequest(Binder(), createTransition(task))).isNull()
    }

    @Test
    fun handleRequest_recentsAnimationRunning_returnNull() {
        // Set up a visible freeform task so a fullscreen task should be converted to freeform
        val freeformTask = setUpFreeformTask()
        markTaskVisible(freeformTask)

        // Mark recents animation running
        recentsTransitionStateListener.onTransitionStateChanged(
            TRANSITION_STATE_ANIMATING,
            DEFAULT_DISPLAY,
        )

        // Open a fullscreen task, check that it does not result in a WCT with changes to it
        val fullscreenTask = createFullscreenTask()
        assertThat(controller.handleRequest(Binder(), createTransition(fullscreenTask))).isNull()
    }

    @Test
    fun onRecentsInDesktopAnimationFinishing_launchOccurred_skipsDeactivation() {
        val activeDeskId = 1
        recentsTransitionStateListener.onTransitionStateChanged(
            TRANSITION_STATE_ANIMATING,
            DEFAULT_DISPLAY,
        )

        val task = setUpFreeformTask()
        val request = createTransition(task, type = TRANSIT_OPEN)
        controller.handleRequest(Binder(), request)

        controller.onRecentsInDesktopAnimationFinishing(
            transition = Binder(),
            finishWct = WindowContainerTransaction(),
            returnToApp = false,
            activeDeskIdOnRecentsStart = activeDeskId,
        )

        verify(desksOrganizer, never()).deactivateDesk(any(), eq(activeDeskId), anyBoolean())
    }

    @Test
    fun handleRequest_recentsAnimationRunning_relaunchActiveTask_tracksDeskDeactivation() {
        // Set up a visible freeform task
        val freeformTask = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = 0)
        markTaskVisible(freeformTask)

        // Mark recents animation running
        recentsTransitionStateListener.onTransitionStateChanged(
            TRANSITION_STATE_ANIMATING,
            DEFAULT_DISPLAY,
        )

        val transition = Binder()
        controller.handleRequest(transition, createTransition(freeformTask))

        desksTransitionsObserver.addPendingTransition(
            DeskTransition.DeactivateDesk(
                transition,
                userId = taskRepository.userId,
                deskId = 0,
                displayId = DEFAULT_DISPLAY,
                switchingUser = false,
                exitReason = ExitReason.UNKNOWN_EXIT,
            )
        )
    }

    @Test
    fun handleRequest_topActivityTransparentWithoutDisplay_multiDesksEnabled_returnSwitchToFreeformWCT() {
        val deskId = 0
        val freeformTask = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = deskId)
        markTaskVisible(freeformTask)

        val task =
            createFullscreenTask().apply {
                isActivityStackTransparent = true
                isTopActivityNoDisplay = true
                numActivities = 1
            }

        val wct = controller.handleRequest(Binder(), createTransition(task))

        assertNotNull(wct)
        verify(desksOrganizer).moveTaskToDesk(wct, deskId, task)
    }

    @Test
    fun handleRequest_exemptFromDesktopFreeformTask_notInDesktop_returnSwitchToFullscreenWCT() {
        taskRepository.setDeskInactive(deskId = 0)
        val tda =
            DisplayAreaInfo(MockToken().token(), DEFAULT_DISPLAY, /* featureId= */ 0).apply {
                configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM
            }
        whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)).thenReturn(tda)
        val freeformExemptTask =
            createFreeformTask(displayId = DEFAULT_DISPLAY).apply {
                baseActivity =
                    ComponentName(
                        context.resources.getString(com.android.internal.R.string.config_systemUi),
                        /* cls= */ "",
                    )
            }

        val wct = controller.handleRequest(Binder(), createTransition(freeformExemptTask))

        assertNotNull(wct, "Should handle request")
        val mode =
            assertNotNull(
                wct.changes[freeformExemptTask.token.asBinder()]?.windowingMode,
                "Should have change for freeform task",
            )
        assertThat(mode).isEqualTo(WINDOWING_MODE_FULLSCREEN)
    }

    @Test
    fun handleRequest_topActivityTransparentWithSignature_returnSwitchToFullscreenWCT() {
        val freeformTask = setUpFreeformTask()
        markTaskVisible(freeformTask)

        val task =
            setUpFreeformTask().apply {
                isActivityStackTransparent = true
                isTopActivityNoDisplay = false
                numActivities = 1
                topActivityInfo =
                    ActivityInfo().apply {
                        applicationInfo =
                            ApplicationInfo().apply {
                                privateFlags = ApplicationInfo.PRIVATE_FLAG_SIGNED_WITH_PLATFORM_KEY
                            }
                    }
                baseActivity = ComponentName(/* pkg= */ "", /* cls= */ "")
            }

        val result = controller.handleRequest(Binder(), createTransition(task))
        assertThat(result?.changes?.get(task.token.asBinder())?.windowingMode)
            .isEqualTo(WINDOWING_MODE_UNDEFINED) // inherited FULLSCREEN
    }

    @Test
    fun handleRequest_topActivityTransparentWithPermission_returnSwitchToFullscreenWCT() {
        val freeformTask = setUpFreeformTask()
        markTaskVisible(freeformTask)

        val packageInfo = org.mockito.kotlin.mock<PackageInfo>()
        packageInfo.requestedPermissions = arrayOf(SYSTEM_ALERT_WINDOW)
        packageInfo.requestedPermissionsFlags = intArrayOf(PackageInfo.REQUESTED_PERMISSION_GRANTED)
        whenever(
                packageManager.getPackageInfoAsUser(
                    anyString(),
                    eq(PackageManager.GET_PERMISSIONS),
                    anyInt(),
                )
            )
            .thenReturn(packageInfo)
        val task =
            setUpFreeformTask().apply {
                isActivityStackTransparent = true
                isTopActivityNoDisplay = false
                numActivities = 1
                baseActivity = ComponentName(/* pkg= */ "", /* cls= */ "")
            }

        val result = controller.handleRequest(Binder(), createTransition(task))
        assertThat(result?.changes?.get(task.token.asBinder())?.windowingMode)
            .isEqualTo(WINDOWING_MODE_UNDEFINED) // inherited FULLSCREEN
    }

    @Test
    fun handleRequest_topActivityTransparent_savedToDesktopRepository() {
        val freeformTask = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        markTaskVisible(freeformTask)

        val topTransparentTask =
            setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = DEFAULT_DISPLAY).apply {
                isActivityStackTransparent = true
                isTopActivityNoDisplay = false
                numActivities = 1
                baseActivity = ComponentName(/* pkg= */ "", /* cls= */ "")
                topActivityInfo =
                    ActivityInfo().apply {
                        applicationInfo =
                            ApplicationInfo().apply {
                                privateFlags = ApplicationInfo.PRIVATE_FLAG_SIGNED_WITH_PLATFORM_KEY
                            }
                    }
            }

        val topTransparentTaskData =
            TopTransparentFullscreenTaskData(topTransparentTask.taskId, topTransparentTask.token)
        controller.handleRequest(Binder(), createTransition(topTransparentTask))
        assertThat(taskRepository.getTopTransparentFullscreenTaskData(DEFAULT_DISPLAY))
            .isEqualTo(topTransparentTaskData)
    }

    @Test
    fun handleRequest_newTaskLaunch_topTransparentFullscreenTaskIdPassedToClear() {
        val transition = Binder()
        val topTransparentTask = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)
        taskRepository.setTopTransparentFullscreenTaskData(DEFAULT_DISPLAY, topTransparentTask)

        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        controller.handleRequest(transition, createTransition(task))

        verify(desktopMixedTransitionHandler)
            .addPendingMixedTransition(
                DesktopMixedTransitionHandler.PendingMixedTransition.Launch(
                    transition,
                    task.taskId,
                    topTransparentTask.taskId,
                    exitingImmersiveTask = null,
                )
            )
    }

    @Test
    fun handleRequest_newTaskLaunch_sameAsTopTransparentFullscreenTask_clearsTopTransparent() {
        val transition = Binder()
        val topTransparentTask = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)
        taskRepository.setTopTransparentFullscreenTaskData(DEFAULT_DISPLAY, topTransparentTask)

        val task =
            setUpFreeformTask(displayId = DEFAULT_DISPLAY, taskId = topTransparentTask.taskId)
        controller.handleRequest(transition, createTransition(task))

        assertThat(taskRepository.getTopTransparentFullscreenTaskData(DEFAULT_DISPLAY)).isNull()
    }

    @Test
    fun handleRequest_desktopNotShowing_topTransparentFullscreenTask_returnNull() {
        taskRepository.setDeskInactive(deskId = 0)
        val task = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)

        assertThat(controller.handleRequest(Binder(), createTransition(task))).isNull()
    }

    @Test
    fun handleRequest_systemUIActivityWithDisplay_returnSwitchToFullscreenWCT() {
        val freeformTask = setUpFreeformTask()
        markTaskVisible(freeformTask)

        // Set task as systemUI package
        val systemUIPackageName =
            context.resources.getString(com.android.internal.R.string.config_systemUi)
        val baseComponent = ComponentName(systemUIPackageName, /* cls= */ "")
        val task =
            setUpFreeformTask().apply {
                baseActivity = baseComponent
                isTopActivityNoDisplay = false
            }

        val result = controller.handleRequest(Binder(), createTransition(task))
        assertThat(result?.changes?.get(task.token.asBinder())?.windowingMode)
            .isEqualTo(WINDOWING_MODE_UNDEFINED) // inherited FULLSCREEN
    }

    @Test
    fun handleRequest_systemUIActivityWithDisplayInFreeformTask_inDesktop_notLastTask_tracksDeskDeactivation() {
        rootTaskDisplayAreaOrganizer.setTouchFirst(DEFAULT_DISPLAY)
        val deskId = 5
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = deskId)
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = deskId)
        val systemUIPackageName =
            context.resources.getString(com.android.internal.R.string.config_systemUi)
        val baseComponent = ComponentName(systemUIPackageName, /* cls= */ "")
        setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        val task =
            setUpFreeformTask(displayId = DEFAULT_DISPLAY).apply {
                baseActivity = baseComponent
                isTopActivityNoDisplay = false
            }

        val transition = Binder()
        controller.handleRequest(transition, createTransition(task))

        verify(desksTransitionsObserver)
            .addPendingTransition(
                DeskTransition.DeactivateDesk(
                    transition,
                    userId = taskRepository.userId,
                    deskId = deskId,
                    displayId = DEFAULT_DISPLAY,
                    switchingUser = false,
                    exitReason = ExitReason.FULLSCREEN_LAUNCH,
                )
            )
    }

    @Test
    fun handleRequest_systemUIActivityWithDisplayInFreeformTask_inDesktop_lastTask_tracksDeskRemoval() {
        rootTaskDisplayAreaOrganizer.setTouchFirst(DEFAULT_DISPLAY)
        val deskId = 5
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = deskId)
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = deskId)
        val systemUIPackageName =
            context.resources.getString(com.android.internal.R.string.config_systemUi)
        val baseComponent = ComponentName(systemUIPackageName, /* cls= */ "")
        val task =
            setUpFreeformTask(displayId = DEFAULT_DISPLAY).apply {
                baseActivity = baseComponent
                isTopActivityNoDisplay = false
            }

        val transition = Binder()
        controller.handleRequest(transition, createTransition(task))

        verify(desksTransitionsObserver)
            .addPendingTransition(
                argThat {
                    this is DeskTransition.RemoveDesk &&
                        this.token == transition &&
                        this.deskId == deskId
                }
            )
    }

    @Test
    fun handleRequest_systemUIActivityWithoutDisplay_multiDesksEnabled_movesTaskToDesk() {
        val deskId = 0
        val freeformTask = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = deskId)
        markTaskVisible(freeformTask)

        // Set task as systemUI package
        val systemUIPackageName =
            context.resources.getString(com.android.internal.R.string.config_systemUi)
        val baseComponent = ComponentName(systemUIPackageName, /* cls= */ "")
        val task =
            createFullscreenTask(displayId = DEFAULT_DISPLAY).apply {
                baseActivity = baseComponent
                isTopActivityNoDisplay = true
            }

        val wct = controller.handleRequest(Binder(), createTransition(task))

        assertNotNull(wct)
        verify(desksOrganizer).moveTaskToDesk(wct, deskId, task)
    }

    @Test
    fun handleRequest_defaultHomePackageWithDisplay_returnSwitchToFullscreenWCT() {
        val freeformTask = setUpFreeformTask()
        markTaskVisible(freeformTask)

        val task =
            setUpFullscreenTask().apply {
                baseActivity = homeComponentName
                isTopActivityNoDisplay = false
            }

        val result = controller.handleRequest(Binder(), createTransition(task))
        assertThat(result?.changes?.get(task.token.asBinder())?.windowingMode)
            .isEqualTo(WINDOWING_MODE_UNDEFINED) // inherited FULLSCREEN
    }

    @Test
    fun handleRequest_defaultHomePackageWithoutDisplay_moveToDesk() {
        val freeformTask = setUpFreeformTask()
        markTaskVisible(freeformTask)

        val task =
            setUpFullscreenTask().apply {
                baseActivity = homeComponentName
                isTopActivityNoDisplay = true
            }

        val result = controller.handleRequest(Binder(), createTransition(task))

        verify(desksOrganizer).moveTaskToDesk(result!!, DEFAULT_DISPLAY, task)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    fun handleRequest_backTransition_singleTaskNoToken_noWallpaper_doesNotHandle() {
        val task = setUpFreeformTask()

        val result =
            controller.handleRequest(Binder(), createTransition(task, type = TRANSIT_TO_BACK))

        assertNull(result, "Should not handle request")
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY,
    )
    fun handleRequest_backTransition_singleTaskNoToken_withWallpaper_noChanges() {
        val task = setUpFreeformTask()

        val result =
            controller.handleRequest(Binder(), createTransition(task, type = TRANSIT_TO_BACK))

        // Should not have any change
        result?.run { assertTrue(changes.isEmpty(), "Should not have changes") }
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    fun handleRequest_backTransition_singleTaskNoToken_withWallpaper_notInDesktop_doesNotHandle() {
        val task = setUpFreeformTask()
        markTaskHidden(task)

        val result =
            controller.handleRequest(Binder(), createTransition(task, type = TRANSIT_TO_BACK))

        assertNull(result, "Should not handle request")
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY,
    )
    fun handleRequest_backTransition_singleTaskNoToken_noChanges() {
        val task = setUpFreeformTask()

        val result =
            controller.handleRequest(Binder(), createTransition(task, type = TRANSIT_TO_BACK))

        // Should not have any change
        result?.run { assertTrue(changes.isEmpty(), "Should not have changes") }
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    fun handleRequest_backTransition_singleTaskWithToken_noWallpaper_doesNotHandle() {
        val task = setUpFreeformTask()

        val result =
            controller.handleRequest(Binder(), createTransition(task, type = TRANSIT_TO_BACK))

        assertNull(result, "Should not handle request")
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER,
    )
    fun handleRequest_backTransition_singleTaskWithToken_noChanges() {
        val task = setUpFreeformTask()

        val result =
            controller.handleRequest(Binder(), createTransition(task, type = TRANSIT_TO_BACK))

        // Should not have any change
        result?.run { assertTrue(changes.isEmpty(), "Should not have changes") }
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    fun handleRequest_backTransition_multipleTasks_noWallpaper_doesNotHandle() {
        val task1 = setUpFreeformTask()
        setUpFreeformTask()

        val result =
            controller.handleRequest(Binder(), createTransition(task1, type = TRANSIT_TO_BACK))

        assertNull(result, "Should not handle request")
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    fun handleRequest_backTransition_multipleTasks_doesNotHandle() {
        val task1 = setUpFreeformTask()
        setUpFreeformTask()

        val result =
            controller.handleRequest(Binder(), createTransition(task1, type = TRANSIT_TO_BACK))

        assertNull(result, "Should not handle request")
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION,
        Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER,
    )
    fun handleRequest_backTransition_multipleTasksSingleNonClosing_noChanges() {
        val task1 = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        val task2 = setUpFreeformTask(displayId = DEFAULT_DISPLAY)

        taskRepository.addClosingTask(
            displayId = DEFAULT_DISPLAY,
            deskId = 0,
            taskId = task2.taskId,
        )
        val result =
            controller.handleRequest(Binder(), createTransition(task1, type = TRANSIT_TO_BACK))

        // Should not have any change
        result?.run { assertTrue(changes.isEmpty(), "Should not have changes") }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER,
    )
    fun handleRequest_backTransition_multipleTasksSingleNonMinimized_noChanges() {
        val task1 = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        val task2 = setUpFreeformTask(displayId = DEFAULT_DISPLAY)

        taskRepository.minimizeTask(displayId = DEFAULT_DISPLAY, taskId = task2.taskId)
        val result =
            controller.handleRequest(Binder(), createTransition(task1, type = TRANSIT_TO_BACK))

        // Should not have any change
        result?.run { assertTrue(changes.isEmpty(), "Should not have changes") }
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    fun handleRequest_backTransition_nonMinimizadTask_withWallpaper_removesWallpaper() {
        val task1 = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        val task2 = setUpFreeformTask(displayId = DEFAULT_DISPLAY)

        taskRepository.minimizeTask(displayId = DEFAULT_DISPLAY, taskId = task2.taskId)
        // Task is being minimized so mark it as not visible.
        taskRepository.updateTask(
            displayId = DEFAULT_DISPLAY,
            task2.taskId,
            isVisible = false,
            taskBounds = TASK_BOUNDS,
        )
        val result =
            controller.handleRequest(Binder(), createTransition(task2, type = TRANSIT_TO_BACK))

        assertNull(result, "Should not handle request")
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    fun handleRequest_closeTransition_singleTaskNoToken_noWallpaper_doesNotHandle() {
        val task = setUpFreeformTask()

        val result =
            controller.handleRequest(Binder(), createTransition(task, type = TRANSIT_CLOSE))

        assertNull(result, "Should not handle request")
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY,
    )
    fun handleRequest_closeTransition_singleTaskNoToken_noChanges() {
        val task = setUpFreeformTask()
        whenever(desktopWallpaperActivityTokenProvider.getToken()).thenReturn(null)

        val result =
            controller.handleRequest(Binder(), createTransition(task, type = TRANSIT_CLOSE))

        // Should not have any change
        result?.run { assertTrue(changes.isEmpty(), "Should not have changes") }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY,
    )
    fun handleRequest_closeTransition_singleTaskNoToken_secondaryDisplay_noChanges() {
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        val task = setUpFreeformTask(displayId = SECOND_DISPLAY)
        whenever(desktopWallpaperActivityTokenProvider.getToken()).thenReturn(null)

        val result =
            controller.handleRequest(Binder(), createTransition(task, type = TRANSIT_CLOSE))

        // Should not have any change
        result?.run { assertTrue(changes.isEmpty(), "Should not have changes") }
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    fun handleRequest_closeTransition_singleTaskWithToken_noWallpaper_doesNotHandle() {
        val task = setUpFreeformTask()

        val result =
            controller.handleRequest(Binder(), createTransition(task, type = TRANSIT_CLOSE))

        assertNull(result, "Should not handle request")
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER,
    )
    fun handleRequest_closeTransition_singleTaskWithToken_withWallpaper_noChanges() {
        val task = setUpFreeformTask()

        val result =
            controller.handleRequest(Binder(), createTransition(task, type = TRANSIT_CLOSE))

        // Should not have any change
        result?.run { assertTrue(changes.isEmpty(), "Should not have changes") }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER,
    )
    fun handleRequest_closeTransition_onlyDesktopTask_dontDeactivateDesk() {
        val task = setUpFreeformTask()

        controller.handleRequest(Binder(), createTransition(task, type = TRANSIT_CLOSE))

        verify(desksOrganizer, never())
            .deactivateDesk(wct = any(), deskId = any(), skipReorder = any())
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER,
    )
    fun handleRequest_closeTransition_onlyDesktopTask_dontAddDeactivatesDeskTransition() {
        val transition = Binder()
        val task = setUpFreeformTask()

        controller.handleRequest(transition, createTransition(task, type = TRANSIT_CLOSE))

        verifyNoInteractions(desksTransitionsObserver)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    fun handleRequest_closeTransition_multipleTasks_noWallpaper_doesNotHandle() {
        val task1 = setUpFreeformTask()
        setUpFreeformTask()

        val result =
            controller.handleRequest(Binder(), createTransition(task1, type = TRANSIT_CLOSE))

        assertNull(result, "Should not handle request")
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
    fun handleRequest_closeTransition_multipleTasksFlagEnabled_doesNotHandle() {
        val task1 = setUpFreeformTask()
        setUpFreeformTask()

        val result =
            controller.handleRequest(Binder(), createTransition(task1, type = TRANSIT_CLOSE))

        assertNull(result, "Should not handle request")
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER,
    )
    fun handleRequest_closeTransition_multipleTasksSingleNonClosing_noChanges() {
        val task1 = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        val task2 = setUpFreeformTask(displayId = DEFAULT_DISPLAY)

        taskRepository.addClosingTask(
            displayId = DEFAULT_DISPLAY,
            deskId = 0,
            taskId = task2.taskId,
        )
        val result =
            controller.handleRequest(Binder(), createTransition(task1, type = TRANSIT_CLOSE))

        // Should not have any change
        result?.run { assertTrue(changes.isEmpty(), "Should not have changes") }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER,
    )
    fun handleRequest_closeTransition_multipleTasksSingleNonMinimized_noChanges() {
        val task1 = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        val task2 = setUpFreeformTask(displayId = DEFAULT_DISPLAY)

        taskRepository.minimizeTask(displayId = DEFAULT_DISPLAY, taskId = task2.taskId)
        val result =
            controller.handleRequest(Binder(), createTransition(task1, type = TRANSIT_CLOSE))

        // Should not have any change
        result?.run { assertTrue(changes.isEmpty(), "Should not have changes") }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY,
    )
    fun handleRequest_toBackTransition_noActiveDesk_notHandled() {
        taskRepository.setDeskInactive(deskId = 0)
        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY)

        val result =
            controller.handleRequest(Binder(), createTransition(task, type = TRANSIT_TO_BACK))

        assertNull(result, "Should not handle request")
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY,
    )
    fun handleRequest_toBackTransition_minimizesTask_noChanges() {
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = 0)

        val result =
            controller.handleRequest(Binder(), createTransition(task, type = TRANSIT_TO_BACK))

        // Should not have any change
        result?.run { assertTrue(changes.isEmpty(), "Should not have changes") }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY,
    )
    fun handleRequest_toBackTransition_lastTask_noChanges() {
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = 0)

        val transition = Binder()
        val result =
            controller.handleRequest(transition, createTransition(task, type = TRANSIT_TO_BACK))

        // Should not have any change
        result?.run { assertTrue(changes.isEmpty(), "Should not have changes") }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY,
    )
    fun handleRequest_toBackTransition_notLastTask_doesNotDeactivateDesk() {
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = 0)
        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = 0)

        val transition = Binder()
        controller.handleRequest(transition, createTransition(task, type = TRANSIT_TO_BACK))

        verify(desksOrganizer, never())
            .deactivateDesk(any(), deskId = eq(0), skipReorder = eq(false))
        verify(desksTransitionsObserver, never())
            .addPendingTransition(
                argThat {
                    this is DeskTransition.RemoveDesk &&
                        this.token == transition &&
                        this.deskId == 0
                }
            )
    }

    @Test
    fun handleRequest_lockTaskMode_freeformTask_movesTaskToFullscreen() {
        val freeformTask = setUpFreeformTask()
        val homeTask = setUpHomeTask()
        val transition = createTransition(task = freeformTask, type = TRANSIT_START_LOCK_TASK_MODE)

        val wct = controller.handleRequest(Binder(), transition)

        assertNotNull(wct) { "Should handle request" }

        // Check for moving to fullscreen
        assertThat(wct.changes[freeformTask.token.asBinder()]?.windowingMode)
            .isEqualTo(WINDOWING_MODE_UNDEFINED)
        assertThat(findBoundsChange(wct, freeformTask)).isEqualTo(Rect())
        // Check for home task reorder
        wct.assertReorder(homeTask, toTop = true)
        // Check for task reorder
        wct.assertReorder(freeformTask, toTop = true)
    }

    @Test
    fun handleRequest_freeformTask_displayDoesntHandleDesktop_returnNull() {
        desktopState.overrideDesktopModeSupportPerDisplay[SECOND_DISPLAY] = false
        val task1 = createFreeformTask(displayId = SECOND_DISPLAY)

        val result =
            controller.handleRequest(Binder(), createTransition(task1, type = TRANSIT_OPEN))

        assertNull(result, "Should not handle request")
    }

    @Test
    fun handleRequest_fullscreenTaskRelaunch_desktopFirst_returnNull() {
        val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
        tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM
        val task = setUpFullscreenTask()
        // Deactivate desk as fullscreen task is visible on top.
        taskRepository.getActiveDeskId(DEFAULT_DISPLAY)?.let { taskRepository.setDeskInactive(it) }

        val result = controller.handleRequest(Binder(), createTransition(task, type = TRANSIT_OPEN))

        assertNull(result, "Should not handle request")
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_FIRST_BASED_DEFAULT_TO_DESKTOP_BUGFIX)
    fun handleRequest_backgroundFullscreenTaskRelaunch_desktopFirst_returnNull() {
        val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
        tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM
        val task = setUpFullscreenTask(visible = false)

        val result = controller.handleRequest(Binder(), createTransition(task, type = TRANSIT_OPEN))

        assertNull(result, "Should not handle request")
    }

    @Test
    fun handleRequest_fullscreenTaskRelaunch_touchFirst_returnNull() {
        val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
        tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
        val task = setUpFullscreenTask()
        // Deactivate desk as fullscreen task is visible on top.
        taskRepository.getActiveDeskId(DEFAULT_DISPLAY)?.let { taskRepository.setDeskInactive(it) }

        val result = controller.handleRequest(Binder(), createTransition(task, type = TRANSIT_OPEN))

        assertNull(result, "Should not handle request")
    }

    @Test
    fun handleRequest_backgroundFullscreenTaskRelaunch_touchFirst_moveToDesk() {
        val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
        tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
        val task = setUpFullscreenTask(visible = false)

        val result = controller.handleRequest(Binder(), createTransition(task, type = TRANSIT_OPEN))

        verify(desksOrganizer).moveTaskToDesk(result!!, DEFAULT_DISPLAY, task)
    }

    @Test
    fun moveFocusedTaskToDesktop_noDisplayActivity_doesNothing() {
        val task = setUpFullscreenTask().apply { isTopActivityNoDisplay = true }

        controller.moveFocusedTaskToDesktop(task.taskId, transitionSource = UNKNOWN)
        verifyEnterDesktopWCTNotExecuted()
    }

    @Test
    fun moveFocusedTaskToDesktop_transparentTask_doesNothing() {
        val task =
            setUpFullscreenTask().apply {
                isActivityStackTransparent = true
                numActivities = 1
            }

        controller.moveFocusedTaskToDesktop(task.taskId, transitionSource = UNKNOWN)
        verifyEnterDesktopWCTNotExecuted()
    }

    @Test
    fun moveFocusedTaskToDesktop_systemUIActivity_doesNothing() {
        // Set task as systemUI package
        val systemUIPackageName =
            context.resources.getString(com.android.internal.R.string.config_systemUi)
        val baseComponent = ComponentName(systemUIPackageName, /* cls= */ "")
        val task = setUpFullscreenTask().apply { baseActivity = baseComponent }

        controller.moveFocusedTaskToDesktop(task.taskId, transitionSource = UNKNOWN)
        verifyEnterDesktopWCTNotExecuted()
    }

    @Test
    fun moveFocusedTaskToDesktop_defaultHomePackage_doesNothing() {
        val task = setUpFullscreenTask().apply { baseActivity = homeComponentName }

        controller.moveFocusedTaskToDesktop(task.taskId, transitionSource = UNKNOWN)
        verifyEnterDesktopWCTNotExecuted()
    }

    @Test
    fun moveFocusedTaskToDesktop_fullscreenTaskIsMovedToDesktop_multiDesksEnabled() =
        testScope.runTest {
            val task1 = setUpFullscreenTask()
            val task2 = setUpFullscreenTask()
            val task3 = setUpFullscreenTask()

            task1.isFocused = true
            task2.isFocused = false
            task3.isFocused = false

            controller.moveFocusedTaskToDesktop(DEFAULT_DISPLAY, transitionSource = UNKNOWN)
            runCurrent()

            val wct = getLatestEnterDesktopWct()
            verify(desksOrganizer).moveTaskToDesk(wct, deskId = 0, task1)
        }

    @Test
    fun moveFocusedTaskToDesktop_splitScreenTaskIsMovedToDesktop_multiDesksEnabled() =
        testScope.runTest {
            val task1 = setUpSplitScreenTask()
            val task2 = setUpFullscreenTask()
            val task3 = setUpFullscreenTask()
            val task4 = setUpSplitScreenTask()

            task1.isFocused = true
            task2.isFocused = false
            task3.isFocused = false
            task4.isFocused = true

            task4.parentTaskId = task1.taskId

            controller.moveFocusedTaskToDesktop(DEFAULT_DISPLAY, transitionSource = UNKNOWN)
            runCurrent()

            val wct = getLatestEnterDesktopWct()
            verify(desksOrganizer).moveTaskToDesk(wct, deskId = 0, task4)
            verify(splitScreenController)
                .prepareExitSplitScreen(
                    any(),
                    anyInt(),
                    eq(SplitScreenController.EXIT_REASON_DESKTOP_MODE),
                )
        }

    @Test
    fun moveFocusedTaskToFullscreen() {
        val task1 = setUpFreeformTask()
        val task2 = setUpFreeformTask()
        val task3 = setUpFreeformTask()

        task1.isFocused = false
        task2.isFocused = true
        task3.isFocused = false

        controller.enterFullscreen(DEFAULT_DISPLAY, transitionSource = UNKNOWN)

        val wct = getLatestExitDesktopWct()
        assertThat(wct.changes[task2.token.asBinder()]?.windowingMode)
            .isEqualTo(WINDOWING_MODE_UNDEFINED) // inherited FULLSCREEN
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER)
    fun moveFocusedTaskToFullscreen_onlyVisibleNonMinimizedTask_removesWallpaperActivity() {
        val task1 = setUpFreeformTask()
        val task2 = setUpFreeformTask()
        val task3 = setUpFreeformTask()

        task1.isFocused = false
        task2.isFocused = true
        task3.isFocused = false
        taskRepository.minimizeTask(DEFAULT_DISPLAY, task1.taskId)
        taskRepository.updateTask(DEFAULT_DISPLAY, task3.taskId, isVisible = false, TASK_BOUNDS)

        controller.enterFullscreen(DEFAULT_DISPLAY, transitionSource = UNKNOWN)

        val wct = getLatestExitDesktopWct()
        val taskChange = assertNotNull(wct.changes[task2.token.asBinder()])
        assertThat(taskChange.windowingMode)
            .isEqualTo(WINDOWING_MODE_UNDEFINED) // inherited FULLSCREEN
        wct.assertReorder(wallpaperToken, toTop = false)
    }

    @Test
    fun moveFocusedTaskToFullscreen_multipleVisibleTasks_fullscreenOverHome_multiDesksEnabled() {
        val homeTask = setUpHomeTask()
        val task1 = setUpFreeformTask()
        val task2 = setUpFreeformTask()
        val task3 = setUpFreeformTask()

        task1.isFocused = false
        task2.isFocused = true
        task3.isFocused = false
        controller.enterFullscreen(DEFAULT_DISPLAY, transitionSource = UNKNOWN)

        val wct = getLatestExitDesktopWct()
        val taskChange = assertNotNull(wct.changes[task2.token.asBinder()])
        assertThat(taskChange.windowingMode)
            .isEqualTo(WINDOWING_MODE_UNDEFINED) // inherited FULLSCREEN
        // Moves home task behind the fullscreen task
        val homeReorderIndex = wct.indexOfReorder(homeTask, toTop = true)
        val fullscreenReorderIndex = wct.indexOfReorder(task2, toTop = true)
        assertThat(homeReorderIndex).isNotEqualTo(-1)
        assertThat(fullscreenReorderIndex).isNotEqualTo(-1)
        assertThat(fullscreenReorderIndex).isGreaterThan(homeReorderIndex)
    }

    @Test
    fun toggleFocusedTaskFullscreenState_desktopTaskIsMovedToFullscreen() {
        val task1 = setUpFreeformTask()
        val task2 = setUpFreeformTask()
        val task3 = setUpFreeformTask()

        task1.isFocused = false
        task2.isFocused = true
        task3.isFocused = false

        controller.toggleFocusedTaskFullscreenState(DEFAULT_DISPLAY, transitionSource = UNKNOWN)

        val wct = getLatestExitDesktopWct()
        assertThat(wct.changes[task2.token.asBinder()]?.windowingMode)
            .isEqualTo(WINDOWING_MODE_UNDEFINED) // inherited FULLSCREEN
    }

    @Test
    fun toggleFocusedTaskFullscreenState_fullscreenTaskIsMovedToDesktop_multiDesksEnabled() =
        testScope.runTest {
            val task1 = setUpFullscreenTask()
            val task2 = setUpFullscreenTask()
            val task3 = setUpFullscreenTask()

            task1.isFocused = true
            task2.isFocused = false
            task3.isFocused = false

            controller.toggleFocusedTaskFullscreenState(DEFAULT_DISPLAY, transitionSource = UNKNOWN)
            runCurrent()

            val wct = getLatestEnterDesktopWct()
            verify(desksOrganizer).moveTaskToDesk(wct, deskId = 0, task1)
        }

    @Test
    fun toggleFocusedTaskFullscreenState_splitScreenTaskIsMovedToFullscreen_multiDesksEnabled() =
        testScope.runTest {
            val task1 = setUpSplitScreenTask()
            val task2 = setUpFullscreenTask()
            val task3 = setUpFullscreenTask()
            val task4 = setUpSplitScreenTask()

            task1.isFocused = true
            task2.isFocused = false
            task3.isFocused = false
            task4.isFocused = true
            task4.parentTaskId = task1.taskId

            controller.toggleFocusedTaskFullscreenState(DEFAULT_DISPLAY, transitionSource = UNKNOWN)
            runCurrent()

            verifyEnterDesktopWCTNotExecuted()
            verify(splitScreenController).goToFullscreenFromSplit()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    fun removeDesk_multipleDesks_addsPendingTransition() {
        val transition = Binder()
        whenever(transitions.startTransition(eq(TRANSIT_CLOSE), any(), anyOrNull()))
            .thenReturn(transition)
        taskRepository.addDesk(DEFAULT_DISPLAY, deskId = 2)

        controller.removeDesk(deskId = 2, exitReason = ExitReason.UNKNOWN_EXIT)

        verify(desksOrganizer).removeDesk(any(), eq(2), any())
        verify(desksTransitionsObserver)
            .addPendingTransition(
                argThat {
                    this is DeskTransition.RemoveDesk &&
                        this.token == transition &&
                        this.deskId == 2
                }
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    fun removeDesk_multipleDesks_removesRunningTasks() {
        val transition = Binder()
        whenever(transitions.startTransition(eq(TRANSIT_CLOSE), any(), anyOrNull()))
            .thenReturn(transition)
        taskRepository.addDesk(DEFAULT_DISPLAY, deskId = 2)
        val task1 = setUpFreeformTask(deskId = 2)
        val task2 = setUpFreeformTask(deskId = 2)
        val task3 = setUpFreeformTask(deskId = 2)

        controller.removeDesk(deskId = 2, exitReason = ExitReason.UNKNOWN_EXIT)

        val wct = getLatestWct(TRANSIT_CLOSE)
        wct.assertRemove(task1.token)
        wct.assertRemove(task2.token)
        wct.assertRemove(task3.token)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    fun removeDesk_multipleDesks_removesRecentTasks() {
        val transition = Binder()
        whenever(transitions.startTransition(eq(TRANSIT_CLOSE), any(), anyOrNull()))
            .thenReturn(transition)
        taskRepository.addDesk(DEFAULT_DISPLAY, deskId = 2)
        val task1 = setUpFreeformTask(deskId = 2, background = true)
        val task2 = setUpFreeformTask(deskId = 2, background = true)
        val task3 = setUpFreeformTask(deskId = 2, background = true)

        controller.removeDesk(deskId = 2, exitReason = ExitReason.UNKNOWN_EXIT)

        verify(recentTasksController).removeBackgroundTask(task1.taskId)
        verify(recentTasksController).removeBackgroundTask(task2.taskId)
        verify(recentTasksController).removeBackgroundTask(task3.taskId)
    }

    @Test
    fun removeDesk_withRemoveTasksFalse_doesNotRemoveTasks() {
        val transition = Binder()
        whenever(transitions.startTransition(eq(TRANSIT_CLOSE), any(), anyOrNull()))
            .thenReturn(transition)
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 2)
        val task1 = setUpFreeformTask(deskId = 2, background = true)
        val task2 = setUpFreeformTask(deskId = 2, background = false)

        controller.removeDesk(deskId = 2, exitReason = ExitReason.UNKNOWN_EXIT, removeTasks = false)

        verify(recentTasksController, never()).removeBackgroundTask(task1.taskId)
        val wct = getLatestWct(TRANSIT_CLOSE)
        assertThat(
                wct.hierarchyOps.any { hop ->
                    hop.container == task2.token.asBinder() &&
                        hop.type == HIERARCHY_OP_TYPE_REMOVE_TASK
                }
            )
            .isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    fun removeAllDesks_multipleDesks_removesAllInSingleTransition() {
        val transition = Binder()
        whenever(transitions.startTransition(eq(TRANSIT_CLOSE), any(), anyOrNull()))
            .thenReturn(transition)
        taskRepository.addDesk(DEFAULT_DISPLAY, deskId = 3)
        taskRepository.addDesk(DEFAULT_DISPLAY, deskId = 4)
        taskRepository.addDesk(DEFAULT_DISPLAY, deskId = 5)

        controller.removeAllDesks(
            userId = taskRepository.userId,
            exitReason = ExitReason.UNKNOWN_EXIT,
        )

        verify(transitions).startTransition(eq(TRANSIT_CLOSE), any(), anyOrNull())
        verify(desksTransitionsObserver)
            .addPendingTransition(
                argThat {
                    this is DeskTransition.RemoveDesk &&
                        this.token == transition &&
                        this.deskId == 3
                }
            )
        verify(desksTransitionsObserver)
            .addPendingTransition(
                argThat {
                    this is DeskTransition.RemoveDesk &&
                        this.token == transition &&
                        this.deskId == 4
                }
            )
        verify(desksTransitionsObserver)
            .addPendingTransition(
                argThat {
                    this is DeskTransition.RemoveDesk &&
                        this.token == transition &&
                        this.deskId == 5
                }
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    fun removeAllDesks_endUpAtHome_hasActiveDesk_deskRemovedAndHomeBroughtToFront() {
        val transition = Binder()
        whenever(transitions.startTransition(eq(TRANSIT_CLOSE), any(), anyOrNull()))
            .thenReturn(transition)
        taskRepository.addDesk(DEFAULT_DISPLAY, deskId = 4)
        taskRepository.addDesk(DEFAULT_DISPLAY, deskId = 5)
        taskRepository.setActiveDesk(DEFAULT_DISPLAY, deskId = 5)

        controller.removeAllDesks(
            userId = taskRepository.userId,
            exitReason = ExitReason.UNKNOWN_EXIT,
            shouldEndUpAtHome = true,
        )

        verify(desksTransitionsObserver)
            .addPendingTransition(
                argThat {
                    this is DeskTransition.RemoveDesk &&
                        this.token == transition &&
                        this.deskId == 4
                }
            )
        verify(desksTransitionsObserver)
            .addPendingTransition(
                argThat {
                    this is DeskTransition.RemoveDesk &&
                        this.token == transition &&
                        this.deskId == 5
                }
            )
        val wct = getLatestWct(type = TRANSIT_CLOSE)
        wct.assertPendingIntent(launchHomeIntent(DEFAULT_DISPLAY))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    fun activateDesk_multipleDesks_addsPendingTransition() {
        val deskId = 0
        val transition = Binder()
        val deskChange = mock(TransitionInfo.Change::class.java)
        whenever(transitions.startTransition(eq(TRANSIT_TO_FRONT), any(), anyOrNull()))
            .thenReturn(transition)
        whenever(desksOrganizer.isDeskActiveAtEnd(deskChange, deskId)).thenReturn(true)
        // Make desk inactive by activating another desk.
        taskRepository.addDesk(DEFAULT_DISPLAY, deskId = 1)
        taskRepository.setActiveDesk(DEFAULT_DISPLAY, deskId = 1)

        controller.activateDesk(
            deskId = deskId,
            userId = taskRepository.userId,
            remoteTransition = RemoteTransition(TestRemoteTransition()),
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        verify(desksTransitionsObserver)
            .addPendingTransition(
                argThat {
                    this is DeskTransition.ActivateDesk &&
                        this.token == transition &&
                        this.deskId == 0
                }
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    fun activateDesk_hasNonRunningTask_startsTask() {
        val deskId = 0
        val nonRunningTask =
            setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = 0, background = true)

        val transition = Binder()
        val deskChange = mock(TransitionInfo.Change::class.java)
        whenever(transitions.startTransition(eq(TRANSIT_TO_FRONT), any(), anyOrNull()))
            .thenReturn(transition)
        whenever(desksOrganizer.isDeskActiveAtEnd(deskChange, deskId)).thenReturn(true)
        // Make desk inactive by activating another desk.
        taskRepository.addDesk(DEFAULT_DISPLAY, deskId = 1)
        taskRepository.setActiveDesk(DEFAULT_DISPLAY, deskId = 1)

        controller.activateDesk(
            deskId = deskId,
            userId = taskRepository.userId,
            remoteTransition = RemoteTransition(TestRemoteTransition()),
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        val wct = getLatestWct(TRANSIT_TO_FRONT, OneShotRemoteHandler::class.java)
        assertNotNull(wct)
        verify(desksOrganizer).addLaunchDeskToActivityOptions(any(), eq(deskId))
        wct.assertLaunchTask(nonRunningTask.taskId, WINDOWING_MODE_FREEFORM)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    fun activateDesk_hasRunningTask_reordersTask() {
        val deskId = 0
        val runningTask = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = 0)

        val transition = Binder()
        val deskChange = mock(TransitionInfo.Change::class.java)
        whenever(transitions.startTransition(eq(TRANSIT_TO_FRONT), any(), anyOrNull()))
            .thenReturn(transition)
        whenever(desksOrganizer.isDeskActiveAtEnd(deskChange, deskId)).thenReturn(true)
        // Make desk inactive by activating another desk.
        taskRepository.addDesk(DEFAULT_DISPLAY, deskId = 1)
        taskRepository.setActiveDesk(DEFAULT_DISPLAY, deskId = 1)

        controller.activateDesk(
            deskId = deskId,
            userId = taskRepository.userId,
            remoteTransition = RemoteTransition(TestRemoteTransition()),
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        verify(desksOrganizer).reorderTaskToFront(any(), eq(deskId), eq(runningTask))
    }

    @Test
    fun activateDesk_otherDeskWasActive_deactivatesOtherDesk() {
        val previouslyActiveDeskId = 1
        val activatingDeskId = 0
        val transition = Binder()
        val deskChange = mock(TransitionInfo.Change::class.java)
        whenever(transitions.startTransition(eq(TRANSIT_TO_FRONT), any(), anyOrNull()))
            .thenReturn(transition)
        whenever(desksOrganizer.isDeskActiveAtEnd(deskChange, activatingDeskId)).thenReturn(true)
        // Make desk inactive by activating another desk.
        taskRepository.addDesk(DEFAULT_DISPLAY, deskId = previouslyActiveDeskId)
        taskRepository.setActiveDesk(DEFAULT_DISPLAY, deskId = previouslyActiveDeskId)

        controller.activateDesk(
            deskId = activatingDeskId,
            userId = taskRepository.userId,
            remoteTransition = RemoteTransition(TestRemoteTransition()),
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        verify(desksOrganizer)
            .deactivateDesk(any(), eq(previouslyActiveDeskId), skipReorder = eq(false))
        verify(desksTransitionsObserver)
            .addPendingTransition(
                argThat {
                    this is DeskTransition.DeactivateDesk &&
                        this.token == transition &&
                        this.deskId == previouslyActiveDeskId
                }
            )
    }

    @Test
    fun activatePreviousDesk_activates() {
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 1)
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 2)
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 2)

        controller.activatePreviousDesk(
            displayId = DEFAULT_DISPLAY,
            userId = DEFAULT_USER_ID,
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        verify(desksOrganizer).activateDesk(any(), eq(1), skipReorder = eq(false))
    }

    @Test
    fun activateNextDesk_activates() {
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 1)
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 2)
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 1)

        controller.activateNextDesk(
            displayId = DEFAULT_DISPLAY,
            userId = DEFAULT_USER_ID,
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        verify(desksOrganizer).activateDesk(any(), eq(2), skipReorder = eq(false))
    }

    @Test
    fun activatePreviousDesk_deskDoesNotExist_doesNotActivate() {
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)

        controller.activatePreviousDesk(
            displayId = DEFAULT_DISPLAY,
            userId = DEFAULT_USER_ID,
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        verify(desksOrganizer, never()).activateDesk(any(), any(), skipReorder = any())
    }

    @Test
    fun activateNextDesk_deskDoesNotExist_doesNotActivate() {
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 1)
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 1)

        controller.activateNextDesk(
            displayId = DEFAULT_DISPLAY,
            userId = DEFAULT_USER_ID,
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        verify(desksOrganizer, never()).activateDesk(any(), any(), skipReorder = any())
    }

    @Test
    fun activatePreviousDesk_noDeskActive_doesNotActivate() {
        taskRepository.setDeskInactive(deskId = 0)

        controller.activatePreviousDesk(
            displayId = DEFAULT_DISPLAY,
            userId = DEFAULT_USER_ID,
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        verify(desksOrganizer, never()).activateDesk(any(), any(), any())
    }

    @Test
    fun activateNextDesk_noDeskActive_doesNotActivate() {
        taskRepository.setDeskInactive(deskId = 0)

        controller.activateNextDesk(
            displayId = DEFAULT_DISPLAY,
            userId = DEFAULT_USER_ID,
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        verify(desksOrganizer, never()).activateDesk(any(), any(), any())
    }

    @Test
    fun activatePreviousDesk_invalidDisplay_activatesFocusedDisplayDesk() {
        val focusedDisplayId = 5
        whenever(focusTransitionObserver.globallyFocusedDisplayId).thenReturn(focusedDisplayId)
        taskRepository.addDesk(displayId = 5, deskId = 1)
        taskRepository.addDesk(displayId = 5, deskId = 2)
        taskRepository.setActiveDesk(displayId = 5, deskId = 2)
        taskRepository.addDesk(displayId = 6, deskId = 3)
        taskRepository.addDesk(displayId = 6, deskId = 4)
        taskRepository.setActiveDesk(displayId = 6, deskId = 4)

        controller.activatePreviousDesk(
            displayId = INVALID_DISPLAY,
            userId = DEFAULT_USER_ID,
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        verify(desksOrganizer).activateDesk(any(), eq(1), skipReorder = eq(false))
    }

    @Test
    fun activateNextDesk_invalidDisplay_activatesFocusedDisplayDesk() {
        val focusedDisplayId = 6
        whenever(focusTransitionObserver.globallyFocusedDisplayId).thenReturn(focusedDisplayId)
        taskRepository.addDesk(displayId = 5, deskId = 1)
        taskRepository.addDesk(displayId = 5, deskId = 2)
        taskRepository.setActiveDesk(displayId = 5, deskId = 1)
        taskRepository.addDesk(displayId = 6, deskId = 3)
        taskRepository.addDesk(displayId = 6, deskId = 4)
        taskRepository.setActiveDesk(displayId = 6, deskId = 3)

        controller.activateNextDesk(
            displayId = INVALID_DISPLAY,
            userId = DEFAULT_USER_ID,
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        verify(desksOrganizer).activateDesk(any(), eq(4), skipReorder = eq(false))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION)
    fun moveTaskToDesk_multipleDesks_addsPendingTransition() {
        val transition = Binder()
        whenever(enterDesktopTransitionHandler.moveToDesktop(any(), any())).thenReturn(transition)
        taskRepository.addDesk(DEFAULT_DISPLAY, deskId = 3)
        val task = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)
        task.isVisible = true

        controller.moveTaskToDesk(taskId = task.taskId, deskId = 3, transitionSource = UNKNOWN)

        verify(desksTransitionsObserver)
            .addPendingTransition(
                argThat {
                    this is DeskTransition.ActivateDeskWithTask &&
                        this.token == transition &&
                        this.deskId == 3 &&
                        this.enterTaskId == task.taskId
                }
            )
    }

    @Test
    fun dragToDesktop_landscapeDevice_resizable_undefinedOrientation_defaultLandscapeBounds() {
        val spyController = spy(controller)
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)

        val task = setUpFullscreenTask()
        setUpLandscapeDisplay()

        spyController.onDragPositioningEndThroughStatusBar(
            DEFAULT_DISPLAY,
            PointF(800f, 1280f),
            task,
            mockSurface,
        )
        val wct = getLatestDragToDesktopWct()
        assertThat(findBoundsChange(wct, task)).isEqualTo(DEFAULT_LANDSCAPE_BOUNDS)
    }

    @Test
    fun dragToDesktop_landscapeDevice_resizable_landscapeOrientation_defaultLandscapeBounds() {
        val spyController = spy(controller)
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)

        val task = setUpFullscreenTask(screenOrientation = SCREEN_ORIENTATION_LANDSCAPE)
        setUpLandscapeDisplay()

        spyController.onDragPositioningEndThroughStatusBar(
            DEFAULT_DISPLAY,
            PointF(800f, 1280f),
            task,
            mockSurface,
        )
        val wct = getLatestDragToDesktopWct()
        assertThat(findBoundsChange(wct, task)).isEqualTo(DEFAULT_LANDSCAPE_BOUNDS)
    }

    @Test
    fun dragToDesktop_landscapeDevice_resizable_portraitOrientation_resizablePortraitBounds() {
        val spyController = spy(controller)
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)

        val task =
            setUpFullscreenTask(
                screenOrientation = SCREEN_ORIENTATION_PORTRAIT,
                shouldLetterbox = true,
            )
        setUpLandscapeDisplay()

        spyController.onDragPositioningEndThroughStatusBar(
            DEFAULT_DISPLAY,
            PointF(800f, 1280f),
            task,
            mockSurface,
        )
        val wct = getLatestDragToDesktopWct()
        assertThat(findBoundsChange(wct, task)).isEqualTo(RESIZABLE_PORTRAIT_BOUNDS)
    }

    @Test
    fun dragToDesktop_landscapeDevice_unResizable_landscapeOrientation_defaultLandscapeBounds() {
        val spyController = spy(controller)
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)

        val task =
            setUpFullscreenTask(
                isResizable = false,
                screenOrientation = SCREEN_ORIENTATION_LANDSCAPE,
            )
        setUpLandscapeDisplay()

        spyController.onDragPositioningEndThroughStatusBar(
            DEFAULT_DISPLAY,
            PointF(800f, 1280f),
            task,
            mockSurface,
        )
        val wct = getLatestDragToDesktopWct()
        assertThat(findBoundsChange(wct, task)).isEqualTo(DEFAULT_LANDSCAPE_BOUNDS)
    }

    @Test
    fun dragToDesktop_landscapeDevice_unResizable_portraitOrientation_unResizablePortraitBounds() {
        val spyController = spy(controller)
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)

        val task =
            setUpFullscreenTask(
                isResizable = false,
                screenOrientation = SCREEN_ORIENTATION_PORTRAIT,
                shouldLetterbox = true,
            )
        setUpLandscapeDisplay()
        spyController.onDragPositioningEndThroughStatusBar(
            DEFAULT_DISPLAY,
            PointF(800f, 1280f),
            task,
            mockSurface,
        )
        val wct = getLatestDragToDesktopWct()
        assertThat(findBoundsChange(wct, task)).isEqualTo(UNRESIZABLE_PORTRAIT_BOUNDS)
    }

    @Test
    fun dragToDesktop_portraitDevice_resizable_undefinedOrientation_defaultPortraitBounds() {
        val spyController = spy(controller)
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)

        val task = setUpFullscreenTask(deviceOrientation = ORIENTATION_PORTRAIT)
        setUpPortraitDisplay()

        spyController.onDragPositioningEndThroughStatusBar(
            DEFAULT_DISPLAY,
            PointF(800f, 1280f),
            task,
            mockSurface,
        )
        val wct = getLatestDragToDesktopWct()
        assertThat(findBoundsChange(wct, task)).isEqualTo(DEFAULT_PORTRAIT_BOUNDS)
    }

    @Test
    fun dragToDesktop_portraitDevice_resizable_portraitOrientation_defaultPortraitBounds() {
        val spyController = spy(controller)
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)

        val task =
            setUpFullscreenTask(
                deviceOrientation = ORIENTATION_PORTRAIT,
                screenOrientation = SCREEN_ORIENTATION_PORTRAIT,
            )
        setUpPortraitDisplay()

        spyController.onDragPositioningEndThroughStatusBar(
            DEFAULT_DISPLAY,
            PointF(800f, 1280f),
            task,
            mockSurface,
        )
        val wct = getLatestDragToDesktopWct()
        assertThat(findBoundsChange(wct, task)).isEqualTo(DEFAULT_PORTRAIT_BOUNDS)
    }

    @Test
    fun dragToDesktop_portraitDevice_resizable_landscapeOrientation_resizableLandscapeBounds() {
        val spyController = spy(controller)
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)

        val task =
            setUpFullscreenTask(
                deviceOrientation = ORIENTATION_PORTRAIT,
                screenOrientation = SCREEN_ORIENTATION_LANDSCAPE,
                shouldLetterbox = true,
            )
        setUpPortraitDisplay()

        spyController.onDragPositioningEndThroughStatusBar(
            DEFAULT_DISPLAY,
            PointF(800f, 1280f),
            task,
            mockSurface,
        )
        val wct = getLatestDragToDesktopWct()
        assertThat(findBoundsChange(wct, task)).isEqualTo(RESIZABLE_LANDSCAPE_BOUNDS)
    }

    @Test
    fun dragToDesktop_portraitDevice_unResizable_portraitOrientation_defaultPortraitBounds() {
        val spyController = spy(controller)
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)

        val task =
            setUpFullscreenTask(
                isResizable = false,
                deviceOrientation = ORIENTATION_PORTRAIT,
                screenOrientation = SCREEN_ORIENTATION_PORTRAIT,
            )
        setUpPortraitDisplay()

        spyController.onDragPositioningEndThroughStatusBar(
            DEFAULT_DISPLAY,
            PointF(800f, 1280f),
            task,
            mockSurface,
        )
        val wct = getLatestDragToDesktopWct()
        assertThat(findBoundsChange(wct, task)).isEqualTo(DEFAULT_PORTRAIT_BOUNDS)
    }

    @Test
    fun dragToDesktop_portraitDevice_unResizable_landscapeOrientation_unResizableLandscapeBounds() {
        val spyController = spy(controller)
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)

        val task =
            setUpFullscreenTask(
                isResizable = false,
                deviceOrientation = ORIENTATION_PORTRAIT,
                screenOrientation = SCREEN_ORIENTATION_LANDSCAPE,
                shouldLetterbox = true,
            )
        setUpPortraitDisplay()

        spyController.onDragPositioningEndThroughStatusBar(
            DEFAULT_DISPLAY,
            PointF(200f, 200f),
            task,
            mockSurface,
        )
        val wct = getLatestDragToDesktopWct()
        assertThat(findBoundsChange(wct, task)).isEqualTo(UNRESIZABLE_LANDSCAPE_BOUNDS)
    }

    @Test
    fun dragToDesktop_movesTaskToDesk() {
        val spyController = spy(controller)
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)
        val task = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)

        spyController.onDragPositioningEndThroughStatusBar(
            DEFAULT_DISPLAY,
            PointF(200f, 200f),
            task,
            mockSurface,
        )

        val wct = getLatestDragToDesktopWct()
        verify(desksOrganizer).moveTaskToDesk(wct, deskId = 0, task)
    }

    @Test
    fun dragToDesktop_activatesDesk() {
        val spyController = spy(controller)
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)
        val task = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)

        spyController.onDragPositioningEndThroughStatusBar(
            DEFAULT_DISPLAY,
            PointF(200f, 200f),
            task,
            mockSurface,
        )

        val wct = getLatestDragToDesktopWct()
        verify(desksOrganizer).activateDesk(wct, deskId = 0)
    }

    @Test
    fun dragToDesktop_triggersEnterDesktopListener() {
        val spyController = spy(controller)
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)
        val task = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)

        spyController.onDragPositioningEndThroughStatusBar(
            DEFAULT_DISPLAY,
            PointF(200f, 200f),
            task,
            mockSurface,
        )

        verify(desktopRemoteListener).onEnterDesktopModeTransitionStarted(TO_DESKTOP_ANIM_DURATION)
    }

    @Test
    fun dragToDesktop_multipleDesks_addsPendingTransition() {
        val transition = Binder()
        val spyController = spy(controller)
        whenever(dragToDesktopTransitionHandler.finishDragToDesktopTransition(any()))
            .thenReturn(transition)
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)
        val task = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)

        spyController.onDragPositioningEndThroughStatusBar(
            DEFAULT_DISPLAY,
            PointF(200f, 200f),
            task,
            mockSurface,
        )

        verify(desksTransitionsObserver)
            .addPendingTransition(
                argThat {
                    this is DeskTransition.ActivateDeskWithTask &&
                        this.token == transition &&
                        this.deskId == 0 &&
                        this.enterTaskId == task.taskId
                }
            )
    }

    @Test
    fun onDesktopDragMove_callVisualIndicatorUpdateScheduler() {
        val task = setUpFreeformTask()
        val spyController = spy(controller)
        val mockSurface = mock(SurfaceControl::class.java)
        val mockDisplayLayout = mock(DisplayLayout::class.java)
        val indicatorType = DesktopModeVisualIndicator.IndicatorType.NO_INDICATOR
        whenever(displayController.getDisplayLayout(task.displayId)).thenReturn(mockDisplayLayout)
        whenever(mockDisplayLayout.stableInsets()).thenReturn(Rect(0, 100, 2000, 2000))
        val inputX = 200f
        val inputY = 10f
        val bounds = Rect(100, -100, 500, 1000)
        doReturn(desktopModeVisualIndicator)
            .whenever(spyController)
            .getOrCreateVisualIndicator(
                eq(task),
                eq(mockSurface),
                eq(DEFAULT_DISPLAY),
                eq(DesktopModeVisualIndicator.DragStartState.FROM_FREEFORM),
            )
        whenever(
                desktopModeVisualIndicator.calculateIndicatorType(
                    eq(DEFAULT_DISPLAY),
                    eq(PointF(inputX, bounds.top.toFloat())),
                )
            )
            .thenReturn(indicatorType)

        spyController.onDragPositioningMove(
            task,
            mockSurface,
            DEFAULT_DISPLAY,
            inputX,
            inputY,
            bounds,
        )

        verify(visualIndicatorUpdateScheduler)
            .schedule(
                eq(DEFAULT_DISPLAY),
                eq(indicatorType),
                eq(inputX),
                eq(inputY),
                eq(bounds),
                any(),
            )
    }

    @Test
    @DisableFlags(Flags.FLAG_REFACTOR_CAPTION_SANDBOXING_TO_CORE)
    fun onDesktopDragMove_endsOutsideValidDragArea_snapsToValidBounds() {
        val task = setUpFreeformTask()
        val spyController = spy(controller)
        val mockSurface = mock(SurfaceControl::class.java)
        val captionInsets = 20
        val mockDisplayLayout = mock(DisplayLayout::class.java)
        whenever(displayController.getDisplayLayout(task.displayId)).thenReturn(mockDisplayLayout)
        whenever(mockDisplayLayout.stableInsets()).thenReturn(Rect(0, 100, 2000, 2000))
        task.configuration.windowConfiguration.appBounds =
            Rect(task.configuration.windowConfiguration.bounds).apply { this.top += captionInsets }
        spyController.onDragPositioningMove(
            task,
            mockSurface,
            DEFAULT_DISPLAY,
            200f,
            10f,
            Rect(100, -100, 500, 1000),
        )

        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.NO_INDICATOR)
        val needDragIndicatorCleanup =
            spyController.onDragPositioningEnd(
                task,
                mockSurface,
                inputCoordinate = PointF(200f, -200f),
                currentDragBounds = Rect(100, -100, 500, 1000),
                validDragArea = Rect(0, 50, 2000, 2000),
                dragStartBounds = Rect(),
                motionEvent,
            )
        val rectAfterEnd = Rect(100, 50, 500, 1150)
        val appBoundsAfterEnd = Rect(rectAfterEnd).apply { this.top += captionInsets }
        verify(transitions)
            .startTransition(
                eq(TRANSIT_CHANGE),
                Mockito.argThat { wct ->
                    return@argThat wct.changes.any { (token, change) ->
                        change.configuration.windowConfiguration.bounds == rectAfterEnd
                        change.configuration.windowConfiguration.appBounds == appBoundsAfterEnd
                    }
                },
                eq(windowDragTransitionHandler),
            )
        assertFalse(needDragIndicatorCleanup)
    }

    @Test
    @DisableFlags(Flags.FLAG_REFACTOR_CAPTION_SANDBOXING_TO_CORE)
    fun onDesktopDragEnd_noIndicator_updatesTaskBounds() {
        val task = setUpFreeformTask()
        val spyController = spy(controller)
        val mockSurface = mock(SurfaceControl::class.java)
        val mockDisplayLayout = mock(DisplayLayout::class.java)
        val captionInsets = 20
        whenever(displayController.getDisplayLayout(task.displayId)).thenReturn(mockDisplayLayout)
        whenever(mockDisplayLayout.stableInsets()).thenReturn(Rect(0, 100, 2000, 2000))
        task.configuration.windowConfiguration.appBounds =
            Rect(task.configuration.windowConfiguration.bounds).apply { this.top += captionInsets }
        spyController.onDragPositioningMove(
            task,
            mockSurface,
            DEFAULT_DISPLAY,
            200f,
            10f,
            Rect(100, 200, 500, 1000),
        )

        val currentDragBounds = Rect(100, 200, 500, 1000)
        val appBounds = Rect(currentDragBounds).apply { this.top += captionInsets }
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.NO_INDICATOR)

        val needDragIndicatorCleanup =
            spyController.onDragPositioningEnd(
                task,
                mockSurface,
                inputCoordinate = PointF(200f, 300f),
                currentDragBounds = currentDragBounds,
                validDragArea = Rect(0, 50, 2000, 2000),
                dragStartBounds = Rect(),
                motionEvent,
            )

        verify(transitions)
            .startTransition(
                eq(TRANSIT_CHANGE),
                Mockito.argThat { wct ->
                    return@argThat wct.changes.any { (token, change) ->
                        change.configuration.windowConfiguration.bounds == currentDragBounds
                        change.configuration.windowConfiguration.appBounds == appBounds
                    }
                },
                eq(windowDragTransitionHandler),
            )
        assertFalse(needDragIndicatorCleanup)
    }

    @Test
    fun onDesktopDragEnd_noBoundsChangeAndMoveToNewDisplay_reparentWct() {
        val task = setUpFreeformTask()
        val spyController = spy(controller)
        val mockSurface = mock(SurfaceControl::class.java)
        val mockDisplayLayout = mock(DisplayLayout::class.java)
        taskRepository.addDesk(displayId = SECONDARY_DISPLAY_ID, deskId = SECONDARY_DISPLAY_ID)
        taskRepository.setActiveDesk(
            displayId = SECONDARY_DISPLAY_ID,
            deskId = SECONDARY_DISPLAY_ID,
        )
        whenever(displayController.getDisplayLayout(task.displayId)).thenReturn(mockDisplayLayout)
        whenever(mockDisplayLayout.stableInsets()).thenReturn(Rect(0, 100, 2000, 2000))

        val currentDragBounds = Rect(100, 200, 500, 1000)
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.NO_INDICATOR)
        whenever(motionEvent.displayId).thenReturn(SECONDARY_DISPLAY_ID)

        spyController.onDragPositioningEnd(
            taskInfo = task,
            taskSurface = mockSurface,
            inputCoordinate = PointF(200f, 300f),
            currentDragBounds = currentDragBounds,
            validDragArea = Rect(0, 50, 2000, 2000),
            dragStartBounds = currentDragBounds,
            motionEvent = motionEvent,
        )

        verify(desksOrganizer).moveTaskToDesk(any(), eq(SECONDARY_DISPLAY_ID), eq(task), eq(false))
    }

    @Test
    fun onDesktopDragEnd_fullscreenIndicator_dragToExitDesktop() {
        val task = setUpFreeformTask(bounds = Rect(0, 0, 100, 100))
        val spyController = spy(controller)
        val mockSurface = mock(SurfaceControl::class.java)
        val mockDisplayLayout = mock(DisplayLayout::class.java)
        val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
        tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
        whenever(displayController.getDisplayLayout(task.displayId)).thenReturn(mockDisplayLayout)
        whenever(mockDisplayLayout.stableInsets()).thenReturn(Rect(0, 100, 2000, 2000))
        whenever(mockDisplayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(STABLE_BOUNDS)
        }
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR)

        // Drag move the task to the top edge
        spyController.onDragPositioningMove(
            task,
            mockSurface,
            DEFAULT_DISPLAY,
            200f,
            10f,
            Rect(100, 200, 500, 1000),
        )

        val needDragIndicatorCleanup =
            spyController.onDragPositioningEnd(
                task,
                mockSurface,
                inputCoordinate = PointF(200f, 300f),
                currentDragBounds = Rect(100, 50, 500, 1000),
                validDragArea = Rect(0, 50, 2000, 2000),
                dragStartBounds = Rect(),
                motionEvent,
            )

        // Assert the task exits desktop mode
        val wct = getLatestExitDesktopWct()
        assertThat(wct.changes[task.token.asBinder()]?.windowingMode)
            .isEqualTo(WINDOWING_MODE_UNDEFINED)
        assertTrue(needDragIndicatorCleanup)
    }

    @Test
    fun onDesktopDragEnd_fullscreenIndicator_dragToMaximize() {
        val task = setUpFreeformTask(bounds = Rect(0, 0, 100, 100))
        val spyController = spy(controller)
        val mockSurface = mock(SurfaceControl::class.java)
        val mockDisplayLayout = mock(DisplayLayout::class.java)
        val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
        tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM
        whenever(displayController.getDisplayLayout(task.displayId)).thenReturn(mockDisplayLayout)
        whenever(mockDisplayLayout.stableInsets()).thenReturn(Rect(0, 100, 2000, 2000))
        whenever(mockDisplayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(STABLE_BOUNDS)
        }
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR)
        whenever(motionEvent.displayId).thenReturn(DEFAULT_DISPLAY)

        // Drag move the task to the top edge
        val currentDragBounds = Rect(100, 50, 500, 1000)
        spyController.onDragPositioningMove(
            task,
            mockSurface,
            DEFAULT_DISPLAY,
            200f,
            10f,
            Rect(100, 200, 500, 1000),
        )
        spyController.onDragPositioningEnd(
            task,
            mockSurface,
            inputCoordinate = PointF(200f, 300f),
            currentDragBounds,
            validDragArea = Rect(0, 50, 2000, 2000),
            dragStartBounds = Rect(),
            motionEvent,
        )

        // Assert bounds set to stable bounds
        val wct = getLatestToggleResizeDesktopTaskWct(currentDragBounds)
        assertThat(findBoundsChange(wct, task)).isEqualTo(STABLE_BOUNDS)
        // Assert event is properly logged
        verify(desktopModeEventLogger, times(1))
            .logTaskResizingStarted(
                resizeTrigger = ResizeTrigger.DRAG_TO_TOP_RESIZE_TRIGGER,
                inputMethod = InputMethod.UNKNOWN_INPUT_METHOD,
                taskInfo = task,
                taskWidth = task.configuration.windowConfiguration.bounds.width(),
                taskHeight = task.configuration.windowConfiguration.bounds.height(),
                displayController = displayController,
                deskId = 0,
            )
        verify(desktopModeEventLogger, times(1))
            .logTaskResizingEnded(
                resizeTrigger = ResizeTrigger.DRAG_TO_TOP_RESIZE_TRIGGER,
                inputMethod = InputMethod.UNKNOWN_INPUT_METHOD,
                taskInfo = task,
                taskWidth = STABLE_BOUNDS.width(),
                taskHeight = STABLE_BOUNDS.height(),
                displayController = displayController,
                deskId = 0,
            )
    }

    @Test
    fun onDesktopDragEnd_fullscreenIndicator_dragToMaximize_alreadyMaximized_noBoundsChange() {
        val task = setUpFreeformTask(bounds = STABLE_BOUNDS)
        val spyController = spy(controller)
        val mockSurface = mock(SurfaceControl::class.java)
        val mockDisplayLayout = mock(DisplayLayout::class.java)
        val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
        tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM
        whenever(displayController.getDisplayLayout(task.displayId)).thenReturn(mockDisplayLayout)
        whenever(mockDisplayLayout.stableInsets()).thenReturn(Rect(0, 100, 2000, 2000))
        whenever(mockDisplayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(STABLE_BOUNDS)
        }
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR)
        whenever(motionEvent.displayId).thenReturn(DEFAULT_DISPLAY)

        // Drag move the task to the top edge
        val currentDragBounds = Rect(100, 50, 500, 1000)
        spyController.onDragPositioningMove(
            task,
            mockSurface,
            DEFAULT_DISPLAY,
            200f,
            10f,
            Rect(100, 200, 500, 1000),
        )
        spyController.onDragPositioningEnd(
            task,
            mockSurface,
            inputCoordinate = PointF(200f, 300f),
            currentDragBounds = currentDragBounds,
            validDragArea = Rect(0, 50, 2000, 2000),
            dragStartBounds = Rect(),
            motionEvent,
        )

        // Assert that task is NOT updated via WCT
        verify(toggleResizeDesktopTaskTransitionHandler, never())
            .startTransition(any(), any(), any(), any())
        // Assert that task leash is updated via Surface Animations
        verify(mReturnToDragStartAnimator)
            .start(
                eq(task.taskId),
                eq(mockSurface),
                eq(currentDragBounds),
                eq(STABLE_BOUNDS),
                anyOrNull(),
            )
        // Assert no event is logged
        verify(desktopModeEventLogger, never())
            .logTaskResizingStarted(any(), any(), any(), any(), any(), any(), any())
        verify(desktopModeEventLogger, never())
            .logTaskResizingEnded(any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun onDesktopDragEnd_noIndicator_dropOnInEligibleDisplay_updateSurfacePosition_noWct() {
        val task = setUpFreeformTask(bounds = STABLE_BOUNDS)
        val spyController = spy(controller)
        val mockSurface = mock(SurfaceControl::class.java)
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.NO_INDICATOR)
        whenever(motionEvent.displayId).thenReturn(DEFAULT_DISPLAY)
        shellDesktopState.canBeWindowDropTarget = false

        val startBounds = Rect(0, 50, 500, 550)
        val currentDragBounds = Rect(100, 100, 600, 600)

        spyController.onDragPositioningEnd(
            taskInfo = task,
            taskSurface = mockSurface,
            inputCoordinate = PointF(250f, 300f),
            currentDragBounds = currentDragBounds,
            validDragArea = Rect(0, 0, 2000, 2000),
            dragStartBounds = startBounds,
            motionEvent = motionEvent,
        )

        // Verify task surface is moved back to start position.
        verify(surfaceControlTransaction)
            .setPosition(mockSurface, startBounds.left.toFloat(), startBounds.top.toFloat())
        // Verify no WCT is started.
        verify(transitions, never()).startTransition(any(), any(), any())
    }

    @Test
    fun onDesktopDragEnd_noIndicator_noBoundsMovement_outOfValidArea_startsReturnToStartAnimation_noWct() {
        val task = setUpFreeformTask(bounds = STABLE_BOUNDS)
        val spyController = spy(controller)
        val mockSurface = mock(SurfaceControl::class.java)
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.NO_INDICATOR)
        whenever(motionEvent.displayId).thenReturn(DEFAULT_DISPLAY)

        val startBounds = Rect(0, 50, 500, 550)
        // Drag slightly outside of valid drag area
        val currentDragBounds = Rect(-10, 50, 490, 550)

        spyController.onDragPositioningEnd(
            taskInfo = task,
            taskSurface = mockSurface,
            inputCoordinate = PointF(250f, 300f),
            currentDragBounds = currentDragBounds,
            validDragArea = Rect(0, 50, 2000, 2000),
            dragStartBounds = startBounds,
            motionEvent = motionEvent,
        )

        // Verify animation to return to start is triggered.
        verify(mReturnToDragStartAnimator)
            .start(
                eq(task.taskId),
                eq(mockSurface),
                eq(currentDragBounds),
                eq(startBounds),
                anyOrNull(),
            )
        // Verify no WCT is started.
        verify(transitions, never()).startTransition(any(), any(), any())
    }

    @Test
    fun onDesktopDragEnd_noIndicator_noBoundsMovement_noReturnToStartAnimation_noWct_setPosition() {
        val task = setUpFreeformTask(bounds = STABLE_BOUNDS)
        val spyController = spy(controller)
        val mockSurface = mock(SurfaceControl::class.java)
        val mockDisplayLayout = mock(DisplayLayout::class.java)
        whenever(displayController.getDisplayLayout(task.displayId)).thenReturn(mockDisplayLayout)
        whenever(mockDisplayLayout.stableInsets()).thenReturn(Rect(0, 100, 2000, 2000))
        whenever(mockDisplayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(STABLE_BOUNDS)
        }
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        whenever(desktopModeVisualIndicator.updateIndicatorType(any(), anyOrNull()))
            .thenReturn(DesktopModeVisualIndicator.IndicatorType.NO_INDICATOR)
        whenever(motionEvent.displayId).thenReturn(DEFAULT_DISPLAY)

        // "Drag move" the task, but the bounds aren't actually changing. Which can be the case
        // on mouse clicks that generate ACTION_MOVE events without position changes.
        val currentDragBounds = Rect(200, 200, 700, 700)
        spyController.onDragPositioningMove(
            taskInfo = task,
            taskSurface = mockSurface,
            displayId = DEFAULT_DISPLAY,
            inputX = 500f,
            inputY = 210f,
            taskBounds = currentDragBounds,
        )
        spyController.onDragPositioningEnd(
            taskInfo = task,
            taskSurface = mockSurface,
            inputCoordinate = PointF(510f, 210f),
            currentDragBounds = currentDragBounds,
            validDragArea = Rect(0, 50, 2000, 2000),
            dragStartBounds = currentDragBounds,
            motionEvent = motionEvent,
        )

        // Even though end bounds are the same as start bounds, there's no need to animate the
        // return because the current bounds are also the same as start/end.
        verify(mReturnToDragStartAnimator, never())
            .start(eq(task.taskId), eq(mockSurface), any(), any(), anyOrNull())
        // Verify no WCT is started.
        verify(transitions, never()).startTransition(any(), any(), any())
        // Verify the task position is finalized to current bounds
        verify(surfaceControlTransaction)
            .setPosition(
                mockSurface,
                currentDragBounds.left.toFloat(),
                currentDragBounds.top.toFloat(),
            )
    }

    @Test
    fun enterSplit_freeformTaskIsMovedToSplit() {
        val task1 = setUpFreeformTask()
        val task2 = setUpFreeformTask()
        val task3 = setUpFreeformTask()

        task1.isFocused = false
        task2.isFocused = true
        task3.isFocused = false

        controller.enterSplit(DEFAULT_DISPLAY, leftOrTop = false)

        verify(splitScreenController)
            .requestEnterSplitSelect(
                eq(task2),
                eq(SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT),
                eq(task2.configuration.windowConfiguration.bounds),
                /* startRecents= */ eq(true),
                /* withRecentsWct= */ any(),
            )
    }

    @Test
    fun newWindow_fromFullscreenOpensInSplit() {
        setUpLandscapeDisplay()
        val task = setUpFullscreenTask()
        val optionsCaptor = argumentCaptor<Bundle>()
        runOpenNewWindow(task)
        verify(splitScreenController)
            .startIntent(
                any(),
                anyInt(),
                any(),
                any(),
                optionsCaptor.capture(),
                anyOrNull(),
                anyOrNull(),
                eq(true),
                eq(SPLIT_INDEX_UNDEFINED),
                eq(DEFAULT_DISPLAY),
            )
        assertThat(ActivityOptions.fromBundle(optionsCaptor.firstValue).launchWindowingMode)
            .isEqualTo(WINDOWING_MODE_MULTI_WINDOW)
    }

    @Test
    fun newWindow_fromSplitOpensInSplit() {
        setUpLandscapeDisplay()
        val task = setUpSplitScreenTask()
        val optionsCaptor = argumentCaptor<Bundle>()
        runOpenNewWindow(task)
        verify(splitScreenController)
            .startIntent(
                any(),
                anyInt(),
                any(),
                any(),
                optionsCaptor.capture(),
                anyOrNull(),
                anyOrNull(),
                eq(true),
                eq(SPLIT_INDEX_UNDEFINED),
                eq(DEFAULT_DISPLAY),
            )
        assertThat(ActivityOptions.fromBundle(optionsCaptor.firstValue).launchWindowingMode)
            .isEqualTo(WINDOWING_MODE_MULTI_WINDOW)
    }

    @Test
    fun newWindow_fromFreeformAddsNewWindow() {
        setUpLandscapeDisplay()
        val task = setUpFreeformTask()
        val wctCaptor = argumentCaptor<WindowContainerTransaction>()
        val transition = Binder()
        whenever(
                mMockDesktopImmersiveController.exitImmersiveIfApplicable(
                    any(),
                    anyInt(),
                    anyOrNull(),
                    any(),
                )
            )
            .thenReturn(ExitResult.NoExit)
        mockStartLaunchTransition(transition = transition)

        runOpenNewWindow(task)

        verifyStartLaunchTransition(wctArgCaptor = wctCaptor)
        assertThat(
                ActivityOptions.fromBundle(wctCaptor.firstValue.hierarchyOps[0].launchOptions)
                    .launchWindowingMode
            )
            .isEqualTo(WINDOWING_MODE_FREEFORM)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY)
    fun newWindow_fromFreeformAddsNewWindow_launchesToCallingDisplay() {
        setUpLandscapeDisplay()
        val deskId = 2
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = deskId)
        taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = deskId)
        val task = setUpFreeformTask(displayId = SECOND_DISPLAY, deskId = deskId)
        val wctCaptor = argumentCaptor<WindowContainerTransaction>()
        val transition = Binder()
        whenever(
                mMockDesktopImmersiveController.exitImmersiveIfApplicable(
                    any(),
                    anyInt(),
                    anyOrNull(),
                    any(),
                )
            )
            .thenReturn(ExitResult.NoExit)
        mockStartLaunchTransition(transition = transition)

        runOpenNewWindow(task)

        verifyStartLaunchTransition(wctArgCaptor = wctCaptor)
        wctCaptor.firstValue.assertLaunchTaskOnDisplay(SECOND_DISPLAY)
    }

    @Test
    fun newWindow_fromFreeform_exitsImmersiveIfNeeded() {
        setUpLandscapeDisplay()
        val immersiveTask = setUpFreeformTask()
        val task = setUpFreeformTask()
        val runOnStart = RunOnStartTransitionCallback()
        val transition = Binder()
        whenever(
                mMockDesktopImmersiveController.exitImmersiveIfApplicable(
                    any(),
                    anyInt(),
                    anyOrNull(),
                    any(),
                )
            )
            .thenReturn(ExitResult.Exit(immersiveTask.taskId, runOnStart))
        mockStartLaunchTransition(transition = transition)

        runOpenNewWindow(task)

        runOnStart.assertOnlyInvocation(transition)
    }

    private fun runOpenNewWindow(task: RunningTaskInfo) {
        markTaskVisible(task)
        task.baseActivity = mock(ComponentName::class.java)
        task.isFocused = true
        runningTasks.add(task)
        controller.openNewWindow(task)
    }

    @Test
    fun openInstance_fromFullscreenOpensInSplit() {
        setUpLandscapeDisplay()
        val task = setUpFullscreenTask()
        val taskToRequest = setUpFreeformTask()
        val optionsCaptor = argumentCaptor<Bundle>()
        runOpenInstance(task, taskToRequest.taskId)
        verify(splitScreenController)
            .startTask(anyInt(), anyInt(), optionsCaptor.capture(), anyOrNull(), any())
        assertThat(ActivityOptions.fromBundle(optionsCaptor.firstValue).launchWindowingMode)
            .isEqualTo(WINDOWING_MODE_MULTI_WINDOW)
    }

    @Test
    fun openInstance_fromSplitOpensInSplit() {
        setUpLandscapeDisplay()
        val task = setUpSplitScreenTask()
        val taskToRequest = setUpFreeformTask()
        val optionsCaptor = argumentCaptor<Bundle>()
        runOpenInstance(task, taskToRequest.taskId)
        verify(splitScreenController)
            .startTask(anyInt(), anyInt(), optionsCaptor.capture(), anyOrNull(), any())
        assertThat(ActivityOptions.fromBundle(optionsCaptor.firstValue).launchWindowingMode)
            .isEqualTo(WINDOWING_MODE_MULTI_WINDOW)
    }

    @Test
    fun openInstance_fromFreeformAddsNewWindow_multiDesksEnabled() {
        setUpLandscapeDisplay()
        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = 0)
        val taskToRequest = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = 0)
        mockStartLaunchTransition(transitionType = TRANSIT_TO_FRONT, taskId = taskToRequest.taskId)

        runOpenInstance(task, taskToRequest.taskId)

        verifyStartLaunchTransition()
        verify(desksOrganizer).reorderTaskToFront(any(), eq(0), eq(taskToRequest))
    }

    @Test
    fun openInstance_fromFreeform_minimizesSeparately() {
        setUpLandscapeDisplay()
        val deskId = 0
        val freeformTasks =
            (1..MAX_TASK_LIMIT + 1).map { _ ->
                setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = deskId)
            }
        val oldestTask = freeformTasks.first()
        val newestTask = freeformTasks.last()

        val transition = Binder()
        val wctCaptor = argumentCaptor<WindowContainerTransaction>()
        mockStartLaunchTransition(transition = transition, wctArgCaptor = wctCaptor)

        runOpenInstance(newestTask, freeformTasks[1].taskId)

        val wct = wctCaptor.firstValue
        verify(desksOrganizer, never()).minimizeTask(wct, deskId, oldestTask)
        assertThat(desktopTasksLimiter.hasTaskLimitTransitionForTesting(transition)).isTrue()
    }

    @Test
    fun openInstance_fromFreeform_exitsImmersiveIfNeeded() {
        setUpLandscapeDisplay()
        val freeformTask = setUpFreeformTask()
        val immersiveTask = setUpFreeformTask()
        taskRepository.setTaskInFullImmersiveState(
            displayId = immersiveTask.displayId,
            taskId = immersiveTask.taskId,
            immersive = true,
        )
        val runOnStartTransit = RunOnStartTransitionCallback()
        val transition = Binder()
        mockStartLaunchTransition(transition = transition)
        whenever(
                mMockDesktopImmersiveController.exitImmersiveIfApplicable(
                    any(),
                    eq(DEFAULT_DISPLAY),
                    eq(freeformTask.taskId),
                    any(),
                )
            )
            .thenReturn(
                ExitResult.Exit(
                    exitingTask = immersiveTask.taskId,
                    runOnTransitionStart = runOnStartTransit,
                )
            )

        runOpenInstance(immersiveTask, freeformTask.taskId)

        verify(mMockDesktopImmersiveController)
            .exitImmersiveIfApplicable(
                any(),
                eq(immersiveTask.displayId),
                eq(freeformTask.taskId),
                any(),
            )
        runOnStartTransit.assertOnlyInvocation(transition)
    }

    private fun runOpenInstance(callingTask: RunningTaskInfo, requestedTaskId: Int) {
        markTaskVisible(callingTask)
        callingTask.baseActivity = mock(ComponentName::class.java)
        callingTask.isFocused = true
        runningTasks.add(callingTask)
        controller.openInstance(callingTask, requestedTaskId)
    }

    @Test
    fun toggleBounds_togglesToStableBounds() {
        val bounds = Rect(0, 0, 100, 100)
        val task = setUpFreeformTask(DEFAULT_DISPLAY, bounds)

        controller.toggleDesktopTaskSize(
            task,
            ToggleTaskSizeInteraction(
                ToggleTaskSizeInteraction.Direction.MAXIMIZE,
                ToggleTaskSizeInteraction.Source.HEADER_BUTTON_TO_MAXIMIZE,
                InputMethod.TOUCH,
            ),
        )

        // Assert bounds set to stable bounds
        val wct = getLatestToggleResizeDesktopTaskWct()
        assertThat(findBoundsChange(wct, task)).isEqualTo(STABLE_BOUNDS)
        verify(desktopModeEventLogger, times(1))
            .logTaskResizingEnded(
                resizeTrigger = ResizeTrigger.MAXIMIZE_BUTTON,
                inputMethod = InputMethod.TOUCH,
                taskInfo = task,
                taskWidth = STABLE_BOUNDS.width(),
                taskHeight = STABLE_BOUNDS.height(),
                displayController = displayController,
                deskId = 0,
            )
    }

    @Test
    fun handleSnapResizingTaskOnDrag_nonResizable_startsRepositionAnimation() {
        val task =
            setUpFreeformTask(DEFAULT_DISPLAY, Rect(0, 0, 200, 100)).apply { isResizeable = false }
        val preDragBounds = Rect(100, 100, 400, 500)
        val currentDragBounds = Rect(0, 100, 300, 500)

        controller.handleSnapResizingTaskOnDrag(
            task,
            SnapPosition.LEFT,
            mockSurface,
            currentDragBounds,
            preDragBounds,
            motionEvent,
        )
        verify(mReturnToDragStartAnimator)
            .start(
                eq(task.taskId),
                eq(mockSurface),
                eq(currentDragBounds),
                eq(preDragBounds),
                any(),
            )
        verify(desktopModeEventLogger, never())
            .logTaskResizingStarted(any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun handleInstantSnapResizingTask_nonResizable_animatorNotStartedAndShowsToast() {
        val taskBounds = Rect(0, 0, 200, 100)
        val task = setUpFreeformTask(DEFAULT_DISPLAY, taskBounds).apply { isResizeable = false }

        controller.handleInstantSnapResizingTask(
            task,
            SnapPosition.LEFT,
            ResizeTrigger.SNAP_LEFT_MENU,
            InputMethod.MOUSE,
        )

        // Assert that task is NOT updated via WCT
        verify(toggleResizeDesktopTaskTransitionHandler, never())
            .startTransition(any(), any(), any(), any())
        verify(mockToast).show()
    }

    @Test
    fun toggleBounds_togglesToCalculatedBoundsForNonResizable() {
        val bounds = Rect(0, 0, 200, 100)
        val task =
            setUpFreeformTask(DEFAULT_DISPLAY, bounds).apply {
                topActivityInfo.apply {
                    this?.screenOrientation = SCREEN_ORIENTATION_LANDSCAPE
                    configuration.windowConfiguration.appBounds = bounds
                }
                appCompatTaskInfo.topActivityAppBounds.set(0, 0, bounds.width(), bounds.height())
                isResizeable = false
            }

        // Bounds should be 1000 x 500, vertically centered in the 1000 x 1000 stable bounds
        val expectedBounds = Rect(STABLE_BOUNDS.left, 250, STABLE_BOUNDS.right, 750)

        controller.toggleDesktopTaskSize(
            task,
            ToggleTaskSizeInteraction(
                ToggleTaskSizeInteraction.Direction.MAXIMIZE,
                ToggleTaskSizeInteraction.Source.HEADER_BUTTON_TO_MAXIMIZE,
                InputMethod.TOUCH,
            ),
        )

        // Assert bounds set to stable bounds
        val wct = getLatestToggleResizeDesktopTaskWct()
        assertThat(findBoundsChange(wct, task)).isEqualTo(expectedBounds)
        verify(desktopModeEventLogger, times(1))
            .logTaskResizingEnded(
                resizeTrigger = ResizeTrigger.MAXIMIZE_BUTTON,
                inputMethod = InputMethod.TOUCH,
                taskInfo = task,
                taskWidth = expectedBounds.width(),
                taskHeight = expectedBounds.height(),
                displayController = displayController,
                deskId = 0,
            )
    }

    @Test
    fun toggleBounds_lastBoundsBeforeMaximizeSaved() {
        val bounds = Rect(0, 0, 100, 100)
        val task = setUpFreeformTask(DEFAULT_DISPLAY, bounds)

        controller.toggleDesktopTaskSize(
            task,
            ToggleTaskSizeInteraction(
                ToggleTaskSizeInteraction.Direction.MAXIMIZE,
                ToggleTaskSizeInteraction.Source.HEADER_BUTTON_TO_MAXIMIZE,
                InputMethod.TOUCH,
            ),
        )

        assertThat(taskRepository.removeBoundsBeforeSnapOrMaximize(task.taskId)).isEqualTo(bounds)
        verify(desktopModeEventLogger)
            .logTaskResizingEnded(
                resizeTrigger = ResizeTrigger.MAXIMIZE_BUTTON,
                inputMethod = InputMethod.TOUCH,
                taskInfo = task,
                taskWidth = STABLE_BOUNDS.width(),
                taskHeight = STABLE_BOUNDS.height(),
                displayController = displayController,
                deskId = 0,
            )
    }

    @Test
    fun toggleBounds_togglesFromStableBoundsToLastBoundsBeforeMaximize() {
        val boundsBeforeMaximize = Rect(0, 0, 100, 100)
        val task = setUpFreeformTask(DEFAULT_DISPLAY, boundsBeforeMaximize)

        // Maximize
        controller.toggleDesktopTaskSize(
            task,
            ToggleTaskSizeInteraction(
                ToggleTaskSizeInteraction.Direction.MAXIMIZE,
                ToggleTaskSizeInteraction.Source.HEADER_BUTTON_TO_MAXIMIZE,
                InputMethod.TOUCH,
            ),
        )
        task.configuration.windowConfiguration.bounds.set(STABLE_BOUNDS)

        // Restore
        controller.toggleDesktopTaskSize(
            task,
            ToggleTaskSizeInteraction(
                ToggleTaskSizeInteraction.Direction.RESTORE,
                ToggleTaskSizeInteraction.Source.HEADER_BUTTON_TO_RESTORE,
                InputMethod.TOUCH,
            ),
        )

        // Assert bounds set to last bounds before maximize
        val wct = getLatestToggleResizeDesktopTaskWct()
        assertThat(findBoundsChange(wct, task)).isEqualTo(boundsBeforeMaximize)
        verify(desktopModeEventLogger, times(1))
            .logTaskResizingEnded(
                resizeTrigger = ResizeTrigger.MAXIMIZE_BUTTON,
                inputMethod = InputMethod.TOUCH,
                taskInfo = task,
                taskWidth = boundsBeforeMaximize.width(),
                taskHeight = boundsBeforeMaximize.height(),
                displayController = displayController,
                deskId = 0,
            )
    }

    @Test
    fun toggleBounds_togglesFromStableBoundsToLastBoundsBeforeMaximize_nonResizeableEqualWidth() {
        val boundsBeforeMaximize = Rect(0, 0, 100, 100)
        val task =
            setUpFreeformTask(DEFAULT_DISPLAY, boundsBeforeMaximize).apply { isResizeable = false }

        // Maximize
        controller.toggleDesktopTaskSize(
            task,
            ToggleTaskSizeInteraction(
                ToggleTaskSizeInteraction.Direction.MAXIMIZE,
                ToggleTaskSizeInteraction.Source.HEADER_BUTTON_TO_MAXIMIZE,
                InputMethod.TOUCH,
            ),
        )
        task.configuration.windowConfiguration.bounds.set(
            STABLE_BOUNDS.left,
            boundsBeforeMaximize.top,
            STABLE_BOUNDS.right,
            boundsBeforeMaximize.bottom,
        )

        // Restore
        controller.toggleDesktopTaskSize(
            task,
            ToggleTaskSizeInteraction(
                ToggleTaskSizeInteraction.Direction.RESTORE,
                ToggleTaskSizeInteraction.Source.HEADER_BUTTON_TO_RESTORE,
                InputMethod.TOUCH,
            ),
        )

        // Assert bounds set to last bounds before maximize
        val wct = getLatestToggleResizeDesktopTaskWct()
        assertThat(findBoundsChange(wct, task)).isEqualTo(boundsBeforeMaximize)
        verify(desktopModeEventLogger, times(1))
            .logTaskResizingEnded(
                resizeTrigger = ResizeTrigger.MAXIMIZE_BUTTON,
                inputMethod = InputMethod.TOUCH,
                taskInfo = task,
                taskWidth = boundsBeforeMaximize.width(),
                taskHeight = boundsBeforeMaximize.height(),
                displayController = displayController,
                deskId = 0,
            )
    }

    @Test
    fun toggleBounds_togglesFromStableBoundsToLastBoundsBeforeMaximize_nonResizeableEqualHeight() {
        val boundsBeforeMaximize = Rect(0, 0, 100, 100)
        val task =
            setUpFreeformTask(DEFAULT_DISPLAY, boundsBeforeMaximize).apply { isResizeable = false }

        // Maximize
        controller.toggleDesktopTaskSize(
            task,
            ToggleTaskSizeInteraction(
                ToggleTaskSizeInteraction.Direction.MAXIMIZE,
                ToggleTaskSizeInteraction.Source.HEADER_BUTTON_TO_MAXIMIZE,
                InputMethod.TOUCH,
            ),
        )
        task.configuration.windowConfiguration.bounds.set(
            boundsBeforeMaximize.left,
            STABLE_BOUNDS.top,
            boundsBeforeMaximize.right,
            STABLE_BOUNDS.bottom,
        )

        // Restore
        controller.toggleDesktopTaskSize(
            task,
            ToggleTaskSizeInteraction(
                ToggleTaskSizeInteraction.Direction.RESTORE,
                ToggleTaskSizeInteraction.Source.HEADER_BUTTON_TO_RESTORE,
                InputMethod.TOUCH,
            ),
        )

        // Assert bounds set to last bounds before maximize
        val wct = getLatestToggleResizeDesktopTaskWct()
        assertThat(findBoundsChange(wct, task)).isEqualTo(boundsBeforeMaximize)
        verify(desktopModeEventLogger, times(1))
            .logTaskResizingEnded(
                resizeTrigger = ResizeTrigger.MAXIMIZE_BUTTON,
                inputMethod = InputMethod.TOUCH,
                taskInfo = task,
                taskWidth = boundsBeforeMaximize.width(),
                taskHeight = boundsBeforeMaximize.height(),
                displayController = displayController,
                deskId = 0,
            )
    }

    @Test
    fun toggleBounds_removesLastBoundsBeforeMaximizeAfterRestoringBounds() {
        val boundsBeforeMaximize = Rect(0, 0, 100, 100)
        val task = setUpFreeformTask(DEFAULT_DISPLAY, boundsBeforeMaximize)

        // Maximize
        controller.toggleDesktopTaskSize(
            task,
            ToggleTaskSizeInteraction(
                ToggleTaskSizeInteraction.Direction.MAXIMIZE,
                ToggleTaskSizeInteraction.Source.HEADER_BUTTON_TO_MAXIMIZE,
                InputMethod.TOUCH,
            ),
        )
        task.configuration.windowConfiguration.bounds.set(STABLE_BOUNDS)

        // Restore
        controller.toggleDesktopTaskSize(
            task,
            ToggleTaskSizeInteraction(
                ToggleTaskSizeInteraction.Direction.RESTORE,
                ToggleTaskSizeInteraction.Source.HEADER_BUTTON_TO_RESTORE,
                InputMethod.TOUCH,
            ),
        )

        // Assert last bounds before maximize removed after use
        assertThat(taskRepository.removeBoundsBeforeSnapOrMaximize(task.taskId)).isNull()
        verify(desktopModeEventLogger, times(1))
            .logTaskResizingEnded(
                resizeTrigger = ResizeTrigger.MAXIMIZE_BUTTON,
                inputMethod = InputMethod.TOUCH,
                taskInfo = task,
                taskWidth = boundsBeforeMaximize.width(),
                taskHeight = boundsBeforeMaximize.height(),
                displayController = displayController,
                deskId = 0,
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_INTERACTION_DEPENDENT_TAB_TEARING_BOUNDS)
    fun onUnhandledDrag_newWindowFromTabIntent() {
        testOnUnhandledDrag(
            DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR,
            PointF(1200f, 700f),
            Rect(1100, 700, 1300, 900),
        )
    }

    @Test
    fun onUnhandledDrag_newFreeformIntentSplitLeft() {
        testOnUnhandledDrag(
            DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_LEFT_INDICATOR,
            PointF(50f, 700f),
            Rect(0, 0, 500, 1000),
        )
    }

    @Test
    fun onUnhandledDrag_newFreeformIntentSplitRight() {
        testOnUnhandledDrag(
            DesktopModeVisualIndicator.IndicatorType.TO_SPLIT_RIGHT_INDICATOR,
            PointF(2500f, 700f),
            Rect(500, 0, 1000, 1000),
        )
    }

    @Test
    fun onUnhandledDrag_newFullscreenIntent() {
        testOnUnhandledDrag(
            DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR,
            PointF(1200f, 50f),
            Rect(),
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_INTERACTION_DEPENDENT_TAB_TEARING_BOUNDS)
    fun onUnhandledDrag_crossDisplayDrag_succeedsOnFocusedFreeformTask() {
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        setUpFreeformTask(displayId = SECOND_DISPLAY, deskId = 2).apply { isFocused = true }
        testOnUnhandledDrag(
            DesktopModeVisualIndicator.IndicatorType.NO_INDICATOR,
            PointF(1200f, 700f),
            Rect(1100, 700, 1300, 900),
            destinationDisplayId = SECOND_DISPLAY,
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_INTERACTION_DEPENDENT_TAB_TEARING_BOUNDS)
    fun onUnhandledDrag_crossDisplayDrag_succeedsOnFocusedDeskRoot() {
        val task = createFreeformTask(displayId = SECOND_DISPLAY).apply { isFocused = true }
        runningTasks.add(task)
        whenever(shellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = task.taskId)
        taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = task.taskId)
        testOnUnhandledDrag(
            DesktopModeVisualIndicator.IndicatorType.NO_INDICATOR,
            PointF(1200f, 700f),
            Rect(1100, 700, 1300, 900),
            destinationDisplayId = SECOND_DISPLAY,
        )
    }

    @Test
    fun onUnhandledDrag_crossDisplayDrag_noOpOnFocusedNonDesktopTask() {
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = SECOND_DISPLAY)
        setUpFullscreenTask(displayId = SECOND_DISPLAY).apply { isFocused = true }
        testOnUnhandledDrag(
            DesktopModeVisualIndicator.IndicatorType.NO_INDICATOR,
            PointF(1200f, 700f),
            Rect(279, 700, 2122, 1852),
            destinationDisplayId = SECOND_DISPLAY,
            verifyNoOp = true,
        )
    }

    @Test
    fun shellController_registersUserChangeListener() {
        verify(shellController, times(2)).addUserChangeListener(any())
    }

    @Test
    fun onTaskInfoChanged_inImmersiveUnrequestsImmersive_exits() {
        val task = setUpFreeformTask(DEFAULT_DISPLAY)
        taskRepository.setTaskInFullImmersiveState(DEFAULT_DISPLAY, task.taskId, immersive = true)

        task.requestedVisibleTypes = WindowInsets.Type.statusBars()
        controller.onTaskInfoChanged(task)

        verify(mMockDesktopImmersiveController).moveTaskToNonImmersive(eq(task), any())
    }

    @Test
    fun onTaskInfoChanged_notInImmersiveUnrequestsImmersive_noReExit() {
        val task = setUpFreeformTask(DEFAULT_DISPLAY)
        taskRepository.setTaskInFullImmersiveState(DEFAULT_DISPLAY, task.taskId, immersive = false)

        task.requestedVisibleTypes = WindowInsets.Type.statusBars()
        controller.onTaskInfoChanged(task)

        verify(mMockDesktopImmersiveController, never()).moveTaskToNonImmersive(eq(task), any())
    }

    @Test
    fun onTaskInfoChanged_inImmersiveUnrequestsImmersive_inRecentsTransition_noExit() {
        val task = setUpFreeformTask(DEFAULT_DISPLAY)
        taskRepository.setTaskInFullImmersiveState(DEFAULT_DISPLAY, task.taskId, immersive = true)
        recentsTransitionStateListener.onTransitionStateChanged(
            TRANSITION_STATE_REQUESTED,
            DEFAULT_DISPLAY,
        )

        task.requestedVisibleTypes = WindowInsets.Type.statusBars()
        controller.onTaskInfoChanged(task)

        verify(mMockDesktopImmersiveController, never()).moveTaskToNonImmersive(eq(task), any())
    }

    @Test
    fun moveTaskToDesktop_background_attemptsImmersiveExit() =
        testScope.runTest {
            val task = setUpFreeformTask(background = true)
            val wct = WindowContainerTransaction()
            val runOnStartTransit = RunOnStartTransitionCallback()
            val transition = Binder()
            whenever(
                    mMockDesktopImmersiveController.exitImmersiveIfApplicable(
                        eq(wct),
                        eq(task.displayId),
                        eq(task.taskId),
                        any(),
                    )
                )
                .thenReturn(
                    ExitResult.Exit(exitingTask = 5, runOnTransitionStart = runOnStartTransit)
                )
            whenever(enterDesktopTransitionHandler.moveToDesktop(wct, UNKNOWN))
                .thenReturn(transition)

            controller.moveTaskToDefaultDeskAndActivate(
                taskId = task.taskId,
                wct = wct,
                transitionSource = UNKNOWN,
            )
            runCurrent()

            verify(mMockDesktopImmersiveController)
                .exitImmersiveIfApplicable(eq(wct), eq(task.displayId), eq(task.taskId), any())
            runOnStartTransit.assertOnlyInvocation(transition)
        }

    @Test
    fun moveTaskToDesktop_foreground_attemptsImmersiveExit() =
        testScope.runTest {
            val task = setUpFreeformTask(background = false)
            val wct = WindowContainerTransaction()
            val runOnStartTransit = RunOnStartTransitionCallback()
            val transition = Binder()
            whenever(
                    mMockDesktopImmersiveController.exitImmersiveIfApplicable(
                        eq(wct),
                        eq(task.displayId),
                        eq(task.taskId),
                        any(),
                    )
                )
                .thenReturn(
                    ExitResult.Exit(exitingTask = 5, runOnTransitionStart = runOnStartTransit)
                )
            whenever(enterDesktopTransitionHandler.moveToDesktop(wct, UNKNOWN))
                .thenReturn(transition)

            controller.moveTaskToDefaultDeskAndActivate(
                taskId = task.taskId,
                wct = wct,
                transitionSource = UNKNOWN,
            )
            runCurrent()

            verify(mMockDesktopImmersiveController)
                .exitImmersiveIfApplicable(eq(wct), eq(task.displayId), eq(task.taskId), any())
            runOnStartTransit.assertOnlyInvocation(transition)
        }

    @Test
    fun moveTaskToFront_background_attemptsImmersiveExit() =
        testScope.runTest {
            val task = setUpFreeformTask(background = true)
            val runOnStartTransit = RunOnStartTransitionCallback()
            val transition = Binder()
            whenever(
                    mMockDesktopImmersiveController.exitImmersiveIfApplicable(
                        any(),
                        eq(task.displayId),
                        eq(task.taskId),
                        any(),
                    )
                )
                .thenReturn(
                    ExitResult.Exit(exitingTask = 5, runOnTransitionStart = runOnStartTransit)
                )
            mockStartLaunchTransition(transition = transition)

            controller.moveTaskToFront(task.taskId, unminimizeReason = UnminimizeReason.UNKNOWN)
            runCurrent()

            verify(mMockDesktopImmersiveController)
                .exitImmersiveIfApplicable(any(), eq(task.displayId), eq(task.taskId), any())
            runOnStartTransit.assertOnlyInvocation(transition)
        }

    @Test
    fun moveTaskToFront_foreground_attemptsImmersiveExit() {
        val task = setUpFreeformTask(background = false)
        val runOnStartTransit = RunOnStartTransitionCallback()
        val transition = Binder()
        whenever(
                mMockDesktopImmersiveController.exitImmersiveIfApplicable(
                    any(),
                    eq(task.displayId),
                    eq(task.taskId),
                    any(),
                )
            )
            .thenReturn(ExitResult.Exit(exitingTask = 5, runOnTransitionStart = runOnStartTransit))
        mockStartLaunchTransition(transition = transition, taskId = task.taskId)

        controller.moveTaskToFront(task.taskId, unminimizeReason = UnminimizeReason.UNKNOWN)

        verify(mMockDesktopImmersiveController)
            .exitImmersiveIfApplicable(any(), eq(task.displayId), eq(task.taskId), any())
        runOnStartTransit.assertOnlyInvocation(transition)
    }

    @Test
    fun handleRequest_freeformLaunchToDesktop_attemptsImmersiveExit() {
        markTaskVisible(setUpFreeformTask())
        val task = setUpFreeformTask()
        markTaskVisible(task)
        val binder = Binder()

        controller.handleRequest(binder, createTransition(task))

        verify(mMockDesktopImmersiveController)
            .exitImmersiveIfApplicable(eq(binder), any(), eq(task.displayId), any())
    }

    @Test
    fun handleRequest_fullscreenLaunchToDesktop_attemptsImmersiveExit() {
        setUpFreeformTask()
        val task = createFullscreenTask()
        val binder = Binder()

        controller.handleRequest(binder, createTransition(task))

        verify(mMockDesktopImmersiveController)
            .exitImmersiveIfApplicable(eq(binder), any(), eq(task.displayId), any())
    }

    @Test
    fun handleRequest_homeTask_activeDesk_deactivates() {
        taskRepository.setActiveDesk(DEFAULT_DISPLAY, deskId = 0)
        val home = createHomeTask(DEFAULT_DISPLAY, userId = taskRepository.userId)

        val transition = Binder()
        val result = controller.handleRequest(transition, createTransition(home))

        assertNotNull(result)
        verify(desksOrganizer).deactivateDesk(result, deskId = 0)
        verify(desksTransitionsObserver)
            .addPendingTransition(
                DeskTransition.DeactivateDesk(
                    token = transition,
                    userId = taskRepository.userId,
                    deskId = 0,
                    displayId = DEFAULT_DISPLAY,
                    switchingUser = false,
                    exitReason = ExitReason.RETURN_HOME_OR_OVERVIEW,
                )
            )
    }

    @Test
    fun handleRequest_homeTask_closing_notHandled() {
        taskRepository.setActiveDesk(DEFAULT_DISPLAY, deskId = 0)
        val home = createHomeTask(DEFAULT_DISPLAY)

        val transition = Binder()
        val result = controller.handleRequest(transition, createTransition(home, TRANSIT_CLOSE))

        assertNull(result)
    }

    @Test
    fun handleRequest_homeTask_activeDesk_nonCurrentUser_deactivatesDeskOfCorrectUser() {
        val currentUserDesk = 0
        taskRepository.setActiveDesk(DEFAULT_DISPLAY, deskId = currentUserDesk)
        val otherUser = 88
        val otherUserDesk = 1
        userRepositories.getProfile(otherUser).apply {
            addDesk(DEFAULT_DISPLAY, deskId = otherUserDesk)
            setActiveDesk(DEFAULT_DISPLAY, deskId = otherUserDesk)
        }
        val home = createHomeTask(DEFAULT_DISPLAY, userId = otherUser)

        val transition = Binder()
        val result = controller.handleRequest(transition, createTransition(home))

        assertNotNull(result)
        verify(desksOrganizer).deactivateDesk(result, deskId = otherUserDesk)
        verify(desksOrganizer, never()).deactivateDesk(result, deskId = currentUserDesk)
    }

    @Test
    fun shouldPlayDesktopAnimation_notShowingDesktop_doesNotPlay() {
        taskRepository.setDeskInactive(deskId = 0)
        val triggerTask = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)
        taskRepository.setTaskInFullImmersiveState(
            displayId = triggerTask.displayId,
            taskId = triggerTask.taskId,
            immersive = true,
        )

        assertThat(
                controller.shouldPlayDesktopAnimation(
                    TransitionRequestInfo(TRANSIT_OPEN, triggerTask, /* remoteTransition= */ null)
                )
            )
            .isFalse()
    }

    @Test
    fun shouldPlayDesktopAnimation_notOpening_doesNotPlay() {
        val triggerTask = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        taskRepository.setTaskInFullImmersiveState(
            displayId = triggerTask.displayId,
            taskId = triggerTask.taskId,
            immersive = true,
        )

        assertThat(
                controller.shouldPlayDesktopAnimation(
                    TransitionRequestInfo(TRANSIT_CHANGE, triggerTask, /* remoteTransition= */ null)
                )
            )
            .isFalse()
    }

    @Test
    fun shouldPlayDesktopAnimation_notImmersive_doesNotPlay() {
        val triggerTask = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        taskRepository.setTaskInFullImmersiveState(
            displayId = triggerTask.displayId,
            taskId = triggerTask.taskId,
            immersive = false,
        )

        assertThat(
                controller.shouldPlayDesktopAnimation(
                    TransitionRequestInfo(TRANSIT_OPEN, triggerTask, /* remoteTransition= */ null)
                )
            )
            .isFalse()
    }

    @Test
    fun shouldPlayDesktopAnimation_fullscreenEntersDesktop_plays() {
        // At least one freeform task to be in a desktop.
        val existingTask = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        val triggerTask = createFullscreenTask(displayId = DEFAULT_DISPLAY)
        assertThat(controller.isAnyDeskActive(triggerTask.displayId)).isTrue()
        taskRepository.setTaskInFullImmersiveState(
            displayId = existingTask.displayId,
            taskId = existingTask.taskId,
            immersive = true,
        )

        assertThat(
                controller.shouldPlayDesktopAnimation(
                    TransitionRequestInfo(TRANSIT_OPEN, triggerTask, /* remoteTransition= */ null)
                )
            )
            .isTrue()
    }

    @Test
    fun shouldPlayDesktopAnimation_fullscreenStaysFullscreen_doesNotPlay() {
        val triggerTask = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)
        taskRepository.setDeskInactive(deskId = 0)
        assertThat(controller.isAnyDeskActive(triggerTask.displayId)).isFalse()

        assertThat(
                controller.shouldPlayDesktopAnimation(
                    TransitionRequestInfo(TRANSIT_OPEN, triggerTask, /* remoteTransition= */ null)
                )
            )
            .isFalse()
    }

    @Test
    fun shouldPlayDesktopAnimation_freeformStaysInDesktop_plays() {
        // At least one freeform task to be in a desktop.
        val existingTask = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
        val triggerTask = setUpFreeformTask(displayId = DEFAULT_DISPLAY, active = false)
        assertThat(controller.isAnyDeskActive(triggerTask.displayId)).isTrue()
        taskRepository.setTaskInFullImmersiveState(
            displayId = existingTask.displayId,
            taskId = existingTask.taskId,
            immersive = true,
        )

        assertThat(
                controller.shouldPlayDesktopAnimation(
                    TransitionRequestInfo(TRANSIT_OPEN, triggerTask, /* remoteTransition= */ null)
                )
            )
            .isTrue()
    }

    @Test
    fun shouldPlayDesktopAnimation_freeformExitsDesktop_doesNotPlay() {
        val triggerTask = setUpFreeformTask(displayId = DEFAULT_DISPLAY, active = false)
        taskRepository.setDeskInactive(deskId = 0)
        assertThat(controller.isAnyDeskActive(triggerTask.displayId)).isFalse()

        assertThat(
                controller.shouldPlayDesktopAnimation(
                    TransitionRequestInfo(TRANSIT_OPEN, triggerTask, /* remoteTransition= */ null)
                )
            )
            .isFalse()
    }

    @Test
    fun testCreateDesk() {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
        val currentDeskCount = taskRepository.getNumberOfDesks(DEFAULT_DISPLAY)
        whenever(desksOrganizer.createDesk(eq(DEFAULT_DISPLAY), any(), any())).thenAnswer {
            invocation ->
            (invocation.arguments[2] as DesksOrganizer.OnCreateCallback).onCreated(deskId = 5)
        }

        controller.createDesk(DEFAULT_DISPLAY, taskRepository.userId)

        assertThat(taskRepository.getNumberOfDesks(DEFAULT_DISPLAY)).isEqualTo(currentDeskCount + 1)
    }

    @Test
    fun testCreateDesk_invalidDisplay_dropsRequest() {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
        controller.createDesk(INVALID_DISPLAY)

        verify(desksOrganizer, never()).createDesk(any(), any(), any())
    }

    @Test
    fun testCreateDesk_systemUser_dropsRequest() {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
        assumeTrue(UserManager.isHeadlessSystemUserMode())

        controller.createDesk(DEFAULT_DISPLAY, UserHandle.USER_SYSTEM)

        verify(desksOrganizer, never()).createDesk(any(), any(), any())
    }

    @Test
    fun onDisplayDisconnect_notifiesFocusReset() {
        val transition = Binder()
        controller.onDisplayDisconnect(SECOND_DISPLAY, DEFAULT_DISPLAY, transition)
        verify(focusTransitionObserver).onDisplayDisconnected(SECOND_DISPLAY, DEFAULT_DISPLAY)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION)
    fun onDisplayDisconnect_desktopModeNotSupported_reparentsDeskTasks_focusedTaskToBottom() {
        val defaultDisplayTask = setUpFullscreenTask()
        val transition = Binder()
        taskRepository.addDesk(SECOND_DISPLAY, DISCONNECTED_DESK_ID)
        val secondDisplayTask = setUpFreeformTask(SECOND_DISPLAY)
        val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
        val wct =
            performDisplayDisconnectTransition(
                transition = transition,
                desktopSupportedOnDefaultDisplay = false,
                taskOnSecondDisplayHasFocus = true,
                defaultDisplayTask = defaultDisplayTask,
                secondDisplayTask = secondDisplayTask,
            )
        wct.assertHop(
            ReparentPredicate(
                token = secondDisplayTask.token,
                parentToken = tda.token,
                toTop = false,
            )
        )
        verify(desksOrganizer).removeDesk(any(), eq(DISCONNECTED_DESK_ID), any())
        verify(desksTransitionsObserver)
            .addPendingTransition(
                argThat {
                    this is DeskTransition.RemoveDesk &&
                        this.token == transition &&
                        this.displayId == SECOND_DISPLAY &&
                        this.deskId == DISCONNECTED_DESK_ID
                }
            )
        verify(desksTransitionsObserver)
            .addPendingTransition(
                argThat {
                    this is DeskTransition.RemoveDisplay &&
                        this.token == transition &&
                        this.displayId == SECOND_DISPLAY
                }
            )
        verify(taskSnapshotManager).takeTaskSnapshot(secondDisplayTask.taskId, true)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION)
    fun onDisplayDisconnect_desktopModeSupported_reparentsDesks_nonFocusedDeskDeactivated() {
        val defaultDisplayTask = setUpFreeformTask()
        val transition = Binder()
        taskRepository.addDesk(SECOND_DISPLAY, DISCONNECTED_DESK_ID)
        taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = DISCONNECTED_DESK_ID)
        val secondDisplayTask = setUpFreeformTask(SECOND_DISPLAY, deskId = DISCONNECTED_DESK_ID)

        performDisplayDisconnectTransition(
            transition = transition,
            desktopSupportedOnDefaultDisplay = true,
            taskOnSecondDisplayHasFocus = false,
            defaultDisplayTask = defaultDisplayTask,
            secondDisplayTask = secondDisplayTask,
        )

        verify(desksOrganizer)
            .moveDeskToDisplay(any(), eq(DISCONNECTED_DESK_ID), eq(DEFAULT_DISPLAY), eq(false))
        verify(desksTransitionsObserver)
            .addPendingTransition(
                argThat {
                    this is DeskTransition.ChangeDeskDisplay &&
                        this.token == transition &&
                        this.displayId == DEFAULT_DISPLAY &&
                        this.deskId == DISCONNECTED_DESK_ID
                }
            )
        verify(desksOrganizer)
            .deactivateDesk(any(), eq(DISCONNECTED_DESK_ID), skipReorder = eq(false))
        verify(desksTransitionsObserver)
            .addPendingTransition(
                argThat {
                    this is DeskTransition.DeactivateDesk &&
                        this.token == transition &&
                        this.deskId == DISCONNECTED_DESK_ID
                }
            )
        verify(taskSnapshotManager).takeTaskSnapshot(secondDisplayTask.taskId, true)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION)
    fun onDisplayDisconnect_desktopModeSupported_reparentsDesks_focusedDeskActivated() {
        val defaultDisplayTask = setUpFreeformTask()
        val transition = Binder()
        taskRepository.addDesk(SECOND_DISPLAY, DISCONNECTED_DESK_ID)
        taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = DISCONNECTED_DESK_ID)
        val secondDisplayTask = setUpFreeformTask(SECOND_DISPLAY, deskId = DISCONNECTED_DESK_ID)

        performDisplayDisconnectTransition(
            transition = transition,
            desktopSupportedOnDefaultDisplay = true,
            taskOnSecondDisplayHasFocus = true,
            defaultDisplayTask = defaultDisplayTask,
            secondDisplayTask = secondDisplayTask,
        )

        verify(desksOrganizer)
            .moveDeskToDisplay(any(), eq(DISCONNECTED_DESK_ID), eq(DEFAULT_DISPLAY), eq(true))
        verify(desksTransitionsObserver)
            .addPendingTransition(
                argThat {
                    this is DeskTransition.ChangeDeskDisplay &&
                        this.token == transition &&
                        this.displayId == DEFAULT_DISPLAY &&
                        this.deskId == DISCONNECTED_DESK_ID
                }
            )
        verify(desksOrganizer)
            .activateDesk(any(), eq(DISCONNECTED_DESK_ID), skipReorder = eq(false))
        verify(desksTransitionsObserver)
            .addPendingTransition(
                argThat {
                    this is DeskTransition.ActivateDesk &&
                        this.token == transition &&
                        this.displayId == DEFAULT_DISPLAY &&
                        this.deskId == DISCONNECTED_DESK_ID
                }
            )
        verify(taskSnapshotManager).takeTaskSnapshot(secondDisplayTask.taskId, true)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION)
    fun onDisplayDisconnect_desktopModeSupported_emptyDeskRemoved() {
        val defaultDisplayTask = setUpFreeformTask()
        val transition = Binder()
        val secondDisplayLayout = mock(DisplayLayout::class.java)
        whenever(displayController.getDisplayLayout(SECOND_DISPLAY)).thenReturn(secondDisplayLayout)
        taskRepository.addDesk(SECOND_DISPLAY, DISCONNECTED_DESK_ID)
        taskRepository.setActiveDesk(SECOND_DISPLAY, DISCONNECTED_DESK_ID)
        val wctCaptor = argumentCaptor<WindowContainerTransaction>()
        whenever(desksOrganizer.removeDesk(wctCaptor.capture(), anyInt(), anyInt())).thenAnswer {
            wctCaptor.firstValue.removeTask(mock(WindowContainerToken::class.java))
        }

        performDisplayDisconnectTransition(
            transition = transition,
            desktopSupportedOnDefaultDisplay = true,
            taskOnSecondDisplayHasFocus = false,
            defaultDisplayTask = defaultDisplayTask,
            secondDisplayTask = null,
        )

        verify(desksOrganizer).removeDesk(any(), eq(DISCONNECTED_DESK_ID), anyInt())
        verify(desksTransitionsObserver)
            .addPendingTransition(
                argThat {
                    this is DeskTransition.RemoveDesk &&
                        this.token == transition &&
                        this.displayId == SECOND_DISPLAY &&
                        this.deskId == DISCONNECTED_DESK_ID
                }
            )
        verify(taskSnapshotManager, never()).takeTaskSnapshot(any(), any())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION)
    fun onDisplayDisconnect_desktopModeSupported_taskResizedBasedOnDensity() {
        val defaultDisplayTask = setUpFreeformTask()
        val transition = Binder()
        val secondDisplayLayout = mock(DisplayLayout::class.java)
        whenever(displayController.getDisplayLayout(SECOND_DISPLAY)).thenReturn(secondDisplayLayout)
        whenever(secondDisplayLayout.height()).thenReturn(1600)
        whenever(secondDisplayLayout.width()).thenReturn(2560)
        whenever(secondDisplayLayout.densityDpi()).thenReturn(240)
        whenever(secondDisplayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(Rect(0, 0, 2560, 1600))
        }
        taskRepository.addDesk(SECOND_DISPLAY, DISCONNECTED_DESK_ID)
        taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = DISCONNECTED_DESK_ID)
        val secondDisplayTask =
            setUpFreeformTask(
                    displayId = SECOND_DISPLAY,
                    deskId = DISCONNECTED_DESK_ID,
                    bounds = Rect(100, 100, 500, 500),
                )
                .apply { isResizeable = true }

        val wct =
            performDisplayDisconnectTransition(
                transition = transition,
                desktopSupportedOnDefaultDisplay = true,
                taskOnSecondDisplayHasFocus = true,
                defaultDisplayTask = defaultDisplayTask,
                secondDisplayTask = secondDisplayTask,
            )
        assertThat(findBoundsChange(wct, secondDisplayTask)).isEqualTo(Rect(33, 61, 299, 327))
        verify(taskSnapshotManager).takeTaskSnapshot(secondDisplayTask.taskId, true)
    }

    private fun performDisplayDisconnectTransition(
        transition: IBinder,
        desktopSupportedOnDefaultDisplay: Boolean,
        taskOnSecondDisplayHasFocus: Boolean,
        defaultDisplayTask: RunningTaskInfo,
        secondDisplayTask: RunningTaskInfo?,
    ): WindowContainerTransaction {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] =
            desktopSupportedOnDefaultDisplay
        val displayChange = TransitionRequestInfo.DisplayChange(SECOND_DISPLAY)
        displayChange.disconnectReparentDisplay = DEFAULT_DISPLAY
        val transitionRequestInfo =
            TransitionRequestInfo(
                    TRANSIT_CLOSE,
                    /* triggerTask = */ null,
                    /* remoteTransition= */ null,
                )
                .apply { setDisplayChanges(listOf(displayChange)) }
        val focusedTaskId =
            if (taskOnSecondDisplayHasFocus) {
                secondDisplayTask?.taskId ?: error("Cannot have a focused null task")
            } else {
                defaultDisplayTask.taskId
            }
        whenever(focusTransitionObserver.globallyFocusedTaskId).thenReturn(focusedTaskId)
        whenever(fullscreenDisconnectHandler.onDisplayDisconnect(anyInt(), anyInt()))
            .thenReturn(WindowContainerTransaction())
        var preserveRequested = false
        controller.preserveDisplayRequestHandler = PreserveDisplayRequestHandler {
            preserveRequested = true
        }

        val wct =
            assertNotNull(
                displayDisconnectTransitionHandler.handleRequest(transition, transitionRequestInfo)
            )
        assertThat(preserveRequested).isTrue()

        return wct
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
        Flags.FLAG_ENABLE_DISPLAY_RECONNECT_INTERACTION,
    )
    fun restoreDisplay_restoresTasksWithCorrectBounds() =
        testScope.runTest {
            taskRepository.addDesk(SECOND_DISPLAY, DISCONNECTED_DESK_ID)
            taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = DISCONNECTED_DESK_ID)
            val firstTaskBounds = Rect(100, 300, 1000, 1200)
            val firstTask =
                setUpFreeformTask(
                    displayId = SECOND_DISPLAY,
                    deskId = DISCONNECTED_DESK_ID,
                    bounds = firstTaskBounds,
                )
            val secondTaskBounds = Rect(400, 400, 1600, 900)
            val secondTask =
                setUpFreeformTask(
                    displayId = SECOND_DISPLAY,
                    deskId = DISCONNECTED_DESK_ID,
                    bounds = secondTaskBounds,
                )
            val wctCaptor = argumentCaptor<WindowContainerTransaction>()
            taskRepository.preserveDisplay(SECOND_DISPLAY, SECOND_DISPLAY_UNIQUE_ID)
            taskRepository.onDeskDisplayChanged(
                DISCONNECTED_DESK_ID,
                DEFAULT_DISPLAY,
                DEFAULT_DISPLAY_UNIQUE_ID,
            )
            taskRepository.setActiveDesk(DEFAULT_DISPLAY, DISCONNECTED_DESK_ID)
            whenever(desksOrganizer.createDesk(eq(SECOND_DISPLAY_ON_RECONNECT), any(), any()))
                .thenAnswer { invocation ->
                    (invocation.arguments[2] as DesksOrganizer.OnCreateCallback).onCreated(
                        deskId = 5
                    )
                }
            val transition = Binder()
            whenever(transitions.startTransition(eq(TRANSIT_CHANGE), any(), anyOrNull()))
                .thenReturn(transition)
            val preservedDisplay =
                taskRepository.removePreservedDisplay(SECOND_DISPLAY_UNIQUE_ID)
                    ?: fail("Expected to find preserved display.")

            controller.restoreDisplay(
                displayId = SECOND_DISPLAY_ON_RECONNECT,
                preservedDisplay = preservedDisplay,
                userId = taskRepository.userId,
            )
            runCurrent()
            testScopeImmediate.runCurrent()

            verify(transitions).startTransition(anyInt(), wctCaptor.capture(), anyOrNull())
            val wct = wctCaptor.firstValue
            assertThat(findBoundsChange(wct, firstTask)).isEqualTo(firstTaskBounds)
            assertThat(findBoundsChange(wct, secondTask)).isEqualTo(secondTaskBounds)
            verify(desksOrganizer).moveTaskToDesk(any(), anyInt(), eq(firstTask), eq(false))
            verify(desksOrganizer).moveTaskToDesk(any(), anyInt(), eq(secondTask), eq(false))
            val deskIds = taskRepository.getDeskIds(SECOND_DISPLAY_ON_RECONNECT)
            assertThat(deskIds.size).isEqualTo(1)
            verify(desksTransitionsObserver)
                .addPendingTransition(
                    DeskTransition.AddTaskToDesk(
                        token = transition,
                        displayId = SECOND_DISPLAY_ON_RECONNECT,
                        deskId = 5,
                        taskId = firstTask.taskId,
                        userId = taskRepository.userId,
                        taskBounds = firstTaskBounds,
                        minimized = false,
                    )
                )
            verify(desksTransitionsObserver)
                .addPendingTransition(
                    DeskTransition.AddTaskToDesk(
                        token = transition,
                        displayId = SECOND_DISPLAY_ON_RECONNECT,
                        deskId = 5,
                        taskId = secondTask.taskId,
                        userId = taskRepository.userId,
                        taskBounds = secondTaskBounds,
                        minimized = false,
                    )
                )
        }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
        Flags.FLAG_ENABLE_DISPLAY_RECONNECT_INTERACTION,
    )
    fun restoreDisplay_restoresMinimizedTask() =
        testScope.runTest {
            taskRepository.addDesk(SECOND_DISPLAY, DISCONNECTED_DESK_ID)
            taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = DISCONNECTED_DESK_ID)
            val firstTaskBounds = Rect(100, 300, 1000, 1200)
            val firstTask =
                setUpFreeformTask(
                    displayId = SECOND_DISPLAY,
                    deskId = DISCONNECTED_DESK_ID,
                    bounds = firstTaskBounds,
                )
            taskRepository.minimizeTaskInDesk(
                SECOND_DISPLAY,
                DISCONNECTED_DESK_ID,
                firstTask.taskId,
            )
            val wctCaptor = argumentCaptor<WindowContainerTransaction>()
            taskRepository.preserveDisplay(SECOND_DISPLAY, SECOND_DISPLAY_UNIQUE_ID)
            taskRepository.onDeskDisplayChanged(
                DISCONNECTED_DESK_ID,
                DEFAULT_DISPLAY,
                DEFAULT_DISPLAY_UNIQUE_ID,
            )
            taskRepository.setActiveDesk(DEFAULT_DISPLAY, DISCONNECTED_DESK_ID)
            whenever(desksOrganizer.createDesk(eq(SECOND_DISPLAY_ON_RECONNECT), any(), any()))
                .thenAnswer { invocation ->
                    (invocation.arguments[2] as DesksOrganizer.OnCreateCallback).onCreated(
                        deskId = 5
                    )
                }
            val transition = Binder()
            whenever(transitions.startTransition(eq(TRANSIT_CHANGE), any(), anyOrNull()))
                .thenReturn(transition)
            val preservedDisplay =
                taskRepository.removePreservedDisplay(SECOND_DISPLAY_UNIQUE_ID)
                    ?: fail("Expected to find preserved display.")
            controller.restoreDisplay(
                displayId = SECOND_DISPLAY_ON_RECONNECT,
                preservedDisplay = preservedDisplay,
                userId = taskRepository.userId,
            )
            runCurrent()
            testScopeImmediate.runCurrent()

            verify(transitions).startTransition(anyInt(), wctCaptor.capture(), anyOrNull())
            verify(desksTransitionsObserver)
                .addPendingTransition(
                    DeskTransition.AddTaskToDesk(
                        token = transition,
                        displayId = SECOND_DISPLAY_ON_RECONNECT,
                        deskId = 5,
                        taskId = firstTask.taskId,
                        userId = taskRepository.userId,
                        taskBounds = firstTaskBounds,
                        minimized = true,
                    )
                )
        }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
        Flags.FLAG_ENABLE_DISPLAY_RECONNECT_INTERACTION,
    )
    fun restoreDisplay_globalFocusedTaskOnDefaultDisplay_retainsFocus() =
        testScope.runTest {
            val defaultDisplayTask = setUpFreeformTask()
            whenever(focusTransitionObserver.globallyFocusedTaskId)
                .thenReturn(defaultDisplayTask.taskId)
            taskRepository.addDesk(SECOND_DISPLAY, DISCONNECTED_DESK_ID)
            taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = DISCONNECTED_DESK_ID)

            val firstTaskBounds = Rect(100, 300, 1000, 1200)
            val firstTask =
                setUpFreeformTask(
                    displayId = SECOND_DISPLAY,
                    deskId = DISCONNECTED_DESK_ID,
                    bounds = firstTaskBounds,
                )
            val secondTaskBounds = Rect(400, 400, 1600, 900)
            val secondTask =
                setUpFreeformTask(
                    displayId = SECOND_DISPLAY,
                    deskId = DISCONNECTED_DESK_ID,
                    bounds = secondTaskBounds,
                )
            val wctCaptor = argumentCaptor<WindowContainerTransaction>()
            taskRepository.preserveDisplay(SECOND_DISPLAY, SECOND_DISPLAY_UNIQUE_ID)
            taskRepository.onDeskDisplayChanged(
                DISCONNECTED_DESK_ID,
                DEFAULT_DISPLAY,
                DEFAULT_DISPLAY_UNIQUE_ID,
            )
            taskRepository.setActiveDesk(DEFAULT_DISPLAY, DISCONNECTED_DESK_ID)
            whenever(desksOrganizer.createDesk(eq(SECOND_DISPLAY_ON_RECONNECT), any(), any()))
                .thenAnswer { invocation ->
                    (invocation.arguments[2] as DesksOrganizer.OnCreateCallback).onCreated(
                        deskId = 5
                    )
                }
            val transition = Binder()
            whenever(transitions.startTransition(eq(TRANSIT_CHANGE), any(), anyOrNull()))
                .thenReturn(transition)
            val preservedDisplay =
                taskRepository.removePreservedDisplay(SECOND_DISPLAY_UNIQUE_ID)
                    ?: fail("Expected to find preserved display.")

            controller.restoreDisplay(
                displayId = SECOND_DISPLAY_ON_RECONNECT,
                preservedDisplay = preservedDisplay,
                userId = taskRepository.userId,
            )
            runCurrent()
            testScopeImmediate.runCurrent()

            verify(transitions).startTransition(anyInt(), wctCaptor.capture(), anyOrNull())
            val wct = wctCaptor.firstValue
            wct.assertReorder(defaultDisplayTask.token, toTop = true, includingParents = true)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
        Flags.FLAG_ENABLE_DISPLAY_RECONNECT_INTERACTION,
    )
    fun restoreDisplay_globalFocusedTaskRestoredToActiveDesk_retainsFocus() =
        testScope.runTest {
            val defaultDisplayTask = setUpFreeformTask()
            taskRepository.addDesk(SECOND_DISPLAY, DISCONNECTED_DESK_ID)
            taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = DISCONNECTED_DESK_ID)

            val firstTaskBounds = Rect(100, 300, 1000, 1200)
            val firstTask =
                setUpFreeformTask(
                    displayId = SECOND_DISPLAY,
                    deskId = DISCONNECTED_DESK_ID,
                    bounds = firstTaskBounds,
                )
            val secondTaskBounds = Rect(400, 400, 1600, 900)
            val secondTask =
                setUpFreeformTask(
                    displayId = SECOND_DISPLAY,
                    deskId = DISCONNECTED_DESK_ID,
                    bounds = secondTaskBounds,
                )
            whenever(focusTransitionObserver.globallyFocusedTaskId).thenReturn(secondTask.taskId)
            val wctCaptor = argumentCaptor<WindowContainerTransaction>()
            taskRepository.preserveDisplay(SECOND_DISPLAY, SECOND_DISPLAY_UNIQUE_ID)
            taskRepository.onDeskDisplayChanged(
                DISCONNECTED_DESK_ID,
                DEFAULT_DISPLAY,
                DEFAULT_DISPLAY_UNIQUE_ID,
            )
            taskRepository.setActiveDesk(DEFAULT_DISPLAY, DISCONNECTED_DESK_ID)
            whenever(desksOrganizer.createDesk(eq(SECOND_DISPLAY_ON_RECONNECT), any(), any()))
                .thenAnswer { invocation ->
                    (invocation.arguments[2] as DesksOrganizer.OnCreateCallback).onCreated(
                        deskId = 5
                    )
                }
            val transition = Binder()
            whenever(transitions.startTransition(eq(TRANSIT_CHANGE), any(), anyOrNull()))
                .thenReturn(transition)
            val preservedDisplay =
                taskRepository.removePreservedDisplay(SECOND_DISPLAY_UNIQUE_ID)
                    ?: fail("Expected to find preserved display.")

            controller.restoreDisplay(
                displayId = SECOND_DISPLAY_ON_RECONNECT,
                preservedDisplay = preservedDisplay,
                userId = taskRepository.userId,
            )
            runCurrent()
            testScopeImmediate.runCurrent()

            verify(transitions).startTransition(anyInt(), wctCaptor.capture(), anyOrNull())
            val wct = wctCaptor.firstValue
            wct.assertReorder(secondTask.token, toTop = true, includingParents = true)
        }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
        Flags.FLAG_ENABLE_DISPLAY_RECONNECT_INTERACTION,
    )
    fun restoreDisplay_globalFocusedTaskRestoredToInactiveDesk_losesFocus() =
        testScope.runTest {
            val defaultDisplayTask = setUpFreeformTask()
            taskRepository.addDesk(SECOND_DISPLAY, DISCONNECTED_DESK_ID)

            val firstTaskBounds = Rect(100, 300, 1000, 1200)
            val firstTask =
                setUpFreeformTask(
                    displayId = SECOND_DISPLAY,
                    deskId = DISCONNECTED_DESK_ID,
                    bounds = firstTaskBounds,
                )
            val secondTaskBounds = Rect(400, 400, 1600, 900)
            val secondTask =
                setUpFreeformTask(
                    displayId = SECOND_DISPLAY,
                    deskId = DISCONNECTED_DESK_ID,
                    bounds = secondTaskBounds,
                )
            whenever(focusTransitionObserver.globallyFocusedTaskId).thenReturn(secondTask.taskId)
            val wctCaptor = argumentCaptor<WindowContainerTransaction>()
            taskRepository.preserveDisplay(SECOND_DISPLAY, SECOND_DISPLAY_UNIQUE_ID)
            taskRepository.onDeskDisplayChanged(
                DISCONNECTED_DESK_ID,
                DEFAULT_DISPLAY,
                DEFAULT_DISPLAY_UNIQUE_ID,
            )
            taskRepository.setActiveDesk(DEFAULT_DISPLAY, DISCONNECTED_DESK_ID)
            whenever(desksOrganizer.createDesk(eq(SECOND_DISPLAY_ON_RECONNECT), any(), any()))
                .thenAnswer { invocation ->
                    (invocation.arguments[2] as DesksOrganizer.OnCreateCallback).onCreated(
                        deskId = 5
                    )
                }
            val transition = Binder()
            whenever(transitions.startTransition(eq(TRANSIT_CHANGE), any(), anyOrNull()))
                .thenReturn(transition)
            val preservedDisplay =
                taskRepository.removePreservedDisplay(SECOND_DISPLAY_UNIQUE_ID)
                    ?: fail("Expected to find preserved display.")

            controller.restoreDisplay(
                displayId = SECOND_DISPLAY_ON_RECONNECT,
                preservedDisplay = preservedDisplay,
                userId = taskRepository.userId,
            )
            testScopeImmediate.runCurrent()

            verify(transitions).startTransition(anyInt(), wctCaptor.capture(), anyOrNull())
            val wct = wctCaptor.firstValue
            wct.assertWithoutHop { hop ->
                hop.type == HIERARCHY_OP_TYPE_REORDER &&
                    hop.container == secondTask.token.asBinder() &&
                    hop.toTop &&
                    hop.includingParents()
            }
        }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
        Flags.FLAG_ENABLE_DISPLAY_RECONNECT_INTERACTION,
    )
    fun restoreDisplay_allTasksRestoredFromOldInactiveDesk_removesOldDesk() =
        testScope.runTest {
            val oldDeskId = 10
            val taskId = 201
            // Set up pre-disconnect state; desk + task
            taskRepository.addDesk(SECOND_DISPLAY, oldDeskId)
            setUpFreeformTask(displayId = SECOND_DISPLAY, deskId = oldDeskId, taskId = taskId)
            // Set up post-disconnect state; display preserved + desk moved; disconnected desk does
            // not become active
            taskRepository.preserveDisplay(SECOND_DISPLAY, SECOND_DISPLAY_UNIQUE_ID)
            taskRepository.onDeskDisplayChanged(
                oldDeskId,
                DEFAULT_DISPLAY,
                DEFAULT_DISPLAY_UNIQUE_ID,
            )
            val transition = Binder()
            whenever(transitions.startTransition(eq(TRANSIT_CHANGE), any(), anyOrNull()))
                .thenReturn(transition)
            whenever(desksOrganizer.createDesk(eq(SECOND_DISPLAY_ON_RECONNECT), any(), any()))
                .thenAnswer { invocation ->
                    (invocation.arguments[2] as DesksOrganizer.OnCreateCallback).onCreated(
                        deskId = 5
                    )
                }
            val preservedDisplay =
                taskRepository.removePreservedDisplay(SECOND_DISPLAY_UNIQUE_ID)
                    ?: fail("Expected preserved display")
            taskRepository.setActiveDesk(DEFAULT_DISPLAY, deskId = 0)

            controller.restoreDisplay(
                displayId = SECOND_DISPLAY_ON_RECONNECT,
                preservedDisplay = preservedDisplay,
                userId = taskRepository.userId,
            )
            testScopeImmediate.runCurrent()

            verify(desksOrganizer).removeDesk(any(), eq(oldDeskId), eq(taskRepository.userId))
        }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
        Flags.FLAG_ENABLE_DISPLAY_RECONNECT_INTERACTION,
    )
    fun restoreDisplay_allTasksRestoredFromOldActiveDesk_oldDeskNotRemoved() =
        testScope.runTest {
            val oldDeskId = 10
            val taskId = 201
            // Set up pre-disconnect state; desk + task
            taskRepository.addDesk(SECOND_DISPLAY, oldDeskId)
            setUpFreeformTask(displayId = SECOND_DISPLAY, deskId = oldDeskId, taskId = taskId)
            // Set up post-disconnect state; display preserved + desk moved; disconnected desk is
            // now active
            taskRepository.preserveDisplay(SECOND_DISPLAY, SECOND_DISPLAY_UNIQUE_ID)
            taskRepository.onDeskDisplayChanged(
                oldDeskId,
                DEFAULT_DISPLAY,
                DEFAULT_DISPLAY_UNIQUE_ID,
            )
            taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = oldDeskId)
            val transition = Binder()
            whenever(transitions.startTransition(eq(TRANSIT_CHANGE), any(), anyOrNull()))
                .thenReturn(transition)
            whenever(desksOrganizer.createDesk(eq(SECOND_DISPLAY_ON_RECONNECT), any(), any()))
                .thenAnswer { invocation ->
                    (invocation.arguments[2] as DesksOrganizer.OnCreateCallback).onCreated(
                        deskId = 5
                    )
                }
            val preservedDisplay =
                taskRepository.removePreservedDisplay(SECOND_DISPLAY_UNIQUE_ID)
                    ?: fail("Expected preserved display")

            controller.restoreDisplay(
                displayId = SECOND_DISPLAY_ON_RECONNECT,
                preservedDisplay = preservedDisplay,
                userId = taskRepository.userId,
            )
            testScopeImmediate.runCurrent()

            verify(desksOrganizer, never())
                .removeDesk(any(), eq(oldDeskId), eq(taskRepository.userId))
        }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
        Flags.FLAG_ENABLE_DISPLAY_RECONNECT_INTERACTION,
    )
    fun restoreTaskToDisplay_withPipTaskOnExtendedDevice_schedulesExitPip() =
        testScope.runTest {
            // Extended device
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
            taskRepository.addDesk(SECOND_DISPLAY, DISCONNECTED_DESK_ID)
            taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = DISCONNECTED_DESK_ID)

            val taskBounds = Rect(400, 400, 1600, 900)
            val deskTask =
                setUpFreeformTask(
                    displayId = SECOND_DISPLAY,
                    deskId = DISCONNECTED_DESK_ID,
                    bounds = taskBounds,
                )
            // Disconnect, preserve display.
            taskRepository.preserveDisplay(SECOND_DISPLAY, SECOND_DISPLAY_UNIQUE_ID)
            taskRepository.onDeskDisplayChanged(
                DISCONNECTED_DESK_ID,
                DEFAULT_DISPLAY,
                DEFAULT_DISPLAY_UNIQUE_ID,
            )
            whenever(desksOrganizer.createDesk(eq(SECOND_DISPLAY_ON_RECONNECT), any(), any()))
                .thenAnswer { invocation ->
                    (invocation.arguments[2] as DesksOrganizer.OnCreateCallback).onCreated(
                        deskId = 5
                    )
                }
            val transition = Binder()
            whenever(transitions.startTransition(eq(TRANSIT_CHANGE), any(), anyOrNull()))
                .thenReturn(transition)
            val preservedDisplay =
                taskRepository.removePreservedDisplay(SECOND_DISPLAY_UNIQUE_ID)
                    ?: fail("Expected to find preserved display.")
            // Move task to pip post-disconnect.
            whenever(pipTransitionState.pipTaskInfo).thenReturn(deskTask)

            controller.restoreDisplay(
                displayId = SECOND_DISPLAY_ON_RECONNECT,
                preservedDisplay = preservedDisplay,
                userId = taskRepository.userId,
            )
            testScopeImmediate.runCurrent()

            verify(pipScheduler).scheduleExitPipViaExpand(eq(true), eq(SECOND_DISPLAY_ON_RECONNECT))
        }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
        Flags.FLAG_ENABLE_DISPLAY_RECONNECT_INTERACTION,
    )
    fun restoreTaskToDisplay_withPipTaskOnProjectedDevice_doesNotExitPip() =
        testScope.runTest {
            // Projected device
            desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = false
            taskRepository.addDesk(SECOND_DISPLAY, DISCONNECTED_DESK_ID)
            taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = DISCONNECTED_DESK_ID)

            val taskBounds = Rect(400, 400, 1600, 900)
            val deskTask =
                setUpFreeformTask(
                    displayId = SECOND_DISPLAY,
                    deskId = DISCONNECTED_DESK_ID,
                    bounds = taskBounds,
                )
            // Disconnect, preserve display.
            taskRepository.preserveDisplay(SECOND_DISPLAY, SECOND_DISPLAY_UNIQUE_ID)
            taskRepository.onDeskDisplayChanged(
                DISCONNECTED_DESK_ID,
                DEFAULT_DISPLAY,
                DEFAULT_DISPLAY_UNIQUE_ID,
            )
            whenever(desksOrganizer.createDesk(eq(SECOND_DISPLAY_ON_RECONNECT), any(), any()))
                .thenAnswer { invocation ->
                    (invocation.arguments[2] as DesksOrganizer.OnCreateCallback).onCreated(
                        deskId = 5
                    )
                }
            val transition = Binder()
            whenever(transitions.startTransition(eq(TRANSIT_CHANGE), any(), anyOrNull()))
                .thenReturn(transition)
            val preservedDisplay =
                taskRepository.removePreservedDisplay(SECOND_DISPLAY_UNIQUE_ID)
                    ?: fail("Expected to find preserved display.")
            // Move task to pip post-disconnect.
            whenever(pipTransitionState.pipTaskInfo).thenReturn(deskTask)

            controller.restoreDisplay(
                displayId = SECOND_DISPLAY_ON_RECONNECT,
                preservedDisplay = preservedDisplay,
                userId = taskRepository.userId,
            )
            testScopeImmediate.runCurrent()

            verify(pipScheduler, never())
                .scheduleExitPipViaExpand(eq(true), eq(SECOND_DISPLAY_ON_RECONNECT))
        }

    @Test
    fun startLaunchTransition_notifiesSnapEventHandler() {
        val deskId = 7
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = deskId)
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = deskId)

        controller.startLaunchTransition(
            transitionType = TRANSIT_OPEN,
            wct = WindowContainerTransaction(),
            launchingTaskId = null,
            deskId = deskId,
            displayId = DEFAULT_DISPLAY,
            userId = taskRepository.userId,
        )

        verify(snapEventHandler).onTaskLaunchStarted()
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER,
        Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING,
    )
    fun startLaunchTransition_desktopNotShowing_movesWallpaperToFront() {
        taskRepository.setDeskInactive(deskId = 0)
        val launchingTask = createFreeformTask(displayId = DEFAULT_DISPLAY)
        val wct = WindowContainerTransaction()
        wct.reorder(launchingTask.token, /* onTop= */ true)
        mockStartLaunchTransition(transitionType = TRANSIT_OPEN)

        controller.startLaunchTransition(
            transitionType = TRANSIT_OPEN,
            wct = wct,
            launchingTaskId = null,
            deskId = 0,
            displayId = DEFAULT_DISPLAY,
            userId = taskRepository.userId,
        )

        val latestWct = getLatestDesktopMixedTaskWct(type = TRANSIT_OPEN)
        val launchingTaskReorderIndex = latestWct.indexOfReorder(launchingTask, toTop = true)
        val wallpaperReorderIndex = latestWct.indexOfReorder(wallpaperToken, toTop = true)
        assertThat(launchingTaskReorderIndex).isNotEqualTo(-1)
        assertThat(wallpaperReorderIndex).isNotEqualTo(-1)
        assertThat(launchingTaskReorderIndex).isGreaterThan(wallpaperReorderIndex)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER,
    )
    fun startLaunchTransition_desktopNotShowing_updatesDesktopRemoteListener() {
        setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = 0)
        taskRepository.setDeskInactive(deskId = 0)

        controller.startLaunchTransition(
            transitionType = TRANSIT_OPEN,
            wct = WindowContainerTransaction(),
            launchingTaskId = null,
            deskId = 0,
            displayId = DEFAULT_DISPLAY,
            userId = taskRepository.userId,
        )

        verify(desktopRemoteListener).onEnterDesktopModeTransitionStarted(any())
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER,
    )
    fun startLaunchTransition_launchingTaskFromInactiveDesk_otherDeskActive_activatesDesk() {
        val activeDeskId = 4
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = activeDeskId)
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = activeDeskId)
        val inactiveDesk = 5
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = inactiveDesk)
        val launchingTask = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = inactiveDesk)
        val transition = Binder()
        whenever(desktopMixedTransitionHandler.startSwitchDeskTransition(eq(TRANSIT_OPEN), any()))
            .thenReturn(transition)

        val wct = WindowContainerTransaction()
        controller.startLaunchTransition(
            transitionType = TRANSIT_OPEN,
            wct = wct,
            launchingTaskId = launchingTask.taskId,
            deskId = inactiveDesk,
            displayId = DEFAULT_DISPLAY,
            userId = taskRepository.userId,
        )

        verify(desksOrganizer).activateDesk(any(), eq(inactiveDesk), skipReorder = eq(false))
        verify(desksTransitionsObserver)
            .addPendingTransition(
                DeskTransition.ActivateDesk(
                    token = transition,
                    userId = taskRepository.userId,
                    displayId = DEFAULT_DISPLAY,
                    deskId = inactiveDesk,
                    enterReason = EnterReason.APP_FREEFORM_INTENT,
                )
            )
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
        Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER,
    )
    fun startLaunchTransition_desktopShowing_doesNotReorderWallpaper() {
        val wct = WindowContainerTransaction()
        mockStartLaunchTransition(transitionType = TRANSIT_OPEN)

        setUpFreeformTask(deskId = 0, displayId = DEFAULT_DISPLAY)
        controller.startLaunchTransition(
            transitionType = TRANSIT_OPEN,
            wct = wct,
            launchingTaskId = null,
            deskId = 0,
            displayId = DEFAULT_DISPLAY,
            userId = taskRepository.userId,
        )

        val latestWct = getLatestDesktopMixedTaskWct(type = TRANSIT_OPEN)
        assertNull(latestWct.hierarchyOps.find { op -> op.container == wallpaperToken.asBinder() })
    }

    @Test
    @EnableFlags(Flags.FLAG_UPDATE_DESKTOP_SCRIM_ON_DESKTOP_TASK_LAUNCH)
    fun startLaunchTransition_maximizedTaskBounds_desktopScrimIsApplied() {
        val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
        val task = setUpFreeformTask(bounds = stableBounds)

        controller.startLaunchTransition(
            transitionType = TRANSIT_OPEN,
            wct = WindowContainerTransaction(),
            launchingTaskId = task.taskId,
            deskId = DEFAULT_DISPLAY,
            displayId = DEFAULT_DISPLAY,
            userId = DEFAULT_USER_ID,
            requestedTaskBounds = null,
            flexibleLaunchSize = false,
        )

        verify(desktopRemoteListener).onTaskbarCornerRoundingUpdate(true, DEFAULT_DISPLAY)
    }

    @Test
    @EnableFlags(Flags.FLAG_UPDATE_DESKTOP_SCRIM_ON_DESKTOP_TASK_LAUNCH)
    fun startLaunchTransition_unmaximizedTaskBounds_desktopScrimIsNotApplied() {
        val task = setUpFreeformTask(bounds = TASK_BOUNDS)

        controller.startLaunchTransition(
            transitionType = TRANSIT_OPEN,
            wct = WindowContainerTransaction(),
            launchingTaskId = task.taskId,
            deskId = DEFAULT_DISPLAY,
            displayId = DEFAULT_DISPLAY,
            userId = DEFAULT_USER_ID,
            requestedTaskBounds = null,
            flexibleLaunchSize = false,
        )

        verify(desktopRemoteListener).onTaskbarCornerRoundingUpdate(false, DEFAULT_DISPLAY)
    }

    @Test
    @EnableFlags(Flags.FLAG_UPDATE_DESKTOP_SCRIM_ON_DESKTOP_TASK_LAUNCH)
    fun startLaunchTransition_maximizedRequestedTaskBounds_desktopScrimIsApplied() {
        val task = setUpFreeformTask(bounds = TASK_BOUNDS)

        val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
        controller.startLaunchTransition(
            transitionType = TRANSIT_OPEN,
            wct = WindowContainerTransaction(),
            launchingTaskId = task.taskId,
            deskId = DEFAULT_DISPLAY,
            displayId = DEFAULT_DISPLAY,
            userId = DEFAULT_USER_ID,
            requestedTaskBounds = stableBounds,
            flexibleLaunchSize = false,
        )

        verify(desktopRemoteListener).onTaskbarCornerRoundingUpdate(true, DEFAULT_DISPLAY)
    }

    @Test
    @EnableFlags(Flags.FLAG_UPDATE_DESKTOP_SCRIM_ON_DESKTOP_TASK_LAUNCH)
    fun startLaunchTransition_unmaximizedRequestedTaskBounds_desktopScrimIsNotApplied() {
        val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
        val task = setUpFreeformTask(bounds = stableBounds)

        controller.startLaunchTransition(
            transitionType = TRANSIT_OPEN,
            wct = WindowContainerTransaction(),
            launchingTaskId = task.taskId,
            deskId = DEFAULT_DISPLAY,
            displayId = DEFAULT_DISPLAY,
            userId = DEFAULT_USER_ID,
            requestedTaskBounds = TASK_BOUNDS,
            flexibleLaunchSize = false,
        )

        verify(desktopRemoteListener).onTaskbarCornerRoundingUpdate(false, DEFAULT_DISPLAY)
    }

    @Test
    @EnableFlags(Flags.FLAG_UPDATE_DESKTOP_SCRIM_ON_DESKTOP_TASK_LAUNCH)
    fun startLaunchTransition_maximizedTaskExists_desktopScrimIsApplied() {
        // Set up an existing maximized task.
        val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
        setUpFreeformTask(bounds = stableBounds, addToRepository = true)

        val task = setUpFreeformTask(bounds = TASK_BOUNDS)

        controller.startLaunchTransition(
            transitionType = TRANSIT_OPEN,
            wct = WindowContainerTransaction(),
            launchingTaskId = task.taskId,
            deskId = DEFAULT_DISPLAY,
            displayId = DEFAULT_DISPLAY,
            userId = DEFAULT_USER_ID,
            requestedTaskBounds = TASK_BOUNDS,
            flexibleLaunchSize = false,
        )

        verify(desktopRemoteListener).onTaskbarCornerRoundingUpdate(true, DEFAULT_DISPLAY)
    }

    @Test
    @EnableFlags(Flags.FLAG_UPDATE_DESKTOP_SCRIM_ON_DESKTOP_TASK_LAUNCH)
    fun startLaunchTransition_flexibleLaunchSize_desktopScrimIsNotUpdated() {
        val task = setUpFreeformTask(bounds = TASK_BOUNDS)

        controller.startLaunchTransition(
            transitionType = TRANSIT_OPEN,
            wct = WindowContainerTransaction(),
            launchingTaskId = task.taskId,
            deskId = DEFAULT_DISPLAY,
            displayId = DEFAULT_DISPLAY,
            userId = DEFAULT_USER_ID,
            requestedTaskBounds = null,
            flexibleLaunchSize = true,
        )

        // Desktop scrim is not updated since the task bounds are not predicted.
        verify(desktopRemoteListener, never()).onTaskbarCornerRoundingUpdate(anyBoolean(), anyInt())
    }

    @Test
    @EnableFlags(Flags.FLAG_SHOW_HOME_BEHIND_DESKTOP)
    fun showHomeBehindDesktop_wallpaperNotPresent() {
        whenever(desktopWallpaperActivityUtils.hasDesktopWallpaperActivityEnabled(any()))
            .thenReturn(false)
        desktopState.shouldShowHomeBehindDesktop = true
        val homeTask = setUpHomeTask()
        val task1 = setUpFreeformTask()

        controller.activateDesk(
            deskId = DEFAULT_DISPLAY,
            userId = taskRepository.userId,
            remoteTransition = RemoteTransition(TestRemoteTransition()),
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        val wct =
            getLatestWct(type = TRANSIT_TO_FRONT, handlerClass = OneShotRemoteHandler::class.java)
        val wallpaperReorderIndex = wct.indexOfReorder(wallpaperToken, toTop = true)

        // There should be no wallpaper present to reorder.
        assertThat(wallpaperReorderIndex).isEqualTo(-1)
    }

    @Test
    @DisableFlags(Flags.FLAG_BETTER_DESK_DEACTIVATION_IN_RECENTS_TRANSITION)
    fun onRecentsInDesktopAnimationFinishing_returningToApp_noDeskDeactivation_betterFlagDisabled() {
        val deskId = 0
        taskRepository.setActiveDesk(DEFAULT_DISPLAY, deskId)

        val transition = Binder()
        val finishWct = WindowContainerTransaction()
        controller.onRecentsInDesktopAnimationFinishing(
            transition = transition,
            finishWct = finishWct,
            returnToApp = true,
            activeDeskIdOnRecentsStart = deskId,
        )

        verify(desksOrganizer, never()).deactivateDesk(finishWct, deskId)
        verify(desksTransitionsObserver, never())
            .addPendingTransition(
                argThat { t -> t.token == transition && t is DeskTransition.DeactivateDesk }
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_BETTER_DESK_DEACTIVATION_IN_RECENTS_TRANSITION)
    @DisableFlags(Flags.FLAG_HANDLE_OVERVIEW_DESKTOP_SCRIM_BUGFIX)
    fun onRecentsInDesktopAnimationFinishing_returningToApp_noDeskDeactivation() {
        val overviewVisibilityListenerCaptor = argumentCaptor<OverviewVisibilityChangeListener>()
        whenever(shellController.isOverviewVisible(DEFAULT_DISPLAY)).thenReturn(true)
        whenever(displayController.getDisplay(DEFAULT_DISPLAY))
            .thenReturn(mock(Display::class.java))
        // TODO (b/489936769): Run this test with the overview scrim bug fix flag enabled. With the
        //  scrim controller depending on the task controller, and the scrim controller also
        //  register an overview listener, the verification of the only one listener added is not
        //  valid. Disable the flag to ensure the test still covers the unit test part of the task
        //  controller. Refactor to make the scrim controller isolated from the task controller and
        //  to mock the scrim controller in test properly before removing the flag to make the test
        //  covers the desired cases always. The same thing for all other tests with the flag
        //  disabled.
        verify(shellController)
            .addOverviewVisibilityChangeListener(overviewVisibilityListenerCaptor.capture())
        overviewVisibilityListenerCaptor.lastValue.onOverviewShown(DEFAULT_DISPLAY)
        val deskId = 0
        taskRepository.setActiveDesk(DEFAULT_DISPLAY, deskId)

        val transition = Binder()
        val finishWct = WindowContainerTransaction()
        controller.onRecentsInDesktopAnimationFinishing(
            transition = transition,
            finishWct = finishWct,
            returnToApp = true,
            activeDeskIdOnRecentsStart = deskId,
        )
        overviewVisibilityListenerCaptor.lastValue.onOverviewHidden(DEFAULT_DISPLAY)

        verify(transitions, never()).startTransition(eq(TRANSIT_TO_BACK), any(), any())
        verify(desksOrganizer, never()).deactivateDesk(any(), eq(deskId), any())
        verify(desksTransitionsObserver, never())
            .addPendingTransition(argThat { t -> t is DeskTransition.DeactivateDesk })
    }

    @Test
    @EnableFlags(Flags.FLAG_BETTER_DESK_DEACTIVATION_IN_RECENTS_TRANSITION)
    @DisableFlags(Flags.FLAG_HANDLE_OVERVIEW_DESKTOP_SCRIM_BUGFIX)
    fun onRecentsInDesktopAnimationFinishing_displayInvalid_noDeskDeactivation() {
        val overviewVisibilityListenerCaptor = argumentCaptor<OverviewVisibilityChangeListener>()
        whenever(shellController.isOverviewVisible(SECONDARY_DISPLAY_ID)).thenReturn(true)
        verify(shellController)
            .addOverviewVisibilityChangeListener(overviewVisibilityListenerCaptor.capture())
        overviewVisibilityListenerCaptor.lastValue.onOverviewShown(SECONDARY_DISPLAY_ID)
        val deskId = 2
        taskRepository.addDesk(displayId = SECONDARY_DISPLAY_ID, deskId = deskId)
        taskRepository.setActiveDesk(SECONDARY_DISPLAY_ID, deskId)
        whenever(displayController.getDisplay(SECONDARY_DISPLAY_ID)).thenReturn(null)

        val transition = Binder()
        val finishWct = WindowContainerTransaction()
        controller.onRecentsInDesktopAnimationFinishing(
            transition = transition,
            finishWct = finishWct,
            returnToApp = false,
            activeDeskIdOnRecentsStart = deskId,
        )
        overviewVisibilityListenerCaptor.lastValue.onOverviewHidden(SECONDARY_DISPLAY_ID)

        verify(transitions, never()).startTransition(eq(TRANSIT_TO_BACK), any(), any())
        verify(desksOrganizer, never()).deactivateDesk(any(), eq(deskId), any())
        verify(desksTransitionsObserver, never())
            .addPendingTransition(argThat { t -> t is DeskTransition.DeactivateDesk })
    }

    @Test
    fun onRecentsInDesktopAnimationFinishing_returningToApp_snapEventHandlerNotified() {
        val deskId = 0
        taskRepository.setActiveDesk(DEFAULT_DISPLAY, deskId)
        whenever(displayController.getDisplay(DEFAULT_DISPLAY))
            .thenReturn(mock(Display::class.java))

        val transition = Binder()
        val finishWct = WindowContainerTransaction()
        controller.onRecentsInDesktopAnimationFinishing(
            transition = transition,
            finishWct = finishWct,
            returnToApp = true,
            activeDeskIdOnRecentsStart = deskId,
        )

        verify(snapEventHandler, times(1)).onRecentsAnimationEndedToSameDesk()
    }

    @Test
    @DisableFlags(Flags.FLAG_BETTER_DESK_DEACTIVATION_IN_RECENTS_TRANSITION)
    fun onRecentsInDesktopAnimationFinishing_deskNoLongerActive_noDeskDeactivation_betterFlagDisabled() {
        val deskId = 0
        taskRepository.setDeskInactive(deskId)

        val transition = Binder()
        val finishWct = WindowContainerTransaction()
        controller.onRecentsInDesktopAnimationFinishing(
            transition = transition,
            finishWct = finishWct,
            returnToApp = false,
            activeDeskIdOnRecentsStart = deskId,
        )

        verify(desksOrganizer, never()).deactivateDesk(finishWct, deskId)
        verify(desksTransitionsObserver, never())
            .addPendingTransition(
                argThat { t -> t.token == transition && t is DeskTransition.DeactivateDesk }
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_BETTER_DESK_DEACTIVATION_IN_RECENTS_TRANSITION)
    @DisableFlags(Flags.FLAG_HANDLE_OVERVIEW_DESKTOP_SCRIM_BUGFIX)
    fun onRecentsInDesktopAnimationFinishing_deskNoLongerActive_noDeskDeactivation() {
        val overviewVisibilityListenerCaptor = argumentCaptor<OverviewVisibilityChangeListener>()
        whenever(shellController.isOverviewVisible(DEFAULT_DISPLAY)).thenReturn(true)
        verify(shellController)
            .addOverviewVisibilityChangeListener(overviewVisibilityListenerCaptor.capture())
        overviewVisibilityListenerCaptor.lastValue.onOverviewShown(DEFAULT_DISPLAY)
        val deskId = 0
        taskRepository.setDeskInactive(deskId)
        whenever(displayController.getDisplay(DEFAULT_DISPLAY))
            .thenReturn(mock(Display::class.java))

        val transition = Binder()
        val finishWct = WindowContainerTransaction()
        controller.onRecentsInDesktopAnimationFinishing(
            transition = transition,
            finishWct = finishWct,
            returnToApp = false,
            activeDeskIdOnRecentsStart = deskId,
        )
        overviewVisibilityListenerCaptor.lastValue.onOverviewHidden(DEFAULT_DISPLAY)

        verify(transitions, never()).startTransition(eq(TRANSIT_TO_BACK), any(), any())
        verify(desksOrganizer, never()).deactivateDesk(any(), eq(deskId), any())
        verify(desksTransitionsObserver, never())
            .addPendingTransition(argThat { t -> t is DeskTransition.DeactivateDesk })
    }

    @Test
    @DisableFlags(Flags.FLAG_BETTER_DESK_DEACTIVATION_IN_RECENTS_TRANSITION)
    fun onRecentsInDesktopAnimationFinishing_deskStillActive_notReturningToDesk_deactivatesDesk_betterFlagDisabled() {
        val deskId = 0
        taskRepository.setActiveDesk(DEFAULT_DISPLAY, deskId)
        whenever(displayController.getDisplay(DEFAULT_DISPLAY))
            .thenReturn(mock(Display::class.java))

        val transition = Binder()
        val finishWct = WindowContainerTransaction()
        controller.onRecentsInDesktopAnimationFinishing(
            transition = transition,
            finishWct = finishWct,
            returnToApp = false,
            activeDeskIdOnRecentsStart = deskId,
        )

        verify(desksOrganizer).deactivateDesk(finishWct, deskId)
        verify(desksTransitionsObserver)
            .addPendingTransition(
                DeskTransition.DeactivateDesk(
                    transition,
                    userId = taskRepository.userId,
                    deskId = deskId,
                    displayId = DEFAULT_DISPLAY,
                    switchingUser = false,
                    exitReason = ExitReason.RETURN_HOME_OR_OVERVIEW,
                )
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_BETTER_DESK_DEACTIVATION_IN_RECENTS_TRANSITION)
    @DisableFlags(Flags.FLAG_HANDLE_OVERVIEW_DESKTOP_SCRIM_BUGFIX)
    fun onRecentsInDesktopAnimationFinishing_deskStillActive_notReturningToDesk_deactivatesDesk() {
        val overviewVisibilityListenerCaptor = argumentCaptor<OverviewVisibilityChangeListener>()
        whenever(shellController.isOverviewVisible(DEFAULT_DISPLAY)).thenReturn(true)
        verify(shellController)
            .addOverviewVisibilityChangeListener(overviewVisibilityListenerCaptor.capture())
        overviewVisibilityListenerCaptor.lastValue.onOverviewShown(DEFAULT_DISPLAY)
        val deskId = 0
        taskRepository.setActiveDesk(DEFAULT_DISPLAY, deskId)
        whenever(displayController.getDisplay(DEFAULT_DISPLAY))
            .thenReturn(mock(Display::class.java))

        val transition = Binder()
        val finishWct = WindowContainerTransaction()
        controller.onRecentsInDesktopAnimationFinishing(
            transition = transition,
            finishWct = finishWct,
            returnToApp = false,
            activeDeskIdOnRecentsStart = deskId,
        )
        overviewVisibilityListenerCaptor.lastValue.onOverviewHidden(DEFAULT_DISPLAY)

        val wct = getLatestWct(TRANSIT_TO_BACK)
        verify(desksOrganizer).deactivateDesk(wct, deskId)
        verify(desksTransitionsObserver)
            .addPendingTransition(
                argThat { t ->
                    t is DeskTransition.DeactivateDesk &&
                        t.userId == taskRepository.userId &&
                        t.deskId == deskId &&
                        t.displayId == DEFAULT_DISPLAY &&
                        t.exitReason == ExitReason.RETURN_HOME_OR_OVERVIEW
                }
            )
    }

    @Test
    @DisableFlags(Flags.FLAG_BETTER_DESK_DEACTIVATION_IN_RECENTS_TRANSITION)
    fun onRecentsInDesktopAnimationFinishing_deskStillActive_notReturningToDesk_doesNotBringUpWallpaperOrHome_betterFlagDisabled() {
        val deskId = 0
        taskRepository.setActiveDesk(DEFAULT_DISPLAY, deskId)

        val transition = Binder()
        val finishWct = WindowContainerTransaction()
        controller.onRecentsInDesktopAnimationFinishing(
            transition = transition,
            finishWct = finishWct,
            returnToApp = false,
            activeDeskIdOnRecentsStart = deskId,
        )

        finishWct.assertWithoutHop { hop ->
            hop.type == HIERARCHY_OP_TYPE_REORDER &&
                hop.container == wallpaperToken.asBinder() &&
                !hop.toTop
        }
        finishWct.assertWithoutHop { hop -> hop.type == HIERARCHY_OP_TYPE_PENDING_INTENT }
    }

    @Test
    @EnableFlags(Flags.FLAG_BETTER_DESK_DEACTIVATION_IN_RECENTS_TRANSITION)
    @DisableFlags(Flags.FLAG_HANDLE_OVERVIEW_DESKTOP_SCRIM_BUGFIX)
    fun onRecentsInDesktopAnimationFinishing_deskStillActive_notReturningToDesk_doesNotBringUpWallpaperOrHome() {
        val overviewVisibilityListenerCaptor = argumentCaptor<OverviewVisibilityChangeListener>()
        whenever(shellController.isOverviewVisible(DEFAULT_DISPLAY)).thenReturn(true)
        verify(shellController)
            .addOverviewVisibilityChangeListener(overviewVisibilityListenerCaptor.capture())
        overviewVisibilityListenerCaptor.lastValue.onOverviewShown(DEFAULT_DISPLAY)
        val deskId = 0
        taskRepository.setActiveDesk(DEFAULT_DISPLAY, deskId)
        whenever(displayController.getDisplay(DEFAULT_DISPLAY))
            .thenReturn(mock(Display::class.java))

        val transition = Binder()
        val finishWct = WindowContainerTransaction()
        controller.onRecentsInDesktopAnimationFinishing(
            transition = transition,
            finishWct = finishWct,
            returnToApp = false,
            activeDeskIdOnRecentsStart = deskId,
        )
        overviewVisibilityListenerCaptor.lastValue.onOverviewHidden(DEFAULT_DISPLAY)

        val wct = getLatestWct(TRANSIT_TO_BACK)
        wct.assertWithoutHop { hop ->
            hop.type == HIERARCHY_OP_TYPE_REORDER &&
                hop.container == wallpaperToken.asBinder() &&
                !hop.toTop
        }
        wct.assertWithoutHop { hop -> hop.type == HIERARCHY_OP_TYPE_PENDING_INTENT }
    }

    @Test
    fun handleRequest_freeformTaskMove_addsBoundsToWct() {
        val stableBounds =
            Rect(0, 0, DISPLAY_DIMENSION_LONG, DISPLAY_DIMENSION_SHORT - TASKBAR_FRAME_HEIGHT)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }

        val task = setUpFreeformTask(bounds = DEFAULT_LANDSCAPE_BOUNDS)
        val requestedBounds = Rect(DEFAULT_LANDSCAPE_BOUNDS).apply { inset(10, 20, 30, 40) }

        val wct =
            controller.handleRequest(
                Binder(),
                createTaskMoveTransition(task, task.displayId, requestedBounds),
            )

        assertNotNull(wct, "should handle request")
        val finalBounds = findBoundsChange(wct, task)
        assertThat(requestedBounds).isEqualTo(finalBounds)
    }

    @Test
    fun handleRequest_freeformTaskMove_sameTargetDisplay_noReorder() {
        val stableBounds =
            Rect(0, 0, DISPLAY_DIMENSION_LONG, DISPLAY_DIMENSION_SHORT - TASKBAR_FRAME_HEIGHT)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }

        val task = setUpFreeformTask(bounds = DEFAULT_LANDSCAPE_BOUNDS)
        val requestedBounds = Rect(DEFAULT_LANDSCAPE_BOUNDS).apply { inset(10, 20, 30, 40) }

        val wct =
            controller.handleRequest(
                Binder(),
                createTaskMoveTransition(task, task.displayId, requestedBounds),
            )

        assertNotNull(wct, "should handle request")
        wct.assertWithoutHop(ReorderPredicate(token = task.token))
        wct.assertWithoutHop(ReparentPredicate(token = task.token))
    }

    @Test
    fun handleRequest_freeformTaskMove_doesNotAdjustStableBounds() {
        val stableBounds =
            Rect(0, 0, DISPLAY_DIMENSION_LONG, DISPLAY_DIMENSION_SHORT - TASKBAR_FRAME_HEIGHT)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }

        val task = setUpFreeformTask(bounds = DEFAULT_LANDSCAPE_BOUNDS)

        val wct =
            controller.handleRequest(
                Binder(),
                createTaskMoveTransition(task, task.displayId, stableBounds),
            )

        assertNotNull(wct, "should handle request")
        val finalBounds = findBoundsChange(wct, task)
        assertThat(finalBounds).isEqualTo(stableBounds)
    }

    @Test
    fun handleRequest_freeformTaskMove_doesNotAdjustBoundsInsideStableBounds() {
        val stableBounds =
            Rect(0, 0, DISPLAY_DIMENSION_LONG, DISPLAY_DIMENSION_SHORT - TASKBAR_FRAME_HEIGHT)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }

        val task = setUpFreeformTask(bounds = DEFAULT_LANDSCAPE_BOUNDS)
        // For this test make sure that |requestedBounds| are fully contained in |stableBounds|.
        val requestedBounds = Rect(stableBounds).apply { inset(10, 20, 30, 40) }

        val wct =
            controller.handleRequest(
                Binder(),
                createTaskMoveTransition(task, task.displayId, requestedBounds),
            )

        assertNotNull(wct, "should handle request")
        val finalBounds = findBoundsChange(wct, task)
        assertThat(finalBounds).isEqualTo(requestedBounds)
    }

    @Test
    fun handleRequest_freeformTaskMove_adjustsToFitInsideStableBounds() {
        val stableBounds =
            Rect(0, 0, DISPLAY_DIMENSION_LONG, DISPLAY_DIMENSION_SHORT).apply {
                inset(10, 20, 30, 40)
            }
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }

        val task = setUpFreeformTask(bounds = DEFAULT_LANDSCAPE_BOUNDS)
        // For this test make sure that |requestedBounds| fully contain |stableBounds|.
        val requestedBounds = Rect(0, 0, DISPLAY_DIMENSION_LONG, DISPLAY_DIMENSION_SHORT)

        val wct =
            controller.handleRequest(
                Binder(),
                createTaskMoveTransition(task, task.displayId, requestedBounds),
            )

        assertNotNull(wct, "should handle request")
        val finalBounds = findBoundsChange(wct, task)
        assertThat(finalBounds).isEqualTo(stableBounds)
    }

    @Test
    fun handleRequest_freeformTaskMove_adjustsToFitInsideStableBounds_preservesSizeIfPossible() {
        val stableBounds =
            Rect(0, 0, DISPLAY_DIMENSION_LONG, DISPLAY_DIMENSION_SHORT).apply {
                inset(10, 20, 30, 40)
            }
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }

        val task = setUpFreeformTask(bounds = DEFAULT_LANDSCAPE_BOUNDS)
        // For this test make sure that |requestedBounds| is inside display bounds, not fully inside
        // |stableBounds| and no bigger than |stableBounds|.
        val requestedBounds = Rect(0, 0, DISPLAY_DIMENSION_LONG / 2, DISPLAY_DIMENSION_SHORT / 2)

        val wct =
            controller.handleRequest(
                Binder(),
                createTaskMoveTransition(task, task.displayId, requestedBounds),
            )

        assertNotNull(wct, "should handle request")
        val finalBounds = findBoundsChange(wct, task)
        assertThat(finalBounds?.width()).isEqualTo(requestedBounds.width())
        assertThat(finalBounds?.height()).isEqualTo(requestedBounds.height())
    }

    @Test
    fun handleRequest_freeformTaskMove_toDisplayWithoutDesks_reparentsToTda() {
        val stableBounds = Rect(0, 0, DISPLAY_DIMENSION_LONG, DISPLAY_DIMENSION_SHORT)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))

        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY, bounds = DEFAULT_LANDSCAPE_BOUNDS)
        val requestedBounds = Rect(DEFAULT_LANDSCAPE_BOUNDS).apply { inset(10, 20, 30, 40) }

        val wct =
            controller.handleRequest(
                Binder(),
                createTaskMoveTransition(task, SECOND_DISPLAY, requestedBounds),
            )
        assertNotNull(wct, "should handle request")

        wct.assertHop(
            ReparentPredicate(
                token = task.token,
                parentToken = secondDisplayArea.token,
                toTop = true,
            )
        )
    }

    @Test
    fun handleRequest_freeformTaskMove_toDisplayWithDesk_multiDesksEnabled_reparentsToActiveDesk() {
        val stableBounds = Rect(0, 0, DISPLAY_DIMENSION_LONG, DISPLAY_DIMENSION_SHORT)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        val targetDeskId = 2
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = targetDeskId)
        taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = targetDeskId)
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        desktopState.enterDesktopByDefaultOnFreeformDisplay = true
        rootTaskDisplayAreaOrganizer.setDesktopFirst(SECOND_DISPLAY)

        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY, bounds = DEFAULT_LANDSCAPE_BOUNDS)
        val requestedBounds = Rect(DEFAULT_LANDSCAPE_BOUNDS).apply { inset(10, 20, 30, 40) }

        val wct =
            controller.handleRequest(
                Binder(),
                createTaskMoveTransition(task, SECOND_DISPLAY, requestedBounds),
            )
        assertNotNull(wct, "should handle request")
        val finalBounds = findBoundsChange(wct, task)
        assertThat(finalBounds).isEqualTo(requestedBounds)
        verify(desksOrganizer).moveTaskToDesk(any(), eq(targetDeskId), eq(task), eq(false))
    }

    @Test
    fun handleRequest_freeformTaskMove_toDisplayWithDesk_multiDesksEnabled_activatesDeskIfNoActive() {
        val stableBounds = Rect(0, 0, DISPLAY_DIMENSION_LONG, DISPLAY_DIMENSION_SHORT)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        val targetDeskId = 2
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = targetDeskId)
        taskRepository.setDeskInactive(targetDeskId)
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        desktopState.enterDesktopByDefaultOnFreeformDisplay = true
        rootTaskDisplayAreaOrganizer.setDesktopFirst(SECOND_DISPLAY)

        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY, bounds = DEFAULT_LANDSCAPE_BOUNDS)
        val requestedBounds = Rect(DEFAULT_LANDSCAPE_BOUNDS).apply { inset(10, 20, 30, 40) }

        val wct =
            controller.handleRequest(
                Binder(),
                createTaskMoveTransition(task, SECOND_DISPLAY, requestedBounds),
            )
        assertNotNull(wct, "should handle request")
        val finalBounds = findBoundsChange(wct, task)
        assertThat(finalBounds).isEqualTo(requestedBounds)
        verify(desksOrganizer).activateDesk(wct, targetDeskId)
    }

    @Test
    fun handleRequest_freeformTaskMove_toDisplayWithDesks_reordersIncludingParents() {
        val stableBounds =
            Rect(0, 0, DISPLAY_DIMENSION_LONG, DISPLAY_DIMENSION_SHORT - TASKBAR_FRAME_HEIGHT)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        val targetDeskId = 2
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = targetDeskId)
        taskRepository.setDeskInactive(targetDeskId)
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        desktopState.enterDesktopByDefaultOnFreeformDisplay = true
        rootTaskDisplayAreaOrganizer.setDesktopFirst(SECOND_DISPLAY)

        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY, bounds = DEFAULT_LANDSCAPE_BOUNDS)
        val requestedBounds = Rect(DEFAULT_LANDSCAPE_BOUNDS).apply { inset(10, 20, 30, 40) }

        val wct =
            controller.handleRequest(
                Binder(),
                createTaskMoveTransition(task, SECOND_DISPLAY, requestedBounds),
            )

        assertNotNull(wct, "should handle request")
        wct.assertHop(ReorderPredicate(token = task.token, toTop = true, includingParents = true))
    }

    @Test
    fun handleRequest_freeformTaskMove_toDisplayWithoutDesks_reordersIncludingParents() {
        val stableBounds = Rect(0, 0, DISPLAY_DIMENSION_LONG, DISPLAY_DIMENSION_SHORT)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))

        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY, bounds = DEFAULT_LANDSCAPE_BOUNDS)
        val requestedBounds = Rect(DEFAULT_LANDSCAPE_BOUNDS).apply { inset(10, 20, 30, 40) }

        val wct =
            controller.handleRequest(
                Binder(),
                createTaskMoveTransition(task, SECOND_DISPLAY, requestedBounds),
            )
        assertNotNull(wct, "should handle request")

        wct.assertHop(ReorderPredicate(token = task.token, toTop = true, includingParents = true))
    }

    @Test
    fun handleRequest_freeformTaskMove_fromMinimizedState_sameDisplay_staysMinimized_noZOrderChange() {
        val stableBounds =
            Rect(0, 0, DISPLAY_DIMENSION_LONG, DISPLAY_DIMENSION_SHORT - TASKBAR_FRAME_HEIGHT)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }

        val task = setUpFreeformTask(bounds = DEFAULT_LANDSCAPE_BOUNDS)
        taskRepository.minimizeTask(DEFAULT_DISPLAY, task.taskId)
        val requestedBounds = Rect(DEFAULT_LANDSCAPE_BOUNDS).apply { inset(10, 20, 30, 40) }

        val wct =
            controller.handleRequest(
                Binder(),
                createTaskMoveTransition(task, task.displayId, requestedBounds),
            )

        assertNotNull(wct, "should handle request")
        assertThat(findBoundsChange(wct, task)).isEqualTo(requestedBounds)
        verify(desksOrganizer, never()).moveTaskToDesk(eq(wct), any(), any(), any())
        verify(desksOrganizer, never()).unminimizeTask(eq(wct), any(), any())
        verify(desksOrganizer, never()).reorderTaskToFront(eq(wct), any(), any())
    }

    @Test
    fun handleRequest_freeformTaskMove_fromMinimizedState_crossDisplay_unminimizes() {
        val stableBounds =
            Rect(0, 0, DISPLAY_DIMENSION_LONG, DISPLAY_DIMENSION_SHORT - TASKBAR_FRAME_HEIGHT)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }

        val task = setUpFreeformTask(bounds = DEFAULT_LANDSCAPE_BOUNDS)
        taskRepository.minimizeTask(DEFAULT_DISPLAY, task.taskId)
        val requestedBounds = Rect(DEFAULT_LANDSCAPE_BOUNDS).apply { inset(10, 20, 30, 40) }

        val targetDeskId = 2
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = targetDeskId)
        taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = targetDeskId)
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        desktopState.enterDesktopByDefaultOnFreeformDisplay = true
        rootTaskDisplayAreaOrganizer.setDesktopFirst(SECOND_DISPLAY)

        val wct =
            controller.handleRequest(
                Binder(),
                createTaskMoveTransition(task, SECOND_DISPLAY, requestedBounds),
            )

        assertNotNull(wct, "should handle request")
        assertThat(findBoundsChange(wct, task)).isEqualTo(requestedBounds)
        verify(desksOrganizer).moveTaskToDesk(wct, targetDeskId, task, minimized = false)
    }

    @Test
    fun handleRequest_freeformTaskMove_fromInactiveDesk_sameDisplay_noZOrderChange() {
        val stableBounds =
            Rect(0, 0, DISPLAY_DIMENSION_LONG, DISPLAY_DIMENSION_SHORT - TASKBAR_FRAME_HEIGHT)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }

        val activeDeskId = 4
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = activeDeskId)
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = activeDeskId)
        val inactiveDeskId = 5
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = inactiveDeskId)

        val task = setUpFreeformTask(bounds = DEFAULT_LANDSCAPE_BOUNDS, deskId = inactiveDeskId)
        val requestedBounds = Rect(DEFAULT_LANDSCAPE_BOUNDS).apply { inset(10, 20, 30, 40) }

        val wct =
            controller.handleRequest(
                Binder(),
                createTaskMoveTransition(task, task.displayId, requestedBounds),
            )

        assertNotNull(wct, "should handle request")
        assertThat(findBoundsChange(wct, task)).isEqualTo(requestedBounds)
        verify(desksOrganizer, never()).moveTaskToDesk(eq(wct), any(), any(), any())
        verify(desksOrganizer, never()).unminimizeTask(eq(wct), any(), any())
        verify(desksOrganizer, never()).reorderTaskToFront(eq(wct), any(), any())
    }

    @Test
    fun handleRequest_freeformTaskMove_fromInactiveDesk_crossDisplay_movesToActiveDesk() {
        val stableBounds =
            Rect(0, 0, DISPLAY_DIMENSION_LONG, DISPLAY_DIMENSION_SHORT - TASKBAR_FRAME_HEIGHT)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }

        val activeDeskId = 4
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = activeDeskId)
        taskRepository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = activeDeskId)
        val inactiveDeskId = 5
        taskRepository.addDesk(displayId = DEFAULT_DISPLAY, deskId = inactiveDeskId)

        val targetDeskId = 2
        taskRepository.addDesk(displayId = SECOND_DISPLAY, deskId = targetDeskId)
        taskRepository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = targetDeskId)
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        desktopState.enterDesktopByDefaultOnFreeformDisplay = true
        rootTaskDisplayAreaOrganizer.setDesktopFirst(SECOND_DISPLAY)

        val task = setUpFreeformTask(bounds = DEFAULT_LANDSCAPE_BOUNDS, deskId = inactiveDeskId)
        val requestedBounds = Rect(DEFAULT_LANDSCAPE_BOUNDS).apply { inset(10, 20, 30, 40) }

        val wct =
            controller.handleRequest(
                Binder(),
                createTaskMoveTransition(task, SECOND_DISPLAY, requestedBounds),
            )

        assertNotNull(wct, "should handle request")
        assertThat(findBoundsChange(wct, task)).isEqualTo(requestedBounds)
        verify(desksOrganizer).moveTaskToDesk(wct, targetDeskId, task, minimized = false)
    }

    @Test
    fun handleRequest_userSwitch() {
        val previousUser = DEFAULT_USER_ID
        val newUser = SECONDARY_USER_ID
        userRepositories.getProfile(DEFAULT_USER_ID).apply {
            addDesk(displayId = DEFAULT_DISPLAY, deskId = 5)
            setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 5)
        }
        userRepositories.getProfile(SECONDARY_USER_ID).apply {
            addDesk(displayId = DEFAULT_DISPLAY, deskId = 110005)
            setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 110005)
        }

        val wct =
            controller.handleRequest(
                Binder(),
                createTransition(
                    task = null,
                    userChange =
                        TransitionRequestInfo.UserChange(
                            /* previousUserId = */ previousUser,
                            /* newUserId = */ newUser,
                        ),
                ),
            )

        assertNotNull(wct, "should handle request")
        val inOrder = inOrder(desksOrganizer)
        inOrder
            .verify(desksOrganizer)
            .deactivateDesk(wct = eq(wct), deskId = eq(5), skipReorder = any())
        inOrder
            .verify(desksOrganizer)
            .activateDesk(wct = eq(wct), deskId = eq(110005), skipReorder = any())
    }

    @Test
    fun handleRequest_userSwitch_multipleDisplays() {
        val previousUser = DEFAULT_USER_ID
        val newUser = SECONDARY_USER_ID
        userRepositories.getProfile(DEFAULT_USER_ID).apply {
            addDesk(displayId = DEFAULT_DISPLAY, deskId = 5)
            setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 5)
            addDesk(displayId = SECONDARY_DISPLAY_ID, deskId = 6)
            setActiveDesk(displayId = SECONDARY_DISPLAY_ID, deskId = 6)
        }
        userRepositories.getProfile(SECONDARY_USER_ID).apply {
            addDesk(displayId = DEFAULT_DISPLAY, deskId = 110005)
            setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 110005)
            addDesk(displayId = SECONDARY_DISPLAY_ID, deskId = 110006)
            setActiveDesk(displayId = SECONDARY_DISPLAY_ID, deskId = 110006)
        }

        val wct =
            controller.handleRequest(
                Binder(),
                createTransition(
                    task = null,
                    userChange =
                        TransitionRequestInfo.UserChange(
                            /* previousUserId = */ previousUser,
                            /* newUserId = */ newUser,
                        ),
                ),
            )

        assertNotNull(wct, "should handle request")
        val inOrder = inOrder(desksOrganizer)
        inOrder
            .verify(desksOrganizer)
            .deactivateDesk(wct = eq(wct), deskId = eq(5), skipReorder = any())
        inOrder
            .verify(desksOrganizer)
            .deactivateDesk(wct = eq(wct), deskId = eq(6), skipReorder = any())
        inOrder
            .verify(desksOrganizer)
            .activateDesk(wct = eq(wct), deskId = eq(110005), skipReorder = any())
        inOrder
            .verify(desksOrganizer)
            .activateDesk(wct = eq(wct), deskId = eq(110006), skipReorder = any())
    }

    @Test
    fun isDesktopModeEnabledOnDisplay_cannotEnterDesktopMode_isFalse() {
        desktopState.canEnterDesktopMode = false
        clearInvocations(shellInit)
        controller = createController()

        assertThat(controller.isDisplayInDesktopMode(SECONDARY_DISPLAY_ID)).isFalse()
    }

    @Test
    fun isDesktopModeEnabledOnDisplay_displayNotDesktop_isFalse() {
        assertThat(controller.isDisplayInDesktopMode(SECONDARY_DISPLAY_ID)).isFalse()
    }

    @Test
    fun isDesktopModeEnabledOnDisplay_displayIsDesktop_isTrue() {
        assertThat(controller.isDisplayInDesktopMode(DEFAULT_DISPLAY)).isTrue()
    }

    @Test
    fun isDesktopTask_desktopTask_isTrue() {
        assertThat(controller.isDesktopTask(setUpFreeformTask())).isTrue()
    }

    @Test
    fun isDesktopTask_desktopTask_isFalse() {
        assertThat(controller.isDesktopTask(setUpFullscreenTask())).isFalse()
    }

    @Test
    @EnableFlags(com.android.launcher3.Flags.FLAG_ENABLE_ALT_TAB_KQS_FLATENNING)
    @DisableFlags(Flags.FLAG_ENABLE_BUBBLE_ROOT_TASK)
    fun addMoveToBubbleFromDesktopChange_disableRootTaskForBubble_reparentToTda() {
        val task = setUpFreeformTask()
        val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
        val wct = WindowContainerTransaction()

        controller.addMoveToBubbleFromDesktopChange(
            wct,
            task,
            transition = mock<IBinder>(IBinder::class.java),
        )

        wct.assertHop(ReparentPredicate(token = task.token, parentToken = tda.token, toTop = true))
        verify(desksOrganizer).removeDesk(wct, deskId = 0, task.userId)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_BUBBLE_ROOT_TASK,
        com.android.launcher3.Flags.FLAG_ENABLE_ALT_TAB_KQS_FLATENNING,
    )
    fun addMoveToBubbleFromDesktopChange_enableAllFlags_reorder() {
        val task = setUpFreeformTask()
        val wct = WindowContainerTransaction()

        controller.addMoveToBubbleFromDesktopChange(
            wct,
            task,
            transition = mock(IBinder::class.java),
        )

        wct.assertHop(ReorderPredicate(token = task.token, toTop = true))
        verify(desksOrganizer).removeDesk(wct, deskId = 0, task.userId)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_BUBBLE_ROOT_TASK)
    fun addMoveToBubbleFromDesktopChange_multiTasks_notExitDesktop() {
        val task = setUpFreeformTask()
        setUpFreeformTask()
        val wct = WindowContainerTransaction()
        rootTaskDisplayAreaOrganizer.setTouchFirst(DEFAULT_DISPLAY)

        controller.addMoveToBubbleFromDesktopChange(
            wct,
            task,
            transition = mock(IBinder::class.java),
        )

        wct.assertHop(ReorderPredicate(token = task.token, toTop = true))
        verify(desksOrganizer, never()).removeDesk(wct, deskId = 0, task.userId)
    }

    @Test
    fun testToggleDesktopTaskSize_callsTransitionHandlerWithUserResize() {
        val task = setUpFreeformTask()
        controller.toggleDesktopTaskSize(
            task,
            ToggleTaskSizeInteraction(
                ToggleTaskSizeInteraction.Direction.MAXIMIZE,
                ToggleTaskSizeInteraction.Source.HEADER_BUTTON_TO_MAXIMIZE,
                InputMethod.TOUCH,
            ),
        )

        verify(toggleResizeDesktopTaskTransitionHandler)
            .startTransition(
                any(),
                anyOrNull(),
                anyOrNull(),
                eq(true), // isUserResize
            )
    }

    @Test
    fun testOnDragResizeTransitionStarted_callsObserver() {
        val transition = mock(IBinder::class.java)
        controller.onDragResizeTransitionStarted(transition)
        verify(desktopTasksTransitionObserver).addPendingUserBoundsChangeTransition(transition)
    }

    @Test
    fun handleFreeformTaskPlacement_prioritizesSuggestedDesk() {
        val suggestedDeskId = 2
        taskRepository.addDesk(DEFAULT_DISPLAY, suggestedDeskId)
        taskRepository.addDesk(DEFAULT_DISPLAY, 1)
        taskRepository.setActiveDesk(DEFAULT_DISPLAY, 1)
        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY)

        controller.handleFreeformTaskPlacement(
            task = task,
            transition = Binder(),
            targetDisplayId = DEFAULT_DISPLAY,
            suggestedTargetDeskId = suggestedDeskId,
            requestedTaskBounds = null,
            requestType = TRANSIT_OPEN,
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        verify(desksOrganizer).moveTaskToDesk(any(), eq(suggestedDeskId), eq(task), anyBoolean())
    }

    @Test
    fun handleFreeformTaskPlacement_noSuggestedDesk_prioritizesSourceDesk() {
        val sourceDeskId = 2
        taskRepository.addDesk(DEFAULT_DISPLAY, sourceDeskId)
        taskRepository.addDesk(DEFAULT_DISPLAY, 1)
        taskRepository.setActiveDesk(DEFAULT_DISPLAY, 1)
        // Task already in sourceDeskId
        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = sourceDeskId)

        controller.handleFreeformTaskPlacement(
            task = task,
            transition = Binder(),
            targetDisplayId = DEFAULT_DISPLAY,
            suggestedTargetDeskId = null,
            requestedTaskBounds = null,
            requestType = TRANSIT_TO_FRONT,
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        // Should stay in source desk instead of moving to active desk.
        verify(desksOrganizer, never()).moveTaskToDesk(any(), eq(1), eq(task), any())
    }

    @Test
    fun handleFreeformTaskPlacement_noSuggestedOrSourceDesk_prioritizesActiveDesk() {
        val activeDeskId = 1
        taskRepository.addDesk(DEFAULT_DISPLAY, activeDeskId)
        taskRepository.setActiveDesk(DEFAULT_DISPLAY, activeDeskId)

        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY, addToRepository = false)

        controller.handleFreeformTaskPlacement(
            task = task,
            transition = Binder(),
            targetDisplayId = DEFAULT_DISPLAY,
            suggestedTargetDeskId = null,
            requestedTaskBounds = null,
            requestType = TRANSIT_OPEN,
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        verify(desksOrganizer).moveTaskToDesk(any(), eq(activeDeskId), eq(task), anyBoolean())
    }

    @Test
    fun handleFreeformTaskPlacement_noSuggestedOrSourceOrActiveDesk_fallsBackToDefault() {
        // No active desk
        taskRepository.setDeskInactive(0)

        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY, addToRepository = false)

        controller.handleFreeformTaskPlacement(
            task = task,
            transition = Binder(),
            targetDisplayId = DEFAULT_DISPLAY,
            suggestedTargetDeskId = null,
            requestedTaskBounds = null,
            requestType = TRANSIT_OPEN,
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        verify(desksOrganizer).moveTaskToDesk(any(), eq(0), eq(task), anyBoolean())
    }

    @Test
    fun handleFreeformTaskPlacement_switchDesks_addsPendingSwitchTransition() {
        val activeDeskId = 1
        val targetDeskId = 2
        taskRepository.addDesk(DEFAULT_DISPLAY, activeDeskId)
        taskRepository.addDesk(DEFAULT_DISPLAY, targetDeskId)
        taskRepository.setActiveDesk(DEFAULT_DISPLAY, activeDeskId)

        val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY, deskId = targetDeskId)
        val transition = Binder()

        controller.handleFreeformTaskPlacement(
            task = task,
            transition = transition,
            targetDisplayId = DEFAULT_DISPLAY,
            suggestedTargetDeskId = null,
            requestedTaskBounds = null,
            requestType = TRANSIT_TO_FRONT,
            enterReason = EnterReason.UNKNOWN_ENTER,
        )

        verify(desktopMixedTransitionHandler)
            .addPendingMixedTransition(
                argThat {
                    this is PendingMixedTransition.SwitchDesk && this.transition == transition
                }
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_UPDATE_DESKTOP_SCRIM_ON_DESKTOP_TASK_LAUNCH)
    fun handleFreeformTaskPlacement_launchingMaximizedTask_desktopScrimIsApplied() {
        val task = setUpFreeformTask(bounds = TASK_BOUNDS, addToRepository = false)

        val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
        controller.handleFreeformTaskPlacement(
            task = task,
            transition = Binder(),
            targetDisplayId = DEFAULT_DISPLAY,
            requestedTaskBounds = stableBounds,
            requestType = TRANSIT_OPEN,
            enterReason = EnterReason.TASK_LAUNCH,
            forceBringTaskToFront = true,
        )

        verify(desktopRemoteListener).onTaskbarCornerRoundingUpdate(true, DEFAULT_DISPLAY)
    }

    @Test
    @EnableFlags(Flags.FLAG_UPDATE_DESKTOP_SCRIM_ON_DESKTOP_TASK_LAUNCH)
    fun handleFreeformTaskPlacement_launchingUnmaximizedTask_desktopScrimIsNotApplied() {
        val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
        val task = setUpFreeformTask(bounds = stableBounds, addToRepository = false)

        controller.handleFreeformTaskPlacement(
            task = task,
            transition = Binder(),
            targetDisplayId = DEFAULT_DISPLAY,
            requestedTaskBounds = TASK_BOUNDS,
            requestType = TRANSIT_OPEN,
            enterReason = EnterReason.TASK_LAUNCH,
            forceBringTaskToFront = true,
        )

        verify(desktopRemoteListener).onTaskbarCornerRoundingUpdate(false, DEFAULT_DISPLAY)
    }

    @Test
    @EnableFlags(Flags.FLAG_UPDATE_DESKTOP_SCRIM_ON_DESKTOP_TASK_LAUNCH)
    fun handleFreeformTaskPlacement_maximizedTaskExists_desktopScrimIsApplied() {
        // Set up an existing maximized task.
        val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
        setUpFreeformTask(bounds = stableBounds, addToRepository = true)

        val task = setUpFreeformTask(bounds = TASK_BOUNDS, addToRepository = false)

        controller.handleFreeformTaskPlacement(
            task = task,
            transition = Binder(),
            targetDisplayId = DEFAULT_DISPLAY,
            requestedTaskBounds = TASK_BOUNDS,
            requestType = TRANSIT_OPEN,
            enterReason = EnterReason.TASK_LAUNCH,
            forceBringTaskToFront = true,
        )

        verify(desktopRemoteListener).onTaskbarCornerRoundingUpdate(true, DEFAULT_DISPLAY)
    }

    @Test
    @EnableFlags(Flags.FLAG_CHANGE_DISPLAY_FOCUS_ON_WALLPAPER_TOUCH)
    fun onWallpaperTouch_focusedTaskExists_reordersTask() {
        val task = createFreeformTask(displayId = DEFAULT_DISPLAY)
        whenever(focusTransitionObserver.getFocusedTaskIdOnDisplay(DEFAULT_DISPLAY))
            .thenReturn(task.taskId)
        whenever(shellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)

        val intent = Intent(DesktopWallpaperActivity.ACTION_WALLPAPER_TOUCH)
        intent.putExtra(DesktopWallpaperActivity.WALLPAPER_TOUCH_EXTRA_DISPLAY_ID, DEFAULT_DISPLAY)

        val receiver = argumentCaptor<BroadcastReceiver>()
        verify(spyContext).registerReceiver(receiver.capture(), any(), anyInt())
        receiver.firstValue.onReceive(spyContext, intent)
        testScopeImmediate.runCurrent()

        val wct = argumentCaptor<WindowContainerTransaction>()
        verify(transitions).startTransition(eq(TRANSIT_TO_FRONT), wct.capture(), anyOrNull())

        assertThat(wct.firstValue.hierarchyOps).hasSize(1)
        assertThat(wct.firstValue.hierarchyOps[0].type).isEqualTo(HIERARCHY_OP_TYPE_REORDER)
        assertThat(wct.firstValue.hierarchyOps[0].container).isEqualTo(task.token.asBinder())
        assertThat(wct.firstValue.hierarchyOps[0].toTop).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_CHANGE_DISPLAY_FOCUS_ON_WALLPAPER_TOUCH)
    fun onWallpaperTouch_noFocusedTask_doNothing() {
        whenever(focusTransitionObserver.getFocusedTaskIdOnDisplay(DEFAULT_DISPLAY))
            .thenReturn(INVALID_TASK_ID)
        val wallpaperToken = Binder()
        whenever(desktopWallpaperActivityTokenProvider.getToken(DEFAULT_DISPLAY))
            .thenReturn(
                WindowContainerToken(IWindowContainerToken.Stub.asInterface(wallpaperToken))
            )

        val intent = Intent(DesktopWallpaperActivity.ACTION_WALLPAPER_TOUCH)
        intent.putExtra(DesktopWallpaperActivity.WALLPAPER_TOUCH_EXTRA_DISPLAY_ID, DEFAULT_DISPLAY)

        val receiver = argumentCaptor<BroadcastReceiver>()
        verify(spyContext).registerReceiver(receiver.capture(), any(), anyInt())
        receiver.firstValue.onReceive(spyContext, intent)
        testScopeImmediate.runCurrent()

        val wct = argumentCaptor<WindowContainerTransaction>()
        verify(transitions, never()).startTransition(any(), any(), anyOrNull())
    }

    private class RunOnStartTransitionCallback : ((IBinder) -> Unit) {
        var invocations = 0
            private set

        var lastInvoked: IBinder? = null
            private set

        override fun invoke(transition: IBinder) {
            invocations++
            lastInvoked = transition
        }
    }

    private fun RunOnStartTransitionCallback.assertOnlyInvocation(transition: IBinder) {
        assertThat(invocations).isEqualTo(1)
        assertThat(lastInvoked).isEqualTo(transition)
    }

    /**
     * Assert that an unhandled drag event launches a PendingIntent with the windowing mode and
     * bounds we are expecting.
     */
    private fun testOnUnhandledDrag(
        indicatorType: DesktopModeVisualIndicator.IndicatorType,
        inputCoordinate: PointF,
        expectedBounds: Rect,
        destinationDisplayId: Int = DEFAULT_DISPLAY,
        verifyNoOp: Boolean = false,
    ) {
        desktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
        desktopState.overrideDesktopModeSupportPerDisplay[destinationDisplayId] = true
        setUpLandscapeDisplay()
        val task = setUpFreeformTask()
        markTaskVisible(task)
        task.isFocused = true
        val runningTasks = ArrayList<RunningTaskInfo>()
        runningTasks.add(task)
        val spyController = spy(controller)
        val mockPendingIntent = mock(PendingIntent::class.java)
        val mockDragEvent = mock(DragEvent::class.java)
        val mockCallback = mock(Consumer::class.java)
        val b = SurfaceControl.Builder()
        b.setName("test surface")
        val dragSurface = b.build()
        whenever(focusTransitionObserver.globallyFocusedTaskId).thenReturn(task.taskId)
        whenever(mockDragEvent.dragSurface).thenReturn(dragSurface)
        whenever(mockDragEvent.x).thenReturn(inputCoordinate.x)
        whenever(mockDragEvent.y).thenReturn(inputCoordinate.y)
        whenever(mockDragEvent.displayId).thenReturn(destinationDisplayId)
        whenever(multiInstanceHelper.supportsMultiInstanceSplit(anyOrNull(), anyInt()))
            .thenReturn(true)
        whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
        doReturn(indicatorType)
            .whenever(spyController)
            .updateVisualIndicator(
                eq(task),
                anyOrNull(),
                any(),
                anyOrNull(),
                anyOrNull(),
                eq(DesktopModeVisualIndicator.DragStartState.DRAGGED_INTENT),
            )
        mockStartLaunchTransition(transitionType = TRANSIT_OPEN, dragEvent = mockDragEvent)

        spyController.onUnhandledDrag(
            mockPendingIntent,
            context.userId,
            mockDragEvent,
            mockCallback as Consumer<Boolean>,
        )
        val times = if (verifyNoOp) never() else times(1)
        val arg = argumentCaptor<WindowContainerTransaction>()
        val expectedWindowingMode: Int
        if (indicatorType == DesktopModeVisualIndicator.IndicatorType.TO_FULLSCREEN_INDICATOR) {
            expectedWindowingMode = WINDOWING_MODE_FULLSCREEN
            // Fullscreen launches currently use default transitions
            verify(transitions, times).startTransition(any(), arg.capture(), anyOrNull())
        } else {
            expectedWindowingMode = WINDOWING_MODE_FREEFORM
            verifyStartLaunchTransition(
                verificationMode = times,
                transitionType = TRANSIT_OPEN,
                wctArgCaptor = arg,
                dragEvent = mockDragEvent,
            )
        }
        if (verifyNoOp) return
        assertThat(
                ActivityOptions.fromBundle(arg.firstValue.hierarchyOps[0].launchOptions)
                    .launchWindowingMode
            )
            .isEqualTo(expectedWindowingMode)
        assertThat(
                ActivityOptions.fromBundle(arg.firstValue.hierarchyOps[0].launchOptions)
                    .launchBounds
            )
            .isEqualTo(expectedBounds)
    }

    private val desktopWallpaperIntent: Intent
        get() = Intent(context, DesktopWallpaperActivity::class.java)

    private fun launchHomeIntent(displayId: Int): Intent {
        return Intent(Intent.ACTION_MAIN).apply {
            if (displayId != DEFAULT_DISPLAY) {
                addCategory(Intent.CATEGORY_SECONDARY_HOME)
            } else {
                addCategory(Intent.CATEGORY_HOME)
            }
        }
    }

    private fun addFreeformTaskAtPosition(
        pos: DesktopTaskPosition,
        stableBounds: Rect,
        bounds: Rect = DEFAULT_LANDSCAPE_BOUNDS,
        offsetPos: Point = Point(0, 0),
    ): RunningTaskInfo {
        val offset = pos.getTopLeftCoordinates(stableBounds, bounds)
        val prevTaskBounds = Rect(bounds)
        prevTaskBounds.offsetTo(offset.x + offsetPos.x, offset.y + offsetPos.y)
        return setUpFreeformTask(bounds = prevTaskBounds)
    }

    private fun setUpFreeformTask(
        displayId: Int = DEFAULT_DISPLAY,
        bounds: Rect = TASK_BOUNDS,
        active: Boolean = true,
        background: Boolean = false,
        deskId: Int? = null,
        taskId: Int? = null,
        addToRepository: Boolean = true,
    ): RunningTaskInfo {
        val task = createFreeformTask(displayId, bounds, taskRepository.userId, taskId)
        val activityInfo = ActivityInfo()
        activityInfo.applicationInfo = ApplicationInfo()
        task.topActivityInfo = activityInfo
        if (background) {
            whenever(shellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(null)
            whenever(recentTasksController.findTaskInBackground(task.taskId))
                .thenReturn(createRecentTaskInfo(taskId = task.taskId, displayId = displayId))
        } else {
            whenever(shellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        }
        if (addToRepository) {
            if (deskId != null) {
                taskRepository.addTaskToDesk(
                    displayId,
                    deskId,
                    task.taskId,
                    isVisible = active,
                    bounds,
                )
            } else {
                taskRepository.addTask(displayId, task.taskId, isVisible = active, bounds)
            }
        }
        if (!background) {
            runningTasks.add(task)
        }
        return task
    }

    private fun setUpPipTask(
        autoEnterEnabled: Boolean,
        displayId: Int = DEFAULT_DISPLAY,
        deskId: Int = DEFAULT_DISPLAY,
    ): RunningTaskInfo {
        val task =
            setUpFreeformTask(displayId = displayId, deskId = deskId).apply {
                pictureInPictureParams =
                    PictureInPictureParams.Builder().setAutoEnterEnabled(autoEnterEnabled).build()
                baseActivity = ComponentName("com.test.dummypackage", "TestClass")
            }
        whenever(packageManager.getApplicationInfoAsUser(any(), anyInt(), anyInt()))
            .thenReturn(task.topActivityInfo?.applicationInfo)
        return task
    }

    private fun setUpHomeTask(displayId: Int = DEFAULT_DISPLAY): RunningTaskInfo {
        val task = createHomeTask(displayId)
        whenever(shellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        runningTasks.add(task)
        return task
    }

    private fun setUpFullscreenTask(
        displayId: Int = DEFAULT_DISPLAY,
        isResizable: Boolean = true,
        windowingMode: Int = WINDOWING_MODE_FULLSCREEN,
        deviceOrientation: Int = ORIENTATION_LANDSCAPE,
        screenOrientation: Int = SCREEN_ORIENTATION_UNSPECIFIED,
        shouldLetterbox: Boolean = false,
        gravity: Int = Gravity.NO_GRAVITY,
        enableUserFullscreenOverride: Boolean = false,
        enableSystemFullscreenOverride: Boolean = false,
        aspectRatioOverrideApplied: Boolean = false,
        visible: Boolean = true,
        background: Boolean = false,
    ): RunningTaskInfo {
        val task =
            createFullscreenTask(displayId).apply {
                baseActivity = ComponentName(/* pkg= */ "", /* cls= */ "")
            }
        val activityInfo = ActivityInfo()
        activityInfo.screenOrientation = screenOrientation
        activityInfo.windowLayout =
            ActivityInfo.WindowLayout(
                -1, /* complexWidth */
                -1f, /* widthFraction */
                -1, /* complexHeight */
                -1f, /* heightFraction */
                gravity,
                -1, /* complexMinWidth */
                -1, /* complexMinHeight */
                null, /* windowLayoutAffinity */
                null, /* displayMetrics */
            )
        activityInfo.applicationInfo = ApplicationInfo()
        with(task) {
            topActivityInfo = activityInfo
            isResizeable = isResizable
            configuration.orientation = deviceOrientation
            configuration.windowConfiguration.windowingMode = windowingMode
            appCompatTaskInfo.isUserFullscreenOverrideEnabled = enableUserFullscreenOverride
            appCompatTaskInfo.isSystemFullscreenOverrideEnabled = enableSystemFullscreenOverride

            if (deviceOrientation == ORIENTATION_LANDSCAPE) {
                appCompatTaskInfo.topActivityAppBounds.set(
                    0,
                    0,
                    DISPLAY_DIMENSION_LONG,
                    DISPLAY_DIMENSION_SHORT,
                )
            } else {
                appCompatTaskInfo.topActivityAppBounds.set(
                    0,
                    0,
                    DISPLAY_DIMENSION_SHORT,
                    DISPLAY_DIMENSION_LONG,
                )
            }

            if (shouldLetterbox) {
                appCompatTaskInfo.setHasMinAspectRatioOverride(aspectRatioOverrideApplied)
                if (
                    deviceOrientation == ORIENTATION_LANDSCAPE &&
                        screenOrientation == SCREEN_ORIENTATION_PORTRAIT
                ) {
                    // Letterbox to portrait size
                    appCompatTaskInfo.isTopActivityLetterboxed = true
                    appCompatTaskInfo.topActivityAppBounds.set(0, 0, 1200, 1600)
                } else if (
                    deviceOrientation == ORIENTATION_PORTRAIT &&
                        screenOrientation == SCREEN_ORIENTATION_LANDSCAPE
                ) {
                    // Letterbox to landscape size
                    appCompatTaskInfo.isTopActivityLetterboxed = true
                    appCompatTaskInfo.topActivityAppBounds.set(0, 0, 1600, 1200)
                }
            }
            isVisible = visible
        }
        if (background) {
            whenever(shellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(null)
            whenever(recentTasksController.findTaskInBackground(task.taskId))
                .thenReturn(createRecentTaskInfo(taskId = task.taskId, displayId = INVALID_DISPLAY))
        } else {
            whenever(shellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
            runningTasks.add(task)
        }
        return task
    }

    private fun setUpLandscapeDisplay() {
        whenever(displayLayout.width()).thenReturn(DISPLAY_DIMENSION_LONG)
        whenever(displayLayout.height()).thenReturn(DISPLAY_DIMENSION_SHORT)
        val stableBounds =
            Rect(0, 0, DISPLAY_DIMENSION_LONG, DISPLAY_DIMENSION_SHORT - TASKBAR_FRAME_HEIGHT)
        whenever(displayLayout.getStableBoundsForDesktopMode(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
    }

    private fun setUpPortraitDisplay() {
        whenever(displayLayout.width()).thenReturn(DISPLAY_DIMENSION_SHORT)
        whenever(displayLayout.height()).thenReturn(DISPLAY_DIMENSION_LONG)
        val stableBounds =
            Rect(0, 0, DISPLAY_DIMENSION_SHORT, DISPLAY_DIMENSION_LONG - TASKBAR_FRAME_HEIGHT)
        whenever(displayLayout.getStableBoundsForDesktopMode(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
    }

    private fun setUpSplitScreenTask(displayId: Int = DEFAULT_DISPLAY): RunningTaskInfo {
        val task =
            createSplitScreenTask(displayId).apply {
                baseActivity = ComponentName(/* pkg= */ "", /* cls= */ "")
            }
        whenever(splitScreenController.isTaskInSplitScreen(task.taskId)).thenReturn(true)
        whenever(shellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        runningTasks.add(task)
        return task
    }

    private fun markTaskVisible(task: RunningTaskInfo) {
        taskRepository.updateTask(task.displayId, task.taskId, isVisible = true, TASK_BOUNDS)
    }

    private fun markTaskHidden(task: RunningTaskInfo) {
        taskRepository.updateTask(task.displayId, task.taskId, isVisible = false, TASK_BOUNDS)
    }

    private fun getLatestWct(
        @WindowManager.TransitionType type: Int = TRANSIT_OPEN,
        handlerClass: Class<out TransitionHandler>? = null,
    ): WindowContainerTransaction {
        val arg = argumentCaptor<WindowContainerTransaction>()
        if (handlerClass == null) {
            verify(transitions).startTransition(eq(type), arg.capture(), isNull())
        } else {
            verify(transitions).startTransition(eq(type), arg.capture(), isA(handlerClass))
        }
        return arg.lastValue
    }

    private fun getLatestToggleResizeDesktopTaskWct(
        currentBounds: Rect? = null
    ): WindowContainerTransaction {
        val arg = argumentCaptor<WindowContainerTransaction>()
        verify(toggleResizeDesktopTaskTransitionHandler, atLeastOnce())
            .startTransition(arg.capture(), eq(currentBounds), isNull(), any())
        return arg.lastValue
    }

    private fun getLatestDesktopMixedTaskWct(
        @WindowManager.TransitionType type: Int = TRANSIT_OPEN
    ): WindowContainerTransaction {
        val arg = argumentCaptor<WindowContainerTransaction>()
        verifyStartLaunchTransition(transitionType = type, wctArgCaptor = arg)
        return arg.lastValue
    }

    private fun mockStartLaunchTransition(
        transition: IBinder = Binder(),
        @WindowManager.TransitionType transitionType: Int? = null,
        wctArgCaptor: KArgumentCaptor<WindowContainerTransaction>? = null,
        taskId: Int? = null,
        dragEvent: DragEvent? = null,
    ) =
        whenever(
                desktopMixedTransitionHandler.startLaunchTransition(
                    if (transitionType != null) eq(transitionType) else anyInt(),
                    if (wctArgCaptor != null) wctArgCaptor.capture() else any(),
                    if (taskId != null) eq(taskId) else anyOrNull(),
                    closingTopTransparentTaskId = anyOrNull(),
                    exitingImmersiveTask = anyOrNull(),
                    if (dragEvent != null) eq(dragEvent) else anyOrNull(),
                )
            )
            .thenReturn(transition)

    private fun verifyStartLaunchTransition(
        verificationMode: VerificationMode = times(1),
        @WindowManager.TransitionType transitionType: Int? = null,
        wctArgCaptor: KArgumentCaptor<WindowContainerTransaction>? = null,
        taskId: Int? = null,
        closingTopTransparentTaskId: Int? = null,
        dragEvent: DragEvent? = null,
    ) =
        verify(desktopMixedTransitionHandler, verificationMode)
            .startLaunchTransition(
                if (transitionType != null) eq(transitionType) else anyInt(),
                if (wctArgCaptor != null) wctArgCaptor.capture() else any(),
                if (taskId != null) eq(taskId) else anyOrNull(),
                if (closingTopTransparentTaskId != null) eq(closingTopTransparentTaskId)
                else anyOrNull(),
                exitingImmersiveTask = anyOrNull(),
                if (dragEvent != null) eq(dragEvent) else anyOrNull(),
            )

    private fun getLatestTransition(): WindowContainerTransaction {
        val arg = argumentCaptor<WindowContainerTransaction>()
        verify(transitions).startTransition(any(), arg.capture(), anyOrNull())
        return arg.lastValue
    }

    private fun getLatestEnterDesktopWct(): WindowContainerTransaction {
        val arg = argumentCaptor<WindowContainerTransaction>()
        verify(enterDesktopTransitionHandler).moveToDesktop(arg.capture(), any())
        return arg.lastValue
    }

    private fun getLatestDragToDesktopWct(): WindowContainerTransaction {
        val arg = argumentCaptor<WindowContainerTransaction>()
        verify(dragToDesktopTransitionHandler).finishDragToDesktopTransition(arg.capture())
        return arg.lastValue
    }

    private fun getLatestExitDesktopWct(): WindowContainerTransaction {
        val arg = argumentCaptor<WindowContainerTransaction>()
        verify(exitDesktopTransitionHandler).startTransition(any(), arg.capture(), any(), any())
        return arg.lastValue
    }

    private fun findBoundsChange(wct: WindowContainerTransaction, task: RunningTaskInfo): Rect? =
        wct.changes.entries
            .find { (token, change) ->
                token == task.token.asBinder() &&
                    (change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0
            }
            ?.value
            ?.configuration
            ?.windowConfiguration
            ?.bounds

    private fun verifyWCTNotExecuted() {
        verify(transitions, never()).startTransition(anyInt(), any(), isNull())
    }

    private fun verifyExitDesktopWCTNotExecuted() {
        verify(exitDesktopTransitionHandler, never()).startTransition(any(), any(), any(), any())
    }

    private fun verifyEnterDesktopWCTNotExecuted() {
        verify(enterDesktopTransitionHandler, never()).moveToDesktop(any(), any())
    }

    private fun createTransition(
        task: RunningTaskInfo?,
        @WindowManager.TransitionType type: Int = TRANSIT_OPEN,
        userChange: TransitionRequestInfo.UserChange? = null,
    ): TransitionRequestInfo {
        return TransitionRequestInfo(type, task, /* remoteTransition= */ null).apply {
            userChange?.let { setUserChange(it) }
        }
    }

    private fun createTaskMoveTransition(
        task: RunningTaskInfo?,
        requestedDisplayId: Int,
        requestedBounds: Rect,
    ): TransitionRequestInfo {
        return createTransition(task, TRANSIT_CHANGE).apply {
            setRequestedLocation(
                TransitionRequestInfo.RequestedLocation(requestedDisplayId, requestedBounds)
            )
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_WALLPAPER_DIM_ISSUES_26Q2)
    fun snapToHalfScreen_singleTiled_taskbarCornerNotRounded() {
        whenever(snapEventHandler.snapToHalfScreen(any(), any(), any())).thenReturn(true)

        val task1 = setUpFreeformTask(bounds = TASK_BOUNDS)
        val task2 = setUpFreeformTask(bounds = TASK_BOUNDS)

        // Mock that task2 is left-tiled after snapping.
        taskRepository.addLeftTiledTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            taskId = task2.taskId,
            deskId = DEFAULT_DISPLAY,
        )

        controller.snapToHalfScreen(
            task2,
            mockSurface,
            TASK_BOUNDS,
            SnapPosition.LEFT,
            ResizeTrigger.SNAP_LEFT_MENU,
            InputMethod.TOUCH,
        )

        // Verify that onTaskbarCornerRoundingUpdate(false) is called because it's just
        // single-tiled.
        verify(desktopRemoteListener).onTaskbarCornerRoundingUpdate(eq(false), eq(DEFAULT_DISPLAY))
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_WALLPAPER_DIM_ISSUES_26Q2)
    fun snapToHalfScreen_doubleTiled_taskbarCornerRounded() {
        whenever(snapEventHandler.snapToHalfScreen(any(), any(), any())).thenReturn(true)

        val task1 = setUpFreeformTask(bounds = TASK_BOUNDS)
        val task2 = setUpFreeformTask(bounds = TASK_BOUNDS)

        // Mock that task1 is already right-tiled.
        taskRepository.addRightTiledTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            taskId = task1.taskId,
            deskId = DEFAULT_DISPLAY,
        )

        // Mock that task2 is left-tiled after snapping.
        taskRepository.addLeftTiledTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            taskId = task2.taskId,
            deskId = DEFAULT_DISPLAY,
        )

        controller.snapToHalfScreen(
            task2,
            mockSurface,
            TASK_BOUNDS,
            SnapPosition.LEFT,
            ResizeTrigger.SNAP_LEFT_MENU,
            InputMethod.TOUCH,
        )

        // Verify that onTaskbarCornerRoundingUpdate(true) is called because it's now double-tiled.
        verify(desktopRemoteListener).onTaskbarCornerRoundingUpdate(eq(true), eq(DEFAULT_DISPLAY))
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_WALLPAPER_DIM_ISSUES_26Q2)
    fun snapToHalfScreen_singleTiledButAnotherTaskIsMaximized_taskbarCornerRounded() {
        whenever(snapEventHandler.snapToHalfScreen(any(), any(), any())).thenReturn(true)

        val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
        val task1 = setUpFreeformTask(bounds = stableBounds)
        val task2 = setUpFreeformTask(bounds = TASK_BOUNDS)

        // Mock that task2 is left-tiled after snapping.
        taskRepository.addLeftTiledTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            taskId = task2.taskId,
            deskId = DEFAULT_DISPLAY,
        )

        controller.snapToHalfScreen(
            task2,
            mockSurface,
            TASK_BOUNDS,
            SnapPosition.LEFT,
            ResizeTrigger.SNAP_LEFT_MENU,
            InputMethod.TOUCH,
        )

        // Verify that onTaskbarCornerRoundingUpdate(true) is called although it's single-tiled
        // because another task is maximized.
        verify(desktopRemoteListener).onTaskbarCornerRoundingUpdate(eq(true), eq(DEFAULT_DISPLAY))
    }

    @Test
    @EnableFlags(Flags.FLAG_FIX_WALLPAPER_DIM_ISSUES_26Q2)
    fun snapToHalfScreen_maximizedTaskIsSnappedToSingleTiled_taskbarCornerNotRounded() {
        whenever(snapEventHandler.snapToHalfScreen(any(), any(), any())).thenReturn(true)

        val stableBounds = Rect().also { displayLayout.getStableBounds(it) }
        val task1 = setUpFreeformTask(bounds = stableBounds)
        val task2 = setUpFreeformTask(bounds = TASK_BOUNDS)

        // Mock that task1 is left-tiled after snapping.
        taskRepository.addLeftTiledTaskToDesk(
            displayId = DEFAULT_DISPLAY,
            taskId = task1.taskId,
            deskId = DEFAULT_DISPLAY,
        )

        controller.snapToHalfScreen(
            task1,
            mockSurface,
            TASK_BOUNDS,
            SnapPosition.LEFT,
            ResizeTrigger.SNAP_LEFT_MENU,
            InputMethod.TOUCH,
        )

        // Verify that onTaskbarCornerRoundingUpdate(false) is called because it's now just
        // single-tiled.
        verify(desktopRemoteListener).onTaskbarCornerRoundingUpdate(eq(false), eq(DEFAULT_DISPLAY))
    }

    private companion object {
        const val SECOND_DISPLAY = 2
        const val SECOND_DISPLAY_ON_RECONNECT = 3
        const val DEFAULT_DISPLAY_UNIQUE_ID = "UNIQUE_ID_1"
        const val SECOND_DISPLAY_UNIQUE_ID = "UNIQUE_ID_2"
        val STABLE_BOUNDS = Rect(0, 0, 1000, 1000)
        const val MAX_TASK_LIMIT = 6
        private const val TASKBAR_FRAME_HEIGHT = 200
        private const val FLOAT_TOLERANCE = 0.005f
        private const val DEFAULT_DESK_ID = 0
        // For testing disconnecting a display containing a desk.
        private const val DISCONNECTED_DESK_ID = 200
        private val TASK_BOUNDS = Rect(100, 100, 300, 300)

        private const val TO_DESKTOP_ANIM_DURATION = 336
    }
}

private fun WindowContainerTransaction.assertIndexInBounds(index: Int) {
    assertWithMessage("WCT does not have a hierarchy operation at index $index")
        .that(hierarchyOps.size)
        .isGreaterThan(index)
}

private fun WindowContainerTransaction.assertHop(
    predicate: (WindowContainerTransaction.HierarchyOp) -> Boolean
) {
    assertThat(hierarchyOps.any(predicate)).isTrue()
}

private fun WindowContainerTransaction.assertWithoutHop(
    predicate: (WindowContainerTransaction.HierarchyOp) -> Boolean
) {
    assertThat(hierarchyOps.none(predicate)).isTrue()
}

private fun WindowContainerTransaction.indexOfReorder(
    token: WindowContainerToken,
    toTop: Boolean? = null,
): Int {
    val hop = hierarchyOps.singleOrNull(ReorderPredicate(token, toTop)) ?: return -1
    return hierarchyOps.indexOf(hop)
}

private fun WindowContainerTransaction.indexOfReorder(
    task: RunningTaskInfo,
    toTop: Boolean? = null,
): Int {
    return indexOfReorder(task.token, toTop)
}

private class ReorderPredicate(
    val token: WindowContainerToken,
    val toTop: Boolean? = null,
    val includingParents: Boolean? = null,
) : ((WindowContainerTransaction.HierarchyOp) -> Boolean) {
    override fun invoke(hop: WindowContainerTransaction.HierarchyOp): Boolean =
        hop.type == HIERARCHY_OP_TYPE_REORDER &&
            (toTop == null || hop.toTop == toTop) &&
            (includingParents == null || hop.includingParents() == includingParents) &&
            hop.container == token.asBinder()
}

private class ReparentPredicate(
    val token: WindowContainerToken,
    val parentToken: WindowContainerToken? = null,
    val toTop: Boolean? = null,
) : ((WindowContainerTransaction.HierarchyOp) -> Boolean) {
    override fun invoke(hop: WindowContainerTransaction.HierarchyOp): Boolean =
        hop.isReparent &&
            (toTop == null || hop.toTop == toTop) &&
            hop.container == token.asBinder() &&
            (parentToken == null || hop.newParent == parentToken.asBinder())
}

private fun WindowContainerTransaction.assertReorder(
    task: RunningTaskInfo,
    toTop: Boolean? = null,
    includingParents: Boolean? = null,
) {
    assertReorder(task.token, toTop, includingParents)
}

private fun WindowContainerTransaction.assertReorder(
    token: WindowContainerToken,
    toTop: Boolean? = null,
    includingParents: Boolean? = null,
) {
    assertHop(ReorderPredicate(token, toTop, includingParents))
}

private fun WindowContainerTransaction.assertReorderAt(
    index: Int,
    task: RunningTaskInfo,
    toTop: Boolean? = null,
    includingParents: Boolean? = null,
) {
    assertIndexInBounds(index)
    val op = hierarchyOps[index]
    assertThat(op.type).isEqualTo(HIERARCHY_OP_TYPE_REORDER)
    assertThat(op.container).isEqualTo(task.token.asBinder())
    toTop?.let { assertThat(op.toTop).isEqualTo(it) }
    includingParents?.let { assertThat(op.includingParents()).isEqualTo(it) }
}

private fun WindowContainerTransaction.assertReorderAt(
    index: Int,
    token: WindowContainerToken,
    toTop: Boolean? = null,
) {
    assertIndexInBounds(index)
    val op = hierarchyOps[index]
    assertThat(op.type).isEqualTo(HIERARCHY_OP_TYPE_REORDER)
    assertThat(op.container).isEqualTo(token.asBinder())
    toTop?.let { assertThat(op.toTop).isEqualTo(it) }
}

private fun WindowContainerTransaction.assertReorderSequence(vararg tasks: RunningTaskInfo) {
    for (i in tasks.indices) {
        assertReorderAt(i, tasks[i])
    }
}

/** Checks if the reorder hierarchy operations in [range] correspond to [tasks] list */
private fun WindowContainerTransaction.assertReorderSequenceInRange(
    range: IntRange,
    vararg tasks: RunningTaskInfo,
) {
    assertThat(hierarchyOps.slice(range).map { it.type to it.container })
        .containsExactlyElementsIn(tasks.map { HIERARCHY_OP_TYPE_REORDER to it.token.asBinder() })
        .inOrder()
}

private fun WindowContainerTransaction.assertRemove(token: WindowContainerToken) {
    assertThat(
            hierarchyOps.any { hop ->
                hop.container == token.asBinder() && hop.type == HIERARCHY_OP_TYPE_REMOVE_TASK
            }
        )
        .isTrue()
}

private fun WindowContainerTransaction.assertRemoveAt(index: Int, token: WindowContainerToken) {
    assertIndexInBounds(index)
    val op = hierarchyOps[index]
    assertThat(op.type).isEqualTo(HIERARCHY_OP_TYPE_REMOVE_TASK)
    assertThat(op.container).isEqualTo(token.asBinder())
}

private fun WindowContainerTransaction.assertNoRemoveAt(index: Int, token: WindowContainerToken) {
    assertIndexInBounds(index)
    val op = hierarchyOps[index]
    assertThat(op.type).isEqualTo(HIERARCHY_OP_TYPE_REMOVE_TASK)
    assertThat(op.container).isEqualTo(token.asBinder())
}

private fun WindowContainerTransaction.hasRemoveAt(index: Int, token: WindowContainerToken) {
    assertIndexInBounds(index)
    val op = hierarchyOps[index]
    assertThat(op.type).isEqualTo(HIERARCHY_OP_TYPE_REMOVE_TASK)
    assertThat(op.container).isEqualTo(token.asBinder())
}

private fun WindowContainerTransaction.assertPendingIntent(intent: Intent) {
    assertHop { hop ->
        hop.type == HIERARCHY_OP_TYPE_PENDING_INTENT &&
            hop.pendingIntent?.intent?.component == intent.component &&
            hop.pendingIntent?.intent?.categories == intent.categories
    }
}

private fun WindowContainerTransaction.assertWithoutPendingIntent(intent: Intent) {
    assertWithoutHop { hop ->
        hop.type == HIERARCHY_OP_TYPE_PENDING_INTENT &&
            hop.pendingIntent?.intent?.component == intent.component
    }
}

private fun WindowContainerTransaction.assertPendingIntentAt(index: Int, intent: Intent) {
    assertIndexInBounds(index)
    val op = hierarchyOps[index]
    assertThat(op.type).isEqualTo(HIERARCHY_OP_TYPE_PENDING_INTENT)
    assertThat(op.pendingIntent?.intent?.component).isEqualTo(intent.component)
    assertThat(op.pendingIntent?.intent?.categories).isEqualTo(intent.categories)
}

private fun WindowContainerTransaction.assertPendingIntentActivityOptionsLaunchDisplayId(
    displayId: Int
) {
    assertHop { hop ->
        hop.launchOptions != null && ActivityOptions(hop.launchOptions).launchDisplayId == displayId
    }
}

private fun WindowContainerTransaction.assertPendingIntentActivityOptionsLaunchDisplayIdAt(
    index: Int,
    displayId: Int,
) {
    assertIndexInBounds(index)
    val op = hierarchyOps[index]
    if (op.launchOptions != null) {
        val options = ActivityOptions(op.launchOptions)
        assertThat(options.launchDisplayId).isEqualTo(displayId)
    }
}

private fun WindowContainerTransaction.assertLaunchTask(taskId: Int, windowingMode: Int) {
    val keyLaunchWindowingMode = "android.activity.windowingMode"

    assertHop { hop ->
        hop.type == HIERARCHY_OP_TYPE_LAUNCH_TASK &&
            hop.launchOptions?.getInt(LAUNCH_KEY_TASK_ID) == taskId &&
            hop.launchOptions?.getInt(keyLaunchWindowingMode, WINDOWING_MODE_UNDEFINED) ==
                windowingMode
    }
}

private fun WindowContainerTransaction.assertLaunchTaskOnDisplay(displayId: Int) {
    val keyLaunchWindowingMode = "android.activity.windowingMode"
    val keyLaunchDisplayId = "android.activity.launchDisplayId"

    assertHop { hop -> hop.launchOptions?.getInt(keyLaunchDisplayId, DEFAULT_DISPLAY) == displayId }
}

private fun WindowContainerTransaction.assertLaunchTaskAt(
    index: Int,
    taskId: Int,
    windowingMode: Int,
) {
    val keyLaunchWindowingMode = "android.activity.windowingMode"

    assertIndexInBounds(index)
    val op = hierarchyOps[index]
    assertThat(op.type).isEqualTo(HIERARCHY_OP_TYPE_LAUNCH_TASK)
    assertThat(op.launchOptions?.getInt(LAUNCH_KEY_TASK_ID)).isEqualTo(taskId)
    assertThat(op.launchOptions?.getInt(keyLaunchWindowingMode, WINDOWING_MODE_UNDEFINED))
        .isEqualTo(windowingMode)
}

private fun WindowContainerTransaction?.anyDensityConfigChange(
    token: WindowContainerToken
): Boolean {
    return this?.changes?.any { change ->
        change.key == token.asBinder() && ((change.value.configSetMask and CONFIG_DENSITY) != 0)
    } ?: false
}

private fun WindowContainerTransaction?.anyWindowingModeChange(
    token: WindowContainerToken
): Boolean {
    return this?.changes?.any { change ->
        change.key == token.asBinder() && change.value.windowingMode >= 0
    } ?: false
}

private fun createRecentTaskInfo(taskId: Int, displayId: Int = DEFAULT_DISPLAY): RecentTaskInfo =
    RecentTaskInfo().apply {
        this.taskId = taskId
        this.displayId = displayId
        token = WindowContainerToken(mock(IWindowContainerToken::class.java))
        positionInParent = Point()
        userId = ActivityManager.getCurrentUser()
    }

private fun RootTaskDisplayAreaOrganizer.setTouchFirst(displayId: Int) {
    val tda = getDisplayAreaInfo(displayId) ?: return
    tda.configuration.windowConfiguration.windowingMode = TOUCH_FIRST_DISPLAY_WINDOWING_MODE
}

private fun RootTaskDisplayAreaOrganizer.setDesktopFirst(displayId: Int) {
    val tda = getDisplayAreaInfo(displayId) ?: return
    tda.configuration.windowConfiguration.windowingMode = DESKTOP_FIRST_DISPLAY_WINDOWING_MODE
}
