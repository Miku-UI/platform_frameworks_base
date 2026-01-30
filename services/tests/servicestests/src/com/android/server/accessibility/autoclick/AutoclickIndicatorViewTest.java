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
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.testing.AndroidTestingRunner;
import android.testing.TestableContext;
import android.testing.TestableLooper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Test cases for {@link AutoclickIndicatorView}. */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class AutoclickIndicatorViewTest {

    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Rule public TestableContext mTestableContext =
            new TestableContext(getInstrumentation().getContext());

    @Mock private Canvas mMockCanvas;
    private AutoclickIndicatorView mAutoclickIndicatorView;
    private AutoclickIndicatorView mSpyAutoclickIndicatorView;

    @Before
    public void setUp() {
        mAutoclickIndicatorView = new AutoclickIndicatorView(mTestableContext);
        mSpyAutoclickIndicatorView = spy(mAutoclickIndicatorView);
    }

    @Test
    public void onDraw_showIndicatorTrue_drawsRingAndPoint() {
        mAutoclickIndicatorView.setCoordination(100f, 200f);
        mAutoclickIndicatorView.redrawIndicator();

        mAutoclickIndicatorView.onDraw(mMockCanvas);

        // Verify ring is drawn.
        verify(mMockCanvas).drawArc(
                any(RectF.class), eq(-90f), anyFloat(), eq(false), any(Paint.class));
        // Verify point is drawn (fill and stroke).
        verify(mMockCanvas, times(2)).drawCircle(eq(100f), eq(200f), anyFloat(), any(Paint.class));
    }

    @Test
    public void onDraw_mouseMovesAfterIndicatorShown_pointMovesWithMouse() {
        mAutoclickIndicatorView.setCoordination(100f, 200f);
        mAutoclickIndicatorView.redrawIndicator();
        // After indicator is shown, mouse moves to a new position.
        mAutoclickIndicatorView.setCoordination(300f, 400f);

        mAutoclickIndicatorView.onDraw(mMockCanvas);

        // Verify ring is drawn at the original snapshot position.
        ArgumentCaptor<RectF> rectCaptor = ArgumentCaptor.forClass(RectF.class);
        verify(mMockCanvas)
                .drawArc(rectCaptor.capture(), eq(-90f), anyFloat(), eq(false), any(Paint.class));
        RectF ringRect = rectCaptor.getValue();
        assertThat(ringRect.centerX()).isEqualTo(100f);
        assertThat(ringRect.centerY()).isEqualTo(200f);

        // Verify point is drawn at the new mouse position (fill and stroke).
        verify(mMockCanvas, times(2)).drawCircle(eq(300f), eq(400f), anyFloat(), any(Paint.class));
    }

    @Test
    public void onDraw_ignoreMinorMovementTrue_pointDoesNotMoveWithMouse() {
        mAutoclickIndicatorView.setIgnoreMinorCursorMovement(true);
        mAutoclickIndicatorView.setCoordination(100f, 200f);
        mAutoclickIndicatorView.redrawIndicator();
        // After indicator is shown, mouse moves to a new position.
        mAutoclickIndicatorView.setCoordination(300f, 400f);

        mAutoclickIndicatorView.onDraw(mMockCanvas);

        // Verify ring is drawn at the original snapshot position.
        ArgumentCaptor<RectF> rectCaptor = ArgumentCaptor.forClass(RectF.class);
        verify(mMockCanvas)
                .drawArc(rectCaptor.capture(), eq(-90f), anyFloat(), eq(false), any(Paint.class));
        RectF ringRect = rectCaptor.getValue();
        assertThat(ringRect.centerX()).isEqualTo(100f);
        assertThat(ringRect.centerY()).isEqualTo(200f);

        // Verify point is drawn at the original snapshot position, not the new mouse position.
        verify(mMockCanvas, times(2)).drawCircle(eq(100f), eq(200f), anyFloat(), any(Paint.class));
        verify(mMockCanvas, never()).drawCircle(eq(300f), eq(400f), anyFloat(), any(Paint.class));
    }

    @Test
    public void onDraw_showIndicatorFalse_doesNotDraw() {
        mAutoclickIndicatorView.clearIndicator();

        mAutoclickIndicatorView.onDraw(mMockCanvas);

        verify(mMockCanvas, never()).drawArc(
                any(RectF.class), anyFloat(), anyFloat(), anyBoolean(), any(Paint.class));
        verify(mMockCanvas, never())
                .drawCircle(anyFloat(), anyFloat(), anyFloat(), any(Paint.class));
    }

    @Test
    public void setCoordination_showIndicatorTrue_invalidatesView() {
        mSpyAutoclickIndicatorView.setCoordination(100f, 200f);
        mSpyAutoclickIndicatorView.redrawIndicator();
        clearInvocations(mSpyAutoclickIndicatorView);

        mSpyAutoclickIndicatorView.setCoordination(300f, 400f);

        verify(mSpyAutoclickIndicatorView).invalidate();
    }

    @Test
    public void setCoordination_showIndicatorFalse_doesNotInvalidateView() {
        mSpyAutoclickIndicatorView.setCoordination(100f, 200f);
        mSpyAutoclickIndicatorView.clearIndicator();
        clearInvocations(mSpyAutoclickIndicatorView);

        mSpyAutoclickIndicatorView.setCoordination(300f, 400f);

        verify(mSpyAutoclickIndicatorView, never()).invalidate();
    }

    @Test
    public void setCoordination_sameCoordinates_doesNotInvalidateView() {
        mSpyAutoclickIndicatorView.setCoordination(100f, 200f);
        mSpyAutoclickIndicatorView.redrawIndicator();
        clearInvocations(mSpyAutoclickIndicatorView);

        mSpyAutoclickIndicatorView.setCoordination(100f, 200f);

        verify(mSpyAutoclickIndicatorView, never()).invalidate();
    }

    @Test
    public void redrawIndicator_startsAnimation() {
        mAutoclickIndicatorView.redrawIndicator();

        ValueAnimator animator = mSpyAutoclickIndicatorView.getAnimatorForTesting();
        assertThat(animator.isStarted()).isTrue();
    }

    @Test
    public void redrawIndicator_invalidatesView() {
        mSpyAutoclickIndicatorView.redrawIndicator();
        verify(mSpyAutoclickIndicatorView).invalidate();
    }

    @Test
    public void clearIndicator_cancelsAnimation() {
        mAutoclickIndicatorView.redrawIndicator();
        mAutoclickIndicatorView.clearIndicator();

        ValueAnimator animator = mSpyAutoclickIndicatorView.getAnimatorForTesting();
        assertThat(animator.isStarted()).isFalse();
    }

    @Test
    public void clearIndicator_invalidatesView() {
        mSpyAutoclickIndicatorView.redrawIndicator();
        clearInvocations(mSpyAutoclickIndicatorView);

        mSpyAutoclickIndicatorView.clearIndicator();

        verify(mSpyAutoclickIndicatorView).invalidate();
    }

    @Test
    public void setAnimationDuration_updatesAnimatorDuration() {
        int testDuration = 1000;
        mAutoclickIndicatorView.setAnimationDuration(testDuration);
        ValueAnimator animator = mSpyAutoclickIndicatorView.getAnimatorForTesting();
        assertThat(animator.getDuration()).isEqualTo(testDuration);
    }

    @Test
    public void setAnimationDuration_withValueLessThanMinimal_setsToMinimal() {
        int testDuration = AutoclickIndicatorView.MINIMAL_ANIMATION_DURATION - 10;
        mAutoclickIndicatorView.setAnimationDuration(testDuration);
        ValueAnimator animator = mSpyAutoclickIndicatorView.getAnimatorForTesting();
        assertThat(animator.getDuration()).isEqualTo(
                AutoclickIndicatorView.MINIMAL_ANIMATION_DURATION);
    }

    @Test
    public void setRadius_updatesRadius() {
        int testRadius = 100;
        mAutoclickIndicatorView.setRadius(testRadius);
        assertThat(mAutoclickIndicatorView.getRadiusForTesting()).isEqualTo(testRadius);
    }
}
