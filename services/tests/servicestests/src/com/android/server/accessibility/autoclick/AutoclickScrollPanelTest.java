/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.accessibility.autoclick;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.never;

import android.content.Context;
import android.testing.AndroidTestingRunner;
import android.testing.TestableContext;
import android.testing.TestableLooper;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;

import com.android.internal.R;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Test cases for {@link AutoclickScrollPanel}. */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class AutoclickScrollPanelTest {
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule
    public TestableContext mTestableContext =
            new TestableContext(getInstrumentation().getContext());

    @Mock private WindowManager mMockWindowManager;
    @Mock private AutoclickScrollPanel.ScrollPanelControllerInterface mMockScrollPanelController;

    private AutoclickScrollPanel mScrollPanel;

    // Scroll panel buttons.
    private ImageButton mUpButton;
    private ImageButton mDownButton;
    private ImageButton mLeftButton;
    private ImageButton mRightButton;
    private ImageButton mExitButton;

    private int mScreenWidth;
    private int mScreenHeight;

    @Before
    public void setUp() {
        mTestableContext.addMockSystemService(Context.WINDOW_SERVICE, mMockWindowManager);
        mScrollPanel = new AutoclickScrollPanel(mTestableContext, mMockWindowManager,
                mMockScrollPanelController);

        View contentView = mScrollPanel.getContentViewForTesting();

        // Initialize buttons.
        mUpButton = contentView.findViewById(R.id.scroll_up);
        mDownButton = contentView.findViewById(R.id.scroll_down);
        mLeftButton = contentView.findViewById(R.id.scroll_left);
        mRightButton = contentView.findViewById(R.id.scroll_right);
        mExitButton = contentView.findViewById(R.id.scroll_exit);

        DisplayMetrics displayMetrics = mTestableContext.getResources().getDisplayMetrics();
        mScreenWidth = displayMetrics.widthPixels;
        mScreenHeight = displayMetrics.heightPixels;
    }

    @Test
    public void show_addsViewToWindowManager() {
        mScrollPanel.show();

        // Verify view is added to window manager.
        verify(mMockWindowManager).addView(any(), any(WindowManager.LayoutParams.class));

        // Verify isVisible reflects correct state.
        assertThat(mScrollPanel.isVisible()).isTrue();
    }

    @Test
    public void show_alreadyVisible_doesNotAddAgain() {
        // Show twice.
        mScrollPanel.show();
        mScrollPanel.show();

        // Verify addView was only called once.
        verify(mMockWindowManager, times(1)).addView(any(), any());
    }

    @Test
    public void hide_removesViewFromWindowManager() {
        // First show the panel.
        mScrollPanel.show();
        // Then hide it.
        mScrollPanel.hide();
        // Verify view is removed from window manager.
        verify(mMockWindowManager).removeView(any());
        // Verify scroll panel is hidden.
        assertThat(mScrollPanel.isVisible()).isFalse();
    }

    @Test
    public void initialState_correctButtonVisibility() {
        // Verify all expected buttons exist in the view.
        assertThat(mUpButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mDownButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mLeftButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mRightButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mExitButton.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void directionButtons_hoverEvents_callsHoverButtonChange() {
        // Test hover enter on direction button.
        triggerHoverEvent(mUpButton, MotionEvent.ACTION_HOVER_ENTER);
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_UP), eq(/* hovered= */ true));

        // Test hover move.
        reset(mMockScrollPanelController);
        triggerHoverEvent(mUpButton, MotionEvent.ACTION_HOVER_MOVE);
        verify(mMockScrollPanelController, never()).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_UP), eq(/* hovered= */ true));

        // Test hover exit.
        reset(mMockScrollPanelController);
        triggerHoverEvent(mUpButton, MotionEvent.ACTION_HOVER_EXIT);
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_UP), eq(/* hovered= */ false));
    }

    @Test
    public void exitButton_hoverEvents_callsHoverButtonChange() {
        // Test hover enter on exit button.
        triggerHoverEvent(mExitButton, MotionEvent.ACTION_HOVER_ENTER);
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_EXIT), eq(/* hovered= */ true));

        // Test hover exit - should call the hover change method with false.
        reset(mMockScrollPanelController);
        triggerHoverEvent(mExitButton, MotionEvent.ACTION_HOVER_EXIT);
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_EXIT), eq(/* hovered= */ false));

        // Test exit button hover move - should be ignored.
        reset(mMockScrollPanelController);
        triggerHoverEvent(mExitButton, MotionEvent.ACTION_HOVER_MOVE);
        verify(mMockScrollPanelController, never()).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_EXIT), anyBoolean());
    }

    @Test
    public void hoverOnButtonSequence_handledCorrectly() {
        // Test a realistic sequence of events.
        // Case 1. Hover enter on up button, then hover move with in up button twice.
        reset(mMockScrollPanelController);
        triggerHoverEvent(mUpButton, MotionEvent.ACTION_HOVER_ENTER);
        triggerHoverEvent(mUpButton, MotionEvent.ACTION_HOVER_MOVE);
        triggerHoverEvent(mUpButton, MotionEvent.ACTION_HOVER_MOVE);
        verify(mMockScrollPanelController, times(1)).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_UP), eq(true));

        // Case 2. Move from left button to exit button.
        reset(mMockScrollPanelController);
        triggerHoverEvent(mLeftButton, MotionEvent.ACTION_HOVER_ENTER);
        triggerHoverEvent(mLeftButton, MotionEvent.ACTION_HOVER_MOVE);
        triggerHoverEvent(mLeftButton, MotionEvent.ACTION_HOVER_EXIT);
        triggerHoverEvent(mExitButton, MotionEvent.ACTION_HOVER_ENTER);
        triggerHoverEvent(mExitButton, MotionEvent.ACTION_HOVER_MOVE);
        triggerHoverEvent(mExitButton, MotionEvent.ACTION_HOVER_EXIT);

        // Verify left button events - 1 'true' call (enter) and 1 'false' call (exit).
        verify(mMockScrollPanelController, times(1)).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_LEFT), eq(/* hovered= */ true));
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_LEFT), eq(/* hovered= */ false));

        // Verify exit button events - 1 'true' call (enter) and 1 'false' call (exit).
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_EXIT), eq(/* hovered= */ true));
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_EXIT), eq(/* hovered= */ false));

        // Case 3. Quick transitions between buttons: left → right → down → exit
        reset(mMockScrollPanelController);
        triggerHoverEvent(mLeftButton, MotionEvent.ACTION_HOVER_EXIT);
        triggerHoverEvent(mRightButton, MotionEvent.ACTION_HOVER_ENTER);
        triggerHoverEvent(mRightButton, MotionEvent.ACTION_HOVER_EXIT);
        triggerHoverEvent(mDownButton, MotionEvent.ACTION_HOVER_ENTER);
        triggerHoverEvent(mDownButton, MotionEvent.ACTION_HOVER_EXIT);
        triggerHoverEvent(mExitButton, MotionEvent.ACTION_HOVER_ENTER);

        // Verify all hover enter/exit events were properly handled
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_LEFT), eq(/* hovered= */ false));
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_RIGHT), eq(/* hovered= */ true));
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_RIGHT), eq(/* hovered= */ false));
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_DOWN), eq(/* hovered= */ true));
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_DOWN), eq(/* hovered= */ false));
        verify(mMockScrollPanelController).onHoverButtonChange(
                eq(AutoclickScrollPanel.DIRECTION_EXIT), eq(/* hovered= */ true));
    }

    @Test
    public void show_withCursorPosition_addsView() {
        float cursorX = 300;
        float cursorY = 300;

        // Call the new method with cursor coordinates.
        mScrollPanel.show(cursorX, cursorY);

        // Verify view is added to window manager.
        verify(mMockWindowManager).addView(eq(mScrollPanel.getContentViewForTesting()),
                any(WindowManager.LayoutParams.class));
        // Verify panel is visible.
        assertThat(mScrollPanel.isVisible()).isTrue();
    }

    @Test
    public void hideAndReshow_updatesPosition() {
        // First show at one position.
        float firstX = 300;
        float firstY = 300;
        mScrollPanel.show(firstX, firstY);
        assertThat(mScrollPanel.isVisible()).isTrue();

        // Hide panel.
        mScrollPanel.hide();
        assertThat(mScrollPanel.isVisible()).isFalse();

        // Show at different position.
        float secondX = 500;
        float secondY = 500;
        mScrollPanel.show(secondX, secondY);

        // Verify panel is visible.
        assertThat(mScrollPanel.isVisible()).isTrue();

        // Verify view was added twice to window manager.
        verify(mMockWindowManager, times(2)).addView(eq(mScrollPanel.getContentViewForTesting()),
                any(WindowManager.LayoutParams.class));
    }

    @Ignore
    @Test
    public void showPanel_normalCase() {
        // Normal case, position at (100, 100).
        int cursorX = 100;
        int cursorY = 100;

        // Capture the current layout params before positioning.
        WindowManager.LayoutParams params = mScrollPanel.getLayoutParamsForTesting();
        mScrollPanel.positionPanelAtCursor(cursorX, cursorY);

        // Calculate expected position for bottom-right placement.
        int margin = 10;
        int xOffset = mScrollPanel.getPanelWidthForTesting() / 2 + margin;
        int yOffset = mScrollPanel.getPanelHeightForTesting() / 2 + margin;
        int expectedX = cursorX + xOffset - mScrollPanel.getPanelWidthForTesting() / 2;
        int expectedY = (cursorY - mScrollPanel.getStatusBarHeightForTesting()) + yOffset
                - mScrollPanel.getPanelHeightForTesting() / 2;

        // Verify panel's position.
        assertThat(params.x).isEqualTo(expectedX);
        assertThat(params.y).isEqualTo(expectedY);
    }

    @Test
    public void showPanel_nearRightEdge_positionsLeftOfCursor() {
        // Near right edge case.
        // 100px from right edge.
        int cursorX = mScreenWidth - 10;
        // Center of screen vertically.
        int cursorY = mScreenHeight / 2;

        // Capture the current layout params before positioning.
        WindowManager.LayoutParams params = mScrollPanel.getLayoutParamsForTesting();
        mScrollPanel.positionPanelAtCursor(cursorX, cursorY);

        // Panel should be left of cursor.
        assertThat(params.x).isLessThan(cursorX);
    }

    @Test
    public void showPanel_nearBottomEdge_positionsAboveCursor() {
        // Near bottom edge case.
        // Center of screen horizontally.
        int cursorX = mScreenWidth / 2;
        // 10px from bottom edge.
        int cursorY = mScreenHeight - 10;

        // Capture the current layout params before positioning.
        WindowManager.LayoutParams params = mScrollPanel.getLayoutParamsForTesting();
        mScrollPanel.positionPanelAtCursor(cursorX, cursorY);

        // Panel should be above cursor.
        assertThat(params.y).isLessThan(cursorY - mScrollPanel.getStatusBarHeightForTesting());
    }

    @Test
    public void showPanel_nearBottomRightCorner_positionsLeftAndAboveCursor() {
        // Near bottom-right corner case.
        // 10px from right edge.
        int cursorX = mScreenWidth - 10;
        // 10px from bottom edge.
        int cursorY = mScreenHeight - 10;

        // Capture the current layout params before positioning.
        WindowManager.LayoutParams params = mScrollPanel.getLayoutParamsForTesting();
        mScrollPanel.positionPanelAtCursor(cursorX, cursorY);

        // Panel should be left of and above cursor.
        assertThat(params.x).isLessThan(cursorX);
        assertThat(params.y).isLessThan(cursorY - mScrollPanel.getStatusBarHeightForTesting());
    }

    @Test
    public void showPanel_closeToEdge_withinBounds() {
        // Test edge case where cursor is very close to edge, panel should still be positioned
        // within PANEL_EDGE_MARGIN (15px).
        int edgeMargin = 15;

        // Near bottom-right corner case.
        // 10px from right edge.
        int cursorX = mScreenWidth - 10;
        // 10px from bottom edge.
        int cursorY = mScreenHeight - 10;

        WindowManager.LayoutParams params = mScrollPanel.getLayoutParamsForTesting();
        mScrollPanel.positionPanelAtCursor(cursorX, cursorY);

        // Verify panel is within bounds with margin.
        assertThat(params.x).isGreaterThan(edgeMargin);
        assertThat(params.y).isGreaterThan(edgeMargin);
        assertThat(params.x + mScrollPanel.getPanelWidthForTesting() + edgeMargin)
                .isLessThan(mScreenWidth);
        assertThat(params.y + mScrollPanel.getPanelHeightForTesting() + edgeMargin)
                .isLessThan(mScreenHeight);
    }

    @Test
    public void exitButton_click_callsOnExitScrollMode() {
        float cursorX = 300;
        float cursorY = 300;

        mScrollPanel.show(cursorX, cursorY);
        assertThat(mScrollPanel.isVisible()).isTrue();

        // Simulate clicking the exit button.
        mExitButton.performClick();

        // Verify that the controller's onExitScrollMode was called.
        verify(mMockScrollPanelController).onExitScrollMode();
    }

    // Helper method to simulate a hover event on a view.
    private void triggerHoverEvent(View view, int action) {
        MotionEvent event = MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 0,
                /* action= */ action,
                /* x= */ 0,
                /* y= */ 0,
                /* metaState= */ 0);

        // Dispatch the event to the view's OnHoverListener.
        view.dispatchGenericMotionEvent(event);
        event.recycle();
    }
}
