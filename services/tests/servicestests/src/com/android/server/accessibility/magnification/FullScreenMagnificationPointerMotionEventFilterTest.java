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

package com.android.server.accessibility.magnification;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import android.graphics.Rect;
import android.provider.Settings;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class FullScreenMagnificationPointerMotionEventFilterTest {
    @Mock
    private FullScreenMagnificationController mMockFullScreenMagnificationController;

    private FullScreenMagnificationPointerMotionEventFilter mFilter;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mFilter = new FullScreenMagnificationPointerMotionEventFilter(
                mMockFullScreenMagnificationController);
    }

    void stubMockFullScreenMagnificationController(boolean activated, float scale, Rect bounds,
            boolean centerOrBottomRight) {
        doAnswer(invocation -> {
            FullScreenMagnificationController.FullScreenMagnificationData outMagnificationData =
                    invocation.getArgument(1);
            outMagnificationData.setActivated(activated);
            outMagnificationData.setScale(scale);
            outMagnificationData.setBounds(bounds);
            outMagnificationData.setOffsetX(centerOrBottomRight
                    ? -bounds.centerX() * (scale - 1.f)
                    : -(bounds.right * scale - bounds.width()));
            outMagnificationData.setMinOffsetX(-bounds.right * (scale - 1.f));
            outMagnificationData.setMaxOffsetX(-bounds.left * (scale - 1.f));
            outMagnificationData.setOffsetY(centerOrBottomRight
                    ? -bounds.centerY() * (scale - 1.f)
                    : -(bounds.bottom * scale - bounds.height()));
            outMagnificationData.setMinOffsetY(-bounds.bottom * (scale - 1.f));
            outMagnificationData.setMaxOffsetY(-bounds.top * (scale - 1.f));
            return null;
        }).when(mMockFullScreenMagnificationController).getFullScreenMagnificationData(anyInt(),
                any(FullScreenMagnificationController.FullScreenMagnificationData.class));
    }

    @Test
    public void inactiveDisplay_doNothing() {
        stubMockFullScreenMagnificationController(/* activated= */ false, /* scale= */ 3.0f,
                /* bounds= */ new Rect(0, 0, 800, 600), /* centerOrBottomRight= */ true);

        float[] delta = new float[]{1.f, 2.f};
        float[] result = mFilter.filterPointerMotionEvent(delta[0], delta[1], 3.0f, 4.0f, 0);
        assertThat(result).isEqualTo(delta);
    }

    @Test
    public void continuousMode() {
        mFilter.setMode(
                Settings.Secure.ACCESSIBILITY_MAGNIFICATION_CURSOR_FOLLOWING_MODE_CONTINUOUS);

        final float scale = 3.f;
        stubMockFullScreenMagnificationController(/* activated= */ true, scale,
                /* bounds= */ new Rect(0, 0, 800, 600), /* centerOrBottomRight= */ true);

        // At the first cursor move, it goes to (20, 30) + (5, 10) = (25, 40). The scale is 3.0.
        // The expected viewport offset is (-25 * (3-1), -40 * (3-1)) = (-50, -80). The cursor
        // movement for the physical display is kept the same.
        float[] currentXy =  new float[]{20.f, 30.f};
        float[] deltaXy = new float[]{5.f, 10.f};
        float[] adjustedDeltaXy = mFilter.filterPointerMotionEvent(deltaXy[0], deltaXy[1],
                currentXy[0], currentXy[1], 0);
        currentXy[0] += adjustedDeltaXy[0];
        currentXy[1] += adjustedDeltaXy[1];
        assertThat(adjustedDeltaXy).isEqualTo(deltaXy);
        assertThat(currentXy).isEqualTo(new float[]{25.f, 40.f});
        verify(mMockFullScreenMagnificationController)
                .setOffset(eq(0), eq(-50.f), eq(-80.f), anyInt());

        // At the second cursor move, it goes to (25, 40) + (10, 5) = (35, 45). The scale is 3.0.
        // The expected viewport offset is (-35 * (3-1), -45 * (3-1)) = (-70, -90). The cursor
        // movement for the physical display is kept the same.
        deltaXy = new float[]{10.f, 5.f};
        adjustedDeltaXy = mFilter.filterPointerMotionEvent(deltaXy[0], deltaXy[1], currentXy[0],
                currentXy[1], 0);
        currentXy[0] += adjustedDeltaXy[0];
        currentXy[1] += adjustedDeltaXy[1];
        assertThat(adjustedDeltaXy).isEqualTo(deltaXy);
        assertThat(currentXy).isEqualTo(new float[]{35.f, 45.f});
        verify(mMockFullScreenMagnificationController)
                .setOffset(eq(0), eq(-70.f), eq(-90.f), anyInt());
    }

    @Test
    public void centerMode_viewportNotAtEdge_viewportMovesButCursorNotMove() {
        mFilter.setMode(Settings.Secure.ACCESSIBILITY_MAGNIFICATION_CURSOR_FOLLOWING_MODE_CENTER);

        // The viewport is at the center of the magnified content.
        final float scale = 3.f;
        final Rect bounds = new Rect(0, 0, 800, 600);
        stubMockFullScreenMagnificationController(/* activated= */ true, scale, bounds,
                /* centerOrBottomRight= */ true);

        // The cursor stays the same. The viewport updates its position. The cursor is at the center
        // of the physical display, (400.f, 300.f).
        float[] currentXy =  new float[]{bounds.centerX(), bounds.centerY()};
        float[] deltaXy = new float[]{5.f, 10.f};
        float[] adjustedDeltaXy = mFilter.filterPointerMotionEvent(deltaXy[0], deltaXy[1],
                currentXy[0], currentXy[1], 0);
        currentXy[0] += adjustedDeltaXy[0];
        currentXy[1] += adjustedDeltaXy[1];
        assertThat(adjustedDeltaXy).isEqualTo(new float[]{0.f, 0.f});
        assertThat(currentXy).isEqualTo(new float[]{400.f, 300.f});
        verify(mMockFullScreenMagnificationController)
                .offsetMagnifiedRegion(eq(0), eq(15.f), eq(30.f), anyInt());
    }

    @Test
    public void centerMode_viewportAtEdge_cursorMovesButViewportNotMove() {
        mFilter.setMode(Settings.Secure.ACCESSIBILITY_MAGNIFICATION_CURSOR_FOLLOWING_MODE_CENTER);

        // The viewport is at the bottom right corner of the magnified
        // content.
        final float scale = 3.f;
        final Rect bounds = new Rect(0, 0, 800, 600);
        stubMockFullScreenMagnificationController(/* activated= */ true, scale, bounds,
                /* centerOrBottomRight= */ false);

        // The cursor updates its position. The viewport stays the same. The cursor is at the center
        // of the physical display, (400.f, 300.f).
        float[] currentXy =  new float[]{bounds.centerX(), bounds.centerY()};
        float[] deltaXy = new float[]{5.f, 10.f};
        float[] adjustedDeltaXy = mFilter.filterPointerMotionEvent(deltaXy[0], deltaXy[1],
                currentXy[0], currentXy[1], 0);
        currentXy[0] += adjustedDeltaXy[0];
        currentXy[1] += adjustedDeltaXy[1];
        assertThat(adjustedDeltaXy).isEqualTo(new float[]{deltaXy[0], deltaXy[1]});
        // The new cursor position: {400.f + 5.f, 300.f + 10.f}.
        assertThat(currentXy).isEqualTo(new float[]{405.f, 310.f});
        verify(mMockFullScreenMagnificationController)
                .offsetMagnifiedRegion(eq(0), eq(0.f), eq(0.f), anyInt());
    }

    @Test
    public void edgeMode_cursorNotAtEdge_cursorMovesButViewportNotMove() {
        mFilter.setMode(Settings.Secure.ACCESSIBILITY_MAGNIFICATION_CURSOR_FOLLOWING_MODE_EDGE);

        // The viewport is at the center of the magnified content.
        final float scale = 3.f;
        final Rect bounds = new Rect(0, 0, 800, 600);
        stubMockFullScreenMagnificationController(/* activated= */ true, scale, bounds,
                /* centerOrBottomRight= */ true);

        // The cursor updates its position. The viewport stays the same. The cursor is at the center
        // of the physical display, (400.f, 300.f).
        float[] currentXy =  new float[]{bounds.centerX(), bounds.centerY()};
        float[] deltaXy = new float[]{5.f, 10.f};
        float[] adjustedDeltaXy = mFilter.filterPointerMotionEvent(deltaXy[0], deltaXy[1],
                currentXy[0], currentXy[1], 0);
        currentXy[0] += adjustedDeltaXy[0];
        currentXy[1] += adjustedDeltaXy[1];
        assertThat(adjustedDeltaXy).isEqualTo(new float[]{deltaXy[0], deltaXy[1]});
        // The new cursor position: {400.f + 5.f, 300.f + 10.f}.
        assertThat(currentXy).isEqualTo(new float[]{405.f, 310.f});
        verify(mMockFullScreenMagnificationController)
                .offsetMagnifiedRegion(eq(0), eq(0.f), eq(0.f), anyInt());
    }

    @Test
    public void edgeMode_cursorAtEdge_cursorNotMoveButViewportMoves() {
        mFilter.setMode(Settings.Secure.ACCESSIBILITY_MAGNIFICATION_CURSOR_FOLLOWING_MODE_EDGE);

        // The viewport is at the center of the magnified content.
        final float scale = 3.f;
        final Rect bounds = new Rect(0, 0, 800, 600);
        stubMockFullScreenMagnificationController(/* activated= */ true, scale, bounds,
                /* centerOrBottomRight= */ true);

        // The cursor stays the same. The viewport updates its position. The cursor is at the edge
        // of the physical display, (800.f - 100.f, 600.f - 100.f).
        final float margin = FullScreenMagnificationPointerMotionEventFilter.EDGE_MODE_MARGIN_PX;
        float[] currentXy =  new float[]{bounds.right - margin, bounds.bottom - margin};
        float[] deltaXy = new float[]{5.f, 10.f};
        float[] adjustedDeltaXy = mFilter.filterPointerMotionEvent(deltaXy[0], deltaXy[1],
                currentXy[0], currentXy[1], 0);
        currentXy[0] += adjustedDeltaXy[0];
        currentXy[1] += adjustedDeltaXy[1];
        assertThat(adjustedDeltaXy).isEqualTo(new float[]{0.f, 0.f});
        // The new cursor position: {800.f - 100.f + 0.f, 600.f - 100.f + 0.f}.
        assertThat(currentXy).isEqualTo(new float[]{700.f, 500.f});
        verify(mMockFullScreenMagnificationController)
                .offsetMagnifiedRegion(eq(0), eq(15.f), eq(30.f), anyInt());
    }
}
