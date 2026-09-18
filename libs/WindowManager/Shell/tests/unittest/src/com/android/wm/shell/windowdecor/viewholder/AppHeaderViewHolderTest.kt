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

import android.app.ActivityManager
import android.content.Context
import android.graphics.Rect
import android.platform.test.annotations.EnableFlags
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.view.MotionEventBuilder
import com.android.window.flags.Flags
import com.android.wm.shell.R
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger
import com.android.wm.shell.transition.FocusTransitionObserver
import com.android.wm.shell.windowdecor.MaximizeButtonView
import com.android.wm.shell.windowdecor.WindowDecorLinearLayout
import com.android.wm.shell.windowdecor.WindowDecorationActions
import com.android.wm.shell.windowdecor.WindowDecorationTestHelper
import com.android.wm.shell.windowdecor.common.DecorThemeUtil
import com.android.wm.shell.windowdecor.common.DrawableInsets
import com.android.wm.shell.windowdecor.common.Theme
import com.android.wm.shell.windowdecor.viewholder.util.HeaderDimensions
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [AppHeaderViewHolder].
 *
 * Build/Install/Run: atest WMShellUnitTests:AppHeaderViewHolderTest
 */
class AppHeaderViewHolderTest : ShellTestCase() {

    private val mockAppHeader = mock<WindowDecorLinearLayout>()
    private val mockCaptionHandle = mock<ViewGroup>()
    private val mockOpenMenuButton = mock<View>()
    private val mockCloseWindowButton = mock<ImageButton>()
    private val mockExpandMenuButton = mock<ImageButton>()
    private val mockMaximizeButtonView = mock<MaximizeButtonView>()
    private val mockMaximizeWindowButton = mock<ImageButton>()
    private val mockMinimizeWindowButton = mock<ImageButton>()
    private val mockAppTextView = mock<TextView>()
    private val mockAppIconView = mock<ImageView>()
    private val mockExpandMenuErrorImageView = mock<ImageView>()

    private val mockWindowDecorationActions = mock<WindowDecorationActions>()
    private val mockGestureInterceptor = mock<WindowDecorLinearLayout.GestureInterceptor>()
    private val mockOnLongClickListener = mock<View.OnLongClickListener>()
    private val mockOnCaptionGenericMotionListener = mock<View.OnGenericMotionListener>()
    private val mockOnMaximizeHoverAnimationFinishedListener = mock<() -> Unit>()
    private val mockDesktopModeUiEventLogger = mock<DesktopModeUiEventLogger>()
    private val mockDimensions = mock<HeaderDimensions>()
    private val mockFocusTransitionObserver = mock<FocusTransitionObserver>()
    private val mockDecorThemeUtilFactory = mock<DecorThemeUtil.Factory>()

    private val task = WindowDecorationTestHelper.createAppHeaderTask()

    private lateinit var appHeaderViewHolder: AppHeaderViewHolder

    @Before
    fun setup() {
        doReturn(mockAppHeader)
            .whenever(mockAppHeader)
            .requireViewById<WindowDecorLinearLayout>(R.id.desktop_mode_caption)
        doReturn(mockCaptionHandle)
            .whenever(mockAppHeader)
            .requireViewById<View>(R.id.caption_handle)
        doReturn(mockOpenMenuButton)
            .whenever(mockAppHeader)
            .requireViewById<View>(R.id.open_menu_button)
        doReturn(mockCloseWindowButton)
            .whenever(mockAppHeader)
            .requireViewById<ImageButton>(R.id.close_window)
        doReturn(mockExpandMenuButton)
            .whenever(mockAppHeader)
            .requireViewById<View>(R.id.expand_menu_button)
        doReturn(mockMaximizeButtonView)
            .whenever(mockAppHeader)
            .requireViewById<MaximizeButtonView>(R.id.maximize_button_view)
        doReturn(mockMaximizeWindowButton)
            .whenever(mockAppHeader)
            .requireViewById<ImageButton>(R.id.maximize_window)
        doReturn(mockMinimizeWindowButton)
            .whenever(mockAppHeader)
            .requireViewById<ImageButton>(R.id.minimize_window)
        doReturn(mockAppTextView)
            .whenever(mockAppHeader)
            .requireViewById<TextView>(R.id.application_name)
        doReturn(mockAppIconView)
            .whenever(mockAppHeader)
            .requireViewById<ImageView>(R.id.application_icon)
        doReturn(mockExpandMenuErrorImageView)
            .whenever(mockAppHeader)
            .requireViewById<ImageView>(R.id.expand_menu_error)

        doReturn(mock<ViewGroup.LayoutParams>()).whenever(mockOpenMenuButton).layoutParams
        doReturn(mock<ViewGroup.LayoutParams>()).whenever(mockMinimizeWindowButton).layoutParams
        doReturn(mock<ViewGroup.LayoutParams>()).whenever(mockCloseWindowButton).layoutParams

        doReturn(Rect()).whenever(mockDimensions).windowControlButtonPadding
        doReturn(DrawableInsets()).whenever(mockDimensions).appChipBackgroundInsets
        doReturn(DrawableInsets()).whenever(mockDimensions).minimizeBackgroundInsets
        doReturn(DrawableInsets()).whenever(mockDimensions).maximizeBackgroundInsets
        doReturn(DrawableInsets()).whenever(mockDimensions).closeBackgroundInsets

        mContext.addMockSystemService(Context.ACCESSIBILITY_SERVICE, mock<AccessibilityManager>())
        listOf(
                mockOpenMenuButton,
                mockMinimizeWindowButton,
                mockMaximizeWindowButton,
                mockCloseWindowButton,
            )
            .forEach { doReturn(mContext).whenever(it).context }

        val mockDecorThemeUtil = mock<DecorThemeUtil>()
        doReturn(Theme.DARK).whenever(mockDecorThemeUtil).getAppTheme(task)
        doReturn(mockDecorThemeUtil).whenever(mockDecorThemeUtilFactory).create(mContext)

        appHeaderViewHolder =
            AppHeaderViewHolder(
                mockAppHeader,
                mContext,
                mockWindowDecorationActions,
                mockGestureInterceptor,
                mockOnLongClickListener,
                mockOnCaptionGenericMotionListener,
                mockOnMaximizeHoverAnimationFinishedListener,
                mockDesktopModeUiEventLogger,
                mockDimensions,
                mockFocusTransitionObserver,
                mockDecorThemeUtilFactory,
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_INTERCEPT_TOUCH_EVENT_FOR_APP_HEADER_DRAG_MOVE)
    fun testClickOnMaximize_movesTaskToFront() {
        bindData()

        val onTouchListenerCaptor = ArgumentCaptor.forClass(View.OnTouchListener::class.java)
        verify(mockMaximizeWindowButton).setOnTouchListener(onTouchListenerCaptor.capture())

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_DOWN)
                .setPointer(50f, 50f)
                .build()
        onTouchListenerCaptor.value.onTouch(mockMaximizeWindowButton, downEvent)

        verify(mockWindowDecorationActions).onCaptionViewReceivedInteraction(task)
    }

    @Test
    @EnableFlags(Flags.FLAG_INTERCEPT_TOUCH_EVENT_FOR_APP_HEADER_DRAG_MOVE)
    fun testClickOnMinimize_movesTaskToFront() {
        bindData()

        val onTouchListenerCaptor = ArgumentCaptor.forClass(View.OnTouchListener::class.java)
        verify(mockMinimizeWindowButton).setOnTouchListener(onTouchListenerCaptor.capture())

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_DOWN)
                .setPointer(50f, 50f)
                .build()
        onTouchListenerCaptor.value.onTouch(mockMinimizeWindowButton, downEvent)

        verify(mockWindowDecorationActions).onCaptionViewReceivedInteraction(task)
    }

    @Test
    @EnableFlags(Flags.FLAG_INTERCEPT_TOUCH_EVENT_FOR_APP_HEADER_DRAG_MOVE)
    fun testClickOnOpenMenu_movesTaskToFront() {
        bindData()

        val onTouchListenerCaptor = ArgumentCaptor.forClass(View.OnTouchListener::class.java)
        verify(mockOpenMenuButton).setOnTouchListener(onTouchListenerCaptor.capture())

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_DOWN)
                .setPointer(50f, 50f)
                .build()
        onTouchListenerCaptor.value.onTouch(mockOpenMenuButton, downEvent)

        verify(mockWindowDecorationActions).onCaptionViewReceivedInteraction(task)
    }

    @Test
    @EnableFlags(Flags.FLAG_INTERCEPT_TOUCH_EVENT_FOR_APP_HEADER_DRAG_MOVE)
    fun testClickOnClose_movesTaskToFront() {
        bindData()

        val onTouchListenerCaptor = ArgumentCaptor.forClass(View.OnTouchListener::class.java)
        verify(mockCloseWindowButton).setOnTouchListener(onTouchListenerCaptor.capture())

        val downEvent =
            MotionEventBuilder.newBuilder()
                .setAction(MotionEvent.ACTION_DOWN)
                .setPointer(50f, 50f)
                .build()
        onTouchListenerCaptor.value.onTouch(mockCloseWindowButton, downEvent)

        verify(mockWindowDecorationActions).onCaptionViewReceivedInteraction(task)
    }

    private fun bindData(
        taskInfo: ActivityManager.RunningTaskInfo = task,
        isTaskMaximized: Boolean = false,
        inFullImmersiveState: Boolean = false,
        hasGlobalFocus: Boolean = false,
        enableMaximizeLongClick: Boolean = false,
        isCaptionVisible: Boolean = false,
    ) {
        appHeaderViewHolder.bindData(
            AppHeaderViewHolder.HeaderData(
                taskInfo,
                isTaskMaximized,
                inFullImmersiveState,
                hasGlobalFocus,
                enableMaximizeLongClick,
                isCaptionVisible,
            )
        )
    }
}
