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
package com.android.wm.shell.common.split;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;

import static com.android.internal.config.sysui.SystemUiDeviceConfigFlags.CURSOR_HOVER_STATES_ENABLED;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.DeviceConfig;
import android.view.InputDevice;
import android.view.InsetsState;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.split.DividerSnapAlgorithm.SnapTarget;
import com.android.wm.shell.shared.desktopmode.FakeDesktopState;
import com.android.wm.shell.splitscreen.SplitStatusBarHider;

import com.google.android.msdl.domain.MSDLPlayer;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link DividerView} */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DividerViewTest extends ShellTestCase {
    private @Mock SplitWindowManager.ParentContainerCallbacks mCallbacks;
    private @Mock SplitLayout.SplitLayoutHandler mSplitLayoutHandler;
    private @Mock DisplayController mDisplayController;
    private @Mock DisplayImeController mDisplayImeController;
    private @Mock ShellTaskOrganizer mTaskOrganizer;
    private @Mock SplitState mSplitState;
    private @Mock Handler mHandler;
    private @Mock SplitStatusBarHider mStatusBarHider;
    private @Mock MSDLPlayer mMSDLPlayer;
    private @Mock SnapTarget mSnapTarget;
    private FakeDesktopState mDesktopState;
    private SplitLayout mSplitLayout;
    private DividerView mDividerView;

    @Before
    @UiThreadTest
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mDesktopState = new FakeDesktopState();
        Configuration configuration = getConfiguration();
        mSplitLayout = spy(new SplitLayout("TestSplitLayout", mContext, configuration,
                mSplitLayoutHandler, mCallbacks, mDisplayController, mDisplayImeController,
                mTaskOrganizer, SplitLayout.PARALLAX_NONE, mSplitState, mHandler, mStatusBarHider,
                mDesktopState, mMSDLPlayer));
        SplitWindowManager splitWindowManager = new SplitWindowManager("TestSplitWindowManager",
                mContext, configuration, mCallbacks);
        splitWindowManager.init(mSplitLayout, new InsetsState(), false /* isRestoring */,
                mDesktopState);
        mDividerView = spy(splitWindowManager.getDividerView());
        doReturn(mSnapTarget).when(mSplitLayout).findSnapTarget(anyInt(), anyFloat(), anyBoolean());
    }

    @Test
    @UiThreadTest
    public void testHoverDividerView() {
        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_SYSTEMUI, CURSOR_HOVER_STATES_ENABLED,
                "true", false);

        Rect dividerBounds = mSplitLayout.getDividerBounds();
        int x = dividerBounds.centerX();
        int y = dividerBounds.centerY();
        long downTime = SystemClock.uptimeMillis();
        mDividerView.onHoverEvent(getMotionEvent(downTime, MotionEvent.ACTION_HOVER_ENTER, x, y));

        verify(mDividerView, times(1)).setHovering();

        mDividerView.onHoverEvent(getMotionEvent(downTime, MotionEvent.ACTION_HOVER_EXIT, x, y));

        verify(mDividerView, times(1)).releaseHovering();

        DeviceConfig.setProperty(DeviceConfig.NAMESPACE_SYSTEMUI, CURSOR_HOVER_STATES_ENABLED,
                "false", false);
    }

    @Test
    public void swapDividerActionForA11y() {
        mDividerView.setAccessibilityDelegate(mDividerView.mHandleDelegate);
        mDividerView.getAccessibilityDelegate().performAccessibilityAction(mDividerView,
                R.id.action_swap_apps, null);
        verify(mSplitLayout, times(1)).onDoubleTappedDivider();
    }

    @Test
    @UiThreadTest
    public void onTouch_moveWithoutDown_returnsFalse() {
        // Simulate a move event without a preceding down event.
        long eventTime = SystemClock.uptimeMillis();
        MotionEvent moveEvent = getMotionEvent(eventTime, MotionEvent.ACTION_MOVE, 100, 100);

        // The touch event should be ignored.
        assertFalse(mDividerView.onTouch(mDividerView, moveEvent));

        // Verify that no dragging operations were initiated.
        verify(mSplitLayout, never()).onStartDragging();
    }

    @Test
    @UiThreadTest
    @Ignore("b/482156644")
    public void onTouch_dragAndRelease_snapsToTarget() {
        // Define touch coordinates.
        final int startX = 100;
        final int startY = 100;
        final int touchSlop = ViewConfiguration.get(mContext).getScaledTouchSlop();
        final int endX = startX + touchSlop + 50;

        // Simulate a full drag gesture: DOWN -> MOVE -> UP.
        long downTime = SystemClock.uptimeMillis();
        MotionEvent downEvent = getMotionEvent(downTime, MotionEvent.ACTION_DOWN, startX, startY);
        mDividerView.onTouch(mDividerView, downEvent);

        // Verify dragging started, but the divider is not yet considered "moving".
        verify(mSplitLayout).onStartDragging();
        assertFalse(mDividerView.isMoving());

        long moveTime = downTime + 10;
        MotionEvent moveEvent = getMotionEvent(downTime, moveTime, MotionEvent.ACTION_MOVE, endX,
                startY);
        mDividerView.onTouch(mDividerView, moveEvent);

        // Verify that after moving beyond the touch slop, the divider is "moving".
        assertTrue(mDividerView.isMoving());

        long upTime = moveTime + 10;
        MotionEvent upEvent = getMotionEvent(downTime, upTime, MotionEvent.ACTION_UP, endX,
                startY);
        mDividerView.onTouch(mDividerView, upEvent);

        // Verify that on release, the layout snaps to the calculated target.
        verify(mSplitLayout).findSnapTarget(anyInt(), anyFloat(), anyBoolean());
        verify(mSplitLayout).snapToTarget(anyInt(), any(SnapTarget.class));
        // Verify dragging is finished.
        assertFalse(mDividerView.isMoving());
    }

    @Test
    @UiThreadTest
    public void onTouch_tapWithoutMove_cancelsDragging() {
        // Define touch coordinates.
        final int startX = 100;
        final int startY = 100;

        // Simulate a tap gesture: DOWN -> UP, without a MOVE event.
        long downTime = SystemClock.uptimeMillis();
        MotionEvent downEvent = getMotionEvent(downTime, MotionEvent.ACTION_DOWN, startX, startY);
        mDividerView.onTouch(mDividerView, downEvent);

        long upTime = downTime + 10;
        MotionEvent upEvent = getMotionEvent(downTime, upTime, MotionEvent.ACTION_UP, startX,
                startY);
        mDividerView.onTouch(mDividerView, upEvent);

        // Verify that dragging was initiated but then cancelled because there was no move.
        verify(mSplitLayout).onStartDragging();
        verify(mSplitLayout).onDraggingCancelled();
        // Verify that no snapping occurs for a simple tap.
        verify(mSplitLayout, never()).findSnapTarget(anyInt(), anyFloat(), anyBoolean());
        verify(mSplitLayout, never()).snapToTarget(anyInt(), any(SnapTarget.class));
        // Verify dragging is finished.
        assertFalse(mDividerView.isMoving());
    }

    private static MotionEvent getMotionEvent(long eventTime, int action, float x, float y) {
        return getMotionEvent(eventTime, eventTime, action, x, y);
    }

    private static MotionEvent getMotionEvent(long downTime, long eventTime, int action, float x,
            float y) {
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = MotionEvent.TOOL_TYPE_UNKNOWN;

        MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.pressure = 1;
        coords.size = 1;
        coords.x = x;
        coords.y = y;

        return MotionEvent.obtain(downTime, eventTime, action, 1,
                new MotionEvent.PointerProperties[]{properties},
                new MotionEvent.PointerCoords[]{coords}, 0, 0, 1.0f, 1.0f, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0);
    }

    private static Configuration getConfiguration() {
        final Configuration configuration = new Configuration();
        configuration.unset();
        configuration.orientation = ORIENTATION_LANDSCAPE;
        configuration.windowConfiguration.setRotation(0);
        configuration.windowConfiguration.setBounds(new Rect(0, 0, 1080, 2160));
        return configuration;
    }
}
