/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_3BUTTON;
import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Handler;
import android.testing.TestableLooper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.policy.GestureNavigationSettingsObserver;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.settings.FakeDisplayTracker;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.phone.LightBarTransitionsController.DarkIntensityApplier;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.time.FakeSystemClock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class LightBarTransitionsControllerTest extends SysuiTestCase {

    @Mock
    private DarkIntensityApplier mApplier;
    @Mock
    private KeyguardStateController mKeyguardStateController;
    @Mock
    private StatusBarStateController mStatusBarStateController;
    @Mock
    private Handler mMainHandler;
    @Mock
    private Handler mBgHandler;

    private final FakeSystemClock mSystemClock = new FakeSystemClock();

    private LightBarTransitionsController mLightBarTransitionsController;

    private final CommandQueue mCommandQueue = new CommandQueue(
            mContext,
            new FakeDisplayTracker(mContext)
    );

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mLightBarTransitionsController = new LightBarTransitionsController(mContext, mMainHandler,
                mBgHandler, mApplier, mSystemClock, mCommandQueue,
                mKeyguardStateController, mStatusBarStateController);
    }

    @Test
    public void setIconsDark_lightAndDark() {
        mLightBarTransitionsController.setIconsDark(true /* dark */, false /* animate */);
        verify(mApplier).applyDarkIntensity(eq(1f));

        mLightBarTransitionsController.setIconsDark(false /* dark */, false /* animate */);
        verify(mApplier).applyDarkIntensity(eq(0f));
    }

    @Test
    public void onDozeAmountChanged_lightWhenDozing() {
        mLightBarTransitionsController.onDozeAmountChanged(1f /* linear */, 1f /* eased */);
        mLightBarTransitionsController.setIconsDark(true /* dark */, false /* animate */);
        verify(mApplier, times(2)).applyDarkIntensity(eq(0f));

        reset(mApplier);
        mLightBarTransitionsController.setIconsDark(false /* dark */, false /* animate */);
        verify(mApplier).applyDarkIntensity(eq(0f));
    }

    @Test
    public void gestureNav_noForceNavButtons_expectNotSupportsIconTint() {
        GestureNavigationSettingsObserver observer = mock(GestureNavigationSettingsObserver.class);
        doReturn(false).when(observer).areNavigationButtonForcedVisible();
        mLightBarTransitionsController.setNavigationSettingsObserver(observer);
        assertFalse(mLightBarTransitionsController.supportsIconTintForNavMode(
                NAV_BAR_MODE_GESTURAL));
    }

    @Test
    public void gestureNav_forceNavButtons_expectSupportsIconTint() {
        GestureNavigationSettingsObserver observer = mock(GestureNavigationSettingsObserver.class);
        doReturn(true).when(observer).areNavigationButtonForcedVisible();
        mLightBarTransitionsController.setNavigationSettingsObserver(observer);
        assertTrue(mLightBarTransitionsController.supportsIconTintForNavMode(
                NAV_BAR_MODE_GESTURAL));
    }

    @Test
    public void buttonNav_noForceNavButtons_expectNotSupportsIconTint() {
        GestureNavigationSettingsObserver observer = mock(GestureNavigationSettingsObserver.class);
        doReturn(false).when(observer).areNavigationButtonForcedVisible();
        mLightBarTransitionsController.setNavigationSettingsObserver(observer);
        assertTrue(mLightBarTransitionsController.supportsIconTintForNavMode(
                NAV_BAR_MODE_3BUTTON));
    }

    @Test
    public void buttonNav_forceNavButtons_expectSupportsIconTint() {
        GestureNavigationSettingsObserver observer = mock(GestureNavigationSettingsObserver.class);
        doReturn(true).when(observer).areNavigationButtonForcedVisible();
        mLightBarTransitionsController.setNavigationSettingsObserver(observer);
        assertTrue(mLightBarTransitionsController.supportsIconTintForNavMode(
                NAV_BAR_MODE_3BUTTON));
    }

    @Test
    public void appTransitionWhileKeyguardIsGoingAway_notForced_pendingTransitionNotSet() {
        int displayId = mContext.getDisplayId();
        when(mKeyguardStateController.isKeyguardGoingAway()).thenReturn(true);

        mCommandQueue.appTransitionPending(displayId, /*forced */ false);
        TestableLooper.get(this).processAllMessages();

        assertFalse(mLightBarTransitionsController.isTransitionPending());

        mCommandQueue.appTransitionStarting(
                displayId,
                /* startTime */ 0,
                /* duration */ 0,
                /* forced */ false);
        TestableLooper.get(this).processAllMessages();

        assertFalse(mLightBarTransitionsController.isTransitionPending());

        mCommandQueue.appTransitionFinished(displayId);
        TestableLooper.get(this).processAllMessages();

        assertFalse(mLightBarTransitionsController.isTransitionPending());
    }

    @Test
    public void appTransitionOnOtherDisplay_keyguardNotGoingAway_noTransitionPending() {
        int displayId = mContext.getDisplayId();
        when(mKeyguardStateController.isKeyguardGoingAway()).thenReturn(true);

        mCommandQueue.appTransitionPending(displayId, /*forced */ false);
        TestableLooper.get(this).processAllMessages();

        assertFalse(mLightBarTransitionsController.isTransitionPending());

        mCommandQueue.appTransitionStarting(
                displayId,
                /* startTime */ 0,
                /* duration */ 0,
                /* forced */ false);
        TestableLooper.get(this).processAllMessages();

        assertFalse(mLightBarTransitionsController.isTransitionPending());

        mCommandQueue.appTransitionFinished(displayId);
        TestableLooper.get(this).processAllMessages();

        assertFalse(mLightBarTransitionsController.isTransitionPending());
    }

    /**
     * See b/457816397
     * The default expected behavior is that appTransitionPending is called before
     * appTransitionStarting. This is in fact the case typically when those calls come in over
     * the Binder.
     *
     * However, the assumption baked into LightBarTransitions is that at some point
     * appTransitionStarting will _also_ be called internally to our process. Those calls always
     * have `forced = true`, and will catch pending tint changes.
     *
     * This test checks specifically for cases when that *internal* call does not happen.
     */
    @Test
    public void setIconsDarkWhileTransitionIsPendingWithoutForce_animationEndCatchesTintChange() {
        int displayId = mContext.getDisplayId();

        // Initially light icons
        mLightBarTransitionsController.setIconsDark(false, false);

        assertFalse(mLightBarTransitionsController.isTransitionPending());
        assertFalse(mLightBarTransitionsController.isTintChangePending());

        mCommandQueue.appTransitionPending(displayId);

        TestableLooper.get(this).processAllMessages();

        assertTrue(mLightBarTransitionsController.isTransitionPending());

        when(mKeyguardStateController.isKeyguardGoingAway()).thenReturn(true);

        // WHEN an app transition starts, but we are *not* told to force.
        mCommandQueue.appTransitionStarting(displayId, 0, 0);

        // WHEN setIconsDark comes in during that transition
        mLightBarTransitionsController.setIconsDark(true, true);

        assertTrue(mLightBarTransitionsController.isTransitionPending());
        assertTrue(mLightBarTransitionsController.isTintChangePending());

        // WHEN the transition finishes
        mCommandQueue.appTransitionFinished(displayId);

        TestableLooper.get(this).processAllMessages();

        // THEN we catch the pending tint change and process it
        assertFalse(mLightBarTransitionsController.isTransitionPending());
        assertFalse(mLightBarTransitionsController.isTintChangePending());
    }
}
