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

import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_FLAG_IS_RECENTS;
import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_APPEARING;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.animation.ValueAnimator;
import android.util.ArrayMap;
import android.view.SurfaceControl;
import android.view.animation.Animation;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.testing.wm.util.ChangeBuilder;
import com.android.testing.wm.util.TransitionInfoBuilder;
import com.android.wm.shell.ShellTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link MergeTransitionHelper}.
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:MergeTransitionHelperTest
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class MergeTransitionHelperTest extends ShellTestCase {

    @Test
    public void testGetMergeConflicts_findsMatches() {
        WindowContainerToken token1 = mock(WindowContainerToken.class);
        WindowContainerToken token2 = mock(WindowContainerToken.class);

        TransitionInfo.Change change1 = new ChangeBuilder(token1, TRANSIT_OPEN).build();
        WindowAnimation anim1 = new WindowAnimation(change1, 0f, mock(Animation.class),
                mock(ValueAnimator.class));

        TransitionInfo.Change change2 = new ChangeBuilder(token2, TRANSIT_OPEN).build();
        WindowAnimation anim2 = new WindowAnimation(change2, 0f, mock(Animation.class),
                mock(ValueAnimator.class));

        List<WindowAnimation> runningAnims = new ArrayList<>();
        runningAnims.add(anim1);
        runningAnims.add(anim2);

        TransitionInfo incomingInfo = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(new ChangeBuilder(token1, TRANSIT_CLOSE).build())
                .build();

        ArrayMap<WindowAnimation, TransitionInfo.Change> conflicts =
                MergeTransitionHelper.getMergeConflicts(runningAnims, incomingInfo);

        assertEquals(1, conflicts.size());
        assertTrue(conflicts.containsKey(anim1));
        assertFalse(conflicts.containsKey(anim2));
    }

    @Test
    public void testCanCreateMergeAnimation_allowedTypesAndFlags() {
        assertTrue(MergeTransitionHelper.canCreateMergeAnimation(
                new TransitionInfoBuilder(TRANSIT_OPEN).build()));
        assertTrue(MergeTransitionHelper.canCreateMergeAnimation(
                new TransitionInfoBuilder(TRANSIT_CLOSE).build()));
        assertTrue(MergeTransitionHelper.canCreateMergeAnimation(
                new TransitionInfoBuilder(TRANSIT_TO_FRONT).build()));
        assertTrue(MergeTransitionHelper.canCreateMergeAnimation(
                new TransitionInfoBuilder(TRANSIT_TO_BACK).build()));

        assertFalse(MergeTransitionHelper.canCreateMergeAnimation(
                new TransitionInfoBuilder(TRANSIT_OPEN, TRANSIT_FLAG_KEYGUARD_APPEARING).build()));

        assertFalse(MergeTransitionHelper.canCreateMergeAnimation(
                new TransitionInfoBuilder(TRANSIT_OPEN, TRANSIT_FLAG_IS_RECENTS)
                        .build()));
    }

    @Test
    public void testArrangeStartTransactionLayers_setCorrectLayersAndVisibility() {
        final SurfaceControl openLeash = mock(SurfaceControl.class);
        final SurfaceControl closeLeash = mock(SurfaceControl.class);
        final SurfaceControl rootLeash = mock(SurfaceControl.class);

        final var openChange = new ChangeBuilder(TRANSIT_TO_FRONT, openLeash)
                .setDisplayId(1).build();
        final var closeChange = new ChangeBuilder(TRANSIT_TO_BACK, closeLeash)
                .setDisplayId(1).build();

        final var incomingInfo = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addRoot(rootLeash, 1)
                .addChange(openChange)
                .addChange(closeChange)
                .build();

        final var conflictingChanges = new ArrayList<TransitionInfo.Change>();
        conflictingChanges.add(openChange);
        conflictingChanges.add(closeChange);

        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);

        MergeTransitionHelper.arrangeStartTransactionLayers(incomingInfo, conflictingChanges,
                startT);

        // Verify root is shown
        verify(startT).show(rootLeash);

        // Verify open change is reparented, layered and shown
        verify(startT).reparent(openLeash, rootLeash);
        verify(startT).setLayer(eq(openLeash), anyInt());
        verify(startT).show(openLeash);

        // Verify conflicting close change is NOT reparented and left in its original root
        verify(startT, never()).reparent(eq(closeLeash), any());
        verify(startT, never()).setLayer(eq(closeLeash), anyInt());
        verify(startT, never()).show(closeLeash);
    }
}
