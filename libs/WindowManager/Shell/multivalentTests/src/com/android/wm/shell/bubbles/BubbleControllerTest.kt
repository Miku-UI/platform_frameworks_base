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

package com.android.wm.shell.bubbles

import android.app.ActivityManager
import android.app.Notification
import android.app.TaskInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.content.res.Resources
import android.graphics.Insets
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.UserHandle
import android.os.UserManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Size
import android.view.IWindowManager
import android.view.InsetsSource
import android.view.InsetsState
import android.view.Surface.ROTATION_0
import android.view.Surface.ROTATION_90
import android.view.SurfaceControl
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.TransitionInfo
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.internal.logging.InstanceIdSequence
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.internal.protolog.ProtoLog
import com.android.internal.statusbar.IStatusBarService
import com.android.wm.shell.Flags
import com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_BAR
import com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
import com.android.wm.shell.Flags.FLAG_UPDATE_BUBBLE_BOUNDS_DURING_ROTATION
import com.android.wm.shell.R
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.bubbles.Bubbles.BubbleExpandListener
import com.android.wm.shell.bubbles.Bubbles.DISMISS_TASK_FINISHED
import com.android.wm.shell.bubbles.Bubbles.DISMISS_USER_GESTURE
import com.android.wm.shell.bubbles.Bubbles.SysuiProxy
import com.android.wm.shell.bubbles.logging.BubbleLogger
import com.android.wm.shell.bubbles.logging.BubbleLogger.Event.BUBBLE_CREATED_FROM_ALL_APPS_ICON_MENU
import com.android.wm.shell.bubbles.logging.BubbleLogger.Event.BUBBLE_CREATED_FROM_NOTIF
import com.android.wm.shell.bubbles.logging.BubbleLogger.Event.BUBBLE_CREATED_FROM_NOTIF_BUBBLE_BUTTON
import com.android.wm.shell.bubbles.logging.BubbleSessionTracker
import com.android.wm.shell.bubbles.logging.BubbleSessionTrackerImpl
import com.android.wm.shell.bubbles.storage.BubblePersistentRepository
import com.android.wm.shell.bubbles.transitions.BubbleTransitions
import com.android.wm.shell.bubbles.user.data.FakeBubbleUserResolver
import com.android.wm.shell.common.DisplayChangeController.OnDisplayChangingListener
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayImeController
import com.android.wm.shell.common.DisplayInsetsController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.FloatingContentCoordinator
import com.android.wm.shell.common.HomeIntentProvider
import com.android.wm.shell.common.ImeListener
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.common.TaskStackListenerImpl
import com.android.wm.shell.common.TestShellExecutor
import com.android.wm.shell.common.TestSyncExecutor
import com.android.wm.shell.draganddrop.DragAndDropController
import com.android.wm.shell.shared.TransactionPool
import com.android.wm.shell.shared.bubbles.BubbleFlagHelper
import com.android.wm.shell.shared.bubbles.DeviceConfig
import com.android.wm.shell.shared.bubbles.FakeBubbleFeatureConfig
import com.android.wm.shell.shared.bubbles.logging.EntryPoint
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.taskview.TaskViewRepository
import com.android.wm.shell.taskview.TaskViewTaskController
import com.android.wm.shell.taskview.TaskViewTransitions
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TRANSIT_CONVERT_TO_BUBBLE
import com.android.wm.shell.transition.Transitions.TransitionHandler
import com.android.wm.shell.unfold.ShellUnfoldProgressProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.Optional
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

/**
 * Tests for [BubbleController].
 *
 * Build/Install/Run:
 * - atest WMShellRobolectricTests:BubbleControllerTest (on host)
 * - atest WMShellMultivalentTestsOnDevice:BubbleControllerTest (on device)
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleControllerTest {

    @get:Rule val setFlagsRule = SetFlagsRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val uiEventLoggerFake = UiEventLoggerFake()
    private val bubbleAppInfoProvider = FakeBubbleAppInfoProvider()
    private val unfoldProgressProvider = FakeShellUnfoldProgressProvider()
    private val displayImeController = mock<DisplayImeController>()
    private val displayInsetsController = mock<DisplayInsetsController>()
    private val splitScreenController = mock<SplitScreenController>()
    private val taskStackListener = mock<TaskStackListenerImpl>()
    private val transitions = mock<Transitions>()
    private val taskViewTransitions = mock<TaskViewTransitions>()
    private val userManager = mock<UserManager>()
    private val windowManager = mock<WindowManager>()
    private val bubbleHelper = mock<BubbleHelper>()

    private lateinit var bubbleController: BubbleController
    private lateinit var bubblePositioner: BubblePositioner
    private lateinit var bubbleLogger: BubbleLogger
    private lateinit var mainExecutor: TestShellExecutor
    private lateinit var bgExecutor: TestShellExecutor
    private lateinit var bubbleData: BubbleData
    private lateinit var eduController: BubbleEducationController
    private lateinit var displayController: DisplayController
    private lateinit var imeListener: ImeListener
    private lateinit var bubbleTransitions: BubbleTransitions
    private lateinit var sessionTracker: BubbleSessionTracker
    private lateinit var bubbleViewInfoTaskFactory: FakeBubbleViewInfoTaskFactory
    private lateinit var displayLayout: DisplayLayout

    private var isStayAwakeOnFold = false

    private val deviceConfigFolded =
        DeviceConfig(
            windowBounds = Rect(0, 0, 700, 1000),
            isLargeScreen = false,
            isSmallTablet = false,
            isLandscape = false,
            isRtl = false,
            insets = Insets.of(10, 20, 30, 40),
        )
    private val deviceConfigUnfolded =
        deviceConfigFolded.copy(
            windowBounds = Rect(0, 0, 1400, 2000),
            isLargeScreen = true,
            isSmallTablet = true,
        )

    @Before
    fun setUp() {
        ProtoLog.REQUIRE_PROTOLOGTOOL = false
        ProtoLog.init()

        bubbleLogger = BubbleLogger(uiEventLoggerFake)
        val instanceIdSequence = InstanceIdSequence(/* instanceIdMax= */ 10)
        sessionTracker = BubbleSessionTrackerImpl(instanceIdSequence, bubbleLogger)
        eduController = BubbleEducationController(context)

        mainExecutor = TestShellExecutor()
        bgExecutor = TestShellExecutor()

        val realWindowManager = context.getSystemService<WindowManager>()!!
        val realDefaultDisplay = realWindowManager.defaultDisplay
        // Tests don't have permission to add our window to windowManager, so we mock it :(
        windowManager.stub {
            // But we do want the metrics from the real one
            on { currentWindowMetrics } doReturn realWindowManager.currentWindowMetrics
            on { defaultDisplay } doReturn realDefaultDisplay
        }
        displayLayout = DisplayLayout(context, realDefaultDisplay)
        displayController =
            mock<DisplayController> { on { getDisplayLayout(anyInt()) } doReturn displayLayout }

        bubblePositioner = BubblePositioner(context, deviceConfigFolded)

        bubbleData =
            BubbleData(
                context,
                bubbleLogger,
                bubblePositioner,
                eduController,
                bubbleAppInfoProvider,
                mainExecutor,
                bgExecutor,
            )

        val shellTaskOrganizer =
            ShellTaskOrganizer(
                mock<ShellInit>(),
                ShellCommandHandler(),
                mock<RootTaskDisplayAreaOrganizer>(),
                null,
                Optional.empty(),
                Optional.empty(),
                TestSyncExecutor(),
            )

        bubbleViewInfoTaskFactory =
            FakeBubbleViewInfoTaskFactory(
                bubblePositioner,
                bubbleAppInfoProvider,
                mainExecutor,
                bgExecutor,
                FakeBubbleUserResolver(),
            )

        bubbleTransitions =
            BubbleTransitions(
                context,
                transitions,
                shellTaskOrganizer,
                mock<TaskViewRepository>(),
                bubbleData,
                taskViewTransitions,
                bubbleViewInfoTaskFactory,
                bubbleHelper,
            )

        bubbleController =
            createBubbleController(
                bubbleData,
                windowManager,
                shellTaskOrganizer,
                bubbleLogger,
                bubblePositioner,
                mainExecutor,
                bgExecutor,
            )

        val insetsChangedListenerCaptor = argumentCaptor<ImeListener>()
        verify(displayInsetsController)
            .addInsetsChangedListener(anyInt(), insetsChangedListenerCaptor.capture())
        imeListener = insetsChangedListenerCaptor.lastValue
    }

    @After
    fun tearDown() {
        getInstrumentation().waitForIdleSync()
    }

    @Test
    fun onInit_addsBubbleTaskStackListener() {
        verify(taskStackListener).addListener(isA<BubbleTaskStackListener>())
    }

    @Test
    fun showOrHideNotesBubble_createsNoteBubble() {
        val intent = Intent(context, TestActivity::class.java)
        intent.setPackage(context.packageName)
        val user = UserHandle.of(0)
        val expectedKey = Bubble.getNoteBubbleKeyForApp(intent.getPackage(), user)

        getInstrumentation().runOnMainSync {
            bubbleController.showOrHideNotesBubble(intent, user, mock<Icon>())
        }
        getInstrumentation().waitForIdleSync()

        assertThat(bubbleController.hasBubbles()).isTrue()
        assertThat(bubbleData.getAnyBubbleWithKey(expectedKey)).isNotNull()
        assertThat(bubbleData.getAnyBubbleWithKey(expectedKey)!!.isNote).isTrue()
    }

    @Test
    fun onDeviceLocked_expanded_imeHidden_shouldCollapseImmediately() {
        val bubble = createBubble("key")
        bubblePositioner.setImeVisible(false, 0)
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true,
            )
        }
        assertThat(bubbleData.hasBubbles()).isTrue()

        // expand and lock the device
        getInstrumentation().runOnMainSync {
            bubbleController.expandStackAndSelectBubble(bubble)
            assertThat(bubbleData.isExpanded).isTrue()
            bubbleController.onStatusBarStateChanged(/* isShade= */ false)
        }
        // verify that we collapsed immediately, since the IME is hidden
        assertThat(bubbleData.isExpanded).isFalse()
    }

    @Test
    fun onDeviceLocked_expanded_imeVisible_shouldCollapseImmediately() {
        val bubble = createBubble("key")
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true,
            )
        }
        assertThat(bubbleData.hasBubbles()).isTrue()

        // expand and show the IME. then lock the device
        val imeVisibleInsetsState = createFakeInsetsState(imeVisible = true)
        getInstrumentation().runOnMainSync {
            bubbleController.expandStackAndSelectBubble(bubble)
            assertThat(bubbleData.isExpanded).isTrue()
            imeListener.insetsChanged(imeVisibleInsetsState)
            assertThat(bubblePositioner.isImeVisible).isTrue()
            bubbleController.onStatusBarStateChanged(/* isShade= */ false)
        }

        assertThat(bubbleData.isExpanded).isFalse()
        // collapsing while the device is locked goes through display ime controller
        verify(displayImeController).hideImeForBubblesWhenLocked(anyInt())
    }

    @Test
    fun setTaskViewVisible_callsBaseTransitionsWithReorder() {
        val baseTransitions = mock<TaskViewTransitions>()
        val taskView = mock<TaskViewTaskController>()
        val bubbleTaskViewController = bubbleController.BubbleTaskViewController(baseTransitions)

        bubbleTaskViewController.setTaskViewVisible(taskView, true /* visible */)

        if (BubbleFlagHelper.enableCreateAnyBubble()) {
            verify(baseTransitions)
                .setTaskViewVisible(
                    taskView,
                    true, /* visible */
                    true, /* reorder */
                    false, /* syncHiddenWithVisibilityOnReorder */
                    false, /* nonBlockingIfPossible */
                    null, /* overrideTransaction */
                )
        } else {
            verify(baseTransitions).setTaskViewVisible(taskView, true /* visible */)
        }
    }

    @Test
    fun setTaskViewVisible_lastBubbleRemoval_skipsTaskViewHiding() {
        assumeTrue(BubbleFlagHelper.enableCreateAnyBubble())

        val baseTransitions = mock<TaskViewTransitions>()
        val taskView = mock<TaskViewTaskController>()
        val bubbleTaskViewController = bubbleController.BubbleTaskViewController(baseTransitions)

        bubbleTaskViewController.setTaskViewVisible(taskView, false /* visible */)

        verify(baseTransitions, never())
            .setTaskViewVisible(
                any(), /* taskView */
                any(), /* visible */
                any(), /* reorder */
                any(), /* syncHiddenWithVisibilityOnReorder */
                any(), /* nonBlockingIfPossible */
                any(), /* overrideTransaction */
            )
    }

    @Test
    fun hasStableBubbleForTask_whenBubbleIsCollapsed_returnsTrue() {
        val taskId = 777
        val bubble = createBubble("key", taskId)
        getInstrumentation().runOnMainSync {
            bubbleData.notificationEntryUpdated(
                bubble,
                true, /* suppressFlyout */
                true,
            /* showInShade= */ )
        }

        assertThat(bubbleController.hasStableBubbleForTask(taskId)).isTrue()
    }

    @Test
    fun hasStableBubbleForTask_whenBubbleInTransition_returnsFalse() {
        val taskId = 777
        val bubble = createBubble("key", taskId).apply { currentTransition = mock() }
        getInstrumentation().runOnMainSync {
            bubbleData.notificationEntryUpdated(
                bubble,
                true, /* suppressFlyout */
                true,
            /* showInShade= */ )
        }

        assertThat(bubbleController.hasStableBubbleForTask(taskId)).isFalse()
    }

    @Test
    fun hasStableBubbleForTask_noBubble_returnsFalse() {
        val bubble = createBubble("key", taskId = 123)
        getInstrumentation().runOnMainSync {
            bubbleData.notificationEntryUpdated(
                bubble,
                true, /* suppressFlyout */
                true,
            /* showInShade= */ )
        }

        assertThat(bubbleController.hasStableBubbleForTask(777)).isFalse()
    }

    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun expandStackAndSelectBubbleForExistingTransition_reusesExistingBubble() {
        assumeTrue(BubbleFlagHelper.enableCreateAnyBubble())

        val bubbleStateListener = FakeBubblesStateListener()

        // switch to bubble bar mode because the transition currently requires bubble layer view
        bubblePositioner.update(deviceConfigUnfolded)
        bubblePositioner.isShowingInBubbleBar = true
        getInstrumentation().runOnMainSync {
            bubbleController.setLauncherHasBubbleBar(true)
            bubbleController.registerBubbleStateListener(bubbleStateListener)
        }

        val taskInfo =
            ActivityManager.RunningTaskInfo().apply {
                taskId = 123
                baseActivity = COMPONENT
            }
        val bubble = createAppBubble(taskInfo)
        getInstrumentation().runOnMainSync {
            bubbleData.notificationEntryUpdated(
                bubble,
                true, /* suppressFlyout */
                true,
            /* showInShade= */ )
        }

        getInstrumentation().runOnMainSync {
            bubbleController.expandStackAndSelectBubbleForExistingTransition(
                taskInfo,
                mock(), /* transition */
            ) {}
        }

        assertThat(bubbleStateListener.lastUpdate!!.bubbleBarLocation).isNull()
        assertThat(bubbleData.selectedBubble).isEqualTo(bubble)
        assertThat(bubbleController.isStackExpanded).isTrue()
    }

    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun expandStackAndSelectBubbleForExistingTransition_newBubble() {
        assumeTrue(BubbleFlagHelper.enableCreateAnyBubble())

        // switch to bubble bar mode because the transition currently requires bubble layer view
        bubblePositioner.update(deviceConfigUnfolded)
        bubblePositioner.isShowingInBubbleBar = true
        getInstrumentation().runOnMainSync {
            bubbleController.setLauncherHasBubbleBar(true)
            bubbleController.registerBubbleStateListener(FakeBubblesStateListener())
        }

        val taskInfo =
            ActivityManager.RunningTaskInfo().apply {
                baseActivity = COMPONENT
                taskId = 123
                token = mock<WindowContainerToken>()
            }

        var transitionHandler: TransitionHandler? = null
        getInstrumentation().runOnMainSync {
            transitionHandler =
                bubbleController.expandStackAndSelectBubbleForExistingTransition(
                    taskInfo,
                    mock(), /* transition */
                ) {}
        }

        assertThat(transitionHandler).isNotNull()

        val leash = SurfaceControl.Builder().setName("taskLeash").build()
        val transitionInfo = TransitionInfo(TRANSIT_CONVERT_TO_BUBBLE, 0)
        val change =
            TransitionInfo.Change(taskInfo.token, leash).apply {
                setTaskInfo(taskInfo)
                mode = TRANSIT_CHANGE
            }
        transitionInfo.addChange(change)
        transitionInfo.addRoot(TransitionInfo.Root(0, mock(), 0, 0))
        getInstrumentation().runOnMainSync {
            transitionHandler!!.startAnimation(mock(), transitionInfo, mock(), mock(), mock())
        }

        assertThat(bubbleData.getBubbleInStackWithKey("key_app_bubble:123")).isNotNull()
    }

    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun convertExpandedBubbleToBar_startsConvertingToBar() {
        val bubble = createBubble("key")
        bubble.setShouldAutoExpand(true)
        bubble.setIsTaskValidToBubbleOnSmallScreen(true)
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true,
            )
        }
        assertThat(bubbleData.hasBubbles()).isTrue()
        assertThat(bubbleData.isExpanded).isTrue()
        assertThat(bubbleData.selectedBubble).isEqualTo(bubble)
        assertThat(bubble.taskView).isNotNull()

        bubblePositioner.update(deviceConfigUnfolded)
        bubblePositioner.isShowingInBubbleBar = true
        getInstrumentation().runOnMainSync {
            bubbleController.setLauncherHasBubbleBar(true)
            bubbleController.registerBubbleStateListener(FakeBubblesStateListener())
        }

        assertThat(bubble.isConvertingToBar).isTrue()
    }

    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun convertExpandedBubbleToBar_updatesTaskViewParent() {
        val bubble = createBubble("key")
        bubble.setShouldAutoExpand(true)
        bubble.setIsTaskValidToBubbleOnSmallScreen(true)
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true,
            )
        }
        assertThat(bubbleData.hasBubbles()).isTrue()
        assertThat(bubbleData.isExpanded).isTrue()
        assertThat(bubbleData.selectedBubble).isEqualTo(bubble)
        assertThat(bubble.taskView).isNotNull()

        assertThat(bubble.taskView.parent.parent).isEqualTo(bubble.expandedView)

        bubblePositioner.update(deviceConfigUnfolded)
        bubblePositioner.isShowingInBubbleBar = true
        getInstrumentation().runOnMainSync {
            bubbleController.setLauncherHasBubbleBar(true)
            bubbleController.registerBubbleStateListener(FakeBubblesStateListener())
        }

        assertThat(bubble.taskView.parent).isEqualTo(bubble.bubbleBarExpandedView)
    }

    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun convertExpandedBubbleToBar_screenOff_doesNotCollapse() {
        val bubble = createBubble("key")
        bubble.setShouldAutoExpand(true)
        bubble.setIsTaskValidToBubbleOnSmallScreen(true)
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true,
            )
        }
        assertThat(bubbleData.hasBubbles()).isTrue()
        assertThat(bubbleData.isExpanded).isTrue()
        assertThat(bubbleData.selectedBubble).isEqualTo(bubble)
        assertThat(bubble.taskView).isNotNull()

        assertThat(bubble.taskView.parent.parent).isEqualTo(bubble.expandedView)

        bubblePositioner.update(deviceConfigUnfolded)
        bubblePositioner.isShowingInBubbleBar = true
        getInstrumentation().runOnMainSync {
            bubbleController.setLauncherHasBubbleBar(true)
            bubbleController.registerBubbleStateListener(FakeBubblesStateListener())
        }

        bubbleController.broadcastReceiver.onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))
        assertThat(bubbleData.isExpanded).isTrue()
    }

    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun expandBubbleBar_thenFold_stayAwakeOnFold_shouldKeepBubbleExpanded() {
        isStayAwakeOnFold = true
        // switch to bubble bar
        bubblePositioner.update(deviceConfigUnfolded)
        bubblePositioner.isShowingInBubbleBar = true
        getInstrumentation().runOnMainSync {
            bubbleController.setLauncherHasBubbleBar(true)
            bubbleController.registerBubbleStateListener(FakeBubblesStateListener())
        }

        val bubble = createBubble("key")
        bubble.setShouldAutoExpand(true)
        bubble.setIsTaskValidToBubbleOnSmallScreen(true)
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true,
            )
        }
        assertThat(bubbleData.hasBubbles()).isTrue()
        assertThat(bubbleData.isExpanded).isTrue()
        assertThat(bubbleData.selectedBubble).isEqualTo(bubble)
        assertThat(bubble.taskView).isNotNull()

        assertThat(bubble.taskView.parent).isEqualTo(bubble.bubbleBarExpandedView)

        unfoldProgressProvider.listener.onFoldStateChanged(/* isFolded= */ true)
        verify(taskViewTransitions).enqueueExternal(eq(bubble.taskView.controller), any())

        // switch to floating
        bubblePositioner.update(deviceConfigFolded)
        bubblePositioner.isShowingInBubbleBar = false
        getInstrumentation().runOnMainSync {
            bubbleController.setLauncherHasBubbleBar(false)
            bubbleController.registerBubbleStateListener(null)
        }

        assertThat(bubbleData.isExpanded).isTrue()
        assertThat(bubbleController.stackView!!.isExpanded).isTrue()
        assertThat(bubble.taskView.parent.parent).isEqualTo(bubble.expandedView)
        assertThat(bubble.taskView.alpha).isEqualTo(1)
    }

    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun expandBubbleBar_thenFold_notStayAwakeOnFold_shouldCollapse() {
        isStayAwakeOnFold = false
        // switch to bubble bar
        bubblePositioner.update(deviceConfigUnfolded)
        bubblePositioner.isShowingInBubbleBar = true
        getInstrumentation().runOnMainSync {
            bubbleController.setLauncherHasBubbleBar(true)
            bubbleController.registerBubbleStateListener(FakeBubblesStateListener())
        }

        val bubble = createBubble("key")
        bubble.setShouldAutoExpand(true)
        bubble.setIsTaskValidToBubbleOnSmallScreen(true)
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true,
            )
        }
        assertThat(bubbleData.hasBubbles()).isTrue()
        assertThat(bubbleData.isExpanded).isTrue()
        assertThat(bubbleData.selectedBubble).isEqualTo(bubble)
        assertThat(bubble.taskView).isNotNull()

        assertThat(bubble.taskView.parent).isEqualTo(bubble.bubbleBarExpandedView)

        unfoldProgressProvider.listener.onFoldStateChanged(/* isFolded= */ true)

        // switch to floating
        bubblePositioner.update(deviceConfigFolded)
        bubblePositioner.isShowingInBubbleBar = false
        getInstrumentation().runOnMainSync {
            bubbleController.setLauncherHasBubbleBar(false)
            bubbleController.registerBubbleStateListener(null)
        }

        assertThat(bubbleData.isExpanded).isFalse()
        assertThat(bubbleController.stackView!!.isExpanded).isFalse()
        assertThat(bubble.taskView.parent.parent).isEqualTo(bubble.expandedView)
        assertThat(bubble.taskView.alpha).isEqualTo(0)
    }

    @DisableFlags(FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun onAllBubblesAnimatedOut_hasBubbles() {
        val bubble = createBubble("key")
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true,
            )
        }
        assertThat(bubbleData.hasBubbles()).isTrue()

        getInstrumentation().runOnMainSync { bubbleController.onAllBubblesAnimatedOut() }
        assertThat(bubbleController.stackView!!.visibility).isEqualTo(View.VISIBLE)
    }

    @DisableFlags(FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun onAllBubblesAnimatedOut_noBubbles() {
        val bubble = createBubble("key")
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true,
            )
        }
        getInstrumentation().runOnMainSync {
            bubbleController.dismissBubble("key", DISMISS_USER_GESTURE)
        }
        assertThat(bubbleData.hasBubbles()).isFalse()

        getInstrumentation().runOnMainSync { bubbleController.onAllBubblesAnimatedOut() }
        assertThat(bubbleController.stackView!!.visibility).isEqualTo(View.INVISIBLE)
    }

    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun onAllBubblesAnimatedOutBubbleBar_hasBubbles() {
        bubblePositioner.update(deviceConfigUnfolded)
        bubblePositioner.isShowingInBubbleBar = true
        getInstrumentation().runOnMainSync {
            bubbleController.setLauncherHasBubbleBar(true)
            bubbleController.registerBubbleStateListener(FakeBubblesStateListener())
        }

        val bubble = createBubble("key")
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true,
            )
        }
        assertThat(bubbleData.hasBubbles()).isTrue()

        getInstrumentation().runOnMainSync { bubbleController.onAllBubblesAnimatedOut() }
        assertThat(bubbleController.layerView!!.visibility).isEqualTo(View.VISIBLE)
    }

    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun onAllBubblesAnimatedOutBubbleBar_noBubbles() {
        bubblePositioner.update(deviceConfigUnfolded)
        bubblePositioner.isShowingInBubbleBar = true
        getInstrumentation().runOnMainSync {
            bubbleController.setLauncherHasBubbleBar(true)
            bubbleController.registerBubbleStateListener(FakeBubblesStateListener())
        }

        val bubble = createBubble("key")
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true,
            )
        }
        getInstrumentation().runOnMainSync {
            bubbleController.dismissBubble("key", DISMISS_USER_GESTURE)
        }
        assertThat(bubbleData.hasBubbles()).isFalse()

        getInstrumentation().runOnMainSync { bubbleController.onAllBubblesAnimatedOut() }
        assertThat(bubbleController.layerView!!.visibility).isEqualTo(View.INVISIBLE)
    }

    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun removeLastBubbleInBubbleBar_hidesLayerViewAndRemovesWindow() {
        // Verifies that when the last bubble is removed in bubble bar mode, the layer view is
        // hidden and the window is removed via the animation callback. This exercises the logic
        // containing the change under test.

        // GIVEN bubble bar mode is active with one bubble
        bubblePositioner.update(deviceConfigUnfolded)
        bubblePositioner.isShowingInBubbleBar = true
        getInstrumentation().runOnMainSync {
            bubbleController.setLauncherHasBubbleBar(true)
            bubbleController.registerBubbleStateListener(FakeBubblesStateListener())
        }

        val bubble = createBubble("key")
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true,
            )
        }
        val layerView = bubbleController.layerView
        assertThat(layerView).isNotNull()
        verify(windowManager).addView(eq(layerView), any())

        // WHEN the last bubble is dismissed
        getInstrumentation().runOnMainSync {
            bubbleController.dismissBubble("key", DISMISS_USER_GESTURE)
        }
        assertThat(bubbleData.hasBubbles()).isFalse()

        // THEN the layer view is made invisible and removed from the window manager
        // The animation end callback in BubbleBarLayerView#removeBubble should run, which
        // executes the lambda in BubbleController that hides the view and posts the removal.
        mainExecutor.flushAll()
        bgExecutor.flushAll()

        assertThat(layerView!!.visibility).isEqualTo(View.INVISIBLE)
        verify(windowManager).removeView(layerView)
    }

    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun removeBubbleInBubbleBar_withRemainingBubbles_doesNotHideLayerView() {
        // GIVEN bubble bar mode is active with two bubbles
        bubblePositioner.update(deviceConfigUnfolded)
        bubblePositioner.isShowingInBubbleBar = true
        getInstrumentation().runOnMainSync {
            bubbleController.setLauncherHasBubbleBar(true)
            bubbleController.registerBubbleStateListener(FakeBubblesStateListener())
        }

        val bubble1 = createBubble("key1")
        val bubble2 = createBubble("key2")
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(bubble1, true, true)
            bubbleController.inflateAndAdd(bubble2, true, true)
        }
        val layerView = bubbleController.layerView
        assertThat(layerView).isNotNull()
        verify(windowManager).addView(eq(layerView), any())

        // WHEN one bubble is dismissed
        getInstrumentation().runOnMainSync {
            bubbleController.dismissBubble("key1", DISMISS_USER_GESTURE)
        }
        assertThat(bubbleData.hasBubbles()).isTrue()

        // THEN the layer view is not hidden and not removed from the window manager, because
        // another bubble is still present. This exercises the animation callback where the new
        // logging was added.
        mainExecutor.flushAll()
        bgExecutor.flushAll()

        assertThat(layerView!!.visibility).isEqualTo(View.VISIBLE)
        verify(windowManager, never()).removeView(any())
    }

    @EnableFlags(Flags.FLAG_FIX_VERIFY_BUBBLE_TASK_ID_ON_REMOVAL)
    @Test
    fun removeBubble_withTaskId_taskIdsMatch_removeBubble() {
        val bubble = createBubble("key", taskId = 123)
        // Initialize the fake task view so we can set the task id
        val bubbleTaskView =
            bubble.getOrCreateBubbleTaskView(FakeBubbleTaskViewFactory(context, mainExecutor))
        bubbleTaskView.listener.onTaskCreated(/* taskId= */ 123, COMPONENT)
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true,
            )
        }
        bubble.taskView.taskInfo?.taskId = 123
        assertThat(bubbleData.hasBubbles()).isTrue()
        assertThat(bubble.taskId).isEqualTo(123)

        getInstrumentation().runOnMainSync {
            bubbleController.removeBubble("key", 123, DISMISS_TASK_FINISHED)
        }
        assertThat(bubbleData.hasBubbles()).isFalse()
    }

    @EnableFlags(Flags.FLAG_FIX_VERIFY_BUBBLE_TASK_ID_ON_REMOVAL)
    @Test
    fun removeBubble_withTaskId_bubbleHasNoTaskId_removeBubble() {
        val bubble = createBubble("key", taskId = -1)
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true,
            )
        }
        assertThat(bubbleData.hasBubbles()).isTrue()
        assertThat(bubble.taskId).isEqualTo(-1)

        getInstrumentation().runOnMainSync {
            bubbleController.removeBubble("key", 123, DISMISS_TASK_FINISHED)
        }
        assertThat(bubbleData.hasBubbles()).isFalse()
    }

    @EnableFlags(Flags.FLAG_FIX_VERIFY_BUBBLE_TASK_ID_ON_REMOVAL)
    @Test
    fun removeBubble_withTaskId_differentTaskIds_doesNotRemoveBubble() {
        val bubble = createBubble("key", taskId = 123)
        // Initialize the fake task view so we can set the task id
        val bubbleTaskView =
            bubble.getOrCreateBubbleTaskView(FakeBubbleTaskViewFactory(context, mainExecutor))
        bubbleTaskView.listener.onTaskCreated(/* taskId= */ 123, COMPONENT)
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true,
            )
        }
        assertThat(bubbleData.hasBubbles()).isTrue()
        assertThat(bubble.taskId).isEqualTo(123)

        getInstrumentation().runOnMainSync {
            bubbleController.removeBubble("key", 456, DISMISS_TASK_FINISHED)
        }
        assertThat(bubbleData.hasBubbles()).isTrue()
    }

    @Test
    fun testOnThemeChanged_skipInflationForOverflowBubbles() {
        val taskInfo1 =
            ActivityManager.RunningTaskInfo().apply {
                taskId = 123
                baseActivity = COMPONENT
            }
        val bubble = createAppBubble(taskInfo1)
        val taskInfo2 =
            ActivityManager.RunningTaskInfo().apply {
                taskId = 124
                baseActivity = COMPONENT
            }
        val overflowBubble = createAppBubble(taskInfo2)
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true,
            )
            bubbleController.inflateAndAdd(
                overflowBubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true,
            )
            bubbleController.dismissBubble(overflowBubble, DISMISS_USER_GESTURE)
        }

        assertThat(bubbleData.hasBubbles()).isTrue()
        assertThat(bubbleData.hasOverflowBubbles()).isTrue()
        assertWithMessage("Overflow bubble should not be inflated since it's dismissed")
            .that(overflowBubble.isInflated)
            .isFalse()

        getInstrumentation().runOnMainSync { bubbleController.onThemeChanged() }

        assertWithMessage("Overflow bubble should not be inflated even if #onThemeChanged")
            .that(overflowBubble.isInflated)
            .isFalse()
    }

    @Test
    fun bubbleCreatedFromNotification_shouldLogEntryPoint() {
        bubbleController.asBubbles().onEntryAdded(createBubbleEntry(pkgName = "package.name"))
        mainExecutor.flushAll()

        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        val log = uiEventLoggerFake.logs.first()
        assertThat(log.packageName).isEqualTo("package.name")
        assertThat(log.eventId).isEqualTo(BUBBLE_CREATED_FROM_NOTIF.id)
    }

    @Test
    fun bubbleCreatedFromNotificationButton_shouldLogEntryPoint() {
        bubbleController
            .asBubbles()
            .onEntryUpdated(
                createBubbleEntry(pkgName = "package.name"),
                /* shouldBubbleUp= */ true,
                /* fromSystem= */ true,
            )
        mainExecutor.flushAll()

        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        val log = uiEventLoggerFake.logs.first()
        assertThat(log.packageName).isEqualTo("package.name")
        assertThat(log.eventId).isEqualTo(BUBBLE_CREATED_FROM_NOTIF_BUBBLE_BUTTON.id)
    }

    @Test
    fun bubbleNotificationUpdated_shouldNotLogEntryPoint() {
        val bubble = createBubble("bubble-key")
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true,
            )
        }
        bubbleController
            .asBubbles()
            .onEntryUpdated(
                createBubbleEntry(bubbleKey = "bubble-key", pkgName = "package.name"),
                /* shouldBubbleUp= */ true,
                /* fromSystem= */ true,
            )
        mainExecutor.flushAll()

        assertThat(uiEventLoggerFake.logs).isEmpty()
    }

    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @Test
    fun expandStackAndSelectBubble_shouldLogEntryPoint() {
        val intent = Intent().apply { setPackage("package.name") }
        getInstrumentation().runOnMainSync {
            bubbleController.expandStackAndSelectBubble(
                intent,
                UserHandle.of(0),
                EntryPoint.ALL_APPS_ICON_MENU,
                /* bubbleBarLocation= */ null,
            )
        }

        assertThat(uiEventLoggerFake.logs).isNotEmpty()
        val log = uiEventLoggerFake.logs.first()
        assertThat(log.packageName).isEqualTo("package.name")
        assertThat(log.eventId).isEqualTo(BUBBLE_CREATED_FROM_ALL_APPS_ICON_MENU.id)
    }

    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun sendInitialBubbleBarState_registerListenerWaitsForLauncherHasBubbleBar() {
        val bubbleStateListener = FakeBubblesStateListener()
        bubblePositioner.update(deviceConfigUnfolded)
        bubblePositioner.isShowingInBubbleBar = true

        getInstrumentation().runOnMainSync { bubbleController.setLauncherHasBubbleBar(true) }
        assertThat(bubbleStateListener.lastUpdate).isNull()

        getInstrumentation().runOnMainSync {
            bubbleController.registerBubbleStateListener(bubbleStateListener)
        }
        assertThat(bubbleStateListener.lastUpdate).isNotNull()
    }

    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun sendInitialBubbleBarState_launcherHasBubbleBarWaitsForListener() {
        val bubbleStateListener = FakeBubblesStateListener()
        bubblePositioner.update(deviceConfigUnfolded)
        bubblePositioner.isShowingInBubbleBar = true

        getInstrumentation().runOnMainSync {
            bubbleController.registerBubbleStateListener(bubbleStateListener)
        }
        assertThat(bubbleStateListener.lastUpdate).isNull()

        getInstrumentation().runOnMainSync { bubbleController.setLauncherHasBubbleBar(true) }
        assertThat(bubbleStateListener.lastUpdate).isNotNull()
    }

    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun sendInitialBubbleBarState_containsBubbleRootTaskId() {
        assumeTrue(BubbleFlagHelper.enableCreateAnyBubble())

        bubbleHelper.stub { on { getAppBubbleRootTaskId() } doReturn 123 }

        val bubbleStateListener = FakeBubblesStateListener()

        bubblePositioner.update(deviceConfigUnfolded)
        bubblePositioner.isShowingInBubbleBar = true
        getInstrumentation().runOnMainSync {
            bubbleController.setLauncherHasBubbleBar(true)
            bubbleController.registerBubbleStateListener(bubbleStateListener)
        }

        assertThat(bubbleStateListener.lastUpdate!!.bubbleRootTaskId).isEqualTo(123)
    }

    @EnableFlags(FLAG_UPDATE_BUBBLE_BOUNDS_DURING_ROTATION)
    @Test
    fun onDisplayChanging_rotating_stackExpanded_updatesTaskBounds() {
        val displayChangingListenerCaptor = argumentCaptor<OnDisplayChangingListener>()
        verify(displayController)
            .addDisplayChangingController(displayChangingListenerCaptor.capture())
        val displayChangingListener = displayChangingListenerCaptor.lastValue

        displayLayout.rotateTo(context.resources, ROTATION_90)
        displayController.stub { on { getDisplayContext(0) } doReturn context }

        val bubble = createBubble("key")
        // this is kinda hacky but ensures that the task view is created using the fake factory
        // which ensures we have a token
        bubble.getOrCreateBubbleTaskView(FakeBubbleTaskViewFactory(context, mainExecutor))
        bubble.setShouldAutoExpand(true)
        bubble.setIsTaskValidToBubbleOnSmallScreen(true)
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true,
            )
        }
        assertThat(bubbleData.hasBubbles()).isTrue()
        assertThat(bubbleData.isExpanded).isTrue()
        assertThat(bubbleData.selectedBubble).isEqualTo(bubble)
        assertThat(bubble.taskView.taskInfo!!.token).isNotNull()

        val wct = mock<WindowContainerTransaction>()
        displayChangingListener.onDisplayChange(0, ROTATION_0, ROTATION_90, null, wct)

        verify(wct).setBounds(eq(bubble.taskView.taskInfo!!.token), any())
    }

    @EnableFlags(FLAG_UPDATE_BUBBLE_BOUNDS_DURING_ROTATION)
    @Test
    fun onDisplayChanging_resizing_stackExpanded_doesNotUpdateTaskBounds() {
        val displayChangingListenerCaptor = argumentCaptor<OnDisplayChangingListener>()
        verify(displayController)
            .addDisplayChangingController(displayChangingListenerCaptor.capture())
        val displayChangingListener = displayChangingListenerCaptor.lastValue

        displayLayout.resizeTo(context.resources, Size(500, 1000))
        displayController.stub { on { getDisplayContext(0) } doReturn context }

        val bubble = createBubble("key")
        // this is kinda hacky but ensures that the task view is created using the fake factory
        // which ensures we have a token
        bubble.getOrCreateBubbleTaskView(FakeBubbleTaskViewFactory(context, mainExecutor))
        bubble.setShouldAutoExpand(true)
        bubble.setIsTaskValidToBubbleOnSmallScreen(true)
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true,
            )
        }
        assertThat(bubbleData.hasBubbles()).isTrue()
        assertThat(bubbleData.isExpanded).isTrue()
        assertThat(bubbleData.selectedBubble).isEqualTo(bubble)
        assertThat(bubble.taskView.taskInfo!!.token).isNotNull()

        val wct = mock<WindowContainerTransaction>()
        displayChangingListener.onDisplayChange(0, ROTATION_0, ROTATION_0, null, wct)

        verify(wct, never()).setBounds(any(), any())
    }

    private fun createBubbleEntry(bubbleKey: String = "key", pkgName: String): BubbleEntry {
        val notif =
            Notification.Builder(context)
                .setBubbleMetadata(Notification.BubbleMetadata.Builder("shortcutId").build())
                .setFlag(Notification.FLAG_BUBBLE, true)
                .build()
        val sbn =
            mock<StatusBarNotification>().stub {
                on { key } doReturn bubbleKey
                on { packageName } doReturn pkgName
                on { notification } doReturn notif
            }
        return BubbleEntry(
            sbn,
            mock<NotificationListenerService.Ranking>(),
            /* isDismissable= */ false,
            /* shouldSuppressNotificationDot= */ true,
            /* shouldSuppressNotificationList= */ true,
            /* shouldSuppressPeek= */ true,
        )
    }

    private fun createBubble(key: String, taskId: Int = 0): Bubble {
        val icon = Icon.createWithResource(context.resources, R.drawable.bubble_ic_overflow_button)
        val shortcutInfo = ShortcutInfo.Builder(context, "fakeId").setIcon(icon).build()
        val bubble =
            Bubble(
                key,
                shortcutInfo,
                /* desiredHeight= */ 0,
                Resources.ID_NULL,
                "title",
                taskId,
                "locus",
                /* isDismissable= */ true,
            ) {}
        return bubble
    }

    private fun createAppBubble(taskInfo: TaskInfo): Bubble {
        return Bubble.createTaskBubble(taskInfo, UserHandle.of(0), null)
    }

    private fun createBubbleController(
        bubbleData: BubbleData,
        windowManager: WindowManager,
        shellTaskOrganizer: ShellTaskOrganizer,
        bubbleLogger: BubbleLogger,
        bubblePositioner: BubblePositioner,
        mainExecutor: TestShellExecutor,
        bgExecutor: TestShellExecutor,
    ): BubbleController {
        val shellInit = ShellInit(mainExecutor)
        val shellCommandHandler = ShellCommandHandler()
        val shellController =
            ShellController(
                context,
                shellInit,
                shellCommandHandler,
                displayInsetsController,
                userManager,
                mainExecutor,
            )
        val surfaceSynchronizer = { obj: Runnable -> obj.run() }

        val bubbleDataRepository =
            BubbleDataRepository(
                mock<LauncherApps>(),
                mainExecutor,
                bgExecutor,
                BubblePersistentRepository(context),
            )

        val resizeChecker = ResizabilityChecker { _, _, _ -> true }

        val bubbleController =
            BubbleController(
                context,
                shellInit,
                shellCommandHandler,
                shellController,
                bubbleData,
                surfaceSynchronizer,
                FloatingContentCoordinator(),
                bubbleDataRepository,
                bubbleTransitions,
                mock<IStatusBarService>(),
                windowManager,
                displayInsetsController,
                displayImeController,
                userManager,
                mock<LauncherApps>(),
                bubbleLogger,
                taskStackListener,
                shellTaskOrganizer,
                bubblePositioner,
                displayController,
                Optional.empty(),
                mock<DragAndDropController>(),
                mainExecutor,
                mock<Handler>(),
                bgExecutor,
                taskViewTransitions,
                transitions,
                SyncTransactionQueue(TransactionPool(), mainExecutor),
                mock<IWindowManager>(),
                resizeChecker,
                HomeIntentProvider(context),
                { Optional.of(splitScreenController) },
                Optional.of(unfoldProgressProvider),
                { isStayAwakeOnFold },
                sessionTracker,
                bubbleViewInfoTaskFactory,
                bubbleHelper,
                FakeBubbleFeatureConfig(),
            )
        bubbleController.setInflateSynchronously(true)
        bubbleController.onInit()

        bubbleController.asBubbles().setSysuiProxy(mock<SysuiProxy>())
        // Flush so that proxy gets set
        mainExecutor.flushAll()

        return bubbleController
    }

    private class FakeBubbleExpandListener : BubbleExpandListener {
        val bubblesExpandedState = mutableMapOf<String, Boolean>()

        override fun onBubbleExpandChanged(isExpanding: Boolean, key: String) {
            bubblesExpandedState[key] = isExpanding
        }
    }

    private class FakeShellUnfoldProgressProvider : ShellUnfoldProgressProvider {

        lateinit var listener: ShellUnfoldProgressProvider.UnfoldListener

        override fun addListener(
            executor: Executor,
            listener: ShellUnfoldProgressProvider.UnfoldListener,
        ) {
            this.listener = listener
        }
    }

    companion object {
        private val COMPONENT = ComponentName("com.example.app", "com.example.app.MainActivity")

        private fun createFakeInsetsState(imeVisible: Boolean): InsetsState {
            val insetsState = InsetsState()
            if (imeVisible) {
                insetsState
                    .getOrCreateSource(InsetsSource.ID_IME, WindowInsets.Type.ime())
                    .setFrame(Rect(0, 100, 100, 200))
                    .setVisible(true)
            }
            return insetsState
        }
    }
}
