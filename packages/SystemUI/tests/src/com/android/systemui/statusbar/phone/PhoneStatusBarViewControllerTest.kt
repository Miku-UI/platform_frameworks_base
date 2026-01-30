/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.statusbar.phone

import android.app.StatusBarManager.WINDOW_STATE_HIDDEN
import android.app.StatusBarManager.WINDOW_STATE_HIDING
import android.app.StatusBarManager.WINDOW_STATE_SHOWING
import android.app.StatusBarManager.WINDOW_STATUS_BAR
import android.content.Context
import android.graphics.Insets
import android.graphics.Region
import android.hardware.display.DisplayManagerGlobal
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.testing.TestableLooper
import android.view.Display
import android.view.DisplayAdjustments.DEFAULT_DISPLAY_ADJUSTMENTS
import android.view.DisplayInfo
import android.view.InputDevice
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewRootImpl
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.SysuiTestableContext
import com.android.systemui.battery.BatteryMeterView
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.plugins.fakeDarkIconDispatcher
import com.android.systemui.res.R
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.ui.view.WindowRootView
import com.android.systemui.shade.ShadeControllerImpl
import com.android.systemui.shade.ShadeLogger
import com.android.systemui.shade.ShadeViewController
import com.android.systemui.shade.StatusBarLongPressGestureDetector
import com.android.systemui.shade.data.repository.ShadeDisplaysRepository
import com.android.systemui.shade.data.repository.defaultShadeDisplayPolicy
import com.android.systemui.shade.display.StatusBarTouchShadeDisplayPolicy
import com.android.systemui.shade.domain.interactor.PanelExpansionInteractor
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.shade.shared.flag.ShadeWindowGoesAround
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.CommandQueue
import com.android.systemui.statusbar.core.StatusBarConnectedDisplays
import com.android.systemui.statusbar.data.repository.fakeStatusBarContentInsetsProviderStore
import com.android.systemui.statusbar.policy.Clock
import com.android.systemui.statusbar.policy.mockStatusBarConfigurationController
import com.android.systemui.statusbar.window.StatusBarWindowControllerStore
import com.android.systemui.statusbar.window.StatusBarWindowStateController
import com.android.systemui.testKosmos
import com.android.systemui.unfold.util.ScopedUnfoldTransitionProgressProvider
import com.android.systemui.user.ui.viewmodel.StatusBarUserChipViewModel
import com.android.systemui.util.view.ViewUtil
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import java.util.function.BooleanSupplier
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
class PhoneStatusBarViewControllerTest(flags: FlagsParameterization) : SysuiTestCase() {
    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val mStatusBarConfigurationController = kosmos.mockStatusBarConfigurationController
    private val statusBarContentInsetsProviderStore = kosmos.fakeStatusBarContentInsetsProviderStore
    private val statusBarContentInsetsProvider = statusBarContentInsetsProviderStore.defaultDisplay
    private val statusBarContentInsetsProviderForSecondaryDisplay =
        statusBarContentInsetsProviderStore.forDisplay(SECONDARY_DISPLAY_ID)
    private val windowRootView = mock<WindowRootView>()

    private val fakeDarkIconDispatcher = kosmos.fakeDarkIconDispatcher
    @Mock private lateinit var shadeViewController: ShadeViewController
    @Mock private lateinit var panelExpansionInteractor: PanelExpansionInteractor
    @Mock private lateinit var progressProvider: ScopedUnfoldTransitionProgressProvider
    @Mock private lateinit var mStatusOverlayHoverListenerFactory: StatusOverlayHoverListenerFactory
    @Mock private lateinit var mStatusOverlayHoverListener: StatusOverlayHoverListener
    @Mock private lateinit var userChipViewModel: StatusBarUserChipViewModel
    @Mock private lateinit var centralSurfacesImpl: CentralSurfacesImpl
    @Mock private lateinit var commandQueue: CommandQueue
    @Mock private lateinit var shadeControllerImpl: ShadeControllerImpl
    @Mock private lateinit var shadeLogger: ShadeLogger
    @Mock private lateinit var viewUtil: ViewUtil
    @Mock private lateinit var mStatusBarLongPressGestureDetector: StatusBarLongPressGestureDetector
    @Mock private lateinit var statusBarTouchShadeDisplayPolicy: StatusBarTouchShadeDisplayPolicy
    @Mock private lateinit var shadeDisplayRepository: ShadeDisplaysRepository
    @Mock private lateinit var statusBarWindowControllerStore: StatusBarWindowControllerStore
    private lateinit var statusBarWindowStateController: StatusBarWindowStateController
    private lateinit var view: PhoneStatusBarView
    private lateinit var controller: PhoneStatusBarViewController

    private lateinit var viewForSecondaryDisplay: PhoneStatusBarView

    private val clockView: Clock
        get() = view.requireViewById(R.id.clock)

    private val batteryView: BatteryMeterView
        get() = view.requireViewById(R.id.battery)

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        statusBarWindowStateController = StatusBarWindowStateController(DISPLAY_ID, commandQueue)

        whenever(statusBarContentInsetsProvider.getStatusBarContentInsetsForCurrentRotation())
            .thenReturn(Insets.NONE)
        whenever(mStatusOverlayHoverListenerFactory.createDarkAwareListener(any()))
            .thenReturn(mStatusOverlayHoverListener)
        whenever(
                mStatusOverlayHoverListenerFactory.createDarkAwareListener(
                    any(),
                    eq(0),
                    eq(0),
                    eq(6),
                    eq(6),
                )
            )
            .thenReturn(mStatusOverlayHoverListener)

        view = createView(mContext)
        controller = createAndInitController(view)

        whenever(
                statusBarContentInsetsProviderForSecondaryDisplay
                    .getStatusBarContentInsetsForCurrentRotation()
            )
            .thenReturn(Insets.NONE)

        val contextForSecondaryDisplay =
            SysuiTestableContext(
                mContext.createDisplayContext(
                    Display(
                        DisplayManagerGlobal.getInstance(),
                        SECONDARY_DISPLAY_ID,
                        DisplayInfo(),
                        DEFAULT_DISPLAY_ADJUSTMENTS,
                    )
                )
            )

        viewForSecondaryDisplay = createView(contextForSecondaryDisplay)
        createAndInitController(viewForSecondaryDisplay)
    }

    @Test
    @DisableFlags(Flags.FLAG_SHADE_WINDOW_GOES_AROUND)
    fun onViewAttachedAndDrawn_addStatusBarConfigurationControllerCallback() {
        attachToWindow(view)

        controller = createAndInitController(view)

        verify(mStatusBarConfigurationController).addCallback(any())
    }

    @Test
    @EnableFlags(Flags.FLAG_SHADE_WINDOW_GOES_AROUND)
    fun onViewAttachedAndDrawn_doesNotAddStatusBarConfigurationControllerCallback() {
        attachToWindow(view)

        controller = createAndInitController(view)

        verify(mStatusBarConfigurationController, never()).addCallback(any())
    }

    @Test
    fun onViewAttachedAndDrawn_darkReceiversRegistered() {
        attachToWindow(view)

        controller = createAndInitController(view)

        assertThat(fakeDarkIconDispatcher.receivers.size).isEqualTo(2)
        assertThat(fakeDarkIconDispatcher.receivers).contains(clockView)
        assertThat(fakeDarkIconDispatcher.receivers).contains(batteryView)
    }

    @Test
    @DisableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun onViewAttachedAndDrawn_connectedDisplaysFlagOff_doesNotSetInteractionGate() {
        attachToWindow(view)

        controller = createAndInitController(view)

        verify(view, never()).setIsStatusBarInteractiveSupplier(any())
    }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun onViewAttachedAndDrawn_connectedDisplaysFlagOn_defaultDisplay_doesNotSetInteractionGate() {
        attachToWindow(view)

        controller = createAndInitController(view)

        verify(view, never()).setIsStatusBarInteractiveSupplier(any())
    }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun onViewAttachedAndDrawn_connectedDisplaysFlagOn_secondaryDisplay_setsInteractionGate() {
        attachToWindow(viewForSecondaryDisplay)

        controller = createAndInitController(viewForSecondaryDisplay)

        verify(viewForSecondaryDisplay).setIsStatusBarInteractiveSupplier(any())
    }

    @Test
    fun onViewAttached_containersInteractive() {
        attachToWindow(view)
        val endSideContainer = spy(view.requireViewById<View>(R.id.system_icons))
        whenever(view.requireViewById<View>(R.id.system_icons)).thenReturn(endSideContainer)
        val startSideContainer = spy(view.requireViewById<View>(R.id.status_bar_start_side_content))
        whenever(view.requireViewById<View>(R.id.status_bar_start_side_content))
            .thenReturn(startSideContainer)

        controller = createAndInitController(view)

        verify(endSideContainer).setOnHoverListener(any())
        verify(endSideContainer).setOnTouchListener(any())
        verify(startSideContainer).setOnHoverListener(any())
        verify(startSideContainer).setOnTouchListener(any())
    }

    @Test
    fun onViewDetached_darkReceiversUnregistered() {
        attachToWindow(view)

        controller = createAndInitController(view)

        assertThat(fakeDarkIconDispatcher.receivers).isNotEmpty()

        controller.onViewDetached()

        assertThat(fakeDarkIconDispatcher.receivers).isEmpty()
    }

    @Test
    fun handleTouchEventFromStatusBar_panelsNotEnabled_returnsFalseAndNoViewEvent() {
        whenever(centralSurfacesImpl.commandQueuePanelsEnabled).thenReturn(false)
        val returnVal =
            view.onTouchEvent(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 2f, 0))
        assertThat(returnVal).isFalse()
        verify(shadeViewController, never()).handleExternalTouch(any())
    }

    @Test
    fun handleTouchEventFromStatusBar_viewNotEnabled_returnsTrueAndNoViewEvent() {
        whenever(centralSurfacesImpl.commandQueuePanelsEnabled).thenReturn(true)
        whenever(shadeViewController.isViewEnabled).thenReturn(false)
        val returnVal =
            view.onTouchEvent(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 2f, 0))
        assertThat(returnVal).isTrue()
        verify(shadeViewController, never()).handleExternalTouch(any())
    }

    @Test
    fun handleTouchEventFromStatusBar_viewNotEnabledButIsMoveEvent_viewReceivesEvent() {
        whenever(centralSurfacesImpl.commandQueuePanelsEnabled).thenReturn(true)
        whenever(shadeViewController.isViewEnabled).thenReturn(false)
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_MOVE, 0f, 2f, 0)

        view.onTouchEvent(event)

        if (SceneContainerFlag.isEnabled) {
            verify(windowRootView).dispatchTouchEvent(event)
        } else {
            verify(shadeViewController).handleExternalTouch(event)
        }
    }

    @Test
    fun handleTouchEventFromStatusBar_panelAndViewEnabled_viewReceivesEvent() {
        whenever(centralSurfacesImpl.commandQueuePanelsEnabled).thenReturn(true)
        whenever(shadeViewController.isViewEnabled).thenReturn(true)
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 2f, 0)

        view.onTouchEvent(event)

        if (SceneContainerFlag.isEnabled) {
            verify(windowRootView).dispatchTouchEvent(event)
        } else {
            verify(shadeViewController).handleExternalTouch(event)
        }
    }

    @Test
    fun handleTouchEventFromStatusBar_topEdgeTouch_viewNeverReceivesEvent() {
        whenever(centralSurfacesImpl.commandQueuePanelsEnabled).thenReturn(true)
        whenever(panelExpansionInteractor.isFullyCollapsed).thenReturn(true)
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 0f, 0)

        view.onTouchEvent(event)

        verify(shadeViewController, never()).handleExternalTouch(any())
    }

    @Test
    fun handleTouchEventFromStatusBar_touchOnPrimaryDisplay_shadeReceivesEvent() {
        whenever(centralSurfacesImpl.commandQueuePanelsEnabled).thenReturn(true)
        whenever(shadeViewController.isViewEnabled).thenReturn(true)
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 2f, 0)

        view.dispatchTouchEvent(event)

        if (SceneContainerFlag.isEnabled) {
            verify(windowRootView).dispatchTouchEvent(event)
        } else {
            verify(shadeViewController).handleExternalTouch(event)
        }
    }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME, ShadeWindowGoesAround.FLAG_NAME)
    fun handleTouchEventFromStatusBar_touchOnSecondaryDisplay_interactionsAllowed_shadeReceivesEvent() {
        attachToWindow(viewForSecondaryDisplay)
        controller = createAndInitController(viewForSecondaryDisplay)
        whenever(centralSurfacesImpl.commandQueuePanelsEnabled).thenReturn(true)
        whenever(shadeViewController.isViewEnabled).thenReturn(true)
        // Ensure test is set up with an interaction gate that allows interactions.
        whenever(shadeDisplayRepository.currentPolicy).thenReturn(statusBarTouchShadeDisplayPolicy)
        val argumentCaptor = argumentCaptor<BooleanSupplier>()
        verify(viewForSecondaryDisplay).setIsStatusBarInteractiveSupplier(argumentCaptor.capture())
        assertThat(argumentCaptor.lastValue.asBoolean).isTrue()
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 2f, 0)

        viewForSecondaryDisplay.dispatchTouchEvent(event)

        if (SceneContainerFlag.isEnabled) {
            verify(windowRootView).dispatchTouchEvent(event)
        } else {
            verify(shadeViewController).handleExternalTouch(event)
        }
    }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME, ShadeWindowGoesAround.FLAG_NAME)
    fun handleTouchEventFromStatusBar_touchOnSecondaryDisplay_interactionsNotAllowed_shadeDoesNotReceiveEvent() {
        attachToWindow(viewForSecondaryDisplay)
        controller = createAndInitController(viewForSecondaryDisplay)
        whenever(centralSurfacesImpl.commandQueuePanelsEnabled).thenReturn(true)
        whenever(shadeViewController.isViewEnabled).thenReturn(true)
        // Ensure test is set up with an interaction gate that does not allow interactions.
        whenever(shadeDisplayRepository.currentPolicy).thenReturn(kosmos.defaultShadeDisplayPolicy)
        val argumentCaptor = argumentCaptor<BooleanSupplier>()
        verify(viewForSecondaryDisplay).setIsStatusBarInteractiveSupplier(argumentCaptor.capture())
        assertThat(argumentCaptor.lastValue.asBoolean).isFalse()
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 2f, 0)

        viewForSecondaryDisplay.dispatchTouchEvent(event)

        verify(shadeViewController, never()).handleExternalTouch(any())
    }

    @Test
    @DisableSceneContainer
    fun handleInterceptTouchEventFromStatusBar_shadeReturnsFalse_viewReturnsFalse() {
        whenever(shadeViewController.handleExternalInterceptTouch(any())).thenReturn(false)
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 2f, 0)

        val returnVal = view.onInterceptTouchEvent(event)

        assertThat(returnVal).isFalse()
    }

    @Test
    @DisableSceneContainer
    fun handleInterceptTouchEventFromStatusBar_shadeReturnsTrue_viewReturnsTrue() {
        whenever(shadeViewController.handleExternalInterceptTouch(any())).thenReturn(true)
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 2f, 0)

        val returnVal = view.onInterceptTouchEvent(event)

        assertThat(returnVal).isTrue()
    }

    @Test
    @EnableSceneContainer
    fun handleInterceptTouchEventFromStatusBar_swipeDown_intercepts() {
        val downEvent = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 10f, 0)
        view.onInterceptTouchEvent(downEvent)

        val moveEvent = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_MOVE, 0f, 100f, 0)
        val intercepted = view.onInterceptTouchEvent(moveEvent)

        assertThat(intercepted).isTrue()
        verify(windowRootView).dispatchTouchEvent(moveEvent)
    }

    @Test
    @EnableSceneContainer
    fun handleInterceptTouchEventFromStatusBar_swipeDown_dispatchesCachedEvents() {
        val downEvent = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 10f, 0)
        view.onInterceptTouchEvent(downEvent)

        val moveEvent = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_MOVE, 0f, 100f, 0)
        view.onInterceptTouchEvent(moveEvent)

        // Verify that the cached ACTION_DOWN and the new ACTION_MOVE events are dispatched.
        val captor = argumentCaptor<MotionEvent>()
        verify(windowRootView, times(2)).dispatchTouchEvent(captor.capture())

        val capturedEvents = captor.allValues
        assertThat(capturedEvents).hasSize(2)
        assertThat(capturedEvents[0].action).isEqualTo(MotionEvent.ACTION_DOWN)
        assertThat(capturedEvents[1].action).isEqualTo(MotionEvent.ACTION_MOVE)
    }

    @Test
    @EnableSceneContainer
    fun handleInterceptTouchEventFromStatusBar_smallSwipe_doesNotIntercept() {
        val downEvent = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 10f, 0)
        view.onInterceptTouchEvent(downEvent)

        val moveEvent = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_MOVE, 0f, 11f, 0)
        val intercepted = view.onInterceptTouchEvent(moveEvent)

        assertThat(intercepted).isFalse()
        verify(windowRootView, never()).dispatchTouchEvent(any())
    }

    @Test
    @EnableSceneContainer
    fun handleInterceptTouchEventFromStatusBar_horizontalSwipe_doesNotIntercept() {
        val downEvent = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 10f, 0)
        view.onInterceptTouchEvent(downEvent)

        val moveEvent = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_MOVE, 100f, 11f, 0)
        val intercepted = view.onInterceptTouchEvent(moveEvent)

        assertThat(intercepted).isFalse()
        verify(windowRootView, never()).dispatchTouchEvent(any())
    }

    @Test
    @EnableSceneContainer
    fun handleInterceptTouchEventFromStatusBar_clearsCacheBetweenGestures() {
        // Gesture 1: A short tap/swipe that does NOT trigger interception.
        val downEvent1 = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 10f, 0)
        view.onInterceptTouchEvent(downEvent1)

        val moveEvent1 = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_MOVE, 0f, 15f, 0)
        view.onInterceptTouchEvent(moveEvent1)

        val upEvent1 = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, 0f, 15f, 0)
        view.onInterceptTouchEvent(upEvent1)

        // Verify that no events were dispatched.
        verify(windowRootView, never()).dispatchTouchEvent(any())

        // Gesture 2: A clear swipe down that triggers interception.
        val downEvent2 = MotionEvent.obtain(100L, 100L, MotionEvent.ACTION_DOWN, 50f, 20f, 0)
        view.onInterceptTouchEvent(downEvent2)

        // Move a large amount, greater than the touch slop.
        val moveEvent2 = MotionEvent.obtain(100L, 100L, MotionEvent.ACTION_MOVE, 50f, 150f, 0)
        view.onInterceptTouchEvent(moveEvent2)

        val captor = argumentCaptor<MotionEvent>()
        verify(windowRootView, times(2)).dispatchTouchEvent(captor.capture())

        val capturedEvents = captor.allValues
        assertThat(capturedEvents).hasSize(2)

        assertThat(capturedEvents[0].action).isEqualTo(MotionEvent.ACTION_DOWN)
        assertThat(capturedEvents[0].downTime).isEqualTo(100L)
        assertThat(capturedEvents[1].action).isEqualTo(MotionEvent.ACTION_MOVE)
        assertThat(capturedEvents[1].y).isEqualTo(150f)
    }

    @Test
    fun onTouch_windowHidden_centralSurfacesNotNotified() {
        val callback = getCommandQueueCallback()
        callback.setWindowState(DISPLAY_ID, WINDOW_STATUS_BAR, WINDOW_STATE_HIDDEN)

        controller.onTouch(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 0f, 0))

        verify(centralSurfacesImpl, never()).setInteracting(any(), any())
    }

    @Test
    fun onTouch_windowHiding_centralSurfacesNotNotified() {
        val callback = getCommandQueueCallback()
        callback.setWindowState(DISPLAY_ID, WINDOW_STATUS_BAR, WINDOW_STATE_HIDING)

        controller.onTouch(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 0f, 0))

        verify(centralSurfacesImpl, never()).setInteracting(any(), any())
    }

    @Test
    fun onTouch_windowShowing_centralSurfacesNotified() {
        val callback = getCommandQueueCallback()
        callback.setWindowState(DISPLAY_ID, WINDOW_STATUS_BAR, WINDOW_STATE_SHOWING)

        controller.onTouch(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 0f, 0))

        verify(centralSurfacesImpl).setInteracting(any(), any())
    }

    @Test
    @EnableFlags(ShadeWindowGoesAround.FLAG_NAME)
    fun onInterceptTouchEvent_actionDown_propagatesToDisplayPolicy() {
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 0f, 0)

        view.onInterceptTouchEvent(event)

        verify(statusBarTouchShadeDisplayPolicy).onStatusBarOrLauncherTouched(eq(event), any())
    }

    @Test
    @EnableFlags(ShadeWindowGoesAround.FLAG_NAME)
    fun onInterceptTouchEvent_actionUp_notPropagatesToDisplayPolicy() {
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_UP, 0f, 0f, 0)

        view.onInterceptTouchEvent(event)

        verify(statusBarTouchShadeDisplayPolicy, never()).onStatusBarOrLauncherTouched(any(), any())
    }

    @Test
    @DisableFlags(ShadeWindowGoesAround.FLAG_NAME)
    fun onInterceptTouchEvent_shadeWindowGoesAroundDisabled_notPropagatesToDisplayPolicy() {
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 0f, 0)

        view.onInterceptTouchEvent(event)

        verify(statusBarTouchShadeDisplayPolicy, never())
            .onStatusBarOrLauncherTouched(eq(event), any())
    }

    @Test
    @EnableFlags(ShadeWindowGoesAround.FLAG_NAME)
    fun onTouch_withMouseOnEndSideIcons_flagOn_propagatedToShadeDisplayPolicy() {
        attachToWindow(view)
        controller = createAndInitController(view)
        val event = getActionUpEventFromSource(InputDevice.SOURCE_MOUSE)

        val statusContainer = view.requireViewById<View>(R.id.system_icons)
        statusContainer.dispatchTouchEvent(event)

        verify(statusBarTouchShadeDisplayPolicy).onStatusBarOrLauncherTouched(eq(event), any())
    }

    @Test
    @EnableFlags(ShadeWindowGoesAround.FLAG_NAME)
    fun onTouch_withMouseOnStartSideIcons_flagOn_propagatedToShadeDisplayPolicy() {
        attachToWindow(view)
        controller = createAndInitController(view)
        val event = getActionUpEventFromSource(InputDevice.SOURCE_MOUSE)

        val statusContainer = view.requireViewById<View>(R.id.status_bar_start_side_content)
        statusContainer.dispatchTouchEvent(event)

        verify(statusBarTouchShadeDisplayPolicy).onStatusBarOrLauncherTouched(eq(event), any())
    }

    @Test
    @DisableFlags(ShadeWindowGoesAround.FLAG_NAME)
    fun onTouch_withMouseOnSystemIcons_flagOff_notPropagatedToShadeDisplayPolicy() {
        attachToWindow(view)
        controller = createAndInitController(view)
        val event = getActionUpEventFromSource(InputDevice.SOURCE_MOUSE)

        val statusContainer = view.requireViewById<View>(R.id.system_icons)
        statusContainer.dispatchTouchEvent(event)

        verify(statusBarTouchShadeDisplayPolicy, never())
            .onStatusBarOrLauncherTouched(eq(event), any())
    }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    @DisableFlags(ShadeWindowGoesAround.FLAG_NAME)
    fun shouldAllowInteractions_shadeGoesAroundFlagOff_returnsFalse() {
        attachToWindow(viewForSecondaryDisplay)
        controller = createAndInitController(viewForSecondaryDisplay)
        val argumentCaptor = argumentCaptor<BooleanSupplier>()
        verify(viewForSecondaryDisplay).setIsStatusBarInteractiveSupplier(argumentCaptor.capture())

        assertThat(argumentCaptor.lastValue.asBoolean).isFalse()
    }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME, ShadeWindowGoesAround.FLAG_NAME)
    fun shouldAllowInteractions_defaultShadeDisplayPolicy_returnsFalse() {
        attachToWindow(viewForSecondaryDisplay)
        controller = createAndInitController(viewForSecondaryDisplay)
        val argumentCaptor = argumentCaptor<BooleanSupplier>()
        verify(viewForSecondaryDisplay).setIsStatusBarInteractiveSupplier(argumentCaptor.capture())

        whenever(shadeDisplayRepository.currentPolicy).thenReturn(kosmos.defaultShadeDisplayPolicy)
        assertThat(argumentCaptor.lastValue.asBoolean).isFalse()
    }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME, ShadeWindowGoesAround.FLAG_NAME)
    fun shouldAllowInteractions_statusBarTouchShadeDisplayPolicy_returnsTrue() {
        attachToWindow(viewForSecondaryDisplay)
        controller = createAndInitController(viewForSecondaryDisplay)
        val argumentCaptor = argumentCaptor<BooleanSupplier>()
        verify(viewForSecondaryDisplay).setIsStatusBarInteractiveSupplier(argumentCaptor.capture())

        whenever(shadeDisplayRepository.currentPolicy).thenReturn(statusBarTouchShadeDisplayPolicy)
        assertThat(argumentCaptor.lastValue.asBoolean).isTrue()
    }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME, ShadeWindowGoesAround.FLAG_NAME)
    fun shouldAllowInteractions_shadePolicyChanges_updatesReturnValue() {
        attachToWindow(viewForSecondaryDisplay)
        controller = createAndInitController(viewForSecondaryDisplay)
        val argumentCaptor = argumentCaptor<BooleanSupplier>()
        verify(viewForSecondaryDisplay).setIsStatusBarInteractiveSupplier(argumentCaptor.capture())

        whenever(shadeDisplayRepository.currentPolicy).thenReturn(kosmos.defaultShadeDisplayPolicy)
        assertThat(argumentCaptor.lastValue.asBoolean).isFalse()

        whenever(shadeDisplayRepository.currentPolicy).thenReturn(statusBarTouchShadeDisplayPolicy)
        assertThat(argumentCaptor.lastValue.asBoolean).isTrue()
    }

    @Test
    @EnableSceneContainer
    fun dualShade_qsIsExpandedOnEndSideContentMouseClick() =
        kosmos.runTest {
            enableDualShade(wideLayout = true)

            val shadeMode by collectLastValue(shadeModeInteractor.shadeMode)
            assertThat(shadeMode).isEqualTo(ShadeMode.Dual)

            attachToWindow(view)
            controller = createAndInitController(view)
            val endSideContainer = view.requireViewById<View>(R.id.system_icons)
            endSideContainer.dispatchTouchEvent(
                getActionUpEventFromSource(InputDevice.SOURCE_MOUSE)
            )

            verify(shadeControllerImpl).animateExpandQs()
            verify(shadeControllerImpl, never()).animateExpandShade()
        }

    @Test
    fun shadeIsExpandedOnEndSideContentMouseClick_singleShade_expandsNotificationsShade() {
        kosmos.enableSingleShade()
        attachToWindow(view)
        controller = createAndInitController(view)
        val endSideContainer = view.requireViewById<View>(R.id.system_icons)
        endSideContainer.dispatchTouchEvent(getActionUpEventFromSource(InputDevice.SOURCE_MOUSE))

        verify(shadeControllerImpl).animateExpandShade()
        verify(shadeControllerImpl, never()).animateExpandQs()
    }

    @Test
    @EnableSceneContainer
    fun shadeIsExpandedOnEndSideContentMouseClick_dualShade_expandsQuickSettingsShade() {
        kosmos.enableDualShade()
        attachToWindow(view)
        controller = createAndInitController(view)
        val endSideContainer = view.requireViewById<View>(R.id.system_icons)
        endSideContainer.dispatchTouchEvent(getActionUpEventFromSource(InputDevice.SOURCE_MOUSE))

        verify(shadeControllerImpl, never()).animateExpandShade()
        verify(shadeControllerImpl).animateExpandQs()
    }

    @Test
    fun shadeIsExpandedOnStartSideContentMouseClick() {
        attachToWindow(view)
        controller = createAndInitController(view)

        val startSideContainer = view.requireViewById<View>(R.id.status_bar_start_side_content)
        startSideContainer.dispatchTouchEvent(getActionUpEventFromSource(InputDevice.SOURCE_MOUSE))

        verify(shadeControllerImpl).animateExpandShade()
        verify(shadeControllerImpl, never()).animateExpandQs()
    }

    @Test
    fun shadeIsExpandedOnStartSideContentTap_flagOn() {
        Assume.assumeTrue(mContext.resources.getBoolean(R.bool.config_statusBarTapToExpandShade))
        attachToWindow(view)
        controller = createAndInitController(view)

        val startSideContainer = view.requireViewById<View>(R.id.status_bar_start_side_content)
        startSideContainer.dispatchTouchEvent(
            getActionDownEventFromSource(InputDevice.SOURCE_TOUCHSCREEN)
        )
        startSideContainer.dispatchTouchEvent(
            getActionUpEventFromSource(InputDevice.SOURCE_TOUCHSCREEN)
        )

        verify(shadeControllerImpl).animateExpandShade()
        verify(shadeControllerImpl, never()).animateExpandQs()
    }

    @Test
    fun shadeIsNotExpandedOnStartSideContentTap_flagoff() {
        Assume.assumeFalse(mContext.resources.getBoolean(R.bool.config_statusBarTapToExpandShade))
        attachToWindow(view)
        controller = createAndInitController(view)

        val startSideContainer = view.requireViewById<View>(R.id.status_bar_start_side_content)
        startSideContainer.dispatchTouchEvent(
            getActionDownEventFromSource(InputDevice.SOURCE_TOUCHSCREEN)
        )
        startSideContainer.dispatchTouchEvent(
            getActionUpEventFromSource(InputDevice.SOURCE_TOUCHSCREEN)
        )

        verify(shadeControllerImpl, never()).animateExpandShade()
    }

    @Test
    fun statusIconContainerIsHandlingTouchScreenTaps_singleShade_expandsNotificationsShade_flagOn() {
        Assume.assumeTrue(mContext.resources.getBoolean(R.bool.config_statusBarTapToExpandShade))
        kosmos.enableSingleShade()
        attachToWindow(view)
        controller = createAndInitController(view)
        val statusContainer = view.requireViewById<View>(R.id.system_icons)
        statusContainer.dispatchTouchEvent(
            getActionDownEventFromSource(InputDevice.SOURCE_TOUCHSCREEN)
        )
        statusContainer.dispatchTouchEvent(
            getActionUpEventFromSource(InputDevice.SOURCE_TOUCHSCREEN)
        )

        verify(shadeControllerImpl).animateExpandShade()
        verify(shadeControllerImpl, never()).animateExpandQs()
    }

    @Test
    @EnableSceneContainer
    fun statusIconContainerIsNotHandlingTouchScreenTaps_flagOff() {
        Assume.assumeFalse(mContext.resources.getBoolean(R.bool.config_statusBarTapToExpandShade))
        kosmos.enableSingleShade()
        attachToWindow(view)
        controller = createAndInitController(view)
        val statusContainer = view.requireViewById<View>(R.id.system_icons)
        statusContainer.dispatchTouchEvent(
            getActionDownEventFromSource(InputDevice.SOURCE_TOUCHSCREEN)
        )
        statusContainer.dispatchTouchEvent(
            getActionUpEventFromSource(InputDevice.SOURCE_TOUCHSCREEN)
        )

        verify(shadeControllerImpl, never()).animateExpandShade()
    }

    @Test
    @EnableSceneContainer
    fun statusIconContainerIsHandlingTouchScreenTaps_dualShade_expandsQuickSettingsShade_flagOn() {
        Assume.assumeTrue(mContext.resources.getBoolean(R.bool.config_statusBarTapToExpandShade))
        kosmos.enableDualShade()
        attachToWindow(view)
        controller = createAndInitController(view)
        val endSideContainer = view.requireViewById<View>(R.id.system_icons)
        endSideContainer.dispatchTouchEvent(
            getActionDownEventFromSource(InputDevice.SOURCE_TOUCHSCREEN)
        )
        endSideContainer.dispatchTouchEvent(
            getActionUpEventFromSource(InputDevice.SOURCE_TOUCHSCREEN)
        )

        verify(shadeControllerImpl, never()).animateExpandShade()
        verify(shadeControllerImpl).animateExpandQs()
    }

    @Test
    @EnableSceneContainer
    fun statusIconContainerIsNotHandlingTouchScreenTaps_dualShade_flagOff() {
        Assume.assumeFalse(mContext.resources.getBoolean(R.bool.config_statusBarTapToExpandShade))
        kosmos.enableDualShade()
        attachToWindow(view)
        controller = createAndInitController(view)
        val endSideContainer = view.requireViewById<View>(R.id.system_icons)
        endSideContainer.dispatchTouchEvent(
            getActionDownEventFromSource(InputDevice.SOURCE_TOUCHSCREEN)
        )
        endSideContainer.dispatchTouchEvent(
            getActionUpEventFromSource(InputDevice.SOURCE_TOUCHSCREEN)
        )

        verify(shadeControllerImpl, never()).animateExpandQs()
    }

    private fun getActionDownEventFromSource(source: Int): MotionEvent {
        val ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
        ev.source = source
        return ev
    }

    private fun getActionUpEventFromSource(source: Int): MotionEvent {
        val ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 0f, 0)
        ev.source = source
        return ev
    }

    @Test
    fun shadeIsNotExpandedOnStatusBarGeneralClick() {
        attachToWindow(view)
        controller = createAndInitController(view)
        view.performClick()
        verify(shadeControllerImpl, never()).animateExpandShade()
    }

    @Test
    @DisableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun connectedDisplayFlagOff_windowControllerIsSetInView() {
        attachToWindow(view)

        controller = createAndInitController(view)

        verify(view).setStatusBarWindowControllerStore(statusBarWindowControllerStore)
    }

    @Test
    @EnableFlags(StatusBarConnectedDisplays.FLAG_NAME)
    fun connectedDisplayFlagOn_windowControllerIsNotSetInView() {
        attachToWindow(view)

        controller = createAndInitController(view)

        verify(view, never()).setStatusBarWindowControllerStore(statusBarWindowControllerStore)
    }

    private fun getCommandQueueCallback(): CommandQueue.Callbacks {
        val captor = argumentCaptor<CommandQueue.Callbacks>()
        verify(commandQueue).addCallback(captor.capture())
        return captor.lastValue
    }

    private fun createView(context: Context): PhoneStatusBarView {
        val parent = FrameLayout(context) // add parent to keep layout params
        val view =
            spy(
                LayoutInflater.from(context).inflate(R.layout.status_bar, parent, false)
                    as PhoneStatusBarView
            )
        whenever(view.viewRootImpl).thenReturn(mock(ViewRootImpl::class.java))
        view.updateTouchableRegion(TOUCHABLE_REGION)
        return view
    }

    private fun attachToWindow(view: PhoneStatusBarView) {
        val viewTreeObserver = mock(ViewTreeObserver::class.java)
        whenever(view.viewTreeObserver).thenReturn(viewTreeObserver)
        whenever(view.isAttachedToWindow).thenReturn(true)
    }

    private fun createAndInitController(view: PhoneStatusBarView): PhoneStatusBarViewController {
        return PhoneStatusBarViewController.Factory(
                Optional.of(progressProvider),
                userChipViewModel,
                centralSurfacesImpl,
                statusBarWindowStateController,
                shadeControllerImpl,
                shadeViewController,
                kosmos.shadeModeInteractor,
                panelExpansionInteractor,
                { mStatusBarLongPressGestureDetector },
                { windowRootView },
                shadeLogger,
                viewUtil,
                mStatusBarConfigurationController,
                mStatusOverlayHoverListenerFactory,
                fakeDarkIconDispatcher,
                statusBarContentInsetsProviderStore,
                { statusBarTouchShadeDisplayPolicy },
                { shadeDisplayRepository },
                statusBarWindowControllerStore,
            )
            .create(view)
            .also { it.init() }
    }

    private companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }

        const val DISPLAY_ID = 0
        const val SECONDARY_DISPLAY_ID = 2
        val TOUCHABLE_REGION = Region(0, 0, 500, 500)
    }
}
