/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package android.view;

import static android.view.InsetsController.ANIMATION_TYPE_NONE;
import static android.view.InsetsController.ANIMATION_TYPE_USER;
import static android.view.InsetsSource.ID_IME;
import static android.view.WindowInsets.Type.ime;
import static android.view.WindowInsets.Type.statusBars;

import static junit.framework.Assert.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import static org.mockito.AdditionalMatchers.and;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.verify;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.view.WindowManager.BadTokenException;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.ImeTracker;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link InsetsSourceConsumer}.
 *
 * <p>Build/Install/Run:
 *  atest FrameworksCoreTests:InsetsSourceConsumerTest
 *
 * <p>This test class is a part of Window Manager Service tests and specified in
 * {@link com.android.server.wm.test.filters.FrameworksTestsFilter}.
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class InsetsSourceConsumerTest {

    private static final int ID_STATUS_BAR = InsetsSource.createId(
            null /* owner */, 0 /* index */, statusBars());

    private InsetsSourceConsumer mConsumer;

    private final SurfaceSession mSession = new SurfaceSession();
    private SurfaceControl mLeash;
    private InsetsSource mSpyInsetsSource;
    private boolean mRemoveSurfaceCalled = false;
    private boolean mSurfaceParamsApplied = false;
    private InsetsController mController;
    private InsetsState mState;
    private ViewRootImpl mViewRoot;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mLeash = new SurfaceControl.Builder(mSession)
                .setName("testSurface")
                .build();
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(() -> {
            final Context context = instrumentation.getTargetContext();
            // cannot mock ViewRootImpl since it's final.
            mViewRoot = new ViewRootImpl(context, context.getDisplayNoVerify());
            try {
                mViewRoot.setView(new TextView(context), new LayoutParams(), null);
            } catch (BadTokenException e) {
                // activity isn't running, lets ignore BadTokenException.
            }
            mState = new InsetsState();
            mSpyInsetsSource = Mockito.spy(new InsetsSource(ID_STATUS_BAR, statusBars()));
            mState.addSource(mSpyInsetsSource);

            mController = new InsetsController(new ViewRootInsetsControllerHost(mViewRoot)) {
                @Override
                public void applySurfaceParams(
                        @NonNull SyncRtSurfaceTransactionApplier.SurfaceParams... params) {
                    mSurfaceParamsApplied = true;
                }
            };
            mConsumer = new InsetsSourceConsumer(ID_STATUS_BAR, statusBars(), mState, mController) {
                @Override
                public void removeSurface() {
                    super.removeSurface();
                    mRemoveSurfaceCalled = true;
                }
            };
        });
        instrumentation.waitForIdleSync();

        mConsumer.setControl(
                new InsetsSourceControl(ID_STATUS_BAR, statusBars(), mLeash,
                        true /* initialVisible */, new Point(), Insets.NONE),
                new int[1], new int[1], new int[1], new int[1]);
    }

    @Test
    public void testSetControl_cancelAnimation() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            final int[] cancelTypes = {0};

            // Change the side of the insets hint from NONE to BOTTOM.
            final InsetsSourceControl newControl1 = new InsetsSourceControl(mConsumer.getControl());
            newControl1.setInsetsHint(Insets.of(0, 0, 0, 100));
            mConsumer.setControl(newControl1, new int[1], new int[1], cancelTypes, new int[1]);

            assertEquals("The animation must not be cancelled", 0, cancelTypes[0]);

            // Change the side of the insets hint from BOTTOM to TOP.
            final InsetsSourceControl newControl2 = new InsetsSourceControl(mConsumer.getControl());
            newControl2.setInsetsHint(Insets.of(0, 100, 0, 0));
            mConsumer.setControl(newControl2, new int[1], new int[1], cancelTypes, new int[1]);

            assertEquals("The animation must be cancelled", statusBars(), cancelTypes[0]);
        });

    }

    @Test
    public void testOnAnimationStateChanged_requestedInvisible() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mController.setRequestedVisibleTypes(0 /* visibleTypes */, mSpyInsetsSource.getType());
            mConsumer.onAnimationStateChanged(false /* running */);
            verify(mSpyInsetsSource).setVisible(eq(false));
        });
    }

    @Test
    public void testOnAnimationStateChanged_requestedVisible() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            // Insets source starts out visible
            final int type = mSpyInsetsSource.getType();
            mController.setRequestedVisibleTypes(0 /* visibleTypes */, type);
            mConsumer.onAnimationStateChanged(false /* running */);
            mController.setRequestedVisibleTypes(type, type);
            mConsumer.onAnimationStateChanged(false /* running */);
            verify(mSpyInsetsSource).setVisible(eq(false));
            verify(mSpyInsetsSource).setVisible(eq(true));
        });
    }

    @Test
    public void testPendingStates() {
        InsetsState state = new InsetsState();
        InsetsController controller = new InsetsController(new ViewRootInsetsControllerHost(
                mViewRoot));
        InsetsSourceConsumer consumer = new InsetsSourceConsumer(ID_IME, ime(), state, controller);

        InsetsSource source = new InsetsSource(ID_IME, ime());
        source.setFrame(0, 1, 2, 3);
        consumer.updateSource(new InsetsSource(source), ANIMATION_TYPE_NONE);

        // While we're animating, updates are delayed
        source.setFrame(4, 5, 6, 7);
        consumer.updateSource(new InsetsSource(source), ANIMATION_TYPE_USER);
        assertEquals(new Rect(0, 1, 2, 3), state.peekSource(ID_IME).getFrame());

        // Finish the animation, now the pending frame should be applied
        assertTrue(consumer.onAnimationStateChanged(false /* running */));
        assertEquals(new Rect(4, 5, 6, 7), state.peekSource(ID_IME).getFrame());

        // Animating again, updates are delayed
        source.setFrame(8, 9, 10, 11);
        consumer.updateSource(new InsetsSource(source), ANIMATION_TYPE_USER);
        assertEquals(new Rect(4, 5, 6, 7), state.peekSource(ID_IME).getFrame());

        // Updating with the current frame triggers a different code path, verify this clears
        // the pending 8, 9, 10, 11 frame:
        source.setFrame(4, 5, 6, 7);
        consumer.updateSource(new InsetsSource(source), ANIMATION_TYPE_USER);

        assertFalse(consumer.onAnimationStateChanged(false /* running */));
        assertEquals(new Rect(4, 5, 6, 7), state.peekSource(ID_IME).getFrame());
    }

    @Test
    public void testRestore() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mConsumer.setControl(null, new int[1], new int[1], new int[1], new int[1]);
            mSurfaceParamsApplied = false;
            mController.setRequestedVisibleTypes(0 /* visibleTypes */, statusBars());
            assertFalse(mSurfaceParamsApplied);
            int[] hideTypes = new int[1];
            mConsumer.setControl(
                    new InsetsSourceControl(ID_STATUS_BAR, statusBars(), mLeash,
                            true /* initialVisible */, new Point(), Insets.NONE),
                    new int[1], hideTypes, new int[1], new int[1]);
            assertEquals(statusBars(), hideTypes[0]);
            assertFalse(mRemoveSurfaceCalled);
        });
    }

    @Test
    public void testRestore_noAnimation() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mController.setRequestedVisibleTypes(0 /* visibleTypes */, statusBars());
            mConsumer.setControl(null, new int[1], new int[1], new int[1], new int[1]);
            mLeash = new SurfaceControl.Builder(mSession)
                    .setName("testSurface")
                    .build();
            int[] hideTypes = new int[1];
            mConsumer.setControl(
                    new InsetsSourceControl(ID_STATUS_BAR, statusBars(), mLeash,
                            false /* initialVisible */, new Point(), Insets.NONE),
                    new int[1], hideTypes, new int[1], new int[1]);
            assertEquals(0, hideTypes[0]);
        });

    }

    @Test
    public void testWontUpdateImeLeashVisibility_whenAnimation() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ViewRootInsetsControllerHost host = new ViewRootInsetsControllerHost(mViewRoot);
            InsetsController insetsController = new InsetsController(host);
            InsetsSourceConsumer imeConsumer = insetsController.getSourceConsumer(ID_IME, ime());

            // Initial IME insets source control with its leash.
            imeConsumer.setControl(new InsetsSourceControl(ID_IME, ime(), mLeash,
                    false /* initialVisible */, new Point(), Insets.NONE), new int[1], new int[1],
                    new int[1], new int[1]);
            mSurfaceParamsApplied = false;

            // Verify when the app requests controlling show IME animation, the IME leash
            // visibility won't be updated when the consumer received the same leash in setControl.
            insetsController.controlWindowInsetsAnimation(ime(), 0L,
                    null /* interpolator */, null /* cancellationSignal */, null /* listener */);
            assertEquals(ANIMATION_TYPE_USER, insetsController.getAnimationType(ime()));
            imeConsumer.setControl(new InsetsSourceControl(ID_IME, ime(), mLeash,
                    true /* initialVisible */, new Point(), Insets.NONE), new int[1], new int[1],
                    new int[1], new int[1]);
            assertFalse(mSurfaceParamsApplied);
        });
    }

    @Test
    @Ignore("b/418178877")
    public void testImeGetAndClearSkipAnimationOnce_expectSkip() {
        // Expect IME animation will skipped when the IME is visible at first place.
        verifyImeGetAndClearSkipAnimationOnce(true /* hasWindowFocus */, true /* hasViewFocus */,
                true /* expectSkipAnim */);
    }

    @Test
    @Ignore("b/418178877")
    public void testImeGetAndClearSkipAnimationOnce_expectNoSkip() {
        // Expect IME animation will not skipped if previously no view focused when gained the
        // window focus and requesting the IME visible next time.
        verifyImeGetAndClearSkipAnimationOnce(true /* hasWindowFocus */, false /* hasViewFocus */,
                false /* expectSkipAnim */);
    }

    private void verifyImeGetAndClearSkipAnimationOnce(boolean hasWindowFocus, boolean hasViewFocus,
            boolean expectSkipAnim) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ViewRootInsetsControllerHost host = new ViewRootInsetsControllerHost(mViewRoot);
            InsetsController insetsController = new InsetsController(host);
            InsetsSourceConsumer imeConsumer = insetsController.getSourceConsumer(ID_IME, ime());
            // Request IME visible before control is available.
            imeConsumer.onWindowFocusGained(hasWindowFocus);
            final boolean imeVisible = hasWindowFocus && hasViewFocus;
            final var statsToken = ImeTracker.Token.empty();
            if (imeVisible) {
                mController.show(WindowInsets.Type.ime(), statsToken);
                // Called once through the show flow.
                verify(mController).applyAnimation(eq(WindowInsets.Type.ime()), eq(true) /* show */,
                        eq(false) /* skipsAnim */, eq(false) /* skipsCallbacks */, eq(statsToken));
            }

            // set control and verify visibility is applied.
            InsetsSourceControl control = Mockito.spy(new InsetsSourceControl(ID_IME,
                    WindowInsets.Type.ime(), mLeash, false, new Point(), Insets.NONE));
            // Simulate IME source control set this flag when the target has starting window.
            control.setSkipAnimationOnce(true);

            if (imeVisible) {
                // Verify IME applyAnimation should be triggered when control becomes available,
                // and expect skip animation state after getAndClearSkipAnimationOnce invoked.
                mController.onControlsChanged(new InsetsSourceControl[]{ control });
                verify(control).getAndClearSkipAnimationOnce();
                // This ends up creating a new request when we gain control,
                // so the statsToken won't match.
                verify(mController).applyAnimation(eq(WindowInsets.Type.ime()), eq(true) /* show */,
                        eq(expectSkipAnim) /* skipsAnim */, eq(false) /* skipsCallbacks */,
                        and(not(eq(statsToken)), notNull()));
            }

            // If previously hasViewFocus is false, verify when requesting the IME visible next
            // time will not skip animation.
            if (!hasViewFocus) {
                final var statsTokenNext = ImeTracker.Token.empty();
                mController.show(WindowInsets.Type.ime(), statsTokenNext);
                // Called once through the show flow.
                verify(mController).applyAnimation(eq(WindowInsets.Type.ime()), eq(true) /* show */,
                        eq(false) /* skipsAnim */, eq(false) /* skipsCallbacks */,
                        eq(statsTokenNext));
                mController.onControlsChanged(new InsetsSourceControl[]{ control });
                // Verify IME show animation should be triggered when control becomes available and
                // the animation will be skipped by getAndClearSkipAnimationOnce invoked.
                verify(control).getAndClearSkipAnimationOnce();
                verify(mController).applyAnimation(eq(WindowInsets.Type.ime()), eq(true) /* show */,
                        eq(true) /* skipsAnim */, eq(false) /* skipsCallbacks */,
                        and(not(eq(statsToken)), notNull()));
            }
        });
    }

}
