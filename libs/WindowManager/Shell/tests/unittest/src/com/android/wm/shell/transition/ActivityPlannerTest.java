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
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.window.TransitionInfo.FLAG_FILLS_TASK;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager.RunningTaskInfo;
import android.content.ComponentName;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.SurfaceControl;
import android.window.ActivityTransitionInfo;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.policy.TransitionAnimation;
import com.android.testing.wm.util.ChangeBuilder;
import com.android.testing.wm.util.StubTransaction;
import com.android.testing.wm.util.TransitionInfoBuilder;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestShellExecutor;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.shared.TransactionPool;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the {@link ActivityPlanner}.
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:ActivityPlannerTest
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ActivityPlannerTest extends ShellTestCase {

    private final DisplayController mDisplayController = mock(DisplayController.class);
    private final DisplayInsetsController mDisplayInsetsController =
            mock(DisplayInsetsController.class);
    private final TransactionPool mTransactionPool = new MockTransactionPool();
    private final TestShellExecutor mMainExecutor = new TestShellExecutor();
    private final TestShellExecutor mAnimExecutor = new TestShellExecutor();

    private ActivityPlanner mPlanner;

    @Before
    public void setUp() {
        TransitionAnimation.initAttributeCache(mContext, new Handler(Looper.getMainLooper()));
        mPlanner = new ActivityPlanner(mContext, mTransactionPool,
                mDisplayController, mDisplayInsetsController, mMainExecutor, mAnimExecutor);
    }

    @After
    public void tearDown() {
        mMainExecutor.flushAll();
    }

    @Test
    public void testPlansActivityClose() {
        final TransitionInfo.Change closeActivity = new ChangeBuilder(TRANSIT_CLOSE)
                .setActivityTransitionInfo(new ActivityTransitionInfo(
                        new ComponentName("pkg", "class"), 1))
                .build();

        final IBinder token = new Binder();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_CLOSE)
                .addChange(closeActivity)
                .build();

        AnimationPlan plan = mock(AnimationPlan.class);
        SurfaceControl.Transaction startT = new StubTransaction();

        mPlanner.plan(plan, info, token, info, startT);

        verify(plan).setAnimation(eq(closeActivity.getContainer()),
                any(ITransitionAnimation.class));
    }

    @Test
    public void testPlansActivityOpen() {

        final TransitionInfo.Change openActivity = new ChangeBuilder(TRANSIT_OPEN)
                .setActivityTransitionInfo(new ActivityTransitionInfo(
                        new ComponentName("pkg", "class"), 1))
                .build();

        final IBinder token = new Binder();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(openActivity)
                .build();

        AnimationPlan plan = mock(AnimationPlan.class);
        SurfaceControl.Transaction startT = new StubTransaction();

        mPlanner.plan(plan, info, token, info, startT);

        verify(plan).setAnimation(eq(openActivity.getContainer()), any(ITransitionAnimation.class));
    }

    @Test
    public void testAppliesEdgeExtensionWhenFillsTask() {
        final TransitionInfo.Change openActivity = new ChangeBuilder(TRANSIT_OPEN)
                .setActivityTransitionInfo(new ActivityTransitionInfo(
                        new ComponentName("pkg", "class"), 1))
                .setFlags(FLAG_FILLS_TASK)
                .build();

        final IBinder token = new Binder();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(openActivity)
                .build();

        AnimationPlan plan = mock(AnimationPlan.class);
        SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);

        mPlanner.plan(plan, info, token, info, startT);

        verify(plan).setAnimation(eq(openActivity.getContainer()), any(ITransitionAnimation.class));
        verify(startT).setEdgeExtensionEffect(eq(openActivity.getLeash()), anyInt());
    }

    @Test
    public void testIgnoresTaskOpen() {
        final TransitionInfo.Change openTask = new ChangeBuilder(createTaskInfo(1), TRANSIT_OPEN)
                .build();

        final IBinder token = new Binder();
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(openTask)
                .build();

        AnimationPlan plan = mock(AnimationPlan.class);
        SurfaceControl.Transaction startT = new StubTransaction();

        mPlanner.plan(plan, info, token, info, startT);

        verify(plan, never()).setAnimation(eq(openTask.getContainer()),
                any(ITransitionAnimation.class));
    }

    private static RunningTaskInfo createTaskInfo(int taskId) {
        RunningTaskInfo taskInfo = new RunningTaskInfo();
        taskInfo.taskId = taskId;
        taskInfo.token = mock(WindowContainerToken.class);
        return taskInfo;
    }
}
