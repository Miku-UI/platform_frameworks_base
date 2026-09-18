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

package com.android.wm.shell.transition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Matrix;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.window.TransitionInfo;
import android.window.WindowAnimationState;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.testing.wm.util.ChangeBuilder;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestShellExecutor;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.List;
import java.util.function.Consumer;

/**
 * Tests for  the container tracking object for window animations.
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:WindowAnimationTest
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class WindowAnimationTest extends ShellTestCase {

    private final TransitionInfo.Change mChange = createChange();
    @Mock
    private ValueAnimator mAnimator;
    @Mock
    private Animation mAnimation;
    @Mock
    private Animator mSiblingAnimator;
    @Mock
    private Animator mSiblingAnimator2;
    @Mock
    private Consumer<WindowAnimation> mFinishCallback;
    private final TestShellExecutor mTestShellExecutor = new TestShellExecutor();

    private static TransitionInfo.Change createChange() {
        final TransitionInfo.Change change = new ChangeBuilder(
                WindowManager.TRANSIT_CHANGE).build();
        change.getStartAbsBounds().set(0, 0, 50, 50);
        change.getEndAbsBounds().set(50, 50, 100, 100);
        return change;
    }

    @Test
    public void testWindowAnimation_startEnd_delegatesToAnimator() {
        final WindowAnimation anim = new WindowAnimation(mChange, 0f, mAnimation, mAnimator);

        anim.start();
        verify(mAnimator).start();

        anim.end();
        verify(mAnimator).end();
    }

    @Test
    public void testWindowAnimation_getState_calculatesBoundsAndScale() {
        final WindowAnimation anim = new WindowAnimation(mChange, 10f, mAnimation, mAnimator);
        doAnswer(invocation -> {
            Transformation t = invocation.getArgument(1);
            Matrix matrix = t.getMatrix();
            matrix.postScale(2f, 2f);
            matrix.postTranslate(100f, 200f);
            return true;
        }).when(mAnimation).getTransformation(anyLong(), any(Transformation.class));
        when(mAnimator.getCurrentPlayTime()).thenReturn(500L);
        when(mAnimator.getDuration()).thenReturn(1000L);

        final WindowAnimationState state = anim.getWindowAnimationState();

        assertNotNull(state);
        assertEquals(100f, state.bounds.left, 0.01f);
        assertEquals(200f, state.bounds.top, 0.01f);
        assertEquals(2f, state.scale, 0.01f);
        assertEquals(10f, state.bottomLeftRadius, 0.01f);
    }

    @Test
    public void testWindowAnimation_getState_calculatesVelocity() {
        final WindowAnimation anim = new WindowAnimation(mChange, 10f, mAnimation, mAnimator);
        when(mAnimator.getCurrentPlayTime()).thenReturn(500L);
        when(mAnimator.getDuration()).thenReturn(1000L);

        // deltaTime = 0.1 * 500 = 50
        // tStart = 500 - 0.3 * 50 = 485
        // tEnd = 500 + 0.7 * 50 = 535
        // effectiveDeltaTime = 50
        doAnswer(invocation -> {
            long time = invocation.getArgument(0);
            Transformation t = invocation.getArgument(1);
            Matrix matrix = t.getMatrix();
            if (time == 535L) {
                matrix.postTranslate(100f, 200f);
            } else if (time == 485L) {
                matrix.postTranslate(50f, 100f);
            }
            return true;
        }).when(mAnimation).getTransformation(anyLong(), any(Transformation.class));

        final WindowAnimationState state = anim.getWindowAnimationState();

        assertNotNull(state);
        // velocityX = (100 - 50) / 50 = 1
        // velocityY = (200 - 100) / 50 = 2
        assertEquals(1f, state.velocityPxPerMs.x, 0.01f);
        assertEquals(2f, state.velocityPxPerMs.y, 0.01f);
    }

    @Test
    public void testWindowAnimation_getState_zeroVelocityBeforeThreshold() {
        final WindowAnimation anim = new WindowAnimation(mChange, 10f, mAnimation, mAnimator);
        when(mAnimator.getCurrentPlayTime()).thenReturn(5L); // Below 10ms threshold
        when(mAnimator.getDuration()).thenReturn(1000L);

        final WindowAnimationState state = anim.getWindowAnimationState();

        assertNotNull(state);
        assertEquals(0f, state.velocityPxPerMs.x, 0.01f);
        assertEquals(0f, state.velocityPxPerMs.y, 0.01f);
    }

    @Test
    public void testWindowAnimation_getState_zeroVelocityAfterThreshold() {
        final WindowAnimation anim = new WindowAnimation(mChange, 10f, mAnimation, mAnimator);
        when(mAnimator.getCurrentPlayTime()).thenReturn(9995L); // Below 10ms threshold
        when(mAnimator.getDuration()).thenReturn(1000L);

        final WindowAnimationState state = anim.getWindowAnimationState();

        assertNotNull(state);
        assertEquals(0f, state.velocityPxPerMs.x, 0.01f);
        assertEquals(0f, state.velocityPxPerMs.y, 0.01f);
    }

    @Test
    public void testMultiPartWindowAnimation_runsAllAnimators() {
        final WindowAnimation mainAnim = new WindowAnimation(mChange, 0f, mAnimation, mAnimator);
        final var siblings = List.of(mSiblingAnimator);

        final var multiAnim = new MultiPartWindowAnimation(mainAnim, siblings);
        multiAnim.addFinishCallback(mFinishCallback, mTestShellExecutor);

        multiAnim.start();

        verify(mAnimator).start();
        verify(mSiblingAnimator).start();

        multiAnim.end();

        verify(mAnimator).end();
        verify(mSiblingAnimator).end();
    }

    @Test
    public void testMultiPartWindowAnimation_callbackOnlyWhenAllFinished() {
        final WindowAnimation mainAnim = new WindowAnimation(mChange, 0f, mAnimation, mAnimator);
        final var siblings = List.of(mSiblingAnimator);

        final var multiAnim = new MultiPartWindowAnimation(mainAnim, siblings);
        multiAnim.addFinishCallback(mFinishCallback, mTestShellExecutor);

        final var mainListenerCaptor = ArgumentCaptor.forClass(
                AnimatorListenerAdapter.class);
        verify(mAnimator).addListener(mainListenerCaptor.capture());

        final var siblingListenerCaptor = ArgumentCaptor.forClass(
                AnimatorListenerAdapter.class);
        verify(mSiblingAnimator).addListener(siblingListenerCaptor.capture());

        mainListenerCaptor.getValue().onAnimationEnd(mAnimator);
        mTestShellExecutor.flushAll();
        verify(mFinishCallback, never()).accept(any());

        siblingListenerCaptor.getValue().onAnimationEnd(mSiblingAnimator);
        mTestShellExecutor.flushAll();
        verify(mFinishCallback, times(1)).accept(multiAnim);
    }

    @Test
    public void testMultiPartWindowAnimation_end_multipleSiblings_someFinished() {
        final WindowAnimation mainAnim = new WindowAnimation(mChange, 0f, mAnimation, mAnimator);
        final var siblings = List.of(mSiblingAnimator, mSiblingAnimator2);

        final var multiAnim = new MultiPartWindowAnimation(mainAnim, siblings);
        multiAnim.addFinishCallback(mFinishCallback, mTestShellExecutor);

        final var mainListenerCaptor = ArgumentCaptor.forClass(
                AnimatorListenerAdapter.class);
        verify(mAnimator).addListener(mainListenerCaptor.capture());

        final var sibling1ListenerCaptor = ArgumentCaptor.forClass(
                AnimatorListenerAdapter.class);
        verify(mSiblingAnimator).addListener(sibling1ListenerCaptor.capture());

        final var sibling2ListenerCaptor = ArgumentCaptor.forClass(
                AnimatorListenerAdapter.class);
        verify(mSiblingAnimator2).addListener(sibling2ListenerCaptor.capture());

        sibling1ListenerCaptor.getValue().onAnimationEnd(mSiblingAnimator);
        mTestShellExecutor.flushAll();
        verify(mFinishCallback, never()).accept(any());

        multiAnim.end();

        verify(mAnimator).end();
        verify(mSiblingAnimator2).end();
        verify(mSiblingAnimator, never()).end(); // Sibling 1 was already finished

        mainListenerCaptor.getValue().onAnimationEnd(mAnimator);
        mTestShellExecutor.flushAll();
        verify(mFinishCallback, never()).accept(any()); // Still waiting for Sibling 2

        sibling2ListenerCaptor.getValue().onAnimationEnd(mSiblingAnimator2);
        mTestShellExecutor.flushAll();
        verify(mFinishCallback, times(1)).accept(multiAnim);
    }
}
