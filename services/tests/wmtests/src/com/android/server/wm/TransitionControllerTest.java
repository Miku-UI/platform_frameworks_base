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
package com.android.server.wm;

import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OPEN;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.server.wm.Transition.PARALLEL_TYPE_RECENTS;
import static com.android.server.wm.TransitionController.getCanBeIndependent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import android.annotation.NonNull;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.view.WindowManager.TransitionType;

import androidx.test.filters.SmallTest;

import com.android.window.flags.Flags;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link TransitionController}.
 *
 * Build/Install/Run:
 * atest WmTests:TransitionControllerTest
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class TransitionControllerTest extends WindowTestsBase {

    private TransitionController mController;

    @Before
    public void setUp() {
        mController = new TransitionController(mAtm);
        mController.setSyncEngine(createTestBLASTSyncEngine());
    }

    @Test
    public void testGetCanBeIndependent_recentsParallelQueued_collectingTaskInSameDisplay_false() {
        final Task task = new ActivityBuilder(mAtm).setCreateTask(true).build().getTask();
        task.mDisplayContent = mDefaultDisplay;
        final Transition collecting = createTestTransition(TRANSIT_CLOSE);
        collecting.mParticipants.add(task);
        final Transition queued = createRecentsParallelTransition(mDefaultDisplay);

        final boolean canBeIndependent = getCanBeIndependent(collecting, queued);

        assertFalse(canBeIndependent);
    }

    @Test
    @DisableFlags(Flags.FLAG_PARALLEL_CD_TRANSITIONS_DURING_RECENTS)
    public void testGetCanBeIndependent_recentsParallelQueued_collectingTaskInOtherDisplay_false() {
        final Task task = new ActivityBuilder(mAtm).setCreateTask(true).build().getTask();
        task.mDisplayContent = mock(DisplayContent.class);
        final Transition collecting = createTestTransition(TRANSIT_CLOSE);
        collecting.mParticipants.add(task);
        final Transition queued = createRecentsParallelTransition(mDefaultDisplay);

        final boolean canBeIndependent = getCanBeIndependent(collecting, queued);

        assertFalse(canBeIndependent);
    }

    @Test
    @EnableFlags(Flags.FLAG_PARALLEL_CD_TRANSITIONS_DURING_RECENTS)
    public void testGetCanBeIndependent_recentsParallelQueued_collectingTaskInOtherDisplay_true() {
        final Transition collecting = createTestTransition(TRANSIT_CLOSE);
        final Task task = new ActivityBuilder(mAtm).setCreateTask(true).build().getTask();
        task.mDisplayContent = mock(DisplayContent.class);
        collecting.mParticipants.add(task);
        final Transition queued = createRecentsParallelTransition(mDefaultDisplay);

        final boolean canBeIndependent = getCanBeIndependent(collecting, queued);

        assertTrue(canBeIndependent);
    }

    private Transition createTestTransition(@TransitionType int type) {
        final Transition transition = new Transition(type, 0 /* flags */, mController,
                mController.mSyncEngine);
        spyOn(transition.mLogger);
        doNothing().when(transition.mLogger).logOnSendAsync(any());
        return transition;
    }

    private Transition createRecentsParallelTransition(@NonNull DisplayContent display) {
        final Transition transition = createTestTransition(TRANSIT_OPEN);
        transition.mParallelCollectType = PARALLEL_TYPE_RECENTS;
        transition.startCollecting(0 /* timeoutMs */);
        final ActivityRecord recent = new ActivityBuilder(mAtm)
                .setCreateTask(true)
                .setVisible(true)
                .build();
        recent.mDisplayContent = display;
        transition.collect(recent);
        return transition;
    }
}
