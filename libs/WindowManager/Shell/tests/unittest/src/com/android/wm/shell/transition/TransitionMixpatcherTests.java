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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import android.annotation.NonNull;
import android.graphics.RectF;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.WindowAnimationState;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.testing.wm.util.ChangeBuilder;
import com.android.testing.wm.util.StubTransaction;
import com.android.testing.wm.util.TransitionInfoBuilder;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestShellExecutor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for transition mixpatcher
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:TransitionMixpatcherTests
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class TransitionMixpatcherTests extends ShellTestCase {
    private final ShellTaskOrganizer mOrganizer = mock(ShellTaskOrganizer.class);
    private final TestShellExecutor mMainExecutor = new TestShellExecutor();

    private final TransitionMixpatcher mMixpatcher =
            new TransitionMixpatcher(mOrganizer, mMainExecutor);
    private final ArrayList<IBinder> mWmFinishedTransitions = new ArrayList<>();
    private final StubTransaction mStubTx = new StubTransaction();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Before
    public void setUp() {
        doAnswer(invocation -> new Binder())
                .when(mOrganizer).startNewTransition(anyInt(), any());
        doAnswer(invocation -> {
            mWmFinishedTransitions.add(invocation.getArgument(0));
            return null;
        }).when(mOrganizer).finishTransition(any(), any());
        mMainExecutor.mIsCurrentThread = true;
    }

    @Test
    public void basicLifecycle() {
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        final TestTransitAnim testAnim = new TestTransitAnim(mMainHandler);
        final TestPlanner d = new TestPlanner(mMainHandler);
        mMixpatcher.mPlanners.add(d);
        boolean[] mRanOnIdle = new boolean[]{false};

        // If already idle, then run-on-idle should happen synchronously
        mMixpatcher.runOnIdle(() -> mRanOnIdle[0] = true);
        assertTrue(mRanOnIdle[0]);
        mRanOnIdle[0] = false;

        final IBinder transit = mMixpatcher.startTransition(null /* transit */, TRANSIT_OPEN, wct,
                null /* interest*/);

        // Once a transition is in the system, it is no-longer idle
        mMixpatcher.runOnIdle(() -> mRanOnIdle[0] = true);
        assertFalse(mRanOnIdle[0]);

        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(new ChangeBuilder(createTestContainer("Open"), TRANSIT_OPEN).build())
                .addChange(new ChangeBuilder(createTestContainer("Close"), TRANSIT_CLOSE).build())
                .build();
        d.mAcceptAll = testAnim;
        mMixpatcher.onTransitionReady(transit, info, mStubTx, mStubTx);

        // Animating now
        assertEquals(2, testAnim.mContainersAnimating.size());
        assertEquals(0, mWmFinishedTransitions.size());

        testAnim.finishNow();
        assertEquals(1, mWmFinishedTransitions.size());
        assertTrue(mRanOnIdle[0]);
    }

    @Test
    public void planning() {
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        final WindowContainerToken ctnrA = createTestContainer("CtnrA");
        final WindowContainerToken ctnrB = createTestContainer("CtnrB");
        final WindowContainerToken ctnrC = createTestContainer("CtnrC");
        final WindowContainerToken ctnrD = createTestContainer("CtnrD");
        final TestTransitAnim anim1 = new TestTransitAnim(mMainHandler);
        final TestTransitAnim anim2 = new TestTransitAnim(mMainHandler);
        final TestTransitAnim anim3 = new TestTransitAnim(mMainHandler);
        final TestTransitAnim anim4 = new TestTransitAnim(mMainHandler);
        final TestPlanner d = new TestPlanner(mMainHandler);
        final TestPlanner d2 = new TestPlanner(mMainHandler);
        final TestPlanner d3 = new TestPlanner(mMainHandler);
        final TestPlanner dfloat = new TestPlanner(mMainHandler);
        // Adding reverse so that d is checked first.
        mMixpatcher.mPlanners.add(d3);
        mMixpatcher.mPlanners.add(d2);
        mMixpatcher.mPlanners.add(d);

        final ArrayList<ITransitionPlanner> interest = new ArrayList<>();
        interest.add(dfloat);
        interest.add(d2);
        final IBinder transit = mMixpatcher.startTransition(null /* transit */, TRANSIT_OPEN, wct,
                interest);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(new ChangeBuilder(ctnrA, TRANSIT_OPEN).build())
                .addChange(new ChangeBuilder(ctnrB, TRANSIT_OPEN).build())
                .addChange(new ChangeBuilder(ctnrC, TRANSIT_OPEN).build())
                .addChange(new ChangeBuilder(ctnrD, TRANSIT_OPEN).build())
                .build();
        dfloat.mAccept = new WindowContainerToken[]{ctnrD};
        dfloat.mAcceptAnims = new ITransitionAnimation[]{anim3};
        d2.mAccept = new WindowContainerToken[]{ctnrC, ctnrD};
        d2.mAcceptAnims = new ITransitionAnimation[]{anim2, anim2};
        d3.mAccept = new WindowContainerToken[]{ctnrC, ctnrD};
        d3.mAcceptAnims = new ITransitionAnimation[]{anim4, anim4};
        d.mAcceptAll = anim1;
        mMixpatcher.onTransitionReady(transit, info, mStubTx, mStubTx);
        // interest takes priority and are ordered.
        // claimed containers are made unavailable to subsequent planners
        assertEquals(1, anim3.mInfo.getChanges().size());
        assertEquals(1, anim2.mInfo.getChanges().size());
        assertNull(anim4.mInfo);
        assertEquals(2, anim1.mInfo.getChanges().size());
        // if a planner is in interest set, it doesn't get called a second time
        assertEquals(1, d2.mCallCount);
    }

    @Test
    public void queueingDispatch() {
        final WindowContainerToken opening = createTestContainer("Open");
        final TestTransitAnim testAnim = new TestTransitAnim(mMainHandler);
        final TestTransitAnim testAnim2 = new TestTransitAnim(mMainHandler);
        final TestTransitAnim testAnim3 = new TestTransitAnim(mMainHandler);
        final TestPlanner d = new TestPlanner(mMainHandler);
        d.mAcceptAll = testAnim;
        d.mDetachAsync = true;
        d.mDetach = new WindowContainerToken[]{opening};
        mMixpatcher.mPlanners.add(d);

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        final IBinder transit = mMixpatcher.startTransition(null /* transit */, TRANSIT_OPEN, wct,
                null /* interest*/);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(new ChangeBuilder(opening, TRANSIT_OPEN).build())
                .addChange(new ChangeBuilder(createTestContainer("Close"), TRANSIT_CLOSE).build())
                .build();
        mMixpatcher.onTransitionReady(transit, info, mStubTx, mStubTx);
        assertNull(testAnim.mInfo); // Not started yet

        // start another transition (with async detach)
        final IBinder transit2 = mMixpatcher.startTransition(null /* transit */, TRANSIT_CLOSE, wct,
                null /* interest*/);
        final TransitionInfo info2 = new TransitionInfoBuilder(TRANSIT_CLOSE)
                .addChange(new ChangeBuilder(createTestContainer("Close2"), TRANSIT_CLOSE).build())
                .build();
        d.mAcceptAll = testAnim2;
        mMixpatcher.onTransitionReady(transit2, info2, mStubTx, mStubTx);
        // Should not be planned yet (since ongoing transfer)
        assertEquals(1, d.mCallCount);

        d.finishDetachNow();
        // Animation can start once detach is complete
        assertEquals(2, testAnim.mInfo.getChanges().size());
        // Next (queued) planning should have run
        assertEquals(2, d.mCallCount);
        // Next animation should start now too (since no async detach)
        assertEquals(1, testAnim2.mInfo.getChanges().size());

        final IBinder transit3 = mMixpatcher.startTransition(null /* transit */, TRANSIT_CLOSE, wct,
                null /* interest*/);
        final TransitionInfo info3 = new TransitionInfoBuilder(TRANSIT_CLOSE)
                .addChange(new ChangeBuilder(createTestContainer("Close3"), TRANSIT_CLOSE).build())
                .build();
        d.mAcceptAll = testAnim3;
        mMixpatcher.onTransitionReady(transit3, info3, mStubTx, mStubTx);
        // No async, so we should be back to straight-through planning
        assertEquals(3, d.mCallCount);
        assertEquals(1, testAnim3.mInfo.getChanges().size());
    }

    @Test
    public void animationLifecycle() {
        final TestTransitAnim testAnim = new TestTransitAnim(mMainHandler);
        final TestTransitAnim testAnim2 = new TestTransitAnim(mMainHandler);
        final TestTransitAnim testAnim3 = new TestTransitAnim(mMainHandler);
        final TestPlanner d = new TestPlanner(mMainHandler);
        mMixpatcher.mPlanners.add(d);

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        final IBinder transit = mMixpatcher.startTransition(null /* transit */, TRANSIT_OPEN, wct,
                null /* interest*/);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(new ChangeBuilder(createTestContainer("Open"), TRANSIT_OPEN).build())
                .addChange(new ChangeBuilder(createTestContainer("Close"), TRANSIT_CLOSE).build())
                .build();
        d.mAccept = new WindowContainerToken[]{info.getChanges().get(0).getContainer(),
                info.getChanges().get(1).getContainer()};
        d.mAcceptAnims = new ITransitionAnimation[]{testAnim, testAnim2};
        mMixpatcher.onTransitionReady(transit, info, mStubTx, mStubTx);
        assertEquals(1, testAnim.mContainersAnimating.size());
        assertEquals(1, testAnim2.mContainersAnimating.size());

        // Start another animation
        final TransitionInfo info2 = new TransitionInfoBuilder(TRANSIT_CLOSE)
                .addChange(new ChangeBuilder(createTestContainer("Close2"), TRANSIT_CLOSE).build())
                .build();
        d.mAcceptAll = testAnim3;
        final IBinder transit2 = mMixpatcher.startTransition(null /* transit */, TRANSIT_OPEN, wct,
                null /* interest*/);
        mMixpatcher.onTransitionReady(transit2, info2, mStubTx, mStubTx);
        assertEquals(1, testAnim3.mContainersAnimating.size());

        // finish anim2 (anim1 should still be running, nothing finished yet)
        testAnim2.finishNow();
        assertEquals(1, testAnim.mContainersAnimating.size());
        assertEquals(1, testAnim3.mContainersAnimating.size());
        assertEquals(0, mWmFinishedTransitions.size());

        // finish anim3 (this finishes transit2, but since it came after transit1 which is still
        //               animating, there should be no finishes reported to WM yet
        testAnim3.finishNow();
        assertEquals(1, testAnim.mContainersAnimating.size());
        assertEquals(0, mWmFinishedTransitions.size());

        // finish anim1 (now everything is done, finishes should be submitted in correct order
        testAnim.finishNow();
        assertEquals(transit, mWmFinishedTransitions.get(0));
        assertEquals(transit2, mWmFinishedTransitions.get(1));
    }

    @Test
    public void detaching() {
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        final WindowContainerToken ctnrA = createTestContainer("CtnrA");
        final WindowContainerToken ctnrB = createTestContainer("CtnrB");
        final WindowContainerToken ctnrC = createTestContainer("CtnrC");
        final WindowContainerToken ctnrD = createTestContainer("CtnrD");
        final TestTransitAnim anim1 = new TestTransitAnim(mMainHandler);
        final TestTransitAnim anim2 = new TestTransitAnim(mMainHandler);
        final TestTransitAnim anim3 = new TestTransitAnim(mMainHandler);
        final TestTransitAnim anim4 = new TestTransitAnim(mMainHandler);
        final TestPlanner d = new TestPlanner(mMainHandler);
        final TestPlanner d2 = new TestPlanner(mMainHandler);
        // Adding reverse so that d is checked first.
        mMixpatcher.mPlanners.add(d2);
        mMixpatcher.mPlanners.add(d);

        final IBinder transit = mMixpatcher.startTransition(null /* transit */, TRANSIT_OPEN, wct,
                null /* interest*/);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(new ChangeBuilder(ctnrA, TRANSIT_OPEN).build())
                .addChange(new ChangeBuilder(ctnrB, TRANSIT_CLOSE).build())
                .addChange(new ChangeBuilder(ctnrC, TRANSIT_CLOSE).build())
                .build();
        d.mAcceptAll = anim1;
        d.mAccept = new WindowContainerToken[]{ctnrA, ctnrB, ctnrC};
        d.mAcceptAnims = new ITransitionAnimation[]{anim1, anim1, anim2};
        d.mDetach = new WindowContainerToken[]{ctnrA, ctnrB};
        d2.mAcceptAll = anim3;
        d2.mDetach = new WindowContainerToken[]{ctnrB, ctnrC};
        mMixpatcher.onTransitionReady(transit, info, mStubTx, mStubTx);

        // All planners got a chance to detach even if all animations are claimed already.
        assertEquals(2, d.mDetached.size());
        assertTrue(d.mDetached.contains(ctnrA));
        assertTrue(d.mDetached.contains(ctnrB));
        assertTrue(d2.mDetached.contains(ctnrC));
        assertNull(anim3.mInfo);
        // However, a container should only be detached once.
        assertEquals(1, d2.mDetached.size());

        d.reset();
        d2.reset();
        final IBinder transit2 = mMixpatcher.startTransition(null /* transit */, TRANSIT_OPEN, wct,
                null /* interest*/);
        final TransitionInfo info2 = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(new ChangeBuilder(ctnrA, TRANSIT_OPEN).build())
                .addChange(new ChangeBuilder(ctnrC, TRANSIT_OPEN).build())
                .addChange(new ChangeBuilder(ctnrD, TRANSIT_CLOSE).build())
                .build();
        d.mAcceptAll = anim3;
        d2.mDetach = new WindowContainerToken[]{ctnrA, ctnrC, ctnrD};
        anim1.mDelayDetach = true;
        mMixpatcher.onTransitionReady(transit2, info2, mStubTx, mStubTx);
        // anim1 and 2 are still playing. Animating containers are already considered "detached"
        // from planner's perspective.
        assertEquals(1, d2.mDetached.size());
        assertTrue(d2.mDetached.contains(ctnrD));
        // Containers must be detached from their prior animations though
        assertEquals(0, anim2.mContainersAnimating.size());
        // anim1 has delayed detach on only 1 container
        assertEquals(1, anim1.mDetaching.size());
        assertTrue(anim1.mDetaching.contains(ctnrA));

        // anim3 hasn't started yet since anim1 is still detaching
        assertNull(anim3.mInfo);
        final IBinder transit3 = mMixpatcher.startTransition(null /* transit */, TRANSIT_OPEN,
                wct, null /* interest*/);
        final TransitionInfo info3 = new TransitionInfoBuilder(TRANSIT_OPEN).build();
        d.reset();
        d2.reset();
        d.mAcceptAll = anim4;
        mMixpatcher.onTransitionReady(transit3, info3, mStubTx, mStubTx);
        // transit2 should be transferring now so further dispatches are queued.
        assertEquals(0, d.mCallCount);

        // Everything should continue once delayed detach is complete
        anim1.finishDetachNow();
        assertEquals(1, d.mCallCount);
        assertEquals(3, anim3.mContainersAnimating.size());
    }

    @Test
    public void planInvisible() {
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        final WindowContainerToken ctnrA = createTestContainer("CtnrA");
        final WindowContainerToken ctnrB = createTestContainer("CtnrB");
        final TestTransitAnim anim1 = new TestTransitAnim(mMainHandler);
        final TestTransitAnim anim2 = new TestTransitAnim(mMainHandler);
        final TestPlanner p = new TestPlanner(mMainHandler);
        final TestPlanner p2 = new TestPlanner(mMainHandler);
        mMixpatcher.mPlanners.add(p2);
        mMixpatcher.mPlanners.add(p);

        final IBinder transit = mMixpatcher.startTransition(null /* transit */, TRANSIT_OPEN, wct,
                List.of(p2));
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(new ChangeBuilder(ctnrA, TRANSIT_OPEN).build())
                .addChange(new ChangeBuilder(ctnrB, TRANSIT_OPEN)
                        .setFlags(TransitionInfo.FLAGS_IS_OCCLUDED_NO_ANIMATION).build())
                .build();
        p2.mAccept = new WindowContainerToken[]{ctnrB};
        p2.mAcceptAnims = new ITransitionAnimation[]{anim2};
        p.mAcceptAll = anim1;
        mMixpatcher.onTransitionReady(transit, info, mStubTx, mStubTx);
        // Invisible takes priority even over interest so `p2` won't see 'ctnrB' and thus won't
        // prepare `anim2`.
        assertNull(anim2.mInfo);
        // Non-invisibles can still dispatch afterwards
        assertEquals(1, anim1.mInfo.getChanges().size());
    }

    private static WindowContainerToken createTestContainer(@NonNull String name) {
        return WindowContainerToken.createProxy(name);
    }

    private static WindowAnimationState buildAnimState() {
        WindowAnimationState out = new WindowAnimationState();
        out.bounds = new RectF(0, 0, 100, 100);
        out.scale = 1.f;
        out.timestamp = System.currentTimeMillis();
        return out;
    }

    static class TestTransitAnim implements ITransitionAnimation {
        boolean mDelayDetach = false;

        TransitionInfo mInfo = null;
        List<WindowContainerToken> mDetaching = null;
        DetachResult mPromise = null;
        IFinishedCallback mFinishCB = null;
        final ArrayList<WindowContainerToken> mContainersAnimating = new ArrayList<>();
        final Handler mMainHandler;

        TestTransitAnim(@NonNull Handler mainHandler) {
            mMainHandler = mainHandler;
        }

        private List<WindowAnimationState> finishDetachInner() {
            final ArrayList<WindowAnimationState> states = new ArrayList<>();
            mDetaching.forEach(s -> states.add(buildAnimState()));
            mContainersAnimating.removeAll(mDetaching);
            return states;
        }

        void finishDetachNow() {
            mPromise.complete(finishDetachInner());
        }

        void finishNow() {
            mFinishCB.onFinished(null /* finishT */);
        }

        @Override
        public DetachResult detach(
                @NonNull List<WindowContainerToken> containers,
                @NonNull SurfaceControl.Transaction startTransaction) {
            mDetaching = containers;
            if (mDelayDetach) {
                mPromise = DetachResult.promise(mMainHandler);
                return mPromise;
            } else {
                return new DetachResult(finishDetachInner());
            }
        }

        @Override
        public void start(@NonNull TransitionInfo info,
                @NonNull List<WindowAnimationState> from,
                @NonNull IFinishedCallback onFinished) {
            mFinishCB = onFinished;
            mInfo = info;
            info.getChanges().forEach(c -> mContainersAnimating.add(c.getContainer()));
        }

        @NonNull
        @Override
        public String getDebugName() {
            return "Test:" + hashCode();
        }
    }

    private static <T> int indexOf(T[] arr, T elem) {
        if (arr == null) return -1;
        for (int i = 0; i < arr.length; ++i) {
            if (arr[i].equals(elem)) return i;
        }
        return -1;
    }

    private static class TestPlanner implements ITransitionPlanner {
        ITransitionAnimation mAcceptAll = null;
        WindowContainerToken[] mAccept = null;
        ITransitionAnimation[] mAcceptAnims = null;

        boolean mDetachAsync = false;
        WindowContainerToken[] mDetach = null;

        final ArrayList<WindowContainerToken> mDetached = new ArrayList<>();
        final ArrayList<DetachResult> mDetachCB = new ArrayList<>();

        int mCallCount = 0;

        final Handler mMainHandler;

        TestPlanner(@NonNull Handler mainHandler) {
            mMainHandler = mainHandler;
        }

        void finishDetachNow() {
            for (int i = 0; i < mDetachCB.size(); ++i) {
                if (mDetachCB.get(i) != null) {
                    mDetachCB.get(i).complete(List.of(buildAnimState()));
                }
                mDetachCB.set(i, null);
            }
        }

        void reset() {
            mCallCount = 0;
            mDetached.clear();
            mDetachCB.clear();
            mDetach = null;
            mAccept = null;
            mAcceptAnims = null;
            mAcceptAll = null;
        }

        @Override
        public void plan(@NonNull AnimationPlan plan,
                @NonNull TransitionInfo fullInfo, @NonNull IBinder transition,
                @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction startTransaction) {
            mCallCount += 1;
            // assign to animations (only available (info) containers)
            final int changeSize = info.getChanges().size();
            for (int i = changeSize - 1; i >= 0; --i) {
                final WindowContainerToken ctnr = info.getChanges().get(i).getContainer();
                int acceptIdx = indexOf(mAccept, ctnr);
                if (acceptIdx >= 0) {
                    plan.setAnimation(ctnr, mAcceptAnims[acceptIdx]);
                } else if (mAcceptAll != null) {
                    plan.setAnimation(ctnr, mAcceptAll);
                }
            }
            // look for containers to detach (all (fullInfo) containers)
            for (int i = fullInfo.getChanges().size() - 1; i >= 0; --i) {
                final WindowContainerToken ctnr = fullInfo.getChanges().get(i).getContainer();
                int detachIdx = indexOf(mDetach, ctnr);
                if (detachIdx >= 0) {
                    if (mDetachAsync) {
                        final DetachResult promise = DetachResult.promise(mMainHandler);
                        if (plan.detachAsync(ctnr, promise)) {
                            mDetached.add(ctnr);
                            mDetachCB.add(promise);
                        }
                    } else {
                        if (plan.detach(ctnr, buildAnimState())) {
                            mDetached.add(ctnr);
                            mDetachCB.add(null);
                        }
                    }
                }
            }
        }

        @NonNull
        @Override
        public String getDebugName() {
            return "Test:" + hashCode();
        }
    }
}
